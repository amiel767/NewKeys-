package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.DrawScope
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

/**
 * 3D Semi-Realistic Rotary Knob with Glowing Fluid-Color LED Contour & Metallic Bezel.
 * Faithfully styled after hardware studio gear & Dark LED UI kits.
 */
@Composable
fun Led3DKnob(
    value: Float, // 0.0f to 1.0f
    onValueChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    valueText: String? = null,
    size: Dp = 64.dp,
    baseColor: Color = NeonCyan,
    dynamicColorMorph: Boolean = true
) {
    var currentValue by remember(value) { mutableFloatStateOf(value) }
    val onValueChangeState by rememberUpdatedState(onValueChange)

    // Calculate fluid morphing LED color across range: Cyan -> Sky Blue -> Violet -> Magenta -> Amber
    val dynamicLedColor = if (dynamicColorMorph) {
        when {
            currentValue < 0.25f -> lerpColor(Color(0xFF00E5FF), Color(0xFF00B0FF), currentValue / 0.25f)
            currentValue < 0.50f -> lerpColor(Color(0xFF00B0FF), Color(0xFF7C4DFF), (currentValue - 0.25f) / 0.25f)
            currentValue < 0.75f -> lerpColor(Color(0xFF7C4DFF), Color(0xFFE040FB), (currentValue - 0.50f) / 0.25f)
            else -> lerpColor(Color(0xFFE040FB), Color(0xFFFF9100), (currentValue - 0.75f) / 0.25f)
        }
    } else {
        baseColor
    }

    Column(
        modifier = modifier.width(size + 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        val delta = -dragAmount / 150f
                        currentValue = (currentValue + delta).coerceIn(0f, 1f)
                        onValueChangeState(currentValue)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Background Canvas: Metallic Outer Bezel + Ambient Track + Glowing Multi-Layer LED Ring
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val outerRadius = this.size.minDimension / 2f

                // 1. Outer Dark Chrome Bevel
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF383C48),
                            Color(0xFF20232B),
                            Color(0xFF111318),
                            Color(0xFF0A0B0E)
                        ),
                        center = center,
                        radius = outerRadius
                    ),
                    radius = outerRadius,
                    center = center
                )

                // 2. Bevel Highlight Ring
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0x55FFFFFF),
                            Color(0x11000000),
                            Color(0x44FFFFFF),
                            Color(0x11000000),
                            Color(0x55FFFFFF)
                        ),
                        center = center
                    ),
                    radius = outerRadius - 1.5f,
                    center = center,
                    style = Stroke(width = 1.8f)
                )

                // 3. Recessed Dark Groove
                val grooveRadius = outerRadius * 0.88f
                drawCircle(
                    color = Color(0xFF0C0D12),
                    radius = grooveRadius,
                    center = center
                )

                // 4. 270-degree LED Track (from 135 deg to 405 deg)
                val startAngle = 135f
                val sweepAngleTotal = 270f
                val currentSweep = sweepAngleTotal * currentValue
                val arcStrokeWidth = size.toPx() * 0.08f
                val arcPadding = (outerRadius - grooveRadius) + arcStrokeWidth / 2f + 2f
                val arcSize = Size(this.size.width - arcPadding * 2f, this.size.height - arcPadding * 2f)
                val arcTopLeft = Offset(arcPadding, arcPadding)

                // Background Inactive LED Track with tick dots
                drawArc(
                    color = Color(0x26FFFFFF),
                    startAngle = startAngle,
                    sweepAngle = sweepAngleTotal,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = arcStrokeWidth * 0.6f, cap = StrokeCap.Round)
                )

                // Glowing Active LED Ring (Triple-pass for intense neon light bloom)
                if (currentSweep > 1f) {
                    // Outer diffuse glow bloom
                    drawArc(
                        color = dynamicLedColor.copy(alpha = 0.35f),
                        startAngle = startAngle,
                        sweepAngle = currentSweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = arcStrokeWidth * 2.2f, cap = StrokeCap.Round)
                    )

                    // Core bright LED arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF), Color(0xFFE040FB), dynamicLedColor),
                            center = center
                        ),
                        startAngle = startAngle,
                        sweepAngle = currentSweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = arcStrokeWidth, cap = StrokeCap.Round)
                    )

                    // Center white-hot filament line
                    drawArc(
                        color = Color.White.copy(alpha = 0.75f),
                        startAngle = startAngle,
                        sweepAngle = currentSweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = arcStrokeWidth * 0.35f, cap = StrokeCap.Round)
                    )
                }
            }

            // Central Tactile Rotary Cap (Brushed metal with 3D drop shadow & concentric grooves)
            val capSize = size * 0.65f
            Box(
                modifier = Modifier
                    .size(capSize)
                    .shadow(10.dp, CircleShape, spotColor = Color.Black)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val radius = this.size.minDimension / 2f

                    // 1. Brushed Dark Metal Radial Face
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF3A3E4C),
                                Color(0xFF262933),
                                Color(0xFF16181F),
                                Color(0xFF0E1015)
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )

                    // 2. Concentric Tactile Grip Grooves
                    drawCircle(
                        color = Color(0x22FFFFFF),
                        radius = radius * 0.85f,
                        center = center,
                        style = Stroke(width = 1f)
                    )
                    drawCircle(
                        color = Color(0x18000000),
                        radius = radius * 0.65f,
                        center = center,
                        style = Stroke(width = 1f)
                    )
                    drawCircle(
                        color = Color(0x22FFFFFF),
                        radius = radius * 0.45f,
                        center = center,
                        style = Stroke(width = 0.8f)
                    )

                    // 3. Metallic Rim Outline
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(Color(0x66FFFFFF), Color(0x11000000), Color(0x66FFFFFF)),
                            center = center
                        ),
                        radius = radius - 1f,
                        center = center,
                        style = Stroke(width = 1.2f)
                    )

                    // 4. Illuminated Pointer Notch with Glowing LED Tip
                    val angleDeg = 135f + (270f * currentValue)
                    val angleRad = (angleDeg * PI / 180f).toFloat()

                    val notchLength = radius * 0.48f
                    val notchStartRadius = radius * 0.35f
                    val startX = center.x + notchStartRadius * cos(angleRad)
                    val startY = center.y + notchStartRadius * sin(angleRad)
                    val endX = center.x + (notchStartRadius + notchLength) * cos(angleRad)
                    val endY = center.y + (notchStartRadius + notchLength) * sin(angleRad)

                    // Pointer glow
                    drawLine(
                        color = dynamicLedColor.copy(alpha = 0.6f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = size.toPx() * 0.08f,
                        cap = StrokeCap.Round
                    )

                    // Pointer core
                    drawLine(
                        color = Color.White,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = size.toPx() * 0.045f,
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
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = dynamicLedColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun lerpColor(c1: Color, c2: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = c1.red + (c2.red - c1.red) * f,
        green = c1.green + (c2.green - c1.green) * f,
        blue = c1.blue + (c2.blue - c1.blue) * f,
        alpha = c1.alpha + (c2.alpha - c1.alpha) * f
    )
}
