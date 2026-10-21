package com.aetherkeys.fx.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.ui.fx.FxFonts
import com.soundstage.mixer.ui.fx.FxStyle
import com.soundstage.mixer.ui.fx.drawFxRail

/**
 * The open effect window (`#ex` content), on the same 825x343 logical canvas as
 * the rest of the window. Geometry is `FxGeom` / `FxKnobBoxes`, measured in the
 * reference, and used verbatim: 1 logical px = 1 dp here, the caller owns the
 * single scale. Knob coordinates are window coordinates (the measured boxes
 * already include the content origin, verified on ref-10-amp.png: Gain sits at
 * x = 91.2 while its box starts at x = 68).
 */
@Composable
fun FxOpenPanel(
    module: EffectModule,
    params: FloatArray,
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
    val clock = rememberFxClock(fullRate = true)
    Box(modifier = modifier.size(CANVAS_W.dp, CANVAS_H.dp)) {
        // `.bd` is the scene band: 825x343 at (13,42), not the whole window. The
        // `.xN` background is painted there; the header and the bottom margin show
        // `#ex`'s own #10141d, which the host draws.
        Box(
            Modifier
                .offset(x = BAND_X.dp, y = BAND_Y.dp)
                .size(BAND_W.dp, BAND_H.dp)
                // A Canvas does not clip: the scenes fill from (-OXL,-OYL), which
                // spills over the header and the margins. The reference band is
                // bounded, so the spill has to be cut here.
                .clipToBounds(),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val d = this.density
                withTransform({ scale(d, d, pivot = Offset.Zero) }) {
                    with(module) { drawScene(params, clock.value, mini = false, tm = tm) }
                    // Only the instruments that actually have one get the bottom rail.
                    // The amp (10) and the bass (11) have no `RS` entry: their "Level"
                    // is a regular knob and the panel runs to the bottom.
                    if (FxStyle.hasRail(module.id)) {
                        drawFxRail(module.id, params.getOrElse(FxStyle.railLevelIndex(module.id)) { 0f }, tm)
                    }
                }
            }
        }

        // `#cl`: close chip pinned at 12,12 (top-left), the `.ph` padding clears it.
        Box(Modifier.offset(x = 12.dp, y = 12.dp)) {
            SmallChip("⌄", onClose)
        }

        // `#pm`: preset menu pinned top-right (176 wide, top 11).
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 11.dp)) {
            PresetChip(activePreset ?: presets.firstOrNull() ?: "", presets, onPreset)
        }

        // `.ph`: height 58, padding 0 16 0 58.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(58.dp).padding(start = 58.dp, end = 200.dp),
        ) {
            BypassChip(bypassed, onBypass)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(module.name, fontSize = 30.sp, fontFamily = FxFonts.title(module.id),
                    color = Color(0xFFDFE4EE), maxLines = 1)
                Text(module.type.uppercase(), fontSize = 9.sp, fontFamily = FxFonts.ui,
                    fontWeight = FontWeight.Bold, letterSpacing = (9 * 0.18f).sp,
                    color = Color(0xFFE6EAF3).copy(alpha = 0.6f), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            if (module.id >= 5) {
                SmallChip("Surprise me", onSurprise)
                Spacer(Modifier.width(8.dp))
                SmallChip(abLabel, onAb)
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
            val diameter = (box.w * 0.86f).dp
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
                    x = (BAND_X + cx).dp - diameter / 2f,
                    y = (BAND_Y + cy).dp - diameter / 2f,
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
private fun BypassChip(bypassed: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
    ) {
        Box(
            Modifier.size(8.dp).clip(RoundedCornerShape(50))
                .background(if (bypassed) Color(0xFF2A3040) else Color(0xFFF2D58A)),
        )
        Spacer(Modifier.width(7.dp))
        Text("Bypass", fontSize = 10.sp, fontFamily = FxFonts.ui,
            fontWeight = FontWeight.Bold, letterSpacing = (10 * 0.06f).sp, color = Color(0xFFE6EAF3))
    }
}

/** `.ib`: height 28, radius 9, gradient #ffffff14 -> #ffffff08, 10 px bold. */
@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 10.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
            letterSpacing = (10 * 0.06f).sp, color = Color(0xFFE6EAF3), maxLines = 1)
    }
}

/** Preset chip + list (`#pm` / `#pml`), values only, never the surface. */
@Composable
private fun PresetChip(current: String, presets: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SmallChip("$current ▾") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            presets.forEach { p ->
                DropdownMenuItem(text = { Text(p) }, onClick = { open = false; onPick(p) })
            }
        }
    }
}
