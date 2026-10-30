// Demo generator: plays "Happy Birthday to You" on each of the 9 factory pianos,
// back to back, so they can be compared by ear.
//   g++ -O2 -std=c++17 -I app/src/main/cpp demo_piano.cpp -o /tmp/dp
// Writes raw interleaved float32 stereo to stdout.
//
// The melody is "Happy Birthday to You", whose tune has been in the public domain
// since 2016 (the Warner/Chappell copyright claim was settled and the song placed in
// the public domain). In F major: C C D C F E / C C D C G F / C C C' A F G F.
// A held bass root is added under the middle phrase so polyphony, sympathetic
// resonance and the bass register are actually exercised — a bare melody line would
// not show them.
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

// "Happy Birthday to You", F major. Phrase 1 and 2 are the two "to you" lines,
// phrase 3 is "dear [name]" resolving on the tonic.
static const Ev kSong[] = {
    {60, 0.75f, 100, false}, {60, 0.25f, 92, false}, {62, 1.0f, 98, false},
    {60, 1.0f, 96, false},  {65, 1.0f, 100, false}, {64, 2.0f, 100, false},
    {60, 0.75f, 100, false}, {60, 0.25f, 92, false}, {62, 1.0f, 98, false},
    {60, 1.0f, 96, false},  {67, 1.0f, 102, false}, {65, 2.0f, 100, false},
    {41, 4.0f, 74, true},
    {60, 0.75f, 102, false}, {60, 0.25f, 94, false}, {72, 1.0f, 106, false},
    {69, 1.0f, 100, false}, {65, 1.0f, 98, false}, {67, 1.0f, 100, false},
    {65, 2.5f, 98, false},
};
static const int kSongLen = static_cast<int>(sizeof(kSong) / sizeof(kSong[0]));

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

    for (int e = 0; e < kSongLen; ++e) {
        const Ev& ev = kSong[e];
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
    const long tail = static_cast<long>(1.2f * kSr);
    for (long done = 0; done < tail; done += block) {
        int n = static_cast<int>(std::min<long>(block, tail - done));
        std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
        eng.process(io.data(), n);
        out.insert(out.end(), io.begin(), io.begin() + static_cast<size_t>(n) * 2);
    }
}

int main(int argc, char** argv) {
    const char* pack = argc > 1 ? argv[1] : "app/src/main/assets/pianopack/piano.bin";
    const char* outDir = argc > 2 ? argv[2] : "/tmp/demo";

    std::vector<uint8_t> bytes = readFile(pack);

    // The 9 factory pianos, in the order AePresets.piano lists them.
    // Les 9 pianos d'usine, dans l'ordre de AePianoPresets. Extraits de AePianoUi.kt
    // pour que la demo fasse entendre exactement ce que l'app charge.
    static const float kPresets[9][12] = {
        {0.15f, 0.05f, 0.50f, 0.30f, 0.20f, 0.10f, 0.50f, 0.80f, 0.30f, 0.50f, 0.40f, 0.00f},
        {0.55f, 0.45f, 0.50f, 0.65f, 0.60f, 0.35f, 0.60f, 0.78f, 0.45f, 0.55f, 0.30f, 0.12f},
        {0.90f, 0.90f, 0.25f, 0.95f, 0.90f, 0.10f, 0.70f, 0.72f, 0.35f, 0.60f, 0.25f, 0.25f},
        {0.25f, 0.15f, 0.60f, 0.40f, 0.25f, 0.55f, 0.50f, 0.78f, 0.60f, 0.45f, 0.40f, 0.38f},
        {0.10f, 0.00f, 0.80f, 0.20f, 0.05f, 1.00f, 0.75f, 0.72f, 0.70f, 0.40f, 0.45f, 0.50f},
        {0.50f, 0.35f, 0.60f, 0.30f, 0.15f, 0.20f, 0.40f, 0.78f, 0.35f, 0.50f, 0.60f, 0.62f},
        {0.35f, 0.30f, 0.40f, 0.55f, 0.35f, 0.60f, 0.45f, 0.78f, 0.50f, 0.50f, 0.30f, 0.75f},
        {0.70f, 0.70f, 0.30f, 0.50f, 0.45f, 0.20f, 0.90f, 0.74f, 0.35f, 0.60f, 0.70f, 0.88f},
        {0.40f, 0.85f, 0.15f, 0.70f, 0.95f, 0.45f, 0.85f, 0.70f, 0.45f, 0.50f, 0.20f, 1.00f},
    };
    static const char* kNames[9] = {
        "Felt", "Studio", "Bright", "Mellow", "Dark",
        "Upright", "Lady D", "Honky", "Electric",
    };
    // Un fichier par modele : la comparaison se fait modele par modele, pas sur un
    // enchainement ou l'oreille perd la reference.
    static const char* kSlugs[9] = {
        "felt", "studio", "bright", "mellow", "dark",
        "upright", "lady", "honky", "electric",
    };

    PianoEngine eng;
    eng.prepare(kSr, 512);
    if (!eng.loadSampleBank(bytes.data(), bytes.size())) {
        fprintf(stderr, "pack illisible\n");
        return 1;
    }
    for (int k = 0; k < 9; ++k) {
        std::vector<float> take;
        renderPreset(eng, kPresets[k], take);
        char path[512];
        snprintf(path, sizeof path, "%s/take_%s.f32", outDir, kSlugs[k]);
        FILE* f = fopen(path, "wb");
        if (!f) { fprintf(stderr, "ecriture impossible : %s\n", path); return 1; }
        fwrite(take.data(), sizeof(float), take.size(), f);
        fclose(f);
        printf("  %-9s %5.1f s -> %s\n", kNames[k], take.size() / 2.0 / kSr, path);
    }
    return 0;
}
