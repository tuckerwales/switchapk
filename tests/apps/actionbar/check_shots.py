#!/usr/bin/env python3
"""Checks tests/apps/actionbar/actionbar.script (1280x720 at 240 dpi).

    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/actionbar/actionbar.script --screenshots build/shots \\
        build/apps/actionbar/actionbar.apk > build/shots/actionbar.log 2>&1
    python3 tests/apps/actionbar/check_shots.py build/shots [build/shots/actionbar.log]

A Theme.Material.Light.DarkActionBar activity gets the window decor action
bar (screen_toolbar): the activity label as title, a subtitle, the up
button, two action items and the overflow. Up, both action items, the
overflow button and the MENU key open or run the options menu. A primary
action mode replaces the bar with the context bar and ends from one of its
items and from B (back). hide() collapses the bar so the content moves up.
A second activity uses setActionBar(Toolbar); with no decor action bar its
action mode is standalone (screen_simple's context bar stub, ended by B), and
its up button finishes it.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

BAR = 0xFF212121
BG = 0xFFFAFAFA
POPUP = 0xFFFAFAFA
MODE = 0xFF303030
ACCENT = 0xFF008577
BUTTON = 0xFFD6D7D7
TOOLBAR = 0xFF00796B

LOG = [
    "AB: bar true", "AB: home", "AB: item Add", "AB: item Share", "AB: menu visible true", "AB: item Refresh",
    "AB: menu visible false", "AB: menu visible true", "AB: menu visible false", "AB: mode started true",
    "AB: mode Delete", "AB: mode destroyed", "AB: mode started true", "AB: mode destroyed", "AB: showing false",
    "AB: showing true", "AB: toolbar title Toolbar bar", "AB: toolbar Edit", "AB: standalone started true",
    "AB: standalone destroyed", "AB: toolbar home",
]


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    fails += check(os.path.join(d, "actionbar_start.png"), [
        (500, 10, BAR, "action bar"), (500, 71, BAR, "action bar 56dp tall"), (500, 74, BG, "content below the bar"),
    ])
    fails += check(os.path.join(d, "actionbar_overflow.png"), [
        (1200, 36, POPUP, "overflow popup over the bar"), (960, 36, BAR, "popup aligned to the end"),
    ])
    fails += check(os.path.join(d, "actionbar_menukey.png"), [(1200, 36, POPUP, "MENU opens the overflow")])
    fails += check(os.path.join(d, "actionbar_mode.png"), [
        (500, 10, MODE, "action mode bar"), (500, 70, ACCENT, "mode bar accent line"), (500, 74, BG, "content"),
    ])
    fails += check(os.path.join(d, "actionbar_modeend.png"), [(500, 10, BAR, "bar back after B"), (1200, 36, BAR, "no popup")])
    fails += check(os.path.join(d, "actionbar_hidden.png"), [(500, 10, BG, "bar hidden"), (90, 60, BUTTON, "content moved up")])
    fails += check(os.path.join(d, "actionbar_toolbar.png"), [(500, 10, TOOLBAR, "toolbar action bar"), (500, 74, BG, "content")])
    fails += check(os.path.join(d, "actionbar_standalone.png"), [
        (500, 10, BG, "standalone context bar"), (500, 108, TOOLBAR, "content pushed below it"),
    ])
    fails += check(os.path.join(d, "actionbar_end.png"), [(500, 10, BAR, "back in the first activity"), (1200, 36, BAR, "bar items")])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        log = [l.split("/", 1)[-1].strip() for l in text.splitlines() if "AB:" in l]
        if log != LOG:
            print("FAIL log: expected %s got %s" % (LOG, log))
            fails += 1
        if "leaked" in text:
            print("FAIL a window leaked")
            fails += 1
    print("FAIL actionbar (%d)" % fails if fails else "PASS actionbar")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
