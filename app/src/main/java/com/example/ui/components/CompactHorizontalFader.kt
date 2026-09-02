package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TrackChannel
import com.example.ui.theme.*

/**
 * Compact Horizontal Fader Strip used when Virtual Piano Keyboard is expanded on screen.
 * Seamlessly exposes Volume Slider, M/S, Pan, Meter, and Track Name in a compact format.
 */
@Composable
fun CompactHorizontalFaderStrip(
    track: TrackChannel,
    onVolumeChange: (Float) -> Unit,
    onPowerToggle: () -> Unit = {},
    onPanChange: (Float) -> Unit = {},
    onMuteSoloClick: () -> Unit = {},
    onTrackNameClick: () -> Unit = {},
    onFxClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isMaster = track.isMaster
    val isEnabled = track.isEnabled
    val (baseColor, vibrantLedColor) = rememberDynamicFaderHue(track.id)

    val audioActivity = if (isEnabled) maxOf(track.peakMeterL, track.peakMeterR).coerceIn(0f, 1f) else 0f
    val auraAlpha = (0.25f + audioActivity * 0.60f).coerceIn(0.25f, 0.85f)

    Row(
        modifier = modifier
            .width(228.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1B202D))
            .background(
                Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.7f to vibrantLedColor.copy(alpha = auraAlpha * 0.25f),
                    1.0f to vibrantLedColor.copy(alpha = auraAlpha * 0.55f)
                )
            )
            .border(1.dp, if (isMaster) Color(0x6622D3EE) else Color(0x1EFFFFFF), RoundedCornerShape(10.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp)
            .testTag("compact_track_${track.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // Track ID & Name Column
        Column(
            modifier = Modifier
                .width(62.dp)
                .clickable { if (!isMaster) onTrackNameClick() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) vibrantLedColor else Color(0xFF64748B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isMaster) "M" else "${track.id}",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 10.sp,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                includeFontPadding = false
                            )
                        )
                    )
                }
                Text(
                    text = if (isMaster) "MASTER" else "Piste ${track.id}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) TextPrimary else TextDim2,
                    maxLines = 1
                )
            }

            val patchText = if (isMaster) "Main Out" else track.patchName.ifEmpty { track.soundfontName.ifEmpty { "-" } }
            Text(
                text = patchText,
                fontSize = 8.sp,
                color = TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Horizontal Volume Slider & VU Meter
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(track.volume * 100).toInt()}%",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = vibrantLedColor
                )

                // Mini Stereo VU Meter
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF0F172A))
                    ) {
                        if (track.peakMeterL > 0.05f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(track.peakMeterL.coerceIn(0f, 1f))
                                    .background(vibrantLedColor)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF0F172A))
                    ) {
                        if (track.peakMeterR > 0.05f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(track.peakMeterR.coerceIn(0f, 1f))
                                    .background(vibrantLedColor)
                            )
                        }
                    }
                }
            }

            Slider(
                value = track.volume,
                onValueChange = onVolumeChange,
                enabled = isEnabled,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = vibrantLedColor,
                    inactiveTrackColor = Color(0x33FFFFFF)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )
        }

        // Mute / Solo Button
        if (!isMaster) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            track.isSolo -> SoloAmber
                            track.isMuted -> MuteRed
                            else -> Color(0x1AFFFFFF)
                        }
                    )
                    .clickable { onMuteSoloClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        track.isSolo -> "S"
                        track.isMuted -> "M"
                        else -> "·"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (track.isSolo || track.isMuted) Color.White else TextDim
                )
            }
        }
    }
}

/**
 * Compact Rack of horizontal fader channels shown when virtual piano keyboard is visible.
 */
@Composable
fun CompactHorizontalFadersRack(
    tracks: List<TrackChannel>,
    masterTrack: TrackChannel,
    onVolumeChange: (Int, Float) -> Unit,
    onPowerToggle: (Int) -> Unit,
    onPanChange: (Int, Float) -> Unit,
    onMuteSoloClick: (Int) -> Unit,
    onTrackNameClick: (Int) -> Unit,
    onFxClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(tracks, key = { it.id }) { track ->
            CompactHorizontalFaderStrip(
                track = track,
                onVolumeChange = { onVolumeChange(track.id, it) },
                onPowerToggle = { onPowerToggle(track.id) },
                onPanChange = { onPanChange(track.id, it) },
                onMuteSoloClick = { onMuteSoloClick(track.id) },
                onTrackNameClick = { onTrackNameClick(track.id) },
                onFxClick = { onFxClick(track.id) }
            )
        }

        item(key = "master") {
            CompactHorizontalFaderStrip(
                track = masterTrack,
                onVolumeChange = { onVolumeChange(0, it) },
                onTrackNameClick = {},
                onMuteSoloClick = {}
            )
        }
    }
}
