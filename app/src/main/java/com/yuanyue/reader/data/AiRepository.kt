package com.yuanyue.reader.data

import com.yuanyue.reader.model.RssArticleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiRepository(
    client: OkHttpClient,
    private val settingsRepository: SettingsRepository,
) {
    private val client = client.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun summarizeRss(articles: List<RssArticleEntity>): Result<String> {
        if (articles.isEmpty()) return Result.failure(IllegalArgumentException("没有可摘要的资讯"))
        val source = articles.take(15).joinToString("\n\n") { article ->
            buildString {
                append("标题：").append(article.title)
                article.author?.takeIf { it.isNotBlank() }?.let { append("\n作者：").append(it) }
                article.description?.takeIf { it.isNotBlank() }?.let {
                    append("\n摘要：").append(it.take(500))
                }
            }
        }
        val prompt = """
            你是一名中文资讯编辑。请阅读以下 RSS 条目，用简洁、克制、有信息密度的中文写一段 120–200 字的今日摘要。
            先归纳共同主题，再点出 2–3 条最值得关注的信息。不要逐条复述，不要使用营销语气，不要编造事实。
            直接输出摘要正文，不要标题。

            $source
        """.trimIndent()
        return chat(prompt)
    }

    suspend fun companion(chapterTitle: String, chapterText: String, question: String): Result<String> {
        val excerpt = chapterText.take(6000)
        val prompt = """
            你是一名耐心、克制的中文阅读伴读。下面是读者正在读的章节，请基于原文回答，不要编造原文没有的信息。
            回答控制在 200 字以内；如果原文不足以回答，请直接说明。
            章节：$chapterTitle
            原文：
            $excerpt

            读者的问题：$question
        """.trimIndent()
        return chat(prompt)
    }

    private suspend fun chat(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsRepository.currentAiSettings()
            if (!settings.isConfigured) {
                throw IllegalStateException("还没有配置 AI API Key")
            }
            val baseUrl = settings.baseUrl.trimEnd('/')
            val endpoint = if (baseUrl.endsWith("/chat/completions")) {
                baseUrl
            } else {
                "$baseUrl/chat/completions"
            }
            val body = JSONObject().apply {
                put("model", settings.model)
                put("temperature", 0.6)
                put("max_tokens", 900)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        },
                    ),
                )
            }.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer ${settings.apiKey}")
                .header("Content-Type", "application/json")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        JSONObject(text).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    throw IllegalStateException(
                        message?.takeIf { it.isNotBlank() } ?: "AI 请求失败：HTTP ${response.code}",
                    )
                }
                JSONObject(text)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("AI 没有返回内容")
            }
        }
    }
}
