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
    fluid_settings_setnum(mSettings, "synth.gain", 0.7);
    fluid_settings_setint(mSettings, "synth.polyphony", 128);
    fluid_settings_setint(mSettings, "synth.midi-channels", kMaxChannels);
    fluid_settings_setint(mSettings, "synth.reverb.active", 0);
    fluid_settings_setint(mSettings, "synth.chorus.active", 0);

    mSynth = new_fluid_synth(mSettings);
    if (!mSynth) {
        LOGE("Failed to allocate fluid_synth");
        delete_fluid_settings(mSettings);
        mSettings = nullptr;
        return false;
    }

    fluid_synth_set_gain(mSynth, 0.7f);
    fluid_synth_set_interp_method(mSynth, -1, FLUID_INTERP_LINEAR);
    fluid_synth_reverb_on(mSynth, -1, 0);
    fluid_synth_chorus_on(mSynth, -1, 0);

    for (int ch = 0; ch < kMaxChannels; ++ch) {
        mTransposeSemitones[ch].store(0, std::memory_order_relaxed);
        fluid_synth_cc(mSynth, ch, 7, 127);  // Volume max
        fluid_synth_cc(mSynth, ch, 10, 64);  // Pan Center
        fluid_synth_cc(mSynth, ch, 11, 127); // Expression Full
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
        LOGI("Synth instance not initialized yet, auto-initializing in loadSoundFont...");
        mSettings = new_fluid_settings();
        if (mSettings) {
            fluid_settings_setnum(mSettings, "synth.sample-rate", 48000.0);
            fluid_settings_setnum(mSettings, "synth.gain", 0.7);
            fluid_settings_setint(mSettings, "synth.polyphony", 128);
            fluid_settings_setint(mSettings, "synth.midi-channels", kMaxChannels);
            fluid_settings_setint(mSettings, "synth.reverb.active", 0);
            fluid_settings_setint(mSettings, "synth.chorus.active", 0);
            mSynth = new_fluid_synth(mSettings);
            if (mSynth) {
                fluid_synth_set_gain(mSynth, 0.7f);
                fluid_synth_set_interp_method(mSynth, -1, FLUID_INTERP_LINEAR);
                fluid_synth_reverb_on(mSynth, -1, 0);
                fluid_synth_chorus_on(mSynth, -1, 0);
                for (int ch = 0; ch < kMaxChannels; ++ch) {
                    mTransposeSemitones[ch].store(0, std::memory_order_relaxed);
                    fluid_synth_cc(mSynth, ch, 7, 127);
                    fluid_synth_cc(mSynth, ch, 10, 64);
                    fluid_synth_cc(mSynth, ch, 11, 127);
                }
            }
        }
    }
    if (!mSynth) {
        LOGE("Cannot load SoundFont: synth instance could not be allocated");
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
    if (!sfont && fluid_synth_sfcount(mSynth) > 0) {
        sfont = fluid_synth_get_sfont(mSynth, 0);
    }
    if (!sfont) return result;

    fluid_sfont_iteration_start(sfont);
    fluid_preset_t* preset;
    while ((preset = fluid_sfont_iteration_next(sfont)) != nullptr) {
        int presetNum = fluid_preset_get_num(preset);
        int bankNum = fluid_preset_get_banknum(preset);
        const char* name = fluid_preset_get_name(preset);
        if (presetNum >= 0 && presetNum <= 127 && bankNum >= 0 && name != nullptr) {
            NativePresetInfo info;
            info.name = name;
            info.bank = bankNum;
            info.preset = presetNum;
            result.push_back(info);
        }
    }
    std::sort(result.begin(), result.end(), [](const NativePresetInfo &a, const NativePresetInfo &b) {
        if (a.bank != b.bank) return a.bank < b.bank;
        return a.preset < b.preset;
    });
    return result;
}

bool SoundfontEngine::selectProgram(int channel, int soundFontId, int bank, int preset) {
    if (channel < 0 || channel >= kMaxChannels) return false;
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mSynth) return false;

    int result = FLUID_FAILED;
    fluid_sfont_t* sfont = nullptr;
    int targetSfId = soundFontId;

    if (soundFontId > 0) {
        sfont = fluid_synth_get_sfont_by_id(mSynth, soundFontId);
    }

    if (!sfont && fluid_synth_sfcount(mSynth) > 0) {
        sfont = fluid_synth_get_sfont(mSynth, 0);
        if (sfont) {
            targetSfId = fluid_sfont_get_id(sfont);
        }
    }

    if (sfont && targetSfId > 0) {
        fluid_synth_sfont_select(mSynth, channel, targetSfId);
        result = fluid_synth_program_select(mSynth, channel, targetSfId, bank, preset);
        if (result != FLUID_OK) {
            LOGW("program_select failed for ch=%d, sfId=%d, bank=%d, preset=%d. Retrying with first available preset...",
                 channel, targetSfId, bank, preset);
            fluid_sfont_iteration_start(sfont);
            fluid_preset_t* firstPreset = fluid_sfont_iteration_next(sfont);
            if (firstPreset) {
                int fbBank = fluid_preset_get_banknum(firstPreset);
                int fbProg = fluid_preset_get_num(firstPreset);
                LOGI("Found fallback preset: bank=%d, prog=%d (%s) for ch=%d",
                     fbBank, fbProg, fluid_preset_get_name(firstPreset), channel);
                result = fluid_synth_program_select(mSynth, channel, targetSfId, fbBank, fbProg);
            }
        }
    }
    if (result != FLUID_OK) {
        fluid_synth_bank_select(mSynth, channel, bank);
        result = fluid_synth_program_change(mSynth, channel, preset);
    }
    LOGI("selectProgram completed for ch=%d, sfId=%d (target=%d), bank=%d, preset=%d -> %s",
         channel, soundFontId, targetSfId, bank, preset, (result == FLUID_OK ? "SUCCESS" : "FAILED"));
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
    if (channel < 0 || channel >= kMaxChannels) return;

    if (velocity <= 0) {
        noteOff(channel, midiNote);
        return;
    }

    int transposedNote = midiNote + mTransposeSemitones[channel].load(std::memory_order_relaxed);
    int clampedNote = std::clamp(transposedNote, 0, 127);
    int clampedVelocity = std::clamp(velocity, 0, 127);

    EngineMidiEvent ev;
    ev.type = EngineMidiEvent::NOTE_ON;
    ev.channel = channel;
    ev.note = clampedNote;
    ev.velocity = clampedVelocity;
    mEventQueue.push(ev);
}

void SoundfontEngine::noteOff(int channel, int midiNote) {
    if (channel < 0 || channel >= kMaxChannels) return;

    int transposedNote = midiNote + mTransposeSemitones[channel].load(std::memory_order_relaxed);
    int clampedNote = std::clamp(transposedNote, 0, 127);

    EngineMidiEvent ev;
    ev.type = EngineMidiEvent::NOTE_OFF;
    ev.channel = channel;
    ev.note = clampedNote;
    ev.velocity = 0;
    mEventQueue.push(ev);
}

void SoundfontEngine::allNotesOff(int channel) {
    EngineMidiEvent ev;
    ev.type = EngineMidiEvent::ALL_NOTES_OFF;
    ev.channel = channel;
    ev.note = 0;
    ev.velocity = 0;
    mEventQueue.push(ev);
}

void SoundfontEngine::pitchBend(int channel, int bendValue) {
    if (channel < 0 || channel >= kMaxChannels) return;
    int clampedBend = std::clamp(bendValue, 0, 16383);

    EngineMidiEvent ev;
    ev.type = EngineMidiEvent::PITCH_BEND;
    ev.channel = channel;
    ev.param1 = clampedBend;
    mEventQueue.push(ev);
}

void SoundfontEngine::setChannelVolume(int channel, float volume01) {
    if (channel < 0 || channel >= kMaxChannels) return;

    // Perceptual mapping: sqrt(vol) compensates for FluidSynth's internal quadratic (cc7/127)^2 attenuation
    float clampedVol = std::clamp(volume01, 0.0f, 1.0f);
    float perceptualVol = std::sqrt(clampedVol);
    int ccVal = static_cast<int>(perceptualVol * 127.0f);

    EngineMidiEvent ev;
    ev.type = EngineMidiEvent::CC;
    ev.channel = channel;
    ev.param1 = 7; // Volume CC
    ev.param2 = ccVal;
    mEventQueue.push(ev);
}

void SoundfontEngine::setChannelPan(int channel, float pan) {
    if (channel < 0 || channel >= kMaxChannels) return;

    float normalized = (std::clamp(pan, -1.0f, 1.0f) + 1.0f) * 0.5f;
    int ccVal = static_cast<int>(normalized * 127.0f);

    EngineMidiEvent ev;
    ev.type = EngineMidiEvent::CC;
    ev.channel = channel;
    ev.param1 = 10; // Pan CC
    ev.param2 = ccVal;
    mEventQueue.push(ev);
}

void SoundfontEngine::setChannelTransposeSemitones(int channel, int semitones) {
    if (channel >= 0 && channel < kMaxChannels) {
        mTransposeSemitones[channel].store(semitones, std::memory_order_relaxed);
    }
}

void SoundfontEngine::setChannelReverb(int channel, float reverb01) {
    if (channel < 0 || channel >= kMaxChannels) return;
    int ccVal = static_cast<int>(std::clamp(reverb01, 0.0f, 1.0f) * 127.0f);

    EngineMidiEvent ev;
    ev.type = EngineMidiEvent::CC;
    ev.channel = channel;
    ev.param1 = 91; // Reverb CC
    ev.param2 = ccVal;
    mEventQueue.push(ev);
}

void SoundfontEngine::setChannelChorus(int channel, float chorus01) {
    if (channel < 0 || channel >= kMaxChannels) return;
    int ccVal = static_cast<int>(std::clamp(chorus01, 0.0f, 1.0f) * 127.0f);

    EngineMidiEvent ev;
    ev.type = EngineMidiEvent::CC;
    ev.channel = channel;
    ev.param1 = 93; // Chorus CC
    ev.param2 = ccVal;
    mEventQueue.push(ev);
}

void SoundfontEngine::setGain(float gain) {
    if (!mSynth) return;
    float clampedGain = std::clamp(gain, 0.0f, 4.0f);
    fluid_synth_set_gain(mSynth, clampedGain);
}

void SoundfontEngine::setPolyphony(int polyphony) {
    if (!mSynth) return;
    int clamped = std::clamp(polyphony, 16, 256);
    fluid_synth_set_polyphony(mSynth, clamped);
}

int SoundfontEngine::getActiveVoiceCount() const {
    if (!mSynth) return 0;
    return fluid_synth_get_active_voice_count(mSynth);
}

void SoundfontEngine::renderStereo(float *outputBuffer, int32_t numFrames, bool accumulate) {
    if (!mSynth || fluid_synth_sfcount(mSynth) == 0) {
        if (!accumulate) {
            std::fill(outputBuffer, outputBuffer + (numFrames * 2), 0.0f);
        }
        return;
    }

    // Dynamic release time management: gently accelerate voice decay when load > 40 voices
    int activeVoices = fluid_synth_get_active_voice_count(mSynth);
    if (activeVoices > 40) {
        int relVal = std::max(15, 64 - (activeVoices - 40) * 1);
        for (int ch = 0; ch < kMaxChannels; ++ch) {
            fluid_synth_cc(mSynth, ch, 72, relVal);
        }
        mWasHighLoad = true;
    } else if (mWasHighLoad) {
        for (int ch = 0; ch < kMaxChannels; ++ch) {
            fluid_synth_cc(mSynth, ch, 72, 64);
        }
        mWasHighLoad = false;
    }

    // Lock-free drain of all pending MIDI events directly on the audio thread
    EngineMidiEvent ev;
    while (mEventQueue.pop(ev)) {
        switch (ev.type) {
            case EngineMidiEvent::NOTE_ON:
                fluid_synth_noteon(mSynth, ev.channel, ev.note, ev.velocity);
                break;
            case EngineMidiEvent::NOTE_OFF:
                fluid_synth_noteoff(mSynth, ev.channel, ev.note);
                break;
            case EngineMidiEvent::ALL_NOTES_OFF:
                if (ev.channel >= 0 && ev.channel < kMaxChannels) {
                    fluid_synth_all_notes_off(mSynth, ev.channel);
                    fluid_synth_cc(mSynth, ev.channel, 123, 0);
                    fluid_synth_cc(mSynth, ev.channel, 120, 0);
                } else {
                    for (int ch = 0; ch < kMaxChannels; ++ch) {
                        fluid_synth_all_notes_off(mSynth, ch);
                        fluid_synth_cc(mSynth, ch, 123, 0);
                        fluid_synth_cc(mSynth, ch, 120, 0);
                    }
                }
                break;
            case EngineMidiEvent::PITCH_BEND:
                fluid_synth_pitch_bend(mSynth, ev.channel, ev.param1);
                break;
            case EngineMidiEvent::CC:
                fluid_synth_cc(mSynth, ev.channel, ev.param1, ev.param2);
                break;
            case EngineMidiEvent::PROGRAM_CHANGE:
                fluid_synth_program_change(mSynth, ev.channel, ev.param1);
                break;
            case EngineMidiEvent::PROGRAM_SELECT:
                fluid_synth_program_select(mSynth, ev.channel, ev.param1, ev.param2, ev.note);
                break;
        }
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
