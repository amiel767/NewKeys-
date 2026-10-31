#!/usr/bin/env python3
"""Watch a peer conversation and print ONLY new messages, one line each.

Reuses the paging of conv_peek but keeps the state on disk, so a poll costs a
couple of lines of terminal output instead of a full transcript. That is the
point: watching a peer must not inflate this conversation.

Usage
-----
    python3 tools/conv_watch.py <CID> --once      # one poll, print new messages
    python3 tools/conv_watch.py <CID> --full      # print the last message now

State: tools/.conv_watch_<CID>.json holds the count already seen.
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
            with urllib.request.urlopen(req, timeout=90) as r:
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


def blocks(x) -> str:
    if isinstance(x, str):
        return x
    if isinstance(x, list):
        return "\n".join(b.get("text", "") for b in x if isinstance(b, dict))
    return ""


def msg_text(ev: dict) -> str:
    lm = ev.get("llm_message")
    if isinstance(lm, dict):
        return blocks(lm.get("content"))
    return blocks(ev.get("content"))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("cid")
    ap.add_argument("--once", action="store_true")
    ap.add_argument("--full", action="store_true")
    ap.add_argument("--reset", action="store_true")
    a = ap.parse_args()

    OUT.mkdir(exist_ok=True)
    state = OUT / f".watch_{a.cid}.json"
    seen = 0
    if state.exists() and not a.reset:
        seen = json.loads(state.read_text()).get("n", 0)

    evs = fetch_all(a.cid)
    (OUT / f"{a.cid}.json").write_text(json.dumps(evs, ensure_ascii=False))
    msgs = [e for e in evs if e.get("kind") == "MessageEvent" and msg_text(e).strip()]

    if a.full or seen == 0:
        new = msgs[-1:] if msgs else []
    else:
        new = msgs[seen:]

    print(f"events={len(evs)} messages={len(msgs)} new={len(new)}")
    for e in new:
        t = " ".join(msg_text(e).split())
        print(f"### {e.get('source')} {e.get('timestamp')} len={len(t)}")
        print(t[:6000])
    state.write_text(json.dumps({"n": len(msgs)}))


if __name__ == "__main__":
    main()
