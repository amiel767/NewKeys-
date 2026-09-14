#include "audio_engine.h"
#include <android/log.h>
#include <thread>
#include <chrono>
#include <pthread.h>
#include <sched.h>
#include <cmath>

#define TAG "AudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#if HAS_OBOE

AudioEngine::AudioEngine() = default;

AudioEngine::~AudioEngine() {
    stop();
}

bool AudioEngine::start(int driverType) {
    mDriverType = driverType;
    return openAndStartStream();
}

bool AudioEngine::openAndStartStream() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (mStream) {
        mStream->stop();
        mStream->close();
        mStream.reset();
    }

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(48000)
        ->setDataCallback(this)
        ->setErrorCallback(this)
        ->setUsage(oboe::Usage::Media);

    if (mDriverType == 1) {
        LOGI("Requesting OpenSL ES audio backend...");
        builder.setAudioApi(oboe::AudioApi::OpenSLES);
    } else {
        LOGI("Requesting Oboe High-Performance (AAudio Exclusive) backend...");
        builder.setAudioApi(oboe::AudioApi::AAudio);
    }

    oboe::Result result = builder.openStream(mStream);
    if (result != oboe::Result::OK) {
        LOGW("Failed to open Exclusive Float audio stream (%s). Retrying with SharingMode::Shared...", oboe::convertToText(result));
        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(mStream);
    }

    if (result != oboe::Result::OK) {
        LOGW("Failed to open Float audio stream (%s). Retrying with I16 format...", oboe::convertToText(result));
        builder.setFormat(oboe::AudioFormat::I16);
        builder.setAudioApi(oboe::AudioApi::OpenSLES);
        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(mStream);
        if (result != oboe::Result::OK) {
            LOGW("Retrying with Unspecified API and Unspecified format...");
            builder.setAudioApi(oboe::AudioApi::Unspecified);
            builder.setFormat(oboe::AudioFormat::Unspecified);
            builder.setPerformanceMode(oboe::PerformanceMode::None);
            result = builder.openStream(mStream);
            if (result != oboe::Result::OK) {
                LOGE("Failed to open audio stream fallback: %s", oboe::convertToText(result));
                return false;
            }
        }
    }

    int burst = mStream->getFramesPerBurst();
    int reqBuffer = mConfiguredBufferSize.load(std::memory_order_relaxed);
    int targetBuffer = (burst > 0) ? std::max(reqBuffer, burst * 2) : reqBuffer;
    mStream->setBufferSizeInFrames(targetBuffer);
    int sampleRate = mStream->getSampleRate();
    mSampleRate = sampleRate;

    LOGI("Audio stream opened: %d Hz, %d frames/burst, buffer set to %d frames (requested: %d).", 
        sampleRate, burst, targetBuffer, reqBuffer);

    // Initialize all DSP blocks with active sample rate
    mMasterDelay.init(sampleRate);
    mMasterReverb.init(sampleRate);
    mSoundGoodizer.init(sampleRate);
    mMasterPunch.init(sampleRate);
    mPadFilter.setLowPass(static_cast<float>(sampleRate), 400.0f * std::pow(45.0f, mPadBrightness), 0.707f);
    mDrumSampler.init(sampleRate);

    // Initialize unified FluidSynth engine (16 MIDI channels covering Tracks 1..8, Drum 8, TonicPad 9)
    if (!mSynthEngine.isInitialized()) {
        mSynthEngine.init(sampleRate, kFaderPolyphony, "UnifiedSynthEngine");
    }

    result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start Oboe audio stream: %s", oboe::convertToText(result));
        return false;
    }

    LOGI("Oboe audio engine running with driver mode: %d and realtime DSP active", mDriverType);
    return true;
}

void AudioEngine::onErrorBeforeClose(oboe::AudioStream *audioStream, oboe::Result error) {
    LOGW("Oboe onErrorBeforeClose: %s", oboe::convertToText(error));
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) {
    LOGI("Oboe stream error/disconnected: %s. Reopening stream asynchronously...", 
        oboe::convertToText(error));
    std::thread([this]() {
        std::this_thread::sleep_for(std::chrono::milliseconds(200));
        openAndStartStream();
    }).detach();
}

void AudioEngine::stop() {
    {
        std::lock_guard<std::mutex> lock(mStreamMutex);
        if (mStream) {
            mStream->stop();
            mStream->close();
            mStream.reset();
        }
    }
    mSynthEngine.destroy();
    LOGI("Audio engine stopped and unified synth instance destroyed");
}

void AudioEngine::setDriver(int driverType) {
    if (mDriverType != driverType) {
        LOGI("Switching audio driver from %d to %d", mDriverType, driverType);
        mDriverType = driverType;
        openAndStartStream();
    }
}

void AudioEngine::setBufferSize(int bufferSizeInFrames) {
    int clampedReq = std::clamp(bufferSizeInFrames, 64, 4096);
    mConfiguredBufferSize.store(clampedReq, std::memory_order_relaxed);
    std::lock_guard<std::mutex> streamLock(mStreamMutex);
    if (mStream) {
        int32_t burst = mStream->getFramesPerBurst();
        int32_t targetFrames = clampedReq;
        if (burst > 0) {
            int32_t numBursts = std::max(2, (targetFrames + burst - 1) / burst);
            targetFrames = numBursts * burst;
        }
        int clamped = std::clamp(targetFrames, 64, 4096);
        auto res = mStream->setBufferSizeInFrames(clamped);
        if (res) {
            LOGI("Oboe buffer size set to %d frames (burst: %d, actual: %d, requested: %d)", clamped, burst, res.value(), clampedReq);
        } else {
            LOGE("Failed to set Oboe buffer size: %s", oboe::convertToText(res.error()));
        }
    }
}

void AudioEngine::setMasterEq(float lowGainDb, float midGainDb, float highGainDb) {
    float sr = static_cast<float>(mSampleRate > 0 ? mSampleRate : 48000);
    mEqLow.setLowShelf(sr, 150.0f, lowGainDb);
    mEqMid.setPeaking(sr, 1000.0f, midGainDb);
    mEqHigh.setHighShelf(sr, 6000.0f, highGainDb);
}

void AudioEngine::setSoundGoodizer(bool enabled, int mode, float amount) {
    mSoundGoodizer.setParams(enabled, mode, amount);
}

void AudioEngine::setMasterReverb(bool enabled, float size, float decay, float damp, float mix) {
    mMasterReverb.setParams(enabled, size, decay, damp, mix);
}

void AudioEngine::setMasterDelay(bool enabled, float timeSec, float feedback, float mix, bool pingPong) {
    mMasterDelay.setParams(enabled, timeSec, feedback, mix, pingPong);
}

void AudioEngine::setSpatialWidener(float amount) {
    mSpatialWidener.setAmount(amount);
}

void AudioEngine::setMasterPunch(float amount) {
    mMasterPunch.setAmount(amount);
}

void AudioEngine::setPadBrightness(float brightness) {
    mPadBrightness = std::clamp(brightness, 0.0f, 1.0f);
    float sr = static_cast<float>(mSampleRate > 0 ? mSampleRate : 48000);
    float f0 = 400.0f * std::pow(45.0f, mPadBrightness);
    mPadFilter.setLowPass(sr, f0, 0.707f);
    
    // MIDI CC 74 (Sound Timbre / Filter Cutoff) directly sent to Tonic Pad channel 9
    int cc74Val = static_cast<int>(mPadBrightness * 127.0f);
    mSynthEngine.sendCC(9, 74, cc74Val);
}

bool AudioEngine::hasActiveSoundFonts() const {
    return mSynthEngine.isInitialized();
}

int AudioEngine::renderDirect(int16_t *outputBuffer16, int32_t numFrames) {
    if (!outputBuffer16 || numFrames <= 0) return 0;

    // If Oboe is actively running, let Oboe handle audio directly to hardware
    if (isOboeActive()) {
        return 0;
    }

    std::lock_guard<std::mutex> lock(mRenderMutex);

    // Ensure sample rate and DSP are initialized
    int sampleRate = mSampleRate > 0 ? mSampleRate : 48000;
    if (!mSynthEngine.isInitialized()) {
        mSynthEngine.init(sampleRate);
    }

    size_t totalSamples = static_cast<size_t>(numFrames * 2);
    if (mFloatRenderBuffer.size() < totalSamples) {
        mFloatRenderBuffer.resize(totalSamples, 0.0f);
    }
    float *floatBuf = mFloatRenderBuffer.data();

    // 1. Render unified 16-channel SoundFont engine (Faders 0..7, Drum 8, TonicPad 9)
    mSynthEngine.renderStereo(floatBuf, numFrames, false);

    // 2. Mix Dedicated SamplePlaybackEngine (DrumPad PCM WAVs)
    mDrumSampler.renderStereo(floatBuf, numFrames, true);

    if (!mBypassMasterFX.load(std::memory_order_relaxed)) {
        float maxAbs = 0.0f;
        for (size_t i = 0; i < totalSamples; ++i) {
            float absVal = std::abs(floatBuf[i]);
            if (absVal > maxAbs) maxAbs = absVal;
        }

        // 3. Apply Master Delay
        mMasterDelay.process(floatBuf, numFrames);

        // 4. Apply Master Reverb, SoundGoodizer & Punch (bypassed if silent to save CPU)
        if (maxAbs > 0.0001f) {
            mMasterReverb.process(floatBuf, numFrames);
            mSoundGoodizer.process(floatBuf, numFrames);
            mMasterPunch.process(floatBuf, numFrames);
        }

        // 5. Apply Spatial Stereo Widener
        mSpatialWidener.process(floatBuf, numFrames);

        // 6. Apply Master 3-Band Biquad EQ
        mEqLow.process(floatBuf, numFrames);
        mEqMid.process(floatBuf, numFrames);
        mEqHigh.process(floatBuf, numFrames);

        // 7. Master Studio Peak Limiter, Parallel Compressor & Sub-Bass Cut HPF
        processMasterChain(floatBuf, numFrames);
    }

    // Convert Float to PCM 16-bit
    for (size_t i = 0; i < totalSamples; ++i) {
        float s = std::clamp(floatBuf[i], -1.0f, 1.0f);
        outputBuffer16[i] = static_cast<int16_t>(s * 32767.0f);
    }

    return numFrames;
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream *audioStream,
    void *audioData,
    int32_t numFrames) {

    // Set real-time thread priority on first callback frame to prevent Android Linux kernel from parking on LITTLE cores
    static std::atomic<bool> sPriorityConfigured{false};
    if (!sPriorityConfigured.exchange(true, std::memory_order_relaxed)) {
        struct sched_param param{};
        param.sched_priority = 90;
        if (pthread_setschedparam(pthread_self(), SCHED_FIFO, &param) != 0) {
            param.sched_priority = sched_get_priority_max(SCHED_RR);
            pthread_setschedparam(pthread_self(), SCHED_RR, &param);
            LOGI("Oboe audio thread realtime priority configured with policy SCHED_RR");
        } else {
            LOGI("Oboe audio thread realtime priority configured with policy SCHED_FIFO");
        }
    }

    auto startTime = std::chrono::high_resolution_clock::now();

    mOboeActive.store(true, std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(mRenderMutex);

    oboe::AudioFormat format = audioStream->getFormat();
    if (format == oboe::AudioFormat::I16) {
        auto *outputBuffer16 = static_cast<int16_t *>(audioData);
        size_t totalSamples = static_cast<size_t>(numFrames * 2);
        if (mFloatRenderBuffer.size() < totalSamples) {
            mFloatRenderBuffer.resize(totalSamples, 0.0f);
        }
        float *floatBuf = mFloatRenderBuffer.data();

        // 1. Render unified 16-channel Soundfont engine (tracks 0..7, Drum 8, TonicPad 9)
        mSynthEngine.renderStereo(floatBuf, numFrames, false);

        // 2. Mix Dedicated SamplePlaybackEngine (DrumPad PCM WAVs)
        mDrumSampler.renderStereo(floatBuf, numFrames, true);

        if (!mBypassMasterFX.load(std::memory_order_relaxed)) {
            float maxAbs = 0.0f;
            for (size_t i = 0; i < totalSamples; ++i) {
                float absVal = std::abs(floatBuf[i]);
                if (absVal > maxAbs) maxAbs = absVal;
            }

            // 3. Apply Master Delay
            mMasterDelay.process(floatBuf, numFrames);

            // 4. Apply Master Reverb, SoundGoodizer & Punch (bypassed if silent)
            if (maxAbs > 0.0001f) {
                mMasterReverb.process(floatBuf, numFrames);
                mSoundGoodizer.process(floatBuf, numFrames);
                mMasterPunch.process(floatBuf, numFrames);
            }

            // 5. Apply Spatial Stereo Widener
            mSpatialWidener.process(floatBuf, numFrames);

            // 6. Apply Master 3-Band Biquad EQ
            mEqLow.process(floatBuf, numFrames);
            mEqMid.process(floatBuf, numFrames);
            mEqHigh.process(floatBuf, numFrames);

            // 7. Master Studio Peak Limiter, Parallel Compressor & Sub-Bass Cut HPF
            processMasterChain(floatBuf, numFrames);
        }

        // Convert Float to PCM 16-bit
        for (size_t i = 0; i < totalSamples; ++i) {
            float s = std::clamp(floatBuf[i], -1.0f, 1.0f);
            outputBuffer16[i] = static_cast<int16_t>(s * 32767.0f);
        }
    } else {
        auto *outputBuffer = static_cast<float *>(audioData);
        size_t totalSamples = static_cast<size_t>(numFrames * 2);

        // 1. Render unified 16-channel Soundfont engine (tracks 0..7, Drum 8, TonicPad 9)
        mSynthEngine.renderStereo(outputBuffer, numFrames, false);

        // 2. Mix Dedicated SamplePlaybackEngine (DrumPad PCM WAVs)
        mDrumSampler.renderStereo(outputBuffer, numFrames, true);

        if (!mBypassMasterFX.load(std::memory_order_relaxed)) {
            float maxAbs = 0.0f;
            for (size_t i = 0; i < totalSamples; ++i) {
                float absVal = std::abs(outputBuffer[i]);
                if (absVal > maxAbs) maxAbs = absVal;
            }

            // 3. Apply Master Delay
            mMasterDelay.process(outputBuffer, numFrames);

            // 4. Apply Master Reverb, SoundGoodizer & Punch (bypassed if silent)
            if (maxAbs > 0.0001f) {
                mMasterReverb.process(outputBuffer, numFrames);
                mSoundGoodizer.process(outputBuffer, numFrames);
                mMasterPunch.process(outputBuffer, numFrames);
            }

            // 5. Apply Spatial Stereo Widener
            mSpatialWidener.process(outputBuffer, numFrames);

            // 6. Apply Master 3-Band Biquad EQ (Low Shelf, Mid Peaking, High Shelf)
            mEqLow.process(outputBuffer, numFrames);
            mEqMid.process(outputBuffer, numFrames);
            mEqHigh.process(outputBuffer, numFrames);

            // 7. Master Studio Peak Limiter, Parallel Compressor & Sub-Bass Cut HPF
            processMasterChain(outputBuffer, numFrames);
        }
    }

    auto endTime = std::chrono::high_resolution_clock::now();
    auto durationUs = std::chrono::duration_cast<std::chrono::microseconds>(endTime - startTime).count();

    static int sLogCounter = 0;
    if (++sLogCounter % 500 == 0 || durationUs > 3500) {
        int totalActiveVoices = mSynthEngine.getActiveVoiceCount();
        int sr = mSampleRate > 0 ? mSampleRate : 48000;
        LOGI("AudioCallback render duration: %ld us (budget: %d us, active voices: %d, frames: %d)",
             (long)durationUs, (numFrames * 1000000 / sr), totalActiveVoices, numFrames);
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::processMasterChain(float *floatBuf, int32_t numFrames) {
    if (mBypassMasterFX.load(std::memory_order_relaxed)) return;

    // 1. Apply Sub-Bass Butterworth 4-pole Cut-off at 30Hz
    mSubBassCut1.process(floatBuf, numFrames);
    mSubBassCut2.process(floatBuf, numFrames);

    // 2. Master Parallel Upward Compressor & Look-Ahead Limiter
    float sr = static_cast<float>(mSampleRate > 0 ? mSampleRate : 48000);
    float alphaAttack = std::exp(-1.0f / (sr * 0.010f)); // 10ms attack
    float alphaRelease = std::exp(-1.0f / (sr * 0.100f)); // 100ms release
    float limAttack = std::exp(-1.0f / (sr * 0.001f)); // 1ms attack
    float limRelease = std::exp(-1.0f / (sr * 0.120f)); // 120ms release
    constexpr float compThreshold = 0.063f; // -24 dB
    constexpr float limCeiling = 0.96f; // Master output safety ceiling
    constexpr float kMasterMakeupGain = 2.2f; // Pro Workstation makeup gain for rich/loud sound without clipping

    for (int32_t i = 0; i < numFrames; ++i) {
        float xL = floatBuf[2 * i] * kMasterMakeupGain;
        float xR = floatBuf[2 * i + 1] * kMasterMakeupGain;

        // A. Parallel / Upward Compression Envelope Follower
        float envIn = std::max(std::abs(xL), std::abs(xR));
        if (envIn > mParallelCompEnv) {
            mParallelCompEnv = envIn + alphaAttack * (mParallelCompEnv - envIn);
        } else {
            mParallelCompEnv = envIn + alphaRelease * (mParallelCompEnv - envIn);
        }

        float compGain = 1.0f;
        if (mParallelCompEnv > compThreshold) {
            float dbEnv = 20.0f * std::log10(mParallelCompEnv / compThreshold);
            float dbTarget = dbEnv / 4.0f; // 4:1 compression ratio
            float dbReduction = dbTarget - dbEnv;
            compGain = std::pow(10.0f, dbReduction / 20.0f);
        }

        // Mix 65% dry and 35% heavily compressed signal to lift low-level details (upward warmth)
        float processedL = xL + 0.35f * (xL * compGain * 2.5f);
        float processedR = xR + 0.35f * (xR * compGain * 2.5f);

        // B. Write processed samples to the look-ahead delay buffer
        mDelayBufferL[mLimiterWriteIndex] = processedL;
        mDelayBufferR[mLimiterWriteIndex] = processedR;

        // C. Look-Ahead Transient Peak Detection
        float futurePeak = 0.0f;
        for (int d = 0; d < 64; ++d) {
            float p = std::max(std::abs(mDelayBufferL[d]), std::abs(mDelayBufferR[d]));
            if (p > futurePeak) futurePeak = p;
        }

        // D. Smooth Limiter Gain Reduction Envelope
        if (futurePeak > mLimiterEnv) {
            mLimiterEnv = futurePeak + limAttack * (mLimiterEnv - futurePeak);
        } else {
            mLimiterEnv = futurePeak + limRelease * (mLimiterEnv - futurePeak);
        }

        float limiterGain = 1.0f;
        if (mLimiterEnv > limCeiling) {
            limiterGain = limCeiling / mLimiterEnv;
        }

        // E. Read delayed sample and apply limiter gain reduction
        int readIndex = (mLimiterWriteIndex + 1) % 64;
        float delayedL = mDelayBufferL[readIndex];
        float delayedR = mDelayBufferR[readIndex];

        floatBuf[2 * i] = delayedL * limiterGain;
        floatBuf[2 * i + 1] = delayedR * limiterGain;

        mLimiterWriteIndex = (mLimiterWriteIndex + 1) % 64;
    }
}

#else
AudioEngine::AudioEngine() = default;
AudioEngine::~AudioEngine() = default;
bool AudioEngine::start(int driverType) { return true; }
void AudioEngine::stop() {}
#endif
