package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppTheme
import com.example.model.MidiDeviceItem
import com.example.model.SoundGoodizerMode
import com.example.ui.theme.*
import kotlin.math.roundToInt

/**
 * Authentic AOSP / Android 14+ Material You Settings Drawer:
 * - Expressive rounded cards (24dp)
 * - Material You Squircle Icon Containers with vibrant pastel & vivid accents
 * - FL Studio SoundGoodizer engine (Modes A, B, C, D) & Master Dynamics
 * - Real USB MIDI status & Audio Engine management
 */
@Composable
fun SettingsDrawer(
    isOpen: Boolean,
    onClose: () -> Unit,
    subPage: String,
    onNavigateSubPage: (String) -> Unit,
    
    // MIDI
    midiDevices: List<MidiDeviceItem>,
    onToggleMidiDevice: (String) -> Unit,
    
    // Audio Engine / Buffer & Polyphony
    audioEngine: String = "Oboe (C++)",
    onSelectAudioEngine: (String) -> Unit = {},
    audioBufferSize: Int,
    onSelectBufferSize: (Int) -> Unit,
    polyphony: Int,
    onSelectPolyphony: (Int) -> Unit,
    isLowLatency: Boolean = true,
    onToggleLowLatency: () -> Unit = {},
    
    // Language
    selectedLanguage: String,
    onSelectLanguage: (String) -> Unit,
    
    // FL SoundGoodizer & Master Processing
    soundGoodizer: Float,
    onSoundGoodizerChange: (Float) -> Unit,
    soundGoodizerMode: SoundGoodizerMode = SoundGoodizerMode.A,
    onSoundGoodizerModeChange: (SoundGoodizerMode) -> Unit = {},
    masterPunch: Float,
    onMasterPunchChange: (Float) -> Unit,
    spatialWidener: Float,
    onSpatialWidenerChange: (Float) -> Unit,
    
    // Velocity Settings
    velocityMin: Float = 0.10f,
    velocityMax: Float = 1.0f,
    onVelocityRangeChange: (Float, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(280)) + fadeIn(tween(200)),
        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(240)) + fadeOut(tween(180)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x77000000))
                .clickable { onClose() },
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 400.dp, max = 520.dp)
                    .fillMaxWidth(0.58f)
                    .shadow(32.dp, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .background(Color(0xFF141722))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .clickable(enabled = false) {}
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .testTag("settings_drawer_content")
            ) {
                when (subPage) {
                    "midi" -> {
                        MidiDevicesSubPage(
                            midiDevices = midiDevices,
                            onToggleDevice = onToggleMidiDevice,
                            onBack = { onNavigateSubPage("main") }
                        )
                    }
                    "buffer_polyphony", "audio" -> {
                        BufferPolyphonySubPage(
                            audioBufferSize = audioBufferSize,
                            onSelectBufferSize = onSelectBufferSize,
                            polyphony = polyphony,
                            onSelectPolyphony = onSelectPolyphony,
                            onBack = { onNavigateSubPage("main") }
                        )
                    }
                    "language" -> {
                        LanguageSelectorSubPage(
                            selectedLanguage = selectedLanguage,
                            onSelectLanguage = onSelectLanguage,
                            onBack = { onNavigateSubPage("main") }
                        )
                    }
                    else -> {
                        AospMainSettingsPage(
                            onClose = onClose,
                            onNavigateSubPage = onNavigateSubPage,
                            midiDevices = midiDevices,
                            audioBufferSize = audioBufferSize,
                            polyphony = polyphony,
                            selectedLanguage = selectedLanguage,
                            soundGoodizer = soundGoodizer,
                            onSoundGoodizerChange = onSoundGoodizerChange,
                            soundGoodizerMode = soundGoodizerMode,
                            onSoundGoodizerModeChange = onSoundGoodizerModeChange,
                            masterPunch = masterPunch,
                            onMasterPunchChange = onMasterPunchChange,
                            spatialWidener = spatialWidener,
                            onSpatialWidenerChange = onSpatialWidenerChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AospMainSettingsPage(
    onClose: () -> Unit,
    onNavigateSubPage: (String) -> Unit,
    midiDevices: List<MidiDeviceItem>,
    audioBufferSize: Int,
    polyphony: Int,
    selectedLanguage: String,
    soundGoodizer: Float,
    onSoundGoodizerChange: (Float) -> Unit,
    soundGoodizerMode: SoundGoodizerMode,
    onSoundGoodizerModeChange: (SoundGoodizerMode) -> Unit,
    masterPunch: Float,
    onMasterPunchChange: (Float) -> Unit,
    spatialWidener: Float,
    onSpatialWidenerChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // AOSP Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF22D3EE).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⚙️", fontSize = 16.sp)
                }
                Column {
                    Text(
                        text = "Paramètres",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✕", fontSize = 12.sp, color = TextPrimary)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CARD 1: PÉRIPHÉRIQUES & AUDIO
            item {
                AospCard(title = "Périphériques & Audio") {
                    Column {
                        AospSettingItem(
                            iconText = "🎹",
                            iconBg = Color(0xFF10B981),
                            title = "USB MIDI",
                            subtitle = "Détection matérielle (${midiDevices.count { it.isConnected }} connectés)",
                            onClick = { onNavigateSubPage("midi") }
                        )

                        AospDivider()

                        AospSettingItem(
                            iconText = "⚡",
                            iconBg = Color(0xFF06B6D4),
                            title = "Buffer & Polyphonie",
                            subtitle = "Tampon $audioBufferSize frames · Polyphonie $polyphony voix",
                            onClick = { onNavigateSubPage("buffer_polyphony") }
                        )
                    }
                }
            }

            // CARD 2: LANGUE & SYSTÈME
            item {
                AospCard(title = "Langue & Système") {
                    Column {
                        AospSettingItem(
                            iconText = "🌐",
                            iconBg = Color(0xFFEC4899),
                            title = "Langue / Language",
                            subtitle = selectedLanguage,
                            onClick = { onNavigateSubPage("language") }
                        )
                    }
                }
            }

            // CARD 3: SOUNDGOODIZER & MASTER PROCESSING
            item {
                AospCard(title = "SoundGoodizer") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Modes A, B, C, D
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Mode SoundGoodizer", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SoundGoodizerMode.values().forEach { mode ->
                                val isSel = (mode == soundGoodizerMode)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSel) {
                                                Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7)))
                                            } else {
                                                Brush.verticalGradient(listOf(Color(0x18FFFFFF), Color(0x0CFFFFFF)))
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (isSel) Color.White else Color(0x18FFFFFF),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onSoundGoodizerModeChange(mode) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSel) Color(0xFF002233) else TextPrimary
                                    )
                                }
                            }
                        }

                        // Knob Slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Intensité ${(soundGoodizer * 100).roundToInt()}%",
                                fontSize = 10.sp,
                                color = TextDim,
                                modifier = Modifier.width(90.dp)
                            )
                            Slider(
                                value = soundGoodizer,
                                onValueChange = onSoundGoodizerChange,
                                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Punch & Widener
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Punch Dynamique: ${(masterPunch * 100).roundToInt()}%",
                                    fontSize = 9.sp,
                                    color = TextDim
                                )
                                Slider(
                                    value = masterPunch,
                                    onValueChange = onMasterPunchChange,
                                    colors = SliderDefaults.colors(thumbColor = SoloAmber, activeTrackColor = SoloAmber)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Largeur Stéréo: ${(spatialWidener * 100).roundToInt()}%",
                                    fontSize = 9.sp,
                                    color = TextDim
                                )
                                Slider(
                                    value = spatialWidener,
                                    onValueChange = onSpatialWidenerChange,
                                    colors = SliderDefaults.colors(thumbColor = NeonMagenta, activeTrackColor = NeonMagenta)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AospCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = NeonCyanLight,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 5.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E212E))
                .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(20.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun AospSettingItem(
    iconText: String,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Material You Squircle Icon Container
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = iconText, fontSize = 15.sp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = TextDim2
            )
        }

        Text(text = "›", fontSize = 14.sp, color = TextDim)
    }
}

@Composable
private fun AospDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.8.dp)
            .background(Color(0x0EFFFFFF))
            .padding(horizontal = 14.dp)
    )
}

@Composable
private fun MidiDevicesSubPage(
    midiDevices: List<MidiDeviceItem>,
    onToggleDevice: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(text = "← Retour", fontSize = 10.sp, color = NeonCyan)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Périphériques USB MIDI", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        if (midiDevices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🔌", fontSize = 28.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Aucun clavier USB MIDI branché.\nBranchez un contrôleur MIDI en USB-OTG pour jouer directement.",
                        fontSize = 10.sp,
                        color = TextDim,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(midiDevices) { dev ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E212E))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = dev.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = if (dev.isConnected) "Connecté" else "Déconnecté", fontSize = 9.sp, color = if (dev.isConnected) Color(0xFF10B981) else TextDim)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (dev.isEnabled) Color(0xFF10B981) else Color(0xFF4B5563))
                                    .clickable { onToggleDevice(dev.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = if (dev.isEnabled) "✓" else "✕", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Switch(
                                checked = dev.isEnabled,
                                onCheckedChange = { onToggleDevice(dev.id) },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BufferPolyphonySubPage(
    audioBufferSize: Int,
    onSelectBufferSize: (Int) -> Unit,
    polyphony: Int,
    onSelectPolyphony: (Int) -> Unit,
    onBack: () -> Unit
) {
    val bufferSizes = listOf(64, 128, 256, 512)
    val polyphonyValues = listOf(64, 128, 256, 512)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(text = "← Retour", fontSize = 10.sp, color = NeonCyan)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Buffer & Polyphonie", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                AospCard(title = "Taille du Buffer Audio (Latence)") {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Un buffer plus petit réduit la latence au toucher du clavier. Réglez selon la puissance de l'appareil.",
                            fontSize = 9.5.sp,
                            color = TextDim
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            bufferSizes.forEach { sz ->
                                val isSel = (sz == audioBufferSize)
                                val latencyMs = when (sz) {
                                    64 -> "~1.4 ms"
                                    128 -> "~2.9 ms"
                                    256 -> "~5.8 ms"
                                    else -> "~11.6 ms"
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSel) NeonCyan else Color(0x14FFFFFF))
                                        .clickable { onSelectBufferSize(sz) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$sz",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) Color(0xFF002233) else TextPrimary
                                        )
                                        Text(
                                            text = latencyMs,
                                            fontSize = 8.sp,
                                            color = if (isSel) Color(0xFF003344) else TextDim2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                AospCard(title = "Polyphonie Maximale (Voix)") {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Nombre maximum de notes jouées simultanément par le moteur FluidSynth.",
                            fontSize = 9.5.sp,
                            color = TextDim
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            polyphonyValues.forEach { p ->
                                val isSel = (p == polyphony)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSel) Color(0xFF10B981) else Color(0x14FFFFFF))
                                        .clickable { onSelectPolyphony(p) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$p",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color(0xFF003311) else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageSelectorSubPage(
    selectedLanguage: String,
    onSelectLanguage: (String) -> Unit,
    onBack: () -> Unit
) {
    val languages = listOf(
        Pair("Français", "🇫🇷"),
        Pair("English", "🇬🇧"),
        Pair("Español", "🇪🇸"),
        Pair("Malagasy", "🇲🇬"),
        Pair("Deutsch", "🇩🇪"),
        Pair("Português", "🇧🇷")
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(text = "← Retour", fontSize = 10.sp, color = NeonCyan)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Langue / Language", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(languages) { (lang, flag) ->
                val isSel = (lang == selectedLanguage)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) Color(0xFF1E2C3D) else Color(0xFF1E212E))
                        .border(1.dp, if (isSel) NeonCyan else Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                        .clickable { onSelectLanguage(lang) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = flag, fontSize = 20.sp)
                    Text(
                        text = lang,
                        fontSize = 13.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSel) {
                        Text(text = "✓", fontSize = 14.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
