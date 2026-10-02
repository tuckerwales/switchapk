package com.android.internal.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ActionMenuPresenter;
import android.widget.ActionMenuView;

/**
 * framework-internal. Port of AOSP AbsActionBarView, the base of the action
 * mode bar. Visibility changes are immediate (property animators are not wired here).
 */
public abstract class AbsActionBarView extends ViewGroup {
    protected final Context mPopupContext;
    protected ActionMenuView mMenuView;
    protected ActionMenuPresenter mActionMenuPresenter;
    protected ViewGroup mSplitView;
    protected boolean mSplitActionBar;
    protected boolean mSplitWhenNarrow;
    protected int mContentHeight;

    private boolean mEatingTouch;
    private boolean mEatingHover;

    public AbsActionBarView(Context context) { this(context, null); }

    public AbsActionBarView(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public AbsActionBarView(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public AbsActionBarView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.actionBarPopupTheme, tv, true) && tv.resourceId != 0) {
            mPopupContext = new ContextThemeWrapper(context, tv.resourceId);
        } else {
            mPopupContext = context;
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        final TypedArray a = getContext().obtainStyledAttributes(null, new int[] {android.R.attr.height},
                android.R.attr.actionBarStyle, 0);
        setContentHeight(a.getLayoutDimension(0, 0));
        a.recycle();
        if (mSplitWhenNarrow) setSplitToolbar(false);
        if (mActionMenuPresenter != null) mActionMenuPresenter.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Action bars always eat touch events so they do not reach views behind them.
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) mEatingTouch = false;
        if (!mEatingTouch) {
            final boolean handled = super.onTouchEvent(ev);
            if (action == MotionEvent.ACTION_DOWN && !handled) mEatingTouch = true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) mEatingTouch = false;
        return true;
    }

    @Override
    public boolean onHoverEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_ENTER) mEatingHover = false;
        if (!mEatingHover) {
            final boolean handled = super.onHoverEvent(ev);
            if (action == MotionEvent.ACTION_HOVER_ENTER && !handled) mEatingHover = true;
        }
        if (action == MotionEvent.ACTION_HOVER_EXIT || action == MotionEvent.ACTION_CANCEL) mEatingHover = false;
        return true;
    }

    public void setSplitToolbar(boolean split) { mSplitActionBar = split; }

    public void setSplitWhenNarrow(boolean splitWhenNarrow) { mSplitWhenNarrow = splitWhenNarrow; }

    public void setContentHeight(int height) {
        mContentHeight = height;
        requestLayout();
    }

    public int getContentHeight() { return mContentHeight; }

    public void setSplitView(ViewGroup splitView) { mSplitView = splitView; }

    public int getAnimatedVisibility() { return getVisibility(); }

    public void animateToVisibility(int visibility) { setVisibility(visibility); }

    @Override
    public void setVisibility(int visibility) {
        if (visibility != getVisibility()) super.setVisibility(visibility);
    }

    public boolean showOverflowMenu() { return mActionMenuPresenter != null && mActionMenuPresenter.showOverflowMenu(); }

    public void postShowOverflowMenu() {
        post(new Runnable() {
            public void run() { showOverflowMenu(); }
        });
    }

    public boolean hideOverflowMenu() { return mActionMenuPresenter != null && mActionMenuPresenter.hideOverflowMenu(); }

    public boolean isOverflowMenuShowing() {
        return mActionMenuPresenter != null && mActionMenuPresenter.isOverflowMenuShowing();
    }

    public boolean isOverflowMenuShowPending() {
        return mActionMenuPresenter != null && mActionMenuPresenter.isOverflowMenuShowPending();
    }

    public boolean isOverflowReserved() { return mActionMenuPresenter != null && mActionMenuPresenter.isOverflowReserved(); }

    public boolean canShowOverflowMenu() { return isOverflowReserved() && getVisibility() == VISIBLE; }

    public void dismissPopupMenus() {
        if (mActionMenuPresenter != null) mActionMenuPresenter.dismissPopupMenus();
    }

    protected int measureChildView(View child, int availableWidth, int childSpecHeight, int spacing) {
        child.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), childSpecHeight);
        availableWidth -= child.getMeasuredWidth();
        availableWidth -= spacing;
        return Math.max(0, availableWidth);
    }

    protected static int next(int x, int val, boolean isRtl) { return isRtl ? x - val : x + val; }

    protected int positionChild(View child, int x, int y, int contentHeight, boolean reverse) {
        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();
        final int childTop = y + (contentHeight - childHeight) / 2;
        if (reverse) {
            child.layout(x - childWidth, childTop, x, childTop + childHeight);
        } else {
            child.layout(x, childTop, x + childWidth, childTop + childHeight);
        }
        return reverse ? -childWidth : childWidth;
    }
}
