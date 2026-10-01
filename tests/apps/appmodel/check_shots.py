#!/usr/bin/env python3
"""Checks tests/apps/appmodel/appmodel.script (1280x720 at 240 dpi).

The activity shows an AlertDialog from the framework's material layouts.
The script moves focus to the positive button with the D-pad and presses
A, then picks the third row of a single-choice list dialog and presses OK,
which turns the band blue. Pixels sample the dialog card, the dim behind
it, the focused button and the D-pad selector on the list row.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import close, pixel, read_png  # noqa: E402

WHITE = 0xFFFFFFFF
DIMMED_BG = 0xFF646464
DIMMED_BAND = 0xFF3F3F3F
FOCUS = 0xFFCCCCCC
ACCENT = 0xFF008577
BLUE = 0xFF2196F3
BG = 0xFFFAFAFA


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    fails += shot(os.path.join(d, "appmodel_alert.png"), [
        (640, 300, WHITE, "dialog card"),
        (100, 600, DIMMED_BG, "dim behind the dialog"),
        (640, 90, DIMMED_BAND, "grey band under the dim"),
        (985, 415, WHITE, "positive button not focused yet"),
    ])
    fails += shot(os.path.join(d, "appmodel_alert_focus.png"), [
        (985, 415, FOCUS, "D-pad focus on the positive button"),
        (842, 420, WHITE, "negative button not focused"),
    ])
    fails += shot(os.path.join(d, "appmodel_list_sel.png"), [
        (900, 440, FOCUS, "D-pad selector on the third row"),
        (900, 300, WHITE, "first row not selected"),
        (302, 283, ACCENT, "first row radio checked"),
    ])
    fails += shot(os.path.join(d, "appmodel_done.png"), [
        (640, 90, BLUE, "OK with the third row turned the band blue"),
        (640, 400, BG, "dialogs dismissed, no dim"),
    ])
    print("FAIL: %d" % fails if fails else "PASS appmodel")
    return 1 if fails else 0


def shot(path, expectations):
    w, h, px = read_png(path)
    fails = 0
    for x, y, color, label in expectations:
        got = pixel(px, w, x, y)
        ok = close(got, color, 6)
        print("%s %s (%d,%d) %s: got #%08X want #%08X" % (
            "ok  " if ok else "FAIL", os.path.basename(path), x, y, label, got, color))
        if not ok:
            fails += 1
    return fails


if __name__ == "__main__":
    sys.exit(main())
