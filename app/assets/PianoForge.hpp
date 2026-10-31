// PianoForge.hpp — header-only C++17. No dependencies.
//
// Turns a handful of simple piano recordings (e.g. one note every 3-4 semitones, 1-3 velocities, 3-6 s each)
// into a complete 88-key piano that sounds rich without weighing gigabytes:
//
//   1. ANALYSIS  (offline, once)   YIN pitch tracking -> exact tuning of every sample (no mistuned zones)
//                                  band-limiting for the pitch range each zone will cover (no aliasing)
//                                  loop baking: period-locked loop + level flattening + equal-power crossfade,
//                                  so a 4 s recording can sustain for 30 s under an adjustable decay model
//                                  16-bit storage with TPDF dither, one stored copy per zone (tens of MB, not GB)
//   2. VOICES    (real time)       Kaiser-windowed sinc resampler (24 taps, 512 phases, linear phase interpolation)
//                                  3 detuned read heads per note = natural string beating (unison strings)
//                                  velocity -> loudness curve + timbre (high shelf + low-pass), key tracked
//                                  hammer noise burst, damper release with falling low-pass, pedal handling
//   3. RESONANCE (real time)       88 digital-waveguide strings (Thiran fractional delay, loop damping) excited by
//                                  the playing: sympathetic resonance, strong when the pedal is down
//                                  synthetic modal soundboard impulse response, partitioned FFT convolution
//
// Real-time rules: process() never allocates, never locks, flushes denormals. Control thread -> audio thread through
// a lock-free SPSC event queue. Build the PianoBank (finalize) BEFORE starting audio.
//
// References: de Cheveigne & Kawahara, "YIN, a fundamental frequency estimator" (JASA 2002); J.O. Smith, "Physical Audio
// Signal Processing" (CCRMA: digital waveguides, Thiran allpass, Kaiser-windowed sinc interpolation); Fletcher & Rossing,
// "The Physics of Musical Instruments" (piano decay, inharmonicity, soundboard modes); RBJ Audio EQ Cookbook (biquads).
//
// Quick use:
//   pf::PianoBank bank;
//   bank.addFile("C3_mf.wav", 48, 80); bank.addFile("E3_mf.wav", 52, 80); ... bank.finalize(48000);
//   pf::PianoEngine piano; piano.init(48000, std::move(bank));
//   piano.noteOn(60, 0.8f); piano.sustain(true);      // from any thread
//   piano.process(left, right, frames);                // from the audio callback

#pragma once
#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <complex>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <random>
#include <string>
#include <utility>
#include <vector>
#if defined(__SSE2__)
#include <xmmintrin.h>
#endif

namespace pf {

using cd = std::complex<double>;
constexpr double kPi = 3.141592653589793238462643383279502884;
inline double midiToHz(double m, double a4 = 440.0) { return a4 * std::pow(2.0, (m - 69.0) / 12.0); }
template <class T> inline T clampT(T v, T lo, T hi) { return v < lo ? lo : (v > hi ? hi : v); }

// Flush denormals to zero for the duration of a scope (SSE and AArch64).
struct ScopedFTZ {
#if defined(__SSE2__)
  unsigned saved;
  ScopedFTZ() : saved(_mm_getcsr()) { _mm_setcsr(saved | 0x8040u); }
  ~ScopedFTZ() { _mm_setcsr(saved); }
#elif defined(__aarch64__)
  uint64_t saved;
  ScopedFTZ() { asm volatile("mrs %0, fpcr" : "=r"(saved)); uint64_t v = saved | (1ull << 24); asm volatile("msr fpcr, %0" ::"r"(v)); }
  ~ScopedFTZ() { asm volatile("msr fpcr, %0" ::"r"(saved)); }
#endif
};

/* ═════════════════════════════ WAV I/O ═════════════════════════════ */

struct Audio {
  std::vector<float> data;  // interleaved
  int channels = 1, sampleRate = 44100;
  size_t frames() const { return channels ? data.size() / (size_t)channels : 0; }
};

inline bool readWav(const std::string& path, Audio& out) {
  std::ifstream f(path, std::ios::binary);
  if (!f) return false;
  char id[4]; uint32_t size = 0;
  auto rd32 = [&](uint32_t& v) { return (bool)f.read(reinterpret_cast<char*>(&v), 4); };
  if (!f.read(id, 4) || std::memcmp(id, "RIFF", 4) || !rd32(size) || !f.read(id, 4) || std::memcmp(id, "WAVE", 4)) return false;
  uint16_t fmt = 0, ch = 0, bits = 0; uint32_t sr = 0; std::vector<uint8_t> raw;
  while (f.read(id, 4) && rd32(size)) {
    if (!std::memcmp(id, "fmt ", 4)) {
      std::vector<uint8_t> b(std::max<uint32_t>(size, 40));
      f.read(reinterpret_cast<char*>(b.data()), size);
      std::memcpy(&fmt, &b[0], 2); std::memcpy(&ch, &b[2], 2); std::memcpy(&sr, &b[4], 4); std::memcpy(&bits, &b[14], 2);
      if (fmt == 0xFFFE && size >= 26) std::memcpy(&fmt, &b[24], 2);
    } else if (!std::memcmp(id, "data", 4)) {
      raw.resize(size); f.read(reinterpret_cast<char*>(raw.data()), size); raw.resize((size_t)f.gcount());
    } else f.seekg(size, std::ios::cur);
    if (size & 1) f.seekg(1, std::ios::cur);
  }
  if (!ch || !sr || raw.empty() || bits % 8) return false;
  const size_t bps = bits / 8, n = raw.size() / bps;
  out.channels = ch; out.sampleRate = (int)sr; out.data.assign(n - n % ch, 0.f);
  for (size_t i = 0; i < out.data.size(); ++i) {
    const uint8_t* p = &raw[i * bps]; float v = 0;
    if (fmt == 3 && bits == 32) { float x; std::memcpy(&x, p, 4); v = x; }
    else if (fmt == 3 && bits == 64) { double x; std::memcpy(&x, p, 8); v = (float)x; }
    else if (bits == 8) v = ((int)p[0] - 128) / 128.f;
    else if (bits == 16) { int16_t x; std::memcpy(&x, p, 2); v = x / 32768.f; }
    else if (bits == 24) { int32_t x = (int32_t)(((uint32_t)p[0] << 8) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 24)); v = x / 2147483648.f; }
    else if (bits == 32) { int32_t x; std::memcpy(&x, p, 4); v = x / 2147483648.f; }
    else return false;
    out.data[i] = v;
  }
  return true;
}

inline bool writeWav16(const std::string& path, const float* interleaved, size_t frames, int channels, int sr) {
  std::ofstream f(path, std::ios::binary);
  if (!f) return false;
  const uint32_t dataBytes = (uint32_t)(frames * channels * 2), riff = 36 + dataBytes, fmtSize = 16, byteRate = sr * channels * 2;
  const uint16_t pcm = 1, ch = (uint16_t)channels, align = (uint16_t)(channels * 2), bits = 16; const uint32_t srate = (uint32_t)sr;
  f.write("RIFF", 4); f.write((const char*)&riff, 4); f.write("WAVEfmt ", 8); f.write((const char*)&fmtSize, 4);
  f.write((const char*)&pcm, 2); f.write((const char*)&ch, 2); f.write((const char*)&srate, 4); f.write((const char*)&byteRate, 4);
  f.write((const char*)&align, 2); f.write((const char*)&bits, 2); f.write("data", 4); f.write((const char*)&dataBytes, 4);
  for (size_t i = 0; i < frames * channels; ++i) { int16_t s = (int16_t)std::lrintf(clampT(interleaved[i], -1.f, 1.f) * 32767.f); f.write((const char*)&s, 2); }
  return (bool)f;
}

/* ═════════════════════════════ DSP BUILDING BLOCKS ═════════════════════════════ */

class FFT {  // radix-2, double precision, in place
 public:
  explicit FFT(size_t n = 2) : n_(n), tw_(n / 2), rev_(n) {
    for (size_t i = 0; i < n / 2; ++i) tw_[i] = std::polar(1.0, -2.0 * kPi * (double)i / (double)n);
    size_t lg = 0; while (((size_t)1 << lg) < n) ++lg;
    for (size_t i = 0; i < n; ++i) { size_t r = 0; for (size_t b = 0; b < lg; ++b) if (i & ((size_t)1 << b)) r |= (size_t)1 << (lg - 1 - b); rev_[i] = r; }
  }
  void forward(cd* x) const { run(x); }
  void inverse(cd* x) const { for (size_t i = 0; i < n_; ++i) x[i] = std::conj(x[i]); run(x); for (size_t i = 0; i < n_; ++i) x[i] = std::conj(x[i]) / (double)n_; }
 private:
  void run(cd* x) const {
    for (size_t i = 0; i < n_; ++i) if (i < rev_[i]) std::swap(x[i], x[rev_[i]]);
    for (size_t len = 2; len <= n_; len <<= 1) {
      const size_t half = len / 2, step = n_ / len;
      for (size_t i = 0; i < n_; i += len) for (size_t j = 0; j < half; ++j) {
        cd u = x[i + j], v = x[i + j + half] * tw_[j * step]; x[i + j] = u + v; x[i + j + half] = u - v;
      }
    }
  }
  size_t n_; std::vector<cd> tw_; std::vector<size_t> rev_;
};

// Kaiser-windowed sinc, 24 taps, 512 phases, linear interpolation between phases. Every phase is normalised to unit DC gain.
class SincKernel {
 public:
  static constexpr int kTaps = 24, kHalf = kTaps / 2, kPhases = 512;
  explicit SincKernel(double cutoff = 0.95, double beta = 9.0) : tab_((size_t)(kPhases + 1) * kTaps) {
    auto i0 = [](double x) { double s = 1, t = 1; for (int k = 1; k < 40; ++k) { t *= (x / (2.0 * k)) * (x / (2.0 * k)); s += t; } return s; };
    const double den = i0(beta);
    for (int p = 0; p <= kPhases; ++p) {
      const double fr = (double)p / kPhases; double sum = 0; float* row = &tab_[(size_t)p * kTaps];
      for (int k = 0; k < kTaps; ++k) {
        const double d = (double)(k - (kHalf - 1)) - fr, u = d / kHalf;
        const double w = std::fabs(u) < 1.0 ? i0(beta * std::sqrt(1.0 - u * u)) / den : 0.0;
        const double a = kPi * cutoff * d, s = std::fabs(a) < 1e-9 ? 1.0 : std::sin(a) / a;
        row[k] = (float)(cutoff * s * w); sum += row[k];
      }
      for (int k = 0; k < kTaps; ++k) row[k] = (float)(row[k] / sum);
    }
  }
  // b points at the sample (i0 - (kHalf-1)); frac in [0,1)
  template <class T> inline float eval(const T* b, double frac) const {
    const double ph = frac * kPhases; const int i = (int)ph; const float f = (float)(ph - i);
    const float* r0 = &tab_[(size_t)i * kTaps]; const float* r1 = r0 + kTaps; float s0 = 0, s1 = 0;
    for (int k = 0; k < kTaps; ++k) { const float x = (float)b[k]; s0 += r0[k] * x; s1 += r1[k] * x; }
    return s0 + f * (s1 - s0);
  }
 private:
  std::vector<float> tab_;
};

struct Biquad {  // RBJ cookbook, transposed direct form II, double precision state
  double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0, z1 = 0, z2 = 0;
  void set(double B0, double B1, double B2, double A0, double A1, double A2) { b0 = B0 / A0; b1 = B1 / A0; b2 = B2 / A0; a1 = A1 / A0; a2 = A2 / A0; }
  void lowpass(double fs, double f, double q) {
    const double w = 2 * kPi * f / fs, c = std::cos(w), al = std::sin(w) / (2 * q);
    set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al);
  }
  void bandpass(double fs, double f, double q) {
    const double w = 2 * kPi * f / fs, c = std::cos(w), al = std::sin(w) / (2 * q);
    set(al, 0, -al, 1 + al, -2 * c, 1 - al);
  }
  void highShelf(double fs, double f, double gainDb, double slope = 1.0) {
    const double A = std::pow(10.0, gainDb / 40.0), w = 2 * kPi * f / fs, c = std::cos(w), s = std::sin(w);
    const double al = s / 2 * std::sqrt((A + 1 / A) * (1 / slope - 1) + 2), sq = 2 * std::sqrt(A) * al;
    set(A * ((A + 1) + (A - 1) * c + sq), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - sq),
        (A + 1) - (A - 1) * c + sq, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - sq);
  }
  inline float process(float x) { const double y = b0 * x + z1; z1 = b1 * x - a1 * y + z2; z2 = b2 * x - a2 * y; return (float)y; }
  void reset() { z1 = z2 = 0; }
};

struct Noise {
  uint32_t s = 2463534242u;
  inline float next() { s ^= s << 13; s ^= s >> 17; s ^= s << 5; return (float)((int32_t)s) * (1.0f / 2147483648.0f); }
};

/* ═════════════════════════════ ANALYSIS (offline) ═════════════════════════════ */

// YIN fundamental estimator with parabolic refinement. Returns Hz (0 if unreliable) and a confidence in [0,1].
inline double yinPitch(const std::vector<float>& x, size_t start, size_t len, int sr, double fmin, double fmax, double& conf) {
  conf = 0;
  if (start + len > x.size()) return 0;
  const size_t maxTau = std::min<size_t>((size_t)(sr / fmin), len / 2), minTau = std::max<size_t>(2, (size_t)(sr / fmax));
  if (maxTau <= minTau + 3) return 0;
  const size_t W = len - maxTau;
  std::vector<double> d(maxTau + 1, 0.0), cm(maxTau + 1, 1.0);
  for (size_t t = 1; t <= maxTau; ++t) { double s = 0; for (size_t j = 0; j < W; ++j) { const double e = (double)x[start + j] - x[start + j + t]; s += e * e; } d[t] = s; }
  double run = 0;
  for (size_t t = 1; t <= maxTau; ++t) { run += d[t]; cm[t] = run > 0 ? d[t] * (double)t / run : 1.0; }
  size_t best = 0;
  for (size_t t = minTau; t + 1 < maxTau; ++t) if (cm[t] < 0.15) { while (t + 1 < maxTau && cm[t + 1] < cm[t]) ++t; best = t; break; }
  if (!best) { best = (size_t)(std::min_element(cm.begin() + minTau, cm.begin() + maxTau) - cm.begin()); if (cm[best] > 0.4) return 0; }
  const double a = cm[best - 1], b = cm[best], c = cm[best + 1], den = a - 2 * b + c, off = den != 0 ? 0.5 * (a - c) / den : 0.0;
  conf = 1.0 - cm[best];
  return sr / ((double)best + off);
}

// Windowed-sinc low-pass (Kaiser), zero-phase, direct convolution. cutoffNorm = cutoffHz / sampleRate (< 0.5).
inline void lowpassInPlace(std::vector<float>& x, double cutoffNorm, int taps = 161) {
  if (cutoffNorm >= 0.49 || x.empty()) return;
  const int h = taps / 2; std::vector<double> k(taps); double sum = 0;
  auto i0 = [](double v) { double s = 1, t = 1; for (int i = 1; i < 40; ++i) { t *= (v / (2.0 * i)) * (v / (2.0 * i)); s += t; } return s; };
  const double beta = 8.6, den = i0(beta);
  for (int i = 0; i < taps; ++i) {
    const double n = i - h, u = n / (h + 1), w = i0(beta * std::sqrt(std::max(0.0, 1 - u * u))) / den;
    const double a = 2 * kPi * cutoffNorm * n, s = std::fabs(a) < 1e-12 ? 1.0 : std::sin(a) / a;
    k[i] = 2 * cutoffNorm * s * w; sum += k[i];
  }
  for (auto& v : k) v /= sum;
  std::vector<float> y(x.size());
  for (long n = 0; n < (long)x.size(); ++n) {
    double acc = 0;
    for (int i = 0; i < taps; ++i) { const long j = n + i - h; if (j >= 0 && j < (long)x.size()) acc += k[i] * x[(size_t)j]; }
    y[(size_t)n] = (float)acc;
  }
  x.swap(y);
}

struct LoopInfo { bool valid = false; size_t start = 0, end = 0; };

// Period-locked loop. The tail amplitude is flattened so the wrap is level-continuous and the end is cross-faded with the
// start (equal power). The buffer is truncated at the loop end. The sustain decay is then applied by the voice (see loopTau).
inline LoopInfo makeLoop(std::vector<std::vector<float>>& ch, double period, int sr) {
  LoopInfo li; const size_t N = ch[0].size();
  const long P = std::max<long>(2, std::llround(period));
  const size_t xf = (size_t)std::max(0.02 * sr, 2.0 * (double)P);
  const long reps = std::max<long>(1, std::llround(std::max(0.35 * sr, 6.0 * (double)P) / (double)P));
  const size_t L = (size_t)(reps * P);
  if (N < L + 3 * xf + (size_t)(0.3 * sr)) return li;
  std::vector<float> m(N, 0.f);
  for (auto& c : ch) for (size_t i = 0; i < N; ++i) m[i] += c[i] / (float)ch.size();
  const size_t Wc = std::min<size_t>(4096, (size_t)(2 * P));
  size_t bestE = 0; double bestScore = -2;
  const size_t eHi = N - xf - 1, eLo = eHi > (size_t)(4 * P) ? eHi - (size_t)(4 * P) : 0;
  for (size_t E = eHi; E > eLo; --E) {
    if (E < L + xf + Wc) break;
    const size_t S = E - L; double ab = 0, aa = 0, bb = 0;
    for (size_t j = 0; j < Wc; ++j) { const double a = m[S - Wc + j], b = m[E - Wc + j]; ab += a * b; aa += a * a; bb += b * b; }
    const double sc = ab / std::sqrt(aa * bb + 1e-30);
    if (sc > bestScore) { bestScore = sc; bestE = E; }
  }
  if (!bestE || bestScore < 0.5) return li;
  const size_t E = bestE, S = E - L;
  auto rms = [&](size_t a, size_t b) { double s = 0; for (size_t i = a; i < b; ++i) s += (double)m[i] * m[i]; return std::sqrt(s / (double)(b - a) + 1e-30); };
  const size_t w2 = std::min<size_t>(2 * (size_t)P, L / 2);
  const double r = clampT(rms(S, S + w2) / rms(E - w2, E), 0.5, 2.0);
  for (auto& c : ch) {
    for (size_t i = S; i < E; ++i) c[i] *= (float)(1.0 + (r - 1.0) * (double)(i - S) / (double)(E - S));
    for (size_t i = 0; i < xf; ++i) {
      const double t = ((double)i + 0.5) / (double)xf, a = std::cos(t * kPi / 2), b = std::sin(t * kPi / 2);
      c[E - xf + i] = (float)(a * c[E - xf + i] + b * c[S - xf + i]);
    }
    c.resize(E);
  }
  li.valid = true; li.start = S; li.end = E;
  return li;
}

/* ═════════════════════════════ SAMPLE BANK ═════════════════════════════ */

struct Zone {
  static constexpr size_t kPad = 24;
  std::vector<int16_t> ch[2];            // [kPad zeros][frames][kPad tail] per channel
  int nch = 1, sr = 44100, rootNote = 60, lo = 0, hi = 127;
  size_t frames = 0;
  double rootHz = 261.63;                // measured by YIN, not nominal
  float vel = 0.7f;                      // recording velocity, 0..1
  LoopInfo loop;
  size_t bytes() const { return (ch[0].size() + ch[1].size()) * sizeof(int16_t); }
  inline float read(int c, double pos, const SincKernel& K) const {
    const long i0 = (long)pos; const double fr = pos - (double)i0;
    return K.eval(ch[c].data() + kPad + i0 - (SincKernel::kHalf - 1), fr) * (1.f / 32768.f);
  }
};

class PianoBank {
 public:
  void addSample(Audio a, int midiNote, int velocity127) { raw_.push_back({std::move(a), midiNote, velocity127}); }
  bool addFile(const std::string& path, int midiNote, int velocity127) { Audio a; if (!readWav(path, a)) return false; addSample(std::move(a), midiNote, velocity127); return true; }

  // Analyse and build the playable zones for a given output sample rate. Heavy: do it at load time, never in the audio thread.
  void finalize(int hostSr) {
    zones_.clear(); Noise dith; dith.s = 0x9E3779B9u;
    for (auto& r : raw_) {
      const Audio& a = r.audio; const int nch = std::min(2, a.channels), sr = a.sampleRate; const size_t N = a.frames();
      if (N < (size_t)(0.2 * sr)) continue;
      std::vector<std::vector<float>> ch((size_t)nch, std::vector<float>(N));
      for (size_t i = 0; i < N; ++i) for (int c = 0; c < nch; ++c) ch[(size_t)c][i] = a.data[i * (size_t)a.channels + (size_t)c];
      std::vector<float> mono(N, 0.f);
      for (auto& c : ch) for (size_t i = 0; i < N; ++i) mono[i] += c[i] / (float)nch;
      // 1. exact tuning
      const double nominal = midiToHz(r.note), maxTauN = sr / (nominal * 0.8);
      const size_t start = std::min<size_t>((size_t)(0.15 * sr), N / 4), len = std::min<size_t>(N - start, (size_t)std::max(3.0 * maxTauN, 8192.0));
      double conf = 0; double f0 = yinPitch(mono, start, len, sr, nominal * 0.8, nominal * 1.25, conf);
      if (conf < 0.6 || std::fabs(1200.0 * std::log2(f0 / nominal)) > 100.0) f0 = nominal;
      Zone z; z.nch = nch; z.sr = sr; z.rootNote = r.note; z.rootHz = f0; z.vel = clampT(r.vel / 127.f, 0.f, 1.f);
      r.f0 = f0;
      raw2_.push_back({std::move(ch), f0, sr, nch, r.note, z.vel});
    }
    raw_.clear();
    // 2. key ranges per velocity layer, then per-zone processing
    std::vector<float> vels; for (auto& r : raw2_) vels.push_back(r.vel);
    std::sort(vels.begin(), vels.end()); vels.erase(std::unique(vels.begin(), vels.end()), vels.end());
    for (float v : vels) {
      std::vector<size_t> idx; for (size_t i = 0; i < raw2_.size(); ++i) if (raw2_[i].vel == v) idx.push_back(i);
      std::sort(idx.begin(), idx.end(), [&](size_t a, size_t b) { return raw2_[a].note < raw2_[b].note; });
      for (size_t k = 0; k < idx.size(); ++k) {
        auto& r = raw2_[idx[k]];
        const int lo = k == 0 ? 0 : (raw2_[idx[k - 1]].note + r.note) / 2 + 1, hi = k + 1 == idx.size() ? 127 : (r.note + raw2_[idx[k + 1]].note) / 2;
        buildZone(r, lo, hi, hostSr);
      }
    }
    raw2_.clear();
  }

  // Nearest key range, nearest recorded velocity layer.
  const Zone* find(int note, float vel01) const {
    const Zone* best = nullptr; float bd = 1e9f;
    for (const auto& z : zones_) if (note >= z.lo && note <= z.hi) { const float d = std::fabs(z.vel - vel01); if (d < bd) { bd = d; best = &z; } }
    return best;
  }
  size_t memoryBytes() const { size_t s = 0; for (auto& z : zones_) s += z.bytes(); return s; }
  size_t zoneCount() const { return zones_.size(); }
  const SincKernel& kernel() const { return kernel_; }

 private:
  struct Raw { Audio audio; int note; int vel; double f0 = 0; };
  struct Raw2 { std::vector<std::vector<float>> ch; double f0; int sr; int nch; int note; float vel; };
  void buildZone(Raw2& r, int lo, int hi, int hostSr) {
    Zone z; z.nch = r.nch; z.sr = r.sr; z.rootNote = r.note; z.rootHz = r.f0; z.vel = r.vel; z.lo = lo; z.hi = hi;
    // band-limit for the highest transposition this zone will see (no aliasing when pitched up)
    const double maxUp = std::pow(2.0, std::max(0, hi - r.note) / 12.0) * 1.02;
    const double cutHz = 0.45 * hostSr / maxUp;
    for (auto& c : r.ch) lowpassInPlace(c, cutHz / r.sr);
    // loop for unlimited sustain
    z.loop = makeLoop(r.ch, r.sr / r.f0, r.sr);
    const size_t F = r.ch[0].size(); z.frames = F;
    float peak = 1e-9f; for (auto& c : r.ch) for (float x : c) peak = std::max(peak, std::fabs(x));
    const float g = 0.95f / peak; Noise n; n.s = 0x1234567u + (uint32_t)r.note * 977u;
    for (int c = 0; c < r.nch; ++c) {
      auto& q = z.ch[c]; q.assign(Zone::kPad + F + Zone::kPad, 0);
      for (size_t i = 0; i < F; ++i) {
        const float dither = (n.next() + n.next()) * 0.5f;   // TPDF, +-1 LSB
        q[Zone::kPad + i] = (int16_t)clampT(std::lrintf(r.ch[(size_t)c][i] * g * 32767.f + dither), -32768L, 32767L);
      }
      if (z.loop.valid) for (size_t k = 0; k < Zone::kPad; ++k) q[Zone::kPad + F + k] = q[Zone::kPad + z.loop.start + k];  // seamless wrap
    }
    zones_.push_back(std::move(z));
  }
  std::vector<Raw> raw_; std::vector<Raw2> raw2_; std::vector<Zone> zones_; SincKernel kernel_;
};

/* ═════════════════════════════ BODY AND STRING RESONANCE ═════════════════════════════ */

// Synthetic soundboard response: ~28 decaying modes (60-3600 Hz, longer decay at low frequency) plus a diffuse tail.
inline std::vector<float> makeBodyIR(int sr, uint32_t seed, double seconds = 0.32) {
  const size_t n = (size_t)(seconds * sr); std::vector<double> ir(n, 0.0);
  std::mt19937 rng(seed); std::uniform_real_distribution<double> U(0.0, 1.0);
  for (int m = 0; m < 28; ++m) {
    const double f = 60.0 * std::pow(60.0, U(rng)), t60 = clampT(0.9 * std::pow(200.0 / f, 0.35), 0.06, 0.9);
    const double dec = std::pow(10.0, -3.0 / (t60 * sr)), amp = (0.5 + U(rng)) / std::sqrt(f / 100.0), ph = 2 * kPi * U(rng), w = 2 * kPi * f / sr;
    double g = 1.0; for (size_t i = 0; i < n; ++i) { ir[i] += amp * g * std::sin(w * (double)i + ph); g *= dec; }
  }
  std::normal_distribution<double> Nn(0.0, 1.0);
  for (size_t i = 0; i < n; ++i) ir[i] += 0.12 * Nn(rng) * std::exp(-(double)i / (0.09 * sr));
  double e = 0; for (double v : ir) e += v * v;
  std::vector<float> out(n); const double s = 1.0 / std::sqrt(e + 1e-30);
  for (size_t i = 0; i < n; ++i) out[i] = (float)(ir[i] * s);
  return out;
}

// Uniformly partitioned overlap-save convolution, one input spectrum shared by two output channels. Latency = one block.
class BodyConvolver {
 public:
  void init(const std::vector<float>& irL, const std::vector<float>& irR, size_t B = 256) {
    B_ = B; N_ = 2 * B; fft_ = FFT(N_); const size_t bins = N_ / 2 + 1;
    P_ = (std::max(irL.size(), irR.size()) + B - 1) / B;
    const std::vector<float>* irs[2] = {&irL, &irR};
    std::vector<cd> buf(N_);
    for (int c = 0; c < 2; ++c) {
      H_[c].assign(P_ * bins, cd(0, 0));
      for (size_t p = 0; p < P_; ++p) {
        std::fill(buf.begin(), buf.end(), cd(0, 0));
        for (size_t i = 0; i < B; ++i) { const size_t j = p * B + i; if (j < irs[c]->size()) buf[i] = (*irs[c])[j]; }
        fft_.forward(buf.data()); for (size_t k = 0; k < bins; ++k) H_[c][p * bins + k] = buf[k];
      }
      out_[c].assign(B, 0.f);
    }
    X_.assign(P_ * bins, cd(0, 0)); prev_.assign(B, 0.f); cur_.assign(B, 0.f); work_.assign(N_, cd(0, 0)); idx_ = 0; fdl_ = 0;
  }
  void process(const float* in, float* outL, float* outR, size_t n) {
    for (size_t j = 0; j < n; ++j) {
      cur_[idx_] = in[j]; outL[j] = out_[0][idx_]; outR[j] = out_[1][idx_];
      if (++idx_ == B_) { block(); idx_ = 0; }
    }
  }
 private:
  void block() {
    const size_t bins = N_ / 2 + 1;
    for (size_t i = 0; i < B_; ++i) { work_[i] = prev_[i]; work_[B_ + i] = cur_[i]; }
    fft_.forward(work_.data());
    for (size_t k = 0; k < bins; ++k) X_[fdl_ * bins + k] = work_[k];
    for (int c = 0; c < 2; ++c) {
      for (size_t k = 0; k < bins; ++k) work_[k] = cd(0, 0);
      for (size_t p = 0; p < P_; ++p) {
        const cd* x = &X_[((fdl_ + P_ - p) % P_) * bins]; const cd* h = &H_[c][p * bins];
        for (size_t k = 0; k < bins; ++k) work_[k] += x[k] * h[k];
      }
      for (size_t k = 1; k < N_ / 2; ++k) work_[N_ - k] = std::conj(work_[k]);
      fft_.inverse(work_.data());
      for (size_t i = 0; i < B_; ++i) out_[c][i] = (float)work_[B_ + i].real();
    }
    prev_ = cur_; fdl_ = (fdl_ + 1) % P_;
  }
  size_t B_ = 256, N_ = 512, P_ = 1, idx_ = 0, fdl_ = 0; FFT fft_{2};
  std::vector<cd> H_[2], X_, work_; std::vector<float> out_[2], prev_, cur_;
};

// 88 digital-waveguide strings: feedback delay + Thiran allpass (fractional length) + loop low-pass.
// Excited by the sound being played. The pedal controls the loop gain (undamped when down).
class SympatheticBank {
 public:
  void init(int sr, double a4 = 440.0, int n0 = 21, int n1 = 108, double t60 = 4.0) {
    r_.clear(); const double lpDelay = (1.0 - damp_) / damp_;
    for (int m = n0; m <= n1; ++m) {
      const double D = sr / midiToHz(m, a4), Deff = D - lpDelay;
      const size_t Ni = (size_t)std::max(2.0, std::floor(Deff - 0.5)); const double d = Deff - (double)Ni;   // d in [0.5, 1.5)
      Res r; r.buf.assign(Ni, 0.f); r.a = (float)((1.0 - d) / (1.0 + d)); r.g = (float)std::pow(10.0, -3.0 * D / (sr * t60)); r.in = (1.0f - r.g) * 0.5f;
      r_.push_back(std::move(r));
    }
    outGain_ = r_.empty() ? 0.f : 1.0f / std::sqrt((float)r_.size());
  }
  void setPedal(bool down) { target_ = down ? 1.0f : 0.08f; }
  inline float process(float x) {
    level_ += 0.0004f * (target_ - level_);
    float sum = 0;
    for (auto& r : r_) {
      const float y = r.buf[r.idx];
      const float ap = r.a * y + r.apIn - r.a * r.apOut; r.apIn = y; r.apOut = ap;
      r.lp += damp_ * (ap - r.lp);
      r.buf[r.idx] = x * r.in + r.g * level_ * r.lp;
      if (++r.idx == r.buf.size()) r.idx = 0;
      sum += ap;
    }
    return sum * outGain_;
  }
 private:
  struct Res { std::vector<float> buf; size_t idx = 0; float apIn = 0, apOut = 0, a = 0, lp = 0, g = 0, in = 0; };
  std::vector<Res> r_; float damp_ = 0.5f, outGain_ = 0, level_ = 0.08f, target_ = 0.08f;
};

/* ═════════════════════════════ ENGINE ═════════════════════════════ */

// Piano decay model after the loop: higher notes die faster (Fletcher & Rossing). Seconds.
inline double loopTau(int note) { return clampT(8.0 * std::pow(2.0, -(note - 48) / 20.0), 0.4, 14.0); }
inline double releaseTau(int note) { return 0.05 + 0.40 * std::exp(-(note - 21) / 16.0); }

struct Params {
  double a4 = 440.0;
  float master = 0.8f, unisonCents = 1.4f, unisonMix = 0.35f, hammer = 0.45f, body = 0.28f, sympathetic = 0.12f;
  float width = 0.8f, velGamma = 1.6f, brightness = 1.0f;
};

class PianoEngine {
 public:
  static constexpr int kVoices = 64, kMaxBlock = 1024;

  void init(int sampleRate, PianoBank&& bank) {
    sr_ = (double)sampleRate; bank_ = std::move(bank);
    symb_.init(sampleRate, p_.a4); conv_.init(makeBodyIR(sampleRate, 11), makeBodyIR(sampleRate, 29));
    mono_.assign(kMaxBlock, 0.f); sym_.assign(kMaxBlock, 0.f); wetL_.assign(kMaxBlock, 0.f); wetR_.assign(kMaxBlock, 0.f);
  }
  Params& params() { return p_; }  // change fields between blocks (single writer)

  // Thread-safe control: callable from any single control thread.
  void noteOn(int note, float vel01) { push({0, (uint8_t)note, vel01}); }
  void noteOff(int note) { push({1, (uint8_t)note, 0.f}); }
  void sustain(bool down) { push({2, 0, down ? 1.f : 0.f}); }
  void allNotesOff() { push({3, 0, 0.f}); }

  void process(float* L, float* R, int frames) {
    ScopedFTZ ftz; drain();
    for (int done = 0; done < frames;) { const int n = std::min(frames - done, kMaxBlock); block(L + done, R + done, n); done += n; }
  }
  size_t memoryBytes() const { return bank_.memoryBytes(); }
  int activeVoices() const { int c = 0; for (auto& v : v_) c += v.active; return c; }

 private:
  struct Event { uint8_t type = 0, note = 0; float v = 0; };
  struct Voice {
    bool active = false, keyDown = false, sustained = false, releasing = false, looped = false;
    int note = 0; const Zone* z = nullptr; uint64_t order = 0;
    double pos[3] = {0, 0, 0}, step[3] = {1, 1, 1}; float w[3] = {1, 0, 0};
    float gain = 1, loopGain = 1, loopMul = 1, relGain = 1, relMul = 1, lpCoef = 1, lpMul = 1, lpState[2] = {0, 0}, hamEnv = 0, hamDec = 0, gl = 1, gr = 1, widthK = 1;
    Biquad shelf[2], lp[2], hb;
  };

  void push(Event e) { const uint32_t h = head_.load(std::memory_order_relaxed), n = (h + 1) % kQ; if (n == tail_.load(std::memory_order_acquire)) return; q_[h] = e; head_.store(n, std::memory_order_release); }
  void drain() {
    uint32_t t = tail_.load(std::memory_order_relaxed);
    while (t != head_.load(std::memory_order_acquire)) {
      const Event e = q_[t]; t = (t + 1) % kQ; tail_.store(t, std::memory_order_release);
      if (e.type == 0) start(e.note, e.v); else if (e.type == 1) release(e.note); else if (e.type == 2) pedal(e.v > 0.5f);
      else for (auto& v : v_) if (v.active) beginRelease(v);
    }
  }

  void start(int note, float vel) {
    const Zone* z = bank_.find(note, vel); if (!z) return;
    Voice* s = nullptr;
    for (auto& v : v_) if (!v.active) { s = &v; break; }
    if (!s) {   // steal: quietest releasing voice, else oldest
      float bg = 2.f; for (auto& v : v_) if (v.releasing && v.relGain < bg) { bg = v.relGain; s = &v; }
      if (!s) { uint64_t o = ~0ull; for (auto& v : v_) if (v.order < o) { o = v.order; s = &v; } }
    }
    Voice v; v.active = true; v.keyDown = true; v.note = note; v.z = z; v.order = ++counter_;
    const double ratio = midiToHz(note, p_.a4) / z->rootHz * ((double)z->sr / sr_), c = p_.unisonCents;
    v.step[0] = ratio; v.step[1] = ratio * std::pow(2.0, c / 1200.0); v.step[2] = ratio * std::pow(2.0, -c / 1200.0);
    const float norm = 1.f / (1.f + 2.f * p_.unisonMix); v.w[0] = norm; v.w[1] = v.w[2] = p_.unisonMix * norm;
    v.gain = 0.01f + 0.99f * std::pow(vel, p_.velGamma);
    // timbre relative to the recorded layer: brighter when harder than the sample, plus a key-tracked low-pass
    const double dB = 12.0 * (std::pow(vel, 0.8) - std::pow(z->vel, 0.8)) * p_.brightness;
    const double cut = clampT((2800.0 + 17000.0 * std::pow(vel, 1.2)) * std::pow(2.0, (note - 60) / 36.0), 1200.0, 0.46 * sr_);
    for (int c = 0; c < 2; ++c) { v.shelf[c].highShelf(sr_, 3200.0, dB); v.lp[c].lowpass(sr_, cut, 0.707); }
    v.hb.bandpass(sr_, clampT(900.0 * std::pow(2.0, (note - 48) / 24.0), 600.0, 6000.0), 0.9);
    v.hamEnv = p_.hammer * 0.12f * vel * vel; v.hamDec = (float)std::exp(-1.0 / (0.010 * sr_));
    v.loopMul = (float)std::exp(-1.0 / (loopTau(note) * sr_));
    const float pan = clampT((note - 60) / 48.f, -1.f, 1.f) * 0.30f * p_.width, th = (pan + 1.f) * (float)kPi / 4.f;
    v.gl = std::sqrt(2.f) * std::cos(th); v.gr = std::sqrt(2.f) * std::sin(th); v.widthK = 0.5f + p_.width;
    *s = v;
  }
  void beginRelease(Voice& v) { v.releasing = true; v.relMul = (float)std::exp(-1.0 / (releaseTau(v.note) * sr_)); v.lpMul = (float)std::exp(-1.0 / (0.06 * sr_)); }
  void release(int note) {
    for (auto& v : v_) if (v.active && v.keyDown && v.note == note) { v.keyDown = false; if (pedal_) v.sustained = true; else beginRelease(v); }
  }
  void pedal(bool down) {
    pedal_ = down; symb_.setPedal(down);
    if (!down) for (auto& v : v_) if (v.active && v.sustained) { v.sustained = false; beginRelease(v); }
  }

  void renderVoice(Voice& v, float* L, float* R, int n) {
    const Zone& z = *v.z; const SincKernel& K = bank_.kernel(); const bool loop = z.loop.valid;
    const double loopLen = loop ? (double)(z.loop.end - z.loop.start) : 0.0;
    for (int i = 0; i < n; ++i) {
      float sl = 0, sr = 0;
      for (int h = 0; h < 3; ++h) {
        const double p = v.pos[h]; const float l = z.read(0, p, K), r = z.nch > 1 ? z.read(1, p, K) : l;
        sl += v.w[h] * l; sr += v.w[h] * r;
        double np = p + v.step[h];
        if (loop) { if (np >= (double)z.loop.end) { np -= loopLen; v.looped = true; } }
        v.pos[h] = np;
      }
      if (!loop && v.pos[0] >= (double)z.frames) { v.active = false; return; }
      if (v.looped) v.loopGain *= v.loopMul;
      float g = v.gain * v.loopGain;
      if (v.releasing) {
        v.relGain *= v.relMul; v.lpCoef = std::max(v.lpCoef * v.lpMul, 0.03f); g *= v.relGain;
        v.lpState[0] += v.lpCoef * (sl - v.lpState[0]); v.lpState[1] += v.lpCoef * (sr - v.lpState[1]); sl = v.lpState[0]; sr = v.lpState[1];
      }
      sl = v.lp[0].process(v.shelf[0].process(sl)); sr = v.lp[1].process(v.shelf[1].process(sr));
      const float m = 0.5f * (sl + sr), s = 0.5f * (sl - sr) * v.widthK;
      float h = 0.f; if (v.hamEnv > 1e-6f) { h = v.hb.process(noise_.next()) * v.hamEnv; v.hamEnv *= v.hamDec; }
      L[i] += (m + s) * g * v.gl + h; R[i] += (m - s) * g * v.gr + h;
      if (v.relGain < 1e-4f || v.loopGain < 1e-4f) { v.active = false; return; }
    }
  }

  void block(float* L, float* R, int n) {
    std::fill(L, L + n, 0.f); std::fill(R, R + n, 0.f);
    for (auto& v : v_) if (v.active) renderVoice(v, L, R, n);
    for (int i = 0; i < n; ++i) mono_[(size_t)i] = 0.5f * (L[i] + R[i]);
    for (int i = 0; i < n; ++i) sym_[(size_t)i] = symb_.process(mono_[(size_t)i]) * p_.sympathetic;
    conv_.process(mono_.data(), wetL_.data(), wetR_.data(), (size_t)n);
    for (int i = 0; i < n; ++i) {
      const float sy = sym_[(size_t)i];
      float l = (L[i] + sy + p_.body * wetL_[(size_t)i]) * p_.master, r = (R[i] + sy + p_.body * wetR_[(size_t)i]) * p_.master;
      L[i] = l / (1.f + 0.15f * std::fabs(l)); R[i] = r / (1.f + 0.15f * std::fabs(r));   // gentle soft clip
    }
  }

  static constexpr uint32_t kQ = 1024;
  std::array<Event, kQ> q_{}; std::atomic<uint32_t> head_{0}, tail_{0};
  Params p_; PianoBank bank_; SympatheticBank symb_; BodyConvolver conv_; Noise noise_;
  std::array<Voice, kVoices> v_{}; std::vector<float> mono_, sym_, wetL_, wetR_;
  double sr_ = 48000; uint64_t counter_ = 0; bool pedal_ = false;
};

}  // namespace pf

/* ═════════════════════════════ OFFLINE DEMO ═════════════════════════════
   g++ -O3 -std=c++17 -DPIANOFORGE_DEMO demo.cpp -o demo
   ./demo out.wav C3.wav:48:80 E3.wav:52:80 G3.wav:55:80 C4.wav:60:80 ...   (file:midiNote:velocity)
   Renders a C major chord then an arpeggio with the pedal down, and prints the memory used. */
#ifdef PIANOFORGE_DEMO
#include <cstdio>
#include <cstdlib>
int main(int argc, char** argv) {
  if (argc < 3) { std::fprintf(stderr, "usage: %s out.wav file:note:vel ...\n", argv[0]); return 1; }
  const int sr = 48000; pf::PianoBank bank;
  for (int i = 2; i < argc; ++i) {
    std::string a = argv[i]; const size_t p1 = a.find(':'), p2 = a.find(':', p1 + 1);
    if (p1 == std::string::npos || p2 == std::string::npos || !bank.addFile(a.substr(0, p1), std::atoi(a.substr(p1 + 1, p2 - p1 - 1).c_str()), std::atoi(a.substr(p2 + 1).c_str()))) { std::fprintf(stderr, "bad sample: %s\n", argv[i]); return 1; }
  }
  bank.finalize(sr);
  std::printf("zones: %zu, memory: %.1f MB\n", bank.zoneCount(), bank.memoryBytes() / 1048576.0);
  pf::PianoEngine piano; piano.init(sr, std::move(bank));
  const int total = sr * 14, blk = 512; std::vector<float> L((size_t)total), R((size_t)total);
  int t = 0; auto at = [&](double s) { return (int)(s * sr); };
  for (int n : {48, 55, 60, 64, 67}) piano.noteOn(n, 0.75f);
  piano.sustain(true);
  for (; t < total; t += blk) {
    if (t >= at(3.0) && t < at(3.0) + blk) piano.noteOff(48), piano.noteOff(55), piano.noteOff(60), piano.noteOff(64), piano.noteOff(67);
    for (int k = 0; k < 8; ++k) if (t >= at(4.0 + 0.25 * k) && t < at(4.0 + 0.25 * k) + blk) piano.noteOn(60 + k * 2, 0.4f + 0.07f * k);
    if (t >= at(9.0) && t < at(9.0) + blk) piano.sustain(false);
    piano.process(&L[(size_t)t], &R[(size_t)t], std::min(blk, total - t));
  }
  std::vector<float> inter((size_t)total * 2); for (int i = 0; i < total; ++i) { inter[(size_t)i * 2] = L[(size_t)i]; inter[(size_t)i * 2 + 1] = R[(size_t)i]; }
  return pf::writeWav16(argv[1], inter.data(), (size_t)total, 2, sr) ? 0 : 1;
}
#endif
