package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.VoiceState
import com.example.ui.theme.AtomicCyan
import com.example.ui.theme.AtomicCyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.StateError
import com.example.ui.theme.StateListening
import com.example.ui.theme.StateSpeaking
import com.example.ui.theme.StateThinking

@Composable
fun MicPulseButton(
    voiceState: VoiceState,
    rmsLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )

    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val activeColor by animateColorAsState(
        targetValue = when (voiceState) {
            VoiceState.LISTENING -> StateListening
            VoiceState.THINKING, VoiceState.PROCESSING -> StateThinking
            VoiceState.SPEAKING -> StateSpeaking
            VoiceState.ERROR -> StateError
            VoiceState.IDLE -> AtomicCyan
        },
        label = "buttonColor"
    )

    // Dynamic scale responding to live audio RMS
    val dynamicRmsScale = 1f + (rmsLevel * 0.35f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(140.dp)
    ) {
        // Outer pulsing ring 2 when listening or speaking
        if (voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(pulseScale2)
                    .background(
                        color = activeColor.copy(alpha = pulseAlpha * 0.4f),
                        shape = CircleShape
                    )
            )
        }

        // Outer pulsing ring 1
        if (voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .scale(pulseScale1)
                    .background(
                        color = activeColor.copy(alpha = pulseAlpha * 0.6f),
                        shape = CircleShape
                    )
            )
        }

        // Inner glowing ambient halo
        Box(
            modifier = Modifier
                .size(86.dp)
                .scale(if (voiceState == VoiceState.LISTENING) dynamicRmsScale else 1f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            activeColor.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Main Push-to-Talk interactive button core
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .testTag("mic_push_button")
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = when (voiceState) {
                            VoiceState.LISTENING -> listOf(AtomicCyan, ElectricBlue)
                            VoiceState.THINKING, VoiceState.PROCESSING -> listOf(StateThinking, Color(0xFFFF8F00))
                            VoiceState.SPEAKING -> listOf(StateSpeaking, ElectricBlue)
                            VoiceState.ERROR -> listOf(StateError, Color(0xFFC62828))
                            VoiceState.IDLE -> listOf(AtomicCyan.copy(alpha = 0.9f), ElectricBlue)
                        }
                    )
                )
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.3f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = Color.White),
                    onClick = onClick
                )
        ) {
            when (voiceState) {
                VoiceState.THINKING, VoiceState.PROCESSING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }
                VoiceState.SPEAKING -> {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop speaking",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                VoiceState.LISTENING -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Listening microphone active",
                        tint = Color.Black,
                        modifier = Modifier
                            .size(36.dp)
                            .scale(if (rmsLevel > 0.2f) 1.15f else 1f)
                    )
                }
                VoiceState.ERROR -> {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "Microphone error",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                VoiceState.IDLE -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Tap to speak with Oganesson",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}
