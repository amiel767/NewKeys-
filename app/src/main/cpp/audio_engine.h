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
#include <cmath>

struct BiquadCoeffs {
    float b0 = 1.0f, b1 = 0.0f, b2 = 0.0f;
    float a1 = 0.0f, a2 = 0.0f;
};

class StereoBiquad {
public:
    void reset() {
        x1_L = x2_L = y1_L = y2_L = 0.0f;
        x1_R = x2_R = y1_R = y2_R = 0.0f;
    }

    void setLowShelf(float sampleRate, float f0, float gainDb, float Q = 0.707f) {
        if (std::abs(gainDb) < 0.05f) {
            bypass = true;
            return;
        }
        bypass = false;
        float A = std::pow(10.0f, gainDb / 40.0f);
        float w0 = 2.0f * 3.14159265f * f0 / sampleRate;
        float alpha = std::sin(w0) / (2.0f * Q);
        float cos_w0 = std::cos(w0);
        float sqrtA2alpha = 2.0f * std::sqrt(A) * alpha;

        float a0 = (A + 1.0f) + (A - 1.0f) * cos_w0 + sqrtA2alpha;
        coeffs.b0 = (A * ((A + 1.0f) - (A - 1.0f) * cos_w0 + sqrtA2alpha)) / a0;
        coeffs.b1 = (2.0f * A * ((A - 1.0f) - (A + 1.0f) * cos_w0)) / a0;
        coeffs.b2 = (A * ((A + 1.0f) - (A - 1.0f) * cos_w0 - sqrtA2alpha)) / a0;
        coeffs.a1 = (-2.0f * ((A - 1.0f) + (A + 1.0f) * cos_w0)) / a0;
        coeffs.a2 = ((A + 1.0f) + (A - 1.0f) * cos_w0 - sqrtA2alpha) / a0;
    }

    void setPeaking(float sampleRate, float f0, float gainDb, float Q = 1.0f) {
        if (std::abs(gainDb) < 0.05f) {
            bypass = true;
            return;
        }
        bypass = false;
        float A = std::pow(10.0f, gainDb / 40.0f);
        float w0 = 2.0f * 3.14159265f * f0 / sampleRate;
        float alpha = std::sin(w0) / (2.0f * Q);
        float cos_w0 = std::cos(w0);

        float a0 = 1.0f + alpha / A;
        coeffs.b0 = (1.0f + alpha * A) / a0;
        coeffs.b1 = (-2.0f * cos_w0) / a0;
        coeffs.b2 = (1.0f - alpha * A) / a0;
        coeffs.a1 = (-2.0f * cos_w0) / a0;
        coeffs.a2 = (1.0f - alpha / A) / a0;
    }

    void setHighShelf(float sampleRate, float f0, float gainDb, float Q = 0.707f) {
        if (std::abs(gainDb) < 0.05f) {
            bypass = true;
            return;
        }
        bypass = false;
        float A = std::pow(10.0f, gainDb / 40.0f);
        float w0 = 2.0f * 3.14159265f * f0 / sampleRate;
        float alpha = std::sin(w0) / (2.0f * Q);
        float cos_w0 = std::cos(w0);
        float sqrtA2alpha = 2.0f * std::sqrt(A) * alpha;

        float a0 = (A + 1.0f) - (A - 1.0f) * cos_w0 + sqrtA2alpha;
        coeffs.b0 = (A * ((A + 1.0f) + (A - 1.0f) * cos_w0 + sqrtA2alpha)) / a0;
        coeffs.b1 = (-2.0f * A * ((A - 1.0f) + (A + 1.0f) * cos_w0)) / a0;
        coeffs.b2 = (A * ((A + 1.0f) - (A - 1.0f) * cos_w0 - sqrtA2alpha)) / a0;
        coeffs.a1 = (2.0f * ((A - 1.0f) - (A + 1.0f) * cos_w0)) / a0;
        coeffs.a2 = ((A + 1.0f) - (A - 1.0f) * cos_w0 - sqrtA2alpha) / a0;
    }

    void process(float *buffer, int32_t numFrames) {
        if (bypass) return;
        for (int32_t i = 0; i < numFrames; ++i) {
            float xL = buffer[2 * i];
            float yL = coeffs.b0 * xL + coeffs.b1 * x1_L + coeffs.b2 * x2_L - coeffs.a1 * y1_L - coeffs.a2 * y2_L;
            x2_L = x1_L; x1_L = xL;
            y2_L = y1_L; y1_L = yL;
            buffer[2 * i] = yL;

            float xR = buffer[2 * i + 1];
            float yR = coeffs.b0 * xR + coeffs.b1 * x1_R + coeffs.b2 * x2_R - coeffs.a1 * y1_R - coeffs.a2 * y2_R;
            x2_R = x1_R; x1_R = xR;
            y2_R = y1_R; y2_R = yR;
            buffer[2 * i + 1] = yR;
        }
    }

private:
    BiquadCoeffs coeffs;
    float x1_L = 0.f, x2_L = 0.f, y1_L = 0.f, y2_L = 0.f;
    float x1_R = 0.f, x2_R = 0.f, y1_R = 0.f, y2_R = 0.f;
    bool bypass = true;
};

/**
 * Multi-Instance Audio Engine with Oboe / OpenSL ES driver support.
 * Manages 3 autonomous synth instances:
 * - Engine 0: FaderEngine (Main Tracks 1..8 & Master)
 * - Engine 1: PadEngine (Tonic Pad)
 * - Engine 2: DrumEngine (Drum Pad)
 */
#if HAS_OBOE
class AudioEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine();

    bool start(int driverType = 0); // 0 = Oboe High-Performance (AAudio), 1 = OpenSL ES
    void stop();
    void setDriver(int driverType);
    void setMasterGain(float gain) {
        for (auto &engine : mEngines) {
            engine.setGain(gain);
        }
    }

    void setPolyphony(int polyphony) {
        for (auto &engine : mEngines) {
            engine.setPolyphony(polyphony);
        }
    }

    void setBufferSize(int bufferSizeInFrames);

    void setMasterEq(float lowGainDb, float midGainDb, float highGainDb);

    SoundfontEngine &getEngine(int engineIndex) {
        if (engineIndex < 0 || engineIndex >= 3) return mEngines[0];
        return mEngines[engineIndex];
    }

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) override;

    void onErrorBeforeClose(oboe::AudioStream *audioStream, oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) override;

private:
    bool openAndStartStream();

    std::shared_ptr<oboe::AudioStream> mStream;
    std::mutex mStreamMutex;
    int mDriverType = 0;
    std::array<SoundfontEngine, 3> mEngines; // 0 = Fader, 1 = Pad, 2 = Drum
    StereoBiquad mEqLow;
    StereoBiquad mEqMid;
    StereoBiquad mEqHigh;
    int mSampleRate = 48000;
};
#else
class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    bool start(int driverType = 0);
    void stop();
    void setDriver(int driverType) {}
    void setMasterGain(float gain) {
        for (auto &engine : mEngines) {
            engine.setGain(gain);
        }
    }
    void setPolyphony(int polyphony) {
        for (auto &engine : mEngines) {
            engine.setPolyphony(polyphony);
        }
    }
    void setBufferSize(int bufferSizeInFrames) {}
    void setMasterEq(float lowGainDb, float midGainDb, float highGainDb) {}

    SoundfontEngine &getEngine(int engineIndex) {
        if (engineIndex < 0 || engineIndex >= 3) return mEngines[0];
        return mEngines[engineIndex];
    }

private:
    std::array<SoundfontEngine, 3> mEngines;
};
#endif

#endif //DAWSTUDIO_AUDIO_ENGINE_H
