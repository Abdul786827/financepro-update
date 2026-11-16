package com.example.util

import com.example.config.AppVersionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class RemoteVersionInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val downloadUrl: String,
    val changes: List<String>
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: RemoteVersionInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersionName: String, val currentVersionCode: Int) : UpdateCheckResult()
    data class NotConfigured(val message: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object AppUpdateChecker {
    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun checkForUpdate(
        feedUrl: String = AppVersionConfig.UPDATE_CHECK_URL,
        currentVersionCode: Int = AppVersionConfig.versionCode,
        currentVersionName: String = AppVersionConfig.versionName,
        client: OkHttpClient = defaultClient
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val cleanUrl = feedUrl.trim()
        if (cleanUrl.isBlank()) {
            return@withContext UpdateCheckResult.NotConfigured(
                "You are running $currentVersionName (build $currentVersionCode). Remote update server is not configured yet."
            )
        }
        return@withContext try {
            val request = Request.Builder()
                .url(cleanUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Error(
                        "Update server returned HTTP ${response.code}. Please try again later."
                    )
                }
                val body = response.body?.string().orEmpty()
                evaluateJsonPayload(
                    jsonString = body,
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName
                )
            }
        } catch (_: IOException) {
            UpdateCheckResult.Error(
                "No internet connection or unable to reach the update server. Please check your connection and try again."
            )
        } catch (e: Exception) {
            UpdateCheckResult.Error(
                e.message?.takeIf { it.isNotBlank() }
                    ?: "Unable to check for updates right now. Please try again later."
            )
        }
    }

    fun parseVersionJson(jsonString: String): RemoteVersionInfo {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Empty update response received from server.")
        }
        val obj = try {
            JSONObject(trimmed)
        } catch (_: JSONException) {
            throw IllegalArgumentException("Invalid JSON format in update response.")
        }
        val latestCode = obj.optInt("latestVersionCode", -1)
        if (latestCode <= 0) {
            throw IllegalArgumentException("Invalid version information in update response.")
        }
        val rawName = obj.optString("latestVersionName", "").trim()
        val latestName = rawName.ifBlank { "build $latestCode" }
        val downloadUrl = obj.optString("downloadUrl", "").trim()
        val changesArray = obj.optJSONArray("changes") ?: JSONArray()
        val changes = buildList {
            for (i in 0 until changesArray.length()) {
                val item = changesArray.optString(i, "").trim()
                if (item.isNotEmpty()) add(item)
            }
        }
        return RemoteVersionInfo(
            latestVersionCode = latestCode,
            latestVersionName = latestName,
            downloadUrl = downloadUrl,
            changes = changes
        )
    }

    fun evaluateJsonPayload(
        jsonString: String,
        currentVersionCode: Int = AppVersionConfig.versionCode,
        currentVersionName: String = AppVersionConfig.versionName
    ): UpdateCheckResult {
        return try {
            val info = parseVersionJson(jsonString)
            if (info.latestVersionCode > currentVersionCode) {
                UpdateCheckResult.UpdateAvailable(info)
            } else {
                UpdateCheckResult.UpToDate(
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode
                )
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(
                e.message?.takeIf { it.isNotBlank() } ?: "Invalid update data received from server."
            )
        }
    }
}
