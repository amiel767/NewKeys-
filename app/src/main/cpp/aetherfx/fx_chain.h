// AetherFX chain + mixer strip.
// Threading contract:
//   control thread (UI/JNI): setEffect / clearSlot / setParam / setBypass / applyPreset / collectGarbage / meter*
//   audio thread:            process() only. No locks, no allocation, no logging.
#pragma once
#include <mutex>
#include "fx_amp.h"
#include "fx_bass.h"
#include "fx_ceiling.h"
#include "fx_core.h"
#include "fx_echo.h"
#include "fx_grip.h"
#include "fx_leads.h"
#include "fx_organ.h"
#include "fx_registry.h"
#include "fx_sax.h"
#include "fx_shaper.h"
#include "fx_synth.h"
#include "fx_voices.h"
#include "fx_space.h"
#include "fx_tone.h"

namespace aetherfx {

// Effects (track slots and master). Returns nullptr for ids that are not effects.
inline std::unique_ptr<Effect> makeEffect(int id, bool lookahead = false) {
  switch (id) {
    case FX_SPACE: return std::make_unique<SpaceReverb>();
    case FX_ECHO: return std::make_unique<EchoDelay>();
    case FX_TONE: return std::make_unique<ToneEq>();
    case FX_GRIP: return std::make_unique<GripCompressor>();
    case FX_CEILING: return std::make_unique<CeilingMaximizer>(lookahead ? 1.0f : 0.0f);
    case FX_AMP: return std::make_unique<AmpFx>();
    case FX_BASS: return std::make_unique<BassAmp>();
    case FX_VOICES: return std::make_unique<VoicesFx>();
    default: return nullptr;
  }
}
// Instruments (one per track): shapers modify the soundfont, sources replace it. Returns nullptr for other ids.
inline std::unique_ptr<Effect> makeInstrument(int id, bool = false) {
  switch (id) {
    case FX_PIANO: case FX_SAX: case FX_STRINGS: case FX_GUITAR: case FX_BRASS: return std::make_unique<ShaperFx>(id);
    case FX_ORGAN: return std::make_unique<OrganEngine>();
    case FX_SYNTH: return std::make_unique<SynthEngine>();
    case FX_LEADS: return std::make_unique<LeadsEngine>();
    case FX_SAX_AE: return std::make_unique<SaxEngine>();
    default: return nullptr;
  }
}

class FxChain {
 public:
  static constexpr int kMaxSlots = 8;
  using Factory = std::unique_ptr<Effect> (*)(int, bool);

  explicit FxChain(int slots, bool lookaheadLimiter = false, Factory factory = &makeEffect) : n_(std::min(slots, kMaxSlots)), lookahead_(lookaheadLimiter), factory_(factory) {}
  ~FxChain() {
    for (int i = 0; i < n_; ++i) delete slot_[i].fx.load();
    for (auto& g : garbage_) delete g.fx;
  }
  FxChain(const FxChain&) = delete;
  FxChain& operator=(const FxChain&) = delete;

  // Call before audio starts (and again if the sample rate changes), from the control thread.
  void prepare(double sampleRate, int maxBlock) {
    std::lock_guard<std::mutex> lk(ctl_);
    sr_ = sampleRate; maxBlock_ = maxBlock;
    dry_.assign(static_cast<size_t>(maxBlock) * 2, 0.f);
    for (int i = 0; i < n_; ++i) if (Effect* e = slot_[i].fx.load()) { e->prepare(sr_, maxBlock_); e->reset(); }
  }

  // ---- control thread ----
  // fxId < 0 clears the slot. The new effect starts with its default parameters.
  bool setEffect(int slot, int fxId) {
    if (slot < 0 || slot >= n_) return false;
    std::lock_guard<std::mutex> lk(ctl_);
    Effect* ne = nullptr;
    if (fxId >= 0) {
      auto e = factory_(fxId, lookahead_);
      if (!e) return false;
      e->prepare(sr_, maxBlock_);
      const auto& pi = info(fxId).params;
      for (size_t i = 0; i < pi.size(); ++i) e->setNorm(static_cast<int>(i), pi[i].def);
      ne = e.release();
    }
    Effect* old = slot_[slot].fx.exchange(ne, std::memory_order_acq_rel);
    slot_[slot].id.store(fxId, std::memory_order_relaxed);
    if (old) garbage_.push_back({old, epoch_.load(std::memory_order_acquire) + 3});
    return true;
  }
  void clearSlot(int slot) { setEffect(slot, -1); }
  void setParam(int slot, int param, float norm) {
    if (slot < 0 || slot >= n_) return;
    if (Effect* e = slot_[slot].fx.load(std::memory_order_acquire)) e->setNorm(param, norm);
  }
  void setBypass(int slot, bool b) { if (slot >= 0 && slot < n_) slot_[slot].bypass.store(b, std::memory_order_relaxed); }
  bool applyPreset(int slot, int presetIdx) {
    if (slot < 0 || slot >= n_) return false;
    const int id = slot_[slot].id.load(std::memory_order_relaxed);
    Effect* e = slot_[slot].fx.load(std::memory_order_acquire);
    if (id < 0 || !e) return false;
    const auto& ps = info(id).presets;
    if (presetIdx < 0 || presetIdx >= static_cast<int>(ps.size())) return false;
    for (size_t i = 0; i < ps[presetIdx].v.size(); ++i) e->setNorm(static_cast<int>(i), ps[presetIdx].v[i]);
    return true;
  }
  int effectId(int slot) const { return (slot >= 0 && slot < n_) ? slot_[slot].id.load(std::memory_order_relaxed) : -1; }
  bool bypassed(int slot) const { return slot_[slot].bypass.load(std::memory_order_relaxed); }
  float meter(int slot, int i) const {
    if (slot < 0 || slot >= n_) return 0.f;
    Effect* e = slot_[slot].fx.load(std::memory_order_acquire);
    return e ? e->meter(i) : 0.f;
  }
  // Retired effects are deleted only after the audio thread has run >= 3 more blocks.
  void collectGarbage() {
    std::lock_guard<std::mutex> lk(ctl_);
    const uint64_t now = epoch_.load(std::memory_order_acquire);
    for (size_t i = 0; i < garbage_.size();) {
      if (now >= garbage_[i].retireAt) { delete garbage_[i].fx; garbage_.erase(garbage_.begin() + i); } else ++i;
    }
  }
  int latencySamples() const {
    int l = 0;
    for (int i = 0; i < n_; ++i) if (Effect* e = slot_[i].fx.load(std::memory_order_acquire)) l += e->latencySamples();
    return l;
  }
  int slots() const { return n_; }
  Effect* peek(int slot) const { return (slot >= 0 && slot < n_) ? slot_[slot].fx.load(std::memory_order_acquire) : nullptr; }

  /** Ask the audio thread to clear every effect state on its next block. Control thread. */
  void requestPanic() { panic_.store(true, std::memory_order_release); }

  // ---- audio thread ----
  void process(float* io, int frames) {
    epoch_.fetch_add(1, std::memory_order_acq_rel);
    if (panic_.exchange(false, std::memory_order_acq_rel)) {
      // Panic: drop every tail (delay/reverb loops, filter state) so a runaway
      // feedback cannot keep ringing after the notes are off. Done here, on the
      // audio thread, because reset() is not safe to call while process() runs.
      for (int i = 0; i < n_; ++i) if (Effect* e = slot_[i].fx.load(std::memory_order_acquire)) e->reset();
    }
    if (frames > maxBlock_) frames = maxBlock_;  // never exceed what prepare() sized
    NoDenormals nd;
    const float xfStep = 1.0f / std::max(1.f, 0.010f * static_cast<float>(sr_));
    for (int i = 0; i < n_; ++i) {
      Slot& s = slot_[i];
      Effect* e = s.fx.load(std::memory_order_acquire);
      if (!e) continue;
      const bool byp = s.bypass.load(std::memory_order_relaxed);
      const float tgt = byp ? 0.f : 1.f;
      if (s.w <= 0.f && byp) { s.needReset = true; continue; }
      if (s.needReset) { e->reset(); s.needReset = false; }
      if (s.w >= 1.f && !byp) { e->process(io, frames); continue; }
      std::memcpy(dry_.data(), io, sizeof(float) * 2 * frames);
      e->process(io, frames);
      float w = s.w;
      for (int n = 0; n < frames; ++n) {
        w += (tgt > w) ? std::min(xfStep, tgt - w) : -std::min(xfStep, w - tgt);
        io[2 * n] = dry_[2 * n] + (io[2 * n] - dry_[2 * n]) * w;
        io[2 * n + 1] = dry_[2 * n + 1] + (io[2 * n + 1] - dry_[2 * n + 1]) * w;
      }
      s.w = w;
    }
  }

 private:
  struct Slot {
    std::atomic<Effect*> fx{nullptr};
    std::atomic<int> id{-1};
    std::atomic<bool> bypass{false};
    float w = 1.f;          // audio thread only
    bool needReset = false;  // audio thread only
  };
  struct Retired { Effect* fx; uint64_t retireAt; };
  int n_;
  bool lookahead_;
  Factory factory_;
  double sr_ = 48000.0;
  int maxBlock_ = 1024;
  Slot slot_[kMaxSlots];
  std::vector<float> dry_;
  std::mutex ctl_;
  std::vector<Retired> garbage_;
  std::atomic<uint64_t> epoch_{0};
  std::atomic<bool> panic_{false};
};

// A mixer strip: FX chain -> fader -> balance -> mute. Gains are smoothed per sample. Used for every track and the master.
class FxStrip {
 public:
  FxStrip(int slots, bool master = false) : chain(slots, master), master_(master) {}
  FxChain chain;

  void prepare(double sr, int maxBlock) { sr_ = sr; chain.prepare(sr, maxBlock); }
  void setGain(float lin) { gain_.store(lin < 0.f ? 0.f : lin, std::memory_order_relaxed); }
  void setPan(float pan) { pan_.store(std::max(-1.f, std::min(1.f, pan)), std::memory_order_relaxed); }
  void setMute(bool m) { mute_.store(m, std::memory_order_relaxed); }
  float peakL() const { return pkL_.load(std::memory_order_relaxed); }
  float peakR() const { return pkR_.load(std::memory_order_relaxed); }

  void process(float* io, int frames) {
    chain.process(io, frames);
    const float pan = pan_.load(std::memory_order_relaxed);
    const float g = mute_.load(std::memory_order_relaxed) ? 0.f : gain_.load(std::memory_order_relaxed);
    const float th = (pan + 1.f) * 0.78539816339f;       // constant-power balance
    const float tl = g * std::cos(th) * 1.41421356f, tr = g * std::sin(th) * 1.41421356f;
    if (!init_) { gl_ = tl; gr_ = tr; init_ = true; }
    const float dl = (tl - gl_) / frames, dr = (tr - gr_) / frames;
    float pl = 0.f, pr = 0.f;
    for (int n = 0; n < frames; ++n) {
      gl_ += dl; gr_ += dr;
      float l = io[2 * n] * gl_, r = io[2 * n + 1] * gr_;
      if (master_) {  // final safety: never output NaN or beyond full scale
        if (!(l == l)) l = 0.f; if (!(r == r)) r = 0.f;
        l = std::max(-1.f, std::min(1.f, l)); r = std::max(-1.f, std::min(1.f, r));
      }
      io[2 * n] = l; io[2 * n + 1] = r;
      pl = std::max(pl, std::fabs(l)); pr = std::max(pr, std::fabs(r));
    }
    gl_ = tl; gr_ = tr;
    pkL_.store(pl, std::memory_order_relaxed); pkR_.store(pr, std::memory_order_relaxed);
  }

 private:
  bool master_;
  double sr_ = 48000.0;
  std::atomic<float> gain_{1.f}, pan_{0.f}, pkL_{0.f}, pkR_{0.f};
  std::atomic<bool> mute_{false};
  float gl_ = 1.f, gr_ = 1.f;
  bool init_ = false;
};

// One mixer track: [instrument slot] -> [6 effects] -> fader/pan/mute. The track keeps its soundfont at all times:
// a SOURCE (Organ, Synth) only pauses it while selected; a SHAPER modifies it; no instrument = the soundfont as it is.
class TrackStrip {
 public:
  FxChain instrument{1, false, &makeInstrument};
  FxStrip strip{6, false};
  void prepare(double sr, int maxBlock) { instrument.prepare(sr, maxBlock); strip.prepare(sr, maxBlock); }
  bool setInstrument(int id) { return instrument.setEffect(0, id); }  // id < 0: back to the plain soundfont
  int instrumentId() const { return instrument.effectId(0); }
  bool hasSource() const { return kindOf(instrument.effectId(0)) == Kind::Source && !instrument.bypassed(0); }  // engine: skip FluidSynth rendering for this channel
  // note events forwarded to the active source (audio thread)
  void noteOn(int n, int v) { if (SourceFx* s = src()) s->noteOn(n, v); }
  void noteOff(int n) { if (SourceFx* s = src()) s->noteOff(n); }
  void allNotesOff() { if (SourceFx* s = src()) s->allNotesOff(); }
  void pitchBend(float b) { if (SourceFx* s = src()) s->pitchBend(b); }
  /** Track Settings bend range, forwarded to the source. No-op when the strip has no source. */
  void setPitchBendRange(int semis) { if (SourceFx* s = src()) s->setPitchBendRange(semis); }
  void process(float* io, int frames) { instrument.process(io, frames); strip.process(io, frames); }
  void collectGarbage() { instrument.collectGarbage(); strip.chain.collectGarbage(); }

 private:
  SourceFx* src() const { return kindOf(instrument.effectId(0)) == Kind::Source ? static_cast<SourceFx*>(instrument.peek(0)) : nullptr; }
};

}  // namespace aetherfx
