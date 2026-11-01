// Le meme instrument sur deux pistes, et deux instruments differents sur deux pistes.
//
//   g++ -O2 -std=c++17 -I app/src/main/cpp multi_strip_check.cpp -o /tmp/msc && /tmp/msc
//
// Ce banc existe parce que « ca devrait marcher » n'est pas une verification : les
// moteurs Æther sont des objets par piste cote natif (FxMixer::tracks_[strip]) et les
// sessions sont indexees par piste cote Kotlin (AeManager.sessions), donc rien dans
// la conception ne s'y oppose — mais aucun test ne le prouvait, et un etat partage
// (statique, cache, singleton) aurait suffi a le casser en silence.
//
// Trois proprietes verifiees :
//   1. le meme instrument sur deux pistes, avec des presets differents, rend deux
//      sons differents (les parametres ne sont pas partages) ;
//   2. deux instruments differents sur deux pistes rendent chacun le sien ;
//   3. charger sur une piste ne change PAS la sortie d'une autre (independance).
#include "aetherfx/fx_mixer.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <vector>

using namespace aetherfx;

static const float kSr = 44100.f;

static double rms(const std::vector<float>& x) {
    double e = 0;
    for (float v : x) e += static_cast<double>(v) * v;
    return std::sqrt(e / std::max<size_t>(1, x.size()));
}

/** Correlation : 1.0 = meme signal.  Deux pistes qui rendraient la meme chose la trahissent. */
static double corr(const std::vector<float>& a, const std::vector<float>& b) {
    size_t n = std::min(a.size(), b.size());
    if (!n) return 0;
    double ma = 0, mb = 0;
    for (size_t i = 0; i < n; ++i) { ma += a[i]; mb += b[i]; }
    ma /= n; mb /= n;
    double sa = 0, sb = 0, sab = 0;
    for (size_t i = 0; i < n; ++i) {
        double da = a[i] - ma, db = b[i] - mb;
        sa += da * da; sb += db * db; sab += da * db;
    }
    return (sa <= 0 || sb <= 0) ? 0 : sab / std::sqrt(sa * sb);
}

/** Joue une note tenue puis relachee, et rend le signal de la piste. */
static std::vector<float> play(FxMixer& mix, int strip, int note, float seconds) {
    const int block = 512;
    std::vector<float> io(static_cast<size_t>(block) * 2), out;
    long total = static_cast<long>(seconds * kSr);
    mix.noteOn(strip, note, 100);
    for (long done = 0; done < total; done += block) {
        int n = static_cast<int>(std::min<long>(block, total - done));
        std::fill(io.begin(), io.begin() + static_cast<size_t>(n) * 2, 0.f);
        mix.processStrip(strip, io.data(), n);
        out.insert(out.end(), io.begin(), io.begin() + static_cast<size_t>(n) * 2);
    }
    mix.noteOff(strip, note);
    return out;
}

static int fails = 0;
static void check(bool ok, const char* what) {
    printf("  %-58s %s\n", what, ok ? "OK" : "ECHEC");
    if (!ok) fails++;
}

int main() {
    // --- 1. Meme instrument, deux pistes, deux presets ---
    {
        FxMixer mix;
        mix.prepare(kSr, 512);
        mix.setInstrument(0, FX_ORGAN);
        mix.setInstrument(1, FX_ORGAN);
        // Presets opposes sur un parametre qui agit SANS banque : les drawbars.
        // (Un test sur le Blend du sax ne prouverait rien ici : sans pack charge, le
        // Blend est inoperant et les deux pistes rendraient la meme synthese.)
        for (int d = 0; d < 9; ++d) {
            mix.setInstrumentParam(0, d, 0.0f);   // tous les drawbars tires
            mix.setInstrumentParam(1, d, 1.0f);   // tous les drawbars pousses
        }

        std::vector<float> a = play(mix, 0, 60, 0.8f);
        std::vector<float> b = play(mix, 1, 60, 0.8f);
        printf("Meme instrument sur 2 pistes (orgue), drawbars opposes\n");
        printf("  rms piste0 %.4f | rms piste1 %.4f | corr %.3f\n", rms(a), rms(b), corr(a, b));
        check(rms(a) > 1e-4 && rms(b) > 1e-4, "les deux pistes rendent du son");
        check(corr(a, b) < 0.99, "les deux presets sont distincts (params non partages)");
    }

    // --- 2. Deux instruments differents sur deux pistes ---
    {
        FxMixer mix;
        mix.prepare(kSr, 512);
        mix.setInstrument(0, FX_ORGAN);
        mix.setInstrument(1, FX_LEADS);
        std::vector<float> a = play(mix, 0, 60, 0.8f);
        std::vector<float> b = play(mix, 1, 60, 0.8f);
        printf("Deux instruments differents sur 2 pistes (orgue, leads)\n");
        printf("  rms orgue %.4f | rms leads %.4f | corr %.3f\n", rms(a), rms(b), corr(a, b));
        check(rms(a) > 1e-4 && rms(b) > 1e-4, "chacun rend du son sur sa piste");
        check(corr(a, b) < 0.9, "ce sont bien deux instruments differents");
    }

    // --- 3. Independance : charger ailleurs ne touche pas la piste deja chargee ---
    {
        FxMixer solo;
        solo.prepare(kSr, 512);
        solo.setInstrument(0, FX_SAX_AE);
        std::vector<float> alone = play(solo, 0, 62, 0.8f);

        FxMixer both;
        both.prepare(kSr, 512);
        both.setInstrument(0, FX_SAX_AE);
        // Pistes 1..3 chargees APRES : c'est le cas « on ajoute des instruments ».
        both.setInstrument(1, FX_ORGAN);
        both.setInstrument(2, FX_PIANO_AE);
        both.setInstrument(3, FX_SYNTH);
        std::vector<float> together = play(both, 0, 62, 0.8f);

        printf("Independance : la piste 0 chargee seule, puis 3 autres pistes ajoutees\n");
        printf("  rms seule %.5f | rms apres ajout %.5f | corr %.5f\n",
               rms(alone), rms(together), corr(alone, together));
        check(corr(alone, together) > 0.999, "la sortie de la piste 0 est inchangee");
    }

    printf("\n%s (%d echec%s)\n", fails ? "ECHEC" : "TOUT PASSE", fails, fails > 1 ? "s" : "");
    return fails ? 1 : 0;
}
