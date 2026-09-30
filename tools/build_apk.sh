#!/bin/sh
# Builds an unsigned APK from an app directory.
# The directory holds AndroidManifest.xml, java/ sources, and an optional res/ tree.
#   tools/build_apk.sh tests/apps/hello [build/apps/hello/hello.apk]
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
APP=$1
if [ -z "$APP" ] || [ ! -f "$APP/AndroidManifest.xml" ] || [ ! -d "$APP/java" ]; then
    echo "usage: tools/build_apk.sh <app-dir> [out.apk]" >&2
    exit 2
fi
APP=$(cd "$APP" && pwd)
NAME=$(basename "$APP")
BUILD=$ROOT/build/apps/$NAME
SDK=$ROOT/build/toolchains/sdk
AAPT2=$SDK/aapt2
R8=$(ls "$ROOT"/build/tools/r8-*.jar | head -1)
OUT=$2
if [ -z "$OUT" ]; then
    OUT=$BUILD/$NAME.apk
fi
case "$OUT" in
    /*) ;;
    *) OUT=$ROOT/$OUT ;;
esac

rm -rf "$BUILD/classes" "$BUILD/gen"
mkdir -p "$BUILD/classes" "$BUILD/gen"
RES_ARG=
if [ -d "$APP/res" ] && [ -n "$(find "$APP/res" -type f | head -1)" ]; then
    "$AAPT2" compile --dir "$APP/res" -o "$BUILD/res.zip"
    RES_ARG=$BUILD/res.zip
fi
# shellcheck disable=SC2086
"$AAPT2" link -I "$SDK/android.jar" --manifest "$APP/AndroidManifest.xml" \
    -o "$BUILD/base.apk" --java "$BUILD/gen" --auto-add-overlay \
    --min-sdk-version 24 --target-sdk-version 29 $RES_ARG

find "$APP/java" "$BUILD/gen" -name '*.java' > "$BUILD/sources.list"
javac -source 8 -target 8 -encoding UTF-8 -nowarn -Xlint:-options \
    -cp "$SDK/android.jar" -d "$BUILD/classes" @"$BUILD/sources.list"
find "$BUILD/classes" -name '*.class' > "$BUILD/classes.list"
java -cp "$R8" com.android.tools.r8.D8 --lib "$SDK/android.jar" --min-api 24 \
    --output "$BUILD" @"$BUILD/classes.list"
cp "$BUILD/base.apk" "$OUT"
(cd "$BUILD" && zip -q -j "$OUT" classes.dex)
echo "wrote $OUT"
