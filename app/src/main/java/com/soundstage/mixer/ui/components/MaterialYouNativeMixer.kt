package com.soundstage.mixer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.R
import com.soundstage.mixer.model.LocalDynamicPalette
import com.soundstage.mixer.model.TrackChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 8 High-fidelity Material You track accent colors from the reference design.
 */
val MaterialYouTrackAccents = listOf(
    Color(0xFFFFD86B), // 1: Warm Gold / Amber
    Color(0xFFB4EC6E), // 2: Lime Acid
    Color(0xFF6FE3A6), // 3: Bright Mint
    Color(0xFF5CD9F0), // 4: Electric Cyan
    Color(0xFF8FA9FF), // 5: Sky Blue
    Color(0xFFC6A0FF), // 6: Soft Violet
    Color(0xFFFF9FD2), // 7: Tokyo Rose
    Color(0xFFFF9F92)  // 8: Coral Sunset
)

/**
 * Native Material You Responsive Mixer Stage
 * - Fills 100% of available screen width and height seamlessly
 * - Adapts fluidly without black bars or letterboxing
 * - When virtual keyboard opens, faders compress vertically without altering width or proportions
 * - Full responsive touch targets (>48dp) and responsive drag gestures
 */
@Composable
fun MaterialYouNativeMixer(
    tracks: List<TrackChannel>,
    masterTrack: TrackChannel,
    onVolumeChange: (Int, Float) -> Unit,
    onPanChange: (Int, Float) -> Unit,
    onMuteClick: (Int) -> Unit,
    onSoloClick: (Int) -> Unit,
    onTrackNameClick: (Int) -> Unit,
    onFxClick: (Int) -> Unit,
    // Top bar parameters
    transpose: Int,
    onTransposeChange: (Int) -> Unit,
    octave: Int,
    onOctaveChange: (Int) -> Unit,
    detectedChord: DetectedChord?,
    isSustainActive: Boolean,
    onToggleSustain: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenDrumPad: () -> Unit,
    onPanic: () -> Unit,
    onOpenScenes: () -> Unit,
    onOpenSettings: () -> Unit,
    // Bottom bar parameters
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    isMetronomeOn: Boolean,
    onToggleMetronome: () -> Unit,
    isSnapshotArmMode: Boolean,
    onToggleSnapshotArm: () -> Unit,
    activeSnapshotSlot: String?,
    onSnapshotSlotClick: (String) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onMasterFxClick: () -> Unit,
    isKeyboardVisible: Boolean,
    onToggleKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dynamicPalette = LocalDynamicPalette.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF151620),
                        Color(0xFF111218)
                    )
                )
            )
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // =================================================================
        // 1. TOP BAR (FULL WIDTH, RESPONSIVE, 38dp)
        // =================================================================
        MaterialYouTopBar(
            transpose = transpose,
            onTransposeChange = onTransposeChange,
            octave = octave,
            onOctaveChange = onOctaveChange,
            detectedChord = detectedChord,
            isSustainActive = isSustainActive,
            onToggleSustain = onToggleSustain,
            onOpenNotes = onOpenNotes,
            onOpenDrumPad = onOpenDrumPad,
            onPanic = onPanic,
            onOpenScenes = onOpenScenes,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // =================================================================
        // 2. 8 CHANNEL STRIPS (EQUAL WEIGHT, RESPONSIVE IN HEIGHT & WIDTH)
        // =================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayTracks = tracks.take(8)
            displayTracks.forEachIndexed { index, track ->
                val accentColor = MaterialYouTrackAccents.getOrElse(index) { Color(0xFF00E5FF) }

                MaterialYouChannelStrip(
                    track = track,
                    accentColor = accentColor,
                    onVolumeChange = { vol -> onVolumeChange(track.id, vol) },
                    onPanChange = { pan -> onPanChange(track.id, pan) },
                    onMuteClick = { onMuteClick(track.id) },
                    onSoloClick = { onSoloClick(track.id) },
                    onTrackNameClick = { onTrackNameClick(track.id) },
                    onFxClick = { onFxClick(track.id) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // =================================================================
        // 3. BOTTOM BAR (FULL WIDTH TRANSPORT, REC, BPM, SNAPSHOTS, MASTER)
        // =================================================================
        MaterialYouBottomBar(
            isRecording = isRecording,
            onToggleRecording = onToggleRecording,
            bpm = bpm,
            onBpmChange = onBpmChange,
            isMetronomeOn = isMetronomeOn,
            onToggleMetronome = onToggleMetronome,
            isSnapshotArmMode = isSnapshotArmMode,
            onToggleSnapshotArm = onToggleSnapshotArm,
            activeSnapshotSlot = activeSnapshotSlot,
            onSnapshotSlotClick = onSnapshotSlotClick,
            masterTrack = masterTrack,
            onMasterVolumeChange = onMasterVolumeChange,
            onMasterFxClick = onMasterFxClick,
            isKeyboardVisible = isKeyboardVisible,
            onToggleKeyboard = onToggleKeyboard,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        )
    }
}

/**
 * High-precision Material You Top Bar with OLED chord display and steppers
 */
@Composable
private fun MaterialYouTopBar(
    transpose: Int,
    onTransposeChange: (Int) -> Unit,
    octave: Int,
    onOctaveChange: (Int) -> Unit,
    detectedChord: DetectedChord?,
    isSustainActive: Boolean,
    onToggleSustain: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenDrumPad: () -> Unit,
    onPanic: () -> Unit,
    onOpenScenes: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barItemBg = Brush.verticalGradient(
        listOf(
            Color(0xFF2B2C36), // SVG ctl top
            Color(0xFF262730)  // SVG ctl bottom
        )
    )
    val barItemBorder = Color(0xFF1E2232)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Transpose Stepper
        StepperWidget(
            label = "TRANS",
            value = if (transpose > 0) "+$transpose" else "$transpose",
            onMinus = { onTransposeChange(-1) },
            onPlus = { onTransposeChange(1) }
        )

        // Octave Stepper
        StepperWidget(
            label = "OCT",
            value = if (octave > 0) "+$octave" else "$octave",
            onMinus = { onOctaveChange(-1) },
            onPlus = { onOctaveChange(1) }
        )

        // Center OLED Studio Chord Display (Matches SVG central screen)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0B0C11))
                .border(1.dp, Color(0xFF1E2232), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (detectedChord != null) {
                Text(
                    text = detectedChord.primaryName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFE4E1EC),
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "---",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5B6175),
                    letterSpacing = 2.sp
                )
            }
        }

        // Global Sustain Button with LED indicator
        val sustainGreen = Color(0xFF10B981)
        val sustainBg = if (isSustainActive) Color(0x2610B981) else Color(0xFF161924)
        val sustainBorder = if (isSustainActive) sustainGreen else barItemBorder

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(sustainBg)
                .border(1.dp, sustainBorder, RoundedCornerShape(10.dp))
                .clickable { onToggleSustain() }
                .padding(horizontal = 9.dp)
                .testTag("btn_global_sustain"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isSustainActive) sustainGreen else Color(0xFF4B5162))
                )
                Text(
                    text = "SUSTAIN",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSustainActive) sustainGreen else Color(0xFF7A8096)
                )
            }
        }

        // Notes Button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(barItemBg)
                .border(1.dp, barItemBorder, RoundedCornerShape(10.dp))
                .clickable { onOpenNotes() }
                .testTag("btn_notes"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_quill),
                contentDescription = "Notes",
                tint = Color(0xFFD1D5DB),
                modifier = Modifier.size(18.dp)
            )
        }

        // Drum Pad Launcher Button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(barItemBg)
                .border(1.dp, barItemBorder, RoundedCornerShape(10.dp))
                .clickable { onOpenDrumPad() }
                .testTag("btn_drum_pad"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFFF5C8A)))
                    Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFFF6FAE)))
                    Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFE84FE0)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFF472E0)))
                    Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFC026D3)))
                    Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFA855F7)))
                }
            }
        }

        // Panic Button (Midi All Notes Off)
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Color(0xFFF8B4B4))
                .border(1.dp, Color(0xFFF8B4B4), RoundedCornerShape(19.dp))
                .clickable { onPanic() }
                .testTag("btn_panic"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_midi_panic),
                contentDescription = "Panic",
                tint = Color(0xFF3D1418),
                modifier = Modifier.size(22.dp)
            )
        }

        // Scenes Button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(barItemBg)
                .border(1.dp, barItemBorder, RoundedCornerShape(10.dp))
                .clickable { onOpenScenes() }
                .testTag("btn_scene"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Scenes",
                tint = Color(0xFFD1D5DB),
                modifier = Modifier.size(18.dp)
            )
        }

        // Settings Button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(barItemBg)
                .border(1.dp, barItemBorder, RoundedCornerShape(10.dp))
                .clickable { onOpenSettings() }
                .testTag("btn_settings"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color(0xFFD1D5DB),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Reusable Stepper Widget for Transpose & Octave
 */
@Composable
private fun StepperWidget(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2B2C36), Color(0xFF262730))
                )
            )
            .border(1.dp, Color(0xFF1E2232), RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(26.dp)
                .fillMaxHeight()
                .clickable { onMinus() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "−", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB9C3FF))
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .background(Color(0xFF0B0C11)) // SVG central black cutout
                .padding(horizontal = 8.dp)
                .defaultMinSize(minWidth = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = label, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8B8C98), lineHeight = 8.sp)
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE4E1EC), lineHeight = 13.sp)
        }

        Box(
            modifier = Modifier
                .width(26.dp)
                .fillMaxHeight()
                .clickable { onPlus() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB9C3FF))
        }
    }
}

/**
 * High-Fidelity Material You Channel Strip
 * Features:
 * - Rounded Matte Card (#1E202C / #191B26)
 * - Soundfont Patch Name Pill
 * - Micro Pan Knob + Pure S / M buttons
 * - Custom Vertical Fader with real-time radial glow bloom and LED column
 * - FX button + Track power led
 */
@Composable
private fun MaterialYouChannelStrip(
    track: TrackChannel,
    accentColor: Color,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onMuteClick: () -> Unit,
    onSoloClick: () -> Unit,
    onTrackNameClick: () -> Unit,
    onFxClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnabled = track.isEnabled
    val audioActivity = if (isEnabled) maxOf(track.peakMeterL, track.peakMeterR).coerceIn(0f, 1f) else 0f
    val baseIdleAlpha = if (isEnabled) 0.30f else 0.10f
    val reactiveAlpha = (baseIdleAlpha + audioActivity * 0.70f).coerceIn(0.10f, 1.0f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF171820))
            .border(1.dp, Color(0xFF232738), RoundedCornerShape(14.dp))
            .testTag("track_${track.id}")
    ) {
        // 1. Ambient & Reactive Accent Aura at bottom of channel strip (Exact SVG col0-col7)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.55f to accentColor.copy(alpha = reactiveAlpha * 0.04f),
                        1.0f to accentColor.copy(alpha = reactiveAlpha * 0.28f)
                    )
                )
        )

        // 2. Radial floor bloom directly under the fader (Exact SVG bloom0-bloom7)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(60.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = reactiveAlpha * 0.35f),
                            accentColor.copy(alpha = reactiveAlpha * 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 3. Crisp luminous bottom LED strip accent (Exact SVG strip0-strip7)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.85f)
                .height(4.dp)
                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                .background(accentColor.copy(alpha = 0.85f))
        )

        // Strip Internal Contents
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // A. TOP SOUNDFONT / PATCH NAME PILL
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF0B0C11))
                    .border(1.dp, Color(0xFF1E2230), RoundedCornerShape(7.dp))
                    .clickable { onTrackNameClick() }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val displayName = if (track.soundfontName.isNotBlank()) {
                    track.soundfontName
                } else if (track.patchName.isNotBlank()) {
                    track.patchName
                } else {
                    "-"
                }
                Text(
                    text = displayName,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (displayName == "-") Color(0xFF555B6E) else Color(0xFFE4E1EC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // B. PAN KNOB & S / M ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pan Knob
                MaterialYouPanKnob(
                    pan = track.pan,
                    onPanChange = onPanChange,
                    isEnabled = isEnabled,
                    activeColor = accentColor,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(2.dp))

                // Solo S Button
                val isSolo = track.isSolo
                val soloBorder = if (isSolo) Color.White else Color(0xFF262A3A)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSolo) Brush.linearGradient(listOf(accentColor, accentColor))
                            else Brush.verticalGradient(listOf(Color(0xFF2B2C36), Color(0xFF262730)))
                        )
                        .border(1.dp, soloBorder, RoundedCornerShape(6.dp))
                        .clickable { onSoloClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSolo) Color(0xFF101118) else Color(0xFFE4E1EC)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Mute M Button
                val isMuted = track.isMuted
                val muteBorder = if (isMuted) Color.White else Color(0xFF262A3A)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isMuted) Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFC62828)))
                            else Brush.verticalGradient(listOf(Color(0xFF2B2C36), Color(0xFF262730)))
                        )
                        .border(1.dp, muteBorder, RoundedCornerShape(6.dp))
                        .clickable { onMuteClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "M",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE4E1EC)
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // C. RESPONSIVE CUSTOM VERTICAL FADER WITH DYNAMIC GLOW & VU METERS
            MaterialYouVerticalFader(
                value = track.volume,
                onValueChange = onVolumeChange,
                accentColor = accentColor,
                audioActivity = audioActivity,
                isEnabled = isEnabled,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(3.dp))

            // D. BOTTOM FX BUTTON & POWER INDICATOR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF1C202C))
                        .border(1.dp, Color(0xFF262A3A), RoundedCornerShape(5.dp))
                        .clickable { onFxClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "FX",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8E94A8)
                    )
                }

                Spacer(modifier = Modifier.width(3.dp))

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF1C202C))
                        .border(1.dp, Color(0xFF262A3A), RoundedCornerShape(5.dp))
                        .clickable { onFxClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isEnabled) accentColor else Color(0x33FFFFFF))
                    )
                }
            }
        }
    }
}

/**
 * Responsive Vertical Fader:
 * - Fluid height dynamically calculated via container constraints
 * - Matte slate bonnet (26x18dp) with center LED indicator
 * - Dynamic radial glow bloom around the bonnet based on track color and VU meter
 * - Active illuminated LED column in slot track
 */
@Composable
private fun MaterialYouVerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    accentColor: Color,
    audioActivity: Float,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val thumbWidth = 26.dp
    val thumbHeight = 18.dp
    val thumbHeightPx = with(density) { thumbHeight.toPx() }

    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var localValue by remember { mutableFloatStateOf(value) }

    LaunchedEffect(value) {
        if (!isDragging) {
            localValue = value.coerceIn(0f, 1f)
        }
    }

    val currentOnValueChange by rememberUpdatedState(onValueChange)

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                if (size.height > 0) {
                    containerHeightPx = size.height.toFloat()
                }
            }
            .pointerInput(isEnabled, containerHeightPx, thumbHeightPx) {
                if (!isEnabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val usableHeight = (containerHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        val newValue = (1f - ((offset.y - thumbHeightPx / 2f) / usableHeight)).coerceIn(0f, 1f)
                        localValue = newValue
                        currentOnValueChange(newValue)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val usableHeight = (containerHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        val newValue = (1f - ((change.position.y - thumbHeightPx / 2f) / usableHeight)).coerceIn(0f, 1f)
                        localValue = newValue
                        currentOnValueChange(newValue)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val usableHeightPx = (containerHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val offsetYPx = ((1f - localValue.coerceIn(0f, 1f)) * usableHeightPx).roundToInt()
        val bonnetCenterYPx = offsetYPx + thumbHeightPx / 2f

        // 1. Reference dB ticks (Matching SVG graduation bars #2A2B34)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val usableH = (size.height - thumbHeightPx).coerceAtLeast(10f)
            val tickFracs = listOf(0.25f, 0.50f, 0.75f)
            val tickColor = Color(0xFF2A2B34)

            tickFracs.forEach { frac ->
                val y = thumbHeightPx / 2f + (1f - frac) * usableH
                drawLine(
                    color = tickColor,
                    start = Offset(2.dp.toPx(), y),
                    end = Offset(w * 0.24f, y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tickColor,
                    start = Offset(w * 0.76f, y),
                    end = Offset(w - 2.dp.toPx(), y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // 2. Central Slot Track with LED Gauge Line (Matching SVG slot w=38, rx=19, rail w=10, fill0-fill7)
        Box(
            modifier = Modifier
                .width(18.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            // Dark Slot Cavity (SVG #08090D -> #0E0F14, rx=19)
            Canvas(
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight(0.96f)
            ) {
                val h = size.height
                val w = size.width
                val corner = CornerRadius(w / 2f, w / 2f)

                // Outer cavity
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF08090D), Color(0xFF0E0F14))
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, h),
                    cornerRadius = corner
                )
                // Inner rail #15161C
                val innerW = (w * 0.5f).coerceAtLeast(2f)
                val innerLeft = (w - innerW) / 2f
                drawRoundRect(
                    color = Color(0xFF15161C),
                    topLeft = Offset(innerLeft, 2.dp.toPx()),
                    size = Size(innerW, h - 4.dp.toPx()),
                    cornerRadius = CornerRadius(innerW / 2f, innerW / 2f)
                )
            }

            // Central Saturated Gauge Line rising to bonnet (SVG fill0-fill7 + glow filter)
            if (isEnabled) {
                Canvas(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight(0.96f)
                ) {
                    val h = size.height
                    val w = size.width
                    val railTopPx = (containerHeightPx - h) / 2f
                    val relativeBonnetY = (bonnetCenterYPx - railTopPx).coerceIn(2.dp.toPx(), h - 2.dp.toPx())
                    val activeHeight = (h - 2.dp.toPx()) - relativeBonnetY
                    val lineWidthPx = (w * 0.8f).coerceAtLeast(2f)
                    val lineLeft = (w - lineWidthPx) / 2f

                    if (activeHeight > 0f) {
                        // Background glow
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(accentColor.copy(alpha = 0.55f), accentColor.copy(alpha = 0.25f)),
                                startY = relativeBonnetY,
                                endY = h - 2.dp.toPx()
                            ),
                            topLeft = Offset(lineLeft - 1.dp.toPx(), relativeBonnetY),
                            size = Size(lineWidthPx + 2.dp.toPx(), activeHeight),
                            cornerRadius = CornerRadius((lineWidthPx + 2.dp.toPx()) / 2f, (lineWidthPx + 2.dp.toPx()) / 2f)
                        )
                        // Core saturated line
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(accentColor, accentColor.copy(alpha = 0.45f)),
                                startY = relativeBonnetY,
                                endY = h - 2.dp.toPx()
                            ),
                            topLeft = Offset(lineLeft, relativeBonnetY),
                            size = Size(lineWidthPx, activeHeight),
                            cornerRadius = CornerRadius(lineWidthPx / 2f, lineWidthPx / 2f)
                        )
                    }
                }
            }
        }

        // 3. Fader Bonnet Cap with dynamic radial glow halo (Matching SVG handle #454651->#363742->#2A2B34, strip0-strip7)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, offsetYPx) }
                .width(thumbWidth)
                .height(thumbHeight),
            contentAlignment = Alignment.Center
        ) {
            // Radial Glow Halo directly behind and around the bonnet (SVG filter glow)
            val glowIntensity = (0.50f + audioActivity * 0.50f).coerceIn(0.40f, 1.0f)
            Canvas(
                modifier = Modifier
                    .size(width = 46.dp, height = 34.dp)
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.60f * glowIntensity),
                            accentColor.copy(alpha = 0.18f * glowIntensity),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }

            // Matte Slate Bonnet body (SVG #handle linearGradient 3-stop + 55% drop shadow)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(5.dp), spotColor = Color(0x8C000000))
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF454651), // SVG handle top
                                Color(0xFF363742), // SVG handle mid
                                Color(0xFF2A2B34)  // SVG handle bottom
                            )
                        )
                    )
                    .border(0.8.dp, Color(0xFF2E3547), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Top white bevel highlight (SVG opacity 0.045)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x0DFFFFFF))
                )

                // Horizontal glowing indicator LED bar (SVG strip0-strip7, rx=5.5)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(accentColor)
                )
            }
        }
    }
}

/**
 * Micro Pan Knob with dynamic notch pointer and double-tap to reset
 */
@Composable
private fun MaterialYouPanKnob(
    pan: Float,
    onPanChange: (Float) -> Unit,
    isEnabled: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    var currentPan by remember(pan) { mutableFloatStateOf(pan) }
    val onPanChangeState by rememberUpdatedState(onPanChange)

    Box(
        modifier = modifier
            .pointerInput(isEnabled) {
                if (isEnabled) {
                    detectTapGestures(
                        onDoubleTap = {
                            currentPan = 0.0f
                            onPanChangeState(0.0f)
                        }
                    )
                }
            }
            .pointerInput(isEnabled) {
                if (isEnabled) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        currentPan = (currentPan - dragAmount * 0.04f).coerceIn(-1.0f, 1.0f)
                        onPanChangeState(currentPan)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension / 2f - 1f
            val innerRadius = outerRadius - 2f

            // Outer ring
            drawCircle(color = Color(0xFF0F131C), radius = outerRadius, center = center)
            drawCircle(color = Color(0x18FFFFFF), radius = outerRadius, center = center, style = Stroke(width = 1.0f))

            // Inner body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF323A4D), Color(0xFF1E2330)),
                    center = center,
                    radius = innerRadius
                ),
                radius = innerRadius,
                center = center
            )

            // Center Notch Pointer
            val angleDeg = 270f + (currentPan * 65f)
            val angleRad = (angleDeg * PI / 180f).toFloat()
            val pointerColor = if (isEnabled) activeColor else Color.DarkGray
            val endX = center.x + innerRadius * 0.85f * cos(angleRad)
            val endY = center.y + innerRadius * 0.85f * sin(angleRad)

            drawLine(
                color = pointerColor,
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 2.0f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Material You Bottom Bar with full transport controls:
 * - Animated REC button
 * - Step BPM control
 * - Metronome pill
 * - 5 Snapshots with arm button (Save floppy disk)
 * - Master volume fader & FX
 * - Retractable Virtual Keyboard toggle
 */
@Composable
private fun MaterialYouBottomBar(
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    isMetronomeOn: Boolean,
    onToggleMetronome: () -> Unit,
    isSnapshotArmMode: Boolean,
    onToggleSnapshotArm: () -> Unit,
    activeSnapshotSlot: String?,
    onSnapshotSlotClick: (String) -> Unit,
    masterTrack: TrackChannel,
    onMasterVolumeChange: (Float) -> Unit,
    onMasterFxClick: () -> Unit,
    isKeyboardVisible: Boolean,
    onToggleKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val infiniteTransition = rememberInfiniteTransition(label = "bottom_pulse")
    val recPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recPulse"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // 1. REC Button
        Box(
            modifier = Modifier
                .width(if (isRecording) 72.dp else 52.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isRecording) {
                        Color(0xFFB71C1C).copy(alpha = recPulseAlpha)
                    } else {
                        Color(0xFFE53935)
                    }
                )
                .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(10.dp))
                .clickable { onToggleRecording() }
                .testTag("btn_rec"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Text(
                    text = "REC",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }

        // 2. BPM Stepper Box
        Row(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF161924))
                .border(1.dp, Color(0xFF1E2232), RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1F2333))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val job = coroutineScope.launch {
                                    onBpmChange((bpm - 1).coerceAtLeast(40))
                                    delay(400)
                                    while (isActive) {
                                        onBpmChange((bpm - 1).coerceAtLeast(40))
                                        delay(70)
                                    }
                                }
                                tryAwaitRelease()
                                job.cancel()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "−", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
            }

            Column(
                modifier = Modifier.padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "$bpm", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, lineHeight = 13.sp)
                Text(text = "BPM", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7A8096), lineHeight = 8.sp)
            }

            Box(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1F2333))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val job = coroutineScope.launch {
                                    onBpmChange((bpm + 1).coerceAtMost(260))
                                    delay(400)
                                    while (isActive) {
                                        onBpmChange((bpm + 1).coerceAtMost(260))
                                        delay(70)
                                    }
                                }
                                tryAwaitRelease()
                                job.cancel()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
            }
        }

        // 3. Metronome Pill Button
        val metroBg by animateColorAsState(
            targetValue = if (isMetronomeOn) Color(0xFF0F394A) else Color(0xFF161924),
            label = "metro_bg"
        )
        val metroBorder by animateColorAsState(
            targetValue = if (isMetronomeOn) Color(0xFF5CD9F0) else Color(0xFF1E2232),
            label = "metro_border"
        )

        Box(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(metroBg)
                .border(1.dp, metroBorder, RoundedCornerShape(10.dp))
                .clickable { onToggleMetronome() }
                .padding(horizontal = 8.dp)
                .testTag("btn_metronome"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "METRO",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isMetronomeOn) Color(0xFF5CD9F0) else Color(0xFF94A3B8)
            )
        }

        // 4. Snapshots & Arm Button
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1D1E26)) // SVG container #1D1E26
                .border(1.dp, Color(0xFF1E2232), RoundedCornerShape(10.dp))
                .padding(horizontal = 3.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Save / Arm Floppy Disk Button (SVG #353F7A)
            val armAlpha by animateFloatAsState(
                targetValue = if (isSnapshotArmMode) 1.0f else 0.40f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "arm_blink"
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSnapshotArmMode) Color(0xFFFF2A55) else Color(0xFF353F7A))
                    .border(
                        1.dp,
                        if (isSnapshotArmMode) Color(0xFFFF2A55).copy(alpha = armAlpha) else Color(0xFF4F6BF7),
                        RoundedCornerShape(7.dp)
                    )
                    .clickable { onToggleSnapshotArm() }
                    .testTag("btn_snapshot_arm"),
                contentAlignment = Alignment.Center
            ) {
                FloppyDiskIcon(
                    isArmed = isSnapshotArmMode,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 5 Snapshot Slots (SVG fill #0B0C11, text #C6C5D0)
            val slotKeys = listOf("slot_default", "slot_1", "slot_2", "slot_3", "slot_end")
            val slotNames = listOf("Défaut", "Snap 1", "Snap 2", "Snap 3", "END")

            slotKeys.forEachIndexed { i, slotKey ->
                val isActive = activeSnapshotSlot == slotKey
                val slotBg = if (isActive) Color(0xFF1B1E2B) else Color(0xFF0B0C11)
                val slotBorder = if (isActive) Color(0xFFB9C3FF) else Color(0xFF1E2232)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(slotBg)
                        .border(1.dp, slotBorder, RoundedCornerShape(6.dp))
                        .clickable { onSnapshotSlotClick(slotKey) }
                        .testTag("btn_snapshot_$slotKey"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = slotNames[i],
                        fontSize = 8.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) Color.White else Color(0xFFC6C5D0),
                        maxLines = 1
                    )
                }
            }
        }

        // 5. Master Fader & FX
        Row(
            modifier = Modifier
                .width(160.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF12141E))
                .border(1.dp, Color(0xFF1E2232), RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("M", color = Color(0xFF8E95A5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(3.dp))
            Slider(
                value = masterTrack.volume,
                onValueChange = onMasterVolumeChange,
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFA4B8FF),
                    activeTrackColor = Color(0xFFA4B8FF),
                    inactiveTrackColor = Color(0xFF1E2238)
                )
            )
            Spacer(modifier = Modifier.width(3.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF161C28))
                    .border(1.dp, Color(0xFF2C3242), RoundedCornerShape(6.dp))
                    .clickable { onMasterFxClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("FX", color = Color(0xFF8E94A8), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 6. Retractable Piano Keyboard Toggle Button
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isKeyboardVisible) Color(0x35B9C3FF) else Color(0xFF1C202C))
                .border(
                    1.5.dp,
                    if (isKeyboardVisible) Color(0xFFB9C3FF) else Color(0xFF2E344A),
                    RoundedCornerShape(10.dp)
                )
                .clickable { onToggleKeyboard() }
                .testTag("btn_toggle_keyboard"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(24.dp, 20.dp)) {
                val w = size.width
                val h = size.height
                val radius = 3.dp.toPx()

                // White piano key base
                drawRoundRect(
                    color = Color(0xFFE2E8F0),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(radius, radius)
                )

                // 3 thin gray separator lines for the 4 white keys
                val keyW = w / 4f
                for (i in 1..3) {
                    drawLine(
                        color = Color(0xFF94A3B8),
                        start = Offset(i * keyW, 0f),
                        end = Offset(i * keyW, h),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // 3 black suspended piano keys with rounded bottom
                val blackKeyW = keyW * 0.58f
                val blackKeyH = h * 0.58f
                val blackKeyCorner = CornerRadius(1.dp.toPx(), 1.dp.toPx())

                val blackKeyPositions = listOf(
                    keyW - blackKeyW / 2f,
                    2f * keyW - blackKeyW / 2f,
                    3f * keyW - blackKeyW / 2f
                )

                blackKeyPositions.forEach { xPos ->
                    drawRoundRect(
                        color = Color(0xFF0F172A),
                        topLeft = Offset(xPos, 0f),
                        size = Size(blackKeyW, blackKeyH),
                        cornerRadius = blackKeyCorner
                    )
                }
            }
        }
    }
}
