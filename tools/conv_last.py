#!/usr/bin/env python3
"""Print the LAST meaningful events of a Cloud conversation.

Reuses conv_sync's paging and describe() so the output is the same summary the
watchdog produces, but ordered oldest -> newest and tailed. `conv_tail.py` was
wrong: `page_id=1` is the OLDEST page, so reading page 1..3 shows the start of the
conversation, not its end. This walks every page via `next_page_id`.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import conv_sync  # noqa: E402

cid = sys.argv[1] if len(sys.argv) > 1 else conv_sync.DEFAULT_CONV
want = int(sys.argv[2]) if len(sys.argv) > 2 else 60

events = conv_sync.fetch_all(cid)
print(f"{len(events)} events; showing the last {want}")
out = []
for ev in events:
    line = conv_sync.describe(ev)
    if line:
        out.append(line)
for line in out[-want:]:
    print(line)
