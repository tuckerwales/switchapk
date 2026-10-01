#!/usr/bin/env python3
"""Checks tests/apps/toolbar/toolbar.script (1280x720 at 240 dpi).

    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/toolbar/toolbar.script --screenshots build/shots \\
        build/apps/toolbar/toolbar.apk > build/shots/toolbar.log 2>&1
    python3 tests/apps/toolbar/check_shots.py build/shots [build/shots/toolbar.log]

A standalone Toolbar with a navigation icon, title and subtitle, and a menu:
Search (ifRoom, icon) and Save (always|withText) become action buttons, the
rest go to the overflow. The navigation button, both action buttons and the
overflow button are tapped; the overflow popup overlaps its anchor at the
top right and About is picked from it.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

WHITE = 0xFFFFFFFF
BAR = 0xFF3F51B5
POPUP = 0xFFFAFAFA

LOG = ["TB: navigation", "TB: item Search", "TB: item Save", "TB: item About"]


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    fails += check(os.path.join(d, "toolbar_start.png"), [
        (4, 4, BAR, "toolbar background"), (640, 70, BAR, "toolbar 56dp tall"), (640, 74, WHITE, "content below"),
        (90, 36, BAR, "gap after 56dp nav button"),
    ])
    fails += check(os.path.join(d, "toolbar_overflow.png"), [
        (1200, 70, POPUP, "overflow popup over the toolbar"), (1200, 140, POPUP, "two rows"),
        (1200, 150, WHITE, "popup ends"), (960, 36, BAR, "popup aligned to the end"),
    ])
    fails += check(os.path.join(d, "toolbar_end.png"), [(1200, 100, WHITE, "popup dismissed")])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        log = [l.split("/", 1)[-1].strip() for l in text.splitlines() if "TB:" in l]
        if log != LOG:
            print("FAIL log: expected %s got %s" % (LOG, log))
            fails += 1
        if "leaked" in text:
            print("FAIL a window leaked")
            fails += 1
    print("FAIL toolbar (%d)" % fails if fails else "PASS toolbar")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
