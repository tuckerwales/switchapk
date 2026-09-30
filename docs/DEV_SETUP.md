# Development setup

## Requirements (host)

- Linux (x86-64 or AArch64), gcc or clang, make, zlib headers, pthreads
- JDK 17+ (`javac`, `java`, `javap`) for building the Java side and the
  VM conformance tests
- Python 3.8+
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
make -f Makefile.switch # (todo, WS10) switchapk.nro
```

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

## Building test APKs manually (until WS13 adds tools/build_apk.sh)

```
SDK=build/toolchains/sdk
aapt2 compile --dir app/res -o build/app/res.zip
aapt2 link -I $SDK/android.jar --manifest app/AndroidManifest.xml -o build/app/base.apk \
      --java build/app/gen build/app/res.zip --auto-add-overlay
javac -source 8 -target 8 -cp $SDK/android.jar -d build/app/classes $(find app/java build/app/gen -name '*.java')
java -cp build/tools/r8-*.jar com.android.tools.r8.D8 --lib $SDK/android.jar --min-api 24 \
      --output build/app $(find build/app/classes -name '*.class')
cd build/app && cp base.apk app.apk && zip -q app.apk classes.dex
```

No signing is required: switchapk ignores signatures.

## Inspecting things

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

## Where the runtime looks for files

- Host: framework dex from `--framework` (default `build/java/framework.dex`);
  framework-res.apk next to it or in `build/toolchains/` (WS0 decides the
  exact search order and documents it here); app data under `--data`.
- Switch (WS10): romfs `romfs:/framework.dex`, `romfs:/framework-res.apk`,
  `romfs:/fonts/`; APKs in `sdmc:/switch/switchapk/apks/`; data under
  `sdmc:/switch/switchapk/data/`.
