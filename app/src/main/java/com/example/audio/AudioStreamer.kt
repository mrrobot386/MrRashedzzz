package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.sqrt

class AudioStreamer(
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "AudioStreamer"
        const val SAMPLE_RATE_IN = 16000 // Gemini input PCM 16kHz
        const val SAMPLE_RATE_OUT = 24000 // Gemini output PCM 24kHz
        private const val CHUNK_SIZE_IN = 1600 // ~100ms of audio (1600 samples = 3200 bytes)
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var recordJob: Job? = null
    private var playbackJob: Job? = null

    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _playbackLevel = MutableStateFlow(0f)
    val playbackLevel: StateFlow<Float> = _playbackLevel.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    var onAudioChunkCaptured: ((base64Chunk: String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (recordJob?.isActive == true) return

        try {
            val minBufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_IN,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufSize, CHUNK_SIZE_IN * 4)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_IN,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            initAudioTrack()

            recordJob = coroutineScope.launch(Dispatchers.IO) {
                val shortBuffer = ShortArray(CHUNK_SIZE_IN)
                val byteBuffer = ByteArray(CHUNK_SIZE_IN * 2)

                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readCount = audioRecord?.read(shortBuffer, 0, CHUNK_SIZE_IN) ?: 0
                    if (readCount > 0) {
                        // Calculate RMS amplitude
                        var sum = 0.0
                        for (i in 0 until readCount) {
                            val sample = shortBuffer[i].toInt()
                            sum += sample * sample

                            // Convert short to little-endian bytes
                            byteBuffer[i * 2] = (sample and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
                        }
                        val rms = sqrt(sum / readCount)
                        // Normalize 0..1 with dampening
                        val normalized = (rms / 8000.0).coerceIn(0.0, 1.0).toFloat()
                        _micLevel.value = normalized

                        val actualBytes = if (readCount == CHUNK_SIZE_IN) {
                            byteBuffer
                        } else {
                            byteBuffer.copyOf(readCount * 2)
                        }

                        val base64 = Base64.encodeToString(actualBytes, Base64.NO_WRAP)
                        onAudioChunkCaptured?.invoke(base64)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
        }
    }

    private fun initAudioTrack() {
        if (audioTrack != null) return
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE_OUT,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuf, SAMPLE_RATE_OUT * 2)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE_OUT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            startPlaybackLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating AudioTrack: ${e.message}", e)
        }
    }

    private fun startPlaybackLoop() {
        if (playbackJob?.isActive == true) return
        playbackJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                val chunk = audioQueue.poll()
                if (chunk != null && chunk.isNotEmpty()) {
                    _isPlaying.value = true
                    // Compute playback amplitude
                    var sum = 0.0
                    val numSamples = chunk.size / 2
                    for (i in 0 until numSamples) {
                        val low = chunk[i * 2].toInt() and 0xFF
                        val high = chunk[i * 2 + 1].toInt()
                        val sample = (high shl 8) or low
                        sum += sample * sample
                    }
                    if (numSamples > 0) {
                        val rms = sqrt(sum / numSamples)
                        _playbackLevel.value = (rms / 9000.0).coerceIn(0.0, 1.0).toFloat()
                    }

                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    if (audioQueue.isEmpty()) {
                        _isPlaying.value = false
                        _playbackLevel.value = 0f
                    }
                    kotlinx.coroutines.delay(10)
                }
            }
        }
    }

    fun enqueueAudioResponse(base64Pcm: String) {
        try {
            val bytes = Base64.decode(base64Pcm, Base64.DEFAULT)
            if (bytes.isNotEmpty()) {
                audioQueue.offer(bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding PCM chunk: ${e.message}")
        }
    }

    /**
     * Immediate interruption: flush AudioTrack and empty incoming queues.
     */
    fun interruptPlayback() {
        audioQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error interrupting audioTrack: ${e.message}")
        }
        _isPlaying.value = false
        _playbackLevel.value = 0f
    }

    fun stopRecording() {
        recordJob?.cancel()
        recordJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null
        _micLevel.value = 0f
    }

    fun release() {
        stopRecording()
        interruptPlayback()
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack: ${e.message}")
        }
        audioTrack = null
    }
}
