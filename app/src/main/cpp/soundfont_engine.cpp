#include "soundfont_engine.h"
#include <android/log.h>
#include <algorithm>

#define TAG "SoundfontEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

bool SoundfontEngine::init(int sampleRate) {
    destroy();

    mSettings = new_fluid_settings();
    if (!mSettings) {
        LOGE("Failed to allocate fluid_settings");
        return false;
    }

    fluid_settings_setnum(mSettings, "synth.sample-rate", static_cast<double>(sampleRate));
    fluid_settings_setint(mSettings, "synth.polyphony", 128);
    fluid_settings_setint(mSettings, "synth.midi-channels", kMaxChannels);

    mSynth = new_fluid_synth(mSettings);
    if (!mSynth) {
        LOGE("Failed to allocate fluid_synth");
        delete_fluid_settings(mSettings);
        mSettings = nullptr;
        return false;
    }

    for (int ch = 0; ch < kMaxChannels; ++ch) {
        mTransposeSemitones[ch].store(0, std::memory_order_relaxed);
        fluid_synth_cc(mSynth, ch, 7, 100);
        fluid_synth_cc(mSynth, ch, 10, 64);
    }

    LOGI("SoundfontEngine instance initialized (sample rate: %d)", sampleRate);
    return true;
}

void SoundfontEngine::destroy() {
    std::lock_guard<std::mutex> lock(mMutex);
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
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mSynth) {
        LOGE("Cannot load SoundFont: synth instance not initialized");
        return -1;
    }

    // reset_presets = 0 so loading a new SoundFont does NOT override presets on other channels!
    int sfontId = fluid_synth_sfload(mSynth, absolutePath.c_str(), 0);
    if (sfontId < 0) {
        LOGE("Failed to load SoundFont from path: %s", absolutePath.c_str());
        return -1;
    }

    LOGI("Loaded SoundFont successfully (ID: %d, reset_presets=0): %s", sfontId, absolutePath.c_str());
    return sfontId;
}

int SoundfontEngine::unloadSoundFont(int sfontId) {
    if (sfontId <= 0) return -1;
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mSynth) return -1;

    int res = fluid_synth_sfunload(mSynth, sfontId, 0);
    LOGI("Unloaded SoundFont ID: %d (res: %d)", sfontId, res);
    return res;
}

std::vector<NativePresetInfo> SoundfontEngine::listPresets(int soundFontId) {
    std::vector<NativePresetInfo> result;
    if (soundFontId <= 0) return result;
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mSynth) return result;

    fluid_sfont_t* sfont = fluid_synth_get_sfont_by_id(mSynth, soundFontId);
    if (!sfont) return result;

    fluid_sfont_iteration_start(sfont);
    fluid_preset_t* preset;
    while ((preset = fluid_sfont_iteration_next(sfont)) != nullptr) {
        NativePresetInfo info;
        info.name = fluid_preset_get_name(preset);
        info.bank = fluid_preset_get_banknum(preset);
        info.preset = fluid_preset_get_num(preset);
        result.push_back(info);
    }
    return result;
}

bool SoundfontEngine::selectProgram(int channel, int soundFontId, int bank, int preset) {
    if (channel < 0 || channel >= kMaxChannels) return false;
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mSynth) return false;

    int result = FLUID_FAILED;
    if (soundFontId > 0) {
        result = fluid_synth_program_select(mSynth, channel, soundFontId, bank, preset);
    }
    if (result != FLUID_OK) {
        fluid_synth_bank_select(mSynth, channel, bank);
        result = fluid_synth_program_change(mSynth, channel, preset);
    }
    return (result == FLUID_OK);
}

bool SoundfontEngine::programChange(int channel, int program) {
    if (channel < 0 || channel >= kMaxChannels) return false;
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mSynth) return false;
    int result = fluid_synth_program_change(mSynth, channel, program);
    return (result == FLUID_OK);
}

void SoundfontEngine::noteOn(int channel, int midiNote, int velocity) {
    if (!mSynth || channel < 0 || channel >= kMaxChannels) return;

    if (velocity <= 0) {
        noteOff(channel, midiNote);
        return;
    }

    int transposedNote = midiNote + mTransposeSemitones[channel].load(std::memory_order_relaxed);
    int clampedNote = std::clamp(transposedNote, 0, 127);
    int clampedVelocity = std::clamp(velocity, 0, 127);

    fluid_synth_noteon(mSynth, channel, clampedNote, clampedVelocity);
}

void SoundfontEngine::noteOff(int channel, int midiNote) {
    if (!mSynth || channel < 0 || channel >= kMaxChannels) return;

    int transposedNote = midiNote + mTransposeSemitones[channel].load(std::memory_order_relaxed);
    int clampedNote = std::clamp(transposedNote, 0, 127);

    fluid_synth_noteoff(mSynth, channel, clampedNote);
}

void SoundfontEngine::allNotesOff(int channel) {
    if (!mSynth) return;

    if (channel >= 0 && channel < kMaxChannels) {
        fluid_synth_all_notes_off(mSynth, channel);
        fluid_synth_cc(mSynth, channel, 123, 0); // All Notes Off CC
        fluid_synth_cc(mSynth, channel, 120, 0); // All Sound Off CC
    } else {
        for (int ch = 0; ch < kMaxChannels; ++ch) {
            fluid_synth_all_notes_off(mSynth, ch);
            fluid_synth_cc(mSynth, ch, 123, 0);
            fluid_synth_cc(mSynth, ch, 120, 0);
        }
    }
}

void SoundfontEngine::pitchBend(int channel, int bendValue) {
    if (!mSynth || channel < 0 || channel >= kMaxChannels) return;
    int clampedBend = std::clamp(bendValue, 0, 16383);
    fluid_synth_pitch_bend(mSynth, channel, clampedBend);
}

void SoundfontEngine::setChannelVolume(int channel, float volume01) {
    if (!mSynth || channel < 0 || channel >= kMaxChannels) return;

    int ccVal = static_cast<int>(std::clamp(volume01, 0.0f, 1.0f) * 127.0f);
    fluid_synth_cc(mSynth, channel, 7, ccVal);
}

void SoundfontEngine::setChannelPan(int channel, float pan) {
    if (!mSynth || channel < 0 || channel >= kMaxChannels) return;

    // pan: -1.0f (left = 0) to +1.0f (right = 127), center (0.0f) = 64
    float normalized = (std::clamp(pan, -1.0f, 1.0f) + 1.0f) * 0.5f;
    int ccVal = static_cast<int>(normalized * 127.0f);
    fluid_synth_cc(mSynth, channel, 10, ccVal);
}

void SoundfontEngine::setChannelTransposeSemitones(int channel, int semitones) {
    if (channel >= 0 && channel < kMaxChannels) {
        mTransposeSemitones[channel].store(semitones, std::memory_order_relaxed);
    }
}

void SoundfontEngine::renderStereo(float *outputBuffer, int32_t numFrames, bool accumulate) {
    if (!mSynth) {
        if (!accumulate) {
            std::fill(outputBuffer, outputBuffer + (numFrames * 2), 0.0f);
        }
        return;
    }

    // NON-BLOCKING LOCK: If a SoundFont is loading or program is changing,
    // immediately fill with silence (or keep buffer) instead of blocking the real-time audio thread!
    std::unique_lock<std::mutex> lock(mMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        if (!accumulate) {
            std::fill(outputBuffer, outputBuffer + (numFrames * 2), 0.0f);
        }
        return;
    }

    if (!accumulate) {
        fluid_synth_write_float(mSynth, numFrames, outputBuffer, 0, 2, outputBuffer, 1, 2);
    } else {
        size_t totalSamples = static_cast<size_t>(numFrames * 2);
        if (mTempRenderBuffer.size() < totalSamples) {
            mTempRenderBuffer.resize(totalSamples, 0.0f);
        }
        std::fill(mTempRenderBuffer.begin(), mTempRenderBuffer.begin() + totalSamples, 0.0f);

        fluid_synth_write_float(
            mSynth,
            numFrames,
            mTempRenderBuffer.data(), 0, 2,
            mTempRenderBuffer.data(), 1, 2
        );

        for (size_t i = 0; i < totalSamples; ++i) {
            outputBuffer[i] += mTempRenderBuffer[i];
        }
    }
}
