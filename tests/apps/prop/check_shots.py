#!/usr/bin/env python3
"""Checks tests/apps/prop/prop.script (1280x720 at 240 dpi, density 1.5).

    tools/build_apk.sh tests/apps/prop
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/prop/prop.script --screenshots build/shots \\
        build/apps/prop/prop.apk > build/shots/prop.log 2>&1
    python3 tests/apps/prop/check_shots.py build/shots build/shots/prop.log

The page is #FF101820. A 200dp yellow square starts at (72, 180) and slides
300px right over 6s (50px/s, linear) via View.animate().translationX. A 2s
wait should catch it in motion: the pixel it left is the page, a pixel in
the traveled range is yellow, and a pixel it has not reached is still the
page. That holds for a travel of about 50 to 180px. The end shot keeps the
square over x [372, 672].

An 80dp blue square at (72, 630) scales to half size around its center in
400ms and stays there, so both shots show the old corner as the page and
the center still blue. A 40dp red square at (24, 24) fades out over 6s
through ObjectAnimator.ofFloat(..., "alpha", ...): the middle is neither
red nor the page, and the end is the page.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check, close, pixel, read_png  # noqa: E402

PAGE = 0xFF101820
YELLOW = 0xFFFFF59D
BLUE = 0xFF1565C0
RED = 0xFFFF5252
CHECKS = 9


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
    end = p("end.png")
    fails += check(mid, [
        (20, 20, PAGE, "page"),
        (100, 300, PAGE, "slide left behind"),
        (420, 300, YELLOW, "slide arrived"),
        (560, 300, PAGE, "slide not yet"),
        (74, 632, PAGE, "scale corner"),
        (132, 690, BLUE, "scale center"),
    ])
    fails += not_endpoint(mid, 54, 54, PAGE, RED, "fade in between")
    fails += check(end, [
        (20, 20, PAGE, "page"),
        (100, 330, PAGE, "slide start"),
        (450, 330, YELLOW, "slide end"),
        (700, 330, PAGE, "past slide"),
        (74, 632, PAGE, "scale corner stays"),
        (132, 690, BLUE, "scale center stays"),
        (54, 54, PAGE, "fade gone"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        bad = [l for l in text.splitlines() if "PROP FAIL" in l]
        ok = len([l for l in text.splitlines() if "PROP ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
        else:
            print("ok   %d logic checks" % ok)
    print("FAIL prop (%d)" % fails if fails else "PASS prop")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
