package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StorageItem
import com.example.ui.theme.*

/**
 * Style / Arranger Dialog (.STY Engine):
 * - Clean Square format
 * - Direct real file explorer in /LiveKeys/Styles
 * - Style Effects & EQ
 * - SoundFont assignment for style engine
 */
@Composable
fun StyleDialog(
    isOpen: Boolean,
    styleFiles: List<StorageItem>,
    soundfonts: List<StorageItem>,
    selectedStyleName: String,
    isStylePlaying: Boolean,
    styleVolume: Float,
    onStyleVolumeChange: (Float) -> Unit,
    activeTab: String,
    onTabChange: (String) -> Unit,
    onSelectStyleFile: (StorageItem) -> Unit,
    onSelectSf2Source: (StorageItem) -> Unit,
    fxLow: Float,
    fxMid: Float,
    fxHigh: Float,
    reverbMix: Float,
    onFxLowChange: (Float) -> Unit,
    onFxMidChange: (Float) -> Unit,
    onFxHighChange: (Float) -> Unit,
    onReverbMixChange: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x730A0B12))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(240, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.95f)
        ) {
            Box(
                modifier = Modifier
                    .size(460.dp) // Clean square form factor
                    .shadow(24.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF221F38), Color(0xFF161424), Color(0xFF0F0E18))
                        )
                    )
                    .border(1.2.dp, Color(0x66818CF8), RoundedCornerShape(20.dp))
                    .clickable(enabled = false) {}
                    .padding(14.dp)
                    .testTag("dialog_style_arranger")
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "🎹", fontSize = 16.sp)
                            Column {
                                Text(
                                    text = "ARRANGEUR DE STYLES (.STY)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Style actif: $selectedStyleName",
                                    fontSize = 8.5.sp,
                                    color = if (isStylePlaying) Color(0xFF818CF8) else TextDim
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x14FFFFFF))
                                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(8.dp))
                                .clickable { onClose() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "✕", fontSize = 12.sp, color = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tab Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("files" to "Fichiers .STY", "effects" to "Effets / EQ", "sf2" to "Source SF2").forEach { (tabKey, tabLabel) ->
                            val isSelected = activeTab == tabKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) Brush.verticalGradient(listOf(Color(0xFF6366F1), Color(0xFF4F46E5))) else Brush.linearGradient(listOf(Color(0x0DFFFFFF), Color(0x08FFFFFF)))
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color.Transparent else Color(0x1AFFFFFF),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onTabChange(tabKey) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else TextDim
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tab Content
                    when (activeTab) {
                        "files" -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "DOSSIER /LiveKeys/Styles",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDim,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "${styleFiles.size} styles trouvés",
                                        fontSize = 8.5.sp,
                                        color = TextDim2
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                if (styleFiles.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x08FFFFFF))
                                            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(text = "📁", fontSize = 28.sp)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Aucun fichier de style trouvé",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Déposez vos styles Yamaha (.sty, .prs, .mid)\ndans le dossier /LiveKeys/Styles",
                                                fontSize = 9.sp,
                                                color = TextDim,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(styleFiles) { file ->
                                            val isSel = selectedStyleName == file.name.substringBeforeLast(".")
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSel) Color(0x2E6366F1) else Color(0x0AFFFFFF))
                                                    .border(1.dp, if (isSel) Color(0xFF818CF8) else Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                                                    .clickable { onSelectStyleFile(file) }
                                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSel) Color(0xFF818CF8) else Color(0x22818CF8)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (isSel && isStylePlaying) "❚❚" else "▶",
                                                        fontSize = 8.5.sp,
                                                        color = if (isSel) Color(0xFF0F0E18) else Color.White
                                                    )
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = file.name,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isSel) Color.White else TextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${file.formattedSize} · Yamaha Arranger Style",
                                                        fontSize = 8.sp,
                                                        color = TextDim2
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "effects" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "ÉGALISEUR & MIX DE L'ACCOMPAGNEMENT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDim
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StyleSliderRow(label = "BASS (LOW)", value = fxLow, onValueChange = onFxLowChange)
                                    StyleSliderRow(label = "MID", value = fxMid, onValueChange = onFxMidChange)
                                    StyleSliderRow(label = "TREBLE (HIGH)", value = fxHigh, onValueChange = onFxHighChange)
                                    StyleSliderRow(label = "REVERB MIX", value = reverbMix, onValueChange = onReverbMixChange)
                                }
                            }
                        }

                        "sf2" -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "SOUNDFONT ASSIGNÉ AUX PISTES D'ACCOMPAGNEMENT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDim
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                if (soundfonts.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x08FFFFFF))
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Déposez vos .sf2 dans /LiveKeys/SoundFonts",
                                            fontSize = 9.5.sp,
                                            color = TextDim
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(soundfonts) { sf2 ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0x0AFFFFFF))
                                                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                                                    .clickable { onSelectSf2Source(sf2) }
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(text = "📦", fontSize = 12.sp)
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = sf2.name,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = TextPrimary
                                                    )
                                                    Text(
                                                        text = sf2.formattedSize,
                                                        fontSize = 8.sp,
                                                        color = TextDim2
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
}

@Composable
private fun StyleSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.width(90.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF818CF8),
                activeTrackColor = Color(0xFF6366F1),
                inactiveTrackColor = Color(0x1AFFFFFF)
            ),
            modifier = Modifier.weight(1f)
        )
    }
}
