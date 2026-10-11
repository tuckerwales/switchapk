#!/bin/sh
# Builds libechoexe.so, a bionic PIE executable shipped as a "library" (as apps ship engines), without the NDK:
# a stub libc.so supplies the soname and symbol names; switchapk's shim provides the functions.
#   native/build.sh <out-dir>   writes <out-dir>/lib/<abi>/libechoexe.so
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$1
CC=${NDK_CC:-clang}
build() { # triple abi
    mkdir -p "$OUT/lib/$2" "$OUT/stub/$2"
    FLAGS="--target=$1 -fPIC -nostdlib -O2 -Wall -fuse-ld=lld -ffreestanding -fno-builtin -fno-stack-protector"
    # shellcheck disable=SC2086
    $CC $FLAGS -shared -Wl,-soname,libc.so "$HERE/libc_stub.c" -o "$OUT/stub/$2/libc.so"
    # shellcheck disable=SC2086
    $CC $FLAGS -fPIE -pie -Wl,--dynamic-linker=/system/bin/linker64 -Wl,--pack-dyn-relocs=android+relr \
        -Wl,--hash-style=both "$HERE/echoexe.c" -L"$OUT/stub/$2" -lc -o "$OUT/lib/$2/libechoexe.so"
}
build x86_64-linux-android21 x86_64
if [ -n "$NDK_ARM64" ]; then
    build aarch64-linux-android21 arm64-v8a
fi
