package com.leplay.app

import android.util.Log
import com.leplay.app.potoken.PoTokenGenerator
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult

class LePlayPoTokenProvider(private val generator: PoTokenGenerator) : PoTokenProvider {
    companion object {
        private const val TAG = "LePlayPoTokenProvider"
    }

    private fun resolvePoToken(videoId: String?): PoTokenResult? {
        videoId ?: return null
        return try {
            val sessionId = Account.visitorData() ?: ("guest_" + System.currentTimeMillis())
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
