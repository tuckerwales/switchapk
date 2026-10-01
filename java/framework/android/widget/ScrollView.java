package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

/**
 * A frame that scrolls its single child vertically (AOSP ScrollView).
 * Dragging follows the finger. A fling continues on {@link OverScroller}.
 */
public class ScrollView extends FrameLayout {
    private static final int ANIMATED_SCROLL_GAP = 250;
    private static final float MAX_SCROLL_FACTOR = 0.5f;
    private static final int INVALID_POINTER = -1;
    private static final int[] SCROLL_ATTRS = {android.R.attr.fillViewport};

    private final Rect mTempRect = new Rect();
    private OverScroller mScroller;
    private EdgeEffect mEdgeGlowTop;
    private EdgeEffect mEdgeGlowBottom;
    private int mLastMotionY;
    private boolean mIsBeingDragged;
    private VelocityTracker mVelocityTracker;
    private boolean mFillViewport;
    private boolean mSmoothScrollingEnabled = true;
    private int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;
    private int mOverscrollDistance;
    private int mOverflingDistance;
    private float mVerticalScrollFactor;
    private int mActivePointerId = INVALID_POINTER;
    private long mLastScroll;
    private View mChildToScrollTo;
    private boolean mIsLayoutDirty = true;
    private SavedState mSavedState;

    public ScrollView(Context context) { this(context, null); }

    public ScrollView(Context context, AttributeSet attrs) { this(context, attrs, android.R.attr.scrollViewStyle); }

    public ScrollView(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public ScrollView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initScrollView();
        TypedArray a = context.obtainStyledAttributes(attrs, SCROLL_ATTRS, defStyleAttr, defStyleRes);
        setFillViewport(a.getBoolean(0, false));
        a.recycle();
    }

    private void initScrollView() {
        mScroller = new OverScroller(getContext());
        setFocusable(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setWillNotDraw(false);
        ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mOverscrollDistance = configuration.getScaledOverscrollDistance();
        mOverflingDistance = configuration.getScaledOverflingDistance();
        mVerticalScrollFactor = configuration.getScaledVerticalScrollFactor();
        mEdgeGlowTop = new EdgeEffect(getContext());
        mEdgeGlowBottom = new EdgeEffect(getContext());
    }

    @Override
    public boolean shouldDelayChildPressedState() { return true; }

    @Override
    protected float getTopFadingEdgeStrength() {
        if (getChildCount() == 0) return 0.0f;
        int length = getVerticalFadingEdgeLength();
        if (length == 0 || mScrollY < length) return length == 0 ? 0 : mScrollY / (float) length;
        return 1.0f;
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        if (getChildCount() == 0) return 0.0f;
        int length = getVerticalFadingEdgeLength();
        if (length == 0) return 0;
        int span = getChildAt(0).getBottom() - mScrollY - (getHeight() - getPaddingBottom());
        if (span < length) return span / (float) length;
        return 1.0f;
    }

    public void setEdgeEffectColor(int color) {
        setTopEdgeEffectColor(color);
        setBottomEdgeEffectColor(color);
    }

    public void setTopEdgeEffectColor(int color) { mEdgeGlowTop.setColor(color); }

    public void setBottomEdgeEffectColor(int color) { mEdgeGlowBottom.setColor(color); }

    public int getTopEdgeEffectColor() { return mEdgeGlowTop.getColor(); }

    public int getBottomEdgeEffectColor() { return mEdgeGlowBottom.getColor(); }

    public int getMaxScrollAmount() { return (int) (MAX_SCROLL_FACTOR * (mBottom - mTop)); }

    @Override
    public void addView(View child) {
        if (getChildCount() > 0) throw new IllegalStateException("ScrollView can host only one direct child");
        super.addView(child);
    }

    @Override
    public void addView(View child, int index) {
        if (getChildCount() > 0) throw new IllegalStateException("ScrollView can host only one direct child");
        super.addView(child, index);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        if (getChildCount() > 0) throw new IllegalStateException("ScrollView can host only one direct child");
        super.addView(child, params);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() > 0) throw new IllegalStateException("ScrollView can host only one direct child");
        super.addView(child, index, params);
    }

    public boolean isFillViewport() { return mFillViewport; }

    public void setFillViewport(boolean fillViewport) {
        if (fillViewport != mFillViewport) {
            mFillViewport = fillViewport;
            requestLayout();
        }
    }

    public boolean isSmoothScrollingEnabled() { return mSmoothScrollingEnabled; }

    public void setSmoothScrollingEnabled(boolean smoothScrollingEnabled) {
        mSmoothScrollingEnabled = smoothScrollingEnabled;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!mFillViewport) return;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) return;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
            int widthPadding = getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin;
            int heightPadding = getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin;
            int desiredHeight = getMeasuredHeight() - heightPadding;
            if (child.getMeasuredHeight() < desiredHeight) {
                int childWidth = getChildMeasureSpec(widthMeasureSpec, widthPadding, lp.width);
                int childHeight = MeasureSpec.makeMeasureSpec(desiredHeight, MeasureSpec.EXACTLY);
                child.measure(childWidth, childHeight);
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return super.dispatchKeyEvent(event) || executeKeyEvent(event);
    }

    public boolean executeKeyEvent(KeyEvent event) {
        if (event == null) return false;
        mTempRect.setEmpty();
        if (!canScroll()) return false;
        boolean handled = false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    handled = event.isAltPressed() ? fullScroll(FOCUS_UP) : arrowScroll(FOCUS_UP);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    handled = event.isAltPressed() ? fullScroll(FOCUS_DOWN) : arrowScroll(FOCUS_DOWN);
                    break;
                case KeyEvent.KEYCODE_SPACE:
                    pageScroll(event.isShiftPressed() ? FOCUS_UP : FOCUS_DOWN);
                    handled = true;
                    break;
                case KeyEvent.KEYCODE_PAGE_UP:
                    pageScroll(FOCUS_UP);
                    handled = true;
                    break;
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    pageScroll(FOCUS_DOWN);
                    handled = true;
                    break;
                default:
                    break;
            }
        }
        return handled;
    }

    private boolean canScroll() {
        if (getChildCount() == 0) return false;
        View child = getChildAt(0);
        return child.getHeight() > getHeight() - mPaddingTop - mPaddingBottom;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) recycleVelocityTracker();
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE && mIsBeingDragged) return true;
        if (super.onInterceptTouchEvent(ev)) return true;
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                int y = (int) ev.getY();
                if (!inChild(ev.getX(), y)) {
                    mIsBeingDragged = false;
                    recycleVelocityTracker();
                    break;
                }
                mLastMotionY = y;
                mActivePointerId = ev.getPointerId(0);
                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(ev);
                mIsBeingDragged = !mScroller.isFinished();
                if (mIsBeingDragged && getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                int index = ev.findPointerIndex(mActivePointerId);
                if (index == -1) break;
                int y = (int) ev.getY(index);
                if (Math.abs(y - mLastMotionY) > mTouchSlop && (getNestedScrollAxes() & SCROLL_AXIS_VERTICAL) == 0) {
                    mIsBeingDragged = true;
                    mLastMotionY = y;
                    initVelocityTrackerIfNotExists();
                    mVelocityTracker.addMovement(ev);
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                recycleVelocityTracker();
                if (mScroller.springBack(mScrollX, mScrollY, 0, 0, 0, getScrollRange())) postInvalidateOnAnimation();
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
            default:
                break;
        }
        return mIsBeingDragged;
    }

    private boolean inChild(float x, float y) {
        if (getChildCount() == 0) return false;
        View child = getChildAt(0);
        return y >= child.getTop() - mScrollY && y < child.getBottom() - mScrollY && x >= child.getLeft()
                && x < child.getRight();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (getChildCount() == 0) return false;
        initVelocityTrackerIfNotExists();
        MotionEvent vtev = MotionEvent.obtain(ev);
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
                mIsBeingDragged = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            }
            mLastMotionY = (int) ev.getY();
            mActivePointerId = ev.getPointerId(0);
        }
        mVelocityTracker.addMovement(vtev);
        vtev.recycle();
        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                int index = ev.findPointerIndex(mActivePointerId);
                if (index == -1) break;
                int y = (int) ev.getY(index);
                int deltaY = mLastMotionY - y;
                if (!mIsBeingDragged && Math.abs(deltaY) > mTouchSlop) {
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    mIsBeingDragged = true;
                    if (deltaY > 0) deltaY -= mTouchSlop;
                    else deltaY += mTouchSlop;
                }
                if (mIsBeingDragged) {
                    mLastMotionY = y;
                    int oldY = mScrollY;
                    int range = getScrollRange();
                    overScrollBy(0, deltaY, 0, mScrollY, 0, range, 0, mOverscrollDistance, true);
                    int unconsumed = deltaY - (mScrollY - oldY);
                    if (unconsumed != 0) pullEdge(unconsumed, ev.getX(index));
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (mIsBeingDragged) {
                    VelocityTracker tracker = mVelocityTracker;
                    tracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialVelocity = (int) tracker.getYVelocity(mActivePointerId);
                    if (Math.abs(initialVelocity) > mMinimumVelocity) fling(-initialVelocity);
                    else if (mScroller.springBack(mScrollX, mScrollY, 0, 0, 0, getScrollRange())) {
                        postInvalidateOnAnimation();
                    }
                    mActivePointerId = INVALID_POINTER;
                    endDrag();
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged && getChildCount() > 0) {
                    if (mScroller.springBack(mScrollX, mScrollY, 0, 0, 0, getScrollRange())) {
                        postInvalidateOnAnimation();
                    }
                }
                mActivePointerId = INVALID_POINTER;
                endDrag();
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = ev.getActionIndex();
                mLastMotionY = (int) ev.getY(index);
                mActivePointerId = ev.getPointerId(index);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                mLastMotionY = (int) ev.getY(ev.findPointerIndex(mActivePointerId));
                break;
            default:
                break;
        }
        return true;
    }

    private void pullEdge(int unconsumed, float x) {
        int overscrollMode = getOverScrollMode();
        boolean canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS
                || (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && getScrollRange() > 0);
        if (!canOverscroll || getHeight() == 0) return;
        float displacement = x / getWidth();
        if (unconsumed < 0) {
            mEdgeGlowTop.onPull((float) -unconsumed / getHeight(), displacement);
            if (!mEdgeGlowBottom.isFinished()) mEdgeGlowBottom.onRelease();
        } else {
            mEdgeGlowBottom.onPull((float) unconsumed / getHeight(), 1f - displacement);
            if (!mEdgeGlowTop.isFinished()) mEdgeGlowTop.onRelease();
        }
        if (!mEdgeGlowTop.isFinished() || !mEdgeGlowBottom.isFinished()) postInvalidateOnAnimation();
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        int index = ev.getActionIndex();
        if (ev.getPointerId(index) == mActivePointerId) {
            int newIndex = index == 0 ? 1 : 0;
            mLastMotionY = (int) ev.getY(newIndex);
            mActivePointerId = ev.getPointerId(newIndex);
            if (mVelocityTracker != null) mVelocityTracker.clear();
        }
    }

    private void endDrag() {
        mIsBeingDragged = false;
        recycleVelocityTracker();
        mEdgeGlowTop.onRelease();
        mEdgeGlowBottom.onRelease();
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & android.view.InputDevice.SOURCE_CLASS_POINTER) != 0
                && event.getAction() == MotionEvent.ACTION_SCROLL) {
            float vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (vscroll != 0) {
                int delta = (int) (vscroll * mVerticalScrollFactor);
                int range = getScrollRange();
                int oldScrollY = mScrollY;
                int newScrollY = oldScrollY - delta;
                if (newScrollY < 0) newScrollY = 0;
                else if (newScrollY > range) newScrollY = range;
                if (newScrollY != oldScrollY) {
                    super.scrollTo(mScrollX, newScrollY);
                    return true;
                }
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        if (!mScroller.isFinished()) {
            int oldX = mScrollX;
            int oldY = mScrollY;
            mScrollX = scrollX;
            mScrollY = scrollY;
            onScrollChanged(mScrollX, mScrollY, oldX, oldY);
            if (clampedY) mScroller.springBack(mScrollX, mScrollY, 0, 0, 0, getScrollRange());
            awakenScrollBars();
        } else {
            super.scrollTo(scrollX, scrollY);
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() { return ScrollView.class.getName(); }

    public boolean pageScroll(int direction) {
        boolean down = direction == FOCUS_DOWN;
        int height = getHeight();
        if (down) {
            mTempRect.top = getScrollY() + height;
            if (getChildCount() > 0) {
                View child = getChildAt(0);
                if (mTempRect.top + height > child.getBottom()) mTempRect.top = child.getBottom() - height;
            }
        } else {
            mTempRect.top = getScrollY() - height;
            if (mTempRect.top < 0) mTempRect.top = 0;
        }
        mTempRect.bottom = mTempRect.top + height;
        return scrollAndFocus(direction, mTempRect.top, mTempRect.bottom);
    }

    public boolean fullScroll(int direction) {
        boolean down = direction == FOCUS_DOWN;
        int height = getHeight();
        mTempRect.top = 0;
        mTempRect.bottom = height;
        if (down && getChildCount() > 0) {
            View child = getChildAt(0);
            mTempRect.bottom = child.getBottom() + getPaddingBottom();
            mTempRect.top = mTempRect.bottom - height;
        }
        return scrollAndFocus(direction, mTempRect.top, mTempRect.bottom);
    }

    private boolean scrollAndFocus(int direction, int top, int bottom) {
        boolean handled = true;
        int height = getHeight();
        int containerTop = getScrollY();
        int containerBottom = containerTop + height;
        boolean up = direction == FOCUS_UP;
        View newFocused = findFocusableViewInBounds(up, top, bottom);
        if (newFocused == null) newFocused = this;
        if (top >= containerTop && bottom <= containerBottom) {
            handled = false;
        } else {
            int delta = up ? (top - containerTop) : (bottom - containerBottom);
            doScrollY(delta);
        }
        if (newFocused != findFocus()) newFocused.requestFocus(direction);
        return handled;
    }

    private View findFocusableViewInBounds(boolean topFocus, int top, int bottom) {
        java.util.ArrayList<View> focusables = getFocusables(FOCUS_FORWARD);
        View focusCandidate = null;
        boolean foundFullyContained = false;
        int count = focusables.size();
        for (int i = 0; i < count; i++) {
            View view = focusables.get(i);
            int viewTop = view.getTop();
            int viewBottom = view.getBottom();
            if (top < viewBottom && viewTop < bottom) {
                boolean viewIsFullyContained = top < viewTop && viewBottom < bottom;
                if (focusCandidate == null) {
                    focusCandidate = view;
                    foundFullyContained = viewIsFullyContained;
                } else {
                    boolean viewIsCloser = (topFocus && viewTop < focusCandidate.getTop())
                            || (!topFocus && viewBottom > focusCandidate.getBottom());
                    if (foundFullyContained) {
                        if (viewIsFullyContained && viewIsCloser) focusCandidate = view;
                    } else if (viewIsFullyContained) {
                        focusCandidate = view;
                        foundFullyContained = true;
                    } else if (viewIsCloser) {
                        focusCandidate = view;
                    }
                }
            }
        }
        return focusCandidate;
    }

    public boolean arrowScroll(int direction) {
        View currentFocused = findFocus();
        if (currentFocused == this) currentFocused = null;
        View nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, direction);
        int maxJump = getMaxScrollAmount();
        if (nextFocused != null && isWithinDeltaOfScreen(nextFocused, maxJump)) {
            nextFocused.getDrawingRect(mTempRect);
            offsetDescendantRectToMyCoords(nextFocused, mTempRect);
            doScrollY(computeScrollDeltaToGetChildRectOnScreen(mTempRect));
            nextFocused.requestFocus(direction);
        } else {
            int scrollDelta = maxJump;
            if (direction == FOCUS_UP && getScrollY() < scrollDelta) scrollDelta = getScrollY();
            else if (direction == FOCUS_DOWN && getChildCount() > 0) {
                int daBottom = getChildAt(0).getBottom();
                int screenBottom = getScrollY() + getHeight() - mPaddingBottom;
                if (daBottom - screenBottom < maxJump) scrollDelta = daBottom - screenBottom;
            }
            if (scrollDelta == 0) return false;
            doScrollY(direction == FOCUS_DOWN ? scrollDelta : -scrollDelta);
        }
        return true;
    }

    private boolean isWithinDeltaOfScreen(View descendant, int delta) {
        descendant.getDrawingRect(mTempRect);
        offsetDescendantRectToMyCoords(descendant, mTempRect);
        return mTempRect.bottom + delta >= getScrollY() && mTempRect.top - delta <= getScrollY() + getHeight();
    }

    private void doScrollY(int delta) {
        if (delta == 0) return;
        if (mSmoothScrollingEnabled) smoothScrollBy(0, delta);
        else scrollBy(0, delta);
    }

    public final void smoothScrollBy(int dx, int dy) {
        if (getChildCount() == 0) return;
        long now = SystemClock.uptimeMillis();
        if (now - mLastScroll > ANIMATED_SCROLL_GAP) {
            int height = getHeight() - mPaddingBottom - mPaddingTop;
            int bottom = getChildAt(0).getHeight();
            int maxY = Math.max(0, bottom - height);
            int scrollY = mScrollY;
            dy = Math.max(0, Math.min(scrollY + dy, maxY)) - scrollY;
            mScroller.startScroll(mScrollX, scrollY, 0, dy);
            postInvalidateOnAnimation();
        } else {
            if (!mScroller.isFinished()) mScroller.abortAnimation();
            scrollBy(dx, dy);
        }
        mLastScroll = now;
    }

    public final void smoothScrollTo(int x, int y) { smoothScrollBy(x - mScrollX, y - mScrollY); }

    @Override
    protected int computeVerticalScrollRange() {
        int contentHeight = getHeight() - mPaddingBottom - mPaddingTop;
        if (getChildCount() == 0) return contentHeight;
        int scrollRange = getChildAt(0).getBottom();
        int scrollY = mScrollY;
        int overscrollBottom = Math.max(0, scrollRange - contentHeight);
        if (scrollY < 0) scrollRange -= scrollY;
        else if (scrollY > overscrollBottom) scrollRange += scrollY - overscrollBottom;
        return scrollRange;
    }

    @Override
    protected int computeVerticalScrollOffset() { return Math.max(0, super.computeVerticalScrollOffset()); }

    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, mPaddingLeft + mPaddingRight, lp.width);
        int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin + widthUsed, lp.width);
        int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                lp.topMargin + lp.bottomMargin, MeasureSpec.UNSPECIFIED);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    public void computeScroll() {
        if (!mScroller.computeScrollOffset()) return;
        int oldX = mScrollX;
        int oldY = mScrollY;
        int x = mScroller.getCurrX();
        int y = mScroller.getCurrY();
        if (oldX != x || oldY != y) {
            int range = getScrollRange();
            int overscrollMode = getOverScrollMode();
            boolean canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS
                    || (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0);
            overScrollBy(x - oldX, y - oldY, oldX, oldY, 0, range, 0, mOverflingDistance, false);
            onScrollChanged(mScrollX, mScrollY, oldX, oldY);
            if (canOverscroll) {
                if (y < 0 && oldY >= 0) mEdgeGlowTop.onAbsorb((int) mScroller.getCurrVelocity());
                else if (y > range && oldY <= range) mEdgeGlowBottom.onAbsorb((int) mScroller.getCurrVelocity());
            }
        }
        if (!awakenScrollBars()) postInvalidateOnAnimation();
    }

    private int getScrollRange() {
        int scrollRange = 0;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            scrollRange = Math.max(0, child.getHeight() - (getHeight() - mPaddingBottom - mPaddingTop));
        }
        return scrollRange;
    }

    public void scrollToDescendant(View child) {
        if (!mIsLayoutDirty) {
            child.getDrawingRect(mTempRect);
            offsetDescendantRectToMyCoords(child, mTempRect);
            int delta = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
            if (delta != 0) scrollBy(0, delta);
        } else {
            mChildToScrollTo = child;
        }
    }

    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        if (getChildCount() == 0) return 0;
        int height = getHeight();
        int screenTop = getScrollY();
        int screenBottom = screenTop + height;
        int fadingEdge = getVerticalFadingEdgeLength();
        if (rect.top > 0) screenTop += fadingEdge;
        if (rect.bottom < getChildAt(0).getHeight()) screenBottom -= fadingEdge;
        int scrollYDelta = 0;
        if (rect.bottom > screenBottom && rect.top > screenTop) {
            if (rect.height() > height) scrollYDelta += rect.top - screenTop;
            else scrollYDelta += rect.bottom - screenBottom;
            int distanceToBottom = getChildAt(0).getBottom() - screenBottom;
            scrollYDelta = Math.min(scrollYDelta, distanceToBottom);
        } else if (rect.top < screenTop && rect.bottom < screenBottom) {
            if (rect.height() > height) scrollYDelta -= screenBottom - rect.bottom;
            else scrollYDelta -= screenTop - rect.top;
            scrollYDelta = Math.max(scrollYDelta, -getScrollY());
        }
        return scrollYDelta;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        if (!mIsLayoutDirty) scrollToDescendant(focused);
        else mChildToScrollTo = focused;
        super.requestChildFocus(child, focused);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
        int delta = computeScrollDeltaToGetChildRectOnScreen(rectangle);
        if (delta == 0) return false;
        if (immediate) scrollBy(0, delta);
        else smoothScrollBy(0, delta);
        return true;
    }

    @Override
    public void requestLayout() {
        mIsLayoutDirty = true;
        super.requestLayout();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mEdgeGlowTop.finish();
        mEdgeGlowBottom.finish();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mIsLayoutDirty = false;
        if (mChildToScrollTo != null && isViewDescendantOf(mChildToScrollTo, this)) {
            scrollToDescendant(mChildToScrollTo);
        }
        mChildToScrollTo = null;
        if (!isLaidOut() && mSavedState != null) {
            int y = mSavedState.scrollPosition;
            mSavedState = null;
            scrollTo(mScrollX, y);
        }
        scrollTo(mScrollX, mScrollY);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        View current = findFocus();
        if (current == null || current == this || !isWithinDeltaOfScreen(current, 0)) return;
        current.getDrawingRect(mTempRect);
        offsetDescendantRectToMyCoords(current, mTempRect);
        doScrollY(computeScrollDeltaToGetChildRectOnScreen(mTempRect));
    }

    private static boolean isViewDescendantOf(View child, View parent) {
        if (child == parent) return true;
        ViewParent theParent = child.getParent();
        return (theParent instanceof ViewGroup) && isViewDescendantOf((View) theParent, parent);
    }

    public void fling(int velocityY) {
        if (getChildCount() == 0) return;
        int height = getHeight() - mPaddingBottom - mPaddingTop;
        int bottom = getChildAt(0).getHeight();
        mScroller.fling(mScrollX, mScrollY, 0, velocityY, 0, 0, 0, Math.max(0, bottom - height), 0, height / 2);
        postInvalidateOnAnimation();
    }

    @Override
    public void scrollTo(int x, int y) {
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            x = clamp(x, getWidth() - getPaddingLeft() - getPaddingRight(), child.getWidth());
            y = clamp(y, getHeight() - getPaddingTop() - getPaddingBottom(), child.getHeight());
        }
        if (x != mScrollX || y != mScrollY) super.scrollTo(x, y);
    }

    private static int clamp(int n, int my, int child) {
        if (my >= child || n < 0) return 0;
        if (my + n > child) return child - my;
        return n;
    }

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        return (nestedScrollAxes & SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int axes) {
        super.onNestedScrollAccepted(child, target, axes);
        startNestedScroll(SCROLL_AXIS_VERTICAL);
    }

    @Override
    public void onStopNestedScroll(View target) { super.onStopNestedScroll(target); }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        int oldScrollY = mScrollY;
        scrollBy(0, dyUnconsumed);
        int myConsumed = mScrollY - oldScrollY;
        int myUnconsumed = dyUnconsumed - myConsumed;
        dispatchNestedScroll(0, myConsumed, 0, myUnconsumed, null);
        if (myUnconsumed != 0) pullEdge(myUnconsumed, getWidth() / 2f);
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        if (!consumed) {
            fling((int) velocityY);
            return true;
        }
        return false;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        int width = getWidth();
        int height = getHeight();
        int scrollY = mScrollY;
        if (!mEdgeGlowTop.isFinished()) {
            int count = canvas.save();
            canvas.translate(getPaddingLeft(), Math.min(0, scrollY) + getPaddingTop());
            mEdgeGlowTop.setSize(width - getPaddingLeft() - getPaddingRight(), height);
            if (mEdgeGlowTop.draw(canvas)) postInvalidateOnAnimation();
            canvas.restoreToCount(count);
        }
        if (!mEdgeGlowBottom.isFinished()) {
            int count = canvas.save();
            canvas.translate(-width + getPaddingRight(), Math.max(getScrollRange(), scrollY) + height);
            canvas.rotate(180, width, 0);
            mEdgeGlowBottom.setSize(width - getPaddingLeft() - getPaddingRight(), height);
            if (mEdgeGlowBottom.draw(canvas)) postInvalidateOnAnimation();
            canvas.restoreToCount(count);
        }
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mSavedState = ss;
        requestLayout();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.scrollPosition = mScrollY;
        return ss;
    }

    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) mVelocityTracker = VelocityTracker.obtain();
        else mVelocityTracker.clear();
    }

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) mVelocityTracker = VelocityTracker.obtain();
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    static class SavedState extends BaseSavedState {
        int scrollPosition;

        SavedState(Parcelable superState) { super(superState); }

        SavedState(Parcel source) {
            super(source);
            scrollPosition = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(scrollPosition);
        }
    }
}
