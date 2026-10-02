#!/usr/bin/env python3
"""Checks tests/apps/cutout (1280x720 at 240 dpi).

    tools/build_apk.sh tests/apps/cutout
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/cutout/cutout.script --screenshots build/shots \\
        build/apps/cutout/cutout.apk > build/shots/cutout.log 2>&1
    python3 tests/apps/cutout/check_shots.py build/shots build/shots/cutout.log

A 200dp yellow square sits on the page. The activity checks DisplayCutout,
WindowInsets, and the layout-in-cutout mode from the theme.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

PAGE = 0xFF101820
YELLOW = 0xFFFFF59D
CHECKS = 15


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    p = lambda name: os.path.join(d, name)  # noqa: E731
    # Square is 72,180 300x300. Same placement as viewdbg, confirmed on the host shot.
    fails += check(p("cutout.png"), [
        (20, 20, PAGE, "page"),
        (222, 330, YELLOW, "square center"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        bad = [l for l in text.splitlines() if "CT FAIL" in l]
        ok = len([l for l in text.splitlines() if "CT ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
        else:
            print("ok   %d logic checks" % ok)
    print("FAIL cutout (%d)" % fails if fails else "PASS cutout")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
