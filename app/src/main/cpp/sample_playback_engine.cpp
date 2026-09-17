#include "sample_playback_engine.h"
#include <android/log.h>
#include <fstream>
#include <cstring>
#include <chrono>
#include <random>

#define TAG "SamplePlaybackEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

SamplePlaybackEngine::SamplePlaybackEngine() {
    for (auto& slot : mFastSampleLookup) {
        slot.store(nullptr, std::memory_order_relaxed);
    }
}

SamplePlaybackEngine::~SamplePlaybackEngine() {
    unloadAllSamples();
}

bool SamplePlaybackEngine::init(int sampleRate) {
    mSamplesReady.store(false, std::memory_order_relaxed);
    mSampleRate = sampleRate > 0 ? sampleRate : kDefaultSampleRate;

    for (size_t i = 0; i < kMaxVoices; ++i) {
        mVoices[i].active = false;
        mVoices[i].pcmData16 = nullptr;
        mVoices[i].readPosition = 0.0;
        mVoices[i].totalFrames = 0;
    }

    mCommandQueue.clear();
    mActiveVoiceCount.store(0, std::memory_order_relaxed);

    // Populate built-in studio drum kit samples
    initDefaultDrumKit();

    LOGI("SamplePlaybackEngine initialized at %d Hz with %zu voices", mSampleRate, kMaxVoices);
    return true;
}

void SamplePlaybackEngine::initDefaultDrumKit() {
    // Generate synthetic studio drum waveforms (Kick, Snare, Closed Hat, Open Hat, Clap, Low Tom, Mid Tom, High Tom, Crash, Ride, Shaker, Rimshot)
    const float sr = static_cast<float>(mSampleRate);

    // Pad 1: Punchy Deep Kick (id: 1)
    {
        size_t len = static_cast<size_t>(sr * 0.35f); // 350ms
        std::vector<float> kick(len * 2, 0.0f);
        float phase = 0.0f;
        for (size_t i = 0; i < len; ++i) {
            float t = static_cast<float>(i) / sr;
            float freq = 160.0f * std::exp(-t * 26.0f) + 45.0f;
            phase += 2.0f * static_cast<float>(M_PI) * freq / sr;
            float amp = std::exp(-t * 12.0f);
            float click = (i < static_cast<size_t>(sr * 0.005f)) ? (1.0f - static_cast<float>(i) / (sr * 0.005f)) * 0.4f : 0.0f;
            float s = (std::sin(phase) + click) * amp;
            kick[i * 2] = s;
            kick[i * 2 + 1] = s;
        }
        registerSamplePcm(1, "Deep Kick", kick.data(), len, mSampleRate);
    }

    // Pad 2: Crisp Acoustic Snare (id: 2)
    {
        size_t len = static_cast<size_t>(sr * 0.28f);
        std::vector<float> snare(len * 2, 0.0f);
        std::mt19937 rng(42);
        std::uniform_real_distribution<float> dist(-1.0f, 1.0f);
        float phase = 0.0f;
        for (size_t i = 0; i < len; ++i) {
            float t = static_cast<float>(i) / sr;
            float toneFreq = 190.0f * std::exp(-t * 20.0f);
            phase += 2.0f * static_cast<float>(M_PI) * toneFreq / sr;
            float tone = std::sin(phase) * std::exp(-t * 22.0f) * 0.5f;
            float noise = dist(rng) * std::exp(-t * 16.0f) * 0.7f;
            float s = (tone + noise);
            snare[i * 2] = s;
            snare[i * 2 + 1] = s;
        }
        registerSamplePcm(2, "Crisp Snare", snare.data(), len, mSampleRate);
    }

    // Pad 3: Closed Hi-Hat (id: 3)
    {
        size_t len = static_cast<size_t>(sr * 0.08f);
        std::vector<float> hat(len * 2, 0.0f);
        std::mt19937 rng(1337);
        std::uniform_real_distribution<float> dist(-1.0f, 1.0f);
        float lastVal = 0.0f;
        for (size_t i = 0; i < len; ++i) {
            float t = static_cast<float>(i) / sr;
            float n = dist(rng);
            float hp = n - lastVal; // Highpass filter
            lastVal = n;
            float s = hp * std::exp(-t * 55.0f) * 0.65f;
            hat[i * 2] = s;
            hat[i * 2 + 1] = s;
        }
        registerSamplePcm(3, "Closed Hat", hat.data(), len, mSampleRate);
    }

    // Pad 4: Open Hi-Hat (id: 4)
    {
        size_t len = static_cast<size_t>(sr * 0.45f);
        std::vector<float> openHat(len * 2, 0.0f);
        std::mt19937 rng(2024);
        std::uniform_real_distribution<float> dist(-1.0f, 1.0f);
        float lastVal = 0.0f;
        for (size_t i = 0; i < len; ++i) {
            float t = static_cast<float>(i) / sr;
            float n = dist(rng);
            float hp = n - lastVal * 0.8f;
            lastVal = n;
            float s = hp * std::exp(-t * 9.0f) * 0.55f;
            openHat[i * 2] = s;
            openHat[i * 2 + 1] = s;
        }
        registerSamplePcm(4, "Open Hat", openHat.data(), len, mSampleRate);
    }

    // Pad 5: Hand Clap (id: 5)
    {
        size_t len = static_cast<size_t>(sr * 0.30f);
        std::vector<float> clap(len * 2, 0.0f);
        std::mt19937 rng(999);
        std::uniform_real_distribution<float> dist(-1.0f, 1.0f);
        for (size_t i = 0; i < len; ++i) {
            float t = static_cast<float>(i) / sr;
            float env = 0.0f;
            // 3 quick pre-bursts + main body
            if (t < 0.010f) env = std::exp(-t * 150.0f) * 0.7f;
            else if (t < 0.022f) env = std::exp(-(t - 0.010f) * 150.0f) * 0.8f;
            else if (t < 0.035f) env = std::exp(-(t - 0.022f) * 150.0f) * 0.9f;
            else env = std::exp(-(t - 0.035f) * 18.0f) * 1.0f;

            float s = dist(rng) * env * 0.7f;
            clap[i * 2] = s;
            clap[i * 2 + 1] = s;
        }
        registerSamplePcm(5, "Studio Clap", clap.data(), len, mSampleRate);
    }

    // Pad 6: Low Tom (id: 6)
    {
        size_t len = static_cast<size_t>(sr * 0.40f);
        std::vector<float> tom(len * 2, 0.0f);
        float phase = 0.0f;
        for (size_t i = 0; i < len; ++i) {
            float t = static_cast<float>(i) / sr;
            float freq = 120.0f * std::exp(-t * 14.0f) + 65.0f;
            phase += 2.0f * static_cast<float>(M_PI) * freq / sr;
            float s = std::sin(phase) * std::exp(-t * 8.0f) * 0.75f;
            tom[i * 2] = s;
            tom[i * 2 + 1] = s;
        }
        registerSamplePcm(6, "Low Tom", tom.data(), len, mSampleRate);
    }

    // Pad 7: Mid Tom (id: 7)
    {
        size_t len = static_cast<size_t>(sr * 0.35f);
        std::vector<float> midTom(len * 2, 0.0f);
        float phase = 0.0f;
        for (size_t i = 0; i < len; ++i) {
            float t = static_cast<float>(i) / sr;
            float freq = 170.0f * std::exp(-t * 16.0f) + 95.0f;
            phase += 2.0f * static_cast<float>(M_PI) * freq / sr;
            float s = std::sin(phase) * std::exp(-t * 10.0f) * 0.75f;
            midTom[i * 2] = s;
            midTom[i * 2 + 1] = s;
        }
        registerSamplePcm(7, "Mid Tom", midTom.data(), len, mSampleRate);
    }

    // Pad 8: Crash Cymbal (id: 8)
    {
        size_t len = static_cast<size_t>(sr * 0.85f);
        std::vector<float> crash(len * 2, 0.0f);
        std::mt19937 rng(777);
        std::uniform_real_distribution<float> dist(-1.0f, 1.0f);
        float lastVal = 0.0f;
        for (size_t i = 0; i < len; ++i) {
            float t = static_cast<float>(i) / sr;
            float n = dist(rng);
            float hp = n - lastVal * 0.7f;
            lastVal = n;
            float s = hp * std::exp(-t * 4.5f) * 0.5f;
            crash[i * 2] = s;
            crash[i * 2 + 1] = s;
        }
        registerSamplePcm(8, "Crash Cymbal", crash.data(), len, mSampleRate);
    }

    mSamplesReady.store(true, std::memory_order_release);
}

int SamplePlaybackEngine::registerSamplePcm(
    int sampleId,
    const std::string& name,
    const float* interleavedStereo,
    size_t totalFrames,
    int sampleRate) {

    if (!interleavedStereo || totalFrames == 0 || sampleId <= 0) return -1;

    auto sample = std::make_shared<AudioSample>();
    sample->id = sampleId;
    sample->name = name;
    sample->totalFrames = totalFrames;
    sample->channels = 2;
    sample->sampleRate = sampleRate;
    
    // Store as 16-bit PCM (Halves RAM usage compared to float32)
    sample->pcmData16.resize(totalFrames * 2);
    for (size_t i = 0; i < totalFrames * 2; ++i) {
        float val = interleavedStereo[i];
        int32_t s16 = static_cast<int32_t>(std::clamp(val, -1.0f, 1.0f) * 32767.0f);
        sample->pcmData16[i] = static_cast<int16_t>(s16);
    }

    {
        std::lock_guard<std::mutex> lock(mSampleMutex);
        mSamples[sampleId] = sample;
    }

    if (sampleId < kMaxFastSampleId) {
        mFastSampleLookup[sampleId].store(sample.get(), std::memory_order_release);
    }

    LOGI("Registered PCM16 sample ID: %d (%s, %zu frames, %d Hz, RAM footprint: %zu KB)",
         sampleId, name.c_str(), totalFrames, sampleRate, (totalFrames * 2 * sizeof(int16_t)) / 1024);
    return sampleId;
}

int SamplePlaybackEngine::loadWavFromMemory(int sampleId, const std::string& name, const uint8_t* wavBytes, size_t byteCount) {
    if (!wavBytes || byteCount < 44 || sampleId <= 0) return -1;

    // Standard RIFF WAVE header validation
    if (std::memcmp(wavBytes, "RIFF", 4) != 0 || std::memcmp(wavBytes + 8, "WAVE", 4) != 0) {
        LOGE("Invalid RIFF/WAVE header for sample ID %d", sampleId);
        return -1;
    }

    size_t offset = 12;
    uint16_t audioFormat = 1;
    uint16_t numChannels = 2;
    uint32_t sampleRate = 44100;
    int bitsPerSample = 16;
    const uint8_t* dataPtr = nullptr;
    size_t dataSize = 0;

    while (offset + 8 <= byteCount) {
        char chunkId[5] = {0};
        std::memcpy(chunkId, wavBytes + offset, 4);
        uint32_t chunkSize = *reinterpret_cast<const uint32_t*>(wavBytes + offset + 4);
        offset += 8;

        if (std::strcmp(chunkId, "fmt ") == 0 && chunkSize >= 16) {
            audioFormat = *reinterpret_cast<const uint16_t*>(wavBytes + offset);
            numChannels = *reinterpret_cast<const uint16_t*>(wavBytes + offset + 2);
            sampleRate = *reinterpret_cast<const uint32_t*>(wavBytes + offset + 4);
            bitsPerSample = *reinterpret_cast<const uint16_t*>(wavBytes + offset + 14);
        } else if (std::strcmp(chunkId, "data") == 0) {
            dataPtr = wavBytes + offset;
            dataSize = std::min(static_cast<size_t>(chunkSize), byteCount - offset);
            break;
        }
        offset += chunkSize;
    }

    if (!dataPtr || dataSize == 0 || numChannels < 1) {
        LOGE("Could not locate audio data chunk in WAV for sample %d", sampleId);
        return -1;
    }

    size_t bytesPerSample = bitsPerSample / 8;
    size_t numFrames = dataSize / (numChannels * bytesPerSample);
    
    auto sample = std::make_shared<AudioSample>();
    sample->id = sampleId;
    sample->name = name;
    sample->totalFrames = numFrames;
    sample->channels = 2;
    sample->sampleRate = sampleRate;
    sample->pcmData16.resize(numFrames * 2);

    for (size_t f = 0; f < numFrames; ++f) {
        int16_t left16 = 0;
        int16_t right16 = 0;

        if (audioFormat == 1 && bitsPerSample == 16) {
            const int16_t* s16 = reinterpret_cast<const int16_t*>(dataPtr + f * numChannels * 2);
            left16 = s16[0];
            right16 = (numChannels > 1) ? s16[1] : left16;
        } else if (audioFormat == 1 && bitsPerSample == 24) {
            const uint8_t* s24 = dataPtr + f * numChannels * 3;
            int32_t valL = (s24[0] << 8) | (s24[1] << 16) | (s24[2] << 24);
            left16 = static_cast<int16_t>(valL >> 16);
            if (numChannels > 1) {
                int32_t valR = (s24[3] << 8) | (s24[4] << 16) | (s24[5] << 24);
                right16 = static_cast<int16_t>(valR >> 16);
            } else {
                right16 = left16;
            }
        } else if (audioFormat == 3 && bitsPerSample == 32) {
            const float* s32 = reinterpret_cast<const float*>(dataPtr + f * numChannels * 4);
            left16 = static_cast<int16_t>(std::clamp(s32[0], -1.0f, 1.0f) * 32767.0f);
            right16 = (numChannels > 1) ? static_cast<int16_t>(std::clamp(s32[1], -1.0f, 1.0f) * 32767.0f) : left16;
        }

        sample->pcmData16[f * 2] = left16;
        sample->pcmData16[f * 2 + 1] = right16;
    }

    {
        std::lock_guard<std::mutex> lock(mSampleMutex);
        mSamples[sampleId] = sample;
    }

    if (sampleId < kMaxFastSampleId) {
        mFastSampleLookup[sampleId].store(sample.get(), std::memory_order_release);
    }

    return sampleId;
}

int SamplePlaybackEngine::loadWavFile(int sampleId, const std::string& filePath) {
    std::ifstream file(filePath, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("Failed to open WAV file on path: %s", filePath.c_str());
        return -1;
    }

    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);

    std::vector<uint8_t> buffer(size);
    if (!file.read(reinterpret_cast<char*>(buffer.data()), size)) {
        LOGE("Failed to read WAV file: %s", filePath.c_str());
        return -1;
    }

    return loadWavFromMemory(sampleId, filePath, buffer.data(), buffer.size());
}

void SamplePlaybackEngine::unloadSample(int sampleId) {
    if (sampleId <= 0) return;

    if (sampleId < kMaxFastSampleId) {
        mFastSampleLookup[sampleId].store(nullptr, std::memory_order_release);
    }

    std::lock_guard<std::mutex> lock(mSampleMutex);
    mSamples.erase(sampleId);
}

void SamplePlaybackEngine::unloadAllSamples() {
    for (auto& slot : mFastSampleLookup) {
        slot.store(nullptr, std::memory_order_release);
    }

    std::lock_guard<std::mutex> lock(mSampleMutex);
    mSamples.clear();
}

void SamplePlaybackEngine::triggerSample(int sampleId, float velocity01, float pan) {
    mTriggersReceived.fetch_add(1, std::memory_order_relaxed);

    SamplerCommand cmd{};
    cmd.type = CommandType::TRIGGER;
    cmd.sampleId = sampleId;
    cmd.velocity = std::clamp(velocity01, 0.01f, 1.0f);
    cmd.pan = std::clamp(pan, -1.0f, 1.0f);
    cmd.volume = 1.0f;

    mCommandQueue.push(cmd);
}

void SamplePlaybackEngine::stopSample(int sampleId) {
    SamplerCommand cmd{};
    cmd.type = CommandType::STOP;
    cmd.sampleId = sampleId;
    mCommandQueue.push(cmd);
}

void SamplePlaybackEngine::stopAll() {
    SamplerCommand cmd{};
    cmd.type = CommandType::STOP_ALL;
    mCommandQueue.push(cmd);
}

void SamplePlaybackEngine::setMasterVolume(float volume01) {
    mMasterVolume.store(std::clamp(volume01, 0.0f, 2.0f), std::memory_order_relaxed);
}

void SamplePlaybackEngine::setMasterPan(float pan) {
    mMasterPan.store(std::clamp(pan, -1.0f, 1.0f), std::memory_order_relaxed);
}

void SamplePlaybackEngine::setChokeGroup(int sampleId, int chokeGroup) {
    std::lock_guard<std::mutex> lock(mSampleMutex);
    auto it = mSamples.find(sampleId);
    if (it != mSamples.end()) {
        it->second->chokeGroup = chokeGroup;
    }
}

int SamplePlaybackEngine::allocateVoice(int sampleId) {
    // 1. Priority: Find inactive voice slot
    for (size_t i = 0; i < kMaxVoices; ++i) {
        if (!mVoices[i].active) {
            return static_cast<int>(i);
        }
    }

    // 2. Voice Stealing: Find voice with least remaining duration or oldest
    mVoiceSteals.fetch_add(1, std::memory_order_relaxed);
    size_t bestIdx = 0;
    double maxProgress = 0.0;

    for (size_t i = 0; i < kMaxVoices; ++i) {
        if (mVoices[i].totalFrames > 0) {
            double progress = mVoices[i].readPosition / static_cast<double>(mVoices[i].totalFrames);
            if (progress > maxProgress) {
                maxProgress = progress;
                bestIdx = i;
            }
        }
    }

    return static_cast<int>(bestIdx);
}

void SamplePlaybackEngine::processCommands() {
    SamplerCommand cmd{};
    while (mCommandQueue.pop(cmd)) {
        switch (cmd.type) {
            case CommandType::TRIGGER: {
                const AudioSample* sample = nullptr;
                if (cmd.sampleId > 0 && cmd.sampleId < kMaxFastSampleId) {
                    sample = mFastSampleLookup[cmd.sampleId].load(std::memory_order_acquire);
                }

                if (sample && sample->totalFrames > 0 && !sample->pcmData16.empty()) {
                    // Choke group handling: choke all other voices currently playing in the same group (e.g., Closed Hat chokes Open Hat)
                    if (sample->chokeGroup > 0) {
                        for (size_t i = 0; i < kMaxVoices; ++i) {
                            if (mVoices[i].active && mVoices[i].chokeGroup == sample->chokeGroup) {
                                mVoices[i].isFadingOut = true;
                            }
                        }
                    }

                    int voiceIdx = allocateVoice(cmd.sampleId);
                    if (voiceIdx >= 0 && voiceIdx < static_cast<int>(kMaxVoices)) {
                        SamplerVoice& v = mVoices[voiceIdx];
                        v.sampleId = cmd.sampleId;
                        v.chokeGroup = sample->chokeGroup;
                        v.pcmData16 = sample->pcmData16.data();
                        v.totalFrames = sample->totalFrames;
                        v.readPosition = 0.0;
                        v.pitchRatio = static_cast<double>(sample->sampleRate) / static_cast<double>(mSampleRate > 0 ? mSampleRate : kDefaultSampleRate);
                        v.triggerTimestamp = ++mTimestampCounter;
                        v.isFadingOut = false;
                        v.fadeMultiplier = 1.0f;

                        // Constant-power panning law
                        float panNorm = (cmd.pan + 1.0f) * 0.5f;
                        float leftPan = std::cos(panNorm * static_cast<float>(M_PI_2));
                        float rightPan = std::sin(panNorm * static_cast<float>(M_PI_2));

                        v.gainLeft = cmd.velocity * leftPan;
                        v.gainRight = cmd.velocity * rightPan;
                        v.active = true;

                        mTriggersExecuted.fetch_add(1, std::memory_order_relaxed);
                    }
                }
                break;
            }
            case CommandType::STOP: {
                for (size_t i = 0; i < kMaxVoices; ++i) {
                    if (mVoices[i].active && mVoices[i].sampleId == cmd.sampleId) {
                        mVoices[i].active = false;
                    }
                }
                break;
            }
            case CommandType::STOP_ALL: {
                for (size_t i = 0; i < kMaxVoices; ++i) {
                    mVoices[i].active = false;
                }
                break;
            }
            case CommandType::SET_VOLUME:
                setMasterVolume(cmd.volume);
                break;
            case CommandType::SET_PAN:
                setMasterPan(cmd.pan);
                break;
            case CommandType::SET_CHOKE_GROUP:
                setChokeGroup(cmd.sampleId, cmd.chokeGroup);
                break;
        }
    }
}

void SamplePlaybackEngine::renderStereo(float* outputBuffer, int32_t numFrames, bool accumulate) {
    if (!outputBuffer || numFrames <= 0) return;

    if (!mSamplesReady.load(std::memory_order_acquire)) {
        if (!accumulate) {
            std::fill(outputBuffer, outputBuffer + (numFrames * 2), 0.0f);
        }
        return;
    }

    auto startTime = std::chrono::high_resolution_clock::now();

    // 1. Process pending triggers lock-free
    processCommands();

    size_t totalSamples = static_cast<size_t>(numFrames * 2);
    if (!accumulate) {
        std::fill(outputBuffer, outputBuffer + totalSamples, 0.0f);
    }

    float masterVol = mMasterVolume.load(std::memory_order_relaxed);
    float masterPan = mMasterPan.load(std::memory_order_relaxed);
    float masterPanNorm = (masterPan + 1.0f) * 0.5f;
    float masterGainL = masterVol * std::cos(masterPanNorm * static_cast<float>(M_PI_2));
    float masterGainR = masterVol * std::sin(masterPanNorm * static_cast<float>(M_PI_2));

    int activeCount = 0;
    const float kFadeStep = 1.0f / (mSampleRate * 0.005f); // 5ms micro-fade for choked voices

    // 2. Render all active voices directly into the stereo output stream
    for (size_t vIdx = 0; vIdx < kMaxVoices; ++vIdx) {
        SamplerVoice& v = mVoices[vIdx];
        if (!v.active || !v.pcmData16 || v.totalFrames == 0) continue;

        activeCount++;
        float gainL = v.gainLeft * masterGainL;
        float gainR = v.gainRight * masterGainR;
        const int16_t* pcm16 = v.pcmData16;
        size_t totalFrames = v.totalFrames;
        double pos = v.readPosition;
        double pitch = v.pitchRatio;

        for (int32_t f = 0; f < numFrames; ++f) {
            if (v.isFadingOut) {
                v.fadeMultiplier -= kFadeStep;
                if (v.fadeMultiplier <= 0.0f) {
                    v.active = false;
                    break;
                }
            }

            size_t frameIdx = static_cast<size_t>(pos);
            if (frameIdx >= totalFrames) {
                v.active = false;
                break;
            }

            // High-speed Linear Interpolation on 16-bit PCM (scaled to float / 32768.0f)
            double frac = pos - static_cast<double>(frameIdx);
            size_t nextIdx = std::min(frameIdx + 1, totalFrames - 1);

            float s0L = pcm16[frameIdx * 2] / 32768.0f;
            float s0R = pcm16[frameIdx * 2 + 1] / 32768.0f;
            float s1L = pcm16[nextIdx * 2] / 32768.0f;
            float s1R = pcm16[nextIdx * 2 + 1] / 32768.0f;

            float sampL = static_cast<float>(s0L + frac * (s1L - s0L)) * v.fadeMultiplier;
            float sampR = static_cast<float>(s0R + frac * (s1R - s0R)) * v.fadeMultiplier;

            outputBuffer[f * 2] += sampL * gainL;
            outputBuffer[f * 2 + 1] += sampR * gainR;

            pos += pitch;
        }

        v.readPosition = pos;
    }

    mActiveVoiceCount.store(activeCount, std::memory_order_relaxed);

    auto endTime = std::chrono::high_resolution_clock::now();
    auto durationUs = std::chrono::duration_cast<std::chrono::microseconds>(endTime - startTime).count();
    mLastRenderDurationUs.store(durationUs, std::memory_order_relaxed);
}
