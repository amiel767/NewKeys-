#!/usr/bin/env python3
"""Analyse a conversation dump written by conv_peek.py, without re-fetching.

Reads tools/.conv_peek/<CID>.json and prints only aggregates and the tail. The
dump is ~90 MB for a long conversation; this never prints it whole.

Usage
-----
    python3 tools/conv_analyse.py <CID>                # kinds + last message
    python3 tools/conv_analyse.py <CID> --tail 30      # last 30 events, one line
    python3 tools/conv_analyse.py <CID> --lastmsg      # last agent message only
    python3 tools/conv_analyse.py <CID> --grep PAT
"""
from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path

OUT = Path(__file__).resolve().parent / ".conv_peek"


def load(cid: str) -> list[dict]:
    p = OUT / f"{cid}.json"
    if not p.exists():
        raise SystemExit(f"no dump at {p}; run conv_peek.py {cid} first")
    return json.loads(p.read_text())


def kind(ev: dict) -> str:
    return ev.get("kind") or "?"


def _blocks(x) -> str:
    """Join the text of an OpenAI-style content list or a bare string."""
    if isinstance(x, str):
        return x
    if isinstance(x, list):
        return "\n".join(b.get("text", "") for b in x if isinstance(b, dict))
    return ""


def msg_text(ev: dict) -> str:
    lm = ev.get("llm_message")
    if isinstance(lm, dict):
        return _blocks(lm.get("content"))
    return _blocks(ev.get("content"))


def action_text(ev: dict) -> str:
    a = ev.get("action")
    if isinstance(a, dict):
        for k in ("command", "path", "file_text", "old_str", "new_str", "thought"):
            if isinstance(a.get(k), str):
                return a[k]
        return json.dumps(a)
    if isinstance(a, str):
        return a
    return ""


def one_line(ev: dict, n: int = 150) -> str:
    k = kind(ev)
    src = ev.get("source", "?")
    if k == "ActionEvent":
        t = action_text(ev)
    elif k == "ObservationEvent":
        obs = ev.get("observation")
        t = json.dumps(obs)[:n] if obs is not None else json.dumps(ev.get("value"))[:n]
    else:
        t = msg_text(ev) or json.dumps(ev.get("value"))[:n] if ev.get("value") is not None else ""
    return f"[{k}/{src}] " + " ".join(str(t).split())[:n]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("cid")
    ap.add_argument("--tail", type=int, default=0)
    ap.add_argument("--lastmsg", action="store_true")
    ap.add_argument("--grep")
    ap.add_argument("--width", type=int, default=160)
    a = ap.parse_args()

    evs = load(a.cid)
    print(f"events: {len(evs)}")
    print("kinds:", dict(Counter(kind(e) for e in evs).most_common()))
    if evs:
        print("last ts:", evs[-1].get("timestamp"))

    if a.grep:
        for i, e in enumerate(evs):
            blob = json.dumps(e.get("value"))[:20000]
            if a.grep.lower() in blob.lower():
                print(f"--- [{i}] {kind(e)} ---")
                print(" ".join(blob.split())[: a.width])
        return

    if a.lastmsg:
        for e in reversed(evs):
            if kind(e) == "MessageEvent" and msg_text(e).strip():
                print(f"--- {e.get('source')} {e.get('timestamp')} ---")
                print(msg_text(e)[:8000])
                return
        print("(no message event)")
        return

    if a.tail:
        for i, e in enumerate(evs[-a.tail :], start=len(evs) - a.tail):
            print(i, one_line(e, a.width))


if __name__ == "__main__":
    main()
