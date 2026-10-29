// AetherFX core: types and DSP building blocks. Header-only, C++17, no allocation in process().
#pragma once
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <memory>
#include <vector>
#if defined(__x86_64__) || defined(__i386__)
#include <xmmintrin.h>
#endif

namespace aetherfx {

constexpr int kMaxParams = 34;
constexpr double kPi = 3.14159265358979323846;

inline float clamp01(float v) { return v < 0.f ? 0.f : (v > 1.f ? 1.f : v); }
inline float dbToLin(float db) { return std::pow(10.0f, db * 0.05f); }
inline float linToDb(float x) { return 20.0f * std::log10(std::max(x, 1e-9f)); }

// Flush-to-zero / denormals-are-zero for the duration of a scope (audio thread).
class NoDenormals {
 public:
  NoDenormals() {
#if defined(__x86_64__) || defined(__i386__)
    old_ = _mm_getcsr();
    _mm_setcsr(static_cast<unsigned>(old_) | 0x8040u);
#elif defined(__aarch64__)
    uint64_t r;
    asm volatile("mrs %0, fpcr" : "=r"(r));
    old_ = r;
    asm volatile("msr fpcr, %0" ::"r"(r | (1ull << 24)));
#endif
  }
  ~NoDenormals() {
#if defined(__x86_64__) || defined(__i386__)
    _mm_setcsr(static_cast<unsigned>(old_));
#elif defined(__aarch64__)
    asm volatile("msr fpcr, %0" ::"r"(old_));
#endif
  }
  NoDenormals(const NoDenormals&) = delete;
  NoDenormals& operator=(const NoDenormals&) = delete;

 private:
  uint64_t old_ = 0;
};

// One-pole smoothing applied once per block (control rate).
struct BlockSmoother {
  float v = 0.f;
  bool init = false;
  float step(float target, float coef) {
    if (!init) { v = target; init = true; }
    v += (target - v) * coef;
    return v;
  }
  void snap(float t) { v = t; init = true; }
};
inline float smoothCoef(int frames, double sr, double tauSec) {
  return 1.0f - std::exp(-static_cast<float>(frames / (sr * tauSec)));
}

// Linear ramp helper for per-sample gains inside a block.
struct Ramp {
  float cur = 0.f;
  bool init = false;
  // returns step to add each sample so that after `frames` samples cur == target
  float begin(float target, int frames) {
    if (!init) { cur = target; init = true; }
    return frames > 0 ? (target - cur) / frames : 0.f;
  }
};

// One-pole low-pass (per channel state held by caller).
struct OnePole {
  float z = 0.f, a = 1.f;
  void setCutoff(double sr, double fc) {
    fc = std::min(fc, sr * 0.45);
    a = 1.0f - std::exp(static_cast<float>(-2.0 * kPi * fc / sr));
  }
  inline float process(float x) { z += a * (x - z); return z; }
  void reset() { z = 0.f; }
};

// RBJ biquad, double precision, transposed direct form II. Two channels share coefficients.
struct Biquad {
  double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
  double z1[2] = {0, 0}, z2[2] = {0, 0};
  void reset() { z1[0] = z1[1] = z2[0] = z2[1] = 0; }
  inline float process(int ch, float x) {
    double y = b0 * x + z1[ch];
    z1[ch] = b1 * x - a1 * y + z2[ch];
    z2[ch] = b2 * x - a2 * y;
    return static_cast<float>(y);
  }
  void norm(double a0, double nb0, double nb1, double nb2, double na1, double na2) {
    b0 = nb0 / a0; b1 = nb1 / a0; b2 = nb2 / a0; a1 = na1 / a0; a2 = na2 / a0;
  }
  void setPeaking(double sr, double f, double q, double gainDb) {
    f = std::min(f, sr * 0.45);
    double A = std::pow(10.0, gainDb / 40.0), w0 = 2 * kPi * f / sr, c = std::cos(w0), al = std::sin(w0) / (2 * q);
    norm(1 + al / A, 1 + al * A, -2 * c, 1 - al * A, -2 * c, 1 - al / A);
  }
  void setLowShelf(double sr, double f, double gainDb) {  // slope S = 1
    f = std::min(f, sr * 0.45);
    double A = std::pow(10.0, gainDb / 40.0), w0 = 2 * kPi * f / sr, c = std::cos(w0), sA = std::sqrt(A);
    double al = std::sin(w0) / 2 * std::sqrt(2.0);
    norm((A + 1) + (A - 1) * c + 2 * sA * al, A * ((A + 1) - (A - 1) * c + 2 * sA * al),
         2 * A * ((A - 1) - (A + 1) * c), A * ((A + 1) - (A - 1) * c - 2 * sA * al),
         -2 * ((A - 1) + (A + 1) * c), (A + 1) + (A - 1) * c - 2 * sA * al);
  }
  void setHighShelf(double sr, double f, double gainDb) {
    f = std::min(f, sr * 0.45);
    double A = std::pow(10.0, gainDb / 40.0), w0 = 2 * kPi * f / sr, c = std::cos(w0), sA = std::sqrt(A);
    double al = std::sin(w0) / 2 * std::sqrt(2.0);
    norm((A + 1) - (A - 1) * c + 2 * sA * al, A * ((A + 1) + (A - 1) * c + 2 * sA * al),
         -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - 2 * sA * al),
         2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - 2 * sA * al);
  }
  void setLowpass(double sr, double f, double q) {
    f = std::min(f, sr * 0.45); double w0 = 2 * kPi * f / sr, c = std::cos(w0), al = std::sin(w0) / (2 * q);
    norm(1 + al, (1 - c) / 2, 1 - c, (1 - c) / 2, -2 * c, 1 - al);
  }
  void setHighpass(double sr, double f, double q) {
    f = std::min(f, sr * 0.45); double w0 = 2 * kPi * f / sr, c = std::cos(w0), al = std::sin(w0) / (2 * q);
    norm(1 + al, (1 + c) / 2, -(1 + c), (1 + c) / 2, -2 * c, 1 - al);
  }
  void setBandpass(double sr, double f, double q) {  // constant 0 dB peak gain
    f = std::min(f, sr * 0.45); double w0 = 2 * kPi * f / sr, c = std::cos(w0), al = std::sin(w0) / (2 * q);
    norm(1 + al, al, 0, -al, -2 * c, 1 - al);
  }
  void setNotch(double sr, double f, double q) {
    f = std::min(f, sr * 0.45); double w0 = 2 * kPi * f / sr, c = std::cos(w0), al = std::sin(w0) / (2 * q);
    norm(1 + al, 1, -2 * c, 1, -2 * c, 1 - al);
  }
  // magnitude response in dB at frequency f (analytic, for UI curves and tests)
  double magDb(double sr, double f) const {
    double w = 2 * kPi * f / sr, c1 = std::cos(w), s1 = std::sin(w), c2 = std::cos(2 * w), s2 = std::sin(2 * w);
    double nr = b0 + b1 * c1 + b2 * c2, ni = -(b1 * s1 + b2 * s2);
    double dr = 1 + a1 * c1 + a2 * c2, di = -(a1 * s1 + a2 * s2);
    return 10.0 * std::log10((nr * nr + ni * ni) / (dr * dr + di * di));
  }
};

// Circular delay line with linear interpolation. d is in samples (>= 1).
struct DelayLine {
  std::vector<float> buf;
  int mask = 0, w = 0;
  void init(int maxSamples) {
    int n = 1;
    while (n < maxSamples + 4) n <<= 1;
    buf.assign(n, 0.f);
    mask = n - 1;
    w = 0;
  }
  void clear() { std::fill(buf.begin(), buf.end(), 0.f); w = 0; }
  inline void write(float x) { buf[w] = x; w = (w + 1) & mask; }
  inline float read(float d) const {
    float pos = static_cast<float>(w) - d;
    int i0 = static_cast<int>(std::floor(pos));
    float fr = pos - static_cast<float>(i0);
    float a = buf[(i0 - 1) & mask], b = buf[i0 & mask];
    return a + (b - a) * fr;
  }
  inline float readInt(int d) const { return buf[(w - d) & mask]; }
};

// Base class of every effect. Parameters are normalised 0..1 (the same values the UI uses).
class Effect {
 public:
  Effect() { for (auto& t : target_) t.store(0.5f, std::memory_order_relaxed); for (auto& m : meters_) m.store(0.f, std::memory_order_relaxed); }
  virtual ~Effect() = default;
  virtual void prepare(double sampleRate, int maxBlock) = 0;
  virtual void reset() = 0;
  virtual void process(float* interleavedStereo, int frames) = 0;
  virtual int latencySamples() const { return 0; }
  virtual int paramCount() const = 0;

  void setNorm(int i, float v) { if (i >= 0 && i < kMaxParams) target_[i].store(clamp01(v), std::memory_order_relaxed); }
  float getNorm(int i) const { return target_[i].load(std::memory_order_relaxed); }
  float meter(int i) const { return meters_[i & 3].load(std::memory_order_relaxed); }

 protected:
  float p(int i) const { return target_[i].load(std::memory_order_relaxed); }
  void setMeter(int i, float v) { meters_[i & 3].store(v, std::memory_order_relaxed); }

 private:
  std::atomic<float> target_[kMaxParams];
  std::atomic<float> meters_[4];
};


// Small deterministic noise source (xorshift32), audio-thread safe.
struct Noise {
  uint32_t s = 0x9E3779B9u;
  inline float next() { s ^= s << 13; s ^= s >> 17; s ^= s << 5; return static_cast<float>(static_cast<int32_t>(s)) * (1.0f / 2147483648.0f); }
};
// Sine table (2048 + 1 entries) for cheap oscillators; phase in [0,1).
struct SineTable {
  float t[2049];
  SineTable() { for (int i = 0; i <= 2048; ++i) t[i] = static_cast<float>(std::sin(2.0 * kPi * i / 2048.0)); }
  inline float at(float ph) const { float x = ph * 2048.0f; int i = static_cast<int>(x); float f = x - i; return t[i] + (t[i + 1] - t[i]) * f; }
};
inline const SineTable& sineTable() { static const SineTable st; return st; }

}  // namespace aetherfx
