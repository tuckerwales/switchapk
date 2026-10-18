#!/bin/sh
# Host check for the Switch home screen (src/app/launcher.c), driven through switchapk-host --launcher.
# Does not boot the VM. Covers: name order with nothing played, D-pad + A, the saved last app and play time,
# "Recently played" order on the next start, touch (tap a tile, tap Play), the empty folder, and the
# "app stopped" screen's Try again, and a split bundle (.apkm) listed and launched like an APK. Screenshots of each screen land in build/launcher-shots.
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
HOST=$ROOT/build/host/switchapk-host
if [ ! -x "$HOST" ]; then
    echo "missing $HOST (run make)" >&2
    exit 1
fi
for app in hello labeled list; do "$ROOT/tools/build_apk.sh" "$ROOT/tests/apps/$app" >/dev/null 2>&1; done
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
SHOTS=$ROOT/build/launcher-shots
mkdir -p "$WORK/apks" "$WORK/empty" "$SHOTS"
for app in hello labeled list; do cp "$ROOT/build/apps/$app/$app.apk" "$WORK/apks/"; done

run() { # run <script text> <apk dir> [extra args]
    printf '%s\n' "$1" > "$WORK/script"
    "$HOST" --launcher --data "$WORK/data" --script "$WORK/script" --screenshots "$SHOTS" "$2" 2>/dev/null
}
expect() { # expect <got> <want>
    if [ "$1" != "$2" ]; then
        echo "FAIL: got '$1', want '$2'" >&2
        exit 1
    fi
}

# Nothing played yet: A to Z (Hello, Labeled, List). Right once, then A.
out=$(run "idle 400
screenshot launcher-home.png
key DPAD_RIGHT
idle 400
screenshot launcher-selected.png
key BUTTON_A" "$WORK/apks")
expect "$out" "launch=$WORK/apks/labeled.apk"
grep -qx "last=labeled.apk" "$WORK/data/launcher/launcher.ini"
grep -q "^played=[0-9]* labeled.apk$" "$WORK/data/launcher/launcher.ini"
test -s "$WORK/data/launcher/icons/labeled.apk.cache"

# Next start: Labeled is first under Recently played and selected, so A runs it again (from the icon cache).
out=$(run "idle 400
screenshot launcher-recent.png
key BUTTON_A" "$WORK/apks")
expect "$out" "launch=$WORK/apks/labeled.apk"

# Touch: tap the third tile (List) to select it, then tap the Play button.
out=$(run "idle 400
tap 640 270
idle 400
tap 1030 534" "$WORK/apks")
expect "$out" "launch=$WORK/apks/list.apk"

# Y switches to A to Z and is remembered.
out=$(run "idle 400
key BUTTON_Y
idle 300
key BUTTON_START" "$WORK/apks")
expect "$out" "exit"
grep -qx "sort=name" "$WORK/data/launcher/launcher.ini"

# Empty folder: X refreshes, + exits.
out=$(run "idle 400
key BUTTON_X
idle 300
screenshot launcher-empty.png
key BUTTON_START" "$WORK/empty")
expect "$out" "exit"

# A split bundle (.apkm: a zip of base.apk and splits) is listed under its app's own name and launched by its
# path; reading its identity merges it once into .switchapk-bundles (hidden, so not listed as an app).
mkdir -p "$WORK/bundles" "$WORK/bundle-src"
cp "$ROOT/build/apps/hello/hello.apk" "$WORK/bundles/"
cp "$ROOT/build/apps/labeled/labeled.apk" "$WORK/bundle-src/base.apk"
printf '{}' > "$WORK/bundle-src/info.json"
(cd "$WORK/bundle-src" && zip -q0 "$WORK/bundles/labeled.apkm" base.apk info.json)
out=$(run "idle 400
screenshot launcher-bundle.png
key DPAD_RIGHT
idle 400
key BUTTON_A" "$WORK/bundles")
expect "$out" "launch=$WORK/bundles/labeled.apkm"
test -s "$WORK/bundles/.switchapk-bundles/labeled.apkm.apk"

# The app stopped screen: X is Try again, A is Back to apps.
printf 'idle 300\nscreenshot launcher-error.png\nkey BUTTON_X\n' > "$WORK/script"
out=$("$HOST" --error-screen --script "$WORK/script" --screenshots "$SHOTS" "$WORK/apks/hello.apk" 2>/dev/null)
expect "$out" "retry"
printf 'idle 300\nkey BUTTON_A\n' > "$WORK/script"
out=$("$HOST" --error-screen --script "$WORK/script" "$WORK/apks/hello.apk" 2>/dev/null)
expect "$out" "back"

printf 'idle 300\nscreenshot launcher-splash.png\n' > "$WORK/script"
"$HOST" --splash --script "$WORK/script" --screenshots "$SHOTS" "$WORK/apks/labeled.apk" 2>/dev/null
for shot in home selected recent bundle empty error splash; do test -s "$SHOTS/launcher-$shot.png"; done
echo "launcher ok"
