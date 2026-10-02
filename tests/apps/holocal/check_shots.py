#!/usr/bin/env python3
"""Checks tests/apps/holocal/holocal.script (1280x720 at 240 dpi).

    tools/build_apk.sh tests/apps/holocal
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/holocal/holocal.script --screenshots build/shots \\
        build/apps/holocal/holocal.apk > build/shots/holocal.log 2>&1
    python3 tests/apps/holocal/check_shots.py build/shots build/shots/holocal.log

March 2024, weeks starting Sunday, week numbers on. The 15th (a Friday) is selected:
the rest of its week is tinted and the day itself sits between two blue bars. A tap
selects Sunday the 10th. A swipe then shows April, with the 10th still selected.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

TINT = 0xFFCCE6E3
WHITE = 0xFFFFFFFF
BAR = 0xFF5BC3EA
WEEK = 0xFF008577
UNFOCUSED = 0xFF9E9E9E
TITLE = 0xFF666666
CHECKS = 19
LOG = ["day 2024-2-15", "day 2024-2-10"]


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    p = lambda name: os.path.join(d, name)  # noqa: E731
    fails += check(p("holocal_start.png"), [
        (623, 23, TITLE, "March 2024 title"),
        (229, 147, UNFOCUSED, "February day in gray"),
        (71, 327, WEEK, "week number"),
        (200, 360, TINT, "selected week tint"),
        (959, 340, BAR, "bar left of the 15th"),
        (1040, 360, WHITE, "the 15th's cell is clear"),
        (20, 700, 0xFFF5F5F5, "status bar"),
    ])
    fails += check(p("holocal_tenth.png"), [
        (200, 360, WHITE, "the 10th's cell is clear"),
        (1040, 360, TINT, "the 15th joins the week tint"),
        (1042, 332, 0xFF000000, "the 15 is still drawn"),
        (241, 334, 0xFF000000, "the 10 is still drawn"),
    ])
    fails += check(p("holocal_scrolled.png"), [
        (567, 41, TITLE, "April title"),
        (400, 150, 0xFF000000, "an April day"),
        (20, 700, 0xFFF5F5F5, "status bar still showing the 10th"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        bad = [l for l in text.splitlines() if "HLCHECK FAIL" in l]
        ok = len([l for l in text.splitlines() if "HLCHECK ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
        else:
            print("ok   %d logic checks" % ok)
        log = [l.split(": ", 1)[-1].strip() for l in text.splitlines()
               if l.startswith("I/HoloCal: day ")]
        if log != LOG:
            print("FAIL log: expected %s got %s" % (LOG, log))
            fails += 1
        else:
            print("ok   selection log")
    print("FAIL holocal (%d)" % fails if fails else "PASS holocal")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
