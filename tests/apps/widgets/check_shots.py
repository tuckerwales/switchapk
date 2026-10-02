#!/usr/bin/env python3
"""Checks tests/apps/widgets/widgets.script (1280x720 at 240 dpi, density 1.5).

Heights are exact dp:
  status    y 0-24
  photo     y 24-144, x 0-120, a square red bitmap
  button    y 144-216
  checkbox  y 216-288, 18dp indicator at the start, centered vertically
  radio A   y 288-360
  radio B   y 360-432
  switch    y 432-504, 64dp track on the end, 20dp thumb

The status bar stays grey until the button, checkbox, radio B and switch
have all been toggled, and turns red if the in-process checks failed.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import close, pixel, read_png  # noqa: E402

WINDOW = 0xFFFAFAFA
GREY = 0xFF9E9E9E
GREEN = 0xFF4CAF50
RED = 0xFFE53935
BLUE = 0xFF1565C0
TRACK = 0xFFBDBDBD
# Theme indicators (btn_check/btn_radio material, drawn as their static state lists).
OUTLINE = 0xFF646464
ACCENT = 0xFF008577
BG = 0xFFFAFAFA
WHITE = 0xFFFFFFFF


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    fails += shot(os.path.join(d, "widgets_off.png"), [
        (10, 4, GREY, "status waiting"),
        (40, 50, RED, "image bitmap"),
        (200, 50, WINDOW, "window beside the image"),
        (1200, 180, BLUE, "button background"),
        (12, 252, OUTLINE, "checkbox off: outline"), (24, 251, BG, "checkbox off: empty box"),
        (10, 326, OUTLINE, "radio A off: ring"), (24, 326, BG, "radio A off: no dot"),
        (10, 398, OUTLINE, "radio B off: ring"), (24, 396, BG, "radio B off: no dot"),
        (1240, 468, TRACK, "switch track off"),
    ])
    fails += shot(os.path.join(d, "widgets_on.png"), [
        (10, 4, GREEN, "button, check, radio B and switch"),
        (40, 50, RED, "image bitmap still"),
        (1200, 180, BLUE, "button background still"),
        (12, 252, ACCENT, "checkbox on: filled box"),
        (10, 326, OUTLINE, "radio A stayed off"), (24, 326, BG, "radio A no dot"),
        (10, 398, ACCENT, "radio B on: ring"), (24, 396, ACCENT, "radio B on: dot"),
        (1200, 468, GREEN, "switch track on"),
        (1270, 468, WHITE, "switch thumb at the end"),
    ])
    print("FAIL: %d" % fails if fails else "PASS widgets")
    return 1 if fails else 0


def shot(path, expectations):
    w, h, px = read_png(path)
    fails = 0
    for x, y, color, label in expectations:
        got = pixel(px, w, x, y)
        ok = close(got, color, 6)
        print("%s %s (%d,%d) %s: got #%08X want #%08X" % (
            "ok  " if ok else "FAIL", os.path.basename(path), x, y, label, got, color))
        if not ok:
            fails += 1
    return fails


if __name__ == "__main__":
    sys.exit(main())
