// Sound SOURCES (Organ, Synth) replace the track's soundfont output while selected. They are driven by note events from the audio thread.
#pragma once
#include "fx_core.h"

namespace aetherfx {

class SourceFx : public Effect {
 public:
  virtual void noteOn(int note, int velocity) = 0;
  virtual void noteOff(int note) = 0;
  virtual void allNotesOff() = 0;
  virtual void pitchBend(float /*minus1..plus1*/) {}
  bool isSource() const { return true; }
};

}  // namespace aetherfx
