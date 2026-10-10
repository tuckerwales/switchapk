# Compatibility

How switchapk does on real, open-source APKs, and what it lacks for them.
The corpus is listed in `tests/corpus/corpus.json` (F-Droid builds, pinned by
version code and sha256, plus proprietary apps whose APK each developer
supplies; see "Proprietary apps" below). APKs are fetched into
`build/corpus` and never committed.

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

Still logged on the way, none fatal: `FileChannel.lock` (Firebase
installations' cross-process lock, caught), `javax.net.ssl` missing
(Crashlytics settings fetch, Firebase), `X500Principal` (measurement),
`PendingIntent` without FLAG_IMMUTABLE from measurement code targeting S+,
`onUpdateInternetAvailable` called before the client registered its
natives (the app catches it), "requires the Google Play Store".

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

## Findings (2026-10-09)

First blockers, in the order that unblocks the most apps:

| Gap | Apps blocked at start | Evidence | Workstream |
|---|---|---|---|
| `android.preference` (PreferenceManager, PreferenceActivity, Preference, ListPreference, CheckBoxPreference) is missing | Andor's Trail, DroidFish, Frozen Bubble, Simple Solitaire; referenced by 7 apps | run: NoClassDefFoundError | WS4 |
| `java.runtime.name` is unset, so libGDX's SharedLibraryLoader decides it is on desktop Linux and looks for `libgdx64.so` in the classpath instead of calling System.loadLibrary | Vector Pinball, Unciv (and Shattered PD next) | run: SharedLibraryLoadRuntimeException | WS16 |
| Unicode block classes in regex (`\p{InHiragana}`, `\p{InCJK_Unified_Ideographs}`, ...) | Shattered PD | run: PatternSyntaxException in a static initializer | WS16 |
| `FileChannel.lock()`, `FileChannel.map()` and `MappedByteBuffer` | Unciv; referenced by 8 apps | run: NoSuchMethodError | WS16 |
| Launching an `activity-alias` (the launcher entry is an alias of SplashActivity) | Simple Calculator | run: ClassNotFoundException for the alias name | WS4 |
| `android.app.ListActivity` | Blockinger (Replica Island references it too) | run: NoClassDefFoundError | WS4 |
| Native shim: `sincos`/`sincosf`, C++ `operator new`/`delete` (`_Znwm`, `_ZdlPv`, ... for code linked against the system libstdc++), `__cxa_pure_virtual`, `vasprintf`, the `syslog` family, `dl_iterate_phdr`, `pthread_rwlock_*`, wide-char ctype | Mindustry renders black after `sincos` returns 0; Frozen Bubble, Vector Pinball, DroidFish libraries need the rest | run: "native code called sincos"; static scan | WS9 |
| No game-server connection: in the sandbox where this ran, TLS is re-terminated and port 43594 blocked (environment); the Switch socket layer is new and not run on hardware | Old School RuneScape | run: the client's own "Error connecting to server" screen; `-v` shows connect to oldschool.config.runescape.com:443 | WS9/WS11 |
| Simon Tatham's Puzzles quits after its own "missing a required file" check, probably the `libpuzzlesgen.so` helper it expects in nativeLibraryDir (not yet confirmed) | Simon Tatham's Puzzles | run: Toast, then System.exit | WS9 |

Already working on the host: **Pixel Dungeon** and **Replica Island** reach
their title screens with no stubs hit (Replica Island is GLES1, which
renders on Mesa 25.2). They are the first candidates for screenshot
goldens (WS13) and for a device demo.

Beyond the first blockers, the AndroidX apps (Andor's Trail, DroidFish,
Simple Calculator, Simon Tatham's Puzzles, Unciv) reference 100 to 220
missing SDK classes each. The breadth tables below rank them; the
accessibility (`AccessibilityNodeInfo`, `AccessibilityManager`),
`android.transition`, `android.icu`, `AppOpsManager` and window insets
APIs lead.

<!-- corpus:begin (generated by tools/corpus.py report; edit outside these markers) -->

Generated 2026-10-09 from 13 apps. Static counts include only members and classes that android.jar has (public SDK); hidden-API references are in build/corpus/scan/*.json. Host run is the smoke script (start, tap, D-pad centre, back) on the x86-64 host with Mesa; native counts are for arm64-v8a.

| App | Tier | min/target SDK | Bundled libraries | Missing classes | Missing java.* members (throw) | Missing android.* members (stubbed) | Native ABIs | Host run |
|---|---|---|---|---|---|---|---|---|
| Pixel Dungeon | 0 | 9/20 | framework only | 1 | 0 | 0 | none | draws |
| Replica Island | 0 | 3/8 | framework only | 7 | 0 | 0 | none | draws |
| Blockinger | 0 | 8/17 | support library | 9 | 1 | 8 | none | fails at start: NoClassDefFoundError: android.app.ListActivity |
| Frozen Bubble | 0 | 12/12 | framework only | 14 | 0 | 1 | arm64, 1 libs, 6 unresolved | fails at start: NoClassDefFoundError: android.preference.PreferenceManager |
| Shattered Pixel Dungeon | 1 | 21/36 | libgdx, androidx (other), androidx.core | 13 | 1 | 2 | arm64, 2 libs, 0 unresolved | fails at start: PatternSyntaxException |
| Vector Pinball | 1 | 4/37 | libgdx, support library | 18 | 23 | 3 | arm64, 1 libs, 6 unresolved | fails at start: SharedLibraryLoadRuntimeException: Unable to read file for extraction: libgdx-box2d64.so |
| Mindustry | 1 | 21/36 | arc | 23 | 22 | 0 | arm64, 2 libs, 11 unresolved | blank screen |
| Unciv | 1 | 21/36 | libgdx, androidx (other), androidx.core, kotlinx.coroutines, support library (Kotlin) | 220 | 98 | 130 | arm64, 1 libs, 0 unresolved | fails at start: NoSuchMethodError: java.nio.channels.FileChannel.lock()Ljava/nio/channels/FileLock; |
| Andor's Trail | 2 | 21/36 | androidx (other), androidx.core, support library, androidx.fragment (Kotlin) | 152 | 48 | 44 | none | fails at start: NoClassDefFoundError: android.preference.PreferenceManager |
| Simple Solitaire Collection | 2 | 11/25 | support library | 18 | 0 | 8 | none | fails at start: NoClassDefFoundError: android.preference.PreferenceManager |
| Simon Tatham's Puzzles | 2 | 21/36 | androidx.compose, androidx (other), androidx.appcompat, material, androidx.core, kotlinx.coroutines, androidx.recyclerview, androidx.constraintlayout, androidx.fragment (Kotlin) | 109 | 5 | 91 | arm64, 3 libs, 3 unresolved | exits at start (rc 0) |
| DroidFish | 2 | 16/28 | androidx (other), material, androidx.core, androidx.appcompat, androidx.recyclerview, androidx.fragment, androidx.constraintlayout, support library | 101 | 8 | 44 | arm64, 3 libs, 61 unresolved | fails at start: NoClassDefFoundError: android.preference.PreferenceManager |
| Simple Calculator | 3 | 23/34 | androidx.appcompat, material, androidx (other), androidx.compose, androidx.recyclerview, androidx.fragment, kotlinx.coroutines, androidx.core, rxjava (Kotlin) | 126 | 11 | 105 | none | fails at start: ClassNotFoundException: com.simplemobiletools.calculator.activities.SplashActivity.Grey_black |
| Old School RuneScape (local, 241.3) | 3 | 26/35 | play services, androidx (other), androidx.core, androidx.appcompat, material, firebase, androidx.recyclerview, androidx.fragment, support library, androidx.constraintlayout (Kotlin) | 121 | 12 | 78 | arm64, 6 libs, 1 unresolved | draws |

### Most-needed packages (static, by number of apps)

| Package | Apps |
|---|---|
| android.app | 10 (andorstrail, blockinger, calculator, droidfish, replicaisland, sgtpuzzles, shatteredpd, solitaire, unciv, vectorpinball) |
| android.media | 8 (andorstrail, blockinger, calculator, droidfish, sgtpuzzles, solitaire, unciv, vectorpinball) |
| android.view | 8 (andorstrail, calculator, droidfish, frozenbubble, sgtpuzzles, solitaire, unciv, vectorpinball) |
| android.widget | 8 (andorstrail, blockinger, calculator, droidfish, frozenbubble, sgtpuzzles, solitaire, unciv) |
| java.nio | 8 (andorstrail, calculator, droidfish, mindustry, sgtpuzzles, shatteredpd, unciv, vectorpinball) |
| java.nio.channels | 8 (andorstrail, calculator, droidfish, mindustry, sgtpuzzles, shatteredpd, unciv, vectorpinball) |
| android.os | 7 (andorstrail, calculator, droidfish, frozenbubble, sgtpuzzles, shatteredpd, unciv) |
| android.preference | 7 (andorstrail, blockinger, droidfish, frozenbubble, replicaisland, solitaire, vectorpinball) |
| android.view.accessibility | 7 (andorstrail, blockinger, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.content.pm | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.transition | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.view.inputmethod | 6 (andorstrail, calculator, droidfish, sgtpuzzles, shatteredpd, unciv) |
| java.security | 6 (andorstrail, calculator, droidfish, mindustry, unciv, vectorpinball) |
| java.util.concurrent | 6 (andorstrail, calculator, mindustry, sgtpuzzles, unciv, vectorpinball) |
| android.accessibilityservice | 5 (andorstrail, blockinger, droidfish, sgtpuzzles, unciv) |
| android.appwidget | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.bluetooth | 5 (andorstrail, droidfish, frozenbubble, sgtpuzzles, unciv) |
| android.graphics.fonts | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.hardware.display | 5 (andorstrail, droidfish, sgtpuzzles, shatteredpd, unciv) |
| android.hardware.input | 5 (andorstrail, droidfish, sgtpuzzles, shatteredpd, unciv) |
| android.hardware.usb | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.icu.text | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.icu.util | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.net | 5 (andorstrail, blockinger, droidfish, frozenbubble, unciv) |
| android.net.wifi | 5 (andorstrail, droidfish, frozenbubble, sgtpuzzles, unciv) |

### Most-needed classes (static)

| Class | Apps |
|---|---|
| java.nio.MappedByteBuffer | 8 (andorstrail, calculator, droidfish, mindustry, sgtpuzzles, shatteredpd, unciv, vectorpinball) |
| android.preference.PreferenceActivity | 7 (andorstrail, blockinger, droidfish, frozenbubble, replicaisland, solitaire, vectorpinball) |
| android.preference.PreferenceManager | 7 (andorstrail, blockinger, droidfish, frozenbubble, replicaisland, solitaire, vectorpinball) |
| android.app.AppOpsManager | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.app.UiModeManager | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.preference.Preference | 6 (blockinger, droidfish, frozenbubble, replicaisland, solitaire, vectorpinball) |
| android.transition.Transition | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.view.accessibility.AccessibilityNodeInfo$CollectionInfo | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.view.accessibility.AccessibilityNodeInfo$CollectionItemInfo | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.accessibilityservice.AccessibilityServiceInfo | 5 (andorstrail, blockinger, droidfish, sgtpuzzles, unciv) |
| android.app.ActivityManager | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.app.AppComponentFactory | 5 (andorstrail, droidfish, sgtpuzzles, shatteredpd, unciv) |
| android.app.KeyguardManager | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.appwidget.AppWidgetManager | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.graphics.fonts.FontVariationAxis | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.hardware.display.DisplayManager | 5 (andorstrail, droidfish, sgtpuzzles, shatteredpd, unciv) |
| android.hardware.input.InputManager | 5 (andorstrail, droidfish, sgtpuzzles, shatteredpd, unciv) |
| android.hardware.usb.UsbManager | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.icu.text.DecimalFormatSymbols | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.net.wifi.WifiManager | 5 (andorstrail, droidfish, frozenbubble, sgtpuzzles, unciv) |
| android.system.Os | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.system.OsConstants | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.system.StructStat | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.telecom.TelecomManager | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.text.PrecomputedText | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |

### Most-needed members (static)

`throws`: java.* member, NoSuchMethodError at the call. `stub`: android.* member, the VM auto-stubs it and the call does nothing.

| Member | Effect | Apps |
|---|---|---|
| java.nio.channels.FileChannel.map(FileChannel$MapMode, long, long) | throws | 8 (andorstrail, calculator, droidfish, mindustry, sgtpuzzles, shatteredpd, unciv, vectorpinball) |
| android.view.accessibility.AccessibilityManager.getEnabledAccessibilityServiceList(int) | stub | 6 (andorstrail, blockinger, calculator, droidfish, sgtpuzzles, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.setCollectionInfo(AccessibilityNodeInfo$CollectionInfo) | stub | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.setCollectionItemInfo(AccessibilityNodeInfo$CollectionItemInfo) | stub | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.widget.OverScroller.&lt;init&gt;(Context, Interpolator) | stub | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.app.Activity.getOnBackInvokedDispatcher() | stub | 5 (calculator, sgtpuzzles, shatteredpd, unciv, vectorpinball) |
| android.app.Activity.requestDragAndDropPermissions(DragEvent) | stub | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.view.Window.getInsetsController() | stub | 5 (andorstrail, calculator, sgtpuzzles, unciv, vectorpinball) |
| android.view.accessibility.AccessibilityNodeInfo.setRangeInfo(AccessibilityNodeInfo$RangeInfo) | stub | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.widget.TextView.getTextMetricsParams() | stub | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.app.Notification$Builder.setContent(RemoteViews) | stub | 4 (andorstrail, blockinger, droidfish, unciv) |
| android.app.Notification$Builder.setTicker(CharSequence, RemoteViews) | stub | 4 (andorstrail, blockinger, droidfish, unciv) |
| android.content.pm.PackageManager.queryIntentActivityOptions(ComponentName, Intent[], Intent, int) | stub | 4 (calculator, droidfish, sgtpuzzles, solitaire) |
| android.media.AudioManager.playSoundEffect(int) | stub | 4 (calculator, droidfish, sgtpuzzles, solitaire) |
| android.view.Window$Callback.onProvideKeyboardShortcuts(List, Menu, int) | stub | 4 (calculator, droidfish, sgtpuzzles, solitaire) |
| android.view.accessibility.AccessibilityManager.getInstalledAccessibilityServiceList() | stub | 4 (andorstrail, blockinger, droidfish, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.findAccessibilityNodeInfosByText(String) | stub | 4 (andorstrail, blockinger, droidfish, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.findFocus(int) | stub | 4 (andorstrail, blockinger, droidfish, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.focusSearch(int) | stub | 4 (andorstrail, blockinger, droidfish, unciv) |
| android.widget.PopupWindow.setEnterTransition(Transition) | stub | 4 (calculator, droidfish, sgtpuzzles, solitaire) |
| android.widget.PopupWindow.setExitTransition(Transition) | stub | 4 (calculator, droidfish, sgtpuzzles, solitaire) |
| android.app.Notification.contentView | throws | 4 (andorstrail, blockinger, droidfish, unciv) |
| android.app.Activity.setEnterSharedElementCallback(SharedElementCallback) | stub | 3 (andorstrail, droidfish, unciv) |
| android.app.Activity.setExitSharedElementCallback(SharedElementCallback) | stub | 3 (andorstrail, droidfish, unciv) |
| android.app.Notification$Builder.setCustomBigContentView(RemoteViews) | stub | 3 (andorstrail, droidfish, unciv) |

### Unresolved native imports (static)

| Symbol | Apps |
|---|---|
| sincosf | 3 (mindustry, sgtpuzzles, vectorpinball) |
| _ZdaPv | 2 (frozenbubble, vectorpinball) |
| _ZdlPv | 2 (frozenbubble, vectorpinball) |
| _Znam | 2 (frozenbubble, vectorpinball) |
| _Znwm | 2 (frozenbubble, vectorpinball) |
| android_set_abort_message | 2 (droidfish, mindustry) |
| closelog | 2 (droidfish, mindustry) |
| dl_iterate_phdr | 2 (droidfish, mindustry) |
| openlog | 2 (droidfish, mindustry) |
| sincos | 2 (mindustry, sgtpuzzles) |
| syslog | 2 (droidfish, mindustry) |
| vasprintf | 2 (droidfish, mindustry) |
| __ctype_get_mb_cur_max | 1 (droidfish) |
| __cxa_pure_virtual | 1 (vectorpinball) |
| __libc_init | 1 (sgtpuzzles) |
| btowc | 1 (droidfish) |
| chmod | 1 (droidfish) |
| freelocale | 1 (droidfish) |
| getrlimit | 1 (droidfish) |
| isblank | 1 (frozenbubble) |
| iswalpha | 1 (droidfish) |
| iswblank | 1 (droidfish) |
| iswcntrl | 1 (droidfish) |
| iswdigit | 1 (droidfish) |
| iswlower | 1 (droidfish) |

### Hit at runtime (smoke run)

| Signal | Apps |
|---|---|
| exception java.lang.NoClassDefFoundError: android.preference.PreferenceManager | 4 (andorstrail, droidfish, frozenbubble, solitaire) |
| exception com.badlogic.gdx.utils.SharedLibraryLoadRuntimeException: Unable to read file for extraction: libgdx-box2d64.so | 1 (vectorpinball) |
| exception com.badlogic.gdx.utils.SharedLibraryLoadRuntimeException: Unable to read file for extraction: libgdx64.so | 1 (unciv) |
| exception java.lang.ClassNotFoundException: com.simplemobiletools.calculator.activities.SplashActivity.Grey_black | 1 (calculator) |
| exception java.lang.NoClassDefFoundError: android.app.ListActivity | 1 (blockinger) |
| exception java.lang.NoSuchMethodError: java.nio.channels.FileChannel.lock()Ljava/nio/channels/FileLock; | 1 (unciv) |
| exception java.util.regex.PatternSyntaxException | 1 (shatteredpd) |
| native call sincos | 1 (mindustry) |

<!-- corpus:end -->
