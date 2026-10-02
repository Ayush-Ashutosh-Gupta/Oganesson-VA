package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import com.example.service.OganessonService
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.VoiceState
import com.example.ui.components.MicPulseButton
import com.example.ui.components.QuickPrompts
import com.example.ui.components.TranscriptView
import com.example.ui.theme.AtomicCyan
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.StateError
import com.example.ui.theme.StateListening
import com.example.ui.theme.StateSpeaking
import com.example.ui.theme.StateThinking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: OganessonViewModel,
    onOpenSettings: () -> Unit,
    onOpenCustomShortcuts: () -> Unit = {},
    onRequestPermissions: () -> Unit,
    onRequestBatteryOptimization: () -> Unit = {}
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val rmsLevel by viewModel.rmsLevel.collectAsState()
    val partialSpeech by viewModel.partialSpeech.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()
    val continuousMode by viewModel.continuousMode.collectAsState()
    val backgroundWakeWord by viewModel.backgroundWakeWord.collectAsState()
    val persistentBackgroundListening by viewModel.persistentBackgroundListening.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val cachedCommands by viewModel.cachedCommands.collectAsState()
    val customVoiceMappings by viewModel.customVoiceMappings.collectAsState()

    var isBatteryOptimized by remember { mutableStateOf(!viewModel.isIgnoringBatteryOptimizations()) }
    var showBackgroundPrompt by remember { mutableStateOf(!persistentBackgroundListening || isBatteryOptimized) }
    var showNotesSheet by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var showTextInputBar by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto-scroll transcript when new items arrive
    LaunchedEffect(transcript.size, partialSpeech) {
        if (transcript.isNotEmpty()) {
            listState.animateScrollToItem(transcript.size)
        }
    }

    // Permission launcher for microphone
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (recordAudioGranted) {
            viewModel.startListening()
        }
    }

    fun handleMicTap() {
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudio) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.SEND_SMS
                )
            )
        } else {
            viewModel.toggleListening()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AtomicCyan.copy(alpha = 0.2f))
                                .border(1.dp, AtomicCyan, CircleShape)
                        ) {
                            Text(
                                text = "Og",
                                color = AtomicCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Oganesson",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isOnline) Color(0xFF00E676) else StateError)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isOnline) "Online" else "Offline",
                                    fontSize = 11.sp,
                                    color = if (isOnline) Color(0xFF00E676) else StateError
                                )
                                if (continuousMode) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• Continuous",
                                        fontSize = 11.sp,
                                        color = AtomicCyan
                                    )
                                }
                                if (persistentBackgroundListening) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• Background Mic ON",
                                        fontSize = 11.sp,
                                        color = Color(0xFF00E676)
                                    )
                                } else if (backgroundWakeWord) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• Wake: \"Hi Oganesson\"",
                                        fontSize = 11.sp,
                                        color = AtomicCyan
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    // Custom Voice Shortcuts (Zero AI Round-Trip)
                    IconButton(
                        onClick = onOpenCustomShortcuts,
                        modifier = Modifier.testTag("open_shortcuts_topbar_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (customVoiceMappings.isNotEmpty()) {
                                    Badge(containerColor = AtomicCyan) {
                                        Text(
                                            text = customVoiceMappings.size.toString(),
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = "Custom Voice Shortcuts",
                                tint = if (customVoiceMappings.isNotEmpty()) AtomicCyan else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    // Notes & Reminders Drawer icon
                    IconButton(
                        onClick = { showNotesSheet = true },
                        modifier = Modifier.testTag("open_notes_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (notes.isNotEmpty()) {
                                    Badge(containerColor = AtomicCyan) {
                                        Text(
                                            text = notes.size.toString(),
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventNote,
                                contentDescription = "View voice notes and reminders",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    // Floating Overlay Quick Summon icon
                    IconButton(
                        onClick = {
                            OganessonService.triggerWake(context)
                        },
                        modifier = Modifier.testTag("summon_floating_overlay_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Open Floating Assistant Overlay",
                            tint = AtomicCyan
                        )
                    }

                    // Settings Icon
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Status or Error Notification Banner
            AnimatedVisibility(
                visible = statusBanner != null || !isOnline,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                Surface(
                    color = if (!isOnline) StateError.copy(alpha = 0.2f) else DarkSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (!isOnline) StateError else DarkBorder
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (!isOnline) {
                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = StateError,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Offline: check your internet connection.",
                                    fontSize = 12.sp,
                                    color = StateError
                                )
                            } else if (statusBanner != null) {
                                Text(
                                    text = statusBanner ?: "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.dismissBanner() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss banner",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Ask user to continuously run in background and listen
            AnimatedVisibility(
                visible = showBackgroundPrompt && (!persistentBackgroundListening || isBatteryOptimized),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AtomicCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("persistent_background_prompt_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(AtomicCyan.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Hearing,
                                        contentDescription = null,
                                        tint = AtomicCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Continuous Background Listening",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }

                            IconButton(
                                onClick = {
                                    showBackgroundPrompt = false
                                    viewModel.markBackgroundPromptShown()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Keep Oganesson's microphone persistently running in the background to hear \"Hi Oganesson\" and continuous commands hands-free even when using other apps or with the screen locked.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    viewModel.updatePersistentBackgroundListening(true)
                                    onRequestPermissions()
                                    onRequestBatteryOptimization()
                                    isBatteryOptimized = !viewModel.isIgnoringBatteryOptimizations()
                                    showBackgroundPrompt = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AtomicCyan),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("enable_background_listening_button")
                            ) {
                                Text(
                                    text = "Enable Background Mic",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    showBackgroundPrompt = false
                                    viewModel.markBackgroundPromptShown()
                                },
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.testTag("dismiss_background_listening_button")
                            ) {
                                Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Conversation Transcript Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                TranscriptView(
                    transcript = transcript,
                    partialSpeech = partialSpeech,
                    listState = listState,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Quick Prompts Suggestions Carousel (including frequently used cached commands)
            QuickPrompts(
                onPromptClick = { promptText ->
                    viewModel.processUserInput(promptText)
                },
                cachedPrompts = cachedCommands.map { it.rawQuery },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            // Optional Keyboard Text Input Bar
            AnimatedVisibility(visible = showTextInputBar) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Ask or tell Oganesson anything...", fontSize = 14.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (textInput.isNotBlank()) {
                                viewModel.processUserInput(textInput)
                                textInput = ""
                                showTextInputBar = false
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AtomicCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("text_query_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.processUserInput(textInput)
                                textInput = ""
                                showTextInputBar = false
                            }
                        },
                        modifier = Modifier.testTag("send_text_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = AtomicCyan
                        )
                    }
                }
            }

            // Bottom Voice Control Hub
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, top = 6.dp)
            ) {
                // Interactive Mic Button
                MicPulseButton(
                    voiceState = voiceState,
                    rmsLevel = rmsLevel,
                    onClick = { handleMicTap() }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Status text below mic
                val statusText = when (voiceState) {
                    VoiceState.LISTENING -> "Listening... Speak now"
                    VoiceState.THINKING, VoiceState.PROCESSING -> "Thinking with Mistral AI..."
                    VoiceState.SPEAKING -> "Tap to stop speaking"
                    VoiceState.ERROR -> "Tap to retry"
                    VoiceState.IDLE -> when {
                        continuousMode -> "Continuous mode • Tap to pause"
                        backgroundWakeWord -> "Tap or say \"Hi Oganesson\""
                        else -> "Tap to speak"
                    }
                }

                val statusColor = when (voiceState) {
                    VoiceState.LISTENING -> StateListening
                    VoiceState.THINKING, VoiceState.PROCESSING -> StateThinking
                    VoiceState.SPEAKING -> StateSpeaking
                    VoiceState.ERROR -> StateError
                    VoiceState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = statusText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    // Keyboard toggle for silent environment
                    IconButton(
                        onClick = { showTextInputBar = !showTextInputBar },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("toggle_keyboard_button")
                    ) {
                        Icon(
                            imageVector = if (showTextInputBar) Icons.Default.Mic else Icons.Default.Keyboard,
                            contentDescription = "Toggle text input",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // Voice Notes Sheet
    if (showNotesSheet) {
        NotesSheet(
            notes = notes,
            onDeleteNote = { viewModel.deleteNote(it) },
            onDismiss = { showNotesSheet = false }
        )
    }
}
