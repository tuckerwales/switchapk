#!/usr/bin/env python3
"""Checks tests/apps/lifecycle/lifecycle.script (starts at 1280x720 at 240 dpi).

    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/lifecycle/lifecycle.script --screenshots build/shots \\
        build/apps/lifecycle/lifecycle.apk > build/shots/lifecycle.log 2>&1
    python3 tests/apps/lifecycle/check_shots.py build/shots [build/shots/lifecycle.log]

MainActivity keeps a counter (band colour), a note EditText and a fragment
counter (swatch) in saved state. The script counts twice, taps the fragment
button and types a note, then docks (screen 1920x1080@360, a density
change), which relaunches the activity: state, the fragment, the
non-configuration instance and a retained fragment must survive. Open
starts DetailActivity for a result; undocking relaunches it while MainActivity
waits stopped (relaunched only when it comes back). Next re-enters Detail
single-top (onNewIntent), B goes back with RESULT_CANCELED (dark strip), and
Done returns RESULT_OK with 6 (blue strip). With the log, the order of the
lifecycle callbacks in each step is compared with what Android does.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import close, pixel, read_png  # noqa: E402

RED = 0xFFE53935
ORANGE = 0xFFFB8C00
YELLOW = 0xFFFDD835
GREEN = 0xFF43A047
CYAN = 0xFF00ACC1
GREY = 0xFF9E9E9E
DARK = 0xFF212121
BLUE = 0xFF1E88E5

# Callback order per script step (ActivityLifecycleCallbacks "cb" lines left out).
STEPS = {
    "dock": [
        "Counter.onPause", "Main.onPause", "Counter.onStop", "Main.onStop", "Main.onSaveInstanceState",
        "Counter.onDestroyView", "Counter.onDestroy", "Main.onDestroy changing=true",
        "Counter.onCreate restored clicks=1 start=5", "Main.onCreate restored", "Main.retained token2",
        "Counter.onCreateView", "Counter.onActivityCreated", "Retained.onActivityCreated instance=1",
        "Main.onStart", "Counter.onStart", "Main.onRestoreInstanceState", "Main.onPostCreate note=hello",
        "Main.onResume", "Counter.onResume",
    ],
    "open": [
        "Counter.onPause", "Main.onPause",
        "Detail.onCreate fresh caller=ComponentInfo{com.example.lifecycle/com.example.lifecycle.MainActivity}",
        "Detail.onStart", "Detail.onResume", "Counter.onStop", "Main.onStop", "Main.onSaveInstanceState",
    ],
    "undock": [
        "Detail.onPause", "Detail.onStop", "Detail.onDestroy",
        "Detail.onCreate restored caller=ComponentInfo{com.example.lifecycle/com.example.lifecycle.MainActivity}",
        "Detail.onStart", "Detail.onResume",
    ],
    "next": ["Detail.onPause", "Detail.onNewIntent item=4", "Detail.onResume"],
    "back": [
        "Detail.onPause", "Counter.onDestroyView", "Counter.onDestroy", "Main.onDestroy changing=true",
        "Counter.onCreate restored clicks=1 start=5", "Main.onCreate restored", "Main.retained token2",
        "Counter.onCreateView", "Counter.onActivityCreated", "Retained.onActivityCreated instance=1",
        "Main.onStart", "Counter.onStart", "Main.onRestoreInstanceState", "Main.onPostCreate note=hello",
        "Main.onActivityResult 7 0 0", "Main.onResume", "Counter.onResume", "Detail.onStop", "Detail.onDestroy",
    ],
    "done": [
        "Detail.onPause", "Main.onRestart", "Main.onStart", "Counter.onStart", "Main.onActivityResult 7 -1 6",
        "Main.onResume", "Counter.onResume", "Detail.onStop", "Detail.onDestroy",
    ],
}


def shot(path, size, expectations):
    w, h, px = read_png(path)
    fails = 0
    if (w, h) != size:
        print("FAIL %s size %dx%d want %dx%d" % (os.path.basename(path), w, h, size[0], size[1]))
        return 1
    for x, y, color, label in expectations:
        got = pixel(px, w, x, y)
        ok = close(got, color, 6)
        print("%s %s (%d,%d) %s: got #%08X want #%08X" % (
            "ok  " if ok else "FAIL", os.path.basename(path), x, y, label, got, color))
        if not ok:
            fails += 1
    return fails


def check_log(path):
    steps = {}
    current = None
    for line in open(path, errors="replace"):
        line = line.strip()
        if "script: " in line:
            current = line.split("script: ", 1)[1]
            steps[current] = []
        elif "LIFE " in line and current is not None:
            event = line.split("LIFE ", 1)[1]
            if not event.startswith("cb "):
                steps[current].append(event)
    fails = 0
    for name, want in STEPS.items():
        got = steps.get(name)
        ok = got == want
        print("%s log step %s" % ("ok  " if ok else "FAIL", name))
        if not ok:
            fails += 1
            print("  got:  %s\n  want: %s" % (got, want))
    retained = [s for s in steps.values() for e in s if e.startswith("Retained.onCreate")]
    if retained:
        print("FAIL retained fragment was created again")
        fails += 1
    return fails


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "build/shots"
    small, big = (1280, 720), (1920, 1080)
    fails = 0
    fails += shot(os.path.join(d, "lifecycle_start.png"), small, [
        (640, 40, RED, "count 0 band"), (640, 105, GREY, "no result yet"), (90, 273, RED, "fragment swatch 0"),
    ])
    fails += shot(os.path.join(d, "lifecycle_counted.png"), small, [
        (640, 40, YELLOW, "count 2 band"), (90, 273, ORANGE, "fragment swatch 1"),
    ])
    fails += shot(os.path.join(d, "lifecycle_docked.png"), big, [
        (960, 60, YELLOW, "count restored after relaunch"), (960, 157, GREY, "strip at 1.5x"),
        (135, 410, ORANGE, "fragment state restored"),
    ])
    fails += shot(os.path.join(d, "lifecycle_detail.png"), big, [(960, 60, GREEN, "detail item 3")])
    fails += shot(os.path.join(d, "lifecycle_detail_undocked.png"), small, [(640, 60, GREEN, "detail relaunched")])
    fails += shot(os.path.join(d, "lifecycle_next.png"), small, [(640, 60, CYAN, "onNewIntent item 4")])
    fails += shot(os.path.join(d, "lifecycle_canceled.png"), small, [
        (640, 40, YELLOW, "count survived the pending relaunch"), (640, 105, DARK, "RESULT_CANCELED strip"),
        (90, 273, ORANGE, "fragment swatch"),
    ])
    fails += shot(os.path.join(d, "lifecycle_result.png"), small, [
        (640, 40, YELLOW, "count"), (640, 105, BLUE, "RESULT_OK with 6"),
    ])
    if len(sys.argv) > 2:
        fails += check_log(sys.argv[2])
    print("FAIL lifecycle (%d)" % fails if fails else "PASS lifecycle")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
