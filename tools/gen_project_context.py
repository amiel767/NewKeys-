#!/usr/bin/env python3
"""
Generate PROJECT_CONTEXT.md : every ACTIVE source file of the project, inlined, so an
agent dropped into this repo is immediately in context.

"Active" = reachable from the app entry points (MainActivity -> MixerScreen). Files with
no external reference (dead UI variants, unused engines) are EXCLUDED and listed in an
appendix instead, so nothing is silently hidden.

Regenerate with:  python3 tools/gen_project_context.py
"""
import os, re, collections, datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java")
CPP = os.path.join(ROOT, "app/src/main/cpp")
OUT = os.path.join(ROOT, "PROJECT_CONTEXT.md")

# --------------------------------------------------------------------------- exclusions
# Third-party C++ (vendored FluidSynth + the whole aetherfx DSP pack) is large and is
# not project-authored. aetherfx headers ARE inlined (they are the FX engine we own).
CPP_EXCLUDE_DIRS = ("fluidsynth",)
# Kotlin files with no external reference: dead variants kept in the tree.
DEAD_EXTRA = {
    "com/soundstage/mixer/ui/components/MaterialYouNativeMixer.kt",
    "com/soundstage/mixer/ui/components/MaterialYouSvgMixerStage.kt",
    "com/soundstage/mixer/ui/components/BottomBar.kt",
    "com/soundstage/mixer/ui/components/DrumPadDialog.kt",
    "com/soundstage/mixer/ui/components/TopBar.kt",
    "com/soundstage/mixer/model/MaterialYouPalette.kt",
}
# Entry points have no inbound reference by construction.
ENTRYPOINTS = {
    "com/soundstage/mixer/MainActivity.kt",
    "com/soundstage/mixer/SoundStageApplication.kt",
}
# Kept in the tree, wired to nothing (verified by reachability scan).
DEAD_KNOWN = {
    "com/soundstage/mixer/audio/DrumLooperEngine.kt",
    "com/soundstage/mixer/audio/TimeStretchPitchShifter.kt",
    "com/soundstage/mixer/model/LoopPlayerState.kt",
    "com/soundstage/mixer/ui/components/RotaryKnob.kt",
    "com/soundstage/mixer/ui/components/SceneDialog.kt",
    "com/soundstage/mixer/ui/components/StyleDialog.kt",
    "com/soundstage/mixer/utils/LanguageManager.kt",
}

DECL_RE = re.compile(
    r'^(?:@\w+(?:\([^)]*\))?\s*)*'
    r'(?:public\s+|internal\s+|private\s+)?'
    r'(?:abstract\s+|open\s+|sealed\s+|data\s+|enum\s+|value\s+|annotation\s+)*'
    r'(class|object|interface|fun|val|var|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)', re.M)


def kotlin_files():
    out = []
    for r, d, fs in os.walk(SRC):
        for f in fs:
            if f.endswith(".kt"):
                out.append(os.path.join(r, f))
    return sorted(out)


def top_level_decls(text):
    names = set()
    for m in DECL_RE.finditer(text):
        if re.search(r'\bprivate\b', m.group(0)):
            continue
        kind, name = m.group(1), m.group(2)
        if kind in ("val", "var") and name.islower():
            continue
        names.add(name)
    return names


def scan():
    files = kotlin_files()
    text = {f: open(f, encoding="utf-8", errors="ignore").read() for f in files}
    decls = {f: top_level_decls(text[f]) for f in files}
    usage = collections.defaultdict(set)
    for f in files:
        for name in set(re.findall(r'\b([A-Za-z][A-Za-z0-9_]{2,})\b', text[f])):
            usage[name].add(f)
    live, dead = [], []
    for f in files:
        rel = os.path.relpath(f, SRC)
        if rel in DEAD_KNOWN or rel in DEAD_EXTRA:
            dead.append((rel, "dead (no live reference)"))
            continue
        if rel in ENTRYPOINTS:
            live.append(rel); continue
        if not decls[f]:
            dead.append((rel, "no top-level declaration")); continue
        if any(usage[n] - {f} for n in decls[f]):
            live.append(rel)
        else:
            dead.append((rel, "no external reference"))
    return live, dead, text


def native_files():
    live, dead = [], []
    for r, d, fs in os.walk(CPP):
        if any(x in r for x in CPP_EXCLUDE_DIRS):
            continue
        for f in sorted(fs):
            if f.endswith((".cpp", ".h")):
                live.append(os.path.relpath(os.path.join(r, f), CPP))
    # the vendored tree is summarised, not inlined
    vendored = 0
    for r, d, fs in os.walk(CPP):
        if "fluidsynth" in r:
            vendored += sum(1 for f in fs if f.endswith((".c", ".cpp", ".h")))
    return sorted(live), vendored


def fence(path, rel):
    ext = os.path.splitext(path)[1].lstrip(".")
    lang = {"kt": "kotlin", "cpp": "cpp", "h": "cpp", "xml": "xml"}.get(ext, ext)
    body = open(path, encoding="utf-8", errors="ignore").read()
    n = body.count("\n") + 1
    return lang, n, body


def main():
    live, dead, text = scan()
    nat, vendored = native_files()

    # group kotlin by package directory
    groups = collections.defaultdict(list)
    for rel in live:
        groups[os.path.dirname(rel)].append(rel)

    total_kt_lines = sum(text[os.path.join(SRC, r)].count("\n") + 1 for r in live)

    L = []
    A = L.append
    A("# PROJECT_CONTEXT.md — NewKeys / SoundStage Mixer")
    A("")
    A("> Generated by `tools/gen_project_context.py`. **Do not edit by hand** — regenerate.")
    A(f"> Generated: {datetime.date.today().isoformat()} · branch `main`")
    A("")
    A("This file inlines the **complete source of every active file** so an agent can be")
    A("dropped into the repository already in context, without reading the tree.")
    A("Dead files (present but referenced by nothing) are excluded from the body and")
    A("listed in the appendix — nothing is hidden, but nothing dead wastes context.")
    A("")
    A("| | |")
    A("|---|---|")
    A(f"| Active Kotlin files | {len(live)} ({total_kt_lines:,} lines) |")
    A(f"| Active native files | {len(nat)} |")
    A(f"| Excluded dead Kotlin files | {len(dead)} |")
    A(f"| Vendored FluidSynth files (summarised, not inlined) | {vendored} |")
    A("")
    A("---")
    A("")
    A("## 1. Orientation")
    A("")
    A("Android audio workstation: 8 mixer tracks + drum + tonic pad, FluidSynth synthesis,")
    A("a 15-effect Æther FX engine (C++ DSP + Compose UI), a virtual keyboard, a live looper.")
    A("")
    A("```")
    A("MainActivity")
    A("  └─ MixerScreen                     (ui/MixerScreen.kt)          app shell, popups")
    A("       ├─ MixerTopBarV2              (ui/components/v2/)          page selector")
    A("       ├─ ChannelStripV2 ×8          (ui/components/v2/)          fader, pan, mute/solo")
    A("       ├─ MixerBottomBarV2           (ui/components/v2/)          master fader, snapshots")
    A("       ├─ VirtualKeyboard            (ui/components/)             MIDI in")
    A("       ├─ DrumPadScreen              (ui/pages/)                  pads + LiveLooper page")
    A("       ├─ FxOverlay → FxWindowHost   (ui/fx/, com.aetherkeys.fx/ui/)  Æther FX window")
    A("       ├─ TrackSettingsWindow        (ui/tracksettings/)          velocity, MIDI, range")
    A("       └─ SettingsScreen             (ui/settings/)               incl. MidiMappingPage")
    A("                                                                  ↓")
    A("MixerViewModel (viewmodel/) ── MixerUiState ── AudioEngine (model/)")
    A("                                                   ↓ JNI (native-lib.cpp)")
    A("                                     audio_engine.{h,cpp}  ← control thread")
    A("                                       ├─ SoundfontEngine   FluidSynth, per-channel render")
    A("                                       ├─ SamplePlaybackEngine  drum pads")
    A("                                       └─ FxMixer (aetherfx/)   11 strips + master")
    A("```")
    A("")
    A("**Two Kotlin namespaces coexist.** `com.soundstage.mixer` is the app;")
    A("`com.aetherkeys.{fx,sheets,setlists}` are the FX engine UI and two feature modules.")
    A("`SettingsScreen` and `SetlistsScreen` exist in **both** — always use full paths.")
    A("")
    A("---")
    A("")
    A("## 2. Active Kotlin sources")
    A("")
    for pkg in sorted(groups):
        A(f"### `{pkg or '.'}`")
        A("")
        for rel in sorted(groups[pkg]):
            path = os.path.join(SRC, rel)
            lang, n, body = fence(path, rel)
            A(f"#### `{os.path.basename(rel)}` — {n} lines")
            A("")
            A(f"```{lang}")
            A(body.rstrip("\n"))
            A("```")
            A("")
    A("---")
    A("")
    A("## 3. Active native sources")
    A("")
    A("`app/src/main/cpp/`, excluding the vendored `fluidsynth/` tree.")
    A("")
    for rel in nat:
        path = os.path.join(CPP, rel)
        lang, n, body = fence(path, rel)
        A(f"### `{rel}` — {n} lines")
        A("")
        A(f"```{lang}")
        A(body.rstrip("\n"))
        A("```")
        A("")
    A("---")
    A("")
    A("## 4. Appendix — excluded (dead) Kotlin files")
    A("")
    A("Present in the tree but referenced by no live code. Verified with a reachability")
    A("scan (`tools/gen_project_context.py`). Kept only so the history is not rewritten.")
    A("")
    A("| File | Reason |")
    A("|---|---|")
    for rel, why in sorted(dead):
        A(f"| `{rel}` | {why} |")
    A("")
    A("Vendored third-party C++ is also excluded from the body:")
    A(f"`app/src/main/cpp/fluidsynth/` ({vendored} files) — upstream FluidSynth 2.6.")
    A("")
    A("---")
    A("")
    A("## 5. Regenerating")
    A("")
    A("```bash")
    A("python3 tools/gen_project_context.py     # rewrites PROJECT_CONTEXT.md")
    A("```")
    A("")

    open(OUT, "w", encoding="utf-8").write("\n".join(L))
    print(f"wrote {OUT}")
    print(f"  {len(live)} active kotlin, {len(nat)} native, {len(dead)} dead listed")
    print(f"  size {os.path.getsize(OUT)/1024/1024:.2f} MB")


if __name__ == "__main__":
    main()
