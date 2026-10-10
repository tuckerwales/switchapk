#!/usr/bin/env python3
"""Converts a binary PPM (P6) to a baseline JPEG, standard library only.

Used by Makefile.switch for the NRO icon: switchapk-host --nro-icon renders
the icon as PPM and elf2nro only takes JPEG.

  tools/ppm_to_jpeg.py in.ppm out.jpg [quality]
"""
import math
import struct
import sys

ZIGZAG = [
    0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5,
    12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
    35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51,
    58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63,
]

# ITU T.81 Annex K tables (natural order).
LUMA_Q = [
    16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55,
    14, 13, 16, 24, 40, 57, 69, 56, 14, 17, 22, 29, 51, 87, 80, 62,
    18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92,
    49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99,
]
CHROMA_Q = [
    17, 18, 24, 47, 99, 99, 99, 99, 18, 21, 26, 66, 99, 99, 99, 99,
    24, 26, 56, 99, 99, 99, 99, 99, 47, 66, 99, 99, 99, 99, 99, 99,
] + [99] * 32

DC_LUMA_BITS = [0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0]
DC_LUMA_VALS = list(range(12))
DC_CHROMA_BITS = [0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0]
DC_CHROMA_VALS = list(range(12))
AC_LUMA_BITS = [0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7D]
AC_LUMA_VALS = bytes.fromhex(
    "01020300041105122131410613516107227114328191a1082342b1c11552d1f02433627282090a161718191a25262728292a3435"
    "363738393a434445464748494a535455565758595a636465666768696a737475767778797a838485868788898a92939495969798"
    "999aa2a3a4a5a6a7a8a9aab2b3b4b5b6b7b8b9bac2c3c4c5c6c7c8c9cad2d3d4d5d6d7d8d9dae1e2e3e4e5e6e7e8e9eaf1f2f3f4"
    "f5f6f7f8f9fa")
AC_CHROMA_BITS = [0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77]
AC_CHROMA_VALS = bytes.fromhex(
    "000102031104052131061241510761711322328108144291a1b1c109233352f0156272d10a162434e125f11718191a262728292a"
    "35363738393a434445464748494a535455565758595a636465666768696a737475767778797a82838485868788898a9293949596"
    "9798999aa2a3a4a5a6a7a8a9aab2b3b4b5b6b7b8b9bac2c3c4c5c6c7c8c9cad2d3d4d5d6d7d8d9dae2e3e4e5e6e7e8e9eaf2f3f4"
    "f5f6f7f8f9fa")


def huff_codes(bits, vals):
    codes, code, k = {}, 0, 0
    for length in range(1, 17):
        for _ in range(bits[length - 1]):
            codes[vals[k]] = (code, length)
            code += 1
            k += 1
        code <<= 1
    return codes


def scale_q(table, quality):
    quality = max(1, min(100, quality))
    s = 5000 // quality if quality < 50 else 200 - quality * 2
    return [max(1, min(255, (q * s + 50) // 100)) for q in table]


def read_ppm(path):
    data = open(path, "rb").read()
    fields, pos = [], 0
    while len(fields) < 4:
        while data[pos:pos + 1].isspace():
            pos += 1
        if data[pos:pos + 1] == b"#":
            while data[pos:pos + 1] not in (b"\n", b""):
                pos += 1
            continue
        start = pos
        while not data[pos:pos + 1].isspace():
            pos += 1
        fields.append(data[start:pos])
    if fields[0] != b"P6" or int(fields[3]) != 255:
        raise SystemExit("%s: expected an 8-bit binary PPM" % path)
    w, h = int(fields[1]), int(fields[2])
    return w, h, data[pos + 1:pos + 1 + w * h * 3]


class BitWriter:
    def __init__(self):
        self.out = bytearray()
        self.acc = 0
        self.n = 0

    def write(self, code, length):
        self.acc = (self.acc << length) | code
        self.n += length
        while self.n >= 8:
            self.n -= 8
            b = (self.acc >> self.n) & 0xFF
            self.out.append(b)
            if b == 0xFF:
                self.out.append(0)
        self.acc &= (1 << self.n) - 1

    def flush(self):
        if self.n:
            self.write((1 << (8 - self.n)) - 1, 8 - self.n)


COS = [[math.cos((2 * x + 1) * u * math.pi / 16) for x in range(8)] for u in range(8)]
C = [1 / math.sqrt(2)] + [1.0] * 7


def fdct(block):
    rows = [[sum(block[y * 8 + x] * COS[u][x] for x in range(8)) for u in range(8)] for y in range(8)]
    out = [0.0] * 64
    for v in range(8):
        for u in range(8):
            out[v * 8 + u] = 0.25 * C[u] * C[v] * sum(rows[y][u] * COS[v][y] for y in range(8))
    return out


def magnitude(v):
    n = abs(v).bit_length()
    return n, (v if v >= 0 else v + (1 << n) - 1)


def encode_block(bw, block, q, prev_dc, dc_codes, ac_codes):
    coef = fdct(block)
    zz = [int(round(coef[ZIGZAG[i]] / q[ZIGZAG[i]])) for i in range(64)]
    diff = zz[0] - prev_dc
    n, bits = magnitude(diff)
    bw.write(*dc_codes[n])
    if n:
        bw.write(bits, n)
    run = 0
    for i in range(1, 64):
        if zz[i] == 0:
            run += 1
            continue
        while run > 15:
            bw.write(*ac_codes[0xF0])
            run -= 16
        n, bits = magnitude(zz[i])
        bw.write(*ac_codes[(run << 4) | n])
        bw.write(bits, n)
        run = 0
    if run:
        bw.write(*ac_codes[0x00])
    return zz[0]


def encode(w, h, rgb, quality):
    lq, cq = scale_q(LUMA_Q, quality), scale_q(CHROMA_Q, quality)
    dc_l, ac_l = huff_codes(DC_LUMA_BITS, DC_LUMA_VALS), huff_codes(AC_LUMA_BITS, AC_LUMA_VALS)
    dc_c, ac_c = huff_codes(DC_CHROMA_BITS, DC_CHROMA_VALS), huff_codes(AC_CHROMA_BITS, AC_CHROMA_VALS)
    planes = ([0.0] * (w * h), [0.0] * (w * h), [0.0] * (w * h))
    for i in range(w * h):
        r, g, b = rgb[i * 3], rgb[i * 3 + 1], rgb[i * 3 + 2]
        planes[0][i] = 0.299 * r + 0.587 * g + 0.114 * b - 128
        planes[1][i] = -0.168736 * r - 0.331264 * g + 0.5 * b
        planes[2][i] = 0.5 * r - 0.418688 * g - 0.081312 * b
    bw = BitWriter()
    prev = [0, 0, 0]
    tables = ((lq, dc_l, ac_l), (cq, dc_c, ac_c), (cq, dc_c, ac_c))
    for by in range(0, h, 8):
        for bx in range(0, w, 8):
            for c in range(3):
                block = [planes[c][min(by + y, h - 1) * w + min(bx + x, w - 1)] for y in range(8) for x in range(8)]
                q, dc, ac = tables[c]
                prev[c] = encode_block(bw, block, q, prev[c], dc, ac)
    bw.flush()

    def seg(marker, payload):
        return struct.pack(">BBH", 0xFF, marker, len(payload) + 2) + payload

    out = bytearray(b"\xFF\xD8")
    out += seg(0xE0, b"JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00")
    out += seg(0xDB, bytes([0]) + bytes(lq[ZIGZAG[i]] for i in range(64)) +
               bytes([1]) + bytes(cq[ZIGZAG[i]] for i in range(64)))
    out += seg(0xC0, struct.pack(">BHHB", 8, h, w, 3) + bytes([1, 0x11, 0, 2, 0x11, 1, 3, 0x11, 1]))
    out += seg(0xC4, bytes([0x00]) + bytes(DC_LUMA_BITS) + bytes(DC_LUMA_VALS) +
               bytes([0x10]) + bytes(AC_LUMA_BITS) + AC_LUMA_VALS +
               bytes([0x01]) + bytes(DC_CHROMA_BITS) + bytes(DC_CHROMA_VALS) +
               bytes([0x11]) + bytes(AC_CHROMA_BITS) + AC_CHROMA_VALS)
    out += seg(0xDA, bytes([3, 1, 0x00, 2, 0x11, 3, 0x11, 0, 63, 0]))
    out += bw.out + b"\xFF\xD9"
    return bytes(out)


def main():
    if len(sys.argv) not in (3, 4):
        raise SystemExit(__doc__)
    w, h, rgb = read_ppm(sys.argv[1])
    quality = int(sys.argv[3]) if len(sys.argv) == 4 else 92
    with open(sys.argv[2], "wb") as f:
        f.write(encode(w, h, rgb, quality))


if __name__ == "__main__":
    main()
