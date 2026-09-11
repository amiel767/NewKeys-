#ifndef SAMPLE_PLAYBACK_ENGINE_H
#define SAMPLE_PLAYBACK_ENGINE_H

#include <cstdint>
#include <cstddef>
#include <array>
#include <vector>
#include <atomic>
#include <mutex>
#include <unordered_map>
#include <string>
#include <memory>
#include <algorithm>
#include <cmath>

/**
 * High-Performance, Lock-Free PCM Sample Playback Engine.
 * Designed for real-time DrumPad and one-shot sample triggering without FluidSynth or GC overhead.
 */
class SamplePlaybackEngine {
public:
    static constexpr size_t kMaxVoices = 16;
    static constexpr size_t kCommandQueueCapacity = 256;
    static constexpr int kDefaultSampleRate = 48000;

    struct AudioSample {
        int id = -1;
        std::string name;
        std::vector<float> pcmData; // Interleaved stereo float32 samples
        size_t totalFrames = 0;
        int channels = 2;
        int sampleRate = kDefaultSampleRate;
    };

    struct SamplerVoice {
        int sampleId = -1;
        const float* pcmData = nullptr;
        size_t totalFrames = 0;
        double readPosition = 0.0;
        double pitchRatio = 1.0;
        float gainLeft = 1.0f;
        float gainRight = 1.0f;
        bool active = false;
        uint64_t triggerTimestamp = 0;
    };

    enum class CommandType : uint8_t {
        TRIGGER,
        STOP,
        STOP_ALL,
        SET_VOLUME,
        SET_PAN
    };

    struct SamplerCommand {
        CommandType type;
        int sampleId;
        float velocity;
        float pan;
        float volume;
    };

    template <typename T, size_t Capacity>
    class LockFreeRingBuffer {
    public:
        LockFreeRingBuffer() : mHead(0), mTail(0) {}

        bool push(const T& item) {
            size_t currentTail = mTail.load(std::memory_order_relaxed);
            size_t nextTail = (currentTail + 1) % Capacity;
            if (nextTail == mHead.load(std::memory_order_acquire)) {
                return false; // Full
            }
            mBuffer[currentTail] = item;
            mTail.store(nextTail, std::memory_order_release);
            return true;
        }

        bool pop(T& item) {
            size_t currentHead = mHead.load(std::memory_order_relaxed);
            if (currentHead == mTail.load(std::memory_order_acquire)) {
                return false; // Empty
            }
            item = mBuffer[currentHead];
            mHead.store((currentHead + 1) % Capacity, std::memory_order_release);
            return true;
        }

        bool peek(T& item) const {
            size_t currentHead = mHead.load(std::memory_order_relaxed);
            if (currentHead == mTail.load(std::memory_order_acquire)) {
                return false; // Empty
            }
            item = mBuffer[currentHead];
            return true;
        }

        void clear() {
            mHead.store(0, std::memory_order_relaxed);
            mTail.store(0, std::memory_order_relaxed);
        }

    private:
        std::array<T, Capacity> mBuffer;
        std::atomic<size_t> mHead;
        std::atomic<size_t> mTail;
    };

    SamplePlaybackEngine();
    ~SamplePlaybackEngine();

    bool init(int sampleRate = kDefaultSampleRate);

    // Audio Thread Render (100% Lock-Free, Zero-Allocation, Real-Time Safe)
    void renderStereo(float* outputBuffer, int32_t numFrames, bool accumulate = true);

    // Thread-safe Sample Management (Called from background/loading threads)
    int registerSamplePcm(int sampleId, const std::string& name, const float* interleavedStereo, size_t totalFrames, int sampleRate = kDefaultSampleRate);
    int loadWavFile(int sampleId, const std::string& filePath);
    int loadWavFromMemory(int sampleId, const std::string& name, const uint8_t* wavBytes, size_t byteCount);
    void unloadSample(int sampleId);
    void unloadAllSamples();

    // Lock-Free Control (Called from UI / JNI without mutexes)
    void triggerSample(int sampleId, float velocity01 = 0.9f, float pan = 0.0f);
    void stopSample(int sampleId);
    void stopAll();
    void setMasterVolume(float volume01);
    void setMasterPan(float pan);

    // Synthesize high-quality default acoustic drum set (Kick, Snare, Hat, Clap, Toms, Cymbal)
    void initDefaultDrumKit();

    // Profiling & Telemetry
    int getActiveVoiceCount() const { return mActiveVoiceCount.load(std::memory_order_relaxed); }
    uint64_t getTriggersReceived() const { return mTriggersReceived.load(std::memory_order_relaxed); }
    uint64_t getTriggersExecuted() const { return mTriggersExecuted.load(std::memory_order_relaxed); }
    uint64_t getVoiceSteals() const { return mVoiceSteals.load(std::memory_order_relaxed); }
    uint64_t getUnderruns() const { return mUnderruns.load(std::memory_order_relaxed); }
    int64_t getLastRenderDurationUs() const { return mLastRenderDurationUs.load(std::memory_order_relaxed); }

private:
    int allocateVoice(int sampleId);
    void processCommands();

    int mSampleRate = kDefaultSampleRate;
    std::atomic<float> mMasterVolume{0.85f};
    std::atomic<float> mMasterPan{0.0f};

    // Preallocated voice pool
    std::array<SamplerVoice, kMaxVoices> mVoices{};
    uint64_t mTimestampCounter = 0;

    // Lock-free command queue
    LockFreeRingBuffer<SamplerCommand, kCommandQueueCapacity> mCommandQueue;

    // Sample storage (Protected by mutex ONLY during load/unload; reader pointers are cached atomically)
    std::mutex mSampleMutex;
    std::unordered_map<int, std::shared_ptr<AudioSample>> mSamples;

    // Fast lock-free lookup table for audio thread (sampleId -> sample pointer)
    static constexpr int kMaxFastSampleId = 128;
    std::array<std::atomic<const AudioSample*>, kMaxFastSampleId> mFastSampleLookup{};

    // Telemetry & Metrics
    std::atomic<int> mActiveVoiceCount{0};
    std::atomic<uint64_t> mTriggersReceived{0};
    std::atomic<uint64_t> mTriggersExecuted{0};
    std::atomic<uint64_t> mVoiceSteals{0};
    std::atomic<uint64_t> mUnderruns{0};
    std::atomic<int64_t> mLastRenderDurationUs{0};
};

#endif // SAMPLE_PLAYBACK_ENGINE_H
