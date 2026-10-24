package com.soundstage.mixer.ui.tracksettings

/*
 * TrackSettings — faithful to TrackSettingsPreview.jsx (approved).
 *
 * One window, four screens: Track (hub), Velocity & Dynamic Response, MIDI, Note range.
 * Stateless UI: everything comes from TrackSettingsState; every change goes out through TrackSettingsActions.
 * Only transient gesture state is local (test velocity, active point, pressed key).
 * Pure logic (velocity curve, note range keyboard geometry) lives in VelocityLogic / RangeKeys.
 *
 * Surfaces: the window background is an OPAQUE gradient and carries the shadow. Translucent fills
 * (white alpha) never carry a shadow (that is what produced the grey boxes on the Setlists cards).
 * Replace the private controls (switch, stepper, segmented, slider, rows) with the Settings page
 * components wherever an equivalent exists, so that everything looks identical (see the brief).
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.soundstage.mixer.ui.components.KeyboardLogic
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/* ═══════════════════════════ MODEL ═══════════════════════════ */

enum class TsPage { Track, Velocity, Midi, Range }

enum class VelocityPreset(val label: String) {
    Linear("Linear"), Soft("Soft"), Hard("Hard"), SCurve("S-curve"), Compress("Compress"), Custom("Custom")
}

@Immutable
data class VelocitySettings(
    val preset: VelocityPreset = VelocityPreset.Linear,
    /** 5 points at input 0, 25, 50, 75, 100 %. Output 0..1. */
    val ys: List<Float> = VelocityLogic.presetYs(VelocityPreset.Linear),
    val floor: Int = 0,
    val ceiling: Int = 127,
    val offset: Int = 0,
    val humanize: Int = 0,
    val fixed: Boolean = false,
    val fixedValue: Int = 100
)

@Immutable
data class TsDeviceItem(val id: String, val name: String, val subtitle: String)

@Immutable
data class TrackSettingsState(
    val trackName: String,            // "Track 4"
    val soundName: String,            // "Organ B3 Fast"
    val color: Color,                 // the track color
    val velocity: VelocitySettings = VelocitySettings(),
    val devices: List<TsDeviceItem>,
    val midiDeviceId: String,
    val midiChannel: Int = 0,         // 0 = Omni, 1..16
    val noteLow: Int = 36,            // MIDI note, C2 = 36
    val noteHigh: Int = 84,
    val transpose: Int = 0,           // -12..+12
    val octave: Int = 0,              // -3..+3
    val pitchBendRange: Int = 2,      // 1..12 semitones
    val defaultVolume: Int = 100,     // 0..127
    val defaultPan: Int = 0,          // -64..63
    val sustain: Boolean = true,
    val aftertouch: Boolean = false,
    val programChange: Boolean = true
)

class TrackSettingsActions(
    val onNavigate: (TsPage) -> Unit,
    val onClose: () -> Unit,
    val onVelocityChange: (VelocitySettings) -> Unit,
    val onMidiDevice: (String) -> Unit,
    val onMidiChannel: (Int) -> Unit,
    val onNoteRange: (low: Int, high: Int) -> Unit,
    val onTranspose: (Int) -> Unit,
    val onOctave: (Int) -> Unit,
    val onPitchBendRange: (Int) -> Unit,
    val onDefaultVolume: (Int) -> Unit,
    val onDefaultPan: (Int) -> Unit,
    val onSustain: (Boolean) -> Unit,
    val onAftertouch: (Boolean) -> Unit,
    val onProgramChange: (Boolean) -> Unit
) {
    companion object {
        val NoOp = TrackSettingsActions({}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/* ═══════════════════════════ PURE LOGIC ═══════════════════════════ */

object VelocityLogic {
    val xs = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

    private fun fn(p: VelocityPreset): ((Float) -> Float)? = when (p) {
        VelocityPreset.Linear -> { x -> x }
        // Soft and Hard were inverted: the gentler curve was on Hard and the
        // demanding one on Soft, so the two presets played the opposite of their
        // names.
        VelocityPreset.Soft -> { x -> x.pow(1.7f) }
        VelocityPreset.Hard -> { x -> x.pow(0.6f) }
        VelocityPreset.SCurve -> { x -> if (x < 0.5f) 2f * x * x else 1f - (-2f * x + 2f).pow(2) / 2f }
        VelocityPreset.Compress -> { x -> 0.5f + (x - 0.5f) * 0.5f }
        VelocityPreset.Custom -> null
    }

    fun presetYs(p: VelocityPreset): List<Float> = xs.map { (fn(p)?.invoke(it) ?: it).coerceIn(0f, 1f) }

    /** Catmull-Rom through the 5 points. x and result are 0..1. */
    fun evalCurve(ys: List<Float>, x: Float): Float {
        val xx = x.coerceIn(0f, 1f)
        val n = ys.size - 1
        val seg = min(n - 1, floor(xx * n).toInt())
        val t = xx * n - seg
        val p0 = ys[max(0, seg - 1)]
        val p1 = ys[seg]
        val p2 = ys[seg + 1]
        val p3 = ys[min(n, seg + 2)]
        val t2 = t * t
        val t3 = t2 * t
        val v = 0.5f * (2f * p1 + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3)
        return v.coerceIn(0f, 1f)
    }

    /** Output velocity (1..127) for an input velocity (0..127). Humanize is applied by the engine, not here. */
    fun outputOf(input: Int, v: VelocitySettings): Int {
        if (v.fixed) return v.fixedValue.coerceIn(1, 127)
        val y = evalCurve(v.ys, input / 127f)
        return (v.floor + (v.ceiling - v.floor) * y + v.offset).roundToInt().coerceIn(1, 127)
    }
}

/**
 * Adapter over the shared keyboard geometry (KeyboardLogic, C1..C8 = 24..108).
 * Same engine as the mixer's Layer lines, so the Note range page and the Layer bar
 * can never disagree on the note bounds.
 */
object RangeKeys {
    const val MIN = KeyboardLogic.NOTE_MIN
    const val MAX = KeyboardLogic.NOTE_MAX
    val notes = KeyboardLogic.notes
    fun isBlack(n: Int) = KeyboardLogic.isBlack(n)
    val whites = KeyboardLogic.whites
    val blacks = KeyboardLogic.blacks
    const val VW = 576f
    val WW = VW / KeyboardLogic.whiteCount
    val BW = WW * 0.6f
    const val BAR_H = 40f
    const val KY = 56f
    const val WH = 110f
    const val BH = 64f
    const val TOTAL_H = KY + WH + 14f

    fun cx(n: Int): Float = KeyboardLogic.centerFraction(n) * VW
    fun nearest(x: Float): Int = KeyboardLogic.nearestNote(x / VW)
    fun noteAt(x: Float, y: Float): Int? {
        if (y < KY || y > KY + WH || x < 0f || x > VW) return null
        return KeyboardLogic.noteAt(x, y - KY, VW, WH, BH)
    }

    fun name(n: Int) = KeyboardLogic.noteName(n)
}

/* ═══════════════════════════ COLORS ═══════════════════════════ */

private val Ink = Color(0xFFE9EDF5)
private val SoftInk = Color(0xFFC9D2E2)
private val Mute = Color(0xFF7B859A)
private val Dim = Color(0xFF6F7A90)
private val LabelInk = Color(0xFF9AA5BA)
private val Accent = Color(0xFF8FB2E8)
private val Mono = FontFamily.Monospace
private fun white(a: Float) = Color.White.copy(alpha = a)

/* ═══════════════════════════ WINDOW ═══════════════════════════ */

@Composable
fun TrackSettingsWindow(
    state: TrackSettingsState,
    screen: TsPage,
    actions: TrackSettingsActions,
    modifier: Modifier = Modifier.size(600.dp, 370.dp),
    transformOrigin: TransformOrigin = TransformOrigin.Center
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 380f)) }
    val shape = RoundedCornerShape(22.dp)

    val title: String
    val sub: String
    when (screen) {
        TsPage.Track -> { title = "${state.trackName} Settings"; sub = "Sound: ${state.soundName}" }
        TsPage.Velocity -> { title = "Velocity & Dynamic Response"; sub = "${state.trackName} · ${state.soundName}" }
        TsPage.Midi -> { title = "MIDI"; sub = "${state.trackName} listens to" }
        TsPage.Range -> { title = "Note range"; sub = "${state.trackName} · ${RangeKeys.name(state.noteLow)} – ${RangeKeys.name(state.noteHigh)}" }
    }

    Column(
        modifier
            .graphicsLayer {
                val e = enter.value
                scaleX = 0.9f + 0.1f * e; scaleY = 0.9f + 0.1f * e
                translationY = (1f - e) * 10.dp.toPx()
                alpha = e.coerceIn(0f, 1f)
                this.transformOrigin = transformOrigin
            }
            .shadow(26.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF232C3E), Color(0xFF1A2230)))) // OPAQUE
            .border(1.dp, white(0.1f), shape)
    ) {
        Header(
            title = title, sub = sub, color = state.color,
            onBack = if (screen != TsPage.Track) ({ actions.onNavigate(TsPage.Track) }) else null,
            onClose = actions.onClose,
            right = if (screen == TsPage.Velocity) {
                { ResetButton { actions.onVelocityChange(VelocitySettings()) } }
            } else null
        )
        AnimatedContent(
            targetState = screen,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val forward = targetState != TsPage.Track
                (slideInHorizontally(tween(280)) { (if (forward) 26 else -26) } + fadeIn(tween(220))) togetherWith fadeOut(tween(100))
            },
            label = "track-settings-screen"
        ) { s ->
            when (s) {
                TsPage.Track -> TrackPage(state, actions)
                TsPage.Velocity -> VelocityPage(state.velocity, state.color, actions.onVelocityChange)
                TsPage.Midi -> MidiPage(state, actions)
                TsPage.Range -> RangePage(state, actions)
            }
        }
    }
}

@Composable
private fun Header(title: String, sub: String, color: Color, onBack: (() -> Unit)?, onClose: () -> Unit, right: (@Composable () -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(66.dp)
            .drawBehind { drawLine(white(0.06f), Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx()) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) RoundIconButton(Icon.Back, onBack)
        else Box(Modifier.size(width = 4.dp, height = 34.dp).background(color, RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(0xFFF4F6FB), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Mute, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        right?.invoke()
        RoundIconButton(Icon.Close, onClose)
    }
}

@Composable
private fun ResetButton(onClick: () -> Unit) {
    Row(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(white(0.05f))
            .border(1.dp, white(0.12f), RoundedCornerShape(10.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon13(Icon.Reset, Color(0xFFC7CFDD))
        Text("Reset", color = Color(0xFFC7CFDD), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/* ═══════════════════════════ TRACK PAGE ═══════════════════════════ */

@Composable
private fun TrackPage(state: TrackSettingsState, a: TrackSettingsActions) {
    val color = state.color
    val device = state.devices.firstOrNull { it.id == state.midiDeviceId }
    Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(268.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SectionLabel("PERFORMANCE")
            NavRow("Velocity & Dynamic Response", "${state.velocity.preset.label} curve", { a.onNavigate(TsPage.Velocity) }) {
                Canvas(Modifier.size(56.dp, 22.dp)) {
                    val p = Path()
                    for (i in 0..16) {
                        val x = i / 16f
                        val y = VelocityLogic.outputOf((x * 127).roundToInt(), state.velocity) / 127f
                        val px = 1f + x * (size.width - 2f)
                        val py = 1f + (1f - y) * (size.height - 2f)
                        if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                    }
                    drawPath(p, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            NavRow("MIDI", "Input device and channel", { a.onNavigate(TsPage.Midi) }) {
                ValueChip("${(device?.name ?: "—").substringBefore(' ')} · ${if (state.midiChannel == 0) "Omni" else "Ch ${state.midiChannel}"}", color)
            }
            NavRow("Note range", "Lowest and highest key", { a.onNavigate(TsPage.Range) }) {
                MiniKeys(state.noteLow, state.noteHigh, color)
                Text("${RangeKeys.name(state.noteLow)}–${RangeKeys.name(state.noteHigh)}", Modifier.padding(start = 6.dp), color = SoftInk, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Mono, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            SectionLabel("PLAYBACK")
            SwitchRow("Sustain pedal", "Hold notes while the pedal is down", state.sustain, color, a.onSustain)
            SwitchRow("Aftertouch", "Send pressure to the sound", state.aftertouch, color, a.onAftertouch)
            SwitchRow("Program change", "Accept incoming patch changes", state.programChange, color, a.onProgramChange)
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("TONE & LEVEL")
            ControlRow("Transpose", "Shifts this track in semitones") {
                Stepper(state.transpose, -12, 12, a.onTranspose) { if (it > 0) "+$it" else "$it" }
            }
            ControlRow("Octave", "Moves this track by octaves") {
                Segmented((-3..3).toList(), state.octave, a.onOctave, color, Modifier.width(238.dp)) { if (it > 0) "+$it" else if (it < 0) "−${abs(it)}" else "0" }
            }
            ControlRow("Pitch bend range", "Semitones reached at full bend") {
                Stepper(state.pitchBendRange, 1, 12, a.onPitchBendRange) { "$it st" }
            }
            Spacer(Modifier.height(4.dp))
            SectionLabel("DEFAULTS AT LOAD")
            Box(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                Slider("Volume", state.defaultVolume, 0, 127, 1, color, onChange = a.onDefaultVolume)
            }
            Box(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                Slider("Pan", state.defaultPan, -64, 63, 1, color, onChange = a.onDefaultPan) { if (it == 0) "C" else if (it < 0) "L ${-it}" else "R $it" }
            }
        }
    }
}

@Composable
private fun NavRow(title: String, hint: String, onClick: () -> Unit, right: @Composable () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(white(0.065f), white(0.03f))))
            .border(1.dp, white(0.06f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hint, color = Mute, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        right()
        Icon13(Icon.Chevron, Dim, 15.dp)
    }
}

@Composable
private fun ValueChip(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) { Text(text, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Mono, maxLines = 1) }
}

@Composable
private fun MiniKeys(low: Int, high: Int, color: Color) {
    Canvas(Modifier.size(78.dp, 14.dp)) {
        val px = 1.dp.toPx()
        drawRoundRect(Color(0xFF0E131C), cornerRadius = CornerRadius(3 * px))
        drawRoundRect(white(0.08f), cornerRadius = CornerRadius(3 * px), style = Stroke(px))
        val span = (RangeKeys.MAX - RangeKeys.MIN).toFloat()
        val x0 = ((low - RangeKeys.MIN) / span).coerceIn(0f, 1f) * 76f * px
        val x1 = ((high - RangeKeys.MIN) / span).coerceIn(0f, 1f) * 76f * px
        drawRoundRect(color.copy(alpha = 0.85f), Offset(x0 + px, 3 * px), Size(max(3 * px, x1 - x0), 8 * px), CornerRadius(2 * px))
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, on: Boolean, color: Color, onChange: (Boolean) -> Unit) {
    // PLAYBACK subtitle was still clipping on the second line: 44dp forced the two
    // lines against the switch. The hint drops to 9.5sp with an 11sp line height and
    // the row gains 2dp, so the whole subtitle reads without touching the rhythm.
    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp)
            Text(hint, color = Dim, fontSize = 9.5.sp, lineHeight = 11.sp)
        }
        TsSwitch(on, color, onChange)
    }
}

@Composable
private fun ControlRow(title: String, hint: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(112.dp)) {
            Text(title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(hint, color = Dim, fontSize = 10.5.sp, modifier = Modifier.padding(top = 1.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { control() }
    }
}

/* ═══════════════════════════ VELOCITY PAGE ═══════════════════════════ */

private const val CW = 280f
private const val CH = 200f
private const val PX0 = 12f
private const val PX1 = 268f
private const val PY0 = 10f
private const val PY1 = 176f

@Composable
private fun VelocityPage(vel: VelocitySettings, color: Color, onChange: (VelocitySettings) -> Unit) {
    var test by remember { mutableStateOf(64) }
    val out = VelocityLogic.outputOf(test, vel)
    Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // presets
        Column(Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SectionLabel("PRESET")
            VelocityPreset.values().forEach { p ->
                val on = vel.preset == p
                val shape = RoundedCornerShape(11.dp)
                Row(
                    Modifier
                        .fillMaxWidth().height(36.dp).clip(shape)
                        .background(if (on) Brush.verticalGradient(listOf(color.copy(alpha = 0.2f), color.copy(alpha = 0.08f))) else Brush.verticalGradient(listOf(white(0.06f), white(0.03f))))
                        .border(1.dp, if (on) color.copy(alpha = 0.55f) else white(0.06f), shape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onChange(vel.copy(preset = p, ys = if (p != VelocityPreset.Custom) VelocityLogic.presetYs(p) else vel.ys))
                        }
                        .padding(start = 9.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val ys = if (p == VelocityPreset.Custom) vel.ys else VelocityLogic.presetYs(p)
                    Canvas(Modifier.size(26.dp, 18.dp)) {
                        val path = Path()
                        for (i in 0..16) {
                            val x = i / 16f
                            val y = VelocityLogic.evalCurve(ys, x)
                            val px = 1f + x * (size.width - 2f); val py = 1f + (1f - y) * (size.height - 2f)
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        drawPath(path, if (on) color else Color(0xFF566075), style = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    Text(p.label, color = if (on) Color(0xFFF4F6FB) else LabelInk, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        }

        // curve panel: a sunk well
        val panelShape = RoundedCornerShape(16.dp)
        Column(
            Modifier
                .width(300.dp).fillMaxHeight().clip(panelShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF0C1119), Color(0xFF0F1520))))
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), endY = 14.dp.toPx()))
                    drawRoundRect(white(0.04f), cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(1.dp.toPx()))
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (vel.preset == VelocityPreset.Custom) "Custom curve" else "${vel.preset.label} curve", Modifier.weight(1f), color = SoftInk, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text("In ", color = Mute, fontSize = 11.5.sp, fontFamily = Mono)
                Text("$test", color = Ink, fontSize = 11.5.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                Text(" → Out ", color = Mute, fontSize = 11.5.sp, fontFamily = Mono)
                Text("$out", color = color, fontSize = 11.5.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
            }
            CurveCanvas(vel, color, test) { i, y ->
                val n = vel.ys.toMutableList(); n[i] = y.coerceIn(0f, 1f)
                onChange(vel.copy(ys = n, preset = VelocityPreset.Custom))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Test input", color = Mute, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.weight(1f)) { Slider("", test, 0, 127, 1, color, showHeader = false, onChange = { test = it }) }
            }
        }

        // parameters
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionLabel("SHAPE")
            Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Slider("Floor", vel.floor, 0, 126, 1, color, dim = vel.fixed, onChange = { onChange(vel.copy(floor = min(it, vel.ceiling - 1), preset = VelocityPreset.Custom)) })
                Slider("Ceiling", vel.ceiling, 1, 127, 1, color, dim = vel.fixed, onChange = { onChange(vel.copy(ceiling = max(it, vel.floor + 1), preset = VelocityPreset.Custom)) })
                Slider("Offset", vel.offset, -32, 32, 1, color, dim = vel.fixed, onChange = { onChange(vel.copy(offset = it, preset = VelocityPreset.Custom)) }) { if (it > 0) "+$it" else "$it" }
                Slider("Humanize", vel.humanize, 0, 24, 1, color, dim = vel.fixed, onChange = { onChange(vel.copy(humanize = it)) }) { "±$it" }
            }
            Spacer(Modifier.weight(1f))
            val cardShape = RoundedCornerShape(11.dp)
            Row(
                Modifier.fillMaxWidth().height(32.dp).clip(cardShape)
                    .background(Brush.verticalGradient(listOf(white(0.06f), white(0.03f))))
                    .border(1.dp, white(0.06f), cardShape)
                    .padding(start = 11.dp, end = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Fixed velocity", Modifier.weight(1f), color = Ink, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                if (vel.fixed) {
                    Stepper(vel.fixedValue, 1, 127, { onChange(vel.copy(fixedValue = it)) }) { "$it" }
                }
                TsSwitch(vel.fixed, color) { onChange(vel.copy(fixed = it)) }
            }
        }
    }
}

@Composable
private fun CurveCanvas(vel: VelocitySettings, color: Color, test: Int, onPoint: (Int, Float) -> Unit) {
    val measurer = rememberTextMeasurer()
    val axis = remember { TextStyle(color = Dim, fontSize = 8.sp, fontFamily = Mono) }
    var active by remember { mutableStateOf<Int?>(null) }
    val latest by rememberUpdatedState(vel)
    // `pointerInput(Unit)` is never restarted, so it would keep the first `onPoint`
    // closure and therefore the first `vel`: each new point then overwrote the
    // previous one with stale values. Reading it through rememberUpdatedState keeps
    // the gesture on the current curve.
    val currentOnPoint by rememberUpdatedState(onPoint)
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(CW / CH)
            .pointerInput(vel) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val s = size.width / CW
                        val ys = latest.ys
                        var best = -1
                        var bd = Float.MAX_VALUE
                        VelocityLogic.xs.forEachIndexed { i, x ->
                            val dx = down.position.x - (PX0 + x * (PX1 - PX0)) * s
                            val dy = down.position.y - (PY0 + (1f - ys[i]) * (PY1 - PY0)) * s
                            val d = dx * dx + dy * dy
                            if (d < bd) { bd = d; best = i }
                        }
                        if (best >= 0 && bd <= (14f * s) * (14f * s)) {
                            active = best
                            down.consume()
                            drag(down.id) { ch ->
                                ch.consume()
                                currentOnPoint(best, 1f - ((ch.position.y / s) - PY0) / (PY1 - PY0))
                            }
                            active = null
                        }
                    }
                }
            }
    ) {
        val s = size.width / CW
        fun px(x: Float) = (PX0 + x * (PX1 - PX0)) * s
        fun py(y: Float) = (PY0 + (1f - y) * (PY1 - PY0)) * s
        val grid = white(0.06f)
        listOf(0.25f, 0.5f, 0.75f).forEach { g ->
            drawLine(grid, Offset(PX0 * s, py(g)), Offset(PX1 * s, py(g)), 1f)
            drawLine(grid, Offset(px(g), PY0 * s), Offset(px(g), PY1 * s), 1f)
        }
        drawLine(white(0.12f), Offset(PX0 * s, PY1 * s), Offset(PX1 * s, PY1 * s), 1f)
        drawLine(white(0.12f), Offset(PX0 * s, PY0 * s), Offset(PX0 * s, PY1 * s), 1f)

        val curve = Path()
        for (i in 0..80) {
            val x = i / 80f
            val y = VelocityLogic.evalCurve(vel.ys, x)
            if (i == 0) curve.moveTo(px(x), py(y)) else curve.lineTo(px(x), py(y))
        }
        val area = Path().apply { addPath(curve); lineTo(px(1f), PY1 * s); lineTo(px(0f), PY1 * s); close() }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.38f), Color.Transparent), startY = PY0 * s, endY = PY1 * s))

        val ty = VelocityLogic.evalCurve(vel.ys, test / 127f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(3f * s, 3f * s))
        drawLine(color.copy(alpha = 0.35f), Offset(PX0 * s, py(ty)), Offset(PX1 * s, py(ty)), 1f, pathEffect = dash)
        drawLine(color.copy(alpha = 0.35f), Offset(px(test / 127f), PY0 * s), Offset(px(test / 127f), PY1 * s), 1f, pathEffect = dash)

        drawPath(curve, color.copy(alpha = 0.25f), style = Stroke(6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(curve, color, style = Stroke(2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))

        drawCircle(color.copy(alpha = 0.35f), 8f * s, Offset(px(test / 127f), py(ty)))
        drawCircle(Color(0xFFF4F6FB), 4.5f * s, Offset(px(test / 127f), py(ty)))

        VelocityLogic.xs.forEachIndexed { i, x ->
            val c = Offset(px(x), py(vel.ys[i]))
            drawCircle(Color(0xFF0F1520), (if (active == i) 6.2f else 4.8f) * s, c)
            drawCircle(color, (if (active == i) 6.2f else 4.8f) * s, c, style = Stroke(2f * s))
            drawCircle(Color(0xFFF4F6FB), 1.8f * s, c)
        }

        fun label(text: String, x: Float, y: Float, center: Boolean = false, end: Boolean = false) {
            val l = measurer.measure(text, axis)
            val ox = when { center -> l.size.width / 2f; end -> l.size.width.toFloat(); else -> 0f }
            drawText(l, topLeft = Offset(x * s - ox, y * s - l.size.height * 0.8f))
        }
        label("127", 8f, 12f); label("0", 8f, 180f)
        label("0", 12f, 192f); label("64", 134f, 192f, center = true); label("127", 268f, 192f, end = true)
    }
}

/* ═══════════════════════════ MIDI PAGE ═══════════════════════════ */

@Composable
private fun MidiPage(state: TrackSettingsState, a: TrackSettingsActions) {
    val color = state.color
    Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(236.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("LISTENS TO")
            state.devices.forEach { d ->
                val on = d.id == state.midiDeviceId
                val shape = RoundedCornerShape(13.dp)
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(shape)
                        .background(if (on) Brush.verticalGradient(listOf(color.copy(alpha = 0.2f), color.copy(alpha = 0.07f))) else Brush.verticalGradient(listOf(white(0.06f), white(0.03f))))
                        .border(1.dp, if (on) color.copy(alpha = 0.55f) else white(0.06f), shape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { a.onMidiDevice(d.id) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.size(16.dp).border(1.5.dp, if (on) color else white(0.22f), CircleShape), contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.size(8.dp).background(color, CircleShape))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(d.name, color = Color(0xFFF1F4FA), fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(d.subtitle, color = Mute, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("CHANNEL")
            ChannelButton("Omni · all channels", state.midiChannel == 0, color, Modifier.fillMaxWidth().height(34.dp), 12.5.sp, false) { a.onMidiChannel(0) }
            (1..16).chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { c -> ChannelButton("$c", state.midiChannel == c, color, Modifier.weight(1f).height(32.dp), 12.5.sp, true) { a.onMidiChannel(c) } }
                }
            }
        }
    }
}

@Composable
private fun ChannelButton(text: String, on: Boolean, color: Color, modifier: Modifier, size: androidx.compose.ui.unit.TextUnit, mono: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (mono) 10.dp else 11.dp)
    val bg by animateFloatAsState(if (on) 1f else 0f, tween(250), label = "ch-bg")
    Box(
        modifier.clip(shape)
            .background(lerpBrush(on, color, bg))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (on) Color(0xFF0F1726) else SoftInk, fontSize = size, fontWeight = FontWeight.ExtraBold, fontFamily = if (mono) Mono else FontFamily.Default)
    }
}

private fun lerpBrush(on: Boolean, color: Color, t: Float): Brush =
    if (on || t > 0.5f) Brush.verticalGradient(listOf(color, color))
    else Brush.verticalGradient(listOf(Color(0xFF2F3850), Color(0xFF252D40)))

/* ═══════════════════════════ NOTE RANGE PAGE ═══════════════════════════ */

private enum class Handle { Low, High }

@Composable
private fun RangePage(state: TrackSettingsState, a: TrackSettingsActions) {
    val color = state.color
    val measurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf<Handle?>(null) }
    var pressed by remember { mutableStateOf<Int?>(null) }
    val st by rememberUpdatedState(state)
    val lit = lerpColor(color, Color.White, 0.35f)
    val litDeep = lerpColor(color, Color.Black, 0.25f)

    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LimitCard("LOWEST", state.noteLow, color, Modifier.weight(1f), RangeKeys.MIN, state.noteHigh - 1) { a.onNoteRange(it, state.noteHigh) }
            LimitCard("HIGHEST", state.noteHigh, color, Modifier.weight(1f), state.noteLow + 1, RangeKeys.MAX) { a.onNoteRange(state.noteLow, it) }
        }

        Canvas(
            Modifier
                .padding(horizontal = 6.dp)
                .fillMaxWidth()
                .aspectRatio(RangeKeys.VW / RangeKeys.TOTAL_H)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val s = size.width / RangeKeys.VW
                            val x = down.position.x / s
                            val y = down.position.y / s
                            val xl = RangeKeys.cx(st.noteLow)
                            val xh = RangeKeys.cx(st.noteHigh)
                            val handle = if (y <= RangeKeys.BAR_H + 6f) {
                                val dl = abs(x - xl); val dh = abs(x - xh)
                                if (min(dl, dh) <= 17f) (if (dl <= dh) Handle.Low else Handle.High) else null
                            } else null
                            if (handle != null) {
                                down.consume()
                                dragging = handle
                                drag(down.id) { ch ->
                                    ch.consume()
                                    val m = RangeKeys.nearest(ch.position.x / s)
                                    if (handle == Handle.Low) a.onNoteRange(m.coerceIn(RangeKeys.MIN, st.noteHigh - 1), st.noteHigh)
                                    else a.onNoteRange(st.noteLow, m.coerceIn(st.noteLow + 1, RangeKeys.MAX))
                                }
                                dragging = null
                            } else {
                                val note = RangeKeys.noteAt(x, y)
                                if (note != null) {
                                    down.consume()
                                    if (abs(note - st.noteLow) <= abs(note - st.noteHigh)) a.onNoteRange(note.coerceIn(RangeKeys.MIN, st.noteHigh - 1), st.noteHigh)
                                    else a.onNoteRange(st.noteLow, note.coerceIn(st.noteLow + 1, RangeKeys.MAX))
                                    pressed = note
                                    scope.launch { delay(160); if (pressed == note) pressed = null }
                                }
                            }
                        }
                    }
                }
        ) {
            val s = size.width / RangeKeys.VW
            val px = s
            val xl = RangeKeys.cx(state.noteLow) * s
            val xh = RangeKeys.cx(state.noteHigh) * s
            val barH = RangeKeys.BAR_H * s
            val textStyle = TextStyle(color = Color(0xFFF1F4FA), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Mono)

            // keyboard frame
            drawRoundRect(Color(0xFF0A0D14), Offset(-6 * px, (RangeKeys.KY - 8) * s), Size((RangeKeys.VW + 12) * s, (RangeKeys.WH + 24) * s), CornerRadius(14 * px))
            drawRoundRect(white(0.05f), Offset(-6 * px, (RangeKeys.KY - 8) * s), Size((RangeKeys.VW + 12) * s, (RangeKeys.WH + 24) * s), CornerRadius(14 * px), style = Stroke(px))

            // white keys
            RangeKeys.whites.forEachIndexed { i, m ->
                val on = m in state.noteLow..state.noteHigh
                val dy = if (pressed == m) 1.5f * px else 0f
                val x = (i * RangeKeys.WW + 0.5f) * s
                val kw = (RangeKeys.WW - 1f) * s
                val ky = RangeKeys.KY * s + dy
                val kh = RangeKeys.WH * s
                val r = CornerRadius(3 * px)
                drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(x, ky + 1.6f * px), Size(kw, kh), r)
                drawRoundRect(
                    if (on) Brush.verticalGradient(listOf(lit, color, litDeep), startY = ky, endY = ky + kh)
                    else Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F3F7), Color(0xFFD9DDE6)), startY = ky, endY = ky + kh),
                    Offset(x, ky), Size(kw, kh), r
                )
                drawRoundRect(Color.Black.copy(alpha = 0.28f), Offset(x, ky), Size(kw, kh), r, style = Stroke(0.6f * px))
                drawRoundRect(Color.Black.copy(alpha = 0.05f), Offset(x + px, ky + kh - 16 * px), Size(kw - 2 * px, 16 * px), CornerRadius(2 * px))
            }
            // black keys
            RangeKeys.blacks.forEach { m ->
                val on = m in state.noteLow..state.noteHigh
                val dy = if (pressed == m) 1.2f * px else 0f
                val c = RangeKeys.cx(m) * s
                val bw = RangeKeys.BW * s
                val ky = RangeKeys.KY * s + dy
                val bh = RangeKeys.BH * s
                val r = CornerRadius(2.5f * px)
                drawRoundRect(Color.Black.copy(alpha = 0.7f), Offset(c - bw / 2f, ky + 3 * px), Size(bw, bh), r)
                drawRoundRect(
                    if (on) Brush.verticalGradient(listOf(lit, color, litDeep), startY = ky, endY = ky + bh)
                    else Brush.verticalGradient(listOf(Color(0xFF2A3040), Color(0xFF141823), Color(0xFF07090E)), startY = ky, endY = ky + bh),
                    Offset(c - bw / 2f, ky), Size(bw, bh), r
                )
                drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(c - bw / 2f, ky), Size(bw, bh), r, style = Stroke(0.6f * px))
                drawRoundRect(Color.White.copy(alpha = if (on) 0.35f else 0.14f), Offset(c - bw / 2f + 1.5f * px, ky + 1.5f * px), Size(bw - 3 * px, 3 * px), CornerRadius(1.2f * px))
            }

            // range bar: full width, the selected span in the track color
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF1C2231), Color(0xFF141A26)), endY = barH), Offset.Zero, Size(RangeKeys.VW * s, barH), CornerRadius(14 * px))
            drawRoundRect(white(0.06f), Offset.Zero, Size(RangeKeys.VW * s, barH), CornerRadius(14 * px), style = Stroke(px))
            val spanW = max(4 * px, xh - xl)
            drawRoundRect(Brush.verticalGradient(listOf(lit, color), endY = barH), Offset(xl, 0f), Size(spanW, barH), CornerRadius(14 * px))
            drawRoundRect(Color.White.copy(alpha = 0.14f), Offset(xl, 0f), Size(spanW, 14 * px), CornerRadius(7 * px))

            fun centered(text: String, cx: Float, cy: Float, style: TextStyle) {
                val l = measurer.measure(text, style)
                drawText(l, topLeft = Offset(cx - l.size.width / 2f, cy - l.size.height / 2f))
            }
            centered(state.trackName, (xl + xh) / 2f, barH / 2f, TextStyle(color = Color(0xFF0F1726), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold))
            // note chips near each handle
            listOf(xl + 10 * px to state.noteLow, xh - 54 * px to state.noteHigh).forEach { (x, n) ->
                drawRoundRect(Color(0xFF0F1726).copy(alpha = 0.78f), Offset(x, barH / 2f - 9 * px), Size(44 * px, 18 * px), CornerRadius(6 * px))
                centered(RangeKeys.name(n), x + 22 * px, barH / 2f, textStyle)
            }
            // handles: white discs with a dark dot
            listOf(Handle.Low to xl, Handle.High to xh).forEach { (h, x) ->
                val r = (if (dragging == h) 11.5f else 10f) * px
                drawCircle(Color.Black.copy(alpha = 0.35f), r, Offset(x, barH / 2f + 1.5f * px))
                drawCircle(Color.White, r, Offset(x, barH / 2f))
                drawCircle(Color(0xFF0F1726), 3.6f * px, Offset(x, barH / 2f))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Drag a handle, or tap a key to move the nearest limit", color = Mute, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("${state.noteHigh - state.noteLow + 1} notes", color = SoftInk, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LimitCard(label: String, note: Int, color: Color, modifier: Modifier, min: Int, max: Int, onChange: (Int) -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier.height(46.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(white(0.065f), white(0.03f))))
            .border(1.dp, white(0.06f), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Dim, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
            Text(RangeKeys.name(note), color = color, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Mono)
        }
        Stepper(note, min, max, onChange) { "" }
    }
}

/* ═══════════════════════════ CONTROLS (replace with the Settings page components when equivalent) ═══════════════════════════ */

@Composable
private fun SectionLabel(text: String) {
    Text(text, Modifier.height(20.dp), color = Dim, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
}

@Composable
private fun TsSwitch(on: Boolean, color: Color, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (on) 17.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "sw")
    Box(
        Modifier
            .size(38.dp, 21.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (on) color else white(0.14f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!on) }
    ) {
        Box(Modifier.offset(x = 2.dp + x, y = 2.dp).size(17.dp).background(if (on) Color(0xFF0F1726) else Color(0xFFC7CFDD), CircleShape))
    }
}

@Composable
private fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit, fmt: (Int) -> String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StepButton("−") { onChange((value - 1).coerceIn(min, max)) }
        Text(fmt(value), Modifier.width(48.dp), color = Color(0xFFF1F4FA), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Mono, textAlign = TextAlign.Center)
        StepButton("+") { onChange((value + 1).coerceIn(min, max)) }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(26.dp).clip(RoundedCornerShape(9.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3A4258), Color(0xFF2E3549))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun <T> Segmented(options: List<T>, value: T, onChange: (T) -> Unit, color: Color, modifier: Modifier = Modifier, label: (T) -> String) {
    val idx = options.indexOf(value).coerceAtLeast(0)
    BoxWithConstraints(modifier.clip(RoundedCornerShape(11.dp)).background(Color(0xFF0E131C)).padding(3.dp)) {
        val segW = maxWidth / options.size
        val x by animateDpAsState(segW * idx, spring(dampingRatio = 0.65f, stiffness = 420f), label = "seg")
        Box(Modifier.offset(x = x).width(segW).height(26.dp).background(Brush.verticalGradient(listOf(Color(0xFF3A4663), Color(0xFF2B3650))), RoundedCornerShape(8.dp)))
        Row {
            options.forEach { o ->
                Box(
                    Modifier.width(segW).height(26.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(o) },
                    contentAlignment = Alignment.Center
                ) { Text(label(o), color = if (o == value) Color(0xFFF1F4FA) else Color(0xFF8690A4), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

private fun Modifier.fractionPointer(enabled: Boolean, onFraction: (Float) -> Unit): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            onFraction((down.position.x / size.width).coerceIn(0f, 1f))
            down.consume()
            drag(down.id) { ch -> ch.consume(); onFraction((ch.position.x / size.width).coerceIn(0f, 1f)) }
        }
    }
}

@Composable
private fun Slider(
    label: String, value: Int, min: Int, max: Int, step: Int, color: Color,
    dim: Boolean = false, showHeader: Boolean = true, onChange: (Int) -> Unit, fmt: (Int) -> String = { "$it" }
) {
    val frac = ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().alpha(if (dim) 0.45f else 1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (showHeader) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(label, color = LabelInk, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Text(fmt(value), color = Ink, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.ExtraBold)
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(18.dp)
                .fractionPointer(!dim) { f -> onChange((min + (f * (max - min) / step).roundToInt() * step).coerceIn(min, max)) }
        ) {
            Box(Modifier.fillMaxWidth().offset(y = 7.dp).height(4.dp).background(Color(0xFF0E131C), RoundedCornerShape(2.dp)))
            Box(Modifier.fillMaxWidth(frac).offset(y = 7.dp).height(4.dp).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.4f), color)), RoundedCornerShape(2.dp)))
            Box(
                Modifier.offset(x = maxWidth * frac - 7.dp, y = 2.dp).size(14.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF6E7799), Color(0xFF3F4766))), RoundedCornerShape(5.dp))
                    .border(1.dp, white(0.16f), RoundedCornerShape(5.dp))
            )
        }
    }
}

/* ── tiny drawn icons ── */

private enum class Icon { Back, Close, Chevron, Reset }

@Composable
private fun RoundIconButton(icon: Icon, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2F3850), Color(0xFF252D40))))
            .border(1.dp, white(0.1f), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon13(icon, Color(0xFFE3E8F2), 15.dp) }
}

@Composable
private fun Icon13(icon: Icon, tint: Color, size: Dp = 13.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val stroke = Stroke(2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), 2.2f * s, StrokeCap.Round)
        when (icon) {
            Icon.Back -> { line(19f, 12f, 5f, 12f); line(11f, 6f, 5f, 12f); line(5f, 12f, 11f, 18f) }
            Icon.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Icon.Chevron -> { line(9f, 6f, 15f, 12f); line(15f, 12f, 9f, 18f) }
            Icon.Reset -> {
                drawArc(tint, 0f, 312f, false, Offset(4f * s, 4f * s), Size(16f * s, 16f * s), style = stroke)
                line(20f, 4f, 20f, 8.5f); line(20f, 8.5f, 15.5f, 8.5f)
            }
        }
    }
}

/* ═══════════════════════════ PREVIEW ═══════════════════════════ */

@Preview(widthDp = 891, heightDp = 411, backgroundColor = 0xFF0F141D, showBackground = true)
@Composable
private fun TrackSettingsPreview() {
    var screen by remember { mutableStateOf(TsPage.Track) }
    var s by remember {
        mutableStateOf(
            TrackSettingsState(
                trackName = "Track 4", soundName = "Organ B3 Fast", color = Color(0xFFA18AE0),
                devices = listOf(
                    TsDeviceItem("all", "All devices", "Listen to everything connected"),
                    TsDeviceItem("kb", "Keystation 88", "USB · connected"),
                    TsDeviceItem("pad", "Launchkey 61", "USB · connected")
                ),
                midiDeviceId = "all"
            )
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TrackSettingsWindow(
            state = s, screen = screen,
            actions = TrackSettingsActions(
                onNavigate = { screen = it }, onClose = {},
                onVelocityChange = { s = s.copy(velocity = it) },
                onMidiDevice = { s = s.copy(midiDeviceId = it) }, onMidiChannel = { s = s.copy(midiChannel = it) },
                onNoteRange = { lo, hi -> s = s.copy(noteLow = lo, noteHigh = hi) },
                onTranspose = { s = s.copy(transpose = it) }, onOctave = { s = s.copy(octave = it) },
                onPitchBendRange = { s = s.copy(pitchBendRange = it) },
                onDefaultVolume = { s = s.copy(defaultVolume = it) }, onDefaultPan = { s = s.copy(defaultPan = it) },
                onSustain = { s = s.copy(sustain = it) }, onAftertouch = { s = s.copy(aftertouch = it) },
                onProgramChange = { s = s.copy(programChange = it) }
            )
        )
    }
}
