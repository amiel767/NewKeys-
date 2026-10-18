package com.soundstage.mixer.ui.components.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.TrackChannel
import com.soundstage.mixer.ui.components.FaderSliderWithVuMeter
import com.soundstage.mixer.ui.components.MicroPanKnob
import com.soundstage.mixer.ui.components.TrackBottomRow

@Composable
fun ChannelStripV2(
    track: TrackChannel,
    isDimmed: Boolean,
    accentColor: Color,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onSoloToggle: () -> Unit,
    onMuteToggle: () -> Unit,
    onTrackNameClick: () -> Unit,
    onFxClick: () -> Unit,
    onTrackSettingsClick: () -> Unit,
    onPowerToggle: () -> Unit,
    onDeleteTrack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val level = if (isDimmed) 0f else maxOf(track.peakMeterL, track.peakMeterR).coerceIn(0f, 1f)
    val auraAlpha = (0.17f + level * 0.42f) * (if (isDimmed) 0.3f else 1f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF232C3D), Color(0xFF191F2B))))
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(18.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = auraAlpha), Color.Transparent),
                center = Offset(size.width / 2f, size.height * 1.15f),
                radius = size.height * 0.75f))
            drawRect(Brush.verticalGradient(colors = listOf(
                Color.Transparent, Color.Transparent, accentColor.copy(alpha = auraAlpha * 0.55f))))
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 7.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF232C3D), Color(0xFF191F2B))))
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(10.dp))
                    .clickable { onTrackNameClick() },
                contentAlignment = Alignment.Center
            ) {
                val name = track.patchName.ifBlank { track.soundfontName.ifBlank { "—" } }
                Text(name,
                    color = if (isDimmed) Color(0xFF8F9AB0) else Color(0xFFE9EDF5),
                    fontSize = if (name.length > 15) 10.5.sp else 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth().height(30.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                MicroPanKnob(track.pan, onPanChange, !isDimmed, accentColor, Modifier.size(30.dp))
                KeyBtnV2("S", track.isSolo, false, accentColor, onSoloToggle, Modifier.weight(1f).fillMaxHeight())
                KeyBtnV2("M", track.isMuted, true, accentColor, onMuteToggle, Modifier.weight(1f).fillMaxHeight())
            }
            Spacer(Modifier.height(6.dp))
            FaderSliderWithVuMeter(
                value = track.volume,
                peakMeterL = track.peakMeterL,
                peakMeterR = track.peakMeterR,
                onValueChange = onVolumeChange,
                isMaster = false,
                isEnabled = !isDimmed,
                ledColor = accentColor,
                showTicks = true,
                modifier = Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            TrackBottomRow(
                trackColor = accentColor, active = track.isEnabled,
                onFx = onFxClick, onSettings = onTrackSettingsClick,
                onToggle = onPowerToggle, onUnload = onDeleteTrack,
                height = 24.dp, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun KeyBtnV2(
    label: String, isActive: Boolean, isMute: Boolean,
    accentColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    val bg = when {
        !isActive -> Brush.verticalGradient(listOf(Color(0xFF3B4560), Color(0xFF2D364D)))
        isMute    -> Brush.verticalGradient(listOf(Color(0xFF6A2E41), Color(0xFF4C2231)))
        else      -> Brush.verticalGradient(listOf(
            accentColor.copy(alpha = 0.85f), accentColor.copy(alpha = 0.60f)))
    }
    val fg = when {
        !isActive -> Color(0xFFB8C2D6)
        isMute    -> Color(0xFFF2B0C0)
        else      -> Color.White
    }
    val glowColor = when {
        !isActive -> Color.Transparent
        isMute    -> Color(0x70C2587A)
        else      -> accentColor.copy(alpha = 0.44f)
    }
    val glowBlur = if (isActive) 10.dp else 0.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp,
                if (isActive) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                RoundedCornerShape(9.dp))
            .shadow(glowBlur, RoundedCornerShape(9.dp), spotColor = glowColor, ambientColor = glowColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
}
