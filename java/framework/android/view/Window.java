package android.view;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.accessibility.AccessibilityEvent;
import java.util.Collections;
import java.util.List;

/**
 * Abstract top-level window (AOSP Window): window attributes, features,
 * callbacks and the theme's window style. PhoneWindow supplies the decor.
 */
public abstract class Window {
    public static final int DECOR_CAPTION_SHADE_AUTO = 0;
    public static final int DECOR_CAPTION_SHADE_DARK = 2;
    public static final int DECOR_CAPTION_SHADE_LIGHT = 1;
    protected static final int DEFAULT_FEATURES = 65;
    public static final int FEATURE_ACTION_BAR = 8;
    public static final int FEATURE_ACTION_BAR_OVERLAY = 9;
    public static final int FEATURE_ACTION_MODE_OVERLAY = 10;
    public static final int FEATURE_ACTIVITY_TRANSITIONS = 13;
    public static final int FEATURE_CONTENT_TRANSITIONS = 12;
    public static final int FEATURE_CONTEXT_MENU = 6;
    public static final int FEATURE_CUSTOM_TITLE = 7;
    public static final int FEATURE_INDETERMINATE_PROGRESS = 5;
    public static final int FEATURE_LEFT_ICON = 3;
    public static final int FEATURE_NO_TITLE = 1;
    public static final int FEATURE_OPTIONS_PANEL = 0;
    public static final int FEATURE_PROGRESS = 2;
    public static final int FEATURE_RIGHT_ICON = 4;
    public static final int FEATURE_SWIPE_TO_DISMISS = 11;
    public static final int ID_ANDROID_CONTENT = 16908290;
    public static final String NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME = "android:navigation:background";
    public static final int PROGRESS_END = 10000;
    public static final int PROGRESS_INDETERMINATE_OFF = -4;
    public static final int PROGRESS_INDETERMINATE_ON = -3;
    public static final int PROGRESS_SECONDARY_END = 30000;
    public static final int PROGRESS_SECONDARY_START = 20000;
    public static final int PROGRESS_START = 0;
    public static final int PROGRESS_VISIBILITY_OFF = -2;
    public static final int PROGRESS_VISIBILITY_ON = -1;
    public static final String STATUS_BAR_BACKGROUND_TRANSITION_NAME = "android:status:background";

    /** framework-internal: the Window styleable attributes read by getWindowStyle(). */
    static final int[] WINDOW_ATTRS = {
        android.R.attr.windowBackground, android.R.attr.windowFrame, android.R.attr.windowNoTitle,
        android.R.attr.windowFullscreen, android.R.attr.windowIsFloating, android.R.attr.windowIsTranslucent,
        android.R.attr.windowContentOverlay, android.R.attr.windowShowWallpaper, android.R.attr.windowActionBar,
        android.R.attr.windowActionBarOverlay, android.R.attr.windowActionModeOverlay,
        android.R.attr.windowSoftInputMode, android.R.attr.windowCloseOnTouchOutside,
        android.R.attr.windowMinWidthMajor, android.R.attr.windowMinWidthMinor, android.R.attr.backgroundDimEnabled,
        android.R.attr.backgroundDimAmount, android.R.attr.windowEnableSplitTouch, android.R.attr.statusBarColor,
        android.R.attr.navigationBarColor, android.R.attr.windowElevation, android.R.attr.windowClipToOutline,
        0 /* windowFixedWidthMajor */, 0 /* windowFixedWidthMinor */,
        0 /* windowFixedHeightMajor */, 0 /* windowFixedHeightMinor */,
        android.R.attr.windowTranslucentStatus, android.R.attr.windowTranslucentNavigation,
        android.R.attr.windowSwipeToDismiss, android.R.attr.windowContentTransitions,
        android.R.attr.windowLightStatusBar, android.R.attr.windowLayoutInDisplayCutoutMode,
        android.R.attr.windowAnimationStyle,
    };
    /** framework-internal: indexes into WINDOW_ATTRS. */
    static final int STYLE_BACKGROUND = 0, STYLE_FRAME = 1, STYLE_NO_TITLE = 2, STYLE_FULLSCREEN = 3,
            STYLE_IS_FLOATING = 4, STYLE_IS_TRANSLUCENT = 5, STYLE_CONTENT_OVERLAY = 6, STYLE_SHOW_WALLPAPER = 7,
            STYLE_ACTION_BAR = 8, STYLE_ACTION_BAR_OVERLAY = 9, STYLE_ACTION_MODE_OVERLAY = 10,
            STYLE_SOFT_INPUT_MODE = 11, STYLE_CLOSE_ON_TOUCH_OUTSIDE = 12, STYLE_MIN_WIDTH_MAJOR = 13,
            STYLE_MIN_WIDTH_MINOR = 14, STYLE_DIM_ENABLED = 15, STYLE_DIM_AMOUNT = 16, STYLE_SPLIT_TOUCH = 17,
            STYLE_STATUS_BAR_COLOR = 18, STYLE_NAVIGATION_BAR_COLOR = 19, STYLE_ELEVATION = 20,
            STYLE_CLIP_TO_OUTLINE = 21, STYLE_FIXED_WIDTH_MAJOR = 22, STYLE_FIXED_WIDTH_MINOR = 23,
            STYLE_FIXED_HEIGHT_MAJOR = 24, STYLE_FIXED_HEIGHT_MINOR = 25, STYLE_ANIMATION_STYLE = 32;

    private final Context mContext;
    private TypedArray mWindowStyle;
    private Callback mCallback;
    private WindowManager mWindowManager;
    private IBinder mAppToken;
    private String mAppName;
    private boolean mHardwareAccelerated;
    private Window mContainer;
    private Window mActiveChild;
    private boolean mIsActive;
    private boolean mHasChildren;
    private boolean mCloseOnTouchOutside;
    private boolean mSetCloseOnTouchOutside;
    private int mForcedWindowFlags;
    private int mFeatures;
    private int mLocalFeatures;
    private boolean mHaveWindowFormat;
    private boolean mHaveDimAmount;
    private int mDefaultWindowFormat = android.graphics.PixelFormat.OPAQUE;
    private boolean mHasSoftInputMode;
    private boolean mDestroyed;
    private final WindowManager.LayoutParams mWindowAttributes = new WindowManager.LayoutParams();
    private List<Rect> mSystemGestureExclusionRects = Collections.emptyList();

    public interface Callback {
        boolean dispatchKeyEvent(KeyEvent event);
        boolean dispatchKeyShortcutEvent(KeyEvent event);
        boolean dispatchTouchEvent(MotionEvent event);
        boolean dispatchTrackballEvent(MotionEvent event);
        boolean dispatchGenericMotionEvent(MotionEvent event);
        boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event);
        View onCreatePanelView(int featureId);
        boolean onCreatePanelMenu(int featureId, Menu menu);
        boolean onPreparePanel(int featureId, View view, Menu menu);
        boolean onMenuOpened(int featureId, Menu menu);
        boolean onMenuItemSelected(int featureId, MenuItem item);
        void onWindowAttributesChanged(WindowManager.LayoutParams attrs);
        void onContentChanged();
        void onWindowFocusChanged(boolean hasFocus);
        void onAttachedToWindow();
        void onDetachedFromWindow();
        void onPanelClosed(int featureId, Menu menu);
        boolean onSearchRequested();
        boolean onSearchRequested(SearchEvent searchEvent);
        ActionMode onWindowStartingActionMode(ActionMode.Callback callback);
        ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type);
        void onActionModeStarted(ActionMode mode);
        void onActionModeFinished(ActionMode mode);
        default void onPointerCaptureChanged(boolean hasCapture) {}
    }

    public interface OnFrameMetricsAvailableListener {
        void onFrameMetricsAvailable(Window window, FrameMetrics frameMetrics, int dropCountSinceLastInvocation);
    }

    public interface OnRestrictedCaptionAreaChangedListener {
        void onRestrictedCaptionAreaChanged(Rect rect);
    }

    public Window(Context context) {
        mContext = context;
        mFeatures = mLocalFeatures = getDefaultFeatures(context);
    }

    public final Context getContext() { return mContext; }

    public final TypedArray getWindowStyle() {
        synchronized (this) {
            if (mWindowStyle == null) {
                if (WINDOW_ATTRS[22] == 0) {
                    // internal (non-public) attributes, looked up by name
                    android.content.res.Resources r = mContext.getResources();
                    WINDOW_ATTRS[22] = r.getIdentifier("windowFixedWidthMajor", "attr", "android");
                    WINDOW_ATTRS[23] = r.getIdentifier("windowFixedWidthMinor", "attr", "android");
                    WINDOW_ATTRS[24] = r.getIdentifier("windowFixedHeightMajor", "attr", "android");
                    WINDOW_ATTRS[25] = r.getIdentifier("windowFixedHeightMinor", "attr", "android");
                }
                mWindowStyle = mContext.obtainStyledAttributes(WINDOW_ATTRS);
            }
            return mWindowStyle;
        }
    }

    public void setContainer(Window container) {
        mContainer = container;
        if (container != null) {
            mFeatures |= 1 << FEATURE_NO_TITLE;
            mLocalFeatures |= 1 << FEATURE_NO_TITLE;
            container.mHasChildren = true;
        }
    }

    public final Window getContainer() { return mContainer; }

    public final boolean hasChildren() { return mHasChildren; }

    /** framework-internal (hidden in AOSP). */
    public final void destroy() { mDestroyed = true; }

    /** framework-internal (hidden in AOSP). */
    public final boolean isDestroyed() { return mDestroyed; }

    public void setWindowManager(WindowManager wm, IBinder appToken, String appName) {
        setWindowManager(wm, appToken, appName, false);
    }

    public void setWindowManager(WindowManager wm, IBinder appToken, String appName, boolean hardwareAccelerated) {
        mAppToken = appToken;
        mAppName = appName;
        mHardwareAccelerated = hardwareAccelerated;
        mWindowManager = wm != null ? wm : WindowManagerImpl.getDefault();
    }

    public WindowManager getWindowManager() {
        return mWindowManager != null ? mWindowManager : WindowManagerImpl.getDefault();
    }

    public void setCallback(Callback callback) { mCallback = callback; }

    public final Callback getCallback() { return mCallback; }

    public final void addOnFrameMetricsAvailableListener(OnFrameMetricsAvailableListener listener, Handler handler) {}

    public final void removeOnFrameMetricsAvailableListener(OnFrameMetricsAvailableListener listener) {}

    public final void setRestrictedCaptionAreaListener(OnRestrictedCaptionAreaChangedListener listener) {}

    public final void setHideOverlayWindows(boolean hide) {}

    public abstract void takeSurface(SurfaceHolder.Callback2 callback);

    public abstract void takeInputQueue(InputQueue.Callback callback);

    public abstract boolean isFloating();

    public void setLayout(int width, int height) {
        final WindowManager.LayoutParams attrs = getAttributes();
        attrs.width = width;
        attrs.height = height;
        dispatchWindowAttributesChanged(attrs);
    }

    public void setGravity(int gravity) {
        final WindowManager.LayoutParams attrs = getAttributes();
        attrs.gravity = gravity;
        dispatchWindowAttributesChanged(attrs);
    }

    public void setType(int type) {
        final WindowManager.LayoutParams attrs = getAttributes();
        attrs.type = type;
        dispatchWindowAttributesChanged(attrs);
    }

    public void setFormat(int format) {
        final WindowManager.LayoutParams attrs = getAttributes();
        if (format != android.graphics.PixelFormat.UNKNOWN) {
            attrs.format = format;
            mHaveWindowFormat = true;
        } else {
            attrs.format = mDefaultWindowFormat;
            mHaveWindowFormat = false;
        }
        dispatchWindowAttributesChanged(attrs);
    }

    public void setWindowAnimations(int resId) {
        final WindowManager.LayoutParams attrs = getAttributes();
        attrs.windowAnimations = resId;
        dispatchWindowAttributesChanged(attrs);
    }

    public void setSoftInputMode(int mode) {
        final WindowManager.LayoutParams attrs = getAttributes();
        if (mode != WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED) {
            attrs.softInputMode = mode;
            mHasSoftInputMode = true;
        } else {
            mHasSoftInputMode = false;
        }
        dispatchWindowAttributesChanged(attrs);
    }

    public void addFlags(int flags) { setFlags(flags, flags); }

    public void clearFlags(int flags) { setFlags(0, flags); }

    public void setFlags(int flags, int mask) {
        final WindowManager.LayoutParams attrs = getAttributes();
        attrs.flags = (attrs.flags & ~mask) | (flags & mask);
        mForcedWindowFlags |= mask;
        dispatchWindowAttributesChanged(attrs);
    }

    public void setColorMode(int colorMode) {}

    public int getColorMode() { return 0; }

    public boolean isWideColorGamut() { return false; }

    public void setPreferMinimalPostProcessing(boolean isPreferred) {
        mWindowAttributes.preferMinimalPostProcessing = isPreferred;
        dispatchWindowAttributesChanged(mWindowAttributes);
    }

    public void setDimAmount(float amount) {
        final WindowManager.LayoutParams attrs = getAttributes();
        attrs.dimAmount = amount;
        mHaveDimAmount = true;
        dispatchWindowAttributesChanged(attrs);
    }

    /** framework-internal (hidden in AOSP). */
    protected boolean haveDimAmount() { return mHaveDimAmount; }

    public void setDecorFitsSystemWindows(boolean decorFitsSystemWindows) {}

    public void setAttributes(WindowManager.LayoutParams a) {
        mWindowAttributes.copyFrom(a);
        dispatchWindowAttributesChanged(mWindowAttributes);
    }

    public final WindowManager.LayoutParams getAttributes() { return mWindowAttributes; }

    protected final int getForcedWindowFlags() { return mForcedWindowFlags; }

    protected final boolean hasSoftInputMode() { return mHasSoftInputMode; }

    /** framework-internal (hidden in AOSP). */
    protected void dispatchWindowAttributesChanged(WindowManager.LayoutParams attrs) {
        if (mCallback != null) mCallback.onWindowAttributesChanged(attrs);
    }

    public void setSustainedPerformanceMode(boolean enable) {}

    public void setCloseOnTouchOutside(boolean close) {
        mCloseOnTouchOutside = close;
        mSetCloseOnTouchOutside = true;
    }

    /** framework-internal (hidden in AOSP). */
    public void setCloseOnTouchOutsideIfNotSet(boolean close) {
        if (!mSetCloseOnTouchOutside) {
            mCloseOnTouchOutside = close;
            mSetCloseOnTouchOutside = true;
        }
    }

    /** framework-internal (hidden in AOSP). */
    public boolean shouldCloseOnTouch(Context context, MotionEvent event) {
        final boolean isOutside = event.getAction() == MotionEvent.ACTION_UP && isOutOfBounds(context, event)
                || event.getAction() == MotionEvent.ACTION_OUTSIDE;
        return mCloseOnTouchOutside && peekDecorView() != null && isOutside;
    }

    private boolean isOutOfBounds(Context context, MotionEvent event) {
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        final int slop = ViewConfiguration.get(context).getScaledWindowTouchSlop();
        final View decorView = getDecorView();
        return (x < -slop) || (y < -slop) || (x > (decorView.getWidth() + slop))
                || (y > (decorView.getHeight() + slop));
    }

    public boolean requestFeature(int featureId) {
        final int flag = 1 << featureId;
        mFeatures |= flag;
        mLocalFeatures |= mContainer != null ? (flag & ~mContainer.mFeatures) : flag;
        return (mFeatures & flag) != 0;
    }

    /** framework-internal (hidden in AOSP). */
    protected void removeFeature(int featureId) {
        final int flag = 1 << featureId;
        mFeatures &= ~flag;
        mLocalFeatures &= ~(mContainer != null ? (flag & ~mContainer.mFeatures) : flag);
    }

    public final void makeActive() {
        if (mContainer != null) {
            if (mContainer.mActiveChild != null) mContainer.mActiveChild.mIsActive = false;
            mContainer.mActiveChild = this;
        }
        mIsActive = true;
        onActive();
    }

    public final boolean isActive() { return mIsActive; }

    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int id) {
        View decor = getDecorView();
        return decor != null ? (T) decor.findViewById(id) : null;
    }

    public final <T extends View> T requireViewById(int id) {
        T view = findViewById(id);
        if (view == null) throw new IllegalArgumentException("ID does not reference a View inside this Window");
        return view;
    }

    public abstract void setContentView(int layoutResID);

    public abstract void setContentView(View view);

    public abstract void setContentView(View view, ViewGroup.LayoutParams params);

    public abstract void addContentView(View view, ViewGroup.LayoutParams params);

    public abstract View getCurrentFocus();

    public abstract LayoutInflater getLayoutInflater();

    public abstract void setTitle(CharSequence title);

    @Deprecated
    public abstract void setTitleColor(int textColor);

    public abstract void openPanel(int featureId, KeyEvent event);

    public abstract void closePanel(int featureId);

    public abstract void togglePanel(int featureId, KeyEvent event);

    public abstract void invalidatePanelMenu(int featureId);

    public abstract boolean performPanelShortcut(int featureId, int keyCode, KeyEvent event, int flags);

    public abstract boolean performPanelIdentifierAction(int featureId, int id, int flags);

    public abstract void closeAllPanels();

    public abstract boolean performContextMenuIdentifierAction(int id, int flags);

    public abstract void onConfigurationChanged(Configuration newConfig);

    public void setElevation(float elevation) {}

    /** framework-internal (hidden in AOSP). */
    public float getElevation() { return 0f; }

    public void setClipToOutline(boolean clipToOutline) {}

    public void setBackgroundDrawableResource(int resId) { setBackgroundDrawable(mContext.getDrawable(resId)); }

    public abstract void setBackgroundDrawable(Drawable drawable);

    public void setBackgroundBlurRadius(int blurRadius) {}

    public abstract void setFeatureDrawableResource(int featureId, int resId);

    public abstract void setFeatureDrawableUri(int featureId, Uri uri);

    public abstract void setFeatureDrawable(int featureId, Drawable drawable);

    public abstract void setFeatureDrawableAlpha(int featureId, int alpha);

    public abstract void setFeatureInt(int featureId, int value);

    public abstract void takeKeyEvents(boolean get);

    public abstract boolean superDispatchKeyEvent(KeyEvent event);

    public abstract boolean superDispatchKeyShortcutEvent(KeyEvent event);

    public abstract boolean superDispatchTouchEvent(MotionEvent event);

    public abstract boolean superDispatchTrackballEvent(MotionEvent event);

    public abstract boolean superDispatchGenericMotionEvent(MotionEvent event);

    public abstract View getDecorView();

    public abstract View peekDecorView();

    public abstract Bundle saveHierarchyState();

    public abstract void restoreHierarchyState(Bundle savedInstanceState);

    protected abstract void onActive();

    protected final int getFeatures() { return mFeatures; }

    public static int getDefaultFeatures(Context context) {
        return (1 << FEATURE_OPTIONS_PANEL) | (1 << FEATURE_CONTEXT_MENU);
    }

    public boolean hasFeature(int feature) { return (getFeatures() & (1 << feature)) != 0; }

    protected final int getLocalFeatures() { return mLocalFeatures; }

    protected void setDefaultWindowFormat(int format) {
        mDefaultWindowFormat = format;
        if (!mHaveWindowFormat) {
            final WindowManager.LayoutParams attrs = getAttributes();
            attrs.format = format;
            dispatchWindowAttributesChanged(attrs);
        }
    }

    /** framework-internal (hidden in AOSP). */
    protected boolean haveWindowFormat() { return mHaveWindowFormat; }

    public abstract void setChildDrawable(int featureId, Drawable drawable);

    public abstract void setChildInt(int featureId, int value);

    public abstract boolean isShortcutKey(int keyCode, KeyEvent event);

    public abstract void setVolumeControlStream(int streamType);

    public abstract int getVolumeControlStream();

    public void setUiOptions(int uiOptions) {}

    public void setUiOptions(int uiOptions, int mask) {}

    public void setIcon(int resId) {}

    public void setLogo(int resId) {}

    public void setLocalFocus(boolean hasFocus, boolean inTouchMode) {}

    public void injectInputEvent(InputEvent event) {}

    public void setAllowEnterTransitionOverlap(boolean allow) {}

    public boolean getAllowEnterTransitionOverlap() { return true; }

    public void setAllowReturnTransitionOverlap(boolean allow) {}

    public boolean getAllowReturnTransitionOverlap() { return true; }

    public long getTransitionBackgroundFadeDuration() { return 300; }

    public void setTransitionBackgroundFadeDuration(long fadeDurationMillis) {}

    public boolean getSharedElementsUseOverlay() { return true; }

    public void setSharedElementsUseOverlay(boolean sharedElementsUseOverlay) {}

    public abstract int getStatusBarColor();

    public abstract void setStatusBarColor(int color);

    public abstract int getNavigationBarColor();

    public abstract void setNavigationBarColor(int color);

    public void setNavigationBarDividerColor(int dividerColor) {}

    public int getNavigationBarDividerColor() { return 0; }

    public void setStatusBarContrastEnforced(boolean ensureContrast) {}

    public boolean isStatusBarContrastEnforced() { return false; }

    public void setNavigationBarContrastEnforced(boolean enforceContrast) {}

    public boolean isNavigationBarContrastEnforced() { return false; }

    public void setSystemGestureExclusionRects(List<Rect> rects) { mSystemGestureExclusionRects = rects; }

    public List<Rect> getSystemGestureExclusionRects() { return mSystemGestureExclusionRects; }

    public abstract void setDecorCaptionShade(int decorCaptionShade);

    public abstract void setResizingCaptionDrawable(Drawable drawable);
}
