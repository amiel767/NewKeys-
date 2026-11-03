// Renders the same short phrase on a real preset with two builds of the engine, so the note-on
// "tac" can be heard rather than inferred. Writes raw stereo float files; demo_encode_leads.py turns
// them into MP3.
//
// Build (twice, into different output dirs):
//   python3 tools/aetherfx/extract_leads_presets.py > /tmp/leads_presets.h
//   g++ -O2 -std=c++17 -I /tmp -I <tree> tools/aetherfx/demo_leads.cpp -o /tmp/dl -DOUTDIR='"/tmp/old"'
//   g++ -O2 -std=c++17 -I /tmp -I app/src/main/cpp tools/aetherfx/demo_leads.cpp -o /tmp/dl -DOUTDIR='"/tmp/new"'

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "leads_presets.h"
#include "aetherfx/fx_leads.h"

#ifndef OUTDIR
#define OUTDIR "/tmp/demo"
#endif

using namespace aetherfx;
static constexpr double SR = 48000.0;
static constexpr int BLK = 256;

static const LeadPreset* find(const char* name) {
  for (int i = 0; i < kLeadPresetCount; ++i)
    if (std::strcmp(kLeadPresets[i].name, name) == 0) return &kLeadPresets[i];
  return nullptr;
}

// One note per beat, the way the artifact was reported: a lead line, not a held chord. Repeated
// notes are included because that is where the mono retrigger used to step.
static const int kPhrase[] = {60, 60, 64, 60, 67, 64, 62, 60};
static const int kBeats = 8;

static void render(const char* presetName, const char* outPath) {
  const LeadPreset* pr = find(presetName);
  if (!pr) { std::printf("preset %s not found\n", presetName); return; }

  LeadsEngine eng;
  eng.prepare(SR, BLK);
  for (int i = 0; i < 33; ++i) eng.setNorm(i, pr->v[i]);
  eng.reset();

  const int beat = static_cast<int>(0.42 * SR);
  const int total = beat * kBeats + static_cast<int>(0.8 * SR);
  std::vector<float> out;
  std::vector<float> buf(static_cast<size_t>(BLK) * 2);
  int done = 0, next = 0;
  while (done < total) {
    if (next < kBeats && done >= next * beat) {
      if (next > 0) eng.noteOff(kPhrase[next - 1]);
      eng.noteOn(kPhrase[next], 100);
      next++;
    }
    // Release each note a little before the next one, like a hand would.
    if (next > 0 && next <= kBeats) {
      const int relAt = (next - 1) * beat + static_cast<int>(0.34 * SR);
      if (done >= relAt && done < relAt + BLK) eng.noteOff(kPhrase[next - 1]);
    }
    std::fill(buf.begin(), buf.end(), 0.f);
    eng.process(buf.data(), BLK);
    for (int i = 0; i < BLK && static_cast<int>(out.size()) < total; ++i) out.push_back(buf[static_cast<size_t>(i) * 2]);
    done += BLK;
  }

  std::string p = std::string(OUTDIR) + "/" + outPath + ".f32";
  FILE* f = std::fopen(p.c_str(), "wb");
  if (!f) { std::printf("cannot write %s\n", p.c_str()); return; }
  std::fwrite(out.data(), sizeof(float), out.size(), f);
  std::fclose(f);
  std::printf("wrote %s (%zu samples)\n", p.c_str(), out.size());
}

int main() {
  render("Pure Sine", "pure_sine");
  render("Mono Saw", "mono_saw");
  render("Gospel Supersaw", "gospel_supersaw");
  render("Praise Lead", "praise_lead");
  return 0;
}
