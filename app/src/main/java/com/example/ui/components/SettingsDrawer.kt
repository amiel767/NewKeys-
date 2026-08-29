package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppTheme
import com.example.model.MidiDeviceItem
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * AOSP MaterialExpressive Settings Drawer
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
    
    // 3D FX Knobs
    soundGoodizer: Float,
    onSoundGoodizerChange: (Float) -> Unit,
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
                    .widthIn(min = 420.dp, max = 540.dp)
                    .fillMaxWidth(0.56f)
                    .shadow(32.dp, RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF222230), Color(0xFF161622), Color(0xFF101018))
                        )
                    )
                    .border(1.2.dp, Color(0x3322D3EE), RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .clickable(enabled = false) {}
                    .padding(16.dp)
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
                    "theme" -> {
                        ThemeSelectorSubPage(
                            currentTheme = currentTheme,
                            onSelectTheme = onSelectTheme,
                            onBack = { onNavigateSubPage("main") }
                        )
                    }
                    "theme_lang" -> {
                        LanguageThemeSubPage(
                            selectedLanguage = selectedLanguage,
                            onSelectLanguage = onSelectLanguage,
                            onBack = { onNavigateSubPage("main") }
                        )
                    }
                    else -> {
                        MainSettingsPage(
                            onClose = onClose,
                            onNavigateSubPage = onNavigateSubPage,
                            connectedMidiCount = midiDevices.count { it.isConnected && it.isEnabled },
                            audioEngine = audioEngine,
                            audioBufferSize = audioBufferSize,
                            polyphony = polyphony,
                            currentTheme = currentTheme,
                            selectedLanguage = selectedLanguage,
                            soundGoodizer = soundGoodizer,
                            onSoundGoodizerChange = onSoundGoodizerChange,
                            masterPunch = masterPunch,
                            onMasterPunchChange = onMasterPunchChange,
                            spatialWidener = spatialWidener,
                            onSpatialWidenerChange = onSpatialWidenerChange,
                            velocityMin = velocityMin,
                            velocityMax = velocityMax,
                            onVelocityRangeChange = onVelocityRangeChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainSettingsPage(
    onClose: () -> Unit,
    onNavigateSubPage: (String) -> Unit,
    connectedMidiCount: Int,
    audioEngine: String,
    audioBufferSize: Int,
    polyphony: Int,
    currentTheme: AppTheme,
    selectedLanguage: String,
    soundGoodizer: Float,
    onSoundGoodizerChange: (Float) -> Unit,
    masterPunch: Float,
    onMasterPunchChange: (Float) -> Unit,
    spatialWidener: Float,
    onSpatialWidenerChange: (Float) -> Unit,
    velocityMin: Float,
    velocityMax: Float,
    onVelocityRangeChange: (Float, Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Drawer Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(NeonCyanLight, NeonCyanDark))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⚙", fontSize = 14.sp, color = Color(0xFF002B36))
                }
                Text(text = "Paramètres LiveKeys", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            }

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x14FFFFFF))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✕", fontSize = 12.sp, color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. THEME SELECTOR CARD
            item {
                SettingsNavCard(
                    icon = "🎨",
                    title = "Thème Visuel de l'Interface",
                    subtitle = "${currentTheme.displayName} • Personnalisation",
                    badge = currentTheme.displayName,
                    badgeColor = NeonCyan,
                    onClick = { onNavigateSubPage("theme") }
                )
            }

            // 2. MIDI DEVICE CARD
            item {
                SettingsNavCard(
                    icon = "🎹",
                    title = "Appareils MIDI Connectés",
                    subtitle = "$connectedMidiCount appareil(s) actif(s) • Clavier & Pédale",
                    badge = if (connectedMidiCount > 0) "CONNECTÉ" else "AUCUN",
                    badgeColor = if (connectedMidiCount > 0) NeonCyan else TextDim,
                    onClick = { onNavigateSubPage("midi") }
                )
            }

            // 3. AUDIO ENGINE & LATENCY CARD
            item {
                SettingsNavCard(
                    icon = "⚡",
                    title = "Moteur Audio & Buffer",
                    subtitle = "$audioEngine • ${audioBufferSize} frames • $polyphony Voix",
                    badge = "BASSE LATENCE",
                    badgeColor = NeonMagenta,
                    onClick = { onNavigateSubPage("audio") }
                )
            }

            // 4. LANGUAGE CARD
            item {
                SettingsNavCard(
                    icon = "🌐",
                    title = "Langue de l'Interface",
                    subtitle = "$selectedLanguage • Internationalisation",
                    badge = selectedLanguage,
                    badgeColor = SoloAmber,
                    onClick = { onNavigateSubPage("theme_lang") }
                )
            }

            // 5. 3D MASTER FX SECTION (Effet 3D + 2 Knobs)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x14FFFFFF))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TRAITEMENTS MASTER (EFFETS 3D)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            letterSpacing = 0.8.sp
                        )
                        Text(text = "DSP Analog Stage", fontSize = 8.5.sp, color = TextDim2)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsKnob3D(
                            value = soundGoodizer,
                            onValueChange = onSoundGoodizerChange,
                            label = "Effet 3D",
                            accentColor = Color(0xFFFF9100)
                        )

                        SettingsKnob3D(
                            value = masterPunch,
                            onValueChange = onMasterPunchChange,
                            label = "Master Punch",
                            accentColor = Color(0xFFE040FB)
                        )

                        SettingsKnob3D(
                            value = spatialWidener,
                            onValueChange = onSpatialWidenerChange,
                            label = "Spatial Widener",
                            accentColor = NeonCyan
                        )
                    }
                }
            }

            // 6. GLOBAL VELOCITY SETTINGS
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x14FFFFFF))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RÉGLAGE VÉLOCITÉ (MIN / MAX)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "${(velocityMin * 100).toInt()}% — ${(velocityMax * 100).toInt()}%",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    RangeSlider(
                        value = velocityMin..velocityMax,
                        onValueChange = { range ->
                            onVelocityRangeChange(range.start, range.endInclusive)
                        },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyanLight,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = Color(0x26FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Toucher Doux (Min: ${(velocityMin * 100).toInt()}%)", fontSize = 8.5.sp, color = TextDim)
                        Text(text = "Dynamique Forte (Max: ${(velocityMax * 100).toInt()}%)", fontSize = 8.5.sp, color = TextDim)
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeSelectorSubPage(
    currentTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "Thème de l'Application", onBack = onBack)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "PERSONNALISATION VISUELLE EXPRESSIVE",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextDim,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AppTheme.values()) { theme ->
                val isSelected = currentTheme == theme
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0x2222D3EE) else Color(0x0EFFFFFF))
                        .border(1.dp, if (isSelected) NeonCyan else Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                        .clickable { onSelectTheme(theme) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    when (theme) {
                                        AppTheme.CYBER_NEON -> Brush.linearGradient(listOf(Color(0xFF22D3EE), Color(0xFFF43F5E)))
                                        AppTheme.OBSIDIAN_GOLD -> Brush.linearGradient(listOf(Color(0xFFFBBF24), Color(0xFFD97706)))
                                        AppTheme.TOKYO_NIGHT -> Brush.linearGradient(listOf(Color(0xFF818CF8), Color(0xFFC084FC)))
                                        AppTheme.STUDIO_SLATE -> Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFF64748B)))
                                        AppTheme.OLED_BLACK -> Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF000000)))
                                    }
                                )
                        )

                        Column {
                            Text(
                                text = theme.displayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) NeonCyan else TextPrimary
                            )
                            Text(
                                text = when (theme) {
                                    AppTheme.CYBER_NEON -> "Cyan & Magenta néon futuriste"
                                    AppTheme.OBSIDIAN_GOLD -> "Or luxueux & noir profond"
                                    AppTheme.TOKYO_NIGHT -> "Violet électrique & bleu nuit"
                                    AppTheme.STUDIO_SLATE -> "Studio épuré gris ardoise"
                                    AppTheme.OLED_BLACK -> "Noir absolu ultra contrasté"
                                },
                                fontSize = 8.5.sp,
                                color = TextDim
                            )
                        }
                    }

                    if (isSelected) {
                        Text(text = "✓", fontSize = 14.sp, color = NeonCyan, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsNavCard(
    icon: String,
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x12FFFFFF))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = icon, fontSize = 18.sp)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(text = badge, fontSize = 7.5.sp, fontWeight = FontWeight.ExtraBold, color = badgeColor)
                }
            }
            Text(text = subtitle, fontSize = 9.sp, color = TextDim)
        }

        Text(text = "›", fontSize = 18.sp, color = TextDim)
    }
}

@Composable
fun SettingsKnob3D(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    accentColor: Color
) {
    var curVal by remember(value) { mutableFloatStateOf(value) }
    val onValueChangeState by rememberUpdatedState(onValueChange)

    Column(
        modifier = Modifier
            .width(86.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    val delta = -dragAmount / 120f
                    curVal = (curVal + delta).coerceIn(0f, 1f)
                    onValueChangeState(curVal)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(54.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 4f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF38384A), Color(0xFF1E1E28), Color(0xFF12121A)),
                    center = center,
                    radius = radius
                ),
                radius = radius
            )

            val startAngle = 135f
            val sweepAngle = 270f * curVal

            drawArc(
                color = Color(0x22FFFFFF),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(center.x - radius + 2, center.y - radius + 2),
                size = Size((radius - 2) * 2, (radius - 2) * 2),
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )

            drawArc(
                color = accentColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius + 2, center.y - radius + 2),
                size = Size((radius - 2) * 2, (radius - 2) * 2),
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )

            val angleDeg = 135f + sweepAngle
            val angleRad = (angleDeg * PI / 180f).toFloat()
            val notchStartX = center.x + radius * 0.4f * cos(angleRad)
            val notchStartY = center.y + radius * 0.4f * sin(angleRad)
            val notchEndX = center.x + radius * 0.82f * cos(angleRad)
            val notchEndY = center.y + radius * 0.82f * sin(angleRad)

            drawLine(
                color = Color.White,
                start = Offset(notchStartX, notchStartY),
                end = Offset(notchEndX, notchEndY),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        Text(
            text = "${(curVal * 100).toInt()}%",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
    }
}

@Composable
fun MidiDevicesSubPage(
    midiDevices: List<MidiDeviceItem>,
    onToggleDevice: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "Appareils MIDI", onBack = onBack)

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "SCAN AUTOMATIQUE DES PORTS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextDim)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x1A22D3EE))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = "↻ Actualiser", fontSize = 9.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(midiDevices) { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x12FFFFFF))
                        .border(1.dp, if (device.isEnabled) Color(0x3322D3EE) else Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = device.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "${device.type} • Statut: Connecté", fontSize = 9.sp, color = TextDim)
                    }

                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (device.isEnabled) NeonCyan else Color(0x26FFFFFF))
                            .clickable { onToggleDevice(device.id) }
                            .padding(2.dp),
                        contentAlignment = if (device.isEnabled) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioEngineSubPage(
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
    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "Moteur Audio & Buffer", onBack = onBack)

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "DRIVER / MOTEUR AUDIO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextDim)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Oboe", "AAudio", "OpenSL ES").forEach { engine ->
                val isSelected = audioEngine == engine
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NeonCyan else Color(0x14FFFFFF))
                        .clickable { onSelectAudioEngine(engine) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = engine,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFF002B36) else TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(text = "TAILLE DU BUFFER AUDIO (FRAMES)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextDim)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(64, 128, 256, 512).forEach { size ->
                val isSelected = audioBufferSize == size
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NeonCyan else Color(0x14FFFFFF))
                        .clickable { onSelectBufferSize(size) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$size",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFF002B36) else TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(text = "POLYPHONIE MAX (VOIX)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextDim)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(32, 64, 128, 256).forEach { poly ->
                val isSelected = polyphony == poly
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NeonCyan else Color(0x14FFFFFF))
                        .clickable { onSelectPolyphony(poly) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$poly",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFF002B36) else TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun LanguageThemeSubPage(
    selectedLanguage: String,
    onSelectLanguage: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "Langue & Affichage", onBack = onBack)

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "CHOISIR LA LANGUE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextDim)
        Spacer(modifier = Modifier.height(6.dp))

        listOf("Français", "English", "Español", "Deutsch").forEach { lang ->
            val isSelected = selectedLanguage == lang
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0x2E22D3EE) else Color(0x0EFFFFFF))
                    .border(1.dp, if (isSelected) NeonCyan else Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                    .clickable { onSelectLanguage(lang) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = lang, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) NeonCyan else TextPrimary)
                if (isSelected) {
                    Text(text = "✓", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun SubPageHeader(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x1A22D3EE))
                .clickable { onBack() }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text(text = "← Retour", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
        }

        Text(text = title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}
