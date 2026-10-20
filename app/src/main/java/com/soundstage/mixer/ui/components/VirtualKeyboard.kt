package com.soundstage.mixer.ui.components

/*
 * VirtualKeyboard — faithful to VirtualKeyboardPreview.jsx (approved).
 *
 * One container: pitch bend (left), layer lines (one per ACTIVE track), keyboard C1…C8.
 *  - The lines are thin at rest. A tap unfolds them (staggered spring), a quiet 5 s folds them back.
 *  - Drag a handle to change a limit, drag the bar to slide the range. While a line moves, a flat halo
 *    of its color lights the keys of the zone (no outline). The halo disappears on release.
 *  - The container NEVER changes color. Only the keyboard (keys) follows the theme.
 *  - Pressed keys: simple theme color by default; LED style adds a short glow (setting).
 *
 * Stateless UI: everything comes from parameters; only transient gesture state is local.
 * Pure logic lives in KeyboardLogic. No dependency on the audio engine.
 *
 * IMPORTANT (lesson from the Setlists cards): the container background is OPAQUE and the shadow is
 * applied before it. Never put Modifier.shadow under a translucent fill.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.soundstage.mixer.ui.theme.AppColors
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/* ═══════════════════════════ MODEL ═══════════════════════════ */

/** One line = one ACTIVE track. low/high are MIDI notes (C1 = 24 … C8 = 108). */
@Immutable
data class KeyboardLine(
    val id: String,
    val name: String,
    val color: Color,
    val low: Int,
    val high: Int
)

enum class KeyboardPressStyle { Color, Led }

/** A keyboard theme. It only styles the KEYS. The container color is fixed (KeyboardChrome). */
@Immutable
data class KeyboardTheme(
    val id: String,
    val name: String,
    val whiteTop: Color, val whiteMid: Color, val whiteBottom: Color,
    val blackTop: Color, val blackMid: Color, val blackBottom: Color,
    val edge: Color,
    val label: Color,
    val press: Color,
    val pressBlack: Color,
    /** Opacity of the thin highlight on top of the white keys (dark themes need it much lower). */
    val highlight: Float = 0.8f
)

/** Fixed colors of the container. They do NOT depend on the theme; taken from the app palette. */
@Immutable
data class KeyboardChrome(
    val frame: Color = AppColors.PanelBottom,
    val well: Color = AppColors.Well,
    val accent: Color = AppColors.Accent
)

object KeyboardThemes {
    private fun c(hex: Long) = Color(0xFF000000 or hex)

    val Classic = KeyboardTheme("classic", "Classic",
        c(0xFFFFFF), c(0xF1F3F7), c(0xD5D9E2), c(0x2D3240), c(0x14171F), c(0x06080C),
        edge = c(0x9AA2B1), label = c(0x7B8396), press = c(0x8FB2E8), pressBlack = c(0x3F73C9), highlight = 0.8f)
    val Blue = KeyboardTheme("blue", "Blue",
        c(0xF7FAFF), c(0xE4ECF8), c(0xC9D6EA), c(0x1E4276), c(0x12294D), c(0x081730),
        edge = c(0x8FA3C4), label = c(0x6F87AE), press = c(0x3D8BFF), pressBlack = c(0x2A6FE0))
    val Cream = KeyboardTheme("cream", "Cream",
        c(0xFFF8E6), c(0xEFDDB8), c(0xD6C196), c(0x4C3B2C), c(0x2B2017), c(0x140E09),
        edge = c(0xB79F78), label = c(0x8E7A5A), press = c(0xF5A524), pressBlack = c(0xD98A0F))
    val Red = KeyboardTheme("red", "Red",
        c(0xFFFFFF), c(0xF6E9EA), c(0xE1C9CC), c(0x8E202D), c(0x5C111B), c(0x2E0910),
        edge = c(0xC9A0A4), label = c(0xA9777C), press = c(0xE5313F), pressBlack = c(0xC71F2D))
    val Dark = KeyboardTheme("dark", "Dark",
        c(0x5A5F6E), c(0x3D414D), c(0x2B2E37), c(0x131418), c(0x0A0B0E), c(0x030304),
        edge = c(0x0A0B0E), label = c(0x8B91A0), press = c(0xFF3B5C), pressBlack = c(0xE0284A), highlight = 0.2f)
    val Violet = KeyboardTheme("violet", "Violet",
        c(0xF8F4FF), c(0xE5DDF7), c(0xCFC3EA), c(0x3E2D6E), c(0x251848), c(0x120B27),
        edge = c(0xA99BC9), label = c(0x8878B0), press = c(0x9A5CFF), pressBlack = c(0x7C3DE0))
    val Emerald = KeyboardTheme("emerald", "Emerald",
        c(0xF4FCF8), c(0xDAEFE3), c(0xC0DDCC), c(0x14513F), c(0x0B2F25), c(0x05150F),
        edge = c(0x9CBDAE), label = c(0x6E9886), press = c(0x22C98B), pressBlack = c(0x12A872))
    val Sunset = KeyboardTheme("sunset", "Sunset",
        c(0xFFF6EE), c(0xF4DCCB), c(0xDDBDA6), c(0x552424), c(0x331313), c(0x180808),
        edge = c(0xC4A08C), label = c(0xA07C68), press = c(0xFF6B3D), pressBlack = c(0xE0522A))
    val Graphite = KeyboardTheme("graphite", "Graphite",
        c(0xEEF0F4), c(0xCDD2DA), c(0xB2B8C3), c(0x3C414C), c(0x1F2229), c(0x0C0E12),
        edge = c(0x8C93A1), label = c(0x707888), press = c(0x7DD3FC), pressBlack = c(0x38BDF8), highlight = 0.6f)

    /** Display order. Classic is the default. */
    val all: List<KeyboardTheme> = listOf(Classic, Blue, Cream, Red, Dark, Violet, Emerald, Sunset, Graphite)
    fun byId(id: String?): KeyboardTheme = all.firstOrNull { it.id == id } ?: Classic
}

class VirtualKeyboardActions(
    val onExpandedChange: (Boolean) -> Unit,
    val onLineRangeChange: (lineId: String, low: Int, high: Int) -> Unit,
    val onKeyDown: (note: Int) -> Unit,
    val onKeyUp: (note: Int) -> Unit,
    /** -1f … +1f. Called while dragging and during the spring back to 0. */
    val onPitchBend: (Float) -> Unit
) {
    companion object {
        val NoOp = VirtualKeyboardActions({}, { _, _, _ -> }, {}, {}, {})
    }
}

/* ═══════════════════════════ PURE LOGIC ═══════════════════════════ */

/**
 * Boundary adapter between the app's note-name storage ("C#4") and the MIDI numbers
 * this UI works with. Kept here so the keyboard file stays self-contained.
 */
object VkbMidi {
    const val NOTE_MIN = 24
    const val NOTE_MAX = 108

    private val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val regex = "([A-Ga-g]#?)(-?\\d+)".toRegex()

    fun midiToKt(midi: Int): String {
        val m = midi.coerceIn(0, 127)
        return "${names[m % 12]}${m / 12 - 1}"
    }

    fun ktToMidi(name: String): Int? {
        val match = regex.matchEntire(name.trim()) ?: return null
        val semitone = when (match.groupValues[1].uppercase()) {
            "C" -> 0; "C#" -> 1
            "D" -> 2; "D#" -> 3
            "E" -> 4
            "F" -> 5; "F#" -> 6
            "G" -> 7; "G#" -> 8
            "A" -> 9; "A#" -> 10
            "B" -> 11
            else -> return null
        }
        val octave = match.groupValues[2].toIntOrNull() ?: return null
        return (octave + 1) * 12 + semitone
    }
}

object KeyboardLogic {
    const val NOTE_MIN = 24     // C1
    const val NOTE_MAX = 108    // C8

    private val blackPitchClasses = setOf(1, 3, 6, 8, 10)
    val notes: List<Int> = (NOTE_MIN..NOTE_MAX).toList()
    fun isBlack(note: Int) = (note % 12) in blackPitchClasses
    val whites: List<Int> = notes.filter { !isBlack(it) }
    val blacks: List<Int> = notes.filter { isBlack(it) }
    val whiteCount: Int = whites.size                       // 50

    private val whiteIndex: Map<Int, Int> = whites.withIndex().associate { it.value to it.index }
    private val whitesBefore: Map<Int, Int> = notes.associateWith { n -> whites.count { it < n } }

    fun whiteIndexOf(note: Int): Int = whiteIndex[note] ?: -1
    fun whitesBeforeOf(note: Int): Int = whitesBefore[note] ?: 0

    /** Horizontal center of a key, as a fraction (0..1) of the keyboard width. */
    fun centerFraction(note: Int): Float =
        if (isBlack(note)) whitesBeforeOf(note) / whiteCount.toFloat()
        else (whiteIndexOf(note) + 0.5f) / whiteCount

    fun barLeftFraction(low: Int): Float = centerFraction(low) - 0.5f / whiteCount
    fun barRightFraction(high: Int): Float = centerFraction(high) + 0.5f / whiteCount

    fun nearestNote(fraction: Float): Int = notes.minByOrNull { abs(centerFraction(it) - fraction) } ?: NOTE_MIN

    /** New low limit while dragging the LOW handle. totalDx = finger travel since the drag started. */
    fun draggedLow(origLow: Int, high: Int, totalDx: Float, widthPx: Float, insetPx: Float): Int {
        val ww = widthPx / whiteCount
        val handleX = barLeftFraction(origLow) * widthPx + insetPx + totalDx
        val target = nearestNote((handleX - insetPx + ww / 2f) / widthPx)
        return target.coerceIn(NOTE_MIN, high - 1)
    }

    /** New high limit while dragging the HIGH handle. */
    fun draggedHigh(low: Int, origHigh: Int, totalDx: Float, widthPx: Float, insetPx: Float): Int {
        val ww = widthPx / whiteCount
        val handleX = barRightFraction(origHigh) * widthPx - insetPx + totalDx
        val target = nearestNote((handleX + insetPx - ww / 2f) / widthPx)
        return target.coerceIn(low + 1, NOTE_MAX)
    }

    /** Slides the whole range, keeping its width. */
    fun shifted(origLow: Int, origHigh: Int, totalDx: Float, widthPx: Float): Pair<Int, Int> {
        val width = origHigh - origLow
        val pxPerSemitone = widthPx / (NOTE_MAX - NOTE_MIN)
        val low = (origLow + (totalDx / pxPerSemitone).roundToInt()).coerceIn(NOTE_MIN, NOTE_MAX - width)
        return low to (low + width)
    }

    /** Hit test: black keys first (upper part), then white keys. */
    fun noteAt(x: Float, y: Float, widthPx: Float, heightPx: Float, blackHeightPx: Float): Int? {
        if (x < 0f || x > widthPx || y < 0f || y > heightPx) return null
        val ww = widthPx / whiteCount
        val bw = ww * 0.6f
        if (y <= blackHeightPx) {
            for (m in blacks) {
                val c = whitesBeforeOf(m) * ww
                if (x >= c - bw / 2f && x <= c + bw / 2f) return m
            }
        }
        return whites[(x / ww).toInt().coerceIn(0, whiteCount - 1)]
    }

    private val names = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    fun noteName(note: Int): String = names[note % 12] + (note / 12 - 1)
}

/* ═══════════════════════════ DIMENSIONS ═══════════════════════════ */

private val KeysHeight = 50.dp
private val BlackHeight = 31.dp
private val RestLineH = 2.6.dp
private val OpenLineH = 29.dp
private val RestGap = 0.5.dp
private val OpenGap = 3.2.dp
private val RestZone = 5.dp
private val OpenZone = 25.dp
private val PitchRestH = 46.dp
private val PitchOpenH = 74.dp
private val HandleInset = 12.dp

/* ═══════════════════════════ CONTAINER ═══════════════════════════ */

@Composable
fun VirtualKeyboardContainer(
    lines: List<KeyboardLine>,
    expanded: Boolean,
    pressedNotes: Set<Int>,
    theme: KeyboardTheme,
    pressStyle: KeyboardPressStyle,
    actions: VirtualKeyboardActions,
    modifier: Modifier = Modifier,
    chrome: KeyboardChrome = KeyboardChrome()
) {
    // one staggered spring per line: they unfold in order, and fold back in reverse order
    val progress = lines.indices.map { rememberLineProgress(expanded, it, lines.size) }
    val p0 = (progress.firstOrNull() ?: if (expanded) 1f else 0f)
    val p0c = p0.coerceIn(0f, 1f)
    var draggingId by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }

    // folds back by itself after 5 quiet seconds (any touch inside the container restarts the delay)
    LaunchedEffect(expanded, draggingId, tick) {
        if (expanded && draggingId == null) {
            delay(5000)
            actions.onExpandedChange(false)
        }
    }

    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(lerp(4.dp, 14.dp, p0c), shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(chrome.frame) // OPAQUE and fixed: the container does not follow the theme
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        tick++
                    }
                }
            }
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        PitchBendStrip(
            height = lerp(PitchRestH, PitchOpenH, p0c),
            theme = theme,
            chrome = chrome,
            onValue = actions.onPitchBend
        )

        val density = LocalDensity.current
        BoxWithConstraints(Modifier.weight(1f)) {
            val widthPx = with(density) { maxWidth.toPx() }
            Column {
                LinesArea(
                    lines = lines,
                    progress = progress,
                    expanded = expanded,
                    widthPx = widthPx,
                    draggingId = draggingId,
                    onDragging = { id, on -> draggingId = if (on) id else if (draggingId == id) null else draggingId },
                    actions = actions
                )
                Box(Modifier.fillMaxWidth().height(KeysHeight)) {
                    KeyboardKeys(
                        modifier = Modifier.fillMaxSize(),
                        theme = theme,
                        pressStyle = pressStyle,
                        pressedNotes = pressedNotes,
                        onKeyDown = actions.onKeyDown,
                        onKeyUp = actions.onKeyUp
                    )
                    // selection halo: only while a line is being moved, flat color, no outline
                    lines.forEach { line ->
                        key(line.id) {
                            val a by animateFloatAsState(if (draggingId == line.id) 1f else 0f, tween(160), label = "halo")
                            if (a > 0.01f) {
                                val l = KeyboardLogic.barLeftFraction(line.low) * widthPx
                                val r = KeyboardLogic.barRightFraction(line.high) * widthPx
                                Box(
                                    Modifier
                                        .offset { IntOffset(l.roundToInt(), 0) }
                                        .width(with(density) { (r - l).toDp() })
                                        .fillMaxHeight()
                                        .background(line.color.copy(alpha = 0.4f * a), RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberLineProgress(expanded: Boolean, index: Int, count: Int): Float {
    val anim = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        delay(if (expanded) index * 45L else (count - 1 - index) * 30L)
        anim.animateTo(if (expanded) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 360f))
    }
    return anim.value
}

/* ═══════════════════════════ LINES ═══════════════════════════ */

@Composable
private fun LinesArea(
    lines: List<KeyboardLine>,
    progress: List<Float>,
    expanded: Boolean,
    widthPx: Float,
    draggingId: String?,
    onDragging: (String, Boolean) -> Unit,
    actions: VirtualKeyboardActions
) {
    val p0 = (progress.firstOrNull() ?: 0f)
    val p0c = p0.coerceIn(0f, 1f)
    val tickAlpha = 0.5f + 0.5f * p0c
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = lerp(3.dp, 2.dp, p0c))
            .drawBehind {
                // octave marks, behind the lines (left edge of every C)
                val ww = size.width / KeyboardLogic.whiteCount
                KeyboardLogic.whites.filter { it % 12 == 0 }.forEach { m ->
                    val x = KeyboardLogic.whiteIndexOf(m) * ww
                    drawLine(Color.White.copy(alpha = 0.1f * tickAlpha), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !expanded) {
                actions.onExpandedChange(true)
            }
    ) {
        Column {
            lines.forEachIndexed { i, line ->
                if (i > 0) Spacer(Modifier.height(lerp(RestGap, OpenGap, progress[i].coerceIn(0f, 1.2f))))
                key(line.id) {
                    LayerLine(
                        line = line,
                        p = progress[i],
                        expanded = expanded,
                        widthPx = widthPx,
                        active = draggingId == line.id,
                        onDragging = { on -> onDragging(line.id, on) },
                        onRange = { lo, hi -> actions.onLineRangeChange(line.id, lo, hi) }
                    )
                }
            }
            Spacer(Modifier.height(lerp(RestZone, OpenZone, p0.coerceIn(0f, 1.2f))))
        }
        // grip: tap to fold
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-5).dp)
                .size(width = 44.dp, height = 12.dp)
                .alpha(p0c)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = expanded) {
                    actions.onExpandedChange(false)
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(width = 30.dp, height = 3.5.dp).background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun LayerLine(
    line: KeyboardLine,
    p: Float,
    expanded: Boolean,
    widthPx: Float,
    active: Boolean,
    onDragging: (Boolean) -> Unit,
    onRange: (Int, Int) -> Unit
) {
    val density = LocalDensity.current
    val current by rememberUpdatedState(line)
    val insetPx = with(density) { HandleInset.toPx() }
    val h = lerp(RestLineH, OpenLineH, p).coerceAtLeast(1.dp)
    val radius = lerp(2.dp, 10.dp, p.coerceIn(0f, 1f))
    val l = KeyboardLogic.barLeftFraction(line.low) * widthPx
    val r = KeyboardLogic.barRightFraction(line.high) * widthPx
    val chipAlpha = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
    val handleScale = ((p - 0.55f) / 0.45f).coerceIn(0f, 1.15f)
    val lift by animateFloatAsState(if (active) -1f else 0f, tween(150), label = "lift")
    val origin = remember { IntArray(2) } // low / high at the start of a handle drag

    Box(Modifier.fillMaxWidth().height(h)) {
        // the bar: flat track color, no gradient. Drag the body to slide the whole range.
        Box(
            Modifier
                .offset { IntOffset(l.roundToInt(), (lift * 1.dp.toPx()).roundToInt()) }
                .width(with(density) { (r - l).toDp() })
                .fillMaxHeight()
                .background(line.color, RoundedCornerShape(radius))
                .pointerInput(expanded, widthPx) {
                    if (!expanded) return@pointerInput
                    var total = 0f
                    var oLow = 0
                    var oHigh = 0
                    detectDragGestures(
                        onDragStart = { total = 0f; oLow = current.low; oHigh = current.high; onDragging(true) },
                        onDrag = { change, drag ->
                            change.consume()
                            total += drag.x
                            val (nl, nh) = KeyboardLogic.shifted(oLow, oHigh, total, widthPx)
                            onRange(nl, nh)
                        },
                        onDragEnd = { onDragging(false) },
                        onDragCancel = { onDragging(false) }
                    )
                }
        ) {
            Row(
                Modifier.fillMaxSize().alpha(chipAlpha),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NoteChip(KeyboardLogic.noteName(line.low))
                Text(
                    line.name,
                    Modifier.padding(horizontal = 8.dp),
                    color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, softWrap = false
                )
                NoteChip(KeyboardLogic.noteName(line.high))
            }
        }
        // handles: white disc with a dark dot, inset 12 dp from each end of the bar
        LineHandle(
            centerX = l + insetPx, heightDp = h, scale = handleScale, active = active, expanded = expanded, widthPx = widthPx,
            onDragStart = { origin[0] = current.low; origin[1] = current.high; onDragging(true) },
            onDragEnd = { onDragging(false) },
            onDrag = { total -> onRange(KeyboardLogic.draggedLow(origin[0], current.high, total, widthPx, insetPx), current.high) }
        )
        LineHandle(
            centerX = r - insetPx, heightDp = h, scale = handleScale, active = active, expanded = expanded, widthPx = widthPx,
            onDragStart = { origin[0] = current.low; origin[1] = current.high; onDragging(true) },
            onDragEnd = { onDragging(false) },
            onDrag = { total -> onRange(current.low, KeyboardLogic.draggedHigh(current.low, origin[1], total, widthPx, insetPx)) }
        )
    }
}

@Composable
private fun NoteChip(text: String) {
    Box(
        Modifier.background(Color(0xFF0A0E16).copy(alpha = 0.82f), RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 1.dp)
    ) {
        Text(text, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

@Composable
private fun LineHandle(
    centerX: Float,
    heightDp: Dp,
    scale: Float,
    active: Boolean,
    expanded: Boolean,
    widthPx: Float,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (totalDx: Float) -> Unit
) {
    Box(
        Modifier
            .offset {
                IntOffset(
                    (centerX - 11.dp.toPx()).roundToInt(),
                    ((heightDp.toPx() - 22.dp.toPx()) / 2f).roundToInt()
                )
            }
            .size(22.dp)
            .scale(scale)
            .alpha(scale.coerceIn(0f, 1f))
            .pointerInput(expanded, widthPx) {
                if (!expanded) return@pointerInput
                var total = 0f
                detectDragGestures(
                    onDragStart = { total = 0f; onDragStart() },
                    onDrag = { change, drag -> change.consume(); total += drag.x; onDrag(total) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(if (active) 14.dp else 12.dp).background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) { Box(Modifier.size(5.dp).background(Color(0xFF0F1726), CircleShape)) }
    }
}

/* ═══════════════════════════ PITCH BEND ═══════════════════════════ */

@Composable
private fun PitchBendStrip(height: Dp, theme: KeyboardTheme, chrome: KeyboardChrome, onValue: (Float) -> Unit) {
    val v = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(10.dp)
    fun setFromY(y: Float, h: Float) {
        val value = (1f - (y / h) * 2f).coerceIn(-1f, 1f)
        scope.launch { v.snapTo(value); onValue(value) }
    }
    fun release() {
        scope.launch { v.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 380f)) { onValue(value) } }
    }
    Canvas(
        Modifier
            .width(34.dp)
            .height(height)
            .clip(shape)
            .background(chrome.well)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { setFromY(it.y, size.height.toFloat()) },
                    onVerticalDrag = { change, _ -> change.consume(); setFromY(change.position.y, size.height.toFloat()) },
                    onDragEnd = { release() },
                    onDragCancel = { release() }
                )
            }
    ) {
        val pad = 11.dp.toPx()
        val travel = size.height / 2f - pad
        val cx = size.width / 2f
        val cy = size.height / 2f - v.value * travel
        // inner shadow of the well
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), endY = 14.dp.toPx()))
        // rail
        drawRoundRect(Color.White.copy(alpha = 0.1f), Offset(cx - 1.dp.toPx(), pad), Size(2.dp.toPx(), size.height - pad * 2), CornerRadius(1.dp.toPx()))
        // fill from the center to the handle, in the theme color
        drawRoundRect(
            theme.press,
            Offset(cx - 1.dp.toPx(), minOf(cy, size.height / 2f)),
            Size(2.dp.toPx(), abs(size.height / 2f - cy)),
            CornerRadius(1.dp.toPx())
        )
        // handle: white bar, 24 × 11 dp
        val hw = 24.dp.toPx(); val hh = 11.dp.toPx()
        val top = cy - hh / 2f
        drawRoundRect(Color.Black.copy(alpha = 0.4f), Offset(cx - hw / 2f, top + 2.dp.toPx()), Size(hw, hh), CornerRadius(6.dp.toPx()))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White, Color(0xFFCED3DE)), startY = top, endY = top + hh),
            Offset(cx - hw / 2f, top), Size(hw, hh), CornerRadius(6.dp.toPx())
        )
        drawLine(Color.Black.copy(alpha = 0.25f), Offset(cx - hw / 2f + 5.dp.toPx(), cy), Offset(cx + hw / 2f - 5.dp.toPx(), cy), 1.dp.toPx())
    }
}

/* ═══════════════════════════ KEYBOARD (NeoTune-style relief) ═══════════════════════════ */

@Composable
fun KeyboardKeys(
    modifier: Modifier,
    theme: KeyboardTheme,
    pressStyle: KeyboardPressStyle,
    pressedNotes: Set<Int>,
    onKeyDown: (Int) -> Unit,
    onKeyUp: (Int) -> Unit
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = remember(theme.label) {
        TextStyle(color = theme.label.copy(alpha = 0.9f), fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
    val labels = remember(theme.label) {
        KeyboardLogic.notes.filter { it % 12 == 0 }.associateWith { measurer.measure(KeyboardLogic.noteName(it), labelStyle) }
    }
    val blackHeightPx = with(LocalDensity.current) { BlackHeight.toPx() }
    val led = pressStyle == KeyboardPressStyle.Led
    val downCb by rememberUpdatedState(onKeyDown)
    val upCb by rememberUpdatedState(onKeyUp)

    Canvas(
        modifier.pointerInput(blackHeightPx) {
            // every finger tracks its own key: chords and glissandi work, a key is released only when no finger holds it
            val held = mutableMapOf<PointerId, Int>()
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { ch ->
                        val prev = held[ch.id]
                        if (ch.pressed) {
                            val note = KeyboardLogic.noteAt(ch.position.x, ch.position.y, size.width.toFloat(), size.height.toFloat(), blackHeightPx)
                            if (note != prev) {
                                if (note == null) held.remove(ch.id) else held[ch.id] = note
                                if (prev != null && held.values.none { it == prev }) upCb(prev)
                                if (note != null && held.values.count { it == note } == 1) downCb(note)
                            }
                            ch.consume()
                        } else if (prev != null) {
                            held.remove(ch.id)
                            if (held.values.none { it == prev }) upCb(prev)
                        }
                    }
                }
            }
        }
            .drawWithCache {
                val geo = cacheGeometry(size.width)
                val brushes = cacheBrushes(theme, size.height)
                onDrawBehind {
            val w = size.width
            val kh = size.height
            val ww = w / KeyboardLogic.whiteCount
            val bw = ww * 0.6f
            val bh = BlackHeight.toPx()
            val px = 1.dp.toPx()

            // LED style: a short glow behind every pressed key
            if (led) {
                pressedNotes.forEach { m ->
                    if (KeyboardLogic.isBlack(m)) {
                        val l = KeyboardLogic.whitesBeforeOf(m) * ww - bw / 2f
                        drawRoundRect(theme.pressBlack.copy(alpha = 0.28f), Offset(l - 2 * px, -2 * px), Size(bw + 4 * px, bh + 4 * px), CornerRadius(4 * px))
                        drawRoundRect(theme.pressBlack.copy(alpha = 0.14f), Offset(l - 4 * px, -4 * px), Size(bw + 8 * px, bh + 8 * px), CornerRadius(6 * px))
                    } else {
                        val x = KeyboardLogic.whiteIndexOf(m) * ww
                        drawRoundRect(theme.press.copy(alpha = 0.28f), Offset(x - 2 * px, -2 * px), Size(ww + 4 * px, kh + 4 * px), CornerRadius(5 * px))
                        drawRoundRect(theme.press.copy(alpha = 0.14f), Offset(x - 4 * px, -4 * px), Size(ww + 8 * px, kh + 8 * px), CornerRadius(7 * px))
                    }
                }
            }

            // white keys
            geo.whites.forEachIndexed { idx, g ->
                val m = g.note
                val on = m in pressedNotes
                val dy = if (on) px else 0f
                val x = g.x + 0.45f * px
                val kw = ww - 0.9f * px
                val r = CornerRadius(2.6f * px)
                drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(x, dy + px), Size(kw, kh), r)
                if (on) {
                    drawRoundRect(theme.press, Offset(x, dy), Size(kw, kh), r)
                    drawRoundRect(Color.Black.copy(alpha = 0.14f), Offset(x, dy), Size(kw, 7 * px), r)
                } else {
                    drawRoundRect(brushes.white, Offset(x, 0f), Size(kw, kh), r)
                }
                drawRoundRect(theme.edge.copy(alpha = 0.55f), Offset(x, dy), Size(kw, kh), r, style = Stroke(0.6f * px))
                drawRoundRect(
                    Color.White.copy(alpha = if (on) 0.35f else theme.highlight),
                    Offset(x + 0.9f * px, dy + 0.9f * px), Size(kw - 1.8f * px, 2 * px), CornerRadius(px)
                )
                drawRoundRect(
                    Color.Black.copy(alpha = if (on) 0.1f else 0.06f),
                    Offset(x + px, dy + kh - 7 * px), Size(kw - 2 * px, 6 * px), CornerRadius(2 * px)
                )
                labels[m]?.let { lay ->
                    drawText(lay, topLeft = Offset(x + (kw - lay.size.width) / 2f, dy + kh - 9 * px - lay.size.height))
                }
            }

            // black keys, with a soft cast shadow on the white keys
            geo.blacks.forEach { g ->
                val m = g.note
                val on = m in pressedNotes
                val dy = if (on) 0.8f * px else 0f
                val l = g.x
                val r = CornerRadius(2.2f * px)
                drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(l - px, dy + 3 * px), Size(bw + 2 * px, bh), CornerRadius(3 * px))
                drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(l, dy + 1.8f * px), Size(bw, bh), r)
                if (on) {
                    drawRoundRect(theme.pressBlack, Offset(l, dy), Size(bw, bh), r)
                } else {
                    drawRoundRect(brushes.black, Offset(l, dy), Size(bw, bh), r)
                }
                drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(l, dy), Size(bw, bh), r, style = Stroke(0.6f * px))
                drawRoundRect(
                    Color.White.copy(alpha = if (on) 0.28f else 0.15f),
                    Offset(l + 1.2f * px, dy + 1.2f * px), Size(bw - 2.4f * px, 2.2f * px), CornerRadius(px)
                )
                val fy = dy + bh - 9 * px
                drawRoundRect(
                    brushes.blackFront, Offset(l + 0.8f * px, fy), Size(bw - 1.6f * px, 8.2f * px), CornerRadius(1.6f * px)
                )
                drawLine(Color.White.copy(alpha = 0.12f), Offset(l + 0.9f * px, dy + 3 * px), Offset(l + 0.9f * px, dy + bh - 4 * px), 0.8f * px)
            }
                }
            }
    ) { }
}
/* Key geometry + gradients are built once per size/theme change, inside drawWithCache. */
private data class VkbKeyGeo(val note: Int, val x: Float)
private data class VkbGeometry(val whites: List<VkbKeyGeo>, val blacks: List<VkbKeyGeo>)
private data class VkbBrushes(val white: Brush, val black: Brush, val blackFront: Brush)

private fun cacheGeometry(w: Float): VkbGeometry {
    val ww = w / KeyboardLogic.whiteCount
    val bw = ww * 0.6f
    val whites = KeyboardLogic.whites.mapIndexed { idx, m -> VkbKeyGeo(m, idx * ww) }
    val blacks = KeyboardLogic.blacks.map { m -> VkbKeyGeo(m, KeyboardLogic.whitesBeforeOf(m) * ww - bw / 2f) }
    return VkbGeometry(whites, blacks)
}

private fun cacheBrushes(theme: KeyboardTheme, kh: Float): VkbBrushes = VkbBrushes(
    white = Brush.verticalGradient(0f to theme.whiteTop, 0.72f to theme.whiteMid, 1f to theme.whiteBottom, startY = 0f, endY = kh),
    black = Brush.verticalGradient(0f to theme.blackTop, 0.55f to theme.blackMid, 1f to theme.blackBottom, startY = 0f, endY = 31f),
    blackFront = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.13f)), startY = 0f, endY = 8.2f)
)

/* ═══════════════════════════ THEME PICKER (Settings → UI section) ═══════════════════════════ */

@Composable
fun KeyboardThemeSwatch(
    theme: KeyboardTheme,
    selected: Boolean,
    onClick: () -> Unit,
    chrome: KeyboardChrome,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) chrome.accent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (selected) chrome.accent else Color.White.copy(alpha = 0.08f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            val px = 1.dp.toPx()
            drawRoundRect(chrome.frame, cornerRadius = CornerRadius(6 * px)) // the frame is the same for every theme
            val pad = 4 * px
            val kw = (size.width - pad * 2) / 4f
            for (k in 0 until 4) {
                val x = pad + k * kw
                drawRoundRect(
                    if (k == 1) theme.press else theme.whiteMid,
                    Offset(x + 0.5f * px, pad), Size(kw - px, size.height - pad * 2), CornerRadius(2 * px)
                )
            }
            listOf(0, 1, 3).forEach { k ->
                val c = pad + (k + 1) * kw
                drawRoundRect(theme.blackMid, Offset(c - kw * 0.3f, pad), Size(kw * 0.6f, (size.height - pad * 2) * 0.58f), CornerRadius(1.5f * px))
            }
        }
        Text(theme.name, color = Color(0xFFC9D2E2), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun PressStyleSegment(value: KeyboardPressStyle, onChange: (KeyboardPressStyle) -> Unit, chrome: KeyboardChrome) {
    val shape = RoundedCornerShape(11.dp)
    Row(Modifier.clip(shape).background(Color(0xFF0E131C)).padding(3.dp)) {
        listOf(KeyboardPressStyle.Color to "Color", KeyboardPressStyle.Led to "LED").forEach { (style, label) ->
            val on = style == value
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) chrome.accent.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(style) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) chrome.accent else Color(0xFF8690A4), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/* ═══════════════════════════ PREVIEWS ═══════════════════════════ */

@Preview(widthDp = 891, heightDp = 260, backgroundColor = 0xFF0F141D, showBackground = true)
@Composable
private fun VirtualKeyboardPreview() {
    var expanded by remember { mutableStateOf(true) }
    var lines by remember {
        mutableStateOf(
            listOf(
                KeyboardLine("t1", "S90 Full Grand", Color(0xFFE68A29), 24, 108),
                KeyboardLine("t2", "Motif XF Galaxy DX", Color(0xFF36B582), 24, 108),
                KeyboardLine("t3", "Analog Warm Pad", Color(0xFF3B82F6), 24, 108),
                KeyboardLine("t4", "Organ B3 Fast", Color(0xFF9333EA), 52, 104)
            )
        )
    }
    var pressed by remember { mutableStateOf(setOf<Int>()) }
    Box(Modifier.fillMaxSize().padding(5.dp), contentAlignment = Alignment.BottomCenter) {
        VirtualKeyboardContainer(
            lines = lines, expanded = expanded, pressedNotes = pressed,
            theme = KeyboardThemes.Classic, pressStyle = KeyboardPressStyle.Color,
            actions = VirtualKeyboardActions(
                onExpandedChange = { expanded = it },
                onLineRangeChange = { id, lo, hi -> lines = lines.map { if (it.id == id) it.copy(low = lo, high = hi) else it } },
                onKeyDown = { pressed = pressed + it },
                onKeyUp = { pressed = pressed - it },
                onPitchBend = {}
            )
        )
    }
}
