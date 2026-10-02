#!/usr/bin/env python3
"""Checks tests/apps/video/video.script (1280x720 at 240 dpi).

    tools/build_apk.sh tests/apps/video
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/video/video.script --screenshots build/shots \\
        build/apps/video/video.apk > build/shots/video.log 2>&1
    python3 tests/apps/video/check_shots.py build/shots build/shots/video.log

A MediaController floats on the dark stage: play, rewind and fast-forward,
a seek bar at 1:05 of 2:05, and the status line "controls". Play then opens
a missing file. prepareAsync fails (no decoder yet) and VideoView shows the
framework dialog "Can't play this video."
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

WHITE = 0xFFFFFFFF
BLACK = 0xFF000000
ACCENT = 0xFF008577
STATUS = 0xFFF5F5F5
TEXT = 0xFF212121
DIM_STATUS = 0xFF626262
DIM_BUTTON = 0xFF00352F
CHECKS = 16


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    p = lambda name: os.path.join(d, name)  # noqa: E731
    fails += check(p("video_controls.png"), [
        (400, 200, BLACK, "empty video surface"),
        (638, 488, WHITE, "play triangle"),
        (400, 550, ACCENT, "seek bar at 1:05"),
        (20, 600, STATUS, "status line"),
        (100, 680, ACCENT, "Play button"),
    ])
    fails += check(p("video_error.png"), [
        (640, 290, WHITE, "error dialog"),
        (425, 326, TEXT, "Can't play this video"),
        (969, 396, ACCENT, "OK"),
        (20, 590, DIM_STATUS, "status dimmed behind the dialog"),
        (640, 660, DIM_BUTTON, "Play button dimmed"),
        (80, 200, BLACK, "stage still black outside the dialog"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            text = f.read()
        bad = [l for l in text.splitlines() if "VVCHECK FAIL" in l]
        ok = len([l for l in text.splitlines() if "VVCHECK ok" in l])
        if bad or ok != CHECKS:
            print("FAIL logic checks: %d ok of %d, %s" % (ok, CHECKS, bad))
            fails += 1
        else:
            print("ok   %d logic checks" % ok)
    print("FAIL video (%d)" % fails if fails else "PASS video")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
