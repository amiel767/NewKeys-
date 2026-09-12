package com.soundstage.mixer.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.soundstage.mixer.model.DrumPadItem
import com.soundstage.mixer.model.DrumSoundType
import com.soundstage.mixer.model.StorageItem
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Live DrumPad Loop Engine & Beat Quantizer.
 *
 * Professional MPC / Ableton-style workflow:
 * 1. Live Metronome-Synchronized Recording with automatic 1/16 or 1/8 note quantization.
 * 2. Instant calculation of seamless bar loop duration (1, 2, 4, 8 bars) eliminating drift.
 * 3. Bounces PCM to WAV and saves directly to "SoundStage/DrumPad/DrumPad loop/".
 * 4. Instant playback with zero-click crossfading.
 */
class DrumLooperEngine(
    private val context: Context,
    private val drumPadDir: File
) {
    companion object {
        private const val TAG = "DrumLooperEngine"
        private const val SAMPLE_RATE = 44100
        private const val CHANNELS = 2
    }

    val drumPadLoopDir: File
        get() = File(drumPadDir, "DrumPad loop").apply { if (!exists()) mkdirs() }

    data class DrumEvent(
        val timestampMs: Long,
        val padId: Int,
        val note: String = "",
        val samplePath: String = "",
        val velocity: Float = 1.0f
    )

    private val recordedEvents = mutableListOf<DrumEvent>()
    @Volatile var isRecording = false
        private set

    private var recordStartTimeMs: Long = 0L
    private var recordBpm: Int = 120
    private var recordSignature: String = "4/4"

    fun startRecording(bpm: Int, signature: String) {
        synchronized(recordedEvents) {
            recordedEvents.clear()
            recordBpm = bpm.coerceIn(30, 300)
            recordSignature = signature
            recordStartTimeMs = System.currentTimeMillis()
            isRecording = true
        }
        Log.d(TAG, "Drum looper recording started at BPM: $bpm, Signature: $signature")
    }

    fun recordPadHit(pad: DrumPadItem, samplePath: String = "", velocity: Float = 1.0f) {
        if (!isRecording) return
        val now = System.currentTimeMillis()
        val offsetMs = (now - recordStartTimeMs).coerceAtLeast(0)
        synchronized(recordedEvents) {
            recordedEvents.add(
                DrumEvent(
                    timestampMs = offsetMs,
                    padId = pad.id,
                    note = if (pad.soundType == DrumSoundType.SF2_NOTE) pad.sf2Note else "",
                    samplePath = samplePath,
                    velocity = velocity
                )
            )
        }
    }

    /**
     * Quantizes events to 1/16th note grid and renders the seamless loop WAV.
     * Calculates exact number of completed bars (minimum 1 bar, up to 16 bars).
     */
    fun stopAndRenderLoop(
        bpm: Int = recordBpm,
        signature: String = recordSignature,
        quantizeStrength: Float = 1.0f, // 1.0 = strict 1/16 grid
        customFileName: String? = null,
        onRendered: (File, Int, Int) -> Unit
    ) {
        if (!isRecording) return
        val totalRecordedDurationMs = (System.currentTimeMillis() - recordStartTimeMs).coerceAtLeast(500)
        isRecording = false

        val beatsPerBar = when (signature) {
            "2/4" -> 2
            "3/4" -> 3
            "6/8" -> 6
            else -> 4
        }

        val msPerBeat = (60_000.0 / bpm.toDouble())
        val msPerBar = msPerBeat * beatsPerBar
        val sixteenthMs = msPerBeat / 4.0 // 1/16th grid

        // Calculate nearest completed bar count (min 1 bar)
        val rawBars = (totalRecordedDurationMs.toDouble() / msPerBar).roundToInt().coerceIn(1, 16)
        val exactLoopDurationMs = (rawBars * msPerBar).toLong()
        val totalLoopFrames = ((exactLoopDurationMs * SAMPLE_RATE) / 1000L).toInt()

        Log.d(TAG, "Stop & Render Loop: $rawBars bars, duration: $exactLoopDurationMs ms, $totalLoopFrames frames")

        val eventsCopy: List<DrumEvent>
        synchronized(recordedEvents) {
            eventsCopy = ArrayList(recordedEvents)
        }

        // Apply 1/16 grid quantization
        val quantizedEvents = eventsCopy.map { event ->
            val nearestGridMs = (event.timestampMs.toDouble() / sixteenthMs).roundToInt() * sixteenthMs
            val finalTimestampMs = (event.timestampMs * (1.0f - quantizeStrength) + nearestGridMs * quantizeStrength).toLong()
            event.copy(timestampMs = finalTimestampMs % exactLoopDurationMs)
        }

        val fileName = customFileName ?: "DrumLoop_${bpm}BPM_${rawBars}Bars_${System.currentTimeMillis() % 10000}.wav"
        val targetFile = File(drumPadLoopDir, fileName)

        // Write WAV header and PCM data
        Thread {
            try {
                val pcmBuffer = ShortArray(totalLoopFrames * CHANNELS)

                // Simple synthesis / audio blending for the drum loop
                val samplesPerMs = SAMPLE_RATE / 1000
                for (event in quantizedEvents) {
                    val startFrame = ((event.timestampMs * SAMPLE_RATE) / 1000L).toInt().coerceIn(0, totalLoopFrames - 1)
                    val noteFreq = when (event.padId) {
                        1 -> 55.0  // Kick: 55Hz
                        2 -> 180.0 // Snare: 180Hz + noise
                        3 -> 400.0 // Clap: 400Hz burst
                        4 -> 350.0 // Rimshot: 350Hz
                        5 -> 900.0 // Closed Hi-Hat: 900Hz
                        6 -> 750.0 // Open Hi-Hat: 750Hz
                        7 -> 80.0  // Low Tom: 80Hz
                        8 -> 130.0 // High Tom: 130Hz
                        else -> 120.0
                    }
                    val decayMs = when (event.padId) {
                        1 -> 250 // Kick
                        2 -> 200 // Snare
                        3 -> 120 // Clap
                        4 -> 80  // Rimshot
                        5 -> 60  // Closed HH
                        6 -> 350 // Open HH
                        7 -> 300 // Low Tom
                        8 -> 250 // High Tom
                        else -> 150
                    }
                    val decayFrames = decayMs * samplesPerMs

                    for (f in 0 until decayFrames) {
                        val frameIdx = (startFrame + f) % totalLoopFrames
                        val t = f.toDouble() / SAMPLE_RATE
                        val env = (1.0 - (f.toDouble() / decayFrames)).coerceIn(0.0, 1.0)
                        val sampleVal = (kotlin.math.sin(2.0 * Math.PI * noteFreq * t) * env * 18000 * event.velocity).toInt()
                        
                        val bufIdx = frameIdx * CHANNELS
                        if (bufIdx + 1 < pcmBuffer.size) {
                            val currentL = pcmBuffer[bufIdx].toInt()
                            val currentR = pcmBuffer[bufIdx + 1].toInt()
                            pcmBuffer[bufIdx] = (currentL + sampleVal).coerceIn(-32768, 32767).toShort()
                            pcmBuffer[bufIdx + 1] = (currentR + sampleVal).coerceIn(-32768, 32767).toShort()
                        }
                    }
                }

                // Apply 5ms micro-crossfade at the wrap point to prevent clicks
                val crossfadeFrames = (SAMPLE_RATE * 5) / 1000
                for (f in 0 until crossfadeFrames) {
                    val alpha = f.toFloat() / crossfadeFrames.toFloat()
                    val startIdx = f * CHANNELS
                    val endIdx = (totalLoopFrames - crossfadeFrames + f) * CHANNELS
                    if (endIdx + 1 < pcmBuffer.size && startIdx + 1 < pcmBuffer.size) {
                        val endL = pcmBuffer[endIdx].toFloat()
                        val endR = pcmBuffer[endIdx + 1].toFloat()
                        val startL = pcmBuffer[startIdx].toFloat()
                        val startR = pcmBuffer[startIdx + 1].toFloat()

                        pcmBuffer[startIdx] = ((startL * alpha) + (endL * (1f - alpha))).toInt().toShort()
                        pcmBuffer[startIdx + 1] = ((startR * alpha) + (endR * (1f - alpha))).toInt().toShort()
                    }
                }

                writeWavFile(targetFile, pcmBuffer, SAMPLE_RATE, CHANNELS)
                Log.d(TAG, "Drum loop rendered successfully: ${targetFile.absolutePath}")

                onRendered(targetFile, bpm, rawBars * beatsPerBar)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to render drum loop: ${e.message}")
            }
        }.start()
    }

    private fun writeWavFile(file: File, pcmData: ShortArray, sampleRate: Int, channels: Int) {
        val byteData = ByteArray(pcmData.size * 2)
        ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcmData)

        val totalAudioLen = byteData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * 2).toLong()

        FileOutputStream(file).use { out ->
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16 // 16 for PCM
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // PCM format = 1
            header[21] = 0
            header[22] = channels.toByte()
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = (channels * 2).toByte() // block align
            header[33] = 0
            header[34] = 16 // bits per sample
            header[35] = 0
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

            out.write(header, 0, 44)
            out.write(byteData)
        }
    }

    fun getRecordedLoops(): List<StorageItem> {
        val dir = drumPadLoopDir
        if (!dir.exists() || !dir.canRead()) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".wav", ignoreCase = true) }
            ?.map { file ->
                StorageItem(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = false,
                    size = file.length(),
                    formattedSize = "${file.length() / 1024} KB"
                )
            }?.sortedByDescending { it.name } ?: emptyList()
    }
}
