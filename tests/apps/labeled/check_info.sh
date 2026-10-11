#!/bin/sh
# Host check for the launcher identity reader. Does not boot the VM.
# labeled: activity @string label and hdpi icon beat the application values and the mdpi bucket.
# hello: a literal label and no icon.
# a zip with no manifest: the file name, and no icon.
set -e
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
HOST=$ROOT/build/host/switchapk-host
if [ ! -x "$HOST" ]; then
    echo "missing $HOST (run make)" >&2
    exit 1
fi
"$ROOT/tools/build_apk.sh" "$ROOT/tests/apps/labeled"
"$ROOT/tools/build_apk.sh" "$ROOT/tests/apps/hello"
LABELED=$ROOT/build/apps/labeled/labeled.apk
HELLO=$ROOT/build/apps/hello/hello.apk
PLAIN=$(mktemp --suffix=.apk)
python3 -c 'import sys, zipfile; z = zipfile.ZipFile(sys.argv[1], "w"); z.writestr("readme.txt", "plain"); z.close()' "$PLAIN"
trap 'rm -f "$PLAIN"' EXIT

check() {
    out=$("$HOST" --apk-info "$1")
    printf '%s\n' "$out"
    shift
    for line in "$@"; do
        printf '%s\n' "$out" | grep -qx "$line"
    done
}

check "$LABELED" "label=Labeled" "package=com.example.labeled" "version=1.2.3" "icon=16x16" "px0=FF1122CC" "px1=FF33AA44"
check "$HELLO" "label=Hello" "version=" "icon=none"
base=$(basename "$PLAIN" .apk)
check "$PLAIN" "label=$base" "package=" "version=" "icon=none"
echo "apk-info ok"
