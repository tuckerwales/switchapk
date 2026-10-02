#!/usr/bin/env python3
"""Checks tests/apps/tabs/tabs.script (1280x720 at 240 dpi, landscape).

    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/tabs/tabs.script --screenshots build/shots \\
        build/apps/tabs/tabs.apk > build/shots/tabs.log 2>&1
    python3 tests/apps/tabs/check_shots.py build/shots [build/shots/tabs.log]

MainActivity is a TabActivity whose layout holds the TabHost. Four material tab indicators
share the row under the action bar and the current one gets the accent underline. The first
tab shows a view from the layout (yellow) and the second a view made by a TabContentFactory
(blue). The third and fourth run ChildActivity embedded through the LocalActivityManager
(green and purple); each logs its lifecycle, and only the current one is resumed. The child
starts PickActivity for a result, which comes back to it through the group. A configuration
change recreates the group on the same tab with the children's retained instances; as on
Android, an embedded activity's saved state is lost because the group saves after stopping
them, so "Picked 7" does not come back. BACK in the embedded activity finishes the group.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

BAR = 0xFFF5F5F5
BG = 0xFFFAFAFA
ACCENT = 0xFF008577
YELLOW = 0xFFFFEB3B
BLUE = 0xFF2196F3
GREEN = 0xFF4CAF50
PURPLE = 0xFF9C27B0
RED = 0xFFF44336
WHITE = 0xFFFFFFFF
BUTTON = 0xFFD6D7D7
TABS = [160, 480, 800, 1120]

LOG = [
    "tab factory",
    "child create new null", "child start", "child resume", "tab child",
    "child pause", "child stop", "child result 7 true 7", "child start", "child resume",
    "child pause", "other create new null", "other start", "other resume", "tab other",
    "other pause", "child stop", "other stop", "child destroy", "other destroy",
    "recreated on tab text", "other create new kept other", "other start", "tab other", "other resume",
    "other pause", "other stop", "other destroy",
    "recreated on tab text", "other create new kept other", "other start", "tab other", "other resume",
    "other pause", "child create new null", "child start", "child resume", "tab child",
    "tab text", "tab child",
    "child pause", "other stop", "child stop", "other destroy", "child destroy",
]
CHECKS = 56


def tabs(current):
    """The accent underline is under the current tab only."""
    return [(x, 142, ACCENT if i == current else BG, "underline %d" % i) for i, x in enumerate(TABS)]


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    p = lambda name: os.path.join(d, name)  # noqa: E731
    fails += check(p("tabs_start.png"), tabs(0) + [
        (600, 36, BAR, "action bar"), (141, 108, 0xFF202020, "selected label"),
        (437, 108, 0xFF646464, "unselected label"), (640, 300, YELLOW, "view tab"),
        (572, 391, 0xFF756B1B, "view tab text"), (14, 690, 0xFF000000, "status line"),
    ])
    fails += check(p("tabs_factory.png"), tabs(1) + [
        (640, 300, BLUE, "factory view"), (400, 397, 0xFF0F446F, "factory text"),
    ])
    fails += check(p("tabs_child.png"), tabs(2) + [
        (640, 400, GREEN, "embedded activity"), (27, 190, WHITE, "its label"), (30, 243, BUTTON, "its button"),
        (27, 300, GREEN, "no result yet"), (640, 690, BG, "status below the tab content"),
    ])
    fails += check(p("tabs_pick.png"), [(600, 36, BAR, "pick action bar"), (640, 200, RED, "pick activity")])
    fails += check(p("tabs_result.png"), tabs(2) + [(27, 300, WHITE, "result in the embedded activity")])
    fails += check(p("tabs_other.png"), tabs(3) + [(640, 400, PURPLE, "second embedded activity")])
    fails += check(p("tabs_recreated.png"), tabs(3) + [
        (640, 400, PURPLE, "same tab after recreation"), (600, 36, BAR, "action bar"),
    ])
    fails += check(p("tabs_restored.png"), tabs(2) + [
        (640, 400, GREEN, "first embedded activity again"), (27, 300, GREEN, "its saved state is gone"),
    ])
    fails += check(p("tabs_text.png"), tabs(0) + [(640, 300, YELLOW, "view tab again")])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        log = [l.split(": ", 1)[-1].strip() for l in text.splitlines() if l.startswith("I/TABS:")]
        log = [l for l in log if not l.startswith("TABCHECK")]
        if log != LOG:
            print("FAIL log: expected %s got %s" % (LOG, log))
            fails += 1
        bad = [l for l in text.splitlines() if "TABCHECK FAIL" in l]
        ok = len([l for l in text.splitlines() if "TABCHECK ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
    print("FAIL tabs (%d)" % fails if fails else "PASS tabs")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
