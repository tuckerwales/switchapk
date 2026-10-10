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

## Findings (2026-10-10, session 25)

12 of the 13 apps reach a drawn screen on the host (2 did in session 24):
Pixel Dungeon, Replica Island, Blockinger, Frozen Bubble, Shattered Pixel
Dungeon, Vector Pinball, Mindustry (loading screen), Unciv (first-run
language picker), Andor's Trail, Simple Solitaire, DroidFish and Simple
Calculator. Only the first screen is checked: the smoke script taps once
and presses back.

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
| ProcessBuilder (fails like a refused exec) | DroidFish (its engine runs as a process; the board works, analysis does not) |
| Asset descriptors (openFd), PackageInfo.versionName, requestFeature after an early getDecorView | Shattered PD |
| dex_proto_desc stack overflow on a long Kotlin return type | Unciv (VM abort) |
| Native shim: C++ new/delete, sincos, locale and wide chars, syslog, semaphores, rwlocks, dl_iterate_phdr | Vector Pinball, Frozen Bubble, Mindustry |
| java.nio Selector and socket channels | Mindustry (network client at startup) |
| Style parent chains deeper than 20 were cut off, so Material3 themes lost the framework theme (no windowNoTitle, so the titled decor failed to inflate) | Simple Calculator (and any Material3 app) |
| External storage in Android's layout (`/storage/emulated/0/Android/data/<pkg>/files`) | Simple Calculator (Simple Commons cuts paths at `Android/data`) |
| PackageManager.queryIntentContentProviders and the API 33 typed-flag overloads | Simple Calculator (EmojiCompat via Glide) |
| openFd refuses compressed assets like Android (was a descriptor that could not be mapped) | Mindustry (Arc falls back to streams) |

Still open:

| Gap | Apps | Evidence |
|---|---|---|
| Simon Tatham's Puzzles runs `libpuzzlesgen.so` as an executable and quits when it cannot ("missing a required file") | Simon Tatham's Puzzles | run: Toast, then exit; there are no processes on the console |
| Mindustry's map previews fail decoding a save chunk (EOFException in `ShortChunkSaveVersion.readMap`; was `Pixmap.setRaw` index -1024) | Mindustry | run, caught by the app; the same save decodes like the JDK through InflaterInputStream and DataInputStream, single and 8 threads, so the cause is in what the app does around it |
| android.bluetooth is missing (caught at start) | Frozen Bubble | run |
| AppCompat/AndroidX surface: accessibility, android.transition, android.icu, AppOpsManager, window insets | Andor's Trail, DroidFish, Simple Calculator, Simon Tatham's Puzzles, Unciv | static scan, 80 to 190 SDK classes each |

`SWITCHAPK_TRACE_THROW=1` makes the host log every throw with its location,
which finds the cause when an app catches an error and shows its own crash
screen.

<!-- corpus:begin (generated by tools/corpus.py report; edit outside these markers) -->

Generated 2026-10-10 from 13 apps. Static counts include only members and classes that android.jar has (public SDK); hidden-API references are in build/corpus/scan/*.json. Host run is the smoke script (start, tap, D-pad centre, back) on the x86-64 host with Mesa; native counts are for arm64-v8a.

| App | Tier | min/target SDK | Bundled libraries | Missing classes | Missing java.* members (throw) | Missing android.* members (stubbed) | Native ABIs | Host run |
|---|---|---|---|---|---|---|---|---|
| Pixel Dungeon | 0 | 9/20 | framework only | 1 | 0 | 0 | none | draws |
| Replica Island | 0 | 3/8 | framework only | 1 | 0 | 0 | none | draws |
| Blockinger | 0 | 8/17 | support library | 3 | 1 | 8 | none | draws |
| Frozen Bubble | 0 | 12/12 | framework only | 11 | 0 | 1 | arm64, 1 libs, 0 unresolved | draws |
| Shattered Pixel Dungeon | 1 | 21/36 | libgdx, androidx (other), androidx.core | 7 | 0 | 2 | arm64, 2 libs, 0 unresolved | draws |
| Vector Pinball | 1 | 4/37 | libgdx, support library | 9 | 21 | 3 | arm64, 1 libs, 0 unresolved | draws |
| Mindustry | 1 | 21/36 | arc | 14 | 4 | 0 | arm64, 2 libs, 0 unresolved | draws |
| Unciv | 1 | 21/36 | libgdx, androidx (other), androidx.core, kotlinx.coroutines, support library (Kotlin) | 190 | 70 | 129 | arm64, 1 libs, 0 unresolved | draws |
| Andor's Trail | 2 | 21/36 | androidx (other), androidx.core, support library, androidx.fragment (Kotlin) | 143 | 45 | 43 | none | draws |
| Simple Solitaire Collection | 2 | 11/25 | support library | 9 | 0 | 7 | none | draws |
| Simon Tatham's Puzzles | 2 | 21/36 | androidx.compose, androidx (other), androidx.appcompat, material, androidx.core, kotlinx.coroutines, androidx.recyclerview, androidx.constraintlayout, androidx.fragment (Kotlin) | 99 | 3 | 90 | arm64, 3 libs, 1 unresolved | exits at start (rc 0) |
| DroidFish | 2 | 16/28 | androidx (other), material, androidx.core, androidx.appcompat, androidx.recyclerview, androidx.fragment, androidx.constraintlayout, support library | 80 | 4 | 43 | arm64, 3 libs, 0 unresolved | draws |
| Simple Calculator | 3 | 23/34 | androidx.appcompat, material, androidx (other), androidx.compose, androidx.recyclerview, androidx.fragment, kotlinx.coroutines, androidx.core, rxjava (Kotlin) | 117 | 9 | 103 | none | draws |

### Most-needed packages (static, by number of apps)

| Package | Apps |
|---|---|
| android.app | 9 (andorstrail, blockinger, calculator, droidfish, sgtpuzzles, shatteredpd, solitaire, unciv, vectorpinball) |
| android.media | 8 (andorstrail, blockinger, calculator, droidfish, sgtpuzzles, solitaire, unciv, vectorpinball) |
| android.view | 8 (andorstrail, calculator, droidfish, frozenbubble, sgtpuzzles, solitaire, unciv, vectorpinball) |
| android.widget | 8 (andorstrail, blockinger, calculator, droidfish, frozenbubble, sgtpuzzles, solitaire, unciv) |
| android.os | 7 (andorstrail, calculator, droidfish, frozenbubble, sgtpuzzles, shatteredpd, unciv) |
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
| android.hardware.usb | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.icu.text | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.icu.util | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.net | 5 (andorstrail, blockinger, droidfish, frozenbubble, unciv) |
| android.net.wifi | 5 (andorstrail, droidfish, frozenbubble, sgtpuzzles, unciv) |
| android.system | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.telecom | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.telephony | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.text | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.window | 5 (calculator, sgtpuzzles, shatteredpd, unciv, vectorpinball) |
| java.io | 5 (andorstrail, calculator, droidfish, unciv, vectorpinball) |
| java.net | 5 (andorstrail, droidfish, frozenbubble, mindustry, unciv) |
| android.app.admin | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.app.usage | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.content | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.hardware | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.hardware.fingerprint | 4 (andorstrail, calculator, droidfish, unciv) |
| android.media.projection | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.media.session | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.media.tv | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.net.nsd | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.net.wifi.p2p | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.nfc | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.os.storage | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.print | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |

### Most-needed classes (static)

| Class | Apps |
|---|---|
| android.app.AppOpsManager | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.app.UiModeManager | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.transition.Transition | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.view.accessibility.AccessibilityNodeInfo$CollectionInfo | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.view.accessibility.AccessibilityNodeInfo$CollectionItemInfo | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.accessibilityservice.AccessibilityServiceInfo | 5 (andorstrail, blockinger, droidfish, sgtpuzzles, unciv) |
| android.app.AppComponentFactory | 5 (andorstrail, droidfish, sgtpuzzles, shatteredpd, unciv) |
| android.app.KeyguardManager | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.appwidget.AppWidgetManager | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.graphics.fonts.FontVariationAxis | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.hardware.usb.UsbManager | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.icu.text.DecimalFormatSymbols | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.net.wifi.WifiManager | 5 (andorstrail, droidfish, frozenbubble, sgtpuzzles, unciv) |
| android.system.Os | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.system.OsConstants | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.system.StructStat | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.telecom.TelecomManager | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.text.PrecomputedText | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.text.PrecomputedText$Params | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.transition.Transition$TransitionListener | 5 (andorstrail, calculator, droidfish, solitaire, unciv) |
| android.view.DragAndDropPermissions | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.view.WindowInsetsController | 5 (andorstrail, calculator, sgtpuzzles, unciv, vectorpinball) |
| android.view.accessibility.AccessibilityNodeInfo$RangeInfo | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.view.inputmethod.InputConnectionWrapper | 5 (andorstrail, calculator, droidfish, sgtpuzzles, unciv) |
| android.window.OnBackInvokedDispatcher | 5 (calculator, sgtpuzzles, shatteredpd, unciv, vectorpinball) |
| java.io.ObjectInputStream | 5 (andorstrail, calculator, droidfish, unciv, vectorpinball) |
| java.io.ObjectOutputStream | 5 (andorstrail, calculator, droidfish, unciv, vectorpinball) |
| android.app.DownloadManager | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.app.SharedElementCallback$OnSharedElementsReadyListener | 4 (andorstrail, calculator, droidfish, unciv) |
| android.app.WallpaperManager | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.app.admin.DevicePolicyManager | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.app.usage.UsageStatsManager | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.bluetooth.BluetoothManager | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.content.RestrictionsManager | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.content.pm.LauncherApps | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.hardware.ConsumerIrManager | 4 (andorstrail, droidfish, sgtpuzzles, unciv) |
| android.hardware.fingerprint.FingerprintManager | 4 (andorstrail, calculator, droidfish, unciv) |
| android.hardware.fingerprint.FingerprintManager$AuthenticationCallback | 4 (andorstrail, calculator, droidfish, unciv) |
| android.hardware.fingerprint.FingerprintManager$AuthenticationResult | 4 (andorstrail, calculator, droidfish, unciv) |
| android.hardware.fingerprint.FingerprintManager$CryptoObject | 4 (andorstrail, calculator, droidfish, unciv) |

### Most-needed members (static)

`throws`: java.* member, NoSuchMethodError at the call. `stub`: android.* member, the VM auto-stubs it and the call does nothing.

| Member | Effect | Apps |
|---|---|---|
| android.view.accessibility.AccessibilityManager.getEnabledAccessibilityServiceList(int) | stub | 6 (andorstrail, blockinger, calculator, droidfish, sgtpuzzles, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.setCollectionInfo(AccessibilityNodeInfo$CollectionInfo) | stub | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
| android.view.accessibility.AccessibilityNodeInfo.setCollectionItemInfo(AccessibilityNodeInfo$CollectionItemInfo) | stub | 6 (andorstrail, calculator, droidfish, sgtpuzzles, solitaire, unciv) |
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
| android.app.Notification$Builder.setCustomContentView(RemoteViews) | stub | 3 (andorstrail, droidfish, unciv) |
| android.app.Notification$Builder.setCustomHeadsUpContentView(RemoteViews) | stub | 3 (andorstrail, droidfish, unciv) |
| android.content.pm.PermissionInfo.getProtection() | stub | 3 (andorstrail, droidfish, unciv) |
| android.content.pm.PermissionInfo.getProtectionFlags() | stub | 3 (andorstrail, droidfish, unciv) |
| android.graphics.fonts.Font$Builder.&lt;init&gt;(ParcelFileDescriptor) | stub | 3 (calculator, sgtpuzzles, unciv) |
| android.graphics.fonts.Font$Builder.setFontVariationSettings(String) | stub | 3 (calculator, sgtpuzzles, unciv) |
| android.graphics.fonts.Font$Builder.setTtcIndex(int) | stub | 3 (calculator, sgtpuzzles, unciv) |
| android.graphics.fonts.Font.getStyle() | stub | 3 (calculator, sgtpuzzles, unciv) |
| android.graphics.fonts.FontFamily.getFont(int) | stub | 3 (calculator, sgtpuzzles, unciv) |
| android.os.ParcelFileDescriptor.fromDatagramSocket(DatagramSocket) | stub | 3 (andorstrail, droidfish, unciv) |
| android.view.View.getAutofillId() | stub | 3 (calculator, sgtpuzzles, unciv) |
| android.view.View.getContentCaptureSession() | stub | 3 (calculator, sgtpuzzles, unciv) |
| android.view.View.getReceiveContentMimeTypes() | stub | 3 (calculator, sgtpuzzles, unciv) |
| android.view.View.getWindowInsetsController() | stub | 3 (calculator, sgtpuzzles, unciv) |
| android.view.View.performReceiveContent(ContentInfo) | stub | 3 (calculator, sgtpuzzles, unciv) |
| android.view.View.setWindowInsetsAnimationCallback(WindowInsetsAnimation$Callback) | stub | 3 (calculator, sgtpuzzles, unciv) |
| android.view.ViewStructure.getExtras() | stub | 3 (calculator, sgtpuzzles, unciv) |

### Unresolved native imports (static)

| Symbol | Apps |
|---|---|
| __libc_init | 1 (sgtpuzzles) |

### Hit at runtime (smoke run)

| Signal | Apps |
|---|---|
| exception java.io.EOFException | 1 (mindustry) |
| exception java.lang.IllegalArgumentException: View=DecorView@3507983 not attached to window manager | 1 (shatteredpd) |
| exception java.lang.NoClassDefFoundError: android.bluetooth.BluetoothAdapter | 1 (frozenbubble) |
| stub method android.media.AudioManager.unloadSoundEffects()V | 1 (vectorpinball) |

<!-- corpus:end -->
