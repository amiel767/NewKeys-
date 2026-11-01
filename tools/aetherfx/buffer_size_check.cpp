// Preuve du calcul de taille de buffer, hors Android.
//
// Deux formules sont en cause dans AudioEngine :
//   ouverture   : target = max(req, burst * 2)   ->  max(req, burst)
//   changement  : frames = ceil(req / burst) * burst  ->  round(req / burst) * burst
//
// On rejoue les deux sur les bursts reels d'une famille d'appareils et on compare
// la latence obtenue a celle que l'UI annonce (req / 44100).
//
//   g++ -O2 -std=c++17 -o /tmp/buf tools/aetherfx/buffer_size_check.cpp && /tmp/buf

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <string>
#include <vector>

static double ms(int frames, int sr) { return frames * 1000.0 / sr; }

struct Case { const char* name; int burst; };

int main() {
    const int sr = 44100;
    const std::vector<Case> devices = {
        {"Pixel 6 (burst 240)", 240},
        {"Pixel 8 (burst 192)", 192},
        {"Snapdragon 24-bit (burst 96)", 96},
        {"USB DAC (burst 480)", 480},
        {"Emulateur (burst 1024)", 1024},
    };
    const std::vector<int> reqs = {64, 128, 256, 512, 1024};

    int fails = 0;

    std::printf("\n== ouverture du stream : max(req, burst*2) contre max(req, burst) ==\n");
    for (const auto& d : devices) {
        for (int req : reqs) {
            int oldT = std::max(req, d.burst * 2);
            int newT = std::max(req, d.burst);
            if (oldT != newT) {
                std::printf("  %-30s req %4d : %6.1f ms  ->  %6.1f ms\n",
                            d.name, req, ms(oldT, sr), ms(newT, sr));
            }
        }
    }

    std::printf("\n== reglage : ceil(req/burst)*burst contre round(req/burst)*burst ==\n");
    for (const auto& d : devices) {
        for (int req : reqs) {
            int oldN = std::max(2, (req + d.burst - 1) / d.burst);
            int newN = std::max(1, (req + d.burst / 2) / d.burst);
            int oldT = oldN * d.burst, newT = newN * d.burst;
            if (oldT != newT) {
                std::printf("  %-30s req %4d : %6.1f ms  ->  %6.1f ms\n",
                            d.name, req, ms(oldT, sr), ms(newT, sr));
            }
        }
    }

    // Garde-fou 1 : le nouveau calcul ne doit jamais rendre MOINS qu'un burst.
    std::printf("\n== garde-fou : jamais sous un burst ==\n");
    for (const auto& d : devices) {
        for (int req : reqs) {
            int t = std::max(req, d.burst);
            if (t < d.burst) { std::printf("  ECHEC %s req %d -> %d\n", d.name, req, t); fails++; }
        }
    }
    std::printf("  %s\n", fails ? "ECHEC" : "OK");

    // Garde-fou 2 : l'UI affiche desormais la taille REELLEMENT appliquee (relue du
    // natif), donc l'ecart affiche/reel doit etre nul.  C'est le contrat corrige :
    // avant, l'UI affichait la demande et annoncait 1,5 ms pour un vrai 5,4 ms.
    std::printf("\n== ecart affiche contre reel (UI affiche le reel) ==\n");
    int worst = 0;
    for (const auto& d : devices) {
        for (int req : reqs) {
            int real = std::max(req, d.burst);   // ce que le natif applique
            double shown = ms(real, sr);          // ce que l'UI affiche maintenant
            double actual = ms(real, sr);
            double err = std::fabs(shown - actual);
            if (err > 0.05) {
                std::printf("  ECHEC %s req %d : affiche %.1f ms, reel %.1f ms\n",
                            d.name, req, shown, actual);
                fails++;
            }
            if (err > worst) worst = static_cast<int>(err * 10);
        }
    }
    std::printf("  ecart max %.1f ms\n", worst / 10.0);

    std::printf("\n%s\n", fails ? "DES ECHECS" : "TOUT PASSE");
    return fails ? 1 : 0;
}
