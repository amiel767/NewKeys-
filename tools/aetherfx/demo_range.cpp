// Demo : la plage complete C0 -> C7 sur chaque piano et piano electrique, a deux
// nuances, en tenue longue.  Sert a entendre le correctif de boucle sur toute
// l'etendue (les notes graves sont celles ou le defaut etait le plus audible) et
// a verifier que la velocite change bien le timbre du bas en haut du clavier.
//
//   g++ -O2 -std=c++17 -I app/src/main/cpp -I tools/aetherfx demo_range.cpp -o /tmp/drng
//   /tmp/drng app/src/main/assets /tmp/demo_rng
//
// Protocole : pour chaque octave, la note est jouee piano (vel 42) puis forte
// (vel 118), chaque fois tenue 2,8 s.  Deux passes sur la meme note separent la
// nuance de la hauteur ; une tenue de 2,8 s laisse ~1,4 s de boucle, la ou un
// defaut de raccord s'entend.
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
static const float kHold = 2.8f;   // duree d'une note tenue
static const float kGap = 0.35f;   // silence entre deux notes
static const float kTail = 2.0f;   // queue apres la derniere note

// C0..C7 : middle C = C4 (MIDI 60).
static const int kNotes[] = {12, 24, 36, 48, 60, 72, 84, 96};
static const int kNoteCount = static_cast<int>(sizeof(kNotes) / sizeof(kNotes[0]));
static const int kPiano = 42;   // pp
static const int kForte = 118;  // ff

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

// Rend `frames` echantillons sans toucher aux notes en cours.
static void render(PianoEngine& eng, long frames, std::vector<float>& out) {
    const int block = 512;
    std::vector<float> io(static_cast<size_t>(block) * 2);
    for (long done = 0; done < frames; done += block) {
        const int n = static_cast<int>(std::min<long>(block, frames - done));
        std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
        eng.process(io.data(), n);
        out.insert(out.end(), io.begin(), io.begin() + static_cast<size_t>(n) * 2);
    }
}

// Rend une note tenue puis la relache et laisse le silence s'installer.
static void playNote(PianoEngine& eng, int note, int vel, std::vector<float>& out) {
    eng.noteOn(note, vel);
    render(eng, static_cast<long>(kHold * kSr), out);
    eng.noteOff(note);
    render(eng, static_cast<long>(kGap * kSr), out);
}

int main(int argc, char** argv) {
    const std::string assets = argc > 1 ? argv[1] : "app/src/main/assets";
    const std::string outDir = argc > 2 ? argv[2] : "/tmp/demo_rng";

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
        for (int k = 0; k < k_piano[0].n; ++k) eng.setNorm(k, pr.v[k]);

        std::vector<float> take;
        std::vector<std::pair<int, double>> levels;  // niveau par note, pour le controle
        for (int pass = 0; pass < 2; ++pass) {
            const int vel = pass == 0 ? kPiano : kForte;
            for (int n = 0; n < kNoteCount; ++n) {
                const size_t before = take.size();
                playNote(eng, kNotes[n], vel, take);
                double e = 0;
                const size_t frames = take.size() - before;
                for (size_t j = before; j < take.size(); ++j) e += static_cast<double>(take[j]) * take[j];
                levels.push_back({kNotes[n], std::sqrt(e / std::max<size_t>(1, frames))});
            }
        }
        render(eng, static_cast<long>(kTail * kSr), take);

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
        snprintf(path, sizeof path, "%s/rng_%02d_%s.f32", outDir.c_str(), i, slug.c_str());
        FILE* f = fopen(path, "wb");
        if (!f) { fprintf(stderr, "ecriture impossible : %s\n", path); return 1; }
        fwrite(take.data(), sizeof(float), take.size(), f);
        fclose(f);
        printf("  %2d %-10s %-12s %5.1f s  rms %.4f  crete %.3f  %s\n", i, pr.cat, pr.name,
               take.size() / 2.0 / kSr, rms, pk, ep[i] ? ep[i] : "piano.bin");
        // pp (passe 0) puis ff (passe 1), meme note : un ecart de 0 dB veut dire
        // que la banque ne repond pas a la velocite sur cette note.
        for (int k = 0; k < kNoteCount; ++k) {
            const double a = levels[k].second, b = levels[k + kNoteCount].second;
            const double db = 20.0 * std::log10(std::max(1e-9, b) / std::max(1e-9, a));
            printf("       note %3d : pp %.5f  ff %.5f  ecart %+5.1f dB\n",
                   levels[k].first, a, b, db);
            if (a < 1e-4 && b < 1e-4) fprintf(stderr, "  !! note %d muette\n", levels[k].first);
        }
    }
    printf("fait -> %s\n", outDir.c_str());
    return 0;
}
