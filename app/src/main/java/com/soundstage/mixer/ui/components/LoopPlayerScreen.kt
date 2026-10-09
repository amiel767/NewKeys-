@file:Suppress("unused")

package com.soundstage.mixer.ui.components

/*
 * ÆTHER KEYS — LOOP PLAYER (Complete DAW loop player interface)
 *
 * Professional Bento design, matte finish without neon glows.
 * Responsive, tactile, and space-efficient.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════════════════════════
// 1. MODELS
// ════════════════════════════════════════════════════════════════════════════════════════

enum class FxType(val label: String) {
    Reverb("Reverb"),
    Delay("Delay"),
    Filter("Filter"),
    Shimmer("Shimmer")
}

data class FxState(val enabled: Boolean = false, val amount: Float = 0.4f)

sealed interface LoopEntry {
    val id: String
    val name: String
}

data class LoopFolder(
    override val id: String,
    override val name: String,
    val itemCount: Int = 0
) : LoopEntry

data class LoopFile(
    override val id: String,
    override val name: String,
    val bpm: Int,
    /** E.g. "Cm", "C#", "Dbm", "A". Empty or unknown = undetected. */
    val keyLabel: String,
    val bars: Int,
    val isFavorite: Boolean = false,
    /** Timestamp (ms) of last play, 0 = never. */
    val lastPlayedAt: Long = 0L,
    /** Normalized waveform peaks 0..1. */
    val peaks: List<Float> = emptyList()
) : LoopEntry

data class Crumb(val id: String, val label: String)

enum class LoopSort(val label: String) {
    NearestBpm("Nearest BPM"),
    Name("Name"),
    Duration("Duration"),
    Recent("Recent")
}

enum class LoopFilter(val label: String) {
    CompatibleKey("Compatible"),
    Favorites("Favorites")
}

enum class LoopStatus { Idle, Ready, Queued, Playing }

data class LoopPlayerUiState(
    // Navigation
    val breadcrumb: List<Crumb> = emptyList(),
    val entries: List<LoopEntry> = emptyList(),
    val query: String = "",
    val sort: LoopSort = LoopSort.NearestBpm,
    val filters: Set<LoopFilter> = emptySet(),
    val selectedId: String? = null,
    val playingId: String? = null,

    // Playback
    val status: LoopStatus = LoopStatus.Idle,
    val loopName: String = "",
    val positionBar: Int = 1,
    val positionBeat: Int = 1,
    val progress: Float = 0f,
    val totalBars: Int = 4,
    val waveform: List<Float> = emptyList(),
    val currentBpm: Int = 120,

    // Pitch & Key
    val keyRoot: String = "C",
    val isMinor: Boolean = false,
    val originalKeyLabel: String = "C",
    val semitoneShift: Int = 0,
    val timeSignature: String = "4/4",
    val stepLabel: String = "Auto",

    // Channel
    val volume: Float = LoopPlayerLogic.UNITY_POSITION,
    val muted: Boolean = false,
    val pan: Float = 0f,
    val fx: Map<FxType, FxState> = FxType.values().associateWith { FxState() }
) {
    val keyLabel: String get() = keyRoot + if (isMinor) "m" else ""
}

interface LoopPlayerActions {
    // Navigation
    fun onOpenFolder(id: String) {}
    fun onNavigateUp() {}
    fun onBreadcrumbClick(index: Int) {}
    fun onSelectFile(id: String) {}
    fun onToggleFavorite(id: String) {}
    fun onPreviewStart(id: String) {}
    fun onPreviewStop() {}
    fun onQueryChange(query: String) {}
    fun onSortChange(sort: LoopSort) {}
    fun onFilterToggle(filter: LoopFilter) {}

    // Playback
    fun onTogglePlay() {}
    fun onSeek(fraction: Float) {}
    fun onKeyStep(delta: Int) {}
    fun onMinorChange(minor: Boolean) {}
    fun onStepChange(delta: Int) {}
    fun onSignatureClick() {}

    // Channel
    fun onVolumeChange(position: Float) {}
    fun onMuteToggle() {}
    fun onPanChange(pan: Float) {}
    fun onFxEnabledChange(type: FxType, enabled: Boolean) {}
    fun onFxAmountChange(type: FxType, amount: Float) {}
}

object NoOpLoopPlayerActions : LoopPlayerActions

// ════════════════════════════════════════════════════════════════════════════════════════
// 2. LOGIC
// ════════════════════════════════════════════════════════════════════════════════════════

data class LoopMixParams(
    val gainLeft: Float,
    val gainRight: Float,
    val reverbWet: Float,
    val delayMix: Float,
    val delayFeedback: Float,
    val delayTimeMs: Float,
    val filterActive: Boolean,
    val filterCutoffHz: Float,
    val shimmerMix: Float,
    val shimmerSemitones: Int
)

data class PanGains(val left: Float, val right: Float)

object LoopPlayerLogic {

    const val DB_MIN = -60f
    const val DB_MAX = 6f
    const val UNITY_POSITION = 0.75f

    fun faderToDb(position: Float): Float {
        val p = position.coerceIn(0f, 1f)
        return when {
            p <= 0.001f -> Float.NEGATIVE_INFINITY
            p <= UNITY_POSITION -> DB_MIN + (p / UNITY_POSITION) * (0f - DB_MIN)
            else -> (p - UNITY_POSITION) / (1f - UNITY_POSITION) * DB_MAX
        }
    }

    fun dbToGain(db: Float): Float =
        if (db == Float.NEGATIVE_INFINITY) 0f else 10f.pow(db / 20f)

    fun volumeGain(position: Float, muted: Boolean): Float =
        if (muted) 0f else dbToGain(faderToDb(position))

    fun volumeLabel(position: Float, muted: Boolean): String {
        if (muted) return "Muted"
        val db = faderToDb(position)
        if (db == Float.NEGATIVE_INFINITY) return "−∞ dB"
        val v = (db * 10f).roundToInt() / 10f
        val sign = when {
            v > 0f -> "+"
            v < 0f -> "−"
            else -> ""
        }
        return sign + String.format(Locale.US, "%.1f", abs(v)) + " dB"
    }

    fun panGains(pan: Float): PanGains {
        val p = pan.coerceIn(-1f, 1f)
        val angle = (p + 1f) * (PI.toFloat() / 4f)
        val k = sqrt(2f)
        return PanGains(
            left = min(1f, cos(angle) * k),
            right = min(1f, sin(angle) * k)
        )
    }

    fun panLabel(pan: Float): String {
        val p = (pan * 100f).roundToInt()
        return when {
            p == 0 -> "C"
            p < 0 -> "L ${-p}"
            else -> "R $p"
        }
    }

    fun reverbWet(amount: Float) = amount.coerceIn(0f, 1f) * 0.55f
    fun delayMix(amount: Float) = amount.coerceIn(0f, 1f) * 0.45f
    fun delayFeedback(amount: Float) = 0.20f + amount.coerceIn(0f, 1f) * 0.40f
    fun delayTimeMs(bpm: Int, beats: Float = 0.75f): Float = 60_000f / max(1, bpm) * beats
    fun filterCutoffHz(amount: Float): Float = 20_000f * 0.02f.pow(amount.coerceIn(0f, 1f))
    fun shimmerMix(amount: Float) = amount.coerceIn(0f, 1f) * 0.35f
    const val SHIMMER_SEMITONES = 12

    fun mixParams(s: LoopPlayerUiState): LoopMixParams {
        val gain = volumeGain(s.volume, s.muted)
        val pg = panGains(s.pan)
        fun fx(t: FxType) = s.fx[t] ?: FxState()
        fun amt(t: FxType) = fx(t).let { if (it.enabled) it.amount else 0f }
        val filter = fx(FxType.Filter)
        return LoopMixParams(
            gainLeft = gain * pg.left,
            gainRight = gain * pg.right,
            reverbWet = reverbWet(amt(FxType.Reverb)),
            delayMix = delayMix(amt(FxType.Delay)),
            delayFeedback = delayFeedback(amt(FxType.Delay)),
            delayTimeMs = delayTimeMs(s.currentBpm),
            filterActive = filter.enabled,
            filterCutoffHz = if (filter.enabled) filterCutoffHz(filter.amount) else 20_000f,
            shimmerMix = shimmerMix(amt(FxType.Shimmer)),
            shimmerSemitones = SHIMMER_SEMITONES
        )
    }

    fun browse(
        entries: List<LoopEntry>,
        query: String,
        sort: LoopSort,
        filters: Set<LoopFilter>,
        currentBpm: Int,
        currentKeyLabel: String
    ): List<LoopEntry> {
        val q = query.trim().lowercase()
        fun matches(e: LoopEntry) = q.isEmpty() || e.name.lowercase().contains(q)

        val folders = entries.filterIsInstance<LoopFolder>()
            .filter(::matches)
            .sortedBy { it.name.lowercase() }

        var files = entries.filterIsInstance<LoopFile>().filter(::matches)
        if (LoopFilter.Favorites in filters) files = files.filter { it.isFavorite }
        if (LoopFilter.CompatibleKey in filters) {
            files = files.filter { LoopKeyTheory.isCompatible(it.keyLabel, currentKeyLabel) }
        }

        val base: Comparator<LoopFile> = when (sort) {
            LoopSort.NearestBpm -> compareBy<LoopFile> { abs(it.bpm - currentBpm) }
            LoopSort.Name -> compareBy<LoopFile> { it.name.lowercase() }
            LoopSort.Duration -> compareByDescending<LoopFile> { it.bars }
            LoopSort.Recent -> compareByDescending<LoopFile> { it.lastPlayedAt }
        }
        files = files.sortedWith(compareByDescending<LoopFile> { it.isFavorite }.then(base))
        return folders + files
    }

    fun placeholderPeaks(seed: Int, count: Int = 96): List<Float> {
        var s = (seed.toLong() * 9301L + 49297L) and 0x7fffffffL
        return List(count) { i ->
            s = (s * 9301L + 49297L) % 233280L
            val r = s / 233280f
            val hit = exp(-((i % 16) / 1.3f)) * 0.9f
            (0.2f + 0.45f * r + hit * (if (r > 0.5f) 1f else 0.35f)).coerceIn(0.08f, 1f)
        }
    }

    fun formatSemitones(n: Int): String = if (n >= 0) "+$n" else "−${-n}"
}

object LoopKeyTheory {
    private val ROOTS = mapOf(
        "C" to 0, "B#" to 0, "C#" to 1, "DB" to 1, "D" to 2, "D#" to 3, "EB" to 3,
        "E" to 4, "FB" to 4, "F" to 5, "E#" to 5, "F#" to 6, "GB" to 6, "G" to 7,
        "G#" to 8, "AB" to 8, "A" to 9, "A#" to 10, "BB" to 10, "B" to 11, "CB" to 11
    )

    fun parse(label: String): Pair<Int, Boolean>? {
        var t = label.trim()
        if (t.isEmpty()) return null
        var minor = false
        when {
            t.endsWith("min", ignoreCase = true) -> { minor = true; t = t.dropLast(3) }
            t.endsWith("m") -> { minor = true; t = t.dropLast(1) }
            t.endsWith("maj", ignoreCase = true) -> t = t.dropLast(3)
        }
        val root = ROOTS[t.trim().uppercase()] ?: return null
        return root to minor
    }

    private fun wheelNumber(root: Int, minor: Boolean): Int {
        val majorRoot = if (minor) (root + 3) % 12 else root
        return (majorRoot * 7) % 12
    }

    fun isCompatible(a: String, b: String): Boolean {
        val ka = parse(a) ?: return false
        val kb = parse(b) ?: return false
        val na = wheelNumber(ka.first, ka.second)
        val nb = wheelNumber(kb.first, kb.second)
        if (na == nb) return true
        val diff = (na - nb + 12) % 12
        return ka.second == kb.second && (diff == 1 || diff == 11)
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 3. STYLE
// ════════════════════════════════════════════════════════════════════════════════════════

internal object LpColors {
    val Bg = Color(0xFF131923)
    val Card = Color(0xFF1D2231)
    val Well = Color(0xFF111620)
    val Pad = Color(0xFF252D3D)
    val PadHi = Color(0xFF2F394D)
    val Line = Color(0xFF2B3447)
    val Ink = Color(0xFFE6EAF2)
    val Soft = Color(0xFFCFD6E4)
    val Mute = Color(0xFF8690A4)
    val Blue = Color(0xFF95BBF5)
    val Amber = Color(0xFFF59E0B)
    val Rose = Color(0xFFC44066)
    val Wave = Color(0xFF4A5878)
}

private object LpStrings {
    const val Channel = "Channel"
    const val Volume = "Volume"
    const val Pan = "Pan"
    const val Mute = "Mute"
    const val Search = "Search"
    const val Empty = "No loops here"
    const val EmptyHint = "Try another folder or filter"
    const val Name = "Name"
    const val Shape = "Shape"
    const val Bpm = "BPM"
    const val Key = "Key"
    const val Bars = "Bars"
    const val Bar = "Bar"
    const val Beat = "Beat"
    const val NoLoop = "No loop"
    const val Ready = "Ready"
    const val Queued = "Queued"
    const val Playing = "Playing"
    const val Step = "Step"
    const val Signature = "Time Sig"
}

private fun lpText(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = LpColors.Ink
) = TextStyle(
    fontSize = size,
    fontWeight = weight,
    color = color,
    fontFeatureSettings = "tnum"
)

private fun Modifier.panel(shape: Shape = RoundedCornerShape(16.dp)): Modifier = this
    .shadow(6.dp, shape, ambientColor = Color.Black, spotColor = Color(0x99000000))
    .clip(shape)
    .background(LpColors.Card)
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)),
        shape
    )

private fun Modifier.raised(
    shape: Shape = RoundedCornerShape(12.dp),
    elevation: Dp = 3.dp
): Modifier = this
    .shadow(elevation, shape, ambientColor = Color.Black, spotColor = Color(0x99000000))
    .clip(shape)
    .background(Brush.verticalGradient(listOf(LpColors.PadHi, LpColors.Pad)))
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)),
        shape
    )

private fun Modifier.sunk(shape: Shape = RoundedCornerShape(10.dp)): Modifier = this
    .clip(shape)
    .background(LpColors.Well)
    .drawBehind {
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
                startY = 0f,
                endY = 10.dp.toPx()
            )
        )
    }
    .border(1.dp, Color.White.copy(alpha = 0.04f), shape)

@Composable
private fun Modifier.pressable(
    pressedScale: Float = 0.96f,
    onLongPress: (() -> Unit)? = null,
    onTap: () -> Unit
): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "press"
    )
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onLongPress = { long?.invoke() },
                onTap = { tap() }
            )
        }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 4. SCREEN (BENTO 2x2 ALIGNMENT)
// ════════════════════════════════════════════════════════════════════════════════════════

@Composable
fun LoopPlayerScreen(
    state: LoopPlayerUiState,
    actions: LoopPlayerActions,
    modifier: Modifier = Modifier
) {
    val faderWidth = 196.dp
    val bottomHeight = 78.dp // Reduced by 30% from 112.dp

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // TOP ROW: Fader / Channel on Left (196.dp), Browser Navigation on Right
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChannelPanel(state, actions, Modifier.width(faderWidth).fillMaxHeight())
            NavigationPanel(state, actions, Modifier.weight(1f).fillMaxHeight())
        }

        // BOTTOM ROW:
        // Left: ControlsCard (Key, Signature, Step) matching EXACT horizontal width of ChannelPanel (196.dp)!
        // Right: Playback controls (Play button + Waveform) shifted to the right side!
        Row(
            Modifier
                .fillMaxWidth()
                .height(bottomHeight),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlsCard(state, actions, Modifier.width(faderWidth).fillMaxHeight())
            PlayButton(
                playing = state.status == LoopStatus.Playing,
                onClick = { actions.onTogglePlay() },
                modifier = Modifier.width(70.dp).fillMaxHeight()
            )
            WaveCard(state, actions, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.1 CHANNEL PANEL : Volume, Pan, Mute, FX
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChannelPanel(state: LoopPlayerUiState, actions: LoopPlayerActions, modifier: Modifier) {
    Column(
        modifier.panel().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VerticalFader(
                value = state.volume,
                onValueChange = { actions.onVolumeChange(it) },
                modifier = Modifier.width(42.dp).fillMaxHeight(),
                dimmed = state.muted
            )
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top header: Volume dB label on left, small square MUTE button on top-right!
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        SmallLabel(LpStrings.Volume)
                        Text(
                            LoopPlayerLogic.volumeLabel(state.volume, state.muted),
                            style = lpText(
                                15.sp, FontWeight.Bold,
                                if (state.muted) LpColors.Rose else LpColors.Ink
                            ),
                            maxLines = 1
                        )
                    }
                    MuteSquareButton(state.muted, { actions.onMuteToggle() })
                }

                // Bottom: Pan slider moved to where Mute was, reduced by 25%, with L / R inside labels
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    PanSlider(
                        pan = state.pan,
                        onChange = { actions.onPanChange(it) },
                        modifier = Modifier
                            .fillMaxWidth(0.75f) // 25% size reduction
                            .height(24.dp)
                    )
                }
            }
        }
        FxGrid(state, actions)
    }
}

@Composable
private fun SmallLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = TextStyle(
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.1.sp,
            color = LpColors.Mute
        ),
        maxLines = 1
    )
}

/** Small square Mute button placed in the top right corner with "M" */
@Composable
private fun MuteSquareButton(muted: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (muted) LpColors.Rose else Color(0xFF252D3D), tween(200), label = "mute_bg"
    )
    val textColor by animateColorAsState(
        if (muted) Color.White else LpColors.Mute, tween(200), label = "mute_text"
    )
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(28.dp)
            .pressable(0.92f, onTap = onClick)
            .raised(shape, 2.dp)
            .background(bg)
            .then(
                if (muted) Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), shape)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "M",
            style = lpText(12.sp, FontWeight.Bold, textColor)
        )
    }
}

@Composable
private fun VerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    defaultValue: Float = LoopPlayerLogic.UNITY_POSITION
) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val haptic = LocalHapticFeedback.current

    fun emit(v: Float) {
        val unity = LoopPlayerLogic.UNITY_POSITION
        if ((current < unity) != (v < unity)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        change(v)
    }

    Box(
        modifier
            .sunk(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { o -> emit(1f - (o.y / size.height).coerceIn(0f, 1f)) },
                    onVerticalDrag = { c, _ ->
                        c.consume()
                        emit(1f - (c.position.y / size.height).coerceIn(0f, 1f))
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { emit(defaultValue) },
                    onTap = { o -> emit(1f - (o.y / size.height).coerceIn(0f, 1f)) }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize().alpha(if (dimmed) 0.5f else 1f)) {
            val w = size.width
            val h = size.height
            val capH = 24.dp.toPx()
            val travel = h - capH
            fun yFor(v: Float) = capH / 2f + (1f - v) * travel

            val gw = 5.dp.toPx()
            val gx = (w - gw) / 2f
            val gTop = 6.dp.toPx()
            val gBottom = h - 6.dp.toPx()
            drawRoundRect(
                Color.Black.copy(alpha = 0.55f),
                Offset(gx, gTop), Size(gw, gBottom - gTop), CornerRadius(gw / 2f)
            )

            val cy = yFor(value.coerceIn(0f, 1f))
            if (gBottom > cy) {
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(LpColors.Blue, Color(0xFF5F7FB8)), startY = cy, endY = gBottom
                    ),
                    Offset(gx, cy), Size(gw, gBottom - cy), CornerRadius(gw / 2f)
                )
            }

            listOf(
                LoopPlayerLogic.UNITY_POSITION to LpColors.Amber.copy(alpha = 0.8f),
                0.5f to LpColors.Mute.copy(alpha = 0.45f),
                0.25f to LpColors.Mute.copy(alpha = 0.35f)
            ).forEach { (t, col) ->
                val y = yFor(t)
                drawLine(col, Offset(2.dp.toPx(), y), Offset(7.dp.toPx(), y), 1.dp.toPx())
                drawLine(col, Offset(w - 7.dp.toPx(), y), Offset(w - 2.dp.toPx(), y), 1.dp.toPx())
            }

            val inset = 3.dp.toPx()
            val cw = w - inset * 2f
            val r = CornerRadius(6.dp.toPx())
            drawRoundRect(
                Color.Black.copy(alpha = 0.35f),
                Offset(inset, cy - capH / 2f + 2.dp.toPx()), Size(cw, capH), r
            )
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF414C69), Color(0xFF262E44)),
                    startY = cy - capH / 2f, endY = cy + capH / 2f
                ),
                Offset(inset, cy - capH / 2f), Size(cw, capH), r
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.12f),
                Offset(inset, cy - capH / 2f), Size(cw, capH), r, style = Stroke(1.dp.toPx())
            )
            drawLine(
                LpColors.Amber,
                Offset(inset + 6.dp.toPx(), cy), Offset(inset + cw - 6.dp.toPx(), cy),
                1.5.dp.toPx(), cap = StrokeCap.Round
            )
        }
    }
}

/** Pan slider with "L" and "R" labels displayed inside at left and right */
@Composable
private fun PanSlider(pan: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(pan)
    val change by rememberUpdatedState(onChange)
    val haptic = LocalHapticFeedback.current

    fun emit(x: Float, width: Float) {
        var v = ((x / width) * 2f - 1f).coerceIn(-1f, 1f)
        if (abs(v) < 0.05f) v = 0f
        if (v == 0f && current != 0f) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        change(v)
    }

    Box(
        modifier
            .sunk(RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> emit(o.x, size.width.toFloat()) },
                    onHorizontalDrag = { c, _ ->
                        c.consume()
                        emit(c.position.x, size.width.toFloat())
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { change(0f) },
                    onTap = { o -> emit(o.x, size.width.toFloat()) }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val thumbW = 18.dp.toPx()
            val travel = w - thumbW
            val cx = thumbW / 2f + (pan.coerceIn(-1f, 1f) + 1f) / 2f * travel
            val mid = w / 2f
            drawLine(LpColors.Mute.copy(alpha = 0.35f), Offset(mid, 3.dp.toPx()), Offset(mid, h - 3.dp.toPx()), 1.dp.toPx())
            if (abs(cx - mid) > 1f) {
                drawRoundRect(
                    LpColors.Blue.copy(alpha = 0.5f),
                    Offset(min(cx, mid), h / 2f - 1.dp.toPx()),
                    Size(abs(cx - mid), 2.5.dp.toPx()),
                    CornerRadius(1.5.dp.toPx())
                )
            }
            val r = CornerRadius(6.dp.toPx())
            drawRoundRect(
                Color.Black.copy(alpha = 0.35f),
                Offset(cx - thumbW / 2f, 3.dp.toPx() + 1.5.dp.toPx()), Size(thumbW, h - 6.dp.toPx()), r
            )
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF414C69), Color(0xFF262E44)),
                    startY = 3.dp.toPx(), endY = h - 3.dp.toPx()
                ),
                Offset(cx - thumbW / 2f, 3.dp.toPx()), Size(thumbW, h - 6.dp.toPx()), r
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.12f),
                Offset(cx - thumbW / 2f, 3.dp.toPx()), Size(thumbW, h - 6.dp.toPx()), r,
                style = Stroke(1.dp.toPx())
            )
        }

        // Inside L / R labels displayed at extreme left and right
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "L",
                style = TextStyle(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (pan < -0.15f) LpColors.Amber else LpColors.Mute.copy(alpha = 0.6f)
                )
            )
            Text(
                "R",
                style = TextStyle(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (pan > 0.15f) LpColors.Amber else LpColors.Mute.copy(alpha = 0.6f)
                )
            )
        }
    }
}

@Composable
private fun FxGrid(state: LoopPlayerUiState, actions: LoopPlayerActions) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FxType.values().toList().chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pair.forEach { type ->
                    FxTile(
                        type = type,
                        state = state.fx[type] ?: FxState(),
                        onEnabled = { actions.onFxEnabledChange(type, it) },
                        onAmount = { actions.onFxAmountChange(type, it) },
                        modifier = Modifier.weight(1f).height(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FxTile(
    type: FxType,
    state: FxState,
    onEnabled: (Boolean) -> Unit,
    onAmount: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabledNow by rememberUpdatedState(state.enabled)
    val amountNow by rememberUpdatedState(state.amount)
    val setEnabled by rememberUpdatedState(onEnabled)
    val setAmount by rememberUpdatedState(onAmount)
    val haptic = LocalHapticFeedback.current

    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "fxp"
    )
    val textColor by animateColorAsState(
        if (state.enabled) LpColors.Ink else LpColors.Mute, tween(200), label = "fxt"
    )
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .raised(shape, 2.dp)
            .then(
                if (state.enabled) Modifier.border(1.dp, LpColors.Amber.copy(alpha = 0.5f), shape)
                else Modifier
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        setEnabled(!enabledNow)
                    }
                )
            }
            .pointerInput(Unit) {
                var local = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        local = amountNow
                        if (!enabledNow) setEnabled(true)
                    },
                    onHorizontalDrag = { c, dx ->
                        c.consume()
                        local = (local + dx / size.width * 1.3f).coerceIn(0f, 1f)
                        setAmount(local)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (state.enabled) FxGlow()
        Text(
            type.label,
            style = lpText(11.sp, FontWeight.SemiBold, textColor),
            modifier = Modifier.padding(bottom = 3.dp),
            maxLines = 1
        )
        Canvas(Modifier.fillMaxSize()) {
            val barH = 2.5.dp.toPx()
            val padX = 8.dp.toPx()
            val y = size.height - barH - 4.dp.toPx()
            val full = size.width - padX * 2f
            drawRoundRect(
                Color.Black.copy(alpha = 0.35f),
                Offset(padX, y), Size(full, barH), CornerRadius(barH / 2f)
            )
            if (state.enabled) {
                drawRoundRect(
                    LpColors.Amber,
                    Offset(padX, y), Size(full * state.amount.coerceIn(0f, 1f), barH),
                    CornerRadius(barH / 2f)
                )
            }
        }
    }
}

@Composable
private fun FxGlow() {
    val t = rememberInfiniteTransition(label = "fxglow")
    val a by t.animateFloat(
        0.25f, 0.9f,
        infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "a"
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = a }
            .background(
                Brush.verticalGradient(
                    listOf(LpColors.Amber.copy(alpha = 0.15f), Color.Transparent)
                )
            )
    )
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.2 NAVIGATION PANEL (BROWSER)
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun NavigationPanel(
    state: LoopPlayerUiState,
    actions: LoopPlayerActions,
    modifier: Modifier
) {
    val visible = remember(
        state.entries, state.query, state.sort, state.filters, state.currentBpm, state.keyLabel
    ) {
        LoopPlayerLogic.browse(
            state.entries, state.query, state.sort, state.filters,
            state.currentBpm, state.keyLabel
        )
    }
    val folderKey = state.breadcrumb.lastOrNull()?.id
    val seen = remember(folderKey) { mutableSetOf<String>() }
    val canGoUp = state.breadcrumb.size > 1
    val upGesture by rememberUpdatedState(canGoUp)

    BoxWithConstraints(modifier.panel()) {
        val showWave = maxWidth > 560.dp
        Column(Modifier.fillMaxSize()) {
            // Header bar containing folder name & sort chips
            NavBar(state, actions, canGoUp)

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(actions) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total > 140f && upGesture) actions.onNavigateUp()
                                total = 0f
                            },
                            onDragCancel = { total = 0f },
                            onHorizontalDrag = { _, d -> total += d }
                        )
                    }
            ) {
                if (visible.isEmpty()) {
                    EmptyState(Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(visible, key = { _, e -> e.id }) { index, entry ->
                            val animateIn = remember { seen.add(entry.id) }
                            when (entry) {
                                is LoopFolder -> FolderRow(entry, index, animateIn, actions)
                                is LoopFile -> FileRow(
                                    file = entry,
                                    index = index,
                                    animateIn = animateIn,
                                    selected = entry.id == state.selectedId,
                                    playing = entry.id == state.playingId &&
                                        state.status == LoopStatus.Playing,
                                    showWave = showWave,
                                    actions = actions
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Header bar: displays only current folder name and the sort/filter chips directly */
@Composable
private fun NavBar(
    state: LoopPlayerUiState,
    actions: LoopPlayerActions,
    canGoUp: Boolean
) {
    val currentFolderName = state.breadcrumb.lastOrNull()?.label ?: "Loops"

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (canGoUp) {
            Box(
                Modifier
                    .size(32.dp)
                    .pressable(0.94f, onTap = { actions.onNavigateUp() })
                    .raised(RoundedCornerShape(8.dp), 2.dp),
                contentAlignment = Alignment.Center
            ) { IconBack(LpColors.Ink, Modifier.size(16.dp)) }
        }

        // Folder name with back arrow icon to navigate internal storage
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .pressable(0.95f, onTap = { actions.onNavigateUp() })
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            IconBack(LpColors.Blue, Modifier.size(14.dp))
            Text(
                currentFolderName,
                style = lpText(13.5.sp, FontWeight.Bold, LpColors.Ink),
                maxLines = 1
            )
        }

        Spacer(Modifier.weight(1f))

        // Sort & Filter chips moved to the header row
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LoopSort.values().forEach { s ->
                Chip(s.label, state.sort == s) { actions.onSortChange(s) }
            }
            Box(Modifier.width(1.dp).height(14.dp).background(LpColors.Line))
            Chip("Favorites", LoopFilter.Favorites in state.filters) {
                actions.onFilterToggle(LoopFilter.Favorites)
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c by animateColorAsState(
        if (selected) LpColors.Amber else LpColors.Mute, tween(180), label = "chip"
    )
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .height(26.dp)
            .pressable(0.95f, onTap = onClick)
            .raised(shape, 1.5.dp)
            .then(
                if (selected) Modifier.border(1.dp, LpColors.Amber.copy(alpha = 0.55f), shape)
                else Modifier
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = lpText(11.sp, FontWeight.SemiBold, c), maxLines = 1)
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(LpStrings.Empty, style = lpText(14.sp, FontWeight.SemiBold, LpColors.Soft))
        Spacer(Modifier.height(3.dp))
        Text(LpStrings.EmptyHint, style = lpText(11.5.sp, color = LpColors.Mute))
    }
}

/** Reduced by 25% from 52.dp to 39.dp */
@Composable
private fun FolderRow(
    folder: LoopFolder,
    index: Int,
    animateIn: Boolean,
    actions: LoopPlayerActions
) {
    val enter = rememberEnter(index, animateIn)
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(39.dp) // Reduced by 25% to save space
            .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 12f }
            .pressable(0.985f, onTap = { actions.onOpenFolder(folder.id) })
            .clip(shape)
            .background(Color.White.copy(alpha = 0.02f))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconFolder(LpColors.Blue, Modifier.size(width = 16.dp, height = 13.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            folder.name,
            style = lpText(13.sp, FontWeight.SemiBold, LpColors.Soft),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (folder.itemCount > 0) {
            Text(
                "${folder.itemCount}",
                style = lpText(11.sp, color = LpColors.Mute)
            )
            Spacer(Modifier.width(6.dp))
        }
        IconChevron(LpColors.Mute, Modifier.size(width = 6.dp, height = 11.dp))
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun rememberEnter(index: Int, animateIn: Boolean): androidx.compose.runtime.State<Float> {
    var shown by remember { mutableStateOf(!animateIn) }
    LaunchedEffect(Unit) {
        if (!shown) {
            delay((index * 20L).coerceAtMost(200L))
            shown = true
        }
    }
    return animateFloatAsState(
        if (shown) 1f else 0f, tween(260, easing = FastOutSlowInEasing), label = "enter"
    )
}

/** Reduced by 25% from 52.dp to 39.dp with BPM, Key, Time Sig chips on the right */
@Composable
private fun FileRow(
    file: LoopFile,
    index: Int,
    animateIn: Boolean,
    selected: Boolean,
    playing: Boolean,
    showWave: Boolean,
    actions: LoopPlayerActions
) {
    val enter = rememberEnter(index, animateIn)
    val bg by animateColorAsState(
        if (selected) LpColors.Amber.copy(alpha = 0.12f) else Color.Transparent,
        tween(200), label = "rowbg"
    )
    val shape = RoundedCornerShape(10.dp)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    var previewed by remember { mutableStateOf(false) }
    val peaks = remember(file.id, file.peaks) {
        file.peaks.ifEmpty { LoopPlayerLogic.placeholderPeaks(file.id.hashCode(), 48) }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(39.dp) // Reduced by 25% to save space
            .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 12f }
            .clip(shape)
            .background(bg)
            .then(
                if (selected) Modifier.border(1.dp, LpColors.Amber.copy(alpha = 0.35f), shape)
                else Modifier
            )
            .background(if (pressed) Color.White.copy(alpha = 0.04f) else Color.Transparent)
            .pointerInput(file.id) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        previewed = false
                        val job = scope.launch {
                            delay(380)
                            previewed = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            actions.onPreviewStart(file.id)
                        }
                        tryAwaitRelease()
                        job.cancel()
                        if (previewed) actions.onPreviewStop()
                        pressed = false
                    },
                    onTap = { if (!previewed) actions.onSelectFile(file.id) }
                )
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayDot(playing)
        Spacer(Modifier.width(8.dp))
        Text(
            file.name,
            style = lpText(
                12.5.sp,
                if (selected) FontWeight.Bold else FontWeight.Normal,
                if (selected) LpColors.Ink else LpColors.Soft
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (showWave) {
            MiniWave(
                peaks,
                if (selected) LpColors.Amber else LpColors.Blue,
                Modifier.width(72.dp).height(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }

        // Badges / chips for BPM, Key, Time Sig / Bars on the right of each file
        FileBadge("${file.bpm} BPM", LpColors.Mute)
        Spacer(Modifier.width(5.dp))
        FileBadge(
            file.keyLabel.ifBlank { "—" },
            if (selected) LpColors.Amber else LpColors.Blue
        )
        Spacer(Modifier.width(5.dp))
        FileBadge("${file.bars} b", LpColors.Mute)
        Spacer(Modifier.width(4.dp))

        Box(
            Modifier
                .size(24.dp)
                .pressable(0.85f, onTap = { actions.onToggleFavorite(file.id) }),
            contentAlignment = Alignment.Center
        ) { IconStar(file.isFavorite, Modifier.size(14.dp)) }
    }
}

@Composable
private fun FileBadge(text: String, color: Color) {
    Box(
        Modifier
            .height(20.dp)
            .sunk(RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = lpText(10.5.sp, FontWeight.Medium, color), maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PlayDot(playing: Boolean) {
    Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
        if (playing) {
            val t = rememberInfiniteTransition(label = "dot")
            val r by t.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(1000, easing = LinearEasing)),
                label = "r"
            )
            Canvas(Modifier.requiredSize(20.dp)) {
                drawCircle(LpColors.Amber.copy(alpha = (1f - r) * 0.5f), 3.dp.toPx() + r * 6.dp.toPx())
                drawCircle(LpColors.Amber, 3.dp.toPx())
            }
        }
    }
}

@Composable
private fun MiniWave(peaks: List<Float>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val n = 32
        val gap = size.width / n
        val bw = gap * 0.6f
        for (i in 0 until n) {
            val v = peaks.bucketMax(i, n)
            val h = max(2.dp.toPx(), v * size.height)
            drawRoundRect(
                color.copy(alpha = 0.75f),
                Offset(i * gap, (size.height - h) / 2f), Size(bw, h), CornerRadius(bw / 2f)
            )
        }
    }
}

private fun List<Float>.bucketMax(i: Int, n: Int): Float {
    if (isEmpty()) return 0.2f
    val start = (i.toLong() * size / n).toInt().coerceIn(0, size - 1)
    val end = max(start + 1, ((i + 1).toLong() * size / n).toInt()).coerceAtMost(size)
    var m = 0f
    for (k in start until end) m = max(m, this[k])
    return m.coerceIn(0.05f, 1f)
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.3 CONTROLS & PLAYBACK
// ────────────────────────────────────────────────────────────────────────────────────────

/** Play button */
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val top by animateColorAsState(
        if (playing) Color(0xFFF2A93B) else Color(0xFF34405C), tween(250), label = "pt"
    )
    val bottom by animateColorAsState(
        if (playing) Color(0xFFB06F12) else Color(0xFF222A40), tween(250), label = "pb"
    )
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .pressable(0.96f, onTap = onClick)
            .shadow(
                6.dp, shape, ambientColor = Color.Black,
                spotColor = if (playing) LpColors.Amber else Color.Black
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent)),
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        PlayPauseIcon(
            playing,
            if (playing) Color(0xFF1A1F2B) else LpColors.Ink,
            Modifier.size(26.dp)
        )
    }
}

/** Waveform card (moved to the right) */
@Composable
private fun WaveCard(state: LoopPlayerUiState, actions: LoopPlayerActions, modifier: Modifier) {
    val peaks = remember(state.waveform, state.loopName) {
        state.waveform.ifEmpty {
            LoopPlayerLogic.placeholderPeaks(state.loopName.hashCode(), 160)
        }
    }
    val seek by rememberUpdatedState(actions)

    Column(
        modifier.panel().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                state.loopName.ifBlank { LpStrings.NoLoop },
                style = lpText(12.5.sp, FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                "${LpStrings.Bar} ${state.positionBar} · ${LpStrings.Beat} ${state.positionBeat}",
                style = lpText(10.5.sp, color = LpColors.Mute),
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            StatusChip(state.status)
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .sunk(RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        seek.onSeek((o.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> seek.onSeek((o.x / size.width).coerceIn(0f, 1f)) },
                        onHorizontalDrag = { c, _ ->
                            c.consume()
                            seek.onSeek((c.position.x / size.width).coerceIn(0f, 1f))
                        }
                    )
                }
        ) {
            Waveform(peaks, state.progress.coerceIn(0f, 1f), Modifier.fillMaxSize())
        }

        BarRuler(state.totalBars)
    }
}

@Composable
private fun Waveform(peaks: List<Float>, progress: Float, modifier: Modifier) {
    Canvas(modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        val barStep = 3.5.dp.toPx()
        val n = max(1, (size.width / barStep).toInt())
        val gap = size.width / n
        val bw = gap * 0.62f
        val px = size.width * progress

        fun DrawScope.bars(brush: Brush?, color: Color?) {
            for (i in 0 until n) {
                val v = peaks.bucketMax(i, n)
                val h = max(2.dp.toPx(), v * size.height)
                val tl = Offset(i * gap, (size.height - h) / 2f)
                val sz = Size(bw, h)
                val r = CornerRadius(bw / 2f)
                if (brush != null) drawRoundRect(brush, tl, sz, r) else drawRoundRect(color!!, tl, sz, r)
            }
        }

        bars(null, LpColors.Wave)
        clipRect(left = 0f, top = 0f, right = px, bottom = size.height) {
            bars(
                Brush.horizontalGradient(
                    listOf(LpColors.Rose, LpColors.Amber), startX = 0f, endX = max(1f, px)
                ),
                null
            )
        }
        drawLine(LpColors.Amber, Offset(px, 0f), Offset(px, size.height), 1.5.dp.toPx())
        drawCircle(LpColors.Amber, 2.5.dp.toPx(), Offset(px, 1.dp.toPx()))
    }
}

@Composable
private fun BarRuler(totalBars: Int) {
    val bars = max(1, totalBars)
    val step = ceil(bars / 16f).toInt().coerceAtLeast(1)
    val groups = ceil(bars / step.toFloat()).toInt()
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(10.dp)) {
        for (g in 0 until groups) {
            Text(
                "${g * step + 1}",
                modifier = Modifier.weight(1f),
                style = lpText(8.5.sp, color = LpColors.Mute),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatusChip(status: LoopStatus) {
    val label = when (status) {
        LoopStatus.Idle -> LpStrings.NoLoop
        LoopStatus.Ready -> LpStrings.Ready
        LoopStatus.Queued -> LpStrings.Queued
        LoopStatus.Playing -> LpStrings.Playing
    }
    val t = rememberInfiniteTransition(label = "status")
    val pulse by t.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
        label = "p"
    )
    val breathe by t.animateFloat(
        0.78f, 1f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b"
    )
    val shape = RoundedCornerShape(50)
    val base = Modifier.height(18.dp).clip(shape)
    when (status) {
        LoopStatus.Idle -> Box(
            base.border(1.dp, LpColors.Line, shape).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) { Text(label, style = lpText(9.5.sp, FontWeight.SemiBold, LpColors.Mute), maxLines = 1) }

        LoopStatus.Ready -> Box(
            base.border(1.dp, LpColors.Amber.copy(alpha = 0.7f), shape).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) { Text(label, style = lpText(9.5.sp, FontWeight.SemiBold, LpColors.Amber), maxLines = 1) }

        LoopStatus.Queued -> Box(
            base
                .graphicsLayer { alpha = pulse }
                .border(1.dp, LpColors.Rose, shape)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) { Text(label, style = lpText(9.5.sp, FontWeight.SemiBold, LpColors.Rose), maxLines = 1) }

        LoopStatus.Playing -> Box(
            base
                .graphicsLayer { alpha = breathe }
                .background(LpColors.Amber)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) { Text(label, style = lpText(9.5.sp, FontWeight.Bold, Color(0xFF1A1F2B)), maxLines = 1) }
    }
}

/** Controls card (Key, Signature, Step) placed at the bottom left with exact same horizontal width as ChannelPanel */
@Composable
private fun ControlsCard(state: LoopPlayerUiState, actions: LoopPlayerActions, modifier: Modifier) {
    Column(
        modifier.panel().padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepperBox(
                value = state.keyLabel,
                caption = "Key",
                onMinus = { actions.onKeyStep(-1) },
                onPlus = { actions.onKeyStep(+1) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                valueSize = 14.sp
            )
            MajMinToggle(state.isMinor, { actions.onMinorChange(it) })
        }
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                Modifier
                    .width(52.dp)
                    .fillMaxHeight()
                    .pressable(0.97f, onTap = { actions.onSignatureClick() })
                    .sunk(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.timeSignature, style = lpText(12.5.sp, FontWeight.Bold), maxLines = 1)
                    Text("Sig", style = lpText(8.sp, color = LpColors.Mute))
                }
            }
            StepperBox(
                value = state.stepLabel,
                caption = "Step",
                onMinus = { actions.onStepChange(-1) },
                onPlus = { actions.onStepChange(+1) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                valueSize = 12.sp
            )
        }
    }
}

@Composable
private fun StepperBox(
    value: String,
    caption: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    valueSize: TextUnit = 14.sp
) {
    Row(
        modifier.sunk(RoundedCornerShape(8.dp)).padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundButton("−", onMinus)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            SlidingValue(value, lpText(valueSize, FontWeight.Bold))
            Text(
                caption,
                style = lpText(8.sp, color = LpColors.Mute),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RoundButton("+", onPlus)
    }
}

@Composable
private fun RoundButton(label: String, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "rb"
    )
    Box(
        Modifier
            .size(24.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .raised(CircleShape, 1.5.dp)
            .pointerInput(Unit) {
                var repeated = false
                detectTapGestures(
                    onPress = {
                        repeated = false
                        pressed = true
                        val job = scope.launch {
                            delay(450)
                            while (isActive) {
                                repeated = true
                                step()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                delay(110)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                        pressed = false
                    },
                    onTap = {
                        if (!repeated) {
                            step()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = lpText(12.sp, FontWeight.Bold, LpColors.Soft), textAlign = TextAlign.Center)
    }
}

@Composable
private fun MajMinToggle(isMinor: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .width(40.dp)
            .fillMaxHeight()
            .pressable(0.96f, onTap = { onChange(!isMinor) })
            .raised(shape, 1.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (isMinor) "Min" else "Maj",
            style = lpText(11.sp, FontWeight.Bold, if (isMinor) LpColors.Amber else LpColors.Soft)
        )
    }
}

@Composable
private fun SlidingValue(value: String, style: TextStyle) {
    Text(value, style = style, maxLines = 1)
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 5. ICONS
// ════════════════════════════════════════════════════════════════════════════════════════

@Composable
private fun IconBack(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = min(size.width, size.height)
        val p = Path().apply {
            moveTo(s * 0.65f, s * 0.25f)
            lineTo(s * 0.35f, s * 0.50f)
            lineTo(s * 0.65f, s * 0.75f)
        }
        drawPath(p, tint, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun IconFolder(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(0f, 2.dp.toPx())
            lineTo(w * 0.40f, 2.dp.toPx())
            lineTo(w * 0.52f, 5.dp.toPx())
            lineTo(w, 5.dp.toPx())
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(p, tint.copy(alpha = 0.25f), style = Fill)
        drawPath(p, tint, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun IconChevron(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.2f, h * 0.15f)
            lineTo(w * 0.8f, h * 0.50f)
            lineTo(w * 0.2f, h * 0.85f)
        }
        drawPath(p, tint, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun PlayPauseIcon(playing: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = min(size.width, size.height)
        if (playing) {
            val barW = s * 0.22f
            val gap = s * 0.18f
            val barH = s * 0.60f
            val top = (s - barH) / 2f
            val x1 = (s - (barW * 2f + gap)) / 2f
            val x2 = x1 + barW + gap
            drawRoundRect(tint, Offset(x1, top), Size(barW, barH), CornerRadius(barW / 3f))
            drawRoundRect(tint, Offset(x2, top), Size(barW, barH), CornerRadius(barW / 3f))
        } else {
            val p = Path().apply {
                val cx = s * 0.52f
                val h = s * 0.60f
                val w = s * 0.50f
                moveTo(cx - w / 2f, (s - h) / 2f)
                lineTo(cx + w / 2f, s / 2f)
                lineTo(cx - w / 2f, (s + h) / 2f)
                close()
            }
            drawPath(p, tint, style = Fill)
        }
    }
}

@Composable
private fun IconStar(filled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = min(size.width, size.height)
        val cx = s / 2f
        val cy = s / 2f
        val outer = s * 0.45f
        val inner = s * 0.20f
        val p = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = -PI / 2.0 + i * PI / 5.0
            val x = cx + (r * cos(a)).toFloat()
            val y = cy + (r * sin(a)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        if (filled) drawPath(p, LpColors.Amber, style = Fill)
        else drawPath(p, LpColors.Mute.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx()))
    }
}
