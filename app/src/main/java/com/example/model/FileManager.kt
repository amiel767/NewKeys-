package com.example.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
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
 * FileManager responsible exclusively for app-private internal storage (context.filesDir/LiveKeys):
 * - SoundFonts: /context.filesDir/LiveKeys/SoundFonts/
 * - Loops: /context.filesDir/LiveKeys/Loops/
 * - DrumPad: /context.filesDir/LiveKeys/DrumPad/
 * - Scenes: /context.filesDir/LiveKeys/Scenes/
 * - Recordings: /context.filesDir/LiveKeys/Recordings/
 * - Midi: /context.filesDir/LiveKeys/Midi/
 * - Styles: /context.filesDir/LiveKeys/Styles/
 * - Presets: /context.filesDir/LiveKeys/Presets/
 * - Logs: /context.filesDir/LiveKeys/Logs/
 */
class FileManager(private val context: Context) {

    val baseDir: File by lazy {
        val internalLiveKeys = File(context.filesDir, "LiveKeys")
        if (!internalLiveKeys.exists()) {
            internalLiveKeys.mkdirs()
        }
        internalLiveKeys
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
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (!logsDir.exists()) logsDir.mkdirs()
                val logFile = File(logsDir, "crash_logs.txt")
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                val logEntry = buildString {
                    append("[$timestamp] [$tag] $message\n")
                    if (throwable != null) {
                        append(Log.getStackTraceString(throwable))
                        append("\n")
                    }
                }
                logFile.appendText(logEntry)
            } catch (_: Exception) {}
        }
    }

    /**
     * Ensures all internal subdirectories exist
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
     * Scans for SoundFont (.sf2, .sfz) files EXCLUSIVELY inside context.filesDir/LiveKeys/SoundFonts/
     */
    suspend fun getSoundFontFiles(): List<StorageItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StorageItem>()
        try {
            if (soundfontsDir.exists() && soundfontsDir.canRead()) {
                soundfontsDir.walkTopDown()
                    .maxDepth(3)
                    .filter { file ->
                        file.isFile && (file.extension.equals("sf2", ignoreCase = true) ||
                                file.extension.equals("sfz", ignoreCase = true))
                    }
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
     * Returns a File instance directly for native C++ fopen from internal storage.
     */
    suspend fun getNativeReadableSoundFontFile(sourcePath: String): File = withContext(Dispatchers.IO) {
        val file = File(sourcePath)
        if (file.exists()) file else File(soundfontsDir, file.name)
    }

    /**
     * Scans EXCLUSIVELY the internal /LiveKeys/Loops directory.
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
     * Returns the dynamic directory tree of internal /LiveKeys/Loops
     */
    suspend fun getLoopFolderTree(): List<LoopFolder> = withContext(Dispatchers.IO) {
        val folders = mutableListOf<LoopFolder>()
        try {
            if (loopsDir.exists() && loopsDir.canRead()) {
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
     * Lists items inside a specific directory path inside internal storage
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
     * Scans EXCLUSIVELY the internal /LiveKeys/DrumPad directory
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result.sortedBy { it.name.lowercase() }
    }

    suspend fun getDrumSampleFiles(): List<StorageItem> = getDrumPadFiles()

    /**
     * Scans EXCLUSIVELY internal /LiveKeys/Styles
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
     * Scans EXCLUSIVELY internal /LiveKeys/Scenes
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
     * Scans EXCLUSIVELY internal /LiveKeys/Recordings
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
     * Scans EXCLUSIVELY internal /LiveKeys/Midi
     */
    suspend fun getMidiFiles(): List<StorageItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StorageItem>()
        try {
            if (midiDir.exists() && midiDir.canRead()) {
                midiDir.walkTopDown()
                    .maxDepth(3)
                    .filter { it.isFile && isMidiFile(it) }
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

    // ==================== ASYNCHRONOUS URI IMPORT FUNCTIONS ====================

    suspend fun importSoundFontFromUri(uri: Uri): File? = copyUriToDirectory(uri, soundfontsDir)

    suspend fun importDrumPadFromUri(uri: Uri): File? = copyUriToDirectory(uri, drumPadDir)

    suspend fun importLoopFromUri(uri: Uri): File? = copyUriToDirectory(uri, loopsDir)

    suspend fun importMidiFromUri(uri: Uri): File? = copyUriToDirectory(uri, midiDir)

    private suspend fun copyUriToDirectory(uri: Uri, destDir: File): File? = withContext(Dispatchers.IO) {
        try {
            if (!destDir.exists()) destDir.mkdirs()
            var fileName = getFileNameFromUri(uri)
            if (fileName.isNullOrBlank()) {
                fileName = "imported_${System.currentTimeMillis()}"
            }
            val targetFile = File(destDir, fileName)
            Log.d("FileManager", "Importing Uri $uri -> ${targetFile.absolutePath}")

            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 65536)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0) {
                Log.d("FileManager", "Successfully imported file: ${targetFile.name} (${targetFile.length()} bytes)")
                targetFile
            } else null
        } catch (e: Exception) {
            Log.e("FileManager", "Error importing file from URI $uri: ${e.message}", e)
            null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (name.isNullOrBlank()) {
            name = uri.path?.let { File(it).name }
        }
        return name
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
