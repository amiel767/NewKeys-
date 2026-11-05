#!/usr/bin/env python3
"""Page de revue : le diff REEl des changements demandes, lisible au navigateur.

Le brief ne porte pas sur tout l'historique : trois demandes (REQ1 setlist a 0
piste, REQ2 choix de source une seule fois, persistance de l'instrument AEther).
Cette page ne montre que les commits qui les traitent, et seulement le code
(app/, pas la memoire ni les captures).

Usage : python3 tools/changes_page.py [base] [head]
        defaut : abcc0d2 (dernier commit pousse par l'autre conversation) -> HEAD
"""
from __future__ import annotations

import html
import subprocess
import sys
from pathlib import Path

OUT = Path("/workspace/dl/pub/changes/index.html")

# Les commits de la demande, dans l'ordre. Un libelle par commit, ecrit ici et
# pas deduit du message git : le message git explique le "comment", l'entete
# doit dire ce que l'utilisateur a demande.
WANTED = [
    ("d39264f", "REQ1 + REQ2 — setlist a 0 piste, choix de source unique, Save AEther, RAM dechargee"),
    ("caa7426", "Persistance de l'instrument AEther (survit au redemarrage)"),
    ("d88f594", "memoire (hors demande)"),
]

STYLE = """
:root{--bg:#0d0f13;--panel:#151922;--line:#242a36;--ink:#e8ecf3;--dim:#8b95a7;
--gold:#d9a441;--add:#4ec9a0;--del:#e06c75;--tag:#7aa2f7}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);
font:15px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}
header{padding:28px 32px 20px;border-bottom:1px solid var(--line);
background:linear-gradient(180deg,#171c26,#0d0f13)}
h1{margin:0 0 6px;font-size:22px;letter-spacing:.2px}
h1 span{color:var(--gold)}
.sub{color:var(--dim);font-size:13.5px}
.sub code{background:#1d2330;padding:1px 6px;border-radius:5px;color:var(--gold);font-size:12.5px}
.wrap{padding:22px 32px 60px;max-width:1180px}
.req{background:var(--panel);border:1px solid var(--line);border-radius:12px;
padding:18px 20px;margin:0 0 20px}
.req h2{margin:0 0 4px;font-size:16.5px}
.req .why{color:var(--dim);font-size:13.5px;margin:0 0 14px}
.req .why b{color:var(--ink);font-weight:600}
.files{display:flex;flex-wrap:wrap;gap:6px;margin:0 0 12px}
.files span{background:#1d2330;border:1px solid var(--line);color:var(--tag);
font:12px/1 ui-monospace,SFMono-Regular,Menlo,monospace;padding:5px 8px;border-radius:6px}
.files span.new{color:var(--add)}
details{border:1px solid var(--line);border-radius:9px;background:#0f131a;margin-top:10px}
details>summary{cursor:pointer;padding:9px 13px;font-size:13px;color:var(--gold);
list-style:none;user-select:none}
details>summary::-webkit-details-marker{display:none}
details>summary::before{content:"+ ";color:var(--dim)}
details[open]>summary::before{content:"- "}
pre{margin:0;padding:13px 15px;overflow-x:auto;border-top:1px solid var(--line);
font:12.5px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace;white-space:pre}
.l-add{color:var(--add)}
.l-del{color:var(--del)}
.l-meta{color:var(--gold)}
.l-hunk{color:var(--tag);opacity:.85}
.l-ctx{color:var(--dim)}
footer{color:var(--dim);font-size:12.5px;padding:0 32px 40px}
footer a{color:var(--tag)}
"""


def sh(*args: str) -> str:
    return subprocess.run(args, capture_output=True, text=True, check=True).stdout


def colorize(diff: str) -> str:
    out = []
    for line in diff.splitlines():
        if line.startswith("+++") or line.startswith("---") or line.startswith("diff "):
            cls = "l-meta"
        elif line.startswith("@@"):
            cls = "l-hunk"
        elif line.startswith("+"):
            cls = "l-add"
        elif line.startswith("-"):
            cls = "l-del"
        else:
            cls = "l-ctx"
        out.append(f'<span class="{cls}">{html.escape(line)}</span>')
    return "\n".join(out)


def commit_meta(rev: str) -> tuple[str, str]:
    msg = sh("git", "log", "-1", "--format=%s", rev).strip()
    date = sh("git", "log", "-1", "--format=%cd", "--date=format:%Y-%m-%d %H:%M", rev).strip()
    return msg, date


def main() -> None:
    base = sys.argv[1] if len(sys.argv) > 1 else "abcc0d2"
    head = sys.argv[2] if len(sys.argv) > 2 else "HEAD"
    head_sha = sh("git", "rev-parse", "--short", head).strip()

    parts = [
        "<!doctype html><html lang=fr><meta charset=utf-8>",
        "<meta name=viewport content='width=device-width,initial-scale=1'>",
        "<title>Changements — SoundStage Mixer</title>",
        f"<style>{STYLE}</style>",
        "<header><h1>Changements demandes — <span>SoundStage Mixer</span></h1>",
        f"<div class=sub>Base <code>{base}</code> → <code>{head_sha}</code> · "
        "diff du code (<code>app/</code>) uniquement, la memoire et les captures sont exclues.</div></header>",
        "<div class=wrap>",
    ]

    for rev, label in WANTED:
        try:
            msg, date = commit_meta(rev)
        except subprocess.CalledProcessError:
            continue
        files = [f for f in sh("git", "show", "--name-only", "--format=", rev).splitlines()
                 if f.startswith("app/") and f.endswith((".kt", ".h", ".cpp"))]
        diff = sh("git", "show", "--format=", rev, "--", "app/")
        parts.append("<section class=req>")
        parts.append(f"<h2>{html.escape(label)}</h2>")
        parts.append(f"<p class=why>{html.escape(msg)} — <b>{date}</b></p>")
        if files:
            chips = "".join(
                f"<span class='{ 'new' if 'AetherJson' in f or 'Test' in f else '' }'>{html.escape(f)}</span>"
                for f in files
            )
            parts.append(f"<div class=files>{chips}</div>")
        if diff.strip():
            n = len([l for l in diff.splitlines() if l.startswith(("+", "-"))])
            parts.append("<details open><summary>voir le diff "
                         f"({n} lignes modifiees)</summary>"
                         f"<pre>{colorize(diff)}</pre></details>")
        else:
            parts.append("<p class=why>Aucun changement de code dans ce commit.</p>")
        parts.append("</section>")

    parts.append("</div>")
    parts.append(f"<footer>Genere depuis le depot local par "
                 f"<code>tools/changes_page.py</code>. Commit de tete : <code>{head_sha}</code>.</footer>")
    parts.append("</html>")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(parts), encoding="utf-8")
    print(f"ecrit {OUT} ({OUT.stat().st_size} o)")


if __name__ == "__main__":
    main()
