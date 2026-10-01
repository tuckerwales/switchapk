#!/usr/bin/env python3
"""Checks tests/apps/pickers/pickers.script (1280x720 at 240 dpi).

    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/pickers/pickers.script --screenshots build/shots \\
        build/apps/pickers/pickers.apk > build/shots/pickers.log 2>&1
    python3 tests/apps/pickers/check_shots.py build/shots [build/shots/pickers.log]

Three Material selector-wheel NumberPickers (0..20 wrapping, months as displayed values, 0..2 which
cannot wrap and has a "#n" formatter), a stopped Chronometer and a TextClock. Each wheel draws the
values above and below the middle through the vertical fading edges, so they come out grey, between
two colorControlNormal dividers. A tap below the middle of the first picker steps it, a drag on the
month wheel scrolls one row and the adjust scroller snaps to the next, a tap above the third picker at
its minimum does nothing. The first D-pad press only leaves touch mode and focuses the first picker
(default focus highlight), the next one steps it; then focus moves right twice and two presses step the
third picker to its maximum and a third press is ignored.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check, pixel, read_png  # noqa: E402

BG = 0xFFFFFFFF
DIVIDER = 0xFF797979
FOCUS_BG = 0xFFCCCCCC
FOCUS_DIVIDER = 0xFF606060

LOG = ["num 5->6", "month Jan->Feb", "month idle", "month Feb->Mar", "num 6->7", "small 0->1", "small 1->2"]
CHECKS = 35
COLUMNS = {"num": 120, "month": 330, "small": 540}
ROWS = {"top": 45, "middle": 135, "bottom": 225}


def darkest(path, column, row):
    """Darkest grey level of the text in one wheel slot (255 when the slot is empty)."""
    w, h, px = read_png(path)
    cx, cy = COLUMNS[column], ROWS[row]
    best = 255
    for y in range(cy - 20, cy + 20):
        for x in range(cx - 30, cx + 30):
            best = min(best, (pixel(px, w, x, y) >> 8) & 0xFF)
    return best


def slots(path, expectations):
    fails = 0
    for column, row, lo, hi, label in expectations:
        got = darkest(path, column, row)
        ok = lo <= got <= hi
        fails += 0 if ok else 1
        print("%s %s %s %s %s: darkest %d want %d..%d" % ("ok  " if ok else "FAIL", os.path.basename(path),
                                                         column, row, label, got, lo, hi))
    return fails


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    start = os.path.join(d, "pickers_start.png")
    fails += check(start, [
        (120, 97, DIVIDER, "top divider"), (120, 172, DIVIDER, "bottom divider"),
        (120, 99, BG, "below the top divider"), (120, 170, BG, "above the bottom divider"),
        (330, 97, DIVIDER, "month top divider"), (540, 172, DIVIDER, "small bottom divider"),
        (20, 135, BG, "margin left of the first picker"),
    ])
    fails += slots(start, [
        ("num", "middle", 0, 0x30, "current value at full colour"),
        ("num", "top", 0x88, 0xA8, "previous value faded"), ("num", "bottom", 0x88, 0xA8, "next value faded"),
        ("month", "top", 0x88, 0xA8, "Dec wraps above Jan"),
        ("small", "top", 255, 255, "nothing above the minimum without wrapping"),
        ("small", "bottom", 0x88, 0xA8, "formatted next value"),
    ])
    fails += check(os.path.join(d, "pickers_focus.png"), [
        (120, 10, FOCUS_BG, "first picker focused"), (120, 97, FOCUS_DIVIDER, "divider over the highlight"),
        (330, 10, BG, "month not focused"),
    ])
    dpad = os.path.join(d, "pickers_dpad.png")
    fails += check(dpad, [
        (120, 10, BG, "first picker no longer focused"), (540, 10, FOCUS_BG, "third picker focused"),
        (540, 260, FOCUS_BG, "highlight covers the whole picker"),
    ])
    fails += slots(dpad, [
        ("small", "bottom", 0xCC, 0xCC, "nothing below the maximum"),
        ("small", "top", 0x70, 0x90, "previous value faded over the highlight"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        log = [l.split(": ", 1)[-1].strip() for l in text.splitlines() if l.startswith("I/PICK:")]
        if log != LOG:
            print("FAIL log: expected %s got %s" % (LOG, log))
            fails += 1
        bad = [l for l in text.splitlines() if "PKCHECK FAIL" in l]
        ok = len([l for l in text.splitlines() if "PKCHECK ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
    print("FAIL pickers (%d)" % fails if fails else "PASS pickers")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
