#ifndef DAWSTUDIO_SOUNDFONT_ENGINE_H
#define DAWSTUDIO_SOUNDFONT_ENGINE_H

#if __has_include(<fluidsynth.h>)
#include <fluidsynth.h>
#else
// Fallback definitions if fluidsynth headers are unavailable
typedef void fluid_settings_t;
typedef void fluid_synth_t;
#define FLUID_OK 0
#define FLUID_FAILED -1
inline fluid_settings_t* new_fluid_settings() { return nullptr; }
inline int delete_fluid_settings(fluid_settings_t*) { return 0; }
inline fluid_synth_t* new_fluid_synth(fluid_settings_t*) { return nullptr; }
inline int delete_fluid_synth(fluid_synth_t*) { return 0; }
inline int fluid_settings_setnum(fluid_settings_t*, const char*, double) { return 0; }
inline int fluid_settings_setint(fluid_settings_t*, const char*, int) { return 0; }
inline int fluid_synth_sfload(fluid_synth_t*, const char*, int) { return -1; }
inline int fluid_synth_program_select(fluid_synth_t*, int, int, int, int) { return -1; }
inline int fluid_synth_program_change(fluid_synth_t*, int, int) { return -1; }
inline int fluid_synth_bank_select(fluid_synth_t*, int, int) { return -1; }
inline int fluid_synth_noteon(fluid_synth_t*, int, int, int) { return 0; }
inline int fluid_synth_noteoff(fluid_synth_t*, int, int) { return 0; }
inline int fluid_synth_all_notes_off(fluid_synth_t*, int) { return 0; }
inline int fluid_synth_pitch_bend(fluid_synth_t*, int, int) { return 0; }
inline int fluid_synth_cc(fluid_synth_t*, int, int, int) { return 0; }
inline int fluid_synth_write_float(fluid_synth_t*, int, void*, int, int, void*, int, int) { return 0; }
#endif

#include <atomic>
#include <array>
#include <string>
#include <vector>

/**
 * Autonomous FluidSynth instance. Multiple instances can co-exist
 * (e.g. FaderEngine, PadEngine, DrumEngine) with independent SoundFont banks and MIDI channels.
 */
class SoundfontEngine {
public:
    static constexpr int kMaxChannels = 16;

    bool init(int sampleRate);
    void destroy();

    int loadSoundFont(const std::string &absolutePath);
    bool selectProgram(int channel, int soundFontId, int bank, int preset);
    bool programChange(int channel, int program);

    void noteOn(int channel, int midiNote, int velocity);
    void noteOff(int channel, int midiNote);
    void allNotesOff(int channel);
    void pitchBend(int channel, int bendValue);

    void setChannelVolume(int channel, float volume01);
    void setChannelPan(int channel, float pan);
    void setChannelTransposeSemitones(int channel, int semitones);

    void renderStereo(float *outputBuffer, int32_t numFrames, bool accumulate = false);

private:
    fluid_settings_t *mSettings = nullptr;
    fluid_synth_t *mSynth = nullptr;
    std::array<std::atomic<int>, kMaxChannels> mTransposeSemitones{};
    std::vector<float> mTempRenderBuffer;
};

#endif //DAWSTUDIO_SOUNDFONT_ENGINE_H
