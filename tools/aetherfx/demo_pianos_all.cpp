// Demo : le refrain demande, joue sur TOUS les pianos et pianos electriques.
//
//   g++ -O2 -std=c++17 -I app/src/main/cpp demo_pianos_all.cpp -o /tmp/dpa
//   /tmp/dpa app/src/main/assets /tmp/demo_all
//
// Un fichier par modele, pas un medley : dans un enchainement l'oreille perd sa
// reference et deux timbres voisins se confondent.  C'est la meme regle que la demo
// precedente (demo_piano.cpp), etendue aux 4 banques electriques.
//
// Les valeurs des 12 modeles ne sont PAS recopiees : demo_presets.h est genere
// depuis AePianoUi.kt par gen_demo_presets.py, donc la demo joue exactement ce que
// l'app charge.  La banque suit AeBanks.fileFor : piano.bin pour les modeles
// acoustiques, ep_*.bin pour les modeles 8..11.
//
// Melodie : refrain publie, en si bemol (F ^F ^D ^C ^F ^D Bb G / F Bb ^D# ^D ^C).
#include "aetherfx/fx_piano.h"
#include "demo_presets.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

using namespace aetherfx;

static const float kSr = 44100.f;
static const float kBeat = 0.36f;

struct Ev { int midi; float beats; int vel; bool bass; };

// Le refrain : "And can you feel the love tonight" (Bb) puis "It is where we are" (Eb).
// Une basse tenue sous chaque phrase exerce la polyphonie et la resonance sympathique,
// qu'une ligne melodique seule ne montrerait pas.
static const Ev kHook[] = {
    {65, 0.5f, 100, false}, {77, 0.5f, 108, false}, {74, 0.5f, 100, false},
    {72, 1.0f, 104, false}, {77, 0.5f, 104, false}, {74, 0.5f, 96, false},
    {70, 1.0f, 100, false}, {67, 1.5f, 88, false},
    {46, 6.0f, 76, true},
    {65, 0.5f, 96, false}, {70, 0.5f, 100, false}, {75, 1.0f, 104, false},
    {74, 0.5f, 96, false}, {72, 1.0f, 100, false}, {70, 1.5f, 96, false},
    {39, 6.0f, 74, true},
};
static const int kHookLen = static_cast<int>(sizeof(kHook) / sizeof(kHook[0]));

static std::vector<uint8_t> readFile(const std::string& path) {
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) return {};
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    std::vector<uint8_t> b(static_cast<size_t>(n));
    if (fread(b.data(), 1, b.size(), f) != b.size()) b.clear();
    fclose(f);
    return b;
}

static void renderTake(PianoEngine& eng, const float* params, std::vector<float>& out) {
    for (int i = 0; i < k_piano[0].n; ++i) eng.setNorm(i, params[i]);
    eng.reset();

    const int block = 512;
    std::vector<float> io(static_cast<size_t>(block) * 2);
    for (int e = 0; e < kHookLen; ++e) {
        const Ev& ev = kHook[e];
        eng.noteOn(ev.midi, ev.vel);
        long frames = static_cast<long>(ev.beats * kBeat * kSr);
        long cursor = 0;
        while (cursor < frames) {
            int n = static_cast<int>(std::min<long>(block, frames - cursor));
            std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
            eng.process(io.data(), n);
            out.insert(out.end(), io.begin(), io.begin() + static_cast<size_t>(n) * 2);
            cursor += n;
        }
        eng.noteOff(ev.midi);
    }
    // Queue : laisse la derniere phrase et la table d'harmonie s'eteindre.
    const long tail = static_cast<long>(1.6f * kSr);
    for (long done = 0; done < tail; done += block) {
        int n = static_cast<int>(std::min<long>(block, tail - done));
        std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
        eng.process(io.data(), n);
        out.insert(out.end(), io.begin(), io.begin() + static_cast<size_t>(n) * 2);
    }
}

int main(int argc, char** argv) {
    const std::string assets = argc > 1 ? argv[1] : "app/src/main/assets";
    const std::string outDir = argc > 2 ? argv[2] : "/tmp/demo_all";

    // La banque de chaque modele, comme AeBanks.fileFor.  Les indices 8..11 changent
    // de banque : un filtre ne transforme pas un Steinway en tine.
    std::vector<uint8_t> acoustic = readFile(assets + "/pianopack/piano.bin");
    if (acoustic.empty()) { fprintf(stderr, "pianopack/piano.bin illisible\n"); return 1; }
    const char* ep[12] = {nullptr};
    ep[8] = "pianopack/ep_tine.bin";
    ep[9] = "pianopack/ep_ballad.bin";
    ep[10] = "pianopack/ep_dx.bin";
    ep[11] = "pianopack/ep_piano.bin";

    for (int i = 0; i < k_piano_n; ++i) {
        const DemoPreset& pr = k_piano[i];
        std::vector<uint8_t> bank = ep[i] ? readFile(assets + "/" + ep[i]) : acoustic;
        if (bank.empty()) { fprintf(stderr, "banque absente pour %s\n", pr.name); continue; }

        PianoEngine eng;
        eng.prepare(kSr, 512);
        if (!eng.loadSampleBank(bank.data(), bank.size())) {
            fprintf(stderr, "banque refusee pour %s\n", pr.name);
            continue;
        }
        std::vector<float> take;
        renderTake(eng, pr.v, take);

        double e = 0;
        for (float x : take) e += static_cast<double>(x) * x;
        double rms = std::sqrt(e / std::max<size_t>(1, take.size()));

        std::string slug;
        for (const char* c = pr.cat; *c; ++c)
            slug += (*c == ' ' || *c == '/') ? '_' : static_cast<char>(::tolower(*c));
        char path[600];
        snprintf(path, sizeof path, "%s/piano_%02d_%s.f32", outDir.c_str(), i, slug.c_str());
        FILE* f = fopen(path, "wb");
        if (!f) { fprintf(stderr, "ecriture impossible : %s\n", path); return 1; }
        fwrite(take.data(), sizeof(float), take.size(), f);
        fclose(f);
        printf("  %2d %-10s %-12s %5.1f s  rms %.4f  %s\n", i, pr.cat, pr.name,
               take.size() / 2.0 / kSr, rms, ep[i] ? ep[i] : "piano.bin");
        if (rms < 0.001) fprintf(stderr, "  !! signal quasi nul : %s\n", pr.name);
    }
    printf("fait -> %s\n", outDir.c_str());
    return 0;
}
