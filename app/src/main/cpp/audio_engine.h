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
#include <algorithm>
#include <atomic>
#include <mutex>

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

    inline void processSample(float &xL, float &xR) {
        if (bypass) return;
        float yL = coeffs.b0 * xL + coeffs.b1 * x1_L + coeffs.b2 * x2_L - coeffs.a1 * y1_L - coeffs.a2 * y2_L;
        x2_L = x1_L; x1_L = xL;
        y2_L = y1_L; y1_L = yL;
        xL = yL;

        float yR = coeffs.b0 * xR + coeffs.b1 * x1_R + coeffs.b2 * x2_R - coeffs.a1 * y1_R - coeffs.a2 * y2_R;
        x2_R = x1_R; x1_R = xR;
        y2_R = y1_R; y1_R = yR;
        xR = yR;
    }

    void process(float *buffer, int32_t numFrames) {
        if (bypass) return;
        for (int32_t i = 0; i < numFrames; ++i) {
            processSample(buffer[2 * i], buffer[2 * i + 1]);
        }
    }

private:
    BiquadCoeffs coeffs;
    float x1_L = 0.f, x2_L = 0.f, y1_L = 0.f, y2_L = 0.f;
    float x1_R = 0.f, x2_R = 0.f, y1_R = 0.f, y2_R = 0.f;
    bool bypass = true;
};

// ================= DSP: STEREO DELAY / ECHO =================
class StereoDelay {
public:
    void init(int sampleRate) {
        mSampleRate = sampleRate > 0 ? sampleRate : 48000;
        mMaxDelayFrames = mSampleRate * 2; // up to 2.0s
        mBufferL.assign(mMaxDelayFrames, 0.0f);
        mBufferR.assign(mMaxDelayFrames, 0.0f);
        mWriteIndex = 0;
        mFilterStateL = 0.0f;
        mFilterStateR = 0.0f;
    }

    void setParams(bool enabled, float timeSec, float feedback, float mix, bool pingPong) {
        mEnabled = enabled;
        mDelayTimeSec = std::clamp(timeSec, 0.02f, 1.8f);
        mFeedback = std::clamp(feedback, 0.0f, 0.92f);
        mMix = std::clamp(mix, 0.0f, 1.0f);
        mPingPong = pingPong;
    }

    void process(float *buffer, int32_t numFrames) {
        if (!mEnabled || mMix <= 0.001f || mBufferL.empty()) return;

        int delaySamples = static_cast<int>(mDelayTimeSec * mSampleRate);
        if (delaySamples >= mMaxDelayFrames) delaySamples = mMaxDelayFrames - 1;
        if (delaySamples < 1) delaySamples = 1;

        float damp = 0.35f; // 1-pole high damping for warm analog tape sound

        for (int32_t i = 0; i < numFrames; ++i) {
            float inL = buffer[2 * i];
            float inR = buffer[2 * i + 1];

            int readIdx = mWriteIndex - delaySamples;
            if (readIdx < 0) readIdx += mMaxDelayFrames;

            float delL = mBufferL[readIdx];
            float delR = mBufferR[readIdx];

            // 1-pole damping filter
            mFilterStateL = mFilterStateL * damp + delL * (1.0f - damp);
            mFilterStateR = mFilterStateR * damp + delR * (1.0f - damp);

            float fbL = mFilterStateL * mFeedback;
            float fbR = mFilterStateR * mFeedback;

            if (mPingPong) {
                mBufferL[mWriteIndex] = inL + fbR;
                mBufferR[mWriteIndex] = inR + fbL;
            } else {
                mBufferL[mWriteIndex] = inL + fbL;
                mBufferR[mWriteIndex] = inR + fbR;
            }

            buffer[2 * i] = inL * (1.0f - mMix) + delL * mMix;
            buffer[2 * i + 1] = inR * (1.0f - mMix) + delR * mMix;

            mWriteIndex = (mWriteIndex + 1) % mMaxDelayFrames;
        }
    }

private:
    int mSampleRate = 48000;
    int mMaxDelayFrames = 96000;
    std::vector<float> mBufferL;
    std::vector<float> mBufferR;
    int mWriteIndex = 0;
    float mFilterStateL = 0.0f;
    float mFilterStateR = 0.0f;
    bool mEnabled = false;
    float mDelayTimeSec = 0.35f;
    float mFeedback = 0.40f;
    float mMix = 0.0f;
    bool mPingPong = false;
};

// ================= DSP: STEREO FREEVERB REVERBERATOR =================
class CombFilter {
public:
    void init(int size) {
        mBuffer.assign(size > 0 ? size : 100, 0.0f);
        mSize = size;
        mIndex = 0;
        mFilterState = 0.0f;
    }
    inline float process(float input, float feedback, float damp) {
        float output = mBuffer[mIndex];
        mFilterState = (output * (1.0f - damp)) + (mFilterState * damp);
        mBuffer[mIndex] = input + (mFilterState * feedback);
        if (++mIndex >= mSize) mIndex = 0;
        return output;
    }
    void clear() {
        std::fill(mBuffer.begin(), mBuffer.end(), 0.0f);
        mFilterState = 0.0f;
    }
private:
    std::vector<float> mBuffer;
    int mSize = 0;
    int mIndex = 0;
    float mFilterState = 0.0f;
};

class AllpassFilter {
public:
    void init(int size) {
        mBuffer.assign(size > 0 ? size : 100, 0.0f);
        mSize = size;
        mIndex = 0;
    }
    inline float process(float input) {
        float bufOut = mBuffer[mIndex];
        float output = -input + bufOut;
        mBuffer[mIndex] = input + (bufOut * 0.5f);
        if (++mIndex >= mSize) mIndex = 0;
        return output;
    }
    void clear() {
        std::fill(mBuffer.begin(), mBuffer.end(), 0.0f);
    }
private:
    std::vector<float> mBuffer;
    int mSize = 0;
    int mIndex = 0;
};

class StereoReverb {
public:
    void init(int sampleRate) {
        float srScale = static_cast<float>(sampleRate > 0 ? sampleRate : 48000) / 44100.0f;
        const int combTunings[8] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        const int allpassTunings[4] = {556, 441, 341, 225};
        const int stereoSpread = 23;

        for (int i = 0; i < 8; ++i) {
            mCombsL[i].init(static_cast<int>(combTunings[i] * srScale));
            mCombsR[i].init(static_cast<int>((combTunings[i] + stereoSpread) * srScale));
        }
        for (int i = 0; i < 4; ++i) {
            mAllpassL[i].init(static_cast<int>(allpassTunings[i] * srScale));
            mAllpassR[i].init(static_cast<int>((allpassTunings[i] + stereoSpread) * srScale));
        }
    }

    void setParams(bool enabled, float size, float decay, float damp, float mix) {
        mEnabled = enabled;
        mRoomSize = std::clamp(size, 0.1f, 0.98f);
        mDecay = std::clamp(decay, 0.1f, 0.98f);
        mDamp = std::clamp(damp, 0.0f, 0.8f);
        mMix = std::clamp(mix, 0.0f, 1.0f);
    }

    void process(float *buffer, int32_t numFrames) {
        if (!mEnabled || mMix <= 0.001f) return;

        float feedback = 0.7f + mRoomSize * 0.28f;

        for (int32_t i = 0; i < numFrames; ++i) {
            float inL = buffer[2 * i];
            float inR = buffer[2 * i + 1];
            float inMono = (inL + inR) * 0.015f;

            float outL = 0.0f;
            float outR = 0.0f;

            for (int c = 0; c < 8; ++c) {
                outL += mCombsL[c].process(inMono, feedback, mDamp);
                outR += mCombsR[c].process(inMono, feedback, mDamp);
            }

            for (int a = 0; a < 4; ++a) {
                outL = mAllpassL[a].process(outL);
                outR = mAllpassR[a].process(outR);
            }

            buffer[2 * i] = inL * (1.0f - mMix) + outL * mMix;
            buffer[2 * i + 1] = inR * (1.0f - mMix) + outR * mMix;
        }
    }

private:
    std::array<CombFilter, 8> mCombsL;
    std::array<CombFilter, 8> mCombsR;
    std::array<AllpassFilter, 4> mAllpassL;
    std::array<AllpassFilter, 4> mAllpassR;
    bool mEnabled = false;
    float mRoomSize = 0.6f;
    float mDecay = 0.5f;
    float mDamp = 0.3f;
    float mMix = 0.0f;
};

// ================= DSP: SOUNDGOODIZER (FL Studio Style) =================
class SoundGoodizerDsp {
public:
    void init(int sampleRate) {
        mSampleRate = sampleRate > 0 ? sampleRate : 48000;
        mAirShelf.setHighShelf(static_cast<float>(mSampleRate), 4500.0f, 4.0f, 0.707f);
        mWarmShelf.setLowShelf(static_cast<float>(mSampleRate), 220.0f, 3.0f, 0.707f);
        mPeakEnv = 0.0f;
    }

    void setParams(bool enabled, int mode, float amount) {
        mEnabled = enabled;
        mMode = std::clamp(mode, 0, 3);
        mAmount = std::clamp(amount, 0.0f, 1.0f);
    }

    void process(float *buffer, int32_t numFrames) {
        if (!mEnabled || mAmount <= 0.001f) return;

        float amt = mAmount;
        float drive = 1.0f + amt * 2.5f;
        float blend = amt * 0.85f;

        for (int32_t i = 0; i < numFrames; ++i) {
            float inL = buffer[2 * i];
            float inR = buffer[2 * i + 1];

            float procL = inL;
            float procR = inR;

            switch (mMode) {
                case 0: { // A - Warm Tube
                    procL = std::tanh(procL * drive) + 0.12f * amt * (procL * procL);
                    procR = std::tanh(procR * drive) + 0.12f * amt * (procR * procR);
                    mWarmShelf.processSample(procL, procR);
                    procL *= (1.0f + amt * 0.2f);
                    procR *= (1.0f + amt * 0.2f);
                    break;
                }
                case 1: { // B - Crisp Air
                    mAirShelf.processSample(procL, procR);
                    procL = procL + 0.35f * amt * std::sin(procL * 3.14159f * 0.5f);
                    procR = procR + 0.35f * amt * std::sin(procR * 3.14159f * 0.5f);
                    break;
                }
                case 2: { // C - Deep Crunch
                    float cL = procL * (drive * 1.3f);
                    float cR = procR * (drive * 1.3f);
                    procL = (cL > 0.0f) ? (1.0f - std::exp(-cL)) : (-1.0f + std::exp(cL));
                    procR = (cR > 0.0f) ? (1.0f - std::exp(-cR)) : (-1.0f + std::exp(cR));
                    mWarmShelf.processSample(procL, procR);
                    break;
                }
                case 3: { // D - Hard Limiter / Maximizer
                    float gain = 1.0f + amt * 2.0f;
                    procL *= gain;
                    procR *= gain;
                    // Soft saturation curve limiting
                    procL = std::clamp(procL, -0.95f, 0.95f);
                    procR = std::clamp(procR, -0.95f, 0.95f);
                    break;
                }
            }

            buffer[2 * i] = inL * (1.0f - blend) + procL * blend;
            buffer[2 * i + 1] = inR * (1.0f - blend) + procR * blend;
        }
    }

private:
    int mSampleRate = 48000;
    bool mEnabled = false;
    int mMode = 0;
    float mAmount = 0.0f;
    StereoBiquad mAirShelf;
    StereoBiquad mWarmShelf;
    float mPeakEnv = 0.0f;
};

// ================= DSP: SPATIAL WIDENER =================
class SpatialWidenerDsp {
public:
    void setAmount(float amount) {
        mAmount = std::clamp(amount, 0.0f, 1.0f);
    }

    void process(float *buffer, int32_t numFrames) {
        if (mAmount <= 0.01f) return;
        float sideGain = 1.0f + mAmount * 1.5f;

        for (int32_t i = 0; i < numFrames; ++i) {
            float inL = buffer[2 * i];
            float inR = buffer[2 * i + 1];

            float mid = (inL + inR) * 0.5f;
            float side = (inL - inR) * 0.5f * sideGain;

            buffer[2 * i] = mid + side;
            buffer[2 * i + 1] = mid - side;
        }
    }

private:
    float mAmount = 0.0f;
};

// ================= DSP: MASTER TRANSIENT PUNCH =================
class MasterPunchDsp {
public:
    void init(int sampleRate) {
        mSampleRate = sampleRate > 0 ? sampleRate : 48000;
        mFastEnvL = mFastEnvR = 0.0f;
        mSlowEnvL = mSlowEnvR = 0.0f;
    }

    void setAmount(float amount) {
        mAmount = std::clamp(amount, 0.0f, 1.0f);
    }

    void process(float *buffer, int32_t numFrames) {
        if (mAmount <= 0.01f) return;

        float fastCoeff = 0.90f;
        float slowCoeff = 0.992f;
        float punchScale = mAmount * 1.2f;

        for (int32_t i = 0; i < numFrames; ++i) {
            float inL = buffer[2 * i];
            float inR = buffer[2 * i + 1];

            float absL = std::abs(inL);
            float absR = std::abs(inR);

            mFastEnvL = mFastEnvL * fastCoeff + absL * (1.0f - fastCoeff);
            mFastEnvR = mFastEnvR * fastCoeff + absR * (1.0f - fastCoeff);

            mSlowEnvL = mSlowEnvL * slowCoeff + absL * (1.0f - slowCoeff);
            mSlowEnvR = mSlowEnvR * slowCoeff + absR * (1.0f - slowCoeff);

            float diffL = std::max(0.0f, mFastEnvL - mSlowEnvL);
            float diffR = std::max(0.0f, mFastEnvR - mSlowEnvR);

            float gainL = 1.0f + diffL * punchScale;
            float gainR = 1.0f + diffR * punchScale;

            buffer[2 * i] = inL * gainL;
            buffer[2 * i + 1] = inR * gainR;
        }
    }

private:
    int mSampleRate = 48000;
    float mAmount = 0.0f;
    float mFastEnvL = 0.0f, mFastEnvR = 0.0f;
    float mSlowEnvL = 0.0f, mSlowEnvR = 0.0f;
};

/**
 * Multi-Instance Audio Engine with Oboe / OpenSL ES driver support and Complete Realtime DSP Effects Pipeline.
 */
#if HAS_OBOE
class AudioEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine();

    bool start(int driverType = 0);
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

    // DSP Controls
    void setMasterEq(float lowGainDb, float midGainDb, float highGainDb);
    void setSoundGoodizer(bool enabled, int mode, float amount);
    void setMasterReverb(bool enabled, float size, float decay, float damp, float mix);
    void setMasterDelay(bool enabled, float timeSec, float feedback, float mix, bool pingPong);
    void setSpatialWidener(float amount);
    void setMasterPunch(float amount);

    SoundfontEngine &getEngine(int engineIndex) {
        if (engineIndex < 0 || engineIndex >= 3) return mEngines[0];
        return mEngines[engineIndex];
    }

    bool hasActiveSoundFonts() const;
    int renderDirect(int16_t *outputBuffer16, int32_t numFrames);
    bool isOboeActive() const {
        return mStream && (mStream->getState() == oboe::StreamState::Started || mStream->getState() == oboe::StreamState::Starting);
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
    std::mutex mRenderMutex;
    std::atomic<bool> mOboeActive{false};
    int mDriverType = 0;
    std::array<SoundfontEngine, 3> mEngines; // 0 = Fader, 1 = Pad, 2 = Drum
    
    // DSP Modules
    StereoBiquad mEqLow;
    StereoBiquad mEqMid;
    StereoBiquad mEqHigh;
    StereoReverb mMasterReverb;
    StereoDelay mMasterDelay;
    SoundGoodizerDsp mSoundGoodizer;
    SpatialWidenerDsp mSpatialWidener;
    MasterPunchDsp mMasterPunch;

    int mSampleRate = 48000;
    std::vector<float> mFloatRenderBuffer;
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
    void setSoundGoodizer(bool enabled, int mode, float amount) {}
    void setMasterReverb(bool enabled, float size, float decay, float damp, float mix) {}
    void setMasterDelay(bool enabled, float timeSec, float feedback, float mix, bool pingPong) {}
    void setSpatialWidener(float amount) {}
    void setMasterPunch(float amount) {}

    SoundfontEngine &getEngine(int engineIndex) {
        if (engineIndex < 0 || engineIndex >= 3) return mEngines[0];
        return mEngines[engineIndex];
    }

private:
    std::array<SoundfontEngine, 3> mEngines;
};
#endif

#endif //DAWSTUDIO_AUDIO_ENGINE_H
