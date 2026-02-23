package com.localai.companion.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.localai.companion.data.local.ChatMessage
import com.localai.companion.databinding.ItemMessageAiBinding
import com.localai.companion.databinding.ItemMessageUserBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecyclerView adapter for chat messages.
 */
class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    private var generatingMessageId: Long? = null

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_AI = 1
    }

    fun setGeneratingMessageId(id: Long?) {
        val oldId = generatingMessageId
        generatingMessageId = id
        
        // Refresh the old and new generating items
        oldId?.let { id ->
            val position = currentList.indexOfFirst { it.id == id }
            if (position >= 0) notifyItemChanged(position)
        }
        id?.let { newId ->
            val position = currentList.indexOfFirst { it.id == newId }
            if (position >= 0) notifyItemChanged(position)
        }
    }

    fun updateLastAIMessage(token: String) {
        if (generatingMessageId == null) return
        
        val position = currentList.indexOfFirst { it.id == generatingMessageId }
        if (position >= 0) {
            val message = currentList[position]
            val newContent = message.content + token
            val updatedMessage = message.copy(content = newContent)
            
            val newList = currentList.toMutableList()
            newList[position] = updatedMessage
            submitList(newList)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).role == "user") VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val binding = ItemMessageUserBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                UserMessageViewHolder(binding)
            }
            else -> {
                val binding = ItemMessageAiBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                AiMessageViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AiMessageViewHolder -> holder.bind(message, message.id == generatingMessageId)
        }
    }

    inner class UserMessageViewHolder(
        private val binding: ItemMessageUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            binding.messageText.text = message.content
            binding.timestamp.text = formatTimestamp(message.timestamp)
        }
    }

    inner class AiMessageViewHolder(
        private val binding: ItemMessageAiBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage, isGenerating: Boolean) {
            binding.messageText.text = message.content
            
            // Show voice controls for non-generating AI messages
            binding.voiceControls.visibility = if (!isGenerating && message.content.isNotEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
            
            binding.timestamp.text = formatTimestamp(message.timestamp)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
