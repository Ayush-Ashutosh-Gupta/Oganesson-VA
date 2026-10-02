package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomVoiceMappingEntity
import com.example.ui.theme.AtomicCyan
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricBlue

/**
 * Dedicated settings screen where users map custom voice phrases directly to Android intents
 * (e.g. Click Picture, Start Video Recording, Voice Recording, Toggling Wi-Fi, Flashlight, Battery)
 * completely locally with zero AI round-trip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomShortcutsScreen(
    viewModel: OganessonViewModel,
    onBack: () -> Unit
) {
    val mappings by viewModel.customVoiceMappings.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Command Shortcuts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Multi-Step Action Macros & Direct Voice Phrases",
                            fontSize = 11.sp,
                            color = AtomicCyan
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("custom_shortcuts_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_shortcut_appbar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add custom shortcut",
                            tint = AtomicCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AtomicCyan,
                contentColor = Color.Black,
                modifier = Modifier.testTag("add_shortcut_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Voice Shortcut")
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tutorial & How-To Guide Card
            item {
                ShortcutsTutorialCard(
                    isExpanded = showTutorial,
                    onToggleExpand = { showTutorial = !showTutorial }
                )
            }

            // Quick Preset Suggestions
            item {
                PresetSuggestionsCard(
                    onAddPreset = { phrase, actionType, target, reply ->
                        viewModel.addCustomVoiceMapping(phrase, actionType, target, reply)
                    }
                )
            }

            // Header for configured shortcuts
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Configured Shortcuts (${mappings.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Saved in offline Room database",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (mappings.isEmpty()) {
                item {
                    EmptyShortcutsView(onAddClick = { showAddDialog = true })
                }
            } else {
                items(mappings, key = { it.id }) { mapping ->
                    ShortcutItemCard(
                        mapping = mapping,
                        onToggle = { viewModel.toggleCustomVoiceMapping(mapping) },
                        onDelete = { viewModel.deleteCustomVoiceMapping(mapping.id) },
                        onTest = {
                            viewModel.handleUserSpeechInput(mapping.phrase)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showAddDialog) {
        AddShortcutDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { phrase, actionType, target, reply ->
                viewModel.addCustomVoiceMapping(phrase, actionType, target, reply)
                showAddDialog = false
            }
        )
    }
}

/**
 * Interactive Tutorial / Guide explaining how to create and use custom voice shortcuts
 */
@Composable
fun ShortcutsTutorialCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, AtomicCyan.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = AtomicCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "How to Create & Use Shortcuts",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AtomicCyan
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "Custom shortcuts execute device commands directly with ZERO round-trip latency without waiting for any AI model:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    TutorialStepItem(
                        number = "1",
                        title = "Define your Trigger Phrase",
                        description = "Choose any phrase you want to say, e.g. \"Coffee Time\", \"Workout Mode\", or \"click picture\"."
                    )
                    TutorialStepItem(
                        number = "2",
                        title = "Build Multi-Step Action Macros or Pick Instant Actions",
                        description = "Chain complex actions together (e.g. \"Open Spotify, play playlist X, set volume 50%\") or trigger instant device intents."
                    )
                    TutorialStepItem(
                        number = "3",
                        title = "Customize the Voice Confirmation",
                        description = "Oganesson speaks your custom voice reply before or while executing each step in sequence."
                    )
                    TutorialStepItem(
                        number = "4",
                        title = "Trigger Seamlessly with 3s Persistence Mode",
                        description = "Say your trigger phrase anytime. Thanks to Persistence Mode, the mic stays active for 3s after execution for any follow-up commands."
                    )
                }
            }
        }
    }
}

@Composable
fun TutorialStepItem(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(AtomicCyan.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AtomicCyan
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun ShortcutItemCard(
    mapping: CustomVoiceMappingEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(
            1.dp,
            if (mapping.isEnabled) AtomicCyan.copy(alpha = 0.35f) else DarkBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shortcut_card_${mapping.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Trigger Phrase, Action badge, and Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (mapping.isEnabled) AtomicCyan.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.1f)
                            )
                    ) {
                        Icon(
                            imageVector = getIntentIcon(mapping.actionType),
                            contentDescription = null,
                            tint = if (mapping.isEnabled) AtomicCyan else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "\"${mapping.phrase}\"",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (mapping.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = if (mapping.isEnabled) AtomicCyan.copy(alpha = 0.18f) else DarkSurfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = formatActionType(mapping.actionType),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (mapping.isEnabled) AtomicCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (mapping.target.isNotBlank()) {
                                Text(
                                    text = "→ ${mapping.target}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Switch(
                    checked = mapping.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = AtomicCyan
                    ),
                    modifier = Modifier.testTag("toggle_shortcut_${mapping.id}")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (mapping.actionType == "MULTI_ACTION_MACRO" && mapping.target.isNotBlank()) {
                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AtomicCyan.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            text = "Macro Action Steps:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AtomicCyan
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        val steps = mapping.target.split(Regex("[,;\n]")).map { it.trim() }.filter { it.isNotBlank() }
                        steps.forEachIndexed { idx, step ->
                            Row(
                                modifier = Modifier.padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}. ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AtomicCyan
                                )
                                Text(
                                    text = step,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Details and Spoken reply
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Voice reply: ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "\"${mapping.spokenReply}\"",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Actions row (Test voice trigger & Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Triggered ${mapping.usageCount} time${if (mapping.usageCount != 1) "s" else ""}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    OutlinedButton(
                        onClick = onTest,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("test_shortcut_${mapping.id}"),
                        border = BorderStroke(1.dp, AtomicCyan.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Test trigger",
                            tint = AtomicCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test", fontSize = 11.sp, color = AtomicCyan)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_shortcut_${mapping.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete shortcut",
                            tint = Color(0xFFFF5252).copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PresetSuggestionsCard(
    onAddPreset: (phrase: String, actionType: String, target: String, reply: String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ElectricBolt,
                    contentDescription = null,
                    tint = AtomicCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "One-Click Quick Presets",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AtomicCyan
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap any preset to add instantly to your shortcuts library:",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            val presets = listOf(
                PresetShortcut("coffee time", "MULTI_ACTION_MACRO", "Open Spotify, play playlist Coffee Beats, set volume 50%", "Starting Coffee Time macro"),
                PresetShortcut("workout mode", "MULTI_ACTION_MACRO", "Open Spotify, play playlist Gym Workout, set volume 80%", "Starting Workout mode"),
                PresetShortcut("bedtime", "MULTI_ACTION_MACRO", "Turn off flashlight, set volume 15%, open clock", "Goodnight, preparing for bed"),
                PresetShortcut("commute", "MULTI_ACTION_MACRO", "Open Google Maps, set volume 70%, open Spotify", "Starting commute mode"),
                PresetShortcut("click picture", "TAKE_PHOTO", "", "Opening camera to take photo"),
                PresetShortcut("start recording", "RECORD_VIDEO", "", "Opening camera for video recording"),
                PresetShortcut("start voice recording", "RECORD_AUDIO", "", "Opening voice recorder"),
                PresetShortcut("turn on torch", "TOGGLE_FLASHLIGHT", "ON", "Turning on flashlight"),
                PresetShortcut("turn off torch", "TOGGLE_FLASHLIGHT", "OFF", "Turning off flashlight"),
                PresetShortcut("open battery", "OPEN_BATTERY_SETTINGS", "", "Opening battery settings"),
                PresetShortcut("turn on wifi", "TOGGLE_WIFI", "ON", "Opening Wi-Fi controls"),
                PresetShortcut("open youtube", "OPEN_APP", "youtube", "Opening YouTube")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.chunked(2).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPresets.forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurfaceVariant,
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onAddPreset(
                                            preset.phrase,
                                            preset.actionType,
                                            preset.target,
                                            preset.reply
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = getIntentIcon(preset.actionType),
                                        contentDescription = null,
                                        tint = AtomicCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "\"${preset.phrase}\"",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        if (rowPresets.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

data class PresetShortcut(
    val phrase: String,
    val actionType: String,
    val target: String,
    val reply: String
)

@Composable
fun EmptyShortcutsView(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(DarkSurfaceVariant)
        ) {
            Icon(
                imageVector = Icons.Default.Hearing,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No Custom Voice Shortcuts Yet",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Create shortcuts for clicking pictures, recording, or opening apps with 0ms latency.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onAddClick,
            colors = ButtonDefaults.buttonColors(containerColor = AtomicCyan, contentColor = Color.Black),
            modifier = Modifier.testTag("empty_add_shortcut_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Create Your First Shortcut")
        }
    }
}

data class IntentTypeOption(
    val type: String,
    val label: String,
    val icon: ImageVector,
    val defaultTarget: String = "",
    val defaultReply: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShortcutDialog(
    onDismiss: () -> Unit,
    onConfirm: (phrase: String, actionType: String, target: String, reply: String) -> Unit
) {
    var phrase by remember { mutableStateOf("") }
    var actionType by remember { mutableStateOf("MULTI_ACTION_MACRO") }
    var target by remember { mutableStateOf("Open Spotify, play playlist Coffee Beats, set volume 50%") }
    var spokenReply by remember { mutableStateOf("Starting Coffee Time macro") }
    var actionExpanded by remember { mutableStateOf(false) }

    val intentOptions = listOf(
        IntentTypeOption("MULTI_ACTION_MACRO", "Multi-Step Action Macro (e.g. Spotify + Playlist + Volume)", Icons.Default.PlayArrow, "Open Spotify, play playlist Coffee Beats, set volume 50%", "Starting Coffee Time macro"),
        IntentTypeOption("TAKE_PHOTO", "Click Picture / Capture Photo", Icons.Default.CameraAlt, "", "Opening camera to capture picture"),
        IntentTypeOption("RECORD_VIDEO", "Start Recording Video", Icons.Default.Videocam, "", "Opening camera for video recording"),
        IntentTypeOption("RECORD_AUDIO", "Start Voice Recording", Icons.Default.Mic, "", "Opening audio voice recorder"),
        IntentTypeOption("TOGGLE_FLASHLIGHT", "Flashlight / Torch", Icons.Default.FlashlightOn, "TOGGLE", "Toggling flashlight"),
        IntentTypeOption("OPEN_BATTERY_SETTINGS", "Open Battery & Power Settings", Icons.Default.BatteryChargingFull, "", "Opening battery settings"),
        IntentTypeOption("OPEN_DISPLAY_SETTINGS", "Open Display Settings", Icons.Default.DisplaySettings, "", "Opening display settings"),
        IntentTypeOption("OPEN_APP", "Open Specific App (YouTube, Spotify, etc.)", Icons.Default.Apps, "youtube", "Opening app"),
        IntentTypeOption("TOGGLE_WIFI", "Toggle Wi-Fi / Wi-Fi Panel", Icons.Default.Wifi, "ON", "Opening Wi-Fi controls"),
        IntentTypeOption("OPEN_WIFI_SETTINGS", "Open Wi-Fi Settings", Icons.Default.Wifi, "", "Opening Wi-Fi settings"),
        IntentTypeOption("OPEN_BLUETOOTH_SETTINGS", "Open Bluetooth Settings", Icons.Default.Bluetooth, "", "Opening Bluetooth settings"),
        IntentTypeOption("OPEN_HOTSPOT_SETTINGS", "Open Hotspot & Tethering", Icons.Default.WifiTethering, "", "Opening Hotspot settings"),
        IntentTypeOption("CUSTOM_INTENT", "Custom Android Intent Action", Icons.Default.Settings, "android.settings.SETTINGS", "Launching settings")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = "Map Voice Phrase to Action",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "When you speak this phrase, Oganesson executes the action immediately on your phone with zero AI round-trip.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Voice Phrase Field
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = { Text("When I say (Voice Phrase)") },
                    placeholder = { Text("e.g. click picture, snap photo, wifi on") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AtomicCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = AtomicCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shortcut_phrase_input")
                )

                // Action Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = actionExpanded,
                    onExpandedChange = { actionExpanded = !actionExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = intentOptions.find { it.type == actionType }?.label ?: actionType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Action") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AtomicCyan,
                            unfocusedBorderColor = DarkBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = actionExpanded,
                        onDismissRequest = { actionExpanded = false }
                    ) {
                        intentOptions.forEach { option ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        tint = AtomicCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = { Text(option.label, fontSize = 13.sp) },
                                onClick = {
                                    actionType = option.type
                                    actionExpanded = false
                                    target = option.defaultTarget
                                    spokenReply = option.defaultReply
                                }
                            )
                        }
                    }
                }

                // Conditional Target parameter (e.g. macro steps, app name, or state)
                if (actionType == "MULTI_ACTION_MACRO") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Macro Commands (comma-separated sequence):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AtomicCyan
                        )
                        OutlinedTextField(
                            value = target,
                            onValueChange = { target = it },
                            label = { Text("Multi-Step Actions") },
                            placeholder = { Text("e.g. Open Spotify, play playlist Coffee Beats, set volume 50%") },
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AtomicCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedLabelColor = AtomicCyan
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("macro_steps_input")
                        )
                        Text(
                            text = "Tap to append actions:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val chips = listOf(
                                "Open Spotify",
                                "play playlist Coffee Beats",
                                "set volume 50%",
                                "set volume 80%",
                                "turn on flashlight",
                                "open camera and take picture",
                                "open Google Maps",
                                "open YouTube"
                            )
                            chips.forEach { chip ->
                                Surface(
                                    color = DarkSurfaceVariant,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, AtomicCyan.copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable {
                                        target = if (target.isBlank()) chip else "$target, $chip"
                                    }
                                ) {
                                    Text(
                                        text = "+ $chip",
                                        fontSize = 10.sp,
                                        color = AtomicCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (actionType == "OPEN_APP") {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it },
                        label = { Text("App Name (e.g. youtube, spotify, camera)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AtomicCyan,
                            unfocusedBorderColor = DarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (actionType == "TOGGLE_FLASHLIGHT") {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it },
                        label = { Text("Target State: ON, OFF, or TOGGLE") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AtomicCyan,
                            unfocusedBorderColor = DarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Spoken Confirmation
                OutlinedTextField(
                    value = spokenReply,
                    onValueChange = { spokenReply = it },
                    label = { Text("Assistant Spoken Reply") },
                    placeholder = { Text("e.g. Opening camera") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AtomicCyan,
                        unfocusedBorderColor = DarkBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shortcut_reply_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (phrase.isNotBlank()) {
                        onConfirm(phrase, actionType, target, spokenReply)
                    }
                },
                enabled = phrase.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AtomicCyan,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("confirm_add_shortcut_button")
            ) {
                Text("Save Shortcut", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

fun getIntentIcon(actionType: String): ImageVector {
    return when (actionType) {
        "MULTI_ACTION_MACRO" -> Icons.Default.PlayArrow
        "PLAY_MUSIC" -> Icons.Default.PlayArrow
        "TAKE_PHOTO" -> Icons.Default.CameraAlt
        "RECORD_VIDEO" -> Icons.Default.Videocam
        "RECORD_AUDIO" -> Icons.Default.Mic
        "TOGGLE_WIFI", "OPEN_WIFI_SETTINGS" -> Icons.Default.Wifi
        "OPEN_APP" -> Icons.Default.Apps
        "OPEN_BLUETOOTH_SETTINGS" -> Icons.Default.Bluetooth
        "OPEN_HOTSPOT_SETTINGS" -> Icons.Default.WifiTethering
        "TOGGLE_FLASHLIGHT" -> Icons.Default.FlashlightOn
        "OPEN_BATTERY_SETTINGS" -> Icons.Default.BatteryChargingFull
        "OPEN_DISPLAY_SETTINGS" -> Icons.Default.DisplaySettings
        else -> Icons.Default.ElectricBolt
    }
}

fun formatActionType(actionType: String): String {
    return when (actionType) {
        "MULTI_ACTION_MACRO" -> "Action Macro"
        "PLAY_MUSIC" -> "Play Music"
        "TAKE_PHOTO" -> "Click Picture"
        "RECORD_VIDEO" -> "Video Recording"
        "RECORD_AUDIO" -> "Voice Recording"
        "TOGGLE_WIFI" -> "Toggle Wi-Fi"
        "OPEN_WIFI_SETTINGS" -> "Wi-Fi Settings"
        "OPEN_APP" -> "Open App"
        "OPEN_BLUETOOTH_SETTINGS" -> "Bluetooth Settings"
        "OPEN_HOTSPOT_SETTINGS" -> "Hotspot Settings"
        "TOGGLE_FLASHLIGHT" -> "Flashlight"
        "OPEN_BATTERY_SETTINGS" -> "Battery Settings"
        "OPEN_DISPLAY_SETTINGS" -> "Display Settings"
        else -> actionType.replace("_", " ")
    }
}
