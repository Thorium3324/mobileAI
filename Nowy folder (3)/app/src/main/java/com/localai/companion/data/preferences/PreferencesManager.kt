package com.localai.companion.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manages application preferences using SharedPreferences.
 * Handles all settings including model parameters, TTS settings, and UI preferences.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Model Settings
    var currentModelPath: String?
        get() = prefs.getString(KEY_CURRENT_MODEL, null)
        set(value) = prefs.edit { putString(KEY_CURRENT_MODEL, value) }

    var modelTemperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE)
        set(value) = prefs.edit { putFloat(KEY_TEMPERATURE, value) }

    var modelTopP: Float
        get() = prefs.getFloat(KEY_TOP_P, DEFAULT_TOP_P)
        set(value) = prefs.edit { putFloat(KEY_TOP_P, value) }

    var modelTopK: Int
        get() = prefs.getInt(KEY_TOP_K, DEFAULT_TOP_K)
        set(value) = prefs.edit { putInt(KEY_TOP_K, value) }

    var modelContextSize: Int
        get() = prefs.getInt(KEY_CONTEXT_SIZE, DEFAULT_CONTEXT_SIZE)
        set(value) = prefs.edit { putInt(KEY_CONTEXT_SIZE, value) }

    var modelMaxTokens: Int
        get() = prefs.getInt(KEY_MAX_TOKENS, DEFAULT_MAX_TOKENS)
        set(value) = prefs.edit { putInt(KEY_MAX_TOKENS, value) }

    var repeatPenalty: Float
        get() = prefs.getFloat(KEY_REPEAT_PENALTY, DEFAULT_REPEAT_PENALTY)
        set(value) = prefs.edit { putFloat(KEY_REPEAT_PENALTY, value) }

    // Performance Settings
    var useGpuInference: Boolean
        get() = prefs.getBoolean(KEY_GPU_INFERENCE, false)
        set(value) = prefs.edit { putBoolean(KEY_GPU_INFERENCE, value) }

    var lowRamMode: Boolean
        get() = prefs.getBoolean(KEY_LOW_RAM_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_LOW_RAM_MODE, value) }

    var cpuThreads: Int
        get() = prefs.getInt(KEY_CPU_THREADS, DEFAULT_THREADS)
        set(value) = prefs.edit { putInt(KEY_CPU_THREADS, value) }

    // TTS Settings
    var ttsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_TTS_ENABLED, value) }

    var ttsVoice: String
        get() = prefs.getString(KEY_TTS_VOICE, DEFAULT_TTS_VOICE) ?: DEFAULT_TTS_VOICE
        set(value) = prefs.edit { putString(KEY_TTS_VOICE, value) }

    var ttsPitch: Float
        get() = prefs.getFloat(KEY_TTS_PITCH, DEFAULT_TTS_PITCH)
        set(value) = prefs.edit { putFloat(KEY_TTS_PITCH, value) }

    var ttsSpeed: Float
        get() = prefs.getFloat(KEY_TTS_SPEED, DEFAULT_TTS_SPEED)
        set(value) = prefs.edit { putFloat(KEY_TTS_SPEED, value) }

    var ttsVolume: Float
        get() = prefs.getFloat(KEY_TTS_VOLUME, DEFAULT_TTS_VOLUME)
        set(value) = prefs.edit { putFloat(KEY_TTS_VOLUME, value) }

    // Chat Settings
    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
        set(value) = prefs.edit { putString(KEY_SYSTEM_PROMPT, value) }

    // UI Settings
    var voiceOutputEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_OUTPUT, false)
        set(value) = prefs.edit { putBoolean(KEY_VOICE_OUTPUT, value) }

    // Clear all preferences
    fun clearAll() {
        prefs.edit { clear() }
    }

    // Clear chat history (but keep settings)
    fun clearChatHistory() {
        // This is handled by the database, not preferences
    }

    companion object {
        private const val PREFS_NAME = "localai_prefs"

        // Model Keys
        private const val KEY_CURRENT_MODEL = "current_model"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TOP_P = "top_p"
        private const val KEY_TOP_K = "top_k"
        private const val KEY_CONTEXT_SIZE = "context_size"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_REPEAT_PENALTY = "repeat_penalty"

        // Performance Keys
        private const val KEY_GPU_INFERENCE = "gpu_inference"
        private const val KEY_LOW_RAM_MODE = "low_ram_mode"
        private const val KEY_CPU_THREADS = "cpu_threads"

        // TTS Keys
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_TTS_VOLUME = "tts_volume"

        // Chat Keys
        private const val KEY_SYSTEM_PROMPT = "system_prompt"

        // UI Keys
        private const val KEY_VOICE_OUTPUT = "voice_output"

        // Default Values
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_TOP_P = 0.9f
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_CONTEXT_SIZE = 2048
        const val DEFAULT_MAX_TOKENS = 512
        const val DEFAULT_REPEAT_PENALTY = 1.1f
        const val DEFAULT_THREADS = 4

        const val DEFAULT_TTS_PITCH = 1.0f
        const val DEFAULT_TTS_SPEED = 1.0f
        const val DEFAULT_TTS_VOLUME = 1.0f
        const val DEFAULT_TTS_VOICE = "default"

        const val DEFAULT_SYSTEM_PROMPT = "You are a helpful AI assistant. Provide accurate and helpful responses."
    }
}
