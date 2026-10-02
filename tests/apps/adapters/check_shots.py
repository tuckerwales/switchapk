#!/usr/bin/env python3
"""Checks tests/apps/adapters/adapters.script (1280x720 at 240 dpi).

    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/adapters/adapters.script --screenshots build/shots \\
        build/apps/adapters/adapters.apk > build/shots/adapters.log 2>&1
    python3 tests/apps/adapters/check_shots.py build/shots [build/shots/adapters.log]

Left: an AutoCompleteTextView over an ArrayAdapter and a ListView over a SimpleCursorAdapter
(MatrixCursor, a ViewBinder paints the swatches). Right: an ExpandableListView over a
SimpleExpandableListAdapter with the framework's expandable and simple list item layouts and the
Material group indicator (a chevron that points down when collapsed, up when expanded). A tap
expands the second group, a tap on its second child and on a cursor row report them, typing "ca"
filters the suggestions on the Filter thread and shows the drop-down with the completion hint, the
first D-pad press highlights the first suggestion, the second moves to the next and A completes it.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

BG = 0xFFECEFF1
INDICATOR = 0xFFAEB1B2
DIVIDER = 0xFFD1D3D5
POPUP = 0xFFFAFAFA
HIGHLIGHT = 0xFFC8C8C8

LOG = ["expand 1", "child Leek", "row 2 id 102", "picked Cameroon"]
CHECKS = 40


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    fails += check(os.path.join(d, "adapters_start.png"), [
        (628, 51, INDICATOR, "Fruit collapsed: chevron tip at the bottom"), (628, 41, BG, "above the tip"),
        (48, 175, 0xFFE53935, "cursor row 0 swatch"), (48, 249, 0xFF43A047, "cursor row 1 swatch"),
        (48, 471, 0xFF8E24AA, "cursor row 4 swatch"), (300, 212, 0xFFE0E0E0, "list divider"),
        (900, 120, BG, "group row"),
    ])
    fails += check(os.path.join(d, "adapters_expanded.png"), [
        (628, 141, INDICATOR, "Vegetables expanded: chevron tip at the top"), (628, 151, BG, "below the tip"),
        (628, 395, INDICATOR, "Grains moved below the two children"), (900, 232, BG, "child row"),
        (900, 269, DIVIDER, "child divider"),
    ])
    fails += check(os.path.join(d, "adapters_popup.png"), [
        (300, 118, HIGHLIGHT, "first suggestion highlighted"), (300, 214, POPUP, "second suggestion"),
        (300, 310, POPUP, "third suggestion"), (300, 395, 0xFFFFFFFF, "drop-down ends after the hint"),
    ])
    fails += check(os.path.join(d, "adapters_picked.png"), [
        (300, 214, 0xFFFFFFFF, "drop-down dismissed"), (300, 67, 0xFF008577, "field focused"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        log = [l.split(": ", 1)[-1].strip() for l in text.splitlines() if l.startswith("I/ADAPT:")]
        if log != LOG:
            print("FAIL log: expected %s got %s" % (LOG, log))
            fails += 1
        bad = [l for l in text.splitlines() if "ADCHECK FAIL" in l]
        ok = len([l for l in text.splitlines() if "ADCHECK ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
    print("FAIL adapters (%d)" % fails if fails else "PASS adapters")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
