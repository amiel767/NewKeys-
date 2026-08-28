package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ui.theme.*

@Composable
fun EffectsDialog(
    trackId: Int,
    fxParameters: FxParameters,
    onUpdateFx: ((FxParameters) -> FxParameters) -> Unit,
    activeTab: String,
    onTabChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = if (trackId == 0) "Effets — Master" else "Effets — Piste $trackId"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x6608080C))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .fillMaxHeight(0.85f)
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
                        fontSize = 14.sp,
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

                Spacer(modifier = Modifier.height(8.dp))

                // Tabs (EQ, Reverb, Comp, Delay)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("eq" to "EQ", "reverb" to "Reverb", "comp" to "Comp", "delay" to "Delay").forEach { (tabKey, tabLabel) ->
                        val isSelected = activeTab == tabKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
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
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else TextDim
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Contents
                when (activeTab) {
                    "eq" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Parametric EQ Response Graph
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(95.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x40000000))
                                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height

                                    // Grid lines
                                    for (i in 1..4) {
                                        val x = w * (i / 5f)
                                        drawLine(
                                            color = Color(0x14FFFFFF),
                                            start = Offset(x, 0f),
                                            end = Offset(x, h),
                                            strokeWidth = 1f
                                        )
                                    }
                                    for (j in 1..3) {
                                        val y = h * (j / 4f)
                                        drawLine(
                                            color = Color(0x14FFFFFF),
                                            start = Offset(0f, y),
                                            end = Offset(w, y),
                                            strokeWidth = 1f
                                        )
                                    }

                                    // Parametric EQ Curve
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

                                    // Glow curve
                                    drawPath(
                                        path = path,
                                        color = NeonCyanGlow,
                                        style = Stroke(width = 6f)
                                    )
                                    drawPath(
                                        path = path,
                                        color = NeonCyan,
                                        style = Stroke(width = 2.5f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 4 EQ Knobs
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
                                        size = 46.dp
                                    )
                                }
                                FxUnitCard("Mid") {
                                    RotaryKnob(
                                        value = fxParameters.eqMid,
                                        onValueChange = { v -> onUpdateFx { it.copy(eqMid = v) } },
                                        label = "Mid",
                                        valueText = "${((fxParameters.eqMid - 0.5f) * 24).toInt()} dB",
                                        size = 46.dp
                                    )
                                }
                                FxUnitCard("High") {
                                    RotaryKnob(
                                        value = fxParameters.eqHigh,
                                        onValueChange = { v -> onUpdateFx { it.copy(eqHigh = v) } },
                                        label = "High",
                                        valueText = "${((fxParameters.eqHigh - 0.5f) * 24).toInt()} dB",
                                        size = 46.dp
                                    )
                                }
                                FxUnitCard("Gain") {
                                    RotaryKnob(
                                        value = fxParameters.eqGain,
                                        onValueChange = { v -> onUpdateFx { it.copy(eqGain = v) } },
                                        label = "Gain",
                                        valueText = "${((fxParameters.eqGain - 0.5f) * 12).toInt()} dB",
                                        size = 46.dp
                                    )
                                }
                            }
                        }
                    }
                    "reverb" -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FxUnitCard("Mix") {
                                RotaryKnob(
                                    value = fxParameters.reverbMix,
                                    onValueChange = { v -> onUpdateFx { it.copy(reverbMix = v) } },
                                    label = "Mix",
                                    valueText = "${(fxParameters.reverbMix * 100).toInt()}%",
                                    size = 48.dp
                                )
                            }
                            FxUnitCard("Size") {
                                RotaryKnob(
                                    value = fxParameters.reverbSize,
                                    onValueChange = { v -> onUpdateFx { it.copy(reverbSize = v) } },
                                    label = "Size",
                                    valueText = "${(fxParameters.reverbSize * 100).toInt()}%",
                                    size = 48.dp
                                )
                            }
                            FxUnitCard("Decay") {
                                RotaryKnob(
                                    value = fxParameters.reverbDecay,
                                    onValueChange = { v -> onUpdateFx { it.copy(reverbDecay = v) } },
                                    label = "Decay",
                                    valueText = "${(fxParameters.reverbDecay * 5).toInt()}s",
                                    size = 48.dp
                                )
                            }
                            FxUnitCard("Damp") {
                                RotaryKnob(
                                    value = fxParameters.reverbDamp,
                                    onValueChange = { v -> onUpdateFx { it.copy(reverbDamp = v) } },
                                    label = "Damp",
                                    valueText = "${(fxParameters.reverbDamp * 100).toInt()}%",
                                    size = 48.dp
                                )
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
                                    size = 48.dp
                                )
                            }
                            FxUnitCard("Ratio") {
                                RotaryKnob(
                                    value = fxParameters.compRatio,
                                    onValueChange = { v -> onUpdateFx { it.copy(compRatio = v) } },
                                    label = "Ratio",
                                    valueText = "1:${(1 + fxParameters.compRatio * 15).toInt()}",
                                    size = 48.dp
                                )
                            }
                            FxUnitCard("Attack") {
                                RotaryKnob(
                                    value = fxParameters.compAttack,
                                    onValueChange = { v -> onUpdateFx { it.copy(compAttack = v) } },
                                    label = "Attack",
                                    valueText = "${(fxParameters.compAttack * 100).toInt()}ms",
                                    size = 48.dp
                                )
                            }
                            FxUnitCard("Release") {
                                RotaryKnob(
                                    value = fxParameters.compRelease,
                                    onValueChange = { v -> onUpdateFx { it.copy(compRelease = v) } },
                                    label = "Release",
                                    valueText = "${(fxParameters.compRelease * 500).toInt()}ms",
                                    size = 48.dp
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
                                    size = 48.dp
                                )
                            }
                            FxUnitCard("Feedback") {
                                RotaryKnob(
                                    value = fxParameters.delayFeedback,
                                    onValueChange = { v -> onUpdateFx { it.copy(delayFeedback = v) } },
                                    label = "Feedback",
                                    valueText = "${(fxParameters.delayFeedback * 100).toInt()}%",
                                    size = 48.dp
                                )
                            }
                            FxUnitCard("Mix") {
                                RotaryKnob(
                                    value = fxParameters.delayMix,
                                    onValueChange = { v -> onUpdateFx { it.copy(delayMix = v) } },
                                    label = "Mix",
                                    valueText = "${(fxParameters.delayMix * 100).toInt()}%",
                                    size = 48.dp
                                )
                            }
                            FxUnitCard("Ping-Pong") {
                                RotaryKnob(
                                    value = fxParameters.delayPingPong,
                                    onValueChange = { v -> onUpdateFx { it.copy(delayPingPong = v) } },
                                    label = "Ping-Pong",
                                    valueText = if (fxParameters.delayPingPong > 0.5f) "ON" else "OFF",
                                    size = 48.dp
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
            .fillMaxHeight(0.9f)
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
