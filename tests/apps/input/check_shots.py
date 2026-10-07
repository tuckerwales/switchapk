#!/usr/bin/env python3
"""Checks tests/apps/input: a native app in the android_native_app_glue style (own thread, ALooper, command pipe,
AInputQueue, AConfiguration) draws what it received.

  status bar (top, y < 40): green when the ALooper checks passed (ALooper_wake seen as ALOOPER_POLL_WAKE, a
      callback fd ran, AConfiguration matches the display); red otherwise
  gold square at the tapped point (400,300): the AMotionEvent coordinates are surface relative and exact
  pink bar (bottom, y >= 680): a DPAD_CENTER key down and up reached the queue with the right key code
Then BACK is reported unhandled, which finishes the activity (checked in the log when one is given).

Usage: check_shots.py [shots-dir] [log]
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402


def check_log(path):
    text = open(path, errors="replace").read()
    steps = [
        ("app thread started with its own looper", "INPUT looper ready"),
        ("input queue attached to the app looper", "INPUT queue attached"),
        ("touch down at 400,300", "INPUT touch down 400 300"),
        ("DPAD_CENTER down then up", "INPUT key 23 down"),
        ("ALooper_wake reported as ALOOPER_POLL_WAKE", "INPUT wake"),
        ("fd callback ran", "INPUT callback"),
        ("BACK reported unhandled", "INPUT back unhandled"),
        ("onDestroy callback after the activity finished", "INPUT onDestroy"),
    ]
    fails = 0
    for name, needle in steps:
        ok = needle in text
        fails += 0 if ok else 1
        print("%s log step %s" % ("ok  " if ok else "FAIL", name))
    return fails


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = check(os.path.join(d, "input.png"), [
        (20, 20, 0xFF00C853, "status bar green: looper, wake, callback and configuration checks passed"),
        (1260, 20, 0xFF00C853, "status bar spans the window"),
        (400, 300, 0xFFFFC107, "tap marker at the touch point"),
        (380, 280, 0xFFFFC107, "tap marker corner"),
        (300, 300, 0xFF102040, "background left of the marker (touch x is not offset)"),
        (400, 200, 0xFF102040, "background above the marker (touch y is not offset)"),
        (640, 700, 0xFFE91E63, "key bar pink: DPAD_CENTER down and up received"),
    ])
    if len(sys.argv) > 2:
        fails += check_log(sys.argv[2])
    print("FAIL: %d" % fails if fails else "PASS input")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
