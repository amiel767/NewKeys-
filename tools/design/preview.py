#!/usr/bin/env python3
"""Serve a design preview and capture it headless, the economical way.

Two subcommands:

    serve <dir> [port]      static HTTP server rooted at <dir> (default 12000)
    shot  <url> <out.png>   chromium headless screenshot

Chromium runs with --virtual-time-budget so animations settle without a sleep.
The screenshot is written to disk; only its size is printed, never the pixels.
"""
from __future__ import annotations

import argparse
import http.server
import os
import socketserver
import subprocess
import sys
from pathlib import Path


def serve(root: str, port: int) -> None:
    os.chdir(root)

    class Handler(http.server.SimpleHTTPRequestHandler):
        def log_message(self, *a):  # keep the terminal quiet
            pass

    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("0.0.0.0", port), Handler) as httpd:
        print(f"serving {root} on :{port}", flush=True)
        httpd.serve_forever()


def shot(url: str, out: str, size: str, budget: int) -> None:
    cmd = [
        "chromium", "--headless", "--no-sandbox", "--disable-gpu",
        "--hide-scrollbars", f"--window-size={size}",
        f"--virtual-time-budget={budget}",
        f"--screenshot={out}", url,
    ]
    r = subprocess.run(cmd, capture_output=True, text=True)
    p = Path(out)
    if p.exists():
        print(f"{out} {p.stat().st_size} bytes")
    else:
        sys.exit(f"capture failed: {r.stderr[-300:]}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("serve")
    s.add_argument("root")
    s.add_argument("port", type=int, nargs="?", default=12000)
    c = sub.add_parser("shot")
    c.add_argument("url")
    c.add_argument("out")
    c.add_argument("--size", default="891,411")
    c.add_argument("--budget", type=int, default=8000)
    args = ap.parse_args()

    if args.cmd == "serve":
        serve(args.root, args.port)
    else:
        shot(args.url, args.out, args.size, args.budget)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
