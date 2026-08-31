#include "audio_engine.h"
#include <android/log.h>

#define LOG_TAG "AudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if HAS_OBOE
bool AudioEngine::start() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(48000)
        ->setDataCallback(this)
        ->setUsage(oboe::Usage::Media);

    oboe::Result result = builder.openStream(mStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open Oboe stream: %s", oboe::convertToText(result));
        return false;
    }

    LOGI("Stream opened: sampleRate=%d, framesPerBurst=%d, channels=%d",
         mStream->getSampleRate(), mStream->getFramesPerBurst(), mStream->getChannelCount());

    mStream->setBufferSizeInFrames(mStream->getFramesPerBurst() * 2);

    if (!mSoundfontEngine.init(mStream->getSampleRate())) {
        LOGE("Failed to initialize SoundfontEngine");
        return false;
    }

    result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start Oboe stream: %s", oboe::convertToText(result));
        return false;
    }

    return true;
}

void AudioEngine::stop() {
    if (mStream) {
        mStream->requestStop();
        mStream->close();
        mStream.reset();
    }
    mSoundfontEngine.destroy();
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) {
    auto *outputBuffer = static_cast<float *>(audioData);
    mSoundfontEngine.renderStereo(outputBuffer, numFrames);
    return oboe::DataCallbackResult::Continue;
}
#endif
