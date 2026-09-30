# switchapk: plan, status and checklist

This file is the single source of truth for tracking work across sessions.
Update the checklists and "Current state" whenever a chunk of work lands.

## Goal

A Nintendo Switch homebrew (NRO) that loads Android APKs and runs them:
Dalvik bytecode, the Android framework APIs apps call, and native `.so`
libraries (NDK apps and games). No Android OS underneath: everything the
app talks to is reimplemented on top of libnx.

## Architecture

```
 APK (classes*.dex, resources.arsc, res/, assets/, lib/arm64-v8a/*.so)
   |
   v
 +------------------------------------------------------------+
 | Java: app code  ->  our Android framework (java/framework)  |
 |                  ->  our libcore (java/libcore)              |
 +------------------------------------------------------------+
 | Dalvik VM in C (src/vm): interpreter, GC, threads, JNI      |
 +------------------------------------------------------------+
 | Framework natives (src/android, src/native)                 |
 |   resources, graphics (src/gfx software renderer), os/input |
 | Native .so loader + bionic shim (src/nativeloader) [todo]   |
 +------------------------------------------------------------+
 | Platform layer (src/platform): headless host | libnx Switch |
 +------------------------------------------------------------+
```

Key design decisions (keep these unless there is a strong reason):

- **VM**: portable C interpreter, 64-bit register slots, mark/sweep GC with
  precise heap tracing and conservative stack scanning, non-moving objects
  (natives may hold raw array pointers during a call). Real OS threads
  serialized by a GIL; natives that block must release it.
- **Missing framework APIs are auto-stubbed** by the VM (methods return
  default values), so partial framework coverage degrades gracefully.
  Because of this, **method signatures must match the real android.jar
  exactly**, otherwise the app's call resolves to a stub.
- **Real framework resources**: `tools/make_framework_res.py` builds
  `framework-res.apk` from the SDK's `android.jar` (Apache 2.0 AOSP
  resources: Material themes, styles, drawables, layouts), slimmed to
  English/default configs. It is loaded as package 0x01 next to the app's
  0x7f package in one merged `ResTable`. `android/R.java` is generated from
  android.jar by `tools/genr/GenR.java` so ids match exactly.
- **Rendering is software**: `src/gfx` is a stateless AA rasterizer
  (paths, strokes, gradients, bitmaps, text via stb_truetype, clip masks).
  Java `Canvas` keeps matrix/clip/layer state and calls it. Bitmaps store
  pixels in a Java `int[]` (ARGB, unpremultiplied) so the GC owns memory.
- **Window system**: one screen back buffer; every window (activity,
  dialog, popup, toast) draws its view tree into it in z order; full redraw
  only when something is invalidated. Portrait-only apps are letterboxed.
- **Main loop**: `Looper` on the main thread; the main `MessageQueue` waits
  in `nativePollOnce`, which waits on platform events or a wake, then Java
  drains input events and dispatches them.
- **Density**: 720p screen reported as 240 dpi (hdpi, 853x480 dp); docked
  1080p as 360 dpi so the dp size is unchanged. SDK_INT reported as 29.

## Build and test

Host (Linux) build and the VM conformance test:

```
make                       # build/host/switchapk-host + build/java/framework.dex
tests/run_dex_test.sh      # runs tests/dex/VmTest.java on OpenJDK and on switchapk, diffs output
```

`tools/build_java.sh` compiles `java/libcore` then `java/framework` with
javac (-source 8, bootclasspath = our libcore) and dexes with d8
(`--no-desugaring`). It downloads r8 into `build/tools` if missing.

Toolchain assets used so far (downloaded into the session scratchpad,
which is NOT persistent; re-fetch in a new session):

- aapt2: `https://dl.google.com/android/maven2/com/android/tools/build/aapt2/8.13.2-14304508/aapt2-8.13.2-14304508-linux.jar` (unzip `aapt2`)
- android.jar: `https://dl.google.com/android/repository/platform-35_r02.zip` (`*/android.jar`)
- devkitPro (devkitA64, libnx, switch portlibs incl. EGL/GLES/mesa, SDL2,
  ffmpeg, mbedtls, curl, zlib, png): pulled as OCI layers from the
  `devkitpro/devkita64` Docker Hub image without a docker daemon.
- SQLite amalgamation: `https://www.sqlite.org/2024/sqlite-amalgamation-3460100.zip` (planned; fetch at build time into `third_party/sqlite`, not committed).

Framework resources: `python3 tools/make_framework_res.py android.jar build/framework-res.apk`.
Regenerate R: `javac -d /tmp/genr tools/genr/GenR.java && java -cp /tmp/genr GenR android.jar > java/framework/android/R.java`.

Host driver usage: `switchapk-host [--framework f] [--data d] [--trace] [--raw-stdio] [-v|-vv] program.dex|app.apk [MainClass] args`.

## Current state (end of session 2)

Committed and working:

- VM core, libcore (hundreds of java.* classes), JNI, reflection, threads.
  `VmTest` output is byte-identical to OpenJDK.
- `src/gfx`: rasterizer (`raster.c`), text (`font.c`, glyph cache, CJK
  fallback, rotated/skewed text through outlines), image decode/PNG encode
  (`image.c`), AA clip masks. Visual test: `tests/c/gfx_test.c`.
- `src/core/res.c`: multi-table ResTable (`arsc_add`) so framework and app
  packages coexist.
- `src/platform/platform.h` + `platform_headless.c`: event queue, scripted
  input (tap/swipe/key/text/idle/screenshot), PNG screenshots, fake audio.
- `src/android/android_res.c` (AssetManager natives, flattened binary XML
  for Java), `src/android/android_graphics.c` (Canvas/Paint/Typeface/
  BitmapFactory/Bitmap natives). Not yet registered from `natives.c`.

Written in Java but **the framework does not compile yet**: it references
classes that are still to be written (android.view.*, android.text.*,
android.webkit.MimeTypeMap, android.opengl.Matrix, android.media.AudioAttributes,
android.app.*, etc.). The framework build step (`tools/build_java.sh`) will
fail until those exist, which also breaks `make` for the dex step. The C
side builds except for the link error on `g_app_apk_path` (defined by the
future app runner).

Java framework written so far (about 300 files, 30k lines):

- android.util (Log, TypedValue, DisplayMetrics, AttributeSet, sparse
  arrays, ArrayMap/ArraySet, LruCache, Xml, Base64, PathParser, ...)
- org.xmlpull.v1 (interfaces, text pull parser, serializer), org.json
- android.os (Looper/Handler/Message/MessageQueue with platform polling,
  Bundle/Parcel, Build, Process, Environment, AsyncTask, ...)
- android.content (Context, generated ContextWrapper, Intent, IntentFilter,
  ComponentName, SharedPreferences(+Impl, Android XML format),
  ContentResolver with in-process providers, ContentProvider, UriMatcher,
  ClipData, ...), android.content.res (AssetManager, XmlBlock parser,
  Resources + Theme with AOSP attribute precedence, TypedArray,
  ColorStateList, Configuration), android.content.pm (PackageManager API,
  infos)
- android.net.Uri, android.accounts (stubs)
- android.database (+ sqlite Java layer over planned natives)
- android.graphics (Color, Rect(F), Point(F), Matrix, Path, PathMeasure,
  Paint, Canvas with layers and clip masks, Bitmap, BitmapFactory with
  density scaling and 9-patch chunks, NinePatch, Typeface, shaders, color
  filters, path effects, Region, Outline, Picture, RenderNode, Camera,
  ImageDecoder, BitmapRegionDecoder, ...)
- android.graphics.drawable (Drawable with XML inflation, Color, Bitmap,
  NinePatch, Gradient(shape), Shape(+shapes), StateList, AnimatedStateList,
  LevelList, Animation, Layer, Ripple, Transition, Inset, Scale, Clip,
  Rotate, AnimatedRotate, Vector, AnimatedVector (static), AdaptiveIcon,
  Icon, ...)
- android.view.Gravity

## Checklist

### 1. VM and libcore
- [x] Interpreter, class loading, GC, threads/monitors, exceptions
- [x] JNI (full JNIEnv/JavaVM tables, call trampolines AArch64/x86-64)
- [x] libcore (lang, util, util.concurrent, util.regex, util.stream, io, nio, text, math, security.MessageDigest)
- [x] JVM-differential conformance test
- [ ] java.net sockets / HttpURLConnection (curl + mbedtls on Switch)
- [ ] finalizers / Cleaner (not needed so far)
- [ ] performance: JIT-less fast paths, inline caches, quickening

### 2. Android framework (Java + natives)  <- IN PROGRESS
- [x] util, xmlpull, json, os, content, content.res, content.pm, net.Uri
- [x] graphics core + drawables
- [x] resource natives, graphics natives (C)
- [ ] android.view: MotionEvent, KeyEvent, InputEvent, InputDevice, KeyCharacterMap
- [ ] android.view: View, ViewGroup, ViewParent, LayoutParams, MeasureSpec, ViewConfiguration, ViewTreeObserver, FocusFinder, TouchDelegate, ViewStub, ViewOverlay, WindowInsets, AbsSavedState, ViewPropertyAnimator, VelocityTracker, GestureDetector, ScaleGestureDetector
- [ ] android.view: Window, WindowManager(+Impl/Global), ViewRootImpl, Choreographer, Display, Surface, SurfaceHolder, SurfaceView, TextureView, LayoutInflater, ContextThemeWrapper, Menu/MenuItem/MenuInflater, ActionMode
- [ ] com.android.internal.policy.PhoneWindow + DecorView (title/action bar from theme)
- [ ] android.view.animation (Animation, sets, interpolators, AnimationUtils XML)
- [ ] android.view.inputmethod (InputMethodManager -> platform text request, EditorInfo, InputConnection), accessibility stubs
- [ ] android.animation (ValueAnimator, ObjectAnimator, AnimatorSet, evaluators, AnimatorInflater)
- [ ] android.text (TextUtils, Spannable*, spans, Layout/StaticLayout/BoringLayout, TextPaint, Editable, TextWatcher, InputFilter, method.*, Html, format.*)
- [ ] android.widget: FrameLayout, LinearLayout, RelativeLayout, TableLayout, GridLayout, ScrollView, HorizontalScrollView, TextView, EditText, Button, CompoundButton/CheckBox/RadioButton/RadioGroup/Switch/ToggleButton, ImageView/ImageButton, ProgressBar/SeekBar/RatingBar, AdapterView/AbsListView/ListView/GridView, adapters, Spinner, Toast, PopupWindow, Toolbar, Space, ViewFlipper, OverScroller/Scroller, EdgeEffect, Checkable, CheckedTextView
- [ ] android.app: Activity (lifecycle, setContentView, results, menus, back), Application, ActivityThread (manifest parse, providers, launcher activity, activity stack), Dialog, AlertDialog, ProgressDialog, Service, IntentService, NativeActivity, ActionBar, ActivityManager, Notification* stubs, PendingIntent, AlarmManager, Fragment (legacy)
- [ ] Framework natives: android_os.c (Log, MessageQueue poll/wake, input event pull, Surface present, text input, vibrate), register all tables from natives.c
- [ ] App runner (replace src/app/app_stub.c): open APK, load framework-res + resources.arsc, add classes*.dex, data dirs, boot, ActivityThread.main
- [ ] android.database.sqlite natives (bundled SQLite)
- [ ] android.media: SoundPool, MediaPlayer, AudioTrack, AudioManager (mixer in C; decoders: WAV, OGG via stb_vorbis/libvorbis, MP3 via mpg123/ffmpeg on Switch)
- [ ] android.opengl: GLSurfaceView, GLES20/30 bindings, EGL14 + javax.microedition.khronos.*, GLUtils, Matrix (GL via mesa on Switch)
- [ ] android.hardware: SensorManager (Joy-Con IMU), Sensor*; android.provider.Settings; android.webkit stubs (MimeTypeMap, WebView placeholder); android.preference
- [ ] androidx / AppCompat compatibility pass (reflection on hidden fields, AppCompatDelegate)

### 3. Native .so loader
- [ ] ELF64 loader (AArch64 relocs; x86-64 for host tests), dependency resolution, symbol lookup, init arrays
- [ ] bionic shim: libc (FILE, pthread, time, locale, errno, __errno), libm, libdl, liblog, libandroid (ANativeWindow, AAsset*, ALooper, AInputQueue, AConfiguration), libEGL/libGLESv2 forwarding, libOpenSLES / AAudio
- [ ] TLS layout: bionic stack guard at tpidr_el0+40
- [ ] JNI_OnLoad, RegisterNatives, System.loadLibrary from APK lib/arm64-v8a
- [ ] NativeActivity + android_native_app_glue compatible callbacks
- [ ] Switch code memory: svcMapProcessCodeMemory / svcSetProcessMemoryPermission

### 4. Switch backend and NRO
- [ ] platform_switch.c: framebuffer (swizzle ARGB->RGBA), vsync present, HID touch + Joy-Con/Pro buttons -> Android keycodes and gamepad MotionEvents, docked/handheld resize, applet lifecycle (focus, home, exit), swkbd text input, audren/audout audio, rumble
- [ ] EGL window path for GL apps (switch between framebuffer and EGL surface)
- [ ] main_switch.c + launcher menu listing APKs on sdmc:/switch/switchapk/apks
- [ ] romfs: framework.dex, framework-res.apk, fonts (Switch shared font via pl service as fallback)
- [ ] Makefile.switch with devkitA64; produce switchapk.nro

### 5. Samples, tests, docs
- [ ] Sample APKs built with aapt2 + d8 against android.jar: views/layouts, custom Canvas view, ListView, dialogs, SharedPreferences/SQLite, GLSurfaceView, NDK NativeActivity
- [ ] Headless end-to-end tests with scripts and screenshot checks (tests/apps/*.script)
- [ ] README with architecture, build, usage, compatibility notes
- [ ] Commit and push at each milestone

## Next steps (in order)

1. android.view input events, View, ViewGroup, LayoutInflater, Window/PhoneWindow/DecorView, ViewRootImpl + WindowManager + Choreographer.
2. android.text (enough for TextView), android.widget core (FrameLayout, LinearLayout, RelativeLayout, TextView, Button, ImageView, ScrollView, ListView, Toast, CheckBox).
3. android.app (Activity, Application, ActivityThread, Dialog/AlertDialog), android_os.c natives, register all native tables, app runner.
4. Get the Java framework compiling (add minimal stubs for any remaining referenced classes), build a first sample APK, run it headless, iterate on screenshots.
5. Then SQLite, media, GL, native loader, Switch backend, NRO.

## Conventions

- Match Android API signatures exactly (see note on auto-stubbing).
- Framework-internal helpers that must be reachable across packages are
  public but documented as framework-internal.
- Natives read Java fields by name through cached `Field*` lookups
  (`FIELD_INT/FIELD_OBJ/...` in `src/android/android.h`); field renames in
  Java must be mirrored in C.
- No em dashes in docs or messages.
