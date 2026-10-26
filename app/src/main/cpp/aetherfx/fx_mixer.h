// FxMixer: everything the audio engine needs, in one object. Control thread: set*/apply* calls. Audio thread: process*/note*.
// Strips: 0..7 soundfont tracks, 8 drum channel (soundfont ch 8), 9 tonic pad (soundfont ch 9), 10 drum-pad sampler, kMaster.
// Instruments (shapers and sources) are allowed on strips 0..7 only. Effects: 6 slots per strip, 8 on the master. No instrument on the master.
#pragma once
#include "fx_chain.h"

namespace aetherfx {

struct GenUpdate { GenSet gens; PerfSet perf; bool valid = false; };

class FxMixer {
 public:
  static constexpr int kStrips = 11, kMaster = 15;
  static bool instrumentAllowed(int strip) { return strip >= 0 && strip <= 7; }

  void prepare(double sr, int maxBlock) { for (auto& t : tracks_) t.prepare(sr, maxBlock); master_.prepare(sr, maxBlock); }

  // ---- control thread ----
  bool setInstrument(int strip, int id) {
    if (!instrumentAllowed(strip)) return false;
    if (id >= 0 && kindOf(id) == Kind::Effect) return false;
    const bool ok = tracks_[strip].setInstrument(id);
    if (ok) {
      // A source takes the channel over from FluidSynth, and the mixer mirrors its fader
      // onto the strip. Releasing the instrument must hand the strip back neutral, or the
      // leftover gain would keep attenuating a soundfont that is loaded afterwards.
      if (id < 0) { tracks_[strip].strip.setGain(1.f); tracks_[strip].strip.setMute(false); tracks_[strip].strip.setPan(0.f); }
      recount();
    }
    return ok;
  }
  bool setEffect(int strip, int slot, int id) {
    if (id >= 0 && kindOf(id) != Kind::Effect) return false;
    FxChain* c = chain(strip);
    if (!c) return false;
    const bool ok = c->setEffect(slot, id);
    if (ok) recount();
    return ok;
  }

  /**
   * True as soon as one strip carries an instrument or an effect. The engine uses
   * it to keep the (much heavier) multi-channel FX render dormant until the user
   * actually places something: until then the legacy stereo mix is audibly
   * equivalent and far cheaper, and the FX path also skips the surface FX chains.
   * Without this gate every note paid for 16 output groups and 11 strip chains,
   * which is what made the audio stutter continuously once the engine started.
   */
  bool hasActiveWork() const { return active_.load(std::memory_order_relaxed) > 0; }

  /**
   * True when the strip carries an instrument or at least one effect. A strip with
   * neither is a straight pass-through: the render folds its input directly into
   * the mix instead of copying it into a strip buffer, running an empty chain and
   * adding it back. That extra round trip per channel was what made the FX path
   * cost scale with the channel count rather than with the work actually placed,
   * so the audio got heavier as soon as one effect existed anywhere.
   */
  bool stripActive(int strip) const {
    if (strip >= 0 && strip < 8) {
      if (tracks_[strip].instrumentId() >= 0) return true;
    } else if (!(strip >= 8 && strip < kStrips)) {
      return true;  // master and out-of-range strips are always processed
    }
    const FxChain& c = tracks_[strip].strip.chain;
    for (int s = 0; s < c.slots(); ++s) if (c.effectId(s) >= 0) return true;
    return false;
  }
  void setParam(int strip, int slot, int param, float norm) { if (FxChain* c = chain(strip)) c->setParam(slot, param, norm); }
  void setBypass(int strip, int slot, bool b) { if (FxChain* c = chain(strip)) c->setBypass(slot, b); }
  bool applyPreset(int strip, int slot, int preset) { FxChain* c = chain(strip); return c ? c->applyPreset(slot, preset) : false; }
  void setInstrumentParam(int strip, int param, float norm) { if (instrumentAllowed(strip)) tracks_[strip].instrument.setParam(0, param, norm); }
  bool applyInstrumentPreset(int strip, int preset) { return instrumentAllowed(strip) ? tracks_[strip].instrument.applyPreset(0, preset) : false; }
  void setInstrumentBypass(int strip, bool b) { if (instrumentAllowed(strip)) tracks_[strip].instrument.setBypass(0, b); }
  void setGain(int strip, float lin) { if (FxStrip* s = fxStrip(strip)) s->setGain(lin); }
  void setPan(int strip, float pan) { if (FxStrip* s = fxStrip(strip)) s->setPan(pan); }
  void setMute(int strip, bool m) { if (FxStrip* s = fxStrip(strip)) s->setMute(m); }
  // Generator offsets for a SHAPER on this strip, computed from its current parameters. Apply with fluid_synth_set_gen(synth, channel, gen, value)
  // on the control thread after every parameter or preset change (diff against the last applied values), and perf options in the note handler.
  GenUpdate instrumentGens(int strip) const {
    GenUpdate u;
    if (!instrumentAllowed(strip)) return u;
    const int id = tracks_[strip].instrumentId();
    if (id < 0 || kindOf(id) != Kind::Shaper) return u;
    Effect* e = tracks_[strip].instrument.peek(0); if (!e) return u;
    float v[kMaxParams]; for (int i = 0; i < kMaxParams; ++i) v[i] = e->getNorm(i);
    computeGens(id, v, u.gens, u.perf); u.valid = true; return u;
  }
  int instrumentId(int strip) const { return instrumentAllowed(strip) ? tracks_[strip].instrumentId() : -1; }
  int effectId(int strip, int slot) const { const FxChain* c = chainC(strip); return c ? c->effectId(slot) : -1; }
  float meter(int strip, int slot, int i) const { const FxChain* c = chainC(strip); return c ? c->meter(slot, i) : 0.f; }
  float peakL(int strip) const { const FxStrip* s = fxStripC(strip); return s ? s->peakL() : 0.f; }
  float peakR(int strip) const { const FxStrip* s = fxStripC(strip); return s ? s->peakR() : 0.f; }
  void collectGarbage() { for (auto& t : tracks_) t.collectGarbage(); master_.chain.collectGarbage(); }

  /** Panic: clears every effect tail (delay/reverb loops, filter state) on all strips. */
  void requestPanic() {
    for (auto& t : tracks_) { t.instrument.requestPanic(); t.strip.chain.requestPanic(); }
    master_.chain.requestPanic();
  }
  // true when the track's soundfont channel can be skipped in the render loop (a source is selected)
  bool soundfontPaused(int strip) const { return instrumentAllowed(strip) && tracks_[strip].hasSource(); }

  // ---- audio thread ----
  void noteOn(int strip, int note, int vel) { if (instrumentAllowed(strip)) tracks_[strip].noteOn(note, vel); }
  void noteOff(int strip, int note) { if (instrumentAllowed(strip)) tracks_[strip].noteOff(note); }
  void allNotesOff(int strip) { if (instrumentAllowed(strip)) tracks_[strip].allNotesOff(); }
  void pitchBend(int strip, float b) { if (instrumentAllowed(strip)) tracks_[strip].pitchBend(b); }
  // Interleaved stereo. For a source track the input (soundfont) is ignored and replaced by the source.
  void processStrip(int strip, float* io, int frames) {
    if (strip >= 0 && strip <= 7) tracks_[strip].process(io, frames);
    else if (strip >= 8 && strip < kStrips) tracks_[strip].strip.process(io, frames);
  }
  void processMaster(float* io, int frames) { master_.process(io, frames); }

 private:
  FxChain* chain(int s) { return s == kMaster ? &master_.chain : (s >= 0 && s < kStrips ? &tracks_[s].strip.chain : nullptr); }
  const FxChain* chainC(int s) const { return s == kMaster ? &master_.chain : (s >= 0 && s < kStrips ? &tracks_[s].strip.chain : nullptr); }
  FxStrip* fxStrip(int s) { return s == kMaster ? &master_ : (s >= 0 && s < kStrips ? &tracks_[s].strip : nullptr); }
  const FxStrip* fxStripC(int s) const { return s == kMaster ? &master_ : (s >= 0 && s < kStrips ? &tracks_[s].strip : nullptr); }
  /** Recounts every occupied slot after a placement. Control thread only, cheap. */
  void recount() {
    int n = 0;
    for (auto& t : tracks_) {
      if (t.instrumentId() >= 0) n++;
      for (int s = 0; s < t.strip.chain.slots(); ++s) if (t.strip.chain.effectId(s) >= 0) n++;
    }
    for (int s = 0; s < master_.chain.slots(); ++s) if (master_.chain.effectId(s) >= 0) n++;
    active_.store(n, std::memory_order_relaxed);
  }
  TrackStrip tracks_[kStrips];
  FxStrip master_{8, true};
  std::atomic<int> active_{0};
};

}  // namespace aetherfx
