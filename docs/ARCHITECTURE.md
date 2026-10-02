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
|                                       | OpenSLES  (src/nativeloader)       |
+---------------------------------------+-----------------------------------+
| Dalvik VM (src/vm): interpreter, class linker, GC, threads/GIL, JNI       |
+---------------------------------------------------------------------------+
| framework natives: src/native (java.*), src/android (android.*)           |
| 2D renderer: src/gfx       resource/zip/dex parsing: src/core             |
+---------------------------------------------------------------------------+
| platform: src/platform/platform.h                                         |
|   platform_headless.c (host tests)  |  platform_switch.c (libnx)          |
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
                 android_res.c, android_graphics.c, android_os.c,
                 android_gl.c + android_gl.h (EGL, GLUtils, GL loader),
                 android_gles_gen.c + gles_funcs.h (generated GLES
                 bindings), android_gles_special.c, android_media.c,
                 android_sqlite.c, sqlite_vfs_switch.c (Switch VFS)
src/gfx/         gfx.h, raster.c (AA rasterizer/compositor), font.c
                 (stb_truetype text), image.c (stb_image decode, PNG encode)
src/platform/    platform.h, platform_headless.c, platform_switch.c
src/nativeloader/ nativeloader.h, elf_loader.c (ELF loader, dl*), shim_libc.c
                 (libc/libm), shim_android.c (liblog, libdl, assets,
                 properties, zlib, GL lookup)
src/app/         main_host.c (host driver), app_runner.c (APK runner),
                 main_switch.c (Switch launcher), apk_info.c (labels/icons)
java/libcore/    java.*, javax.*, sun.*, libcore.*, dalvik.* classes
java/framework/  android.*, com.android.internal.*, org.json, org.xmlpull
third_party/     stb (image, truetype), sqlite (fetched, gitignored)
tools/           build_java.sh, fetch_toolchains.py, make_framework_res.py,
                 gen_gles.py (GLES bindings generator),
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
| `switchapk.nro` | `make -f Makefile.switch` | NRO with romfs: framework.dex, framework-res.apk, fonts |

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
- Runtime annotations: `Class`, `Field`, and `Method` `getAnnotation`
  read `VISIBILITY_RUNTIME` annotations from the dex, including nested
  annotations, arrays, enums, class literals, and `AnnotationDefault`
  for omitted elements. d8 stores that default as one class annotation
  whose value lists every member; a per-method `AnnotationDefault` is
  also accepted. CLASS and SOURCE retention are not returned.
  Parameter annotations are not. `@Inherited` on a class annotation is
  visible through `getAnnotation` on subclasses.

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
    load from the APK's `lib/<abi>/` (or an absolute path; 6.7), run
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
  `action_mode_bar_stub` for a StandaloneActionMode. TYPE_FLOATING
  creates a FloatingActionMode: a popup row of the menu items above the
  content rect from Callback2.onGetContentRect, or below it when the row
  does not fit. hide dismisses the popup without finishing the mode.
  A long press on selectable or editable text selects the word under
  the finger and starts that floating mode. The menu is Cut, Copy,
  Paste, and Select all. Copy, cut, and paste finish the mode; Select
  all updates the content rect. Destroying the mode clears a
  non-editable selection and collapses an editable one to a cursor.
  Context.CLIPBOARD_SERVICE is one process-wide ClipboardManager, which
  is what those items read and write. An anchored long press calls
  performLongClick() with the point stored, so a no-arg override sees
  it. BACK finishes a floating mode before a primary one.
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
- `DisplayCutout` holds safe insets, one bounding rect per edge, and
  waterfall insets. `WindowInsets.getDisplayCutout` returns the cutout
  attached with `Builder.setDisplayCutout`. That setter does not add
  `Type.displayCutout` insets; `consumeDisplayCutout` clears the cutout
  and leaves those insets. `inset` moves the cutout with the other
  insets. `getCutoutPath` is null: cutout specs are not parsed. The
  Switch has no cutout, so dispatched insets carry none.
  `PhoneWindow` copies the theme `windowLayoutInDisplayCutoutMode`
  (`default` 0, `shortEdges` 1, `never` 2, `always` 3) onto
  `LayoutParams.layoutInDisplayCutoutMode`.
- `ViewDebug` is the public annotation set (`ExportedProperty`,
  `CapturedViewProperty`, `IntToString`, `FlagToString`) plus
  `dumpCapturedView`, which logs fields and no-arg methods marked
  `@CapturedViewProperty`. Hierarchy and recycler tracing are no-ops.
  Hardware capture and the view server are not implemented.
- `View.setClipToOutline(true)` clips the view, its background, and its
  children to the outline from `getOutlineProvider()`. Only a round rect
  clips (`Outline.canClip()` is false for a path). The rect is in view
  coordinates and is shifted by the scroll, matching
  `draw(Canvas, ViewGroup)`. Shadows are not drawn. `invalidateOutline()`
  invalidates the view.
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
  Video is not decoded. A missing file, or a source that is not WAV, Ogg
  Vorbis or MP3, makes `prepareAsync` post `MEDIA_ERROR_UNKNOWN` /
  `MEDIA_ERROR_UNSUPPORTED` on the main looper, and VideoView shows the
  framework "Can't play this video." dialog unless an `OnErrorListener`
  returns true (tests/apps/video). Audio files play through the mixer in
  6.6. `Context.AUDIO_SERVICE` returns an
  AudioManager that grants focus and never revokes it, and stores a
  volume index per stream. Subtitle sources are reported unsupported.
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
  backs `TextureView` and `new Surface(surfaceTexture)`. EGL window
  surfaces post into the same queue (6.6.1).
- `View.animate()` returns a `ViewPropertyAnimator`. `ValueAnimator` and
  `ObjectAnimator` advance on Choreographer frames and set view
  properties (translation, scale, rotation, alpha, x/y/z). A translation
  is part of the view matrix, so `invalidateChild` expands the dirty
  rect by that matrix. `StateListAnimator` runs the first matching
  animator when the drawable state changes. The view holds it strongly,
  because this VM clears weak references on every GC.
  `android:stateListAnimator` is read from the tag itself, not the theme.
  A ViewGroup layout animation binds in `dispatchDraw`: each child gets a
  clone whose start offset is `LayoutAnimationController.getDelayForView`.
  `LayoutTransition` fades a child in on add and out on remove.
  `startViewTransition` keeps a disappearing child parented until the fade
  ends, and `dispatchDraw` draws `mDisappearingChildren` after the live
  children. Change-type animators are stored and not started, so sibling
  positions are not animated. `AnimatedVectorDrawable` clones the target
  animators on `start`, sets them on `VectorDrawable.getTargetByName`
  (groups and paths: trim, color, stroke, transforms) and invalidates
  itself each frame. pathData morphs when the two paths have the same
  commands: `PathParser.canMorph` compares `PathData` nodes, and
  `setPathData` rebuilds the path. A fraction outside 0..1 extrapolates
  the first or last keyframe interval, so overshoot and anticipate move
  a property past its values. View
  tweens apply in `draw(Canvas, ViewGroup, long)`: the animation matrix
  is concatenated
  with the view matrix, and the animation alpha multiplies the view
  alpha. While `getTransformation` asks for another frame the parent is
  invalidated, so a translation is not clipped to the view's layout rect.
  `fillAfter` false clears the animation after the end frame.
  `AnimationUtils.loadAnimation` loads `set`, `alpha`, `scale`, `rotate`
  and `translate`. `loadLayoutAnimation` loads `layoutAnimation` and
  `gridLayoutAnimation`. Other animation tags stay an identity alpha.
  `loadInterpolator` loads linear, accelerate, decelerate,
  accelerateDecelerate, cycle, anticipate, overshoot, anticipateOvershoot,
  bounce and path. ProgressBar still
  drives its own `AlphaAnimation`. Accessibility classes are value holders
  since no accessibility service runs.
- ProgressBar family (AOSP ports): determinate progress sets drawable
  levels per layer id (`android:id/progress`, `secondaryProgress`,
  `background`), indeterminate starts an Animatable drawable or cycles
  levels with an AlphaAnimation. Bitmap layers are tiled with a repeating
  BitmapDrawable clone (keeps the tint; RatingBar stars). The Material
  spinners are `com.android.internal.graphics.drawable.
  AnimationScaleListDrawable`, which keeps its static child so a spinner
  screenshot does not depend on the phase.
  AbsSeekBar adds the thumb,
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
- SQLite (WS6, host acceptance in): the amalgamation
  (`third_party/sqlite`, `THREADSAFE=1`, `OMIT_LOAD_EXTENSION`) links into
  the host binary and the NRO. `SQLiteNative` handles are `sqlite3` and
  `sqlite3_stmt` pointers. `nOpen` maps Android paths through
  `platform_map_path` (`:memory:` stays as it is). Queries materialize
  every row in `SQLiteDatabase.runQuery`. `nFinalize(0)` is a no-op.
  Result codes become the matching `SQLiteException` subclass.
  `ContextImpl.openOrCreateDatabase` opens `getDatabasePath`. Natives drop
  the GIL around open, prepare, step and close. The Switch object is
  compiled `SQLITE_OS_OTHER` and `SQLITE_OMIT_WAL`; `sqlite_vfs_switch.c`
  registers a POSIX VFS (libnx routes `sdmc:` paths) and installs pthread
  mutexes before the first open. Locks are process-local.
  `tests/apps/store` covers helper create, upgrade on a second process,
  selection arguments, a rolled-back transaction, a constraint failure, a
  read-only open, and SharedPreferences across the two runs. It also
  checks DatabaseUtils, a cursor window, settings, media and FileProvider.
- `CursorWindow` is a Java row table. Get and put use absolute indexes
  (`row - startPosition`). The default budget is 2 MiB. A value that does
  not fit is refused and `fillWindow` drops that row and stops.
  `AbstractCursor.fillWindow` calls `DatabaseUtils.cursorFillWindow`.
  `SQLiteStatement.simpleQueryForBlobFileDescriptor` writes the bytes to
  a temp file and returns a read-only `ParcelFileDescriptor`. A SQL NULL
  returns null. No row throws `SQLiteDoneException`.
- Settings and media providers are installed by
  `FrameworkProviders.install` at the start of
  `ActivityThread.installProviders`, before manifest providers. Settings
  name/value rows persist as URL-encoded lines under
  `/data/local/tmp/settings/{system,secure,global}` (mapped with the data
  root, so a second host run sees them). Media rows live in the process.
  `Settings.System`/`Secure`/`Global` get and put go through
  `ContentResolver`. A null put deletes the row.
- `androidx.core.content.FileProvider` is the framework copy of the
  AndroidX class (boot dex wins over an app copy). The manifest parser
  stores `android:grantUriPermissions`. `attachInfo` rejects an exported
  provider and a provider that does not grant URI permissions. Paths come
  from `android.support.FILE_PROVIDER_PATHS`. A file must sit on a
  configured root or on `root + "/"`. Photo-picker and cloud-media
  helpers on `MediaStore` (`createDeleteRequest`, `getVersion`, volume
  sets) are not implemented; a missing method returns the VM stub.
- Audio (WS7, built on the host): one 48 kHz stereo float mixer
  (`src/android/audio_mixer.c`) is the `platform_audio_start` callback.
  It mixes SoundPool clips, MediaPlayer clips, AudioTrack static clips
  and streams, ToneGenerator sines and OpenSL ES buffer queues. Decoders
  in `audio_decode.c` are WAV (PCM 8/16 and float), Ogg Vorbis
  (`third_party/stb/stb_vorbis.c`) and MP3 (`third_party/minimp3`, no
  SIMD). The callback never takes the VM lock. Java natives release that
  lock before decode and before a blocking stream write. The stream is
  started on the first play and not stopped. Headless and Switch both
  pull 1024 frames and, on Switch, discard them; audren/audout is WS10.
  `android.media.MixDebug` reports which sources have contributed a
  non-silent sample. Vibrator already calls `platform_vibrate`.
- OpenGL ES (WS8, built): see 6.6.1.

### 6.6.1 OpenGL ES and EGL

- Bindings: `tools/gen_gles.py` reads the exact Java signatures of
  `android.opengl.GLES10/10Ext/11/11Ext/20/30/31/31Ext/32` from android.jar
  (javap) and the C prototypes from the Khronos headers, pairs each method
  with its C function (array + offset, java.nio buffer at its position,
  String, int/long offset into a bound buffer) and writes the Java classes,
  `src/android/android_gles_gen.c` and `src/android/gles_funcs.h`
  (X-macro of every entry point). It also writes the
  `javax.microedition.khronos.opengles` GL10/GL11 interfaces and
  `com.google.android.gles_jni.GLImpl`, which forwards to the GLES10/11
  statics. Methods that do not pair (sized string outputs, String[]
  inputs, mapped buffers) are hand-written in `android_gles_special.c`;
  the generator reads its registration table and emits a logging fallback
  for the rest (the KHR debug callbacks and message logs). Regenerate after
  changing the generator; never edit the outputs.
- Entry points: every GL ES and EGL function is called through the `sa_gl`
  / `sa_egl` pointer tables (`android_gl.h`). The host dlopens libEGL,
  libGLESv2 and libGLESv1_CM the first time an EGL display is requested,
  so the binary does not link GL and a machine without it reports
  EGL_NO_DISPLAY. The Switch links Mesa statically when the switch-mesa
  portlib is installed (`SA_HAVE_EGL`, set by Makefile.switch); otherwise
  GL reports itself unavailable. A GL call with no resolved entry point
  logs once and returns 0. glFinish, glReadPixels and glClientWaitSync
  release the GIL; other GL calls hold it.
- EGL: `EGL14` and the EGL10 implementation (`com.google.android.gles_jni.EGLImpl`,
  returned by `EGLContext.getEGL()`) are Java over the framework-internal
  `android.opengl.EGLNative`. Display, config and context handles are the
  real EGL handles as longs; a surface handle is a pointer to a C `SaSurf`
  record (real surface, size, window flag), so window surfaces can be
  replaced under the app. Configs are chosen as pbuffer configs
  (EGL_WINDOW_BIT is rewritten to EGL_PBUFFER_BIT, and reported back on
  query); EGL_RECORDABLE_ANDROID and EGL_FRAMEBUFFER_TARGET_ANDROID are
  dropped. The host uses the surfaceless Mesa platform.
- Window surfaces: `eglCreateWindowSurface` accepts a Surface,
  SurfaceView, SurfaceHolder or SurfaceTexture (as AOSP) and creates a
  pbuffer the size of the Surface's software buffer queue. On
  `eglSwapBuffers` the frame is read back (`EGLNative.nReadWindow`: default
  framebuffer, any bound FBO or pixel pack buffer restored, rows flipped,
  RGBA to unpremultiplied ARGB, alpha forced for opaque queues) into the
  queue's back buffer through `Surface.lockGlBuffer` /
  `unlockGlBufferAndPost`, which posts and paces it like
  `unlockCanvasAndPost`. When the queue size changed, the pbuffer is
  replaced after the swap (kept current). SurfaceView and TextureView
  therefore show GL frames with no special casing, and views over a
  GLSurfaceView composite normally. A direct NWindow path for fullscreen
  GL on the Switch is a later optimisation.
- Native EGL: the NDK shim resolves `egl*` through `sa_egl_native_proc`
  before the driver (`sa_gl_proc`). `eglGetDisplay` uses the same
  surfaceless display as Java. `eglChooseConfig` rewrites
  EGL_WINDOW_BIT to EGL_PBUFFER_BIT and drops the two Android-only
  attributes; `eglGetConfigAttrib` reports the window bit again.
  `eglCreateWindowSurface` accepts an `ANativeWindow` (magic `SANW`),
  makes a pbuffer of that window's size and wraps it in an `SaSurf`
  (magic `SASU`, first field, plus the window pointer). `eglSwapBuffers`
  on that surface reads pixels with the same conversion as
  `nReadWindow` (GIL released around the GL call) and posts them with
  `anw_post_argb`. Config handles stay raw driver pointers. Java's
  `EGLNative` path is unchanged: it still readbacks in Java and does
  not go through these wrappers.
- GLSurfaceView is AOSP's (GLThread state machine, EglHelper, the default
  config, context and window surface factories). If the GL thread cannot
  bring EGL up, it logs the exception and the view draws an "OpenGL ES
  unavailable" panel (#202020) instead of crashing the app.
- GLUtils uploads Bitmaps (unpremultiplied ARGB in Java, D10) as
  premultiplied data like Android, converting to RGBA/RGB/ALPHA/LUMINANCE
  bytes or 565/4444/5551 shorts. `android.opengl.Matrix` and `GLU` are
  full ports. Not yet: EGL15 syncs and images, eglCreatePbufferFromClientBuffer,
  pixmaps, ETC1/ETC1Util, GLDebugHelper, SurfaceTexture.updateTexImage
  (external textures).
- Java fields read from C: `java.nio.Buffer.position`, `limit` and
  `elementSizeShift` (by `gles_buffer`), besides `backing` and
  `byteOffset` (by `vm_buffer_address`, declared in vm.h).

### 6.7 Native libraries

- `System.loadLibrary("foo")` / `System.load(path)` reach
  `nativeloader_load_library` (src/nativeloader/elf_loader.c). Libraries
  come from the APK's `lib/<abi>/` (`x86_64` on the host, `arm64-v8a` on
  AArch64 and the Switch; `SA_NATIVE_ABI`). Absolute paths are read from
  the file system when they exist, else by file name from the APK, so
  `ApplicationInfo.nativeLibraryDir` (`/data/app/<pkg>/lib/<x86_64|arm64>`)
  paths work. `os.arch` (and so `Build.CPU_ABI`) reports the real CPU.
  A library already loaded is not loaded again and its JNI_OnLoad does
  not rerun; a missing one gives Android's message
  (`dlopen failed: library "libx.so" not found`) as UnsatisfiedLinkError.
- Loading: PT_LOAD segments are copied into an anonymous mapping,
  DT_NEEDED libraries load first (shim names are skipped), relocations
  are applied (RELR / DT_ANDROID_RELR, Android packed APS2
  (DT_ANDROID_RELA), RELA, JMPREL; AArch64 ABS64, GLOB_DAT, JUMP_SLOT,
  RELATIVE, IRELATIVE and their x86-64 equivalents), segments get their
  final protections (RELRO read-only), then DT_INIT and DT_INIT_ARRAY run,
  then JNI_OnLoad (with a JNI frame) whose version is checked like ART.
  Constructors and JNI_OnLoad run with the GIL released, like JNI calls.
  ELF TLS is not supported (TLS relocations fail the load); NDK code for
  minSdk < 29 uses emulated TLS, which needs nothing from us.
- Symbol resolution follows Android's linker: the shim first (standing in
  for the global group: libc, libm, libdl, liblog, libandroid, libz, GL),
  then the library's local group breadth-first (itself, then its
  DT_NEEDED tree). Weak undefined imports become 0. An import nothing
  provides is bound to a generated stub (x86-64 or AArch64 code in a
  per-library page) that logs `native code called X, which no library
  provides` once and returns 0; the load succeeds with a warning listing
  the unresolved names. This trades Android's load failure for partial
  coverage, like framework auto-stubbing.
- `nativeloader_find_symbol` (JNI binding) searches every loaded library.
  libdl's dlopen/dlsym/dladdr go through the same loader; dlopen of a
  system library name returns a handle whose dlsym searches the shim.
- Shim (`shim_libc.c`, `shim_android.c`): bionic names mapped to the host
  C library where the ABI matches, with wrappers where bionic differs:
  Android path mapping for open/fopen/stat/opendir/..., bionic sysconf
  numbering, `__errno`, fortify `_chk` entry points, `__sF`
  (stdin/stdout/stderr for code built before API 23) and FILE* translation,
  stack protector, `__cxa_atexit` (native destructors never run), pthread
  mutexes and condition variables (bionic's 40/48-byte objects hold a
  pointer to a lazily created host object, so zeroed static initializers
  and the recursive initializer work), bionic pthread_attr_t, liblog
  (to sa_log), AAssetManager (reads `assets/` from the APK; no file
  descriptors), `__system_property_get` (SDK 29 values), zlib, and GL/EGL
  names resolved from the driver (`sa_gl_proc`, 6.6.1), with the EGL
  calls in 6.6.1 intercepted first. `ANativeWindow_*` and
  `ANativeActivity_finish` / `setWindowFormat` / `setWindowFlags` /
  `showSoftInput` / `hideSoftInput` are real symbols. Struct layouts
  (stat, dirent, tm, timespec) match bionic on 64-bit Linux hosts; the
  Switch needs translation wrappers for newlib's. newlib also has no
  getpagesize, posix_memalign or pipe: the shim provides the first two
  and pipe returns ENOSYS.
- Code memory: mmap/mprotect on the host. On the Switch each mapping is
  page-aligned heap memory (`memalign`) mirrored into the alias region
  (`virtmemFindCodeMemory`) with `svcMapProcessCodeMemory` on
  `envGetOwnProcessHandle()` (the current-process pseudo handle is
  rejected) and made read-write with `svcSetProcessMemoryPermission`.
  After relocations, executable segments become read-execute and the rest
  stay read-write or read-only. Horizon rejects permission values above 5,
  so a segment that asks for write and execute keeps execute. Releasing a
  library calls `svcUnmapProcessCodeMemory`, drops the virtmem reservation
  and frees the heap pages. hbloader hints syscalls 0x73, 0x77 and 0x78
  only for an application launch; an applet fails the load with that
  reason in the error string. newlib struct layouts (stat, dirent, O_*
  flags, clock ids) still need translation wrappers.
- NativeActivity (`android.app.NativeActivity`): public API matches
  android.jar, including the hidden natives `loadNativeCode` and the
  lifecycle/surface forwards. `PhoneWindow.takeSurface` is a no-op, so
  the content view is a full-bleed SurfaceView (format RGBA_8888, set
  before the first `updateSurface`). `loadNativeCode` dlopens
  `ApplicationInfo.nativeLibraryDir`/`lib<name>.so` (the loader falls
  back to `lib/<abi>/` in the APK) and calls the entry, default
  `ANativeActivity_onCreate`, with the NDK `ANativeActivity` layout
  (sdkVersion at offset 48, instance at 56, 80 bytes). The entry runs
  before the surface exists. `surfaceCreated` wraps the Surface as an
  ANativeWindow and calls `onNativeWindowCreated`;
  `onNativeWindowResized` fires only when the size changes. Lock returns
  RGBA bytes (R, G, B, A); the queue stores ARGB, so unlock converts.
  The previous posted frame is copied into the next lock. Input-queue
  callbacks are not invoked.
- OpenSL ES (WS7, symbols in this shim): `slCreateEngine` and the
  `SL_IID_*` pointer objects (engine, object, play, volume, buffer
  queue, output mix, Android simple buffer queue). GetInterface matches
  pointer identity or the 16-byte UUID. A player copies each enqueued
  buffer and runs the queue callback on the audio thread after the
  mixer lock is released. AAudio is not implemented.
- Not yet: ALooper and AInputQueue, AAudio, AConfiguration,
  ASensorManager, libc++_shared coverage checks, socket APIs.
  NativeActivity and native EGL have not been run on hardware.

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
- Launcher: a C screen listing the APKs in `sdmc:/switch/switchapk/apks`
  (D-pad, stick or touch, A runs, + exits). Each row shows the launcher
  activity's `android:label` and `android:icon` when the APK has them
  (else the application's, else the file name without `.apk`). Labels and
  icons are read with the zip, binary XML and resource table code, at
  240 dpi (`apk_read_identity` in `src/app/apk_info.c`). Bitmap icons are
  drawn; XML drawables (adaptive icons, vectors) are skipped. `argv[1]`
  ending in `.apk` skips the list (nxlink). When the app ends the NRO
  reloads itself through hbloader (`envSetNextLoad`). A non-zero exit
  shows the last 48 INFO+ log lines (`sa_log_recent`) on an error screen.
- GL: Mesa (switch-mesa) is linked when installed (6.6.1); not yet run on
  hardware.
- Not yet: device audio output (the mixer callback runs and the samples
  are discarded, as on the headless backend; audren/audout remains),
  rumble, 1080p docked rendering. Native libraries can be mapped (6.7)
  but have not been run on hardware, and neither has a NativeActivity.

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
- Launcher identity (`apk_read_identity`) is checked on the host with
  `switchapk-host --apk-info` (`tests/apps/labeled/check_info.sh`), without
  booting the VM.
- Real-world APKs: keep a local (not committed) corpus of open-source APKs
  (F-Droid) and track results in `docs/COMPATIBILITY.md` (to be created).
