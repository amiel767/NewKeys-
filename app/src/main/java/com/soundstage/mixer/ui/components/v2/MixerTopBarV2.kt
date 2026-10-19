package com.soundstage.mixer.ui.components.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.R
import com.soundstage.mixer.ui.components.ChordDisplay

private val PanelTop = Color(0xFF2B3348)
private val PanelBottom = Color(0xFF1D2433)
private val TextMain = Color(0xFFE9EDF5)
private val TextDim = Color(0xFF7B859A)
private val Accent = Color(0xFF8FB2E8)

@Composable
fun MixerTopBarV2(
    transpose: Int, octave: Int,
    onTransposeChange: (Int) -> Unit, onOctaveChange: (Int) -> Unit,
    chord: ChordDisplay?,
    isSustainActive: Boolean, onToggleSustain: () -> Unit,
    onOpenNotes: () -> Unit, onOpenDrumPad: () -> Unit,
    onPanic: () -> Unit, onOpenScenes: () -> Unit, onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.height(44.dp), // -20% as asked (55 -> 44)
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)) {

        StepperV2("TRANS", if (transpose > 0) "+$transpose" else "$transpose",
            { onTransposeChange(-1) }, { onTransposeChange(1) })
        StepperV2("OCT", if (octave > 0) "+$octave" else "$octave",
            { onOctaveChange(-1) }, { onOctaveChange(1) })

        ChordZoneV2(chord)

        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFD2587C), Color(0xFFB03F63))))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPanic() },
            contentAlignment = Alignment.Center) {
            IconDinV2(size = 22.dp, color = Color.White, withBar = true)
        }

        SustainPedalButton(isActive = isSustainActive, onToggle = onToggleSustain,
            panelTop = PanelTop, panelBottom = PanelBottom)

        TopIconButton(onOpenNotes) {
            Icon(painterResource(R.drawable.ic_quill), "Notes", tint = Color.White,
                modifier = Modifier.size(19.dp))
        }

        TopIconButton(onOpenDrumPad) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(
                    listOf(Color(0xFFE0588C), Color(0xFFE0588C), Color(0xFFA55BE0)),
                    listOf(Color(0xFFE0588C), Color(0xFFA55BE0), Color(0xFFA55BE0)),
                    listOf(Color(0xFFA55BE0), Color(0xFF4F7FE6), Color(0xFF4F7FE6))
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { c -> Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(c)) }
                    }
                }
            }
        }

        // Patches/Scenes: the original stacked patch-card icon, not a folder.
        TopIconButton(onOpenScenes) { IconPatchesV2(size = 20.dp, color = Accent) }
        TopIconButton(onOpenSettings) {
            Icon(Icons.Default.Settings, "Settings", tint = Color(0xFFC9D1E0),
                modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Chord readout, translated from the JSX `ChordZone` (MixerPreview.jsx:110).
 * The JSX canvas is 891 x 411 px and matches 891 x 411 dp, so its fontSize values
 * are used as-is: root 28 / 24 sp, quality and bass 15 sp, ALSO chips 11.5 sp,
 * note chips 11 sp, inversion 10 sp.
 */
@Composable
private fun RowScope.ChordZoneV2(chord: ChordDisplay?) {
    MixerModuleV2(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        panelTop = PanelTop, panelBottom = PanelBottom
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {

            Box(Modifier.widthIn(min = 70.dp, max = 170.dp), contentAlignment = Alignment.BottomStart) {
                if (chord == null) {
                    Text("– – –", color = Color(0xFF4F596E), fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(chord.root, color = Color(0xFFF4F6FB), fontSize = chord.rootFontSp.sp,
                            fontWeight = FontWeight.ExtraBold, lineHeight = chord.rootFontSp.sp, maxLines = 1)
                        Text(chord.quality, color = Color(0xFFC9D2E4), fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold, maxLines = 1,
                            modifier = Modifier.padding(start = 1.dp, bottom = 2.dp))
                        chord.bass?.let {
                            Text("/$it", color = Color(0xFF8FB2E8), fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold, maxLines = 1,
                                modifier = Modifier.padding(start = 3.dp, bottom = 2.dp))
                        }
                    }
                }
            }

            Box(Modifier.width(1.dp).fillMaxHeight(0.72f).background(Color(0x14FFFFFF)))

            if (chord == null) {
                Text("Waiting for notes…", color = Color(0xFF566075), fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
            } else {
                Column(Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                    if (chord.also.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            chord.also.forEach { alt ->
                                Box(Modifier.clip(RoundedCornerShape(7.dp))
                                        .background(Color(0x14FFFFFF))
                                        .padding(horizontal = 7.dp, vertical = 1.dp)) {
                                    Text(alt, color = TextMain, fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold, maxLines = 1)
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        chord.notes.forEach { n ->
                            // JSX: minWidth 20, height 18. lineHeight is pinned so the
                            // inherited Material3 24 sp line height cannot push the glyph
                            // off centre inside the 18 dp chip.
                            Box(Modifier.widthIn(min = 20.dp).height(18.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x298FB2E8))
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center) {
                                Text(n, color = Color(0xFFB9CDF2), fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold, maxLines = 1,
                                    lineHeight = 11.sp)
                            }
                        }
                        Text(chord.inversion, color = Color(0xFF6F7A90), fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1,
                            modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

/**
 * TRANS / OCT case, translated from the JSX `Stepper` (MixerPreview.jsx:97): the
 * value and its label only. The round +/- buttons are gone; the case stays
 * tappable on either side of the value so it can still be stepped.
 */
@Composable
private fun StepperV2(
    label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit
) {
    MixerModuleV2(
        modifier = Modifier.width(118.dp).fillMaxHeight(),
        panelTop = PanelTop, panelBottom = PanelBottom
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            RoundStepV2(size = 24.dp, onClick = onMinus) {
                StepSignV2(vertical = false)
            }
            Column(Modifier.padding(horizontal = 2.dp).weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(label, color = TextDim, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp, lineHeight = 10.sp)
                Text(value, color = Color(0xFFF1F4FA), fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
            }
            RoundStepV2(size = 24.dp, onClick = onPlus) {
                StepSignV2(vertical = true)
            }
        }
    }
}

/** Thin +/− glyph for the stepper caps: rounded strokes, soft shadow, no outline. */
@Composable
private fun StepSignV2(vertical: Boolean) {
    Canvas(Modifier.size(11.dp)) {
        val c = center
        val r = size.minDimension / 2f
        val stroke = 2.2.dp.toPx()
        drawLine(Color.Black.copy(alpha = 0.35f), Offset(c.x - r, c.y + 1f), Offset(c.x + r, c.y + 1f),
            strokeWidth = stroke, cap = StrokeCap.Round)
        if (vertical) {
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(c.x + 1f, c.y - r), Offset(c.x + 1f, c.y + r),
                strokeWidth = stroke, cap = StrokeCap.Round)
        }
        drawLine(Color(0xFFE3E8F2), Offset(c.x - r, c.y), Offset(c.x + r, c.y),
            strokeWidth = stroke, cap = StrokeCap.Round)
        if (vertical) {
            drawLine(Color(0xFFE3E8F2), Offset(c.x, c.y - r), Offset(c.x, c.y + r),
                strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun TopIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    MixerModuleV2(
        modifier = Modifier.size(44.dp),
        panelTop = PanelTop, panelBottom = PanelBottom
    ) {
        Box(Modifier.fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
            contentAlignment = Alignment.Center) { content() }
    }
}
