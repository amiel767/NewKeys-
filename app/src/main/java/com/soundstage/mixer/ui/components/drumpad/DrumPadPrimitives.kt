package com.soundstage.mixer.ui.components.drumpad

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.SurfaceFxChain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object DpPalette {
    private val COLORS = listOf(
        Color(0xFF7F9BC7), Color(0xFF9A86C2), Color(0xFFC27A8E), Color(0xFFC9A15C),
        Color(0xFF86A394), Color(0xFF6FB3A8), Color(0xFFB58E6B), Color(0xFF8D97B8)
    )
    fun at(idx: Int): Color = COLORS[((idx % 8) + 8) % 8]
}

object DpColors {
    val PANEL_TOP = Color(0xFF232C3E)
    val PANEL_BOTTOM = Color(0xFF1A2230)
    val WELL = Color(0xFF0F141D)
    val INK = Color(0xFFE9EDF5)
    val MUTE = Color(0xFF8690A4)
    val SOFT = Color(0xFFDCE2EE)
    val ACCENT = Color(0xFF8FB2E8)
    val WARM = Color(0xFFD8A24A)
    val ROSE = Color(0xFFC2587A)
    val GREEN = Color(0xFF6EE7B7)
}

fun hslToColor(h: Float, s: Float, l: Float, a: Float = 1f): Color {
    val hh = (((h % 360f) + 360f) % 360f) / 60f
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs(hh % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        hh < 1f -> Triple(c, x, 0f); hh < 2f -> Triple(x, c, 0f)
        hh < 3f -> Triple(0f, c, x); hh < 4f -> Triple(0f, x, c)
        hh < 5f -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    return Color((r1+m).coerceIn(0f,1f), (g1+m).coerceIn(0f,1f), (b1+m).coerceIn(0f,1f), a.coerceIn(0f,1f))
}

fun vivColor(noteIndex: Int, alpha: Float = 1f): Color {
    return hslToColor(((noteIndex * 30 + 205) % 360).toFloat(), 0.84f, 0.61f, alpha)
}

private object NoiseTexture {
    val bitmap: ImageBitmap by lazy {
        val size = 120
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val rnd = Random(42L)
        for (y in 0 until size) for (x in 0 until size) {
            val v = rnd.nextInt(256); bmp.setPixel(x, y, AColor.argb(v, v, v, v))
        }
        bmp.asImageBitmap()
    }
    val brush: Brush = object : ShaderBrush() {
        override fun createShader(size: Size): android.graphics.Shader = ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated)
    }
}

fun Modifier.noiseOverlay(alpha: Float = 0.06f): Modifier = this.drawWithContent {
    drawContent()
    drawRect(brush = NoiseTexture.brush, alpha = alpha, blendMode = BlendMode.Overlay)
}

@Composable
fun DpIcon(name: String, size: Dp = 16.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (name) {
            "back" -> {
                drawLine(color, Offset(w*0.8f, h*0.5f), Offset(w*0.2f, h*0.5f), strokeWidth = w*0.12f, cap = StrokeCap.Round)
                drawLine(color, Offset(w*0.2f, h*0.5f), Offset(w*0.4f, h*0.3f), strokeWidth = w*0.12f, cap = StrokeCap.Round)
                drawLine(color, Offset(w*0.2f, h*0.5f), Offset(w*0.4f, h*0.7f), strokeWidth = w*0.12f, cap = StrokeCap.Round)
            }
            "gear" -> {
                drawCircle(color, w*0.15f, center = Offset(w*0.5f, h*0.5f), style = stroke)
                for (i in 0 until 8) {
                    val angle = i * Math.PI / 4
                    val sx = (w*0.35f * cos(angle)).toFloat() + w*0.5f
                    val sy = (h*0.35f * sin(angle)).toFloat() + h*0.5f
                    val ex = (w*0.45f * cos(angle)).toFloat() + w*0.5f
                    val ey = (h*0.45f * sin(angle)).toFloat() + h*0.5f
                    drawLine(color, Offset(sx, sy), Offset(ex, ey), strokeWidth = w*0.1f, cap = StrokeCap.Round)
                }
            }
            "wave" -> {
                val path = Path().apply {
                    moveTo(w*0.1f, h*0.5f); lineTo(w*0.3f, h*0.5f)
                    lineTo(w*0.4f, h*0.2f); lineTo(w*0.6f, h*0.8f)
                    lineTo(w*0.7f, h*0.5f); lineTo(w*0.9f, h*0.5f)
                }
                drawPath(path, color, style = stroke)
            }
            "folder" -> {
                val path = Path().apply {
                    moveTo(w*0.1f, h*0.8f); lineTo(w*0.1f, h*0.2f)
                    lineTo(w*0.4f, h*0.2f); lineTo(w*0.5f, h*0.35f)
                    lineTo(w*0.9f, h*0.35f); lineTo(w*0.9f, h*0.8f); close()
                }
                drawPath(path, color, style = stroke)
            }
            "x" -> {
                drawLine(color, Offset(w*0.2f, h*0.2f), Offset(w*0.8f, h*0.8f), strokeWidth = w*0.12f, cap = StrokeCap.Round)
                drawLine(color, Offset(w*0.8f, h*0.2f), Offset(w*0.2f, h*0.8f), strokeWidth = w*0.12f, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
fun DpRoll(value: Any, style: TextStyle) {
    val key = value.toString()
    var prevKey by remember { mutableStateOf(key) }
    val alphaAnim = remember { Animatable(1f) }
    val transAnim = remember { Animatable(0f) }
    LaunchedEffect(key) {
        if (prevKey != key) {
            alphaAnim.snapTo(0f); transAnim.snapTo(12f)
            launch { alphaAnim.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
            launch { transAnim.animateTo(0f, tween(220, easing = FastOutSlowInEasing)) }
            prevKey = key
        }
    }
    Text(key, style = style, modifier = Modifier.graphicsLayer { alpha = alphaAnim.value; translationY = transAnim.value })
}

@Composable
fun <T> DpSeg(options: List<Pair<T, String>>, selected: T, onChange: (T) -> Unit, modifier: Modifier = Modifier, height: Dp = 22.dp, warn: Boolean = false) {
    val idx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(modifier.height(height + 6.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xCC0F141D)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(3.dp)) {
        val segW = maxWidth / options.size
        val offsetX by animateDpAsState(segW * idx, spring(dampingRatio = 0.6f, stiffness = 500f), label = "seg")
        Box(Modifier.offset(x = offsetX).width(segW).fillMaxHeight().padding(horizontal = 1.dp).clip(RoundedCornerShape(9.dp)).background(Brush.verticalGradient(listOf(Color(0xFF38445E), Color(0xFF2B3650)))).drawBehind {
            drawRoundRect(Color.Black.copy(alpha = 0.4f), topLeft = Offset(0f, size.height * 0.15f), size = Size(size.width, size.height), cornerRadius = CornerRadius(9.dp.toPx()))
        })
        Row(Modifier.fillMaxSize()) {
            options.forEach { (k, l) ->
                val isSel = selected == k
                val color = if (!isSel) Color(0xFF8690A4) else if (warn && l == "Edit") DpColors.WARM else Color(0xFFF1F4FA)
                Box(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(k) }, contentAlignment = Alignment.Center) {
                    Text(l, color = color, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun DpNoteKey(label: String, oct: Int, idx: Int, on: Boolean, ghost: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val hue = ((idx * 30 + 205) % 360).toFloat()
    val tintBase = hslToColor(hue, 0.84f, 0.61f)
    val tint98 = hslToColor(hue, 0.84f, 0.61f, 0.98f)
    val tint66 = hslToColor(hue, 0.84f, 0.61f, 0.66f)
    val tint26 = hslToColor(hue, 0.84f, 0.61f, 0.26f)
    var pressed by remember { mutableStateOf(false) }
    
    val pressScale by animateFloatAsState(if (pressed) 0.965f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "note")
    val bg = if (on) Brush.verticalGradient(listOf(tint98, tint66)) else Brush.verticalGradient(listOf(tint26, Color(0xFF1B2336)))

    Box(
        modifier = modifier.graphicsLayer { scaleX = pressScale; scaleY = pressScale }.clip(RoundedCornerShape(13.dp)).background(bg)
            .then(if (!on && ghost) Modifier.border(1.5.dp, hslToColor(hue, 0.84f, 0.61f, 0.9f), RoundedCornerShape(13.dp)) else Modifier)
            .noiseOverlay(0.06f).pointerInput(idx) { detectTapGestures(onPress = { pressed = true; onToggle(); tryAwaitRelease(); pressed = false }) },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (on) Color.White else tintBase, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
        if (on) Box(Modifier.fillMaxSize()) { Text(oct.toString(), color = Color.White.copy(alpha = 0.85f), fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) }
    }
}

@Composable
fun DpVolumeKnob(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, size: Dp = 52.dp, onDoubleTap: Float = 50f, showLabel: Boolean = true) {
    val v = value.coerceIn(0f, 100f)
    var dragStartV by remember { mutableStateOf(0f) }
    
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        Box(Modifier.size(size).pointerInput(Unit) { detectDragGestures(onDragStart = { dragStartV = v }) { change, dragAmount ->
            change.consume()
            val newV = (dragStartV - dragAmount.y * 0.6f).coerceIn(0f, 100f)
            dragStartV = newV
            onChange(newV)
        } }.pointerInput(Unit) { detectTapGestures(onDoubleTap = { onChange(onDoubleTap) }) }) {
            val angle = -135f + (v / 100f) * 270f
            Canvas(Modifier.fillMaxSize()) {
                val c = center; val r = size.toPx() * 0.43f
                drawArc(Color.White.copy(alpha = 0.08f), 135f, 270f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                if (v > 0.5f) drawArc(Brush.linearGradient(listOf(DpColors.ACCENT.copy(alpha = 0.45f), DpColors.ACCENT), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)), 135f, 270f * (v / 100f), false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(Color.Black.copy(alpha = 0.5f), r * 0.76f, c + Offset(0f, 1.6.dp.toPx()))
                drawCircle(Brush.radialGradient(listOf(Color(0xFF626C90), Color(0xFF2C3550), Color(0xFF141A29)), Offset(c.x - r*0.28f, c.y - r*0.44f), r*1.56f), r * 0.74f, c)
                rotate(angle, c) { drawLine(DpColors.ACCENT, c + Offset(0f, -r * 0.95f), c + Offset(0f, -r * 0.55f), 2.4.dp.toPx(), StrokeCap.Round) }
            }
        }
        if (showLabel) Column {
            Text("VOLUME", color = Color(0xFF7B859A), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            Text("${v.toInt()}%", color = Color(0xFFF1F4FA), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun DpFxCase(fx: SurfaceFxChain, includeShimmer: Boolean, label: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val effects = buildList { add(fx.filterOn); add(fx.brightnessOn); add(fx.delayOn); add(fx.reverbOn); if (includeShimmer) add(fx.shimmerOn) }
    val active = effects.count { it }; val anyOn = active > 0
    val bg = if (anyOn) Brush.verticalGradient(listOf(Color(0xFF34304A), Color(0xFF262238))) else Brush.verticalGradient(listOf(Color(0xFF2A3347), Color(0xFF1F2739)))
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(bg).then(if (anyOn) Modifier.border(1.dp, DpColors.WARM.copy(alpha = 0.4f), RoundedCornerShape(14.dp)) else Modifier).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpen() }.padding(10.dp, 8.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = if (anyOn) Color(0xFFF1F4FA) else Color(0xFF9AA5BA), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                Text("$active/${effects.size}", color = if (anyOn) DpColors.WARM else Color(0xFF566075), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                effects.forEach { on -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (on) DpColors.WARM else Color.White.copy(alpha = 0.1f))) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// DpLedRing — conic gradient spinnant (JSX `LedRing`)
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun DpLedRing(
    color: Color,
    mode: String,
    durMs: Int = 2000,
    radius: Dp = 18.dp,
    strokeWidth: Dp = 2.5.dp,
    modifier: Modifier = Modifier,
) {
    if (mode == "off") return
    val rot by rememberInfiniteTransition(label = "ledSpin").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durMs, easing = androidx.compose.animation.core.LinearEasing)),
        label = "ledRot"
    )
    val pulse by rememberInfiniteTransition(label = "ledPulse").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(260, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ledPulse"
    )
    Canvas(modifier.fillMaxSize()) {
        val stroke = strokeWidth.toPx()
        val r = size.minDimension / 2f - stroke / 2f
        when (mode) {
            "run" -> {
                rotate(rot) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color.Transparent, Color.Transparent, color),
                            center = center
                        ),
                        radius = r,
                        style = Stroke(stroke)
                    )
                }
            }
            "queued" -> {
                drawCircle(color = color.copy(alpha = pulse), radius = r, style = Stroke(stroke))
            }
            else -> {
                drawCircle(color = color, radius = r, style = Stroke(stroke))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// DpVolumeOverlay — 14 bars + % (JSX `VolumeOverlay`)
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun DpVolumeOverlay(value: Int, color: Color, modifier: Modifier = Modifier) {
    val v = value.coerceIn(0, 100)
    val active = (v * 14f / 100f).toInt()
    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xC7060914)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$v%",
                color = Color(0xFFF1F4FA),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.height(30.dp)
            ) {
                for (i in 0 until 14) {
                    val frac = i / 14f
                    val on = i < active
                    val barColor = when {
                        frac > 0.82f -> DpColors.ROSE
                        frac > 0.6f -> DpColors.WARM
                        else -> color
                    }
                    val h = (6 + frac * 22).dp
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(h)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (on) barColor else Color.White.copy(alpha = 0.12f))
                    )
                }
            }
        }
    }
}
