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
- WS4 started (dialogs). Dialog, AlertDialog and its Builder are ports of
  AOSP on top of the real framework-res alert layouts (title, message,
  up to three buttons, item lists, single and multi choice, custom
  view), with AlertController and the internal AlertDialogLayout,
  ButtonBarLayout and DialogTitle. Added CheckedTextView (choice rows),
  the ActionBar API (abstract; no decor yet), a FragmentTransaction
  placeholder and InternalRes for internal resource ids. Two fixes in
  the WS3 list code for D-pad use: ListView tracks DPAD_CENTER/ENTER so a
  confirm key clicks the selected row, and AbsListView draws the
  selector on the selected row when out of touch mode.
  tests/apps/appmodel drives an alert and a choice list with the D-pad.
  ProgressDialog waits for WS3's ProgressBar.
- RelativeLayout (WS3 scope, written from WS4 because the framework menu
  item layouts need it): AOSP port with the dependency graph, start/end
  rule resolution and the API 18+ measure rules. ActivityThread now reads
  `uses-sdk`, so ApplicationInfo.minSdkVersion/targetSdkVersion are real
  (they were 0, which selected legacy code paths). tests/apps/relative
  checks 13 positions and a screenshot.
- WS4 menus. Options menu: PhoneWindow handles MENU (the + button falls
  back to MENU), builds the menu through Window.Callback and shows it as
  an overflow-style popup (MenuPanel) in the top end corner. Context menus
  (long press, showContextMenu) and sub menus are AlertDialog lists via
  MenuDialogHelper with ListMenuItemView rows from the framework layouts;
  selections reach onOptionsItemSelected/onContextItemSelected and
  closing reaches onOptionsMenuClosed/onContextMenuClosed. View sets the
  menu info while it adds context items. Fixed WS2 TextView height with
  maxLines/singleLine (AOSP getDesiredHeight: whole lines, not ems), which
  clipped descenders of single-line text. tests/apps/appmodel covers the
  options popup, a sub menu and a context menu.
- WS4 lifecycle and fragments. Activity is rewritten around AOSP's
  perform* methods (pre/post ActivityLifecycleCallbacks, SuperNotCalled
  checks, managed dialogs, non-config instances, FragmentController
  host), Application gains the full callback set, and the platform
  fragments are ported (Fragment, FragmentManagerImpl with back stack and
  saved/retained state, BackStackRecord, DialogFragment, ListFragment,
  LoaderManager). ActivityThread's stack now posts start/finish to the
  looper and follows AOSP ordering for launch, finish, results, launch
  modes and intent flags, and treats PEV_RESIZE as a configuration change
  that relaunches activities (stopped ones lazily). The headless script
  gained `screen WxH@dpi`; the headless present now reallocates its frame
  when the size changes (it overflowed). VM fix: Class.getModifiers reads
  member class flags from the InnerClass annotation (a public static
  nested Fragment was rejected). tests/apps/lifecycle drives a dock and
  undock with two activities, results, single-top and a fragment and
  checks screenshots and the callback order in the log. Open question:
  two script taps with no idle between them click a button once.
- WS4 services and broadcasts. In-process BroadcastQueue (registered,
  manifest, ordered with priority and abort, sticky, goAsync, the target-O
  implicit block, restricted receiver context) and ActiveServices
  (started, bound with cached binders and onRebind, stopSelf ids,
  BIND_AUTO_CREATE rules, binding death, executor binds), Service,
  IntentService, PendingIntent (AMS identity and flags, OnFinished,
  createPendingResult), IntentSender, AlarmManager on the main looper, and
  a minimal Notification and NotificationManager that logs. Activities and
  services now get per-component ContextImpls so leaked receivers and
  connections are dropped after onDestroy with AOSP's leak errors. The
  manifest parser now reads intent-filter priority. tests/apps/services
  runs 21 steps, each comparing its event sequence with Android's, and
  checks a result grid plus the framework's log lines. Next for WS4:
  Notification.Builder and channels, action bar decor, ProgressDialog.
- WS4 notifications. Notification rewritten on AOSP's data model:
  Builder with every non-RemoteViews setter, Action (+Builder, RemoteInput,
  data-only inputs), BigText/BigPicture/Inbox/Messaging/Media/
  DecoratedCustomView styles with restore for recoverBuilder,
  BubbleMetadata; Person, RemoteInput (results via ClipData), LocusId,
  NotificationChannel, NotificationChannelGroup, StatusBarNotification and
  NotificationManager.Policy. NotificationManager enforces channels for
  target O+, blocks IMPORTANCE_NONE, follows the channel update rules and
  tracks active notifications. Intent.fillIn now carries ClipData.
  startForeground marks its notification FLAG_FOREGROUND_SERVICE. Note:
  framework code cannot use lambdas or method references (libcore has no
  java.lang.invoke; javac crashes), and `make` failures print "Error",
  so grep case-insensitively. tests/apps/services gains a notifications
  step (replacing the plain notify step).
- WS4 JobScheduler. android.app.job ported: JobInfo (+Builder validation,
  period and flex clamping), JobParameters (dequeueWork/completeWork),
  JobWorkItem, JobService, JobServiceEngine (binder the scheduler drives,
  as AndroidX JobIntentService expects) and JobSchedulerImpl (latency,
  deadline, constraints, periodic windows, backoff, enqueue to a running
  job, stop on cancel/timeout). The manifest parser reads a service's
  android:permission. tests/apps/services gains four job steps (25).
  The WS3 session has pushed nothing since its ListView commit and could
  not be reached; the action bar decor and ProgressDialog wait on its
  Toolbar, ActionMenuView and ProgressBar, so they were left alone.
- WS3 taken over by the WS4 session (the WS3 session stopped; the user
  confirmed). ProgressBar, AbsSeekBar, SeekBar and RatingBar ported from
  AOSP with the Material styles; ProgressDialog (WS4) on the framework
  progress layouts. The WS5 tween core ProgressBar needs landed early
  (TimeInterpolator, Interpolator, basic interpolators, Animation,
  AlphaAnimation, Transformation, AnimationUtils.loadInterpolator).
  Added com.android.internal.graphics.drawable.AnimationScaleListDrawable
  (the Material spinners failed to inflate without it); it shows the
  static frame until AVDs animate. tileify follows AOSP N+ (repeating
  BitmapDrawable clone) so RatingBar star tints survive. tests/apps/progress
  checks the bars, spinner frame, a SeekBar drag, a star tap, a
  ProgressDialog and the listener calls.
- WS3 popups. PopupWindow, ListPopupWindow, DropDownListView, PopupMenu
  (with an internal MenuPopupHelper over the existing MenuAdapter),
  AbsSpinner, Spinner (drop-down and dialog) and Toast ported from AOSP.
  The window manager now clamps windows to the screen (unless
  FLAG_LAYOUT_NO_LIMITS) and does not treat toast windows as activity
  leaks. AdapterView now holds mInLayout/mBlockLayoutRequests and posts
  selection callbacks fired during layout (AOSP SelectionNotifier); the
  first Spinner selection used to set text that never laid out.
  tests/apps/popups covers the drop-down, the dialog spinner, a popup menu
  with a sub menu, a toast, a popup flipping above its anchor, outside
  dismissal and D-pad use of the drop-down.
- WS3 Toolbar. Toolbar, ActionMenuView, ActionMenuPresenter (overflow
  button and popup, action buttons by ActionBarPolicy, submenus),
  RtlSpacingHelper and CollapsibleActionView ported from AOSP. The menu
  core gained presenters (MenuPresenter, BaseMenuPresenter,
  ActionMenuItemView, ActionMenuItem), action item flagging and action
  view expansion in MenuBuilder/MenuItemImpl. DecorToolbar and
  ToolbarWidgetWrapper are ready for the action bar decor (tabs not
  ported). InternalRes.attr now falls back to the "^attr-private" type:
  private attrs such as navigationButtonStyle live there in framework-res,
  so they used to resolve to 0 and silently drop their styles.
  tests/apps/toolbar checks layout, nav/action/overflow clicks and the
  overflow popup.
- WS4 action bar decor and WS1 action modes. PhoneWindow now inflates the
  decor AOSP would (screen_toolbar for action bar themes, the dialog
  title decor, screen_title, screen_simple) and drives the options menu
  through the decor toolbar (posted rebuild, overflow on MENU). Ported
  ActionBarOverlayLayout, ActionBarContainer, AbsActionBarView,
  ActionBarContextView, DecorContentParent, WindowDecorActionBar (with
  ActionModeImpl; tabs dispatched, no tab strip), ToolbarActionBar,
  WindowCallbackWrapper and StandaloneActionMode; DecorView starts
  action modes and BACK ends them. Activity getActionBar, setActionBar,
  getMenuInflater (themed), home-as-up, menu visibility and MENU key
  routing follow AOSP. Contexts with no theme now get
  Resources.selectDefaultTheme, so tests/apps/hello (no theme) shows an
  action bar like on Android; its checks moved down by 72px.
  tests/apps/actionbar covers the bar, items, overflow, MENU, both kinds
  of action mode, hide/show and setActionBar(Toolbar).
- WS3 grid and tables. AbsListView now fills, scrolls and recycles whole
  rows (itemsPerRow, per-column width and left hooks); GridView ports
  AOSP determineColumns, stretch modes, spacing and gravity on top, with
  D-pad navigation that keeps the selected row on screen. TableLayout,
  TableRow (on LinearLayout's virtual child hooks) and AbsoluteLayout
  ported. Fixed a list bug: ACTION_UP started a fling and then reset the
  touch mode, so flings never ran and the next tap only "stopped" the
  dead scroller. tests/apps/grid covers all of it.
- WS10 Switch backend (claimed by the WS4 session). Toolchain fetch now
  streams layers to disk with Range resume (a 380 MB layer kept getting
  cut). Makefile.switch builds build/switch/switchapk.nro with
  framework.dex and framework-res.apk in romfs; `dist` makes an SD zip
  with the sample APKs. platform_switch.c (framebuffer, pad, stick as
  D-pad, touch, applet hooks, swkbd on the main thread) and
  main_switch.c (launcher, VM thread on core 1, error screen with recent
  log lines, log file, nxlink, relaunch through hbloader). Fonts fall
  back to the system shared fonts. The whole tree compiled for the
  Switch with one fix. The VM was never run on AArch64 before: the host
  build cross-compiled for aarch64 passes VmTest and every sample under
  qemu-user. Not yet booted on hardware (no device here).
- WS3 adapters. Filter now works like AOSP (worker thread, results on
  the caller's looper, a newer request replaces a pending one). Ported
  SimpleAdapter, CursorAdapter (+CursorFilter, FilterQueryProvider),
  ResourceCursorAdapter, SimpleCursorAdapter, the expandable list family
  (ExpandableListView, ExpandableListConnector, ExpandableListPosition,
  BaseExpandableListAdapter, SimpleExpandableListAdapter,
  HeterogeneousExpandableList), TwoLineListItem, AutoCompleteTextView and
  MultiAutoCompleteTextView. Fixed two list bugs found by the drop-down:
  setSelection(INVALID_POSITION) selected row 0, and leaving touch mode
  resurrected a drop-down selection that should stay hidden until the
  first arrow key. tests/apps/adapters covers the adapters, expand and
  collapse by touch, child clicks, and typing then picking a suggestion
  with the D-pad and A.
- WS3 pickers and clocks. Ported NumberPicker (Material selector wheel:
  fling and adjust scrollers, taps above or below the dividers, long-press
  repeat, D-pad steps, wrapping, displayed values, formatters, the input
  filter), Chronometer (ticks on second boundaries, count down, format)
  and TextClock (12/24-hour formats, time zone, ticks scheduled on the
  next second or minute since there is no TIME_TICK broadcast). Scroller
  gained its Interpolator constructors. View now draws fading edges
  (`requiresFadingEdge`; the legacy `fadingEdge` attr is ignored as on
  ICS and later), which the wheel uses to fade its outer values. Fixed a
  ViewRootImpl bug: the dirty rect was emptied after drawing, so an
  invalidate() from computeScroll() during the draw was lost and
  animations driven that way stalled after one frame. That fix exposed
  RippleDrawable swapping a colour filter on its layers while drawing,
  which invalidated and so redrew forever; the swap now detaches the
  layer's callback. Also ported the ViewAnimator family (ViewAnimator,
  ViewFlipper, ViewSwitcher, TextSwitcher, ImageSwitcher and the
  internal DialogViewAnimator the date picker needs); View now holds
  tween animations for WS5 to apply. tests/apps/pickers
  covers taps, a drag with the adjust snap, wrapping, a value at its
  limit, D-pad focus and steps, and the clocks' text.
- WS3 date widgets. Ported the internal ViewPager and PagerAdapter, the
  material day picker (SimpleMonthView with touch and D-pad day
  navigation, DayPickerPagerAdapter, DayPickerViewPager, DayPickerView,
  YearPickerView), DatePicker with its calendar and spinner delegates,
  CalendarView (material delegate; the holo week list falls back to it)
  and DatePickerDialog. Supporting pieces: StateSet view-state masks,
  ColorStateList.hasState, NumberPicker's two-digit formatter, narrow
  month and weekday names in the formatters, and an en-US skeleton table
  for getBestDateTimePattern. Fixed three things the dialog exposed:
  Calendar.set normalized pending fields before each set (Feb 30 became
  Mar 30), LinearLayout used the pre-N weighted measure for every app
  (a weighted child got its wrap width plus the excess), and
  DialogViewAnimator lacked AOSP's measure, so its match_parent year
  list made the picker report TOO_SMALL and the dialog went full screen.
  Faithful quirk kept: CalendarView passes its attributes to the inner
  DayPickerView, so `android:visibility="gone"` in XML hides that view for
  good. tests/apps/dates covers day taps, month paging by arrow and swipe,
  the year list, the spinners (leap day), the dialog and the CalendarView
  with touch and D-pad.
- WS3 time widgets. Ported TimePicker with the clock delegate (header with
  NumericTextView hour and minute, AM/PM radio labels, RadialTimePickerView
  with the 24-hour inner ring, minute snapping that prefers the marks, the
  between-marks dot, auto-advance from hours to minutes, the text input
  mode via TextInputTimePickerView) and the spinner delegate (hour, minute
  and AM/PM NumberPickers, the minute wrap rolling the hour and AM/PM),
  plus TimePickerDialog (validates text input before OK). The hours to
  minutes crossfade runs on a frame callback since ObjectAnimator is WS5.
  The AM/PM labels exposed CompoundButton's old placeholder box, drawn for
  any null button drawable; it is gone, since the material indicators load
  now, and tests/apps/widgets checks the real checkbox and radio drawables
  instead. tests/apps/times covers radial hour and minute taps, AM/PM, the
  spinner wrap, the 24-hour dialog and typing an hour in text input mode.
- WS3 GridLayout. Ported GridLayout whole: auto placement around explicit
  indices and spans, the per-axis Bellman-Ford constraint solve with
  topologically sorted arcs, alignment groups (start, end, left, right,
  center, fill, baseline), default margins (half of `default_gap`, none
  for Space), ALIGN_BOUNDS, row and column weights (binary search for the
  largest share that still solves), GONE handling and the consistency
  check. ViewGroup.onSetLayoutParams became protected so GridLayout can
  hook it. tests/apps/gridlayout has 29 logic checks and a calculator
  grid with row and column spans, a weighted row that re-splits when a
  cell goes GONE, and an alignment column with a row-weighted cell.
- WS3 SearchView. Ported SearchView (with SearchAutoComplete and the
  updatable touch delegate), the package-private SuggestionsAdapter,
  SearchManager (searchables from the app's ACTION_SEARCH activities,
  `getSuggestions`, the search dialog, global search reporting none),
  SearchableInfo (searchable XML with actionkey children), SearchDialog
  on the framework `search_bar` layout, and the RecognizerIntent
  constants. Activity gained onSearchRequested, startSearch,
  triggerSearch, type-to-search key modes and a per-activity
  SearchManager. Fixes found on the way: the manifest parser now stores
  `<meta-data>` and `PackageManager.getXml` works; TextView handles Enter
  in single-line editors before its key listener (Enter was being
  inserted as a newline); MenuBuilder expands collapsible action views on
  tap; Filter supports the hidden Delayer; AutoCompleteTextView's hidden
  doBefore/AfterTextChanged do real work; ListPopupWindow.show waits for
  an anchor. tests/apps/search has 41 logic checks and covers the action
  bar SearchView with provider suggestions and refine arrow, launching
  ResultsActivity from a suggestion, the search dialog, an in-place list
  filter and an iconified SearchView expanding and closing.
