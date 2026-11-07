package com.leplay.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object PlayerControlSettings {
    private const val KEY_ENABLED = "show_quick_speed_quality"
    private var prefs: SharedPreferences? = null

    var showQuickControls by mutableStateOf(true)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        showQuickControls = prefs?.getBoolean(KEY_ENABLED, true) ?: true
    }

    fun set(on: Boolean) {
        showQuickControls = on
        prefs?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
    }
}

object PipSettings {
    private const val KEY_ENABLED = "app_pip_enabled"
    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean(KEY_ENABLED, true) ?: true
    }

    fun set(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
    }
}

object MiniPlayerSettings {
    private const val KEY_USE_LIQUID = "use_liquid_mini_player"
    private var prefs: SharedPreferences? = null

    var useLiquidMiniPlayer by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        useLiquidMiniPlayer = prefs?.getBoolean(KEY_USE_LIQUID, false) ?: false
    }

    fun set(on: Boolean) {
        useLiquidMiniPlayer = on
        prefs?.edit()?.putBoolean(KEY_USE_LIQUID, on)?.apply()
    }
}

object ShortsSettings {
    private const val KEY_CLEAN_MODE = "shorts_clean_mode"
    private var prefs: SharedPreferences? = null

    var cleanMode by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        cleanMode = prefs?.getBoolean(KEY_CLEAN_MODE, false) ?: false
    }

    fun set(on: Boolean) {
        cleanMode = on
        prefs?.edit()?.putBoolean(KEY_CLEAN_MODE, on)?.apply()
    }
}
