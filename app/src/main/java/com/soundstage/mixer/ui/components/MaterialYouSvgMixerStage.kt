package com.soundstage.mixer.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.R
import com.soundstage.mixer.model.TrackChannel

/**
 * Exact colors from SVG mixer_material_you.svg
 */
private val SvgTrackAccents = listOf(
    Color(0xFFFFD86B), // Voie 1
    Color(0xFFB4EC6E), // Voie 2
    Color(0xFF6FE3A6), // Voie 3
    Color(0xFF5CD9F0), // Voie 4
    Color(0xFF8FA9FF), // Voie 5
    Color(0xFFC6A0FF), // Voie 6
    Color(0xFFFF9FD2), // Voie 7
    Color(0xFFFF9F92)  // Voie 8
)

private const val DESIGN_W = 2340f
private const val DESIGN_H = 1080f

/**
 * MaterialYouSvgMixerStage:
 * Direct 1:1 injection of mixer_material_you_hires.png with mathematical
 * coordinates from mixer_material_you.svg for all interactive and dynamic components.
 */
@Composable
fun MaterialYouSvgMixerStage(
    tracks: List<TrackChannel>,
    masterTrack: TrackChannel,
    onVolumeChange: (Int, Float) -> Unit,
    onPanChange: (Int, Float) -> Unit,
    onMuteClick: (Int) -> Unit,
    onSoloClick: (Int) -> Unit,
    onTrackNameClick: (Int) -> Unit,
    onFxClick: (Int) -> Unit,
    // Top bar
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
    // Bottom bar
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
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val containerW = maxWidth.value
        val containerH = maxHeight.value
        // When keyboard is visible, prioritize width-scale so mixer stays full width instead of shrinking into a tiny box
        val baseScale = if (isKeyboardVisible) {
            containerW / DESIGN_W
        } else {
            minOf(containerW / DESIGN_W, containerH / DESIGN_H)
        }
        val scale = baseScale.coerceAtLeast(0.2f)

        val stageW = (DESIGN_W * scale).dp
        val stageH = (DESIGN_H * scale).dp

        val density = LocalDensity.current
        val densityScale = scale
        val scalePx = with(density) { scale.dp.toPx() }

        fun sx(svgX: Float): androidx.compose.ui.unit.Dp = (svgX * densityScale).dp
        fun sy(svgY: Float): androidx.compose.ui.unit.Dp = (svgY * densityScale).dp
        fun sSize(svgDim: Float): androidx.compose.ui.unit.Dp = (svgDim * densityScale).dp
        fun sSp(svgFontSize: Float): androidx.compose.ui.unit.TextUnit = (svgFontSize * densityScale * 0.92f).sp

        val infiniteTransition = rememberInfiniteTransition(label = "svg_pulse")
        val recPulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(550),
                repeatMode = RepeatMode.Reverse
            ),
            label = "recPulse"
        )

        Box(
            modifier = Modifier
                .size(stageW, stageH)
                .clip(RoundedCornerShape(sSize(54f)))
        ) {
            // 1. HIGH-RES BACKGROUND IMAGE (Pixel-perfect render from SVG)
            Image(
                painter = painterResource(id = R.drawable.mixer_material_you_hires),
                contentDescription = "Mixeur 8 voies — Material You",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )

            // -------------------------------------------------------------
            // 2. TOP BAR INTERACTIVE OVERLAYS
            // -------------------------------------------------------------

            // Transpose - (x: 40, y: 35, w: 76, h: 103)
            Box(
                modifier = Modifier
                    .offset(x = sx(40f), y = sy(35f))
                    .size(width = sx(76f), height = sy(103f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTransposeChange(transpose - 1) }
            )

            // Transpose Value (x: 116, y: 35, w: 148, h: 103)
            if (transpose != 0) {
                Box(
                    modifier = Modifier
                        .offset(x = sx(116f), y = sy(35f))
                        .size(width = sx(148f), height = sy(103f))
                        .background(Color(0xFF0B0C11)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (transpose > 0) "+$transpose" else "$transpose",
                        fontSize = sSp(38f),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE4E1EC)
                    )
                }
            }

            // Transpose + (x: 264, y: 35, w: 76, h: 103)
            Box(
                modifier = Modifier
                    .offset(x = sx(264f), y = sy(35f))
                    .size(width = sx(76f), height = sy(103f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTransposeChange(transpose + 1) }
            )

            // Octave - (x: 359, y: 35, w: 76, h: 103)
            Box(
                modifier = Modifier
                    .offset(x = sx(359f), y = sy(35f))
                    .size(width = sx(76f), height = sy(103f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onOctaveChange(octave - 1) }
            )

            // Octave Value (x: 435, y: 35, w: 148, h: 103)
            if (octave != 0) {
                Box(
                    modifier = Modifier
                        .offset(x = sx(435f), y = sy(35f))
                        .size(width = sx(148f), height = sy(103f))
                        .background(Color(0xFF0B0C11)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (octave > 0) "+$octave" else "$octave",
                        fontSize = sSp(38f),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE4E1EC)
                    )
                }
            }

            // Octave + (x: 583, y: 35, w: 76, h: 103)
            Box(
                modifier = Modifier
                    .offset(x = sx(583f), y = sy(35f))
                    .size(width = sx(76f), height = sy(103f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onOctaveChange(octave + 1) }
            )

            // Central Display (x: 678, y: 35, w: 790, h: 103)
            if (detectedChord != null) {
                Box(
                    modifier = Modifier
                        .offset(x = sx(678f), y = sy(35f))
                        .size(width = sx(790f), height = sy(103f))
                        .clip(RoundedCornerShape(sSize(36f)))
                        .background(Color(0xFF0B0C11)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = detectedChord.primaryName,
                        fontSize = sSp(40f),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE4E1EC),
                        letterSpacing = (2 * scale).sp
                    )
                }
            }

            // Sustain (x: 1487, y: 35, w: 203, h: 103)
            Box(
                modifier = Modifier
                    .offset(x = sx(1487f), y = sy(35f))
                    .size(width = sx(203f), height = sy(103f))
                    .clip(RoundedCornerShape(sSize(36f)))
                    .clickable { onToggleSustain() }
            ) {
                if (isSustainActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x35B9C3FF))
                            .border(1.5.dp, Color(0xFFB9C3FF), RoundedCornerShape(sSize(36f)))
                    )
                    // Active LED indicator
                    Box(
                        modifier = Modifier
                            .offset(x = sx(1520f - 1487f - 10f), y = sy(87f - 35f - 10f))
                            .size(sSize(20f))
                            .clip(CircleShape)
                            .background(Color(0xFFB9C3FF))
                    )
                }
            }

            // Crayon / Notes (x: 1709, y: 35, w: 103, h: 103)
            Box(
                modifier = Modifier
                    .offset(x = sx(1709f), y = sy(35f))
                    .size(width = sx(103f), height = sy(103f))
                    .clip(RoundedCornerShape(sSize(36f)))
                    .clickable { onOpenNotes() }
            )

            // Drum Pads (x: 1831, y: 35, w: 103, h: 103)
            Box(
                modifier = Modifier
                    .offset(x = sx(1831f), y = sy(35f))
                    .size(width = sx(103f), height = sy(103f))
                    .clip(RoundedCornerShape(sSize(36f)))
                    .clickable { onOpenDrumPad() }
            )

            // Panic / Mute all (cx: 2005, cy: 87, r: 52 -> bounds x: 1953, y: 35, w: 104, h: 104)
            Box(
                modifier = Modifier
                    .offset(x = sx(1953f), y = sy(35f))
                    .size(width = sx(104f), height = sy(104f))
                    .clip(CircleShape)
                    .clickable { onPanic() }
            )

            // Menu / Scenes (x: 2075, y: 35, w: 103, h: 103)
            Box(
                modifier = Modifier
                    .offset(x = sx(2075f), y = sy(35f))
                    .size(width = sx(103f), height = sy(103f))
                    .clip(RoundedCornerShape(sSize(36f)))
                    .clickable { onOpenScenes() }
            )

            // Settings / Nuances (x: 2197, y: 35, w: 103, h: 103)
            Box(
                modifier = Modifier
                    .offset(x = sx(2197f), y = sy(35f))
                    .size(width = sx(103f), height = sy(103f))
                    .clip(RoundedCornerShape(sSize(36f)))
                    .clickable { onOpenSettings() }
            )

            // -------------------------------------------------------------
            // 3. THE 8 CHANNELS / FADERS
            // -------------------------------------------------------------
            tracks.take(8).forEachIndexed { index, track ->
                val trackX = 40f + index * 284f
                val accentColor = SvgTrackAccents.getOrElse(index) { Color(0xFFB9C3FF) }

                // Preset Name (x: trackX + 11, y: 166, w: 249, h: 62)
                Box(
                    modifier = Modifier
                        .offset(x = sx(trackX + 11f), y = sy(166f))
                        .size(width = sx(249f), height = sy(62f))
                        .clip(RoundedCornerShape(sSize(20f)))
                        .clickable { onTrackNameClick(track.id) }
                ) {
                    if (track.soundfontName.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0B0C11)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = track.soundfontName.take(14),
                                fontSize = sSp(24f),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE4E1EC),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Pan Knob (bounds x: trackX + 16, y: 245, w: 62, h: 62)
                Box(
                    modifier = Modifier
                        .offset(x = sx(trackX + 16f), y = sy(245f))
                        .size(width = sx(62f), height = sy(62f))
                        .clip(CircleShape)
                        .pointerInput(track.id) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val delta = dragAmount.x / 100f
                                onPanChange(track.id, (track.pan + delta).coerceIn(-1f, 1f))
                            }
                        }
                ) {
                    if (Math.abs(track.pan) > 0.04f) {
                        // Dynamic Pan pointer line
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(track.pan * 135f),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = sSize(6f))
                                    .size(width = sSize(3.5f), height = sSize(14f))
                                    .clip(RoundedCornerShape(sSize(2f)))
                                    .background(accentColor)
                            )
                        }
                    }
                }

                // Solo S (x: trackX + 88, y: 245, w: 81, h: 61, rx: 20)
                Box(
                    modifier = Modifier
                        .offset(x = sx(trackX + 88f), y = sy(245f))
                        .size(width = sx(81f), height = sy(61f))
                        .clip(RoundedCornerShape(sSize(20f)))
                        .clickable { onSoloClick(track.id) }
                ) {
                    if (track.isSolo) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(accentColor)
                                .border(1.dp, Color.White, RoundedCornerShape(sSize(20f))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "S",
                                fontSize = sSp(27f),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF101118)
                            )
                        }
                    }
                }

                // Mute M (x: trackX + 176, y: 245, w: 81, h: 61, rx: 20)
                Box(
                    modifier = Modifier
                        .offset(x = sx(trackX + 176f), y = sy(245f))
                        .size(width = sx(81f), height = sy(61f))
                        .clip(RoundedCornerShape(sSize(20f)))
                        .clickable { onMuteClick(track.id) }
                ) {
                    if (track.isMuted) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFE53935))
                                .border(1.dp, Color.White, RoundedCornerShape(sSize(20f))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "M",
                                fontSize = sSp(27f),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // ---------------------------------------------------------
                // FADER: Slot Travel (Top: 340, Bottom: 717, Travel: 377)
                // ---------------------------------------------------------
                val topY = 340f
                val bottomY = 717f
                val travel = bottomY - topY
                val handleY = bottomY - (track.volume.coerceIn(0f, 1f) * travel)

                // 1. Pristine clean slot redraw with exact SVG geometry & graduation
                Box(
                    modifier = Modifier
                        .offset(x = sx(trackX + 117f), y = sy(328f))
                        .size(width = sx(38f), height = sy(487f))
                        .clip(RoundedCornerShape(sSize(19f)))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF08090D), Color(0xFF0E0F14))
                            )
                        )
                ) {
                    // Inner dark slot line (x: 171, y: 340, w: 10, h: 463)
                    Box(
                        modifier = Modifier
                            .offset(x = sx(14f), y = sy(12f))
                            .size(width = sx(10f), height = sy(463f))
                            .clip(RoundedCornerShape(sSize(5f)))
                            .background(Color(0xFF15161C))
                    )
                }

                // 2. Active illuminated LED column inside slot up to handle position
                val fillHeight = (800f - (handleY + 43f)).coerceAtLeast(0f)
                if (fillHeight > 0f) {
                    Box(
                        modifier = Modifier
                            .offset(x = sx(trackX + 131f), y = sy(handleY + 43f))
                            .size(width = sx(10f), height = sy(fillHeight))
                            .clip(RoundedCornerShape(sSize(5f)))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(accentColor, accentColor.copy(alpha = 0.45f))
                                )
                            )
                    )
                }

                // 3. Dynamic Moving Fader Bonnet with animated shadow & glow aura
                // Drop shadow underneath bonnet (dy = 6)
                Box(
                    modifier = Modifier
                        .offset(x = sx(trackX + 100f), y = sy(handleY + 6f))
                        .size(width = sx(72f), height = sy(86f))
                        .background(
                            Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(sSize(16f))
                        )
                )

                // Dynamic Radial Glow Halo radiating around the bonnet cap
                val vuActivity = maxOf(track.peakMeterL, track.peakMeterR).coerceIn(0f, 1f)
                val glowAlpha = (0.35f + vuActivity * 0.45f).coerceIn(0.2f, 0.9f)
                Canvas(
                    modifier = Modifier
                        .offset(x = sx(trackX + 76f), y = sy(handleY - 10f))
                        .size(width = sx(120f), height = sy(106f))
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.55f * glowAlpha),
                                accentColor.copy(alpha = 0.15f * glowAlpha),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }

                // Bonnet Body (width 72, height 86, rx 16)
                Box(
                    modifier = Modifier
                        .offset(x = sx(trackX + 100f), y = sy(handleY))
                        .size(width = sx(72f), height = sy(86f))
                        .clip(RoundedCornerShape(sSize(16f)))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF454651),
                                    Color(0xFF363742),
                                    Color(0xFF2A2B34)
                                )
                            )
                        )
                ) {
                    // Bevel highlight
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x0CFFFFFF))
                    )

                    // Glow halo around the LED strip
                    Box(
                        modifier = Modifier
                            .offset(x = sx(7f), y = sy(34.5f))
                            .size(width = sx(58f), height = sy(17f))
                            .clip(RoundedCornerShape(sSize(8.5f)))
                            .background(accentColor.copy(alpha = 0.40f))
                    )

                    // Center LED strip (width 50, height 11, rx 5.5)
                    Box(
                        modifier = Modifier
                            .offset(x = sx(11f), y = sy(37.5f))
                            .size(width = sx(50f), height = sy(11f))
                            .clip(RoundedCornerShape(sSize(5.5f)))
                            .background(accentColor)
                    )
                }

                // 4. Touch & Interactive Drag Area over the fader slot
                Box(
                    modifier = Modifier
                        .offset(x = sx(trackX + 80f), y = sy(320f))
                        .size(width = sx(112f), height = sy(500f))
                        .pointerInput(track.id, scalePx) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val usableTravelPx = (travel * scalePx).coerceAtLeast(10f)
                                val deltaVol = -dragAmount.y / usableTravelPx
                                val newVol = (track.volume + deltaVol).coerceIn(0f, 1f)
                                onVolumeChange(track.id, newVol)
                            }
                        }
                )

                // FX button (x: trackX + 11, y: 837, w: 178, h: 61, rx: 20)
                Box(
                    modifier = Modifier
                        .offset(x = sx(trackX + 11f), y = sy(837f))
                        .size(width = sx(178f), height = sy(61f))
                        .clip(RoundedCornerShape(sSize(20f)))
                        .clickable { onFxClick(track.id) }
                )

                // FX Indicator Square (x: trackX + 199, y: 837, w: 61, h: 61, rx: 20)
                Box(
                    modifier = Modifier
                        .offset(x = sx(trackX + 199f), y = sy(837f))
                        .size(width = sx(61f), height = sy(61f))
                        .clip(RoundedCornerShape(sSize(20f)))
                        .clickable { onFxClick(track.id) }
                )
            }

            // -------------------------------------------------------------
            // 4. BOTTOM BAR INTERACTIVE OVERLAYS
            // -------------------------------------------------------------

            // REC Button (x: 40, y: 926, w: 137, h: 120)
            Box(
                modifier = Modifier
                    .offset(x = sx(40f), y = sy(926f))
                    .size(width = sx(137f), height = sy(120f))
                    .clip(RoundedCornerShape(sSize(32f)))
                    .clickable { onToggleRecording() }
            ) {
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFB71C1C).copy(alpha = recPulseAlpha))
                            .border(2.dp, Color(0xFFFF8A80), RoundedCornerShape(sSize(32f))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "● REC",
                            fontSize = sSp(30f),
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }

            // BPM Section (x: 192, y: 926, w: 201, h: 120)
            // Minus (x: 192, w: 60)
            Box(
                modifier = Modifier
                    .offset(x = sx(192f), y = sy(926f))
                    .size(width = sx(60f), height = sy(120f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBpmChange((bpm - 1).coerceAtLeast(40)) }
            )

            // BPM Value (x: 252, w: 81)
            if (bpm != 120) {
                Box(
                    modifier = Modifier
                        .offset(x = sx(252f), y = sy(926f))
                        .size(width = sx(81f), height = sy(65f))
                        .background(Color(0xFF282932)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$bpm",
                        fontSize = sSp(38f),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE4E1EC)
                    )
                }
            }

            // Plus (x: 333, w: 60)
            Box(
                modifier = Modifier
                    .offset(x = sx(333f), y = sy(926f))
                    .size(width = sx(60f), height = sy(120f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBpmChange((bpm + 1).coerceAtMost(260)) }
            )

            // Metronome & 4/4 (x: 410, y: 926, w: 228, h: 120)
            Box(
                modifier = Modifier
                    .offset(x = sx(410f), y = sy(926f))
                    .size(width = sx(228f), height = sy(120f))
                    .clip(RoundedCornerShape(sSize(32f)))
                    .clickable { onToggleMetronome() }
            ) {
                if (isMetronomeOn) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x28B9C3FF))
                            .border(1.5.dp, Color(0xFFB9C3FF), RoundedCornerShape(sSize(32f)))
                    )
                }
            }

            // Snapshot Arm / Disquette (x: 666, y: 937, w: 97, h: 97)
            Box(
                modifier = Modifier
                    .offset(x = sx(666f), y = sy(937f))
                    .size(width = sx(97f), height = sy(97f))
                    .clip(RoundedCornerShape(sSize(26f)))
                    .clickable { onToggleSnapshotArm() }
            ) {
                if (isSnapshotArmMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFFF9800))
                            .border(1.5.dp, Color.White, RoundedCornerShape(sSize(26f)))
                    )
                }
            }

            // Snapshot Slot 0 (x: 773, y: 934, w: 182, h: 103)
            Box(
                modifier = Modifier
                    .offset(x = sx(773f), y = sy(934f))
                    .size(width = sx(182f), height = sy(103f))
                    .clip(RoundedCornerShape(sSize(26f)))
                    .clickable { onSnapshotSlotClick("slot_0") }
            ) {
                if (activeSnapshotSlot == "slot_0") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x35B9C3FF))
                            .border(2.dp, Color(0xFFB9C3FF), RoundedCornerShape(sSize(26f)))
                    )
                }
            }

            // Snapshot Slot 1 (x: 965, y: 934, w: 183, h: 103)
            Box(
                modifier = Modifier
                    .offset(x = sx(965f), y = sy(934f))
                    .size(width = sx(183f), height = sy(103f))
                    .clip(RoundedCornerShape(sSize(26f)))
                    .clickable { onSnapshotSlotClick("slot_1") }
            ) {
                if (activeSnapshotSlot == "slot_1") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x35B9C3FF))
                            .border(2.dp, Color(0xFFB9C3FF), RoundedCornerShape(sSize(26f)))
                    )
                }
            }

            // Snapshot Slot 2 (x: 1158, y: 934, w: 183, h: 103)
            Box(
                modifier = Modifier
                    .offset(x = sx(1158f), y = sy(934f))
                    .size(width = sx(183f), height = sy(103f))
                    .clip(RoundedCornerShape(sSize(26f)))
                    .clickable { onSnapshotSlotClick("slot_2") }
            ) {
                if (activeSnapshotSlot == "slot_2") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x35B9C3FF))
                            .border(2.dp, Color(0xFFB9C3FF), RoundedCornerShape(sSize(26f)))
                    )
                }
            }

            // Snapshot Slot 3 (x: 1351, y: 934, w: 183, h: 103)
            Box(
                modifier = Modifier
                    .offset(x = sx(1351f), y = sy(934f))
                    .size(width = sx(183f), height = sy(103f))
                    .clip(RoundedCornerShape(sSize(26f)))
                    .clickable { onSnapshotSlotClick("slot_3") }
            ) {
                if (activeSnapshotSlot == "slot_3") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x35B9C3FF))
                            .border(2.dp, Color(0xFFB9C3FF), RoundedCornerShape(sSize(26f)))
                    )
                }
            }

            // Snapshot Slot 4 (x: 1544, y: 934, w: 183, h: 103)
            Box(
                modifier = Modifier
                    .offset(x = sx(1544f), y = sy(934f))
                    .size(width = sx(183f), height = sy(103f))
                    .clip(RoundedCornerShape(sSize(26f)))
                    .clickable { onSnapshotSlotClick("slot_4") }
            ) {
                if (activeSnapshotSlot == "slot_4") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x35B9C3FF))
                            .border(2.dp, Color(0xFFB9C3FF), RoundedCornerShape(sSize(26f)))
                    )
                }
            }

            // Master Section (x: 1754, y: 926, w: 411, h: 120)
            // Master volume horizontal slider (x: 1800, y: 964, w: 175, h: 44)
            Box(
                modifier = Modifier
                    .offset(x = sx(1800f), y = sy(964f))
                    .size(width = sx(175f), height = sy(44f))
                    .clip(RoundedCornerShape(sSize(22f)))
                    .pointerInput(Unit, scalePx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val usableWidthPx = (175f * scalePx).coerceAtLeast(10f)
                            val deltaVol = dragAmount.x / usableWidthPx
                            onMasterVolumeChange((masterTrack.volume + deltaVol).coerceIn(0f, 1f))
                        }
                    }
            )

            // Master Limiter Toggle (x: 2020, y: 966, w: 62, h: 40)
            Box(
                modifier = Modifier
                    .offset(x = sx(2020f), y = sy(966f))
                    .size(width = sx(62f), height = sy(40f))
                    .clip(RoundedCornerShape(sSize(20f)))
                    .clickable { }
            )

            // Master FX (x: 2095, y: 955, w: 58, h: 62)
            Box(
                modifier = Modifier
                    .offset(x = sx(2095f), y = sy(955f))
                    .size(width = sx(58f), height = sy(62f))
                    .clip(RoundedCornerShape(sSize(19f)))
                    .clickable { onMasterFxClick() }
            )

            // Piano Keyboard Toggle Button (x: 2181, y: 926, w: 119, h: 120)
            Box(
                modifier = Modifier
                    .offset(x = sx(2181f), y = sy(926f))
                    .size(width = sx(119f), height = sy(120f))
                    .clip(RoundedCornerShape(sSize(32f)))
                    .clickable { onToggleKeyboard() }
            ) {
                if (isKeyboardVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x35B9C3FF))
                            .border(2.dp, Color(0xFFB9C3FF), RoundedCornerShape(sSize(32f)))
                    )
                }
            }
        }
    }
}
