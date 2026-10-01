package com.android.internal.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import com.android.internal.util.InternalRes;

/** Button bar that stacks its buttons vertically when they do not fit (port of AOSP ButtonBarLayout). */
public class ButtonBarLayout extends LinearLayout {
    private static final int PEEK_BUTTON_DP = 16;
    private boolean mAllowStacking;
    private int mLastWidthSize = -1;
    private int mMinimumHeight = 0;

    public ButtonBarLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        int attr = InternalRes.attr("allowStacking");
        if (attr != 0) {
            final TypedArray ta = context.obtainStyledAttributes(attrs, new int[] {attr});
            mAllowStacking = ta.getBoolean(0, true);
            ta.recycle();
        } else {
            mAllowStacking = true;
        }
    }

    public void setAllowStacking(boolean allowStacking) {
        if (mAllowStacking != allowStacking) {
            mAllowStacking = allowStacking;
            if (!mAllowStacking && getOrientation() == LinearLayout.VERTICAL) setStacked(false);
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        if (mAllowStacking) {
            if (widthSize > mLastWidthSize && isStacked()) setStacked(false);
            mLastWidthSize = widthSize;
        }
        boolean needsRemeasure = false;
        final int initialWidthMeasureSpec;
        if (!isStacked() && MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            initialWidthMeasureSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.AT_MOST);
            needsRemeasure = true;
        } else {
            initialWidthMeasureSpec = widthMeasureSpec;
        }
        super.onMeasure(initialWidthMeasureSpec, heightMeasureSpec);
        if (mAllowStacking && !isStacked()) {
            final int measuredWidth = getMeasuredWidthAndState();
            final int measuredWidthState = measuredWidth & MEASURED_STATE_MASK;
            if (measuredWidthState == MEASURED_STATE_TOO_SMALL) {
                setStacked(true);
                needsRemeasure = true;
            }
        }
        if (needsRemeasure) super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int minHeight = 0;
        final int firstVisible = getNextVisibleChildIndex(0);
        if (firstVisible >= 0) {
            final View firstButton = getChildAt(firstVisible);
            final LayoutParams firstParams = (LayoutParams) firstButton.getLayoutParams();
            minHeight += getPaddingTop() + firstButton.getMeasuredHeight() + firstParams.topMargin
                    + firstParams.bottomMargin;
            if (isStacked()) {
                final int secondVisible = getNextVisibleChildIndex(firstVisible + 1);
                if (secondVisible >= 0) {
                    minHeight += getChildAt(secondVisible).getPaddingTop()
                            + (int) (PEEK_BUTTON_DP * getResources().getDisplayMetrics().density);
                }
            } else {
                minHeight += getPaddingBottom();
            }
        }
        if (getMinimumHeight() != minHeight) setMinimumHeight(minHeight);
    }

    private int getNextVisibleChildIndex(int index) {
        for (int i = index, count = getChildCount(); i < count; i++) {
            if (getChildAt(i).getVisibility() == View.VISIBLE) return i;
        }
        return -1;
    }

    @Override
    public int getMinimumHeight() { return Math.max(mMinimumHeight, super.getMinimumHeight()); }

    private void setStacked(boolean stacked) {
        setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        setGravity(stacked ? Gravity.END : Gravity.BOTTOM);
        final View spacer = findViewById(InternalRes.viewId("spacer"));
        if (spacer != null) spacer.setVisibility(stacked ? View.GONE : View.INVISIBLE);
        final int childCount = getChildCount();
        for (int i = childCount - 2; i >= 0; i--) bringChildToFront(getChildAt(i));
    }

    private boolean isStacked() { return getOrientation() == LinearLayout.VERTICAL; }
}
