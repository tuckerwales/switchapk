#!/usr/bin/env python3
"""Check a hello screenshot: the default theme's action bar (the manifest sets
no theme, so targetSdk 29 gets Theme.DeviceDefault.Light.DarkActionBar), then
the dark content background and the gold rectangle below it."""
import struct
import sys
import zlib


def read_png(path):
    data = open(path, "rb").read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit("not a png: " + path)
    pos = 8
    w = h = None
    raw = b""
    while pos + 8 <= len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            w, h, depth, color = struct.unpack(">IIBB", chunk[:10])
            if depth != 8 or color != 6:
                raise SystemExit("expected 8-bit RGBA, got depth %s color %s" % (depth, color))
        elif kind == b"IDAT":
            raw += chunk
        elif kind == b"IEND":
            break
    rows = zlib.decompress(raw)
    stride = w * 4 + 1
    px = bytearray(w * h * 4)
    for y in range(h):
        row = rows[y * stride:(y + 1) * stride]
        if row[0] != 0:
            raise SystemExit("unsupported png filter %s" % row[0])
        px[y * w * 4:(y + 1) * w * 4] = row[1:]
    return w, h, px


def pixel(px, w, x, y):
    i = (y * w + x) * 4
    r, g, b, a = px[i:i + 4]
    return (a << 24) | (r << 16) | (g << 8) | b


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "hello.png"
    w, h, px = read_png(path)
    if (w, h) != (1280, 720):
        raise SystemExit("size %sx%s, expected 1280x720" % (w, h))
    checks = {
        (10, 10): 0xFF1A1B20,
        (10, 71): 0xFF1A1B20,
        (10, 72): 0xFF101820,
        (100, 172): 0xFFE8C547,
        (200, 222): 0xFFE8C547,
        (600, 472): 0xFF101820,
    }
    failed = False
    for (x, y), expect in checks.items():
        got = pixel(px, w, x, y)
        if got != expect:
            print("FAIL (%d,%d) got #%08X expected #%08X" % (x, y, got, expect))
            failed = True
        else:
            print("ok (%d,%d) #%08X" % (x, y, got))
    # Text is anti-aliased. Require some non-background ink in the label band.
    ink = 0
    for y in range(412, 472):
        for x in range(80, 520):
            if pixel(px, w, x, y) != 0xFF101820:
                ink += 1
    print("text ink pixels: %d" % ink)
    title = 0
    for y in range(20, 52):
        for x in range(24, 90):
            if pixel(px, w, x, y) != 0xFF1A1B20:
                title += 1
    print("title ink pixels: %d" % title)
    if title < 50:
        print("FAIL action bar title did not draw")
        failed = True
    if ink < 50:
        print("FAIL text did not draw")
        failed = True
    if failed:
        raise SystemExit(1)
    print("PASS", path)


if __name__ == "__main__":
    main()
