package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.R
import com.soundstage.mixer.model.FileManager
import com.soundstage.mixer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modern Google Keep-style Notes & ChordPro Floating Component
 * - Single subtle border, no heavy double frames, no obstructive header titles
 * - Instant Note Creation on "+ Nouvelle note"
 * - Full internal storage support (zero Scoped Storage permission barriers)
 * - ChordPro syntax highlighting and live transposition (+/- semitones)
 * - Auto-scroll engine with adjustable speed for live performance
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NotesDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    detectedChord: DetectedChord?,
    selectedRootKey: String = "C",
    useFlats: Boolean = false,
    notesDir: File? = null,
    fileManager: FileManager? = null,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Internal app directory for notes (bypasses Scoped Storage restrictions)
    val actualNotesDir = remember(notesDir) {
        notesDir ?: File(context.getExternalFilesDir(null) ?: context.filesDir, "Notes").apply {
            if (!exists()) mkdirs()
        }
    }

    var currentView by remember { mutableIntStateOf(1) } // 1: Keep Grid List, 2: Note Editor
    var activeNoteFile by remember { mutableStateOf<File?>(null) }
    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var transposeSemitones by remember { mutableIntStateOf(0) }
    var isAutoScrolling by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(1.0f) }
    var showCopyFeedback by remember { mutableStateOf(false) }
    var fileToRename by remember { mutableStateOf<File?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showRomanNotation by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Refresh note list
    var noteFiles by remember {
        mutableStateOf(
            actualNotesDir.listFiles { _, name -> name.endsWith(".txt") || name.endsWith(".chordpro") || name.endsWith(".chopro") }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        )
    }

    fun refreshFiles() {
        noteFiles = actualNotesDir.listFiles { _, name -> name.endsWith(".txt") || name.endsWith(".chordpro") || name.endsWith(".chopro") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    // Auto-save active note
    fun saveActiveNote() {
        val file = activeNoteFile ?: return
        try {
            val content = if (noteTitle.isNotBlank()) "# $noteTitle\n\n$noteBody" else noteBody
            file.writeText(content)
            refreshFiles()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteActiveNote() {
        val file = activeNoteFile
        if (file != null && file.exists()) {
            try {
                file.delete()
                activeNoteFile = null
                currentView = 1
                refreshFiles()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Open an existing note
    fun openNote(file: File) {
        activeNoteFile = file
        transposeSemitones = 0
        isAutoScrolling = false
        try {
            val lines = file.readLines()
            if (lines.isNotEmpty() && lines[0].startsWith("# ")) {
                noteTitle = lines[0].removePrefix("# ").trim()
                noteBody = lines.drop(1).joinToString("\n").trimStart('\n')
            } else {
                noteTitle = file.nameWithoutExtension
                noteBody = file.readText()
            }
            currentView = 2
        } catch (e: Exception) {
            noteTitle = file.nameWithoutExtension
            noteBody = ""
            currentView = 2
        }
    }

    // Create a new note immediately
    fun createNewNote() {
        try {
            if (!actualNotesDir.exists()) actualNotesDir.mkdirs()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val newFile = File(actualNotesDir, "Note_$timeStamp.txt")
            newFile.writeText("# Nouvelle note\n\n")
            noteTitle = "Nouvelle note"
            noteBody = ""
            activeNoteFile = newFile
            transposeSemitones = 0
            isAutoScrolling = false
            refreshFiles()
            currentView = 2
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Auto-scroll loop
    LaunchedEffect(isAutoScrolling, scrollSpeed) {
        if (isAutoScrolling) {
            while (isAutoScrolling && scrollState.value < scrollState.maxValue) {
                val nextVal = (scrollState.value + (2 * scrollSpeed).toInt()).coerceAtMost(scrollState.maxValue)
                scrollState.scrollTo(nextVal)
                delay(40L)
            }
            if (scrollState.value >= scrollState.maxValue) {
                isAutoScrolling = false
            }
        }
    }

    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val flatNames = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    val flatToSharp = mapOf("Db" to "C#", "Eb" to "D#", "Gb" to "F#", "Ab" to "G#", "Bb" to "A#")
    val sharpToFlat = flatToSharp.entries.associate { (k, v) -> v to k }

    fun transposeText(text: String, semitones: Int): String {
        if (semitones == 0 && !useFlats) return text
        val chordRegex = Regex("""\b([A-G][b#]?)(maj7|min7|m7|7|m|maj|dim|aug|sus2|sus4|add9|9|11|13|m9|ø7|6)?(/[A-G][b#]?)?\b""")
        return chordRegex.replace(text) { match ->
            val root = match.groupValues[1]
            val suffix = match.groupValues[2]
            val bass = match.groupValues[3]

            val standardizedRoot = flatToSharp[root] ?: root
            val rootIndex = noteNames.indexOf(standardizedRoot)
            val newRoot = if (rootIndex != -1) {
                val newIndex = (rootIndex + semitones).mod(12)
                if (useFlats) flatNames[newIndex] else noteNames[newIndex]
            } else root

            val newBass = if (bass.isNotEmpty()) {
                val bassNote = bass.substring(1)
                val stdBass = flatToSharp[bassNote] ?: bassNote
                val bassIndex = noteNames.indexOf(stdBass)
                if (bassIndex != -1) {
                    val newIndex = (bassIndex + semitones).mod(12)
                    "/" + (if (useFlats) flatNames[newIndex] else noteNames[newIndex])
                } else bass
            } else ""

            "$newRoot$suffix$newBass"
        }
    }

    // Convert Harmonic Degrees (All Arabic 1, 2m, 3mineur, 4aug, 5dim... and Roman I, ii, IV, V, etc.)
    fun convertDegreesToChords(text: String, rootKey: String): String {
        return HarmonicProgressionCalculator.convertAllDegreesToChords(text, rootKey, useFlats)
    }

    // Modern Minimalist Card Container (Spacious, single border controlled by parent window)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141824))
            .padding(14.dp)
            .testTag("notes_google_keep_panel")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (currentView == 1) {
                // ================= GOOGLE KEEP VIEW 1: NOTE EXPLORER =================
                // Sleek Header with Quill Icon, Search & + Nouvelle note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0x2200E5FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_quill),
                                contentDescription = "Notes",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Notes & Grilles",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // "+ Nouvelle note" Button (Google Keep style)
                    Button(
                        onClick = { createNewNote() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = Color(0xFF003844)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("btn_new_note")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(text = "New Note", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Spacer(modifier = Modifier.height(10.dp))

                // Notes Keep Cards Grid
                val filteredNotes = remember(noteFiles, searchQuery) {
                    if (searchQuery.isBlank()) noteFiles
                    else noteFiles.filter { it.name.contains(searchQuery, ignoreCase = true) || (try { it.readText().contains(searchQuery, ignoreCase = true) } catch (_: Exception) { false }) }
                }

                if (filteredNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_quill),
                                contentDescription = null,
                                tint = TextDim2,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Aucune note enregistrée",
                                fontSize = 12.sp,
                                color = TextDim,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Touchez \"+ Nouvelle note\" pour commencer",
                                fontSize = 10.5.sp,
                                color = TextDim2
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredNotes) { file ->
                            val fileContent = remember(file, file.lastModified()) {
                                try { file.readLines().take(4).joinToString("\n") } catch (_: Exception) { "" }
                            }
                            val displayName = remember(file, file.lastModified()) {
                                try {
                                    val first = file.readLines().firstOrNull() ?: ""
                                    if (first.startsWith("# ")) first.removePrefix("# ") else file.nameWithoutExtension
                                } catch (_: Exception) { file.nameWithoutExtension }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1B2030))
                                    .border(1.dp, Color(0x2200E5FF), RoundedCornerShape(10.dp))
                                    .combinedClickable(
                                        onClick = { openNote(file) },
                                        onLongClick = {
                                            renameInput = file.nameWithoutExtension
                                            fileToRename = file
                                        }
                                    )
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = displayName,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Supprimer",
                                            tint = TextDim2,
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clickable {
                                                    file.delete()
                                                    refreshFiles()
                                                }
                                        )
                                    }
                                    Text(
                                        text = fileContent.ifEmpty { "Note vide..." },
                                        fontSize = 10.sp,
                                        color = TextDim,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ================= GOOGLE KEEP VIEW 2: NOTE & CHORDPRO EDITOR =================
                // Clean Top Action Bar: Back Arrow, Title, Transpose & Auto-Scroll
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                saveActiveNote()
                                currentView = 1
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Retour",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Inline Editable Title
                        BasicTextField(
                            value = noteTitle,
                            onValueChange = {
                                noteTitle = it
                                saveActiveNote()
                            },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(NeonCyan),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (noteTitle.isEmpty()) {
                                    Text(text = "Titre de la note...", color = TextDim2, fontSize = 13.sp)
                                }
                                innerTextField()
                            }
                        )
                    }

                    // Editor Toolbar Actions: Transpose, Auto-Scroll, Degrees, Copy
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Transpose Buttons [-] [0] [+]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E2333))
                                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(6.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "−",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                modifier = Modifier
                                    .clickable { transposeSemitones-- }
                                    .padding(horizontal = 5.dp)
                            )
                            Text(
                                text = if (transposeSemitones == 0) "TRANS" else "${if (transposeSemitones > 0) "+$transposeSemitones" else transposeSemitones}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (transposeSemitones != 0) NeonCyan else TextDim
                            )
                            Text(
                                text = "+",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                modifier = Modifier
                                    .clickable { transposeSemitones++ }
                                    .padding(horizontal = 5.dp)
                            )
                        }

                        // Auto-scroll toggle
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isAutoScrolling) Color(0x3300E5FF) else Color(0xFF1E2333))
                                .border(1.dp, if (isAutoScrolling) NeonCyan else Color(0x22FFFFFF), RoundedCornerShape(6.dp))
                                .clickable { isAutoScrolling = !isAutoScrolling }
                                .padding(horizontal = 7.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (isAutoScrolling) "❚❚ DÉFILÉ" else "▶ DÉFILÉ",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAutoScrolling) NeonCyan else TextDim
                            )
                        }

                        // Copy / Share
                        IconButton(
                            onClick = {
                                val currentText = if (transposeSemitones != 0) transposeText(noteBody, transposeSemitones) else noteBody
                                clipboardManager.setText(AnnotatedString(currentText))
                                showCopyFeedback = true
                                coroutineScope.launch {
                                    delay(1500)
                                    showCopyFeedback = false
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = if (showCopyFeedback) Icons.Default.Check else Icons.Default.Share,
                                contentDescription = "Copy",
                                tint = if (showCopyFeedback) NeonCyan else TextDim,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Delete Note Button
                        IconButton(
                            onClick = { deleteActiveNote() },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Note",
                                tint = Color(0xFFFF4444),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Main Note Body Editor with ChordPro formatting & Transpose view
                val effectiveBody = remember(noteBody, transposeSemitones) {
                    if (transposeSemitones != 0) transposeText(noteBody, transposeSemitones) else noteBody
                }

                // Real-time Harmonic & Progression Analysis Bar
                val extractedChords = remember(effectiveBody) {
                    HarmonicProgressionCalculator.extractChordsFromText(effectiveBody)
                }
                val progressionAnalysis = remember(extractedChords, selectedRootKey) {
                    HarmonicProgressionCalculator.analyzeProgression(extractedChords, selectedRootKey)
                }

                if (extractedChords.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D111A))
                            .border(1.dp, Color(0x2600E5FF), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "TONALITÉ: $selectedRootKey",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan
                                )
                                if (progressionAnalysis.progressionName != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0x2200E5FF))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = progressionAnalysis.progressionName,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFE2E8F0)
                                        )
                                    }
                                }
                            }

                            // Roman Notation Toggle
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (showRomanNotation) NeonCyan.copy(alpha = 0.25f) else Color(0xFF1E2333))
                                    .border(1.dp, if (showRomanNotation) NeonCyan else Color(0x33FFFFFF), RoundedCornerShape(5.dp))
                                    .clickable { showRomanNotation = !showRomanNotation }
                                    .padding(horizontal = 6.dp, vertical = 2.5.dp)
                            ) {
                                Text(
                                    text = if (showRomanNotation) "ROMAIN (I, ii)" else "ACCORDS",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (showRomanNotation) NeonCyan else TextDim
                                )
                            }
                        }

                        // Horizontal list of analyzed chords with passing chord badges
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            progressionAnalysis.chords.forEach { ac ->
                                val displayText = if (showRomanNotation) ac.romanNumeral else ac.originalChord
                                if (ac.isPassingChord) {
                                    // Highlighted Amber LED for Passing Chord ("Accord de passage")
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(0x33FFB300))
                                            .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(5.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(
                                                text = "⚡ $displayText",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFFFD54F)
                                            )
                                            if (ac.passingDescription != null) {
                                                Text(
                                                    text = "(${ac.passingDescription})",
                                                    fontSize = 8.sp,
                                                    color = Color(0xFFFFCA28)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Diatonic / Standard degree badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(0xFF161C2C))
                                            .border(1.dp, if (ac.isDiatonic) Color(0x3300E5FF) else Color(0x22FFFFFF), RoundedCornerShape(5.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = displayText,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (ac.isDiatonic) Color.White else TextDim
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F121C))
                        .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                        .verticalScroll(scrollState)
                ) {
                    BasicTextField(
                        value = effectiveBody,
                        onValueChange = { newText ->
                            if (transposeSemitones == 0) {
                                noteBody = newText
                                saveActiveNote()
                            }
                        },
                        readOnly = (transposeSemitones != 0),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        ),
                        cursorBrush = SolidColor(NeonCyan),
                        modifier = Modifier.fillMaxSize(),
                        decorationBox = { innerTextField ->
                            if (effectiveBody.isEmpty()) {
                                Text(
                                    text = "Écrivez les paroles et accords ici...\nExemple:\n[C] Amazing [G] grace how [Am] sweet the [F] sound\n\n(Ou tapez les degrés 1 4 5 6m)",
                                    color = TextDim2,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                // Bottom Assistant Bar: Quick chord insert & Degree converter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Quick Insert Current Live Detected Chord
                    if (detectedChord != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x2200E5FF))
                                .border(1.dp, NeonCyan, RoundedCornerShape(6.dp))
                                .clickable {
                                    val chordName = detectedChord.primaryName
                                    noteBody += " [$chordName] "
                                    saveActiveNote()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "+ Insérer ${detectedChord.primaryName}",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }
                    } else {
                        Text(
                            text = "Format ChordPro: [C] Paroles...",
                            fontSize = 9.sp,
                            color = TextDim2
                        )
                    }

                    // Degrees to Chords Converter Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E2333))
                            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(6.dp))
                            .clickable {
                                noteBody = convertDegreesToChords(noteBody, selectedRootKey)
                                saveActiveNote()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Convertir Degrés (3m, 4aug, 5dim, ii...)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonCyan
                        )
                    }
                }
            }
        }

        if (fileToRename != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { fileToRename = null },
                title = { Text("Renommer la partition", color = Color.White) },
                text = {
                    BasicTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(NeonCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E2333), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            val f = fileToRename
                            if (f != null && renameInput.isNotBlank()) {
                                val newFile = java.io.File(f.parentFile, "${renameInput.trim()}.${f.extension}")
                                f.renameTo(newFile)
                                refreshFiles()
                            }
                            fileToRename = null
                        }
                    ) {
                        Text("Renommer", color = NeonCyan)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { fileToRename = null }) {
                        Text("Annuler", color = TextDim)
                    }
                },
                containerColor = Color(0xFF111522)
            )
        }
    }
}
