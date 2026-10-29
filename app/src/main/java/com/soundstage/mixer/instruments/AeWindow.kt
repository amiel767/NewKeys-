package com.soundstage.mixer.instruments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.soundstage.mixer.ui.components.FloppyDiskIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
    var naming by remember { mutableStateOf(false) }
    // One loop per open window. While anything is moving it runs at ~60 fps, and when the
    // instrument is idle it *sleeps on the note counter* instead of polling slowly: a slow
    // idle poll made the first key press wait for the next tick, which is what read as
    // "the interface lags behind the note". A note wakes the loop immediately.
    LaunchedEffect(s) {
        var last = System.nanoTime()
        var seenNotes = s.noteEvents.intValue
        while (true) {
            val active = s.held.isNotEmpty() || a.lv > 0.01f || s.tweening
            if (active) {
                delay(16L)
            } else {
                // Idle: wait for a note, a knob tween or a timeout, whichever comes first.
                withTimeoutOrNull(100L) {
                    snapshotFlow { s.noteEvents.intValue to s.tweening }.first { it.first != seenNotes || it.second }
                }
            }
            seenNotes = s.noteEvents.intValue
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
                AeT(presets.getOrNull(s.preset)?.name ?: "", 12, AeC.tx); AeT(presets.getOrNull(s.preset)?.category ?: "", 10, AeC.mute, weight = FontWeight.SemiBold)
            }
            HeaderBtn("▶", 902, 8, 30, 30) { manager.loadPreset(strip, s.preset + 1) }
            // Save-to-disk, left of the preset chip and matching its height, so the two
            // read as one control group. It opens the naming sheet; nothing is written
            // until a name is confirmed.
            SaveDiskBtn(620, 7) { naming = true }
            if (naming) NameSheet(
                initial = "My ${s.instrument.title.removePrefix("Æther ")}",
                onCancel = { naming = false },
                onConfirm = { manager.saveUserPreset(strip, it); naming = false },
            )
            if (list) PresetList(
                m = manager, strip = strip, s = s,
                onPick = { manager.loadPreset(strip, it) },
                onDeleteUser = { manager.deleteUserPreset(s.instrument, it) },
                onUnload = { list = false; onClose(); manager.unload(strip) },
                onDismiss = { list = false },
            )
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

/**
 * Preset browser. Presets are grouped under a header per category and shown as one
 * continuous scrolling list: picking a category used to hide every other preset
 * behind an isolation overlay, which read as "the list is empty". Now every preset
 * stays visible and the category is a header, not a filter.
 *
 * Picking a preset loads it and LEAVES THE LIST OPEN, so the user can audition
 * several in a row. Tapping outside the panel closes it.
 */
@Composable
private fun BoxScope.PresetList(
    m: AeManager,
    strip: Int,
    s: AeSession,
    onPick: (Int) -> Unit,
    onDeleteUser: (Int) -> Unit,
    onUnload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val all = m.presets(s.instrument)
    val base = m.factoryPresets(s.instrument).size
    val sh = RoundedCornerShape(18.su())
    // Scrim: the whole stage, transparent, closing on tap. Sits under the panel so a
    // tap on a preset still reaches it.
    Box(Modifier.at(0, 0, AE_W, AE_H).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
    Box(Modifier.at(16, 48, 968, 396).clip(sh).background(Color(0xF40B0F1A)).border(1.su(), Color(0x22FFFFFF), sh)) {
        Box(Modifier.at(12, 12, 700, 26)) { AeT("PRESETS", 11, AeC.mute, spacing = 0.18f) }
        Box(Modifier.at(800, 12, 156, 26).clip(RoundedCornerShape(50)).background(Color(0x33FF5C7A)).clickable(remember { MutableInteractionSource() }, null, onClick = onUnload), contentAlignment = Alignment.Center) { AeT("Unload instrument", 10, Color(0xFFFFB3C0)) }
        LazyColumn(Modifier.at(12, 46, 944, 336).testTag("aePresetList")) {
            var lastCat = ""
            all.forEachIndexed { i, p ->
                if (p.category != lastCat) {
                    lastCat = p.category
                    item(key = "hdr-$i-${p.category}") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.su(), bottom = 4.su()), verticalAlignment = Alignment.CenterVertically) {
                            AeT(p.category, 10, if (p.category == AeUserStore.USER_CATEGORY) AeC.gold else AeC.mute, spacing = 0.16f)
                            Box(Modifier.padding(start = 8.su()).weight(1f).height(1.su()).background(Color(0x14FFFFFF)))
                        }
                    }
                }
                item(key = "p-$i") {
                    val isUser = i >= base
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 4.su()).clip(RoundedCornerShape(10.su()))
                            .background(if (i == s.preset) Color(0x26FFFFFF) else Color(0x0CFFFFFF))
                            .clickable(remember { MutableInteractionSource() }, null) { onPick(i) }
                            .padding(horizontal = 12.su(), vertical = 8.su()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AeT(p.name, 12, AeC.tx, Modifier.weight(1f))
                        if (i == s.preset) AeT("●", 10, AeC.gold)
                        if (isUser) Box(
                            Modifier.padding(start = 10.su()).clip(RoundedCornerShape(50)).background(Color(0x22FF5C7A))
                                .clickable(remember { MutableInteractionSource() }, null) { onDeleteUser(i) }
                                .padding(horizontal = 10.su(), vertical = 4.su()),
                        ) { AeT("Delete", 9, Color(0xFFFFB3C0)) }
                    }
                }
            }
        }
    }
}

/** Floppy button that saves the current knobs as a user preset. Same chrome as the header buttons. */
@Composable
private fun BoxScope.SaveDiskBtn(x: Number, y: Number, onClick: () -> Unit) {
    val sh = RoundedCornerShape(10.su())
    Box(
        Modifier.at(x, y, 30, 32).clip(sh).background(Color(0x14FFFFFF)).border(1.su(), Color(0x1CFFFFFF), sh)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { FloppyDiskIcon(isArmed = false, modifier = Modifier.size(16.su())) }
}

/**
 * Small naming sheet for a user preset. It is a real [Dialog] so it sits above the
 * stage and takes the keyboard; the confirm button is disabled on a blank name.
 */
@Composable
private fun NameSheet(initial: String, onCancel: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onCancel) {
        val sh = RoundedCornerShape(16.dp)
        Column(
            Modifier.clip(sh).background(Color(0xFF151A26)).border(1.dp, Color(0x22FFFFFF), sh).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Save preset", color = Color(0xFFF1F4FA), fontSize = 14.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, fontFamily = AeFonts.ui)
            Text("Stored under the User category.", color = Color(0xFF8D96AB), fontSize = 11.sp, lineHeight = 11.sp, fontFamily = AeFonts.ui)
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF0B0F1A)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFFF1F4FA), fontSize = 14.sp, fontFamily = AeFonts.ui),
                    cursorBrush = SolidColor(Color(0xFF8FB2E8)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(0x14FFFFFF))
                        .clickable(remember { MutableInteractionSource() }, null, onClick = onCancel).padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Cancel", color = Color(0xFFC9D2E2), fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, fontFamily = AeFonts.ui) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (text.isBlank()) Color(0x3348A0FF) else Color(0xFF3E7BD6))
                        .clickable(enabled = text.isNotBlank()) { onConfirm(text) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Save", color = Color.White, fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, fontFamily = AeFonts.ui) }
            }
        }
    }
}

// ───────── Soundfont window: new left-column item + content ─────────

/** Left-column item under the Presets/All strip. Gradient pill, as in the validated preview. */
@Composable
fun AeInstrumentsTabItem(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sh = RoundedCornerShape(14.dp)
    Box(
        modifier.fillMaxWidth().shadow(if (selected) 14.dp else 8.dp, sh, spotColor = Color(0xFF7A5CFF)).clip(sh)
            .background(Brush.linearGradient(0f to AeC.p1, 0.6f to AeC.p2, 1f to AeC.p3))
            .then(if (selected) Modifier.border(1.5.dp, Color(0xCCFFFFFF), sh) else Modifier)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Æther Instruments", color = Color.White, fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, fontFamily = AeFonts.ui, maxLines = 1)
    }
}

/** Right-hand content of the tab: four cards (2 x 2). Tapping a card loads it NOW (audio live) then calls [onOpen]. */
@Composable
fun AeInstrumentsTabContent(manager: AeManager, strip: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val all = listOf(AeInstrument.ORGAN, AeInstrument.SYNTH, AeInstrument.LEADS, AeInstrument.SAX)
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        all.chunked(2).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { inst ->
                    val loaded = manager.sessions[strip]?.instrument == inst
                    AeTile(
                        inst, loaded, Modifier.weight(1f).fillMaxHeight(),
                        onClick = { if (manager.load(strip, inst) != null) onOpen() },
                        onPower = { if (loaded) manager.unload(strip) else manager.load(strip, inst) },
                    )
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

/** On/off power symbol. Lit green when [on], a dim grey outline when off. */
@Composable
private fun AePowerIcon(on: Boolean, onClick: () -> Unit, size: Dp = 20.dp) {
    val tint = if (on) Color(0xFF35E06A) else Color(0x59FFFFFF)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(50))
            .background(if (on) Color(0x2635E06A) else Color(0x14FFFFFF))
            .border(1.dp, if (on) Color(0x9935E06A) else Color(0x33FFFFFF), RoundedCornerShape(50))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.58f)) {
            val sw = this.size.minDimension * 0.15f
            val r = this.size.minDimension * 0.34f
            val c = Offset(this.size.width / 2f, this.size.height / 2f + sw * 0.6f)
            drawArc(
                color = tint, startAngle = -58f, sweepAngle = 296f, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2f, r * 2f),
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
            drawLine(
                color = tint,
                start = Offset(c.x, c.y - r * 0.95f),
                end = Offset(c.x, c.y - r * 0.35f),
                strokeWidth = sw, cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun AeTile(inst: AeInstrument, loaded: Boolean, modifier: Modifier, onClick: () -> Unit, onPower: () -> Unit) {
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
                Chip(st.tag)
                // Power toggle at the card's top-right: green when the instrument holds the strip,
                // grey when it does not. Tapping loads it (without opening the editor) or unloads
                // it, so the rest of the card stays available for "open".
                AePowerIcon(loaded, onClick = onPower)
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
