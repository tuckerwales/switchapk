package com.android.internal.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.android.internal.util.InternalRes;

/**
 * Root of the material alert dialog layout (port of AOSP AlertDialogLayout).
 * Measures the title and buttons first and gives the rest of the height to
 * the content, so long messages scroll instead of pushing the buttons out.
 */
public class AlertDialogLayout extends LinearLayout {
    public AlertDialogLayout(Context context) { super(context); }

    public AlertDialogLayout(Context context, AttributeSet attrs) { super(context, attrs); }

    public AlertDialogLayout(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    public AlertDialogLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!tryOnMeasure(widthMeasureSpec, heightMeasureSpec)) super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private boolean tryOnMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View topPanel = null;
        View buttonPanel = null;
        View middlePanel = null;
        final int idTop = InternalRes.viewId("topPanel");
        final int idButtons = InternalRes.viewId("buttonPanel");
        final int idContent = InternalRes.viewId("contentPanel");
        final int idCustom = InternalRes.viewId("customPanel");
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            final int id = child.getId();
            if (id == idTop && id != 0) {
                topPanel = child;
            } else if (id == idButtons && id != 0) {
                buttonPanel = child;
            } else if ((id == idContent || id == idCustom) && id != 0) {
                if (middlePanel != null) return false;
                middlePanel = child;
            } else {
                return false;
            }
        }
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int childState = 0;
        int usedHeight = getPaddingTop() + getPaddingBottom();
        if (topPanel != null) {
            topPanel.measure(widthMeasureSpec, MeasureSpec.UNSPECIFIED);
            usedHeight += topPanel.getMeasuredHeight();
            childState = combineMeasuredStates(childState, topPanel.getMeasuredState());
        }
        int buttonHeight = 0;
        int buttonWantsHeight = 0;
        if (buttonPanel != null) {
            buttonPanel.measure(widthMeasureSpec, MeasureSpec.UNSPECIFIED);
            buttonHeight = resolveMinimumHeight(buttonPanel);
            buttonWantsHeight = buttonPanel.getMeasuredHeight() - buttonHeight;
            usedHeight += buttonHeight;
            childState = combineMeasuredStates(childState, buttonPanel.getMeasuredState());
        }
        int middleHeight = 0;
        if (middlePanel != null) {
            final int childHeightSpec;
            if (heightMode == MeasureSpec.UNSPECIFIED) {
                childHeightSpec = MeasureSpec.UNSPECIFIED;
            } else {
                childHeightSpec = MeasureSpec.makeMeasureSpec(Math.max(0, heightSize - usedHeight), heightMode);
            }
            middlePanel.measure(widthMeasureSpec, childHeightSpec);
            middleHeight = middlePanel.getMeasuredHeight();
            usedHeight += middleHeight;
            childState = combineMeasuredStates(childState, middlePanel.getMeasuredState());
        }
        int remainingHeight = heightSize - usedHeight;
        if (buttonPanel != null) {
            usedHeight -= buttonHeight;
            final int heightToGive = Math.min(remainingHeight, buttonWantsHeight);
            if (heightToGive > 0) {
                remainingHeight -= heightToGive;
                buttonHeight += heightToGive;
            }
            final int childHeightSpec = MeasureSpec.makeMeasureSpec(buttonHeight, MeasureSpec.EXACTLY);
            buttonPanel.measure(widthMeasureSpec, childHeightSpec);
            usedHeight += buttonPanel.getMeasuredHeight();
            childState = combineMeasuredStates(childState, buttonPanel.getMeasuredState());
        }
        if (middlePanel != null && remainingHeight > 0) {
            usedHeight -= middleHeight;
            final int heightToGive = remainingHeight;
            remainingHeight -= heightToGive;
            middleHeight += heightToGive;
            final int childHeightSpec = MeasureSpec.makeMeasureSpec(middleHeight, heightMode);
            middlePanel.measure(widthMeasureSpec, childHeightSpec);
            usedHeight += middlePanel.getMeasuredHeight();
            childState = combineMeasuredStates(childState, middlePanel.getMeasuredState());
        }
        int maxWidth = 0;
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) maxWidth = Math.max(maxWidth, child.getMeasuredWidth());
        }
        maxWidth += getPaddingLeft() + getPaddingRight();
        final int widthSizeAndState = resolveSizeAndState(maxWidth, widthMeasureSpec, childState);
        final int heightSizeAndState = resolveSizeAndState(usedHeight, heightMeasureSpec, 0);
        setMeasuredDimension(widthSizeAndState, heightSizeAndState);
        if (widthMode != MeasureSpec.EXACTLY) forceUniformWidth(count, heightMeasureSpec);
        return true;
    }

    private void forceUniformWidth(int count, int heightMeasureSpec) {
        final int uniformMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (lp.width == LayoutParams.MATCH_PARENT) {
                    final int oldHeight = lp.height;
                    lp.height = child.getMeasuredHeight();
                    measureChildWithMargins(child, uniformMeasureSpec, 0, heightMeasureSpec, 0);
                    lp.height = oldHeight;
                }
            }
        }
    }

    private int resolveMinimumHeight(View v) {
        final int minHeight = v.getMinimumHeight();
        if (minHeight > 0) return minHeight;
        if (v instanceof ViewGroup) {
            final ViewGroup vg = (ViewGroup) v;
            if (vg.getChildCount() == 1) return resolveMinimumHeight(vg.getChildAt(0));
        }
        return 0;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int paddingLeft = getPaddingLeft();
        final int width = right - left;
        final int childRight = width - getPaddingRight();
        final int childSpace = width - paddingLeft - getPaddingRight();
        final int totalLength = getMeasuredHeight();
        final int count = getChildCount();
        final int gravity = getGravity();
        final int majorGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;
        final int minorGravity = gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK;
        int childTop;
        switch (majorGravity) {
            case Gravity.BOTTOM: childTop = getPaddingTop() + bottom - top - totalLength; break;
            case Gravity.CENTER_VERTICAL: childTop = getPaddingTop() + (bottom - top - totalLength) / 2; break;
            default: childTop = getPaddingTop(); break;
        }
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child != null && child.getVisibility() != GONE) {
                final int childWidth = child.getMeasuredWidth();
                final int childHeight = child.getMeasuredHeight();
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                int layoutGravity = lp.gravity;
                if (layoutGravity < 0) layoutGravity = minorGravity;
                final int absoluteGravity = Gravity.getAbsoluteGravity(layoutGravity, getLayoutDirection());
                final int childLeft;
                switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = paddingLeft + ((childSpace - childWidth) / 2) + lp.leftMargin - lp.rightMargin;
                        break;
                    case Gravity.RIGHT:
                        childLeft = childRight - childWidth - lp.rightMargin;
                        break;
                    default:
                        childLeft = paddingLeft + lp.leftMargin;
                        break;
                }
                childTop += lp.topMargin;
                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
                childTop += childHeight + lp.bottomMargin;
            }
        }
    }
}
