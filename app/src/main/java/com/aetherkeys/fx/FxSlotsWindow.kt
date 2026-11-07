package com.aetherkeys.fx

/*
 * FX SLOTS WINDOW — one window per track, same look and behaviour family as LiveLooper and DrumPad
 * (relief tiles, spring press, opaque surfaces under shadows, in-window overlays, UNDO instead of confirmations).
 *
 * STRUCTURE
 *   1 MIDI slot + 3 AUDIO slots. A slot is either an effect card or "+ Add FX".
 *   Every effect card shows a small LIVE preview of what it does (steps, rings, taps, waves, rotor, curve...), a summary,
 *   a power button and a remove button. Removing slides the card out and shows an UNDO bar.
 *
 * BEHAVIOUR
 *   - Tap "+ Add FX": the catalog opens IN THE SAME WINDOW and only lists what fits the slot (MIDI effects for the MIDI slot,
 *     audio effects for an audio slot). Picking one on an occupied MIDI slot replaces it.
 *   - Tap a card: the window morphs to the size that effect needs (FxKind.editorW x editorH, clamped to the screen) and shows
 *     its editor. Header: back, title, power. Footer: Discard Changes / Confirm. Back = Discard.
 *   - The editors are NOT in this file: pass them in `editor` (FxEditorScreen, the Space panel, the Delay panel ...).
 *     The shell only gives them the whole body area and calls onEditorConfirm / onEditorDiscard.
 *   - Not a full screen: the window never covers more than the screen minus a 24 dp margin, the scrim closes it.
 */

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/* ═══════════════════════════ MODEL ═══════════════════════════ */

enum class FxKind(val title: String, val midi: Boolean, val desc: String, val color: Color, val editorW: Int, val editorH: Int) {
    Arp("Arpeggiator", true, "Turns held notes into a pattern", Color(0xFF8FB2E8), 840, 372),
    Octave("Octave Up", true, "Adds an octave above every note", Color(0xFF9A86C2), 440, 250),
    Easy("Easy Chord", true, "One note plays a programmed chord", Color(0xFFC27A8E), 780, 340),
    Seq("Sequencer", true, "One note plays a sequence", Color(0xFF6FB3A8), 840, 372),
    Chords("Chord Sequencer", true, "Plays a chord progression", Color(0xFFC9A15C), 840, 384),
    Delay("Delay", false, "Echoes, tape and ping-pong", Color(0xFF7CC3FF), 620, 300),
    Reverb("Space", false, "Halls, plates, clouds — the app's reverb", Color(0xFFFF6FB8), 860, 392),
    Chorus("Chorus", false, "Thickens and widens", Color(0xFFB28CFF), 620, 300),
    Tremolo("Tremolo", false, "Pulsing amplitude", Color(0xFFFFC56B), 580, 290),
    Rotor("Rotor", false, "Rotating horn and drum", Color(0xFFE6C07A), 660, 312),
    Overdrive("Overdrive", false, "Warm tube to hard clipping", Color(0xFFFF8F6B), 620, 300)
}

/**
 * CE QUI EXISTE VRAIMENT, et rien d'autre.
 *
 * Les onze entrées de [FxKind] restent définies : la maquette les dessine toutes
 * et les aperçus animés en ont besoin. Mais le CATALOGUE n'en propose que deux —
 * celles qui sonnent aujourd'hui : l'Arpeggiator (MIDI) et Æther Space (audio).
 * Proposer un effet qu'on ne sait pas faire serait un mensonge.
 */
private val CATALOG: List<FxKind> = listOf(FxKind.Arp, FxKind.Reverb)

data class SlotRef(val midi: Boolean, val index: Int = 0)
data class FxSlot(val id: Long, val kind: FxKind, val on: Boolean = true, val summary: String = "")
data class FxChain(val midi: FxSlot? = null, val audio: List<FxSlot?> = listOf(null, null, null)) {
    operator fun get(r: SlotRef): FxSlot? = if (r.midi) midi else audio.getOrNull(r.index)
    fun with(r: SlotRef, s: FxSlot?): FxChain = if (r.midi) copy(midi = s) else copy(audio = audio.mapIndexed { i, x -> if (i == r.index) s else x })
}

sealed interface FxView {
    object Slots : FxView
    data class Catalog(val target: SlotRef) : FxView
    data class Editor(val ref: SlotRef) : FxView
}

data class FxWindowState(val chain: FxChain = FxChain(), val view: FxView = FxView.Slots, val removed: Pair<SlotRef, FxSlot>? = null)

class FxSlotsController(
    initial: FxChain = FxChain(),
    private val onChain: (FxChain) -> Unit = {},
    private val onEditorOpen: (SlotRef, FxKind) -> Unit = { _, _ -> },
    private val onEditorConfirm: (SlotRef, FxKind) -> Unit = { _, _ -> },
    private val onEditorDiscard: (SlotRef, FxKind) -> Unit = { _, _ -> },
    private val summaryOf: (FxKind) -> String = { it.desc }   // e.g. "Dark Hall · Mix 38 %" from the real effect state
) {
    private val _state = MutableStateFlow(FxWindowState(chain = initial))
    val state: StateFlow<FxWindowState> = _state.asStateFlow()
    private fun chain(f: (FxChain) -> FxChain) { _state.update { it.copy(chain = f(it.chain)) }; onChain(_state.value.chain) }

    /**
     * Pose une chaîne SANS repasser par le moteur. Sert à l'adoption de ce que
     * le moteur porte déjà : rejouer `onChain` réinstallerait chaque effet et
     * écraserait les réglages qu'on vient justement de lire.
     */
    fun adopt(c: FxChain) = _state.update { it.copy(chain = c) }

    fun openCatalog(r: SlotRef) = _state.update { it.copy(view = FxView.Catalog(r)) }
    fun openEditor(r: SlotRef) {
        _state.value.chain[r]?.let { onEditorOpen(r, it.kind) }
        _state.update { it.copy(view = FxView.Editor(r)) }
    }
    fun back() { val v = _state.value.view; if (v is FxView.Editor) _state.value.chain[v.ref]?.let { onEditorDiscard(v.ref, it.kind) }; _state.update { it.copy(view = FxView.Slots) } }
    fun confirm() { val v = _state.value.view; if (v is FxView.Editor) _state.value.chain[v.ref]?.let { onEditorConfirm(v.ref, it.kind) }; _state.update { it.copy(view = FxView.Slots) } }
    fun pick(r: SlotRef, k: FxKind) {
        chain { it.with(r, FxSlot(System.nanoTime(), k, true, summaryOf(k))) }
        _state.update { it.copy(view = FxView.Slots) }
    }
    fun toggle(r: SlotRef) = chain { c -> c[r]?.let { c.with(r, it.copy(on = !it.on)) } ?: c }
    fun remove(r: SlotRef) { val s = _state.value.chain[r] ?: return; chain { it.with(r, null) }; _state.update { it.copy(removed = r to s) } }
    fun undoRemove() { val (r, s) = _state.value.removed ?: return; chain { it.with(r, s) }; _state.update { it.copy(removed = null) } }
    fun clearRemoved() = _state.update { it.copy(removed = null) }
    fun setSummary(r: SlotRef, s: String) = chain { c -> c[r]?.let { c.with(r, it.copy(summary = s)) } ?: c }
}

/* ═══════════════════════════ STYLE (same family as LiveLooper / DrumPad) ═══════════════════════════ */

private val Line = Color.White.copy(alpha = 0.07f)
private val TileTop = Color(0xFF6B75A8); private val TileBottom = Color(0xFF505A8C)
private val WindowBg = Brush.verticalGradient(listOf(Color(0xFF232B3D), Color(0xFF161B28)))
private val CardBg = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.03f)))
private val Dim = Color(0xFF7C86A8); private val Ink = Color(0xFFE9EDF5); private val Pale1 = Color(0xFFC7D9F9); private val Pale2 = Color(0xFF9DB9EF)
private fun hash(n: Float) = abs(sin(n * 127.1f) * 43758.5453f) % 1f

@Composable
private fun Modifier.pressable(enabled: Boolean = true, onLongPress: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.55f, 600f), label = "press")
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongPress)
    return this.scale(s).pointerInput(enabled) {
        if (enabled) detectTapGestures(
            onPress = { pressed = true; tryAwaitRelease(); pressed = false },
            onTap = { click() },
            onLongPress = long?.let { { it() } },
        )
    }
}

@Composable
private fun Power(on: Boolean, size: Dp = 32.dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(size).clip(shape)
            .background(if (on) Brush.verticalGradient(listOf(Pale1, Pale2)) else SolidColor(Color(0xFF232842)), shape)
            .border(1.dp, if (on) Color.White.copy(alpha = 0.35f) else Line, shape).pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {   // power glyph drawn, no resource needed
        Canvas(Modifier.size(size * 0.5f)) {
            val c = if (on) Color(0xFF1B2440) else Color(0xFF5B6590); val u = this.size.width / 24f
            drawLine(c, Offset(12f * u, 3f * u), Offset(12f * u, 11f * u), 2.4f * u, StrokeCap.Round)
            drawArc(c, -50f, 280f, false, Offset(5f * u, 5f * u), Size(14f * u, 14f * u), style = Stroke(2.4f * u, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit, width: Dp? = null, content: @Composable () -> Unit = {}) {
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.height(34.dp).then(if (width != null) Modifier.width(width) else Modifier).clip(shape).background(Color(0xFF252B45)).border(1.dp, Line, shape).pressable(onClick = onClick).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        if (label.isNotEmpty()) Text(label, color = Color(0xFFD5DCF2), fontSize = 12.5.sp, fontWeight = FontWeight.Bold) else content()
    }
}

/* ═══════════════════════════ LIVE MINI PREVIEWS ═══════════════════════════ */

/** What the effect does, in about 100 × 50 dp. t = seconds; frozen (t = 0) and dimmed when the slot is off. */
private fun DrawScope.drawPreview(kind: FxKind, t: Float) {
    val w = size.width; val h = size.height; val c = kind.color; val cx = w / 2; val cy = h / 2
    when (kind) {
        FxKind.Arp -> {
            // La GRILLE DE PAS de l'écran : des colonnes JOINTIVES sur toute la
            // largeur, la hauteur suivant la vélocité du pas. Seize barres fines
            // laissaient de grands vides — on double le nombre de pas et on ne
            // garde qu'un filet entre eux, pour que ça se lise comme une nappe.
            val n = if (w >= 120f) 32 else 16
            val step = w / n
            val bw = step * 0.9f
            val vel = floatArrayOf(1f, .58f, .74f, .58f, .88f, .52f, .68f, .52f)
            for (i in 0 until n) {
                val x = (i + 0.5f) * step
                val hh = h * vel[i % vel.size]
                val hot = ((t * 8f).toInt() % n) == i
                drawRoundRect(c.copy(alpha = 0.12f), Offset(x - bw / 2, 0f), Size(bw, h), CornerRadius(bw * 0.28f))
                drawRoundRect(c.copy(alpha = if (hot) 1f else 0.55f), Offset(x - bw / 2, h - hh), Size(bw, hh), CornerRadius(bw * 0.28f))
            }
        }
        FxKind.Octave -> { val p = sin(t * 2f) * 0.5f + 0.5f; drawCircle(c, h * 0.12f, Offset(cx, h * 0.74f)); drawLine(c.copy(alpha = 0.3f), Offset(cx, h * 0.62f), Offset(cx, h * 0.4f), 2f); drawCircle(c.copy(alpha = 0.35f + 0.65f * p), h * 0.12f, Offset(cx, h * 0.28f)) }
        FxKind.Easy -> for (k in 0 until 4) for (n in 0 until 3) { val hot = ((t * 1.6f).toInt() % 4) == k; drawCircle(c.copy(alpha = if (hot) 1f else 0.35f), h * 0.085f, Offset(w * (0.2f + 0.2f * k), h * (0.26f + 0.24f * n))) }
        FxKind.Seq -> for (i in 0 until 8) for (j in 0 until 3) { val on = hash(i * 3f + j) > 0.45f; val hot = ((t * 5f).toInt() % 8) == i; drawRoundRect(c.copy(alpha = if (on) (if (hot) 1f else 0.55f) else 0.12f), Offset(w * i / 8f + 2f, h * j / 3f + 2f), Size(w / 8f - 4f, h / 3f - 4f), CornerRadius(3f)) }
        FxKind.Chords -> { val widths = floatArrayOf(0.3f, 0.2f, 0.3f, 0.2f); var x = 0f; for (i in 0..3) { drawRoundRect(c.copy(alpha = 0.3f + 0.12f * i), Offset(x * w + 2f, h * 0.2f), Size(widths[i] * w - 4f, h * 0.6f), CornerRadius(6f)); x += widths[i] }; val px = ((t * 0.25f) % 1f) * w; drawLine(Color.White.copy(alpha = 0.7f), Offset(px, h * 0.1f), Offset(px, h * 0.9f), 2f) }
        FxKind.Reverb -> { for (i in 0 until 4) { val a = ((t * 0.5f + i * 0.25f) % 1f); drawCircle(c.copy(alpha = (1f - a) * 0.8f), h * (0.12f + a * 0.46f), Offset(cx, cy), style = Stroke(2.4f)) }; drawCircle(c, h * 0.1f, Offset(cx, cy)) }
        FxKind.Delay -> for (n in 0 until 7) { val a = Math.pow(0.72, n.toDouble()).toFloat(); val hot = ((t * 4f).toInt() % 7) == n; drawRoundRect(c.copy(alpha = if (hot) 1f else 0.5f), Offset(w * (0.08f + 0.13f * n), cy - h * 0.42f * a), Size(w * 0.07f, h * 0.84f * a), CornerRadius(4f)) }
        FxKind.Chorus -> for (i in 0 until 3) { val p = Path(); for (x in 0..40) { val px = x / 40f * w; val y = cy + sin(x * 0.4f + t * 2.4f + i * 2.1f) * h * 0.2f + (i - 1) * h * 0.1f; if (x == 0) p.moveTo(px, y) else p.lineTo(px, y) }; drawPath(p, c.copy(alpha = 0.9f - 0.2f * i), style = Stroke(2.4f, cap = StrokeCap.Round)) }
        FxKind.Tremolo -> { val p = Path(); for (x in 0..60) { val px = x / 60f * w; val env = 0.35f + 0.65f * (sin(x * 0.18f - t * 4f) * 0.5f + 0.5f); val y = cy + sin(x * 0.9f) * h * 0.42f * env; if (x == 0) p.moveTo(px, y) else p.lineTo(px, y) }; drawPath(p, c, style = Stroke(2.2f, cap = StrokeCap.Round)) }
        FxKind.Rotor -> { drawOval(Color.White.copy(alpha = 0.12f), Offset(cx - w * 0.3f, cy - h * 0.4f), Size(w * 0.6f, h * 0.8f), style = Stroke(1.5f)); rotate(t * 280f, Offset(cx, cy)) { drawLine(c, Offset(cx - w * 0.26f, cy), Offset(cx + w * 0.26f, cy), 5f, StrokeCap.Round) }; drawCircle(c, 4f, Offset(cx, cy)) }
        FxKind.Overdrive -> { val p = Path(); for (i in 0..30) { val x = -1f + i / 15f; val y = (Math.tanh((x * 5f).toDouble())).toFloat(); val px = cx + x * w * 0.22f - w * 0.22f; if (i == 0) p.moveTo(px, cy - y * h * 0.42f) else p.lineTo(px, cy - y * h * 0.42f) }; drawPath(p, c, style = Stroke(2.6f, cap = StrokeCap.Round)); val q = Path(); for (x in 0..40) { val s = sin(x * 0.4f - t * 5f); val y = Math.tanh((s * 4f).toDouble()).toFloat(); val px = w * 0.52f + x / 40f * w * 0.44f; if (x == 0) q.moveTo(px, cy - y * h * 0.38f) else q.lineTo(px, cy - y * h * 0.38f) }; drawPath(q, c.copy(alpha = 0.8f), style = Stroke(2f)) }
    }
}

@Composable
private fun Preview(kind: FxKind, t: Float, live: Boolean, modifier: Modifier) =
    Canvas(modifier.alpha(if (live) 1f else 0.3f)) { drawPreview(kind, if (live) t else 0f) }

@Composable
private fun rememberFxTime(): Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) withInfiniteAnimationFrameNanos { t = it / 1e9f } }
    return t
}

/* ═══════════════════════════ SLOT CARDS ═══════════════════════════ */

@Composable
private fun SlotCard(
    slot: FxSlot?, hint: String, t: Float, big: Boolean, modifier: Modifier,
    onAdd: () -> Unit, onOpen: () -> Unit, onToggle: () -> Unit, onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    if (slot == null) {
        Box(
            modifier.drawBehind { drawRoundRect(Color.White.copy(alpha = 0.16f), cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f)))) }.clip(shape).pressable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", color = Pale2, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Text("Add FX", color = Pale2, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text(hint, color = Dim, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        return
    }
    var leaving by remember { mutableStateOf(false) }
    val a by animateFloatAsState(if (leaving) 0f else 1f, tween(200), label = "leave")
    LaunchedEffect(leaving) { if (leaving) { delay(210); onRemove(); leaving = false } }
    val card = modifier.graphicsLayer { alpha = a; translationX = -(1f - a) * 40.dp.toPx(); scaleX = 0.96f + 0.04f * a; scaleY = 0.96f + 0.04f * a }
        .shadow(5.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape)
        .background(Color(0xFF1B2231)).background(CardBg, shape)
        .border(1.dp, slot.kind.color.copy(alpha = if (slot.on) 0.4f else 0.12f), shape)

    if (big) {
        // MIDI : titre à gauche, juste à côté, un aperçu court, et le power à droite.
        // Le retrait se fait par APPUI LONG : la carte reste propre, sans corbeille.
        Row(
            card.pressable(onLongPress = { leaving = true }, onClick = onOpen).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(4.dp, 18.dp).background(slot.kind.color, RoundedCornerShape(2.dp)))
            Text(slot.kind.title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Preview(slot.kind, t, slot.on, Modifier.weight(1f).fillMaxHeight())
            Power(slot.on, 30.dp, onToggle)
        }
    } else {
        // AUDIO : rien qu'un grand titre au centre, dans le style et la couleur du
        // FX (comme SPACE). L'aperçu n'a pas sa place ici — la case audio est
        // étroite et doit rester libre pour en ajouter d'autres.
        Column(
            card.pressable(onLongPress = { leaving = true }, onClick = onOpen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) { Power(slot.on, 28.dp, onToggle) }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                if (slot.kind == FxKind.Reverb) SpaceCardTitle(slot.summary, Modifier.fillMaxWidth())
                else FxTitle(slot.kind.title, slot.kind.color, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Le titre d'une carte AUDIO. Pour Space, il parle la langue de SON interface —
 * mot pour mot `AeSpaceScreen` : « Space » dans la teinte du preset, puis le
 * STYLE du preset (pas son nom) dans la teinte suivante. La carte ne dit donc
 * ni le preset ni une approximation : elle dit la même chose que l'écran.
 *
 * Le corps reste celui des autres cartes (26 sp au plus) et CENTRÉ dans la
 * case : un titre à l'échelle de l'écran de l'effet (42 sp) débordait la case.
 */
@Composable
private fun SpaceCardTitle(preset: String, modifier: Modifier) {
    val pal = FxDesign.SPACE.palettes[preset] ?: FxDesign.SPACE.palettes.values.first()
    val ramp = pal.colors
    val style = spaceStyleFor(preset)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val chars = style.label.length + 5
        val size = min(26f, maxWidth.value / (chars * 0.62f)).coerceAtLeast(11f)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = ramp[0])) { append("Space") }
                withStyle(SpanStyle(color = ramp[2])) { append(style.label) }
            },
            fontFamily = FxFonts.ui, fontSize = size.sp, fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp, maxLines = 1, textAlign = TextAlign.Center,
        )
    }
}

/**
 * Le grand titre d'un effet, dans le style de SPACE : la police du titre, la
 * teinte du FX, l'interlettre serrée. Le mot reste entier — il rétrécit pour
 * tenir, il ne se coupe pas.
 */
@Composable
private fun FxTitle(text: String, color: Color, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val size = min(26f, maxWidth.value / (text.length * 0.62f)).coerceAtLeast(13f)
        Text(
            text, color = color, fontFamily = FxFonts.title, fontSize = size.sp,
            fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
            maxLines = 1, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SlotsView(chain: FxChain, t: Float, c: FxSlotsController) {
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Label("MIDI")
        SlotCard(chain.midi, "MIDI effect", t, true, Modifier.fillMaxWidth().height(78.dp), { c.openCatalog(SlotRef(true)) }, { c.openEditor(SlotRef(true)) }, { c.toggle(SlotRef(true)) }, { c.remove(SlotRef(true)) })
        Label("AUDIO")
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chain.audio.forEachIndexed { i, s ->
                val r = SlotRef(false, i)
                SlotCard(s, "Audio effect ${i + 1}", t, false, Modifier.weight(1f).fillMaxHeight(), { c.openCatalog(r) }, { c.openEditor(r) }, { c.toggle(r) }, { c.remove(r) })
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, color = Dim, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)

@Composable
private fun CatalogView(target: SlotRef, t: Float, c: FxSlotsController) {
    val kinds = CATALOG.filter { it.midi == target.midi }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (target.midi) "A track has one MIDI effect. Picking another one replaces it." else "Three audio slots, played in order.", color = Dim, fontSize = 11.sp, lineHeight = 16.sp)
        kinds.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { k ->
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        Modifier.weight(1f).height(66.dp).clip(shape).background(CardBg, shape).border(1.dp, Line, shape).pressable { c.pick(target, k) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Preview(k, t, true, Modifier.size(64.dp, 44.dp))
                        Column(Modifier.weight(1f)) { Text(k.title, color = Ink, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1); Text(k.desc, Modifier.padding(top = 2.dp), color = Dim, fontSize = 10.5.sp, maxLines = 2, lineHeight = 13.sp) }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/* ═══════════════════════════ THE WINDOW ═══════════════════════════ */

/**
 * @param editor the editor of an effect, given the whole body area (reuse FxEditorScreen's content, the Space panel, ...)
 * Place it at the root of the page; it draws its own scrim.
 */
@Composable
fun FxSlotsWindow(
    state: FxWindowState, controller: FxSlotsController, trackName: String, trackColor: Color, onClose: () -> Unit,
    editor: @Composable (FxKind, SlotRef) -> Unit, modifier: Modifier = Modifier
) {
    val t = rememberFxTime()
    val view = state.view
    val kind = (view as? FxView.Editor)?.let { state.chain[it.ref]?.kind }
    val (tw, th) = when (view) { FxView.Slots -> 640 to 320; is FxView.Catalog -> 560 to 300; is FxView.Editor -> (kind?.editorW ?: 640) to (kind?.editorH ?: 320) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w by animateDpAsState(min(tw.dp.value, maxWidth.value - 24f).dp, spring(0.8f, Spring.StiffnessMediumLow), label = "w")
        val h by animateDpAsState(min(th.dp.value, maxHeight.value - 24f).dp, spring(0.8f, Spring.StiffnessMediumLow), label = "h")
        Box(Modifier.fillMaxSize().background(Color(0xFF060912).copy(alpha = 0.62f)).clickable(remember { MutableInteractionSource() }, null, onClick = onClose))
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.align(Alignment.Center).size(w, h).shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape).background(WindowBg).border(1.dp, Line, shape)
                .clickable(remember { MutableInteractionSource() }, null) {}
        ) {
            // La vue d'un EFFET n'a pas d'en-tête : l'écran de l'effet occupe toute
            // la fenêtre (son propre en-tête serait un cadre de plus et un second
            // nom). Les vues de navigation — les emplacements et le catalogue —
            // gardent la barre, qui porte leur titre et la sortie.
            if (view !is FxView.Editor) {
                Row(
                    Modifier.fillMaxWidth().height(54.dp).drawBehind { drawLine(Line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (view is FxView.Catalog) SmallButton("‹", controller::back, 36.dp)
                    else Box(Modifier.size(4.dp, 26.dp).background(trackColor, RoundedCornerShape(2.dp)))
                    Text(
                        if (view is FxView.Catalog) "Add FX" else "$trackName · FX",
                        Modifier.weight(1f), color = Color(0xFFF1F4FA), fontSize = 16.sp, fontWeight = FontWeight.Black,
                        textAlign = if (view is FxView.Catalog) TextAlign.Center else TextAlign.Start,
                    )
                    SmallButton("✕", onClose, 36.dp)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(view, animationSpec = tween(220), label = "view") { v ->
                    when (v) {
                        FxView.Slots -> SlotsView(state.chain, t, controller)
                        is FxView.Catalog -> CatalogView(v.target, t, controller)
                        is FxView.Editor -> state.chain[v.ref]?.let { editor(it.kind, v.ref) }
                    }
                }
                state.removed?.let { (_, s) ->
                    LaunchedEffect(s.id) { delay(4200); controller.clearRemoved() }
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).shadow(8.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color(0xFF262C4A)).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("${s.kind.title} removed", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("UNDO", Modifier.clickable { controller.undoRemove() }, color = Pale2, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}
