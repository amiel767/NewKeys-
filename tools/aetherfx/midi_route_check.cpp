// Verifie, hors Android, la condition exacte qui decide si une note atteint un
// instrument AEther : FxMixer::soundfontPaused(strip).  C'est ce booleen que
// AudioEngine::routeEventToSource lit a chaque NoteOn.  S'il est faux, la note
// part a FluidSynth (le soundfont joue) et l'instrument reste muet.
//
//   g++ -O2 -std=c++17 -I app/src/main/cpp -o /tmp/mrc tools/aetherfx/midi_route_check.cpp
#include "aetherfx/fx_mixer.h"
#include "aetherfx/fx_registry.h"

#include <cmath>
#include <cstdio>
#include <vector>

using namespace aetherfx;

static float rmsOf(const std::vector<float>& v) {
    double s = 0.0;
    for (float x : v) s += static_cast<double>(x) * x;
    return v.empty() ? 0.f : static_cast<float>(std::sqrt(s / v.size()));
}

int main() {
    const struct { int id; const char* name; } insts[] = {
        {6, "Organ"}, {14, "Synth"}, {15, "Leads"}, {16, "Sax"}, {17, "Piano"},
    };

    int fails = 0;
    for (const auto& t : insts) {
        FxMixer fx;
        fx.prepare(48000.0, 256);

        const bool installed = fx.setInstrument(0, t.id);
        const bool paused = fx.soundfontPaused(0);
        const bool active = fx.hasActiveWork();
        const int idBack = fx.instrumentId(0);

        fx.noteOn(0, 60, 100);
        std::vector<float> buf(256 * 2, 0.f);
        float peak = 0.f;
        for (int b = 0; b < 16; ++b) {
            std::fill(buf.begin(), buf.end(), 0.f);
            fx.processStrip(0, buf.data(), 256);
            for (float x : buf) peak = std::max(peak, std::fabs(x));
        }

        const bool routingOk = installed && paused && active && idBack == t.id;
        if (!routingOk) fails++;
        std::printf("%-6s id=%2d installed=%d paused=%d active=%d idBack=%2d  peak=%.5f  %s\n",
                    t.name, t.id, installed, paused, active, idBack, peak,
                    routingOk ? "ROUTAGE OK" : "ROUTAGE CASSE");
    }

    std::printf("\n%s (%d echec)\n", fails == 0 ? "TOUT PASSE" : "ECHEC", fails);
    return fails == 0 ? 0 : 1;
}
