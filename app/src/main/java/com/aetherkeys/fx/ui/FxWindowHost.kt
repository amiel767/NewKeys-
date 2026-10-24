package com.aetherkeys.fx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.soundstage.mixer.ui.fx.FxFonts
import com.soundstage.mixer.ui.fx.fxPanelBrush
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * The window is the effect area itself: 825x343, the reference's `PWL` x `PHL`.
 * It is *not* the 851x394 of the PNG — that is the screenshot, which includes the
 * page margin (12 px left/right, 38 px top, 10 px bottom) around the window.
 * Measured: the effect background spans y_log 39.4..381.6 and x_log 12.8..837.0,
 * so 825x343 with the page outside it. Taking 851x394 as the window is what made
 * the background look undersized.
 */
const val CANVAS_W = 825f
const val CANVAS_H = 343f

/**
 * `.bd`: the scene canvas, 779x263 at the content origin (20,58) inside the
 * window. The scenes are written in content-local coordinates and paint their own
 * `.xN` background from (-OXL,-OYL), which lands exactly on the window. Painting
 * them at the window origin dropped every element by (20,58).
 */
const val SCENE_X = 20f
const val SCENE_Y = 58f

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
fun FxWindowHost(store: FxShellStore, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    LaunchedEffect(store.notice) {
        if (store.notice != null) { delay(2400); store.notice = null }
    }
    // The scrim stays full screen, but the window itself keeps the reference's own
    // size: 825x343 logical, centred, and never blown up past 1:1. Filling the screen
    // stretched the rack and the effect window on tablets and looked nothing like the
    // preview.
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val scale = minOf(maxWidth.value / CANVAS_W, maxHeight.value / CANVAS_H, 1f)
        val winW = (CANVAS_W * scale).dp
        val winH = (CANVAS_H * scale).dp
        Box(
            modifier = Modifier
                .width(winW)
                .height(winH)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (store.openId != null) fxPanelBrush(store.openId!!)
                    else Brush.verticalGradient(listOf(Color(0xFF1F2635), Color(0xFF131925))),
                )
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.TopStart,
        ) {
            val tm = rememberTextMeasurer()
            Box(
                modifier = Modifier
                    .width(CANVAS_W.dp)
                    .height(CANVAS_H.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale, transformOrigin = TransformOrigin(0f, 0f)),
            ) {
                val openId = store.openId
                if (openId == null) {
                    Column(Modifier.fillMaxSize()) {
                        Header(store, onClose)
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
                            page = store.page,
                            onPage = { store.page = it },
                            onEdit = { store.editOpen = !store.editOpen },
                            editOpen = store.editOpen,
                            tm = tm,
                        )
                    }
                }
            }
            // `#ad` and the notice are window chrome, not canvas content: they stay
            // unscaled so hit targets do not shrink with the canvas.
            // The reference slides the sheet from translateY(102%) to 0 over 500 ms.
            // It used to appear in a single frame, which read as a hard cut; the scrim
            // fades while the sheet rises, and both are keyed on `addOpen` so closing
            // plays the reverse instead of tearing the overlay down instantly.
            androidx.compose.animation.AnimatedVisibility(
                visible = store.addOpen,
                enter = androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(500)
                ) + androidx.compose.animation.slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(500),
                ),
                exit = androidx.compose.animation.fadeOut(
                    androidx.compose.animation.core.tween(220)
                ) + androidx.compose.animation.slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(220),
                ),
            ) {
                Box(Modifier.fillMaxSize().background(Color(0xE60A0E16)).clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { store.addOpen = false }) {
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
}

/** `.wh`: height 42, padding 0 12, gap 10. */
@Composable
private fun Header(store: FxShellStore, onClose: (() -> Unit)? = null) {
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
        if (onClose != null) CloseChip(onClose)
    }
}

/**
 * Window close button. The FX window is a modal overlay with no other way out, so
 * this is the one control the user needs to leave it; it sits last in the header,
 * on the same rhythm as the other chips.
 */
@Composable
private fun CloseChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .shadow(3.dp, RoundedCornerShape(9.dp), clip = false)
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x0FFFFFFF))))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Two strokes so the cross is crisp at this size without an icon dependency.
        androidx.compose.foundation.Canvas(Modifier.size(12.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 1.6f * density
            drawLine(Color(0xFFE6EAF3), Offset(0f, 0f), Offset(w, h), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(Color(0xFFE6EAF3), Offset(w, 0f), Offset(0f, h), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** `.bt`: height 28, radius 9, gradient #ffffff14 -> #ffffff08, border #ffffff14, shadow 0 2 4 #0006. */
@Composable
internal fun Chip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(28.dp)
            // Raised look: a soft drop shadow plus a 1 dp top highlight. Without the
            // highlight the buttons read as flat text and did not look pressable.
            .shadow(4.dp, RoundedCornerShape(9.dp), clip = false)
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(9.dp))
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
    val slots = store.slotsOf(store.strip)
    val ids = buildList {
        store.instrumentOf(store.strip)?.let { add(it) }
        slots.forEach { if (it != null) add(it) }
    }
    // The rack always ends with an empty `+` slot: it is the only way to reach the add
    // sheet, and the reference shows it as a card of the same size. It used to be
    // missing entirely, so adding an effect had no visible entry point.
    val hasRoom = slots.any { it == null }

    // Drag-to-delete: press and hold a card, lift it, and drop it on the bin. The
    // reference plays this as a real lift (the card rises and casts a bigger shadow)
    // and offers an Undo bandeau for a few seconds. Long-press alone used to delete
    // the card with no animation and no way back, which read as a glitch.
    var lifted by remember { mutableStateOf<Int?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var undo by remember { mutableStateOf<Int?>(null) }
    val overBin = lifted != null && dragY < -BIN_DROP
    LaunchedEffect(undo) {
        if (undo != null) { delay(5_200); undo = null }
    }

    Box(Modifier.fillMaxSize()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
        ) {
            items(ids) { id ->
                val m = store.modules[id] ?: return@items
                RackCard(
                    m = m,
                    params = store.valuesOf(id),
                    tm = tm,
                    lifted = lifted == id,
                    dragY = if (lifted == id) dragY else 0f,
                    onOpen = { store.openId = id },
                    onDragStart = { lifted = id; dragY = 0f },
                    onDrag = { dy -> dragY = dy },
                    onDragEnd = {
                        val drop = dragY < -BIN_DROP
                        lifted = null
                        dragY = 0f
                        if (drop) { store.removeEffect(id); undo = id }
                    },
                )
            }
            if (hasRoom) {
                item { AddSlotCard { store.addTab = FxAddTab.EFFECTS; store.addOpen = true } }
            }
        }

        // The bin appears only while a card is lifted, so the rack keeps its clean
        // look the rest of the time.
        if (lifted != null) {
            val armed = overBin
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (armed) Color(0xF27A2216) else Color(0xCC1A2030))
                    .border(1.5.dp, if (armed) Color(0xFFFF7A5A) else Color(0x24FFFFFF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(
                    if (armed) "Release to delete" else "Drag up to delete",
                    fontSize = 11.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFE9E0),
                )
            }
        }

        undo?.let { id ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE62A3042))
                    .border(1.dp, Color(0x1CFFFFFF), RoundedCornerShape(14.dp))
                    .padding(start = 16.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${store.modules[id]?.name ?: "Effect"} removed",
                        fontSize = 11.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE6EAF3),
                    )
                    Text(
                        "UNDO",
                        fontSize = 11.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (11 * 0.12f).sp, color = Color(0xFF8FB2E8),
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { store.addEffect(id); undo = null }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

/** Vertical travel (logical px) that arms the bin. */
private const val BIN_DROP = 110f

/**
 * The trailing empty slot: same footprint as a card, but carved *into* the window
 * instead of raised on top of it. A raised card here read as a filled effect; the
 * inset gradient (dark at the top, catching light at the bottom) plus the inner
 * shadow rim is what makes it read as an empty socket waiting for an effect.
 */
@Composable
private fun AddSlotCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(158.dp)
            .fillMaxHeight()
            .clip(shape)
            // Carved: inverted gradient (top dark -> bottom lighter) and no drop shadow.
            .background(Brush.verticalGradient(listOf(Color(0xFF141924), Color(0xFF232A38))))
            .border(1.dp, Color(0x14000000), shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                // Concave disc: darker at the top so it looks pressed in.
                .background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0x0AFFFFFF))))
                .border(1.dp, Color(0x22000000), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // The `+` is drawn, not typed: a text glyph sits off-centre inside its
            // line box, which is why the plus looked low in the circle.
            androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
                val c = size.width / 2f
                val arm = size.width * 0.36f
                val stroke = 2.4f * density
                drawLine(Color(0xFFC7CFDD), Offset(c - arm, c), Offset(c + arm, c), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Color(0xFFC7CFDD), Offset(c, c - arm), Offset(c, c + arm), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("ADD EFFECT", fontSize = 10.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
            letterSpacing = (10 * 0.12f).sp, color = Color(0xFF8D96AB))
    }
}

/**
 * `.cd`: width 158, radius 16, padding 10, gap 6, gradient #323845 -> #222733.
 * Canvas 136x160 (`.cd canvas`), scene drawn at mini scale (m = 1).
 */
@Composable
internal fun RackCard(
    m: EffectModule,
    params: FloatArray,
    tm: androidx.compose.ui.text.TextMeasurer,
    lifted: Boolean = false,
    dragY: Float = 0f,
    onOpen: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val clock = rememberFxClock(fullRate = false)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(158.dp)
            .fillMaxHeight()
            // While lifted the card rises with the finger, sits above its neighbours
            // and casts a deeper shadow, which is what makes the drag read as a lift
            // rather than a jump.
            .zIndex(if (lifted) 20f else 0f)
            .offset(y = dragY.dp)
            .graphicsLayer { scaleX = if (lifted) 1.04f else 1f; scaleY = if (lifted) 1.04f else 1f }
            .shadow(if (lifted) 18.dp else 8.dp, shape, clip = false)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF323845), Color(0xFF222733))))
            .border(1.dp, Color(0x12FFFFFF), shape)
            .clickable(onClick = onOpen)
            .pointerInput(m.id) {
                detectVerticalDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                ) { change, amount ->
                    change.consume()
                    onDrag(dragY + amount)
                }
            }
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
