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
    return tracks_[strip].setInstrument(id);
  }
  bool setEffect(int strip, int slot, int id) {
    if (id >= 0 && kindOf(id) != Kind::Effect) return false;
    return chain(strip) ? chain(strip)->setEffect(slot, id) : false;
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
  TrackStrip tracks_[kStrips];
  FxStrip master_{8, true};
};

}  // namespace aetherfx
