#!/usr/bin/env python3
"""Checks tests/apps/gles: a GLES2 textured cube and a GLES1 (GL10) triangle rendered through GLSurfaceView.

Some Mesa builds cannot create ES1 contexts (Ubuntu's llvmpipe among them). Then the right half must show
GLSurfaceView's "OpenGL ES unavailable" panel instead of crashing, which is checked instead of the triangle.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check, close, pixel, read_png  # noqa: E402


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    # A failed in-app check turns the GLES2 clear color magenta (#FFFF00FF).
    path = os.path.join(d, "gles.png")
    fails = check(path, [
        (20, 700, 0xFF1A1A4D, "GLES2 clear color (all in-app checks passed)"),
        (320, 360, 0xFFFFC107, "GLES2 cube face: GLUtils texture through shaders and VBOs"),
        (320, 120, 0xFF1A1A4D, "GLES2 background above the cube (perspective and depth)"),
        (300, 340, 0xFFFFC107, "GLES2 cube face near the centre"),
    ])
    w, h, px = read_png(path)
    if close(pixel(px, w, 660, 20), 0xFF202020, 6):
        print("note: the GL driver has no ES1 contexts; checking the graceful failure panel instead")
        fails += check(path, [
            (660, 20, 0xFF202020, "GLES1 unavailable: GLSurfaceView failure panel"),
            (700, 680, 0xFF202020, "GLES1 unavailable: failure panel fills the view"),
        ])
    else:
        fails += check(path, [
            (660, 20, 0xFF330000, "GLES1 clear color"),
            (960, 400, 0xFF00C853, "GLES1 triangle from glVertexPointer + glColor4f through GL10"),
            (700, 680, 0xFF330000, "GLES1 outside the triangle"),
        ])
    print("FAIL: %d" % fails if fails else "PASS gles")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
