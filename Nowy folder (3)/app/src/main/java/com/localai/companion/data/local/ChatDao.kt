package com.localai.companion.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for chat messages.
 */
@Dao
interface ChatDao {

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesList(): List<ChatMessage>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ChatMessage>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(): ChatMessage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Delete
    suspend fun deleteMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getMessageCount(): Int

    @Query("DELETE FROM chat_messages WHERE id IN (SELECT id FROM chat_messages ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldestMessages(count: Int)
}

/**
 * Data Access Object for model files.
 */
@Dao
interface ModelDao {

    @Query("SELECT * FROM model_files ORDER BY lastModified DESC")
    fun getAllModels(): Flow<List<ModelFile>>

    @Query("SELECT * FROM model_files ORDER BY lastModified DESC")
    suspend fun getAllModelsList(): List<ModelFile>

    @Query("SELECT * FROM model_files WHERE path = :path")
    suspend fun getModel(path: String): ModelFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelFile)

    @Delete
    suspend fun deleteModel(model: ModelFile)

    @Query("DELETE FROM model_files")
    suspend fun deleteAllModels()
}
