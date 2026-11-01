// Isolation du "marteau" du preset Pure Sine : quelle couche / quel reglage le produit ?
//
//   g++ -O2 -std=c++17 -I app/src/main/cpp sine_hammer_check.cpp -o /tmp/shc && /tmp/shc
//
// Un "marteau" est un transitoire large bande a l'appui : bref, riche en hautes
// frequences, absent du sustain.  On le mesure donc en comparant l'energie aigue des
// 20 premieres ms a celle du sustain — pas une ondulation d'enveloppe, qui n'est que
// la decroissance naturelle de la note.
#include "aetherfx/fx_leads.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <vector>

using namespace aetherfx;

static const float kSr = 44100.f;
static const int kNote = 69;
static const int kParams = 33;

/** Valeurs exactes du preset "Pure Sine" (AePresets.kt, leads). */
static void pureSine(float* p) {
    for (int i = 0; i < kParams; ++i) p[i] = 0.5f;
    p[0] = 0.8f; p[1] = 0.f; p[2] = 0.f; p[3] = 0.f; p[4] = 0.1f;
    p[10] = 0.5f; p[11] = 0.3f;
    p[12] = 0.04f; p[13] = 0.f; p[14] = 0.3f; p[15] = 0.8f; p[16] = 0.25f;
    p[17] = 0.f; p[18] = 0.72f; p[19] = 0.1f; p[20] = 0.2f;
    p[21] = 0.4f; p[22] = 0.3f; p[23] = 0.f;
    p[24] = 0.5f; p[25] = 0.2f; p[26] = 0.f; p[27] = 0.5f;
    p[28] = 0.5f; p[29] = 0.1f; p[30] = 0.18f; p[31] = 0.75f;
}

static std::vector<float> render(const float* params, float seconds) {
    LeadsEngine eng;
    eng.prepare(kSr, 512);
    for (int i = 0; i < kParams; ++i) eng.setNorm(i, params[i]);
    eng.reset();
    eng.noteOn(kNote, 100);
    const int block = 512;
    std::vector<float> io(static_cast<size_t>(block) * 2), out;
    const long total = static_cast<long>(seconds * kSr);
    for (long done = 0; done < total; done += block) {
        int n = static_cast<int>(std::min<long>(block, total - done));
        std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
        eng.process(io.data(), n);
        for (int i = 0; i < n; ++i) out.push_back(io[2 * i]);
    }
    return out;
}

static double rms(const std::vector<float>& x, size_t from, size_t len) {
    double e = 0;
    size_t n = 0;
    for (size_t i = from; i < from + len && i < x.size(); ++i) { e += static_cast<double>(x[i]) * x[i]; n++; }
    return n ? std::sqrt(e / n) : 0;
}

/** Energie au-dessus de `fc` : un one-pole aigu suffit a reveler un transitoire. */
static double hfRms(const std::vector<float>& x, size_t from, size_t len, double fc) {
    const double a = std::exp(-2.0 * 3.14159265358979 * fc / kSr);
    double lp = 0, e = 0;
    size_t n = 0;
    for (size_t i = from; i < from + len && i < x.size(); ++i) {
        lp = x[i] + a * lp;
        const double hi = x[i] - lp;
        e += hi * hi;
        n++;
    }
    return n ? std::sqrt(e / n) : 0;
}

struct Row { const char* name; double hfAttack, hfSustain, ratio, jump; };

static Row measure(const char* name, const float* p) {
    std::vector<float> x = render(p, 1.2f);
    const size_t atk = static_cast<size_t>(0.020 * kSr);
    const size_t sus = static_cast<size_t>(0.600 * kSr);
    Row r;
    r.name = name;
    r.hfAttack = hfRms(x, 0, atk, 2000.0);
    r.hfSustain = hfRms(x, sus, atk, 2000.0);
    r.ratio = r.hfAttack / std::max(1e-12, r.hfSustain);
    r.jump = 0;
    for (size_t i = 1; i < atk; ++i)
        r.jump = std::max(r.jump, static_cast<double>(std::fabs(x[i] - x[i - 1])));
    return r;
}

int main() {
    float base[kParams];
    pureSine(base);

    std::vector<Row> rows;
    rows.push_back(measure("Pure Sine (exact)", base));

    // Chaque couche / reglage coupe un par un : la ligne ou le marteau disparait
    // designe le coupable.
    struct Case { const char* name; int idx; float value; };
    const Case cases[] = {
        {"sans soft-tri (couche 5)", 4, 0.f},
        {"sans sine (couche 1)", 0, 0.f},
        {"filtre passe-haut", 17, 0.5f},
        {"sans env de filtre", 20, 0.f},
        {"cutoff ferme", 18, 0.f},
        {"sans vibrato", 30, 0.f},
        {"sans reverb", 25, 0.f},
        {"sans saturation (niveau bas)", 31, 0.3f},
        {"sans resonance", 19, 0.f},
    };
    for (const Case& c : cases) {
        float q[kParams];
        for (int i = 0; i < kParams; ++i) q[i] = base[i];
        q[c.idx] = c.value;
        rows.push_back(measure(c.name, q));
    }

    // Forme d'onde brute : un transitoire percussif se VOIT, il ne se deduit pas.
    {
        float q[kParams];
        for (int i = 0; i < kParams; ++i) q[i] = base[i];
        q[26] = 0.f; q[23] = 0.f; q[25] = 0.f;
        std::vector<float> x = render(q, 0.2f);
        printf("Echantillons bruts a l'appui (tous les 8, 0 -> 14 ms)\n  ");
        for (int i = 0; i < 78; ++i) printf("%+.3f ", x[static_cast<size_t>(i) * 8]);
        printf("\n\n");
    }

    printf("%-28s %10s %10s %8s %9s\n", "variante", "HF attaque", "HF sustain", "ratio", "saut max");
    for (const Row& r : rows)
        printf("%-28s %10.6f %10.6f %8.2f %9.5f\n", r.name, r.hfAttack, r.hfSustain, r.ratio, r.jump);

    // Le saut le plus grand : OU est-il, et a quoi ressemble-t-il ?
    {
        struct Q { const char* n; int idx; float v; };
        const Q qs[] = {{"Pure Sine", -1, 0}, {"Warm Sine", -2, 0}};
        for (const Q& qq : qs) {
            float q[kParams];
            for (int i = 0; i < kParams; ++i) q[i] = base[i];
            if (qq.idx == -2) { q[4] = 0.45f; q[12] = 0.02f; q[29] = 0.22f; q[18] = 0.7f; q[20] = 0.1f; }
            std::vector<float> x = render(q, 0.3f);
            size_t at = 0; double mx = 0;
            for (size_t i = 1; i < x.size(); ++i) {
                const double d = std::fabs(x[i] - x[i - 1]);
                if (d > mx) { mx = d; at = i; }
            }
            printf("%s : saut %.5f a %.2f ms  (env. %.3f ms)\n", qq.n, mx,
                   1000.0 * at / kSr, 1000.0 * at / kSr);
            printf("   ");
            for (long i = static_cast<long>(at) - 4; i <= static_cast<long>(at) + 4; ++i)
                printf("%+.4f ", x[static_cast<size_t>(i)]);
            printf("\n");
        }
    }

    printf("\nUn marteau = ratio HF attaque/sustain eleve. La reference (premiere ligne)\n");
    printf("sert d'etalon : la ligne dont le ratio s'effondre designe la cause.\n");
    return 0;
}
