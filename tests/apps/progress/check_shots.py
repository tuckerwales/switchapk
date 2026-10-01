#!/usr/bin/env python3
"""Checks tests/apps/progress/progress.script (1280x720 at 240 dpi).

    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/progress/progress.script --screenshots build/shots \\
        build/apps/progress/progress.apk > build/shots/progress.log 2>&1
    python3 tests/apps/progress/check_shots.py build/shots [build/shots/progress.log]

Material ProgressBar (40% primary, 70% secondary), the spinners (their static
frame: animators run once WS5 lands, as on Android with animations off), an
indeterminate horizontal bar, a SeekBar at 25 and a RatingBar at 3.5. The
script drags the SeekBar to 75, taps the fifth star, then opens a horizontal
ProgressDialog at 60/100. With the log, the listener calls are checked too.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

ACCENT = 0xFF008577
TRACK = 0xFFD8D8D8
SECONDARY = 0xFFA0C2BE
WHITE = 0xFFFFFFFF
DIM = 0xFF666666

LOG = [
    "PROG: horizontal 40/100 secondary 70 indeterminate true",
    "PROG: seek start",
    "PROG: seek 75 user=true",
    "PROG: seek stop",
    "PROG: rating 5.0 user=true",
    "PROG: dialog 60/100",
]


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    fails += check(os.path.join(d, "progress_start.png"), [
        (144, 36, ACCENT, "primary progress 40%"), (354, 36, SECONDARY, "secondary progress 70%"),
        (540, 36, TRACK, "track"), (40, 84, ACCENT, "spinner static frame"),
        (110, 133, ACCENT, "seek progress 25"), (186, 133, ACCENT, "seek thumb"), (400, 133, TRACK, "seek track"),
        (60, 220, ACCENT, "star 1"), (262, 225, ACCENT, "half of star 4"), (290, 225, WHITE, "gap in star 4"),
        (348, 220, 0xFFD7D7D7, "star 5 empty"),
    ])
    fails += check(os.path.join(d, "progress_changed.png"), [
        (400, 133, ACCENT, "seek progress 75"), (462, 133, ACCENT, "seek thumb at 75"), (540, 133, TRACK, "seek track"),
        (348, 220, ACCENT, "star 5 filled"),
    ])
    fails += check(os.path.join(d, "progress_dialog.png"), [
        (400, 413, ACCENT, "dialog progress 60%"), (800, 413, TRACK, "dialog track"), (640, 300, WHITE, "dialog"),
        (100, 600, DIM, "dim behind dialog"),
    ])
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            log = [l.split("/", 1)[-1].strip() for l in f.read().splitlines() if "PROG:" in l]
        if log != LOG:
            print("FAIL log: expected %s got %s" % (LOG, log))
            fails += 1
    print("FAIL progress (%d)" % fails if fails else "PASS progress")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
