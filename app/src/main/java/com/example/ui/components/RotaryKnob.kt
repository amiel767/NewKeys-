package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RotaryKnob(
    value: Float, // 0.0f to 1.0f
    onValueChange: (Float) -> Unit,
    label: String,
    valueText: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    activeColor: Color = NeonCyan,
    glowColor: Color = NeonCyanGlow
) {
    var currentValue by remember(value) { mutableFloatStateOf(value) }

    Column(
        modifier = modifier.width(size + 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        // dragging up increases value, down decreases
                        val delta = -dragAmount / 160f
                        currentValue = (currentValue + delta).coerceIn(0f, 1f)
                        onValueChange(currentValue)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Background LED Arc Track & Glow Fill
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = size.toPx() * 0.085f
                val padding = strokeWidth / 2 + 3f
                val arcSize = Size(this.size.width - padding * 2, this.size.height - padding * 2)
                val topLeft = Offset(padding, padding)

                // 270 degree arc from 135 deg to 405 deg (135 + 270)
                val startAngle = 135f
                val sweepAngleTotal = 270f
                val currentSweep = sweepAngleTotal * currentValue

                // Inactive track
                drawArc(
                    color = Color.White.copy(alpha = 0.08f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngleTotal,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Active glow
                if (currentSweep > 1f) {
                    drawArc(
                        color = glowColor,
                        startAngle = startAngle,
                        sweepAngle = currentSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth * 1.5f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.linearGradient(
                            colors = listOf(activeColor, activeColor.copy(alpha = 0.8f))
                        ),
                        startAngle = startAngle,
                        sweepAngle = currentSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            // Central Tactile Rotary Cap
            val capSize = size * 0.66f
            Box(
                modifier = Modifier
                    .size(capSize)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF383844), Color(0xFF1E1E26), Color(0xFF131318)),
                            radius = capSize.value * 1.4f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Notch indicator
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = this.size.width / 2
                    val center = Offset(radius, radius)
                    // Map 0..1 to 135..405 deg in radians
                    val angleDeg = 135f + (270f * currentValue)
                    val angleRad = (angleDeg * PI / 180f).toFloat()

                    val notchLength = radius * 0.45f
                    val notchStartRadius = radius * 0.40f
                    val startX = center.x + notchStartRadius * cos(angleRad)
                    val startY = center.y + notchStartRadius * sin(angleRad)
                    val endX = center.x + (notchStartRadius + notchLength) * cos(angleRad)
                    val endY = center.y + (notchStartRadius + notchLength) * sin(angleRad)

                    drawLine(
                        color = activeColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = size.toPx() * 0.05f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Label
        Text(
            text = label,
            fontSize = 9.sp,
            color = TextDim,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 11.sp
        )

        // Formatted Value
        if (valueText != null) {
            Text(
                text = valueText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = activeColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
