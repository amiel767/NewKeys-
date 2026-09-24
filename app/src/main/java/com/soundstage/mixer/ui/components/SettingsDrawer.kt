package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.AppTheme
import com.soundstage.mixer.model.MaterialYouStyle
import com.soundstage.mixer.model.MidiDeviceItem
import com.soundstage.mixer.model.SoundGoodizerMode
import com.soundstage.mixer.ui.theme.*
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
    
    // Master Processing
    masterPunch: Float = 0.5f,
    onMasterPunchChange: (Float) -> Unit = {},
    spatialWidener: Float = 0.5f,
    onSpatialWidenerChange: (Float) -> Unit = {},
    
    // Screen Keep Alive
    keepScreenOn: Boolean = true,
    onToggleKeepScreenOn: () -> Unit = {},
    
    // Velocity Settings
    velocityMin: Float = 0.10f,
    velocityMax: Float = 1.0f,
    onVelocityRangeChange: (Float, Float) -> Unit = { _, _ -> },

    // Musical Notation Preference (# vs ♭)
    useFlats: Boolean = false,
    onToggleUseFlats: () -> Unit = {},

    // Theme & Palettes
    currentTheme: AppTheme = AppTheme.MATERIAL_YOU,
    onSelectTheme: (AppTheme) -> Unit = {},
    selectedPaletteColorIndex: Int = 0,
    onSelectPaletteColorIndex: (Int) -> Unit = {},
    saturation: Float = 1.0f,
    onSaturationChange: (Float) -> Unit = {},
    nuance: Float = 0.5f,
    onNuanceChange: (Float) -> Unit = {},

    // Material You Studio Controls
    materialYouStyle: MaterialYouStyle = MaterialYouStyle.VIBRANT,
    onSelectMaterialYouStyle: (MaterialYouStyle) -> Unit = {},
    accentSaturation: Float = 1.0f,
    onAccentSaturationChange: (Float) -> Unit = {},
    onResetAccentSaturation: () -> Unit = {},
    bgSaturation: Float = 1.0f,
    onBgSaturationChange: (Float) -> Unit = {},
    onResetBgSaturation: () -> Unit = {},
    bgBrightness: Float = 1.0f,
    onBgBrightnessChange: (Float) -> Unit = {},
    onResetBgBrightness: () -> Unit = {},
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
                    "theme_selection", "material_you_studio" -> {
                        NuancesSettingsSubPage(
                            accentSaturation = accentSaturation,
                            onAccentSaturationChange = onAccentSaturationChange,
                            onResetAccentSaturation = onResetAccentSaturation,
                            nuance = nuance,
                            onNuanceChange = onNuanceChange,
                            bgSaturation = bgSaturation,
                            onBgSaturationChange = onBgSaturationChange,
                            onResetBgSaturation = onResetBgSaturation,
                            bgBrightness = bgBrightness,
                            onBgBrightnessChange = onBgBrightnessChange,
                            onResetBgBrightness = onResetBgBrightness,
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
                            masterPunch = masterPunch,
                            onMasterPunchChange = onMasterPunchChange,
                            spatialWidener = spatialWidener,
                            onSpatialWidenerChange = onSpatialWidenerChange,
                            keepScreenOn = keepScreenOn,
                            onToggleKeepScreenOn = onToggleKeepScreenOn,
                            velocityMin = velocityMin,
                            velocityMax = velocityMax,
                            onVelocityRangeChange = onVelocityRangeChange,
                            useFlats = useFlats,
                            onToggleUseFlats = onToggleUseFlats,
                            currentTheme = currentTheme,
                            materialYouStyle = materialYouStyle
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AospMainSettingsPage(
    onClose: () -> Unit,
    onNavigateSubPage: (String) -> Unit,
    midiDevices: List<MidiDeviceItem>,
    audioBufferSize: Int,
    polyphony: Int,
    selectedLanguage: String,
    masterPunch: Float,
    onMasterPunchChange: (Float) -> Unit,
    spatialWidener: Float,
    onSpatialWidenerChange: (Float) -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    velocityMin: Float,
    velocityMax: Float,
    onVelocityRangeChange: (Float, Float) -> Unit,
    useFlats: Boolean = false,
    onToggleUseFlats: () -> Unit = {},
    currentTheme: AppTheme = AppTheme.MATERIAL_YOU,
    materialYouStyle: MaterialYouStyle = MaterialYouStyle.VIBRANT
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
                        .background(Color(0x1800E5FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Paramètres",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
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
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fermer",
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CARD 0: NUANCES & APPARENCE
            item {
                AospCard(title = "Apparence") {
                    AospSettingItem(
                        icon = Icons.Default.Palette,
                        iconTint = Color(0xFFFCD34D),
                        iconBg = Color(0x28FCD34D),
                        title = "Nuances & Auras",
                        subtitle = "Saturation, teinte chromatique et luminosité du châssis",
                        onClick = { onNavigateSubPage("theme_selection") }
                    )
                }
            }
            // CARD 1: PÉRIPHÉRIQUES & AUDIO
            item {
                AospCard(title = "Périphériques & Audio") {
                    Column {
                        AospSettingItem(
                            icon = Icons.Default.Usb,
                            iconTint = Color(0xFF10B981),
                            iconBg = Color(0x1810B981),
                            title = "USB MIDI",
                            subtitle = "Détection matérielle (${midiDevices.count { it.isConnected }} connectés)",
                            onClick = { onNavigateSubPage("midi") }
                        )

                        AospDivider()

                        AospSettingItem(
                            icon = Icons.Default.Tune,
                            iconTint = NeonCyan,
                            iconBg = Color(0x1800E5FF),
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
                            icon = Icons.Default.Language,
                            iconTint = Color(0xFFEC4899),
                            iconBg = Color(0x18EC4899),
                            title = "Langue / Language",
                            subtitle = selectedLanguage,
                            onClick = { onNavigateSubPage("language") }
                        )

                        AospDivider()

                        // Musical Notation Accidental Toggle (# Sharps vs ♭ Flats)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x183B82F6)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (useFlats) "♭" else "♯",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF3B82F6)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Musical Notation",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (useFlats) "Flats (♭ - Db, Eb...)" else "Sharps (♯ - C#, D#...)",
                                        fontSize = 11.sp,
                                        color = TextDim
                                    )
                                }
                            }
                            
                            Row(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF141722))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                                        .background(if (!useFlats) Color(0xFF3B82F6) else Color.Transparent)
                                        .clickable { if(useFlats) onToggleUseFlats() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("♯", color = if (!useFlats) Color.White else TextDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                                        .background(if (useFlats) Color(0xFF3B82F6) else Color.Transparent)
                                        .clickable { if(!useFlats) onToggleUseFlats() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("♭", color = if (useFlats) Color.White else TextDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        AospDivider()

                        // Keep Screen On Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleKeepScreenOn() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x1800E5FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Écran Allumé",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Écran toujours allumé",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (keepScreenOn) "Actif (empêche la mise en veille)" else "Désactivé (veille système)",
                                    fontSize = 11.sp,
                                    color = TextDim
                                )
                            }

                            Switch(
                                checked = keepScreenOn,
                                onCheckedChange = { onToggleKeepScreenOn() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = NeonCyan,
                                    uncheckedThumbColor = Color.LightGray,
                                    uncheckedTrackColor = Color(0xFF33384A)
                                )
                            )
                        }
                    }
                }
            }

            // CARD 3: DYNAMIQUE & VÉLOCITÉ
            item {
                AospCard(title = "Dynamique & Vélocité") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Velocity Min/Max
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Vélocité (Min - Max): ${(velocityMin * 127).roundToInt()} - ${(velocityMax * 127).roundToInt()}",
                                    fontSize = 9.sp,
                                    color = TextDim
                                )
                                val safeMin = minOf(velocityMin, velocityMax).coerceIn(0f, 1f)
                                val safeMax = maxOf(velocityMin, velocityMax).coerceIn(0f, 1f)
                                androidx.compose.material3.RangeSlider(
                                    value = safeMin..safeMax,
                                    onValueChange = { range -> onVelocityRangeChange(range.start, range.endInclusive) },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
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
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color = Color(0x18FFFFFF),
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
        // Material You Simple Clean Squircle Container
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
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
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = NeonCyan,
                    modifier = Modifier.size(14.dp)
                )
                Text(text = "Retour", fontSize = 10.sp, color = NeonCyan)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Périphériques USB MIDI", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        if (midiDevices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0x14FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            tint = Color(0x66FFFFFF),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
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
    val bufferSizes = listOf(64, 128, 256, 512, 1024)
    val polyphonyValues = listOf(64, 128, 256, 512)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = NeonCyan,
                    modifier = Modifier.size(14.dp)
                )
                Text(text = "Retour", fontSize = 10.sp, color = NeonCyan)
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
                                    512 -> "~11.6 ms"
                                    else -> "~23.2 ms"
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
    val languages = listOf("English", "Français", "Español")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NeonCyan,
                    modifier = Modifier.size(14.dp)
                )
                Text(text = "Back", fontSize = 10.sp, color = NeonCyan)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Language", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(languages) { lang ->
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

/**
 * Page Réglages Nuances & Couleurs (Design studio unique avec réglages fins)
 */
@Composable
private fun NuancesSettingsSubPage(
    accentSaturation: Float,
    onAccentSaturationChange: (Float) -> Unit,
    onResetAccentSaturation: () -> Unit,
    nuance: Float,
    onNuanceChange: (Float) -> Unit,
    bgSaturation: Float,
    onBgSaturationChange: (Float) -> Unit,
    onResetBgSaturation: () -> Unit,
    bgBrightness: Float,
    onBgBrightnessChange: (Float) -> Unit,
    onResetBgBrightness: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x18FFFFFF))
                    .clickable { onBack() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = Color(0xFFFCD34D),
                    modifier = Modifier.size(16.dp)
                )
                Text(text = "Retour", fontSize = 11.sp, color = Color(0xFFFCD34D))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Nuances & Auras", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "Ajustez la saturation des témoins lumineux, les nuances chromatiques et la luminosité du châssis ardoise studio.",
                    fontSize = 11.sp,
                    color = TextDim,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // Slider 1: Saturation des accents et LED
            item {
                SliderAdjustCard(
                    title = "Saturation des accents & LED",
                    value = accentSaturation,
                    onValueChange = onAccentSaturationChange,
                    onReset = onResetAccentSaturation,
                    valueRange = 0.50f..2.00f,
                    thumbColor = Color(0xFFFCD34D)
                )
            }

            // Slider 2: Nuance & Teinte chromatique
            item {
                SliderAdjustCard(
                    title = "Nuance chromatique globale",
                    value = nuance,
                    onValueChange = onNuanceChange,
                    onReset = { onNuanceChange(0.5f) },
                    valueRange = 0.00f..1.00f,
                    thumbColor = Color(0xFFA3E635)
                )
            }

            // Slider 3: Saturation de l'arrière-plan
            item {
                SliderAdjustCard(
                    title = "Saturation de l'arrière-plan",
                    value = bgSaturation,
                    onValueChange = onBgSaturationChange,
                    onReset = onResetBgSaturation,
                    valueRange = 0.50f..2.00f,
                    thumbColor = Color(0xFF38BDF8)
                )
            }

            // Slider 4: Luminosité du fond de châssis
            item {
                SliderAdjustCard(
                    title = "Luminosité du châssis ardoise",
                    value = bgBrightness,
                    onValueChange = onBgBrightnessChange,
                    onReset = onResetBgBrightness,
                    valueRange = 0.60f..1.40f,
                    thumbColor = Color(0xFFA4B8FF)
                )
            }
        }
    }
}

@Composable
private fun SplitQuadrantCircle(
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val c1 = colors.getOrElse(0) { Color(0xFFE2B4BD) }
        val c2 = colors.getOrElse(1) { Color(0xFF00E5FF) }
        val c3 = colors.getOrElse(2) { Color(0xFFF59E0B) }
        val c4 = colors.getOrElse(3) { Color(0xFF10B981) }

        // Top-left quadrant
        drawArc(
            color = c1,
            startAngle = 180f,
            sweepAngle = 90f,
            useCenter = true
        )
        // Top-right quadrant
        drawArc(
            color = c2,
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = true
        )
        // Bottom-right quadrant
        drawArc(
            color = c3,
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = true
        )
        // Bottom-left quadrant
        drawArc(
            color = c4,
            startAngle = 90f,
            sweepAngle = 90f,
            useCenter = true
        )
    }
}

@Composable
private fun SliderAdjustCard(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    thumbColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E212E))
            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "Sélection: ${String.format(java.util.Locale.US, "%.2fx", value)}",
                    fontSize = 10.sp,
                    color = thumbColor,
                    fontWeight = FontWeight.Medium
                )
            }

            // Circular Reset Button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .clickable { onReset() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Réinitialiser",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                activeTrackColor = thumbColor,
                inactiveTrackColor = Color(0x28FFFFFF)
            )
        )
    }
}
