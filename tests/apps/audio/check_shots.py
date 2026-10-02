#!/usr/bin/env python3
"""Checks tests/apps/audio: five sources mixed, then the window turns green.

A missing source or a silent mix leaves the window magenta (#FFFF00FF).
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    path = os.path.join(d, "audio.png")
    fails = check(path, [
        (640, 360, 0xFF2E7D32, "mixed audio: green center"),
        (40, 40, 0xFF2E7D32, "mixed audio: green corner"),
        (1200, 680, 0xFF2E7D32, "mixed audio: green bottom-right"),
    ])
    print("FAIL: %d" % fails if fails else "PASS audio")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
