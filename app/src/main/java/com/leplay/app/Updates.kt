package com.leplay.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

@Immutable
data class UpdateInfo(
    val version: String,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val htmlUrl: String = "",
)

@Immutable
data class ReleaseEntry(
    val version: String,
    val tag: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val prerelease: Boolean,
)

/** Failure of [fetchReleaseHistory] carrying a short, user-visible detail
 *  (exception name + message) so the version-history screen can show WHY
 *  it failed instead of a bare "could not load". */
class ReleaseHistoryException(val detail: String, cause: Throwable? = null) :
    Exception(detail, cause)

/** Full release list for the About version-history screen, newest first. */
suspend fun fetchReleaseHistory(): List<ReleaseEntry> = withContext(Dispatchers.IO) {
    try {
        val request = Request.Builder()
            .url("https://api.github.com/repos/cnyel9/leplay/releases?per_page=100")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "LePlay/${BuildConfig.VERSION_NAME}")
            .build()
        Net.client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("GitHub releases returned ${response.code}")
            // GitHub can answer 200 with a JSON OBJECT instead of the
            // releases array (rate limit / abuse / error payloads): its
            // "message" field is the useful detail, surface it.
            if (bodyText.trimStart().startsWith("{")) {
                val message = runCatching { JSONObject(bodyText).optString("message") }.getOrNull()
                throw ReleaseHistoryException(
                    "GitHub error object" + if (!message.isNullOrBlank()) ": $message" else "",
                )
            }
            val array = org.json.JSONArray(bodyText)
            val out = ArrayList<ReleaseEntry>()
            for (i in 0 until array.length()) {
                val json = array.optJSONObject(i) ?: continue
                if (json.optBoolean("draft")) continue
                val tag = json.optString("tag_name").ifBlank { continue }
                out.add(
                    ReleaseEntry(
                        version = tag.removePrefix("v").removePrefix("V").trim(),
                        tag = tag,
                        name = json.optString("name").ifBlank { tag },
                        body = json.optString("body").orEmpty(),
                        publishedAt = json.optString("published_at"),
                        prerelease = json.optBoolean("prerelease"),
                    ),
                )
            }
            out
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: ReleaseHistoryException) {
        Log.e("LePlayUpdates", "fetchReleaseHistory failed: ${e.detail}", e)
        throw e
    } catch (e: Exception) {
        Log.e("LePlayUpdates", "fetchReleaseHistory failed", e)
        val detail = "${e.javaClass.simpleName}: ${e.message ?: "no message"}".take(120)
        throw ReleaseHistoryException(detail, e)
    }
}

/**
 * Shown when the GitHub release body is empty (or when testing an APK that is
 * newer than the latest published release). Keep this in sync with the build.
 */
internal val RELEASE_NOTES = """
    • LePlay 0.0.6 test-1: channel Live tab gets Latest / Popular / Oldest sort chips with full paging, and video rows now show the view count together with the upload time in every language.
    • LePlay 0.0.5: Indonesian is now the default language with the full interface translated, video descriptions can be selected and copied, and viewer counts use Indonesian formatting (rb, jt, M).
    • Ad-free YouTube and YouTube Music playback with a Liquid Glass UI.
    • Personalised Shorts feed, synced lyrics, and high-quality downloads up to 4K HDR.
    • Background playback, Picture-in-Picture, and Android TV mode.
""".trimIndent()

/** Outcome of the most recent update check, so the Settings version badge
 *  reflects a real result instead of a static label (0.1.1, notes 40/51). */
enum class UpdateCheckOutcome { Unknown, UpToDate, Available, Failed }

/**
 * Checks the GitHub releases feed, downloads the newest APK and hands it to the system installer.
 * No server, no account: the release page is the source of truth.
 */
@Stable
class UpdateState(private val scope: CoroutineScope) {

    var info by mutableStateOf<UpdateInfo?>(null)
        private set
    var visible by mutableStateOf(false)
        private set
    var checking by mutableStateOf(false)
        private set
    var downloading by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var downloaded by mutableStateOf<File?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    /** Result of the latest completed check; Failed keeps the last success
     *  cached in [lastKnownLatest] for display (0.1.1, note 40d). */
    var lastOutcome by mutableStateOf(UpdateCheckOutcome.Unknown)
        private set
    var lastKnownLatest by mutableStateOf<UpdateInfo?>(null)
        private set

    private var downloadJob: Job? = null

    fun check(manual: Boolean = false) {
        if (checking) return
        checking = true
        scope.launch {
            val latest = withContext(Dispatchers.IO) { runCatching { fetchLatest() }.getOrNull() }
            checking = false
            if (latest == null) {
                // A failed check must not clobber a known good result: the
                // Settings badge keeps showing the cached success (note 40c/d).
                if (lastOutcome == UpdateCheckOutcome.Unknown) lastOutcome = UpdateCheckOutcome.Failed
                if (manual) message = I18n.t("update_check_failed", "Couldn't check for updates. Try again in a moment.")
                return@launch
            }
            lastKnownLatest = latest
            if (isNewer(latest.version, BuildConfig.VERSION_NAME)) {
                info = latest
                error = null
                lastOutcome = UpdateCheckOutcome.Available
                message = null
                visible = true
            } else {
                lastOutcome = UpdateCheckOutcome.UpToDate
                message = if (manual) {
                    I18n.t("latest_version_notice", "You're on the latest version ({version}).").replace("{version}", BuildConfig.VERSION_NAME)
                } else {
                    null
                }
            }
        }
    }

    fun download(context: Context) {
        val update = info ?: return
        if (downloading) return
        downloading = true
        error = null
        progress = 0f
        downloadJob = scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching { downloadApk(context, update) { p -> progress = p } }.getOrNull()
            }
            downloading = false
            downloadJob = null
            if (file != null) {
                downloaded = file
                install(context)
            } else {
                error = I18n.t("update_download_failed", "Download failed. Check your connection and try again.")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        downloading = false
        progress = 0f
    }

    fun install(context: Context) {
        val file = downloaded ?: return
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            error = I18n.t("update_allow_installs", "Allow installs from LePlay, then tap Install again.")
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        error = null
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            error = I18n.t("update_installer_failed", "Couldn't open the installer. Try again.")
        }
    }

    fun dismiss() {
        if (!downloading) visible = false
    }

    private fun fetchLatest(): UpdateInfo? {
        // GitHub's unauthenticated API is limited per IP and the limit is
        // often shared (CGNAT), so a single 403/429 used to surface as a
        // spurious "check failed" (0.1.1, note 40). Retry a few times with
        // backoff, honouring Retry-After / X-RateLimit-Reset when present.
        var attempt = 0
        while (true) {
            val result = fetchLatestOnce()
            if (result.info != null || !result.retryable || attempt >= 2) return result.info
            val waitMs = result.retryAfterMs ?: (1500L shl attempt)
            runCatching { Thread.sleep(waitMs.coerceIn(500L, 60_000L)) }
            attempt++
        }
    }

    private data class LatestAttempt(val info: UpdateInfo?, val retryable: Boolean, val retryAfterMs: Long? = null)

    private fun fetchLatestOnce(): LatestAttempt {
        val request = Request.Builder()
            .url("https://api.github.com/repos/cnyel9/leplay/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "LePlay/${BuildConfig.VERSION_NAME}")
            .build()
        val executed = try {
            Net.client.newCall(request).execute()
        } catch (t: Throwable) {
            return LatestAttempt(null, retryable = true)
        }
        return executed.use { response ->
            if (!response.isSuccessful) {
                val code = response.code
                val retryable = code == 403 || code == 429 || code >= 500
                var waitMs: Long? = null
                if (retryable) {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    val reset = response.header("X-RateLimit-Reset")?.toLongOrNull()
                    waitMs = when {
                        retryAfter != null -> retryAfter * 1000L
                        reset != null -> reset * 1000L - System.currentTimeMillis()
                        else -> null
                    }?.coerceIn(500L, 60_000L)
                }
                return@use LatestAttempt(null, retryable = retryable, retryAfterMs = waitMs)
            }
            runCatching { parseLatest(response) }.getOrElse { LatestAttempt(null, retryable = true) }
        }
    }

    private fun parseLatest(response: okhttp3.Response): LatestAttempt {
        response.body?.string().orEmpty().let { raw ->
            if (raw.isBlank()) return LatestAttempt(null, retryable = true)
            val json = JSONObject(raw)
            val tag = json.optString("tag_name").ifBlank { return LatestAttempt(null, retryable = false) }
            val version = tag.removePrefix("v").removePrefix("V").trim()
            if (version.isBlank()) return LatestAttempt(null, retryable = false)
            val assets = json.optJSONArray("assets") ?: return LatestAttempt(null, retryable = false)
            var apkUrl: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val candidate = asset.optString("browser_download_url")
                if (candidate.isBlank()) continue
                if (apkUrl == null || name.equals("LePlay.apk", ignoreCase = true)) {
                    apkUrl = candidate
                    size = asset.optLong("size")
                }
                if (name.equals("LePlay.apk", ignoreCase = true)) break
            }
            val url = apkUrl ?: return LatestAttempt(null, retryable = false)
            return LatestAttempt(
                UpdateInfo(
                    version = version,
                    tag = tag,
                    notes = json.optString("body").orEmpty(),
                    apkUrl = url,
                    sizeBytes = size,
                    htmlUrl = json.optString("html_url").orEmpty(),
                ),
                retryable = false,
            )
        }
    }

    private suspend fun downloadApk(
        context: Context,
        update: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "LePlay.apk")
        if (target.exists()) target.delete()
        val request = Request.Builder()
            .url(update.apkUrl)
            .header("User-Agent", "LePlay/${BuildConfig.VERSION_NAME}")
            .build()
        Net.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    var lastTick = 0L
                    while (input.read(buffer).also { read = it } > 0) {
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val p = (done.toFloat() / total).coerceIn(0f, 1f)
                            val now = System.currentTimeMillis()
                            if (now - lastTick > 80 || p >= 1f) {
                                lastTick = now
                                withContext(Dispatchers.Main) { onProgress(p) }
                            }
                        }
                    }
                }
            }
        }
        if (target.length() <= 0L) error("Empty download")
        return target
    }
}

fun isNewerVersion(remote: String, current: String): Boolean {
    fun parts(value: String) = value.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .split('.')
        .map { it.toIntOrNull() ?: 0 }
    val r = parts(remote)
    val c = parts(current)
    for (i in 0 until maxOf(r.size, c.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = c.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

private fun isNewer(remote: String, current: String) = isNewerVersion(remote, current)

private fun formatUpdateSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}

@Composable
fun UpdateOverlay(state: UpdateState, backdrop: Backdrop) {
    val context = LocalContext.current
    val update = state.info
    val c = MaterialTheme.colorScheme

    AnimatedVisibility(
        visible = state.visible && update != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(150)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.38f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { state.dismiss() }
                // On TV this scrim must not become a giant dead focus stop.
                .then(if (TvMode.isTv) Modifier.focusProperties { canFocus = false } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = state.visible,
                enter = scaleIn(
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
                    initialScale = 0.86f,
                ) + fadeIn(tween(220)),
                exit = scaleOut(tween(140), targetScale = 0.94f) + fadeOut(tween(140)),
            ) {
                Column(
                    modifier = Modifier
                        .padding(22.dp)
                        .widthIn(max = 410.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .liquidGlass(
                            backdrop,
                            glassSpec(
                                shape = RoundedCornerShape(30.dp),
                                tint = glassFill(0.9f),
                                blur = 26.dp,
                                refractionHeight = 16.dp,
                                refractionAmount = 24.dp,
                            ),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { }
                        // Same dead-focus-stop guard for the card itself.
                        .then(if (TvMode.isTv) Modifier.focusProperties { canFocus = false } else Modifier)
                        .padding(22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(c.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = c.primary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                I18n.t("update_available", "Update available"),
                                color = c.onSurface,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "LePlay ${update?.version.orEmpty()}" +
                                    formatUpdateSize(update?.sizeBytes ?: 0L).let { if (it.isBlank()) "" else " · $it" },
                                color = c.onSurfaceVariant,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .pressable(onClick = { state.dismiss() }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = I18n.t("close_update_dialog", "Close update dialog"),
                                tint = c.onSurfaceVariant,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        I18n.t("whats_new", "What's new"),
                        color = c.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        update?.notes?.trim()?.ifBlank { RELEASE_NOTES } ?: RELEASE_NOTES,
                        color = c.onSurface.copy(alpha = 0.92f),
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        I18n.t("view_on_github", "View on GitHub"),
                        color = c.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .pressable {
                                val url = update?.htmlUrl?.ifBlank { null }
                                    ?: "https://github.com/cnyel9/leplay/releases/tag/${update?.tag.orEmpty()}"
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                                    )
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )

                    state.error?.let { message ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            message,
                            color = c.error,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    if (state.downloading) {
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = c.primary,
                            trackColor = c.onSurface.copy(alpha = 0.12f),
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            I18n.t("update_downloading", "{percent}% · downloading LePlay.apk").replace("{percent}", "${(state.progress * 100).toInt()}"),
                            color = c.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                if (state.downloading) state.cancelDownload() else state.dismiss()
                            },
                        ) {
                            Text(
                                I18n.t("cancel", "Cancel"),
                                color = c.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                if (state.downloaded != null) state.install(context) else state.download(context)
                            },
                            enabled = !state.downloading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = c.primary,
                                contentColor = c.onPrimary,
                            ),
                        ) {
                            if (state.checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = c.onPrimary,
                                )
                            } else {
                                Text(
                                    I18n.t("install", "Install"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        I18n.t("update_downloaded_from", "Downloaded from GitHub Releases · {tag}").replace("{tag}", update?.tag.orEmpty()),
                        color = c.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
