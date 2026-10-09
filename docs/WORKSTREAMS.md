# Workstreams

The remaining work split into packages that different agents can own in
parallel. Each package lists scope, the files it owns, what it depends on,
the interfaces it must honour (see `docs/ARCHITECTURE.md`), and acceptance
criteria. Keep the status table current: it is how parallel agents avoid
duplicating work.

## How to use this file (protocol for agents)

1. `git pull` the integration branch and read `CLAUDE.md`, `docs/PLAN.md`,
   this file and the relevant sections of `docs/ARCHITECTURE.md`.
2. Pick a package whose dependencies are done (or whose dependencies are
   satisfied by stubs). Claim it: set `Status` to `in progress`, fill
   `Owner` (agent/session id and date), commit that change alone and push
   it before starting, so other agents see the claim.
3. Stay inside the files the package owns. If you must change a shared
   file or an interface, keep the change minimal, note it under
   "Interface changes" in `docs/PLAN.md`, and update `ARCHITECTURE.md`.
4. Keep the build green on every push (`make` and `tests/run_dex_test.sh
   tests/dex/VmTest.java` must pass once WS0 has landed). Missing
   collaborator classes are replaced by minimal compilable versions with
   the exact Android signatures, marked `// TODO(WSn)`.
5. When done: tick the checklist items in `docs/PLAN.md`, set the status
   here to `done`, add a line to `docs/SESSION_LOG.md`, push.
6. Long packages: push partial progress at least every few hours with the
   status still `in progress` and a note of what remains.

## Status table

| ID | Package | Depends on | Status | Owner |
|---|---|---|---|---|
| WS0 | Integration skeleton: framework compiles, natives registered, app runner, first APK on screen | none | done | ws0 2026-09-30 |
| WS1 | View system core (View, ViewGroup, input dispatch, focus, windows, ViewRootImpl, Choreographer, LayoutInflater, PhoneWindow/DecorView) | WS0 | done (core, surfaces, acceptance sample, context menus and primary action modes done (action modes landed from the WS4 session); floating toolbar, text selection, and clipToOutline landed (tests/apps/outline); ViewDebug annotations landed (tests/apps/viewdbg); DisplayCutout landed (tests/apps/cutout)) | session 7, 2026-10-02 |
| WS2 | Text: android.text + TextView/EditText + IME bridge | WS0, WS1 (View API) | done | ws2 session 5, 2026-10-01 |
| WS3 | Widgets: layouts, lists/adapters, scrolling, buttons, progress, Toast, PopupWindow, Spinner, Toolbar | WS1 | done (ImageView, compound controls, scrolling and lists landed; progress, popups, Toolbar, grids, adapters, ExpandableListView, AutoCompleteTextView, NumberPicker, the clocks, the ViewAnimator family, DatePicker, CalendarView material delegate and holo week list, TimePicker and their dialogs, GridLayout, SearchView, TabHost, the CursorTreeAdapter family done; VideoView done; RemoteViews done) | session 7, 2026-10-02 |
| WS4 | App model: Activity/ActivityThread lifecycle, manifest, intents, dialogs, menus/ActionBar, services, legacy fragments | WS0 | in progress (lifecycle, fragments, dialogs, ProgressDialog, menus, action bar decor, services, broadcasts, notifications and jobs done; Date/TimePicker dialogs and loaders left) | ws4 session 6, 2026-10-01 |
| WS5 | Animation: android.animation, view.animation, ViewPropertyAnimator, AVD animation | WS1 | done (tweens tests/apps/tween; property animators tests/apps/prop; StateListAnimator, layout animation, LayoutTransition and AnimatedVectorDrawable tests/apps/motion; PathInterpolator, cycle/anticipate/overshoot/bounce and path morph tests/apps/curves). PopupWindow transitions and the toolbar visibility fade still apply immediately (android.transition is not in the tree) | session 12, 2026-10-02 |
| WS6 | Storage: SQLite natives, database/content provider checks, file APIs, SharedPreferences tests | WS0 | done (natives, DatabaseUtils, CursorWindow, Settings and MediaStore, FileProvider; tests/apps/store two-run check) | session 14, 2026-10-02 |
| WS7 | Audio/media: mixer, SoundPool, MediaPlayer, AudioTrack, decoders, platform audio | WS0 | in progress (mixer, WAV/Ogg/MP3, SoundPool, MediaPlayer, AudioTrack, ToneGenerator and the OpenSL buffer queue on the host, tests/apps/audio; Switch output still drained, audren is WS10) | session 10, 2026-10-02 |
| WS8 | OpenGL ES + EGL: bindings, GLSurfaceView, EGL window, compositing | WS0, WS10 for device | in progress (bindings, EGL, GLSurfaceView, window surfaces and tests/apps/gles done on host Mesa; Switch GL on hardware, GLES1 verification, EGL15 syncs/images and SurfaceTexture left) | session 9, 2026-10-02 |
| WS9 | Native loader: ELF loader, bionic shim, JNI_OnLoad, NativeActivity, libandroid | WS0 | in progress (ELF loader, bionic shim, JNI_OnLoad, System.load/loadLibrary and tests/apps/ndk done on x86-64 and AArch64 Linux; Switch code memory via svcMapProcessCodeMemory done, not run on hardware; NativeActivity, ANativeWindow and native EGL window surfaces done on the host, tests/apps/native; OpenSL ES buffer queue is in the shim and shares the WS7 mixer; ALooper, AInputQueue and AConfiguration done on the host (tests/apps/input); newlib struct translation, ALooper/input on the Switch (no pipe/poll in newlib) and AAudio left) | session 16, 2026-10-07 |
| WS10 | Switch platform backend, NRO build, launcher | WS0 (platform.h is stable now) | in progress (NRO boots on hardware; launcher labels and icons landed (tests/apps/labeled); audio, rumble, 1080p docked remain) | session 7, 2026-10-02 |
| WS11 | Networking: java.net sockets, HttpURLConnection, TLS | none | in progress (sockets, DNS, UDP, HttpURLConnection over HTTP/1.1 with transparent gzip, and ConnectivityManager done on the host (tests/dex/NetTest, tests/apps/net); java.util.zip and java.util.jar done (tests/dex/ZipTest); TLS, pooling/cookies and a device run left) | session 19, 2026-10-09 (zip, gzip); session 17, 2026-10-07 |
| WS12 | VM performance and memory | none | not started | |
| WS13 | Test infrastructure and sample apps | WS0 | in progress (Actions workflow uploads switchapk-sd.zip with every tests/apps APK; screenshot runner and VmTest on CI remain) | session 15, 2026-10-02 |
| WS14 | AndroidX / AppCompat / Material Components compatibility | WS1-WS4 | not started | |
| WS15 | System services: sensors (IMU), vibration, battery, connectivity, Settings, misc managers | WS0 | in progress (sensors with fused gravity/rotation/orientation, battery broadcasts, rumble waveforms, PowerManager, and location/telephony/camera answering as absent done on the host, tests/apps/sensors; the IMU axis signs, rumble and psm have not been run on a console) | session 18, 2026-10-08 |
| WS16 | libcore API completeness: members and classes android.jar has in java.* that libcore lacks | none | in progress (session 19: java.lang, java.util and java.util.concurrent gaps first) | session 19, 2026-10-09 |

Parallelism: after WS0 lands, WS1, WS4, WS6, WS7, WS9, WS10, WS11, WS12,
WS13, WS15 can all run at once. WS2, WS3, WS5 start once the View API of
WS1 exists (WS1 should push the public `View`/`ViewGroup` API early, even
with partial behaviour). WS8 needs WS10 for on-device testing but can be
developed against host EGL (mesa llvmpipe/surfaceless) if available.

---

## WS0: Integration skeleton (blocking, do first)

Goal: a compiling tree and a trivial APK rendering on the headless
platform, so everyone else works against a green build.

Scope:
- Make `java/framework` compile: add minimal but signature-exact versions
  of every class referenced and not yet written. Known gaps: `android.view.*`
  (View, ViewGroup, Display, Surface, KeyEvent, MotionEvent, WindowManager,
  WindowManagerImpl, ...), `android.text.*` (TextUtils, ClipboardManager, ...),
  `android.webkit.MimeTypeMap`, `android.opengl.Matrix`,
  `android.media.AudioAttributes`, `android.app.*`. Run
  `javac ... @list` (see DEV_SETUP) to list them.
- `src/android/android_os.c`: Log, MessageQueue poll/wake, PlatformInput
  event pull, `WindowManagerGlobal.nPresent`, `Display.nGetInfo`,
  `InputMethodManager.nRequestText`, `Vibrator$SystemVibrator.nativeVibrate`.
- Replace `src/native/android_stub.c`: `natives_android_register()` calls
  `android_res_register`, `android_graphics_register`, `android_os_register`.
- `src/app/app_runner.c` replacing `app_stub.c` (defines `g_app_apk_path`):
  see ARCHITECTURE 6.5. Host driver passes `--script`, `--screenshots`,
  `--screen WxH@dpi` through to the platform.
- Minimal `ActivityThread`, `Activity` (setContentView(View)),
  `ViewRootImpl` that measures/lays out/draws one view tree and presents.
- `tests/apps/hello/`: an APK with a custom View drawing shapes and text,
  built by a script, and a headless run producing a screenshot.

Acceptance: `make` builds cleanly; `VmTest` passes; the hello APK runs
headless and its screenshot shows the drawing; no crash on quit.

## WS1: View system core

Owns: `java/framework/android/view/**` (except `animation`, `inputmethod`,
`accessibility` which are shared with WS5/WS2), `com/android/internal/policy/**`.

Scope: View (measure/layout/draw, padding, background/foreground,
transforms, visibility, states and drawable states, touch handling with
press/click/long-click, keys, focus and focusable-in-touch-mode, scroll,
scrollbars, fading edges (optional), tags, listeners, saved state, post/
postDelayed, invalidate, attach/detach, nested scrolling API, outline/
elevation (no shadows at first), getLocationOnScreen, hit rects,
ViewTreeObserver, ViewOverlay, TouchDelegate, drawing cache),
ViewGroup (child management, dispatchTouchEvent with intercept and touch
targets, dispatchKeyEvent, focus traversal, drawChild with transforms and
clipping, LayoutParams / MarginLayoutParams, generateLayoutParams from
XML, layout transitions stubbed), MotionEvent/KeyEvent/InputDevice/
KeyCharacterMap, ViewConfiguration, VelocityTracker, GestureDetector,
ScaleGestureDetector, FocusFinder, LayoutInflater (tags, include, merge,
ViewStub, `android:theme`, factories), ContextThemeWrapper, Window/
PhoneWindow/DecorView (content parent, title/action bar decor from the
theme's window attributes, background, fitsSystemWindows no-op insets),
WindowManager/Impl/Global (window stack, z-order, dim), ViewRootImpl
(traversals, input dispatch, fallback keys and synthetic joystick DPAD,
focus navigation), Choreographer, Display, Surface/SurfaceHolder/
SurfaceView (software surface via lockCanvas), TextureView (software),
Menu/MenuItem/SubMenu/MenuInflater/ContextMenu, ActionMode (stub),
WindowInsets, SoundEffectConstants, HapticFeedbackConstants.

Interfaces: ARCHITECTURE 6.4 (event pull, present, display info,
controller mapping).

Acceptance: sample app with nested LinearLayout/FrameLayout from XML,
clickable views reacting to scripted taps, D-pad focus navigation between
buttons, a dialog-style second window, all verified by screenshots.
(Met by `tests/apps/views`. FrameLayout and LinearLayout were ported here
because the decor needs them; WS3 owns them from now on.)

## WS2: Text

Owns: `java/framework/android/text/**`, `android/widget/TextView.java`,
`EditText.java`, `android/view/inputmethod/**`.

Scope: TextUtils, CharSequence utilities, Spanned/Spannable/SpannableString(
Builder)/SpannedString, spans (style, color, size, underline, strikethrough,
clickable/URL, image, leading margin, typeface), Editable, TextWatcher,
InputFilter, InputType, Selection, method.* (movement, key listeners,
transformation incl. password and single line), Layout/StaticLayout/
BoringLayout/DynamicLayout (line breaking, alignment, ellipsizing,
line spacing, includeFontPadding, span rendering), TextPaint, Html
(fromHtml basic tags), format.DateFormat/DateUtils/Formatter, util.Linkify,
TextView (all common attributes, compound drawables, text appearance,
autoLink, ellipsize, maxLines, gravity, hints, shadows, selection
highlight), EditText (cursor, selection, key input from hardware keyboard,
IME via platform text request), InputMethodManager/EditorInfo/
InputConnection/BaseInputConnection, android.text.ClipboardManager.

Acceptance: text-heavy sample (wrapping, spans, ellipsize, RTL-agnostic
alignment) matches expected screenshots; EditText receives scripted
`text` input and fires TextWatcher.

## WS3: Widgets

Owns: `java/framework/android/widget/**` except TextView/EditText.

Scope: FrameLayout, LinearLayout (weights, dividers, baseline), RelativeLayout,
AbsoluteLayout, TableLayout/TableRow, GridLayout, ScrollView,
HorizontalScrollView, NestedScrollView-like behaviour through View APIs,
OverScroller/Scroller, EdgeEffect (simple), AdapterView/AbsListView/
ListView/GridView (recycling, selection, dividers, headers/footers,
fling), ExpandableListView (basic), BaseAdapter/ArrayAdapter/
SimpleAdapter/CursorAdapter/SimpleCursorAdapter, Spinner (dropdown via
PopupWindow, dialog mode), Button, ImageButton, ImageView (scale types,
tint, adjustViewBounds, maxWidth/Height, level), CompoundButton/CheckBox/
RadioButton/RadioGroup/Switch/ToggleButton, CheckedTextView,
ProgressBar (determinate/indeterminate with animation), SeekBar,
RatingBar, Space, ViewAnimator/ViewFlipper/ViewSwitcher, Toast (window
at bottom, timeout), PopupWindow, PopupMenu, ListPopupWindow, Toolbar,
Chronometer, TextClock, NumberPicker, DatePicker/TimePicker (simple),
SearchView (basic), VideoView (placeholder), RemoteViews (apply and reapply).

Acceptance: a widget gallery sample renders like Android's Material look
(framework styles), lists scroll with swipe scripts, controls toggle.

## WS4: App model

Owns: `java/framework/android/app/**`, `src/app/app_runner.c` (after WS0),
`android/content/pm` implementations.

Scope: ActivityThread (manifest parsing, components, providers before
Application.onCreate, AppComponentFactory), LoadedApk/ContextImpl
(file dirs, getSystemService table, getSharedPreferences, databases,
package manager implementation from manifest), Application (lifecycle
callbacks, registerActivityLifecycleCallbacks), Activity (lifecycle incl.
onSaveInstanceState/onRestoreInstanceState and recreation on config
change, setContentView variants, findViewById, startActivity(ForResult),
onActivityResult, finish, onBackPressed, options menu, ActionBar,
window features, runOnUiThread, requestPermissions granted immediately,
setRequestedOrientation, immersive/fullscreen flags as no-ops, onUserLeaveHint),
Instrumentation (minimal), Dialog/AlertDialog/ProgressDialog/
DatePickerDialog/TimePickerDialog, Fragment/FragmentManager (legacy
framework fragments), Service/IntentService (in-process, started and
bound), BroadcastReceiver dispatch (in-process registerReceiver/
sendBroadcast, manifest receivers for own broadcasts), PendingIntent,
AlarmManager (Handler-backed), NotificationManager (log only),
ActivityManager (memory info, running processes), KeyguardManager,
UiModeManager, DownloadManager (stub), NativeActivity glue with WS9.

Acceptance: multi-activity sample (list -> detail -> result back),
dialogs, options menu via BUTTON_START, back navigation, pause/resume on
focus loss, state survives a simulated docked/handheld switch.

## WS5: Animation

Owns: `java/framework/android/animation/**`, `android/view/animation/**`,
`android/view/ViewPropertyAnimator.java`.

Scope: ValueAnimator (Choreographer driven, duration scale), ObjectAnimator
(property reflection and android.util.Property), AnimatorSet,
PropertyValuesHolder, Keyframe, evaluators, TimeInterpolator and all
framework interpolators (incl. PathInterpolator), AnimatorInflater and
AnimationUtils XML loading (animator, set, objectAnimator, alpha, scale,
translate, rotate, layoutAnimation), LayoutTransition (basic), view tween
animations applied in View.draw, StateListAnimator (basic),
AnimatedVectorDrawable real animation (target groups/paths).

Acceptance: sample with property animations, tween animations and an
AVD checkbox transition produces the expected intermediate frames
(scripted screenshots at fixed times).

## WS6: Storage

Owns: `src/android/android_sqlite.c`, `java/framework/android/database/**`,
`android/provider/**` (with WS15), storage tests.

Scope: SQLite natives (open with flags, prepare/bind/step/column,
changes/last insert id, error codes to SQLiteException subclasses),
Makefile rules compiling `third_party/sqlite/sqlite3.c` (THREADSAFE=1,
OMIT_LOAD_EXTENSION), DatabaseUtils, CursorWindow semantics where apps
depend on them, android.provider.BaseColumns/Settings/MediaStore stubs,
FileProvider support through ContentResolver, path mapping tests.

Acceptance: sample using SQLiteOpenHelper (create/upgrade), queries with
selection args, transactions, and SharedPreferences persisting across two
runs of the host driver.

## WS7: Audio and media

Owns: `src/android/android_media.c`, `java/framework/android/media/**`,
platform audio functions (with WS10 for Switch).

Scope: mixer (voices with volume/pan/rate, streams), SoundPool (load from
assets/resources/files, play/pause/stop/setVolume/setRate/loop, max
streams, OnLoadCompleteListener), MediaPlayer (prepare/prepareAsync,
start/pause/seek/loop/volume, listeners, data sources: asset fd, file,
resource via `MediaPlayer.create`), AudioTrack (static and stream modes,
PCM 8/16/float), AudioManager (stream volumes, focus requests granted),
AudioAttributes/AudioFormat, ToneGenerator (simple), Vibrator via
platform. Decoders: WAV, OGG Vorbis (stb_vorbis, add to third_party),
MP3 (minimp3 on host; mpg123 or minimp3 on Switch).

Acceptance: headless audio callback receives non-silent mixed samples for
a sample app playing a SoundPool effect over MediaPlayer music (assert via
a debug sample counter or dump option). Met on the host by tests/apps/audio
(MediaPlayer, SoundPool, AudioTrack, ToneGenerator and OpenSL together;
`android.media.MixDebug` mask and nonzero-frame count). Switch playback
hardware is still WS10.

## WS8: OpenGL ES and EGL

Owns: `src/android/android_opengl.c` (+ generated files),
`java/framework/android/opengl/**`, `javax/microedition/khronos/**`.

Scope: generate GLES20/GLES30 (and GLES31 if cheap) Java classes and C
natives from the Khronos headers (script in tools/), with Buffer and array
overloads; GLUtils (texImage2D from Bitmap: convert ARGB -> RGBA
premultiplied), android.opengl.Matrix (pure Java), EGL14/EGLExt/EGL10
wrappers, GLSurfaceView (GL thread, renderer callbacks, render modes,
EGLConfigChooser), window surface on Switch from `platform_native_window()`,
presentation handover between software frames and EGL.

Acceptance: a spinning textured cube sample renders on Switch; on host,
if EGL is unavailable the GL classes fail gracefully (GLSurfaceView shows
a message instead of crashing).

## WS9: Native loader and NDK shim

Owns: `src/nativeloader/**`, `src/app` glue for NativeActivity with WS4.

Scope: ARCHITECTURE 6.7. Start on host x86-64 with a test .so built by the
NDK clang for x86_64 (or by host clang with `-nostdlib` and bionic-like
imports), then AArch64 on Switch. Include libc symbol coverage tracking
(log unresolved imports).

Acceptance: an NDK sample with JNI functions (strings, arrays, callbacks
into Java, exceptions) passes on host; a NativeActivity sample drawing via
ANativeWindow_lock shows up; on Switch a GL NativeActivity renders.

## WS10: Switch backend and NRO

Owns: `src/platform/platform_switch.c`, `src/app/main_switch.c`,
`Makefile.switch`, launcher UI, romfs layout.

Scope: ARCHITECTURE 7 (Switch section): framebuffer present with swizzle
and letterboxing, docked/handheld switching (PEV_RESIZE), input mapping
(buttons, sticks, touch with pointer ids), applet lifecycle (focus loss,
HOME, exit request), swkbd, audren audio thread, rumble, shared fonts,
romfs framework files, launcher listing `sdmc:/switch/switchapk/apks/*.apk`
with icons and labels read from each APK (use our own resource code),
error screen showing Java exceptions instead of a black screen, large
heap (run as application with full memory where possible; detect applet
mode and warn).

Acceptance: `switchapk.nro` builds with devkitA64; boots on hardware or
Ryujinx/yuzu-compatible emulator; launcher shows APKs; the WS0 hello APK
runs with touch and controller input.

## WS11: Networking

Owns: `java/libcore/java/net/**`, `javax/net/**`, `src/native/java_net.c`.

Scope: Socket/ServerSocket/DatagramSocket/InetAddress (getaddrinfo),
URLConnection/HttpURLConnection (HTTP/1.1, chunked, redirects), HTTPS via
mbedtls on Switch and host OpenSSL or mbedtls, android.net.
ConnectivityManager/NetworkInfo reporting WiFi state (nifm on Switch).
Many apps only need "no network" to work gracefully: make offline
behaviour clean (UnknownHostException) before full support.

Status: sockets, DNS, UDP, HTTP/1.1 with transparent gzip and
ConnectivityManager are in (see ARCHITECTURE 5.1), and so are
java.util.zip and java.util.jar (5.2). Remaining: TLS (mbedtls;
`javax.net.ssl` has only the exception classes, https throws
SSLHandshakeException), connection pooling, CookieManager, NIO socket
channels, and running tests/apps/net on hardware.

## WS12: VM performance and memory

Owns: `src/vm/**` (coordinate interface changes).

Ideas: computed-goto dispatch, instruction quickening (resolved field
offsets, vtable indexes cached in the instruction stream), inline caches
for invoke-virtual/interface, faster string and array natives
(System.arraycopy, String ops), allocation fast path (bump allocator per
size class instead of calloc + ptrmap), incremental or generational GC to
avoid long pauses, memory limit awareness on Switch, profiling mode
(method call counts, `-vv` sampling). Must keep VmTest green.

## WS13: Tests and samples

Owns: `tests/**`, `tools/build_apk.sh` (new), CI configuration.

Scope: script to build an APK from `tests/apps/<name>` (aapt2 compile/link
against android.jar, javac against android.jar, d8, zip, no signing
needed), a runner that executes all app scripts headless and compares
screenshots with goldens (per-pixel tolerance), log scanning for crashes
and STUB lines, a GitHub Actions workflow running `make`, VmTest and app
tests on Linux.

`.github/workflows/package.yml` is the packaging half. On push, on a
pull request from a fork, and on manual dispatch, it fetches the SDK and
devkitPro, runs `make` and `make -f Makefile.switch dist`, and uploads
`build/switch/switchapk-sd.zip` as the `switchapk-sd` artifact. The zip
holds every app under `tests/apps`. Still open: the screenshot runner,
log scanning, and CI for VmTest and the app tests.

## WS14: AndroidX / AppCompat / Material

Scope: build samples using AppCompatActivity, ConstraintLayout,
RecyclerView, Material Components, Fragments (androidx), ViewModel/
LiveData, Room (after WS6), WorkManager (stub-friendly). Fix framework
gaps they hit (hidden fields accessed by reflection, View APIs, theme
attributes, `ViewCompat` paths). Track results in docs/COMPATIBILITY.md.

## WS15: System services

Owns: `android/hardware/**`, `android/os/*Manager*`, `android/net/Connectivity*`,
`android/provider/Settings*`, `android/location/**` (stubs).

Scope: SensorManager with accelerometer/gyroscope from Joy-Con/console
IMU (PEV_SENSOR), rotation vector derived, listeners at requested rates;
Vibrator/VibratorManager; BatteryManager and ACTION_BATTERY_CHANGED sticky
intent (psm on Switch); ConnectivityManager; Settings.Secure.ANDROID_ID
stable per install; LocationManager (no providers); TelephonyManager
(no telephony); Camera/Camera2 (no cameras, graceful).

Status: all of the above is in on the host (see ARCHITECTURE 6.8 and
tests/apps/sensors). ConnectivityManager came with WS11 and Settings
with WS6. Remaining: run the Switch side on a console (six-axis axis
signs, `ACCEL_SIGN` in platform_switch.c; HD rumble; psm), and sensors
the console could add later (the original model's ambient light sensor
through lbl).

Acceptance: an app sees the accelerometer, gyroscope and the fused
sensors at the rates it asks for, tilting and turning the console move
them the right way, and battery and rumble work on hardware.

## WS16: libcore API completeness

Owns: `java/libcore/java/**` members that are missing (not the
subsystems other packages own: java.net is WS11, java.util.zip/jar are
WS11 too).

`tools/api_check.py -p <package>` lists what android.jar declares and
libcore lacks. A missing libcore method is not auto-stubbed: the app
gets NoSuchMethodError. Covariant overrides count (an app compiled
against android.jar calls `IntStream.parallel()` with an IntStream
return type). Skip what libcore cannot express yet (java.time,
java.security certificates, SequencedCollection from API 35) unless an
app needs it.

Scope, roughly by how often apps hit it: String, StringBuilder,
Character, Integer/Long, Math/StrictMath, Class (annotations, enclosing
members, generic strings), Arrays, Collections, streams, atomics,
ConcurrentHashMap, locks, CompletableFuture; missing classes such as
ThreadLocalRandom, Scanner, ResourceBundle, ObjectInput/OutputStream,
the piped streams, StreamTokenizer, AbstractOwnableSynchronizer,
StampedLock, ForkJoin tasks.

Acceptance: each batch has a `tests/dex` program that matches OpenJDK,
and `api_check.py` shows no gaps in the packages it covers except the
documented exclusions.
