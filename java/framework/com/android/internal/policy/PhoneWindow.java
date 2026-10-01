package com.android.internal.policy;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.InputQueue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;

/**
 * framework-internal. The standard window (AOSP PhoneWindow): builds the decor
 * from the theme's window attributes (framework screen_* layouts), hosts the
 * content view and routes input through Window.Callback.
 */
public class PhoneWindow extends Window {
    private static final String TAG = "PhoneWindow";
    private static final String FOCUSED_ID_TAG = "android:focusedViewId";
    private static final String VIEWS_TAG = "android:views";

    DecorView mDecor;
    ViewGroup mContentParent;
    ViewGroup mContentRoot;
    private final LayoutInflater mLayoutInflater;
    private boolean mIsFloating;
    private boolean mIsTranslucent;
    private CharSequence mTitle;
    private int mTitleColor;
    private int mBackgroundResource;
    private Drawable mBackgroundDrawable;
    private int mVolumeControlStreamType = -1;
    private int mStatusBarColor;
    private int mNavigationBarColor;
    private boolean mTakeKeyEvents = true;
    private static boolean sLoggedActionBar;

    final TypedValue mMinWidthMajor = new TypedValue();
    final TypedValue mMinWidthMinor = new TypedValue();
    TypedValue mFixedWidthMajor;
    TypedValue mFixedWidthMinor;
    TypedValue mFixedHeightMajor;
    TypedValue mFixedHeightMinor;

    public PhoneWindow(Context context) {
        super(context);
        mLayoutInflater = LayoutInflater.from(context);
    }

    @Override
    public final void setContainer(Window container) { super.setContainer(container); }

    @Override
    public boolean requestFeature(int featureId) {
        if (mContentParent != null) throw new android.util.AndroidRuntimeException("requestFeature() must be called before adding content");
        final int features = getFeatures();
        final int newFeatures = features | (1 << featureId);
        if ((newFeatures & (1 << FEATURE_CUSTOM_TITLE)) != 0 && (newFeatures & ~(1 << FEATURE_CUSTOM_TITLE)
                & ((1 << FEATURE_LEFT_ICON) | (1 << FEATURE_RIGHT_ICON) | (1 << FEATURE_PROGRESS))) != 0) {
            throw new android.util.AndroidRuntimeException("You cannot combine custom titles with other title features");
        }
        if ((features & (1 << FEATURE_NO_TITLE)) != 0 && featureId == FEATURE_ACTION_BAR) return false;
        if ((features & (1 << FEATURE_ACTION_BAR)) != 0 && featureId == FEATURE_NO_TITLE) removeFeature(FEATURE_ACTION_BAR);
        return super.requestFeature(featureId);
    }

    @Override
    public LayoutInflater getLayoutInflater() { return mLayoutInflater; }

    @Override
    public void setContentView(int layoutResID) {
        if (mContentParent == null) installDecor();
        else mContentParent.removeAllViews();
        mLayoutInflater.inflate(layoutResID, mContentParent);
        mContentParent.requestApplyInsets();
        final Callback cb = getCallback();
        if (cb != null && !isDestroyed()) cb.onContentChanged();
    }

    @Override
    public void setContentView(View view) {
        setContentView(view, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        if (mContentParent == null) installDecor();
        else mContentParent.removeAllViews();
        mContentParent.addView(view, params);
        mContentParent.requestApplyInsets();
        final Callback cb = getCallback();
        if (cb != null && !isDestroyed()) cb.onContentChanged();
    }

    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        if (mContentParent == null) installDecor();
        mContentParent.addView(view, params);
        mContentParent.requestApplyInsets();
        final Callback cb = getCallback();
        if (cb != null && !isDestroyed()) cb.onContentChanged();
    }

    @Override
    public View getCurrentFocus() { return mDecor != null ? mDecor.findFocus() : null; }

    @Override
    public void takeSurface(SurfaceHolder.Callback2 callback) {}

    @Override
    public void takeInputQueue(InputQueue.Callback callback) {}

    @Override
    public boolean isFloating() { return mIsFloating; }

    /** framework-internal (hidden in AOSP). */
    public boolean isTranslucent() { return mIsTranslucent; }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        WindowManager.LayoutParams params = getAttributes();
        if (!android.text.TextUtils.equals(title, params.getTitle())) {
            params.setTitle(title);
            dispatchWindowAttributesChanged(params);
        }
    }

    /** framework-internal. */
    public CharSequence getTitle() { return mTitle; }

    @Override
    @Deprecated
    public void setTitleColor(int textColor) { mTitleColor = textColor; }

    // Options and context panels are TODO(WS4) (menus, action bar).
    @Override
    public void openPanel(int featureId, KeyEvent event) {}

    @Override
    public void closePanel(int featureId) {}

    @Override
    public void togglePanel(int featureId, KeyEvent event) {}

    @Override
    public void invalidatePanelMenu(int featureId) {}

    @Override
    public boolean performPanelShortcut(int featureId, int keyCode, KeyEvent event, int flags) { return false; }

    @Override
    public boolean performPanelIdentifierAction(int featureId, int id, int flags) { return false; }

    @Override
    public void closeAllPanels() {}

    @Override
    public boolean performContextMenuIdentifierAction(int id, int flags) { return false; }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {}

    @Override
    public void setBackgroundDrawable(Drawable drawable) {
        if (drawable != mBackgroundDrawable || mBackgroundResource != 0) {
            mBackgroundResource = 0;
            mBackgroundDrawable = drawable;
            if (mDecor != null) mDecor.setWindowBackground(drawable);
        }
    }

    @Override
    public void setFeatureDrawableResource(int featureId, int resId) {}

    @Override
    public void setFeatureDrawableUri(int featureId, Uri uri) {}

    @Override
    public void setFeatureDrawable(int featureId, Drawable drawable) {}

    @Override
    public void setFeatureDrawableAlpha(int featureId, int alpha) {}

    @Override
    public void setFeatureInt(int featureId, int value) {}

    @Override
    public void takeKeyEvents(boolean get) { mTakeKeyEvents = get; }

    @Override
    public boolean superDispatchKeyEvent(KeyEvent event) { return mDecor.superDispatchKeyEvent(event); }

    @Override
    public boolean superDispatchKeyShortcutEvent(KeyEvent event) { return mDecor.superDispatchKeyShortcutEvent(event); }

    @Override
    public boolean superDispatchTouchEvent(MotionEvent event) { return mDecor.superDispatchTouchEvent(event); }

    @Override
    public boolean superDispatchTrackballEvent(MotionEvent event) { return mDecor.superDispatchTrackballEvent(event); }

    @Override
    public boolean superDispatchGenericMotionEvent(MotionEvent event) {
        return mDecor.superDispatchGenericMotionEvent(event);
    }

    /** framework-internal. Keys the view hierarchy and callback did not handle. */
    protected boolean onKeyDown(int featureId, int keyCode, KeyEvent event) { return false; }

    /** framework-internal. */
    protected boolean onKeyUp(int featureId, int keyCode, KeyEvent event) { return false; }

    @Override
    public final View getDecorView() {
        if (mDecor == null) installDecor();
        return mDecor;
    }

    @Override
    public final View peekDecorView() { return mDecor; }

    @Override
    public Bundle saveHierarchyState() {
        Bundle outState = new Bundle();
        if (mContentParent == null) return outState;
        SparseArray<Parcelable> states = new SparseArray<Parcelable>();
        mContentParent.saveHierarchyState(states);
        outState.putSparseParcelableArray(VIEWS_TAG, states);
        final View focusedView = mContentParent.findFocus();
        if (focusedView != null && focusedView.getId() != View.NO_ID) outState.putInt(FOCUSED_ID_TAG, focusedView.getId());
        return outState;
    }

    @Override
    public void restoreHierarchyState(Bundle savedInstanceState) {
        if (mContentParent == null) return;
        SparseArray<Parcelable> savedStates = savedInstanceState.getSparseParcelableArray(VIEWS_TAG);
        if (savedStates != null) mContentParent.restoreHierarchyState(savedStates);
        int focusedViewId = savedInstanceState.getInt(FOCUSED_ID_TAG, View.NO_ID);
        if (focusedViewId != View.NO_ID) {
            View needsFocus = mContentParent.findViewById(focusedViewId);
            if (needsFocus != null) needsFocus.requestFocus();
        }
    }

    @Override
    protected void onActive() {}

    @Override
    public void setChildDrawable(int featureId, Drawable drawable) {}

    @Override
    public void setChildInt(int featureId, int value) {}

    @Override
    public boolean isShortcutKey(int keyCode, KeyEvent event) { return false; }

    @Override
    public void setVolumeControlStream(int streamType) { mVolumeControlStreamType = streamType; }

    @Override
    public int getVolumeControlStream() { return mVolumeControlStreamType; }

    @Override
    public int getStatusBarColor() { return mStatusBarColor; }

    @Override
    public void setStatusBarColor(int color) { mStatusBarColor = color; }

    @Override
    public int getNavigationBarColor() { return mNavigationBarColor; }

    @Override
    public void setNavigationBarColor(int color) { mNavigationBarColor = color; }

    @Override
    public void setDecorCaptionShade(int decorCaptionShade) {}

    @Override
    public void setResizingCaptionDrawable(Drawable drawable) {}

    @Override
    protected void dispatchWindowAttributesChanged(WindowManager.LayoutParams attrs) {
        super.dispatchWindowAttributesChanged(attrs);
        if (mDecor != null && mDecor.getParent() != null) {
            android.view.WindowManagerImpl.getDefault().updateViewLayout(mDecor, attrs);
        }
    }

    // ---------------------------------------------------------------- decor construction

    private void installDecor() {
        if (mDecor == null) {
            mDecor = new DecorView(getContext(), this);
            mDecor.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            mDecor.setIsRootNamespace(true);
        }
        if (mContentParent == null) mContentParent = generateLayout(mDecor);
    }

    private static int internalId(Context context, String name, String type) {
        return context.getResources().getIdentifier(name, type, "android");
    }

    protected ViewGroup generateLayout(DecorView decor) {
        final Context context = getContext();
        TypedArray a = getWindowStyle();
        mIsFloating = a.getBoolean(STYLE_IS_FLOATING_INDEX, false);
        final WindowManager.LayoutParams params = getAttributes();
        if (mIsFloating) {
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            setFlags(0, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
        }
        if (a.getBoolean(STYLE_NO_TITLE_INDEX, false)) {
            requestFeature(FEATURE_NO_TITLE);
        } else if (a.getBoolean(STYLE_ACTION_BAR_INDEX, false)) {
            requestFeature(FEATURE_ACTION_BAR);
        }
        if (a.getBoolean(STYLE_ACTION_BAR_OVERLAY_INDEX, false)) requestFeature(FEATURE_ACTION_BAR_OVERLAY);
        if (a.getBoolean(STYLE_ACTION_MODE_OVERLAY_INDEX, false)) requestFeature(FEATURE_ACTION_MODE_OVERLAY);
        if (a.getBoolean(STYLE_FULLSCREEN_INDEX, false)) {
            setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN & (~getForcedWindowFlags()));
        }
        mIsTranslucent = a.getBoolean(STYLE_IS_TRANSLUCENT_INDEX, false);
        a.getValue(STYLE_MIN_WIDTH_MAJOR_INDEX, mMinWidthMajor);
        a.getValue(STYLE_MIN_WIDTH_MINOR_INDEX, mMinWidthMinor);
        if (a.hasValue(STYLE_FIXED_WIDTH_MAJOR_INDEX)) {
            mFixedWidthMajor = new TypedValue();
            a.getValue(STYLE_FIXED_WIDTH_MAJOR_INDEX, mFixedWidthMajor);
        }
        if (a.hasValue(STYLE_FIXED_WIDTH_MINOR_INDEX)) {
            mFixedWidthMinor = new TypedValue();
            a.getValue(STYLE_FIXED_WIDTH_MINOR_INDEX, mFixedWidthMinor);
        }
        if (a.hasValue(STYLE_FIXED_HEIGHT_MAJOR_INDEX)) {
            mFixedHeightMajor = new TypedValue();
            a.getValue(STYLE_FIXED_HEIGHT_MAJOR_INDEX, mFixedHeightMajor);
        }
        if (a.hasValue(STYLE_FIXED_HEIGHT_MINOR_INDEX)) {
            mFixedHeightMinor = new TypedValue();
            a.getValue(STYLE_FIXED_HEIGHT_MINOR_INDEX, mFixedHeightMinor);
        }
        if (a.getBoolean(STYLE_CLOSE_ON_TOUCH_OUTSIDE_INDEX, false)) setCloseOnTouchOutsideIfNotSet(true);
        if (!hasSoftInputMode()) params.softInputMode = a.getInt(STYLE_SOFT_INPUT_MODE_INDEX, params.softInputMode);
        if (a.getBoolean(STYLE_DIM_ENABLED_INDEX, mIsFloating)) {
            if ((getForcedWindowFlags() & WindowManager.LayoutParams.FLAG_DIM_BEHIND) == 0) {
                params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            }
            if (!haveDimAmount()) params.dimAmount = a.getFloat(STYLE_DIM_AMOUNT_INDEX, 0.5f);
        }
        if (params.windowAnimations == 0) params.windowAnimations = a.getResourceId(STYLE_ANIMATION_STYLE_INDEX, 0);
        mStatusBarColor = a.getColor(STYLE_STATUS_BAR_COLOR_INDEX, 0xff000000);
        mNavigationBarColor = a.getColor(STYLE_NAVIGATION_BAR_COLOR_INDEX, 0xff000000);

        // Decor layout: the action bar and title decors need ActionBar and TextView (TODO(WS4), TODO(WS2)),
        // so every window uses screen_simple for now.
        int features = getLocalFeatures();
        if ((features & ((1 << FEATURE_ACTION_BAR) | (1 << FEATURE_CUSTOM_TITLE))) != 0 && !sLoggedActionBar) {
            sLoggedActionBar = true;
            Log.w(TAG, "action bar and title decors are not implemented yet; using screen_simple");
        }
        int layoutResource = internalId(context, "screen_simple", "layout");
        View root = null;
        if (layoutResource != 0) {
            try {
                root = mLayoutInflater.inflate(layoutResource, null);
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not inflate screen_simple: " + e);
            }
        }
        if (root == null) {
            android.widget.FrameLayout content = new android.widget.FrameLayout(context);
            content.setId(ID_ANDROID_CONTENT);
            root = content;
        }
        decor.addView(root, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        mContentRoot = (ViewGroup) root;
        ViewGroup contentParent = (ViewGroup) decor.findViewById(ID_ANDROID_CONTENT);
        if (contentParent == null) throw new RuntimeException("Window couldn't find content container view");

        if (getContainer() == null) {
            final Drawable background;
            if (mBackgroundDrawable != null) {
                background = mBackgroundDrawable;
            } else {
                if (mBackgroundResource == 0) mBackgroundResource = a.getResourceId(STYLE_BACKGROUND_INDEX, 0);
                background = mBackgroundResource != 0 ? context.getDrawable(mBackgroundResource) : null;
            }
            decor.setWindowBackground(background);
            final Drawable frame = a.hasValue(STYLE_FRAME_INDEX) ? a.getDrawable(STYLE_FRAME_INDEX) : null;
            decor.setWindowFrame(frame);
            if (mTitle != null) setTitle(mTitle);
        }
        return contentParent;
    }

    // indexes into Window.WINDOW_ATTRS (kept in sync with Window)
    private static final int STYLE_BACKGROUND_INDEX = 0, STYLE_FRAME_INDEX = 1, STYLE_NO_TITLE_INDEX = 2,
            STYLE_FULLSCREEN_INDEX = 3, STYLE_IS_FLOATING_INDEX = 4, STYLE_IS_TRANSLUCENT_INDEX = 5,
            STYLE_ACTION_BAR_INDEX = 8, STYLE_ACTION_BAR_OVERLAY_INDEX = 9, STYLE_ACTION_MODE_OVERLAY_INDEX = 10,
            STYLE_SOFT_INPUT_MODE_INDEX = 11, STYLE_CLOSE_ON_TOUCH_OUTSIDE_INDEX = 12,
            STYLE_MIN_WIDTH_MAJOR_INDEX = 13, STYLE_MIN_WIDTH_MINOR_INDEX = 14, STYLE_DIM_ENABLED_INDEX = 15,
            STYLE_DIM_AMOUNT_INDEX = 16, STYLE_STATUS_BAR_COLOR_INDEX = 18, STYLE_NAVIGATION_BAR_COLOR_INDEX = 19,
            STYLE_FIXED_WIDTH_MAJOR_INDEX = 22, STYLE_FIXED_WIDTH_MINOR_INDEX = 23,
            STYLE_FIXED_HEIGHT_MAJOR_INDEX = 24, STYLE_FIXED_HEIGHT_MINOR_INDEX = 25,
            STYLE_ANIMATION_STYLE_INDEX = 32;

    /** framework-internal. Window format follows the background opacity, as AOSP does. */
    void updateFormatForBackground(Drawable background) {
        int format = PixelFormat.OPAQUE;
        if (background == null || background.getOpacity() != PixelFormat.OPAQUE || mIsTranslucent || mIsFloating) {
            format = PixelFormat.TRANSLUCENT;
        }
        setDefaultWindowFormat(format);
    }
}
