package com.example.audio

import android.util.Log

data class PresetInfo(
    val bank: Int,
    val preset: Int,
    val name: String
)

object NativeAudioBridge {
    private var isLibraryLoaded = false

    const val ENGINE_FADER = 0
    const val ENGINE_PAD = 1
    const val ENGINE_DRUM = 2

    // ================= DEDICATED NAMED MIDI CHANNELS =================
    // UI Tracks 1..8 map strictly to MIDI channels 0..7
    const val CHANNEL_TRACK_1 = 0
    const val CHANNEL_TRACK_2 = 1
    const val CHANNEL_TRACK_3 = 2
    const val CHANNEL_TRACK_4 = 3
    const val CHANNEL_TRACK_5 = 4
    const val CHANNEL_TRACK_6 = 5
    const val CHANNEL_TRACK_7 = 6
    const val CHANNEL_TRACK_8 = 7
    const val TRACK_CHANNELS_COUNT = 8

    // Isolated dedicated channels for DrumPad & Tonic Pad
    const val CHANNEL_DRUMPAD = 8
    const val CHANNEL_TONIC_PAD = 9

    const val DRIVER_OBOE = 0
    const val DRIVER_OPENSL_ES = 1

    init {
        try {
            System.loadLibrary("native-lib")
            isLibraryLoaded = true
            Log.i("NativeAudioBridge", "Librairie native chargée avec succès")
        } catch (e: Throwable) {
            isLibraryLoaded = false
            Log.w("NativeAudioBridge", "Librairie native non disponible dans cet environnement: ${e.message}")
        }
    }

    fun isNativeReady(): Boolean = isLibraryLoaded

    external fun startEngine(driverType: Int = 0): Boolean
    external fun stopEngine()
    external fun setAudioDriver(driverType: Int)
    external fun loadSoundFont(engineIndex: Int, absolutePath: String): Int
    external fun unloadSoundFont(engineIndex: Int, soundFontId: Int): Int
    external fun listPresets(soundFontId: Int): Array<PresetInfo>
    external fun selectProgram(engineIndex: Int, channel: Int, soundFontId: Int, bank: Int, preset: Int): Boolean
    external fun programChange(engineIndex: Int, channel: Int, preset: Int): Boolean
    external fun noteOn(engineIndex: Int, channel: Int, midiNote: Int, velocity: Int)
    external fun noteOff(engineIndex: Int, channel: Int, midiNote: Int)
    external fun allNotesOff(engineIndex: Int, channel: Int)
    external fun setTrackVolume(engineIndex: Int, channel: Int, volume01: Float)
    external fun setTrackPan(engineIndex: Int, channel: Int, pan: Float)
    external fun setTrackTranspose(engineIndex: Int, channel: Int, semitones: Int)
    external fun pitchBend(engineIndex: Int, channel: Int, bendValue: Int)

    // Safe wrappers to avoid crashes if native library isn't compiled or loaded
    fun safeStartEngine(driverType: Int = 0): Boolean {
        return if (isLibraryLoaded) {
            try {
                startEngine(driverType)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking startEngine: ${e.message}")
                false
            }
        } else false
    }

    fun safeStopEngine() {
        if (isLibraryLoaded) {
            try {
                stopEngine()
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking stopEngine: ${e.message}")
            }
        }
    }

    fun safeSetAudioDriver(driverType: Int) {
        if (isLibraryLoaded) {
            try {
                setAudioDriver(driverType)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking setAudioDriver: ${e.message}")
            }
        }
    }

    fun safeLoadSoundFont(engineIndex: Int = ENGINE_FADER, absolutePath: String): Int {
        return if (isLibraryLoaded) {
            try {
                loadSoundFont(engineIndex, absolutePath)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking loadSoundFont: ${e.message}")
                -1
            }
        } else -1
    }

    fun safeUnloadSoundFont(engineIndex: Int = ENGINE_FADER, soundFontId: Int): Int {
        return if (isLibraryLoaded && soundFontId > 0) {
            try {
                unloadSoundFont(engineIndex, soundFontId)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking unloadSoundFont: ${e.message}")
                -1
            }
        } else -1
    }

    fun safeSelectProgram(
        engineIndex: Int = ENGINE_FADER,
        channel: Int,
        soundFontId: Int,
        bank: Int,
        preset: Int
    ): Boolean {
        return if (isLibraryLoaded) {
            try {
                selectProgram(engineIndex, channel, soundFontId, bank, preset)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking selectProgram: ${e.message}")
                false
            }
        } else false
    }

    fun safeProgramChange(
        engineIndex: Int = ENGINE_FADER,
        channel: Int,
        preset: Int
    ): Boolean {
        return if (isLibraryLoaded) {
            try {
                programChange(engineIndex, channel, preset)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking programChange: ${e.message}")
                false
            }
        } else false
    }

    fun safeNoteOn(
        channel: Int,
        midiNote: Int,
        velocity: Int = 100,
        engineIndex: Int = ENGINE_FADER
    ) {
        if (isLibraryLoaded) {
            try {
                noteOn(engineIndex, channel, midiNote, velocity)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking noteOn: ${e.message}")
            }
        }
    }

    fun safeNoteOff(
        channel: Int,
        midiNote: Int,
        engineIndex: Int = ENGINE_FADER
    ) {
        if (isLibraryLoaded) {
            try {
                noteOff(engineIndex, channel, midiNote)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking noteOff: ${e.message}")
            }
        }
    }

    fun safeNoteOnEngine(
        engineIndex: Int,
        channel: Int,
        midiNote: Int,
        velocity: Int = 100
    ) {
        if (isLibraryLoaded) {
            try {
                noteOn(engineIndex, channel, midiNote, velocity)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking noteOn: ${e.message}")
            }
        }
    }

    fun safeNoteOffEngine(
        engineIndex: Int,
        channel: Int,
        midiNote: Int
    ) {
        if (isLibraryLoaded) {
            try {
                noteOff(engineIndex, channel, midiNote)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking noteOff: ${e.message}")
            }
        }
    }

    fun safeAllNotesOff(
        channel: Int = -1,
        engineIndex: Int = ENGINE_FADER
    ) {
        if (isLibraryLoaded) {
            try {
                allNotesOff(engineIndex, channel)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking allNotesOff: ${e.message}")
            }
        }
    }

    fun safeSetTrackVolume(
        channel: Int,
        volume01: Float,
        engineIndex: Int = ENGINE_FADER
    ) {
        if (isLibraryLoaded) {
            try {
                setTrackVolume(engineIndex, channel, volume01)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking setTrackVolume: ${e.message}")
            }
        }
    }

    fun safeSetTrackPan(
        channel: Int,
        pan: Float,
        engineIndex: Int = ENGINE_FADER
    ) {
        if (isLibraryLoaded) {
            try {
                setTrackPan(engineIndex, channel, pan)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking setTrackPan: ${e.message}")
            }
        }
    }

    fun safeSetTrackTranspose(
        channel: Int,
        semitones: Int,
        engineIndex: Int = ENGINE_FADER
    ) {
        if (isLibraryLoaded) {
            try {
                setTrackTranspose(engineIndex, channel, semitones)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking setTrackTranspose: ${e.message}")
            }
        }
    }

    fun safePitchBend(
        channel: Int,
        bendValue: Int,
        engineIndex: Int = ENGINE_FADER
    ) {
        if (isLibraryLoaded) {
            try {
                pitchBend(engineIndex, channel, bendValue)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking pitchBend: ${e.message}")
            }
        }
    }
}
