#!/usr/bin/env python3
"""Checks tests/apps/grid/grid.script (1280x720 at 240 dpi).

    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/grid/grid.script --screenshots build/shots \\
        build/apps/grid/grid.apk > build/shots/grid.log 2>&1
    python3 tests/apps/grid/check_shots.py build/shots [build/shots/grid.log]

Left: a TableLayout (column 1 stretched, column 3 collapsed, a cell spanning
two columns) and an AbsoluteLayout. Right: a GridView with auto_fit columns
of 100dp, 8dp spacing and stretched column width (AOSP determineColumns:
4 columns of 155px). A tap clicks an item, a swipe flings the grid, a tap
after the fling clicks, the D-pad moves the selection by item and row, A
clicks it, and moving down reveals the partial last row at the bottom.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

GRIDBG = 0xFF263238
EVEN = 0xFF5C6BC0
ODD = 0xFF26A69A
SELECTED = 0xFF1E847B

LOG = [
    "GRID: columns 4 width 155 spacing 12", "GRID: click 5", "GRID: selected 5", "GRID: click 29",
    "GRID: selected 29", "GRID: selected 30", "GRID: selected 34", "GRID: selected 33", "GRID: click 33",
    "GRID: selected 37", "GRID: selected 41", "GRID: selected 45", "GRID: selected 49",
]


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    fails += check(os.path.join(d, "grid_start.png"), [
        (100, 32, 0xFFB2E4FB, "stretched table column"), (560, 32, 0xFFE0E0E0, "unit column after it"),
        (100, 112, 0xFFFFE0B2, "spanning cell"), (560, 112, 0xFFFFFFFF, "spanning cell ends before unit"),
        (70, 180, 0xFFE53935, "absolute child at 20dp,10dp"), (190, 260, 0xFF43A047, "absolute child at 100dp,60dp"),
        (610, 30, GRIDBG, "grid padding"), (689, 30, EVEN, "item 0"), (856, 30, ODD, "item 1"),
        (1190, 30, ODD, "item 3 in the fourth column"), (689, 124, GRIDBG, "vertical spacing"),
    ])
    fails += check(os.path.join(d, "grid_dpad.png"), [(856, 370, SELECTED, "selector on 33"), (856, 250, ODD, "29 not selected")])
    fails += check(os.path.join(d, "grid_end.png"), [
        (856, 630, SELECTED, "selector on 49"), (1023, 650, GRIDBG, "partial last row"),
        (689, 700, EVEN, "48 on the last row"), (689, 712, GRIDBG, "last row ends at the padding"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        log = [l.split("/", 1)[-1].strip() for l in text.splitlines() if "GRID:" in l]
        if log != LOG:
            print("FAIL log: expected %s got %s" % (LOG, log))
            fails += 1
    print("FAIL grid (%d)" % fails if fails else "PASS grid")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
