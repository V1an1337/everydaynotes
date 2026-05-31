package xyz.v1an.everydaynotes

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("everydaynotes", Context.MODE_PRIVATE)

    var apiBase: String
        get() = prefs.getString(KEY_API_BASE, DEFAULT_API_BASE) ?: DEFAULT_API_BASE
        set(value) = prefs.edit().putString(KEY_API_BASE, value.trim().trimEnd('/')).apply()

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        const val DEFAULT_API_BASE = "https://notes.v1an.xyz/api"
        private const val KEY_API_BASE = "api_base"
        private const val KEY_TOKEN = "token"
    }
}

