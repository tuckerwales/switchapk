# switchapk: plan, status and checklist

Single source of truth for direction and progress across sessions and
agents. Companion documents:

| Document | Purpose |
|---|---|
| `CLAUDE.md` | entry point for agents: rules and reading order |
| `docs/ARCHITECTURE.md` | how every subsystem works and the contracts between them |
| `docs/WORKSTREAMS.md` | parallel work packages, dependencies, claims |
| `docs/CONVENTIONS.md` | coding, API-fidelity, native, testing and git conventions |
| `docs/DEV_SETUP.md` | toolchains, building, running, debugging |
| `docs/DECISIONS.md` | decision log with rationale |
| `docs/SESSION_LOG.md` | what each session did |

## Vision

Put an APK on the Switch SD card, pick it in the switchapk launcher, and
it runs: Java/Kotlin apps built with the standard Android toolchain,
including AndroidX apps, 2D and OpenGL ES games, and NDK games. Input comes
from touch, Joy-Cons and Pro Controller; audio plays; files and
preferences persist. Compatibility is judged against real apps, not API
coverage percentages.

Non-goals (for now): Google Play Services, WebView rendering, cameras,
telephony, multi-process apps, Vulkan, 32-bit ARM (armeabi-v7a) native
libraries, x86 native libraries on device.

## Milestones

Each milestone has acceptance criteria; later milestones assume earlier
ones.

- **M0 VM** (done): Dalvik VM, libcore, JNI; VmTest identical to OpenJDK.
- **M1 First pixels** (WS0): framework compiles, natives registered, APK
  runner; a hello APK with a custom View draws on the headless platform
  and produces a correct screenshot.
- **M2 Classic UI apps** (WS1, WS2, WS3, WS4): XML layouts with the core
  widgets, Material framework themes, touch and controller navigation,
  multiple activities, dialogs, menus, SharedPreferences. Acceptance:
  sample apps in `tests/apps` pass their scripts with screenshot checks.
- **M3 On device** (WS10): NRO with launcher runs M2 samples on a Switch
  (handheld and docked).
- **M4 Games** (WS5, WS7, WS8, WS15): animations, SoundPool/MediaPlayer
  audio, GLSurfaceView GLES2 games, sensors. Acceptance: a Canvas game and
  a GLES2 game sample are playable on device with sound.
- **M5 Native** (WS9): NDK JNI libraries and NativeActivity games
  (android_native_app_glue, EGL/GLES, OpenSL ES audio).
- **M6 Real apps** (WS6, WS11, WS12, WS13, WS14): SQLite, networking,
  AndroidX/AppCompat/Material/RecyclerView, performance work; track a
  corpus of open-source APKs in `docs/COMPATIBILITY.md`.

## Current state (end of session 2, see SESSION_LOG.md)

Working:
- VM core, libcore, JNI, reflection, threads; VmTest passes.
- `src/gfx` renderer, fonts, images, clip masks (visual test in
  `tests/c/gfx_test.c`).
- Multi-package resource table; AssetManager and graphics natives (C).
- Headless platform with scripted input and screenshots.
- `tools/fetch_toolchains.py` fetches aapt2, android.jar, builds
  framework-res.apk, fetches SQLite; devkitPro fetch implemented (Docker
  Hub may rate-limit: retries built in).

Written, not yet compiling (M1 blocker, WS0):
- ~300 framework Java files: android.util, org.xmlpull, org.json,
  android.os, android.content (+res, +pm), android.net.Uri,
  android.database (+sqlite Java side), android.graphics (+drawable).
  They reference classes that do not exist yet (android.view, android.text,
  android.app, android.media.AudioAttributes, android.webkit.MimeTypeMap,
  android.opengl.Matrix, ...).
- Graphics/resource natives are not registered yet
  (`src/native/android_stub.c` still empty); link fails because
  `g_app_apk_path` has no definition until the app runner exists.

## Checklist

Summary per workstream; the detailed scope lives in WORKSTREAMS.md.

### Done
- [x] VM: interpreter, class linking, auto-stubbing, GC, threads/GIL, monitors, exceptions, reflection, proxies, lambdas
- [x] JNI: JNIEnv/JavaVM, call trampolines (AArch64, x86-64)
- [x] libcore: lang, util (+concurrent/stream/regex/zip), io, nio, text, math, security digests
- [x] VM conformance test vs OpenJDK
- [x] Renderer (src/gfx): paths, strokes, shaders, bitmaps, text, clip masks, PNG encode, image decode
- [x] Resource parsing: AXML, ARSC, multi-package tables; framework-res.apk generator; android.R generator
- [x] Platform API + headless implementation
- [x] Java: util, xmlpull, json, os, content, content.res, content.pm, net.Uri, database (Java side), graphics, drawables
- [x] Natives: AssetManager, Canvas/Paint/Typeface/BitmapFactory/Bitmap
- [x] Toolchain fetch script

### M1 (WS0)
- [ ] Framework compiles (signature-exact skeletons for all referenced classes)
- [ ] android_os.c natives (Log, MessageQueue, PlatformInput, present, display info, IME request, vibrate)
- [ ] Register android natives; remove android_stub.c
- [ ] app_runner.c (APK open, resources, dex list, data dirs, ActivityThread.main)
- [ ] Minimal ActivityThread/Activity/ViewRootImpl/WindowManagerGlobal
- [ ] tests/apps/hello + build script + headless screenshot

### M2
- [ ] WS1 view system core
- [ ] WS2 text and IME
- [ ] WS3 widgets
- [ ] WS4 app model
- [ ] WS13 app test runner with screenshot goldens

### M3
- [ ] WS10 Switch platform, NRO, launcher

### M4
- [ ] WS5 animation
- [ ] WS7 audio/media
- [ ] WS8 OpenGL ES/EGL
- [ ] WS15 sensors and system services

### M5
- [ ] WS9 native loader, bionic shim, NativeActivity

### M6
- [ ] WS6 SQLite natives and storage
- [ ] WS11 networking
- [ ] WS12 VM performance
- [ ] WS14 AndroidX compatibility
- [ ] docs/COMPATIBILITY.md with a tested APK corpus

## Next steps (in order)

1. WS0 (single agent): close the compile gap with signature-exact
   skeletons, write android_os.c, register natives, app runner, hello APK.
   Everything else waits for a green build.
2. Then fan out: WS1 first pushes the public View/ViewGroup API; in
   parallel WS4 (app model), WS10 (Switch backend), WS13 (test infra),
   WS6/WS7/WS9/WS11/WS12/WS15 as agents are available.
3. WS2/WS3/WS5 once View API is in; WS8 once WS10 can present.

## Known issues and gotchas

- Docker Hub rate-limits anonymous pulls (HTTP 429) for the devkitPro
  image; the fetch script retries, or install devkitPro with dkp-pacman.
- The session scratchpad is not persistent; everything needed is fetched
  into `build/toolchains` by `tools/fetch_toolchains.py`.
- Auto-stubbing hides signature mismatches; always compare against
  android.jar (`javap -cp build/toolchains/sdk/android.jar android.view.View`).
- The renderer does no text shaping (no ligatures, complex scripts or
  bidi); fine for Latin/CJK UI text.
- Weak/soft references are cleared on every GC (caches like LruCache are
  unaffected; SoftReference-based image caches will miss more often).
- No finalizers: classes relying on `finalize()` to free native memory
  must be written to not need it (ours keep memory in Java arrays).

## Interface changes log

Record any change to a cross-workstream contract here (date, what, why),
and update ARCHITECTURE.md in the same commit.

- (none yet)
