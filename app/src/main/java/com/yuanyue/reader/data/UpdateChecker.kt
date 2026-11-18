package com.yuanyue.reader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val version: String,
    val url: String,
    val notes: String,
)

class UpdateChecker(client: OkHttpClient) {
    private val client = client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/XSimplxe/OriginRead/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "YuanyueReader")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = JSONObject(response.body?.string().orEmpty())
                val tag = root.optString("tag_name").trim()
                val url = root.optString("html_url").trim()
                if (tag.isBlank() || url.isBlank()) return@use null
                if (!isNewer(tag, currentVersion)) return@use null
                UpdateInfo(
                    version = tag,
                    url = url,
                    notes = root.optString("body").trim().take(600),
                )
            }
        }.getOrNull()
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = versionParts(remote)
        val currentParts = versionParts(current)
        val size = maxOf(remoteParts.size, currentParts.size)
        for (index in 0 until size) {
            val remoteValue = remoteParts.getOrElse(index) { 0 }
            val currentValue = currentParts.getOrElse(index) { 0 }
            if (remoteValue != currentValue) return remoteValue > currentValue
        }
        return false
    }

    private fun versionParts(value: String): List<Int> =
        value.removePrefix("v")
            .substringBefore('-')
            .split('.')
            .mapNotNull { it.toIntOrNull() }
}
