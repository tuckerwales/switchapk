#!/usr/bin/env python3
"""
Builds framework-res.apk from the Android SDK's android.jar.

android.jar ships the complete compiled framework resource table
(resources.arsc: themes, styles, colors, dimensions, strings) plus the
res/ files it points at. switchapk loads it as package 0x01 so apps get the
genuine Material themes, widget styles and drawables.

The table is slimmed down for the Switch: only English/default strings are
kept, watch/car/TV/large-screen/ldpi variants are dropped, and the global
string pool is rebuilt with just the strings that are still referenced
(13 MB -> well under 1 MB).

usage: make_framework_res.py android.jar out.apk
"""
import struct
import sys
import zipfile

RES_STRING_POOL = 0x0001
RES_TABLE = 0x0002
RES_TABLE_PACKAGE = 0x0200
RES_TABLE_TYPE = 0x0201
TYPE_STRING = 0x03

NO_ENTRY = 0xFFFFFFFF


def read_pool(d, pos):
    _, hsize, size, count, style_count, flags, strings_start, styles_start = struct.unpack_from('<HHIIIIII', d, pos)
    offs = struct.unpack_from('<%dI' % count, d, pos + hsize)
    utf8 = bool(flags & 0x100)
    base = pos + strings_start
    raw = []
    for o in offs:
        p = base + o
        if utf8:
            # u16len (1-2 bytes) then u8len (1-2 bytes) then bytes + NUL
            n = d[p]
            p += 2 if n & 0x80 else 1
            n8 = d[p]
            if n8 & 0x80:
                n8 = ((n8 & 0x7F) << 8) | d[p + 1]
                p += 2
            else:
                p += 1
            raw.append(d[p:p + n8].decode('utf-8', 'surrogatepass'))
        else:
            n = struct.unpack_from('<H', d, p)[0]
            p += 2
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | struct.unpack_from('<H', d, p)[0]
                p += 2
            raw.append(d[p:p + n * 2].decode('utf-16-le', 'surrogatepass'))
    return raw


def write_pool_utf8(strings):
    data = bytearray()
    offs = []
    for s in strings:
        offs.append(len(data))
        b = s.encode('utf-8', 'surrogatepass')
        n16 = len(s.encode('utf-16-le', 'surrogatepass')) // 2
        for n in (n16, len(b)):
            if n > 0x7F:
                data += bytes([0x80 | (n >> 8), n & 0xFF])
            else:
                data += bytes([n])
        data += b + b'\0'
    while len(data) % 4:
        data += b'\0'
    hsize = 28
    strings_start = hsize + 4 * len(strings)
    size = strings_start + len(data)
    out = struct.pack('<HHIIIIII', RES_STRING_POOL, hsize, size, len(strings), 0, 0x100, strings_start, 0)
    out += struct.pack('<%dI' % len(strings), *offs) + bytes(data)
    return out


def keep_config(cfg):
    size = struct.unpack_from('<I', cfg, 0)[0]
    cfg = cfg + b'\0' * max(0, 64 - len(cfg))
    mcc, mnc = struct.unpack_from('<HH', cfg, 4)
    lang = cfg[8:10]
    country = cfg[10:12]
    density = struct.unpack_from('<H', cfg, 14)[0]
    ui_mode = cfg[33]
    sw_dp = struct.unpack_from('<H', cfg, 34)[0]
    screen_layout2 = cfg[52] if size > 52 else 0
    if mcc or mnc:
        return False
    if lang not in (b'\0\0', b'en'):
        return False
    if lang == b'en' and country not in (b'\0\0', b'US'):
        return False
    if density == 120:  # ldpi
        return False
    if (ui_mode & 0x0F) in (3, 4, 6, 7):  # car, television, watch, vr headset
        return False
    if sw_dp >= 600:
        return False
    if screen_layout2 & 0x03 == 0x02:  # round screens
        return False
    return True


def filter_type_chunk(d, pos, remap):
    """Returns a patched copy of a type chunk, remapping string values."""
    chunk = bytearray(d[pos:pos + struct.unpack_from('<I', d, pos + 4)[0]])
    hsize = struct.unpack_from('<H', chunk, 2)[0]
    flags = chunk[9]
    count, entries_start = struct.unpack_from('<II', chunk, 12)
    sparse = flags & 0x01
    off16 = flags & 0x02
    for i in range(count):
        if sparse:
            off = struct.unpack_from('<H', chunk, hsize + i * 4 + 2)[0] * 4
        elif off16:
            o = struct.unpack_from('<H', chunk, hsize + i * 2)[0]
            if o == 0xFFFF:
                continue
            off = o * 4
        else:
            off = struct.unpack_from('<I', chunk, hsize + i * 4)[0]
            if off == NO_ENTRY:
                continue
        e = entries_start + off
        esize, eflags = struct.unpack_from('<HH', chunk, e)
        if eflags & 0x0008:  # compact
            if (eflags >> 8) == TYPE_STRING:
                idx = struct.unpack_from('<I', chunk, e + 4)[0]
                struct.pack_into('<I', chunk, e + 4, remap(idx))
        elif eflags & 0x0001:  # complex (bag)
            n = struct.unpack_from('<I', chunk, e + 12)[0]
            m = e + esize
            for k in range(n):
                v = m + k * 12 + 4
                if chunk[v + 3] == TYPE_STRING:
                    idx = struct.unpack_from('<I', chunk, v + 4)[0]
                    struct.pack_into('<I', chunk, v + 4, remap(idx))
        else:
            v = e + esize
            if chunk[v + 3] == TYPE_STRING:
                idx = struct.unpack_from('<I', chunk, v + 4)[0]
                struct.pack_into('<I', chunk, v + 4, remap(idx))
    return bytes(chunk)


def build_arsc(d):
    t, hs, total, npk = struct.unpack_from('<HHII', d, 0)
    assert t == RES_TABLE
    pos = hs
    pool = None
    package = None
    while pos < len(d):
        ct, chs, csz = struct.unpack_from('<HHI', d, pos)
        if ct == RES_STRING_POOL and pool is None:
            pool = read_pool(d, pos)
        elif ct == RES_TABLE_PACKAGE and package is None:
            package = (pos, chs, csz)
        pos += csz
    ppos, phs, psz = package
    pkg_id = struct.unpack_from('<I', d, ppos + 8)[0]
    assert pkg_id == 1, 'expected the android package first'

    new_strings = []
    index = {}

    def remap(i):
        s = pool[i]
        j = index.get(s)
        if j is None:
            j = index[s] = len(new_strings)
            new_strings.append(s)
        return j

    body = bytearray()
    p = ppos + phs
    kept = dropped = 0
    while p < ppos + psz:
        ct, chs, csz = struct.unpack_from('<HHI', d, p)
        if ct == RES_TABLE_TYPE:
            cfg = d[p + 20:p + chs]
            if keep_config(cfg):
                body += filter_type_chunk(d, p, remap)
                kept += 1
            else:
                dropped += 1
        else:
            body += d[p:p + csz]
        p += csz
    header = bytearray(d[ppos:ppos + phs])
    struct.pack_into('<I', header, 4, phs + len(body))
    pkg = bytes(header) + bytes(body)
    sp = write_pool_utf8(new_strings)
    table = struct.pack('<HHII', RES_TABLE, 12, 12 + len(sp) + len(pkg), 1) + sp + pkg
    print('framework-res: %d type chunks kept, %d dropped, %d/%d strings, arsc %d bytes' %
          (kept, dropped, len(new_strings), len(pool), len(table)))
    return table


SKIP_DIR_PARTS = ('-ldpi', '-watch', '-car', '-television', '-sw600dp', '-sw720dp', '-round', '-xlarge', '-large',
                  '-mcc', '-land-ldpi')


def keep_file(name):
    if not name.startswith('res/'):
        return False
    folder = name.split('/')[1]
    parts = folder.split('-')[1:]
    for q in parts:
        if len(q) == 2 and q.isalpha() and q != 'en' and q not in ('v4',):
            return False  # other languages (res/xml-fr etc.)
    return not any(s in '-' + '-'.join(parts) for s in SKIP_DIR_PARTS)


def main():
    src, out = sys.argv[1], sys.argv[2]
    zin = zipfile.ZipFile(src)
    arsc = build_arsc(zin.read('resources.arsc'))
    n = 0
    with zipfile.ZipFile(out, 'w') as zout:
        zout.writestr(zipfile.ZipInfo('resources.arsc', (2008, 1, 1, 0, 0, 0)), arsc, zipfile.ZIP_DEFLATED)
        for info in zin.infolist():
            if info.is_dir() or not keep_file(info.filename):
                continue
            data = zin.read(info.filename)
            method = zipfile.ZIP_STORED if info.filename.endswith('.png') else zipfile.ZIP_DEFLATED
            zi = zipfile.ZipInfo(info.filename, (2008, 1, 1, 0, 0, 0))
            zout.writestr(zi, data, method)
            n += 1
    print('framework-res: %d files -> %s' % (n, out))


if __name__ == '__main__':
    main()
