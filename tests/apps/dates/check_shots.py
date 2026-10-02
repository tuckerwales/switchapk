#!/usr/bin/env python3
"""Checks tests/apps/dates/dates.script (1280x720 at 240 dpi, landscape).

    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/dates/dates.script --screenshots build/shots \\
        build/apps/dates/dates.apk > build/shots/dates.log 2>&1
    python3 tests/apps/dates/check_shots.py build/shots [build/shots/dates.log]

A calendar-mode DatePicker (landscape layout: accent header on the left, month on the right, using
the land day sizes) starts on 14 February 2024. A tap selects the 21st, the next arrow and a swipe page
to April, the header year opens the year list (2024 activated) and picking 2026 goes back to February
2026 with the 21st (a Saturday) selected. The spinner DatePicker steps Jan 31 to Feb 29 (2024 is a leap
year) and the day back to the 28th. The DatePickerDialog is sized like Android's (pref dialog width
trial, not full screen), with its button bar inside the right column; picking the 20th and OK reports
June 20. Then the page is swapped for a CalendarView (August 2024): a tap selects the 30th, the D-pad
focuses the month and moves a highlight (14th, then 6th) and centre selects it.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check, close, pixel, read_png  # noqa: E402

BG = 0xFFFFFFFF
ACCENT = 0xFF008577
DIM_ACCENT = 0xFF00352F
FOCUS_BG = 0xFFCCCCCC
HIGHLIGHT = 0xFFA3A3A3
SELECTED_FOCUSED = 0xFF3F8880

LOG = ["cal 2024-1-21", "cal 2026-1-21", "spin 2024-1-29", "spin 2024-1-28", "dialog 2024-5-20",
       "calendar shown", "calview 2024-7-30", "calview 2024-7-6"]
CHECKS = 28


def has_colour(path, box, colour, tol=24):
    """Whether any pixel in box (x0, y0, x1, y1) is close to colour (text strokes are anti-aliased)."""
    w, h, px = read_png(path)
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            if close(pixel(px, w, x, y), colour, tol):
                return True
    return False


def boxes(path, expectations):
    fails = 0
    for box, colour, want, label in expectations:
        got = has_colour(path, box, colour)
        ok = got == want
        fails += 0 if ok else 1
        print("%s %s %s" % ("ok  " if ok else "FAIL", os.path.basename(path), label))
    return fails


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    p = lambda name: os.path.join(d, name)  # noqa: E731
    fails += check(p("dates_start.png"), [
        (100, 500, ACCENT, "header background"), (513, 201, ACCENT, "14th selected"),
        (513, 249, BG, "21st not selected"), (780, 600, BG, "right of the calendar"),
    ])
    fails += check(p("dates_april.png"), [
        (513, 249, BG, "no selection in April"), (513, 201, BG, "no selection in April (row 3)"),
    ])
    fails += boxes(p("dates_years.png"), [
        ((440, 140, 600, 190), ACCENT, True, "2024 activated in the year list"),
        ((440, 300, 600, 340), ACCENT, False, "2026 not activated"),
    ])
    fails += check(p("dates_2026.png"), [
        (713, 201, ACCENT, "Saturday the 21st selected in February 2026"), (513, 201, BG, "Wednesday column empty"),
    ])
    fails += check(p("dates_dialog.png"), [
        (100, 600, DIM_ACCENT, "activity dimmed behind the dialog"), (372, 500, ACCENT, "dialog header"),
        (972, 340, ACCENT, "June 15 selected"), (1100, 340, 0xFF666666, "dim right of the dialog"),
        (200, 100, DIM_ACCENT, "dim above the dialog"),
    ])
    fails += check(p("dates_calview.png"), [
        (708, 162, ACCENT, "August 8 selected"), (300, 200, BG, "page swapped away"),
    ])
    fails += check(p("dates_dpad1.png"), [
        (400, 350, FOCUS_BG, "month focused"), (639, 190, HIGHLIGHT, "14th highlighted"),
        (777, 290, 0xFF006A5F, "30th selected over the focus highlight"),
    ])
    fails += check(p("dates_dpad3.png"), [
        (570, 143, SELECTED_FOCUSED, "6th selected and highlighted"), (777, 290, FOCUS_BG, "30th no longer selected"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        log = [l.split(": ", 1)[-1].strip() for l in text.splitlines() if l.startswith("I/DATE:")]
        if log != LOG:
            print("FAIL log: expected %s got %s" % (LOG, log))
            fails += 1
        bad = [l for l in text.splitlines() if "DTCHECK FAIL" in l]
        ok = len([l for l in text.splitlines() if "DTCHECK ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
    print("FAIL dates (%d)" % fails if fails else "PASS dates")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
