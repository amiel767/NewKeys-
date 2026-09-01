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
    
    // Audio Engine
    audioEngine: String,
    onSelectAudioEngine: (String) -> Unit,
    audioBufferSize: Int,
    onSelectBufferSize: (Int) -> Unit,
    polyphony: Int,
    onSelectPolyphony: (Int) -> Unit,
    isLowLatency: Boolean,
    onToggleLowLatency: () -> Unit,
    
    // Theme & Language
    currentTheme: AppTheme = AppTheme.CYBER_NEON,
    onSelectTheme: (AppTheme) -> Unit = {},
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
    velocityMin: Float,
    velocityMax: Float,
    onVelocityRangeChange: (Float, Float) -> Unit,
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
                    "audio" -> {
                        AudioEngineSubPage(
                            audioEngine = audioEngine,
                            onSelectAudioEngine = onSelectAudioEngine,
                            audioBufferSize = audioBufferSize,
                            onSelectBufferSize = onSelectBufferSize,
                            polyphony = polyphony,
                            onSelectPolyphony = onSelectPolyphony,
                            isLowLatency = isLowLatency,
                            onToggleLowLatency = onToggleLowLatency,
                            onBack = { onNavigateSubPage("main") }
                        )
                    }
                    "themes" -> {
                        ThemeSelectorSubPage(
                            currentTheme = currentTheme,
                            onSelectTheme = onSelectTheme,
                            onBack = { onNavigateSubPage("main") }
                        )
                    }
                    else -> {
                        AospMainSettingsPage(
                            onClose = onClose,
                            onNavigateSubPage = onNavigateSubPage,
                            midiDevices = midiDevices,
                            audioEngine = audioEngine,
                            audioBufferSize = audioBufferSize,
                            currentTheme = currentTheme,
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
    audioEngine: String,
    audioBufferSize: Int,
    currentTheme: AppTheme,
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
            // CARD 1: SOUNDGOODIZER & MASTER PROCESSING
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

                        // Spatial Widener
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Stéréo 3D ${(spatialWidener * 100).roundToInt()}%",
                                fontSize = 10.sp,
                                color = TextDim,
                                modifier = Modifier.width(90.dp)
                            )
                            Slider(
                                value = spatialWidener,
                                onValueChange = onSpatialWidenerChange,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFFA855F7), activeTrackColor = Color(0xFFA855F7)),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Master Punch
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Punch ${(masterPunch * 100).roundToInt()}%",
                                fontSize = 10.sp,
                                color = TextDim,
                                modifier = Modifier.width(90.dp)
                            )
                            Slider(
                                value = masterPunch,
                                onValueChange = onMasterPunchChange,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFFF97316), activeTrackColor = Color(0xFFF97316)),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // CARD 2: CONNECTIVITÉ & PÉRIPHÉRIQUES (AOSP STYLE)
            item {
                AospCard(title = "Périphériques & Connectivité") {
                    Column {
                        AospSettingItem(
                            iconText = "🎹",
                            iconBg = Color(0xFF10B981),
                            title = "USB MIDI",
                            subtitle = "Détection matérielle active (${midiDevices.count { it.isConnected }} connectés)",
                            onClick = { onNavigateSubPage("midi") }
                        )

                        AospDivider()

                        AospSettingItem(
                            iconText = "⚡",
                            iconBg = Color(0xFF06B6D4),
                            title = "Moteur Audio & Latence",
                            subtitle = "$audioEngine · Tampon $audioBufferSize frames (~4.2ms)",
                            onClick = { onNavigateSubPage("audio") }
                        )
                    }
                }
            }

            // CARD 3: PERSONNALISATION & APPARENCE (MATERIAL YOU)
            item {
                AospCard(title = "Personnalisation & Interface") {
                    Column {
                        AospSettingItem(
                            iconText = "🎨",
                            iconBg = Color(0xFF8B5CF6),
                            title = "Thème d'application",
                            subtitle = currentTheme.displayName,
                            onClick = { onNavigateSubPage("themes") }
                        )

                        AospDivider()

                        AospSettingItem(
                            iconText = "🌐",
                            iconBg = Color(0xFFEC4899),
                            title = "Langue / Language",
                            subtitle = selectedLanguage,
                            onClick = {}
                        )
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

@Composable
private fun AudioEngineSubPage(
    audioEngine: String,
    onSelectAudioEngine: (String) -> Unit,
    audioBufferSize: Int,
    onSelectBufferSize: (Int) -> Unit,
    polyphony: Int,
    onSelectPolyphony: (Int) -> Unit,
    isLowLatency: Boolean,
    onToggleLowLatency: () -> Unit,
    onBack: () -> Unit
) {
    val engines = listOf("FluidSynth (Oboe High-Performance)", "FluidSynth (OpenSL ES)")
    val bufferSizes = listOf(64, 128, 256, 512)

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
            Text(text = "Moteur Audio Basse Latence", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        AospCard(title = "Moteur de Synthèse") {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                engines.forEach { eng ->
                    val isSel = eng == audioEngine
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) Color(0x3322D3EE) else Color.Transparent)
                            .clickable { onSelectAudioEngine(eng) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = eng, fontSize = 11.sp, color = if (isSel) Color.White else TextDim)
                        if (isSel) Text(text = "✓", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        AospCard(title = "Taille du Tampon Audio") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                bufferSizes.forEach { sz ->
                    val isSel = sz == audioBufferSize
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) NeonCyan else Color(0x14FFFFFF))
                            .clickable { onSelectBufferSize(sz) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$sz",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color(0xFF002233) else TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSelectorSubPage(
    currentTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
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
            Text(text = "Thèmes Material You", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(AppTheme.values()) { th ->
                val isSel = th == currentTheme
                val themeColor = when (th) {
                    AppTheme.CYBER_NEON -> NeonCyan
                    AppTheme.OBSIDIAN_GOLD -> Color(0xFFFFC247)
                    AppTheme.TOKYO_NIGHT -> Color(0xFF7AA2F7)
                    AppTheme.STUDIO_SLATE -> Color(0xFF90CAF9)
                    AppTheme.OLED_BLACK -> Color(0xFF00E5FF)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) Color(0xFF1E2C3D) else Color(0xFF1E212E))
                        .border(1.dp, if (isSel) NeonCyan else Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                        .clickable { onSelectTheme(th) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(themeColor)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = th.displayName, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = th.description, fontSize = 9.sp, color = TextDim2)
                    }
                    if (isSel) {
                        Text(text = "✓", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
