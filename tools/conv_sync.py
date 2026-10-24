#!/usr/bin/env python3
"""Watch a parallel OpenHands conversation and report drift since the last check.

The project is developed by several agents at once. This script polls one Cloud
conversation, diffs its event stream against a local watermark, and prints what
changed: user instructions, agent reports, file edits and git commands.

Usage
-----
    python3 tools/conv_sync.py                 # incremental: only new events
    python3 tools/conv_sync.py --full          # whole stream, ignore watermark
    python3 tools/conv_sync.py --since <ts>    # explicit ISO timestamp
    python3 tools/conv_sync.py --json out.json # also dump the new events

Watermark lives in tools/.conv_sync_state.json (gitignored). Run this BEFORE and
AFTER every change so the two agents do not edit the same file at once.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

DEFAULT_CONV = "dde6dffb48c5475a931918c2e3ff38f9"
API = "https://app.all-hands.dev/api/v1/conversation/{cid}/events/search"
STATE = Path(__file__).resolve().parent / ".conv_sync_state.json"


def token() -> str:
    tok = os.environ.get("OPENHANDS_API_KEY")
    if not tok:
        sys.exit("OPENHANDS_API_KEY is not set; export it before running.")
    return tok


def fetch_all(cid: str) -> list[dict]:
    """Walk every page of the conversation event stream."""
    items: list[dict] = []
    page: object = 0
    pages = 0
    while True:
        url = f"{API.format(cid=cid)}?limit=100&page_id={page}"
        req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token()}"})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                data = json.load(r)
        except urllib.error.HTTPError as e:
            sys.exit(f"HTTP {e.code} fetching events: {e.read()[:200]!r}")
        batch = data.get("items", [])
        items.extend(batch)
        nxt = data.get("next_page_id")
        pages += 1
        if nxt is None or not batch or pages > 200:
            break
        page = nxt
    return items


def text_of(ev: dict) -> str:
    for key in ("llm_message", "observation", "thought"):
        block = ev.get(key)
        if not block:
            continue
        content = block.get("content") if isinstance(block, dict) else None
        if isinstance(content, list):
            return "\n".join(c.get("text", "") for c in content if isinstance(c, dict))
        if isinstance(content, str):
            return content
    return ""


def describe(ev: dict) -> str | None:
    """One-line summary of an event worth reporting, or None to skip it."""
    src = ev.get("source")
    kind = ev.get("kind")
    ts = ev.get("timestamp", "")

    if kind == "MessageEvent" and src == "user":
        return f"[{ts}] USER: {text_of(ev).strip()[:400]}"
    if kind == "MessageEvent" and src == "agent":
        body = text_of(ev).strip()
        if body:
            return f"[{ts}] AGENT: {body[:400]}"
        return None
    if kind != "ActionEvent":
        return None

    tool = ev.get("tool_name")
    action = ev.get("action") or {}
    if tool == "file_editor":
        path = action.get("path", "?")
        return f"[{ts}] EDIT {action.get('command')} {path}"
    if tool == "terminal":
        cmd = action.get("command", "") or ""
        if any(k in cmd for k in ("git commit", "git push", "git rebase", "git merge", "git reset", "workflow")):
            return f"[{ts}] GIT: {cmd[:300]}"
    return None


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--conv", default=DEFAULT_CONV, help="conversation id")
    ap.add_argument("--full", action="store_true", help="ignore the watermark")
    ap.add_argument("--since", help="ISO timestamp; report events strictly after it")
    ap.add_argument("--json", dest="dump", help="write the new events to this file")
    ap.add_argument("--quiet", action="store_true", help="only print the summary line")
    args = ap.parse_args()

    state = {}
    if STATE.exists():
        try:
            state = json.loads(STATE.read_text())
        except json.JSONDecodeError:
            state = {}

    since = None
    if args.since:
        since = args.since
    elif not args.full:
        since = state.get(args.conv)

    events = fetch_all(args.conv)
    # Events arrive in chronological order; keep those strictly after the watermark.
    if since:
        fresh = [e for e in events if e.get("timestamp", "") > since]
    else:
        fresh = events

    lines = [d for d in (describe(e) for e in fresh) if d]
    if not args.quiet:
        for ln in lines:
            print(ln)
    last_ts = events[-1].get("timestamp") if events else None
    print(f"--- {len(fresh)} new / {len(events)} total events; last={last_ts}")

    if args.dump:
        Path(args.dump).write_text(json.dumps(fresh, ensure_ascii=False, indent=1))

    if last_ts:
        state[args.conv] = last_ts
        STATE.write_text(json.dumps(state, indent=1))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
