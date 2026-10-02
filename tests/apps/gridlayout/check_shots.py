#!/usr/bin/env python3
"""Checks tests/apps/gridlayout/gridlayout.script (1280x720 at 240 dpi, landscape).

    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/gridlayout/gridlayout.script --screenshots build/shots \\
        build/apps/gridlayout/gridlayout.apk > build/shots/gridlayout.log 2>&1
    python3 tests/apps/gridlayout/check_shots.py build/shots [build/shots/gridlayout.log]

A calculator GridLayout (4 columns, default margins of 4dp around each 64x48dp key) with a
display spanning all columns, "+" and "=" spanning two rows, and "0" spanning two columns; the
keys without indices flow around the row spans. On the right a 360dp GridLayout splits its width
between three cells of column weight 1, and a vertical GridLayout aligns small cells right,
centred and filled against a 160dp cell, with a row-weighted yellow cell filling the column.
Typing 1 + 23 = logs 24. Tapping the display hides the middle weighted cell (GONE) so the other
two take half each; tapping again brings it back.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

GRID_BG = 0xFFEEEEEE
DISPLAY = 0xFF212121
KEY = 0xFF3F51B5
OP = 0xFFFF9800
RED = 0xFFF44336
GREEN = 0xFF4CAF50
BLUE = 0xFF2196F3
YELLOW = 0xFFFFEB3B
ALIGN_BG = 0xFFE0E0E0
WHITE = 0xFFFFFFFF

LOG = ["weights 180", "result 24", "weights 270", "weights 180"]
CHECKS = 29


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    p = lambda name: os.path.join(d, name)  # noqa: E731
    fails += check(p("gridlayout_start.png"), [
        (27, 27, GRID_BG, "grid background inside the 16dp margin"), (20, 20, WHITE, "outside the grid"),
        (100, 66, DISPLAY, "display spans the row"), (447, 66, DISPLAY, "display fills to the last column"),
        (40, 125, KEY, "key 7"), (132, 150, GRID_BG, "gap between 7 and 8"),
        (402, 260, OP, "+ spans rows 1 and 2"), (402, 276, GRID_BG, "gap below +"),
        (40, 290, KEY, "1 flowed to row 3"), (200, 430, KEY, "0 spans two columns"),
        (240, 430, GRID_BG, "gap after 0"), (402, 430, OP, "= spans rows 3 and 4"),
        (570, 60, RED, "weight cell 1"), (750, 60, GREEN, "weight cell 2"), (930, 60, BLUE, "weight cell 3"),
        (1030, 60, WHITE, "weights end at 360dp"),
        (600, 130, 0xFF616161, "wide cell"), (690, 174, RED, "right aligned"), (600, 174, ALIGN_BG, "left of it"),
        (600, 210, GREEN, "centred"), (530, 210, ALIGN_BG, "left of centre"), (500, 246, BLUE, "filled"),
        (768, 125, YELLOW, "row weight top"), (768, 260, YELLOW, "row weight bottom"),
    ])
    fails += check(p("gridlayout_gone.png"), [
        (740, 60, RED, "red takes half"), (760, 60, BLUE, "blue takes half"),
    ])
    fails += check(p("gridlayout_back.png"), [
        (650, 60, RED, "red back to a third"), (750, 60, GREEN, "green back"), (850, 60, BLUE, "blue back"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        log = [l.split(": ", 1)[-1].strip() for l in text.splitlines() if l.startswith("I/GRID:")]
        if log != LOG:
            print("FAIL log: expected %s got %s" % (LOG, log))
            fails += 1
        bad = [l for l in text.splitlines() if "GLCHECK FAIL" in l]
        ok = len([l for l in text.splitlines() if "GLCHECK ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
    print("FAIL gridlayout (%d)" % fails if fails else "PASS gridlayout")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
