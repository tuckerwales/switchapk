package android.widget;

import android.app.ActionBar;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Layout;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.CollapsibleActionView;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import com.android.internal.util.InternalRes;
import com.android.internal.view.menu.MenuBuilder;
import com.android.internal.view.menu.MenuItemImpl;
import com.android.internal.view.menu.MenuPresenter;
import com.android.internal.view.menu.MenuView;
import com.android.internal.view.menu.SubMenuBuilder;
import com.android.internal.widget.DecorToolbar;
import com.android.internal.widget.ToolbarWidgetWrapper;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of AOSP Toolbar: navigation button, logo, title and subtitle, custom
 * views and an ActionMenuView, laid out with content insets and title
 * margins as on Android. Also the decor action bar of Material themes
 * (through ToolbarWidgetWrapper).
 */
public class Toolbar extends ViewGroup {
    private ActionMenuView mMenuView;
    private TextView mTitleTextView;
    private TextView mSubtitleTextView;
    private ImageButton mNavButtonView;
    private ImageView mLogoView;
    private Drawable mCollapseIcon;
    private CharSequence mCollapseDescription;
    private ImageButton mCollapseButtonView;
    View mExpandedActionView;

    private Context mPopupContext;
    private int mPopupTheme;
    private int mTitleTextAppearance;
    private int mSubtitleTextAppearance;
    private int mNavButtonStyle;
    private int mButtonGravity;
    private int mMaxButtonHeight;
    private int mTitleMarginStart;
    private int mTitleMarginEnd;
    private int mTitleMarginTop;
    private int mTitleMarginBottom;
    private RtlSpacingHelper mContentInsets;
    private int mContentInsetStartWithNavigation;
    private int mContentInsetEndWithActions;
    private int mGravity = Gravity.START | Gravity.CENTER_VERTICAL;
    private CharSequence mTitleText;
    private CharSequence mSubtitleText;
    private int mTitleTextColor;
    private int mSubtitleTextColor;
    private boolean mEatingTouch;

    private final ArrayList<View> mTempViews = new ArrayList<View>();
    private final ArrayList<View> mHiddenViews = new ArrayList<View>();
    private final int[] mTempMargins = new int[2];

    private OnMenuItemClickListener mOnMenuItemClickListener;
    private final ActionMenuView.OnMenuItemClickListener mMenuViewItemClickListener =
            new ActionMenuView.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    return mOnMenuItemClickListener != null && mOnMenuItemClickListener.onMenuItemClick(item);
                }
            };

    private ToolbarWidgetWrapper mWrapper;
    private ActionMenuPresenter mOuterActionMenuPresenter;
    private ExpandedActionViewMenuPresenter mExpandedMenuPresenter;
    private MenuPresenter.Callback mActionMenuPresenterCallback;
    private MenuBuilder.Callback mMenuBuilderCallback;
    private boolean mCollapsible;

    private final Runnable mShowOverflowMenuRunnable = new Runnable() {
        public void run() { showOverflowMenu(); }
    };

    public Toolbar(Context context) { this(context, null); }

    public Toolbar(Context context, AttributeSet attrs) { this(context, attrs, android.R.attr.toolbarStyle); }

    public Toolbar(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public Toolbar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final int navButtonStyleAttr = InternalRes.attr("navigationButtonStyle");
        final TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.titleTextAppearance, android.R.attr.subtitleTextAppearance, navButtonStyleAttr,
                android.R.attr.gravity, android.R.attr.buttonGravity, android.R.attr.titleMargin,
                android.R.attr.titleMarginStart, android.R.attr.titleMarginEnd, android.R.attr.titleMarginTop,
                android.R.attr.titleMarginBottom, android.R.attr.maxButtonHeight, android.R.attr.contentInsetStart,
                android.R.attr.contentInsetEnd, android.R.attr.contentInsetLeft, android.R.attr.contentInsetRight,
                android.R.attr.contentInsetStartWithNavigation, android.R.attr.contentInsetEndWithActions,
                android.R.attr.collapseIcon, android.R.attr.collapseContentDescription, android.R.attr.title,
                android.R.attr.subtitle, android.R.attr.popupTheme, android.R.attr.navigationIcon,
                android.R.attr.navigationContentDescription, android.R.attr.logo, android.R.attr.logoDescription,
                android.R.attr.titleTextColor, android.R.attr.subtitleTextColor}, defStyleAttr, defStyleRes);
        mTitleTextAppearance = a.getResourceId(0, 0);
        mSubtitleTextAppearance = a.getResourceId(1, 0);
        mNavButtonStyle = navButtonStyleAttr != 0 ? a.getResourceId(2, 0) : 0;
        mGravity = a.getInteger(3, mGravity);
        mButtonGravity = a.getInteger(4, Gravity.TOP);
        mTitleMarginStart = mTitleMarginEnd = mTitleMarginTop = mTitleMarginBottom = a.getDimensionPixelOffset(5, 0);
        final int marginStart = a.getDimensionPixelOffset(6, -1);
        if (marginStart >= 0) mTitleMarginStart = marginStart;
        final int marginEnd = a.getDimensionPixelOffset(7, -1);
        if (marginEnd >= 0) mTitleMarginEnd = marginEnd;
        final int marginTop = a.getDimensionPixelOffset(8, -1);
        if (marginTop >= 0) mTitleMarginTop = marginTop;
        final int marginBottom = a.getDimensionPixelOffset(9, -1);
        if (marginBottom >= 0) mTitleMarginBottom = marginBottom;
        mMaxButtonHeight = a.getDimensionPixelSize(10, -1);
        final int contentInsetStart = a.getDimensionPixelOffset(11, RtlSpacingHelper.UNDEFINED);
        final int contentInsetEnd = a.getDimensionPixelOffset(12, RtlSpacingHelper.UNDEFINED);
        final int contentInsetLeft = a.getDimensionPixelSize(13, 0);
        final int contentInsetRight = a.getDimensionPixelSize(14, 0);
        ensureContentInsets();
        mContentInsets.setAbsolute(contentInsetLeft, contentInsetRight);
        if (contentInsetStart != RtlSpacingHelper.UNDEFINED || contentInsetEnd != RtlSpacingHelper.UNDEFINED) {
            mContentInsets.setRelative(contentInsetStart, contentInsetEnd);
        }
        mContentInsetStartWithNavigation = a.getDimensionPixelOffset(15, RtlSpacingHelper.UNDEFINED);
        mContentInsetEndWithActions = a.getDimensionPixelOffset(16, RtlSpacingHelper.UNDEFINED);
        mCollapseIcon = a.getDrawable(17);
        mCollapseDescription = a.getText(18);
        final CharSequence title = a.getText(19);
        if (!TextUtils.isEmpty(title)) setTitle(title);
        final CharSequence subtitle = a.getText(20);
        if (!TextUtils.isEmpty(subtitle)) setSubtitle(subtitle);
        mPopupContext = getContext();
        setPopupTheme(a.getResourceId(21, 0));
        final Drawable navIcon = a.getDrawable(22);
        if (navIcon != null) setNavigationIcon(navIcon);
        final CharSequence navDesc = a.getText(23);
        if (!TextUtils.isEmpty(navDesc)) setNavigationContentDescription(navDesc);
        final Drawable logo = a.getDrawable(24);
        if (logo != null) setLogo(logo);
        final CharSequence logoDesc = a.getText(25);
        if (!TextUtils.isEmpty(logoDesc)) setLogoDescription(logoDesc);
        if (a.hasValue(26)) setTitleTextColor(a.getColor(26, 0xffffffff));
        if (a.hasValue(27)) setSubtitleTextColor(a.getColor(27, 0xffffffff));
        a.recycle();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // If the container is a cluster, unmark itself as a cluster to avoid having nested clusters.
        ViewParent parent = getParent();
        while (parent instanceof ViewGroup) {
            final ViewGroup vgParent = (ViewGroup) parent;
            if (vgParent.isKeyboardNavigationCluster()) {
                setKeyboardNavigationCluster(false);
                if (vgParent.getTouchscreenBlocksFocus()) setTouchscreenBlocksFocus(false);
                break;
            }
            parent = vgParent.getParent();
        }
    }

    public void setPopupTheme(int resId) {
        if (mPopupTheme != resId) {
            mPopupTheme = resId;
            mPopupContext = resId == 0 ? getContext() : new ContextThemeWrapper(getContext(), resId);
        }
    }

    public int getPopupTheme() { return mPopupTheme; }

    public void setTitleMargin(int start, int top, int end, int bottom) {
        mTitleMarginStart = start;
        mTitleMarginTop = top;
        mTitleMarginEnd = end;
        mTitleMarginBottom = bottom;
        requestLayout();
    }

    public int getTitleMarginStart() { return mTitleMarginStart; }

    public void setTitleMarginStart(int margin) {
        mTitleMarginStart = margin;
        requestLayout();
    }

    public int getTitleMarginTop() { return mTitleMarginTop; }

    public void setTitleMarginTop(int margin) {
        mTitleMarginTop = margin;
        requestLayout();
    }

    public int getTitleMarginEnd() { return mTitleMarginEnd; }

    public void setTitleMarginEnd(int margin) {
        mTitleMarginEnd = margin;
        requestLayout();
    }

    public int getTitleMarginBottom() { return mTitleMarginBottom; }

    public void setTitleMarginBottom(int margin) {
        mTitleMarginBottom = margin;
        requestLayout();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        ensureContentInsets();
        mContentInsets.setDirection(layoutDirection == LAYOUT_DIRECTION_RTL);
    }

    public void setLogo(int resId) { setLogo(getContext().getDrawable(resId)); }

    /** framework-internal (hidden in AOSP). */
    public boolean canShowOverflowMenu() {
        return getVisibility() == VISIBLE && mMenuView != null && mMenuView.isOverflowReserved();
    }

    public boolean isOverflowMenuShowing() { return mMenuView != null && mMenuView.isOverflowMenuShowing(); }

    /** framework-internal (hidden in AOSP). */
    public boolean isOverflowMenuShowPending() { return mMenuView != null && mMenuView.isOverflowMenuShowPending(); }

    public boolean showOverflowMenu() { return mMenuView != null && mMenuView.showOverflowMenu(); }

    public boolean hideOverflowMenu() { return mMenuView != null && mMenuView.hideOverflowMenu(); }

    /** framework-internal (hidden in AOSP). Used by the decor action bar. */
    public void setMenu(MenuBuilder menu, ActionMenuPresenter outerPresenter) {
        if (menu == null && mMenuView == null) return;
        ensureMenuView();
        final MenuBuilder oldMenu = mMenuView.peekMenu();
        if (oldMenu == menu) return;
        if (oldMenu != null) {
            oldMenu.removeMenuPresenter(mOuterActionMenuPresenter);
            oldMenu.removeMenuPresenter(mExpandedMenuPresenter);
        }
        if (mExpandedMenuPresenter == null) mExpandedMenuPresenter = new ExpandedActionViewMenuPresenter();
        outerPresenter.setExpandedActionViewsExclusive(true);
        if (menu != null) {
            menu.addMenuPresenter(outerPresenter, mPopupContext);
            menu.addMenuPresenter(mExpandedMenuPresenter, mPopupContext);
        } else {
            outerPresenter.initForMenu(mPopupContext, null);
            mExpandedMenuPresenter.initForMenu(mPopupContext, null);
            outerPresenter.updateMenuView(true);
            mExpandedMenuPresenter.updateMenuView(true);
        }
        mMenuView.setPopupTheme(mPopupTheme);
        mMenuView.setPresenter(outerPresenter);
        mOuterActionMenuPresenter = outerPresenter;
    }

    public void dismissPopupMenus() {
        if (mMenuView != null) mMenuView.dismissPopupMenus();
    }

    /** framework-internal (hidden in AOSP). */
    public boolean isTitleTruncated() {
        if (mTitleTextView == null) return false;
        final Layout titleLayout = mTitleTextView.getLayout();
        if (titleLayout == null) return false;
        final int lineCount = titleLayout.getLineCount();
        for (int i = 0; i < lineCount; i++) {
            if (titleLayout.getEllipsisCount(i) > 0) return true;
        }
        return false;
    }

    public void setLogo(Drawable drawable) {
        if (drawable != null) {
            ensureLogoView();
            if (!isChildOrHidden(mLogoView)) addSystemView(mLogoView, true);
        } else if (mLogoView != null && isChildOrHidden(mLogoView)) {
            removeView(mLogoView);
            mHiddenViews.remove(mLogoView);
        }
        if (mLogoView != null) mLogoView.setImageDrawable(drawable);
    }

    public Drawable getLogo() { return mLogoView != null ? mLogoView.getDrawable() : null; }

    public void setLogoDescription(int resId) { setLogoDescription(getContext().getText(resId)); }

    public void setLogoDescription(CharSequence description) {
        if (!TextUtils.isEmpty(description)) ensureLogoView();
        if (mLogoView != null) mLogoView.setContentDescription(description);
    }

    public CharSequence getLogoDescription() { return mLogoView != null ? mLogoView.getContentDescription() : null; }

    private void ensureLogoView() {
        if (mLogoView == null) mLogoView = new ImageView(getContext());
    }

    public boolean hasExpandedActionView() {
        return mExpandedMenuPresenter != null && mExpandedMenuPresenter.mCurrentExpandedItem != null;
    }

    public void collapseActionView() {
        final MenuItemImpl item = mExpandedMenuPresenter == null ? null : mExpandedMenuPresenter.mCurrentExpandedItem;
        if (item != null) item.collapseActionView();
    }

    public CharSequence getTitle() { return mTitleText; }

    public void setTitle(int resId) { setTitle(getContext().getText(resId)); }

    public void setTitle(CharSequence title) {
        if (!TextUtils.isEmpty(title)) {
            if (mTitleTextView == null) {
                final Context context = getContext();
                mTitleTextView = new TextView(context);
                mTitleTextView.setSingleLine();
                mTitleTextView.setEllipsize(TextUtils.TruncateAt.END);
                if (mTitleTextAppearance != 0) mTitleTextView.setTextAppearance(mTitleTextAppearance);
                if (mTitleTextColor != 0) mTitleTextView.setTextColor(mTitleTextColor);
            }
            if (!isChildOrHidden(mTitleTextView)) addSystemView(mTitleTextView, true);
        } else if (mTitleTextView != null && isChildOrHidden(mTitleTextView)) {
            removeView(mTitleTextView);
            mHiddenViews.remove(mTitleTextView);
        }
        if (mTitleTextView != null) mTitleTextView.setText(title);
        mTitleText = title;
    }

    public CharSequence getSubtitle() { return mSubtitleText; }

    public void setSubtitle(int resId) { setSubtitle(getContext().getText(resId)); }

    public void setSubtitle(CharSequence subtitle) {
        if (!TextUtils.isEmpty(subtitle)) {
            if (mSubtitleTextView == null) {
                final Context context = getContext();
                mSubtitleTextView = new TextView(context);
                mSubtitleTextView.setSingleLine();
                mSubtitleTextView.setEllipsize(TextUtils.TruncateAt.END);
                if (mSubtitleTextAppearance != 0) mSubtitleTextView.setTextAppearance(mSubtitleTextAppearance);
                if (mSubtitleTextColor != 0) mSubtitleTextView.setTextColor(mSubtitleTextColor);
            }
            if (!isChildOrHidden(mSubtitleTextView)) addSystemView(mSubtitleTextView, true);
        } else if (mSubtitleTextView != null && isChildOrHidden(mSubtitleTextView)) {
            removeView(mSubtitleTextView);
            mHiddenViews.remove(mSubtitleTextView);
        }
        if (mSubtitleTextView != null) mSubtitleTextView.setText(subtitle);
        mSubtitleText = subtitle;
    }

    public void setTitleTextAppearance(Context context, int resId) {
        mTitleTextAppearance = resId;
        if (mTitleTextView != null) mTitleTextView.setTextAppearance(resId);
    }

    public void setSubtitleTextAppearance(Context context, int resId) {
        mSubtitleTextAppearance = resId;
        if (mSubtitleTextView != null) mSubtitleTextView.setTextAppearance(resId);
    }

    public void setTitleTextColor(int color) {
        mTitleTextColor = color;
        if (mTitleTextView != null) mTitleTextView.setTextColor(color);
    }

    public void setSubtitleTextColor(int color) {
        mSubtitleTextColor = color;
        if (mSubtitleTextView != null) mSubtitleTextView.setTextColor(color);
    }

    public CharSequence getNavigationContentDescription() {
        return mNavButtonView != null ? mNavButtonView.getContentDescription() : null;
    }

    public void setNavigationContentDescription(int resId) {
        setNavigationContentDescription(resId != 0 ? getContext().getText(resId) : null);
    }

    public void setNavigationContentDescription(CharSequence description) {
        if (!TextUtils.isEmpty(description)) ensureNavButtonView();
        if (mNavButtonView != null) mNavButtonView.setContentDescription(description);
    }

    public void setNavigationIcon(int resId) { setNavigationIcon(getContext().getDrawable(resId)); }

    public void setNavigationIcon(Drawable icon) {
        if (icon != null) {
            ensureNavButtonView();
            if (!isChildOrHidden(mNavButtonView)) addSystemView(mNavButtonView, true);
        } else if (mNavButtonView != null && isChildOrHidden(mNavButtonView)) {
            removeView(mNavButtonView);
            mHiddenViews.remove(mNavButtonView);
        }
        if (mNavButtonView != null) mNavButtonView.setImageDrawable(icon);
    }

    public Drawable getNavigationIcon() { return mNavButtonView != null ? mNavButtonView.getDrawable() : null; }

    public void setNavigationOnClickListener(OnClickListener listener) {
        ensureNavButtonView();
        mNavButtonView.setOnClickListener(listener);
    }

    /** framework-internal (hidden in AOSP). */
    public View getNavigationView() { return mNavButtonView; }

    public CharSequence getCollapseContentDescription() {
        return mCollapseButtonView != null ? mCollapseButtonView.getContentDescription() : null;
    }

    public void setCollapseContentDescription(int resId) {
        setCollapseContentDescription(resId != 0 ? getContext().getText(resId) : null);
    }

    public void setCollapseContentDescription(CharSequence description) {
        if (!TextUtils.isEmpty(description)) ensureCollapseButtonView();
        if (mCollapseButtonView != null) mCollapseButtonView.setContentDescription(description);
    }

    public Drawable getCollapseIcon() { return mCollapseButtonView != null ? mCollapseButtonView.getDrawable() : null; }

    public void setCollapseIcon(int resId) { setCollapseIcon(getContext().getDrawable(resId)); }

    public void setCollapseIcon(Drawable icon) {
        if (icon != null) {
            ensureCollapseButtonView();
            mCollapseButtonView.setImageDrawable(icon);
        } else if (mCollapseButtonView != null) {
            mCollapseButtonView.setImageDrawable(mCollapseIcon);
        }
    }

    public Menu getMenu() {
        ensureMenu();
        return mMenuView.getMenu();
    }

    public void setOverflowIcon(Drawable icon) {
        ensureMenu();
        mMenuView.setOverflowIcon(icon);
    }

    public Drawable getOverflowIcon() {
        ensureMenu();
        return mMenuView.getOverflowIcon();
    }

    private void ensureMenu() {
        ensureMenuView();
        if (mMenuView.peekMenu() == null) {
            final MenuBuilder menu = (MenuBuilder) mMenuView.getMenu();
            if (mExpandedMenuPresenter == null) mExpandedMenuPresenter = new ExpandedActionViewMenuPresenter();
            mMenuView.setExpandedActionViewsExclusive(true);
            menu.addMenuPresenter(mExpandedMenuPresenter, mPopupContext);
        }
    }

    private void ensureMenuView() {
        if (mMenuView == null) {
            mMenuView = new ActionMenuView(getContext());
            mMenuView.setPopupTheme(mPopupTheme);
            mMenuView.setOnMenuItemClickListener(mMenuViewItemClickListener);
            mMenuView.setMenuCallbacks(mActionMenuPresenterCallback, mMenuBuilderCallback);
            final LayoutParams lp = generateDefaultLayoutParams();
            lp.gravity = Gravity.END | (mButtonGravity & Gravity.VERTICAL_GRAVITY_MASK);
            mMenuView.setLayoutParams(lp);
            addSystemView(mMenuView, false);
        }
    }

    private MenuInflater getMenuInflater() { return new MenuInflater(getContext()); }

    public void inflateMenu(int resId) { getMenuInflater().inflate(resId, getMenu()); }

    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) { mOnMenuItemClickListener = listener; }

    public void setContentInsetsRelative(int contentInsetStart, int contentInsetEnd) {
        ensureContentInsets();
        mContentInsets.setRelative(contentInsetStart, contentInsetEnd);
    }

    public int getContentInsetStart() { return mContentInsets != null ? mContentInsets.getStart() : 0; }

    public int getContentInsetEnd() { return mContentInsets != null ? mContentInsets.getEnd() : 0; }

    public void setContentInsetsAbsolute(int contentInsetLeft, int contentInsetRight) {
        ensureContentInsets();
        mContentInsets.setAbsolute(contentInsetLeft, contentInsetRight);
    }

    public int getContentInsetLeft() { return mContentInsets != null ? mContentInsets.getLeft() : 0; }

    public int getContentInsetRight() { return mContentInsets != null ? mContentInsets.getRight() : 0; }

    public int getContentInsetStartWithNavigation() {
        return mContentInsetStartWithNavigation != RtlSpacingHelper.UNDEFINED
                ? mContentInsetStartWithNavigation : getContentInsetStart();
    }

    public void setContentInsetStartWithNavigation(int insetStartWithNavigation) {
        if (insetStartWithNavigation < 0) insetStartWithNavigation = RtlSpacingHelper.UNDEFINED;
        if (insetStartWithNavigation != mContentInsetStartWithNavigation) {
            mContentInsetStartWithNavigation = insetStartWithNavigation;
            if (getNavigationIcon() != null) requestLayout();
        }
    }

    public int getContentInsetEndWithActions() {
        return mContentInsetEndWithActions != RtlSpacingHelper.UNDEFINED
                ? mContentInsetEndWithActions : getContentInsetEnd();
    }

    public void setContentInsetEndWithActions(int insetEndWithActions) {
        if (insetEndWithActions < 0) insetEndWithActions = RtlSpacingHelper.UNDEFINED;
        if (insetEndWithActions != mContentInsetEndWithActions) {
            mContentInsetEndWithActions = insetEndWithActions;
            if (getNavigationIcon() != null) requestLayout();
        }
    }

    public int getCurrentContentInsetStart() {
        return getNavigationIcon() != null
                ? Math.max(getContentInsetStart(), Math.max(mContentInsetStartWithNavigation, 0))
                : getContentInsetStart();
    }

    public int getCurrentContentInsetEnd() {
        boolean hasActions = false;
        if (mMenuView != null) {
            final MenuBuilder mb = mMenuView.peekMenu();
            hasActions = mb != null && mb.hasVisibleItems();
        }
        return hasActions ? Math.max(getContentInsetEnd(), Math.max(mContentInsetEndWithActions, 0))
                : getContentInsetEnd();
    }

    public int getCurrentContentInsetLeft() {
        return isLayoutRtl() ? getCurrentContentInsetEnd() : getCurrentContentInsetStart();
    }

    public int getCurrentContentInsetRight() {
        return isLayoutRtl() ? getCurrentContentInsetStart() : getCurrentContentInsetEnd();
    }

    private void ensureNavButtonView() {
        if (mNavButtonView == null) {
            mNavButtonView = new ImageButton(getContext(), null, 0, mNavButtonStyle);
            final LayoutParams lp = generateDefaultLayoutParams();
            lp.gravity = Gravity.START | (mButtonGravity & Gravity.VERTICAL_GRAVITY_MASK);
            mNavButtonView.setLayoutParams(lp);
        }
    }

    private void ensureCollapseButtonView() {
        if (mCollapseButtonView == null) {
            mCollapseButtonView = new ImageButton(getContext(), null, 0, mNavButtonStyle);
            mCollapseButtonView.setImageDrawable(mCollapseIcon);
            mCollapseButtonView.setContentDescription(mCollapseDescription);
            final LayoutParams lp = generateDefaultLayoutParams();
            lp.gravity = Gravity.START | (mButtonGravity & Gravity.VERTICAL_GRAVITY_MASK);
            lp.mViewType = LayoutParams.EXPANDED;
            mCollapseButtonView.setLayoutParams(lp);
            mCollapseButtonView.setOnClickListener(new OnClickListener() {
                public void onClick(View v) { collapseActionView(); }
            });
        }
    }

    private void addSystemView(View v, boolean allowHide) {
        final ViewGroup.LayoutParams vlp = v.getLayoutParams();
        final LayoutParams lp;
        if (vlp == null) lp = generateDefaultLayoutParams();
        else if (!checkLayoutParams(vlp)) lp = generateLayoutParams(vlp);
        else lp = (LayoutParams) vlp;
        lp.mViewType = LayoutParams.SYSTEM;
        if (allowHide && mExpandedActionView != null) {
            v.setLayoutParams(lp);
            mHiddenViews.add(v);
        } else {
            addView(v, lp);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        if (mExpandedMenuPresenter != null && mExpandedMenuPresenter.mCurrentExpandedItem != null) {
            state.expandedMenuItemId = mExpandedMenuPresenter.mCurrentExpandedItem.getItemId();
        }
        state.isOverflowOpen = isOverflowMenuShowing();
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        final SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        final Menu menu = mMenuView != null ? mMenuView.peekMenu() : null;
        if (ss.expandedMenuItemId != 0 && mExpandedMenuPresenter != null && menu != null) {
            final MenuItem item = menu.findItem(ss.expandedMenuItemId);
            if (item != null) item.expandActionView();
        }
        if (ss.isOverflowOpen) postShowOverflowMenu();
    }

    private void postShowOverflowMenu() {
        removeCallbacks(mShowOverflowMenuRunnable);
        post(mShowOverflowMenuRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(mShowOverflowMenuRunnable);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Toolbars always eat touch events, but should still respect the touch event dispatch
        // contract. If the normal View implementation doesn't want the events, we'll just silently
        // eat the rest of the gesture without reporting the events to the default implementation
        // since that's what it expects.
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) mEatingTouch = false;
        if (!mEatingTouch) {
            final boolean handled = super.onTouchEvent(ev);
            if (action == MotionEvent.ACTION_DOWN && !handled) mEatingTouch = true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) mEatingTouch = false;
        return true;
    }

    private void measureChildConstrained(View child, int parentWidthSpec, int widthUsed, int parentHeightSpec,
            int heightUsed, int heightConstraint) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        int childWidthSpec = getChildMeasureSpec(parentWidthSpec,
                mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin + widthUsed, lp.width);
        int childHeightSpec = getChildMeasureSpec(parentHeightSpec,
                mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin + heightUsed, lp.height);
        final int childHeightMode = MeasureSpec.getMode(childHeightSpec);
        if (childHeightMode != MeasureSpec.EXACTLY && heightConstraint >= 0) {
            final int size = childHeightMode != MeasureSpec.UNSPECIFIED
                    ? Math.min(MeasureSpec.getSize(childHeightSpec), heightConstraint) : heightConstraint;
            childHeightSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
        }
        child.measure(childWidthSpec, childHeightSpec);
    }

    private int measureChildCollapseMargins(View child, int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed, int[] collapsingMargins) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        final int leftDiff = lp.leftMargin - collapsingMargins[0];
        final int rightDiff = lp.rightMargin - collapsingMargins[1];
        final int leftMargin = Math.max(0, leftDiff);
        final int rightMargin = Math.max(0, rightDiff);
        final int hMargins = leftMargin + rightMargin;
        collapsingMargins[0] = Math.max(0, -leftDiff);
        collapsingMargins[1] = Math.max(0, -rightDiff);
        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                mPaddingLeft + mPaddingRight + hMargins + widthUsed, lp.width);
        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin + heightUsed, lp.height);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        return child.getMeasuredWidth() + hMargins;
    }

    private boolean shouldCollapse() {
        if (!mCollapsible) return false;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (shouldLayout(child) && child.getMeasuredWidth() > 0 && child.getMeasuredHeight() > 0) return false;
        }
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = 0;
        int height = 0;
        int childState = 0;
        final int[] collapsingMargins = mTempMargins;
        final int marginStartIndex;
        final int marginEndIndex;
        if (isLayoutRtl()) {
            marginStartIndex = 1;
            marginEndIndex = 0;
        } else {
            marginStartIndex = 0;
            marginEndIndex = 1;
        }
        int navWidth = 0;
        if (shouldLayout(mNavButtonView)) {
            measureChildConstrained(mNavButtonView, widthMeasureSpec, width, heightMeasureSpec, 0, mMaxButtonHeight);
            navWidth = mNavButtonView.getMeasuredWidth() + getHorizontalMargins(mNavButtonView);
            height = Math.max(height, mNavButtonView.getMeasuredHeight() + getVerticalMargins(mNavButtonView));
            childState = combineMeasuredStates(childState, mNavButtonView.getMeasuredState());
        }
        if (shouldLayout(mCollapseButtonView)) {
            measureChildConstrained(mCollapseButtonView, widthMeasureSpec, width, heightMeasureSpec, 0,
                    mMaxButtonHeight);
            navWidth = mCollapseButtonView.getMeasuredWidth() + getHorizontalMargins(mCollapseButtonView);
            height = Math.max(height, mCollapseButtonView.getMeasuredHeight()
                    + getVerticalMargins(mCollapseButtonView));
            childState = combineMeasuredStates(childState, mCollapseButtonView.getMeasuredState());
        }
        final int contentInsetStart = getCurrentContentInsetStart();
        width += Math.max(contentInsetStart, navWidth);
        collapsingMargins[marginStartIndex] = Math.max(0, contentInsetStart - navWidth);
        int menuWidth = 0;
        if (shouldLayout(mMenuView)) {
            measureChildConstrained(mMenuView, widthMeasureSpec, width, heightMeasureSpec, 0, mMaxButtonHeight);
            menuWidth = mMenuView.getMeasuredWidth() + getHorizontalMargins(mMenuView);
            height = Math.max(height, mMenuView.getMeasuredHeight() + getVerticalMargins(mMenuView));
            childState = combineMeasuredStates(childState, mMenuView.getMeasuredState());
        }
        final int contentInsetEnd = getCurrentContentInsetEnd();
        width += Math.max(contentInsetEnd, menuWidth);
        collapsingMargins[marginEndIndex] = Math.max(0, contentInsetEnd - menuWidth);
        if (shouldLayout(mExpandedActionView)) {
            width += measureChildCollapseMargins(mExpandedActionView, widthMeasureSpec, width, heightMeasureSpec, 0,
                    collapsingMargins);
            height = Math.max(height, mExpandedActionView.getMeasuredHeight() + getVerticalMargins(mExpandedActionView));
            childState = combineMeasuredStates(childState, mExpandedActionView.getMeasuredState());
        }
        if (shouldLayout(mLogoView)) {
            width += measureChildCollapseMargins(mLogoView, widthMeasureSpec, width, heightMeasureSpec, 0,
                    collapsingMargins);
            height = Math.max(height, mLogoView.getMeasuredHeight() + getVerticalMargins(mLogoView));
            childState = combineMeasuredStates(childState, mLogoView.getMeasuredState());
        }
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp.mViewType != LayoutParams.CUSTOM || !shouldLayout(child)) continue;
            width += measureChildCollapseMargins(child, widthMeasureSpec, width, heightMeasureSpec, 0, collapsingMargins);
            height = Math.max(height, child.getMeasuredHeight() + getVerticalMargins(child));
            childState = combineMeasuredStates(childState, child.getMeasuredState());
        }
        int titleWidth = 0;
        int titleHeight = 0;
        final int titleVertMargins = mTitleMarginTop + mTitleMarginBottom;
        final int titleHorizMargins = mTitleMarginStart + mTitleMarginEnd;
        if (shouldLayout(mTitleTextView)) {
            titleWidth = measureChildCollapseMargins(mTitleTextView, widthMeasureSpec, width + titleHorizMargins,
                    heightMeasureSpec, titleVertMargins, collapsingMargins);
            titleWidth = mTitleTextView.getMeasuredWidth() + getHorizontalMargins(mTitleTextView);
            titleHeight = mTitleTextView.getMeasuredHeight() + getVerticalMargins(mTitleTextView);
            childState = combineMeasuredStates(childState, mTitleTextView.getMeasuredState());
        }
        if (shouldLayout(mSubtitleTextView)) {
            titleWidth = Math.max(titleWidth, measureChildCollapseMargins(mSubtitleTextView, widthMeasureSpec,
                    width + titleHorizMargins, heightMeasureSpec, titleHeight + titleVertMargins, collapsingMargins));
            titleHeight += mSubtitleTextView.getMeasuredHeight() + getVerticalMargins(mSubtitleTextView);
            childState = combineMeasuredStates(childState, mSubtitleTextView.getMeasuredState());
        }
        width += titleWidth;
        height = Math.max(height, titleHeight);
        width += getPaddingLeft() + getPaddingRight();
        height += getPaddingTop() + getPaddingBottom();
        final int measuredWidth = resolveSizeAndState(Math.max(width, getSuggestedMinimumWidth()), widthMeasureSpec,
                childState & MEASURED_STATE_MASK);
        final int measuredHeight = resolveSizeAndState(Math.max(height, getSuggestedMinimumHeight()), heightMeasureSpec,
                childState << MEASURED_HEIGHT_STATE_SHIFT);
        setMeasuredDimension(measuredWidth, shouldCollapse() ? 0 : measuredHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        final int width = getWidth();
        final int height = getHeight();
        final int paddingLeft = getPaddingLeft();
        final int paddingRight = getPaddingRight();
        final int paddingTop = getPaddingTop();
        final int paddingBottom = getPaddingBottom();
        int left = paddingLeft;
        int right = width - paddingRight;
        final int[] collapsingMargins = mTempMargins;
        collapsingMargins[0] = collapsingMargins[1] = 0;
        final int alignmentHeight = getMinimumHeight();
        final int minHeight = alignmentHeight >= 0 ? Math.min(alignmentHeight, b - t) : 0;
        if (shouldLayout(mNavButtonView)) {
            if (isRtl) right = layoutChildRight(mNavButtonView, right, collapsingMargins, minHeight);
            else left = layoutChildLeft(mNavButtonView, left, collapsingMargins, minHeight);
        }
        if (shouldLayout(mCollapseButtonView)) {
            if (isRtl) right = layoutChildRight(mCollapseButtonView, right, collapsingMargins, minHeight);
            else left = layoutChildLeft(mCollapseButtonView, left, collapsingMargins, minHeight);
        }
        if (shouldLayout(mMenuView)) {
            if (isRtl) left = layoutChildLeft(mMenuView, left, collapsingMargins, minHeight);
            else right = layoutChildRight(mMenuView, right, collapsingMargins, minHeight);
        }
        final int contentInsetLeft = getCurrentContentInsetLeft();
        final int contentInsetRight = getCurrentContentInsetRight();
        collapsingMargins[0] = Math.max(0, contentInsetLeft - left);
        collapsingMargins[1] = Math.max(0, contentInsetRight - (width - paddingRight - right));
        left = Math.max(left, contentInsetLeft);
        right = Math.min(right, width - paddingRight - contentInsetRight);
        if (shouldLayout(mExpandedActionView)) {
            if (isRtl) right = layoutChildRight(mExpandedActionView, right, collapsingMargins, minHeight);
            else left = layoutChildLeft(mExpandedActionView, left, collapsingMargins, minHeight);
        }
        if (shouldLayout(mLogoView)) {
            if (isRtl) right = layoutChildRight(mLogoView, right, collapsingMargins, minHeight);
            else left = layoutChildLeft(mLogoView, left, collapsingMargins, minHeight);
        }
        final boolean layoutTitle = shouldLayout(mTitleTextView);
        final boolean layoutSubtitle = shouldLayout(mSubtitleTextView);
        int titleHeight = 0;
        if (layoutTitle) {
            final LayoutParams lp = (LayoutParams) mTitleTextView.getLayoutParams();
            titleHeight += lp.topMargin + mTitleTextView.getMeasuredHeight() + lp.bottomMargin;
        }
        if (layoutSubtitle) {
            final LayoutParams lp = (LayoutParams) mSubtitleTextView.getLayoutParams();
            titleHeight += lp.topMargin + mSubtitleTextView.getMeasuredHeight() + lp.bottomMargin;
        }
        if (layoutTitle || layoutSubtitle) {
            int titleTop;
            final View topChild = layoutTitle ? mTitleTextView : mSubtitleTextView;
            final View bottomChild = layoutSubtitle ? mSubtitleTextView : mTitleTextView;
            final LayoutParams toplp = (LayoutParams) topChild.getLayoutParams();
            final LayoutParams bottomlp = (LayoutParams) bottomChild.getLayoutParams();
            final boolean titleHasWidth = layoutTitle && mTitleTextView.getMeasuredWidth() > 0
                    || layoutSubtitle && mSubtitleTextView.getMeasuredWidth() > 0;
            switch (mGravity & Gravity.VERTICAL_GRAVITY_MASK) {
                case Gravity.TOP:
                    titleTop = getPaddingTop() + toplp.topMargin + mTitleMarginTop;
                    break;
                default:
                case Gravity.CENTER_VERTICAL:
                    final int space = height - paddingTop - paddingBottom;
                    int spaceAbove = (space - titleHeight) / 2;
                    if (spaceAbove < toplp.topMargin + mTitleMarginTop) {
                        spaceAbove = toplp.topMargin + mTitleMarginTop;
                    } else {
                        final int spaceBelow = height - paddingBottom - titleHeight - spaceAbove - paddingTop;
                        if (spaceBelow < toplp.bottomMargin + mTitleMarginBottom) {
                            spaceAbove = Math.max(0, spaceAbove - (bottomlp.bottomMargin + mTitleMarginBottom - spaceBelow));
                        }
                    }
                    titleTop = paddingTop + spaceAbove;
                    break;
                case Gravity.BOTTOM:
                    titleTop = height - paddingBottom - bottomlp.bottomMargin - mTitleMarginBottom - titleHeight;
                    break;
            }
            if (isRtl) {
                final int rd = (titleHasWidth ? mTitleMarginStart : 0) - collapsingMargins[1];
                right -= Math.max(0, rd);
                collapsingMargins[1] = Math.max(0, -rd);
                int titleRight = right;
                int subtitleRight = right;
                if (layoutTitle) {
                    final LayoutParams lp = (LayoutParams) mTitleTextView.getLayoutParams();
                    final int titleLeft = titleRight - mTitleTextView.getMeasuredWidth();
                    final int titleBottom = titleTop + mTitleTextView.getMeasuredHeight();
                    mTitleTextView.layout(titleLeft, titleTop, titleRight, titleBottom);
                    titleRight = titleLeft - mTitleMarginEnd;
                    titleTop = titleBottom + lp.bottomMargin;
                }
                if (layoutSubtitle) {
                    final LayoutParams lp = (LayoutParams) mSubtitleTextView.getLayoutParams();
                    titleTop += lp.topMargin;
                    final int subtitleLeft = subtitleRight - mSubtitleTextView.getMeasuredWidth();
                    final int subtitleBottom = titleTop + mSubtitleTextView.getMeasuredHeight();
                    mSubtitleTextView.layout(subtitleLeft, titleTop, subtitleRight, subtitleBottom);
                    subtitleRight = subtitleRight - mTitleMarginEnd;
                    titleTop = subtitleBottom + lp.bottomMargin;
                }
                if (titleHasWidth) right = Math.min(titleRight, subtitleRight);
            } else {
                final int ld = (titleHasWidth ? mTitleMarginStart : 0) - collapsingMargins[0];
                left += Math.max(0, ld);
                collapsingMargins[0] = Math.max(0, -ld);
                int titleLeft = left;
                int subtitleLeft = left;
                if (layoutTitle) {
                    final LayoutParams lp = (LayoutParams) mTitleTextView.getLayoutParams();
                    final int titleRight = titleLeft + mTitleTextView.getMeasuredWidth();
                    final int titleBottom = titleTop + mTitleTextView.getMeasuredHeight();
                    mTitleTextView.layout(titleLeft, titleTop, titleRight, titleBottom);
                    titleLeft = titleRight + mTitleMarginEnd;
                    titleTop = titleBottom + lp.bottomMargin;
                }
                if (layoutSubtitle) {
                    final LayoutParams lp = (LayoutParams) mSubtitleTextView.getLayoutParams();
                    titleTop += lp.topMargin;
                    final int subtitleRight = subtitleLeft + mSubtitleTextView.getMeasuredWidth();
                    final int subtitleBottom = titleTop + mSubtitleTextView.getMeasuredHeight();
                    mSubtitleTextView.layout(subtitleLeft, titleTop, subtitleRight, subtitleBottom);
                    subtitleLeft = subtitleRight + mTitleMarginEnd;
                    titleTop = subtitleBottom + lp.bottomMargin;
                }
                if (titleHasWidth) left = Math.max(titleLeft, subtitleLeft);
            }
        }
        // Custom views: left, right, then centered within the space left over.
        addCustomViewsWithGravity(mTempViews, Gravity.LEFT);
        final int leftViewsCount = mTempViews.size();
        for (int i = 0; i < leftViewsCount; i++) {
            left = layoutChildLeft(mTempViews.get(i), left, collapsingMargins, minHeight);
        }
        addCustomViewsWithGravity(mTempViews, Gravity.RIGHT);
        final int rightViewsCount = mTempViews.size();
        for (int i = 0; i < rightViewsCount; i++) {
            right = layoutChildRight(mTempViews.get(i), right, collapsingMargins, minHeight);
        }
        addCustomViewsWithGravity(mTempViews, Gravity.CENTER_HORIZONTAL);
        final int centerViewsWidth = getViewListMeasuredWidth(mTempViews, collapsingMargins);
        final int parentCenter = paddingLeft + (width - paddingLeft - paddingRight) / 2;
        final int halfCenterViewsWidth = centerViewsWidth / 2;
        int centerLeft = parentCenter - halfCenterViewsWidth;
        final int centerRight = centerLeft + centerViewsWidth;
        if (centerLeft < left) centerLeft = left;
        else if (centerRight > right) centerLeft -= centerRight - right;
        final int centerViewsCount = mTempViews.size();
        for (int i = 0; i < centerViewsCount; i++) {
            centerLeft = layoutChildLeft(mTempViews.get(i), centerLeft, collapsingMargins, minHeight);
        }
        mTempViews.clear();
    }

    private int getViewListMeasuredWidth(List<View> views, int[] collapsingMargins) {
        int collapseLeft = collapsingMargins[0];
        int collapseRight = collapsingMargins[1];
        int width = 0;
        final int count = views.size();
        for (int i = 0; i < count; i++) {
            final View v = views.get(i);
            final LayoutParams lp = (LayoutParams) v.getLayoutParams();
            final int l = lp.leftMargin - collapseLeft;
            final int r = lp.rightMargin - collapseRight;
            final int leftMargin = Math.max(0, l);
            final int rightMargin = Math.max(0, r);
            collapseLeft = Math.max(0, -l);
            collapseRight = Math.max(0, -r);
            width += leftMargin + v.getMeasuredWidth() + rightMargin;
        }
        return width;
    }

    private int layoutChildLeft(View child, int left, int[] collapsingMargins, int alignmentHeight) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int l = lp.leftMargin - collapsingMargins[0];
        left += Math.max(0, l);
        collapsingMargins[0] = Math.max(0, -l);
        final int top = getChildTop(child, alignmentHeight);
        final int childWidth = child.getMeasuredWidth();
        child.layout(left, top, left + childWidth, top + child.getMeasuredHeight());
        left += childWidth + lp.rightMargin;
        return left;
    }

    private int layoutChildRight(View child, int right, int[] collapsingMargins, int alignmentHeight) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int r = lp.rightMargin - collapsingMargins[1];
        right -= Math.max(0, r);
        collapsingMargins[1] = Math.max(0, -r);
        final int top = getChildTop(child, alignmentHeight);
        final int childWidth = child.getMeasuredWidth();
        child.layout(right - childWidth, top, right, top + child.getMeasuredHeight());
        right -= childWidth + lp.leftMargin;
        return right;
    }

    private int getChildTop(View child, int alignmentHeight) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        final int childHeight = child.getMeasuredHeight();
        final int alignmentOffset = alignmentHeight > 0 ? (childHeight - alignmentHeight) / 2 : 0;
        switch (getChildVerticalGravity(lp.gravity)) {
            case Gravity.TOP:
                return getPaddingTop() - alignmentOffset;
            case Gravity.BOTTOM:
                return getHeight() - getPaddingBottom() - childHeight - lp.bottomMargin - alignmentOffset;
            default:
            case Gravity.CENTER_VERTICAL:
                final int paddingTop = getPaddingTop();
                final int paddingBottom = getPaddingBottom();
                final int height = getHeight();
                final int space = height - paddingTop - paddingBottom;
                int spaceAbove = (space - childHeight) / 2;
                if (spaceAbove < lp.topMargin) {
                    spaceAbove = lp.topMargin;
                } else {
                    final int spaceBelow = height - paddingBottom - childHeight - spaceAbove - paddingTop;
                    if (spaceBelow < lp.bottomMargin) spaceAbove = Math.max(0, spaceAbove - (lp.bottomMargin - spaceBelow));
                }
                return paddingTop + spaceAbove;
        }
    }

    private int getChildVerticalGravity(int gravity) {
        final int vgrav = gravity & Gravity.VERTICAL_GRAVITY_MASK;
        switch (vgrav) {
            case Gravity.TOP:
            case Gravity.BOTTOM:
            case Gravity.CENTER_VERTICAL:
                return vgrav;
            default:
                return mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        }
    }

    private void addCustomViewsWithGravity(List<View> views, int gravity) {
        final boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        final int childCount = getChildCount();
        final int absGrav = Gravity.getAbsoluteGravity(gravity, getLayoutDirection());
        views.clear();
        if (isRtl) {
            for (int i = childCount - 1; i >= 0; i--) {
                final View child = getChildAt(i);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (lp.mViewType == LayoutParams.CUSTOM && shouldLayout(child)
                        && getChildHorizontalGravity(lp.gravity) == absGrav) {
                    views.add(child);
                }
            }
        } else {
            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (lp.mViewType == LayoutParams.CUSTOM && shouldLayout(child)
                        && getChildHorizontalGravity(lp.gravity) == absGrav) {
                    views.add(child);
                }
            }
        }
    }

    private int getChildHorizontalGravity(int gravity) {
        final int ld = getLayoutDirection();
        final int absGrav = Gravity.getAbsoluteGravity(gravity, ld);
        final int hGrav = absGrav & Gravity.HORIZONTAL_GRAVITY_MASK;
        switch (hGrav) {
            case Gravity.LEFT:
            case Gravity.RIGHT:
            case Gravity.CENTER_HORIZONTAL:
                return hGrav;
            default:
                return ld == LAYOUT_DIRECTION_RTL ? Gravity.RIGHT : Gravity.LEFT;
        }
    }

    private boolean shouldLayout(View view) {
        return view != null && view.getParent() == this && view.getVisibility() != GONE;
    }

    private int getHorizontalMargins(View v) {
        final MarginLayoutParams mlp = (MarginLayoutParams) v.getLayoutParams();
        return mlp.getMarginStart() + mlp.getMarginEnd();
    }

    private int getVerticalMargins(View v) {
        final MarginLayoutParams mlp = (MarginLayoutParams) v.getLayoutParams();
        return mlp.topMargin + mlp.bottomMargin;
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) { return new LayoutParams(getContext(), attrs); }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        if (p instanceof LayoutParams) return new LayoutParams((LayoutParams) p);
        if (p instanceof ActionBar.LayoutParams) return new LayoutParams((ActionBar.LayoutParams) p);
        if (p instanceof MarginLayoutParams) return new LayoutParams((MarginLayoutParams) p);
        return new LayoutParams(p);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return super.checkLayoutParams(p) && p instanceof LayoutParams;
    }

    private static boolean isCustomView(View child) {
        return ((LayoutParams) child.getLayoutParams()).mViewType == LayoutParams.CUSTOM;
    }

    /** framework-internal (hidden in AOSP). */
    public DecorToolbar getWrapper() {
        if (mWrapper == null) mWrapper = new ToolbarWidgetWrapper(this, true);
        return mWrapper;
    }

    void removeChildrenForExpandedActionView() {
        final int childCount = getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp.mViewType != LayoutParams.EXPANDED && child != mMenuView) {
                removeViewAt(i);
                mHiddenViews.add(child);
            }
        }
    }

    void addChildrenForExpandedActionView() {
        final int count = mHiddenViews.size();
        for (int i = count - 1; i >= 0; i--) addView(mHiddenViews.get(i));
        mHiddenViews.clear();
    }

    private boolean isChildOrHidden(View child) { return child.getParent() == this || mHiddenViews.contains(child); }

    /** framework-internal (hidden in AOSP). */
    public void setCollapsible(boolean collapsible) {
        mCollapsible = collapsible;
        requestLayout();
    }

    /** framework-internal (hidden in AOSP). */
    public void setMenuCallbacks(MenuPresenter.Callback pcb, MenuBuilder.Callback mcb) {
        mActionMenuPresenterCallback = pcb;
        mMenuBuilderCallback = mcb;
        if (mMenuView != null) mMenuView.setMenuCallbacks(pcb, mcb);
    }

    private void ensureContentInsets() {
        if (mContentInsets == null) mContentInsets = new RtlSpacingHelper();
    }

    /** framework-internal (hidden in AOSP). */
    ActionMenuPresenter getOuterActionMenuPresenter() { return mOuterActionMenuPresenter; }

    /** framework-internal (hidden in AOSP). */
    Context getPopupContext() { return mPopupContext; }

    public interface OnMenuItemClickListener {
        boolean onMenuItemClick(MenuItem item);
    }

    public static class LayoutParams extends ActionBar.LayoutParams {
        static final int CUSTOM = 0;
        static final int SYSTEM = 1;
        static final int EXPANDED = 2;

        int mViewType = CUSTOM;

        public LayoutParams(Context c, AttributeSet attrs) { super(c, attrs); }

        public LayoutParams(int width, int height) {
            super(width, height);
            this.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        }

        public LayoutParams(int width, int height, int gravity) {
            super(width, height);
            this.gravity = gravity;
        }

        public LayoutParams(int gravity) { this(WRAP_CONTENT, MATCH_PARENT, gravity); }

        public LayoutParams(LayoutParams source) {
            super(source);
            mViewType = source.mViewType;
        }

        public LayoutParams(ActionBar.LayoutParams source) { super(source); }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
            // ActionBar.LayoutParams doesn't have a MarginLayoutParams constructor.
            // Fake it here and copy over the relevant data.
            copyMarginsFrom(source);
        }

        public LayoutParams(ViewGroup.LayoutParams source) { super(source); }
    }

    static class SavedState extends BaseSavedState {
        public int expandedMenuItemId;
        public boolean isOverflowOpen;

        SavedState(Parcel source) {
            super(source);
            expandedMenuItemId = source.readInt();
            isOverflowOpen = source.readInt() != 0;
        }

        SavedState(Parcelable superState) { super(superState); }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(expandedMenuItemId);
            out.writeInt(isOverflowOpen ? 1 : 0);
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel source) { return new SavedState(source); }
            public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }

    /** Shows a collapsible action view in place of the toolbar's contents (SearchView and such). */
    private class ExpandedActionViewMenuPresenter implements MenuPresenter {
        MenuBuilder mMenu;
        MenuItemImpl mCurrentExpandedItem;

        public void initForMenu(Context context, MenuBuilder menu) {
            if (mMenu != null && mCurrentExpandedItem != null) mMenu.collapseItemActionView(mCurrentExpandedItem);
            mMenu = menu;
        }

        public MenuView getMenuView(ViewGroup root) { return null; }

        public void updateMenuView(boolean cleared) {
            if (mCurrentExpandedItem != null) {
                boolean found = false;
                if (mMenu != null) {
                    final int count = mMenu.size();
                    for (int i = 0; i < count; i++) {
                        if (mMenu.getItem(i) == mCurrentExpandedItem) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) collapseItemActionView(mMenu, mCurrentExpandedItem);
            }
        }

        public void setCallback(Callback cb) {}

        public boolean onSubMenuSelected(SubMenuBuilder subMenu) { return false; }

        public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {}

        public boolean flagActionItems() { return false; }

        public boolean expandItemActionView(MenuBuilder menu, MenuItemImpl item) {
            ensureCollapseButtonView();
            if (mCollapseButtonView.getParent() != Toolbar.this) addView(mCollapseButtonView);
            mExpandedActionView = item.getActionView();
            mCurrentExpandedItem = item;
            if (mExpandedActionView.getParent() != Toolbar.this) {
                final LayoutParams lp = generateDefaultLayoutParams();
                lp.gravity = Gravity.START | (mButtonGravity & Gravity.VERTICAL_GRAVITY_MASK);
                lp.mViewType = LayoutParams.EXPANDED;
                mExpandedActionView.setLayoutParams(lp);
                addView(mExpandedActionView);
            }
            removeChildrenForExpandedActionView();
            requestLayout();
            if (mExpandedActionView instanceof CollapsibleActionView) {
                ((CollapsibleActionView) mExpandedActionView).onActionViewExpanded();
            }
            return true;
        }

        public boolean collapseItemActionView(MenuBuilder menu, MenuItemImpl item) {
            if (mExpandedActionView instanceof CollapsibleActionView) {
                ((CollapsibleActionView) mExpandedActionView).onActionViewCollapsed();
            }
            removeView(mExpandedActionView);
            removeView(mCollapseButtonView);
            mExpandedActionView = null;
            addChildrenForExpandedActionView();
            mCurrentExpandedItem = null;
            requestLayout();
            return true;
        }

        public int getId() { return 0; }

        public Parcelable onSaveInstanceState() { return null; }

        public void onRestoreInstanceState(Parcelable state) {}
    }
}
