#ifndef DAWSTUDIO_AUDIO_ENGINE_H
#define DAWSTUDIO_AUDIO_ENGINE_H

#if __has_include(<oboe/Oboe.h>)
#include <oboe/Oboe.h>
#define HAS_OBOE 1
#else
#define HAS_OBOE 0
#endif

#include "soundfont_engine.h"
#include <memory>

#if HAS_OBOE
class AudioEngine : public oboe::AudioStreamDataCallback {
public:
    bool start();
    void stop();
    SoundfontEngine &soundfont() { return mSoundfontEngine; }

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) override;

private:
    std::shared_ptr<oboe::AudioStream> mStream;
    SoundfontEngine mSoundfontEngine;
};
#else
class AudioEngine {
public:
    bool start() { return mSoundfontEngine.init(48000); }
    void stop() { mSoundfontEngine.destroy(); }
    SoundfontEngine &soundfont() { return mSoundfontEngine; }

private:
    SoundfontEngine mSoundfontEngine;
};
#endif

#endif //DAWSTUDIO_AUDIO_ENGINE_H
