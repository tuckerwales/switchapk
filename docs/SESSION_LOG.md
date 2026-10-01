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
- SurfaceView, Surface (software buffer queue that paces render threads)
  and a software TextureView; `tests/apps/surface` checks them.
- Left in WS1: context menu and action mode presentation (needs WS4
  dialogs), clipToOutline.

### Session 5 (2026-10-01, branch ccr-08dbdaa2-6llszo)
- Text engine: Spanned/Spannable, spans, TextUtils, TextPaint,
  StaticLayout, BoringLayout and DynamicLayout (greedy wrap, ellipsize,
  alignment). No bidi or shaping. Checked by `tests/apps/text` SelfTest.
- TextView measures and draws through Layout. Common XML attributes,
  gravity, ellipsize, hints, compound drawables, selection and cursor,
  and password, single-line and all-caps transformations. Spannable text
  ellipsizes via a framework-internal `DynamicLayout.Builder.setMaxLines`.
- `tests/apps/text` inflates TextViews and checks screenshots plus
  in-process logic (wrap, ellipsis count, gravity, spans, TextWatcher,
  password dots, length filter).
- EditText, arrow/scrolling/link movement, key listeners (text, qwerty,
  digits) and BaseInputConnection. A focused editor asks for platform
  text; the result is committed with InputConnection.commitText.
  `tests/apps/text` types hello, A and DEL and checks the TextWatcher.
- Html.fromHtml/toHtml/escapeHtml for the common tags, Linkify (web,
  email, phone, a simple street-address pattern) and
  DateUtils/DateFormat/Formatter. autoLink runs Linkify from setText.
  Checked in `tests/apps/text` without moving the screenshot bands.
  Copy and paste still need a clipboard service.
- WS3 started. ImageView (scale types, adjustViewBounds), Button,
  ImageButton, CompoundButton, CheckBox, RadioButton, RadioGroup,
  ToggleButton, Switch and Space. A tap toggles the compound controls.
  tests/apps/widgets checks measure, exclusive radios and screenshots.
- WS3 scrolling. Scroller and OverScroller (viscous scroll, spline fling).
  Constructors that take an Interpolator stay unimplemented because that
  type is not in the tree. EdgeEffect draws a glow. ScrollView and
  HorizontalScrollView drag, fling and clamp to the child.
  tests/apps/scroll swipes a tall column and a wide row; the sample pixel
  leaves the red band for blue. In-process checks cover clamp, fling
  distance, spring-back and the edge glow.
- WS3 lists. Adapter, ListAdapter, SpinnerAdapter, BaseAdapter,
  ArrayAdapter, Filter and ListView. AbsListView recycles visible rows,
  and headers, choice mode and dividers work. tests/apps/list swipes the
  list; the sample pixel leaves the red row for blue. In-process checks
  cover recycling, the end clamp, selection, headers, filtering and
  stackFromBottom.
  Progress, popups and Toolbar are still open.

- WS2 follow-up (merged with the parallel ws2/ws3 work). MetaKeyKeyListener,
  BaseMovementMethod, ArrowKeyMovementMethod, ScrollingMovementMethod and
  LinkMovementMethod are now straight AOSP ports: meta states report
  pressed (1) or locked (2) as on Android, META_SELECTING exists (hidden
  KeyEvent constant), modifiers pick word/line/paragraph moves, and
  LinkMovementMethod moves between links with the D-pad and clicks the
  selected one with A/center. Added Touch, Dialer/Date/Time/DateTime key
  listeners, HideReturnsTransformationMethod, TransformationMethod2, and
  the inputmethod value types (ExtractedText, ExtractedTextRequest,
  CompletionInfo, CorrectionInfo, SurroundingText, TextAttribute,
  InputContentInfo, TextSnapshot) with the matching InputConnection,
  BaseInputConnection and TextView methods (extractText, setExtractedText,
  onCommitCompletion, getHorizontallyScrolling). tests/apps/text checks
  D-pad link selection and click and the alt meta state.
