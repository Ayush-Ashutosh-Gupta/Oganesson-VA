package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CommandCacheEntity
import com.example.data.local.OganessonDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.ActionDetails
import com.example.data.model.AssistantActionPayload
import com.example.data.network.MistralClient
import com.example.data.repository.CommandCacheRepository
import com.example.service.WakeWordDetector
import com.example.ui.overlay.FloatingOverlayManager
import com.example.ui.overlay.OverlayState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var inMemoryDatabase: OganessonDatabase
    private lateinit var cacheRepository: CommandCacheRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        inMemoryDatabase = Room.inMemoryDatabaseBuilder(context, OganessonDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cacheRepository = CommandCacheRepository(inMemoryDatabase.commandCacheDao())
    }

    @After
    fun tearDown() {
        inMemoryDatabase.close()
    }

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Oganesson", appName)
    }

    @Test
    fun `retrofit mistral client creates valid api service`() {
        val client = MistralClient()
        assertNotNull(client.apiService)
    }

    @Test
    fun `parse valid Mistral JSON payload`() {
        val client = MistralClient()
        val rawJson = """
            {
              "spoken_reply": "Opening YouTube right now.",
              "action": {
                "type": "OPEN_APP",
                "parameters": {
                  "app": "youtube"
                }
              }
            }
        """.trimIndent()

        val parsed = client.parseAssistantResponse(rawJson)
        assertEquals("Opening YouTube right now.", parsed.spokenReply)
        assertNotNull(parsed.action)
        assertEquals("OPEN_APP", parsed.action?.type)
        assertEquals("youtube", parsed.action?.parameters?.get("app"))
    }

    @Test
    fun `parse markdown wrapped Mistral response`() {
        val client = MistralClient()
        val markdownJson = """
            ```json
            {
              "spoken_reply": "Ten minute timer set.",
              "action": {
                "type": "SET_TIMER",
                "parameters": {
                  "seconds": "600",
                  "message": "10 minutes"
                }
              }
            }
            ```
        """.trimIndent()

        val parsed = client.parseAssistantResponse(markdownJson)
        assertEquals("Ten minute timer set.", parsed.spokenReply)
        assertEquals("SET_TIMER", parsed.action?.type)
        assertEquals("600", parsed.action?.parameters?.get("seconds"))
    }

    @Test
    fun `parse reasoning model response with think tags`() {
        val client = MistralClient()
        val thinkResponse = """
            <think>
            The user wants to open the camera. I should formulate an OPEN_APP action.
            </think>
            {
              "spoken_reply": "Opening your camera.",
              "action": {
                "type": "OPEN_APP",
                "parameters": {
                  "app": "camera"
                }
              }
            }
        """.trimIndent()

        val parsed = client.parseAssistantResponse(thinkResponse)
        assertEquals("Opening your camera.", parsed.spokenReply)
        assertEquals("OPEN_APP", parsed.action?.type)
        assertEquals("camera", parsed.action?.parameters?.get("app"))
    }

    @Test
    fun `mistral models catalog has all free tier models`() {
        val models = PreferencesManager.AVAILABLE_MISTRAL_MODELS
        assertTrue(models.any { it.id == "mistral-small-latest" })
        assertTrue(models.any { it.id == "open-mistral-nemo" })
        assertTrue(models.any { it.id == "open-mistral-7b" })
    }

    @Test
    fun `getEffectiveApiKey returns user key when available`() {
        val key = PreferencesManager.getEffectiveApiKey("user_key_123")
        assertEquals("user_key_123", key)
    }

    @Test
    fun `getEffectiveApiKey falls back to buildConfig when user key is blank`() {
        val key = PreferencesManager.getEffectiveApiKey("   ")
        assertEquals(PreferencesManager.sanitizeApiKey(com.example.BuildConfig.MISTRAL_FALLBACK_API_KEY), key)
    }

    @Test
    fun `wake phrases contain Hi Oganesson variations`() {
        assertTrue(WakeWordDetector.WAKE_PHRASES.contains("hi oganesson"))
        assertTrue(WakeWordDetector.WAKE_PHRASES.contains("hey oganesson"))
        assertTrue(WakeWordDetector.WAKE_PHRASES.contains("oganesson"))
    }

    @Test
    fun `floating overlay manager initializes with default state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = FloatingOverlayManager(context)
        assertEquals(OverlayState.LISTENING, manager.overlayState.value)
        assertEquals("", manager.userSpokenText.value)
        assertEquals("", manager.assistantReplyText.value)
        manager.destroy()
    }

    @Test
    fun `room command cache saves and retrieves frequently used commands`() = runBlocking {
        val query = "Open YouTube"
        val payload = AssistantActionPayload(
            spokenReply = "Opening YouTube right now.",
            action = ActionDetails(type = "OPEN_APP", parameters = mapOf("app" to "youtube"))
        )

        // Initial cache lookup should be null
        val initialLookup = cacheRepository.findCachedCommand(query)
        assertNull(initialLookup)

        // Store into Room cache
        cacheRepository.saveOrUpdateCommand(query, payload)

        // Lookup again
        val cached = cacheRepository.findCachedCommand("open youtube!")
        assertNotNull(cached)
        assertEquals("Opening YouTube right now.", cached?.spokenReply)
        assertEquals("OPEN_APP", cached?.action?.type)
        assertEquals("youtube", cached?.action?.parameters?.get("app"))

        // Increment count and check frequently used list
        cacheRepository.saveOrUpdateCommand("open youtube", payload)
        val frequent = cacheRepository.getFrequentlyUsedCommands().first()
        assertEquals(1, frequent.size)
        assertEquals(2, frequent[0].usageCount)
    }

    @Test
    fun `room command cache normalizes variations of punctuation and casing`() {
        val n1 = CommandCacheRepository.normalizeQuery("What time is it???")
        val n2 = CommandCacheRepository.normalizeQuery("what time is it")
        assertEquals(n1, n2)
        assertEquals("what time is it", n1)
    }

    @Test
    fun `custom voice mapping repository matches custom phrase and persists to Room`() = runBlocking {
        val repo = com.example.data.repository.CustomVoiceMappingRepository(inMemoryDatabase.customVoiceMappingDao())
        val mapping = com.example.data.local.CustomVoiceMappingEntity(
            phrase = "turn on wifi",
            actionType = "TOGGLE_WIFI",
            target = "ON",
            spokenReply = "Opening Wi-Fi controls to turn Wi-Fi on"
        )
        val id = repo.addMapping(mapping)
        assertTrue(id > 0)

        val match = repo.findMatchingMapping("turn on wifi")
        assertNotNull(match)
        assertEquals("TOGGLE_WIFI", match?.actionType)
        assertEquals("ON", match?.target)

        val allMappings = repo.allMappings.first()
        assertEquals(1, allMappings.size)

        repo.incrementUsage(id)
        val updated = repo.findMatchingMapping("turn on wifi")
        assertEquals(1, updated?.usageCount)
    }

    @Test
    fun `action parser detects wifi requests locally without AI`() {
        val res = com.example.service.ActionParser.parseLocalAction("turn on wifi please")
        assertTrue(res.isHandledLocally)
        assertNotNull(res.payload)
        assertEquals("TOGGLE_WIFI", res.payload?.action?.type)
        assertEquals("ON", res.payload?.action?.parameters?.get("state"))
    }
}
