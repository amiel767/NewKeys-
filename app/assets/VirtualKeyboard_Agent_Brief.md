# Virtual Keyboard — Integration Brief (for the coding agent)

## 0. Read this first

- This brief covers the **Virtual Keyboard container** at the bottom of the Mixer page, and the **theme picker** in the Settings page.
- You receive `VirtualKeyboard.kt` (stateless UI + pure logic) and `VirtualKeyboardPreview.jsx` (the approved look and motion). If the code and the preview disagree, **the preview wins**; report the difference.
- You must **not** change the audio engine, the MIDI engine, the MIDI mapping engine, or the semantics of the existing Note range setting.
- Start with the **diagnostic report (section 9)**. Do not implement before it has been reviewed.

## 1. What the component is

One container, with a fixed color, holding three things:

1. **Pitch bend** strip on the left.
2. **Layer lines**: one line per **active track** (power on), in track order, in the track's color, flat (no gradient).
3. **Keyboard** from C1 (MIDI 24) to C8 (MIDI 108), 85 keys.

### Behavior

**Layer lines**
- At rest the lines are thin bands stacked just above the keys. Faint octave marks sit behind them (one at each C).
- **A tap on the thin lines unfolds them** to full size: a staggered spring, line 1 first, with a small overshoot. Chips (low note, track name, high note) and the two white handles appear when each line is about 60% open.
- The container grows upward while the lines unfold. Folding runs in reverse order.
- It **folds back by itself after 5 quiet seconds** (any touch inside the container restarts the delay; while a line is being dragged it never folds). A small grip at the top center also folds it.
- Each line shows the range of its track from `low` to `high`. Outside the range the line is empty.
- **Drag a handle** to change a limit (the limits never cross: at least one semitone apart). **Drag the bar** to slide the whole range (the width is kept, the range stays inside C1–C8).
- **Halo**: while a line is being moved, a flat halo of that line's color lights the keys of its zone. No outline, no gradient. It fades out (160 ms) when the finger lifts. The halo is **not** shown when playing keys.

**Pitch bend**
- Vertical strip, range −1 to +1. It springs back to 0 on release (spring, slight overshoot). The strip is taller when the lines are unfolded.

**Keyboard**
- NeoTune-style relief: white keys with a soft vertical gradient and a thin top highlight, black keys with a top reflection, a lighter front face and a cast shadow on the white keys, a slightly darker lip at the bottom of the white keys. **No horizontal indicators on the keys.**
- Multi-touch: every finger follows its own key (chords, glissando). A note is released only when no finger holds it.
- Pressed keys:
  - **Color** (default): the key takes the theme's flat pressed color and sinks by 1 dp.
  - **LED**: same color plus a short soft glow behind the key.

### Hard rules
- **The container never changes color.** Only the keys follow the theme.
- The container background is **opaque**, and the shadow is applied before it. Never place `Modifier.shadow` under a translucent fill (that is what produced the grey box on the Setlists cards).
- Line colors are the track colors, flat. No gradients on the lines.

## 2. Files and where they go

- `VirtualKeyboard.kt` → keep it as one file in the UI package (adapt the `package` line). It contains: `KeyboardLine`, `KeyboardTheme`, `KeyboardThemes`, `KeyboardChrome`, `VirtualKeyboardActions`, `KeyboardLogic`, `VirtualKeyboardContainer`, `KeyboardKeys`, `KeyboardThemePicker`, `KeyboardThemeSwatch`, and two `@Preview`.
- The new container **replaces** the current virtual keyboard container (keyboard, pitch bend, and the layer lines if they exist). Remove the old composables once the new one works. Do not leave dead code.

## 3. Wiring table

| Parameter / action | Source in the app |
|---|---|
| `lines` | Active tracks only (power on), in track order. `id` = track id, `name` = soundfont/track name shown on the strip, `color` = the track color already used by the strip, `low`/`high` = the track's **Note range**. |
| `onLineRangeChange(id, low, high)` | Writes the track's Note range. It is the **same value** as in Track settings → Note range (single source of truth, same storage). Changing it in either place updates the other. |
| `expanded` + `onExpandedChange` | UI state in the Mixer view model. **Not persisted.** Starts folded. |
| `pressedNotes` | The set of notes currently held on the virtual keyboard (from the touch callbacks). If the app also lights keys for external MIDI input, merge both sets. |
| `onKeyDown(note)` / `onKeyUp(note)` | The **existing** note on / note off path of the virtual keyboard, with the **existing** velocity logic. Do not add a new path. |
| `onPitchBend(value)` | The existing pitch bend path. `value` is −1f…+1f; map it with the mapping already used today. It is called while dragging and during the spring back. |
| `theme` | `KeyboardThemes.byId(prefs "vkb_theme")`. Default `classic`. |
| `pressStyle` | `prefs "vkb_press_style"` (`"color"` or `"led"`). Default `color`. |
| `chrome` | `KeyboardChrome(frame, well, accent)`: take the values from the app palette (the same neutral frame and well used by the other containers). **Never from the keyboard theme.** The defaults in the file match the approved preview. |

Notes:
- The Note range of a track that is **not** active has no line. Its value is untouched.
- If the number of active tracks changes, the lines list simply changes (add/remove a line). No special animation is required for that.
- If there are no active tracks, show the keyboard and pitch bend with an empty lines area (keep the folded height).

## 4. Settings: theme picker (UI section)

Add a **"Virtual keyboard"** block in **Settings → UI section** (the "Interface" tab of the Settings page).

- Use `KeyboardThemePicker`: a grid of 9 theme previews (3 per row), each a small keyboard with the theme's real key colors and its pressed color on the second key, with the theme name under it. The selected one has an accent border. Under the grid, a "Pressed keys" row with **Color | LED**.
- **It must look and behave like the rest of the Settings page**, not like a separate widget. Replace the private styling in `KeyboardThemePicker` and `PressStyleSegment` with the Settings page's own components: the same section header, the same row layout and spacing, the same segmented control, the same press feedback and haptics. Only the swatches themselves are specific.
- Changes apply **immediately** and persist (no confirm button).
- The Settings page "Reset" for the UI section returns the keyboard to **Classic** and **Color**.
- The frame of every swatch is the same color on purpose: the frame never follows the theme.

### Themes (already defined in `KeyboardThemes`)
Classic (default), Blue, Cream, Red, Dark, Violet, Emerald, Sunset, Graphite. Do not rename them or change their values without asking.

## 5. Persistence

Use the existing `SharedPreferences` file `livekeys_mixer_prefs`. New keys only:

- `vkb_theme` — String, theme id, default `"classic"`.
- `vkb_press_style` — String, `"color"` or `"led"`, default `"color"`.

Track ranges use the **existing** Note range keys. Do not create new ones.

## 6. Performance

- `KeyboardKeys` redraws when `pressedNotes` or the theme changes. Keep `pressedNotes` as an immutable `Set<Int>`; avoid creating it on every frame.
- The pitch bend value is local to the strip (an animation); do not recompose the whole container on every value.
- No `animateContentSize` on the container: the height comes from the lines' own springs.
- Do not wrap the container in a `graphicsLayer` with alpha (it flattens the shadow).

## 7. Out of scope (do not do now)

- MIDI mapping of the keyboard or the pitch bend (decided later).
- Any change to sound, velocity curves, or the engine.
- 16-pad or DrumPad work.
- Adding gradients, outlines, glow on the lines, or a halo when playing keys.

## 8. Acceptance checklist (check each on a device, with a screenshot)

1. Folded: thin lines visible above the keys, one per active track, in track colors.
2. Tap on the lines: they unfold in order with a small overshoot; chips and handles appear after; the container grows upward.
3. Quiet for 5 s: it folds back in reverse order. Dragging a line prevents folding. The grip folds it.
4. Handle drag changes one limit and never crosses the other. Bar drag slides the range and stops at C1 and C8.
5. A flat halo of the line's color lights the zone **only while dragging**, then fades out. No outline.
6. Track settings → Note range shows the same value after a change here, and vice versa.
7. Pitch bend springs back to center; it is taller when unfolded.
8. Keyboard: relief matches the preview; no horizontal indicators on the keys; C labels visible.
9. Two or three fingers: chords and glissando work, a held note stays down until its last finger lifts.
10. All 9 themes: only the keys change, **the container color never changes**. Dark looks clean (clear contrast between white and black keys, discreet top highlight).
11. Pressed keys: Color by default; LED adds a short glow.
12. Settings → UI: the picker looks like the rest of the Settings page, applies instantly, persists after restart, and Reset returns to Classic and Color.
13. No grey box or shadow showing through anywhere (the container is opaque).

## 9. First deliverable from the agent (before any code)

Reply with a short report, in English, with file and line for each point:

1. The current virtual keyboard container and its parts (keyboard, pitch bend, any layer lines).
2. How pressed notes are tracked today, and the note on / note off and pitch bend call paths (and the velocity logic).
3. Where the track Note range is stored and read today.
4. The Settings page structure: where the UI section ("Interface" tab) lives, and the components used for rows, section headers and segmented controls.
5. Where the container colors come from today (so `KeyboardChrome` can be wired to the palette).
6. Any blocker or difference with the preview.
7. Your proposed order of steps, each with a device check.

Do not start the implementation until this report has been reviewed.
