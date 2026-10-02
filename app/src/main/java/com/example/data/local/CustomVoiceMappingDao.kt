package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomVoiceMappingDao {

    @Query("SELECT * FROM custom_voice_mappings ORDER BY createdAt DESC")
    fun getAllMappingsFlow(): Flow<List<CustomVoiceMappingEntity>>

    @Query("SELECT * FROM custom_voice_mappings WHERE isEnabled = 1")
    suspend fun getEnabledMappings(): List<CustomVoiceMappingEntity>

    @Query("SELECT * FROM custom_voice_mappings WHERE LOWER(phrase) = LOWER(:phrase) LIMIT 1")
    suspend fun findByPhrase(phrase: String): CustomVoiceMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: CustomVoiceMappingEntity): Long

    @Update
    suspend fun updateMapping(mapping: CustomVoiceMappingEntity)

    @Delete
    suspend fun deleteMapping(mapping: CustomVoiceMappingEntity)

    @Query("UPDATE custom_voice_mappings SET usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementUsage(id: Long)

    @Query("DELETE FROM custom_voice_mappings WHERE id = :id")
    suspend fun deleteById(id: Long)
}
