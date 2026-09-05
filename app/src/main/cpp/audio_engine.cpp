#include "audio_engine.h"
#include <android/log.h>

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
        LOGI("Requesting Oboe High-Performance (AAudio) backend...");
        builder.setAudioApi(oboe::AudioApi::AAudio);
    }

    oboe::Result result = builder.openStream(mStream);
    if (result != oboe::Result::OK) {
        LOGW("Failed to open preferred audio stream (%s). Retrying with Unspecified API...", oboe::convertToText(result));
        builder.setAudioApi(oboe::AudioApi::Unspecified);
        result = builder.openStream(mStream);
        if (result != oboe::Result::OK) {
            LOGE("Failed to open audio stream fallback: %s", oboe::convertToText(result));
            return false;
        }
    }

    mStream->setBufferSizeInFrames(mStream->getFramesPerBurst() * 2);

    int sampleRate = mStream->getSampleRate();
    mSampleRate = sampleRate;
    LOGI("Audio stream opened: %d Hz, %d frames/burst.",
         sampleRate, mStream->getFramesPerBurst());

    // Only initialize engines if they are not already initialized.
    // This preserves all loaded SoundFonts, banks, presets, programs, and volumes across device changes (headphones / USB-C DAC)
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

    LOGI("Oboe audio engine running with driver mode: %d", mDriverType);
    return true;
}

void AudioEngine::onErrorBeforeClose(oboe::AudioStream *audioStream, oboe::Result error) {
    LOGW("Oboe onErrorBeforeClose: %s", oboe::convertToText(error));
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) {
    LOGI("Oboe stream error/disconnected: %s (Audio route/device changed). Automatically reopening stream...",
         oboe::convertToText(error));
    openAndStartStream();
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
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (mStream) {
        int clamped = std::clamp(bufferSizeInFrames, 64, 4096);
        auto res = mStream->setBufferSizeInFrames(clamped);
        if (res) {
            LOGI("Oboe buffer size set to %d frames (actual: %d)", clamped, res.value());
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

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream *audioStream,
    void *audioData,
    int32_t numFrames) {

    auto *outputBuffer = static_cast<float *>(audioData);

    // 1. Render FaderEngine (mixer channels)
    mEngines[0].renderStereo(outputBuffer, numFrames, false);

    // 2. Mix PadEngine (Tonic Pad)
    mEngines[1].renderStereo(outputBuffer, numFrames, true);

    // 3. Mix DrumEngine (Drum Pad)
    mEngines[2].renderStereo(outputBuffer, numFrames, true);

    // 4. Apply Master 3-Band Biquad EQ (Low Shelf, Mid Peaking, High Shelf)
    mEqLow.process(outputBuffer, numFrames);
    mEqMid.process(outputBuffer, numFrames);
    mEqHigh.process(outputBuffer, numFrames);

    return oboe::DataCallbackResult::Continue;
}

#else

AudioEngine::AudioEngine() = default;
AudioEngine::~AudioEngine() = default;
bool AudioEngine::start(int driverType) { return true; }
void AudioEngine::stop() {}

#endif
