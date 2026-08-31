#ifndef DAWSTUDIO_SOUNDFONT_ENGINE_H
#define DAWSTUDIO_SOUNDFONT_ENGINE_H

#if __has_include(<fluidsynth.h>)
#include <fluidsynth.h>
#else
// Fallback definitions if fluidsynth is not yet linked during initial phase
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
inline int fluid_synth_noteon(fluid_synth_t*, int, int, int) { return 0; }
inline int fluid_synth_noteoff(fluid_synth_t*, int, int) { return 0; }
inline int fluid_synth_all_notes_off(fluid_synth_t*, int) { return 0; }
inline int fluid_synth_cc(fluid_synth_t*, int, int, int) { return 0; }
inline int fluid_synth_write_float(fluid_synth_t*, int, void*, int, int, void*, int, int) { return 0; }
#endif

#include <atomic>
#include <array>
#include <string>

// One FluidSynth instance drives ALL tracks at once, using MIDI channels 0-15,
// instead of one synth instance per track — this avoids duplicating the whole
// SoundFont sample data in memory 8 times over.
class SoundfontEngine {
public:
    static constexpr int kMaxChannels = 16;

    bool init(int sampleRate);
    void destroy();

    int loadSoundFont(const std::string &absolutePath);
    bool selectProgram(int channel, int soundFontId, int bank, int preset);

    void noteOn(int channel, int midiNote, int velocity);
    void noteOff(int channel, int midiNote);
    void allNotesOff(int channel);

    void setChannelVolume(int channel, float volume01);
    void setChannelPan(int channel, float pan);

    // Real transpose: re-maps which MIDI note number gets played (NOT a
    // playback-speed/pitch-bend hack), so the timbre stays natural.
    void setChannelTransposeSemitones(int channel, int semitones);

    void renderStereo(float *outputBuffer, int32_t numFrames);

private:
    fluid_settings_t *mSettings = nullptr;
    fluid_synth_t *mSynth = nullptr;
    std::array<std::atomic<int>, kMaxChannels> mTransposeSemitones{};
};

#endif //DAWSTUDIO_SOUNDFONT_ENGINE_H
