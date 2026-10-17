@file:Suppress("unused")

package com.soundstage.mixer.ui.setlists

/*
 * ÆTHER KEYS — SETLISTS SCREEN (final design)
 *
 * Scope: this file contains ONLY the Setlists screen and its pure logic. It does not touch the
 * DrumPad, the Loop Player, the mixer, the audio engine, or the global top bar.
 * A setlist is a folder that stores patches: the business logic lives in the app and is wired
 * through [SetlistsActions] / [SetlistsUiState]. Everything the user sees is in English.
 *
 * Requirements: Jetpack Compose (foundation 1.7+ for LazyItemScope.animateItem), material3 (Text only),
 * ui-tooling-preview (for the @Preview only). java.time needs minSdk 26 or core library desugaring.
 * For the "Create" sheet to rise above the keyboard: windowSoftInputMode = adjustResize and
 * WindowCompat.setDecorFitsSystemWindows(window, false).
 *
 * Entry point: [SetlistsScreen].
 */

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.soundstage.mixer.ui.theme.AppColors

// ════════════════════════════════════════════════════════════════════════════════════════
// 1. MODELS
// ════════════════════════════════════════════════════════════════════════════════════════

/** Four tones with the same luminosity and a moderate saturation: none of them dominates. */
enum class SetlistColor(val base: Color, val text: Color) {
    Blue(AppColors.SetlistBlue, Color(0xFFA9C6F0)),
    Amber(AppColors.SetlistAmber, Color(0xFFE6BC73)),
    Rose(AppColors.SetlistRose, Color(0xFFDB8AA3)),
    Sage(AppColors.SetlistSage, Color(0xFFA5BFB1))
}

data class Setlist(
    val id: String,
    val name: String,
    val patchCount: Int,
    val color: SetlistColor,
    val isPinned: Boolean = false,
    /** Epoch millis of the last time the setlist was opened. */
    val lastUsedAt: Long = 0L
)

enum class SetlistSort(val label: String) { Recent("Recent"), Name("Name"), Patches("Patches") }

enum class OpenGesture(val label: String) { Tap("Tap"), DoubleTap("Double tap"), Hold("Hold") }

enum class CreateMode { Blank, Duplicate }

/** Page-level preferences (opened by the gear). Persist them on the app side. */
data class SetlistsPrefs(
    val animatedBackground: Boolean = true,
    val cardAuras: Boolean = true,
    val compactRows: Boolean = false,
    val patchDots: Boolean = true,
    val openWith: OpenGesture = OpenGesture.Tap,
    val pinnedOnTop: Boolean = true,
    val rememberSort: Boolean = true,
    val lockDeletion: Boolean = false,
    val haptics: Boolean = true
)

data class SetlistsUiState(
    val setlists: List<Setlist> = emptyList(),
    /** The setlist currently loaded in the app, or null (first launch / none open). */
    val openSetlistId: String? = null,
    /** The last setlist that was opened (kept even when none is open right now). */
    val lastOpenedId: String? = null,
    val isLoading: Boolean = false,
    val query: String = "",
    val sort: SetlistSort = SetlistSort.Recent,
    val onlyPinned: Boolean = false,
    val prefs: SetlistsPrefs = SetlistsPrefs(),
    /** True when the system asks for reduced motion (e.g. animator duration scale = 0). */
    val reducedMotion: Boolean = false
) {
    /** The global Back button (returns to the mixer) must only be enabled when this is true. */
    val isBackEnabled: Boolean get() = openSetlistId != null
}

/** Every method has an empty default body: wire what you need first. */
interface SetlistsActions {
    fun onCancel() {}
    /** Load the setlist, mark it as open and last opened, update lastUsedAt, return to the mixer. */
    fun onOpenSetlist(id: String) {}
    /** [mode] Blank = no patches. Duplicate = copy of the currently open setlist. Color: see [SetlistsLogic.autoColor]. */
    fun onCreateSetlist(name: String, mode: CreateMode) {}
    fun onRenameSetlist(id: String, name: String) {}
    fun onChangeColor(id: String, color: SetlistColor) {}
    fun onDuplicateSetlist(id: String) {}
    fun onTogglePin(id: String) {}
    /** Delete immediately; the host must keep the last deleted setlist so that [onUndoDelete] can restore it. */
    fun onDeleteSetlist(id: String) {}
    fun onUndoDelete() {}
    fun onQueryChange(query: String) {}
    fun onSortChange(sort: SetlistSort) {}
    fun onOnlyPinnedChange(only: Boolean) {}
    fun onPrefsChange(prefs: SetlistsPrefs) {}
    fun onResetPrefs() {}
    /** "All app settings" link inside the page settings panel. */
    fun onOpenAppSettings() {}
}

object NoOpSetlistsActions : SetlistsActions

// ════════════════════════════════════════════════════════════════════════════════════════
// 2. PURE LOGIC (unit-testable, no Android dependency except java.time)
// ════════════════════════════════════════════════════════════════════════════════════════

object SetlistsLogic {
    const val MAX_NAME = 40
    const val TOP_CARDS = 4

    fun patchLabel(n: Int) = if (n == 1) "patch" else "patches"

    /** "Setlist N" with the first free N. */
    fun nextDefaultName(existing: List<Setlist>): String {
        var n = existing.size + 1
        while (existing.any { it.name == "Setlist $n" }) n++
        return "Setlist $n"
    }

    fun isNameValid(name: String) = name.trim().isNotEmpty() && name.length <= MAX_NAME

    /**
     * Automatic color on creation: the least used color (ties: palette order).
     * For a duplicate pass the source color: the copy takes the next one in the palette.
     */
    fun autoColor(existing: List<Setlist>, after: SetlistColor? = null): SetlistColor {
        val all = SetlistColor.values()
        if (after != null) return all[(after.ordinal + 1) % all.size]
        val counts = all.map { c -> existing.count { it.color == c } }
        return all[counts.indexOf(counts.minOrNull() ?: 0)]
    }

    /** The 4 most recently used setlists. Fixed: not affected by search, sort or the Pinned filter. */
    fun topCards(all: List<Setlist>): List<Setlist> =
        all.sortedByDescending { it.lastUsedAt }.take(TOP_CARDS)

    /** Search + Pinned filter + sort. Pinned setlists stay on top when [pinnedOnTop] is true. */
    fun visibleList(
        all: List<Setlist>,
        query: String,
        sort: SetlistSort,
        onlyPinned: Boolean,
        pinnedOnTop: Boolean
    ): List<Setlist> {
        val q = query.trim().lowercase()
        val base: Comparator<Setlist> = when (sort) {
            SetlistSort.Recent -> compareByDescending<Setlist> { it.lastUsedAt }
            SetlistSort.Name -> compareBy<Setlist> { it.name.lowercase() }
            SetlistSort.Patches -> compareByDescending<Setlist> { it.patchCount }
        }
        val cmp = if (pinnedOnTop) compareByDescending<Setlist> { it.isPinned }.then(base) else base
        return all
            .filter { (q.isEmpty() || it.name.lowercase().contains(q)) && (!onlyPinned || it.isPinned) }
            .sortedWith(cmp)
    }

    /** "Today, 21:51" / "Yesterday, 18:20" / "Oct 6". */
    fun formatLastUsed(
        timestampMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val d = Instant.ofEpochMilli(timestampMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val time = d.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH))
        return when (d.toLocalDate()) {
            today -> "Today, $time"
            today.minusDays(1) -> "Yesterday, $time"
            else -> d.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 3. STYLE
// ════════════════════════════════════════════════════════════════════════════════════════

internal object SlColors {
    val BgTop = AppColors.BgTop
    val BgBottom = AppColors.BgBottom
    val Ink = AppColors.Ink
    val Soft = AppColors.Soft
    val Mute = AppColors.Mute
    val Dim = AppColors.Dim
    val Accent = AppColors.Accent
    val AccentDeep = AppColors.AccentDeep
    val AccentText = AppColors.AccentText
    val Danger = AppColors.Danger
    val DangerArmed = AppColors.DangerArmed
    val DangerText = AppColors.DangerText
    val PanelTop = AppColors.PanelTop
    val PanelBottom = AppColors.PanelBottom
    val Well = AppColors.Well
}

private fun white(a: Float) = Color.White.copy(alpha = a)

private fun slText(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = SlColors.Ink
) = TextStyle(fontSize = size, fontWeight = weight, color = color, fontFeatureSettings = "tnum")

private class SlHaptics(private val h: HapticFeedback, private val on: Boolean) {
    fun tick() { if (on) h.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    fun strong() { if (on) h.performHapticFeedback(HapticFeedbackType.LongPress) }
}

private fun Modifier.slSurface(shape: Shape, open: Boolean = false, elevation: Dp = 3.dp): Modifier = this
    .shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = 0.30f), spotColor = Color.Black.copy(alpha = 0.30f))
    .clip(shape)
    .background(
        if (open) SlColors.PanelTop.copy(alpha = 0.65f)
        else SlColors.PanelBottom.copy(alpha = 0.55f)
    )

private fun Modifier.slSurfaceTinted(
    shape: Shape,
    tint: Color,
    open: Boolean = false,
    elevation: Dp = 3.dp,
): Modifier = this
    .shadow(
        elevation, shape,
        ambientColor = Color.Black.copy(alpha = 0.30f),
        spotColor = Color.Black.copy(alpha = 0.30f),
    )
    .clip(shape)
    .drawBehind {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    tint.copy(alpha = if (open) 0.32f else 0.22f),
                    tint.copy(alpha = if (open) 0.16f else 0.10f),
                    tint.copy(alpha = if (open) 0.06f else 0.03f),
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
        )
    }

private fun Modifier.slPanel(shape: Shape = RoundedCornerShape(18.dp)): Modifier = this
    .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.30f), spotColor = Color.Black.copy(alpha = 0.30f))
    .clip(shape)
    .background(Brush.verticalGradient(listOf(SlColors.PanelTop, SlColors.PanelBottom)))
    .border(1.dp, white(0.10f), shape)

private fun Modifier.slSunk(shape: Shape): Modifier = this
    .clip(shape)
    .background(SlColors.Well)
    .drawBehind {
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                startY = 0f, endY = 10.dp.toPx()
            )
        )
    }
    .border(1.dp, white(0.05f), shape)

/** Press = light spring scale-down. Apply BEFORE the surface/shadow modifiers. */
@Composable
private fun Modifier.slTap(scaleDown: Float = 0.96f, onTap: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(
        if (pressed) scaleDown else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "tap"
    )
    val tap by rememberUpdatedState(onTap)
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                onTap = { tap() }
            )
        }
}

/** Open gesture driven by the "Open a setlist with" preference. */
@Composable
private fun Modifier.slOpenGesture(
    mode: OpenGesture,
    onOpen: () -> Unit,
    onHint: (String) -> Unit
): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(
        if (pressed) 0.98f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "open"
    )
    val open by rememberUpdatedState(onOpen)
    val hint by rememberUpdatedState(onHint)
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .pointerInput(mode) {
            val dbl: ((Offset) -> Unit)? =
                if (mode == OpenGesture.DoubleTap) ({ _: Offset -> open() }) else null
            val long: ((Offset) -> Unit)? =
                if (mode == OpenGesture.Hold) ({ _: Offset -> open() }) else null
            detectTapGestures(
                onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                onDoubleTap = dbl,
                onLongPress = long,
                onTap = {
                    when (mode) {
                        OpenGesture.Tap -> open()
                        OpenGesture.DoubleTap -> hint("Double tap to open")
                        OpenGesture.Hold -> hint("Press and hold to open")
                    }
                }
            )
        }
}

/** Staggered entrance (30 ms per item). [animateIn] false = already seen, no replay on scroll. */
@Composable
private fun Modifier.slRise(index: Int, animateIn: Boolean): Modifier {
    val p = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (p.value < 1f) {
            delay((index * 30L).coerceAtMost(260L))
            p.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        }
    }
    return this.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 10.dp.toPx() }
}

@Composable
private fun Modifier.slShimmer(): Modifier {
    val t = rememberInfiniteTransition(label = "shimmer")
    val shift = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "s")
    return this.drawBehind {
        val w = size.width
        val start = -w + shift.value * 2f * w
        drawRect(
            Brush.horizontalGradient(
                listOf(white(0.04f), white(0.09f), white(0.04f)), startX = start, endX = start + w
            )
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 4. SCREEN
// ════════════════════════════════════════════════════════════════════════════════════════

private sealed interface SlOverlay {
    object None : SlOverlay
    data class Menu(val id: String, val anchor: Rect, val mode: MenuMode = MenuMode.Main) : SlOverlay
    object Create : SlOverlay
    object Settings : SlOverlay
}

private enum class MenuMode { Main, Rename, Color }

private data class SlSnack(val key: Long, val text: String, val undo: Boolean, val durationMs: Long)

@Composable
fun SetlistsScreen(
    state: SetlistsUiState,
    actions: SetlistsActions,
    modifier: Modifier = Modifier
) {
    val prefs = state.prefs
    val haptic = LocalHapticFeedback.current
    val haptics = remember(haptic, prefs.haptics) { SlHaptics(haptic, prefs.haptics) }

    var overlay by remember { mutableStateOf<SlOverlay>(SlOverlay.None) }
    var snack by remember { mutableStateOf<SlSnack?>(null) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }

    fun show(text: String, undo: Boolean = false, ms: Long = 1900L) {
        snack = SlSnack(System.nanoTime(), text, undo, ms)
    }
    LaunchedEffect(snack?.key) {
        val s = snack ?: return@LaunchedEffect
        delay(s.durationMs)
        if (snack?.key == s.key) snack = null
    }

    val setlists = state.setlists
    val visible = remember(setlists, state.query, state.sort, state.onlyPinned, prefs.pinnedOnTop) {
        SetlistsLogic.visibleList(setlists, state.query, state.sort, state.onlyPinned, prefs.pinnedOnTop)
    }
    val topCards = remember(setlists) { SetlistsLogic.topCards(setlists) }
    val totalPatches = remember(setlists) { setlists.sumOf { it.patchCount } }
    val nowMs = remember(setlists) { System.currentTimeMillis() }
    val openItem = setlists.firstOrNull { it.id == state.openSetlistId }
    val animate = prefs.animatedBackground && !state.reducedMotion
    val empty = !state.isLoading && setlists.isEmpty()
    val seen = remember { mutableSetOf<String>() }

    fun open(id: String) {
        haptics.tick()
        overlay = SlOverlay.None
        actions.onOpenSetlist(id)
    }

    fun delete(id: String) {
        haptics.strong()
        overlay = SlOverlay.None
        actions.onDeleteSetlist(id)
        show("Setlist deleted", undo = true, ms = 5000L)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SlColors.BgTop, SlColors.BgBottom)))
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
            .onSizeChanged { rootSize = it }
    ) {
        SlHalos(tint = openItem?.color?.base, animate = animate, modifier = Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize()) {
            SlTopBar(
                query = state.query,
                searchEnabled = !empty,
                settingsOpen = overlay is SlOverlay.Settings,
                setlistCount = setlists.size,
                patchCount = totalPatches,
                onCancel = { actions.onCancel() },
                onQuery = { actions.onQueryChange(it) },
                onCreate = { overlay = SlOverlay.Create },
                onSettings = {
                    overlay = if (overlay is SlOverlay.Settings) SlOverlay.None else SlOverlay.Settings
                }
            )
            Box(
                Modifier
                    .padding(horizontal = 18.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(white(0.07f))
            )

            if (!empty) {
                // Fixed zone: never moves when the list scrolls.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 9.dp)
                ) {
                    SlLabel("Recently used", Modifier.height(14.dp))
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().height(96.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.isLoading) {
                            repeat(SetlistsLogic.TOP_CARDS) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(16.dp))
                                        .slShimmer()
                                )
                            }
                        } else {
                            topCards.forEachIndexed { i, s ->
                                val animateIn = remember(s.id) { seen.add("c" + s.id) }
                                SetlistCard(
                                    s = s, index = i, animateIn = animateIn,
                                    isOpen = s.id == state.openSetlistId,
                                    isLast = s.id == state.lastOpenedId,
                                    prefs = prefs, animate = animate, nowMs = nowMs,
                                    onOpen = { open(s.id) },
                                    onHint = { show(it, ms = 1100L) },
                                    onMenu = { overlay = SlOverlay.Menu(s.id, it) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(SetlistsLogic.TOP_CARDS - topCards.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    SlListHeader(
                        sort = state.sort,
                        onlyPinned = state.onlyPinned,
                        onSort = { actions.onSortChange(it) },
                        onOnlyPinned = { actions.onOnlyPinnedChange(it) }
                    )
                }
            }

            // Only this area scrolls.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> Column(
                        Modifier.padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(4) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(if (prefs.compactRows) 38.dp else 44.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .slShimmer()
                            )
                        }
                    }

                    empty -> SlEmptyState(onCreate = { overlay = SlOverlay.Create })

                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (visible.isEmpty()) {
                            item(key = "no-match") {
                                Text(
                                    if (state.onlyPinned && state.query.isBlank()) "No pinned setlists yet."
                                    else "No setlist matches “${state.query}”.",
                                    style = slText(13.5.sp, color = SlColors.Dim),
                                    modifier = Modifier.padding(vertical = 18.dp, horizontal = 4.dp)
                                )
                            }
                        }
                        items(visible, key = { it.id }) { s ->
                            val index = visible.indexOf(s)
                            val animateIn = remember(s.id) { seen.add("r" + s.id) }
                            SetlistRow(
                                s = s, index = index, animateIn = animateIn,
                                isOpen = s.id == state.openSetlistId,
                                isLast = s.id == state.lastOpenedId,
                                prefs = prefs, haptics = haptics, nowMs = nowMs,
                                onOpen = { open(s.id) },
                                onHint = { show(it, ms = 1100L) },
                                onMenu = { overlay = SlOverlay.Menu(s.id, it) },
                                onDelete = { delete(s.id) },
                                onLockedHint = { show("Deletion is locked", ms = 1300L) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }

        // ── overlays ──
        val ov = overlay
        if (ov is SlOverlay.Menu) {
            val target = setlists.firstOrNull { it.id == ov.id }
            if (target != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(29f)
                        .pointerInput(Unit) { detectTapGestures { overlay = SlOverlay.None } }
                )
                val density = LocalDensity.current
                val menuW = with(density) { 188.dp.toPx() }
                val menuH = with(density) { 204.dp.toPx() }
                val gap = with(density) { 6.dp.toPx() }
                val pad = with(density) { 8.dp.toPx() }
                val ax = ov.anchor.right - rootOrigin.x
                val ay = ov.anchor.bottom - rootOrigin.y
                val x = (ax - menuW).coerceIn(pad, max(pad, rootSize.width - menuW - pad))
                val below = ay + gap
                val y = if (below + menuH > rootSize.height) max(pad, ov.anchor.top - rootOrigin.y - menuH - gap) else below
                Box(
                    Modifier
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .zIndex(30f)
                        .width(188.dp)
                        .slAppear()
                ) {
                    SlItemMenu(
                        s = target, mode = ov.mode, locked = prefs.lockDeletion,
                        onMode = { overlay = ov.copy(mode = it) },
                        onPin = { haptics.tick(); overlay = SlOverlay.None; actions.onTogglePin(target.id) },
                        onRename = { n -> overlay = SlOverlay.None; actions.onRenameSetlist(target.id, n.trim()) },
                        onColor = { c -> haptics.tick(); overlay = SlOverlay.None; actions.onChangeColor(target.id, c) },
                        onDuplicate = {
                            haptics.tick(); overlay = SlOverlay.None
                            actions.onDuplicateSetlist(target.id)
                            show("“${target.name} copy” created")
                        },
                        onDelete = { if (prefs.lockDeletion) show("Deletion is locked", ms = 1300L) else delete(target.id) }
                    )
                }
            }
        }

        if (ov is SlOverlay.Settings) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(44f)
                    .pointerInput(Unit) { detectTapGestures { overlay = SlOverlay.None } }
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 18.dp)
                    .zIndex(45f)
                    .slAppear()
            ) {
                SlSettingsPanel(
                    prefs = prefs,
                    onPrefs = { haptics.tick(); actions.onPrefsChange(it) },
                    onClose = { overlay = SlOverlay.None },
                    onReset = { haptics.tick(); actions.onResetPrefs() },
                    onAll = { overlay = SlOverlay.None; actions.onOpenAppSettings() }
                )
            }
        }

        if (ov is SlOverlay.Create) {
            SlCreateSheet(
                setlists = setlists,
                openItem = openItem,
                onClose = { overlay = SlOverlay.None },
                onCreate = { name, mode ->
                    haptics.tick()
                    overlay = SlOverlay.None
                    actions.onCreateSetlist(name.trim(), mode)
                    show("“${name.trim()}” created")
                },
                modifier = Modifier.zIndex(40f)
            )
        }

        snack?.let { sn ->
            SlSnackbar(
                snack = sn,
                onUndo = { actions.onUndoDelete(); snack = null },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).zIndex(60f)
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.1 TOP BAR
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SlTopBar(
    query: String,
    searchEnabled: Boolean,
    settingsOpen: Boolean,
    setlistCount: Int,
    patchCount: Int,
    onCancel: () -> Unit,
    onQuery: (String) -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 18.dp)
    ) {
        Row(
            Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SlGhostButton(onClick = onCancel) {
                Text("Cancel", style = slText(14.5.sp, FontWeight.Bold, Color(0xFFD3DAE8)))
            }
            SlSearchField(query, searchEnabled, onQuery, Modifier.width(230.dp))
        }

        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Setlists", style = slText(20.sp, FontWeight.ExtraBold, Color(0xFFF4F6FB)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RollNumber(setlistCount, slText(10.sp, color = SlColors.Dim))
                Text(
                    " ${if (setlistCount == 1) "setlist" else "setlists"} · ",
                    style = slText(10.sp, color = SlColors.Dim)
                )
                RollNumber(patchCount, slText(10.sp, color = SlColors.Dim))
                Text(
                    " ${SetlistsLogic.patchLabel(patchCount)}",
                    style = slText(10.sp, color = SlColors.Dim)
                )
            }
        }

        Row(
            Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SlPrimaryButton(onClick = onCreate) {
                SlIcon(SlIconKind.Plus, SlColors.AccentText, Modifier.size(16.dp), stroke = 2.6.dp)
                Spacer(Modifier.width(7.dp))
                Text("Create New Setlist", style = slText(14.sp, FontWeight.ExtraBold, SlColors.AccentText))
            }
            Box(
                Modifier
                    .size(38.dp)
                    .slTap(0.94f, onSettings)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (settingsOpen) white(0.12f) else white(0.045f))
                    .border(1.dp, white(if (settingsOpen) 0.28f else 0.12f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) { SlIcon(SlIconKind.Gear, Color(0xFFD3DAE8), Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun SlSearchField(query: String, enabled: Boolean, onChange: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier
            .height(38.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(Color(0xB30F141D))
            .border(1.dp, white(0.10f), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SlIcon(SlIconKind.Search, SlColors.Mute, Modifier.size(16.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search setlists", style = slText(14.sp, color = SlColors.Mute))
            BasicTextField(
                value = query,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                textStyle = slText(14.sp),
                cursorBrush = SolidColor(SlColors.Accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(20.dp).slTap(0.85f) { onChange("") }, contentAlignment = Alignment.Center) {
                SlIcon(SlIconKind.Close, SlColors.Mute, Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun SlGhostButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier
            .height(38.dp)
            .slTap(0.96f, onClick)
            .clip(shape)
            .background(white(0.045f))
            .border(1.dp, white(0.12f), shape)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun SlPrimaryButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        Modifier
            .height(38.dp)
            .slTap(0.96f, onClick)
            .shadow(4.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF9DBDEB), SlColors.AccentDeep)))
            .border(1.dp, white(0.2f), shape)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.2 FIXED ZONE: header row (All setlists · Pinned · Sort by)
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SlLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = TextStyle(
            fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, color = Color(0xFF7B859A)
        ),
        maxLines = 1
    )
}

@Composable
private fun SlListHeader(
    sort: SetlistSort,
    onlyPinned: Boolean,
    onSort: (SetlistSort) -> Unit,
    onOnlyPinned: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 9.dp, bottom = 3.dp)
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SlLabel("All setlists", Modifier.weight(1f))
        SlChip(
            label = "Pinned", selected = onlyPinned, onClick = { onOnlyPinned(!onlyPinned) },
            icon = SlIconKind.Pin
        )
        Spacer(Modifier.width(14.dp))
        Text("Sort by", style = slText(12.sp, color = SlColors.Dim))
        Spacer(Modifier.width(10.dp))
        SlSegmented(
            options = SetlistSort.values().map { it to it.label },
            selected = sort,
            onSelect = onSort,
            modifier = Modifier.width(66.dp * 3 + 6.dp),
            height = 28.dp
        )
    }
}

@Composable
private fun SlChip(label: String, selected: Boolean, onClick: () -> Unit, icon: SlIconKind) {
    val shape = RoundedCornerShape(13.dp)
    val bg by animateColorAsState(if (selected) white(0.12f) else Color.Transparent, tween(250), label = "chipbg")
    val fg by animateColorAsState(if (selected) SlColors.Ink else SlColors.Mute, tween(250), label = "chipfg")
    Row(
        Modifier
            .height(26.dp)
            .slTap(0.95f, onClick)
            .clip(shape)
            .background(bg)
            .border(1.dp, white(if (selected) 0.24f else 0.09f), shape)
            .padding(start = 9.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SlIcon(icon, fg, Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = slText(12.sp, FontWeight.Bold, fg))
    }
}

/** Segmented control with a spring-loaded sliding thumb. Pass a width (fixed or fillMaxWidth). */
@Composable
private fun <T> SlSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 34.dp
) {
    val idx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(
        modifier
            .height(height)
            .clip(shape)
            .background(Color(0xCC0F141D))
            .border(1.dp, white(0.08f), shape)
            .padding(3.dp)
    ) {
        val segW = maxWidth / options.size
        val thumbX by animateDpAsState(
            segW * idx, spring(dampingRatio = 0.7f, stiffness = 500f), label = "thumb"
        )
        Box(
            Modifier
                .offset(x = thumbX)
                .width(segW)
                .fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(9.dp))
                .clip(RoundedCornerShape(9.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF38445E), Color(0xFF2B3650))))
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { (value, label) ->
                val on = value == selected
                val c by animateColorAsState(if (on) Color(0xFFF1F4FA) else SlColors.Mute, tween(200), label = "segc")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(value) { detectTapGestures { onSelect(value) } },
                    contentAlignment = Alignment.Center
                ) { Text(label, style = slText(11.5.sp, FontWeight.Bold, c), maxLines = 1) }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.3 TOP CARD (aura + faint tinted contour, no side bar, no monogram)
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SetlistCard(
    s: Setlist,
    index: Int,
    animateIn: Boolean,
    isOpen: Boolean,
    isLast: Boolean,
    prefs: SetlistsPrefs,
    animate: Boolean,
    nowMs: Long,
    onOpen: () -> Unit,
    onHint: (String) -> Unit,
    onMenu: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val c = s.color
    val contour by animateFloatAsState(if (isOpen) 0.40f else if (isLast) 0.32f else 0.24f, tween(400), label = "contour")
    val auraAlpha by animateFloatAsState(if (!prefs.cardAuras) 0f else if (isOpen) 0.28f else if (isLast) 0.22f else 0.16f, tween(400), label = "aura")
    // The aura is a small tinted bubble drifting very slowly (60–80 s per leg).
    val dur = (60 + (s.id.hashCode().absoluteValue % 5) * 5) * 1000
    val inf = rememberInfiniteTransition(label = "auraDrift")
    val dx: State<Float> = if (animate) inf.animateFloat(
        0f, 90f, infiniteRepeatable(tween(dur, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dx"
    ) else remember { mutableStateOf(0f) }
    val dy: State<Float> = if (animate) inf.animateFloat(
        -18f, 34f, infiniteRepeatable(tween((dur * 0.8f).toInt(), easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dy"
    ) else remember { mutableStateOf(0f) }

    Box(
        modifier
            .height(96.dp)
            .slRise(index, animateIn)
            .slOpenGesture(prefs.openWith, onOpen, onHint)
            .slSurfaceTinted(shape, tint = c.base, open = isOpen, elevation = 3.dp)
            .drawBehind {
                if (auraAlpha > 0.001f) {
                    val r = 110.dp.toPx()
                    val center = Offset(45.dp.toPx() + dx.value.dp.toPx(), 35.dp.toPx() + dy.value.dp.toPx())
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to c.base.copy(alpha = auraAlpha),
                            0.66f to c.base.copy(alpha = 0f),
                            center = center, radius = r
                        ),
                        radius = r, center = center
                    )
                }
            }
            .drawWithContent {
                drawContent()
                val sw = 1.dp.toPx()
                val inset = sw / 2f
                val r = 16.dp.toPx() - inset
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            c.base.copy(alpha = contour),
                            c.base.copy(alpha = contour * 0.35f),
                            white(0.05f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - sw, size.height - sw),
                    cornerRadius = CornerRadius(r),
                    style = Stroke(width = sw)
                )
            }
            .padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 10.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    s.name,
                    style = slText(16.sp, FontWeight.Bold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isOpen) OpenTag(c)
                if (s.isPinned) SlIcon(SlIconKind.Pin, Color(0xFF8F99AD), Modifier.size(12.dp))
                MenuDotsButton(26.dp, onMenu)
            }
            Spacer(Modifier.height(6.dp))
            Row {
                RollNumber(s.patchCount, slText(13.sp, FontWeight.SemiBold, Color(0xFFB4BED0)))
                Text(
                    " ${SetlistsLogic.patchLabel(s.patchCount)}",
                    style = slText(13.sp, FontWeight.SemiBold, Color(0xFFB4BED0))
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "Last used: ${SetlistsLogic.formatLastUsed(s.lastUsedAt, nowMs)}",
                style = slText(10.sp, color = SlColors.Dim),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** "OPEN" in the color of the card or row where it sits. */
@Composable
private fun OpenTag(c: SetlistColor) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        "OPEN",
        style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = c.text),
        modifier = Modifier
            .clip(shape)
            .background(c.base.copy(alpha = 0.16f))
            .border(1.dp, c.base.copy(alpha = 0.28f), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun MenuDotsButton(size: Dp, onClick: (Rect) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        Modifier
            .size(size)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .clip(CircleShape)
            .pointerInput(Unit) { detectTapGestures { onClick(bounds) } },
        contentAlignment = Alignment.Center
    ) { SlIcon(SlIconKind.Dots, Color(0xFF7B859A), Modifier.size(size * 0.6f)) }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.4 LIST ROW (neutral; only the patch dots carry color) with swipe-left delete
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SetlistRow(
    s: Setlist,
    index: Int,
    animateIn: Boolean,
    isOpen: Boolean,
    isLast: Boolean,
    prefs: SetlistsPrefs,
    haptics: SlHaptics,
    nowMs: Long,
    onOpen: () -> Unit,
    onHint: (String) -> Unit,
    onMenu: (Rect) -> Unit,
    onDelete: () -> Unit,
    onLockedHint: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rowH = if (prefs.compactRows) 38.dp else 44.dp
    val shape = RoundedCornerShape(13.dp)
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val locked = prefs.lockDeletion
    val gapPx = with(density) { 8.dp.toPx() }
    val pillW = max(0f, -offset.value - gapPx)

    Box(
        modifier
            .fillMaxWidth()
            .height(rowH)
            .slRise(index, animateIn)
            .onSizeChanged { widthPx = it.width }
    ) {
        // Delete pill, detached from the row by an 8 dp gap.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(with(density) { pillW.toDp() })
                .alpha(min(1f, pillW / with(density) { 40.dp.toPx() }))
                .clip(shape)
                .background(if (armed) SlColors.DangerArmed else SlColors.Danger),
            contentAlignment = Alignment.Center
        ) {
            if (pillW > with(density) { 36.dp.toPx() }) {
                val rot by animateFloatAsState(if (armed) -14f else 0f, spring(0.5f, 500f), label = "rot")
                val sc by animateFloatAsState(if (armed) 1.15f else 1f, spring(0.5f, 500f), label = "sc")
                SlIcon(
                    SlIconKind.Trash, Color.White,
                    Modifier.size(19.dp).graphicsLayer { rotationZ = rot; scaleX = sc; scaleY = sc }
                )
            }
        }

        Row(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .fillMaxSize()
                .pointerInput(locked) {
                    var total = 0f
                    var hinted = false
                    val resist = 24.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f; hinted = false },
                        onDragEnd = {
                            scope.launch {
                                if (!locked && armed) {
                                    offset.animateTo(-widthPx.toFloat(), tween(180))
                                    onDelete()
                                } else {
                                    offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f))
                                }
                                armed = false
                            }
                        },
                        onDragCancel = {
                            scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f)) }
                            armed = false
                        },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            if (locked) {
                                if (!hinted && dx < 0f) { hinted = true; onLockedHint() }
                                return@detectHorizontalDragGestures
                            }
                            total += dx
                            val d = min(0f, total)
                            val eff = if (d > -resist) d else -resist + (d + resist) * 0.75f
                            scope.launch { offset.snapTo(eff) }
                            val threshold = min(size.width * 0.4f, 200.dp.toPx())
                            val a = eff <= -threshold
                            if (a != armed) { armed = a; if (a) haptics.strong() }
                        }
                    )
                }
                .slOpenGesture(prefs.openWith, onOpen, onHint)
                .slSurface(shape, open = isOpen, elevation = 2.dp)
                .border(
                    1.dp,
                    white(if (isOpen) 0.17f else if (isLast) 0.12f else 0.07f),
                    shape
                )
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    s.name, style = slText(15.sp, FontWeight.Bold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                if (isOpen) OpenTag(s.color)
            }
            Row(Modifier.width(84.dp)) {
                RollNumber(s.patchCount, slText(12.5.sp, FontWeight.SemiBold, Color(0xFFA3AEC2)))
                Text(
                    " ${SetlistsLogic.patchLabel(s.patchCount)}",
                    style = slText(12.5.sp, FontWeight.SemiBold, Color(0xFFA3AEC2))
                )
            }
            if (prefs.patchDots) Box(Modifier.width(126.dp)) { PatchDots(s.patchCount, s.color.base) }
            Text(
                "Last used: ${SetlistsLogic.formatLastUsed(s.lastUsedAt, nowMs)}",
                style = slText(11.5.sp, color = SlColors.Dim),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(132.dp)
            )
            Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                if (s.isPinned) SlIcon(SlIconKind.Pin, Color(0xFF8F99AD), Modifier.size(14.dp))
            }
            MenuDotsButton(32.dp, onMenu)
        }
    }
}

/** One dot per patch, in order. Color only lives here (and in the card aura). */
@Composable
private fun PatchDots(n: Int, color: Color, max: Int = 12) {
    val c = min(n, max)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until c) {
            val a = 0.35f + 0.55f * (if (c > 1) i / (c - 1f) else 1f)
            Box(Modifier.size(5.dp).clip(CircleShape).background(color.copy(alpha = a)))
        }
        if (n > max) Text("+${n - max}", style = slText(10.sp, color = SlColors.Mute))
        if (n == 0) Text("empty", style = slText(10.sp, color = Color(0xFF566075)))
    }
}

/** The new value slides in from below whenever the number changes. */
@Composable
private fun RollNumber(value: Int, style: TextStyle) {
    val offset = remember { Animatable(0f) }
    var shown by remember { mutableIntStateOf(value) }
    LaunchedEffect(value) {
        if (value != shown) {
            shown = value
            offset.snapTo(9f)
            offset.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        }
    }
    Text(
        "$shown", style = style, maxLines = 1,
        modifier = Modifier.graphicsLayer {
            translationY = offset.value.dp.toPx()
            alpha = 1f - (offset.value / 9f) * 0.8f
        }
    )
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.5 EMPTY STATE
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SlEmptyState(onCreate: () -> Unit) {
    val t = rememberInfiniteTransition(label = "empty")
    val a by t.animateFloat(
        0.35f, 0.7f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe"
    )
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .width(204.dp)
                .height(76.dp)
                .alpha(a)
                .clip(RoundedCornerShape(16.dp))
                .background(white(0.025f))
                .drawBehind {
                    drawRoundRect(
                        color = white(0.45f),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)))
                    )
                },
            contentAlignment = Alignment.Center
        ) { SlIcon(SlIconKind.Plus, white(0.6f), Modifier.size(24.dp)) }
        Spacer(Modifier.height(14.dp))
        Text("Your first setlist starts here", style = slText(19.sp, FontWeight.ExtraBold, Color(0xFFF1F4FA)))
        Spacer(Modifier.height(4.dp))
        Text("Group patches for a show.", style = slText(13.sp, color = SlColors.Mute))
        Spacer(Modifier.height(14.dp))
        SlPrimaryButton(onClick = onCreate) {
            Text("Create setlist", style = slText(14.sp, FontWeight.ExtraBold, SlColors.AccentText))
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.6 LIVING BACKGROUND HALOS (4 soft bubbles, 62–110 s cycles, tint follows the open setlist)
// ────────────────────────────────────────────────────────────────────────────────────────

private data class HaloCfg(
    val base: Color, val radiusDp: Float, val ax: Float, val ay: Float, val by: Float,
    val fx: Float, val fy: Float, val px: Float, val py: Float, val alpha: Float, val mix: Float
)

private val HALOS = listOf(
    HaloCfg(Color(0xFF7F98C4), 230f, 0.34f, 0.30f, 0.60f, 0.0000739f, 0.0000898f, 0.0f, 1.2f, 0.12f, 0.50f),
    HaloCfg(Color(0xFF566C9A), 280f, 0.38f, 0.26f, 0.56f, 0.0000571f, 0.0000838f, 2.1f, 0.4f, 0.10f, 0.45f),
    HaloCfg(Color(0xFFA8884F), 170f, 0.30f, 0.34f, 0.62f, 0.0000967f, 0.0000661f, 4.0f, 2.6f, 0.05f, 0.60f),
    HaloCfg(Color(0xFF9A5670), 200f, 0.28f, 0.30f, 0.58f, 0.0000785f, 0.0001013f, 1.0f, 5.0f, 0.05f, 0.60f)
)

@Composable
private fun SlHalos(tint: Color?, animate: Boolean, modifier: Modifier) {
    var timeMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameMillis { t ->
                if (t - last >= 33L) { last = t; timeMs = t } // ~30 fps cap
            }
        }
    }
    var lastTint by remember { mutableStateOf(Color(0xFF8FB2E8)) }
    if (tint != null) lastTint = tint
    val tintColor by animateColorAsState(lastTint, tween(600), label = "haloTint")
    val tintAmt by animateFloatAsState(if (tint != null) 1f else 0f, tween(600), label = "haloAmt")

    Canvas(modifier) {
        val t = timeMs.toFloat()
        HALOS.forEach { b ->
            val col = lerp(b.base, lerp(b.base, tintColor, b.mix), tintAmt)
            val r = b.radiusDp.dp.toPx()
            val x = size.width * (0.5f + b.ax * sin(t * b.fx + b.px))
            val y = size.height * (b.by + b.ay * cos(t * b.fy + b.py) * (0.75f + 0.25f * sin(t * b.fy * 0.31f + b.px)))
            val center = Offset(x, y)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to col.copy(alpha = b.alpha),
                    0.68f to col.copy(alpha = 0f),
                    center = center, radius = r
                ),
                radius = r, center = center
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.7 ITEM MENU (⋮): Pin · Rename · Change color · Duplicate · Delete
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SlItemMenu(
    s: Setlist,
    mode: MenuMode,
    locked: Boolean,
    onMode: (MenuMode) -> Unit,
    onPin: () -> Unit,
    onRename: (String) -> Unit,
    onColor: (SetlistColor) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Column(Modifier.fillMaxWidth().slPanel(RoundedCornerShape(16.dp)).padding(6.dp)) {
        when (mode) {
            MenuMode.Main -> {
                MenuRow(SlIconKind.Pin, if (s.isPinned) "Unpin" else "Pin to top", SlColors.Soft, onPin)
                MenuRow(SlIconKind.Edit, "Rename", SlColors.Soft) { onMode(MenuMode.Rename) }
                MenuRow(SlIconKind.Swatch, "Change color", SlColors.Soft) { onMode(MenuMode.Color) }
                MenuRow(SlIconKind.Copy, "Duplicate", SlColors.Soft, onDuplicate)
                Box(Modifier.padding(horizontal = 6.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(white(0.07f)))
                MenuRow(
                    if (locked) SlIconKind.Lock else SlIconKind.Trash, "Delete",
                    if (locked) SlColors.Dim else SlColors.DangerText, onDelete
                )
            }
            MenuMode.Color -> Column(Modifier.padding(8.dp)) {
                SlLabel("Color")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SetlistColor.values().forEach { c ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .slTap(0.9f) { onColor(c) }
                                .clip(CircleShape)
                                .background(c.base)
                                .border(2.dp, if (c == s.color) Color.White else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { if (c == s.color) SlIcon(SlIconKind.Check, SlColors.AccentText, Modifier.size(15.dp), stroke = 3.dp) }
                    }
                }
            }
            MenuMode.Rename -> {
                var tf by remember { mutableStateOf(TextFieldValue(s.name, TextRange(0, s.name.length))) }
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Column(Modifier.padding(8.dp)) {
                    SlLabel("Rename")
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value = tf,
                        onValueChange = { if (it.text.length <= SetlistsLogic.MAX_NAME) tf = it },
                        singleLine = true,
                        textStyle = slText(14.sp),
                        cursorBrush = SolidColor(SlColors.Accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (SetlistsLogic.isNameValid(tf.text)) onRename(tf.text) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .focusRequester(focus)
                            .slSunk(RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .slTap(0.96f) { if (SetlistsLogic.isNameValid(tf.text)) onRename(tf.text) }
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlColors.Accent),
                        contentAlignment = Alignment.Center
                    ) { Text("Save", style = slText(13.sp, FontWeight.ExtraBold, SlColors.AccentText)) }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: SlIconKind, label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .slTap(0.97f, onClick)
            .clip(RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SlIcon(icon, color, Modifier.size(16.dp))
        Text(label, style = slText(13.5.sp, FontWeight.SemiBold, color))
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.8 CREATE SHEET (name field + Duplicate / Blank)
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SlCreateSheet(
    setlists: List<Setlist>,
    openItem: Setlist?,
    onClose: () -> Unit,
    onCreate: (String, CreateMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val initial = remember { SetlistsLogic.nextDefaultName(setlists) }
    var tf by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    var mode by remember { mutableStateOf(CreateMode.Blank) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val valid = SetlistsLogic.isNameValid(tf.text) && (mode == CreateMode.Blank || openItem != null)
    fun submit() { if (valid) onCreate(tf.text, mode) }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x9E060910))
                .pointerInput(Unit) { detectTapGestures { onClose() } }
        )
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(460.dp)
                    .slAppear()
                    .slPanel(RoundedCornerShape(22.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("New setlist", style = slText(19.sp, FontWeight.ExtraBold, Color(0xFFF1F4FA)), modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(30.dp).slTap(0.9f, onClose).clip(CircleShape).background(white(0.06f)),
                        contentAlignment = Alignment.Center
                    ) { SlIcon(SlIconKind.Close, Color(0xFF9AA5BA), Modifier.size(15.dp)) }
                }
                Column {
                    Row {
                        SlLabel("Name", Modifier.weight(1f))
                        Text("${tf.text.length}/${SetlistsLogic.MAX_NAME}", style = slText(10.sp, FontWeight.Bold, Color(0xFF7B859A)))
                    }
                    Spacer(Modifier.height(6.dp))
                    val shape = RoundedCornerShape(12.dp)
                    BasicTextField(
                        value = tf,
                        onValueChange = { if (it.text.length <= SetlistsLogic.MAX_NAME) tf = it },
                        singleLine = true,
                        textStyle = slText(16.sp, FontWeight.SemiBold, Color(0xFFF1F4FA)),
                        cursorBrush = SolidColor(SlColors.Accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .focusRequester(focus)
                            .slSunk(shape)
                            .border(1.dp, SlColors.Accent.copy(alpha = 0.4f), shape)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CreateTile(
                        modifier = Modifier.weight(1f),
                        icon = SlIconKind.Copy, title = "Duplicate",
                        sub = if (openItem != null) "Copy of ${openItem.name}" else "No setlist open",
                        selected = mode == CreateMode.Duplicate, enabled = openItem != null,
                        onClick = { mode = CreateMode.Duplicate }
                    )
                    CreateTile(
                        modifier = Modifier.weight(1f),
                        icon = SlIconKind.Plus, title = "Blank", sub = "Start with no patches",
                        selected = mode == CreateMode.Blank, enabled = true,
                        onClick = { mode = CreateMode.Blank }
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    SlGhostButton(onClick = onClose) {
                        Text("Cancel", style = slText(14.sp, FontWeight.Bold, Color(0xFFC7CFDD)))
                    }
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .height(38.dp)
                            .alpha(if (valid) 1f else 0.5f)
                            .slTap(0.96f) { submit() }
                            .clip(shape)
                            .background(
                                if (valid) Brush.verticalGradient(listOf(Color(0xFF9DBDEB), SlColors.AccentDeep))
                                else SolidColor(SlColors.Accent.copy(alpha = 0.2f))
                            )
                            .padding(horizontal = 26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Create",
                            style = slText(14.sp, FontWeight.ExtraBold, if (valid) SlColors.AccentText else white(0.35f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateTile(
    modifier: Modifier,
    icon: SlIconKind,
    title: String,
    sub: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val border by animateColorAsState(
        if (selected) SlColors.Accent.copy(alpha = 0.65f) else white(0.08f), tween(200), label = "tileBorder"
    )
    val iconBg by animateColorAsState(if (selected) SlColors.Accent else white(0.07f), tween(200), label = "tileIcon")
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .slTap(0.97f) { if (enabled) onClick() }
            .clip(shape)
            .background(if (selected) white(0.08f) else white(0.04f))
            .border(1.dp, border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            SlIcon(icon, if (selected) SlColors.AccentText else Color(0xFF9AA5BA), Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = slText(15.sp, FontWeight.Bold))
            Text(sub, style = slText(11.5.sp, color = SlColors.Mute), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) SlColors.Accent else Color.Transparent)
                .border(1.5.dp, if (selected) Color.Transparent else white(0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) { if (selected) SlIcon(SlIconKind.Check, SlColors.AccentText, Modifier.size(13.dp), stroke = 3.dp) }
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.9 PAGE SETTINGS PANEL (opened by the gear; NOT the global settings overlay)
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SlSettingsPanel(
    prefs: SetlistsPrefs,
    onPrefs: (SetlistsPrefs) -> Unit,
    onClose: () -> Unit,
    onReset: () -> Unit,
    onAll: () -> Unit
) {
    Column(
        Modifier
            .widthIn(max = 540.dp)
            .fillMaxWidth()
            .slPanel(RoundedCornerShape(18.dp))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Page settings", style = slText(16.sp, FontWeight.ExtraBold, Color(0xFFF1F4FA)), modifier = Modifier.weight(1f))
            Box(
                Modifier.size(28.dp).slTap(0.9f, onClose).clip(CircleShape).background(white(0.06f)),
                contentAlignment = Alignment.Center
            ) { SlIcon(SlIconKind.Close, Color(0xFF9AA5BA), Modifier.size(14.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f)) {
                SlLabel("Display", Modifier.height(18.dp))
                SettingToggle("Animated background", prefs.animatedBackground) { onPrefs(prefs.copy(animatedBackground = it)) }
                SettingToggle("Card auras", prefs.cardAuras) { onPrefs(prefs.copy(cardAuras = it)) }
                SettingToggle("Compact rows", prefs.compactRows) { onPrefs(prefs.copy(compactRows = it)) }
                SettingToggle("Patch dots", prefs.patchDots) { onPrefs(prefs.copy(patchDots = it)) }
                Spacer(Modifier.height(8.dp))
                SlLabel("Safety", Modifier.height(18.dp))
                SettingToggle("Lock deletion", prefs.lockDeletion) { onPrefs(prefs.copy(lockDeletion = it)) }
                SettingToggle("Haptics", prefs.haptics) { onPrefs(prefs.copy(haptics = it)) }
            }
            Column(Modifier.weight(1f)) {
                SlLabel("Behavior", Modifier.height(18.dp))
                Text(
                    "Open a setlist with", style = slText(13.5.sp, FontWeight.SemiBold, SlColors.Soft),
                    modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
                )
                SlSegmented(
                    options = OpenGesture.values().map { it to it.label },
                    selected = prefs.openWith,
                    onSelect = { onPrefs(prefs.copy(openWith = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    height = 34.dp
                )
                Spacer(Modifier.height(8.dp))
                SettingToggle("Pinned on top", prefs.pinnedOnTop) { onPrefs(prefs.copy(pinnedOnTop = it)) }
                SettingToggle("Remember sort", prefs.rememberSort) { onPrefs(prefs.copy(rememberSort = it)) }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.height(30.dp).slTap(0.96f, onReset),
                    contentAlignment = Alignment.CenterStart
                ) { Text("Reset page settings", style = slText(12.5.sp, FontWeight.Bold, Color(0xFFC7CFDD))) }
                Box(
                    Modifier.height(30.dp).slTap(0.96f, onAll),
                    contentAlignment = Alignment.CenterStart
                ) { Text("All app settings", style = slText(12.5.sp, FontWeight.Bold, SlColors.Accent)) }
            }
        }
    }
}

@Composable
private fun SettingToggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(label, style = slText(13.5.sp, FontWeight.SemiBold, SlColors.Soft), modifier = Modifier.weight(1f), maxLines = 1)
        SlSwitch(on, onChange)
    }
}

@Composable
private fun SlSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (on) SlColors.Accent else white(0.14f), tween(250), label = "swTrack")
    val knob by animateColorAsState(if (on) SlColors.AccentText else Color(0xFFC7CFDD), tween(250), label = "swKnob")
    val x by animateDpAsState(if (on) 20.dp else 2.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "swX")
    Box(
        Modifier
            .size(width = 40.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(track)
            .pointerInput(on) { detectTapGestures { onChange(!on) } }
    ) {
        Box(Modifier.offset(x = x, y = 2.dp).size(18.dp).clip(CircleShape).background(knob))
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// 4.10 SNACKBAR (with Undo for deletions)
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SlSnackbar(snack: SlSnack, onUndo: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val progress = remember(snack.key) { Animatable(1f) }
    LaunchedEffect(snack.key) {
        if (snack.undo) progress.animateTo(0f, tween(snack.durationMs.toInt(), easing = LinearEasing))
    }
    Box(modifier.slAppear()) {
        Box(
            Modifier
                .widthIn(min = 260.dp)
                .shadow(12.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(Color(0xFF222C40))
                .border(1.dp, white(0.10f), shape)
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(snack.text, style = slText(13.5.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f, fill = false))
                if (snack.undo) {
                    Text(
                        "UNDO",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = SlColors.Accent),
                        modifier = Modifier.slTap(0.92f, onUndo).padding(4.dp)
                    )
                }
            }
            if (snack.undo) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress.value)
                        .height(3.dp)
                        .background(SlColors.Accent)
                )
            }
        }
    }
}

/** Fade + small rise on first composition (menus, panels, snackbar). */
@Composable
private fun Modifier.slAppear(): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 8.dp.toPx()
        val s = 0.96f + 0.04f * p.value
        scaleX = s; scaleY = s
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 5. ICONS (drawn, no material-icons dependency)
// ════════════════════════════════════════════════════════════════════════════════════════

private enum class SlIconKind { Search, Gear, Plus, Dots, Pin, Trash, Copy, Check, Close, Edit, Swatch, Lock }

@Composable
private fun SlIcon(kind: SlIconKind, color: Color, modifier: Modifier, stroke: Dp = 2.dp) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = stroke.toPx()
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2), sw, StrokeCap.Round)
        when (kind) {
            SlIconKind.Search -> {
                drawCircle(color, w * 0.30f, Offset(w * 0.45f, h * 0.45f), style = st)
                line(0.68f, 0.68f, 0.92f, 0.92f)
            }
            SlIconKind.Plus -> { line(0.5f, 0.1f, 0.5f, 0.9f); line(0.1f, 0.5f, 0.9f, 0.5f) }
            SlIconKind.Close -> { line(0.15f, 0.15f, 0.85f, 0.85f); line(0.85f, 0.15f, 0.15f, 0.85f) }
            SlIconKind.Dots -> {
                val r = w * 0.09f
                drawCircle(color, r, Offset(w / 2f, h * 0.2f))
                drawCircle(color, r, Offset(w / 2f, h * 0.5f))
                drawCircle(color, r, Offset(w / 2f, h * 0.8f))
            }
            SlIconKind.Gear -> {
                drawCircle(color, w * 0.22f, Offset(w / 2f, h / 2f), style = st)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4.0
                    val cx = w / 2f
                    val cy = h / 2f
                    drawLine(
                        color,
                        Offset(cx + (w * 0.34f * cos(a)).toFloat(), cy + (h * 0.34f * sin(a)).toFloat()),
                        Offset(cx + (w * 0.46f * cos(a)).toFloat(), cy + (h * 0.46f * sin(a)).toFloat()),
                        sw * 1.6f, StrokeCap.Round
                    )
                }
                drawCircle(color, w * 0.36f, Offset(w / 2f, h / 2f), style = st)
            }
            SlIconKind.Pin -> {
                line(0.5f, 0.66f, 0.5f, 0.95f)
                val p = Path().apply {
                    moveTo(w * 0.35f, h * 0.10f); lineTo(w * 0.65f, h * 0.10f); lineTo(w * 0.60f, h * 0.38f)
                    lineTo(w * 0.76f, h * 0.54f); lineTo(w * 0.24f, h * 0.54f); lineTo(w * 0.40f, h * 0.38f); close()
                }
                drawPath(p, color, style = st)
            }
            SlIconKind.Trash -> {
                line(0.15f, 0.25f, 0.85f, 0.25f)
                line(0.38f, 0.25f, 0.38f, 0.10f); line(0.38f, 0.10f, 0.62f, 0.10f); line(0.62f, 0.10f, 0.62f, 0.25f)
                val p = Path().apply {
                    moveTo(w * 0.25f, h * 0.25f); lineTo(w * 0.30f, h * 0.90f)
                    lineTo(w * 0.70f, h * 0.90f); lineTo(w * 0.75f, h * 0.25f)
                }
                drawPath(p, color, style = st)
                line(0.42f, 0.42f, 0.42f, 0.74f); line(0.58f, 0.42f, 0.58f, 0.74f)
            }
            SlIconKind.Copy -> {
                drawRoundRect(color, Offset(w * 0.34f, h * 0.34f), Size(w * 0.56f, h * 0.56f), CornerRadius(w * 0.1f), style = st)
                val p = Path().apply {
                    moveTo(w * 0.22f, h * 0.66f); lineTo(w * 0.18f, h * 0.66f)
                    lineTo(w * 0.10f, h * 0.58f); lineTo(w * 0.10f, h * 0.18f)
                    lineTo(w * 0.18f, h * 0.10f); lineTo(w * 0.58f, h * 0.10f); lineTo(w * 0.66f, h * 0.18f)
                }
                drawPath(p, color, style = st)
            }
            SlIconKind.Check -> { line(0.2f, 0.52f, 0.42f, 0.74f); line(0.42f, 0.74f, 0.82f, 0.28f) }
            SlIconKind.Edit -> {
                val p = Path().apply {
                    moveTo(w * 0.15f, h * 0.85f); lineTo(w * 0.15f, h * 0.65f); lineTo(w * 0.65f, h * 0.15f)
                    lineTo(w * 0.85f, h * 0.35f); lineTo(w * 0.35f, h * 0.85f); close()
                }
                drawPath(p, color, style = st)
            }
            SlIconKind.Swatch -> {
                drawCircle(color, w * 0.38f, Offset(w / 2f, h / 2f), style = st)
                drawCircle(color, w * 0.05f, Offset(w * 0.38f, h * 0.42f))
                drawCircle(color, w * 0.05f, Offset(w * 0.58f, h * 0.38f))
                drawCircle(color, w * 0.05f, Offset(w * 0.62f, h * 0.60f))
            }
            SlIconKind.Lock -> {
                drawRoundRect(color, Offset(w * 0.2f, h * 0.46f), Size(w * 0.6f, h * 0.44f), CornerRadius(w * 0.1f), style = st)
                drawArc(
                    color, 180f, 180f, false, Offset(w * 0.32f, h * 0.12f), Size(w * 0.36f, h * 0.68f), style = st
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 6. PREVIEW (interactive: use "Interactive mode" in Android Studio)
// ════════════════════════════════════════════════════════════════════════════════════════

@Preview(widthDp = 891, heightDp = 411, showBackground = true, backgroundColor = 0xFF141B27)
@Composable
private fun SetlistsScreenPreview() {
    val now = System.currentTimeMillis()
    val hour = 3600_000L
    var state by remember {
        mutableStateOf(
            SetlistsUiState(
                setlists = listOf(
                    Setlist("1", "Main Stage", 12, SetlistColor.Blue, true, now - 3 * hour),
                    Setlist("2", "Studio Jams", 7, SetlistColor.Amber, false, now - hour / 2),
                    Setlist("3", "Ambient Pads", 9, SetlistColor.Sage, false, now - 27 * hour),
                    Setlist("4", "Acoustic Night", 5, SetlistColor.Rose, false, now - 6 * 24 * hour),
                    Setlist("5", "Warm-Up", 3, SetlistColor.Blue, false, now - 5 * hour),
                    Setlist("6", "Sunday Service", 8, SetlistColor.Amber, true, now - 3 * 24 * hour),
                    Setlist("7", "Rehearsal", 6, SetlistColor.Sage, false, now - 9 * 24 * hour)
                ),
                lastOpenedId = "2"
            )
        )
    }
    var lastDeleted by remember { mutableStateOf<Setlist?>(null) }
    val actions = remember {
        object : SetlistsActions {
            override fun onOpenSetlist(id: String) {
                state = state.copy(
                    openSetlistId = id, lastOpenedId = id,
                    setlists = state.setlists.map { if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it }
                )
            }
            override fun onCreateSetlist(name: String, mode: CreateMode) {
                val src = state.setlists.firstOrNull { it.id == state.openSetlistId }
                val base = if (mode == CreateMode.Duplicate) src else null
                val color = if (base != null) SetlistsLogic.autoColor(state.setlists, base.color) else SetlistsLogic.autoColor(state.setlists)
                val s = Setlist(System.nanoTime().toString(), name, base?.patchCount ?: 0, color, false, System.currentTimeMillis())
                state = state.copy(setlists = listOf(s) + state.setlists)
            }
            override fun onRenameSetlist(id: String, name: String) {
                state = state.copy(setlists = state.setlists.map { if (it.id == id) it.copy(name = name) else it })
            }
            override fun onChangeColor(id: String, color: SetlistColor) {
                state = state.copy(setlists = state.setlists.map { if (it.id == id) it.copy(color = color) else it })
            }
            override fun onDuplicateSetlist(id: String) {
                val s = state.setlists.firstOrNull { it.id == id } ?: return
                val copy = s.copy(
                    id = System.nanoTime().toString(), name = "${s.name} copy", isPinned = false,
                    color = SetlistsLogic.autoColor(state.setlists, s.color), lastUsedAt = System.currentTimeMillis()
                )
                state = state.copy(setlists = listOf(copy) + state.setlists)
            }
            override fun onTogglePin(id: String) {
                state = state.copy(setlists = state.setlists.map { if (it.id == id) it.copy(isPinned = !it.isPinned) else it })
            }
            override fun onDeleteSetlist(id: String) {
                lastDeleted = state.setlists.firstOrNull { it.id == id }
                state = state.copy(
                    setlists = state.setlists.filterNot { it.id == id },
                    openSetlistId = state.openSetlistId.takeIf { it != id },
                    lastOpenedId = state.lastOpenedId.takeIf { it != id }
                )
            }
            override fun onUndoDelete() {
                lastDeleted?.let { state = state.copy(setlists = state.setlists + it) }
                lastDeleted = null
            }
            override fun onQueryChange(query: String) { state = state.copy(query = query) }
            override fun onSortChange(sort: SetlistSort) { state = state.copy(sort = sort) }
            override fun onOnlyPinnedChange(only: Boolean) { state = state.copy(onlyPinned = only) }
            override fun onPrefsChange(prefs: SetlistsPrefs) { state = state.copy(prefs = prefs) }
            override fun onResetPrefs() { state = state.copy(prefs = SetlistsPrefs()) }
        }
    }
    SetlistsScreen(state, actions)
}
