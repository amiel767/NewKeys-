// VelocitySettingsPage.kt
// Page « Velocity & Dynamic Response » : courbe manipulable, presets, réglages, vélocité fixe (exacte).
// Jetpack Compose, aucune image/ressource. S'adapte à toute fenêtre rectangulaire (2 colonnes si large, 1 si étroit, scroll si bas).

package com.soundstage.mixer.ui.components

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
    val fixedValue: Int = 100,        // 1..127, valeur EXACTE émise quand fixedEnabled
    val invert: Boolean = false,
)

object VelocityPresets {
    private val xs = intArrayOf(0, 32, 64, 96, 127)
    private fun n(vararg ys: Int) = xs.indices.map { CurveNode(xs[it], ys[it]) }

    val Linear = n(0, 32, 64, 96, 127)
    val Soft = n(0, 52, 84, 107, 127)
    val Hard = n(0, 14, 42, 80, 127)
    val SCurve = n(0, 14, 64, 113, 127)
    val Compress = n(24, 64, 92, 111, 127)

    val all: List<Pair<String, List<CurveNode>>> = listOf(
        "Linear" to Linear, "Soft" to Soft, "Hard" to Hard, "S-curve" to SCurve, "Compress" to Compress,
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
     * Sortie « théorique » (sans aléa), utilisée par l'UI et par la table de runtime.
     * Ordre : FIXE (exact) > courbe > inversion > offset > bornes floor/ceiling.
     */
    fun output(c: VelocityConfig, curve: VelocityCurve, input: Float, jitter: Float = 0f): Float {
        if (c.fixedEnabled) return c.fixedValue.toFloat()          // exact : aucun autre réglage ne s'applique
        var y = curve.at(input)
        if (c.invert) y = 127f - y
        y += c.offset + jitter
        return y.coerceIn(c.floor.toFloat(), c.ceiling.toFloat())
    }
}

/**
 * Processeur temps réel : construis-le UNE fois par config, puis appelle process() à chaque note-on.
 * Aucune allocation dans process() (table de 128 entrées).
 */
class VelocityProcessor(private val config: VelocityConfig, private val random: Random = Random.Default) {
    private val table = IntArray(128).also { t ->
        val curve = VelocityCurve(config.nodes)
        for (v in 0..127) t[v] = VelocityMath.output(config, curve, v.toFloat()).roundToInt()
    }

    /** @param input vélocité d'entrée 1..127 ; retourne 1..127 (0 serait un note-off, jamais émis). */
    fun process(input: Int): Int {
        if (config.fixedEnabled) return config.fixedValue.coerceIn(1, 127)           // exact, sans aléa
        var out = table[input.coerceIn(1, 127)]
        if (config.humanize > 0) {
            out = (out + random.nextInt(-config.humanize, config.humanize + 1))
                .coerceIn(config.floor, config.ceiling)
        }
        return out.coerceIn(1, 127)
    }
}

// ═══════════════════════════ 2. PALETTE ═══════════════════════════

private object Vel {
    val Bg = Color(0xFF151924)
    val PanelBorder = Color(0xFF262C3C)
    val CardTop = Color(0xFF2D3243)
    val CardBottom = Color(0xFF262B3A)
    val CardBorder = Color(0xFF353C50)
    val KeyTop = Color(0xFF323849)
    val KeyBottom = Color(0xFF292E3D)
    val KeyBorder = Color(0xFF3A4156)
    val ActionTop = Color(0xFF424A5B)
    val ActionBottom = Color(0xFF343A4A)
    val ActionBorder = Color(0xFF50586D)
    val TextPrimary = Color(0xFFE8EBF3)
    val TextSoft = Color(0xFFA8B0C4)
    val TextMuted = Color(0xFF7C818E)
    val BlueA = Color(0xFFA7C6F0)
    val BlueB = Color(0xFFA2B9E5)
    val BlueBorder = Color(0xFFBCD2F4)
    val OnBlue = Color(0xFF252C3F)
    val Well = Color(0xFF0F1624)
    val WellBorder = Color(0xFF2F3A55)
    val Grid = Color(0xFF1D2740)
    val Diagonal = Color(0xFF34405E)
    val RawCurve = Color(0xFF7D8AA8)

    val CardBrush get() = Brush.verticalGradient(listOf(CardTop, CardBottom))
    val KeyBrush get() = Brush.verticalGradient(listOf(KeyTop, KeyBottom))
    val ActionBrush get() = Brush.verticalGradient(listOf(ActionTop, ActionBottom))
    val BlueBrush get() = Brush.linearGradient(listOf(BlueA, BlueB))
}

// ═══════════════════════════ 3. API PUBLIQUE ═══════════════════════════

/** Fenêtre autonome (même panneau que TrackSettingsDialog). */
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
                .width(760.dp)
                .widthIn(max = 780.dp)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Vel.Bg, lerp(Vel.Bg, trackColor, 0.09f))))
                .border(1.dp, Vel.PanelBorder, shape)
        ) {
            VelocitySettingsPage(
                trackNumber, soundName, trackColor, config, onConfigChange,
                onBack = onBack, onClose = onDismiss, onDone = onDismiss,
            )
        }
    }
}

/**
 * Contenu de la page Velocity & Dynamic Response :
 * - Volet gauche : Courbe rectangulaire compacte + Test input (slider en haut, titre et valeur discrets dessous)
 * - Volet droit : Presets en haut (sans titre redondant), Réglages (Floor/Ceiling/Offset/Humanize), et Bascules (Fixed velocity, Invert)
 * - Pied de page : Reset et Done aimantés à droite
 * - Aucun scroll forcé
 */
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
    var test by rememberSaveable { mutableStateOf(64) }
    val curve = remember(config.nodes) { VelocityCurve(config.nodes) }
    val preset = matchPreset(config)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 500.dp
        val isNarrow = maxWidth < 400.dp

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // En-tête
            Header(
                title = "Velocity & Dynamic Response",
                subtitle = "Track $trackNumber · $soundName",
                onBack = onBack,
                onClose = onClose,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )

            // Contenu 2 volets sans scroll forcé
            val curveCard: @Composable () -> Unit = {
                CurveCard(config, curve, trackColor, preset, test, { test = it }, onConfigChange)
            }
            val presets: @Composable () -> Unit = {
                PresetGrid(preset, if (isNarrow) 3 else 5) { nodes ->
                    onConfigChange(
                        config.copy(
                            nodes = nodes, invert = false, offset = 0, floor = 0, ceiling = 127,
                            fixedEnabled = false,
                        )
                    )
                }
            }
            val settings: @Composable () -> Unit = { SettingsCard(config, trackColor, onConfigChange) }
            val toggles: @Composable () -> Unit = { TogglesCard(config, trackColor, onConfigChange) }

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Volet gauche : Courbe
                    Column(
                        Modifier.weight(1.05f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        curveCard()
                    }
                    // Volet droit : Presets en haut, Réglages, puis Bascules
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets()
                        settings()
                        toggles()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    curveCard()
                    presets()
                    settings()
                    toggles()
                }
            }

            // Pied de page : Reset & Done aimantés à droite
            Footer(
                onReset = { onConfigChange(VelocityConfig()); test = 64 },
                onDone = onDone,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
            )
        }
    }
}

private fun matchPreset(c: VelocityConfig): String {
    if (c.fixedEnabled || c.invert || c.offset != 0 || c.floor > 0 || c.ceiling < 127) return "Custom"
    return VelocityPresets.all.firstOrNull { it.second == c.nodes }?.first ?: "Custom"
}

// ═══════════════════════════ 4. CARTES ═══════════════════════════

@Composable
private fun Header(title: String, subtitle: String, onBack: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KeyButton(VIcon.Back, onBack)
        Column(Modifier.weight(1f)) {
            Text(title, color = Vel.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Vel.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        KeyButton(VIcon.Close, onClose)
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
        ActionButton("Reset", Modifier.width(76.dp), blue = false, onClick = onReset)
        Spacer(Modifier.width(8.dp))
        ActionButton("Done", Modifier.width(76.dp), blue = true, onClick = onDone)
    }
}

@Composable
private fun CurveCard(
    config: VelocityConfig,
    curve: VelocityCurve,
    trackColor: Color,
    preset: String,
    test: Int,
    onTest: (Int) -> Unit,
    onChange: (VelocityConfig) -> Unit,
) {
    val out = VelocityMath.output(config, curve, test.toFloat()).roundToInt()
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(buildLabel("Preset: ", preset), color = Vel.TextMuted, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("In ", color = Vel.TextMuted, fontSize = 11.sp)
                Text("$test", color = Vel.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(" → Out ", color = Vel.TextMuted, fontSize = 11.sp)
                Text("$out", color = brighten(trackColor), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(4.dp))
        CurveEditor(config, curve, trackColor, test, onChange, Modifier.fillMaxWidth().height(175.dp))
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0", color = Vel.TextMuted, fontSize = 9.sp)
            Text("Input velocity", color = Vel.TextMuted, fontSize = 9.sp)
            Text("127", color = Vel.TextMuted, fontSize = 9.sp)
        }
        Spacer(Modifier.height(3.dp))
        // Slider de test en haut
        FaderSlider(test, 1..127, trackColor, true) { onTest(it) }
        // Titre et valeur plus discrets en dessous
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp).padding(top = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Test input", color = Vel.TextSoft, fontSize = 10.sp)
            Text("$test", color = Vel.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun buildLabel(prefix: String, value: String) = androidx.compose.ui.text.buildAnnotatedString {
    append(prefix)
    pushStyle(androidx.compose.ui.text.SpanStyle(color = Vel.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 11.sp))
    append(value)
    pop()
}

@Composable
private fun SettingsCard(c: VelocityConfig, track: Color, onChange: (VelocityConfig) -> Unit) {
    val on = !c.fixedEnabled
    Card(Modifier.alpha(if (on) 1f else 0.4f)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledSlider("Floor", "${c.floor}", c.floor, 0..126, track, on, Modifier.weight(1f)) {
                onChange(c.copy(floor = it, ceiling = maxOf(c.ceiling, it + 1)))
            }
            LabeledSlider("Ceiling", "${c.ceiling}", c.ceiling, 1..127, track, on, Modifier.weight(1f)) {
                onChange(c.copy(ceiling = it, floor = minOf(c.floor, it - 1)))
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledSlider("Offset", (if (c.offset > 0) "+" else "") + c.offset, c.offset, -40..40, track, on, Modifier.weight(1f)) {
                onChange(c.copy(offset = it))
            }
            LabeledSlider("Humanize", "±${c.humanize}", c.humanize, 0..20, track, on, Modifier.weight(1f)) {
                onChange(c.copy(humanize = it))
            }
        }
    }
}

@Composable
private fun TogglesCard(c: VelocityConfig, track: Color, onChange: (VelocityConfig) -> Unit) {
    Card {
        SwitchRow("Fixed velocity", "Exact value, bypasses all", c.fixedEnabled, track) {
            onChange(c.copy(fixedEnabled = it))
        }
        if (c.fixedEnabled) {
            Spacer(Modifier.height(2.dp))
            LabeledSlider("Exact value", "${c.fixedValue}", c.fixedValue, 1..127, track, true) {
                onChange(c.copy(fixedValue = it))
            }
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.alpha(if (c.fixedEnabled) 0.4f else 1f)) {
            SwitchRow("Invert response", "Soft touch gives loud notes", c.invert, track, enabled = !c.fixedEnabled) {
                onChange(c.copy(invert = it))
            }
        }
    }
}

// ═══════════════════════════ 5. ÉDITEUR DE COURBE ═══════════════════════════

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
    var dragging by remember { mutableStateOf(-1) }
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
                val pad = 16.dp.toPx()
                val hit = 28.dp.toPx()
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
                                raw.coerceIn(nodes[i - 1].x + 8, nodes[i + 1].x - 8)
                            }
                            change(cfg.copy(nodes = nodes.toMutableList().also { it[i] = CurveNode(nx, ny) }))
                        }
                    },
                    onDragEnd = { dragging = -1 },
                    onDragCancel = { dragging = -1 },
                )
            }
    ) {
        val pad = 16.dp.toPx()
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

        // grille + diagonale de référence
        for (i in 0..4) {
            drawLine(Vel.Grid, Offset(r.left + r.width * i / 4f, r.top), Offset(r.left + r.width * i / 4f, r.bottom), 1.dp.toPx())
            drawLine(Vel.Grid, Offset(r.left, r.top + r.height * i / 4f), Offset(r.right, r.top + r.height * i / 4f), 1.dp.toPx())
        }
        drawLine(Vel.Diagonal, Offset(r.left, r.bottom), Offset(r.right, r.top), 1.dp.toPx(), pathEffect = dash)

        // remplissage sous la courbe
        val eff = pathOf(out)
        val fill = Path().apply { addPath(eff); lineTo(px(127f), r.bottom); lineTo(px(0f), r.bottom); close() }
        drawPath(fill, Brush.verticalGradient(listOf(trackColor.copy(alpha = 0.30f), Color.Transparent), startY = r.top, endY = r.bottom))

        // bande Humanize
        if (config.humanize > 0 && !config.fixedEnabled) {
            val h = config.humanize.toFloat()
            val band = Path()
            for (i in 0..32) { val x = i * 127f / 32; val y = (out(x) + h).coerceAtMost(127f); if (i == 0) band.moveTo(px(x), py(y)) else band.lineTo(px(x), py(y)) }
            for (i in 32 downTo 0) { val x = i * 127f / 32; band.lineTo(px(x), py((out(x) - h).coerceAtLeast(0f))) }
            band.close()
            drawPath(band, trackColor.copy(alpha = 0.14f))
        }

        // bornes floor / ceiling
        if (!config.fixedEnabled) {
            if (config.floor > 0) drawLine(trackColor.copy(alpha = 0.35f), Offset(r.left, py(config.floor.toFloat())), Offset(r.right, py(config.floor.toFloat())), 1.dp.toPx(), pathEffect = dash)
            if (config.ceiling < 127) drawLine(trackColor.copy(alpha = 0.35f), Offset(r.left, py(config.ceiling.toFloat())), Offset(r.right, py(config.ceiling.toFloat())), 1.dp.toPx(), pathEffect = dash)
        }

        // courbe de base en pointillés si un réglage la modifie
        val modified = !config.fixedEnabled && (config.invert || config.offset != 0 || config.floor > 0 || config.ceiling < 127)
        if (modified) drawPath(pathOf({ x -> curve.at(x) }), Vel.RawCurve, style = Stroke(1.2.dp.toPx(), pathEffect = dash))

        // courbe effective : halo + trait
        drawPath(eff, bright.copy(alpha = 0.28f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(eff, bright, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // repère de test
        val ty = out(test.toFloat()).coerceIn(0f, 127f)
        val guide = Vel.BlueA.copy(alpha = 0.6f)
        drawLine(guide, Offset(px(test.toFloat()), r.bottom), Offset(px(test.toFloat()), py(ty)), 1.dp.toPx(), pathEffect = dash)
        drawLine(guide, Offset(r.left, py(ty)), Offset(px(test.toFloat()), py(ty)), 1.dp.toPx(), pathEffect = dash)
        drawCircle(Vel.BlueA, 4.5.dp.toPx(), Offset(px(test.toFloat()), py(ty)))

        // poignées
        if (editable) {
            config.nodes.forEachIndexed { i, nd ->
                val c = Offset(px(nd.x.toFloat()), py(nd.y.toFloat()))
                val active = dragging == i
                drawCircle(trackColor.copy(alpha = if (active) 0.28f else 0.14f), (if (active) 12 else 10).dp.toPx(), c)
                drawCircle(Color(0xFF4A5066), 6.5.dp.toPx(), c)
                drawCircle(Vel.TextPrimary, 6.5.dp.toPx(), c, style = Stroke(1.6.dp.toPx()))
                drawCircle(trackColor, 2.dp.toPx(), c)
            }
        }
    }
}

// ═══════════════════════════ 6. COMPOSANTS ═══════════════════════════

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .velSurface(RoundedCornerShape(11.dp), Vel.CardBrush, Vel.CardBorder)
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) { content() }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Vel.TextMuted, fontSize = 11.sp, letterSpacing = 0.5.sp, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
}

@Composable
private fun PresetGrid(current: String, columns: Int, onPick: (List<CurveNode>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        VelocityPresets.all.chunked(columns).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEach { (name, nodes) ->
                    Chip(name, current == name, Modifier.weight(1f)) { onPick(nodes) }
                }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(30.dp)
            .velClick(onClick)
            .velSurface(RoundedCornerShape(8.dp), if (selected) Vel.BlueBrush else Vel.KeyBrush, if (selected) Vel.BlueBorder else Vel.KeyBorder),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = if (selected) Vel.OnBlue else Color(0xFFC9CFDD),
            fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 3.dp),
        )
    }
}

@Composable
private fun ActionButton(label: String, modifier: Modifier, blue: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .height(42.dp)
            .velClick(onClick)
            .velSurface(RoundedCornerShape(10.dp), if (blue) Vel.BlueBrush else Vel.ActionBrush, if (blue) Vel.BlueBorder else Vel.ActionBorder),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (blue) Vel.OnBlue else Vel.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun KeyButton(icon: VIcon, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).velClick(onClick).velSurface(RoundedCornerShape(9.dp), Vel.KeyBrush, Vel.KeyBorder),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val s = size.width / 24f
            val sw = 2f * s
            val c = Color(0xFFC9CFDD)
            fun o(x: Float, y: Float) = Offset(x * s, y * s)
            fun seg(a: Offset, b: Offset) = drawLine(c, a, b, sw, StrokeCap.Round)
            when (icon) {
                VIcon.Close -> { seg(o(6f, 6f), o(18f, 18f)); seg(o(18f, 6f), o(6f, 18f)) }
                VIcon.Back -> { seg(o(19f, 12f), o(5f, 12f)); seg(o(11f, 6f), o(5f, 12f)); seg(o(5f, 12f), o(11f, 18f)) }
            }
        }
    }
}

private enum class VIcon { Back, Close }

@Composable
private fun LabeledSlider(
    label: String, valueText: String, value: Int, range: IntRange, track: Color, enabled: Boolean,
    modifier: Modifier = Modifier, onChange: (Int) -> Unit,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Vel.TextSoft, fontSize = 12.sp)
            Text(valueText, color = Vel.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        FaderSlider(value, range, track, enabled, onChange)
    }
}

/** Curseur au style du fader : rail sombre, remplissage + halo de la couleur de piste, poignée en relief. */
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
            .height(26.dp)
            .pointerInput(range, enabled) {
                if (enabled) detectTapGestures { o -> onC(valueAt(o.x, size.width.toFloat(), 10.dp.toPx())) }
            }
            .pointerInput(range, enabled) {
                if (enabled) detectHorizontalDragGestures { ch, _ ->
                    ch.consume(); onC(valueAt(ch.position.x, size.width.toFloat(), 10.dp.toPx()))
                }
            }
    ) {
        val m = 10.dp.toPx()
        val frac = (value - range.first) / (range.last - range.first).toFloat()
        val cx = m + frac * (size.width - 2 * m)
        val cy = size.height / 2f
        val th = 6.dp.toPx()
        val rr = CornerRadius(th / 2f)
        drawRoundRect(Vel.Well, Offset(0f, cy - th / 2), Size(size.width, th), rr)
        drawRoundRect(Vel.WellBorder, Offset(0f, cy - th / 2), Size(size.width, th), rr, style = Stroke(1.dp.toPx()))
        drawRoundRect(track.copy(alpha = 0.25f), Offset(0f, cy - th), Size(cx, th * 2), CornerRadius(th))
        drawRoundRect(track, Offset(0f, cy - th / 2), Size(cx, th), rr)
        val tw = 20.dp.toPx(); val thh = 22.dp.toPx(); val tr = CornerRadius(6.dp.toPx())
        drawRoundRect(Color.Black.copy(alpha = 0.4f), Offset(cx - tw / 2, cy - thh / 2 + 2.dp.toPx()), Size(tw, thh), tr)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF6A7089), Color(0xFF4A5066)), startY = cy - thh / 2, endY = cy + thh / 2),
            Offset(cx - tw / 2, cy - thh / 2), Size(tw, thh), tr,
        )
        drawRoundRect(Color(0xFF7A8199), Offset(cx - tw / 2, cy - thh / 2), Size(tw, thh), tr, style = Stroke(1.dp.toPx()))
        drawLine(track, Offset(cx - 5.dp.toPx(), cy), Offset(cx + 5.dp.toPx(), cy), 2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, track: Color, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Vel.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(subtitle, color = Vel.TextMuted, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1)
        }
        LedSwitch(checked, track) { if (enabled) onChange(it) }
    }
}

@Composable
private fun LedSwitch(checked: Boolean, track: Color, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (checked) 18.dp else 2.dp, label = "thumb")
    val shape = RoundedCornerShape(11.dp)
    Box(
        Modifier
            .size(38.dp, 22.dp)
            .velClick { onChange(!checked) }
            .drawBehind {
                if (checked) {
                    val r = 22.dp.toPx()
                    drawCircle(Brush.radialGradient(listOf(track.copy(alpha = 0.40f), Color.Transparent), center = center, radius = r), r, center)
                }
            }
            .clip(shape)
            .background(if (checked) lerp(Vel.Bg, track, 0.70f) else Vel.Well)
            .border(1.dp, if (checked) track else Vel.WellBorder, shape)
    ) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(
                    if (checked) Brush.verticalGradient(listOf(Color.White, Color(0xFFDFE3EE)))
                    else Brush.verticalGradient(listOf(Color(0xFF6A7089), Color(0xFF4A5066)))
                )
        )
    }
}

// ═══════════════════════════ 7. MODIFIERS ═══════════════════════════

private fun Modifier.velSurface(shape: Shape, brush: Brush, border: Color): Modifier =
    this.clip(shape)
        .background(brush)
        .border(1.dp, border, shape)
        .drawWithContent {
            drawContent()
            drawLine(
                Color.White.copy(alpha = 0.07f),
                Offset(10.dp.toPx(), 1.dp.toPx()), Offset(size.width - 10.dp.toPx(), 1.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }

@Composable
private fun Modifier.velClick(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

private fun brighten(c: Color): Color = lerp(c, Color.White, 0.15f)

// ═══════════════════════════ 8. PREVIEWS ═══════════════════════════

@Preview(widthDp = 760, heightDp = 380, showBackground = true, backgroundColor = 0xFF151924)
@Composable
private fun VelocityWide() {
    var cfg by remember { mutableStateOf(VelocityConfig(nodes = VelocityPresets.Soft)) }
    VelocitySettingsPage(1, "Default GM", Color(0xFFD1812C), cfg, { cfg = it }, {}, {})
}

@Preview(widthDp = 360, heightDp = 760, showBackground = true, backgroundColor = 0xFF151924)
@Composable
private fun VelocityNarrow() {
    var cfg by remember { mutableStateOf(VelocityConfig()) }
    VelocitySettingsPage(2, "Motif XF", Color(0xFF38A679), cfg, { cfg = it }, {}, {})
}
