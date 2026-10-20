package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.AppTheme
import com.soundstage.mixer.model.TrackChannel
import com.soundstage.mixer.model.MidiDeviceItem
import com.soundstage.mixer.ui.components.v2.IconUsbV2
import com.soundstage.mixer.model.MidiMappingItem
import com.soundstage.mixer.model.MidiActionType
import com.soundstage.mixer.model.MidiControlType
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
    midiMappings: List<MidiMappingItem> = emptyList(),
    learningActionType: MidiActionType? = null,
    lastMidiSummary: String = "",
    onStartMidiLearn: (MidiActionType) -> Unit = {},
    onCancelMidiLearn: () -> Unit = {},
    onUnmapMidiAction: (MidiActionType) -> Unit = {},
    onToggleMidiMappingEnabled: (MidiActionType) -> Unit = {},
    onUpdateManualMidiMapping: (MidiActionType, MidiControlType, Int, Int) -> Unit = { _, _, _, _ -> },
    onResetMidiMappings: () -> Unit = {},
    onClearMidiMappings: () -> Unit = {},
    midiDebounceEnabled: Boolean = false,
    midiDebounceMs: Long = 30L,
    onToggleMidiDebounce: () -> Unit = {},
    onUpdateMidiDebounceMs: (Long) -> Unit = {},
    tracks: List<TrackChannel> = emptyList(),
    selectedMidiOutputDevice: String = "All Connected Devices",
    onSelectMidiOutputDevice: (String) -> Unit = {},
    onUpdateTrackMidiChannel: (Int, Int) -> Unit = { _, _ -> },
    
    // Audio Engine / Buffer & Polyphony
    audioEngine: String = "Oboe (C++)",
    onSelectAudioEngine: (String) -> Unit = {},
    audioBufferSize: Int,
    onSelectBufferSize: (Int) -> Unit,
    polyphony: Int,
    onSelectPolyphony: (Int) -> Unit,
    isLowLatency: Boolean = true,
    onToggleLowLatency: () -> Unit = {},
    fluidSynthGain: Float = 1.2f,
    onFluidSynthGainChange: (Float) -> Unit = {},
    
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
    trackColorSaturation: Float = 1.0f,
    onTrackColorSaturationChange: (Float) -> Unit = {},
    themePaletteName: String = "Midnight Slate (Default)",
    onSelectThemePalette: (ThemePalettePreset) -> Unit = {},
    onResetTheme: () -> Unit = {},
    isFaderAutoFit: Boolean = false,
    onToggleFaderAutoFit: () -> Unit = {},
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

    var isMidiLearnExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(subPage) {
        if (subPage != "midi_learn") {
            isMidiLearnExpanded = false
        }
    }

    val animatedDrawerWidthFraction by animateFloatAsState(
        targetValue = if (subPage == "midi_learn" && isMidiLearnExpanded) 0.92f else 0.58f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow),
        label = "drawer_width_fraction"
    )

    val animatedDrawerMinWidth by animateDpAsState(
        targetValue = if (subPage == "midi_learn" && isMidiLearnExpanded) 760.dp else 400.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow),
        label = "drawer_min_width"
    )

    val animatedDrawerMaxWidth by animateDpAsState(
        targetValue = if (subPage == "midi_learn" && isMidiLearnExpanded) 980.dp else 520.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow),
        label = "drawer_max_width"
    )

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
                    .widthIn(min = animatedDrawerMinWidth, max = animatedDrawerMaxWidth)
                    .fillMaxWidth(animatedDrawerWidthFraction)
                    .shadow(32.dp, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .background(Color(0xFF181C28))
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
                    "midi_learn" -> {
                        MidiLearnSubPage(
                            mappings = midiMappings,
                            learningActionType = learningActionType,
                            lastMidiSummary = lastMidiSummary,
                            connectedMidiCount = midiDevices.count { it.isConnected },
                            onStartLearn = onStartMidiLearn,
                            onCancelLearn = onCancelMidiLearn,
                            onUnmapAction = onUnmapMidiAction,
                            onToggleMappingEnabled = onToggleMidiMappingEnabled,
                            onUpdateManualMapping = onUpdateManualMidiMapping,
                            onResetDefaults = onResetMidiMappings,
                            onClearAll = onClearMidiMappings,
                            onBack = { onNavigateSubPage("main") },
                            onExpandedChange = { isMidiLearnExpanded = it },
                            midiDebounceEnabled = midiDebounceEnabled,
                            midiDebounceMs = midiDebounceMs,
                            onToggleMidiDebounce = onToggleMidiDebounce,
                            onUpdateMidiDebounceMs = onUpdateMidiDebounceMs
                        )
                    }
                    "buffer_polyphony", "audio" -> {
                        BufferPolyphonySubPage(
                            audioBufferSize = audioBufferSize,
                            onSelectBufferSize = onSelectBufferSize,
                            polyphony = polyphony,
                            onSelectPolyphony = onSelectPolyphony,
                            fluidSynthGain = fluidSynthGain,
                            onFluidSynthGainChange = onFluidSynthGainChange,
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
                            trackColorSaturation = trackColorSaturation,
                            onTrackColorSaturationChange = onTrackColorSaturationChange,
                            onResetTheme = onResetTheme,
                            isFaderAutoFit = isFaderAutoFit,
                            onToggleFaderAutoFit = onToggleFaderAutoFit,
                            onBack = { onNavigateSubPage("main") }
                        )
                    }
                    else -> {
                        AospMainSettingsPage(
                            onClose = onClose,
                            onNavigateSubPage = onNavigateSubPage,
                            midiDevices = midiDevices,
                            midiMappings = midiMappings,
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
    midiMappings: List<MidiMappingItem> = emptyList(),
    audioBufferSize: Int,
    polyphony: Int,
    selectedLanguage: String,
    masterPunch: Float,
    onMasterPunchChange: (Float) -> Unit,
    spatialWidener: Float,
    onSpatialWidenerChange: (Float) -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    velocityMin: Float = 0f,
    velocityMax: Float = 1f,
    onVelocityRangeChange: (Float, Float) -> Unit = { _, _ -> },
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
                        .background(Color(0x183B82F6)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CARD 1: DEVICES & AUDIO
            item {
                AospCard(title = "Devices & Audio") {
                    Column {
                        AospSettingItem(
                            icon = null,
                            leading = { IconUsbV2(size = 18.dp, color = Color(0xFF10B981)) },
                            iconTint = Color(0xFF10B981),
                            iconBg = Color(0x1810B981),
                            title = "USB MIDI",
                            subtitle = "Hardware Detection (${midiDevices.count { it.isConnected }} connected)",
                            onClick = { onNavigateSubPage("midi") }
                        )

                        AospDivider()

                        AospSettingItem(
                            icon = Icons.Default.Tune,
                            iconTint = Color(0xFF3B82F6),
                            iconBg = Color(0x183B82F6),
                            title = "Buffer & Polyphony",
                            subtitle = "Buffer $audioBufferSize frames · Polyphony $polyphony voices",
                            onClick = { onNavigateSubPage("buffer_polyphony") }
                        )

                        AospDivider()

                        AospSettingItem(
                            icon = Icons.Default.Tune,
                            iconTint = Color(0xFFF59E0B),
                            iconBg = Color(0x18F59E0B),
                            title = "MIDI Learn & Controllers",
                            subtitle = "${midiMappings.count { it.isAssigned }} mapped action(s) · Pedals, Knobs & Faders",
                            onClick = { onNavigateSubPage("midi_learn") }
                        )
                    }
                }
            }

            // CARD 2: UI SETTINGS
            item {
                AospCard(title = "UI Settings") {
                    AospSettingItem(
                        icon = Icons.Default.Palette,
                        iconTint = Color(0xFFA855F7),
                        iconBg = Color(0x18A855F7),
                        title = "UI Settings",
                        subtitle = "Colors, theme presets, and faders layout mode",
                        onClick = { onNavigateSubPage("theme") }
                    )
                }
            }

            // CARD 3: LANGUAGE & SYSTEM
            item {
                AospCard(title = "Language & System") {
                    Column {
                        AospSettingItem(
                            icon = Icons.Default.Language,
                            iconTint = Color(0xFFEC4899),
                            iconBg = Color(0x18EC4899),
                            title = "Language",
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
                                    .background(Color(0x183B82F6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Screen On",
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Keep Screen On",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (keepScreenOn) "Active (prevents screen sleep)" else "Disabled (system sleep)",
                                    fontSize = 11.sp,
                                    color = TextDim
                                )
                            }

                            Switch(
                                checked = keepScreenOn,
                                onCheckedChange = { onToggleKeepScreenOn() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF3B82F6),
                                    uncheckedThumbColor = Color.LightGray,
                                    uncheckedTrackColor = Color(0xFF33384A)
                                )
                            )
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
            color = Color(0xFF94A3B8),
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 5.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black, spotColor = Color(0x88000000))
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                    )
                )
                .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(16.dp))
                .drawWithContent {
                    drawContent()
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(6.dp.toPx(), 1f),
                        end = Offset(size.width - 6.dp.toPx(), 1f),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
private fun AospSettingItem(
    icon: ImageVector? = null,
    iconTint: Color,
    iconBg: Color = Color(0x18FFFFFF),
    title: String,
    subtitle: String,
    leading: (@Composable () -> Unit)? = null,
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
            if (leading != null) {
                leading()
            } else {
                Icon(
                    imageVector = icon!!,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(text = "USB MIDI Devices", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = "${midiDevices.count { it.isConnected }} device(s) connected", fontSize = 11.sp, color = Color(0xFF10B981))
            }
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
                        IconUsbV2(size = 28.dp, color = Color(0x66FFFFFF))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No USB MIDI keyboard detected.\nConnect a USB-OTG MIDI controller to play directly.",
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
                            Text(text = if (dev.isConnected) "Connected" else "Disconnected", fontSize = 9.sp, color = if (dev.isConnected) Color(0xFF10B981) else TextDim)
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
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF3B82F6), checkedTrackColor = Color(0x663B82F6))
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
    fluidSynthGain: Float = 1.2f,
    onFluidSynthGainChange: (Float) -> Unit = {},
    onBack: () -> Unit
) {
    val bufferSizes = listOf(64, 128, 256, 512, 1024)
    val polyphonyValues = listOf(64, 128, 256, 512)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(text = "Buffer, Polyphony & Gain", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = "Audio engine headroom & latency", fontSize = 11.sp, color = TextDim)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                AospCard(title = "Audio Buffer Size (Latency)") {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "A smaller buffer size reduces key touch latency. Adjust according to device performance.",
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
                                        .background(if (isSel) Color(0xFF3B82F6) else Color(0x14FFFFFF))
                                        .clickable { onSelectBufferSize(sz) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$sz",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) Color.White else TextPrimary
                                        )
                                        Text(
                                            text = latencyMs,
                                            fontSize = 8.sp,
                                            color = if (isSel) Color(0xCCFFFFFF) else TextDim2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                AospCard(title = "Maximum Polyphony (Voices)") {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Maximum number of notes played simultaneously by the synth engine.",
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
                                        color = if (isSel) Color.White else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                AospCard(title = "FluidSynth Master Gain") {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Synth Output Headroom",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "%.2fx (Std: 1.20x)".format(fluidSynthGain),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (fluidSynthGain > 1.5f) Color(0xFFF59E0B) else Color(0xFF9BC0F8)
                            )
                        }

                        Slider(
                            value = fluidSynthGain,
                            onValueChange = onFluidSynthGainChange,
                            valueRange = 0.2f..2.5f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = if (fluidSynthGain > 1.5f) Color(0xFFF59E0B) else Color(0xFF9BC0F8),
                                inactiveTrackColor = Color(0x28FFFFFF)
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x18F59E0B))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "⚠️",
                                fontSize = 9.5.sp
                            )
                            Text(
                                text = "Warning: High gain may cause digital clipping.",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFDE68A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(text = "Language", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = "Choose UI display language", fontSize = 11.sp, color = TextDim)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(languages) { lang ->
                val isSel = (lang == selectedLanguage)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) Color(0xFF1E3A8A) else Color(0xFF1E2433))
                        .border(1.dp, if (isSel) Color(0x66FFFFFF) else Color(0x18FFFFFF), RoundedCornerShape(12.dp))
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
                        Text(text = "✓", fontSize = 14.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PadlockIcon(
    isLocked: Boolean,
    tint: Color = Color.White,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bodyWidth = w * 0.62f
        val bodyHeight = h * 0.44f
        val bodyLeft = (w - bodyWidth) / 2f
        val bodyTop = h * 0.48f

        val shackleRadius = bodyWidth * 0.32f
        val shackleCenterX = w / 2f
        val shackleCenterY = bodyTop
        val strokeW = w * 0.11f

        val shacklePath = androidx.compose.ui.graphics.Path()

        if (isLocked) {
            // Closed shackle
            shacklePath.moveTo(shackleCenterX - shackleRadius, shackleCenterY)
            shacklePath.lineTo(shackleCenterX - shackleRadius, shackleCenterY - shackleRadius)
            shacklePath.arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    shackleCenterX - shackleRadius,
                    shackleCenterY - (shackleRadius * 2f),
                    shackleCenterX + shackleRadius,
                    shackleCenterY
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            shacklePath.lineTo(shackleCenterX + shackleRadius, shackleCenterY)
        } else {
            // Open shackle (lifted up)
            val openLift = h * 0.14f
            shacklePath.moveTo(shackleCenterX - shackleRadius, shackleCenterY - openLift)
            shacklePath.lineTo(shackleCenterX - shackleRadius, shackleCenterY - shackleRadius - openLift)
            shacklePath.arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    shackleCenterX - shackleRadius,
                    shackleCenterY - (shackleRadius * 2f) - openLift,
                    shackleCenterX + shackleRadius,
                    shackleCenterY - openLift
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            shacklePath.lineTo(shackleCenterX + shackleRadius, shackleCenterY - (openLift * 1.8f))
        }

        drawPath(
            path = shacklePath,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeW,
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )

        // Draw Body
        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyLeft, bodyTop),
            size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bodyWidth * 0.20f)
        )

        // Keyhole
        val holeRadius = bodyWidth * 0.12f
        val holeCenter = Offset(w / 2f, bodyTop + bodyHeight * 0.42f)
        drawCircle(
            color = Color(0xFF191F2C),
            radius = holeRadius,
            center = holeCenter
        )
        drawRect(
            color = Color(0xFF191F2C),
            topLeft = Offset(holeCenter.x - (holeRadius * 0.5f), holeCenter.y),
            size = androidx.compose.ui.geometry.Size(holeRadius, holeRadius * 1.5f)
        )
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
    trackColorSaturation: Float = 1.0f,
    onTrackColorSaturationChange: (Float) -> Unit = {},
    onResetTheme: () -> Unit,
    isFaderAutoFit: Boolean = false,
    onToggleFaderAutoFit: () -> Unit = {},
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = "UI Settings",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            // Custom circular Reset Button (small curved reset arrow)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0x18FFFFFF))
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                    .clickable { onResetTheme() }
                    .testTag("btn_reset_theme"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Theme",
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // CARD 1: FADERS LAYOUT
            item {
                AospCard(title = "Faders Layout") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleFaderAutoFit() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Faders Auto-Fit",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isFaderAutoFit) "Locked to screen (no scroll)" else "Fixed width with scroll",
                                fontSize = 11.sp,
                                color = TextDim
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .shadow(2.dp, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isFaderAutoFit) Color(0xFF3B82F6) else Color(0xFF222836))
                                .clickable { onToggleFaderAutoFit() },
                            contentAlignment = Alignment.Center
                        ) {
                            PadlockIcon(
                                isLocked = isFaderAutoFit,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // CARD 2: COLORS
            item {
                AospCard(title = "Colors") {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Primary Interface Hue
                        ThemeSliderCard(
                            title = "Primary Interface Hue",
                            valueText = "${themeHue.toInt()}°",
                            progress = (themeHue / 360f).coerceIn(0f, 1f),
                            onProgressChange = { p -> onThemeHueChange(p * 360f) },
                            onReset = { onThemeHueChange(220f) }
                        )

                        // 2. Accent Color Saturation
                        ThemeSliderCard(
                            title = "Accent Color Saturation",
                            valueText = String.format(java.util.Locale.US, "%.2fx", themeAccentSaturation),
                            progress = ((themeAccentSaturation - 0.5f) / 1.5f).coerceIn(0f, 1f),
                            onProgressChange = { p -> onThemeAccentSaturationChange(0.5f + p * 1.5f) },
                            onReset = { onThemeAccentSaturationChange(1.27f) }
                        )

                        // 3. Background Tone Saturation
                        ThemeSliderCard(
                            title = "Background Tone Saturation",
                            valueText = String.format(java.util.Locale.US, "%.2fx", themeBgSaturation),
                            progress = (themeBgSaturation / 2.0f).coerceIn(0f, 1f),
                            onProgressChange = { p -> onThemeBgSaturationChange(p * 2.0f) },
                            onReset = { onThemeBgSaturationChange(1.60f) }
                        )

                        // 4. Background Lightness
                        ThemeSliderCard(
                            title = "Background Lightness",
                            valueText = String.format(java.util.Locale.US, "%.2fx", themeBgLightness),
                            progress = ((themeBgLightness - 0.7f) / 0.7f).coerceIn(0f, 1f),
                            onProgressChange = { p -> onThemeBgLightnessChange(0.7f + p * 0.7f) },
                            onReset = { onThemeBgLightnessChange(0.95f) }
                        )
                    }
                }
            }
        }
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
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat()
                    val downTime = System.currentTimeMillis()
                    val startX = down.position.x
                    var hasUnlocked = false
                    val pointerId = down.id

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break

                        val elapsed = System.currentTimeMillis() - downTime
                        val dragDistance = kotlin.math.abs(change.position.x - startX)

                        // Unlock slide modification only after holding briefly (> 160ms) or an intentional horizontal drag (> 10px)
                        if (!hasUnlocked && (elapsed > 160L || dragDistance > 10f)) {
                            hasUnlocked = true
                        }

                        if (hasUnlocked && width > 0f) {
                            change.consume()
                            currentOnProgressChange((change.position.x / width).coerceIn(0f, 1f))
                        }
                    }
                }
            }
    ) {
        val fillWidth = maxWidth * progress.coerceIn(0f, 1f)

        // Filled progress gauge from left to right matching dynamic primary color
        if (progress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(fillWidth)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            bottomStart = 16.dp,
                            topEnd = if (progress >= 0.96f) 16.dp else 4.dp,
                            bottomEnd = if (progress >= 0.96f) 16.dp else 4.dp
                        )
                    )
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                            )
                        )
                    )
            )
        }

        // Foreground content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = valueText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Circular reset button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onReset() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset",
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MidiRoutingSubPage(
    midiDevices: List<MidiDeviceItem>,
    tracks: List<TrackChannel>,
    selectedMidiOutputDevice: String,
    onSelectMidiOutputDevice: (String) -> Unit,
    onUpdateTrackMidiChannel: (Int, Int) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0x18FFFFFF))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = "MIDI Output & Channel Routing",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CARD 1: USB MIDI OUTPUT DEVICE
            item {
                AospCard(title = "USB MIDI Output Device") {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Select active MIDI hardware output port:",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                        val outputOptions = mutableListOf("All Connected Devices", "None (Internal Only)")
                        outputOptions.addAll(midiDevices.filter { it.isConnected }.map { it.name })

                        outputOptions.distinct().forEach { devName ->
                            val isSelected = selectedMidiOutputDevice.equals(devName, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0x223B82F6) else Color(0x0CFFFFFF))
                                    .border(1.dp, if (isSelected) Color(0xFF3B82F6) else Color(0x18FFFFFF), RoundedCornerShape(8.dp))
                                    .clickable { onSelectMidiOutputDevice(devName) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconUsbV2(
                                        size = 16.dp,
                                        color = if (isSelected) Color(0xFF3B82F6) else Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = devName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                    )
                                }
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF3B82F6))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // CARD 2: TRACK MIDI CHANNEL ROUTING
            item {
                AospCard(title = "Track MIDI Channel Assignment") {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Assign MIDI input channels to tracks for instant multi-channel routing:",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )

                        if (tracks.isEmpty()) {
                            Text(
                                text = "No active tracks available",
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B)
                            )
                        } else {
                            tracks.forEach { track ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x12FFFFFF))
                                        .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(getTrackThemeColor(track.id, track.isMaster))
                                            )
                                            Text(
                                                text = "Track ${track.id}: ${track.name}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        val soundLabel = if (track.patchName.isNotBlank()) track.patchName else if (track.soundfontName.isNotBlank()) track.soundfontName else "Default"
                                        Text(
                                            text = soundLabel,
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }

                                    // MIDI Channel Buttons Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val channelOptions = (1..16).map { it to "Ch.$it" }
                                        channelOptions.forEach { (chVal, chLabel) ->
                                            val isChSelected = track.midiChannel == chVal
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isChSelected) Color(0xFF3B82F6) else Color(0x1AFFFFFF))
                                                    .clickable { onUpdateTrackMidiChannel(track.id, chVal) }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = chLabel,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isChSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isChSelected) Color.White else Color(0xFFCBD5E1)
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
        }
    }
}
