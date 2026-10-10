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

## Current state (end of session 26, see SESSION_LOG.md)

Working (host tests on Linux x86-64; AArch64 checked under qemu-user):
- VM core, libcore, JNI, reflection (including RUNTIME annotations),
  threads; VmTest passes.
- Renderer, fonts, images; resource tables with the real framework-res.apk.
- View system (WS1, done): views, input, focus, windows, decor, menus,
  action modes and the floating toolbar, clipToOutline, ViewDebug,
  DisplayCutout.
- Text and IME (WS2, done) and widgets (WS3, done), including lists,
  pickers, popups, Toolbar, SearchView, TabHost, VideoView controls and
  RemoteViews.
- App model (WS4): lifecycle, configuration changes, dialogs, action bar,
  fragments, services, broadcasts, notifications, JobScheduler.
- Animation (WS5, done): view tweens, property animators, state lists,
  layout animation, animated vectors, the remaining interpolators and
  path morph.
- OpenGL ES (WS8): GLES 1.x to 3.2 bindings, EGL14/EGL10, GLUtils,
  Matrix, GLU and GLSurfaceView; GL frames reach the screen through the
  SurfaceView buffer queue (tests/apps/gles on Mesa llvmpipe).
- Native libraries (WS9): ELF loader and bionic shim; JNI libraries from
  the APK load with their dependencies and JNI_OnLoad (tests/apps/ndk on
  x86-64 and AArch64). NativeActivity loads a library, wraps its
  SurfaceView as an ANativeWindow, and native EGL window surfaces post
  into that queue (tests/apps/native on host Mesa). The OpenSL ES buffer
  queue is in the same shim and shares the WS7 mixer.
- Switch (WS10): the NRO boots on hardware; launcher with APK labels and
  icons; the build links Mesa when switch-mesa is installed. CI packages
  the NRO and sample APKs (`.github/workflows/package.yml`).
- Audio (WS7): one 48 kHz stereo mixer for SoundPool, MediaPlayer,
  AudioTrack, ToneGenerator and the OpenSL ES buffer queue. Decoders are
  WAV, Ogg Vorbis and MP3. tests/apps/audio checks a non-silent mix on
  the host. The Switch audio thread still discards samples.
- Storage (WS6): SQLite natives over the bundled amalgamation.
  DatabaseUtils, an in-memory CursorWindow, Settings and MediaStore,
  and androidx.core.content.FileProvider. tests/apps/store creates a
  database, upgrades it on a second host run, reads the preference and
  the settings value written by the first run, and checks the window,
  media rows and a FileProvider file.
- Networking (WS11): java.net sockets (TCP, UDP, server), DNS through
  getaddrinfo, and an HTTP/1.1 HttpURLConnection (chunked and fixed
  bodies, streaming uploads, redirects, Android error semantics).
  ConnectivityManager reports the platform network (nifm on the Switch)
  with callbacks and CONNECTIVITY_ACTION. tests/dex/NetTest matches
  OpenJDK; tests/apps/net checks HTTP, UDP and network switches on the
  host. No TLS yet: https fails with SSLHandshakeException.
- System services (WS15): accelerometer and gyroscope from the platform
  (headless script values; the Switch six-axis sensor) with gravity,
  linear acceleration, rotation vectors and orientation fused from them;
  live battery state and broadcasts; rumble waveforms; PowerManager.
  Location (off), telephony and cameras (none) answer like a device
  without them. tests/apps/sensors checks all of it on the host.
- The sample apps in `tests/apps` pass their screenshot checks (gles needs
  host Mesa; curves can miss its mid-animation frame on a loaded machine), and
  tests/apps/store passes its two-run check (it is not in the NRO sample
  list).

Compression: java.util.zip and java.util.jar over zlib natives
(tests/dex/ZipTest matches OpenJDK), and transparent gzip in
HttpURLConnection.

Real apps (session 25): 12 of the 13 corpus apps reach a drawn screen on
the host (docs/COMPATIBILITY.md). On the way in: android.preference and
ListActivity (AOSP ports, tests/apps/prefs), ActivityManager,
DisplayManager, InputManager, activity-alias, SAX, java.util.logging,
ServiceLoader, generic signatures, sun.misc.Unsafe, FileChannel locks and
mapping, java.nio selectors and socket channels, regex Unicode classes,
ProcessBuilder (always refused), and the native shim's C++ runtime,
locale, wide-character, semaphore, rwlock and dl_iterate_phdr entries.
New JDK-compared tests: RegexTest, NioTest, XmlTest, UnsafeTest,
LoggingTest, GenericsTest, SelectorTest.

Not yet: GL on the Switch (Mesa linked but not run on hardware), device
audio output (audren/audout), running a native library on hardware (code
memory is mapped; newlib struct translation remains), ALooper and the
input queue on the Switch (no pipe/poll in newlib) and AAudio,
TLS for https and running the sockets on hardware (WS11), AndroidX
(WS14), and running the sensors, rumble and battery on a console
(WS15). Photo-picker and
cloud-media helpers on MediaStore (createDeleteRequest, getVersion,
volume-name sets) are still missing and auto-stub.

## Checklist

Summary per workstream; the detailed scope lives in WORKSTREAMS.md.

### Done
- [x] VM: interpreter, class linking, auto-stubbing, GC, threads/GIL, monitors, exceptions, reflection, proxies, lambdas
- [x] JNI: JNIEnv/JavaVM, call trampolines (AArch64, x86-64)
- [x] libcore: lang, util (+concurrent/stream/regex), io, nio, text, math, security digests
- [x] libcore: java.util.zip and java.util.jar over zlib (tests/dex/ZipTest against OpenJDK; session 23)
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
- [x] WS1 view system core
  - [x] View, ViewGroup, input events, ViewConfiguration, VelocityTracker, gesture detectors, FocusFinder
  - [x] ViewRootImpl, Choreographer, WindowManagerGlobal (window stack, routing, compositing)
  - [x] LayoutInflater, ViewStub, PhoneWindow/DecorView, MenuInflater + menu model, Window.Callback
  - [x] tests/apps/views acceptance sample
  - [x] SurfaceView/SurfaceHolder (software lockCanvas, paced producers), TextureView (software)
  - [x] context menu presentation (MenuDialogHelper, from WS4)
  - [x] action mode presentation: primary modes in the action bar's
    context bar (WindowDecorActionBar) or standalone in the decor's
    action_mode_bar stub; floating toolbar (TYPE_FLOATING) shows the menu
    above the content rect (tests/apps/floating). A long press on
    selectable text selects a word and opens that toolbar
    (tests/apps/select)
  - [x] clipToOutline: a round-rect outline clips the view and its
    children (tests/apps/outline). Path outlines do not clip
  - [x] ViewDebug annotations: runtime reflection of class, field and
    method annotations (defaults, nested annotations, arrays, enums,
    class literals) and ViewDebug.dumpCapturedView (tests/dex/VmTest,
    tests/apps/viewdbg)
  - [x] DisplayCutout: safe insets, per-edge bounds, waterfall insets,
    WindowInsets.getDisplayCutout, and the layout-in-cutout mode
    (tests/apps/cutout). The Switch reports no cutout
- [x] WS2 text and IME
  - [x] Spanned/Spannable, spans, TextUtils, Layout/StaticLayout/BoringLayout/DynamicLayout
  - [x] TextView measure, draw, common XML attributes, transformations; tests/apps/text screenshots
  - [x] movement and key listeners, EditText, BaseInputConnection, IME `text` delivery
  - [x] Html.fromHtml, Linkify, DateUtils
- [x] WS3 widgets
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
  - [x] Toolbar, ActionMenuView, ActionMenuPresenter (action buttons,
    overflow popup), menu presenters (MenuPresenter, BaseMenuPresenter,
    ActionMenuItemView), DecorToolbar/ToolbarWidgetWrapper; tests/apps/toolbar
  - [x] GridView (AbsListView lays out rows of N items), TableLayout,
    TableRow, AbsoluteLayout; tests/apps/grid
  - [x] Filter on a worker thread (as AOSP), SimpleAdapter, CursorAdapter,
    ResourceCursorAdapter, SimpleCursorAdapter, ExpandableListView
    (connector, indicators, child dividers, saved state),
    BaseExpandableListAdapter, SimpleExpandableListAdapter,
    TwoLineListItem, AutoCompleteTextView, MultiAutoCompleteTextView;
    tests/apps/adapters
  - [x] NumberPicker (selector wheel), Chronometer, TextClock, Scroller
    interpolators, View fading edges, ViewAnimator, ViewFlipper,
    ViewSwitcher, TextSwitcher, ImageSwitcher, DialogViewAnimator;
    tests/apps/pickers
  - [x] DatePicker (calendar and spinner delegates), CalendarView
    (material delegate), DatePickerDialog, internal ViewPager, DayPickerView,
    SimpleMonthView, YearPickerView; tests/apps/dates
  - [x] CalendarView holo week list (CalendarViewLegacyDelegate);
    tests/apps/holocal
  - [x] TimePicker (clock and spinner delegates), RadialTimePickerView,
    NumericTextView, TextInputTimePickerView, TimePickerDialog;
    tests/apps/times
  - [x] GridLayout (constraint solver, spans, alignments, baselines,
    default margins, weights); tests/apps/gridlayout
  - [x] SearchView (suggestions adapter, iconified and action view modes),
    SearchManager, SearchableInfo, the search dialog, RecognizerIntent
    constants; tests/apps/search
  - [x] TabHost, TabWidget, TabActivity, ActivityGroup and
    LocalActivityManager (embedded activities); tests/apps/tabs
  - [x] CursorTreeAdapter, ResourceCursorTreeAdapter,
    SimpleCursorTreeAdapter; tests/apps/adapters
  - [x] VideoView and MediaController (playback fails with the framework
    error dialog until WS7 decodes); tests/apps/video
  - [x] RemoteViews (apply, reapply, parcel, collections); tests/apps/remote
- [ ] WS4 app model
  - [x] Dialog, AlertDialog (+Builder: message, buttons, items, single and
    multi choice, custom view) on the framework's material alert layouts,
    CheckedTextView, ActionBar API; tests/apps/appmodel
  - [x] ProgressDialog (spinner and horizontal); tests/apps/progress
  - [ ] DatePicker/TimePicker dialogs
  - [x] options menu (+ button falls back to MENU) as an overflow-style
    popup, context menus and sub menus as dialogs
  - [x] action bar decor: screen_toolbar (ActionBarOverlayLayout,
    ActionBarContainer, ActionBarContextView), WindowDecorActionBar
    (title, subtitle, up, action items, overflow, MENU key, hide/show,
    overlay mode, action modes; tabs dispatched but not drawn),
    setActionBar(Toolbar) through ToolbarActionBar, screen_title and the
    floating dialog title decor, default theme selection by targetSdk;
    tests/apps/actionbar
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
  - [x] GitHub Actions workflow builds the NRO and packages the sample
    APKs (`.github/workflows/package.yml`, artifact `switchapk-sd`)

### M3
- [ ] WS10 Switch platform, NRO, launcher
  - [x] Makefile.switch (devkitA64, romfs with framework.dex and
    framework-res.apk), `dist` SD zip with the sample APKs
  - [x] platform_switch.c: framebuffer present, pad/stick/touch input,
    applet focus/exit, swkbd, shared fonts
  - [x] main_switch.c: APK launcher, VM thread, error screen, log file,
    nxlink, relaunch to the list
  - [x] VM and samples verified on AArch64 (qemu-user host build)
  - [x] first boot on hardware (confirmed 2026-10-02)
  - [x] APK labels and icons in the launcher (tests/apps/labeled,
    switchapk-host --apk-info)
  - [ ] audio (audren/audout), rumble, 1080p docked

### M4
- [x] WS5 animation
  - [x] view tween animations applied while drawing (translate, scale,
    rotate, alpha, set; tests/apps/tween)
  - [x] property animators (ValueAnimator, ObjectAnimator,
    ViewPropertyAnimator; tests/apps/prop)
  - [x] StateListAnimator (tests/apps/motion)
  - [x] layout animation, LayoutTransition and AnimatedVectorDrawable
    (tests/apps/motion)
  - [x] PathInterpolator, cycle, anticipate, overshoot, bounce and path
    morph (tests/apps/curves)
- [ ] WS7 audio/media
- [ ] WS8 OpenGL ES/EGL
  - [x] GLES10/11/20/30/31/32 (+Ext) bindings generated from android.jar and
    the Khronos headers (tools/gen_gles.py), GL10/GL11 interfaces and GLImpl
  - [x] EGL14, EGL10/EGL11 (javax.microedition), EGLExt constants, GLUtils,
    Matrix, GLU, GLSurfaceView (AOSP port) with a graceful failure panel
  - [x] window surfaces as pbuffers read back into the Surface buffer
    queue on swap (SurfaceView/TextureView consume GL frames);
    tests/apps/gles (GLES2 textured cube on host Mesa llvmpipe)
  - [ ] GLES1 rendering verified (Ubuntu's Mesa has no ES1 contexts; the
    sample checks the failure panel there)
  - [x] Switch build links switch-mesa (with -lstdc++) and builds without it
  - [x] Switch: window and pbuffer surfaces as framebuffer objects in a
    surfaceless context (switch-mesa has no pbuffers, which showed
    "No configs match configSpec" on hardware); host tests the path with
    SWITCHAPK_EGL_FBO=1; alpha-free variants of RGBA8888 configs, since
    switch-mesa has no RGB888 ones (the GLES1 view's "No config chosen")
  - [ ] Switch: verify GL on hardware; direct NWindow presentation for
    fullscreen GL
  - [ ] EGL15 syncs/images, SurfaceTexture.updateTexImage, ETC1Util
- [ ] WS15 sensors and system services
  - [x] platform sensors (accelerometer, gyroscope) and battery calls;
    headless `sensor` and `battery` script commands; Switch six-axis,
    psm and HD rumble (built, not run on hardware)
  - [x] SensorManager API and AOSP math, SystemSensorManager with
    per-listener pacing, gravity, linear acceleration, rotation vectors
    and orientation fused from the IMU (tests/apps/sensors: rest, a
    quarter turn, a 45 degree tilt)
  - [x] BatteryManager live state, sticky ACTION_BATTERY_CHANGED kept
    current, power connected and battery low/okay broadcasts
  - [x] Vibrator waveforms (amplitudes, repeat, predefined effects,
    compositions), VibratorManager, PowerManager wake locks and thermal
    listeners, hasSystemFeature for the console's hardware
  - [x] LocationManager (location off), Location, TelephonyManager (no
    telephony), Camera and camera2 CameraManager (no cameras)
  - [ ] IMU axis signs, rumble and battery checked on a console

### M5
- [ ] WS9 native loader, bionic shim, NativeActivity
  - [x] ELF64 loader (x86-64 and AArch64; RELA, APS2, RELR; DT_NEEDED from
    the APK; constructors; JNI_OnLoad), unresolved imports bound to
    logging stubs, System.load/loadLibrary and nativeLibraryDir
  - [x] shim: libc/libm subset with bionic wrappers (paths, sysconf,
    pthread objects, __sF, fortify), liblog, libdl, AAssetManager,
    system properties, zlib, GL/EGL lookup
  - [x] tests/apps/ndk (19 JNI checks; passes on x86-64 and on the
    AArch64 host build under qemu with arm64-v8a libraries)
  - [x] Switch code memory (svcMapProcessCodeMemory +
    svcSetProcessMemoryPermission on the alias region; application
    launches only). newlib struct translation (stat, dirent, O_* flags,
    clock ids) remains
  - [x] NativeActivity and ANativeWindow; native eglCreateWindowSurface
    posts through the same Surface queue as Java (tests/apps/native:
    red EGL clear, gold rect from ANativeWindow_lock)
  - [x] OpenSL ES buffer queue (engine, play, volume, Android simple
    buffer queue) sharing the WS7 mixer (tests/apps/audio on the host)
  - [x] ALooper, AInputQueue/AInputEvent and AConfiguration; NativeActivity
    delivers key, touch and joystick events; native threads enter the VM
    (tests/apps/input, a glue-style app, on x86-64 and AArch64)
  - [ ] ALooper/input on the Switch (needs virtual descriptors: no
    pipe/poll in newlib), AAudio
  - [x] shim runtime for corpus libraries: C++ operator new/delete,
    sincos, C.UTF-8 locale and wide chars, syslog, semaphores, rwlocks,
    dl_iterate_phdr (Vector Pinball, Frozen Bubble, Mindustry load)
  - [ ] real NDK-built APK corpus (libc++_shared, emulated TLS)

### M6
- [x] WS6 SQLite natives and storage
  - [x] SQLite natives (open, prepare, bind, step, column, changes, last
    insert id, error subclasses), amalgamation in both Makefiles,
    ContextImpl.openOrCreateDatabase, tests/apps/store (helper create,
    upgrade on the second host run, selection args, transaction,
    SharedPreferences)
  - [x] DatabaseUtils, CursorWindow, android.provider stubs, FileProvider
    (tests/apps/store: window fill, settings across two runs, media
    insert, FileProvider read and a path escape)
- [ ] WS11 networking
  - [x] socket natives (resolve, TCP client and server, UDP, options,
    timeouts, close wakes blocked threads) with the GIL released;
    InetAddress, InetSocketAddress, Socket, ServerSocket,
    DatagramSocket, javax.net socket factories (tests/dex/NetTest
    against OpenJDK)
  - [x] HttpURLConnection over HTTP/1.1: chunked, fixed and until-close
    bodies, buffered and streamed uploads, redirects, error stream,
    header and date accessors (NetTest, tests/apps/net)
  - [x] ConnectivityManager, NetworkInfo, Network, NetworkCapabilities,
    NetworkRequest, LinkProperties, callbacks and sticky
    CONNECTIVITY_ACTION from `platform_network_state` (nifm on the
    Switch; tests/apps/net flips the host state)
  - [ ] TLS: javax.net.ssl (SSLSocketFactory, SSLContext, trust
    managers, HttpsURLConnection) over mbedtls, with a CA bundle
  - [x] java.util.zip and java.util.jar (zlib natives; tests/dex/ZipTest
    against OpenJDK); HttpURLConnection asks for gzip and decodes it
    like OkHttp (tests/apps/net)
  - [x] NIO selectors and socket, server socket and datagram channels,
    blocking and non-blocking (tests/dex/SelectorTest against OpenJDK)
  - [ ] connection pooling, CookieManager, proxies
  - [ ] sockets and nifm on hardware
- [ ] WS12 VM performance
- [ ] WS14 AndroidX compatibility
- [ ] docs/COMPATIBILITY.md with a tested APK corpus
  - [x] corpus of 13 F-Droid APKs (`tests/corpus/corpus.json`, pinned),
    `tools/corpus.py` static gap scan, headless smoke run and generated
    report (docs/COMPATIBILITY.md)
  - [x] first blockers fixed: 12 of 13 apps draw their first screen on
    the host (session 25; COMPATIBILITY.md "Findings")
  - [ ] corpus runs in CI; per-app scripts that get past the title screen

## Next steps (in order)

1. WS1 is done. WS2 is done. WS3 is done: ImageView, the compound controls,
   scrolling (ScrollView, HorizontalScrollView, Scroller, OverScroller,
   EdgeEffect) and lists (ListView, AbsListView, ArrayAdapter) have
   landed, and so have progress, popups, Toolbar, the action bar,
   adapters, the expandable list, NumberPicker, the clocks, DatePicker,
   CalendarView (material delegate and the holo week list),
   DatePickerDialog, TimePicker, TimePickerDialog and GridLayout,
   SearchView, TabHost, the CursorTreeAdapter family, VideoView
   (transport controls and the error dialog; decoding is WS7) and
   RemoteViews (inflate, actions and reapply; notification content
   views stay unsupported). Text selection opens the floating toolbar
   (tests/apps/select).
   Next on device: audio (audren/audout), rumble, and 1080p docked
   rendering. The NRO boots on hardware, and the launcher shows APK
   labels and icons. View tween animations apply while drawing
   (tests/apps/tween). Property animators run on Choreographer
   (tests/apps/prop). StateListAnimator, layout animation, LayoutTransition
   and animated vectors land in tests/apps/motion. PathInterpolator, the
   cycle/anticipate/overshoot/bounce interpolators and path morph land in
   tests/apps/curves. WS5 is done.
2. WS1 is done. DisplayCutout has landed (tests/apps/cutout), and so
   have ViewDebug (tests/apps/viewdbg), the floating toolbar
   (tests/apps/floating), text selection (tests/apps/select), and
   clipToOutline (tests/apps/outline).
3. Audio (audren/audout), and 1080p docked rendering (rumble is written,
   see item 7). In parallel
   as agents are available: WS13 (test runner around the app scripts;
   packaging CI is `.github/workflows/package.yml`), WS11/WS12/WS15.
   WS6 is done. WS15 is in on the host (item 7).
4. WS8: the bindings, EGL, GLSurfaceView and the host sample are in;
   next is the Switch build with switch-mesa and a device run, then
   SurfaceTexture external textures.
5. WS9: JNI libraries load on the host and on AArch64 (qemu), and the
   Switch loader maps executable pages with svcMapProcessCodeMemory
   (application launches; not yet run on hardware). NativeActivity,
   ANativeWindow and native EGL window surfaces are in
   (tests/apps/native, host Mesa). OpenSL ES buffer queues share the
   WS7 mixer (tests/apps/audio on the host; the Switch build still
   discards samples and has not been run on hardware). Still open:
   ALooper and input on the Switch (virtual descriptors), AAudio, and
   newlib struct translation (stat, dirent, O_* flags, clock ids).

6. WS11: sockets, HTTP, gzip and ConnectivityManager are in (host),
   and so are java.util.zip and java.util.jar. Next is TLS (mbedtls on
   both targets, `javax.net.ssl` and HttpsURLConnection), then a device
   run of tests/apps/net.

8. libcore API gaps. `tools/api_check.py -p java.lang` and so on list
   about 1,700 members across java.lang, java.util,
   java.util.concurrent, java.text, java.nio, java.io and java.net. Many
   are API 34/35 additions (SequencedCollection, Math.clamp) or interface
   methods the checker does not follow. Real ones that apps hit include
   `Map.of` with six or more pairs and `Map.ofEntries`, `String.codePoints`,
   the `Math` exact and floor/ceil variants, `Class.getDeclaredAnnotation`
   and `toGenericString`, Character code point helpers (the Character
   tables are approximations outside Latin, Greek, Cyrillic and CJK), and
   the checked/navigable `Collections` wrappers. Still missing as whole
   areas: java.time, ResourceBundle, org.w3c.dom and DocumentBuilder,
   MulticastChannel and pipes, a default serialVersionUID hash.

9. Real-app corpus (docs/COMPATIBILITY.md, `tools/corpus.py all`). 12 of
   13 apps draw their first screen. Next, by what each unblocks: drive
   the apps past the first screen (a per-app smoke script that starts a
   game), the AppCompat/AndroidX surface the static scan ranks highest
   (accessibility, android.transition, android.icu, AppOpsManager,
   window insets; WS14), Mindustry's map preview decode error (zip and
   DataInputStream ruled out), and ActionBarView for Holo decors (they
   use the Toolbar decor now). Simon Tatham's Puzzles needs to run a bundled executable,
   which the console cannot do. Then run the drawing apps on hardware.

7. WS15: sensors, battery, rumble, power and the absent-hardware
   services are in (host). Next is a console run: check the six-axis
   axis signs (tilt a game that uses the accelerometer), rumble and the
   battery level.

## Known issues and gotchas

- Holo-themed apps get the Toolbar action bar decor: AOSP's
  `screen_action_bar` is built on ActionBarView, which is not ported.
  Behaviour matches; the bar is drawn in Toolbar style.
- `SWITCHAPK_TRACE_THROW=1` logs every Java throw and VM-raised exception
  with its method and line, caught or not. Use it when an app hides an
  error behind its own crash screen.

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
- Apps whose manifest sets no theme get Theme.DeviceDefault.Light.DarkActionBar
  (AOSP selectDefaultTheme for targetSdk 24+), so they now show an action
  bar with the activity label. Action bar show/hide and action mode
  transitions are still immediate: the animator classes exist, but
  PhoneWindow does not use them. View tweens (translate, scale, rotate,
  alpha, set) and property animators (ValueAnimator, ObjectAnimator,
  ViewPropertyAnimator) already run.
- Native libraries map executable pages on the Switch only when hbloader
  hints code-memory syscalls (an application launch, not an applet). That
  path is not yet run on hardware. newlib struct layouts still differ
  from bionic. ALooper and the NativeActivity input queue need pipe and
  poll, which newlib lacks, so on the Switch they report failure and the
  app gets no input. Native EGL window surfaces are pbuffers read back into
  the Surface queue, the same path as Java, and have not been run on
  hardware.
- Sensor listeners keep the headless sampling thread posting events.
  An app that redraws on every sample never lets the script's `idle`
  settle (it gives up after 20 s); tests/apps/sensors unregisters once
  its checks are done. The Switch six-axis axis signs are a guess until
  checked on hardware (`ACCEL_SIGN` in platform_switch.c).
- Host GL tests need Mesa's EGL and GLES libraries (`libegl1`,
  `libgles2`; `libegl-dev`/`libgles-dev` to regenerate the bindings).
  Ubuntu's Mesa cannot create ES1 contexts, so GLES1 rendering is not
  verified on the host; tests/apps/gles checks the failure panel instead.
- The headless `idle` script command now waits until queued input is
  consumed and nothing was presented for the quiet time since the command
  started.
- The launcher draws bitmap icons from the APK. Adaptive-icon and vector
  XML drawables are skipped, and that row shows the label only.

## Interface changes log

Record any change to a cross-workstream contract here (date, what, why),
and update ARCHITECTURE.md in the same commit.

- 2026-10-10 (WS4/WS1): external storage is `/storage/emulated/0` and the
  app's external files, cache, OBB and media directories use Android's
  `Android/data|obb|media/<pkg>` layout under `<data root>/sdcard` (were
  under the app's data directory). Style bags follow parents to any depth.
  ARCHITECTURE 5, 6.2.
- 2026-10-10 (WS11/WS16): java.net sockets own the descriptor for NIO
  channels; `Socket`, `ServerSocket` and `DatagramSocket` gained hidden
  `fd$()`, `setChannel$()` and `closeInternal$()` hooks (`Socket` also
  `prepareFd$()`, `markConnected$()`, `accepted$()`), and `libcore.io.Net`
  gained non-blocking natives and `poll`. ARCHITECTURE 5.1.
- 2026-10-10 (WS9): the shim has a third table, `shim_runtime_symbols()`
  (src/nativeloader/shim_runtime.c), and the loader exports
  `loader_iterate_phdr()` for `dl_iterate_phdr`; `--shim-symbols` lists
  all three tables. ARCHITECTURE 6.7.
- 2026-10-10 (WS1/WS4): `com.android.internal.R` exists, generated by
  `tools/gen_internal_r.py` for ported AOSP code. Bag merging keeps an
  entry's own items with equal keys (array items). ARCHITECTURE 6.2.
- 2026-10-10 (WS16): `java.lang.reflect.AnnotationParser.signature()` and
  the `readSignature` native expose generic signatures; `sun.misc.Unsafe`
  natives read `Field.vmField`. ARCHITECTURE 5.3, 5.4.

- 2026-10-09 (WS8, touches WS9): `sa_egl_native_proc` also returns
  wrappers for `glBindFramebuffer` and `glBindFramebufferOES`, so the
  NDK shim hands native code the redirect FBO surfaces need. No new
  native fields. ARCHITECTURE 6.6 updated.

- 2026-10-02 (WS13, touches WS10): `make -f Makefile.switch dist`
  packages every directory under `tests/apps` that has an
  `AndroidManifest.xml`. Apps with `native/build.sh` are still rebuilt
  with `NDK_ARM64=1`. The Package NRO check requires each of those APKs
  in the SD zip, and an arm64-v8a library in each native APK. No new
  native fields.
- 2026-10-02 (WS6, touches WS4): `AbstractCursor.fillWindow` copies rows
  through `DatabaseUtils.cursorFillWindow` into a Java `CursorWindow`
  (absolute row indexes, 2 MiB default budget). `simpleQueryForBlobFileDescriptor`
  spills the blob to a temp file. `FrameworkProviders.install` registers
  the settings authority (rows under `/data/local/tmp/settings`) and the
  in-memory media authority before manifest providers.
  `<provider android:grantUriPermissions>` is stored on `ProviderInfo`.
  `androidx.core.content.FileProvider` serves the paths XML. No new
  native fields. ARCHITECTURE 6.6.
- 2026-10-02 (WS6, touches WS4): `SQLiteNative` is registered from
  `android_sqlite.c` over the bundled amalgamation (`SQLITE_THREADSAFE=1`,
  `SQLITE_OMIT_LOAD_EXTENSION`). Paths other than `:memory:` go through
  `platform_map_path`. `ContextImpl.openOrCreateDatabase` opens
  `getDatabasePath`. Result codes map to the
  `SQLiteException` subclasses. `nFinalize(0)` does nothing, so a failed
  prepare does not hide its exception. The Switch amalgamation is
  `SQLITE_OS_OTHER` with `sqlite_vfs_switch.c` (POSIX files, pthread
  mutexes, no WAL). No new Java fields. ARCHITECTURE 6.6.
- 2026-10-02 (WS5, touches WS1): `AnimationUtils.loadInterpolator` loads
  cycle, anticipate, overshoot, anticipateOvershoot, bounce and path
  interpolators. `PropertyValuesHolder` extrapolates a fraction outside
  0..1 across the first or last keyframe interval, so overshoot and
  anticipate move a property past its values. `PathParser.canMorph` is
  true for two `PathData` values with the same commands.
  `VectorDrawable` path `setPathData` rebuilds the path, and
  `AnimatorInflater` runs a pathData animator through that setter. No new
  native fields. ARCHITECTURE 6.4.
- 2026-10-02 (WS5, touches WS1): `View.setStateListAnimator` runs the first
  matching animator when the drawable state changes. The view holds that
  animator strongly, because this VM clears weak references on every GC.
  `android:stateListAnimator` is read from the tag itself, not the theme.
  A ViewGroup layout animation binds in `dispatchDraw`: each child gets a
  clone whose start offset comes from `getDelayForView`. `LayoutTransition`
  fades a child in on add and out on remove. A disappearing child stays
  parented until the fade ends, and `dispatchDraw` draws it from
  `mDisappearingChildren`. Change-type animators are stored and not
  started, so sibling positions do not move. `AnimatedVectorDrawable`
  clones its target animators onto `VectorDrawable` groups and paths
  (trim, color, stroke, transforms). pathData morphs are not applied.
  No new native fields. ARCHITECTURE 6.4 updated.
- 2026-10-02 (WS7, touches WS9 and WS10): `platform_audio_start` is called
  once with the mixer callback (`src/android/audio_mixer.c`, 48 kHz
  stereo float) and is not stopped. The audio thread takes only the
  mixer mutex and never the VM lock. `android_media.c` releases the GIL
  before a decode and before a blocking stream write. OpenSL ES symbols
  are exported from the WS9 shim: `slCreateEngine`, and each `SL_IID_*`
  as the address of the pointer object (a GLOB_DAT into the app GOT, then
  a load of the UUID). `android.media.MixDebug` is framework-internal
  (`getSourceMask`, `getNonZeroFrames`); it is not in android.jar. The
  Switch callback still discards its buffer. ARCHITECTURE 6.6.
- 2026-10-02 (WS9, touches WS8): `android.app.NativeActivity` loads
  `lib/<abi>/lib<name>.so` (meta-data `android.app.lib_name`, default
  entry `ANativeActivity_onCreate`) and installs a full-bleed
  SurfaceView, because `PhoneWindow.takeSurface` does not deliver a
  surface. `ANativeWindow_*` locks that Surface as RGBA bytes and posts
  ARGB through `Surface.lockGlBuffer`. The NDK shim's
  `eglCreateWindowSurface` accepts an ANativeWindow, makes a pbuffer,
  and `eglSwapBuffers` reads it back with the same conversion as
  `EGLNative.nReadWindow` (`sa_egl_native_proc` in front of `sa_gl_proc`).
  ARCHITECTURE 6.6.1 and 6.7.
- 2026-10-07 (WS9, touches WS4 and the loader): ALooper, AInputQueue,
  AInputEvent and AConfiguration are in the NDK shim (native_looper.c,
  native_input.c, native_config.c; declarations in ndk_android.h).
  `android.app.NativeActivity` creates the native input queue with the
  first surface and, once the app attaches it to an ALooper, copies key,
  touch and generic motion events into it from `dispatchKeyEvent`,
  `dispatchTouchEvent` and `dispatchGenericMotionEvent` instead of
  dispatching them (new private natives `enqueueKeyNative` and
  `enqueueMotionNative`; `onInputQueueCreatedNative` and
  `onInputQueueDestroyedNative` lost their queue pointer argument). The
  framework-internal `nl_vm_enter`/`nl_vm_leave` (nativeloader.h) let
  threads the app created call into the VM: the ANativeActivity_*
  functions and ANativeWindow posting now work from the glue thread.
  ARCHITECTURE 6.7 updated.
- 2026-10-02 (WS8/WS9, touches WS10 and WS13): `make -f Makefile.switch
  dist` packages tests/apps/gles and tests/apps/ndk. Apps with a
  `native/build.sh` are always rebuilt with `NDK_ARM64=1`, so the APK
  carries arm64-v8a libraries. The Package NRO workflow installs clang,
  lld and libc6-dev-arm64-cross, and checks that gles.apk is in the SD zip
  and that ndk.apk has arm64-v8a libraries. ndk.apk is the first
  hardware test of the Switch loader (code memory, the shim on newlib).
- 2026-10-02 (WS9, touches WS4, WS13, libcore): `src/nativeloader/loader_stub.c`
  is replaced by the ELF loader and shim (same `nativeloader_load_library`
  / `nativeloader_find_symbol` contract, ARCHITECTURE 6.7). `os.arch` comes
  from the new `System.nativeArch()` native ("x86_64" on x86-64 hosts,
  "aarch64" otherwise), so `Build.CPU_ABI` matches the loader's ABI.
  `ApplicationInfo.nativeLibraryDir` is set to
  `/data/app/<pkg>/lib/<x86_64|arm64>`. `tools/build_apk.sh` packages an
  app's `assets/` and runs an optional `native/build.sh <out>` whose
  `lib/<abi>/*.so` go into the APK. Makefile.switch adds `-lstdc++` with
  Mesa (switch-mesa is C++).
- 2026-10-02 (WS8, touches WS1 and the VM): `Surface` gained
  framework-internal `lockGlBuffer(w, h)`, `unlockGlBufferAndPost()` and
  `isOpaqueBuffer()` so EGL window surfaces post frames through the
  software buffer queue (posting shares `unlockCanvasAndPost`'s pacing).
  `vm_buffer_address(Object *buf)` (jni.c) is now declared in vm.h; the
  GL natives also read `Buffer.position`, `limit` and `elementSizeShift`.
  `natives_android_register` registers `android_opengl_register`.
  Makefile.switch links Mesa (`-lEGL -lglapi -ldrm_nouveau`, defines
  `SA_HAVE_EGL`) only when the switch-mesa portlib is installed.
  ARCHITECTURE 6.6.1 added.

- 2026-10-01 (WS10): Switch backend. `platform_switch_pump()` and
  `platform_switch_buttons_down()` are Switch-only extras used by
  main_switch.c. PEV_TEXT results replace the whole edited field
  (InputMethodManager selects all, then commits), matching swkbd which
  returns the full text; null means cancelled. `sa_log_recent()` returns
  the last INFO+ log lines; the log file is flushed only on WARN+.
- 2026-10-01 (WS3/WS4): `PhoneWindow` picks its decor like AOSP
  generateLayout: the theme's windowActionBarFullscreenDecorLayout
  (screen_toolbar on Material) for FEATURE_ACTION_BAR, the
  dialogTitleDecorLayout for floating windows with a title, screen_title
  for other titled windows, screen_simple(_overlay_action_mode) otherwise.
  With an action bar the options menu is the decor toolbar's menu
  (rebuilt, posted, on invalidatePanelMenu) and MENU toggles its
  overflow. `DecorView` starts action modes (Window.Callback
  onWindowStartingActionMode, then a StandaloneActionMode) and BACK ends
  the primary mode. `Activity.getActionBar/setActionBar` are real.
  Contexts with no theme get `Resources.selectDefaultTheme`.
  `InternalRes.attr` also looks up the "^attr-private" type.

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
- 2026-10-01 (WS3): `android.widget.Filter` runs `performFiltering` on a
  "Filter" HandlerThread and `publishResults` on the creating looper, as
  AOSP does; filtering is no longer synchronous (tests/apps/list updated).
  AbsListView has the AOSP package-private `createContextMenuInfo` hook
  and ListView `drawDivider(Canvas, Rect, int)`, both overridden by
  ExpandableListView. `setSelection(INVALID_POSITION)` now clears the
  selection instead of selecting row 0. ARCHITECTURE 6.4 updated.
- 2026-10-01 (WS3, touches WS1): `ViewRootImpl.performDraw` empties the
  dirty rect before drawing (AOSP drawSoftware order), so an invalidate()
  made while drawing, e.g. from `computeScroll()`, schedules the next
  frame instead of being dropped. `View.draw` draws fading edges for
  `requiresFadingEdge` / `setVerticalFadingEdgeEnabled` (one saveLayer and
  a DST_OUT ramp per edge, or a ramp of `getSolidColor()`); the legacy
  `fadingEdge` attr is ignored as on ICS and later. `Scroller` has the
  Interpolator constructors. View holds tween animations
  (`startAnimation` and friends, applied by WS5 later) and
  `AnimationUtils.loadAnimation` parses `<alpha>`. ARCHITECTURE 6.4.1
  updated.
- 2026-10-02 (WS3, touches WS1 and libcore): LinearLayout gives a 0dp
  weighted child only its share of the excess when the spec is not EXACTLY
  for apps targeting N or later (AOSP `mAllowInconsistentMeasurement`; it
  used the pre-N wrap-plus-share rule for everyone). DialogViewAnimator has
  AOSP's measure (MATCH_PARENT children do not count towards its wrap
  size). `Calendar.set(field, value)` no longer normalizes pending fields
  first, so `set(2024, FEBRUARY, 30)` is March 1. Framework-internal
  additions: `StateSet.get(mask)` and `VIEW_STATE_*`, `ColorStateList.hasState`,
  `HapticFeedbackConstants.CALENDAR_DATE`, `NumberPicker.getTwoDigitFormatter`,
  `CalendarView.parseDate`; `DateFormat.getBestDateTimePattern` maps en-US
  skeletons like ICU. ARCHITECTURE 6.4.1 updated.
- 2026-10-02 (WS3): CompoundButton no longer draws a flat placeholder box
  when it has no button drawable; a null button (`android:button="@null"`,
  `setButtonDrawable(null)`) draws nothing, as on Android. The theme
  indicators (the material animated selectors) draw instead.
  `MathUtils.lerpDeg` added. RadialTimePickerView animates its
  hours/minutes crossfade on a frame callback until WS5 brings
  ObjectAnimator. ARCHITECTURE 6.4.1 updated.
- 2026-10-02 (WS3, WS1 hook): `ViewGroup.onSetLayoutParams(View, LayoutParams)`
  is now protected (hidden in AOSP) instead of package-private, so
  GridLayout can validate a child's new params and drop its cached
  structure, as on Android. ARCHITECTURE 6.4.1 updated.
- 2026-10-02 (WS3, WS4 package manager): the manifest parser now fills
  `PackageItemInfo.metaData` from `<meta-data>` (resource ids as ints,
  values by type: string, boolean, int, float) for the application,
  activities, receivers, services and providers, and
  `PackageManager.getXml` returns the app's XML resources, so
  `loadXmlMetaData` works. `Context.SEARCH_SERVICE` returns a
  SearchManager (one per activity, as on Android). TextView handles Enter
  in a single-line editor before its key listener, so Enter is never
  inserted. MenuBuilder expands collapsible action views on item taps.
  ARCHITECTURE 6.4.1 and the boot section updated.
- 2026-10-02 (WS3, WS4 app model): embedded activities. Activity has
  AOSP's `mParent`/`mEmbeddedID`; `isChild`/`getParent` report them, a
  child's window is contained by its parent's, and a child's finish,
  startActivityForResult (results come back tagged with the child's id),
  setTitle and options menu callbacks go through the parent. ActivityThread
  gained package-private `resolveActivityInfo`, `startActivityNow` and
  `destroyEmbeddedActivity`, which LocalActivityManager drives instead of
  AOSP's client transactions. `isRootNamespace`/`setIsRootNamespace` moved
  from ViewGroup to View (as in AOSP). ARCHITECTURE 6.5 updated.
- 2026-10-02 (WS3): CalendarView mode 0 constructs
  CalendarViewLegacyDelegate (the holo week list) instead of the material
  day picker. `DateUtils.getDayOfWeekString` with `LENGTH_SHORTEST`
  returns one letter, and `formatDateRange` with `FORMAT_NO_MONTH_DAY`
  includes the month name. Format pieces still join with ", ", so the
  week-list title is "March, 2024". ARCHITECTURE 6.4.1 updated.
- 2026-10-02 (WS3, touches WS7): `Context.AUDIO_SERVICE` returns an
  AudioManager. Focus requests are granted and never revoked.
  `MediaPlayer.prepareAsync` fails with `MEDIA_ERROR_UNKNOWN` /
  `MEDIA_ERROR_UNSUPPORTED` until WS7 has a decoder. VideoView shows the
  framework error dialog when no OnErrorListener consumes the error.
  ARCHITECTURE 6.4.1 and 6.6 updated.
- 2026-10-02 (WS1): `View.startActionMode(callback, TYPE_FLOATING)` creates
  a FloatingActionMode. Its toolbar is a popup of the menu items above
  the content rect from `Callback2.onGetContentRect` (below it when the
  row does not fit). `hide` dismisses the popup without finishing the
  mode. Text selection does not open one yet. ARCHITECTURE 6.4 updated.
- 2026-10-02 (WS1): A long press on selectable or editable text selects
  the word and starts a TYPE_FLOATING action mode on the selection
  bounds. Copy, cut, and paste call onTextContextMenuItem and finish
  the mode. Select all keeps it and updates the rect.
  Context.CLIPBOARD_SERVICE returns one process-wide ClipboardManager.
  View.performLongClick(float, float) stores the point and calls
  performLongClick(), so a no-arg override sees an anchored press.
  ARCHITECTURE 6.4 updated.
- 2026-10-02 (WS1): `View.setClipToOutline(true)` clips that view, its
  background, and its children to the outline from `getOutlineProvider()`.
  Only round rects clip. A path outline (`Outline.canClip()` is false)
  does not. `invalidateOutline()` invalidates the view. Shadows are not
  drawn. ARCHITECTURE 6.4.1 updated.
- 2026-10-02 (WS1, touches the VM and libcore): `Class`, `Field` and
  `Method` `getAnnotation` return RUNTIME annotations from the dex,
  including defaults, nested annotations, arrays, enums and class
  literals. CLASS and SOURCE retention stay invisible.
  `ViewDebug.dumpCapturedView` logs `@CapturedViewProperty` fields and
  no-arg methods. `RemoteViews.onLoadClass` still allows framework View
  packages by name and does not require `@RemoteView`. ARCHITECTURE 4.2
  and 6.4.1 updated.
- 2026-10-02 (WS1): `DisplayCutout` stores safe insets, one bounding rect
  per edge, and waterfall insets. `WindowInsets.getDisplayCutout`
  returns it. `consumeDisplayCutout` drops the cutout and leaves type
  insets in place. `Builder.setDisplayCutout` does not add type insets
  by itself. `PhoneWindow` copies `windowLayoutInDisplayCutoutMode`
  onto `LayoutParams`. The Switch reports no cutout, and `getCutoutPath`
  stays null because cutout specs are not parsed. ARCHITECTURE 6.4.1
  updated.
- 2026-10-02 (WS5): `View.draw` applies the child's tween `Animation`
  (matrix and alpha) and keeps invalidating its parent until the
  animation ends. `fillAfter` false clears it on the last frame. No new
  native fields. ARCHITECTURE 6.4 updated.
- 2026-10-02 (WS5): `ValueAnimator`, `ObjectAnimator` and
  `ViewPropertyAnimator` run on Choreographer frames and write view
  properties (translation, scale, rotation, alpha, and x/y/z).
  Invalidation reuses `invalidateChild`'s child-matrix transform. No new
  native fields. ARCHITECTURE 6.4 updated.
- 2026-10-07 (WS11): new platform call `platform_network_state(PlatformNetwork*)`
  (connected, `PLATFORM_NET_*` transport, Wi-Fi bars), implemented by
  the headless backend (`/data/local/tmp/network` or `SWITCHAPK_NETWORK`
  override) and the Switch backend (nifm, opened lazily, closed in
  `platform_shutdown`). New native classes `libcore.io.Net`
  (`src/native/java_net.c`) and `ConnectivityManager.nGetState`
  (`src/android/android_net.c`). `BroadcastQueue.register` asks
  ConnectivityManager for the sticky CONNECTIVITY_ACTION when a filter
  has that action. ARCHITECTURE 5.1 and 7 updated.
- 2026-10-08 (WS15): new platform calls `platform_sensor_mask()`,
  `platform_sensor_set_rate(type, period_us)` (samples as PEV_SENSOR in
  Android units and axes) and `platform_battery_state(PlatformBattery*)`;
  `platform_vibrate` takes an amplitude (`platform_vibrate(ms, amplitude)`).
  Headless script commands `sensor accel|gyro x y z` and
  `battery <level> [none|ac|usb]`. New natives
  `SystemSensorManager.nGetSensorMask/nSetRate` and
  `BatteryManager.nGetState`; `Vibrator$SystemVibrator.nativeVibrate` is
  now `(II)V`. `SystemSensorManager` installs itself as the
  `PlatformInput` sensor sink. `BroadcastQueue.register` asks
  `BatteryManager.stickyBatteryIntent` for ACTION_BATTERY_CHANGED when a
  filter has a battery action. ARCHITECTURE 6.4, 6.8 and 7 updated.
- 2026-10-09 (WS11): new native file `src/native/java_zip.c`
  (`natives_java_zip_register`): static natives on `java.util.zip.CRC32`,
  `Adler32`, `Inflater` and `Deflater`. Inflater/Deflater keep their
  `z_stream` address in a private long and pass it as an argument, so C
  reads no Java fields. `HttpURLConnectionImpl` adds `Accept-Encoding:
  gzip` and decodes gzip responses. ARCHITECTURE 5.1 and 5.2 updated.
- 2026-10-09 (WS13): `switchapk-host --shim-symbols` prints the names the
  native shim provides (`shim_libc_symbols` and `shim_android_symbols`),
  one per line, and exits. `tools/corpus.py` uses it to find native
  imports that would bind to logging stubs. ARCHITECTURE 8 updated.
