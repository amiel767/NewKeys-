package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.AppTheme
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

    // Theme Customization
    themeHue: Float = 220f,
    onThemeHueChange: (Float) -> Unit = {},
    themeAccentSaturation: Float = 1.0f,
    onThemeAccentSaturationChange: (Float) -> Unit = {},
    themeBgSaturation: Float = 1.0f,
    onThemeBgSaturationChange: (Float) -> Unit = {},
    themeBgLightness: Float = 1.0f,
    onThemeBgLightnessChange: (Float) -> Unit = {},
    themeModulesLightness: Float = 1.0f,
    onThemeModulesLightnessChange: (Float) -> Unit = {},
    themePaletteName: String = "Midnight Slate (Défaut)",
    onSelectThemePalette: (ThemePalettePreset) -> Unit = {},
    onResetTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isOpen) {
        val scrimAlpha by animateFloatAsState(
            targetValue = 0.55f,
            animationSpec = tween(260),
            label = "settings_scrim_fade"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable { onClose() }
                .testTag("settings_drawer_scrim")
        )
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(280)) + fadeIn(tween(200)),
        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(240)) + fadeOut(tween(180)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 400.dp, max = 520.dp)
                    .fillMaxWidth(0.58f)
                    .shadow(32.dp, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .background(Color(0xFF181C28))
                    .border(1.dp, Color(0xFF2C3549), RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
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
                    "theme" -> {
                        ThemeSettingsSubPage(
                            themeHue = themeHue,
                            onThemeHueChange = onThemeHueChange,
                            themeAccentSaturation = themeAccentSaturation,
                            onThemeAccentSaturationChange = onThemeAccentSaturationChange,
                            themeBgSaturation = themeBgSaturation,
                            onThemeBgSaturationChange = onThemeBgSaturationChange,
                            themeBgLightness = themeBgLightness,
                            onThemeBgLightnessChange = onThemeBgLightnessChange,
                            themeModulesLightness = themeModulesLightness,
                            onThemeModulesLightnessChange = onThemeModulesLightnessChange,
                            onResetTheme = onResetTheme,
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
                            onToggleUseFlats = onToggleUseFlats
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
    onToggleUseFlats: () -> Unit = {}
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
                AospCard(title = "Thème & Ambiance") {
                    AospSettingItem(
                        icon = Icons.Default.Palette,
                        iconTint = Color(0xFFA855F7),
                        iconBg = Color(0x18A855F7),
                        title = "Thème",
                        subtitle = "Teinte, saturation, nuance et palettes harmonieuses",
                        onClick = { onNavigateSubPage("theme") }
                    )
                }
            }

            // CARD 3: LANGUE & SYSTÈME
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

@Composable
private fun ThemeSettingsSubPage(
    themeHue: Float,
    onThemeHueChange: (Float) -> Unit,
    themeAccentSaturation: Float,
    onThemeAccentSaturationChange: (Float) -> Unit,
    themeBgSaturation: Float,
    onThemeBgSaturationChange: (Float) -> Unit,
    themeBgLightness: Float,
    onThemeBgLightnessChange: (Float) -> Unit,
    themeModulesLightness: Float,
    onThemeModulesLightnessChange: (Float) -> Unit,
    onResetTheme: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = NeonCyan,
                    modifier = Modifier.size(14.dp)
                )
                Text(text = "Retour", fontSize = 11.sp, color = NeonCyan)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Thème",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { onResetTheme() }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Rétablir",
                    tint = TextDim,
                    modifier = Modifier.size(12.dp)
                )
                Text(text = "Défaut", fontSize = 10.sp, color = TextDim)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. ColorBlendr Tonal Palettes Header Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E2435))
                        .border(1.dp, Color(0xFF2C354B), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Row 1: Primaire, Secondaire, Tertiaire
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TonalSwatch(
                                label = "Primaire",
                                topColor = hsvToColor(themeHue, 0.70f, 0.90f),
                                bottomColor = hsvToColor(themeHue, 0.60f, 0.45f)
                            )
                            TonalSwatch(
                                label = "Secondaire",
                                topColor = hsvToColor((themeHue + 40f) % 360f, 0.65f, 0.85f),
                                bottomColor = hsvToColor((themeHue + 40f) % 360f, 0.50f, 0.40f)
                            )
                            TonalSwatch(
                                label = "Tertiaire",
                                topColor = hsvToColor((themeHue + 140f) % 360f, 0.60f, 0.82f),
                                bottomColor = hsvToColor((themeHue + 140f) % 360f, 0.45f, 0.38f)
                            )
                        }

                        // Row 2: Neutre 1, Neutre 2
                        Row(
                            modifier = Modifier.fillMaxWidth(0.72f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TonalSwatch(
                                label = "Neutre 1",
                                topColor = Color(0xFF94A3B8),
                                bottomColor = Color(0xFF334155)
                            )
                            TonalSwatch(
                                label = "Neutre 2",
                                topColor = Color(0xFF64748B),
                                bottomColor = Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }

            // 2. Horizontal Slide-to-Adjust Cards (Exact ColorBlendr Interaction)
            item {
                ThemeSliderCard(
                    title = "Saturation de la couleur d'accentuation",
                    valueText = String.format(java.util.Locale.US, "Sélection: %.2fx", themeAccentSaturation),
                    progress = ((themeAccentSaturation - 0.5f) / 1.5f).coerceIn(0f, 1f),
                    onProgressChange = { p -> onThemeAccentSaturationChange(0.5f + p * 1.5f) },
                    onReset = { onThemeAccentSaturationChange(1.0f) }
                )
            }

            item {
                ThemeSliderCard(
                    title = "Saturation de l'arrière-plan",
                    valueText = String.format(java.util.Locale.US, "Sélection: %.2fx", themeBgSaturation),
                    progress = (themeBgSaturation / 2.0f).coerceIn(0f, 1f),
                    onProgressChange = { p -> onThemeBgSaturationChange(p * 2.0f) },
                    onReset = { onThemeBgSaturationChange(1.0f) }
                )
            }

            item {
                ThemeSliderCard(
                    title = "Luminosité du fond",
                    valueText = String.format(java.util.Locale.US, "Sélection: %.2fx", themeBgLightness),
                    progress = ((themeBgLightness - 0.7f) / 0.7f).coerceIn(0f, 1f),
                    onProgressChange = { p -> onThemeBgLightnessChange(0.7f + p * 0.7f) },
                    onReset = { onThemeBgLightnessChange(1.0f) }
                )
            }

            item {
                ThemeSliderCard(
                    title = "Luminosité & contraste des modules",
                    valueText = String.format(java.util.Locale.US, "Sélection: %.2fx", themeModulesLightness),
                    progress = ((themeModulesLightness - 0.8f) / 0.7f).coerceIn(0f, 1f),
                    onProgressChange = { p -> onThemeModulesLightnessChange(0.8f + p * 0.7f) },
                    onReset = { onThemeModulesLightnessChange(1.0f) }
                )
            }

            item {
                ThemeSliderCard(
                    title = "Teinte principale de l'interface",
                    valueText = "Sélection: ${themeHue.toInt()}°",
                    progress = (themeHue / 360f).coerceIn(0f, 1f),
                    onProgressChange = { p -> onThemeHueChange(p * 360f) },
                    onReset = { onThemeHueChange(220f) }
                )
            }
        }
    }
}

@Composable
private fun TonalSwatch(
    label: String,
    topColor: Color,
    bottomColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(topColor)
                .border(1.2.dp, Color(0x33FFFFFF), CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .background(bottomColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .background(bottomColor.copy(alpha = 0.35f))
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ThemeSliderCard(
    title: String,
    valueText: String,
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnProgressChange by rememberUpdatedState(onProgressChange)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1E2435))
            .border(1.dp, Color(0xFF2C354B), RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat()
                    if (width > 0f) {
                        currentOnProgressChange((down.position.x / width).coerceIn(0f, 1f))
                    }
                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        if (width > 0f) {
                            change.consume()
                            currentOnProgressChange((change.position.x / width).coerceIn(0f, 1f))
                        }
                    }
                }
            }
    ) {
        val fillWidth = maxWidth * progress.coerceIn(0f, 1f)

        // ColorBlendr filled progress gauge from left to right
        if (progress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(fillWidth)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            bottomStart = 18.dp,
                            topEnd = if (progress >= 0.96f) 18.dp else 4.dp,
                            bottomEnd = if (progress >= 0.96f) 18.dp else 4.dp
                        )
                    )
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            )
                        )
                    )
            )
        }

        // Foreground content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = valueText,
                    fontSize = 11.5.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            // Circular reset button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onReset() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Réinitialiser",
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
