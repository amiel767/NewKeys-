package com.example.model

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * High-performance, low-latency polyphonic fallback audio synthesizer.
 *
 * Ensures the app ALWAYS produces sound when the user interacts with the keyboard,
 * pads, or MIDI devices, even if a SoundFont is not yet loaded or if native FluidSynth
 * is initializing.
 */
class FallbackSynth {

    companion object {
        private const val TAG = "FallbackSynth"
        private const val SAMPLE_RATE = 44100
        private const val MAX_VOICES = 12
    }

    private class Voice {
        var midiNote: Int = -1
        var frequency: Double = 440.0
        var phase: Double = 0.0
        var velocity: Float = 0.8f
        var level: Float = 0.0f
        var state: Int = STATE_IDLE // 0: IDLE, 1: ATTACK, 2: DECAY, 3: SUSTAIN, 4: RELEASE
        var sampleCount: Long = 0

        companion object {
            const val STATE_IDLE = 0
            const val STATE_ATTACK = 1
            const val STATE_DECAY = 2
            const val STATE_SUSTAIN = 3
            const val STATE_RELEASE = 4
        }
    }

    private class DrumVoice {
        var type: Int = 0 // 1: Kick, 2: Snare, 3: Hat, 4: Clap, 5: Tom
        var velocity: Float = 0.9f
        var phase: Double = 0.0
        var sampleCount: Long = 0
        var totalSamples: Long = 0
        var active: Boolean = false
    }

    private val voices = Array(MAX_VOICES) { Voice() }
    private val drumVoices = Array(8) { DrumVoice() }

    private var audioTrack: AudioTrack? = null
    private var synthThread: Thread? = null

    @Volatile private var isRunning = false
    @Volatile private var masterGain: Float = 0.75f
    @Volatile var isBypassed: Boolean = false // Set to true when high-fidelity SoundFont is active

    private val random = java.util.Random(1337)

    fun start() {
        if (isRunning) return
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = max(minBuf * 2, 2048)

            audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
                bufSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            audioTrack?.play()
            isRunning = true

            synthThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val bufferFrames = 256
                val pcmBuffer = ShortArray(bufferFrames)

                while (isRunning) {
                    if (isBypassed) {
                        try {
                            Thread.sleep(10)
                        } catch (_: InterruptedException) {
                            break
                        }
                        continue
                    }

                    var hasActiveVoices = false

                    for (i in 0 until bufferFrames) {
                        var sample = 0.0

                        // 1. Synthesize Polyphonic Melodic Voices
                        synchronized(voices) {
                            for (v in voices) {
                                if (v.state == Voice.STATE_IDLE) continue
                                hasActiveVoices = true

                                // Update ADSR Envelope
                                when (v.state) {
                                    Voice.STATE_ATTACK -> {
                                        v.level += 0.015f // ~6ms attack
                                        if (v.level >= 1.0f) {
                                            v.level = 1.0f
                                            v.state = Voice.STATE_DECAY
                                        }
                                    }
                                    Voice.STATE_DECAY -> {
                                        v.level -= 0.00035f // ~200ms decay to sustain
                                        if (v.level <= 0.65f) {
                                            v.level = 0.65f
                                            v.state = Voice.STATE_SUSTAIN
                                        }
                                    }
                                    Voice.STATE_SUSTAIN -> {
                                        // Natural slow acoustic decay while held
                                        v.level -= 0.00003f
                                        if (v.level <= 0.05f) {
                                            v.state = Voice.STATE_IDLE
                                        }
                                    }
                                    Voice.STATE_RELEASE -> {
                                        v.level -= 0.0025f // ~30ms quick release
                                        if (v.level <= 0.001f) {
                                            v.level = 0.0f
                                            v.state = Voice.STATE_IDLE
                                        }
                                    }
                                }

                                val phaseInc = (2.0 * Math.PI * v.frequency) / SAMPLE_RATE
                                v.phase += phaseInc
                                if (v.phase > 2.0 * Math.PI) v.phase -= 2.0 * Math.PI

                                // Warm electric piano / rich harmonic timbre:
                                val f1 = sin(v.phase)
                                val f2 = sin(v.phase * 2.0) * 0.35
                                val f3 = sin(v.phase * 3.0) * 0.15
                                val rawWave = (f1 + f2 + f3) * 0.65

                                sample += rawWave * v.level * v.velocity
                            }
                        }

                        // 2. Synthesize Drum Pad Hits
                        synchronized(drumVoices) {
                            for (dv in drumVoices) {
                                if (!dv.active) continue
                                hasActiveVoices = true

                                val t = dv.sampleCount.toDouble() / SAMPLE_RATE
                                val drumVel = dv.velocity.toDouble()

                                when (dv.type) {
                                    1 -> { // Kick
                                        val env = exp(-t * 16.0)
                                        val freq = 135.0 * exp(-t * 32.0) + 45.0
                                        sample += sin(2.0 * Math.PI * freq * t) * env * drumVel * 0.85
                                    }
                                    2 -> { // Snare
                                        val env = exp(-t * 22.0)
                                        val noise = (random.nextDouble() * 2.0 - 1.0) * 0.50
                                        val tone = sin(2.0 * Math.PI * 185.0 * t) * 0.35
                                        sample += (noise + tone) * env * drumVel * 0.70
                                    }
                                    3 -> { // Hi-Hat
                                        val env = exp(-t * 60.0)
                                        val noise = (random.nextDouble() * 2.0 - 1.0) * 0.40
                                        sample += noise * env * drumVel
                                    }
                                    4 -> { // Clap
                                        val env = exp(-t * 25.0)
                                        val noise = (random.nextDouble() * 2.0 - 1.0) * 0.55
                                        sample += noise * env * drumVel * 0.65
                                    }
                                    else -> { // Tom
                                        val env = exp(-t * 12.0)
                                        val freq = 110.0 * exp(-t * 15.0) + 60.0
                                        sample += sin(2.0 * Math.PI * freq * t) * env * drumVel * 0.75
                                    }
                                }

                                dv.sampleCount++
                                if (dv.sampleCount >= dv.totalSamples) {
                                    dv.active = false
                                }
                            }
                        }

                        val clamped = (sample * masterGain).coerceIn(-0.95, 0.95)
                        pcmBuffer[i] = (clamped * 32767.0).toInt().toShort()
                    }

                    audioTrack?.write(pcmBuffer, 0, bufferFrames)

                    // Power saving if idle
                    if (!hasActiveVoices) {
                        try {
                            Thread.sleep(4)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            }, "FallbackSynthAudioThread").apply { start() }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting FallbackSynth: ${e.message}")
        }
    }

    fun noteOn(midiNote: Int, velocity: Float) {
        val freq = 440.0 * 2.0.pow((midiNote - 69).toDouble() / 12.0)
        synchronized(voices) {
            // Re-trigger existing note if playing
            var voiceToUse = voices.firstOrNull { it.midiNote == midiNote && it.state != Voice.STATE_IDLE }
            if (voiceToUse == null) {
                // Find idle voice or oldest
                voiceToUse = voices.firstOrNull { it.state == Voice.STATE_IDLE }
                    ?: voices.minByOrNull { it.level }
            }

            voiceToUse?.apply {
                this.midiNote = midiNote
                this.frequency = freq
                this.velocity = velocity.coerceIn(0.1f, 1.0f)
                this.level = 0.0f
                this.state = Voice.STATE_ATTACK
                this.phase = 0.0
            }
        }
    }

    fun noteOff(midiNote: Int) {
        synchronized(voices) {
            for (v in voices) {
                if (v.midiNote == midiNote && v.state != Voice.STATE_IDLE) {
                    v.state = Voice.STATE_RELEASE
                }
            }
        }
    }

    fun allNotesOff() {
        synchronized(voices) {
            for (v in voices) {
                v.state = Voice.STATE_RELEASE
            }
        }
    }

    fun playDrumHit(padIndex: Int, velocity: Float) {
        val type = when (padIndex) {
            1, 5 -> 1 // Kick / 808
            2, 6 -> 2 // Snare / Rim
            3, 4, 8, 12 -> 3 // Hats / Cymbal
            7 -> 4 // Clap
            else -> 5 // Tom
        }
        val durationMs = when (type) {
            1 -> 250
            2 -> 180
            3 -> 80
            4 -> 140
            else -> 200
        }

        synchronized(drumVoices) {
            val dv = drumVoices.firstOrNull { !it.active } ?: drumVoices[0]
            dv.type = type
            dv.velocity = velocity.coerceIn(0.1f, 1.0f)
            dv.sampleCount = 0
            dv.totalSamples = (durationMs.toLong() * SAMPLE_RATE) / 1000L
            dv.active = true
        }
    }

    fun setMasterGain(gain: Float) {
        masterGain = gain.coerceIn(0f, 1f)
    }

    fun stop() {
        isRunning = false
        try {
            synthThread?.interrupt()
            synthThread = null
        } catch (_: Exception) {}

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
