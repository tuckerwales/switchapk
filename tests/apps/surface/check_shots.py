#!/usr/bin/env python3
"""Checks tests/apps/surface: SurfaceView content from a render thread, TextureView from lockCanvas."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = check(os.path.join(d, "surface.png"), [
        (320, 600, 0xFFF44336, "SurfaceView frame from the render thread"),
        (960, 100, 0xFF4CAF50, "TextureView drawn with lockCanvas"),
        (960, 360, 0xFFFFFFFF, "TextureView circle"),
    ])
    print("FAIL: %d" % fails if fails else "PASS surface")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
