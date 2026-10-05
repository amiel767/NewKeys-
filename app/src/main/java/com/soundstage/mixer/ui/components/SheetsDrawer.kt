package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.R
import com.soundstage.mixer.model.SheetItem

private val SnapshotAccentColor = Color(0xFF9BC0F8)

@Composable
fun SheetsDrawer(
    isOpen: Boolean,
    onClose: () -> Unit,
    sheets: List<SheetItem>,
    activeSheetId: String?,
    onSelectSheet: (String) -> Unit,
    onCreateSheet: (title: String, key: String, content: String) -> Unit,
    onUpdateSheet: (id: String, title: String, key: String, content: String) -> Unit,
    onDeleteSheet: (id: String) -> Unit,
    notesText: String,
    onNotesChange: (String) -> Unit,
    transpose: Int,
    onTransposeChange: (Int) -> Unit,
    onOpenFullPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    // When a sheet card is tapped, it opens in its own page INSIDE this overlay
    var viewingSheetId by remember { mutableStateOf<String?>(null) }

    val activeViewingSheet = remember(sheets, viewingSheetId) {
        sheets.find { it.id == viewingSheetId }
    }

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
                .clickable {
                    viewingSheetId = null
                    onClose()
                }
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
                    .clickable(enabled = false) {}
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .testTag("sheets_drawer_content")
            ) {
                if (activeViewingSheet != null) {
                    // DEDICATED SHEET VIEW INSIDE THE OVERLAY
                    SheetOverlayDetailView(
                        sheet = activeViewingSheet,
                        onBack = { viewingSheetId = null },
                        onOpenFullStudio = {
                            onSelectSheet(activeViewingSheet.id)
                            onOpenFullPage()
                        },
                        onSave = { updatedTitle, updatedKey, updatedContent ->
                            onUpdateSheet(activeViewingSheet.id, updatedTitle, updatedKey, updatedContent)
                        },
                        onDelete = {
                            onDeleteSheet(activeViewingSheet.id)
                            viewingSheetId = null
                        }
                    )
                } else {
                    // OVERLAY LIST OF SHEETS (No + button, stacked books icon opens full studio)
                    Column(modifier = Modifier.fillMaxSize()) {
                        SheetsOverlayHeader(
                            count = sheets.size,
                            onOpenFullPage = onOpenFullPage,
                            onClose = onClose
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (sheets.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x14FFFFFF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_sheet_books),
                                            contentDescription = "Empty Sheets",
                                            tint = Color(0x66FFFFFF),
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Text(
                                        text = "No sheets in library",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Tap the books icon above to open full Sheets studio",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(sheets, key = { it.id }) { sheet ->
                                    SheetOverlayCard(
                                        sheet = sheet,
                                        onClick = {
                                            // Opens inside the overlay, not jumping out to the page
                                            viewingSheetId = sheet.id
                                            onSelectSheet(sheet.id)
                                        }
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

// ======================================================================================
// OVERLAY HEADER
// ======================================================================================
@Composable
private fun SheetsOverlayHeader(
    count: Int,
    onOpenFullPage: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Sheets",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "$count sheet${if (count > 1) "s" else ""} in library",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SnapshotAccentColor)
                    .clickable { onOpenFullPage() }
                    .testTag("btn_sheets_full_page"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_sheet_books),
                    contentDescription = "Open Sheets Studio",
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(19.dp)
                )
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
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ======================================================================================
// SHEET OVERLAY CARD (AOSP Relief Style with Key Chip & Large Formatted Preview)
// ======================================================================================
@Composable
private fun SheetOverlayCard(
    sheet: SheetItem,
    onClick: () -> Unit
) {
    val keyColor = sheet.getKeyColor()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black, spotColor = Color(0x66000000))
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF222836), Color(0xFF191F2C))
                )
            )
            .drawWithContent {
                drawContent()
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(6.dp.toPx(), 1f),
                    end = Offset(size.width - 6.dp.toPx(), 1f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            .clickable { onClick() }
            .padding(14.dp)
            .testTag("sheet_card_${sheet.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = sheet.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(keyColor.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = sheet.key,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = keyColor
                    )
                }
            }

            if (sheet.content.isNotEmpty()) {
                val annotatedContent = buildAnnotatedString {
                    val lines = sheet.content.lines().take(4)
                    lines.forEachIndexed { i, line ->
                        if (line.trim().startsWith("[") && line.contains("]")) {
                            val tag = line.substringBefore("]") + "]"
                            val rest = line.substringAfter("]")
                            withStyle(SpanStyle(color = Color(0xFF60A5FA), fontWeight = FontWeight.SemiBold)) {
                                append(tag)
                            }
                            withStyle(SpanStyle(color = Color(0xFFCBD5E1))) {
                                append(rest)
                            }
                        } else {
                            withStyle(SpanStyle(color = Color(0xFF94A3B8))) {
                                append(line)
                            }
                        }
                        if (i < lines.size - 1) append("\n")
                    }
                }

                Text(
                    text = annotatedContent,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "No chords or notes written yet",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

// ======================================================================================
// DEDICATED SHEET DETAIL VIEW INSIDE THE OVERLAY
// Opens right inside the overlay without navigating out
// ======================================================================================
@Composable
private fun SheetOverlayDetailView(
    sheet: SheetItem,
    onBack: () -> Unit,
    onOpenFullStudio: () -> Unit,
    onSave: (title: String, key: String, content: String) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(sheet.title) }
    var content by remember { mutableStateOf(sheet.content) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Detail Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to list",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    cursorBrush = SolidColor(SnapshotAccentColor),
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Key badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(sheet.getKeyColor().copy(alpha = 0.20f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = sheet.key,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = sheet.getKeyColor()
                    )
                }

                // Open in full studio button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SnapshotAccentColor)
                        .clickable { onOpenFullStudio() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_sheet_books),
                        contentDescription = "Full Studio",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Delete button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SnapshotAccentColor)
                        .clickable { showDeleteConfirm = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Sheet Content Display & Editor inside overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E2435))
                .padding(14.dp)
        ) {
            BasicTextField(
                value = content,
                onValueChange = { content = it },
                textStyle = TextStyle(
                    color = sheet.getTextColor(),
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(SnapshotAccentColor),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                decorationBox = { innerTextField ->
                    if (content.isEmpty()) {
                        Text(
                            text = "No notes written yet.\nType your chords & lyrics here...",
                            fontSize = 14.sp,
                            color = Color(0x66FFFFFF)
                        )
                    }
                    innerTextField()
                }
            )
        }

        // Save inside overlay
        Button(
            colors = ButtonDefaults.buttonColors(containerColor = SnapshotAccentColor),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onSave(title, sheet.key, content)
                onBack()
            }
        ) {
            Text(
                text = "Save Sheet",
                color = Color(0xFF0F172A),
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Sheet", color = Color.White) },
            text = { Text("Are you sure you want to delete \"${sheet.title}\"?", color = Color(0xFFCBD5E1)) },
            containerColor = Color(0xFF1E2435),
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = SnapshotAccentColor),
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}
