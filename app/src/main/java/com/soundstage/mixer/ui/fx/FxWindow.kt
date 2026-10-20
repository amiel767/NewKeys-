package com.soundstage.mixer.ui.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.fx.FxController
import com.soundstage.mixer.fx.FxRegistry

private val Accent = Color(0xFFE6EAF3)

/**
 * The Æther FX window: a single animated surface ported from
 * `fx-window-preview.html`. Logical region 825x343, content 779x263 at (20, 58),
 * bottom rail from y = 214. Everything is drawn in that logical space and scaled
 * once to the available width, so the layout never reflows.
 *
 * Robustness (per the preview's own rules): one animation loop for the whole
 * window, restarted only when the window is (re)composed; changing preset or
 * parameter never recreates the drawing surface.
 */
@Composable
fun FxWindow(
    effectId: Int,
    slot: Int,
    controller: FxController,
    level: Float,
    onClose: () -> Unit,
) {
    val info = remember(effectId) { FxRegistry.info(effectId) }
    val accent = FxStyle.accent(effectId)
    val tm = rememberTextMeasurer()

    var t by remember { mutableFloatStateOf(0f) }
    var values by remember(effectId, slot) {
        mutableStateOf(FloatArray(FxRegistry.MAX_PARAMS) { i -> controller.params[slot].getOrElse(i) { 0f } })
    }
    var bypassed by remember(effectId, slot) { mutableStateOf(controller.bypassed.getOrElse(slot) { false }) }
    var presetName by remember(effectId, slot) { mutableStateOf(info.presets.firstOrNull()?.name ?: "Default") }
    var presetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) withFrameNanos { t = (it / 1_000_000_000f) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1F2635), Color(0xFF131925))))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(20.dp)),
    ) {
        val scale = maxWidth.value / FxStyle.PWL
        val heightDp = FxStyle.PHL * scale

        Box(Modifier.fillMaxWidth().size(maxWidth, heightDp.dp)) {
            // Scene + rail, in logical coordinates.
            Canvas(Modifier.fillMaxSize()) {
                withTransform({
                    translate(FxStyle.OXL * scale, FxStyle.OYL * scale)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    drawFxScene(effectId, values, t, tm)
                    drawFxRail(instrumentId = -1, level = level, tm = tm)
                }
            }

            // Knobs, positioned from the preview's AB() declarations.
            for (spec in knobsFor(effectId)) {
                val p = info.params.getOrNull(spec.param)
                Column(
                    modifier = Modifier.offset(
                        x = ((FxStyle.OXL + spec.x) * scale).dp,
                        y = ((FxStyle.OYL + spec.y - 20f) * scale).dp,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (p != null) {
                        Text(p.name, color = Color(0xFFB9C2D4), fontSize = (7.5f * scale).sp,
                            fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                    FxKnob(
                        value = values.getOrElse(spec.param) { 0f },
                        accent = accent,
                        default = p?.default ?: 0f,
                        sizeDp = spec.size * scale,
                    ) { nv ->
                        val copy = values.copyOf()
                        copy[spec.param] = nv
                        values = copy
                        controller.setEffectParam(slot, spec.param, nv)
                    }
                    if (p != null) {
                        Text(formatParam(p, values.getOrElse(spec.param) { 0f }),
                            color = Color(0xFF9AA4B8), fontSize = (7f * scale).sp,
                            fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                }
            }

            // Header: bypass, title, preset, close.
            Row(
                modifier = Modifier.fillMaxWidth().padding(
                    start = (FxStyle.OXL * scale).dp,
                    end = (FxStyle.OXL * scale).dp,
                    top = (6f * scale).dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        bypassed = !bypassed
                        controller.setBypass(slot, bypassed)
                    },
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(
                        if (!bypassed) accent else Color(0xFF2A3040)))
                    Spacer(Modifier.width(6.dp))
                    Text("Bypass", color = Color(0xFF8A94A8), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(info.name, color = Accent, fontSize = (16f * scale).sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(info.type, color = Color(0xFF7A8498), fontSize = (9f * scale).sp,
                        fontFamily = FontFamily.Monospace, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                if (info.presets.isNotEmpty()) {
                    Box {
                        Text(presetName, color = accent, fontSize = (10f * scale).sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x14FFFFFF))
                                .clickable { presetOpen = true }
                                .padding(horizontal = 8.dp, vertical = 3.dp))
                        DropdownMenu(expanded = presetOpen, onDismissRequest = { presetOpen = false }) {
                            info.presets.forEachIndexed { i, preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.name) },
                                    onClick = {
                                        presetOpen = false
                                        presetName = preset.name
                                        if (controller.applyPreset(slot, i)) {
                                            val copy = values.copyOf()
                                            preset.values.forEachIndexed { k, v -> if (k < copy.size) copy[k] = v }
                                            values = copy
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Text("✕", color = Color(0xFF9AA4B8), fontSize = (14f * scale).sp,
                    modifier = Modifier.clickable { onClose() }.padding(4.dp))
            }
        }
    }
}

/** Value as shown by the preview's `val()`: scaled to real units and rounded. */
private fun formatParam(p: com.soundstage.mixer.fx.FxParam, norm: Float): String {
    val real = FxRegistry.toReal(p, norm)
    val text = when (p.decimals) {
        0 -> real.toInt().toString()
        1 -> String.format("%.1f", real)
        else -> String.format("%.2f", real)
    }
    return "$text${p.unit}"
}

/** Vertical list of the FX cards, used as the rack placeholder. */
@Composable
fun FxRack(controller: FxController, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val count = controller.effectIds.count { it >= 0 }
        Text("$count / ${controller.slotCount} effects", color = Color(0xFF8A94A8),
            fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        controller.effectIds.forEachIndexed { slot, id ->
            if (id >= 0) {
                Text("• ${FxRegistry.info(id).name}",
                    color = FxStyle.accent(id), fontSize = 12.sp)
            }
        }
    }
}
