package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.viewmodel.AssistantStatus
import com.example.viewmodel.MahiUiState

@Composable
fun MahiScreen(
    state: MahiUiState,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onInterrupt: () -> Unit,
    onToggleMute: () -> Unit,
    onSendQuickPrompt: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quickStarters = listOf(
        "Why are you so sassy? 😉",
        "Open YouTube for me 🎬",
        "Give me your best playful roast 🔥",
        "Open Google 🌐",
        "What are you thinking right now? ✨"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_mic")
    val micPulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CyberBlack,
                        Color(0xFF0D081B),
                        Color(0xFF080512)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- TOP BAR ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Branding Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(NeonPink, NeonPurple)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "M",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                    }

                    Column {
                        Text(
                            text = "Mahi AI",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Voice-to-Voice Companion",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                }

                // Mood / Status Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CyberSurfaceVariant.copy(alpha = 0.8f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (state.status == AssistantStatus.DISCONNECTED) TextTertiary else NeonPink.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (state.status) {
                                        AssistantStatus.DISCONNECTED -> TextTertiary
                                        AssistantStatus.CONNECTING -> ElectricCyan
                                        AssistantStatus.LISTENING -> ElectricCyan
                                        AssistantStatus.SPEAKING -> NeonPink
                                        AssistantStatus.ERROR -> Color(0xFFFF495C)
                                    }
                                )
                        )
                        Text(
                            text = when (state.status) {
                                AssistantStatus.DISCONNECTED -> "OFFLINE"
                                AssistantStatus.CONNECTING -> "CONNECTING"
                                AssistantStatus.LISTENING -> "LISTENING"
                                AssistantStatus.SPEAKING -> "SPEAKING"
                                AssistantStatus.ERROR -> "ERROR"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Persona Attitude Tag
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CyberSurface.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.3f)),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = state.personaMood,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonPink,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // --- CENTER VISUALIZER ORB ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                MahiVisualizerCanvas(
                    status = state.status,
                    audioLevel = state.audioLevel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )

                // Subtitle/Thoughts Overlay
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = CyberSurface.copy(alpha = 0.85f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when (state.status) {
                                AssistantStatus.SPEAKING -> NeonPink.copy(alpha = 0.5f)
                                AssistantStatus.LISTENING -> ElectricCyan.copy(alpha = 0.4f)
                                else -> Color.White.copy(alpha = 0.1f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = state.subtitle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // --- TOOL EXECUTION BADGE (when toolCall is triggered) ---
            AnimatedVisibility(
                visible = state.lastExecutedTool != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                state.lastExecutedTool?.let { toolInfo ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ElectricCyan.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Tool Executed",
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = toolInfo,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // --- ERROR BANNER ---
            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                state.errorMessage?.let { error ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF3B0B14),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF495C)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = error,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDismissError,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // --- QUICK FUN PROMPTS ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "TEASE MAHI:",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(quickStarters) { prompt ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CyberSurfaceVariant.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSendQuickPrompt(prompt) }
                                .testTag("prompt_chip")
                        ) {
                            Text(
                                text = prompt,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- BOTTOM CONTROLS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute / Unmute Button
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(CyberSurface)
                        .border(1.dp, if (state.isMuted) Color(0xFFFF495C) else Color.White.copy(alpha = 0.15f), CircleShape)
                        .testTag("mute_button")
                ) {
                    Icon(
                        imageVector = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Toggle Mute",
                        tint = if (state.isMuted) Color(0xFFFF495C) else TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // CENTRAL GLOWING MIC / POWER TOGGLE
                val isRunning = state.status != AssistantStatus.DISCONNECTED
                val mainColor by animateColorAsState(
                    targetValue = if (isRunning) NeonPink else ElectricCyan,
                    label = "mainColor"
                )

                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .scale(if (state.status == AssistantStatus.LISTENING) micPulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    mainColor,
                                    mainColor.copy(alpha = 0.75f),
                                    Color(0xFF1B0B2E)
                                )
                            )
                        )
                        .border(
                            2.dp,
                            if (isRunning) Color.White.copy(alpha = 0.8f) else ElectricCyan.copy(alpha = 0.6f),
                            CircleShape
                        )
                        .clickable {
                            if (isRunning) onStopSession() else onStartSession()
                        }
                        .testTag("central_mic_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.PowerSettingsNew else Icons.Default.Mic,
                        contentDescription = if (isRunning) "Disconnect Mahi" else "Wake Mahi",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Interruption Button ("Hush / Stop" when speaking)
                val canInterrupt = state.status == AssistantStatus.SPEAKING
                IconButton(
                    onClick = onInterrupt,
                    enabled = canInterrupt,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (canInterrupt) NeonPurple.copy(alpha = 0.3f) else CyberSurface)
                        .border(
                            1.dp,
                            if (canInterrupt) NeonPurple else Color.White.copy(alpha = 0.15f),
                            CircleShape
                        )
                        .testTag("interrupt_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Hush Mahi",
                        tint = if (canInterrupt) NeonPink else TextTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
