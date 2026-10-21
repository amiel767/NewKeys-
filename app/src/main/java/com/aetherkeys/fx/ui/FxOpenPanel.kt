package com.aetherkeys.fx.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.ui.fx.FxFonts

/**
 * The open effect window (`#ex` content). Geometry is `FxGeom` / `FxKnobBoxes`,
 * measured in the reference. All knob coordinates are window coordinates (the
 * measured boxes already include the content origin, verified on ref-10-amp.png:
 * Gain sits at x = 91.2 while its box starts at x = 68).
 */
@Composable
fun FxOpenPanel(
    module: EffectModule,
    params: FloatArray,
    scale: Float,
    presets: List<String>,
    activePreset: String?,
    bypassed: Boolean,
    abLabel: String,
    onParam: (index: Int, value: Float) -> Unit,
    onPreset: (String) -> Unit,
    onBypass: () -> Unit,
    onAb: () -> Unit,
    onSurprise: () -> Unit,
    onClose: () -> Unit,
    tm: TextMeasurer,
    modifier: Modifier = Modifier,
) {
    val s = scale
    val clock = rememberFxClock(fullRate = true)
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF10141D))) {
        // `.bd`: the scene canvas spans the whole window; scenes draw their own
        // background from (-OXL,-OYL) over PWL x PHL, exactly as the reference.
        Canvas(Modifier.fillMaxSize()) {
            with(module) { drawScene(params, clock.value, mini = false, tm = tm) }
        }

        // `#cl`: close chip pinned at 12,12 (top-left), the `.ph` padding clears it.
        Box(Modifier.offset(x = (12 * s).dp, y = (12 * s).dp)) {
            SmallChip("⌄", s, onClose)
        }

        // `#pm`: preset menu pinned top-right (176 wide, top 11).
        Box(Modifier.align(Alignment.TopEnd).offset(x = -(16 * s).dp, y = (11 * s).dp)) {
            PresetChip(activePreset ?: presets.firstOrNull() ?: "", presets, s, onPreset)
        }

        // `.ph`: height 58, padding 0 16 0 58.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height((58 * s).dp)
                .padding(start = (58 * s).dp, end = (200 * s).dp),
        ) {
            BypassChip(bypassed, s, onBypass)
            Spacer(Modifier.width((12 * s).dp))
            Column {
                Text(module.name, fontSize = (30 * s).sp, fontFamily = FxFonts.title(module.id),
                    color = Color(0xFFDFE4EE), maxLines = 1)
                Text(module.type.uppercase(), fontSize = (9 * s).sp, fontFamily = FxFonts.ui,
                    fontWeight = FontWeight.Bold, letterSpacing = (9 * s * 0.18f).sp,
                    color = Color(0xFFE6EAF3).copy(alpha = 0.6f), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            if (module.id >= 5) {
                SmallChip("Surprise me", s, onSurprise)
                Spacer(Modifier.width((8 * s).dp))
                SmallChip(abLabel, s, onAb)
            }
        }

        // Knobs, at their measured boxes. `.kn.ab` is translate(-50%,-50%) on the
        // box centre, so the visual centre is box.x + box.w/2.
        FxKnobBoxes.byEffect[module.id].orEmpty().forEachIndexed { i, box ->
            val spec = module.knobs.getOrNull(i) ?: return@forEachIndexed
            if (box.w <= 0f) return@forEachIndexed
            val v = params.getOrElse(spec.paramIndex) { 0f }
            val cx = box.x + box.w / 2f
            val cy = box.y + box.h / 2f
            val diameter = (box.w * 0.86f * s).dp
            FxKnob(
                label = box.label,
                value = v,
                accent = module.accent,
                diameter = diameter,
                valueText = formatValue(module, spec.paramIndex, v),
                style = KnobStyle.forEffect(module.id),
                light = module.id in LIGHT_EFFECTS,
                onChange = { onParam(spec.paramIndex, it) },
                modifier = Modifier.offset(
                    x = (cx * s).dp - diameter / 2f,
                    y = (cy * s).dp - diameter / 2f,
                ),
            )
        }
    }
}

/** Effects whose knob bodies are dark, from `LIGHT` in the reference. */
private val LIGHT_EFFECTS = setOf(1, 3, 4, 8, 10, 11, 12, 13)

/** `.ph .nm`/`label` value readout: the reference prints the raw value plus unit. */
private fun formatValue(module: EffectModule, index: Int, norm: Float): String {
    val p = module.paramAt(index) ?: return "${(norm * 100).toInt()}"
    val raw = p.min + (p.max - p.min) * norm
    val d = p.decimals
    return "%.${d}f%s".format(raw, p.unit)
}

@Composable
private fun BypassChip(bypassed: Boolean, s: Float, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height((28 * s).dp)
            .clip(RoundedCornerShape((9 * s).dp))
            .background(Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape((9 * s).dp))
            .clickable(onClick = onClick)
            .padding(horizontal = (11 * s).dp),
    ) {
        Box(
            Modifier.size((8 * s).dp).clip(RoundedCornerShape(50))
                .background(if (bypassed) Color(0xFF2A3040) else Color(0xFFF2D58A)),
        )
        Spacer(Modifier.width((7 * s).dp))
        Text("Bypass", fontSize = (10 * s).sp, fontFamily = FxFonts.ui,
            fontWeight = FontWeight.Bold, letterSpacing = (10 * s * 0.06f).sp, color = Color(0xFFE6EAF3))
    }
}

/** `.ib`: height 28, radius 9, gradient #ffffff14 -> #ffffff08, 10 px bold. */
@Composable
private fun SmallChip(label: String, s: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height((28 * s).dp)
            .clip(RoundedCornerShape((9 * s).dp))
            .background(Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape((9 * s).dp))
            .clickable(onClick = onClick)
            .padding(horizontal = (11 * s).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = (10 * s).sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
            letterSpacing = (10 * s * 0.06f).sp, color = Color(0xFFE6EAF3), maxLines = 1)
    }
}

/** Preset chip + list (`#pm` / `#pml`), values only, never the surface. */
@Composable
private fun PresetChip(current: String, presets: List<String>, s: Float, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SmallChip("$current ▾", s) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            presets.forEach { p ->
                DropdownMenuItem(text = { Text(p) }, onClick = { open = false; onPick(p) })
            }
        }
    }
}
