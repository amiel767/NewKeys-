package com.aetherkeys.fx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.ui.fx.FxFonts
import com.soundstage.mixer.ui.fx.fxPanelBackground
import kotlinx.coroutines.delay
import kotlin.math.min

/** Logical canvas of the window: the reference's `PWL` x `PHL`. */
const val CANVAS_W = 825f
const val CANVAS_H = 343f

/**
 * The whole FX window, ported from `#win` in fx-window-preview.html.
 * Chrome values are the reference's: header 42, chip 28 r9, card 158 r16,
 * panel padding 20 r22, add sheet translateY(102%) -> 0 over 500 ms.
 *
 * The window hosts one fixed logical canvas ([CANVAS_W] x [CANVAS_H]) scaled once
 * from the outside. Scenes, the rail and the knobs all live in those coordinates,
 * so the scale must never be reapplied inside. Rack XOR panel: opening an effect
 * replaces the rack entirely instead of overlaying it.
 */
@Composable
fun FxWindowHost(store: FxShellStore, modifier: Modifier = Modifier) {
    LaunchedEffect(store.notice) {
        if (store.notice != null) { delay(2400); store.notice = null }
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(
                // With an effect open the reference's `.xN` rule paints #pan, which
                // spans the whole window; the scene canvas then covers its top-left
                // 825x343 and the background shows in the right/bottom bands.
                if (store.openId != null) Brush.verticalGradient(listOf(fxPanelBackground(store.openId!!), fxPanelBackground(store.openId!!)))
                else Brush.verticalGradient(listOf(Color(0xFF1F2635), Color(0xFF131925))),
            )
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.TopStart,
    ) {
        val scale = min(maxWidth.value / CANVAS_W, maxHeight.value / CANVAS_H)
        val tm = rememberTextMeasurer()
        Box(
            modifier = Modifier
                .width(CANVAS_W.dp)
                .height(CANVAS_H.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale),
        ) {
            val openId = store.openId
            if (openId == null) {
                Column(Modifier.fillMaxSize()) {
                    Header(store)
                    Rack(store, tm)
                }
            } else {
                val module = store.modules[openId]
                if (module != null) {
                    FxOpenPanel(
                        module = module,
                        params = store.valuesOf(module.id),
                        presets = module.presets,
                        activePreset = store.activePresetOf(module.id),
                        bypassed = store.isBypassed(module.id),
                        abLabel = store.abLabel(module.id),
                        onParam = { i, v -> store.setParam(module.id, i, v) },
                        onPreset = { name -> store.applyPreset(module.id, module.presets.indexOf(name)) },
                        onBypass = { store.toggleBypass(module.id) },
                        onAb = { store.toggleAB(module.id) },
                        onSurprise = { store.surprise(module.id) },
                        onClose = { store.openId = null },
                        tm = tm,
                    )
                }
            }
        }
        // `#ad` and the notice are window chrome, not canvas content: they stay
        // unscaled so hit targets do not shrink with the canvas.
        if (store.addOpen) {
            Box(Modifier.fillMaxSize().background(Color(0xE60A0E16))) {
                FxAddOverlay(
                    modules = store.modules.values.sortedBy { it.id },
                    tab = store.addTab,
                    onTab = { store.addTab = it },
                    onPick = { m ->
                        val ok = if (m.kind == FxScreenKind.EFFECT) store.addEffect(m.id) else store.setInstrument(m.id)
                        if (ok) store.addOpen = false
                    },
                    isOn = { store.isOn(it) },
                    effectsUsed = store.effectCount(),
                    onClose = { store.addOpen = false },
                )
            }
        }
        store.notice?.let { msg ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2A3042))
                    .border(1.dp, Color(0x1CFFFFFF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text(msg, fontSize = 12.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.SemiBold, color = Color(0xFFE6EAF3)) }
        }
    }
}

/** `.wh`: height 42, padding 0 12, gap 10. */
@Composable
private fun Header(store: FxShellStore) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp),
    ) {
        Text("⌄", fontSize = 14.sp, color = Color(0xFF8D96AB))
        Text(
            "EFFECTS",
            fontSize = 12.sp,
            fontFamily = FxFonts.ui,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (12 * 0.16f).sp,
            color = Color(0xFF8D96AB),
            modifier = Modifier.weight(1f),
        )
        Text(
            "${store.effectCount()} / 6 EFFECTS",
            fontSize = 10.sp,
            fontFamily = FxFonts.ui,
            fontWeight = FontWeight.Bold,
            letterSpacing = (10 * 0.12f).sp,
            color = Color(0xFF8D96AB),
        )
        // Not yet wired to the mixer's soundfont window: say so instead of a silent no-op.
        Chip("Soundfont · Grand Piano ▾") { store.notice = "Soundfont: open it from the mixer track" }
        Chip("+ Instrument") { store.addTab = FxAddTab.INSTRUMENT; store.addOpen = true }
        Chip("+ Effect") { store.addTab = FxAddTab.EFFECTS; store.addOpen = true }
    }
}

/** `.bt`: height 28, radius 9, gradient #ffffff14 -> #ffffff08, border #ffffff14, shadow 0 2 4 #0006. */
@Composable
internal fun Chip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 11.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
            color = Color(0xFFE6EAF3), maxLines = 1)
    }
}

/** `#rack`: gap 12, padding 4 14 14. */
@Composable
private fun Rack(store: FxShellStore, tm: androidx.compose.ui.text.TextMeasurer) {
    val ids = buildList {
        store.instrumentOf(store.strip)?.let { add(it) }
        store.slotsOf(store.strip).forEach { if (it != null) add(it) }
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
    ) {
        items(ids) { id ->
            val m = store.modules[id] ?: return@items
            RackCard(m, store.valuesOf(id), tm) { store.openId = id }
        }
    }
}

/**
 * `.cd`: width 158, radius 16, padding 10, gap 6, gradient #323845 -> #222733.
 * Canvas 136x160 (`.cd canvas`), scene drawn at mini scale (m = 1).
 */
@Composable
internal fun RackCard(m: EffectModule, params: FloatArray, tm: androidx.compose.ui.text.TextMeasurer, onClick: () -> Unit) {
    val clock = rememberFxClock(fullRate = false)
    Column(
        modifier = Modifier
            .width(158.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF323845), Color(0xFF222733))))
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(m.type.uppercase(), fontSize = 8.sp, fontFamily = FxFonts.ui,
            fontWeight = FontWeight.Bold, letterSpacing = (8 * 0.16f).sp, color = Color(0xFF8D96AB), maxLines = 1)
        Text(m.name, fontSize = 20.sp, fontFamily = FxFonts.title(m.id), color = Color(0xFFDFE4EE), maxLines = 1)
        Box(
            modifier = Modifier
                .width(136.dp)
                .height(160.dp)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF12161F)),
        ) {
            // `wrapMini`: fill 136x160, translate(3, 46), scale(.165) — the whole
            // 825x343 scene at 16.5 %, not a different drawing. The scene's logical
            // units are dp, the canvas is in px, so density is folded into the scale.
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val d = this.density
                withTransform({
                    translate(3f * d, 46f * d)
                    scale(0.165f * d, 0.165f * d, pivot = Offset.Zero)
                }) {
                    with(m) { drawScene(params, clock.value, mini = true, tm = tm) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(8.dp).height(8.dp).clip(CircleShape).background(Color(0xFF2A3040)))
            Text(m.presets.firstOrNull() ?: "", fontSize = 9.sp, fontFamily = FxFonts.ui,
                fontWeight = FontWeight.SemiBold, color = Color(0xFF8D96AB), maxLines = 1)
        }
    }
}
