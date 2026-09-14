package com.example.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class LocalVoiceEngine(
    context: Context,
    private val onSpeechStart: () -> Unit = {},
    private val onSpeechDone: () -> Unit = {}
) {
    companion object {
        private const val TAG = "LocalVoiceEngine"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                // Tune pitch and rate for youthful, confident female tone
                tts?.setPitch(1.2f)
                tts?.setSpeechRate(1.05f)
                isInitialized = true
                Log.d(TAG, "LocalVoiceEngine initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize TextToSpeech")
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                onSpeechStart()
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                onSpeechDone()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                onSpeechDone()
            }
        })
    }

    fun speak(text: String) {
        if (!isInitialized || text.isBlank()) return
        val utteranceId = "mahi_speech_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        onSpeechDone()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
