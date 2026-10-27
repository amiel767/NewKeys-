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

  /**
   * Bend range in semitones reached at full wheel. A source renders outside FluidSynth,
   * so the per-track "Pitch bend range" of Track Settings has to reach it here: the
   * FluidSynth sensitivity (fluid_synth_pitch_wheel_sens) is never consulted for a
   * channel the source has taken over. Default 2 semitones = one whole tone.
   */
  virtual void setPitchBendRange(int semis) { bendSemis_ = semis < 1 ? 1 : (semis > 12 ? 12 : semis); }
  int pitchBendRange() const { return bendSemis_; }

  /**
   * Load an optional sample bank into the source. Sources that have no bank ignore it and
   * return false. The bytes are copied, so the caller may free its buffer immediately.
   * This is what lets a source run hybrid (synthesis + samples) without the engine having
   * to know which source it is holding.
   */
  virtual bool loadSampleBank(const uint8_t* /*bytes*/, size_t /*size*/) { return false; }

  /** Mix of samples in the source output, 0 = pure synthesis. Sources without a bank stay at 0. */
  virtual float sampleMix() const { return 0.f; }
  virtual void setSampleMix(float /*v*/) {}
  virtual bool hasSampleBank() const { return false; }

  bool isSource() const { return true; }

 protected:
  int bendSemis_ = 2;
};

}  // namespace aetherfx
