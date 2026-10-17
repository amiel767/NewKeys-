package com.soundstage.mixer.ui.components.drumpad

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.SurfaceFxChain
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────
// MetroState — état du métronome exposé aux overlays (V1, non persisté)
// ─────────────────────────────────────────────────────────────────────────
data class MetroState(
    val on: Boolean = false,
    val sig: String = "4/4",
    val sub: String = "1/4",
    val sound: String = "Woodblock",
    val volume: Int = 65,
    val accent1: Boolean = true,
    val accent13: Boolean = false,
    val visualFlash: Boolean = true,
    val countIn: String = "off",
    val tapBpm: Int = 0,
)

private val METRO_SIGNATURES = listOf(
    "4/4", "3/4", "2/4", "6/8", "9/8",
    "12/8", "5/4", "7/8", "5/8", "3/8",
)
private val METRO_SUBDIVISIONS = listOf("1/4", "1/8", "1/16", "Triplet")
private val METRO_SOUNDS = listOf("Woodblock", "Beep", "Digital", "Click", "Kick", "Conga")
private val METRO_COUNT_IN = listOf("off", "1 bar", "2 bars")

private val PANEL_TOP = Color(0xFF232C3E)
private val PANEL_BOTTOM = Color(0xFF1A2230)
private val WELL = Color(0xFF0F141D)
private val INK = Color(0xFFE9EDF5)
private val MUTE = Color(0xFF8690A4)
private val ACCENT = Color(0xFF8FB2E8)
private val METRO_GREEN = Color(0xFF6EE7B7)

// ─────────────────────────────────────────────────────────────────────────
// DpTopBar — barre du haut (mixer/retour, titre, métronome, BPM, gear)
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun DpTopBar(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    onBackToMixer: () -> Unit,
    metroOn: Boolean,
    onOpenMetro: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Brush.verticalGradient(listOf(PANEL_TOP, PANEL_BOTTOM)))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ← Mixer
        Text(
            "← Mixer",
            color = MUTE,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onBackToMixer() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )

        // Title
        Text(
            "DrumPad",
            color = INK,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.weight(1f))

        // Metronome pill
        MetronomePill(on = metroOn, onClick = onOpenMetro)

        // BPM pill with long-press ramp
        BpmPill(bpm = bpm, onBpmChange = onBpmChange)

        // Gear
        Text(
            "⚙",
            color = MUTE,
            fontSize = 16.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenSettings() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MetronomePill(on: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "metroPill")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (on) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "metroPulse"
    )
    val bg = if (on) METRO_GREEN.copy(alpha = 0.18f) else WELL
    val fg = if (on) METRO_GREEN else MUTE
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(fg)
        )
        Text(
            "Metro",
            color = fg,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun BpmPill(bpm: Int, onBpmChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(WELL)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        BpmStepButton("−", -1, onBpmChange)
        DpRoll(
            value = bpm,
            style = TextStyle(
                color = INK,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        )
        Text(
            " BPM",
            color = MUTE,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 6.dp)
        )
        BpmStepButton("+", +1, onBpmChange)
    }
}

@Composable
private fun BpmStepButton(symbol: String, delta: Int, onBpmChange: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var rampJob by remember { mutableStateOf<Job?>(null) }
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(PANEL_BOTTOM)
            .pointerInput(delta) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onBpmChange(delta)
                    rampJob = scope.launch {
                        delay(420L)
                        var step = 260L
                        while (isActive) {
                            onBpmChange(delta)
                            delay(step)
                            step = (step * 0.85f).toLong().coerceAtLeast(28L)
                        }
                    }
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.changes.all { !it.pressed }) break
                        }
                    } finally {
                        rampJob?.cancel()
                        rampJob = null
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            color = INK,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// DpMetronomeOverlay — panel 320dp à droite
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun DpMetronomeOverlay(
    state: MetroState,
    onChange: (MetroState) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DpSidePanel(title = "Metronome", onClose = onClose, modifier = modifier) {
        // Header : toggle play
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (state.on) "ON" else "OFF",
                color = if (state.on) METRO_GREEN else MUTE,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = state.on,
                onCheckedChange = { onChange(state.copy(on = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = METRO_GREEN,
                    checkedTrackColor = METRO_GREEN.copy(alpha = 0.35f),
                )
            )
        }

        Spacer(Modifier.height(12.dp))

        // SIGNATURE — grid 5 × 2
        SectionLabel("SIGNATURE")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            METRO_SIGNATURES.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { sig ->
                        ChipCell(
                            text = sig,
                            selected = state.sig == sig,
                            onClick = { onChange(state.copy(sig = sig)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // pad if last row incomplete
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // SUBDIVISION — Seg
        SectionLabel("SUBDIVISION")
        OverlaySeg(
            options = METRO_SUBDIVISIONS,
            selected = state.sub,
            onSelect = { onChange(state.copy(sub = it)) }
        )

        Spacer(Modifier.height(12.dp))

        // SOUND — grid 3 × 2
        SectionLabel("SOUND")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            METRO_SOUNDS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { s ->
                        ChipCell(
                            text = s,
                            selected = state.sound == s,
                            onClick = { onChange(state.copy(sound = s)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // VOLUME
        SectionLabel("VOLUME  ${state.volume}%")
        Slider(
            value = state.volume / 100f,
            onValueChange = { onChange(state.copy(volume = (it * 100).toInt())) },
            colors = SliderDefaults.colors(
                thumbColor = ACCENT,
                activeTrackColor = ACCENT,
                inactiveTrackColor = WELL,
            )
        )

        Spacer(Modifier.height(12.dp))

        // Toggles
        SectionLabel("ACCENTS")
        ToggleRow("Accent 1", state.accent1) { onChange(state.copy(accent1 = it)) }
        ToggleRow("Accent 1 & 3", state.accent13) { onChange(state.copy(accent13 = it)) }
        ToggleRow("Visual flash", state.visualFlash) { onChange(state.copy(visualFlash = it)) }

        Spacer(Modifier.height(12.dp))

        // COUNT-IN
        SectionLabel("COUNT-IN")
        OverlaySeg(
            options = METRO_COUNT_IN,
            selected = state.countIn,
            onSelect = { onChange(state.copy(countIn = it)) }
        )

        Spacer(Modifier.height(12.dp))

        // TAP TEMPO
        TapTempoButton(state = state, onChange = onChange)
    }
}

@Composable
private fun TapTempoButton(state: MetroState, onChange: (MetroState) -> Unit) {
    var lastTapNs by remember { mutableStateOf(0L) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(WELL)
            .clickable {
                val now = System.nanoTime()
                val prev = lastTapNs
                if (prev > 0L && now - prev in 200_000_000L..2_000_000_000L) {
                    val computed = (60_000_000_000L / (now - prev)).toInt().coerceIn(30, 300)
                    onChange(state.copy(tapBpm = computed))
                }
                lastTapNs = now
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "TAP TEMPO",
            color = INK,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            if (state.tapBpm > 0) "${state.tapBpm} BPM" else "—",
            color = MUTE,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// DpDrumPadSettings — panel 320dp à droite
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun DpDrumPadSettings(
    padCount: Int,
    onPadCountChange: (Int) -> Unit,
    preload: Boolean,
    onPreloadChange: (Boolean) -> Unit,
    hapticPads: Boolean,
    onHapticPadsChange: (Boolean) -> Unit,
    hapticLoops: Boolean,
    onHapticLoopsChange: (Boolean) -> Unit,
    quantize: String,
    onQuantizeChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DpSidePanel(title = "DrumPad Settings", onClose = onClose, modifier = modifier) {
        SectionLabel("LAYOUT")
        OverlaySeg(
            options = listOf("8 pads", "16 pads"),
            selected = if (padCount == 16) "16 pads" else "8 pads",
            onSelect = { onPadCountChange(if (it == "16 pads") 16 else 8) }
        )

        Spacer(Modifier.height(16.dp))

        SectionLabel("PERFORMANCE")
        ToggleRow("Preload samples", preload, onPreloadChange)
        ToggleRow("Haptics — pads", hapticPads, onHapticPadsChange)
        ToggleRow("Haptics — loops", hapticLoops, onHapticLoopsChange)

        Spacer(Modifier.height(16.dp))

        SectionLabel("DEFAULT LOOP")
        OverlaySeg(
            options = listOf("1/16", "Free"),
            selected = quantize,
            onSelect = onQuantizeChange
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// DpFxSheet — sheet centrée, 5 ou 4 effets
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun DpFxSheet(
    chain: SurfaceFxChain,
    includeShimmer: Boolean,
    label: String,
    onChange: (SurfaceFxChain) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DpCenteredSheet(title = label, onClose = onClose, modifier = modifier) {
        FxRow(
            name = "Filter",
            color = DpPalette.at(0),
            on = chain.filterOn,
            amount = chain.filterAmount,
            onToggle = { onChange(chain.copy(filterOn = it)) },
            onAmount = { onChange(chain.copy(filterAmount = it)) }
        )
        FxRow(
            name = "Brightness",
            color = DpPalette.at(3),
            on = chain.brightnessOn,
            amount = chain.brightnessAmount,
            onToggle = { onChange(chain.copy(brightnessOn = it)) },
            onAmount = { onChange(chain.copy(brightnessAmount = it)) }
        )
        FxRow(
            name = "Delay",
            color = DpPalette.at(5),
            on = chain.delayOn,
            amount = chain.delayAmount,
            onToggle = { onChange(chain.copy(delayOn = it)) },
            onAmount = { onChange(chain.copy(delayAmount = it)) }
        )
        FxRow(
            name = "Reverb",
            color = DpPalette.at(4),
            on = chain.reverbOn,
            amount = chain.reverbAmount,
            onToggle = { onChange(chain.copy(reverbOn = it)) },
            onAmount = { onChange(chain.copy(reverbAmount = it)) }
        )
        if (includeShimmer) {
            FxRow(
                name = "Shimmer",
                color = DpPalette.at(1),
                on = chain.shimmerOn,
                amount = chain.shimmerAmount,
                onToggle = { onChange(chain.copy(shimmerOn = it)) },
                onAmount = { onChange(chain.copy(shimmerAmount = it)) }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "DSP applies in next update.",
            color = MUTE,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun FxRow(
    name: String,
    color: Color,
    on: Boolean,
    amount: Float,
    onToggle: (Boolean) -> Unit,
    onAmount: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (on) color else MUTE.copy(alpha = 0.3f))
            )
            Text(
                name,
                color = if (on) INK else MUTE,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(amount * 100).toInt()}",
                color = MUTE,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(28.dp)
            )
            Switch(
                checked = on,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = color,
                    checkedTrackColor = color.copy(alpha = 0.35f),
                )
            )
        }
        if (on) {
            Slider(
                value = amount.coerceIn(0f, 1f),
                onValueChange = onAmount,
                colors = SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = color,
                    inactiveTrackColor = WELL,
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun DpSidePanel(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() }
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .width(320.dp)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(PANEL_TOP, PANEL_BOTTOM)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume */ }
                .padding(16.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        title,
                        color = INK,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "✕",
                        color = MUTE,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onClose() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun DpCenteredSheet(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() }
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .width(420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(PANEL_TOP, PANEL_BOTTOM)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume */ }
                .padding(20.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        title,
                        color = INK,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "✕",
                        color = MUTE,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onClose() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MUTE,
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun ChipCell(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) PANEL_TOP else WELL)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) INK else MUTE,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = INK,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ACCENT,
                checkedTrackColor = ACCENT.copy(alpha = 0.35f),
            )
        )
    }
}

@Composable
private fun OverlaySeg(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(WELL)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { opt ->
            val isSel = opt == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSel) PANEL_TOP else Color.Transparent)
                    .clickable { onSelect(opt) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    opt,
                    color = if (isSel) INK else MUTE,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
