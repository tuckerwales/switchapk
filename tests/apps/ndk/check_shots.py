#!/usr/bin/env python3
"""Checks tests/apps/ndk: the JNI checks against libndktest.so all passed (green panel; red when one failed)."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = check(os.path.join(d, "ndk.png"), [
        (40, 40, 0xFF2E7D32, "all NDK checks passed (green panel)"),
        (1240, 680, 0xFF2E7D32, "panel fills the window"),
    ])
    print("FAIL: %d" % fails if fails else "PASS ndk")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
