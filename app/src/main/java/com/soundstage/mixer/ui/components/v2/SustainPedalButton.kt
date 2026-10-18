package com.soundstage.mixer.ui.components.v2

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

@Composable
fun SustainPedalButton(
    isActive: Boolean,
    onToggle: () -> Unit,
    panelTop: Color = Color(0xFF2B3348),
    panelBottom: Color = Color(0xFF1D2433),
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFF8FB2E8)
    val angle by animateFloatAsState(
        targetValue = if (isActive) 10f else -8f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 800f), // Fix: ressort plus sec
        label = "pedal"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.55f else 0f,
        animationSpec = tween(280),
        label = "pedalGlow"
    )

    Box(
        modifier = modifier
            .width(56.dp)
            .height(36.dp) // Fix: hauteur réduite
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isActive)
                    Brush.verticalGradient(listOf(Color(0x478FB2E8), Color(0x248FB2E8)))
                else
                    Brush.verticalGradient(listOf(panelTop, panelBottom))
            )
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val baseY = size.height * 0.78f

            if (glowAlpha > 0.01f) {
                // Fix: Halo contenu
                drawCircle(
                    color = accent.copy(alpha = glowAlpha * 0.45f),
                    radius = size.width * 0.40f,
                    center = Offset(cx, baseY + 4.dp.toPx())
                )
            }
            drawOval(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(cx - 16.dp.toPx(), baseY + 5.dp.toPx()),
                size = Size(32.dp.toPx() + if (isActive) 4.dp.toPx() else 0f, 4.dp.toPx())
            )
            val cable = Path().apply {
                moveTo(cx - 18.dp.toPx(), baseY - 4.dp.toPx())
                quadraticBezierTo(cx - 26.dp.toPx(), baseY - 6.dp.toPx(), cx - 30.dp.toPx(), baseY - 14.dp.toPx())
            }
            drawPath(cable, Color(0xFF2E3549), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))

            val spring = Path().apply {
                moveTo(cx - 14.dp.toPx(), baseY - 2.dp.toPx())
                quadraticBezierTo(cx - 11.dp.toPx(), baseY, cx - 11.dp.toPx(), baseY + 2.dp.toPx())
                quadraticBezierTo(cx - 11.dp.toPx(), baseY + 4.dp.toPx(), cx - 14.dp.toPx(), baseY + 2.dp.toPx())
            }
            drawPath(spring, Color(0xFF5A6379), style = Stroke(1.1.dp.toPx(), cap = StrokeCap.Round))

            drawRoundRect(
                color = Color(0xFF2E3549),
                topLeft = Offset(cx - 19.dp.toPx(), baseY),
                size = Size(10.dp.toPx(), 5.dp.toPx()),
                cornerRadius = CornerRadius(1.4.dp.toPx())
            )
            drawCircle(Color(0xFF1A2030), 1.dp.toPx(), Offset(cx - 14.dp.toPx(), baseY + 2.5.dp.toPx()))

            rotate(degrees = angle, pivot = Offset(cx - 14.dp.toPx(), baseY - 6.dp.toPx())) {
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF6E7799), Color(0xFF4B5373), Color(0xFF3F4766))),
                    topLeft = Offset(cx - 14.dp.toPx(), baseY - 16.dp.toPx()),
                    size = Size(36.dp.toPx(), 10.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                drawLine(
                    Color(0x0FFFFFFF),
                    Offset(cx - 12.dp.toPx(), baseY - 13.dp.toPx()),
                    Offset(cx + 20.dp.toPx(), baseY - 13.dp.toPx()),
                    0.6.dp.toPx()
                )
                // Fix: LED plus discrète
                drawRoundRect(
                    color = if (isActive) accent else Color(0xFF5A6379),
                    topLeft = Offset(cx + 18.dp.toPx(), baseY - 14.dp.toPx()),
                    size = Size(1.5.dp.toPx(), 4.dp.toPx()),
                    cornerRadius = CornerRadius(0.8.dp.toPx())
                )
            }
        }
    }
}
