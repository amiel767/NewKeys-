package com.example.model

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.example.audio.NativeAudioBridge
import kotlin.math.max

/**
 * Direct Audio Output Pump for Native FluidSynth SoundFonts.
 *
 * Pulls and streams native C++ FluidSynth audio into Android AudioTrack
 * whenever hardware Oboe is inactive, guaranteeing pristine soundfont playback
 * with zero synthetic superposition and zero thread collisions.
 */
class FallbackSynth {

    companion object {
        private const val TAG = "DirectAudioPump"
        private const val SAMPLE_RATE = 48000
    }

    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

    @Volatile private var isRunning = false
    @Volatile private var masterGain: Float = 0.85f
    @Volatile var isBypassed: Boolean = false

    fun start() {
        if (isRunning) return
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = max(minBuf * 2, 4096)

            audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
                bufSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            audioTrack?.play()
            isRunning = true

            audioThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val bufferFrames = 256
                val totalStereoSamples = bufferFrames * 2
                val pcmBuffer = ShortArray(totalStereoSamples)

                while (isRunning) {
                    if (isBypassed) {
                        try {
                            Thread.sleep(20)
                        } catch (_: InterruptedException) {
                            break
                        }
                        continue
                    }

                    // Render direct from native FluidSynth C++
                    val renderedFrames = NativeAudioBridge.safeRenderNativeAudio(pcmBuffer, bufferFrames)

                    if (renderedFrames > 0) {
                        audioTrack?.write(pcmBuffer, 0, totalStereoSamples)
                    } else {
                        // Oboe is active or no frames rendered -> sleep briefly to prevent CPU spinning
                        try {
                            Thread.sleep(15)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            }, "DirectAudioPumpThread").apply { start() }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio pump: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        audioThread?.interrupt()
        audioThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    fun noteOn(midiNote: Int, velocity: Float) {
        // Synthetic oscillator removed: SoundFonts only
    }

    fun noteOff(midiNote: Int) {
        // Synthetic oscillator removed: SoundFonts only
    }

    fun allNotesOff() {
        // Synthetic oscillator removed: SoundFonts only
    }

    fun playDrumHit(padIndex: Int, velocity: Float) {
        // Synthetic drum hit removed: SoundFonts only
    }

    fun setMasterGain(gain: Float) {
        masterGain = gain.coerceIn(0f, 1f)
    }
}
