package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.viewmodel.AssistantStatus
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MahiVisualizerCanvas(
    status: AssistantStatus,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer_anim")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
            val baseRadius = minOf(canvasWidth, canvasHeight) * 0.28f

            // Adjust reactivity based on current audioLevel
            val effectiveLevel = when (status) {
                AssistantStatus.DISCONNECTED -> 0.05f
                AssistantStatus.CONNECTING -> 0.35f
                AssistantStatus.LISTENING -> (audioLevel * 1.5f).coerceIn(0.12f, 1.2f)
                AssistantStatus.SPEAKING -> (audioLevel * 1.8f).coerceIn(0.25f, 1.5f)
                AssistantStatus.ERROR -> 0.1f
            }

            val dynamicRadius = baseRadius * pulseScale * (1f + effectiveLevel * 0.35f)

            // Primary Glow Gradient
            val glowColor1 = when (status) {
                AssistantStatus.SPEAKING -> NeonPink
                AssistantStatus.LISTENING -> ElectricCyan
                AssistantStatus.CONNECTING -> NeonPurple
                AssistantStatus.DISCONNECTED -> Color(0xFF2A1C40)
                AssistantStatus.ERROR -> Color(0xFFFF495C)
            }

            val glowColor2 = when (status) {
                AssistantStatus.SPEAKING -> NeonPurple
                AssistantStatus.LISTENING -> Color(0xFF0077B6)
                AssistantStatus.CONNECTING -> ElectricCyan
                AssistantStatus.DISCONNECTED -> Color(0xFF140D26)
                AssistantStatus.ERROR -> Color(0xFF7A0E1A)
            }

            // 1. Draw outer ambient atmospheric glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor1.copy(alpha = if (status == AssistantStatus.DISCONNECTED) 0.15f else 0.45f),
                        glowColor2.copy(alpha = if (status == AssistantStatus.DISCONNECTED) 0.08f else 0.25f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = dynamicRadius * 1.9f
                ),
                radius = dynamicRadius * 1.9f,
                center = center
            )

            // 2. Draw outer reactive concentric soundwave ripple rings
            val ringCount = if (status == AssistantStatus.DISCONNECTED) 2 else 4
            for (i in 1..ringCount) {
                val ringOffset = (i * 22f) * (1f + effectiveLevel * 0.8f)
                val ringAlpha = (0.5f / i) * if (status == AssistantStatus.DISCONNECTED) 0.4f else 0.9f
                drawCircle(
                    color = glowColor1.copy(alpha = ringAlpha),
                    radius = dynamicRadius + ringOffset,
                    center = center,
                    style = Stroke(
                        width = (2.5f - i * 0.4f).coerceAtLeast(1f),
                        cap = StrokeCap.Round
                    )
                )
            }

            // 3. Draw rotating particle arc satellites (when active or connecting)
            if (status != AssistantStatus.DISCONNECTED) {
                val numDots = 12
                val orbitalRadius = dynamicRadius + 45f + (effectiveLevel * 30f)
                val rotRad = Math.toRadians(rotationAngle.toDouble())

                for (idx in 0 until numDots) {
                    val angle = rotRad + (idx * (2 * Math.PI / numDots))
                    val dotX = center.x + (orbitalRadius * cos(angle)).toFloat()
                    val dotY = center.y + (orbitalRadius * sin(angle)).toFloat()
                    val dotAlpha = (0.3f + 0.7f * ((idx % 3) / 2f)).coerceIn(0.2f, 1f)
                    val dotSize = (3.5f + (effectiveLevel * 3.5f))

                    drawCircle(
                        color = if (idx % 2 == 0) glowColor1.copy(alpha = dotAlpha) else glowColor2.copy(alpha = dotAlpha),
                        radius = dotSize,
                        center = Offset(dotX, dotY)
                    )
                }
            }

            // 4. Draw Core Glowing Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor1.copy(alpha = 0.95f),
                        glowColor2.copy(alpha = 0.85f),
                        Color(0xFF0C071C)
                    ),
                    center = Offset(center.x - dynamicRadius * 0.2f, center.y - dynamicRadius * 0.2f),
                    radius = dynamicRadius
                ),
                radius = dynamicRadius,
                center = center
            )

            // Inner Core Rim
            drawCircle(
                color = Color.White.copy(alpha = if (status == AssistantStatus.SPEAKING) 0.6f else 0.3f),
                radius = dynamicRadius,
                center = center,
                style = Stroke(width = 2f)
            )

            // 5. Draw Dynamic Center Waveform (Sine wave that reacts to real speech)
            if (status == AssistantStatus.SPEAKING || status == AssistantStatus.LISTENING) {
                val wavePath = Path()
                val wavePoints = 40
                val waveWidth = dynamicRadius * 1.5f
                val startX = center.x - (waveWidth / 2f)
                val waveHeight = (dynamicRadius * 0.5f) * (0.3f + effectiveLevel * 0.9f)

                wavePath.moveTo(startX, center.y)

                for (p in 0..wavePoints) {
                    val progress = p.toFloat() / wavePoints
                    val x = startX + progress * waveWidth
                    // Harmonic sine wave formulation
                    val y = center.y + sin(progress * 4 * Math.PI + wavePhase).toFloat() * waveHeight *
                            sin(progress * Math.PI).toFloat() // envelope taper at ends

                    if (p == 0) {
                        wavePath.moveTo(x, y)
                    } else {
                        wavePath.lineTo(x, y)
                    }
                }

                drawPath(
                    path = wavePath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.2f),
                            Color.White,
                            NeonPink,
                            ElectricCyan.copy(alpha = 0.2f)
                        ),
                        startX = startX,
                        endX = startX + waveWidth
                    ),
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }
        }
    }
}
