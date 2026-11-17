package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.MeroState
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.ArcCyanBright
import com.example.ui.theme.FilmyGold
import com.example.ui.theme.FilmyGoldBright
import com.example.ui.theme.IceWhite
import com.example.ui.theme.MistSlate
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PulseCrimson
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArcCoreVisualizer(
    meroState: MeroState,
    amplitude: Float,
    spectrum: List<Float>,
    recordingDurationMs: Long,
    coreSize: Dp = 220.dp,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arc_core_spin")
    val slowRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "slow_rotation"
    )
    val fastRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fast_rotation"
    )
    val breathing by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    val smoothAmp by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 450f, dampingRatio = 0.72f),
        label = "smooth_amp"
    )

    val primaryGlow by animateColorAsState(
        targetValue = when (meroState) {
            MeroState.STANDBY -> ArcCyan
            MeroState.LISTENING -> PulseCrimson
            MeroState.THINKING -> ArcCyanBright
            MeroState.SPEAKING -> FilmyGold
            MeroState.ERROR -> PulseCrimson
        },
        animationSpec = tween(300),
        label = "primary_glow"
    )

    val secondaryGlow by animateColorAsState(
        targetValue = when (meroState) {
            MeroState.STANDBY -> FilmyGold.copy(alpha = 0.65f)
            MeroState.LISTENING -> ArcCyan
            MeroState.THINKING -> FilmyGoldBright
            MeroState.SPEAKING -> ArcCyanBright
            MeroState.ERROR -> FilmyGold
        },
        animationSpec = tween(300),
        label = "secondary_glow"
    )

    val tapLabel = when (meroState) {
        MeroState.STANDBY -> "Tap Mero core to speak"
        MeroState.LISTENING -> "Listening. Tap to send your voice message"
        MeroState.THINKING -> "Mero is thinking. Tap to cancel"
        MeroState.SPEAKING -> "Mero is speaking. Tap to interrupt and listen"
        MeroState.ERROR -> "Attention needed. Tap to retry speaking"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(coreSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f
                val pulseFactor = if (meroState == MeroState.STANDBY) breathing else 1f + smoothAmp * 0.14f

                // Ambient plasma halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = 0.24f + smoothAmp * 0.22f),
                            secondaryGlow.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxRadius * pulseFactor
                    ),
                    radius = maxRadius * pulseFactor,
                    center = center
                )

                // Outer segmented tactical HUD ring
                val outerRadius = maxRadius * 0.88f
                rotate(
                    degrees = if (meroState == MeroState.THINKING) slowRotation * 3f else slowRotation,
                    pivot = center
                ) {
                    val arcRectSize = Size(outerRadius * 2f, outerRadius * 2f)
                    val arcTopLeft = Offset(center.x - outerRadius, center.y - outerRadius)
                    for (i in 0 until 6) {
                        drawArc(
                            color = primaryGlow.copy(alpha = if (i % 2 == 0) 0.55f else 0.22f),
                            startAngle = i * 60f + 8f,
                            sweepAngle = 38f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcRectSize,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Counter-rotating inner orbital ring
                val innerOrbitalRadius = maxRadius * 0.72f
                rotate(
                    degrees = if (meroState == MeroState.THINKING) fastRotation * 2.5f else fastRotation,
                    pivot = center
                ) {
                    val arcRectSize = Size(innerOrbitalRadius * 2f, innerOrbitalRadius * 2f)
                    val arcTopLeft = Offset(center.x - innerOrbitalRadius, center.y - innerOrbitalRadius)
                    for (i in 0 until 4) {
                        drawArc(
                            color = secondaryGlow.copy(alpha = 0.7f),
                            startAngle = i * 90f + 12f,
                            sweepAngle = 52f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcRectSize,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // 28-band radial spectrum visualizer
                val barCount = spectrum.size.coerceAtLeast(1)
                val baseBarRadius = maxRadius * 0.56f
                val maxBarHeight = maxRadius * 0.24f
                for (i in 0 until barCount) {
                    val angleRad = (2.0 * PI * i / barCount) - PI / 2.0
                    val level = when (meroState) {
                        MeroState.STANDBY -> 0.14f + 0.08f * sin(slowRotation * 0.05f + i * 0.5f)
                        MeroState.THINKING -> 0.22f + 0.35f * ((sin(fastRotation * 0.08f + i * 0.6f) + 1f) / 2f)
                        else -> spectrum[i]
                    }.coerceIn(0.08f, 1f)

                    val startX = center.x + cos(angleRad).toFloat() * baseBarRadius
                    val startY = center.y + sin(angleRad).toFloat() * baseBarRadius
                    val endRadius = baseBarRadius + maxBarHeight * (0.2f + level * 0.8f)
                    val endX = center.x + cos(angleRad).toFloat() * endRadius
                    val endY = center.y + sin(angleRad).toFloat() * endRadius

                    drawLine(
                        color = if (i % 2 == 0) primaryGlow.copy(alpha = 0.85f) else secondaryGlow.copy(alpha = 0.8f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 2.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Interactive Central Core Orb
            val buttonSize = coreSize * 0.44f
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                primaryGlow.copy(alpha = 0.35f),
                                ObsidianElevated,
                                ObsidianVoid
                            )
                        )
                    )
                    .border(
                        width = 1.8.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(primaryGlow, secondaryGlow)
                        ),
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true, color = primaryGlow),
                        role = Role.Button,
                        onClick = onTap
                    )
                    .semantics { contentDescription = tapLabel }
                    .testTag("arc_core_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (meroState) {
                        MeroState.STANDBY -> Icons.Rounded.Mic
                        MeroState.LISTENING -> Icons.Rounded.Stop
                        MeroState.THINKING -> Icons.Rounded.AutoAwesome
                        MeroState.SPEAKING -> Icons.Rounded.GraphicEq
                        MeroState.ERROR -> Icons.Rounded.WarningAmber
                    },
                    contentDescription = null,
                    tint = IceWhite,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val caption = when (meroState) {
            MeroState.STANDBY -> "TAP CORE TO SPEAK"
            MeroState.LISTENING -> {
                val secs = recordingDurationMs / 1000
                val tenths = (recordingDurationMs % 1000) / 100
                "LISTENING • ${secs}.${tenths}s • TAP TO SEND"
            }
            MeroState.THINKING -> "NEURAL SYNTHESIS IN PROGRESS…"
            MeroState.SPEAKING -> "MERO SPEAKING • TAP TO INTERRUPT"
            MeroState.ERROR -> "CORE ALERT • TAP TO RETRY"
        }

        Text(
            text = caption,
            style = MaterialTheme.typography.labelLarge,
            color = if (meroState == MeroState.STANDBY) MistSlate else primaryGlow,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
