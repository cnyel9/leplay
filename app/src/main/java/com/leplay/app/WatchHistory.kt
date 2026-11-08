package com.leplay.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, on-device watch history for videos, music and Shorts.
 *
 * Until 0.0.8 the app kept no local history at all: the Collection tab
 * showed only the signed-in server feed (FEhistory), Shorts were never
 * recorded anywhere, and signed-out users saw no history section at all.
 * This store mirrors the RecentMusic pattern (SharedPreferences + JSON)
 * but keeps every kind in one list with a kind tag per entry, most
 * recent first, and exposes the list as Compose state so the History
 * page updates in realtime while the user watches.
 */
object WatchHistory {
    const val KIND_VIDEO = 0
    const val KIND_MUSIC = 1
    const val KIND_SHORT = 2

    private const val PREFS = "watch_history"
    private const val KEY = "entries"
    private const val MAX = 200

    private val prefs by lazy {
        LePlayApplication.appContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
    }

    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set

    /** id -> KIND_* for entries recorded locally; server items have no entry here. */
    var kinds by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        val raw = prefs.getString(KEY, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            val out = ArrayList<VideoItem>(array.length())
            val kindMap = HashMap<String, Int>()
            for (i in 0 until array.length()) {
                val json = array.optJSONObject(i) ?: continue
                val id = json.optString("id")
                if (id.isBlank()) continue
                out += VideoItem(
                    id = id,
                    title = json.optString("title"),
                    channel = json.optString("channel"),
                    channelUrl = null,
                    thumbnail = json.optString("thumb").takeIf { it.isNotBlank() && it != "null" },
                    avatar = null,
                    durationSec = json.optLong("duration"),
                    views = -1,
                    uploaded = "",
                )
                kindMap[id] = json.optInt("kind", KIND_VIDEO)
            }
            items = out
            kinds = kindMap
        }
    }

    fun record(video: VideoItem, kind: Int) {
        if (video.id.isBlank() || video.id.length != 11) return
        load()
        val fresh = video.copy(localUri = null)
        items = (listOf(fresh) + items.filter { it.id != fresh.id }).take(MAX)
        kinds = kinds + (fresh.id to kind)
        runCatching {
            val array = JSONArray()
            items.forEach { item ->
                array.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("title", item.title)
                        .put("channel", item.channel)
                        .put("thumb", item.thumbnail ?: "")
                        .put("duration", item.durationSec)
                        .put("kind", kinds[item.id] ?: KIND_VIDEO),
                )
            }
            prefs.edit().putString(KEY, array.toString()).apply()
        }
    }

    /** Kind for any history item: the locally recorded tag wins; server-only
     *  items fall back to the music heuristic, then to a short-duration guess. */
    fun kindOf(item: VideoItem): Int = kinds[item.id] ?: when {
        isMusicTrack(item) -> KIND_MUSIC
        item.durationSec in 1..62 -> KIND_SHORT
        else -> KIND_VIDEO
    }
}
