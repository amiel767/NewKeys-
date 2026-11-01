// Le moteur est propre : reste a trouver CE QU'ON ENTEND derriere le sine.
//
//   g++ -O2 -std=c++17 -I app/src/main/cpp sine_spectrum_check.cpp -o /tmp/ssp && /tmp/ssp
//
// Un sinus pur ne doit avoir qu'UNE raie.  Toute autre composante est ce "truc en
// arriere-plan" : une raie parasite (souffle, aliasing, fuite de table) ou une
// modulation lente (battement, vibrato, derive).
#include "aetherfx/fx_leads.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <vector>

using namespace aetherfx;

static const float kSr = 44100.f;
static const int kNote = 69;  // A4 = 440 Hz
static const int kParams = 33;

/** Transformee naive : une seule raie, la resolution n'a pas besoin d'etre fine. */
static void spectrum(const std::vector<float>& x, std::vector<double>& mag) {
    const int n = static_cast<int>(x.size());
    mag.assign(n / 2, 0.0);
    for (int k = 0; k < n / 2; ++k) {
        double re = 0, im = 0;
        const double w = -2.0 * 3.14159265358979 * k / n;
        for (int i = 0; i < n; ++i) { re += x[i] * std::cos(w * i); im += x[i] * std::sin(w * i); }
        mag[k] = std::sqrt(re * re + im * im) / n;
    }
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

/** Amplitude dans une bande etroite : sert a suivre une raie dans le temps. */
static double band(const std::vector<float>& x, size_t from, size_t len, double f) {
    double re = 0, im = 0;
    const double w = 2.0 * 3.14159265358979 * f / kSr;
    for (size_t i = 0; i < len && from + i < x.size(); ++i) {
        re += x[from + i] * std::cos(w * i);
        im += x[from + i] * std::sin(w * i);
    }
    return 2.0 * std::sqrt(re * re + im * im) / std::max<size_t>(1, len);
}

int main() {
    // "Pure Sine" : seule la couche sine est ouverte (WaveA 0,8 ; les 4 autres a 0).
    float pure[kParams];
    for (int i = 0; i < kParams; ++i) pure[i] = 0.5f;
    pure[0] = 0.8f; pure[1] = 0.f; pure[2] = 0.f; pure[3] = 0.f; pure[4] = 0.1f;
    pure[28] = 0.5f; pure[29] = 0.1f; pure[31] = 0.75f;

    // 0,5 s de sustain, loin de l'attaque : ce qu'on entend "en arriere-plan" vit la.
    std::vector<float> x = render(pure, 2.0f);
    std::vector<float> sustain(x.begin() + static_cast<size_t>(1.0 * kSr),
                              x.begin() + static_cast<size_t>(1.5 * kSr));

    std::vector<double> mag;
    spectrum(sustain, mag);

    // Les harmoniques de 440 Hz sont legitimes (la couche soft-tri en ajoute) ; ce qui
    // compte est ce qui n'est NI 440, NI un multiple, NI tres basse frequence.
    double peak = 0;
    for (double m : mag) peak = std::max(peak, m);
    printf("Spectre du sustain (440 Hz = 1.00)\n");
    int shown = 0;
    for (int k = 2; k < static_cast<int>(mag.size()) && shown < 8; ++k) {
        const double f = k * kSr / sustain.size();
        if (f < 20) continue;                       // continu deja mesure a zero
        const bool harmonic = std::fabs(f / 440.0 - std::round(f / 440.0)) < 0.03;
        if (harmonic) continue;
        if (mag[k] < peak * 0.002) continue;        // -54 dB : inaudible
        printf("  %7.1f Hz  %.4f  (%+.1f dB)  %s\n", f, mag[k] / peak,
               20 * std::log10(mag[k] / peak), f < 30 ? "TRES BASSE" : "non harmonique");
        shown++;
    }
    if (!shown) printf("  aucune composante non harmonique au-dessus de -54 dB\n");

    // Modulation d'amplitude : un battement se lit dans l'enveloppe lente.
    printf("Enveloppe (RMS par tranche de 50 ms)\n");
    const size_t w = static_cast<size_t>(0.05 * kSr);
    double lo = 1e9, hi = 0;
    for (size_t s = 0; s + w <= sustain.size(); s += w) {
        double e = 0;
        for (size_t i = 0; i < w; ++i) e += static_cast<double>(sustain[s + i]) * sustain[s + i];
        const double r = std::sqrt(e / w);
        lo = std::min(lo, r); hi = std::max(hi, r);
    }
    printf("  min %.5f | max %.5f | ondulation %.2f dB\n", lo, hi,
           20 * std::log10(hi / std::max(1e-9, lo)));

    // L'attaque, au detail : c'est la que vit un transitoire percussif ("marteau").
    printf("Attaque (fenetres de 2 ms, effets coupes)\n");
    {
        float q[kParams];
        for (int i = 0; i < kParams; ++i) q[i] = pure[i];
        q[26] = 0.f; q[23] = 0.f; q[25] = 0.f;
        std::vector<float> r = render(q, 0.3f);
        const size_t aw = static_cast<size_t>(0.002 * kSr);
        printf("  ");
        for (size_t st = 0; st + aw <= static_cast<size_t>(0.04 * kSr); st += aw) {
            double e = 0;
            for (size_t i = 0; i < aw; ++i) e += static_cast<double>(r[st + i]) * r[st + i];
            printf("%.4f ", std::sqrt(e / aw));
        }
        printf("\n");
    }

    // Forme de l'ondulation dans le temps : un battement, une derive lente et un
    // balayage de filtre ne se corrigent pas de la meme facon.
    printf("Enveloppe dans le temps (50 ms), 1,0 -> 2,0 s\n");
    {
        float q[kParams];
        for (int i = 0; i < kParams; ++i) q[i] = pure[i];
        q[26] = 0.f; q[23] = 0.f; q[25] = 0.f;   // ni chorus, ni delay, ni reverb
        std::vector<float> r = render(q, 2.0f);
        printf("  ");
        for (size_t st = static_cast<size_t>(1.0 * kSr);
             st + w <= static_cast<size_t>(1.9 * kSr); st += w) {
            double e = 0;
            for (size_t i = 0; i < w; ++i) e += static_cast<double>(r[st + i]) * r[st + i];
            printf("%.3f ", std::sqrt(e / w));
        }
        printf("\n");
    }

    // L'ondulation vient-elle des effets (chorus/delay/reverb) ou du coeur ?
    // Couper les trois tranche la question : un reste d'ondulation est un defaut,
    // un retour au calme signifie que ce qu'on entend est l'effet, donc voulu.
    printf("Ondulation, effets coupes un par un\n");
    struct Case { const char* name; int idx; };
    const Case cases[] = {{"reference (tous effets)", -1}, {"sans chorus", 26},
                          {"sans delay", 23}, {"sans reverb", 25},
                          {"sans chorus ni delay", -2}};
    for (const Case& c : cases) {
        float q[kParams];
        for (int i = 0; i < kParams; ++i) q[i] = pure[i];
        if (c.idx >= 0) q[c.idx] = 0.f;
        if (c.idx == -2) { q[26] = 0.f; q[23] = 0.f; }
        std::vector<float> r = render(q, 2.0f);
        std::vector<float> sus(r.begin() + static_cast<size_t>(1.0 * kSr),
                               r.begin() + static_cast<size_t>(1.5 * kSr));
        double lo = 1e9, hi = 0;
        for (size_t st = 0; st + w <= sus.size(); st += w) {
            double e = 0;
            for (size_t i = 0; i < w; ++i) e += static_cast<double>(sus[st + i]) * sus[st + i];
            const double rr = std::sqrt(e / w);
            lo = std::min(lo, rr); hi = std::max(hi, rr);
        }
        printf("  %-22s ondulation %5.2f dB  (rms max %.5f)\n", c.name,
               20 * std::log10(hi / std::max(1e-9, lo)), hi);
    }

    // Derive de hauteur : le LFO de drift (0,12..0,47 Hz, 1,5..4 cents) se mesure en
    // suivant la raie fondamentale tranche par tranche.
    printf("Hauteur de la fondamentale dans le temps\n");
    const size_t pw = static_cast<size_t>(0.25 * kSr);
    double fmin = 1e9, fmax = 0;
    for (size_t s = 0; s + pw <= sustain.size(); s += pw) {
        // recherche fine autour de 440 Hz
        double best = 0, bestF = 440;
        for (double f = 430; f < 451; f += 0.25) {
            const double a = band(sustain, s, pw, f);
            if (a > best) { best = a; bestF = f; }
        }
        fmin = std::min(fmin, bestF); fmax = std::max(fmax, bestF);
    }
    printf("  min %.2f Hz | max %.2f Hz | ecart %.1f cents\n", fmin, fmax,
           1200 * std::log2(fmax / std::max(1.0, fmin)));
    return 0;
}
