#!/bin/sh
# Builds libopenslmix.so the way the NDK would (Android target, lld, packed relocations).
#   native/build.sh <out-dir>   writes <out-dir>/lib/<abi>/libopenslmix.so
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../../../.." && pwd)
OUT=$1
JDK_INC=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")/include
CC=${NDK_CC:-clang}
build() { # triple abi multiarch
    mkdir -p "$OUT/lib/$2"
    ARCH_INC=/usr/include/$3
    [ -d "$ARCH_INC" ] || ARCH_INC=/usr/$3/include
    FLAGS="--target=$1 -fPIC -shared -nostdlib -fno-builtin -O2 -Wall -fuse-ld=lld \
        -I$JDK_INC -I$JDK_INC/linux -I$ROOT/src/nativeloader \
        -isystem $ARCH_INC -U_FORTIFY_SOURCE -Wl,--pack-dyn-relocs=android+relr -Wl,--hash-style=both"
    # shellcheck disable=SC2086
    $CC $FLAGS -Wl,-soname,libopenslmix.so "$HERE/openslmix.c" -o "$OUT/lib/$2/libopenslmix.so"
}
build x86_64-linux-android21 x86_64 x86_64-linux-gnu
if [ -n "$NDK_ARM64" ]; then
    build aarch64-linux-android21 arm64-v8a aarch64-linux-gnu
fi
