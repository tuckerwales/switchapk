#!/usr/bin/env python3
"""Checks tests/apps/motion/motion.script (1280x720 at 240 dpi, density 1.5).

    tools/build_apk.sh tests/apps/motion
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/motion/motion.script --screenshots build/shots \\
        build/apps/motion/motion.apk > build/shots/motion.log 2>&1
    python3 tests/apps/motion/check_shots.py build/shots build/shots/motion.log

The page is #FF101820. A yellow 80dp square sits at (36, 36). Its pressed
state slides translationX 0 to 240 over 4s, linear, after the tap timeout.
The press shot is about 1.9s in, so the square covers (200, 96) and has
left (96, 96). Mid and end keep it on (96, 96).

Three 64dp squares in a column at y 288, 396 and 504 slide from x +180 to
0 over 4s, staggered by 0.5. Mid: red at +90 (174, 288), green and blue
still at +180 (264). Press: red home (84, 288), green at +90, blue at
+180. The end shot is after the stagger finishes; those pixels are not
checked.

An animated vector at (750, 36), 300 by 60, trims a full-height amber
stroke from 0 to 1 over 4s. Mid: (800, 66) amber and (980, 66) page.
Press and end: both amber.

An orange square fades in at (810, 270) over 3s. Mid is neither the page
nor orange. Press and end are solid orange.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check, close, pixel, read_png  # noqa: E402

PAGE = 0xFF101820
YELLOW = 0xFFFFF59D
RED = 0xFFE53935
GREEN = 0xFF43A047
BLUE = 0xFF1E88E5
AMBER = 0xFFFFC107
ORANGE = 0xFFFF6F00
CHECKS = 8


def not_endpoint(path, x, y, a, b, label):
    w, h, px = read_png(path)
    got = pixel(px, w, x, y)
    bad = close(got, a, 6) or close(got, b, 6)
    print("%s %s (%d,%d) %s: got #%08X" % ("FAIL" if bad else "ok  ", path.split("/")[-1], x, y, label, got))
    return 1 if bad else 0


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    p = lambda name: os.path.join(d, name)  # noqa: E731
    mid = p("mid.png")
    press = p("press.png")
    end = p("end.png")
    fails += check(mid, [
        (20, 20, PAGE, "page"),
        (96, 96, YELLOW, "state rest"),
        (200, 96, PAGE, "state not pressed"),
        (174, 288, RED, "row0 halfway"),
        (264, 396, GREEN, "row1 waiting"),
        (264, 504, BLUE, "row2 waiting"),
        (800, 66, AMBER, "trim started"),
        (980, 66, PAGE, "trim not there"),
    ])
    fails += not_endpoint(mid, 810, 270, PAGE, ORANGE, "fade in between")
    fails += check(press, [
        (96, 96, PAGE, "state left behind"),
        (200, 96, YELLOW, "state sliding"),
        (340, 96, PAGE, "state not yet"),
        (84, 288, RED, "row0 home"),
        (174, 396, GREEN, "row1 halfway"),
        (264, 504, BLUE, "row2 waiting"),
        (800, 66, AMBER, "trim full left"),
        (980, 66, AMBER, "trim full right"),
        (810, 270, ORANGE, "fade done"),
    ])
    fails += check(end, [
        (96, 96, YELLOW, "state released"),
        (200, 96, PAGE, "state back"),
        (800, 66, AMBER, "trim stays"),
        (980, 66, AMBER, "trim stays right"),
        (810, 270, ORANGE, "fade stays"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        bad = [l for l in text.splitlines() if "MOTION FAIL" in l]
        ok = len([l for l in text.splitlines() if "MOTION ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
        else:
            print("ok   %d logic checks" % ok)
    print("FAIL motion (%d)" % fails if fails else "PASS motion")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
