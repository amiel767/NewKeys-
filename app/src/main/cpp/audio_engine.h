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
#include <array>
#include <vector>

/**
 * Multi-Instance Audio Engine with Oboe / OpenSL ES driver support.
 * Manages 3 autonomous synth instances:
 * - Engine 0: FaderEngine (Main Tracks 1..8 & Master)
 * - Engine 1: PadEngine (Tonic Pad)
 * - Engine 2: DrumEngine (Drum Pad)
 */
#if HAS_OBOE
class AudioEngine : public oboe::AudioStreamDataCallback {
public:
    AudioEngine();
    ~AudioEngine();

    bool start(int driverType = 0); // 0 = Oboe High-Performance (AAudio), 1 = OpenSL ES
    void stop();
    void setDriver(int driverType);

    SoundfontEngine &getEngine(int engineIndex) {
        if (engineIndex < 0 || engineIndex >= 3) return mEngines[0];
        return mEngines[engineIndex];
    }

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) override;

private:
    std::shared_ptr<oboe::AudioStream> mStream;
    int mDriverType = 0;
    std::array<SoundfontEngine, 3> mEngines; // 0 = Fader, 1 = Pad, 2 = Drum
};
#else
class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    bool start(int driverType = 0);
    void stop();
    void setDriver(int driverType) {}

    SoundfontEngine &getEngine(int engineIndex) {
        if (engineIndex < 0 || engineIndex >= 3) return mEngines[0];
        return mEngines[engineIndex];
    }

private:
    std::array<SoundfontEngine, 3> mEngines;
};
#endif

#endif //DAWSTUDIO_AUDIO_ENGINE_H
