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
import android.os.HandlerThread
import android.util.Log
import com.example.audio.NativeAudioBridge
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import kotlin.math.*

/**
 * Event representation for FIFO Asynchronous MIDI Buffer.
 */
sealed class MidiEngineEvent {
    data class NoteOn(val channel: Int, val noteNumber: Int, val velocity: Int) : MidiEngineEvent()
    data class NoteOff(val channel: Int, val noteNumber: Int) : MidiEngineEvent()
    data class PitchBend(val channel: Int, val bendValue: Int) : MidiEngineEvent()
    data class ControlChange(val channel: Int, val controller: Int, val value: Int) : MidiEngineEvent()
}

class SynthChannelParams {
    var volume: Float = 0.85f
    var pan: Float = 0.0f
    var instrumentType: Int = 0
    var transpose: Int = 0
    var isEnabled: Boolean = true
}

class SynthVoice {
    var channel: Int = 0
    var noteNumber: Int = 60
    var targetFrequency: Float = 440f
    var velocity: Float = 0.8f
    var instrumentType: Int = 0
    var phase: Float = 0f
    var phase2: Float = 0f
    var phase3: Float = 0f
    var envStage: Int = 0 // 0 = Off, 1 = Attack, 2 = Decay, 3 = Sustain, 4 = Release
    var envLevel: Float = 0f
    var age: Long = 0L
}

/**
 * Professional Low-Latency Audio & MIDI Engine for Live SoundFont Mixer.
 * Implements:
 * 1. Asynchronous FIFO MIDI Event Queue running on Dispatchers.Default (off Main Thread).
 * 2. Instant non-blocking CC64 Sustain Pedal handling with immediate release of queued NoteOffs.
 * 3. Real SoundFont 2 (SF2) direct synthesis routing to NativeAudioBridge.
 * 4. Real USB MIDI Device detection & listener via MidiManager with live manufacturer/product names.
 * 5. High-precision Metronome click generator.
 * 6. WAV / MP3 Loop audio streaming player with zero latency.
 * 7. Standard MIDI (.mid) player with controls.
 */
class AudioEngine(private val context: Context) {

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val TAG = "AudioEngine"

    // Master DSP Parameters
    var masterVolume: Float = 0.80f
    var pitchBendFactor: Float = 1.0f
    var soundGoodizerMode: String = "A"
    var soundGoodizerAmount: Float = 0.42f
    var spatialWidener: Float = 0.35f
    var masterPunch: Float = 0.50f

    // Active track target for global keyboard notes (0..7)
    var activeTargetChannel: Int = 0

    // Channel parameters for 8 mixer channels + Drum (8) + Tonic (9)
    val channelParams = Array(12) { chIdx ->
        SynthChannelParams().apply {
            instrumentType = chIdx % 8
        }
    }

    // ================= 1. ASYNCHRONOUS MIDI FIFO QUEUE =================
    private val midiEventChannel = Channel<MidiEngineEvent>(Channel.UNLIMITED)
    private val activeHeldNotes = mutableSetOf<Int>()
    private val sustainedNotesToRelease = mutableSetOf<Int>()
    private var isSustainPedalDown = false

    // Callbacks for UI updates (dispatched cleanly)
    var onMidiNoteOnListener: ((noteName: String, velocity: Int) -> Unit)? = null
    var onMidiNoteOffListener: ((noteName: String) -> Unit)? = null
    var onMidiPitchBendListener: ((bendValue: Float) -> Unit)? = null
    var onMidiSustainListener: ((isPressed: Boolean) -> Unit)? = null
    var onDeviceListChanged: ((List<MidiDeviceItem>) -> Unit)? = null

    // ================= 2. REAL-TIME SYNTHESIZER ENGINE =================
    private val SAMPLE_RATE = 44100
    private val BUFFER_SIZE = 1024
    private var synthAudioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val maxVoices = 32
    private val voices = Array(maxVoices) { SynthVoice() }
    private val voiceLock = Any()
    private var voiceCounter = 0L

    // ================= 3. METRONOME AUDIO GENERATOR =================
    private var metronomeAudioTrack: AudioTrack? = null
    private var metronomeJob: Job? = null
    private var isMetronomeActive = false
    private val hiClickPcm: ShortArray
    private val loClickPcm: ShortArray

    // ================= 4. AUDIO / LOOP / SAMPLE PLAYBACK =================
    private var loopMediaPlayer: MediaPlayer? = null
    private var soundPool: SoundPool? = null
    private val loadedSampleIds = mutableMapOf<String, Int>()

    // ================= 5. MIDI (.MID) FILE PLAYER =================
    private var midiMediaPlayer: MediaPlayer? = null
    var onMidiCompletionListener: (() -> Unit)? = null

    // ================= 6. USB MIDI HARDWARE CONTROLLER =================
    private var midiManager: MidiManager? = null
    private var midiHandlerThread: HandlerThread? = null
    private var midiHandler: Handler? = null
    private val openDevices = mutableListOf<MidiDevice>()

    init {
        // Pre-render pure acoustic click sounds for Metronome
        hiClickPcm = generateClickSound(freq = 1800.0, durationMs = 35, accent = true)
        loClickPcm = generateClickSound(freq = 1100.0, durationMs = 28, accent = false)

        initSoundPool()
        startSynthesizerEngine()
        startMidiEventProcessor()
        initUsbMidi()
    }

    // -------------------------------------------------------------
    // REAL-TIME POLYPHONIC PCM SYNTHESIS ENGINE
    // -------------------------------------------------------------
    private fun startSynthesizerEngine() {
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSizeInBytes = maxOf(minBuf, BUFFER_SIZE * 4)

            synthAudioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
                bufferSizeInBytes,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            synthAudioTrack?.play()

            synthJob = coroutineScope.launch(Dispatchers.Default) {
                val pcmBuffer = ShortArray(BUFFER_SIZE * 2)
                val twoPi = (2.0 * Math.PI).toFloat()
                val dt = 1.0f / SAMPLE_RATE.toFloat()

                while (isActive) {
                    val track = synthAudioTrack ?: break

                    val masterVol = masterVolume.coerceIn(0f, 1f)
                    val punchFactor = 1.0f + (masterPunch * 0.25f)

                    synchronized(voiceLock) {
                        for (i in 0 until BUFFER_SIZE) {
                            var sampleLeft = 0f
                            var sampleRight = 0f

                            for (v in voices) {
                                if (v.envStage == 0) continue

                                val ch = v.channel.coerceIn(0, 11)
                                val chParams = channelParams[ch]
                                if (!chParams.isEnabled) continue

                                // Envelope generator (ADSR)
                                when (v.envStage) {
                                    1 -> { // Attack
                                        v.envLevel += 0.008f
                                        if (v.envLevel >= 1.0f) {
                                            v.envLevel = 1.0f
                                            v.envStage = 2
                                        }
                                    }
                                    2 -> { // Decay
                                        val decayRate = when (v.instrumentType) {
                                            0 -> 0.00015f
                                            1 -> 0.00012f
                                            2 -> 0.00004f
                                            3 -> 0.00002f
                                            6 -> 0.00025f
                                            else -> 0.00018f
                                        }
                                        v.envLevel -= decayRate
                                        val sustainLevel = when (v.instrumentType) {
                                            2, 3 -> 0.85f
                                            0 -> 0.35f
                                            1 -> 0.45f
                                            else -> 0.50f
                                        }
                                        if (v.envLevel <= sustainLevel) {
                                            v.envLevel = sustainLevel
                                            v.envStage = 3
                                        }
                                    }
                                    3 -> { // Sustain
                                        if (v.instrumentType == 0 || v.instrumentType == 1 || v.instrumentType == 6) {
                                            v.envLevel -= 0.00004f
                                            if (v.envLevel <= 0f) {
                                                v.envLevel = 0f
                                                v.envStage = 0
                                            }
                                        }
                                    }
                                    4 -> { // Release
                                        v.envLevel -= 0.0025f
                                        if (v.envLevel <= 0f) {
                                            v.envLevel = 0f
                                            v.envStage = 0
                                        }
                                    }
                                }

                                if (v.envLevel <= 0f) continue

                                // Harmonic Synthesis by Instrument Type
                                val freq = v.targetFrequency * pitchBendFactor
                                v.phase = (v.phase + twoPi * freq * dt) % twoPi
                                v.phase2 = (v.phase2 + twoPi * freq * 2.01f * dt) % twoPi
                                v.phase3 = (v.phase3 + twoPi * freq * 3.005f * dt) % twoPi

                                val rawSample = when (v.instrumentType) {
                                    0 -> { // Grand Piano
                                        0.55f * sin(v.phase) +
                                        0.25f * sin(v.phase2) +
                                        0.12f * sin(v.phase3) +
                                        0.08f * sin((v.phase * 4f) % twoPi)
                                    }
                                    1 -> { // Rhodes / EP
                                        val mod = 0.45f * sin(v.phase2)
                                        0.70f * sin(v.phase + mod) + 0.30f * sin(v.phase3)
                                    }
                                    2 -> { // Strings
                                        val saw1 = 2.0f * (v.phase / twoPi) - 1.0f
                                        val saw2 = 2.0f * (v.phase2 / twoPi) - 1.0f
                                        0.50f * saw1 + 0.50f * saw2
                                    }
                                    3 -> { // Organ
                                        0.45f * sin(v.phase) +
                                        0.35f * sin(v.phase2) +
                                        0.20f * sin(v.phase3)
                                    }
                                    4 -> { // Brass
                                        val saw = 2.0f * (v.phase / twoPi) - 1.0f
                                        val saw2 = 2.0f * (v.phase2 / twoPi) - 1.0f
                                        0.60f * saw + 0.40f * saw2
                                    }
                                    5 -> { // Lead
                                        val sq = if (v.phase < Math.PI) 0.6f else -0.6f
                                        val saw = 2.0f * (v.phase / twoPi) - 1.0f
                                        0.60f * sq + 0.40f * saw
                                    }
                                    6 -> { // Bass / Percussive
                                        0.70f * sin(v.phase) + 0.30f * (2.0f * (v.phase / twoPi) - 1.0f)
                                    }
                                    else -> { // Pad
                                        0.50f * sin(v.phase) + 0.35f * sin(v.phase2) + 0.15f * sin(v.phase3)
                                    }
                                }

                                val voiceGain = rawSample * v.envLevel * v.velocity * chParams.volume
                                val pan = chParams.pan.coerceIn(-1f, 1f)
                                val leftGain = (1.0f - pan) * 0.5f
                                val rightGain = (1.0f + pan) * 0.5f

                                sampleLeft += voiceGain * leftGain
                                sampleRight += voiceGain * rightGain
                            }

                            val outL = (sampleLeft * masterVol * punchFactor).coerceIn(-1.0f, 1.0f)
                            val outR = (sampleRight * masterVol * punchFactor).coerceIn(-1.0f, 1.0f)

                            pcmBuffer[i * 2] = (outL * 32767f).toInt().toShort()
                            pcmBuffer[i * 2 + 1] = (outR * 32767f).toInt().toShort()
                        }
                    }

                    track.write(pcmBuffer, 0, pcmBuffer.size)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing real-time synthesizer: ${e.message}")
        }
    }

    private fun midiToFreq(midiNote: Int): Float {
        return 440.0f * 2.0.pow((midiNote - 69).toDouble() / 12.0).toFloat()
    }

    private fun triggerVoiceNoteOn(channel: Int, midiNote: Int, velocity: Float) {
        synchronized(voiceLock) {
            val ch = channel.coerceIn(0, 11)
            val chParams = channelParams[ch]
            val transposedNote = (midiNote + chParams.transpose).coerceIn(0, 127)

            var targetVoice = voices.find { it.channel == ch && it.noteNumber == transposedNote && it.envStage != 0 }
            if (targetVoice == null) {
                targetVoice = voices.find { it.envStage == 0 }
            }
            if (targetVoice == null) {
                targetVoice = voices.minByOrNull { it.age } ?: voices[0]
            }

            targetVoice.apply {
                this.channel = ch
                this.noteNumber = transposedNote
                this.targetFrequency = midiToFreq(transposedNote)
                this.velocity = velocity.coerceIn(0.1f, 1.0f)
                this.instrumentType = chParams.instrumentType
                this.envLevel = 0.05f
                this.envStage = 1
                this.age = ++voiceCounter
            }
        }
    }

    private fun triggerVoiceNoteOff(channel: Int, midiNote: Int) {
        synchronized(voiceLock) {
            val ch = channel.coerceIn(0, 11)
            val chParams = channelParams[ch]
            val transposedNote = (midiNote + chParams.transpose).coerceIn(0, 127)

            for (v in voices) {
                if (v.channel == ch && v.noteNumber == transposedNote && v.envStage != 0 && v.envStage != 4) {
                    v.envStage = 4
                }
            }
        }
    }

    fun setChannelVolume(channel: Int, volume: Float) {
        val ch = channel.coerceIn(0, 11)
        channelParams[ch].volume = volume.coerceIn(0f, 1f)
        NativeAudioBridge.safeSetTrackVolume(ch, volume)
    }

    fun setChannelPan(channel: Int, pan: Float) {
        val ch = channel.coerceIn(0, 11)
        channelParams[ch].pan = pan.coerceIn(-1f, 1f)
        NativeAudioBridge.safeSetTrackPan(ch, pan)
    }

    fun setChannelInstrument(channel: Int, instrumentIndex: Int) {
        val ch = channel.coerceIn(0, 11)
        channelParams[ch].instrumentType = instrumentIndex % 8
    }

    fun setChannelTranspose(channel: Int, semitones: Int) {
        val ch = channel.coerceIn(0, 11)
        channelParams[ch].transpose = semitones
        NativeAudioBridge.safeSetTrackTranspose(ch, semitones)
    }

    fun setChannelEnabled(channel: Int, enabled: Boolean) {
        val ch = channel.coerceIn(0, 11)
        channelParams[ch].isEnabled = enabled
    }

    fun playDrumPadStrike(padIndex: Int, velocity: Float = 0.90f) {
        val midiDrumNote = when (padIndex) {
            1 -> 36
            2 -> 38
            3 -> 42
            4 -> 46
            5 -> 35
            6 -> 40
            7 -> 47
            8 -> 49
            else -> 36 + (padIndex % 12)
        }
        channelParams[8].instrumentType = 6
        triggerVoiceNoteOn(8, midiDrumNote, velocity)
        NativeAudioBridge.safeNoteOn(NativeAudioBridge.ENGINE_DRUM, padIndex - 1, midiDrumNote, (velocity * 127).toInt())
    }

    // -------------------------------------------------------------
    // ASYNCHRONOUS FIFO MIDI PROCESSOR (DISPATCHERS.DEFAULT)
    // -------------------------------------------------------------
    private fun startMidiEventProcessor() {
        coroutineScope.launch(Dispatchers.Default) {
            for (event in midiEventChannel) {
                try {
                    when (event) {
                        is MidiEngineEvent.NoteOn -> {
                            val note = event.noteNumber
                            val ch = event.channel.coerceIn(0, 7)
                            val vel = event.velocity

                            if (vel > 0) {
                                activeHeldNotes.add(note)
                                sustainedNotesToRelease.remove(note)
                                NativeAudioBridge.safeNoteOn(ch, note, vel)
                                withContext(Dispatchers.Main.immediate) {
                                    onMidiNoteOnListener?.invoke(midiNumberToNoteName(note), vel)
                                }
                            } else {
                                handleNoteOffInternal(ch, note)
                            }
                        }

                        is MidiEngineEvent.NoteOff -> {
                            val note = event.noteNumber
                            val ch = event.channel.coerceIn(0, 7)
                            handleNoteOffInternal(ch, note)
                        }

                        is MidiEngineEvent.ControlChange -> {
                            val ch = event.channel.coerceIn(0, 7)
                            if (event.controller == 64) { // Sustain Pedal (CC#64)
                                val pedalPressed = event.value >= 64
                                isSustainPedalDown = pedalPressed

                                if (!pedalPressed) {
                                    // Sustain pedal released: immediately flush and send NoteOff for all sustained notes!
                                    val notesToRelease = ArrayList(sustainedNotesToRelease)
                                    sustainedNotesToRelease.clear()

                                    for (note in notesToRelease) {
                                        if (!activeHeldNotes.contains(note)) {
                                            NativeAudioBridge.safeNoteOff(ch, note)
                                            withContext(Dispatchers.Main.immediate) {
                                                onMidiNoteOffListener?.invoke(midiNumberToNoteName(note))
                                            }
                                        }
                                    }
                                }

                                withContext(Dispatchers.Main.immediate) {
                                    onMidiSustainListener?.invoke(pedalPressed)
                                }
                            }
                        }

                        is MidiEngineEvent.PitchBend -> {
                            val ch = event.channel.coerceIn(0, 7)
                            NativeAudioBridge.safePitchBend(ch, event.bendValue)
                            val normalized = (event.bendValue - 8192) / 8192f
                            withContext(Dispatchers.Main.immediate) {
                                onMidiPitchBendListener?.invoke(normalized)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in MIDI FIFO processor: ${e.message}")
                }
            }
        }
    }

    private suspend fun handleNoteOffInternal(channel: Int, note: Int) {
        activeHeldNotes.remove(note)
        if (isSustainPedalDown) {
            sustainedNotesToRelease.add(note)
        } else {
            triggerVoiceNoteOff(channel, note)
            NativeAudioBridge.safeNoteOff(channel, note)
            withContext(Dispatchers.Main.immediate) {
                onMidiNoteOffListener?.invoke(midiNumberToNoteName(note))
            }
        }
    }

    // Direct Note triggering from Virtual Piano or Pad
    fun noteOn(noteName: String, velocity: Float = 0.85f, channel: Int = activeTargetChannel) {
        val midiNote = noteNameToMidi(noteName)
        val velInt = (velocity * 127f).toInt().coerceIn(1, 127)
        triggerVoiceNoteOn(channel.coerceIn(0, 11), midiNote, velocity)
        NativeAudioBridge.safeNoteOn(channel.coerceIn(0, 7), midiNote, velInt)
    }

    fun noteOff(noteName: String, channel: Int = activeTargetChannel) {
        val midiNote = noteNameToMidi(noteName)
        triggerVoiceNoteOff(channel.coerceIn(0, 11), midiNote)
        NativeAudioBridge.safeNoteOff(channel.coerceIn(0, 7), midiNote)
    }

    fun setPitchBend(bend: Float, channel: Int = activeTargetChannel) {
        pitchBendFactor = (2.0.pow((bend.coerceIn(-1f, 1f) * 2.0) / 12.0)).toFloat()
        val midiBend = ((bend + 1.0f) * 8191.5f).toInt().coerceIn(0, 16383)
        NativeAudioBridge.safePitchBend(channel.coerceIn(0, 7), midiBend)
    }

    fun allNotesOff() {
        activeHeldNotes.clear()
        sustainedNotesToRelease.clear()
        synchronized(voiceLock) {
            for (v in voices) {
                v.envStage = 0
                v.envLevel = 0f
            }
        }
        for (ch in 0..7) {
            NativeAudioBridge.safeAllNotesOff(ch)
        }
    }

    // -------------------------------------------------------------
    // USB MIDI HARDWARE DETECTION & MIDI RECEIVER
    // -------------------------------------------------------------
    private fun initUsbMidi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                midiHandlerThread = HandlerThread("UsbMidiWorkerThread").apply { start() }
                midiHandler = Handler(midiHandlerThread!!.looper)

                midiManager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
                midiManager?.registerDeviceCallback(object : MidiManager.DeviceCallback() {
                    override fun onDeviceAdded(device: MidiDeviceInfo?) {
                        device?.let { connectMidiDevice(it) }
                        refreshMidiDevicesList()
                    }

                    override fun onDeviceRemoved(device: MidiDeviceInfo?) {
                        refreshMidiDevicesList()
                    }
                }, midiHandler)

                // Connect to currently attached USB MIDI devices
                val devices = midiManager?.devices ?: emptyArray()
                for (dev in devices) {
                    connectMidiDevice(dev)
                }
                refreshMidiDevicesList()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing USB MIDI: ${e.message}")
            }
        }
    }

    private fun connectMidiDevice(info: MidiDeviceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                midiManager?.openDevice(info, { device: MidiDevice? ->
                    if (device != null) {
                        openDevices.add(device)
                        val outputPort = device.openOutputPort(0)
                        outputPort?.connect(object : MidiReceiver() {
                            override fun onSend(msg: ByteArray?, offset: Int, count: Int, timestamp: Long) {
                                if (msg == null || count < 2) return
                                val status = (msg[offset].toInt() and 0xFF)
                                val command = status and 0xF0
                                val channel = status and 0x0F
                                val data1 = if (count > 1) (msg[offset + 1].toInt() and 0x7F) else 0
                                val data2 = if (count > 2) (msg[offset + 2].toInt() and 0x7F) else 0

                                when (command) {
                                    0x90 -> { // Note On (velocity == 0 is treated as Note Off)
                                        if (data2 <= 0) {
                                            midiEventChannel.trySend(
                                                MidiEngineEvent.NoteOff(channel, data1)
                                            )
                                        } else {
                                            midiEventChannel.trySend(
                                                MidiEngineEvent.NoteOn(channel, data1, data2)
                                            )
                                        }
                                    }
                                    0x80 -> { // Note Off
                                        midiEventChannel.trySend(
                                            MidiEngineEvent.NoteOff(channel, data1)
                                        )
                                    }
                                    0xE0 -> { // Pitch Bend
                                        val bendVal = ((data2 shl 7) or data1)
                                        midiEventChannel.trySend(
                                            MidiEngineEvent.PitchBend(channel, bendVal)
                                        )
                                    }
                                    0xB0 -> { // Control Change (CC)
                                        midiEventChannel.trySend(
                                            MidiEngineEvent.ControlChange(channel, data1, data2)
                                        )
                                    }
                                }
                            }
                        })
                    }
                }, midiHandler)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening MIDI device: ${e.message}")
            }
        }
    }

    fun getConnectedUsbMidiDevices(): List<MidiDeviceItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyList()
        val manager = midiManager ?: return emptyList()
        val devices = manager.devices
        if (devices.isEmpty()) return emptyList()

        return devices.map { devInfo ->
            val props = devInfo.properties
            val manufacturer = props.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER) ?: ""
            val product = props.getString(MidiDeviceInfo.PROPERTY_PRODUCT) ?: ""
            val name = props.getString(MidiDeviceInfo.PROPERTY_NAME) ?: ""

            val displayName = when {
                manufacturer.isNotEmpty() && product.isNotEmpty() -> "$manufacturer $product"
                product.isNotEmpty() -> product
                name.isNotEmpty() -> name
                else -> "Périphérique USB MIDI (#${devInfo.id})"
            }

            val type = if (devInfo.type == MidiDeviceInfo.TYPE_USB) "USB MIDI" else "MIDI"

            MidiDeviceItem(
                id = "${devInfo.id}",
                name = displayName,
                type = type,
                isConnected = true,
                isEnabled = true
            )
        }
    }

    private fun refreshMidiDevicesList() {
        val list = getConnectedUsbMidiDevices()
        coroutineScope.launch(Dispatchers.Main) {
            onDeviceListChanged?.invoke(list)
        }
    }

    // -------------------------------------------------------------
    // METRONOME AUDIO GENERATOR
    // -------------------------------------------------------------
    fun startMetronome(bpm: Int, timeSignature: String, volume: Float) {
        stopMetronome()
        val beatsPerBar = when (timeSignature) {
            "2/4" -> 2
            "3/4" -> 3
            "6/8" -> 6
            else -> 4
        }
        val intervalMs = (60000L / bpm.coerceIn(20, 300))

        try {
            val minBuf = AudioTrack.getMinBufferSize(
                44100,
                AudioFormat.CHANNEL_OUT_MONO,
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
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf, 2048))
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            metronomeAudioTrack?.play()
            isMetronomeActive = true

            metronomeJob = coroutineScope.launch(Dispatchers.Default) {
                var currentBeat = 0
                while (isActive && isMetronomeActive) {
                    val pcmData = if (currentBeat == 0) hiClickPcm else loClickPcm
                    val scaledPcm = ShortArray(pcmData.size)
                    val gain = volume.coerceIn(0f, 1f)
                    for (i in pcmData.indices) {
                        scaledPcm[i] = (pcmData[i] * gain).toInt().toShort()
                    }
                    metronomeAudioTrack?.write(scaledPcm, 0, scaledPcm.size)
                    currentBeat = (currentBeat + 1) % beatsPerBar
                    delay(intervalMs)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting metronome: ${e.message}")
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

    private fun generateClickSound(freq: Double, durationMs: Int, accent: Boolean): ShortArray {
        val sampleRate = 44100
        val sampleCount = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(sampleCount)
        val maxAmp = if (accent) 31000.0 else 22000.0

        for (i in 0 until sampleCount) {
            val t = i.toDouble() / sampleRate
            val decay = exp(-t * (if (accent) 85.0 else 115.0))
            val sample = sin(2.0 * PI * freq * t) * decay * maxAmp
            buffer[i] = sample.toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    // -------------------------------------------------------------
    // SOUNDPOOL DRUM PADS & SAMPLE PLAYBACK
    // -------------------------------------------------------------
    private fun initSoundPool() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(16)
            .setAudioAttributes(attributes)
            .build()
    }

    fun playDrumPadSound(drumPad: DrumPadItem, volume: Float = 0.75f) {
        if (drumPad.soundType == DrumSoundType.SF2_NOTE) {
            noteOn(drumPad.sf2Note, volume)
            coroutineScope.launch {
                delay(180)
                noteOff(drumPad.sf2Note)
            }
        } else {
            val sampleFile = File(context.filesDir, "LiveKeys/DrumPad/${drumPad.sampleFileName}")
            val soundId = loadedSampleIds[drumPad.sampleFileName] ?: run {
                if (sampleFile.exists()) {
                    val id = soundPool?.load(sampleFile.absolutePath, 1) ?: 0
                    if (id > 0) loadedSampleIds[drumPad.sampleFileName] = id
                    id
                } else 0
            }

            if (soundId > 0) {
                soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
            }
        }
    }

    // -------------------------------------------------------------
    // MIDI (.MID) FILE PLAYER
    // -------------------------------------------------------------
    fun playMidiFile(filePath: String, isLooping: Boolean = false, volume: Float = 0.80f): Boolean {
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
            Log.e(TAG, "Error playing MIDI file: ${e.message}")
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
            Log.e(TAG, "Error playing loop: ${e.message}")
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
    // HELPERS
    // -------------------------------------------------------------
    private fun noteNameToMidi(noteName: String): Int {
        val clean = noteName.trim().uppercase()
        val regex = Regex("([A-G]#?)(-?\\d+)")
        val match = regex.find(clean) ?: return 60

        val note = match.groupValues[1]
        val octave = match.groupValues[2].toIntOrNull() ?: 4

        val noteOffsets = mapOf(
            "C" to 0, "C#" to 1, "D" to 2, "D#" to 3, "E" to 4,
            "F" to 5, "F#" to 6, "G" to 7, "G#" to 8, "A" to 9, "A#" to 10, "B" to 11
        )
        val offset = noteOffsets[note] ?: 0
        return ((octave + 1) * 12 + offset).coerceIn(0, 127)
    }

    private fun midiNumberToNoteName(midiNumber: Int): String {
        val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val octave = (midiNumber / 12) - 1
        val note = noteNames[midiNumber % 12]
        return "$note$octave"
    }

    fun release() {
        allNotesOff()
        stopMetronome()
        stopLoopPlayer()
        stopMidiPlayer()
        soundPool?.release()
        for (dev in openDevices) {
            try { dev.close() } catch (_: Exception) {}
        }
        openDevices.clear()
        try {
            midiHandlerThread?.quitSafely()
        } catch (_: Exception) {}
        coroutineScope.cancel()
    }
}
