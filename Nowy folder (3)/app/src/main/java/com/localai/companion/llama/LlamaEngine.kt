package com.localai.companion.llama

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Callback interface for receiving inference tokens.
 */
interface InferenceCallback {
    fun onToken(token: String)
    fun onComplete(fullResponse: String)
    fun onError(error: String)
}

/**
 * Engine for GGUF LLM inference using llama.cpp.
 * This provides a high-level interface for loading models and generating responses.
 */
class LlamaEngine(private val context: Context) {

    private val TAG = "LlamaEngine"
    
    // Model state
    private var modelLoaded = false
    private var currentModelPath: String? = null
    private var currentModelName: String? = null
    
    // Inference state
    private var isGenerating = false
    private var currentJob: Job? = null
    
    // State flows
    private val _modelState = MutableStateFlow(ModelState.NO_MODEL)
    val modelState: StateFlow<ModelState> = _modelState
    
    private val _inferenceState = MutableStateFlow(InferenceState.IDLE)
    val inferenceState: StateFlow<InferenceState> = _inferenceState
    
    // Token flow for streaming
    private val _tokens = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val tokens: SharedFlow<String> = _tokens

    // Model info
    private var modelContextSize = 2048
    private var modelParams: String = ""
    private var modelSize: Long = 0

    init {
        // Initialize native library
        System.loadLibrary("llama")
        Log.i(TAG, "LlamaEngine initialized")
    }

    /**
     * Load a GGUF model from the given path.
     */
    fun loadModel(modelPath: String): Boolean {
        if (modelLoaded && currentModelPath == modelPath) {
            Log.w(TAG, "Model already loaded: $modelPath")
            return true
        }

        // Unload current model if any
        if (modelLoaded) {
            unloadModel()
        }

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file not found: $modelPath")
            _modelState.value = ModelState.ERROR
            return false
        }

        try {
            _modelState.value = ModelState.LOADING
            
            // Initialize the model with native call
            val result = nativeLoadModel(
                modelPath,
                getNativeContextSize(),
                getNativeThreads(),
                useGpu()
            )
            
            if (result) {
                modelLoaded = true
                currentModelPath = modelPath
                currentModelName = modelFile.name
                modelSize = modelFile.length()
                
                // Get model info
                modelContextSize = getNativeContextSize()
                modelParams = estimateModelParams(modelFile.name)
                
                _modelState.value = ModelState.LOADED
                Log.i(TAG, "Model loaded successfully: $currentModelName")
                return true
            } else {
                _modelState.value = ModelState.ERROR
                Log.e(TAG, "Failed to load model: $modelPath")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            _modelState.value = ModelState.ERROR
            return false
        }
    }

    /**
     * Unload the current model.
     */
    fun unloadModel() {
        if (!modelLoaded) return

        try {
            // Stop any ongoing inference
            stopInference()
            
            // Unload native model
            nativeUnloadModel()
            
            modelLoaded = false
            currentModelPath = null
            currentModelName = null
            _modelState.value = ModelState.NO_MODEL
            
            Log.i(TAG, "Model unloaded")
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
        }
    }

    /**
     * Generate a response from the model.
     */
    fun generate(
        prompt: String,
        callback: InferenceCallback,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        topK: Int = 40,
        maxTokens: Int = 512
    ): Job {
        if (!modelLoaded) {
            callback.onError("No model loaded")
            return CoroutineScope(Dispatchers.Main).launch { }
        }

        if (isGenerating) {
            callback.onError("Already generating")
            return CoroutineScope(Dispatchers.Main).launch { }
        }

        isGenerating = true
        _inferenceState.value = InferenceState.GENERATING

        return CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = StringBuilder()
                
                // Native inference with callback
                nativeGenerate(
                    prompt,
                    temperature,
                    topP,
                    topK,
                    maxTokens
                ) { token ->
                    // Token callback from native code
                    response.append(token)
                    
                    // Emit token to flow
                    CoroutineScope(Dispatchers.IO).launch {
                        _tokens.emit(token)
                    }
                    
                    // Callback
                    callback.onToken(token)
                }
                
                // Inference complete
                isGenerating = false
                _inferenceState.value = InferenceState.IDLE
                callback.onComplete(response.toString())
                
            } catch (e: Exception) {
                isGenerating = false
                _inferenceState.value = InferenceState.ERROR
                callback.onError(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Stop ongoing inference.
     */
    fun stopInference() {
        if (isGenerating) {
            try {
                nativeStopGeneration()
                currentJob?.cancel()
                isGenerating = false
                _inferenceState.value = InferenceState.IDLE
                Log.i(TAG, "Inference stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping inference", e)
            }
        }
    }

    /**
     * Get current model name.
     */
    fun getCurrentModelName(): String? = currentModelName

    /**
     * Get current model info.
     */
    fun getModelInfo(): ModelInfo? {
        if (!modelLoaded) return null
        return ModelInfo(
            name = currentModelName ?: "",
            path = currentModelPath ?: "",
            contextSize = modelContextSize,
            params = modelParams,
            size = modelSize
        )
    }

    /**
     * Check if model is loaded.
     */
    fun isModelLoaded(): Boolean = modelLoaded

    /**
     * Check if currently generating.
     */
    fun isGenerating(): Boolean = isGenerating

    /**
     * Release all resources.
     */
    fun release() {
        unloadModel()
    }

    // Native methods (implemented in JNI)
    private external fun nativeLoadModel(
        modelPath: String,
        contextSize: Int,
        threads: Int,
        useGpu: Boolean
    ): Boolean

    private external fun nativeUnloadModel()

    private external fun nativeGenerate(
        prompt: String,
        temperature: Float,
        topP: Float,
        topK: Int,
        maxTokens: Int,
        tokenCallback: (String) -> Unit
    )

    private external fun nativeStopGeneration()

    // Helper methods
    private fun getNativeContextSize(): Int = 2048 // Default, can be configured
    private fun getNativeThreads(): Int = 4
    private fun useGpu(): Boolean = false // GPU disabled by default

    private fun estimateModelParams(fileName: String): String {
        // Simple heuristic to estimate model parameters from filename
        return when {
            fileName.contains("1.1B") || fileName.contains("1.1b") -> "1.1B"
            fileName.contains("2.7B") || fileName.contains("2.7b") -> "2.7B"
            fileName.contains("7B") || fileName.contains("7b") -> "7B"
            fileName.contains("13B") || fileName.contains("13b") -> "13B"
            fileName.contains("70B") || fileName.contains("70b") -> "70B"
            else -> "Unknown"
        }
    }

    /**
     * Model state enumeration.
     */
    enum class ModelState {
        NO_MODEL,
        LOADING,
        LOADED,
        ERROR
    }

    /**
     * Inference state enumeration.
     */
    enum class InferenceState {
        IDLE,
        GENERATING,
        ERROR
    }

    /**
     * Model info data class.
     */
    data class ModelInfo(
        val name: String,
        val path: String,
        val contextSize: Int,
        val params: String,
        val size: Long
    )
}
