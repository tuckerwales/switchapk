#!/usr/bin/env python3
"""
Fetches the external toolchains and SDK pieces switchapk needs into
build/toolchains (gitignored). Safe to re-run: finished steps are skipped.

  tools/fetch_toolchains.py sdk        # aapt2 + android.jar (+ framework-res.apk)
  tools/fetch_toolchains.py devkitpro  # devkitA64 + libnx + switch portlibs (no docker needed)
  tools/fetch_toolchains.py sqlite     # SQLite amalgamation -> third_party/sqlite (gitignored)
  tools/fetch_toolchains.py all

Layout produced:
  build/toolchains/sdk/aapt2
  build/toolchains/sdk/android.jar
  build/toolchains/framework-res.apk
  build/toolchains/devkitpro/opt/devkitpro/{devkitA64,libnx,portlibs,tools}
  third_party/sqlite/sqlite3.{c,h}

Environment variables for the Switch build: DEVKITPRO=build/toolchains/devkitpro/opt/devkitpro
"""
import io
import json
import os
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "build", "toolchains")

AAPT2_VERSION = "8.13.2-14304508"
AAPT2_URL = "https://dl.google.com/android/maven2/com/android/tools/build/aapt2/{v}/aapt2-{v}-linux.jar".format(v=AAPT2_VERSION)
PLATFORM_URL = "https://dl.google.com/android/repository/platform-35_r02.zip"
SQLITE_URL = "https://www.sqlite.org/2024/sqlite-amalgamation-3460100.zip"
DKP_IMAGE = "devkitpro/devkita64"
DKP_TAG = "latest"


def log(msg):
    print("[fetch] " + msg, flush=True)


def fetch(url, headers=None, attempts=7):
    """GET with retries: Docker Hub rate-limits anonymous pulls (HTTP 429)."""
    delay = 5
    for i in range(attempts):
        req = urllib.request.Request(url, headers=headers or {})
        try:
            with urllib.request.urlopen(req) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code not in (429, 500, 502, 503, 504) or i == attempts - 1:
                raise
            log("HTTP %d, retrying in %ds" % (e.code, delay))
            time.sleep(delay)
            delay = min(delay * 2, 120)


def fetch_sdk():
    sdk = os.path.join(OUT, "sdk")
    os.makedirs(sdk, exist_ok=True)
    aapt2 = os.path.join(sdk, "aapt2")
    if not os.path.exists(aapt2):
        log("aapt2 " + AAPT2_VERSION)
        z = zipfile.ZipFile(io.BytesIO(fetch(AAPT2_URL)))
        with open(aapt2, "wb") as f:
            f.write(z.read("aapt2"))
        os.chmod(aapt2, 0o755)
    jar = os.path.join(sdk, "android.jar")
    if not os.path.exists(jar):
        log("android platform 35 (android.jar)")
        z = zipfile.ZipFile(io.BytesIO(fetch(PLATFORM_URL)))
        name = [n for n in z.namelist() if n.endswith("/android.jar")][0]
        with open(jar, "wb") as f:
            f.write(z.read(name))
    fwres = os.path.join(OUT, "framework-res.apk")
    if not os.path.exists(fwres):
        log("framework-res.apk")
        subprocess.check_call([sys.executable, os.path.join(ROOT, "tools", "make_framework_res.py"), jar, fwres])
    log("sdk ready in " + sdk)


def fetch_sqlite():
    dst = os.path.join(ROOT, "third_party", "sqlite")
    if os.path.exists(os.path.join(dst, "sqlite3.c")):
        return
    log("sqlite amalgamation")
    os.makedirs(dst, exist_ok=True)
    z = zipfile.ZipFile(io.BytesIO(fetch(SQLITE_URL)))
    for n in z.namelist():
        base = os.path.basename(n)
        if base in ("sqlite3.c", "sqlite3.h"):
            with open(os.path.join(dst, base), "wb") as f:
                f.write(z.read(n))


def fetch_devkitpro():
    if os.environ.get("DEVKITPRO") and os.path.isdir(os.path.join(os.environ["DEVKITPRO"], "libnx")):
        log("using existing DEVKITPRO=" + os.environ["DEVKITPRO"])
        return
    dst = os.path.join(OUT, "devkitpro")
    marker = os.path.join(dst, ".complete")
    if os.path.exists(marker):
        return
    os.makedirs(dst, exist_ok=True)
    log("devkitPro image %s:%s (registry layers, no docker daemon)" % (DKP_IMAGE, DKP_TAG))
    tok = json.loads(fetch("https://auth.docker.io/token?service=registry.docker.io&scope=repository:%s:pull" % DKP_IMAGE))["token"]
    accept = ", ".join([
        "application/vnd.oci.image.index.v1+json",
        "application/vnd.docker.distribution.manifest.list.v2+json",
        "application/vnd.oci.image.manifest.v1+json",
        "application/vnd.docker.distribution.manifest.v2+json",
    ])
    hdr = {"Authorization": "Bearer " + tok, "Accept": accept}
    base = "https://registry-1.docker.io/v2/%s" % DKP_IMAGE
    man = json.loads(fetch("%s/manifests/%s" % (base, DKP_TAG), hdr))
    if "manifests" in man:  # multi-arch index: pick linux/amd64
        digest = [m["digest"] for m in man["manifests"]
                  if m.get("platform", {}).get("architecture") == "amd64" and m.get("platform", {}).get("os") == "linux"][0]
        man = json.loads(fetch("%s/manifests/%s" % (base, digest), hdr))
    layers = man["layers"]
    for i, layer in enumerate(layers):
        d = layer["digest"]
        log("layer %d/%d %s (%d MB)" % (i + 1, len(layers), d[:19], layer.get("size", 0) >> 20))
        blob = fetch("%s/blobs/%s" % (base, d), {"Authorization": "Bearer " + tok})
        with tarfile.open(fileobj=io.BytesIO(blob), mode="r:*") as tf:
            members = []
            for m in tf.getmembers():
                name = m.name.lstrip("./")
                # only the devkitPro tree matters; skip the base OS and whiteouts
                if not name.startswith("opt/devkitpro") or os.path.basename(name).startswith(".wh."):
                    continue
                if m.isdev():
                    continue
                members.append(m)
            tf.extractall(dst, members=members)
    open(marker, "w").close()
    log("devkitPro ready: export DEVKITPRO=%s" % os.path.join(dst, "opt", "devkitpro"))


def main():
    what = sys.argv[1] if len(sys.argv) > 1 else "all"
    if what in ("sdk", "all"):
        fetch_sdk()
    if what in ("sqlite", "all"):
        fetch_sqlite()
    if what in ("devkitpro", "all"):
        fetch_devkitpro()


if __name__ == "__main__":
    main()
