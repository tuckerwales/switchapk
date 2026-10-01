package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Positions children relative to each other or to the parent (port of AOSP
 * RelativeLayout, including the dependency graph and the API 18+ measure
 * behaviour).
 */
public class RelativeLayout extends ViewGroup {
    public static final int TRUE = -1;
    public static final int LEFT_OF = 0;
    public static final int RIGHT_OF = 1;
    public static final int ABOVE = 2;
    public static final int BELOW = 3;
    public static final int ALIGN_BASELINE = 4;
    public static final int ALIGN_LEFT = 5;
    public static final int ALIGN_TOP = 6;
    public static final int ALIGN_RIGHT = 7;
    public static final int ALIGN_BOTTOM = 8;
    public static final int ALIGN_PARENT_LEFT = 9;
    public static final int ALIGN_PARENT_TOP = 10;
    public static final int ALIGN_PARENT_RIGHT = 11;
    public static final int ALIGN_PARENT_BOTTOM = 12;
    public static final int CENTER_IN_PARENT = 13;
    public static final int CENTER_HORIZONTAL = 14;
    public static final int CENTER_VERTICAL = 15;
    public static final int START_OF = 16;
    public static final int END_OF = 17;
    public static final int ALIGN_START = 18;
    public static final int ALIGN_END = 19;
    public static final int ALIGN_PARENT_START = 20;
    public static final int ALIGN_PARENT_END = 21;

    private static final int VERB_COUNT = 22;

    private static final int[] RULES_VERTICAL = {ABOVE, BELOW, ALIGN_BASELINE, ALIGN_TOP, ALIGN_BOTTOM};
    private static final int[] RULES_HORIZONTAL = {LEFT_OF, RIGHT_OF, ALIGN_LEFT, ALIGN_RIGHT, START_OF, END_OF,
        ALIGN_START, ALIGN_END};

    private static final int VALUE_NOT_SET = Integer.MIN_VALUE;

    private View mBaselineView = null;
    private int mGravity = Gravity.START | Gravity.TOP;
    private final Rect mContentBounds = new Rect();
    private final Rect mSelfBounds = new Rect();
    private int mIgnoreGravity;
    private SortedSet<View> mTopToBottomLeftToRightSet = null;
    private boolean mDirtyHierarchy;
    private View[] mSortedHorizontalChildren;
    private View[] mSortedVerticalChildren;
    private final DependencyGraph mGraph = new DependencyGraph();
    private boolean mAllowBrokenMeasureSpecs = false;
    private boolean mMeasureVerticalWithPaddingMargin = false;

    private static final int[] ATTRS = {android.R.attr.ignoreGravity, android.R.attr.gravity};

    public RelativeLayout(Context context) { this(context, null); }

    public RelativeLayout(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public RelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public RelativeLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (attrs != null || defStyleAttr != 0 || defStyleRes != 0) {
            final TypedArray a = context.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, defStyleRes);
            mIgnoreGravity = a.getResourceId(0, View.NO_ID);
            mGravity = a.getInt(1, mGravity);
            a.recycle();
        } else {
            mIgnoreGravity = View.NO_ID;
        }
        final int target = context.getApplicationInfo() != null ? context.getApplicationInfo().targetSdkVersion : 30;
        mAllowBrokenMeasureSpecs = target <= 17;
        mMeasureVerticalWithPaddingMargin = target >= 18;
    }

    @Override
    public boolean shouldDelayChildPressedState() { return false; }

    public void setIgnoreGravity(int viewId) { mIgnoreGravity = viewId; }

    public int getIgnoreGravity() { return mIgnoreGravity; }

    public int getGravity() { return mGravity; }

    public void setGravity(int gravity) {
        if (mGravity != gravity) {
            if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) gravity |= Gravity.START;
            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == 0) gravity |= Gravity.TOP;
            mGravity = gravity;
            requestLayout();
        }
    }

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
    public int getBaseline() { return mBaselineView != null ? mBaselineView.getBaseline() : super.getBaseline(); }

    @Override
    public void requestLayout() {
        super.requestLayout();
        mDirtyHierarchy = true;
    }

    private void sortChildren() {
        final int count = getChildCount();
        if (mSortedVerticalChildren == null || mSortedVerticalChildren.length != count) {
            mSortedVerticalChildren = new View[count];
        }
        if (mSortedHorizontalChildren == null || mSortedHorizontalChildren.length != count) {
            mSortedHorizontalChildren = new View[count];
        }
        final DependencyGraph graph = mGraph;
        graph.clear();
        final int layoutDirection = getLayoutDirection();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            // Resolve start/end rules first; the graph reads the resolved left/right rules.
            ((LayoutParams) child.getLayoutParams()).getRules(layoutDirection);
            graph.add(child);
        }
        graph.getSortedViews(mSortedVerticalChildren, RULES_VERTICAL);
        graph.getSortedViews(mSortedHorizontalChildren, RULES_HORIZONTAL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mDirtyHierarchy) {
            mDirtyHierarchy = false;
            sortChildren();
        }
        int myWidth = -1;
        int myHeight = -1;
        int width = 0;
        int height = 0;
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        if (widthMode != MeasureSpec.UNSPECIFIED) myWidth = widthSize;
        if (heightMode != MeasureSpec.UNSPECIFIED) myHeight = heightSize;
        if (widthMode == MeasureSpec.EXACTLY) width = myWidth;
        if (heightMode == MeasureSpec.EXACTLY) height = myHeight;

        View ignore = null;
        int gravity = mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK;
        final boolean horizontalGravity = gravity != Gravity.START && gravity != 0;
        gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        final boolean verticalGravity = gravity != Gravity.TOP && gravity != 0;

        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        boolean offsetHorizontalAxis = false;
        boolean offsetVerticalAxis = false;
        if ((horizontalGravity || verticalGravity) && mIgnoreGravity != View.NO_ID) ignore = findViewById(mIgnoreGravity);

        final boolean isWrapContentWidth = widthMode != MeasureSpec.EXACTLY;
        final boolean isWrapContentHeight = heightMode != MeasureSpec.EXACTLY;
        final int layoutDirection = getLayoutDirection();
        if (isLayoutRtl() && myWidth == -1) myWidth = DEFAULT_WIDTH;

        View[] views = mSortedHorizontalChildren;
        int count = views.length;
        for (int i = 0; i < count; i++) {
            View child = views[i];
            if (child.getVisibility() != GONE) {
                LayoutParams params = (LayoutParams) child.getLayoutParams();
                int[] rules = params.getRules(layoutDirection);
                applyHorizontalSizeRules(params, myWidth, rules);
                measureChildHorizontal(child, params, myWidth, myHeight);
                if (positionChildHorizontal(child, params, myWidth, isWrapContentWidth)) offsetHorizontalAxis = true;
            }
        }

        views = mSortedVerticalChildren;
        count = views.length;
        for (int i = 0; i < count; i++) {
            final View child = views[i];
            if (child.getVisibility() != GONE) {
                final LayoutParams params = (LayoutParams) child.getLayoutParams();
                applyVerticalSizeRules(params, myHeight, child.getBaseline());
                measureChild(child, params, myWidth, myHeight);
                if (positionChildVertical(child, params, myHeight, isWrapContentHeight)) offsetVerticalAxis = true;
                if (isWrapContentWidth) {
                    if (isLayoutRtl()) {
                        width = Math.max(width, myWidth - params.mLeft + params.leftMargin);
                    } else {
                        width = Math.max(width, params.mRight + params.rightMargin);
                    }
                }
                if (isWrapContentHeight) height = Math.max(height, params.mBottom + params.bottomMargin);
                if (child != ignore || verticalGravity) {
                    left = Math.min(left, params.mLeft - params.leftMargin);
                    top = Math.min(top, params.mTop - params.topMargin);
                }
                if (child != ignore || horizontalGravity) {
                    right = Math.max(right, params.mRight + params.rightMargin);
                    bottom = Math.max(bottom, params.mBottom + params.bottomMargin);
                }
            }
        }

        View baselineView = null;
        LayoutParams baselineParams = null;
        for (int i = 0; i < count; i++) {
            final View child = views[i];
            if (child.getVisibility() != GONE) {
                final LayoutParams childParams = (LayoutParams) child.getLayoutParams();
                if (baselineView == null || baselineParams == null || compareLayoutPosition(childParams, baselineParams) < 0) {
                    baselineView = child;
                    baselineParams = childParams;
                }
            }
        }
        mBaselineView = baselineView;

        if (isWrapContentWidth) {
            width += getPaddingRight();
            if (getLayoutParams() != null && getLayoutParams().width >= 0) {
                width = Math.max(width, getLayoutParams().width);
            }
            width = Math.max(width, getSuggestedMinimumWidth());
            width = resolveSize(width, widthMeasureSpec);
            if (offsetHorizontalAxis) {
                for (int i = 0; i < count; i++) {
                    final View child = views[i];
                    if (child.getVisibility() != GONE) {
                        final LayoutParams params = (LayoutParams) child.getLayoutParams();
                        final int[] rules = params.getRules(layoutDirection);
                        if (rules[CENTER_IN_PARENT] != 0 || rules[CENTER_HORIZONTAL] != 0) {
                            centerHorizontal(child, params, width);
                        } else if (rules[ALIGN_PARENT_RIGHT] != 0) {
                            final int childWidth = child.getMeasuredWidth();
                            params.mLeft = width - getPaddingRight() - childWidth;
                            params.mRight = params.mLeft + childWidth;
                        }
                    }
                }
            }
        }

        if (isWrapContentHeight) {
            height += getPaddingBottom();
            if (getLayoutParams() != null && getLayoutParams().height >= 0) {
                height = Math.max(height, getLayoutParams().height);
            }
            height = Math.max(height, getSuggestedMinimumHeight());
            height = resolveSize(height, heightMeasureSpec);
            if (offsetVerticalAxis) {
                for (int i = 0; i < count; i++) {
                    final View child = views[i];
                    if (child.getVisibility() != GONE) {
                        final LayoutParams params = (LayoutParams) child.getLayoutParams();
                        final int[] rules = params.getRules(layoutDirection);
                        if (rules[CENTER_IN_PARENT] != 0 || rules[CENTER_VERTICAL] != 0) {
                            centerVertical(child, params, height);
                        } else if (rules[ALIGN_PARENT_BOTTOM] != 0) {
                            final int childHeight = child.getMeasuredHeight();
                            params.mTop = height - getPaddingBottom() - childHeight;
                            params.mBottom = params.mTop + childHeight;
                        }
                    }
                }
            }
        }

        if (horizontalGravity || verticalGravity) {
            final Rect selfBounds = mSelfBounds;
            selfBounds.set(getPaddingLeft(), getPaddingTop(), width - getPaddingRight(), height - getPaddingBottom());
            final Rect contentBounds = mContentBounds;
            Gravity.apply(mGravity, right - left, bottom - top, selfBounds, contentBounds, layoutDirection);
            final int horizontalOffset = contentBounds.left - left;
            final int verticalOffset = contentBounds.top - top;
            if (horizontalOffset != 0 || verticalOffset != 0) {
                for (int i = 0; i < count; i++) {
                    final View child = views[i];
                    if (child.getVisibility() != GONE && child != ignore) {
                        final LayoutParams params = (LayoutParams) child.getLayoutParams();
                        if (horizontalGravity) {
                            params.mLeft += horizontalOffset;
                            params.mRight += horizontalOffset;
                        }
                        if (verticalGravity) {
                            params.mTop += verticalOffset;
                            params.mBottom += verticalOffset;
                        }
                    }
                }
            }
        }

        if (isLayoutRtl()) {
            final int offsetWidth = myWidth - width;
            for (int i = 0; i < count; i++) {
                final View child = views[i];
                if (child.getVisibility() != GONE) {
                    final LayoutParams params = (LayoutParams) child.getLayoutParams();
                    params.mLeft -= offsetWidth;
                    params.mRight -= offsetWidth;
                }
            }
        }
        setMeasuredDimension(width, height);
    }

    private static final int DEFAULT_WIDTH = 0x00010000;

    private int compareLayoutPosition(LayoutParams p1, LayoutParams p2) {
        final int topDiff = p1.mTop - p2.mTop;
        if (topDiff != 0) return topDiff;
        return p1.mLeft - p2.mLeft;
    }

    private void measureChild(View child, LayoutParams params, int myWidth, int myHeight) {
        int childWidthMeasureSpec = getChildMeasureSpec(params.mLeft, params.mRight, params.width, params.leftMargin,
                params.rightMargin, getPaddingLeft(), getPaddingRight(), myWidth);
        int childHeightMeasureSpec = getChildMeasureSpec(params.mTop, params.mBottom, params.height,
                params.topMargin, params.bottomMargin, getPaddingTop(), getPaddingBottom(), myHeight);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    private void measureChildHorizontal(View child, LayoutParams params, int myWidth, int myHeight) {
        final int childWidthMeasureSpec = getChildMeasureSpec(params.mLeft, params.mRight, params.width,
                params.leftMargin, params.rightMargin, getPaddingLeft(), getPaddingRight(), myWidth);
        final int childHeightMeasureSpec;
        if (myHeight < 0 && !mAllowBrokenMeasureSpecs) {
            if (params.height >= 0) {
                childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(params.height, MeasureSpec.EXACTLY);
            } else {
                childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            }
        } else {
            final int maxHeight;
            if (mMeasureVerticalWithPaddingMargin) {
                maxHeight = Math.max(0, myHeight - getPaddingTop() - getPaddingBottom() - params.topMargin
                        - params.bottomMargin);
            } else {
                maxHeight = Math.max(0, myHeight);
            }
            final int heightMode;
            if (params.height == LayoutParams.MATCH_PARENT) heightMode = MeasureSpec.EXACTLY;
            else heightMode = MeasureSpec.AT_MOST;
            childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, heightMode);
        }
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    private int getChildMeasureSpec(int childStart, int childEnd, int childSize, int startMargin, int endMargin,
            int startPadding, int endPadding, int mySize) {
        int childSpecMode = 0;
        int childSpecSize = 0;
        final boolean isUnspecified = mySize < 0;
        if (isUnspecified && !mAllowBrokenMeasureSpecs) {
            if (childStart != VALUE_NOT_SET && childEnd != VALUE_NOT_SET) {
                childSpecSize = Math.max(0, childEnd - childStart);
                childSpecMode = MeasureSpec.EXACTLY;
            } else if (childSize >= 0) {
                childSpecSize = childSize;
                childSpecMode = MeasureSpec.EXACTLY;
            } else {
                childSpecSize = 0;
                childSpecMode = MeasureSpec.UNSPECIFIED;
            }
            return MeasureSpec.makeMeasureSpec(childSpecSize, childSpecMode);
        }
        int tempStart = childStart;
        int tempEnd = childEnd;
        if (tempStart == VALUE_NOT_SET) tempStart = startPadding + startMargin;
        if (tempEnd == VALUE_NOT_SET) tempEnd = mySize - endPadding - endMargin;
        final int maxAvailable = tempEnd - tempStart;
        if (childStart != VALUE_NOT_SET && childEnd != VALUE_NOT_SET) {
            childSpecMode = isUnspecified ? MeasureSpec.UNSPECIFIED : MeasureSpec.EXACTLY;
            childSpecSize = Math.max(0, maxAvailable);
        } else {
            if (childSize >= 0) {
                childSpecMode = MeasureSpec.EXACTLY;
                if (maxAvailable >= 0) childSpecSize = Math.min(maxAvailable, childSize);
                else childSpecSize = childSize;
            } else if (childSize == LayoutParams.MATCH_PARENT) {
                childSpecMode = isUnspecified ? MeasureSpec.UNSPECIFIED : MeasureSpec.EXACTLY;
                childSpecSize = Math.max(0, maxAvailable);
            } else if (childSize == LayoutParams.WRAP_CONTENT) {
                if (maxAvailable >= 0) {
                    childSpecMode = MeasureSpec.AT_MOST;
                    childSpecSize = maxAvailable;
                } else {
                    childSpecMode = MeasureSpec.UNSPECIFIED;
                    childSpecSize = 0;
                }
            }
        }
        return MeasureSpec.makeMeasureSpec(childSpecSize, childSpecMode);
    }

    private boolean positionChildHorizontal(View child, LayoutParams params, int myWidth, boolean wrapContent) {
        final int layoutDirection = getLayoutDirection();
        int[] rules = params.getRules(layoutDirection);
        if (params.mLeft == VALUE_NOT_SET && params.mRight != VALUE_NOT_SET) {
            params.mLeft = params.mRight - child.getMeasuredWidth();
        } else if (params.mLeft != VALUE_NOT_SET && params.mRight == VALUE_NOT_SET) {
            params.mRight = params.mLeft + child.getMeasuredWidth();
        } else if (params.mLeft == VALUE_NOT_SET && params.mRight == VALUE_NOT_SET) {
            if (rules[CENTER_IN_PARENT] != 0 || rules[CENTER_HORIZONTAL] != 0) {
                if (!wrapContent) {
                    centerHorizontal(child, params, myWidth);
                } else {
                    positionAtEdge(child, params, myWidth);
                }
                return true;
            } else {
                positionAtEdge(child, params, myWidth);
            }
        }
        return rules[ALIGN_PARENT_END] != 0;
    }

    private void positionAtEdge(View child, LayoutParams params, int myWidth) {
        if (isLayoutRtl()) {
            params.mRight = myWidth - getPaddingRight() - params.rightMargin;
            params.mLeft = params.mRight - child.getMeasuredWidth();
        } else {
            params.mLeft = getPaddingLeft() + params.leftMargin;
            params.mRight = params.mLeft + child.getMeasuredWidth();
        }
    }

    private boolean positionChildVertical(View child, LayoutParams params, int myHeight, boolean wrapContent) {
        int[] rules = params.getRules();
        if (params.mTop == VALUE_NOT_SET && params.mBottom != VALUE_NOT_SET) {
            params.mTop = params.mBottom - child.getMeasuredHeight();
        } else if (params.mTop != VALUE_NOT_SET && params.mBottom == VALUE_NOT_SET) {
            params.mBottom = params.mTop + child.getMeasuredHeight();
        } else if (params.mTop == VALUE_NOT_SET && params.mBottom == VALUE_NOT_SET) {
            if (rules[CENTER_IN_PARENT] != 0 || rules[CENTER_VERTICAL] != 0) {
                if (!wrapContent) {
                    centerVertical(child, params, myHeight);
                } else {
                    params.mTop = getPaddingTop() + params.topMargin;
                    params.mBottom = params.mTop + child.getMeasuredHeight();
                }
                return true;
            } else {
                params.mTop = getPaddingTop() + params.topMargin;
                params.mBottom = params.mTop + child.getMeasuredHeight();
            }
        }
        return rules[ALIGN_PARENT_BOTTOM] != 0;
    }

    private void applyHorizontalSizeRules(LayoutParams childParams, int myWidth, int[] rules) {
        RelativeLayout.LayoutParams anchorParams;
        childParams.mLeft = VALUE_NOT_SET;
        childParams.mRight = VALUE_NOT_SET;
        anchorParams = getRelatedViewParams(rules, LEFT_OF);
        if (anchorParams != null) {
            childParams.mRight = anchorParams.mLeft - (anchorParams.leftMargin + childParams.rightMargin);
        } else if (childParams.alignWithParent && rules[LEFT_OF] != 0) {
            if (myWidth >= 0) childParams.mRight = myWidth - getPaddingRight() - childParams.rightMargin;
        }
        anchorParams = getRelatedViewParams(rules, RIGHT_OF);
        if (anchorParams != null) {
            childParams.mLeft = anchorParams.mRight + (anchorParams.rightMargin + childParams.leftMargin);
        } else if (childParams.alignWithParent && rules[RIGHT_OF] != 0) {
            childParams.mLeft = getPaddingLeft() + childParams.leftMargin;
        }
        anchorParams = getRelatedViewParams(rules, ALIGN_LEFT);
        if (anchorParams != null) {
            childParams.mLeft = anchorParams.mLeft + childParams.leftMargin;
        } else if (childParams.alignWithParent && rules[ALIGN_LEFT] != 0) {
            childParams.mLeft = getPaddingLeft() + childParams.leftMargin;
        }
        anchorParams = getRelatedViewParams(rules, ALIGN_RIGHT);
        if (anchorParams != null) {
            childParams.mRight = anchorParams.mRight - childParams.rightMargin;
        } else if (childParams.alignWithParent && rules[ALIGN_RIGHT] != 0) {
            if (myWidth >= 0) childParams.mRight = myWidth - getPaddingRight() - childParams.rightMargin;
        }
        if (0 != rules[ALIGN_PARENT_LEFT]) childParams.mLeft = getPaddingLeft() + childParams.leftMargin;
        if (0 != rules[ALIGN_PARENT_RIGHT]) {
            if (myWidth >= 0) childParams.mRight = myWidth - getPaddingRight() - childParams.rightMargin;
        }
    }

    private void applyVerticalSizeRules(LayoutParams childParams, int myHeight, int myBaseline) {
        final int[] rules = childParams.getRules();
        int baselineOffset = getRelatedViewBaselineOffset(rules);
        if (baselineOffset != -1) {
            if (myBaseline != -1) baselineOffset -= myBaseline;
            childParams.mTop = baselineOffset;
            childParams.mBottom = VALUE_NOT_SET;
            return;
        }
        RelativeLayout.LayoutParams anchorParams;
        childParams.mTop = VALUE_NOT_SET;
        childParams.mBottom = VALUE_NOT_SET;
        anchorParams = getRelatedViewParams(rules, ABOVE);
        if (anchorParams != null) {
            childParams.mBottom = anchorParams.mTop - (anchorParams.topMargin + childParams.bottomMargin);
        } else if (childParams.alignWithParent && rules[ABOVE] != 0) {
            if (myHeight >= 0) childParams.mBottom = myHeight - getPaddingBottom() - childParams.bottomMargin;
        }
        anchorParams = getRelatedViewParams(rules, BELOW);
        if (anchorParams != null) {
            childParams.mTop = anchorParams.mBottom + (anchorParams.bottomMargin + childParams.topMargin);
        } else if (childParams.alignWithParent && rules[BELOW] != 0) {
            childParams.mTop = getPaddingTop() + childParams.topMargin;
        }
        anchorParams = getRelatedViewParams(rules, ALIGN_TOP);
        if (anchorParams != null) {
            childParams.mTop = anchorParams.mTop + childParams.topMargin;
        } else if (childParams.alignWithParent && rules[ALIGN_TOP] != 0) {
            childParams.mTop = getPaddingTop() + childParams.topMargin;
        }
        anchorParams = getRelatedViewParams(rules, ALIGN_BOTTOM);
        if (anchorParams != null) {
            childParams.mBottom = anchorParams.mBottom - childParams.bottomMargin;
        } else if (childParams.alignWithParent && rules[ALIGN_BOTTOM] != 0) {
            if (myHeight >= 0) childParams.mBottom = myHeight - getPaddingBottom() - childParams.bottomMargin;
        }
        if (0 != rules[ALIGN_PARENT_TOP]) childParams.mTop = getPaddingTop() + childParams.topMargin;
        if (0 != rules[ALIGN_PARENT_BOTTOM]) {
            if (myHeight >= 0) childParams.mBottom = myHeight - getPaddingBottom() - childParams.bottomMargin;
        }
    }

    private View getRelatedView(int[] rules, int relation) {
        int id = rules[relation];
        if (id != 0) {
            DependencyGraph.Node node = mGraph.mKeyNodes.get(id);
            if (node == null) return null;
            View v = node.view;
            while (v.getVisibility() == View.GONE) {
                rules = ((LayoutParams) v.getLayoutParams()).getRules(v.getLayoutDirection());
                node = mGraph.mKeyNodes.get((rules[relation]));
                if (node == null || v == node.view) return null;
                v = node.view;
            }
            return v;
        }
        return null;
    }

    private LayoutParams getRelatedViewParams(int[] rules, int relation) {
        View v = getRelatedView(rules, relation);
        if (v != null) {
            ViewGroup.LayoutParams params = v.getLayoutParams();
            if (params instanceof LayoutParams) return (LayoutParams) v.getLayoutParams();
        }
        return null;
    }

    private int getRelatedViewBaselineOffset(int[] rules) {
        final View v = getRelatedView(rules, ALIGN_BASELINE);
        if (v != null) {
            final int baseline = v.getBaseline();
            if (baseline != -1) {
                final ViewGroup.LayoutParams params = v.getLayoutParams();
                if (params instanceof LayoutParams) {
                    final LayoutParams anchorParams = (LayoutParams) v.getLayoutParams();
                    return anchorParams.mTop + baseline;
                }
            }
        }
        return -1;
    }

    private static void centerHorizontal(View child, LayoutParams params, int myWidth) {
        int childWidth = child.getMeasuredWidth();
        int left = (myWidth - childWidth) / 2;
        params.mLeft = left;
        params.mRight = left + childWidth;
    }

    private static void centerVertical(View child, LayoutParams params, int myHeight) {
        int childHeight = child.getMeasuredHeight();
        int top = (myHeight - childHeight) / 2;
        params.mTop = top;
        params.mBottom = top + childHeight;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                RelativeLayout.LayoutParams st = (RelativeLayout.LayoutParams) child.getLayoutParams();
                child.layout(st.mLeft, st.mTop, st.mRight, st.mBottom);
            }
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) { return new RelativeLayout.LayoutParams(getContext(), attrs); }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof RelativeLayout.LayoutParams; }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        if (lp instanceof LayoutParams) return new LayoutParams((LayoutParams) lp);
        else if (lp instanceof MarginLayoutParams) return new LayoutParams((MarginLayoutParams) lp);
        else return new LayoutParams(lp);
    }

    @Override
    public CharSequence getAccessibilityClassName() { return RelativeLayout.class.getName(); }

    /** Per-child rules (port of AOSP RelativeLayout.LayoutParams). */
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        private int[] mRules = new int[VERB_COUNT];
        private int[] mInitialRules = new int[VERB_COUNT];
        int mLeft;
        int mTop;
        int mRight;
        int mBottom;
        private boolean mRulesChanged = false;
        private boolean mIsRtlCompatibilityMode = false;
        public boolean alignWithParent;

        private static int[] sAttrs;
        /** Rule verb for each entry of {@link #sAttrs}; -1 marks alignWithParentIfMissing. */
        private static final int[] ATTR_VERBS = {
            -1, LEFT_OF, RIGHT_OF, ABOVE, BELOW, ALIGN_BASELINE, ALIGN_LEFT, ALIGN_TOP, ALIGN_RIGHT, ALIGN_BOTTOM,
            ALIGN_PARENT_LEFT, ALIGN_PARENT_TOP, ALIGN_PARENT_RIGHT, ALIGN_PARENT_BOTTOM, CENTER_IN_PARENT,
            CENTER_HORIZONTAL, CENTER_VERTICAL, START_OF, END_OF, ALIGN_START, ALIGN_END, ALIGN_PARENT_START,
            ALIGN_PARENT_END,
        };
        private static final boolean[] ATTR_BOOLEAN = {
            true, false, false, false, false, false, false, false, false, false, true, true, true, true, true, true,
            true, false, false, false, false, true, true,
        };

        private static int[] attrs() {
            if (sAttrs == null) {
                sAttrs = new int[] {
                    android.R.attr.layout_alignWithParentIfMissing, android.R.attr.layout_toLeftOf,
                    android.R.attr.layout_toRightOf, android.R.attr.layout_above, android.R.attr.layout_below,
                    android.R.attr.layout_alignBaseline, android.R.attr.layout_alignLeft,
                    android.R.attr.layout_alignTop, android.R.attr.layout_alignRight,
                    android.R.attr.layout_alignBottom, android.R.attr.layout_alignParentLeft,
                    android.R.attr.layout_alignParentTop, android.R.attr.layout_alignParentRight,
                    android.R.attr.layout_alignParentBottom, android.R.attr.layout_centerInParent,
                    android.R.attr.layout_centerHorizontal, android.R.attr.layout_centerVertical,
                    android.R.attr.layout_toStartOf, android.R.attr.layout_toEndOf, android.R.attr.layout_alignStart,
                    android.R.attr.layout_alignEnd, android.R.attr.layout_alignParentStart,
                    android.R.attr.layout_alignParentEnd,
                };
            }
            return sAttrs;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            final int target = c.getApplicationInfo() != null ? c.getApplicationInfo().targetSdkVersion : 30;
            mIsRtlCompatibilityMode = target < 17;
            final int[] rules = mRules;
            final int[] initialRules = mInitialRules;
            TypedArray a = c.obtainStyledAttributes(attrs, attrs());
            for (int i = 0; i < ATTR_VERBS.length; i++) {
                if (!a.hasValue(i)) continue;
                final int verb = ATTR_VERBS[i];
                if (verb < 0) {
                    alignWithParent = a.getBoolean(i, false);
                } else if (ATTR_BOOLEAN[i]) {
                    rules[verb] = a.getBoolean(i, false) ? TRUE : 0;
                } else {
                    rules[verb] = a.getResourceId(i, 0);
                }
            }
            mRulesChanged = true;
            System.arraycopy(rules, LEFT_OF, initialRules, LEFT_OF, VERB_COUNT);
            a.recycle();
        }

        public LayoutParams(int w, int h) { super(w, h); }

        public LayoutParams(ViewGroup.LayoutParams source) { super(source); }

        public LayoutParams(ViewGroup.MarginLayoutParams source) { super(source); }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.mIsRtlCompatibilityMode = source.mIsRtlCompatibilityMode;
            this.mRulesChanged = source.mRulesChanged;
            this.alignWithParent = source.alignWithParent;
            System.arraycopy(source.mRules, LEFT_OF, this.mRules, LEFT_OF, VERB_COUNT);
            System.arraycopy(source.mInitialRules, LEFT_OF, this.mInitialRules, LEFT_OF, VERB_COUNT);
        }

        public String debug(String output) { return output + "ViewGroup.LayoutParams={ width=" + width + ", height=" + height + " }"; }

        public void addRule(int verb) { addRule(verb, TRUE); }

        public void addRule(int verb, int subject) {
            if (!mNeedsLayoutResolution && isRelativeRule(verb) && mInitialRules[verb] != 0 && subject == 0) {
                mNeedsLayoutResolution = true;
            }
            mRules[verb] = subject;
            mInitialRules[verb] = subject;
            mRulesChanged = true;
        }

        private boolean mNeedsLayoutResolution;

        public void removeRule(int verb) { addRule(verb, 0); }

        public int getRule(int verb) { return mRules[verb]; }

        private boolean hasRelativeRules() {
            return (mInitialRules[START_OF] != 0 || mInitialRules[END_OF] != 0 || mInitialRules[ALIGN_START] != 0
                    || mInitialRules[ALIGN_END] != 0 || mInitialRules[ALIGN_PARENT_START] != 0
                    || mInitialRules[ALIGN_PARENT_END] != 0);
        }

        private boolean isRelativeRule(int rule) {
            return rule == START_OF || rule == END_OF || rule == ALIGN_START || rule == ALIGN_END
                    || rule == ALIGN_PARENT_START || rule == ALIGN_PARENT_END;
        }

        private void resolveRules(int layoutDirection) {
            final boolean isLayoutRtl = (layoutDirection == View.LAYOUT_DIRECTION_RTL);
            System.arraycopy(mInitialRules, LEFT_OF, mRules, LEFT_OF, VERB_COUNT);
            if (mIsRtlCompatibilityMode) {
                if (mRules[ALIGN_START] != 0) {
                    if (mRules[ALIGN_LEFT] == 0) mRules[ALIGN_LEFT] = mRules[ALIGN_START];
                    mRules[ALIGN_START] = 0;
                }
                if (mRules[ALIGN_END] != 0) {
                    if (mRules[ALIGN_RIGHT] == 0) mRules[ALIGN_RIGHT] = mRules[ALIGN_END];
                    mRules[ALIGN_END] = 0;
                }
                if (mRules[START_OF] != 0) {
                    if (mRules[LEFT_OF] == 0) mRules[LEFT_OF] = mRules[START_OF];
                    mRules[START_OF] = 0;
                }
                if (mRules[END_OF] != 0) {
                    if (mRules[RIGHT_OF] == 0) mRules[RIGHT_OF] = mRules[END_OF];
                    mRules[END_OF] = 0;
                }
                if (mRules[ALIGN_PARENT_START] != 0) {
                    if (mRules[ALIGN_PARENT_LEFT] == 0) mRules[ALIGN_PARENT_LEFT] = mRules[ALIGN_PARENT_START];
                    mRules[ALIGN_PARENT_START] = 0;
                }
                if (mRules[ALIGN_PARENT_END] != 0) {
                    if (mRules[ALIGN_PARENT_RIGHT] == 0) mRules[ALIGN_PARENT_RIGHT] = mRules[ALIGN_PARENT_END];
                    mRules[ALIGN_PARENT_END] = 0;
                }
            } else {
                if ((mRules[ALIGN_START] != 0 || mRules[ALIGN_END] != 0)
                        && (mRules[ALIGN_LEFT] != 0 || mRules[ALIGN_RIGHT] != 0)) {
                    mRules[ALIGN_LEFT] = 0;
                    mRules[ALIGN_RIGHT] = 0;
                }
                if (mRules[ALIGN_START] != 0) {
                    mRules[isLayoutRtl ? ALIGN_RIGHT : ALIGN_LEFT] = mRules[ALIGN_START];
                    mRules[ALIGN_START] = 0;
                }
                if (mRules[ALIGN_END] != 0) {
                    mRules[isLayoutRtl ? ALIGN_LEFT : ALIGN_RIGHT] = mRules[ALIGN_END];
                    mRules[ALIGN_END] = 0;
                }
                if ((mRules[START_OF] != 0 || mRules[END_OF] != 0) && (mRules[LEFT_OF] != 0 || mRules[RIGHT_OF] != 0)) {
                    mRules[LEFT_OF] = 0;
                    mRules[RIGHT_OF] = 0;
                }
                if (mRules[START_OF] != 0) {
                    mRules[isLayoutRtl ? RIGHT_OF : LEFT_OF] = mRules[START_OF];
                    mRules[START_OF] = 0;
                }
                if (mRules[END_OF] != 0) {
                    mRules[isLayoutRtl ? LEFT_OF : RIGHT_OF] = mRules[END_OF];
                    mRules[END_OF] = 0;
                }
                if ((mRules[ALIGN_PARENT_START] != 0 || mRules[ALIGN_PARENT_END] != 0)
                        && (mRules[ALIGN_PARENT_LEFT] != 0 || mRules[ALIGN_PARENT_RIGHT] != 0)) {
                    mRules[ALIGN_PARENT_LEFT] = 0;
                    mRules[ALIGN_PARENT_RIGHT] = 0;
                }
                if (mRules[ALIGN_PARENT_START] != 0) {
                    mRules[isLayoutRtl ? ALIGN_PARENT_RIGHT : ALIGN_PARENT_LEFT] = mRules[ALIGN_PARENT_START];
                    mRules[ALIGN_PARENT_START] = 0;
                }
                if (mRules[ALIGN_PARENT_END] != 0) {
                    mRules[isLayoutRtl ? ALIGN_PARENT_LEFT : ALIGN_PARENT_RIGHT] = mRules[ALIGN_PARENT_END];
                    mRules[ALIGN_PARENT_END] = 0;
                }
            }
            mRulesChanged = false;
            mNeedsLayoutResolution = false;
        }

        /** Hidden AOSP API: rules resolved for the layout direction. */
        public int[] getRules(int layoutDirection) {
            resolveLayoutDirection(layoutDirection);
            return mRules;
        }

        public int[] getRules() { return mRules; }

        @Override
        public void resolveLayoutDirection(int layoutDirection) {
            if (shouldResolveLayoutDirection(layoutDirection)) resolveRules(layoutDirection);
            super.resolveLayoutDirection(layoutDirection);
        }

        private int mResolvedDirection = -1;

        private boolean shouldResolveLayoutDirection(int layoutDirection) {
            boolean should = (mNeedsLayoutResolution || hasRelativeRules() || mRulesChanged)
                    && (mRulesChanged || mNeedsLayoutResolution || layoutDirection != mResolvedDirection);
            mResolvedDirection = layoutDirection;
            return should;
        }
    }

    private static class DependencyGraph {
        private final ArrayList<Node> mNodes = new ArrayList<Node>();
        private final SparseArray<Node> mKeyNodes = new SparseArray<Node>();
        private final ArrayDeque<Node> mRoots = new ArrayDeque<Node>();

        void clear() {
            mNodes.clear();
            mKeyNodes.clear();
            mRoots.clear();
        }

        void add(View view) {
            final int id = view.getId();
            final Node node = new Node(view);
            if (id != View.NO_ID) mKeyNodes.put(id, node);
            mNodes.add(node);
        }

        void getSortedViews(View[] sorted, int... rules) {
            final ArrayDeque<Node> roots = findRoots(rules);
            int index = 0;
            Node node;
            while ((node = roots.pollLast()) != null) {
                final View view = node.view;
                final int key = view.getId();
                sorted[index++] = view;
                final ArrayMap<Node, DependencyGraph> dependents = node.dependents;
                final int count = dependents.size();
                for (int i = 0; i < count; i++) {
                    final Node dependent = dependents.keyAt(i);
                    final SparseArray<Node> dependencies = dependent.dependencies;
                    dependencies.remove(key);
                    if (dependencies.size() == 0) roots.add(dependent);
                }
            }
            if (index < sorted.length) {
                throw new IllegalStateException("Circular dependencies cannot exist in RelativeLayout");
            }
        }

        private ArrayDeque<Node> findRoots(int[] rulesFilter) {
            final SparseArray<Node> keyNodes = mKeyNodes;
            final ArrayList<Node> nodes = mNodes;
            final int count = nodes.size();
            for (int i = 0; i < count; i++) {
                final Node node = nodes.get(i);
                node.dependents.clear();
                node.dependencies.clear();
            }
            for (int i = 0; i < count; i++) {
                final Node node = nodes.get(i);
                final LayoutParams layoutParams = (LayoutParams) node.view.getLayoutParams();
                final int[] rules = layoutParams.mRules;
                final int rulesCount = rulesFilter.length;
                for (int j = 0; j < rulesCount; j++) {
                    final int rule = rules[rulesFilter[j]];
                    if (rule > 0 || (rule < -1)) {
                        final Node dependency = keyNodes.get(rule);
                        if (dependency == null || dependency == node) continue;
                        dependency.dependents.put(node, this);
                        node.dependencies.put(rule, dependency);
                    }
                }
            }
            final ArrayDeque<Node> roots = mRoots;
            roots.clear();
            for (int i = 0; i < count; i++) {
                final Node node = nodes.get(i);
                if (node.dependencies.size() == 0) roots.addLast(node);
            }
            return roots;
        }

        static class Node {
            View view;
            final ArrayMap<Node, DependencyGraph> dependents = new ArrayMap<Node, DependencyGraph>();
            final SparseArray<Node> dependencies = new SparseArray<Node>();

            Node(View view) { this.view = view; }
        }
    }
}
