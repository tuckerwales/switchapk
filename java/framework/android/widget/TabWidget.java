package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;

import com.android.internal.util.InternalRes;

/**
 * Displays a list of tab labels representing each page in the parent's tab
 * collection. Ported from AOSP.
 */
public class TabWidget extends LinearLayout implements OnFocusChangeListener {
    /** The TabWidget styleable, shared with TabHost. */
    static final int[] TAB_WIDGET_ATTRS = {
            android.R.attr.divider, android.R.attr.tabStripEnabled, android.R.attr.tabStripLeft,
            android.R.attr.tabStripRight, InternalRes.attr("tabLayout") };
    private static final int STYLE_TAB_STRIP_ENABLED = 1, STYLE_TAB_STRIP_LEFT = 2, STYLE_TAB_STRIP_RIGHT = 3;
    /** Index of tabLayout in {@link #TAB_WIDGET_ATTRS}, read by TabHost. */
    static final int STYLE_TAB_LAYOUT = 4;

    private final Rect mBounds = new Rect();

    private OnTabSelectionChanged mSelectionChangedListener;

    // This value will be set to 0 as soon as the first tab is added to TabHost.
    private int mSelectedTab = -1;

    private Drawable mLeftStrip;
    private Drawable mRightStrip;

    private boolean mDrawBottomStrips = true;
    private boolean mStripMoved;

    // When positive, the widths and heights of tabs will be imposed so that
    // they fit in parent.
    private int mImposedTabsHeight = -1;
    private int[] mImposedTabWidths;

    public TabWidget(Context context) {
        this(context, null);
    }

    public TabWidget(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.tabWidgetStyle);
    }

    public TabWidget(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TabWidget(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, TAB_WIDGET_ATTRS, defStyleAttr, defStyleRes);

        mDrawBottomStrips = a.getBoolean(STYLE_TAB_STRIP_ENABLED, mDrawBottomStrips);

        // Tests the target SDK version, as set in the Manifest. Could not be
        // set using styles.xml in a values-v? directory which targets the
        // current platform SDK version instead.
        final boolean isTargetSdkDonutOrLower =
                context.getApplicationInfo().targetSdkVersion <= Build.VERSION_CODES.DONUT;

        final boolean hasExplicitLeft = a.hasValueOrEmpty(STYLE_TAB_STRIP_LEFT);
        if (hasExplicitLeft) {
            mLeftStrip = a.getDrawable(STYLE_TAB_STRIP_LEFT);
        } else if (isTargetSdkDonutOrLower) {
            mLeftStrip = context.getDrawable(InternalRes.drawable("tab_bottom_left_v4"));
        } else {
            mLeftStrip = context.getDrawable(InternalRes.drawable("tab_bottom_left"));
        }

        final boolean hasExplicitRight = a.hasValueOrEmpty(STYLE_TAB_STRIP_RIGHT);
        if (hasExplicitRight) {
            mRightStrip = a.getDrawable(STYLE_TAB_STRIP_RIGHT);
        } else if (isTargetSdkDonutOrLower) {
            mRightStrip = context.getDrawable(InternalRes.drawable("tab_bottom_right_v4"));
        } else {
            mRightStrip = context.getDrawable(InternalRes.drawable("tab_bottom_right"));
        }

        a.recycle();

        setChildrenDrawingOrderEnabled(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mStripMoved = true;

        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        if (mSelectedTab == -1) {
            return i;
        } else {
            // Always draw the selected tab last, so that drop shadows are drawn
            // in the correct z-order.
            if (i == childCount - 1) {
                return mSelectedTab;
            } else if (i >= mSelectedTab) {
                return i + 1;
            } else {
                return i;
            }
        }
    }

    @Override
    void measureChildBeforeLayout(View child, int childIndex, int widthMeasureSpec, int totalWidth,
            int heightMeasureSpec, int totalHeight) {
        if (!isMeasureWithLargestChildEnabled() && mImposedTabsHeight >= 0) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(
                    totalWidth + mImposedTabWidths[childIndex], MeasureSpec.EXACTLY);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(mImposedTabsHeight,
                    MeasureSpec.EXACTLY);
        }

        super.measureChildBeforeLayout(child, childIndex,
                widthMeasureSpec, totalWidth, heightMeasureSpec, totalHeight);
    }

    @Override
    void measureHorizontal(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            super.measureHorizontal(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // First, measure with no constraint
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int unspecifiedWidth = MeasureSpec.makeSafeMeasureSpec(width,
                MeasureSpec.UNSPECIFIED);
        mImposedTabsHeight = -1;
        super.measureHorizontal(unspecifiedWidth, heightMeasureSpec);

        int extraWidth = getMeasuredWidth() - width;
        if (extraWidth > 0) {
            final int count = getChildCount();

            int childCount = 0;
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                childCount++;
            }

            if (childCount > 0) {
                if (mImposedTabWidths == null || mImposedTabWidths.length != count) {
                    mImposedTabWidths = new int[count];
                }
                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) continue;
                    final int childWidth = child.getMeasuredWidth();
                    final int delta = extraWidth / childCount;
                    final int newWidth = Math.max(0, childWidth - delta);
                    mImposedTabWidths[i] = newWidth;
                    // Make sure the extra width is evenly distributed, no int division remainder
                    extraWidth -= childWidth - newWidth; // delta may have been clamped
                    childCount--;
                    mImposedTabsHeight = Math.max(mImposedTabsHeight, child.getMeasuredHeight());
                }
            }
        }

        // Measure again, this time with imposed tab widths and respecting
        // initial spec request.
        super.measureHorizontal(widthMeasureSpec, heightMeasureSpec);
    }

    /** Returns the tab indicator view at the given index. */
    public View getChildTabViewAt(int index) {
        return getChildAt(index);
    }

    /** Returns the number of tab indicator views. */
    public int getTabCount() {
        return getChildCount();
    }

    /** Sets the drawable to use as a divider between the tab indicators. */
    @Override
    public void setDividerDrawable(Drawable drawable) {
        super.setDividerDrawable(drawable);
    }

    /** Sets the drawable to use as a divider between the tab indicators. */
    public void setDividerDrawable(int resId) {
        setDividerDrawable(getContext().getDrawable(resId));
    }

    /** Sets the drawable to use as the left part of the strip below the tab indicators. */
    public void setLeftStripDrawable(Drawable drawable) {
        mLeftStrip = drawable;
        requestLayout();
        invalidate();
    }

    /** Sets the drawable to use as the left part of the strip below the tab indicators. */
    public void setLeftStripDrawable(int resId) {
        setLeftStripDrawable(getContext().getDrawable(resId));
    }

    /** Returns the drawable used to draw the left part of the strip at the bottom of the tab widget. */
    public Drawable getLeftStripDrawable() {
        return mLeftStrip;
    }

    /** Sets the drawable to use as the right part of the strip below the tab indicators. */
    public void setRightStripDrawable(Drawable drawable) {
        mRightStrip = drawable;
        requestLayout();
        invalidate();
    }

    /** Sets the drawable to use as the right part of the strip below the tab indicators. */
    public void setRightStripDrawable(int resId) {
        setRightStripDrawable(getContext().getDrawable(resId));
    }

    /** Returns the drawable used to draw the right part of the strip at the bottom of the tab widget. */
    public Drawable getRightStripDrawable() {
        return mRightStrip;
    }

    /** Controls whether the bottom strips on the tab indicators are drawn or not. */
    public void setStripEnabled(boolean stripEnabled) {
        mDrawBottomStrips = stripEnabled;
        invalidate();
    }

    /** Indicates whether the bottom strips on the tab indicators are drawn or not. */
    public boolean isStripEnabled() {
        return mDrawBottomStrips;
    }

    @Override
    public void childDrawableStateChanged(View child) {
        if (getTabCount() > 0 && child == getChildTabViewAt(mSelectedTab)) {
            // To make sure that the bottom strip is redrawn
            invalidate();
        }
        super.childDrawableStateChanged(child);
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        // Do nothing if there are no tabs.
        if (getTabCount() == 0) return;

        // If the user specified a custom view for the tab indicators, then
        // do not draw the bottom strips.
        if (!mDrawBottomStrips) {
            // Skip drawing the bottom strips.
            return;
        }

        final View selectedChild = getChildTabViewAt(mSelectedTab);

        final Drawable leftStrip = mLeftStrip;
        final Drawable rightStrip = mRightStrip;

        if (leftStrip != null) {
            leftStrip.setState(selectedChild.getDrawableState());
        }
        if (rightStrip != null) {
            rightStrip.setState(selectedChild.getDrawableState());
        }

        if (mStripMoved) {
            final Rect bounds = mBounds;
            bounds.left = selectedChild.getLeft();
            bounds.right = selectedChild.getRight();
            final int myHeight = getHeight();
            if (leftStrip != null) {
                leftStrip.setBounds(Math.min(0, bounds.left - leftStrip.getIntrinsicWidth()),
                        myHeight - leftStrip.getIntrinsicHeight(), bounds.left, myHeight);
            }
            if (rightStrip != null) {
                rightStrip.setBounds(bounds.right, myHeight - rightStrip.getIntrinsicHeight(),
                        Math.max(getWidth(), bounds.right + rightStrip.getIntrinsicWidth()),
                        myHeight);
            }
            mStripMoved = false;
        }

        if (leftStrip != null) {
            leftStrip.draw(canvas);
        }
        if (rightStrip != null) {
            rightStrip.draw(canvas);
        }
    }

    /**
     * Puts focus on the specified tab indicator without changing focus; see
     * {@link #focusCurrentTab(int)} to also move focus.
     */
    public void setCurrentTab(int index) {
        if (index < 0 || index >= getTabCount() || index == mSelectedTab) {
            return;
        }

        if (mSelectedTab != -1) {
            getChildTabViewAt(mSelectedTab).setSelected(false);
        }
        mSelectedTab = index;
        getChildTabViewAt(mSelectedTab).setSelected(true);
        mStripMoved = true;
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return TabWidget.class.getName();
    }

    /**
     * Sets the current tab and focuses the UI on it.
     */
    public void focusCurrentTab(int index) {
        final int oldTab = mSelectedTab;

        // set the tab
        setCurrentTab(index);

        // change the focus if applicable.
        if (oldTab != index) {
            getChildTabViewAt(index).requestFocus();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        final int count = getTabCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildTabViewAt(i);
            child.setEnabled(enabled);
        }
    }

    @Override
    public void addView(View child) {
        if (child.getLayoutParams() == null) {
            final LinearLayout.LayoutParams lp = new LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            lp.setMargins(0, 0, 0, 0);
            child.setLayoutParams(lp);
        }

        // Ensure you can navigate to the tab with the keyboard, and you can touch it
        child.setFocusable(true);
        child.setClickable(true);

        if (child.getPointerIcon() == null) {
            child.setPointerIcon(PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_HAND));
        }

        super.addView(child);

        // TODO: detect this via geometry with a tabwidget listener rather
        // than potentially interfere with the view's listener
        child.setOnClickListener(new TabClickListener(getTabCount() - 1));
    }

    @Override
    public void removeAllViews() {
        super.removeAllViews();
        mSelectedTab = -1;
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
        if (!isEnabled()) {
            return null;
        }
        return super.onResolvePointerIcon(event, pointerIndex);
    }

    /**
     * Provides a way for {@link TabHost} to be notified that the user clicked
     * on a tab indicator.
     */
    void setTabSelectionListener(OnTabSelectionChanged listener) {
        mSelectionChangedListener = listener;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        // No-op. Tab selection is separate from keyboard focus.
    }

    // registered with each tab indicator so we can notify tab host
    private class TabClickListener implements OnClickListener {
        private final int mTabIndex;

        private TabClickListener(int tabIndex) {
            mTabIndex = tabIndex;
        }

        public void onClick(View v) {
            mSelectionChangedListener.onTabSelectionChanged(mTabIndex, true);
        }
    }

    /**
     * Lets {@link TabHost} know that the user clicked on a tab indicator.
     */
    interface OnTabSelectionChanged {
        /**
         * Informs the TabHost which tab was selected. It also indicates
         * if the tab was clicked/pressed or just focused into.
         *
         * @param tabIndex index of the tab that was selected
         * @param clicked whether the selection changed due to a touch/click or
         *                due to focus entering the tab through navigation.
         *                {@code true} if it was due to a press/click and
         *                {@code false} otherwise.
         */
        void onTabSelectionChanged(int tabIndex, boolean clicked);
    }
}
