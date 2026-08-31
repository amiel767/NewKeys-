#include "soundfont_engine.h"
#include <android/log.h>
#include <algorithm>

#define LOG_TAG "SoundfontEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

bool SoundfontEngine::init(int sampleRate) {
    mSettings = new_fluid_settings();
    if (!mSettings) {
        LOGE("Failed to create fluid_settings_t");
        return false;
    }
    fluid_settings_setnum(mSettings, "synth.sample-rate", sampleRate);
    fluid_settings_setint(mSettings, "synth.polyphony", 64);
    fluid_settings_setint(mSettings, "synth.midi-channels", kMaxChannels);
    fluid_settings_setint(mSettings, "synth.audio-channels", 1);
    fluid_settings_setint(mSettings, "synth.reverb.active", 0);
    fluid_settings_setint(mSettings, "synth.chorus.active", 0);
    fluid_settings_setint(mSettings, "synth.threadsafe-api", 1);

    mSynth = new_fluid_synth(mSettings);
    if (!mSynth) {
        LOGE("Failed to create fluid_synth_t");
        return false;
    }
    for (auto &t : mTransposeSemitones) {
        t.store(0);
    }
    LOGI("SoundfontEngine initialized: sampleRate=%d, channels=%d", sampleRate, kMaxChannels);
    return true;
}

void SoundfontEngine::destroy() {
    if (mSynth) {
        delete_fluid_synth(mSynth);
        mSynth = nullptr;
    }
    if (mSettings) {
        delete_fluid_settings(mSettings);
        mSettings = nullptr;
    }
}

int SoundfontEngine::loadSoundFont(const std::string &absolutePath) {
    if (!mSynth) return -1;
    int sfId = fluid_synth_sfload(mSynth, absolutePath.c_str(), 1);
    if (sfId == FLUID_FAILED) {
        LOGE("Failed to load soundfont: %s", absolutePath.c_str());
        return -1;
    }
    LOGI("Soundfont loaded OK: %s (id=%d)", absolutePath.c_str(), sfId);
    return sfId;
}

bool SoundfontEngine::selectProgram(int channel, int soundFontId, int bank, int preset) {
    if (!mSynth || channel < 0 || channel >= kMaxChannels) return false;
    int result = fluid_synth_program_select(mSynth, channel, soundFontId, bank, preset);
    if (result != FLUID_OK) {
        LOGE("program_select failed: channel=%d sfId=%d bank=%d preset=%d",
             channel, soundFontId, bank, preset);
        return false;
    }
    return true;
}

void SoundfontEngine::noteOn(int channel, int midiNote, int velocity) {
    if (!mSynth || channel < 0 || channel >= kMaxChannels) return;
    int shiftedNote = midiNote + mTransposeSemitones[channel].load();
    shiftedNote = std::clamp(shiftedNote, 0, 127);
    fluid_synth_noteon(mSynth, channel, shiftedNote, velocity);
}

void SoundfontEngine::noteOff(int channel, int midiNote) {
    if (!mSynth || channel < 0 || channel >= kMaxChannels) return;
    int shiftedNote = midiNote + mTransposeSemitones[channel].load();
    shiftedNote = std::clamp(shiftedNote, 0, 127);
    fluid_synth_noteoff(mSynth, channel, shiftedNote);
}

void SoundfontEngine::allNotesOff(int channel) {
    if (!mSynth || channel < 0 || channel >= kMaxChannels) return;
    fluid_synth_all_notes_off(mSynth, channel);
}

void SoundfontEngine::setChannelVolume(int channel, float volume01) {
    if (!mSynth || channel < 0 || channel >= kMaxChannels) return;
    int midiValue = std::clamp(static_cast<int>(volume01 * 127.0f), 0, 127);
    fluid_synth_cc(mSynth, channel, 7, midiValue);
}

void SoundfontEngine::setChannelPan(int channel, float pan) {
    if (!mSynth || channel < 0 || channel >= kMaxChannels) return;
    int midiValue = std::clamp(static_cast<int>((pan + 1.0f) * 63.5f), 0, 127);
    fluid_synth_cc(mSynth, channel, 10, midiValue);
}

void SoundfontEngine::setChannelTransposeSemitones(int channel, int semitones) {
    if (channel < 0 || channel >= kMaxChannels) return;
    mTransposeSemitones[channel].store(semitones);
}

void SoundfontEngine::renderStereo(float *outputBuffer, int32_t numFrames) {
    if (!mSynth) {
        std::fill(outputBuffer, outputBuffer + (numFrames * 2), 0.0f);
        return;
    }
    fluid_synth_write_float(mSynth, numFrames, outputBuffer, 0, 2, outputBuffer, 1, 2);
}
