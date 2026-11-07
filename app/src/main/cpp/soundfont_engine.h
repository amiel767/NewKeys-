#ifndef DAWSTUDIO_SOUNDFONT_ENGINE_H
#define DAWSTUDIO_SOUNDFONT_ENGINE_H

#include "fx_arp.h"

#if __has_include(<fluidsynth.h>)
#include <fluidsynth.h>
#else
// Fallback definitions if fluidsynth headers are unavailable
typedef void fluid_settings_t;
typedef void fluid_synth_t;
typedef void fluid_sfont_t;
typedef void fluid_preset_t;
#define FLUID_OK 0
#define FLUID_FAILED -1
inline fluid_settings_t* new_fluid_settings() { return nullptr; }
inline int delete_fluid_settings(fluid_settings_t*) { return 0; }
inline fluid_synth_t* new_fluid_synth(fluid_settings_t*) { return nullptr; }
inline int delete_fluid_synth(fluid_synth_t*) { return 0; }
inline int fluid_settings_setnum(fluid_settings_t*, const char*, double) { return 0; }
inline int fluid_settings_setint(fluid_settings_t*, const char*, int) { return 0; }
inline int fluid_synth_sfload(fluid_synth_t*, const char*, int) { return -1; }
inline int fluid_synth_sfunload(fluid_synth_t*, int, int) { return -1; }
inline int fluid_synth_program_select(fluid_synth_t*, int, int, int, int) { return -1; }
inline int fluid_synth_program_change(fluid_synth_t*, int, int) { return -1; }
inline int fluid_synth_bank_select(fluid_synth_t*, int, int) { return -1; }
inline int fluid_synth_noteon(fluid_synth_t*, int, int, int) { return 0; }
inline int fluid_synth_noteoff(fluid_synth_t*, int, int) { return 0; }
inline int fluid_synth_all_notes_off(fluid_synth_t*, int) { return 0; }
inline int fluid_synth_all_sounds_off(fluid_synth_t*, int) { return 0; }
inline int fluid_synth_pitch_bend(fluid_synth_t*, int, int) { return 0; }
inline int fluid_synth_cc(fluid_synth_t*, int, int, int) { return 0; }
inline int fluid_synth_write_float(fluid_synth_t*, int, void*, int, int, void*, int, int) { return 0; }
inline fluid_sfont_t* fluid_synth_get_sfont_by_id(fluid_synth_t*, int) { return nullptr; }
inline void fluid_sfont_iteration_start(fluid_sfont_t*) {}
inline fluid_preset_t* fluid_sfont_iteration_next(fluid_sfont_t*) { return nullptr; }
inline const char* fluid_preset_get_name(fluid_preset_t*) { return ""; }
inline int fluid_preset_get_banknum(fluid_preset_t*) { return 0; }
inline int fluid_preset_get_num(fluid_preset_t*) { return 0; }
inline void fluid_synth_set_gain(fluid_synth_t*, float) {}
inline int fluid_synth_set_polyphony(fluid_synth_t*, int) { return 0; }
inline int fluid_synth_get_polyphony(fluid_synth_t*) { return 0; }
inline int fluid_synth_process(fluid_synth_t*, int, int, float **, int, float **) { return 0; }
inline int fluid_synth_set_gen(fluid_synth_t*, int, int, float) { return 0; }
inline float fluid_synth_get_gen(fluid_synth_t*, int, int) { return 0.0f; }
inline int fluid_synth_pitch_wheel_sens(fluid_synth_t*, int, int) { return 0; }
inline int fluid_synth_channel_pressure(fluid_synth_t*, int, int) { return 0; }
#endif

#include <atomic>
#include <array>
#include <mutex>
#include <string>
#include <vector>
#include <unordered_map>

constexpr int kFaderPolyphony = 128;
constexpr int kPadPolyphony = 64;

struct NativePresetInfo {
    int bank;
    int preset;
    std::string name;
};

struct EngineMidiEvent {
    enum Type {
        NOTE_ON,
        NOTE_OFF,
        ALL_NOTES_OFF,
        PITCH_BEND,
        CC,
        PROGRAM_CHANGE,
        PROGRAM_SELECT,
        // Shaper generator offsets. These must run on the audio thread because the
        // synth is not thread-safe, so they travel through this same queue.
        SET_GEN,
        PITCH_SENS,
        CHANNEL_PRESSURE,
        // Hard panic. Runs on the audio thread like the rest of the queue, so it is
        // ordered after every note event already queued: the notes stop first, then
        // the reverb tail is flushed. A control-thread reset would have raced the
        // events still in the queue and left the tail ringing.
        PANIC
    } type;
    int channel;
    int note;
    int velocity;
    int param1;
    int param2;
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

struct VoiceAuditSnapshot {
    int totalVoices = 0;
    int activeHeldVoices = 0;
    int sustainedVoices = 0;
    int releaseVoices = 0;
    int audibleVoices = 0;
    int silentVoices = 0;
    int voiceSteals = 0;
    int voicesCreatedTotal = 0;
    int voicesFinishedTotal = 0;
    int maxVoicesObserved = 0;
    int renderDurationUs = 0;
};

struct NoteOnAuditRecord {
    uint64_t timestampNs = 0;
    int channel = 0;
    int note = 0;
    int velocity = 0;
    int voicesBefore = 0;
    int voicesAfter = 0;
    int deltaVoices = 0;
};

struct NoteOffAuditRecord {
    uint64_t timestampNs = 0;
    int channel = 0;
    int note = 0;
    int voicesBefore = 0;
    int releaseVoicesAfter = 0;
};

/**
 * Autonomous FluidSynth instance. Multiple instances can co-exist
 * (e.g. FaderEngine, PadEngine, DrumEngine) with independent SoundFont banks and MIDI channels.
 */
class SoundfontEngine {
public:
    static constexpr int kMaxChannels = 16;
    // Stereo output groups the synth actually renders. FluidSynth always renders
    // every group declared at init, whether or not anything is routed to it, and
    // this number cannot be changed without recreating the synth. The app only
    // addresses MIDI channels 0..9 (8 tracks, Drum, TonicPad), so declaring 16
    // groups made every block pay for six silent ones. This is the app's cost
    // ceiling: it must stay >= the highest MIDI channel used.
    static constexpr int kAudioGroups = 10;
    // Per-channel scratch capacity in frames. 4096 covers every Oboe burst the
    // engine requests with a wide margin and keeps the multi-channel buffers out
    // of the audio callback's allocation path.
    static constexpr int kChannelFrameCapacity = 4096;

    bool init(int sampleRate, int polyphony = kFaderPolyphony, const char* instanceName = "FaderEngine");
    void destroy();
    void destroyInternal();
    bool isInitialized() const { return mSynth != nullptr; }

    int loadSoundFont(const std::string &absolutePath);
    int unloadSoundFont(int sfontId);
    void unloadAllSoundFonts();
    std::vector<NativePresetInfo> listPresets(int soundFontId);
    bool selectProgram(int channel, int soundFontId, int bank, int preset);
    bool programChange(int channel, int program);

    void noteOn(int channel, int midiNote, int velocity);
    void noteOff(int channel, int midiNote);
    void allNotesOff(int channel);

    /**
     * Re-emits the last note events that could not be queued.
     *
     * The event ring buffer is finite (2048). `push()` returns false when it is
     * full, and a dropped NOTE_OFF is the worst kind of drop: the note has no way
     * to stop, so it rings forever — the stuck notes reported on every Æther
     * instrument. NoteOns are rate-limited on the audio thread (48/block) but
     * NoteOffs are not, so a dense controller stream (arpeggiator, MPE, a sweep of
     * CCs) can outrun the drain and lose exactly the events that end notes.
     *
     * Called from the control thread at a low rate (the UI tick), not per event.
     * NoteOns are kept too so their pairing NoteOff cannot be applied before the
     * note exists; the note simply starts late rather than never stopping.
     */
    /**
     * Retente les evenements refuses depuis le thread de controle.
     *
     * Meme discipline de verrou que [pushEvent] : le ring n'a jamais deux
     * producteurs a la fois.
     */
    void resendDroppedEvents() {
        std::lock_guard<std::mutex> lk(mDroppedMutex);
        size_t i = 0;
        while (i < mDropped.size()) {
            if (!mEventQueue.push(mDropped[i])) break;   // plus de place : au prochain tick
            mDropped.erase(mDropped.begin() + i);
        }
    }

    /**
     * Retente les evenements refuses, SANS jamais bloquer le thread audio.
     *
     * Appele en fin de drain : la file vient d'etre videe, donc c'est le moment ou
     * la place est disponible.  `try_lock` : si le thread de controle tient deja le
     * verrou, on repasse au bloc suivant plutot que de risquer une inversion de
     * priorite sur le thread audio.
     */
    void retryDroppedLockFree() {
        std::unique_lock<std::mutex> lk(mDroppedMutex, std::try_to_lock);
        if (!lk.owns_lock() || mDropped.empty()) return;
        size_t i = 0;
        while (i < mDropped.size()) {
            if (!mEventQueue.push(mDropped[i])) break;   // plus de place : au bloc suivant
            mDropped.erase(mDropped.begin() + i);
        }
    }
    void sendCC(int channel, int cc, int value);

    // ---- Arpeggiator -------------------------------------------------------
    // The arp sits BETWEEN the player and the engine: it never makes sound, it
    // turns held notes into a stream of notes. There is ONE arp PER CHANNEL
    // (0..kMaxChannels-1): any track of a patch can carry one, and several can
    // run at once. Notes sent to an armed channel feed that channel's arp
    // instead of the synth, and the notes the arp emits sound on that channel.
    // All of these are control-thread calls; the arps themselves are lock-free.
    void setArpArmed(int channel, bool armed) {
        std::lock_guard<std::recursive_mutex> lock(mMutex);
        if (channel < 0 || channel >= kMaxChannels) return;
        const uint32_t bit = 1u << channel;
        if (armed) {
            mArpArmedMask.fetch_or(bit, std::memory_order_relaxed);
            mArpEmitMask.fetch_or(bit, std::memory_order_relaxed);
        } else {
            mArpArmedMask.fetch_and(~bit, std::memory_order_relaxed);
            // L'emission survit au desarmement : les notes deja allumees doivent
            // encore pouvoir sortir (sinon elles sonneraient sans fin).
        }
        // Armer le canal EST l'interrupteur : sans ca, les notes interceptees
        // seraient avalees et rien ne sonnerait. Une seule source de verite.
        mArps[channel].setParam(aetherfx::ARP_ON, armed ? 1.f : 0.f);
        mArps[channel].allNotesOff();
    }
    bool isArpArmed(int channel) const {
        return channel >= 0 && channel < kMaxChannels &&
               (mArpArmedMask.load(std::memory_order_relaxed) & (1u << channel)) != 0;
    }
    // ARP_ON is driven by setArpArmed (arming IS the switch), so a host push of
    // it is ignored: two writers would fight and the notes could be swallowed.
    void setArpParam(int channel, int index, float norm) {
        if (channel < 0 || channel >= kMaxChannels) return;
        if (index == aetherfx::ARP_ON) return;
        mArps[channel].setParam(index, norm);
    }
    // The tempo is global (the mixer bar), so it lands on every arp.
    void setArpTempo(double bpm) {
        for (auto &a : mArps) a.setTempo(bpm);
    }
    void setArpStep(int channel, int index, bool on, float vel) {
        if (channel < 0 || channel >= kMaxChannels) return;
        mArps[channel].setStep(index, on, vel);
    }
    void arpAllNotesOff(int channel) {
        if (channel < 0 || channel >= kMaxChannels) return;
        mArps[channel].allNotesOff();
    }
    void arpAllNotesOffAll() {
        for (auto &a : mArps) a.allNotesOff();
    }

    /**
     * Hard panic: silences every voice *and* flushes the FluidSynth reverb/chorus
     * tail. `allNotesOff` only stops the notes, so with the synth reverb engaged a
     * note kept ringing for seconds after the panic button. Queued like every other
     * event, so it is applied after the notes already in flight.
     */
    void panic();

    // Shaper generator offsets and pitch-bend sensitivity. The synth is not
    // thread-safe, so these are queued and applied by drainEvents() on the audio
    // thread. Values are native SoundFont units (cents, seconds, etc.).
    void setGen(int channel, int gen, float value) {
        if (channel < 0 || channel >= kMaxChannels) return;
        EngineMidiEvent ev;
        ev.type = EngineMidiEvent::SET_GEN;
        ev.channel = channel;
        ev.param1 = gen;
        ev.param2 = static_cast<int>(std::lround(value));
        mEventQueue.push(ev);
    }
    void setPitchBendSensitivity(int channel, int semitones) {
        if (channel < 0 || channel >= kMaxChannels) return;
        EngineMidiEvent ev;
        ev.type = EngineMidiEvent::PITCH_SENS;
        ev.channel = channel;
        ev.param1 = semitones;
        mEventQueue.push(ev);
    }

    /** Channel pressure (aftertouch). FluidSynth maps it to the SoundFont mod env. */
    void channelPressure(int channel, int value) {
        if (channel < 0 || channel >= kMaxChannels) return;
        EngineMidiEvent ev;
        ev.type = EngineMidiEvent::CHANNEL_PRESSURE;
        ev.channel = channel;
        ev.param1 = std::clamp(value, 0, 127);
        mEventQueue.push(ev);
    }
    void pitchBend(int channel, int bendValue);

    void setChannelVolume(int channel, float volume01);
    void setChannelPan(int channel, float pan);
    void setChannelTransposeSemitones(int channel, int semitones);
    void setChannelReverb(int channel, float reverb01);
    void setChannelChorus(int channel, float chorus01);
    void setGain(float gain);
    void setPolyphony(int polyphony);
    int getActiveVoiceCount() const;

    void renderStereo(float *outputBuffer, int32_t numFrames, bool accumulate = false);

    // Per-channel output for the FX mixer. `out` is channel-major: the stereo
    // pair of channel c occupies [c * 2 * numFrames, (c + 1) * 2 * numFrames),
    // left/right interleaved inside it. Channel-major (rather than sample-major)
    // keeps both this interleave and the consumer's copy to the strip buffer
    // contiguous: the sample-major layout made both passes stride by 2 *
    // channelCount, which is what the FX path paid for on every block.
    // Returns false when multi-channel output is not enabled, so the caller can
    // fall back to renderStereo. Never allocates: buffers are sized in init().
    bool renderChannels(float *out, int32_t numFrames, int channelCount);
    /**
     * Renders every audio group into the internal planar scratch (mChannelPtrs)
     * instead of an interleaved buffer. renderStereo() folds them there, which
     * avoids the planar -> interleaved -> fold round trip. Returns false when
     * multi-channel mode is off, the synth is missing or the frame count exceeds
     * the pre-sized scratch.
     */
    bool renderPlanar(int32_t numFrames, int channelCount);

    // Multi-channel mode is opt-in: the per-channel path has to be validated on
    // a device (sum of channels must equal the mono mix) before it is trusted.
    void setMultiChannelEnabled(bool enabled) { mMultiChannelEnabled = enabled; }
    bool isMultiChannelEnabled() const { return mMultiChannelEnabled; }

    // Note routing for a strip whose instrument is a Source: return true when the
    // event was consumed by the source (so FluidSynth must not see it). Called on
    // the audio thread from drainEvents(). Type/fields match EngineMidiEvent.
    using SourceRouterFn = bool (*)(void *ctx, int channel, int type, int note,
                                    int velocity, int param1, int param2);
    void setSourceRouter(SourceRouterFn fn, void *ctx) { mSourceRouter = fn; mSourceRouterCtx = ctx; }
    // Called for every ALL_NOTES_OFF so a source can drop its held notes too.
    using AllNotesOffFn = void (*)(void *ctx, int channel);
    void setAllNotesOffHook(AllNotesOffFn fn, void *ctx) { mAllNotesOffHook = fn; mAllNotesOffHookCtx = ctx; }

    // FaderEngine Voice Audit
    VoiceAuditSnapshot getLatestAuditSnapshot() const;
    void resetAuditCounters();

private:
    // Applies every queued MIDI event to the synth, routing notes to a Source
    // when one is selected on that strip. Audio thread only.
    void drainEvents();
    void executeProgramSelect(int channel, int soundFontId, int bank, int preset);
    std::string mInstanceName = "FaderEngine";
    int mConfiguredPolyphony = kFaderPolyphony;
    fluid_settings_t *mSettings = nullptr;
    fluid_synth_t *mSynth = nullptr;
    mutable std::recursive_mutex mMutex;
    std::array<std::atomic<int>, kMaxChannels> mTransposeSemitones{};
    std::array<std::atomic<int>, kMaxChannels> mChannelVolumes{};
    std::vector<float> mTempRenderBuffer;
    // Multi-channel scratch, sized once in init(); the audio callback must never
    // resize. mChannelPtrs aliases into mChannelBuffer for fluid_synth_process.
    std::vector<float> mChannelBuffer;
    std::array<float *, 2 * kMaxChannels> mChannelPtrs{};
    // renderStereo() folds every audio group into one stereo pair. Sized once in
    // init() (2 * kChannelFrameCapacity) so the fold never allocates on the audio
    // thread, where renderChannels() hands it numFrames <= kChannelFrameCapacity.
    std::vector<float> mStereoFold;
    bool mMultiChannelEnabled = false;
    // Note routing hooks (set from the control thread before the stream opens).
    SourceRouterFn mSourceRouter = nullptr;
    void *mSourceRouterCtx = nullptr;
    AllNotesOffFn mAllNotesOffHook = nullptr;
    void *mAllNotesOffHookCtx = nullptr;
    LockFreeRingBuffer<EngineMidiEvent, 2048> mEventQueue;

    /**
     * Events refused by a full queue, kept so the control thread can retry them.
     * Bounded on purpose: past a hundred events the situation is a stalled audio
     * thread, and retrying forever would only grow the backlog. Only the control
     * thread touches these.
     */
    static constexpr size_t kMaxDropped = 256;
    std::vector<EngineMidiEvent> mDropped;
    std::mutex mDroppedMutex;

    /**
     * Pushes `ev`, stashing it for a retry when the queue is full.
     *
     * The mutex is held across the ring push, not just around the stash. The ring is
     * single-producer/single-consumer: the retry path pushes from the audio thread,
     * so without this the control thread and the audio thread would be two producers
     * and could tear the tail index.
     */
    bool pushEvent(const EngineMidiEvent& ev) {
        std::lock_guard<std::mutex> lk(mDroppedMutex);
        if (mEventQueue.push(ev)) return true;
        if (mDropped.size() < kMaxDropped) mDropped.push_back(ev);
        return false;
    }
    bool mWasHighLoad = false;

    // ---- Arpeggiator -------------------------------------------------------
    // One arp PER CHANNEL. A channel's bit in `mArpArmedMask` means the arp is
    // live there (notes sent to it feed the arp instead of FluidSynth); the
    // matching bit in `mArpEmitMask` means the arp still has to emit its own
    // notes (it survives disarm so already-started notes can be released).
    std::array<aetherfx::ArpEngine, kMaxChannels> mArps;
    // Lues par le thread audio, ecrites par le thread de controle : atomiques.
    std::atomic<uint32_t> mArpArmedMask{0};
    std::atomic<uint32_t> mArpEmitMask{0};

    /** Le pont arp -> synthé : la frame d'émission découpe le rendu du bloc. */
    static void arpEmitTrampoline(void *ctx, int frame, int note, int velocity, bool on) {
        static_cast<SoundfontEngine *>(ctx)->arpEmit(frame, note, velocity, on);
    }
    void arpEmit(int frame, int note, int velocity, bool on);

    /**
     * Runs every live arp for this block. Returns true when at least one channel
     * is armed. The notes the arps emit are written into `arpOut_` with their
     * frame offset and their channel, and the caller splits the FluidSynth render
     * at those offsets so a 1/32 at 120 BPM does not jitter by a whole block.
     */
    bool runArp(int numFrames);
    struct ArpNote { int frame; int channel; int note; int velocity; bool on; };
    static constexpr int kArpMaxOut = 128;
    ArpNote arpOut_[kArpMaxOut]{};
    int arpOutCount_ = 0;
    /** Canal de l'arp en cours d'émission (thread audio uniquement) : le
     *  trampoline n'a pas de paramètre de canal, il le lit ici. */
    int arpEmitCurrent_ = -1;
    void applyArpEvent(const ArpNote &e);
    /**
     * Rend le bloc en le DÉCOUPANT à chaque note de l'arp, pour que la note
     * tombe sur son échantillon exact au lieu du bord de bloc. `mChannelPtrs`
     * est décalé par segment ; l'appelant a déjà vidé la zone.
     */
    void fluidProcessSegmented(int32_t numFrames, int channelCount);

    // Temporary non-audio intrusive instrumentation
    std::atomic<int> mAuditTotalVoices{0};
    std::atomic<int> mAuditActiveHeldVoices{0};
    std::atomic<int> mAuditSustainedVoices{0};
    std::atomic<int> mAuditReleaseVoices{0};
    std::atomic<int> mAuditAudibleVoices{0};
    std::atomic<int> mAuditSilentVoices{0};
    std::atomic<int> mAuditVoiceSteals{0};
    std::atomic<int> mAuditVoicesCreatedTotal{0};
    std::atomic<int> mAuditVoicesFinishedTotal{0};
    std::atomic<int> mAuditMaxVoicesObserved{0};
    std::atomic<int> mAuditLastRenderDurationUs{0};

    LockFreeRingBuffer<VoiceAuditSnapshot, 1024> mAuditSnapshots;
    LockFreeRingBuffer<NoteOnAuditRecord, 512> mNoteOnRecords;
    LockFreeRingBuffer<NoteOffAuditRecord, 512> mNoteOffRecords;

    // Shared SoundFont pool for RAM optimization (zero memory duplication)
    std::unordered_map<std::string, int> mPathToSfontId;
    std::unordered_map<int, int> mSfontRefCount;
    std::unordered_map<int, std::string> mSfontIdToPath;
};

#endif //DAWSTUDIO_SOUNDFONT_ENGINE_H
