package com.android.internal.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toolbar;
import com.android.internal.util.InternalRes;
import com.android.internal.view.menu.MenuPresenter;

/**
 * framework-internal. Port of AOSP ActionBarOverlayLayout: the root of the
 * action bar decor. Lays out the action bar container over the content and
 * pushes the content below it unless the window is in overlay mode. There
 * are no system insets on this platform, and hiding on content scroll only
 * honours explicit hide offsets.
 */
public class ActionBarOverlayLayout extends ViewGroup implements DecorContentParent {
    private int mActionBarHeight;
    private int mWindowVisibility = View.VISIBLE;
    private View mContent;
    private ActionBarContainer mActionBarTop;
    private ActionBarContainer mActionBarBottom;
    private DecorToolbar mDecorToolbar;
    private Drawable mWindowContentOverlay;
    private boolean mIgnoreWindowContentOverlay;
    private boolean mOverlayMode;
    private boolean mHasNonEmbeddedTabs;
    private boolean mHideOnContentScroll;
    private ActionBarVisibilityCallback mActionBarVisibilityCallback;
    private final Rect mContentInsets = new Rect();
    private final Rect mLastContentInsets = new Rect();

    public ActionBarOverlayLayout(Context context) { this(context, null); }

    public ActionBarOverlayLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        final TypedArray ta = getContext().getTheme().obtainStyledAttributes(
                new int[] {android.R.attr.actionBarSize, android.R.attr.windowContentOverlay});
        mActionBarHeight = ta.getDimensionPixelSize(0, 0);
        mWindowContentOverlay = ta.getDrawable(1);
        setWillNotDraw(mWindowContentOverlay == null);
        ta.recycle();
        mIgnoreWindowContentOverlay = context.getApplicationInfo().targetSdkVersion < Build.VERSION_CODES.KITKAT;
    }

    public void setActionBarVisibilityCallback(ActionBarVisibilityCallback cb) {
        mActionBarVisibilityCallback = cb;
        if (getWindowToken() != null) mActionBarVisibilityCallback.onWindowVisibilityChanged(mWindowVisibility);
    }

    public void setOverlayMode(boolean overlayMode) {
        mOverlayMode = overlayMode;
        mIgnoreWindowContentOverlay = overlayMode
                && getContext().getApplicationInfo().targetSdkVersion < Build.VERSION_CODES.KITKAT;
    }

    public boolean isInOverlayMode() { return mOverlayMode; }

    public void setHasNonEmbeddedTabs(boolean hasNonEmbeddedTabs) { mHasNonEmbeddedTabs = hasNonEmbeddedTabs; }

    public void setShowingForActionMode(boolean showing) {}

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mWindowVisibility = visibility;
        if (mActionBarVisibilityCallback != null) mActionBarVisibilityCallback.onWindowVisibilityChanged(visibility);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) { return new LayoutParams(getContext(), attrs); }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) { return new LayoutParams(p); }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof LayoutParams; }

    private static boolean applyInsets(View view, Rect insets, boolean left, boolean top, boolean bottom, boolean right) {
        boolean changed = false;
        final LayoutParams lp = (LayoutParams) view.getLayoutParams();
        if (left && lp.leftMargin != insets.left) {
            changed = true;
            lp.leftMargin = insets.left;
        }
        if (top && lp.topMargin != insets.top) {
            changed = true;
            lp.topMargin = insets.top;
        }
        if (right && lp.rightMargin != insets.right) {
            changed = true;
            lp.rightMargin = insets.right;
        }
        if (bottom && lp.bottomMargin != insets.bottom) {
            changed = true;
            lp.bottomMargin = insets.bottom;
        }
        return changed;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        pullChildren();
        int maxHeight = 0;
        int maxWidth = 0;
        int childState = 0;
        int topInset = 0;
        int bottomInset = 0;

        measureChildWithMargins(mActionBarTop, widthMeasureSpec, 0, heightMeasureSpec, 0);
        LayoutParams lp = (LayoutParams) mActionBarTop.getLayoutParams();
        maxWidth = Math.max(maxWidth, mActionBarTop.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
        maxHeight = Math.max(maxHeight, mActionBarTop.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
        childState = combineMeasuredStates(childState, mActionBarTop.getMeasuredState());

        if (mActionBarBottom != null) {
            measureChildWithMargins(mActionBarBottom, widthMeasureSpec, 0, heightMeasureSpec, 0);
            lp = (LayoutParams) mActionBarBottom.getLayoutParams();
            maxWidth = Math.max(maxWidth, mActionBarBottom.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
            maxHeight = Math.max(maxHeight, mActionBarBottom.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
            childState = combineMeasuredStates(childState, mActionBarBottom.getMeasuredState());
        }

        final int vis = getWindowSystemUiVisibility();
        final boolean stable = (vis & SYSTEM_UI_FLAG_LAYOUT_STABLE) != 0;
        if (stable) {
            topInset = mActionBarHeight;
        } else if (mActionBarTop.getVisibility() != GONE) {
            topInset = mActionBarTop.getMeasuredHeight();
        }
        if (mDecorToolbar.isSplit() && mActionBarBottom != null) {
            bottomInset = stable ? mActionBarHeight : mActionBarBottom.getMeasuredHeight();
        }

        mContentInsets.setEmpty();
        if (!mOverlayMode && !stable) {
            mContentInsets.top += topInset;
            mContentInsets.bottom += bottomInset;
        }
        applyInsets(mContent, mContentInsets, true, true, true, true);
        mLastContentInsets.set(mContentInsets);

        measureChildWithMargins(mContent, widthMeasureSpec, 0, heightMeasureSpec, 0);
        lp = (LayoutParams) mContent.getLayoutParams();
        maxWidth = Math.max(maxWidth, mContent.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
        maxHeight = Math.max(maxHeight, mContent.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
        childState = combineMeasuredStates(childState, mContent.getMeasuredState());

        maxWidth += getPaddingLeft() + getPaddingRight();
        maxHeight += getPaddingTop() + getPaddingBottom();
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());
        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
                resolveSizeAndState(maxHeight, heightMeasureSpec, childState << MEASURED_HEIGHT_STATE_SHIFT));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int count = getChildCount();
        final int parentLeft = getPaddingLeft();
        final int parentTop = getPaddingTop();
        final int parentBottom = bottom - top - getPaddingBottom();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final int width = child.getMeasuredWidth();
            final int height = child.getMeasuredHeight();
            final int childLeft = parentLeft + lp.leftMargin;
            final int childTop = child == mActionBarBottom ? parentBottom - height - lp.bottomMargin
                    : parentTop + lp.topMargin;
            child.layout(childLeft, childTop, childLeft + width, childTop + height);
        }
    }

    @Override
    public void draw(Canvas c) {
        super.draw(c);
        if (mWindowContentOverlay != null && !mIgnoreWindowContentOverlay) {
            final int top = mActionBarTop.getVisibility() == VISIBLE
                    ? (int) (mActionBarTop.getBottom() + mActionBarTop.getTranslationY() + 0.5f) : 0;
            mWindowContentOverlay.setBounds(0, top, getWidth(), top + mWindowContentOverlay.getIntrinsicHeight());
            mWindowContentOverlay.draw(c);
        }
    }

    @Override
    public boolean shouldDelayChildPressedState() { return false; }

    void pullChildren() {
        if (mContent == null) {
            mContent = findViewById(android.R.id.content);
            mActionBarTop = (ActionBarContainer) findViewById(InternalRes.viewId("action_bar_container"));
            mDecorToolbar = getDecorToolbar(findViewById(InternalRes.viewId("action_bar")));
            final View bottom = findViewById(InternalRes.viewId("split_action_bar"));
            mActionBarBottom = bottom instanceof ActionBarContainer ? (ActionBarContainer) bottom : null;
        }
    }

    private static DecorToolbar getDecorToolbar(View view) {
        if (view instanceof DecorToolbar) return (DecorToolbar) view;
        if (view instanceof Toolbar) return ((Toolbar) view).getWrapper();
        throw new IllegalStateException("Can't make a decor toolbar out of "
                + (view != null ? view.getClass().getSimpleName() : "null"));
    }

    public void setHideOnContentScrollEnabled(boolean hideOnContentScroll) {
        if (hideOnContentScroll != mHideOnContentScroll) {
            mHideOnContentScroll = hideOnContentScroll;
            if (!hideOnContentScroll) setActionBarHideOffset(0);
        }
    }

    public boolean isHideOnContentScrollEnabled() { return mHideOnContentScroll; }

    public int getActionBarHideOffset() { return mActionBarTop != null ? -((int) mActionBarTop.getTranslationY()) : 0; }

    public void setActionBarHideOffset(int offset) {
        pullChildren();
        final int topHeight = mActionBarTop.getHeight();
        offset = Math.max(0, Math.min(offset, topHeight));
        mActionBarTop.setTranslationY(-offset);
        if (mActionBarBottom != null && mActionBarBottom.getVisibility() != GONE) {
            final float fOffset = (float) offset / topHeight;
            final int bOffset = (int) (mActionBarBottom.getHeight() * fOffset);
            mActionBarBottom.setTranslationY(bOffset);
        }
    }

    @Override
    public void setWindowCallback(Window.Callback cb) {
        pullChildren();
        mDecorToolbar.setWindowCallback(cb);
    }

    @Override
    public void setWindowTitle(CharSequence title) {
        pullChildren();
        mDecorToolbar.setWindowTitle(title);
    }

    @Override
    public CharSequence getTitle() {
        pullChildren();
        return mDecorToolbar.getTitle();
    }

    @Override
    public void initFeature(int windowFeature) {
        pullChildren();
        switch (windowFeature) {
            case Window.FEATURE_PROGRESS:
                mDecorToolbar.initProgress();
                break;
            case Window.FEATURE_INDETERMINATE_PROGRESS:
                mDecorToolbar.initIndeterminateProgress();
                break;
            case Window.FEATURE_ACTION_BAR_OVERLAY:
                setOverlayMode(true);
                break;
            default:
                break;
        }
    }

    @Override
    public void setUiOptions(int uiOptions) {
        // Split action bars are not ported; the toolbar never splits.
    }

    @Override
    public boolean hasIcon() {
        pullChildren();
        return mDecorToolbar.hasIcon();
    }

    @Override
    public boolean hasLogo() {
        pullChildren();
        return mDecorToolbar.hasLogo();
    }

    @Override
    public void setIcon(int resId) {
        pullChildren();
        mDecorToolbar.setIcon(resId);
    }

    @Override
    public void setIcon(Drawable d) {
        pullChildren();
        mDecorToolbar.setIcon(d);
    }

    @Override
    public void setLogo(int resId) {
        pullChildren();
        mDecorToolbar.setLogo(resId);
    }

    @Override
    public boolean canShowOverflowMenu() {
        pullChildren();
        return mDecorToolbar.canShowOverflowMenu();
    }

    @Override
    public boolean isOverflowMenuShowing() {
        pullChildren();
        return mDecorToolbar.isOverflowMenuShowing();
    }

    @Override
    public boolean isOverflowMenuShowPending() {
        pullChildren();
        return mDecorToolbar.isOverflowMenuShowPending();
    }

    @Override
    public boolean showOverflowMenu() {
        pullChildren();
        return mDecorToolbar.showOverflowMenu();
    }

    @Override
    public boolean hideOverflowMenu() {
        pullChildren();
        return mDecorToolbar.hideOverflowMenu();
    }

    @Override
    public void setMenuPrepared() {
        pullChildren();
        mDecorToolbar.setMenuPrepared();
    }

    @Override
    public void setMenu(Menu menu, MenuPresenter.Callback cb) {
        pullChildren();
        mDecorToolbar.setMenu(menu, cb);
    }

    @Override
    public void saveToolbarHierarchyState(SparseArray<Parcelable> toolbarStates) {
        pullChildren();
        mDecorToolbar.saveHierarchyState(toolbarStates);
    }

    @Override
    public void restoreToolbarHierarchyState(SparseArray<Parcelable> toolbarStates) {
        pullChildren();
        mDecorToolbar.restoreHierarchyState(toolbarStates);
    }

    @Override
    public void dismissPopups() {
        pullChildren();
        mDecorToolbar.dismissPopupMenus();
    }

    public static class LayoutParams extends MarginLayoutParams {
        public LayoutParams(Context c, AttributeSet attrs) { super(c, attrs); }

        public LayoutParams(int width, int height) { super(width, height); }

        public LayoutParams(ViewGroup.LayoutParams source) { super(source); }

        public LayoutParams(ViewGroup.MarginLayoutParams source) { super(source); }
    }

    /** framework-internal. Implemented by WindowDecorActionBar. */
    public interface ActionBarVisibilityCallback {
        void onWindowVisibilityChanged(int visibility);

        void showForSystem();

        void hideForSystem();

        void enableContentAnimations(boolean enable);

        void onContentScrollStarted();

        void onContentScrollStopped();
    }
}
