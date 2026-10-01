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
 * A frame that scrolls its single child horizontally (AOSP HorizontalScrollView).
 * Dragging follows the finger. A fling continues on {@link OverScroller}.
 */
public class HorizontalScrollView extends FrameLayout {
    private static final int ANIMATED_SCROLL_GAP = 250;
    private static final float MAX_SCROLL_FACTOR = 0.5f;
    private static final int INVALID_POINTER = -1;
    private static final int[] SCROLL_ATTRS = {android.R.attr.fillViewport};

    private final Rect mTempRect = new Rect();
    private OverScroller mScroller;
    private EdgeEffect mEdgeGlowLeft;
    private EdgeEffect mEdgeGlowRight;
    private int mLastMotionX;
    private boolean mIsBeingDragged;
    private VelocityTracker mVelocityTracker;
    private boolean mFillViewport;
    private boolean mSmoothScrollingEnabled = true;
    private int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;
    private int mOverscrollDistance;
    private int mOverflingDistance;
    private float mHorizontalScrollFactor;
    private int mActivePointerId = INVALID_POINTER;
    private long mLastScroll;
    private View mChildToScrollTo;
    private boolean mIsLayoutDirty = true;
    private SavedState mSavedState;

    public HorizontalScrollView(Context context) { this(context, null); }

    public HorizontalScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.horizontalScrollViewStyle);
    }

    public HorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public HorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
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
        mHorizontalScrollFactor = configuration.getScaledHorizontalScrollFactor();
        mEdgeGlowLeft = new EdgeEffect(getContext());
        mEdgeGlowRight = new EdgeEffect(getContext());
    }

    @Override
    protected float getLeftFadingEdgeStrength() {
        if (getChildCount() == 0) return 0.0f;
        int length = getHorizontalFadingEdgeLength();
        if (length == 0 || mScrollX < length) return length == 0 ? 0 : mScrollX / (float) length;
        return 1.0f;
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        if (getChildCount() == 0) return 0.0f;
        int length = getHorizontalFadingEdgeLength();
        if (length == 0) return 0;
        int span = getChildAt(0).getRight() - mScrollX - (getWidth() - getPaddingRight());
        if (span < length) return span / (float) length;
        return 1.0f;
    }

    public void setEdgeEffectColor(int color) {
        setLeftEdgeEffectColor(color);
        setRightEdgeEffectColor(color);
    }

    public void setLeftEdgeEffectColor(int color) { mEdgeGlowLeft.setColor(color); }

    public void setRightEdgeEffectColor(int color) { mEdgeGlowRight.setColor(color); }

    public int getLeftEdgeEffectColor() { return mEdgeGlowLeft.getColor(); }

    public int getRightEdgeEffectColor() { return mEdgeGlowRight.getColor(); }

    public int getMaxScrollAmount() { return (int) (MAX_SCROLL_FACTOR * (mRight - mLeft)); }

    @Override
    public void addView(View child) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("HorizontalScrollView can host only one direct child");
        }
        super.addView(child);
    }

    @Override
    public void addView(View child, int index) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("HorizontalScrollView can host only one direct child");
        }
        super.addView(child, index);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("HorizontalScrollView can host only one direct child");
        }
        super.addView(child, params);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("HorizontalScrollView can host only one direct child");
        }
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
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) return;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
            int widthPadding = getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin;
            int heightPadding = getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin;
            int desiredWidth = getMeasuredWidth() - widthPadding;
            if (child.getMeasuredWidth() < desiredWidth) {
                int childWidth = MeasureSpec.makeMeasureSpec(desiredWidth, MeasureSpec.EXACTLY);
                int childHeight = getChildMeasureSpec(heightMeasureSpec, heightPadding, lp.height);
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
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    handled = event.isAltPressed() ? fullScroll(FOCUS_LEFT) : arrowScroll(FOCUS_LEFT);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    handled = event.isAltPressed() ? fullScroll(FOCUS_RIGHT) : arrowScroll(FOCUS_RIGHT);
                    break;
                case KeyEvent.KEYCODE_SPACE:
                    pageScroll(event.isShiftPressed() ? FOCUS_LEFT : FOCUS_RIGHT);
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
        return child.getWidth() > getWidth() - mPaddingLeft - mPaddingRight;
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
                int x = (int) ev.getX();
                if (!inChild(x, ev.getY())) {
                    mIsBeingDragged = false;
                    recycleVelocityTracker();
                    break;
                }
                mLastMotionX = x;
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
                int x = (int) ev.getX(index);
                if (Math.abs(x - mLastMotionX) > mTouchSlop && (getNestedScrollAxes() & SCROLL_AXIS_HORIZONTAL) == 0) {
                    mIsBeingDragged = true;
                    mLastMotionX = x;
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
                if (mScroller.springBack(mScrollX, mScrollY, 0, getScrollRange(), 0, 0)) postInvalidateOnAnimation();
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
            default:
                break;
        }
        return mIsBeingDragged;
    }

    @Override
    public boolean shouldDelayChildPressedState() { return true; }

    private boolean inChild(float x, float y) {
        if (getChildCount() == 0) return false;
        View child = getChildAt(0);
        return y >= child.getTop() && y < child.getBottom() && x >= child.getLeft() - mScrollX
                && x < child.getRight() - mScrollX;
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
            mLastMotionX = (int) ev.getX();
            mActivePointerId = ev.getPointerId(0);
        }
        mVelocityTracker.addMovement(vtev);
        vtev.recycle();
        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                int index = ev.findPointerIndex(mActivePointerId);
                if (index == -1) break;
                int x = (int) ev.getX(index);
                int deltaX = mLastMotionX - x;
                if (!mIsBeingDragged && Math.abs(deltaX) > mTouchSlop) {
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    mIsBeingDragged = true;
                    if (deltaX > 0) deltaX -= mTouchSlop;
                    else deltaX += mTouchSlop;
                }
                if (mIsBeingDragged) {
                    mLastMotionX = x;
                    int oldX = mScrollX;
                    int range = getScrollRange();
                    overScrollBy(deltaX, 0, mScrollX, 0, range, 0, mOverscrollDistance, 0, true);
                    int unconsumed = deltaX - (mScrollX - oldX);
                    if (unconsumed != 0) pullEdge(unconsumed, ev.getY(index));
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (mIsBeingDragged) {
                    VelocityTracker tracker = mVelocityTracker;
                    tracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialVelocity = (int) tracker.getXVelocity(mActivePointerId);
                    if (Math.abs(initialVelocity) > mMinimumVelocity) fling(-initialVelocity);
                    else if (mScroller.springBack(mScrollX, mScrollY, 0, getScrollRange(), 0, 0)) {
                        postInvalidateOnAnimation();
                    }
                    mActivePointerId = INVALID_POINTER;
                    endDrag();
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged && getChildCount() > 0) {
                    if (mScroller.springBack(mScrollX, mScrollY, 0, getScrollRange(), 0, 0)) {
                        postInvalidateOnAnimation();
                    }
                }
                mActivePointerId = INVALID_POINTER;
                endDrag();
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = ev.getActionIndex();
                mLastMotionX = (int) ev.getX(index);
                mActivePointerId = ev.getPointerId(index);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                int index = ev.findPointerIndex(mActivePointerId);
                if (index >= 0) mLastMotionX = (int) ev.getX(index);
                break;
            default:
                break;
        }
        return true;
    }

    private void pullEdge(int unconsumed, float y) {
        int overscrollMode = getOverScrollMode();
        boolean canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS
                || (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && getScrollRange() > 0);
        if (!canOverscroll || getWidth() == 0) return;
        float displacement = y / getHeight();
        if (unconsumed < 0) {
            mEdgeGlowLeft.onPull((float) -unconsumed / getWidth(), 1f - displacement);
            if (!mEdgeGlowRight.isFinished()) mEdgeGlowRight.onRelease();
        } else {
            mEdgeGlowRight.onPull((float) unconsumed / getWidth(), displacement);
            if (!mEdgeGlowLeft.isFinished()) mEdgeGlowLeft.onRelease();
        }
        if (!mEdgeGlowLeft.isFinished() || !mEdgeGlowRight.isFinished()) postInvalidateOnAnimation();
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        int index = ev.getActionIndex();
        if (ev.getPointerId(index) == mActivePointerId) {
            int newIndex = index == 0 ? 1 : 0;
            mLastMotionX = (int) ev.getX(newIndex);
            mActivePointerId = ev.getPointerId(newIndex);
            if (mVelocityTracker != null) mVelocityTracker.clear();
        }
    }

    private void endDrag() {
        mIsBeingDragged = false;
        recycleVelocityTracker();
        mEdgeGlowLeft.onRelease();
        mEdgeGlowRight.onRelease();
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & android.view.InputDevice.SOURCE_CLASS_POINTER) != 0
                && event.getAction() == MotionEvent.ACTION_SCROLL && !mIsBeingDragged) {
            float hscroll;
            if ((event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0) {
                hscroll = -event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            } else {
                hscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
            }
            if (hscroll != 0) {
                int delta = (int) (hscroll * mHorizontalScrollFactor);
                int range = getScrollRange();
                int oldScrollX = mScrollX;
                int newScrollX = oldScrollX + delta;
                if (newScrollX < 0) newScrollX = 0;
                else if (newScrollX > range) newScrollX = range;
                if (newScrollX != oldScrollX) {
                    super.scrollTo(newScrollX, mScrollY);
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
            if (clampedX) mScroller.springBack(mScrollX, mScrollY, 0, getScrollRange(), 0, 0);
            awakenScrollBars();
        } else {
            super.scrollTo(scrollX, scrollY);
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() { return HorizontalScrollView.class.getName(); }

    public boolean pageScroll(int direction) {
        boolean right = direction == FOCUS_RIGHT;
        int width = getWidth();
        if (right) {
            mTempRect.left = getScrollX() + width;
            if (getChildCount() > 0) {
                View child = getChildAt(0);
                if (mTempRect.left + width > child.getRight()) mTempRect.left = child.getRight() - width;
            }
        } else {
            mTempRect.left = getScrollX() - width;
            if (mTempRect.left < 0) mTempRect.left = 0;
        }
        mTempRect.right = mTempRect.left + width;
        return scrollAndFocus(direction, mTempRect.left, mTempRect.right);
    }

    public boolean fullScroll(int direction) {
        boolean right = direction == FOCUS_RIGHT;
        int width = getWidth();
        mTempRect.left = 0;
        mTempRect.right = width;
        if (right && getChildCount() > 0) {
            View child = getChildAt(0);
            mTempRect.right = child.getRight() + getPaddingRight();
            mTempRect.left = mTempRect.right - width;
        }
        return scrollAndFocus(direction, mTempRect.left, mTempRect.right);
    }

    private boolean scrollAndFocus(int direction, int left, int right) {
        boolean handled = true;
        int width = getWidth();
        int containerLeft = getScrollX();
        int containerRight = containerLeft + width;
        boolean goLeft = direction == FOCUS_LEFT;
        View newFocused = findFocusableViewInBounds(goLeft, left, right);
        if (newFocused == null) newFocused = this;
        if (left >= containerLeft && right <= containerRight) handled = false;
        else doScrollX(goLeft ? (left - containerLeft) : (right - containerRight));
        if (newFocused != findFocus()) newFocused.requestFocus(direction);
        return handled;
    }

    private View findFocusableViewInBounds(boolean leftFocus, int left, int right) {
        java.util.ArrayList<View> focusables = getFocusables(FOCUS_FORWARD);
        View focusCandidate = null;
        boolean foundFullyContained = false;
        int count = focusables.size();
        for (int i = 0; i < count; i++) {
            View view = focusables.get(i);
            int viewLeft = view.getLeft();
            int viewRight = view.getRight();
            if (left < viewRight && viewLeft < right) {
                boolean contained = left < viewLeft && viewRight < right;
                if (focusCandidate == null) {
                    focusCandidate = view;
                    foundFullyContained = contained;
                } else {
                    boolean closer = (leftFocus && viewLeft < focusCandidate.getLeft())
                            || (!leftFocus && viewRight > focusCandidate.getRight());
                    if (foundFullyContained) {
                        if (contained && closer) focusCandidate = view;
                    } else if (contained) {
                        focusCandidate = view;
                        foundFullyContained = true;
                    } else if (closer) {
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
            doScrollX(computeScrollDeltaToGetChildRectOnScreen(mTempRect));
            nextFocused.requestFocus(direction);
        } else {
            int scrollDelta = maxJump;
            if (direction == FOCUS_LEFT && getScrollX() < scrollDelta) scrollDelta = getScrollX();
            else if (direction == FOCUS_RIGHT && getChildCount() > 0) {
                int daRight = getChildAt(0).getRight();
                int screenRight = getScrollX() + getWidth() - mPaddingRight;
                if (daRight - screenRight < maxJump) scrollDelta = daRight - screenRight;
            }
            if (scrollDelta == 0) return false;
            doScrollX(direction == FOCUS_RIGHT ? scrollDelta : -scrollDelta);
        }
        return true;
    }

    private boolean isWithinDeltaOfScreen(View descendant, int delta) {
        descendant.getDrawingRect(mTempRect);
        offsetDescendantRectToMyCoords(descendant, mTempRect);
        return mTempRect.right + delta >= getScrollX() && mTempRect.left - delta <= getScrollX() + getWidth();
    }

    private void doScrollX(int delta) {
        if (delta == 0) return;
        if (mSmoothScrollingEnabled) smoothScrollBy(delta, 0);
        else scrollBy(delta, 0);
    }

    public final void smoothScrollBy(int dx, int dy) {
        if (getChildCount() == 0) return;
        long now = SystemClock.uptimeMillis();
        if (now - mLastScroll > ANIMATED_SCROLL_GAP) {
            int width = getWidth() - mPaddingRight - mPaddingLeft;
            int right = getChildAt(0).getWidth();
            int maxX = Math.max(0, right - width);
            int scrollX = mScrollX;
            dx = Math.max(0, Math.min(scrollX + dx, maxX)) - scrollX;
            mScroller.startScroll(scrollX, mScrollY, dx, 0);
            postInvalidateOnAnimation();
        } else {
            if (!mScroller.isFinished()) mScroller.abortAnimation();
            scrollBy(dx, dy);
        }
        mLastScroll = now;
    }

    public final void smoothScrollTo(int x, int y) { smoothScrollBy(x - mScrollX, y - mScrollY); }

    @Override
    protected int computeHorizontalScrollRange() {
        int contentWidth = getWidth() - mPaddingRight - mPaddingLeft;
        if (getChildCount() == 0) return contentWidth;
        int scrollRange = getChildAt(0).getRight();
        int scrollX = mScrollX;
        int overscrollRight = Math.max(0, scrollRange - contentWidth);
        if (scrollX < 0) scrollRange -= scrollX;
        else if (scrollX > overscrollRight) scrollRange += scrollX - overscrollRight;
        return scrollRange;
    }

    @Override
    protected int computeHorizontalScrollOffset() { return Math.max(0, super.computeHorizontalScrollOffset()); }

    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, mPaddingTop + mPaddingBottom,
                lp.height);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                lp.leftMargin + lp.rightMargin, MeasureSpec.UNSPECIFIED);
        int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin + heightUsed, lp.height);
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
            overScrollBy(x - oldX, y - oldY, oldX, oldY, range, 0, mOverflingDistance, 0, false);
            onScrollChanged(mScrollX, mScrollY, oldX, oldY);
            if (canOverscroll) {
                if (x < 0 && oldX >= 0) mEdgeGlowLeft.onAbsorb((int) mScroller.getCurrVelocity());
                else if (x > range && oldX <= range) mEdgeGlowRight.onAbsorb((int) mScroller.getCurrVelocity());
            }
        }
        if (!awakenScrollBars()) postInvalidateOnAnimation();
    }

    private int getScrollRange() {
        if (getChildCount() == 0) return 0;
        View child = getChildAt(0);
        return Math.max(0, child.getWidth() - (getWidth() - mPaddingLeft - mPaddingRight));
    }

    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        if (getChildCount() == 0) return 0;
        int width = getWidth();
        int screenLeft = getScrollX();
        int screenRight = screenLeft + width;
        int fadingEdge = getHorizontalFadingEdgeLength();
        if (rect.left > 0) screenLeft += fadingEdge;
        if (rect.right < getChildAt(0).getWidth()) screenRight -= fadingEdge;
        int scrollXDelta = 0;
        if (rect.right > screenRight && rect.left > screenLeft) {
            if (rect.width() > width) scrollXDelta += rect.left - screenLeft;
            else scrollXDelta += rect.right - screenRight;
            int distanceToRight = getChildAt(0).getRight() - screenRight;
            scrollXDelta = Math.min(scrollXDelta, distanceToRight);
        } else if (rect.left < screenLeft && rect.right < screenRight) {
            if (rect.width() > width) scrollXDelta -= screenRight - rect.right;
            else scrollXDelta -= screenLeft - rect.left;
            scrollXDelta = Math.max(scrollXDelta, -getScrollX());
        }
        return scrollXDelta;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        if (focused != null && !mIsLayoutDirty) {
            focused.getDrawingRect(mTempRect);
            offsetDescendantRectToMyCoords(focused, mTempRect);
            int delta = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
            if (delta != 0) scrollBy(delta, 0);
        } else {
            mChildToScrollTo = focused;
        }
        super.requestChildFocus(child, focused);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        rectangle.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
        int delta = computeScrollDeltaToGetChildRectOnScreen(rectangle);
        if (delta == 0) return false;
        if (immediate) scrollBy(delta, 0);
        else smoothScrollBy(delta, 0);
        return true;
    }

    @Override
    public void requestLayout() {
        mIsLayoutDirty = true;
        super.requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mIsLayoutDirty = false;
        if (mChildToScrollTo != null && isViewDescendantOf(mChildToScrollTo, this)) {
            mChildToScrollTo.getDrawingRect(mTempRect);
            offsetDescendantRectToMyCoords(mChildToScrollTo, mTempRect);
            int delta = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
            if (delta != 0) scrollBy(delta, 0);
        }
        mChildToScrollTo = null;
        if (!isLaidOut() && mSavedState != null) {
            int x = mSavedState.scrollPosition;
            mSavedState = null;
            scrollTo(x, mScrollY);
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
        doScrollX(computeScrollDeltaToGetChildRectOnScreen(mTempRect));
    }

    private static boolean isViewDescendantOf(View child, View parent) {
        if (child == parent) return true;
        ViewParent theParent = child.getParent();
        return (theParent instanceof ViewGroup) && isViewDescendantOf((View) theParent, parent);
    }

    public void fling(int velocityX) {
        if (getChildCount() == 0) return;
        int width = getWidth() - mPaddingRight - mPaddingLeft;
        int right = getChildAt(0).getWidth();
        mScroller.fling(mScrollX, mScrollY, velocityX, 0, 0, Math.max(0, right - width), 0, 0, width / 2, 0);
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
    public void draw(Canvas canvas) {
        super.draw(canvas);
        int width = getWidth();
        int height = getHeight();
        int scrollX = mScrollX;
        if (!mEdgeGlowLeft.isFinished()) {
            int count = canvas.save();
            canvas.rotate(270);
            canvas.translate(-height + getPaddingTop(), Math.min(0, scrollX) + getPaddingLeft());
            mEdgeGlowLeft.setSize(height - getPaddingTop() - getPaddingBottom(), width);
            if (mEdgeGlowLeft.draw(canvas)) postInvalidateOnAnimation();
            canvas.restoreToCount(count);
        }
        if (!mEdgeGlowRight.isFinished()) {
            int count = canvas.save();
            canvas.rotate(90);
            canvas.translate(-getPaddingTop(), -(Math.max(getScrollRange(), scrollX) + width));
            mEdgeGlowRight.setSize(height - getPaddingTop() - getPaddingBottom(), width);
            if (mEdgeGlowRight.draw(canvas)) postInvalidateOnAnimation();
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
        ss.scrollPosition = mScrollX;
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
