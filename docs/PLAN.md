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

## Current state (end of session 5, see SESSION_LOG.md)

Working:
- VM core, libcore, JNI, reflection, threads; VmTest passes.
- `src/gfx` renderer, fonts, images, clip masks (visual test in
  `tests/c/gfx_test.c`).
- Multi-package resource table; AssetManager and graphics natives (C).
- Headless platform with scripted input and screenshots.
- `tools/fetch_toolchains.py` fetches aapt2, android.jar, builds
  framework-res.apk, fetches SQLite; devkitPro fetch implemented (Docker
  Hub may rate-limit: retries built in).
- `java/framework` compiles to `build/java/framework.dex`; resource,
  graphics and OS natives are registered; `app_runner` opens an APK and
  enters `ActivityThread.main`.
- View system core (WS1, most of it): AOSP ports of View, ViewGroup,
  MotionEvent/KeyEvent/InputDevice/KeyCharacterMap, ViewConfiguration,
  VelocityTracker, GestureDetector, ScaleGestureDetector, FocusFinder,
  ViewTreeObserver, LayoutInflater (include, merge, ViewStub, themes),
  Choreographer, ViewRootImpl (dirty-rect redraw, touch mode, focus
  navigation, synthetic D-pad), WindowManagerGlobal (window stack,
  input routing, A/B fallbacks, dim, compositing), PhoneWindow/DecorView
  (theme window attributes, screen_simple decor), MenuInflater and an
  internal menu model, Activity as Window.Callback. FrameLayout and
  LinearLayout are ported (needed by the decor).
- `tests/apps/hello`, `tests/apps/views` and `tests/apps/surface` pass their screenshot checks
  (views: XML layouts with weights, include, ViewStub, selector states,
  tap, D-pad focus, A/B buttons, long press, dim-behind second window).
- Text engine (WS2): Spanned/Spannable, spans, TextUtils, TextPaint,
  StaticLayout, BoringLayout, DynamicLayout. TextView measures and draws
  through Layout (wrapping, gravity, ellipsize, hints, compound
  drawables, password and single-line transformations). EditText takes
  hardware keys and scripted `text` through InputConnection.commitText.
  Html.fromHtml (basic tags), Linkify and DateUtils/DateFormat/Formatter
  are in place. autoLink runs Linkify when text is set. `tests/apps/text`
  checks screenshots and in-process logic.

Not started: most widgets (WS3), the rest of the app model (WS4: action
bar decor, ProgressDialog), animation (WS5) and the other
post-WS0 packages.

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
- [x] Framework compiles (signature-exact skeletons for all referenced classes)
- [x] android_os.c natives (Log, MessageQueue, PlatformInput, present, display info, IME request, vibrate)
- [x] Register android natives; remove android_stub.c
- [x] app_runner.c (APK open, resources, dex list, data dirs, ActivityThread.main)
- [x] Minimal ActivityThread/Activity/ViewRootImpl/WindowManagerGlobal
- [x] tests/apps/hello + build script + headless screenshot

### M2
- [ ] WS1 view system core
  - [x] View, ViewGroup, input events, ViewConfiguration, VelocityTracker, gesture detectors, FocusFinder
  - [x] ViewRootImpl, Choreographer, WindowManagerGlobal (window stack, routing, compositing)
  - [x] LayoutInflater, ViewStub, PhoneWindow/DecorView, MenuInflater + menu model, Window.Callback
  - [x] tests/apps/views acceptance sample
  - [x] SurfaceView/SurfaceHolder (software lockCanvas, paced producers), TextureView (software)
  - [x] context menu presentation (MenuDialogHelper, from WS4)
  - [ ] action mode presentation (needs the action bar decor)
  - [ ] clipToOutline, ViewDebug annotations, DisplayCutout
- [x] WS2 text and IME
  - [x] Spanned/Spannable, spans, TextUtils, Layout/StaticLayout/BoringLayout/DynamicLayout
  - [x] TextView measure, draw, common XML attributes, transformations; tests/apps/text screenshots
  - [x] movement and key listeners, EditText, BaseInputConnection, IME `text` delivery
  - [x] Html.fromHtml, Linkify, DateUtils
- [ ] WS3 widgets
  - [x] ImageView (scale types), Button, ImageButton, CompoundButton,
    CheckBox, RadioButton, RadioGroup, ToggleButton, Switch, Space;
    tests/apps/widgets
  - [x] Scroller, OverScroller, EdgeEffect, ScrollView,
    HorizontalScrollView; tests/apps/scroll
  - [x] Adapter, ListAdapter, SpinnerAdapter, BaseAdapter, ArrayAdapter,
    Filter, AdapterView, AbsListView, ListView; tests/apps/list
  - [x] RelativeLayout (landed from WS4, which needed it); tests/apps/relative
  - [x] ProgressBar (determinate, indeterminate, tints, tiling), AbsSeekBar,
    SeekBar, RatingBar, AnimationScaleListDrawable; tests/apps/progress
  - [x] PopupWindow, ListPopupWindow, DropDownListView, PopupMenu
    (MenuPopupHelper), AbsSpinner, Spinner (drop-down and dialog), Toast;
    tests/apps/popups (touch and D-pad)
  - [ ] Toolbar, ActionMenuView, and the rest
- [ ] WS4 app model
  - [x] Dialog, AlertDialog (+Builder: message, buttons, items, single and
    multi choice, custom view) on the framework's material alert layouts,
    CheckedTextView, ActionBar API; tests/apps/appmodel
  - [x] ProgressDialog (spinner and horizontal); tests/apps/progress
  - [ ] DatePicker/TimePicker dialogs
  - [x] options menu (+ button falls back to MENU) as an overflow-style
    popup, context menus and sub menus as dialogs
  - [ ] action bar decor (Theme.Material with title and overflow)
  - [x] lifecycle in AOSP order, results, launch modes and flags, saved
    state, recreation on docked/handheld switch (lazy for stopped
    activities), ActivityLifecycleCallbacks with pre/post;
    tests/apps/lifecycle (screenshots plus callback order from the log)
  - [x] platform fragments (FragmentManager, back stack, saved and
    retained state, DialogFragment, ListFragment, LoaderManager)
  - [x] in-process services (started, bound, IntentService), broadcasts
    (registered, manifest, ordered, sticky, goAsync), PendingIntent,
    IntentSender, AlarmManager, createPendingResult, leak cleanup on
    destroy; tests/apps/services (25 steps checked against AOSP behaviour)
  - [x] Notification.Builder (AOSP extras layout), Action with
    RemoteInput, BigText/BigPicture/Inbox/Messaging/Media styles,
    BubbleMetadata, Person, recoverBuilder, NotificationChannel and groups,
    StatusBarNotification; NotificationManager enforces channels for
    target O+ and logs what is posted (no RemoteViews content yet)
  - [x] JobScheduler in process (android.app.job: JobInfo validation,
    latency, deadlines, constraints, periodic, backoff, enqueue/dequeue
    work, JobServiceEngine binder for AndroidX JobIntentService)
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

1. WS2 is done. WS3 is in progress: ImageView, the compound controls,
   scrolling (ScrollView, HorizontalScrollView, Scroller, OverScroller,
   EdgeEffect) and lists (ListView, AbsListView, ArrayAdapter) have
   landed. Progress, popups and Toolbar are still open. WS4 (app model)
   and WS5 (animation) can start in parallel.
2. Finish WS1: context menu and action mode presentation once WS4 has
   dialogs.
3. In parallel as agents are available: WS10 (Switch backend), WS13
   (test runner around the app scripts), WS6/WS7/WS9/WS11/WS12/WS15.
4. WS8 once WS10 can present.

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
- `tools/api_check.py <class...>` (or `-p <package>`) lists android.jar
  members our framework lacks: run it on every class you touch.
- Themes with an action bar or title (Theme.Material, Theme.Holo) use the
  screen_simple decor until WS4 adds the action bar, so their content
  starts at the top of the window.
- The headless `idle` script command now waits until queued input is
  consumed and nothing was presented for the quiet time since the command
  started.

## Interface changes log

Record any change to a cross-workstream contract here (date, what, why),
and update ARCHITECTURE.md in the same commit.

- 2026-09-30: the app runner loads `framework-res.apk` from the first
  readable path of `build/toolchains/framework-res.apk`,
  `build/java/framework-res.apk`, and
  `{platform_framework_path()}/framework-res.apk`. If none is readable it
  still passes the toolchain path and continues with an empty framework
  table. Host tests and the later Switch romfs layout share this rule.
- 2026-10-01 (WS1): `PlatformInput` merges per-pointer touch events into
  multi-pointer MotionEvents, assigns key/joystick sources and devices
  (`InputDevice.ID_TOUCHSCREEN` 1, `ID_GAMEPAD` 2), and exposes
  `setSensorSink` for PEV_SENSOR. `WindowManagerGlobal` gained
  `dispatchTouch/dispatchKey/dispatchGenericMotion` (screen coordinates),
  `setPlatformFocus` and `onDisplayChanged` (`scheduleAll` kept).
  `Activity` implements `Window.Callback`, `KeyEvent.Callback` and
  `LayoutInflater.Factory2` (BACK handled by onKeyDown/onKeyUp tracking).
  `ContextImpl` serves LAYOUT_INFLATER_SERVICE (PhoneLayoutInflater) and
  ACCESSIBILITY_SERVICE. TypedArray reads `@null` as no value.
- 2026-10-01 (WS2): PEV_TEXT is still (request id, string) and
  `nRequestText` is unchanged. `InputMethodManager.deliverTextResult`
  commits that string with `InputConnection.commitText` on the view that
  last called `showSoftInput`.
- 2026-10-01 (WS4): `WindowManagerGlobal` key fallbacks gain
  BUTTON_START -> MENU (the + button opens the options menu when the app
  does not handle it). Documented with the controller mapping in
  ARCHITECTURE 6.4.
- 2026-10-01 (WS4): the headless script gains `screen WxH@dpi` (changes
  the display, posts PEV_RESIZE). ActivityThread handles PEV_RESIZE as a
  configuration change (relaunch or onConfigurationChanged per activity)
  instead of only relaying out windows. `WindowManagerGlobal.closeAll`
  removes windows a destroyed activity leaked. `Class.getModifiers` now
  reports member class modifiers from the InnerClass annotation (static,
  private), as ART does; FragmentManager checks them. ActivityThread start
  and finish requests are posted to the main looper (asynchronous, as on
  Android). ARCHITECTURE 6.5 updated.
- 2026-10-01 (WS4): each Activity and Service gets its own `ContextImpl`
  (`createComponentContext`, sharing package state with the application's)
  so receivers and service connections are tracked per component and
  removed with a leak warning after onDestroy, as on Android. Broadcasts
  (`BroadcastQueue`) and services (`ActiveServices`) are delivered in
  process on the main looper; `BroadcastReceiver.PendingResult` now
  carries the ordered result and continues the broadcast on `finish()`.
  The manifest parser reads `<intent-filter android:priority>`.
  `IntentSender` wraps a `PendingIntent`. Documented in ARCHITECTURE 6.5.
- 2026-10-01 (WS4): `ServiceInfo.permission` is now read from the
  manifest (`android:permission` on `<service>`); JobScheduler requires
  BIND_JOB_SERVICE there. `Context.getSystemService(JOB_SCHEDULER_SERVICE)`
  returns `android.app.job.JobSchedulerImpl`. ARCHITECTURE 6.5 updated.
- 2026-10-01 (WS3): ViewRootImpl clamps a window into the screen unless it
  sets FLAG_LAYOUT_NO_LIMITS (as the window manager does); `closeAll` only
  removes activity-token window types (application and sub windows), so
  toasts survive their activity. AdapterView owns `mInLayout` and
  `mBlockLayoutRequests` (moved from AbsListView) and posts selection
  callbacks during layout. ARCHITECTURE 6.4.1 updated.
