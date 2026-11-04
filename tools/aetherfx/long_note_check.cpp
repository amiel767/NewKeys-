// Mesure le « pompage » d'une note LONGUE tenue (faux sustain / echo).
//
//   g++ -O2 -std=c++17 -I app/src/main/cpp -I tools/aetherfx long_note_check.cpp -o /tmp/ln
//   /tmp/ln app/src/main/assets/pianopack/piano.bin
//
// Le symptome : « on dirait que la boucle redemarre au lieu de continuer de sonner ».
// Un segment de boucle qui decroit, reboucle sans fondu, fait REMONTER l'enveloppe a
// chaque tour.  Compter les maxima locaux ne marche pas : le signal lui-meme ondule
// (battements entre partiels inharmoniques) et noie la mesure.
//
// Metrique retenue : on calcule l'enveloppe RMS, on la DETREND par une moyenne longue
// (ce qui retire la decroissance naturelle de la corde), puis on mesure l'energie du
// residu et sa periode dominante par autocorrelation.  Une note saine a un residu plat
// (< quelques %) ; une boucle qui pompe montre un residu dont la periode EST la boucle.
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
    fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
    std::vector<uint8_t> b(static_cast<size_t>(n));
    if (fread(b.data(), 1, b.size(), f) != b.size()) b.clear();
    fclose(f);
    return b;
}

int main(int argc, char** argv) {
    const std::string bank = argc > 1 ? argv[1] : "app/src/main/assets/pianopack/piano.bin";
    auto bytes = readFile(bank);
    if (bytes.empty()) { fprintf(stderr, "banque illisible : %s\n", bank.c_str()); return 1; }

    PianoEngine eng;
    eng.prepare(kSr, 512);
    if (!eng.loadSampleBank(bytes.data(), bytes.size())) { fprintf(stderr, "banque refusee\n"); return 1; }
    for (int i = 0; i < k_piano[1].n; ++i) eng.setNorm(i, k_piano[1].v[i]);   // Studio

    const int notes[] = {36, 48, 60, 72, 84};
    const int blk = 512;
    std::vector<float> io(blk * 2);

    printf("%-4s | %7s | %8s | %9s\n", "note", "residu", "periode", "niveau dB");
    for (int nt : notes) {
        eng.reset();
        eng.noteOn(nt, 100);
        const long total = static_cast<long>(8.0f * kSr);
        std::vector<float> out;
        for (long done = 0; done < total; done += blk) {
            int n = static_cast<int>(std::min<long>(blk, total - done));
            std::fill(io.begin(), io.begin() + n * 2, 0.f);
            eng.process(io.data(), n);
            for (int j = 0; j < n; ++j) out.push_back(0.5f * (io[2*j] + io[2*j+1]));
        }
        // Enveloppe RMS a 10 ms.
        const int w = static_cast<int>(0.010 * kSr);
        std::vector<double> env;
        for (size_t a = 0; a + w <= out.size(); a += w) {
            double e = 0;
            for (int j = 0; j < w; ++j) e += static_cast<double>(out[a+j]) * out[a+j];
            env.push_back(std::sqrt(e / w));
        }
        // On ne garde que la tenue (apres 0,6 s, une fois l'attaque et le marteau finis).
        const size_t from = static_cast<size_t>(0.6 * kSr / w);
        std::vector<double> seg(env.begin() + from, env.end());
        const size_t N = seg.size();
        // Detrend par moyenne mobile longue (0,5 s) : retire la decroissance de la corde.
        const size_t M = static_cast<size_t>(0.5 * kSr / w);
        std::vector<double> resid(N, 0.0);
        for (size_t i = 0; i < N; ++i) {
            double s = 0; size_t c = 0;
            for (size_t k = (i > M ? i - M : 0); k <= std::min(N - 1, i + M); ++k) { s += seg[k]; ++c; }
            const double trend = s / std::max<size_t>(1, c);
            resid[i] = seg[i] / std::max(1e-12, trend) - 1.0;
        }
        double e = 0; for (double v : resid) e += v * v;
        const double rms = std::sqrt(e / std::max<size_t>(1, N));
        // Autocorrelation du residu : la periode du pompage est le 1er pic.
        double ac0 = 0; for (double v : resid) ac0 += v * v;
        size_t bestLag = 0; double bestAc = 0;
        const size_t loLag = static_cast<size_t>(0.15 * kSr / w), hiLag = std::min(N / 2, static_cast<size_t>(1.4 * kSr / w));
        for (size_t lag = loLag; lag < hiLag; ++lag) {
            double a = 0;
            for (size_t i = 0; i + lag < N; ++i) a += resid[i] * resid[i + lag];
            a /= std::max(1.0, ac0);
            if (a > bestAc) { bestAc = a; bestLag = lag; }
        }
        const double head = env[from], tail = env.back();
        printf("%4d | %6.1f%% | %6.2f s | %+6.1f dB\n", nt, rms * 100.0,
               bestLag * (w / kSr), 20 * std::log10(std::max(1e-9, tail) / std::max(1e-9, head)));
    }
    return 0;
}
