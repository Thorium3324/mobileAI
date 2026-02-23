package com.localai.companion.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.localai.companion.LocalAIApplication
import com.localai.companion.R
import com.localai.companion.data.local.ChatMessage
import com.localai.companion.databinding.FragmentChatBinding
import com.localai.companion.llama.InferenceCallback
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Fragment for the main chat interface.
 */
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { LocalAIApplication.get(requireContext()) }
    private val chatAdapter = ChatAdapter()

    private var isGenerating = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupInputField()
        setupButtons()
        observeModelState()
        loadChatHistory()
    }

    private fun setupRecyclerView() {
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private fun setupInputField() {
        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun setupButtons() {
        binding.btnSend.setOnClickListener {
            sendMessage()
        }

        binding.btnVoiceToggle.setOnClickListener {
            toggleVoiceOutput()
        }
    }

    private fun observeModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            app.llamaEngine.modelState.collectLatest { state ->
                updateModelStatus(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            app.llamaEngine.inferenceState.collectLatest { state ->
                updateInferenceState(state)
            }
        }

        // Observe token streaming
        viewLifecycleOwner.lifecycleScope.launch {
            app.llamaEngine.tokens.collectLatest { token ->
                // Update the last AI message with streaming token
                chatAdapter.updateLastAIMessage(token)
                scrollToBottom()
            }
        }
    }

    private fun updateModelStatus(state: com.localai.companion.llama.LlamaEngine.ModelState) {
        when (state) {
            com.localai.companion.llama.LlamaEngine.ModelState.NO_MODEL -> {
                binding.modelStatus.text = getString(R.string.no_model_loaded)
                binding.statusIndicator.setBackgroundResource(R.drawable.status_indicator)
                binding.statusText.text = getString(R.string.status_ready)
            }
            com.localai.companion.llama.LlamaEngine.ModelState.LOADING -> {
                binding.modelStatus.text = getString(R.string.model_loading)
                binding.statusText.text = getString(R.string.status_loading)
            }
            com.localai.companion.llama.LlamaEngine.ModelState.LOADED -> {
                binding.modelStatus.text = app.llamaEngine.getCurrentModelName() ?: ""
                binding.statusIndicator.setBackgroundResource(R.drawable.status_indicator)
                binding.statusText.text = getString(R.string.status_ready)
            }
            com.localai.companion.llama.LlamaEngine.ModelState.ERROR -> {
                binding.statusText.text = getString(R.string.status_error)
            }
        }
    }

    private fun updateInferenceState(state: com.localai.companion.llama.LlamaEngine.InferenceState) {
        when (state) {
            com.localai.companion.llama.LlamaEngine.InferenceState.IDLE -> {
                isGenerating = false
                binding.statusText.text = getString(R.string.status_ready)
                binding.loadingIndicator.visibility = View.GONE
                binding.btnSend.isEnabled = true
            }
            com.localai.companion.llama.LlamaEngine.InferenceState.GENERATING -> {
                isGenerating = true
                binding.statusText.text = getString(R.string.status_generating)
                binding.loadingIndicator.visibility = View.VISIBLE
                binding.btnSend.isEnabled = false
            }
            com.localai.companion.llama.LlamaEngine.InferenceState.ERROR -> {
                isGenerating = false
                binding.statusText.text = getString(R.string.status_error)
                binding.loadingIndicator.visibility = View.GONE
                binding.btnSend.isEnabled = true
            }
        }
    }

    private fun loadChatHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.chatDao().getAllMessages().collectLatest { messages ->
                chatAdapter.submitList(messages)
                updateEmptyState(messages.isEmpty())
                scrollToBottom()
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.chatRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun scrollToBottom() {
        if (chatAdapter.itemCount > 0) {
            binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    private fun sendMessage() {
        val message = binding.messageInput.text?.toString()?.trim()
        if (message.isNullOrEmpty()) return

        // Check if model is loaded
        if (!app.llamaEngine.isModelLoaded()) {
            Toast.makeText(requireContext(), R.string.error_no_model, Toast.LENGTH_SHORT).show()
            return
        }

        // Clear input
        binding.messageInput.text?.clear()

        // Add user message
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.chatDao().insertMessage(
                ChatMessage(role = "user", content = message)
            )
        }

        // Add empty AI message placeholder
        viewLifecycleOwner.lifecycleScope.launch {
            val aiMessageId = app.database.chatDao().insertMessage(
                ChatMessage(role = "assistant", content = "")
            )
            chatAdapter.setGeneratingMessageId(aiMessageId)
        }

        // Generate response
        generateResponse(message)
    }

    private fun generateResponse(userMessage: String) {
        val prefs = app.preferencesManager

        // Build prompt from chat history
        viewLifecycleOwner.lifecycleScope.launch {
            val history = app.database.chatDao().getRecentMessages(20)
            val prompt = buildPrompt(history, userMessage, prefs.systemPrompt)

            app.llamaEngine.generate(
                prompt = prompt,
                temperature = prefs.modelTemperature,
                topP = prefs.modelTopP,
                topK = prefs.modelTopK,
                maxTokens = prefs.modelMaxTokens,
                callback = object : InferenceCallback {
                    override fun onToken(token: String) {
                        // Token handled by flow collector
                    }

                    override fun onComplete(fullResponse: String) {
                        // Save response to database
                        viewLifecycleOwner.lifecycleScope.launch {
                            // Update the last AI message
                            val messages = app.database.chatDao().getAllMessagesList()
                            val lastMessage = messages.lastOrNull { it.role == "assistant" }
                            lastMessage?.let {
                                app.database.chatDao().updateMessage(
                                    it.copy(content = fullResponse.trim())
                                )
                            }
                            chatAdapter.setGeneratingMessageId(null)

                            // Auto-speak if enabled
                            if (prefs.ttsEnabled && prefs.voiceOutputEnabled) {
                                app.ttsEngine.speak(fullResponse.trim())
                            }
                        }
                    }

                    override fun onError(error: String) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            chatAdapter.setGeneratingMessageId(null)
                            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }

    private fun buildPrompt(
        history: List<ChatMessage>,
        currentMessage: String,
        systemPrompt: String
    ): String {
        val sb = StringBuilder()

        // Add system prompt
        sb.appendLine("System: $systemPrompt")
        sb.appendLine()

        // Add chat history
        for (msg in history) {
            when (msg.role) {
                "user" -> sb.appendLine("User: ${msg.content}")
                "assistant" -> sb.appendLine("Assistant: ${msg.content}")
            }
        }

        // Add current message
        sb.append("User: $currentMessage")
        sb.appendLine()
        sb.append("Assistant:")

        return sb.toString()
    }

    private fun toggleVoiceOutput() {
        val prefs = app.preferencesManager
        prefs.voiceOutputEnabled = !prefs.voiceOutputEnabled

        if (prefs.voiceOutputEnabled) {
            binding.btnVoiceToggle.setColorFilter(
                resources.getColor(R.color.primary, null)
            )
        } else {
            binding.btnVoiceToggle.setColorFilter(
                resources.getColor(R.color.icon, null)
            )
            app.ttsEngine.stop()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
