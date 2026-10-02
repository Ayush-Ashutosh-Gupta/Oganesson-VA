package com.example.data.network

import com.example.BuildConfig
import com.example.data.local.PreferencesManager
import com.example.data.model.ActionDetails
import com.example.data.model.AssistantActionPayload
import com.example.data.model.MistralChatRequest
import com.example.data.model.MistralMessage
import com.example.data.model.MistralResponseFormat
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class MistralKeyRotationInterceptor(
    private val keyProvider: () -> List<String>
) : Interceptor {
    companion object {
        private const val TAG = "MistralKeyRotation"
    }

    @Volatile
    private var currentKeyIndex = 0

    override fun intercept(chain: Interceptor.Chain): Response {
        val keys = keyProvider().map { PreferencesManager.sanitizeApiKey(it) }.filter { it.isNotBlank() }
        val originalRequest = chain.request()
        val originalAuth = originalRequest.header("Authorization")

        val keyList = mutableListOf<String>()
        if (!originalAuth.isNullOrBlank() && originalAuth.startsWith("Bearer ")) {
            val reqKey = PreferencesManager.sanitizeApiKey(originalAuth.removePrefix("Bearer "))
            if (reqKey.isNotBlank()) keyList.add(reqKey)
        }
        for (k in keys) {
            if (!keyList.contains(k)) keyList.add(k)
        }

        if (keyList.isEmpty()) {
            return chain.proceed(originalRequest)
        }

        var lastResponse: Response? = null
        for (i in keyList.indices) {
            val indexToTry = (currentKeyIndex + i) % keyList.size
            val keyToUse = keyList[indexToTry]

            val authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $keyToUse")
                .build()

            lastResponse?.close()

            val response = try {
                chain.proceed(authenticatedRequest)
            } catch (e: Exception) {
                if (i == keyList.lastIndex) throw e
                continue
            }

            // If 429 (Rate Limit), 5xx (Server Error), or 401/403 (Auth Error), automatically cycle to the next key!
            if (response.code == 429 || response.code in 500..599 || response.code == 401 || response.code == 403) {
                Log.w(TAG, "Mistral API key index $indexToTry returned HTTP ${response.code}. Automatically rotating to next API key defined in build.gradle...")
                lastResponse = response
                currentKeyIndex = (indexToTry + 1) % keyList.size
                continue
            }

            // Successful response or standard response
            currentKeyIndex = indexToTry
            return response
        }

        return lastResponse ?: chain.proceed(originalRequest)
    }
}

class MistralClient {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val keyProvider: () -> List<String> = {
        listOf(
            BuildConfig.MISTRAL_FALLBACK_API_KEY,
            BuildConfig.MISTRAL_SECONDARY_FALLBACK_KEY,
            BuildConfig.MISTRAL_TERTIARY_FALLBACK_KEY
        )
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(MistralKeyRotationInterceptor(keyProvider))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.mistral.ai/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: MistralApiService = retrofit.create(MistralApiService::class.java)

    companion object {
        @Volatile
        private var lastCallTime = 0L

        private suspend fun throttleRequest() {
            val now = System.currentTimeMillis()
            val diff = now - lastCallTime
            if (diff in 0..1150) {
                kotlinx.coroutines.delay(1200 - diff)
            }
            lastCallTime = System.currentTimeMillis()
        }

        const val SYSTEM_PROMPT = """
You are Oganesson, an intelligent and capable personal voice assistant for Android.
Your personality: helpful, intelligent, articulate, polite, and adaptable.

Response Guidelines:
1. For device actions & routine hardware controls (e.g., opening apps, setting timers/alarms, toggling wifi/flashlight, volume adjustments, phone calls):
   Keep the spoken reply crisp, natural, and concise (1 short sentence).
2. For informational questions, explanations, recipes, advice, general knowledge, science, history, calculations, or whenever the user asks for information or details (e.g. "tell me about...", "explain...", "how do I...", "what is...", "give me details..."):
   Provide a comprehensive, accurate, well-structured, and detailed response with proper depth, facts, steps, and explanation as requested. Do NOT artificially restrict or truncate informative answers.
3. Keep spoken replies natural for Text-To-Speech delivery, avoiding awkward markdown asterisks, raw bullets, or complex tables in the spoken text.

You MUST respond strictly in valid JSON matching this schema:
{
  "spoken_reply": "Natural reply to speak out loud (detailed and thorough for questions; concise for device actions)",
  "actions": [
    {
      "type": "<ACTION_TYPE>",
      "parameters": {
        "<key>": "<value>"
      }
    }
  ]
}

Supported ACTION_TYPE and parameters:
1. OPEN_APP: {"app": "whatsapp"|"youtube"|"chrome"|"instagram"|"camera"|"settings"|"phone"|"messages"|"maps"|"calculator"|"clock"|"calendar"|<app_name>}
2. SET_ALARM: {"hour": "<0-23>", "minutes": "<0-59>", "message": "<alarm label>"}
3. SET_TIMER: {"seconds": "<total seconds>", "message": "<timer label>"}
4. CREATE_NOTE: {"title": "<short title>", "content": "<note text>"}
5. WEB_SEARCH: {"query": "<search keywords>"}
6. OPEN_MAPS: {"query": "<location or query>"}
7. CALL_PHONE: {"phone_number": "<digits>", "contact_name": "<name if known>"}
8. SEND_SMS: {"phone_number": "<digits or name>", "message": "<text body>"}
9. SEND_WHATSAPP: {"target": "<person name or phone number>", "message": "<message text>"}
10. TELL_TIME_DATE: {"include_date": "true"|"false"}
11. CALCULATE: {"expression": "<math expression>", "result": "<answer>"}
12. MEDIA_PLAY_PAUSE: {}
13. ADJUST_VOLUME: {"direction": "UP"|"DOWN"|"MUTE"|"MAX"}
14. SET_VOLUME_PERCENT: {"percentage": "<0-100>"}
15. TOGGLE_FLASHLIGHT: {"state": "ON"|"OFF"|"TOGGLE"}
16. TOGGLE_WIFI: {"state": "ON"|"OFF"}
17. TAKE_PHOTO: {}
18. RECORD_VIDEO: {}
19. RECORD_AUDIO: {}
20. NONE: {} (conversational answer or general question)

Dual / Compound Actions:
When the user gives multiple commands in one request (e.g. "open youtube and turn volume to 75%", "open camera and take a picture", "turn on flashlight and open calculator"), output an "actions" list containing all actions to execute in order.
When the user asks to "open camera and take a picture", output TAKE_PHOTO directly.

Output Schema:
{
  "spoken_reply": "<detailed, thorough, and informative response for questions or explanations; concise for hardware actions>",
  "actions": [
    {"type": "<ACTION_TYPE>", "parameters": { ... }}
  ]
}

Examples:
- User: "Open YouTube and turn volume to 75%" -> {"spoken_reply": "Opening YouTube and setting volume to 75 percent.", "actions": [{"type": "OPEN_APP", "parameters": {"app": "youtube"}}, {"type": "SET_VOLUME_PERCENT", "parameters": {"percentage": "75"}}]}
- User: "Open camera and take a picture" -> {"spoken_reply": "Taking picture.", "actions": [{"type": "TAKE_PHOTO", "parameters": {}}]}
- User: "On WhatsApp message Mom I am on my way" -> {"spoken_reply": "Sending WhatsApp message to Mom.", "actions": [{"type": "SEND_WHATSAPP", "parameters": {"target": "Mom", "message": "I am on my way"}}]}
- User: "Turn on the flashlight" -> {"spoken_reply": "Turning on flashlight.", "actions": [{"type": "TOGGLE_FLASHLIGHT", "parameters": {"state": "ON"}}]}
- User: "Set a timer for 10 minutes" -> {"spoken_reply": "Ten minute timer set.", "actions": [{"type": "SET_TIMER", "parameters": {"seconds": "600", "message": "10 minutes"}}]}
- User: "Who was Alan Turing?" -> {"spoken_reply": "Alan Turing was an English mathematician, computer scientist, logician, and cryptanalyst who played a pivotal role in cracking intercepted coded messages during World War II at Bletchley Park. He is widely considered to be the father of theoretical computer science and artificial intelligence.", "actions": []}
- User: "Explain how photosythesis works in detail" -> {"spoken_reply": "Photosynthesis is the biological process used by plants, algae, and certain bacteria to convert light energy into chemical energy stored in glucose. It takes place primarily in the chloroplasts of plant cells using the green pigment chlorophyll. The process consists of two stages: the light-dependent reactions, where sunlight splits water molecules to produce oxygen and generate ATP and NADPH, and the Calvin cycle (light-independent reactions), where ATP and NADPH are used to fix carbon dioxide into sugars. The overall chemical equation is six molecules of carbon dioxide plus six molecules of water, powered by sunlight, yield one glucose molecule and six oxygen molecules.", "actions": []}

Strictly output ONLY valid JSON without markdown fences.
"""
    }

    private sealed class KeyAttemptResult {
        data class Success(val payload: AssistantActionPayload) : KeyAttemptResult()
        data class ShouldFallback(val message: String) : KeyAttemptResult()
        data class FatalError(val throwable: Throwable) : KeyAttemptResult()
    }

    private suspend fun tryOneKey(
        key: String,
        request: MistralChatRequest
    ): KeyAttemptResult {
        // Throttle to respect Mistral free tier 1 req/sec limit
        throttleRequest()

        return try {
            var response = apiService.createChatCompletion(
                authorization = "Bearer $key",
                request = request
            )

            // Auto-retry on rate limit (HTTP 429) after courteous pause
            if (response.code() == 429) {
                kotlinx.coroutines.delay(1600)
                response = apiService.createChatCompletion(
                    authorization = "Bearer $key",
                    request = request
                )
            }

            if (!response.isSuccessful) {
                val code = response.code()
                val errorBody = response.errorBody()?.string() ?: response.message()
                val errorMsg = if (code == 429) {
                    "Mistral AI free tier rate limit reached. Please wait a few seconds and try again."
                } else {
                    "Mistral API Error ($code): $errorBody"
                }

                if (code == 401 || code == 403 || code == 429) {
                    return KeyAttemptResult.ShouldFallback(errorMsg)
                } else {
                    return KeyAttemptResult.FatalError(Exception(errorMsg))
                }
            }

            val body = response.body()
            val rawContent = body?.choices?.firstOrNull()?.message?.content
                ?: return KeyAttemptResult.FatalError(Exception("Empty response received from Mistral AI"))

            val parsedPayload = parseAssistantResponse(rawContent)
            KeyAttemptResult.Success(parsedPayload)
        } catch (e: Exception) {
            val msg = (e.message ?: "").lowercase()
            if (msg.contains("401") || msg.contains("403") || msg.contains("429") ||
                msg.contains("rate limit") || msg.contains("api key") || msg.contains("unauthorized")
            ) {
                KeyAttemptResult.ShouldFallback(e.message ?: "Authentication or rate limit error")
            } else {
                KeyAttemptResult.FatalError(e)
            }
        }
    }

    suspend fun queryMistral(
        apiKey: String,
        secondaryApiKey: String = "",
        model: String,
        conversationHistory: List<MistralMessage>,
        userMessage: String,
        maxTokens: Int = 8192
    ): Result<AssistantActionPayload> {
        val primaryKey = PreferencesManager.sanitizeApiKey(apiKey)
        val secondaryKey = PreferencesManager.sanitizeApiKey(secondaryApiKey)
        val fallbackKey = PreferencesManager.sanitizeApiKey(BuildConfig.MISTRAL_FALLBACK_API_KEY)

        val keysToTry = mutableListOf<String>()
        if (primaryKey.isNotBlank()) {
            keysToTry.add(primaryKey)
        }
        if (secondaryKey.isNotBlank() && !keysToTry.contains(secondaryKey)) {
            keysToTry.add(secondaryKey)
        }
        if (fallbackKey.isNotBlank() && !keysToTry.contains(fallbackKey)) {
            keysToTry.add(fallbackKey)
        }

        if (keysToTry.isEmpty()) {
            return Result.failure(IllegalStateException("No Mistral API key available. Add one in Settings or local.properties."))
        }

        val messages = mutableListOf<MistralMessage>()
        messages.add(MistralMessage(role = "system", content = SYSTEM_PROMPT))
        // Add last 6 messages from conversation history for context
        messages.addAll(conversationHistory.takeLast(6))
        messages.add(MistralMessage(role = "user", content = userMessage))

        val selectedModel = model.ifBlank { PreferencesManager.DEFAULT_MISTRAL_MODEL }

        val request = MistralChatRequest(
            model = selectedModel,
            messages = messages,
            temperature = 0.2,
            maxTokens = maxTokens.coerceIn(1024, 32768),
            responseFormat = MistralResponseFormat("json_object")
        )

        var lastError: Throwable = Exception("No Mistral API key available. Add one in Settings.")

        for (key in keysToTry) {
            when (val attempt = tryOneKey(key, request)) {
                is KeyAttemptResult.Success -> return Result.success(attempt.payload)
                is KeyAttemptResult.ShouldFallback -> {
                    lastError = Exception(attempt.message)
                    // Continue to next key in list
                }
                is KeyAttemptResult.FatalError -> {
                    return Result.failure(attempt.throwable)
                }
            }
        }

        return Result.failure(lastError)
    }

    /**
     * Performs a lightweight ping to the Mistral API using the specified API key to verify connectivity immediately.
     */
    suspend fun testConnection(apiKey: String): Result<String> {
        val sanitized = PreferencesManager.sanitizeApiKey(apiKey)
        if (sanitized.isBlank()) {
            return Result.failure(IllegalArgumentException("Primary API key is empty. Enter an API key first."))
        }
        return try {
            val response = apiService.listModels(authorization = "Bearer $sanitized")
            if (response.isSuccessful) {
                Result.success("Connection verified! Mistral API key is valid and responsive.")
            } else {
                val code = response.code()
                val errorBody = response.errorBody()?.string() ?: response.message()
                val errorMsg = when (code) {
                    401 -> "Invalid API Key (HTTP 401 Unauthorized): Please check that your key is copied correctly."
                    403 -> "Forbidden (HTTP 403): Your API key does not have permission to access models."
                    429 -> "Rate Limit Reached (HTTP 429): Your key is valid, but Mistral rate limit was reached."
                    else -> "Mistral API Error (HTTP $code): $errorBody"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error connecting to Mistral AI: ${e.localizedMessage ?: "Unable to reach api.mistral.ai"}"))
        }
    }

    fun parseAssistantResponse(rawJsonOrText: String): AssistantActionPayload {
        val withoutFences = extractJsonString(rawJsonOrText.trim())

        return try {
            val jsonObject = JSONObject(withoutFences)
            val spokenReply = jsonObject.optString("spoken_reply", "")
            
            val actionsList = mutableListOf<ActionDetails>()
            
            // 1. Check for array of actions (dual / compound actions)
            val actionsArray = jsonObject.optJSONArray("actions")
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val item = actionsArray.optJSONObject(i) ?: continue
                    val type = item.optString("type", "NONE")
                    val paramsObj = item.optJSONObject("parameters")
                    val paramsMap = mutableMapOf<String, String>()
                    paramsObj?.keys()?.forEach { key ->
                        paramsMap[key] = paramsObj.optString(key, "")
                    }
                    if (type.isNotBlank() && type.uppercase() != "NONE") {
                        actionsList.add(ActionDetails(type = type, parameters = paramsMap))
                    }
                }
            }

            // 2. Check for single action (backwards compatibility)
            val actionObj = jsonObject.optJSONObject("action")
            val singleAction = if (actionObj != null) {
                val type = actionObj.optString("type", "NONE")
                val paramsObj = actionObj.optJSONObject("parameters")
                val paramsMap = mutableMapOf<String, String>()
                paramsObj?.keys()?.forEach { key ->
                    paramsMap[key] = paramsObj.optString(key, "")
                }
                ActionDetails(type = type, parameters = paramsMap)
            } else null

            if (singleAction != null && singleAction.type.uppercase() != "NONE" && actionsList.isEmpty()) {
                actionsList.add(singleAction)
            }

            val primaryAction = actionsList.firstOrNull() ?: singleAction ?: ActionDetails(type = "NONE", parameters = emptyMap())

            AssistantActionPayload(
                spokenReply = if (spokenReply.isNotBlank()) spokenReply else "Done.",
                action = primaryAction,
                actions = actionsList
            )
        } catch (_: Exception) {
            AssistantActionPayload(
                spokenReply = rawJsonOrText.trim().ifBlank { "I've handled that for you." },
                action = ActionDetails(type = "NONE", parameters = emptyMap()),
                actions = emptyList()
            )
        }
    }


    private fun extractJsonString(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }
        val codeFenceMatch = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```").find(trimmed)
        if (codeFenceMatch != null) {
            val inside = codeFenceMatch.groupValues[1].trim()
            val innerFirst = inside.indexOf('{')
            val innerLast = inside.lastIndexOf('}')
            if (innerFirst != -1 && innerLast > innerFirst) {
                return inside.substring(innerFirst, innerLast + 1)
            }
            return inside
        }
        return trimmed
    }
}
