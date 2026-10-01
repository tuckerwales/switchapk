#!/usr/bin/env python3
"""Checks tests/apps/relative/relative.script (1280x720 at 240 dpi).

RelativeLayout with 10dp (15px) padding: a header aligned to the top, a red
box below it, a green box to its end stretched to the parent end, a gold
square centered in the parent, a footer at the bottom and a purple box
above the footer at the right. The status label turns green when the
in-process position checks pass.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import close, pixel, read_png  # noqa: E402

CHECKS = [
    (640, 40, 0xFF1565C0, "header below the top padding"),
    (80, 130, 0xFFE53935, "red box below the header"),
    (1200, 130, 0xFF43A047, "green box stretched to the end"),
    (640, 360, 0xFFFFB300, "gold square centered"),
    (640, 690, 0xFF6D4C41, "footer at the bottom"),
    (1230, 640, 0xFF8E24AA, "purple box above the footer"),
    (606, 184, 0xFF4CAF50, "in-process checks passed"),
    (5, 5, 0xFFFAFAFA, "padding left empty"),
]


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    path = os.path.join(d, "relative.png")
    w, h, px = read_png(path)
    fails = 0
    for x, y, color, label in CHECKS:
        got = pixel(px, w, x, y)
        ok = close(got, color, 6)
        print("%s (%d,%d) %s: got #%08X want #%08X" % ("ok  " if ok else "FAIL", x, y, label, got, color))
        if not ok:
            fails += 1
    print("FAIL: %d" % fails if fails else "PASS relative")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
