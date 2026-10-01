#!/usr/bin/env python3
"""Checks tests/apps/services/services.script (1280x720 at 240 dpi).

    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/services/services.script --screenshots build/shots \\
        build/apps/services/services.apk > build/shots/services.log 2>&1
    python3 tests/apps/services/check_shots.py build/shots [build/shots/services.log]

MainActivity runs broadcast, service, PendingIntent and alarm steps and compares
the events each produced with what Android delivers, adding a green (match) or
red cell per step; the band turns green when all passed. With the log, the
framework's own lines are checked too: leak warnings for the receiver and
connection LeakActivity left behind, the target-O block on implicit broadcasts
to manifest receivers, and the logged notification.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import close, pixel, read_png  # noqa: E402

GREEN = 0xFF43A047
WHITE = 0xFFFFFFFF
STEPS = 21

LOG_LINES = [
    "has leaked IntentReceiver com.example.services.LeakActivity$1",
    "has leaked ServiceConnection com.example.services.LeakActivity$2",
    "Background execution not allowed: receiving Intent { act=com.example.services.PING",
    "notify 3 [Hello] World",
    "SVC: done steps=%d failed=0" % STEPS,
]


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    fails = 0
    w, h, px = read_png(os.path.join(d, "services.png"))
    if (w, h) != (1280, 720):
        print("FAIL size %dx%d" % (w, h))
        fails += 1
    else:
        if not close(pixel(px, w, 640, 30), GREEN, 6):
            print("FAIL band not green: all steps should pass")
            fails += 1
        # Cells: 40dp (60px) squares with 4dp (6px) margins, ten per row, from y=102.
        green = 0
        for i in range(STEPS + 1):
            x = 6 + (i % 10) * 72 + 30
            y = 102 + (i // 10) * 72 + 30
            c = pixel(px, w, x, y)
            if i < STEPS:
                if close(c, GREEN, 6):
                    green += 1
                else:
                    print("FAIL cell %d is %08x" % (i, c))
                    fails += 1
            elif not close(c, WHITE, 6):
                print("FAIL unexpected cell %d" % i)
                fails += 1
    if len(sys.argv) > 2:
        with open(sys.argv[2], errors="replace") as f:
            log = f.read()
        for line in log.splitlines():
            if "SVC: FAIL" in line:
                print(line)
                fails += 1
        for want in LOG_LINES:
            if want not in log:
                print("FAIL log lacks: " + want)
                fails += 1
    print("FAIL services (%d)" % fails if fails else "PASS services")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
