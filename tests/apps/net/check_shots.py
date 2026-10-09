#!/usr/bin/env python3
"""Checks tests/apps/net (needs no network: everything runs over loopback).

    tools/build_apk.sh tests/apps/net
    mkdir -p build/shots/net
    build/host/switchapk-host --data build/data --screen 1280x720@240 \\
        --script tests/apps/net/net.script --screenshots build/shots/net \\
        build/apps/net/net.apk > build/shots/net/net.log 2>&1
    python3 tests/apps/net/check_shots.py build/shots/net

The app talks HTTP and UDP to a server on 127.0.0.1 inside the process, expects https and an .invalid host to fail,
reads ConnectivityManager, then writes /data/local/tmp/network to switch the host's reported network to none and
then to ethernet, and waits for the callbacks and CONNECTIVITY_ACTION broadcasts. The swatch turns green when every
event arrived.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from shotlib import check  # noqa: E402

GREEN = 0xFF43A047

LINES = [
    "I/NET: active WIFI connected=true type=1",
    "I/NET: caps wifi=true internet=true validated=true metered=false",
    "I/NET: link wlan0 handle=true",
    "I/NET: default available wifi",
    "I/NET: broadcast noConnectivity=false type=WIFI",
    "I/NET: http 200 text/plain hello from switchapk!",
    "I/NET: gzip 200 length=-1 encoding=null squeezed through gzip",
    "I/NET: raw gzip encoding=gzip magic=1f8b text=squeezed through gzip",
    'I/NET: post 201 {"a":1}',
    "I/NET: 404 404 err=nope",
    "I/NET: https javax.net.ssl.SSLHandshakeException",
    "I/NET: dns UnknownHostException",
    "I/NET: udp ping",
    "I/NET: default lost",
    "I/NET: broadcast noConnectivity=true type=WIFI",
    "I/NET: offline active=null network=null",
    "I/NET: default available ethernet",
    "I/NET: ethernet available",
    "I/NET: broadcast noConnectivity=false type=ETHERNET",
    "I/NET: online active=ETHERNET",
    "I/NET: all ok",
]


def main():
    if len(sys.argv) != 2:
        print("usage: check_shots.py <shots-dir>")
        return 2
    d = sys.argv[1]
    text = open(os.path.join(d, "net.log"), encoding="utf-8", errors="replace").read()
    fails = 0
    for line in LINES:
        ok = line in text
        print("%s has %s" % ("ok  " if ok else "FAIL", line))
        fails += 0 if ok else 1
    # The ethernet-only request must not fire while the network is Wi-Fi.
    first_eth = text.find("I/NET: ethernet available")
    ok = first_eth > text.find("I/NET: offline active=null")
    print("%s ethernet callback only after the switch" % ("ok  " if ok else "FAIL"))
    fails += 0 if ok else 1
    fails += check(os.path.join(d, "net.png"), [(640, 120, GREEN, "all-ok swatch")])
    if fails:
        print("FAIL %d" % fails)
        return 1
    print("ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
