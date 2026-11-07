package com.leplay.app

import android.content.Context
import android.util.Base64
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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

@Immutable
data class LiveChatMessage(
    val id: String,
    val author: String,
    val text: String,
    val avatar: String? = null,
    val pending: Boolean = false,
    val offsetMs: Long? = null,
)

internal data class LiveChatPage(
    val messages: List<LiveChatMessage>,
    val next: String?,
    val sendParams: String? = null,
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
        job = scope.launch {
            try {
                val start: LiveChatStart = withContext(Dispatchers.IO) { LiveChatFeed.start(id) }
                    ?: run {
                        error = "Live chat isn't available for this stream."
                        loading = false
                        return@launch
                    }
                if (channelId == null) channelId = start.channelId
                if (sendParams == null) sendParams = start.sendParams
                var cursor = start.continuation
                while (isActive) {
                    val page = withContext(Dispatchers.IO) { LiveChatFeed.page(cursor) }
                    if (page.messages.isNotEmpty()) {
                        val seen = messages.mapTo(HashSet()) { it.id }
                        messages = (messages + page.messages.filter { seen.add(it.id) }).takeLast(220)
                    }
                    if (sendParams == null) sendParams = page.sendParams
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
            sendError = "Sign in to chat."
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
                        author = profile?.name ?: "You",
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
        root.walk { key, obj ->
            when (key) {
                "liveChatRenderer" -> {
                    if (token == null) token = firstContinuation(obj.optJSONArray("continuations"))
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
        )
    }

    internal fun page(cursor: String): LiveChatPage {
        val root = InnerTube.postAny("live_chat/get_live_chat", JSONObject().put("continuation", cursor))
        val out = ArrayList<LiveChatMessage>()
        var next: String? = null
        var sendParams: String? = null
        root.walk { key, obj ->
            when (key) {
                "liveChatTextMessageRenderer",
                "liveChatPaidMessageRenderer",
                "liveChatMembershipItemRenderer",
                -> parseMessage(obj)?.let(out::add)

                "liveChatContinuation" -> {
                    next = firstContinuation(obj.optJSONArray("continuations")) ?: next
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
        return LiveChatPage(out.distinctBy { it.id }, next, sendParams)
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
            ?: throw IOException("Chat isn't ready yet. Try again in a moment.")
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
            val msg = err?.optString("message") ?: "YouTube could not send your message"
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
        val text = obj.text("message").orEmpty().trim()
        if (author.isBlank() || text.isBlank()) return null
        val avatar = obj.optJSONObject("authorPhoto")?.thumbnails()?.lastOrNull()
        val id = obj.optString("id").ifBlank { obj.optString("messageId") }
            .ifBlank { "${author}_${text}_${obj.optString("timestampUsec")}" }
        return LiveChatMessage(id = id, author = author, text = text, avatar = avatar)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveChatPanel(
    state: LiveChatState,
    onSignIn: () -> Unit,
    onClose: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) runCatching { list.animateScrollToItem(state.messages.lastIndex) }
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 560.dp)
                .imePadding()
                .padding(bottom = 10.dp),
        ) {
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
                Text("Live chat", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(42.dp).pressable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        "Close live chat",
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
                        "Waiting for messages…",
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
                        items(state.messages, key = { it.id }) { message ->
                            LiveChatRow(message)
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
            if (Account.signedIn) {
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
                        "Sign in to chat",
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
                Text("Chat…", color = c.onSurfaceVariant, fontSize = 14.sp)
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
                    "Send message",
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        ChannelAvatar(message.avatar, message.author, size = 26)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                message.author,
                color = c.onSurfaceVariant,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(message.text, color = c.onSurface, fontSize = 13.5.sp, lineHeight = 18.sp)
        }
    }
}

/**
 * Read-only replay of a past stream's chat. Messages appear as the playhead
 * reaches the offset they were sent at, and the panel keeps the buffer
 * filled ahead of the playhead while it is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatReplayPanel(
    state: ChatReplayState,
    positionMs: Long,
    onClose: () -> Unit,
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
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 560.dp)
                .imePadding()
                .padding(bottom = 10.dp),
        ) {
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
                        "Close chat replay",
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
                        "Waiting for messages…",
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
}
