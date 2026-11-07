// Demo : le couplet de « Isn't She Lovely » (Stevie Wonder, 1976) joue sur les
// 12 pianos et pianos electriques du depot, pour ecouter le correctif de boucle
// en musique plutot que sur un banc.
//
//   g++ -O2 -std=c++17 -I app/src/main/cpp -I tools/aetherfx demo_isnt_she_lovely.cpp -o /tmp/disl
//   /tmp/disl app/src/main/assets /tmp/demo_isl
//
// Ton : mi majeur, 12/8.  Grille du couplet (releves concordants) :
//   C#m7 | F#9 | B9sus4 | E   puis   Amaj7 | G#7 | C#m7 | F#9 | B9sus4 | E
// La basse tient la fondamentale de chaque accord.  La ligne chantee est
// RECONSTITUEE en pentatonique de mi (E F# G# B C#), la gamme du morceau, et se
// termine sur le riff pentatonique B-C#-E-F#-G# releve a la guitare.
//
// Les valeurs des 12 modeles ne sont PAS recopiees : demo_presets.h est genere
// depuis AePianoUi.kt, donc la demo joue exactement ce que l'app charge.  La
// banque suit AeBanks.fileFor : piano.bin pour les acoustiques, ep_*.bin pour 8..11.
#include "aetherfx/fx_piano.h"
#include "demo_presets.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

using namespace aetherfx;

static const float kSr = 44100.f;
static const float kEighth = 0.24f;  // croche de 12/8
static const int kPerBar = 12;

struct MEv { int midi; float e; int vel; };
struct Bar { int bass; std::vector<MEv> mel; };

// Une mesure par ligne de texte.  Les durees sont en croches et totalisent 12.
static const Bar kBars[] = {
    // "Isn't she lovely"        (C#m7)
    {37, {{73, 2, 96}, {71, 2, 100}, {68, 4, 104}, {68, 4, 96}}},
    // "Isn't she wonderful"     (F#9)
    {42, {{66, 2, 96}, {68, 2, 100}, {71, 2, 104}, {71, 2, 100}, {68, 2, 96}, {66, 2, 92}}},
    // "Isn't she precious"      (B9sus4)
    {35, {{71, 2, 96}, {71, 2, 98}, {73, 2, 104}, {73, 2, 100}, {71, 4, 94}}},
    // "Less than one minute old" (E)
    {40, {{68, 2, 96}, {66, 2, 98}, {64, 2, 100}, {66, 2, 98}, {68, 2, 94}, {64, 2, 92}}},
    // "But isn't she lovely"    (C#m7 -> F#9 -> B11) : montee pentatonique
    {37, {{68, 2, 96}, {71, 2, 100}, {73, 2, 104}, {76, 2, 106}, {78, 2, 104}, {80, 2, 108}}},
    // "made from love"          (E) : resolution tenue sur la tonique
    {40, {{80, 2, 104}, {76, 2, 100}, {76, 8, 94}}},
};
static const int kBarCount = static_cast<int>(sizeof(kBars) / sizeof(kBars[0]));

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

struct Event { long frame; int midi; int vel; bool on; };

static std::vector<Event> buildTimeline(long& totalFrames) {
    std::vector<Event> ev;
    long barStart = 0;
    for (int b = 0; b < kBarCount; ++b) {
        const Bar& bar = kBars[b];
        const long barFrames = static_cast<long>(kPerBar * kEighth * kSr);
        ev.push_back({barStart, bar.bass, 76, true});
        ev.push_back({barStart + barFrames, bar.bass, 0, false});
        long cursor = barStart;
        for (const MEv& m : bar.mel) {
            const long dur = static_cast<long>(m.e * kEighth * kSr);
            ev.push_back({cursor, m.midi, m.vel, true});
            ev.push_back({cursor + dur, m.midi, 0, false});
            cursor += dur;
        }
        barStart += barFrames;
    }
    totalFrames = barStart;
    // Relachement avant attaque au meme instant : une note repetee se rejoue proprement.
    std::sort(ev.begin(), ev.end(), [](const Event& a, const Event& b) {
        if (a.frame != b.frame) return a.frame < b.frame;
        return a.on < b.on;
    });
    return ev;
}

static void renderTake(PianoEngine& eng, const float* params, std::vector<float>& out) {
    for (int i = 0; i < k_piano[0].n; ++i) eng.setNorm(i, params[i]);
    eng.reset();

    long total = 0;
    const std::vector<Event> ev = buildTimeline(total);
    const long end = total + static_cast<long>(2.2f * kSr);  // queue de resonance

    const int block = 512;
    std::vector<float> io(static_cast<size_t>(block) * 2);
    size_t next = 0;
    long pos = 0;
    while (pos < end) {
        // On rend jusqu'au prochain evenement pour que les attaques tombent a
        // l'echantillon pres (une note par bloc decalerait tout le rythme).
        long boundary = end;
        if (next < ev.size() && ev[next].frame < boundary) boundary = ev[next].frame;
        while (pos < boundary) {
            const int n = static_cast<int>(std::min<long>(block, boundary - pos));
            std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
            eng.process(io.data(), n);
            out.insert(out.end(), io.begin(), io.begin() + static_cast<size_t>(n) * 2);
            pos += n;
        }
        for (; next < ev.size() && ev[next].frame <= pos; ++next) {
            if (ev[next].on) eng.noteOn(ev[next].midi, ev[next].vel);
            else eng.noteOff(ev[next].midi);
        }
    }
}

int main(int argc, char** argv) {
    const std::string assets = argc > 1 ? argv[1] : "app/src/main/assets";
    const std::string outDir = argc > 2 ? argv[2] : "/tmp/demo_isl";

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

        double e = 0, pk = 0;
        for (float x : take) {
            e += static_cast<double>(x) * x;
            pk = std::max(pk, std::fabs(static_cast<double>(x)));
        }
        const double rms = std::sqrt(e / std::max<size_t>(1, take.size()));

        std::string slug;
        for (const char* c = pr.cat; *c; ++c)
            slug += (*c == ' ' || *c == '/') ? '_' : static_cast<char>(::tolower(*c));
        char path[600];
        snprintf(path, sizeof path, "%s/isl_%02d_%s.f32", outDir.c_str(), i, slug.c_str());
        FILE* f = fopen(path, "wb");
        if (!f) { fprintf(stderr, "ecriture impossible : %s\n", path); return 1; }
        fwrite(take.data(), sizeof(float), take.size(), f);
        fclose(f);
        printf("  %2d %-10s %-12s %5.1f s  rms %.4f  crete %.3f  %s\n", i, pr.cat, pr.name,
               take.size() / 2.0 / kSr, rms, pk, ep[i] ? ep[i] : "piano.bin");
        if (rms < 0.001) fprintf(stderr, "  !! signal quasi nul : %s\n", pr.name);
    }
    printf("fait -> %s\n", outDir.c_str());
    return 0;
}
