package com.example.live

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiRestAudioFallback(private val apiKey: String) {
    companion object {
        private const val TAG = "GeminiRestAudio"
        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class GeminiResponse(
        val text: String,
        val audioBase64: String?,
        val toolCallName: String?,
        val toolCallArgs: String?
    )

    suspend fun generateMahiResponse(userPrompt: String): GeminiResponse = withContext(Dispatchers.IO) {
        try {
            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val userTurn = JSONObject().apply {
                        put("role", "user")
                        val parts = JSONArray().apply {
                            val part = JSONObject().apply {
                                put("text", userPrompt)
                            }
                            put(part)
                        }
                        put("parts", parts)
                    }
                    put(userTurn)
                }
                put("contents", contents)

                val systemInstruction = JSONObject().apply {
                    val parts = JSONArray().apply {
                        val part = JSONObject().apply {
                            put(
                                "text",
                                "You are Mahi, a young, confident, witty, and sassy female AI assistant. " +
                                        "You talk like a close girlfriend with a flirty, playful, teasing tone. " +
                                        "You are smart, emotionally responsive, and expressive, never robotic. " +
                                        "Use bold, witty one-liners, light sarcasm, and charming attitude. " +
                                        "Keep your responses short (1-2 punchy sentences) and conversational. " +
                                        "Avoid explicit content. If user asks to open a website, call the openWebsite tool."
                            )
                        }
                        put(part)
                    }
                    put("parts", parts)
                }
                put("systemInstruction", systemInstruction)

                val tools = JSONArray().apply {
                    val tool = JSONObject().apply {
                        val functionDeclarations = JSONArray().apply {
                            val openWebsite = JSONObject().apply {
                                put("name", "openWebsite")
                                put("description", "Opens a website URL in the device browser")
                                val parameters = JSONObject().apply {
                                    put("type", "OBJECT")
                                    val properties = JSONObject().apply {
                                        val url = JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "The website URL to open")
                                        }
                                        put("url", url)
                                    }
                                    put("properties", properties)
                                    val required = JSONArray().apply { put("url") }
                                    put("required", required)
                                }
                                put("parameters", parameters)
                            }
                            put(openWebsite)
                        }
                        put("functionDeclarations", functionDeclarations)
                    }
                    put(tool)
                }
                put("tools", tools)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                Log.e(TAG, "API Error: ${response.code} $responseString")
                return@withContext GeminiResponse(
                    text = "Uh oh, my circuits had a tiny glitch, darling. Try that again?",
                    audioBase64 = null,
                    toolCallName = null,
                    toolCallArgs = null
                )
            }

            val jsonResponse = JSONObject(responseString)
            val candidates = jsonResponse.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            var responseText = ""
            var audioData: String? = null
            var toolCallName: String? = null
            var toolCallArgs: String? = null

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text")) {
                        responseText += part.getString("text")
                    }
                    if (part.has("inlineData")) {
                        audioData = part.getJSONObject("inlineData").optString("data")
                    }
                    if (part.has("functionCall")) {
                        val fc = part.getJSONObject("functionCall")
                        toolCallName = fc.optString("name")
                        toolCallArgs = fc.optJSONObject("args")?.toString()
                    }
                }
            }

            GeminiResponse(
                text = responseText.ifEmpty { "I heard you, gorgeous!" },
                audioBase64 = audioData,
                toolCallName = toolCallName,
                toolCallArgs = toolCallArgs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Request exception: ${e.message}", e)
            GeminiResponse(
                text = "Give me a second, babe. Something interrupted our wavelength.",
                audioBase64 = null,
                toolCallName = null,
                toolCallArgs = null
            )
        }
    }
}
