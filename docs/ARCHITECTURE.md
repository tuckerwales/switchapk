# switchapk architecture

Technical reference for every subsystem: what it does, where it lives, and
the contracts other subsystems rely on. Read this before changing an
interface between components. Status of each piece is tracked in
`docs/PLAN.md`; who is working on what is in `docs/WORKSTREAMS.md`.

## 1. Big picture

switchapk runs unmodified Android APKs as a Nintendo Switch homebrew
application (NRO). There is no Android OS underneath. Instead we provide:

1. a Dalvik bytecode VM (C),
2. a Java class library (`java.*`, our own libcore),
3. a reimplementation of the Android framework (`android.*`) in Java with
   C natives,
4. a loader for the APK's native libraries plus a bionic/NDK shim,
5. a platform layer that maps everything onto libnx (Switch) or onto a
   headless host implementation used for development and tests.

```
+---------------------------------------------------------------------------+
| APK: classes*.dex  resources.arsc  res/  assets/  lib/arm64-v8a/*.so       |
+---------------------------------------------------------------------------+
| app Java code                | app native code (.so, JNI / NativeActivity) |
+------------------------------+--------------------------------------------+
| android.* framework (java/framework)  | NDK shim: libc/libm/libdl/liblog/  |
| java.* libcore       (java/libcore)   | libandroid/libEGL/libGLESv2/       |
|                                       | OpenSLES  (src/nativeloader) [todo]|
+---------------------------------------+-----------------------------------+
| Dalvik VM (src/vm): interpreter, class linker, GC, threads/GIL, JNI       |
+---------------------------------------------------------------------------+
| framework natives: src/native (java.*), src/android (android.*)           |
| 2D renderer: src/gfx       resource/zip/dex parsing: src/core             |
+---------------------------------------------------------------------------+
| platform: src/platform/platform.h                                         |
|   platform_headless.c (host tests)  |  platform_switch.c (libnx) [todo]   |
+---------------------------------------------------------------------------+
```

Process model: one host process runs exactly one APK. The Switch launcher
(todo) lets the user pick an APK, then starts the runtime for it. Returning
to the launcher restarts the NRO (simplest way to get a clean VM).

## 2. Source tree

```
src/core/        common.h (logging, containers, UTF conversion), util.c,
                 zip.c (APK reader), dex.c (DEX parser), res.c (AXML + ARSC)
src/vm/          vm.h (public VM API), class.c (loading/linking/resolution,
                 stubbing), interp.c (interpreter), heap.c (GC), thread.c
                 (threads, GIL, monitors), exception.c, boot.c (natives
                 registry, VM boot), jni.c + jni_types.h (JNI), callstub.S +
                 nativecall.h (native call trampoline)
src/native/      natives for java.* (java_lang.c, java_io.c incl. Android
                 path mapping, java_reflect.c, java_misc.c), natives.h
                 (argument/return macros), natives.c (registers all tables),
                 android_stub.c (placeholder, replaced by src/android)
src/android/     natives for android.*: android.h (shared helpers),
                 android_res.c, android_graphics.c, [todo] android_os.c,
                 android_media.c, android_opengl.c, android_sqlite.c
src/gfx/         gfx.h, raster.c (AA rasterizer/compositor), font.c
                 (stb_truetype text), image.c (stb_image decode, PNG encode)
src/platform/    platform.h, platform_headless.c, [todo] platform_switch.c
src/nativeloader/ loader_stub.c (placeholder), [todo] ELF loader + bionic shim
src/app/         main_host.c (host driver), app_stub.c (placeholder for the
                 APK runner), [todo] app_runner.c, main_switch.c
java/libcore/    java.*, javax.*, sun.*, libcore.*, dalvik.* classes
java/framework/  android.*, com.android.internal.*, org.json, org.xmlpull
third_party/     stb (image, truetype), sqlite (fetched, gitignored)
tools/           build_java.sh, fetch_toolchains.py, make_framework_res.py,
                 genr/GenR.java (android.R generator), dexdump.py,
                 build_apk.sh (test APKs), api_check.py (API diff vs android.jar)
tests/           run_dex_test.sh + tests/dex (VM conformance vs OpenJDK),
                 tests/c (native unit/visual tests), tests/apps (sample APKs
                 with scripts and screenshot checks; shotlib.py)
docs/            this documentation
```

## 3. Build outputs

| Output | Produced by | Notes |
|---|---|---|
| `build/host/switchapk-host` | `make` | host driver, headless platform |
| `build/java/framework.dex` | `tools/build_java.sh` (via `make`) | libcore + framework in one dex |
| `build/toolchains/framework-res.apk` | `tools/fetch_toolchains.py sdk` | framework resources from SDK android.jar |
| `switchapk.nro` | `make -f Makefile.switch` [todo] | NRO with romfs: framework.dex, framework-res.apk, fonts |

The Java side is compiled with `javac -source 8 -target 8` against our own
libcore as bootclasspath (never against the JDK), then dexed with d8
`--no-desugaring` (our VM implements invoke-custom/lambdas directly, and
`--min-api 24`).

## 4. The VM (src/vm)

### 4.1 Values and objects
- Dalvik registers are 64-bit slots (`uint64_t`). References are host
  pointers stored in one slot. Wide values (long/double) use the first slot
  of the pair; the second slot is ignored.
- `Object { Class *clazz; uint32 monitor; uint32 gcflags; }` followed by
  fields at `Field.offset`. `ArrayObject` adds `length` and a `data`
  pointer (normally pointing at inline storage, but may point at external
  memory, used by `NewDirectByteBuffer`). Always access array elements via
  `ARRAY_DATA(a, type)`.
- Booleans are stored as 1 byte, chars/shorts as 2 bytes, int/float 4,
  long/double/references 8.
- Strings are `java.lang.String` objects with a UTF-16 `char[]` value;
  helpers in vm.h: `vm_new_string_utf8`, `vm_string_to_utf8` (malloc'd),
  `vm_string_chars`, `vm_string_length`.

### 4.2 Class loading
- Two dex lists: boot (`framework.dex`) and app (`classes*.dex`).
  `vm_find_class` looks in boot first, then app (parent-first, like
  Android's boot class loader). Classes from boot dex carry `CF_BOOT`.
- **Auto-stubbing**: if app code references a method or static field that
  does not exist on a boot class in `android.*`, `com.android.*` or
  `dalvik.*`, the VM synthesizes it (methods return zero/null/false, fields
  read as zero) and logs `STUB: missing framework method ...`. This keeps
  apps running while the framework is incomplete, and it means a wrong
  signature in our framework silently turns a real call into a no-op.
  Missing classes are NOT stubbed (they throw NoClassDefFoundError).
  Grep app logs for `STUB:` to find coverage gaps.

### 4.3 Interpreter
- Non-recursive switch interpreter over a per-thread register stack
  (`rstack`). `vm_interpret(t, m, args)` runs until the entry frame
  returns. Java-to-Java invokes push frames without C recursion; calls into
  natives and back into Java (`vm_call*`) recurse on the C stack.
- Safepoints at backward branches and invokes: GC and GIL yield happen only
  there (and on allocation failure).
- StackOverflowError: a reserve zone on the register stack lets the error
  be constructed without overflowing again (`handling_soe`).

### 4.4 Garbage collector
- Mark/sweep, non-moving. Roots: static fields, interned strings, global
  JNI refs, explicit roots (`vm_add_root(&slot)`), each thread's register
  stack (precisely by frame), JNI local refs, and **conservative scanning
  of every thread's C stack** plus callee-saved registers captured when the
  thread parks.
- Consequences for native code:
  - Object pointers held in C locals of a native are safe (conservative
    scan), including across calls back into Java.
  - Objects stored in malloc'd C memory or C globals are NOT roots: use
    `vm_add_root` / `vm_remove_root` or JNI global refs.
  - Raw pointers into arrays (`ARRAY_DATA`) stay valid while the array is
    reachable: objects never move.
- GC is triggered at safepoints when `heap_bytes > heap_threshold`
  (threshold = 2x live after last GC, minimum 24 MB) and on malloc failure.
- Weak/soft references are cleared on every GC (no soft-reference
  retention policy yet). No finalizers.

### 4.5 Threads and the GIL
- Every `java.lang.Thread` is a real pthread. A FIFO ticket GIL serializes
  execution of Java code; the running thread yields after a 4 ms slice at
  a safepoint if others wait.
- **Rule for natives**: any native that may block (I/O waits, sleeps,
  condition waits, presenting a frame, waiting for input, long decoding)
  must call `vm_gil_release(t)` before and `vm_gil_acquire(t)` after, and
  must not touch Java objects in between (other threads may run, GC may
  run; arrays are not moved, so raw array pointers obtained before release
  remain valid if the array stays reachable from a root such as the
  caller's frame).
- JNI native methods (from app `.so` files) run with the GIL released, like
  on Android where JNI code runs concurrently. JNI functions reacquire it.
- Monitors: `synchronized`, `wait/notify` implemented with per-monitor
  condition variables.

### 4.6 Internal natives
- Signature: `void fn(VMThread *t, uint64_t *args, JValue *ret)` declared
  with `NATIVE(fn)` from `src/native/natives.h`.
- Arguments: `args[0]` is `this` for instance methods. Each argument takes
  one slot, **except long and double which take two slots**. Accessors:
  `A_OBJ(i) A_ARR(i) A_INT(i) A_BOOL(i) A_LONG(i) A_FLOAT(i) A_DOUBLE(i)`.
  Example: `static native float f(long font, float size, char[] text)` ->
  font = `A_LONG(0)`, size = `A_FLOAT(2)`, text = `A_ARR(3)`.
- Returns: `R_INT R_BOOL R_LONG R_FLOAT R_DOUBLE R_OBJ`.
- Throwing: `vm_throw_new(t, "Ljava/lang/IllegalStateException;", "fmt", ...)`
  then return; the interpreter checks `t->exception`.
- Registration: a static `NativeMethodReg[]` table of
  `{class descriptor, name, descriptor, fn}` passed to
  `vm_register_natives`, from a `*_register()` function called by
  `vm_natives_init()` in `src/native/natives.c`. Unregistered `native`
  methods fall back to JNI lookup in loaded `.so` files, then throw
  `UnsatisfiedLinkError`.
- Reading Java fields from C: `FIELD_INT(obj, "name")`, `FIELD_FLOAT`,
  `FIELD_BOOL`, `FIELD_LONG`, `FIELD_OBJ(obj, "name", "Ltype;")` in
  `src/android/android.h`. They cache the `Field*` per call site. **Field
  names used from C are part of the contract**: renaming them in Java
  requires changing the C code (grep for the name).

### 4.7 JNI
- Complete JNIEnv (all 233 slots) and JavaVM invoke interface in
  `src/vm/jni.c`. References are raw object pointers; locals are also
  recorded in a per-thread table (so they are roots); weak globals are
  tagged pointers.
- Native methods found by symbol name (`Java_pkg_Class_method` short and
  long forms) via `nativeloader_find_symbol`, or registered with
  `RegisterNatives`. Calls go through `sa_native_call` (callstub.S), which
  builds the platform ABI call from the method shorty (AArch64 AAPCS64 and
  x86-64 SysV).
- Contract with the native loader (src/nativeloader):
  - `const char *nativeloader_load_library(VMThread *t, const char *name, bool is_libname)`:
    load from the APK's `lib/arm64-v8a/` (or an absolute path), run
    constructors and `JNI_OnLoad`; return NULL or an error message.
  - `void *nativeloader_find_symbol(const char *name)`: search all loaded
    libraries.

## 5. Class library (java/libcore)

Our own implementation of the `java.*` subset Android apps use: lang,
lang.reflect (+ Proxy), lang.invoke (lambdas), util (+ concurrent, atomic,
locks, function, stream, regex, zip), io, nio (buffers, charset, basic
file APIs), text, math, net (URL/URI only so far), security (digests).
Natives in `src/native`. File paths from Java are translated by
`platform_map_path()` (src/native/java_io.c):

| Android path | Host / Switch path |
|---|---|
| `/data/data/<pkg>/...`, `/data/user/0/<pkg>/...` | `<data root>/apps/<pkg>/...` |
| `/sdcard/...`, `/storage/emulated/0/...` | `<data root>/sdcard/...` |
| `/data/local/tmp/...` | `<data root>/tmp/...` |
| other absolute paths on Switch | `sdmc:<path>` |

Data root on Switch will be `sdmc:/switch/switchapk/data`; on host it is
`--data` (default `build/data`).

## 6. Android framework (java/framework)

### 6.1 Principles
- Implement the public API of the **real android.jar (API 35 signatures)**,
  behaving like **API 29** (`Build.VERSION.SDK_INT = 29`). Signature
  fidelity matters more than completeness (see auto-stubbing).
- Prefer porting AOSP algorithms (measure/layout, TypedArray resolution,
  text layout, focus search) so apps observe the same behaviour.
- Keep native surface small: Java owns state and logic; natives do pixels,
  resource-table lookups and platform I/O.

### 6.2 Resources
- One merged `ResTable` in C (`g_res`): package 0x01 from
  `framework-res.apk` (SDK resources, English only) and package 0x7f from
  the APK. `arsc_add` merges tables; each table keeps its own string pool.
- Device configuration pushed by `Resources.updateConfiguration` ->
  `AssetManager.nSetConfiguration(density, wdp, hdp, swdp, orientation,
  night, sdk, lang, country, uiModeType)`.
- AssetManager natives (src/android/android_res.c) answer:
  value lookup with reference following (`nGetValue`, fills TypedValue
  fields `type data resourceId assetCookie density string`), flattened style
  bags with parents merged (`nGetBag` -> `{int[] (attr,type,data)*, String[]}`),
  names/identifiers, asset bytes, directory listing, and binary XML
  flattened into arrays (`nOpenXml` -> `{int[] events, int[] attrs, String[] strings}`)
  consumed by `XmlBlock.Parser`. Asset cookies: 1 = framework, 2 = app,
  0 = search app then framework.
- Theme and styled attribute resolution happen in Java
  (`Resources.Theme`, `Resources.obtain`) following AOSP precedence:
  XML attribute > `style=""` > default style (from `defStyleAttr` in the
  theme, else `defStyleRes`) > theme; `?attr` references resolve through
  the theme; `@ref` references through the table.
- Drawables and ColorStateLists inflate from XML in Java; bitmaps are
  decoded natively and scaled by density at decode time (nine-patches keep
  pixels and scale their divs at draw time).
- `android.R` is generated from the SDK (tools/genr) so constant values
  match what apps were compiled against. Internal (non-public) framework
  attributes can be looked up by name with
  `Resources.getIdentifier(name, "attr", "android")`.

### 6.3 Graphics
- `src/gfx` is a stateless renderer: every call receives target pixels,
  an affine matrix (`a b c d e f`, x' = a*x + c*y + e), a clip rectangle
  with optional 8-bit AA mask, and a paint. Anti-aliasing uses 4 vertical
  sub-scanlines with exact horizontal coverage. Supports fill rules
  (incl. inverse), strokes with caps/joins/miter, Porter-Duff modes,
  linear/radial/sweep/bitmap shaders with tile modes, bilinear bitmap
  sampling, color filters (tint, multiply), text with a glyph mask cache
  for axis-aligned text and outline rendering for any other transform.
- Pixels are `uint32` ARGB (0xAARRGGBB), **not premultiplied**, identical
  to `Bitmap.getPixel`. The Switch backend swizzles to RGBA on present.
- Java `Canvas` keeps the save stack (matrix, clip rect, clip mask, layers).
  `saveLayer` allocates an offscreen buffer the size of the clipped bounds
  and composites it on `restore`. Non-rectangular clips (clipPath, rotated
  clipRect, clipOut*) render into a byte mask via `nClipPathMask`.
- Java fields read by graphics natives (contract):
  - Canvas: `mPixels int[]`, `mWidth`, `mHeight`, `mMatrix float[6]`,
    `mClip int[4]` (device l,t,r,b), `mClipMask byte[]` (w*h or null)
  - Bitmap: `mPixels int[]`, `mWidth`, `mHeight`
  - Paint: `mColor mFlags mStyle mStrokeWidth mCap mJoin mMiter mXfer
    mCfMode mCfColor mShader mTextSize mTypeface mTextSkewX mTextScaleX`
  - Shader: `mType mGeom float[5] mColors int[] mPositions float[] mTileX
    mTileY mLocal float[6] mBitmap`
  - Typeface: `mNative long` (GfxFont*), `mFakeBold`
- Fonts: default family is the first found of Roboto (romfs or system),
  Noto Sans, DejaVu Sans, Liberation Sans; CJK fallback (WenQuanYi on
  host, Switch shared fonts on device). `Typeface.createFromAsset/File`
  load TTF/OTF through stb_truetype. No shaping (no complex scripts,
  ligatures or bidi reordering yet).

### 6.4 Main loop, input and windows
- `ActivityThread.main` prepares the main `Looper` and loops forever.
- The main `MessageQueue` (flag `mIsMain`) never sleeps in Java: it calls
  `MessageQueue.nativePollOnce(timeoutMs)`, which releases the GIL and
  waits in `platform_wait_event()` for an input event, a wake
  (`nativeWake()` from `enqueueMessage` on any thread) or the timeout.
  Afterwards it runs `MessageQueue.sPlatformDispatcher`, which drains events.
- Event pull API (src/android/android_os.c, Java class
  `android.view.PlatformInput`):
  `static native int nNextEvent(int[] ints /*4*/, float[] floats /*8*/, long[] timeNs /*1*/)`
  returns the event kind (0 = none) with the payload of `PlatformEvent`
  (`a b c d`, `f[0..7]`, `time_ns`); `static native String nTakeText()`
  returns the text of the last PEV_TEXT event.
- Event kinds (platform.h): PEV_TOUCH (a = MotionEvent action DOWN/UP/MOVE/
  POINTER_DOWN/POINTER_UP/CANCEL, b = pointer id, f0/f1 = x/y in screen
  pixels), PEV_KEY (a = 0 down / 1 up, b = Android keycode, c = meta,
  d = repeat), PEV_JOYSTICK (f0..f7 = AXIS_X, AXIS_Y, AXIS_Z, AXIS_RZ,
  AXIS_LTRIGGER, AXIS_RTRIGGER, AXIS_HAT_X, AXIS_HAT_Y), PEV_QUIT,
  PEV_FOCUS (a = gained), PEV_RESIZE (a, b = size, c = dpi), PEV_SENSOR
  (a = Sensor type, f0..f2), PEV_TEXT (a = request id, text).
- `PlatformInput` turns platform events into Android events: per-pointer
  PEV_TOUCH events are merged into multi-pointer `MotionEvent`s (pointer
  ids kept, ACTION_POINTER_DOWN/UP with the pointer index, device
  `InputDevice.ID_TOUCHSCREEN`, source SOURCE_TOUCHSCREEN); keys get the
  down time of their press, source SOURCE_GAMEPAD for gamepad buttons,
  SOURCE_DPAD for D-pad keys, SOURCE_KEYBOARD otherwise; PEV_JOYSTICK
  becomes an ACTION_MOVE `MotionEvent` from SOURCE_JOYSTICK with the eight
  axes (plus AXIS_BRAKE/GAS mirroring the triggers). PEV_SENSOR goes to
  `PlatformInput.setSensorSink` (for WS15). PEV_FOCUS also tells
  `WindowManagerGlobal.setPlatformFocus`.
- Input devices (`InputDevice`): id -1 virtual keyboard (US
  `KeyCharacterMap`), id 1 touch screen, id 2 "Nintendo Switch Controller"
  (SOURCE_GAMEPAD | SOURCE_DPAD | SOURCE_JOYSTICK, stick ranges -1..1,
  triggers 0..1).
- Controller mapping (Switch): A=BUTTON_A(96), B=BUTTON_B(97), X=99, Y=100,
  L=BUTTON_L1(102), R=BUTTON_R1(103), ZL=BUTTON_L2(104), ZR=BUTTON_R2(105),
  Plus=BUTTON_START(108), Minus=BUTTON_SELECT(109), stick clicks
  THUMBL(106)/THUMBR(107), D-pad DPAD_UP/DOWN/LEFT/RIGHT (19..22). As on
  Android (Generic.kcm fallbacks), `WindowManagerGlobal` re-dispatches an
  unhandled BUTTON_A as DPAD_CENTER, an unhandled BUTTON_B as BACK and an
  unhandled BUTTON_START (+) as MENU, which opens the options menu
  (FLAG_FALLBACK; the up of a fallback is canceled if the original up was
  handled). Joystick motion that no view consumes is turned into D-pad
  keys with key repeat by `ViewRootImpl.SyntheticJoystickHandler`
  (threshold 0.5 on the left stick or hat). Every app is therefore
  navigable with a controller.
- Frames: `Choreographer` keeps AOSP's callback queues (INPUT, ANIMATION,
  INSETS_ANIMATION, TRAVERSAL, COMMIT; `postCallback` and friends are
  public hidden APIs) and runs them once per frame, paced at the display
  refresh rate. `View.postOnAnimation` uses CALLBACK_ANIMATION.
- Windows: `WindowManagerGlobal` keeps a z-ordered list of `ViewRootImpl`s
  (layer by window type: application 2, sub-windows 3, system 10, input
  method 15, toast 20; newer windows above older ones of the same layer).
  Each `ViewRootImpl` owns an ARGB `Bitmap` of its window size and redraws
  only the union of invalidated rectangles (dirty rects are propagated up
  through `ViewGroup.invalidateChildInParent`, transformed by child
  matrices). The window size comes from its `WindowManager.LayoutParams`:
  MATCH_PARENT fills the display, WRAP_CONTENT measures the root first
  against the 320dp preferred dialog width (as AOSP does); the window is
  placed with `gravity`, `x`, `y` and margins. In the COMMIT phase the
  windows are composited bottom-up into a screen bitmap (FLAG_DIM_BEHIND
  draws a black layer of `dimAmount` alpha first; `alpha` is applied) and
  presented with `WindowManagerGlobal.nPresent(int[] px, int w, int h)`
  (GIL released). A single opaque full-screen window is presented without
  copying. Nothing is drawn or presented while nothing is invalidated.
- Input routing: touch DOWN goes to the topmost window that contains the
  point or is touch-modal (not FLAG_NOT_TOUCH_MODAL / FLAG_NOT_FOCUSABLE),
  skipping FLAG_NOT_TOUCHABLE windows, and the rest of the gesture follows
  it (coordinates offset to the window); windows below a modal one that
  set FLAG_WATCH_OUTSIDE_TOUCH get ACTION_OUTSIDE. Keys and joystick go to
  the focused window, the topmost one without FLAG_NOT_FOCUSABLE. Window
  focus changes are dispatched as `onWindowFocusChanged`.
- Inside a window, `ViewRootImpl` follows AOSP: touch mode (entered on a
  touch DOWN, left by a navigation key, which focuses the first focusable
  view and is consumed), key pipeline pre-IME, view tree, unhandled-key
  listeners, Ctrl shortcuts, then D-pad/Tab focus navigation through
  `FocusFinder`. The root `DecorView` passes events to the
  `Window.Callback` (Activity, later Dialog), which calls back into
  `Window.superDispatch*`.
- Decor: `PhoneWindow` reads the theme's window attributes (background,
  floating, translucent, dim, min width for floating windows, close on
  touch outside, soft input mode) and inflates the framework decor layout
  chosen as in AOSP generateLayout: the action bar decor
  (windowActionBarFullscreenDecorLayout, `screen_toolbar` on Material:
  ActionBarOverlayLayout `decor_content_parent` holding the content and an
  ActionBarContainer with a Toolbar `action_bar` and an
  ActionBarContextView `action_context_bar`), `dialogTitleDecorLayout` for
  floating windows with a title, `screen_title`, or `screen_simple`
  (LinearLayout + action mode ViewStub + FrameLayout `android:id/content`).
  The decor content parent (DecorContentParent) takes the window title,
  features, icon/logo and the options menu (MenuBuilder themed with
  actionBarTheme/actionBarWidgetTheme, presenters from the toolbar). The
  activity's ActionBar is a WindowDecorActionBar over that decor, or a
  ToolbarActionBar after setActionBar(Toolbar), which wraps the window
  callback. DecorView owns the primary ActionMode: the window callback
  may supply it (the action bar's context bar), else DecorView inflates
  `action_mode_bar_stub` for a StandaloneActionMode. BACK finishes it.
- Default theme: a context whose component and application set no theme
  uses `Resources.selectDefaultTheme(0, targetSdk)` (DeviceDefault Light
  DarkActionBar for targetSdk 24+), as AOSP ContextImpl and
  ContextThemeWrapper do.
- Lists: AbsListView fills, scrolls and recycles in rows of
  `itemsPerRow()` items (1 for ListView, the column count for GridView),
  with `childWidthMeasureSpec`/`childLeft` per column and `childGap()`
  between rows; the first position is always a row start.
  Subclass hooks (AOSP names): `createContextMenuInfo(view, position, id)`
  and ListView `drawDivider(canvas, bounds, childIndex)`;
  ExpandableListView overrides both and flattens its adapter through
  ExpandableListConnector (only expanded groups are recorded).
  `setSelection(INVALID_POSITION)` clears the selection.
- Filtering: `Filter.filter` posts to a "Filter" HandlerThread (quits
  after 3 s idle) and publishes on the looper of the thread that created
  the Filter, so results arrive asynchronously, as on Android.
- Private framework resources: `InternalRes` resolves
  com.android.internal ids by name; private attrs live under the
  "^attr-private" type in framework-res and are found there.
- Display metrics: `android.view.Display.nGetInfo(int[] out)` returns
  width, height, dpi, refresh rate x 1000, has-touch. 1280x720 at 240 dpi
  in handheld mode, 1920x1080 at 360 dpi docked (same 853x480 dp).
  Portrait-locked activities get a letterboxed portrait window (height =
  screen height, width = 9/16 of it) with density lowered so the window is
  at least 320 dp wide (todo, WS4).
- Soft keyboard: `InputMethodManager` calls
  `nRequestText(int id, String initial, String hint, int inputType, int maxLen)`;
  the platform shows swkbd (Switch) or answers from the test script
  (host) and posts PEV_TEXT. InputMethodManager commits that string into
  the editor that called showSoftInput, via InputConnection.commitText.

### 6.4.1 View system notes for widget authors
- `View`/`ViewGroup` are ports of AOSP; subclasses behave as on Android.
  Package-private hooks used inside `android.view`: `View.draw(Canvas,
  ViewGroup, long)` (per-child transform/alpha/clip), `mAttachInfo`
  (`View.AttachInfo`), `dispatchAttachedToWindow/DetachedFromWindow`,
  `invalidate(boolean)` and `assignParent`.
- Fields that apps or AndroidX read by reflection keep their AOSP names:
  `View.mListenerInfo`, `mAttachInfo`, `mLayoutParams`, `mMinWidth`,
  `mMinHeight`, `mID`, `mParent`, `mPrivateFlags`, `mViewFlags`;
  `View.AttachInfo.mStableInsets`/`mContentInsets` (WindowInsetsCompat);
  `ViewGroup.mGroupFlags`; `LayoutInflater.mFactory`, `mFactory2`,
  `mPrivateFactory`, `mConstructorArgs`.
- Hidden AOSP methods other packages may call are public and marked
  "framework-internal (hidden in AOSP)", e.g. `View.isLayoutRtl()`,
  `View.hasIdentityMatrix()`, `View.getInverseMatrix()`,
  `View.pointInView()`, `View.internalSetPadding()`, `View.setFrame()`,
  `ViewGroup.setIsRootNamespace()`, `MotionEvent.split()`,
  `MotionEvent.getPointerIdBits()`, `KeyEvent.isConfirmKey()`.
- `@null` attribute values (a reference to 0) read as no value in
  `TypedArray`, as in AOSP's ApplyStyle.
- `ViewRootImpl.performDraw` empties its dirty rect before drawing, as
  AOSP's drawSoftware does: an `invalidate()` made during the draw (from
  `computeScroll()` or `onDraw`) schedules the next frame.
- Fading edges: `View.draw` honours `requiresFadingEdge` (the legacy
  `fadingEdge` attr is ignored, as since ICS) and
  `setVertical/HorizontalFadingEdgeEnabled`. Content (not the background)
  goes into one `saveLayer` over the padded box and each edge with a
  strength above zero is erased with a scaled black-to-clear
  LinearGradient in DST_OUT; with a non-zero `getSolidColor()` a ramp of
  that colour is drawn instead. NumberPicker fades its outer values so.
- Clocks have no system time broadcasts: TextClock schedules its own tick
  on the next second (formats with seconds) or minute boundary while
  attached and visible; Chronometer ticks on second boundaries of its base.
- LinearLayout measures a 0dp weighted child at only its share of the
  excess when the spec is not EXACTLY if the app targets N or later, and
  at its wrap size plus the share for older targets (AOSP
  `mAllowInconsistentMeasurement`). Dialog sizing depends on it: the
  wrap-content window trial at `config_prefDialogWidth` only succeeds when
  nothing below reports MEASURED_STATE_TOO_SMALL.
- Date widgets: DatePicker uses the material day picker (internal
  ViewPager of SimpleMonthViews, YearPickerView); the landscape
  `layout-land` dimens and layouts apply on the Switch screen.
  CalendarView follows `calendarViewMode`: material (the Theme.Material
  default) is that day picker, and holo (`Widget.CalendarView`, mode 0)
  is CalendarViewLegacyDelegate, a ListView of weeks inflated from the
  framework `calendar_view` layout (month title, day-name header, week
  numbers, selected-week tint, vertical bars). There is no
  libcore.icu.LocaleData, so the default first day of the week is
  `Calendar.getInstance().getFirstDayOfWeek()` (Sunday on the Gregorian
  calendar). `DateUtils` joins format pieces with ", ", emits the month
  for `FORMAT_NO_MONTH_DAY`, and returns a one-letter weekday for
  `LENGTH_SHORTEST`, so the holo header reads "March, 2024" over
  "S M T W T F S". TimePicker uses the radial clock (with the text input
  mode) or NumberPicker spinners; RadialTimePickerView crossfades hours
  and minutes on a frame callback rather than an ObjectAnimator until
  WS5 lands.
- VideoView is the AOSP widget on a SurfaceView, with MediaController as
  the floating transport bar from the framework `media_controller` layout.
  Nothing is decoded yet: `MediaPlayer.prepareAsync` posts
  `MEDIA_ERROR_UNKNOWN` / `MEDIA_ERROR_UNSUPPORTED` on the main looper, and
  VideoView shows the framework "Can't play this video." dialog unless an
  `OnErrorListener` returns true. `Context.AUDIO_SERVICE` returns an
  AudioManager that grants focus and never revokes it. Subtitle sources
  are reported unsupported. The mixer and decoders are WS7.
- RemoteViews inflates its layout and runs the action list (reflection
  setters, click and checked PendingIntents, fill-in against a template
  tag on an ancestor, and RemoteCollectionItems as a BaseAdapter).
  onLoadClass allows framework View packages because the VM does not
  surface the RemoteView annotation. DrawInstructions apply as an empty
  view. setRemoteAdapter(Intent) is not hosted. Notification content
  RemoteViews stay unsupported, and this does not change Notification.
- GridLayout is the AOSP port: row and column lines come from a
  difference-constraint solve per axis, cached until the structure (child
  set, spans, GONE changes, `onSetLayoutParams`) or the values (any
  `requestLayout`) are invalidated. Subclasses of ViewGroup outside
  android.view can override the hidden `onSetLayoutParams` hook, which
  View.setLayoutParams calls after storing the params.
- SearchView is the AOSP port on the framework `search_view` layout, with
  the package-private SuggestionsAdapter querying a searchable's
  suggestions provider through `SearchManager.getSuggestions`.
  SearchManager finds searchables among the app's own activities that
  handle ACTION_SEARCH and carry `android.app.searchable` meta-data
  (directly or through `android.app.default_searchable`); global and web
  search report none and voice search never resolves, so the voice button
  stays hidden. `Activity.onSearchRequested` opens the AOSP SearchDialog
  (the `search_bar` layout) at the top of the activity. A suggestions
  drop-down anchors to the first `search_edit_frame` in its window, as on
  Android, so several SearchViews in one window share an anchor.
  `ListPopupWindow.show` does nothing until an anchor is set (a posted
  show can run before one exists).
- CompoundButton draws only its button drawable; with none (an explicit
  `@null`) nothing is drawn, as on Android. Dates format through
  `DateFormat.getBestDateTimePattern`, which maps skeletons to en-US
  patterns as ICU would (there is no ICU).
- Drawing is software only: `isHardwareAccelerated()` is false, layer
  types only add a `saveLayer` with the layer paint, elevation and
  outlines draw no shadows, and `clipToOutline` is not applied yet.
- `Surface` is a software buffer queue (`Surface.BufferQueue`, two ARGB
  bitmaps). `lockCanvas` copies the last frame into the back buffer;
  `unlockCanvasAndPost` swaps, notifies the consumer and, on a non-UI
  thread, waits until the frame was drawn (at most ~34 ms), which paces
  render threads to the display. `SurfaceView` draws the latest frame in
  its own draw pass (scaled when `setFixedSize` was used) and runs the
  `SurfaceHolder.Callback`s on the UI thread when it is attached, visible
  and sized. `SurfaceTexture.getSoftwareBufferQueue()` (framework-internal)
  backs `TextureView` and `new Surface(surfaceTexture)`. GL is WS8.
- `View.animate()`, tween application and `StateListAnimator` belong to
  WS5. `View.startAnimation/setAnimation/getAnimation/clearAnimation`
  exist and hold the animation (ViewAnimator and friends use them) but
  draw does not apply it yet; `AnimationUtils.loadAnimation` parses
  `<alpha>` and loads other tags as an identity alpha animation. Only the tween core ProgressBar needs exists so far
  (`TimeInterpolator`, `Interpolator`, linear/accelerate/decelerate
  interpolators, `Animation`, `AlphaAnimation`, `Transformation`,
  `AnimationUtils.loadInterpolator`); views do not apply tween animations
  and AnimatedVectorDrawables do not animate. Accessibility classes are
  value holders since no accessibility service runs.
- ProgressBar family (AOSP ports): determinate progress sets drawable
  levels per layer id (`android:id/progress`, `secondaryProgress`,
  `background`), indeterminate starts an Animatable drawable or cycles
  levels with an AlphaAnimation. Bitmap layers are tiled with a repeating
  BitmapDrawable clone (keeps the tint; RatingBar stars). The Material
  spinners are `com.android.internal.graphics.drawable.
  AnimationScaleListDrawable`, which shows its static child until WS5
  (what Android shows with animations off). AbsSeekBar adds the thumb,
  split track, tick marks, touch drag (slop in scrolling containers) and
  D-pad/plus/minus steps (`keyProgressIncrement`, about 1/20 of the
  range); RatingBar steps by stepSize and reports user changes on release.
- Popups (AOSP ports): PopupWindow adds a TYPE_APPLICATION_PANEL window
  (decor view dismissing on BACK and on touches outside; background view
  with the above-anchor state); drop-downs go below the anchor, or above
  when there is no room, and follow it when it scrolls. The window manager
  keeps windows on screen unless FLAG_LAYOUT_NO_LIMITS. ListPopupWindow
  sizes a DropDownListView to its rows (`ListView.measureHeightOfChildren`).
  PopupMenu uses `MenuPopupHelper` (StandardMenuPopup folded in; a sub
  menu replaces its parent on the same anchor). Spinner keeps AOSP's
  AbsSpinner bookkeeping; its drop-down is a modal ListPopupWindow, its
  dialog mode a single-choice AlertDialog. Toast queues one TYPE_TOAST
  window at a time (2 s / 3.5 s) and logs `Toast: show: <text>`; toast
  windows are not closed as activity leaks. Item selection callbacks
  that fire during layout are posted (AdapterView SelectionNotifier), so
  listeners can change other views. Transitions and window animations
  are not run.

### 6.5 Application model (design)
- The app runner (C, `app_run_apk`) opens the APK, sets `g_app_zip` and
  `g_app_apk_path`, loads framework-res + app resources
  (`android_res_init`), adds `classes.dex`, `classes2.dex`, ... to the app
  dex list, sets the data root and package, boots the VM and calls
  `android.app.ActivityThread.main(String[] {apkPath})`.
  `framework-res.apk` is the first readable path among
  `build/toolchains/framework-res.apk`, `build/java/framework-res.apk` and
  `{platform_framework_path()}/framework-res.apk`. If none is readable the
  toolchain path is passed anyway and the framework table stays empty.
- `ActivityThread` parses `AndroidManifest.xml` with `XmlBlock` (package,
  application class, activities with intent filters and themes and
  screenOrientation/configChanges, services, receivers, providers,
  meta-data into `PackageItemInfo.metaData` as PackageParser stores it,
  uses-feature), creates `LoadedApk`/`ContextImpl`/
  `Application` (via `AppComponentFactory` when declared), installs
  content providers **before** `Application.onCreate` (as Android does;
  androidx startup relies on it), then launches the MAIN/LAUNCHER activity.
- Activities form a single task stack (`ActivityThread.ActivityRecord`:
  caller, requestCode, resultWho, started, saved state, pending results,
  relaunch pending). `startActivity`, `finish` and `recreate` resolve the
  intent synchronously and post the work to the main looper, like the
  binder calls they replace, so they never run inside another activity's
  callback. Explicit components that do not resolve throw
  ActivityNotFoundException; implicit intents for other apps (browser,
  market, share) are logged and ignored. Manifest `launchMode`,
  `parentActivityName` and `uiOptions` are read; FLAG_ACTIVITY_SINGLE_TOP,
  CLEAR_TOP, NEW_TASK|CLEAR_TASK, singleTop and singleTask are honoured
  (onNewIntent with a pause around it). navigateUpTo, finishAffinity,
  finishActivity(requestCode), getCallingActivity and isTaskRoot work on
  the stack.
- Lifecycle, AOSP order: launching pauses the old top, then creates,
  starts, post-creates and resumes the new activity, then stops the old
  one and saves its state (API 28+ order: onStop before
  onSaveInstanceState). Floating or translucent activities leave the one
  below paused but not stopped. Finishing pauses, then the activity below
  gets onRestart/onStart, its pending onActivityResult, onResume, then the
  finished one is stopped and destroyed; windows it leaked are removed
  (`WindowManagerGlobal.closeAll`). PEV_FOCUS(0) (HOME / applet suspend)
  pauses the top, refocus resumes it; PEV_QUIT destroys everything top
  down and exits.
- Configuration changes: PEV_RESIZE (docked/handheld) recomputes the
  Configuration and DisplayMetrics, updates the shared Resources, relays
  out all windows and calls Application.onConfigurationChanged. Each
  activity whose `configChanges` (plus the AOSP implied bits for old
  targetSdk) covers the diff gets onConfigurationChanged; the others are
  relaunched: pause, stop, save, retainNonConfigurationInstances
  (fragments, loaders, onRetainNonConfigurationInstance), destroy, then a
  new instance is created with that state and restored
  (onRestoreInstanceState before onPostCreate). Stopped activities are
  relaunched lazily when they come back to the top.
- Embedded activities (ActivityGroup, TabActivity): LocalActivityManager
  creates a child through `ActivityThread.startActivityNow` (attach with a
  parent and an embedded id, then onCreate) and moves it through its own
  RESTORED/INITIALIZING/CREATED/STARTED/RESUMED state machine with direct
  perform* calls, following the group's lifecycle. A child never gets a
  stack record or a window of its own: its window is contained by the
  parent's (no title or action bar, no window background) and TabHost
  adds its decor to the tab content. finish, startActivityForResult,
  setTitle and the options menu callbacks go through the parent; results
  for a child come back to the group tagged with its id and ActivityGroup
  routes them. As on Android, a group only saves the state of children
  that are resumed, and from API 28 it saves after stopping them, so
  embedded activity state does not survive a configuration change (the
  current tab and retained non-config instances do).
- Fragments: the platform `android.app.Fragment`, `FragmentManager`
  (FragmentManagerImpl state machine, back stack, BackStackRecord ops,
  saved and retained state, `<fragment>` inflation), `DialogFragment`,
  `ListFragment` and `LoaderManager`, hosted by Activity through
  `FragmentController`/`FragmentHostCallback` as in AOSP (no transitions
  or animators yet). AndroidX ReportFragment relies on this.
- Contexts: the application's `ContextImpl` holds the package state;
  each Activity and Service gets its own `ContextImpl` from
  `createComponentContext(outer)` that shares it. Receivers and service
  connections are keyed by that outer context, and `scheduleFinalCleanup`
  (after onDestroy) unregisters and unbinds what the component leaked,
  logging AOSP's "has leaked IntentReceiver/ServiceConnection" errors.
- Broadcasts (`BroadcastQueue`, in place of the AMS queue and
  LoadedApk's receiver dispatchers): always asynchronous. A normal
  broadcast goes to registered receivers in parallel (each on its
  scheduler Handler, else the main looper), then to manifest receivers
  one at a time; an ordered one goes to all of them one at a time by
  filter priority (registered first at equal priority), carrying result
  code, data and extras in `BroadcastReceiver.PendingResult`, stopping on
  abort, then calling the result receiver. `goAsync` holds the broadcast
  until `PendingResult.finish()` (any thread). An explicit component
  reaches only that manifest receiver; for targetSdk >= 26 implicit
  broadcasts skip manifest receivers (logged as on Android). Manifest
  receivers get a `ReceiverRestrictedContext` (no register or bind).
  Sticky broadcasts are kept and replayed on register; the system's
  ACTION_BATTERY_CHANGED is sticky (level 100 until WS15 reads the
  battery).
- Services (`ActiveServices`, in place of AMS ActiveServices and
  LoadedApk's service dispatchers): bookkeeping is synchronous and
  thread-safe; calls into the service and clients are posted to the main
  looper in AMS order. One record per component; onCreate, then
  onStartCommand with increasing start ids or onBind once per
  filter-equal intent, whose binder is cached and handed to every
  connection (onRebind when onUnbind returned true). stopSelf(id) acts on
  the latest id only. A service is destroyed when neither started nor
  bound with BIND_AUTO_CREATE; remaining connections get onBindingDied.
  Service intents must be explicit for targetSdk >= 21. Connections are
  dispatched per (context, ServiceConnection), so a second bind to the
  same service does not repeat onServiceConnected. Running services get
  onConfigurationChanged. `IntentService` is the AOSP worker thread one.
- PendingIntent keeps an in-process record table keyed like AMS (kind,
  request code, filter-equal intent, flags, activity for
  createPendingResult) with FLAG_NO_CREATE/CANCEL_CURRENT/UPDATE_CURRENT/
  ONE_SHOT, the S+ mutability check, fill-in for mutable ones and
  OnFinished (via an ordered broadcast for broadcasts). `IntentSender`
  wraps one; `startIntentSenderForResult` starts activity targets from
  the caller so the result comes back. AlarmManager posts alarms on the
  main looper (RTC converted to elapsed time; a re-set PendingIntent or
  listener replaces its alarm; repeating alarms skip missed periods);
  alarms live only as long as the process.
- Notifications: `Notification.Builder` and the styles write the AOSP
  extras keys (EXTRA_TITLE, EXTRA_BIG_TEXT, EXTRA_TEMPLATE, EXTRA_MESSAGES
  bundles, ...), so NotificationCompat and recoverBuilder read back what
  they expect; RemoteViews content is not supported. NotificationManager
  keeps channels, groups and the active list per process (as
  NotificationManagerService does per package): target O+ notifications
  without an existing channel are dropped with the AOSP "No Channel found"
  error, IMPORTANCE_NONE channels block, re-creating a channel may only
  rename it, lower its importance or set its group once. Posted
  notifications are logged (`notify <id> [title] text channel=<id>`);
  there is no shade on the Switch.
- Jobs (`android.app.job.JobSchedulerImpl`, in place of
  JobSchedulerService): schedule/enqueue validate the service (declared,
  requires BIND_JOB_SERVICE). A job is ready when its minimum latency has
  passed and constraints hold, or its override deadline has passed. The
  service is bound with BIND_AUTO_CREATE and driven through the
  `JobServiceEngine` binder (so AndroidX JobIntentService works):
  onStartJob, then jobFinished, a false return, or dequeueWork returning
  null with no work in progress ends the run and unbinds. cancel,
  re-schedule and a 10 minute timeout call onStopJob. Reschedules back
  off (linear/exponential, 5 h cap); periodic jobs run once per interval
  in their flex window. Constraint sources: network is taken as unmetered
  Wi-Fi, charging and battery come from the sticky ACTION_BATTERY_CHANGED
  (polled every minute while a job waits), the device is never idle,
  content-URI triggers never fire. Jobs do not outlive the process
  (WorkManager reschedules its work at app start).
- Dialogs: `android.app.Dialog` owns a floating `PhoneWindow` themed from
  `android:dialogTheme` (`alertDialogTheme` for AlertDialog) and is added
  to the window manager on `show()`. `AlertDialog` uses a port of
  `com.android.internal.app.AlertController` with the framework-res
  layouts (`alert_dialog_material`, `select_dialog_*_material`) and the
  internal widgets `AlertDialogLayout`, `ButtonBarLayout`, `DialogTitle`.
- Menus: `PhoneWindow` builds the options menu (Window.Callback
  onCreatePanelMenu/onPreparePanel) on MENU key up and shows it with
  `MenuPanel`, an overflow-style popup in the top end corner (no action
  bar decor yet). Context menus (`View.showContextMenu` -> `DecorView` ->
  `PhoneWindow`) and sub menus are AlertDialog lists via
  `MenuDialogHelper`, like AOSP. Rows use the framework
  `popup_menu_item_layout`/`list_menu_item_layout` with
  `ListMenuItemView`. Selections go to `onMenuItemSelected(featureId)`,
  closing to `onPanelClosed`.
- Framework resources that are not in the public `android.R`
  (`com.android.internal.R` on AOSP) are looked up by name with
  `com.android.internal.util.InternalRes` (`attr`, `layout`, `viewId`,
  `style`, `attrs(...)`), which caches `Resources.getIdentifier`.

### 6.6 Storage, media, GL (design)
- SQLite: bundled amalgamation compiled into the binary
  (`third_party/sqlite`), Java API in `android.database.sqlite` over thin
  natives (`SQLiteNative`); results are fully materialized per query.
- Audio (not built): one float stereo mixer in C (`platform_audio_start`
  callback) mixing SoundPool voices, AudioTrack streams and MediaPlayer
  streams. Decoders: WAV/PCM, OGG Vorbis (stb_vorbis or libvorbis), MP3
  (mpg123 on Switch portlibs). Switch output via audren or audout. Until
  that lands, the Java MediaPlayer and AudioManager are the placeholders
  in 6.4.1 and produce no samples.
- OpenGL ES 2/3: GLES20/GLES30 Java bindings generated from the Khronos
  headers into natives that call the real GLES (mesa/nouveau on Switch via
  portlibs). EGL14 and `javax.microedition.khronos.egl` map to real EGL
  with the NWindow from libnx. While a GL surface is active the platform
  presents through EGL instead of the software framebuffer; views drawn on
  top of a GLSurfaceView are composited by uploading the software layer as
  a texture (or unsupported in the first version).

### 6.7 Native libraries (design)
- ELF64 loader for `lib/arm64-v8a/*.so` from the APK: map segments,
  apply relocations (AArch64: RELATIVE, GLOB_DAT, JUMP_SLOT, ABS64,
  TLSDESC; x86-64 equivalents for host tests), resolve imports against
  (1) other loaded app libraries, (2) our shim libraries, run
  `.init_array`, then `JNI_OnLoad`.
- Shim ("bionic on newlib"): exported symbol tables for libc, libm, libdl,
  liblog, libandroid (ANativeWindow, AAssetManager, ALooper, AInputQueue,
  AConfiguration, ANativeActivity), libEGL, libGLESv2, libOpenSLES,
  libaaudio, libz. Most libc symbols forward to newlib/libnx; bionic
  struct layouts that differ (FILE, pthread types, stat, dirent, sigaction)
  need translation wrappers.
- TLS: bionic keeps the stack protector guard at `tpidr_el0 + 40` and uses
  fixed TLS slots; the loader must provide a compatible TLS block per
  thread.
- Switch executable memory: map code with `svcMapProcessCodeMemory` +
  `svcSetProcessMemoryPermission` (needs the process handle; works on
  homebrew with JIT-capable environments, e.g. when launched as an
  application rather than an applet). Host builds use mmap/mprotect.
- NativeActivity: implement `ANativeActivity_onCreate` callbacks and the
  input queue/window lifecycle expected by `android_native_app_glue`.

## 7. Platform layer (src/platform/platform.h)

Single C interface implemented once per target:

- lifecycle: `platform_init(argc, argv)`, `platform_shutdown()`
- display: `platform_get_display(PlatformDisplay*)`,
  `platform_present(argb, w, h, stride)` (blocks for vsync on Switch,
  letterboxes smaller windows centred)
- events: `platform_wait_event(ev, timeout_ms)`, `platform_wake()`,
  `platform_push_event(ev)` (thread-safe)
- text: `platform_request_text(id, initial, hint, input_type, max_len)`
- audio: `platform_audio_start(rate, cb, user)`, `platform_audio_stop()`
- misc: `platform_vibrate(ms)`, `platform_framework_path()`,
  `platform_native_window()` (NWindow* for EGL), `platform_is_headless()`,
  `platform_screenshot(path)`, headless-only `platform_set_headless_script`
  and `platform_set_screenshot_dir`
- the data root and Android path mapping live in `src/native/java_io.c`
  (`platform_set_data_root`, `platform_data_root`, `platform_map_path`).

Headless implementation: in-memory frame, event queue, script thread
(`wait`, `idle [ms]`, `tap x y`, `down/move/up x y`, `swipe`, `key NAME`,
`keydown/keyup`, `text ...`, `screen WxH@dpi` (changes the display and
posts PEV_RESIZE, to simulate a dock switch), `screenshot file.png`,
`log`, `quit`), audio
consumer thread that discards samples in real time. When the script ends
it posts PEV_QUIT.

Switch implementation (`platform_switch.c`, `main_switch.c`):
- Threads: the main thread runs the launcher, then pumps
  `platform_switch_pump()` every 8 ms (applet loop, pad, touch, swkbd)
  while the APK runs on a 16 MB-stack VM thread moved to core 1. The
  event queue is the headless one (mutex + condvar).
- Display: libnx linear framebuffer, 1280x720 RGBA8888, double buffered;
  `platform_present` converts ARGB to RGBA and blocks for a free buffer
  (vsync). Docked output is upscaled by the system, so apps always see
  1280x720 at 240 dpi. Touch (handheld) is relative to the letterboxed
  window, as on the host.
- Input: buttons map to Android gamepad key codes (see 6.x), the D-pad
  and the left stick (as a D-pad, with key repeat) to DPAD_*; sticks and
  triggers also go out as PEV_JOYSTICK axes.
- Applet: focus changes post PEV_FOCUS, exit requests PEV_QUIT;
  operation mode updates `PlatformDisplay.touch`.
- Text: `platform_request_text` queues a request the main thread shows
  with swkbd; the result (or null on cancel) comes back as PEV_TEXT and
  replaces the whole field (InputMethodManager selects all, then commits).
- Fonts: `romfs:/fonts/Roboto-*.ttf` if bundled, else the system shared
  fonts (`plGetSharedFontByType`: Standard, then CJK, Korean and Nintendo
  extension fonts as fallbacks).
- Files: `romfs:/framework.dex`, `romfs:/framework-res.apk`; APKs in
  `sdmc:/switch/switchapk/apks`, app data in `sdmc:/switch/switchapk/data`,
  log in `sdmc:/switch/switchapk/log.txt` (flushed on warnings and errors).
- Launcher: a C screen listing the APKs (D-pad, stick or touch, A runs,
  + exits); `argv[1]` ending in .apk skips it (nxlink). When the app ends
  the NRO reloads itself through hbloader (`envSetNextLoad`). A non-zero
  exit shows the last 48 INFO+ log lines (`sa_log_recent`) on an error
  screen.
- Not yet: audio (samples are drained like the headless backend), rumble,
  native .so loading, 1080p docked rendering.

## 8. Testing strategy

- VM conformance: Java programs run on OpenJDK and on switchapk; stdout
  must match byte for byte (`tests/run_dex_test.sh`). Add a test program
  for every VM or libcore bug fixed.
- Renderer: `tests/c/gfx_test.c` renders a gallery to PNG for visual
  review. Future: golden-image comparisons with a tolerance.
- Framework: small APKs in `tests/apps/<name>/` (sources + manifest +
  res), built with aapt2 + javac + d8 against android.jar, run headless
  with a `.script`; screenshots compared with goldens; logcat output
  checked for exceptions and `STUB:` lines.
- Real-world APKs: keep a local (not committed) corpus of open-source APKs
  (F-Droid) and track results in `docs/COMPATIBILITY.md` (to be created).
