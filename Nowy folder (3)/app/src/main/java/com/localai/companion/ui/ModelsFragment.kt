package com.localai.companion.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.localai.companion.LocalAIApplication
import com.localai.companion.R
import com.localai.companion.data.local.ModelFile
import com.localai.companion.databinding.FragmentModelsBinding
import com.localai.companion.llama.LlamaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fragment for managing LLM models.
 */
class ModelsFragment : Fragment() {

    private var _binding: FragmentModelsBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { LocalAIApplication.get(requireContext()) }
    private val modelAdapter = ModelAdapter { model ->
        loadModel(model.path)
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Copy selected file to models directory
                copyModelFile(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupButtons()
        setupSliders()
        observeModelState()
        scanModels()
    }

    private fun setupRecyclerView() {
        binding.modelsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = modelAdapter
        }
    }

    private fun setupButtons() {
        binding.btnLoadModel.setOnClickListener {
            openFilePicker()
        }

        binding.btnUnloadModel.setOnClickListener {
            unloadModel()
        }
    }

    private fun setupSliders() {
        val prefs = app.preferencesManager

        // Temperature slider
        binding.sliderTemperature.value = prefs.modelTemperature
        binding.temperatureValue.text = prefs.modelTemperature.toString()
        binding.sliderTemperature.addOnChangeListener { _, value, _ ->
            prefs.modelTemperature = value
            binding.temperatureValue.text = String.format("%.1f", value)
        }

        // Top P slider
        binding.sliderTopP.value = prefs.modelTopP
        binding.topPValue.text = prefs.modelTopP.toString()
        binding.sliderTopP.addOnChangeListener { _, value, _ ->
            prefs.modelTopP = value
            binding.topPValue.text = String.format("%.2f", value)
        }

        // Top K slider
        binding.sliderTopK.value = prefs.modelTopK.toFloat()
        binding.topKValue.text = prefs.modelTopK.toString()
        binding.sliderTopK.addOnChangeListener { _, value, _ ->
            val intValue = value.toInt()
            prefs.modelTopK = intValue
            binding.topKValue.text = intValue.toString()
        }

        // Context size slider
        binding.sliderContext.value = prefs.modelContextSize.toFloat()
        binding.contextValue.text = prefs.modelContextSize.toString()
        binding.sliderContext.addOnChangeListener { _, value, _ ->
            val intValue = value.toInt()
            prefs.modelContextSize = intValue
            binding.contextValue.text = intValue.toString()
        }
    }

    private fun observeModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            app.llamaEngine.modelState.collectLatest { state ->
                updateUI(state)
            }
        }
    }

    private fun updateUI(state: LlamaEngine.ModelState) {
        when (state) {
            LlamaEngine.ModelState.NO_MODEL -> {
                binding.currentModelName.text = getString(R.string.no_model_loaded)
                binding.btnLoadModel.isEnabled = true
                binding.btnUnloadModel.isEnabled = false
                binding.modelInfo.visibility = View.GONE
            }
            LlamaEngine.ModelState.LOADING -> {
                binding.currentModelName.text = getString(R.string.model_loading)
                binding.btnLoadModel.isEnabled = false
                binding.btnUnloadModel.isEnabled = false
            }
            LlamaEngine.ModelState.LOADED -> {
                val modelInfo = app.llamaEngine.getModelInfo()
                binding.currentModelName.text = modelInfo?.name ?: getString(R.string.model_loaded)
                binding.btnLoadModel.isEnabled = true
                binding.btnUnloadModel.isEnabled = true

                // Show model info
                modelInfo?.let { info ->
                    binding.modelInfo.visibility = View.VISIBLE
                    binding.modelParams.text = "Parameters: ${info.params}"
                    binding.modelContext.text = "Context: ${info.contextSize}"
                }
            }
            LlamaEngine.ModelState.ERROR -> {
                binding.currentModelName.text = getString(R.string.model_load_error)
                binding.btnLoadModel.isEnabled = true
                binding.btnUnloadModel.isEnabled = false
            }
        }
    }

    private fun scanModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            val modelsDir = File(requireContext().filesDir, "models")
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }

            val modelFiles = withContext(Dispatchers.IO) {
                modelsDir.listFiles()?.filter { it.extension == "gguf" }?.map { file ->
                    ModelFile(
                        path = file.absolutePath,
                        name = file.name,
                        size = file.length(),
                        lastModified = file.lastModified()
                    )
                } ?: emptyList()
            }

            modelAdapter.submitList(modelFiles)
            binding.emptyState.visibility = if (modelFiles.isEmpty()) View.VISIBLE else View.GONE
            binding.modelsRecyclerView.visibility = if (modelFiles.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/x-gguf"))
        }
        pickFileLauncher.launch(intent)
    }

    private fun copyModelFile(uri: android.net.Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val fileName = "model_${System.currentTimeMillis()}.gguf"
                val modelsDir = File(requireContext().filesDir, "models")
                val outputFile = File(modelsDir, fileName)

                inputStream?.use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Model copied to: ${outputFile.name}", Toast.LENGTH_SHORT).show()
                    scanModels()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to copy model: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadModel(modelPath: String) {
        val success = app.llamaEngine.loadModel(modelPath)
        if (success) {
            app.preferencesManager.currentModelPath = modelPath
            Toast.makeText(requireContext(), R.string.model_loaded, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), R.string.model_load_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun unloadModel() {
        app.llamaEngine.unloadModel()
        app.preferencesManager.currentModelPath = null
        Toast.makeText(requireContext(), R.string.model_unloaded, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
