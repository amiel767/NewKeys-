// Locates the "tac" on the Aether Leads engine.
//
// Ten previous attempts all worked on the attack envelope, which assumes the click is the attack.
// This bench does not assume: it renders a note and reports, millisecond by millisecond, the largest
// sample-to-sample jump, then compares each one against the natural slew of the waveform.
//
// Natural slew: a signal of frequency f and amplitude a moves at most 2*pi*f*a/sr per sample. Anything
// well above that is a discontinuity, i.e. an audible click. A ramp that is too fast shows up as a
// high slew in the first few ms; a state reset shows up as a single isolated spike.
//
// Build: g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/leads_click_check.cpp -o /tmp/lcc

#include <cmath>
#include <cstdio>
#include <string>
#include <vector>

#include "aetherfx/fx_leads.h"

using namespace aetherfx;

static constexpr double SR = 48000.0;
static constexpr int BLK = 256;

struct Preset {
  const char* name;
  float v[33];
};

// A preset is 33 values. These mirror the real "super saw" family of the app, which is where the
// user reports the click, plus a pure sine as a control: a sine has no band-limited edge, so it
// isolates the amplitude path from the oscillator path.
static Preset mk(const char* name, float l0, float l1, float l2, float l3, float l4, float atk, float rel) {
  Preset p{};
  p.name = name;
  for (int i = 0; i < 33; ++i) p.v[i] = 0.f;
  p.v[0] = l0; p.v[1] = l1; p.v[2] = l2; p.v[3] = l3; p.v[4] = l4;   // layer levels
  p.v[5] = 0.5f; p.v[6] = 0.5f; p.v[7] = 0.5f; p.v[8] = 0.5f; p.v[9] = 0.5f;  // octave = x1
  p.v[10] = 0.5f; p.v[11] = 0.15f;                                   // pulse width, detune
  p.v[12] = atk; p.v[13] = 0.0f; p.v[14] = 0.45f; p.v[15] = 0.8f; p.v[16] = rel;
  p.v[17] = 0.f;                                                     // LP
  p.v[18] = 0.85f; p.v[19] = 0.15f; p.v[20] = 0.5f;                  // cutoff, res, env
  p.v[21] = 0.f; p.v[22] = 0.f; p.v[23] = 0.f;                       // no delay
  p.v[24] = 0.f; p.v[25] = 0.f; p.v[26] = 0.f;                       // no reverb, no chorus
  p.v[27] = 0.5f;                                                    // tone flat
  p.v[28] = 0.f;                                                     // poly
  p.v[29] = 0.f; p.v[30] = 0.f;                                      // no glide, no vibrato
  p.v[31] = 0.8f;                                                    // level
  p.v[32] = 0.5f;                                                    // intensity neutral
  return p;
}

// Renders one note and returns the mono output (left channel).
static std::vector<float> render(const Preset& pr, int note, double seconds) {
  LeadsEngine eng;
  eng.prepare(SR, BLK);
  for (int i = 0; i < 33; ++i) eng.setNorm(i, pr.v[i]);
  eng.reset();

  const int total = static_cast<int>(seconds * SR);
  std::vector<float> out;
  out.reserve(static_cast<size_t>(total) + 2 * BLK);

  std::vector<float> buf(static_cast<size_t>(BLK) * 2);
  int done = 0;
  bool played = false;
  while (done < total) {
    if (!played) { eng.noteOn(note, 100); played = true; }
    else if (false) {
      // second strike halfway through: the same click should appear again, and this is what makes it
      // "follow every note" rather than being a one-off at engine start.
      static int fired = 0;
      if (fired < 1) { eng.noteOff(note); eng.noteOn(note, 100); fired++; }
    }
    std::fill(buf.begin(), buf.end(), 0.f);
    eng.process(buf.data(), BLK);
    for (int i = 0; i < BLK && done + i < total; ++i) out.push_back(buf[static_cast<size_t>(i) * 2]);
    done += BLK;
  }
  return out;
}

struct Hit { float jump = 0.f; int idx = 0; };

// Largest sample-to-sample jump in [from, to).
static Hit worstJump(const std::vector<float>& x, int from, int to) {
  Hit h;
  for (int i = from; i + 1 < to && i + 1 < static_cast<int>(x.size()); ++i) {
    const float d = std::fabs(x[i + 1] - x[i]);
    if (d > h.jump) { h.jump = d; h.idx = i; }
  }
  return h;
}

static float peakOf(const std::vector<float>& x, int from, int to) {
  float p = 0.f;
  for (int i = from; i < to && i < static_cast<int>(x.size()); ++i) p = std::max(p, std::fabs(x[i]));
  return p;
}

/**
 * The measure that matters: how much EXTRA discontinuity the note-on adds, as a fraction of the
 * note's own peak.
 *
 * Neither of the obvious measures works. A raw jump cannot be compared across waveforms (a saw's
 * own reset is 100 % of its peak, a sine's slew is 1 %). A head/sustain ratio removes the waveform
 * but not the level: the same absolute excess reads as 1.7 on a quiet sine and 1.1 on a loud saw.
 * Expressing the excess against the peak is level-independent and comparable between presets:
 * below ~10 % of peak it is inaudible, above ~25 % it is a click.
 */
static void report(const Preset& pr, int note) {
  const std::vector<float> x = render(pr, note, 0.8);
  const int headEnd = static_cast<int>(0.012 * SR);
  const int susA = static_cast<int>(0.30 * SR), susB = static_cast<int>(0.50 * SR);

  const Hit head = worstJump(x, 0, headEnd);
  const Hit sus = worstJump(x, susA, susB);
  const float peak = std::max(peakOf(x, 0, susB), 1e-6f);
  const float excess = (head.jump - sus.jump) / peak;
  const float tms = head.idx * 1000.f / static_cast<float>(SR);

  const char* verdict = excess > 0.25f ? "<== CLICK" : (excess > 0.10f ? "borderline" : "ok");
  std::printf("%-22s note %3d | peak %.4f | head %.5f @ %6.3f ms | sustain %.5f | excess %+6.1f%% of peak  %s\n",
              pr.name, note, peak, head.jump, tms, sus.jump, excess * 100.f, verdict);
}

int main() {
  std::printf("=== Aether Leads: note-on discontinuity ===\n");
  std::printf("(excess = extra jump at note-on over steady state, as a fraction of the note peak)\n\n");

  const Preset sine = mk("sine only", 1.f, 0.f, 0.f, 0.f, 0.f, 0.02f, 0.3f);
  const Preset pulse = mk("pulse only", 0.f, 1.f, 0.f, 0.f, 0.f, 0.02f, 0.3f);
  const Preset saw = mk("saw only", 0.f, 0.f, 1.f, 0.f, 0.f, 0.02f, 0.3f);
  const Preset supersaw = mk("super saw", 0.f, 0.f, 0.f, 1.f, 0.f, 0.02f, 0.3f);
  const Preset allLayers = mk("all layers", 0.6f, 0.5f, 0.5f, 1.f, 0.5f, 0.02f, 0.3f);
  const Preset fastAtk = mk("super saw, fast atk", 0.f, 0.f, 0.f, 1.f, 0.f, 0.0f, 0.3f);
  const Preset slowAtk = mk("super saw, slow atk", 0.f, 0.f, 0.f, 1.f, 0.f, 0.6f, 0.3f);

  std::printf("--- one layer at a time ---\n");
  for (const Preset& pr : {sine, pulse, saw, supersaw, allLayers}) {
    report(pr, 48);
    report(pr, 60);
    report(pr, 72);
  }

  std::printf("\n--- attack time should not matter ---\n");
  for (const Preset& pr : {fastAtk, supersaw, slowAtk}) {
    report(pr, 60);
  }

  std::printf("\n--- bus does not add anything ---\n");
  Preset full = supersaw; full.name = "super saw, full bus";
  full.v[26] = 0.5f; full.v[21] = 0.3f; full.v[22] = 0.3f; full.v[23] = 0.3f; full.v[24] = 0.5f; full.v[25] = 0.4f;
  report(full, 60);

  return 0;
}
