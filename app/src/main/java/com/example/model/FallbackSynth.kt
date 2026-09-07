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
 * Runs only when hardware Oboe/AAudio is unavailable or inactive,
 * streaming pure SoundFont audio with rock-solid blocking stream writes (zero stutter, zero saccade).
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
            val bufSize = max(minBuf * 4, 8192)

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
                val bufferFrames = 512
                val totalStereoSamples = bufferFrames * 2
                val pcmBuffer = ShortArray(totalStereoSamples)

                while (isRunning) {
                    if (isBypassed || NativeAudioBridge.safeIsOboeActive()) {
                        try {
                            Thread.sleep(100)
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
                        try {
                            Thread.sleep(50)
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

    fun noteOn(midiNote: Int, velocity: Float) {}
    fun noteOff(midiNote: Int) {}
    fun allNotesOff() {}
    fun playDrumHit(padIndex: Int, velocity: Float) {}
    fun setMasterGain(gain: Float) {
        masterGain = gain.coerceIn(0f, 1f)
    }
}
