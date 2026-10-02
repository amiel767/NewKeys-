package com.soundstage.mixer.ui.pages

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SheetsPageDarkBg = Color(0xFF0F121C)
private val StudioBlue = Color(0xFF3B82F6)
private val CardBorderDark = Color(0xFF2C3549)
private val ReliefBgBrush = Brush.verticalGradient(
    listOf(Color(0xFF222836), Color(0xFF191F2C))
)

@Composable
fun SheetsPage(
    notesText: String,
    onNotesChange: (String) -> Unit,
    transpose: Int,
    onTransposeChange: (Int) -> Unit,
    onBackToMixer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLiveViewMode by remember { mutableStateOf(false) }
    var fontSizeSp by remember { mutableStateOf(14) }

    val roots = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
    val commonChords = listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim", "C7", "G7", "F#m", "Bb", "Eb")
    val sectionTags = listOf("Intro", "Verse 1", "Verse 2", "Chorus", "Bridge", "Solo", "Outro")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SheetsPageDarkBg)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("sheets_page_root")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. TOP HEADER BAR
            SheetsTopBar(
                onBackToMixer = onBackToMixer,
                transpose = transpose,
                onTransposeChange = onTransposeChange,
                isLiveViewMode = isLiveViewMode,
                onToggleViewMode = { isLiveViewMode = !isLiveViewMode },
                fontSizeSp = fontSizeSp,
                onFontSizeChange = { fontSizeSp = it },
                onClear = { onNotesChange("") },
                onInsertSample = {
                    if (notesText.isEmpty()) {
                        onNotesChange(SAMPLE_CHORD_CHART)
                    }
                }
            )

            // 2. QUICK PALETTE BAR (Section tags & Chord chips)
            SheetsPaletteBar(
                sectionTags = sectionTags,
                chords = commonChords,
                onInsertTag = { tag ->
                    val prefix = if (notesText.isEmpty() || notesText.endsWith("\n")) "" else "\n"
                    onNotesChange("$notesText$prefix[$tag]\n")
                },
                onInsertChord = { chord ->
                    val separator = if (notesText.isEmpty() || notesText.endsWith(" ") || notesText.endsWith("\n")) "" else " "
                    onNotesChange("$notesText$separator$chord ")
                }
            )

            // 3. MAIN EDITOR / LIVE STAGE AREA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .shadow(3.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color(0x88000000))
                    .clip(RoundedCornerShape(12.dp))
                    .background(ReliefBgBrush)
                    .border(1.dp, CardBorderDark, RoundedCornerShape(12.dp))
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
                if (isLiveViewMode) {
                    // LIVE PERFORMANCE VIEW
                    LiveChordSheetViewer(
                        content = notesText,
                        fontSizeSp = fontSizeSp
                    )
                } else {
                    // EDITING MODE
                    ChordSheetEditor(
                        notesText = notesText,
                        onNotesChange = onNotesChange,
                        fontSizeSp = fontSizeSp
                    )
                }
            }

            // 4. FOOTER STATUS BAR
            SheetsFooterBar(
                notesText = notesText,
                isLiveMode = isLiveViewMode
            )
        }
    }
}

@Composable
private fun SheetsTopBar(
    onBackToMixer: () -> Unit,
    transpose: Int,
    onTransposeChange: (Int) -> Unit,
    isLiveViewMode: Boolean,
    onToggleViewMode: () -> Unit,
    fontSizeSp: Int,
    onFontSizeChange: (Int) -> Unit,
    onClear: () -> Unit,
    onInsertSample: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(2.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black, spotColor = Color(0x77000000))
            .clip(RoundedCornerShape(10.dp))
            .background(ReliefBgBrush)
            .border(1.dp, CardBorderDark, RoundedCornerShape(10.dp))
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
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Back to Mixer button
        Row(
            modifier = Modifier
                .height(34.dp)
                .shadow(2.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF283144), Color(0xFF1D2433))))
                .border(1.dp, CardBorderDark, RoundedCornerShape(8.dp))
                .clickable { onBackToMixer() }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Mixer",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Mixer",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Sheets & Chord Charts",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.weight(1f))

        // Transpose Stepper
        TransposeStepper(
            transpose = transpose,
            onTransposeChange = onTransposeChange
        )

        // Font Size Stepper
        FontSizeStepper(
            fontSizeSp = fontSizeSp,
            onFontSizeChange = onFontSizeChange
        )

        // View Mode Toggle (Live View vs Editor)
        Box(
            modifier = Modifier
                .height(34.dp)
                .shadow(2.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isLiveViewMode) Brush.verticalGradient(listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)))
                    else Brush.verticalGradient(listOf(Color(0xFF252D3D), Color(0xFF1B2230)))
                )
                .border(
                    1.dp,
                    if (isLiveViewMode) Color(0xFF60A5FA) else CardBorderDark,
                    RoundedCornerShape(8.dp)
                )
                .clickable { onToggleViewMode() }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isLiveViewMode) "Live View" else "Editor",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Sample Chart button if empty
        TextButton(
            onClick = onInsertSample,
            modifier = Modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                text = "Sample",
                fontSize = 11.sp,
                color = StudioBlue
            )
        }

        // Clear button
        TextButton(
            onClick = onClear,
            modifier = Modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                text = "Clear",
                fontSize = 11.sp,
                color = Color(0xFFEF4444)
            )
        }
    }
}

@Composable
private fun SheetsPaletteBar(
    sectionTags: List<String>,
    chords: List<String>,
    onInsertTag: (String) -> Unit,
    onInsertChord: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Sections:",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8E99AF)
        )

        sectionTags.forEach { tag ->
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
                    .clickable { onInsertTag(tag) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tag,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE2E8F0)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(18.dp)
                .background(Color.White.copy(alpha = 0.12f))
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = "Chords:",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8E99AF)
        )

        chords.forEach { chord ->
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .widthIn(min = 34.dp)
                    .shadow(1.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF283144), Color(0xFF1E2534))))
                    .border(1.dp, Color(0xFF3B4863), RoundedCornerShape(6.dp))
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
                    .clickable { onInsertChord(chord) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chord,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ChordSheetEditor(
    notesText: String,
    onNotesChange: (String) -> Unit,
    fontSizeSp: Int
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF12151F))
            .border(1.dp, Color(0xFF202636), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        if (notesText.isEmpty()) {
            Text(
                text = "Write or paste chord charts and lyrics here...\n\nExample format:\n[Intro]\nC   G   Am   F\n\n[Verse 1]\nC                G\nI close my eyes and hear the sound\nAm               F\nThe stage is alive tonight\n\n[Chorus]\nF        G        C\nSinging loud under the lights",
                color = Color(0xFF6B7280),
                fontSize = fontSizeSp.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = (fontSizeSp + 6).sp
            )
        }

        BasicTextField(
            value = notesText,
            onValueChange = onNotesChange,
            modifier = Modifier
                .fillMaxSize()
                .testTag("sheets_fullscreen_editor"),
            textStyle = TextStyle(
                color = Color.White,
                fontSize = fontSizeSp.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = (fontSizeSp + 6).sp
            ),
            cursorBrush = SolidColor(StudioBlue)
        )
    }
}

@Composable
private fun LiveChordSheetViewer(
    content: String,
    fontSizeSp: Int
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF12151F))
            .border(1.dp, Color(0xFF202636), RoundedCornerShape(8.dp))
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        if (content.isBlank()) {
            Text(
                text = "No chords or lyrics entered yet. Switch to Editor mode or tap 'Sample'.",
                color = Color(0xFF6B7280),
                fontSize = fontSizeSp.sp,
                fontFamily = FontFamily.Monospace
            )
        } else {
            val lines = content.lines()
            val annotated = buildAnnotatedString {
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    when {
                        // Section header like [Intro] or [Chorus]
                        trimmed.startsWith("[") && trimmed.endsWith("]") -> {
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFFF59E0B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (fontSizeSp + 2).sp
                                )
                            ) {
                                append(line)
                            }
                        }
                        // Heuristic chord line (short tokens, common chord patterns)
                        isLikelyChordLine(trimmed) -> {
                            withStyle(
                                SpanStyle(
                                    color = StudioBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (fontSizeSp + 1).sp
                                )
                            ) {
                                append(line)
                            }
                        }
                        // Regular lyric line
                        else -> {
                            withStyle(
                                SpanStyle(
                                    color = Color.White,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = fontSizeSp.sp
                                )
                            ) {
                                append(line)
                            }
                        }
                    }
                    if (index < lines.size - 1) {
                        append("\n")
                    }
                }
            }

            Text(
                text = annotated,
                fontFamily = FontFamily.Monospace,
                lineHeight = (fontSizeSp + 6).sp
            )
        }
    }
}

@Composable
private fun TransposeStepper(
    transpose: Int,
    onTransposeChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .shadow(2.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF222836), Color(0xFF191F2C))))
            .border(1.dp, CardBorderDark, RoundedCornerShape(8.dp))
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
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .clickable { onTransposeChange((transpose - 1).coerceIn(-12, 12)) },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "−", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.65f)
                .background(Color.White.copy(alpha = 0.12f))
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .clickable { onTransposeChange(0) },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Transp", color = Color(0xFF8E99AF), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = if (transpose > 0) "+$transpose" else "$transpose",
                    color = StudioBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.65f)
                .background(Color.White.copy(alpha = 0.12f))
        )

        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .clickable { onTransposeChange((transpose + 1).coerceIn(-12, 12)) },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "+", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FontSizeStepper(
    fontSizeSp: Int,
    onFontSizeChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .shadow(2.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF222836), Color(0xFF191F2C))))
            .border(1.dp, CardBorderDark, RoundedCornerShape(8.dp))
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
        Box(
            modifier = Modifier
                .width(26.dp)
                .fillMaxHeight()
                .clickable { onFontSizeChange((fontSizeSp - 1).coerceIn(10, 24)) },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "−", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.65f)
                .background(Color.White.copy(alpha = 0.12f))
        )

        Box(
            modifier = Modifier.padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${fontSizeSp}sp",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.65f)
                .background(Color.White.copy(alpha = 0.12f))
        )

        Box(
            modifier = Modifier
                .width(26.dp)
                .fillMaxHeight()
                .clickable { onFontSizeChange((fontSizeSp + 1).coerceIn(10, 24)) },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "+", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SheetsFooterBar(
    notesText: String,
    isLiveMode: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Mode: ${if (isLiveMode) "Live Performance View" else "Editor Mode"}",
            fontSize = 11.sp,
            color = Color(0xFF8E99AF)
        )
        Text(
            text = "${notesText.length} characters • ${notesText.lines().size} lines",
            fontSize = 11.sp,
            color = Color(0xFF8E99AF)
        )
    }
}

private fun isLikelyChordLine(line: String): Boolean {
    if (line.isBlank()) return false
    val tokens = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return false
    val chordRegex = "^[A-G][b#]?(m|maj|min|dim|aug|sus[24]?|[0-9])?(/[A-G][b#]?)?$".toRegex()
    val matchCount = tokens.count { token -> chordRegex.matches(token) }
    return matchCount >= (tokens.size * 0.6f).coerceAtLeast(1f)
}

private const val SAMPLE_CHORD_CHART = """[Intro]
C   G   Am   F

[Verse 1]
C                G
Walking through the city night
Am               F
Every melody feels so right
C                G
Keys are glowing on the board
Am               F
Finding every perfect chord

[Chorus]
F        G        C        Am
SoundStage playing loud and clear
F        G        C
Music rising far and near

[Bridge]
Dm               Em
Hold the note and let it ring
F                G
Hear the whole arena sing

[Outro]
C   G   Am   F
C
"""
