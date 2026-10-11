# Fix Brief — Setlists home, Settings page, Mapping mode, live theme

## 0. Context and rules

The Compose files `SetlistsScreen.kt` and `SettingsScreen.kt` were delivered as **UI only**. They render what they receive and call actions. In the current build, the UI is visible but almost nothing is wired. Your job is to **connect** them to the app, not to redesign them.

Hard rules:

- **Do not change the visual design** of Setlists, Settings, the mapping mode or the mixer. The reference is the approved preview, not your memory of it.
- **Do not rewrite the UI files** unless a fix below explicitly says so. Prefer changing the host (navigation, ViewModel, theme source) over editing the UI.
- **Do not touch** the audio engine, the MIDI learn engine internals, the storage format of other features, the DrumPad, the Loop Player.
- **Do not claim a fix is done** unless the verification of that section passes on a device or emulator. Report the result of each check.
- All user-facing text stays in English.

## 1. Mandatory first step: diagnose before changing anything

Before editing code, run these checks and write the findings in a short report (one line per check: file, line, what you found). Send the report first. Do not start fixing until the report is written.

1. **Home route.** Find the start destination of the app (navigation graph, `NavHost` `startDestination`, or the activity's initial composable). Report which composable is shown on cold start.
2. **Old home still present.** Search for every composable that draws the old home interface and every call site. Report whether the old home is still reachable from any route.
3. **Patches button.** Find the handler of the Patches button. Report where it navigates or what it opens.
4. **Settings entry.** Find the handler of the gear button. Report what it opens (old overlay, new `SettingsScreen`, or both).
5. **Old overlay.** Search for the old settings overlay composable and every place it is drawn. Report whether it is still composed (even hidden) in the same tree as the new page.
6. **Actions passed to the UI.** Search for `NoOpSettingsActions`, `NoOpSetlistsActions`, and any `object : SettingsActions` / `object : SetlistsActions` left in production code. Report every place where these are passed to `SettingsScreen`, `MappingMode` or `SetlistsScreen`.
7. **State collection.** Report how the UI receives its state: `collectAsStateWithLifecycle()` on a `StateFlow`, or a copied value. A state passed once and never updated is the most likely cause of the "nothing reacts" symptom.
8. **Theme source.** Find the object that provides the colors of the mixer background and the accent. Report whether `SettingsScreen` receives its background from the settings state or from a hardcoded default.
9. **Mapping host.** Find where `MappingMode` is called. Report whether it is called when `state.mappingPage != null`, and whether the host branches between `SettingsScreen` and `MappingMode`.
10. **Persistence.** Find the existing storage used by the app (DataStore, Room, SharedPreferences). Report which key holds the Setlists preferences and the Settings values today.

## 2. Symptom: the old home is still shown on first launch

Expected: on cold start, the Setlists screen is the home page. The old home is gone. The Patches button no longer opens a separate overlay; it opens the same Setlists screen.

Fix:

1. Set the start destination to the Setlists route.
2. Delete (or unregister from the navigation graph) the old home composable and its entry point. Do not keep it as a fallback.
3. Make the Patches button navigate to the Setlists route, or, if the design calls for it, open the same Setlists route as the home. There must be **one** Setlists screen in the app, not two.

Verification:

- Cold start (force-stop then open): the first screen is Setlists.
- No code path shows the old home (search the repo for its composable name: zero call sites).
- The Patches button opens the Setlists screen, and the back action returns to where the user was.

## 3. Symptom: the top cards look flat grey, and the aura is not visible

Expected: each of the 4 top cards shows a faint tinted aura that drifts slowly, and a faint tinted contour that fades on the far corner. The rows of the list are neutral.

Diagnose:

1. Check the `SetlistsPrefs` used by the card: `cardAuras` must be `true` by default and must come from the state, not a hardcoded `false`.
2. Check `animatedBackground` and `reducedMotion`: when they are `false`, the aura is drawn but does not drift. Confirm the aura is drawn (static is acceptable only if reduced motion is actually on in system settings).
3. Check that the card's aura is drawn with the setlist's color: `Setlist.color.base`, not a fallback gray.
4. Check that no layer above the card paints an opaque background (a parent `Surface`, a `background(Color.White...)` or a `Card` default container).

Fix: restore the aura drawing from the approved preview values and make sure the parent does not paint over it. Do not change the values to make it look different from the approved preview.

Verification: a top card shows a visible colored aura in its upper-left area, and a colored contour; the list rows are neutral with colored patch dots only.

## 4. Symptom: Setlists page settings do nothing

Expected: every control of the gear panel (Display, Behavior, Safety) changes the screen immediately, and the value survives an app restart.

Fix:

1. Pass the real `SetlistsActions`, not `NoOpSetlistsActions`. Implement `onPrefsChange(prefs)` and `onResetPrefs()`.
2. `onPrefsChange` must write the new `SetlistsPrefs` to the existing storage, then emit it in the state flow.
3. The screen must read `state.prefs` from the flow. No local copy.
4. Implement the other actions the page needs: open, create, rename, color, pin, delete with undo, sort, pinned filter, query. Each must update the state flow and persist what the spec says to persist (see the Setlists brief v2).

Verification (on device): toggle each preference, see the change, force-stop the app, reopen, see the same value. Reset page settings restores the defaults.

## 5. Symptom: the Settings page does nothing (tabs, switches, sliders, rows)

Expected: tapping a tab opens its content. Every switch, segmented control and slider changes its value immediately and persists it. Reset buttons appear only when a value differs from its default.

Diagnose: if even the tabs do not respond, the most likely cause is that a layer covers the page and swallows touches: the old overlay still composed, the mapping veil still composed, or a parent `pointerInput` consuming events. Check item 5 of the first step.

Fix:

1. Make sure the page is the only settings UI in the tree. Remove the old overlay composition completely.
2. Pass a real `SettingsActions` implementation from a ViewModel. Never pass `NoOpSettingsActions` in production.
3. Collect the state with `collectAsStateWithLifecycle()` and pass it to `SettingsScreen`. Every action updates the ViewModel state; every change is written to the existing storage in the same call.
4. Confirm the gesture chain: no parent gives a `pointerInput` or `clickable` that consumes taps before the page. If the settings page is inside a scrollable container, check that taps inside `SettingRow` still reach the row.

Verification (on device):

- Tap each tab: the content changes.
- Change each switch and segmented control: the app behaves accordingly and the value survives a restart.
- Slide each slider: the value label updates while dragging, and the value survives a restart.
- Reset buttons appear and reset.

## 6. Symptom: the color sliders change only the mixer background

Expected: when the user drags Primary hue, Accent saturation, Background tone or Background lightness, the **settings page itself** and the **mixer and DrumPad** update live, from the same source.

Fix:

1. Create one function, for example `SettingsTheme.palette(state: SettingsState)`, that returns the colors used by the app (background brush, accent, accent text, panel colors). It is the single source of colors.
2. Provide that palette to the whole app through a `CompositionLocal` at the root, or pass it explicitly to every screen. The mixer, the DrumPad, the Setlists screen and the Settings page all read the same palette.
3. In the host of the Settings page, pass `background = SettingsTheme.backgroundBrush(state)` to `SettingsScreen`. Do not rely on its default parameter.
4. The settings page must not read a copy of the colors captured at first composition. It reads the state.

Verification: drag Primary hue from one end to the other: the settings background, the tab highlight, the sliders and the mixer background all change in the same frame. Reset colors restores them.

## 7. Symptom: the mapping mode does not open

Expected: MIDI tab → Map Mixer opens the real mixer, frozen and darkened, with chips on its controls. Map DrumPad opens the real DrumPad the same way. Done returns to the MIDI tab.

Fix:

1. `onOpenMapping(page)` must set `mappingPage` in the state flow.
2. The host must branch: when `state.mappingPage == null`, show `SettingsScreen`; when it is not null, show `MappingMode(state, actions, pageContent = { <the real Mixer or DrumPad> }, pageScroll = ...)`. Verify this branch exists; if not, add it.
3. `pageContent` must be the **real** composable of the page, with `Modifier.mappable(id)` on each mappable element. If the real page is not wrapped, chips will not appear.
4. `onCloseMapping()` sets `mappingPage` to `null`. Switching between Mixer and DrumPad closes the mapping mode first.
5. Remove the mapping mode's own `onGloballyPositioned` / `boundsInRoot` assumptions only if they break the layout; otherwise keep them.

Verification: Map Mixer opens the mixer, not the settings page. Chips appear with the entrance wave. Tapping a chip shows the listening card. Done returns to Settings, MIDI tab.

## 8. Symptom: the chip and listening behavior

Only after section 7 passes:

- A MIDI message received while listening assigns the message to the control, unless `findConflict` returns an id, in which case it sets `conflict` in the state.
- Replace moves the message and clears the other control. Cancel stops listening.
- Long-press on a mapped chip clears it.
- The mappings are saved to the existing mapping storage.

## 9. Persistence (all settings)

Every value must survive an app restart: audio buffer, polyphony, headroom, auto-fit, the four color values, anti-jitter and its window, notation, keep-screen-on, last tab, all mappings, the Setlists preferences, and the setlists themselves.

Use the existing storage. Do not add a second store. If a key already exists, reuse it. Write in the same call that updates the state.

## 10. Order of work

1. Diagnostic report (section 1).
2. Home route (section 2).
3. Single settings tree, real actions, state collection (section 5, steps 1 to 3).
4. Theme palette (section 6).
5. Mapping branch (section 7).
6. Setlists actions and preferences (section 4).
7. Setlists card aura (section 3).
8. Chip and listening behavior (section 8).
9. Persistence audit (section 9).

After each step, run its verification on a device before moving on. Report pass or fail. If a step fails, stop and report, do not stack fixes.

## 11. Definition of done

- Cold start opens Setlists. The old home is gone from the code.
- Patches opens the same Setlists screen.
- Every Setlists preference changes the screen and survives a restart.
- The Settings page is the only settings UI. Every tab and control works, and survives a restart.
- Colors change the settings page, the mixer and the DrumPad live from one palette.
- Map Mixer and Map DrumPad open the real pages with chips. Listening, conflict, clear and Done work.
- No `NoOp` action remains in production wiring.
- The approved visual design is unchanged.

## 12. Rules for the diagnostic report

- Every finding cites a **file and a line** (or a symbol searched with its result). "Peut", "parfois", "probablement" are not findings: either the code shows it, or the check is marked "not verified".
- Do not invent class or file names. If a name in the report (for example `MixerViewModel`, `ProjectsPage`, `currentPage`) is not found by a search, say so and report what the search returned.
- Report the result of each of the 10 checks in section 1, even when the result is "no match".
- Stop after the report. Wait for confirmation before starting section 2.

## 13. Corrections from the diagnostic report (evidence received)

Confirmed by code:

- `MainActivity.kt` line 66: `setContent` renders `MixerScreen` directly. There is no `NavHost` and no `startDestination`. The app has no navigation graph; screens are switched inside `MixerScreen`.
- `MainActivity.kt` line 44: `uiState` is collected with `collectAsStateWithLifecycle()`. This part is correct.
- `MainActivity.kt` lines 58-69: `SoundfontLiveMixerTheme` receives the theme values from `uiState`. The mixer is therefore themed live, which explains why only the mixer background changes.
- `MixerScreen.kt` line 642: the Patches button calls `onOpenSetlists`. Line 655: the gear calls the callback that opens `SettingsScreen`.
- `MixerScreen.kt` line 786: `MappingMode` is called when `state.mappingPage != null`.
- `AppStatePersistence.kt` line 21: persistence uses SharedPreferences, file `livekeys_mixer_prefs`. This is the single storage to reuse.

Corrected facts:

- The "old home" is **`MixerScreen` itself**, not a `ProjectsPage`. `ProjectsPage` does not exist. Section 2 applies to `MixerScreen`: on cold start, the app must show Setlists first, and the mixer must be opened from a setlist or from the Setlists page.
- The old settings overlay is absent. Remove the claim that it covers the page. The blocker is elsewhere (see below).

Still not verified (the report did not cover these; the agent must check them and cite file and line):

1. **Where `SettingsScreen` is rendered.** It must be shown as a full screen, not inside `MixerScreen`'s overlay branch. Report the parent composable and the condition that shows it.
2. **Where `MappingMode` is rendered relative to `SettingsScreen`.** If `SettingsScreen` is still composed on top while `mappingPage` is set, the mapping is hidden underneath. Report the branch order at `MixerScreen.kt` 786 and the settings branch.
3. **The production `SettingsActions` implementation.** The report lists only `NoOpSettingsActions` in a preview (line 1586). Find the object or class that really implements the actions and passes them to `SettingsScreen`. If none exists, that is the cause of "nothing reacts".
4. **The production `SetlistsActions` implementation.** Same question for `NoOpSetlistsActions` at `SetlistsScreen.kt` line 217: is it in a preview, or in production code?
5. **The `background` parameter passed to `SettingsScreen`.** If it is the default brush, the settings page does not follow the theme. Report the call site.
6. **The mixer ViewModel's settings methods** (headroom, polyphony, buffer, hue, saturation, auto-fit, jitter, notation, keep-screen-on). For each, report: the method name, the file and line, whether it updates the state flow, and whether it writes to `livekeys_mixer_prefs`.
7. **The Setlists preferences writes.** Report whether `onPrefsChange` writes to SharedPreferences and emits the new state.

Next step: do section 2 now, with this decision needed from the user (see message), then checks 1 to 7 above before any settings fix.

## 14. Decision: Cancel behavior (option A, decided)

- Setlists is the home screen on cold start. It is shown first, before the mixer.
- **Cancel** on Setlists returns to the **last screen the user had open** (mixer or DrumPad). Store that screen in SharedPreferences (`livekeys_mixer_prefs`) so it survives a restart.
- If no screen was ever opened (true first launch), **Cancel stays on Setlists** and does nothing else.
- The Patches button in the mixer opens Setlists. Its Cancel returns to the mixer it came from, using the same rule.
- Only one Setlists screen exists in the app. Remove any duplicate entry point.

Implementation in `MixerScreen` / the host (not in the UI files): add a `currentScreen` value (`Setlists`, `Mixer`, `DrumPad`) held by the ViewModel, defaulting to `Setlists` on cold start. Render the matching screen from it. Record the last non-Setlists screen when leaving it. Verify: cold start shows Setlists; open a patch, go to Mixer, open Setlists via Patches, Cancel returns to Mixer; force-stop and reopen, Setlists shows first, Cancel returns to Mixer.
