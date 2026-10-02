package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import com.example.BuildConfig
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.LanguageOption
import com.example.data.local.PreferencesManager
import com.example.service.OganessonService
import com.example.ui.theme.AtomicCyan
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DeepViolet
import com.example.ui.theme.ElectricBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: OganessonViewModel,
    onBack: () -> Unit,
    onOpenCustomShortcuts: () -> Unit = {},
    onRequestPermissions: () -> Unit,
    onRequestBatteryOptimization: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentApiKey by viewModel.mistralApiKey.collectAsState()
    val currentSecondaryApiKey by viewModel.secondaryMistralApiKey.collectAsState()
    val currentModel by viewModel.mistralModel.collectAsState()
    val currentLanguage by viewModel.assistantLanguage.collectAsState()
    val currentCustomWakeWord by viewModel.customWakeWord.collectAsState()

    val continuousMode by viewModel.continuousMode.collectAsState()
    val persistenceMode by viewModel.persistenceMode.collectAsState()
    val backgroundWakeWord by viewModel.backgroundWakeWord.collectAsState()
    val persistentBackgroundListening by viewModel.persistentBackgroundListening.collectAsState()
    val ttsSpeed by viewModel.ttsSpeed.collectAsState()
    val ttsPitch by viewModel.ttsPitch.collectAsState()
    val ttsVoice by viewModel.ttsVoice.collectAsState()
    val preferOffline by viewModel.preferOffline.collectAsState()
    val availableVoices by viewModel.ttsManager.availableVoices.collectAsState()
    val cachedCommands by viewModel.cachedCommands.collectAsState()
    val customVoiceMappings by viewModel.customVoiceMappings.collectAsState()
    val connectionTestState by viewModel.connectionTestState.collectAsState()
    val secondaryConnectionTestState by viewModel.secondaryConnectionTestState.collectAsState()
    val currentPowerMode by viewModel.powerMode.collectAsState()
    val currentMaxTokens by viewModel.maxTokens.collectAsState()
    val batteryTemp by viewModel.thermalBatteryManager.batteryTemperatureCelsius.collectAsState()
    val batteryLevel by viewModel.thermalBatteryManager.batteryLevelPercent.collectAsState()
    val thermalLevelName by viewModel.thermalBatteryManager.thermalLevelName.collectAsState()
    val isThermalThrottled by viewModel.thermalBatteryManager.isThermalThrottled.collectAsState()

    var apiKeyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var secondaryApiKeyInput by remember(currentSecondaryApiKey) { mutableStateOf(currentSecondaryApiKey) }
    var customWakeWordInput by remember(currentCustomWakeWord) { mutableStateOf(currentCustomWakeWord) }
    var showPassword by remember { mutableStateOf(false) }
    var showSecondaryPassword by remember { mutableStateOf(false) }
    var languageDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to assistant",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Background Service & "Hi Oganesson" Wake Word + Overlay + Custom Wake Word
            val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }

            SettingsCard(
                title = "Wake Word & Floating Overlay UI",
                icon = Icons.Default.Hearing
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Background Wake Word + Overlay",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Listens for \"Hi Oganesson\" or your custom wake phrase while screen is ON, popping up the dynamic Siri/Bixby style glowing overlay on top of any active screen.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = backgroundWakeWord,
                        onCheckedChange = { isChecked ->
                            viewModel.updateBackgroundWakeWord(isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = AtomicCyan
                        ),
                        modifier = Modifier.testTag("wake_word_switch")
                    )
                }

                val hasRecordAudio = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasRecordAudio && backgroundWakeWord) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFFF5252).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Microphone Permission Required",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Background wake word detection requires microphone access to listen for \"Hi Oganesson\". Please grant microphone permission.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onRequestPermissions,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant Microphone Permission", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (!hasOverlayPermission && backgroundWakeWord) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFFFB703).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFB703).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB703),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Appear on Top Permission Needed",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB703)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "To pop up the glowing assistant overlay directly over other apps or the home screen, please enable \"Display over other apps\" for Oganesson.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB703)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant \"Appear on top\" Permission", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        OganessonService.triggerWake(context)
                    },
                    border = BorderStroke(1.dp, AtomicCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AtomicCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_floating_overlay_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Floating Overlay Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom Wake Word Option
                Text(
                    text = "Custom Wake-Up Command",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Define your own wake phrase (e.g. \"Jarvis\", \"Computer\", \"Hey Buddy\"). Predefined commands like \"Hi Oganesson\" will continue working too!",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customWakeWordInput,
                        onValueChange = { customWakeWordInput = it },
                        placeholder = { Text("e.g. Jarvis, Computer, Hey Buddy") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AtomicCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedLabelColor = AtomicCyan
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_wake_word_input")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { viewModel.updateCustomWakeWord(customWakeWordInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = AtomicCyan, contentColor = Color.Black),
                        modifier = Modifier.testTag("save_custom_wake_word_button")
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }

                if (currentCustomWakeWord.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Active custom wake word: \"$currentCustomWakeWord\"",
                        fontSize = 11.sp,
                        color = AtomicCyan,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Section 1.5: Languages & Regional Accents
            SettingsCard(
                title = "Language & Regional Accents",
                icon = Icons.Default.Language
            ) {
                Text(
                    text = "Select your preferred language or regional dialect. Both speech recognition and assistant voice replies will synchronize with this language.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                val selectedLangOption = PreferencesManager.SUPPORTED_LANGUAGES.find { it.code == currentLanguage }
                    ?: PreferencesManager.SUPPORTED_LANGUAGES.first()

                ExposedDropdownMenuBox(
                    expanded = languageDropdownExpanded,
                    onExpandedChange = { languageDropdownExpanded = !languageDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = "${selectedLangOption.displayName} (${selectedLangOption.nativeName})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Selected Assistant Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageDropdownExpanded) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = AtomicCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AtomicCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedLabelColor = AtomicCyan
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("language_selector_input")
                    )

                    ExposedDropdownMenu(
                        expanded = languageDropdownExpanded,
                        onDismissRequest = { languageDropdownExpanded = false }
                    ) {
                        PreferencesManager.SUPPORTED_LANGUAGES.forEach { lang ->
                            val isSelected = lang.code == currentLanguage
                            DropdownMenuItem(
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = AtomicCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(16.dp))
                                    }
                                },
                                text = {
                                    Column {
                                        Text(
                                            text = lang.displayName,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) AtomicCyan else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${lang.nativeName} • ${lang.region}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.updateAssistantLanguage(lang.code)
                                    languageDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Section 2: Command Shortcuts & Multi-Step Action Macros
            SettingsCard(
                title = "Command Shortcuts",
                icon = Icons.Default.ElectricBolt
            ) {
                Text(
                    text = "Map custom trigger phrases (e.g., 'Coffee Time') to complex multi-step action macros (e.g., 'Open Spotify, play playlist X, set volume 50') with 0ms zero-AI round-trip latency.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AtomicCyan.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCustomShortcuts() }
                        .testTag("open_custom_shortcuts_row")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
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
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(AtomicCyan.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ElectricBolt,
                                    contentDescription = null,
                                    tint = AtomicCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Command Shortcuts Menu",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${customVoiceMappings.size} active shortcut${if (customVoiceMappings.size != 1) "s" else ""} • Multi-Step Macros & Quick Actions",
                                    fontSize = 11.sp,
                                    color = AtomicCyan
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Open Command Shortcuts menu",
                            tint = AtomicCyan
                        )
                    }
                }
            }

            // Section: Power Mode & Thermal Control
            SettingsCard(
                title = "Power Mode & Thermal Control",
                icon = Icons.Default.Thermostat
            ) {
                Text(
                    text = "Control device temperature and background voice processing frequency to prevent heating and conserve battery.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time Thermal & Battery Status Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkBackground,
                    border = BorderStroke(1.dp, if (isThermalThrottled) Color(0xFFFFAB00) else DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        if (batteryTemp >= 40f) Color(0xFFFF5252)
                                        else if (batteryTemp >= 37f) Color(0xFFFFAB00)
                                        else Color(0xFF00E676),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Battery: ${batteryLevel}% • ${"%.1f".format(batteryTemp)}°C",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isThermalThrottled) Color(0xFFFFAB00).copy(alpha = 0.2f) else AtomicCyan.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = if (isThermalThrottled) "THERMAL THROTTLED" else "STATUS: $thermalLevelName",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isThermalThrottled) Color(0xFFFFAB00) else AtomicCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Power Mode Selector ('Balanced' vs 'Eco')
                Text(
                    text = "Voice Assistant Operating Profile",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                val isEco = currentPowerMode == PreferencesManager.POWER_MODE_ECO

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Balanced Mode Option
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (!isEco) AtomicCyan.copy(alpha = 0.15f) else DarkBackground,
                        border = BorderStroke(1.dp, if (!isEco) AtomicCyan else DarkBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.updatePowerMode(PreferencesManager.POWER_MODE_BALANCED) }
                            .testTag("power_mode_balanced_button")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = if (!isEco) AtomicCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Balanced",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (!isEco) AtomicCyan else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Standard 1.2s voice loop. Responsive for active interaction.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    // Eco Mode Option
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isEco) Color(0xFF00E676).copy(alpha = 0.15f) else DarkBackground,
                        border = BorderStroke(1.dp, if (isEco) Color(0xFF00E676) else DarkBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.updatePowerMode(PreferencesManager.POWER_MODE_ECO) }
                            .testTag("power_mode_eco_button")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.BatteryFull,
                                    contentDescription = null,
                                    tint = if (isEco) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Eco (Low Heat)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isEco) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Calm 4.5s–6.0s intervals. Drops CPU load and prevents heating.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Section 3: Mistral AI Configuration (Free Tier Models)
            SettingsCard(
                title = "Mistral AI Intelligence",
                icon = Icons.Default.Key
            ) {
                Text(
                    text = "Oganesson features a Dual Mistral AI API Fallback engine. If your primary API key hits rate limits (HTTP 429) or authentication errors, queries instantly fail over to your secondary key without dropping commands.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Dual API Key Active Status Card
                val primaryKey = currentApiKey.trim()
                val secondaryKey = currentSecondaryApiKey.trim()
                val fallbackKey = BuildConfig.MISTRAL_FALLBACK_API_KEY.trim()
                val isPrimaryActive = primaryKey.isNotBlank()
                val isSecondaryActive = secondaryKey.isNotBlank()
                val isBakedActive = fallbackKey.isNotBlank()
                val isDualFallbackActive = isPrimaryActive && isSecondaryActive

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkBackground,
                    border = BorderStroke(
                        1.dp,
                        when {
                            isDualFallbackActive -> Color(0xFF00E676).copy(alpha = 0.6f)
                            isPrimaryActive || isBakedActive -> AtomicCyan.copy(alpha = 0.5f)
                            isSecondaryActive -> ElectricBlue.copy(alpha = 0.5f)
                            else -> DarkBorder
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_api_key_status_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isDualFallbackActive) Icons.Default.Security else Icons.Default.Key,
                                    contentDescription = null,
                                    tint = if (isDualFallbackActive) Color(0xFF00E676) else if (isPrimaryActive) AtomicCyan else if (isBakedActive) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "FAILOVER ENGINE STATUS",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = when {
                                            isDualFallbackActive -> "Dual API Failover Active"
                                            isPrimaryActive && isBakedActive -> "Primary + Local Fallback Active"
                                            isPrimaryActive -> "Primary Key Active (Single Key)"
                                            isSecondaryActive -> "Secondary Key Active (Backup Only)"
                                            isBakedActive -> "Local Fallback Active (local.properties)"
                                            else -> "No API Key Configured"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            isDualFallbackActive -> Color(0xFF00E676)
                                            isPrimaryActive || isBakedActive -> AtomicCyan
                                            isSecondaryActive -> ElectricBlue
                                            else -> Color(0xFFFFAB00)
                                        }
                                    )
                                }
                            }

                            // Failover Badge Chip
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    isDualFallbackActive -> Color(0xFF00E676).copy(alpha = 0.15f)
                                    isPrimaryActive || isBakedActive -> AtomicCyan.copy(alpha = 0.15f)
                                    isSecondaryActive -> ElectricBlue.copy(alpha = 0.15f)
                                    else -> Color(0xFFFFAB00).copy(alpha = 0.15f)
                                }
                            ) {
                                Text(
                                    text = when {
                                        isDualFallbackActive -> "Dual Fallback ON"
                                        isPrimaryActive && isBakedActive -> "Failover Ready"
                                        isPrimaryActive -> "Primary Active"
                                        isSecondaryActive -> "Backup Active"
                                        isBakedActive -> "Local Fallback"
                                        else -> "Keys Missing"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isDualFallbackActive -> Color(0xFF00E676)
                                        isPrimaryActive || isBakedActive -> AtomicCyan
                                        isSecondaryActive -> ElectricBlue
                                        else -> Color(0xFFFFAB00)
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (isBakedActive) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Local properties fallback detected (auto-safety net active)",
                                    fontSize = 11.sp,
                                    color = ElectricBlue
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- 1. PRIMARY API KEY ---
                Text(
                    text = "1. Primary Mistral API Key",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = PreferencesManager.sanitizeApiKey(it) },
                    label = { Text("Primary Mistral API Key") },
                    placeholder = { Text("Enter primary Mistral API key") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showPassword) "Hide API Key" else "Show API Key",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AtomicCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = AtomicCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input")
                )

                if (connectionTestState.message != null || connectionTestState.isTesting) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (connectionTestState.isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = AtomicCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing Primary Key...", fontSize = 11.sp, color = AtomicCyan)
                        } else if (connectionTestState.isValid == true) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Primary Key Valid: ${connectionTestState.message}", fontSize = 11.sp, color = Color(0xFF00E676))
                        } else {
                            Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Error: ${connectionTestState.message}", fontSize = 11.sp, color = Color(0xFFFF5252))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.testMistralConnection(PreferencesManager.sanitizeApiKey(apiKeyInput))
                        },
                        enabled = !connectionTestState.isTesting && (apiKeyInput.isNotBlank() || isPrimaryActive),
                        border = BorderStroke(1.dp, if (!connectionTestState.isTesting && (apiKeyInput.isNotBlank() || isPrimaryActive)) AtomicCyan else DarkBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AtomicCyan,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("test_connection_button")
                    ) {
                        Text("Test Primary Key", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { viewModel.updateMistralApiKey(PreferencesManager.sanitizeApiKey(apiKeyInput)) },
                        colors = ButtonDefaults.buttonColors(containerColor = AtomicCyan, contentColor = Color.Black),
                        modifier = Modifier.testTag("save_api_key_button")
                    ) {
                        Text("Save Primary", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- 2. SECONDARY / BACKUP API KEY (DUAL FALLBACK) ---
                Text(
                    text = "2. Secondary / Backup Mistral API Key (Dual Fallback)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Automatic failover target if primary key encounters rate limits (429) or quota errors.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = secondaryApiKeyInput,
                    onValueChange = { secondaryApiKeyInput = PreferencesManager.sanitizeApiKey(it) },
                    label = { Text("Secondary / Backup Mistral API Key") },
                    placeholder = { Text("Enter secondary / backup Mistral API key") },
                    singleLine = true,
                    visualTransformation = if (showSecondaryPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showSecondaryPassword = !showSecondaryPassword }) {
                            Icon(
                                imageVector = if (showSecondaryPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showSecondaryPassword) "Hide API Key" else "Show API Key",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = ElectricBlue
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("secondary_api_key_input")
                )

                if (secondaryConnectionTestState.message != null || secondaryConnectionTestState.isTesting) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (secondaryConnectionTestState.isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = ElectricBlue)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing Backup Key...", fontSize = 11.sp, color = ElectricBlue)
                        } else if (secondaryConnectionTestState.isValid == true) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup Key Valid: ${secondaryConnectionTestState.message}", fontSize = 11.sp, color = Color(0xFF00E676))
                        } else {
                            Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Error: ${secondaryConnectionTestState.message}", fontSize = 11.sp, color = Color(0xFFFF5252))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.mistral.ai/api-keys/")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Get free keys on Mistral Console",
                            fontSize = 12.sp,
                            color = AtomicCyan,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = AtomicCyan,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.testSecondaryMistralConnection(PreferencesManager.sanitizeApiKey(secondaryApiKeyInput))
                            },
                            enabled = !secondaryConnectionTestState.isTesting && (secondaryApiKeyInput.isNotBlank() || isSecondaryActive),
                            border = BorderStroke(1.dp, if (!secondaryConnectionTestState.isTesting && (secondaryApiKeyInput.isNotBlank() || isSecondaryActive)) ElectricBlue else DarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ElectricBlue,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("test_secondary_connection_button")
                        ) {
                            Text("Test Backup Key", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { viewModel.updateSecondaryMistralApiKey(PreferencesManager.sanitizeApiKey(secondaryApiKeyInput)) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White),
                            modifier = Modifier.testTag("save_secondary_api_key_button")
                        ) {
                            Text("Save Backup", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }


                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Select Mistral AI Model (Free Tier)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Exact specifications for models available in the free developer tier:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PreferencesManager.AVAILABLE_MISTRAL_MODELS.forEach { modelOption ->
                        val isSelected = currentModel == modelOption.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) AtomicCyan.copy(alpha = 0.12f) else DarkBackground,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) AtomicCyan else DarkBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateMistralModel(modelOption.id) }
                                .testTag("model_card_${modelOption.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(
                                            color = if (isSelected) AtomicCyan else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .border(
                                            BorderStroke(
                                                1.5.dp,
                                                if (isSelected) AtomicCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color.Black, CircleShape)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = modelOption.displayName,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) AtomicCyan else MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            color = if (isSelected) AtomicCyan.copy(alpha = 0.2f) else DarkSurfaceVariant,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = modelOption.category,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) AtomicCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = modelOption.description,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp,
                                        softWrap = true
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "ID: ${modelOption.id} • Context: ${modelOption.contextWindow}",
                                        fontSize = 10.sp,
                                        color = AtomicCyan.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "AI Response Length & Maximum Token Limit",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Configure output token depth. Higher budgets allow Mistral AI to provide extensive, in-depth explanations, recipes, coding assistance, and comprehensive answers without premature truncation.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                val tokenPresets = listOf(
                    4096 to "4K",
                    8192 to "8K (Default)",
                    12288 to "12K",
                    16384 to "16K (Max)"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tokenPresets.forEach { (tokens, label) ->
                        val isSelected = currentMaxTokens == tokens
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) AtomicCyan.copy(alpha = 0.15f) else DarkBackground,
                            border = BorderStroke(1.dp, if (isSelected) AtomicCyan else DarkBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.updateMaxTokens(tokens) }
                                .testTag("token_limit_${tokens}_button")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isSelected) AtomicCyan else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when (tokens) {
                                        4096 -> "Crisp"
                                        8192 -> "Detailed"
                                        12288 -> "Extended"
                                        else -> "Maximum"
                                    },
                                    fontSize = 10.sp,
                                    color = if (isSelected) AtomicCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Section 4: Actual Voice Persona & Natural Speech Synthesis
            SettingsCard(
                title = "Actual Voice & Persona",
                icon = Icons.Default.RecordVoiceOver
            ) {
                Text(
                    text = "Select an authentic natural voice persona for Oganesson with tailored cadence, pitch, and inflection.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Persona preset chips
                Text(
                    text = "Voice Personas",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = ttsPitch in 0.98f..1.02f && ttsSpeed in 1.0f..1.1f,
                        onClick = { viewModel.applyVoicePreset("natural") },
                        label = { Text("Natural") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AtomicCyan.copy(alpha = 0.2f),
                            selectedLabelColor = AtomicCyan
                        ),
                        modifier = Modifier.testTag("preset_natural")
                    )
                    FilterChip(
                        selected = ttsPitch < 0.95f && ttsSpeed < 1.0f,
                        onClick = { viewModel.applyVoicePreset("warm") },
                        label = { Text("Warm") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AtomicCyan.copy(alpha = 0.2f),
                            selectedLabelColor = AtomicCyan
                        ),
                        modifier = Modifier.testTag("preset_warm")
                    )
                    FilterChip(
                        selected = ttsSpeed > 1.1f,
                        onClick = { viewModel.applyVoicePreset("brisk") },
                        label = { Text("Brisk") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AtomicCyan.copy(alpha = 0.2f),
                            selectedLabelColor = AtomicCyan
                        ),
                        modifier = Modifier.testTag("preset_brisk")
                    )
                    FilterChip(
                        selected = ttsPitch < 0.9f,
                        onClick = { viewModel.applyVoicePreset("deep") },
                        label = { Text("Deep") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AtomicCyan.copy(alpha = 0.2f),
                            selectedLabelColor = AtomicCyan
                        ),
                        modifier = Modifier.testTag("preset_deep")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Speech Rate Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Speech Rate", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "${String.format("%.2f", ttsSpeed)}x", fontSize = 13.sp, color = AtomicCyan)
                }
                Slider(
                    value = ttsSpeed,
                    onValueChange = { viewModel.setTtsSpeed(it) },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = AtomicCyan, activeTrackColor = AtomicCyan),
                    modifier = Modifier.testTag("tts_speed_slider")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Pitch Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Voice Pitch", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "${String.format("%.2f", ttsPitch)}x", fontSize = 13.sp, color = AtomicCyan)
                }
                Slider(
                    value = ttsPitch,
                    onValueChange = { viewModel.setTtsPitch(it) },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = AtomicCyan, activeTrackColor = AtomicCyan),
                    modifier = Modifier.testTag("tts_pitch_slider")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { viewModel.testTtsSpeech("Hello! I am Oganesson, your intelligent mobile assistant.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_voice_button"),
                    border = BorderStroke(1.dp, AtomicCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Test voice sample",
                        tint = AtomicCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Play Voice Sample", color = AtomicCyan)
                }
            }

            // Section 5: Continuous Mode & Recognition
            SettingsCard(title = "Conversation Behavior", icon = Icons.Default.RecordVoiceOver) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Continuous Conversation Mode",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Oganesson keeps listening automatically after completing each spoken response.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = continuousMode,
                        onCheckedChange = { viewModel.updateContinuousMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = AtomicCyan
                        ),
                        modifier = Modifier.testTag("continuous_mode_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Speech Persistence Mode (3s Follow-Up Window)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Speech Persistence Mode",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = AtomicCyan.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "3s Window",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AtomicCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Keeps the microphone active for 3 seconds after an action completes, allowing you to give follow-up commands without re-triggering the wake word.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = persistenceMode,
                        onCheckedChange = { viewModel.updatePersistenceMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = AtomicCyan
                        ),
                        modifier = Modifier.testTag("persistence_mode_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Persistent Background Listening (Always-On Mic)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Persistent Background Mic",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = AtomicCyan.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "Always-On",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AtomicCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Keep microphone continuously active in the background, listening for wake words even when screen is off or another app is open.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = persistentBackgroundListening,
                        onCheckedChange = { viewModel.updatePersistentBackgroundListening(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = AtomicCyan
                        ),
                        modifier = Modifier.testTag("persistent_background_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Prefer Offline Speech-to-Text",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Uses on-device recognition when available for faster transcription and zero network latency.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferOffline,
                        onCheckedChange = { viewModel.updatePreferOffline(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = AtomicCyan
                        ),
                        modifier = Modifier.testTag("prefer_offline_switch")
                    )
                }
            }

            // Section 6: Permissions Status
            SettingsCard(title = "System Permissions", icon = Icons.Default.Security) {
                val hasRecordAudio = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else true

                val isBatteryExempt = viewModel.isIgnoringBatteryOptimizations()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PermissionStatusRow(
                        name = "Microphone Access",
                        isGranted = hasRecordAudio
                    )
                    PermissionStatusRow(
                        name = "Background Notifications",
                        isGranted = hasNotification
                    )
                    PermissionStatusRow(
                        name = "Appear on Top (Floating Overlay)",
                        isGranted = hasOverlayPermission
                    )
                    PermissionStatusRow(
                        name = "Battery Optimization Exemption",
                        isGranted = isBatteryExempt
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(containerColor = AtomicCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("request_permissions_button")
                ) {
                    Text(
                        text = "Check & Request Permissions",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isBatteryExempt) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRequestBatteryOptimization,
                        border = BorderStroke(1.dp, Color(0xFFFFB703)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_battery_optimization_button")
                    ) {
                        Text(
                            text = "Exempt from Battery Optimization (Always-On Mic)",
                            color = Color(0xFFFFB703),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Section 7: Room Database Cache
            SettingsCard(title = "Room Local Database Cache", icon = Icons.Default.OfflineBolt) {
                Text(
                    text = "Frequently executed commands and actions are cached in the local Room database to execute with zero AI round-trip latency.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${cachedCommands.size} cached command pattern(s)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AtomicCyan
                )

                if (cachedCommands.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    cachedCommands.take(3).forEach { cmd ->
                        Text(
                            text = "• \"${cmd.normalizedQuery}\" (${cmd.usageCount} uses)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AtomicCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
fun PermissionStatusRow(name: String, isGranted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF00E676) else Color(0xFFFF5252),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isGranted) "Granted" else "Missing",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isGranted) Color(0xFF00E676) else Color(0xFFFF5252)
            )
        }
    }
}
