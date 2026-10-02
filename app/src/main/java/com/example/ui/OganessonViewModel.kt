package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CommandCacheEntity
import com.example.data.local.CustomVoiceMappingEntity
import com.example.data.local.NoteDao
import com.example.data.local.NoteEntity
import com.example.data.local.OganessonDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.ActionDetails
import com.example.data.model.AssistantActionPayload
import com.example.data.model.MistralMessage
import com.example.data.model.SenderType
import com.example.data.model.TranscriptItem
import com.example.data.model.VoiceState
import com.example.data.network.MistralClient
import com.example.data.repository.CommandCacheRepository
import com.example.data.repository.CustomVoiceMappingRepository
import com.example.service.ActionExecutor
import com.example.service.ActionParser
import com.example.service.AudioCaptureManager
import com.example.service.OganessonService
import com.example.service.SpeechManager
import com.example.service.TextToSpeechManager
import com.example.service.ThermalBatteryManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OganessonViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    val preferencesManager = PreferencesManager(context)
    val database = OganessonDatabase.getDatabase(context)
    val noteDao: NoteDao = database.noteDao()
    val commandCacheDao = database.commandCacheDao()
    val commandCacheRepository = CommandCacheRepository(commandCacheDao)
    val customVoiceMappingDao = database.customVoiceMappingDao()
    val customVoiceMappingRepository = CustomVoiceMappingRepository(customVoiceMappingDao)

    val mistralClient = MistralClient()
    val actionExecutor = ActionExecutor(context, noteDao)
    val speechManager = SpeechManager(context)
    val ttsManager = TextToSpeechManager(context)
    val audioCaptureManager = AudioCaptureManager(context)
    val thermalBatteryManager = ThermalBatteryManager(context)

    // Voice & Interaction State
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    val rmsLevel: StateFlow<Float> = speechManager.rmsLevel
    val partialSpeech: StateFlow<String> = speechManager.partialText

    private val _transcript = MutableStateFlow<List<TranscriptItem>>(emptyList())
    val transcript: StateFlow<List<TranscriptItem>> = _transcript.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    // Preferences Flows
    val mistralApiKey: StateFlow<String> = preferencesManager.mistralApiKey.stateIn(
        viewModelScope, SharingStarted.Eagerly, ""
    )
    val secondaryMistralApiKey: StateFlow<String> = preferencesManager.secondaryMistralApiKey.stateIn(
        viewModelScope, SharingStarted.Eagerly, ""
    )
    val mistralModel: StateFlow<String> = preferencesManager.mistralModel.stateIn(
        viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_MISTRAL_MODEL
    )
    val assistantLanguage: StateFlow<String> = preferencesManager.assistantLanguage.stateIn(
        viewModelScope, SharingStarted.Eagerly, "en-US"
    )
    val customWakeWord: StateFlow<String> = preferencesManager.customWakeWord.stateIn(
        viewModelScope, SharingStarted.Eagerly, ""
    )

    val continuousMode: StateFlow<Boolean> = preferencesManager.continuousMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )
    val persistenceMode: StateFlow<Boolean> = preferencesManager.persistenceMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )
    val ttsSpeed: StateFlow<Float> = preferencesManager.ttsSpeed.stateIn(
        viewModelScope, SharingStarted.Eagerly, 1.0f
    )
    val ttsPitch: StateFlow<Float> = preferencesManager.ttsPitch.stateIn(
        viewModelScope, SharingStarted.Eagerly, 1.0f
    )
    val ttsVoice: StateFlow<String> = preferencesManager.ttsVoice.stateIn(
        viewModelScope, SharingStarted.Eagerly, ""
    )
    val preferOffline: StateFlow<Boolean> = preferencesManager.preferOffline.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )
    val powerMode: StateFlow<String> = preferencesManager.powerMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_POWER_MODE
    )
    val maxTokens: StateFlow<Int> = preferencesManager.maxTokens.stateIn(
        viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_MAX_TOKENS
    )
    val backgroundWakeWord: StateFlow<Boolean> = preferencesManager.backgroundWakeWord.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )
    val persistentBackgroundListening: StateFlow<Boolean> = preferencesManager.persistentBackgroundListening.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )
    val backgroundPromptShown: StateFlow<Boolean> = preferencesManager.backgroundPromptShown.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    val notes: StateFlow<List<NoteEntity>> = noteDao.getAllNotes().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    data class ConnectionTestState(
        val isTesting: Boolean = false,
        val isValid: Boolean? = null,
        val message: String? = null,
        val timestamp: Long = 0L
    )

    private val _connectionTestState = MutableStateFlow(ConnectionTestState())
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    private val _secondaryConnectionTestState = MutableStateFlow(ConnectionTestState())
    val secondaryConnectionTestState: StateFlow<ConnectionTestState> = _secondaryConnectionTestState.asStateFlow()

    fun testMistralConnection(keyToTest: String? = null) {
        val key = PreferencesManager.sanitizeApiKey(keyToTest ?: mistralApiKey.value)
        if (key.isBlank()) {
            _connectionTestState.value = ConnectionTestState(
                isTesting = false,
                isValid = false,
                message = "No primary API key entered. Enter your Mistral key to test."
            )
            return
        }
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState(isTesting = true)
            val result = mistralClient.testConnection(key)
            result.onSuccess { msg ->
                _connectionTestState.value = ConnectionTestState(
                    isTesting = false,
                    isValid = true,
                    message = msg,
                    timestamp = System.currentTimeMillis()
                )
            }.onFailure { err ->
                _connectionTestState.value = ConnectionTestState(
                    isTesting = false,
                    isValid = false,
                    message = err.message ?: "Connection test failed",
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }

    fun testSecondaryMistralConnection(keyToTest: String? = null) {
        val key = PreferencesManager.sanitizeApiKey(keyToTest ?: secondaryMistralApiKey.value)
        if (key.isBlank()) {
            _secondaryConnectionTestState.value = ConnectionTestState(
                isTesting = false,
                isValid = false,
                message = "No secondary API key entered. Enter your backup Mistral key to test."
            )
            return
        }
        viewModelScope.launch {
            _secondaryConnectionTestState.value = ConnectionTestState(isTesting = true)
            val result = mistralClient.testConnection(key)
            result.onSuccess { msg ->
                _secondaryConnectionTestState.value = ConnectionTestState(
                    isTesting = false,
                    isValid = true,
                    message = "Fallback Key verified: $msg",
                    timestamp = System.currentTimeMillis()
                )
            }.onFailure { err ->
                _secondaryConnectionTestState.value = ConnectionTestState(
                    isTesting = false,
                    isValid = false,
                    message = err.message ?: "Secondary connection test failed",
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }


    val cachedCommands: StateFlow<List<CommandCacheEntity>> = commandCacheRepository.getFrequentlyUsedCommands(10).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val customVoiceMappings: StateFlow<List<CustomVoiceMappingEntity>> = customVoiceMappingRepository.allMappings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private var currentProcessingJob: Job? = null
    private var continuousLoopJob: Job? = null

    init {
        observeNetwork()
        observePreferences()
        setupSpeechCallbacks()
        setupTtsCallbacks()
        seedDefaultCustomShortcutsIfEmpty()

        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (backgroundWakeWord.value && hasAudio) {
            OganessonService.start(context)
        }
    }

    private fun seedDefaultCustomShortcutsIfEmpty() {
        viewModelScope.launch {
            if (customVoiceMappingRepository.getCount() == 0) {
                val defaults = listOf(
                    CustomVoiceMappingEntity(
                        phrase = "coffee time",
                        actionType = "MULTI_ACTION_MACRO",
                        target = "Open Spotify, play playlist Coffee Beats, set volume 50%",
                        spokenReply = "Starting Coffee Time: Opening Spotify, playing playlist, and setting volume to 50%",
                        isEnabled = true
                    ),
                    CustomVoiceMappingEntity(
                        phrase = "click picture",
                        actionType = "TAKE_PHOTO",
                        target = "",
                        spokenReply = "Opening camera to take photo",
                        isEnabled = true
                    ),
                    CustomVoiceMappingEntity(
                        phrase = "start recording",
                        actionType = "RECORD_VIDEO",
                        target = "",
                        spokenReply = "Opening camera for video recording",
                        isEnabled = true
                    ),
                    CustomVoiceMappingEntity(
                        phrase = "start voice recording",
                        actionType = "RECORD_AUDIO",
                        target = "",
                        spokenReply = "Opening voice recorder",
                        isEnabled = true
                    ),
                    CustomVoiceMappingEntity(
                        phrase = "turn on torch",
                        actionType = "TOGGLE_FLASHLIGHT",
                        target = "ON",
                        spokenReply = "Turning on flashlight",
                        isEnabled = true
                    ),
                    CustomVoiceMappingEntity(
                        phrase = "open battery",
                        actionType = "OPEN_BATTERY_SETTINGS",
                        target = "",
                        spokenReply = "Opening battery settings",
                        isEnabled = true
                    )
                )
                defaults.forEach { customVoiceMappingRepository.addMapping(it) }
            }
        }
    }

    private fun observeNetwork() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager?.registerNetworkCallback(
            networkRequest,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                }

                override fun onLost(network: Network) {
                    _isOnline.value = false
                }
            }
        )
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesManager.ttsSpeed.collect { ttsManager.setSpeechRate(it) }
        }
        viewModelScope.launch {
            preferencesManager.ttsPitch.collect { ttsManager.setPitch(it) }
        }
        viewModelScope.launch {
            preferencesManager.ttsVoice.collect { ttsManager.setVoice(it) }
        }
        viewModelScope.launch {
            preferencesManager.assistantLanguage.collect { lang ->
                ttsManager.setLanguage(lang)
            }
        }
    }

    private var isContinuousConversationActive: Boolean = false
    private var continuousInactivityJob: Job? = null
    private var consecutiveSpeechErrors = 0

    private fun setupSpeechCallbacks() {
        speechManager.onSpeechStart = {
            _voiceState.value = VoiceState.LISTENING
            _statusBanner.value = null
            resetContinuousInactivityTimeout()
        }

        speechManager.onSpeechPartial = { _ ->
            consecutiveSpeechErrors = 0
            resetContinuousInactivityTimeout()
        }

        speechManager.onSpeechResult = { recognizedText ->
            consecutiveSpeechErrors = 0
            continuousInactivityJob?.cancel()
            val text = recognizedText.trim()
            if (text.isNotBlank()) {
                handleUserSpeechInput(text)
            } else {
                if (isContinuousConversationActive || continuousMode.value) {
                    restartContinuousListeningLoop(delayMs = 800L)
                } else {
                    _voiceState.value = VoiceState.IDLE
                }
            }
        }

        speechManager.onSpeechError = { errorMessage ->
            Log.d("OganessonVM", "Speech recognizer error: $errorMessage")
            consecutiveSpeechErrors++
            if (isContinuousConversationActive || continuousMode.value) {
                if (consecutiveSpeechErrors >= 2 || thermalBatteryManager.isOverheated()) {
                    Log.d("OganessonVM", "Continuous conversation paused to conserve CPU/battery")
                    isContinuousConversationActive = false
                    _voiceState.value = VoiceState.IDLE
                    _statusBanner.value = "Standby • Tap mic or say 'Hi Oganesson'"
                } else {
                    _statusBanner.value = "Listening for command... (say 'stop' to finish)"
                    continuousLoopJob?.cancel()
                    continuousLoopJob = viewModelScope.launch {
                        val adaptiveBackoff = thermalBatteryManager.getAdaptiveBackoffDelayMs(1200L)
                        delay(adaptiveBackoff)
                        if (isContinuousConversationActive || continuousMode.value) {
                            startListeningInternal()
                        }
                    }
                }
            } else {
                _voiceState.value = VoiceState.ERROR
                _statusBanner.value = errorMessage
            }
        }

        speechManager.onSpeechEnd = {
            if (_voiceState.value == VoiceState.LISTENING) {
                _voiceState.value = VoiceState.PROCESSING
            }
        }
    }

    private fun setupTtsCallbacks() {
        ttsManager.onSpeechStarted = {
            _voiceState.value = VoiceState.SPEAKING
            continuousInactivityJob?.cancel()
        }

        ttsManager.onSpeechCompleted = {
            if (isContinuousConversationActive || continuousMode.value || persistenceMode.value) {
                restartContinuousListeningLoop(delayMs = 600L)
            } else {
                _voiceState.value = VoiceState.IDLE
            }
        }

        ttsManager.onSpeechError = { errorMsg ->
            if (isContinuousConversationActive || continuousMode.value || persistenceMode.value) {
                restartContinuousListeningLoop(delayMs = 800L)
            } else {
                _voiceState.value = VoiceState.IDLE
                _statusBanner.value = errorMsg
            }
        }
    }

    fun startListening() {
        isContinuousConversationActive = true
        startListeningInternal()
    }

    private fun startListeningInternal() {
        val wasSpeaking = _voiceState.value == VoiceState.SPEAKING
        ttsManager.stop()
        currentProcessingJob?.cancel()
        continuousLoopJob?.cancel()

        if (!speechManager.isAvailable()) {
            _voiceState.value = VoiceState.ERROR
            _statusBanner.value = "Speech recognition is not available on this device."
            return
        }

        _voiceState.value = VoiceState.LISTENING

        if (wasSpeaking) {
            viewModelScope.launch {
                delay(180)
                speechManager.startListening(
                    preferOffline = preferOffline.value,
                    languageCode = assistantLanguage.value
                )
            }
        } else {
            speechManager.startListening(
                preferOffline = preferOffline.value,
                languageCode = assistantLanguage.value
            )
        }
    }

    fun toggleListening() {
        if (_voiceState.value == VoiceState.LISTENING || _voiceState.value == VoiceState.PROCESSING || _voiceState.value == VoiceState.SPEAKING) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun stopListening() {
        isContinuousConversationActive = false
        continuousLoopJob?.cancel()
        continuousInactivityJob?.cancel()
        speechManager.stopListening()
        ttsManager.stop()
        _voiceState.value = VoiceState.IDLE
        _statusBanner.value = null
    }

    fun cancelInteraction() {
        isContinuousConversationActive = false
        speechManager.cancelListening()
        ttsManager.stop()
        currentProcessingJob?.cancel()
        continuousLoopJob?.cancel()
        continuousInactivityJob?.cancel()
        _voiceState.value = VoiceState.IDLE
        _statusBanner.value = null
    }

    private fun restartContinuousListeningLoop(delayMs: Long = 600L) {
        continuousLoopJob?.cancel()
        val adaptiveDelay = thermalBatteryManager.getAdaptiveBackoffDelayMs(delayMs)
        continuousLoopJob = viewModelScope.launch {
            delay(adaptiveDelay)
            if (isContinuousConversationActive || continuousMode.value || persistenceMode.value) {
                if (_voiceState.value != VoiceState.PROCESSING) {
                    _statusBanner.value = "Listening for command... (say 'stop' to finish)"
                    startListeningInternal()
                    resetContinuousInactivityTimeout()
                }
            } else {
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    private fun resetContinuousInactivityTimeout() {
        continuousInactivityJob?.cancel()
        continuousInactivityJob = viewModelScope.launch {
            // Keep continuous conversation active for 25 seconds of silence before going IDLE
            delay(25_000L)
            if (_voiceState.value == VoiceState.LISTENING) {
                isContinuousConversationActive = false
                speechManager.stopListening()
                _voiceState.value = VoiceState.IDLE
                _statusBanner.value = null
            }
        }
    }

    fun handleUserSpeechInput(inputText: String) {
        val cleanInput = inputText.trim()
        if (cleanInput.isBlank()) return

        // Check if user requested termination of continuous conversation
        val lower = cleanInput.lowercase()
        val isTerminationPhrase = lower in listOf(
            "stop", "cancel", "bye", "goodbye", "exit", "quit", "sleep", "close", "shut up", "dismiss"
        ) || lower == "stop listening" || lower == "turn off" || lower == "stop assistant"

        if (isTerminationPhrase) {
            isContinuousConversationActive = false
            continuousLoopJob?.cancel()
            continuousInactivityJob?.cancel()
            speechManager.stopListening()

            val userItem = TranscriptItem(
                sender = SenderType.USER,
                text = cleanInput
            )
            val byeReply = "Goodbye! Voice assistant paused."
            val byeItem = TranscriptItem(
                sender = SenderType.ASSISTANT,
                text = byeReply
            )
            _transcript.value = _transcript.value + userItem + byeItem
            ttsManager.onSpeechCompleted = {
                _voiceState.value = VoiceState.IDLE
                _statusBanner.value = null
            }
            ttsManager.onSpeechError = {
                _voiceState.value = VoiceState.IDLE
                _statusBanner.value = null
            }
            ttsManager.speak(byeReply)
            return
        }

        // Enable continuous mode for active conversation session
        isContinuousConversationActive = true

        val userItem = TranscriptItem(
            sender = SenderType.USER,
            text = cleanInput
        )
        _transcript.value = _transcript.value + userItem

        processQueryWithIntelligence(cleanInput)
    }

    fun processUserInput(text: String) = handleUserSpeechInput(text)

    private fun processQueryWithIntelligence(cleanInput: String) {
        currentProcessingJob?.cancel()
        currentProcessingJob = viewModelScope.launch {
            _voiceState.value = VoiceState.PROCESSING

            // 0a. Check Custom Voice Shortcuts (Zero AI Round-Trip)
            val customShortcut = customVoiceMappingRepository.findMatchingMapping(cleanInput)
            if (customShortcut != null) {
                var actionSummary: String? = null
                val actionType = customShortcut.actionType
                if (customShortcut.actionType == "MULTI_ACTION_MACRO") {
                    val macroActions = ActionParser.parseMacroSteps(customShortcut.target)
                    if (macroActions.isNotEmpty()) {
                        val results = actionExecutor.executeActions(macroActions)
                        actionSummary = results.joinToString(" • ") { it.message }
                    } else {
                        val fallback = ActionParser.parseLocalAction(customShortcut.target)
                        val actions = fallback.payload?.getAllActions() ?: emptyList()
                        if (actions.isNotEmpty()) {
                            val results = actionExecutor.executeActions(actions)
                            actionSummary = results.joinToString(" • ") { it.message }
                        }
                    }
                } else {
                    val action = ActionDetails(
                        type = customShortcut.actionType,
                        parameters = when (customShortcut.actionType) {
                            "OPEN_APP" -> mapOf("app" to customShortcut.target)
                            "TOGGLE_WIFI", "OPEN_WIFI_SETTINGS" -> if (customShortcut.target.isNotBlank()) mapOf("state" to customShortcut.target) else emptyMap()
                            "CUSTOM_INTENT" -> mapOf("action" to customShortcut.target)
                            else -> if (customShortcut.target.isNotBlank()) mapOf("state" to customShortcut.target, "query" to customShortcut.target) else emptyMap()
                        }
                    )
                    val execResult = actionExecutor.executeAction(action)
                    actionSummary = execResult.message
                }
                customVoiceMappingRepository.incrementUsage(customShortcut.id)

                val reply = customShortcut.spokenReply.ifBlank { actionSummary ?: "Shortcut \"${customShortcut.phrase}\" executed." }
                val item = TranscriptItem(
                    sender = SenderType.ASSISTANT,
                    text = reply,
                    actionType = actionType,
                    actionSummary = actionSummary,
                    isActionExecuted = true
                )
                _transcript.value = _transcript.value + item
                ttsManager.speak(reply)
                return@launch
            }

            // 0b. Check Local Offline Actions (Zero AI Round-Trip, supports dual/multi actions)
            val localResult = ActionParser.parseLocalAction(cleanInput)
            if (localResult.isHandledLocally && localResult.payload != null) {
                val payload = localResult.payload
                val reply = payload.spokenReply ?: "Executing command."
                val actions = payload.getAllActions()

                var actionSummary: String? = null
                var actionType: String? = null

                if (actions.isNotEmpty()) {
                    if (actions.size > 1) {
                        actionType = actions.joinToString(", ") { it.type }
                        val results = actionExecutor.executeActions(actions)
                        actionSummary = results.joinToString(" • ") { it.message }
                    } else if (actions[0].type.uppercase() != "NONE") {
                        actionType = actions[0].type
                        val execution = actionExecutor.executeAction(actions[0])
                        actionSummary = execution.message
                    }
                }

                val assistantItem = TranscriptItem(
                    sender = SenderType.ASSISTANT,
                    text = reply,
                    actionType = actionType,
                    actionSummary = actionSummary,
                    isActionExecuted = actionSummary != null
                )
                _transcript.value = _transcript.value + assistantItem
                ttsManager.speak(reply)
                return@launch
            }

            // 1. Check local Room database cache (Instant local response & rate-limit avoidance)
            val cachedPayload = commandCacheRepository.getCachedPayload(cleanInput)
            val apiKey = mistralApiKey.value
            val isNetworkAvailable = _isOnline.value

            if (cachedPayload != null) {
                val reply = cachedPayload.spokenReply ?: "Done."
                val actions = cachedPayload.getAllActions()

                var actionSummary: String? = null
                var actionType: String? = null

                if (actions.isNotEmpty()) {
                    if (actions.size > 1) {
                        actionType = actions.joinToString(", ") { it.type }
                        val results = actionExecutor.executeActions(actions)
                        actionSummary = results.joinToString(" • ") { it.message }
                    } else if (actions[0].type.uppercase() != "NONE") {
                        actionType = actions[0].type
                        val execution = actionExecutor.executeAction(actions[0])
                        actionSummary = execution.message
                    }
                }

                val cachedItem = TranscriptItem(
                    sender = SenderType.ASSISTANT,
                    text = reply,
                    actionType = actionType,
                    actionSummary = actionSummary,
                    isActionExecuted = actionSummary != null
                )
                _transcript.value = _transcript.value + cachedItem
                commandCacheRepository.saveOrUpdateCommand(cleanInput, cachedPayload)
                ttsManager.speak(reply)
                return@launch
            }

            // If offline and not in cache
            if (!isNetworkAvailable) {
                val offlineReply = "I'm currently offline and this command is not cached yet. Please check your internet connection."
                val offlineItem = TranscriptItem(
                    sender = SenderType.ASSISTANT,
                    text = offlineReply
                )
                _transcript.value = _transcript.value + offlineItem
                ttsManager.speak(offlineReply)
                return@launch
            }

            // Check API key (user primary key, secondary fallback key, or baked fallback key)
            val primaryKey = mistralApiKey.value
            val secondaryKey = secondaryMistralApiKey.value
            val effectiveKey = PreferencesManager.getEffectiveApiKey(primaryKey, secondaryKey)

            if (effectiveKey.isBlank()) {
                val missingKeyReply = "Please enter your Mistral AI API key in Settings to enable full intelligence."
                val keyItem = TranscriptItem(
                    sender = SenderType.SYSTEM,
                    text = missingKeyReply
                )
                _transcript.value = _transcript.value + keyItem
                ttsManager.speak(missingKeyReply)
                return@launch
            }

            // Build conversation history for Mistral AI
            val history = _transcript.value.mapNotNull { item ->
                when (item.sender) {
                    SenderType.USER -> MistralMessage(role = "user", content = item.text)
                    SenderType.ASSISTANT -> MistralMessage(role = "assistant", content = item.text)
                    SenderType.SYSTEM -> null
                }
            }

            // Query Mistral AI with dual key fallback & thermal adaptive model
            val effectiveModel = thermalBatteryManager.getEffectiveModel(mistralModel.value)
            val result = mistralClient.queryMistral(
                apiKey = primaryKey,
                secondaryApiKey = secondaryKey,
                model = effectiveModel,
                conversationHistory = history,
                userMessage = cleanInput,
                maxTokens = maxTokens.value
            )

            result.onSuccess { payload ->
                val reply = payload.spokenReply ?: "Done."
                val actions = payload.getAllActions()

                var actionSummary: String? = null
                var actionType: String? = null

                if (actions.isNotEmpty()) {
                    if (actions.size > 1) {
                        actionType = actions.joinToString(", ") { it.type }
                        val results = actionExecutor.executeActions(actions)
                        actionSummary = results.joinToString(" • ") { it.message }
                    } else if (actions[0].type.uppercase() != "NONE") {
                        actionType = actions[0].type
                        val execution = actionExecutor.executeAction(actions[0])
                        actionSummary = execution.message
                    }
                }

                val assistantItem = TranscriptItem(
                    sender = SenderType.ASSISTANT,
                    text = reply,
                    actionType = actionType,
                    actionSummary = actionSummary,
                    isActionExecuted = actionSummary != null
                )
                _transcript.value = _transcript.value + assistantItem

                commandCacheRepository.saveOrUpdateCommand(cleanInput, payload)
                ttsManager.speak(reply)
            }.onFailure { exception ->
                if (cachedPayload != null) {
                    val reply = cachedPayload.spokenReply ?: "Done."
                    val actions = cachedPayload.getAllActions()
                    var actionSummary: String? = null
                    var actionType: String? = null

                    if (actions.isNotEmpty()) {
                        if (actions.size > 1) {
                            actionType = actions.joinToString(", ") { it.type }
                            val results = actionExecutor.executeActions(actions)
                            actionSummary = results.joinToString(" • ") { it.message }
                        } else if (actions[0].type.uppercase() != "NONE") {
                            actionType = actions[0].type
                            val execution = actionExecutor.executeAction(actions[0])
                            actionSummary = execution.message
                        }
                    }

                    val cachedItem = TranscriptItem(
                        sender = SenderType.ASSISTANT,
                        text = "$reply [Fallback Cache]",
                        actionType = actionType,
                        actionSummary = actionSummary,
                        isActionExecuted = actionSummary != null
                    )
                    _transcript.value = _transcript.value + cachedItem
                    commandCacheRepository.saveOrUpdateCommand(cleanInput, cachedPayload)
                    ttsManager.speak(reply)
                    return@launch
                }

                _voiceState.value = VoiceState.ERROR
                val errorMsg = "Trouble connecting to Mistral AI: ${exception.localizedMessage ?: "Unknown error"}"
                _statusBanner.value = errorMsg

                val errorItem = TranscriptItem(
                    sender = SenderType.SYSTEM,
                    text = "Error: ${exception.localizedMessage}. Please check your Mistral AI API key and network."
                )
                _transcript.value = _transcript.value + errorItem
                ttsManager.speak("Sorry, I had trouble reaching Mistral AI. Please check your settings.")
            }
        }
    }

    fun clearTranscript() {
        _transcript.value = emptyList()
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            noteDao.deleteNoteById(noteId)
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.deleteNote(note)
        }
    }

    fun updateMistralApiKey(apiKey: String) {
        viewModelScope.launch {
            preferencesManager.setMistralApiKey(apiKey)
        }
    }

    fun saveMistralApiKey(apiKey: String) = updateMistralApiKey(apiKey)

    fun updateSecondaryMistralApiKey(apiKey: String) {
        viewModelScope.launch {
            preferencesManager.setSecondaryMistralApiKey(apiKey)
        }
    }

    fun saveSecondaryMistralApiKey(apiKey: String) = updateSecondaryMistralApiKey(apiKey)

    fun updateMistralModel(model: String) {
        viewModelScope.launch {
            preferencesManager.setMistralModel(model)
        }
    }

    fun saveMistralModel(model: String) = updateMistralModel(model)

    fun updateAssistantLanguage(languageCode: String) {
        viewModelScope.launch {
            preferencesManager.setAssistantLanguage(languageCode)
        }
    }

    fun updateCustomWakeWord(wakeWord: String) {
        viewModelScope.launch {
            preferencesManager.setCustomWakeWord(wakeWord)
        }
    }

    fun updatePowerMode(mode: String) {
        viewModelScope.launch {
            preferencesManager.setPowerMode(mode)
            thermalBatteryManager.isEcoModeActive = (mode == PreferencesManager.POWER_MODE_ECO)
        }
    }

    fun updateMaxTokens(tokens: Int) {
        viewModelScope.launch {
            preferencesManager.setMaxTokens(tokens)
        }
    }

    fun updateContinuousMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setContinuousMode(enabled)
        }
    }

    fun setContinuousMode(enabled: Boolean) = updateContinuousMode(enabled)

    fun updatePersistenceMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setPersistenceMode(enabled)
        }
    }

    fun setPersistenceMode(enabled: Boolean) = updatePersistenceMode(enabled)

    fun updateBackgroundWakeWord(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setBackgroundWakeWord(enabled)
            if (enabled) {
                val hasAudio = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasAudio) {
                    OganessonService.start(context)
                }
            } else {
                OganessonService.stop(context)
            }
        }
    }

    fun setBackgroundWakeWord(enabled: Boolean) = updateBackgroundWakeWord(enabled)

    fun updatePersistentBackgroundListening(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setPersistentBackgroundListening(enabled)
            if (enabled) {
                preferencesManager.setBackgroundWakeWord(true)
                val hasAudio = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasAudio) {
                    OganessonService.start(context)
                }
            }
        }
    }

    fun markBackgroundPromptShown() {
        viewModelScope.launch {
            preferencesManager.setBackgroundPromptShown(true)
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    fun updateTtsSpeed(speed: Float) {
        viewModelScope.launch {
            preferencesManager.setTtsSpeed(speed)
        }
    }

    fun setTtsSpeed(speed: Float) = updateTtsSpeed(speed)

    fun updateTtsPitch(pitch: Float) {
        viewModelScope.launch {
            preferencesManager.setTtsPitch(pitch)
        }
    }

    fun setTtsPitch(pitch: Float) = updateTtsPitch(pitch)

    fun updateTtsVoice(voiceName: String) {
        viewModelScope.launch {
            preferencesManager.setTtsVoice(voiceName)
        }
    }

    fun setTtsVoice(voiceName: String) = updateTtsVoice(voiceName)

    fun updatePreferOffline(prefer: Boolean) {
        viewModelScope.launch {
            preferencesManager.setPreferOffline(prefer)
        }
    }

    fun setPreferOffline(prefer: Boolean) = updatePreferOffline(prefer)

    fun applyVoicePreset(preset: String) {
        when (preset.lowercase()) {
            "natural" -> {
                updateTtsSpeed(1.05f)
                updateTtsPitch(1.0f)
            }
            "warm" -> {
                updateTtsSpeed(0.92f)
                updateTtsPitch(0.92f)
            }
            "brisk" -> {
                updateTtsSpeed(1.25f)
                updateTtsPitch(1.02f)
            }
            "deep" -> {
                updateTtsSpeed(0.95f)
                updateTtsPitch(0.82f)
            }
        }
    }

    fun testTtsSpeech(text: String) {
        ttsManager.speak(text)
    }

    fun addCustomVoiceMapping(
        phrase: String,
        actionType: String,
        target: String,
        spokenReply: String
    ) {
        viewModelScope.launch {
            val entity = CustomVoiceMappingEntity(
                phrase = phrase.trim(),
                actionType = actionType.trim(),
                target = target.trim(),
                spokenReply = spokenReply.trim().ifBlank { "Executing shortcut" },
                isEnabled = true
            )
            customVoiceMappingRepository.addMapping(entity)
        }
    }

    fun updateCustomVoiceMapping(mapping: CustomVoiceMappingEntity) {
        viewModelScope.launch {
            customVoiceMappingRepository.updateMapping(mapping)
        }
    }

    fun deleteCustomVoiceMapping(mapping: CustomVoiceMappingEntity) {
        viewModelScope.launch {
            customVoiceMappingRepository.deleteMapping(mapping)
        }
    }

    fun deleteCustomVoiceMapping(id: Long) {
        viewModelScope.launch {
            customVoiceMappingRepository.deleteById(id)
        }
    }

    fun toggleCustomVoiceMapping(mapping: CustomVoiceMappingEntity) {
        viewModelScope.launch {
            customVoiceMappingRepository.updateMapping(mapping.copy(isEnabled = !mapping.isEnabled))
        }
    }

    override fun onCleared() {
        super.onCleared()
        thermalBatteryManager.cleanup()
        audioCaptureManager.release()
        speechManager.destroy()
        ttsManager.shutdown()
        currentProcessingJob?.cancel()
        continuousLoopJob?.cancel()
    }
}
