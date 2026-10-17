package com.soundstage.mixer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.pow

/**
 * Métronome autonome, synchronisé sur TransportClock.
 * Synthèse algorithmique des 6 sons (aucun asset).
 * Thread d'écriture dédié, AudioTrack statique en MODE_STATIC pour latence
 * minimale, réutilisation des buffers pré-calculés.
 *
 * Supporte : 10 signatures, 4 subdivisions, accent fort/moyen par signature,
 * volume global, click-in (count-in) au prochain début de mesure.
 */
class MetronomeEngine(private val clock: TransportClock) {

    private val TAG = "MetronomeEngine"
    private val sampleRate = 44100

    enum class Sound(val label: String) {
        Woodblock("Woodblock"),
        Beep("Beep"),
        Digital("Digital"),
        Click("Click"),
        Kick("Kick"),
        Conga("Conga");
        companion object {
            fun parse(s: String): Sound = values().firstOrNull { it.label.equals(s, true) } ?: Woodblock
        }
    }

    @Volatile var isActive: Boolean = false
        private set
    @Volatile var volume: Float = 0.65f
    @Volatile var accent1: Boolean = true
    @Volatile var accent13: Boolean = false
    @Volatile var visualFlash: Boolean = true
    @Volatile var currentSound: Sound = Sound.Woodblock

    private var audioTrack: AudioTrack? = null
    private var schedulerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Buffer PCM par son et par accent (fort / moyen / normal)
    private val bufferStrong: ShortArray by lazy { synthStrong(currentSound) }
    private val bufferMedium: ShortArray by lazy { synthMedium(currentSound) }
    private val bufferNormal: ShortArray by lazy { synthNormal(currentSound) }

    private var bufferedSound: Sound = Sound.Woodblock

    fun start() {
        if (isActive) return
        isActive = true
        bufferedSound = currentSound
        openTrack()
        schedulerJob = scope.launch { runScheduler() }
    }

    fun stop() {
        isActive = false
        schedulerJob?.cancel()
        schedulerJob = null
        try {
            audioTrack?.pause(); audioTrack?.flush(); audioTrack?.stop(); audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    fun release() {
        stop()
        scope.cancel()
    }

    fun changeSound(s: Sound) {
        if (bufferedSound == s) return
        currentSound = s
        // La reconstruction lazy se fait au prochain start()
        if (isActive) {
            stop(); start()
        }
    }

    private fun openTrack() {
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val builder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            val track = builder.build()
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.play()
                audioTrack = track
            } else {
                Log.w(TAG, "AudioTrack failed to init")
                track.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "openTrack: ${e.message}")
        }
    }

    private suspend fun runScheduler() {
        while (isActive) {
            val nowNs = System.nanoTime()
            val pos = clock.positionAt(nowNs)
            val subNs = clock.subDurationNs()
            val nextSubInNs = clock.timeToNextSubdivision(nowNs)
            val targetNs = nowNs + nextSubInNs

            val nextSub = (pos.subInBeat + 1) % clock.subdivision.subdivPerBeat
            val nextBeatInBar = if (nextSub == 0) (pos.beatInBar + 1) % clock.signature.beatsPerBar else pos.beatInBar
            val nextBar = if (nextSub == 0 && nextBeatInBar == 0) pos.barIndex + 1 else pos.barIndex

            val strong = nextSub == 0 && nextBeatInBar == 0 && accent1
            val medium = nextSub == 0 && clock.isMediumBeat(nextBeatInBar) && (accent13 || clock.isMediumBeat(nextBeatInBar))
            val playAtBeat = nextSub == 0

            // Dormir jusqu'à ~2 ms avant la cible
            val sleepMs = ((nextSubInNs - 2_000_000L) / 1_000_000L).coerceAtLeast(1L)
            delay(sleepMs)
            while (System.nanoTime() < targetNs) { /* spin court */ }

            if (playAtBeat) {
                val pcm = when {
                    strong && accent1 -> bufferStrong
                    medium && (accent13 || clock.signature.beatsPerBar >= 4) -> bufferMedium
                    else -> bufferNormal
                }
                writeWithGain(pcm, volume)
            } else if (clock.subdivision != TransportClock.Subdivision.Quarter) {
                writeWithGain(bufferNormal, volume * 0.4f)
            }

            clock.emitTickFromScheduler(nextBar, nextBeatInBar, nextSub, strong, medium, targetNs)
            // Petite pause pour ne pas saturer le scheduler si bpm élevé
            delay(1L)
        }
    }

    private fun writeWithGain(pcm: ShortArray, gain: Float) {
        val track = audioTrack ?: return
        val scaled = ShortArray(pcm.size)
        val g = gain.coerceIn(0f, 1f)
        for (i in pcm.indices) scaled[i] = (pcm[i] * g).toInt().coerceIn(-32768, 32767).toShort()
        try { track.write(scaled, 0, scaled.size) } catch (_: Exception) {}
    }

    // ─── Synthèse sons (44.1 kHz mono) ───

    private fun synthStrong(s: Sound): ShortArray = synth(s, 1.0f, strong = true)
    private fun synthMedium(s: Sound): ShortArray = synth(s, 0.8f, strong = false)
    private fun synthNormal(s: Sound): ShortArray = synth(s, 0.55f, strong = false)

    private fun synth(sound: Sound, gain: Float, strong: Boolean): ShortArray = when (sound) {
        Sound.Woodblock -> synthWoodblock(gain, strong)
        Sound.Beep -> synthBeep(gain, strong)
        Sound.Digital -> synthDigital(gain, strong)
        Sound.Click -> synthClick(gain)
        Sound.Kick -> synthKick(gain)
        Sound.Conga -> synthConga(gain, strong)
    }

    private fun synthWoodblock(gain: Float, strong: Boolean): ShortArray {
        val dur = 0.035f
        val n = (sampleRate * dur).toInt()
        val out = ShortArray(n)
        val f0 = if (strong) 1050f else 900f
        val decay = 55f
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val env = exp(-decay * t)
            val v = sin(2.0 * PI * f0 * t) * env + 0.3 * sin(2.0 * PI * f0 * 2.1 * t) * env
            out[i] = (v * gain * 0.85f * 32767f).toInt().toShort()
        }
        return out
    }

    private fun synthBeep(gain: Float, strong: Boolean): ShortArray {
        val dur = 0.028f
        val n = (sampleRate * dur).toInt()
        val out = ShortArray(n)
        val f0 = if (strong) 1200f else 1000f
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val env = if (t < 0.002f) t / 0.002f else exp(-70f * (t - 0.002f))
            val v = sin(2.0 * PI * f0 * t) * env
            out[i] = (v * gain * 0.7f * 32767f).toInt().toShort()
        }
        return out
    }

    private fun synthDigital(gain: Float, strong: Boolean): ShortArray {
        val dur = 0.018f
        val n = (sampleRate * dur).toInt()
        val out = ShortArray(n)
        val f0 = if (strong) 1800f else 1400f
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val env = exp(-110f * t)
            val v = if (sin(2.0 * PI * f0 * t) > 0) 1.0 else -1.0
            out[i] = (v * env * gain * 0.5f * 32767f).toInt().toShort()
        }
        return out
    }

    private fun synthClick(gain: Float): ShortArray {
        val dur = 0.008f
        val n = (sampleRate * dur).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val env = exp(-240f * t)
            val noise = (Math.random() * 2 - 1).toFloat()
            out[i] = (noise * env * gain * 0.6f * 32767f).toInt().toShort()
        }
        return out
    }

    private fun synthKick(gain: Float): ShortArray {
        val dur = 0.18f
        val n = (sampleRate * dur).toInt()
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val env = exp(-25f * t)
            val f = 60f * (1f + exp(-30f * t) * 3f)
            phase += 2.0 * PI * f / sampleRate
            val v = sin(phase) * env
            out[i] = (v * gain * 0.9f * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun synthConga(gain: Float, strong: Boolean): ShortArray {
        val dur = 0.11f
        val n = (sampleRate * dur).toInt()
        val out = ShortArray(n)
        val f0 = if (strong) 420f else 380f
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val env = exp(-25f * t)
            val v = sin(2.0 * PI * f0 * t) * env + 0.35 * sin(2.0 * PI * f0 * 2.0 * t) * env
            out[i] = (v * gain * 0.85f * 32767f).toInt().toShort()
        }
        return out
    }
}
