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

AudioEngine::AudioEngine() {
    int sampleRate = 48000;
    mSampleRate = sampleRate;
    mFloatRenderBuffer.resize(2048, 0.0f);
    // One stereo output group per MIDI channel: renderFxPath() needs each track's
    // soundfont render in its own group, and FluidSynth only honours
    // synth.audio-groups at init time. Without this renderChannels() returns false
    // and the whole Æther FX path silently falls back to the legacy stereo mix.
    mSynthEngine.setMultiChannelEnabled(true);
    mSynthEngine.init(sampleRate);
    mMasterDelay.init(sampleRate);
    mMasterReverb.init(sampleRate);
    mSoundGoodizer.init(sampleRate);
    mMasterPunch.init(sampleRate);
    mPadFilter.setLowPass(static_cast<float>(sampleRate), 400.0f * std::pow(45.0f, mPadBrightness), 0.707f);
    mTonicFxDelay.init(sampleRate);
    mPadsFxDelay.init(sampleRate);
    mTonicFxReverb.init(sampleRate);
    mPadsFxReverb.init(sampleRate);
    mDrumSampler.init(sampleRate);
    mMasterCompressor.init(sampleRate, -15.0f, 1.6f, 12.0f, 120.0f, 4.0f);
    mMasterCompressor.setParams(false, -15.0f, 1.6f, 12.0f, 120.0f, 0.0f);
    mSoftClipper.init(sampleRate);
    mSpatialWidener.setAmount(0.0f);
}

AudioEngine::~AudioEngine() {
    stop();
}

bool AudioEngine::start(int driverType) {
    mDriverType = driverType;
    return openAndStartStream();
}

bool AudioEngine::openAndStartStream() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    mOboeActive.store(false, std::memory_order_relaxed);
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
        ->setSampleRate(oboe::kUnspecified)
        ->setDataCallback(this)
        ->setErrorCallback(this)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music);

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

    // Size the Æther FX scratch on the control thread before the stream runs. The
    // capacity is the max Oboe burst the stream can hand us; FxMixer gets the same
    // number so its internal blocks are never reallocated mid-callback.
    {
        int capacity = std::max({burst * 4, reqBuffer, targetBuffer, 2048});
        mFxChannelBuffer.assign(static_cast<size_t>(capacity) * 2 * 16, 0.0f);
        mFxStripBuffer.assign(static_cast<size_t>(capacity) * 2, 0.0f);
        mFxMixBuffer.assign(static_cast<size_t>(capacity) * 2, 0.0f);
        mFx.prepare(sampleRate, capacity);
    }

    // Note routing must be installed before the stream runs: it is a plain
    // function pointer read on the audio thread, never mutated while running.
    mSynthEngine.setSourceRouter(&AudioEngine::sourceRouterTrampoline, this);
    mSynthEngine.setAllNotesOffHook(&AudioEngine::allNotesOffTrampoline, this);

    LOGI("Audio stream opened: %d Hz, %d frames/burst, buffer set to %d frames (requested: %d).", 
        sampleRate, burst, targetBuffer, reqBuffer);

    // Initialize all DSP blocks with active sample rate
    mMasterDelay.init(sampleRate);
    mMasterChorus.init(sampleRate);
    mMasterReverb.init(sampleRate);
    mSoundGoodizer.init(sampleRate);
    mMasterPunch.init(sampleRate);
    mPadFilter.setLowPass(static_cast<float>(sampleRate), 400.0f * std::pow(45.0f, mPadBrightness), 0.707f);
    mTonicFxDelay.init(sampleRate);
    mPadsFxDelay.init(sampleRate);
    mTonicFxReverb.init(sampleRate);
    mPadsFxReverb.init(sampleRate);
    mDrumSampler.init(sampleRate);
    mMasterCompressor.init(sampleRate, mMasterCompressorThreshDb, mMasterCompressorRatio, mMasterCompressorAttackMs, mMasterCompressorReleaseMs, mMasterCompressorMakeupDb);
    mSoftClipper.init(sampleRate);

    // Immediately re-apply all preserved DSP, Gain and Volume parameters
    // This ensures that when plugging in or unplugging a 3.5mm jack or BT device,
    // the audio output retains full power, master gain, punch, and dynamics without ducking!
    mMasterDelay.setParams(mMasterDelayEnabled, mMasterDelayTime, mMasterDelayFeedback, mMasterDelayMix, mMasterDelayPingPong);
    mMasterChorus.setParams(mMasterChorusEnabled, mMasterChorusRateHz, mMasterChorusDepthMs, mMasterChorusMix);
    mMasterCompressor.setParams(mMasterCompressorEnabled, mMasterCompressorThreshDb, mMasterCompressorRatio, mMasterCompressorAttackMs, mMasterCompressorReleaseMs, mMasterCompressorMakeupDb);
    mMasterReverb.setParams(mMasterReverbEnabled, mMasterReverbSize, mMasterReverbDecay, mMasterReverbDamp, mMasterReverbMix);
    mSoundGoodizer.setParams(mSoundGoodizerEnabled, mSoundGoodizerMode, mSoundGoodizerAmount);
    mSpatialWidener.setAmount(mSpatialWidenerAmount);
    mMasterPunch.setAmount(mMasterPunchAmount);
    setMasterEq(mEqLowDb, mEqMidDb, mEqHighDb);
    mDrumSampler.setMasterVolume(mDrumMasterVolume);

    // Initialize unified FluidSynth engine (16 MIDI channels covering Tracks 1..8, Drum 8, TonicPad 9)
    if (!mSynthEngine.isInitialized()) {
        mSynthEngine.setMultiChannelEnabled(true);
        mSynthEngine.init(sampleRate, kFaderPolyphony, "UnifiedSynthEngine");
    }
    mSynthEngine.setGain(mMasterGain);

    result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start Oboe audio stream: %s", oboe::convertToText(result));
        return false;
    }

    mOboeActive.store(true, std::memory_order_relaxed);
    LOGI("Oboe audio engine running with driver mode: %d and realtime DSP active", mDriverType);
    return true;
}

void AudioEngine::onErrorBeforeClose(oboe::AudioStream *audioStream, oboe::Result error) {
    LOGW("Oboe onErrorBeforeClose: %s", oboe::convertToText(error));
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) {
    LOGI("Oboe stream error/disconnected: %s. Reopening stream asynchronously...", 
        oboe::convertToText(error));
    if (mIsReconnecting.exchange(true)) {
        LOGI("Reconnection already pending or in progress, skipping duplicate request");
        return;
    }
    std::thread([this]() {
        std::this_thread::sleep_for(std::chrono::milliseconds(250));
        openAndStartStream();
        mIsReconnecting.store(false);
    }).detach();
}

void AudioEngine::stop() {
    mOboeActive.store(false, std::memory_order_relaxed);
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
    mEqLowDb = lowGainDb;
    mEqMidDb = midGainDb;
    mEqHighDb = highGainDb;
    float sr = static_cast<float>(mSampleRate > 0 ? mSampleRate : 48000);
    mEqLow.setLowShelf(sr, 150.0f, lowGainDb);
    mEqMid.setPeaking(sr, 1000.0f, midGainDb);
    mEqHigh.setHighShelf(sr, 6000.0f, highGainDb);
}

void AudioEngine::setSoundGoodizer(bool enabled, int mode, float amount) {
    mSoundGoodizerEnabled = enabled;
    mSoundGoodizerMode = mode;
    mSoundGoodizerAmount = amount;
    mSoundGoodizer.setParams(enabled, mode, amount);
}

void AudioEngine::setMasterReverb(bool enabled, float size, float decay, float damp, float mix) {
    mMasterReverbEnabled = enabled;
    mMasterReverbSize = size;
    mMasterReverbDecay = decay;
    mMasterReverbDamp = damp;
    mMasterReverbMix = mix;
    mMasterReverb.setParams(enabled, size, decay, damp, mix);
}

void AudioEngine::setMasterDelay(bool enabled, float timeSec, float feedback, float mix, bool pingPong) {
    mMasterDelayEnabled = enabled;
    mMasterDelayTime = timeSec;
    mMasterDelayFeedback = feedback;
    mMasterDelayMix = mix;
    mMasterDelayPingPong = pingPong;
    mMasterDelay.setParams(enabled, timeSec, feedback, mix, pingPong);
}

void AudioEngine::setMasterChorus(bool enabled, float rateHz, float depthMs, float mix) {
    mMasterChorusEnabled = enabled;
    mMasterChorusRateHz = rateHz;
    mMasterChorusDepthMs = depthMs;
    mMasterChorusMix = mix;
    mMasterChorus.setParams(enabled, rateHz, depthMs, mix);
}

void AudioEngine::setMasterCompressor(bool enabled, float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupGainDb) {
    mMasterCompressorEnabled = enabled;
    mMasterCompressorThreshDb = thresholdDb;
    mMasterCompressorRatio = ratio;
    mMasterCompressorAttackMs = attackMs;
    mMasterCompressorReleaseMs = releaseMs;
    mMasterCompressorMakeupDb = makeupGainDb;
    mMasterCompressor.setParams(enabled, thresholdDb, ratio, attackMs, releaseMs, makeupGainDb);
}

void AudioEngine::setSpatialWidener(float amount) {
    mSpatialWidenerAmount = amount;
    mSpatialWidener.setAmount(amount);
}

void AudioEngine::setMasterPunch(float amount) {
    mMasterPunchAmount = amount;
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

void AudioEngine::processSurfaceFx(float *buf, int32_t numFrames, const SurfaceFxChainState &fx,
                                   StereoBiquad &filter, StereoDelay &delay, StereoReverb &reverb,
                                   bool withShimmer, StereoBiquad *brightness) {
    if (numFrames <= 0) return;
    float sr = static_cast<float>(mSampleRate > 0 ? mSampleRate : 48000);

    // Filter: low-pass whose cutoff opens with the amount (dark -> open).
    if (fx.filterOn.load(std::memory_order_relaxed)) {
        float amt = std::clamp(fx.filterAmt.load(std::memory_order_relaxed), 0.0f, 1.0f);
        float f0 = 180.0f * std::pow(120.0f, amt); // 180 Hz .. 21.6 kHz
        filter.setLowPass(sr, f0, 0.707f);
        filter.process(buf, numFrames);
    }

    // Brightness: high shelf. Shimmer doubles as a long bright tail on the same chain.
    if (brightness != nullptr && fx.brightnessOn.load(std::memory_order_relaxed)) {
        float amt = std::clamp(fx.brightnessAmt.load(std::memory_order_relaxed), 0.0f, 1.0f);
        brightness->setHighShelf(sr, 3500.0f, (amt - 0.5f) * 18.0f, 0.707f);
        brightness->process(buf, numFrames);
    }

    if (fx.delayOn.load(std::memory_order_relaxed)) {
        float amt = std::clamp(fx.delayAmt.load(std::memory_order_relaxed), 0.0f, 1.0f);
        delay.setParams(true, 0.16f + amt * 0.34f, 0.18f + amt * 0.42f, amt * 0.55f, false);
        delay.process(buf, numFrames);
    } else {
        delay.setParams(false, 0.3f, 0.3f, 0.0f, false);
    }

    if (fx.reverbOn.load(std::memory_order_relaxed) || (withShimmer && fx.shimmerOn.load(std::memory_order_relaxed))) {
        float revAmt = fx.reverbOn.load(std::memory_order_relaxed)
                       ? std::clamp(fx.reverbAmt.load(std::memory_order_relaxed), 0.0f, 1.0f) : 0.0f;
        float shimAmt = (withShimmer && fx.shimmerOn.load(std::memory_order_relaxed))
                        ? std::clamp(fx.shimmerAmt.load(std::memory_order_relaxed), 0.0f, 1.0f) : 0.0f;
        float size = std::clamp(0.42f + revAmt * 0.5f + shimAmt * 0.08f, 0.0f, 1.0f);
        float decay = std::clamp(0.5f + revAmt * 0.42f + shimAmt * 0.06f, 0.0f, 0.98f);
        float mix = std::clamp(std::max(revAmt * 0.6f, shimAmt * 0.7f), 0.0f, 1.0f);
        if (mix > 0.001f) {
            reverb.setParams(true, size, decay, 0.45f, mix);
            reverb.process(buf, numFrames);
        }
    }
}

void AudioEngine::applySurfaceFx(float *mixedBuf, const float *preDrumBuf, float *drumBuf,
                                 int32_t numFrames, bool padsAudible) {
    if (numFrames <= 0) return;
    size_t totalSamples = static_cast<size_t>(numFrames * 2);

    // ── DrumPad chain: the sampler owns its own buffer, so we can process it in
    //    isolation and add it back. This is a real, audible per-surface chain. ──
    bool padsFxOn =
        g_padsFx.filterOn.load(std::memory_order_relaxed) ||
        g_padsFx.brightnessOn.load(std::memory_order_relaxed) ||
        g_padsFx.delayOn.load(std::memory_order_relaxed) ||
        g_padsFx.reverbOn.load(std::memory_order_relaxed);
    if (padsFxOn) {
        if (mSurfaceFxBuffer.size() < totalSamples) mSurfaceFxBuffer.resize(totalSamples, 0.0f);
        if (padsAudible && drumBuf != nullptr) {
            std::copy(drumBuf, drumBuf + totalSamples, mSurfaceFxBuffer.data());
            processSurfaceFx(mSurfaceFxBuffer.data(), numFrames, g_padsFx,
                             mPadsFxFilter, mPadsFxDelay, mPadsFxReverb, false, &mPadsFxBrightness);
            // mixedBuf currently holds preDrum + dry drums: swap the dry drums for the wet ones.
            for (size_t i = 0; i < totalSamples; ++i) {
                mixedBuf[i] += mSurfaceFxBuffer[i] - drumBuf[i];
            }
        } else {
            // Voices are silent: feed silence so reverb/delay tails decay naturally.
            std::fill(mSurfaceFxBuffer.begin(), mSurfaceFxBuffer.begin() + totalSamples, 0.0f);
            processSurfaceFx(mSurfaceFxBuffer.data(), numFrames, g_padsFx,
                             mPadsFxFilter, mPadsFxDelay, mPadsFxReverb, false, &mPadsFxBrightness);
            for (size_t i = 0; i < totalSamples; ++i) mixedBuf[i] += mSurfaceFxBuffer[i];
        }
    }

    // ── TonicPad chain: channel 9 lives inside the shared synth bus and cannot be
    //    isolated per block, so it is driven through per-channel MIDI CCs, which
    //    fluidsynth applies to that channel only. Audible, and correctly scoped. ──
    bool tonicActive = false;
    float rev = 0.0f, cutoff = 1.0f;
    if (g_tonicFx.reverbOn.load(std::memory_order_relaxed)) {
        rev = std::clamp(g_tonicFx.reverbAmt.load(std::memory_order_relaxed), 0.0f, 1.0f);
        tonicActive = true;
    }
    if (g_tonicFx.shimmerOn.load(std::memory_order_relaxed)) {
        rev = std::clamp(std::max(rev, g_tonicFx.shimmerAmt.load(std::memory_order_relaxed)), 0.0f, 1.0f);
        tonicActive = true;
    }
    if (g_tonicFx.filterOn.load(std::memory_order_relaxed)) {
        cutoff = std::clamp(g_tonicFx.filterAmt.load(std::memory_order_relaxed), 0.0f, 1.0f);
        tonicActive = true;
    }
    if (tonicActive) {
        if (std::abs(rev - mTonicCcReverb) > 0.004f) {
            mTonicCcReverb = rev;
            mSynthEngine.setChannelReverb(9, rev);
        }
        if (std::abs(cutoff - mTonicCcCutoff) > 0.004f) {
            mTonicCcCutoff = cutoff;
            mSynthEngine.sendCC(9, 74, static_cast<int>(cutoff * 127.0f));
        }
    }
    (void)preDrumBuf;
}

int AudioEngine::renderDirect(int16_t *outputBuffer16, int32_t numFrames) {
    if (!this || !outputBuffer16 || numFrames <= 0) return 0;

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
    if (!floatBuf) {
        std::fill_n(outputBuffer16, totalSamples, static_cast<int16_t>(0));
        return 0;
    }

    // 1. Render unified 16-channel SoundFont engine (Faders 0..7, Drum 8, TonicPad 9)
    mSynthEngine.renderStereo(floatBuf, numFrames, false);

    // 2. Mix Dedicated SamplePlaybackEngine (DrumPad PCM WAVs)
    // Always render the sampler so queued triggers are drained this block; the
    // voice count is only known after rendering.
    if (mPadsIsolateBuffer.size() < totalSamples) mPadsIsolateBuffer.resize(totalSamples, 0.0f);
    mDrumSampler.renderStereo(mPadsIsolateBuffer.data(), numFrames, false);
    bool padsAudible = mDrumSampler.getActiveVoiceCount() > 0;
    if (padsAudible) {
        for (size_t i = 0; i < totalSamples; ++i) floatBuf[i] += mPadsIsolateBuffer[i];
    }
    applySurfaceFx(floatBuf, nullptr, padsAudible ? mPadsIsolateBuffer.data() : nullptr, numFrames, padsAudible);

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

bool AudioEngine::sourceRouterTrampoline(void *ctx, int channel, int type, int note,
                                         int velocity, int param1, int param2) {
    return static_cast<AudioEngine *>(ctx)->routeEventToSource(channel, type, note, velocity, param1, param2);
}

void AudioEngine::allNotesOffTrampoline(void *ctx, int channel) {
    AudioEngine *e = static_cast<AudioEngine *>(ctx);
    if (channel >= 0 && channel < 10) {
        e->mFx.allNotesOff(channel);
    } else {
        for (int s = 0; s < 10; ++s) e->mFx.allNotesOff(s);
    }
}

// Pushes the generator offsets of a track's shaper to its soundfont channel. The
// values are diffed against the last applied set so a slider drag does not flood
// the MIDI queue. Control thread only.
void AudioEngine::refreshTrackGens(int track) {
    if (track < 0 || track >= 8) return;
    aetherfx::GenUpdate u = mFx.instrumentGens(track);
    std::lock_guard<std::mutex> lk(mGenMutex);
    GenCache &c = mGenCache[track];
    if (!u.valid) {
        if (c.n > 0) {
            // Instrument removed: release the offsets back to the soundfont defaults.
            for (int i = 0; i < c.n; ++i) mSynthEngine.setGen(track, c.gen[i], 0.0f);
            c.n = 0;
        }
        if (c.pitchSens != -1) { mSynthEngine.setPitchBendSensitivity(track, 2); c.pitchSens = -1; }
        return;
    }
    for (int i = 0; i < u.gens.n; ++i) {
        bool known = false;
        for (int k = 0; k < c.n; ++k) {
            if (c.gen[k] == u.gens.gen[i]) {
                known = true;
                if (c.val[k] != u.gens.val[i]) { c.val[k] = u.gens.val[i]; mSynthEngine.setGen(track, c.gen[k], c.val[k]); }
                break;
            }
        }
        if (!known && c.n < 16) { c.gen[c.n] = u.gens.gen[i]; c.val[c.n] = u.gens.val[i]; ++c.n; mSynthEngine.setGen(track, u.gens.gen[i], u.gens.val[i]); }
    }
    if (c.pitchSens != u.perf.bendSemis) { c.pitchSens = u.perf.bendSemis; mSynthEngine.setPitchBendSensitivity(track, c.pitchSens); }
}

void AudioEngine::setTrackInstrument(int track, int fxId) {
    if (!mFx.setInstrument(track, fxId)) return;
    if (fxId < 0) {
        std::lock_guard<std::mutex> lk(mGenMutex);
        GenCache &c = mGenCache[track];
        for (int i = 0; i < c.n; ++i) mSynthEngine.setGen(track, c.gen[i], 0.0f);
        c.n = 0;
        if (c.pitchSens != -1) { mSynthEngine.setPitchBendSensitivity(track, 2); c.pitchSens = -1; }
        return;
    }
    refreshTrackGens(track);
}

void AudioEngine::setTrackInstrumentParam(int track, int param, float norm) {
    mFx.setInstrumentParam(track, param, norm);
    refreshTrackGens(track);
}

bool AudioEngine::routeEventToSource(int channel, int type, int note, int velocity, int param1, int param2) {
    if (!mFx.soundfontPaused(channel)) return false;
    switch (type) {
        case EngineMidiEvent::NOTE_ON:
            mFx.noteOn(channel, note, velocity);
            return true;
        case EngineMidiEvent::NOTE_OFF:
            mFx.noteOff(channel, note);
            return true;
        case EngineMidiEvent::PITCH_BEND:
            // FluidSynth uses 14-bit 0..16383 with 8192 as centre; the source API
            // expects -1..1.
            mFx.pitchBend(channel, (static_cast<float>(param1) - 8192.0f) / 8192.0f);
            return true;
        default:
            return false;
    }
}

// Æther FX render path. Returns false when the FX path is off or its buffers are
// not ready, so the caller falls back to the legacy mix unchanged.
bool AudioEngine::renderFxPath(float *outputBuffer, int32_t numFrames) {
    if (!mFxEnabled.load(std::memory_order_relaxed)) return false;
    if (numFrames <= 0 || mFxChannelBuffer.empty() || mFxStripBuffer.empty() || mFxMixBuffer.empty()) return false;

    const int channelCount = 10;  // 0..7 tracks, 8 Drum, 9 TonicPad
    size_t channelSamples = static_cast<size_t>(numFrames) * 2 * channelCount;
    size_t stripSamples = static_cast<size_t>(numFrames) * 2;
    if (channelSamples > mFxChannelBuffer.size() || stripSamples > mFxStripBuffer.size()) {
        std::fill(outputBuffer, outputBuffer + stripSamples, 0.0f);
        return true;
    }

    // 1. Per-channel render of the soundfont engine.
    if (!mSynthEngine.renderChannels(mFxChannelBuffer.data(), numFrames, channelCount)) {
        return false;  // multi-channel not armed: let the caller use renderStereo
    }

    std::fill(mFxMixBuffer.begin(), mFxMixBuffer.begin() + stripSamples, 0.0f);

    // 2. Each synth channel is a strip: a Source replaces the soundfont, every
    //    strip then runs its own effect chain before being summed into the mix.
    for (int s = 0; s < channelCount; ++s) {
        const float *src = mFxChannelBuffer.data() + static_cast<size_t>(s) * 2;
        float *strip = mFxStripBuffer.data();
        if (mFx.soundfontPaused(s)) {
            std::fill(strip, strip + stripSamples, 0.0f);
        } else {
            for (int i = 0; i < numFrames; ++i) {
                strip[2 * i] = src[static_cast<size_t>(i) * 2 * channelCount];
                strip[2 * i + 1] = src[static_cast<size_t>(i) * 2 * channelCount + 1];
            }
        }
        mFx.processStrip(s, strip, numFrames);
        for (size_t i = 0; i < stripSamples; ++i) mFxMixBuffer[i] += strip[i];
    }

    // 3. DrumPad sampler is strip 10.
    mDrumSampler.renderStereo(mFxStripBuffer.data(), numFrames, false);
    mFx.processStrip(10, mFxStripBuffer.data(), numFrames);
    for (size_t i = 0; i < stripSamples; ++i) mFxMixBuffer[i] += mFxStripBuffer[i];

    // 4. Master chain, then the single conversion to the stream format.
    mFx.processMaster(mFxMixBuffer.data(), numFrames);

    // 5. The legacy master bus stays on the FX path. Switching the render over to the
    //    FX path used to drop it entirely: no glue compressor, no soft clipper and,
    //    worst of all, no limiter — so a hot preset clipped and cracked instead of
    //    being tamed, and the mix lost the character it had before the FX window.
    if (!mBypassMasterFX.load(std::memory_order_relaxed)) {
        float *mix = mFxMixBuffer.data();
        mMasterChorus.process(mix, numFrames);
        mMasterDelay.process(mix, numFrames);
        mMasterReverb.process(mix, numFrames);
        mSoundGoodizer.process(mix, numFrames);
        mMasterPunch.process(mix, numFrames);
        mSpatialWidener.process(mix, numFrames);
        mEqLow.process(mix, numFrames);
        mEqMid.process(mix, numFrames);
        mEqHigh.process(mix, numFrames);
        processMasterChain(mix, numFrames);
    }

    std::copy(mFxMixBuffer.begin(), mFxMixBuffer.begin() + stripSamples, outputBuffer);
    return true;
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

        if (renderFxPath(floatBuf, numFrames)) {
            for (size_t i = 0; i < totalSamples; ++i) {
                float s = std::clamp(floatBuf[i], -1.0f, 1.0f);
                outputBuffer16[i] = static_cast<int16_t>(s * 32767.0f);
            }
            return oboe::DataCallbackResult::Continue;
        }

        // 1. Render unified 16-channel Soundfont engine (tracks 0..7, Drum 8, TonicPad 9)
        mSynthEngine.renderStereo(floatBuf, numFrames, false);

        // 2. Mix Dedicated SamplePlaybackEngine (DrumPad PCM WAVs)
        // Always render the sampler so queued triggers are drained this block; the
        // voice count is only known after rendering.
        if (mPadsIsolateBuffer.size() < totalSamples) mPadsIsolateBuffer.resize(totalSamples, 0.0f);
        mDrumSampler.renderStereo(mPadsIsolateBuffer.data(), numFrames, false);
        bool padsAudible = mDrumSampler.getActiveVoiceCount() > 0;
        if (padsAudible) {
            for (size_t i = 0; i < totalSamples; ++i) floatBuf[i] += mPadsIsolateBuffer[i];
        }
        // Surface FX chains (TonicPad CCs + isolated DrumPad DSP)
        applySurfaceFx(floatBuf, nullptr, padsAudible ? mPadsIsolateBuffer.data() : nullptr, numFrames, padsAudible);

        if (!mBypassMasterFX.load(std::memory_order_relaxed)) {
            float maxAbs = 0.0f;
            for (size_t i = 0; i < totalSamples; ++i) {
                float absVal = std::abs(floatBuf[i]);
                if (absVal > maxAbs) maxAbs = absVal;
            }

            // 3. Apply Master Delay & Chorus
            mMasterChorus.process(floatBuf, numFrames);
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

        if (renderFxPath(outputBuffer, numFrames)) {
            return oboe::DataCallbackResult::Continue;
        }

        // 1. Render unified 16-channel Soundfont engine (tracks 0..7, Drum 8, TonicPad 9)
        mSynthEngine.renderStereo(outputBuffer, numFrames, false);

        // 2. Mix Dedicated SamplePlaybackEngine (DrumPad PCM WAVs)
        // Always render the sampler so queued triggers are drained this block; the
        // voice count is only known after rendering.
        if (mPadsIsolateBuffer.size() < totalSamples) mPadsIsolateBuffer.resize(totalSamples, 0.0f);
        mDrumSampler.renderStereo(mPadsIsolateBuffer.data(), numFrames, false);
        bool padsAudible = mDrumSampler.getActiveVoiceCount() > 0;
        if (padsAudible) {
            for (size_t i = 0; i < totalSamples; ++i) outputBuffer[i] += mPadsIsolateBuffer[i];
        }
        applySurfaceFx(outputBuffer, nullptr, padsAudible ? mPadsIsolateBuffer.data() : nullptr, numFrames, padsAudible);

        if (!mBypassMasterFX.load(std::memory_order_relaxed)) {
            float maxAbs = 0.0f;
            for (size_t i = 0; i < totalSamples; ++i) {
                float absVal = std::abs(outputBuffer[i]);
                if (absVal > maxAbs) maxAbs = absVal;
            }

            // 3. Apply Master Delay & Chorus
            mMasterChorus.process(outputBuffer, numFrames);
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

    // 1. Smooth master bus compressor to glue tracks and elevate quiet details
    mMasterCompressor.process(floatBuf, numFrames);

    // 2. High-performance soft clipper to prevent saturation and boost perceived volume safely
    mSoftClipper.process(floatBuf, numFrames);

    // Safety peak clamp guard to prevent hardware out-of-range issues under extreme feedback/accidents
    for (int32_t i = 0; i < numFrames; ++i) {
        floatBuf[2 * i] = std::clamp(floatBuf[2 * i], -1.0f, 1.0f);
        floatBuf[2 * i + 1] = std::clamp(floatBuf[2 * i + 1], -1.0f, 1.0f);
    }
}

#else
AudioEngine::AudioEngine() = default;
AudioEngine::~AudioEngine() = default;
bool AudioEngine::start(int driverType) { return true; }
void AudioEngine::stop() {}
#endif

extern "C" JNIEXPORT void JNICALL
Java_com_soundstage_mixer_audio_NativeAudioBridge_setTonicFxChain(
    JNIEnv* env, jobject thiz,
    jboolean filterOn, jfloat filterAmt,
    jboolean brightnessOn, jfloat brightnessAmt,
    jboolean delayOn, jfloat delayAmt,
    jboolean reverbOn, jfloat reverbAmt,
    jboolean shimmerOn, jfloat shimmerAmt) {
    (void)env; (void)thiz;
    g_tonicFx.filterOn.store(filterOn);
    g_tonicFx.filterAmt.store(filterAmt);
    g_tonicFx.brightnessOn.store(brightnessOn);
    g_tonicFx.brightnessAmt.store(brightnessAmt);
    g_tonicFx.delayOn.store(delayOn);
    g_tonicFx.delayAmt.store(delayAmt);
    g_tonicFx.reverbOn.store(reverbOn);
    g_tonicFx.reverbAmt.store(reverbAmt);
    g_tonicFx.shimmerOn.store(shimmerOn);
    g_tonicFx.shimmerAmt.store(shimmerAmt);
}

extern "C" JNIEXPORT void JNICALL
Java_com_soundstage_mixer_audio_NativeAudioBridge_setPadsFxChain(
    JNIEnv* env, jobject thiz,
    jboolean filterOn, jfloat filterAmt,
    jboolean brightnessOn, jfloat brightnessAmt,
    jboolean delayOn, jfloat delayAmt,
    jboolean reverbOn, jfloat reverbAmt) {
    (void)env; (void)thiz;
    g_padsFx.filterOn.store(filterOn);
    g_padsFx.filterAmt.store(filterAmt);
    g_padsFx.brightnessOn.store(brightnessOn);
    g_padsFx.brightnessAmt.store(brightnessAmt);
    g_padsFx.delayOn.store(delayOn);
    g_padsFx.delayAmt.store(delayAmt);
    g_padsFx.reverbOn.store(reverbOn);
    g_padsFx.reverbAmt.store(reverbAmt);
}
