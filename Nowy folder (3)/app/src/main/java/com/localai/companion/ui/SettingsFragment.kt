package com.localai.companion.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.localai.companion.BuildConfig
import com.localai.companion.LocalAIApplication
import com.localai.companion.R
import com.localai.companion.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

/**
 * Fragment for app settings.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { LocalAIApplication.get(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTTSSettings()
        setupPerformanceSettings()
        setupChatSettings()
        setupAbout()
    }

    private fun setupTTSSettings() {
        val prefs = app.preferencesManager
        val ttsEngine = app.ttsEngine

        // TTS enabled switch
        binding.switchTtsEnabled.isChecked = prefs.ttsEnabled
        binding.switchTtsEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.ttsEnabled = isChecked
        }

        // Voice spinner
        val voices = ttsEngine.getAvailableVoices()
        val voiceNames = voices.map { it.name }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            voiceNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerVoice.adapter = adapter

        // Set current voice
        val currentVoiceIndex = voiceNames.indexOf(prefs.ttsVoice)
        if (currentVoiceIndex >= 0) {
            binding.spinnerVoice.setSelection(currentVoiceIndex)
        }

        // Pitch slider
        binding.sliderPitch.value = prefs.ttsPitch
        binding.pitchValue.text = String.format("%.1f", prefs.ttsPitch)
        binding.sliderPitch.addOnChangeListener { _, value, _ ->
            prefs.ttsPitch = value
            ttsEngine.setPitch(value)
            binding.pitchValue.text = String.format("%.1f", value)
        }

        // Speed slider
        binding.sliderSpeed.value = prefs.ttsSpeed
        binding.speedValue.text = String.format("%.1f", prefs.ttsSpeed)
        binding.sliderSpeed.addOnChangeListener { _, value, _ ->
            prefs.ttsSpeed = value
            ttsEngine.setSpeed(value)
            binding.speedValue.text = String.format("%.1f", value)
        }
    }

    private fun setupPerformanceSettings() {
        val prefs = app.preferencesManager

        // GPU inference switch
        binding.switchGpu.isChecked = prefs.useGpuInference
        binding.switchGpu.setOnCheckedChangeListener { _, isChecked ->
            prefs.useGpuInference = isChecked
            Toast.makeText(
                requireContext(),
                "Restart app for GPU changes to take effect",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Low RAM mode switch
        binding.switchLowRam.isChecked = prefs.lowRamMode
        binding.switchLowRam.setOnCheckedChangeListener { _, isChecked ->
            prefs.lowRamMode = isChecked
        }

        // CPU threads slider
        binding.sliderThreads.value = prefs.cpuThreads.toFloat()
        binding.threadsValue.text = prefs.cpuThreads.toString()
        binding.sliderThreads.addOnChangeListener { _, value, _ ->
            val intValue = value.toInt()
            prefs.cpuThreads = intValue
            binding.threadsValue.text = intValue.toString()
        }
    }

    private fun setupChatSettings() {
        val prefs = app.preferencesManager

        // System prompt
        binding.systemPromptInput.setText(prefs.systemPrompt)
        binding.systemPromptInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val newPrompt = binding.systemPromptInput.text?.toString() ?: ""
                if (newPrompt.isNotBlank()) {
                    prefs.systemPrompt = newPrompt
                }
            }
        }

        // Clear chat button
        binding.btnClearChat.setOnClickListener {
            showClearChatDialog()
        }
    }

    private fun setupAbout() {
        // Version
        binding.versionText.text = getString(R.string.version) + " " + BuildConfig.VERSION_NAME
    }

    private fun showClearChatDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_clear_chat_title)
            .setMessage(R.string.dialog_clear_chat_message)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                clearChatHistory()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun clearChatHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            app.database.chatDao().deleteAllMessages()
            Toast.makeText(requireContext(), R.string.memory_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
