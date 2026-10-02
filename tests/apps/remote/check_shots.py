#!/usr/bin/env python3
"""Checks tests/apps/remote/remote.script (1280x720 at 240 dpi).

    tools/build_apk.sh tests/apps/remote
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/remote/remote.script --screenshots build/shots \\
        build/apps/remote/remote.apk > build/shots/remote.log 2>&1
    python3 tests/apps/remote/check_shots.py build/shots build/shots/remote.log

The card is a RemoteViews hierarchy: title, teal swatch, purple icon,
a 40 percent progress bar, two list rows, and an Open button. The second
shot is after that button's PendingIntent, which reapply()s an orange swatch.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

TEAL = 0xFF008577
ORANGE = 0xFFE65100
WHITE = 0xFFFFFFFF
TITLE = 0xFF212121
PURPLE = 0xFF673AB7
ROW = 0xFF1565C0
ROW2 = 0xFF2E7D32
TRACK = 0xFFD8D8D8
STATUS = 0xFF203040
STATUS_TEXT = 0xFFF5F5F5
CHECKS = 43


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    p = lambda name: os.path.join(d, name)  # noqa: E731
    # Coordinates come from the 1280x720 shots: title glyph, swatch, icon,
    # 40 percent progress, both list rows, the Open button, and the status line.
    fails += check(p("remote_card.png"), [
        (200, 30, TITLE, "title text"),
        (40, 80, TEAL, "teal swatch"),
        (160, 80, PURPLE, "icon bitmap"),
        (100, 195, TEAL, "progress fill"),
        (700, 195, TRACK, "progress track"),
        (8, 220, ROW, "first list row"),
        (8, 290, ROW2, "second list row"),
        (200, 400, TEAL, "Open button"),
        (40, 680, STATUS_TEXT, "status word"),
        (1200, 690, STATUS, "status bar"),
    ])
    fails += check(p("remote_open.png"), [
        (21, 32, TITLE, "title after reapply"),
        (40, 80, ORANGE, "swatch after the click"),
        (200, 400, TEAL, "Open button still teal"),
        (640, 405, WHITE, "Opened label"),
        (60, 686, STATUS_TEXT, "status word opened"),
        (1200, 690, STATUS, "status bar"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        bad = [l for l in text.splitlines() if "RVCHECK FAIL" in l]
        ok = len([l for l in text.splitlines() if "RVCHECK ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
        else:
            print("ok   %d logic checks" % ok)
    print("FAIL remote (%d)" % fails if fails else "PASS remote")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
