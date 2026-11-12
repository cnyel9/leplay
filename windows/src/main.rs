#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]
// LePlay Windows Spike: search YouTube via InnerTube, resolve a direct
// stream in Rust, and hand playback to a bundled mpv.exe.
// Proof targets: (1) Rust-side stream resolution without the Android
// pipeline, (2) playback plus seek on a weak laptop, (3) small footprint.
// Fix1: review pass by 20 read-only agents (subsystem GUI, stream ranking,
// diagnostics, shared HTTP clients, canonical client identities).

use std::collections::HashSet;
use std::io::Write;
use std::path::PathBuf;
use std::process::{Command, Stdio};
use std::sync::mpsc::{channel, Receiver, Sender};
use std::sync::OnceLock;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

#[cfg(windows)]
use std::os::windows::process::CommandExt;

use eframe::egui;
use serde_json::{json, Value};

// ---------- logging (leplay-spike-log.txt next to the exe) ----------

fn log_path() -> PathBuf {
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            return dir.join("leplay-spike-log.txt");
        }
    }
    PathBuf::from("leplay-spike-log.txt")
}

fn log_line(msg: &str) {
    let secs = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    if let Ok(mut f) = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(log_path())
    {
        let _ = writeln!(f, "[{secs}] {msg}");
    }
}

// ---------- HTTP (two shared clients, one per user-agent) ----------

const KEY_WEB: &str = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
const KEY_ANDROID: &str = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w";
const KEY_IOS: &str = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc";
const UA_BROWSER: &str =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
const UA_ANDROID: &str = "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip";
const UA_ANDROID_VR: &str =
    "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; Quest 3) gzip";
const UA_IOS: &str = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)";

fn build_client(ua: &str) -> reqwest::blocking::Client {
    reqwest::blocking::Client::builder()
        .user_agent(ua)
        .timeout(Duration::from_secs(30))
        .connect_timeout(Duration::from_secs(8))
        .build()
        .expect("reqwest client")
}

fn client_for(ua: &str) -> &'static reqwest::blocking::Client {
    static BROWSER: OnceLock<reqwest::blocking::Client> = OnceLock::new();
    static ANDROID: OnceLock<reqwest::blocking::Client> = OnceLock::new();
    static ANDROID_VR: OnceLock<reqwest::blocking::Client> = OnceLock::new();
    static IOS: OnceLock<reqwest::blocking::Client> = OnceLock::new();
    let slot = if ua == UA_ANDROID {
        &ANDROID
    } else if ua == UA_ANDROID_VR {
        &ANDROID_VR
    } else if ua == UA_IOS {
        &IOS
    } else {
        &BROWSER
    };
    slot.get_or_init(|| build_client(ua))
}

fn post_json(
    url: &str,
    ua: &str,
    body: &Value,
    client_headers: Option<(&str, &str)>,
) -> Result<Value, String> {
    let client = client_for(ua);
    let mut req = client.post(url).json(body);
    if let Some((name_id, version)) = client_headers {
        req = req
            .header("X-YouTube-Client-Name", name_id)
            .header("X-YouTube-Client-Version", version)
            .header("X-Goog-Api-Format-Version", "1");
    }
    let resp = req
        .send()
        .map_err(|e| format!("permintaan gagal: {e}"))?;
    let status = resp.status();
    if !status.is_success() {
        let text = resp.text().unwrap_or_default();
        let snippet: String = text.chars().take(200).collect();
        return Err(format!("http {status}: {snippet}"));
    }
    resp.json::<Value>()
        .map_err(|e| format!("json tidak valid: {e}"))
}

// ---------- InnerTube search ----------

#[derive(Clone)]
struct VideoItem {
    id: String,
    title: String,
    channel: String,
    duration: String,
    views: String,
}

fn collect_key<'a>(v: &'a Value, key: &str, out: &mut Vec<&'a Value>) {
    match v {
        Value::Object(map) => {
            if let Some(inner) = map.get(key) {
                out.push(inner);
            }
            for val in map.values() {
                collect_key(val, key, out);
            }
        }
        Value::Array(arr) => {
            for val in arr {
                collect_key(val, key, out);
            }
        }
        _ => {}
    }
}

fn runs_text(v: &Value) -> String {
    if let Some(runs) = v.get("runs").and_then(|r| r.as_array()) {
        let mut s = String::new();
        for r in runs {
            if let Some(t) = r.get("text").and_then(|t| t.as_str()) {
                s.push_str(t);
            }
        }
        return s;
    }
    v.get("simpleText")
        .and_then(|t| t.as_str())
        .unwrap_or("")
        .to_string()
}

fn non_empty(v: Option<String>) -> Option<String> {
    v.filter(|s| !s.trim().is_empty())
}

fn search(query: &str) -> Result<Vec<VideoItem>, String> {
    let body = json!({
        "context": {"client": {"clientName": "WEB", "clientVersion": "2.20251001.01.00"}},
        "query": query
    });
    let url = format!("https://www.youtube.com/youtubei/v1/search?key={KEY_WEB}&prettyPrint=false");
    let data = post_json(&url, UA_BROWSER, &body, Some(("1", "2.20251001.01.00")))?;
    let mut renderers = Vec::new();
    collect_key(&data, "videoRenderer", &mut renderers);
    let mut seen: HashSet<String> = HashSet::new();
    let mut items = Vec::new();
    for vr in renderers {
        let id = vr
            .get("videoId")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();
        if id.is_empty() || !seen.insert(id.clone()) {
            continue;
        }
        let title = non_empty(vr.get("title").map(runs_text))
            .unwrap_or_else(|| "(tanpa judul)".to_string());
        let channel = non_empty(vr.get("longBylineText").map(runs_text))
            .or_else(|| non_empty(vr.get("ownerText").map(runs_text)))
            .or_else(|| non_empty(vr.get("shortBylineText").map(runs_text)))
            .unwrap_or_default();
        let duration = non_empty(vr.get("lengthText").map(runs_text))
            .unwrap_or_else(|| "LIVE".to_string());
        let views = vr
            .get("viewCountText")
            .map(runs_text)
            .unwrap_or_default();
        items.push(VideoItem {
            id,
            title,
            channel,
            duration,
            views,
        });
        if items.len() >= 25 {
            break;
        }
    }
    log_line(&format!("pencarian '{query}' -> {} hasil", items.len()));
    if items.is_empty() {
        return Err("tidak ada hasil video".to_string());
    }
    Ok(items)
}

// ---------- InnerTube player / stream picking ----------

struct PlayTarget {
    video_url: String,
    audio_url: Option<String>,
    desc: String,
}

struct ClientSpec {
    name: &'static str,
    name_id: &'static str,
    version: &'static str,
    key: &'static str,
    ua: &'static str,
    client: Value,
}

fn player_clients() -> Vec<ClientSpec> {
    // Client versions age out; bump from yt-dlp's INNERTUBE_CLIENTS table.
    vec![
        ClientSpec {
            name: "ANDROID_VR",
            name_id: "28",
            version: "1.65.10",
            key: KEY_WEB,
            ua: UA_ANDROID_VR,
            client: json!({"clientName": "ANDROID_VR", "clientVersion": "1.65.10",
                "deviceMake": "Oculus", "deviceModel": "Quest 3",
                "osName": "Android", "osVersion": "12L", "androidSdkVersion": 32}),
        },
        ClientSpec {
            name: "ANDROID",
            name_id: "3",
            version: "20.10.38",
            key: KEY_ANDROID,
            ua: UA_ANDROID,
            client: json!({"clientName": "ANDROID", "clientVersion": "20.10.38",
                "osName": "Android", "osVersion": "11", "androidSdkVersion": 30}),
        },
        ClientSpec {
            name: "IOS",
            name_id: "5",
            version: "20.10.4",
            key: KEY_IOS,
            ua: UA_IOS,
            client: json!({"clientName": "IOS", "clientVersion": "20.10.4",
                "deviceMake": "Apple", "deviceModel": "iPhone16,2",
                "osName": "iPhone", "osVersion": "18.3.2.22D82"}),
        },
    ]
}

struct VCand {
    height: i64,
    bitrate: i64,
    fps: i64,
    avc: bool,
    url: String,
}

fn video_better(a: &VCand, b: &VCand) -> bool {
    // Hardware-decodable AVC first, then the <=720 partition, then height
    // (highest inside the cap, closest above the cap), then <=30fps, then
    // bitrate. AV1/VP9 in mp4 would be pure software decode on the target.
    if a.avc != b.avc {
        return a.avc;
    }
    let a_in = a.height <= 720;
    let b_in = b.height <= 720;
    if a_in != b_in {
        return a_in;
    }
    if a.height != b.height {
        return if a_in {
            a.height > b.height
        } else {
            a.height < b.height
        };
    }
    let a60 = a.fps > 30;
    let b60 = b.fps > 30;
    if a60 != b60 {
        return !a60;
    }
    a.bitrate > b.bitrate
}

fn fmt_url(f: &Value) -> Option<String> {
    f.get("url").and_then(|u| u.as_str()).map(|s| s.to_string())
}

fn pick_audio(adaptive: &[Value], container: &str) -> Option<String> {
    let mut best_default: Option<(i64, String)> = None;
    let mut best_any: Option<(i64, String)> = None;
    for f in adaptive {
        let mime = f.get("mimeType").and_then(|m| m.as_str()).unwrap_or("");
        if !mime.starts_with(container) {
            continue;
        }
        if let Some(url) = fmt_url(f) {
            let bitrate = f.get("bitrate").and_then(|b| b.as_i64()).unwrap_or(0);
            if best_any.as_ref().map(|(bb, _)| bitrate > *bb).unwrap_or(true) {
                best_any = Some((bitrate, url.clone()));
            }
            let is_default = f
                .get("audioTrack")
                .and_then(|t| t.get("audioIsDefault"))
                .and_then(|d| d.as_bool())
                .unwrap_or(false);
            if is_default
                && best_default
                    .as_ref()
                    .map(|(bb, _)| bitrate > *bb)
                    .unwrap_or(true)
            {
                best_default = Some((bitrate, url));
            }
        }
    }
    best_default.map(|(_, u)| u).or(best_any.map(|(_, u)| u))
}

fn pick_streams(spec_name: &str, data: &Value) -> Result<PlayTarget, String> {
    let sd = data
        .get("streamingData")
        .ok_or_else(|| "data streaming tidak tersedia".to_string())?;
    let adaptive = sd
        .get("adaptiveFormats")
        .and_then(|v| v.as_array())
        .cloned()
        .unwrap_or_default();
    let formats = sd
        .get("formats")
        .and_then(|v| v.as_array())
        .cloned()
        .unwrap_or_default();
    let hls = sd
        .get("hlsManifestUrl")
        .and_then(|u| u.as_str())
        .map(|s| s.to_string());
    let is_live = data
        .get("videoDetails")
        .and_then(|v| v.get("isLive"))
        .and_then(|b| b.as_bool())
        .unwrap_or(false);
    // Live streams: HLS is the correct vehicle, not a DASH pair.
    if is_live {
        if let Some(h) = &hls {
            return Ok(PlayTarget {
                video_url: h.clone(),
                audio_url: None,
                desc: format!("{spec_name} HLS live"),
            });
        }
    }
    // Adaptive video candidates (mp4 only; webm/VP9 is software decode).
    let mut best_video: Option<VCand> = None;
    for f in &adaptive {
        let mime = f.get("mimeType").and_then(|m| m.as_str()).unwrap_or("");
        if !mime.starts_with("video/mp4") {
            continue;
        }
        if let Some(url) = fmt_url(f) {
            let cand = VCand {
                height: f.get("height").and_then(|h| h.as_i64()).unwrap_or(0),
                bitrate: f.get("bitrate").and_then(|b| b.as_i64()).unwrap_or(0),
                fps: f.get("fps").and_then(|v| v.as_i64()).unwrap_or(0),
                avc: mime.contains("avc1"),
                url,
            };
            if best_video
                .as_ref()
                .map(|b| video_better(&cand, b))
                .unwrap_or(true)
            {
                best_video = Some(cand);
            }
        }
    }
    let audio = pick_audio(&adaptive, "audio/mp4")
        .or_else(|| pick_audio(&adaptive, "audio/webm"));
    if let (Some(v), Some(a)) = (&best_video, &audio) {
        return Ok(PlayTarget {
            video_url: v.url.clone(),
            audio_url: Some(a.clone()),
            desc: format!(
                "{spec_name} mp4 {}p{} + audio terpisah",
                v.height,
                if v.avc { " avc" } else { "" }
            ),
        });
    }
    // Muxed fallback: best by height then bitrate, mp4 preferred on ties.
    let mut best_mux: Option<(i64, i64, bool, String, i64)> = None;
    for f in &formats {
        if let Some(url) = fmt_url(f) {
            let height = f.get("height").and_then(|h| h.as_i64()).unwrap_or(0);
            let bitrate = f.get("bitrate").and_then(|b| b.as_i64()).unwrap_or(0);
            let mime = f.get("mimeType").and_then(|m| m.as_str()).unwrap_or("");
            let is_mp4 = mime.starts_with("video/mp4");
            let itag = f.get("itag").and_then(|i| i.as_i64()).unwrap_or(0);
            let better = match &best_mux {
                None => true,
                Some((bh, bb, bmp, _, _)) => {
                    height > *bh
                        || (height == *bh && is_mp4 && !bmp)
                        || (height == *bh && is_mp4 == *bmp && bitrate > *bb)
                }
            };
            if better {
                best_mux = Some((height, bitrate, is_mp4, url, itag));
            }
        }
    }
    if let Some((_, _, _, url, itag)) = best_mux {
        return Ok(PlayTarget {
            video_url: url,
            audio_url: None,
            desc: format!("{spec_name} format gabungan (itag {itag})"),
        });
    }
    if let Some(h) = &hls {
        return Ok(PlayTarget {
            video_url: h.clone(),
            audio_url: None,
            desc: format!("{spec_name} HLS"),
        });
    }
    // Diagnostics so a SABR/cipher-only response is recognisable as such.
    let all: Vec<&Value> = adaptive.iter().chain(formats.iter()).collect();
    let with_url = all.iter().filter(|f| f.get("url").is_some()).count();
    let cipher = all
        .iter()
        .filter(|f| f.get("signatureCipher").is_some() || f.get("cipher").is_some())
        .count();
    let sabr = sd.get("serverAbrStreamingUrl").is_some();
    Err(format!(
        "tidak ada URL stream langsung (format {}, ber-url {}, cipher {}, sabr {}, hls {})",
        all.len(),
        with_url,
        cipher,
        sabr,
        hls.is_some()
    ))
}

fn resolve(video_id: &str) -> Result<PlayTarget, String> {
    let mut reasons = Vec::new();
    for spec in player_clients() {
        log_line(&format!("ambil stream {video_id} coba {}", spec.name));
        let body = json!({
            "context": {"client": spec.client},
            "videoId": video_id,
            "playbackContext": {"contentPlaybackContext": {
                "vis": 0, "splay": false, "signatureTimestamp": 20073 }},
            "racyCheckOk": true,
            "contentCheckOk": true
        });
        let url = format!(
            "https://www.youtube.com/youtubei/v1/player?key={}&prettyPrint=false",
            spec.key
        );
        match post_json(&url, spec.ua, &body, Some((spec.name_id, spec.version))) {
            Ok(data) => {
                let ps = data.get("playabilityStatus");
                let status = ps
                    .and_then(|p| p.get("status"))
                    .and_then(|s| s.as_str())
                    .unwrap_or("?");
                if status != "OK" {
                    let reason = ps
                        .and_then(|p| p.get("reason"))
                        .and_then(|s| s.as_str())
                        .unwrap_or(status);
                    let sub = ps
                        .and_then(|p| p.get("subreason"))
                        .map(runs_text)
                        .unwrap_or_default();
                    let full = if sub.is_empty() {
                        reason.to_string()
                    } else {
                        format!("{reason} / {sub}")
                    };
                    log_line(&format!("ambil stream {video_id} {} gagal: {}", spec.name, full));
                    reasons.push(format!("{}: {}", spec.name, full));
                    continue;
                }
                match pick_streams(spec.name, &data) {
                    Ok(target) => {
                        let expires = data
                            .get("streamingData")
                            .and_then(|s| s.get("expiresInSeconds"))
                            .and_then(|e| e.as_i64())
                            .unwrap_or(0);
                        log_line(&format!(
                            "ambil stream {video_id} OK -> {} (kedaluwarsa ~{expires} dtk)",
                            target.desc
                        ));
                        return Ok(target);
                    }
                    Err(e) => {
                        log_line(&format!("ambil stream {video_id} {} gagal: {}", spec.name, e));
                        reasons.push(format!("{}: {}", spec.name, e));
                    }
                }
            }
            Err(e) => {
                log_line(&format!("ambil stream {video_id} {} gagal: {}", spec.name, e));
                reasons.push(format!("{}: {}", spec.name, e));
            }
        }
    }
    Err(format!("semua klien gagal ({})", reasons.join("; ")))
}

fn spawn_mpv(target: &PlayTarget, title: &str) -> Result<String, String> {
    let exe_dir = std::env::current_exe()
        .ok()
        .and_then(|p| p.parent().map(|d| d.to_path_buf()));
    let bundled = exe_dir
        .as_ref()
        .map(|d| d.join("mpv.exe"))
        .filter(|p| p.exists());
    let mut cmd = match &bundled {
        Some(p) => Command::new(p),
        None => Command::new("mpv"),
    };
    let safe_title = title.replace('$', "$$");
    let mpv_log = log_path().with_file_name("leplay-mpv-log.txt");
    cmd.arg(format!("--title={safe_title}"))
        .arg("--force-window=yes")
        .arg("--hwdec=auto-safe")
        .arg(format!("--log-file={}", mpv_log.display()))
        .arg(format!("--user-agent={UA_BROWSER}"))
        .arg("--referrer=https://www.youtube.com/");
    if let Some(audio) = &target.audio_url {
        cmd.arg(format!("--audio-file={audio}"));
    }
    cmd.arg("--");
    cmd.arg(&target.video_url);
    cmd.stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    #[cfg(windows)]
    cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
    let via = bundled
        .as_ref()
        .map(|p| p.display().to_string())
        .unwrap_or_else(|| "mpv".to_string());
    log_line(&format!("jalankan mpv lewat {via} ({})", target.desc));
    match cmd.spawn() {
        Ok(mut child) => {
            std::thread::spawn(move || match child.wait() {
                Ok(st) => log_line(&format!("mpv keluar: {st}")),
                Err(e) => log_line(&format!("mpv wait gagal: {e}")),
            });
            Ok(format!("mpv berjalan: {}", target.desc))
        }
        Err(e) => Err(format!("gagal menjalankan mpv ({via}): {e}")),
    }
}

// ---------- UI ----------

enum Msg {
    SearchDone(Result<Vec<VideoItem>, String>),
    PlayDone(Result<String, String>),
}

struct SpikeApp {
    query: String,
    results: Vec<VideoItem>,
    status: String,
    busy: bool,
    focused_once: bool,
    egui_ctx: egui::Context,
    tx: Sender<Msg>,
    rx: Receiver<Msg>,
}

impl SpikeApp {
    fn new(egui_ctx: egui::Context) -> Self {
        let (tx, rx) = channel();
        Self {
            query: String::new(),
            results: Vec::new(),
            status: "Ketik kata kunci lalu tekan Cari atau Enter. Klik Putar pada hasil untuk memutar lewat mpv.".to_string(),
            busy: false,
            focused_once: false,
            egui_ctx,
            tx,
            rx,
        }
    }

    fn start_search(&mut self) {
        let q = self.query.trim().to_string();
        if q.is_empty() || self.busy {
            return;
        }
        self.busy = true;
        self.status = format!("Mencari \"{q}\"...");
        let tx = self.tx.clone();
        let ctx = self.egui_ctx.clone();
        std::thread::spawn(move || {
            let result =
                std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| search(&q)))
                    .unwrap_or_else(|_| Err("proses latar berhenti mendadak".to_string()));
            let _ = tx.send(Msg::SearchDone(result));
            ctx.request_repaint();
        });
    }

    fn start_play(&mut self, item: &VideoItem) {
        if self.busy {
            return;
        }
        self.busy = true;
        self.status = format!("Mengambil stream: {}", item.title);
        let tx = self.tx.clone();
        let ctx = self.egui_ctx.clone();
        let id = item.id.clone();
        let title = item.title.clone();
        std::thread::spawn(move || {
            let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                resolve(&id).and_then(|t| spawn_mpv(&t, &title))
            }))
            .unwrap_or_else(|_| Err("proses latar berhenti mendadak".to_string()));
            let _ = tx.send(Msg::PlayDone(result));
            ctx.request_repaint();
        });
    }
}

impl eframe::App for SpikeApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        while let Ok(msg) = self.rx.try_recv() {
            self.busy = false;
            match msg {
                Msg::SearchDone(Ok(items)) => {
                    self.status = format!("{} hasil. Klik Putar untuk memutar.", items.len());
                    self.results = items;
                }
                Msg::SearchDone(Err(e)) => {
                    self.status = format!("Pencarian gagal: {e}");
                    log_line(&format!("pencarian gagal: {e}"));
                }
                Msg::PlayDone(Ok(s)) => {
                    self.status = format!("{s}. Cek jendela mpv, videonya harusnya sudah bunyi di sana. Tiap klik Putar membuka jendela mpv baru.");
                }
                Msg::PlayDone(Err(e)) => {
                    self.status = format!("Gagal memutar: {e}");
                    log_line(&format!("putar gagal: {e}"));
                }
            }
        }
        if self.busy {
            ctx.request_repaint_after(Duration::from_millis(100));
        }

        egui::TopBottomPanel::top("search_bar").show(ctx, |ui| {
            ui.add_space(6.0);
            ui.horizontal(|ui| {
                ui.heading("LePlay Windows Spike");
                ui.add_space(10.0);
                let resp = ui.add(
                    egui::TextEdit::singleline(&mut self.query)
                        .hint_text("Cari video di YouTube...")
                        .desired_width(380.0),
                );
                if !self.focused_once {
                    resp.request_focus();
                    self.focused_once = true;
                }
                let enter = resp.lost_focus()
                    && ui.input(|i| i.key_pressed(egui::Key::Enter));
                if ui
                    .add_enabled(!self.busy, egui::Button::new("Cari"))
                    .clicked()
                    || enter
                {
                    self.start_search();
                }
                if self.busy {
                    ui.spinner();
                }
            });
            ui.add_space(6.0);
        });

        egui::TopBottomPanel::bottom("status_bar").show(ctx, |ui| {
            ui.add_space(4.0);
            ui.label(&self.status);
            ui.add_space(4.0);
        });

        egui::CentralPanel::default().show(ctx, |ui| {
            egui::ScrollArea::vertical().show(ui, |ui| {
                if self.results.is_empty() {
                    if self.busy {
                        ui.label("Mencari...");
                    } else {
                        ui.label("Belum ada hasil. Cari dulu di atas.");
                    }
                }
                let mut clicked: Option<VideoItem> = None;
                for item in &self.results {
                    ui.group(|ui| {
                        ui.set_min_width(ui.available_width());
                        ui.horizontal(|ui| {
                            ui.vertical(|ui| {
                                ui.add(
                                    egui::Label::new(
                                        egui::RichText::new(&item.title).strong(),
                                    )
                                    .truncate(),
                                );
                                let mut meta = item.channel.clone();
                                if !item.duration.is_empty() {
                                    meta.push_str(&format!("  •  {}", item.duration));
                                }
                                if !item.views.is_empty() {
                                    meta.push_str(&format!("  •  {}", item.views));
                                }
                                ui.label(egui::RichText::new(meta).small().weak());
                            });
                            ui.with_layout(
                                egui::Layout::right_to_left(egui::Align::Center),
                                |ui| {
                                    if ui
                                        .add_enabled(
                                            !self.busy,
                                            egui::Button::new("Putar"),
                                        )
                                        .clicked()
                                    {
                                        clicked = Some(item.clone());
                                    }
                                },
                            );
                        });
                    });
                    ui.add_space(4.0);
                }
                if let Some(item) = clicked {
                    self.start_play(&item);
                }
            });
        });
    }
}

fn main() -> eframe::Result<()> {
    std::panic::set_hook(Box::new(|info| {
        log_line(&format!("PANIC: {info}"));
    }));
    log_line("LePlay Windows Spike mulai");
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_title("LePlay Windows Spike")
            .with_inner_size([880.0, 560.0]),
        ..Default::default()
    };
    let result = eframe::run_native(
        "LePlay Windows Spike",
        options,
        Box::new(|cc| Ok(Box::new(SpikeApp::new(cc.egui_ctx.clone())))),
    );
    if let Err(e) = &result {
        log_line(&format!("run_native gagal: {e}"));
    }
    result
}
