#!/usr/bin/env python3
"""Checks tests/apps/list/list.script (1280x720 at 240 dpi, density 1.5).

A 16dp status strip sits above the list. Rows are 160dp (240px) and colored
in pairs: red, blue, green, gold. A slow 600px swipe scrolls about 588px
after touch slop, so the sample pixel leaves red and lands in blue. The
swipe is long enough that it does not fling. Blue stays valid while the
scroll distance is in about [424, 904).
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import close, pixel, read_png  # noqa: E402

GREEN = 0xFF4CAF50
RED = 0xFFE53935
BLUE = 0xFF1565C0
FAIL = 0xFFF44336


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    fails += shot(os.path.join(d, "list_before.png"), [
        (10, 4, GREEN, "in-process checks"),
        (40, 80, RED, "row 0 at rest"),
    ])
    fails += shot(os.path.join(d, "list_after.png"), [
        (10, 4, GREEN, "checks still passing"),
        (40, 80, BLUE, "swipe brought a blue row up"),
    ])
    print("FAIL: %d" % fails if fails else "PASS list")
    return 1 if fails else 0


def shot(path, expectations):
    w, h, px = read_png(path)
    fails = 0
    for x, y, color, label in expectations:
        got = pixel(px, w, x, y)
        ok = close(got, color, 6)
        print("%s %s (%d,%d) %s: got #%08X want #%08X" % (
            "ok  " if ok else "FAIL", os.path.basename(path), x, y, label, got, color))
        if not ok:
            fails += 1
    return fails


if __name__ == "__main__":
    sys.exit(main())
