#!/bin/sh
# Compiles tests/bench/Bench.java to dex and runs it on switchapk (and, with --jvm, on the JVM in interpreted mode).
#   tests/bench/run_bench.sh [--jvm] [scale]
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=$ROOT/build/bench
R8=$(ls "$ROOT"/build/tools/r8-*.jar | head -1)
JVM=0
if [ "$1" = "--jvm" ]; then JVM=1; shift; fi
rm -rf "$OUT" && mkdir -p "$OUT/classes"
javac --release 17 -nowarn -d "$OUT/classes" "$ROOT/tests/bench/Bench.java" 2>&1 | grep -v "Picked up" || true
java -cp "$R8" com.android.tools.r8.D8 --min-api 24 --release --lib "$ROOT/build/java/libcore" --output "$OUT" \
    $(find "$OUT/classes" -name '*.class') 2>&1 | grep -v "Picked up" | grep -v "^Warning" || true
if [ $JVM = 1 ]; then
    echo "== JVM, interpreted (-Xint)"
    java -Xint -cp "$OUT/classes" Bench "$@" 2>&1 | grep -v "Picked up"
fi
echo "== switchapk"
"$ROOT/build/host/switchapk-host" --raw-stdio --data "$OUT/data" "$OUT/classes.dex" Bench "$@"
