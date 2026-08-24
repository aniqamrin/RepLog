package com.replog.app.data.ai

import com.replog.app.data.remote.ChatMessageDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectAiClient @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        apiKey: String,
        baseUrl: String,
        model: String,
        history: List<Pair<String, String>>,
        question: String,
        context: String
    ): Result<String> {
        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", AiPromptFactory.SYSTEM_PROMPT)
            })
            add(buildJsonObject {
                put("role", "system")
                put("content", "User data context:\n$context")
            })
            history.takeLast(8).forEach { (role, content) ->
                add(buildJsonObject {
                    put("role", if (role == "assistant") "assistant" else "user")
                    put("content", content)
                })
            }
            add(buildJsonObject {
                put("role", "user")
                put("content", question)
            })
        }
        return execute(baseUrl, model, messages, apiKey)
    }

    suspend fun vision(
        apiKey: String,
        baseUrl: String,
        model: String,
        imageBase64: String,
        prompt: String
    ): Result<String> {
        val clean = imageBase64.replace(Regex("^data:image/\\w+;base64,"), "")
        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", prompt)
                    })
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject {
                            put("url", "data:image/jpeg;base64,$clean")
                            put("detail", "low")
                        })
                    })
                })
            })
        }
        return execute(baseUrl, model, messages, apiKey)
    }

    private suspend fun execute(
        baseUrl: String,
        model: String,
        messages: kotlinx.serialization.json.JsonArray,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("model", model)
                put("messages", messages)
            }.toString()
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        "AI provider error ${response.code}: ${text.take(200).ifBlank { response.message }}"
                    )
                }
                val parsed = json.parseToJsonElement(text)
                    .jsonObjectOrNull()?.get("choices")?.jsonArrayOrNull()
                    ?.firstOrNull()?.jsonObjectOrNull()
                    ?.get("message")?.jsonObjectOrNull()
                    ?.get("content")?.toString()
                    ?.removeSurrounding("\"")
                    ?.trim()
                if (parsed.isNullOrBlank()) throw IllegalStateException("Empty AI reply")
                parsed
            }
        }
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull() =
        this as? kotlinx.serialization.json.JsonObject

    private fun kotlinx.serialization.json.JsonElement.jsonArrayOrNull() =
        this as? kotlinx.serialization.json.JsonArray
}
