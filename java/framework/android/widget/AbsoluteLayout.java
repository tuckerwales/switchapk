package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Port of AOSP AbsoluteLayout: children at fixed x/y positions. */
@Deprecated
public class AbsoluteLayout extends ViewGroup {
    public AbsoluteLayout(Context context) { this(context, null); }

    public AbsoluteLayout(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public AbsoluteLayout(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public AbsoluteLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int count = getChildCount();
        int maxHeight = 0;
        int maxWidth = 0;
        measureChildren(widthMeasureSpec, heightMeasureSpec);
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                final AbsoluteLayout.LayoutParams lp = (AbsoluteLayout.LayoutParams) child.getLayoutParams();
                final int childRight = lp.x + child.getMeasuredWidth();
                final int childBottom = lp.y + child.getMeasuredHeight();
                maxWidth = Math.max(maxWidth, childRight);
                maxHeight = Math.max(maxHeight, childBottom);
            }
        }
        maxWidth += getPaddingLeft() + getPaddingRight();
        maxHeight += getPaddingTop() + getPaddingBottom();
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());
        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, 0),
                resolveSizeAndState(maxHeight, heightMeasureSpec, 0));
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0, 0);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                final AbsoluteLayout.LayoutParams lp = (AbsoluteLayout.LayoutParams) child.getLayoutParams();
                final int childLeft = getPaddingLeft() + lp.x;
                final int childTop = getPaddingTop() + lp.y;
                child.layout(childLeft, childTop, childLeft + child.getMeasuredWidth(),
                        childTop + child.getMeasuredHeight());
            }
        }
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new AbsoluteLayout.LayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof AbsoluteLayout.LayoutParams; }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) { return new LayoutParams(p); }

    @Override
    public boolean shouldDelayChildPressedState() { return false; }

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public int x;
        public int y;

        public LayoutParams(int width, int height, int x, int y) {
            super(width, height);
            this.x = x;
            this.y = y;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            final TypedArray a = c.obtainStyledAttributes(attrs,
                    new int[] {android.R.attr.layout_x, android.R.attr.layout_y});
            x = a.getDimensionPixelOffset(0, 0);
            y = a.getDimensionPixelOffset(1, 0);
            a.recycle();
        }

        public LayoutParams(ViewGroup.LayoutParams source) { super(source); }

        public String debug(String output) {
            return output + "Absolute.LayoutParams={width=" + sizeToString(width) + ", height="
                    + sizeToString(height) + " x=" + x + " y=" + y + "}";
        }
    }
}
