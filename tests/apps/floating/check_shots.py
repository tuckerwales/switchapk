#!/usr/bin/env python3
"""Checks tests/apps/floating/floating.script (1280x720 at 240 dpi).

    tools/build_apk.sh tests/apps/floating
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/floating/floating.script --screenshots build/shots \\
        build/apps/floating/floating.apk > build/shots/floating.log 2>&1
    python3 tests/apps/floating/check_shots.py build/shots build/shots/floating.log

The first shot is a floating action mode toolbar above a yellow selection.
The second shot is after Copy, which finishes the mode.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

BAR = 0xFFFFFFFF
TEXT = 0xFF212121
SELECTION = 0xFFFFF59D
PAGE = 0xFF101820
STATUS = 0xFF203040
STATUS_TEXT = 0xFFF5F5F5
CHECKS = 9


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    p = lambda name: os.path.join(d, name)  # noqa: E731
    # Toolbar is 72,216 264x72 above the selection at 72,300. Copy's glyph
    # is the dark pixel; the status word moves when the click finishes the mode.
    fails += check(p("floating_bar.png"), [
        (100, 240, BAR, "toolbar"),
        (114, 252, TEXT, "Copy glyph"),
        (100, 330, SELECTION, "selection"),
        (20, 20, PAGE, "page"),
        (27, 680, STATUS_TEXT, "status word"),
        (1200, 690, STATUS, "status bar"),
    ])
    fails += check(p("floating_done.png"), [
        (100, 240, PAGE, "toolbar gone"),
        (100, 330, SELECTION, "selection"),
        (26, 683, STATUS_TEXT, "status word copied"),
        (1200, 690, STATUS, "status bar"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        bad = [l for l in text.splitlines() if "FLOAT FAIL" in l]
        ok = len([l for l in text.splitlines() if "FLOAT ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
        else:
            print("ok   %d logic checks" % ok)
    print("FAIL floating (%d)" % fails if fails else "PASS floating")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
