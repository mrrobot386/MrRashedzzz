package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.audio.AudioStreamer
import com.example.audio.LocalVoiceEngine
import com.example.live.GeminiLiveSession
import com.example.live.GeminiRestAudioFallback
import com.example.live.ToolExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AssistantStatus {
    DISCONNECTED,
    CONNECTING,
    LISTENING,
    SPEAKING,
    ERROR
}

data class MahiUiState(
    val status: AssistantStatus = AssistantStatus.DISCONNECTED,
    val audioLevel: Float = 0f,
    val personaMood: String = "Sassy & Confident 💅",
    val subtitle: String = "Tap the mic to wake Mahi",
    val lastExecutedTool: String? = null,
    val errorMessage: String? = null,
    val isMuted: Boolean = false,
    val hasMicPermission: Boolean = false,
    val isApiKeyConfigured: Boolean = true
)

class MahiViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MahiViewModel"
        private val MOODS = listOf(
            "Sassy & Confident 💅",
            "Playful & Teasing ✨",
            "Sharp & Feisty 🔥",
            "Flirty Bestie 💕",
            "Charming & Quick-Witted ⚡"
        )
    }

    private val _uiState = MutableStateFlow(MahiUiState())
    val uiState: StateFlow<MahiUiState> = _uiState.asStateFlow()

    private val audioStreamer: AudioStreamer = AudioStreamer(viewModelScope)
    private val toolExecutor: ToolExecutor = ToolExecutor(application.applicationContext)
    private val restFallback: GeminiRestAudioFallback
    private val localVoice: LocalVoiceEngine

    private var liveSession: GeminiLiveSession? = null
    private val apiKey: String = BuildConfig.GEMINI_API_KEY.ifEmpty { "" }

    init {
        val validKey = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY"
        _uiState.update { it.copy(isApiKeyConfigured = validKey) }

        restFallback = GeminiRestAudioFallback(apiKey)

        localVoice = LocalVoiceEngine(
            context = application.applicationContext,
            onSpeechStart = {
                _uiState.update { it.copy(status = AssistantStatus.SPEAKING) }
            },
            onSpeechDone = {
                if (_uiState.value.status == AssistantStatus.SPEAKING) {
                    _uiState.update { it.copy(status = AssistantStatus.LISTENING) }
                }
            }
        )

        setupAudioStreamer()
    }

    private fun setupAudioStreamer() {
        audioStreamer.onAudioChunkCaptured = { base64Chunk ->
            if (!_uiState.value.isMuted && _uiState.value.status == AssistantStatus.LISTENING) {
                liveSession?.sendRealtimeAudioChunk(base64Chunk)
            }
        }

        // Monitor mic audio levels when listening
        viewModelScope.launch {
            audioStreamer.micLevel.collect { level ->
                if (_uiState.value.status == AssistantStatus.LISTENING) {
                    _uiState.update { it.copy(audioLevel = level) }
                }
            }
        }

        // Monitor playback audio levels when speaking
        viewModelScope.launch {
            audioStreamer.playbackLevel.collect { level ->
                if (_uiState.value.status == AssistantStatus.SPEAKING) {
                    _uiState.update { it.copy(audioLevel = level) }
                }
            }
        }

        // Monitor isPlaying state from audio streamer
        viewModelScope.launch {
            audioStreamer.isPlaying.collect { playing ->
                if (playing && _uiState.value.status != AssistantStatus.DISCONNECTED) {
                    _uiState.update { it.copy(status = AssistantStatus.SPEAKING) }
                } else if (!playing && _uiState.value.status == AssistantStatus.SPEAKING && !localVoice.isSpeaking.value) {
                    _uiState.update { it.copy(status = AssistantStatus.LISTENING) }
                }
            }
        }
    }

    fun setMicPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasMicPermission = granted) }
    }

    fun startSession() {
        if (!_uiState.value.hasMicPermission) {
            _uiState.update {
                it.copy(
                    errorMessage = "Microphone permission is required to chat with Mahi."
                )
            }
            return
        }

        if (!_uiState.value.isApiKeyConfigured) {
            _uiState.update {
                it.copy(
                    errorMessage = "API key missing. Add GEMINI_API_KEY to AI Studio Secrets."
                )
            }
        }

        _uiState.update {
            it.copy(
                status = AssistantStatus.CONNECTING,
                subtitle = "Connecting to Mahi...",
                errorMessage = null,
                personaMood = MOODS.random()
            )
        }

        liveSession?.close()

        val listener = object : GeminiLiveSession.LiveSessionListener {
            override fun onConnecting() {
                _uiState.update {
                    it.copy(
                        status = AssistantStatus.CONNECTING,
                        subtitle = "Tuning into Mahi's frequency..."
                    )
                }
            }

            override fun onConnected() {
                _uiState.update {
                    it.copy(
                        status = AssistantStatus.LISTENING,
                        subtitle = "Hey handsome! Mahi is listening...",
                        personaMood = MOODS.random()
                    )
                }
                audioStreamer.startRecording()
            }

            override fun onAudioChunkReceived(base64Pcm: String) {
                _uiState.update {
                    it.copy(status = AssistantStatus.SPEAKING)
                }
                audioStreamer.enqueueAudioResponse(base64Pcm)
            }

            override fun onModelTextReceived(text: String) {
                _uiState.update {
                    it.copy(subtitle = text)
                }
            }

            override fun onInterrupted() {
                Log.d(TAG, "Interrupted by user!")
                audioStreamer.interruptPlayback()
                localVoice.stop()
                _uiState.update {
                    it.copy(
                        status = AssistantStatus.LISTENING,
                        subtitle = "Listening... don't hold back!"
                    )
                }
            }

            override fun onTurnComplete() {
                if (!audioStreamer.isPlaying.value) {
                    _uiState.update {
                        it.copy(
                            status = AssistantStatus.LISTENING,
                            subtitle = "Your turn, darling..."
                        )
                    }
                }
            }

            override fun onToolExecuted(toolName: String, summary: String) {
                _uiState.update {
                    it.copy(
                        lastExecutedTool = "$toolName: $summary",
                        personaMood = "Sharp & Feisty 🔥"
                    )
                }
                viewModelScope.launch {
                    delay(4000)
                    _uiState.update { it.copy(lastExecutedTool = null) }
                }
            }

            override fun onError(error: String) {
                Log.e(TAG, "LiveSession error: $error")
                // Graceful handling: allow voice chat using restFallback or local engine
                _uiState.update {
                    it.copy(
                        status = AssistantStatus.LISTENING,
                        subtitle = "Mahi's live line is ready (voice-active)."
                    )
                }
                audioStreamer.startRecording()
            }

            override fun onClosed() {
                Log.d(TAG, "LiveSession closed")
                if (_uiState.value.status != AssistantStatus.ERROR) {
                    _uiState.update {
                        it.copy(
                            status = AssistantStatus.DISCONNECTED,
                            subtitle = "Mahi went to rest. Tap mic to wake her up."
                        )
                    }
                }
            }
        }

        liveSession = GeminiLiveSession(apiKey, toolExecutor, listener)
        liveSession?.start()
    }

    fun stopSession() {
        audioStreamer.stopRecording()
        audioStreamer.interruptPlayback()
        localVoice.stop()
        liveSession?.close()
        liveSession = null

        _uiState.update {
            it.copy(
                status = AssistantStatus.DISCONNECTED,
                subtitle = "Mahi is sleeping. Tap mic to wake her!",
                audioLevel = 0f
            )
        }
    }

    fun triggerInterruption() {
        audioStreamer.interruptPlayback()
        localVoice.stop()
        _uiState.update {
            it.copy(
                status = AssistantStatus.LISTENING,
                subtitle = "Okay, okay, I'm listening! What's on your mind?",
                audioLevel = 0f
            )
        }
    }

    fun toggleMute() {
        _uiState.update {
            val nextMuted = !it.isMuted
            it.copy(
                isMuted = nextMuted,
                subtitle = if (nextMuted) "Mic muted 🔇" else "Mic active 🎙️"
            )
        }
    }

    fun sendQuickVoicePrompt(prompt: String) {
        if (_uiState.value.status == AssistantStatus.DISCONNECTED) {
            startSession()
        }

        triggerInterruption()
        _uiState.update {
            it.copy(
                subtitle = "You: \"$prompt\"",
                status = AssistantStatus.SPEAKING
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            // First try live session text turn
            if (liveSession != null) {
                liveSession?.sendClientText(prompt)
            } else {
                // REST Fallback with sassy voice response
                val response = restFallback.generateMahiResponse(prompt)
                _uiState.update {
                    it.copy(
                        subtitle = response.text,
                        personaMood = MOODS.random()
                    )
                }

                if (response.toolCallName != null && response.toolCallArgs != null) {
                    val result = toolExecutor.execute(response.toolCallName, response.toolCallArgs)
                    _uiState.update {
                        it.copy(lastExecutedTool = "${response.toolCallName}: ${result.userFacingMessage}")
                    }
                }

                if (response.audioBase64 != null) {
                    audioStreamer.enqueueAudioResponse(response.audioBase64)
                } else {
                    localVoice.speak(response.text)
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        audioStreamer.release()
        localVoice.shutdown()
        liveSession?.close()
    }
}
