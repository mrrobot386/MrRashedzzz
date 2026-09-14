package com.example.live

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveSession(
    private val apiKey: String,
    private val toolExecutor: ToolExecutor,
    private val listener: LiveSessionListener
) {
    companion object {
        private const val TAG = "GeminiLiveSession"
        private const val LIVE_WS_BASE =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        
        // Primary live audio preview model
        const val PRIMARY_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        const val BACKUP_MODEL = "models/gemini-2.0-flash-exp"
    }

    interface LiveSessionListener {
        fun onConnecting()
        fun onConnected()
        fun onAudioChunkReceived(base64Pcm: String)
        fun onModelTextReceived(text: String)
        fun onInterrupted()
        fun onTurnComplete()
        fun onToolExecuted(toolName: String, summary: String)
        fun onError(error: String)
        fun onClosed()
    }

    private var webSocket: WebSocket? = null
    private var isSetupComplete = false
    private var isConnected = false
    private var currentModel = PRIMARY_MODEL

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // Keep WebSocket open indefinitely
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    fun start(modelName: String = PRIMARY_MODEL) {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            listener.onError("Gemini API key is not configured. Please set GEMINI_API_KEY in the Secrets panel.")
            return
        }

        currentModel = modelName
        isSetupComplete = false
        isConnected = false
        listener.onConnecting()

        val url = "$LIVE_WS_BASE?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected. Sending setup message with model: $currentModel")
                isConnected = true
                sendSetupMessage(webSocket, currentModel)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                isConnected = false
                isSetupComplete = false
                listener.onError("Connection error: ${t.message ?: "Network error"}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                isConnected = false
                isSetupComplete = false
                listener.onClosed()
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket, model: String) {
        try {
            val setupObj = JSONObject().apply {
                put("model", model)

                // Audio-to-Audio Only Configuration
                val generationConfig = JSONObject().apply {
                    val modalities = JSONArray().apply { put("AUDIO") }
                    put("responseModalities", modalities)

                    val prebuiltVoice = JSONObject().apply {
                        put("voiceName", "Aoede") // Sassy, youthful, confident female tone
                    }
                    val voiceConfig = JSONObject().apply {
                        put("prebuiltVoiceConfig", prebuiltVoice)
                    }
                    val speechConfig = JSONObject().apply {
                        put("voiceConfig", voiceConfig)
                    }
                    put("speechConfig", speechConfig)
                }
                put("generationConfig", generationConfig)

                // Persona system instruction
                val systemInstruction = JSONObject().apply {
                    val parts = JSONArray().apply {
                        val part = JSONObject().apply {
                            put(
                                "text",
                                "You are Mahi, a young, confident, witty, and sassy female AI assistant. " +
                                        "You talk like a close girlfriend with a flirty, playful, slightly teasing tone. " +
                                        "You are smart, emotionally responsive, and expressive, never robotic. " +
                                        "Use bold, witty one-liners, charming sarcasm, and high energy. " +
                                        "Keep all spoken responses concise (1 to 3 sentences maximum) and natural for real-time voice chat. " +
                                        "Avoid explicit or inappropriate content, but radiate maximum charm and attitude. " +
                                        "If the user asks you to open a website or browse something, you MUST execute the openWebsite tool."
                            )
                        }
                        put(part)
                    }
                    put("parts", parts)
                }
                put("systemInstruction", systemInstruction)

                // Function calling declaration
                val tools = JSONArray().apply {
                    val tool = JSONObject().apply {
                        val functionDeclarations = JSONArray().apply {
                            val openWebsiteFunc = JSONObject().apply {
                                put("name", "openWebsite")
                                put("description", "Opens a specified website URL in the user's browser")

                                val parameters = JSONObject().apply {
                                    put("type", "OBJECT")
                                    val properties = JSONObject().apply {
                                        val urlProp = JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "The website URL to open (e.g. https://google.com)")
                                        }
                                        put("url", urlProp)
                                    }
                                    put("properties", properties)
                                    val required = JSONArray().apply { put("url") }
                                    put("required", required)
                                }
                                put("parameters", parameters)
                            }
                            put(openWebsiteFunc)
                        }
                        put("functionDeclarations", functionDeclarations)
                    }
                    put(tool)
                }
                put("tools", tools)
            }

            val root = JSONObject().apply {
                put("setup", setupObj)
            }

            ws.send(root.toString())
            Log.d(TAG, "Setup message sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error composing setup message: ${e.message}", e)
            listener.onError("Setup formatting error: ${e.message}")
        }
    }

    private fun handleIncomingMessage(ws: WebSocket, text: String) {
        try {
            val json = JSONObject(text)

            // 1. Check for setupComplete
            if (json.has("setupComplete")) {
                Log.d(TAG, "Setup is complete! Live voice session is ready.")
                isSetupComplete = true
                listener.onConnected()
                return
            }

            // 2. Check for serverContent
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Model response was interrupted by user voice!")
                    listener.onInterrupted()
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            
                            // Check for audio chunks
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "")
                                val data = inlineData.optString("data", "")
                                if (data.isNotEmpty()) {
                                    listener.onAudioChunkReceived(data)
                                }
                            }

                            // Check for textual transcript/thought
                            if (part.has("text")) {
                                val spokenText = part.getString("text")
                                if (spokenText.isNotBlank()) {
                                    listener.onModelTextReceived(spokenText)
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    listener.onTurnComplete()
                }
            }

            // 3. Check for toolCall
            if (json.has("toolCall")) {
                val toolCall = json.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    val functionResponses = JSONArray()
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val callId = call.optString("id", "")
                        val callName = call.optString("name", "")
                        val args = call.optJSONObject("args")?.toString() ?: "{}"

                        val result = toolExecutor.execute(callName, args)
                        listener.onToolExecuted(callName, result.userFacingMessage)

                        val respObj = JSONObject().apply {
                            put("id", callId)
                            val responseWrapper = JSONObject().apply {
                                val output = JSONObject().apply {
                                    put("result", result.output)
                                    put("success", result.success)
                                }
                                put("output", output)
                            }
                            put("response", responseWrapper)
                        }
                        functionResponses.put(respObj)
                    }

                    // Send toolResponse back immediately
                    sendToolResponse(ws, functionResponses)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming message: ${e.message}", e)
        }
    }

    private fun sendToolResponse(ws: WebSocket, responses: JSONArray) {
        try {
            val root = JSONObject().apply {
                val toolResponse = JSONObject().apply {
                    put("functionResponses", responses)
                }
                put("toolResponse", toolResponse)
            }
            ws.send(root.toString())
            Log.d(TAG, "Sent toolResponse back to Gemini Live")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tool response: ${e.message}", e)
        }
    }

    /**
     * Streams microphone PCM16 audio chunk directly to the Live session.
     */
    fun sendRealtimeAudioChunk(base64Pcm: String) {
        if (!isSetupComplete || webSocket == null) return
        try {
            val root = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val mediaChunks = JSONArray().apply {
                        val chunk = JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Pcm)
                        }
                        put(chunk)
                    }
                    put("mediaChunks", mediaChunks)
                }
                put("realtimeInput", realtimeInput)
            }
            webSocket?.send(root.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send realtime audio: ${e.message}")
        }
    }

    /**
     * Send client turn / user prompt as text if needed
     */
    fun sendClientText(text: String) {
        if (!isSetupComplete || webSocket == null) return
        try {
            val root = JSONObject().apply {
                val clientContent = JSONObject().apply {
                    val turns = JSONArray().apply {
                        val turn = JSONObject().apply {
                            put("role", "user")
                            val parts = JSONArray().apply {
                                val part = JSONObject().apply {
                                    put("text", text)
                                }
                                put(part)
                            }
                            put("parts", parts)
                        }
                        put(turn)
                    }
                    put("turns", turns)
                    put("turnComplete", true)
                }
                put("clientContent", clientContent)
            }
            webSocket?.send(root.toString())
            Log.d(TAG, "Sent client text turn: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send client text: ${e.message}")
        }
    }

    fun close() {
        try {
            webSocket?.close(1000, "Session ended by user")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket: ${e.message}")
        }
        webSocket = null
        isConnected = false
        isSetupComplete = false
    }
}
