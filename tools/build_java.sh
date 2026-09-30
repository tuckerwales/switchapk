#!/bin/sh
# Builds the Java class library (libcore + Android framework) into framework.dex.
# Requires: javac (JDK 11+), d8 (fetched automatically from Google's maven repo).
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${OUT:-$ROOT/build/java}
R8_VERSION=${R8_VERSION:-8.13.25}
R8_JAR=${R8_JAR:-$ROOT/build/tools/r8-$R8_VERSION.jar}
JAVAC_FLAGS="-source 8 -target 8 -encoding UTF-8 -nowarn -Xlint:-options -Xmaxerrs 1000 -g"

if [ ! -f "$R8_JAR" ]; then
    mkdir -p "$(dirname "$R8_JAR")"
    echo "Fetching d8/r8 $R8_VERSION"
    curl -sSfL -o "$R8_JAR" "https://dl.google.com/android/maven2/com/android/tools/r8/$R8_VERSION/r8-$R8_VERSION.jar"
fi

# Runs a command, filtering the JAVA_TOOL_OPTIONS banner but keeping its exit status.
quiet() {
    "$@" > "$OUT/.log" 2>&1 && status=0 || status=$?
    grep -v "Picked up JAVA_TOOL_OPTIONS" "$OUT/.log" || true
    return $status
}

mkdir -p "$OUT"
rm -rf "$OUT/libcore" "$OUT/framework"
mkdir -p "$OUT/libcore" "$OUT/framework"

echo "javac libcore"
find "$ROOT/java/libcore" -name '*.java' > "$OUT/libcore.list"
quiet javac $JAVAC_FLAGS -bootclasspath "$OUT/libcore" -d "$OUT/libcore" @"$OUT/libcore.list"

if [ -d "$ROOT/java/framework" ] && [ -n "$(find "$ROOT/java/framework" -name '*.java' | head -1)" ]; then
    echo "javac framework"
    find "$ROOT/java/framework" -name '*.java' > "$OUT/framework.list"
    quiet javac $JAVAC_FLAGS -bootclasspath "$OUT/libcore" -d "$OUT/framework" @"$OUT/framework.list"
fi

echo "d8"
find "$OUT/libcore" "$OUT/framework" -name '*.class' > "$OUT/classes.list"
quiet java -cp "$R8_JAR" com.android.tools.r8.D8 --release --min-api 24 --no-desugaring \
    --output "$OUT" @"$OUT/classes.list"
mv "$OUT/classes.dex" "$OUT/framework.dex"
ls -la "$OUT/framework.dex"
