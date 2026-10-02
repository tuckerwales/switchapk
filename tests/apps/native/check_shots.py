#!/usr/bin/env python3
"""Checks tests/apps/native: NativeActivity clears an EGL window to red, then paints a gold rect via ANativeWindow_lock.

A failed EGL setup fills the window with magenta (#FFFF00FF).
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    path = os.path.join(d, "native.png")
    fails = check(path, [
        (40, 40, 0xFFFF0000, "EGL clear: red corner"),
        (1200, 40, 0xFFFF0000, "EGL clear: red top-right"),
        (40, 680, 0xFFFF0000, "EGL clear: red bottom-left"),
        (640, 360, 0xFFFFC107, "ANativeWindow_lock gold rectangle"),
        (400, 200, 0xFFFFC107, "gold rectangle inside the quarter bounds"),
        (200, 100, 0xFFFF0000, "red outside the gold rectangle"),
    ])
    print("FAIL: %d" % fails if fails else "PASS native")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
