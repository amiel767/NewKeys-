#include "audio_engine.h"
#include <android/log.h>
#include <thread>
#include <chrono>

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
        ->setSharingMode(oboe::SharingMode::Shared)
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
        LOGI("Requesting Oboe High-Performance (AAudio) backend...");
        builder.setAudioApi(oboe::AudioApi::AAudio);
    }

    oboe::Result result = builder.openStream(mStream);
    if (result != oboe::Result::OK) {
        LOGW("Failed to open Float audio stream (%s). Retrying with I16 format...", oboe::convertToText(result));
        builder.setFormat(oboe::AudioFormat::I16);
        builder.setAudioApi(oboe::AudioApi::OpenSLES);
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
    int targetBuffer = std::max(512, burst * 4);
    mStream->setBufferSizeInFrames(targetBuffer);
    int sampleRate = mStream->getSampleRate();
    mSampleRate = sampleRate;

    LOGI("Audio stream opened: %d Hz, %d frames/burst, buffer set to %d frames.", 
        sampleRate, burst, targetBuffer);

    // Initialize all DSP blocks with active sample rate
    mMasterDelay.init(sampleRate);
    mMasterReverb.init(sampleRate);
    mSoundGoodizer.init(sampleRate);
    mMasterPunch.init(sampleRate);
    mPadFilter.setLowPass(static_cast<float>(sampleRate), 400.0f * std::pow(45.0f, mPadBrightness), 0.707f);

    // Initialize FluidSynth engines
    for (int i = 0; i < 3; ++i) {
        if (!mEngines[i].isInitialized()) {
            mEngines[i].init(sampleRate);
        }
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
    for (int i = 0; i < 3; ++i) {
        mEngines[i].destroy();
    }
    LOGI("Audio engine stopped and all synth instances destroyed");
}

void AudioEngine::setDriver(int driverType) {
    if (mDriverType != driverType) {
        LOGI("Switching audio driver from %d to %d", mDriverType, driverType);
        mDriverType = driverType;
        openAndStartStream();
    }
}

void AudioEngine::setBufferSize(int bufferSizeInFrames) {
    std::lock_guard<std::mutex> streamLock(mStreamMutex);
    if (mStream) {
        int32_t burst = mStream->getFramesPerBurst();
        int32_t targetFrames = bufferSizeInFrames;
        if (burst > 0) {
            int32_t numBursts = std::max(4, (targetFrames + burst - 1) / burst);
            targetFrames = numBursts * burst;
        }
        int clamped = std::clamp(targetFrames, 64, 4096);
        auto res = mStream->setBufferSizeInFrames(clamped);
        if (res) {
            LOGI("Oboe buffer size set to %d frames (burst: %d, actual: %d)", clamped, burst, res.value());
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
}

bool AudioEngine::hasActiveSoundFonts() const {
    for (int i = 0; i < 3; ++i) {
        if (mEngines[i].isInitialized()) {
            return true;
        }
    }
    return false;
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
    for (int i = 0; i < 3; ++i) {
        if (!mEngines[i].isInitialized()) {
            mEngines[i].init(sampleRate);
        }
    }

    size_t totalSamples = static_cast<size_t>(numFrames * 2);
    if (mFloatRenderBuffer.size() < totalSamples) {
        mFloatRenderBuffer.resize(totalSamples, 0.0f);
    }
    float *floatBuf = mFloatRenderBuffer.data();

    // 1. Render FaderEngine (mixer channels 0..7)
    mEngines[0].renderStereo(floatBuf, numFrames, false);

    // 2. Mix PadEngine (Tonic Pad) through Brightness Filter
    if (mPadRenderBuffer.size() < totalSamples) {
        mPadRenderBuffer.resize(totalSamples, 0.0f);
    }
    mEngines[1].renderStereo(mPadRenderBuffer.data(), numFrames, false);
    mPadFilter.process(mPadRenderBuffer.data(), numFrames);
    for (size_t i = 0; i < totalSamples; ++i) {
        floatBuf[i] += mPadRenderBuffer[i];
    }

    // 3. Mix DrumEngine (Drum Pad)
    mEngines[2].renderStereo(floatBuf, numFrames, true);

    // 4. Apply Master Delay
    mMasterDelay.process(floatBuf, numFrames);

    // 5. Apply Master Reverb
    mMasterReverb.process(floatBuf, numFrames);

    // 6. Apply SoundGoodizer Multiband/Tube DSP
    mSoundGoodizer.process(floatBuf, numFrames);

    // 7. Apply Transient Punch
    mMasterPunch.process(floatBuf, numFrames);

    // 8. Apply Spatial Stereo Widener
    mSpatialWidener.process(floatBuf, numFrames);

    // 9. Apply Master 3-Band Biquad EQ
    mEqLow.process(floatBuf, numFrames);
    mEqMid.process(floatBuf, numFrames);
    mEqHigh.process(floatBuf, numFrames);

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

        // 1. Render FaderEngine (mixer channels 0..7)
        mEngines[0].renderStereo(floatBuf, numFrames, false);

        // 2. Mix PadEngine (Tonic Pad) through Brightness Filter
        if (mPadRenderBuffer.size() < totalSamples) {
            mPadRenderBuffer.resize(totalSamples, 0.0f);
        }
        mEngines[1].renderStereo(mPadRenderBuffer.data(), numFrames, false);
        mPadFilter.process(mPadRenderBuffer.data(), numFrames);
        for (size_t i = 0; i < totalSamples; ++i) {
            floatBuf[i] += mPadRenderBuffer[i];
        }

        // 3. Mix DrumEngine (Drum Pad)
        mEngines[2].renderStereo(floatBuf, numFrames, true);

        // 4. Apply Master Delay
        mMasterDelay.process(floatBuf, numFrames);

        // 5. Apply Master Reverb
        mMasterReverb.process(floatBuf, numFrames);

        // 6. Apply SoundGoodizer Multiband/Tube DSP
        mSoundGoodizer.process(floatBuf, numFrames);

        // 7. Apply Transient Punch
        mMasterPunch.process(floatBuf, numFrames);

        // 8. Apply Spatial Stereo Widener
        mSpatialWidener.process(floatBuf, numFrames);

        // 9. Apply Master 3-Band Biquad EQ
        mEqLow.process(floatBuf, numFrames);
        mEqMid.process(floatBuf, numFrames);
        mEqHigh.process(floatBuf, numFrames);

        // Convert Float to PCM 16-bit
        for (size_t i = 0; i < totalSamples; ++i) {
            float s = std::clamp(floatBuf[i], -1.0f, 1.0f);
            outputBuffer16[i] = static_cast<int16_t>(s * 32767.0f);
        }
    } else {
        auto *outputBuffer = static_cast<float *>(audioData);

        // 1. Render FaderEngine (mixer channels 0..7)
        mEngines[0].renderStereo(outputBuffer, numFrames, false);

        // 2. Mix PadEngine (Tonic Pad) through Brightness Filter
        size_t totalSamples = static_cast<size_t>(numFrames * 2);
        if (mPadRenderBuffer.size() < totalSamples) {
            mPadRenderBuffer.resize(totalSamples, 0.0f);
        }
        mEngines[1].renderStereo(mPadRenderBuffer.data(), numFrames, false);
        mPadFilter.process(mPadRenderBuffer.data(), numFrames);
        for (size_t i = 0; i < totalSamples; ++i) {
            outputBuffer[i] += mPadRenderBuffer[i];
        }

        // 3. Mix DrumEngine (Drum Pad)
        mEngines[2].renderStereo(outputBuffer, numFrames, true);

        // 4. Apply Master Delay
        mMasterDelay.process(outputBuffer, numFrames);

        // 5. Apply Master Reverb
        mMasterReverb.process(outputBuffer, numFrames);

        // 6. Apply SoundGoodizer Multiband/Tube DSP
        mSoundGoodizer.process(outputBuffer, numFrames);

        // 7. Apply Transient Punch
        mMasterPunch.process(outputBuffer, numFrames);

        // 8. Apply Spatial Stereo Widener
        mSpatialWidener.process(outputBuffer, numFrames);

        // 9. Apply Master 3-Band Biquad EQ (Low Shelf, Mid Peaking, High Shelf)
        mEqLow.process(outputBuffer, numFrames);
        mEqMid.process(outputBuffer, numFrames);
        mEqHigh.process(outputBuffer, numFrames);

        // 10. Master Soft-Clipping Maximizer / Limiter (Boosts low Soundfont volume cleanly without clipping)
        for (size_t i = 0; i < totalSamples; ++i) {
            float x = outputBuffer[i] * 1.8f; // Clean 1.8x volume boost
            if (x > 0.95f) {
                outputBuffer[i] = 0.95f + 0.05f * tanhf((x - 0.95f) / 0.05f);
            } else if (x < -0.95f) {
                outputBuffer[i] = -0.95f + 0.05f * tanhf((x + 0.95f) / 0.05f);
            } else {
                outputBuffer[i] = x;
            }
        }
    }

    auto endTime = std::chrono::high_resolution_clock::now();
    auto durationUs = std::chrono::duration_cast<std::chrono::microseconds>(endTime - startTime).count();

    static int sLogCounter = 0;
    if (++sLogCounter % 500 == 0 || durationUs > 3500) {
        int totalActiveVoices = 0;
        for (int i = 0; i < 3; ++i) {
            if (mEngines[i].isInitialized()) {
                totalActiveVoices += mEngines[i].getActiveVoiceCount();
            }
        }
        int sr = mSampleRate > 0 ? mSampleRate : 48000;
        LOGI("AudioCallback render duration: %ld us (budget: %d us, active voices: %d, frames: %d)",
             (long)durationUs, (numFrames * 1000000 / sr), totalActiveVoices, numFrames);
    }

    return oboe::DataCallbackResult::Continue;
}

#else
AudioEngine::AudioEngine() = default;
AudioEngine::~AudioEngine() = default;
bool AudioEngine::start(int driverType) { return true; }
void AudioEngine::stop() {}
#endif
