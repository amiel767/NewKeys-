#!/usr/bin/env python3
"""Lit un dump local tools/.conv_peek/<cid>.json et n'imprime que les messages
(role + debut du texte), jamais le flux complet. Zero reseau."""
import json
import sys


def text_of(m):
    out = []
    for c in m.get("content", []) or []:
        if isinstance(c, dict) and c.get("type") == "text":
            out.append(c.get("text", ""))
    return " ".join(out)


def main():
    for cid in sys.argv[1:]:
        try:
            d = json.load(open("tools/.conv_peek/%s.json" % cid))
        except Exception as e:
            print("=== %s : illisible (%s)" % (cid[:8], e))
            continue
        ev = d if isinstance(d, list) else d.get("events", [])
        msgs = [e for e in ev if e.get("kind") == "MessageEvent"]
        print("=== %s : %d messages" % (cid[:8], len(msgs)))
        for e in msgs[:2] + msgs[-2:]:
            m = e.get("llm_message", {})
            t = text_of(m).replace("\n", " ")
            print("  [%s] %s" % (m.get("role", "?"), t[:260]))


if __name__ == "__main__":
    main()
