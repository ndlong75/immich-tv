package com.immichtv.util

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {

    private const val PREFS_NAME = "immich_tv_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_SLIDESHOW_INTERVAL = "slideshow_interval"
    private const val KEY_SHOW_INFO_OVERLAY = "show_info_overlay"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var accessToken: String
        get() = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var userEmail: String
        get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var slideshowIntervalSeconds: Int
        get() = prefs.getInt(KEY_SLIDESHOW_INTERVAL, 5)
        set(value) = prefs.edit().putInt(KEY_SLIDESHOW_INTERVAL, value).apply()

    var showInfoOverlay: Boolean
        get() = prefs.getBoolean(KEY_SHOW_INFO_OVERLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_INFO_OVERLAY, value).apply()

    fun isConfigured(): Boolean {
        return serverUrl.isNotBlank() && accessToken.isNotBlank()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
