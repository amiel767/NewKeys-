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
    stop();
    mDriverType = driverType;

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(48000)
        ->setDataCallback(this)
        ->setUsage(oboe::Usage::Media);

    if (driverType == 1) {
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
    LOGI("Audio stream opened: %d Hz, %d frames/burst. Initializing 3 SoundFont engines...",
         sampleRate, mStream->getFramesPerBurst());

    for (int i = 0; i < 3; ++i) {
        mEngines[i].init(sampleRate);
    }

    result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start Oboe audio stream: %s", oboe::convertToText(result));
        return false;
    }

    LOGI("Oboe audio engine running with driver mode: %d", driverType);
    return true;
}

void AudioEngine::stop() {
    if (mStream) {
        mStream->stop();
        mStream->close();
        mStream.reset();
    }
    for (int i = 0; i < 3; ++i) {
        mEngines[i].destroy();
    }
    LOGI("Audio engine stopped and all synth instances destroyed");
}

void AudioEngine::setDriver(int driverType) {
    if (mDriverType != driverType) {
        LOGI("Switching audio driver from %d to %d", mDriverType, driverType);
        start(driverType);
    }
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

    return oboe::DataCallbackResult::Continue;
}

#else

AudioEngine::AudioEngine() = default;
AudioEngine::~AudioEngine() = default;
bool AudioEngine::start(int driverType) { return true; }
void AudioEngine::stop() {}

#endif
