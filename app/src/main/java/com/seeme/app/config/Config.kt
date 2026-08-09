package com.seeme.app.config

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.UUID

/** 应用配置：服务器地址、Token、设备 ID（持久化） */
object Config {
    private const val PREFS = "seeme_config"
    private const val KEY_URL = "server_url"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_DEVICE_ID = "device_id"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun serverUrl(context: Context): String =
        prefs(context).getString(KEY_URL, "").orEmpty()

    fun saveServerUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_URL, url.trim().trimEnd('/')).apply()
    }

    fun authToken(context: Context): String =
        prefs(context).getString(KEY_TOKEN, "").orEmpty()

    fun saveAuthToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    fun deviceId(context: Context): String {
        val p = prefs(context)
        var id = p.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            p.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    fun isConfigured(context: Context): Boolean =
        serverUrl(context).isNotBlank() && authToken(context).isNotBlank()
}
