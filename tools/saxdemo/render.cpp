// Renders a sax phrase through the REAL engine (aetherfx is header-only) and writes stereo
// float32 WAV. Stereo matters: SpaceReverb builds a true mid/side image (sideG = width*1.4),
// so exporting mono throws away part of the voice.
#include "aetherfx/fx_sax.h"
#include "presets_sax.h"
#include <cstdio>
#include <cstdlib>
#include <cmath>
#include <cstring>
#include <string>
#include <vector>
#include <algorithm>

using namespace aetherfx;
static const int SR = 48000, BLK = 256;

static std::vector<uint8_t> slurp(const std::string& p) {
    FILE* f = fopen(p.c_str(), "rb");
    if (!f) { fprintf(stderr, "cannot open %s\n", p.c_str()); exit(1); }
    fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
    std::vector<uint8_t> v(n);
    if (fread(v.data(), 1, n, f) != (size_t)n) { fprintf(stderr, "short read %s\n", p.c_str()); exit(1); }
    fclose(f);
    return v;
}

static void writeWav(const std::string& p, const std::vector<float>& lr) {
    const uint32_t frames = (uint32_t)(lr.size() / 2);
    const uint32_t dataBytes = frames * 2 * 4;
    FILE* f = fopen(p.c_str(), "wb");
    auto u32 = [&](uint32_t v) { fwrite(&v, 4, 1, f); };
    auto u16 = [&](uint16_t v) { fwrite(&v, 2, 1, f); };
    fwrite("RIFF", 1, 4, f); u32(36 + dataBytes); fwrite("WAVE", 1, 4, f);
    fwrite("fmt ", 1, 4, f); u32(16); u16(3); u16(2); u32(SR); u32(SR * 2 * 4); u16(8); u16(32);
    fwrite("data", 1, 4, f); u32(dataBytes);
    fwrite(lr.data(), 4, lr.size(), f);
    fclose(f);
}

// ---- score: a note plus how it is played ----
struct Note { int midi; float dur; bool slur; int vel; };
struct Score { std::vector<Note> notes; float tail = 1.6f; };

// Renders one preset over one score. `blend` sets the sample/synthesis mix.
static void render(const std::string& bank, const std::vector<uint8_t>& bytes, const float* P,
                   const Score& sc, float blend, const std::string& out) {
    SaxEngine s;
    s.prepare(SR, BLK);
    if (!s.loadSamples(bytes.data(), bytes.size())) { fprintf(stderr, "bank load failed\n"); exit(1); }
    for (int i = 0; i < 23; ++i) s.setNorm(i, P[i]);
    // Negative blend means "use the preset's own value" (parameter 22, the Blend knob).
    if (blend >= 0.f) s.setSampleMix(blend);

    std::vector<float> b(BLK * 2), outv;
    auto pump = [&](float seconds) {
        int blocks = (int)std::ceil(seconds * SR / BLK);
        for (int k = 0; k < blocks; ++k) {
            std::fill(b.begin(), b.end(), 0.f);
            s.process(b.data(), BLK);
            for (int j = 0; j < BLK; ++j) { outv.push_back(b[2 * j]); outv.push_back(b[2 * j + 1]); }
        }
    };
    for (size_t i = 0; i < sc.notes.size(); ++i) {
        const Note& n = sc.notes[i];
        s.noteOn(n.midi, n.vel);
        pump(n.dur);
        bool last = (i + 1 == sc.notes.size());
        if (!n.slur || last) {
            s.noteOff(n.midi);
            if (n.slur || last) pump(0.04f);   // let the release start before the next note
        }
    }
    pump(sc.tail);
    writeWav(out, outv);
    double peak = 0, sum = 0;
    for (float v : outv) { peak = std::max(peak, (double)std::fabs(v)); sum += v * v; }
    printf("  %-28s frames %7zu  crete %.3f  rms %.4f\n", out.c_str(), outv.size() / 2, peak,
           std::sqrt(sum / outv.size()));
}

// ---- demo riffs ----
// Idiomatic jazz/blues material rather than scales: each one is a phrase a player would
// actually use, so the demo shows the engine on musical content. `slur` = legato into the
// next note (no re-articulation). Durations in seconds.
static Score bluesRiff() {   // classic minor-blues lick, ends on a bend-ready long note
    return {{ {57,0.18f,true,100},{60,0.16f,true,102},{62,0.16f,true,104},{63,0.20f,false,106},
              {62,0.14f,true,100},{60,0.14f,true,98},{57,0.34f,false,104},
              {60,0.16f,true,102},{63,0.16f,true,108},{65,0.16f,true,110},{67,0.42f,false,112},
              {65,0.14f,true,102},{63,0.14f,true,100},{60,0.72f,false,100} }, 1.4f};
}
static Score bebopRiff() {   // fast bebop enclosure run
    return {{ {64,0.13f,true,104},{66,0.12f,true,106},{67,0.12f,true,108},{69,0.12f,true,110},
              {71,0.13f,true,112},{72,0.12f,true,110},{74,0.12f,true,112},{76,0.13f,true,114},
              {74,0.11f,true,106},{72,0.11f,true,104},{71,0.11f,true,106},{69,0.40f,false,108},
              {67,0.12f,true,102},{66,0.12f,true,100},{64,0.56f,false,102} }, 1.2f};
}
static Score balladRiff() {  // slow, breathy, long tones — shows the sustained voice
    return {{ {62,0.55f,true,92},{65,0.55f,true,96},{69,0.85f,false,100},
              {67,0.45f,true,94},{65,0.45f,true,92},{62,1.05f,false,96} }, 1.8f};
}
static Score bendRiff() {    // ends on a held note: the harness bends it during the render
    return {{ {67,0.30f,true,102},{70,0.30f,true,106},{72,0.30f,false,108},{72,1.20f,false,110} }, 1.6f};
}

// The preset's first parameter picks the sax type, and each type has its own recorded bank
// (same rule as AeBanks.fileFor on the Kotlin side). Rendering a tenor preset on the alto
// bank would show the wrong instrument.
static const char* bankFileFor(float type) {
    if (type < 0.165f) return "saxpack_sop.bin";
    if (type < 0.5f)   return "saxpack_alt.bin";
    if (type < 0.835f) return "saxpack_ten.bin";
    return "saxpack_bar.bin";
}

int main(int argc, char** argv) {
    std::string packDir = "/workspace/project/NewKeys-/app/src/main/assets/saxpack";
    std::string outDir = "/tmp/saxdemo";
    if (argc > 1) packDir = argv[1];
    if (argc > 2) outDir = argv[2];
    printf("score                            frames     crete    rms   bank\n");
    struct Job { const char* preset; Score sc; const char* tag; };
    std::vector<Job> jobs = {
        {"Jazz Ballad Alto", balladRiff(), "ballad"},
        {"Bebop Alto", bebopRiff(), "bebop"},
        {"Growl Rock Tenor", bluesRiff(), "blues"},
    };
    for (auto& j : jobs) {
        const SaxPreset* pr = nullptr;
        for (int i = 0; i < SAX_PRESET_COUNT; ++i)
            if (std::string(SAX_PRESETS[i].name) == j.preset) pr = &SAX_PRESETS[i];
        if (!pr) { fprintf(stderr, "preset not found: %s\n", j.preset); return 1; }
        std::string bank = packDir + "/" + bankFileFor(pr->v[0]);
        auto bytes = slurp(bank);
        printf("  %-28s %-9s", j.preset, j.tag);
        render(bank, bytes, pr->v, j.sc, -1.0f, outDir + "/" + j.tag + "_" + pr->name + ".wav");
    }
    return 0;
}
