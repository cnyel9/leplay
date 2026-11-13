package com.leplay.app

import android.content.Context
import android.util.Log
import com.leplay.app.potoken.PoTokenGenerator
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult

/**
 * Persistent guest session id for PoToken generation (0.1.2, note 53
 * item 3). Previously every call invented a fresh "guest_<now>" id, and
 * because the generator recreates its whole WebView BotGuard session
 * whenever the session id changes, signed-out users paid a full WebView
 * recreation for EVERY video. The id is created once, stored, and reused
 * so the streaming token stays cached across videos.
 */
object GuestSession {
    private const val PREFS = "guest_session"
    private const val KEY = "id"

    @Volatile
    private var cached: String? = null

    fun id(): String {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val prefs = LePlayApplication.appContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stored = prefs.getString(KEY, null)
            val value = if (!stored.isNullOrBlank()) {
                stored
            } else {
                ("guest_" + System.currentTimeMillis()).also {
                    prefs.edit().putString(KEY, it).apply()
                }
            }
            cached = value
            return value
        }
    }
}

class LePlayPoTokenProvider(private val generator: PoTokenGenerator) : PoTokenProvider {
    companion object {
        private const val TAG = "LePlayPoTokenProvider"
    }

    private fun resolvePoToken(videoId: String?): PoTokenResult? {
        videoId ?: return null
        return try {
            val sessionId = Account.visitorData() ?: GuestSession.id()
            val res = generator.getWebClientPoToken(videoId, sessionId) ?: return null
            PoTokenResult(
                sessionId,
                res.playerRequestPoToken,
                res.streamingDataPoToken
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate PoToken for $videoId: ${e.message}")
            null
        }
    }

    override fun getWebClientPoToken(videoId: String?): PoTokenResult? = resolvePoToken(videoId)

    override fun getWebEmbedClientPoToken(videoId: String?): PoTokenResult? = resolvePoToken(videoId)

    override fun getAndroidClientPoToken(videoId: String?): PoTokenResult? = resolvePoToken(videoId)

    override fun getIosClientPoToken(videoId: String?): PoTokenResult? = resolvePoToken(videoId)
}
