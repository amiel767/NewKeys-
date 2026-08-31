package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FxParameters
import com.example.model.TrackChannel
import com.example.ui.theme.*

@Composable
fun EffectsDialog(
    trackId: Int,
    track: TrackChannel? = null,
    fxParameters: FxParameters,
    isGlobalSplitterActive: Boolean = false,
    onUpdateFx: ((FxParameters) -> FxParameters) -> Unit,
    onSetReverbPreset: (String) -> Unit = {},
    onSetVelocityCurve: (Float) -> Unit = {},
    onSetSplitRange: (Int, Int) -> Unit = { _, _ -> },
    activeTab: String,
    onTabChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = if (trackId == 0) "Effets — Master" else "Effets & Réglages — Piste $trackId"

    val tabs = if (trackId == 0) {
        listOf("eq" to "EQ", "reverb" to "Reverb", "comp" to "Comp", "delay" to "Delay")
    } else {
        listOf(
            "reverb" to "Reverb",
            "velocity" to "Vélocité",
            "splitter" to "Splitter",
            "eq" to "EQ",
            "comp" to "Comp",
            "delay" to "Delay"
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x6608080C))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(520.dp)
                .fillMaxHeight(0.88f)
                .shadow(24.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF241A3D), Color(0xFF180F2C), Color(0xFF120A20))
                    )
                )
                .border(1.dp, Color(0x668B5CF6), RoundedCornerShape(20.dp))
                .clickable(enabled = false) {}
                .padding(14.dp)
                .testTag("dialog_effects")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(8.dp))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", fontSize = 12.sp, color = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable Tabs Row
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(tabs) { (tabKey, tabLabel) ->
                        val isSelected = activeTab == tabKey
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) Brush.verticalGradient(listOf(NeonPurpleLight, NeonPurple)) else Brush.linearGradient(listOf(Color(0x0DFFFFFF), Color(0x08FFFFFF)))
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color.Transparent else Color(0x14FFFFFF),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onTabChange(tabKey) }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabLabel,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else TextDim
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // TAB CONTENTS
                when (activeTab) {
                    "reverb" -> {
                        val reverbPresets = listOf(
                            "Concert Hall", "Warm Room", "Plate 80s", "Cathedral", "Ambient Shimmer", "Vocal Chamber", "Studio Room"
                        )
                        val isEnabled = fxParameters.isReverbEnabled
                        val currentPreset = track?.reverbPreset ?: fxParameters.reverbPreset

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Presets Header with ON/OFF Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "PRESETS REVERB",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDim,
                                    letterSpacing = 0.6.sp
                                )

                                // ON / OFF Button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isEnabled) Color(0xFF00E5FF) else Color(0x1EFFFFFF))
                                        .border(1.dp, if (isEnabled) Color(0xFF00E5FF) else Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                                        .clickable {
                                            onUpdateFx { it.copy(isReverbEnabled = !it.isReverbEnabled) }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isEnabled) "REVERB ON" else "BYPASS (OFF)",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isEnabled) Color(0xFF002933) else TextDim
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(reverbPresets) { preset ->
                                    val isSel = (currentPreset == preset)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSel) NeonCyan else Color(0x14FFFFFF))
                                            .border(1.dp, if (isSel) Color.Transparent else Color(0x1FFFFFFF), RoundedCornerShape(6.dp))
                                            .clickable {
                                                onSetReverbPreset(preset)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = preset,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) Color(0xFF002933) else TextPrimary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 4 Reverb Knobs (Mix, Size, Decay, Damp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FxUnitCard("Mix") {
                                    RotaryKnob(
                                        value = if (isEnabled) fxParameters.reverbMix else 0f,
                                        onValueChange = { v ->
                                            onUpdateFx { it.copy(reverbMix = v, reverbPreset = "Custom") }
                                        },
                                        label = "Mix",
                                        valueText = if (isEnabled) "${(fxParameters.reverbMix * 100).toInt()}%" else "OFF",
                                        size = 46.dp
                                    )
                                }
                                FxUnitCard("Size") {
                                    RotaryKnob(
                                        value = fxParameters.reverbSize,
                                        onValueChange = { v ->
                                            onUpdateFx { it.copy(reverbSize = v, reverbPreset = "Custom") }
                                        },
                                        label = "Size",
                                        valueText = "${(fxParameters.reverbSize * 100).toInt()}%",
                                        size = 46.dp
                                    )
                                }
                                FxUnitCard("Decay") {
                                    RotaryKnob(
                                        value = fxParameters.reverbDecay,
                                        onValueChange = { v ->
                                            onUpdateFx { it.copy(reverbDecay = v, reverbPreset = "Custom") }
                                        },
                                        label = "Decay",
                                        valueText = "${(fxParameters.reverbDecay * 6).toInt()}s",
                                        size = 46.dp
                                    )
                                }
                                FxUnitCard("Damp") {
                                    RotaryKnob(
                                        value = fxParameters.reverbDamp,
                                        onValueChange = { v ->
                                            onUpdateFx { it.copy(reverbDamp = v, reverbPreset = "Custom") }
                                        },
                                        label = "Damp",
                                        valueText = "${(fxParameters.reverbDamp * 100).toInt()}%",
                                        size = 46.dp
                                    )
                                }
                            }
                        }
                    }
                    "velocity" -> {
                        var curveValue by remember(track?.velocityCurve) { mutableFloatStateOf(track?.velocityCurve ?: 0.5f) }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column {
                                Text(
                                    text = "COURBE DE VÉLOCITÉ DÉDIÉE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Ajustez la sensibilité dynamique des frappes clavier pour cette piste.",
                                    fontSize = 9.sp,
                                    color = TextDim
                                )
                            }

                            // Dynamic Visualizer Curve
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x33000000))
                                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height
                                    val ctrlY = h - (curveValue * h)
                                    val path = Path().apply {
                                        moveTo(0f, h)
                                        quadraticTo(w * 0.5f, ctrlY, w, 0f)
                                    }
                                    drawPath(path, NeonCyan, style = Stroke(width = 3f))
                                }
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Douce (Soft)", fontSize = 9.sp, color = TextDim)
                                    Text(
                                        text = when {
                                            curveValue < 0.35f -> "Douce / Soft"
                                            curveValue > 0.65f -> "Dure / Hard"
                                            else -> "Linéaire"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonCyan
                                    )
                                    Text(text = "Dure (Hard)", fontSize = 9.sp, color = TextDim)
                                }

                                Slider(
                                    value = curveValue,
                                    onValueChange = {
                                        curveValue = it
                                        onSetVelocityCurve(it)
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = NeonCyan,
                                        activeTrackColor = NeonCyan,
                                        inactiveTrackColor = Color(0x1AFFFFFF)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    "splitter" -> {
                        val minNote = track?.splitNoteMin ?: 36
                        val maxNote = track?.splitNoteMax ?: 84

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "KEYBOARD SPLITTER — CLAVIER VIRTUEL CLASSIQUE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Touchez les touches blanches ou noires pour assigner la zone du split.",
                                        fontSize = 9.sp,
                                        color = TextDim
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isGlobalSplitterActive) Color(0x3322D3EE) else Color(0x1AFFFFFF))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isGlobalSplitterActive) "SPLITTER GLOBAL: ACTIF ✂️" else "SPLITTER INACTIF",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isGlobalSplitterActive) NeonCyan else TextDim
                                    )
                                }
                            }

                            // Classical Virtual Keyboard with white & black keys
                            SplitterKeyboard(
                                minNote = minNote,
                                maxNote = maxNote,
                                onSetSplitRange = onSetSplitRange,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    "eq" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Parametric EQ Response Graph
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(85.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x40000000))
                                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height

                                    val midH = h / 2f
                                    val lowShift = (fxParameters.eqLow - 0.5f) * (h * 0.7f)
                                    val midShift = (fxParameters.eqMid - 0.5f) * (h * 0.7f)
                                    val highShift = (fxParameters.eqHigh - 0.5f) * (h * 0.7f)
                                    val gainShift = (fxParameters.eqGain - 0.5f) * (h * 0.3f)

                                    val path = Path().apply {
                                        moveTo(0f, midH - lowShift - gainShift)
                                        cubicTo(
                                            w * 0.25f, midH - lowShift - gainShift,
                                            w * 0.40f, midH - midShift - gainShift,
                                            w * 0.50f, midH - midShift - gainShift
                                        )
                                        cubicTo(
                                            w * 0.60f, midH - midShift - gainShift,
                                            w * 0.75f, midH - highShift - gainShift,
                                            w, midH - highShift - gainShift
                                        )
                                    }

                                    drawPath(
                                        path = path,
                                        color = NeonCyanGlow,
                                        style = Stroke(width = 5f)
                                    )
                                    drawPath(
                                        path = path,
                                        color = NeonCyan,
                                        style = Stroke(width = 2.5f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FxUnitCard("Low") {
                                    RotaryKnob(
                                        value = fxParameters.eqLow,
                                        onValueChange = { v -> onUpdateFx { it.copy(eqLow = v) } },
                                        label = "Low",
                                        valueText = "${((fxParameters.eqLow - 0.5f) * 24).toInt()} dB",
                                        size = 44.dp
                                    )
                                }
                                FxUnitCard("Mid") {
                                    RotaryKnob(
                                        value = fxParameters.eqMid,
                                        onValueChange = { v -> onUpdateFx { it.copy(eqMid = v) } },
                                        label = "Mid",
                                        valueText = "${((fxParameters.eqMid - 0.5f) * 24).toInt()} dB",
                                        size = 44.dp
                                    )
                                }
                                FxUnitCard("High") {
                                    RotaryKnob(
                                        value = fxParameters.eqHigh,
                                        onValueChange = { v -> onUpdateFx { it.copy(eqHigh = v) } },
                                        label = "High",
                                        valueText = "${((fxParameters.eqHigh - 0.5f) * 24).toInt()} dB",
                                        size = 44.dp
                                    )
                                }
                                FxUnitCard("Gain") {
                                    RotaryKnob(
                                        value = fxParameters.eqGain,
                                        onValueChange = { v -> onUpdateFx { it.copy(eqGain = v) } },
                                        label = "Gain",
                                        valueText = "${((fxParameters.eqGain - 0.5f) * 12).toInt()} dB",
                                        size = 44.dp
                                    )
                                }
                            }
                        }
                    }
                    "comp" -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FxUnitCard("Thresh") {
                                RotaryKnob(
                                    value = fxParameters.compThresh,
                                    onValueChange = { v -> onUpdateFx { it.copy(compThresh = v) } },
                                    label = "Thresh",
                                    valueText = "${((fxParameters.compThresh * 40) - 40).toInt()} dB",
                                    size = 46.dp
                                )
                            }
                            FxUnitCard("Ratio") {
                                RotaryKnob(
                                    value = fxParameters.compRatio,
                                    onValueChange = { v -> onUpdateFx { it.copy(compRatio = v) } },
                                    label = "Ratio",
                                    valueText = "1:${(1 + fxParameters.compRatio * 15).toInt()}",
                                    size = 46.dp
                                )
                            }
                            FxUnitCard("Attack") {
                                RotaryKnob(
                                    value = fxParameters.compAttack,
                                    onValueChange = { v -> onUpdateFx { it.copy(compAttack = v) } },
                                    label = "Attack",
                                    valueText = "${(fxParameters.compAttack * 100).toInt()}ms",
                                    size = 46.dp
                                )
                            }
                            FxUnitCard("Release") {
                                RotaryKnob(
                                    value = fxParameters.compRelease,
                                    onValueChange = { v -> onUpdateFx { it.copy(compRelease = v) } },
                                    label = "Release",
                                    valueText = "${(fxParameters.compRelease * 500).toInt()}ms",
                                    size = 46.dp
                                )
                            }
                        }
                    }
                    "delay" -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FxUnitCard("Time") {
                                RotaryKnob(
                                    value = fxParameters.delayTime,
                                    onValueChange = { v -> onUpdateFx { it.copy(delayTime = v) } },
                                    label = "Time",
                                    valueText = "${(fxParameters.delayTime * 1000).toInt()}ms",
                                    size = 46.dp
                                )
                            }
                            FxUnitCard("Feedback") {
                                RotaryKnob(
                                    value = fxParameters.delayFeedback,
                                    onValueChange = { v -> onUpdateFx { it.copy(delayFeedback = v) } },
                                    label = "Feedback",
                                    valueText = "${(fxParameters.delayFeedback * 100).toInt()}%",
                                    size = 46.dp
                                )
                            }
                            FxUnitCard("Mix") {
                                RotaryKnob(
                                    value = fxParameters.delayMix,
                                    onValueChange = { v -> onUpdateFx { it.copy(delayMix = v) } },
                                    label = "Mix",
                                    valueText = "${(fxParameters.delayMix * 100).toInt()}%",
                                    size = 46.dp
                                )
                            }
                            FxUnitCard("Ping-Pong") {
                                RotaryKnob(
                                    value = fxParameters.delayPingPong,
                                    onValueChange = { v -> onUpdateFx { it.copy(delayPingPong = v) } },
                                    label = "Ping-Pong",
                                    valueText = if (fxParameters.delayPingPong > 0.5f) "ON" else "OFF",
                                    size = 46.dp
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
fun FxUnitCard(
    title: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .width(96.dp)
            .fillMaxHeight(0.85f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF2B2B37), Color(0xFF1C1C26)))
            )
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
