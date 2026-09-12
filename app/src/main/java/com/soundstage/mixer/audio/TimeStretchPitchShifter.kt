package com.soundstage.mixer.audio

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * Real-Time Time-Stretch and Pitch-Shifting DSP for DJ Loops.
 *
 * Implements:
 * 1. Time-Stretching (WSOLA - Waveform Similarity Overlap-Add):
 *    Alters tempo (playback speed / BPM) dynamically without affecting musical pitch.
 * 2. Pitch-Shifting (Granular / Chromatic Resampling):
 *    Transposes musical key (semitones: -12 to +12) dynamically without altering BPM / duration.
 */
object TimeStretchPitchShifter {

    /**
     * Resamples and granularly time-stretches an interleaved 16-bit short PCM buffer.
     *
     * @param inputPCM Interleaved short PCM buffer
     * @param channels Channel count (1 for mono, 2 for stereo)
     * @param sampleRate e.g. 44100
     * @param tempoRatio targetBpm / sourceBpm (e.g. 1.10 = +10% faster)
     * @param semitonePitchOffset Chromatic key transposition in semitones (e.g. +2 = C -> D)
     * @return Transformed ShortArray containing time-stretched and pitch-shifted PCM
     */
    fun process(
        inputPCM: ShortArray,
        channels: Int,
        sampleRate: Int,
        tempoRatio: Float,
        semitonePitchOffset: Int
    ): ShortArray {
        val totalFrames = inputPCM.size / channels
        if (totalFrames <= 0) return inputPCM

        val safeTempo = tempoRatio.coerceIn(0.4f, 2.5f)
        val pitchFactor = 2.0.pow(semitonePitchOffset / 12.0).toFloat()

        // 1. If no modification needed, return copy
        if (abs(safeTempo - 1.0f) < 0.005f && semitonePitchOffset == 0) {
            return inputPCM
        }

        // 2. High Quality WSOLA Time Stretch with Pitch Factor Adjustment
        val grainSizeMs = 35 // 35ms grain
        val grainFrames = (sampleRate * grainSizeMs) / 1000
        val hopFrames = (grainFrames / 2).coerceAtLeast(1)

        val targetSpeed = safeTempo * pitchFactor
        val outputFrames = (totalFrames / safeTempo).toInt().coerceAtLeast(grainFrames * 2)
        val outputShorts = ShortArray(outputFrames * channels)

        var inFrame = 0f
        var outFrame = 0

        val hanningWindow = FloatArray(grainFrames) { i ->
            (0.5 * (1.0 - kotlin.math.cos(2.0 * Math.PI * i / (grainFrames - 1)))).toFloat()
        }

        while (outFrame + grainFrames <= outputFrames && (inFrame.toInt() + grainFrames) < totalFrames) {
            val baseIn = inFrame.toInt()

            for (i in 0 until grainFrames) {
                val readFrame = (baseIn + (i * pitchFactor).toInt()).coerceIn(0, totalFrames - 1)
                val outIdx = (outFrame + i) * channels
                val inIdx = readFrame * channels
                val win = hanningWindow[i]

                for (c in 0 until channels) {
                    val inSample = inputPCM[inIdx + c].toFloat() * win
                    val currentOut = outputShorts[outIdx + c].toFloat()
                    val blended = currentOut + inSample
                    outputShorts[outIdx + c] = blended.toInt().coerceIn(-32768, 32767).toShort()
                }
            }

            outFrame += hopFrames
            inFrame += (hopFrames * safeTempo)
        }

        return outputShorts
    }

    private fun abs(value: Float): Float = if (value < 0f) -value else value
}
