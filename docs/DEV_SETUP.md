# Development setup

## Requirements (host)

- Linux (x86-64 or AArch64), gcc or clang, make, zlib headers, pthreads
- JDK 17+ (`javac`, `java`, `javap`) for building the Java side and the
  VM conformance tests
- Python 3.8+
- For OpenGL ES samples: Mesa EGL/GLES (`apt install libegl1 libgles2
  libgl1-mesa-dri`); the headers (`libegl-dev libgles-dev`) only to
  regenerate the bindings. Without them GL apps show "OpenGL ES
  unavailable" instead of rendering.
- Internet access to dl.google.com, sqlite.org and Docker Hub (for devkitPro)

## Fetch toolchains

```
python3 tools/fetch_toolchains.py sdk        # build/toolchains/sdk/{aapt2,android.jar}, build/toolchains/framework-res.apk
python3 tools/fetch_toolchains.py sqlite     # third_party/sqlite/sqlite3.{c,h}
python3 tools/fetch_toolchains.py devkitpro  # build/toolchains/devkitpro/opt/devkitpro (large; Docker Hub may rate-limit)
export DEVKITPRO=$PWD/build/toolchains/devkitpro/opt/devkitpro
```

`tools/build_java.sh` downloads d8/r8 into `build/tools` on first use.
If `DEVKITPRO` already points at an installation (dkp-pacman), the
devkitpro step is skipped. Alternatives when Docker Hub throttles: wait
and retry, `docker pull devkitpro/devkita64`, or the official installer.

## Build

```
make                    # host binary + framework.dex
make java               # only the Java side
make clean
make -f Makefile.switch       # build/switch/switchapk.nro (run `make` first for framework.dex)
make -f Makefile.switch dist  # build/switch/switchapk-sd.zip: NRO + sample APKs in the SD layout
```

## CI

`.github/workflows/package.yml` (Package NRO) runs on every push, on pull
requests from forks, and from the Actions tab (workflow dispatch). The
runner is Ubuntu 24.04 with JDK 17. It fetches the SDK and devkitPro
(cached between runs), then runs `make` and `make -f Makefile.switch dist`.
The SD zip includes every app under `tests/apps`. `dist` always rebuilds an
app that has `native/build.sh` with `NDK_ARM64=1`, so its APK carries
arm64-v8a libraries; that needs clang, lld and `libc6-dev-arm64-cross`,
which the workflow installs. The workflow checks that each sample APK is
in the zip and that each native APK has an arm64-v8a library. The `switchapk-sd` artifact is
`build/switch/switchapk-sd.zip`, kept for 14 days. Unzip it at the root of the SD card, the same as a local build.

## Run on a Switch

Needs a Switch running homebrew (Atmosphere + hbmenu).

1. Unzip `build/switch/switchapk-sd.zip` at the root of the SD card. It
   creates `switch/switchapk/switchapk.nro` and `switch/switchapk/apks/`
   with the sample apps. Put other APKs in that `apks` folder.
2. Start hbmenu in full-memory mode (hold R while starting a game, not
   from the Album applet: applet mode has far less memory) and open
   switchapk.
3. Pick an APK with the D-pad, stick or touch and press A. The app ends
   with B (back) on its first screen, or HOME and close. switchapk then
   returns to its list; + exits.

Controls in apps: touch in handheld mode; D-pad or left stick moves
focus; A clicks; B is back; + opens the options menu.

When an app fails, an error screen shows the last log lines. The full
log is `sdmc:/switch/switchapk/log.txt`. For live logs, start it with
nxlink: `$DEVKITPRO/tools/bin/nxlink -s -a <switch-ip> build/switch/switchapk.nro sdmc:/switch/switchapk/apks/hello.apk`
(the APK path argument skips the launcher).

Native `.so` libraries do not load on the Switch yet (WS9 needs code
memory there), so only APKs whose code is all Java/Kotlin run on device.
The host build loads them (x86_64 libraries; arm64-v8a under qemu, below).

## Run

Plain dex programs (VM testing):

```
build/host/switchapk-host --raw-stdio program.dex MainClass args...
tests/run_dex_test.sh tests/dex/VmTest.java
```

APKs (after WS0):

```
build/host/switchapk-host [--data build/data] [--screen 1280x720@240] \
    [--script tests/apps/hello/hello.script] [--screenshots build/shots] app.apk
```

Useful flags: `-v` / `-vv` (debug/verbose logs), `--trace` (every
instruction), `--framework path/to/framework.dex`. Look for `STUB:` lines
to find framework APIs apps call that we do not implement.

## Building test APKs

An app directory holds `AndroidManifest.xml`, `java/` sources and an
optional `res/` tree. `tools/build_apk.sh` links with aapt2, compiles
against `android.jar` at Java 8, dexes with d8 and writes an unsigned APK.
No signing is required: switchapk ignores signatures.

```
tools/build_apk.sh tests/apps/hello
build/host/switchapk-host --data build/data --screen 1280x720@240 \
    --script tests/apps/hello/hello.script --screenshots build/shots \
    build/apps/hello/hello.apk
python3 tests/apps/hello/check_shot.py build/shots/hello.png
```

Sample apps with scripts and screenshot checks:

```
tools/build_apk.sh tests/apps/views
mkdir -p build/shots
build/host/switchapk-host --data build/data --screen 1280x720@240 \
    --script tests/apps/views/views.script --screenshots build/shots \
    build/apps/views/views.apk
python3 tests/apps/views/check_shots.py build/shots
```

An app directory may also hold `assets/` (packaged as APK assets) and an
executable `native/build.sh <out-dir>` that writes `lib/<abi>/*.so` into
`<out-dir>`; `tests/apps/ndk` builds its libraries with clang and lld
for `x86_64-linux-android21` (plus `aarch64-linux-android21` when
`NDK_ARM64=1`), with Android packed relocations and no NDK.

The screenshot directory must exist. `idle` in a script waits until the
queued input was consumed and the app presented nothing for the quiet time.

## Inspecting things

- `python3 tools/api_check.py android.view.View android.view.ViewGroup`
  (or `-p android.view`) lists public/protected members of android.jar
  that our framework lacks (the ones the VM would auto-stub); `-v` also
  lists members we declare that android.jar does not have. Run `make java`
  first.

- `python3 tools/dexdump.py file.dex` lists classes/methods.
- `javap -cp build/toolchains/sdk/android.jar -public <class>` shows the
  exact API signatures to match.
- `aapt2 dump xmltree app.apk --file AndroidManifest.xml`, `aapt2 dump
  resources app.apk` for resources.
- `tests/c/gfx_test.c` builds a standalone renderer test:
  `cc -Isrc -Ithird_party tests/c/gfx_test.c build/host/src/gfx/*.o build/host/src/core/util.o -lz -lm -lpthread -o gfx_test && ./gfx_test out.png`

## Regenerating generated sources

- `java/framework/android/R.java`:
  `javac -d /tmp/genr tools/genr/GenR.java && java -cp /tmp/genr GenR build/toolchains/sdk/android.jar > java/framework/android/R.java`
- `framework-res.apk`:
  `python3 tools/make_framework_res.py build/toolchains/sdk/android.jar build/toolchains/framework-res.apk`

- GLES bindings (`android.opengl.GLES*`, GL10/GL11, GLImpl,
  `src/android/android_gles_gen.c`, `src/android/gles_funcs.h`):
  `python3 tools/gen_gles.py` (needs android.jar and the Khronos headers in
  /usr/include). Hand-written exceptions live in
  `src/android/android_gles_special.c`.

## Where the runtime looks for files

- Host: framework dex from `--framework` (default `build/java/framework.dex`).
  framework-res.apk is the first readable path of
  `build/toolchains/framework-res.apk`, `build/java/framework-res.apk` and
  `{platform_framework_path()}/framework-res.apk` (that function returns
  `build/java` on the host). If none is readable the toolchain path is
  still passed and the framework resource table stays empty. App data is
  under `--data`.
- Switch: romfs `romfs:/framework.dex`, `romfs:/framework-res.apk`,
  optional `romfs:/fonts/` (else the system shared fonts); APKs in
  `sdmc:/switch/switchapk/apks/`; data under `sdmc:/switch/switchapk/data/`;
  log in `sdmc:/switch/switchapk/log.txt`.
- Checking the VM on AArch64 without a Switch: build the host binary with
  `make CC=aarch64-linux-gnu-gcc BUILD=build/host-a64 build/host-a64/switchapk-host`
  and run it under `qemu-aarch64` (`QEMU_LD_PREFIX=/usr/aarch64-linux-gnu`,
  needs `zlib1g-dev:arm64`, or a cross-built zlib passed with
  `CC="aarch64-linux-gnu-gcc -I<zlib>/include" LDLIBS="-L<zlib>/lib -lz -lm -lpthread -ldl"`).
  This is how the loader's AArch64 path is tested: build tests/apps/ndk
  with `NDK_ARM64=1` and run the APK with that binary.
