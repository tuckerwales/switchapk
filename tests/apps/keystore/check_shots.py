#!/usr/bin/env python3
"""Checks tests/apps/keystore across two host runs that share --data.

    rm -rf build/data/apps/com.example.keystore
    mkdir -p build/shots/keystore1 build/shots/keystore2
    for n in 1 2; do
        build/host/switchapk-host --data build/data --screen 1280x720@240 \\
            --script tests/apps/keystore/keystore.script --screenshots build/shots/keystore$n \\
            build/apps/keystore/keystore.apk > build/shots/keystore$n/keystore.log 2>&1
    done
    python3 tests/apps/keystore/check_shots.py build/shots/keystore1 build/shots/keystore2

Run 1 generates an AndroidKeyStore AES-GCM key and seals a secret; run 2
loads the key and opens it (green, then blue; red on any exception).
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

GREEN = 0xFF43A047
BLUE = 0xFF1E88E5

RUN1 = ["I/KEYSTORE: has key false", "I/KEYSTORE: sealed iv=12 ct=26"]
RUN2 = ["I/KEYSTORE: has key true", "I/KEYSTORE: opened the secret"]
BOTH = [
    "I/KEYSTORE: opaque alg=AES format=null encoded=null",
    "I/KEYSTORE: info box size=256 purposes=3 modes=GCM hw=false",
    "I/KEYSTORE: mode enforced",
    "I/KEYSTORE: imported decrypt 140f0f1011b5223d79587717ffd9ec3a",
    "I/KEYSTORE: purpose enforced",
    "I/KEYSTORE: aliases [box, imported]",
    "I/KEYSTORE: after delete [box] size=1",
    "I/KEYSTORE: totp 94287082",
    "I/KEYSTORE: keystore mac 32",
]


def require(log_path, needles):
    text = open(log_path, encoding="utf-8", errors="replace").read()
    fails = 0
    for line in needles:
        ok = line in text
        print("%s %s has %s" % ("ok  " if ok else "FAIL", os.path.basename(os.path.dirname(log_path)), line))
        if not ok:
            fails += 1
    return fails


def main():
    if len(sys.argv) != 3:
        print("usage: check_shots.py <run1-dir> <run2-dir>")
        return 2
    run1, run2 = sys.argv[1], sys.argv[2]
    fails = require(os.path.join(run1, "keystore.log"), RUN1 + BOTH)
    fails += require(os.path.join(run2, "keystore.log"), RUN2 + BOTH)
    fails += check(os.path.join(run1, "keystore.png"), [(640, 360, GREEN, "first run")])
    fails += check(os.path.join(run2, "keystore.png"), [(640, 360, BLUE, "second run")])
    if fails:
        print("FAIL %d" % fails)
        return 1
    print("ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
