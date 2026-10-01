#!/usr/bin/env python3
"""Checks tests/apps/scroll/scroll.script (1280x720 at 240 dpi, density 1.5).

A 16dp status strip sits above two panes. Each pane holds four 320dp bands
(red, blue, green, gold), 480px at this density. A slow swipe moves about
one band, so the sample pixel leaves red and lands in blue. Touch slop eats
a few pixels of the gesture, and the swipe is long enough that it does not fling.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import close, pixel, read_png  # noqa: E402

GREEN = 0xFF4CAF50
RED = 0xFFE53935
BLUE = 0xFF1565C0


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    fails += shot(os.path.join(d, "scroll_before.png"), [
        (10, 4, GREEN, "in-process checks"),
        (40, 80, RED, "vertical band at rest"),
        (700, 80, RED, "horizontal band at rest"),
    ])
    fails += shot(os.path.join(d, "scroll_vert.png"), [
        (10, 4, GREEN, "checks still passing"),
        (40, 80, BLUE, "vertical swipe brought blue up"),
        (700, 80, RED, "horizontal pane stayed put"),
    ])
    fails += shot(os.path.join(d, "scroll_horiz.png"), [
        (10, 4, GREEN, "checks still passing"),
        (40, 80, BLUE, "vertical pane stayed scrolled"),
        (700, 80, BLUE, "horizontal swipe brought blue across"),
    ])
    print("FAIL: %d" % fails if fails else "PASS scroll")
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
