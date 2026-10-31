#!/usr/bin/env python3
"""Compact peek at one OpenHands Cloud conversation.

Writes the raw event stream to a JSON file on disk and prints only aggregates, so
a multi-megabyte stream never floods the terminal. That is the whole point: the
project's parallel conversations are watched every turn, and dumping a full
transcript costs tens of MB per turn.

Usage
-----
    python3 tools/conv_peek.py <CONV_ID>              # summary + last message
    python3 tools/conv_peek.py <CONV_ID> --tail 40    # last 40 event kinds
    python3 tools/conv_peek.py <CONV_ID> --last       # only the last agent text
    python3 tools/conv_peek.py <CONV_ID> --grep PAT   # events whose text matches

The raw dump goes to tools/.conv_peek/<CONV_ID>.json (gitignored).
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

API = "https://app.all-hands.dev/api/v1/conversation/{cid}/events/search"
OUT = Path(__file__).resolve().parent / ".conv_peek"


def token() -> str:
    tok = os.environ.get("OPENHANDS_API_KEY")
    if not tok:
        sys.exit("OPENHANDS_API_KEY is not set.")
    return tok


def fetch_all(cid: str) -> list[dict]:
    items: list[dict] = []
    page: object = 0
    for _ in range(400):
        url = f"{API.format(cid=cid)}?limit=100&page_id={page}"
        req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token()}"})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                data = json.load(r)
        except urllib.error.HTTPError as e:
            sys.exit(f"HTTP {e.code}: {e.read()[:200]!r}")
        batch = data.get("items", [])
        items.extend(batch)
        nxt = data.get("next_page_id")
        if nxt is None or not batch:
            break
        page = nxt
    return items


def text_of(ev: dict) -> str:
    """Best-effort plain text of an event, without pulling in huge blobs."""
    parts: list[str] = []
    for key in ("message", "content", "text", "thought", "args", "action"):
        v = ev.get(key)
        if isinstance(v, str):
            parts.append(v)
        elif isinstance(v, dict):
            for k in ("content", "text", "message", "command", "path", "thought"):
                if isinstance(v.get(k), str):
                    parts.append(v[k])
    return "\n".join(p for p in parts if p)


def kind_of(ev: dict) -> str:
    return (
        ev.get("kind")
        or ev.get("event")
        or ev.get("type")
        or (ev.get("action") or {}).get("kind")
        if isinstance(ev.get("action"), dict)
        else ev.get("kind") or ev.get("event") or ev.get("type") or "?"
    )


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("cid")
    ap.add_argument("--tail", type=int, default=0)
    ap.add_argument("--last", action="store_true")
    ap.add_argument("--grep")
    ap.add_argument("--maxchars", type=int, default=4000)
    a = ap.parse_args()

    evs = fetch_all(a.cid)
    OUT.mkdir(exist_ok=True)
    (OUT / f"{a.cid}.json").write_text(json.dumps(evs, ensure_ascii=False))

    if a.grep:
        for i, ev in enumerate(evs):
            t = text_of(ev)
            if a.grep.lower() in t.lower():
                print(f"--- [{i}] {kind_of(ev)} ---")
                print(t[: a.maxchars])
        return

    if a.last:
        for ev in reversed(evs):
            t = text_of(ev)
            if t.strip():
                print(t[: a.maxchars])
                break
        return

    print(f"events: {len(evs)}  bytes: {(OUT / f'{a.cid}.json').stat().st_size}")
    if evs:
        for k in ("kind", "event", "type"):
            if k in evs[0]:
                print(f"first key names: {sorted(evs[0].keys())[:12]}")
                break
    if a.tail:
        for i, ev in enumerate(evs[-a.tail :], start=len(evs) - a.tail):
            t = text_of(ev).replace("\n", " ")[:110]
            print(f"[{i}] {kind_of(ev)}: {t}")


if __name__ == "__main__":
    main()
