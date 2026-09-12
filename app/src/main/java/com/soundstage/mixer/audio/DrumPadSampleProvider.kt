package com.soundstage.mixer.audio

import android.content.Context
import com.soundstage.mixer.model.DrumPadItem
import java.io.File
import kotlin.math.exp
import kotlin.math.sin

/**
 * Provides synthesized or decoded PCM samples for DrumPad looper mixdowns.
 */
object DrumPadSampleProvider {

    private val sampleCache = java.util.concurrent.ConcurrentHashMap<String, ShortArray>()

    fun getOrGeneratePadPcm(context: Context, pad: DrumPadItem): ShortArray {
        val sampleFile = when {
            pad.sampleFilePath.isNotEmpty() && File(pad.sampleFilePath).exists() -> File(pad.sampleFilePath)
            File(context.filesDir, "LiveKeys/DrumPad/${pad.sampleFileName}").exists() -> File(context.filesDir, "LiveKeys/DrumPad/${pad.sampleFileName}")
            File(context.filesDir, pad.sampleFileName).exists() -> File(context.filesDir, pad.sampleFileName)
            else -> null
        }

        if (sampleFile != null && sampleFile.exists()) {
            val cached = sampleCache[sampleFile.absolutePath]
            if (cached != null) return cached
            try {
                val decoded = kotlinx.coroutines.runBlocking { AudioDecoder.decodeAudioFile(sampleFile) }
                if (decoded != null) {
                    // Convert float stereo to short stereo
                    val shorts = ShortArray(decoded.pcmData.size)
                    for (i in decoded.pcmData.indices) {
                        shorts[i] = (decoded.pcmData[i].coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
                    }
                    sampleCache[sampleFile.absolutePath] = shorts
                    return shorts
                }
            } catch (_: Exception) {}
        }

        // Fallback: Generate high quality synth drum hit (44.1kHz stereo)
        val defaultId = ((pad.id - 1) % 8) + 1
        return generateSynthDrumPcm(defaultId)
    }

    private fun generateSynthDrumPcm(kitId: Int): ShortArray {
        val sampleRate = 44100
        val durationMs = when (kitId) {
            1 -> 350 // Kick
            2 -> 250 // Snare
            3 -> 80  // Closed Hat
            4 -> 300 // Open Hat
            5 -> 200 // Clap
            6 -> 300 // Low Tom
            7 -> 280 // Mid Tom
            8 -> 600 // Crash
            else -> 200
        }

        val totalFrames = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(totalFrames * 2)

        for (f in 0 until totalFrames) {
            val t = f.toDouble() / sampleRate
            val sampleVal: Double = when (kitId) {
                1 -> { // Punchy Kick (Pitch sweep 150Hz -> 45Hz)
                    val freq = 45.0 + 105.0 * exp(-t * 28.0)
                    val decay = exp(-t * 12.0)
                    sin(2.0 * Math.PI * freq * t) * decay * 30000.0
                }
                2 -> { // Crisp Snare (Tone + Noise)
                    val tone = sin(2.0 * Math.PI * 180.0 * t) * exp(-t * 22.0) * 16000.0
                    val noise = (Math.random() * 2.0 - 1.0) * exp(-t * 18.0) * 15000.0
                    tone + noise
                }
                3 -> { // Tight Closed Hat (High metallic noise)
                    val noise = (Math.random() * 2.0 - 1.0) * exp(-t * 65.0) * 18000.0
                    noise
                }
                4 -> { // Open Hat
                    val noise = (Math.random() * 2.0 - 1.0) * exp(-t * 14.0) * 18000.0
                    noise
                }
                5 -> { // Clap (Multi-burst)
                    val burst = if (t < 0.03) (Math.random() * 2.0 - 1.0) * 22000.0 else (Math.random() * 2.0 - 1.0) * exp(-(t - 0.03) * 20.0) * 20000.0
                    burst
                }
                6 -> { // Low Tom
                    val freq = 80.0 + 40.0 * exp(-t * 15.0)
                    val decay = exp(-t * 9.0)
                    sin(2.0 * Math.PI * freq * t) * decay * 26000.0
                }
                7 -> { // Mid Tom
                    val freq = 120.0 + 50.0 * exp(-t * 15.0)
                    val decay = exp(-t * 10.0)
                    sin(2.0 * Math.PI * freq * t) * decay * 26000.0
                }
                else -> { // Crash
                    (Math.random() * 2.0 - 1.0) * exp(-t * 7.0) * 20000.0
                }
            }

            val s = sampleVal.toInt().coerceIn(-32768, 32767).toShort()
            buffer[f * 2] = s
            buffer[f * 2 + 1] = s
        }

        return buffer
    }
}
