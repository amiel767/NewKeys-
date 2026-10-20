// AetherFX registry: ids, parameter descriptors (units, ranges) and presets. Values mirror the UI mock-up.
#pragma once
#include <cmath>
#include <vector>

namespace aetherfx {

enum FxId { FX_SPACE = 0, FX_ECHO = 1, FX_TONE = 2, FX_GRIP = 3, FX_CEILING = 4,
  FX_PIANO = 5, FX_ORGAN = 6, FX_SAX = 7, FX_STRINGS = 8, FX_GUITAR = 9, FX_AMP = 10, FX_BASS = 11, FX_BRASS = 12, FX_VOICES = 13, FX_SYNTH = 14, FX_COUNT = 15 };
// Effects live in the 6 track slots (8 on the master). Shapers modify the track's soundfont. Sources replace the soundfont. One instrument (shaper or source) per track; none on the master.
enum class Kind { Effect, Shaper, Source };
inline Kind kindOf(int id) { return (id == FX_ORGAN || id == FX_SYNTH) ? Kind::Source : (id == FX_PIANO || id == FX_SAX || id == FX_STRINGS || id == FX_GUITAR || id == FX_BRASS) ? Kind::Shaper : Kind::Effect; }
enum class Curve { Linear, FreqLog, RatioStep };

struct ParamDesc { const char* name; float min, max; const char* unit; int decimals; float def; Curve curve; bool ui = false; };  // ui = visual only, not sent to the audio engine
struct Preset { const char* name; std::vector<float> v; };
struct EffectInfo { const char* name; const char* type; std::vector<ParamDesc> params; std::vector<Preset> presets; };

constexpr float kRatios[6] = {1.5f, 2.f, 3.f, 4.f, 8.f, 20.f};

inline float toReal(const ParamDesc& d, float norm) {
  switch (d.curve) {
    case Curve::FreqLog: return 20.f * std::pow(1000.f, norm);
    case Curve::RatioStep: return kRatios[static_cast<int>(std::lround(norm * 5.f))];
    default: return d.min + (d.max - d.min) * norm;
  }
}

inline const EffectInfo& info(int id) {
  static const std::vector<EffectInfo> all = [] {
    std::vector<EffectInfo> v(FX_COUNT);
    using C = Curve;
    v[FX_SPACE] = {"Aether Space", "Reverb",
      {{"Size", 0, 100, "%", 0, .62f, C::Linear}, {"Decay", .4f, 8, "s", 1, .7f, C::Linear}, {"Pre-delay", 0, 120, "ms", 0, .2f, C::Linear},
       {"Damp", 1, 12, "kHz", 1, .45f, C::Linear}, {"Width", 0, 100, "%", 0, .8f, C::Linear}, {"Mix", 0, 100, "%", 0, .35f, C::Linear},
       {"Diffuse", 0, 100, "%", 0, .5f, C::Linear}},
      {{"Hall", {.62f, .7f, .2f, .45f, .8f, .35f, .5f}}, {"Plate", {.4f, .5f, .1f, .7f, .9f, .3f, .7f}}, {"Room", {.25f, .3f, .05f, .5f, .6f, .25f, .3f}},
       {"Cathedral", {.95f, .95f, .35f, .3f, 1, .45f, .8f}}, {"Shimmer", {.7f, .85f, .25f, .2f, 1, .5f, .9f}}, {"Chamber", {.35f, .4f, .08f, .55f, .7f, .28f, .45f}},
       {"Bloom", {.8f, .8f, .3f, .35f, .95f, .42f, 1}}, {"Infinite", {1, 1, .4f, .25f, 1, .6f, .95f}}}};
    const float dt[8] = {.15f, .9f, .3f, .7f, .45f, .5f, .6f, .3f};
    std::vector<ParamDesc> ep;
    static const char* tn[4] = {"Time 1", "Time 2", "Time 3", "Time 4"}; static const char* ln[4] = {"Level 1", "Level 2", "Level 3", "Level 4"};
    for (int k = 0; k < 4; ++k) { ep.push_back({tn[k], 10, 1000, "ms", 0, dt[2 * k], C::Linear}); ep.push_back({ln[k], 0, 100, "%", 0, dt[2 * k + 1], C::Linear}); }
    ep.push_back({"Feedback", 0, 95, "%", 0, .42f, C::Linear}); ep.push_back({"Tone", 1, 12, "kHz", 1, .6f, C::Linear});
    ep.push_back({"Wow", 0, 100, "%", 0, .2f, C::Linear}); ep.push_back({"Mix", 0, 100, "%", 0, .28f, C::Linear});
    auto E = [](std::vector<float> a, float f, float t, float w, float m) { a.insert(a.end(), {f, t, w, m}); return a; };
    v[FX_ECHO] = {"Aether Echo", "Delay", ep,
      {{"Dotted", E({.15f, .9f, .3f, .7f, .45f, .5f, .6f, .3f}, .42f, .6f, .2f, .28f)}, {"Tape", E({.12f, .9f, .26f, .8f, .42f, .65f, .6f, .5f}, .6f, .35f, .6f, .3f)},
       {"Ping-pong", E({.1f, .9f, .35f, .8f, .55f, .6f, .8f, .4f}, .5f, .7f, .1f, .32f)}, {"Slap", E({.05f, .9f, .1f, .2f, .15f, .1f, .2f, .05f}, .15f, .8f, 0, .2f)},
       {"Ambient", E({.2f, .7f, .4f, .6f, .62f, .5f, .85f, .4f}, .7f, .45f, .35f, .4f)}, {"Dub", E({.3f, .9f, .5f, .75f, .7f, .55f, .9f, .4f}, .78f, .3f, .5f, .35f)},
       {"Doubler", E({.02f, .8f, .04f, .7f, .06f, .5f, .08f, .3f}, .1f, .9f, .1f, .3f)}, {"Pulse", E({.1f, .9f, .2f, .9f, .3f, .9f, .4f, .9f}, .55f, .7f, 0, .3f)}}};
    auto Q = [](float l, float m, float h, float mf = .5f, float q = .4f, float o = .5f) { return std::vector<float>{.15f, l, mf, m, q, .85f, h, o}; };
    v[FX_TONE] = {"Prism Tone", "Equalizer",
      {{"Low freq", 20, 20000, "Hz", 0, .15f, C::FreqLog}, {"Low", -12, 12, "dB", 1, .62f, C::Linear}, {"Mid freq", 20, 20000, "Hz", 0, .5f, C::FreqLog},
       {"Mid", -12, 12, "dB", 1, .45f, C::Linear}, {"Mid Q", .3f, 4, "", 1, .4f, C::Linear}, {"High freq", 20, 20000, "Hz", 0, .85f, C::FreqLog},
       {"High", -12, 12, "dB", 1, .7f, C::Linear}, {"Output", -12, 12, "dB", 1, .5f, C::Linear}},
      {{"Air", Q(.5f, .45f, .85f, .5f, .3f)}, {"Warm", Q(.72f, .55f, .35f)}, {"Vocal", Q(.4f, .62f, .62f, .58f, .5f)}, {"Presence", Q(.45f, .5f, .72f, .62f, .6f)},
       {"Telephone", Q(.25f, .78f, .3f, .52f, .9f)}, {"Smile", Q(.78f, .4f, .78f, .45f, .3f)}, {"Flat", Q(.5f, .5f, .5f)}, {"Black Box", Q(.62f, .46f, .7f, .4f, .45f, .52f)}}};
    v[FX_GRIP] = {"Iron Grip", "Compressor",
      {{"Threshold", -60, 0, "dB", 0, .48f, C::Linear}, {"Ratio", 1, 20, ":1", 1, .35f, C::RatioStep}, {"Attack", .1f, 100, "ms", 1, .25f, C::Linear},
       {"Release", 10, 1000, "ms", 0, .52f, C::Linear}, {"Knee", 0, 24, "dB", 0, .4f, C::Linear}, {"Makeup", 0, 24, "dB", 1, .4f, C::Linear}},
      {{"Glue", {.55f, .2f, .35f, .6f, .6f, .3f}}, {"Gentle", {.4f, 0, .25f, .5f, .7f, .2f}}, {"Punch", {.5f, .6f, .6f, .3f, .3f, .5f}}, {"Limit", {.7f, 1, .05f, .2f, .1f, .6f}},
       {"Vocal Leveler", {.45f, .4f, .3f, .55f, .8f, .35f}}, {"Drum Bus", {.5f, .4f, .2f, .25f, .3f, .45f}}, {"Parallel", {.6f, .8f, .1f, .4f, .2f, .5f}}}};
    v[FX_CEILING] = {"Solar Ceiling", "Maximizer",
      {{"Amount", 0, 100, "%", 0, .5f, C::Linear}, {"Ceiling", -6, 0, "dB", 1, .92f, C::Linear}, {"Release", 10, 500, "ms", 0, .4f, C::Linear},
       {"Width", 0, 100, "%", 0, .3f, C::Linear}, {"Mix", 0, 100, "%", 0, 1, C::Linear}, {"Output", -12, 12, "dB", 1, .5f, C::Linear}},
      {{"Transparent", {.3f, .95f, .6f, .2f, 1, .5f}}, {"Loud", {.75f, .9f, .35f, .4f, 1, .55f}}, {"Punch", {.6f, .9f, .2f, .2f, .9f, .55f}}, {"Wide", {.5f, .92f, .5f, .8f, 1, .5f}},
       {"Master Glue", {.4f, .93f, .55f, .5f, 1, .5f}}, {"Warm Tape", {.55f, .9f, .45f, .3f, .85f, .52f}}, {"Radio", {.85f, .85f, .2f, .1f, 1, .58f}}}};
    v[5] = {"Aether Piano", "Instrument",
      {{"Felt", 0.0f, 100.0f, " %", 0, 0.8f, C::Linear, false},
       {"Lid", 0.0f, 100.0f, " %", 0, 0.35f, C::Linear, false},
       {"Pedal", 0.0f, 100.0f, " %", 0, 0.55f, C::Linear, false},
       {"Room", 0.0f, 100.0f, " %", 0, 0.3f, C::Linear, false},
       {"Width", 0.0f, 100.0f, " %", 0, 0.7f, C::Linear, false},
       {"Tone", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Body", 0.0f, 100.0f, " %", 0, 0.6f, C::Linear, false},
       {"Space", 0.0f, 100.0f, " %", 0, 0.25f, C::Linear, false},
       {"Level", -24.0f, 6.0f, " dB", 1, 0.8f, C::Linear, false}},
      {{"Felt", {0.8f, 0.35f, 0.55f, 0.3f, 0.7f, 0.4f, 0.6f, 0.25f, 0.8f}},
       {"Studio", {0.4f, 0.6f, 0.35f, 0.15f, 0.6f, 0.55f, 0.4f, 0.1f, 0.8f}},
       {"Upright", {0.5f, 0.4f, 0.4f, 0.2f, 0.4f, 0.45f, 0.7f, 0.2f, 0.8f}},
       {"Concert", {0.55f, 0.7f, 0.7f, 0.6f, 0.9f, 0.5f, 0.5f, 0.6f, 0.8f}},
       {"Mellow", {0.9f, 0.2f, 0.5f, 0.3f, 0.7f, 0.3f, 0.65f, 0.3f, 0.8f}},
       {"Bright", {0.2f, 0.85f, 0.45f, 0.2f, 0.6f, 0.75f, 0.4f, 0.15f, 0.8f}}}};
    v[6] = {"Aether Organ", "Instrument",
      {{"Upper 16'", 0.0f, 8.0f, "", 0, 1.0f, C::Linear, false},
       {"Upper 5⅓'", 0.0f, 8.0f, "", 0, 1.0f, C::Linear, false},
       {"Upper 8'", 0.0f, 8.0f, "", 0, 1.0f, C::Linear, false},
       {"Upper 4'", 0.0f, 8.0f, "", 0, 1.0f, C::Linear, false},
       {"Upper 2⅔'", 0.0f, 8.0f, "", 0, 0.0f, C::Linear, false},
       {"Upper 2'", 0.0f, 8.0f, "", 0, 0.0f, C::Linear, false},
       {"Upper 1⅗'", 0.0f, 8.0f, "", 0, 0.0f, C::Linear, false},
       {"Upper 1⅓'", 0.0f, 8.0f, "", 0, 0.0f, C::Linear, false},
       {"Upper 1'", 0.0f, 8.0f, "", 0, 0.0f, C::Linear, false},
       {"Lower 16'", 0.0f, 8.0f, "", 0, 1.0f, C::Linear, false},
       {"Lower 5⅓'", 0.0f, 8.0f, "", 0, 1.0f, C::Linear, false},
       {"Lower 8'", 0.0f, 8.0f, "", 0, 1.0f, C::Linear, false},
       {"Lower 4'", 0.0f, 8.0f, "", 0, 0.0f, C::Linear, false},
       {"Lower 2⅔'", 0.0f, 8.0f, "", 0, 0.0f, C::Linear, false},
       {"Lower 2'", 0.0f, 8.0f, "", 0, 0.0f, C::Linear, false},
       {"Lower 1⅗'", 0.0f, 8.0f, "", 0, 0.0f, C::Linear, false},
       {"Lower 1⅓'", 0.0f, 8.0f, "", 0, 0.0f, C::Linear, false},
       {"Lower 1'", 0.0f, 8.0f, "", 0, 0.0f, C::Linear, false},
       {"Perc on", 0.0f, 1.0f, "", 0, 0.0f, C::Linear, false},
       {"Perc soft", 0.0f, 1.0f, "", 0, 0.0f, C::Linear, false},
       {"Perc fast", 0.0f, 1.0f, "", 0, 0.0f, C::Linear, false},
       {"Perc 3rd", 0.0f, 1.0f, "", 0, 0.0f, C::Linear, false},
       {"Vibrato", 0.0f, 5.0f, "", 0, 0.6f, C::Linear, false},
       {"Vib upper", 0.0f, 1.0f, "", 0, 1.0f, C::Linear, false},
       {"Vib lower", 0.0f, 1.0f, "", 0, 1.0f, C::Linear, false},
       {"Rotary", 0.0f, 2.0f, "", 0, 1.0f, C::Linear, false},
       {"Click", 0.0f, 100.0f, " %", 0, 0.3f, C::Linear, false},
       {"Tone", 0.0f, 100.0f, " %", 0, 0.55f, C::Linear, false},
       {"Drive", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Space", 0.0f, 100.0f, " %", 0, 0.25f, C::Linear, false},
       {"Level", -24.0f, 6.0f, " dB", 1, 0.8f, C::Linear, false}},
      {{"Gospel", {1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.6f, 1.0f, 1.0f, 1.0f, 0.3f, 0.55f, 0.4f, 0.25f, 0.8f}},
       {"Full", {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.5f, 0.3f, 0.5f, 0.2f, 0.2f, 0.8f}},
       {"Jazz", {1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.2f, 1.0f, 0.0f, 1.0f, 0.35f, 0.5f, 0.3f, 0.2f, 0.8f}},
       {"Rock", {1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.4f, 0.6f, 0.7f, 0.15f, 0.8f}},
       {"Balanced", {0.75f, 1.0f, 1.0f, 0.75f, 0.5f, 0.625f, 0.375f, 0.25f, 0.25f, 0.0f, 0.0f, 1.0f, 0.625f, 0.0f, 0.375f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.4f, 1.0f, 1.0f, 0.5f, 0.3f, 0.5f, 0.3f, 0.25f, 0.8f}},
       {"Whisper", {0.0f, 0.0f, 0.5f, 0.0f, 0.25f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.625f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.5f, 0.2f, 0.4f, 0.1f, 0.35f, 0.8f}}}};
    v[7] = {"Aether Sax", "Instrument",
      {{"Breath", 0.0f, 100.0f, " %", 0, 0.55f, C::Linear, false},
       {"Reed", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Growl", 0.0f, 100.0f, " %", 0, 0.15f, C::Linear, false},
       {"Vibrato", 0.0f, 100.0f, " %", 0, 0.35f, C::Linear, false},
       {"Rate", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Tone", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Body", 0.0f, 100.0f, " %", 0, 0.6f, C::Linear, false},
       {"Space", 0.0f, 100.0f, " %", 0, 0.3f, C::Linear, false},
       {"Level", -24.0f, 6.0f, " dB", 1, 0.8f, C::Linear, false}},
      {{"Smooth", {0.55f, 0.4f, 0.1f, 0.3f, 0.35f, 0.45f, 0.65f, 0.3f, 0.8f}},
       {"Breathy", {0.85f, 0.3f, 0.05f, 0.4f, 0.4f, 0.5f, 0.5f, 0.35f, 0.8f}},
       {"Growl", {0.7f, 0.7f, 0.8f, 0.25f, 0.45f, 0.6f, 0.6f, 0.2f, 0.8f}},
       {"Club", {0.5f, 0.5f, 0.2f, 0.5f, 0.4f, 0.4f, 0.7f, 0.6f, 0.8f}},
       {"Bright", {0.6f, 0.8f, 0.15f, 0.3f, 0.5f, 0.8f, 0.4f, 0.25f, 0.8f}},
       {"Subtone", {0.45f, 0.2f, 0.1f, 0.2f, 0.3f, 0.25f, 0.8f, 0.3f, 0.8f}}}};
    v[8] = {"Aether Strings", "Instrument",
      {{"Pressure", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Speed", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Motion", 0.0f, 100.0f, " %", 0, 0.35f, C::Linear, false},
       {"Morph", 0.0f, 100.0f, " %", 0, 0.1f, C::Linear, false},
       {"Time", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Color", 0.0f, 100.0f, " %", 0, 0.1f, C::Linear, true},
       {"Tone", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Body", 0.0f, 100.0f, " %", 0, 0.6f, C::Linear, false},
       {"Space", 0.0f, 100.0f, " %", 0, 0.3f, C::Linear, false},
       {"Level", -24.0f, 6.0f, " dB", 1, 0.8f, C::Linear, false}},
      {{"Solo", {0.55f, 0.5f, 0.5f, 0.05f, 0.5f, 0.1f, 0.5f, 0.55f, 0.25f, 0.8f}},
       {"Chamber", {0.5f, 0.45f, 0.4f, 0.35f, 0.45f, 0.55f, 0.45f, 0.6f, 0.35f, 0.8f}},
       {"Orchestra", {0.45f, 0.4f, 0.3f, 0.9f, 0.6f, 0.75f, 0.5f, 0.6f, 0.55f, 0.8f}},
       {"Cinematic", {0.6f, 0.3f, 0.25f, 1.0f, 0.75f, 0.92f, 0.35f, 0.7f, 0.8f, 0.8f}},
       {"Warm", {0.4f, 0.5f, 0.45f, 0.5f, 0.4f, 0.02f, 0.25f, 0.75f, 0.4f, 0.8f}},
       {"Bright", {0.65f, 0.65f, 0.3f, 0.4f, 0.3f, 0.5f, 0.8f, 0.4f, 0.3f, 0.8f}}}};
    v[9] = {"Aether Guitar", "Instrument",
      {{"Body", 0.0f, 3.0f, "", 0, 0.0f, C::Linear, false},
       {"Pick", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Strum", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Mute", 0.0f, 100.0f, " %", 0, 0.1f, C::Linear, false},
       {"Bend", 0.0f, 100.0f, " %", 0, 0.2f, C::Linear, false},
       {"Pickup", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Finish", 0.0f, 3.0f, "", 0, 0.33f, C::Linear, true},
       {"Tone", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Resonance", 0.0f, 100.0f, " %", 0, 0.7f, C::Linear, false},
       {"Space", 0.0f, 100.0f, " %", 0, 0.25f, C::Linear, false},
       {"Level", -24.0f, 6.0f, " dB", 1, 0.8f, C::Linear, false}},
      {{"Acoustic", {0.0f, 0.45f, 0.4f, 0.1f, 0.0f, 0.5f, 0.33f, 0.5f, 0.7f, 0.25f, 0.8f}},
       {"Nylon", {0.33f, 0.25f, 0.3f, 0.15f, 0.0f, 0.45f, 0.0f, 0.4f, 0.75f, 0.3f, 0.8f}},
       {"Clean", {0.67f, 0.55f, 0.45f, 0.1f, 0.1f, 0.5f, 0.33f, 0.55f, 0.4f, 0.3f, 0.8f}},
       {"Crunch", {1.0f, 0.65f, 0.45f, 0.2f, 0.2f, 0.7f, 0.67f, 0.6f, 0.35f, 0.2f, 0.8f}},
       {"Lead", {0.67f, 0.75f, 0.2f, 0.0f, 0.6f, 0.9f, 1.0f, 0.7f, 0.3f, 0.35f, 0.8f}},
       {"Ambient", {0.0f, 0.4f, 0.6f, 0.0f, 0.1f, 0.5f, 0.0f, 0.35f, 0.5f, 0.8f, 0.8f}}}};
    v[10] = {"Aether Amp", "Amp",
      {{"Gain", 0.0f, 100.0f, " %", 0, 0.3f, C::Linear, false},
       {"Bass", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Mid", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Treble", 0.0f, 100.0f, " %", 0, 0.6f, C::Linear, false},
       {"Presence", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Master", 0.0f, 100.0f, " %", 0, 0.6f, C::Linear, false},
       {"Channel", 0.0f, 2.0f, "", 0, 0.0f, C::Linear, false},
       {"Mic pos", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Mic dist", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Boost", 0.0f, 1.0f, "", 0, 0.0f, C::Linear, false},
       {"Tremolo", 0.0f, 1.0f, "", 0, 0.0f, C::Linear, false},
       {"Level", -24.0f, 6.0f, " dB", 1, 0.8f, C::Linear, false}},
      {{"Clean", {0.3f, 0.5f, 0.5f, 0.6f, 0.5f, 0.6f, 0.0f, 0.4f, 0.4f, 0.0f, 0.0f, 0.8f}},
       {"Crunch", {0.6f, 0.55f, 0.6f, 0.55f, 0.55f, 0.55f, 0.5f, 0.5f, 0.4f, 0.0f, 0.0f, 0.8f}},
       {"Lead", {0.8f, 0.5f, 0.65f, 0.6f, 0.6f, 0.5f, 1.0f, 0.55f, 0.35f, 1.0f, 0.0f, 0.8f}},
       {"Blues", {0.5f, 0.6f, 0.55f, 0.5f, 0.45f, 0.55f, 0.5f, 0.4f, 0.5f, 0.0f, 1.0f, 0.8f}},
       {"Jazz", {0.2f, 0.65f, 0.4f, 0.4f, 0.3f, 0.6f, 0.0f, 0.3f, 0.6f, 0.0f, 0.0f, 0.8f}},
       {"Metal", {0.95f, 0.6f, 0.4f, 0.7f, 0.7f, 0.5f, 1.0f, 0.6f, 0.3f, 1.0f, 0.0f, 0.8f}}}};
    v[11] = {"Aether Bass", "Amp",
      {{"Drive", 0.0f, 100.0f, " %", 0, 0.25f, C::Linear, false},
       {"Comp", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Blend", 0.0f, 100.0f, " %", 0, 0.6f, C::Linear, false},
       {"63 Hz", 0.0f, 100.0f, " %", 0, 0.55f, C::Linear, false},
       {"250 Hz", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"1 kHz", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"3 kHz", 0.0f, 100.0f, " %", 0, 0.45f, C::Linear, false},
       {"8 kHz", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Cab", 0.0f, 2.0f, "", 0, 0.5f, C::Linear, false},
       {"Level", -24.0f, 6.0f, " dB", 1, 0.8f, C::Linear, false}},
      {{"Motown", {0.15f, 0.5f, 0.3f, 0.6f, 0.45f, 0.4f, 0.3f, 0.25f, 0.5f, 0.8f}},
       {"Rock", {0.5f, 0.55f, 0.7f, 0.5f, 0.5f, 0.65f, 0.6f, 0.5f, 1.0f, 0.8f}},
       {"Slap", {0.35f, 0.6f, 0.8f, 0.7f, 0.3f, 0.45f, 0.7f, 0.75f, 0.5f, 0.8f}},
       {"Fuzz", {0.9f, 0.4f, 0.9f, 0.5f, 0.5f, 0.7f, 0.65f, 0.4f, 1.0f, 0.8f}},
       {"Dub", {0.2f, 0.7f, 0.3f, 0.9f, 0.4f, 0.2f, 0.2f, 0.15f, 0.0f, 0.8f}},
       {"Clean DI", {0.0f, 0.3f, 0.0f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.8f}}}};
    v[12] = {"Aether Brass", "Instrument",
      {{"Lips", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Bite", 0.0f, 100.0f, " %", 0, 0.45f, C::Linear, false},
       {"Vibrato", 0.0f, 100.0f, " %", 0, 0.3f, C::Linear, false},
       {"Section", 0.0f, 4.0f, "", 0, 0.25f, C::Linear, false},
       {"Mute", 0.0f, 4.0f, "", 0, 0.0f, C::Linear, false},
       {"Tone", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Body", 0.0f, 100.0f, " %", 0, 0.6f, C::Linear, false},
       {"Space", 0.0f, 100.0f, " %", 0, 0.3f, C::Linear, false},
       {"Level", -24.0f, 6.0f, " dB", 1, 0.8f, C::Linear, false}},
      {{"Open", {0.5f, 0.45f, 0.3f, 0.25f, 0.0f, 0.5f, 0.6f, 0.3f, 0.8f}},
       {"Muted Jazz", {0.4f, 0.3f, 0.4f, 0.0f, 0.75f, 0.35f, 0.7f, 0.4f, 0.8f}},
       {"Section Stab", {0.7f, 0.8f, 0.1f, 1.0f, 0.0f, 0.7f, 0.55f, 0.4f, 0.8f}},
       {"Fanfare", {0.8f, 0.7f, 0.2f, 0.75f, 0.0f, 0.8f, 0.5f, 0.55f, 0.8f}},
       {"Soft", {0.3f, 0.2f, 0.45f, 0.25f, 0.5f, 0.3f, 0.7f, 0.35f, 0.8f}},
       {"Harmon", {0.55f, 0.5f, 0.25f, 0.0f, 0.75f, 0.55f, 0.45f, 0.3f, 0.8f}}}};
    v[13] = {"Aether Voices", "Voice",
      {{"Vowel X", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Vowel Y", 0.0f, 100.0f, " %", 0, 0.3f, C::Linear, false},
       {"Breath", 0.0f, 100.0f, " %", 0, 0.3f, C::Linear, false},
       {"Choir", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Vibrato", 0.0f, 100.0f, " %", 0, 0.35f, C::Linear, false},
       {"Tone", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Body", 0.0f, 100.0f, " %", 0, 0.5f, C::Linear, false},
       {"Space", 0.0f, 100.0f, " %", 0, 0.45f, C::Linear, false},
       {"Level", -24.0f, 6.0f, " dB", 1, 0.8f, C::Linear, false}},
      {{"Ah", {0.5f, 0.1f, 0.3f, 0.5f, 0.35f, 0.5f, 0.5f, 0.45f, 0.8f}},
       {"Ooh", {0.9f, 0.9f, 0.2f, 0.6f, 0.3f, 0.35f, 0.5f, 0.5f, 0.8f}},
       {"Eeh", {0.1f, 0.9f, 0.3f, 0.5f, 0.35f, 0.6f, 0.45f, 0.4f, 0.8f}},
       {"Ensemble", {0.5f, 0.5f, 0.25f, 0.9f, 0.4f, 0.5f, 0.55f, 0.6f, 0.8f}},
       {"Whisper", {0.5f, 0.5f, 0.9f, 0.4f, 0.1f, 0.4f, 0.4f, 0.5f, 0.8f}},
       {"Angels", {0.7f, 0.6f, 0.35f, 1.0f, 0.45f, 0.7f, 0.6f, 0.85f, 0.8f}}}};
    v[14] = {"Aether Synth", "Instrument",
      {{"Wave A", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Wave B", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Detune", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Sub", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Noise", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Unison", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Spread", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Cutoff", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Resonance", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Drive", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Env amt", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Filter", 0.0f, 3.0f, "", 0, 0.0f, C::Linear, false},
       {"Attack", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Decay", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Sustain", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Release", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"LFO1 rate", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"LFO1 depth", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"LFO1 shape", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"LFO1 dest", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"LFO2 rate", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"LFO2 depth", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Macro 1", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Macro 2", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Macro 3", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Macro 4", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Mode", 0.0f, 2.0f, "", 0, 0.0f, C::Linear, false},
       {"Glide", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Voices", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Bend", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Tune", 0.0f, 100.0f, " %", 0, 0.4f, C::Linear, false},
       {"Level", -24.0f, 6.0f, " dB", 1, 0.8f, C::Linear, false}},
      {{"Pad", {0.3f, 0.5f, 0.3f, 0.2f, 0.05f, 0.7f, 0.7f, 0.35f, 0.2f, 0.15f, 0.35f, 0.0f, 0.7f, 0.5f, 0.7f, 0.8f, 0.3f, 0.3f, 0.0f, 0.0f, 0.2f, 0.2f, 0.3f, 0.5f, 0.5f, 0.5f, 0.0f, 0.1f, 0.5f, 0.2f, 0.5f, 0.8f}},
       {"Lead", {0.9f, 0.6f, 0.2f, 0.2f, 0.05f, 0.3f, 0.4f, 0.6f, 0.4f, 0.5f, 0.35f, 0.0f, 0.05f, 0.4f, 0.8f, 0.3f, 0.3f, 0.3f, 0.0f, 0.0f, 0.2f, 0.2f, 0.3f, 0.5f, 0.5f, 0.5f, 0.0f, 0.1f, 0.5f, 0.2f, 0.5f, 0.8f}},
       {"Bass", {0.6f, 0.2f, 0.2f, 0.7f, 0.05f, 0.0f, 0.4f, 0.25f, 0.35f, 0.4f, 0.5f, 0.0f, 0.02f, 0.4f, 0.6f, 0.2f, 0.3f, 0.3f, 0.0f, 0.0f, 0.2f, 0.2f, 0.3f, 0.5f, 0.5f, 0.5f, 0.5f, 0.1f, 0.5f, 0.2f, 0.5f, 0.8f}},
       {"Pluck", {0.5f, 0.5f, 0.2f, 0.2f, 0.05f, 0.3f, 0.4f, 0.5f, 0.25f, 0.15f, 0.6f, 0.0f, 0.01f, 0.25f, 0.1f, 0.2f, 0.3f, 0.3f, 0.0f, 0.0f, 0.2f, 0.2f, 0.3f, 0.5f, 0.5f, 0.5f, 0.0f, 0.1f, 0.5f, 0.2f, 0.5f, 0.8f}},
       {"Keys", {0.1f, 0.2f, 0.15f, 0.2f, 0.05f, 0.3f, 0.4f, 0.45f, 0.25f, 0.15f, 0.35f, 0.0f, 0.03f, 0.5f, 0.5f, 0.4f, 0.3f, 0.3f, 0.0f, 0.0f, 0.2f, 0.2f, 0.3f, 0.5f, 0.5f, 0.5f, 0.0f, 0.1f, 0.5f, 0.2f, 0.5f, 0.8f}},
       {"FX", {1.0f, 0.4f, 0.8f, 0.2f, 0.5f, 0.3f, 0.4f, 0.5f, 0.7f, 0.15f, 0.35f, 0.0f, 0.1f, 0.4f, 0.7f, 0.35f, 0.7f, 0.8f, 0.5f, 0.0f, 0.2f, 0.2f, 0.3f, 0.5f, 0.5f, 0.5f, 0.0f, 0.1f, 0.5f, 0.2f, 0.5f, 0.8f}}}};
    return v;
  }();
  return all[id];
}

}  // namespace aetherfx
