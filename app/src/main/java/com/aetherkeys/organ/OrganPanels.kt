@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.aetherkeys.organ

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soundstage.mixer.R
import kotlin.math.roundToInt

private val C = OrganColors
private val Bounce = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

internal data class PanelRect(val x: Float, val y: Float, val w: Float, val h: Float)

/* ------------------------- Panneau générique ------------------------- */
@Composable
internal fun OrganPanel(
    rect: PanelRect, overflowVisible: Boolean = false, zIndex: Float = 0f,
    header: @Composable BoxScope.() -> Unit, content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier.offset(rect.x.dp, rect.y.dp).size(rect.w.dp, rect.h.dp).zIndex(zIndex).cssBox(
            radius = 10f, fill = vGrad(C.Panel, C.PanelDark), border = C.Border,
            shadows = listOf(inset(0f, 1f, 0f, white(0.04f)), shadow(0f, 4f, 10f, black(0.4f))),
        ),
    ) {
        val inner = Modifier.fillMaxSize().padding(1.dp).let { if (overflowVisible) it else it.clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp)) }
        Box(inner) {
            Box(Modifier.padding(4.dp).fillMaxSize().let { if (overflowVisible) it else it.clipToBounds() }) { content() }
            Box(Modifier.padding(start = 6.dp, top = 4.dp, end = 6.dp)) { header() }
        }
    }
}

@Composable
internal fun PanelTitle(text: String) {
    Text(text.uppercase(), style = otext(8f, FontWeight.ExtraBold, C.TextSub, ls = 1f, lh = 9.7f), modifier = Modifier.graphicsLayer { alpha = 0.7f })
}

/** Interrupteur maître ON/OFF : couleurs et libellé interpolés, donc l'état change en fondu
 * au lieu de sauter. Les deux libellés sont superposés pour que la largeur ne bouge pas. */
@Composable
private fun MasterToggle(active: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(if (active) Color(0x1A8FB2E8) else C.Dark1, tween(180), label = "mtf")
    val border by animateColorAsState(if (active) C.Accent else white(0.1f), tween(180), label = "mtb")
    val dot by animateColorAsState(if (active) C.Accent else C.Dark2, tween(180), label = "mtd")
    val ink by animateColorAsState(if (active) C.TextMain else C.TextSub, tween(180), label = "mtt")
    val onA by animateFloatAsState(if (active) 1f else 0f, tween(160), label = "mta")
    PressBox(
        Modifier.cssBox(
            radius = 4f, fill = solid(fill), border = border,
            shadows = if (active) listOf(shadow(0f, 0f, 4f, C.Accent)) else emptyList(),
        ), pressShift = 0f, onClick = onClick,
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 1.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(3.dp).cssBox(radius = 1.5f, fill = solid(dot)))
            Box(contentAlignment = Alignment.Center) {
                Text("OFF", Modifier.graphicsLayer { alpha = 1f - onA }, style = otext(7f, FontWeight.ExtraBold, ink, lh = 8.5f), maxLines = 1, softWrap = false)
                Text("ON", Modifier.graphicsLayer { alpha = onA }, style = otext(7f, FontWeight.ExtraBold, ink, lh = 8.5f), maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun ToggleBtn(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val top by animateColorAsState(if (active) C.BtnTop else C.Dark2, tween(180), label = "tbt")
    val bot by animateColorAsState(if (active) C.BtnBottom else C.Dark1, tween(180), label = "tbb")
    val ink by animateColorAsState(if (active) C.TextMain else C.TextSub, tween(180), label = "tbi")
    PressBox(modifier, 1f, onClick) { pressed ->
        Box(
            Modifier.fillMaxSize().cssBox(
                radius = 4f, fill = vGrad(top, bot), border = white(0.08f),
                shadows = when {
                    pressed && !active -> listOf(inset(0f, 2f, 4f, black(0.6f)))
                    active -> listOf(inset(0f, 1f, 0f, white(0.1f)), shadow(0f, 1f, 2f, black(0.3f)))
                    else -> listOf(inset(0f, 1f, 2f, white(0.05f)), shadow(0f, 1f, 3f, black(0.4f)))
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                LedDot(4f, active)
                Text(label, style = otext(8f, FontWeight.ExtraBold, ink, lh = 9.7f), maxLines = 1, softWrap = false)
            }
        }
    }
}

/* ============================ OCT ============================ */
@Composable
internal fun OctPanel(rect: PanelRect, st: OrganState) = OrganPanel(rect, header = { PanelTitle("Oct +/-") }) {
    Row(
        Modifier.fillMaxSize().padding(start = 6.dp, top = 14.dp, end = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        OctBtn("−") { st.changeOct(-1) }
        Box(
            Modifier.widthIn(min = 48.dp).height(34.dp).cssBox(
                radius = 5f, fill = vGrad(Color(0xFF050A14), Color(0xFF020610)), border = Color(0x148FB2E8),
                shadows = listOf(inset(0f, 2f, 5f, black(0.9f)), shadow(0f, 1f, 0f, white(0.05f))),
            ).scanlines(3f, 2f, 0.4f, 5f),
            contentAlignment = Alignment.Center,
        ) {
            GlowText(
                if (st.octave > 0) "+${st.octave}" else "${st.octave}",
                otext(17f, FontWeight.Bold, C.AccentSoft, OrganFonts.PlexMono, ls = 1f, lh = 18f),
                listOf(AccentGlow70 to 8f, AccentSoftGlow90 to 2f),
            )
        }
        OctBtn("+") { st.changeOct(1) }
    }
}

@Composable
private fun OctBtn(label: String, onClick: () -> Unit) = PressBox(Modifier.size(36.dp, 34.dp), 2f, onClick) { pressed ->
    Box(
        Modifier.fillMaxSize().cssBox(
            radius = 5f, fill = vGrad(C.BtnTop, C.BtnBottom), border = white(0.12f),
            shadows = if (pressed) listOf(inset(0f, 2f, 4f, black(0.7f))) else listOf(inset(0f, 1f, 0f, white(0.08f)), shadow(0f, 2f, 4f, black(0.4f))),
        ),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = otext(18f, FontWeight.Black, C.TextMain, lh = 18f)) }
}

/* ============================ VIBRATO / PERC ============================ */
@Composable
internal fun VibratoPanel(rect: PanelRect, st: OrganState) = OrganPanel(
    rect,
    header = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                PanelTitle("Vibrato"); MasterToggle(st.vibMasterOn) { st.toggleVibMaster() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                PanelTitle("B3 Perc"); MasterToggle(st.percMasterOn) { st.togglePercMaster() }
            }
        }
    },
) {
    Row(Modifier.fillMaxSize().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().groupDisabled(st.vibGroupDisabled), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val labels = listOf("V1", "C1", "V2", "C2", "V3", "C3")
            for (row in 0..2) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (col in 0..1) { val i = row * 2 + col; ToggleBtn(labels[i], st.vibActive == i, Modifier.weight(1f).fillMaxHeight()) { st.selectVibrato(i) } }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().groupDisabled(st.percGroupDisabled), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ToggleBtn("Soft", st.percSoft, Modifier.weight(1f).fillMaxWidth()) { st.togglePerc(0) }
            ToggleBtn("Fast", st.percFast, Modifier.weight(1f).fillMaxWidth()) { st.togglePerc(1) }
            ToggleBtn("Third", st.percThird, Modifier.weight(1f).fillMaxWidth()) { st.togglePerc(2) }
        }
    }
}

/* ============================ ROTARY ============================ */
@Composable
internal fun RotaryPanel(rect: PanelRect, st: OrganState) = OrganPanel(
    rect,
    header = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PanelTitle("Rotary")
            Spacer(Modifier.weight(1f))
            CabinetToggle(st.cabinetOn) { st.toggleCabinet() }
        }
    },
) {
    Box(Modifier.fillMaxSize().padding(start = 6.dp, top = 16.dp, end = 6.dp, bottom = 4.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                    KnobItem("Rise", st.rise, "${"%.1f".format(st.rise / 20f)}s") { st.setKnob(OrganKnobId.RotaryRise, it) }
                    KnobItem("Fall", st.fall, "${"%.1f".format(st.fall / 20f)}s") { st.setKnob(OrganKnobId.RotaryFall, it) }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SpeedButton(st.speedFast) { st.toggleSpeed() }
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.weight(1f).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        OrganVFader(st.depth, { st.setFader(OrganFaderId.RotaryDepth, it) })
                        OrganVFader(st.mic, { st.setFader(OrganFaderId.RotaryMic, it) })
                    }
                    Row(Modifier.padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf("Depth", "Mic").forEach {
                            Text(it.uppercase(), Modifier.width(18.dp), style = otext(6f, FontWeight.ExtraBold, C.TextSub, ls = 0.3f, lh = 7.2f), textAlign = TextAlign.Center, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth().height(16.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("HORN / DRUM", style = otext(6f, FontWeight.ExtraBold, C.TextSub, ls = 0.4f, lh = 7.2f), maxLines = 1, softWrap = false)
                OrganHFader(st.balance, { st.setFader(OrganFaderId.RotaryHornDrum, it) }, Modifier.weight(1f))
            }
        }
    }
}

/** Cabine ON/OFF : dans l'en-tête, collé à l'extrême droite haute du panneau, états en fondu. */
@Composable
private fun CabinetToggle(on: Boolean, onClick: () -> Unit) {
    val border by animateColorAsState(if (on) Color(0x80EF4444) else white(0.1f), tween(180), label = "cbb")
    val ink by animateColorAsState(if (on) C.Red else C.TextSub, tween(180), label = "cbi")
    val a by animateFloatAsState(if (on) 1f else 0f, tween(160), label = "cba")
    PressBox(Modifier.height(12.dp), 0f, onClick) {
        Box(
            Modifier.fillMaxHeight().cssBox(radius = 3f, fill = solid(C.Dark1), border = border).padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            val s = otext(6f, FontWeight.ExtraBold, ink, ls = 0.4f, lh = 7.2f)
            Box(contentAlignment = Alignment.Center) {
                Text("OFF", Modifier.graphicsLayer { alpha = 1f - a }, style = s, maxLines = 1, softWrap = false)
                GlowText("ON", s, listOf(Color(0x99EF4444).copy(alpha = a) to 3f), Modifier.graphicsLayer { alpha = a })
            }
        }
    }
}

@Composable
private fun KnobItem(label: String, v: Float, valueText: String, onChange: (Float) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OrganKnob(52, v, onChange)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label.uppercase(), style = otext(8f, FontWeight.ExtraBold, C.TextSub, ls = 0.4f, lh = 8f), maxLines = 1, softWrap = false)
            Text(valueText, style = otext(9f, FontWeight.Bold, C.Accent, OrganFonts.PlexMono, lh = 9f), maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun SpeedButton(fast: Boolean, onClick: () -> Unit) {
    val top by animateColorAsState(if (fast) C.Dark2 else C.BtnTop, tween(180), label = "sbt")
    val bot by animateColorAsState(if (fast) C.Dark1 else C.BtnBottom, tween(180), label = "sbb")
    val ink by animateColorAsState(if (fast) C.TextMain else C.TextSub, tween(180), label = "sbi")
    val a by animateFloatAsState(if (fast) 1f else 0f, tween(160), label = "sba")
    PressBox(Modifier.size(74.dp, 22.dp), 1f, onClick) {
        Box(
            Modifier.fillMaxSize().cssBox(
                radius = 4f,
                fill = vGrad(top, bot),
                border = if (fast) white(0.1f) else white(0.15f),
                shadows = if (fast) listOf(shadow(0f, 1f, 2f, black(0.5f)), inset(0f, 2f, 4f, black(0.7f)))
                else listOf(shadow(0f, 2f, 3f, black(0.4f)), inset(0f, 1f, 0f, white(0.08f))),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                LedDot(5f, fast, C.LedGreen, Color(0xFF111111), 4f)
                Box(contentAlignment = Alignment.Center) {
                    Text("SLOW", Modifier.graphicsLayer { alpha = 1f - a }, style = otext(8f, FontWeight.ExtraBold, ink, ls = 0.6f, lh = 9.7f), maxLines = 1, softWrap = false)
                    Text("FAST", Modifier.graphicsLayer { alpha = a }, style = otext(8f, FontWeight.ExtraBold, ink, ls = 0.6f, lh = 9.7f), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/* ============================ PRESETS ============================ */
@Composable
internal fun PresetsPanel(rect: PanelRect, st: OrganState) = OrganPanel(rect, overflowVisible = true, zIndex = 5f, header = { PanelTitle("Preset") }) {
    val list = st.currentList
    val idx = st.currentIndex.coerceIn(0, list.size - 1)
    val flash = remember { Animatable(0f) }
    LaunchedEffect(st.flashTick) {
        if (st.flashTick > 0) { flash.snapTo(0f); flash.animateTo(1f, tween(275)); flash.animateTo(0f, tween(275)) }
    }
    Box(Modifier.fillMaxSize().padding(start = 4.dp, top = 22.dp, end = 4.dp, bottom = 4.dp)) {
        Box(Modifier.fillMaxWidth().height(34.dp).align(Alignment.Center)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                NavBtn("◀") { st.prevPreset() }
                PresetDisplay(st, list[idx].name, idx, list.size, flash.value, Modifier.weight(1f).fillMaxHeight())
                NavBtn("▶") { st.nextPreset() }
            }
            Dropdown(st, list, idx)
        }
    }
    // barre du haut : Save + FACTORY/USER
    Row(Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 1.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        val canSave = st.presetUserMode
        PressBox(Modifier.height(18.dp).graphicsLayer { alpha = if (canSave) 1f else 0.3f }, 1f, { if (canSave) st.savePreset() }) { pressed ->
            Box(
                Modifier.fillMaxHeight().cssBox(
                    radius = 3f, fill = vGrad(C.BtnTop, C.BtnBottom), border = white(0.15f),
                    shadows = if (pressed && canSave) listOf(inset(0f, 2f, 3f, black(0.6f))) else listOf(shadow(0f, 1f, 2f, black(0.4f))),
                ).padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val s = otext(7f, FontWeight.Bold, if (canSave) C.AccentSoft else C.TextSub, OrganFonts.PlexMono, ls = 0.6f, lh = 7f)
                if (canSave) GlowText("SAVE", s, listOf(Color(0x668FB2E8) to 4f)) else Text("SAVE", style = s, maxLines = 1, softWrap = false)
            }
        }
        Row(Modifier.cssBox(radius = 10f, fill = solid(Color(0xFF0B0E14)), border = white(0.05f), shadows = listOf(inset(0f, 1f, 3f, black(0.6f)))).padding(2.5.dp)) {
            ModeOption("FACTORY", !st.presetUserMode) { st.switchMode(false) }
            ModeOption("USER", st.presetUserMode) { st.switchMode(true) }
        }
    }
}

@Composable
private fun ModeOption(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.pointerInput(Unit) { detectTapGestures { onClick() } }.then(
            if (active) Modifier.cssBox(radius = 8f, fill = vGrad(C.BtnTop, C.BtnBottom), shadows = listOf(shadow(0f, 1f, 2f, black(0.5f)))) else Modifier,
        ).padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        val s = otext(7.5f, FontWeight.Bold, if (active) C.AccentSoft else C.TextSub, OrganFonts.PlexMono, ls = 0.6f, lh = 7.5f)
        if (active) GlowText(label, s, listOf(Color(0x808FB2E8) to 4f)) else Text(label, style = s, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun NavBtn(label: String, onClick: () -> Unit) = PressBox(Modifier.width(18.dp).fillMaxHeight(), 1f, onClick) { pressed ->
    Box(
        Modifier.fillMaxSize().cssBox(
            radius = 4f, fill = vGrad(C.BtnTop, C.BtnBottom), border = white(0.12f),
            shadows = if (pressed) listOf(inset(0f, 2f, 4f, black(0.7f))) else listOf(inset(0f, 1f, 0f, white(0.08f)), shadow(0f, 2f, 4f, black(0.4f))),
        ),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = otext(9f, FontWeight.Normal, C.TextSub, lh = 9f), maxLines = 1, softWrap = false) }
}

private val UpperTransform = VisualTransformation { AnnotatedString(it.text.uppercase()).let { a -> TransformedText(a, OffsetMapping.Identity) } }

@Composable
private fun PresetDisplay(st: OrganState, name: String, idx: Int, total: Int, flash: Float, modifier: Modifier) {
    val chevron by animateFloatAsState(if (st.dropdownOpen) 180f else 0f, tween(300, easing = Bounce), label = "chev")
    val nameStyle = otext(10f, FontWeight.Bold, C.AccentSoft, OrganFonts.PlexMono, ls = 0.6f, lh = 11f)
    Box(
        modifier
            .cssBox(
                radius = 4f, fill = vGrad(Color(0xFF030812), Color(0xFF010306)), border = Color(0x1A8FB2E8),
                shadows = listOfNotNull(
                    if (flash > 0f) shadow(0f, 0f, 20f, Color(0x998FB2E8).copy(alpha = 0.6f * flash)) else null,
                    if (flash > 0f) inset(0f, 0f, 12f, Color(0x4D8FB2E8).copy(alpha = 0.3f * flash)) else null,
                    inset(0f, 2f, 6f, black(0.9f)), shadow(0f, 1f, 0f, white(0.04f)),
                ),
            )
            .scanlines(2f, 1.5f, 0.35f, 4f)
            .pointerInput(st.presetUserMode, st.editingName) {
                detectTapGestures(
                    onTap = { if (!st.editingName) st.dropdownOpen = !st.dropdownOpen },
                    onDoubleTap = { if (st.presetUserMode && !st.editingName) st.editingName = true },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            if (st.editingName) NameEditor(st, name, nameStyle)
            else GlowText(name.uppercase(), nameStyle, listOf(AccentGlow60 to 6f, AccentSoftGlow80 to 2f), Modifier.widthIn(max = 300.dp), overflow = TextOverflow.Ellipsis)
            GlowText(
                "%02d / %02d".format(idx + 1, total),
                otext(6.5f, FontWeight.Medium, Color(0x808FB2E8), OrganFonts.PlexMono, ls = 1.2f, lh = 6.5f), listOf(Color(0x4D8FB2E8) to 3f),
            )
        }
        Box(Modifier.align(Alignment.TopStart).offset(3.dp, 3.dp).size(2.5.dp).cssBox(radius = 1.25f, fill = solid(C.Accent), shadows = listOf(shadow(0f, 0f, 4f, C.Accent))))
        Text("▼", Modifier.align(Alignment.BottomEnd).offset(x = (-3).dp, y = (-1).dp).graphicsLayer { rotationZ = chevron }, style = otext(5f, FontWeight.Normal, Color(0x668FB2E8), lh = 5f))
    }
}

@Composable
private fun NameEditor(st: OrganState, name: String, style: androidx.compose.ui.text.TextStyle) {
    var tf by remember { mutableStateOf(TextFieldValue(name, TextRange(0, name.length))) }
    val focus = remember { FocusRequester() }
    var gotFocus by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    fun finish() { if (done) return; done = true; val n = tf.text.trim().ifEmpty { name }; st.editingName = false; st.renameCurrentUser(n) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        tf, { if (it.text.length <= 20) tf = it }, Modifier.widthIn(min = 40.dp, max = 300.dp).focusRequester(focus)
            .onFocusChanged { if (it.isFocused) gotFocus = true else if (gotFocus) finish() },
        textStyle = style.copy(textAlign = TextAlign.Center), singleLine = true, cursorBrush = SolidColor(C.AccentSoft),
        visualTransformation = UpperTransform, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { finish() }),
    )
}

@Composable
private fun Dropdown(st: OrganState, list: List<OrganPreset>, active: Int) {
    val p by animateFloatAsState(if (st.dropdownOpen) 1f else 0f, tween(220, easing = Bounce), label = "dd")
    if (p <= 0f && !st.dropdownOpen) return
    val scroll = rememberScrollState()
    Box(
        Modifier.offset(y = 38.dp).fillMaxWidth().heightIn(max = 180.dp)
            .graphicsLayer {
                alpha = p.coerceIn(0f, 1f); translationY = -8f * density * (1f - p); val s = 0.96f + 0.04f * p
                scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .cssBox(radius = 6f, fill = vGrad(Color(0xFF0F1620), Color(0xFF060A12)), border = Color(0x338FB2E8),
                shadows = listOf(inset(0f, 1f, 0f, white(0.05f)), shadow(0f, 12f, 32f, black(0.9f))))
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .drawWithContent {
                drawContent()
                if (scroll.maxValue > 0) {
                    val vh = size.height; val th = vh * vh / (vh + scroll.maxValue)
                    val y = scroll.value.toFloat() / scroll.maxValue * (vh - th)
                    drawRoundRect(Color(0x4D8FB2E8), Offset(size.width - 4f * density, y), Size(3f * density, th), CornerRadius(1.5f * density))
                }
            },
    ) {
        Column(Modifier.verticalScroll(scroll).padding(4.dp)) {
            list.forEachIndexed { i, pr ->
                val on = i == active
                Row(
                    Modifier.fillMaxWidth().then(if (on) Modifier.cssBox(radius = 3f, fill = solid(Color(0x268FB2E8))) else Modifier)
                        .pointerInput(i) { detectTapGestures { st.selectPreset(i) } }.padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    val s = otext(8f, if (on) FontWeight.Bold else FontWeight.Medium, if (on) C.AccentSoft else C.TextSub, OrganFonts.PlexMono, ls = 0.4f, lh = 9.6f)
                    if (on) GlowText(pr.name, s, listOf(Color(0x668FB2E8) to 4f)) else Text(pr.name, style = s, maxLines = 1, softWrap = false)
                    Text("%02d".format(i + 1), Modifier.graphicsLayer { alpha = 0.5f }, style = otext(7f, FontWeight.Normal, if (on) C.AccentSoft else C.TextSub, OrganFonts.PlexMono, lh = 8.4f))
                }
            }
        }
    }
}

/* ============================ DRAWBARS ============================ */
@Composable
internal fun DrawbarsPanel(rect: PanelRect, st: OrganState) = OrganPanel(rect, header = { PanelTitle("Drawbars") }) {
    val thumbs = listOf(R.drawable.organ_thumb_brown, R.drawable.organ_thumb_brown, R.drawable.organ_thumb_white, R.drawable.organ_thumb_white,
        R.drawable.organ_thumb_black, R.drawable.organ_thumb_white, R.drawable.organ_thumb_black, R.drawable.organ_thumb_black, R.drawable.organ_thumb_white)
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 8.dp, top = 22.dp, end = 8.dp, bottom = 8.dp)) {
        val colW = (maxWidth - 32.dp) / 9
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("BASS16" to 2, "STR16" to 1, "FLUTES" to 1, "OBOES" to 1, "TRMPS" to 1, "STR8" to 1, "FLUTE4" to 1, "STR4" to 1).forEach { (t, span) ->
                    Box(
                        Modifier.width(colW * span + 4.dp * (span - 1)).fillMaxHeight().drawBehind {
                            drawRect(Color(0x338FB2E8), Offset(8f * density, size.height - density), Size(size.width - 8f * density, density))
                        },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Text(t, Modifier.padding(start = 8.dp, bottom = 4.dp).wrapContentWidth(unbounded = true).graphicsLayer { alpha = 0.9f },
                            style = otext(8f, FontWeight.Bold, C.GroupText, OrganFonts.PlexMono, ls = 0.3f, lh = 9.6f), maxLines = 1, softWrap = false)
                    }
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 9) DrawbarUnit(thumbs[i], st.drawbars[i], st.drawbarsEpoch, { st.setDrawbar(i, it) }, Modifier.width(colW))
            }
            Row(Modifier.height(22.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("16'", "5⅓'", "8'", "4'", "2⅔'", "2'", "1⅗'", "1⅓'", "1'").forEach {
                    Box(Modifier.width(colW).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(it, Modifier.padding(start = 8.dp).wrapContentWidth(unbounded = true).graphicsLayer { alpha = 0.95f },
                            style = otext(10f, FontWeight.Bold, C.GroupText, OrganFonts.PlexMono, ls = 0.3f, lh = 12f), maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

/* ============================ MODEL ============================ */
@Composable
internal fun ModelPanel(rect: PanelRect, st: OrganState) = OrganPanel(rect, header = { PanelTitle("Organ Model") }) {
    val measurer = rememberTextMeasurer()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopStart).offset(94.5.dp, 16.dp).size(46.dp)) {
            // échelle (SVG 100×100 sur 58 dp, inset -6)
            Canvas(Modifier.requiredSize(58.dp).align(Alignment.Center)) {
                val k = size.width / 100f
                for (a in -135..135 step 27) rotate(a.toFloat(), pivot = Offset(50f * k, 50f * k)) {
                    drawLine(white(0.4f), Offset(50f * k, 10f * k), Offset(50f * k, 6f * k), 1.5f * k, StrokeCap.Round)
                }
                val st2 = otext(7.5f * 0.58f, FontWeight.Black, Color.White, lh = 7.5f * 0.58f)
                listOf(Triple("0", 16f, 80f), Triple("2", 5f, 46f), Triple("4", 28f, 14f), Triple("6", 72f, 14f), Triple("8", 95f, 46f), Triple("10", 84f, 80f)).forEach { (t, x, y) ->
                    val l = measurer.measure(AnnotatedString(t), st2)
                    drawText(l, topLeft = Offset(x * k - l.size.width / 2f, y * k - l.size.height / 2f))
                }
            }
            Box(
                Modifier.fillMaxSize().graphicsLayer { rotationZ = -135f + st.modelDial / 100f * 270f }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val y0 = down.position.y; val v0 = st.modelDial
                            drag(down.id) { ch -> st.turnModelDial((v0 + (y0 - ch.position.y) / density / 1.5f).coerceIn(0f, 100f)); ch.consume() }
                        }
                    },
            ) { PaddedVector(R.drawable.organ_rotary_switch, 46f, 46f, 6f) }
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.width(44.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("B3", "Pipe", "Farf").forEachIndexed { i, n ->
                    val on = st.modelIndex == i
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        Text(n.uppercase(), Modifier.graphicsLayer { alpha = if (on) 1f else 0.6f }, style = otext(6f, FontWeight.ExtraBold, if (on) C.TextMain else C.TextSub, ls = 0.5f, lh = 7.2f), maxLines = 1, softWrap = false)
                        LedDot(5f, on, C.LedRed, glow = 6f)
                    }
                }
            }
            val hx by animateDpAsState((2 + st.modelIndex * 13).dp, tween(200, easing = CubicBezierEasing(0.3f, 1.5f, 0.5f, 1f)), label = "toggle")
            Box(Modifier.size(44.dp, 18.dp).pointerInput(Unit) { detectTapGestures { st.cycleModel() } }) {
                PaddedVector(R.drawable.organ_toggle_track, 44f, 18f, 4f)
                Box(Modifier.offset(hx, 2.dp)) { PaddedVector(R.drawable.organ_toggle_handle, 14f, 14f, 4f) }
            }
        }
    }
}

/* ============================ EQ ============================ */
private fun eqText(v: Float): String {
    val raw = v / 100f * 24f - 12f
    return "${if (raw > 0f) "+" else ""}${raw.roundToInt()} dB"
}

@Composable
internal fun EqPanel(rect: PanelRect, st: OrganState) = OrganPanel(rect, header = { PanelTitle("EQ") }) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Triple("Treble", st.treble, OrganKnobId.EqTreble).let { EqChannel(it.first, it.second) { v -> st.setKnob(it.third, v) } }
        Triple("Mid", st.mid, OrganKnobId.EqMid).let { EqChannel(it.first, it.second) { v -> st.setKnob(it.third, v) } }
        Triple("Bass", st.bass, OrganKnobId.EqBass).let { EqChannel(it.first, it.second) { v -> st.setKnob(it.third, v) } }
    }
}

@Composable
private fun EqChannel(label: String, v: Float, onChange: (Float) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OrganKnob(48, v, onChange)
        Text(label.uppercase(), Modifier.padding(top = 2.dp), style = otext(8f, FontWeight.ExtraBold, C.TextSub, lh = 9.6f), maxLines = 1, softWrap = false)
        Box(Modifier.widthIn(min = 26.dp).cssBox(radius = 3f, fill = solid(black(0.3f))).padding(horizontal = 4.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
            Text(eqText(v), style = otext(9f, FontWeight.Bold, C.TextMain, OrganFonts.Mono, lh = 10.5f), textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        }
    }
}

/* ============================ DRIVE ============================ */
@Composable
internal fun DrivePanel(rect: PanelRect, st: OrganState) = OrganPanel(rect, header = { PanelTitle("Drive") }) {
    val measurer = rememberTextMeasurer()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopStart).offset(75.5.dp, 0.6.dp).size(84.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / 100f
                for (a in listOf(-135, -81, -27, 27, 81, 135)) rotate(a.toFloat(), pivot = Offset(50f * k, 50f * k)) {
                    drawLine(white(0.45f), Offset(50f * k, 17f * k), Offset(50f * k, 12f * k), 1.5f * k, StrokeCap.Round)
                }
                val s = otext(6.5f * 0.84f, FontWeight.Bold, C.TextSub, lh = 6.5f * 0.84f)
                listOf(Triple("0", 18.9f, 81.1f), Triple("2", 6.5f, 43.1f), Triple("4", 30f, 10.8f), Triple("5", 50f, 6f), Triple("6", 70f, 10.8f), Triple("8", 93.5f, 43.1f), Triple("10", 81.1f, 81.1f)).forEach { (t, x, y) ->
                    val l = measurer.measure(AnnotatedString(t), s)
                    drawText(l, topLeft = Offset(x * k - l.size.width / 2f, y * k - l.size.height / 2f))
                }
            }
            Box(Modifier.align(Alignment.Center)) { OrganKnob(56, st.drive, { st.setKnob(OrganKnobId.Drive, it) }) }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
            Text("AMOUNT", style = otext(8f, FontWeight.ExtraBold, C.TextSub, ls = 0.8f, lh = 9.6f), maxLines = 1, softWrap = false)
            Text("%.1f".format(st.drive / 10f), style = otext(9f, FontWeight.ExtraBold, C.Accent, OrganFonts.Mono, lh = 10.5f), maxLines = 1, softWrap = false)
        }
    }
}

/* ============================ REVERB ============================ */
@Composable
internal fun ReverbPanel(rect: PanelRect, st: OrganState) = OrganPanel(rect, header = { PanelTitle("Reverb") }) {
    Row(Modifier.fillMaxSize().padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OrganKnob(48, st.reverbAmount, { st.setKnob(OrganKnobId.ReverbAmount, it) })
            Text("AMOUNT", style = otext(8f, FontWeight.ExtraBold, C.TextSub, ls = 0.8f, lh = 9.6f), maxLines = 1, softWrap = false)
        }
        Column(Modifier.width(85.dp).offset(y = (-4).dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(0.dp))
            Column(
                Modifier.fillMaxWidth().cssBox(radius = 6f, fill = solid(Color(0xFF0B0E14)), border = white(0.05f), shadows = listOf(inset(0f, 2f, 4f, black(0.8f)), shadow(0f, 1f, 1f, white(0.05f)))).padding(3.dp),
            ) {
                listOf("Room", "Stage", "Hall").forEachIndexed { i, n ->
                    val on = st.reverbMode == i
                    Box(
                        Modifier.fillMaxWidth().pointerInput(i) { detectTapGestures { st.chooseReverbMode(i) } }
                            .then(if (on) Modifier.cssBox(radius = 4f, fill = vGrad(C.BtnTop, C.BtnBottom), shadows = listOf(shadow(0f, 2f, 4f, black(0.5f)), inset(0f, 1f, 0f, white(0.1f)))) else Modifier)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(n.uppercase(), style = otext(9f, FontWeight.ExtraBold, if (on) C.TextMain else C.TextSub, ls = 0.5f, lh = 10.9f), maxLines = 1, softWrap = false) }
                }
            }
        }
    }
}
