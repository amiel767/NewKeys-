package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Classical Piano Virtual Keyboard for Keyboard Splitter Zone assignment
 * Displays authentic white and black keys with Split zone highlighting,
 * note labels, and intuitive split range configuration.
 */
@Composable
fun SplitterKeyboard(
    minNote: Int,
    maxNote: Int,
    onSetSplitRange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var startNote by remember(minNote) { mutableIntStateOf(minNote) }
    var endNote by remember(maxNote) { mutableIntStateOf(maxNote) }
    var selectionMode by remember { mutableStateOf<String?>("min") } // "min" or "max"
    var baseOctave by remember { mutableIntStateOf(2) } // Starts at C2 (MIDI 36)

    // Helper to get note name from MIDI number
    fun midiToNoteName(midi: Int): String {
        val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val octave = (midi / 12) - 1
        val note = noteNames[midi % 12]
        return "$note$octave"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF14141E))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header Controls: Range info, Quick presets & Octave Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Note info indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Min Note Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectionMode == "min") Color(0x3322D3EE) else Color(0x14FFFFFF))
                        .border(
                            1.dp,
                            if (selectionMode == "min") NeonCyan else Color(0x26FFFFFF),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectionMode = "min" }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "DÉBUT (MIN)",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectionMode == "min") NeonCyan else TextDim
                        )
                        Text(
                            text = "${midiToNoteName(startNote)} ($startNote)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }
                }

                // Split Range Arrow
                Text(text = "➔", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)

                // Max Note Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectionMode == "max") Color(0x3322D3EE) else Color(0x14FFFFFF))
                        .border(
                            1.dp,
                            if (selectionMode == "max") NeonCyan else Color(0x26FFFFFF),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectionMode = "max" }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "FIN (MAX)",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectionMode == "max") NeonCyan else TextDim
                        )
                        Text(
                            text = "${midiToNoteName(endNote)} ($endNote)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Octave Shift Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x1AFFFFFF))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(6.dp))
                        .clickable(enabled = baseOctave > 1) {
                            if (baseOctave > 1) baseOctave--
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "◀ C${baseOctave - 1}", fontSize = 9.sp, color = if (baseOctave > 1) TextPrimary else TextDim2)
                }

                Text(
                    text = "Vue: C$baseOctave — C${baseOctave + 3}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x1AFFFFFF))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(6.dp))
                        .clickable(enabled = baseOctave < 5) {
                            if (baseOctave < 5) baseOctave++
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "C${baseOctave + 4} ▶", fontSize = 9.sp, color = if (baseOctave < 5) TextPrimary else TextDim2)
                }
            }
        }

        // Classical Piano Virtual Keyboard (Overlapping White and Black Keys)
        // 3 Full Octaves displayed based on baseOctave
        val octavesToShow = 3
        val startMidi = (baseOctave + 1) * 12 // e.g. baseOctave=2 -> C2 = 36
        val totalWhiteKeys = octavesToShow * 7 + 1 // 22 white keys

        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0D0D14))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                .padding(top = 4.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
                .horizontalScroll(scrollState)
        ) {
            val keyWidth = 32.dp
            val blackKeyWidth = 20.dp
            val blackKeyHeight = 78.dp

            // 1. Layer of White Keys
            Row(modifier = Modifier.height(120.dp)) {
                for (oct in 0 until octavesToShow) {
                    val octNumber = baseOctave + oct
                    val octStartMidi = (octNumber + 1) * 12
                    val whiteNotesInOctave = listOf(0 to "C", 2 to "D", 4 to "E", 5 to "F", 7 to "G", 9 to "A", 11 to "B")

                    for ((semitone, noteLetter) in whiteNotesInOctave) {
                        val midi = octStartMidi + semitone
                        val inSplitRange = midi in startNote..endNote

                        val isRootC = noteLetter == "C"

                        val whiteBrush = if (inSplitRange) {
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFE0F7FA),
                                    Color(0xFF80DEEA),
                                    Color(0xFF26C6DA)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFAFAFC),
                                    Color(0xFFECEFF1),
                                    Color(0xFFCFD8DC)
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(keyWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 0.8.dp)
                                .shadow(if (inSplitRange) 6.dp else 2.dp, RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                                .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                                .background(whiteBrush)
                                .border(
                                    1.dp,
                                    if (inSplitRange) NeonCyan else Color(0x6690A4AE),
                                    RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp)
                                )
                                .clickable {
                                    if (selectionMode == "min") {
                                        startNote = midi
                                        if (startNote > endNote) endNote = startNote
                                        selectionMode = "max"
                                    } else {
                                        endNote = midi
                                        if (endNote < startNote) startNote = endNote
                                        selectionMode = "min"
                                    }
                                    onSetSplitRange(startNote, endNote)
                                }
                                .padding(bottom = 4.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (inSplitRange && (midi == startNote || midi == endNote)) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (midi == startNote) Color(0xFF00796B) else Color(0xFFE65100))
                                    )
                                }
                                Text(
                                    text = "$noteLetter$octNumber",
                                    fontSize = 8.5.sp,
                                    fontWeight = if (isRootC || inSplitRange) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (inSplitRange) Color(0xFF004D40) else Color(0xFF37474F)
                                )
                            }
                        }
                    }
                }

                // Final C key
                val finalOctNumber = baseOctave + octavesToShow
                val finalMidi = (finalOctNumber + 1) * 12
                val inFinalSplit = finalMidi in startNote..endNote

                Box(
                    modifier = Modifier
                        .width(keyWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 0.8.dp)
                        .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                        .background(if (inFinalSplit) Brush.verticalGradient(listOf(Color(0xFFE0F7FA), Color(0xFF26C6DA))) else Brush.verticalGradient(listOf(Color(0xFFFAFAFC), Color(0xFFCFD8DC))))
                        .border(1.dp, if (inFinalSplit) NeonCyan else Color(0x6690A4AE), RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                        .clickable {
                            if (selectionMode == "min") {
                                startNote = finalMidi
                                selectionMode = "max"
                            } else {
                                endNote = finalMidi
                                selectionMode = "min"
                            }
                            onSetSplitRange(startNote, endNote)
                        }
                        .padding(bottom = 4.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = "C$finalOctNumber",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (inFinalSplit) Color(0xFF004D40) else Color(0xFF37474F)
                    )
                }
            }

            // 2. Layer of Black Keys (Accidentals: C#, D#, F#, G#, A#)
            for (oct in 0 until octavesToShow) {
                val octNumber = baseOctave + oct
                val octStartMidi = (octNumber + 1) * 12

                // Offsets relative to white key width
                val blackKeysInfo = listOf(
                    Triple(1, "C#", 0),  // after white key 0 (C)
                    Triple(3, "D#", 1),  // after white key 1 (D)
                    Triple(6, "F#", 3),  // after white key 3 (F)
                    Triple(8, "G#", 4),  // after white key 4 (G)
                    Triple(10, "A#", 5)  // after white key 5 (A)
                )

                for ((semitone, noteLetter, whiteKeyIndex) in blackKeysInfo) {
                    val midi = octStartMidi + semitone
                    val inSplitRange = midi in startNote..endNote

                    // Calculate X offset in DP
                    val totalWhiteKeyOffset = 32.dp * (oct * 7 + whiteKeyIndex + 1) - (blackKeyWidth / 2)

                    val blackBrush = if (inSplitRange) {
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF00ACC1),
                                Color(0xFF00838F),
                                Color(0xFF004D40)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF37474F),
                                Color(0xFF212121),
                                Color(0xFF000000)
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .offset(x = totalWhiteKeyOffset)
                            .width(blackKeyWidth)
                            .height(blackKeyHeight)
                            .shadow(6.dp, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(blackBrush)
                            .border(
                                1.dp,
                                if (inSplitRange) NeonCyan else Color(0x44FFFFFF),
                                RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                            )
                            .clickable {
                                if (selectionMode == "min") {
                                    startNote = midi
                                    if (startNote > endNote) endNote = startNote
                                    selectionMode = "max"
                                } else {
                                    endNote = midi
                                    if (endNote < startNote) startNote = endNote
                                    selectionMode = "min"
                                }
                                onSetSplitRange(startNote, endNote)
                            }
                            .padding(bottom = 3.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = "$noteLetter",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (inSplitRange) Color.White else Color(0xCCFFFFFF)
                        )
                    }
                }
            }
        }
    }
}
