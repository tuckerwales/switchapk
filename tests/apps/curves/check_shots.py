#!/usr/bin/env python3
"""Checks tests/apps/curves/curves.script (1280x720 at 240 dpi, density 1.5).

    tools/build_apk.sh tests/apps/curves
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/curves/curves.script --screenshots build/shots \\
        build/apps/curves/curves.apk > build/shots/curves.log 2>&1
    python3 tests/apps/curves/check_shots.py build/shots build/shots/curves.log

The page is #FF101820. Six 40dp squares start at x=72 (a 48dp margin, so a
negative translation is not clipped as padding) and slide translationX
0 to 240 over 4s. The shot is about t=2s, fraction 0.5.
Row centers are y=54, 126, 198, 270, 342, 414.

    linear     +120  center x=222
    overshoot  +270  center x=372  (tension 2 is 1.125)
    anticipate -30   center x=72   (tension 2 is -0.125)
    bounce     +168  center x=270  (about 0.702)
    cycle      +0    center x=102  (sin(pi) is 0)
    path       +216  center x=318  (quad control (0, 1) is about 0.90)

A purple stroke morphs from viewport x 10..40 to 160..190. The view is at
(780, 36), 300 by 60, viewport 200 by 40. Halfway the bar is x 85..115,
so (930, 66) is purple and the endpoint centers are page.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

PAGE = 0xFF101820
RED = 0xFFE53935
GREEN = 0xFF43A047
BLUE = 0xFF1E88E5
AMBER = 0xFFFFC107
PURPLE = 0xFFAB47BC
ORANGE = 0xFFFF6F00
MORPH = 0xFF8E24AA
CHECKS = 11


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    mid = os.path.join(d, "mid.png")
    fails += check(mid, [
        (20, 20, PAGE, "page"),
        (222, 54, RED, "linear halfway"),
        (102, 54, PAGE, "linear left"),
        (372, 126, GREEN, "overshoot past the end"),
        (320, 126, PAGE, "overshoot left the end value"),
        (222, 126, PAGE, "overshoot not linear"),
        (50, 198, BLUE, "anticipate behind the start"),
        (120, 198, PAGE, "anticipate left the start"),
        (222, 198, PAGE, "anticipate not linear"),
        (270, 270, AMBER, "bounce"),
        (222, 270, PAGE, "bounce not linear"),
        (102, 342, PURPLE, "cycle back at the start"),
        (222, 342, PAGE, "cycle not linear"),
        (318, 414, ORANGE, "path ease"),
        (222, 414, PAGE, "path not linear"),
        (930, 66, MORPH, "morph halfway"),
        (818, 66, PAGE, "morph left the start"),
        (1042, 66, PAGE, "morph not at the end"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        bad = [line for line in text.splitlines() if "CURVES FAIL" in line]
        ok = len([line for line in text.splitlines() if "CURVES ok" in line])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
        else:
            print("ok   %d logic checks" % ok)
    print("FAIL curves (%d)" % fails if fails else "PASS curves")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
