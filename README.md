# switchapk

Run Android apps on the Nintendo Switch.

switchapk is a homebrew app (NRO) for the Switch. It loads unmodified APKs
and runs them with its own Android runtime, built from scratch:

- a Dalvik VM in C (interpreter, garbage collector, JNI)
- a Java class library and Android framework that follow the real Android
  APIs (activities, views, widgets, resources, themes, menus, dialogs,
  services, notifications)
- a software 2D renderer for `android.graphics`
- a platform layer over libnx (display, controllers, touch, keyboard, fonts)

It also runs headless on Linux, which is how the project is tested.

## Status

Early and experimental. It has not yet been confirmed on real hardware.

**Works today (tested on Linux, x86-64 and AArch64):**

- Apps written in Java or Kotlin that use the classic Android UI toolkit:
  XML layouts, Material framework themes, the common widgets (text and
  editing, buttons, lists, grids, tables, scrolling, progress bars, seek
  bars, spinners, popups, toasts, toolbars, the action bar and action
  modes)
- Several activities, dialogs, options and context menus, fragments
- Services, broadcasts, alarms, notifications, JobScheduler
- Touch and controller navigation (focus moves with the D-pad)

**Not yet:**

- Sound
- OpenGL ES and NDK (native `.so`) code, so most games do not run
- AndroidX/AppCompat apps are not targeted yet
- Networking and SQLite

See [docs/PLAN.md](docs/PLAN.md) for the full checklist and milestones.

## Running on a Switch

You need a Switch that runs homebrew (Atmosphere with hbmenu).

1. Build the SD card package (see below) or use a prebuilt
   `switchapk-sd.zip`, and unzip it at the root of the SD card. You get
   `switch/switchapk/switchapk.nro` and sample apps in
   `switch/switchapk/apks/`.
2. Put any other APKs in `switch/switchapk/apks/`.
3. Start hbmenu in full-memory mode: hold R while starting a game. The
   Album applet gives apps much less memory.
4. Open switchapk, pick an app with the D-pad, stick or touch, and press A.

Controls inside apps:

| Switch | Android |
|---|---|
| Touch screen (handheld) | touch |
| D-pad or left stick | move focus (D-pad keys) |
| A | select / click |
| B | back |
| + | options menu |
| X, Y, L, R, ZL, ZR, - | gamepad buttons |

When an app ends you return to the list; + exits switchapk. If an app
fails, an error screen shows the last log lines, and the full log is in
`sdmc:/switch/switchapk/log.txt`.

## Building

On Linux (x86-64 or AArch64) with gcc or clang, make, zlib headers,
JDK 17+ and Python 3:

```
python3 tools/fetch_toolchains.py sdk        # aapt2, android.jar, framework-res.apk
python3 tools/fetch_toolchains.py sqlite
python3 tools/fetch_toolchains.py devkitpro  # devkitA64 + libnx, only for the Switch build

make                                         # host binary + framework.dex
make -f Makefile.switch                      # build/switch/switchapk.nro
make -f Makefile.switch dist                 # build/switch/switchapk-sd.zip
```

Toolchains are downloaded into `build/toolchains` and are never committed.
To send an app straight to a Switch with live logs:

```
build/toolchains/devkitpro/opt/devkitpro/tools/bin/nxlink -s -a <switch-ip> \
    build/switch/switchapk.nro sdmc:/switch/switchapk/apks/hello.apk
```

## Testing on Linux

```
tests/run_dex_test.sh tests/dex/VmTest.java    # VM output must match OpenJDK

tools/build_apk.sh tests/apps/actionbar        # build a sample APK
build/host/switchapk-host --data build/data --screen 1280x720@240 \
    --script tests/apps/actionbar/actionbar.script --screenshots build/shots \
    build/apps/actionbar/actionbar.apk
```

Each sample in `tests/apps` has a script of taps and key presses, and a
checker that compares pixels in the screenshots and the order of the
app's log lines. [docs/DEV_SETUP.md](docs/DEV_SETUP.md) has the details,
including how to run the AArch64 build under qemu.

## Layout

| Path | What |
|---|---|
| `src/vm` | Dalvik VM: interpreter, classes, heap, threads, JNI |
| `src/native` | native methods for the Java class library |
| `src/android` | native side of the framework (resources, graphics, input) |
| `src/gfx` | software renderer, fonts, image codecs |
| `src/platform` | `platform.h` and the headless and Switch backends |
| `src/app` | entry points: host runner and Switch launcher |
| `java/libcore` | Java class library |
| `java/framework` | Android framework (`android.*`, `com.android.internal.*`) |
| `tests` | VM tests and sample apps with screenshot checks |
| `tools` | toolchain fetch, Java and APK build scripts, API checker |
| `docs` | plan, architecture, workstreams, conventions, setup, logs |

## Contributing

Start with [CLAUDE.md](CLAUDE.md) and [docs/PLAN.md](docs/PLAN.md). Work is
split into workstreams in [docs/WORKSTREAMS.md](docs/WORKSTREAMS.md); claim
one before starting. The key rule is API fidelity: public classes must
match the real Android signatures exactly (check with `tools/api_check.py`),
because the VM quietly stubs calls that do not match.

switchapk is not affiliated with Nintendo or Google. Android is a trademark
of Google LLC; Nintendo Switch is a trademark of Nintendo.
