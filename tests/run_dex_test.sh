#!/bin/sh
# Compiles a Java test, runs it on the reference JVM and on switchapk, and diffs stdout.
#   tests/run_dex_test.sh tests/dex/VmTest.java
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SRC=$1
NAME=$(basename "$SRC" .java)
OUT=$ROOT/build/tests/$NAME
R8=$(ls "$ROOT"/build/tools/r8-*.jar | head -1)
rm -rf "$OUT" && mkdir -p "$OUT/classes"
javac --release 17 -nowarn -encoding UTF-8 -d "$OUT/classes" "$SRC" 2>&1 | grep -v "Picked up" || true
(cd "$OUT/classes" && java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US -Duser.timezone=UTC -Djava.io.tmpdir=/tmp "$NAME") \
    2>&1 | grep -v "Picked up JAVA_TOOL_OPTIONS" > "$OUT/expected.txt" || true
java -cp "$R8" com.android.tools.r8.D8 --min-api 24 --lib "$ROOT/build/java/libcore" --output "$OUT" \
    $(find "$OUT/classes" -name '*.class') 2>&1 | grep -v "Picked up" | grep -v "^Warning" || true
"$ROOT/build/host/switchapk-host" --raw-stdio --data "$OUT/data" "$OUT/classes.dex" "$NAME" > "$OUT/actual.txt" 2> "$OUT/stderr.txt" || true
if diff -u "$OUT/expected.txt" "$OUT/actual.txt" > "$OUT/diff.txt"; then
    echo "PASS $NAME"
else
    echo "FAIL $NAME (see $OUT/diff.txt)"
    head -80 "$OUT/diff.txt"
    tail -20 "$OUT/stderr.txt"
    exit 1
fi
