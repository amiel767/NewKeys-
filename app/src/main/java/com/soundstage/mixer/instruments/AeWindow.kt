package com.soundstage.mixer.instruments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * The instrument window. Fill the soundfont window's bounds (or a dialog) with it; the stage letterboxes itself.
 * Recommended size: ratio 1000:460 (≈ 2.17, the phone's own ratio), about 97 % of the screen width.
 * Closing (X) keeps the instrument loaded and its state; reopening shows exactly what was left. Unload is in the preset list.
 */
@Composable
fun AeInstrumentWindow(manager: AeManager, strip: Int, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val s = manager.sessions[strip] ?: return
    val c = remember(s) { AeCtl(s, manager) }
    val a = remember(s) { AeAnim() }
    var list by remember { mutableStateOf(false) }
    // One loop per open window: 30 fps while playing / dragging / gliding, ~8 fps idle. Cancelled when the window leaves composition.
    LaunchedEffect(s) {
        var last = System.nanoTime()
        while (true) {
            delay(if (s.held.isNotEmpty() || a.lv > 0.01f || s.tweening) 33L else 125L)
            manager.pollLevel(strip)
            val now = System.nanoTime(); a.step(min(0.1f, (now - last) / 1e9f), s); last = now
        }
    }
    AeStage(modifier) {
        val shape = RoundedCornerShape(24.su())
        Box(Modifier.fillMaxSize().clip(shape).border(1.su(), Color(0x22FFFFFF), shape)) {
            when (s.instrument) { AeInstrument.ORGAN -> AeOrganUi(c, a); AeInstrument.SYNTH -> AeSynthUi(c, a); AeInstrument.LEADS -> AeLeadsUi(c, a); AeInstrument.SAX -> AeSaxUi(c, a) }
            when (s.instrument) {
                AeInstrument.SYNTH -> AeT("ÆTHER SYNTH", 13, Color.White, Modifier.at(58, 15, 200, 18), AeFonts.display, spacing = 0.18f)
                AeInstrument.LEADS -> AeT("ÆTHER LEADS", 13, Color(0xFF6FE9FF), Modifier.at(58, 15, 200, 18), AeFonts.display, spacing = 0.18f)
                AeInstrument.SAX -> AeT("ÆTHER SAX", 13, AeC.gold, Modifier.at(58, 15, 200, 18), AeFonts.plate, FontWeight.ExtraBold, 0.2f)
                else -> Unit
            }
            val presets = manager.presets(s.instrument)
            BackArrow(10, 9, 30) { onClose() }
            HeaderBtn("◀", 650, 8, 30, 30) { manager.loadPreset(strip, s.preset - 1) }
            val ns = RoundedCornerShape(10.su())
            Row(Modifier.at(686, 7, 210, 32).clip(ns).background(Color(0x14FFFFFF)).border(1.su(), Color(0x1CFFFFFF), ns).clickable(remember { MutableInteractionSource() }, null) { list = !list }.padding(horizontal = 12.su()),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                AeT(presets[s.preset].name, 12, AeC.tx); AeT(presets[s.preset].category, 10, AeC.mute, weight = FontWeight.SemiBold)
            }
            HeaderBtn("▶", 902, 8, 30, 30) { manager.loadPreset(strip, s.preset + 1) }
            if (list) PresetList(manager, strip, s, onPick = { manager.loadPreset(strip, it); list = false }, onUnload = { list = false; onClose(); manager.unload(strip) })
        }
    }
}

/**
 * Close affordance: a quiet back chevron in the top-left corner, the way a pushed screen is
 * dismissed, rather than a ✕ boxed in the top-right corner.
 */
@Composable
private fun BoxScope.BackArrow(x: Number, y: Number, size: Number, onClick: () -> Unit) {
    val s = size.toFloat()
    Box(
        Modifier.at(x, y, size, size).clip(CircleShape)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val k = this.size.width / s
            val cx = 17f * k; val cy = 15f * k
            val w = 2.2f * k
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx + 4.5f * k, cy - 6f * k)
                lineTo(cx - 3.5f * k, cy)
                lineTo(cx + 4.5f * k, cy + 6f * k)
            }
            drawPath(p, AeC.tx, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

@Composable
private fun BoxScope.HeaderBtn(t: String, x: Number, y: Number, w: Number, h: Number, onClick: () -> Unit) {
    Box(Modifier.at(x, y, w, h).clip(RoundedCornerShape(9.su())).background(Color(0x14FFFFFF)).clickable(remember { MutableInteractionSource() }, null, onClick = onClick), contentAlignment = Alignment.Center) { AeT(t, 12, AeC.tx) }
}

@Composable
private fun BoxScope.PresetList(m: AeManager, strip: Int, s: AeSession, onPick: (Int) -> Unit, onUnload: () -> Unit) {
    val all = m.presets(s.instrument)
    val cats = listOf("All") + all.map { it.category }.distinct()
    var cat by remember { mutableStateOf("All") }
    val sh = RoundedCornerShape(18.su())
    Box(Modifier.at(16, 48, 968, 396).clip(sh).background(Color(0xF40B0F1A)).border(1.su(), Color(0x22FFFFFF), sh)) {
        LazyRow(Modifier.at(12, 12, 780, 26), horizontalArrangement = Arrangement.spacedBy(6.su())) {
            items(cats) { k ->
                val on = k == cat
                Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White else Color(0x10FFFFFF)).clickable(remember { MutableInteractionSource() }, null) { cat = k }.padding(horizontal = 12.su(), vertical = 6.su())) { AeT(k, 11, if (on) Color(0xFF0B0F1C) else AeC.tx) }
            }
        }
        Box(Modifier.at(800, 12, 156, 26).clip(RoundedCornerShape(50)).background(Color(0x33FF5C7A)).clickable(remember { MutableInteractionSource() }, null, onClick = onUnload), contentAlignment = Alignment.Center) { AeT("Unload instrument", 10, Color(0xFFFFB3C0)) }
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.at(12, 48, 944, 336), verticalArrangement = Arrangement.spacedBy(6.su()), horizontalArrangement = Arrangement.spacedBy(6.su())) {
            itemsIndexed(all) { i, p ->
                if (cat == "All" || p.category == cat) Column(Modifier.clip(RoundedCornerShape(12.su())).background(if (i == s.preset) Color(0x26FFFFFF) else Color(0x0CFFFFFF))
                    .clickable(remember { MutableInteractionSource() }, null) { onPick(i) }.padding(10.su())) { AeT(p.name, 12, AeC.tx); AeT(p.category, 10, AeC.mute, weight = FontWeight.Medium) }
            }
        }
    }
}

// ───────── Soundfont window: new left-column item + content ─────────

/** Left-column item under the Presets/All strip. Gradient pill, as in the validated preview. */
@Composable
fun AeInstrumentsTabItem(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sh = RoundedCornerShape(14.dp)
    Column(
        modifier.fillMaxWidth().shadow(if (selected) 14.dp else 8.dp, sh, spotColor = Color(0xFF7A5CFF)).clip(sh)
            .background(Brush.linearGradient(0f to AeC.p1, 0.6f to AeC.p2, 1f to AeC.p3))
            .then(if (selected) Modifier.border(1.5.dp, Color(0xCCFFFFFF), sh) else Modifier)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text("Æther Instruments", color = Color.White, fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, fontFamily = AeFonts.ui, maxLines = 1)
        Text("Organ · Synth · Leads · Sax", color = Color(0xD9FFFFFF), fontSize = 8.sp, lineHeight = 8.sp, fontWeight = FontWeight.SemiBold, fontFamily = AeFonts.ui, maxLines = 1)
    }
}

/** Right-hand content of the tab: four cards (2 x 2). Tapping one loads it NOW (audio live) then calls [onOpen]. */
@Composable
fun AeInstrumentsTabContent(manager: AeManager, strip: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val all = listOf(AeInstrument.ORGAN, AeInstrument.SYNTH, AeInstrument.LEADS, AeInstrument.SAX)
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        all.chunked(2).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { inst ->
                    val loaded = manager.sessions[strip]?.instrument == inst
                    AeTile(inst, loaded, Modifier.weight(1f).fillMaxHeight(), onClick = { if (manager.load(strip, inst) != null) onOpen() }, onUnload = { manager.unload(strip) })
                }
            }
        }
    }
}

@Composable
private fun Chip(t: String, modifier: Modifier = Modifier) = Box(modifier.clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF)).padding(horizontal = 8.dp, vertical = 4.dp)) {
    Text(t, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp, fontFamily = AeFonts.ui)
}

private class TileStyle(val c0: Color, val c1: Color, val border: Color, val tag: String, val sub: String)

/** Mini preview of the sax card: the alto cut-out, centred. */
@Composable
private fun AeSaxMini(modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp = remember { AeSaxBitmaps.get(ctx, 1, 1) }
    Canvas(modifier) {
        bmp?.let {
            val w = min(size.width, size.height * it.width / it.height); val h = w * it.height / it.width
            drawImage(it, dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()), dstSize = IntSize(w.roundToInt(), h.roundToInt()), filterQuality = FilterQuality.High)
        }
    }
}

@Composable
private fun AeTile(inst: AeInstrument, loaded: Boolean, modifier: Modifier, onClick: () -> Unit, onUnload: () -> Unit) {
    val st = when (inst) {
        AeInstrument.ORGAN -> TileStyle(Color(0xFF4B2A17), Color(0xFF1B0E07), Color(0x55D9B36A), "TONEWHEEL", "Drawbars · Rotary · Percussion")
        AeInstrument.SYNTH -> TileStyle(Color(0xFF2B1A5E), Color(0xFF0C0A22), Color(0x668A7AFF), "POLYSYNTH", "Morph oscillators · Filter · Macros")
        AeInstrument.LEADS -> TileStyle(Color(0xFF08283A), Color(0xFF050912), Color(0x5519E3FF), "LED MONO LEAD", "Sine · Square · Saw · Super · Soft")
        AeInstrument.SAX -> TileStyle(Color(0xFF4A2A10), Color(0xFF120905), Color(0x66F2B84A), "BREATH SAXOPHONE", "Soprano · Alto · Tenor · Baritone")
    }
    val sh = RoundedCornerShape(20.dp)
    Box(
        modifier.clip(sh).background(Brush.linearGradient(listOf(st.c0, st.c1), end = androidx.compose.ui.geometry.Offset(300f, 900f)))
            .border(1.dp, st.border, sh).clickable(remember { MutableInteractionSource() }, null, onClick = onClick).padding(12.dp),
    ) {
        // Three bands, so the title and sub-title can never sit on top of the artwork when the
        // card is short: chips on top, artwork in the middle at whatever height is left, text at
        // the bottom. Before, all three were absolutely placed in the box and collided.
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Chip(st.tag); if (loaded) Chip("● LOADED") }
                if (loaded) Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FF5C7A)).clickable(remember { MutableInteractionSource() }, null, onClick = onUnload).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("Unload", color = Color(0xFFFFB3C0), fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, fontFamily = AeFonts.ui)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (inst) {
                    AeInstrument.ORGAN -> Box(Modifier.size(110.dp, 73.dp).aeSvg(AeSvgs.MINI_ORGAN))
                    AeInstrument.SYNTH -> Box(Modifier.size(110.dp, 73.dp).aeSvg(AeSvgs.MINI_SYNTH))
                    AeInstrument.LEADS -> Box(Modifier.size(110.dp, 59.dp).aeSvg(AeSvgs.MINI_LEADS))
                    AeInstrument.SAX -> AeSaxMini(Modifier.size(54.dp, 96.dp))
                }
            }
            Column {
                Text(inst.title, color = Color.White, fontSize = 16.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = AeFonts.ui, maxLines = 1)
                Text(st.sub, color = Color(0xCCFFFFFF), fontSize = 10.sp, lineHeight = 10.sp, fontFamily = AeFonts.ui, maxLines = 1)
            }
        }
    }
}
