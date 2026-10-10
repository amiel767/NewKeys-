// VelocitySettingsPage.kt
// Page « Velocity & Dynamic Response » : courbe réactive, presets calibrés, réglages de réponse, vélocité fixe.
// Jetpack Compose responsive : s'adapte parfaitement à tout écran (392dp à large tablette/desktop) sans forcer de scroll ni tronquer.

package com.soundstage.mixer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// ═══════════════════════════ 1. MODÈLE + MOTEUR (partagé UI / audio) ═══════════════════════════

data class CurveNode(val x: Int, val y: Int)

data class VelocityConfig(
    val nodes: List<CurveNode> = VelocityPresets.Linear,
    val floor: Int = 0,               // 0..126
    val ceiling: Int = 127,           // 1..127, toujours > floor
    val offset: Int = 0,              // -40..+40
    val humanize: Int = 0,            // 0..20 (± aléatoire, runtime uniquement)
    val fixedEnabled: Boolean = false,
    val fixedValue: Int = 100         // 1..127, valeur EXACTE émise quand fixedEnabled
)

object VelocityPresets {
    private val xs = intArrayOf(0, 32, 64, 96, 127)
    private fun n(vararg ys: Int) = xs.indices.map { CurveNode(xs[it], ys[it]) }

    val Linear = n(0, 32, 64, 96, 127)
    // Soft (toucher doux / léger) : courbe progressive/convexe, une frappe douce produit déjà une bonne dynamique
    val Soft = n(0, 48, 80, 106, 127)
    // Hard (toucher dur / lourd) : courbe concave, conserve la nuance pianissimo et exige une attaque ferme pour atteindre 127
    val Hard = n(0, 14, 38, 76, 127)
    // S-curve : réponse équilibrée très musicale
    val SCurve = n(0, 18, 64, 110, 127)
    // Compress : dynamique resserrée utilisable en live (35..120)
    val Compress = n(35, 62, 88, 108, 122)
    val Custom = emptyList<CurveNode>()

    val all: List<Pair<String, List<CurveNode>>> = listOf(
        "Linear" to Linear,
        "Soft" to Soft,
        "Hard" to Hard,
        "S-curve" to SCurve,
        "Compress" to Compress,
        "Custom" to Custom
    )
}

/** Interpolation cubique monotone (Fritsch–Carlson) : lisse, sans dépassement entre les points. */
class VelocityCurve(nodes: List<CurveNode>) {
    private val n = nodes.size
    private val xs = FloatArray(n) { nodes[it].x.toFloat() }
    private val ys = FloatArray(n) { nodes[it].y.toFloat() }
    private val m = FloatArray(n)

    init {
        val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
        m[0] = d[0]; m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
        for (i in 0 until n - 1) {
            if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f } else {
                val a = m[i] / d[i]; val b = m[i + 1] / d[i]; val h = a * a + b * b
                if (h > 9f) { val t = 3f / sqrt(h); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
            }
        }
    }

    fun at(x: Float): Float {
        val xc = x.coerceIn(xs[0], xs[n - 1])
        var i = 0
        while (i < n - 2 && xc > xs[i + 1]) i++
        val h = xs[i + 1] - xs[i]
        val t = (xc - xs[i]) / h; val t2 = t * t; val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h * m[i] +
            (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h * m[i + 1]
    }
}

object VelocityMath {
    /**
     * Sortie directe : FIXE (exact) > courbe > offset > bornes floor/ceiling.
     */
    fun output(c: VelocityConfig, curve: VelocityCurve, input: Float, jitter: Float = 0f): Float {
        if (c.fixedEnabled) return c.fixedValue.toFloat()
        var y = curve.at(input)
        y += c.offset + jitter
        return y.coerceIn(c.floor.toFloat(), c.ceiling.toFloat())
    }
}

/**
 * Processeur temps réel : table précalculée sans allocation dans process().
 */
class VelocityProcessor(private val config: VelocityConfig, private val random: Random = Random.Default) {
    private val table = IntArray(128).also { t ->
        val curve = VelocityCurve(config.nodes)
        for (v in 0..127) t[v] = VelocityMath.output(config, curve, v.toFloat()).roundToInt()
    }

    fun process(input: Int): Int {
        if (config.fixedEnabled) return config.fixedValue.coerceIn(1, 127)
        var out = table[input.coerceIn(1, 127)]
        if (config.humanize > 0) {
            out = (out + random.nextInt(-config.humanize, config.humanize + 1))
                .coerceIn(config.floor, config.ceiling)
        }
        return out.coerceIn(1, 127)
    }
}

// ═══════════════════════════ 2. PALETTE & STYLES ═══════════════════════════

private object Vel {
    val Bg = Color(0xFF131722)
    val PanelBorder = Color(0xFF283244)
    val CardTop = Color(0xFF202634)
    val CardBottom = Color(0xFF181E2A)
    val CardBorder = Color(0xFF2E384C)
    val KeyTop = Color(0xFF2B3344)
    val KeyBottom = Color(0xFF222938)
    val KeyBorder = Color(0xFF3B465E)
    val ActionTop = Color(0xFF333D52)
    val ActionBottom = Color(0xFF272F40)
    val ActionBorder = Color(0xFF455470)
    val TextPrimary = Color(0xFFF1F5F9)
    val TextSoft = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
    val BlueA = Color(0xFF9BC0F8)
    val BlueB = Color(0xFF8FB6F2)
    val BlueBorder = Color.Transparent
    val OnBlue = Color(0xFF101726)
    val Well = Color(0xFF0F141E)
    val WellBorder = Color(0xFF283346)
    val Grid = Color(0xFF1B2333)
    val Diagonal = Color(0xFF334155)
    val RawCurve = Color(0xFF64748B)

    val CardBrush get() = Brush.verticalGradient(listOf(CardTop, CardBottom))
    val KeyBrush get() = Brush.verticalGradient(listOf(KeyTop, KeyBottom))
    val ActionBrush get() = Brush.verticalGradient(listOf(ActionTop, ActionBottom))
    val BlueBrush get() = Brush.verticalGradient(listOf(BlueA, BlueB))
}

// ═══════════════════════════ 3. DIALOG & MAIN PAGE ═══════════════════════════

@Composable
fun VelocitySettingsDialog(
    trackNumber: Int,
    soundName: String,
    trackColor: Color,
    config: VelocityConfig,
    onConfigChange: (VelocityConfig) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .widthIn(max = 780.dp)
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Vel.Bg, lerp(Vel.Bg, trackColor, 0.08f))))
                .border(1.dp, Vel.PanelBorder, shape)
        ) {
            VelocitySettingsPage(
                trackNumber = trackNumber,
                soundName = soundName,
                trackColor = trackColor,
                config = config,
                onConfigChange = onConfigChange,
                onBack = onBack,
                onClose = onDismiss,
                onDone = onDismiss,
            )
        }
    }
}

@Composable
fun VelocitySettingsPage(
    trackNumber: Int,
    soundName: String,
    trackColor: Color,
    config: VelocityConfig,
    onConfigChange: (VelocityConfig) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit = onClose,
    modifier: Modifier = Modifier,
) {
    var test by rememberSaveable { mutableIntStateOf(64) }
    val curve = remember(config.nodes) { VelocityCurve(config.nodes) }
    val preset = matchPreset(config)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val isWide = maxWidth >= 480.dp

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header (titre + back + close)
            Header(
                title = "Velocity & Dynamic Response",
                subtitle = "Track $trackNumber · $soundName",
                onBack = onBack,
                onClose = onClose,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )

            // Contenu en 2 volets sans scroll forcé
            val leftCurvePanel: @Composable (Modifier) -> Unit = { mod ->
                CurveCard(
                    config = config,
                    curve = curve,
                    trackColor = trackColor,
                    preset = preset,
                    test = test,
                    onTest = { test = it },
                    onChange = onConfigChange,
                    modifier = mod
                )
            }

            val rightControlPanel: @Composable (Modifier) -> Unit = { mod ->
                Column(
                    modifier = mod,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Presets Grid (sans titre "Presets" pour gagner de la place)
                    PresetGrid(preset = preset, columns = 5) { nodes ->
                        onConfigChange(
                            config.copy(
                                nodes = nodes,
                                offset = 0,
                                floor = 0,
                                ceiling = 127,
                                fixedEnabled = false
                            )
                        )
                    }

                    // Dynamic Sliders
                    SettingsCard(c = config, track = trackColor, onChange = onConfigChange)

                    // Fixed Velocity Card
                    FixedVelocityCard(c = config, track = trackColor, onChange = onConfigChange)
                }
            }

            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    leftCurvePanel(Modifier.weight(1.05f).fillMaxHeight())
                    rightControlPanel(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    leftCurvePanel(Modifier.fillMaxWidth().weight(1.1f))
                    rightControlPanel(Modifier.fillMaxWidth().weight(1f))
                }
            }

            // Footer : Reset & Done sur fond propre transparent sans masquer le bas
            Footer(
                onReset = { onConfigChange(VelocityConfig()); test = 64 },
                onDone = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
            )
        }
    }
}

private fun matchPreset(c: VelocityConfig): String {
    if (c.fixedEnabled || c.offset != 0 || c.floor > 0 || c.ceiling < 127) return "Custom"
    return VelocityPresets.all.firstOrNull { it.second == c.nodes }?.first ?: "Custom"
}

// ═══════════════════════════ 4. HEADER & FOOTER ═══════════════════════════

@Composable
private fun Header(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0x1AFFFFFF))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Vel.TextPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = Vel.TextMuted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0x1AFFFFFF))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun Footer(onReset: () -> Unit, onDone: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        ActionButton("Reset", Modifier.width(78.dp), blue = false, onClick = onReset)
        Spacer(Modifier.width(8.dp))
        ActionButton("Done", Modifier.width(78.dp), blue = true, onClick = onDone)
    }
}

// ═══════════════════════════ 5. COMPOSANTS DE PANNEAUX ═══════════════════════════

@Composable
private fun CurveCard(
    config: VelocityConfig,
    curve: VelocityCurve,
    trackColor: Color,
    preset: String,
    test: Int,
    onTest: (Int) -> Unit,
    onChange: (VelocityConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val out = VelocityMath.output(config, curve, test.toFloat()).roundToInt()
    Card(modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Preset status & In -> Out live feedback
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Preset: $preset",
                    color = Vel.TextSoft,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("In ", color = Vel.TextMuted, fontSize = 10.5.sp)
                    Text("$test", color = Vel.TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    Text(" → Out ", color = Vel.TextMuted, fontSize = 10.5.sp)
                    Text("$out", color = brighten(trackColor), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            // Curve canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .padding(vertical = 3.dp)
            ) {
                CurveEditor(
                    config = config,
                    curve = curve,
                    trackColor = trackColor,
                    test = test,
                    onChange = onChange,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Slider de test compact avec label et valeur en dessous
            Column(modifier = Modifier.fillMaxWidth()) {
                FaderSlider(test, 1..127, trackColor, true) { onTest(it) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Test input velocity", color = Vel.TextMuted, fontSize = 9.sp)
                    Text("$test", color = Vel.TextPrimary, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    c: VelocityConfig,
    track: Color,
    onChange: (VelocityConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val on = !c.fixedEnabled
    Card(modifier.alpha(if (on) 1f else 0.45f)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledSlider("Floor", "${c.floor}", c.floor, 0..126, track, on, Modifier.weight(1f)) {
                    onChange(c.copy(floor = it, ceiling = maxOf(c.ceiling, it + 1)))
                }
                LabeledSlider("Ceiling", "${c.ceiling}", c.ceiling, 1..127, track, on, Modifier.weight(1f)) {
                    onChange(c.copy(ceiling = it, floor = minOf(c.floor, it - 1)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledSlider("Offset", (if (c.offset > 0) "+" else "") + c.offset, c.offset, -40..40, track, on, Modifier.weight(1f)) {
                    onChange(c.copy(offset = it))
                }
                LabeledSlider("Humanize", "±${c.humanize}", c.humanize, 0..20, track, on, Modifier.weight(1f)) {
                    onChange(c.copy(humanize = it))
                }
            }
        }
    }
}

@Composable
private fun FixedVelocityCard(
    c: VelocityConfig,
    track: Color,
    onChange: (VelocityConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            SwitchRow("Fixed velocity", "Bypasses dynamics with constant level", c.fixedEnabled, track) {
                onChange(c.copy(fixedEnabled = it))
            }
            if (c.fixedEnabled) {
                LabeledSlider("Exact value", "${c.fixedValue}", c.fixedValue, 1..127, track, true) {
                    onChange(c.copy(fixedValue = it))
                }
            }
        }
    }
}

// ═══════════════════════════ 6. ÉDITEUR DE COURBE (CANVAS) ═══════════════════════════

@Composable
private fun CurveEditor(
    config: VelocityConfig,
    curve: VelocityCurve,
    trackColor: Color,
    test: Int,
    onChange: (VelocityConfig) -> Unit,
    modifier: Modifier,
) {
    val cfg by rememberUpdatedState(config)
    val change by rememberUpdatedState(onChange)
    var dragging by remember { mutableIntStateOf(-1) }
    val editable = !config.fixedEnabled
    val bright = brighten(trackColor)
    val shape = RoundedCornerShape(9.dp)

    Canvas(
        modifier
            .clip(shape)
            .background(Vel.Well)
            .border(1.dp, Vel.WellBorder, shape)
            .pointerInput(editable) {
                if (!editable) return@pointerInput
                val pad = 14.dp.toPx()
                val hit = 26.dp.toPx()
                fun rect() = Rect(pad, pad, size.width - pad, size.height - pad)
                detectDragGestures(
                    onDragStart = { o ->
                        val r = rect()
                        var best = -1; var bestD = hit
                        cfg.nodes.forEachIndexed { i, nd ->
                            val d = hypot(o.x - (r.left + nd.x / 127f * r.width), o.y - (r.bottom - nd.y / 127f * r.height))
                            if (d < bestD) { bestD = d; best = i }
                        }
                        dragging = best
                    },
                    onDrag = { ch, _ ->
                        val i = dragging
                        if (i >= 0) {
                            ch.consume()
                            val r = rect()
                            val ny = ((r.bottom - ch.position.y) / r.height * 127f).roundToInt().coerceIn(0, 127)
                            val nodes = cfg.nodes
                            val nx = if (i == 0 || i == nodes.lastIndex) nodes[i].x else {
                                val raw = ((ch.position.x - r.left) / r.width * 127f).roundToInt()
                                raw.coerceIn(nodes[i - 1].x + 6, nodes[i + 1].x - 6)
                            }
                            change(cfg.copy(nodes = nodes.toMutableList().also { it[i] = CurveNode(nx, ny) }))
                        }
                    },
                    onDragEnd = { dragging = -1 },
                    onDragCancel = { dragging = -1 },
                )
            }
    ) {
        val pad = 14.dp.toPx()
        val r = Rect(pad, pad, size.width - pad, size.height - pad)
        fun px(v: Float) = r.left + v / 127f * r.width
        fun py(v: Float) = r.bottom - v / 127f * r.height
        fun pathOf(f: (Float) -> Float, steps: Int = 64) = Path().apply {
            for (i in 0..steps) {
                val x = i * 127f / steps
                val y = f(x).coerceIn(0f, 127f)
                if (i == 0) moveTo(px(x), py(y)) else lineTo(px(x), py(y))
            }
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
        val out = { x: Float -> VelocityMath.output(config, curve, x) }

        // Grille & Diagonale
        for (i in 0..4) {
            drawLine(Vel.Grid, Offset(r.left + r.width * i / 4f, r.top), Offset(r.left + r.width * i / 4f, r.bottom), 1.dp.toPx())
            drawLine(Vel.Grid, Offset(r.left, r.top + r.height * i / 4f), Offset(r.right, r.top + r.height * i / 4f), 1.dp.toPx())
        }
        drawLine(Vel.Diagonal, Offset(r.left, r.bottom), Offset(r.right, r.top), 1.dp.toPx(), pathEffect = dash)

        // Remplissage sous la courbe
        val eff = pathOf(out)
        val fill = Path().apply { addPath(eff); lineTo(px(127f), r.bottom); lineTo(px(0f), r.bottom); close() }
        drawPath(fill, Brush.verticalGradient(listOf(trackColor.copy(alpha = 0.26f), Color.Transparent), startY = r.top, endY = r.bottom))

        // Humanize band
        if (config.humanize > 0 && !config.fixedEnabled) {
            val h = config.humanize.toFloat()
            val band = Path()
            for (i in 0..32) { val x = i * 127f / 32; val y = (out(x) + h).coerceAtMost(127f); if (i == 0) band.moveTo(px(x), py(y)) else band.lineTo(px(x), py(y)) }
            for (i in 32 downTo 0) { val x = i * 127f / 32; band.lineTo(px(x), py((out(x) - h).coerceAtLeast(0f))) }
            band.close()
            drawPath(band, trackColor.copy(alpha = 0.12f))
        }

        // Floor / Ceiling
        if (!config.fixedEnabled) {
            if (config.floor > 0) drawLine(trackColor.copy(alpha = 0.4f), Offset(r.left, py(config.floor.toFloat())), Offset(r.right, py(config.floor.toFloat())), 1.dp.toPx(), pathEffect = dash)
            if (config.ceiling < 127) drawLine(trackColor.copy(alpha = 0.4f), Offset(r.left, py(config.ceiling.toFloat())), Offset(r.right, py(config.ceiling.toFloat())), 1.dp.toPx(), pathEffect = dash)
        }

        // Courbe effective
        drawPath(eff, bright.copy(alpha = 0.25f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(eff, bright, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Repère de test
        val ty = out(test.toFloat()).coerceIn(0f, 127f)
        val guide = Vel.BlueA.copy(alpha = 0.65f)
        drawLine(guide, Offset(px(test.toFloat()), r.bottom), Offset(px(test.toFloat()), py(ty)), 1.dp.toPx(), pathEffect = dash)
        drawLine(guide, Offset(r.left, py(ty)), Offset(px(test.toFloat()), py(ty)), 1.dp.toPx(), pathEffect = dash)
        drawCircle(Vel.BlueA, 4.dp.toPx(), Offset(px(test.toFloat()), py(ty)))

        // Poignées de courbe
        if (editable) {
            config.nodes.forEachIndexed { i, nd ->
                val c = Offset(px(nd.x.toFloat()), py(nd.y.toFloat()))
                val active = dragging == i
                drawCircle(trackColor.copy(alpha = if (active) 0.32f else 0.16f), (if (active) 11 else 9).dp.toPx(), c)
                drawCircle(Color(0xFF334155), 5.5.dp.toPx(), c)
                drawCircle(Vel.TextPrimary, 5.5.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
                drawCircle(trackColor, 2.dp.toPx(), c)
            }
        }
    }
}

// ═══════════════════════════ 7. ÉLÉMENTS GRAPHIQUES ET BOUTONS ═══════════════════════════

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .velSurface(RoundedCornerShape(11.dp), Vel.CardBrush, Vel.CardBorder)
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) { content() }
}

@Composable
private fun PresetGrid(preset: String, columns: Int, onPick: (List<CurveNode>) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        VelocityPresets.all.forEach { (name, nodes) ->
            val isSel = (preset == name)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .velClick {
                        if (nodes.isNotEmpty()) {
                            onPick(nodes)
                        }
                    }
                    .velSurface(
                        shape = RoundedCornerShape(7.dp),
                        brush = if (isSel) Vel.BlueBrush else Vel.KeyBrush,
                        border = if (isSel) Vel.BlueBorder else Vel.KeyBorder
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name,
                    color = if (isSel) Vel.OnBlue else Color(0xFFCBD5E1),
                    fontSize = 10.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    trackColor: Color,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 8.5.sp,
                color = Vel.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = trackColor,
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color(0xFF2B3344)
            )
        )
    }
}

@Composable
private fun ActionButton(label: String, modifier: Modifier, blue: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .height(36.dp)
            .velClick(onClick)
            .velSurface(
                shape = RoundedCornerShape(8.dp),
                brush = if (blue) Vel.BlueBrush else Vel.ActionBrush,
                border = if (blue) Vel.BlueBorder else Vel.ActionBorder
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (blue) Vel.OnBlue else Vel.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Int,
    range: IntRange,
    track: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Vel.TextSoft, fontSize = 10.5.sp)
            Text(valueText, color = Vel.TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
        FaderSlider(value, range, track, enabled, onChange)
    }
}

@Composable
private fun FaderSlider(value: Int, range: IntRange, track: Color, enabled: Boolean, onChange: (Int) -> Unit) {
    val onC by rememberUpdatedState(onChange)
    fun valueAt(x: Float, w: Float, m: Float): Int {
        val f = ((x - m) / (w - 2 * m)).coerceIn(0f, 1f)
        return range.first + (f * (range.last - range.first)).roundToInt()
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .pointerInput(range, enabled) {
                if (enabled) detectTapGestures { o -> onC(valueAt(o.x, size.width.toFloat(), 12.dp.toPx())) }
            }
            .pointerInput(range, enabled) {
                if (enabled) detectHorizontalDragGestures { ch, _ ->
                    ch.consume(); onC(valueAt(ch.position.x, size.width.toFloat(), 12.dp.toPx()))
                }
            }
    ) {
        val w = size.width; val h = size.height
        val m = 12.dp.toPx()
        val frac = if (range.last == range.first) 0f else (value - range.first).toFloat() / (range.last - range.first)
        val cx = m + frac.coerceIn(0f, 1f) * (w - 2 * m)
        val cy = h / 2f
        val rh = 7.dp.toPx()

        // Rail
        drawRoundRect(Color(0xFF0F141E), Offset(m, cy - rh / 2f), Size(w - 2 * m, rh), CornerRadius(rh / 2f))
        if (enabled && frac > 0f) {
            drawRoundRect(track.copy(alpha = 0.85f), Offset(m, cy - rh / 2f), Size((cx - m).coerceAtLeast(rh), rh), CornerRadius(rh / 2f))
        }

        // Handle
        val hw = 20.dp.toPx(); val hh = 24.dp.toPx()
        drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(cx - hw / 2f, cy - hh / 2f + 1.dp.toPx()), Size(hw, hh), CornerRadius(5.dp.toPx()))
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF475569), Color(0xFF1E293B))),
            topLeft = Offset(cx - hw / 2f, cy - hh / 2f),
            size = Size(hw, hh),
            cornerRadius = CornerRadius(5.dp.toPx())
        )
        drawRoundRect(Color.White.copy(alpha = 0.2f), Offset(cx - hw / 2f, cy - hh / 2f), Size(hw, hh), CornerRadius(5.dp.toPx()), style = Stroke(1.dp.toPx()))
        drawLine(if (enabled) track else Color.Gray, Offset(cx, cy - 5.dp.toPx()), Offset(cx, cy + 5.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
    }
}

// ═══════════════════════════ 8. UTILITAIRES DE STYLE ═══════════════════════════

private fun brighten(c: Color) = lerp(c, Color.White, 0.45f)

private fun Modifier.velSurface(shape: Shape, brush: Brush, border: Color) = this
    .clip(shape)
    .background(brush)
    .border(1.dp, border, shape)

@Composable
private fun Modifier.velClick(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val p by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (p) 0.95f else 1f, label = "velClickScale")
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}
