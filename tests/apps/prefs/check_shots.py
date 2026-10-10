#!/usr/bin/env python3
"""Checks tests/apps/prefs across two host runs that share --data.

    rm -rf build/data/apps/com.example.prefs
    for r in 1 2; do mkdir -p build/shots/prefs$r
        build/host/switchapk-host --data build/data --screen 1280x720@240 \\
            --script tests/apps/prefs/prefs.script --screenshots build/shots/prefs$r \\
            build/apps/prefs/prefs.apk > build/shots/prefs$r/prefs.log 2>&1; done
    python3 tests/apps/prefs/check_shots.py build/shots/prefs1 build/shots/prefs2

Run 1 starts from the XML defaults, unchecks Sound effects (which disables the dependent Music
switch, so the tap on Music does nothing), clicks About, picks Hard in the Difficulty list dialog
and types a player name in the EditText dialog. Run 2 reads those values back: setDefaultValues
does not overwrite them, and Music is enabled again once Sound effects is rechecked.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

TEAL = 0xFF008577
GREY_BOX = 0xFF646464
DIM = 0xFF646464

BOTH = [
    "I/PREFS: default name com.example.prefs_preferences",
    "I/PREFS: screen count=2 sound in cat_sound",
    "I/PREFS: about intent android.intent.action.VIEW https://example.com/about from=prefs",
    "I/PREFS: adapter before bind true",
    "I/PREFS: list adapter count=7",
    "I/PREFS: clicked about",
]
RUN1 = [
    "I/PREFS: defaults sound=true music=false difficulty=1 name=Player",
    "I/PREFS: difficulty entry=Normal summary=Normal",
    "I/PREFS: changed sound=false",
    "I/PREFS: after sound music enabled=false difficulty summary=Normal name=Player",
    "I/PREFS: changed difficulty=2",
    "I/PREFS: after difficulty music enabled=false difficulty summary=Hard name=Player",
    "I/PREFS: changed name=Ada",
    "I/PREFS: after name music enabled=false difficulty summary=Hard name=Ada",
]
RUN2 = [
    "I/PREFS: defaults sound=false music=false difficulty=2 name=Ada",
    "I/PREFS: difficulty entry=Hard summary=Hard",
    "I/PREFS: changed sound=true",
    "I/PREFS: after sound music enabled=true difficulty summary=Hard name=Ada",
    "I/PREFS: changed music=true",
]


def require(log_path, needles, banned):
    text = open(log_path, encoding="utf-8", errors="replace").read()
    fails = 0
    tag = os.path.basename(os.path.dirname(log_path))
    for line in needles:
        ok = line in text
        print("%s %s has %s" % ("ok  " if ok else "FAIL", tag, line))
        fails += 0 if ok else 1
    for line in banned:
        ok = line not in text
        print("%s %s lacks %s" % ("ok  " if ok else "FAIL", tag, line))
        fails += 0 if ok else 1
    return fails


def main():
    if len(sys.argv) != 3:
        print("usage: check_shots.py <run1-dir> <run2-dir>")
        return 2
    run1, run2 = sys.argv[1], sys.argv[2]
    banned = ["Exception", "changed music"]
    fails = require(os.path.join(run1, "prefs.log"), BOTH + RUN1, banned)
    fails += require(os.path.join(run2, "prefs.log"), BOTH + RUN2, ["Exception"])
    fails += check(os.path.join(run1, "prefs.png"), [(1240, 180, TEAL, "sound checked")])
    fails += check(os.path.join(run1, "off.png"), [(1220, 180, GREY_BOX, "sound unchecked"),
                                                   (1240, 180, 0xFFFAFAFA, "box empty")])
    fails += check(os.path.join(run1, "dialog.png"), [(100, 700, DIM, "dimmed behind dialog"),
                                                      (302, 357, TEAL, "Normal selected"),
                                                      (640, 300, 0xFFFFFFFF, "dialog")])
    fails += check(os.path.join(run1, "done.png"), [(640, 300, 0xFFFAFAFA, "dialogs closed")])
    fails += check(os.path.join(run2, "prefs.png"), [(1220, 180, GREY_BOX, "sound restored unchecked")])
    if fails:
        print("FAIL %d" % fails)
        return 1
    print("ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
