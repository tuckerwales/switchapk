#!/usr/bin/env python3
"""Checks the screenshots of tests/apps/views/views.script (1280x720 at 240 dpi, density 1.5)."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

WINDOW_BG = 0xFFFAFAFA
HEADER = 0xFF3F51B5
NORMAL = 0xFF2196F3
FOCUSED = 0xFF4CAF50
FRAME = 0xFFDDDDDD
STATUS_GREY = 0xFF9E9E9E

# view centres in screen pixels
TILE1 = (200, 200)
TILE2 = (600, 200)
TILE3 = (1000, 200)
TILE4 = (200, 660)
STATUS = (640, 474)


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    p = lambda name: os.path.join(d, name)
    fails = 0
    fails += check(p("views_initial.png"), [
        (5, 5, WINDOW_BG, "window background from the theme"),
        (640, 54, HEADER, "header"),
        (TILE1[0], TILE1[1], NORMAL, "tile1"),
        (338, 168, WINDOW_BG, "16dp margin between tile1 and tile2"),
        (TILE3[0], TILE3[1], NORMAL, "tile3 (weight 2)"),
        (300, 400, FRAME, "frame background"),
        (STATUS[0], STATUS[1], STATUS_GREY, "status centred"),
        (60, 288, 0xFF795548, "ViewStub content top|start"),
        (1220, 660, 0xFF009688, "<include> bottom|end"),
        (TILE4[0], TILE4[1], NORMAL, "tile4 bottom|start"),
    ])
    fails += check(p("views_tap.png"), [
        (STATUS[0], STATUS[1], 0xFFFFEB3B, "tap on tile2 clicked it"),
        (374, 132, 0xFFFFFFFF, "tile2 click dot"),
        (TILE2[0], TILE2[1], NORMAL, "no focus highlight in touch mode"),
    ])
    fails += check(p("views_focus1.png"), [
        (TILE1[0], TILE1[1], FOCUSED, "DPAD_DOWN left touch mode and focused tile1"),
        (TILE2[0], TILE2[1], NORMAL, "tile2 not focused"),
    ])
    fails += check(p("views_focus2.png"), [
        (TILE1[0], TILE1[1], NORMAL, "tile1 lost focus"),
        (TILE2[0], TILE2[1], FOCUSED, "DPAD_RIGHT moved focus to tile2"),
    ])
    fails += check(p("views_click3.png"), [
        (TILE3[0], TILE3[1], FOCUSED, "tile3 focused"),
        (STATUS[0], STATUS[1], 0xFF9C27B0, "BUTTON_A clicked the focused tile3"),
    ])
    fails += check(p("views_focus4.png"), [
        (TILE4[0], TILE4[1], FOCUSED, "DPAD_DOWN from tile3 reached tile4"),
    ])
    fails += check(p("views_dialog.png"), [
        (5, 5, 0xFF7D7D7D, "dim behind the dialog"),
        (520, 300, 0xFFFFFFFF, "dialog window centred, min 200x120dp"),
        (640, 360, FOCUSED, "dialog button took focus"),
    ])
    fails += check(p("views_after.png"), [
        (5, 5, WINDOW_BG, "BUTTON_B (BACK) dismissed the dialog"),
        (TILE4[0], TILE4[1], FOCUSED, "focus back on tile4"),
    ])
    fails += check(p("views_pressed.png"), [
        (TILE1[0], TILE1[1], 0xFFFF5722, "tile1 shows the pressed state while held"),
    ])
    fails += check(p("views_long.png"), [
        (STATUS[0], STATUS[1], 0xFF000000, "long press on tile1"),
        (TILE4[0], TILE4[1], NORMAL, "touch re-entered touch mode and cleared focus"),
        (TILE1[0], TILE1[1], NORMAL, "tile1 not left pressed"),
    ])
    print("FAIL: %d" % fails if fails else "PASS views")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
