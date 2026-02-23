package com.localai.companion.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.math.roundToInt

/**
 * Engine for Text-to-Speech output.
 * Uses Android's built-in TTS engine with offline voice models.
 */
class TTSEngine(private val context: Context) {

    private val TAG = "TTSEngine"

    // Android TTS
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    // State
    private val _ttsState = MutableStateFlow(TTSState.IDLE)
    val ttsState: StateFlow<TTSState> = _ttsState

    // Settings
    private var currentVoice: String = "default"
    private var pitch: Float = 1.0f
    private var speed: Float = 1.0f
    private var volume: Float = 1.0f

    // Audio playback
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    // Available voices
    private val availableVoices = mutableListOf<VoiceInfo>()

    init {
        initializeTTS()
    }

    /**
     * Initialize the Android TTS engine.
     */
    private fun initializeTTS() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true

                // Set language to use offline voices
                val result = textToSpeech?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Language not supported, using default")
                }

                // Load available voices
                loadAvailableVoices()

                // Set up utterance listener
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _ttsState.value = TTSState.SPEAKING
                    }

                    override fun onDone(utteranceId: String?) {
                        _ttsState.value = TTSState.IDLE
                        isPlaying = false
                    }

                    override fun onError(utteranceId: String?) {
                        _ttsState.value = TTSState.ERROR
                        isPlaying = false
                        Log.e(TAG, "TTS error")
                    }
                })

                Log.i(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize TTS: $status")
                _ttsState.value = TTSState.ERROR
            }
        }
    }

    /**
     * Load available TTS voices.
     */
    private fun loadAvailableVoices() {
        availableVoices.clear()

        // Get available voices from Android TTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val voices = textToSpeech?.voices
            voices?.forEach { voice ->
                if (!voice.name.contains("network")) {
                    // Only include offline voices
                    availableVoices.add(
                        VoiceInfo(
                            name = voice.name,
                            locale = voice.locale.displayLanguage,
                            isOffline = !voice.name.contains("network")
                        )
                    )
                }
            }
        }

        // Add default voice if no voices found
        if (availableVoices.isEmpty()) {
            availableVoices.add(
                VoiceInfo(
                    name = "default",
                    locale = "English",
                    isOffline = true
                )
            )
        }
    }

    /**
     * Speak text using TTS.
     */
    fun speak(text: String): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "TTS not initialized")
            return false
        }

        if (text.isBlank()) {
            return false
        }

        try {
            _ttsState.value = TTSState.SPEAKING

            // Set speech parameters
            textToSpeech?.setPitch(pitch)
            textToSpeech?.setSpeechRate(speed)

            // Generate unique utterance ID
            val utteranceId = UUID.randomUUID().toString()

            // Speak text
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            } else {
                @Suppress("DEPRECATION")
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, utteranceId)
            }

            isPlaying = true
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking text", e)
            _ttsState.value = TTSState.ERROR
            return false
        }
    }

    /**
     * Stop speaking.
     */
    fun stop() {
        try {
            textToSpeech?.stop()
            _ttsState.value = TTSState.IDLE
            isPlaying = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    /**
     * Generate WAV audio from text (for streaming audio).
     */
    fun generateAudio(text: String, outputFile: File): File? {
        // This would require a native TTS engine like Piper or Coqui
        // For now, we'll return null and use Android's built-in TTS
        Log.w(TAG, "Native TTS audio generation not implemented, using Android TTS")
        return null
    }

    /**
     * Play generated audio file.
     */
    fun playAudio(audioFile: File): Boolean {
        if (!audioFile.exists()) {
            Log.e(TAG, "Audio file not found: ${audioFile.absolutePath}")
            return false
        }

        try {
            stop() // Stop any current playback

            // Read audio file
            val audioData = audioFile.readBytes()

            // Create AudioTrack
            val sampleRate = 22050 // Default for most TTS
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(audioFormat)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            // Write and play
            audioTrack?.write(audioData, 0, audioData.size)
            audioTrack?.play()

            _ttsState.value = TTSState.SPEAKING
            isPlaying = true

            // Monitor playback completion
            CoroutineScope(Dispatchers.IO).launch {
                while (isPlaying) {
                    delay(100)
                    if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        isPlaying = false
                        _ttsState.value = TTSState.IDLE
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
            _ttsState.value = TTSState.ERROR
            return false
        }
    }

    /**
     * Set the voice to use.
     */
    fun setVoice(voiceName: String) {
        currentVoice = voiceName

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val voices = textToSpeech?.voices
            val selectedVoice = voices?.find { it.name == voiceName }
            if (selectedVoice != null) {
                textToSpeech?.voice = selectedVoice
            }
        }
    }

    /**
     * Set the pitch of the voice.
     */
    fun setPitch(value: Float) {
        pitch = value.coerceIn(0.5f, 2.0f)
    }

    /**
     * Set the speech rate.
     */
    fun setSpeed(value: Float) {
        speed = value.coerceIn(0.5f, 2.0f)
    }

    /**
     * Set the volume.
     */
    fun setVolume(value: Float) {
        volume = value.coerceIn(0.0f, 1.0f)
    }

    /**
     * Get available voices.
     */
    fun getAvailableVoices(): List<VoiceInfo> = availableVoices.toList()

    /**
     * Check if TTS is currently speaking.
     */
    fun isSpeaking(): Boolean = isPlaying

    /**
     * Release TTS resources.
     */
    fun release() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        audioTrack?.release()
        audioTrack = null
        isInitialized = false
        Log.i(TAG, "TTS released")
    }

    /**
     * TTS state enumeration.
     */
    enum class TTSState {
        IDLE,
        SPEAKING,
        ERROR
    }

    /**
     * Voice information data class.
     */
    data class VoiceInfo(
        val name: String,
        val locale: String,
        val isOffline: Boolean
    )
}
