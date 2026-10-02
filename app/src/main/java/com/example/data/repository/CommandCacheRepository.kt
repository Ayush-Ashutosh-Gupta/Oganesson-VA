package com.example.data.repository

import com.example.data.local.CommandCacheDao
import com.example.data.local.CommandCacheEntity
import com.example.data.model.ActionDetails
import com.example.data.model.AssistantActionPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Repository for managing cached voice commands in Room.
 * Enables instant responses for repeated voice commands when the network is unstable or offline.
 */
class CommandCacheRepository(private val commandCacheDao: CommandCacheDao) {

    fun getFrequentlyUsedCommands(limit: Int = 10): Flow<List<CommandCacheEntity>> {
        return commandCacheDao.getFrequentlyUsedCommands(limit)
    }

    suspend fun findCachedCommand(query: String): AssistantActionPayload? = withContext(Dispatchers.IO) {
        val normalized = normalizeQuery(query)
        if (normalized.isBlank()) return@withContext null

        val cached = commandCacheDao.getCachedCommand(normalized) ?: return@withContext null

        // Decode action parameters
        val paramsMap = try {
            val json = JSONObject(cached.actionParametersJson)
            val map = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = json.optString(key, "")
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }

        val action = if (cached.actionType.isNotBlank() && cached.actionType != "NONE") {
            ActionDetails(
                type = cached.actionType,
                parameters = paramsMap
            )
        } else {
            ActionDetails(type = "NONE", parameters = emptyMap())
        }

        AssistantActionPayload(
            spokenReply = cached.spokenReply,
            action = action
        )
    }

    suspend fun getCachedPayload(query: String): AssistantActionPayload? = findCachedCommand(query)

    suspend fun saveOrUpdateCommand(
        rawQuery: String,
        payload: AssistantActionPayload
    ) = withContext(Dispatchers.IO) {
        val normalized = normalizeQuery(rawQuery)
        if (normalized.isBlank()) return@withContext

        val reply = payload.spokenReply ?: "Done."
        val actionType = payload.action?.type ?: "NONE"
        val paramsJson = try {
            val json = JSONObject()
            payload.action?.parameters?.forEach { (k, v) ->
                json.put(k, v)
            }
            json.toString()
        } catch (e: Exception) {
            "{}"
        }

        val existing = commandCacheDao.getCachedCommand(normalized)
        if (existing != null) {
            commandCacheDao.incrementUsage(
                normalizedQuery = normalized,
                spokenReply = reply,
                actionType = actionType,
                parametersJson = paramsJson
            )
        } else {
            commandCacheDao.insertOrUpdate(
                CommandCacheEntity(
                    rawQuery = rawQuery.trim(),
                    normalizedQuery = normalized,
                    spokenReply = reply,
                    actionType = actionType,
                    actionParametersJson = paramsJson,
                    usageCount = 1
                )
            )
        }
    }

    companion object {
        fun normalizeQuery(query: String): String {
            return query.lowercase()
                .trim()
                .replace(Regex("[^a-z0-9\\s]"), "")
                .replace(Regex("\\s+"), " ")
        }
    }
}
