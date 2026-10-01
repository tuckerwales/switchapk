"""Small PNG reader and pixel assertions shared by the app screenshot checks."""
import struct
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


def close(c1, c2, tol):
    for shift in (0, 8, 16, 24):
        if abs(((c1 >> shift) & 0xff) - ((c2 >> shift) & 0xff)) > tol:
            return False
    return True


def check(path, expectations, tol=6):
    """expectations: list of (x, y, 0xAARRGGBB, label). Returns the number of failures."""
    w, h, px = read_png(path)
    failures = 0
    for x, y, want, label in expectations:
        got = pixel(px, w, x, y)
        ok = close(got, want, tol)
        if not ok:
            failures += 1
        print("%s %s (%d,%d) %s: got #%08X want #%08X" % ("ok  " if ok else "FAIL", path.split("/")[-1], x, y,
                                                         label, got, want))
    return failures
