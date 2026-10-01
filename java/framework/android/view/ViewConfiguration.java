package android.view;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.SparseArray;

/** UI timeouts, sizes and distances, with the AOSP (API 29) default values scaled by density. */
public class ViewConfiguration {
    private static final int SCROLL_BAR_SIZE = 4;
    private static final int SCROLL_BAR_FADE_DURATION = 250;
    private static final int SCROLL_BAR_DEFAULT_DELAY = 300;
    private static final int FADING_EDGE_LENGTH = 12;
    private static final int PRESSED_STATE_DURATION = 64;
    private static final int DEFAULT_LONG_PRESS_TIMEOUT = 500;
    private static final int DEFAULT_MULTI_PRESS_TIMEOUT = 300;
    private static final int KEY_REPEAT_DELAY = 50;
    private static final int GLOBAL_ACTIONS_KEY_TIMEOUT = 500;
    private static final int TAP_TIMEOUT = 100;
    private static final int JUMP_TAP_TIMEOUT = 500;
    private static final int DOUBLE_TAP_TIMEOUT = 300;
    private static final int DOUBLE_TAP_MIN_TIME = 40;
    private static final int HOVER_TAP_TIMEOUT = 150;
    private static final int HOVER_TAP_SLOP = 20;
    private static final int ZOOM_CONTROLS_TIMEOUT = 3000;
    private static final int EDGE_SLOP = 12;
    private static final int TOUCH_SLOP = 8;
    private static final int MIN_SCALING_SPAN = 170;
    private static final int DOUBLE_TAP_TOUCH_SLOP = TOUCH_SLOP;
    private static final int PAGING_TOUCH_SLOP = TOUCH_SLOP * 2;
    private static final int DOUBLE_TAP_SLOP = 100;
    private static final int WINDOW_TOUCH_SLOP = 16;
    private static final int MINIMUM_FLING_VELOCITY = 50;
    private static final int MAXIMUM_FLING_VELOCITY = 8000;
    private static final int MAXIMUM_DRAWING_CACHE_SIZE = 480 * 800 * 4;
    private static final float SCROLL_FRICTION = 0.015f;
    private static final int OVERSCROLL_DISTANCE = 0;
    private static final int OVERFLING_DISTANCE = 6;
    private static final int HORIZONTAL_SCROLL_FACTOR = 64;
    private static final int VERTICAL_SCROLL_FACTOR = 64;
    private static final float AMBIGUOUS_GESTURE_MULTIPLIER = 2f;
    private static final long ACTION_MODE_HIDE_DURATION_DEFAULT = 2000;

    private static final SparseArray<ViewConfiguration> sConfigurations = new SparseArray<ViewConfiguration>(2);

    private final int mEdgeSlop;
    private final int mFadingEdgeLength;
    private final int mMinimumFlingVelocity;
    private final int mMaximumFlingVelocity;
    private final int mScrollbarSize;
    private final int mTouchSlop;
    private final int mHoverSlop;
    private final int mMinScalingSpan;
    private final int mDoubleTapTouchSlop;
    private final int mPagingTouchSlop;
    private final int mDoubleTapSlop;
    private final int mWindowTouchSlop;
    private final int mMaximumDrawingCacheSize;
    private final int mOverscrollDistance;
    private final int mOverflingDistance;
    private final float mHorizontalScrollFactor;
    private final float mVerticalScrollFactor;

    @Deprecated
    public ViewConfiguration() { this(1f, 480 * 800 * 4); }

    private ViewConfiguration(float density, int cacheSize) {
        mEdgeSlop = (int) (density * EDGE_SLOP + 0.5f);
        mFadingEdgeLength = (int) (density * FADING_EDGE_LENGTH + 0.5f);
        mMinimumFlingVelocity = (int) (density * MINIMUM_FLING_VELOCITY + 0.5f);
        mMaximumFlingVelocity = (int) (density * MAXIMUM_FLING_VELOCITY + 0.5f);
        mScrollbarSize = (int) (density * SCROLL_BAR_SIZE + 0.5f);
        mTouchSlop = (int) (density * TOUCH_SLOP + 0.5f);
        mHoverSlop = mTouchSlop / 2;
        mMinScalingSpan = (int) (density * MIN_SCALING_SPAN + 0.5f);
        mDoubleTapTouchSlop = (int) (density * DOUBLE_TAP_TOUCH_SLOP + 0.5f);
        mPagingTouchSlop = mTouchSlop * 2;
        mDoubleTapSlop = (int) (density * DOUBLE_TAP_SLOP + 0.5f);
        mWindowTouchSlop = (int) (density * WINDOW_TOUCH_SLOP + 0.5f);
        mMaximumDrawingCacheSize = cacheSize;
        mOverscrollDistance = (int) (density * OVERSCROLL_DISTANCE + 0.5f);
        mOverflingDistance = (int) (density * OVERFLING_DISTANCE + 0.5f);
        mHorizontalScrollFactor = density * HORIZONTAL_SCROLL_FACTOR;
        mVerticalScrollFactor = density * VERTICAL_SCROLL_FACTOR;
    }

    public static ViewConfiguration get(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int density = (int) (100.0f * metrics.density);
        ViewConfiguration configuration = sConfigurations.get(density);
        if (configuration == null) {
            configuration = new ViewConfiguration(metrics.density, 4 * metrics.widthPixels * metrics.heightPixels);
            sConfigurations.put(density, configuration);
        }
        return configuration;
    }

    @Deprecated
    public static int getScrollBarSize() { return SCROLL_BAR_SIZE; }
    public int getScaledScrollBarSize() { return mScrollbarSize; }
    public static int getScrollBarFadeDuration() { return SCROLL_BAR_FADE_DURATION; }
    public static int getScrollDefaultDelay() { return SCROLL_BAR_DEFAULT_DELAY; }
    @Deprecated
    public static int getFadingEdgeLength() { return FADING_EDGE_LENGTH; }
    public int getScaledFadingEdgeLength() { return mFadingEdgeLength; }
    public static int getPressedStateDuration() { return PRESSED_STATE_DURATION; }
    public static int getLongPressTimeout() { return DEFAULT_LONG_PRESS_TIMEOUT; }
    public static int getMultiPressTimeout() { return DEFAULT_MULTI_PRESS_TIMEOUT; }
    public static int getKeyRepeatTimeout() { return getLongPressTimeout(); }
    public static int getKeyRepeatDelay() { return KEY_REPEAT_DELAY; }
    public static int getTapTimeout() { return TAP_TIMEOUT; }
    public static int getJumpTapTimeout() { return JUMP_TAP_TIMEOUT; }
    public static int getDoubleTapTimeout() { return DOUBLE_TAP_TIMEOUT; }

    /** framework-internal (hidden in AOSP). */
    public static int getDoubleTapMinTime() { return DOUBLE_TAP_MIN_TIME; }

    /** framework-internal (hidden in AOSP). */
    public static int getHoverTapTimeout() { return HOVER_TAP_TIMEOUT; }

    /** framework-internal (hidden in AOSP). */
    public static int getHoverTapSlop() { return HOVER_TAP_SLOP; }

    @Deprecated
    public static int getEdgeSlop() { return EDGE_SLOP; }
    public int getScaledEdgeSlop() { return mEdgeSlop; }
    @Deprecated
    public static int getTouchSlop() { return TOUCH_SLOP; }
    public int getScaledTouchSlop() { return mTouchSlop; }
    public int getScaledHandwritingSlop() { return mTouchSlop; }
    public int getScaledHoverSlop() { return mHoverSlop; }
    public int getScaledPagingTouchSlop() { return mPagingTouchSlop; }
    public int getScaledDoubleTapSlop() { return mDoubleTapSlop; }
    public int getScaledHandwritingGestureLineMargin() { return mTouchSlop; }

    /** framework-internal (hidden in AOSP). */
    public int getScaledDoubleTapTouchSlop() { return mDoubleTapTouchSlop; }

    @Deprecated
    public static int getWindowTouchSlop() { return WINDOW_TOUCH_SLOP; }
    public int getScaledWindowTouchSlop() { return mWindowTouchSlop; }
    @Deprecated
    public static int getMinimumFlingVelocity() { return MINIMUM_FLING_VELOCITY; }
    public int getScaledMinimumFlingVelocity() { return mMinimumFlingVelocity; }
    @Deprecated
    public static int getMaximumFlingVelocity() { return MAXIMUM_FLING_VELOCITY; }
    public int getScaledMaximumFlingVelocity() { return mMaximumFlingVelocity; }
    public float getScaledHorizontalScrollFactor() { return mHorizontalScrollFactor; }
    public float getScaledVerticalScrollFactor() { return mVerticalScrollFactor; }
    @Deprecated
    public static int getMaximumDrawingCacheSize() { return MAXIMUM_DRAWING_CACHE_SIZE; }
    public int getScaledMaximumDrawingCacheSize() { return mMaximumDrawingCacheSize; }
    public int getScaledOverscrollDistance() { return mOverscrollDistance; }
    public int getScaledOverflingDistance() { return mOverflingDistance; }
    public static long getZoomControlsTimeout() { return ZOOM_CONTROLS_TIMEOUT; }
    @Deprecated
    public static long getGlobalActionKeyTimeout() { return GLOBAL_ACTIONS_KEY_TIMEOUT; }
    public static float getScrollFriction() { return SCROLL_FRICTION; }
    public static long getDefaultActionModeHideDuration() { return ACTION_MODE_HIDE_DURATION_DEFAULT; }
    @Deprecated
    public static float getAmbiguousGestureMultiplier() { return AMBIGUOUS_GESTURE_MULTIPLIER; }
    public float getScaledAmbiguousGestureMultiplier() { return AMBIGUOUS_GESTURE_MULTIPLIER; }
    public boolean hasPermanentMenuKey() { return false; }

    public int getScaledMinimumFlingVelocity(int inputDeviceId, int axis, int source) {
        return mMinimumFlingVelocity;
    }

    public int getScaledMaximumFlingVelocity(int inputDeviceId, int axis, int source) {
        return mMaximumFlingVelocity;
    }

    public boolean shouldShowMenuShortcutsWhenKeyboardPresent() { return false; }
    public int getScaledMinimumScalingSpan() { return mMinScalingSpan; }

    /** framework-internal (hidden in AOSP). */
    public boolean isFadingMarqueeEnabled() { return false; }
}
