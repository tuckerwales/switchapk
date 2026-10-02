#!/usr/bin/env python3
"""Checks tests/apps/viewdbg (1280x720 at 240 dpi).

    tools/build_apk.sh tests/apps/viewdbg
    build/host/switchapk-host -v --data build/data --screen 1280x720@240 \\
        --script tests/apps/viewdbg/viewdbg.script --screenshots build/shots \\
        build/apps/viewdbg/viewdbg.apk > build/shots/viewdbg.log 2>&1
    python3 tests/apps/viewdbg/check_shots.py build/shots build/shots/viewdbg.log

A 200dp yellow square sits on the page. The activity also checks
ViewDebug annotations and dumpCapturedView.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

PAGE = 0xFF101820
YELLOW = 0xFFFFF59D
CHECKS = 11


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    p = lambda name: os.path.join(d, name)  # noqa: E731
    # Square is 72,180 300x300. Locked from the host screenshot.
    fails += check(p("viewdbg.png"), [
        (20, 20, PAGE, "page"),
        (222, 330, YELLOW, "square center"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        bad = [l for l in text.splitlines() if "VD FAIL" in l]
        ok = len([l for l in text.splitlines() if "VD ok" in l])
        dumped = "label=beta" in text and "getCode()=7" in text and "getBits#getN()=4" in text
        if bad or ok != CHECKS or not dumped:
            print("FAIL logic checks: %d ok of %d, dump %s, %s" % (ok, CHECKS, dumped, bad))
            fails += 1
        else:
            print("ok   %d logic checks" % ok)
    print("FAIL viewdbg (%d)" % fails if fails else "PASS viewdbg")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
