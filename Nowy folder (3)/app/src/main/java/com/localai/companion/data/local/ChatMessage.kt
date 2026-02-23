package com.localai.companion.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a chat message in the local database.
 */
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Entity representing a model file in the local storage.
 */
@Entity(tableName = "model_files")
data class ModelFile(
    @PrimaryKey
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long = System.currentTimeMillis()
)
