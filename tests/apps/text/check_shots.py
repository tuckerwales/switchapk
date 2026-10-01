#!/usr/bin/env python3
"""Checks tests/apps/text/text.script (1280x720 at 240 dpi, density 1.5).

Heights are exact dp, so the bands are fixed:
  status   y 0-24
  wrap     y 24-168, x 0-300
  center   y 168-240
  end      y 240-300
  ellipsis y 300-408, x 0-270
  spans    y 408-480
  hint     y 480-534, x 0-300
Glyphs are antialiased, so ink is a region predicate. The status bar is a
flat color set from the in-process TEXTCHECK result.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import close, pixel, read_png  # noqa: E402

WINDOW = 0xFFFAFAFA


def has(px, w, x0, y0, x1, y1, pred):
    for y in range(y0, y1):
        for x in range(x0, x1):
            if pred(pixel(px, w, x, y)):
                return True
    return False


def lacks(px, w, x0, y0, x1, y1, pred):
    return not has(px, w, x0, y0, x1, y1, pred)


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    path = os.path.join(d, "text_views.png")
    w, h, px = read_png(path)
    fails = 0

    def expect(x, y, color, label):
        nonlocal fails
        got = pixel(px, w, x, y)
        ok = close(got, color, 6)
        print("%s %s (%d,%d) %s: got #%08X want #%08X" % (
            "ok  " if ok else "FAIL", os.path.basename(path), x, y, label, got, color))
        if not ok:
            fails += 1

    def expect_has(x0, y0, x1, y1, pred, label):
        nonlocal fails
        ok = has(px, w, x0, y0, x1, y1, pred)
        print("%s %s region (%d,%d)-(%d,%d) %s" % (
            "ok  " if ok else "FAIL", os.path.basename(path), x0, y0, x1, y1, label))
        if not ok:
            fails += 1

    def expect_lacks(x0, y0, x1, y1, pred, label):
        nonlocal fails
        ok = lacks(px, w, x0, y0, x1, y1, pred)
        print("%s %s region (%d,%d)-(%d,%d) %s" % (
            "ok  " if ok else "FAIL", os.path.basename(path), x0, y0, x1, y1, label))
        if not ok:
            fails += 1

    def black(c):
        return (c & 0xFFFFFF) < 0x303030 and (c >> 24) > 0xF0

    def blue(c):
        r, g, b = (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF
        return b > 100 and b > r + 40 and b > g

    def green_ink(c):
        r, g, b = (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF
        return g > 70 and g > r + 20 and g > b + 20

    def red_ink(c):
        r, g, b = (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF
        return r > 140 and g < 80 and b < 80

    def solid_green(c):
        r, g, b = (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF
        return g > 200 and r < 80 and b < 80

    def purple(c):
        r, g, b = (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF
        return b > 120 and b > r and r > 60 and g < 80

    expect(10, 4, 0xFF4CAF50, "logic checks passed")
    expect(310, 40, WINDOW, "window to the right of the wrap view")
    expect(10, 176, 0xFFE3F2FD, "center view background above the line")
    expect(280, 350, WINDOW, "ellipsis view does not paint past its width")
    expect(250, 506, 0xFFF3E5F5, "hint view background past the word")

    expect_has(4, 30, 290, 160, black, "wrapped black text")
    expect_lacks(0, 170, 80, 238, blue, "centered text stays off the left edge")
    expect_has(560, 170, 720, 238, blue, "centered text")
    expect_lacks(0, 242, 200, 298, green_ink, "end gravity stays off the left edge")
    expect_has(1100, 242, 1270, 298, green_ink, "end-aligned text")
    expect_has(4, 310, 260, 400, red_ink, "ellipsized red text")
    expect_has(2, 412, 120, 470, red_ink, "foreground span")
    expect_has(40, 412, 700, 470, solid_green, "background span")
    expect_has(4, 486, 160, 528, purple, "hint text")

    print("FAIL: %d" % fails if fails else "PASS text")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
