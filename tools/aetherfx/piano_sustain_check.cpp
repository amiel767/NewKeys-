// Un seul piano, une seule note tenue : isole le « dain dain dain » du sustain.
//
//   g++ -O2 -std=c++17 -I app/src/main/cpp tools/aetherfx/piano_sustain_check.cpp -o /tmp/psc
//   /tmp/psc app/src/main/assets
//
// Le refrain multi-notes masque la modulation de boucle (l'autocorrelation est
// dominee par le rythme de la phrase).  Ici une note est frappee puis tenue : toute
// periodicite de l'enveloppe pendant la tenue vient du rebouclage de l'echantillon.
#include "aetherfx/fx_piano.h"
#include "demo_presets.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
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

/** Tenue d'une note : 0,15 s d'attaque puis 4 s tenues, sans relachement. */
static std::vector<float> hold(PianoEngine& eng, const float* params, int midi, int vel) {
    for (int i = 0; i < k_piano[0].n; ++i) eng.setNorm(i, params[i]);
    eng.reset();
    std::vector<float> out;
    const int block = 512;
    std::vector<float> io(static_cast<size_t>(block) * 2);
    eng.noteOn(midi, vel);
    const long frames = static_cast<long>(4.0f * kSr);
    for (long c = 0; c < frames; c += block) {
        const int n = static_cast<int>(std::min<long>(block, frames - c));
        std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
        eng.process(io.data(), n);
        out.insert(out.end(), io.begin(), io.begin() + static_cast<size_t>(n) * 2);
    }
    return out;
}

int main(int argc, char** argv) {
    const std::string assets = argc > 1 ? argv[1] : "app/src/main/assets";
    std::vector<uint8_t> acoustic = readFile(assets + "/pianopack/piano.bin");
    if (acoustic.empty()) { fprintf(stderr, "piano.bin illisible\n"); return 1; }

    // 4 modeles acoustiques + 1 electrique, notes graves/moyennes (la boucle y est
    // plus longue, donc le defaut plus audible).
    const int models[] = {1, 0, 5, 8};
    const int notes[] = {48, 60, 72};

    for (int mi : models) {
        const DemoPreset& pr = k_piano[mi];
        std::vector<uint8_t> bank = acoustic;
        if (mi == 8) {
            bank = readFile(assets + "/pianopack/ep_tine.bin");
            if (bank.empty()) continue;
        }
        for (int midi : notes) {
            PianoEngine eng;
            eng.prepare(kSr, 512);
            if (!eng.loadSampleBank(bank.data(), bank.size())) continue;
            std::vector<float> pv(pr.v, pr.v + k_piano[0].n);
            const std::string opt = argc > 2 ? argv[2] : "";
            if (opt == "noloop") eng.setLoopsEnabled(false);
            if (opt == "nobody") pv[2] = 0.f;        // resonateurs (RESONANCE)
            if (opt == "nospace") pv[5] = 0.f;       // reverb (SPACE)
            if (opt == "nodamper") pv[10] = 0.f;     // etouffoir
            if (opt == "single") pv[9] = 0.f;        // pas de fondu de couches (vel. fixe)
            if (opt == "tine") pv[15] = 1.f;         // tremolo ON (comme le preset Tine Keys)
            if (opt == "notine") pv[15] = 0.5f;      // tremolo OFF
            std::vector<float> take = hold(eng, pv.data(), midi, 100);
            if (midi == 60 && (mi == 1 || mi == 8)) {   // acoustique + electrique
                char p[64];
                snprintf(p, sizeof p, "/tmp/hold_%s.f32", mi == 8 ? "ep" : "ac");
                FILE* f = fopen(p, "wb");
                if (f) { fwrite(take.data(), sizeof(float), take.size(), f); fclose(f); }
            }

            const int w = 220;                 // 5 ms
            const int n = static_cast<int>(take.size() / 2) / w;
            std::vector<double> env(n);
            for (int i = 0; i < n; ++i) {
                double s = 0;
                for (int j = 0; j < w; ++j) {
                    const float l = take[2 * (i * w + j)], r = take[2 * (i * w + j) + 1];
                    s += 0.5 * (l * l + r * r);
                }
                env[i] = std::sqrt(s / w);
            }
            // Autocorrelation sur la tenue (0,3 s .. fin).  Une enveloppe de piano
            // DECROIT : son autocorrelation brute est monotone et le maximum tombe
            // toujours au plus petit lag (resultat vide).  On retire donc la
            // tendance : on analyse l'ecart a une version lissee, ce qui ne garde
            // que la modulation (le « dain dain ») et pas la decroissance.
            const int a = static_cast<int>(0.3 / 0.005);
            const int b = n;
            const int len = b - a;
            std::vector<double> x(len);
            for (int i = 0; i < len; ++i) x[i] = env[a + i];
            // Lissage par moyenne glissante large (0,25 s) = la tendance.
            const int sw = 50;
            std::vector<double> trend(len), d(len);
            for (int i = 0; i < len; ++i) {
                double s = 0; int c = 0;
                for (int j = std::max(0, i - sw); j <= std::min(len - 1, i + sw); ++j) { s += x[j]; ++c; }
                trend[i] = s / std::max(1, c);
                d[i] = x[i] - trend[i];
            }
            double v0 = 0;
            for (int i = 0; i < len; ++i) v0 += d[i] * d[i];
            double best = 0;
            int bestLag = 0;
            for (int lag = 4; lag < std::min(len / 2, 800); ++lag) {
                double c = 0;
                for (int i = 0; i + lag < len; ++i) c += d[i] * d[i + lag];
                c /= std::max(1e-12, v0);
                if (c > best) { best = c; bestLag = lag; }
            }
            // Modulation residuelle : ecart-type du detrend / tendance, en dB.
            double sd = std::sqrt(v0 / std::max(1, len));
            double mt = 0;
            for (int i = 0; i < len; ++i) mt += trend[i];
            mt /= std::max(1, len);
            printf("  %-10s note %3d : periode %5.3f s  corr %.2f   modulation %.1f dB\n",
                   pr.name, midi, bestLag * 0.005, best,
                   20.0 * std::log10((mt + sd) / std::max(1e-9, mt - sd)));
        }
    }
    return 0;
}
