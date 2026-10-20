// Soundfont / instrument compatibility. Used to show the big "this doesn't look like a saxophone" warning. Never blocks.
#pragma once
#include <cctype>
#include <string>

namespace aetherfx {

enum class Match { Ok, Weak, Mismatch };

inline bool containsAny(const std::string& hay, const char* const* words, int n) {
  std::string h; for (char c : hay) h.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
  for (int i = 0; i < n; ++i) if (h.find(words[i]) != std::string::npos) return true;
  return false;
}
// fxId: 5 Piano, 7 Sax, 8 Strings, 9 Guitar, 12 Brass. gmProgram: 0..127 (-1 unknown). bank: 128 = drum kit (-1 unknown).
// The preset NAME is checked first (most reliable), then the General MIDI program family, then the played key range.
inline Match matchShaper(int fxId, const std::string& presetName, int gmProgram, int bank, int keyLow = -1, int keyHigh = -1) {
  static const char* kPiano[] = {"piano", "keys", "clav", "harpsichord", "rhodes", "wurli", "epiano", "e.piano"};
  static const char* kSax[] = {"sax", "reed", "clarinet", "oboe", "bassoon"};
  static const char* kStr[] = {"violin", "viola", "cello", "string", "orchestra", "fiddle", "bass section", "ensemble"};
  static const char* kGtr[] = {"guitar", "gtr", "strat", "telecaster", "les paul", "nylon", "banjo", "ukulele", "mandolin"};
  static const char* kBrass[] = {"trumpet", "trombone", "horn", "brass", "tuba", "cornet", "flugel", "bugle"};
  static const char* kOther[][9] = {{"drum", "kit", "percussion", "perc", "snare", "kick"}, {}};
  const char* const* words = kPiano; int nw = 8; int lo[2] = {0, 7}, lo2[2] = {16, 23};
  switch (fxId) {
    case 7: words = kSax; nw = 5; lo[0] = 64; lo[1] = 71; lo2[0] = lo2[1] = -1; break;
    case 8: words = kStr; nw = 8; lo[0] = 40; lo[1] = 51; lo2[0] = lo2[1] = -1; break;
    case 9: words = kGtr; nw = 9; lo[0] = 24; lo[1] = 31; lo2[0] = lo2[1] = -1; break;
    case 12: words = kBrass; nw = 8; lo[0] = 56; lo[1] = 63; lo2[0] = lo2[1] = -1; break;
    default: break;
  }
  if (bank == 128 || containsAny(presetName, kOther[0], 6)) return Match::Mismatch;
  if (containsAny(presetName, words, nw)) return Match::Ok;
  if (gmProgram >= 0) {
    if ((gmProgram >= lo[0] && gmProgram <= lo[1]) || (lo2[0] >= 0 && gmProgram >= lo2[0] && gmProgram <= lo2[1])) return Match::Ok;
    return Match::Mismatch;
  }
  if (keyLow >= 0 && keyHigh >= 0 && keyHigh - keyLow < 12) return Match::Mismatch;  // a one-octave sound is not an instrument range
  return Match::Weak;
}

}  // namespace aetherfx
