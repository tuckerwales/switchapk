#!/usr/bin/env python3
"""Checks tests/apps/outline/outline.script (1280x720 at 240 dpi).

    tools/build_apk.sh tests/apps/outline
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/outline/outline.script --screenshots build/shots \\
        build/apps/outline/outline.apk > build/shots/outline.log 2>&1
    python3 tests/apps/outline/check_shots.py build/shots build/shots/outline.log

A 200dp square is clipped to a 48dp round rect. Corner pixels are the page;
the flat edge and the inside of the arc stay yellow.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

PAGE = 0xFF101820
YELLOW = 0xFFFFF59D
CHECKS = 3


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    p = lambda name: os.path.join(d, name)  # noqa: E731
    # Box is 72,180 300x300, radius 72. Locked from outline.png.
    fails += check(p("outline.png"), [
        (20, 20, PAGE, "page"),
        (74, 182, PAGE, "clipped corner"),
        (371, 479, PAGE, "opposite corner"),
        (95, 203, YELLOW, "inside the arc"),
        (144, 180, YELLOW, "top edge"),
        (222, 330, YELLOW, "center"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        bad = [l for l in text.splitlines() if "CLIP FAIL" in l]
        ok = len([l for l in text.splitlines() if "CLIP ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
        else:
            print("ok   %d logic checks" % ok)
    print("FAIL outline (%d)" % fails if fails else "PASS outline")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
