// Mesure le residu d'enveloppe d'une note TENUE, par velocite.
//
//   g++ -O2 -std=c++17 -I app/src/main/cpp -I tools/aetherfx hold_residual.cpp -o /tmp/hr
//   /tmp/hr app/src/main/assets/pianopack/piano.bin
//
// Le symptome « Daiiiinnndainnndainnn » : l'enveloppe de la tenue ondule a la
// periode de la boucle.  On la mesure directement : enveloppe RMS a 5 ms, detrend
// par une moyenne de 250 ms, residu RMS en %, et periode dominante par
// autocorrelation.  Une note saine a un residu < 2 % et une periode NON correlee a
// la boucle ; une boucle qui bat montre un residu fort a SA periode.
#include "aetherfx/fx_piano.h"
#include "demo_presets.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <chrono>
#include <string>
#include <vector>

using namespace aetherfx;
static const float kSr = 44100.f;

static std::vector<uint8_t> readFile(const std::string& p) {
    FILE* f = fopen(p.c_str(), "rb");
    if (!f) return {};
    fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
    std::vector<uint8_t> b(static_cast<size_t>(n));
    if (fread(b.data(), 1, b.size(), f) != b.size()) b.clear();
    fclose(f);
    return b;
}

struct Res { double rms; double period; double tailDb; double loopMod; int rises; double riseAmp; };

/** Modulation de l'enveloppe a la periode de BOUCLE, en % de l'enveloppe moyenne.
 *
 * C'est la metrique qui identifie le « dain dain » : une boucle qui reboucle mal
 * remonte ou retombe a chaque tour, donc l'enveloppe porte une composante a
 * f = 1/longueur_de_boucle.  On la mesure par DFT (Goertzel) sur l'enveloppe
 * RMS, en se restreignant a la portion AUDIBLE de la note (au-dessus de -25 dB
 * de son maximum) : dans la queue, le plancher de quantification ADPCM domine et
 * toute mesure de modulation relative devient du bruit.
 *
 * Le residu large bande (ancienne metrique) est domine par la decroissance
 * naturelle de la corde (mesure 45 %) et ne dit rien de la boucle.
 */
static Res analyse(const std::vector<float>& out, double loopSec) {
    const int w = static_cast<int>(0.005 * kSr);
    std::vector<double> env;
    for (size_t a = 0; a + w <= out.size(); a += w) {
        double e = 0;
        for (int j = 0; j < w; ++j) e += static_cast<double>(out[a + j]) * out[a + j];
        env.push_back(std::sqrt(e / w));
    }
    const size_t from = static_cast<size_t>(0.5 * kSr / w);
    std::vector<double> seg(env.begin() + std::min(from, env.size() - 1), env.end());
    const size_t N = seg.size();
    const size_t M = static_cast<size_t>(0.25 * kSr / w);
    std::vector<double> res(N, 0.0);
    for (size_t i = 0; i < N; ++i) {
        double s = 0; size_t c = 0;
        for (size_t k = (i > M ? i - M : 0); k <= std::min(N - 1, i + M); ++k) { s += seg[k]; ++c; }
        res[i] = seg[i] / std::max(1e-12, s / std::max<size_t>(1, c)) - 1.0;
    }
    double e = 0; for (double v : res) e += v * v;
    const double rms = std::sqrt(e / std::max<size_t>(1, N));
    double ac0 = 0; for (double v : res) ac0 += v * v;
    size_t bestLag = 0; double bestAc = 0;
    const size_t lo = static_cast<size_t>(0.04 * kSr / w);
    const size_t hi = std::min(N / 2, static_cast<size_t>(1.0 * kSr / w));
    for (size_t lag = lo; lag < hi; ++lag) {
        double a = 0;
        for (size_t i = 0; i + lag < N; ++i) a += res[i] * res[i + lag];
        a /= std::max(1.0, ac0);
        if (a > bestAc) { bestAc = a; bestLag = lag; }
    }
    const double head = seg.front(), tail = seg.back();
    double peak = 0;
    for (double v : env) peak = std::max(peak, v);

    // Remontees d'enveloppe dans la tenue audible : un piano sain decroit, donc
    // toute remontee > 0,5 dB est un artefact de rebouclage.
    // Resolution 20 ms : a 5 ms, le battement naturel de la corde (et le plancher
    // ADPCM) produisent des remontees de 0,02 dB qui noient la mesure.  L'oreille
    // entend le « dain dain » a l'echelle de la dizaine de millisecondes.
    const int dec = std::max(1, static_cast<int>(std::lround(0.020 * kSr / w)));
    std::vector<double> env2;
    for (size_t i = 0; i + dec <= env.size(); i += dec) {
        double a = 0;
        for (int k = 0; k < dec; ++k) a += env[i + k] * env[i + k];
        env2.push_back(std::sqrt(a / dec));
    }
    // Plus FORTE remontee d'enveloppe dans la tenue : l'amplitude du « dain dain ».
    // On part apres l'attaque (non monotone par nature) et on s'arrete ou la note
    // n'est plus audible (-25 dB sous son maximum).  On ne somme pas : un tremolo
    // naturel du pack cumule beaucoup de petites remontees sans que rien s'entende.
    int nr = 0; double ra = 0, mx = 0;
    const double thr = peak * 0.056;
    const size_t first = std::max<size_t>(1, static_cast<size_t>(1.5 * kSr / w) / dec);
    for (size_t i = first; i < env2.size(); ++i) {
        if (env2[i] < thr) break;
        const double d = 20.0 * std::log10(std::max(1e-12, env2[i]) / std::max(1e-12, env2[i - 1]));
        // Une remontee de plus de 12 dB en 20 ms n'est pas une enveloppe : c'est le
        // passage par un zero de la somme des couches (annulation momentanee), qui
        // fait exploser le rapport sans rien faire entendre.  On l'ecarte.
        if (d > 0.3 && d < 12.0) { ++nr; ra += d; mx = std::max(mx, d); }
    }
    return {rms, bestLag * (w / kSr),
            20 * std::log10(std::max(1e-9, tail) / std::max(1e-9, head)), mx, nr,
            nr ? ra / nr : 0.0};
}

int main(int argc, char** argv) {
    const std::string bank = argc > 1 ? argv[1] : "app/src/main/assets/pianopack/piano.bin";
    std::string mode = argc > 2 ? argv[2] : "";
    std::string dumpPath = (argc > 3 && std::string(argv[3]) == "dump")
                               ? "/tmp/hold_dump.f32" : "";
    const int dumpNote = argc > 4 ? atoi(argv[4]) : 60;
    const int dumpVel = argc > 5 ? atoi(argv[5]) : 100;
    auto bytes = readFile(bank);
    if (bytes.empty()) { fprintf(stderr, "illisible %s\n", bank.c_str()); return 1; }
    PianoEngine eng;
    eng.prepare(kSr, 512);
    const auto t0 = std::chrono::steady_clock::now();
    if (!eng.loadSampleBank(bytes.data(), bytes.size())) { fprintf(stderr, "refusee\n"); return 1; }
    const double loadMs = std::chrono::duration<double, std::milli>(
        std::chrono::steady_clock::now() - t0).count();
    if (std::getenv("PIANO_TIME"))
        fprintf(stderr, "chargement de %s : %.0f ms\n", bank.c_str(), loadMs);
    for (int i = 0; i < k_piano[1].n; ++i) eng.setNorm(i, k_piano[1].v[i]);
    const bool dumpOnly = (mode == "dumponly");
    if (mode == "noloop") eng.setLoopsEnabled(false);
    if (mode == "nobody") eng.setNorm(2, 0.f);
    if (mode == "nospace") { eng.setNorm(5, 0.f); }
    if (mode == "dry") { eng.setNorm(2, 0.f); eng.setNorm(5, 0.f); }
    if (mode == "nohammer") { eng.setNorm(0, 0.f); eng.setNorm(16, 0.f); eng.setNorm(12, 0.f); }

    // Balayage complet : le defaut depend de la note (la longueur de boucle change)
    // et de la velocite (le fondu entre couches est maximal a 50/50).
    const int notes[] = {36, 40, 44, 48, 52, 56, 60, 64, 68, 72, 76, 80, 84};
    const int vels[] = {40, 64, 100, 120};
    double worst = 0, sum = 0; int cnt = 0; int wn = 0, wv = 0; double wp = 0;
    // Metrique : plus FORTE remontee d'enveloppe dans la tenue audible, en dB.
    // C'est l'amplitude du « dain dain » (une note saine decroit, donc remontee ~0).
    long rises = 0; double riseAmp = 0;
    const int blk = 512;
    std::vector<float> io(blk * 2);
    printf("banque %s mode=%s regions=%zu\n", bank.c_str(), mode.empty() ? "-" : mode.c_str(),
           eng.bankRegions());
    printf("%-4s %-4s %8s %9s %9s\n", "note", "vel", "remontee", "periode", "queue dB");
    for (int nt : notes) {
        for (int vl : vels) {
            eng.reset();
            eng.noteOn(nt, vl);
            const long total = static_cast<long>(8.0f * kSr);
            std::vector<float> out;
            out.reserve(static_cast<size_t>(total));
            for (long done = 0; done < total; done += blk) {
                int n = static_cast<int>(std::min<long>(blk, total - done));
                std::fill(io.begin(), io.begin() + n * 2, 0.f);
                eng.process(io.data(), n);
                for (int j = 0; j < n; ++j) out.push_back(0.5f * (io[2 * j] + io[2 * j + 1]));
            }
            if (!dumpPath.empty() && nt == dumpNote && vl == dumpVel) {
                FILE* f = fopen(dumpPath.c_str(), "wb");
                if (f) { fwrite(out.data(), 4, out.size(), f); fclose(f); }
                if (dumpOnly) return 0;   // pas d'analyse : sert au pilotage externe
            }
            if (std::getenv("PIANO_MAP")) {
                const auto* r0 = eng.pickRegion(nt, vl);
                if (r0) printf("  note %3d vel %3d -> racine %3d couche %d  boucle %u ech (%.3f s)\n",
                               nt, vl, r0->root, r0->layer, r0->loopEnd - r0->loopStart,
                               (r0->loopEnd - r0->loopStart) / static_cast<double>(kSr));
            }
            const auto* rg = eng.pickRegion(nt, vl);
            const double loopSec = rg ? (rg->loopEnd - rg->loopStart) / kSr : 0.0;
            Res r = analyse(out, loopSec);
            const double pc = r.loopMod;   // remontee max d'enveloppe, en dB
            sum += pc; ++cnt;
            if (pc > worst) { worst = pc; wn = nt; wv = vl; wp = r.period; }
            rises += r.rises; riseAmp += r.riseAmp;
            printf("%-4d %-4d %7.2f dB %8.2f s %+8.1f  boucle %6.1f ms  remontees %d\n",
                   nt, vl, pc, r.period, r.tailDb, loopSec * 1000.0, r.rises);
        }
    }
    printf("RESUME %s : remontee MAX %.2f dB  (moyenne des notes %.2f dB, %d mesures)\n",
           mode.empty() ? "-" : mode.c_str(), worst, sum / std::max(1, cnt), cnt);
    return 0;
}
