// Aether Arp: arpeggiator / pattern generator. Header-only, C++17, no allocation in process().
//
// The arp SITS BETWEEN the player and the engine. It never makes sound: it turns
// the notes you hold into a stream of notes. Everything downstream (soundfont,
// Aether instrument, FX chain) stays exactly as it is — that is why this file has
// no DSP and no dependence on the mixer.
//
// THE ONE RULE THAT MATTERS: the arp must NEVER release a note it did not start,
// and must ALWAYS release the ones it did. A missed NoteOff leaves a note ringing
// forever; a spurious one cuts a note the player is holding. Every note this class
// emits is recorded in `sounding_`, and only that list is released.
//
// TWO THREADS, and they must not be confused:
//   - noteIn() / setParam() / setTempo(): the CONTROL thread. They only push into
//     a lock-free ring and into atomics. They never touch the clock.
//   - process(): the AUDIO thread, once per block. It drains the ring, advances
//     the clock and emits notes. `held_`, `sounding_` and the phase live here only.
//
// SAMPLE-ACCURATE BY CONSTRUCTION. A block is ~10 ms; a 1/32 step at 120 BPM is
// 62 ms. Firing a step at the START of the block that contains it would jitter up
// to 17 % of a step — audible. So process() walks the block and splits it at each
// event, handing the callback the exact frame offset.
#pragma once
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>

namespace aetherfx {

enum class ArpMode { Up, Down, UpDown, DownUp, Random, Chord };
enum class ArpRate { Quarter, Eighth, Sixteenth, ThirtySecond, EighthT, SixteenthT };

// Beats per step. A quarter note is 1 beat, so a 1/16 is 0.25 beat.
inline double arpRateBeats(ArpRate r) {
  switch (r) {
    case ArpRate::Quarter: return 1.0;
    case ArpRate::Eighth: return 0.5;
    case ArpRate::Sixteenth: return 0.25;
    case ArpRate::ThirtySecond: return 0.125;
    case ArpRate::EighthT: return 1.0 / 3.0;     // three in the space of two
    case ArpRate::SixteenthT: return 1.0 / 6.0;
  }
  return 0.25;
}

// Normalised parameters, in the order the UI sends them (0..1), like the FX rack.
enum ArpParam {
  ARP_ON = 0, ARP_RATE, ARP_MODE, ARP_OCTAVES, ARP_GATE, ARP_SWING,
  ARP_HOLD, ARP_VELOCITY, ARP_PARAM_COUNT
};

class ArpEngine {
 public:
  static constexpr int kMaxHeld = 32;
  static constexpr int kMaxSound = 32;
  static constexpr int kMaxPattern = kMaxHeld * 4;

  // `frame` is the offset INSIDE the current block, so the host splits its render
  // there. `on` distinguishes NoteOn from NoteOff.
  using EmitFn = void (*)(void* ctx, int frame, int note, int velocity, bool on);

  void prepare(double sampleRate) {
    sr_ = sampleRate > 0 ? sampleRate : 48000.0;
    reset();
  }

  void reset() {
    nHeld_ = 0; nPhys_ = 0; nSound_ = 0;
    phase_ = 0.0; step_ = 0; pos_ = 0; dirUp_ = true; rng_ = 0x2545F491u;
    pendingStart_ = false;
    gateFrames_ = 0.0;
    stepFrames_ = computeStepFrames();
  }

  // ── CONTROL thread ───────────────────────────────────────────────────────
  // Lock-free push. A full ring DROPS the event rather than blocking the caller:
  // the UI thread must never wait on the audio thread.
  void noteIn(int note, int velocity, bool on) {
    if (note < 0 || note > 127) return;
    const uint32_t w = w_.load(std::memory_order_relaxed);
    const uint32_t r = r_.load(std::memory_order_acquire);
    if (w - r >= kRing) return;                     // full: drop
    ring_[w & (kRing - 1)] = {note, std::clamp(velocity, 1, 127), on};
    w_.store(w + 1, std::memory_order_release);
  }

  // Queued like everything else, so it lands in order AFTER the notes already
  // sent: a reset that ran on the control thread would race them.
  void allNotesOff() { noteIn(-1, 0, false); }

  void setParam(int i, float v) {
    if (i < 0 || i >= ARP_PARAM_COUNT) return;
    params_[i].store(v < 0.f ? 0.f : (v > 1.f ? 1.f : v), std::memory_order_relaxed);
  }

  // Host tempo. Read once per block, so a tempo change lands on a block edge.
  void setTempo(double bpm) { tempo_.store(bpm, std::memory_order_relaxed); }

  // ── AUDIO thread ─────────────────────────────────────────────────────────
  void process(int frames, EmitFn emit, void* ctx) {
    if (frames <= 0 || !emit) return;
    resolveParams();
    drain(emit, ctx);

    if (!on_ || nHeld_ == 0) {
      if (nSound_ > 0) emitSounding(emit, ctx, 0, false);
      phase_ = 0.0;
      return;
    }

    // The FIRST note of a chord sounds the moment it is pressed, not one step
    // later: that is what a player expects, and every classic arp does it. The
    // clock then runs from that instant.
    if (pendingStart_) {
      pendingStart_ = false;
      phase_ = 0.0;
      step_ = 0;
      stepFrames_ = computeStepFrames();
      startStep(emit, ctx, 0);
    }

    if (stepFrames_ < 1.0) stepFrames_ = 1.0;

    int done = 0, guard = 0;
    while (done < frames && guard++ < 1024) {
      const double toGate = (gateFrames_ > 0.0 && nSound_ > 0) ? (gateFrames_ - phase_) : 1e30;
      const double toStep = stepFrames_ - phase_;
      const double next = std::min(toGate, toStep);

      int chunk = static_cast<int>(std::ceil(next - 1e-6));
      if (chunk < 1) chunk = 1;

      if (done + chunk > frames) {
        // The event is past this block: just advance the clock and stop.
        phase_ += frames - done;
        return;
      }
      done += chunk;
      phase_ += chunk;

      // Gate off first: a step starting exactly on the gate point must not cancel
      // its own release.
      if (gateFrames_ > 0.0 && nSound_ > 0 && phase_ + 1e-6 >= gateFrames_) {
        emitSounding(emit, ctx, done, false);
      }
      if (phase_ + 1e-6 >= stepFrames_) {
        phase_ -= stepFrames_;
        if (phase_ < 0.0) phase_ = 0.0;
        step_++;
        stepFrames_ = computeStepFrames();
        startStep(emit, ctx, done);
      }
    }
  }

 private:
  struct Ev { int note; int velocity; bool on; };

  void resolveParams() {
    on_ = params_[ARP_ON].load(std::memory_order_relaxed) > 0.5f;
    rate_ = static_cast<ArpRate>(std::min(5, static_cast<int>(params_[ARP_RATE].load(std::memory_order_relaxed) * 6.0f)));
    mode_ = static_cast<ArpMode>(std::min(5, static_cast<int>(params_[ARP_MODE].load(std::memory_order_relaxed) * 6.0f)));
    octaves_ = std::clamp(1 + static_cast<int>(params_[ARP_OCTAVES].load(std::memory_order_relaxed) * 3.999f), 1, 4);
    // Gate: 0.05 .. 1.0. At 1.0 the notes touch (legato) and the release happens
    // exactly when the next step starts, which is what a player expects from "full".
    gate_ = 0.05f + params_[ARP_GATE].load(std::memory_order_relaxed) * 0.95f;
    swing_ = params_[ARP_SWING].load(std::memory_order_relaxed) * 0.66f;
    hold_ = params_[ARP_HOLD].load(std::memory_order_relaxed) > 0.5f;
    velocity_ = 0.15f + params_[ARP_VELOCITY].load(std::memory_order_relaxed) * 0.85f;
    bpm_ = std::clamp(tempo_.load(std::memory_order_relaxed), 20.0, 400.0);
  }

  // Even steps get longer and odd steps shorter, so the PAIR keeps its total:
  // swing moves the notes without dragging the tempo.
  double computeStepFrames() const {
    const double base = sr_ * 60.0 / bpm_ * arpRateBeats(rate_);
    const double f = base * ((step_ % 2 == 0) ? (1.0 + swing_) : (1.0 - swing_));
    return f < 1.0 ? 1.0 : f;
  }

  void drain(EmitFn emit, void* ctx) {
    uint32_t r = r_.load(std::memory_order_relaxed);
    const uint32_t w = w_.load(std::memory_order_acquire);
    while (r != w) {
      const Ev e = ring_[r & (kRing - 1)];
      r++;
      if (e.note < 0) {                       // allNotesOff
        nHeld_ = 0; nPhys_ = 0; pendingStart_ = false;
        if (nSound_ > 0) emitSounding(emit, ctx, 0, false);
        phase_ = 0.0;
        continue;
      }
      if (e.on) {
        const bool wasSilent = (nHeld_ == 0);
        // A fresh press while nothing is physically down replaces the latched
        // chord. Without this, a latched arp would keep piling notes up.
        if (hold_ && nPhys_ == 0) { nHeld_ = 0; if (nSound_ > 0) emitSounding(emit, ctx, 0, false); }
        holdAdd(e.note);
        physAdd(e.note);
        // First note of a new chord: the pattern starts NOW, not at the next tick.
        if (wasSilent && nHeld_ > 0) pendingStart_ = true;
        if (nHeld_ > 0) {
          pos_ = 0; dirUp_ = true;
          if (mode_ == ArpMode::Down || mode_ == ArpMode::DownUp) { pos_ = nHeld_ - 1; dirUp_ = (mode_ == ArpMode::Down); }
        }
      } else {
        physDel(e.note);
        if (!hold_) holdDel(e.note);
      }
    }
    r_.store(r, std::memory_order_release);
  }

  void startStep(EmitFn emit, void* ctx, int frame) {
    int pat[kMaxPattern]; int pn = 0;
    for (int o = 0; o < octaves_; ++o)
      for (int i = 0; i < nHeld_; ++i) {
        const int n = held_[i] + 12 * o;
        if (n <= 127 && pn < kMaxPattern) pat[pn++] = n;
      }
    if (pn == 0) { if (nSound_ > 0) emitSounding(emit, ctx, frame, false); return; }

    const int vel = std::clamp(static_cast<int>(std::lround(velocity_ * 127.0f)), 1, 127);

    // What does this step want to sound?
    int want[kMaxSound]; int wn = 0;
    if (mode_ == ArpMode::Chord) {
      // Every held note at once, octave stacking included. Capped: a 16-key chord
      // over 4 octaves would be 64 notes, which the engine does not need.
      for (int i = 0; i < pn && wn < kMaxSound; ++i) want[wn++] = pat[i];
    } else {
      int idx;
      if (mode_ == ArpMode::Random) {
        rng_ ^= rng_ << 13; rng_ ^= rng_ >> 17; rng_ ^= rng_ << 5;
        idx = static_cast<int>(rng_ % static_cast<uint32_t>(pn));
      } else {
        idx = walk(pn);
      }
      want[wn++] = pat[idx];
    }

    // A full gate is LEGATO. Re-attacking a note that is already sounding would
    // click and cut its tail, so in legato we only release the notes this step
    // does NOT want. That is the whole difference between gate 0.99 and 1.0.
    const bool legato = (gate_ >= 0.999f);
    if (legato) {
      for (int i = 0; i < nSound_; ++i) {
        bool keep = false;
        for (int j = 0; j < wn; ++j) if (want[j] == sounding_[i]) { keep = true; break; }
        if (!keep) emit(ctx, frame, sounding_[i], 0, false);
      }
    } else if (nSound_ > 0) {
      emitSounding(emit, ctx, frame, false);
    }

    // Start what is not already sounding, and carry the rest over.
    int keep[kMaxSound]; int kn = 0;
    for (int i = 0; i < nSound_; ++i)
      for (int j = 0; j < wn; ++j)
        if (want[j] == sounding_[i]) { if (kn < kMaxSound) keep[kn++] = sounding_[i]; break; }
    for (int j = 0; j < wn; ++j) {
      bool already = false;
      for (int i = 0; i < kn; ++i) if (keep[i] == want[j]) { already = true; break; }
      if (!already) { emit(ctx, frame, want[j], vel, true); if (kn < kMaxSound) keep[kn++] = want[j]; }
    }
    for (int i = 0; i < kn; ++i) sounding_[i] = keep[i];
    nSound_ = kn;

    gateFrames_ = legato ? 0.0 : stepFrames_ * gate_;
  }

  // Returns the current index then advances. The bounce does NOT repeat the
  // extremes, which is what every classic arpeggiator does.
  int walk(int pn) {
    if (pn <= 1) { pos_ = 0; return 0; }
    if (pos_ < 0 || pos_ >= pn) pos_ = 0;
    const int r = pos_;
    switch (mode_) {
      case ArpMode::Up: pos_ = (pos_ + 1) % pn; break;
      case ArpMode::Down: pos_ = (pos_ - 1 + pn) % pn; break;
      default:
        if (dirUp_) {
          if (pos_ + 1 >= pn) { dirUp_ = false; pos_ = pn - 2; } else pos_++;
        } else {
          if (pos_ - 1 < 0) { dirUp_ = true; pos_ = 1; } else pos_--;
        }
        break;
    }
    return r;
  }

  void emitSounding(EmitFn emit, void* ctx, int frame, bool on) {
    for (int i = 0; i < nSound_; ++i) emit(ctx, frame, sounding_[i], 0, on);
    nSound_ = 0;
  }

  // The held list is kept in PITCH ORDER: a human plays a chord in any order, but
  // the pattern must walk it from low to high. Insertion sort after the insert —
  // n <= 32, so it costs nothing.
  void holdAdd(int v) {
    for (int i = 0; i < nHeld_; ++i) if (held_[i] == v) return;
    if (nHeld_ >= kMaxHeld) return;
    held_[nHeld_++] = v;
    for (int i = nHeld_ - 1; i > 0 && held_[i - 1] > held_[i]; --i) std::swap(held_[i - 1], held_[i]);
  }
  void holdDel(int v) {
    for (int i = 0; i < nHeld_; ++i) if (held_[i] == v) { held_[i] = held_[--nHeld_]; return; }
  }
  // The physical list is order-agnostic: it only answers "is any key still down",
  // which is what tells latch whether a fresh press starts a new chord.
  void physAdd(int v) {
    for (int i = 0; i < nPhys_; ++i) if (phys_[i] == v) return;
    if (nPhys_ < kMaxHeld) phys_[nPhys_++] = v;
  }
  void physDel(int v) {
    for (int i = 0; i < nPhys_; ++i) if (phys_[i] == v) { phys_[i] = phys_[--nPhys_]; return; }
  }

  double sr_ = 48000.0;
  int nHeld_ = 0, nPhys_ = 0, nSound_ = 0;
  int held_[kMaxHeld]{}; int phys_[kMaxHeld]{}; int sounding_[kMaxSound]{};
  double phase_ = 0.0, stepFrames_ = 0.0, gateFrames_ = 0.0, bpm_ = 120.0;
  int pos_ = 0, step_ = 0; bool dirUp_ = true;
  bool pendingStart_ = false;
  uint32_t rng_ = 0x2545F491u;
  bool on_ = false, hold_ = false;
  ArpRate rate_ = ArpRate::Sixteenth;
  ArpMode mode_ = ArpMode::Up;
  int octaves_ = 1;
  float gate_ = 0.5f, swing_ = 0.f, velocity_ = 1.f;

  static constexpr uint32_t kRing = 256;
  Ev ring_[kRing]{};
  std::atomic<uint32_t> w_{0}, r_{0};
  std::atomic<float> params_[ARP_PARAM_COUNT]{};
  std::atomic<double> tempo_{120.0};
};

}  // namespace aetherfx
