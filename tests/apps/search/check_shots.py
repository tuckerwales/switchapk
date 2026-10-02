#!/usr/bin/env python3
"""Checks tests/apps/search/search.script (1280x720 at 240 dpi, landscape).

    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/search/search.script --screenshots build/shots \\
        build/apps/search/search.apk > build/shots/search.log 2>&1
    python3 tests/apps/search/check_shots.py build/shots [build/shots/search.log]

The main activity has a collapsible SearchView action item. Its SearchableInfo comes from the
"android.app.default_searchable" meta-data naming ResultsActivity, whose "android.app.searchable"
XML names a suggestions provider. Tapping the item expands the SearchView into the action bar
(up arrow, query, close button); typing "ba" queries the provider and the drop-down shows
Banana with its second line and a refine arrow. Tapping it starts ResultsActivity with
ACTION_SEARCH and the suggestion's query. The "Search dialog" button calls onSearchRequested,
which opens the search bar dialog at the top (default app icon, focused query field); "ch"
shows Cherry and enter starts the search. The Filter activity has a SearchView filtering a
list as the query changes (enter goes to the listener) and an iconified SearchView that
expands on tap and collapses again on close.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

BAR = 0xFFF5F5F5
BG = 0xFFFAFAFA
TEXT = 0xFF202020
ICON = 0xFF646464
GREEN = 0xFF4CAF50
TEAL = 0xFF008577

LOG = [
    "action view expanded", "suggest search_suggest_query [ba] limit 50",
    "results android.intent.action.SEARCH banana user=ba", "action view collapsed",
    "suggest search_suggest_query [ch] limit 50", "dialog dismissed",
    "results android.intent.action.SEARCH ch user=ch", "change ap", "submit ap", "expanded", "closed",
]
CHECKS = 41


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    p = lambda name: os.path.join(d, name)  # noqa: E731
    fails += check(p("search_start.png"), [
        (600, 36, BAR, "action bar"), (1231, 22, 0xFF787878, "search action item"),
        (60, 115, 0xFFD6D7D7, "search dialog button"), (300, 400, BG, "window"),
    ])
    fails += check(p("search_suggest.png"), [
        (141, 20, 0xFF1F1F1F, "query text in the action bar"), (42, 36, 0xFF1F1F1F, "up arrow"),
        (516, 36, 0xFF1F1F1F, "close button"), (1231, 22, BAR, "action item replaced by the view"),
        (111, 86, TEXT, "Banana suggestion"), (134, 122, TEXT, "second line"),
        (511, 115, TEXT, "refine arrow"), (33, 130, 0xFFD6D7D7, "drop-down shadow"),
        (300, 200, BG, "one suggestion only"),
    ])
    fails += check(p("search_results.png"), [(640, 600, GREEN, "results activity"), (600, 36, BAR, "its action bar")])
    fails += check(p("search_dialog.png"), [
        (60, 36, 0xFF808080, "default app icon"), (18, 36, ICON, "close arrow"), (500, 70, TEAL, "focused query"),
        (935, 36, ICON, "clear button"), (127, 85, TEXT, "Cherry suggestion"), (300, 300, BG, "activity below"),
    ])
    fails += check(p("search_results2.png"), [(640, 600, GREEN, "results activity again")])
    fails += check(p("search_main.png"), [(1231, 22, 0xFF787878, "action item back"), (600, 36, BAR, "title bar")])
    fails += check(p("search_filter.png"), [
        (65, 119, ICON, "search icon"), (508, 131, ICON, "clear button"), (582, 124, ICON, "submit button"),
        (92, 269, TEXT, "Apricot kept"), (80, 352, BG, "Avocado filtered out"),
    ])
    fails += check(p("search_expanded.png"), [
        (706, 123, 0xFFB9B9B9, "hint icon"), (1096, 131, 0xFFB9B9B9, "disabled close button"),
        (900, 167, 0xFFDADADA, "query underline"),
    ])
    fails += check(p("search_collapsed.png"), [
        (693, 119, ICON, "search button"), (900, 167, BG, "query field gone"), (1096, 131, BG, "close gone"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        log = [l.split(": ", 1)[-1].strip() for l in text.splitlines() if l.startswith("I/SEARCH:")]
        log = [l for l in log if not l.startswith("SVCHECK") and "[]" not in l]
        if log != LOG:
            print("FAIL log: expected %s got %s" % (LOG, log))
            fails += 1
        bad = [l for l in text.splitlines() if "SVCHECK FAIL" in l]
        ok = len([l for l in text.splitlines() if "SVCHECK ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
    print("FAIL search (%d)" % fails if fails else "PASS search")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
