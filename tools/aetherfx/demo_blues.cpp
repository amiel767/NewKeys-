// Blues fill demo for the Æther Piano, off Android (aetherfx is header-only).
//   g++ -O2 -std=c++17 -I app/src/main/cpp demo_blues.cpp -o /tmp/db
//
//   /tmp/db <piano.bin> <presets.txt> <outdir>            # render the takes
//   /tmp/db <piano.bin> <presets.txt> verify              # check every pitch
//
// The fill is the classic CHROMATIC TURNAROUND: twelve notes stepping down from
// C5 to C4, landing on the tonic.  It is the most recognisable fill in the blues
// vocabulary, and it is also the honest way to test the instrument's pitch: it
// spans exactly one octave, so an octave error of any kind is impossible to miss.
//
// The song is eight bars of C blues at 96 BPM: two bars of boogie shuffle, the
// two-bar fill, the shuffle again, the fill again.  The left hand is written out
// rather than left to a chord symbol, because the point is the exact notes.
#include <cstdio>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <algorithm>
#include <complex>
#include <fstream>
#include <string>
#include <vector>

#include "aetherfx/fx_piano.h"

using namespace aetherfx;

static std::vector<uint8_t> g_bank;   // shared by the verify path
static PianoSampleReader g_probe;     // reports the chosen region

static constexpr int kSr = 44100;
static constexpr float kBpm = 96.f;
static constexpr float kBeat = 60.f / kBpm;      // seconds per beat

// ---------------------------------------------------------------------------
// The music.  Times are in beats from the start of the eight-bar form.
// ---------------------------------------------------------------------------
struct Ev { int midi; float at; float dur; int vel; };

static std::string noteName(int midi) {
  static const char* n[12] = {"C", "C#", "D", "D#", "E", "F",
                              "F#", "G", "G#", "A", "A#", "B"};
  // A std::string, not a shared static buffer: two calls inside one printf would
  // otherwise alias and print the same note twice.
  char buf[16];
  std::snprintf(buf, sizeof buf, "%s%d", n[midi % 12], midi / 12 - 1);
  return buf;
}

static double midiHz(int midi) { return 440.0 * std::pow(2.0, (midi - 69) / 12.0); }

// Left hand: C7 boogie shuffle.  Root, fifth, octave, fifth -- the engine of a
// blues piano, and it puts real energy in the low register.
static void addGroove(std::vector<Ev>& v, float t0) {
  const int low = 36, fifth = 43, oct = 48;      // C2, G2, C3
  for (int bar = 0; bar < 2; ++bar) {
    const float b = t0 + bar * 4.f;
    v.push_back({low, b + 0.0f, 0.5f, 78});
    v.push_back({fifth, b + 0.5f, 0.5f, 62});
    v.push_back({oct, b + 1.0f, 0.5f, 70});
    v.push_back({fifth, b + 1.5f, 0.5f, 60});
    v.push_back({low, b + 2.0f, 0.5f, 76});
    v.push_back({fifth, b + 2.5f, 0.5f, 62});
    v.push_back({oct, b + 3.0f, 0.5f, 70});
    v.push_back({fifth, b + 3.5f, 0.5f, 58});
  }
  // Right-hand stabs on the two downbeats, so the groove reads as a full band.
  // C4/E4: they sit UNDER the fill, which runs from C5 down to C4.  An octave
  // higher they would collide with the fill's first notes.
  const int s1 = 60, s2 = 64;                    // C4, E4
  v.push_back({s1, t0 + 0.0f, 0.4f, 88});
  v.push_back({s2, t0 + 0.0f, 0.4f, 82});
  v.push_back({s1, t0 + 2.0f, 0.4f, 84});
  v.push_back({s2, t0 + 2.0f, 0.4f, 78});
  v.push_back({s1, t0 + 4.0f, 0.4f, 86});
  v.push_back({s2, t0 + 4.0f, 0.4f, 80});
  v.push_back({s1, t0 + 6.0f, 0.4f, 82});
  v.push_back({s2, t0 + 6.0f, 0.4f, 76});
}

// THE FILL: twelve chromatic notes down from C5 to C4, eighth notes, landing on
// the tonic and holding it.  Bar 1 is the run, bar 2 closes on C4.
static const Ev kFill[] = {
    {72, 0.5f, 0.5f, 96},   // C5
    {71, 1.0f, 0.5f, 90},   // B4
    {70, 1.5f, 0.5f, 94},   // Bb4
    {69, 2.0f, 0.5f, 98},   // A4
    {68, 2.5f, 0.5f, 90},   // Ab4
    {67, 3.0f, 0.5f, 100},  // G4
    {66, 3.5f, 0.5f, 90},   // F#4
    {65, 4.0f, 0.5f, 102},  // F4
    {64, 4.5f, 0.5f, 94},   // E4
    {63, 5.0f, 0.5f, 92},   // Eb4
    {62, 5.5f, 0.5f, 96},   // D4
    {60, 6.0f, 2.0f, 104},  // C4, held to the end of the form
};
static const int kFillLen = static_cast<int>(sizeof(kFill) / sizeof(kFill[0]));

// Half-note bass under the fill, so the run is heard against a steady floor.
static const Ev kFillBass[] = {
    {36, 0.0f, 2.0f, 76},   // C2
    {43, 2.0f, 2.0f, 68},   // G2
    {36, 4.0f, 2.0f, 76},   // C2
    {43, 6.0f, 2.0f, 66},   // G2
};

static void addFill(std::vector<Ev>& v, float t0) {
  for (int i = 0; i < kFillLen; ++i)
    v.push_back({kFill[i].midi, t0 + kFill[i].at, kFill[i].dur, kFill[i].vel});
  for (const Ev& e : kFillBass)
    v.push_back({e.midi, t0 + e.at, e.dur, e.vel});
}

static std::vector<Ev> buildSong() {
  std::vector<Ev> v;
  addGroove(v, 0.f);     // bars 1-2
  addFill(v, 8.f);       // bars 3-4  <- the fill
  addGroove(v, 16.f);    // bars 5-6
  addFill(v, 24.f);      // bars 7-8  <- the fill again
  std::sort(v.begin(), v.end(), [](const Ev& a, const Ev& b) { return a.at < b.at; });
  return v;
}

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------
static std::vector<uint8_t> readFile(const char* path) {
  std::ifstream f(path, std::ios::binary);
  if (!f) { std::fprintf(stderr, "cannot open %s\n", path); std::exit(1); }
  return std::vector<uint8_t>((std::istreambuf_iterator<char>(f)),
                              std::istreambuf_iterator<char>());
}

struct Preset { std::string name; std::vector<float> v; };

static std::vector<Preset> loadPresets(const char* path) {
  std::ifstream f(path);
  if (!f) { std::fprintf(stderr, "cannot open %s\n", path); std::exit(1); }
  std::vector<Preset> out;
  std::string line;
  while (std::getline(f, line)) {
    if (line.empty() || line[0] == '#') continue;
    const size_t tab = line.find('|');
    if (tab == std::string::npos) continue;
    Preset p;
    p.name = line.substr(0, tab);
    std::string rest = line.substr(tab + 1);
    size_t pos = 0;
    while (pos < rest.size()) {
      size_t nxt = rest.find(',', pos);
      if (nxt == std::string::npos) nxt = rest.size();
      p.v.push_back(std::stof(rest.substr(pos, nxt - pos)));
      pos = nxt + 1;
    }
    out.push_back(p);
  }
  return out;
}

// Plays the events with a real scheduler, so the held bass and the fill overlap
// the way two hands do.  The old sequential renderer could not overlap notes at
// all, which is fine for a bare melody and wrong for anything else.
static void render(PianoEngine& eng, const std::vector<float>& params,
                   const std::vector<Ev>& song, std::vector<float>& out) {
  for (int i = 0; i < PianoEngine::kParams; ++i)
    eng.setNorm(i, i < static_cast<int>(params.size()) ? params[i] : 0.5f);
  eng.reset();
  out.clear();

  const int block = 512;
  std::vector<float> io(static_cast<size_t>(block) * 2);
  const double endBeat = 32.0;
  const long total = static_cast<long>((endBeat * kBeat + 2.0) * kSr);
  long done = 0;
  size_t next = 0;
  std::vector<size_t> pendingOff;

  while (done < total) {
    const int n = static_cast<int>(std::min<long>(block, total - done));
    const double t0 = done / static_cast<double>(kSr);
    const double t1 = (done + n) / static_cast<double>(kSr);

    while (next < song.size() && song[next].at * kBeat < t1) {
      eng.noteOn(song[next].midi, song[next].vel);
      pendingOff.push_back(next);
      ++next;
    }
    std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
    eng.process(io.data(), n);
    out.insert(out.end(), io.begin(), io.begin() + static_cast<size_t>(n) * 2);
    done += n;

    for (size_t i = 0; i < pendingOff.size();) {
      const Ev& e = song[pendingOff[i]];
      if ((e.at + e.dur) * kBeat < t1) { eng.noteOff(e.midi); pendingOff.erase(pendingOff.begin() + i); }
      else ++i;
    }
    (void)t0;
  }
}

// ---------------------------------------------------------------------------
// Pitch verification.  A piano's second harmonic is often stronger than its
// fundamental, so a plain spectral peak reports the wrong octave.  The harmonic
// product spectrum multiplies the spectrum by its own x2..x5 compressions: only
// the true fundamental survives that.
// ---------------------------------------------------------------------------
static void fft(std::vector<std::complex<double>>& a) {
  const size_t n = a.size();
  for (size_t i = 1, j = 0; i < n; ++i) {
    size_t bit = n >> 1;
    for (; j & bit; bit >>= 1) j ^= bit;
    j ^= bit;
    if (i < j) std::swap(a[i], a[j]);
  }
  for (size_t len = 2; len <= n; len <<= 1) {
    const double ang = -2 * M_PI / len;
    const std::complex<double> wl(std::cos(ang), std::sin(ang));
    for (size_t i = 0; i < n; i += len) {
      std::complex<double> w(1, 0);
      for (size_t k = 0; k < len / 2; ++k) {
        const std::complex<double> u = a[i + k], v = a[i + k + len / 2] * w;
        a[i + k] = u + v;
        a[i + k + len / 2] = u - v;
        w *= wl;
      }
    }
  }
}

// Harmonic-sum pitch detector.  A piano's second harmonic is often stronger than
// its fundamental, so the single strongest spectral peak reports the wrong octave.
// Summing the energy at h*f0 for h=1..8 only peaks where the WHOLE harmonic series
// lines up, which is the definition of the fundamental.
//
// The product form (multiplying magnitudes) is the textbook HPS, but it collapses
// to zero as soon as one harmonic is missing and then noise decides the winner --
// measured on a pure tone it reported an octave BELOW.  A normalised sum does not
// have that failure.
static double hpsF0(const std::vector<float>& mono, double lo, double hi) {
  size_t n = 1;
  while (n * 2 <= mono.size() && n < (1u << 17)) n <<= 1;
  std::vector<std::complex<double>> buf(n);
  for (size_t i = 0; i < n; ++i)
    buf[i] = mono[i] * (0.5 - 0.5 * std::cos(2 * M_PI * i / (n - 1)));
  fft(buf);
  const size_t half = n / 2;
  std::vector<double> mag(half);
  for (size_t k = 0; k < half; ++k) mag[k] = std::abs(buf[k]);

  const int H = 8;
  auto scoreAt = [&](int k) -> double {
    const int hk = std::min(H, static_cast<int>(half - 1) / std::max(1, k));
    if (hk < 1) return 0.0;
    double s = 0;
    for (int h = 1; h <= hk; ++h) s += mag[static_cast<size_t>(k) * h];
    return s / hk;                       // normalised, so low k is not favoured
  };

  const int klo = std::max(2, static_cast<int>(lo * n / kSr));
  const int khi = std::min(static_cast<int>(half) / (H + 1), static_cast<int>(hi * n / kSr));
  double best = -1;
  int bestK = klo;
  for (int k = klo; k <= khi; ++k) {
    const double s = scoreAt(k);
    if (s > best) { best = s; bestK = k; }
  }
  // A sub-octave explains the spectrum just as well whenever the signal happens to
  // carry only even harmonics (a plain 2-harmonic tone does).  The two candidates
  // then score identically, and the true fundamental is the HIGHER one, so on a
  // near-tie the highest frequency wins.  Without this the detector reported an
  // octave below on a pure tone -- the exact failure it exists to catch.
  for (int k = khi; k > bestK; --k)
    if (scoreAt(k) >= 0.9 * best) { bestK = k; break; }
  // Parabolic refinement on the score, so the answer is not quantised to a bin.
  if (bestK > klo && bestK < khi) {
    const double a = scoreAt(bestK - 1), b = scoreAt(bestK), c = scoreAt(bestK + 1);
    const double den = a - 2 * b + c;
    const double d = std::fabs(den) > 1e-18 ? 0.5 * (a - c) / den : 0.0;
    return (bestK + std::max(-0.5, std::min(0.5, d))) * static_cast<double>(kSr) / n;
  }
  return bestK * static_cast<double>(kSr) / n;
}

static int verify(PianoEngine& eng, const std::vector<float>& params) {
  // Validate the analyser itself before trusting it on the instrument: a pitch
  // detector that fails on a pure sine cannot be used to judge anything.
  {
    std::printf("self-test of the analyser on synthetic tones\n");
    int bad = 0;
    for (double f : {261.63, 440.0, 523.25}) {
      std::vector<float> s(static_cast<size_t>(0.6 * kSr));
      for (size_t i = 0; i < s.size(); ++i)
        s[i] = 0.4f * std::sin(2 * M_PI * f * i / kSr)
             + 0.2f * std::sin(2 * M_PI * 2 * f * i / kSr);   // strong 2nd harmonic
      const double got = hpsF0(s, f * 0.45, f * 2.2);
      const double c = 1200.0 * std::log2(got / f);
      std::printf("  %7.2f Hz -> %7.2f Hz (%+6.1f cents) %s\n", f, got, c,
                  std::fabs(c) < 25 ? "ok" : "FAUX");
      if (std::fabs(c) >= 25) ++bad;
    }
    if (bad) { std::printf("\nANALYSEUR FAUX : le banc ne peut rien prouver.\n"); return 1; }
    std::printf("\n");
  }

  std::printf("%-5s %-6s %9s %9s %8s %6s  %-12s | %9s %8s %s\n",
              "midi", "note", "attendu", "mesure", "ecart", "root",
              "moteur neuf", "mesure2", "ecart2", "moteur reutilise");
  int bad = 0;
  for (int i = 0; i < kFillLen; ++i) {
    const int midi = kFill[i].midi;
    std::vector<Ev> one{{midi, 0.f, 1.2f, 100}};
    std::vector<float> audio;
    // A FRESH engine per note.  If the result changes with reuse, the fault is in
    // the harness or in reset(); if it does not, the engine itself is at fault.
    PianoEngine fresh;
    fresh.prepare(kSr, 512);
    if (!fresh.loadSampleBank(g_bank.data(), g_bank.size())) {
      std::fprintf(stderr, "pack refused\n");
      return 2;
    }
    render(fresh, params, one, audio);

    // Analyse the sustain, past the hammer transient.
    std::vector<float> mono;
    const long a = static_cast<long>(0.15 * kSr), b = static_cast<long>(0.75 * kSr);
    for (long k = a; k < b && 2 * k + 1 < static_cast<long>(audio.size()); ++k)
      mono.push_back(0.5f * (audio[2 * k] + audio[2 * k + 1]));

    const double want = midiHz(midi);
    // Search a wide window: if the engine transposed, the answer is not near.
    const double got = hpsF0(mono, want * 0.45, want * 2.2);
    const double cents = 1200.0 * std::log2(got / want);

    // The same note again, this time on a REUSED engine.  The app plays every note
    // through one instance, so if reuse leaks the previous note the app is wrong
    // even though a fresh engine is right.
    std::vector<float> audio2;
    render(eng, params, one, audio2);
    std::vector<float> mono2;
    for (long k = a; k < b && 2 * k + 1 < static_cast<long>(audio2.size()); ++k)
      mono2.push_back(0.5f * (audio2[2 * k] + audio2[2 * k + 1]));
    const double got2 = hpsF0(mono2, want * 0.45, want * 2.2);
    const double cents2 = 1200.0 * std::log2(got2 / want);

    const bool ok = std::fabs(cents) < 25.0;
    const bool ok2 = std::fabs(cents2) < 25.0;
    if (!ok || !ok2) ++bad;
    const auto* rg = g_probe.pick(midi, 100);
    std::printf("%-5d %-6s %9.1f %9.1f %+7.1f %6d  %s   | %9.1f %+7.1f %s\n",
                midi, noteName(midi), want, got, cents, rg ? rg->root : -1,
                ok ? "ok" : "FAUX", got2, cents2, ok2 ? "ok" : "FAUX");
  }
  std::printf("\n%s\n", bad ? "ECHEC" : "TOUTES LES NOTES JUSTES (0 ecart > 25 cents)");

  // The progression itself: the twelve notes played IN ORDER through one engine,
  // each held long enough to measure.  This is what a listener hears, and it is
  // the case the reused-engine bug broke.
  {
    std::printf("\nprogression jouee dans l'ordre, un seul moteur\n");
    std::vector<Ev> seq;
    float t = 0;
    for (int i = 0; i < kFillLen; ++i) { seq.push_back({kFill[i].midi, t, 0.85f, 100}); t += 0.95f; }
    std::vector<float> audio;
    PianoEngine one;
    one.prepare(kSr, 512);
    one.loadSampleBank(g_bank.data(), g_bank.size());
    render(one, params, seq, audio);

    int bad2 = 0;
    for (int i = 0; i < kFillLen; ++i) {
      const double at = seq[i].at * kBeat;
      std::vector<float> mono;
      const long a = static_cast<long>((at + 0.20) * kSr), b = static_cast<long>((at + 0.80) * kSr);
      for (long k = a; k < b && 2 * k + 1 < static_cast<long>(audio.size()); ++k)
        mono.push_back(0.5f * (audio[2 * k] + audio[2 * k + 1]));
      const double want = midiHz(kFill[i].midi);
      const double got = hpsF0(mono, want * 0.45, want * 2.2);
      const double c = 1200.0 * std::log2(got / want);
      const bool ok = std::fabs(c) < 25.0;
      if (!ok) ++bad2;
      std::printf("  %2d. %-4s attendu %7.1f Hz  mesure %7.1f Hz  %+6.1f cents  %s\n",
                  i + 1, noteName(kFill[i].midi).c_str(), want, got, c, ok ? "ok" : "FAUX");
    }
    std::printf("%s\n", bad2 ? "PROGRESSION FAUSSE" : "progression juste (12/12)");
    bad += bad2;
  }
  return bad;
}

int main(int argc, char** argv) {
  if (argc < 3) {
    std::fprintf(stderr, "usage: %s <piano.bin> <presets.txt> <outdir|verify> [names]\n",
                 argv[0]);
    return 2;
  }
  auto bin = readFile(argv[1]);
  g_bank = bin;
  auto presets = loadPresets(argv[2]);
  if (presets.empty()) { std::fprintf(stderr, "no presets\n"); return 2; }

  PianoEngine eng;
  eng.prepare(kSr, 512);
  if (!eng.loadSampleBank(bin.data(), bin.size())) {
    std::fprintf(stderr, "pack refused by the engine\n");
    return 2;
  }

  if (std::string(argv[3]) == "verify") {
    g_probe.load(bin.data(), bin.size());
    // Studio: the neutral preset, so the pitch is not coloured by a character.
    const std::vector<float>* neutral = &presets[0].v;
    for (const auto& p : presets)
      if (p.name == "Studio") neutral = &p.v;
    return verify(eng, *neutral) ? 1 : 0;
  }

  const std::string names = argc > 4 ? argv[4] : "Honky,Studio,Bright,Upright";
  const auto song = buildSong();
  const std::string lo = noteName(kFill[0].midi), hi = noteName(kFill[kFillLen - 1].midi);
  std::printf("fill: %d notes, %s .. %s (one octave down)\n", kFillLen, lo.c_str(),
              hi.c_str());

  std::vector<std::string> wanted;
  for (size_t pos = 0; pos < names.size();) {
    size_t nxt = names.find(',', pos);
    if (nxt == std::string::npos) nxt = names.size();
    wanted.push_back(names.substr(pos, nxt - pos));
    pos = nxt + 1;
  }

  for (const auto& want : wanted) {
    const Preset* p = nullptr;
    for (const auto& q : presets)
      if (q.name == want) p = &q;
    if (!p) { std::fprintf(stderr, "preset '%s' absent\n", want.c_str()); continue; }
    std::vector<float> audio;
    render(eng, p->v, song, audio);
    std::string slug;
    for (char c : want) slug += (c == ' ') ? '_' : static_cast<char>(std::tolower(c));
    const std::string path = std::string(argv[3]) + "/blues_" + slug + ".f32";
    std::ofstream o(path, std::ios::binary);
    o.write(reinterpret_cast<const char*>(audio.data()),
            static_cast<std::streamsize>(audio.size() * sizeof(float)));
    double peak = 0;
    for (float x : audio) peak = std::max(peak, static_cast<double>(std::fabs(x)));
    std::printf("  %-8s %6.2f s  peak %.3f%s  -> %s\n", want.c_str(),
                audio.size() / 2.0 / kSr, peak, peak > 0.999 ? "  CLIP" : "",
                path.c_str());
  }
  return 0;
}
