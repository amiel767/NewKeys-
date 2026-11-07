// Invariant check on the FX registry — no audio, no rendering. It guards the
// CONTRACT between the registry table and the engine: a curve that disagrees with
// the engine's own mapping, or a preset with the wrong number of values, is a
// silent defect that no listening test catches.
//
//   g++ -O2 -std=c++17 -I app/src/main/cpp -o /tmp/regcheck tools/design/regcheck.cpp && /tmp/regcheck
//
// Exit code 0 and "TOUT PASSE" mean every invariant holds.
#include "aetherfx/fx_registry.h"
#include "aetherfx/fx_space.h"
#include <cmath>
#include <cstdio>
#include <string>
#include <vector>

using namespace aetherfx;

static int failures = 0;

static void expect(bool ok, const std::string& what) {
  if (!ok) { std::printf("  ECHEC  %s\n", what.c_str()); ++failures; }
}

int main() {
  const EffectInfo& space = info(0);

  // 1. The parameter count is a contract with the engine (SpaceReverb::paramCount).
  expect(space.params.size() == 15, "Space doit declarer 15 reglages");
  expect(SpaceReverb().paramCount() == (int)space.params.size(),
         "paramCount() du moteur == nombre de reglages du registre");

  // 2. Every preset carries one value per parameter, in the parameter's order.
  for (const auto& p : space.presets)
    expect(p.v.size() == space.params.size(),
           std::string("preset ") + p.name + " : une valeur par reglage");

  // 3. A frequency curve needs a positive, ordered range to be logarithmically
  //    swept; toReal would divide by zero or invert the sweep otherwise.
  for (const auto& d : space.params) {
    if (d.curve != Curve::FreqLog) continue;
    expect(d.min > 0.f && d.max > d.min,
           std::string("FreqLog ") + d.name + " : 0 < min < max");
    // The ends of the travel must land exactly on the declared bounds.
    expect(std::fabs(toReal(d, 0.f) - d.min) < 1e-3f,
           std::string("FreqLog ") + d.name + " : n=0 -> min");
    expect(std::fabs(toReal(d, 1.f) - d.max) / d.max < 1e-3f,
           std::string("FreqLog ") + d.name + " : n=1 -> max");
  }

  // 4. The registry's FreqLog must agree with the engine's own mapping. Space is
  //    the only effect with a hand-written freqHz; this pins the two together so a
  //    future edit to one cannot silently drift from the other.
  const int freqIndex[4] = {3, 5, 13, 14};
  for (int i : freqIndex)
    for (float n = 0.f; n <= 1.0001f; n += 0.05f) {
      const float a = toReal(space.params[i], n);
      const float b = SpaceReverb::freqHz(i, n);
      expect(std::fabs(a - b) / b < 1e-3f,
             std::string("registre == moteur pour ") + space.params[i].name);
    }

  // 5. A linear curve is exactly min..max, and its ends are the bounds.
  for (const auto& d : space.params) {
    if (d.curve != Curve::Linear) continue;
    expect(std::fabs(toReal(d, 0.f) - d.min) < 1e-4f &&
           std::fabs(toReal(d, 1.f) - d.max) < 1e-4f,
           std::string("Linear ") + d.name + " : n=0 -> min, n=1 -> max");
  }

  std::printf("%zu reglages, %zu presets, %d echec(s)\n",
              space.params.size(), space.presets.size(), failures);
  std::printf(failures ? "REVOIR\n" : "TOUT PASSE\n");
  return failures ? 1 : 0;
}
