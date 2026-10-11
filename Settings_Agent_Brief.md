# Settings & MIDI Mapping — Integration Brief (for the coding agent)

## 1. Mission

Replace the current **Settings overlay** with the new **full-screen Settings page** (file `SettingsScreen.kt`), and add the **MIDI mapping mode** on top of the **real** Mixer and DrumPad pages.

The file `SettingsScreen.kt` contains the UI only. The logic that already exists in the app (MIDI learn engine, storage, audio engine, Mixer, DrumPad, top bar, chord display) stays in the app. Your job is to **wire** the existing logic to the new UI, not to rewrite it.

Rules that apply to the whole task:

- Do **not** change the behavior of any existing feature. If a feature exists, reuse its current logic.
- Do **not** create a second storage solution. Use the one the app already has.
- Do **not** add settings, tabs, or controls that are not listed here. If something seems missing, leave a `TODO(settings)` and report it.
- All user-facing text is in **English**.
- Remove the old settings overlay and the old MIDI lists (Master & Live, Tracks & Channels, Snapshots, Drum Pads, MIDI Learn & Controllers) completely once the new page works. Their mappings are migrated, not recreated (see section 8).

## 2. Files delivered

- `SettingsScreen.kt`: `SettingsScreen`, `MappingMode`, `MappingBar`, `Modifier.mappable(id)`, `SettingsState`, `SettingsActions`, `MidiMessage`, `MidiFamily`, `MappableControl`, `MappingCatalog`, `SettingsLogic`, `SettingsDefaults`, `SettingsRanges`.
- `Settings_Agent_Brief.md`: this document.

Requirements: Jetpack Compose foundation 1.7 or later, material3 (for `Text` only), `ui-tooling-preview` (preview only). `Modifier.blur` is used only on API 31 and above.

Change the package line to the real package.

## 3. Page structure (what the user sees)

**Settings page (full screen, no container, no outline):**

- Left rail (about 212 dp): four tabs, **Audio**, **Interface**, **MIDI**, **System**. The selected tab has a soft fill that slides between tabs. No card around the rail.
- Header: back button (returns to the previous screen: Mixer or DrumPad, whichever was open), title **Settings**, subtitle of the current tab, search field on the right.
- Content: flat sections with uppercase labels and thin hairline dividers. No cards, no borders around sections. Content scrolls vertically.
- Search: typing shows the matching settings in a flat list. Tapping a result opens its tab and clears the search.
- The page background is the app's interface background (see Interface colors). No bubbles, no aura on this page.

**Tabs and their settings:**

| Tab | Section | Setting | Control | Default | Range / values |
|---|---|---|---|---|---|
| Audio | Audio engine | Audio buffer size | Segmented + latency footers | 256 | 64, 128, 256, 512, 1024 (footer: ms at 44.1 kHz) |
| Audio | Audio engine | Maximum polyphony | Segmented | 128 | 64, 128, 256, 512 |
| Audio | Output | Synth output headroom | Slider + reset | 1.00x | 0.50x to 2.00x, step 0.05. Warning above 1.20x: "High gain may cause digital clipping." |
| Interface | Layout | Auto-fit faders | Switch | Off | On: all faders fit. Off: fixed width with scroll. |
| Interface | Colors | Primary interface hue | Slider + reset | 220 | 0 to 360 |
| Interface | Colors | Accent color saturation | Slider + reset | 1.00x | 0 to 2.00, step 0.05 |
| Interface | Colors | Background tone saturation | Slider + reset | 1.60x | 0 to 3.00, step 0.05 |
| Interface | Colors | Background lightness | Slider + reset | 1.00x | 0.50 to 2.00, step 0.05 |
| MIDI | Input | Anti-jitter filter | Switch | On | |
| MIDI | Input | Filter sensitivity window | Slider + reset | 15 ms | 1 to 30 ms, step 1. Disabled when the filter is off. |
| MIDI | MIDI mapping | Map Mixer | Row with counter (`done / total`) | — | Opens the mapping mode on the Mixer |
| MIDI | MIDI mapping | Map DrumPad | Row with counter | — | Opens the mapping mode on the DrumPad |
| MIDI | Management | Reset to defaults | Row + confirmation | — | Restores the default mapping (section 6) |
| MIDI | Management | Clear all mappings | Row + confirmation | — | Removes every mapping |
| System | Language & display | Language | Row (opens the language list) | English | |
| System | Language & display | Musical notation | Segmented ♯ / ♭ | Sharps | |
| System | Language & display | Keep screen on | Switch | On | |

Notes:

- The **Reset** button next to a slider appears only when the value differs from its default. Tapping it restores the default.
- The **Reset colors** link in the Interface tab appears only when at least one color value differs from its default.
- There is no "All app settings" link. The old overlay is gone, so any global settings entry point is handled by the existing app navigation, not by this page.

## 4. Mapping mode (what the user sees)

Entry: MIDI tab → **Map Mixer** or **Map DrumPad**. Exit: **Done**, or switching between Mixer and DrumPad (the mode closes, see section 7).

Rules:

1. **The page underneath is the REAL page** of the app: the real Mixer or the real DrumPad, with its real components. Do not build a static copy. Pass the real page into `MappingMode(pageContent = { ... })`.
2. **The page is complete.** All 8 tracks, the 5 snapshots (Default + Snapshot 1 to 4 as shown in the app; the app has 5 slots in total, check the real count), the master, the FX row, the top bar elements that are mappable. Horizontal scrolling of the mixer stays available in fixed-width mode. In auto-fit mode, all tracks fit on screen.
3. **The page is frozen and darkened**: touches never reach the page. It is softly blurred (very light) on API 31 and above. Do not add heavy effects.
4. **Chips**: one chip per mappable control with a visible element. Each chip sits on the **top-right corner** of its element. Chips follow the element when the mixer scrolls. Chips for elements that are out of the visible area are not drawn.
5. **Chip colors by family**: each control has its own family color (`MidiFamily`):
   - CC: soft blue
   - Note: amber
   - PC: sage
   - Transport (tap tempo, metronome): teal
   - Panic: rose
   - Snapshot: lilac
   Mapped chips show the message (`CC 7 · 1`, `Note 36 · All`). Unmapped chips show `+`.
6. **Standalone actions** (controls with no visible element: Panic, Tap tempo, Metronome, Previous snapshot, Next snapshot, Loop stop) are **not** chips. They are listed in **Other actions** (a card opened from the bottom bar).
7. **The chord display** (between Panic and Oct in the top bar) is **not** mappable and gets no chip. It is informational only. Do not add it to the catalog.
8. **Mapping bar**: a flat bar at the bottom, placed where the FX / settings / on-off row sits (the position of the existing FX settings on/off buttons). It must **not resize or push** the interface underneath. Use `barAlignment` and `barPadding` of `MappingMode` to place it. It shows: the page name, `done / total mapped`, **Other actions**, and **Done**.
9. **Assigning**: tap a chip → the card "Move a control on your controller" appears, with the control name and its current message if any. The next incoming MIDI message is assigned to this control. The chip then shows the new message.
10. **Conflict**: if the incoming message is already used by another control, the card shows "`CC 7 · Ch 1` is already used by `<other control>`" with **Replace** and **Cancel**. Replace moves the message to the new control and clears the other one. This is new behavior to wire (see section 5).
11. **Clear one mapping**: long-press a mapped chip. The mapping is removed, with a short message.
12. **Cancel listening**: the Cancel button in the card, or tapping another chip (the new chip becomes the listening target).
13. **Entrance animation**: chips appear in reading order (left to right, top to bottom), one after another with a 12 ms step, each starting at about 60% size with a light overshoot. They then breathe very slowly. **Exit**: the chips shrink in reverse order, then the page returns to Settings. Total exit time is about 520 ms. Do not change these timings without reason.

## 5. Wiring table

Each row says what the UI does, what the app must provide, and whether the logic already exists.

| UI action | App logic to call | Status |
|---|---|---|
| `onBack` | Return to the previous screen (Mixer or DrumPad) | Existing navigation |
| `onTabSelected` | Store the last tab (persisted) | New persistence key |
| `onQueryChange` | Filter the search (not persisted) | UI only |
| `onBufferSizeChange` | Existing audio buffer setter | Existing logic |
| `onMaxPolyphonyChange` | Existing polyphony setter | Existing logic |
| `onHeadroomChange` | Existing synth headroom setter | Existing logic |
| `onAutoFitChange` | Mixer layout mode | Existing UI setting, **persistence to add** |
| `onHueChange`, `onAccentSaturationChange`, `onBackgroundToneChange`, `onBackgroundLightnessChange` | Existing theme parameters | Existing logic, **persistence to check** |
| `onResetColors` | Restore the four default color values | Existing defaults |
| `onAntiJitterChange`, `onJitterWindowChange` | Existing MIDI filter | Existing logic |
| `onOpenMapping(page)` | Show `MappingMode` on the given page | New UI state |
| `onResetMappingsToDefaults` | Replace all mappings by `SettingsDefaults.mappings` | Existing reset logic, check it matches the default |
| `onClearAllMappings` | Remove all mappings | Existing logic |
| `onNotationChange` | Existing notation setting (♯ / ♭) | Existing logic |
| `onKeepScreenOnChange` | Existing keep-screen-on setting | Existing logic |
| `onOpenLanguage` | Existing language list | Existing logic |
| `onCloseMapping` | Hide `MappingMode`, return to Settings (MIDI tab) | New UI state |
| `onStartListening(id)` | Start the existing MIDI learn for this control id | Existing learn engine, **connect it** |
| `onCancelListening` | Stop the learn for the current control | Existing learn engine |
| `onClearMapping(id)` | Remove the mapping of this control | Existing logic |
| `onReplaceConflict` | Move the message to the listening control and clear the other one | **New behavior** |

**Incoming MIDI in mapping mode.** The learn engine receives the message. If a control is listening, the app either assigns the message or sets `conflict` in the state. The UI never reads MIDI directly.

**Conflict detection.** Compare the incoming `MidiMessage` (family, number, channel) with all current mappings. Channel `null` means Omni: decide the rule (see section 9) and apply it the same way everywhere.

## 6. Default mapping

`SettingsDefaults.mappings` contains the standard mapping: master volume on **CC 7, Omni**. The "Reset to defaults" action restores this mapping and removes every other mapping. Check that the app's current default matches this. If it differs, keep the app's current default and tell us, do not change it silently.

## 7. Mapping mode and mode switching

- Opening the mapping mode shows the **current** page (Mixer or DrumPad) and freezes it.
- Switching between Mixer and DrumPad **closes** the mapping mode. Do not keep the old page's mapping open.
- Closing returns to the Settings page, MIDI tab.
- Chords and the top bar are not affected by the mapping mode (except for the frozen look).

## 8. Persistence (mandatory)

**Every setting is persistent.** After the app is closed and reopened, every value must be the same. Use the app's existing storage (do not create another one).

Persist:

- Audio: buffer size, maximum polyphony, synth headroom.
- Interface: auto-fit faders, primary hue, accent saturation, background tone saturation, background lightness.
- MIDI: anti-jitter on/off, filter window in ms, all mappings (control id → message).
- System: notation (♯ / ♭), keep screen on.
- Last opened tab.
- Page settings of the Setlists screen (already defined in the Setlists brief).

**Not persisted** (temporary state): the search query, the mapping mode state, the listening state, the conflict, the "Other actions" card.

**Migration.** On the first launch of the new version, migrate the existing mappings from the old storage to the new mapping format (control id → message) without loss. Keep the old key until the migration is confirmed, then remove it. If the old format cannot be mapped to the new control ids, list the unmapped entries in the log and tell us.

## 9. Mapping catalog (align with the app)

`MappingCatalog.controls` is a **placeholder list**. The app must provide its real list through `SettingsState.catalog` (the UI reads the catalog from the state; it never reads `MappingCatalog` directly). Align it with the real controls before release:

1. Use the **real** control ids of the app. Keep them stable: the mappings are stored by id.
2. Every control with a visible element must be wrapped with `Modifier.mappable(id)` in the page composable. Do not wrap decorative elements.
3. Standalone actions (no visible element) are listed with `standalone = true`.
4. Check the real number of snapshots and of mixer tracks. The placeholder assumes 8 tracks and 5 snapshots.
5. The DrumPad catalog must match the real pads (the placeholder assumes 8).

**Conflict rule (decided, single rule):** two messages conflict when they have the same family and number **and their channels overlap**. Omni (no channel) overlaps every channel; two different explicit channels do not overlap. This is implemented in `SettingsLogic.conflicts` and `SettingsLogic.findConflict(mappings, controlId, incoming)`. Use `findConflict` when a message arrives while a control is listening: if it returns an id, set `SettingsState.conflict`; otherwise assign. Do not reimplement this rule elsewhere.

## 10. Layout constraints

- `MappingMode` measures its own origin, so chips stay aligned even if it is nested in a padded container. It must still **cover the same area as the page** (same size and position), because chips are placed on the real elements of the page rendered inside it.
- `MappingMode(pageScroll = ...)`: pass the mixer's horizontal scroll state (a `ScrollableState`, for example the `ScrollState` of the tracks row) in fixed-width mode, and `null` otherwise (DrumPad, or auto-fit). The veil blocks all touches to the page, so the mapping mode forwards horizontal drags and the edge arrows (`‹ n` / `n ›`) to this state. Without it, the mixer cannot scroll during mapping.
- The mapping bar must not change the size of the page underneath.
- The settings page fills the whole screen, under the top safe area. It does not sit in a container.
- Touch targets are at least 38 dp. The chips are smaller, but their tap area is the chip itself with its padding; if needed, enlarge the tap area with `minimumInteractiveComponentSize` without changing the look.

## 11. Things that must not change

- The Mixer and DrumPad behavior, sounds, and layouts (except the mapping mode overlay).
- The chord display and the top bar actions.
- The Setlists screen (its own brief applies).
- The audio engine, the MIDI learn engine internals, the storage format of other features.

## 12. Acceptance checklist

1. The old Settings overlay is gone. Its entry point (gear) opens the new full-screen page.
2. Four tabs: Audio, Interface, MIDI, System. The rail has no container. Sections have no card or outline.
3. Every setting listed in section 3 works, and its value survives an app restart.
4. Reset buttons appear only when the value differs from the default.
5. The warning above 1.20x headroom appears and disappears correctly.
6. Auto-fit faders changes the mixer layout and is persisted.
7. Search finds settings and opens the right tab.
8. Map Mixer and Map DrumPad open the **real** pages, frozen and darkened, with a complete set of chips.
9. Chips follow their elements when the mixer scrolls, and disappear when their element leaves the screen.
10. Chip colors follow the family rules. Mapped chips show the message.
11. Tapping a chip starts listening. The next MIDI message is assigned. A conflict shows Replace and Cancel.
12. Long-press clears a mapped chip.
13. Other actions lists the standalone actions and their current message.
14. Done leaves the mapping mode with the exit animation. Switching between Mixer and DrumPad closes it.
15. The mapping bar sits on the FX / settings row without resizing the page.
16. The chord display is not mappable and has no chip.
17. Migration keeps every existing mapping. Reset to defaults and Clear all mappings work.
18. No old MIDI list remains in the app.
19. All text is in English. The DrumPad, Mixer, Setlists screen and audio engine are unchanged.

## 13. Open points to confirm with the user

- Whether the default mapping in the app matches `SettingsDefaults.mappings` (section 6).
- Exact list of mappable controls and their ids (section 9).
- The exact number of snapshots in the app (the design shows Default and 4 snapshots, and the brief above assumes 5).
