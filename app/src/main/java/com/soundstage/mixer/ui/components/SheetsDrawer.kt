package com.soundstage.mixer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.R

private val StudioAccent = Color(0xFF3B82F6)
private val CardBorderColor = Color(0xFF2C3549)
private val CardBgBrush = Brush.verticalGradient(
    listOf(Color(0xFF222836), Color(0xFF191F2C))
)

@Composable
fun SheetsDrawer(
    isOpen: Boolean,
    onClose: () -> Unit,
    notesText: String,
    onNotesChange: (String) -> Unit,
    transpose: Int,
    onTransposeChange: (Int) -> Unit,
    onOpenFullPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isOpen) {
        val scrimAlpha by animateFloatAsState(
            targetValue = 0.55f,
            animationSpec = tween(260),
            label = "sheets_scrim_fade"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable { onClose() }
                .testTag("sheets_drawer_scrim")
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
                    .widthIn(min = 400.dp, max = 540.dp)
                    .fillMaxWidth(0.58f)
                    .shadow(32.dp, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .background(Color(0xFF181C28))
                    .border(1.dp, CardBorderColor, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                    .clickable(enabled = false) {}
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .testTag("sheets_drawer_content")
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. Header with Title, Quill Button (Full Page), and Close Button
                    SheetsDrawerHeader(
                        onOpenFullPage = onOpenFullPage,
                        onClose = onClose
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable Drawer Content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Section 1: Transpose & Quick Section Tags
                        SheetsReliefCard(title = "TRANSPOSE & SECTIONS") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Stepper with fine vertical divider lines
                                StepperTransposeControl(
                                    label = "Transpose",
                                    value = if (transpose > 0) "+$transpose" else "$transpose",
                                    onMinus = { onTransposeChange((transpose - 1).coerceIn(-12, 12)) },
                                    onPlus = { onTransposeChange((transpose + 1).coerceIn(-12, 12)) },
                                    onReset = { onTransposeChange(0) }
                                )

                                // Quick section chips
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf("Intro", "Verse", "Chorus", "Bridge", "Outro").forEach { tag ->
                                        TactileSectionChip(
                                            label = tag,
                                            onClick = {
                                                val prefix = if (notesText.isEmpty() || notesText.endsWith("\n")) "" else "\n"
                                                onNotesChange("$notesText$prefix[$tag]\n")
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Section 2: Quick Chords Bar
                        val chordsList = listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim", "C7", "G7", "F#m", "Bb", "Eb")
                        SheetsReliefCard(title = "QUICK CHORDS PALETTE") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                chordsList.forEach { chord ->
                                    TactileChordChip(
                                        chord = chord,
                                        onClick = {
                                            val separator = if (notesText.isEmpty() || notesText.endsWith(" ") || notesText.endsWith("\n")) "" else " "
                                            onNotesChange("$notesText$separator$chord ")
                                        }
                                    )
                                }
                            }
                        }

                        // Section 3: Notes & Chords Quick Editor
                        SheetsReliefCard(title = "CHORDS & NOTES SHEET") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF12151F))
                                        .border(1.dp, Color(0xFF22293A), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    if (notesText.isEmpty()) {
                                        Text(
                                            text = "Write or paste lyrics & chord charts here...\nExample:\n[Intro]\nC  G  Am  F\n\n[Verse 1]\nC           G\nFirst line of the song\nAm          F\nSecond line of the song",
                                            color = Color(0xFF6B7280),
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 18.sp
                                        )
                                    }
                                    BasicTextField(
                                        value = notesText,
                                        onValueChange = onNotesChange,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("sheets_quick_editor"),
                                        textStyle = TextStyle(
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 18.sp
                                        ),
                                        cursorBrush = SolidColor(StudioAccent)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${notesText.length} characters • ${notesText.lines().size} lines",
                                        fontSize = 10.sp,
                                        color = Color(0xFF8E99AF)
                                    )

                                    if (notesText.isNotEmpty()) {
                                        TextButton(
                                            onClick = { onNotesChange("") },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text(
                                                text = "Clear",
                                                fontSize = 11.sp,
                                                color = Color(0xFFEF4444)
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

@Composable
private fun SheetsDrawerHeader(
    onOpenFullPage: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Sheets & Chords",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Quick chord reference & song lyrics",
                color = Color(0xFF8E99AF),
                fontSize = 11.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Small Quill Button to Open Full Page with tactile relief
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(2.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color(0x99000000))
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBgBrush)
                    .border(1.dp, CardBorderColor, RoundedCornerShape(8.dp))
                    .drawWithContent {
                        drawContent()
                        drawLine(
                            color = Color.White.copy(alpha = 0.12f),
                            start = Offset(4.dp.toPx(), 1f),
                            end = Offset(size.width - 4.dp.toPx(), 1f),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    .clickable { onOpenFullPage() }
                    .testTag("btn_sheets_open_full"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_quill),
                    contentDescription = "Open Full Page",
                    tint = StudioAccent,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Close Button
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222836))
                    .border(1.dp, Color(0xFF2C3549), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Sheets",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SheetsReliefCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color(0x88000000))
            .clip(RoundedCornerShape(12.dp))
            .background(CardBgBrush)
            .border(1.dp, CardBorderColor, RoundedCornerShape(12.dp))
            .drawWithContent {
                drawContent()
                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = Offset(8.dp.toPx(), 1f),
                    end = Offset(size.width - 8.dp.toPx(), 1f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            .padding(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8E99AF),
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun StepperTransposeControl(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .shadow(2.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color(0x66000000))
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF222836), Color(0xFF191F2C))))
            .border(1.dp, Color(0xFF2C3549), RoundedCornerShape(8.dp))
            .drawWithContent {
                drawContent()
                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = Offset(4.dp.toPx(), 1f),
                    end = Offset(size.width - 4.dp.toPx(), 1f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Minus Button
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .clickable { onMinus() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "−",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Fine vertical divider line
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.65f)
                .background(Color.White.copy(alpha = 0.12f))
        )

        // Center Value & Label
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .clickable { onReset() },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = label,
                    color = Color(0xFF8E99AF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    color = StudioAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Fine vertical divider line
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.65f)
                .background(Color.White.copy(alpha = 0.12f))
        )

        // Plus Button
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .clickable { onPlus() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TactileSectionChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .shadow(1.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF252D3D), Color(0xFF1B2230))))
            .border(1.dp, Color(0xFF333E54), RoundedCornerShape(6.dp))
            .drawWithContent {
                drawContent()
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(2.dp.toPx(), 1f),
                    end = Offset(size.width - 2.dp.toPx(), 1f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE2E8F0)
        )
    }
}

@Composable
private fun TactileChordChip(
    chord: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .widthIn(min = 36.dp)
            .shadow(2.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF263044), Color(0xFF1B2232))))
            .border(1.dp, Color(0xFF374460), RoundedCornerShape(6.dp))
            .drawWithContent {
                drawContent()
                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = Offset(2.dp.toPx(), 1f),
                    end = Offset(size.width - 2.dp.toPx(), 1f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = chord,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
