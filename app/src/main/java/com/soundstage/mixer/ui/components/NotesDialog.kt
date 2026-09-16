package com.soundstage.mixer.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundstage.mixer.model.FileManager
import com.soundstage.mixer.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Notes, Chords & Setlist Component (Section 3)
 * Non-modal anchored overlay inside MixerScreen with 2 Interior Views:
 * - View 1: Notes List / Explorer (/SoundStage/Notes/)
 * - View 2: Note Editor & Harmonic Analysis / Transposition Page
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesDialog(
    isOpen: Boolean,
    onClose: () -> Unit,
    detectedChord: DetectedChord?,
    selectedRootKey: String = "C",
    notesDir: File? = null,
    fileManager: FileManager? = null,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val clipboardManager = LocalClipboardManager.current
    var currentView by remember { mutableIntStateOf(1) } // 1: List Explorer, 2: Note Editor
    var selectedNoteFile by remember { mutableStateOf<File?>(null) }
    var noteContent by remember { mutableStateOf("") }
    var newNoteName by remember { mutableStateOf("") }
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var showCopyFeedback by remember { mutableStateOf(false) }

    // Ensure notes directory exists
    val actualNotesDir = remember(notesDir) {
        notesDir ?: File("/storage/emulated/0/SoundStage/Notes").apply { if (!exists()) mkdirs() }
    }

    var noteFiles by remember(currentView) {
        mutableStateOf(
            actualNotesDir.listFiles { _, name -> name.endsWith(".txt") || name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        )
    }

    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val flatToSharp = mapOf("Db" to "C#", "Eb" to "D#", "Gb" to "F#", "Ab" to "G#", "Bb" to "A#")

    // Transposition Logic
    fun transposeText(text: String, semitones: Int): String {
        if (semitones == 0) return text
        val chordRegex = Regex("""\b([A-G][b#]?)(maj7|min7|m7|7|m|maj|dim|aug|sus2|sus4|add9|9|11|13|m9|ø7|6)?(/[A-G][b#]?)?\b""")
        return chordRegex.replace(text) { match ->
            val root = match.groupValues[1]
            val suffix = match.groupValues[2]
            val bass = match.groupValues[3]

            val standardizedRoot = flatToSharp[root] ?: root
            val rootIndex = noteNames.indexOf(standardizedRoot)
            val newRoot = if (rootIndex != -1) {
                val newIndex = (rootIndex + semitones).mod(12)
                noteNames[newIndex]
            } else root

            val newBass = if (bass.isNotEmpty()) {
                val bassNote = bass.substring(1)
                val stdBass = flatToSharp[bassNote] ?: bassNote
                val bassIndex = noteNames.indexOf(stdBass)
                if (bassIndex != -1) {
                    val newIndex = (bassIndex + semitones).mod(12)
                    "/${noteNames[newIndex]}"
                } else bass
            } else ""

            "$newRoot$suffix$newBass"
        }
    }

    // Harmonic Degree Converter
    fun convertDegreesToChords(text: String, rootKey: String): String {
        val rootIdx = noteNames.indexOf(rootKey).coerceAtLeast(0)
        val degreeMap = mapOf(
            "1" to (0 to ""), "I" to (0 to ""), "i" to (0 to "m"),
            "2m" to (2 to "m"), "ii" to (2 to "m"), "2" to (2 to ""), "II" to (2 to ""),
            "3m" to (4 to "m"), "iii" to (4 to "m"), "3" to (4 to ""), "III" to (4 to ""),
            "4" to (5 to ""), "IV" to (5 to ""), "iv" to (5 to "m"),
            "5" to (7 to ""), "V" to (7 to ""), "v" to (7 to "m"),
            "6m" to (9 to "m"), "vi" to (9 to "m"), "6" to (9 to ""), "VI" to (9 to ""),
            "7" to (11 to "dim"), "vii" to (11 to "dim"), "VII" to (11 to "")
        )

        val regex = Regex("""\b(1|2m|2|3m|3|4|5|6m|6|7m|7|I|i|II|ii|III|iii|IV|iv|V|v|VI|vi|VII|vii)\b""")
        return regex.replace(text) { match ->
            val deg = match.value
            val info = degreeMap[deg]
            if (info != null) {
                val semitones = info.first
                val defaultQuality = info.second
                val noteIdx = (rootIdx + semitones).mod(12)
                "${noteNames[noteIdx]}$defaultQuality"
            } else {
                deg
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF131622))
            .border(1.2.dp, Color(0x668B5CF6), RoundedCornerShape(16.dp))
            .padding(10.dp)
            .testTag("notes_panel_container")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (currentView == 1) {
                // ================= VUE 1 : EXPLORATEUR DE NOTES =================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x338B5CF6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📝", fontSize = 16.sp)
                        }
                        Column {
                            Text(
                                text = "MES NOTES & PAROLES",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "${noteFiles.size} fichier(s) · /SoundStage/Notes/",
                                fontSize = 9.5.sp,
                                color = TextDim
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Button + Nouvelle Note
                        Button(
                            onClick = { showNewNoteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nouvelle Note", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Close button
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (noteFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0C0E14))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📁 Aucune note enregistrée", fontSize = 12.sp, color = TextDim)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Cliquez sur '+ Nouvelle Note' pour commencer", fontSize = 10.sp, color = TextDim2)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(noteFiles) { file ->
                            val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
                            val modDate = dateFormat.format(Date(file.lastModified()))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1B2030))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedNoteFile = file
                                        noteContent = try { file.readText() } catch (e: Exception) { "" }
                                        currentView = 2
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = file.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Modifié le $modDate",
                                            fontSize = 9.5.sp,
                                            color = TextDim
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        file.delete()
                                        noteFiles = actualNotesDir.listFiles { _, name -> name.endsWith(".txt") || name.endsWith(".json") }
                                            ?.sortedByDescending { it.lastModified() } ?: emptyList()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Supprimer",
                                        tint = Color(0xFFFF4466),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ================= VUE 2 : ÉDITEUR DE NOTE & ANALYSE HARMONIQUE =================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                // Auto save before returning
                                selectedNoteFile?.let { f ->
                                    try { f.writeText(noteContent) } catch (e: Exception) {}
                                }
                                currentView = 1
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Retour",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = selectedNoteFile?.name ?: "Note.txt",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Save Button
                        Button(
                            onClick = {
                                selectedNoteFile?.let { f ->
                                    try { f.writeText(noteContent) } catch (e: Exception) {}
                                }
                                showCopyFeedback = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("💾 Sauvegarder", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }

                        // Close Button
                        IconButton(
                            onClick = {
                                selectedNoteFile?.let { f ->
                                    try { f.writeText(noteContent) } catch (e: Exception) {}
                                }
                                onClose()
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Toolbar: Transposition & Harmonic Analysis
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1B2030))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Transposition Buttons: [-1] [+1] [-12] [+12]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("TRANS :", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = TextDim)
                            listOf(-12, -1, 1, 12).forEach { semitones ->
                                val label = if (semitones > 0) "+$semitones" else "$semitones"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF283048))
                                        .clickable { noteContent = transposeText(noteContent, semitones) }
                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        // Convert Harmonic Degrees Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF332050))
                                .border(1.dp, NeonPurple, RoundedCornerShape(8.dp))
                                .clickable {
                                    noteContent = convertDegreesToChords(noteContent, selectedRootKey)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "🎼 Convertir Degrés ($selectedRootKey)",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonPurpleLight
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Insert Played Chord Button
                        val currentChordName = detectedChord?.primaryName ?: "Accord..."
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x2222D3EE))
                                .border(1.dp, NeonCyan.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                .clickable(enabled = detectedChord != null) {
                                    if (detectedChord != null) {
                                        val space = if (noteContent.isNotEmpty() && !noteContent.endsWith(" ") && !noteContent.endsWith("\n")) " " else ""
                                        noteContent += space + detectedChord.primaryName
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "➕ Insérer Joué : $currentChordName",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (detectedChord != null) NeonCyan else TextDim
                            )
                        }

                        // Copy Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF283048))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(noteContent))
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📋 Copier", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Text Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0C0E14))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    TextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        modifier = Modifier.fillMaxSize(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        placeholder = {
                            Text(
                                text = "Saisissez vos paroles, mémo ou grille d'accords...\nEx : 1 - 4 - 6m - 5\nOu : Cmaj7 | Am7 | Dm7 | G7",
                                fontSize = 11.sp,
                                color = TextDim2
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    )
                }
            }
        }

        // New Note Dialog Popup
        if (showNewNoteDialog) {
            AlertDialog(
                onDismissRequest = { showNewNoteDialog = false },
                title = { Text("Créer une nouvelle note", fontSize = 14.sp, color = Color.White) },
                text = {
                    OutlinedTextField(
                        value = newNoteName,
                        onValueChange = { newNoteName = it },
                        label = { Text("Nom du fichier") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newNoteName.isNotBlank()) {
                                var safeName = newNoteName.trim()
                                if (!safeName.endsWith(".txt")) safeName += ".txt"
                                val newFile = File(actualNotesDir, safeName)
                                try {
                                    newFile.createNewFile()
                                    selectedNoteFile = newFile
                                    noteContent = ""
                                    currentView = 2
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            showNewNoteDialog = false
                            newNoteName = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                    ) {
                        Text("Créer", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewNoteDialog = false }) {
                        Text("Annuler", color = TextDim)
                    }
                },
                containerColor = Color(0xFF1E2232)
            )
        }
    }
}
