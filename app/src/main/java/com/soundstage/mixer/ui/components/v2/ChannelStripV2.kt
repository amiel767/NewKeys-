package com.soundstage.mixer.ui.components.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
    track: TrackChannel, isDimmed: Boolean, accentColor: Color,
    onVolumeChange: (Float) -> Unit, onPanChange: (Float) -> Unit,
    onSoloToggle: () -> Unit, onMuteToggle: () -> Unit,
    onTrackNameClick: () -> Unit, onFxClick: () -> Unit,
    onTrackSettingsClick: () -> Unit, onPowerToggle: () -> Unit,
    onDeleteTrack: () -> Unit, modifier: Modifier = Modifier
) {
    val level = if (isDimmed) 0f else maxOf(track.peakMeterL, track.peakMeterR).coerceIn(0f, 1f)
    val auraAlpha = (0.17f + level * 0.42f) * (if (isDimmed) 0.3f else 1f)

    Box(
        modifier = modifier
            // Drop shadow under every strip case, to lift it off the panel.
            .shadow(7.dp, RoundedCornerShape(14.dp), spotColor = Color(0x8C000000), ambientColor = Color(0x59000000))
            .clip(RoundedCornerShape(14.dp)) // Fix: 14dp au lieu de 18
            .background(Brush.verticalGradient(listOf(Color(0xFF232C3D), Color(0xFF191F2B))))
            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(14.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = auraAlpha), Color.Transparent),
                center = Offset(size.width / 2f, size.height * 1.15f),
                radius = size.height * 0.75f))
            drawRect(Brush.verticalGradient(colors = listOf(
                Color.Transparent, Color.Transparent, accentColor.copy(alpha = auraAlpha * 0.55f))))
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp)) {
            // Name Plate : 22dp hauteur, font 9.5sp
            Box(
                modifier = Modifier.fillMaxWidth().height(22.dp)
                    // Relief: drop shadow below + a light 1 dp outline, flat fill.
                    .shadow(4.dp, RoundedCornerShape(8.dp),
                        spotColor = Color(0x8C000000), ambientColor = Color(0x40000000))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF20293A))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                    .drawWithContent {
                        drawContent()
                        drawLine(
                            color = Color.White.copy(alpha = 0.12f),
                            start = Offset(size.width * 0.07f, 0.5f),
                            end = Offset(size.width * 0.93f, 0.5f),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color.Black.copy(alpha = 0.30f),
                            start = Offset(size.width * 0.07f, size.height - 0.5f),
                            end = Offset(size.width * 0.93f, size.height - 0.5f),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTrackNameClick() },
                contentAlignment = Alignment.Center
            ) {
                val name = track.patchName.ifBlank { track.soundfontName.ifBlank { "—" } }
                // JSX: textShadow "0 1px 0 rgba(0,0,0,.45)" - rendered by an offset ghost Text.
                Box(contentAlignment = Alignment.Center) {
                    Text(name, color = Color(0x8C000000),
                        fontSize = if (name.length > 15) 8.5.sp else 9.5.sp,
                        fontWeight = FontWeight.ExtraBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.offset(y = 1.5.dp))
                    Text(name, color = if (isDimmed) Color(0xFF8F9AB0) else Color(0xFFF1F4FA),
                        fontSize = if (name.length > 15) 8.5.sp else 9.5.sp,
                        fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(6.dp))
            
            // Pan + S/M : hauteur 24dp
            Row(Modifier.fillMaxWidth().height(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MicroPanKnob(track.pan, onPanChange, !isDimmed, accentColor, Modifier.size(24.dp))
                KeyBtnV2("S", track.isSolo, false, accentColor, onSoloToggle, Modifier.weight(1f).fillMaxHeight())
                KeyBtnV2("M", track.isMuted, true, accentColor, onMuteToggle, Modifier.weight(1f).fillMaxHeight())
            }
            Spacer(Modifier.height(4.dp))
            
            // Fader INCHANGÉ
            FaderSliderWithVuMeter(
                value = track.volume, peakMeterL = track.peakMeterL, peakMeterR = track.peakMeterR,
                onValueChange = onVolumeChange, isMaster = false, isEnabled = !isDimmed,
                ledColor = accentColor, showTicks = true,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            
            // Bottom Row INCHANGÉ
            TrackBottomRow(
                trackColor = accentColor, active = track.isEnabled,
                onFx = onFxClick, onSettings = onTrackSettingsClick,
                onToggle = onPowerToggle, onUnload = onDeleteTrack,
                height = 22.dp, modifier = Modifier.fillMaxWidth()
            )
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
        isMute    -> Brush.verticalGradient(listOf(Color(0xFFC62828), Color(0xFF8E1B1B)))
        else      -> Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.85f), accentColor.copy(alpha = 0.60f)))
    }
    val fg = when {
        !isActive -> Color(0xFFB8C2D6)
        else      -> Color.White
    }
    val glowColor = when {
        !isActive -> Color.Transparent
        isMute    -> Color(0x70E53935)
        else      -> accentColor.copy(alpha = 0.44f)
    }
    val glowBlur = if (isActive) 10.dp else 0.dp

    Box(
        modifier = modifier
            // Shadow first: placed after the background it was drawn on top of the
            // fill, and a solo cycle (elevation 10 -> 0) left the case blank.
            .shadow(glowBlur, RoundedCornerShape(7.dp), spotColor = glowColor, ambientColor = glowColor)
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .border(1.dp, if (isActive) Color.White.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(7.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) } // Fix: 11sp
}
