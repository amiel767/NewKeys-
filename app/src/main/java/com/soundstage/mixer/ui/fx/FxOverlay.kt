package com.soundstage.mixer.ui.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.soundstage.mixer.fx.FxController
import com.soundstage.mixer.fx.FxKind
import com.soundstage.mixer.fx.FxRegistry

/**
 * FX overlay for one mixer strip: the rack, the full window of the selected
 * effect, and the add panel. Only the open window animates at full cadence; the
 * cards keep their own reduced cadence.
 */
@Composable
fun FxOverlay(
    trackId: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strip = stripForTrack(trackId)
    val controller = remember(strip) { FxController(strip) }
    var selectedSlot by remember(strip) { mutableIntStateOf(-1) }
    var addOpen by remember(strip) { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var undoSlot by remember { mutableIntStateOf(-1) }
    var undoId by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    // Peak poll for the rail, 30 Hz.
    androidx.compose.runtime.LaunchedEffect(strip) {
        while (true) {
            level = maxOf(controller.peak(0), controller.peak(1))
            kotlinx.coroutines.delay(33)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC05070C))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            FxRackStrip(
                controller = controller,
                selectedSlot = if (selectedSlot >= 0) selectedSlot else null,
                onSelect = { selectedSlot = it },
                onRemove = { slot ->
                    val id = controller.effectIds.getOrNull(slot) ?: -1
                    if (id >= 0) {
                        undoSlot = slot
                        undoId = id
                        controller.setEffect(slot, -1)
                        if (selectedSlot == slot) selectedSlot = -1
                        scope.launch {
                            delay(5200)
                            if (undoSlot == slot) { undoSlot = -1; undoId = -1 }
                        }
                    }
                },
                onAddInstrument = { addOpen = true },
                onAddEffect = { addOpen = true },
                onClose = onClose,
            )
        }

        val slot = selectedSlot
        if (slot >= 0 && controller.effectIds.getOrNull(slot)?.let { it >= 0 } == true) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .padding(top = 38.dp, bottom = 10.dp),
            ) {
                FxWindow(
                    effectId = controller.effectIds[slot],
                    slot = slot,
                    controller = controller,
                    level = level,
                    onClose = { selectedSlot = -1 },
                )
            }
        }

        if (undoSlot >= 0 && undoId >= 0) {
            Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xF0161C28))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        FxRegistry.info(undoId).name + " removed",
                        color = Color(0xFFDDE3EE), fontSize = 12.sp,
                        fontFamily = FxFonts.mono)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Undo", color = FxStyle.accent(undoId), fontSize = 12.sp,
                        fontFamily = FxFonts.mono,
                        modifier = Modifier.clickable {
                            controller.setEffect(undoSlot, undoId)
                            selectedSlot = undoSlot
                            undoSlot = -1
                            undoId = -1
                        })
                }
            }
        }
        if (addOpen) {
            Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                FxAddPanel(
                    controller = controller,
                    onPick = { id ->
                        val info = FxRegistry.info(id)
                        if (info.kind == FxKind.EFFECT) {
                            val free = controller.effectIds.indexOfFirst { it < 0 }
                            if (free >= 0 && controller.setEffect(free, id)) selectedSlot = free
                        } else {
                            controller.setInstrument(id)
                            val first = controller.effectIds.indexOfFirst { it < 0 }
                            selectedSlot = if (first >= 0) first else 0
                        }
                        addOpen = false
                    },
                    onClose = { addOpen = false },
                )
            }
        }
    }
}
