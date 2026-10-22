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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random
import com.soundstage.mixer.fx.FxController
import com.soundstage.mixer.fx.FxRegistry

private val Accent = Color(0xFFE6EAF3)

/** Quantisation grid per effect, from the preview's STEP table (surprise me). */
private val STEP: Map<Int, Map<Int, Int>> = mapOf(
    9 to mapOf(0 to 4, 6 to 4),
    10 to mapOf(6 to 3, 9 to 2, 10 to 2),
    11 to mapOf(8 to 3),
    12 to mapOf(3 to 5, 4 to 5),
    14 to mapOf(11 to 4, 18 to 4, 19 to 4, 26 to 3),
)

/** Soundfont edit drawer, from the preview's ED table (shapers only, v1 mock). */
private val EDIT_GROUPS: List<Pair<String, List<String>>> = listOf(
    "ENVELOPE" to listOf("Attack", "Decay", "Sustain", "Release"),
    "FILTER" to listOf("Cutoff", "Resonance"),
    "TUNING" to listOf("Coarse", "Fine"),
    "VELOCITY" to listOf("Curve"),
    "VIBRATO" to listOf("Vib depth", "Vib rate"),
    "KEY RANGE" to listOf("Key low", "Key high"),
)

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
    val scene = remember(effectId, slot) { FxSceneState() }
    val scope = rememberCoroutineScope()
    val locks = remember(effectId, slot) { mutableStateOf(emptySet<Int>()) }
    var abA by remember(effectId, slot) { mutableStateOf<FloatArray?>(null) }
    var abB by remember(effectId, slot) { mutableStateOf<FloatArray?>(null) }
    var abCur by remember(effectId, slot) { mutableStateOf(controller.abCur) }
    var editOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) withFrameNanos { t = (it / 1_000_000_000f) }
    }

    fun pushAll(v: FloatArray) {
        for (i in v.indices) controller.setEffectParam(slot, i, v[i])
    }

    // A/B and Surprise me both move the knobs smoothly (420 ms in the preview).
    fun tweenTo(target: FloatArray) {
        val from = values.copyOf()
        scope.launch {
            val steps = 25
            for (k in 1..steps) {
                val u = k / steps.toFloat()
                val e = 1f - (1f - u).pow(3)
                val next = FloatArray(from.size) { i -> from[i] + (target[i] - from[i]) * e }
                values = next
                pushAll(next)
                kotlinx.coroutines.delay(17)
            }
            controller.save()
        }
    }

    fun surprise() {
        val target = values.copyOf()
        for (j in 0 until info.params.size - 1) {
            if (j in locks.value) continue
            if (effectId != 6) {
                var value = 0.12f + Random.nextFloat() * 0.78f
                val step = STEP[effectId]?.get(j)
                if (step != null) value = (value * (step - 1)).roundToInt() / (step - 1).toFloat()
                target[j] = value
            } else if (j < 18) {
                target[j] = (if (j == 2 || j == 11) 4f + Random.nextFloat() * 4f else Random.nextFloat() * 8f).roundToInt() / 8f
            } else if (j < 22 || j == 23 || j == 24) {
                target[j] = Random.nextFloat().roundToInt().toFloat()
            } else if (j == 22) {
                target[j] = floor(Random.nextFloat() * 6f) / 5f
            } else if (j == 25) {
                target[j] = if (Random.nextFloat() < 0.5f) 0.5f else 1f
            } else {
                target[j] = 0.15f + Random.nextFloat() * 0.7f
            }
        }
        tweenTo(target)
    }

    fun abToggle() {
        if (controller.abA == null) {
            controller.abA = values.copyOf()
            controller.abB = values.copyOf()
        }
        if (controller.abCur == 0) controller.abA = values.copyOf()
        else controller.abB = values.copyOf()
        controller.abCur = if (controller.abCur == 0) 1 else 0
        abA = controller.abA
        abB = controller.abB
        abCur = controller.abCur
        tweenTo(if (controller.abCur == 0) controller.abA!! else controller.abB!!)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth(0.75f)
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
                    scene.presetIndex = info.presets.indexOfFirst { it.name == presetName }.coerceAtLeast(0)
                    drawFxScene(effectId, values, t, tm, scene)
                    // The preview calls `rail()` for 5,6,7,8,9,12,13,14 only — its
                    // `RS` table has no other key. Without this guard the rail was
                    // painted on all 15, adding a copper line and a #2a1810 band
                    // across the bottom of sax (and a lighter band on piano,
                    // guitar, brass, iron-grip), which the reference does not have.
                    if (FxStyle.hasRail(effectId)) {
                        drawFxRail(instrumentId = effectId, level = level, tm = tm)
                    }
                }
            }

            if (editOpen) {
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = (34f * scale).dp, end = (10f * scale).dp)
                        .width(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xF01A2130))
                        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Text("Soundfont edit", color = Accent, fontSize = 11.sp,
                        fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold)
                    Text("Applies to the soundfont of this track. No reload.",
                        color = Color(0x99FFFFFF), fontSize = 10.sp, fontFamily = FxFonts.ui)
                    Spacer(Modifier.height(8.dp))
                    EDIT_GROUPS.forEach { (head, keys) ->
                        Text(head, color = accent, fontSize = 9.sp,
                            fontFamily = FxFonts.mono, fontWeight = FontWeight.Bold)
                        keys.forEach { k ->
                            Text(k, color = Color(0xFFB9C2D4), fontSize = 10.sp,
                                fontFamily = FxFonts.ui)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
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
                        val locked = spec.param in locks.value
                        Text(p.name, color = if (locked) accent else Color(0xFFB9C2D4),
                            fontSize = (7.5f * scale).sp, fontFamily = FxFonts.mono, maxLines = 1,
                            modifier = Modifier.clickable {
                                locks.value = if (locked) locks.value - spec.param
                                else locks.value + spec.param
                            })
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
                            fontFamily = FxFonts.mono, maxLines = 1)
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
                        controller.save()
                    },
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(
                        if (!bypassed) accent else Color(0xFF2A3040)))
                    Spacer(Modifier.width(6.dp))
                    Text("Bypass", color = Color(0xFF8A94A8), fontSize = 9.sp, fontFamily = FxFonts.mono)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(info.name, color = Accent, fontSize = (16f * scale).sp,
                        fontFamily = FxFonts.title(effectId),
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(info.type, color = Color(0xFF7A8498), fontSize = (9f * scale).sp,
                        fontFamily = FxFonts.mono, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                if (info.presets.isNotEmpty()) {
                    Box {
                        Text(presetName, color = accent, fontSize = (10f * scale).sp,
                            fontFamily = FxFonts.mono,
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
                                        controller.presetNames[slot] = preset.name
                                        if (controller.applyPreset(slot, i)) {
                                            val copy = values.copyOf()
                                            preset.values.forEachIndexed { k, v -> if (k < copy.size) copy[k] = v }
                                            values = copy
                                        }
                                        controller.save()
                                    },
                                )
                            }
                        }
                    }
                    // Weighted spacer after the preset selector: it shares the free
                    // space with the leading one, so the selector sits left of the
                    // right-hand cluster. Deliberate deviation from the reference,
                    // which pins it to the far right.
                    Spacer(Modifier.weight(0.45f))
                }
                if (effectId >= 5) {
                    Text("Surprise me", color = accent, fontSize = (10f * scale).sp,
                        fontFamily = FxFonts.mono,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(Color(0x14FFFFFF)).clickable { surprise() }
                            .padding(horizontal = 8.dp, vertical = 3.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (abCur == 0) "A" else "B", color = accent, fontSize = (10f * scale).sp,
                        fontFamily = FxFonts.mono,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(Color(0x14FFFFFF)).clickable { abToggle() }
                            .padding(horizontal = 10.dp, vertical = 3.dp))
                    if (effectId in listOf(5, 7, 8, 9, 12)) {
                        Spacer(Modifier.width(8.dp))
                        Text("Edit", color = Color(0xFFB9C2D4), fontSize = (10f * scale).sp,
                            fontFamily = FxFonts.mono,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                .background(Color(0x14FFFFFF)).clickable { editOpen = !editOpen }
                                .padding(horizontal = 8.dp, vertical = 3.dp))
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
            fontSize = 11.sp, fontFamily = FxFonts.mono)
        controller.effectIds.forEachIndexed { slot, id ->
            if (id >= 0) {
                Text("• ${FxRegistry.info(id).name}",
                    color = FxStyle.accent(id), fontSize = 12.sp)
            }
        }
    }
}
