package com.soundstage.mixer.ui.components.v2

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sustain case, matching the JSX (MixerPreview.jsx:425) exactly.
 *
 * The JSX draws no pedal: just a small 8 px dot whose colour changes
 * (#5A6379 idle -> #8FB2E8 active, with an 8 px glow when active), the word
 * SUSTAIN, a case tint that fades in, and the shared relief
 * (inset top highlight + drop shadow). The animated dot is the only moving part,
 * and it follows the real sustain state, including an external MIDI pedal.
 */
@Composable
fun SustainPedalButton(
    isActive: Boolean,
    onToggle: () -> Unit,
    panelTop: Color = Color(0xFF2B3348),
    panelBottom: Color = Color(0xFF1D2433),
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFF8FB2E8)
    val idleDot = Color(0xFF5A6379)

    val dotColor by animateColorAsState(
        targetValue = if (isActive) accent else idleDot,
        animationSpec = tween(250),
        label = "pedalDot"
    )
    // JSX: boxShadow 0 0 8px on the dot only while active.
    val glowRadius by animateFloatAsState(
        targetValue = if (isActive) 8f else 0f,
        animationSpec = tween(250),
        label = "pedalGlow"
    )
    // Dot breathes slightly while the pedal is down, so the state reads at a glance.
    val dotScale by animateFloatAsState(
        targetValue = if (isActive) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 480f),
        label = "pedalDotScale"
    )
    val caseTint by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(220),
        label = "pedalTint"
    )

    Box(
        modifier = modifier
            .width(84.dp)
            .height(44.dp) // JSX: width 84, height 44
            .shadow(5.dp, RoundedCornerShape(14.dp),
                spotColor = Color(0x59000000), ambientColor = Color.Black)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(panelTop, Color(0xFF5B79AE), caseTint * 0.55f),
                        lerp(panelBottom, Color(0xFF33507E), caseTint * 0.55f)
                    )
                )
            )
            .drawWithContent {
                drawContent()
                // JSX inset 0 1px 0 rgba(255,255,255,.09): the top relief edge.
                drawLine(
                    color = Color.White.copy(alpha = 0.09f),
                    start = Offset(size.width * 0.08f, 0.5f),
                    end = Offset(size.width * 0.92f, 0.5f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // The JSX dot pair: an 8 x 8 span, 8 px apart from the label.
            Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                if (glowRadius > 0f) {
                    Box(
                        Modifier.size(12.dp * dotScale).clip(CircleShape)
                            .background(accent.copy(alpha = 0.32f * (glowRadius / 8f)))
                    )
                }
                Box(Modifier.size(8.dp * dotScale).clip(CircleShape).background(dotColor))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "SUSTAIN",
                color = if (isActive) Color(0xFFF1F4FA) else Color(0xFF9AA5BA),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                softWrap = false,
                maxLines = 1
            )
        }
    }
}
/** Linear colour mix, used for the animated active tint of the sustain case. */
private fun lerp(a: Color, b: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue = a.blue + (b.blue - a.blue) * f,
        alpha = a.alpha + (b.alpha - a.alpha) * f
    )
}
