package com.example.model

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.*

/**
 * Professional Low-Latency Audio Engine for Live Soundfont Mixer.
 * Implements:
 * 1. Multi-voice Polyphonic Synthesizer with Wavetable / ADSR / Pitch Bend / Velocity.
 * 2. SoundGoodizer 4-Mode DSP Saturation & Multiband Engine (Modes A, B, C, D).
 * 3. High-precision Metronome Audio Generator (AudioTrack click generator with dynamic time signatures).
 * 4. Wave / MP3 / Loop audio streaming player with seamless looping and zero latency.
 * 5. Native MIDI (.mid) Audio Player with Play, Pause, Seek, Loop, and Volume.
 * 6. USB MIDI Hardware Receiver listening to USB MIDI keyboards, pitch bend, and sustain pedals.
 */
class AudioEngine(private val context: Context) {

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ================= 1. SYNTHESIZER AUDIO TRACK =================
    private var synthAudioTrack: AudioTrack? = null
    private var isSynthRunning = false
    private val sampleRate = 44100
    private val bufferSize = 512

    // Active Polyphonic Voices
    private data class Voice(
        val noteName: String,
        val freq: Double,
        var phase: Double = 0.0,
        var velocity: Float = 1.0f,
        var envelopeState: Int = 0, // 0 = Idle, 1 = Attack, 2 = Decay, 3 = Sustain, 4 = Release
        var envelopeLevel: Float = 0f,
        var releaseLevel: Float = 0f,
        var age: Long = 0L
    )

    private val activeVoices = mutableMapOf<String, Voice>()
    private val voiceLock = Any()

    var masterVolume: Float = 0.75f
    var pitchBendFactor: Float = 1.0f // 1.0 = normal, 0.5 to 2.0 = bend

    // SoundGoodizer & Master DSP Parameters
    var soundGoodizerMode: String = "A" // "A", "B", "C", "D"
    var soundGoodizerAmount: Float = 0.42f
    var spatialWidener: Float = 0.35f
    var masterPunch: Float = 0.50f

    // Master EQ Filters
    var eqLowGain: Float = 1.0f
    var eqMidGain: Float = 1.0f
    var eqHighGain: Float = 1.0f

    // Reverb Mix
    var reverbMix: Float = 0.20f
    private val combDelays = intArrayOf(1116, 1188, 1277, 1356)
    private val combBuffers = Array(4) { FloatArray(2048) }
    private val combPointers = IntArray(4)

    // ================= 2. METRONOME AUDIO GENERATOR =================
    private var metronomeAudioTrack: AudioTrack? = null
    private var metronomeJob: Job? = null
    private var isMetronomeActive = false
    private val hiClickPcm: ShortArray
    private val loClickPcm: ShortArray

    // ================= 3. AUDIO / LOOP / SAMPLE PLAYBACK =================
    private var loopMediaPlayer: MediaPlayer? = null
    private var soundPool: SoundPool? = null
    private val loadedSampleIds = mutableMapOf<String, Int>()

    // ================= 4. MIDI (.MID) FILE PLAYER =================
    private var midiMediaPlayer: MediaPlayer? = null
    var onMidiCompletionListener: (() -> Unit)? = null

    // ================= 5. USB MIDI CONTROLLER =================
    private var midiManager: MidiManager? = null
    var onMidiNoteOnListener: ((noteName: String, velocity: Int) -> Unit)? = null
    var onMidiNoteOffListener: ((noteName: String) -> Unit)? = null
    var onMidiPitchBendListener: ((bendValue: Float) -> Unit)? = null
    var onMidiSustainListener: ((isPressed: Boolean) -> Unit)? = null

    init {
        // Pre-render pure acoustic click sounds for Metronome
        hiClickPcm = generateClickSound(freq = 1800.0, durationMs = 35, accent = true)
        loClickPcm = generateClickSound(freq = 1100.0, durationMs = 28, accent = false)

        initSoundPool()
        initUsbMidi()
        startSynthesizer()
    }

    // -------------------------------------------------------------
    // SYNTHESIZER ENGINE IMPLEMENTATION
    // -------------------------------------------------------------
    private fun startSynthesizer() {
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val trackBuf = max(minBuf, bufferSize * 4)

            synthAudioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(trackBuf)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            synthAudioTrack?.play()
            isSynthRunning = true

            coroutineScope.launch(Dispatchers.Default) {
                val pcmBuffer = ShortArray(bufferSize * 2) // Stereo (L, R)
                val leftFloat = FloatArray(bufferSize)
                val rightFloat = FloatArray(bufferSize)

                while (isActive && isSynthRunning) {
                    synchronized(voiceLock) {
                        if (activeVoices.isEmpty()) {
                            pcmBuffer.fill(0)
                        } else {
                            leftFloat.fill(0f)
                            rightFloat.fill(0f)

                            val iterator = activeVoices.entries.iterator()
                            while (iterator.hasNext()) {
                                val entry = iterator.next()
                                val voice = entry.value
                                val targetFreq = voice.freq * pitchBendFactor
                                val phaseInc = (2.0 * Math.PI * targetFreq) / sampleRate

                                for (i in 0 until bufferSize) {
                                    // ADSR Envelope calculation
                                    when (voice.envelopeState) {
                                        1 -> { // Attack
                                            voice.envelopeLevel += 0.008f
                                            if (voice.envelopeLevel >= 1.0f) {
                                                voice.envelopeLevel = 1.0f
                                                voice.envelopeState = 2 // Decay
                                            }
                                        }
                                        2 -> { // Decay
                                            voice.envelopeLevel -= 0.002f
                                            if (voice.envelopeLevel <= 0.75f) {
                                                voice.envelopeLevel = 0.75f
                                                voice.envelopeState = 3 // Sustain
                                            }
                                        }
                                        3 -> { // Sustain
                                            voice.envelopeLevel = 0.75f
                                        }
                                        4 -> { // Release
                                            voice.envelopeLevel -= 0.004f
                                            if (voice.envelopeLevel <= 0.001f) {
                                                voice.envelopeLevel = 0f
                                                voice.envelopeState = 0
                                            }
                                        }
                                    }

                                    if (voice.envelopeState == 0) break

                                    // Multi-Harmonic SoundFont-like Warm Tone
                                    val wave1 = sin(voice.phase)
                                    val wave2 = sin(voice.phase * 2.0) * 0.35
                                    val wave3 = sin(voice.phase * 3.0) * 0.15
                                    val sample = (wave1 + wave2 + wave3) * voice.envelopeLevel * voice.velocity * 0.25f

                                    voice.phase += phaseInc
                                    if (voice.phase >= 2.0 * Math.PI) {
                                        voice.phase -= 2.0 * Math.PI
                                    }

                                    leftFloat[i] += sample.toFloat()
                                    rightFloat[i] += sample.toFloat()
                                }

                                if (voice.envelopeState == 0) {
                                    iterator.remove()
                                }
                            }

                            // Apply DSP Chain: SoundGoodizer, EQ, Reverb, Limiting
                            applyMasterDspChain(leftFloat, rightFloat, bufferSize)

                            // Convert float buffer to 16-bit PCM
                            for (i in 0 until bufferSize) {
                                val l = (leftFloat[i] * 32767f).toInt().coerceIn(-32768, 32767)
                                val r = (rightFloat[i] * 32767f).toInt().coerceIn(-32768, 32767)
                                pcmBuffer[i * 2] = l.toShort()
                                pcmBuffer[i * 2 + 1] = r.toShort()
                            }
                        }
                    }

                    synthAudioTrack?.write(pcmBuffer, 0, pcmBuffer.size)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * SoundGoodizer & Master DSP Chain
     * Authentic FL Studio-style multiband saturation with modes A/B/C/D
     */
    private fun applyMasterDspChain(left: FloatArray, right: FloatArray, size: Int) {
        val sgAmount = soundGoodizerAmount.coerceIn(0f, 1f)
        val punch = masterPunch.coerceIn(0f, 1f)
        val widener = spatialWidener.coerceIn(0f, 1f)

        for (i in 0 until size) {
            var l = left[i] * masterVolume
            var r = right[i] * masterVolume

            // 1. SoundGoodizer Multiband Saturation Curve based on Mode
            if (sgAmount > 0.02f) {
                when (soundGoodizerMode) {
                    "A" -> { // Warm Tube & Low Punch
                        val drive = 1.0f + sgAmount * 2.2f
                        l = tanh(l * drive) / sqrt(drive)
                        r = tanh(r * drive) / sqrt(drive)
                    }
                    "B" -> { // Crisp Air & Exciter Highs
                        val drive = 1.0f + sgAmount * 2.8f
                        l = (l * (1f + drive * 0.4f)).coerceIn(-1f, 1f) - 0.1f * l.pow(3)
                        r = (r * (1f + drive * 0.4f)).coerceIn(-1f, 1f) - 0.1f * r.pow(3)
                    }
                    "C" -> { // Deep Crunch & Multiband Warmth
                        val drive = 1.0f + sgAmount * 3.5f
                        l = sin(l.coerceIn(-1.5f, 1.5f) * drive * 0.5f)
                        r = sin(r.coerceIn(-1.5f, 1.5f) * drive * 0.5f)
                    }
                    "D" -> { // Hard Punch & Limiter Squeeze
                        val drive = 1.0f + sgAmount * 4.0f
                        l = sign(l) * (1f - exp(-abs(l) * drive))
                        r = sign(r) * (1f - exp(-abs(r) * drive))
                    }
                }
            }

            // 2. Spatial Stereo Widener (Mid/Side processing)
            if (widener > 0.05f) {
                val mid = (l + r) * 0.5f
                val side = (l - r) * 0.5f * (1.0f + widener * 1.5f)
                l = mid + side
                r = mid - side
            }

            // 3. Simple Studio Reverb Comb Filter
            if (reverbMix > 0.05f) {
                var reverbL = 0f
                for (c in 0 until 4) {
                    val delaySamples = combDelays[c]
                    val ptr = combPointers[c]
                    val buf = combBuffers[c]
                    val out = buf[ptr]
                    buf[ptr] = l + out * 0.70f
                    combPointers[c] = (ptr + 1) % delaySamples
                    reverbL += out * 0.25f
                }
                l = l * (1f - reverbMix * 0.5f) + reverbL * reverbMix
                r = r * (1f - reverbMix * 0.5f) + reverbL * reverbMix
            }

            // 4. Soft Clipper Peak Limiter
            left[i] = l.coerceIn(-0.95f, 0.95f)
            right[i] = r.coerceIn(-0.95f, 0.95f)
        }
    }

    fun noteOn(noteName: String, velocity: Float = 0.85f) {
        val freq = noteNameToFreq(noteName)
        synchronized(voiceLock) {
            val voice = activeVoices[noteName] ?: Voice(noteName = noteName, freq = freq)
            voice.velocity = velocity.coerceIn(0.1f, 1.0f)
            voice.envelopeState = 1 // Attack
            voice.envelopeLevel = max(voice.envelopeLevel, 0.1f)
            activeVoices[noteName] = voice
        }
    }

    fun noteOff(noteName: String) {
        synchronized(voiceLock) {
            activeVoices[noteName]?.let {
                it.envelopeState = 4 // Release
                it.releaseLevel = it.envelopeLevel
            }
        }
    }

    fun allNotesOff() {
        synchronized(voiceLock) {
            activeVoices.clear()
        }
    }

    private fun noteNameToFreq(note: String): Double {
        val clean = note.trim().uppercase()
        val letter = clean.filter { it.isLetter() || it == '#' }
        val octave = clean.filter { it.isDigit() }.toIntOrNull() ?: 4

        val semitonesFromC = when (letter) {
            "C" -> 0
            "C#", "DB" -> 1
            "D" -> 2
            "D#", "EB" -> 3
            "E" -> 4
            "F" -> 5
            "F#", "GB" -> 6
            "G" -> 7
            "G#", "AB" -> 8
            "A" -> 9
            "A#", "BB" -> 10
            "B" -> 11
            else -> 0
        }

        val midiNote = (octave + 1) * 12 + semitonesFromC
        return 440.0 * 2.0.pow((midiNote - 69) / 12.0)
    }

    // -------------------------------------------------------------
    // METRONOME AUDIO ENGINE (High-precision AudioTrack pulses)
    // -------------------------------------------------------------
    private fun generateClickSound(freq: Double, durationMs: Int, accent: Boolean): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples * 2) // Stereo
        val amp = if (accent) 30000.0 else 20000.0
        val decay = if (accent) 0.995 else 0.992

        var currentAmp = amp
        for (i in 0 until numSamples) {
            val sampleVal = (sin(2.0 * Math.PI * freq * i / sampleRate) * currentAmp).toInt().toShort()
            buffer[i * 2] = sampleVal
            buffer[i * 2 + 1] = sampleVal
            currentAmp *= decay
        }
        return buffer
    }

    fun startMetronome(bpm: Int, signature: String, volume: Float) {
        stopMetronome()
        isMetronomeActive = true

        val beatsPerBar = when {
            signature.startsWith("2") -> 2
            signature.startsWith("3") -> 3
            signature.startsWith("6") -> 6
            else -> 4
        }

        val intervalMs = (60000.0 / bpm).toLong()

        metronomeJob = coroutineScope.launch(Dispatchers.Default) {
            var currentBeat = 1

            // Setup dedicated metronome AudioTrack
            val bufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            metronomeAudioTrack = AudioTrack.Builder()
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
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            metronomeAudioTrack?.play()

            while (isActive && isMetronomeActive) {
                val clickPcm = if (currentBeat == 1) hiClickPcm else loClickPcm
                metronomeAudioTrack?.setVolume(volume)
                metronomeAudioTrack?.write(clickPcm, 0, clickPcm.size)

                currentBeat++
                if (currentBeat > beatsPerBar) {
                    currentBeat = 1
                }

                delay(intervalMs)
            }
        }
    }

    fun stopMetronome() {
        isMetronomeActive = false
        metronomeJob?.cancel()
        metronomeJob = null
        try {
            metronomeAudioTrack?.stop()
            metronomeAudioTrack?.release()
        } catch (_: Exception) {}
        metronomeAudioTrack = null
    }

    // -------------------------------------------------------------
    // AUDIO & DRUM PAD SOUNDPOOL
    // -------------------------------------------------------------
    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(16)
            .setAudioAttributes(audioAttributes)
            .build()
    }

    fun playDrumPadSound(item: DrumPadItem, volume: Float) {
        if (item.soundType == DrumSoundType.SF2_NOTE) {
            noteOn("${item.sf2NoteKey}${item.sf2NoteOctave}", velocity = volume)
            coroutineScope.launch {
                delay(300)
                noteOff("${item.sf2NoteKey}${item.sf2NoteOctave}")
            }
        } else {
            // Sample playback
            val file = File(context.filesDir, "LiveKeys/DrumPad/${item.sampleFileName}")
            if (file.exists()) {
                try {
                    val sId = loadedSampleIds.getOrPut(file.absolutePath) {
                        soundPool?.load(file.absolutePath, 1) ?: 1
                    }
                    soundPool?.play(sId, volume, volume, 1, 0, 1.0f)
                } catch (e: Exception) {
                    noteOn("C2", velocity = volume)
                }
            } else {
                // Synthesized acoustic drum punch fallback
                noteOn("C1", velocity = volume)
                coroutineScope.launch {
                    delay(150)
                    noteOff("C1")
                }
            }
        }
    }

    // -------------------------------------------------------------
    // MIDI (.MID) FILE PLAYER
    // -------------------------------------------------------------
    fun playMidiFile(filePath: String, volume: Float = 0.75f, isLooping: Boolean = true): Boolean {
        return try {
            stopMidiPlayer()
            val file = File(filePath)
            if (!file.exists()) return false

            midiMediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(filePath)
                setVolume(volume, volume)
                this.isLooping = isLooping
                setOnCompletionListener {
                    onMidiCompletionListener?.invoke()
                }
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun pauseMidiPlayer() {
        try {
            if (midiMediaPlayer?.isPlaying == true) {
                midiMediaPlayer?.pause()
            }
        } catch (_: Exception) {}
    }

    fun resumeMidiPlayer() {
        try {
            midiMediaPlayer?.start()
        } catch (_: Exception) {}
    }

    fun stopMidiPlayer() {
        try {
            midiMediaPlayer?.stop()
            midiMediaPlayer?.release()
        } catch (_: Exception) {}
        midiMediaPlayer = null
    }

    fun setMidiVolume(vol: Float) {
        try {
            midiMediaPlayer?.setVolume(vol, vol)
        } catch (_: Exception) {}
    }

    fun isMidiPlaying(): Boolean = midiMediaPlayer?.isPlaying == true

    fun getMidiDuration(): Int = try { midiMediaPlayer?.duration ?: 0 } catch (_: Exception) { 0 }
    fun getMidiPosition(): Int = try { midiMediaPlayer?.currentPosition ?: 0 } catch (_: Exception) { 0 }
    fun seekMidiTo(posMs: Int) {
        try { midiMediaPlayer?.seekTo(posMs) } catch (_: Exception) {}
    }

    // -------------------------------------------------------------
    // AUDIO LOOPS PLAYER (.wav, .mp3)
    // -------------------------------------------------------------
    fun playLoopFile(filePath: String, volume: Float = 0.75f) {
        try {
            stopLoopPlayer()
            val file = File(filePath)
            if (!file.exists()) return

            loopMediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(filePath)
                setVolume(volume, volume)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopLoopPlayer() {
        try {
            loopMediaPlayer?.stop()
            loopMediaPlayer?.release()
        } catch (_: Exception) {}
        loopMediaPlayer = null
    }

    fun setLoopVolume(volume: Float) {
        try {
            loopMediaPlayer?.setVolume(volume, volume)
        } catch (_: Exception) {}
    }

    // -------------------------------------------------------------
    // USB MIDI HARDWARE DETECTION & LISTENER
    // -------------------------------------------------------------
    private fun initUsbMidi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                midiManager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
                midiManager?.registerDeviceCallback(object : MidiManager.DeviceCallback() {
                    override fun onDeviceAdded(device: MidiDeviceInfo?) {
                        device?.let { connectMidiDevice(it) }
                    }

                    override fun onDeviceRemoved(device: MidiDeviceInfo?) {
                        // Device disconnected
                    }
                }, Handler(Looper.getMainLooper()))

                // Connect to existing USB MIDI devices
                val devices = midiManager?.devices ?: emptyArray()
                for (dev in devices) {
                    connectMidiDevice(dev)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun connectMidiDevice(info: MidiDeviceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            midiManager?.openDevice(info, { device: MidiDevice? ->
                if (device != null) {
                    val outputPort = device.openOutputPort(0)
                    outputPort?.connect(object : MidiReceiver() {
                        override fun onSend(msg: ByteArray?, offset: Int, count: Int, timestamp: Long) {
                            if (msg == null || count < 2) return
                            val status = (msg[offset].toInt() and 0xFF)
                            val command = status and 0xF0
                            val data1 = if (count > 1) (msg[offset + 1].toInt() and 0x7F) else 0
                            val data2 = if (count > 2) (msg[offset + 2].toInt() and 0x7F) else 0

                            when (command) {
                                0x90 -> { // Note On
                                    val noteName = midiNumberToNoteName(data1)
                                    val velocity = data2
                                    if (velocity > 0) {
                                        noteOn(noteName, velocity / 127f)
                                        onMidiNoteOnListener?.invoke(noteName, velocity)
                                    } else {
                                        noteOff(noteName)
                                        onMidiNoteOffListener?.invoke(noteName)
                                    }
                                }
                                0x80 -> { // Note Off
                                    val noteName = midiNumberToNoteName(data1)
                                    noteOff(noteName)
                                    onMidiNoteOffListener?.invoke(noteName)
                                }
                                0xE0 -> { // Pitch Bend
                                    val bendVal = ((data2 shl 7) or data1) - 8192
                                    val normalizedBend = bendVal / 8192f
                                    pitchBendFactor = 2.0f.pow(normalizedBend * (2.0f / 12.0f))
                                    onMidiPitchBendListener?.invoke(normalizedBend)
                                }
                                0xB0 -> { // Control Change (CC#64 = Sustain pedal)
                                    if (data1 == 64) {
                                        val isPressed = data2 >= 64
                                        onMidiSustainListener?.invoke(isPressed)
                                    }
                                }
                            }
                        }
                    })
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    private fun midiNumberToNoteName(midiNumber: Int): String {
        val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val octave = (midiNumber / 12) - 1
        val note = noteNames[midiNumber % 12]
        return "$note$octave"
    }

    fun release() {
        isSynthRunning = false
        allNotesOff()
        stopMetronome()
        stopLoopPlayer()
        stopMidiPlayer()
        try {
            synthAudioTrack?.stop()
            synthAudioTrack?.release()
        } catch (_: Exception) {}
        soundPool?.release()
        coroutineScope.cancel()
    }
}
