package com.localai.companion

import android.app.Application
import android.content.Context
import com.localai.companion.data.local.AppDatabase
import com.localai.companion.data.preferences.PreferencesManager
import com.localai.companion.llama.LlamaEngine
import com.localai.companion.tts.TTSEngine

/**
 * Main Application class for LocalAI Companion.
 * Provides singleton access to core components.
 */
class LocalAIApplication : Application() {

    // Database instance
    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    // Preferences manager
    val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(this)
    }

    // LLM Inference engine
    val llamaEngine: LlamaEngine by lazy {
        LlamaEngine(this)
    }

    // TTS Engine
    val ttsEngine: TTSEngine by lazy {
        TTSEngine(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize directories
        initializeDirectories()
    }

    private fun initializeDirectories() {
        // Create model directory
        val modelDir = getDir("models", Context.MODE_PRIVATE)
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        // Create TTS model directory
        val ttsDir = getDir("tts", Context.MODE_PRIVATE)
        if (!ttsDir.exists()) {
            ttsDir.mkdirs()
        }

        // Create audio cache directory
        val audioCacheDir = cacheDir.resolve("audio_cache")
        if (!audioCacheDir.exists()) {
            audioCacheDir.mkdirs()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // Clean up resources
        llamaEngine.release()
        ttsEngine.release()
    }

    companion object {
        lateinit var instance: LocalAIApplication
            private set

        fun get(context: Context): LocalAIApplication {
            return context.applicationContext as LocalAIApplication
        }
    }
}
