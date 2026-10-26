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
    // One loop per open window: 30 fps while playing / dragging / gliding, ~12 fps idle. Cancelled when the window leaves composition.
    LaunchedEffect(s) {
        var last = System.nanoTime()
        while (true) {
            delay(if (s.held.isNotEmpty() || a.lv > 0.01f || s.tweening) 33L else 80L)
            manager.pollLevel(strip)
            val now = System.nanoTime(); a.step(min(0.1f, (now - last) / 1e9f), s); last = now
        }
    }
    AeStage(modifier) {
        val shape = RoundedCornerShape(24.su())
        Box(Modifier.fillMaxSize().clip(shape).border(1.su(), Color(0x22FFFFFF), shape)) {
            if (s.instrument == AeInstrument.ORGAN) AeOrganUi(c, a) else AeSynthUi(c, a)
            if (s.instrument == AeInstrument.SYNTH) AeT("ÆTHER SYNTH", 13, Color.White, Modifier.at(24, 15, 200, 18), AeFonts.display, spacing = 0.18f)
            val presets = manager.presets(s.instrument)
            HeaderBtn("◀", 650, 8, 30, 30) { manager.loadPreset(strip, s.preset - 1) }
            val ns = RoundedCornerShape(10.su())
            Row(Modifier.at(686, 7, 210, 32).clip(ns).background(Color(0x14FFFFFF)).border(1.su(), Color(0x1CFFFFFF), ns).clickable(remember { MutableInteractionSource() }, null) { list = !list }.padding(horizontal = 12.su()),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                AeT(presets[s.preset].name, 12, AeC.tx); AeT(presets[s.preset].category, 10, AeC.mute, weight = FontWeight.SemiBold)
            }
            HeaderBtn("▶", 902, 8, 30, 30) { manager.loadPreset(strip, s.preset + 1) }
            Box(Modifier.at(950, 6, 34, 34).clip(CircleShape).background(if (s.instrument == AeInstrument.ORGAN) Color(0xAA000000) else Color(0x1AFFFFFF))
                .border(1.su(), if (s.instrument == AeInstrument.ORGAN) Color(0x66D9B36A) else Color(0x26FFFFFF), CircleShape)
                .clickable(remember { MutableInteractionSource() }, null) { onClose() }, contentAlignment = Alignment.Center) { AeT("✕", 15, if (s.instrument == AeInstrument.ORGAN) AeC.gold else Color.White) }
            if (list) PresetList(manager, strip, s, onPick = { manager.loadPreset(strip, it); list = false }, onUnload = { list = false; onClose(); manager.unload(strip) })
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
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text("Æther Instruments", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = AeFonts.ui, maxLines = 1)
        Text("Organ · Synth · Leads soon", color = Color(0xD9FFFFFF), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, fontFamily = AeFonts.ui, maxLines = 1)
    }
}

/** Right-hand content of the tab: three cards. Tapping Organ/Synth loads it NOW (audio live) then calls [onOpen]. */
@Composable
fun AeInstrumentsTabContent(manager: AeManager, strip: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(AeInstrument.ORGAN, AeInstrument.SYNTH).forEach { inst ->
            val loaded = manager.sessions[strip]?.instrument == inst
            AeTile(inst, loaded, Modifier.weight(1f).fillMaxHeight(),
                onClick = { if (manager.load(strip, inst) != null) onOpen() }, onUnload = { manager.unload(strip) })
        }
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Color(0x0CFFFFFF)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(20.dp)).padding(14.dp)) {
            Chip("SOON"); Column(Modifier.align(Alignment.BottomStart)) { Text("Æther Leads", color = Color.White.copy(alpha = 0.55f), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, fontFamily = AeFonts.ui); Text("Five lead types, ready-made styles", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, fontFamily = AeFonts.ui) }
        }
    }
}

@Composable
private fun Chip(t: String, modifier: Modifier = Modifier) = Box(modifier.clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF)).padding(horizontal = 8.dp, vertical = 4.dp)) {
    Text(t, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp, fontFamily = AeFonts.ui)
}

@Composable
private fun AeTile(inst: AeInstrument, loaded: Boolean, modifier: Modifier, onClick: () -> Unit, onUnload: () -> Unit) {
    val organ = inst == AeInstrument.ORGAN
    val sh = RoundedCornerShape(20.dp)
    Box(
        modifier.clip(sh)
            .background(Brush.linearGradient(if (organ) listOf(Color(0xFF4B2A17), Color(0xFF1B0E07)) else listOf(Color(0xFF2B1A5E), Color(0xFF0C0A22)), end = androidx.compose.ui.geometry.Offset(300f, 900f)))
            .border(1.dp, if (organ) Color(0x55D9B36A) else Color(0x668A7AFF), sh)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick).padding(14.dp),
    ) {
        Row(Modifier.align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(if (organ) "TONEWHEEL" else "POLYSYNTH")
            if (loaded) Chip("● LOADED")
        }
        // mini preview, centred in the card (not at the edges)
        Box(Modifier.align(Alignment.Center).size(150.dp, 100.dp).aeSvg(if (organ) AeSvgs.MINI_ORGAN else AeSvgs.MINI_SYNTH))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(inst.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, fontFamily = AeFonts.ui)
            Text(if (organ) "Drawbars · Rotary · Percussion" else "Morph oscillators · Filter · Macros", color = Color(0xCCFFFFFF), fontSize = 11.sp, fontFamily = AeFonts.ui)
        }
        if (loaded) Box(Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(50)).background(Color(0x33FF5C7A)).clickable(remember { MutableInteractionSource() }, null, onClick = onUnload).padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text("Unload", color = Color(0xFFFFB3C0), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = AeFonts.ui)
        }
    }
}
