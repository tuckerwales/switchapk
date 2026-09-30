#!/usr/bin/env python3
"""Tiny dex inspector: dumps the code units of methods matching Class.method."""
import struct, sys

def uleb(b, p):
    r = s = 0
    while True:
        x = b[p]; p += 1
        r |= (x & 0x7f) << s; s += 7
        if not x & 0x80: return r, p

def main(path, pattern):
    b = open(path, 'rb').read()
    u32 = lambda o: struct.unpack_from('<I', b, o)[0]
    u16 = lambda o: struct.unpack_from('<H', b, o)[0]
    sids_n, sids = u32(0x38), u32(0x3c)
    tids = u32(0x44)
    mids_n, mids = u32(0x58), u32(0x5c)
    cdefs_n, cdefs = u32(0x60), u32(0x64)
    def string(i):
        o = u32(sids + i * 4); _, o = uleb(b, o)
        e = b.index(0, o); return b[o:e].decode('utf-8', 'replace')
    def typ(i): return string(u32(tids + i * 4))
    cls_pat, _, m_pat = pattern.rpartition('.')
    for c in range(cdefs_n):
        cd = cdefs + c * 32
        cname = typ(u32(cd))
        if cls_pat and cls_pat not in cname: continue
        data = u32(cd + 24)
        if not data: continue
        p = data
        sf, p = uleb(b, p); inf, p = uleb(b, p); dm, p = uleb(b, p); vm, p = uleb(b, p)
        for _ in range(sf + inf): _, p = uleb(b, p); _, p = uleb(b, p)
        idx = 0
        for k in range(dm + vm):
            if k == dm: idx = 0
            d, p = uleb(b, p); idx += d
            _, p = uleb(b, p); code, p = uleb(b, p)
            name = string(u32(mids + idx * 8 + 4))
            if m_pat and name != m_pat: continue
            if not code: continue
            regs, ins, outs, tries = u16(code), u16(code + 2), u16(code + 4), u16(code + 6)
            n = u32(code + 12)
            print(f'{cname}.{name}: regs={regs} ins={ins} insns={n}')
            for i in range(n):
                w = u16(code + 16 + i * 2)
                print(f'  {i:04x}: {w:04x}  op={w & 0xff:02x}')

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
