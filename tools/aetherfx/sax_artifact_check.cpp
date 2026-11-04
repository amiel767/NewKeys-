// Isole un artefact percussif (« marteau ») sur une note de sax tenue.
//
//   g++ -O2 -std=c++17 -I app/src/main/cpp -I tools/aetherfx sax_artifact_check.cpp -o /tmp/sa
//   /tmp/sa app/src/main/assets/saxpack/saxpack_alt.bin
//
// On rend la meme note trois fois — echantillon seul (blend 1), synthese seule
// (blend 0), hybride — et on imprime l'enveloppe RMS par 5 ms.  Un « marteau »
// (transitoire percussif) se lit comme un pic d'enveloppe ; un bruit de fond
// continu comme un plancher qui ne descend pas avec la note.
#include "aetherfx/fx_sax.h"
#include "demo_presets.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <string>
#include <vector>

using namespace aetherfx;

static const float kSr = 44100.f;
static const int kBlk = 256;

static std::vector<uint8_t> readFile(const std::string& p) {
    FILE* f = fopen(p.c_str(), "rb");
    if (!f) return {};
    fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
    std::vector<uint8_t> b(static_cast<size_t>(n));
    if (fread(b.data(), 1, b.size(), f) != b.size()) b.clear();
    fclose(f);
    return b;
}

static std::vector<float> render(const std::vector<uint8_t>& bin, const float* prm,
                                 int note, float seconds, float blend) {
    SaxEngine sax;
    sax.prepare(kSr, kBlk);
    if (!bin.empty()) sax.loadSampleBank(bin.data(), bin.size());
    for (int i = 0; i < 23; ++i) sax.setNorm(i, prm[i]);
    sax.setNorm(22, blend);
    std::vector<float> out;
    std::vector<float> io(kBlk * 2);
    sax.noteOn(note, 96);
    const int blocks = static_cast<int>(seconds * kSr / kBlk);
    for (int b = 0; b < blocks; ++b) {
        std::fill(io.begin(), io.end(), 0.f);
        sax.process(io.data(), kBlk);
        for (int j = 0; j < kBlk; ++j) out.push_back(0.5f * (io[2 * j] + io[2 * j + 1]));
    }
    return out;
}

static void envelope(const char* label, const std::vector<float>& x) {
    const int w = static_cast<int>(0.005 * kSr);
    printf("%s :\n  ", label);
    for (size_t a = 0; a + w <= x.size(); a += w) {
        double e = 0;
        for (int j = 0; j < w; ++j) e += static_cast<double>(x[a + j]) * x[a + j];
        printf("%.3f ", std::sqrt(e / w));
        if (((a / w) + 1) % 16 == 0) printf("\n  ");
    }
    printf("\n");
}

int main(int argc, char** argv) {
    const std::string bank = argc > 1 ? argv[1] : "app/src/main/assets/saxpack/saxpack_alt.bin";
    auto bytes = readFile(bank);
    if (bytes.empty()) { fprintf(stderr, "banque illisible : %s\n", bank.c_str()); return 1; }

    const DemoPreset* pr = nullptr;
    for (int i = 0; i < k_sax_n; ++i)
        if (std::string(k_sax[i].name) == "Jazz Ballad Alto") pr = &k_sax[i];
    if (!pr) { fprintf(stderr, "preset introuvable\n"); return 1; }

    const int note = 60;
    envelope("echantillon seul (blend 1.0)", render(bytes, pr->v, note, 1.2f, 1.0f));
    envelope("synthese seule   (blend 0.0)", render(bytes, pr->v, note, 1.2f, 0.0f));
    envelope("hybride          (blend 1.0)", render(bytes, pr->v, note, 1.2f, 1.0f));
    return 0;
}
