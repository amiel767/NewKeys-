// Demo : une phrase reelle jouee sur chacun des 4 saxophones livres (MTG Solo Sax).
//
//   g++ -O2 -std=c++17 -I app/src/main/cpp -I tools/aetherfx demo_sax.cpp -o /tmp/dsax
//   /tmp/dsax app/src/main/assets /tmp/demo_sax
//
// Un fichier par type (soprano, alto, tenor, baryton), pas un medley : l'oreille perd sa
// reference dans un enchainement et deux timbres voisins se confondent.
//
// Les valeurs des presets ne sont PAS recopiees : demo_presets.h est genere depuis
// AePresets.kt par gen_demo_presets.py, donc la demo joue exactement ce que l'app charge.
// La banque suit AeInstrumentHost.saxFiles : saxpack_{sop,alt,ten,bar}.bin.
//
// La phrase est un lick blues/jazz ORIGINAL (aucun droit d'auteur) en huitiemes, avec une
// note tenue (vibrato), un saut d'octave et une descente : de quoi entendre l'attaque, la
// tenue et le registre grave/aigu de chaque type. La ligne est transposee par type pour
// rester dans la tessiture de chacun (soprano haut, baryton bas).
#include "aetherfx/fx_sax.h"
#include "demo_presets.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

using namespace aetherfx;

static const float kSr = 44100.f;
static const int kBlk = 256;
static const float kBeat = 0.42f;  // noire = 420 ms (~143 BPM, tempo de jazz median)

struct Ev { int midi; float beats; int vel; };

// Lick blues/jazz original : montée chromatique, note tenue, saut, descente.
// Transpose par `shift` selon le type (soprano +5, alto 0, tenor -5, baryton -12).
static const Ev kLick[] = {
    {67, 0.5f, 96}, {69, 0.5f, 98}, {70, 0.5f, 100}, {71, 0.5f, 104},
    {72, 1.0f, 108}, {71, 0.5f, 100}, {70, 0.5f, 96}, {69, 1.0f, 94},
    {64, 0.5f, 92}, {67, 0.5f, 96}, {69, 1.5f, 100},   // note tenue -> vibrato
    {72, 0.5f, 104}, {74, 0.5f, 108}, {75, 1.0f, 110}, {74, 0.5f, 104},
    {72, 0.5f, 100}, {71, 0.5f, 98}, {69, 1.5f, 96},
    {67, 0.5f, 98}, {64, 0.5f, 94}, {60, 2.0f, 92},    // resolution grave, tenue
};
static const int kLickLen = static_cast<int>(sizeof(kLick) / sizeof(kLick[0]));

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

// Rend une prise : la phrase entiere puis une queue pour laisser la salle s'eteindre.
static void renderTake(SaxEngine& eng, const float* params, int shift, std::vector<float>& out) {
    for (int i = 0; i < 23; ++i) eng.setNorm(i, params[i]);
    eng.reset();

    std::vector<float> io(static_cast<size_t>(kBlk) * 2);
    for (int e = 0; e < kLickLen; ++e) {
        const Ev& ev = kLick[e];
        const int note = ev.midi + shift;
        eng.noteOn(note, ev.vel);
        long frames = static_cast<long>(ev.beats * kBeat * kSr);
        long cursor = 0;
        while (cursor < frames) {
            int n = static_cast<int>(std::min<long>(kBlk, frames - cursor));
            std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
            eng.process(io.data(), n);
            out.insert(out.end(), io.begin(), io.begin() + static_cast<size_t>(n) * 2);
            cursor += n;
        }
        eng.noteOff(note);
    }
    const long tail = static_cast<long>(2.0f * kSr);
    for (long done = 0; done < tail; done += kBlk) {
        int n = static_cast<int>(std::min<long>(kBlk, tail - done));
        std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
        eng.process(io.data(), n);
        out.insert(out.end(), io.begin(), io.begin() + static_cast<size_t>(n) * 2);
    }
}

// Preset par type, cherche par nom dans la table generee depuis AePresets.kt.
static const DemoPreset* findPreset(const char* name) {
    for (int i = 0; i < k_sax_n; ++i)
        if (std::string(k_sax[i].name) == name) return &k_sax[i];
    return nullptr;
}

int main(int argc, char** argv) {
    const std::string assets = argc > 1 ? argv[1] : "app/src/main/assets";
    const std::string outDir = argc > 2 ? argv[2] : "/tmp/demo_sax";

    struct Type { const char* preset; const char* bank; int shift; const char* slug; };
    // Le parametre 0 du preset choisit le type (0 sop, 1 alto, 2 tenor, 3 bari) ; la banque
    // doit correspondre, sinon le moteur joue un autre saxophone que celui annonce.
    const Type types[4] = {
        {"Smooth Soprano", "saxpack_sop.bin", 5, "soprano"},
        {"Jazz Ballad Alto", "saxpack_alt.bin", 0, "alto"},
        {"Neo-Soul Tenor", "saxpack_ten.bin", -5, "tenor"},
        {"Bari Groove", "saxpack_bar.bin", -12, "baryton"},
    };

    for (const Type& ty : types) {
        const DemoPreset* pr = findPreset(ty.preset);
        if (!pr) { fprintf(stderr, "preset introuvable : %s\n", ty.preset); continue; }
        std::vector<uint8_t> bank = readFile(assets + "/saxpack/" + ty.bank);
        if (bank.empty()) { fprintf(stderr, "banque absente : %s\n", ty.bank); continue; }

        SaxEngine eng;
        eng.prepare(kSr, kBlk);
        if (!eng.loadSampleBank(bank.data(), bank.size())) {
            fprintf(stderr, "banque refusee : %s\n", ty.bank);
            continue;
        }
        std::vector<float> take;
        renderTake(eng, pr->v, ty.shift, take);

        double e = 0, pk = 0;
        for (float x : take) { e += static_cast<double>(x) * x; pk = std::max(pk, std::fabs(static_cast<double>(x))); }
        const double rms = std::sqrt(e / std::max<size_t>(1, take.size()));

        char path[600];
        snprintf(path, sizeof path, "%s/sax_%s.f32", outDir.c_str(), ty.slug);
        FILE* f = fopen(path, "wb");
        if (!f) { fprintf(stderr, "ecriture impossible : %s\n", path); return 1; }
        fwrite(take.data(), sizeof(float), take.size(), f);
        fclose(f);
        printf("  %-8s %-20s %5.1f s  rms %.4f  crete %.3f  %s\n", ty.slug, ty.preset,
               take.size() / 2.0 / kSr, rms, pk, ty.bank);
        if (rms < 0.001) fprintf(stderr, "  !! signal quasi nul : %s\n", ty.slug);
    }
    printf("fait -> %s\n", outDir.c_str());
    return 0;
}
