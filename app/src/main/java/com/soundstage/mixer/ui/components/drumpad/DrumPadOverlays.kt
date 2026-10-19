package com.soundstage.mixer.ui.components.drumpad

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.DrumPadItem
import com.soundstage.mixer.model.SurfaceFxChain
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════
// MetroState (conservé, contrat inchangé)
// ═══════════════════════════════════════════════════════════════════════════
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

// ═══════════════════════════════════════════════════════════════════════════
// DpTopBar — fidèle JSX <TopBar>
// ═══════════════════════════════════════════════════════════════════════════
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
            .height(52.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DpPill(onClick = onBackToMixer) {
            Text("←", color = DpColors.SOFT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text("Mixer", color = DpColors.SOFT, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }
        DpPill(onClick = null) {
            Text("DrumPad", color = DpColors.SOFT, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.weight(1f))

        // Metro pill
        MetroPill(on = metroOn, onClick = onOpenMetro)

        // BPM pill
        BpmPill(bpm = bpm, onBpmChange = onBpmChange)

        // Gear pill
        DpPill(onClick = onOpenSettings) {
            Text("⚙", color = DpColors.SOFT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DpPill(
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2A3347), Color(0xFF202839))))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(13.dp))
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() } else Modifier
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
    }
}

@Composable
private fun MetroPill(on: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "metroPill")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "metroDotAlpha"
    )
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (on) Brush.verticalGradient(listOf(Color(0xFF2F4A3C), Color(0xFF1E3329)))
                else Brush.verticalGradient(listOf(Color(0xFF2A3347), Color(0xFF202839)))
            )
            .border(
                1.dp,
                if (on) Color(0xFF6EE7B7).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(13.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("♩", color = if (on) Color(0xFF6EE7B7) else Color(0xFFC7CFDD), fontSize = 16.sp)
        if (on) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6EE7B7).copy(alpha = dotAlpha))
            )
        }
    }
}

@Composable
private fun BpmPill(bpm: Int, onBpmChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2A3347), Color(0xFF202839))))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(13.dp))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BpmStepButton("−", -1, onBpmChange)
        Row(
            Modifier.width(82.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            DpRoll(
                value = bpm,
                style = TextStyle(
                    color = DpColors.SOFT,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
            Text(
                " BPM",
                color = DpColors.SOFT,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        BpmStepButton("+", +1, onBpmChange)
    }
}

@Composable
private fun BpmStepButton(symbol: String, delta: Int, onBpmChange: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var rampJob by remember { mutableStateOf<Job?>(null) }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(0xFF202839))
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
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            color = DpColors.SOFT,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Wrappers Sheet
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun DpSideSheet(
    title: String,
    onClose: () -> Unit,
    width: Dp = 320.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() })
        Box(Modifier.align(Alignment.TopEnd).padding(top = 58.dp, end = 12.dp).width(width).clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(Color(0xFF232C3E), Color(0xFF1A2230)))).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp)).padding(14.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, color = Color(0xFFF1F4FA), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.07f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }, contentAlignment = Alignment.Center) { DpIcon("x", 14.dp, Color(0xFF9AA5BA)) }
                }
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

@Composable
private fun DpCenterSheet(
    title: String,
    onClose: () -> Unit,
    width: Dp = 400.dp,
    topPadding: Dp = 56.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() })
        Box(Modifier.align(Alignment.TopCenter).padding(top = topPadding).width(width).clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2A3447), Color(0xFF1E2736)))).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp)).padding(14.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, color = Color(0xFFF1F4FA), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.07f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }, contentAlignment = Alignment.Center) { DpIcon("x", 13.dp, Color(0xFF9AA5BA)) }
                }
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}


@Composable
private fun DpCloseBtn(onClose: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Text("✕", color = Color(0xFF9AA5BA), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// DpMetronomeOverlay
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun DpLoopMenu(
    index: Int,
    canUndo: Boolean,
    canDouble: Boolean,
    canToggle: Boolean,
    canClear: Boolean,
    canClearAll: Boolean,
    isStopped: Boolean,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x5906090E))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }
        )
        Column(
            Modifier
                .width(190.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A3447), Color(0xFF1E2736))))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(6.dp)
        ) {
            Text(
                "LOOP ${index + 1}",
                color = Color(0xFF7B859A),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp,
                modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp)
            )
            LoopMenuItem("Undo last layer", canUndo) { onAction("undo") }
            LoopMenuItem("Double length", canDouble) { onAction("double") }
            LoopMenuItem(if (isStopped) "Play" else "Stop", canToggle) { onAction(if (isStopped) "play" else "stop") }
            LoopMenuItem("Clear", canClear) { onAction("clear") }
            LoopMenuItem("Clear all", canClearAll) { onAction("clearAll") }
        }
    }
}

@Composable
private fun LoopMenuItem(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            label,
            color = if (enabled) Color(0xFFDCE2EE) else Color(0xFF566075),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DpMetronomeOverlay(
    state: MetroState,
    onChange: (MetroState) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        DpSideSheet(title = "Metronome", onClose = onClose, width = 320.dp) {
            // ON / OFF toggle
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (state.on) Color(0xFF6EE7B7) else Color(0xFF4B5563))
                    )
                    Text(
                        if (state.on) "ON" else "OFF",
                        color = if (state.on) Color(0xFF6EE7B7) else DpColors.MUTE,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                DpToggle(checked = state.on, onChange = { onChange(state.copy(on = it)) })
            }

            Spacer(Modifier.height(10.dp))

            // SIGNATURE 5x2
            DpSectionLabel("SIGNATURE")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                METRO_SIGNATURES.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { sig ->
                            DpChipCell(
                                text = sig,
                                selected = state.sig == sig,
                                onClick = { onChange(state.copy(sig = sig)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // SUBDIVISION
            DpSectionLabel("SUBDIVISION")
            DpSeg(
                options = METRO_SUBDIVISIONS.map { it to if (it == "Triplet") "Triplet" else it },
                selected = state.sub,
                onChange = { onChange(state.copy(sub = it)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))

            // SOUND 3x2
            DpSectionLabel("SOUND")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                METRO_SOUNDS.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { s ->
                            DpChipCell(
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

            Spacer(Modifier.height(10.dp))

            // VOLUME
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "VOLUME",
                    color = Color(0xFF7B859A),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                DpSlider(
                    value = state.volume / 100f,
                    onChange = { onChange(state.copy(volume = (it * 100).toInt().coerceIn(0, 100))) },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${state.volume}%",
                    color = Color(0xFFC7CFDD),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(38.dp)
                )
            }

            Spacer(Modifier.height(6.dp))

            // Toggles
            DpToggleRow("Accent beat 1", state.accent1) { onChange(state.copy(accent1 = it)) }
            DpToggleRow("Accent beats 1 & 3", state.accent13) { onChange(state.copy(accent13 = it)) }
            DpToggleRow("Visual flash", state.visualFlash) { onChange(state.copy(visualFlash = it)) }

            Spacer(Modifier.height(6.dp))

            // Count-in
            Row(
                Modifier.fillMaxWidth().height(30.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Count-in before REC",
                    color = Color(0xFFDCE2EE),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                DpSeg(
                    options = listOf("off" to "Off", "1 bar" to "1 bar", "2 bars" to "2 bars"),
                    selected = state.countIn,
                    onChange = { onChange(state.copy(countIn = it)) },
                    modifier = Modifier.width(140.dp),
                    height = 20.dp,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Tap tempo
            val tapRef = remember { TapTempoRef() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DpColors.ACCENT.copy(alpha = 0.14f))
                    .border(1.dp, DpColors.ACCENT.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        val computed = tapRef.tap(System.currentTimeMillis())
                        if (computed != null) onChange(state.copy(tapBpm = computed))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.tapBpm > 0) "Tap Tempo · ${state.tapBpm} BPM" else "Tap Tempo",
                    color = DpColors.ACCENT,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

private class TapTempoRef {
    private var last: Long = 0L
    private val times = mutableListOf<Long>()
    fun tap(now: Long): Int? {
        if (now - last > 2500L) times.clear()
        times.add(now)
        last = now
        if (times.size > 5) times.removeAt(0)
        if (times.size < 2) return null
        var sum = 0L
        for (i in 1 until times.size) sum += times[i] - times[i - 1]
        val avg = sum.toDouble() / (times.size - 1)
        return (60000.0 / avg).toInt().coerceIn(40, 240)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// DpDrumPadSettings
// ═══════════════════════════════════════════════════════════════════════════
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
    Box(modifier.fillMaxSize()) {
        DpSideSheet(title = "DrumPad settings", onClose = onClose, width = 320.dp) {
            DpSectionLabel("LAYOUT")
            Row(
                Modifier.fillMaxWidth().height(30.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Pad count",
                    color = Color(0xFFDCE2EE),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                DpSeg(
                    options = listOf(8 to "8", 16 to "16"),
                    selected = if (padCount == 16) 16 else 8,
                    onChange = { onPadCountChange(it) },
                    modifier = Modifier.width(80.dp),
                    height = 20.dp,
                )
            }

            Spacer(Modifier.height(8.dp))

            DpSectionLabel("PERFORMANCE")
            DpToggleRow("Preload samples", preload, onPreloadChange)
            DpToggleRow("Haptics on pads", hapticPads, onHapticPadsChange)
            DpToggleRow("Haptics on loops", hapticLoops, onHapticLoopsChange)

            Spacer(Modifier.height(8.dp))

            DpSectionLabel("DEFAULT LOOP")
            Row(
                Modifier.fillMaxWidth().height(30.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Quantize",
                    color = Color(0xFFDCE2EE),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                DpSeg(
                    options = listOf("1/16" to "1/16", "free" to "Free"),
                    selected = quantize,
                    onChange = onQuantizeChange,
                    modifier = Modifier.width(110.dp),
                    height = 20.dp,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// DpFxSheet
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun DpFxSheet(
    chain: SurfaceFxChain,
    includeShimmer: Boolean,
    label: String,
    onChange: (SurfaceFxChain) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        DpCenterSheet(title = "$label effects", onClose = onClose, width = 400.dp) {
            FxRow(
                name = "Filter",
                on = chain.filterOn,
                amount = chain.filterAmount,
                onToggle = { onChange(chain.copy(filterOn = it)) },
                onAmount = { onChange(chain.copy(filterAmount = it)) }
            )
            FxRow(
                name = "Brightness",
                on = chain.brightnessOn,
                amount = chain.brightnessAmount,
                onToggle = { onChange(chain.copy(brightnessOn = it)) },
                onAmount = { onChange(chain.copy(brightnessAmount = it)) }
            )
            FxRow(
                name = "Delay",
                on = chain.delayOn,
                amount = chain.delayAmount,
                onToggle = { onChange(chain.copy(delayOn = it)) },
                onAmount = { onChange(chain.copy(delayAmount = it)) }
            )
            FxRow(
                name = "Reverb",
                on = chain.reverbOn,
                amount = chain.reverbAmount,
                onToggle = { onChange(chain.copy(reverbOn = it)) },
                onAmount = { onChange(chain.copy(reverbAmount = it)) }
            )
            if (includeShimmer) {
                FxRow(
                    name = "Shimmer",
                    on = chain.shimmerOn,
                    amount = chain.shimmerAmount,
                    onToggle = { onChange(chain.copy(shimmerOn = it)) },
                    onAmount = { onChange(chain.copy(shimmerAmount = it)) }
                )
            }
        }
    }
}

@Composable
private fun FxRow(
    name: String,
    on: Boolean,
    amount: Float,
    onToggle: (Boolean) -> Unit,
    onAmount: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Custom small toggle 40x22
        Box(
            Modifier
                .size(width = 40.dp, height = 22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (on) DpColors.WARM else Color.White.copy(alpha = 0.14f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle(!on) },
        ) {
            Box(
                Modifier
                    .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (on) Color(0xFF1A1F2B) else Color(0xFFC7CFDD))
            )
        }
        Text(
            name,
            color = Color(0xFFE9EDF5),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(82.dp)
        )
        // Bar slider
        FxSlider(
            value = amount.coerceIn(0f, 1f),
            onColor = if (on) DpColors.WARM else DpColors.ACCENT,
            onChange = onAmount,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${(amount * 100).toInt()}",
            color = Color(0xFF9AA5BA),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(30.dp)
        )
    }
}

@Composable
private fun FxSlider(
    value: Float,
    onColor: Color,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraintsCompat(
        modifier = modifier.height(22.dp),
    ) { maxWidthPx ->
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF0F141D))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        onChange((down.position.x / w).coerceIn(0f, 1f))
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            onChange((ch.position.x / w).coerceIn(0f, 1f))
                            ch.consume()
                        }
                    }
                }
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(onColor.copy(alpha = 0.4f), onColor)
                        )
                    )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// DpPadEditor
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun DpPadEditor(
    pad: DrumPadItem,
    color: Color,
    onChange: (DrumPadItem) -> Unit,
    onClear: () -> Unit,
    onAssignClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        DpCenterSheet(title = "", onClose = onClose, width = 430.dp, topPadding = 46.dp) {
            // Custom header (color dot + title + subtitle)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "Pad D${pad.id}",
                        color = Color(0xFFF1F4FA),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        if (pad.sampleFileName.isEmpty()) "No sample assigned" else pad.sampleFileName,
                        color = Color(0xFF7B859A),
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                DpCloseBtn(onClose = onClose)
            }
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DpVolumeKnob(
                    value = (pad.volume * 100f).coerceIn(0f, 100f),
                    onChange = { onChange(pad.copy(volume = (it / 100f).coerceIn(0f, 1f))) },
                    size = 56.dp,
                    onDoubleTap = 80f,
                    showLabel = true,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "Mode",
                        color = Color(0xFF9AA5BA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    DpSeg(
                        options = listOf(false to "One-shot", true to "Loop"),
                        selected = pad.isLoopMode,
                        onChange = { onChange(pad.copy(isLoopMode = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        height = 26.dp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Assign button
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(13.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAssignClick() }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("📁", color = DpColors.ACCENT, fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (pad.sampleFileName.isEmpty()) "Assign sample" else "Change sample",
                    color = Color(0xFFE9EDF5),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .height(34.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClear() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Clear pad", color = Color(0xFFC7CFDD), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .height(34.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF9DBDEB), Color(0xFF7FA3DD))))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Done", color = Color(0xFF0F1726), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Helpers
// ═══════════════════════════════════════════════════════════════════════════
@Composable
private fun DpSectionLabel(text: String) {
    Text(
        text,
        color = Color(0xFF7B859A),
        fontSize = 9.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 5.dp)
    )
}

@Composable
private fun DpChipCell(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) DpColors.ACCENT.copy(alpha = 0.18f)
                else Color.White.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                if (selected) DpColors.ACCENT.copy(alpha = 0.53f) else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) DpColors.ACCENT else Color(0xFF9AA5BA),
            fontSize = if (text.length > 6) 10.5.sp else 11.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun DpToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 34.dp, height = 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (checked) DpColors.ACCENT else Color.White.copy(alpha = 0.14f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) },
    ) {
        Box(
            Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) Color(0xFF0F1726) else Color(0xFFC7CFDD))
        )
    }
}

@Composable
private fun DpToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = Color(0xFFDCE2EE),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
        )
        DpToggle(checked = checked, onChange = onChange)
    }
}

@Composable
private fun DpSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraintsCompat(
        modifier = modifier.height(20.dp),
    ) { _ ->
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        onChange((down.position.x / w).coerceIn(0f, 1f))
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            onChange((ch.position.x / w).coerceIn(0f, 1f))
                            ch.consume()
                        }
                    }
                }
        ) {
            // track
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.14f))
            )
            // fill
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DpColors.ACCENT)
            ) {
                // thumb à droite
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F4FA))
                )
            }
        }
    }
}

// Simple compat wrapper (évite import BoxWithConstraints direct si conflit)
@Composable
private fun BoxWithConstraintsCompat(
    modifier: Modifier = Modifier,
    content: @Composable (maxWidthDp: Dp) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        content(maxWidth)
    }
}
