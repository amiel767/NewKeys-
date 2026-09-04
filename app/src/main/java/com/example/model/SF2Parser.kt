package com.example.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Metadata for a single SoundFont 2 (SF2) Preset extracted from binary RIFF headers.
 */
data class SF2PresetInfo(
    val presetIndex: Int,
    val name: String,
    val bank: Int,
    val preset: Int
) {
    val displayName: String get() = SF2Parser.cleanPresetName(name)
}

/**
 * Pure Kotlin, high-performance binary parser for SoundFont 2 (.sf2) files.
 * Directly navigates RIFF chunks to find the 'pdta' (preset data) list
 * and parses ALL 'phdr' (Preset Header) chunk records.
 */
object SF2Parser {

    private const val TAG = "SF2Parser"

    fun cleanPresetName(rawName: String): String {
        var name = rawName.trim { it <= ' ' || it == '\u0000' }
        // Clean bracketed bank/preset markers like [000:000] if present
        name = name.replace("\\[\\d+:\\d+\\]".toRegex(), "").trim()
        if (name.isBlank()) {
            name = "Instrument"
        }
        return name
    }

    suspend fun parsePresets(sf2File: File): List<SF2PresetInfo> = withContext(Dispatchers.IO) {
        val presets = mutableListOf<SF2PresetInfo>()
        if (!sf2File.exists() || !sf2File.canRead() || sf2File.length() < 128) {
            return@withContext presets
        }

        var raf: RandomAccessFile? = null
        try {
            raf = RandomAccessFile(sf2File, "r")
            val fileLength = raf.length()

            // 1. Verify RIFF header
            val headerBytes = ByteArray(12)
            raf.readFully(headerBytes)
            val headerBuffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

            val riffId = readString(headerBuffer, 4)
            if (riffId != "RIFF") {
                Log.w(TAG, "Not a valid RIFF file: ${sf2File.name}")
                return@withContext fallbackSinglePreset(sf2File)
            }
            val riffSize = headerBuffer.int
            val sfbkId = readString(headerBuffer, 4)
            if (sfbkId != "sfbk") {
                Log.w(TAG, "Not a valid sfbk SoundFont file: ${sf2File.name}")
                return@withContext fallbackSinglePreset(sf2File)
            }

            // 2. Scan top-level chunks to find LIST of type 'pdta'
            var phdrOffset: Long = -1L
            var phdrSize: Long = 0L

            while (raf.filePointer < fileLength - 8) {
                val chunkIdBytes = ByteArray(4)
                raf.readFully(chunkIdBytes)
                val chunkId = String(chunkIdBytes, Charsets.US_ASCII)
                val chunkSize = readUInt32(raf)
                val chunkEnd = (raf.filePointer + chunkSize).coerceAtMost(fileLength)

                if (chunkId == "LIST") {
                    val listTypeBytes = ByteArray(4)
                    raf.readFully(listTypeBytes)
                    val listType = String(listTypeBytes, Charsets.US_ASCII)

                    if (listType == "pdta") {
                        // Scan sub-chunks inside pdta to locate 'phdr'
                        while (raf.filePointer < chunkEnd - 8) {
                            val subIdBytes = ByteArray(4)
                            raf.readFully(subIdBytes)
                            val subId = String(subIdBytes, Charsets.US_ASCII)
                            val subSize = readUInt32(raf)

                            if (subId == "phdr") {
                                phdrOffset = raf.filePointer
                                phdrSize = subSize
                                break
                            } else {
                                raf.seek(raf.filePointer + subSize)
                            }
                        }
                        break
                    } else {
                        raf.seek(chunkEnd)
                    }
                } else {
                    raf.seek(chunkEnd)
                }
            }

            // 3. Parse records in phdr chunk
            if (phdrOffset > 0 && phdrSize >= 38) {
                raf.seek(phdrOffset)
                val recordCount = (phdrSize / 38).toInt()
                val recordBuffer = ByteArray(38)

                for (i in 0 until recordCount) {
                    raf.readFully(recordBuffer)
                    val buf = ByteBuffer.wrap(recordBuffer).order(ByteOrder.LITTLE_ENDIAN)

                    // achPresetName: 20 bytes null-terminated ASCII
                    val nameBytes = ByteArray(20)
                    buf.get(nameBytes)
                    val rawName = String(nameBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }

                    val wPreset = buf.short.toInt() and 0xFFFF
                    val wBank = buf.short.toInt() and 0xFFFF
                    val wPresetBagNdx = buf.short.toInt() and 0xFFFF
                    val dwLibrary = buf.int
                    val dwGenre = buf.int
                    val dwMorphology = buf.int

                    // SF2 spec: The terminal record is always named "EOP" (End of Presets)
                    if (rawName.equals("EOP", ignoreCase = true)) {
                        break
                    }

                    if (rawName.isNotEmpty()) {
                        val cleaned = cleanPresetName(rawName)
                        presets.add(
                            SF2PresetInfo(
                                presetIndex = presets.size,
                                name = cleaned,
                                bank = wBank,
                                preset = wPreset
                            )
                        )
                    }
                }
            }

            if (presets.isEmpty()) {
                fallbackSinglePreset(sf2File)
            } else {
                presets.sortedWith(compareBy({ it.bank }, { it.preset }))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SF2 binary presets: ${e.message}", e)
            fallbackSinglePreset(sf2File)
        } finally {
            try {
                raf?.close()
            } catch (_: Exception) {}
        }
    }

    private fun fallbackSinglePreset(file: File): List<SF2PresetInfo> {
        val cleanName = file.nameWithoutExtension.ifEmpty { file.name }
        return listOf(
            SF2PresetInfo(
                presetIndex = 0,
                name = cleanName,
                bank = 0,
                preset = 0
            )
        )
    }

    private fun readString(buf: ByteBuffer, length: Int): String {
        val bytes = ByteArray(length)
        buf.get(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun readUInt32(raf: RandomAccessFile): Long {
        val b0 = raf.read()
        val b1 = raf.read()
        val b2 = raf.read()
        val b3 = raf.read()
        if ((b0 or b1 or b2 or b3) < 0) return 0L
        return ((b0 and 0xFF) or
                ((b1 and 0xFF) shl 8) or
                ((b2 and 0xFF) shl 16) or
                ((b3 and 0xFF) shl 24)).toLong() and 0xFFFFFFFFL
    }
}
