#!/usr/bin/env python3
"""Checks tests/apps/procs: ProcessBuilder running a bionic executable in-process (spawn.c).

    tools/build_apk.sh tests/apps/procs
    mkdir -p build/shots/procs
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/procs/procs.script --screenshots build/shots/procs \\
        build/apps/procs/procs.apk > build/shots/procs/procs.log 2>&1
    python3 tests/apps/procs/check_shots.py build/shots/procs

The executable echoes stdin upper-cased, reports argv, its environment and a global that a fresh
image resets, writes from a thread and to stderr, creates a file relative to its working
directory (without one, as run2, the path resolves under "/" and fails, as on Android), and ends by returning, by exit() and by exit() from a thread. A copy in the app's
files runs too; destroy() ends a process waiting on stdin; a shell, a missing path and a
non-executable fail like a failed exec.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

GREEN = 0xFF43A047

EXPECT = [
    "I/PROCS: lib exists=true canExecute=true",
    "I/PROCS: run1 out=argc=2 argv1=hello counter=10 env=abc|ABC|XYZ|from thread|wrote rel.txt|",
    "I/PROCS: run1 err=to stderr| code=3 exitValue=3",
    "I/PROCS: rel.txt=relative",
    "I/PROCS: run2 out=argc=2 argv1=exit counter=10 env=(null)|AGAIN|from thread|to stderr| code=5",
    "I/PROCS: run3 out=argc=2 argv1=thread-exit counter=10 env=(null)| code=9",
    "I/PROCS: run4 running=true destroyed code=3",
    "I/PROCS: start fails error=2, No such file or directory",
    "I/PROCS: start fails error=8, Exec format error",
    "I/PROCS: done",
]


def main():
    if len(sys.argv) != 2:
        print("usage: check_shots.py <shots-dir>")
        return 2
    d = sys.argv[1]
    text = open(os.path.join(d, "procs.log"), encoding="utf-8", errors="replace").read()
    fails = 0
    for line in EXPECT:
        ok = line in text
        print("%s has %s" % ("ok  " if ok else "FAIL", line))
        fails += 0 if ok else 1
    fails += check(os.path.join(d, "procs.png"), [(640, 360, GREEN, "no failure")])
    if fails:
        print("FAIL %d" % fails)
        return 1
    print("ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
