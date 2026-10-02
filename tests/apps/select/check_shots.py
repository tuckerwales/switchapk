#!/usr/bin/env python3
"""Checks tests/apps/select/select.script (1280x720 at 240 dpi).

    tools/build_apk.sh tests/apps/select
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/select/select.script --screenshots build/shots \\
        build/apps/select/select.apk > build/shots/select.log 2>&1
    python3 tests/apps/select/check_shots.py build/shots build/shots/select.log

The first shot is a long press on "beta": the word is highlighted and the
floating toolbar sits on that selection. The second shot is after Copy.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

BAR = 0xFFFFFFFF
TEXT = 0xFF212121
PAGE = 0xFF101820
STATUS = 0xFF203040
STATUS_TEXT = 0xFFF5F5F5
# Highlight of 0x6633B5E5 over the white text background, locked from select_bar.png.
HIGHLIGHT = 0xFF99CEC8
CHECKS = 7


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    p = lambda name: os.path.join(d, name)  # noqa: E731
    # Toolbar is 247,120 290x72. Copy is the first item; its glyph is the dark pixel.
    # The highlight sits on "beta" (about 248..355, 204..266).
    fails += check(p("select_bar.png"), [
        (300, 156, BAR, "toolbar"),
        (336, 156, TEXT, "Copy glyph"),
        (300, 220, HIGHLIGHT, "selection"),
        (20, 20, PAGE, "page"),
        (26, 683, STATUS_TEXT, "status word"),
        (1200, 690, STATUS, "status bar"),
    ])
    fails += check(p("select_done.png"), [
        (300, 156, PAGE, "toolbar gone"),
        (300, 220, BAR, "selection cleared"),
        (302, 236, TEXT, "beta glyph"),
        (26, 683, STATUS_TEXT, "status word copied"),
        (1200, 690, STATUS, "status bar"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        bad = [l for l in text.splitlines() if "SEL FAIL" in l]
        ok = len([l for l in text.splitlines() if "SEL ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
        else:
            print("ok   %d logic checks" % ok)
    print("FAIL select (%d)" % fails if fails else "PASS select")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
