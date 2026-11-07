#!/usr/bin/env python3
"""Bounded reader for a conv dump (tools/.eco/stream_<cid>.json)."""
import json, sys, re
from collections import Counter

path = sys.argv[1]
mode = sys.argv[2] if len(sys.argv) > 2 else "users"
budget = int(sys.argv[3]) if len(sys.argv) > 3 else 300

ev = json.load(open(path))
if isinstance(ev, dict):
    ev = ev.get("items") or ev.get("events") or []

def kind(e):
    return e.get("kind") or e.get("type") or ""

def text_of(e):
    m = e.get("llm_message")
    if isinstance(m, dict):
        c = m.get("content")
        if isinstance(c, list):
            return " ".join(b.get("text", "") for b in c if isinstance(b, dict))
        if isinstance(c, str):
            return c
    for k in ("message", "text"):
        v = e.get(k)
        if isinstance(v, str):
            return v
        if isinstance(v, dict):
            c = v.get("content")
            if isinstance(c, list):
                return " ".join(b.get("text", "") for b in c if isinstance(b, dict))
    return ""

def norm(t):
    return re.sub(r"\s+", " ", t).strip()

users, agents = [], []
for i, e in enumerate(ev):
    src, k = e.get("source"), kind(e)
    if src == "user" and "Message" in k:
        t = text_of(e)
        if t.strip():
            users.append((i, t, e.get("timestamp", "")[:19]))
    if src == "agent" and "Message" in k:
        t = text_of(e)
        if t.strip():
            agents.append((i, t))

if mode == "users":
    print(f"# {len(users)} user, {len(agents)} agent, {len(ev)} events")
    for i, t, ts in users:
        t = norm(t)
        print(f"\n--- U@{i} {ts} ({len(t)} ch) ---")
        print(t[:budget])
elif mode == "kinds":
    for k, n in Counter((kind(e), e.get("source")) for e in ev).most_common(20):
        print(n, k)
elif mode == "agents":
    for i, t in agents[-budget:]:
        t = norm(t)
        print(f"\n--- A@{i} ({len(t)} ch) ---")
        print(t[:2500])
