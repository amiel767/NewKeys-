// Demo de la reponse en VELOCITE du piano acoustique (piano.bin).
//
//   g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/demo_velocity.cpp -o /tmp/dv
//   /tmp/dv app/src/main/assets/pianopack/piano.bin /tmp/vel
//
// Le pack est normalise PAR COUCHE : c'est l'ecart de niveau entre couches qui porte
// la dynamique.  Une prise unique ne montre pas si cet ecart existe ; il faut donc
// comparer pp / mf / ff sur le MEME materiau.  Trois prises :
//   - gamme montante, chaque note +6 de velocite  -> entend le timbre changer
//   - meme accord a pp / mf / ff                  -> entend l'ecart de niveau
//   - meme note repetee pp / mf / ff en boucle    -> entend l'attaque
// Sortie : float32 stereo brut, un fichier par prise.
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

struct Event { int midi; float beats; int vel; };

// Joue une sequence d'accords/notes, chaque evenement pouvant porter N notes.
static void play(PianoEngine& eng, const std::vector<std::vector<Event>>& bars,
                 float beat, std::vector<float>& out) {
    const int block = 512;
    std::vector<float> io(static_cast<size_t>(block) * 2);
    for (const auto& bar : bars) {
        // Duree = le plus long evenement de la barre (ils demarrent ensemble).
        float longest = 0.f;
        for (const auto& e : bar) longest = std::max(longest, e.beats);
        for (const auto& e : bar) eng.noteOn(e.midi, e.vel);
        long frames = static_cast<long>(longest * beat * kSr);
        for (long done = 0; done < frames; done += block) {
            int n = static_cast<int>(std::min<long>(block, frames - done));
            std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
            eng.process(io.data(), n);
            out.insert(out.end(), io.begin(), io.begin() + static_cast<size_t>(n) * 2);
        }
        for (const auto& e : bar) eng.noteOff(e.midi);
    }
    // Queue : laisse la table d'harmonie s'eteindre.
    const long tail = static_cast<long>(1.5f * kSr);
    for (long done = 0; done < tail; done += block) {
        int n = static_cast<int>(std::min<long>(block, tail - done));
        std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
        eng.process(io.data(), n);
        out.insert(out.end(), io.begin(), io.begin() + static_cast<size_t>(n) * 2);
    }
}

static bool writeF32(const std::string& path, const std::vector<float>& x) {
    FILE* f = fopen(path.c_str(), "wb");
    if (!f) return false;
    fwrite(x.data(), sizeof(float), x.size(), f);
    fclose(f);
    return true;
}

static double rmsOf(const std::vector<float>& x) {
    double e = 0;
    for (float v : x) e += static_cast<double>(v) * v;
    return std::sqrt(e / std::max<size_t>(1, x.size()));
}

int main(int argc, char** argv) {
    const std::string assets = argc > 1 ? argv[1] : "app/src/main/assets";
    const std::string outDir = argc > 2 ? argv[2] : "/tmp/vel";
    const std::string wantModel = argc > 3 ? argv[3] : "Studio";

    // Banque par modele, comme AeBanks.fileFor : acoustiques sur piano.bin,
    // 8..11 sur leur ep_*.bin.
    const char* ep[12] = {nullptr};
    ep[8] = "pianopack/ep_tine.bin";
    ep[9] = "pianopack/ep_ballad.bin";
    ep[10] = "pianopack/ep_dx.bin";
    ep[11] = "pianopack/ep_piano.bin";

    const DemoPreset* model = nullptr;
    int modelIdx = -1;
    for (int i = 0; i < k_piano_n; ++i)
        if (std::string(k_piano[i].name) == wantModel) { model = &k_piano[i]; modelIdx = i; }
    if (!model) { fprintf(stderr, "modele « %s » absent de demo_presets.h\n", wantModel.c_str()); return 1; }

    const std::string bankPath = assets + "/" + (ep[modelIdx] ? ep[modelIdx] : "pianopack/piano.bin");
    auto bytes = readFile(bankPath);
    if (bytes.empty()) { fprintf(stderr, "banque illisible : %s\n", bankPath.c_str()); return 1; }
    printf("modele %s  banque %s\n", model->name, bankPath.c_str());

    PianoEngine eng;
    eng.prepare(kSr, 512);
    if (!eng.loadSampleBank(bytes.data(), bytes.size())) { fprintf(stderr, "banque refusee\n"); return 1; }

    struct Take { const char* name; const char* desc; std::vector<std::vector<Event>> bars; float beat; };
    std::vector<Take> takes;

    // 1. Gamme de do majeur montante, +6 de velocite par note (26 -> 116) : le
    //    timbre doit s'eclaircir, pas seulement grossir.
    {
        std::vector<std::vector<Event>> bars;
        const int scale[] = {60, 62, 64, 65, 67, 69, 71, 72, 74, 76, 77, 79, 81, 83, 84};
        for (int i = 0; i < 15; ++i)
            bars.push_back({{scale[i], 0.5f, 26 + i * 6}});
        takes.push_back({"vel_scale", "gamme montante, velocite 26 -> 110", bars, 0.42f});
    }
    // 2. Meme accord (Do majeur + 7e) a pp / mf / ff, enchaines : l'ecart de niveau
    //    et de timbre s'entend d'un bloc a l'autre.
    {
        std::vector<std::vector<Event>> bars;
        const int chord[] = {48, 55, 60, 64, 67, 70};
        const int vels[] = {24, 48, 72, 96, 120};
        for (int v : vels) {
            std::vector<Event> bar;
            for (int m : chord) bar.push_back({m, 1.2f, v});
            bars.push_back(bar);
        }
        takes.push_back({"vel_chord", "meme accord a pp/pp-mf/mf/ff/fff", bars, 0.9f});
    }
    // 3. Meme note repetee pp / mf / ff : montre l'attaque (marteau) par palier.
    {
        std::vector<std::vector<Event>> bars;
        const int vels[] = {20, 40, 60, 80, 100, 120};
        for (int rep = 0; rep < 3; ++rep)
            for (int v : vels) bars.push_back({{60, 0.45f, v}});
        takes.push_back({"vel_repeat", "Do4 repete pp -> ff (x3)", bars, 0.5f});
    }

    for (auto& t : takes) {
        for (int i = 0; i < model->n; ++i) eng.setNorm(i, model->v[i]);
        eng.reset();
        std::vector<float> take;
        play(eng, t.bars, t.beat, take);
        std::string path = outDir + "/" + t.name + ".f32";
        if (!writeF32(path, take)) { fprintf(stderr, "ecriture impossible : %s\n", path.c_str()); return 1; }
        printf("  %-11s %5.1f s  rms %.4f  %s\n", t.name, take.size() / 2.0 / kSr, rmsOf(take), t.desc);
    }
    printf("fait -> %s\n", outDir.c_str());
    return 0;
}
