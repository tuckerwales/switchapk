package com.android.internal.widget;

import android.app.ActionBar;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ActionMenuPresenter;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Toolbar;
import com.android.internal.util.InternalRes;
import com.android.internal.view.menu.ActionMenuItem;
import com.android.internal.view.menu.MenuBuilder;
import com.android.internal.view.menu.MenuPresenter;

/**
 * framework-internal. Port of AOSP ToolbarWidgetWrapper: a Toolbar driven as
 * the window's action bar (display options, title, icon/logo, up button,
 * custom view, list navigation, menu). Tab navigation is not ported.
 */
public class ToolbarWidgetWrapper implements DecorToolbar {
    private static final int AFFECTS_LOGO_MASK = ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_USE_LOGO;

    private final Toolbar mToolbar;
    private int mDisplayOpts;
    private Spinner mSpinner;
    private View mCustomView;
    private Drawable mIcon;
    private Drawable mLogo;
    private Drawable mNavIcon;
    private boolean mTitleSet;
    private CharSequence mTitle;
    private CharSequence mSubtitle;
    private CharSequence mHomeDescription;
    private Window.Callback mWindowCallback;
    private boolean mMenuPrepared;
    private ActionMenuPresenter mActionMenuPresenter;
    private int mNavigationMode = ActionBar.NAVIGATION_MODE_STANDARD;
    private int mDefaultNavigationContentDescription = 0;
    private Drawable mDefaultNavigationIcon;

    public ToolbarWidgetWrapper(Toolbar toolbar, boolean style) {
        this(toolbar, style, InternalRes.id("string", "action_bar_up_description"));
    }

    public ToolbarWidgetWrapper(Toolbar toolbar, boolean style, int defaultNavigationContentDescription) {
        mToolbar = toolbar;
        mTitle = toolbar.getTitle();
        mSubtitle = toolbar.getSubtitle();
        mTitleSet = mTitle != null;
        mNavIcon = mToolbar.getNavigationIcon();
        final TypedArray a = toolbar.getContext().obtainStyledAttributes(null, new int[] {
                android.R.attr.homeAsUpIndicator, android.R.attr.title, android.R.attr.subtitle, android.R.attr.logo,
                android.R.attr.icon, android.R.attr.displayOptions, android.R.attr.customNavigationLayout,
                android.R.attr.height, android.R.attr.contentInsetStart, android.R.attr.contentInsetEnd,
                android.R.attr.titleTextStyle, android.R.attr.subtitleTextStyle, android.R.attr.popupTheme},
                android.R.attr.actionBarStyle, 0);
        mDefaultNavigationIcon = a.getDrawable(0);
        if (style) {
            final CharSequence title = a.getText(1);
            if (!TextUtils.isEmpty(title)) setTitle(title);
            final CharSequence subtitle = a.getText(2);
            if (!TextUtils.isEmpty(subtitle)) setSubtitle(subtitle);
            final Drawable logo = a.getDrawable(3);
            if (logo != null) setLogo(logo);
            final Drawable icon = a.getDrawable(4);
            if (icon != null) setIcon(icon);
            if (mNavIcon == null && mDefaultNavigationIcon != null) setNavigationIcon(mDefaultNavigationIcon);
            setDisplayOptions(a.getInt(5, 0));
            final int customNavId = a.getResourceId(6, 0);
            if (customNavId != 0) {
                setCustomView(LayoutInflater.from(mToolbar.getContext()).inflate(customNavId, mToolbar, false));
                setDisplayOptions(mDisplayOpts | ActionBar.DISPLAY_SHOW_CUSTOM);
            }
            final int height = a.getLayoutDimension(7, 0);
            if (height > 0) {
                ViewGroup.LayoutParams lp = mToolbar.getLayoutParams();
                if (lp != null) {
                    lp.height = height;
                    mToolbar.setLayoutParams(lp);
                }
            }
            final int contentInsetStart = a.getDimensionPixelOffset(8, -1);
            final int contentInsetEnd = a.getDimensionPixelOffset(9, -1);
            if (contentInsetStart >= 0 || contentInsetEnd >= 0) {
                mToolbar.setContentInsetsRelative(Math.max(contentInsetStart, 0), Math.max(contentInsetEnd, 0));
            }
            final int titleTextStyle = a.getResourceId(10, 0);
            if (titleTextStyle != 0) mToolbar.setTitleTextAppearance(mToolbar.getContext(), titleTextStyle);
            final int subtitleTextStyle = a.getResourceId(11, 0);
            if (subtitleTextStyle != 0) mToolbar.setSubtitleTextAppearance(mToolbar.getContext(), subtitleTextStyle);
            final int popupTheme = a.getResourceId(12, 0);
            if (popupTheme != 0) mToolbar.setPopupTheme(popupTheme);
        } else {
            mDisplayOpts = detectDisplayOptions();
        }
        a.recycle();
        setDefaultNavigationContentDescription(defaultNavigationContentDescription);
        mHomeDescription = mToolbar.getNavigationContentDescription();
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            final ActionMenuItem mNavItem = new ActionMenuItem(mToolbar.getContext(), 0, android.R.id.home, 0, 0, mTitle);

            public void onClick(View v) {
                if (mWindowCallback != null && mMenuPrepared) {
                    mWindowCallback.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, mNavItem);
                }
            }
        });
    }

    public void setDefaultNavigationContentDescription(int defaultNavigationContentDescription) {
        if (defaultNavigationContentDescription == mDefaultNavigationContentDescription) return;
        mDefaultNavigationContentDescription = defaultNavigationContentDescription;
        if (TextUtils.isEmpty(mToolbar.getNavigationContentDescription())) {
            setNavigationContentDescription(mDefaultNavigationContentDescription);
        }
    }

    private int detectDisplayOptions() {
        int opts = ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_USE_LOGO;
        if (mToolbar.getNavigationIcon() != null) {
            opts |= ActionBar.DISPLAY_HOME_AS_UP;
            mDefaultNavigationIcon = mToolbar.getNavigationIcon();
        }
        return opts;
    }

    public ViewGroup getViewGroup() { return mToolbar; }

    public Context getContext() { return mToolbar.getContext(); }

    public boolean isSplit() { return false; }

    public boolean hasExpandedActionView() { return mToolbar.hasExpandedActionView(); }

    public void collapseActionView() { mToolbar.collapseActionView(); }

    public void setWindowCallback(Window.Callback cb) { mWindowCallback = cb; }

    public void setWindowTitle(CharSequence title) {
        // "Real" title always trumps window title.
        if (!mTitleSet) setTitleInt(title);
    }

    public CharSequence getTitle() { return mToolbar.getTitle(); }

    public void setTitle(CharSequence title) {
        mTitleSet = true;
        setTitleInt(title);
    }

    private void setTitleInt(CharSequence title) {
        mTitle = title;
        if ((mDisplayOpts & ActionBar.DISPLAY_SHOW_TITLE) != 0) mToolbar.setTitle(title);
    }

    public CharSequence getSubtitle() { return mToolbar.getSubtitle(); }

    public void setSubtitle(CharSequence subtitle) {
        mSubtitle = subtitle;
        if ((mDisplayOpts & ActionBar.DISPLAY_SHOW_TITLE) != 0) mToolbar.setSubtitle(subtitle);
    }

    public void initProgress() {}

    public void initIndeterminateProgress() {}

    public boolean canSplit() { return false; }

    public void setSplitView(ViewGroup splitView) {}

    public void setSplitToolbar(boolean split) {
        if (split) throw new UnsupportedOperationException("Cannot split an android.widget.Toolbar");
    }

    public void setSplitWhenNarrow(boolean splitWhenNarrow) {}

    public boolean hasIcon() { return mIcon != null; }

    public boolean hasLogo() { return mLogo != null; }

    public void setIcon(int resId) { setIcon(resId != 0 ? getContext().getDrawable(resId) : null); }

    public void setIcon(Drawable d) {
        mIcon = d;
        updateToolbarLogo();
    }

    public void setLogo(int resId) { setLogo(resId != 0 ? getContext().getDrawable(resId) : null); }

    public void setLogo(Drawable d) {
        mLogo = d;
        updateToolbarLogo();
    }

    private void updateToolbarLogo() {
        Drawable logo = null;
        if ((mDisplayOpts & ActionBar.DISPLAY_SHOW_HOME) != 0) {
            if ((mDisplayOpts & ActionBar.DISPLAY_USE_LOGO) != 0) logo = mLogo != null ? mLogo : mIcon;
            else logo = mIcon;
        }
        mToolbar.setLogo(logo);
    }

    public boolean canShowOverflowMenu() { return mToolbar.canShowOverflowMenu(); }

    public boolean isOverflowMenuShowing() { return mToolbar.isOverflowMenuShowing(); }

    public boolean isOverflowMenuShowPending() { return mToolbar.isOverflowMenuShowPending(); }

    public boolean showOverflowMenu() { return mToolbar.showOverflowMenu(); }

    public boolean hideOverflowMenu() { return mToolbar.hideOverflowMenu(); }

    public void setMenuPrepared() { mMenuPrepared = true; }

    public void setMenu(Menu menu, MenuPresenter.Callback cb) {
        if (mActionMenuPresenter == null) {
            mActionMenuPresenter = new ActionMenuPresenter(mToolbar.getContext());
            mActionMenuPresenter.setId(InternalRes.viewId("action_menu_presenter"));
        }
        mActionMenuPresenter.setCallback(cb);
        mToolbar.setMenu((MenuBuilder) menu, mActionMenuPresenter);
    }

    public void dismissPopupMenus() { mToolbar.dismissPopupMenus(); }

    public int getDisplayOptions() { return mDisplayOpts; }

    public void setDisplayOptions(int newOpts) {
        final int oldOpts = mDisplayOpts;
        final int changed = oldOpts ^ newOpts;
        mDisplayOpts = newOpts;
        if (changed != 0) {
            if ((changed & ActionBar.DISPLAY_HOME_AS_UP) != 0) {
                if ((newOpts & ActionBar.DISPLAY_HOME_AS_UP) != 0) updateHomeAccessibility();
                updateNavigationIcon();
            }
            if ((changed & AFFECTS_LOGO_MASK) != 0) updateToolbarLogo();
            if ((changed & ActionBar.DISPLAY_SHOW_TITLE) != 0) {
                if ((newOpts & ActionBar.DISPLAY_SHOW_TITLE) != 0) {
                    mToolbar.setTitle(mTitle);
                    mToolbar.setSubtitle(mSubtitle);
                } else {
                    mToolbar.setTitle(null);
                    mToolbar.setSubtitle(null);
                }
            }
            if ((changed & ActionBar.DISPLAY_SHOW_CUSTOM) != 0 && mCustomView != null) {
                if ((newOpts & ActionBar.DISPLAY_SHOW_CUSTOM) != 0) mToolbar.addView(mCustomView);
                else mToolbar.removeView(mCustomView);
            }
        }
    }

    public boolean hasEmbeddedTabs() { return false; }

    public boolean isTitleTruncated() { return mToolbar.isTitleTruncated(); }

    public void setCollapsible(boolean collapsible) { mToolbar.setCollapsible(collapsible); }

    public void setHomeButtonEnabled(boolean enable) {
        // Ignore
    }

    public int getNavigationMode() { return mNavigationMode; }

    public void setNavigationMode(int mode) {
        final int oldMode = mNavigationMode;
        if (mode != oldMode) {
            if (oldMode == ActionBar.NAVIGATION_MODE_LIST && mSpinner != null && mSpinner.getParent() == mToolbar) {
                mToolbar.removeView(mSpinner);
            }
            mNavigationMode = mode;
            if (mode == ActionBar.NAVIGATION_MODE_LIST) {
                ensureSpinner();
                mToolbar.addView(mSpinner, 0);
            } else if (mode == ActionBar.NAVIGATION_MODE_TABS) {
                android.util.Log.w("ToolbarWidgetWrapper", "Tab navigation is not supported");
            }
        }
    }

    private void ensureSpinner() {
        if (mSpinner == null) {
            mSpinner = new Spinner(getContext(), null, android.R.attr.actionDropDownStyle);
            Toolbar.LayoutParams lp = new Toolbar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL);
            mSpinner.setLayoutParams(lp);
        }
    }

    public void setDropdownParams(SpinnerAdapter adapter, AdapterView.OnItemSelectedListener listener) {
        ensureSpinner();
        mSpinner.setAdapter(adapter);
        mSpinner.setOnItemSelectedListener(listener);
    }

    public void setDropdownSelectedPosition(int position) {
        if (mSpinner == null) throw new IllegalStateException("Can't set dropdown selected position without an adapter");
        mSpinner.setSelection(position);
    }

    public int getDropdownSelectedPosition() { return mSpinner != null ? mSpinner.getSelectedItemPosition() : 0; }

    public int getDropdownItemCount() { return mSpinner != null ? mSpinner.getCount() : 0; }

    public void setCustomView(View view) {
        if (mCustomView != null && (mDisplayOpts & ActionBar.DISPLAY_SHOW_CUSTOM) != 0) mToolbar.removeView(mCustomView);
        mCustomView = view;
        if (view != null && (mDisplayOpts & ActionBar.DISPLAY_SHOW_CUSTOM) != 0) mToolbar.addView(mCustomView);
    }

    public View getCustomView() { return mCustomView; }

    /** Visibility changes immediately. A fade needs android.transition, which is not in the tree. */
    public void animateToVisibility(int visibility) { mToolbar.setVisibility(visibility); }

    public void setNavigationIcon(Drawable icon) {
        mNavIcon = icon;
        updateNavigationIcon();
    }

    public void setNavigationIcon(int resId) { setNavigationIcon(resId != 0 ? mToolbar.getContext().getDrawable(resId) : null); }

    public void setDefaultNavigationIcon(Drawable defaultNavigationIcon) {
        if (mDefaultNavigationIcon != defaultNavigationIcon) {
            mDefaultNavigationIcon = defaultNavigationIcon;
            updateNavigationIcon();
        }
    }

    private void updateNavigationIcon() {
        if ((mDisplayOpts & ActionBar.DISPLAY_HOME_AS_UP) != 0) {
            mToolbar.setNavigationIcon(mNavIcon != null ? mNavIcon : mDefaultNavigationIcon);
        } else {
            mToolbar.setNavigationIcon(null);
        }
    }

    public void setNavigationContentDescription(CharSequence description) {
        mHomeDescription = description;
        updateHomeAccessibility();
    }

    public void setNavigationContentDescription(int resId) {
        setNavigationContentDescription(resId == 0 ? null : getContext().getString(resId));
    }

    private void updateHomeAccessibility() {
        if ((mDisplayOpts & ActionBar.DISPLAY_HOME_AS_UP) != 0) {
            if (TextUtils.isEmpty(mHomeDescription)) {
                mToolbar.setNavigationContentDescription(mDefaultNavigationContentDescription);
            } else {
                mToolbar.setNavigationContentDescription(mHomeDescription);
            }
        }
    }

    public void saveHierarchyState(SparseArray<Parcelable> toolbarStates) { mToolbar.saveHierarchyState(toolbarStates); }

    public void restoreHierarchyState(SparseArray<Parcelable> toolbarStates) {
        mToolbar.restoreHierarchyState(toolbarStates);
    }

    public void setBackgroundDrawable(Drawable d) { mToolbar.setBackground(d); }

    public int getHeight() { return mToolbar.getHeight(); }

    public void setVisibility(int visible) { mToolbar.setVisibility(visible); }

    public int getVisibility() { return mToolbar.getVisibility(); }

    public void setMenuCallbacks(MenuPresenter.Callback presenterCallback, MenuBuilder.Callback menuBuilderCallback) {
        mToolbar.setMenuCallbacks(presenterCallback, menuBuilderCallback);
    }

    public Menu getMenu() { return mToolbar.getMenu(); }
}
