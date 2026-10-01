package android.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.accessibility.AccessibilityEvent;
import java.util.ArrayList;

/**
 * framework-internal. The top of a view hierarchy: one window. Runs
 * measure/layout/draw traversals on Choreographer frames, keeps the window's
 * pixels in a bitmap (only the dirty region is redrawn), and dispatches input
 * (touch mode, key focus navigation, synthetic D-pad from the joystick).
 * WindowManagerGlobal composites the windows and presents the screen.
 */
public final class ViewRootImpl implements ViewParent {
    private static final String TAG = "ViewRootImpl";

    private static boolean sInTouchMode = true;

    final Context mContext;
    final Handler mHandler;
    final View.AttachInfo mAttachInfo;
    final Choreographer mChoreographer;
    View mView;
    final WindowManager.LayoutParams mWindowAttributes = new WindowManager.LayoutParams();

    // window frame on screen and backing pixels
    int mWinX;
    int mWinY;
    int mWidth = -1;
    int mHeight = -1;
    Bitmap mSurface;
    private Canvas mCanvas;
    final Rect mDirty = new Rect();
    private boolean mFullRedraw = true;
    boolean mDrawnOnce;

    private boolean mTraversalScheduled;
    private boolean mLayoutRequested;
    private boolean mFirst = true;
    private boolean mInLayout;
    private boolean mAdded;
    private boolean mWindowAttributesChanged = true;
    private boolean mRemoved;
    private final ArrayList<Runnable> mAnimationRunnables = new ArrayList<Runnable>();
    private final Rect mTempRect = new Rect();
    private WindowInsets mLastInsets;
    private final SyntheticJoystickHandler mJoystick = new SyntheticJoystickHandler();

    private final Runnable mTraversalRunnable = new Runnable() {
        public void run() { doTraversal(); }
    };

    public ViewRootImpl(Context context) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
        mChoreographer = Choreographer.getInstance();
        mAttachInfo = new View.AttachInfo(this, mHandler, new Binder(), context);
        mAttachInfo.mInTouchMode = sInTouchMode;
    }

    static boolean isInTouchModeStatic() { return sInTouchMode; }

    public void setView(View view, WindowManager.LayoutParams attrs) {
        if (mView != null) return;
        mView = view;
        if (attrs != null) mWindowAttributes.copyFrom(attrs);
        mAttachInfo.mRootView = view;
        view.assignParent(this);
        mAdded = true;
        requestLayout();
    }

    public View getView() { return mView; }

    WindowManager.LayoutParams getWindowAttributes() { return mWindowAttributes; }

    void setLayoutParams(WindowManager.LayoutParams attrs) {
        mWindowAttributes.copyFrom(attrs);
        mWindowAttributesChanged = true;
        requestLayout();
    }

    /** framework-internal. Detaches the hierarchy when the window is removed. */
    public void die() {
        if (mRemoved) return;
        mRemoved = true;
        mChoreographer.removeCallbacks(Choreographer.CALLBACK_TRAVERSAL, mTraversalRunnable, null);
        mTraversalScheduled = false;
        mJoystick.cancel();
        if (mView != null) {
            if (mAttachInfo.mHasWindowFocus) {
                mAttachInfo.mHasWindowFocus = false;
                mView.dispatchWindowFocusChanged(false);
            }
            if (!mFirst) {
                mAttachInfo.mTreeObserver.dispatchOnWindowAttachedChange(false);
                mView.dispatchDetachedFromWindow();
            }
            mView.assignParent(null);
            mView = null;
        }
        mSurface = null;
        mCanvas = null;
    }

    // ---------------------------------------------------------------- ViewParent

    public void requestLayout() {
        mLayoutRequested = true;
        scheduleTraversals();
    }

    public boolean isLayoutRequested() { return mLayoutRequested; }

    boolean isInLayout() { return mInLayout; }

    boolean requestLayoutDuringLayout(View view) { return true; }

    public void requestTransparentRegion(View child) {}

    public void invalidateChild(View child, Rect dirty) { invalidateChildInParent(null, dirty); }

    public ViewParent invalidateChildInParent(int[] location, Rect dirty) {
        if (dirty == null) {
            invalidate();
            return null;
        }
        if (location != null) dirty.offset(location[0], location[1]);
        if (dirty.isEmpty()) return null;
        if (mDirty.isEmpty()) mDirty.set(dirty);
        else mDirty.union(dirty);
        scheduleTraversals();
        return null;
    }

    /** framework-internal. Redraws the whole window on the next frame. */
    public void invalidate() {
        mFullRedraw = true;
        scheduleTraversals();
    }

    void invalidateOnAnimation(View view) {
        view.invalidate(true);
    }

    public ViewParent getParent() { return null; }

    public void requestChildFocus(View child, View focused) {
        scheduleTraversals();
    }

    public void recomputeViewAttributes(View child) {}

    public void clearChildFocus(View child) {
        scheduleTraversals();
    }

    public boolean getChildVisibleRect(View child, Rect r, Point offset) {
        if (child != mView) throw new RuntimeException("child is not mine, honest!");
        if (offset != null) offset.offset(mWinX, mWinY);
        r.offset(mWinX, mWinY);
        return r.intersect(0, 0, Math.max(0, mWinX + mWidth), Math.max(0, mWinY + mHeight));
    }

    public View focusSearch(View focused, int direction) {
        if (!(mView instanceof ViewGroup)) return null;
        return FocusFinder.getInstance().findNextFocus((ViewGroup) mView, focused, direction);
    }

    public View keyboardNavigationClusterSearch(View currentCluster, int direction) { return null; }

    public void bringChildToFront(View child) {}

    public void focusableViewAvailable(View v) {
        if (mView != null) {
            if (!mView.hasFocus()) {
                if (!mAttachInfo.mInTouchMode) v.requestFocus();
            } else {
                View focused = mView.findFocus();
                if (focused instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) focused;
                    if (group.getDescendantFocusability() == ViewGroup.FOCUS_AFTER_DESCENDANTS
                            && isViewDescendantOf(v, focused)) {
                        v.requestFocus();
                    }
                }
            }
        }
    }

    private static boolean isViewDescendantOf(View child, View parent) {
        if (child == parent) return true;
        final ViewParent theParent = child.getParent();
        return (theParent instanceof ViewGroup) && isViewDescendantOf((View) theParent, parent);
    }

    public boolean showContextMenuForChild(View originalView) {
        return WindowManagerGlobal.getInstance().showContextMenuForChild(this, originalView, Float.NaN, Float.NaN);
    }

    public boolean showContextMenuForChild(View originalView, float x, float y) {
        return WindowManagerGlobal.getInstance().showContextMenuForChild(this, originalView, x, y);
    }

    public void createContextMenu(ContextMenu menu) {}

    public ActionMode startActionModeForChild(View originalView, ActionMode.Callback callback) { return null; }

    public ActionMode startActionModeForChild(View originalView, ActionMode.Callback callback, int type) { return null; }

    public void childDrawableStateChanged(View child) {}

    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {}

    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) { return false; }

    public boolean requestSendAccessibilityEvent(View child, AccessibilityEvent event) { return false; }

    public void childHasTransientStateChanged(View child, boolean hasTransientState) {}

    public void requestFitSystemWindows() {
        mLastInsets = null;
        requestLayout();
    }

    public ViewParent getParentForAccessibility() { return null; }

    public void notifySubtreeAccessibilityStateChanged(View child, View source, int changeType) {}

    public boolean canResolveLayoutDirection() { return true; }

    public boolean isLayoutDirectionResolved() { return true; }

    public int getLayoutDirection() { return View.LAYOUT_DIRECTION_LTR; }

    public boolean canResolveTextDirection() { return true; }

    public boolean isTextDirectionResolved() { return true; }

    public int getTextDirection() { return View.TEXT_DIRECTION_FIRST_STRONG; }

    public boolean canResolveTextAlignment() { return true; }

    public boolean isTextAlignmentResolved() { return true; }

    public int getTextAlignment() { return View.TEXT_ALIGNMENT_GRAVITY; }

    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) { return false; }

    public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {}

    public void onStopNestedScroll(View target) {}

    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {}

    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {}

    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) { return false; }

    public boolean onNestedPreFling(View target, float velocityX, float velocityY) { return false; }

    public boolean onNestedPrePerformAccessibilityAction(View target, int action, Bundle args) { return false; }

    WindowInsets getWindowInsets() {
        if (mLastInsets == null) mLastInsets = new WindowInsets(new Rect());
        return mLastInsets;
    }

    // ---------------------------------------------------------------- animation runnables

    void postOnAnimation(final Runnable action, long delayMillis) {
        mChoreographer.postCallbackDelayed(Choreographer.CALLBACK_ANIMATION, action, null, delayMillis);
    }

    void removeOnAnimation(Runnable action) {
        mChoreographer.removeCallbacks(Choreographer.CALLBACK_ANIMATION, action, null);
    }

    // ---------------------------------------------------------------- traversals

    void scheduleTraversals() {
        if (mTraversalScheduled || mRemoved) return;
        mTraversalScheduled = true;
        mChoreographer.postCallback(Choreographer.CALLBACK_TRAVERSAL, mTraversalRunnable, null);
    }

    void doTraversal() {
        if (!mTraversalScheduled) return;
        mTraversalScheduled = false;
        try {
            performTraversals();
        } catch (RuntimeException e) {
            Log.e(TAG, "Exception in traversal", e);
            throw e;
        }
    }

    private static int getRootMeasureSpec(int windowSize, int rootDimension) {
        switch (rootDimension) {
            case ViewGroup.LayoutParams.MATCH_PARENT:
                return View.MeasureSpec.makeMeasureSpec(windowSize, View.MeasureSpec.EXACTLY);
            case ViewGroup.LayoutParams.WRAP_CONTENT:
                return View.MeasureSpec.makeMeasureSpec(windowSize, View.MeasureSpec.AT_MOST);
            default:
                return View.MeasureSpec.makeMeasureSpec(rootDimension, View.MeasureSpec.EXACTLY);
        }
    }

    private void measureHierarchy(View host, WindowManager.LayoutParams lp, int desiredWindowWidth,
            int desiredWindowHeight) {
        boolean goodMeasure = false;
        if (lp.width == ViewGroup.LayoutParams.WRAP_CONTENT) {
            // AOSP: try the preferred dialog width (config_prefDialogWidth, 320dp) before the full width
            int baseSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 320,
                    mContext.getResources().getDisplayMetrics());
            if (baseSize != 0 && desiredWindowWidth > baseSize) {
                int childWidthMeasureSpec = getRootMeasureSpec(baseSize, lp.width);
                int childHeightMeasureSpec = getRootMeasureSpec(desiredWindowHeight, lp.height);
                host.measure(childWidthMeasureSpec, childHeightMeasureSpec);
                if ((host.getMeasuredWidthAndState() & View.MEASURED_STATE_TOO_SMALL) == 0) {
                    goodMeasure = true;
                } else {
                    baseSize = (baseSize + desiredWindowWidth) / 2;
                    childWidthMeasureSpec = getRootMeasureSpec(baseSize, lp.width);
                    host.measure(childWidthMeasureSpec, childHeightMeasureSpec);
                    if ((host.getMeasuredWidthAndState() & View.MEASURED_STATE_TOO_SMALL) == 0) goodMeasure = true;
                }
            }
        }
        if (!goodMeasure) {
            int childWidthMeasureSpec = getRootMeasureSpec(desiredWindowWidth, lp.width);
            int childHeightMeasureSpec = getRootMeasureSpec(desiredWindowHeight, lp.height);
            host.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        }
    }

    private void performTraversals() {
        final View host = mView;
        if (host == null || !mAdded || mRemoved) return;
        final WindowManager.LayoutParams lp = mWindowAttributes;
        Display display = Display.defaultDisplay();
        final int screenW = display.getWidth();
        final int screenH = display.getHeight();
        if (screenW <= 0 || screenH <= 0) return;

        if (mFirst) {
            mAttachInfo.mWindowVisibility = View.VISIBLE;
            mAttachInfo.mInTouchMode = sInTouchMode;
            host.dispatchAttachedToWindow(mAttachInfo, 0);
            mAttachInfo.mTreeObserver.dispatchOnWindowAttachedChange(true);
            host.dispatchApplyWindowInsets(getWindowInsets());
            mLayoutRequested = true;
        }

        boolean layoutDone = false;
        if (mLayoutRequested || mWindowAttributesChanged) {
            mLayoutRequested = false;
            measureHierarchy(host, lp, screenW, screenH);
            int w = lp.width == ViewGroup.LayoutParams.MATCH_PARENT ? screenW : host.getMeasuredWidth();
            int h = lp.height == ViewGroup.LayoutParams.MATCH_PARENT ? screenH : host.getMeasuredHeight();
            if (lp.width >= 0) w = lp.width;
            if (lp.height >= 0) h = lp.height;
            if (w != host.getMeasuredWidth() || h != host.getMeasuredHeight()) {
                host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            }
            positionWindow(lp, w, h, screenW, screenH);
            if (w != mWidth || h != mHeight || mSurface == null) {
                mWidth = Math.max(1, w);
                mHeight = Math.max(1, h);
                mSurface = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
                mCanvas = new Canvas(mSurface);
                mFullRedraw = true;
            }
            mWindowAttributesChanged = false;
            mInLayout = true;
            try {
                host.layout(0, 0, host.getMeasuredWidth(), host.getMeasuredHeight());
            } finally {
                mInLayout = false;
            }
            layoutDone = true;
            mAttachInfo.mTreeObserver.dispatchOnGlobalLayout();
        }

        if (mFirst) {
            mFirst = false;
            WindowManagerGlobal.getInstance().updateFocusedWindow();
            if (!mAttachInfo.mInTouchMode && !host.hasFocus()) host.restoreDefaultFocus();
        }

        if (mAttachInfo.mViewScrollChanged) {
            mAttachInfo.mViewScrollChanged = false;
            mAttachInfo.mTreeObserver.dispatchOnScrollChanged();
        }

        boolean cancelDraw = mAttachInfo.mTreeObserver.dispatchOnPreDraw();
        if (!cancelDraw) {
            performDraw();
        } else {
            scheduleTraversals();
        }
        if (layoutDone && mLayoutRequested) scheduleTraversals();
    }

    private void positionWindow(WindowManager.LayoutParams lp, int w, int h, int screenW, int screenH) {
        int gravity = lp.gravity;
        int x = lp.x;
        int y = lp.y;
        if (gravity == 0 || (w >= screenW && h >= screenH)) {
            mWinX = w >= screenW ? 0 : x;
            mWinY = h >= screenH ? 0 : y;
            return;
        }
        Rect container = new Rect(0, 0, screenW, screenH);
        Rect out = new Rect();
        Gravity.apply(gravity, w, h, container, (int) (lp.horizontalMargin * screenW) + x,
                (int) (lp.verticalMargin * screenH) + y, out);
        mWinX = out.left;
        mWinY = out.top;
    }

    private void performDraw() {
        if (mSurface == null || mView == null) return;
        if (mFullRedraw) {
            mDirty.set(0, 0, mWidth, mHeight);
            mFullRedraw = false;
        }
        if (!mDirty.intersect(0, 0, mWidth, mHeight)) {
            mDirty.setEmpty();
            return;
        }
        final Canvas canvas = mCanvas;
        final int save = canvas.save();
        canvas.clipRect(mDirty);
        if (!isOpaque()) canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        mAttachInfo.mDrawingTime = SystemClock.uptimeMillis();
        mAttachInfo.mTreeObserver.dispatchOnDraw();
        mView.mPrivateFlags |= View.PFLAG_DRAWN;
        mView.draw(canvas);
        canvas.restoreToCount(save);
        mDirty.setEmpty();
        mDrawnOnce = true;
        WindowManagerGlobal.getInstance().windowDrawn(this);
        mAttachInfo.mTreeObserver.dispatchOnFrameCommit();
    }

    boolean isOpaque() {
        if (mWindowAttributes.format == PixelFormat.OPAQUE) {
            return mView == null || mView.getBackground() == null || mView.isOpaque();
        }
        return false;
    }

    /** framework-internal. Display size changed: relayout and redraw everything. */
    void onDisplayChanged() {
        mFullRedraw = true;
        mLayoutRequested = true;
        mWindowAttributesChanged = true;
        if (mView != null) forceLayoutTree(mView);
        scheduleTraversals();
    }

    private static void forceLayoutTree(View v) {
        v.forceLayout();
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) forceLayoutTree(g.getChildAt(i));
        }
    }

    // ---------------------------------------------------------------- window focus and touch mode

    void windowFocusChanged(boolean hasFocus) {
        if (mView == null || mAttachInfo.mHasWindowFocus == hasFocus) return;
        mAttachInfo.mHasWindowFocus = hasFocus;
        if (hasFocus) mAttachInfo.mInTouchMode = sInTouchMode;
        mView.dispatchWindowFocusChanged(hasFocus);
        mAttachInfo.mTreeObserver.dispatchOnWindowFocusChange(hasFocus);
        if (hasFocus && !mAttachInfo.mInTouchMode && !mView.hasFocus()) mView.restoreDefaultFocus();
        invalidate();
    }

    boolean ensureTouchMode(boolean inTouchMode) {
        if (mAttachInfo.mInTouchMode == inTouchMode && sInTouchMode == inTouchMode) return false;
        sInTouchMode = inTouchMode;
        WindowManagerGlobal.getInstance().setTouchModeAll(inTouchMode);
        return inTouchMode ? enterTouchMode() : leaveTouchMode();
    }

    void applyTouchMode(boolean inTouchMode) {
        if (mAttachInfo.mInTouchMode == inTouchMode) return;
        mAttachInfo.mInTouchMode = inTouchMode;
        mAttachInfo.mTreeObserver.dispatchOnTouchModeChanged(inTouchMode);
        invalidate();
        if (mView != null) refreshTree(mView);
    }

    private static void refreshTree(View v) {
        v.refreshDrawableState();
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) refreshTree(g.getChildAt(i));
        }
    }

    private boolean enterTouchMode() {
        if (mView != null && mView.hasFocus()) {
            final View focused = mView.findFocus();
            if (focused != null && !focused.isFocusableInTouchMode()) {
                final ViewGroup ancestorToTakeFocus = findAncestorToTakeFocusInTouchMode(focused);
                if (ancestorToTakeFocus != null) return ancestorToTakeFocus.requestFocus();
                focused.clearFocusInternal(null, true, false);
                return true;
            }
        }
        return false;
    }

    private static ViewGroup findAncestorToTakeFocusInTouchMode(View focused) {
        ViewParent parent = focused.getParent();
        while (parent instanceof ViewGroup) {
            final ViewGroup vgParent = (ViewGroup) parent;
            if (vgParent.getDescendantFocusability() == ViewGroup.FOCUS_AFTER_DESCENDANTS
                    && vgParent.isFocusableInTouchMode()) {
                return vgParent;
            }
            if (vgParent.isRootNamespace()) return null;
            parent = vgParent.getParent();
        }
        return null;
    }

    private boolean leaveTouchMode() {
        if (mView != null) {
            if (mView.hasFocus()) {
                View focusedView = mView.findFocus();
                if (!(focusedView instanceof ViewGroup)) return false;
                if (((ViewGroup) focusedView).getDescendantFocusability() != ViewGroup.FOCUS_AFTER_DESCENDANTS) {
                    return false;
                }
            }
            final View focused = focusSearch(null, View.FOCUS_DOWN);
            if (focused != null) return focused.requestFocus(View.FOCUS_DOWN);
        }
        return false;
    }

    private boolean checkForLeavingTouchModeAndConsume(KeyEvent event) {
        if (!mAttachInfo.mInTouchMode) return false;
        final int action = event.getAction();
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_MULTIPLE) return false;
        if ((event.getFlags() & KeyEvent.FLAG_KEEP_TOUCH_MODE) != 0) return false;
        if (isNavigationKey(event)) return ensureTouchMode(false);
        if (isTypingKey(event)) {
            ensureTouchMode(false);
            return false;
        }
        return false;
    }

    private static boolean isNavigationKey(KeyEvent keyEvent) {
        switch (keyEvent.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_MOVE_HOME:
            case KeyEvent.KEYCODE_MOVE_END:
            case KeyEvent.KEYCODE_TAB:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_ENTER:
                return true;
        }
        return false;
    }

    private static boolean isTypingKey(KeyEvent keyEvent) { return keyEvent.getUnicodeChar() > 0; }

    // ---------------------------------------------------------------- input

    /** framework-internal. Touch in window coordinates. */
    boolean dispatchPointer(MotionEvent event) {
        if (mView == null) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            ensureTouchMode(event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN));
        }
        return mView.dispatchPointerEvent(event);
    }

    /** framework-internal. Joystick and other non-pointer motion; unhandled sticks move focus. */
    boolean dispatchGenericMotion(MotionEvent event) {
        if (mView == null) return false;
        if (mView.dispatchGenericMotionEvent(event)) {
            mJoystick.cancel();
            return true;
        }
        if (event.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK)) {
            mJoystick.process(event);
            return true;
        }
        return false;
    }

    /** framework-internal. Full key pipeline for this window (pre-IME, view tree, unhandled, focus moves). */
    boolean dispatchKey(KeyEvent event) {
        if (mView == null) return false;
        if (checkForLeavingTouchModeAndConsume(event)) return true;
        if (mView.dispatchKeyEventPreIme(event)) return true;
        if (mView.dispatchKeyEvent(event)) return true;
        if (mView.dispatchUnhandledKeyEvent(event)) return true;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.isCtrlPressed() && event.getRepeatCount() == 0
                && !KeyEvent.isModifierKey(event.getKeyCode())) {
            if (mView.dispatchKeyShortcutEvent(event)) return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) return performFocusNavigation(event);
        return false;
    }

    private boolean performFocusNavigation(KeyEvent event) {
        int direction = 0;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (event.hasNoModifiers()) direction = View.FOCUS_LEFT;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.hasNoModifiers()) direction = View.FOCUS_RIGHT;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (event.hasNoModifiers()) direction = View.FOCUS_UP;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (event.hasNoModifiers()) direction = View.FOCUS_DOWN;
                break;
            case KeyEvent.KEYCODE_TAB:
                if (event.hasNoModifiers()) direction = View.FOCUS_FORWARD;
                else if (event.hasModifiers(KeyEvent.META_SHIFT_ON)) direction = View.FOCUS_BACKWARD;
                break;
        }
        if (direction != 0) {
            View focused = mView.findFocus();
            if (focused != null) {
                View v = focused.focusSearch(direction);
                if (v != null && v != focused) {
                    focused.getFocusedRect(mTempRect);
                    if (mView instanceof ViewGroup) {
                        ((ViewGroup) mView).offsetDescendantRectToMyCoords(focused, mTempRect);
                        ((ViewGroup) mView).offsetRectIntoDescendantCoords(v, mTempRect);
                    }
                    if (v.requestFocus(direction, mTempRect)) {
                        focused.playSoundEffect(SoundEffectConstants.getContantForFocusDirection(direction));
                        return true;
                    }
                }
                if (mView.dispatchUnhandledMove(focused, direction)) return true;
            } else {
                if (mView.restoreDefaultFocus()) return true;
            }
        }
        return false;
    }

    /**
     * Turns joystick motion into D-pad key events when no view consumed it
     * (AOSP ViewRootImpl.SyntheticJoystickHandler), with key repeat.
     */
    private final class SyntheticJoystickHandler {
        private int mLastXKey;
        private int mLastYKey;
        private KeyEvent mRepeatEvent;
        private final Runnable mRepeat = new Runnable() {
            public void run() {
                KeyEvent oldEvent = mRepeatEvent;
                if (oldEvent == null) return;
                KeyEvent e = new KeyEvent(oldEvent.getDownTime(), SystemClock.uptimeMillis(), oldEvent.getAction(),
                        oldEvent.getKeyCode(), oldEvent.getRepeatCount() + 1, oldEvent.getMetaState(),
                        oldEvent.getDeviceId(), oldEvent.getScanCode(), oldEvent.getFlags(), oldEvent.getSource());
                mRepeatEvent = e;
                WindowManagerGlobal.getInstance().dispatchKeyToWindow(ViewRootImpl.this, e);
                mHandler.postDelayed(this, ViewConfiguration.getKeyRepeatDelay() * 3);
            }
        };

        void process(MotionEvent event) {
            float x = event.getAxisValue(MotionEvent.AXIS_HAT_X);
            if (x == 0) x = event.getAxisValue(MotionEvent.AXIS_X);
            float y = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
            if (y == 0) y = event.getAxisValue(MotionEvent.AXIS_Y);
            long time = event.getEventTime();
            int xKey = x >= 0.5f ? KeyEvent.KEYCODE_DPAD_RIGHT : x <= -0.5f ? KeyEvent.KEYCODE_DPAD_LEFT : 0;
            int yKey = y >= 0.5f ? KeyEvent.KEYCODE_DPAD_DOWN : y <= -0.5f ? KeyEvent.KEYCODE_DPAD_UP : 0;
            mLastXKey = update(mLastXKey, xKey, time, event);
            mLastYKey = update(mLastYKey, yKey, time, event);
        }

        private int update(int lastKey, int key, long time, MotionEvent event) {
            if (key == lastKey) return lastKey;
            if (lastKey != 0) {
                stopRepeat();
                send(new KeyEvent(time, time, KeyEvent.ACTION_UP, lastKey, 0, event.getMetaState(),
                        event.getDeviceId(), 0, KeyEvent.FLAG_FALLBACK, event.getSource()));
            }
            if (key != 0) {
                KeyEvent down = new KeyEvent(time, time, KeyEvent.ACTION_DOWN, key, 0, event.getMetaState(),
                        event.getDeviceId(), 0, KeyEvent.FLAG_FALLBACK, event.getSource());
                send(down);
                mRepeatEvent = down;
                mHandler.removeCallbacks(mRepeat);
                mHandler.postDelayed(mRepeat, ViewConfiguration.getKeyRepeatTimeout());
            }
            return key;
        }

        private void send(KeyEvent e) { WindowManagerGlobal.getInstance().dispatchKeyToWindow(ViewRootImpl.this, e); }

        private void stopRepeat() {
            mRepeatEvent = null;
            mHandler.removeCallbacks(mRepeat);
        }

        void cancel() {
            stopRepeat();
            long now = SystemClock.uptimeMillis();
            if (mLastXKey != 0) {
                send(new KeyEvent(now, now, KeyEvent.ACTION_UP, mLastXKey, 0, 0, InputDevice.ID_GAMEPAD, 0,
                        KeyEvent.FLAG_FALLBACK | KeyEvent.FLAG_CANCELED, InputDevice.SOURCE_JOYSTICK));
                mLastXKey = 0;
            }
            if (mLastYKey != 0) {
                send(new KeyEvent(now, now, KeyEvent.ACTION_UP, mLastYKey, 0, 0, InputDevice.ID_GAMEPAD, 0,
                        KeyEvent.FLAG_FALLBACK | KeyEvent.FLAG_CANCELED, InputDevice.SOURCE_JOYSTICK));
                mLastYKey = 0;
            }
        }
    }
}
