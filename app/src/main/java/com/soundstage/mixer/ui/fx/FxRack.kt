package com.soundstage.mixer.ui.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.soundstage.mixer.fx.FxController
import com.soundstage.mixer.fx.FxKind
import com.soundstage.mixer.fx.FxRegistry

/** FX strip for a mixer track: tracks 1..8 map to strips 0..7, track 0 is master. */
fun stripForTrack(trackId: Int): Int =
    if (trackId == 0) FxRegistry.STRIP_MASTER else (trackId - 1).coerceIn(0, 7)

private val CardBg = Brush.linearGradient(listOf(Color(0xFF323845), Color(0xFF222733)))
private val HeaderBg = Brush.linearGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF)))

/**
 * Reduced, animated preview of an effect, as the rack cards show it. The card is
 * 158x164 logical units; the scene is drawn at the preview's own mini scale
 * (card width / 825) and centred vertically.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FxCard(
    id: Int,
    selected: Boolean,
    lifted: Boolean,
    t: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = remember(id) { FxRegistry.info(id) }
    val accent = FxStyle.accent(id)
    val tm = rememberTextMeasurer()
    val scene = remember(id) { FxSceneState() }
    val values = remember(id) { FloatArray(FxRegistry.MAX_PARAMS) { i -> info.params.getOrNull(i)?.default ?: 0f } }
    var dragY by remember(id) { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .width(158.dp)
            .zIndex(if (lifted) 20f else 0f)
            .offset(y = dragY.dp)
            .graphicsLayer { alpha = if (lifted) 0.92f else 1f }
            // Raised card: no outline, the lift comes from the drop shadow and a
            // 1px inner highlight along the top edge (`.cd` in the reference uses
            // `0 8px 16px #0008, inset 0 1px 0 #ffffff1c`). Selection is shown by
            // the shadow's accent tint instead of a border.
            .shadow(
                elevation = if (selected) 14.dp else 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = if (selected) Color(0x66E8C98A) else Color.Black,
                spotColor = if (selected) Color(0x66E8C98A) else Color.Black,
            )
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .drawWithContent {
                drawContent()
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0x00FFFFFF), Color(0x1CFFFFFF), Color(0x00FFFFFF))),
                    start = Offset(0f, 0.5f), end = Offset(size.width, 0.5f), strokeWidth = 1f,
                )
            }
            .combinedClickable(onClick = { onClick() }, onLongClick = { onLongClick() })
            .pointerInput(id) {
                detectVerticalDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragStop() },
                    onDragCancel = { onDragStop() },
                ) { change, amount ->
                    change.consume()
                    dragY += amount
                    onDrag(dragY)
                }
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(info.type.uppercase(), color = Color(0xFF8D96AB), fontSize = 8.sp,
            fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(info.name, color = Color(0xFFDFE4EE), fontSize = 20.sp,
            fontFamily = FxFonts.title(id), maxLines = 1)
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(136.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF12161F)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val s = size.width / FxStyle.PWL
                val sceneH = FxStyle.PHL * s
                withTransform({
                    translate(0f, (size.height - sceneH) / 2f)
                    scale(s, s, pivot = Offset.Zero)
                }) {
                    drawFxScene(id, values, t, tm, scene)
                }
            }
        }
        Text("${info.params.size} knobs", color = Color(0xFF8D96AB), fontSize = 9.sp,
            fontFamily = FxFonts.ui, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** One pill button of the window header. */
@Composable
private fun HeaderButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(HeaderBg)
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFFDDE3EE), fontSize = 11.sp,
            fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * The rack: header (count, add buttons, close) plus the row of effect cards.
 * [selectedSlot] is the slot whose full window is open, if any.
 */
@Composable
fun FxRackStrip(
    controller: FxController,
    selectedSlot: Int?,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDragUpdate: (Int, Float) -> Unit,
    onDragStop: (Int) -> Unit,
    onAddInstrument: () -> Unit,
    onAddEffect: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ids = controller.effectIds
    val count = ids.count { it >= 0 }
    var draggingSlot by remember { mutableIntStateOf(-1) }
    var dragDist by remember { mutableFloatStateOf(0f) }
    // One clock for the whole rack: cards animated at ~15 images/s (doc 03),
    // previously one LaunchedEffect per card.
    var cardT by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { cardT = (it / 1_000_000_000f) }
            kotlinx.coroutines.delay(66)
        }
    }
    val threshold = 110f
    val overTrash = draggingSlot >= 0 && dragDist > threshold
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1F2635), Color(0xFF131925))))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (draggingSlot >= 0 && event.type == PointerEventType.Release) {
                            val slot = draggingSlot
                            val drop = dragDist > threshold
                            draggingSlot = -1
                            dragDist = 0f
                            onDragStop(slot)
                            if (drop) onRemove(slot)
                        }
                    }
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Effects", color = Color(0xFF8D96AB), fontSize = 12.sp,
                fontFamily = FxFonts.ui, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f))
            Text("$count / ${controller.slotCount} effects", color = Color(0xFF8D96AB),
                fontSize = 10.sp, fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 6.dp))
            HeaderButton("+ Instrument", onAddInstrument)
            Spacer(Modifier.width(8.dp))
            HeaderButton("+ Effect", onAddEffect)
            Spacer(Modifier.width(8.dp))
            HeaderButton("✕", onClose)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ids.forEachIndexed { slot, id ->
                if (id >= 0) {
                    FxCard(
                        id = id,
                        selected = selectedSlot == slot,
                        lifted = draggingSlot == slot,
                        t = cardT,
                        onClick = { onSelect(slot) },
                        onLongClick = { onRemove(slot) },
                        onDragStart = {
                            draggingSlot = slot
                            dragDist = 0f
                        },
                        onDrag = { dy ->
                            dragDist = dy
                            onDragUpdate(slot, dy)
                        },
                        onDragStop = {
                            draggingSlot = -1
                            dragDist = 0f
                            onDragStop(slot)
                        },
                    )
                }
            }
        }
        if (overTrash) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xE67A2216))
                        .border(1.5.dp, Color(0xFFFF7A5A), RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("\u232B", color = Color(0xFFFFE9E0), fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("Drop here to remove", color = Color(0xFFFFE9E0), fontSize = 12.sp,
                        fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Add panel: two tabs (effects / instrument) over a grid of the registry entries.
 * Picking an instrument replaces the current one, as the preview does.
 */
@Composable
fun FxAddPanel(
    controller: FxController,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(if (controller.instrumentId >= 0) 1 else 0) }
    val entries = remember(tab) {
        if (tab == 1) FxRegistry.all.filter { it.kind != FxKind.EFFECT }
        else FxRegistry.all.filter { it.kind == FxKind.EFFECT }
    }
    Column(
        modifier = modifier
            .widthIn(min = 420.dp, max = 640.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF232A3A), Color(0xFF161C28))))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (tab == 1) "Add an instrument" else "Add an effect",
                color = Color(0xFFE6EAF3), fontSize = 14.sp,
                fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            HeaderButton("✕", onClose)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderButton("EFFECTS", { tab = 0 })
            HeaderButton("INSTRUMENT", { tab = 1 })
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { fx ->
                        Box(
                            Modifier
                                .width(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x14FFFFFF))
                                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                                .clickable { onPick(fx.id) }
                                .padding(10.dp),
                        ) {
                            Column {
                                Text(fx.name, color = FxStyle.accent(fx.id), fontSize = 12.sp,
                                    fontFamily = FxFonts.title(fx.id), maxLines = 1)
                                Text(fx.type, color = Color(0xFF8D96AB), fontSize = 9.sp,
                                    fontFamily = FxFonts.mono, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
