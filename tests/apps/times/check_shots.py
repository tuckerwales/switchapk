#!/usr/bin/env python3
"""Checks tests/apps/times/times.script (1280x720 at 240 dpi, landscape).

    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/times/times.script --screenshots build/shots \\
        build/apps/times/times.apk > build/shots/times.log 2>&1
    python3 tests/apps/times/check_shots.py build/shots [build/shots/times.log]

A clock-mode TimePicker (landscape layout: accent header with the time and AM/PM, radial picker on
the right) starts at 9:41 AM with the selector on 9. Tapping 3 selects 3 AM and moves on to the
minutes, where 41 sits between marks (the selector shows its centre dot); tapping 15 and PM gives
15:15. The spinner TimePicker steps its minute from 59 to 00, which rolls the hour to 12 and AM to
PM. The 24-hour TimePickerDialog starts at 18:30 on the inner ring; tapping 20 there and OK reports
20:30. The keyboard button switches the clock picker to text input, where typing 8 (PM selected)
gives 20:15, and the clock button switches back with the selector on 15.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

ACCENT = 0xFF008577
DIM_ACCENT = 0xFF00352F
CIRCLE = 0xFFEEEEEE
WHITE = 0xFFFFFFFF
LINE = 0xFFA5A5A5

LOG = ["clock 3:41", "clock 3:15", "clock 15:15", "spin 12:0", "dialog 20:30", "clock 20:15"]
CHECKS = 17


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    p = lambda name: os.path.join(d, name)  # noqa: E731
    fails += check(p("times_start.png"), [
        (100, 400, ACCENT, "header background"), (335, 180, ACCENT, "selector on 9"),
        (485, 192, ACCENT, "centre dot"), (620, 192, 0xFFE3E3E3, "3 not selected"),
    ])
    fails += check(p("times_minutes.png"), [
        (352, 238, ACCENT, "minute selector at 41"), (363, 246, WHITE, "dot: 41 is between marks"),
        (620, 192, CIRCLE, "hour selector gone"), (350, 192, CIRCLE, "nothing at 9 any more"),
    ])
    fails += check(p("times_pm.png"), [(620, 192, ACCENT, "selector on 15")])
    fails += check(p("times_dialog.png"), [
        (100, 400, DIM_ACCENT, "activity dimmed"), (500, 300, ACCENT, "dialog header"),
        (780, 411, ACCENT, "selector on 18 (inner ring)"),
    ])
    fails += check(p("times_dialog_minutes.png"), [
        (765, 478, ACCENT, "minute selector at 30"), (795, 478, ACCENT, "minute selector at 30 (right)"),
    ])
    fails += check(p("times_input.png"), [(100, 200, ACCENT, "input header"), (600, 400, WHITE, "no radial picker")])
    fails += check(p("times_typed.png"), [
        (389, 145, ACCENT, "hour field focused"), (464, 145, LINE, "minute field not focused"),
    ])
    fails += check(p("times_back.png"), [
        (605, 180, ACCENT, "back on the clock, selector on 15"), (350, 192, CIRCLE, "nothing at 45"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        log = [l.split(": ", 1)[-1].strip() for l in text.splitlines() if l.startswith("I/TIME:")]
        if log != LOG:
            print("FAIL log: expected %s got %s" % (LOG, log))
            fails += 1
        bad = [l for l in text.splitlines() if "TMCHECK FAIL" in l]
        ok = len([l for l in text.splitlines() if "TMCHECK ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
    print("FAIL times (%d)" % fails if fails else "PASS times")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
