package com.soundstage.mixer.fx // ← adapt to the real package

/*
 * FxModes — faithful to FxModesPreview.jsx (approved).
 *
 *  - One MIDI effect slot per track (Arpeggiator, Octave Up, Easy Chord, Sequencer, Chord Sequencer) and up to 3 audio effects.
 *  - One "Add FX" button; the catalog opens in the same window (categories inside). Picking a MIDI effect REPLACES the current one.
 *  - Cards with an ON/OFF power button on the right. Tap a MIDI card to edit it in a full-screen editor with
 *    Replace Effect, Presets, Help, Effect ON, Discard Changes and Confirm (Discard restores the snapshot taken on open).
 *  - The 16 steps are ALWAYS visible; the step count only enables the first N, the others stay there, dimmed.
 *  - Sequencer / Easy Chord notes are set by tapping a tile and then PLAYING A KEY on the app keyboard:
 *    the app calls FxController.onKeyPlayed(note). No keyboard widget inside the editor.
 *  - Swing is a GLOBAL setting (SwingChip in the mixer bar), shared by every MIDI effect. It is not in the FX window.
 *  - The detailed icons are SVG path data (24 × 24 grid, 2 px stroke, round caps) turned into ImageVectors.
 *
 * Layout of the file: model → pure logic → controller → icons → UI.
 */

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/* ═══════════════════════════ MODEL ═══════════════════════════ */

enum class MidiType(val title: String, val glyph: String, val desc: String) {
    Arp("Arpeggiator", "⤴", "Turns held notes into a pattern"),
    Octave("Octave Up", "⇧", "Adds an octave above every note"),
    Easy("Easy Chord", "♬", "One note plays a programmed chord"),
    Seq("Sequencer", "▤", "One note plays a sequence of notes"),
    Chords("Chord Sequencer", "♪", "Plays a chord progression")
}

enum class AudioType(val title: String) { Delay("Delay"), Reverb("Reverb"), Chorus("Chorus"), Tremolo("Tremolo"), Rotor("Rotor"), Overdrive("Overdrive") }
enum class ArpStyle(val label: String) { Up("Up"), Down("Down"), UpDown("Up-Down"), DownUp("Down-Up"), Random("Random"), Chord("Chord") }
enum class Scale { Major, Minor }
enum class Voicing(val label: String) { Close("Close"), Open("Open"), Drop2("Drop 2") }
enum class PlayStyle(val label: String) { Block("Block"), Strum("Strum"), Arp("Arp") }
enum class Quality(val label: String, val suffix: String, val intervals: List<Int>) {
    Maj("maj", "", listOf(0, 4, 7)), Min("min", "m", listOf(0, 3, 7)), Dom7("7", "7", listOf(0, 4, 7, 10)),
    Maj7("maj7", "maj7", listOf(0, 4, 7, 11)), Min7("m7", "m7", listOf(0, 3, 7, 10)), Sus2("sus2", "sus2", listOf(0, 2, 7)),
    Sus4("sus4", "sus4", listOf(0, 5, 7)), Dim("dim", "dim", listOf(0, 3, 6)), Add9("add9", "add9", listOf(0, 4, 7, 14))
}

/** Subdivision: label and steps per beat. */
val Subdivisions = listOf("1/4" to 1, "1/8" to 2, "1/16" to 4, "1/32" to 8)

data class NoteRow(val input: Int, val out: List<Int?>)
data class ChordSlot(val id: Int, val root: Int, val quality: Quality, val len: Int = 2, val inv: Int = 0, val voicing: Voicing = Voicing.Close, val oct: Int = 0)

sealed interface MidiParams
data class ArpParams(val sub: Int = 2, val style: ArpStyle = ArpStyle.Up, val steps: Int = 16, val vel: List<Float>, val on: List<Boolean> = List(16) { true }) : MidiParams
data class OctaveParams(val blend: Int = 60) : MidiParams
data class EasyParams(val rows: List<NoteRow>) : MidiParams
data class SeqParams(val sub: Int = 2, val vel: Int = 100, val steps: Int = 16, val rows: List<NoteRow>) : MidiParams
data class ChordParams(
    val keyPc: Int = 0, val scale: Scale = Scale.Major, val sevenths: Boolean = false, val style: PlayStyle = PlayStyle.Block,
    val sel: Int? = null, val chords: List<ChordSlot>
) : MidiParams

data class MidiEffect(val type: MidiType, val on: Boolean = true, val params: MidiParams)
data class AudioEffect(val id: Long, val type: AudioType, val on: Boolean = true)
data class TrackFx(val midi: MidiEffect? = null, val audio: List<AudioEffect> = emptyList())
data class TrackInfo(val id: Int, val name: String, val color: Color)

enum class WindowView { List, Picker }
data class EditingFx(val trackId: Int, val snapshot: MidiParams)
/** A tile waiting for a key: col == null means the input chip. */
data class ListenTarget(val row: Int, val col: Int?)
data class FxToast(val key: Long, val text: String)

data class FxUiState(
    val fx: Map<Int, TrackFx> = emptyMap(),
    val windowTrackId: Int? = null,
    val view: WindowView = WindowView.List,
    val editing: EditingFx? = null,
    val listen: ListenTarget? = null,
    val swing: Int = 54,                 // GLOBAL, shared by every MIDI effect (50..75)
    val toast: FxToast? = null
) {
    val editedEffect: MidiEffect? get() = editing?.let { fx[it.trackId]?.midi }
}

class FxActions(
    val onCloseWindow: () -> Unit = {},
    val onView: (WindowView) -> Unit = {},
    val onOpenEditor: (Int) -> Unit = {},
    val onToggleMidi: (Int, Boolean) -> Unit = { _, _ -> },
    val onRemoveMidi: (Int) -> Unit = {},
    val onToggleAudio: (Int, Long) -> Unit = { _, _ -> },
    val onRemoveAudio: (Int, Long) -> Unit = { _, _ -> },
    val onPickMidi: (Int, MidiType) -> Unit = { _, _ -> },
    val onPickAudio: (Int, AudioType) -> Unit = { _, _ -> },
    val onEdit: ((MidiParams) -> MidiParams) -> Unit = {},
    val onEffectOn: (Boolean) -> Unit = {},
    val onReplace: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onListen: (ListenTarget?) -> Unit = {},
    val onSwing: (Int) -> Unit = {},
    val onMessage: (String) -> Unit = {},
    val onToastDismiss: () -> Unit = {}
)

/** Typed edit helper: actions.edit<ArpParams> { it.copy(steps = 8) } */
inline fun <reified T : MidiParams> FxActions.edit(crossinline f: (T) -> T) = onEdit { p -> if (p is T) f(p) else p }

/* ═══════════════════════════ PURE LOGIC ═══════════════════════════ */

object FxLogic {
    private val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val major = listOf(0, 2, 4, 5, 7, 9, 11)
    private val minor = listOf(0, 2, 3, 5, 7, 8, 10)
    val romanMajor = listOf("I", "ii", "iii", "IV", "V", "vi", "vii°")
    val romanMinor = listOf("i", "ii°", "III", "iv", "v", "VI", "VII")
    const val MAX_AUDIO = 3
    const val MAX_SEQ_ROWS = 8

    fun pitchName(pc: Int) = names[((pc % 12) + 12) % 12]
    /** C3 = MIDI 48. */
    fun noteName(m: Int) = names[m % 12] + (m / 12 - 1)
    fun scaleIntervals(s: Scale) = if (s == Scale.Major) major else minor
    fun roman(s: Scale) = if (s == Scale.Major) romanMajor else romanMinor

    fun diaChord(keyPc: Int, scale: Scale, degree: Int, sevenths: Boolean): Pair<Int, Quality> {
        val base = if (scale == Scale.Major) listOf(Quality.Maj, Quality.Min, Quality.Min, Quality.Maj, Quality.Maj, Quality.Min, Quality.Dim)
        else listOf(Quality.Min, Quality.Dim, Quality.Maj, Quality.Min, Quality.Min, Quality.Maj, Quality.Maj)
        var q = base[degree]
        if (sevenths) q = when (q) { Quality.Maj -> if (degree == 4) Quality.Dom7 else Quality.Maj7; Quality.Min -> Quality.Min7; else -> q }
        return (keyPc + scaleIntervals(scale)[degree]) % 12 to q
    }

    fun chordNotes(c: ChordSlot): List<Int> {
        val n = c.quality.intervals.map { 48 + c.root + it + 12 * c.oct }.toMutableList()
        repeat(c.inv) { n.add(n.removeAt(0) + 12) }
        if (c.voicing == Voicing.Open && n.size > 2) n[1] += 12
        if (c.voicing == Voicing.Drop2 && n.size > 2) n[n.size - 2] -= 12
        return n.sorted()
    }

    fun chordName(c: ChordSlot): String = pitchName(c.root) + c.quality.suffix + if (c.inv > 0) "/" + pitchName(chordNotes(c)[0]) else ""
    fun degreeOf(p: ChordParams, c: ChordSlot) = scaleIntervals(p.scale).indexOfFirst { (p.keyPc + it) % 12 == c.root }

    fun mkMidi(type: MidiType): MidiEffect = MidiEffect(
        type, true,
        when (type) {
            MidiType.Arp -> ArpParams(vel = List(16) { if (it % 4 == 0) 1f else if (it % 2 == 0) 0.78f else 0.62f })
            MidiType.Octave -> OctaveParams()
            MidiType.Easy -> EasyParams(listOf(24 to listOf(24, 48, 55), 26 to listOf(26, 48, 53), 28 to listOf(28, 48, 55), 29 to listOf(29, 48, 53, 55), 31 to listOf(31, 47, 55)).map { NoteRow(it.first, it.second) })
            MidiType.Seq -> SeqParams(rows = listOf(24 to 55, 26 to 53, 28 to 55, 29 to 53).map { (i, top) -> NoteRow(i, List(16) { k -> if (k % 3 == 1 || k == 9 || k == 12 || k == 15) top else 48 }) })
            MidiType.Chords -> ChordParams(sel = 1, chords = listOf(0 to 2, 4 to 2, 5 to 2, 3 to 2).mapIndexed { i, (d, len) -> diaChord(0, Scale.Major, d, false).let { ChordSlot(i + 1, it.first, it.second, len) } })
        }
    )

    fun summary(m: MidiEffect): String = when (val p = m.params) {
        is ArpParams -> "${p.style.label} · ${Subdivisions[p.sub].first} · ${p.steps} steps"
        is OctaveParams -> "Blend ${p.blend}%"
        is EasyParams -> "${p.rows.size} chord${if (p.rows.size > 1) "s" else ""} programmed"
        is SeqParams -> "${p.rows.size} sequences · ${p.steps} steps · ${Subdivisions[p.sub].first}"
        is ChordParams -> "${p.chords.size} chords · ${pitchName(p.keyPc)} ${p.scale.name.lowercase()}"
    }

    /** Index of the step playing now (or -1). beatPos = position in beats. */
    fun currentStep(beatPos: Double?, subIndex: Int, steps: Int): Int =
        if (beatPos == null) -1 else floor(beatPos * Subdivisions[subIndex].second).toInt() % max(1, steps)

    /** Position in bars inside the 8-bar chord timeline (or -1). */
    fun chordBar(beatPos: Double?): Double = if (beatPos == null) -1.0 else (beatPos / 4.0) % 8.0

    fun withNote(p: MidiParams, row: Int, col: Int?, note: Int): MidiParams = when (p) {
        is SeqParams -> p.copy(rows = p.rows.mapIndexed { i, r -> if (i != row) r else if (col == null) r.copy(input = note) else r.copy(out = r.out.toMutableList().also { it[col] = note }) })
        is EasyParams -> p.copy(rows = p.rows.mapIndexed { i, r ->
            if (i != row) r else if (col == null) r.copy(input = note)
            else r.copy(out = r.out.toMutableList().also { while (it.size <= col) it.add(null); it[col] = note })
        })
        else -> p
    }
}

/* ═══════════════════════════ CONTROLLER ═══════════════════════════ */

/**
 * Holds the FX state of every track. onChanged is called with the new map whenever an effect is confirmed,
 * added, replaced, toggled or removed (persist it with the patch: confirming updates the existing patch snapshots).
 * Call onKeyPlayed(note) from the app keyboard: it fills the tile the user is listening on.
 */
class FxController(initial: Map<Int, TrackFx>, swing: Int = 54, private val onChanged: (Map<Int, TrackFx>) -> Unit = {}) {
    private val _state = MutableStateFlow(FxUiState(fx = initial, swing = swing))
    val state: StateFlow<FxUiState> = _state.asStateFlow()

    private fun cur() = _state.value
    private fun msg(t: String) = _state.update { it.copy(toast = FxToast(System.nanoTime(), t)) }
    private fun change(tid: Int, f: (TrackFx) -> TrackFx, persist: Boolean = true) {
        _state.update { it.copy(fx = it.fx + (tid to f(it.fx[tid] ?: TrackFx()))) }
        if (persist) onChanged(cur().fx)
    }

    fun openWindow(tid: Int) = _state.update { it.copy(windowTrackId = tid, view = WindowView.List) }
    fun closeWindow() = _state.update { it.copy(windowTrackId = null) }
    fun setView(v: WindowView) = _state.update { it.copy(view = v) }
    fun toggleMidi(tid: Int, on: Boolean) = change(tid, { t -> t.copy(midi = t.midi?.copy(on = on)) })
    fun removeMidi(tid: Int) = change(tid, { it.copy(midi = null) })
    fun toggleAudio(tid: Int, id: Long) = change(tid, { t -> t.copy(audio = t.audio.map { if (it.id == id) it.copy(on = !it.on) else it }) })
    fun removeAudio(tid: Int, id: Long) = change(tid, { t -> t.copy(audio = t.audio.filter { it.id != id }) })

    fun pickMidi(tid: Int, type: MidiType) {
        val had = cur().fx[tid]?.midi != null
        change(tid, { it.copy(midi = FxLogic.mkMidi(type)) })
        _state.update { it.copy(view = WindowView.List, editing = null) }
        msg(if (had) "Replaced by ${type.title}" else "${type.title} added")
    }
    fun pickAudio(tid: Int, type: AudioType) {
        if ((cur().fx[tid]?.audio?.size ?: 0) >= FxLogic.MAX_AUDIO) { msg("Up to ${FxLogic.MAX_AUDIO} audio effects"); return }
        change(tid, { it.copy(audio = it.audio + AudioEffect(System.nanoTime(), type)) })
        _state.update { it.copy(view = WindowView.List) }
        msg("${type.title} added")
    }

    fun openEditor(tid: Int) { val m = cur().fx[tid]?.midi ?: return; _state.update { it.copy(editing = EditingFx(tid, m.params), listen = null) } }
    fun edit(f: (MidiParams) -> MidiParams) {
        val e = cur().editing ?: return
        change(e.trackId, { t -> t.copy(midi = t.midi?.copy(params = f(t.midi.params))) }, persist = false)
    }
    fun setEffectOn(on: Boolean) { val e = cur().editing ?: return; change(e.trackId, { t -> t.copy(midi = t.midi?.copy(on = on)) }, persist = false) }
    fun replace() { _state.update { it.copy(editing = null, listen = null, view = WindowView.Picker) } }
    fun discard() {
        val e = cur().editing ?: return
        change(e.trackId, { t -> t.copy(midi = t.midi?.copy(params = e.snapshot)) }, persist = false)
        _state.update { it.copy(editing = null, listen = null) }
    }
    fun confirm() { onChanged(cur().fx); _state.update { it.copy(editing = null, listen = null) }; msg("Changes confirmed") }
    fun listen(t: ListenTarget?) = _state.update { it.copy(listen = t) }
    fun setSwing(v: Int) = _state.update { it.copy(swing = v.coerceIn(50, 75)) }

    /** Called by the app keyboard for every key the user plays. */
    fun onKeyPlayed(note: Int) {
        val s = cur(); val l = s.listen ?: return; val e = s.editing ?: return
        edit { FxLogic.withNote(it, l.row, l.col, note) }
        val m = s.editedEffect ?: return
        val limit = when (val p = m.params) { is SeqParams -> p.steps; is EasyParams -> 8; else -> 0 }
        if (l.col != null && l.col + 1 < limit) _state.update { it.copy(listen = l.copy(col = l.col + 1)) }
        if (e.trackId < 0) return
    }
    fun dismissToast() = _state.update { it.copy(toast = null) }

    fun actions() = FxActions(
        onCloseWindow = ::closeWindow, onView = ::setView, onOpenEditor = ::openEditor, onToggleMidi = ::toggleMidi, onRemoveMidi = ::removeMidi,
        onToggleAudio = ::toggleAudio, onRemoveAudio = ::removeAudio, onPickMidi = ::pickMidi, onPickAudio = ::pickAudio,
        onEdit = ::edit, onEffectOn = ::setEffectOn, onReplace = ::replace, onDiscard = ::discard, onConfirm = ::confirm,
        onListen = ::listen, onSwing = ::setSwing, onMessage = ::msg, onToastDismiss = ::dismissToast
    )
}

/* ═══════════════════════════ ICONS (SVG path data) ═══════════════════════════ */

private fun svg(name: String, vararg d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        d.forEach {
            addPath(addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
    }.build()

private const val CIRCLE = "M3,12 a9,9 0 1,0 18,0 a9,9 0 1,0 -18,0 Z"

private object FxIcons {
    val Power by lazy { svg("Power", "M12,3v8", "M7.2,6.8a7,7 0 1 0 9.6,0") }
    val Back by lazy { svg("Back", "M19,12H5", "M11,6l-6,6 6,6") }
    val Close by lazy { svg("Close", "M6,6l12,12", "M18,6L6,18") }
    val Plus by lazy { svg("Plus", "M12,5v14", "M5,12h14") }
    val Minus by lazy { svg("Minus", "M5,12h14") }
    val Trash by lazy { svg("Trash", "M4,7h16", "M9,7V4h6v3", "M6,7l1,13h10l1,-13", "M10,11v6", "M14,11v6") }
    val Help by lazy { svg("Help", CIRCLE, "M9.5,9.5a2.5,2.5 0 1 1 3.5,2.3c-0.7,0.4 -1,1 -1,1.7", "M12,17h0.01") }
    val Replace by lazy { svg("Replace", "M20,12a8,8 0 0 0 -14,-5.3L4,9", "M4,4v5h5", "M4,12a8,8 0 0 0 14,5.3L20,15", "M20,20v-5h-5") }
    val Down by lazy { svg("Down", "M6,9l6,6 6,-6") }
    val Left by lazy { svg("Left", "M15,6l-6,6 6,6") }
    val Right by lazy { svg("Right", "M9,6l6,6 -6,6") }
    val Go by lazy { svg("Go", CIRCLE, "M8,12h8", "M12,8l4,4 -4,4") }
    val Copy by lazy { svg("Copy", "M10,8h8a2,2 0 0 1 2,2v8a2,2 0 0 1 -2,2h-8a2,2 0 0 1 -2,-2v-8a2,2 0 0 1 2,-2Z", "M16,8V6a2,2 0 0 0 -2,-2H6a2,2 0 0 0 -2,2v8a2,2 0 0 0 2,2h2") }
    val StyleUp by lazy { svg("Up", "M3,19h4v-4h4v-4h4V7h4") }
    val StyleDown by lazy { svg("Down", "M3,5h4v4h4v4h4v4h4") }
    val StyleUpDown by lazy { svg("UpDown", "M3,18l4,-6 5,-6 5,6 4,6") }
    val StyleDownUp by lazy { svg("DownUp", "M3,6l4,6 5,6 5,-6 4,-6") }
    val StyleRandom by lazy { svg("Random", "M3,14l4,-6 4,8 4,-10 5,8") }
    val StyleChord by lazy { svg("Chord", "M12,4v3", "M12,10.5v3", "M12,17v3") }
    fun forStyle(s: ArpStyle) = when (s) { ArpStyle.Up -> StyleUp; ArpStyle.Down -> StyleDown; ArpStyle.UpDown -> StyleUpDown; ArpStyle.DownUp -> StyleDownUp; ArpStyle.Random -> StyleRandom; ArpStyle.Chord -> StyleChord }
}

@Composable
private fun FxIcon(icon: ImageVector, tint: Color, size: Dp = 16.dp) = Icon(icon, null, Modifier.size(size), tint)

/* ═══════════════════════════ STYLE ═══════════════════════════ */

private object FM {
    val In1 = Color(0xFFC7D9F9); val In2 = Color(0xFF9DB9EF); val Out1 = Color(0xFF6B75A8); val Out2 = Color(0xFF505A8C)
    val Empty = Color(0xFF1F2539); val Btn = Color(0xFF252B45); val Txt = Color(0xFFE7ECFB); val Dim = Color(0xFF7C86A8)
    val Org = Color(0xFFF2A33A); val Ink = Color(0xFF1B2440); val Dark = Color(0xFF2B3254); val Link = Color(0xFF6F85B5)
    val Mono = FontFamily.Monospace
}
private fun white(a: Float) = Color.White.copy(alpha = a)
private fun Color.a(x: Float) = copy(alpha = x)
private val Line = white(0.07f)
private val ScreenBg = Brush.verticalGradient(listOf(Color(0xFF131728), Color(0xFF1A1F33)))
private val WindowBg = Brush.verticalGradient(listOf(Color(0xFF1B2036), Color(0xFF161A2C)))

/** The subtle grain of the screenshots: a tiny noise bitmap repeated over the surface. */
private val GrainBrush: Brush by lazy {
    val bmp = androidx.compose.ui.graphics.ImageBitmap(64, 64)
    val c = androidx.compose.ui.graphics.Canvas(bmp)
    val p = androidx.compose.ui.graphics.Paint()
    val r = java.util.Random(7)
    repeat(520) { p.color = Color.White.copy(alpha = 0.2f + r.nextFloat() * 0.8f); val x = r.nextInt(63).toFloat(); val y = r.nextInt(63).toFloat(); c.drawRect(Rect(x, y, x + 1.2f, y + 1.2f), p) }
    ShaderBrush(ImageShader(bmp, TileMode.Repeated, TileMode.Repeated))
}
private fun Modifier.grain(alpha: Float = 0.05f): Modifier = drawBehind { drawRect(GrainBrush, alpha = alpha) }

private enum class TileKind { In, Out, Dark, Empty }

/** The relief tile of the screenshots: pale blue (input), steel blue (output), dark (bypassed), empty. */
private fun Modifier.tile(kind: TileKind, radius: Dp = 10.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    val fill: Brush = when (kind) {
        TileKind.In -> Brush.verticalGradient(listOf(FM.In1, FM.In2))
        TileKind.Out -> Brush.verticalGradient(listOf(FM.Out1, FM.Out2))
        TileKind.Dark -> SolidColor(FM.Dark)
        TileKind.Empty -> SolidColor(FM.Empty)
    }
    return (if (kind == TileKind.Empty) this else this.shadow(2.dp, shape, ambientColor = Color.Black, spotColor = Color.Black))
        .clip(shape).background(fill, shape)
        .border(1.dp, if (kind == TileKind.Empty) white(0.05f) else white(0.16f), shape)
        .drawBehind { if (kind != TileKind.Empty) drawLine(white(0.14f), Offset(radius.toPx(), 0.5f), Offset(size.width - radius.toPx(), 0.5f), 1f) }
}

@Composable
private fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.55f, 600f), label = "press")
    val click by rememberUpdatedState(onClick)
    return this.scale(sc).pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { click() })
    }
}

@Composable
private fun TileText(text: String, kind: TileKind, size: Float = 12f, modifier: Modifier = Modifier) {
    val c = when (kind) { TileKind.In -> FM.Ink; TileKind.Dark -> Color(0xFF8791B5); else -> FM.Txt }
    Text(text, modifier, color = c, fontSize = size.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
}

@Composable
private fun FxButton(modifier: Modifier = Modifier, height: Dp = 36.dp, width: Dp? = null, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier.height(height).then(if (width != null) Modifier.width(width) else Modifier.padding(0.dp)).clip(shape).background(FM.Btn).border(1.dp, Line, shape)
            .pressable(onClick = onClick).padding(horizontal = if (width != null) 0.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

@Composable
private fun PowerButton(on: Boolean, size: Dp = 38.dp, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        Modifier.size(size)
            .drawBehind { if (on) drawRoundRect(FM.In2.a(0.3f), Offset(-3.dp.toPx(), -3.dp.toPx()), Size(this.size.width + 6.dp.toPx(), this.size.height + 6.dp.toPx()), CornerRadius(14.dp.toPx())) }
            .clip(shape)
            .background(if (on) Brush.verticalGradient(listOf(FM.In1, FM.In2)) else SolidColor(Color(0xFF232842)), shape)
            .border(1.dp, if (on) white(0.35f) else Line, shape)
            .pressable { onChange(!on) },
        contentAlignment = Alignment.Center
    ) { FxIcon(FxIcons.Power, if (on) FM.Ink else Color(0xFF5B6590), size * 0.5f) }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit, width: Dp, height: Dp = 26.dp, fontSize: Float = 11.5f) {
    val idx = options.indexOfFirst { it.first == value }.coerceAtLeast(0)
    val shape = RoundedCornerShape(10.dp)
    BoxWithConstraints(Modifier.width(width).clip(shape).background(Color(0xFF161A2C)).border(1.dp, Line, shape).padding(2.dp)) {
        val segW = maxWidth / options.size
        val x by animateDpAsState(segW * idx, spring(0.65f, 420f), label = "seg")
        Box(Modifier.offset(x = x).width(segW).height(height).background(Color(0xFF5B6591), RoundedCornerShape(8.dp)))
        Row {
            options.forEach { (k, l) ->
                Box(Modifier.width(segW).height(height).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(k) }, contentAlignment = Alignment.Center) {
                    Text(l, color = if (k == value) Color.White else Color(0xFF8791B5), fontSize = fontSize.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

/** Knob with an arc, as in the screenshots. Drag vertically. */
@Composable
private fun Knob(value: Int, min: Int, max: Int, label: String, display: String, onChange: (Int) -> Unit, size: Dp = 46.dp) {
    val cur by rememberUpdatedState(value)
    var base = 0; var acc = 0f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(
            Modifier.size(size).pointerInput(min, max) {
                detectVerticalDragGestures(
                    onDragStart = { base = cur; acc = 0f },
                    onVerticalDrag = { c, dy -> c.consume(); acc += dy; onChange((base - acc / 120f * (max - min)).roundToInt().coerceIn(min, max)) }
                )
            }
        ) {
            val s = this.size.width / 44f
            val f = (value - min).toFloat() / (max - min)
            val c = Offset(22f * s, 22f * s)
            drawCircle(Color(0xFF141829), 17f * s, c)
            drawCircle(white(0.08f), 17f * s, c, style = Stroke(1f * s))
            val tl = Offset(2.5f * s, 2.5f * s); val sz = Size(39f * s, 39f * s)
            drawArc(white(0.1f), 135f, 270f, false, tl, sz, style = Stroke(2.4f * s, cap = StrokeCap.Round))
            if (f > 0.01f) drawArc(FM.In2, 135f, 270f * f, false, tl, sz, style = Stroke(2.4f * s, cap = StrokeCap.Round))
            rotate(-135f + f * 270f, c) { drawRoundRect(Color(0xFFDCE8FF), Offset(21f * s, 8f * s), Size(2f * s, 8f * s), CornerRadius(1f * s)) }
        }
        Column {
            Text(label, color = FM.Dim, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
            Text(display, color = Color(0xFFCFE0FF), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun StepsStepper(value: Int, onChange: (Int) -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Steps", color = FM.Dim, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.height(40.dp).clip(shape).background(Color(0xFF2A3050)).border(1.dp, Line, shape), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange((value - 1).coerceIn(1, 16)) }, contentAlignment = Alignment.Center) { FxIcon(FxIcons.Minus, FM.Txt, 16.dp) }
            Box(Modifier.width(50.dp).fillMaxHeight().drawBehind { drawLine(Line, Offset(0f, 0f), Offset(0f, size.height), 1f); drawLine(Line, Offset(size.width, 0f), Offset(size.width, size.height), 1f) }, contentAlignment = Alignment.Center) {
                Text("$value", color = FM.Txt, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(Modifier.size(40.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange((value + 1).coerceIn(1, 16)) }, contentAlignment = Alignment.Center) { FxIcon(FxIcons.Plus, FM.Txt, 16.dp) }
        }
    }
}

/** Row of 16 small indicators; the playing step is pale blue. */
@Composable
private fun StepDots(cur: Int) {
    Row(Modifier.fillMaxWidth().height(16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until 16) Box(
            Modifier.weight(1f).fillMaxHeight().background(if (cur == i) FM.In2 else Color(0xFF121626), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center
        ) { Text("${i + 1}", color = if (cur == i) FM.Ink else Color(0xFF4A5278), fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold) }
    }
}

private fun Modifier.panel(): Modifier = this.clip(RoundedCornerShape(14.dp)).background(white(0.035f)).border(1.dp, Line, RoundedCornerShape(14.dp))

/* ═══════════════════════════ EDITORS ═══════════════════════════ */

@Composable
private fun ArpEditor(p: ArpParams, actions: FxActions, cur: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(58.dp).panel().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Knob(p.sub, 0, 3, "Subdivision", Subdivisions[p.sub].first, { v -> actions.edit<ArpParams> { it.copy(sub = v) } })
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Text("Style", color = FM.Dim, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                val shape = RoundedCornerShape(12.dp)
                Row(Modifier.clip(shape).background(Color(0xFF161A2C)).border(1.dp, Line, shape).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ArpStyle.values().forEach { s ->
                        val sel = p.style == s
                        val w by animateDpAsState(if (sel) 104.dp else 46.dp, tween(200), label = "stylew")
                        Row(
                            Modifier.width(w).height(38.dp).clip(RoundedCornerShape(9.dp)).background(if (sel) Color(0xFF5B6591) else Color.Transparent)
                                .pressable { actions.edit<ArpParams> { it.copy(style = s) } },
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically
                        ) {
                            FxIcon(FxIcons.forStyle(s), if (sel) Color.White else Color(0xFF8791B5), 17.dp)
                            if (sel) Text(s.label, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
            StepsStepper(p.steps) { v -> actions.edit<ArpParams> { it.copy(steps = v) } }
        }
        Column(Modifier.fillMaxWidth().weight(1f).panel().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.width(44.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    Text("+", color = FM.Dim, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Velocity", color = FM.Dim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.graphicsLayer { rotationZ = -90f }.widthIn(min = 60.dp), textAlign = TextAlign.Center, maxLines = 1)
                    Text("−", color = FM.Dim, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (i in 0 until 16) {
                        val active = i < p.steps          // all 16 stay visible; the count only enables the first N
                        val eng = p.on[i]
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val barShape = RoundedCornerShape(9.dp)
                            Box(
                                Modifier.weight(1f).fillMaxWidth().clip(barShape).background(if (active) Color(0xFF171B2E) else white(0.03f))
                                    .border(1.dp, if (active) white(0.05f) else Color.Transparent, barShape)
                                    .pointerInput(active) {
                                        if (!active) return@pointerInput
                                        awaitPointerEventScope {
                                            while (true) {
                                                val d = awaitFirstDown(requireUnconsumed = false)
                                                val set = { y: Float -> val v = (1f - y / size.height).coerceIn(0.08f, 1f); actions.edit<ArpParams> { a -> a.copy(vel = a.vel.mapIndexed { k, x -> if (k == i) v else x }) } }
                                                set(d.position.y); d.consume()
                                                drag(d.id) { ch -> ch.consume(); set(ch.position.y) }
                                            }
                                        }
                                    }
                            ) {
                                if (active) Box(
                                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(p.vel[i]).clip(RoundedCornerShape(8.dp))
                                        .background(if (eng) Brush.verticalGradient(listOf(Color(0xFF6F79AE), Color(0xFF4D5788))) else SolidColor(Color(0xFF323A5C)))
                                ) {
                                    if (cur == i) Box(Modifier.fillMaxSize().background(white(0.18f)))
                                    Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(16.dp, 6.dp).drawBehind {
                                        drawLine(white(0.35f), Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx())
                                        drawLine(white(0.35f), Offset(0f, 5.dp.toPx()), Offset(size.width, 5.dp.toPx()), 2.dp.toPx())
                                    })
                                }
                            }
                            val kind = if (!active) TileKind.Empty else if (eng) TileKind.Out else TileKind.Dark
                            Box(
                                Modifier.fillMaxWidth().height(36.dp).alpha(if (active) 1f else 0.4f).tile(kind).pressable(enabled = active) { actions.edit<ArpParams> { a -> a.copy(on = a.on.mapIndexed { k, x -> if (k == i) !x else x }) } },
                                contentAlignment = Alignment.Center
                            ) { TileText("${i + 1}", kind, 13f) }
                        }
                    }
                }
            }
            StepDots(cur)
        }
    }
}

@Composable
private fun RowsEditor(m: MidiEffect, state: FxUiState, actions: FxActions, cur: Int, modifier: Modifier) {
    val isSeq = m.params is SeqParams
    val rows = (m.params as? SeqParams)?.rows ?: (m.params as EasyParams).rows
    val steps = (m.params as? SeqParams)?.steps ?: 8
    val cells = if (isSeq) 16 else 8
    val pulse by rememberInfiniteTransition(label = "listen").animateFloat(0.9f, 0.25f, infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    fun setRows(f: (List<NoteRow>) -> List<NoteRow>) { if (isSeq) actions.edit<SeqParams> { it.copy(rows = f(it.rows)) } else actions.edit<EasyParams> { it.copy(rows = f(it.rows)) } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isSeq) {
            val p = m.params as SeqParams
            Row(Modifier.fillMaxWidth().height(58.dp).panel().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Knob(p.sub, 0, 3, "Subdivision", Subdivisions[p.sub].first, { v -> actions.edit<SeqParams> { it.copy(sub = v) } })
                Knob(p.vel, 10, 100, "Velocity", "${p.vel} %", { v -> actions.edit<SeqParams> { it.copy(vel = v) } })
                Spacer(Modifier.weight(1f))
                StepsStepper(p.steps) { v -> actions.edit<SeqParams> { it.copy(steps = v) } }
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).panel().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.width(24.dp)); Text("Input", Modifier.width(54.dp), color = FM.Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold); Text("Output", color = FM.Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            rows.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${i + 1}.", Modifier.width(24.dp), color = FM.Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    val li = state.listen != null && state.listen.row == i && state.listen.col == null
                    Box(Modifier.width(54.dp).height(44.dp).tile(TileKind.In).then(if (li) Modifier.border(2.dp, FM.In1.a(pulse), RoundedCornerShape(10.dp)) else Modifier).pressable { actions.onListen(ListenTarget(i, null)) }, contentAlignment = Alignment.Center) { TileText(FxLogic.noteName(r.input), TileKind.In) }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (k in 0 until cells) {
                            val note = r.out.getOrNull(k)
                            val on = !isSeq || k < steps              // the other cells stay visible, dimmed
                            val ls = state.listen != null && state.listen.row == i && state.listen.col == k
                            val kind = if (note == null) TileKind.Empty else TileKind.Out
                            Box(
                                Modifier.weight(1f).height(44.dp).alpha(if (on) 1f else 0.32f).tile(kind)
                                    .then(if (isSeq && cur == k && on) Modifier.border(2.dp, FM.In1.a(0.55f), RoundedCornerShape(10.dp)) else Modifier)
                                    .then(if (ls) Modifier.border(2.dp, FM.In1.a(pulse), RoundedCornerShape(10.dp)) else Modifier)
                                    .pressable(enabled = on) { actions.onListen(ListenTarget(i, k)) },
                                contentAlignment = Alignment.Center
                            ) { if (note != null) TileText(FxLogic.noteName(note), kind, 10.5f) }
                        }
                    }
                    Box(Modifier.size(30.dp, 36.dp).pressable { if (rows.size > 1) setRows { l -> l.filterIndexed { k, _ -> k != i } } }, contentAlignment = Alignment.Center) { FxIcon(FxIcons.Trash, FM.Link, 18.dp) }
                }
            }
            if (rows.size < (if (isSeq) FxLogic.MAX_SEQ_ROWS else 99)) FxButton(Modifier.padding(start = 34.dp), 38.dp, null, onClick = {
                setRows { l -> l + NoteRow((l.last().input + 2).coerceIn(24, 90), if (isSeq) List(16) { 48 } else listOf(48)) }
            }) { FxIcon(FxIcons.Plus, Color(0xFFD5DCF2), 14.dp); Text(if (isSeq) "Add Sequence (Up to 8)" else "Add Chord", color = Color(0xFFD5DCF2), fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
            if (state.listen != null) Text("Play a key on your keyboard to set this note", Modifier.padding(start = 34.dp), color = FM.In2, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
        if (isSeq) StepDots(cur)
    }
}

@Composable
private fun OctaveEditor(p: OctaveParams, actions: FxActions, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(Modifier.width(440.dp).panel().padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Knob(p.blend, 0, 100, "Blend", "${p.blend} %", { v -> actions.edit<OctaveParams> { it.copy(blend = v) } }, 78.dp)
            Text("Adds an octave above every note you play. Blend sets the velocity of the added note.", color = Color(0xFFA3ACCB), fontSize = 12.5.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun ChordEditor(p: ChordParams, actions: FxActions, barPos: Double, modifier: Modifier) {
    val sel = p.chords.firstOrNull { it.id == p.sel }
    val total = p.chords.sumOf { it.len }
    val room = 8 - total
    fun upd(f: (ChordSlot) -> ChordSlot) = actions.edit<ChordParams> { c -> c.copy(chords = c.chords.map { if (it.id == c.sel) f(it) else it }) }
    fun place(d: Int) {
        val (root, q) = FxLogic.diaChord(p.keyPc, p.scale, d, p.sevenths)
        if (sel != null) { upd { it.copy(root = root, quality = q) }; return }
        if (room < 1) return
        val n = ChordSlot((System.nanoTime() and 0x7FFFFFFF).toInt(), root, q, min(2, room))
        actions.edit<ChordParams> { it.copy(chords = it.chords + n, sel = n.id) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FxButton(height = 34.dp, width = 34.dp, onClick = { actions.edit<ChordParams> { it.copy(keyPc = (it.keyPc + 11) % 12) } }) { FxIcon(FxIcons.Left, Color(0xFFD5DCF2), 14.dp) }
            Row(Modifier.width(54.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("KEY ", color = FM.Dim, fontSize = 9.sp); Text(FxLogic.pitchName(p.keyPc), color = FM.Txt, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            FxButton(height = 34.dp, width = 34.dp, onClick = { actions.edit<ChordParams> { it.copy(keyPc = (it.keyPc + 1) % 12) } }) { FxIcon(FxIcons.Right, Color(0xFFD5DCF2), 14.dp) }
            Segmented(listOf(Scale.Major to "Major", Scale.Minor to "Minor"), p.scale, { v -> actions.edit<ChordParams> { it.copy(scale = v) } }, 130.dp, 28.dp)
            Box(Modifier.height(34.dp).clip(RoundedCornerShape(11.dp)).background(if (p.sevenths) Color(0xFF5B6591) else FM.Btn).pressable { actions.edit<ChordParams> { it.copy(sevenths = !it.sevenths) } }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("7ths", color = Color(0xFFD5DCF2), fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            Segmented(PlayStyle.values().map { it to it.label }, p.style, { v -> actions.edit<ChordParams> { it.copy(style = v) } }, 170.dp, 28.dp)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(14.dp)).background(white(0.03f)).border(1.dp, Line, RoundedCornerShape(14.dp))
            .drawBehind { for (i in 1 until 8) drawLine(white(0.05f), Offset(size.width / 8 * i, 0f), Offset(size.width / 8 * i, size.height), 1f) }) {
            val slot = maxWidth / 8
            val slotPx = with(androidx.compose.ui.platform.LocalDensity.current) { slot.toPx() }
            var start = 0
            p.chords.forEach { ch ->
                val s0 = start; start += ch.len
                val active = barPos >= s0 && barPos < s0 + ch.len
                val deg = FxLogic.degreeOf(p, ch)
                val roomNow by rememberUpdatedState(room)
                val selected = p.sel == ch.id
                Box(
                    Modifier.offset(x = slot * s0 + 3.dp, y = 6.dp).width(slot * ch.len - 6.dp).height(72.dp).tile(TileKind.Out)
                        .then(if (selected) Modifier.border(2.dp, FM.In1, RoundedCornerShape(10.dp)) else Modifier)
                        .pressable { actions.edit<ChordParams> { it.copy(sel = ch.id) } }
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(if (deg >= 0) FxLogic.roman(p.scale)[deg] else "·", color = FM.Txt, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Text(FxLogic.chordName(ch), color = Color(0xFFDCE6FF), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${ch.len} bar${if (ch.len > 1) "s" else ""}", color = Color(0xFFB4BEE0), fontSize = 9.5.sp)
                    }
                    if (active) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(((barPos - s0) / ch.len).toFloat().coerceIn(0f, 1f)).height(3.dp).background(FM.In1))
                    // drag the right edge to change the length of the chord
                    Box(
                        Modifier.align(Alignment.CenterEnd).width(18.dp).fillMaxHeight().pointerInput(ch.id) {
                            var total = 0f; var len0 = 0; var max0 = 0
                            detectDragGestures(
                                onDragStart = { total = 0f; len0 = ch.len; max0 = ch.len + roomNow },
                                onDrag = { c, d -> c.consume(); total += d.x; val nl = (len0 + total / slotPx).roundToInt().coerceIn(1, max0); actions.edit<ChordParams> { cp -> cp.copy(chords = cp.chords.map { if (it.id == ch.id) it.copy(len = nl) else it }) } }
                            )
                        },
                        contentAlignment = Alignment.Center
                    ) { Box(Modifier.size(3.dp, 24.dp).background(white(0.4f), RoundedCornerShape(2.dp))) }
                }
            }
            if (room > 0) Box(
                Modifier.offset(x = slot * (8 - room) + 3.dp, y = 6.dp).width(slot * room - 6.dp).height(72.dp)
                    .drawBehind { drawRoundRect(white(0.15f), cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f)))) }
                    .pressable { place(0) }, contentAlignment = Alignment.Center
            ) { Text("+ Add chord", color = FM.Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            if (barPos >= 0) Box(Modifier.offset(x = slot * barPos.toFloat()).width(2.dp).fillMaxHeight().background(FM.In1.a(0.9f)))
        }
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in 0 until 7) {
                val (root, q) = FxLogic.diaChord(p.keyPc, p.scale, d, p.sevenths)
                Column(Modifier.weight(1f).fillMaxHeight().tile(TileKind.Out).pressable { place(d) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(FxLogic.roman(p.scale)[d], color = Color(0xFFC9D5F5), fontSize = 10.sp)
                    TileText(FxLogic.pitchName(root) + q.suffix, TileKind.Out, 13f)
                }
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).panel().alpha(if (sel != null) 1f else 0.5f).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Quality.values().forEach { q ->
                    val kind = if (sel?.quality == q) TileKind.In else TileKind.Out
                    Box(Modifier.height(32.dp).widthIn(min = 52.dp).tile(kind).pressable(enabled = sel != null) { upd { it.copy(quality = q) } }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { TileText(q.label, kind) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Segmented(listOf(0 to "Root", 1 to "1st", 2 to "2nd"), sel?.inv ?: 0, { v -> upd { it.copy(inv = min(v, it.quality.intervals.size - 1)) } }, 150.dp)
                Segmented(Voicing.values().map { it to it.label }, sel?.voicing ?: Voicing.Close, { v -> upd { it.copy(voicing = v) } }, 170.dp)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                    (sel?.let { FxLogic.chordNotes(it) } ?: emptyList()).forEach { n -> Box(Modifier.size(42.dp, 32.dp).tile(TileKind.Out), contentAlignment = Alignment.Center) { TileText(FxLogic.noteName(n), TileKind.Out, 11f) } }
                }
                FxButton(height = 32.dp, width = 42.dp, onClick = {
                    if (sel != null && sel.len <= room) actions.edit<ChordParams> { c -> val n = sel.copy(id = (System.nanoTime() and 0x7FFFFFFF).toInt()); c.copy(chords = c.chords + n, sel = n.id) }
                }) { FxIcon(FxIcons.Copy, Color(0xFFD5DCF2), 14.dp) }
                FxButton(height = 32.dp, width = 42.dp, onClick = { actions.edit<ChordParams> { c -> c.copy(chords = c.chords.filter { it.id != c.sel }, sel = null) } }) { FxIcon(FxIcons.Trash, Color(0xFFE58AA0), 14.dp) }
            }
        }
    }
}

/**
 * Full-screen editor for the MIDI effect being edited. beatPos = transport position in beats while notes are playing
 * (drives the step indicators), or null when nothing is playing.
 */
@Composable
fun FxEditorScreen(state: FxUiState, actions: FxActions, beatPos: Double?, modifier: Modifier = Modifier) {
    val m = state.editedEffect ?: return
    val content = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 62.dp, bottom = 56.dp)
    val subIndex = when (val p = m.params) { is ArpParams -> p.sub; is SeqParams -> p.sub; else -> 2 }
    val steps = when (val p = m.params) { is ArpParams -> p.steps; is SeqParams -> p.steps; else -> 16 }
    val cur = FxLogic.currentStep(beatPos, subIndex, steps)
    Box(modifier.fillMaxSize().background(ScreenBg).grain()) {
        Row(
            Modifier.fillMaxWidth().height(54.dp).background(Color(0xFF0F1220)).drawBehind { drawLine(Line, Offset(0f, size.height - 0.5f), Offset
Continue avec un autre message


kotlin
Copy
Download
        Row(
            Modifier.fillMaxWidth().height(54.dp).background(Color(0xFF0F1220)).drawBehind { drawLine(Line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FxButton(onClick = actions.onReplace) { FxIcon(FxIcons.Replace, Color(0xFFD5DCF2), 15.dp); Text("Replace Effect", color = Color(0xFFD5DCF2), fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
            FxButton(onClick = { actions.onMessage("Presets list") }) { Text("Presets", color = Color(0xFFD5DCF2), fontSize = 12.5.sp, fontWeight = FontWeight.Bold); FxIcon(FxIcons.Down, Color(0xFFD5DCF2), 13.dp) }
            FxButton(onClick = { actions.onMessage("Help") }) { FxIcon(FxIcons.Help, Color(0xFFD5DCF2), 15.dp); Text("Help", color = Color(0xFFD5DCF2), fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(FM.Org, RoundedCornerShape(4.dp)))
                Text(m.type.title, color = FM.Txt, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text("Effect ${if (m.on) "ON" else "OFF"}", color = FM.Dim, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
            PowerButton(m.on, onChange = actions.onEffectOn)
        }
        when (val p = m.params) {
            is ArpParams -> ArpEditor(p, actions, cur, content)
            is SeqParams, is EasyParams -> RowsEditor(m, state, actions, cur, content)
            is OctaveParams -> OctaveEditor(p, actions, content)
            is ChordParams -> ChordEditor(p, actions, FxLogic.chordBar(beatPos), content)
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(start = 10.dp, end = 10.dp, bottom = 8.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Discard Changes", Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = actions.onDiscard), color = Color(0xFFB5BEDB), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.height(40.dp).clip(RoundedCornerShape(11.dp)).background(Brush.verticalGradient(listOf(FM.In1, FM.In2))).pressable(onClick = actions.onConfirm).padding(horizontal = 30.dp), contentAlignment = Alignment.Center) {
                Text("Confirm", color = FM.Ink, fontSize = 13.5.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/* ═══════════════════════════ FX WINDOW ═══════════════════════════ */

@Composable
private fun FxCard(glyph: String, name: String, sub: String, on: Boolean, onToggle: (Boolean) -> Unit, onOpen: () -> Unit, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().height(64.dp).alpha(if (on) 1f else 0.65f).clip(shape).background(white(0.04f)).border(1.dp, Line, shape).pressable(onClick = onOpen).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(40.dp).tile(TileKind.Out), contentAlignment = Alignment.Center) { TileText(glyph, TileKind.Out, 18f) }
        Column(Modifier.weight(1f)) {
            Text(name, color = FM.Txt, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, Modifier.padding(top = 2.dp), color = FM.Dim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(32.dp, 36.dp).pressable(onClick = onRemove), contentAlignment = Alignment.Center) { FxIcon(FxIcons.Trash, FM.Link, 17.dp) }
        PowerButton(on, onChange = onToggle)
    }
}

@Composable
private fun SectionLabel(text: String) = Text(text, color = FM.Dim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)

/** The effects window of one track. Place it at the root of the page, above everything. */
@Composable
fun FxWindow(track: TrackInfo, state: FxUiState, actions: FxActions, modifier: Modifier = Modifier) {
    val st = state.fx[track.id] ?: TrackFx()
    var cat by remember { mutableStateOf("midi") }
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(300)) }
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0xFF060810).a(0.66f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = actions.onCloseWindow))
        Column(
            Modifier.fillMaxSize().padding(start = 100.dp, end = 100.dp, top = 26.dp, bottom = 24.dp)
                .graphicsLayer { val e = enter.value; scaleX = 0.94f + 0.06f * e; scaleY = 0.94f + 0.06f * e; translationY = (1f - e) * 8.dp.toPx(); alpha = e }
                .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape).background(WindowBg).border(1.dp, Line, shape).grain()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            Row(
                Modifier.fillMaxWidth().height(58.dp).drawBehind { drawLine(Line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.view == WindowView.Picker) FxButton(height = 36.dp, width = 36.dp, onClick = { actions.onView(WindowView.List) }) { FxIcon(FxIcons.Back, Color(0xFFD5DCF2), 16.dp) }
                else Box(Modifier.size(4.dp, 28.dp).background(track.color, RoundedCornerShape(2.dp)))
                Text(
                    if (state.view == WindowView.Picker) "Select Effect" else "${track.name} · Effects", Modifier.weight(1f),
                    color = FM.Txt, fontSize = 16.sp, fontWeight = FontWeight.Black, textAlign = if (state.view == WindowView.Picker) TextAlign.Center else TextAlign.Start
                )
                FxButton(height = 36.dp, width = 36.dp, onClick = actions.onCloseWindow) { FxIcon(FxIcons.Close, Color(0xFFD5DCF2), 15.dp) }
            }
            if (state.view == WindowView.List) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("MIDI EFFECT · 1 SLOT")
                    st.midi?.let { m -> FxCard(m.type.glyph, m.type.title, FxLogic.summary(m), m.on, { actions.onToggleMidi(track.id, it) }, { actions.onOpenEditor(track.id) }, { actions.onRemoveMidi(track.id) }) }
                    if (st.audio.isNotEmpty()) { Spacer(Modifier.height(4.dp)); SectionLabel("AUDIO EFFECTS · UP TO 3") }
                    st.audio.forEach { a -> FxCard("≋", a.type.title, "Audio effect", a.on, { actions.onToggleAudio(track.id, a.id) }, { actions.onMessage("Audio effect editors are not part of this screen") }, { actions.onRemoveAudio(track.id, a.id) }) }
                    val empty = st.midi == null && st.audio.isEmpty()
                    Box(
                        Modifier.padding(top = 4.dp).fillMaxWidth().height(if (empty) 120.dp else 54.dp)
                            .drawBehind { drawRoundRect(white(0.16f), cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f)))) }
                            .pressable { actions.onView(WindowView.Picker) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FxIcon(FxIcons.Plus, FM.In2, 17.dp); Text("Add FX", color = FM.In2, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.width(150.dp).fillMaxHeight().drawBehind { drawLine(Line, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), 1f) }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("midi" to "MIDI", "audio" to "Audio").forEach { (k, l) ->
                            val kind = if (cat == k) TileKind.Out else TileKind.Empty
                            Box(Modifier.fillMaxWidth().height(42.dp).tile(kind).pressable { cat = k }.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) { TileText(l, kind, 13.5f) }
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp)) {
                        Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FxIcon(FxIcons.Help, FM.In2, 15.dp)
                            Text(
                                if (cat == "midi") "A track has one MIDI effect. Choosing another one replaces the current effect. Confirming changes updates the existing patch snapshots."
                                else "A track can use up to three audio effects.",
                                color = Color(0xFF9AA4C8), fontSize = 11.sp, lineHeight = 16.sp
                            )
                        }
                        val items: List<Triple<String, String, () -> Unit>> =
                            if (cat == "midi") MidiType.values().map { t -> Triple(t.title, t.desc) { actions.onPickMidi(track.id, t) } }
                            else AudioType.values().map { t -> Triple(t.title, "Audio effect") { actions.onPickAudio(track.id, t) } }
                        items.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { (n, d, go) ->
                                    val s = RoundedCornerShape(12.dp)
                                    Row(Modifier.weight(1f).height(58.dp).clip(s).background(white(0.045f)).border(1.dp, Line, s).pressable(onClick = go).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Column(Modifier.weight(1f)) {
                                            Text(n, color = FM.Txt, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                                            Text(d, Modifier.padding(top = 2.dp), color = FM.Dim, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        FxIcon(FxIcons.Go, Color(0xFF8FA5D8), 22.dp)
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Global swing chip for the mixer top bar (shared by every MIDI effect, not part of the FX window). */
@Composable
fun SwingChip(swing: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.height(40.dp).clip(shape).background(FM.Btn).border(1.dp, Line, shape).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF2F365A)).pressable { onChange((swing - 1).coerceIn(50, 75)) }, contentAlignment = Alignment.Center) { FxIcon(FxIcons.Minus, FM.Txt, 13.dp) }
        Text("Swing $swing%", Modifier.width(82.dp), color = FM.Txt, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF2F365A)).pressable { onChange((swing + 1).coerceIn(50, 75)) }, contentAlignment = Alignment.Center) { FxIcon(FxIcons.Plus, FM.Txt, 13.dp) }
    }
}

@Composable
fun FxToastHost(toast: FxToast?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    toast ?: return
    LaunchedEffect(toast.key) { delay(1600); onDismiss() }
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(toast.text, Modifier.padding(bottom = 14.dp).clip(shape).background(Color(0xFF262C4A)).border(1.dp, Line, shape).padding(horizontal = 14.dp, vertical = 8.dp), color = FM.Txt, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Position in beats, refreshed ~30 times a second. Visual only: pass beatPos = null when nothing is playing. */
@Composable
fun rememberBeatPos(clock: () -> Double): Double {
    var now by remember { mutableStateOf(clock()) }
    val c by rememberUpdatedState(clock)
    LaunchedEffect(Unit) { var last = 0L; while (true) androidx.compose.runtime.withFrameNanos { t -> if (t - last >= 30_000_000L) { last = t; now = c() } } }
    return now
}

/* ═══════════════════════════ PREVIEW ═══════════════════════════ */

@Preview(widthDp = 891, heightDp = 411, backgroundColor = 0xFF131728, showBackground = true)
@Composable
private fun FxModesPreview() {
    val controller = remember {
        FxController(mapOf(1 to TrackFx(FxLogic.mkMidi(MidiType.Arp), listOf(AudioEffect(1, AudioType.Reverb))), 2 to TrackFx()))
    }
    val state by androidx.compose.runtime.collectAsState(controller.state)
    val actions = remember { controller.actions() }
    LaunchedEffect(Unit) { controller.openWindow(1) }
    val t0 = remember { System.nanoTime() }
    val beat = rememberBeatPos { (System.nanoTime() - t0) / 1e9 * 119.0 / 60.0 }
    Box(Modifier.fillMaxSize().background(ScreenBg)) {
        state.windowTrackId?.let { FxWindow(TrackInfo(it, "Piano", Color(0xFF7F9BC7)), state, actions) }
        if (state.editing != null) FxEditorScreen(state, actions, beat)
        FxToastHost(state.toast, actions.onToastDismiss)
    }
}

Le fichier FxModes.kt est maintenant complet jusqu'à la fin (section PREVIEW incluse). Le fichier FxModesPreview.jsx avait déjà été fourni intégralement dans le message précédent — les deux fichiers sont donc entièrement reproduits.

Tu me renvoies juste leur contenu exact sans altération.
Continue avec un autre message
Continue with DeepSeek
Cookie Settings
We use cookies to provide and improve services and ensure security. Click to view our 
cookie policy. You can choose to accept all or only necessary cookies, which may affect some functionalities.
Necessary cookies only
Accept all cookies
