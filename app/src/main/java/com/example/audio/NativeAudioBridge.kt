package com.example.audio

import android.util.Log

object NativeAudioBridge {
    private var isLibraryLoaded = false

    init {
        try {
            System.loadLibrary("native-lib")
            isLibraryLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.w("NativeAudioBridge", "Native library native-lib not loaded yet: ${e.message}")
        } catch (e: Exception) {
            Log.e("NativeAudioBridge", "Error loading native-lib: ${e.message}")
        }
    }

    fun isNativeReady(): Boolean = isLibraryLoaded

    external fun startEngine(): Boolean
    external fun stopEngine()
    external fun loadSoundFont(absolutePath: String): Int
    external fun selectProgram(channel: Int, soundFontId: Int, bank: Int, preset: Int): Boolean
    external fun noteOn(channel: Int, midiNote: Int, velocity: Int)
    external fun noteOff(channel: Int, midiNote: Int)
    external fun allNotesOff(channel: Int)
    external fun setTrackVolume(channel: Int, volume01: Float)
    external fun setTrackPan(channel: Int, pan: Float)
    external fun setTrackTranspose(channel: Int, semitones: Int)

    // Safe wrappers to avoid crashes if native library isn't compiled into the APK yet
    fun safeStartEngine(): Boolean {
        return if (isLibraryLoaded) {
            try {
                startEngine()
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

    fun safeLoadSoundFont(absolutePath: String): Int {
        return if (isLibraryLoaded) {
            try {
                loadSoundFont(absolutePath)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking loadSoundFont: ${e.message}")
                -1
            }
        } else -1
    }

    fun safeSelectProgram(channel: Int, soundFontId: Int, bank: Int, preset: Int): Boolean {
        return if (isLibraryLoaded) {
            try {
                selectProgram(channel, soundFontId, bank, preset)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking selectProgram: ${e.message}")
                false
            }
        } else false
    }

    fun safeNoteOn(channel: Int, midiNote: Int, velocity: Int = 100) {
        if (isLibraryLoaded) {
            try {
                noteOn(channel, midiNote, velocity)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking noteOn: ${e.message}")
            }
        }
    }

    fun safeNoteOff(channel: Int, midiNote: Int) {
        if (isLibraryLoaded) {
            try {
                noteOff(channel, midiNote)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking noteOff: ${e.message}")
            }
        }
    }

    fun safeAllNotesOff(channel: Int) {
        if (isLibraryLoaded) {
            try {
                allNotesOff(channel)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking allNotesOff: ${e.message}")
            }
        }
    }

    fun safeSetTrackVolume(channel: Int, volume01: Float) {
        if (isLibraryLoaded) {
            try {
                setTrackVolume(channel, volume01)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking setTrackVolume: ${e.message}")
            }
        }
    }

    fun safeSetTrackPan(channel: Int, pan: Float) {
        if (isLibraryLoaded) {
            try {
                setTrackPan(channel, pan)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking setTrackPan: ${e.message}")
            }
        }
    }

    fun safeSetTrackTranspose(channel: Int, semitones: Int) {
        if (isLibraryLoaded) {
            try {
                setTrackTranspose(channel, semitones)
            } catch (e: Throwable) {
                Log.e("NativeAudioBridge", "Error invoking setTrackTranspose: ${e.message}")
            }
        }
    }
}
