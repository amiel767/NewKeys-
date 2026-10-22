package com.soundstage.mixer.ui.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import com.aetherkeys.fx.ui.FxControllerEngine
import com.aetherkeys.fx.ui.FxShellStore
import com.aetherkeys.fx.ui.FxWindowHost
import com.aetherkeys.fx.ui.fxModules
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
    soundfontName: String,
    program: Int,
    onClose: () -> Unit,
    onChooseSoundfont: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val strip = stripForTrack(trackId)
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val controller = remember(strip) { FxController(strip, appContext) }
    // The new shell owns the rack, the effect window and the add sheet. The
    // controller stays the bridge to the native mixer and the persistence layer.
    val store = remember(strip) { FxShellStore(fxModules(), FxControllerEngine(controller)) }
    val warned = remember(strip) { mutableStateOf(emptySet<Int>()) }
    val inst = controller.instrumentId
    val warnId = if (inst >= 0 && FxSfWarning.checked(inst) && inst !in warned.value &&
        !FxSfWarning.matches(inst, soundfontName, program)) inst else -1

    // Restore once, then auto-save whenever the strip changes.
    androidx.compose.runtime.LaunchedEffect(strip) {
        // Storage is restored first: the controller pushes every effect and every
        // parameter to the native mixer, so the engine is the authority at this
        // point. Seeding the shell from the controller, rather than the reverse,
        // avoids replaying the whole strip back into the mixer.
        controller.restore()
        store.strip = strip
        store.seed(strip, controller.effectIds.toList(), controller.params.map { it.toList() },
            controller.bypassed.toList(), controller.instrumentId.takeIf { it >= 0 })
    }
    androidx.compose.runtime.LaunchedEffect(strip) {
        snapshotFlow { controller.effectIds.toList() to controller.bypassed.toList() }
            .collect { controller.save() }
    }
    // Knob drags fire many changes; save once the user stops for 500 ms.
    androidx.compose.runtime.LaunchedEffect(strip) {
        snapshotFlow { controller.params.map { it.toList() } }
            .collectLatest {
                kotlinx.coroutines.delay(500)
                controller.save()
            }
    }


    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC05070C))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            FxWindowHost(store = store, modifier = Modifier.fillMaxSize())
        }

        if (warnId >= 0) {
            Box(
                Modifier.fillMaxWidth().padding(top = 64.dp, start = 14.dp, end = 14.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(0xF27A2216), Color(0xF24A120C))))
                        .border(1.dp, Color(0xFFFF7A5A), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(FxSfWarning.head(warnId), color = Color(0xFFFFE9E0), fontSize = 12.sp,
                        fontFamily = FxFonts.ui, fontWeight = FontWeight.Bold)
                    Text(FxSfWarning.body(warnId, soundfontName), color = Color(0xFFFFE9E0),
                        fontSize = 11.sp, fontFamily = FxFonts.ui)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CHOOSE A SOUNDFONT", color = Color(0xFF4A120C), fontSize = 9.5.sp,
                            fontFamily = FxFonts.ui, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFFFFE9E0))
                                .clickable { onChooseSoundfont() }
                                .padding(horizontal = 10.dp, vertical = 6.dp))
                        Text("USE ANYWAY", color = Color(0xFFFFE9E0), fontSize = 9.5.sp,
                            fontFamily = FxFonts.ui, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0x1CFFFFFF))
                                .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(9.dp))
                                .clickable { warned.value = warned.value + warnId }
                                .padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }

    }
}
