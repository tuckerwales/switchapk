#!/usr/bin/env python3
"""Real-app corpus: fetch open-source APKs, find what switchapk lacks for them, rank the gaps.

Usage:
  tools/corpus.py fetch [id...]     download the pinned APKs of tests/corpus/corpus.json into build/corpus/apks
  tools/corpus.py scan [id...]      static analysis of each APK against build/java/framework.dex
  tools/corpus.py run [id...]       boot each APK headless with a smoke script, collect log signals
  tools/corpus.py report            aggregate scans and runs into docs/COMPATIBILITY.md
  tools/corpus.py all [id...]       fetch, scan, run, report
  tools/corpus.py scan --apk x.apk  scan any APK (prints the summary, writes nothing)

scan resolves every class, method and field the app's dex files reference (walking the app's own
class hierarchy into the framework, as the VM does) against framework.dex, and checks each miss
against android.jar to tell public SDK gaps from hidden or non-SDK references. A missing android.*
method is auto-stubbed by the VM (silent no-op); a missing java.* member throws NoSuchMethodError;
a missing class throws NoClassDefFoundError. Native libraries are checked for imports that neither
the APK's own libraries nor the shim (switchapk-host --shim-symbols) provide, and for ELF TLS.

run captures STUB lines, uncaught exceptions, unresolved native calls and screenshots. Static
results are an upper bound (all referenced code, guarded or not); run results are what the start
path really hits. Outputs go to build/corpus/{scan,run}/<id>.json; report writes the tables.
"""
import collections
import hashlib
import json
import os
import re
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CORPUS = os.path.join(ROOT, "tests/corpus/corpus.json")
OUT = os.path.join(ROOT, "build/corpus")
FRAMEWORK_DEX = os.path.join(ROOT, "build/java/framework.dex")
ANDROID_JAR = os.path.join(ROOT, "build/toolchains/sdk/android.jar")
AAPT2 = os.path.join(ROOT, "build/toolchains/sdk/aapt2")
HOST = os.path.join(ROOT, "build/host/switchapk-host")
REPORT = os.path.join(ROOT, "docs/COMPATIBILITY.md")
FDROID = "https://f-droid.org/%s/%s_%d.apk"

# Packages the VM auto-stubs (src/vm/class.c stub_allowed).
STUBBED = ("Landroid/", "Lcom/android/", "Ldalvik/")
# Packages that are platform API when an app references them; others are the app's own (optional) deps.
PLATFORM = STUBBED + ("Ljava/", "Ljavax/", "Lorg/json/", "Lorg/xml/", "Lorg/w3c/", "Lorg/xmlpull/",
                      "Lorg/apache/http/", "Ljunit/", "Lsun/misc/", "Llibcore/")

# Bundled libraries and frameworks recognised by class prefix.
LIBRARIES = [
    ("androidx.compose", "Landroidx/compose/"),
    ("androidx.appcompat", "Landroidx/appcompat/"),
    ("androidx.fragment", "Landroidx/fragment/"),
    ("androidx.recyclerview", "Landroidx/recyclerview/"),
    ("androidx.constraintlayout", "Landroidx/constraintlayout/"),
    ("androidx.core", "Landroidx/core/"),
    ("androidx (other)", "Landroidx/"),
    ("support library", "Landroid/support/"),
    ("material", "Lcom/google/android/material/"),
    ("play services", "Lcom/google/android/gms/"),
    ("firebase", "Lcom/google/firebase/"),
    ("kotlin", "Lkotlin/"),
    ("kotlinx.coroutines", "Lkotlinx/coroutines/"),
    ("libgdx", "Lcom/badlogic/gdx/"),
    ("arc", "Larc/"),
    ("sdl", "Lorg/libsdl/"),
    ("okhttp", "Lokhttp3/"),
    ("rxjava", "Lio/reactivex/"),
    ("gson", "Lcom/google/gson/"),
]

# Platform types whose mere reference marks a feature worth knowing about.
FEATURES = {
    "webview": ["Landroid/webkit/WebView;"],
    "https": ["Ljavax/net/ssl/HttpsURLConnection;", "Ljavax/net/ssl/SSLContext;", "Ljavax/net/ssl/SSLSocketFactory;"],
    "gles": ["Landroid/opengl/GLSurfaceView;", "Landroid/opengl/GLES20;", "Landroid/opengl/GLES30;"],
    "gles1": ["Ljavax/microedition/khronos/opengles/GL10;"],
    "native-activity": ["Landroid/app/NativeActivity;"],
    "camera": ["Landroid/hardware/Camera;", "Landroid/hardware/camera2/CameraManager;"],
    "media-player": ["Landroid/media/MediaPlayer;", "Landroid/media/SoundPool;", "Landroid/media/AudioTrack;"],
    "exoplayer-codecs": ["Landroid/media/MediaCodec;"],
    "dex-loading": ["Ldalvik/system/DexClassLoader;", "Ldalvik/system/InMemoryDexClassLoader;"],
    "reflection-hidden": ["Ldalvik/system/VMRuntime;"],
}


def die(msg):
    sys.stderr.write("corpus: %s\n" % msg)
    sys.exit(1)


def load_corpus():
    with open(CORPUS) as f:
        return json.load(f)["apps"]


def select(apps, ids):
    if not ids:
        return apps
    known = {a["id"]: a for a in apps}
    for i in ids:
        if i not in known:
            die("unknown corpus id %s (have: %s)" % (i, ", ".join(sorted(known))))
    return [known[i] for i in ids]


def apk_path(app):
    return os.path.join(OUT, "apks", "%s_%d.apk" % (app["package"], app["version_code"]))


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


# ---- fetch -----------------------------------------------------------------------------------------------

def cmd_fetch(apps):
    os.makedirs(os.path.join(OUT, "apks"), exist_ok=True)
    for app in apps:
        path = apk_path(app)
        if not os.path.exists(path):
            # F-Droid moves old versions from repo to archive once newer ones ship.
            repos = [app.get("repo", "repo")] + [r for r in ("repo", "archive") if r != app.get("repo", "repo")]
            for repo in repos:
                url = FDROID % (repo, app["package"], app["version_code"])
                print("fetch %s <- %s" % (app["id"], url))
                tmp = path + ".part"
                try:
                    with urllib.request.urlopen(url, timeout=600) as r, open(tmp, "wb") as f:
                        while True:
                            chunk = r.read(1 << 20)
                            if not chunk:
                                break
                            f.write(chunk)
                except urllib.error.HTTPError as e:
                    if e.code != 404:
                        raise
                    continue
                os.replace(tmp, path)
                break
            else:
                die("%s: version %d is on neither F-Droid repo" % (app["id"], app["version_code"]))
        digest = sha256(path)
        want = app.get("sha256")
        if want and want != digest:
            die("%s: sha256 %s does not match the pinned %s" % (app["id"], digest, want))
        if not want:
            print("%s: sha256 %s (not pinned yet)" % (app["id"], digest))


# ---- dex -------------------------------------------------------------------------------------------------

def uleb(b, p):
    r = s = 0
    while True:
        x = b[p]
        p += 1
        r |= (x & 0x7f) << s
        s += 7
        if not x & 0x80:
            return r, p


def _widths():
    w = [1] * 256
    for op, n in ((0x02, 2), (0x03, 3), (0x05, 2), (0x06, 3), (0x08, 2), (0x09, 3), (0x13, 2), (0x14, 3), (0x15, 2),
                  (0x16, 2), (0x17, 3), (0x18, 5), (0x19, 2), (0x1a, 2), (0x1b, 3), (0x1c, 2), (0x1f, 2), (0x20, 2),
                  (0x22, 2), (0x23, 2), (0x24, 3), (0x25, 3), (0x26, 3), (0x29, 2), (0x2a, 3), (0x2b, 3), (0x2c, 3),
                  (0xfa, 4), (0xfb, 4), (0xfc, 3), (0xfd, 3), (0xfe, 2), (0xff, 2)):
        w[op] = n
    for lo, hi, n in ((0x2d, 0x3d, 2), (0x44, 0x72, 2), (0x6e, 0x72, 3), (0x74, 0x78, 3), (0x90, 0xaf, 2),
                      (0xd0, 0xe2, 2)):
        for op in range(lo, hi + 1):
            w[op] = n
    return w


WIDTHS = _widths()
TYPE_OPS = {0x1c, 0x1f, 0x20, 0x22, 0x23, 0x24, 0x25}  # const-class, check-cast, instance-of, new-*, filled-new-array
SFIELD_OPS = set(range(0x60, 0x6e))
IFIELD_OPS = set(range(0x52, 0x60))
INVOKE_OPS = set(range(0x6e, 0x73)) | set(range(0x74, 0x79)) | {0xfa, 0xfb}


class Dex:
    """The parts of a dex file the scanner needs: class definitions and what the code references."""

    def __init__(self, data):
        b = self.b = data
        u32 = lambda o: struct.unpack_from("<I", b, o)[0]
        n_str, o_str = u32(0x38), u32(0x3c)
        n_typ, o_typ = u32(0x40), u32(0x44)
        n_pro, o_pro = u32(0x48), u32(0x4c)
        n_fld, o_fld = u32(0x50), u32(0x54)
        n_met, o_met = u32(0x58), u32(0x5c)
        n_cls, o_cls = u32(0x60), u32(0x64)
        offs = struct.unpack_from("<%dI" % n_str, b, o_str)
        strings = []
        for o in offs:
            _, o = uleb(b, o)
            strings.append(b[o:b.index(0, o)].decode("utf-8", "replace"))
        self.types = [strings[i] for i in struct.unpack_from("<%dI" % n_typ, b, o_typ)]
        self.protos = []
        for k in range(n_pro):
            _, ret, params = struct.unpack_from("<III", b, o_pro + k * 12)
            self.protos.append("(%s)%s" % ("".join(self.type_list(params)), self.types[ret]))
        self.fields = []
        for k in range(n_fld):
            c, t, n = struct.unpack_from("<HHI", b, o_fld + k * 8)
            self.fields.append((self.types[c], strings[n], self.types[t]))
        self.methods = []
        for k in range(n_met):
            c, p, n = struct.unpack_from("<HHI", b, o_met + k * 8)
            self.methods.append((self.types[c], strings[n], self.protos[p]))
        self.classes = {}
        # Indexes used by instructions (annotations also reference types and methods; those do not count).
        self.code_types, self.code_methods, self.code_sfields, self.code_ifields = set(), set(), set(), set()
        for k in range(n_cls):
            cidx, acc, sup, ifo, _, _, data, _ = struct.unpack_from("<8I", b, o_cls + k * 32)
            desc = self.types[cidx]
            info = {
                "super": self.types[sup] if sup != 0xffffffff else None,
                "ifaces": self.type_list(ifo),
                "methods": set(),
                "fields": {},
                "access": acc,
            }
            if data:
                p = data
                sf, p = uleb(b, p)
                inf, p = uleb(b, p)
                dm, p = uleb(b, p)
                vm, p = uleb(b, p)
                for group, static in ((sf, True), (inf, False)):
                    idx = 0
                    for _ in range(group):
                        d, p = uleb(b, p)
                        _, p = uleb(b, p)
                        idx += d
                        _, name, typ = self.fields[idx]
                        info["fields"][name + ":" + typ] = static
                for group in (dm, vm):
                    idx = 0
                    for _ in range(group):
                        d, p = uleb(b, p)
                        _, p = uleb(b, p)
                        code, p = uleb(b, p)
                        idx += d
                        _, name, proto = self.methods[idx]
                        info["methods"].add(name + proto)
                        if code:
                            self.scan_code(code)
            self.classes.setdefault(desc, info)

    def scan_code(self, off):
        b = self.b
        n = struct.unpack_from("<I", b, off + 12)[0]
        if not n:
            return
        ins = struct.unpack_from("<%dH" % n, b, off + 16)
        pc = 0
        while pc < n:
            u = ins[pc]
            op = u & 0xff
            if op == 0 and u:  # payloads: packed-switch, sparse-switch, fill-array-data
                if u == 0x100:
                    pc += 4 + ins[pc + 1] * 2
                elif u == 0x200:
                    pc += 2 + ins[pc + 1] * 4
                elif u == 0x300:
                    size = ins[pc + 2] | (ins[pc + 3] << 16)
                    pc += 4 + (size * ins[pc + 1] + 1) // 2
                else:
                    pc += 1
                continue
            if op in INVOKE_OPS:
                self.code_methods.add(ins[pc + 1])
            elif op in IFIELD_OPS:
                self.code_ifields.add(ins[pc + 1])
            elif op in SFIELD_OPS:
                self.code_sfields.add(ins[pc + 1])
            elif op in TYPE_OPS:
                self.code_types.add(ins[pc + 1])
            pc += WIDTHS[op]

    def type_list(self, off):
        if not off:
            return []
        n = struct.unpack_from("<I", self.b, off)[0]
        return [self.types[i] for i in struct.unpack_from("<%dH" % n, self.b, off + 4)]


# ---- class files (android.jar) ---------------------------------------------------------------------------

def parse_class(data):
    """Returns (descriptor, info) for a .class file, in the same shape as Dex.classes."""
    p = 10
    n = struct.unpack_from(">H", data, 8)[0]
    utf8 = {}
    cls = {}
    i = 1
    while i < n:
        tag = data[p]
        if tag == 1:
            ln = struct.unpack_from(">H", data, p + 1)[0]
            utf8[i] = data[p + 3:p + 3 + ln].decode("utf-8", "replace")
            p += 3 + ln
        elif tag == 7:
            cls[i] = struct.unpack_from(">H", data, p + 1)[0]
            p += 3
        elif tag in (8, 16, 19, 20):
            p += 3
        elif tag == 15:
            p += 4
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            p += 5
        elif tag in (5, 6):
            p += 9
            i += 1
        else:
            raise ValueError("bad constant pool tag %d" % tag)
        i += 1

    def cname(idx):
        return "L" + utf8[cls[idx]] + ";" if idx else None

    acc, this, sup = struct.unpack_from(">HHH", data, p)
    p += 6
    ni = struct.unpack_from(">H", data, p)[0]
    ifaces = [cname(x) for x in struct.unpack_from(">%dH" % ni, data, p + 2)]
    p += 2 + 2 * ni
    info = {"super": cname(sup), "ifaces": ifaces, "methods": set(), "fields": {}, "access": acc}
    for kind in ("fields", "methods"):
        cnt = struct.unpack_from(">H", data, p)[0]
        p += 2
        for _ in range(cnt):
            macc, name, desc, nattr = struct.unpack_from(">HHHH", data, p)
            p += 8
            for _ in range(nattr):
                p += 6 + struct.unpack_from(">I", data, p + 2)[0]
            if macc & 0x1000:  # synthetic
                continue
            if kind == "fields":
                info["fields"][utf8[name] + ":" + utf8[desc]] = bool(macc & 0x8)
            else:
                info["methods"].add(utf8[name] + utf8[desc])
    return cname(this), info


def load_sdk():
    cache = os.path.join(OUT, "sdk-index.json")
    if os.path.exists(cache) and os.path.getmtime(cache) > os.path.getmtime(ANDROID_JAR):
        with open(cache) as f:
            raw = json.load(f)
        for v in raw.values():
            v["methods"] = set(v["methods"])
        return raw
    sdk = {}
    with zipfile.ZipFile(ANDROID_JAR) as z:
        for n in z.namelist():
            if n.endswith(".class"):
                desc, info = parse_class(z.read(n))
                sdk[desc] = info
    os.makedirs(OUT, exist_ok=True)
    with open(cache, "w") as f:
        json.dump({k: dict(v, methods=sorted(v["methods"])) for k, v in sdk.items()}, f)
    return sdk


# ---- hierarchy resolution --------------------------------------------------------------------------------

class World:
    """Boot classes first, then app classes (parent-first, like the VM's class loading)."""

    def __init__(self, boot, app):
        self.boot = boot
        self.app = app

    def get(self, desc):
        info = self.boot.get(desc)
        if info is not None:
            return info, True
        info = self.app.get(desc)
        if info is not None:
            return info, False
        return None, False

    def resolve(self, desc, member, kind):
        """Walks desc's supers and interfaces. Returns (found, first_boot_class, missing_ancestor)."""
        seen = set()
        todo = [desc]
        first_boot = None
        missing = None
        while todo:
            c = todo.pop(0)
            if c is None or c in seen:
                continue
            seen.add(c)
            info, is_boot = self.get(c)
            if info is None:
                missing = missing or c
                continue
            if is_boot and first_boot is None:
                first_boot = c
            if (member in info["methods"]) if kind == "m" else (member in info["fields"]):
                return True, first_boot, None
            todo.append(info["super"])
            todo.extend(info["ifaces"])
        return False, first_boot, missing


def java_name(desc):
    if desc.startswith("L") and desc.endswith(";"):
        return desc[1:-1].replace("/", ".")
    return desc


# ---- native libraries ------------------------------------------------------------------------------------

def elf_info(data):
    """Dynamic imports, exports, DT_NEEDED and PT_TLS of an ELF64 little-endian shared object."""
    if data[:4] != b"\x7fELF" or data[4] != 2:
        return None
    e_phoff, e_shoff = struct.unpack_from("<QQ", data, 0x20)
    e_phentsize, e_phnum, e_shentsize, e_shnum = struct.unpack_from("<HHHH", data, 0x36)
    tls = False
    for k in range(e_phnum):
        if struct.unpack_from("<I", data, e_phoff + k * e_phentsize)[0] == 7:
            tls = True
    secs = []
    for k in range(e_shnum):
        o = e_shoff + k * e_shentsize
        _, typ, _, _, off, size, link, _, _, entsize = struct.unpack_from("<IIQQQQIIQQ", data, o)
        secs.append((typ, off, size, link, entsize))

    def cstr(off):
        return data[off:data.index(0, off)].decode("utf-8", "replace")

    imports, exports, needed = set(), set(), []
    for typ, off, size, link, entsize in secs:
        if typ == 11:  # SHT_DYNSYM
            stroff = secs[link][1]
            for k in range(1, size // 24):
                name, info, _, shndx = struct.unpack_from("<IBBH", data, off + k * 24)
                if not name:
                    continue
                bind = info >> 4
                nm = cstr(stroff + name)
                if shndx == 0:
                    if bind == 1:  # weak undefined imports become 0, as in the loader
                        imports.add(nm)
                elif bind in (1, 2, 10):
                    exports.add(nm)
        elif typ == 6:  # SHT_DYNAMIC
            stroff = secs[link][1]
            for k in range(size // 16):
                tag, val = struct.unpack_from("<qQ", data, off + k * 16)
                if tag == 1:
                    needed.append(cstr(stroff + val))
                elif tag == 0:
                    break
    return {"imports": imports, "exports": exports, "needed": needed, "tls": tls}


_shim = None


def shim_symbols():
    global _shim
    if _shim is None:
        if not os.path.exists(HOST):
            die("%s is missing: run make first" % HOST)
        out = subprocess.run([HOST, "--shim-symbols"], stdout=subprocess.PIPE, text=True, check=True).stdout
        _shim = set(out.split())
    return _shim


def scan_natives(z):
    by_abi = collections.defaultdict(dict)
    for n in z.namelist():
        m = re.match(r"lib/([^/]+)/([^/]+\.so)$", n)
        if m:
            by_abi[m.group(1)][m.group(2)] = n
    result = {"abis": sorted(by_abi), "libs": {}}
    # The Switch and the AArch64 host check arm64-v8a; the x86-64 host runs x86_64.
    for abi in ("arm64-v8a", "x86_64"):
        libs = by_abi.get(abi)
        if not libs:
            continue
        infos = {}
        for so, path in libs.items():
            info = elf_info(z.read(path))
            if info:
                infos[so] = info
        provided = set()
        for info in infos.values():
            provided |= info["exports"]
        shim = shim_symbols()
        for so, info in sorted(infos.items()):
            missing = sorted(s for s in info["imports"] - provided - shim
                             if not s.startswith(("gl", "egl")))
            result["libs"].setdefault(so, {})[abi] = {
                "needed": info["needed"], "tls": info["tls"], "missing": missing}
    return result


# ---- manifest --------------------------------------------------------------------------------------------

def manifest(path):
    info = {"activities": 0, "processes": [], "uses_library": [], "features": []}
    if not os.path.exists(AAPT2):
        return info
    out = subprocess.run([AAPT2, "dump", "badging", path], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                         text=True).stdout
    for line in out.splitlines():
        if line.startswith("package:"):
            info["version_name"] = (re.search(r"versionName='([^']*)'", line) or [None, None])[1]
        elif line.startswith(("sdkVersion:", "minSdkVersion:")):
            info["min_sdk"] = int(line.split("'")[1])
        elif line.startswith("targetSdkVersion:"):
            info["target_sdk"] = int(line.split("'")[1])
        elif line.startswith("application-label:"):
            info["label"] = line.split("'")[1]
        elif line.startswith("launchable-activity:"):
            info["launcher"] = (re.search(r"name='([^']*)'", line) or [None, None])[1]
        elif line.startswith("uses-library") or line.startswith("uses-library-not-required"):
            info["uses_library"].append(line.split("'")[1])
        elif line.startswith("uses-feature:") and "required" not in line:
            info["features"].append(line.split("'")[1])
    tree = subprocess.run([AAPT2, "dump", "xmltree", "--file", "AndroidManifest.xml", path],
                          stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True).stdout
    info["activities"] = len(re.findall(r"E: activity(?:-alias)? ", tree))
    info["processes"] = sorted(set(re.findall(r'android:process\(0x[0-9a-f]+\)="([^"]+)"', tree)))
    return info


# ---- scan ------------------------------------------------------------------------------------------------

def scan_apk(path, boot, sdk):
    with zipfile.ZipFile(path) as z:
        dex_names = sorted((n for n in z.namelist() if re.match(r"classes\d*\.dex$", n)),
                           key=lambda n: int(re.sub(r"\D", "", n) or 1))
        dexes = [Dex(z.read(n)) for n in dex_names]
        natives = scan_natives(z)
    app = {}
    for d in dexes:
        for k, v in d.classes.items():
            app.setdefault(k, v)
    world = World(boot, app)
    sdk_world = World(sdk, {})

    # Types the code can load: instruction operands, the owners and signatures of members it uses,
    # and the supertypes of app classes.
    referenced_types = set()
    for d in dexes:
        referenced_types.update(d.types[i].lstrip("[") for i in d.code_types)
        for i in d.code_methods:
            cls, _, proto = d.methods[i]
            referenced_types.add(cls.lstrip("["))
            referenced_types.update(re.findall(r"L[^;]+;", proto))
        for i in d.code_sfields | d.code_ifields:
            cls, _, typ = d.fields[i]
            referenced_types.add(cls.lstrip("["))
            referenced_types.add(typ.lstrip("["))
    for info in app.values():
        referenced_types.add(info["super"])
        referenced_types.update(info["ifaces"])
    referenced_types.discard(None)

    missing_classes = {}
    for t in sorted(referenced_types):
        if not t.startswith("L") or t in boot or t in app:
            continue
        if t.startswith(PLATFORM):
            missing_classes[t] = t in sdk
    libraries = collections.Counter()
    for c in app:
        for name, prefix in LIBRARIES:
            if c.startswith(prefix):
                libraries[name] += 1
                break
    features = sorted(f for f, types in FEATURES.items() if any(t in referenced_types for t in types))

    gaps = {}  # key -> (kind, severity, in_sdk)

    def note(owner, member, kind, static):
        if not owner.startswith(PLATFORM):
            return
        found_sdk, _, _ = sdk_world.resolve(owner, member, kind)
        # The VM stubs missing android.* methods and static fields; instance fields and java.* throw.
        sev = "stub" if owner.startswith(STUBBED) and (kind == "m" or static) else "throws"
        key = "%s.%s" % (java_name(owner), member)
        gaps[key] = {"owner": java_name(owner), "member": member, "kind": "method" if kind == "m" else "field",
                     "severity": sev, "sdk": found_sdk}

    for d in dexes:
        for cls, name, proto in (d.methods[i] for i in sorted(d.code_methods)):
            if not cls.startswith("L") or name == "<clinit>":
                continue
            found, first_boot, miss = world.resolve(cls, name + proto, "m")
            if found or first_boot is None:
                continue
            if miss and miss.startswith(PLATFORM):
                continue  # counted as a missing class
            # MethodHandle.invoke and friends are signature polymorphic.
            if first_boot in ("Ljava/lang/invoke/MethodHandle;", "Ljava/lang/invoke/VarHandle;"):
                continue
            owner = cls if cls in boot else first_boot
            note(owner, name + proto, "m", False)
        for i in sorted(d.code_sfields | d.code_ifields):
            cls, name, typ = d.fields[i]
            if not cls.startswith("L"):
                continue
            found, first_boot, miss = world.resolve(cls, name + ":" + typ, "f")
            if found or first_boot is None or (miss and miss.startswith(PLATFORM)):
                continue
            owner = cls if cls in boot else first_boot
            note(owner, name + ":" + typ, "f", i in d.code_sfields)

    return {
        "apk": os.path.basename(path),
        "size": os.path.getsize(path),
        "manifest": manifest(path),
        "dex_files": len(dexes),
        "app_classes": len(app),
        "libraries": dict(libraries.most_common()),
        "features": features,
        "missing_classes": [{"class": java_name(c), "sdk": s} for c, s in sorted(missing_classes.items())],
        "missing_members": [gaps[k] for k in sorted(gaps)],
        "natives": natives,
    }


def blockers(scan):
    """Static reasons an app cannot work yet, most serious first."""
    out = []
    nat = scan["natives"]
    if nat["abis"] and "arm64-v8a" not in nat["abis"]:
        out.append("no arm64-v8a libraries (%s)" % ", ".join(nat["abis"]))
    for so, per in sorted(nat["libs"].items()):
        a = per.get("arm64-v8a")
        if a and a["tls"]:
            out.append("%s uses ELF TLS" % so)
    if scan["manifest"].get("processes"):
        out.append("multi-process (%s)" % ", ".join(scan["manifest"]["processes"]))
    if "play services" in scan["libraries"]:
        out.append("bundles Play Services")
    if "https" in scan["features"]:
        out.append("references TLS (javax.net.ssl)")
    if "webview" in scan["features"]:
        out.append("references WebView")
    sdk_classes = [c["class"] for c in scan["missing_classes"] if c["sdk"]]
    if sdk_classes:
        out.append("%d missing SDK classes" % len(sdk_classes))
    throws = [m for m in scan["missing_members"] if m["severity"] == "throws" and m["sdk"]]
    if throws:
        out.append("%d missing java.* members (NoSuchMethodError)" % len(throws))
    return out


def summarize_scan(app_id, s):
    print("== %s (%s)" % (app_id, s["apk"]))
    m = s["manifest"]
    print("   sdk %s/%s, %d dex, %d classes, %d activities" % (
        m.get("min_sdk"), m.get("target_sdk"), s["dex_files"], s["app_classes"], m.get("activities", 0)))
    print("   libraries: %s" % (", ".join("%s(%d)" % kv for kv in s["libraries"].items()) or "none"))
    print("   features: %s" % (", ".join(s["features"]) or "none"))
    mc = s["missing_classes"]
    mm = s["missing_members"]
    print("   missing: %d SDK classes, %d non-SDK classes; members: %d stubbed, %d throwing (SDK only)" % (
        sum(c["sdk"] for c in mc), sum(not c["sdk"] for c in mc),
        sum(x["severity"] == "stub" and x["sdk"] for x in mm),
        sum(x["severity"] == "throws" and x["sdk"] for x in mm)))
    nat = s["natives"]
    if nat["abis"]:
        print("   natives: %s" % ", ".join(nat["abis"]))
        for so, per in sorted(nat["libs"].items()):
            a = per.get("arm64-v8a") or per.get("x86_64")
            if a and (a["missing"] or a["tls"]):
                print("     %s: %d unresolved imports%s" % (so, len(a["missing"]), ", TLS" if a["tls"] else ""))
    for b in blockers(s):
        print("   ! " + b)


def load_boot():
    if not os.path.exists(FRAMEWORK_DEX):
        die("%s is missing: run make first" % FRAMEWORK_DEX)
    with open(FRAMEWORK_DEX, "rb") as f:
        return Dex(f.read()).classes


def cmd_scan(apps, apk=None):
    if not os.path.exists(ANDROID_JAR):
        die("android.jar is missing: python3 tools/fetch_toolchains.py sdk")
    boot = load_boot()
    sdk = load_sdk()
    if apk:
        summarize_scan(os.path.basename(apk), scan_apk(apk, boot, sdk))
        return
    os.makedirs(os.path.join(OUT, "scan"), exist_ok=True)
    for app in apps:
        path = apk_path(app)
        if not os.path.exists(path):
            print("skip %s: not fetched" % app["id"])
            continue
        s = scan_apk(path, boot, sdk)
        with open(os.path.join(OUT, "scan", app["id"] + ".json"), "w") as f:
            json.dump(s, f, indent=1)
        summarize_scan(app["id"], s)


# ---- run -------------------------------------------------------------------------------------------------

SMOKE_SCRIPT = """\
# corpus smoke run: let the app start, poke it, screenshot each stage.
wait {start}
screenshot 1-start.png
tap 640 360
wait 3000
screenshot 2-tap.png
key DPAD_CENTER
wait 2000
key BACK
wait 2000
screenshot 3-back.png
quit
"""

STUB_RE = re.compile(r"STUB: missing framework (method|field) (.*?)(?: \(returns default\)| \(.*\))?$")
NATIVE_CALL_RE = re.compile(r"native code called (\S+), which no library provides")
NATIVE_LOAD_RE = re.compile(r"linker: (\S+): unresolved (?:function|object|symbol) (\S+)")
LOG_PREFIX_RE = re.compile(r"^[VDIWEF]/[^:]*:\s?")
EXC_RE = re.compile(r"^(Caused by: )?((?:[a-z][\w$]*\.)+[A-Z][\w$]*(?:Exception|Error))(?::\s*(.*))?$")
NOTE_RES = (re.compile(r"Toast: show: (.*)"), re.compile(r"(No activity for Intent .*)"),
            re.compile(r"(OpenGL ES is unavailable)"), re.compile(r"(GL thread failed)"))


def classify_log(text):
    """Pulls the gap signals out of a run log."""
    stubs = collections.Counter()
    called, load = set(), set()
    chains = []  # [[top, cause, ..., root]] per thrown exception
    notes = []
    for raw in text.splitlines():
        m = STUB_RE.search(raw)
        if m:
            stubs[m.group(1) + " " + m.group(2)] += 1
            continue
        m = NATIVE_CALL_RE.search(raw)
        if m:
            called.add(m.group(1))
            continue
        m = NATIVE_LOAD_RE.search(raw)
        if m:
            load.add(m.group(2))
            continue
        for r in NOTE_RES:
            m = r.search(raw)
            if m and m.group(1)[:200] not in notes:
                notes.append(m.group(1)[:200])
        line = LOG_PREFIX_RE.sub("", raw).strip()
        line = re.sub(r"^Exception in thread \"[^\"]*\"\s*", "", line)
        m = EXC_RE.match(line)
        if m:
            entry = m.group(2) + (": " + m.group(3)[:160] if m.group(3) else "")
            if m.group(1) and chains:
                chains[-1].append(entry)
            else:
                chains.append([entry])
    uniq = collections.Counter(" <- ".join(c) for c in chains)
    return stubs, called, load, uniq, notes


def screenshot_stats(path):
    sys.path.insert(0, os.path.join(ROOT, "tests/apps"))
    import shotlib
    try:
        w, h, px = shotlib.read_png(path)
    except SystemExit:
        return None
    colors = set()
    step = max(1, (w * h) // 4096)
    for i in range(0, w * h, step):
        colors.add(bytes(px[i * 4:i * 4 + 3]))
    return {"colors": len(colors), "size": [w, h]}


def cmd_run(apps, timeout, start_ms):
    if not os.path.exists(HOST):
        die("%s is missing: run make first" % HOST)
    os.makedirs(os.path.join(OUT, "run"), exist_ok=True)
    for app in apps:
        path = apk_path(app)
        if not os.path.exists(path):
            print("skip %s: not fetched" % app["id"])
            continue
        work = os.path.join(OUT, "work", app["id"])
        shots = os.path.join(work, "shots")
        data = os.path.join(work, "data")
        os.makedirs(shots, exist_ok=True)
        for d in (shots,):
            for n in os.listdir(d):
                os.unlink(os.path.join(d, n))
        script = os.path.join(work, "smoke.script")
        with open(script, "w") as f:
            f.write(SMOKE_SCRIPT.format(start=start_ms))
        cmd = [HOST, "--data", data, "--screen", "1280x720@240", "--script", script, "--screenshots", shots, path]
        t0 = time.time()
        try:
            p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout, cwd=ROOT)
            rc, log, timed_out = p.returncode, p.stdout, False
        except subprocess.TimeoutExpired as e:
            rc, log, timed_out = None, e.stdout or b"", True
        log = log.decode("utf-8", "replace")
        with open(os.path.join(work, "log.txt"), "w") as f:
            f.write(log)
        stubs, called, load, exceptions, notes = classify_log(log)
        shots_info = {}
        for n in sorted(os.listdir(shots)):
            if n.endswith(".png"):
                shots_info[n] = screenshot_stats(os.path.join(shots, n))
        root = None
        if exceptions:
            first = next(iter(exceptions))
            root = first.split(" <- ")[-1]
        if timed_out:
            outcome = "hang"
        elif rc and rc < 0:
            outcome = "crash (signal %d)" % -rc
        elif not shots_info:
            outcome = "fails at start: %s" % root if rc and root else "exits at start (rc %s)" % rc
        elif any(n in ("OpenGL ES is unavailable", "GL thread failed") for n in notes):
            outcome = "GL failed"
        elif all(s and s["colors"] <= 2 for s in shots_info.values()):
            outcome = "blank screen"
        else:
            outcome = "draws"
        r = {
            "outcome": outcome,
            "rc": rc,
            "seconds": round(time.time() - t0, 1),
            "stubs": dict(stubs.most_common()),
            "native_called": sorted(called),
            "native_unresolved_at_load": sorted(load),
            "exceptions": dict(exceptions.most_common()),
            "notes": notes,
            "screenshots": shots_info,
            "log_tail": log.splitlines()[-40:],
        }
        with open(os.path.join(OUT, "run", app["id"] + ".json"), "w") as f:
            json.dump(r, f, indent=1)
        print("== %s: %s in %.0fs; %d stubs hit, %d exceptions, %d stubbed native calls" % (
            app["id"], outcome, r["seconds"], len(stubs), len(exceptions), len(called)))
        for e, n in list(exceptions.most_common())[:5]:
            print("   %dx %s" % (n, e))
        for n in notes[:5]:
            print("   note: %s" % n)


# ---- report ----------------------------------------------------------------------------------------------

BEGIN = "<!-- corpus:begin (generated by tools/corpus.py report; edit outside these markers) -->"
END = "<!-- corpus:end -->"


def load_json(kind, app_id):
    p = os.path.join(OUT, kind, app_id + ".json")
    if not os.path.exists(p):
        return None
    with open(p) as f:
        return json.load(f)


def md_escape(s):
    return s.replace("|", "\\|").replace("<", "&lt;").replace(">", "&gt;")


def short_member(m):
    member = m["member"]
    if m["kind"] == "method":
        name, _, rest = member.partition("(")
        params, _, _ = rest.partition(")")
        return "%s.%s(%s)" % (m["owner"], name, dex_params_java(params))
    name, _, typ = member.partition(":")
    return "%s.%s" % (m["owner"], name)


def dex_params_java(params):
    out = []
    i = 0
    prim = {"Z": "boolean", "B": "byte", "C": "char", "S": "short", "I": "int", "J": "long", "F": "float", "D": "double"}
    while i < len(params):
        dims = 0
        while params[i] == "[":
            dims += 1
            i += 1
        if params[i] == "L":
            j = params.index(";", i)
            t = params[i + 1:j].rsplit("/", 1)[-1]
            i = j + 1
        else:
            t = prim.get(params[i], params[i])
            i += 1
        out.append(t + "[]" * dims)
    return ", ".join(out)


def native_cell(nat):
    if not nat["abis"]:
        return "none"
    if "arm64-v8a" not in nat["abis"]:
        return "no arm64 (%s)" % ", ".join(nat["abis"])
    missing = set()
    for per in nat["libs"].values():
        missing.update((per.get("arm64-v8a") or {}).get("missing", []))
    return "arm64, %d libs, %d unresolved" % (len(nat["libs"]), len(missing))


def short_outcome(o):
    o = re.sub(r"\b(?:java\.lang|java\.util\.regex|com\.badlogic\.gdx\.utils)\.", "", o)
    return o if len(o) <= 110 else o[:107] + "..."


def cmd_report(apps, top):
    rows = []
    class_gaps = collections.defaultdict(set)
    member_gaps = collections.defaultdict(set)
    package_gaps = collections.defaultdict(set)
    run_stubs = collections.defaultdict(set)
    run_exc = collections.defaultdict(set)
    native_gaps = collections.defaultdict(set)
    run_native = collections.defaultdict(set)
    for app in apps:
        s = load_json("scan", app["id"])
        r = load_json("run", app["id"])
        if not s:
            continue
        m = s["manifest"]
        # A handful of classes (annotations, one shim) is not a bundled library.
        libs = ", ".join(k for k, n in s["libraries"].items() if k != "kotlin" and n >= 5) or "framework only"
        if "kotlin" in s["libraries"]:
            libs += " (Kotlin)"
        stub_n = sum(x["severity"] == "stub" and x["sdk"] for x in s["missing_members"])
        throw_n = sum(x["severity"] == "throws" and x["sdk"] for x in s["missing_members"])
        cls_n = sum(c["sdk"] for c in s["missing_classes"])
        rows.append("| %s | %d | %s/%s | %s | %d | %d | %d | %s | %s |" % (
            app["name"], app["tier"], m.get("min_sdk", "?"), m.get("target_sdk", "?"), libs, cls_n, throw_n,
            stub_n, native_cell(s["natives"]), md_escape(short_outcome(r["outcome"])) if r else "not run"))
        for c in s["missing_classes"]:
            if c["sdk"]:
                class_gaps[c["class"]].add(app["id"])
                package_gaps[c["class"].rsplit(".", 1)[0]].add(app["id"])
        for x in s["missing_members"]:
            if x["sdk"]:
                member_gaps[(x["severity"], short_member(x))].add(app["id"])
                package_gaps[x["owner"].rsplit(".", 1)[0]].add(app["id"])
        for so, per in s["natives"]["libs"].items():
            a = per.get("arm64-v8a") or per.get("x86_64") or {}
            for sym in a.get("missing", []):
                native_gaps[sym].add(app["id"])
        if r:
            for k in r["stubs"]:
                run_stubs[k].add(app["id"])
            for k in r["exceptions"]:
                run_exc[k.split(" <- ")[-1]].add(app["id"])
            for k in r["native_called"]:
                run_native[k].add(app["id"])

    def ranked(d, n):
        items = sorted(d.items(), key=lambda kv: (-len(kv[1]), str(kv[0])))
        return items[:n]

    out = [BEGIN, ""]
    out.append("Generated %s from %d apps. Static counts include only members and classes that android.jar "
               "has (public SDK); hidden-API references are in build/corpus/scan/*.json. Host run is the smoke "
               "script (start, tap, D-pad centre, back) on the x86-64 host with Mesa; native counts are for "
               "arm64-v8a." % (time.strftime("%Y-%m-%d"), len(rows)))
    out.append("")
    out.append("| App | Tier | min/target SDK | Bundled libraries | Missing classes | Missing java.* members "
               "(throw) | Missing android.* members (stubbed) | Native ABIs | Host run |")
    out.append("|---|---|---|---|---|---|---|---|---|")
    out.extend(rows)
    out.append("")
    out.append("### Most-needed packages (static, by number of apps)")
    out.append("")
    out.append("| Package | Apps |")
    out.append("|---|---|")
    for pkg, ids in ranked(package_gaps, top):
        out.append("| %s | %d (%s) |" % (pkg, len(ids), ", ".join(sorted(ids))))
    out.append("")
    out.append("### Most-needed classes (static)")
    out.append("")
    out.append("| Class | Apps |")
    out.append("|---|---|")
    for c, ids in ranked(class_gaps, top):
        out.append("| %s | %d (%s) |" % (c, len(ids), ", ".join(sorted(ids))))
    out.append("")
    out.append("### Most-needed members (static)")
    out.append("")
    out.append("`throws`: java.* member, NoSuchMethodError at the call. `stub`: android.* member, the VM "
               "auto-stubs it and the call does nothing.")
    out.append("")
    out.append("| Member | Effect | Apps |")
    out.append("|---|---|---|")
    for (sev, mem), ids in ranked(member_gaps, top):
        out.append("| %s | %s | %d (%s) |" % (md_escape(mem), sev, len(ids), ", ".join(sorted(ids))))
    out.append("")
    if native_gaps:
        out.append("### Unresolved native imports (static)")
        out.append("")
        out.append("| Symbol | Apps |")
        out.append("|---|---|")
        for sym, ids in ranked(native_gaps, top):
            out.append("| %s | %d (%s) |" % (md_escape(sym), len(ids), ", ".join(sorted(ids))))
        out.append("")
    if run_stubs or run_exc:
        out.append("### Hit at runtime (smoke run)")
        out.append("")
        out.append("| Signal | Apps |")
        out.append("|---|---|")
        for k, ids in ranked(run_exc, top):
            out.append("| exception %s | %d (%s) |" % (md_escape(k), len(ids), ", ".join(sorted(ids))))
        for k, ids in ranked(run_native, top):
            out.append("| native call %s | %d (%s) |" % (md_escape(k), len(ids), ", ".join(sorted(ids))))
        for k, ids in ranked(run_stubs, top):
            out.append("| stub %s | %d (%s) |" % (md_escape(k), len(ids), ", ".join(sorted(ids))))
        out.append("")
    out.append(END)
    block = "\n".join(out)

    if os.path.exists(REPORT):
        with open(REPORT) as f:
            text = f.read()
        if BEGIN in text and END in text:
            text = text[:text.index(BEGIN)] + block + text[text.index(END) + len(END):]
        else:
            text = text.rstrip() + "\n\n" + block + "\n"
    else:
        text = "# Compatibility\n\n" + block + "\n"
    with open(REPORT, "w") as f:
        f.write(text)
    print("wrote %s (%d apps)" % (os.path.relpath(REPORT, ROOT), len(rows)))


def main(argv):
    if len(argv) < 2 or argv[1] in ("-h", "--help"):
        print(__doc__)
        return
    cmd, rest = argv[1], argv[2:]
    opts = {"--timeout": "120", "--start": "8000", "--top": "40", "--apk": None}
    ids = []
    i = 0
    while i < len(rest):
        if rest[i] in opts and i + 1 < len(rest):
            opts[rest[i]] = rest[i + 1]
            i += 2
        else:
            ids.append(rest[i])
            i += 1
    apps = select(load_corpus(), ids)
    if cmd == "fetch":
        cmd_fetch(apps)
    elif cmd == "scan":
        cmd_scan(apps, opts["--apk"])
    elif cmd == "run":
        cmd_run(apps, int(opts["--timeout"]), int(opts["--start"]))
    elif cmd == "report":
        cmd_report(load_corpus(), int(opts["--top"]))
    elif cmd == "all":
        cmd_fetch(apps)
        cmd_scan(apps)
        cmd_run(apps, int(opts["--timeout"]), int(opts["--start"]))
        cmd_report(load_corpus(), int(opts["--top"]))
    else:
        die("unknown command %s" % cmd)


if __name__ == "__main__":
    main(sys.argv)
