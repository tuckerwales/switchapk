# Compatibility

How switchapk does on real, open-source APKs, and what it lacks for them.
The corpus is listed in `tests/corpus/corpus.json` (F-Droid builds, pinned by
version code and sha256). APKs are fetched into `build/corpus` and never
committed.

## Running it

```
make                                    # host binary and framework.dex
python3 tools/fetch_toolchains.py sdk   # android.jar and aapt2
tools/corpus.py all                     # fetch, scan, run, report
tools/corpus.py scan pixeldungeon       # one app; ids are in corpus.json
tools/corpus.py scan --apk any.apk      # any APK, summary only
```

GL apps need host Mesa (see DEV_SETUP.md), or they stop at "OpenGL ES
unavailable".

## Proprietary apps

Entries with `"source": "local"` are commercial apps that cannot be
downloaded by the tool or committed. Bring your own copy, from a device you
own:

```
adb shell pm path com.jagex.oldscape.android      # lists base.apk and the splits
adb pull <each path> build/corpus/in/
tools/corpus.py import osrs build/corpus/in/*.apk # or one .apks, .xapk or .apkm bundle
tools/corpus.py scan osrs && tools/corpus.py run osrs && tools/corpus.py report
```

`import` copies the files into `build/corpus/local/<id>/`; `fetch` (and
`all`) then assembles one APK from them, because Play installs are split
and the VM loads a single zip. The base APK (the one with `classes.dex`)
is kept whole; `lib/` and `assets/` entries of the other splits are added,
and their manifests, `resources.arsc` and `res/` are dropped (density and
language splits). Apps nobody has supplied show as "APK not supplied" in
the table and are skipped by every other command.

APKMirror's `.apkm` works when it is a plain zip (the apkm_version 5 file used here was); an
encrypted one fails to open. Compare the splits' signing certificate with
the publisher's before trusting a mirror copy.

These apps update often, so they are not pinned. `fetch` prints the
versionCode and sha256 it assembled and the table names the version that
was scanned; quote both when you record findings. A phone gives only
arm64-v8a libraries, so the smoke run needs an AArch64 Linux host (WS9
loads there) or the console; on an x86-64 host the static scan still
covers the arm64 libraries.

### Old School RuneScape (`osrs`)

Package `com.jagex.oldscape.android`, Play only. Supplied as APKMirror's
bundle of 241.3 (versionCode 24103008, posted 2026-10-06): base plus
arm64-v8a and x86_64 splits, so it also runs on the x86-64 host. All three
APKs carry the same v2/v3 signing certificate, "Jagex Ltd, Cambridge, GB"
(2017-2067, SHA-256 `074caa82...622605a6`); the certificate was compared,
the signatures themselves were not verified. Assembled APK sha256
`c636aec9...2d1816d5870`.

First scan and run (2026-10-10):

- **Shape.** `com.jagex.android.MainActivity` extends AGDK `GameActivity`
  (`com.google.androidgamesdk`, on AppCompat). The game is the 11 MB C++
  library `liblibs.hal.system.osclient.so` (NEEDED: libandroid, libEGL,
  libGLESv3, libOpenSLES, libz, liblog, libdl, libm, libc; no ELF TLS).
  Around it: Play Services (1151 classes), Firebase with Crashlytics
  native libraries, Braze, Play Billing, AppAuth for Jagex Account login
  through a browser redirect, and WebView activities. min/target SDK 26/35.
- **First blocker (run).** `NoClassDefFoundError: android.os.UserManager`
  in `FirebaseInitProvider.onCreate` (androidx.core `UserManagerCompat`),
  before any activity starts. WS15.
- **Native gaps (static).** The game library imports 122 symbols the shim
  lacks. The heavy ones: BSD sockets and DNS (`socket`, `connect`,
  `send`/`recv`, `select`, `poll`, `getaddrinfo`, `setsockopt`,
  `inet_pton`, ...), since the client talks to the game servers from C
  (WS9 with WS11); `sigaction`/`sigaltstack`/`signal`; the locale-aware
  `*_l` and wide-char functions of libc++; `sincos`/`sincosf`,
  `pthread_rwlock_*`, `dl_iterate_phdr`, `vasprintf`, `syslog`; and
  process calls (`fork`, `execvp`, `waitpid`, `kill`) that are probably
  crash-reporting paths. Crashlytics' own libraries add the same names
  plus `epoll`/`eventfd`.
- **Java gaps (static).** 131 missing SDK classes, led by `javax.net.ssl`
  and `java.security.cert` (TLS, WS11), `android.webkit`, `java.time`,
  `java.lang.invoke`, `android.transition`, `android.icu`, autofill and
  window insets; 12 java.* members that throw (`FileChannel.lock`/`map`,
  `Date.toInstant`, `Proxy.address`, `ExecutorService.invokeAll` with
  timeout); 78 android.* members that would be stubbed.

Progress (2026-10-10, same build). The host run now reaches the game
client's own screen: the C++ client renders "Error connecting to server.
Please check your network connection and try again" with OpenGL ES 3 from
its native render thread. What it took, in start-up order:

1. `android.os.UserManager` (USER_SERVICE), for Firebase's init provider.
2. `java.util.logging` (Logger, Level, LogRecord, handlers, LogManager).
3. `AtomicMarkableReference` (and `AtomicStampedReference`).
4. Manifest meta-data references resolved to values: Play Services checks
   `com.google.android.gms.version`, which is `@integer/...`, and was
   getting the resource id.
5. `android.app.ActivityManager` (ACTIVITY_SERVICE) and `ApplicationExitInfo`.
6. `dalvik.system.BaseDexClassLoader.findLibrary`: GameActivity casts the
   app's class loader and asks it for `liblibs.hal.system.osclient.so`.
7. A native ALooper on the main thread under the Java MessageQueue:
   GameActivity's native glue fails with "Unable to retrieve native
   ALooper" otherwise, and its command pipe callbacks run there.
8. `java.vm.vendor` = "The Android Project": the client loads
   `windows/x64/libs.hal.system.osclient` (its desktop build) otherwise,
   then reports "Failed to load native bootstrap library".
9. `shim_posix.c`: sockets, DNS, fcntl, syscall, signals (recorded, not
   installed), locales, UTF-8 multibyte, rwlocks, dl_iterate_phdr and the
   rest. Unresolved native imports went from 154 to 1 (`__libc_init`,
   Crashlytics' trampoline executable, never loaded).
10. `eglSwapBuffers` on a native window from a non-VM thread.

Still logged on the way, none fatal: `javax.net.ssl` missing
(Crashlytics settings fetch, Firebase), `X500Principal` (measurement),
`PendingIntent` without FLAG_IMMUTABLE from measurement code targeting S+,
`onUpdateInternetAvailable` called before the client registered its
natives (the app catches it; fixed 2026-10-17, see below), "requires the Google Play Store". Gone
since: `FileChannel.lock` (Firebase installations' lock) and Firebase
datastore's "Bad file descriptor" (`ParcelFileDescriptor` now holds a
real descriptor, which its native shared counter truncates and maps).

The connection (checked with `switchapk-host -v`, which logs the shim's
`getaddrinfo` and `connect`): the client resolves
`oldschool.config.runescape.com` and connects to port 443 from C. It
links OpenSSL 1.0.2o statically and does its own TLS, so it needs only
sockets, not `javax.net.ssl`. In the cloud container this was run in,
outbound TLS is re-terminated by the sandbox's egress proxy (its own CA)
and the game port 43594 is blocked, so the error screen is the expected
result there and says nothing about switchapk. Next steps:

- Run it on a host with a plain network to see the next stage (the
  config fetch, the world list, the login screen).
- The Switch: native sockets and name lookup now go through
  `shim_bsd.c` (Linux ABI to libnx's FreeBSD one; checked by
  `tests/c/shim_bsd_test.c` and against libnx's headers at compile time),
  so the device can try the connection. Not run on hardware yet.
- Then input, audio (OpenSL ES), and the Jagex Account login (AppAuth
  through a browser redirect).
- The client opens `imgui.ini` with a relative path. Native relative
  paths now fail as under Android's read-only "/" (ENOENT/EROFS), so it
  no longer writes into the host's working directory.

Input, audio and network state (2026-10-17, host). Taps and keys reach
the native client: with `SWITCHAPK_TRACE_NATIVE=Native` a tap logs two
`GameActivity.onTouchEventNative` calls (down and up) and D-pad centre
logs `onKeyDownNative`/`onKeyUpNative`; `setInputConnectionNative`
registers GameTextInput's connection. Typing into the game's own text
fields needs its login screen, so it is untested. Every OpenSL ES import
is provided by the shim (`tests/apps/audio` plays through the same path),
but the client creates no audio engine at the error screen; the shim now
logs `D/opensl: slCreateEngine` and each player's format so a run that
gets further shows it. The `onUpdateInternetAvailable` error above came
from timing: the client registers that native with RegisterNatives after
its first network callback. ConnectivityManager now sends
`onCapabilitiesChanged` again 1.5 s after `onAvailable`, as Android does
once a network validates, and the client's native side then receives the
network state.

To try it on a Linux PC with an ordinary network (no proxy re-terminating
TLS, port 43594 open):

```
tools/run_osrs.sh path/to/osrs.apkm        # or base.apk plus splits, or .apks
tools/run_osrs.sh --again --seconds 180    # rerun the APK assembled last time
```

It checks the build tools and packages, fetches the toolchains, builds,
assembles the APK, checks that it carries libraries for the PC's
architecture (x86_64, or arm64-v8a on an AArch64 machine), runs the
client headless with a screenshot every 5 seconds (or `--script` your
own) and writes `build/osrs-run/summary.txt`: name lookups and
connections, audio setup, browser requests and errors. The host build
has no window, so this shows how far the client gets, not a playable
game.

## How gaps are found

Two passes, both per app, aggregated by `tools/corpus.py report` into the
generated section below (the rest of this file is written by hand).

1. **Static scan** (`build/corpus/scan/<id>.json`). Every class, method and
   field the app's bytecode uses (instruction operands, not annotations) is
   resolved the way the VM resolves it: boot classes first, walking the
   app's own class hierarchy into the framework. Each miss is checked
   against android.jar, so public SDK gaps are separated from hidden-API and
   optional-dependency references. Effects follow the VM: a missing class
   throws NoClassDefFoundError, a missing java.* member throws
   NoSuchMethodError or NoSuchFieldError, a missing android.* method or
   static field is auto-stubbed (the call silently does nothing). Native
   libraries are checked for imports that neither the APK's own libraries
   nor the shim provide (`switchapk-host --shim-symbols`; GL and EGL come
   from the driver) and for ELF TLS. The scan also records bundled
   libraries (AndroidX, Compose, libGDX, Play Services, ...), features
   (WebView, TLS, GLES, dex loading) and manifest facts (SDK levels,
   processes). It is an upper bound: it counts code behind SDK_INT checks
   and paths the app never takes.
2. **Smoke run** (`build/corpus/run/<id>.json`, log and screenshots in
   `build/corpus/work/<id>`). The APK boots headless with a fixed script
   (wait, screenshot, tap, D-pad centre, back). The run records the
   outcome, exception cause chains, `STUB:` lines actually hit, native
   calls that reached a logging stub, imports unresolved at load, Toasts
   and unhandled intents. This is what the start path really needs.

Static ranking says what to build for breadth; the run says what blocks
each app first. Re-run after a fix and the tables move.

## Findings (2026-10-11)

All 13 open apps reach a drawn screen on the host (2 did in session 24):
Pixel Dungeon, Replica Island, Blockinger, Frozen Bubble, Shattered Pixel
Dungeon, Vector Pinball, Mindustry (loading screen), Unciv (first-run
language picker), Andor's Trail, Simple Solitaire, Simon Tatham's Puzzles
(a generated Sudoku), DroidFish (with its Stockfish engine running) and
Simple Calculator. Only the first screen is checked: the smoke script taps
once and presses back. Old School RuneScape (local, see above) draws its own
"Error connecting to server" screen.

What it took, by the first blocker each fix removed:

| Fix | Apps |
|---|---|
| android.preference (ported from AOSP), ListActivity | Andor's Trail, DroidFish, Frozen Bubble, Simple Solitaire, Blockinger |
| Array resources: newer aapt2 gives every item the same key, and bag merging dropped all but one | Simple Solitaire (and any app built with recent aapt2) |
| Default locale in Configuration (was null) | DroidFish |
| java.runtime.name and the other Android runtime properties | Vector Pinball, Unciv (libGDX desktop path) |
| Regex Unicode blocks, scripts and categories | Shattered PD |
| FileChannel lock/map, MappedByteBuffer | Unciv |
| activity-alias launches its target | Simple Calculator |
| Holo action bar decor through the Toolbar decor (ActionBarView is not ported) | Blockinger |
| OverScroller Interpolator constructors (were auto-stubbed, leaving the object empty) | Simple Solitaire (RecyclerView) |
| ActivityManager, DisplayManager, InputManager, MediaScannerConnection | Simple Calculator, Shattered PD, Unciv |
| SAX and javax.xml.parsers | DroidFish (AndroidSVG) |
| sun.misc.Unsafe | Vector Pinball (desugared j$ ConcurrentHashMap) |
| java.util.logging, ServiceLoader, generic signatures | Unciv (SLF4J, Kotlin reflection, libGDX Json) |
| ProcessBuilder runs bionic executables in-process (spawn.c), with native libraries extracted to nativeLibraryDir | DroidFish (Stockfish), Simon Tatham's Puzzles (its generator) |
| Fortified sprintf with an unknown buffer size truncated its output (the shim passed (size_t)-1 to glibc's vsnprintf) | Simon Tatham's Puzzles (empty game descriptions; any native code using sprintf) |
| AutofillManager, CursorAnchorInfo (PR 22), color state lists as drawables | Simon Tatham's Puzzles (Jetpack Compose) |
| Asset descriptors (openFd), PackageInfo.versionName, requestFeature after an early getDecorView | Shattered PD |
| dex_proto_desc stack overflow on a long Kotlin return type | Unciv (VM abort) |
| Native shim: C++ new/delete, sincos, locale and wide chars, syslog, semaphores, rwlocks, dl_iterate_phdr | Vector Pinball, Frozen Bubble, Mindustry |
| java.nio Selector and socket channels | Mindustry (network client at startup) |
| Style parent chains deeper than 20 were cut off, so Material3 themes lost the framework theme (no windowNoTitle, so the titled decor failed to inflate) | Simple Calculator (and any Material3 app) |
| External storage in Android's layout (`/storage/emulated/0/Android/data/<pkg>/files`) | Simple Calculator (Simple Commons cuts paths at `Android/data`) |
| PackageManager.queryIntentContentProviders and the API 33 typed-flag overloads | Simple Calculator (EmojiCompat via Glide) |
| android.bluetooth answering as absent (no adapter), OrientationEventListener (from AOSP) | Frozen Bubble (Bluetooth multiplayer greyed out) |
| A removed window loses focus while still added (apps call setFlags from onWindowFocusChanged(false)) | Shattered PD |
| openFd refuses compressed assets like Android (was a descriptor that could not be mapped) | Mindustry (Arc falls back to streams) |

Still open:

| Gap | Apps | Evidence |
|---|---|---|
| Compose: `View.getContentCaptureSession`, `RenderNode.getMatrix` and `setClipToOutline` auto-stub | Simon Tatham's Puzzles | run: STUB lines |
| Executables share the app's loaded libraries instead of private copies, and cannot be killed (destroy() ends their streams) | DroidFish, Simon Tatham's Puzzles | spawn.c limits; not seen to matter yet |
| Mindustry's map previews fail decoding a save chunk (EOFException in `ShortChunkSaveVersion.readMap`; was `Pixmap.setRaw` index -1024) | Mindustry | run, caught by the app; the same save decodes like the JDK through InflaterInputStream and DataInputStream, single and 8 threads, so the cause is in what the app does around it |
| AppCompat/AndroidX surface: accessibility, android.transition, android.icu, AppOpsManager, window insets | Andor's Trail, DroidFish, Simple Calculator, Simon Tatham's Puzzles, Unciv | static scan, 80 to 190 SDK classes each |

`SWITCHAPK_TRACE_THROW=1` makes the host log every throw with its location,
which finds the cause when an app catches an error and shows its own crash
screen.

<!-- corpus:begin (generated by tools/corpus.py report; edit outside these markers) -->

Generated 2026-10-17 from 14 apps. Static counts include only members and classes that android.jar has (public SDK); hidden-API references are in build/corpus/scan/*.json. Host run is the smoke script (start, tap, D-pad centre, back) on the x86-64 host with Mesa; native counts are for arm64-v8a.

| App | Tier | min/target SDK | Bundled libraries | Missing classes | Missing java.* members (throw) | Missing android.* members (stubbed) | Native ABIs | Host run |
|---|---|---|---|---|---|---|---|---|
| Pixel Dungeon | 0 | 9/20 | framework only | 1 | 0 | 0 | none | draws |
| Replica Island | 0 | 3/8 | framework only | 1 | 0 | 0 | none | draws |
| Blockinger | 0 | 8/17 | support library | 2 | 0 | 3 | none | draws |
| Frozen Bubble | 0 | 12/12 | framework only | 6 | 0 | 1 | arm64, 1 libs, 0 unresolved | draws |
| Shattered Pixel Dungeon | 1 | 21/36 | libgdx, androidx (other), androidx.core | 7 | 0 | 1 | arm64, 2 libs, 0 unresolved | draws |
| Vector Pinball | 1 | 4/37 | libgdx, support library | 6 | 20 | 2 | arm64, 1 libs, 0 unresolved | draws |
| Mindustry | 1 | 21/36 | arc | 13 | 4 | 0 | arm64, 2 libs, 0 unresolved | draws |
| Unciv | 1 | 21/36 | libgdx, androidx (other), androidx.core, kotlinx.coroutines, support library (Kotlin) | 145 | 66 | 109 | arm64, 1 libs, 0 unresolved | draws |
| Andor's Trail | 2 | 21/36 | androidx (other), androidx.core, support library, androidx.fragment (Kotlin) | 130 | 42 | 32 | none | draws |
| Simple Solitaire Collection | 2 | 11/25 | support library | 9 | 0 | 6 | none | draws |
| Simon Tatham's Puzzles | 2 | 21/36 | androidx.compose, androidx (other), androidx.appcompat, material, androidx.core, kotlinx.coroutines, androidx.recyclerview, androidx.constraintlayout, androidx.fragment (Kotlin) | 80 | 0 | 78 | arm64, 3 libs, 0 unresolved | draws |
| DroidFish | 2 | 16/28 | androidx (other), material, androidx.core, androidx.appcompat, androidx.recyclerview, androidx.fragment, androidx.constraintlayout, support library | 69 | 1 | 35 | arm64, 3 libs, 0 unresolved | draws |
| Simple Calculator | 3 | 23/34 | androidx.appcompat, material, androidx (other), androidx.compose, androidx.recyclerview, androidx.fragment, kotlinx.coroutines, androidx.core, rxjava (Kotlin) | 95 | 6 | 87 | none | draws |
| Old School RuneScape (local, 241.3) | 3 | 26/35 | play services, androidx (other), androidx.core, androidx.appcompat, material, firebase, androidx.recyclerview, androidx.fragment, support library, androidx.constraintlayout (Kotlin) | 75 | 4 | 55 | arm64, 6 libs, 0 unresolved | draws |

### Most-needed packages (static, by number of apps)

| Package | Apps |
|---|---|
| android.app | 9 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, shatteredpd, solitaire, unciv, vectorpinball) |
| android.widget | 9 (andorstrail, blockinger, calculator, droidfish, frozenbubble, osrs, sgtpuzzles, solitaire, unciv) |
| android.os | 8 (andorstrail, calculator, droidfish, frozenbubble, osrs, sgtpuzzles, shatteredpd, unciv) |
| android.view | 8 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, solitaire, unciv, vectorpinball) |
| android.view.accessibility | 8 (andorstrail, blockinger, calculator, droidfish, osrs, sgtpuzzles, solitaire, unciv) |
| android.content.pm | 7 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, solitaire, unciv) |
| android.transition | 7 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, solitaire, unciv) |
| java.util.concurrent | 7 (andorstrail, calculator, mindustry, osrs, sgtpuzzles, unciv, vectorpinball) |
| android.icu.text | 6 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, unciv) |
| android.icu.util | 6 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, unciv) |
| android.media | 6 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, unciv) |
| android.net | 6 (andorstrail, blockinger, droidfish, frozenbubble, osrs, unciv) |
| android.text | 6 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, unciv) |
| android.view.inputmethod | 6 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, unciv) |
| android.window | 6 (calculator, osrs, sgtpuzzles, shatteredpd, unciv, vectorpinball) |
| java.net | 6 (andorstrail, droidfish, frozenbubble, mindustry, osrs, unciv) |
| android.appwidget | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.content | 5 (andorstrail, droidfish, osrs, sgtpuzzles, unciv) |
| android.hardware.usb | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.net.wifi | 5 (andorstrail, droidfish, frozenbubble, sgtpuzzles, unciv) |
| android.system | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.telecom | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.telephony | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.view.textclassifier | 5 (calculator, droidfish, osrs, sgtpuzzles, unciv) |
| android.app.admin | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.app.usage | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.hardware | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.hardware.fingerprint | 4 (andorstrail, calculator, droidfish, unciv) |
| android.media.projection | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.media.session | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.media.tv | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.net.nsd | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.net.wifi.p2p | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.nfc | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.print | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.provider | 4 (andorstrail, calculator, osrs, unciv) |
| android.view.contentcapture | 4 (calculator, osrs, sgtpuzzles, unciv) |
| android.view.textservice | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| java.time | 4 (andorstrail, calculator, osrs, unciv) |
| java.util | 4 (calculator, droidfish, mindustry, osrs) |

### Most-needed classes (static)

| Class | Apps |
|---|---|
| android.app.AppOpsManager | 7 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, solitaire, unciv) |
| android.app.UiModeManager | 7 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, solitaire, unciv) |
| android.transition.Transition | 7 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, solitaire, unciv) |
| android.view.accessibility.AccessibilityNodeInfo$CollectionInfo | 7 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, solitaire, unciv) |
| android.view.accessibility.AccessibilityNodeInfo$CollectionItemInfo | 7 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, solitaire, unciv) |
| android.app.KeyguardManager | 6 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, unciv) |
| android.icu.text.DecimalFormatSymbols | 6 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, unciv) |
| android.text.PrecomputedText | 6 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, unciv) |
| android.text.PrecomputedText$Params | 6 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, unciv) |
| android.transition.Transition$TransitionListener | 6 (andorstrail, calculator, droidfish, osrs, solitaire, unciv) |
| android.view.DragAndDropPermissions | 6 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, unciv) |
| android.view.WindowInsetsController | 6 (andorstrail, calculator, osrs, sgtpuzzles, unciv, vectorpinball) |
| android.view.accessibility.AccessibilityNodeInfo$RangeInfo | 6 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, unciv) |
| android.view.inputmethod.InputConnectionWrapper | 6 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, unciv) |
| android.window.OnBackInvokedDispatcher | 6 (calculator, osrs, sgtpuzzles, shatteredpd, unciv, vectorpinball) |
| android.app.AppComponentFactory | 5 (andorstrail, droidfish, sgtpuzzles, shatteredpd, unciv) |
| android.app.SharedElementCallback$OnSharedElementsReadyListener | 5 (andorstrail, calculator, droidfish, osrs, unciv) |
| android.appwidget.AppWidgetManager | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.hardware.usb.UsbManager | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.icu.util.ULocale | 5 (andorstrail, calculator, droidfish, osrs, unciv) |
| android.media.MediaMetadataRetriever | 5 (andorstrail, calculator, osrs, sgtpuzzles, unciv) |
| android.net.TrafficStats | 5 (andorstrail, blockinger, droidfish, osrs, unciv) |
| android.net.wifi.WifiManager | 5 (andorstrail, droidfish, frozenbubble, sgtpuzzles, unciv) |
| android.system.Os | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.system.OsConstants | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.system.StructStat | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.telecom.TelecomManager | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.transition.Transition$EpicenterCallback | 5 (andorstrail, calculator, droidfish, osrs, solitaire) |
| android.transition.TransitionManager | 5 (andorstrail, calculator, droidfish, osrs, solitaire) |
| android.transition.TransitionSet | 5 (andorstrail, calculator, droidfish, osrs, solitaire) |
| android.app.ActivityOptions | 4 (andorstrail, droidfish, osrs, unciv) |
| android.app.DownloadManager | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.app.LocaleManager | 4 (calculator, osrs, sgtpuzzles, unciv) |
| android.app.SharedElementCallback | 4 (andorstrail, droidfish, osrs, unciv) |
| android.app.WallpaperManager | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.app.admin.DevicePolicyManager | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.app.usage.UsageStatsManager | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.content.RestrictionsManager | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.content.pm.LauncherApps | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.hardware.ConsumerIrManager | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |

### Most-needed members (static)

`throws`: java.* member, NoSuchMethodError at the call. `stub`: android.* member, the VM auto-stubs it and the call does nothing.

| Member | Effect | Apps |
|---|---|---|
| android.view.accessibility.AccessibilityNodeInfo.setCollectionInfo(AccessibilityNodeInfo$CollectionInfo) | stub | 7 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, solitaire, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.setCollectionItemInfo(AccessibilityNodeInfo$CollectionItemInfo) | stub | 7 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, solitaire, unciv) |
| android.app.Activity.getOnBackInvokedDispatcher() | stub | 6 (calculator, osrs, sgtpuzzles, shatteredpd, unciv, vectorpinball) |
| android.app.Activity.requestDragAndDropPermissions(DragEvent) | stub | 6 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, unciv) |
| android.view.Window.getInsetsController() | stub | 6 (andorstrail, calculator, osrs, sgtpuzzles, unciv, vectorpinball) |
| android.widget.TextView.getTextMetricsParams() | stub | 6 (andorstrail, calculator, droidfish, osrs, sgtpuzzles, unciv) |
| android.content.pm.PackageManager.queryIntentActivityOptions(ComponentName, Intent[], Intent, int) | stub | 5 (calculator, droidfish, osrs, sgtpuzzles, solitaire) |
| android.view.Window$Callback.onProvideKeyboardShortcuts(List, Menu, int) | stub | 5 (calculator, droidfish, osrs, sgtpuzzles, solitaire) |
| android.view.accessibility.AccessibilityNodeInfo.setRangeInfo(AccessibilityNodeInfo$RangeInfo) | stub | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.widget.PopupWindow.setEnterTransition(Transition) | stub | 5 (calculator, droidfish, osrs, sgtpuzzles, solitaire) |
| android.widget.PopupWindow.setExitTransition(Transition) | stub | 5 (calculator, droidfish, osrs, sgtpuzzles, solitaire) |
| android.app.Activity.setEnterSharedElementCallback(SharedElementCallback) | stub | 4 (andorstrail, droidfish, osrs, unciv) |
| android.app.Activity.setExitSharedElementCallback(SharedElementCallback) | stub | 4 (andorstrail, droidfish, osrs, unciv) |
| android.view.View.getContentCaptureSession() | stub | 4 (calculator, osrs, sgtpuzzles, unciv) |
| android.view.View.getReceiveContentMimeTypes() | stub | 4 (calculator, osrs, sgtpuzzles, unciv) |
| android.view.View.getWindowInsetsController() | stub | 4 (calculator, osrs, sgtpuzzles, unciv) |
| android.view.View.performReceiveContent(ContentInfo) | stub | 4 (calculator, osrs, sgtpuzzles, unciv) |
| android.view.View.setWindowInsetsAnimationCallback(WindowInsetsAnimation$Callback) | stub | 4 (calculator, osrs, sgtpuzzles, unciv) |
| android.view.WindowInsets.getRoundedCorner(int) | stub | 4 (calculator, osrs, sgtpuzzles, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.findAccessibilityNodeInfosByText(String) | stub | 4 (andorstrail, blockinger, droidfish, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.findFocus(int) | stub | 4 (andorstrail, blockinger, droidfish, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.focusSearch(int) | stub | 4 (andorstrail, blockinger, droidfish, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.getBoundsInWindow(Rect) | stub | 4 (calculator, osrs, sgtpuzzles, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.getContainerTitle() | stub | 4 (calculator, osrs, sgtpuzzles, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.getUniqueId() | stub | 4 (calculator, osrs, sgtpuzzles, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.isAccessibilityDataSensitive() | stub | 4 (calculator, osrs, sgtpuzzles, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.isTextSelectable() | stub | 4 (calculator, osrs, sgtpuzzles, unciv) |
| android.view.inputmethod.EditorInfo.setInitialSurroundingSubText(CharSequence, int) | stub | 4 (calculator, osrs, sgtpuzzles, unciv) |
| android.widget.TextView.getTextClassifier() | stub | 4 (calculator, droidfish, osrs, sgtpuzzles) |
| android.widget.TextView.setTextClassifier(TextClassifier) | stub | 4 (calculator, droidfish, osrs, sgtpuzzles) |
| android.app.Dialog.getOnBackInvokedDispatcher() | stub | 3 (calculator, osrs, sgtpuzzles) |
| android.content.pm.PermissionInfo.getProtection() | stub | 3 (andorstrail, droidfish, unciv) |
| android.content.pm.PermissionInfo.getProtectionFlags() | stub | 3 (andorstrail, droidfish, unciv) |
| android.os.ParcelFileDescriptor.fromDatagramSocket(DatagramSocket) | stub | 3 (andorstrail, droidfish, unciv) |
| android.view.View.findOnBackInvokedDispatcher() | stub | 3 (calculator, osrs, sgtpuzzles) |
| android.view.View.setContentCaptureSession(ContentCaptureSession) | stub | 3 (calculator, osrs, unciv) |
| android.view.View.setOnReceiveContentListener(String[], OnReceiveContentListener) | stub | 3 (calculator, osrs, unciv) |
| android.view.ViewStructure.getExtras() | stub | 3 (calculator, sgtpuzzles, unciv) |
| android.view.ViewStructure.newChild(int) | stub | 3 (calculator, osrs, sgtpuzzles) |
| android.view.ViewStructure.setAutofillId(AutofillId) | stub | 3 (calculator, osrs, sgtpuzzles) |

### Hit at runtime (smoke run)

| Signal | Apps |
|---|---|
| exception java.security.cert.CertificateException: java.security.cert.CertPathValidatorException: Trust anchor for certification path not found. | 2 (mindustry, osrs) |
| exception kotlin.reflect.jvm.internal.KotlinReflectionInternalError: Unresolved class: class java.lang.Object (kind = null) | 1 (unciv) |
| stub method android.graphics.RenderNode.getMatrix(Landroid/graphics/Matrix;)V | 1 (sgtpuzzles) |
| stub method android.graphics.RenderNode.setClipToOutline(Z)Z | 1 (sgtpuzzles) |
| stub method android.view.View.getContentCaptureSession()Landroid/view/contentcapture/ContentCaptureSession; | 1 (sgtpuzzles) |

<!-- corpus:end -->
