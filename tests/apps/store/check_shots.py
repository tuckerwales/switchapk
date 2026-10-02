#!/usr/bin/env python3
"""Checks tests/apps/store across two host runs that share --data.

    rm -rf build/data/apps/com.example.store
    mkdir -p build/shots/store1 build/shots/store2
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/store/store.script --screenshots build/shots/store1 \\
        build/apps/store/store.apk > build/shots/store1/store.log 2>&1
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/store/store.script --screenshots build/shots/store2 \\
        build/apps/store/store.apk > build/shots/store2/store.log 2>&1
    python3 tests/apps/store/check_shots.py build/shots/store1 build/shots/store2

Run 1 creates notes.db (SQLiteOpenHelper version 1), rolls a row back, keeps
another, rejects a duplicate key, and writes a preference. Run 2 opens the
same file at version 2 (onUpgrade) and reads that preference back. Both runs
also check DatabaseUtils, a cursor window, settings, media, and FileProvider.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

GREEN = 0xFF43A047
BLUE = 0xFF1E88E5
CREATED = "I/STORE: query created n=1 body=alpha blob=4:1,4 score=1.5"

RUN1 = [
    "I/STORE: onCreate notes.db",
    CREATED,
    "I/STORE: query kept n=1 body=beta",
    "I/STORE: query rolled n=0",
    "I/STORE: constraint",
    "I/STORE: readonly",
    "I/STORE: pref-write",
    "I/STORE: context-db write 4",
    "I/STORE: memory 3",
    "I/STORE: entries 2",
]
RUN2 = [
    "I/STORE: onUpgrade 1 2",
    CREATED,
    "I/STORE: query kept n=1 body=beta",
    "I/STORE: query rolled n=0",
    "I/STORE: query upgrade n=1 body=v1to2 extra=added",
    "I/STORE: pref-read run1 7",
    "I/STORE: context-db read 4",
    "I/STORE: memory 3",
    "I/STORE: entries 3",
    "I/STORE: settings prev=40",
]
BOTH = [
    "I/STORE: string alpha",
    "I/STORE: escape ok",
    "I/STORE: where ok",
    "I/STORE: types 1,8,6,99,1",
    "I/STORE: collate ok",
    "I/STORE: window body=alpha",
    "I/STORE: tiny rows=1",
    "I/STORE: parcel body=alpha",
    "I/STORE: blobfd 4:1,4",
    "I/STORE: row body=alpha",
    "I/STORE: settings 40",
    "I/STORE: media content://media/external/images/media shot.png",
    "I/STORE: file note.txt=hello text/plain",
    "I/STORE: file escape",
    "I/STORE: dump 9 v3",
]


def require(log_path, needles, banned):
    text = open(log_path, encoding="utf-8", errors="replace").read()
    fails = 0
    for line in needles:
        ok = line in text
        print("%s %s has %s" % ("ok  " if ok else "FAIL", os.path.basename(os.path.dirname(log_path)), line))
        if not ok:
            fails += 1
    for line in banned:
        ok = line not in text
        print("%s %s lacks %s" % ("ok  " if ok else "FAIL", os.path.basename(os.path.dirname(log_path)), line))
        if not ok:
            fails += 1
    return fails


def main():
    if len(sys.argv) != 3:
        print("usage: check_shots.py <run1-dir> <run2-dir>")
        return 2
    run1, run2 = sys.argv[1], sys.argv[2]
    fails = 0
    fails += require(os.path.join(run1, "store.log"), RUN1 + BOTH, ["I/STORE: onUpgrade"])
    fails += require(os.path.join(run2, "store.log"), RUN2 + BOTH, ["I/STORE: onCreate"])
    fails += check(os.path.join(run1, "store.png"), [(640, 360, GREEN, "create swatch")])
    fails += check(os.path.join(run2, "store.png"), [(640, 360, BLUE, "upgrade swatch")])
    if fails:
        print("FAIL %d" % fails)
        return 1
    print("ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
