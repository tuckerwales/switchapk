#!/bin/sh
# Runs Old School RuneScape on the host build and records how far it gets: a log with the client's DNS lookups,
# connections and audio setup, and a screenshot every few seconds. Use it on a Linux PC with a normal network (the
# cloud sandbox blocks the game port and re-terminates TLS, so there the client stops at "Error connecting").
#
#   tools/run_osrs.sh <osrs.apkm | base.apk split.apk... | bundle.apks> [--seconds N] [--script file]
#   tools/run_osrs.sh --again [--seconds N] [--script file]     # rerun the APK assembled last time
#
# Needs: Linux on x86-64 or AArch64, gcc or clang, make, python3, JDK 17+, and the dev packages
#   zlib1g-dev libmbedtls-dev libegl1 libgles2 libgl1-mesa-dri   (Debian/Ubuntu names)
# The APK's native libraries must match the PC: a phone gives arm64-v8a only (AArch64 Linux), APKMirror's
# bundle also has x86_64. The host build is headless: it draws into screenshots, it does not open a window.
#
# Output in build/osrs-run/: log.txt (the whole run with -v), shots/NN.png, summary.txt.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
OUT=build/osrs-run
SECONDS_TO_RUN=90
SCRIPT=
AGAIN=0
FILES=
while [ $# -gt 0 ]; do
    case "$1" in
        --seconds) SECONDS_TO_RUN=$2; shift 2 ;;
        --script) SCRIPT=$2; shift 2 ;;
        --again) AGAIN=1; shift ;;
        -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
        *) FILES="$FILES $1"; shift ;;
    esac
done
if [ $AGAIN = 0 ] && [ -z "$FILES" ]; then
    sed -n '2,16p' "$0"
    exit 2
fi

say() { printf '\n== %s\n' "$*"; }

say "checking tools"
missing=
for t in make python3 javac java; do command -v $t >/dev/null 2>&1 || missing="$missing $t"; done
command -v cc >/dev/null 2>&1 || command -v gcc >/dev/null 2>&1 || missing="$missing cc"
[ -f /usr/include/mbedtls/ssl.h ] || missing="$missing libmbedtls-dev"
[ -f /usr/include/zlib.h ] || missing="$missing zlib1g-dev"
if [ -n "$missing" ]; then
    echo "missing:$missing"
    echo "on Debian/Ubuntu: sudo apt install build-essential python3 openjdk-17-jdk-headless zlib1g-dev libmbedtls-dev libegl1 libgles2 libgl1-mesa-dri"
    exit 1
fi
ldconfig -p 2>/dev/null | grep -q libEGL.so || echo "warning: no libEGL found; the game needs OpenGL ES 3 (install libegl1 libgles2 libgl1-mesa-dri)"

say "toolchains and build"
[ -f build/toolchains/sdk/android.jar ] || python3 tools/fetch_toolchains.py sdk
[ -f third_party/sqlite/sqlite3.c ] || python3 tools/fetch_toolchains.py sqlite
make -j"$(nproc 2>/dev/null || echo 4)"

APK=build/corpus/apks/com.jagex.oldscape.android_local.apk
if [ $AGAIN = 0 ]; then
    say "assembling the APK from your copy"
    # shellcheck disable=SC2086
    python3 tools/corpus.py import osrs $FILES
    rm -f "$APK"
    python3 tools/corpus.py fetch osrs
fi
[ -f "$APK" ] || { echo "no assembled APK at $APK; run with your bundle first"; exit 1; }

case "$(uname -m)" in
    x86_64) ABI=x86_64 ;;
    aarch64|arm64) ABI=arm64-v8a ;;
    *) ABI=unknown ;;
esac
if ! python3 -c "import sys, zipfile; z = zipfile.ZipFile(sys.argv[1]); sys.exit(0 if any(n.startswith('lib/' + sys.argv[2] + '/') for n in z.namelist()) else 1)" "$APK" "$ABI"; then
    echo "the APK has no lib/$ABI libraries, so the game cannot load on this $(uname -m) PC"
    echo "(a phone gives arm64-v8a only: use an AArch64 Linux machine, or a bundle that includes $ABI)"
    exit 1
fi

say "running for ${SECONDS_TO_RUN}s"
rm -rf "$OUT/shots"
mkdir -p "$OUT/shots" "$OUT/data"
if [ -z "$SCRIPT" ]; then
    SCRIPT=$OUT/watch.script
    {
        echo "# written by tools/run_osrs.sh: a screenshot every 5 seconds"
        n=0
        while [ $((n * 5)) -lt "$SECONDS_TO_RUN" ]; do
            n=$((n + 1))
            echo "wait 5000"
            printf 'screenshot %02d.png\n' "$n"
        done
        echo "quit"
    } > "$SCRIPT"
fi
# SWITCHAPK_TRACE_NATIVE shows the touch and key events reaching the game's native code.
set +e
SWITCHAPK_TRACE_NATIVE=onTouchEventNative timeout $((SECONDS_TO_RUN + 60)) \
    build/host/switchapk-host -v --data "$OUT/data" --screen 1280x720@240 --script "$SCRIPT" \
    --screenshots "$OUT/shots" "$APK" > "$OUT/log.txt" 2>&1
STATUS=$?
set -e

say "summary"
{
    echo "exit status: $STATUS (124 means it was still running at the time limit)"
    echo
    echo "name lookups and connections from the game's native code:"
    grep -E "D/libc: (getaddrinfo|connect)\(" "$OUT/log.txt" | sed 's/^/  /' | head -40
    echo
    echo "audio (OpenSL ES):"
    grep -E "D/opensl" "$OUT/log.txt" | sed 's/^/  /' | head -10
    echo
    echo "browser requests (Jagex Account login goes through one):"
    grep -E "browser open|SystemBrowser" "$OUT/log.txt" | sed 's/^/  /' | head -10
    echo
    echo "uncaught exceptions and native errors:"
    grep -E "FATAL|Uncaught|UnsatisfiedLinkError|SIGSEGV|signal [0-9]+" "$OUT/log.txt" | sed 's/^/  /' | head -20
    echo
    echo "screenshots: $(ls "$OUT/shots" | wc -l) in $OUT/shots"
} | tee "$OUT/summary.txt"
echo
echo "full log: $OUT/log.txt"
