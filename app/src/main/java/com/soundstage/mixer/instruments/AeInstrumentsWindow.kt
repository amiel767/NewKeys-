package com.soundstage.mixer.instruments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

// The Æther Instruments window. A migration of the Soundfont window's shell: same panel size,
// same backdrop, same left-column layout, but its own content (the instrument list) and its own
// left item (the "Æther Instruments" pill). The instrument interface is not a separate overlay
// any more: it is hosted inside this same window, which grows to the 1000 x 460 stage.

private val AePanelShape = RoundedCornerShape(24.dp)
private val AeBackdrop = Color(0x9E060910)
private val AePanelTop = Color(0xFF232C3E)
private val AePanelBottom = Color(0xFF1A2230)

/**
 * The Æther Instruments window.
 *
 * Two states, one window:
 *  - no instrument loaded (or the user went back): the list of instruments, laid out like the
 *    soundfont window (left column with a single pill, the rows on the right);
 *  - an instrument loaded: the instrument's own interface, the window growing to the stage's
 *    1000:460 ratio. The back chevron inside the interface returns to the list.
 *
 * [isOpen] drives the enter/exit animation so the caller keeps the window in the composition
 * (and its scroll position) while it is closed.
 */
@Composable
fun AetherInstrumentsWindow(
    manager: AeManager,
    strip: Int,
    isOpen: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which instrument's interface is shown; null means the list. The strip's loaded instrument
    // decides what to show when the window opens, so loading from a track lands straight on the
    // interface instead of the list.
    var open by remember { mutableStateOf<AeInstrument?>(null) }
    val loaded = manager.sessions[strip]?.instrument
    LaunchedEffect(isOpen, loaded, strip) {
        open = if (!isOpen) null else loaded
    }

    Box(modifier.fillMaxSize().zIndex(50f)) {
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(AeBackdrop)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onClose() }
            )
        }

        AnimatedVisibility(
            visible = isOpen,
            enter = scaleIn(
                initialScale = 0.55f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f)
            ) + fadeIn(tween(220)),
            exit = scaleOut(targetScale = 0.75f, animationSpec = tween(180)) + fadeOut(tween(160)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val inst = open
                if (inst == null) {
                    AeListPanel(
                        manager = manager,
                        strip = strip,
                        onOpen = { open = manager.sessions[strip]?.instrument },
                        onClose = onClose,
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth(0.97f)
                            .aspectRatio(AE_W / AE_H)
                            .shadow(28.dp, AePanelShape, ambientColor = Color.Black, spotColor = Color(0xAA000000))
                            .clip(AePanelShape)
                    ) {
                        AeInstrumentWindow(
                            manager = manager,
                            strip = strip,
                            onClose = { open = null },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/** The list state of the window: the Soundfont window's shell, with the Æther content. */
@Composable
private fun AeListPanel(
    manager: AeManager,
    strip: Int,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .width(660.dp)
            .height(372.dp)
            .shadow(28.dp, AePanelShape, ambientColor = Color.Black, spotColor = Color(0xAA000000))
            .clip(AePanelShape)
            .background(Brush.verticalGradient(listOf(AePanelTop, AePanelBottom)))
            .border(1.dp, Color(0x1AFFFFFF), AePanelShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* absorb */ }
    ) {
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 20.dp, end = 14.dp, top = 14.dp)
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Æther Instruments",
                color = Color(0xFFF4F6FB),
                fontSize = 17.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = AeFonts.ui,
                modifier = Modifier.weight(1f),
            )
            AeCloseBtn(onClose)
        }

        // The left column carries only the pill, by request: nothing else for now.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 68.dp, bottom = 14.dp)
                .width(154.dp)
                .fillMaxHeight()
        ) {
            AeInstrumentsTabItem(selected = true, onClick = {})
        }

        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 182.dp, end = 14.dp, top = 68.dp, bottom = 14.dp)
                .fillMaxSize()
        ) {
            AeInstrumentsTabContent(manager = manager, strip = strip, onOpen = onOpen)
        }
    }
}

@Composable
private fun AeCloseBtn(onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color(0x12FFFFFF))
            .border(1.dp, Color(0x1FFFFFFF), CircleShape)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(15.dp)) {
            val w = 2.4.dp.toPx()
            drawLine(Color(0xFFE9EDF5), Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
            drawLine(Color(0xFFE9EDF5), Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
        }
    }
}

/**
 * The chooser that a track name opens: two sound sources, each its own window.
 *
 * A small centred dialog rather than a full window, because it only routes to one of the two
 * windows and dismisses itself. The two entries are the ones that exist; there are no "soon"
 * placeholders.
 */
@Composable
fun SoundSourceChooserPopup(
    onPickSoundfonts: () -> Unit,
    onPickAether: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().zIndex(50f)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AeBackdrop)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClose() }
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .width(420.dp)
                    .shadow(28.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black, spotColor = Color(0xAA000000))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(listOf(AePanelTop, AePanelBottom)))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(22.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* absorb */ }
                    .padding(20.dp)
            ) {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
                    Text(
                        "Sound source",
                        color = Color(0xFFF4F6FB),
                        fontSize = 15.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AeFonts.ui,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
                    ChooserRow(
                        title = "Soundfonts",
                        sub = "SF2 · SF3 · presets",
                        accent = Color(0xFF8FB2E8),
                        onClick = onPickSoundfonts,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                    ChooserRow(
                        title = "Æther Instruments",
                        sub = "Organ · Synth · Leads · Sax · Piano",
                        accent = Color(0xFFB9A8FF),
                        onClick = onPickAether,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChooserRow(title: String, sub: String, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, Color(0x1FFFFFFF), shape)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            androidx.compose.foundation.layout.Spacer(Modifier.width(14.dp))
            androidx.compose.foundation.layout.Column {
                Text(
                    title,
                    color = Color(0xFFF4F6FB),
                    fontSize = 15.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AeFonts.ui,
                )
                Text(
                    sub,
                    color = accent.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontFamily = AeFonts.ui,
                )
            }
        }
    }
}
