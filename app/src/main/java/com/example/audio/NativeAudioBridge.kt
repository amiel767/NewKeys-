package com.example.audio

import android.util.Log

object NativeAudioBridge {
    private var isLibraryLoaded = false

    const val ENGINE_FADER = 0
    const val ENGINE_PAD = 1
    const val ENGINE_DRUM = 2

    const val DRIVER_OBOE = 0
    const val DRIVER_OPENSL_ES = 1

    init {
        try {
            System.loadLibrary("native-lib")
            isLibraryLoaded = true
            Log.i("NativeAudioBridge", "Librairie native chargée avec succès")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NativeAudioBridge", "ECHEC chargement librairie native", e)
            throw e
        }
    }

    fun isNativeReady(): Boolean = isLibraryLoaded

    external fun startEngine(driverType: Int = 0): Boolean
    external fun stopEngine()
    external fun setAudioDriver(driverType: Int)
    external fun loadSoundFont(engineIndex: Int, absolutePath: String): Int
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
