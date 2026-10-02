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
import com.android.internal.view.menu.ContextMenuBuilder;
import com.android.internal.view.menu.MenuBuilder;
import com.android.internal.view.menu.MenuDialogHelper;
import com.android.internal.view.menu.MenuPanel;
import com.android.internal.view.menu.MenuPresenter;
import com.android.internal.widget.DecorContentParent;

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
    private static boolean sLoggedCustomTitle;
    private static final String ACTION_BAR_TAG = "android:ActionBar";

    /** The action bar decor (ActionBarOverlayLayout), or null when the window has none. */
    DecorContentParent mDecorContentParent;
    private android.widget.TextView mTitleView;
    private ActionMenuPresenterCallback mActionMenuPresenterCallback;
    private boolean mInvalidatePanelMenuPosted;
    private int mUiOptions;
    private int mIconRes;
    private int mLogoRes;

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
        if (mTitleView != null) {
            mTitleView.setText(title);
        } else if (mDecorContentParent != null) {
            mDecorContentParent.setWindowTitle(title);
        }
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
    public void setTitleColor(int textColor) {
        if (mTitleView != null) mTitleView.setTextColor(textColor);
        mTitleColor = textColor;
    }

    @Override
    public void setUiOptions(int uiOptions) { mUiOptions = uiOptions; }

    @Override
    public void setUiOptions(int uiOptions, int mask) { mUiOptions = (mUiOptions & ~mask) | (uiOptions & mask); }

    @Override
    public void setIcon(int resId) {
        mIconRes = resId;
        if (mDecorContentParent != null) mDecorContentParent.setIcon(resId);
    }

    @Override
    public void setDefaultIcon(int resId) {
        if (mIconRes != 0) return;
        mIconRes = resId;
        if (mDecorContentParent != null && !mDecorContentParent.hasIcon() && resId != 0) mDecorContentParent.setIcon(resId);
    }

    @Override
    public void setLogo(int resId) {
        mLogoRes = resId;
        if (mDecorContentParent != null) mDecorContentParent.setLogo(resId);
    }

    @Override
    public void setDefaultLogo(int resId) {
        if (mLogoRes != 0) return;
        mLogoRes = resId;
        if (mDecorContentParent != null && !mDecorContentParent.hasLogo() && resId != 0) mDecorContentParent.setLogo(resId);
    }

    // ---------------------------------------------------------------- panels (options menu, context menu)

    private MenuBuilder mOptionsMenu;
    private boolean mOptionsMenuInvalid = true;
    private MenuPanel mOptionsPanel;
    private ContextMenuBuilder mContextMenu;
    private MenuDialogHelper mContextMenuHelper;

    private MenuBuilder.Callback menuCallback(final int featureId) {
        return new MenuBuilder.Callback() {
            public boolean onMenuItemSelected(MenuBuilder menu, android.view.MenuItem item) {
                final Callback cb = getCallback();
                return cb != null && !isDestroyed() && cb.onMenuItemSelected(featureId, item);
            }

            public void onMenuModeChange(MenuBuilder menu) {}
        };
    }

    /** Creates and prepares the options menu; false when the app shows none (AOSP preparePanel). */
    private boolean prepareOptionsMenu() {
        final Callback cb = getCallback();
        if (cb == null || isDestroyed()) return false;
        final boolean actionBar = mDecorContentParent != null;
        if (actionBar) mDecorContentParent.setMenuPrepared();
        if (mOptionsMenu == null || mOptionsMenuInvalid) {
            MenuBuilder menu = new MenuBuilder(actionBar ? actionBarMenuContext() : getContext());
            menu.setCallback(menuCallback(FEATURE_OPTIONS_PANEL));
            if (actionBar) {
                if (mActionMenuPresenterCallback == null) mActionMenuPresenterCallback = new ActionMenuPresenterCallback();
                mDecorContentParent.setMenu(menu, mActionMenuPresenterCallback);
            }
            menu.stopDispatchingItemsChanged();
            if (!cb.onCreatePanelMenu(FEATURE_OPTIONS_PANEL, menu)) {
                mOptionsMenu = null;
                if (actionBar) mDecorContentParent.setMenu(null, mActionMenuPresenterCallback);
                return false;
            }
            mOptionsMenu = menu;
            mOptionsMenuInvalid = false;
        }
        mOptionsMenu.stopDispatchingItemsChanged();
        if (!cb.onPreparePanel(FEATURE_OPTIONS_PANEL, null, mOptionsMenu)) {
            if (actionBar) mDecorContentParent.setMenu(null, mActionMenuPresenterCallback);
            mOptionsMenu.startDispatchingItemsChanged();
            return false;
        }
        mOptionsMenu.startDispatchingItemsChanged();
        return true;
    }

    /** The action bar menu inflates its views with actionBarTheme and actionBarWidgetTheme (AOSP initializePanelMenu). */
    private Context actionBarMenuContext() {
        final Context context = getContext();
        final TypedValue outValue = new TypedValue();
        final android.content.res.Resources.Theme baseTheme = context.getTheme();
        baseTheme.resolveAttribute(android.R.attr.actionBarTheme, outValue, true);
        android.content.res.Resources.Theme widgetTheme = null;
        if (outValue.resourceId != 0) {
            widgetTheme = context.getResources().newTheme();
            widgetTheme.setTo(baseTheme);
            widgetTheme.applyStyle(outValue.resourceId, true);
            widgetTheme.resolveAttribute(android.R.attr.actionBarWidgetTheme, outValue, true);
        } else {
            baseTheme.resolveAttribute(android.R.attr.actionBarWidgetTheme, outValue, true);
        }
        if (outValue.resourceId != 0) {
            if (widgetTheme == null) {
                widgetTheme = context.getResources().newTheme();
                widgetTheme.setTo(baseTheme);
            }
            widgetTheme.applyStyle(outValue.resourceId, true);
        }
        if (widgetTheme == null) return context;
        final android.view.ContextThemeWrapper wrapper = new android.view.ContextThemeWrapper(context, 0);
        wrapper.getTheme().setTo(widgetTheme);
        return wrapper;
    }

    private boolean usesActionBarOverflow() {
        return mDecorContentParent != null && mDecorContentParent.canShowOverflowMenu()
                && !android.view.ViewConfiguration.get(getContext()).hasPermanentMenuKey();
    }

    /** Presenter callback for the action bar's menu (AOSP ActionMenuPresenterCallback). */
    private final class ActionMenuPresenterCallback implements MenuPresenter.Callback {
        private boolean mClosingActionMenu;

        public boolean onOpenSubMenu(MenuBuilder subMenu) {
            final Callback cb = getCallback();
            if (cb != null && !isDestroyed()) {
                cb.onMenuOpened(FEATURE_ACTION_BAR, subMenu);
                return true;
            }
            return false;
        }

        public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
            if (mClosingActionMenu) return;
            mClosingActionMenu = true;
            mDecorContentParent.dismissPopups();
            final Callback cb = getCallback();
            if (cb != null && !isDestroyed()) cb.onPanelClosed(FEATURE_ACTION_BAR, menu);
            mClosingActionMenu = false;
        }
    }

    private final Runnable mInvalidatePanelMenuRunnable = new Runnable() {
        public void run() {
            mInvalidatePanelMenuPosted = false;
            if (mDecorContentParent != null && !isDestroyed()) {
                mOptionsMenuInvalid = true;
                prepareOptionsMenu();
            }
        }
    };

    private final MenuBuilder.CloseListener mOptionsClosed = new MenuBuilder.CloseListener() {
        public void onMenuClosed(MenuBuilder menu, boolean allMenusAreClosing) {
            menu.removeCloseListener(this);
            mOptionsPanel = null;
            final Callback cb = getCallback();
            if (cb != null && !isDestroyed()) cb.onPanelClosed(FEATURE_OPTIONS_PANEL, menu);
        }
    };

    private final MenuBuilder.CloseListener mContextClosed = new MenuBuilder.CloseListener() {
        public void onMenuClosed(MenuBuilder menu, boolean allMenusAreClosing) {
            menu.removeCloseListener(this);
            mContextMenuHelper = null;
            final Callback cb = getCallback();
            if (cb != null && !isDestroyed()) cb.onPanelClosed(FEATURE_CONTEXT_MENU, menu);
        }
    };

    @Override
    public void openPanel(int featureId, KeyEvent event) {
        if (featureId == FEATURE_OPTIONS_PANEL && usesActionBarOverflow()) {
            if (!mDecorContentParent.isOverflowMenuShowing() && prepareOptionsMenu()) mDecorContentParent.showOverflowMenu();
            return;
        }
        if (featureId != FEATURE_OPTIONS_PANEL || mOptionsPanel != null) return;
        if (!prepareOptionsMenu() || !mOptionsMenu.hasVisibleItems()) return;
        final Callback cb = getCallback();
        if (cb != null && !cb.onMenuOpened(FEATURE_OPTIONS_PANEL, mOptionsMenu)) {
            cb.onPanelClosed(FEATURE_OPTIONS_PANEL, mOptionsMenu);
            return;
        }
        mOptionsMenu.addCloseListener(mOptionsClosed);
        mOptionsPanel = new MenuPanel(getContext(), mOptionsMenu, null);
        mOptionsPanel.show();
    }

    @Override
    public void closePanel(int featureId) {
        if (featureId == FEATURE_OPTIONS_PANEL && usesActionBarOverflow()) {
            mDecorContentParent.hideOverflowMenu();
        } else if (featureId == FEATURE_OPTIONS_PANEL) {
            if (mOptionsPanel != null && mOptionsMenu != null) mOptionsMenu.close(true);
        } else if (featureId == FEATURE_CONTEXT_MENU) {
            if (mContextMenuHelper != null && mContextMenu != null) mContextMenu.close(true);
        }
    }

    @Override
    public void togglePanel(int featureId, KeyEvent event) {
        if (featureId == FEATURE_OPTIONS_PANEL && usesActionBarOverflow()) {
            if (mDecorContentParent.isOverflowMenuShowing()) closePanel(featureId);
            else openPanel(featureId, event);
        } else if (featureId == FEATURE_OPTIONS_PANEL && mOptionsPanel != null) closePanel(featureId);
        else openPanel(featureId, event);
    }

    @Override
    public void invalidatePanelMenu(int featureId) {
        if (featureId == FEATURE_OPTIONS_PANEL || featureId == FEATURE_ACTION_BAR) mOptionsMenuInvalid = true;
        // With an action bar the menu is visible, so rebuild it soon (AOSP doInvalidatePanelMenu).
        if (mDecorContentParent != null && mDecor != null && !mInvalidatePanelMenuPosted) {
            mInvalidatePanelMenuPosted = true;
            mDecor.postOnAnimation(mInvalidatePanelMenuRunnable);
        }
    }

    @Override
    public boolean performPanelShortcut(int featureId, int keyCode, KeyEvent event, int flags) {
        if (featureId != FEATURE_OPTIONS_PANEL || event.isSystem()) return false;
        if (!prepareOptionsMenu()) return false;
        return mOptionsMenu.performShortcut(keyCode, event, flags);
    }

    @Override
    public boolean performPanelIdentifierAction(int featureId, int id, int flags) {
        if (featureId != FEATURE_OPTIONS_PANEL || !prepareOptionsMenu()) return false;
        boolean res = mOptionsMenu.performIdentifierAction(id, flags);
        closePanel(featureId);
        return res;
    }

    @Override
    public void closeAllPanels() {
        if (mDecorContentParent != null) mDecorContentParent.dismissPopups();
        closePanel(FEATURE_OPTIONS_PANEL);
        closePanel(FEATURE_CONTEXT_MENU);
    }

    @Override
    public boolean performContextMenuIdentifierAction(int id, int flags) {
        return mContextMenu != null && mContextMenu.performIdentifierAction(id, flags);
    }

    /** framework-internal. Called by the decor when a child asks for its context menu. */
    boolean showContextMenuForChild(View originalView) {
        if (mContextMenuHelper != null) {
            mContextMenu.close(true);
            mContextMenuHelper = null;
        }
        if (mContextMenu == null) {
            mContextMenu = new ContextMenuBuilder(getContext());
            mContextMenu.setCallback(menuCallback(FEATURE_CONTEXT_MENU));
        } else {
            mContextMenu.clear();
            mContextMenu.clearHeader();
        }
        final MenuDialogHelper helper = mContextMenu.showDialog(originalView, originalView.getWindowToken());
        if (helper != null) {
            mContextMenu.addCloseListener(mContextClosed);
            mContextMenuHelper = helper;
        }
        return helper != null;
    }

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

    /** framework-internal. Keys the view hierarchy and callback did not handle (AOSP onKeyDown). */
    protected boolean onKeyDown(int featureId, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.getRepeatCount() == 0) event.startTracking();
            return true;
        }
        return false;
    }

    /** framework-internal. MENU toggles the options panel on release (AOSP onKeyUpPanel). */
    protected boolean onKeyUp(int featureId, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (!event.isCanceled()) togglePanel(featureId < 0 ? FEATURE_OPTIONS_PANEL : featureId, event);
            return true;
        }
        return false;
    }

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
        if (mDecorContentParent != null) {
            SparseArray<Parcelable> actionBarStates = new SparseArray<Parcelable>();
            mDecorContentParent.saveToolbarHierarchyState(actionBarStates);
            outState.putSparseParcelableArray(ACTION_BAR_TAG, actionBarStates);
        }
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
        if (mDecorContentParent != null) {
            SparseArray<Parcelable> actionBarStates = savedInstanceState.getSparseParcelableArray(ACTION_BAR_TAG);
            if (actionBarStates != null) mDecorContentParent.restoreToolbarHierarchyState(actionBarStates);
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
        if (mContentParent == null) {
            mContentParent = generateLayout(mDecor);
            final View dcp = mDecor.findViewById(com.android.internal.util.InternalRes.viewId("decor_content_parent"));
            if (dcp instanceof DecorContentParent) {
                mDecorContentParent = (DecorContentParent) dcp;
                mDecorContentParent.setWindowCallback(getCallback());
                if (mDecorContentParent.getTitle() == null) mDecorContentParent.setWindowTitle(mTitle);
                final int localFeatures = getLocalFeatures();
                for (int i = 0; i < 32; i++) {
                    if ((localFeatures & (1 << i)) != 0) mDecorContentParent.initFeature(i);
                }
                mDecorContentParent.setUiOptions(mUiOptions);
                if (mIconRes != 0 && !mDecorContentParent.hasIcon()) mDecorContentParent.setIcon(mIconRes);
                if (mLogoRes != 0 && !mDecorContentParent.hasLogo()) mDecorContentParent.setLogo(mLogoRes);
                if (!isDestroyed() && mOptionsMenu == null) invalidatePanelMenu(FEATURE_ACTION_BAR);
            } else {
                final View title = mDecor.findViewById(android.R.id.title);
                mTitleView = title instanceof android.widget.TextView ? (android.widget.TextView) title : null;
                if (mTitleView != null) {
                    if ((getLocalFeatures() & (1 << FEATURE_NO_TITLE)) != 0) {
                        final View titleContainer = mDecor.findViewById(
                                com.android.internal.util.InternalRes.viewId("title_container"));
                        if (titleContainer != null) titleContainer.setVisibility(View.GONE);
                        else mTitleView.setVisibility(View.GONE);
                    } else {
                        mTitleView.setText(mTitle);
                    }
                }
            }
        }
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
        params.layoutInDisplayCutoutMode = a.getInt(STYLE_LAYOUT_IN_DISPLAY_CUTOUT_MODE_INDEX,
                params.layoutInDisplayCutoutMode);
        if (a.getBoolean(STYLE_DIM_ENABLED_INDEX, mIsFloating)) {
            if ((getForcedWindowFlags() & WindowManager.LayoutParams.FLAG_DIM_BEHIND) == 0) {
                params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            }
            if (!haveDimAmount()) params.dimAmount = a.getFloat(STYLE_DIM_AMOUNT_INDEX, 0.5f);
        }
        if (params.windowAnimations == 0) params.windowAnimations = a.getResourceId(STYLE_ANIMATION_STYLE_INDEX, 0);
        mStatusBarColor = a.getColor(STYLE_STATUS_BAR_COLOR_INDEX, 0xff000000);
        mNavigationBarColor = a.getColor(STYLE_NAVIGATION_BAR_COLOR_INDEX, 0xff000000);

        // Decor layout (AOSP generateLayout; progress and left/right icon decors are not ported).
        final int features = getLocalFeatures();
        int layoutResource;
        String layoutName;
        if ((features & (1 << FEATURE_CUSTOM_TITLE)) != 0 && !sLoggedCustomTitle) {
            sLoggedCustomTitle = true;
            Log.w(TAG, "custom title decors are not implemented yet; using the plain title");
        }
        if ((features & (1 << FEATURE_NO_TITLE)) == 0) {
            if (mIsFloating) {
                final TypedValue res = new TypedValue();
                context.getTheme().resolveAttribute(com.android.internal.util.InternalRes.attr("dialogTitleDecorLayout"),
                        res, true);
                layoutResource = res.resourceId;
                layoutName = "dialogTitleDecorLayout";
            } else if ((features & (1 << FEATURE_ACTION_BAR)) != 0) {
                final TypedValue res = new TypedValue();
                final int attr = com.android.internal.util.InternalRes.attr("windowActionBarFullscreenDecorLayout");
                layoutResource = attr != 0 && context.getTheme().resolveAttribute(attr, res, true) && res.resourceId != 0
                        ? res.resourceId : internalId(context, "screen_action_bar", "layout");
                layoutName = "action bar decor";
            } else {
                layoutResource = internalId(context, "screen_title", "layout");
                layoutName = "screen_title";
            }
        } else if ((features & (1 << FEATURE_ACTION_MODE_OVERLAY)) != 0) {
            layoutResource = internalId(context, "screen_simple_overlay_action_mode", "layout");
            layoutName = "screen_simple_overlay_action_mode";
        } else {
            layoutResource = internalId(context, "screen_simple", "layout");
            layoutName = "screen_simple";
        }
        View root = null;
        if (layoutResource != 0) {
            try {
                root = mLayoutInflater.inflate(layoutResource, null);
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not inflate " + layoutName, e);
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
            STYLE_LAYOUT_IN_DISPLAY_CUTOUT_MODE_INDEX = 31, STYLE_ANIMATION_STYLE_INDEX = 32;

    /** framework-internal. Window format follows the background opacity, as AOSP does. */
    void updateFormatForBackground(Drawable background) {
        int format = PixelFormat.OPAQUE;
        if (background == null || background.getOpacity() != PixelFormat.OPAQUE || mIsTranslucent || mIsFloating) {
            format = PixelFormat.TRANSLUCENT;
        }
        setDefaultWindowFormat(format);
    }
}
