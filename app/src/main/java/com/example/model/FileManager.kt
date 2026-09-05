package com.example.model

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import java.io.File

/**
 * Storage item representing minimal metadata for scanned files and directories.
 * Designed for light memory footprint and high responsiveness.
 */
data class StorageItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val extension: String = "",
    val subItemCount: Int = 0,
    val formattedSize: String = ""
)

/**
 * FileManager responsible for:
 * 1. Automatically and silently creating /LiveKeys principal directory & subfolders:
 *    - /LiveKeys/SoundFonts
 *    - /LiveKeys/Loops
 *    - /LiveKeys/Recordings
 *    - /LiveKeys/Styles
 * 2. Scanning real internal & external storage asynchronously on Dispatchers.IO.
 * 3. Extracting minimal metadata without memory overload.
 */
class FileManager(private val context: Context) {

    // Resolves primary LiveKeys directory with graceful fallbacks
    val baseDir: File by lazy {
        val externalStorage = Environment.getExternalStorageDirectory()
        val primaryDir = File(externalStorage, "LiveKeys")
        
        try {
            if (!primaryDir.exists()) {
                primaryDir.mkdirs()
            }
        } catch (e: Exception) {
            writeLog("FileManager", "Error creating primary LiveKeys directory", e)
        }
        primaryDir
    }

    val soundfontsDir: File get() = File(baseDir, "SoundFonts")
    val loopsDir: File get() = File(baseDir, "Loops")
    val drumPadDir: File get() = File(baseDir, "DrumPad")
    val scenesDir: File get() = File(baseDir, "Scenes")
    val recordingsDir: File get() = File(baseDir, "Recordings")
    val midiDir: File get() = File(baseDir, "Midi")
    val stylesDir: File get() = File(baseDir, "Styles")
    val presetsDir: File get() = File(baseDir, "Presets")
    val logsDir: File get() = File(baseDir, "Logs")

    fun writeLog(tag: String, message: String, throwable: Throwable? = null) {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                if (!logsDir.exists()) logsDir.mkdirs()
                val logFile = File(logsDir, "crash_logs.txt")
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                val logEntry = buildString {
                    append("[$timestamp] [$tag] $message\n")
                    if (throwable != null) {
                        append(android.util.Log.getStackTraceString(throwable))
                        append("\n")
                    }
                }
                logFile.appendText(logEntry)
            } catch (_: Exception) {}
        }
    }

    /**
     * Ensures all subdirectories exist asynchronously on Dispatchers.IO
     */
    suspend fun ensureDirectoriesExist() = withContext(Dispatchers.IO) {
        try {
            if (!soundfontsDir.exists()) soundfontsDir.mkdirs()
            if (!loopsDir.exists()) loopsDir.mkdirs()
            if (!drumPadDir.exists()) drumPadDir.mkdirs()
            if (!scenesDir.exists()) scenesDir.mkdirs()
            if (!presetsDir.exists()) presetsDir.mkdirs()
            if (!logsDir.exists()) logsDir.mkdirs()
            if (!recordingsDir.exists()) recordingsDir.mkdirs()
            if (!midiDir.exists()) midiDir.mkdirs()
            if (!stylesDir.exists()) stylesDir.mkdirs()
        } catch (_: Exception) { }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
        }
    }

    /**
     * Recursively and safely scans for SoundFont (.sf2, .sfz) files on Dispatchers.IO
     */
    suspend fun getSoundFontFiles(): List<StorageItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StorageItem>()
        try {
            val directoriesToScan = listOf(
                soundfontsDir,
                File(Environment.getExternalStorageDirectory(), "Music/SoundfontsLive"),
                File(Environment.getExternalStorageDirectory(), "Soundfonts"),
                File(Environment.getExternalStorageDirectory(), "Music")
            )

            val visitedPaths = mutableSetOf<String>()

            for (dir in directoriesToScan) {
                if (!dir.exists() || !dir.canRead()) continue
                
                dir.walkTopDown()
                    .maxDepth(3)
                    .filter { file ->
                        file.isFile && (file.extension.equals("sf2", ignoreCase = true) ||
                                file.extension.equals("sfz", ignoreCase = true))
                    }
                    .forEach { file ->
                        if (visitedPaths.add(file.absolutePath)) {
                            result.add(
                                StorageItem(
                                    name = file.name,
                                    path = file.absolutePath,
                                    isDirectory = false,
                                    size = file.length(),
                                    extension = file.extension.lowercase(),
                                    formattedSize = formatSize(file.length())
                                )
                            )
                        }
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result.sortedBy { it.name.lowercase() }
    }

    /**
     * Scans the /LiveKeys/Loops directory recursively without heavy buffer allocations.
     */
    suspend fun getLoopFiles(): List<StorageItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StorageItem>()
        try {
            if (loopsDir.exists() && loopsDir.canRead()) {
                loopsDir.walkTopDown()
                    .maxDepth(4)
                    .filter { it.isFile && isAudioFile(it) }
                    .forEach { file ->
                        result.add(
                            StorageItem(
                                name = file.name,
                                path = file.absolutePath,
                                isDirectory = false,
                                size = file.length(),
                                extension = file.extension.lowercase(),
                                formattedSize = formatSize(file.length())
                            )
                        )
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result.sortedBy { it.name.lowercase() }
    }

    /**
     * Returns the dynamic directory tree of /LiveKeys/Loops as LoopFolder models for Material Explorer
     */
    suspend fun getLoopFolderTree(): List<LoopFolder> = withContext(Dispatchers.IO) {
        val folders = mutableListOf<LoopFolder>()
        try {
            if (loopsDir.exists() && loopsDir.canRead()) {
                // Root files
                val rootFiles = loopsDir.listFiles { file -> file.isFile && isAudioFile(file) }
                    ?.map { file ->
                        LoopFile(
                            name = file.name,
                            duration = "Audio",
                            folder = "Loops",
                            bpm = 120
                        )
                    } ?: emptyList()

                if (rootFiles.isNotEmpty()) {
                    folders.add(
                        LoopFolder(
                            name = "Racine /Loops",
                            icon = "📁",
                            files = rootFiles,
                            isOpen = true
                        )
                    )
                }

                // Subdirectories
                loopsDir.listFiles { file -> file.isDirectory }?.forEach { subDir ->
                    val subFiles = subDir.listFiles { file -> file.isFile && isAudioFile(file) }
                        ?.map { file ->
                            LoopFile(
                                name = file.name,
                                duration = "Audio",
                                folder = subDir.name,
                                bpm = 120
                            )
                        } ?: emptyList()

                    val icon = when {
                        subDir.name.contains("drum", ignoreCase = true) -> "🥁"
                        subDir.name.contains("bass", ignoreCase = true) -> "🎸"
                        subDir.name.contains("guitar", ignoreCase = true) -> "🎶"
                        subDir.name.contains("worship", ignoreCase = true) || subDir.name.contains("pad", ignoreCase = true) -> "🌊"
                        else -> "📁"
                    }

                    folders.add(
                        LoopFolder(
                            name = subDir.name,
                            icon = icon,
                            files = subFiles,
                            isOpen = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        folders
    }

    /**
     * Lists items inside a specific directory path for the Material file explorer
     */
    suspend fun listItemsInDirectory(dirPath: String): List<StorageItem> = withContext(Dispatchers.IO) {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return@withContext emptyList()

        val items = mutableListOf<StorageItem>()
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    val count = file.listFiles()?.size ?: 0
                    items.add(
                        StorageItem(
                            name = file.name,
                            path = file.absolutePath,
                            isDirectory = true,
                            subItemCount = count,
                            formattedSize = "$count éléments"
                        )
                    )
                } else if (isAudioFile(file) || isStyleFile(file) || isSoundFontFile(file)) {
                    items.add(
                        StorageItem(
                            name = file.name,
                            path = file.absolutePath,
                            isDirectory = false,
                            size = file.length(),
                            extension = file.extension.lowercase(),
                            formattedSize = formatSize(file.length())
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /**
     * Scans the /LiveKeys/DrumPad directory specifically on Dispatchers.IO
     */
    suspend fun getDrumPadFiles(): List<StorageItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StorageItem>()
        try {
            if (drumPadDir.exists() && drumPadDir.canRead()) {
                drumPadDir.walkTopDown()
                    .maxDepth(4)
                    .filter { it.isFile && isAudioFile(it) }
                    .forEach { file ->
                        result.add(
                            StorageItem(
                                name = file.name,
                                path = file.absolutePath,
                                isDirectory = false,
                                size = file.length(),
                                extension = file.extension.lowercase(),
                                formattedSize = formatSize(file.length())
                            )
                        )
                    }
            }
            // Fallback: check secondary external DrumPad directory if primary is empty
            if (result.isEmpty()) {
                val extDrumPad = File(Environment.getExternalStorageDirectory(), "DrumPad")
                if (extDrumPad.exists() && extDrumPad.canRead()) {
                    extDrumPad.walkTopDown()
                        .maxDepth(3)
                        .filter { it.isFile && isAudioFile(it) }
                        .forEach { file ->
                            result.add(
                                StorageItem(
                                    name = file.name,
                                    path = file.absolutePath,
                                    isDirectory = false,
                                    size = file.length(),
                                    extension = file.extension.lowercase(),
                                    formattedSize = formatSize(file.length())
                                )
                            )
                        }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result.sortedBy { it.name.lowercase() }
    }

    /**
     * Scans for Drum Samples in /LiveKeys/DrumPad and /DrumPad
     */
    suspend fun getDrumSampleFiles(): List<StorageItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StorageItem>()
        try {
            val dirs = listOf(
                drumPadDir,
                File(Environment.getExternalStorageDirectory(), "DrumPad"),
                File(Environment.getExternalStorageDirectory(), "Music/DrumPad"),
                loopsDir
            )
            val visited = mutableSetOf<String>()
            for (dir in dirs) {
                if (!dir.exists() || !dir.canRead()) continue
                dir.walkTopDown()
                    .maxDepth(3)
                    .filter { it.isFile && isAudioFile(it) }
                    .forEach { file ->
                        if (visited.add(file.absolutePath)) {
                            result.add(
                                StorageItem(
                                    name = file.name,
                                    path = file.absolutePath,
                                    isDirectory = false,
                                    size = file.length(),
                                    extension = file.extension.lowercase(),
                                    formattedSize = formatSize(file.length())
                                )
                            )
                        }
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result.sortedBy { it.name.lowercase() }
    }

    /**
     * Scans for Arranger / Styles (.sty, .prs, .sst, .mid) in /LiveKeys/Styles
     */
    suspend fun getStyleFiles(): List<StorageItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StorageItem>()
        try {
            if (stylesDir.exists() && stylesDir.canRead()) {
                stylesDir.walkTopDown()
                    .maxDepth(3)
                    .filter { it.isFile && isStyleFile(it) }
                    .forEach { file ->
                        result.add(
                            StorageItem(
                                name = file.name,
                                path = file.absolutePath,
                                isDirectory = false,
                                size = file.length(),
                                extension = file.extension.lowercase(),
                                formattedSize = formatSize(file.length())
                            )
                        )
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result.sortedBy { it.name.lowercase() }
    }

    /**
     * Scans for Scene Presets (.scene) in /LiveKeys/scènes
     */
    suspend fun getSceneFiles(): List<StorageItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StorageItem>()
        try {
            if (scenesDir.exists() && scenesDir.canRead()) {
                scenesDir.listFiles { file -> file.isFile && (file.extension.equals("scene", ignoreCase = true) || file.extension.equals("json", ignoreCase = true)) }
                    ?.forEach { file ->
                        result.add(
                            StorageItem(
                                name = file.nameWithoutExtension,
                                path = file.absolutePath,
                                isDirectory = false,
                                size = file.length(),
                                extension = file.extension.lowercase(),
                                formattedSize = formatSize(file.length())
                            )
                        )
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result.sortedByDescending { File(it.path).lastModified() }
    }

    /**
     * Saves a scene JSON configuration directly to a .scene file in /LiveKeys/scènes
     */
    suspend fun saveSceneFile(sceneName: String, jsonContent: String): File? = withContext(Dispatchers.IO) {
        try {
            if (!scenesDir.exists()) scenesDir.mkdirs()
            val cleanName = sceneName.trim().replace(Regex("[^a-zA-Z0-9_\\-\\sÀ-ÿ]"), "_")
            val targetFile = File(scenesDir, "$cleanName.scene")
            targetFile.writeText(jsonContent, Charsets.UTF_8)
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Loads the raw JSON string from a .scene file
     */
    suspend fun loadSceneFile(filePath: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists() && file.canRead()) {
                file.readText(Charsets.UTF_8)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes a .scene file
     */
    suspend fun deleteSceneFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Scans for Recordings (.wav, .mp3, .m4a) in /LiveKeys/Recording
     */
    suspend fun getRecordingFiles(): List<StorageItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StorageItem>()
        try {
            if (recordingsDir.exists() && recordingsDir.canRead()) {
                recordingsDir.listFiles { file -> file.isFile && isAudioFile(file) }
                    ?.forEach { file ->
                        result.add(
                            StorageItem(
                                name = file.name,
                                path = file.absolutePath,
                                isDirectory = false,
                                size = file.length(),
                                extension = file.extension.lowercase(),
                                formattedSize = formatSize(file.length())
                            )
                        )
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result.sortedByDescending { it.name }
    }

    /**
     * Scans for MIDI (.mid, .midi) in /LiveKeys/Midi and system Music directories
     */
    suspend fun getMidiFiles(): List<StorageItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StorageItem>()
        try {
            val dirs = listOf(
                midiDir,
                File(Environment.getExternalStorageDirectory(), "Music/Midi"),
                File(Environment.getExternalStorageDirectory(), "Midi"),
                File(Environment.getExternalStorageDirectory(), "Music")
            )
            val visited = mutableSetOf<String>()
            for (dir in dirs) {
                if (!dir.exists() || !dir.canRead()) continue
                dir.walkTopDown()
                    .maxDepth(3)
                    .filter { it.isFile && isMidiFile(it) }
                    .forEach { file ->
                        if (visited.add(file.absolutePath)) {
                            result.add(
                                StorageItem(
                                    name = file.name,
                                    path = file.absolutePath,
                                    isDirectory = false,
                                    size = file.length(),
                                    extension = file.extension.lowercase(),
                                    formattedSize = formatSize(file.length())
                                )
                            )
                        }
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result.sortedBy { it.name.lowercase() }
    }

    /**
     * Returns MIDI directory tree for MIDI browser panel
     */
    suspend fun getMidiFolderTree(): List<LoopFolder> = withContext(Dispatchers.IO) {
        val folders = mutableListOf<LoopFolder>()
        try {
            if (midiDir.exists() && midiDir.canRead()) {
                val rootFiles = midiDir.listFiles { file -> file.isFile && isMidiFile(file) }
                    ?.map { file ->
                        LoopFile(
                            name = file.name,
                            duration = "MIDI",
                            folder = "Midi",
                            bpm = 120
                        )
                    } ?: emptyList()

                if (rootFiles.isNotEmpty()) {
                    folders.add(
                        LoopFolder(
                            name = "Racine /Midi",
                            icon = "🎹",
                            files = rootFiles,
                            isOpen = true
                        )
                    )
                }

                midiDir.listFiles { file -> file.isDirectory }?.forEach { subDir ->
                    val subFiles = subDir.listFiles { file -> file.isFile && isMidiFile(file) }
                        ?.map { file ->
                            LoopFile(
                                name = file.name,
                                duration = "MIDI",
                                folder = subDir.name,
                                bpm = 120
                            )
                        } ?: emptyList()

                    folders.add(
                        LoopFolder(
                            name = subDir.name,
                            icon = "📁",
                            files = subFiles,
                            isOpen = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        folders
    }

    private fun isAudioFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in listOf("wav", "mp3", "ogg", "flac", "m4a", "aac")
    }

    private fun isMidiFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in listOf("mid", "midi")
    }

    private fun isStyleFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in listOf("sty", "prs", "sst", "mid", "midi")
    }

    private fun isSoundFontFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in listOf("sf2", "sfz")
    }
}
