@file:Suppress("unused")

package com.soundstage.mixer.ui.settings

/*
 * ÆTHER KEYS — SETTINGS PAGE (final UI)
 *
 * What this file contains
 *  - SettingsScreen : full-screen settings page (tabs Audio / Interface / MIDI / System).
 *    No container, no card outline: flat sections separated by hairlines.
 *  - The MIDI tab opens [MidiMappingPage], a page inside Settings, never a window.
 *
 * What this file does NOT contain
 *  - The MIDI learn engine, the storage, the audio engine, the chord display, the existing
 *    Mixer / DrumPad / top bar. They stay in the app and are wired through [SettingsActions]
 *    and [SettingsState]. Every action has an empty default body.
 *
 * Requirements: Jetpack Compose foundation 1.7+, material3 (Text only), ui-tooling-preview (preview only).
 * Blur: Modifier.blur is applied to the page content only when the API level is 31+.
 *
 * All user-facing text is English.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.composed
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.soundstage.mixer.model.MidiActionType
import com.soundstage.mixer.model.MidiControlType
import com.soundstage.mixer.model.MidiControlMode
import com.soundstage.mixer.model.MidiMappingItem
import androidx.compose.runtime.key
import com.soundstage.mixer.ui.components.KeyboardChrome
import com.soundstage.mixer.ui.components.KeyboardThemeSwatch
import com.soundstage.mixer.ui.components.KeyboardThemes

// ════════════════════════════════════════════════════════════════════════════════════════
// 1. MODELS
// ════════════════════════════════════════════════════════════════════════════════════════

enum class SettingsTab(val label: String, val subtitle: String) {
    Audio("Audio", "Audio engine & latency"),
    Interface("Interface", "Layout & colors"),
    Midi("MIDI", "Controllers & mapping"),
    System("System", "Language & display")
}

enum class Notation { Sharps, Flats }

/**
 * Family of a MIDI message or of a control. Each family has its own color on the chips.
 * Special families (Transport, Panic, Snapshot) have their own colors, not the CC/Note palette.
 */
enum class MidiFamily(val tag: String, val color: Color) {
    ControlChange("CC", Color(0xFF8FB2E8)),
    Note("Note", Color(0xFFD8A24A)),
    ProgramChange("PC", Color(0xFF86A394)),
    Transport("Transport", Color(0xFF7FC7B8)),
    Panic("Panic", Color(0xFFC2587A)),
    Snapshot("Snapshot", Color(0xFFA391D9))
}

/** A MIDI message as stored by the app. [channel] null means Omni (all channels). */
data class MidiMessage(val family: MidiFamily, val number: Int, val channel: Int? = null) {
    /** Compact chip label, e.g. "CC 7 · 1" or "Note 36 · Omni". */
    fun short(): String {
        val ch = channel?.toString() ?: "All"
        return "${family.tag} $number · $ch"
    }

    /** Full label for the card, e.g. "CC 7 · Ch 1". */
    fun full(): String {
        val ch = channel?.let { "Ch $it" } ?: "Omni (All Ch)"
        return "${family.tag} $number · $ch"
    }
}

/**
 * One mappable element of a page.
 * [standalone] = true for actions that have no visible element (Panic, Tap tempo, Metronome,
 * Previous/Next snapshot): they are listed in "Other actions" instead of getting a chip on the page.
 */
data class SettingsState(
    val tab: SettingsTab = SettingsTab.Audio,                 // persisted (last tab)
    val query: String = "",                                   // not persisted
    // Audio (persisted)
    val bufferSize: Int = 256,
    val maxPolyphony: Int = 128,
    val headroom: Float = 1.00f,
    // Interface (persisted)
    val autoFit: Boolean = false,
    val hue: Float = 220f,
    val accentSaturation: Float = 1.00f,
    val backgroundTone: Float = 1.60f,
    val backgroundLightness: Float = 1.00f,
    /** Virtual keyboard theme id (KeyboardThemes) and pressed-key style ("color"/"led"). Persisted. */
    val vkbTheme: String = "classic",
    val vkbPressStyle: String = "color",
    // MIDI input (persisted)
    val antiJitter: Boolean = false,
    val jitterWindowMs: Int = 15,
    // System (persisted)
    val notation: Notation = Notation.Sharps,
    val keepScreenOn: Boolean = true,
    // Mapping page (not persisted): the MIDI mapping page replaces the MIDI tab body.
    val midiMappingOpen: Boolean = false,
    /** Persisted bindings, one per action. */
    val midiMappings: List<MidiMappingItem> = emptyList(),
    val learningActionType: MidiActionType? = null,
    val lastMidiSummary: String = "No MIDI activity yet",
    // Extended Audio (persisted)
    val audioEngine: String = "oboe",
    val fluidSynthGain: Float = 1.20f,
    // System (persisted)
    val hapticsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    /** Live list of USB MIDI devices currently attached, provided by the app. */
    val midiDevices: List<com.soundstage.mixer.model.MidiDeviceItem> = emptyList()
)

object SettingsDefaults {
    const val BUFFER_SIZE = 256
    const val MAX_POLYPHONY = 128
    const val HEADROOM = 1.00f
    const val HUE = 220f
    const val ACCENT_SATURATION = 1.00f
    const val BACKGROUND_TONE = 1.60f
    const val BACKGROUND_LIGHTNESS = 1.00f
    const val JITTER_WINDOW_MS = 15
    const val FLUID_GAIN = 1.20f

    /** Standard mapping restored by "Reset to defaults": master volume on CC#7, omni. */
    val mappings: Map<String, MidiMessage> = mapOf(
        "mixer.master" to MidiMessage(MidiFamily.ControlChange, 7, null)
    )
}

object SettingsRanges {
    val BUFFER_SIZES = listOf(64, 128, 256, 512, 1024)
    val POLYPHONY = listOf(64, 128, 256, 512)
    const val HEADROOM_MIN = 0.5f
    const val HEADROOM_MAX = 2.0f
    const val HEADROOM_STD = 1.20f           // above this, the clipping warning appears
    const val HUE_MAX = 360f
    const val SAT_MAX = 2.0f
    const val TONE_MAX = 3.0f
    const val LIGHT_MIN = 0.5f
    const val LIGHT_MAX = 2.0f
    const val JITTER_MIN = 1
    const val JITTER_MAX = 30
    const val NAME_MAX = 40
}

/**
 * Every method has an empty default body. The app wires each one to the existing logic and to
 * persistent storage. Every setting change must be persisted by the app (see the brief).
 */
interface SettingsActions {
    fun onBack() {}
    fun onTabSelected(tab: SettingsTab) {}
    fun onQueryChange(query: String) {}
    fun onBufferSizeChange(size: Int) {}
    fun onMaxPolyphonyChange(voices: Int) {}
    fun onHeadroomChange(value: Float) {}
    fun onAutoFitChange(enabled: Boolean) {}
    fun onHueChange(value: Float) {}
    fun onAccentSaturationChange(value: Float) {}
    fun onBackgroundToneChange(value: Float) {}
    fun onBackgroundLightnessChange(value: Float) {}
    fun onResetColors() {}
    fun onVkbThemeChange(themeId: String) {}
    fun onVkbPressStyleChange(style: String) {}
    fun onAntiJitterChange(enabled: Boolean) {}
    fun onJitterWindowChange(ms: Int) {}
    fun onStartMidiLearn(action: MidiActionType) {}
    fun onCancelMidiLearn() {}
    fun onUnmapMidiAction(action: MidiActionType) {}
    fun onToggleMidiMappingEnabled(action: MidiActionType) {}
    fun onUpdateManualMidiMapping(action: MidiActionType, type: MidiControlType, channel: Int, number: Int) {}
    fun onSetMidiMappingMode(action: MidiActionType, mode: MidiControlMode) {}
    fun onResetMidiMappings() {}
    fun onClearMidiMappings() {}
    fun onToggleMidiDevice(deviceId: String) {}
    fun onOpenMidiMapping() {}
    fun onCloseMidiMapping() {}
    fun onNotationChange(notation: Notation) {}
    fun onKeepScreenOnChange(enabled: Boolean) {}
    fun onOpenLanguage() {}
    fun onOpenAppSettings() {}
    fun onAudioEngineChange(engine: String) {}
    fun onFluidSynthGainChange(value: Float) {}
    fun onHapticsChange(enabled: Boolean) {}
    fun onReducedMotionChange(enabled: Boolean) {}

}

object NoOpSettingsActions : SettingsActions

// ════════════════════════════════════════════════════════════════════════════════════════
// 2. PURE LOGIC
// ════════════════════════════════════════════════════════════════════════════════════════

object SettingsLogic {
    /** Latency in ms for a buffer at 44.1 kHz, shown under each buffer size. */
    fun bufferLatencyMs(size: Int, sampleRate: Int = 44_100): Float = size * 1000f / sampleRate

    fun headroomLabel(value: Float) = String.format(java.util.Locale.US, "%.2fx", value)

    fun showClippingWarning(headroom: Float) = headroom > SettingsRanges.HEADROOM_STD

    /**
     * Search index. [keywords] are shown in the results; the tab is where the setting lives.
     * Kept here so the search does not depend on the UI layout.
     */
    data class Entry(val title: String, val tab: SettingsTab, val keywords: String)

    val index: List<Entry> = listOf(
        Entry("Audio buffer size", SettingsTab.Audio, "latency buffer ms"),
        Entry("Maximum polyphony", SettingsTab.Audio, "voices notes"),
        Entry("Audio engine", SettingsTab.Audio, "oboe opensl driver"),
        Entry("Synth output headroom", SettingsTab.Audio, "gain clipping safety"),
        Entry("FluidSynth gain", SettingsTab.Audio, "fluidsynth output gain"),
        Entry("Auto-fit faders", SettingsTab.Interface, "layout scroll mixer"),
        Entry("Primary interface hue", SettingsTab.Interface, "color theme"),
        Entry("Accent color saturation", SettingsTab.Interface, "color"),
        Entry("Background tone saturation", SettingsTab.Interface, "color"),
        Entry("Background lightness", SettingsTab.Interface, "color brightness"),
        Entry("MIDI mapping", SettingsTab.Midi, "learn controller pads faders"),
        Entry("Anti-jitter filter", SettingsTab.Midi, "debounce burst"),
        Entry("Filter sensitivity window", SettingsTab.Midi, "debounce ms"),
        Entry("Musical notation", SettingsTab.System, "sharps flats"),
        Entry("Keep screen on", SettingsTab.System, "sleep"),
        Entry("Haptics", SettingsTab.System, "vibration feedback"),
        Entry("Reduced motion", SettingsTab.System, "animation accessibility")
    )

    fun search(query: String): List<Entry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return index.filter { (it.title + " " + it.keywords).lowercase().contains(q) }
    }

}

// ════════════════════════════════════════════════════════════════════════════════════════
// 3. STYLE (same language as the Setlists screen)
// ════════════════════════════════════════════════════════════════════════════════════════

object SettingsTheme {
    data class Palette(
        val backgroundBrush: Brush,
        val accent: Color,
        val accentDeep: Color,
        val accentText: Color,
        val panelTop: Color,
        val panelBottom: Color,
        val well: Color
    )

    fun palette(state: SettingsState): Palette {
        val effectiveHue = ((state.hue % 360f) + 360f) % 360f
        val accent = com.soundstage.mixer.ui.theme.hsvToColor(effectiveHue, (0.38f * state.accentSaturation).coerceIn(0.1f, 1f), 0.92f)
        val accentDeep = com.soundstage.mixer.ui.theme.hsvToColor(effectiveHue, (0.44f * state.accentSaturation).coerceIn(0.1f, 1f), 0.85f)
        val bgSat = (0.22f * state.backgroundTone).coerceIn(0.04f, 0.60f)
        val bgValTop = (0.13f * state.backgroundLightness).coerceIn(0.04f, 0.40f)
        val bgValBottom = (0.09f * state.backgroundLightness).coerceIn(0.03f, 0.35f)
        val bgTop = com.soundstage.mixer.ui.theme.hsvToColor(effectiveHue, bgSat, bgValTop)
        val bgBottom = com.soundstage.mixer.ui.theme.hsvToColor(effectiveHue, bgSat, bgValBottom)
        val panelTop = com.soundstage.mixer.ui.theme.hsvToColor(effectiveHue, (bgSat * 0.9f).coerceIn(0.05f, 0.5f), (0.22f * state.backgroundLightness).coerceIn(0.08f, 0.5f))
        val panelBottom = com.soundstage.mixer.ui.theme.hsvToColor(effectiveHue, (bgSat * 0.9f).coerceIn(0.05f, 0.5f), (0.16f * state.backgroundLightness).coerceIn(0.06f, 0.45f))
        val well = com.soundstage.mixer.ui.theme.hsvToColor(effectiveHue, bgSat, (0.09f * state.backgroundLightness).coerceIn(0.03f, 0.3f))
        return Palette(
            backgroundBrush = Brush.verticalGradient(listOf(bgTop, bgBottom)),
            accent = accent,
            accentDeep = accentDeep,
            accentText = Color(0xFF0F1726),
            panelTop = panelTop,
            panelBottom = panelBottom,
            well = well
        )
    }

    fun backgroundBrush(state: SettingsState): Brush = palette(state).backgroundBrush
}

internal object SetColors {
    val Ink = Color(0xFFE9EDF5)
    val Soft = Color(0xFFDCE2EE)
    val Mute = Color(0xFF8690A4)
    val Dim = Color(0xFF6F7A90)
    val Accent = Color(0xFF8FB2E8)
    val AccentDeep = Color(0xFF7FA3DD)
    val AccentText = Color(0xFF0F1726)
    val Warn = Color(0xFFD8A24A)
    val Ok = Color(0xFF3CC48A)
    val Danger = Color(0xFFB8465F)
    val Well = Color(0xFF0F141D)
    val PanelTop = Color(0xFF232C3E)
    val PanelBottom = Color(0xFF1A2230)
    val Background = Color(0xFF141B27)
    val BackgroundBottom = Color(0xFF0F141D)
}

internal fun white(a: Float) = Color.White.copy(alpha = a)

internal fun setText(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = SetColors.Ink) =
    TextStyle(fontSize = size, fontWeight = weight, color = color, fontFeatureSettings = "tnum")

private fun Modifier.setSunk(shape: androidx.compose.ui.graphics.Shape): Modifier = this
    .clip(shape)
    .background(SetColors.Well)
    .drawBehind {
        drawRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), startY = 0f, endY = 10.dp.toPx())
        )
    }
    .border(1.dp, white(0.05f), shape)

/** Light spring press. Apply it before the visual modifiers. */
@Composable
private fun Modifier.tapScale(scaleDown: Float = 0.96f, onTap: () -> Unit): Modifier {
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

@Composable
private fun rememberHaptics(enabled: Boolean): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(enabled, haptic) {
        {
            if (enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 4. SETTINGS PAGE (full screen, no container)
// ════════════════════════════════════════════════════════════════════════════════════════

@Composable
fun SettingsScreen(
    state: SettingsState,
    actions: SettingsActions,
    /** Background brush derived from the interface colors (hue, saturation, lightness). */
    background: Brush = SettingsTheme.backgroundBrush(state),
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxSize()
            .background(background)
    ) {
        TabRail(
            selected = state.tab,
            searching = state.query.isNotBlank(),
            onSelect = { actions.onTabSelected(it) },
            modifier = Modifier.width(212.dp).fillMaxHeight()
        )
        Column(Modifier.weight(1f).fillMaxHeight()) {
            SettingsHeader(state, actions)
            Box(Modifier.fillMaxWidth().height(1.dp).background(white(0.07f)))
            if (state.midiMappingOpen) {
                MidiMappingPage(
                    mappings = state.midiMappings,
                    antiJitter = state.antiJitter,
                    jitterWindowMs = state.jitterWindowMs,
                    midiDevices = state.midiDevices,
                    learningActionType = state.learningActionType,
                    lastMidiSummary = state.lastMidiSummary,
                    onStartLearn = { actions.onStartMidiLearn(it) },
                    onCancelLearn = { actions.onCancelMidiLearn() },
                    onUnmap = { actions.onUnmapMidiAction(it) },
                    onToggleEnabled = { actions.onToggleMidiMappingEnabled(it) },
                    onUpdateManual = { a, t, c, n -> actions.onUpdateManualMidiMapping(a, t, c, n) },
                    onSetMode = { a, m -> actions.onSetMidiMappingMode(a, m) },
                    onResetDefaults = { actions.onResetMidiMappings() },
                    onClearAll = { actions.onClearMidiMappings() },
                    onToggleDevice = { actions.onToggleMidiDevice(it) },
                    onAntiJitterChange = { actions.onAntiJitterChange(it) },
                    onJitterWindowChange = { actions.onJitterWindowChange(it) },
                    onBack = { actions.onCloseMidiMapping() }
                )
            } else if (state.query.isNotBlank()) {
                SearchResults(state, actions)
            } else {
                TabContent(state, actions)
            }
        }
    }
}

@Composable
private fun SettingsHeader(state: SettingsState, actions: SettingsActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .tapScale(0.94f) { actions.onBack() }
                .clip(CircleShape)
                .background(white(0.07f))
                .border(1.dp, white(0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) { SetIcon(SetIconKind.Back, SetColors.Ink, Modifier.size(19.dp)) }

        Column(Modifier.weight(1f)) {
            Text("Settings", style = setText(21.sp, FontWeight.ExtraBold, Color(0xFFF4F6FB)), maxLines = 1)
            Text(
                if (state.query.isNotBlank()) "Search results" else state.tab.subtitle,
                style = setText(12.sp, color = SetColors.Dim),
                maxLines = 1
            )
        }
        SearchField(state.query, actions::onQueryChange, Modifier.width(240.dp))
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(Color(0xB30F141D))
            .border(1.dp, white(0.10f), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SetIcon(SetIconKind.Search, SetColors.Mute, Modifier.size(16.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search settings", style = setText(14.sp, color = SetColors.Mute))
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = setText(14.sp),
                cursorBrush = SolidColor(SetColors.Accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(20.dp).tapScale(0.85f) { onChange("") }, contentAlignment = Alignment.Center) {
                SetIcon(SetIconKind.Close, SetColors.Mute, Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun TabRail(
    selected: SettingsTab,
    searching: Boolean,
    onSelect: (SettingsTab) -> Unit,
    modifier: Modifier
) {
    val idx = SettingsTab.values().indexOf(selected)
    val slide by animateFloatAsState(idx.toFloat(), spring(dampingRatio = 0.75f, stiffness = 380f), label = "rail")
    Column(modifier.padding(start = 18.dp, end = 10.dp, top = 12.dp)) {
        Box(Modifier.fillMaxWidth()) {
            // Sliding highlight (no outline: a soft fill only, like the Setlists top cards).
            Box(
                Modifier
                    .offset { IntOffset(0, (slide * 50.dp.toPx()).roundToInt()) }
                    .fillMaxWidth()
                    .height(44.dp)
                    .alpha(if (searching) 0.3f else 1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.verticalGradient(listOf(white(0.10f), white(0.05f))))
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsTab.values().forEach { tab ->
                    val on = tab == selected && !searching
                    val c by androidx.compose.animation.animateColorAsState(
                        if (on) SetColors.Accent else SetColors.Mute, tween(250), label = "tabc"
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .tapScale(0.98f) { onSelect(tab) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = tab.tabIcon(),
                            contentDescription = null,
                            tint = c,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(tab.label, style = setText(14.5.sp, FontWeight.Bold, if (on) SetColors.Ink else Color(0xFF9AA5BA)))
                    }
                }
            }
        }
    }
}

private fun SettingsTab.tabIcon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    SettingsTab.Audio -> SettingsTabIcons.Audio
    SettingsTab.Interface -> SettingsTabIcons.Interface
    SettingsTab.Midi -> SettingsTabIcons.Midi
    SettingsTab.System -> SettingsTabIcons.System
}

@Composable
private fun TabContent(state: SettingsState, actions: SettingsActions) {
    // Keyed on the tab: the content is replaced and the slide + fade replays on every tab change.
    key(state.tab) { TabBody(state, actions) }
}

@Composable
private fun TabBody(state: SettingsState, actions: SettingsActions) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 14 },
        modifier = Modifier.fillMaxSize(),
        label = "tabContent"
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 24.dp)
        ) {
            when (state.tab) {
                SettingsTab.Audio -> AudioSection(state, actions)
                SettingsTab.Interface -> InterfaceSection(state, actions)
                SettingsTab.Midi -> MidiSection(state, actions)
                SettingsTab.System -> SystemSection(state, actions)
            }
        }
    }
}

@Composable
private fun SearchResults(state: SettingsState, actions: SettingsActions) {
    val results = remember(state.query) { SettingsLogic.search(state.query) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp)
    ) {
        item(key = "count") {
            SectionLabel("${results.size} ${if (results.size == 1) "result" else "results"}")
        }
        if (results.isEmpty()) {
            item(key = "none") {
                Text("No setting matches “${state.query}”.", style = setText(13.5.sp, color = SetColors.Dim), modifier = Modifier.padding(vertical = 14.dp))
            }
        }
        items(results, key = { it.title }) { entry ->
            // One root per item: several siblings in a LazyColumn item would overlap.
            Column {
                SettingRow(
                    title = entry.title,
                    hint = entry.tab.label,
                    onClick = { actions.onQueryChange(""); actions.onTabSelected(entry.tab) }
                ) {
                    SetIcon(SetIconKind.Chevron, SetColors.Mute, Modifier.size(16.dp))
                }
                SetDivider()
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// Tabs
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun AudioSection(state: SettingsState, actions: SettingsActions) {
    SectionLabel("Audio engine")
    SettingRow(
        title = "Audio buffer size",
        hint = "A smaller buffer lowers key-touch latency. Adjust to your device's performance."
    ) {
        Segmented(
            options = SettingsRanges.BUFFER_SIZES.map { it to "$it" },
            selected = state.bufferSize,
            onSelect = actions::onBufferSizeChange,
            modifier = Modifier.fillMaxWidth(),
            footers = SettingsRanges.BUFFER_SIZES.map { String.format(java.util.Locale.US, "~%.1f ms", SettingsLogic.bufferLatencyMs(it)) }
        )
    }
    SetDivider()
    SettingRow(title = "Maximum polyphony", hint = "Notes the synth engine can play at once (voices).") {
        Segmented(
            options = SettingsRanges.POLYPHONY.map { it to "$it" },
            selected = state.maxPolyphony,
            onSelect = actions::onMaxPolyphonyChange,
            modifier = Modifier.fillMaxWidth()
        )
    }
    SetDivider()
    SettingRow(
        title = "Audio engine",
        hint = "Oboe uses AAudio low-latency. OpenSL ES is the fallback for older or unsupported devices."
    ) {
        Segmented(
            options = listOf("oboe" to "Oboe", "opensl" to "OpenSL"),
            selected = state.audioEngine,
            onSelect = actions::onAudioEngineChange,
            modifier = Modifier.width(190.dp)
        )
    }

    SectionLabel("Output")
    SliderRow(
        title = "Synth output headroom",
        hint = "Safety peak ceiling before the master bus.",
        value = state.headroom,
        min = SettingsRanges.HEADROOM_MIN,
        max = SettingsRanges.HEADROOM_MAX,
        step = 0.05f,
        defaultValue = SettingsDefaults.HEADROOM,
        format = { SettingsLogic.headroomLabel(it) },
        onChange = actions::onHeadroomChange
    )
    AnimatedVisibility(
        visible = SettingsLogic.showClippingWarning(state.headroom),
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(200))
    ) {
        Text(
            "High gain may cause digital clipping.",
            style = setText(11.5.sp, FontWeight.SemiBold, SetColors.Warn),
            modifier = Modifier.padding(bottom = 10.dp)
        )
    }
    SetDivider()
    SliderRow(
        title = "FluidSynth gain",
        hint = "Master gain of the SoundFont synth. Standard: 1.20x.",
        value = state.fluidSynthGain,
        min = 0.5f,
        max = 2.5f,
        step = 0.05f,
        defaultValue = SettingsDefaults.FLUID_GAIN,
        format = { String.format(java.util.Locale.US, "%.2fx", it) },
        onChange = actions::onFluidSynthGainChange
    )
    AnimatedVisibility(
        visible = state.fluidSynthGain > 1.5f,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(200))
    ) {
        Text(
            "Values above 1.50x may distort on dense mixes.",
            style = setText(11.5.sp, FontWeight.SemiBold, SetColors.Warn),
            modifier = Modifier.padding(bottom = 10.dp)
        )
    }
}

@Composable
private fun InterfaceSection(state: SettingsState, actions: SettingsActions) {
    SectionLabel("Layout")
    SettingRow(
        title = "Auto-fit faders",
        hint = if (state.autoFit) "On: all faders fit the screen." else "Off: fixed width with scroll."
    ) {
        SetSwitch(state.autoFit, actions::onAutoFitChange)
    }

    val colorsChanged =
        kotlin.math.abs(state.hue - SettingsDefaults.HUE) > 0.5f ||
        kotlin.math.abs(state.accentSaturation - SettingsDefaults.ACCENT_SATURATION) > 0.001f ||
        kotlin.math.abs(state.backgroundTone - SettingsDefaults.BACKGROUND_TONE) > 0.001f ||
        kotlin.math.abs(state.backgroundLightness - SettingsDefaults.BACKGROUND_LIGHTNESS) > 0.001f

    SectionLabel(
        text = "Colors",
        trailing = {
            if (colorsChanged) {
                Text(
                    "Reset colors",
                    style = setText(12.sp, FontWeight.Bold, Color(0xFF9AA5BA)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { actions.onResetColors() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    )

    SliderRow(
        title = "Primary interface hue",
        hint = null,
        value = state.hue,
        min = 0f,
        max = SettingsRanges.HUE_MAX,
        step = 1f,
        defaultValue = SettingsDefaults.HUE,
        format = { "${it.toInt()}°" },
        onChange = actions::onHueChange
    )
    SetDivider()
    SliderRow(
        title = "Accent color saturation",
        hint = null,
        value = state.accentSaturation,
        min = 0f,
        max = SettingsRanges.SAT_MAX,
        step = 0.05f,
        defaultValue = SettingsDefaults.ACCENT_SATURATION,
        format = { String.format(java.util.Locale.US, "%.2fx", it) },
        onChange = actions::onAccentSaturationChange
    )
    SetDivider()
    SliderRow(
        title = "Background tone saturation",
        hint = null,
        value = state.backgroundTone,
        min = 0f,
        max = SettingsRanges.TONE_MAX,
        step = 0.05f,
        defaultValue = SettingsDefaults.BACKGROUND_TONE,
        format = { String.format(java.util.Locale.US, "%.2fx", it) },
        onChange = actions::onBackgroundToneChange
    )
    SetDivider()
    SliderRow(
        title = "Background lightness",
        hint = null,
        value = state.backgroundLightness,
        min = SettingsRanges.LIGHT_MIN,
        max = SettingsRanges.LIGHT_MAX,
        step = 0.05f,
        defaultValue = SettingsDefaults.BACKGROUND_LIGHTNESS,
        format = { String.format(java.util.Locale.US, "%.2fx", it) },
        onChange = actions::onBackgroundLightnessChange
    )

    SectionLabel("Virtual keyboard")
    VkbThemeGrid(
        selectedThemeId = state.vkbTheme,
        onThemeSelected = actions::onVkbThemeChange
    )
    SetDivider()
    SettingRow(
        title = "Pressed keys",
        hint = "Color: flat theme color. LED: adds a short glow behind the key."
    ) {
        Segmented(
            options = listOf("color" to "Color", "led" to "LED"),
            selected = state.vkbPressStyle,
            onSelect = actions::onVkbPressStyleChange
        )
    }
}

/**
 * 3-per-row grid of the keyboard themes. Each swatch shows the theme's real key colors and
 * its pressed color on the second key; the frame stays the same for every theme.
 */
@Composable
private fun VkbThemeGrid(selectedThemeId: String, onThemeSelected: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyboardThemes.all.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    val pick = { onThemeSelected(t.id) }
                    KeyboardThemeSwatch(
                        theme = t,
                        selected = t.id == selectedThemeId,
                        onClick = pick,
                        chrome = KeyboardChrome(),
                        modifier = Modifier.weight(1f).tapScale { pick() }
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MidiSection(state: SettingsState, actions: SettingsActions) {
    SectionLabel("Connected devices") {
        val n = state.midiDevices.count { it.isConnected }
        Text("$n connected", style = setText(10.5.sp, FontWeight.Bold, if (n > 0) SetColors.Ok else SetColors.Mute))
    }
    MidiDevicesCard(state.midiDevices, actions::onToggleMidiDevice)

    SectionLabel("Input")
    SettingRow(
        title = "Anti-jitter filter",
        hint = "Removes parasitic burst signals from worn buttons and encoders."
    ) {
        SetSwitch(state.antiJitter, actions::onAntiJitterChange)
    }
    SetDivider()
    SliderRow(
        title = "Filter sensitivity window",
        hint = null,
        value = state.jitterWindowMs.toFloat(),
        min = SettingsRanges.JITTER_MIN.toFloat(),
        max = SettingsRanges.JITTER_MAX.toFloat(),
        step = 1f,
        defaultValue = SettingsDefaults.JITTER_WINDOW_MS.toFloat(),
        format = { "${it.toInt()} ms" },
        onChange = { actions.onJitterWindowChange(it.toInt()) },
        enabled = state.antiJitter
    )

    SectionLabel("MIDI mapping")
    val mappedCount = remember(state.midiMappings) { state.midiMappings.count { it.isAssigned } }
    val totalCount = remember { MidiActionType.values().size }
    SettingRow(
        title = "MIDI Mapping",
        hint = "Snapshots, patches, tracks, drum pads and global controls.",
        onClick = { actions.onOpenMidiMapping() }
    ) {
        Text("$mappedCount / $totalCount", style = setText(12.sp, FontWeight.Bold, SetColors.Mute))
        SetIcon(SetIconKind.Chevron, SetColors.Mute, Modifier.size(16.dp))
    }

}

/** Counter shown next to Map Mixer / Map DrumPad. The count is recomputed by the app's catalog (see brief). */

/**
 * USB MIDI devices currently attached to the app. Each device is a raised row: a
 * status LED, the name, its type, and an ON/OFF pill. An empty list shows a single
 * dashed placeholder so the section never collapses.
 *
 * The device icon is drawn here rather than pulled from resources so it keeps the
 * settings stroke weight and picks up the status colour.
 */
@Composable
private fun MidiDevicesCard(
    devices: List<com.soundstage.mixer.model.MidiDeviceItem>,
    onToggleDevice: (String) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(SetColors.PanelTop, SetColors.PanelBottom)))
            .border(1.dp, white(0.08f), shape)
            .padding(vertical = 4.dp)
    ) {
        if (devices.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MidiPlugIcon(SetColors.Dim, Modifier.size(30.dp))
                Column(Modifier.weight(1f)) {
                    Text("No USB MIDI device", style = setText(13.5.sp, FontWeight.Bold, SetColors.Mute))
                    Text("Plug a controller to route notes, faders and pads.",
                        style = setText(11.sp, color = SetColors.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            return@Column
        }
        devices.forEachIndexed { i, dev ->
            if (i > 0) SetDivider()
            val tint = if (dev.isConnected) SetColors.Ok else SetColors.Dim
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                            .background(white(0.05f))
                            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) { MidiPlugIcon(tint, Modifier.size(22.dp)) }
                    Box(
                        Modifier.size(11.dp).clip(CircleShape).background(SetColors.PanelTop)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) { Box(Modifier.fillMaxSize().clip(CircleShape).background(tint)) }
                }
                Column(Modifier.weight(1f)) {
                    Text(dev.name, style = setText(13.5.sp, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (dev.isConnected) "${dev.type} · connected" else "${dev.type} · offline",
                        style = setText(11.sp, color = if (dev.isConnected) SetColors.Ok else SetColors.Mute),
                        maxLines = 1
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(9.dp))
                        .background(if (dev.isEnabled) SetColors.Accent.copy(alpha = 0.16f) else white(0.05f))
                        .clickable(enabled = dev.isConnected) { onToggleDevice(dev.id) }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (dev.isEnabled) "ACTIVE" else "MUTED",
                        style = setText(9.5.sp, FontWeight.Bold,
                            if (dev.isEnabled) SetColors.Accent else SetColors.Mute)
                    )
                }
            }
        }
    }
}

/** Small DIN-plug MIDI icon: a rounded plug body with a short cable, settings weight. */
@Composable
private fun MidiPlugIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.8.dp.toPx()
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Plug body (a half-round shell) on the left, cable curling to the right.
        val body = Path().apply {
            moveTo(w * 0.30f, h * 0.28f)
            lineTo(w * 0.62f, h * 0.28f)
            lineTo(w * 0.62f, h * 0.72f)
            lineTo(w * 0.30f, h * 0.72f)
            close()
        }
        drawPath(body, color, style = st)
        drawLine(color, Offset(w * 0.30f, h * 0.5f), Offset(w * 0.10f, h * 0.5f), sw, StrokeCap.Round)
        val cable = Path().apply {
            moveTo(w * 0.62f, h * 0.5f)
            cubicTo(w * 0.86f, h * 0.5f, w * 0.80f, h * 0.18f, w * 0.94f, h * 0.18f)
        }
        drawPath(cable, color, style = st)
        // Three contact pins inside the shell.
        listOf(0.38f, 0.50f, 0.62f).forEach { fy ->
            drawCircle(color, sw * 0.6f, Offset(w * 0.46f, h * fy))
        }
    }
}

@Composable
private fun SystemSection(state: SettingsState, actions: SettingsActions) {
    SectionLabel("Display & notation")
    SettingRow(
        title = "Musical notation",
        hint = if (state.notation == Notation.Sharps) "Sharps (♯ · C#, D#…)" else "Flats (♭ · Db, Eb…)"
    ) {
        Segmented(
            options = listOf(Notation.Sharps to "♯", Notation.Flats to "♭"),
            selected = state.notation,
            onSelect = actions::onNotationChange,
            modifier = Modifier.width(150.dp)
        )
    }
    SetDivider()
    SettingRow(
        title = "Keep screen on",
        hint = if (state.keepScreenOn) "Active (prevents screen sleep)." else "Off (the screen can sleep)."
    ) {
        SetSwitch(state.keepScreenOn, actions::onKeepScreenOnChange)
    }

    SectionLabel("Behavior")
    SettingRow(
        title = "Haptics",
        hint = if (state.hapticsEnabled) "Vibration feedback on pads, cases and value changes."
               else "Off — no vibration feedback."
    ) {
        SetSwitch(state.hapticsEnabled, actions::onHapticsChange)
    }
    SetDivider()
    SettingRow(
        title = "Reduced motion",
        hint = if (state.reducedMotion) "Continuous animations are off (halos, breathing, spins). States stay."
               else "Off — full motion."
    ) {
        SetSwitch(state.reducedMotion, actions::onReducedMotionChange)
    }
}

// ────────────────────────────────────────────────────────────────────────────────────────
// Setting primitives (flat: no card, no outline)
// ────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SectionLabel(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 6.dp)
            .height(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text.uppercase(),
            style = TextStyle(fontSize = 10.5.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B859A)),
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
internal fun SetDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(white(0.06f)))
}

/** A setting line: title and hint on the left, control on the right. */
@Composable
internal fun SettingRow(
    title: String,
    hint: String? = null,
    onClick: (() -> Unit)? = null,
    control: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.tapScale(0.99f, onClick) else it }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = setText(14.5.sp, FontWeight.Bold, SetColors.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hint != null) {
                Text(hint, style = setText(11.5.sp, color = SetColors.Dim), modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (control != null) {
            Row(
                Modifier.weight(1.2f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) { control() }
        }
    }
}

@Composable
internal fun SetSwitch(on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val track by androidx.compose.animation.animateColorAsState(
        if (on) SetColors.Accent else white(0.14f), tween(250), label = "swTrack"
    )
    val knobX by animateFloatAsState(if (on) 20f else 2f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "swX")
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .size(width = 40.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(track)
            .pointerInput(on, enabled) { detectTapGestures { if (enabled) onChange(!on) } }
    ) {
        Box(
            Modifier
                .offset { IntOffset(knobX.dp.roundToPx(), 2.dp.roundToPx()) }
                .size(18.dp)
                .clip(CircleShape)
                .background(if (on) SetColors.AccentText else Color(0xFFC7CFDD))
        )
    }
}

@Composable
private fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    footers: List<String>? = null
) {
    val idx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val shape = RoundedCornerShape(12.dp)
    Column(modifier) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(shape)
                .background(Color(0xCC0F141D))
                .border(1.dp, white(0.08f), shape)
                .padding(3.dp)
        ) {
            val segW = maxWidth / options.size
            val thumb by animateFloatAsState(
                idx.toFloat(), spring(dampingRatio = 0.7f, stiffness = 500f), label = "segThumb"
            )
            Box(
                Modifier
                    .offset { IntOffset((thumb * segW.toPx()).roundToInt(), 0) }
                    .width(segW)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF38445E), Color(0xFF2B3650))))
            )
            Row(Modifier.fillMaxSize()) {
                options.forEach { (value, label) ->
                    val on = value == selected
                    val c by androidx.compose.animation.animateColorAsState(
                        if (on) Color(0xFFF1F4FA) else SetColors.Mute, tween(200), label = "segC"
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(value) { detectTapGestures { onSelect(value) } },
                        contentAlignment = Alignment.Center
                    ) { Text(label, style = setText(12.5.sp, FontWeight.Bold, c), maxLines = 1) }
                }
            }
        }
        if (footers != null) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                footers.forEachIndexed { i, f ->
                    Text(
                        f,
                        style = setText(9.sp, FontWeight.SemiBold, if (i == idx) SetColors.Soft else SetColors.Dim),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Slider: the whole track is the control, the value rolls on change. A reset appears only when
 * the value differs from the default.
 */
@Composable
internal fun SliderRow(
    title: String,
    hint: String?,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    defaultValue: Float,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    val changed = abs(value - defaultValue) > 1e-4f
    val haptic = LocalHapticFeedback.current
    var trackWidth by remember { mutableStateOf(1f) }
    val fraction = ((value - min) / (max - min)).coerceIn(0f, 1f)
    val animFraction by animateFloatAsState(fraction, spring(dampingRatio = 0.85f, stiffness = 600f), label = "sliderFrac")

    fun emit(x: Float) {
        if (!enabled) return
        val f = (x / trackWidth).coerceIn(0f, 1f)
        val raw = min + f * (max - min)
        val snapped = (raw / step).roundToInt() * step
        val v = snapped.coerceIn(min, max)
        if (v != value) {
            onChange(v)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val latestEmit by rememberUpdatedState<(Float) -> Unit> { x -> emit(x) }

    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = setText(14.5.sp, FontWeight.Bold), maxLines = 1)
                if (hint != null) Text(hint, style = setText(11.5.sp, color = SetColors.Dim), modifier = Modifier.padding(top = 2.dp))
            }
            Text(format(value), style = setText(13.sp, FontWeight.ExtraBold, SetColors.Accent))
            Box(
                Modifier
                    .size(24.dp)
                    .alpha(if (changed) 1f else 0f)
                    .tapScale(0.9f) { if (changed) onChange(defaultValue) }
                    .clip(CircleShape)
                    .background(white(0.06f)),
                contentAlignment = Alignment.Center
            ) { SetIcon(SetIconKind.Reset, SetColors.Mute, Modifier.size(13.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .onGloballyPositioned { trackWidth = it.size.width.toFloat() }
                .pointerInput(enabled, min, max, step) {
                    detectHorizontalDragGestures(
                        onDragStart = { latestEmit(it.x) },
                        onHorizontalDrag = { change, _ -> change.consume(); latestEmit(change.position.x) }
                    )
                }
                .pointerInput(enabled) { detectTapGestures { latestEmit(it.x) } },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(SetColors.Well)
            )
            Box(
                Modifier
                    .fillMaxWidth(animFraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(SetColors.Accent.copy(alpha = 0.45f), SetColors.Accent)))
            )
            Box(
                Modifier
                    .offset { IntOffset((animFraction * trackWidth - 10.dp.toPx()).roundToInt(), 0) }
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF4A5675), Color(0xFF2B3550))))
                    .border(1.dp, white(0.22f), CircleShape)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 7. ICONS (drawn, no dependency)
// ════════════════════════════════════════════════════════════════════════════════════════

internal enum class SetIconKind { Back, Search, Close, Chevron, Reset }

@Composable
internal fun SetIcon(kind: SetIconKind, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = 2.dp.toPx()
        val st = Stroke(sw, cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2), sw, StrokeCap.Round)
        when (kind) {
            SetIconKind.Back -> { line(0.9f, 0.5f, 0.1f, 0.5f); line(0.1f, 0.5f, 0.45f, 0.15f); line(0.1f, 0.5f, 0.45f, 0.85f) }
            SetIconKind.Search -> { drawCircle(color, w * 0.3f, Offset(w * 0.45f, h * 0.45f), style = st); line(0.68f, 0.68f, 0.92f, 0.92f) }
            SetIconKind.Close -> { line(0.15f, 0.15f, 0.85f, 0.85f); line(0.85f, 0.15f, 0.15f, 0.85f) }
            SetIconKind.Chevron -> { line(0.35f, 0.15f, 0.7f, 0.5f); line(0.7f, 0.5f, 0.35f, 0.85f) }
            SetIconKind.Reset -> { drawArc(color, 40f, 290f, false, Offset(w * 0.15f, h * 0.15f), Size(w * 0.7f, h * 0.7f), style = st) }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════════════
// 8. PREVIEW (settings only; the mapping mode needs the real page, see the brief)
// ════════════════════════════════════════════════════════════════════════════════════════

@Preview(widthDp = 891, heightDp = 411, showBackground = true, backgroundColor = 0xFF141B27)
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(
        state = SettingsState(tab = SettingsTab.Interface),
        actions = NoOpSettingsActions
    )
}
