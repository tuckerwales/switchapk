package com.android.internal.app;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.SpinnerAdapter;
import android.widget.Toolbar;
import com.android.internal.util.InternalRes;
import com.android.internal.view.ActionBarPolicy;
import com.android.internal.view.menu.MenuBuilder;
import com.android.internal.widget.ActionBarContainer;
import com.android.internal.widget.ActionBarContextView;
import com.android.internal.widget.ActionBarOverlayLayout;
import com.android.internal.widget.DecorToolbar;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * framework-internal. Port of AOSP WindowDecorActionBar: the ActionBar of a
 * window whose decor is the action bar layout (screen_toolbar). Primary
 * action modes replace the bar with the context bar. Show, hide and mode
 * changes are immediate (no animators until WS5). Tabs are tracked and
 * their listeners run, but no tab strip is drawn (ScrollingTabContainerView
 * is not ported).
 */
public class WindowDecorActionBar extends ActionBar implements ActionBarOverlayLayout.ActionBarVisibilityCallback {
    private static final String TAG = "WindowDecorActionBar";
    private static final int CONTEXT_DISPLAY_NORMAL = 0;
    private static final int CONTEXT_DISPLAY_SPLIT = 1;
    private static final int INVALID_POSITION = -1;

    private Context mContext;
    private Context mThemedContext;
    private Activity mActivity;
    private Dialog mDialog;

    private ActionBarOverlayLayout mOverlayLayout;
    private ActionBarContainer mContainerView;
    private DecorToolbar mDecorToolbar;
    private ActionBarContextView mContextView;
    private ActionBarContainer mSplitView;
    private View mContentView;

    private final ArrayList<TabImpl> mTabs = new ArrayList<TabImpl>();
    private TabImpl mSelectedTab;
    private int mSavedTabPosition = INVALID_POSITION;
    private static boolean sLoggedTabs;

    private boolean mDisplayHomeAsUpSet;

    ActionModeImpl mActionMode;
    ActionMode mDeferredDestroyActionMode;
    ActionMode.Callback mDeferredModeDestroyCallback;

    private boolean mLastMenuVisibility;
    private final ArrayList<OnMenuVisibilityListener> mMenuVisibilityListeners = new ArrayList<OnMenuVisibilityListener>();

    private int mContextDisplayMode;
    private boolean mHasEmbeddedTabs;
    private int mCurWindowVisibility = View.VISIBLE;
    private boolean mContentAnimations = true;
    private boolean mHiddenByApp;
    private boolean mHiddenBySystem;
    private boolean mShowingForMode;
    private boolean mNowShowing = true;
    private boolean mShowHideAnimationEnabled;
    boolean mHideOnContentScroll;

    public WindowDecorActionBar(Activity activity) {
        mActivity = activity;
        final Window window = activity.getWindow();
        final View decor = window.getDecorView();
        final boolean overlayMode = mActivity.getWindow().hasFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        init(decor);
        if (!overlayMode) mContentView = decor.findViewById(android.R.id.content);
    }

    public WindowDecorActionBar(Dialog dialog) {
        mDialog = dialog;
        init(dialog.getWindow().getDecorView());
    }

    public WindowDecorActionBar(View layout) { init(layout); }

    private void init(View decor) {
        mOverlayLayout = (ActionBarOverlayLayout) decor.findViewById(InternalRes.viewId("decor_content_parent"));
        if (mOverlayLayout != null) mOverlayLayout.setActionBarVisibilityCallback(this);
        mDecorToolbar = getDecorToolbar(decor.findViewById(InternalRes.viewId("action_bar")));
        mContextView = (ActionBarContextView) decor.findViewById(InternalRes.viewId("action_context_bar"));
        mContainerView = (ActionBarContainer) decor.findViewById(InternalRes.viewId("action_bar_container"));
        final View split = decor.findViewById(InternalRes.viewId("split_action_bar"));
        mSplitView = split instanceof ActionBarContainer ? (ActionBarContainer) split : null;
        if (mDecorToolbar == null || mContextView == null || mContainerView == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " can only be used with a compatible window decor layout");
        }
        mContext = mDecorToolbar.getContext();
        mContextDisplayMode = mDecorToolbar.isSplit() ? CONTEXT_DISPLAY_SPLIT : CONTEXT_DISPLAY_NORMAL;

        final int current = mDecorToolbar.getDisplayOptions();
        final boolean homeAsUp = (current & DISPLAY_HOME_AS_UP) != 0;
        if (homeAsUp) mDisplayHomeAsUpSet = true;

        final ActionBarPolicy abp = ActionBarPolicy.get(mContext);
        setHomeButtonEnabled(abp.enableHomeButtonByDefault() || homeAsUp);
        setHasEmbeddedTabs(abp.hasEmbeddedTabs());

        final TypedArray a = mContext.obtainStyledAttributes(null, new int[] {
                android.R.attr.hideOnContentScroll, android.R.attr.elevation}, android.R.attr.actionBarStyle, 0);
        if (a.getBoolean(0, false)) setHideOnContentScrollEnabled(true);
        final int elevation = a.getDimensionPixelSize(1, 0);
        if (elevation != 0) setElevation(elevation);
        a.recycle();
    }

    private static DecorToolbar getDecorToolbar(View view) {
        if (view instanceof DecorToolbar) return (DecorToolbar) view;
        if (view instanceof Toolbar) return ((Toolbar) view).getWrapper();
        throw new IllegalStateException("Can't make a decor toolbar out of "
                + (view != null ? view.getClass().getSimpleName() : "null"));
    }

    @Override
    public void setElevation(float elevation) { mContainerView.setElevation(elevation); }

    @Override
    public float getElevation() { return mContainerView.getElevation(); }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        setHasEmbeddedTabs(ActionBarPolicy.get(mContext).hasEmbeddedTabs());
    }

    private void setHasEmbeddedTabs(boolean hasEmbeddedTabs) {
        mHasEmbeddedTabs = hasEmbeddedTabs;
        final boolean isInTabMode = getNavigationMode() == NAVIGATION_MODE_TABS;
        mDecorToolbar.setCollapsible(!mHasEmbeddedTabs && isInTabMode);
        if (mOverlayLayout != null) mOverlayLayout.setHasNonEmbeddedTabs(!mHasEmbeddedTabs && isInTabMode);
    }

    @Override
    public void onWindowVisibilityChanged(int visibility) { mCurWindowVisibility = visibility; }

    @Override
    public void setShowHideAnimationEnabled(boolean enabled) { mShowHideAnimationEnabled = enabled; }

    @Override
    public void addOnMenuVisibilityListener(OnMenuVisibilityListener listener) { mMenuVisibilityListeners.add(listener); }

    @Override
    public void removeOnMenuVisibilityListener(OnMenuVisibilityListener listener) {
        mMenuVisibilityListeners.remove(listener);
    }

    @Override
    public void dispatchMenuVisibilityChanged(boolean isVisible) {
        if (isVisible == mLastMenuVisibility) return;
        mLastMenuVisibility = isVisible;
        final int count = mMenuVisibilityListeners.size();
        for (int i = 0; i < count; i++) mMenuVisibilityListeners.get(i).onMenuVisibilityChanged(isVisible);
    }

    @Override
    public void setCustomView(int resId) {
        setCustomView(LayoutInflater.from(getThemedContext()).inflate(resId, mDecorToolbar.getViewGroup(), false));
    }

    @Override
    public void setDisplayUseLogoEnabled(boolean useLogo) { setDisplayOptions(useLogo ? DISPLAY_USE_LOGO : 0, DISPLAY_USE_LOGO); }

    @Override
    public void setDisplayShowHomeEnabled(boolean showHome) {
        setDisplayOptions(showHome ? DISPLAY_SHOW_HOME : 0, DISPLAY_SHOW_HOME);
    }

    @Override
    public void setDisplayHomeAsUpEnabled(boolean showHomeAsUp) {
        setDisplayOptions(showHomeAsUp ? DISPLAY_HOME_AS_UP : 0, DISPLAY_HOME_AS_UP);
    }

    @Override
    public void setDisplayShowTitleEnabled(boolean showTitle) {
        setDisplayOptions(showTitle ? DISPLAY_SHOW_TITLE : 0, DISPLAY_SHOW_TITLE);
    }

    @Override
    public void setDisplayShowCustomEnabled(boolean showCustom) {
        setDisplayOptions(showCustom ? DISPLAY_SHOW_CUSTOM : 0, DISPLAY_SHOW_CUSTOM);
    }

    @Override
    public void setHomeButtonEnabled(boolean enable) { mDecorToolbar.setHomeButtonEnabled(enable); }

    @Override
    public void setTitle(int resId) { setTitle(mContext.getString(resId)); }

    @Override
    public void setSubtitle(int resId) { setSubtitle(mContext.getString(resId)); }

    @Override
    public void setSelectedNavigationItem(int position) {
        switch (mDecorToolbar.getNavigationMode()) {
            case NAVIGATION_MODE_TABS:
                selectTab(mTabs.get(position));
                break;
            case NAVIGATION_MODE_LIST:
                mDecorToolbar.setDropdownSelectedPosition(position);
                break;
            default:
                throw new IllegalStateException("setSelectedNavigationIndex not valid for current navigation mode");
        }
    }

    @Override
    public void removeAllTabs() { cleanupTabs(); }

    private void cleanupTabs() {
        if (mSelectedTab != null) selectTab(null);
        mTabs.clear();
        mSavedTabPosition = INVALID_POSITION;
    }

    @Override
    public void setTitle(CharSequence title) { mDecorToolbar.setTitle(title); }

    @Override
    public void setWindowTitle(CharSequence title) { mDecorToolbar.setWindowTitle(title); }

    @Override
    public boolean requestFocus() {
        final ViewGroup viewGroup = mDecorToolbar.getViewGroup();
        if (viewGroup != null && !viewGroup.hasFocus()) {
            viewGroup.requestFocus();
            return true;
        }
        return false;
    }

    @Override
    public void setSubtitle(CharSequence subtitle) { mDecorToolbar.setSubtitle(subtitle); }

    @Override
    public void setDisplayOptions(int options) {
        if ((options & DISPLAY_HOME_AS_UP) != 0) mDisplayHomeAsUpSet = true;
        mDecorToolbar.setDisplayOptions(options);
    }

    @Override
    public void setDisplayOptions(int options, int mask) {
        final int current = mDecorToolbar.getDisplayOptions();
        if ((mask & DISPLAY_HOME_AS_UP) != 0) mDisplayHomeAsUpSet = true;
        mDecorToolbar.setDisplayOptions((options & mask) | (current & ~mask));
    }

    @Override
    public void setBackgroundDrawable(Drawable d) { mContainerView.setPrimaryBackground(d); }

    @Override
    public void setStackedBackgroundDrawable(Drawable d) { mContainerView.setStackedBackground(d); }

    @Override
    public void setSplitBackgroundDrawable(Drawable d) {
        if (mSplitView != null) mSplitView.setSplitBackground(d);
    }

    @Override
    public View getCustomView() { return mDecorToolbar.getCustomView(); }

    @Override
    public CharSequence getTitle() { return mDecorToolbar.getTitle(); }

    @Override
    public CharSequence getSubtitle() { return mDecorToolbar.getSubtitle(); }

    @Override
    public int getNavigationMode() { return mDecorToolbar.getNavigationMode(); }

    @Override
    public int getDisplayOptions() { return mDecorToolbar.getDisplayOptions(); }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback) {
        if (mActionMode != null) mActionMode.finish();
        if (mOverlayLayout != null) mOverlayLayout.setHideOnContentScrollEnabled(false);
        mContextView.killMode();
        final ActionModeImpl mode = new ActionModeImpl(mContextView.getContext(), callback);
        if (mode.dispatchOnCreate()) {
            mActionMode = mode;
            mode.invalidate();
            mContextView.initForMode(mode);
            animateToMode(true);
            if (mSplitView != null && mContextDisplayMode == CONTEXT_DISPLAY_SPLIT
                    && mSplitView.getVisibility() != View.VISIBLE) {
                mSplitView.setVisibility(View.VISIBLE);
                if (mOverlayLayout != null) mOverlayLayout.requestApplyInsets();
            }
            return mode;
        }
        return null;
    }

    void completeDeferredDestroyActionMode() {
        if (mDeferredModeDestroyCallback != null) {
            mDeferredModeDestroyCallback.onDestroyActionMode(mDeferredDestroyActionMode);
            mDeferredDestroyActionMode = null;
            mDeferredModeDestroyCallback = null;
        }
    }

    private void configureTab(Tab tab, int position) {
        final TabImpl tabi = (TabImpl) tab;
        if (tabi.getCallback() == null) throw new IllegalStateException("Action Bar Tab must have a Callback");
        tabi.setPosition(position);
        mTabs.add(position, tabi);
        final int count = mTabs.size();
        for (int i = position + 1; i < count; i++) mTabs.get(i).setPosition(i);
    }

    @Override
    public void addTab(Tab tab) { addTab(tab, mTabs.isEmpty()); }

    @Override
    public void addTab(Tab tab, int position) { addTab(tab, position, mTabs.isEmpty()); }

    @Override
    public void addTab(Tab tab, boolean setSelected) {
        configureTab(tab, mTabs.size());
        if (setSelected) selectTab(tab);
    }

    @Override
    public void addTab(Tab tab, int position, boolean setSelected) {
        configureTab(tab, position);
        if (setSelected) selectTab(tab);
    }

    @Override
    public Tab newTab() { return new TabImpl(); }

    @Override
    public void removeTab(Tab tab) { removeTabAt(tab.getPosition()); }

    @Override
    public void removeTabAt(int position) {
        final int selectedTabPosition = mSelectedTab != null ? mSelectedTab.getPosition() : mSavedTabPosition;
        final TabImpl removedTab = mTabs.remove(position);
        if (removedTab != null) removedTab.setPosition(-1);
        final int newTabCount = mTabs.size();
        for (int i = position; i < newTabCount; i++) mTabs.get(i).setPosition(i);
        if (selectedTabPosition == position) selectTab(mTabs.isEmpty() ? null : mTabs.get(Math.max(0, position - 1)));
    }

    @Override
    public void selectTab(Tab tab) {
        if (getNavigationMode() != NAVIGATION_MODE_TABS) {
            mSavedTabPosition = tab != null ? tab.getPosition() : INVALID_POSITION;
            return;
        }
        final FragmentTransaction trans = mActivity != null
                ? mActivity.getFragmentManager().beginTransaction().disallowAddToBackStack() : null;
        if (mSelectedTab == tab) {
            if (mSelectedTab != null) mSelectedTab.getCallback().onTabReselected(mSelectedTab, trans);
        } else {
            if (mSelectedTab != null) mSelectedTab.getCallback().onTabUnselected(mSelectedTab, trans);
            mSelectedTab = (TabImpl) tab;
            if (mSelectedTab != null) mSelectedTab.getCallback().onTabSelected(mSelectedTab, trans);
        }
        if (trans != null && !trans.isEmpty()) trans.commit();
    }

    @Override
    public Tab getSelectedTab() { return mSelectedTab; }

    @Override
    public int getHeight() { return mContainerView.getHeight(); }

    @Override
    public void enableContentAnimations(boolean enabled) { mContentAnimations = enabled; }

    @Override
    public void show() {
        if (mHiddenByApp) {
            mHiddenByApp = false;
            updateVisibility(false);
        }
    }

    private void showForActionMode() {
        if (!mShowingForMode) {
            mShowingForMode = true;
            if (mOverlayLayout != null) mOverlayLayout.setShowingForActionMode(true);
            updateVisibility(false);
        }
    }

    @Override
    public void showForSystem() {
        if (mHiddenBySystem) {
            mHiddenBySystem = false;
            updateVisibility(true);
        }
    }

    @Override
    public void hide() {
        if (!mHiddenByApp) {
            mHiddenByApp = true;
            updateVisibility(false);
        }
    }

    private void hideForActionMode() {
        if (mShowingForMode) {
            mShowingForMode = false;
            if (mOverlayLayout != null) mOverlayLayout.setShowingForActionMode(false);
            updateVisibility(false);
        }
    }

    @Override
    public void hideForSystem() {
        if (!mHiddenBySystem) {
            mHiddenBySystem = true;
            updateVisibility(true);
        }
    }

    @Override
    public void setHideOnContentScrollEnabled(boolean hideOnContentScroll) {
        if (hideOnContentScroll && (mOverlayLayout == null || !mOverlayLayout.isInOverlayMode())) {
            throw new IllegalStateException("Action bar must be in overlay mode "
                    + "(Window.FEATURE_OVERLAY_ACTION_BAR) to enable hide on content scroll");
        }
        mHideOnContentScroll = hideOnContentScroll;
        mOverlayLayout.setHideOnContentScrollEnabled(hideOnContentScroll);
    }

    @Override
    public boolean isHideOnContentScrollEnabled() {
        return mOverlayLayout != null && mOverlayLayout.isHideOnContentScrollEnabled();
    }

    @Override
    public int getHideOffset() { return mOverlayLayout != null ? mOverlayLayout.getActionBarHideOffset() : 0; }

    @Override
    public void setHideOffset(int offset) {
        if (offset != 0 && (mOverlayLayout == null || !mOverlayLayout.isInOverlayMode())) {
            throw new IllegalStateException("Action bar must be in overlay mode "
                    + "(Window.FEATURE_OVERLAY_ACTION_BAR) to set a non-zero hide offset");
        }
        if (mOverlayLayout != null) mOverlayLayout.setActionBarHideOffset(offset);
    }

    private static boolean checkShowingFlags(boolean hiddenByApp, boolean hiddenBySystem, boolean showingForMode) {
        if (showingForMode) return true;
        return !(hiddenByApp || hiddenBySystem);
    }

    private void updateVisibility(boolean fromSystem) {
        final boolean shown = checkShowingFlags(mHiddenByApp, mHiddenBySystem, mShowingForMode);
        if (shown) {
            if (!mNowShowing) {
                mNowShowing = true;
                doShow(fromSystem);
            }
        } else {
            if (mNowShowing) {
                mNowShowing = false;
                doHide(fromSystem);
            }
        }
    }

    public void doShow(boolean fromSystem) {
        mContainerView.setVisibility(View.VISIBLE);
        mContainerView.setAlpha(1);
        mContainerView.setTranslationY(0);
        if (mContentAnimations && mContentView != null) mContentView.setTranslationY(0);
        if (mSplitView != null && mContextDisplayMode == CONTEXT_DISPLAY_SPLIT) {
            mSplitView.setAlpha(1);
            mSplitView.setTranslationY(0);
            mSplitView.setVisibility(View.VISIBLE);
        }
        if (mOverlayLayout != null) mOverlayLayout.requestApplyInsets();
    }

    public void doHide(boolean fromSystem) {
        if (mContentAnimations && mContentView != null) mContentView.setTranslationY(0);
        mContainerView.setVisibility(View.GONE);
        mContainerView.setTransitioning(false);
        if (mSplitView != null && mContextDisplayMode == CONTEXT_DISPLAY_SPLIT) mSplitView.setVisibility(View.GONE);
        completeDeferredDestroyActionMode();
        if (mOverlayLayout != null) mOverlayLayout.requestApplyInsets();
    }

    @Override
    public boolean isShowing() {
        final int height = getHeight();
        return mNowShowing && (height == 0 || getHideOffset() < height);
    }

    void animateToMode(boolean toActionMode) {
        if (toActionMode) {
            showForActionMode();
        } else {
            hideForActionMode();
        }
        if (toActionMode) {
            mDecorToolbar.setVisibility(View.INVISIBLE);
            mContextView.setVisibility(View.VISIBLE);
        } else {
            mDecorToolbar.setVisibility(View.VISIBLE);
            mContextView.setVisibility(View.GONE);
        }
    }

    @Override
    public Context getThemedContext() {
        if (mThemedContext == null) {
            final TypedValue outValue = new TypedValue();
            final android.content.res.Resources.Theme currentTheme = mContext.getTheme();
            currentTheme.resolveAttribute(android.R.attr.actionBarWidgetTheme, outValue, true);
            final int targetThemeRes = outValue.resourceId;
            if (targetThemeRes != 0) {
                mThemedContext = new ContextThemeWrapper(mContext, targetThemeRes);
            } else {
                mThemedContext = mContext;
            }
        }
        return mThemedContext;
    }

    @Override
    public boolean isTitleTruncated() { return mDecorToolbar != null && mDecorToolbar.isTitleTruncated(); }

    @Override
    public void setHomeAsUpIndicator(Drawable indicator) { mDecorToolbar.setNavigationIcon(indicator); }

    @Override
    public void setHomeAsUpIndicator(int resId) { mDecorToolbar.setNavigationIcon(resId); }

    @Override
    public void setHomeActionContentDescription(CharSequence description) {
        mDecorToolbar.setNavigationContentDescription(description);
    }

    @Override
    public void setHomeActionContentDescription(int resId) { mDecorToolbar.setNavigationContentDescription(resId); }

    @Override
    public void onContentScrollStarted() {}

    @Override
    public void onContentScrollStopped() {}

    @Override
    public boolean collapseActionView() {
        if (mDecorToolbar != null && mDecorToolbar.hasExpandedActionView()) {
            mDecorToolbar.collapseActionView();
            return true;
        }
        return false;
    }

    /** framework-internal. The primary action mode shown in the action bar's context view. */
    public class ActionModeImpl extends ActionMode implements MenuBuilder.Callback {
        private final Context mActionModeContext;
        private final MenuBuilder mMenu;
        private ActionMode.Callback mCallback;
        private WeakReference<View> mCustomView;

        public ActionModeImpl(Context context, ActionMode.Callback callback) {
            mActionModeContext = context;
            mCallback = callback;
            mMenu = new MenuBuilder(context).setDefaultShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            mMenu.setCallback(this);
        }

        @Override
        public MenuInflater getMenuInflater() { return new MenuInflater(mActionModeContext); }

        @Override
        public Menu getMenu() { return mMenu; }

        @Override
        public void finish() {
            if (mActionMode != this) return;
            if (!checkShowingFlags(mHiddenByApp, mHiddenBySystem, false)) {
                // The bar is hidden: destroy the mode after it is gone.
                mDeferredDestroyActionMode = this;
                mDeferredModeDestroyCallback = mCallback;
            } else {
                mCallback.onDestroyActionMode(this);
            }
            mCallback = null;
            animateToMode(false);
            mContextView.closeMode();
            if (mOverlayLayout != null) mOverlayLayout.setHideOnContentScrollEnabled(mHideOnContentScroll);
            mActionMode = null;
        }

        @Override
        public void invalidate() {
            if (mActionMode != this) return;
            mMenu.stopDispatchingItemsChanged();
            try {
                mCallback.onPrepareActionMode(this, mMenu);
            } finally {
                mMenu.startDispatchingItemsChanged();
            }
        }

        public boolean dispatchOnCreate() {
            mMenu.stopDispatchingItemsChanged();
            try {
                return mCallback.onCreateActionMode(this, mMenu);
            } finally {
                mMenu.startDispatchingItemsChanged();
            }
        }

        @Override
        public void setCustomView(View view) {
            mContextView.setCustomView(view);
            mCustomView = new WeakReference<View>(view);
        }

        @Override
        public void setSubtitle(CharSequence subtitle) { mContextView.setSubtitle(subtitle); }

        @Override
        public void setTitle(CharSequence title) { mContextView.setTitle(title); }

        @Override
        public void setTitle(int resId) { setTitle(mContext.getResources().getString(resId)); }

        @Override
        public void setSubtitle(int resId) { setSubtitle(mContext.getResources().getString(resId)); }

        @Override
        public CharSequence getTitle() { return mContextView.getTitle(); }

        @Override
        public CharSequence getSubtitle() { return mContextView.getSubtitle(); }

        @Override
        public void setTitleOptionalHint(boolean titleOptional) {
            super.setTitleOptionalHint(titleOptional);
            mContextView.setTitleOptional(titleOptional);
        }

        @Override
        public boolean isTitleOptional() { return mContextView.isTitleOptional(); }

        @Override
        public View getCustomView() { return mCustomView != null ? mCustomView.get() : null; }

        public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
            return mCallback != null && mCallback.onActionItemClicked(this, item);
        }

        public void onMenuModeChange(MenuBuilder menu) {
            if (mCallback == null) return;
            invalidate();
            mContextView.showOverflowMenu();
        }
    }

    /** framework-internal. A tab; tracked and dispatched, but not drawn. */
    public class TabImpl extends Tab {
        private TabListener mCallback;
        private Object mTag;
        private Drawable mIcon;
        private CharSequence mText;
        private CharSequence mContentDesc;
        private int mPosition = -1;
        private View mCustomView;

        @Override
        public Object getTag() { return mTag; }

        @Override
        public Tab setTag(Object tag) {
            mTag = tag;
            return this;
        }

        public TabListener getCallback() { return mCallback; }

        @Override
        public Tab setTabListener(TabListener callback) {
            mCallback = callback;
            return this;
        }

        @Override
        public View getCustomView() { return mCustomView; }

        @Override
        public Tab setCustomView(View view) {
            mCustomView = view;
            return this;
        }

        @Override
        public Tab setCustomView(int layoutResId) {
            return setCustomView(LayoutInflater.from(getThemedContext()).inflate(layoutResId, null));
        }

        @Override
        public Drawable getIcon() { return mIcon; }

        @Override
        public int getPosition() { return mPosition; }

        public void setPosition(int position) { mPosition = position; }

        @Override
        public CharSequence getText() { return mText; }

        @Override
        public Tab setIcon(Drawable icon) {
            mIcon = icon;
            return this;
        }

        @Override
        public Tab setIcon(int resId) { return setIcon(mContext.getDrawable(resId)); }

        @Override
        public Tab setText(CharSequence text) {
            mText = text;
            return this;
        }

        @Override
        public Tab setText(int resId) { return setText(mContext.getResources().getText(resId)); }

        @Override
        public void select() { selectTab(this); }

        @Override
        public Tab setContentDescription(int resId) { return setContentDescription(mContext.getResources().getText(resId)); }

        @Override
        public Tab setContentDescription(CharSequence contentDesc) {
            mContentDesc = contentDesc;
            return this;
        }

        @Override
        public CharSequence getContentDescription() { return mContentDesc; }
    }

    @Override
    public void setCustomView(View view) { mDecorToolbar.setCustomView(view); }

    @Override
    public void setCustomView(View view, LayoutParams layoutParams) {
        view.setLayoutParams(layoutParams);
        mDecorToolbar.setCustomView(view);
    }

    @Override
    public void setListNavigationCallbacks(SpinnerAdapter adapter, final OnNavigationListener callback) {
        mDecorToolbar.setDropdownParams(adapter, new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (callback != null) callback.onNavigationItemSelected(position, id);
            }

            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    @Override
    public int getSelectedNavigationIndex() {
        switch (mDecorToolbar.getNavigationMode()) {
            case NAVIGATION_MODE_TABS:
                return mSelectedTab != null ? mSelectedTab.getPosition() : -1;
            case NAVIGATION_MODE_LIST:
                return mDecorToolbar.getDropdownSelectedPosition();
            default:
                return -1;
        }
    }

    @Override
    public int getNavigationItemCount() {
        switch (mDecorToolbar.getNavigationMode()) {
            case NAVIGATION_MODE_TABS:
                return mTabs.size();
            case NAVIGATION_MODE_LIST:
                return mDecorToolbar.getDropdownItemCount();
            default:
                return 0;
        }
    }

    @Override
    public int getTabCount() { return mTabs.size(); }

    @Override
    public void setNavigationMode(int mode) {
        final int oldMode = mDecorToolbar.getNavigationMode();
        if (oldMode == NAVIGATION_MODE_TABS) {
            mSavedTabPosition = getSelectedNavigationIndex();
            selectTab(null);
        }
        if (oldMode != mode && !mHasEmbeddedTabs && mOverlayLayout != null) mOverlayLayout.requestFitSystemWindows();
        mDecorToolbar.setNavigationMode(mode);
        if (mode == NAVIGATION_MODE_TABS) {
            if (!sLoggedTabs) {
                sLoggedTabs = true;
                Log.w(TAG, "action bar tabs are dispatched but not drawn (no tab strip yet)");
            }
            if (mSavedTabPosition != INVALID_POSITION) {
                setSelectedNavigationItem(mSavedTabPosition);
                mSavedTabPosition = INVALID_POSITION;
            }
        }
        mDecorToolbar.setCollapsible(mode == NAVIGATION_MODE_TABS && !mHasEmbeddedTabs);
        if (mOverlayLayout != null) mOverlayLayout.setHasNonEmbeddedTabs(mode == NAVIGATION_MODE_TABS && !mHasEmbeddedTabs);
    }

    @Override
    public Tab getTabAt(int index) { return mTabs.get(index); }

    @Override
    public void setIcon(int resId) { mDecorToolbar.setIcon(resId); }

    @Override
    public void setIcon(Drawable icon) { mDecorToolbar.setIcon(icon); }

    public boolean hasIcon() { return mDecorToolbar.hasIcon(); }

    @Override
    public void setLogo(int resId) { mDecorToolbar.setLogo(resId); }

    @Override
    public void setLogo(Drawable logo) { mDecorToolbar.setLogo(logo); }

    public boolean hasLogo() { return mDecorToolbar.hasLogo(); }

    @Override
    public void setDefaultDisplayHomeAsUpEnabled(boolean enable) {
        if (!mDisplayHomeAsUpSet) setDisplayHomeAsUpEnabled(enable);
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        if (mActionMode == null) return false;
        final Menu menu = mActionMode.getMenu();
        if (menu != null) {
            final KeyCharacterMap kmap = KeyCharacterMap.load(event != null ? event.getDeviceId() : KeyCharacterMap.VIRTUAL_KEYBOARD);
            menu.setQwertyMode(kmap.getKeyboardType() != KeyCharacterMap.NUMERIC);
            return menu.performShortcut(keyCode, event, 0);
        }
        return false;
    }
}
