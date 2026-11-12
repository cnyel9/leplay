// LePlay Windows Spike: search YouTube via InnerTube, resolve a direct
// stream in Rust, and hand playback to a bundled mpv.exe.
// Proof targets: (1) Rust-side stream resolution without the Android
// pipeline, (2) playback plus seek on a weak laptop, (3) small footprint.

use std::io::Write;
use std::path::PathBuf;
use std::process::{Command, Stdio};
use std::sync::mpsc::{channel, Receiver, Sender};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

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

// ---------- InnerTube ----------

const KEY_WEB: &str = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
const KEY_ANDROID: &str = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w";
const KEY_IOS: &str = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc";
const UA_BROWSER: &str =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
const UA_ANDROID: &str = "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip";

fn http(ua: &str) -> Result<reqwest::blocking::Client, String> {
    reqwest::blocking::Client::builder()
        .user_agent(ua)
        .timeout(Duration::from_secs(30))
        .build()
        .map_err(|e| format!("http client: {e}"))
}

fn post_json(url: &str, ua: &str, body: &Value) -> Result<Value, String> {
    let client = http(ua)?;
    let resp = client
        .post(url)
        .json(body)
        .send()
        .map_err(|e| format!("request: {e}"))?;
    if !resp.status().is_success() {
        return Err(format!("http {}", resp.status()));
    }
    resp.json::<Value>().map_err(|e| format!("json: {e}"))
}

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

fn search(query: &str) -> Result<Vec<VideoItem>, String> {
    let body = json!({
        "context": {"client": {"clientName": "WEB", "clientVersion": "2.20251001.01.00"}},
        "query": query
    });
    let url = format!("https://www.youtube.com/youtubei/v1/search?key={KEY_WEB}&prettyPrint=false");
    let data = post_json(&url, UA_BROWSER, &body)?;
    let mut renderers = Vec::new();
    collect_key(&data, "videoRenderer", &mut renderers);
    let mut items = Vec::new();
    for vr in renderers {
        let id = vr
            .get("videoId")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();
        if id.is_empty() {
            continue;
        }
        let title = vr
            .get("title")
            .map(runs_text)
            .unwrap_or_else(|| "(tanpa judul)".to_string());
        let channel = vr
            .get("longBylineText")
            .map(runs_text)
            .unwrap_or_default();
        let duration = vr
            .get("lengthText")
            .map(runs_text)
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
    log_line(&format!("search '{query}' -> {} hasil", items.len()));
    if items.is_empty() {
        return Err("tidak ada hasil video".to_string());
    }
    Ok(items)
}

struct PlayTarget {
    video_url: String,
    audio_url: Option<String>,
    desc: String,
}

struct ClientSpec {
    name: &'static str,
    key: &'static str,
    ua: &'static str,
    client: Value,
}

fn player_clients() -> Vec<ClientSpec> {
    vec![
        ClientSpec {
            name: "ANDROID_VR",
            key: KEY_WEB,
            ua: UA_BROWSER,
            client: json!({"clientName": "ANDROID_VR", "clientVersion": "1.65.10",
                "deviceMake": "Oculus", "deviceModel": "Quest 3",
                "osName": "Android", "osVersion": "12L", "androidSdkVersion": 32}),
        },
        ClientSpec {
            name: "ANDROID",
            key: KEY_ANDROID,
            ua: UA_ANDROID,
            client: json!({"clientName": "ANDROID", "clientVersion": "20.10.38",
                "osName": "Android", "osVersion": "11", "androidSdkVersion": 30}),
        },
        ClientSpec {
            name: "IOS",
            key: KEY_IOS,
            ua: UA_BROWSER,
            client: json!({"clientName": "IOS", "clientVersion": "20.10.4",
                "deviceMake": "Apple", "deviceModel": "iPhone16,2",
                "osName": "iPhone", "osVersion": "18.3.2.22D82"}),
        },
    ]
}

fn pick_streams(spec_name: &str, data: &Value) -> Result<PlayTarget, String> {
    let sd = data
        .get("streamingData")
        .ok_or_else(|| "streamingData kosong".to_string())?;
    let adaptive = sd
        .get("adaptiveFormats")
        .and_then(|v| v.as_array())
        .cloned()
        .unwrap_or_default();
    let direct = |f: &Value| f.get("url").and_then(|u| u.as_str()).map(|s| s.to_string());
    // Adaptive: best mp4 video at or below 720p, plus best mp4 audio.
    let mut best_video: Option<(i64, i64, String)> = None; // (height, bitrate, url)
    for f in &adaptive {
        let mime = f.get("mimeType").and_then(|m| m.as_str()).unwrap_or("");
        if !mime.starts_with("video/mp4") {
            continue;
        }
        if let Some(url) = direct(f) {
            let height = f.get("height").and_then(|h| h.as_i64()).unwrap_or(0);
            let bitrate = f.get("bitrate").and_then(|b| b.as_i64()).unwrap_or(0);
            let better = match &best_video {
                None => true,
                Some((bh, bb, _)) => {
                    let over_new = height > 720;
                    let over_old = *bh > 720;
                    if over_new != over_old {
                        !over_new
                    } else if height != *bh {
                        height > *bh
                    } else {
                        bitrate > *bb
                    }
                }
            };
            if better {
                best_video = Some((height, bitrate, url));
            }
        }
    }
    let mut best_audio: Option<(i64, String)> = None;
    for f in &adaptive {
        let mime = f.get("mimeType").and_then(|m| m.as_str()).unwrap_or("");
        if !mime.starts_with("audio/mp4") {
            continue;
        }
        if let Some(url) = direct(f) {
            let bitrate = f.get("bitrate").and_then(|b| b.as_i64()).unwrap_or(0);
            if best_audio.as_ref().map(|(bb, _)| bitrate > *bb).unwrap_or(true) {
                best_audio = Some((bitrate, url));
            }
        }
    }
    if let (Some((height, _, vurl)), Some((_, aurl))) = (best_video, best_audio) {
        return Ok(PlayTarget {
            video_url: vurl,
            audio_url: Some(aurl),
            desc: format!("{spec_name} mp4 {height}p + audio terpisah"),
        });
    }
    // Muxed fallback (single file, usually itag 18 360p).
    if let Some(formats) = sd.get("formats").and_then(|v| v.as_array()) {
        for f in formats {
            if let Some(url) = direct(f) {
                let itag = f.get("itag").and_then(|i| i.as_i64()).unwrap_or(0);
                return Ok(PlayTarget {
                    video_url: url,
                    audio_url: None,
                    desc: format!("{spec_name} muxed itag {itag}"),
                });
            }
        }
    }
    // HLS fallback.
    if let Some(hls) = sd.get("hlsManifestUrl").and_then(|u| u.as_str()) {
        return Ok(PlayTarget {
            video_url: hls.to_string(),
            audio_url: None,
            desc: format!("{spec_name} HLS"),
        });
    }
    Err("tidak ada stream url langsung".to_string())
}

fn resolve(video_id: &str) -> Result<PlayTarget, String> {
    let mut reasons = Vec::new();
    for spec in player_clients() {
        let body = json!({
            "context": {"client": spec.client},
            "videoId": video_id,
            "racyCheckOk": true,
            "contentCheckOk": true
        });
        let url = format!(
            "https://www.youtube.com/youtubei/v1/player?key={}&prettyPrint=false",
            spec.key
        );
        match post_json(&url, spec.ua, &body) {
            Ok(data) => {
                let status = data
                    .get("playabilityStatus")
                    .and_then(|p| p.get("status"))
                    .and_then(|s| s.as_str())
                    .unwrap_or("?");
                if status != "OK" {
                    let reason = data
                        .get("playabilityStatus")
                        .and_then(|p| p.get("reason"))
                        .and_then(|s| s.as_str())
                        .unwrap_or(status);
                    reasons.push(format!("{}: {}", spec.name, reason));
                    continue;
                }
                match pick_streams(spec.name, &data) {
                    Ok(target) => {
                        log_line(&format!("resolve {video_id} OK -> {}", target.desc));
                        return Ok(target);
                    }
                    Err(e) => reasons.push(format!("{}: {}", spec.name, e)),
                }
            }
            Err(e) => reasons.push(format!("{}: {}", spec.name, e)),
        }
    }
    Err(format!("semua klien gagal ({})", reasons.join("; ")))
}

fn spawn_mpv(target: &PlayTarget, title: &str) -> Result<String, String> {
    let exe_dir = std::env::current_exe()
        .ok()
        .and_then(|p| p.parent().map(|d| d.to_path_buf()));
    let bundled = exe_dir.map(|d| d.join("mpv.exe")).filter(|p| p.exists());
    let mpv_cmd = bundled
        .as_ref()
        .map(|p| p.to_string_lossy().to_string())
        .unwrap_or_else(|| "mpv".to_string());
    let mut cmd = Command::new(&mpv_cmd);
    cmd.arg(format!("--title={title}"))
        .arg("--force-window=yes")
        .arg(format!("--user-agent={UA_BROWSER}"))
        .arg("--referrer=https://www.youtube.com/");
    if let Some(audio) = &target.audio_url {
        cmd.arg(format!("--audio-file={audio}"));
    }
    cmd.arg(&target.video_url);
    cmd.stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    log_line(&format!("spawn mpv via {mpv_cmd} ({})", target.desc));
    cmd.spawn()
        .map(|_| format!("mpv jalan: {}", target.desc))
        .map_err(|e| format!("gagal menjalankan mpv ({mpv_cmd}): {e}"))
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
    tx: Sender<Msg>,
    rx: Receiver<Msg>,
}

impl SpikeApp {
    fn new() -> Self {
        let (tx, rx) = channel();
        Self {
            query: String::new(),
            results: Vec::new(),
            status: "Ketik kata kunci lalu tekan Cari. Klik Putar pada hasil untuk memutar lewat mpv.".to_string(),
            busy: false,
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
        std::thread::spawn(move || {
            let _ = tx.send(Msg::SearchDone(search(&q)));
        });
    }

    fn start_play(&mut self, item: &VideoItem) {
        if self.busy {
            return;
        }
        self.busy = true;
        self.status = format!("Mengambil stream: {}", item.title);
        let tx = self.tx.clone();
        let id = item.id.clone();
        let title = item.title.clone();
        std::thread::spawn(move || {
            let result = resolve(&id).and_then(|t| spawn_mpv(&t, &title));
            let _ = tx.send(Msg::PlayDone(result));
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
                    log_line(&format!("search gagal: {e}"));
                }
                Msg::PlayDone(Ok(s)) => {
                    self.status = format!("Diputar di jendela mpv ({s}). Seek dan ganti kualitas dari jendela mpv.");
                }
                Msg::PlayDone(Err(e)) => {
                    self.status = format!("Gagal memutar: {e}");
                    log_line(&format!("play gagal: {e}"));
                }
            }
        }
        if self.busy {
            ctx.request_repaint_after(Duration::from_millis(150));
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
                let enter = resp.lost_focus()
                    && ui.input(|i| i.key_pressed(egui::Key::Enter));
                if ui.button("Cari").clicked() || enter {
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
                    ui.label("Belum ada hasil. Cari dulu di atas.");
                }
                let items = self.results.clone();
                for item in &items {
                    ui.group(|ui| {
                        ui.set_min_width(ui.available_width());
                        ui.horizontal(|ui| {
                            ui.vertical(|ui| {
                                ui.label(egui::RichText::new(&item.title).strong());
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
                                    if ui.button("Putar").clicked() {
                                        self.start_play(item);
                                    }
                                },
                            );
                        });
                    });
                    ui.add_space(4.0);
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
            .with_inner_size([920.0, 620.0]),
        ..Default::default()
    };
    eframe::run_native(
        "LePlay Windows Spike",
        options,
        Box::new(|_cc| Ok(Box::new(SpikeApp::new()))),
    )
}
