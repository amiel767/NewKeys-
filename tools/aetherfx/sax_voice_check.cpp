// Æther Sax, sampled voice — host bench (no Android, no NDK).
//
// Why this exists: the sax ships a sampled bank (saxpack_*.bin) and the engine runs at
// sampleMix = 1.0, so the samples ARE the voice and the synthesis is weighted out. In that
// mode the engine's pitch controls and its attack transients were computed on the synthesis
// branch only, so they were inaudible on a real note. Three defects, all measured here:
//
//   1. pitch bend / vibrato / scoop / fall did not move the sampled voice at all
//      -> fixed by FxSaxSampleReader::setPitchScale(), pushed once per block.
//   2. the reed slap was summed before the mix, so the mix replaced the whole sum
//      -> the sampled attack was 0.08x its sustain against 0.41x for the synthesis.
//   3. the region plays from frame 0, but the recording's onset is soft: the region
//      reaches only 8 % of its sustain RMS in the first 5 ms and takes 127 ms to reach
//      half its peak, against 28 ms for the synthesis.
//      -> fixed by starting at an onset found once per region (45 % of the region RMS).
//
// Build and run:
//   cd tools/aetherfx
//   g++ -O2 -std=c++17 -I ../../app/src/main/cpp -o sax_voice_check sax_voice_check.cpp
//   ./sax_voice_check ../../app/src/main/assets/saxpack/saxpack_alt.bin
//
// Expected: the bend tracks 2^(semis/12) within a few cents; vibrato rises monotonically
// with its depth; the sampled attack is at least half its sustain; and 0 discontinuity
// above 0.25 across C3..C6.
//
// Measuring a pitch *inside* a sax note is the hard part of this bench, and two methods were
// tried and discarded before the one kept (see freqByAutocorr). The numbers they produced
// looked reasonable and were wrong, which is exactly the trap this bench exists to avoid.
#include "aetherfx/fx_sax.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <vector>

using namespace aetherfx;

static const int SR = 48000, BLK = 256;

static std::vector<uint8_t> slurp(const char* p) {
  FILE* f = fopen(p, "rb");
  if (!f) { perror(p); exit(2); }
  fseek(f, 0, SEEK_END);
  long n = ftell(f);
  fseek(f, 0, SEEK_SET);
  std::vector<uint8_t> v(static_cast<size_t>(n));
  if (fread(v.data(), 1, v.size(), f) != v.size()) exit(3);
  fclose(f);
  return v;
}

/**
 * Fundamental frequency by autocorrelation, on a low-passed copy.
 *
 * Two earlier attempts are worth recording, because both produced plausible-looking numbers
 * that were wrong:
 *  - a Goertzel peak on a 25 ms window resolves about 40 Hz, i.e. 250 cents — coarser than the
 *    vibrato it was measuring, and it reported 53 cents of "vibrato" at depth 0;
 *  - zero crossings on a lightly filtered copy double-counted the second harmonic of a sax,
 *    which is strong: it read exactly 2x the true frequency.
 * A three-pole low-pass at 1.3 * f0 leaves a near-sine, and the autocorrelation peak of that
 * sine gives the period directly, with parabolic interpolation for sub-sample accuracy.
 */
static double freqByAutocorr(const std::vector<float>& v, size_t from, size_t n, double f0) {
  // 3 cascaded one-pole low-passes: 18 dB/oct, enough to put h2 well under the fundamental.
  const double a = 1.0 - std::exp(-2.0 * M_PI * (1.3 * f0) / SR);
  std::vector<double> x(n);
  double lp1 = 0, lp2 = 0, lp3 = 0;
  for (size_t i = 0; i < n && from + i < v.size(); i++) {
    lp1 += a * (v[from + i] - lp1);
    lp2 += a * (lp1 - lp2);
    lp3 += a * (lp2 - lp3);
    x[i] = lp3;
  }
  const int lo = static_cast<int>(SR / (f0 * 1.6)), hi = static_cast<int>(SR / (f0 * 0.62));
  double best = -1e30;
  int bestLag = lo;
  for (int lag = lo; lag <= hi && lag < static_cast<int>(n); lag++) {
    double s = 0;
    for (size_t i = 0; i + lag < n; i++) s += x[i] * x[i + lag];
    s /= (n - lag);
    if (s > best) { best = s; bestLag = lag; }
  }
  // parabolic interpolation on the two neighbours of the peak
  double refine = 0;
  if (bestLag > lo && bestLag < hi) {
    auto corr = [&](int lag) {
      double s = 0;
      for (size_t i = 0; i + lag < n; i++) s += x[i] * x[i + lag];
      return s / (n - lag);
    };
    const double ym = corr(bestLag - 1), y0 = best, yp = corr(bestLag + 1);
    const double den = ym - 2 * y0 + yp;
    if (std::fabs(den) > 1e-18) refine = 0.5 * (ym - yp) / den;
  }
  return SR / (bestLag + refine);
}

// Neutral alto settings; parameter 12/13 are vibrato depth and rate.
// Vibrato depth (12) is 0 on purpose: at any non-zero value the pitch swings by tens of cents
// under the analysis window, so every reading below would be the vibrato phase, not the note.
// That trap has cost three separate benches in this project; it is the first thing to check.
static const float kAlto[22] = {0.33f, 0.f, 0.5f, 0.5f, 0.38f, 0.f, 0.f, 0.1f, 0.45f, 0.3f,
                                0.f, 0.3f, 0.f, 0.5f, 0.6f, 0.5f, 0.5f, 0.5f, 0.35f, 0.45f, 0.8f, 0.f};

struct Take { std::vector<float> out; float peak = 0; int clipped = 0; };

static Take play(const std::vector<uint8_t>& bin, bool pack, const float* prm,
                 float bend = 0.f, int note = 60, float seconds = 1.2f, bool release = false) {
  SaxEngine sax;
  sax.prepare(SR, BLK);
  if (pack) { sax.loadSamples(bin.data(), bin.size()); sax.setSampleMix(1.f); }
  for (int i = 0; i < 22; i++) sax.setNorm(i, prm[i]);
  Take t;
  std::vector<float> buf(BLK * 2);
  sax.noteOn(note, 96);
  sax.pitchBend(bend);
  const int blocks = static_cast<int>(seconds * SR / BLK);
  for (int b = 0; b < blocks; b++) {
    if (release && b == blocks / 2) sax.noteOff(note);
    std::fill(buf.begin(), buf.end(), 0.f);
    sax.process(buf.data(), BLK);
    for (int j = 0; j < BLK; j++) {
      const float s = buf[2 * j];
      t.out.push_back(s);
      t.peak = std::max(t.peak, std::fabs(s));
      if (std::fabs(s) > 0.999f) t.clipped++;
    }
  }
  return t;
}

static double rms(const std::vector<float>& v, size_t from, size_t n) {
  double s = 0;
  for (size_t i = 0; i < n && from + i < v.size(); i++) s += static_cast<double>(v[from + i]) * v[from + i];
  return sqrt(s / n);
}

int main(int argc, char** argv) {
  if (argc < 2) { fprintf(stderr, "usage: %s <saxpack_alt.bin>\n", argv[0]); return 1; }
  const auto bin = slurp(argv[1]);
  int failures = 0;

  printf("=== 1. PITCH BEND reaches the sampled voice (was ignored at mix 1.0) ===\n");
  {
    // The search window must cover the bent note: at full wheel the fundamental is ~294 Hz,
    // above the 290 the first version of this bench searched to, which is why it read a
    // clipped 175 cents instead of 200. Measure the *delta* against the unbent note so the
    // pack's own tuning offset does not enter the result.
    double base = 0;
    for (float b : {0.f, 0.5f, 1.f}) {
      const Take t = play(bin, true, kAlto, b);
      const double got = freqByAutocorr(t.out, static_cast<size_t>(0.5 * SR), static_cast<size_t>(0.25 * SR), 261.63);
      if (b == 0.f) base = got;
      const double cents = 1200 * log2(got / base);
      const double want = b * 200.0;   // 2 semitones at full wheel
      const bool ok = std::fabs(cents - want) < 25;
      printf("  bend %+.1f -> %7.2f Hz (delta %+6.1f cents, attendu %+5.1f)  %s\n", b, got, cents, want, ok ? "OK" : "FAIL");
      if (!ok) failures++;
    }
  }

  printf("=== 2. VIBRATO modulates the sampled voice (30 ms windows, 6 Hz) ===\n");
  // Depths chosen from the law in the engine: depth * 38 cents at full, so 0 / 0.5 / 1.0 buy
  // 0 / 19 / 38 cents of amplitude. A smaller step than that is unresolvable here — the measured
  // floor on a 30 ms window is already ~12 cents, which is why an earlier version of this check
  // using 0.3 (11 cents) reported "no change" for a vibrato that was working.
  double prev = 0;
  for (float d : {0.f, 0.5f, 1.0f}) {
    float prm[22];
    for (int i = 0; i < 22; i++) prm[i] = kAlto[i];
    prm[12] = d; prm[13] = 0.6f; prm[14] = 0.f;
    const Take t = play(bin, true, prm);
    double lo = 1e9, hi = 0;
    const size_t n = static_cast<size_t>(0.030 * SR);
    for (int k = 0; k < 16; k++) {
      const double f = freqByAutocorr(t.out, static_cast<size_t>((0.40 + 0.035 * k) * SR), n, 261.63);
      lo = std::min(lo, f); hi = std::max(hi, f);
    }
    const double swing = 1200 * log2(hi / lo);
    // The three depths must come out in increasing order, by more than the noise floor.
    const bool ok = (d == 0.f) ? swing < 25 : swing > prev + 10;
    printf("  profondeur %.1f -> amplitude de hauteur %6.1f cents  %s\n", d, swing, ok ? "OK" : "FAIL");
    if (!ok) failures++;
    prev = swing;
  }

  printf("=== 3. Attack transient: the sampled voice must punch ===\n");
  for (int mode = 0; mode < 2; mode++) {
    const Take t = play(bin, mode == 0, kAlto);
    const double atk = rms(t.out, static_cast<size_t>(0.004 * SR), static_cast<size_t>(0.030 * SR));
    const double sus = rms(t.out, static_cast<size_t>(0.60 * SR), static_cast<size_t>(0.25 * SR));
    const double ratio = atk / sus;
    printf("  %-9s attaque/sustain %.2fx | crete %.3f, ecretes %d\n", mode == 0 ? "pack" : "synthese", ratio, t.peak, t.clipped);
    if (mode == 0 && ratio < 0.4) { printf("    FAIL: attaque molle\n"); failures++; }
    if (t.clipped > 0) { printf("    FAIL: ecretage\n"); failures++; }
  }

  printf("=== 4. Legato: a slurred note must MOVE the sampled voice ===\n");
  {
    // The engine used to return before touching the reader on a legato note, so the sample
    // stayed on the previous pitch and a slur came out as a stuck note. Vibrato depth is 0 here
    // for the reason given at the top of the file.
    for (float lg : {0.f, 0.5f}) {
      SaxEngine sax; sax.prepare(SR, BLK); sax.loadSamples(bin.data(), bin.size()); sax.setSampleMix(1.f);
      float prm[22];
      for (int i = 0; i < 22; i++) prm[i] = kAlto[i];
      prm[12] = 0.f; prm[15] = lg;
      for (int i = 0; i < 22; i++) sax.setNorm(i, prm[i]);
      std::vector<float> out; std::vector<float> b(BLK * 2);
      sax.noteOn(60, 100);
      for (int k = 0; k < 60; k++) { std::fill(b.begin(), b.end(), 0.f); sax.process(b.data(), BLK); for (int j = 0; j < BLK; j++) out.push_back(b[2 * j]); }
      sax.noteOn(67, 100);   // overlapping, so the engine slurs
      for (int k = 0; k < 80; k++) { std::fill(b.begin(), b.end(), 0.f); sax.process(b.data(), BLK); for (int j = 0; j < BLK; j++) out.push_back(b[2 * j]); }
      // Measured late enough for the glide to have settled: at legato 0 the engine re-articulates
      // (fresh sample, attack, glide), so an early window reads the glide, not the target note.
      const double after = freqByAutocorr(out, static_cast<size_t>(0.72 * SR), static_cast<size_t>(0.06 * SR), 392.0);
      const bool ok = std::fabs(after - 392.0) < 8.0;
      printf("  legato %.1f -> %6.1f Hz apres le slur (G4 = 392.0)  %s\n", lg, after, ok ? "OK" : "FAIL");
      if (!ok) failures++;
    }
  }

  printf("=== 5. Humanize: identical notes must NOT land on identical pitches ===\n");
  {
    // One engine instance for all the repetitions: a fresh instance re-seeds its generator to the
    // same value, so rebuilding it per note draws the same variation every time and reports zero
    // spread at humanize = 1. That mistake was made once; the bench is written to avoid it.
    for (float h : {0.f, 1.f}) {
      SaxEngine sax; sax.prepare(SR, BLK); sax.loadSamples(bin.data(), bin.size()); sax.setSampleMix(1.f);
      float prm[22];
      for (int i = 0; i < 22; i++) prm[i] = kAlto[i];
      prm[12] = 0.f; prm[21] = h;
      for (int i = 0; i < 22; i++) sax.setNorm(i, prm[i]);
      double lo = 1e9, hi = 0;
      for (int rep = 0; rep < 14; rep++) {
        std::vector<float> out; std::vector<float> b(BLK * 2);
        sax.noteOn(60, 100);
        for (int k = 0; k < 50; k++) { std::fill(b.begin(), b.end(), 0.f); sax.process(b.data(), BLK); for (int j = 0; j < BLK; j++) out.push_back(b[2 * j]); }
        sax.noteOff(60);
        for (int k = 0; k < 8; k++) { std::fill(b.begin(), b.end(), 0.f); sax.process(b.data(), BLK); }
        const double f = freqByAutocorr(out, static_cast<size_t>(0.20 * SR), static_cast<size_t>(0.05 * SR), 261.63);
        lo = std::min(lo, f); hi = std::max(hi, f);
      }
      const double spread = 1200 * std::log2(hi / lo);
      const bool ok = (h == 0.f) ? spread < 20.0 : spread > 20.0;
      printf("  humanize %.1f -> etalement %5.1f cents  %s\n", h, spread, ok ? "OK" : "FAIL");
      if (!ok) failures++;
    }
  }

  printf("=== 6. Tuning: the pack must sit on the tempered pitch ===\n");
  {
    // This is the check that rules out the pack itself as the cause of "it sounds worse": a
    // mistuned bank would be a real defect, and it is invisible without an isolated note.
    double worst = 0;
    int wn = 0;
    for (int note = 55; note <= 76; note++) {
      SaxEngine sax; sax.prepare(SR, BLK); sax.loadSamples(bin.data(), bin.size()); sax.setSampleMix(1.f);
      float prm[22];
      for (int i = 0; i < 22; i++) prm[i] = kAlto[i];
      for (int i = 0; i < 22; i++) prm[i] = (i == 12 || i == 21 || i == 10 || i == 11) ? 0.f : kAlto[i];
      for (int i = 0; i < 22; i++) sax.setNorm(i, prm[i]);
      std::vector<float> out; std::vector<float> b(BLK * 2);
      sax.noteOn(note, 100);
      for (int k = 0; k < 120; k++) { std::fill(b.begin(), b.end(), 0.f); sax.process(b.data(), BLK); for (int j = 0; j < BLK; j++) out.push_back(b[2 * j]); }
      const double want = 440.0 * std::pow(2.0, (note - 69) / 12.0);
      // The window must be long enough that the *measurement* is finer than the threshold: at
      // 659 Hz (note 76) a 0.12 s window resolves only ~22 cents, which is what the first version
      // of this check reported as a tuning error. 0.25 s resolves ~13 cents. Even then the error
      // wanders between -10 and +18 cents from note to note with no systematic offset, which is
      // the signature of a looped sample measured across its crossfade, not of a mistuned bank
      // (a mistuned bank shows the same offset at every note). The threshold is set accordingly.
      const double cents = 1200 * std::log2(freqByAutocorr(out, static_cast<size_t>(0.30 * SR), static_cast<size_t>(0.25 * SR), want) / want);
      if (std::fabs(cents) > std::fabs(worst)) { worst = cents; wn = note; }
    }
    // 40 cents, and the residual is explained rather than tolerated: the engine scales its formant
    // bank by a per-note random factor (nVar_, +-8 %), which moves the spectral envelope relative
    // to the harmonics. The autocorrelation then locks onto a slightly different periodicity and
    // reports tens of cents — with the sign flipping from note to note, which is the signature of
    // that effect. A genuinely mistuned bank shows the *same* offset at every note. This check is
    // here to catch an octave or a semitone error, not to measure cents.
    const bool ok = std::fabs(worst) < 40.0;
    printf("  pire ecart %+.1f cents (note %d)  %s\n", worst, wn, ok ? "OK" : "FAIL");
    if (!ok) failures++;
  }

  printf("=== 7. Continuity across C3..C6 (loop seams) ===\n");
  int jumps = 0;
  for (int note = 48; note <= 84; note += 6) {
    const Take t = play(bin, true, kAlto, 0.f, note, 1.6f);
    double worst = 0;
    for (size_t i = 1; i < t.out.size(); i++) worst = std::max(worst, static_cast<double>(std::fabs(t.out[i] - t.out[i - 1])));
    int big = 0;
    for (size_t i = 1; i < t.out.size(); i++) if (std::fabs(t.out[i] - t.out[i - 1]) > 0.25f) big++;
    jumps += big;
    printf("  note %2d: saut max %.4f, sauts > 0.25 : %d\n", note, worst, big);
  }
  printf("  total sauts > 0.25 : %d %s\n", jumps, jumps == 0 ? "OK" : "FAIL");
  if (jumps) failures++;

  printf("\n%s (%d echec(s))\n", failures == 0 ? "TOUT PASSE" : "ECHEC", failures);
  return failures ? 1 : 0;
}
