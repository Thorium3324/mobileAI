package com.localai.companion.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.localai.companion.data.local.ModelFile
import com.localai.companion.databinding.ItemModelBinding

/**
 * RecyclerView adapter for model files.
 */
class ModelAdapter(
    private val onModelClick: (ModelFile) -> Unit
) : ListAdapter<ModelFile, ModelAdapter.ModelViewHolder>(ModelDiffCallback()) {

    private var selectedModelPath: String? = null

    fun setSelectedModel(path: String?) {
        val oldPath = selectedModelPath
        selectedModelPath = path
        
        // Refresh items
        currentList.forEachIndexed { index, model ->
            if (model.path == oldPath || model.path == path) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val binding = ItemModelBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ModelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position).path == selectedModelPath)
    }

    inner class ModelViewHolder(
        private val binding: ItemModelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onModelClick(getItem(position))
                }
            }
        }

        fun bind(model: ModelFile, isSelected: Boolean) {
            binding.modelName.text = model.name
            binding.modelInfo.text = formatModelInfo(model)
            binding.selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
        }

        private fun formatModelInfo(model: ModelFile): String {
            val sizeStr = formatFileSize(model.size)
            val params = extractModelParams(model.name)
            return "$params • $sizeStr"
        }

        private fun extractModelParams(fileName: String): String {
            return when {
                fileName.contains("1.1B", ignoreCase = true) -> "1.1B params"
                fileName.contains("2.7B", ignoreCase = true) -> "2.7B params"
                fileName.contains("7B", ignoreCase = true) -> "7B params"
                fileName.contains("13B", ignoreCase = true) -> "13B params"
                fileName.contains("70B", ignoreCase = true) -> "70B params"
                fileName.contains("phi", ignoreCase = true) -> "Phi series"
                fileName.contains("tiny", ignoreCase = true) -> "Tiny model"
                fileName.contains("mistral", ignoreCase = true) -> "Mistral"
                fileName.contains("llama", ignoreCase = true) -> "LLaMA"
                else -> "Unknown"
            }
        }

        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            }
        }
    }

    class ModelDiffCallback : DiffUtil.ItemCallback<ModelFile>() {
        override fun areItemsTheSame(oldItem: ModelFile, newItem: ModelFile): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: ModelFile, newItem: ModelFile): Boolean {
            return oldItem == newItem
        }
    }
}
