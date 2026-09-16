package com.soundstage.mixer.ui.components

import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.FxParameters
import com.soundstage.mixer.model.TrackChannel
import com.soundstage.mixer.ui.theme.*

@Composable
fun EffectsDialog(
    trackId: Int,
    track: TrackChannel? = null,
    fxParameters: FxParameters,
    onUpdateFx: ((FxParameters) -> FxParameters) -> Unit,
    onSetReverbPreset: (String) -> Unit = {},
    onSetVelocityCurve: (Float) -> Unit = {},
    activeTab: String,
    onTabChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = if (trackId == 0) "Master FX Rack" else "Track $trackId FX"

    val tabs = if (trackId == 0) {
        listOf("eq" to "EQ", "reverb" to "Reverb", "comp" to "Compressor", "delay" to "Delay", "sg" to "Maximizer")
    } else {
        listOf(
            "reverb" to "Reverb",
            "velocity" to "Velocity"
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(540.dp)
                .fillMaxHeight(0.90f)
                .shadow(28.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF141722), Color(0xFF0C0E17), Color(0xFF080A10))
                    )
                )
                .border(1.5.dp, Color(0x3322D3EE), RoundedCornerShape(16.dp))
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NeonCyan)
                                .shadow(6.dp, CircleShape, spotColor = NeonCyan)
                        )
                        Text(
                            text = title.uppercase(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(6.dp))
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(tabs) { (tabKey, tabLabel) ->
                        val isSelected = activeTab == tabKey
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) Brush.verticalGradient(listOf(Color(0xFF00E5FF), Color(0xFF00B8D4))) else Brush.linearGradient(listOf(Color(0x14FFFFFF), Color(0x0AFFFFFF)))
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color.Transparent else Color(0x14FFFFFF),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onTabChange(tabKey) }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabLabel,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF002233) else TextDim
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // TAB CONTENTS
                when (activeTab) {
                    "reverb" -> {
                        val reverbPresets = listOf(
                            "Concert Hall", "Warm Room", "Plate 80s", "Cathedral", "Ambient Shimmer", "Vocal Chamber", "Studio Room"
                        )
                        val isEnabled = fxParameters.isReverbEnabled
                        val currentPreset = track?.reverbPreset ?: fxParameters.reverbPreset

                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "REVERB PRESETS",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDim,
                                    letterSpacing = 0.6.sp
                                )

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
                                        text = if (isEnabled) "REVERB ON" else "BYPASS",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isEnabled) Color(0xFF002933) else TextDim
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
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
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
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

                            Spacer(modifier = Modifier.height(12.dp))

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
                                    text = "DYNAMIC VELOCITY CURVE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Adjust keyboard strike sensitivity and response curve for this track.",
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
                                    .background(Color(0xFF080A10))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
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
                                    Text(text = "Soft Touch", fontSize = 9.sp, color = TextDim)
                                    Text(
                                        text = when {
                                            curveValue < 0.35f -> "Soft"
                                            curveValue > 0.65f -> "Hard"
                                            else -> "Linear"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonCyan
                                    )
                                    Text(text = "Hard Touch", fontSize = 9.sp, color = TextDim)
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
                    "eq" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 3D Parametric Spectrum Analyzer Screen (FabFilter Pro Q3 inspired)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(95.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF07090F))
                                    .border(1.2.dp, Color(0x3322D3EE), RoundedCornerShape(10.dp))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height

                                    // Grid lines
                                    val gridColor = Color(0x14FFFFFF)
                                    drawLine(gridColor, Offset(0f, h * 0.25f), Offset(w, h * 0.25f))
                                    drawLine(gridColor, Offset(0f, h * 0.50f), Offset(w, h * 0.50f))
                                    drawLine(gridColor, Offset(0f, h * 0.75f), Offset(w, h * 0.75f))
                                    drawLine(gridColor, Offset(w * 0.33f, 0f), Offset(w * 0.33f, h))
                                    drawLine(gridColor, Offset(w * 0.66f, 0f), Offset(w * 0.66f, h))

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

                                    // Glowing Band Nodes
                                    drawCircle(NeonCyan, radius = 4.dp.toPx(), center = Offset(w * 0.2f, midH - lowShift - gainShift))
                                    drawCircle(Color(0xFF84CC16), radius = 4.dp.toPx(), center = Offset(w * 0.5f, midH - midShift - gainShift))
                                    drawCircle(Color(0xFFEC4899), radius = 4.dp.toPx(), center = Offset(w * 0.8f, midH - highShift - gainShift))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

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
                        // 3D SSL Bus Compressor Rack with Analog VU Meter
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Analog VU Meter Screen
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(75.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFFF8E7)) // Vintage amber VU paper face
                                    .border(2.dp, Color(0xFF2B2519), RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height

                                    // Arc & Scale text
                                    drawArc(
                                        color = Color(0xFF332A1B),
                                        startAngle = 210f,
                                        sweepAngle = 120f,
                                        useCenter = false,
                                        style = Stroke(width = 2.dp.toPx())
                                    )

                                    // Dynamic Gain Reduction Needle Angle
                                    val reduction = (1f - fxParameters.compThresh) * fxParameters.compRatio
                                    val targetAngle = 210f + (reduction * 120f).coerceIn(0f, 120f)
                                    val rad = Math.toRadians(targetAngle.toDouble())

                                    val pivot = Offset(w / 2f, h * 1.3f)
                                    val needleLen = h * 1.1f
                                    val tip = Offset(
                                        pivot.x + needleLen * cos(rad).toFloat(),
                                        pivot.y + needleLen * sin(rad).toFloat()
                                    )

                                    drawLine(
                                        color = Color(0xFFD92B2B), // Classic red needle
                                        start = pivot,
                                        end = tip,
                                        strokeWidth = 2.5.dp.toPx()
                                    )

                                    drawCircle(Color(0xFF221B10), radius = 5.dp.toPx(), center = pivot)
                                }
                                Text(
                                    text = "GAIN REDUCTION (dB)",
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4A3E2C),
                                    modifier = Modifier.align(Alignment.TopCenter)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

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
                                        size = 44.dp
                                    )
                                }
                                FxUnitCard("Ratio") {
                                    RotaryKnob(
                                        value = fxParameters.compRatio,
                                        onValueChange = { v -> onUpdateFx { it.copy(compRatio = v) } },
                                        label = "Ratio",
                                        valueText = "1:${(1 + fxParameters.compRatio * 15).toInt()}",
                                        size = 44.dp
                                    )
                                }
                                FxUnitCard("Attack") {
                                    RotaryKnob(
                                        value = fxParameters.compAttack,
                                        onValueChange = { v -> onUpdateFx { it.copy(compAttack = v) } },
                                        label = "Attack",
                                        valueText = "${(fxParameters.compAttack * 100).toInt()}ms",
                                        size = 44.dp
                                    )
                                }
                                FxUnitCard("Release") {
                                    RotaryKnob(
                                        value = fxParameters.compRelease,
                                        onValueChange = { v -> onUpdateFx { it.copy(compRelease = v) } },
                                        label = "Release",
                                        valueText = "${(fxParameters.compRelease * 500).toInt()}ms",
                                        size = 44.dp
                                    )
                                }
                            }
                        }
                    }
                    "delay" -> {
                        val isDelayOn = fxParameters.isDelayEnabled
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "STEREO DELAY / ECHO",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDim,
                                    letterSpacing = 0.6.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isDelayOn) NeonCyan else Color(0x1AFFFFFF))
                                        .clickable {
                                            val nextOn = !isDelayOn
                                            onUpdateFx {
                                                it.copy(
                                                    isDelayEnabled = nextOn,
                                                    delayMix = if (nextOn && it.delayMix < 0.05f) 0.25f else it.delayMix
                                                )
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isDelayOn) "DELAY ON" else "DELAY OFF",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDelayOn) Color(0xFF002233) else TextDim
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
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
                                        size = 44.dp
                                    )
                                }
                                FxUnitCard("Feedback") {
                                    RotaryKnob(
                                        value = fxParameters.delayFeedback,
                                        onValueChange = { v -> onUpdateFx { it.copy(delayFeedback = v) } },
                                        label = "Feedback",
                                        valueText = "${(fxParameters.delayFeedback * 100).toInt()}%",
                                        size = 44.dp
                                    )
                                }
                                FxUnitCard("Mix") {
                                    RotaryKnob(
                                        value = fxParameters.delayMix,
                                        onValueChange = { v ->
                                            onUpdateFx {
                                                it.copy(
                                                    delayMix = v,
                                                    isDelayEnabled = v > 0.01f
                                                )
                                            }
                                        },
                                        label = "Mix",
                                        valueText = "${(fxParameters.delayMix * 100).toInt()}%",
                                        size = 44.dp
                                    )
                                }
                                FxUnitCard("Ping-Pong") {
                                    RotaryKnob(
                                        value = fxParameters.delayPingPong,
                                        onValueChange = { v -> onUpdateFx { it.copy(delayPingPong = v) } },
                                        label = "Ping-Pong",
                                        valueText = if (fxParameters.delayPingPong > 0.5f) "ON" else "OFF",
                                        size = 44.dp
                                    )
                                }
                            }
                        }
                    }
                    "sg" -> {
                        SoundGoodizerMasterView(
                            isEnabled = fxParameters.isSgEnabled,
                            amount = fxParameters.sgAmount,
                            mode = fxParameters.sgMode,
                            onToggleEnabled = {
                                val nextState = !fxParameters.isSgEnabled
                                onUpdateFx {
                                    it.copy(
                                        isSgEnabled = nextState,
                                        sgAmount = if (nextState && it.sgAmount < 0.05f) 0.50f else it.sgAmount
                                    )
                                }
                            },
                            onAmountChange = { newAmt ->
                                onUpdateFx {
                                    it.copy(
                                        sgAmount = newAmt,
                                        isSgEnabled = newAmt > 0.001f
                                    )
                                }
                            },
                            onModeChange = { newMode ->
                                onUpdateFx { it.copy(sgMode = newMode) }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * SoundGoodizer Master Effect View
 * Features:
 * - Grand Knob with vibrant luminous contour arc
 * - Center glowing percentage readout
 * - Minimal aesthetic ABCD preset capsules (A: Punch, B: Warmth, C: Air, D: Maximizer)
 * - Power bypass switch
 */
@Composable
private fun SoundGoodizerMasterView(
    isEnabled: Boolean,
    amount: Float,
    mode: Int,
    onToggleEnabled: () -> Unit,
    onAmountChange: (Float) -> Unit,
    onModeChange: (Int) -> Unit
) {
    val modeColor = when (mode) {
        0 -> Color(0xFF00F5FF) // A: Electric Cyan
        1 -> Color(0xFFF43F5E) // B: Warm Rose/Pink
        2 -> Color(0xFFF59E0B) // C: Warm Gold/Amber
        3 -> Color(0xFF10B981) // D: Emerald Boost
        else -> Color(0xFF00F5FF)
    }

    val activeGlowColor = if (isEnabled) modeColor else Color(0x33FFFFFF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Sub-Header with Power Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) modeColor else Color(0x44FFFFFF))
                        .shadow(if (isEnabled) 6.dp else 0.dp, CircleShape, spotColor = modeColor)
                )
                Text(
                    text = "MAXIMISER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) Color.White else TextDim,
                    letterSpacing = 1.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isEnabled) modeColor.copy(alpha = 0.25f) else Color(0x14FFFFFF))
                    .border(1.dp, if (isEnabled) modeColor else Color(0x22FFFFFF), RoundedCornerShape(6.dp))
                    .clickable { onToggleEnabled() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isEnabled) "ACTIVE" else "BYPASS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isEnabled) modeColor else TextDim
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Center: GRAND KNOB WITH LUMINOUS CONTOUR & DUAL PEAK LED METERS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Input Peak Meter
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "IN", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = TextDim)
                Spacer(modifier = Modifier.height(2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val inActive = if (isEnabled) (amount * 8 + 2).toInt().coerceIn(0, 10) else 0
                    for (i in 9 downTo 0) {
                        val segColor = when {
                            i >= 8 -> Color(0xFFEF4444)
                            i >= 6 -> Color(0xFFF59E0B)
                            else -> modeColor
                        }
                        Box(
                            modifier = Modifier
                                .size(width = 14.dp, height = 5.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (i < inActive) segColor else Color(0x1EFFFFFF))
                        )
                    }
                }
            }

            GrandLuminousKnob(
                value = if (isEnabled) amount else 0f,
                onValueChange = onAmountChange,
                glowColor = activeGlowColor,
                isEnabled = isEnabled,
                modifier = Modifier.size(116.dp)
            )

            // Output Peak Meter
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "OUT", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = TextDim)
                Spacer(modifier = Modifier.height(2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val outActive = if (isEnabled) (amount * 9 + 1).toInt().coerceIn(0, 10) else 0
                    for (i in 9 downTo 0) {
                        val segColor = when {
                            i >= 8 -> Color(0xFFEF4444)
                            i >= 6 -> Color(0xFFF59E0B)
                            else -> modeColor
                        }
                        Box(
                            modifier = Modifier
                                .size(width = 14.dp, height = 5.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (i < outActive) segColor else Color(0x1EFFFFFF))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Bottom: 3D REALISTIC HARDWARE CONSOLE ABCD PUSH-SWITCHES
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modesList = listOf(
                Triple(0, "A", "Punch"),
                Triple(1, "B", "Warmth"),
                Triple(2, "C", "Air"),
                Triple(3, "D", "Boost")
            )

            modesList.forEach { (mIndex, mLabel, mDesc) ->
                val isSelected = mode == mIndex
                val buttonColor = when (mIndex) {
                    0 -> Color(0xFF00F5FF)
                    1 -> Color(0xFFF43F5E)
                    2 -> Color(0xFFF59E0B)
                    3 -> Color(0xFF10B981)
                    else -> Color(0xFF00F5FF)
                }

                // 3D Recessed Socket Well
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0D0F18))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                        .padding(2.dp)
                ) {
                    // Physical Push Switch Surface
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(y = if (isSelected) 1.5.dp else 0.dp)
                            .shadow(
                                elevation = if (isSelected && isEnabled) 6.dp else 2.dp,
                                shape = RoundedCornerShape(8.dp),
                                spotColor = buttonColor
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected && isEnabled) {
                                    Brush.verticalGradient(
                                        listOf(
                                            buttonColor.copy(alpha = 0.38f),
                                            Color(0xFF1E2235)
                                        )
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF252A3D),
                                            Color(0xFF141724)
                                        )
                                    )
                                }
                            )
                            .border(
                                width = if (isSelected && isEnabled) 1.5.dp else 0.8.dp,
                                color = if (isSelected && isEnabled) buttonColor else Color(0x33FFFFFF),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onModeChange(mIndex) }
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Top LED Jewel Indicator
                            Box(
                                modifier = Modifier
                                    .size(width = 14.dp, height = 3.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(
                                        if (isSelected && isEnabled) buttonColor else Color(0x33FFFFFF)
                                    )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = mLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected && isEnabled) buttonColor else Color.White
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = mDesc,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected && isEnabled) Color.White else TextDim
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Grand Knob with Luminous Contour
 * High precision rotary dial with glowing LED sweep arc and interactive drag gesture
 */
@Composable
private fun GrandLuminousKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    glowColor: Color,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val currentValState by rememberUpdatedState(value)
    val onValueChangeState by rememberUpdatedState(onValueChange)

    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "knob_val"
    )

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // Fluid, immediate tactile response to vertical/horizontal touch drag
                        val delta = (-dragAmount.y * 1.3f + dragAmount.x * 0.7f) / 100f
                        val nextVal = (currentValState + delta).coerceIn(0f, 1f)
                        onValueChangeState(nextVal)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onValueChangeState(0.5f) }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Canvas: Outer Glowing Track & Luminous Arc
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val strokeWidth = 8.dp.toPx()
            val diameter = (size.minDimension - strokeWidth - 6.dp.toPx()).coerceAtLeast(0f)
            if (diameter <= 0f) return@Canvas
            val radius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            val startAngle = 135f
            val sweepTotal = 270f
            val safeAnimVal = if (animatedValue.isNaN()) 0f else animatedValue.coerceIn(0f, 1f)
            val activeSweep = safeAnimVal * sweepTotal

            // 1. Dark Background Track
            drawArc(
                color = Color(0x22FFFFFF),
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Outer Luminous Glow Arc
            if (activeSweep > 1f) {
                drawArc(
                    color = glowColor.copy(alpha = 0.35f),
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth + 4.dp.toPx(), cap = StrokeCap.Round)
                )

                // 3. Primary Crisp Luminous Arc
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(glowColor.copy(alpha = 0.7f), glowColor, glowColor),
                        center = center
                    ),
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // 4. Indicator Needle Tip
            val tipAngleRad = Math.toRadians((startAngle + activeSweep).toDouble())
            val tipX = center.x + (radius - 2.dp.toPx()) * cos(tipAngleRad).toFloat()
            val tipY = center.y + (radius - 2.dp.toPx()) * sin(tipAngleRad).toFloat()

            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = Offset(tipX, tipY)
            )
        }

        // Inner Dial Core (Metallic brushed feel)
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(12.dp, CircleShape, ambientColor = glowColor, spotColor = glowColor)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2C2245),
                            Color(0xFF1E1630),
                            Color(0xFF120B20)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    color = if (isEnabled) glowColor.copy(alpha = 0.6f) else Color(0x22FFFFFF),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isEnabled) "${(value * 100).roundToInt()}%" else "OFF",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isEnabled) glowColor else TextDim
                )
                Text(
                    text = "AMOUNT",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0x88FFFFFF),
                    letterSpacing = 0.5.sp
                )
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
            .fillMaxHeight(0.88f)
            .shadow(6.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF222738), Color(0xFF131624), Color(0xFF0D0F1A)))
            )
            .border(1.2.dp, Brush.verticalGradient(listOf(Color(0x4022D3EE), Color(0x10FFFFFF))), RoundedCornerShape(10.dp))
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
