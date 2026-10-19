#!/usr/bin/env python3
"""Audits font sizes: every JSX fontSize literal against the Kotlin .sp literals.

The previews draw an 891 x 411 canvas that maps 1:1 to the 891 x 411 dp device
screen, so a JSX `fontSize: N` must appear as `N.sp` in the matching Kotlin file.
Any value that has no counterpart, or whose closest counterpart differs by more
than the tolerance, is a candidate for a "disproportionate" text size.
"""
import re
import sys
from collections import Counter


def jsx_sizes(path):
    txt = open(path, encoding="utf-8").read()
    return Counter(float(v) for v in re.findall(r"fontSize:\s*([0-9]+(?:\.[0-9]+)?)", txt))


def kt_sizes(path):
    txt = open(path, encoding="utf-8").read()
    vals = re.findall(r"fontSize\s*=\s*([0-9]+(?:\.[0-9]+)?)\.sp", txt)
    vals += re.findall(r"fontSize\s*:\s*TextUnit", txt)
    return Counter(float(v) for v in vals)


def main():
    jsx, kt = sys.argv[1], sys.argv[2]
    j = jsx_sizes(jsx)
    k = kt_sizes(kt)
    print(f"== {jsx}  vs  {kt}")
    print("JSX sizes (size x count):", dict(sorted(j.items())))
    print("KOTLIN sp  (size x count):", dict(sorted(k.items())))
    print()
    print("JSX size -> nearest Kotlin sp")
    ks = sorted(k)
    for v in sorted(j):
        if not ks:
            print(f"  {v:5} -> (none)")
            continue
        near = min(ks, key=lambda x: abs(x - v))
        flag = "" if abs(near - v) <= 0.5 else "   <-- MISMATCH"
        print(f"  {v:5} -> {near:5}{flag}")
    print()
    print("Kotlin sp with no JSX counterpart nearby")
    js = sorted(j)
    for v in ks:
        near = min(js, key=lambda x: abs(x - v))
        if abs(near - v) > 0.5:
            print(f"  {v:5} (nearest JSX {near})")


if __name__ == "__main__":
    main()
