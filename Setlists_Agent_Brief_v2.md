# Setlists Screen — Integration Brief (v2, for the coding agent)

## Mission

Replace the current Setlists screen with `SetlistsScreen.kt` (Jetpack Compose). It is the **final design**: do not redesign it, do not add features.

Do **not** touch: DrumPad, TonicPad, Loop Player, the mixer, the audio engine, the global top bar logic. A setlist stays what it is today: **a folder that stores patches**. No durations, tempo, timelines, scores or new data fields beyond those in `Setlist`.

All user-facing text is **English** (already in the file; keep it that way).

## 1. Files and requirements

- Copy `SetlistsScreen.kt` into the UI module and adjust the `package` line.
- Compose foundation **1.7+** (uses `LazyItemScope.animateItem()`), `material3` (for `Text` only), `ui-tooling-preview` (for the `@Preview` only). `java.time` needs minSdk 26 or core library desugaring.
- For the create sheet to rise above the keyboard: `android:windowSoftInputMode="adjustResize"` and `WindowCompat.setDecorFitsSystemWindows(window, false)`.
- The screen fills the whole area under the global top bar. Apply the host's insets/padding around it if needed.
- The file defines its own private styles (`SlColors`, etc.). If the project has a theme, map the palette to it instead of duplicating, but keep the exact colors below.

## 2. Wiring (stateless screen)

`SetlistsScreen(state: SetlistsUiState, actions: SetlistsActions)` shows state and calls actions. Create (or reuse) a ViewModel that exposes `StateFlow<SetlistsUiState>` and implements `SetlistsActions`. Every action has an empty default body: wire the minimum first, then the rest.

| Action | What the app must do |
|---|---|
| `onOpenSetlist(id)` | Load that setlist (existing logic), set `openSetlistId` and `lastOpenedId`, update its `lastUsedAt` to now, go back to the mixer. |
| `onCreateSetlist(name, mode)` | `Blank`: empty setlist. `Duplicate`: copy of the currently open setlist (patches included). Color: `SetlistsLogic.autoColor(existing)`; for a duplicate `autoColor(existing, after = source.color)`. `lastUsedAt` = now. Do not open it automatically. |
| `onRenameSetlist`, `onChangeColor` | Update and persist. |
| `onDuplicateSetlist(id)` | Same as the menu "Duplicate": name `"<name> copy"`, not pinned, next color. |
| `onTogglePin(id)` | Toggle and persist `isPinned`. |
| `onDeleteSetlist(id)` | Delete now but **keep the last deleted setlist (with its patches) in memory**. |
| `onUndoDelete()` | Restore the last deleted setlist exactly as it was. The UI shows the 5-second Undo bar itself. |
| `onQueryChange`, `onSortChange`, `onOnlyPinnedChange` | Only update `query`, `sort`, `onlyPinned` in the state. Search, filter and sort are done by the UI with `SetlistsLogic.visibleList`; **do not redo them**. |
| `onPrefsChange(prefs)`, `onResetPrefs()` | Persist page preferences (see section 5). |
| `onCancel()` | Close the Setlists screen and return to the previous screen. |
| `onOpenAppSettings()` | Open the global settings overlay (the "All app settings" link). |

State to provide: `setlists`, `openSetlistId` (null on first launch or when none is open), `lastOpenedId`, `isLoading`, `query`, `sort`, `onlyPinned`, `prefs`, `reducedMotion` (true if the system reduces animations, e.g. animator duration scale is 0).

**Back button rule (global top bar, not in this file):** the Back button that returns to the mixer must be **disabled** on first launch and whenever no setlist is open. Use `SetlistsUiState.isBackEnabled` (`openSetlistId != null`).

## 3. Design reference (what the file implements)

**Top bar (56 dp):** `Cancel` (ghost button) · search field (`Search setlists`, 230 dp) · centered title `Setlists` with a counter under it (`N setlists · M patches`, numbers roll when they change) · `Create New Setlist` (primary) · gear button (opens the **page settings** panel, never the global overlay). There is **no** "Load Setlist" button and no bottom bar.

**Fixed zone (never moves when the list scrolls):**
- Label `Recently used`, then **exactly 4 cards on one row**: the 4 most recently used setlists (`SetlistsLogic.topCards`). They ignore search, sort and the Pinned filter.
- Header row: `All setlists` · `Pinned` chip (filters the list) · `Sort by` segmented control (`Recent` / `Name` / `Patches`, sliding thumb).

**Scrolling zone:** only the list below the header scrolls. About 4 rows are visible on a standard landscape phone (rows 44 dp, 38 dp in compact mode). Pinned setlists stay at the top of the list (unless "Pinned on top" is off), shown with a small pin icon. There is no separate Pinned section.

**Top cards (96 dp):** name, patch count on its own line (`12 patches`), then `Last used: Today, 21:51` in a smaller size. No patch dots, no side bar, no monogram chip. The color shows as (a) a faint **aura**: a small tinted bubble drifting inside the card very slowly (60–80 s per leg), and (b) a faint tinted **contour** that fades out on the opposite corner. No outer glow, no neon. Open card: aura and contour slightly stronger, `OPEN` tag. Last opened card: slightly stronger contour.

**List rows:** neutral graphite (no aura, no tint). Color appears only in the **patch dots** (one dot per patch, in order, max 12 then `+N`). `OPEN` tag takes the color of the setlist.

**Open gesture** (preference `Open a setlist with`): `Tap` (default), `Double tap` or `Hold`. With Double tap or Hold, a simple tap shows a short hint (`Double tap to open` / `Press and hold to open`).

**Delete:** swipe a list row to the left. Light resistance, a rose pill grows behind the row **with an 8 dp gap**, it locks (haptic) past the threshold (`min(40% of width, 200 dp)`), release deletes. Below the threshold the row springs back. No confirmation dialog: a bottom bar shows `Setlist deleted   UNDO` for 5 seconds. Top cards are deleted through the `⋮` menu. If "Lock deletion" is on, swiping does nothing but show `Deletion is locked`, and `Delete` in the menu is greyed with a lock icon.

**`⋮` menu:** `Pin to top` / `Unpin`, `Rename` (inline field + Save), `Change color` (4 round swatches), `Duplicate`, `Delete`.

**Create sheet:** opened by `Create New Setlist`. Title `New setlist`, a name field focused automatically (keyboard opens, default `Setlist N`, max 40 characters), two tiles: **Duplicate** (subtitle `Copy of <open setlist>`, disabled with `No setlist open` when none is open) and **Blank** (`Start with no patches`, default). Buttons `Cancel` and `Create` (enabled only for a valid name). IME action Done submits.

**Empty state (no setlist):** dashed ghost card breathing slowly, title `Your first setlist starts here`, subtitle `Group patches for a show.`, button `Create setlist`. Search field is dimmed and disabled.

**Loading state:** shimmering skeleton cards and rows.

**Background:** 4 very soft bubbles drifting with 62–110 s cycles, centered slightly low but roaming, low opacity. They ease (600 ms) toward the color of the **open** setlist and back to neutral when none is open. They are independent from list scrolling (no parallax). Throttled to about 30 fps. Static when "Animated background" is off or `reducedMotion` is true.

## 4. Colors (do not add others)

| Name | Base | Text variant |
|---|---|---|
| Blue (first setlist) | `#8FB2E8` | `#A9C6F0` |
| Amber | `#D8A24A` | `#E6BC73` |
| Rose | `#C2587A` | `#DB8AA3` |
| Sage | `#86A394` | `#A5BFB1` |

Auto-assignment: least used color (ties: palette order); a duplicate takes the next color after its source. The blue accent for actions (`#8FB2E8` buttons, selections) is separate from setlist colors in meaning.

## 5. Page preferences (gear panel, persisted)

`SetlistsPrefs` (defaults in parentheses). **Display:** `animatedBackground` (on), `cardAuras` (on), `compactRows` (off), `patchDots` (on). **Behavior:** `openWith` (Tap), `pinnedOnTop` (on), `rememberSort` (on: restore the last sort and the Pinned filter at launch; off: always start on Recent). **Safety:** `lockDeletion` (off), `haptics` (on). The panel also has `Reset page settings` (`onResetPrefs`) and `All app settings` (`onOpenAppSettings`). Apply changes immediately; no Save button.

## 6. Rules and edge cases

- Names: trim, 1–40 characters. Duplicate names are allowed.
- `lastUsedAt` is the only source for "Recently used" and the `Recent` sort.
- Relative labels (`Today`, `Yesterday`) are computed when the list is built; refresh them when the screen is opened.
- Performance: do not rebuild `setlists` on every frame; only when data changes. Background and aura animations must not trigger recomposition of the list.
- Accessibility: touch targets are at least 38 dp (menu dots are 26–32 dp inside a larger row tap area). Keep content descriptions if the project requires them.
- If something in the existing app prevents a wiring, **do not change the engine or other screens**: leave the action empty, add `// TODO(setlists)` and report it.

## 7. Acceptance checklist

1. No "Load Setlist" button; Cancel sits left of the search field.
2. Exactly 4 recent cards on one row, fixed while the list scrolls; only the list scrolls.
3. Cards show aura + faint contour, no side bar, no monogram, no outer glow. List rows are neutral with colored patch dots only.
4. Tapping a card or row opens that setlist (default gesture) and returns to the mixer.
5. Global Back is disabled on first launch and when no setlist is open.
6. Swipe-left delete with 8 dp gap, haptic at threshold, Undo for 5 seconds restores the setlist and its patches.
7. Create sheet: name auto-focused, Duplicate/Blank, auto color, new setlist appears first.
8. Pinned setlists stay on top of the list; the `Pinned` chip filters them; pin works from the menu.
9. Gear opens the page settings panel (not the global overlay); each preference works immediately.
10. Empty state and loading skeleton are visible when relevant.
11. Background bubbles are slow, independent from scrolling, tinted by the open setlist, and static when disabled.
12. DrumPad, Loop Player and the engine are unchanged.
