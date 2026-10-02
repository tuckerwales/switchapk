#!/bin/sh
# Builds libnativeact.so the way the NDK would (Android target, lld, packed
# relocations) without the NDK. The library imports ANativeWindow and EGL;
# nothing here is linked against libc.
#   native/build.sh <out-dir>
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$1
CC=${NDK_CC:-clang}
INC="$HERE/../../../../src/nativeloader"
build() { # triple abi multiarch
    mkdir -p "$OUT/lib/$2"
    ARCH_INC=/usr/include/$3
    [ -d "$ARCH_INC" ] || ARCH_INC=/usr/$3/include
    FLAGS="--target=$1 -fPIC -shared -nostdlib -O2 -Wall -fuse-ld=lld -I$INC -isystem $ARCH_INC \
        -U_FORTIFY_SOURCE -Wl,--pack-dyn-relocs=android+relr -Wl,--hash-style=both"
    # shellcheck disable=SC2086
    $CC $FLAGS -Wl,-soname,libnativeact.so "$HERE/nativeact.c" -o "$OUT/lib/$2/libnativeact.so"
}
build x86_64-linux-android21 x86_64 x86_64-linux-gnu
if [ -n "$NDK_ARM64" ]; then
    build aarch64-linux-android21 arm64-v8a aarch64-linux-gnu
fi
