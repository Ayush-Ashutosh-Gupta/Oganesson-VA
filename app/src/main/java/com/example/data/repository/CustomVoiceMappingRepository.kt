package com.example.data.repository

import com.example.data.local.CustomVoiceMappingDao
import com.example.data.local.CustomVoiceMappingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository to abstract Custom Voice Mapping data operations from UI and Services.
 */
class CustomVoiceMappingRepository(
    private val dao: CustomVoiceMappingDao
) {

    val allMappings: Flow<List<CustomVoiceMappingEntity>> = dao.getAllMappingsFlow()

    suspend fun getEnabledMappings(): List<CustomVoiceMappingEntity> = withContext(Dispatchers.IO) {
        dao.getEnabledMappings()
    }

    suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        dao.getEnabledMappings().size
    }

    suspend fun findMatchingMapping(spokenQuery: String): CustomVoiceMappingEntity? = withContext(Dispatchers.IO) {
        val clean = spokenQuery.trim().lowercase()
        val enabled = dao.getEnabledMappings()

        // 1. Exact match
        val exact = enabled.find { it.phrase.trim().lowercase() == clean }
        if (exact != null) return@withContext exact

        // 2. Starts with or ends with
        val startsOrEnds = enabled.find {
            val p = it.phrase.trim().lowercase()
            clean == p || clean.startsWith("$p ") || clean.endsWith(" $p")
        }
        if (startsOrEnds != null) return@withContext startsOrEnds

        // 3. Substring match if phrase is substantial
        enabled.find {
            val p = it.phrase.trim().lowercase()
            p.length >= 3 && clean.contains(p)
        }
    }

    suspend fun addMapping(mapping: CustomVoiceMappingEntity): Long = withContext(Dispatchers.IO) {
        dao.insertMapping(mapping)
    }

    suspend fun updateMapping(mapping: CustomVoiceMappingEntity) = withContext(Dispatchers.IO) {
        dao.updateMapping(mapping)
    }

    suspend fun deleteMapping(mapping: CustomVoiceMappingEntity) = withContext(Dispatchers.IO) {
        dao.deleteMapping(mapping)
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun incrementUsage(id: Long) = withContext(Dispatchers.IO) {
        dao.incrementUsage(id)
    }
}
