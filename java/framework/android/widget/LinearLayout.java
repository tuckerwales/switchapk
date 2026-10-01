package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

/**
 * Arranges children in a single row or column (port of AOSP LinearLayout, API 29
 * behaviour): weights and weightSum, baseline alignment, gravity, dividers and
 * measureWithLargestChild.
 */
public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;
    public static final int SHOW_DIVIDER_NONE = 0;
    public static final int SHOW_DIVIDER_BEGINNING = 1;
    public static final int SHOW_DIVIDER_MIDDLE = 2;
    public static final int SHOW_DIVIDER_END = 4;

    private static final int VERTICAL_GRAVITY_COUNT = 4;
    private static final int INDEX_CENTER_VERTICAL = 0;
    private static final int INDEX_TOP = 1;
    private static final int INDEX_BOTTOM = 2;
    private static final int INDEX_FILL = 3;

    private boolean mBaselineAligned = true;
    private int mBaselineAlignedChildIndex = -1;
    private int mBaselineChildTop = 0;
    private int mOrientation;
    private int mGravity = Gravity.START | Gravity.TOP;
    private int mTotalLength;
    private float mWeightSum;
    private boolean mUseLargestChild;
    private int[] mMaxAscent;
    private int[] mMaxDescent;
    private Drawable mDivider;
    private int mDividerWidth;
    private int mDividerHeight;
    private int mShowDividers;
    private int mDividerPadding;
    private int mLayoutDirection = View.LAYOUT_DIRECTION_LTR;

    private static final int[] LINEAR_ATTRS = {
        android.R.attr.orientation, android.R.attr.gravity, android.R.attr.baselineAligned,
        android.R.attr.weightSum, android.R.attr.baselineAlignedChildIndex, android.R.attr.measureWithLargestChild,
        android.R.attr.divider, android.R.attr.showDividers, android.R.attr.dividerPadding,
    };

    public LinearLayout(Context context) { this(context, null); }

    public LinearLayout(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public LinearLayout(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public LinearLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (attrs == null && defStyleAttr == 0 && defStyleRes == 0) return;
        final TypedArray a = context.obtainStyledAttributes(attrs, LINEAR_ATTRS, defStyleAttr, defStyleRes);
        int index = a.getInt(0, -1);
        if (index >= 0) setOrientation(index);
        index = a.getInt(1, -1);
        if (index >= 0) setGravity(index);
        boolean baselineAligned = a.getBoolean(2, true);
        if (!baselineAligned) setBaselineAligned(baselineAligned);
        mWeightSum = a.getFloat(3, -1.0f);
        mBaselineAlignedChildIndex = a.getInt(4, -1);
        mUseLargestChild = a.getBoolean(5, false);
        mShowDividers = a.getInt(7, SHOW_DIVIDER_NONE);
        mDividerPadding = a.getDimensionPixelSize(8, 0);
        setDividerDrawable(a.getDrawable(6));
        a.recycle();
    }

    private boolean isShowingDividers() { return (mShowDividers != SHOW_DIVIDER_NONE) && (mDivider != null); }

    public void setShowDividers(int showDividers) {
        if (showDividers == mShowDividers) return;
        mShowDividers = showDividers;
        setWillNotDraw(!isShowingDividers());
        requestLayout();
    }

    @Override
    public boolean shouldDelayChildPressedState() { return false; }

    public int getShowDividers() { return mShowDividers; }

    public Drawable getDividerDrawable() { return mDivider; }

    public void setDividerDrawable(Drawable divider) {
        if (divider == mDivider) return;
        mDivider = divider;
        if (divider != null) {
            mDividerWidth = divider.getIntrinsicWidth();
            mDividerHeight = divider.getIntrinsicHeight();
        } else {
            mDividerWidth = 0;
            mDividerHeight = 0;
        }
        setWillNotDraw(!isShowingDividers());
        requestLayout();
    }

    public void setDividerPadding(int padding) {
        if (padding == mDividerPadding) return;
        mDividerPadding = padding;
        if (isShowingDividers()) {
            requestLayout();
            invalidate();
        }
    }

    public int getDividerPadding() { return mDividerPadding; }

    /** framework-internal (hidden in AOSP). */
    public int getDividerWidth() { return mDividerWidth; }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mDivider == null) return;
        if (mOrientation == VERTICAL) drawDividersVertical(canvas);
        else drawDividersHorizontal(canvas);
    }

    void drawDividersVertical(Canvas canvas) {
        final int count = getVirtualChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getVirtualChildAt(i);
            if (child != null && child.getVisibility() != GONE) {
                if (hasDividerBeforeChildAt(i)) {
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    final int top = child.getTop() - lp.topMargin - mDividerHeight;
                    drawHorizontalDivider(canvas, top);
                }
            }
        }
        if (hasDividerBeforeChildAt(count)) {
            final View child = getLastNonGoneChild();
            int bottom;
            if (child == null) {
                bottom = getHeight() - getPaddingBottom() - mDividerHeight;
            } else {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                bottom = child.getBottom() + lp.bottomMargin;
            }
            drawHorizontalDivider(canvas, bottom);
        }
    }

    private View getLastNonGoneChild() {
        for (int i = getVirtualChildCount() - 1; i >= 0; i--) {
            final View child = getVirtualChildAt(i);
            if (child != null && child.getVisibility() != GONE) return child;
        }
        return null;
    }

    void drawDividersHorizontal(Canvas canvas) {
        final int count = getVirtualChildCount();
        final boolean isLayoutRtl = isLayoutRtl();
        for (int i = 0; i < count; i++) {
            final View child = getVirtualChildAt(i);
            if (child != null && child.getVisibility() != GONE) {
                if (hasDividerBeforeChildAt(i)) {
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    final int position;
                    if (isLayoutRtl) position = child.getRight() + lp.rightMargin;
                    else position = child.getLeft() - lp.leftMargin - mDividerWidth;
                    drawVerticalDivider(canvas, position);
                }
            }
        }
        if (hasDividerBeforeChildAt(count)) {
            final View child = getLastNonGoneChild();
            int position;
            if (child == null) {
                if (isLayoutRtl) position = getPaddingLeft();
                else position = getWidth() - getPaddingRight() - mDividerWidth;
            } else {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (isLayoutRtl) position = child.getLeft() - lp.leftMargin - mDividerWidth;
                else position = child.getRight() + lp.rightMargin;
            }
            drawVerticalDivider(canvas, position);
        }
    }

    void drawHorizontalDivider(Canvas canvas, int top) {
        mDivider.setBounds(getPaddingLeft() + mDividerPadding, top, getWidth() - getPaddingRight() - mDividerPadding,
                top + mDividerHeight);
        mDivider.draw(canvas);
    }

    void drawVerticalDivider(Canvas canvas, int left) {
        mDivider.setBounds(left, getPaddingTop() + mDividerPadding, left + mDividerWidth,
                getHeight() - getPaddingBottom() - mDividerPadding);
        mDivider.draw(canvas);
    }

    public boolean isBaselineAligned() { return mBaselineAligned; }

    public void setBaselineAligned(boolean baselineAligned) { mBaselineAligned = baselineAligned; }

    public boolean isMeasureWithLargestChildEnabled() { return mUseLargestChild; }

    public void setMeasureWithLargestChildEnabled(boolean enabled) { mUseLargestChild = enabled; }

    @Override
    public int getBaseline() {
        if (mBaselineAlignedChildIndex < 0) return super.getBaseline();
        if (getChildCount() <= mBaselineAlignedChildIndex) {
            throw new RuntimeException("mBaselineAlignedChildIndex of LinearLayout set to an index that is out of bounds.");
        }
        final View child = getChildAt(mBaselineAlignedChildIndex);
        final int childBaseline = child.getBaseline();
        if (childBaseline == -1) {
            if (mBaselineAlignedChildIndex == 0) return -1;
            throw new RuntimeException("mBaselineAlignedChildIndex of LinearLayout points to a View that doesn't know "
                    + "how to get its baseline.");
        }
        int childTop = mBaselineChildTop;
        if (mOrientation == VERTICAL) {
            final int majorGravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
            if (majorGravity != Gravity.TOP) {
                switch (majorGravity) {
                    case Gravity.BOTTOM:
                        childTop = getBottom() - getTop() - getPaddingBottom() - mTotalLength;
                        break;
                    case Gravity.CENTER_VERTICAL:
                        childTop += ((getBottom() - getTop() - getPaddingTop() - getPaddingBottom()) - mTotalLength) / 2;
                        break;
                }
            }
        }
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        return childTop + lp.topMargin + childBaseline;
    }

    public int getBaselineAlignedChildIndex() { return mBaselineAlignedChildIndex; }

    public void setBaselineAlignedChildIndex(int i) {
        if ((i < 0) || (i >= getChildCount())) {
            throw new IllegalArgumentException("base aligned child index out of range (0, " + getChildCount() + ")");
        }
        mBaselineAlignedChildIndex = i;
    }

    View getVirtualChildAt(int index) { return getChildAt(index); }

    int getVirtualChildCount() { return getChildCount(); }

    public float getWeightSum() { return mWeightSum; }

    public void setWeightSum(float weightSum) { mWeightSum = Math.max(0.0f, weightSum); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mOrientation == VERTICAL) measureVertical(widthMeasureSpec, heightMeasureSpec);
        else measureHorizontal(widthMeasureSpec, heightMeasureSpec);
    }

    protected boolean hasDividerBeforeChildAt(int childIndex) {
        if (childIndex == getVirtualChildCount()) return (mShowDividers & SHOW_DIVIDER_END) != 0;
        boolean allViewsAreGoneBefore = allViewsAreGoneBefore(childIndex);
        if (allViewsAreGoneBefore) return (mShowDividers & SHOW_DIVIDER_BEGINNING) != 0;
        return (mShowDividers & SHOW_DIVIDER_MIDDLE) != 0;
    }

    private boolean allViewsAreGoneBefore(int childIndex) {
        for (int i = childIndex - 1; i >= 0; i--) {
            final View child = getVirtualChildAt(i);
            if (child != null && child.getVisibility() != GONE) return false;
        }
        return true;
    }

    void measureVertical(int widthMeasureSpec, int heightMeasureSpec) {
        mTotalLength = 0;
        int maxWidth = 0;
        int childState = 0;
        int alternativeMaxWidth = 0;
        int weightedMaxWidth = 0;
        boolean allFillParent = true;
        float totalWeight = 0;
        final int count = getVirtualChildCount();
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        boolean matchWidth = false;
        boolean skippedMeasure = false;
        final int baselineChildIndex = mBaselineAlignedChildIndex;
        final boolean useLargestChild = mUseLargestChild;
        int largestChildHeight = Integer.MIN_VALUE;
        int consumedExcessSpace = 0;
        int nonSkippedChildCount = 0;

        for (int i = 0; i < count; ++i) {
            final View child = getVirtualChildAt(i);
            if (child == null) {
                mTotalLength += measureNullChild(i);
                continue;
            }
            if (child.getVisibility() == View.GONE) {
                i += getChildrenSkipCount(child, i);
                continue;
            }
            nonSkippedChildCount++;
            if (hasDividerBeforeChildAt(i)) mTotalLength += mDividerHeight;
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            totalWeight += lp.weight;
            final boolean useExcessSpace = lp.height == 0 && lp.weight > 0;
            if (heightMode == MeasureSpec.EXACTLY && useExcessSpace) {
                final int totalLength = mTotalLength;
                mTotalLength = Math.max(totalLength, totalLength + lp.topMargin + lp.bottomMargin);
                skippedMeasure = true;
            } else {
                if (useExcessSpace) lp.height = LayoutParams.WRAP_CONTENT;
                final int usedHeight = totalWeight == 0 ? mTotalLength : 0;
                measureChildBeforeLayout(child, i, widthMeasureSpec, 0, heightMeasureSpec, usedHeight);
                final int childHeight = child.getMeasuredHeight();
                if (useExcessSpace) {
                    lp.height = 0;
                    consumedExcessSpace += childHeight;
                }
                final int totalLength = mTotalLength;
                mTotalLength = Math.max(totalLength, totalLength + childHeight + lp.topMargin + lp.bottomMargin
                        + getNextLocationOffset(child));
                if (useLargestChild) largestChildHeight = Math.max(childHeight, largestChildHeight);
            }
            if ((baselineChildIndex >= 0) && (baselineChildIndex == i + 1)) mBaselineChildTop = mTotalLength;
            if (i < baselineChildIndex && lp.weight > 0) {
                throw new RuntimeException("A child of LinearLayout with index less than mBaselineAlignedChildIndex "
                        + "has weight > 0, which won't work.  Either remove the weight, or don't set "
                        + "mBaselineAlignedChildIndex.");
            }
            boolean matchWidthLocally = false;
            if (widthMode != MeasureSpec.EXACTLY && lp.width == LayoutParams.MATCH_PARENT) {
                matchWidth = true;
                matchWidthLocally = true;
            }
            final int margin = lp.leftMargin + lp.rightMargin;
            final int measuredWidth = child.getMeasuredWidth() + margin;
            maxWidth = Math.max(maxWidth, measuredWidth);
            childState = combineMeasuredStates(childState, child.getMeasuredState());
            allFillParent = allFillParent && lp.width == LayoutParams.MATCH_PARENT;
            if (lp.weight > 0) {
                weightedMaxWidth = Math.max(weightedMaxWidth, matchWidthLocally ? margin : measuredWidth);
            } else {
                alternativeMaxWidth = Math.max(alternativeMaxWidth, matchWidthLocally ? margin : measuredWidth);
            }
            i += getChildrenSkipCount(child, i);
        }

        if (nonSkippedChildCount > 0 && hasDividerBeforeChildAt(count)) mTotalLength += mDividerHeight;

        if (useLargestChild && (heightMode == MeasureSpec.AT_MOST || heightMode == MeasureSpec.UNSPECIFIED)) {
            mTotalLength = 0;
            for (int i = 0; i < count; ++i) {
                final View child = getVirtualChildAt(i);
                if (child == null) {
                    mTotalLength += measureNullChild(i);
                    continue;
                }
                if (child.getVisibility() == GONE) {
                    i += getChildrenSkipCount(child, i);
                    continue;
                }
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                final int totalLength = mTotalLength;
                mTotalLength = Math.max(totalLength, totalLength + largestChildHeight + lp.topMargin + lp.bottomMargin
                        + getNextLocationOffset(child));
            }
        }

        mTotalLength += getPaddingTop() + getPaddingBottom();
        int heightSize = mTotalLength;
        heightSize = Math.max(heightSize, getSuggestedMinimumHeight());
        int heightSizeAndState = resolveSizeAndState(heightSize, heightMeasureSpec, 0);
        heightSize = heightSizeAndState & MEASURED_SIZE_MASK;
        int remainingExcess = heightSize - mTotalLength + consumedExcessSpace;
        if (skippedMeasure || (remainingExcess != 0 && totalWeight > 0.0f)) {
            float remainingWeightSum = mWeightSum > 0.0f ? mWeightSum : totalWeight;
            mTotalLength = 0;
            for (int i = 0; i < count; ++i) {
                final View child = getVirtualChildAt(i);
                if (child == null || child.getVisibility() == View.GONE) continue;
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                final float childWeight = lp.weight;
                if (childWeight > 0) {
                    final int share = (int) (childWeight * remainingExcess / remainingWeightSum);
                    remainingExcess -= share;
                    remainingWeightSum -= childWeight;
                    final int childHeight;
                    if (mUseLargestChild && heightMode != MeasureSpec.EXACTLY) {
                        childHeight = largestChildHeight;
                    } else if (lp.height == 0 && heightMode == MeasureSpec.EXACTLY) {
                        childHeight = share;
                    } else {
                        childHeight = child.getMeasuredHeight() + share;
                    }
                    final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.max(0, childHeight),
                            MeasureSpec.EXACTLY);
                    final int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                            getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin, lp.width);
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
                    childState = combineMeasuredStates(childState, child.getMeasuredState()
                            & (MEASURED_STATE_MASK >> MEASURED_HEIGHT_STATE_SHIFT));
                }
                final int margin = lp.leftMargin + lp.rightMargin;
                final int measuredWidth = child.getMeasuredWidth() + margin;
                maxWidth = Math.max(maxWidth, measuredWidth);
                boolean matchWidthLocally = widthMode != MeasureSpec.EXACTLY && lp.width == LayoutParams.MATCH_PARENT;
                alternativeMaxWidth = Math.max(alternativeMaxWidth, matchWidthLocally ? margin : measuredWidth);
                allFillParent = allFillParent && lp.width == LayoutParams.MATCH_PARENT;
                final int totalLength = mTotalLength;
                mTotalLength = Math.max(totalLength, totalLength + child.getMeasuredHeight() + lp.topMargin
                        + lp.bottomMargin + getNextLocationOffset(child));
            }
            mTotalLength += getPaddingTop() + getPaddingBottom();
        } else {
            alternativeMaxWidth = Math.max(alternativeMaxWidth, weightedMaxWidth);
            if (useLargestChild && heightMode != MeasureSpec.EXACTLY) {
                for (int i = 0; i < count; i++) {
                    final View child = getVirtualChildAt(i);
                    if (child == null || child.getVisibility() == View.GONE) continue;
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    float childExtra = lp.weight;
                    if (childExtra > 0) {
                        child.measure(MeasureSpec.makeMeasureSpec(child.getMeasuredWidth(), MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(largestChildHeight, MeasureSpec.EXACTLY));
                    }
                }
            }
        }

        if (!allFillParent && widthMode != MeasureSpec.EXACTLY) maxWidth = alternativeMaxWidth;
        maxWidth += getPaddingLeft() + getPaddingRight();
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());
        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, childState), heightSizeAndState);
        if (matchWidth) forceUniformWidth(count, heightMeasureSpec);
    }

    private void forceUniformWidth(int count, int heightMeasureSpec) {
        int uniformMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
        for (int i = 0; i < count; ++i) {
            final View child = getVirtualChildAt(i);
            if (child != null && child.getVisibility() != GONE) {
                LayoutParams lp = ((LayoutParams) child.getLayoutParams());
                if (lp.width == LayoutParams.MATCH_PARENT) {
                    int oldHeight = lp.height;
                    lp.height = child.getMeasuredHeight();
                    measureChildWithMargins(child, uniformMeasureSpec, 0, heightMeasureSpec, 0);
                    lp.height = oldHeight;
                }
            }
        }
    }

    void measureHorizontal(int widthMeasureSpec, int heightMeasureSpec) {
        mTotalLength = 0;
        int maxHeight = 0;
        int childState = 0;
        int alternativeMaxHeight = 0;
        int weightedMaxHeight = 0;
        boolean allFillParent = true;
        float totalWeight = 0;
        final int count = getVirtualChildCount();
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        boolean matchHeight = false;
        boolean skippedMeasure = false;
        if (mMaxAscent == null || mMaxDescent == null) {
            mMaxAscent = new int[VERTICAL_GRAVITY_COUNT];
            mMaxDescent = new int[VERTICAL_GRAVITY_COUNT];
        }
        final int[] maxAscent = mMaxAscent;
        final int[] maxDescent = mMaxDescent;
        maxAscent[0] = maxAscent[1] = maxAscent[2] = maxAscent[3] = -1;
        maxDescent[0] = maxDescent[1] = maxDescent[2] = maxDescent[3] = -1;
        final boolean baselineAligned = mBaselineAligned;
        final boolean useLargestChild = mUseLargestChild;
        final boolean isExactly = widthMode == MeasureSpec.EXACTLY;
        int largestChildWidth = Integer.MIN_VALUE;
        int usedExcessSpace = 0;
        int nonSkippedChildCount = 0;

        for (int i = 0; i < count; ++i) {
            final View child = getVirtualChildAt(i);
            if (child == null) {
                mTotalLength += measureNullChild(i);
                continue;
            }
            if (child.getVisibility() == GONE) {
                i += getChildrenSkipCount(child, i);
                continue;
            }
            nonSkippedChildCount++;
            if (hasDividerBeforeChildAt(i)) mTotalLength += mDividerWidth;
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            totalWeight += lp.weight;
            final boolean useExcessSpace = lp.width == 0 && lp.weight > 0;
            if (widthMode == MeasureSpec.EXACTLY && useExcessSpace) {
                if (isExactly) {
                    mTotalLength += lp.leftMargin + lp.rightMargin;
                } else {
                    final int totalLength = mTotalLength;
                    mTotalLength = Math.max(totalLength, totalLength + lp.leftMargin + lp.rightMargin);
                }
                if (baselineAligned) {
                    final int freeWidthSpec = MeasureSpec.makeSafeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec),
                            MeasureSpec.UNSPECIFIED);
                    final int freeHeightSpec = MeasureSpec.makeSafeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec),
                            MeasureSpec.UNSPECIFIED);
                    child.measure(freeWidthSpec, freeHeightSpec);
                } else {
                    skippedMeasure = true;
                }
            } else {
                if (useExcessSpace) lp.width = LayoutParams.WRAP_CONTENT;
                final int usedWidth = totalWeight == 0 ? mTotalLength : 0;
                measureChildBeforeLayout(child, i, widthMeasureSpec, usedWidth, heightMeasureSpec, 0);
                final int childWidth = child.getMeasuredWidth();
                if (useExcessSpace) {
                    lp.width = 0;
                    usedExcessSpace += childWidth;
                }
                if (isExactly) {
                    mTotalLength += childWidth + lp.leftMargin + lp.rightMargin + getNextLocationOffset(child);
                } else {
                    final int totalLength = mTotalLength;
                    mTotalLength = Math.max(totalLength, totalLength + childWidth + lp.leftMargin + lp.rightMargin
                            + getNextLocationOffset(child));
                }
                if (useLargestChild) largestChildWidth = Math.max(childWidth, largestChildWidth);
            }
            boolean matchHeightLocally = false;
            if (heightMode != MeasureSpec.EXACTLY && lp.height == LayoutParams.MATCH_PARENT) {
                matchHeight = true;
                matchHeightLocally = true;
            }
            final int margin = lp.topMargin + lp.bottomMargin;
            final int childHeight = child.getMeasuredHeight() + margin;
            childState = combineMeasuredStates(childState, child.getMeasuredState());
            if (baselineAligned) {
                final int childBaseline = child.getBaseline();
                if (childBaseline != -1) {
                    final int gravity = (lp.gravity < 0 ? mGravity : lp.gravity) & Gravity.VERTICAL_GRAVITY_MASK;
                    final int index = ((gravity >> Gravity.AXIS_Y_SHIFT) & ~Gravity.AXIS_SPECIFIED) >> 1;
                    maxAscent[index] = Math.max(maxAscent[index], childBaseline);
                    maxDescent[index] = Math.max(maxDescent[index], childHeight - childBaseline);
                }
            }
            maxHeight = Math.max(maxHeight, childHeight);
            allFillParent = allFillParent && lp.height == LayoutParams.MATCH_PARENT;
            if (lp.weight > 0) {
                weightedMaxHeight = Math.max(weightedMaxHeight, matchHeightLocally ? margin : childHeight);
            } else {
                alternativeMaxHeight = Math.max(alternativeMaxHeight, matchHeightLocally ? margin : childHeight);
            }
            i += getChildrenSkipCount(child, i);
        }

        if (nonSkippedChildCount > 0 && hasDividerBeforeChildAt(count)) mTotalLength += mDividerWidth;

        if (maxAscent[INDEX_TOP] != -1 || maxAscent[INDEX_CENTER_VERTICAL] != -1 || maxAscent[INDEX_BOTTOM] != -1
                || maxAscent[INDEX_FILL] != -1) {
            final int ascent = Math.max(maxAscent[INDEX_FILL], Math.max(maxAscent[INDEX_CENTER_VERTICAL],
                    Math.max(maxAscent[INDEX_TOP], maxAscent[INDEX_BOTTOM])));
            final int descent = Math.max(maxDescent[INDEX_FILL], Math.max(maxDescent[INDEX_CENTER_VERTICAL],
                    Math.max(maxDescent[INDEX_TOP], maxDescent[INDEX_BOTTOM])));
            maxHeight = Math.max(maxHeight, ascent + descent);
        }

        if (useLargestChild && (widthMode == MeasureSpec.AT_MOST || widthMode == MeasureSpec.UNSPECIFIED)) {
            mTotalLength = 0;
            for (int i = 0; i < count; ++i) {
                final View child = getVirtualChildAt(i);
                if (child == null) {
                    mTotalLength += measureNullChild(i);
                    continue;
                }
                if (child.getVisibility() == GONE) {
                    i += getChildrenSkipCount(child, i);
                    continue;
                }
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (isExactly) {
                    mTotalLength += largestChildWidth + lp.leftMargin + lp.rightMargin + getNextLocationOffset(child);
                } else {
                    final int totalLength = mTotalLength;
                    mTotalLength = Math.max(totalLength, totalLength + largestChildWidth + lp.leftMargin
                            + lp.rightMargin + getNextLocationOffset(child));
                }
            }
        }

        mTotalLength += getPaddingLeft() + getPaddingRight();
        int widthSize = mTotalLength;
        widthSize = Math.max(widthSize, getSuggestedMinimumWidth());
        int widthSizeAndState = resolveSizeAndState(widthSize, widthMeasureSpec, 0);
        widthSize = widthSizeAndState & MEASURED_SIZE_MASK;
        int remainingExcess = widthSize - mTotalLength + usedExcessSpace;
        if (skippedMeasure || (remainingExcess != 0 && totalWeight > 0.0f)) {
            float remainingWeightSum = mWeightSum > 0.0f ? mWeightSum : totalWeight;
            maxAscent[0] = maxAscent[1] = maxAscent[2] = maxAscent[3] = -1;
            maxDescent[0] = maxDescent[1] = maxDescent[2] = maxDescent[3] = -1;
            maxHeight = -1;
            mTotalLength = 0;
            for (int i = 0; i < count; ++i) {
                final View child = getVirtualChildAt(i);
                if (child == null || child.getVisibility() == View.GONE) continue;
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                final float childWeight = lp.weight;
                if (childWeight > 0) {
                    final int share = (int) (childWeight * remainingExcess / remainingWeightSum);
                    remainingExcess -= share;
                    remainingWeightSum -= childWeight;
                    final int childWidth;
                    if (mUseLargestChild && widthMode != MeasureSpec.EXACTLY) {
                        childWidth = largestChildWidth;
                    } else if (lp.width == 0 && widthMode == MeasureSpec.EXACTLY) {
                        childWidth = share;
                    } else {
                        childWidth = child.getMeasuredWidth() + share;
                    }
                    final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(Math.max(0, childWidth),
                            MeasureSpec.EXACTLY);
                    final int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                            getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin, lp.height);
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
                    childState = combineMeasuredStates(childState, child.getMeasuredState() & MEASURED_STATE_MASK);
                }
                if (isExactly) {
                    mTotalLength += child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin
                            + getNextLocationOffset(child);
                } else {
                    final int totalLength = mTotalLength;
                    mTotalLength = Math.max(totalLength, totalLength + child.getMeasuredWidth() + lp.leftMargin
                            + lp.rightMargin + getNextLocationOffset(child));
                }
                boolean matchHeightLocally = heightMode != MeasureSpec.EXACTLY && lp.height == LayoutParams.MATCH_PARENT;
                final int margin = lp.topMargin + lp.bottomMargin;
                int childHeight = child.getMeasuredHeight() + margin;
                maxHeight = Math.max(maxHeight, childHeight);
                alternativeMaxHeight = Math.max(alternativeMaxHeight, matchHeightLocally ? margin : childHeight);
                allFillParent = allFillParent && lp.height == LayoutParams.MATCH_PARENT;
                if (baselineAligned) {
                    final int childBaseline = child.getBaseline();
                    if (childBaseline != -1) {
                        final int gravity = (lp.gravity < 0 ? mGravity : lp.gravity) & Gravity.VERTICAL_GRAVITY_MASK;
                        final int index = ((gravity >> Gravity.AXIS_Y_SHIFT) & ~Gravity.AXIS_SPECIFIED) >> 1;
                        maxAscent[index] = Math.max(maxAscent[index], childBaseline);
                        maxDescent[index] = Math.max(maxDescent[index], childHeight - childBaseline);
                    }
                }
            }
            mTotalLength += getPaddingLeft() + getPaddingRight();
            if (maxAscent[INDEX_TOP] != -1 || maxAscent[INDEX_CENTER_VERTICAL] != -1 || maxAscent[INDEX_BOTTOM] != -1
                    || maxAscent[INDEX_FILL] != -1) {
                final int ascent = Math.max(maxAscent[INDEX_FILL], Math.max(maxAscent[INDEX_CENTER_VERTICAL],
                        Math.max(maxAscent[INDEX_TOP], maxAscent[INDEX_BOTTOM])));
                final int descent = Math.max(maxDescent[INDEX_FILL], Math.max(maxDescent[INDEX_CENTER_VERTICAL],
                        Math.max(maxDescent[INDEX_TOP], maxDescent[INDEX_BOTTOM])));
                maxHeight = Math.max(maxHeight, ascent + descent);
            }
        } else {
            alternativeMaxHeight = Math.max(alternativeMaxHeight, weightedMaxHeight);
            if (useLargestChild && widthMode != MeasureSpec.EXACTLY) {
                for (int i = 0; i < count; i++) {
                    final View child = getVirtualChildAt(i);
                    if (child == null || child.getVisibility() == View.GONE) continue;
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    float childExtra = lp.weight;
                    if (childExtra > 0) {
                        child.measure(MeasureSpec.makeMeasureSpec(largestChildWidth, MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(child.getMeasuredHeight(), MeasureSpec.EXACTLY));
                    }
                }
            }
        }

        if (!allFillParent && heightMode != MeasureSpec.EXACTLY) maxHeight = alternativeMaxHeight;
        maxHeight += getPaddingTop() + getPaddingBottom();
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        setMeasuredDimension(widthSizeAndState | (childState & MEASURED_STATE_MASK),
                resolveSizeAndState(maxHeight, heightMeasureSpec, (childState << MEASURED_HEIGHT_STATE_SHIFT)));
        if (matchHeight) forceUniformHeight(count, widthMeasureSpec);
    }

    private void forceUniformHeight(int count, int widthMeasureSpec) {
        int uniformMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY);
        for (int i = 0; i < count; ++i) {
            final View child = getVirtualChildAt(i);
            if (child != null && child.getVisibility() != GONE) {
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (lp.height == LayoutParams.MATCH_PARENT) {
                    int oldWidth = lp.width;
                    lp.width = child.getMeasuredWidth();
                    measureChildWithMargins(child, widthMeasureSpec, 0, uniformMeasureSpec, 0);
                    lp.width = oldWidth;
                }
            }
        }
    }

    int getChildrenSkipCount(View child, int index) { return 0; }

    int measureNullChild(int childIndex) { return 0; }

    void measureChildBeforeLayout(View child, int childIndex, int widthMeasureSpec, int totalWidth,
            int heightMeasureSpec, int totalHeight) {
        measureChildWithMargins(child, widthMeasureSpec, totalWidth, heightMeasureSpec, totalHeight);
    }

    int getLocationOffset(View child) { return 0; }

    int getNextLocationOffset(View child) { return 0; }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mOrientation == VERTICAL) layoutVertical(l, t, r, b);
        else layoutHorizontal(l, t, r, b);
    }

    void layoutVertical(int left, int top, int right, int bottom) {
        final int paddingLeft = getPaddingLeft();
        int childTop;
        int childLeft;
        final int width = right - left;
        int childRight = width - getPaddingRight();
        int childSpace = width - paddingLeft - getPaddingRight();
        final int count = getVirtualChildCount();
        final int majorGravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        final int minorGravity = mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK;
        switch (majorGravity) {
            case Gravity.BOTTOM:
                childTop = getPaddingTop() + bottom - top - mTotalLength;
                break;
            case Gravity.CENTER_VERTICAL:
                childTop = getPaddingTop() + (bottom - top - mTotalLength) / 2;
                break;
            case Gravity.TOP:
            default:
                childTop = getPaddingTop();
                break;
        }
        for (int i = 0; i < count; i++) {
            final View child = getVirtualChildAt(i);
            if (child == null) {
                childTop += measureNullChild(i);
            } else if (child.getVisibility() != GONE) {
                final int childWidth = child.getMeasuredWidth();
                final int childHeight = child.getMeasuredHeight();
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                int gravity = lp.gravity;
                if (gravity < 0) gravity = minorGravity;
                final int layoutDirection = getLayoutDirection();
                final int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);
                switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = paddingLeft + ((childSpace - childWidth) / 2) + lp.leftMargin - lp.rightMargin;
                        break;
                    case Gravity.RIGHT:
                        childLeft = childRight - childWidth - lp.rightMargin;
                        break;
                    case Gravity.LEFT:
                    default:
                        childLeft = paddingLeft + lp.leftMargin;
                        break;
                }
                if (hasDividerBeforeChildAt(i)) childTop += mDividerHeight;
                childTop += lp.topMargin;
                setChildFrame(child, childLeft, childTop + getLocationOffset(child), childWidth, childHeight);
                childTop += childHeight + lp.bottomMargin + getNextLocationOffset(child);
                i += getChildrenSkipCount(child, i);
            }
        }
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        if (layoutDirection != mLayoutDirection) {
            mLayoutDirection = layoutDirection;
            if (mOrientation == HORIZONTAL) requestLayout();
        }
    }

    void layoutHorizontal(int left, int top, int right, int bottom) {
        final boolean isLayoutRtl = isLayoutRtl();
        final int paddingTop = getPaddingTop();
        int childTop;
        int childLeft;
        final int height = bottom - top;
        int childBottom = height - getPaddingBottom();
        int childSpace = height - paddingTop - getPaddingBottom();
        final int count = getVirtualChildCount();
        final int majorGravity = mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK;
        final int minorGravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        final boolean baselineAligned = mBaselineAligned;
        final int[] maxAscent = mMaxAscent;
        final int[] maxDescent = mMaxDescent;
        final int layoutDirection = getLayoutDirection();
        switch (Gravity.getAbsoluteGravity(majorGravity, layoutDirection)) {
            case Gravity.RIGHT:
                childLeft = getPaddingLeft() + right - left - mTotalLength;
                break;
            case Gravity.CENTER_HORIZONTAL:
                childLeft = getPaddingLeft() + (right - left - mTotalLength) / 2;
                break;
            case Gravity.LEFT:
            default:
                childLeft = getPaddingLeft();
                break;
        }
        int start = 0;
        int dir = 1;
        if (isLayoutRtl) {
            start = count - 1;
            dir = -1;
        }
        for (int i = 0; i < count; i++) {
            final int childIndex = start + dir * i;
            final View child = getVirtualChildAt(childIndex);
            if (child == null) {
                childLeft += measureNullChild(childIndex);
            } else if (child.getVisibility() != GONE) {
                final int childWidth = child.getMeasuredWidth();
                final int childHeight = child.getMeasuredHeight();
                int childBaseline = -1;
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (baselineAligned && lp.height != LayoutParams.MATCH_PARENT) childBaseline = child.getBaseline();
                int gravity = lp.gravity;
                if (gravity < 0) gravity = minorGravity;
                switch (gravity & Gravity.VERTICAL_GRAVITY_MASK) {
                    case Gravity.TOP:
                        childTop = paddingTop + lp.topMargin;
                        if (childBaseline != -1 && maxAscent != null) childTop += maxAscent[INDEX_TOP] - childBaseline;
                        break;
                    case Gravity.CENTER_VERTICAL:
                        childTop = paddingTop + ((childSpace - childHeight) / 2) + lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.BOTTOM:
                        childTop = childBottom - childHeight - lp.bottomMargin;
                        if (childBaseline != -1 && maxDescent != null) {
                            int descent = child.getMeasuredHeight() - childBaseline;
                            childTop -= (maxDescent[INDEX_BOTTOM] - descent);
                        }
                        break;
                    default:
                        childTop = paddingTop;
                        break;
                }
                if (isLayoutRtl) {
                    if (hasDividerBeforeChildAt(childIndex + 1)) childLeft += mDividerWidth;
                } else {
                    if (hasDividerBeforeChildAt(childIndex)) childLeft += mDividerWidth;
                }
                childLeft += lp.leftMargin;
                setChildFrame(child, childLeft + getLocationOffset(child), childTop, childWidth, childHeight);
                childLeft += childWidth + lp.rightMargin + getNextLocationOffset(child);
                i += getChildrenSkipCount(child, childIndex);
            }
        }
    }

    private void setChildFrame(View child, int left, int top, int width, int height) {
        child.layout(left, top, left + width, top + height);
    }

    public void setOrientation(int orientation) {
        if (mOrientation != orientation) {
            mOrientation = orientation;
            requestLayout();
        }
    }

    public int getOrientation() { return mOrientation; }

    public void setGravity(int gravity) {
        if (mGravity != gravity) {
            if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) gravity |= Gravity.START;
            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == 0) gravity |= Gravity.TOP;
            mGravity = gravity;
            requestLayout();
        }
    }

    public int getGravity() { return mGravity; }

    public void setHorizontalGravity(int horizontalGravity) {
        final int gravity = horizontalGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK;
        if ((mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) != gravity) {
            mGravity = (mGravity & ~Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) | gravity;
            requestLayout();
        }
    }

    public void setVerticalGravity(int verticalGravity) {
        final int gravity = verticalGravity & Gravity.VERTICAL_GRAVITY_MASK;
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != gravity) {
            mGravity = (mGravity & ~Gravity.VERTICAL_GRAVITY_MASK) | gravity;
            requestLayout();
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) { return new LinearLayout.LayoutParams(getContext(), attrs); }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        if (mOrientation == HORIZONTAL) return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        else if (mOrientation == VERTICAL) return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        return null;
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        if (lp instanceof LayoutParams) return new LayoutParams((LayoutParams) lp);
        if (lp instanceof MarginLayoutParams) return new LayoutParams((MarginLayoutParams) lp);
        return new LayoutParams(lp);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof LinearLayout.LayoutParams; }

    @Override
    public CharSequence getAccessibilityClassName() { return LinearLayout.class.getName(); }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public float weight;
        public int gravity = -1;

        private static final int[] LP_ATTRS = {android.R.attr.layout_gravity, android.R.attr.layout_weight};

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs, LP_ATTRS);
            weight = a.getFloat(1, 0);
            gravity = a.getInt(0, -1);
            a.recycle();
        }

        public LayoutParams(int width, int height) {
            super(width, height);
            weight = 0;
        }

        public LayoutParams(int width, int height, float weight) {
            super(width, height);
            this.weight = weight;
        }

        public LayoutParams(ViewGroup.LayoutParams p) { super(p); }

        public LayoutParams(ViewGroup.MarginLayoutParams source) { super(source); }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.weight = source.weight;
            this.gravity = source.gravity;
        }

        @Override
        public String debug(String output) {
            return output + "LinearLayout.LayoutParams={width=" + sizeToString(width) + ", height="
                    + sizeToString(height) + " weight=" + weight + "}";
        }
    }
}
