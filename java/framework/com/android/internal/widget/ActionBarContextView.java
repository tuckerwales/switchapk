package com.android.internal.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ActionMenuPresenter;
import android.widget.ActionMenuView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.internal.util.InternalRes;
import com.android.internal.view.menu.MenuBuilder;

/**
 * framework-internal. Port of AOSP ActionBarContextView: the bar an action
 * mode shows (close button, title, custom view and the mode's menu).
 */
public class ActionBarContextView extends AbsActionBarView {
    private CharSequence mTitle;
    private CharSequence mSubtitle;
    private View mClose;
    private View mCustomView;
    private LinearLayout mTitleLayout;
    private TextView mTitleView;
    private TextView mSubtitleView;
    private final int mTitleStyleRes;
    private final int mSubtitleStyleRes;
    private Drawable mSplitBackground;
    private boolean mTitleOptional;
    private final int mCloseItemLayout;

    public ActionBarContextView(Context context) { this(context, null); }

    public ActionBarContextView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.actionModeStyle);
    }

    public ActionBarContextView(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public ActionBarContextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.background, android.R.attr.titleTextStyle, android.R.attr.subtitleTextStyle,
                android.R.attr.height, android.R.attr.backgroundSplit, InternalRes.attr("closeItemLayout")},
                defStyleAttr, defStyleRes);
        setBackground(a.getDrawable(0));
        mTitleStyleRes = a.getResourceId(1, 0);
        mSubtitleStyleRes = a.getResourceId(2, 0);
        mContentHeight = a.getLayoutDimension(3, 0);
        mSplitBackground = a.getDrawable(4);
        int closeLayout = a.getResourceId(5, 0);
        if (closeLayout == 0) closeLayout = InternalRes.layout("action_mode_close_item_material");
        mCloseItemLayout = closeLayout;
        a.recycle();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mActionMenuPresenter != null) {
            mActionMenuPresenter.hideOverflowMenu();
            mActionMenuPresenter.dismissPopupMenus();
        }
    }

    @Override
    public void setSplitToolbar(boolean split) {
        if (mSplitActionBar != split) {
            if (mActionMenuPresenter != null) {
                final LayoutParams layoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
                final ViewGroup oldParent = (ViewGroup) mMenuView.getParent();
                if (oldParent != null) oldParent.removeView(mMenuView);
                if (!split) {
                    mMenuView = (ActionMenuView) mActionMenuPresenter.getMenuView(this);
                    mMenuView.setBackground(null);
                    addView(mMenuView, layoutParams);
                } else {
                    mActionMenuPresenter.setWidthLimit(getContext().getResources().getDisplayMetrics().widthPixels, true);
                    mActionMenuPresenter.setItemLimit(Integer.MAX_VALUE);
                    layoutParams.width = LayoutParams.MATCH_PARENT;
                    layoutParams.height = mContentHeight;
                    mMenuView = (ActionMenuView) mActionMenuPresenter.getMenuView(this);
                    mMenuView.setBackground(mSplitBackground);
                    mSplitView.addView(mMenuView, layoutParams);
                }
            }
            super.setSplitToolbar(split);
        }
    }

    public void setCustomView(View view) {
        if (mCustomView != null) removeView(mCustomView);
        mCustomView = view;
        if (view != null && mTitleLayout != null) {
            removeView(mTitleLayout);
            mTitleLayout = null;
        }
        if (view != null) addView(view);
        requestLayout();
    }

    public void setTitle(CharSequence title) {
        mTitle = title;
        initTitle();
    }

    public void setSubtitle(CharSequence subtitle) {
        mSubtitle = subtitle;
        initTitle();
    }

    public CharSequence getTitle() { return mTitle; }

    public CharSequence getSubtitle() { return mSubtitle; }

    private void initTitle() {
        if (mTitleLayout == null) {
            final LayoutInflater inflater = LayoutInflater.from(getContext());
            inflater.inflate(InternalRes.layout("action_bar_title_item"), this);
            mTitleLayout = (LinearLayout) getChildAt(getChildCount() - 1);
            mTitleView = (TextView) mTitleLayout.findViewById(InternalRes.viewId("action_bar_title"));
            mSubtitleView = (TextView) mTitleLayout.findViewById(InternalRes.viewId("action_bar_subtitle"));
            if (mTitleStyleRes != 0) mTitleView.setTextAppearance(mTitleStyleRes);
            if (mSubtitleStyleRes != 0) mSubtitleView.setTextAppearance(mSubtitleStyleRes);
        }
        mTitleView.setText(mTitle);
        mSubtitleView.setText(mSubtitle);
        final boolean hasTitle = !TextUtils.isEmpty(mTitle);
        final boolean hasSubtitle = !TextUtils.isEmpty(mSubtitle);
        mSubtitleView.setVisibility(hasSubtitle ? VISIBLE : GONE);
        mTitleLayout.setVisibility(hasTitle || hasSubtitle ? VISIBLE : GONE);
        if (mTitleLayout.getParent() == null) addView(mTitleLayout);
    }

    public void initForMode(final ActionMode mode) {
        if (mClose == null) {
            mClose = LayoutInflater.from(getContext()).inflate(mCloseItemLayout, this, false);
            addView(mClose);
        } else if (mClose.getParent() == null) {
            addView(mClose);
        }
        final View closeButton = mClose.findViewById(InternalRes.viewId("action_mode_close_button"));
        if (closeButton != null) {
            closeButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) { mode.finish(); }
            });
        }
        final MenuBuilder menu = (MenuBuilder) mode.getMenu();
        if (mActionMenuPresenter != null) mActionMenuPresenter.dismissPopupMenus();
        mActionMenuPresenter = new ActionMenuPresenter(getContext());
        mActionMenuPresenter.setReserveOverflow(true);
        final LayoutParams layoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        if (!mSplitActionBar) {
            menu.addMenuPresenter(mActionMenuPresenter, mPopupContext);
            mMenuView = (ActionMenuView) mActionMenuPresenter.getMenuView(this);
            mMenuView.setBackground(null);
            addView(mMenuView, layoutParams);
        } else {
            mActionMenuPresenter.setWidthLimit(getContext().getResources().getDisplayMetrics().widthPixels, true);
            mActionMenuPresenter.setItemLimit(Integer.MAX_VALUE);
            layoutParams.width = LayoutParams.MATCH_PARENT;
            layoutParams.height = mContentHeight;
            menu.addMenuPresenter(mActionMenuPresenter, mPopupContext);
            mMenuView = (ActionMenuView) mActionMenuPresenter.getMenuView(this);
            mMenuView.setBackground(mSplitBackground);
            mSplitView.addView(mMenuView, layoutParams);
        }
    }

    public void closeMode() { killMode(); }

    public void killMode() {
        removeAllViews();
        if (mSplitView != null && mMenuView != null) mSplitView.removeView(mMenuView);
        mCustomView = null;
        mMenuView = null;
    }

    @Override
    public boolean showOverflowMenu() { return mActionMenuPresenter != null && mActionMenuPresenter.showOverflowMenu(); }

    @Override
    public boolean hideOverflowMenu() { return mActionMenuPresenter != null && mActionMenuPresenter.hideOverflowMenu(); }

    @Override
    public boolean isOverflowMenuShowing() {
        return mActionMenuPresenter != null && mActionMenuPresenter.isOverflowMenuShowing();
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof MarginLayoutParams; }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) { return new MarginLayoutParams(p); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        if (widthMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException(getClass().getSimpleName() + " can only be used "
                    + "with android:layout_width=\"match_parent\" (or fill_parent)");
        }
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            throw new IllegalStateException(getClass().getSimpleName() + " can only be used "
                    + "with android:layout_height=\"wrap_content\"");
        }
        final int contentWidth = MeasureSpec.getSize(widthMeasureSpec);
        final int maxHeight = mContentHeight > 0 ? mContentHeight : MeasureSpec.getSize(heightMeasureSpec);
        final int verticalPadding = getPaddingTop() + getPaddingBottom();
        int availableWidth = contentWidth - getPaddingLeft() - getPaddingRight();
        final int height = maxHeight - verticalPadding;
        final int childSpecHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);

        if (mClose != null) {
            availableWidth = measureChildView(mClose, availableWidth, childSpecHeight, 0);
            final MarginLayoutParams lp = (MarginLayoutParams) mClose.getLayoutParams();
            availableWidth -= lp.leftMargin + lp.rightMargin;
        }
        if (mMenuView != null && mMenuView.getParent() == this) {
            availableWidth = measureChildView(mMenuView, availableWidth, childSpecHeight, 0);
        }
        if (mTitleLayout != null && mCustomView == null) {
            if (mTitleOptional) {
                final int titleWidthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                mTitleLayout.measure(titleWidthSpec, childSpecHeight);
                final int titleWidth = mTitleLayout.getMeasuredWidth();
                final boolean titleFits = titleWidth <= availableWidth;
                if (titleFits) availableWidth -= titleWidth;
                mTitleLayout.setVisibility(titleFits ? VISIBLE : GONE);
            } else {
                availableWidth = measureChildView(mTitleLayout, availableWidth, childSpecHeight, 0);
            }
        }
        if (mCustomView != null) {
            final ViewGroup.LayoutParams lp = mCustomView.getLayoutParams();
            final int customWidthMode = lp.width != LayoutParams.WRAP_CONTENT ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST;
            final int customWidth = lp.width >= 0 ? Math.min(lp.width, availableWidth) : availableWidth;
            final int customHeightMode = lp.height != LayoutParams.WRAP_CONTENT ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST;
            final int customHeight = lp.height >= 0 ? Math.min(lp.height, height) : height;
            mCustomView.measure(MeasureSpec.makeMeasureSpec(customWidth, customWidthMode),
                    MeasureSpec.makeMeasureSpec(customHeight, customHeightMode));
        }

        if (mContentHeight <= 0) {
            int measuredHeight = 0;
            final int count = getChildCount();
            for (int i = 0; i < count; i++) {
                final View v = getChildAt(i);
                final int paddedViewHeight = v.getMeasuredHeight() + verticalPadding;
                if (paddedViewHeight > measuredHeight) measuredHeight = paddedViewHeight;
            }
            setMeasuredDimension(contentWidth, measuredHeight);
        } else {
            setMeasuredDimension(contentWidth, maxHeight);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final boolean isLayoutRtl = isLayoutRtl();
        int x = isLayoutRtl ? r - l - getPaddingRight() : getPaddingLeft();
        final int y = getPaddingTop();
        final int contentHeight = b - t - getPaddingTop() - getPaddingBottom();

        if (mClose != null && mClose.getVisibility() != GONE) {
            final MarginLayoutParams lp = (MarginLayoutParams) mClose.getLayoutParams();
            final int startMargin = isLayoutRtl ? lp.rightMargin : lp.leftMargin;
            final int endMargin = isLayoutRtl ? lp.leftMargin : lp.rightMargin;
            x = next(x, startMargin, isLayoutRtl);
            x += positionChild(mClose, x, y, contentHeight, isLayoutRtl);
            x = next(x, endMargin, isLayoutRtl);
        }
        if (mTitleLayout != null && mCustomView == null && mTitleLayout.getVisibility() != GONE) {
            x += positionChild(mTitleLayout, x, y, contentHeight, isLayoutRtl);
        }
        if (mCustomView != null) x += positionChild(mCustomView, x, y, contentHeight, isLayoutRtl);
        x = isLayoutRtl ? getPaddingLeft() : r - l - getPaddingRight();
        if (mMenuView != null) x += positionChild(mMenuView, x, y, contentHeight, !isLayoutRtl);
    }

    @Override
    public boolean shouldDelayChildPressedState() { return false; }

    public void setTitleOptional(boolean titleOptional) {
        if (titleOptional != mTitleOptional) requestLayout();
        mTitleOptional = titleOptional;
    }

    public boolean isTitleOptional() { return mTitleOptional; }
}
