package com.soundstage.mixer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Studio palette of refined, organic, non-neon harmonic tones
val StudioBubblePalette = listOf(
    Color(0xFF5B92E5), // Soft Studio Blue
    Color(0xFF818CF8), // Pastel Periwinkle
    Color(0xFF9F8FEF), // Delicate Soft Lavender
    Color(0xFFD47A9E), // Muted Dusky Rose
    Color(0xFFD98A6C), // Warm Amber Dusk Glow
    Color(0xFF38B2AC), // Soft Mineral Teal
    Color(0xFF4A90E2)  // Atmospheric Sky Azure
)

/**
 * Shared Dynamic Ambient Background with Floating Luminous Bubbles.
 * Exactly identical across Mixer, Setlists, and DrumPad pages.
 */
@Composable
fun AmbientStudioBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val studioBg = MaterialTheme.colorScheme.background

    val infiniteTransition = rememberInfiniteTransition(label = "ambient_studio_glow_orb")
    val animPhaseX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 52000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glow_x"
    )
    val animPhaseY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 68000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glow_y"
    )
    val animPhaseRadius by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_radius_pulse"
    )
    val animColorPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = StudioBubblePalette.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 84000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glow_color_phase"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = studioBg)

            val cx = size.width * (0.50f + 0.35f * kotlin.math.sin(animPhaseX))
            val cy = size.height * (0.50f + 0.30f * kotlin.math.cos(animPhaseY))
            val baseRadius = (size.width * 0.35f).coerceIn(160.dp.toPx(), 320.dp.toPx())
            val currentOrbRadius = baseRadius * animPhaseRadius

            fun sampleBubbleColor(phaseOffset: Float): Color {
                val count = StudioBubblePalette.size
                val norm = ((animColorPhase + phaseOffset) % count + count) % count
                val idx1 = norm.toInt()
                val idx2 = (idx1 + 1) % count
                val frac = norm - idx1
                return androidx.compose.ui.graphics.lerp(StudioBubblePalette[idx1], StudioBubblePalette[idx2], frac)
            }

            val primaryOrbColor = sampleBubbleColor(0f)
            val satelliteOrbColor = sampleBubbleColor(2.35f)
            val tertiaryOrbColor = sampleBubbleColor(4.70f)

            // Layer 1: Primary Floating Ambient Glow Orb (smooth, subtle, organic luminous bubble)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryOrbColor.copy(alpha = 0.12f),
                        primaryOrbColor.copy(alpha = 0.05f),
                        primaryOrbColor.copy(alpha = 0.01f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = currentOrbRadius * 1.4f
                ),
                center = Offset(cx, cy),
                radius = currentOrbRadius * 1.4f
            )

            // Primary Gentle Soft Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryOrbColor.copy(alpha = 0.16f),
                        primaryOrbColor.copy(alpha = 0.07f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = currentOrbRadius * 0.65f
                ),
                center = Offset(cx, cy),
                radius = currentOrbRadius * 0.65f
            )

            // Layer 2: Secondary Floating Satellite Glow Orb drifting counter-phase
            val satX = size.width * (0.50f - 0.34f * kotlin.math.sin(animPhaseY * 0.85f))
            val satY = size.height * (0.50f - 0.28f * kotlin.math.cos(animPhaseX * 0.75f))
            val satRadius = baseRadius * 0.78f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        satelliteOrbColor.copy(alpha = 0.11f),
                        satelliteOrbColor.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(satX, satY),
                    radius = satRadius
                ),
                center = Offset(satX, satY),
                radius = satRadius
            )

            // Layer 3: Tertiary Delicate Floating Accent Orb drifting slowly
            val tertX = size.width * (0.50f + 0.32f * kotlin.math.cos(animPhaseX * 0.60f + 1.2f))
            val tertY = size.height * (0.50f - 0.30f * kotlin.math.sin(animPhaseY * 0.65f + 0.9f))
            val tertRadius = baseRadius * 0.58f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tertiaryOrbColor.copy(alpha = 0.09f),
                        tertiaryOrbColor.copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    center = Offset(tertX, tertY),
                    radius = tertRadius
                ),
                center = Offset(tertX, tertY),
                radius = tertRadius
            )
        }

        content()
    }
}
