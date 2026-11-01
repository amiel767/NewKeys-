// Clic en arriere-plan du preset "Pure Sine" (leads).
//
//   g++ -O2 -std=c++17 -I app/src/main/cpp sine_click_check.cpp -o /tmp/scc && /tmp/scc
//
// Le preset n'active QUE la couche sine (WaveA = 0,8, les quatre autres a 0), donc
// toute discontinuite mesuree ici vient de la couche sine elle-meme ou de sa chaine,
// jamais d'une autre couche.
//
// Deux signatures a distinguer, parce qu'elles n'ont pas la meme cause :
//   - un saut echantillon a echantillon (max|d|) : discontinuite du signal ;
//   - une valeur moyenne non nulle : composante continue, qui fait cliquer le fader
//     et les transitions de gain en aval.
#include "aetherfx/fx_leads.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <vector>

using namespace aetherfx;

static const float kSr = 44100.f;
static const int kNote = 69;  // A4, la reference
static const int kParams = 33;

static int fails = 0;
static void check(bool ok, const char* what) {
    printf("  %-56s %s\n", what, ok ? "OK" : "ECHEC");
    if (!ok) fails++;
}

struct Take {
    std::vector<float> l;
    double dc = 0, rms = 0, jump = 0, jumpAfterAttack = 0;
    int clipped = 0;
};

static Take run(const float* params, float seconds, bool gate) {
    LeadsEngine eng;
    eng.prepare(kSr, 512);
    for (int i = 0; i < kParams; ++i) eng.setNorm(i, params[i]);
    eng.reset();
    eng.noteOn(kNote, 100);

    Take t;
    const int block = 512;
    std::vector<float> io(static_cast<size_t>(block) * 2);
    const long total = static_cast<long>(seconds * kSr);
    const long releaseAt = gate ? total / 2 : total;
    for (long done = 0; done < total; done += block) {
        if (gate && done >= releaseAt) { eng.noteOff(kNote); }
        int n = static_cast<int>(std::min<long>(block, total - done));
        std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
        eng.process(io.data(), n);
        for (int i = 0; i < n; ++i) t.l.push_back(io[2 * i]);
    }
    double sum = 0, e = 0;
    for (size_t i = 0; i < t.l.size(); ++i) { sum += t.l[i]; e += static_cast<double>(t.l[i]) * t.l[i]; }
    t.dc = sum / std::max<size_t>(1, t.l.size());
    t.rms = std::sqrt(e / std::max<size_t>(1, t.l.size()));
    // Les 25 premieres ms sont l'attaque : c'est la que vit le clic d'origine, et
    // c'est justement la fenetre ou le fade doit contenir le saturateur.
    const size_t win = static_cast<size_t>(0.025 * kSr);
    for (size_t i = 1; i < t.l.size(); ++i) {
        const double d = std::fabs(t.l[i] - t.l[i - 1]);
        t.jump = std::max(t.jump, d);
        if (i < win) t.jumpAfterAttack = std::max(t.jumpAfterAttack, d);
    }
    for (float v : t.l) if (std::fabs(v) > 0.95f) t.clipped++;
    return t;
}

int main() {
    // "Pure Sine" tel qu'il est dans AePresets.kt : seule la couche sine est ouverte.
    float pure[kParams];
    for (int i = 0; i < kParams; ++i) pure[i] = 0.5f;
    pure[0] = 0.8f;    // WaveA : sine
    pure[1] = 0.f;     // pulse
    pure[2] = 0.f;     // saw
    pure[3] = 0.f;     // super-saw
    pure[4] = 0.1f;    // soft triangle
    pure[28] = 0.1f;   // mode mono (le preset d'origine)
    pure[29] = 0.18f;  // release
    pure[31] = 0.75f;  // level

    printf("Pure Sine, note tenue 1,5 s\n");
    Take held = run(pure, 1.5f, false);
    printf("  dc %.6f | rms %.4f | saut max %.5f | ecretes %d\n",
           held.dc, held.rms, held.jump, held.clipped);
    check(std::fabs(held.dc) < 0.002, "pas de composante continue (pas de clic de fond)");
    check(held.jump < 0.05, "aucune discontinuite echantillon a echantillon");
    check(held.clipped == 0, "rien n'est ecrete");
    check(held.rms > 1e-3, "le preset rend bien du son");

    printf("Pure Sine, note jouee puis relachee\n");
    Take gated = run(pure, 1.5f, true);
    printf("  dc %.6f | saut max %.5f | ecretes %d\n", gated.dc, gated.jump, gated.clipped);
    check(std::fabs(gated.dc) < 0.002, "pas de composante continue apres relachement");
    check(gated.clipped == 0, "le relachement n'ecrete pas");

    // Controle : les autres presets sine du catalogue, pour voir si le defaut est
    // propre a "Pure Sine" ou commun a toute la famille.
    printf("Controle : 4 autres presets sine\n");
    struct P { const char* name; float p[kParams]; };
    std::vector<P> others;
    {
        P a; a.name = "Warm Sine Lead";
        for (int i = 0; i < kParams; ++i) a.p[i] = 0.5f;
        a.p[0] = 0.8f; a.p[1] = 0; a.p[2] = 0; a.p[3] = 0; a.p[4] = 0.45f;
        a.p[28] = 0.1f; a.p[29] = 0.22f; a.p[31] = 0.75f;
        others.push_back(a);
    }
    {
        P a; a.name = "Worship Sine";
        for (int i = 0; i < kParams; ++i) a.p[i] = 0.5f;
        a.p[0] = 0.8f; a.p[1] = 0; a.p[2] = 0; a.p[3] = 0; a.p[4] = 0.3f;
        a.p[28] = 0.5f; a.p[29] = 0.1f; a.p[31] = 0.75f;
        others.push_back(a);
    }
    {
        P a; a.name = "Cool Sine";
        for (int i = 0; i < kParams; ++i) a.p[i] = 0.5f;
        a.p[0] = 0.8f; a.p[1] = 0; a.p[2] = 0; a.p[3] = 0; a.p[4] = 0.35f;
        a.p[28] = 0.1f; a.p[29] = 0.2f; a.p[31] = 0.75f;
        others.push_back(a);
    }
    {
        P a; a.name = "Air Sine";
        for (int i = 0; i < kParams; ++i) a.p[i] = 0.5f;
        a.p[0] = 0.8f; a.p[1] = 0; a.p[2] = 0; a.p[3] = 0; a.p[4] = 0.2f;
        a.p[28] = 0.1f; a.p[29] = 0.25f; a.p[31] = 0.75f;
        others.push_back(a);
    }
    for (const P& p : others) {
        Take t = run(p.p, 1.2f, false);
        printf("  %-16s dc %+.6f | saut max %.5f | rms %.4f\n", p.name, t.dc, t.jump, t.rms);
    }

    printf("\n%s (%d echec%s)\n", fails ? "ECHEC" : "TOUT PASSE", fails, fails > 1 ? "s" : "");
    return fails ? 1 : 0;
}
