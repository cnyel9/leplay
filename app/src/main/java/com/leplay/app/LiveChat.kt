package com.leplay.app

import android.content.Context
import android.util.Base64
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.UUID

/** One run of a chat message: plain text or a channel emote image
 *  (0.1.1, note 46). */
@Immutable
data class ChatSegment(val text: String = "", val emoteUrl: String? = null)

@Immutable
data class LiveChatMessage(
    val id: String,
    val author: String,
    val text: String,
    val avatar: String? = null,
    val pending: Boolean = false,
    val offsetMs: Long? = null,

    /** Message runs with emotes kept as images; empty for plain messages. */
    val segments: List<ChatSegment> = emptyList(),
    val isMember: Boolean = false,
    val isModerator: Boolean = false,
    val isOwner: Boolean = false,
    val memberBadgeUrl: String? = null,
)

internal data class LiveChatPage(
    val messages: List<LiveChatMessage>,
    val next: String?,
    val sendParams: String? = null,
    val viewerCount: String? = null,
    val membersOnly: Boolean = false,
)

internal data class ReplayPage(
    val messages: List<LiveChatMessage>,
    val next: String?,
    val horizonMs: Long,
)

internal data class LiveChatStart(
    val continuation: String,
    val channelId: String?,
    val sendParams: String?,
    val viewerCount: String? = null,
)

@Stable
class LiveChatState(private val scope: CoroutineScope) {
    var messages by mutableStateOf<List<LiveChatMessage>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var live by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var draft by mutableStateOf("")
    var sending by mutableStateOf(false)
        private set

    /** Concurrent viewers text for the header, e.g. "1.234" (0.1.1, note 45). */
    var viewerCount by mutableStateOf<String?>(null)
        private set

    /** Chat is members-only: the composer is replaced by a notice (note 46). */
    var membersOnly by mutableStateOf(false)
        private set
    var sendError by mutableStateOf<String?>(null)
        private set

    private var videoId: String? = null
    private var channelId: String? = null
    private var sendParams: String? = null
    private var job: Job? = null

    fun start(id: String, channelHint: String? = null) {
        if (videoId == id && job?.isActive == true) return
        stop()
        videoId = id
        channelId = channelHint
        sendParams = null
        messages = emptyList()
        loading = true
        live = false
        error = null
        sendError = null
        viewerCount = null
        membersOnly = false
        job = scope.launch {
            try {
                val start: LiveChatStart = withContext(Dispatchers.IO) { LiveChatFeed.start(id) }
                    ?: run {
                        error = I18n.t("live_chat_unavailable", "Live chat isn't available for this stream.")
                        loading = false
                        return@launch
                    }
                if (channelId == null) channelId = start.channelId
                if (sendParams == null) sendParams = start.sendParams
                if (viewerCount == null) viewerCount = start.viewerCount
                var cursor = start.continuation
                while (isActive) {
                    val page = withContext(Dispatchers.IO) { LiveChatFeed.page(cursor) }
                    if (page.messages.isNotEmpty()) {
                        val seen = messages.mapTo(HashSet()) { it.id }
                        messages = (messages + page.messages.filter { seen.add(it.id) }).takeLast(220)
                    }
                    if (sendParams == null) sendParams = page.sendParams
                    page.viewerCount?.let { viewerCount = it }
                    if (page.membersOnly) membersOnly = true
                    page.next?.takeIf { it.isNotBlank() }?.let { cursor = it }
                    live = true
                    loading = false
                    delay(2_500)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                error = YouTube.friendlyError(t)
                loading = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        loading = false
        live = false
        sending = false
    }

    fun send() {
        val id = videoId ?: return
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        if (!Account.signedIn) {
            sendError = I18n.t("sign_in_to_chat", "Sign in to chat.")
            return
        }
        sending = true
        sendError = null
        scope.launch {
            try {
                val sent = withContext(Dispatchers.IO) {
                    LiveChatFeed.send(id, channelId, sendParams, text)
                }
                if (sent.isEmpty()) {
                    val profile = Account.profile
                    val local = LiveChatMessage(
                        id = "local-${UUID.randomUUID()}",
                        author = profile?.name ?: I18n.t("you", "You"),
                        text = text,
                        avatar = profile?.avatar,
                    )
                    messages = (messages + local).takeLast(220)
                } else {
                    val seen = messages.mapTo(HashSet()) { it.id }
                    messages = (messages + sent.filter { seen.add(it.id) }).takeLast(220)
                }
                draft = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                sendError = YouTube.friendlyError(t)
            } finally {
                sending = false
            }
        }
    }
}

/**
 * Buffers a past stream's chat replay ahead of the playhead. Detection and
 * prefetching are silent: when a video has no replay, [available] simply
 * stays false and no UI ever appears for it.
 */
@Stable
class ChatReplayState(private val scope: CoroutineScope) {
    var available by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var messages by mutableStateOf<List<LiveChatMessage>>(emptyList())
        private set

    private var videoId: String? = null
    private var nextCursor: String? = null
    private var horizonMs: Long = 0L
    private var wantedMs: Long = 0L
    private var job: Job? = null

    /** Probes for a replay and, when one exists, prefetches its first minute. */
    fun prepare(id: String) {
        if (videoId == id) return
        reset()
        videoId = id
        job = scope.launch {
            try {
                val token = withContext(Dispatchers.IO) { LiveChatFeed.replayStart(id) }
                if (token == null || videoId != id) return@launch
                nextCursor = token
                wantedMs = 60_000L
                available = true
                drain()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                // No replay for this video; stay unavailable without any error UI.
            }
        }
    }

    /** Fetches further pages until the buffer covers [upToMs] plus a minute of slack. */
    fun ensureBuffered(upToMs: Long) {
        if (!available) return
        if (upToMs > wantedMs) wantedMs = upToMs
        if (nextCursor == null || horizonMs >= wantedMs + 60_000L) return
        if (job?.isActive == true) return
        val id = videoId ?: return
        job = scope.launch {
            try {
                if (videoId == id) drain()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                // Keep whatever is buffered; the replay just stops advancing.
            }
        }
    }

    private suspend fun drain() {
        while (true) {
            val cursor = nextCursor ?: return
            if (horizonMs >= wantedMs + 60_000L) return
            loading = true
            val page = try {
                withContext(Dispatchers.IO) { LiveChatFeed.replayPage(cursor) }
            } finally {
                loading = false
            }
            if (page.messages.isNotEmpty()) {
                val seen = messages.mapTo(HashSet()) { it.id }
                messages = (messages + page.messages.filter { seen.add(it.id) })
                    .sortedBy { it.offsetMs ?: 0L }
            }
            if (page.horizonMs > horizonMs) horizonMs = page.horizonMs
            nextCursor = page.next
            if (page.next == null || page.next == cursor) return
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        loading = false
    }

    fun reset() {
        stop()
        videoId = null
        nextCursor = null
        horizonMs = 0L
        wantedMs = 0L
        available = false
        messages = emptyList()
    }
}

object LiveChatFeed {

    internal fun start(videoId: String): LiveChatStart? {
        val root = InnerTube.postAny("next", JSONObject().put("videoId", videoId))
        var token: String? = null
        var channelId: String? = null
        var ownerId: String? = null
        var sendParams: String? = null
        var viewers: String? = null
        root.walk { key, obj ->
            when (key) {
                "liveChatRenderer" -> {
                    if (token == null) token = firstContinuation(obj.optJSONArray("continuations"))
                }

                "videoViewCountRenderer" -> {
                    if (viewers == null) {
                        val raw = obj.text("viewCount")
                        viewers = raw?.replace(Regex("[^0-9.,KMBkmb]"), "")?.trim()?.ifBlank { null }
                    }
                }

                "videoOwnerRenderer" -> {
                    if (ownerId == null) {
                        ownerId = obj.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId")
                            ?.takeIf { it.startsWith("UC") }
                    }
                }

                "sendLiveChatMessageEndpoint",
                "liveChatSendMessageEndpoint",
                "liveChatSendMessageCommand" -> {
                    if (sendParams == null) {
                        sendParams = obj.optString("params").ifBlank { null }
                    }
                }

                "liveChatMessageInputRenderer" -> {
                    if (sendParams == null) {
                        val endpoint = obj.optJSONObject("sendButton")
                            ?.optJSONObject("buttonRenderer")
                            ?.optJSONObject("serviceEndpoint")
                            ?.let {
                                it.optJSONObject("sendLiveChatMessageEndpoint")
                                    ?: it.optJSONObject("liveChatSendMessageEndpoint")
                            }
                        sendParams = endpoint?.optString("params")?.ifBlank { null }
                    }
                }

                "browseEndpoint" -> {
                    if (channelId == null) {
                        channelId = obj.optString("browseId").takeIf { it.startsWith("UC") }
                    }
                }
            }
        }
        val continuation = token ?: return null
        return LiveChatStart(
            continuation = continuation,
            channelId = ownerId ?: channelId,
            sendParams = sendParams,
            viewerCount = viewers,
        )
    }

    internal fun page(cursor: String): LiveChatPage {
        val root = InnerTube.postAny("live_chat/get_live_chat", JSONObject().put("continuation", cursor))
        val out = ArrayList<LiveChatMessage>()
        var next: String? = null
        var sendParams: String? = null
        var membersOnly = false
        root.walk { key, obj ->
            when (key) {
                "liveChatTextMessageRenderer",
                "liveChatPaidMessageRenderer",
                "liveChatMembershipItemRenderer",
                -> parseMessage(obj)?.let(out::add)

                "liveChatContinuation" -> {
                    next = firstContinuation(obj.optJSONArray("continuations")) ?: next
                }

                "liveChatMessageInputRenderer" -> {
                    // A members-only stream's input renderer carries no
                    // send button; detect the mode so the UI can show the
                    // blocked-style notice (0.1.1, note 46).
                    val modeText = obj.text("placeholderText").orEmpty()
                    if (obj.optJSONObject("sendButton") == null ||
                        modeText.contains("member", ignoreCase = true)
                    ) {
                        membersOnly = true
                    }
                }

                "sendLiveChatMessageEndpoint",
                "liveChatSendMessageEndpoint",
                "liveChatSendMessageCommand" -> {
                    if (sendParams == null) {
                        sendParams = obj.optString("params").ifBlank { null }
                    }
                }

                "liveChatMessageInputRenderer" -> {
                    if (sendParams == null) {
                        val endpoint = obj.optJSONObject("sendButton")
                            ?.optJSONObject("buttonRenderer")
                            ?.optJSONObject("serviceEndpoint")
                            ?.let {
                                it.optJSONObject("sendLiveChatMessageEndpoint")
                                    ?: it.optJSONObject("liveChatSendMessageEndpoint")
                            }
                        sendParams = endpoint?.optString("params")?.ifBlank { null }
                    }
                }
            }
        }
        return LiveChatPage(out.distinctBy { it.id }, next, sendParams, membersOnly = membersOnly)
    }

    /**
     * Returns the chat replay continuation for a past stream, or null when the
     * video has no replay. Only a continuation entry whose key is explicitly
     * liveChatReplayContinuationData counts; live entries are ignored so a
     * running stream never looks like a replay.
     */
    internal fun replayStart(videoId: String): String? {
        val root = InnerTube.postAny("next", JSONObject().put("videoId", videoId))
        var token: String? = null
        root.walk { key, obj ->
            if (key != "liveChatRenderer" || token != null) return@walk
            val continuations = obj.optJSONArray("continuations") ?: return@walk
            for (i in 0 until continuations.length()) {
                val entry = continuations.optJSONObject(i) ?: continue
                entry.optJSONObject("liveChatReplayContinuationData")
                    ?.optString("continuation")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { token = it }
                if (token != null) break
            }
        }
        // The anonymous "next" response carries no chat renderer at all, so
        // signed-out users fall back to the watch page, whose embedded data
        // holds the same replay continuation when a replay exists.
        return token ?: replayTokenFromWatchPage(videoId)
    }

    private fun replayTokenFromWatchPage(videoId: String): String? = runCatching {
        val request = okhttp3.Request.Builder()
            .url("https://www.youtube.com/watch?v=$videoId")
            .header("User-Agent", Net.USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        val html = Net.client.newCall(request).execute().use { it.body?.string().orEmpty() }
        Regex("\"liveChatReplayContinuationData\"\\s*:\\s*\\{\\s*\"continuation\"\\s*:\\s*\"([^\"]+)\"")
            .find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Fetches one replay page. Each replayChatItemAction carries the video
     * offset its messages appeared at; those offsets stamp the parsed
     * messages so the panel can reveal them in sync with playback.
     */
    internal fun replayPage(cursor: String): ReplayPage {
        val root = InnerTube.postAny("live_chat/get_live_chat_replay", JSONObject().put("continuation", cursor))
        val out = ArrayList<LiveChatMessage>()
        var next: String? = null
        var untilMs: Long? = null
        var maxOffset = 0L
        root.walk { key, obj ->
            when (key) {
                "liveChatContinuation" -> {
                    val continuations = obj.optJSONArray("continuations") ?: return@walk
                    for (i in 0 until continuations.length()) {
                        val entry = continuations.optJSONObject(i) ?: continue
                        val data = entry.optJSONObject("liveChatReplayContinuationData") ?: continue
                        data.optString("continuation").takeIf { it.isNotBlank() }?.let { next = it }
                        data.optString("timeUntilLastMessageMsec").toLongOrNull()?.let { untilMs = it }
                    }
                }

                "replayChatItemAction" -> {
                    val offset = obj.optString("videoOffsetTimeMsec").toLongOrNull() ?: 0L
                    if (offset > maxOffset) maxOffset = offset
                    obj.walk { innerKey, inner ->
                        if (innerKey == "liveChatTextMessageRenderer" ||
                            innerKey == "liveChatPaidMessageRenderer" ||
                            innerKey == "liveChatMembershipItemRenderer"
                        ) {
                            parseMessage(inner)?.let { out += it.copy(offsetMs = offset) }
                        }
                    }
                }
            }
        }
        if (out.isEmpty() && next == null) return ReplayPage(emptyList(), null, 0L)
        return ReplayPage(out.distinctBy { it.id }, next, maxOf(untilMs ?: 0L, maxOffset))
    }

    /**
     * Posts a chat message to YouTube's live chat endpoint.
     */
    internal fun send(
        videoId: String,
        channelId: String?,
        pageParams: String?,
        text: String,
    ): List<LiveChatMessage> {
        val params = pageParams ?: channelId?.let { liveMessageParams(it, videoId) }
            ?: throw IOException(I18n.t("chat_not_ready", "Chat isn't ready yet. Try again in a moment."))
        val body = JSONObject()
            .put("params", params)
            .put("clientMessageId", UUID.randomUUID().toString())
            .put(
                "richMessage",
                JSONObject().put(
                    "textSegments",
                    JSONArray().put(JSONObject().put("text", text)),
                ),
            )
        val root = InnerTube.post("live_chat/send_message", body)
        if (root.has("error")) {
            val err = root.optJSONObject("error")
            val msg = err?.optString("message") ?: I18n.t("chat_send_failed", "YouTube could not send your message")
            throw IOException(msg)
        }
        var errorMsg: String? = null
        val out = ArrayList<LiveChatMessage>()
        root.walk { key, obj ->
            if (key == "liveChatTextMessageRenderer" ||
                key == "liveChatPaidMessageRenderer" ||
                key == "liveChatMembershipItemRenderer"
            ) {
                parseMessage(obj)?.let(out::add)
            } else if (key == "liveChatActionErrorRenderer" || key == "showLiveChatActionErrorAction") {
                errorMsg = obj.text("errorText") ?: obj.optString("errorText").ifBlank { null }
            }
        }
        if (errorMsg != null) {
            throw IOException(errorMsg)
        }
        return out.distinctBy { it.id }
    }

    internal fun liveMessageParams(channelId: String, videoId: String): String {
        val ids = ByteArrayOutputStream().apply {
            write(protoString(1, channelId))
            write(protoString(2, videoId))
        }.toByteArray()
        val params = protoBytes(5, ids)
        val root = ByteArrayOutputStream().apply {
            write(protoBytes(1, params))
            write(protoVarintField(2, 1L))
            write(protoVarintField(3, 4L))
        }.toByteArray()
        return Base64.encodeToString(root, Base64.NO_WRAP)
    }

    private fun protoVarint(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        while (true) {
            val bits = (v and 0x7f).toInt()
            v = v ushr 7
            if (v == 0L) {
                out.write(bits)
                return out.toByteArray()
            }
            out.write(bits or 0x80)
        }
    }

    private fun protoTag(field: Int, wire: Int): ByteArray =
        protoVarint(((field shl 3) or wire).toLong())

    private fun protoBytes(field: Int, bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(protoTag(field, 2))
        out.write(protoVarint(bytes.size.toLong()))
        out.write(bytes)
        return out.toByteArray()
    }

    private fun protoVarintField(field: Int, value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(protoTag(field, 0))
        out.write(protoVarint(value))
        return out.toByteArray()
    }

    private fun protoString(field: Int, value: String): ByteArray =
        protoBytes(field, value.toByteArray(Charsets.UTF_8))

    private fun firstContinuation(continuations: JSONArray?): String? {
        if (continuations == null) return null
        for (i in 0 until continuations.length()) {
            val entry = continuations.optJSONObject(i) ?: continue
            continuationFrom(entry)?.let { return it }
        }
        return null
    }

    private fun continuationFrom(entry: JSONObject): String? {
        for (key in listOf(
            "reloadContinuationData",
            "realtimeChatRealtimeContinuationData",
            "invalidationContinuationData",
            "timedContinuationData",
        )) {
            entry.optJSONObject(key)?.optString("continuation")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun parseMessage(obj: JSONObject): LiveChatMessage? {
        val author = obj.text("authorName").orEmpty().trim()
        // Keep message runs as segments so custom channel emotes render as
        // images instead of being flattened away (0.1.1, note 46). The
        // flattened [text] stays as the fallback and for plain messages.
        val segments = ArrayList<ChatSegment>()
        val flat = StringBuilder()
        obj.optJSONObject("message")?.optJSONArray("runs")?.let { runs ->
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val runText = run.optString("text")
                if (runText.isNotEmpty()) {
                    segments.add(ChatSegment(text = runText))
                    flat.append(runText)
                    continue
                }
                val emoji = run.optJSONObject("emoji") ?: continue
                val url = emoji.optJSONObject("image")?.thumbnails()?.lastOrNull()
                if (url != null) {
                    segments.add(ChatSegment(emoteUrl = url))
                    flat.append(emoji.optJSONArray("shortcuts")?.optString(0).orEmpty())
                }
            }
        }
        val text = if (segments.isNotEmpty()) flat.toString().trim() else obj.text("message").orEmpty().trim()
        if (author.isBlank() || text.isBlank()) return null
        val avatar = obj.optJSONObject("authorPhoto")?.thumbnails()?.lastOrNull()
        val id = obj.optString("id").ifBlank { obj.optString("messageId") }
            .ifBlank { "${author}_${text}_${obj.optString("timestampUsec")}" }
        var isMember = false
        var isModerator = false
        var isOwner = false
        var memberBadge: String? = null
        obj.optJSONArray("authorBadges")?.let { badges ->
            for (i in 0 until badges.length()) {
                val badge = badges.optJSONObject(i)?.optJSONObject("liveChatAuthorBadgeRenderer") ?: continue
                val tooltip = badge.optString("tooltip")
                val tooltipLower = tooltip.lowercase()
                val custom = badge.optJSONObject("customThumbnail")?.thumbnails()?.lastOrNull()
                when {
                    tooltipLower.contains("member") || tooltipLower.contains("sponsor") -> {
                        isMember = true
                        if (memberBadge == null) memberBadge = custom
                    }
                    tooltipLower.contains("moderator") -> isModerator = true
                    tooltipLower.contains("owner") -> isOwner = true
                    custom != null && (badge.optString("icon").contains("MEMBER", ignoreCase = true)) -> {
                        isMember = true
                        if (memberBadge == null) memberBadge = custom
                    }
                }
            }
        }
        return LiveChatMessage(
            id = id,
            author = author,
            text = text,
            avatar = avatar,
            segments = if (segments.any { it.emoteUrl != null }) segments else emptyList(),
            isMember = isMember,
            isModerator = isModerator,
            isOwner = isOwner,
            memberBadgeUrl = memberBadge,
        )
    }
}

/**
 * Bottom-anchored shell for the chat panels, with the same mechanics as the
 * comments panel: it opens at the shared partial height under the player
 * header, drags between partial and full height, settles on release, and
 * dismisses from the close button, back press, or a downward fling. The
 * video behind the panel is never dimmed.
 */
@Composable
private fun ChatPanelScaffold(
    partialTop: Dp,
    onClose: () -> Unit,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val fullPx = with(density) { (maxHeight - statusTop - 8.dp).toPx() }
        val partialPx = with(density) { (maxHeight - partialTop).toPx() }.coerceIn(fullPx * 0.35f, fullPx)
        val height = remember { Animatable(partialPx) }
        val slide = remember { Animatable(1f) }
        LaunchedEffect(Unit) { if (reduced) slide.snapTo(0f) else slide.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 550f)) }
        fun close() = scope.launch {
            if (!reduced) slide.animateTo(1f, tween(240, easing = Motion.Emphasized))
            onClose()
        }
        BackHandler { close() }
        fun dragBy(dy: Float): Float {
            val before = height.value
            val after = (before - dy).coerceIn(partialPx * 0.25f, fullPx)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { height.snapTo(after) }
            return before - after
        }
        fun settle(velocity: Float) {
            val h = height.value
            when {
                h < partialPx * 0.7f || velocity > 1200f && h < partialPx - 1f || velocity > 1800f && h <= partialPx + 1f -> close()
                velocity < -1200f -> scope.launch { height.animateTo(fullPx, spring(dampingRatio = 0.86f, stiffness = 550f)) }
                velocity > 1200f -> scope.launch { height.animateTo(partialPx, spring(dampingRatio = 0.86f, stiffness = 550f)) }
                else -> {
                    val target = if (h > (partialPx + fullPx) / 2f) fullPx else partialPx
                    scope.launch { height.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 550f)) }
                }
            }
        }

        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { height.value.toDp() })
                .graphicsLayer { translationY = slide.value * size.height }
                .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
                .clip(shape)
                .background(c.surfaceContainerLow)
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .imePadding()
                .padding(bottom = 10.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = rememberDraggableState { dragBy(it) },
                        orientation = Orientation.Vertical,
                        onDragStopped = { v -> settle(v) },
                    )
                    .padding(top = 14.dp, bottom = 12.dp),
                Alignment.Center,
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.onSurfaceVariant.copy(alpha = 0.45f)))
            }
            content { close() }
        }
    }
}

@Composable
fun LiveChatPanel(
    state: LiveChatState,
    partialTop: Dp,
    onSignIn: () -> Unit,
    onClose: () -> Unit,
) {
    ChatPanelScaffold(partialTop = partialTop, onClose = onClose) { close ->
        LiveChatContent(state = state, onSignIn = onSignIn, onClose = close)
    }
}

@Composable
fun LiveChatSideColumn(
    state: LiveChatState,
    onSignIn: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .imePadding(),
    ) {
        LiveChatContent(state = state, onSignIn = onSignIn, onClose = onClose, modifier = Modifier.weight(1f))
    }
}

@Composable
fun LiveChatContent(
    state: LiveChatState,
    onSignIn: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Follow the tail only while the user is at (or near) the bottom
    // (0.1.1, note 47): scrolling up to read history stops the follow, and
    // the small arrow button resumes it.
    val atBottom by remember {
        derivedStateOf {
            val info = list.layoutInfo
            val last = info.totalItemsCount - 1
            last < 0 || (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= last - 2
        }
    }
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(atBottom) { if (atBottom) following = true }
    LaunchedEffect(state.messages.size, following) {
        if (following && state.messages.isNotEmpty()) {
            runCatching { list.animateScrollToItem(state.messages.lastIndex) }
        }
    }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (state.live) c.error else c.onSurfaceVariant),
            )
            Spacer(Modifier.width(8.dp))
            Text(I18n.t("live_chat", "Live chat"), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            state.viewerCount?.let { count ->
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Rounded.People,
                    contentDescription = null,
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(count, color = c.onSurfaceVariant, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(42.dp).pressable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    I18n.t("close_live_chat", "Close live chat"),
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.error != null && state.messages.isEmpty() -> Text(
                    state.error.orEmpty(),
                    color = c.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                )

                state.loading && state.messages.isEmpty() -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }

                state.messages.isEmpty() -> Text(
                    I18n.t("waiting_for_messages", "Waiting for messages…"),
                    color = c.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                )

                else -> {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            LiveChatRow(message)
                        }
                    }
                    if (!following && state.messages.isNotEmpty()) {
                        // Small, unobtrusive jump-to-latest button (note 47).
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 14.dp, bottom = 10.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(c.primary)
                                .pressable {
                                    following = true
                                    scope.launch {
                                        runCatching { list.animateScrollToItem(state.messages.lastIndex) }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                I18n.t("new_messages_below", "New messages"),
                                tint = c.onPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
        state.sendError?.let { message ->
            Text(
                message,
                color = c.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        if (Account.signedIn && state.membersOnly) {
            // Members-only chat: a blocked-style notice instead of the
            // composer (0.1.1, note 46), mirroring the signed-out bar.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.People,
                    contentDescription = null,
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    I18n.t("members_only_chat", "Members-only chat"),
                    color = c.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else if (Account.signedIn) {
            LiveChatComposer(state)
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.surfaceContainer)
                    .pressable { onSignIn() }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    I18n.t("sign_in_to_chat", "Sign in to chat"),
                    color = c.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveChatComposer(state: LiveChatState) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val view = LocalView.current
    val focus = LocalFocusManager.current
    val canSend = state.draft.isNotBlank() && !state.sending

    fun send() {
        if (!canSend) return
        focus.clearFocus(force = true)
        context.getSystemService(Context.INPUT_METHOD_SERVICE)
            ?.let { it as? InputMethodManager }
            ?.hideSoftInputFromWindow(view.windowToken, 0)
        state.send()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(c.surfaceContainer)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (state.draft.isEmpty()) {
                Text(I18n.t("chat_placeholder", "Chat…"), color = c.onSurfaceVariant, fontSize = 14.sp)
            }
            BasicTextField(
                value = state.draft,
                onValueChange = { state.draft = it },
                textStyle = TextStyle(color = c.onSurface, fontSize = 14.sp),
                cursorBrush = SolidColor(c.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(38.dp)
                .pressable(enabled = canSend) { send() }
                .clip(CircleShape)
                .background(if (canSend) c.primary else c.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (state.sending) {
                CircularProgressIndicator(
                    color = c.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    I18n.t("send_message", "Send message"),
                    tint = if (canSend) c.onPrimary else c.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveChatRow(message: LiveChatMessage) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        ChannelAvatar(message.avatar, message.author, size = 26)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message.author,
                    // Members chat in green; owner and moderator get their
                    // own tints (0.1.1, note 46).
                    color = when {
                        message.isOwner -> Color(0xFFF5A623)
                        message.isModerator -> Color(0xFF5E84F1)
                        message.isMember -> Color(0xFF2BA640)
                        else -> c.onSurfaceVariant
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                message.memberBadgeUrl?.let { badge ->
                    Spacer(Modifier.width(5.dp))
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(badge)
                            .crossfade(false)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (message.segments.isEmpty()) {
                Text(message.text, color = c.onSurface, fontSize = 13.5.sp, lineHeight = 18.sp)
            } else {
                // Emote runs render as inline images inside the text. Each
                // emote occurrence gets its own placeholder id so a message
                // with several different emotes renders each one correctly.
                val body = remember(message.id, message.segments) {
                    buildAnnotatedString {
                        message.segments.forEachIndexed { index, segment ->
                            if (segment.emoteUrl != null) {
                                appendInlineContent("chatEmote$index", "emote")
                            } else {
                                withStyle(SpanStyle(color = c.onSurface)) { append(segment.text) }
                            }
                        }
                    }
                }
                val inline = remember(message.segments) {
                    message.segments.mapIndexedNotNull { index, segment ->
                        val url = segment.emoteUrl ?: return@mapIndexedNotNull null
                        "chatEmote$index" to androidx.compose.ui.text.InlineTextContent(
                            Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.TextCenter),
                        ) {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(context)
                                    .data(url)
                                    .crossfade(false)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }.toMap()
                }
                Text(body, inlineContent = inline, fontSize = 13.5.sp, lineHeight = 18.sp)
            }
        }
    }
}


/**
 * Read-only replay of a past stream's chat. Messages appear as the playhead
 * reaches the offset they were sent at, and the panel keeps the buffer
 * filled ahead of the playhead while it is open.
 */
@Composable
fun ChatReplayPanel(
    state: ChatReplayState,
    positionMs: Long,
    partialTop: Dp,
    onClose: () -> Unit,
) {
    ChatPanelScaffold(partialTop = partialTop, onClose = onClose) { close ->
        ChatReplayContent(state = state, positionMs = positionMs, onClose = close)
    }
}

@Composable
fun ChatReplaySideColumn(
    state: ChatReplayState,
    positionMs: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .imePadding(),
    ) {
        ChatReplayContent(state = state, positionMs = positionMs, onClose = onClose, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ChatReplayContent(
    state: ChatReplayState,
    positionMs: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    val visible = state.messages.filter { (it.offsetMs ?: 0L) <= positionMs }
    val dragged by list.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(positionMs) { state.ensureBuffered(positionMs) }
    LaunchedEffect(visible.size, dragged) {
        if (visible.isNotEmpty() && !dragged) {
            runCatching { list.animateScrollToItem(visible.lastIndex) }
        }
    }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(I18n.t("chat_replay", "Chat replay"), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(42.dp).pressable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    I18n.t("close_chat_replay", "Close chat replay"),
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.loading && visible.isEmpty() -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }

                visible.isEmpty() -> Text(
                    I18n.t("waiting_for_messages", "Waiting for messages…"),
                    color = c.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                )

                else -> LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(visible, key = { it.id }) { message ->
                        LiveChatRow(message)
                    }
                }
            }
        }
    }
}
