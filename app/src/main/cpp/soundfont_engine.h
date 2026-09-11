#ifndef DAWSTUDIO_SOUNDFONT_ENGINE_H
#define DAWSTUDIO_SOUNDFONT_ENGINE_H

#if __has_include(<fluidsynth.h>)
#include <fluidsynth.h>
#else
// Fallback definitions if fluidsynth headers are unavailable
typedef void fluid_settings_t;
typedef void fluid_synth_t;
typedef void fluid_sfont_t;
typedef void fluid_preset_t;
#define FLUID_OK 0
#define FLUID_FAILED -1
inline fluid_settings_t* new_fluid_settings() { return nullptr; }
inline int delete_fluid_settings(fluid_settings_t*) { return 0; }
inline fluid_synth_t* new_fluid_synth(fluid_settings_t*) { return nullptr; }
inline int delete_fluid_synth(fluid_synth_t*) { return 0; }
inline int fluid_settings_setnum(fluid_settings_t*, const char*, double) { return 0; }
inline int fluid_settings_setint(fluid_settings_t*, const char*, int) { return 0; }
inline int fluid_synth_sfload(fluid_synth_t*, const char*, int) { return -1; }
inline int fluid_synth_sfunload(fluid_synth_t*, int, int) { return -1; }
inline int fluid_synth_program_select(fluid_synth_t*, int, int, int, int) { return -1; }
inline int fluid_synth_program_change(fluid_synth_t*, int, int) { return -1; }
inline int fluid_synth_bank_select(fluid_synth_t*, int, int) { return -1; }
inline int fluid_synth_noteon(fluid_synth_t*, int, int, int) { return 0; }
inline int fluid_synth_noteoff(fluid_synth_t*, int, int) { return 0; }
inline int fluid_synth_all_notes_off(fluid_synth_t*, int) { return 0; }
inline int fluid_synth_pitch_bend(fluid_synth_t*, int, int) { return 0; }
inline int fluid_synth_cc(fluid_synth_t*, int, int, int) { return 0; }
inline int fluid_synth_write_float(fluid_synth_t*, int, void*, int, int, void*, int, int) { return 0; }
inline fluid_sfont_t* fluid_synth_get_sfont_by_id(fluid_synth_t*, int) { return nullptr; }
inline void fluid_sfont_iteration_start(fluid_sfont_t*) {}
inline fluid_preset_t* fluid_sfont_iteration_next(fluid_sfont_t*) { return nullptr; }
inline const char* fluid_preset_get_name(fluid_preset_t*) { return ""; }
inline int fluid_preset_get_banknum(fluid_preset_t*) { return 0; }
inline int fluid_preset_get_num(fluid_preset_t*) { return 0; }
inline void fluid_synth_set_gain(fluid_synth_t*, float) {}
inline int fluid_synth_set_polyphony(fluid_synth_t*, int) { return 0; }
#endif

#include <atomic>
#include <array>
#include <mutex>
#include <string>
#include <vector>

struct NativePresetInfo {
    int bank;
    int preset;
    std::string name;
};

struct EngineMidiEvent {
    enum Type {
        NOTE_ON,
        NOTE_OFF,
        ALL_NOTES_OFF,
        PITCH_BEND,
        CC,
        PROGRAM_CHANGE,
        PROGRAM_SELECT
    } type;
    int channel;
    int note;
    int velocity;
    int param1;
    int param2;
};

template <typename T, size_t Capacity>
class LockFreeRingBuffer {
public:
    LockFreeRingBuffer() : mHead(0), mTail(0) {}

    bool push(const T& item) {
        size_t currentTail = mTail.load(std::memory_order_relaxed);
        size_t nextTail = (currentTail + 1) % Capacity;
        if (nextTail == mHead.load(std::memory_order_acquire)) {
            return false; // Full
        }
        mBuffer[currentTail] = item;
        mTail.store(nextTail, std::memory_order_release);
        return true;
    }

    bool pop(T& item) {
        size_t currentHead = mHead.load(std::memory_order_relaxed);
        if (currentHead == mTail.load(std::memory_order_acquire)) {
            return false; // Empty
        }
        item = mBuffer[currentHead];
        mHead.store((currentHead + 1) % Capacity, std::memory_order_release);
        return true;
    }

    bool peek(T& item) const {
        size_t currentHead = mHead.load(std::memory_order_relaxed);
        if (currentHead == mTail.load(std::memory_order_acquire)) {
            return false; // Empty
        }
        item = mBuffer[currentHead];
        return true;
    }

    void clear() {
        mHead.store(0, std::memory_order_relaxed);
        mTail.store(0, std::memory_order_relaxed);
    }

private:
    std::array<T, Capacity> mBuffer;
    std::atomic<size_t> mHead;
    std::atomic<size_t> mTail;
};

/**
 * Autonomous FluidSynth instance. Multiple instances can co-exist
 * (e.g. FaderEngine, PadEngine, DrumEngine) with independent SoundFont banks and MIDI channels.
 */
class SoundfontEngine {
public:
    static constexpr int kMaxChannels = 16;

    bool init(int sampleRate);
    void destroy();
    bool isInitialized() const { return mSynth != nullptr; }

    int loadSoundFont(const std::string &absolutePath);
    int unloadSoundFont(int sfontId);
    std::vector<NativePresetInfo> listPresets(int soundFontId);
    bool selectProgram(int channel, int soundFontId, int bank, int preset);
    bool programChange(int channel, int program);

    void noteOn(int channel, int midiNote, int velocity);
    void noteOff(int channel, int midiNote);
    void allNotesOff(int channel);
    void pitchBend(int channel, int bendValue);

    void setChannelVolume(int channel, float volume01);
    void setChannelPan(int channel, float pan);
    void setChannelTransposeSemitones(int channel, int semitones);
    void setChannelReverb(int channel, float reverb01);
    void setChannelChorus(int channel, float chorus01);
    void setGain(float gain);
    void setPolyphony(int polyphony);
    int getActiveVoiceCount() const;

    void renderStereo(float *outputBuffer, int32_t numFrames, bool accumulate = false);

private:
    fluid_settings_t *mSettings = nullptr;
    fluid_synth_t *mSynth = nullptr;
    std::mutex mMutex;
    std::array<std::atomic<int>, kMaxChannels> mTransposeSemitones{};
    std::vector<float> mTempRenderBuffer;
    LockFreeRingBuffer<EngineMidiEvent, 2048> mEventQueue;
    bool mWasHighLoad = false;
};

#endif //DAWSTUDIO_SOUNDFONT_ENGINE_H
