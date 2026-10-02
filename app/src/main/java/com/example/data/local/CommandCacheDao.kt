package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandCacheDao {

    @Query("SELECT * FROM command_cache WHERE normalizedQuery = :normalizedQuery LIMIT 1")
    suspend fun getCachedCommand(normalizedQuery: String): CommandCacheEntity?

    @Query("SELECT * FROM command_cache ORDER BY usageCount DESC, lastUsedAt DESC LIMIT :limit")
    fun getFrequentlyUsedCommands(limit: Int = 10): Flow<List<CommandCacheEntity>>

    @Query("SELECT * FROM command_cache ORDER BY usageCount DESC, lastUsedAt DESC LIMIT :limit")
    suspend fun getFrequentlyUsedCommandsList(limit: Int = 10): List<CommandCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(command: CommandCacheEntity): Long

    @Query("""
        UPDATE command_cache 
        SET usageCount = usageCount + 1, lastUsedAt = :timestamp, spokenReply = :spokenReply, actionType = :actionType, actionParametersJson = :parametersJson
        WHERE normalizedQuery = :normalizedQuery
    """)
    suspend fun incrementUsage(
        normalizedQuery: String,
        spokenReply: String,
        actionType: String,
        parametersJson: String,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    @Query("SELECT COUNT(*) FROM command_cache")
    suspend fun getCacheCount(): Int

    @Query("DELETE FROM command_cache WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM command_cache")
    suspend fun clearCache()
}
