#!/usr/bin/env python3
"""Checks tests/apps/prefs (android.preference) across two host runs that share --data.

    tools/build_apk.sh tests/apps/prefs
    rm -rf build/data/apps/com.example.prefs
    mkdir -p build/shots/prefs1 build/shots/prefs2
    for r in 1 2; do build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/prefs/prefs.script --screenshots build/shots/prefs$r \\
        build/apps/prefs/prefs.apk > build/shots/prefs$r/prefs.log 2>&1; done
    python3 tests/apps/prefs/check_shots.py build/shots/prefs1 build/shots/prefs2

Run 1: PreferenceManager.setDefaultValues writes the XML defaults, then a legacy PreferenceActivity lists the
hierarchy. The script unticks Sound (Volume depends on it and greys out), turns Music on, picks Hard in the
Level list dialog (the summary formats the entry with %s) and types a name in the EditText dialog. Run 2 must
read the changed values back, with setDefaultValues leaving them alone.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import close, pixel, read_png  # noqa: E402

RUN1 = [
    "I/PREFS: defaults sound=true music=false level=2 name=Player has=true",
    "I/PREFS: volume enabled=true level summary=Now: Normal",
    "I/PREFS: changed sound=false volume enabled=true level summary=Now: Normal",
    "I/PREFS: changed music=true volume enabled=false level summary=Now: Normal",
    "I/PREFS: changed level=3 volume enabled=false level summary=Now: Hard",
    "I/PREFS: changed name=Ana volume enabled=false level summary=Now: Hard",
]
RUN2 = ["I/PREFS: defaults sound=false music=true level=3 name=Ana has=true"]
SWITCH_ON = 0xFF008274   # the Material switch thumb, accent colour
SWITCH_OFF = 0xFFB9B9B9  # the off track
SHOTS = ["prefs_start.png", "prefs_toggled.png", "prefs_list_dialog.png", "prefs_edit_dialog.png", "prefs_done.png"]


def check_log(path, lines):
    fails = 0
    text = open(path).read() if os.path.exists(path) else ""
    pos = 0
    for line in lines:
        i = text.find(line, pos)
        if i < 0:
            print("FAIL %s: missing (in order) %r" % (os.path.basename(path), line))
            fails += 1
        else:
            pos = i + len(line)
    for bad in ("Exception", "STUB:"):
        if bad in text:
            print("FAIL %s: contains %s" % (os.path.basename(path), bad))
            fails += 1
    return fails


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: check_shots.py <run1-dir> <run2-dir>")
    d1, d2 = sys.argv[1], sys.argv[2]
    fails = check_log(os.path.join(d1, "prefs.log"), RUN1) + check_log(os.path.join(d2, "prefs.log"), RUN2)
    for name in SHOTS:
        if not os.path.exists(os.path.join(d1, name)):
            print("FAIL missing %s" % name)
            fails += 1
    if not fails:
        w, h, px = read_png(os.path.join(d1, "prefs_start.png"))
        c = pixel(px, w, 1235, 386)
        if not close(c, SWITCH_OFF, 12):
            print("FAIL Music switch should start off: %08x" % c)
            fails += 1
        w, h, px = read_png(os.path.join(d1, "prefs_toggled.png"))
        c = pixel(px, w, 1235, 386)
        if not close(c, SWITCH_ON, 12):
            print("FAIL Music switch should be on after the tap: %08x" % c)
            fails += 1
    print("ok" if not fails else "%d failures" % fails)
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
