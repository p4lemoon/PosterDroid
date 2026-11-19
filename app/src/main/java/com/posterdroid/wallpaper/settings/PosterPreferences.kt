package com.posterdroid.wallpaper.settings

import android.content.Context
import android.content.SharedPreferences
import com.posterdroid.core.PosterConstants

class PosterPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PosterConstants.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var activeTendiesPath: String?
        get() = prefs.getString(PosterConstants.PREF_ACTIVE_TENDIES_PATH, null)
        set(value) = prefs.edit().putString(PosterConstants.PREF_ACTIVE_TENDIES_PATH, value).apply()

    var activeTendiesName: String
        get() = prefs.getString(PosterConstants.PREF_ACTIVE_TENDIES_NAME, "default.tendies") ?: "default.tendies"
        set(value) = prefs.edit().putString(PosterConstants.PREF_ACTIVE_TENDIES_NAME, value).apply()

    var isEffectEnabled: Boolean
        get() = prefs.getBoolean(PosterConstants.PREF_EFFECT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(PosterConstants.PREF_EFFECT_ENABLED, value).apply()

    var isSmoothingEnabled: Boolean
        get() = prefs.getBoolean(PosterConstants.PREF_SMOOTHING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(PosterConstants.PREF_SMOOTHING_ENABLED, value).apply()

    var sensitivity: Float
        get() = prefs.getFloat(PosterConstants.PREF_SENSITIVITY, 1.0f)
        set(value) = prefs.edit().putFloat(PosterConstants.PREF_SENSITIVITY, value).apply()

    var targetFps: Int
        get() = prefs.getInt(PosterConstants.PREF_TARGET_FPS, PosterConstants.DEFAULT_TARGET_FPS)
        set(value) = prefs.edit().putInt(PosterConstants.PREF_TARGET_FPS, value).apply()

    val randomFunnyString: String
        get() = PosterConstants.FUNNY_STRINGS.random()
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
