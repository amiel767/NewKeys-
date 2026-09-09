package com.soundstage.mixer.audio

import kotlin.math.abs
import kotlin.math.min

/**
 * SEAMLESS LOOP TRIMMER
 *
 * Direct PCM sample manipulation for silence trimming and seamless looping:
 * 1. autoTrimSilence: finds startIndex and endIndex where amplitude crosses threshold.
 * 2. processSeamlessLoop: writes PCM with instant wrap-around and 10ms linear crossfade
 *    to eliminate digital clicks/pops at loop restart boundaries.
 */
object SeamlessLoopTrimmer {

    data class TrimResult(val startIndex: Int, val endIndex: Int)

    /**
     * 1. autoTrimSilence on float PCM buffer [-1.0f .. 1.0f]
     */
    fun autoTrimSilence(
        pcmBuffer: FloatArray,
        threshold: Float = 0.005f
    ): TrimResult {
        val totalSamples = pcmBuffer.size
        if (totalSamples <= 0) return TrimResult(0, 0)

        var startIndex = 0
        var endIndex = totalSamples - 1

        // 1. Détection du début
        for (i in 0 until totalSamples) {
            if (abs(pcmBuffer[i]) >= threshold) {
                startIndex = i
                break
            }
        }

        // 2. Détection de la fin (parcours arrière)
        for (i in totalSamples - 1 downTo startIndex) {
            if (abs(pcmBuffer[i]) >= threshold) {
                endIndex = i
                break
            }
        }

        if (endIndex <= startIndex) {
            endIndex = min(startIndex + 1, totalSamples - 1)
        }

        return TrimResult(startIndex, endIndex)
    }

    /**
     * autoTrimSilence on 16-bit short PCM buffer (interleaved stereo or mono)
     * Returns frame indices (where frameIndex = sampleIndex / channels)
     */
    fun autoTrimSilenceShorts(
        pcmBuffer: ShortArray,
        channels: Int = 2,
        threshold: Float = 0.005f
    ): TrimResult {
        val totalSamples = pcmBuffer.size
        if (totalSamples <= 0 || channels <= 0) return TrimResult(0, 0)

        val totalFrames = totalSamples / channels
        val thresholdShort = (threshold * 32767f).toInt().coerceIn(50, 32000)

        var startFrame = 0
        var endFrame = totalFrames - 1

        // Début
        outerStart@ for (f in 0 until totalFrames) {
            val base = f * channels
            for (c in 0 until channels) {
                if (abs(pcmBuffer[base + c].toInt()) >= thresholdShort) {
                    startFrame = f
                    break@outerStart
                }
            }
        }

        // Fin (parcours arrière)
        outerEnd@ for (f in totalFrames - 1 downTo startFrame) {
            val base = f * channels
            for (c in 0 until channels) {
                if (abs(pcmBuffer[base + c].toInt()) >= thresholdShort) {
                    endFrame = f
                    break@outerEnd
                }
            }
        }

        if (endFrame <= startFrame) {
            endFrame = min(startFrame + 1, totalFrames - 1)
        }

        return TrimResult(startFrame, endFrame)
    }

    /**
     * 2. processSeamlessLoop
     * Renders frames with seamless 10ms micro crossfade between endIndex and startIndex.
     */
    fun processSeamlessLoop(
        outputBuffer: FloatArray,
        pcmBuffer: FloatArray,
        startIndex: Int,
        endIndex: Int,
        currentReadIndexRef: IntArray, // [0] = currentReadIndex
        framesToRender: Int,
        sampleRate: Int = 44100
    ) {
        val loopLength = endIndex - startIndex
        if (loopLength <= 0 || framesToRender <= 0) return

        var crossfadeSamples = (sampleRate * 10) / 1000
        if (crossfadeSamples > loopLength / 2) {
            crossfadeSamples = loopLength / 2
        }

        var currentReadIndex = currentReadIndexRef[0]
        if (currentReadIndex < startIndex || currentReadIndex >= endIndex) {
            currentReadIndex = startIndex
        }

        for (i in 0 until framesToRender) {
            val distToEnd = endIndex - currentReadIndex

            if (distToEnd <= crossfadeSamples && crossfadeSamples > 0) {
                val alpha = 1.0f - (distToEnd.toFloat() / crossfadeSamples.toFloat())
                val sampleOut = pcmBuffer[currentReadIndex]
                val incomingIndex = startIndex + (crossfadeSamples - distToEnd)
                val sampleIn = if (incomingIndex in pcmBuffer.indices) pcmBuffer[incomingIndex] else 0f

                outputBuffer[i] = (sampleOut * (1.0f - alpha)) + (sampleIn * alpha)
            } else {
                outputBuffer[i] = pcmBuffer[currentReadIndex]
            }

            currentReadIndex++
            if (currentReadIndex >= endIndex) {
                currentReadIndex = startIndex
            }
        }

        currentReadIndexRef[0] = currentReadIndex
    }

    /**
     * Process 16-bit interleaved stereo PCM with 10ms micro crossfade on loop restart
     */
    fun renderSeamlessStereoShorts(
        output: ShortArray,
        pcm: ShortArray,
        startFrame: Int,
        endFrame: Int,
        channels: Int,
        sampleRate: Int,
        playheadRef: IntArray,
        framesToRender: Int
    ) {
        val loopFrames = endFrame - startFrame
        if (loopFrames <= 0 || framesToRender <= 0) return

        var crossfadeFrames = (sampleRate * 10) / 1000 // 10ms
        if (crossfadeFrames > loopFrames / 2) {
            crossfadeFrames = loopFrames / 2
        }

        var playhead = playheadRef[0]
        if (playhead < startFrame || playhead >= endFrame) {
            playhead = startFrame
        }

        for (f in 0 until framesToRender) {
            val distToEnd = endFrame - playhead
            val outOffset = f * channels

            if (distToEnd <= crossfadeFrames && crossfadeFrames > 0) {
                val alpha = 1.0f - (distToEnd.toFloat() / crossfadeFrames.toFloat())
                val incomingFrame = startFrame + (crossfadeFrames - distToEnd)

                for (c in 0 until channels) {
                    val outSample = pcm[(playhead * channels) + c].toFloat()
                    val inIdx = (incomingFrame * channels) + c
                    val inSample = if (inIdx in pcm.indices) pcm[inIdx].toFloat() else 0f

                    val blended = (outSample * (1.0f - alpha)) + (inSample * alpha)
                    output[outOffset + c] = blended.toInt().coerceIn(-32768, 32767).toShort()
                }
            } else {
                val inOffset = playhead * channels
                for (c in 0 until channels) {
                    output[outOffset + c] = pcm[inOffset + c]
                }
            }

            playhead++
            if (playhead >= endFrame) {
                playhead = startFrame
            }
        }

        playheadRef[0] = playhead
    }
}
