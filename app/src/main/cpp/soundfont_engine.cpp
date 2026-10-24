#include "soundfont_engine.h"
#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <thread>
#include <malloc.h>

// FluidSynth's reverb/chorus reset entry points are declared in the internal
// fluid_synth.h, which cannot be included here (it re-declares the public API).
// The symbols live in the static libfluidsynth linked into this same library, so a
// local declaration is enough. Used by panic() to flush the effect tails.
extern "C" int fluid_synth_reset_reverb(fluid_synth_t *synth);
extern "C" int fluid_synth_reset_chorus(fluid_synth_t *synth);

#if __has_include(<fluidsynth/voice.h>)
#include <fluidsynth/voice.h>
#endif

#define TAG "SoundfontEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

bool SoundfontEngine::init(int sampleRate, int polyphony, const char* instanceName) {
    std::lock_guard<std::recursive_mutex> lock(mMutex);
    destroyInternal();

    mInstanceName = instanceName ? instanceName : "FaderEngine";
    mConfiguredPolyphony = std::clamp(polyphony, 16, 256);

    mSettings = new_fluid_settings();
    if (!mSettings) {
        LOGE("[%s] Failed to allocate fluid_settings", mInstanceName.c_str());
        return false;
    }

    fluid_settings_setnum(mSettings, "synth.sample-rate", static_cast<double>(sampleRate));
    fluid_settings_setnum(mSettings, "synth.gain", 2.2);
    fluid_settings_setint(mSettings, "synth.polyphony", mConfiguredPolyphony);
    fluid_settings_setint(mSettings, "synth.midi-channels", kMaxChannels);
    fluid_settings_setint(mSettings, "synth.reverb.active", 0);
    // Multi-channel mode needs one stereo output per MIDI channel, so channel i
    // renders into group i. When the flag is off we keep the historical mono mix
    // (and its chorus) untouched, so the audible result cannot change.
    if (mMultiChannelEnabled) {
        // One stereo output per MIDI channel the app addresses (0..9). FluidSynth
        // renders every declared group on every block, so this is a direct cost
        // ceiling; kAudioGroups is the highest channel used + 1.
        fluid_settings_setint(mSettings, "synth.audio-channels", kAudioGroups);
        fluid_settings_setint(mSettings, "synth.audio-groups", kAudioGroups);
        fluid_settings_setint(mSettings, "synth.chorus.active", 0);
    } else {
        fluid_settings_setint(mSettings, "synth.chorus.active", 1);
    }
    fluid_settings_setint(mSettings, "synth.dynamic-sample-loading", 1);

    mSynth = new_fluid_synth(mSettings);
    if (!mSynth) {
        LOGE("[%s] Failed to allocate fluid_synth", mInstanceName.c_str());
        delete_fluid_settings(mSettings);
        mSettings = nullptr;
        return false;
    }

    fluid_synth_set_gain(mSynth, 2.2f);
    fluid_synth_set_interp_method(mSynth, -1, FLUID_INTERP_4THORDER);
    fluid_synth_reverb_on(mSynth, -1, 0);
    fluid_synth_chorus_on(mSynth, -1, mMultiChannelEnabled ? 0 : 1);

    // Pre-size the per-channel scratch. The audio callback must never resize, so
    // the allocation happens here and renderChannels() only fails if asked for
    // more channels than were reserved.
    mChannelBuffer.assign(static_cast<size_t>(2 * kMaxChannels) * kChannelFrameCapacity, 0.0f);
    for (int ch = 0; ch < kMaxChannels; ++ch) {
        mChannelPtrs[2 * ch] = mChannelBuffer.data() + static_cast<size_t>(2 * ch) * kChannelFrameCapacity;
        mChannelPtrs[2 * ch + 1] = mChannelPtrs[2 * ch] + kChannelFrameCapacity;
    }
    // renderStereo()'s group fold, pre-sized so it never allocates in the callback.
    mStereoFold.assign(static_cast<size_t>(2) * kChannelFrameCapacity, 0.0f);

    for (int ch = 0; ch < kMaxChannels; ++ch) {
        fluid_synth_set_channel_type(mSynth, ch, CHANNEL_TYPE_MELODIC);
        mTransposeSemitones[ch].store(0, std::memory_order_relaxed);
        mChannelVolumes[ch].store(108, std::memory_order_relaxed);
        fluid_synth_cc(mSynth, ch, 7, 108);  // Default healthy volume
        fluid_synth_cc(mSynth, ch, 10, 64);  // Pan Center
        fluid_synth_cc(mSynth, ch, 11, 127); // Expression Full
    }

    int effectivePoly = fluid_synth_get_polyphony(mSynth);
    LOGI("[%s] Startup Verification: Requested Polyphony=%d, Effective Polyphony=%d, Interpolation=LINEAR(1), SampleRate=%d",
         mInstanceName.c_str(), mConfiguredPolyphony, effectivePoly, sampleRate);
    return true;
}

bool SoundfontEngine::renderChannels(float *out, int32_t numFrames, int channelCount) {
    if (!mMultiChannelEnabled || !out) return false;
    if (channelCount <= 0) channelCount = kAudioGroups;
    if (channelCount > kAudioGroups) channelCount = kAudioGroups;
    if (numFrames <= 0) return true;
    if (numFrames > kChannelFrameCapacity) return false;

    std::unique_lock<std::recursive_mutex> lock(mMutex, std::try_to_lock);
    if (!lock.owns_lock() || !mSynth) {
        std::fill(out, out + static_cast<size_t>(numFrames) * 2 * channelCount, 0.0f);
        return true;
    }

    drainEvents();

    // fluid_synth_process() *adds* into the buffers it is given, and the planar
    // scratch is reused every block, so it has to be cleared here or each block
    // piles onto the previous one and the strip input grows without bound.
    for (int ch = 0; ch < 2 * channelCount; ++ch) {
        std::fill(mChannelPtrs[ch], mChannelPtrs[ch] + numFrames, 0.0f);
    }

    // `out` is channel-major (L0,R0,L0,R0,... then L1,R1,...) while
    // fluid_synth_process wants one planar pointer per channel, so each channel
    // is rendered into the planar scratch and then copied out. Both sides are
    // contiguous, so this is a straight copy per channel rather than the two
    // stride-2 * channelCount passes the old sample-major layout needed.
    fluid_synth_process(mSynth, numFrames, 0, nullptr, 2 * channelCount, mChannelPtrs.data());
    for (int ch = 0; ch < channelCount; ++ch) {
        float *dst = out + static_cast<size_t>(2 * ch) * numFrames;
        const float *srcL = mChannelPtrs[2 * ch];
        const float *srcR = mChannelPtrs[2 * ch + 1];
        for (int i = 0; i < numFrames; ++i) {
            dst[2 * i] = srcL[i];
            dst[2 * i + 1] = srcR[i];
        }
    }

    int activeCount = fluid_synth_get_active_voice_count(mSynth);
    mAuditTotalVoices.store(activeCount, std::memory_order_relaxed);
    mAuditAudibleVoices.store(activeCount, std::memory_order_relaxed);
    return true;
}

bool SoundfontEngine::renderPlanar(int32_t numFrames, int channelCount) {
    if (!mMultiChannelEnabled || !mSynth) return false;
    if (channelCount <= 0) channelCount = kAudioGroups;
    if (channelCount > kAudioGroups) channelCount = kAudioGroups;
    if (numFrames <= 0) return true;
    if (numFrames > kChannelFrameCapacity) return false;

    std::unique_lock<std::recursive_mutex> lock(mMutex, std::try_to_lock);
    if (!lock.owns_lock()) return false;

    drainEvents();
    for (int ch = 0; ch < 2 * channelCount; ++ch) {
        std::fill(mChannelPtrs[ch], mChannelPtrs[ch] + numFrames, 0.0f);
    }
    fluid_synth_process(mSynth, numFrames, 0, nullptr, 2 * channelCount, mChannelPtrs.data());

    int activeCount = fluid_synth_get_active_voice_count(mSynth);
    mAuditTotalVoices.store(activeCount, std::memory_order_relaxed);
    mAuditAudibleVoices.store(activeCount, std::memory_order_relaxed);
    return true;
}

void SoundfontEngine::destroy() {
    std::lock_guard<std::recursive_mutex> lock(mMutex);
    destroyInternal();
}

void SoundfontEngine::destroyInternal() {
    if (mSynth) {
        delete_fluid_synth(mSynth);
        mSynth = nullptr;
    }
    if (mSettings) {
        delete_fluid_settings(mSettings);
        mSettings = nullptr;
    }
    mPathToSfontId.clear();
    mSfontRefCount.clear();
    mSfontIdToPath.clear();
#if defined(__ANDROID__) && __ANDROID_API__ >= 28
    malloc_trim(0);
#endif
}

int SoundfontEngine::loadSoundFont(const std::string &absolutePath) {
    std::lock_guard<std::recursive_mutex> lock(mMutex);
    if (!mSynth) {
        LOGI("[%s] Synth instance not initialized yet, auto-initializing in loadSoundFont...", mInstanceName.c_str());
        mSettings = new_fluid_settings();
        if (mSettings) {
            fluid_settings_setnum(mSettings, "synth.sample-rate", 48000.0);
            fluid_settings_setnum(mSettings, "synth.gain", 2.2);
            fluid_settings_setint(mSettings, "synth.polyphony", mConfiguredPolyphony);
            fluid_settings_setint(mSettings, "synth.midi-channels", kMaxChannels);
            fluid_settings_setint(mSettings, "synth.reverb.active", 0);
            fluid_settings_setint(mSettings, "synth.chorus.active", 1);
            fluid_settings_setint(mSettings, "synth.dynamic-sample-loading", 1);
            mSynth = new_fluid_synth(mSettings);
            if (mSynth) {
                fluid_synth_set_gain(mSynth, 2.2f);
                fluid_synth_set_interp_method(mSynth, -1, FLUID_INTERP_4THORDER);
                fluid_synth_reverb_on(mSynth, -1, 0);
                fluid_synth_chorus_on(mSynth, -1, 1);
                for (int ch = 0; ch < kMaxChannels; ++ch) {
                    fluid_synth_set_channel_type(mSynth, ch, CHANNEL_TYPE_MELODIC);
                    mTransposeSemitones[ch].store(0, std::memory_order_relaxed);
                    mChannelVolumes[ch].store(108, std::memory_order_relaxed);
                    fluid_synth_cc(mSynth, ch, 7, 108);
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

    // Check if this SoundFont is ALREADY loaded in memory (Shared Pool / Zero-RAM Duplication)
    auto it = mPathToSfontId.find(absolutePath);
    if (it != mPathToSfontId.end()) {
        int existingId = it->second;
        LOGI("[%s] Reusing existing loaded SoundFont ID: %d for path: %s",
             mInstanceName.c_str(), existingId, absolutePath.c_str());
        return existingId;
    }

    // reset_presets = 0 so loading a new SoundFont does NOT override presets on other channels!
    int sfontId = fluid_synth_sfload(mSynth, absolutePath.c_str(), 0);
    if (sfontId < 0) {
        LOGE("Failed to load SoundFont from path: %s", absolutePath.c_str());
        return -1;
    }

    // Register in shared pool
    mPathToSfontId[absolutePath] = sfontId;
    mSfontIdToPath[sfontId] = absolutePath;
    mSfontRefCount[sfontId] = 1;

    LOGI("[%s] Loaded SoundFont into Shared Pool successfully (ID: %d): %s",
         mInstanceName.c_str(), sfontId, absolutePath.c_str());
    return sfontId;
}

int SoundfontEngine::unloadSoundFont(int sfontId) {
    if (sfontId <= 0) return -1;
    std::lock_guard<std::recursive_mutex> lock(mMutex);
    if (!mSynth) return -1;

    // Purge from tracking maps
    auto it = mSfontIdToPath.find(sfontId);
    if (it != mSfontIdToPath.end()) {
        std::string path = it->second;
        mPathToSfontId.erase(path);
        mSfontIdToPath.erase(sfontId);
        mSfontRefCount.erase(sfontId);
    }

    // Safety: shut down any voices using this soundfont before freeing memory
    fluid_synth_all_notes_off(mSynth, -1);
    
    // Slight delay to let voices fade if needed, though all_notes_off should be immediate
    std::this_thread::sleep_for(std::chrono::milliseconds(10));

    // reset_presets = 0 to avoid crashing active voices on other channels
    int res = fluid_synth_sfunload(mSynth, sfontId, 0);
#if defined(__ANDROID__) && __ANDROID_API__ >= 28
    malloc_trim(0);
#endif
    LOGI("[%s] Completely Unloaded SoundFont ID: %d from RAM (res: %d)", mInstanceName.c_str(), sfontId, res);
    return res;
}

void SoundfontEngine::unloadAllSoundFonts() {
    std::lock_guard<std::recursive_mutex> lock(mMutex);
    if (!mSynth) return;

    fluid_synth_all_notes_off(mSynth, -1);
    for (int ch = 0; ch < kMaxChannels; ++ch) {
        fluid_synth_cc(mSynth, ch, 123, 0);
        fluid_synth_cc(mSynth, ch, 120, 0);
    }
    mEventQueue.clear();

    // Copy IDs to safely unload all soundfonts
    std::vector<int> sfIds;
    for (const auto &pair : mSfontIdToPath) {
        sfIds.push_back(pair.first);
    }

    for (int sfId : sfIds) {
        fluid_synth_sfunload(mSynth, sfId, 0);
    }

    mPathToSfontId.clear();
    mSfontRefCount.clear();
    mSfontIdToPath.clear();

#if defined(__ANDROID__) && __ANDROID_API__ >= 28
    malloc_trim(0);
#endif
    LOGI("[%s] Unloaded ALL SoundFonts and purged native RAM cache.", mInstanceName.c_str());
}

std::vector<NativePresetInfo> SoundfontEngine::listPresets(int soundFontId) {
    std::vector<NativePresetInfo> result;
    if (soundFontId <= 0) return result;
    std::lock_guard<std::recursive_mutex> lock(mMutex);
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
            std::string nameStr(name);
            // Trim leading/trailing whitespace and control chars
            size_t start = nameStr.find_first_not_of(" \t\r\n\0");
            size_t end = nameStr.find_last_not_of(" \t\r\n\0");
            if (start == std::string::npos) continue;
            std::string trimmed = nameStr.substr(start, end - start + 1);
            if (trimmed.empty() || trimmed == "EOP" || trimmed == "EOS" || trimmed == "End of Presets") continue;
            
            std::string lowerTrimmed = trimmed;
            std::transform(lowerTrimmed.begin(), lowerTrimmed.end(), lowerTrimmed.begin(), ::tolower);
            if (lowerTrimmed == "unused" || lowerTrimmed == "empty" || lowerTrimmed == "blank" || lowerTrimmed == "null") continue;

            NativePresetInfo info;
            info.name = trimmed;
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

void SoundfontEngine::executeProgramSelect(int channel, int soundFontId, int bank, int preset) {
    if (!mSynth) return;

    int result = FLUID_FAILED;
    fluid_sfont_t* sfont = nullptr;

    if (soundFontId > 0) {
        sfont = fluid_synth_get_sfont_by_id(mSynth, soundFontId);
    }

    if (sfont && soundFontId > 0) {
        fluid_synth_sfont_select(mSynth, channel, soundFontId);
        result = fluid_synth_program_select(mSynth, channel, soundFontId, bank, preset);
        if (result != FLUID_OK) {
            LOGW("program_select failed for ch=%d, sfId=%d, bank=%d, preset=%d. Retrying with first available preset...",
                 channel, soundFontId, bank, preset);
            fluid_sfont_iteration_start(sfont);
            fluid_preset_t* firstPreset = fluid_sfont_iteration_next(sfont);
            if (firstPreset) {
                int fbBank = fluid_preset_get_banknum(firstPreset);
                int fbProg = fluid_preset_get_num(firstPreset);
                LOGI("Found fallback preset: bank=%d, prog=%d (%s) for ch=%d",
                     fbBank, fbProg, fluid_preset_get_name(firstPreset), channel);
                result = fluid_synth_program_select(mSynth, channel, soundFontId, fbBank, fbProg);
            }
        }
    } else if (soundFontId <= 0) {
        fluid_synth_bank_select(mSynth, channel, bank);
        result = fluid_synth_program_change(mSynth, channel, preset);
    }
    // Instantly restore and enforce channel CC 7 (Volume) and CC 11 (Expression)
    // so sound is heard immediately upon loading/selecting presets without touching fader!
    int currentVol = mChannelVolumes[channel].load(std::memory_order_relaxed);
    if (currentVol <= 0) currentVol = 108;
    fluid_synth_cc(mSynth, channel, 7, currentVol);
    fluid_synth_cc(mSynth, channel, 11, 127);
    LOGI("executeProgramSelect completed for ch=%d, sfId=%d, bank=%d, preset=%d -> %s (vol=%d)",
         channel, soundFontId, bank, preset, (result == FLUID_OK ? "SUCCESS" : "FAILED"), currentVol);
}

bool SoundfontEngine::selectProgram(int channel, int soundFontId, int bank, int preset) {
    if (channel < 0 || channel >= kMaxChannels) return false;
    
    EngineMidiEvent ev;
    ev.type = EngineMidiEvent::PROGRAM_SELECT;
    ev.channel = channel;
    ev.param1 = soundFontId;
    ev.param2 = bank;
    ev.note = preset;
    ev.velocity = 0;
    
    return mEventQueue.push(ev);
}

bool SoundfontEngine::programChange(int channel, int program) {
    if (channel < 0 || channel >= kMaxChannels) return false;
    
    EngineMidiEvent ev;
    ev.type = EngineMidiEvent::PROGRAM_CHANGE;
    ev.channel = channel;
    ev.param1 = program;
    ev.param2 = 0;
    ev.note = 0;
    ev.velocity = 0;
    
    return mEventQueue.push(ev);
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

void SoundfontEngine::panic() {
    EngineMidiEvent ev{};
    ev.type = EngineMidiEvent::PANIC;
    ev.channel = -1;
    ev.note = 0;
    ev.velocity = 0;
    ev.param1 = 0;
    ev.param2 = 0;
    mEventQueue.push(ev);
}

void SoundfontEngine::sendCC(int channel, int cc, int value) {
    EngineMidiEvent ev;
    ev.type = EngineMidiEvent::CC;
    ev.channel = channel;
    ev.param1 = cc;
    ev.param2 = value;
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

    float clampedVol = std::clamp(volume01, 0.0f, 1.0f);
    // FluidSynth natively calculates attenuation using the standard SoundFont 2.04 specification:
    // Attenuation (dB) = 40 * log10(127 / CC7).
    // Mapping directly ensures true full dynamics, punchy transients, and zero volume recession!
    int ccVal = static_cast<int>(std::round(clampedVol * 127.0f));
    ccVal = std::clamp(ccVal, 0, 127);
    mChannelVolumes[channel].store(ccVal, std::memory_order_relaxed);

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
    mConfiguredPolyphony = std::clamp(polyphony, 16, 256);
    if (!mSynth) return;
    fluid_synth_set_polyphony(mSynth, mConfiguredPolyphony);
    int effective = fluid_synth_get_polyphony(mSynth);
    LOGI("[%s] Polyphony updated: requested=%d, effective=%d", mInstanceName.c_str(), mConfiguredPolyphony, effective);
}

int SoundfontEngine::getActiveVoiceCount() const {
    if (!mSynth) return 0;
    return fluid_synth_get_active_voice_count(mSynth);
}

VoiceAuditSnapshot SoundfontEngine::getLatestAuditSnapshot() const {
    VoiceAuditSnapshot snap;
    snap.totalVoices = mAuditTotalVoices.load(std::memory_order_relaxed);
    snap.activeHeldVoices = mAuditActiveHeldVoices.load(std::memory_order_relaxed);
    snap.sustainedVoices = mAuditSustainedVoices.load(std::memory_order_relaxed);
    snap.releaseVoices = mAuditReleaseVoices.load(std::memory_order_relaxed);
    snap.audibleVoices = mAuditAudibleVoices.load(std::memory_order_relaxed);
    snap.silentVoices = mAuditSilentVoices.load(std::memory_order_relaxed);
    snap.voiceSteals = mAuditVoiceSteals.load(std::memory_order_relaxed);
    snap.voicesCreatedTotal = mAuditVoicesCreatedTotal.load(std::memory_order_relaxed);
    snap.voicesFinishedTotal = mAuditVoicesFinishedTotal.load(std::memory_order_relaxed);
    snap.maxVoicesObserved = mAuditMaxVoicesObserved.load(std::memory_order_relaxed);
    snap.renderDurationUs = mAuditLastRenderDurationUs.load(std::memory_order_relaxed);
    return snap;
}

void SoundfontEngine::resetAuditCounters() {
    mAuditTotalVoices.store(0, std::memory_order_relaxed);
    mAuditActiveHeldVoices.store(0, std::memory_order_relaxed);
    mAuditSustainedVoices.store(0, std::memory_order_relaxed);
    mAuditReleaseVoices.store(0, std::memory_order_relaxed);
    mAuditAudibleVoices.store(0, std::memory_order_relaxed);
    mAuditSilentVoices.store(0, std::memory_order_relaxed);
    mAuditVoiceSteals.store(0, std::memory_order_relaxed);
    mAuditVoicesCreatedTotal.store(0, std::memory_order_relaxed);
    mAuditVoicesFinishedTotal.store(0, std::memory_order_relaxed);
    mAuditMaxVoicesObserved.store(0, std::memory_order_relaxed);
    mAuditLastRenderDurationUs.store(0, std::memory_order_relaxed);
}

void SoundfontEngine::drainEvents() {
    // Lock-free drain of pending MIDI events on the audio thread. NoteOns,
    // NoteOffs, PitchBends, CCs and AllNotesOffs are applied with high
    // throughput; up to 48 NoteOns per block so 8-track chords trigger with zero
    // latency, any excess waits for the next block. When a strip has a Source
    // selected the note goes to that source instead of FluidSynth, and the
    // soundfont channel keeps its sustain/CC state but receives no notes.
    int noteOnsProcessed = 0;
    const int kMaxNoteOnPerFrame = 48;
    EngineMidiEvent ev;
    while (mEventQueue.peek(ev)) {
        if (ev.type == EngineMidiEvent::NOTE_ON) {
            if (noteOnsProcessed >= kMaxNoteOnPerFrame) break;
            noteOnsProcessed++;
        }
        mEventQueue.pop(ev);
        const bool routedToSource = mSourceRouter && ev.channel >= 0 && ev.channel < kMaxChannels &&
                                    mSourceRouter(mSourceRouterCtx, ev.channel, ev.type, ev.note,
                                                  ev.velocity, ev.param1, ev.param2);
        switch (ev.type) {
            case EngineMidiEvent::NOTE_ON:
                if (routedToSource) break;
                fluid_synth_noteon(mSynth, ev.channel, ev.note, ev.velocity);
                break;
            case EngineMidiEvent::NOTE_OFF:
                if (routedToSource) break;
                fluid_synth_noteoff(mSynth, ev.channel, ev.note);
                break;
            case EngineMidiEvent::ALL_NOTES_OFF:
                // A source has no sustain pedal in v1, so it is always cleared.
                if (mAllNotesOffHook) mAllNotesOffHook(mAllNotesOffHookCtx, ev.channel);
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
                if (routedToSource) break;
                fluid_synth_pitch_bend(mSynth, ev.channel, ev.param1);
                break;
            case EngineMidiEvent::CC:
                fluid_synth_cc(mSynth, ev.channel, ev.param1, ev.param2);
                break;
            case EngineMidiEvent::PROGRAM_CHANGE:
                fluid_synth_program_change(mSynth, ev.channel, ev.param1);
                break;
            case EngineMidiEvent::PROGRAM_SELECT:
                executeProgramSelect(ev.channel, ev.param1, ev.param2, ev.note);
                break;
            case EngineMidiEvent::SET_GEN:
                // param1 = generator id, param2 = value (already scaled to native
                // units by the shaper on the control thread).
                fluid_synth_set_gen(mSynth, ev.channel, ev.param1, static_cast<float>(ev.param2));
                break;
            case EngineMidiEvent::PITCH_SENS:
                fluid_synth_pitch_wheel_sens(mSynth, ev.channel, ev.param1);
                break;
            case EngineMidiEvent::CHANNEL_PRESSURE:
                fluid_synth_channel_pressure(mSynth, ev.channel, ev.param1);
                break;
            case EngineMidiEvent::PANIC:
                // Notes first, then the reverb/chorus tail. fluid_synth_all_notes_off
                // only releases the voices, so the synth reverb kept ringing for
                // seconds after a panic; resetting the reverb engine flushes it. Done
                // on the audio thread, after every note event already queued.
                if (mAllNotesOffHook) mAllNotesOffHook(mAllNotesOffHookCtx, -1);
                for (int ch = 0; ch < kMaxChannels; ++ch) {
                    fluid_synth_all_notes_off(mSynth, ch);
                    fluid_synth_cc(mSynth, ch, 123, 0);
                    fluid_synth_cc(mSynth, ch, 120, 0);
                }
                fluid_synth_reset_reverb(mSynth);
                fluid_synth_reset_chorus(mSynth);
                break;
        }
    }
}

void SoundfontEngine::renderStereo(float *outputBuffer, int32_t numFrames, bool accumulate) {
    std::unique_lock<std::recursive_mutex> lock(mMutex, std::try_to_lock);
    if (!lock.owns_lock() || !mSynth) {
        if (!accumulate) {
            std::fill(outputBuffer, outputBuffer + (numFrames * 2), 0.0f);
        }
        return;
    }

    int sfCount = fluid_synth_sfcount(mSynth);
    if (sfCount <= 0) {
        if (!accumulate) {
            std::fill(outputBuffer, outputBuffer + (numFrames * 2), 0.0f);
        }
        return;
    }

    auto tStart = std::chrono::steady_clock::now();

    drainEvents();

    // In multi-channel mode FluidSynth sends each MIDI channel to its own audio
    // group (`group = channel % audio_groups`, see fluid_voice.c). But
    // fluid_synth_write_float() is a *stereo* writer: it copies group 0 only. Used
    // here it emitted MIDI channel 0 and nothing else, so with the FX path idle
    // every other track, the Drum channel and the TonicPad were silent — the
    // "only track 1 makes sound" report. Render every group and fold them into one
    // stereo pair instead. The fold reads the planar scratch directly, so it costs
    // one pass per sample and never re-enters the interleaving the FX path needs.
    if (mMultiChannelEnabled && renderPlanar(numFrames, kAudioGroups)) {
        const size_t totalSamples = static_cast<size_t>(numFrames) * 2;
        if (mStereoFold.size() < totalSamples) mStereoFold.resize(totalSamples, 0.0f);
        float *fold = mStereoFold.data();
        std::fill(fold, fold + totalSamples, 0.0f);
        for (int ch = 0; ch < kAudioGroups; ++ch) {
            const float *srcL = mChannelPtrs[2 * ch];
            const float *srcR = mChannelPtrs[2 * ch + 1];
            for (int i = 0; i < numFrames; ++i) {
                fold[2 * i] += srcL[i];
                fold[2 * i + 1] += srcR[i];
            }
        }
        if (accumulate) {
            for (size_t i = 0; i < totalSamples; ++i) outputBuffer[i] += fold[i];
        } else {
            std::copy(fold, fold + totalSamples, outputBuffer);
        }
    } else if (!accumulate) {
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

    auto tEnd = std::chrono::steady_clock::now();
    int renderUs = static_cast<int>(std::chrono::duration_cast<std::chrono::microseconds>(tEnd - tStart).count());
    mAuditLastRenderDurationUs.store(renderUs, std::memory_order_relaxed);

    // Update voice count via lightweight O(1) getter - never traverse voice list in audio loop
    int activeCount = fluid_synth_get_active_voice_count(mSynth);
    mAuditTotalVoices.store(activeCount, std::memory_order_relaxed);
    mAuditAudibleVoices.store(activeCount, std::memory_order_relaxed);

    int prevMax = mAuditMaxVoicesObserved.load(std::memory_order_relaxed);
    if (activeCount > prevMax) {
        mAuditMaxVoicesObserved.store(activeCount, std::memory_order_relaxed);
    }
    if (activeCount >= mConfiguredPolyphony) {
        mAuditVoiceSteals.fetch_add(1, std::memory_order_relaxed);
    }
}
