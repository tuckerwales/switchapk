# Session log

Append one entry per session (newest last): date, branch, what was done,
what is left in flight.

### Session 1
- Built the VM (interpreter, class linking, GC, threads/GIL, JNI with
  call trampolines), libcore, core parsers (zip, dex, AXML/ARSC), host
  driver.
- JVM-differential conformance test (VmTest) passing; fixed branch
  dispatch, StackOverflowError handling, several libcore gaps.
- Obtained devkitA64/libnx/portlibs without a docker daemon.

### Session 2 (2026-09-30, branch ccr-7d62ed8a-m0sd12)
- Added the software renderer (`src/gfx`), fonts, image codecs, clip masks.
- Headless platform (`src/platform`) with scripted input and screenshots.
- Multi-package ResTable; `tools/make_framework_res.py` (framework
  resources from android.jar); `tools/genr` (android.R).
- AssetManager and graphics natives (`src/android`).
- ~300 framework Java files: util, xmlpull, json, os, content(+res, pm),
  net.Uri, database, graphics, drawables.
- Documentation set (this docs/ directory, CLAUDE.md) and
  `tools/fetch_toolchains.py`.
- In flight: WS0 (framework does not compile yet; android.view and the
  rest are next).

### Session 3 (2026-09-30, branch ccr-7d62ed8a-m0sd12)
- WS0 landed. `java/framework` compiles. Activity, PhoneWindow and
  ViewRootImpl present one full-screen view.
- Resource, graphics and OS natives are registered. `app_runner` opens an
  APK, loads its dexes and enters `ActivityThread.main`.
- `tests/apps/hello` draws a dark background, a gold rectangle and
  "Hello Switch". The headless screenshot matches.
  `tools/build_apk.sh` builds unsigned test APKs.
- WS0 is done. View system, widgets, app model and the other post-WS0
  packages are not started.

### Session 4 (2026-10-01, branch ccr-08dbdaa2-6llszo)
- Claimed WS1 and ported the view system core from AOSP: View, ViewGroup
  (touch targets, interception, split events), MotionEvent (multi-pointer,
  history), KeyEvent, KeyCharacterMap, InputDevice, ViewConfiguration,
  VelocityTracker, GestureDetector, ScaleGestureDetector, FocusFinder,
  ViewTreeObserver, WindowInsets, accessibility value classes.
- Choreographer with callback queues; ViewRootImpl with dirty-rect redraw,
  touch mode, focus navigation and synthetic D-pad; WindowManagerGlobal
  with window stack, input routing, A/B fallbacks, dim and compositing.
- LayoutInflater, ViewStub, PhoneWindow/DecorView (screen_simple decor),
  MenuInflater with an internal menu model, Activity as Window.Callback;
  FrameLayout and LinearLayout ported for the decor.
- `tests/apps/views` (WS1 acceptance) and `tests/apps/shotlib.py`;
  `tools/api_check.py`; headless `idle` waits for queued input.
- Left in WS1: SurfaceView/TextureView, context menu and action mode
  presentation (needs WS4 dialogs), clipToOutline.

