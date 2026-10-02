#!/bin/sh
# Builds the sample's native libraries like the NDK would (Android target, lld, packed relocations),
# without needing the NDK: no libc headers beyond the JDK's jni.h, imports are declared by hand.
#   native/build.sh <out-dir>   writes <out-dir>/lib/<abi>/libndkdep.so and libndktest.so
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$1
JDK_INC=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")/include
CC=${NDK_CC:-clang}
build() { # triple abi multiarch
    mkdir -p "$OUT/lib/$2"
    # jni.h includes <stdio.h>: use the host's (or the cross) libc headers for that; nothing links to them
    ARCH_INC=/usr/include/$3
    [ -d "$ARCH_INC" ] || ARCH_INC=/usr/$3/include
    FLAGS="--target=$1 -fPIC -shared -nostdlib -O2 -Wall -fuse-ld=lld -I$JDK_INC -I$JDK_INC/linux \
        -isystem $ARCH_INC -U_FORTIFY_SOURCE -Wl,--pack-dyn-relocs=android+relr -Wl,--hash-style=both"
    # shellcheck disable=SC2086
    $CC $FLAGS -Wl,-soname,libndkdep.so "$HERE/ndkdep.c" -o "$OUT/lib/$2/libndkdep.so"
    # shellcheck disable=SC2086
    $CC $FLAGS -Wl,-soname,libndktest.so "$HERE/ndktest.c" -L"$OUT/lib/$2" -lndkdep -o "$OUT/lib/$2/libndktest.so"
}
build x86_64-linux-android21 x86_64 x86_64-linux-gnu
if [ -n "$NDK_ARM64" ]; then
    build aarch64-linux-android21 arm64-v8a aarch64-linux-gnu
fi
