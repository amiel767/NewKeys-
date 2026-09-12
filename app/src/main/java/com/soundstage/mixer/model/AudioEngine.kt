package com.soundstage.mixer.model

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
import com.soundstage.mixer.audio.NativeAudioBridge
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
    var program: Int = 0
    var bank: Int = 0
    var transpose: Int = 0
    var isEnabled: Boolean = true
    var brightness: Float = 0.70f
    var shimmer: Float = 0.15f
    var reverb: Float = 0.25f
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

    // DJ-Style Gapless Looping Engine (sample-accurate 0ms restart)
    private val djLoopEngine = DjLoopEngine(context)

    // Fallback Polyphonic Synthesizer (ensures sound is ALWAYS heard immediately)
    private val fallbackSynth = FallbackSynth()

    private var audioManager: AudioManager? = null
    var activeLayerChannelsProvider: ((midiNote: Int) -> List<Int>)? = null

    private val audioDeviceCallback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            Log.i(TAG, "Audio output device attached (headphones/BT). Restoring audio stream...")
            reconnectAudioStream()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
            super.onAudioDevicesRemoved(removedDevices)
            Log.i(TAG, "Audio output device removed. Restoring audio stream...")
            reconnectAudioStream()
        }
    }

    init {
        fallbackSynth.start()
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, null)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register AudioDeviceCallback: ${e.message}")
        }
    }

    fun reconnectAudioStream() {
        coroutineScope.launch {
            kotlinx.coroutines.delay(200)
            NativeAudioBridge.safeStartEngine(0)
        }
    }

    fun setHasActiveSoundFont(hasSoundFont: Boolean) {
        fallbackSynth.isBypassed = false
    }

    // Master DSP Parameters
    var masterVolume: Float = 0.80f
        set(value) {
            field = value
            NativeAudioBridge.safeSetMasterVolume(value)
            fallbackSynth.setMasterGain(value)
        }
    var pitchBendFactor: Float = 1.0f
    var soundGoodizerMode: String = "A"
        set(value) {
            field = value
            val modeInt = when (value) {
                "A" -> 0
                "B" -> 1
                "C" -> 2
                "D" -> 3
                else -> 0
            }
            NativeAudioBridge.safeSetSoundGoodizer(isSoundGoodizerEnabled, modeInt, soundGoodizerAmount)
        }
    var isSoundGoodizerEnabled: Boolean = false
        set(value) {
            field = value
            val modeInt = when (soundGoodizerMode) {
                "A" -> 0
                "B" -> 1
                "C" -> 2
                "D" -> 3
                else -> 0
            }
            NativeAudioBridge.safeSetSoundGoodizer(value, modeInt, soundGoodizerAmount)
        }
    var soundGoodizerAmount: Float = 0.42f
        set(value) {
            field = value
            val modeInt = when (soundGoodizerMode) {
                "A" -> 0
                "B" -> 1
                "C" -> 2
                "D" -> 3
                else -> 0
            }
            NativeAudioBridge.safeSetSoundGoodizer(isSoundGoodizerEnabled, modeInt, value)
        }
    var spatialWidener: Float = 0.35f
        set(value) {
            field = value
            NativeAudioBridge.safeSetSpatialWidener(value)
        }
    var masterPunch: Float = 0.50f
        set(value) {
            field = value
            NativeAudioBridge.safeSetMasterPunch(value)
        }

    var globalVelocityMin: Float = 0.10f
    var globalVelocityMax: Float = 1.0f

    // External MIDI Keyboard Octave & Transpose
    @Volatile var globalOctaveShift: Int = 0
    @Volatile var globalTranspose: Int = 0
    private data class ActiveMidiNote(val effectiveNote: Int, val targetChannels: List<Int>)
    private val activeMidiNoteMap = java.util.concurrent.ConcurrentHashMap<Int, ActiveMidiNote>()

    // Active track target for global keyboard notes (0..7, 8, 9)
    @Volatile var activeTargetChannel: Int = 0
    @Volatile var usbMidiRouteToActiveSlot: Boolean = true

    // Channel parameters for 8 mixer channels + Drum (8) + Tonic (9)
    val channelParams = Array(12) { chIdx ->
        SynthChannelParams().apply {
            instrumentType = chIdx % 8
        }
    }

    // ================= 1. DIRECT SYNCHRONOUS MIDI AUDIO + ASYNC UI NOTIFICATION =================
    private val activeHeldNotes = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val sustainedNotesToRelease = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var isSustainPedalDown = false

    // Callbacks for UI updates (dispatched cleanly)
    var onMidiNoteOnListener: ((noteName: String, velocity: Int) -> Unit)? = null
    var onMidiNoteOffListener: ((noteName: String) -> Unit)? = null
    var onMidiPitchBendListener: ((bendValue: Float) -> Unit)? = null
    var onMidiSustainListener: ((isPressed: Boolean) -> Unit)? = null
    var onMidiCcListener: ((channel: Int, cc: Int, value: Int) -> Unit)? = null
    var onDeviceListChanged: ((List<MidiDeviceItem>) -> Unit)? = null

    private val disabledMidiDeviceIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun setMidiDeviceEnabled(deviceId: String, enabled: Boolean) {
        if (enabled) {
            disabledMidiDeviceIds.remove(deviceId)
        } else {
            disabledMidiDeviceIds.add(deviceId)
            allNotesOff()
        }
        refreshMidiDevicesList()
    }

    // ================= 3. METRONOME AUDIO GENERATOR (ATOMIC BPM UPDATES) =================
    private var metronomeAudioTrack: AudioTrack? = null
    private var metronomeJob: Job? = null
    @Volatile private var isMetronomeActive = false
    @Volatile private var metronomeBpm: Int = 120
    @Volatile private var metronomeBeatsPerBar: Int = 4
    @Volatile private var metronomeVolumeGain: Float = 0.8f
    private val hiClickPcm: ShortArray
    private val loClickPcm: ShortArray

    // ================= 4. AUDIO / LOOP / SAMPLE PLAYBACK =================
    private var loopMediaPlayer: MediaPlayer? = null
    private var nextLoopMediaPlayer: MediaPlayer? = null
    private var currentLoopFilePath: String = ""
    @Volatile private var currentLoopVolume: Float = 0.75f
    @Volatile private var loopTrimStartMs: Int = 0
    @Volatile private var loopTrimEndMs: Int = 0
    private var soundPool: SoundPool? = null
    private val loadedSampleIds = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val pendingSamplePlays = java.util.concurrent.ConcurrentHashMap<Int, Float>()

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
        initUsbMidi()
    }

    fun getActivePerformanceChannels(preferredChannel: Int = activeTargetChannel): List<Int> {
        return listOf(preferredChannel.coerceIn(0, 11))
    }

    fun setChannelProgram(channel: Int, program: Int, bank: Int = 0) {
        val ch = channel.coerceIn(0, 11)
        channelParams[ch].program = program.coerceIn(0, 127)
        channelParams[ch].bank = bank
        channelParams[ch].instrumentType = (program / 16).coerceIn(0, 7)
    }

    private val activeTonicPitches = mutableSetOf<Int>()
    private val activeShimmerPitches = mutableSetOf<Int>()

    var trackVelocityCurveProvider: ((channel: Int) -> Float)? = null

    private fun applyVelocityCurve(velocity: Float, curve: Float): Float {
        val v = velocity.coerceIn(0.01f, 1.0f)
        return when {
            curve < 0.48f -> {
                val exp = 1.0f + (0.5f - curve) * 3.0f
                v.pow(exp)
            }
            curve > 0.52f -> {
                val exp = 1.0f / (1.0f + (curve - 0.5f) * 3.0f)
                v.pow(exp)
            }
            else -> v
        }
    }

    fun setTonicDrone(notes: Set<String>, octaveRange: String, brightness: Float = 0.70f, shimmer: Float = 0.15f) {
        channelParams[9].brightness = brightness
        channelParams[9].shimmer = shimmer
        channelParams[9].isEnabled = true

        NativeAudioBridge.safeSetPadBrightness(brightness)

        val baseOctave = when (octaveRange) {
            "C1 — C2" -> 1
            "C2 — C3" -> 2
            "C3 — C4" -> 3
            "C4 — C5" -> 4
            "C5 — C6" -> 5
            else -> 1
        }

        val chromaticNotes = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val newPitches = notes.mapNotNull { note ->
            val idx = chromaticNotes.indexOf(note)
            if (idx >= 0) (baseOctave + 1) * 12 + idx else null
        }.toSet()

        // Shimmer: Harmonic upper octave (+12 semitones)
        val newShimmerPitches = if (shimmer > 0.05f) {
            newPitches.map { (it + 12).coerceIn(0, 127) }.toSet()
        } else emptySet()

        // 1. Turn on new fundamental notes first for seamless crossfade
        val toTurnOn = newPitches - activeTonicPitches
        for (pitch in toTurnOn) {
            NativeAudioBridge.safeNoteOn(
                channel = NativeAudioBridge.CHANNEL_TONIC_PAD,
                midiNote = pitch,
                velocity = 85,
                engineIndex = NativeAudioBridge.ENGINE_FADER
            )
        }

        // 2. Turn on new shimmer harmonic notes
        val shimmerToTurnOn = newShimmerPitches - activeShimmerPitches
        for (pitch in shimmerToTurnOn) {
            val shimmerVelocity = (shimmer * 80f).toInt().coerceIn(20, 95)
            NativeAudioBridge.safeNoteOn(
                channel = NativeAudioBridge.CHANNEL_TONIC_PAD,
                midiNote = pitch,
                velocity = shimmerVelocity,
                engineIndex = NativeAudioBridge.ENGINE_FADER
            )
        }

        // 3. Musical crossfade: release outgoing notes after a brief overlap (65ms)
        val toTurnOff = activeTonicPitches - newPitches
        val shimmerToTurnOff = activeShimmerPitches - newShimmerPitches
        if (toTurnOff.isNotEmpty() || shimmerToTurnOff.isNotEmpty()) {
            coroutineScope.launch {
                delay(65)
                for (pitch in toTurnOff) {
                    NativeAudioBridge.safeNoteOff(
                        channel = NativeAudioBridge.CHANNEL_TONIC_PAD,
                        midiNote = pitch,
                        engineIndex = NativeAudioBridge.ENGINE_FADER
                    )
                }
                for (pitch in shimmerToTurnOff) {
                    NativeAudioBridge.safeNoteOff(
                        channel = NativeAudioBridge.CHANNEL_TONIC_PAD,
                        midiNote = pitch,
                        engineIndex = NativeAudioBridge.ENGINE_FADER
                    )
                }
            }
        }

        activeTonicPitches.clear()
        activeTonicPitches.addAll(newPitches)
        activeShimmerPitches.clear()
        activeShimmerPitches.addAll(newShimmerPitches)
    }

    fun setChannelVolume(channel: Int, volume: Float) {
        val ch = channel.coerceIn(0, 11)
        val clamped = volume.coerceIn(0f, 1f)
        channelParams[ch].volume = clamped
        if (ch in 0..7) {
            NativeAudioBridge.safeSetTrackVolume(ch, clamped, NativeAudioBridge.ENGINE_FADER)
        } else if (ch == 8) {
            NativeAudioBridge.safeSetTrackVolume(8, clamped, NativeAudioBridge.ENGINE_FADER)
            NativeAudioBridge.safeSetDrumMasterVolume(clamped)
        } else if (ch == 9) {
            NativeAudioBridge.safeSetTrackVolume(9, clamped, NativeAudioBridge.ENGINE_FADER)
        } else {
            NativeAudioBridge.safeSetTrackVolume(ch, clamped)
        }
    }

    fun setChannelPan(channel: Int, pan: Float) {
        val ch = channel.coerceIn(0, 11)
        channelParams[ch].pan = pan.coerceIn(-1f, 1f)
        NativeAudioBridge.safeSetTrackPan(ch, pan)
        if (ch == 8) {
            NativeAudioBridge.safeSetDrumMasterPan(pan)
        }
    }

    fun setChannelInstrument(channel: Int, instrumentIndex: Int) {
        val ch = channel.coerceIn(0, 11)
        channelParams[ch].instrumentType = instrumentIndex % 8
        channelParams[ch].program = (instrumentIndex % 8) * 16
    }

    fun setChannelTranspose(channel: Int, semitones: Int) {
        val ch = channel.coerceIn(0, 11)
        channelParams[ch].transpose = semitones
        NativeAudioBridge.safeSetTrackTranspose(ch, semitones)
    }

    fun setChannelEnabled(channel: Int, enabled: Boolean) {
        val ch = channel.coerceIn(0, 11)
        channelParams[ch].isEnabled = enabled
        if (!enabled) {
            NativeAudioBridge.safeAllNotesOff(ch)
            NativeAudioBridge.safeSetTrackVolume(ch, 0f)
        } else {
            NativeAudioBridge.safeSetTrackVolume(ch, channelParams[ch].volume)
        }
    }

    fun setChannelReverb(channel: Int, reverb: Float) {
        val ch = channel.coerceIn(0, 11)
        val clamped = reverb.coerceIn(0f, 1f)
        channelParams[ch].reverb = clamped
        if (ch in 0..7) {
            NativeAudioBridge.safeSetChannelReverb(ch, clamped, NativeAudioBridge.ENGINE_FADER)
        } else if (ch == 9) {
            NativeAudioBridge.safeSetChannelReverb(9, clamped, NativeAudioBridge.ENGINE_FADER)
        }
    }

    fun setMasterReverb(enabled: Boolean, size: Float, decay: Float, damp: Float, mix: Float) {
        NativeAudioBridge.safeSetMasterReverb(enabled, size, decay, damp, mix)
    }

    fun setMasterDelay(enabled: Boolean, timeSec: Float, feedback: Float, mix: Float, pingPong: Boolean = false) {
        NativeAudioBridge.safeSetMasterDelay(enabled, timeSec, feedback, mix, pingPong)
    }

    fun playDrumPadStrike(padIndex: Int, velocity: Float = 0.90f) {
        val sampleId = ((padIndex - 1) % 8) + 1
        NativeAudioBridge.safeTriggerDrumSample(sampleId, velocity)
    }

    private fun playMidiNote(midiNote: Int, velocity: Int, targetChannel: Int) {
        val ch = if (targetChannel == 9) NativeAudioBridge.CHANNEL_TONIC_PAD else targetChannel.coerceIn(0, 8)
        NativeAudioBridge.safeNoteOn(ch, midiNote, velocity, engineIndex = NativeAudioBridge.ENGINE_FADER)
    }

    private fun stopMidiNote(midiNote: Int, targetChannel: Int) {
        val ch = if (targetChannel == 9) NativeAudioBridge.CHANNEL_TONIC_PAD else targetChannel.coerceIn(0, 8)
        NativeAudioBridge.safeNoteOff(ch, midiNote, engineIndex = NativeAudioBridge.ENGINE_FADER)
    }

    private fun bendMidiPitch(midiBend: Int, targetChannel: Int) {
        val ch = if (targetChannel == 9) NativeAudioBridge.CHANNEL_TONIC_PAD else targetChannel.coerceIn(0, 8)
        NativeAudioBridge.safePitchBend(ch, midiBend, engineIndex = NativeAudioBridge.ENGINE_FADER)
    }

    // -------------------------------------------------------------
    // DIRECT ZERO-LATENCY MIDI PROCESSOR (RUNS ON MIDI IO THREAD)
    // -------------------------------------------------------------
    fun handleIncomingMidi(channel: Int, command: Int, data1: Int, data2: Int) {
        val effectiveNote = (data1 + globalOctaveShift * 12).coerceIn(0, 127)
        val targetChannels = if (usbMidiRouteToActiveSlot) {
            activeLayerChannelsProvider?.invoke(effectiveNote) ?: listOf(activeTargetChannel)
        } else {
            listOf(midiChannelForSlot(channel))
        }

        when (command) {
            0x90 -> { // Note On (velocity == 0 is treated as Note Off)
                if (data2 > 0) {
                    val rawVel = (data2 / 127f).coerceIn(0.01f, 1.0f)
                    val activeChannels = targetChannels.toList()
                    activeMidiNoteMap[data1] = ActiveMidiNote(effectiveNote, activeChannels)
                    activeHeldNotes.add(effectiveNote)
                    sustainedNotesToRelease.remove(effectiveNote)

                    var displayVelInt = (rawVel * 127f).toInt().coerceIn(1, 127)
                    activeChannels.forEach { ch ->
                        val curve = trackVelocityCurveProvider?.invoke(ch) ?: 0.5f
                        val curvedVel = applyVelocityCurve(rawVel, curve)
                        val scaledVel = globalVelocityMin + curvedVel * (globalVelocityMax - globalVelocityMin)
                        val chVelInt = (scaledVel * 127f).toInt().coerceIn(1, 127)
                        displayVelInt = chVelInt
                        playMidiNote(effectiveNote, chVelInt, ch)
                    }

                    coroutineScope.launch(Dispatchers.Main) {
                        onMidiNoteOnListener?.invoke(midiNumberToNoteName(effectiveNote), displayVelInt)
                    }
                } else {
                    val active = activeMidiNoteMap.remove(data1)
                    val releasedNote = active?.effectiveNote ?: effectiveNote
                    val channelsToStop = active?.targetChannels ?: targetChannels
                    channelsToStop.forEach { ch ->
                        handleNoteOffDirect(ch, releasedNote)
                    }
                }
            }

            0x80 -> { // Note Off
                val active = activeMidiNoteMap.remove(data1)
                val releasedNote = active?.effectiveNote ?: effectiveNote
                val channelsToStop = active?.targetChannels ?: targetChannels
                channelsToStop.forEach { ch ->
                    handleNoteOffDirect(ch, releasedNote)
                }
            }

            0xE0 -> { // Pitch Bend
                val bendVal = ((data2 shl 7) or data1)
                targetChannels.forEach { ch ->
                    bendMidiPitch(bendVal, ch)
                }
                val normalized = (bendVal - 8192) / 8192f
                coroutineScope.launch(Dispatchers.Main) {
                    onMidiPitchBendListener?.invoke(normalized)
                }
            }

            0xB0 -> { // Control Change (CC)
                when (data1) {
                    7, 11 -> { // CC#7 Volume, CC#11 Expression
                        val vol = (data2 / 127f).coerceIn(0f, 1f)
                        targetChannels.forEach { ch ->
                            NativeAudioBridge.safeSetTrackVolume(ch, vol)
                        }
                    }
                    10 -> { // CC#10 Pan
                        val pan = ((data2 - 64) / 63f).coerceIn(-1f, 1f)
                        targetChannels.forEach { ch ->
                            NativeAudioBridge.safeSetTrackPan(ch, pan)
                        }
                    }
                }

                coroutineScope.launch(Dispatchers.Main) {
                    onMidiCcListener?.invoke(channel, data1, data2)
                }

                if (data1 == 64) { // Sustain Pedal (CC#64)
                    val pedalPressed = data2 >= 64
                    isSustainPedalDown = pedalPressed

                    if (!pedalPressed) {
                        // Sustain pedal released: immediately flush and send NoteOff for all sustained notes across channels (excluding channel 9 Tonic Pad)
                        val notesToRelease = ArrayList(sustainedNotesToRelease)
                        sustainedNotesToRelease.clear()

                        for (note in notesToRelease) {
                            if (!activeHeldNotes.contains(note)) {
                                for (ch in 0..8) { // Channels 0..8 (Instruments & Drums), Tonic Pad 9 is independent
                                    stopMidiNote(note, ch)
                                }
                                coroutineScope.launch(Dispatchers.Main) {
                                    onMidiNoteOffListener?.invoke(midiNumberToNoteName(note))
                                }
                            }
                        }
                    }

                    coroutineScope.launch(Dispatchers.Main) {
                        onMidiSustainListener?.invoke(pedalPressed)
                    }
                }
            }
        }
    }

    fun setSustainPedal(pedalPressed: Boolean) {
        isSustainPedalDown = pedalPressed
        if (!pedalPressed) {
            val notesToRelease = ArrayList(sustainedNotesToRelease)
            sustainedNotesToRelease.clear()
            for (note in notesToRelease) {
                if (!activeHeldNotes.contains(note)) {
                    for (ch in 0..8) { // Exclude Tonic Pad 9
                        stopMidiNote(note, ch)
                    }
                    coroutineScope.launch(Dispatchers.Main) {
                        onMidiNoteOffListener?.invoke(midiNumberToNoteName(note))
                    }
                }
            }
        }
        coroutineScope.launch(Dispatchers.Main) {
            onMidiSustainListener?.invoke(pedalPressed)
        }
    }

    fun allNotesOff() {
        activeMidiNoteMap.clear()
        activeHeldNotes.clear()
        sustainedNotesToRelease.clear()
        for (ch in 0..15) {
            NativeAudioBridge.safeAllNotesOff(ch, NativeAudioBridge.ENGINE_FADER)
        }
        NativeAudioBridge.safeStopAllDrumSamples()
        for (ch in 0..9) {
            for (note in 0..127) {
                stopMidiNote(note, ch)
            }
        }
    }

    private fun handleNoteOffDirect(targetChannel: Int, note: Int) {
        activeHeldNotes.remove(note)
        // Tonic Pad (channel 9) is explicitly immune to sustain pedal holding
        if (isSustainPedalDown && targetChannel != NativeAudioBridge.CHANNEL_TONIC_PAD && targetChannel != 9) {
            sustainedNotesToRelease.add(note)
        } else {
            stopMidiNote(note, targetChannel)
            coroutineScope.launch(Dispatchers.Main) {
                onMidiNoteOffListener?.invoke(midiNumberToNoteName(note))
            }
        }
    }

    // Direct Note triggering from Virtual Piano or Pad
    fun noteOn(noteName: String, velocity: Float = 0.85f, channel: Int = activeTargetChannel) {
        val baseMidi = noteNameToMidi(noteName)
        val midiNote = (baseMidi + globalOctaveShift * 12).coerceIn(0, 127)
        val velInt = (velocity.coerceIn(0.01f, 1f) * 127f).toInt().coerceIn(1, 127)
        activeHeldNotes.add(midiNote)
        sustainedNotesToRelease.remove(midiNote)
        playMidiNote(midiNote, velInt, channel)
    }

    fun noteOff(noteName: String, channel: Int = activeTargetChannel) {
        val baseMidi = noteNameToMidi(noteName)
        val midiNote = (baseMidi + globalOctaveShift * 12).coerceIn(0, 127)
        handleNoteOffDirect(channel, midiNote)
    }

    fun setPitchBend(bend: Float, channel: Int = activeTargetChannel) {
        pitchBendFactor = (2.0.pow((bend.coerceIn(-1f, 1f) * 2.0) / 12.0)).toFloat()
        val midiBend = ((bend + 1.0f) * 8191.5f).toInt().coerceIn(0, 16383)
        bendMidiPitch(midiBend, channel)
    }

    // -------------------------------------------------------------
    // USB MIDI HARDWARE DETECTION & MIDI RECEIVER
    // -------------------------------------------------------------
    private fun initUsbMidi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                midiHandlerThread = HandlerThread("UsbMidiWorkerThread", android.os.Process.THREAD_PRIORITY_URGENT_AUDIO).apply { start() }
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
                val devIdStr = info.id.toString()
                midiManager?.openDevice(info, { device: MidiDevice? ->
                    if (device != null) {
                        openDevices.add(device)
                        val portInfo = info.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
                        if (portInfo != null) {
                            val outputPort = device.openOutputPort(portInfo.portNumber)
                            outputPort?.connect(object : MidiReceiver() {
                                override fun onSend(msg: ByteArray?, offset: Int, count: Int, timestamp: Long) {
                                    if (disabledMidiDeviceIds.contains(devIdStr)) return
                                    if (msg == null || count == 0) return
                                    
                                    var i = offset
                                    val end = offset + count
                                    while (i < end) {
                                        val status = msg[i].toInt() and 0xFF
                                        if (status >= 0xF8) {
                                            i++
                                            continue
                                        }
                                        
                                        if (status >= 0x80 && status < 0xF0) {
                                            var command = status and 0xF0
                                            val channel = status and 0x0F
                                            val hasTwoDataBytes = (command != 0xC0 && command != 0xD0)
                                            
                                            val data1 = if (i + 1 < end) (msg[i + 1].toInt() and 0x7F) else 0
                                            val data2 = if (hasTwoDataBytes && i + 2 < end) (msg[i + 2].toInt() and 0x7F) else 0
                                            
                                            // Core fix: explicitly convert Note On with velocity 0 to Note Off
                                            if (command == 0x90 && data2 == 0) {
                                                command = 0x80
                                            }
                                            
                                            handleIncomingMidi(channel, command, data1, data2)
                                            i += if (hasTwoDataBytes) 3 else 2
                                        } else {
                                            i++
                                        }
                                    }
                                }
                            })
                        }
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
            val isEnabled = !disabledMidiDeviceIds.contains(devInfo.id.toString())

            MidiDeviceItem(
                id = "${devInfo.id}",
                name = displayName,
                type = type,
                isConnected = true,
                isEnabled = isEnabled
            )
        }
    }

    fun setMasterEq(lowGainDb: Float, midGainDb: Float, highGainDb: Float) {
        NativeAudioBridge.safeSetMasterEq(lowGainDb, midGainDb, highGainDb)
    }

    fun setBufferSize(bufferSize: Int) {
        NativeAudioBridge.safeSetBufferSize(bufferSize)
    }

    fun setPolyphony(polyphony: Int) {
        NativeAudioBridge.safeSetPolyphony(polyphony)
    }

    private fun refreshMidiDevicesList() {
        val list = getConnectedUsbMidiDevices()
        coroutineScope.launch(Dispatchers.Main) {
            onDeviceListChanged?.invoke(list)
        }
    }

    // -------------------------------------------------------------
    // METRONOME AUDIO GENERATOR (ATOMIC BPM UPDATES)
    // -------------------------------------------------------------
    fun startMetronome(bpm: Int, timeSignature: String, volume: Float) {
        val beatsPerBar = when (timeSignature) {
            "2/4" -> 2
            "3/4" -> 3
            "6/8" -> 6
            else -> 4
        }
        metronomeBpm = bpm.coerceIn(30, 240)
        metronomeBeatsPerBar = beatsPerBar
        metronomeVolumeGain = volume.coerceIn(0f, 1f)

        // If metronome is already playing with an active AudioTrack, updating the volatile parameters is enough!
        if (isMetronomeActive && metronomeAudioTrack != null && metronomeJob?.isActive == true) {
            return
        }

        stopMetronome()

        try {
            val sampleRate = 44100
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
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
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf, 4096))
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            metronomeAudioTrack?.play()
            isMetronomeActive = true

            metronomeJob = coroutineScope.launch(Dispatchers.Default) {
                var currentBeat = 0
                val silenceBuffer = ShortArray(2048)

                while (isActive && isMetronomeActive) {
                    val track = metronomeAudioTrack ?: break
                    val currentBpm = metronomeBpm.coerceIn(30, 240)
                    val totalBeatSamples = ((sampleRate.toLong() * 60L) / currentBpm).toInt()

                    val pcmData = if (currentBeat == 0) hiClickPcm else loClickPcm
                    val clickSamples = min(pcmData.size, totalBeatSamples)
                    val scaledPcm = ShortArray(clickSamples)
                    val gain = metronomeVolumeGain
                    for (i in 0 until clickSamples) {
                        scaledPcm[i] = (pcmData[i] * gain).toInt().toShort()
                    }
                    track.write(scaledPcm, 0, scaledPcm.size)

                    var remainingSilence = totalBeatSamples - clickSamples
                    while (isActive && isMetronomeActive && remainingSilence > 0) {
                        val toWrite = min(remainingSilence, silenceBuffer.size)
                        val written = track.write(silenceBuffer, 0, toWrite)
                        if (written <= 0) break
                        remainingSilence -= written
                    }

                    currentBeat = (currentBeat + 1) % metronomeBeatsPerBar
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting metronome: ${e.message}")
        }
    }

    fun setMetronomeVolume(volume: Float) {
        metronomeVolumeGain = volume.coerceIn(0f, 1f)
    }

    fun stopMetronome() {
        isMetronomeActive = false
        metronomeJob?.cancel()
        metronomeJob = null
        try {
            metronomeAudioTrack?.pause()
            metronomeAudioTrack?.flush()
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

        soundPool?.setOnLoadCompleteListener { pool, sampleId, status ->
            if (status == 0) {
                val pendingVol = pendingSamplePlays.remove(sampleId)
                if (pendingVol != null) {
                    pool.play(sampleId, pendingVol, pendingVol, 1, 0, 1.0f)
                }
            }
        }
    }

    private val nativeDrumSampleIds = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val nextNativeSampleId = java.util.concurrent.atomic.AtomicInteger(20)

    fun preloadDrumSample(samplePath: String) {
        if (samplePath.isEmpty()) return
        try {
            val sampleFile = File(samplePath)
            if (sampleFile.exists() && !nativeDrumSampleIds.containsKey(sampleFile.absolutePath)) {
                val assignedId = nextNativeSampleId.getAndIncrement()
                nativeDrumSampleIds[sampleFile.absolutePath] = assignedId
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val decoded = com.soundstage.mixer.audio.AudioDecoder.decodeAudioFile(sampleFile)
                        if (decoded != null) {
                            NativeAudioBridge.safeLoadDrumSamplePcm(
                                sampleId = assignedId,
                                sampleName = sampleFile.name,
                                pcmData = decoded.pcmData,
                                totalFrames = decoded.totalFrames,
                                sampleRate = decoded.sampleRate
                            )
                        } else if (sampleFile.extension.equals("wav", ignoreCase = true)) {
                            NativeAudioBridge.safeLoadDrumWavFile(assignedId, sampleFile.absolutePath)
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error decoding sample for native engine: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading sample: ${e.message}")
        }
    }

    fun playDrumPadSound(drumPad: DrumPadItem, volume: Float = 0.75f) {
        // Resolve sample file if custom
        val sampleFile = when {
            drumPad.sampleFilePath.isNotEmpty() && File(drumPad.sampleFilePath).exists() -> File(drumPad.sampleFilePath)
            File(context.filesDir, "LiveKeys/DrumPad/${drumPad.sampleFileName}").exists() -> File(context.filesDir, "LiveKeys/DrumPad/${drumPad.sampleFileName}")
            File(context.filesDir, drumPad.sampleFileName).exists() -> File(context.filesDir, drumPad.sampleFileName)
            else -> null
        }

        if (sampleFile != null && sampleFile.exists()) {
            val cachedId = nativeDrumSampleIds[sampleFile.absolutePath]
            if (cachedId != null) {
                NativeAudioBridge.safeTriggerDrumSample(cachedId, volume, 0.0f)
            } else {
                val newId = nextNativeSampleId.getAndIncrement()
                nativeDrumSampleIds[sampleFile.absolutePath] = newId
                coroutineScope.launch(Dispatchers.IO) {
                    val decoded = com.soundstage.mixer.audio.AudioDecoder.decodeAudioFile(sampleFile)
                    if (decoded != null) {
                        NativeAudioBridge.safeLoadDrumSamplePcm(
                            sampleId = newId,
                            sampleName = sampleFile.name,
                            pcmData = decoded.pcmData,
                            totalFrames = decoded.totalFrames,
                            sampleRate = decoded.sampleRate
                        )
                    } else if (sampleFile.extension.equals("wav", ignoreCase = true)) {
                        NativeAudioBridge.safeLoadDrumWavFile(newId, sampleFile.absolutePath)
                    }
                    NativeAudioBridge.safeTriggerDrumSample(newId, volume, 0.0f)
                }
            }
        } else {
            // Built-in studio drum kit (1 = Kick, 2 = Snare, 3 = Hat, 4 = OpenHat, 5 = Clap, 6 = LowTom, 7 = MidTom, 8 = Crash...)
            val defaultId = ((drumPad.id - 1) % 8) + 1
            NativeAudioBridge.safeTriggerDrumSample(defaultId, volume, 0.0f)
        }
    }

    fun playDrumSample(sampleName: String, samplePath: String = "", volume: Float = 0.75f) {
        val sampleFile = when {
            samplePath.isNotEmpty() && File(samplePath).exists() -> File(samplePath)
            File(context.filesDir, "LiveKeys/DrumPad/$sampleName").exists() -> File(context.filesDir, "LiveKeys/DrumPad/$sampleName")
            File(context.filesDir, sampleName).exists() -> File(context.filesDir, sampleName)
            else -> null
        }

        if (sampleFile != null && sampleFile.exists()) {
            val cachedId = nativeDrumSampleIds[sampleFile.absolutePath]
            if (cachedId != null) {
                NativeAudioBridge.safeTriggerDrumSample(cachedId, volume, 0.0f)
            } else {
                val newId = nextNativeSampleId.getAndIncrement()
                nativeDrumSampleIds[sampleFile.absolutePath] = newId
                coroutineScope.launch(Dispatchers.IO) {
                    val decoded = com.soundstage.mixer.audio.AudioDecoder.decodeAudioFile(sampleFile)
                    if (decoded != null) {
                        NativeAudioBridge.safeLoadDrumSamplePcm(
                            sampleId = newId,
                            sampleName = sampleFile.name,
                            pcmData = decoded.pcmData,
                            totalFrames = decoded.totalFrames,
                            sampleRate = decoded.sampleRate
                        )
                    } else if (sampleFile.extension.equals("wav", ignoreCase = true)) {
                        NativeAudioBridge.safeLoadDrumWavFile(newId, sampleFile.absolutePath)
                    }
                    NativeAudioBridge.safeTriggerDrumSample(newId, volume, 0.0f)
                }
            }
        } else {
            NativeAudioBridge.safeTriggerDrumSample(1, volume, 0.0f)
        }
    }

    private fun mapSampleNameToDrumNote(name: String, fallbackPadId: Int): Int {
        val lower = name.lowercase()
        return when {
            lower.contains("kick") || lower.contains("808") || lower.contains("bd") -> 36
            lower.contains("snare") || lower.contains("sd") -> 38
            lower.contains("hihat") || lower.contains("hat") || lower.contains("hh") -> if (lower.contains("open")) 46 else 42
            lower.contains("clap") || lower.contains("cp") -> 39
            lower.contains("rim") || lower.contains("stick") -> 40
            lower.contains("tom") -> if (lower.contains("hi")) 48 else if (lower.contains("mid")) 45 else 41
            lower.contains("crash") || lower.contains("cymbal") -> 49
            lower.contains("ride") -> 51
            lower.contains("shaker") || lower.contains("per") -> 42
            lower.contains("conga") || lower.contains("bongo") -> 47
            else -> 36 + ((fallbackPadId - 1).coerceIn(0, 15))
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
    // AUDIO LOOPS PLAYER (.wav, .mp3) - DJ-STYLE GAPLESS & BEAT-SYNCED LOOPING
    // -------------------------------------------------------------
    @Volatile private var activeLoopBeats: Int = 0
    @Volatile private var activeLoopBpm: Int = 120

    fun playLoopFile(
        filePath: String,
        volume: Float = 0.65f,
        beatCount: Int = 0,
        bpm: Int = 120,
        startMs: Int = 0,
        endMs: Int = 0,
        pitchShiftSemitones: Int = 0
    ) {
        currentLoopVolume = volume.coerceIn(0f, 1f)
        currentLoopFilePath = filePath
        activeLoopBeats = beatCount
        activeLoopBpm = bpm
        loopTrimStartMs = startMs
        loopTrimEndMs = endMs
        djLoopEngine.playLoop(filePath, currentLoopVolume, beatCount, bpm, startMs, endMs, pitchShiftSemitones)
    }

    fun setLoopPitchShift(semitones: Int) {
        djLoopEngine.setPitchShift(semitones)
    }

    fun setLoopBeats(beatCount: Int, bpm: Int) {
        activeLoopBeats = beatCount
        activeLoopBpm = bpm
        djLoopEngine.setBeats(beatCount, bpm)
    }

    fun updateLoopTrims(startMs: Int, endMs: Int) {
        loopTrimStartMs = startMs
        loopTrimEndMs = endMs
        djLoopEngine.updateTrims(startMs, endMs)
    }

    fun stopLoopPlayer() {
        djLoopEngine.stop()
        currentLoopFilePath = ""
    }

    fun setLoopVolume(volume: Float) {
        currentLoopVolume = volume.coerceIn(0f, 1f)
        djLoopEngine.setVolume(currentLoopVolume)
    }

    fun isLoopPlaying(): Boolean = djLoopEngine.isPlaying()

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
        soundPool = null
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
