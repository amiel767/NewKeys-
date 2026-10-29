// Reader for the compact sax packs built by tools/saxpack/extract.py.
//
// The pack is a flat container: a header, one 24-byte record per region, then a
// 4-bit IMA ADPCM payload. Everything is header-only and allocation-free after
// load(), so it is safe on the audio thread.
//
// A pack holds two velocity layers and roughly one region per semitone, so the
// reader picks the nearest root and transposes by the ratio between the wanted
// frequency and the root's. The loops are edited to a whole number of periods
// and re-aligned at build time (see snap_loop_end in extract.py), so the join is
// already clean; the short crossfade below only guards against the phase drift a
// transposed loop can introduce.
#pragma once
#include <cstdint>
#include <cstring>
#include <cmath>
#include <vector>
#include <algorithm>

namespace aetherfx {

class SaxSampleReader {
 public:
  struct Region {
    int root = 60;
    int layer = 1;             // 0 = soft, 1 = loud
    uint32_t offset = 0, frames = 0, loopStart = 0, loopEnd = 0;
    float tuneCents = 0.f;
  };

  static constexpr int kStep[89] = {
      7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
      50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230,
      253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963,
      1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327,
      3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442,
      11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794,
      32767};
  static constexpr int kIdx[8] = {-1, -1, -1, -1, 2, 4, 6, 8};

  bool load(const uint8_t* bytes, size_t size) {
    clear();
    if (size < 28) return false;
    if (std::memcmp(bytes, "SAXP", 4) != 0) return false;
    uint32_t rate = 0, count = 0, pcmLen = 0, recLen = 0;
    std::memcpy(&rate, bytes + 8, 4);
    std::memcpy(&count, bytes + 12, 4);
    std::memcpy(&pcmLen, bytes + 16, 4);
    std::memcpy(&recLen, bytes + 20, 4);
    if (28 + recLen + pcmLen > size || count == 0 || count > 4096) return false;
    rate_ = static_cast<int>(rate);
    for (uint32_t i = 0; i < count; ++i) {
      const uint8_t* r = bytes + 28 + i * 24;
      Region g;
      g.root = r[0];
      g.layer = r[1];
      std::memcpy(&g.offset, r + 4, 4);
      std::memcpy(&g.frames, r + 8, 4);
      std::memcpy(&g.loopStart, r + 12, 4);
      std::memcpy(&g.loopEnd, r + 16, 4);
      std::memcpy(&g.tuneCents, r + 20, 4);
        if (g.frames < 64 || g.offset + (g.frames + 1) / 2 > pcmLen) continue;
      regions_.push_back(g);
    }
    // Own the payload: the caller's buffer may be freed (an asset stream, a
    // local vector) while a note is still sounding. Holding a pointer into it
    // is a use-after-free, which is exactly how this crashed the first time.
    pcm_.assign(bytes + 28 + recLen, bytes + 28 + recLen + pcmLen);
    return !regions_.empty();
  }

  void clear() { regions_.clear(); pcm_.clear(); rate_ = 22050; }

  bool loaded() const { return !regions_.empty(); }
  int rate() const { return rate_; }
  size_t regionCount() const { return regions_.size(); }

  void noteOn(int note, int velocity) {
    if (regions_.empty()) return;
    // louder half of the velocity range uses the loud layer
    const int want = velocity >= 80 ? 1 : 0;
    const Region* best = nullptr;
    int bestCost = 1 << 30;
    for (const Region& g : regions_) {
      // a region of the wanted layer always beats one of the other layer
      const int cost = std::abs(g.root - note) * 4 + (g.layer == want ? 0 : 1);
      if (cost < bestCost) { bestCost = cost; best = &g; }
    }
    if (!best) return;
    cur_ = best;
    decodeRegion();          // one pass, into the reusable buffer
    const float f = 440.f * std::pow(2.f, (note - 69) / 12.f);
    const float rootF = 440.f * std::pow(2.f, (best->root - 69) / 12.f) * std::pow(2.f, best->tuneCents / 1200.f);
    ratio_ = (rootF > 1e-6f) ? f / rootF : 1.f;
    pos_ = 0.0;
    env_ = 0.f;
    active_ = true;
    releasing_ = false;
    vel_ = velocity / 127.f;
  }

  void noteOff() { releasing_ = true; }

  void allNotesOff() { active_ = false; releasing_ = false; }

  bool active() const { return active_; }

  /** Mixes `n` frames into a mono accumulator at the given gain. */
  void render(float* out, int n, float gain) {
    if (!active_ || !cur_ || buf_.empty() || pcm_.empty()) return;
    const double step = static_cast<double>(ratio_) * static_cast<double>(rate_) / outRate_;
    const double ls = cur_->loopStart, le = cur_->loopEnd;
    const double xf = std::min<double>(64.0, std::max(4.0, (le - ls) * 0.05));
    // Attack 3.5 ms, release ~60 ms. The release used to be 1 ms, which is fine while the
    // synthesis is mixed underneath (it carries its own, slow, release) but not once the
    // samples carry the voice alone: cutting a note in a millisecond is a click, not a release.
    const float att = 0.006f, rel = 3.5e-4f;
    const uint32_t nf = static_cast<uint32_t>(buf_.size());
    for (int i = 0; i < n; ++i) {
      if (releasing_) { env_ -= env_ * rel; if (env_ < 1e-4f) { active_ = false; break; } }
      else if (env_ < 1.f) env_ = std::min(1.f, env_ + att);
      if (pos_ >= nf - 2) { active_ = false; break; }

      float s = interp(pos_);
      // Crossfade the tail of the loop into the *matching phase of the previous loop*, i.e.
      // x[ls-xf .. ls) blended with x[le-xf .. le). Fading into the head x[ls .. ls+xf) instead —
      // which is what this did — makes the last blended frame sit xf frames past loopStart while
      // the wrap restarts at loopStart, so every wrap jumps by whatever the waveform does in xf
      // frames. At 22 kHz with a 64-frame blend that is a 3 ms jump, which is audible as a
      // periodic click: measured at 0.57 against 0.09 for the synthesis, exactly once per loop
      // period. Blending with the earlier phase keeps the join at the size of one normal sample
      // step and, because the loop is a whole number of periods, the two phases are in step.
      if (pos_ >= le - xf && pos_ < le) {
        const float u = static_cast<float>((pos_ - (le - xf)) / xf);
        const double j = static_cast<double>(ls) - xf + (pos_ - (le - xf));
        s = s * (1.f - u) + interp(j >= 0.0 ? j : ls) * u;
      }
      if (pos_ >= le) pos_ += ls - le;      // loop back
      pos_ += step;
      out[i] += s * env_ * vel_ * gain;
    }
  }

 private:
  float interp(double p) const {
    const uint32_t i = static_cast<uint32_t>(p);
    if (i + 1 >= buf_.size()) return 0.f;
    const float fr = static_cast<float>(p - i);
    return buf_[i] * (1.f - fr) + buf_[i + 1] * fr;
  }

  /** Decodes the whole selected region once, on noteOn, off the inner loop.
   *
   * Decoding lazily inside render() would be quadratic: 4 bits per frame means
   * the predictor has to be replayed from the region start on every access.
   * A region is at most ~108k frames, so one pass costs a fraction of a
   * millisecond and the buffer is reused between notes.
   */
  void decodeRegion() {
    const uint32_t nbytes = (cur_->frames + 1) / 2;
    const uint8_t* blob = pcm_.data() + cur_->offset;
    buf_.resize(cur_->frames);
    int pred = 0, index = 0;
    uint32_t f = 0;
    for (uint32_t b = 0; b < nbytes && f < cur_->frames; ++b) {
      const uint8_t byte = blob[b];
      for (int half = 0; half < 2; ++half) {
        if (f >= cur_->frames) break;
        const int code = half ? ((byte >> 4) & 0x0F) : (byte & 0x0F);
        const int st = kStep[index];
        int delta = st >> 3;
        if (code & 4) delta += st;
        if (code & 2) delta += st >> 1;
        if (code & 1) delta += st >> 2;
        pred += (code & 8) ? -delta : delta;
        pred = std::max(-32768, std::min(32767, pred));
        index = std::max(0, std::min(88, index + kIdx[code & 7]));
        buf_[f++] = pred / 32768.f;
      }
    }
  }

 public:
  /** Output rate, set once by the engine before any rendering. */
  void setOutputRate(double sr) { outRate_ = sr; }

 private:
  std::vector<Region> regions_;
  std::vector<float> buf_;      // decoded region, reused across notes
  std::vector<uint8_t> pcm_;    // owned copy of the pack payload
  int rate_ = 22050;
  double outRate_ = 48000.0;
  const Region* cur_ = nullptr;
  double pos_ = 0.0;
  float ratio_ = 1.f, env_ = 0.f, vel_ = 1.f;
  bool active_ = false, releasing_ = false;
};

}  // namespace aetherfx
