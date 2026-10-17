package com.soundstage.mixer.ui.components.drumpad

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ─── Palette v5 (8 couleurs stables) ───
object DpPalette {
    val colors: List<Color> = listOf(
        Color(0xFF7F9BC7), // steel
        Color(0xFF9A86C2), // plum
        Color(0xFFC27A8E), // dusty rose
        Color(0xFFC9A15C), // sand
        Color(0xFF86A394), // sage
        Color(0xFF6FB3A8), // teal
        Color(0xFFB58E6B), // copper
        Color(0xFF8D97B8), // slate
    )
    fun at(index: Int): Color = colors[index % 8]
}

object DpColors {
    val Accent = Color(0xFF8FB2E8)
    val AccentDeep = Color(0xFF7FA3DD)
    val AccentText = Color(0xFF0F1726)
    val Warm = Color(0xFFD8A24A)
    val Rose = Color(0xFFC2587A)
    val RoseDeep = Color(0xFF8A3B56)
    val Ink = Color(0xFFE9EDF5)
    val Mute = Color(0xFF8690A4)
    val Dim = Color(0xFF7B859A)
    val Well = Color(0xFF0F141D)
    val PanelTop = Color(0xFF232C3E)
    val PanelBottom = Color(0xFF1A2230)
    val BgTop = Color(0xFF141B27)
    val BgBottom = Color(0xFF0F141D)
}

fun dpWhite(alpha: Float): Color = Color.White.copy(alpha = alpha)

fun dpText(size: Float, weight: FontWeight = FontWeight.Normal, color: Color = DpColors.Ink): TextStyle =
    TextStyle(fontSize = size.sp, fontWeight = weight, color = color, letterSpacing = 0.sp)

fun vivColor(noteIndex: Int, alpha: Float = 1f): Color {
    val hue = ((noteIndex * 30 + 205) % 360).toFloat()
    return Color.hsl(hue, 0.84f, 0.61f).copy(alpha = alpha)
}

// ─── Roll — animation de chiffre qui change ───
@Composable
fun DpRoll(value: Any, style: TextStyle) {
    val prev = remember { mutableStateOf(value) }
    var trigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(value) {
        if (prev.value != value) {
            prev.value = value
            trigger++
        }
    }
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(220),
        label = "roll$trigger"
    )
    Text(
        text = value.toString(),
        style = style,
        modifier = Modifier.alpha(alpha)
    )
}

// ─── Seg — segmenté générique ───
@Composable
fun <T> DpSeg(
    options: List<Pair<T, String>>,
    value: T,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
    warn: Boolean = false,
) {
    val idx = options.indexOfFirst { it.first == value }.coerceAtLeast(0)
    val fraction by animateFloatAsState(
        targetValue = if (options.isNotEmpty()) idx.toFloat() / options.size else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
        label = "segFrac"
    )
    BoxWithConstraints(
        modifier
            .height(height + 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC0F141D))
            .border(1.dp, dpWhite(0.08f), RoundedCornerShape(12.dp))
            .padding(3.dp)
    ) {
        if (options.isNotEmpty()) {
            val segFraction = 1f / options.size
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(maxWidth * segFraction)
                    .offset(x = maxWidth * fraction)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF38445E), Color(0xFF2B3650))))
            )
        }
        Row(Modifier.fillMaxSize()) {
            options.forEach { (key, label) ->
                val on = key == value
                val c by animateColorAsState(
                    if (on) (if (warn && label == "Edit") DpColors.Warm else Color(0xFFF1F4FA)) else DpColors.Mute,
                    tween(200),
                    label = "segColor"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) { detectTapGestures { onChange(key) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = dpText(11.5f, FontWeight.Bold, c), maxLines = 1)
                }
            }
        }
    }
}

// ─── LedRing — anneau LED (statique ou rotatif) ───
@Composable
fun DpLedRing(
    color: Color,
    mode: String,           // "off" | "run" | "queued" | "solid"
    durSeconds: Float = 2f,
    cornerDp: Dp = 18.dp,
    strokeDp: Dp = 2.5.dp,
) {
    if (mode == "off") return
    val infiniteTransition = rememberInfiniteTransition(label = "led")
    val rotation by if (mode == "run") {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween((durSeconds * 1000).toInt(), easing = LinearEasing), RepeatMode.Restart),
            label = "ledRot"
        )
    } else remember { mutableFloatStateOf(0f) }
    val alpha by if (mode == "queued") {
        infiniteTransition.animateFloat(
            initialValue = 0.35f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
            label = "ledPulse"
        )
    } else remember { mutableFloatStateOf(1f) }
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerDp))
    ) {
        val strokePx = strokeDp.toPx()
        val inset = strokePx / 2f
        val cornerPx = cornerDp.toPx()
        if (mode == "run") {
            rotate(rotation) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, color, Color.Transparent)),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    alpha = alpha
                )
            }
        } else {
            drawRoundRect(
                color = color,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx),
                style = Stroke(width = strokePx),
                alpha = alpha
            )
        }
    }
}

// ─── VolumeOverlay — 14 barres, pad → amber → rose ───
@Composable
fun DpVolumeOverlay(value: Float, padColor: Color, modifier: Modifier = Modifier) {
    val N = 14
    val active = Math.round((value / 100f) * N)
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xC7060914)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${value.toInt()}%",
                style = dpText(22f, FontWeight.ExtraBold, Color(0xFFF1F4FA))
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                for (i in 0 until N) {
                    val on = i < active
                    val h = (6 + (i.toFloat() / N) * 22).dp
                    val segCol = when {
                        (i.toFloat() / N) > 0.82f -> DpColors.Rose
                        (i.toFloat() / N) > 0.6f -> DpColors.Warm
                        else -> padColor
                    }
                    Box(
                        Modifier
                            .padding(horizontal = 1.dp)
                            .width(4.dp)
                            .height(h)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (on) segCol else dpWhite(0.12f))
                    )
                }
            }
        }
    }
}

// ─── VolumeKnob — 3D slider drag vertical ───
@Composable
fun DpVolumeKnob(
    value: Float,               // 0..100
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 52.dp,
    onDoubleTap: Float = 80f,
    label: Boolean = true,
) {
    var dragStart by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    val angle = -135f + (value / 100f) * 270f
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(sizeDp)
                .pointerInput(value) {
                    detectDragGestures(
                        onDragStart = { dragStart = it.y to value },
                        onDragEnd = { dragStart = null },
                        onDragCancel = { dragStart = null },
                        onDrag = { change, dy ->
                            change.consume()
                            val start = dragStart ?: return@detectDragGestures
                            val newValue = (start.second + (start.first - change.position.y) * 0.6f).coerceIn(0f, 100f)
                            onChange(newValue)
                        },
                    )
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onChange(onDoubleTap) }) }
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val cx = this.size.width / 2
                val cy = this.size.height / 2
                val outerR = this.size.minDimension / 2 * 0.95f
                val innerR = outerR * 0.78f
                // Outer ring
                drawCircle(
                    color = dpWhite(0.08f),
                    radius = outerR,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3f)
                )
                // Arc progress
                drawArc(
                    color = DpColors.Accent,
                    startAngle = 135f,
                    sweepAngle = (value / 100f) * 270f,
                    useCenter = false,
                    topLeft = Offset(cx - outerR, cy - outerR),
                    size = androidx.compose.ui.geometry.Size(outerR * 2, outerR * 2),
                    style = Stroke(width = 3f, cap = StrokeCap.Round),
                )
                // Body
                drawCircle(color = Color(0xFF2C3550), radius = innerR, center = Offset(cx, cy))
                drawCircle(color = dpWhite(0.14f), radius = innerR, center = Offset(cx, cy), style = Stroke(width = 1f))
                // Indicator
                val rad = Math.toRadians((angle + 90f).toDouble())
                val endX = cx + (innerR * 0.85f * cos(rad)).toFloat()
                val endY = cy + (innerR * 0.85f * sin(rad)).toFloat()
                drawLine(DpColors.Accent, Offset(cx, cy), Offset(endX, endY), strokeWidth = 2.5f, cap = StrokeCap.Round)
            }
        }
        if (label) {
            Column {
                Text("VOLUME", style = dpText(9.5f, FontWeight.Bold, DpColors.Dim).copy(letterSpacing = 1.2.sp))
                Text("${value.toInt()}%", style = dpText(17f, FontWeight.ExtraBold, Color(0xFFF1F4FA)))
            }
        }
    }
}
