# Patches Overlay & Setlists Integration Plan (Revised)

## Summary
Restore the Patches overlay accessible on touching the box, ensuring the Setlists screen is accessed exclusively from the button/case inside the Patches overlay window (reverting any direct startup change), and wire all settings and setlists parameters cleanly with `MixerViewModel` and `AppStatePersistence`.

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Startup Flow**: Keep standard startup (Mixer / Patches overlay), do NOT set Setlists as direct app launch screen.
> - **Patches Overlay**: Restore Patches overlay modal/drawer triggered on touching the patch box.
> - **Setlists Navigation**: Access Setlists screen solely via the button/case inside the Patches overlay.
> - **Parameter Wiring**: Wire all Settings and Setlists parameters properly into `MixerViewModel` and `AppStatePersistence`.

- **Confirmed Decision 1**: Patches overlay restored on touch.
- **Confirmed Decision 2**: Setlists entry point restricted to Patches overlay button.

## 1. Overview & Core Concept

- **What It Does**: Preserves the standard app startup flow while restoring the Patches overlay modal on touch. Users can open the Setlists page from within the Patches overlay, and all settings and setlists parameters are fully wired and persisted via `MixerViewModel` and `AppStatePersistence`.
- **Target Audience**: Musicians using the sound stage mixer.
- **Key Value**: Intuitive navigation matching original design while integrating new setlists and settings capabilities.

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. App launch -> Normal startup screen (Mixer).
  2. Tap patch box -> Opens Patches overlay window.
  3. Tap Setlists button in Patches overlay -> Navigates to `SetlistsScreen`.
  4. Settings & Preferences -> Adjusted and persisted seamlessly.

## 3. Key Product Decisions & Trade-Offs

- **Decision 1**: Restore Patches Overlay Trigger
  - *Chosen Approach*: Reconnect touch listener on patch box to show Patches overlay.
  - *Why*: Matches user request and preserves expected navigation flow.
- **Decision 2**: Setlists Entry Point
  - *Chosen Approach*: Navigation button inside Patches overlay.
  - *Why*: Keeps Setlists contextual to patches management.

## 4. Technical Architecture & Data Strategy

```
┌──────────────────────────────────────────────┐
│                  MixerScreen                 │
│         (Tap patch box -> Show Overlay)      │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│                Patches Overlay               │
│      (Tap Setlists button -> Navigate)       │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│                 SetlistsScreen               │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│          MixerViewModel & Persistence        │
└──────────────────────────────────────────────┘
```

- **Data Model & State**: Fully bound parameters for settings and setlists in `MixerViewModel` backed by `AppStatePersistence`.
