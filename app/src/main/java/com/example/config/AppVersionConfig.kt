package com.example.config

import android.content.Context
import com.example.BuildConfig

/**
 * Single source of truth for application version, database schema version,
 * changelog persistence, and optional remote update check configuration.
 */
object AppVersionConfig {
    val versionCode: Int get() = BuildConfig.VERSION_CODE
    val versionName: String get() = BuildConfig.VERSION_NAME
    const val DB_SCHEMA_VERSION: Int = 10

    /**
     * Remote update endpoint pointing to the hosted version.json file:
     * {
     *   "latestVersionCode": 3,
     *   "latestVersionName": "1.2.0",
     *   "downloadUrl": "https://...",
     *   "changes": ["...", "..."]
     * }
     */
    const val UPDATE_CHECK_URL: String =
        "https://raw.githubusercontent.com/Abdul786827/financepro-update/refs/heads/main/version.json"

    val formattedVersion: String
        get() = "Version $versionName (build $versionCode)"

    private const val PREFS_NAME = "finance_pro_version_prefs"
    private const val KEY_LAST_SEEN_VERSION_CODE = "last_seen_version_code"

    fun getLastSeenVersionCode(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_LAST_SEEN_VERSION_CODE, 0)
    }

    fun markCurrentVersionSeen(context: Context, versionCodeToMark: Int = versionCode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_LAST_SEEN_VERSION_CODE, versionCodeToMark).apply()
    }

    fun shouldShowWhatsNew(context: Context, currentCode: Int = versionCode): Boolean {
        return currentCode > getLastSeenVersionCode(context)
    }
}
