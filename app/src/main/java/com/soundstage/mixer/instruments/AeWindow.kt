package com.soundstage.mixer.instruments

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.soundstage.mixer.ui.components.FloppyDiskIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
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
    val ctx = LocalContext.current
    val c = remember(s) { AeCtl(s, manager) }
    val a = remember(s) { AeAnim() }
    var list by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    // The shell paints its own text over whatever the instrument draws behind it. On the
    // piano that backdrop is a photograph, so a fixed near-white ink turns white-on-white
    // on the six light models. Measure the same band the title lives in and follow it.
    val winLuma = remember(s.instrument, s.preset) {
        if (s.instrument != AeInstrument.PIANO) null
        else AePianoModels.all.getOrNull(s.preset)?.let { AePianoAssets.plate(ctx, it.id)?.topLuma }
    }
    val winTh = rememberAeThemeFromLuma(winLuma, AeC.p1)
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
            when (s.instrument) { AeInstrument.ORGAN -> AeOrganUi(c, a); AeInstrument.SYNTH -> AeSynthUi(c, a); AeInstrument.LEADS -> AeLeadsUi(c, a); AeInstrument.SAX -> AeSaxUi(c, a); AeInstrument.PIANO -> AePianoUi(c, manager, strip, onClose) }
            when (s.instrument) {
                AeInstrument.SYNTH -> AeT("ÆTHER SYNTH", 13, Color.White, Modifier.at(58, 15, 200, 18), AeFonts.display, spacing = 0.18f)
                AeInstrument.LEADS -> AeT("ÆTHER LEADS", 13, Color(0xFF6FE9FF), Modifier.at(58, 15, 200, 18), AeFonts.display, spacing = 0.18f)
                AeInstrument.SAX -> AeT("ÆTHER SAX", 13, AeC.gold, Modifier.at(58, 15, 200, 18), AeFonts.plate, FontWeight.ExtraBold, 0.2f)
                else -> Unit
            }
            val presets = manager.presets(s.instrument)
            BackArrow(winTh, 10, 9, 30) { onClose() }
            // Header geometry, left to right, with no overlaps left:
            //   back 10..40 | title from 58 | tabs 264..532 | disk 552..582 | prev 596..626 |
            //   preset chip 640..866 | next 880..910.
            // The tabs used to start at 354 (Leads) or 318 (Synth) and the Synth row ran to 682,
            // under the disk at 620 — the crowding the user reported. The preset chip used to
            // start at 686, i.e. 36 units from the disk's right edge at 650; it now starts 58
            // units after the disk, and 14 after the prev button.
            HeaderBtn(winTh, "◀", 596, 8, 30, 30) { manager.loadPreset(strip, s.preset - 1) }
            val ns = RoundedCornerShape(10.su())
            Row(Modifier.at(640, 7, 226, 32).clip(ns).background(winTh.glass).border(1.su(), winTh.edge, ns).clickable(remember { MutableInteractionSource() }, null) { list = !list }.padding(horizontal = 12.su()),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                AeT(presets.getOrNull(s.preset)?.name ?: "", 12, winTh.ink); AeT(presets.getOrNull(s.preset)?.category ?: "", 10, winTh.ink2, weight = FontWeight.SemiBold)
            }
            HeaderBtn(winTh, "▶", 880, 8, 30, 30) { manager.loadPreset(strip, s.preset + 1) }
            SaveDiskBtn(winTh, 552, 7) { naming = true }
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
private fun BoxScope.BackArrow(th: AeTheme, x: Number, y: Number, size: Number, onClick: () -> Unit) {
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
            drawPath(p, th.ink, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

@Composable
private fun BoxScope.HeaderBtn(th: AeTheme, t: String, x: Number, y: Number, w: Number, h: Number, onClick: () -> Unit) {
    Box(Modifier.at(x, y, w, h).clip(RoundedCornerShape(9.su())).background(th.glass).clickable(remember { MutableInteractionSource() }, null, onClick = onClick), contentAlignment = Alignment.Center) { AeT(t, 12, th.ink) }
}

/**
 * Preset browser: a three-pane library, the layout professional instrument plug-ins use.
 *
 * The shape follows Arturia Analog Lab / Serum's browser rather than a two-column index:
 *
 *  - a *category rail* on the left lists the categories with their counts, so the whole
 *    library is visible as a map and a category is one tap away instead of a long scroll;
 *  - the *list* is the filtered result, one preset per row, numbered, so position in the
 *    filtered set is legible;
 *  - the *selection* is a highlighted row with a left accent bar and the category shown in
 *    the row, so a filtered list still says where each preset comes from;
 *  - a *count line* under the header states what the filter currently holds ("12 of 150"),
 *    which is what makes a filter feel like a filter and not a missing list.
 *
 * The two earlier versions were a single stack of full-width bars (about eight visible) and a
 * two-column index (~30 visible but with no way to reach a category directly). Both are gone:
 * this one shows the categories and the presets at the same time.
 *
 * Picking a preset loads it and LEAVES THE LIST OPEN, so several can be auditioned in a row.
 * Tapping outside the panel closes it.
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
    val pal = AePals.of(s.instrument)
    val sh = RoundedCornerShape(18.su())
    // null = "All". Stored as the category *name* rather than an index so it survives the list
    // being rebuilt when a user preset is saved or deleted.
    var cat by remember(s.instrument) { mutableStateOf<String?>(null) }
    val cats = all.groupBy { it.category }.toList()
    val shown = remember(all, cat) {
        all.withIndex().filter { cat == null || it.value.category == cat }
    }
    Box(Modifier.at(0, 0, AE_W, AE_H).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
    Box(
        Modifier.at(16, 48, 968, 396).clip(sh)
            .background(Brush.verticalGradient(listOf(Color(0xFF151B29), Color(0xFF0A0E17))))
            .drawWithContent {
                drawContent()
                drawLine(Color(0x26FFFFFF), Offset(14f, 1f), Offset(size.width - 14f, 1f), 1f)
                drawLine(Color(0x66000000), Offset(14f, size.height - 1f), Offset(size.width - 14f, size.height - 1f), 1f)
            },
    ) {
        Row(Modifier.at(18, 13, 620, 18), verticalAlignment = Alignment.CenterVertically) {
            AeT("PRESETS", 11, AeC.mute, spacing = 0.18f)
            AeT(s.instrument.title, 10, pal.chipOn1, Modifier.padding(start = 10.su()), spacing = 0.1f)
            AeT("${shown.size} of ${all.size}", 10, AeC.mute, Modifier.padding(start = 10.su()), font = AeFonts.mono)
        }
        Box(
            Modifier.at(786, 11, 166, 24).clip(RoundedCornerShape(50)).background(Color(0x1AFF5C7A))
                .border(1.su(), Color(0x33FF5C7A), RoundedCornerShape(50))
                .noRipple(onUnload),
            contentAlignment = Alignment.Center,
        ) { AeT("Unload instrument", 10, Color(0xFFFFB3C0)) }

        // ---- left rail: categories ----
        val railW = 214
        Box(Modifier.at(16, 42, railW, 340).background(Color(0x14000000)))
        AeT("CATEGORY", 8, AeC.mute, Modifier.at(30, 50, 180, 10), spacing = 0.18f, weight = FontWeight.ExtraBold)
        val rail = rememberLazyListState()
        LazyColumn(
            state = rail,
            modifier = Modifier.at(24, 64, railW - 16, 314),
            verticalArrangement = Arrangement.spacedBy(1.su()),
        ) {
            item(key = "cat-all") { CatRow("All", all.size, cat == null, pal.chipOn1, true) { cat = null } }
            items(cats.size, key = { "cat-$it" }) { i ->
                val (name, list) = cats[i]
                val user = name == AeUserStore.USER_CATEGORY
                CatRow(name, list.size, cat == name, if (user) AeC.gold else pal.chipOn1, false) { cat = name }
            }
        }

        // ---- right: the filtered presets ----
        val grid = rememberLazyListState()
        val listX = 16 + railW + 8
        val listW = 936 - railW - 8
        LazyColumn(
            state = grid,
            modifier = Modifier.at(listX, 50, listW, 332).testTag("aePresetList"),
            verticalArrangement = Arrangement.spacedBy(1.su()),
        ) {
            itemsIndexed(shown, key = { _, e -> "p-${e.index}" }) { n, e ->
                val i = e.index
                val p = e.value
                val sel = i == s.preset
                Row(
                    Modifier.fillMaxWidth().height(28.su()).clip(RoundedCornerShape(7.su()))
                        .background(
                            when {
                                sel -> Brush.horizontalGradient(listOf(pal.chipOn1.copy(alpha = 0.22f), Color(0x0819E3FF)))
                                n % 2 == 0 -> Brush.horizontalGradient(listOf(Color(0x07FFFFFF), Color(0x02FFFFFF)))
                                else -> Brush.horizontalGradient(listOf(Color(0x00000000), Color(0x00000000)))
                            }
                        )
                        .noRipple { onPick(i) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.padding(start = 4.su()).width(3.su()).height(15.su())
                            .clip(RoundedCornerShape(2.su()))
                            .background(if (sel) pal.chipOn1 else Color.Transparent),
                    )
                    AeT("${n + 1}", 9, if (sel) pal.chipOn1 else AeC.mute, Modifier.padding(start = 8.su(), end = 2.su()).width(20.su()), font = AeFonts.mono, align = TextAlign.End)
                    AeT(p.name, 11, if (sel) Color.White else AeC.tx, Modifier.padding(start = 8.su()).weight(1f))
                    if (cat == null) AeT(p.category, 9, AeC.mute, Modifier.padding(end = 6.su()), spacing = 0.04f)
                    if (i >= base) Box(
                        Modifier.padding(end = 6.su()).size(16.su()).clip(CircleShape).background(Color(0x1AFF5C7A))
                            .clickable(remember { MutableInteractionSource() }, null) { onDeleteUser(i) },
                        contentAlignment = Alignment.Center,
                    ) { AeT("✕", 9, Color(0xFFFFB3C0)) }
                }
            }
        }
        // Scroll thumb for the preset list only; the category rail is short enough to read whole.
        val info = grid.layoutInfo
        val total = info.totalItemsCount
        if (total > 0 && info.visibleItemsInfo.size < total) {
            val frac = info.visibleItemsInfo.size.toFloat() / total
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
            val track = 332f
            val thumbH = (track * frac).coerceAtLeast(26f)
            val y = 50f + (track - thumbH) * (first.toFloat() / total)
            Box(Modifier.at(958, y, 3, thumbH).clip(RoundedCornerShape(2.su())).background(Color(0x2EFFFFFF)))
        }
    }
}

/** One row of the category rail: name, count, and the selected state as a filled pill. */
@Composable
private fun CatRow(name: String, count: Int, sel: Boolean, accent: Color, isAll: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(26.su()).clip(RoundedCornerShape(7.su()))
            .background(if (sel) accent.copy(alpha = 0.18f) else Color.Transparent)
            .noRipple(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.padding(start = 3.su()).width(2.5f.su()).height(14.su())
                .clip(RoundedCornerShape(2.su()))
                .background(if (sel) accent else Color.Transparent),
        )
        AeT(name.uppercase(), if (isAll) 9 else 8.5f, if (sel) Color.White else AeC.mute,
            Modifier.padding(start = 8.su()).weight(1f), spacing = 0.12f,
            weight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold)
        AeT("$count", 9, if (sel) accent else AeC.mute, Modifier.padding(end = 9.su()), font = AeFonts.mono)
    }
}

/** Floppy button that saves the current knobs as a user preset. Same chrome as the header buttons. */
@Composable
private fun BoxScope.SaveDiskBtn(th: AeTheme, x: Number, y: Number, onClick: () -> Unit) {
    val sh = RoundedCornerShape(10.su())
    Box(
        Modifier.at(x, y, 30, 32).clip(sh).background(th.glass).border(1.su(), th.edge, sh)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { FloppyDiskIcon(isArmed = false, tint = th.ink, modifier = Modifier.size(16.su())) }
}

/**
 * Naming sheet for a user preset.
 *
 * It used to be a [Dialog] holding a plain Column: a Dialog sizes itself to its content, so
 * the sheet stretched to the full width of whatever the window happened to be, and the field
 * was a flat rectangle on a flat panel. Three things fix that:
 *
 *  - a bounded width, so the sheet is a small card wherever it is shown, not a full-width band;
 *  - a material, not a flat fill: a brushed vertical grain over the panel, a lit top edge and a
 *    dark bottom edge, which is what the rest of the app's chrome reads as;
 *  - the field is a recessed well with a caret, and the confirm button carries the same relief
 *    as the rest of the controls instead of being a flat blue block.
 */
@Composable
private fun NameSheet(initial: String, onCancel: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Focus the field as soon as the sheet opens, so the keyboard is already up and the user
    // can type the name straight away instead of having to tap the field first.
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    Dialog(onDismissRequest = onCancel) {
        val sh = RoundedCornerShape(18.dp)
        Column(
            Modifier
                .width(340.dp)
                .clip(sh)
                .background(Color(0xFF141926))
                .drawWithContent {
                    drawContent()
                    // Brushed grain: thin vertical strokes of alternating light and dark. Kept
                    // deterministic so the card does not shimmer when it recomposes.
                    val r = Random(11)
                    var x = 0f
                    while (x < size.width) {
                        val w = 1f + r.nextFloat() * 2f
                        drawRect(
                            color = if (r.nextBoolean()) Color(0x0AFFFFFF) else Color(0x08000000),
                            topLeft = Offset(x, 0f),
                            size = Size(w, size.height),
                        )
                        x += w + 2f + r.nextFloat() * 3f
                    }
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0x1AFFFFFF), Color(0x00FFFFFF), Color(0x2E000000)),
                        ),
                    )
                }
                .drawWithContent {
                    drawContent()
                    // Lit top edge, dark bottom edge: the card is a raised plate.
                    drawLine(Color(0x33FFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 1.4f)
                    drawLine(Color(0x66000000), Offset(0f, size.height), Offset(size.width, size.height), 1.6f)
                }
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Save preset", color = Color(0xFFF1F4FA), fontSize = 14.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, fontFamily = AeFonts.ui)
            Text("Stored under the User category.", color = Color(0xFF8D96AB), fontSize = 11.sp, lineHeight = 11.sp, fontFamily = AeFonts.ui)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF070A11), Color(0xFF0D1220))))
                    .drawWithContent {
                        drawContent()
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(Color(0x73000000), Color(0x00000000)), endY = size.height * 0.8f),
                            cornerRadius = CornerRadius(10f * density),
                        )
                        drawRoundRect(
                            color = Color(0x14FFFFFF),
                            style = Stroke(width = 1f * density),
                            cornerRadius = CornerRadius(10f * density),
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFFF1F4FA), fontSize = 14.sp, fontFamily = AeFonts.ui),
                    cursorBrush = SolidColor(Color(0xFF8FB2E8)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onConfirm(text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x0AFFFFFF))))
                        .clickable(remember { MutableInteractionSource() }, null, onClick = onCancel).padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Cancel", color = Color(0xFFC9D2E2), fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, fontFamily = AeFonts.ui) }
                val armed = text.isNotBlank()
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(
                            if (armed) Brush.verticalGradient(listOf(Color(0xFF4E8FE4), Color(0xFF2F63B4)))
                            else Brush.verticalGradient(listOf(Color(0x3348A0FF), Color(0x2248A0FF))),
                        )
                        .drawWithContent {
                            drawContent()
                            if (armed) drawLine(Color(0x4DFFFFFF), Offset(2f, 0f), Offset(size.width - 2f, 0f), 1.2f)
                        }
                        .clickable(enabled = armed) { onConfirm(text) }.padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Save", color = if (armed) Color.White else Color(0x88FFFFFF), fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, fontFamily = AeFonts.ui) }
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
    val all = listOf(AeInstrument.ORGAN, AeInstrument.SYNTH, AeInstrument.LEADS, AeInstrument.SAX, AeInstrument.PIANO)
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

/**
 * On/off power symbol. Lit green when [on], a dim grey when off.
 *
 * Two things this must get right, and the first version got both wrong:
 *
 *  - CENTRING. The glyph is an arc with a gap at the top plus a stem through that gap. Its
 *    visual centre is not the centre of the arc: the stem sits above the arc's centre and the
 *    gap removes ink at the top, so centring the arc in the box pushes the whole mark down.
 *    The arc is therefore placed on a circle whose centre is nudged down by the stem's own
 *    overhang, and the stem is drawn from the top of that circle, which is what makes the mark
 *    read as centred.
 *  - RELIEF. No hard outline. The button is a recessed well with a soft inner shadow, the glyph
 *    is a light-on-dark emboss (a dark copy offset down, the lit copy on top), and the press
 *    animates with a spring so it answers the finger.
 */
@Composable
private fun AePowerIcon(on: Boolean, onClick: () -> Unit, size: Dp = 22.dp) {
    val lit = if (on) Color(0xFF35E06A) else Color(0xFF8A93A6)
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shade by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 520f), label = "powerShade")
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0x1F000000), Color(0x05000000))))
            .drawWithContent {
                drawContent()
                // Soft inner shadow at the top: the well, without a hard ring.
                drawCircle(
                    brush = Brush.verticalGradient(
                        listOf(Color(0x73000000), Color(0x00000000)),
                        endY = this.size.height * 0.85f,
                    ),
                    radius = this.size.minDimension / 2f,
                )
                if (on) drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0x3335E06A), Color(0x00000000)),
                        center = Offset(this.size.width / 2f, this.size.height / 2f),
                        radius = this.size.minDimension * 0.62f,
                    ),
                    radius = this.size.minDimension * 0.62f,
                )
                // Light bevel on the lower rim, shadow on the upper: the face is lit from above.
                drawArc(
                    color = Color(0x1AFFFFFF),
                    startAngle = 20f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(0.75f, 0.75f),
                    size = Size(this.size.width - 1.5f, this.size.height - 1.5f),
                    style = Stroke(width = 1.1f * density),
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    scope.launch { press.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 1400f)) }
                    tryAwaitRelease()
                    scope.launch { press.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 620f)) }
                    onClick()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.62f)) {
            val s = this.size.minDimension
            val sw = s * 0.145f
            val r = s * 0.335f
            val cx = this.size.width / 2f
            // The arc's centre sits below the box centre by half the stem's overhang, and the
            // stem runs from the top of the arc circle up to the box top. That is the geometry
            // that makes the mark look centred instead of low.
            val cy = this.size.height / 2f + s * 0.055f
            val glyph = lit
            // Emboss: a dark copy offset one step down, then the lit copy. Relief, no outline.
            drawArc(
                color = Color(0x66000000), startAngle = -58f, sweepAngle = 296f, useCenter = false,
                topLeft = Offset(cx - r, cy - r + sw * 0.45f), size = Size(r * 2f, r * 2f),
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
            drawLine(
                color = Color(0x66000000),
                start = Offset(cx, cy - r * 0.98f + sw * 0.45f),
                end = Offset(cx, cy - r * 0.30f + sw * 0.45f),
                strokeWidth = sw, cap = StrokeCap.Round,
            )
            drawArc(
                color = glyph, startAngle = -58f, sweepAngle = 296f, useCenter = false,
                topLeft = Offset(cx - r, cy - r), size = Size(r * 2f, r * 2f),
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
            drawLine(
                color = glyph,
                start = Offset(cx, cy - r * 0.98f),
                end = Offset(cx, cy - r * 0.30f),
                strokeWidth = sw, cap = StrokeCap.Round,
            )
            if (shade > 0.01f) drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0x55FFFFFF), Color(0x00000000)),
                    center = Offset(cx, cy - r * 0.9f),
                    radius = r * 1.6f,
                ),
                radius = r * 1.6f,
                alpha = shade * 0.5f,
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
        AeInstrument.PIANO -> TileStyle(Color(0xFF2A1C10), Color(0xFF0C0704), Color(0x66E3C48A), "HYBRID GRAND", "Felt · Studio · Upright · Electric")
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
                    AeInstrument.PIANO -> AePianoCardPreview(Modifier.size(110.dp, 73.dp))
                }
            }
            Column {
                Text(inst.title, color = Color.White, fontSize = 16.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = AeFonts.ui, maxLines = 1)
                Text(st.sub, color = Color(0xCCFFFFFF), fontSize = 10.sp, lineHeight = 10.sp, fontFamily = AeFonts.ui, maxLines = 1)
            }
        }
    }
}
