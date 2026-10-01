#!/usr/bin/env python3
"""Checks tests/apps/popups/popups.script (1280x720 at 240 dpi).

    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/popups/popups.script --screenshots build/shots \\
        build/apps/popups/popups.apk > build/shots/popups.log 2>&1
    python3 tests/apps/popups/check_shots.py build/shots [build/shots/popups.log]

A drop-down Spinner opens its list over itself and picks Earth, a dialog-mode
Spinner picks Mars, a PopupMenu opens below its button and its sub menu
replaces it on the same anchor (Delete), a Toast shows at the bottom, and a
PopupWindow anchored near the bottom flips above its anchor and is dismissed
by a touch outside. Then the D-pad focuses the first Spinner, A opens it,
two downs move the selector and A picks Jupiter. With the log, the listener calls are checked in order.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

WHITE = 0xFFFFFFFF
POPUP = 0xFFFAFAFA
DIM = 0xFF666666
ARROW = 0xFF666666
TOAST = 0xFFF2F2F2
BLUE = 0xFF3949AB

LOG = [
    "POP: dropdown 0 Mercury", "POP: dialog 0 Mercury", "POP: dropdown 2 Earth", "POP: dialog 3 Mars",
    "POP: menu More", "POP: menu Delete", "POP: menu dismissed", "POP: popup above true", "POP: popup dismissed",
    "POP: dropdown 4 Jupiter",
]


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    fails += check(os.path.join(d, "popups_start.png"), [(348, 42, ARROW, "spinner arrow"), (600, 300, WHITE, "no popup")])
    fails += check(os.path.join(d, "popups_dropdown.png"), [
        (200, 240, POPUP, "drop-down list"), (200, 400, WHITE, "below the five rows"),
    ])
    fails += check(os.path.join(d, "popups_dialog.png"), [(100, 600, DIM, "dim"), (640, 300, WHITE, "dialog list")])
    fails += check(os.path.join(d, "popups_menu.png"), [(200, 300, POPUP, "popup menu"), (200, 400, WHITE, "three rows")])
    fails += check(os.path.join(d, "popups_submenu.png"), [(200, 240, POPUP, "sub menu"), (200, 340, WHITE, "two rows")])
    fails += check(os.path.join(d, "popups_toast.png"), [(600, 650, TOAST, "toast"), (640, 600, WHITE, "above toast")])
    fails += check(os.path.join(d, "popups_popup.png"), [
        (174, 524, BLUE, "popup above anchor"), (174, 620, BLUE, "popup ends above anchor"),
        (174, 640, WHITE, "anchor row not covered"),
    ])
    fails += check(os.path.join(d, "popups_end.png"), [(174, 524, WHITE, "dismissed by outside touch")])
    fails += check(os.path.join(d, "popups_dpad.png"), [
        (200, 348, 0xFFC8C8C8, "D-pad selector on Jupiter"), (200, 204, POPUP, "Earth no longer selected"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        log = [l.split("/", 1)[-1].strip() for l in text.splitlines() if "POP:" in l]
        if log != LOG:
            print("FAIL log: expected %s got %s" % (LOG, log))
            fails += 1
        if "Toast: show: Hello toast" not in text:
            print("FAIL log lacks the toast")
            fails += 1
        if "leaked" in text:
            print("FAIL a window leaked")
            fails += 1
    print("FAIL popups (%d)" % fails if fails else "PASS popups")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
