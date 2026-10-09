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
- WS3 TabHost. Ported TabHost (view id, factory and intent content,
  label, label and icon, and custom view indicators, D-pad focus hand-off
  from embedded activities), TabWidget (bottom strips, imposed tab widths
  when the row overflows, selected tab drawn last), and the embedded
  activity stack: LocalActivityManager, ActivityGroup and TabActivity.
  Activity gained its parent and embedded id, with finish,
  startActivityForResult, setTitle and the options menu routed through
  the parent and results routed back to the child. tests/apps/tabs has 56
  logic checks and covers material tab indicators, the view and factory
  tabs, two embedded activities (only the current one resumed), a result
  delivered to an embedded activity, recreation on the same tab with
  retained instances, and BACK finishing the group through its child.
- WS3 CursorTreeAdapter family. Ported CursorTreeAdapter (a group
  cursor plus lazily fetched children cursors per group, closed when the
  group collapses or the data set changes, null children cursors filled
  later by setChildrenCursor, the group Filter), ResourceCursorTreeAdapter
  (collapsed, expanded, child and last child layouts) and
  SimpleCursorTreeAdapter (column to view binding with a ViewBinder).
  tests/apps/adapters gained 13 logic checks (40 in all), including an
  ExpandableListView collapse closing the children cursor.

### Session 7 (2026-10-02, branch ccr-08dbdaa2-6llszo)
- WS3 holo CalendarView. Mode 0 (`Widget.CalendarView` and the Holo
  styles) now builds CalendarViewLegacyDelegate: the framework
  `calendar_view` layout, a month title, single-letter day names, and a
  ListView of weeks with week numbers, a selected-week tint and the
  vertical bar drawable. Theme.Material still builds the material
  delegate, so `new CalendarView(activity)` is unchanged.
  `DateUtils.getDayOfWeekString` honors `LENGTH_SHORTEST`, and
  `formatDateRange` with `FORMAT_NO_MONTH_DAY` keeps the month name.
  Pieces still join with ", ", so the title is "March, 2024".
  tests/apps/holocal has 19 logic checks and covers March 2024 with the
  15th selected, a tap on Sunday the 10th, and a swipe that settles on
  April with the 10th still selected. tests/apps/dates stays green.
- WS3 VideoView placeholder. Ported VideoView (aspect-ratio measure,
  audio-focus request, the framework error dialog, MediaController
  attach) and MediaController (the `media_controller` layout, seek bar,
  play, rewind, fast-forward, and prev/next once listeners are set).
  `Context.AUDIO_SERVICE` returns an AudioManager that grants focus.
  MediaPlayer keeps the data source and `prepareAsync` posts
  `MEDIA_ERROR_UNKNOWN` / `MEDIA_ERROR_UNSUPPORTED`, because decoding is
  WS7. Subtitles are reported unsupported. tests/apps/video has 16 logic
  checks: the controller draws 1:05 of a 2:05 clip, and Play opens
  "Can't play this video."
- WS3 RemoteViews. apply inflates the layout and runs the action list:
  reflection setters, click and checked PendingIntents, fill-in against
  a template tag on an ancestor, and RemoteCollectionItems as a
  BaseAdapter. onLoadClass allows framework View packages because the
  VM does not surface the RemoteView annotation. DrawInstructions apply
  as an empty view, and setRemoteAdapter(Intent) is not hosted.
  Notification content views stay unsupported. tests/apps/remote has 43
  logic checks, including a parcel snapshot, a landscape and sized
  choice, a two-row list whose fill-in delivers the row extra, and an
  Open click that reapply()s an orange swatch.
- WS1 floating action mode toolbar. TYPE_FLOATING now builds a
  FloatingActionMode. The menu is a horizontal popup above the content
  rect from Callback2.onGetContentRect (below it when there is no room).
  hide dismisses the popup without ending the mode, and BACK finishes a
  floating mode before a primary one. Text selection does not open one
  yet. tests/apps/floating has 9 logic checks: Copy and Share sit above
  a yellow selection, and tapping Copy finishes the mode.
- WS1 text selection toolbar. A long press on selectable or editable
  text selects the word and opens the floating action mode on the
  selection bounds. Cut, Copy, Paste, and Select all use
  onTextContextMenuItem. Copy, cut, and paste finish the mode; Select
  all updates the rect. Context.CLIPBOARD_SERVICE is a process-wide
  ClipboardManager. tests/apps/select has 7 logic checks: "beta" is
  highlighted, Copy is the first toolbar item, and tapping it clears
  the selection.
- WS1 clipToOutline. setClipToOutline clips the view, its background,
  and its children to a round-rect outline. Path outlines do not clip,
  and shadows are not drawn. tests/apps/outline has 3 logic checks: a
  48dp corner is the page color, and the inside of the arc stays the
  child's yellow.
- WS1 ViewDebug annotations. `android.view.ViewDebug` carries
  ExportedProperty, CapturedViewProperty, IntToString and FlagToString.
  The VM now returns RUNTIME annotations from `Class`, `Field` and
  `Method` `getAnnotation`, including defaults, nested annotations,
  arrays, enums and class literals. `dumpCapturedView` logs captured
  fields and no-arg methods. Hierarchy tracing stays a no-op.
  tests/apps/viewdbg has 11 logic checks, and VmTest covers the
  reflection against the reference JVM.
- WS1 DisplayCutout. The class stores safe insets, per-edge bounds and
  waterfall insets. WindowInsets.getDisplayCutout returns one attached
  by the builder, and consumeDisplayCutout drops it without clearing
  type insets. PhoneWindow reads windowLayoutInDisplayCutoutMode into
  the layout params. The Switch reports no cutout, and getCutoutPath
  stays null. tests/apps/cutout has 15 logic checks: shortEdges comes
  from the theme, an inset notch moves, and the live window insets
  have no cutout.
- WS10 launcher labels and icons. The NRO boot on hardware works. The
  launcher reads each APK's MAIN/LAUNCHER label and bitmap icon (else the
  application's, else the file name) at 240 dpi, and draws them on the
  row. XML drawables are skipped. `switchapk-host --apk-info` checks this
  without the VM: tests/apps/labeled (a @string label and an hdpi PNG that
  beats the application icon and the mdpi bucket), the hello APK's literal
  label, and a zip with no manifest. Audio, rumble, and 1080p stay open.
- WS5 view tweens. `View.draw` applies the current Animation (matrix and
  alpha) and keeps invalidating the parent until it ends. `fillAfter`
  false clears it after the last frame. `AnimationUtils` loads set, alpha,
  scale, rotate and translate. An `AnimationSet` pushes duration, fill,
  repeat, offset and the shared interpolator only when that tag set them,
  so a child keeps its own duration. Property animators, StateListAnimator,
  layout animation and AVD stay later. tests/apps/tween has 9 logic
  checks: a 6s slide is partway across at 2s and parked 300px to the
  right, a scale stays at half size, and a fade is neither solid nor gone
  in the middle.
- WS5 property animators. ValueAnimator, ObjectAnimator and
  ViewPropertyAnimator run on Choreographer frames. ObjectAnimator sets
  properties by name (the float setter is the primitive, not only the
  boxed form) or through android.util.Property. View.animate() batches
  one frame of property changes onto one animator. Values stay put when
  the animator ends. Multi-float and multi-int holders log once and do
  not call the setter. StateListAnimator, layout animation and AVD stay
  later. tests/apps/prop has 9 logic checks: a 6s translation is partway
  across at 2s and parked 300px to the right, a scale stays at half
  size, and a named alpha fade is neither solid nor gone in the middle.
- Next on device: audio (audren/audout), rumble, and 1080p docked. Next
  on WS5: StateListAnimator, layout animation, AVD.

### Session 8 (2026-10-02, branch ccr-08dbdaa2-6llszo)
- GitHub Actions workflow `.github/workflows/package.yml` fetches the SDK
  and devkitPro, builds the host tree and `switchapk.nro`, and packages
  the sample APKs with `make -f Makefile.switch dist`. The run uploads
  `build/switch/switchapk-sd.zip` as the `switchapk-sd` artifact.
- WS13 screenshot runner, log scanning, and VmTest on CI are still open.

### Session 9 (2026-10-02, branch ccr-9f2fb326-x1o1g3)

- WS8 OpenGL ES and EGL. `tools/gen_gles.py` generates GLES10/11/20/30/31/32
  (+Ext) from android.jar signatures and the Khronos headers (850 natives,
  561 entry points), the GL10/GL11 interfaces and GLImpl; 24 irregular
  methods are hand-written, and the KHR debug callbacks log as unsupported.
  EGL14, EGL10/EGL11, EGLExt, GLUtils, Matrix and GLU (full ports) and an
  AOSP GLSurfaceView sit on `EGLNative`. Window surfaces are pbuffers read
  back into the SurfaceView buffer queue on swap, so GL frames reach the
  screen through the existing consumer path. GL is loaded with dlopen on
  the host (no link dependency) and linked from switch-mesa on the Switch
  when installed. tests/apps/gles renders a GLES2 textured cube on Mesa
  llvmpipe and checks it; Ubuntu's Mesa has no ES1 contexts, so the GLES1
  half checks the "OpenGL ES unavailable" panel there.
- WS9 native loader. An ELF64 loader replaces loader_stub.c: it maps
  lib/<abi>/ libraries from the APK, loads DT_NEEDED dependencies,
  applies RELA, APS2 and RELR relocations (x86-64 and AArch64), runs
  constructors and JNI_OnLoad, and binds imports nobody provides to
  stubs that log once and return 0. The shim maps bionic libc/libm onto
  the host C library with wrappers where bionic differs (paths, sysconf,
  pthread objects, __sF, fortify), plus liblog, libdl, AAssetManager,
  system properties, zlib and GL. `os.arch` now reports the real CPU and
  nativeLibraryDir is set. tools/build_apk.sh packages assets/ and
  native/build.sh output. tests/apps/ndk builds two libraries with clang
  and lld for an Android target (no NDK) and runs 19 JNI checks; they
  pass on x86-64 and on the AArch64 host build under qemu.
- Switch build checked with devkitPro: the NRO links with switch-mesa
  (needed -lstdc++) and compiles without it. Not run on hardware.
- The SD package (and the Package NRO workflow) now includes gles.apk and
  ndk.apk with arm64-v8a libraries, for the first hardware runs of GL and
  the native loader.

### Session 10 (2026-10-02, branch ccr-7d62ed8a-m0sd12)

- WS9 Switch code memory. The loader mirrors each library (and its
  unresolved-import stub page) into the alias region with
  svcMapProcessCodeMemory and svcSetProcessMemoryPermission, on the
  own-process handle. Code pages are read-execute and data pages stay
  read-write; a write+execute request keeps execute. An applet, where
  hbloader does not hint those syscalls, fails the load with that
  reason. The NRO links again: newlib has no getpagesize, posix_memalign
  or pipe, so the shim provides the first two and pipe returns ENOSYS.
  newlib struct translation is still open. Not run on hardware.
- WS9 NativeActivity. `android.app.NativeActivity` matches the jar,
  including the hidden load and lifecycle natives. The content view is
  a full-bleed SurfaceView (PhoneWindow.takeSurface does not install a
  surface) with format RGBA_8888. The entry, default
  ANativeActivity_onCreate, runs before that surface exists.
  ANativeWindow locks the Surface as RGBA bytes and posts ARGB through
  lockGlBuffer. The NDK shim's eglCreateWindowSurface takes an
  ANativeWindow, backs it with a pbuffer, and eglSwapBuffers reads the
  frame back the same way Java does. tests/apps/native clears red and
  paints a gold rectangle; the screenshot checks both. ndk, gles and
  VmTest still pass. ALooper and AInputQueue are not delivered, so
  onInputQueueCreated is intentionally not called. Not run on hardware.
- WS7 audio. One 48 kHz stereo float mixer serves SoundPool, MediaPlayer,
  AudioTrack, ToneGenerator and the OpenSL ES buffer queue. Decoders are
  WAV, Ogg Vorbis (stb_vorbis) and MP3 (minimp3, no SIMD, on the host and
  on Switch). The audio thread never takes the VM lock; natives drop the
  GIL before decode and before a blocking write. tests/apps/audio plays
  all five and turns green when every source has contributed a non-silent
  sample. A missing video URI still reports MEDIA_ERROR_UNKNOWN /
  MEDIA_ERROR_UNSUPPORTED (tests/apps/video). ndk, gles, native and
  VmTest still pass, and the Switch NRO links. The Switch audio thread
  still discards samples. Not run on hardware. AAudio remains open.

### Session 11 (2026-10-02, branch ccr-7d62ed8a-m0sd12)

- WS5 StateListAnimator, layout animation, LayoutTransition and
  AnimatedVectorDrawable. A state list runs the first matching animator
  when the drawable state changes. The view holds it strongly, because
  this VM clears weak references on every GC. `android:stateListAnimator`
  is taken from the tag, not the theme, so a framework button style does
  not start an animator on every control. A layout animation binds in
  `dispatchDraw` and staggers a cloned tween by `getDelayForView`.
  `LayoutTransition` fades a child in on add and out on remove. The
  disappearing child stays parented until the fade ends, and is drawn
  after the live children. Change-type animators are stored and not
  started. An animated vector clones its target animators onto the
  vector's groups and paths (trim, color, stroke, transforms) and
  invalidates each frame. pathData is not morphed.
  `AnimationScaleListDrawable` still shows the static spinner frame.
  tests/apps/motion checks the press slide, the staggered column, the
  trim, and the fade at the middle and the end. prop, tween and progress
  still pass. PathInterpolator, the cycle/anticipate/overshoot/bounce
  interpolators, and path morph remain.

### Session 12 (2026-10-02, branch ccr-7d62ed8a-m0sd12)

- WS5 interpolators and path morph. Cycle, anticipate, overshoot,
  anticipateOvershoot, bounce and PathInterpolator load from XML.
  A fraction outside 0..1 extrapolates the first or last keyframe interval, so
  overshoot and anticipate move a property past its from/to values.
  PathParser keeps SVG commands as PathData. canMorph requires the same
  commands, and setPathData rebuilds the vector path. AnimatorInflater
  runs a pathData object animator through that setter. tests/apps/curves
  checks the halfway frame: overshoot past the end, anticipate behind
  the start, bounce, cycle back at rest, a path ease, and a stroke that
  has left both endpoints. prop, tween, motion and progress still pass.
  WS5 is done. PopupWindow enter/exit transitions and the toolbar
  visibility fade still apply immediately: android.transition is not in
  the tree.

### Session 13 (2026-10-02, branch ccr-7d62ed8a-m0sd12)

- WS6 SQLite natives. `android_sqlite.c` implements `SQLiteNative` over
  the bundled amalgamation and maps result codes onto the
  `SQLiteException` subclasses. Paths other than `:memory:` use
  `platform_map_path`. `ContextImpl.openOrCreateDatabase` opens the file
  under `getDatabasePath`. The Switch build compiles SQLite as
  `SQLITE_OS_OTHER` (no WAL) and links `sqlite_vfs_switch.c`, a POSIX VFS
  with pthread mutexes. That VFS was smoke-tested on the host; the NRO
  itself was not rebuilt here.
  tests/apps/store creates `notes.db` at version 1 (selection arguments,
  a rolled-back row, a kept row, a constraint failure, a read-only
  insert) and writes a preference. A second host process with the same
  `--data` directory upgrades to version 2 and reads the preference
  back. Both runs check a full-screen swatch.
  DatabaseUtils, CursorWindow, provider stubs and FileProvider remain.
  WS6 stays in progress. Package NRO fetches the amalgamation before
  `make`: `third_party/sqlite` is gitignored and is not in the toolchain
  cache. The Switch objects compile with devkitA64.

### Session 14 (2026-10-02, branch ccr-7d62ed8a-m0sd12)

- WS6 DatabaseUtils, CursorWindow, provider stubs and FileProvider.
  `CursorWindow` keeps rows in Java. Indexes are absolute, and a put
  that would pass the byte budget returns false so `fillWindow` stops.
  `AbstractCursor.fillWindow` delegates to `DatabaseUtils`.
  `simpleQueryForBlobFileDescriptor` writes the blob to a temp file.
  Settings and media are process providers installed before the manifest
  providers. Settings rows persist under `/data/local/tmp/settings`, so
  the second host run reads the brightness written by the first. Media
  rows last for the process. `androidx.core.content.FileProvider` reads
  the paths XML. The manifest parser stores `grantUriPermissions`, and
  `attachInfo` rejects a provider that is exported or that does not grant
  URI permissions. A resolved path must stay under its root.
  tests/apps/store checks the window, a parcel round trip, the blob
  descriptor, statement types, settings, a media insert, a FileProvider
  read and a `../` escape, on top of the existing two-run database check.
  `make`, the store check and VmTest pass. Not run on hardware.
  Photo-picker and cloud-media helpers on MediaStore are still missing
  and auto-stub. WS6 is done.

### Session 15 (2026-10-02, branch ccr-7d62ed8a-m0sd12)

- The SD zip now packages every sample under `tests/apps`, not the old
  fixed list. `make -f Makefile.switch dist` takes each directory that
  has an `AndroidManifest.xml`. Apps with `native/build.sh` (ndk, native,
  audio) are still rebuilt with `NDK_ARM64=1`. The Package NRO workflow
  fails if any of those APKs is missing from the zip, or if a native APK
  has no arm64-v8a library. The NRO itself was not rebuilt here.

### Session 16 (2026-10-07, branch ccr-9f2fb326-x1o1g3)

- WS9 ALooper, AInputQueue and AConfiguration. ALooper is plain C over
  poll and a wake pipe with AOSP semantics (callbacks, idents, wake,
  fds changed while blocked). NativeActivity creates the input queue with
  the first surface; once the app attaches it, key, touch and joystick
  events are copied into it from the Activity's dispatch methods (10
  pointers, 24 axes each), and an unhandled BACK finishes the activity.
  AConfiguration follows the display. Threads the app created now enter
  the VM through `nl_vm_enter` (attach on first use, detach at thread
  exit), which fixes ANativeWindow posting and the ANativeActivity_*
  calls from the glue thread. tests/apps/input is a glue-style app (own
  thread and looper, command pipe, fd callback, wake from another thread,
  tap, DPAD_CENTER, BACK); it passes on x86-64 and on the AArch64 host
  build under qemu. The Switch NRO links; there ALooper and input report
  failure because newlib has no pipe or poll. The NDK's own
  android_native_app_glue.c was not available here, so the sample
  re-implements its pattern.

### Session 17 (2026-10-07, branch ccr-bd2ea933-rl940q)

- WS11 networking, host side. `libcore.io.Net` natives
  (`src/native/java_net.c`) over BSD sockets with the GIL released
  around every wait, and on top of them InetAddress (getaddrinfo),
  InetSocketAddress, Socket, ServerSocket, DatagramSocket and the
  javax.net factories. `URL.openConnection()` for http is now a real
  HTTP/1.1 client (chunked, fixed and until-close bodies, streamed
  uploads, redirects, Android's FileNotFoundException and error-stream
  behaviour); https fails with SSLHandshakeException until TLS lands.
  New `tests/dex/NetTest.java` runs TCP, timeout, refused,
  close-during-accept, UDP and HTTP scenarios over loopback and matches
  OpenJDK. New platform call `platform_network_state` (headless with a
  `/data/local/tmp/network` override, nifm on the Switch) feeds
  `android.net.ConnectivityManager`, NetworkInfo, Network,
  NetworkCapabilities, NetworkRequest, LinkProperties, network callbacks
  and a sticky CONNECTIVITY_ACTION that follows changes (3 s poll while
  anyone listens). tests/apps/net checks HTTP, UDP, https and DNS
  failures, and the callbacks and broadcasts across Wi-Fi, none and
  Ethernet. `make`, VmTest, NetTest and the sample checks pass (gles
  needs host Mesa, absent here; curves misses its mid-animation frame
  here on the base commit too). `make -f Makefile.switch` builds the NRO
  without warnings; sockets and nifm have not been run on hardware.

### Session 18 (2026-10-08, branch ccr-3bd2dbb6-k37rb5)

- WS15 system services, host side. New platform calls for the motion
  sensors (`platform_sensor_mask`, `platform_sensor_set_rate`, samples
  as PEV_SENSOR in Android units) and the battery
  (`platform_battery_state`); `platform_vibrate` gained an amplitude.
  The headless backend samples script-set values on a thread (`sensor`
  and `battery` script commands). The Switch backend reads the six-axis
  sensor of the handheld Joy-Cons or player 1's controller, psm for the
  battery, and sends HD rumble; it builds without warnings but has not
  been run on a console, so the IMU axis signs are unverified.
- `android.hardware`: Sensor, SensorManager with AOSP's rotation math,
  the listener types, and SystemSensorManager. It paces each listener
  and fuses gravity, linear acceleration, both rotation vectors and the
  legacy orientation sensor from the accelerometer and gyroscope with a
  Mahony filter (no magnetometer, so the heading follows the gyroscope).
  BatteryManager reads live state and keeps ACTION_BATTERY_CHANGED
  current, with power connected/disconnected and battery low/okay
  broadcasts. VibrationEffect is a real waveform played by
  SystemVibrator on its own thread; VibratorManager, CombinedVibration,
  WorkSource and the rest of PowerManager were added. hasSystemFeature
  now reports the console's hardware. POWER_SERVICE was missing from
  ContextImpl before and is now there.
- Absent hardware no longer gives null services: LocationManager
  (providers present, location off), a complete Location, Criteria,
  TelephonyManager (no phone, no SIM, empty operator strings), the
  legacy Camera (no cameras) and camera2 CameraManager.
- tests/apps/sensors drives it all from its script (rest, a quarter
  turn, a 45 degree tilt, battery to 12 % then onto AC, a rumble
  waveform) and passes, also with three copies running at once. `make`,
  VmTest and every sample check pass except gles and native, which need
  host Mesa (libEGL is absent here). `make -f Makefile.switch` builds
  the NRO.


## Session 19, 2026-10-09: sensors sample on a console

- First console run of tests/apps/sensors: one rumble, then "sensing..."
  forever. Not a hang: the text only changed once all 14 checks passed,
  and yaw, tilt, battery low/okay, power connected and charging only
  happen under the host script. The app now shows live accelerometer,
  gyroscope, gravity and orientation readings, how many checks passed
  and which are pending, so a console run shows whether the IMU works
  (lying screen up should read accel about 0 0 9.8; if z is negative,
  flip ACCEL_SIGN in platform_switch.c). The host check still passes.

### Session 20 (2026-10-09, branch claude/new-session-0z3wws)

- WS8 on hardware: tests/apps/gles showed "OpenGL ES unavailable / No
  configs match configSpec" in both views on a Switch. devkitPro's
  switch-mesa EGL driver (src/egl/drivers/switch, all branches) gives
  every config EGL_SURFACE_TYPE = EGL_WINDOW_BIT only and returns NULL
  from CreatePbufferSurface, so our pbuffer configs matched nothing.
- FBO surfaces (android_gl.c): when a display has no pbuffer configs,
  surfaces are framebuffer objects in a surfaceless context (the driver
  has EGL_KHR_surfaceless_context), and binding framebuffer 0 binds the
  current surface's FBO, for the Java bindings and native code alike.
  `SWITCHAPK_EGL_FBO=1` forces the mode on the host. The Java side is
  unchanged.
- `make`, VmTest, and the gles and native samples pass with host Mesa in
  both modes (installed libegl1/libgles2/libgl1-mesa-dri here). `make -f
  Makefile.switch` builds the NRO without warnings. Not yet run on a
  console: the GLES1 view (llvmpipe has no ES1) and the Switch driver's
  FBO completeness for each depth/stencil config are unverified there.
