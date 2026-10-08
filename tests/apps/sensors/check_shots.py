#!/usr/bin/env python3
"""Checks tests/apps/sensors (system services: sensors, battery, rumble, power).

    tools/build_apk.sh tests/apps/sensors
    mkdir -p build/shots/sensors
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/sensors/sensors.script --screenshots build/shots/sensors \\
        build/apps/sensors/sensors.apk > build/shots/sensors/sensors.log 2>&1
    python3 tests/apps/sensors/check_shots.py build/shots/sensors

The script sets the headless accelerometer and gyroscope (sensor command) and the battery (battery command). The
app reads the sensors at rest, after a quarter turn about z (gyroscope only, so the fused rotation must integrate
it) and after the top edge is lifted by 45 degrees (the accelerometer pulls the fused tilt there), then follows the
battery to 12 percent and onto a charger. It also rumbles a two-pulse waveform and checks PowerManager. The swatch
turns green when every expected event arrived.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

GREEN = 0xFF43A047

LINES = [
    "I/SENSORS: have 1=true 4=true 9=true 10=true 11=true 15=true 3=true 2=false 5=false",
    "I/SENSORS: list 9 accel android.sensor.accelerometer minDelay 5000",
    "I/SENSORS: register twice false null false",
    "I/SENSORS: legacy sensors 131 register true",
    "I/SENSORS: flush started true unknown false",
    "I/SENSORS: flush Gyroscope",
    "I/SENSORS: trigger IllegalArgumentException",
    "I/SENSORS: math true azimuth 0 inclination -61 remap 0.0 1.0 altitude 989",
    "I/SENSORS: battery level=100 scale=100 plugged=0 status=3 capacity=100 charging=false",
    "I/SENSORS: vibrator has=true amplitude=true ids=1 click=1",
    "I/SENSORS: power interactive=true save=false held true then false",
    "I/SENSORS: accuracy accel 3",
    "I/SENSORS: legacy accel 9.8 n=6",
    "I/SENSORS: changed level=100 plugged=0 status=3 low=false initial=true",
    "I/SENSORS: thermal 0",
    "I/headless: vibrate 40 ms amplitude 255",
    "I/headless: vibrate 40 ms amplitude 200",
    "I/SENSORS: oneshot rejects 0",
    "I/SENSORS: rest accel 0.0 0.0 9.8 gravity 0.0 0.0 9.8 linear 0.0 0.0 0.0 grv 0.00 0.00 0.00 1.00",
    "I/SENSORS: yaw ok grv 0.00 0.00 0.71 0.71 getOrientation -90",
    "I/SENSORS: tilt ok pitch=-45 roll=0",
    "I/SENSORS: sensors off",
    "I/SENSORS: changed level=12 plugged=0 status=3 low=true initial=false",
    "I/SENSORS: BATTERY_LOW",
    "I/SENSORS: changed level=50 plugged=1 status=2 low=false initial=false",
    "I/SENSORS: charging level 50",
    "I/SENSORS: POWER_CONNECTED",
    "I/SENSORS: BATTERY_OKAY",
    "I/SENSORS: all ok",
]


def main():
    if len(sys.argv) != 2:
        print("usage: check_shots.py <shots-dir>")
        return 2
    d = sys.argv[1]
    text = open(os.path.join(d, "sensors.log"), encoding="utf-8", errors="replace").read()
    fails = 0
    for line in LINES:
        ok = line in text
        print("%s has %s" % ("ok  " if ok else "FAIL", line))
        fails += 0 if ok else 1
    # The flush completes after flush() returns, as on Android.
    ok = text.find("I/SENSORS: flush started") < text.find("I/SENSORS: flush Gyroscope")
    print("%s flush completes later" % ("ok  " if ok else "FAIL"))
    fails += 0 if ok else 1
    fails += check(os.path.join(d, "sensors.png"), [(640, 120, GREEN, "all-ok swatch")])
    if fails:
        print("FAIL %d" % fails)
        return 1
    print("ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
