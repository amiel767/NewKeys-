// Demo generator: plays the "Can You Feel The Love Tonight" chorus hook on each of the
// 9 factory pianos, back to back, so they can be compared by ear.
//   g++ -O2 -std=c++17 -I app/src/main/cpp demo_piano.cpp -o /tmp/dp
// Writes raw interleaved float32 stereo to stdout.
//
// The melody is the published letter notation for the chorus, one octave marker as
// written ("^" = octave up, "#" = sharp): F ^F ^D ^C ^F ^D Bb G / F Bb ^D# ^D ^C.
// A held bass root is added under each phrase so polyphony, sympathetic resonance and
// the bass register are actually exercised — a bare melody line would not show them.
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <vector>
#include <string>

#include "aetherfx/fx_piano.h"

using namespace aetherfx;

static const float kSr = 44100.f;
static const float kBeat = 0.36f;

struct Ev { int midi; float beats; int vel; bool bass; };

// "And can you feel the love tonight" (Bb) then "It is where we are" (Eb).
static const Ev kHook[] = {
    {65, 0.5f, 100, false}, {77, 0.5f, 108, false}, {74, 0.5f, 100, false},
    {72, 1.0f, 104, false}, {77, 0.5f, 104, false}, {74, 0.5f, 96, false},
    {70, 1.0f, 100, false}, {67, 1.5f, 88, false},
    {46, 6.0f, 76, true},
    {65, 0.5f, 96, false}, {70, 0.5f, 100, false}, {75, 1.0f, 104, false},
    {74, 0.5f, 96, false}, {72, 1.5f, 92, false},
    {51, 4.0f, 76, true},
};
static const int kHookLen = static_cast<int>(sizeof(kHook) / sizeof(kHook[0]));
static const float kHookBeats = 10.0f;

static std::vector<uint8_t> readFile(const char* path) {
    FILE* f = fopen(path, "rb");
    if (!f) { fprintf(stderr, "ouverture impossible : %s\n", path); exit(1); }
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    std::vector<uint8_t> b(static_cast<size_t>(n));
    if (fread(b.data(), 1, b.size(), f) != b.size()) { fprintf(stderr, "lecture courte\n"); exit(1); }
    fclose(f);
    return b;
}

static void renderPreset(PianoEngine& eng, const float* params, std::vector<float>& out) {
    for (int i = 0; i < 12; ++i) eng.setNorm(i, params[i]);
    eng.reset();

    const int block = 512;
    std::vector<float> io(static_cast<size_t>(block) * 2);

    // Active voices, each with a frame countdown so the release tail overlaps the next
    // attack the way a real piano's does.
    struct Voice { int midi; long left; };
    std::vector<Voice> voices;
    int cursor = 0;

    for (int e = 0; e < kHookLen; ++e) {
        const Ev& ev = kHook[e];
        eng.noteOn(ev.midi, ev.vel);
        long frames = static_cast<long>(ev.beats * kBeat * kSr);
        voices.push_back({ev.midi, frames});
        cursor = 0;
        while (cursor < frames) {
            int n = static_cast<int>(std::min<long>(block, frames - cursor));
            std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
            eng.process(io.data(), n);
            out.insert(out.end(), io.begin(), io.begin() + static_cast<size_t>(n) * 2);
            cursor += n;
        }
        eng.noteOff(ev.midi);
    }

    // Tail: let the last notes and the modelled soundboard decay out.
    const long tail = static_cast<long>(1.8f * kSr);
    for (long done = 0; done < tail; done += block) {
        int n = static_cast<int>(std::min<long>(block, tail - done));
        std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
        eng.process(io.data(), n);
        out.insert(out.end(), io.begin(), io.begin() + static_cast<size_t>(n) * 2);
    }
}

int main(int argc, char** argv) {
    const char* pack = argc > 1 ? argv[1] : "app/src/main/assets/pianopack/piano.bin";
    const char* namesPath = argc > 2 ? argv[2] : "/tmp/demo/names.txt";

    std::vector<uint8_t> bytes = readFile(pack);

    // The 9 factory pianos, in the order AePresets.piano lists them.
    static const float kPresets[9][12] = {
        {0.15f, 0.22f, 0.62f, 0.45f, 0.55f, 0.25f, 0.40f, 0.60f, 0.50f, 0.55f, 0.20f, 0.80f},
        {0.55f, 0.58f, 0.50f, 0.35f, 0.38f, 0.55f, 0.28f, 0.55f, 0.50f, 0.60f, 0.25f, 0.80f},
        {0.80f, 0.85f, 0.38f, 0.28f, 0.30f, 0.75f, 0.22f, 0.50f, 0.50f, 0.65f, 0.30f, 0.78f},
        {0.22f, 0.30f, 0.70f, 0.55f, 0.60f, 0.30f, 0.42f, 0.62f, 0.50f, 0.50f, 0.20f, 0.80f},
        {0.18f, 0.15f, 0.75f, 0.60f, 0.65f, 0.18f, 0.45f, 0.60f, 0.50f, 0.48f, 0.15f, 0.80f},
        {0.45f, 0.45f, 0.58f, 0.42f, 0.45f, 0.40f, 0.35f, 0.45f, 0.50f, 0.58f, 0.35f, 0.78f},
        {0.62f, 0.70f, 0.52f, 0.38f, 0.42f, 0.65f, 0.32f, 0.62f, 0.50f, 0.62f, 0.28f, 0.80f},
        {0.60f, 0.62f, 0.45f, 0.35f, 0.35f, 0.50f, 0.25f, 0.50f, 0.50f, 0.60f, 0.55f, 0.76f},
        {0.35f, 0.40f, 0.30f, 0.50f, 0.70f, 0.35f, 0.55f, 0.70f, 0.50f, 0.52f, 0.10f, 0.80f},
    };
    static const char* kNames[9] = {
        "Felt", "Studio", "Bright", "Mellow", "Dark",
        "Upright", "Lady D", "Honky", "Electric",
    };

    // Silence between takes, so the boundary is audible rather than guessed.
    const long gap = static_cast<long>(0.9f * kSr);
    std::vector<float> gapBuf(static_cast<size_t>(gap) * 2, 0.f);

    std::vector<float> all;
    PianoEngine eng;
    eng.prepare(kSr, 512);
    if (!eng.loadSampleBank(bytes.data(), bytes.size())) {
        fprintf(stderr, "pack illisible\n");
        return 1;
    }
    for (int k = 0; k < 9; ++k) {
        renderPreset(eng, kPresets[k], all);
        all.insert(all.end(), gapBuf.begin(), gapBuf.end());
    }

    // Names + sample offsets, for the player's track markers.
    long total = static_cast<long>(all.size()) / 2;
    long each = total / 9;
    FILE* f = fopen(namesPath, "w");
    if (f) {
        for (int k = 0; k < 9; ++k)
            fprintf(f, "%s\t%.2f\n", kNames[k], static_cast<double>(each * k) / kSr);
        fclose(f);
    }
    fwrite(all.data(), sizeof(float), all.size(), stdout);

    fprintf(stderr, "%ld frames, %.1f s, %s\n",
            static_cast<long>(all.size()) / 2,
            static_cast<double>(all.size()) / 2 / kSr, namesPath);
    return 0;
}
