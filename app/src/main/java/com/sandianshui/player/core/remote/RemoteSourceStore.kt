package com.sandianshui.player.core.remote

import android.content.Context
import android.content.SharedPreferences

/**
 * 在线音源接口地址存储。
 *
 * 本应用**不内置任何在线音源接口地址**，需要用户自行填写与所用 JSON API 兼容的
 * 根地址（base URL）。地址仅保存在本机 SharedPreferences，不会随应用分发或上传。
 */
class RemoteSourceStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 在线音源根地址（不带末尾斜杠）；未配置时为空串。 */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "")?.trim().orEmpty()
        set(value) {
            prefs.edit().putString(KEY_BASE_URL, normalize(value)).apply()
        }

    /** 是否已配置接口地址。 */
    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    companion object {
        private const val PREFS_NAME = "remote_source"
        private const val KEY_BASE_URL = "base_url"

        /** 去除首尾空白并去掉末尾斜杠，保证后续拼接路径正确。 */
        fun normalize(value: String): String {
            var v = value.trim()
            while (v.endsWith("/")) v = v.dropLast(1)
            return v
        }
    }
}
