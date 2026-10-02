package android.widget;

import static android.view.Gravity.AXIS_PULL_AFTER;
import static android.view.Gravity.AXIS_PULL_BEFORE;
import static android.view.Gravity.AXIS_SPECIFIED;
import static android.view.Gravity.AXIS_X_SHIFT;
import static android.view.Gravity.AXIS_Y_SHIFT;
import static android.view.Gravity.HORIZONTAL_GRAVITY_MASK;
import static android.view.Gravity.RELATIVE_LAYOUT_DIRECTION;
import static android.view.Gravity.VERTICAL_GRAVITY_MASK;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.util.LogPrinter;
import android.util.Pair;
import android.util.Printer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import com.android.internal.util.InternalRes;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A layout that places its children in a rectangular grid. Port of the AOSP
 * GridLayout: row and column boundaries are solved as a system of difference
 * constraints (a Bellman-Ford variant), with alignment groups, baselines,
 * default margins and weights.
 */
public class GridLayout extends ViewGroup {

    public static final int HORIZONTAL = LinearLayout.HORIZONTAL;
    public static final int VERTICAL = LinearLayout.VERTICAL;
    public static final int UNDEFINED = Integer.MIN_VALUE;
    public static final int ALIGN_BOUNDS = 0;
    public static final int ALIGN_MARGINS = 1;

    static final int MAX_SIZE = 100000;
    static final int DEFAULT_CONTAINER_MARGIN = 0;
    static final int UNINITIALIZED_HASH = 0;
    static final Printer LOG_PRINTER = new LogPrinter(Log.DEBUG, GridLayout.class.getName());
    static final Printer NO_PRINTER = new Printer() {
        @Override
        public void println(String x) {
        }
    };

    private static final int DEFAULT_ORIENTATION = HORIZONTAL;
    private static final int DEFAULT_COUNT = UNDEFINED;
    private static final boolean DEFAULT_USE_DEFAULT_MARGINS = false;
    private static final boolean DEFAULT_ORDER_PRESERVED = true;
    private static final int DEFAULT_ALIGNMENT_MODE = ALIGN_MARGINS;

    private static final int[] GRID_ATTRS = {
        android.R.attr.orientation, android.R.attr.rowCount, android.R.attr.columnCount,
        android.R.attr.useDefaultMargins, android.R.attr.alignmentMode,
        android.R.attr.rowOrderPreserved, android.R.attr.columnOrderPreserved,
    };
    private static final int ORIENTATION = 0;
    private static final int ROW_COUNT = 1;
    private static final int COLUMN_COUNT = 2;
    private static final int USE_DEFAULT_MARGINS = 3;
    private static final int ALIGNMENT_MODE = 4;
    private static final int ROW_ORDER_PRESERVED = 5;
    private static final int COLUMN_ORDER_PRESERVED = 6;

    final Axis mHorizontalAxis = new Axis(true);
    final Axis mVerticalAxis = new Axis(false);
    int mOrientation = DEFAULT_ORIENTATION;
    boolean mUseDefaultMargins = DEFAULT_USE_DEFAULT_MARGINS;
    int mAlignmentMode = DEFAULT_ALIGNMENT_MODE;
    int mDefaultGap;
    int mLastLayoutParamsHashCode = UNINITIALIZED_HASH;
    Printer mPrinter = LOG_PRINTER;

    public GridLayout(Context context) {
        this(context, null);
    }

    public GridLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GridLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public GridLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final int gapId = InternalRes.dimen("default_gap");
        mDefaultGap = gapId != 0 ? context.getResources().getDimensionPixelOffset(gapId)
                : Math.round(8 * context.getResources().getDisplayMetrics().density);
        final TypedArray a = context.obtainStyledAttributes(attrs, GRID_ATTRS, defStyleAttr, defStyleRes);
        try {
            setRowCount(a.getInt(ROW_COUNT, DEFAULT_COUNT));
            setColumnCount(a.getInt(COLUMN_COUNT, DEFAULT_COUNT));
            setOrientation(a.getInt(ORIENTATION, DEFAULT_ORIENTATION));
            setUseDefaultMargins(a.getBoolean(USE_DEFAULT_MARGINS, DEFAULT_USE_DEFAULT_MARGINS));
            setAlignmentMode(a.getInt(ALIGNMENT_MODE, DEFAULT_ALIGNMENT_MODE));
            setRowOrderPreserved(a.getBoolean(ROW_ORDER_PRESERVED, DEFAULT_ORDER_PRESERVED));
            setColumnOrderPreserved(a.getBoolean(COLUMN_ORDER_PRESERVED, DEFAULT_ORDER_PRESERVED));
        } finally {
            a.recycle();
        }
    }

    // Implementation

    public int getOrientation() {
        return mOrientation;
    }

    public void setOrientation(int orientation) {
        if (this.mOrientation != orientation) {
            this.mOrientation = orientation;
            invalidateStructure();
            requestLayout();
        }
    }

    public int getRowCount() {
        return mVerticalAxis.getCount();
    }

    public void setRowCount(int rowCount) {
        mVerticalAxis.setCount(rowCount);
        invalidateStructure();
        requestLayout();
    }

    public int getColumnCount() {
        return mHorizontalAxis.getCount();
    }

    public void setColumnCount(int columnCount) {
        mHorizontalAxis.setCount(columnCount);
        invalidateStructure();
        requestLayout();
    }

    public boolean getUseDefaultMargins() {
        return mUseDefaultMargins;
    }

    public void setUseDefaultMargins(boolean useDefaultMargins) {
        this.mUseDefaultMargins = useDefaultMargins;
        requestLayout();
    }

    public int getAlignmentMode() {
        return mAlignmentMode;
    }

    public void setAlignmentMode(int alignmentMode) {
        this.mAlignmentMode = alignmentMode;
        requestLayout();
    }

    public boolean isRowOrderPreserved() {
        return mVerticalAxis.isOrderPreserved();
    }

    public void setRowOrderPreserved(boolean rowOrderPreserved) {
        mVerticalAxis.setOrderPreserved(rowOrderPreserved);
        invalidateStructure();
        requestLayout();
    }

    public boolean isColumnOrderPreserved() {
        return mHorizontalAxis.isOrderPreserved();
    }

    public void setColumnOrderPreserved(boolean columnOrderPreserved) {
        mHorizontalAxis.setOrderPreserved(columnOrderPreserved);
        invalidateStructure();
        requestLayout();
    }

    /** framework-internal (hidden in AOSP). */
    public Printer getPrinter() {
        return mPrinter;
    }

    /** framework-internal (hidden in AOSP). */
    public void setPrinter(Printer printer) {
        this.mPrinter = (printer == null) ? NO_PRINTER : printer;
    }

    // Static utility methods

    static int max2(int[] a, int valueIfEmpty) {
        int result = valueIfEmpty;
        for (int i = 0, N = a.length; i < N; i++) {
            result = Math.max(result, a[i]);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    static <T> T[] append(T[] a, T[] b) {
        T[] result = (T[]) Array.newInstance(a.getClass().getComponentType(), a.length + b.length);
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    static Alignment getAlignment(int gravity, boolean horizontal) {
        int mask = horizontal ? HORIZONTAL_GRAVITY_MASK : VERTICAL_GRAVITY_MASK;
        int shift = horizontal ? AXIS_X_SHIFT : AXIS_Y_SHIFT;
        int flags = (gravity & mask) >> shift;
        switch (flags) {
            case (AXIS_SPECIFIED | AXIS_PULL_BEFORE):
                return horizontal ? LEFT : TOP;
            case (AXIS_SPECIFIED | AXIS_PULL_AFTER):
                return horizontal ? RIGHT : BOTTOM;
            case (AXIS_SPECIFIED | AXIS_PULL_BEFORE | AXIS_PULL_AFTER):
                return FILL;
            case AXIS_SPECIFIED:
                return CENTER;
            case (AXIS_SPECIFIED | AXIS_PULL_BEFORE | RELATIVE_LAYOUT_DIRECTION):
                return START;
            case (AXIS_SPECIFIED | AXIS_PULL_AFTER | RELATIVE_LAYOUT_DIRECTION):
                return END;
            default:
                return UNDEFINED_ALIGNMENT;
        }
    }

    /** @noinspection UnusedParameters*/
    private int getDefaultMargin(View c, boolean horizontal, boolean leading) {
        if (c.getClass() == Space.class) {
            return 0;
        }
        return mDefaultGap / 2;
    }

    private int getDefaultMargin(View c, boolean isAtEdge, boolean horizontal, boolean leading) {
        return /*isAtEdge ? DEFAULT_CONTAINER_MARGIN :*/ getDefaultMargin(c, horizontal, leading);
    }

    private int getDefaultMargin(View c, LayoutParams p, boolean horizontal, boolean leading) {
        if (!mUseDefaultMargins) {
            return 0;
        }
        Spec spec = horizontal ? p.columnSpec : p.rowSpec;
        Axis axis = horizontal ? mHorizontalAxis : mVerticalAxis;
        Interval span = spec.span;
        boolean leading1 = (horizontal && isLayoutRtl()) ? !leading : leading;
        boolean isAtEdge = leading1 ? (span.min == 0) : (span.max == axis.getCount());

        return getDefaultMargin(c, isAtEdge, horizontal, leading);
    }

    int getMargin1(View view, boolean horizontal, boolean leading) {
        LayoutParams lp = getLayoutParams(view);
        int margin = horizontal
                ? (leading ? lp.leftMargin : lp.rightMargin)
                : (leading ? lp.topMargin : lp.bottomMargin);
        return margin == UNDEFINED ? getDefaultMargin(view, lp, horizontal, leading) : margin;
    }

    private int getMargin(View view, boolean horizontal, boolean leading) {
        if (mAlignmentMode == ALIGN_MARGINS) {
            return getMargin1(view, horizontal, leading);
        } else {
            Axis axis = horizontal ? mHorizontalAxis : mVerticalAxis;
            int[] margins = leading ? axis.getLeadingMargins() : axis.getTrailingMargins();
            LayoutParams lp = getLayoutParams(view);
            Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
            int index = leading ? spec.span.min : spec.span.max;
            return margins[index];
        }
    }

    private int getTotalMargin(View child, boolean horizontal) {
        return getMargin(child, horizontal, true) + getMargin(child, horizontal, false);
    }

    private static boolean fits(int[] a, int value, int start, int end) {
        if (end > a.length) {
            return false;
        }
        for (int i = start; i < end; i++) {
            if (a[i] > value) {
                return false;
            }
        }
        return true;
    }

    private static void procrusteanFill(int[] a, int start, int end, int value) {
        int length = a.length;
        Arrays.fill(a, Math.min(start, length), Math.min(end, length), value);
    }

    private static void setCellGroup(LayoutParams lp, int row, int rowSpan, int col, int colSpan) {
        lp.setRowSpecSpan(new Interval(row, row + rowSpan));
        lp.setColumnSpecSpan(new Interval(col, col + colSpan));
    }

    // Logic to avert infinite loops by ensuring that the cells can be placed somewhere.
    private static int clip(Interval minorRange, boolean minorWasDefined, int count) {
        int size = minorRange.size();
        if (count == 0) {
            return size;
        }
        int min = minorWasDefined ? min(minorRange.min, count) : 0;
        return min(size, count - min);
    }

    // install default indices for cells that don't define them
    private void validateLayoutParams() {
        final boolean horizontal = (mOrientation == HORIZONTAL);
        final Axis axis = horizontal ? mHorizontalAxis : mVerticalAxis;
        final int count = (axis.definedCount != UNDEFINED) ? axis.definedCount : 0;

        int major = 0;
        int minor = 0;
        int[] maxSizes = new int[count];

        for (int i = 0, N = getChildCount(); i < N; i++) {
            LayoutParams lp = (LayoutParams) getChildAt(i).getLayoutParams();

            final Spec majorSpec = horizontal ? lp.rowSpec : lp.columnSpec;
            final Interval majorRange = majorSpec.span;
            final boolean majorWasDefined = majorSpec.startDefined;
            final int majorSpan = majorRange.size();
            if (majorWasDefined) {
                major = majorRange.min;
            }

            final Spec minorSpec = horizontal ? lp.columnSpec : lp.rowSpec;
            final Interval minorRange = minorSpec.span;
            final boolean minorWasDefined = minorSpec.startDefined;
            final int minorSpan = clip(minorRange, minorWasDefined, count);
            if (minorWasDefined) {
                minor = minorRange.min;
            }

            if (count != 0) {
                // Find suitable row/col values when at least one is undefined.
                if (!majorWasDefined || !minorWasDefined) {
                    while (!fits(maxSizes, major, minor, minor + minorSpan)) {
                        if (minorWasDefined) {
                            major++;
                        } else {
                            if (minor + minorSpan <= count) {
                                minor++;
                            } else {
                                minor = 0;
                                major++;
                            }
                        }
                    }
                }
                procrusteanFill(maxSizes, minor, minor + minorSpan, major + majorSpan);
            }

            if (horizontal) {
                setCellGroup(lp, major, majorSpan, minor, minorSpan);
            } else {
                setCellGroup(lp, minor, minorSpan, major, majorSpan);
            }

            minor = minor + minorSpan;
        }
    }

    private void invalidateStructure() {
        mLastLayoutParamsHashCode = UNINITIALIZED_HASH;
        mHorizontalAxis.invalidateStructure();
        mVerticalAxis.invalidateStructure();
        // This can end up being done twice. Better twice than not at all.
        invalidateValues();
    }

    private void invalidateValues() {
        // Need null check because requestLayout() is called in View's initializer,
        // before we are set up.
        if (mHorizontalAxis != null && mVerticalAxis != null) {
            mHorizontalAxis.invalidateValues();
            mVerticalAxis.invalidateValues();
        }
    }

    /** framework-internal (hidden in AOSP). */
    @Override
    protected void onSetLayoutParams(View child, ViewGroup.LayoutParams layoutParams) {
        super.onSetLayoutParams(child, layoutParams);

        if (!checkLayoutParams(layoutParams)) {
            handleInvalidParams("supplied LayoutParams are of the wrong type");
        }

        invalidateStructure();
    }

    final LayoutParams getLayoutParams(View c) {
        return (LayoutParams) c.getLayoutParams();
    }

    private static void handleInvalidParams(String msg) {
        throw new IllegalArgumentException(msg + ". ");
    }

    private void checkLayoutParams(LayoutParams lp, boolean horizontal) {
        String groupName = horizontal ? "column" : "row";
        Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
        Interval span = spec.span;
        if (span.min != UNDEFINED && span.min < 0) {
            handleInvalidParams(groupName + " indices must be positive");
        }
        Axis axis = horizontal ? mHorizontalAxis : mVerticalAxis;
        int count = axis.definedCount;
        if (count != UNDEFINED) {
            if (span.max > count) {
                handleInvalidParams(groupName
                        + " indices (start + span) mustn't exceed the " + groupName + " count");
            }
            if (span.size() > count) {
                handleInvalidParams(groupName + " span mustn't exceed the " + groupName + " count");
            }
        }
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        if (!(p instanceof LayoutParams)) {
            return false;
        }
        LayoutParams lp = (LayoutParams) p;

        checkLayoutParams(lp, true);
        checkLayoutParams(lp, false);

        return true;
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        // Apps target N or later here, so margins survive the conversion.
        if (lp instanceof LayoutParams) {
            return new LayoutParams((LayoutParams) lp);
        } else if (lp instanceof MarginLayoutParams) {
            return new LayoutParams((MarginLayoutParams) lp);
        }
        return new LayoutParams(lp);
    }

    // Add/remove

    /** framework-internal (hidden in AOSP). */
    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        invalidateStructure();
    }

    /** framework-internal (hidden in AOSP). */
    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        invalidateStructure();
    }

    /**
     * We need to call invalidateStructure() when a child's GONE flag changes state.
     * This implementation is a catch-all, invalidating on any change in the visibility flags.
     */
    @Override
    protected void onChildVisibilityChanged(View child, int oldVisibility, int newVisibility) {
        super.onChildVisibilityChanged(child, oldVisibility, newVisibility);
        if (oldVisibility == GONE || newVisibility == GONE) {
            invalidateStructure();
        }
    }

    private int computeLayoutParamsHashCode() {
        int result = 1;
        for (int i = 0, N = getChildCount(); i < N; i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == View.GONE) continue;
            LayoutParams lp = (LayoutParams) c.getLayoutParams();
            result = 31 * result + lp.hashCode();
        }
        return result;
    }

    private void consistencyCheck() {
        if (mLastLayoutParamsHashCode == UNINITIALIZED_HASH) {
            validateLayoutParams();
            mLastLayoutParamsHashCode = computeLayoutParamsHashCode();
        } else if (mLastLayoutParamsHashCode != computeLayoutParamsHashCode()) {
            mPrinter.println("The fields of some layout parameters were modified in between "
                    + "layout operations. Check the javadoc for GridLayout.LayoutParams#rowSpec.");
            invalidateStructure();
            consistencyCheck();
        }
    }

    // Measurement

    // Note: padding has already been removed from the supplied specs
    private void measureChildWithMargins2(View child, int parentWidthSpec, int parentHeightSpec,
            int childWidth, int childHeight) {
        int childWidthSpec = getChildMeasureSpec(parentWidthSpec,
                getTotalMargin(child, true), childWidth);
        int childHeightSpec = getChildMeasureSpec(parentHeightSpec,
                getTotalMargin(child, false), childHeight);
        child.measure(childWidthSpec, childHeightSpec);
    }

    // Note: padding has already been removed from the supplied specs
    private void measureChildrenWithMargins(int widthSpec, int heightSpec, boolean firstPass) {
        for (int i = 0, N = getChildCount(); i < N; i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == View.GONE) continue;
            LayoutParams lp = getLayoutParams(c);
            if (firstPass) {
                measureChildWithMargins2(c, widthSpec, heightSpec, lp.width, lp.height);
            } else {
                boolean horizontal = (mOrientation == HORIZONTAL);
                Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
                if (spec.getAbsoluteAlignment(horizontal) == FILL) {
                    Interval span = spec.span;
                    Axis axis = horizontal ? mHorizontalAxis : mVerticalAxis;
                    int[] locations = axis.getLocations();
                    int cellSize = locations[span.max] - locations[span.min];
                    int viewSize = cellSize - getTotalMargin(c, horizontal);
                    if (horizontal) {
                        measureChildWithMargins2(c, widthSpec, heightSpec, viewSize, lp.height);
                    } else {
                        measureChildWithMargins2(c, widthSpec, heightSpec, lp.width, viewSize);
                    }
                }
            }
        }
    }

    static int adjust(int measureSpec, int delta) {
        return makeMeasureSpec(
                MeasureSpec.getSize(measureSpec + delta), MeasureSpec.getMode(measureSpec));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        consistencyCheck();

        // If we have been called by View.measure(), one of width or height is likely to
        // have changed. We must invalidate if so.
        invalidateValues();

        int hPadding = getPaddingLeft() + getPaddingRight();
        int vPadding = getPaddingTop() + getPaddingBottom();

        int widthSpecSansPadding = adjust(widthSpec, -hPadding);
        int heightSpecSansPadding = adjust(heightSpec, -vPadding);

        measureChildrenWithMargins(widthSpecSansPadding, heightSpecSansPadding, true);

        int widthSansPadding;
        int heightSansPadding;

        // Use the orientation property to decide which axis should be laid out first.
        if (mOrientation == HORIZONTAL) {
            widthSansPadding = mHorizontalAxis.getMeasure(widthSpecSansPadding);
            measureChildrenWithMargins(widthSpecSansPadding, heightSpecSansPadding, false);
            heightSansPadding = mVerticalAxis.getMeasure(heightSpecSansPadding);
        } else {
            heightSansPadding = mVerticalAxis.getMeasure(heightSpecSansPadding);
            measureChildrenWithMargins(widthSpecSansPadding, heightSpecSansPadding, false);
            widthSansPadding = mHorizontalAxis.getMeasure(widthSpecSansPadding);
        }

        int measuredWidth = Math.max(widthSansPadding + hPadding, getSuggestedMinimumWidth());
        int measuredHeight = Math.max(heightSansPadding + vPadding, getSuggestedMinimumHeight());

        setMeasuredDimension(
                resolveSizeAndState(measuredWidth, widthSpec, 0),
                resolveSizeAndState(measuredHeight, heightSpec, 0));
    }

    private int getMeasurement(View c, boolean horizontal) {
        return horizontal ? c.getMeasuredWidth() : c.getMeasuredHeight();
    }

    final int getMeasurementIncludingMargin(View c, boolean horizontal) {
        if (c.getVisibility() == View.GONE) {
            return 0;
        }
        return getMeasurement(c, horizontal) + getTotalMargin(c, horizontal);
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        invalidateValues();
    }

    // Layout container

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        consistencyCheck();

        int targetWidth = right - left;
        int targetHeight = bottom - top;

        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();

        mHorizontalAxis.layout(targetWidth - paddingLeft - paddingRight);
        mVerticalAxis.layout(targetHeight - paddingTop - paddingBottom);

        int[] hLocations = mHorizontalAxis.getLocations();
        int[] vLocations = mVerticalAxis.getLocations();

        for (int i = 0, N = getChildCount(); i < N; i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == View.GONE) continue;
            LayoutParams lp = getLayoutParams(c);
            Spec columnSpec = lp.columnSpec;
            Spec rowSpec = lp.rowSpec;

            Interval colSpan = columnSpec.span;
            Interval rowSpan = rowSpec.span;

            int x1 = hLocations[colSpan.min];
            int y1 = vLocations[rowSpan.min];

            int x2 = hLocations[colSpan.max];
            int y2 = vLocations[rowSpan.max];

            int cellWidth = x2 - x1;
            int cellHeight = y2 - y1;

            int pWidth = getMeasurement(c, true);
            int pHeight = getMeasurement(c, false);

            Alignment hAlign = columnSpec.getAbsoluteAlignment(true);
            Alignment vAlign = rowSpec.getAbsoluteAlignment(false);

            Bounds boundsX = mHorizontalAxis.getGroupBounds().getValue(i);
            Bounds boundsY = mVerticalAxis.getGroupBounds().getValue(i);

            // Gravity offsets: the location of the alignment group relative to its cell group.
            int gravityOffsetX = hAlign.getGravityOffset(c, cellWidth - boundsX.size(true));
            int gravityOffsetY = vAlign.getGravityOffset(c, cellHeight - boundsY.size(true));

            int leftMargin = getMargin(c, true, true);
            int topMargin = getMargin(c, false, true);
            int rightMargin = getMargin(c, true, false);
            int bottomMargin = getMargin(c, false, false);

            int sumMarginsX = leftMargin + rightMargin;
            int sumMarginsY = topMargin + bottomMargin;

            // Alignment offsets: the location of the view relative to its alignment group.
            int alignmentOffsetX = boundsX.getOffset(this, c, hAlign, pWidth + sumMarginsX, true);
            int alignmentOffsetY = boundsY.getOffset(this, c, vAlign, pHeight + sumMarginsY, false);

            int width = hAlign.getSizeInCell(c, pWidth, cellWidth - sumMarginsX);
            int height = vAlign.getSizeInCell(c, pHeight, cellHeight - sumMarginsY);

            int dx = x1 + gravityOffsetX + alignmentOffsetX;

            int cx = !isLayoutRtl() ? paddingLeft + leftMargin + dx
                    : targetWidth - width - paddingRight - rightMargin - dx;
            int cy = paddingTop + y1 + gravityOffsetY + alignmentOffsetY + topMargin;

            if (width != c.getMeasuredWidth() || height != c.getMeasuredHeight()) {
                c.measure(makeMeasureSpec(width, EXACTLY), makeMeasureSpec(height, EXACTLY));
            }
            c.layout(cx, cy, cx + width, cy + height);
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return GridLayout.class.getName();
    }

    // Inner classes

    /*
     * This internal class houses the algorithm for converting from a list of LayoutParams to
     * a set of row and column boundaries (locations).
     */
    final class Axis {
        private static final int NEW = 0;
        private static final int PENDING = 1;
        private static final int COMPLETE = 2;

        public final boolean horizontal;

        public int definedCount = UNDEFINED;
        private int maxIndex = UNDEFINED;

        PackedMap<Spec, Bounds> groupBounds;
        public boolean groupBoundsValid = false;

        PackedMap<Interval, MutableInt> forwardLinks;
        public boolean forwardLinksValid = false;

        PackedMap<Interval, MutableInt> backwardLinks;
        public boolean backwardLinksValid = false;

        public int[] leadingMargins;
        public boolean leadingMarginsValid = false;

        public int[] trailingMargins;
        public boolean trailingMarginsValid = false;

        public Arc[] arcs;
        public boolean arcsValid = false;

        public int[] locations;
        public boolean locationsValid = false;

        public boolean hasWeights;
        public boolean hasWeightsValid = false;
        public int[] deltas;

        boolean orderPreserved = DEFAULT_ORDER_PRESERVED;

        private MutableInt parentMin = new MutableInt(0);
        private MutableInt parentMax = new MutableInt(-MAX_SIZE);

        private Axis(boolean horizontal) {
            this.horizontal = horizontal;
        }

        private int calculateMaxIndex() {
            // the number Integer.MIN_VALUE + 1 comes up in undefined cells
            int result = -1;
            for (int i = 0, N = getChildCount(); i < N; i++) {
                View c = getChildAt(i);
                LayoutParams params = getLayoutParams(c);
                Spec spec = horizontal ? params.columnSpec : params.rowSpec;
                Interval span = spec.span;
                result = max(result, span.min);
                result = max(result, span.max);
                result = max(result, span.size());
            }
            return result == -1 ? UNDEFINED : result;
        }

        private int getMaxIndex() {
            if (maxIndex == UNDEFINED) {
                maxIndex = max(0, calculateMaxIndex()); // use zero when there are no children
            }
            return maxIndex;
        }

        public int getCount() {
            return max(definedCount, getMaxIndex());
        }

        public void setCount(int count) {
            if (count != UNDEFINED && count < getMaxIndex()) {
                handleInvalidParams((horizontal ? "column" : "row")
                        + "Count must be greater than or equal to the maximum of all grid indices "
                        + "(and spans) defined in the LayoutParams of each child");
            }
            this.definedCount = count;
        }

        public boolean isOrderPreserved() {
            return orderPreserved;
        }

        public void setOrderPreserved(boolean orderPreserved) {
            this.orderPreserved = orderPreserved;
            invalidateStructure();
        }

        private PackedMap<Spec, Bounds> createGroupBounds() {
            Assoc<Spec, Bounds> assoc = Assoc.of(Spec.class, Bounds.class);
            for (int i = 0, N = getChildCount(); i < N; i++) {
                View c = getChildAt(i);
                // we must include views that are GONE here, see introductory javadoc
                LayoutParams lp = getLayoutParams(c);
                Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
                Bounds bounds = spec.getAbsoluteAlignment(horizontal).getBounds();
                assoc.put(spec, bounds);
            }
            return assoc.pack();
        }

        private void computeGroupBounds() {
            Bounds[] values = groupBounds.values;
            for (int i = 0; i < values.length; i++) {
                values[i].reset();
            }
            for (int i = 0, N = getChildCount(); i < N; i++) {
                View c = getChildAt(i);
                // we must include views that are GONE here, see introductory javadoc
                LayoutParams lp = getLayoutParams(c);
                Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
                int size = getMeasurementIncludingMargin(c, horizontal)
                        + ((spec.weight == 0) ? 0 : getDeltas()[i]);
                groupBounds.getValue(i).include(GridLayout.this, c, spec, this, size);
            }
        }

        public PackedMap<Spec, Bounds> getGroupBounds() {
            if (groupBounds == null) {
                groupBounds = createGroupBounds();
            }
            if (!groupBoundsValid) {
                computeGroupBounds();
                groupBoundsValid = true;
            }
            return groupBounds;
        }

        // Add values computed by alignment - taking the max of all alignments in each span
        private PackedMap<Interval, MutableInt> createLinks(boolean min) {
            Assoc<Interval, MutableInt> result = Assoc.of(Interval.class, MutableInt.class);
            Spec[] keys = getGroupBounds().keys;
            for (int i = 0, N = keys.length; i < N; i++) {
                Interval span = min ? keys[i].span : keys[i].span.inverse();
                result.put(span, new MutableInt());
            }
            return result.pack();
        }

        private void computeLinks(PackedMap<Interval, MutableInt> links, boolean min) {
            MutableInt[] spans = links.values;
            for (int i = 0; i < spans.length; i++) {
                spans[i].reset();
            }

            // Use getter to trigger a re-evaluation
            Bounds[] bounds = getGroupBounds().values;
            for (int i = 0; i < bounds.length; i++) {
                int size = bounds[i].size(min);
                MutableInt valueHolder = links.getValue(i);
                // this effectively takes the max() of the minima and the min() of the maxima
                valueHolder.value = max(valueHolder.value, min ? size : -size);
            }
        }

        private PackedMap<Interval, MutableInt> getForwardLinks() {
            if (forwardLinks == null) {
                forwardLinks = createLinks(true);
            }
            if (!forwardLinksValid) {
                computeLinks(forwardLinks, true);
                forwardLinksValid = true;
            }
            return forwardLinks;
        }

        private PackedMap<Interval, MutableInt> getBackwardLinks() {
            if (backwardLinks == null) {
                backwardLinks = createLinks(false);
            }
            if (!backwardLinksValid) {
                computeLinks(backwardLinks, false);
                backwardLinksValid = true;
            }
            return backwardLinks;
        }

        private void include(List<Arc> arcs, Interval key, MutableInt size,
                boolean ignoreIfAlreadyPresent) {
            /*
            Remove self referential links.
            These appear:
                . as parental constraints when GridLayout has no children
                . when components have been marked as GONE
            */
            if (key.size() == 0) {
                return;
            }
            // this bit below should really be computed outside here -
            // its just to stop default (row/col > 0) constraints obliterating valid entries
            if (ignoreIfAlreadyPresent) {
                for (Arc arc : arcs) {
                    Interval span = arc.span;
                    if (span.equals(key)) {
                        return;
                    }
                }
            }
            arcs.add(new Arc(key, size));
        }

        private void include(List<Arc> arcs, Interval key, MutableInt size) {
            include(arcs, key, size, true);
        }

        // Group arcs by their first vertex, returning an array of arrays.
        // This is linear in the number of arcs.
        Arc[][] groupArcsByFirstVertex(Arc[] arcs) {
            int N = getCount() + 1; // the number of vertices
            Arc[][] result = new Arc[N][];
            int[] sizes = new int[N];
            for (Arc arc : arcs) {
                sizes[arc.span.min]++;
            }
            for (int i = 0; i < sizes.length; i++) {
                result[i] = new Arc[sizes[i]];
            }
            // reuse the sizes array to hold the current last elements as we insert each arc
            Arrays.fill(sizes, 0);
            for (Arc arc : arcs) {
                int i = arc.span.min;
                result[i][sizes[i]++] = arc;
            }

            return result;
        }

        private Arc[] topologicalSort(final Arc[] arcs) {
            return new Object() {
                Arc[] result = new Arc[arcs.length];
                int cursor = result.length - 1;
                Arc[][] arcsByVertex = groupArcsByFirstVertex(arcs);
                int[] visited = new int[getCount() + 1];

                void walk(int loc) {
                    switch (visited[loc]) {
                        case NEW: {
                            visited[loc] = PENDING;
                            for (Arc arc : arcsByVertex[loc]) {
                                walk(arc.span.max);
                                result[cursor--] = arc;
                            }
                            visited[loc] = COMPLETE;
                            break;
                        }
                        case PENDING: {
                            // le singe est dans l'arbre
                            assert false;
                            break;
                        }
                        case COMPLETE: {
                            break;
                        }
                    }
                }

                Arc[] sort() {
                    for (int loc = 0, N = arcsByVertex.length; loc < N; loc++) {
                        walk(loc);
                    }
                    assert cursor == -1;
                    return result;
                }
            }.sort();
        }

        private Arc[] topologicalSort(List<Arc> arcs) {
            return topologicalSort(arcs.toArray(new Arc[arcs.size()]));
        }

        private void addComponentSizes(List<Arc> result, PackedMap<Interval, MutableInt> links) {
            for (int i = 0; i < links.keys.length; i++) {
                Interval key = links.keys[i];
                include(result, key, links.values[i], false);
            }
        }

        private Arc[] createArcs() {
            List<Arc> mins = new ArrayList<Arc>();
            List<Arc> maxs = new ArrayList<Arc>();

            // Add the minimum values from the components.
            addComponentSizes(mins, getForwardLinks());
            // Add the maximum values from the components.
            addComponentSizes(maxs, getBackwardLinks());

            // Add ordering constraints to prevent row/col sizes from going negative
            if (orderPreserved) {
                // Add a constraint for every row/col
                for (int i = 0; i < getCount(); i++) {
                    include(mins, new Interval(i, i + 1), new MutableInt(0));
                }
            }

            // Add the container constraints. Use the version of include that allows
            // duplicate entries in case a child spans the entire grid.
            int N = getCount();
            include(mins, new Interval(0, N), parentMin, false);
            include(maxs, new Interval(N, 0), parentMax, false);

            // Sort
            Arc[] sMins = topologicalSort(mins);
            Arc[] sMaxs = topologicalSort(maxs);

            return append(sMins, sMaxs);
        }

        private void computeArcs() {
            // getting the links validates the values that are shared by the arc list
            getForwardLinks();
            getBackwardLinks();
        }

        public Arc[] getArcs() {
            if (arcs == null) {
                arcs = createArcs();
            }
            if (!arcsValid) {
                computeArcs();
                arcsValid = true;
            }
            return arcs;
        }

        private boolean relax(int[] locations, Arc entry) {
            if (!entry.valid) {
                return false;
            }
            Interval span = entry.span;
            int u = span.min;
            int v = span.max;
            int value = entry.value.value;
            int candidate = locations[u] + value;
            if (candidate > locations[v]) {
                locations[v] = candidate;
                return true;
            }
            return false;
        }

        private void init(int[] locations) {
            Arrays.fill(locations, 0);
        }

        private String arcsToString(List<Arc> arcs) {
            String var = horizontal ? "x" : "y";
            StringBuilder result = new StringBuilder();
            boolean first = true;
            for (Arc arc : arcs) {
                if (first) {
                    first = false;
                } else {
                    result = result.append(", ");
                }
                int src = arc.span.min;
                int dst = arc.span.max;
                int value = arc.value.value;
                result.append((src < dst)
                        ? var + dst + "-" + var + src + ">=" + value
                        : var + src + "-" + var + dst + "<=" + -value);

            }
            return result.toString();
        }

        private void logError(String axisName, Arc[] arcs, boolean[] culprits0) {
            List<Arc> culprits = new ArrayList<Arc>();
            List<Arc> removed = new ArrayList<Arc>();
            for (int c = 0; c < arcs.length; c++) {
                Arc arc = arcs[c];
                if (culprits0[c]) {
                    culprits.add(arc);
                }
                if (!arc.valid) {
                    removed.add(arc);
                }
            }
            mPrinter.println(axisName + " constraints: " + arcsToString(culprits)
                    + " are inconsistent; permanently removing: " + arcsToString(removed) + ". ");
        }

        /*
        Bellman-Ford variant, modified to reduce typical running time from O(N^2) to O(N).

        GridLayout converts its requirements into a system of linear constraints of the
        form:

        x[i] - x[j] < a[k]

        Where the x[i] are variables and the a[k] are constants.

        For example, if the variables were instead labeled x, y, z we might have:

            x - y < 17
            y - z < 23
            z - x < 42

        This is a special case of the Linear Programming problem that is, in turn,
        equivalent to the single-source shortest paths problem on a digraph, for
        which the O(n^2) Bellman-Ford algorithm the most commonly used general solution.

        Arcs are sorted topologically (in solve()) so that the first pass over them
        settles most layouts; the outer loop then only removes inconsistent max arcs.
        */
        private boolean solve(Arc[] arcs, int[] locations) {
            return solve(arcs, locations, true);
        }

        private boolean solve(Arc[] arcs, int[] locations, boolean modifyOnError) {
            String axisName = horizontal ? "horizontal" : "vertical";
            int N = getCount() + 1; // The number of vertices is the number of columns/rows + 1.
            boolean[] originalCulprits = null;

            for (int p = 0; p < arcs.length; p++) {
                init(locations);

                // We take one extra pass over traditional Bellman-Ford (and omit their final step)
                for (int i = 0; i < N; i++) {
                    boolean changed = false;
                    for (int j = 0, length = arcs.length; j < length; j++) {
                        changed |= relax(locations, arcs[j]);
                    }
                    if (!changed) {
                        if (originalCulprits != null) {
                            logError(axisName, arcs, originalCulprits);
                        }
                        return true;
                    }
                }

                if (!modifyOnError) {
                    return false; // cannot solve with these constraints
                }

                boolean[] culprits = new boolean[arcs.length];
                for (int i = 0; i < N; i++) {
                    for (int j = 0, length = arcs.length; j < length; j++) {
                        culprits[j] |= relax(locations, arcs[j]);
                    }
                }

                if (p == 0) {
                    originalCulprits = culprits;
                }

                for (int i = 0; i < arcs.length; i++) {
                    if (culprits[i]) {
                        Arc arc = arcs[i];
                        // Only remove max values, min values alone cannot be inconsistent
                        if (arc.span.min < arc.span.max) {
                            continue;
                        }
                        arc.valid = false;
                        break;
                    }
                }
            }
            return true;
        }

        private void computeMargins(boolean leading) {
            int[] margins = leading ? leadingMargins : trailingMargins;
            for (int i = 0, N = getChildCount(); i < N; i++) {
                View c = getChildAt(i);
                if (c.getVisibility() == View.GONE) continue;
                LayoutParams lp = getLayoutParams(c);
                Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
                Interval span = spec.span;
                int index = leading ? span.min : span.max;
                margins[index] = max(margins[index], getMargin1(c, horizontal, leading));
            }
        }

        // External entry points

        public int[] getLeadingMargins() {
            if (leadingMargins == null) {
                leadingMargins = new int[getCount() + 1];
            }
            if (!leadingMarginsValid) {
                computeMargins(true);
                leadingMarginsValid = true;
            }
            return leadingMargins;
        }

        public int[] getTrailingMargins() {
            if (trailingMargins == null) {
                trailingMargins = new int[getCount() + 1];
            }
            if (!trailingMarginsValid) {
                computeMargins(false);
                trailingMarginsValid = true;
            }
            return trailingMargins;
        }

        private boolean solve(int[] a) {
            return solve(getArcs(), a);
        }

        private boolean computeHasWeights() {
            for (int i = 0, N = getChildCount(); i < N; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == View.GONE) {
                    continue;
                }
                LayoutParams lp = getLayoutParams(child);
                Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
                if (spec.weight != 0) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasWeights() {
            if (!hasWeightsValid) {
                hasWeights = computeHasWeights();
                hasWeightsValid = true;
            }
            return hasWeights;
        }

        public int[] getDeltas() {
            if (deltas == null) {
                deltas = new int[getChildCount()];
            }
            return deltas;
        }

        private void shareOutDelta(int totalDelta, float totalWeight) {
            Arrays.fill(deltas, 0);
            for (int i = 0, N = getChildCount(); i < N; i++) {
                final View c = getChildAt(i);
                if (c.getVisibility() == View.GONE) {
                    continue;
                }
                LayoutParams lp = getLayoutParams(c);
                Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
                float weight = spec.weight;
                if (weight != 0) {
                    int delta = Math.round((weight * totalDelta / totalWeight));
                    deltas[i] = delta;
                    // remove the delta and weight from the totals to avoid rounding errors
                    totalDelta -= delta;
                    totalWeight -= weight;
                }
            }
        }

        private void solveAndDistributeSpace(int[] a) {
            Arrays.fill(getDeltas(), 0);
            solve(a);
            int deltaMax = parentMin.value * getChildCount() + 1; //exclusive
            if (deltaMax < 2) {
                return; //don't have any delta to distribute
            }
            int deltaMin = 0; //inclusive

            float totalWeight = calculateTotalWeight();

            int validDelta = -1; //delta for which a solution exists
            boolean validSolution = true;
            // do a binary search to find the max delta that won't conflict with constraints
            while (deltaMin < deltaMax) {
                // cast to long to prevent overflow.
                final int delta = (int) (((long) deltaMin + deltaMax) / 2);
                invalidateValues();
                shareOutDelta(delta, totalWeight);
                validSolution = solve(getArcs(), a, false);
                if (validSolution) {
                    validDelta = delta;
                    deltaMin = delta + 1;
                } else {
                    deltaMax = delta;
                }
            }
            if (validDelta > 0 && !validSolution) {
                // last solution was not successful but we have a successful one. Use it.
                invalidateValues();
                shareOutDelta(validDelta, totalWeight);
                solve(a);
            }
        }

        private float calculateTotalWeight() {
            float totalWeight = 0f;
            for (int i = 0, N = getChildCount(); i < N; i++) {
                View c = getChildAt(i);
                if (c.getVisibility() == View.GONE) {
                    continue;
                }
                LayoutParams lp = getLayoutParams(c);
                Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
                totalWeight += spec.weight;
            }
            return totalWeight;
        }

        private void computeLocations(int[] a) {
            if (!hasWeights()) {
                solve(a);
            } else {
                solveAndDistributeSpace(a);
            }
            if (!orderPreserved) {
                // Solve returns the smallest solution to the constraint system for which all
                // values are positive. One value is therefore zero - though if the row/col
                // order is not preserved this may not be the first vertex. For consistency,
                // translate all the values so that they measure the distance from a[0]; the
                // leading edge of the parent. After this transformation some values may be
                // negative.
                int a0 = a[0];
                for (int i = 0, N = a.length; i < N; i++) {
                    a[i] = a[i] - a0;
                }
            }
        }

        public int[] getLocations() {
            if (locations == null) {
                int N = getCount() + 1;
                locations = new int[N];
            }
            if (!locationsValid) {
                computeLocations(locations);
                locationsValid = true;
            }
            return locations;
        }

        private int size(int[] locations) {
            // The parental edges are attached to vertices 0 and N - even when order is not
            // being preserved and other vertices fall outside this range. Measure the distance
            // between vertices 0 and N, assuming that locations[0] = 0.
            return locations[getCount()];
        }

        private void setParentConstraints(int min, int max) {
            parentMin.value = min;
            parentMax.value = -max;
            locationsValid = false;
        }

        private int getMeasure(int min, int max) {
            setParentConstraints(min, max);
            return size(getLocations());
        }

        public int getMeasure(int measureSpec) {
            int mode = MeasureSpec.getMode(measureSpec);
            int size = MeasureSpec.getSize(measureSpec);
            switch (mode) {
                case MeasureSpec.UNSPECIFIED: {
                    return getMeasure(0, MAX_SIZE);
                }
                case MeasureSpec.EXACTLY: {
                    return getMeasure(size, size);
                }
                case MeasureSpec.AT_MOST: {
                    return getMeasure(0, size);
                }
                default: {
                    assert false;
                    return 0;
                }
            }
        }

        public void layout(int size) {
            setParentConstraints(size, size);
            getLocations();
        }

        public void invalidateStructure() {
            maxIndex = UNDEFINED;

            groupBounds = null;
            forwardLinks = null;
            backwardLinks = null;

            leadingMargins = null;
            trailingMargins = null;
            arcs = null;

            locations = null;

            deltas = null;
            hasWeightsValid = false;

            invalidateValues();
        }

        public void invalidateValues() {
            groupBoundsValid = false;
            forwardLinksValid = false;
            backwardLinksValid = false;

            leadingMarginsValid = false;
            trailingMarginsValid = false;
            arcsValid = false;

            locationsValid = false;
        }
    }

    /**
     * Layout information associated with each of the children of a GridLayout: a
     * {@link Spec} for its row and one for its column.
     */
    public static class LayoutParams extends MarginLayoutParams {

        // Default values

        private static final int DEFAULT_WIDTH = WRAP_CONTENT;
        private static final int DEFAULT_HEIGHT = WRAP_CONTENT;
        private static final int DEFAULT_MARGIN = UNDEFINED;
        private static final int DEFAULT_ROW = UNDEFINED;
        private static final int DEFAULT_COLUMN = UNDEFINED;
        private static final Interval DEFAULT_SPAN = new Interval(UNDEFINED, UNDEFINED + 1);
        private static final int DEFAULT_SPAN_SIZE = DEFAULT_SPAN.size();

        // TypedArray indices

        private static final int[] MARGIN_ATTRS = {
            android.R.attr.layout_margin, android.R.attr.layout_marginLeft,
            android.R.attr.layout_marginTop, android.R.attr.layout_marginRight,
            android.R.attr.layout_marginBottom,
        };
        private static final int MARGIN = 0;
        private static final int LEFT_MARGIN = 1;
        private static final int TOP_MARGIN = 2;
        private static final int RIGHT_MARGIN = 3;
        private static final int BOTTOM_MARGIN = 4;

        private static final int[] LAYOUT_ATTRS = {
            android.R.attr.layout_column, android.R.attr.layout_columnSpan,
            android.R.attr.layout_columnWeight, android.R.attr.layout_row,
            android.R.attr.layout_rowSpan, android.R.attr.layout_rowWeight,
            android.R.attr.layout_gravity,
        };
        private static final int COLUMN = 0;
        private static final int COLUMN_SPAN = 1;
        private static final int COLUMN_WEIGHT = 2;
        private static final int ROW = 3;
        private static final int ROW_SPAN = 4;
        private static final int ROW_WEIGHT = 5;
        private static final int GRAVITY = 6;

        // Instance variables

        /**
         * The spec that defines the vertical characteristics of the cell group
         * described by these layout parameters.
         */
        public Spec rowSpec = Spec.UNDEFINED;

        /**
         * The spec that defines the horizontal characteristics of the cell group
         * described by these layout parameters.
         */
        public Spec columnSpec = Spec.UNDEFINED;

        // Constructors

        private LayoutParams(
                int width, int height,
                int left, int top, int right, int bottom,
                Spec rowSpec, Spec columnSpec) {
            super(width, height);
            setMargins(left, top, right, bottom);
            this.rowSpec = rowSpec;
            this.columnSpec = columnSpec;
        }

        public LayoutParams(Spec rowSpec, Spec columnSpec) {
            this(DEFAULT_WIDTH, DEFAULT_HEIGHT,
                    DEFAULT_MARGIN, DEFAULT_MARGIN, DEFAULT_MARGIN, DEFAULT_MARGIN,
                    rowSpec, columnSpec);
        }

        public LayoutParams() {
            this(Spec.UNDEFINED, Spec.UNDEFINED);
        }

        // Copying constructors

        public LayoutParams(ViewGroup.LayoutParams params) {
            super(params);
        }

        public LayoutParams(MarginLayoutParams params) {
            super(params);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.rowSpec = source.rowSpec;
            this.columnSpec = source.columnSpec;
        }

        // AttributeSet constructors

        public LayoutParams(Context context, AttributeSet attrs) {
            super(context, attrs);
            reInitSuper(context, attrs);
            init(context, attrs);
        }

        // Implementation

        // Reinitialise the margins using a different default policy than MarginLayoutParams.
        // Here we use the value UNDEFINED (as distinct from zero) to represent the undefined state
        // so that a layout manager default can be accessed post set up. We need this as, at the
        // point of installation, we do not know how many rows/cols there are and therefore
        // which elements are positioned next to the container's trailing edges. We need to
        // know this as margins around the container's boundary should have different
        // defaults to those between peers.

        // This method could be parametrized and moved into MarginLayout.
        private void reInitSuper(Context context, AttributeSet attrs) {
            TypedArray a = context.obtainStyledAttributes(attrs, MARGIN_ATTRS);
            try {
                int margin = a.getDimensionPixelSize(MARGIN, DEFAULT_MARGIN);

                this.leftMargin = a.getDimensionPixelSize(LEFT_MARGIN, margin);
                this.topMargin = a.getDimensionPixelSize(TOP_MARGIN, margin);
                this.rightMargin = a.getDimensionPixelSize(RIGHT_MARGIN, margin);
                this.bottomMargin = a.getDimensionPixelSize(BOTTOM_MARGIN, margin);
            } finally {
                a.recycle();
            }
        }

        private void init(Context context, AttributeSet attrs) {
            TypedArray a = context.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
            try {
                int gravity = a.getInt(GRAVITY, Gravity.NO_GRAVITY);

                int column = a.getInt(COLUMN, DEFAULT_COLUMN);
                int colSpan = a.getInt(COLUMN_SPAN, DEFAULT_SPAN_SIZE);
                float colWeight = a.getFloat(COLUMN_WEIGHT, Spec.DEFAULT_WEIGHT);
                this.columnSpec = spec(column, colSpan, getAlignment(gravity, true), colWeight);

                int row = a.getInt(ROW, DEFAULT_ROW);
                int rowSpan = a.getInt(ROW_SPAN, DEFAULT_SPAN_SIZE);
                float rowWeight = a.getFloat(ROW_WEIGHT, Spec.DEFAULT_WEIGHT);
                this.rowSpec = spec(row, rowSpan, getAlignment(gravity, false), rowWeight);
            } finally {
                a.recycle();
            }
        }

        /**
         * Describes how the child views are positioned. Default is {@code LEFT | BASELINE}.
         */
        public void setGravity(int gravity) {
            rowSpec = rowSpec.copyWriteAlignment(getAlignment(gravity, false));
            columnSpec = columnSpec.copyWriteAlignment(getAlignment(gravity, true));
        }

        @Override
        protected void setBaseAttributes(TypedArray attributes, int widthAttr, int heightAttr) {
            this.width = attributes.getLayoutDimension(widthAttr, DEFAULT_WIDTH);
            this.height = attributes.getLayoutDimension(heightAttr, DEFAULT_HEIGHT);
        }

        final void setRowSpecSpan(Interval span) {
            rowSpec = rowSpec.copyWriteSpan(span);
        }

        final void setColumnSpecSpan(Interval span) {
            columnSpec = columnSpec.copyWriteSpan(span);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LayoutParams that = (LayoutParams) o;

            if (!columnSpec.equals(that.columnSpec)) return false;
            if (!rowSpec.equals(that.rowSpec)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = rowSpec.hashCode();
            result = 31 * result + columnSpec.hashCode();
            return result;
        }
    }

    /*
    In place of a HashMap from span to Int, use an array of key/value pairs that is stored in Arcs.
    Add the mutables completesCycle flag to avoid creating another hash table for detecting cycles.
     */
    final static class Arc {
        public final Interval span;
        public final MutableInt value;
        public boolean valid = true;

        public Arc(Interval span, MutableInt value) {
            this.span = span;
            this.value = value;
        }

        @Override
        public String toString() {
            return span + " " + (!valid ? "+>" : "->") + " " + value;
        }
    }

    // A mutable Integer - used to avoid heap allocation during the layout operation

    final static class MutableInt {
        public int value;

        public MutableInt() {
            reset();
        }

        public MutableInt(int value) {
            this.value = value;
        }

        public void reset() {
            value = Integer.MIN_VALUE;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    final static class Assoc<K, V> extends ArrayList<Pair<K, V>> {
        private final Class<K> keyType;
        private final Class<V> valueType;

        private Assoc(Class<K> keyType, Class<V> valueType) {
            this.keyType = keyType;
            this.valueType = valueType;
        }

        public static <K, V> Assoc<K, V> of(Class<K> keyType, Class<V> valueType) {
            return new Assoc<K, V>(keyType, valueType);
        }

        public void put(K key, V value) {
            add(Pair.create(key, value));
        }

        @SuppressWarnings(value = "unchecked")
        public PackedMap<K, V> pack() {
            int N = size();
            K[] keys = (K[]) Array.newInstance(keyType, N);
            V[] values = (V[]) Array.newInstance(valueType, N);
            for (int i = 0; i < N; i++) {
                keys[i] = get(i).first;
                values[i] = get(i).second;
            }
            return new PackedMap<K, V>(keys, values);
        }
    }

    /*
    This data structure is used in place of a Map where we have an index that refers directly
    into the keys and values arrays. The index is used directly by the layout operation
    (avoiding the hash lookup), and the keys are hashed only once, when the map is created.
     */
    @SuppressWarnings(value = "unchecked")
    final static class PackedMap<K, V> {
        public final int[] index;
        public final K[] keys;
        public final V[] values;

        private PackedMap(K[] keys, V[] values) {
            this.index = createIndex(keys);

            this.keys = compact(keys, index);
            this.values = compact(values, index);
        }

        public V getValue(int i) {
            return values[index[i]];
        }

        private static <K> int[] createIndex(K[] keys) {
            int size = keys.length;
            int[] result = new int[size];

            Map<K, Integer> keyToIndex = new HashMap<K, Integer>();
            for (int i = 0; i < size; i++) {
                K key = keys[i];
                Integer index = keyToIndex.get(key);
                if (index == null) {
                    index = keyToIndex.size();
                    keyToIndex.put(key, index);
                }
                result[i] = index;
            }
            return result;
        }

        /*
        Create a compact array of keys or values using the supplied index.
         */
        private static <K> K[] compact(K[] a, int[] index) {
            int size = a.length;
            Class<?> componentType = a.getClass().getComponentType();
            K[] result = (K[]) Array.newInstance(componentType, max2(index, -1) + 1);

            // this overwrite duplicates, retaining the last equivalent entry
            for (int i = 0; i < size; i++) {
                result[index[i]] = a[i];
            }
            return result;
        }
    }

    /*
    For each group (with a given alignment) we need to store the amount of space required
    before the alignment point and the amount of space required after it. One side of this
    calculation is always 0 for START and END alignments but we don't make use of this.
    For CENTER and BASELINE alignments both sides are needed and in the BASELINE case no
    simple optimisations are possible.

    The general algorithm therefore is to create a Map (actually a PackedMap) from
    group to Bounds and to loop through all Views in the group taking the maximum
    of the values for each View.
    */
    static class Bounds {
        public int before;
        public int after;
        public int flexibility; // we're flexible iff all included specs are flexible

        private Bounds() {
            reset();
        }

        protected void reset() {
            before = Integer.MIN_VALUE;
            after = Integer.MIN_VALUE;
            flexibility = CAN_STRETCH; // from the above, we're flexible when empty
        }

        protected void include(int before, int after) {
            this.before = max(this.before, before);
            this.after = max(this.after, after);
        }

        protected int size(boolean min) {
            if (!min) {
                if (canStretch(flexibility)) {
                    return MAX_SIZE;
                }
            }
            return before + after;
        }

        protected int getOffset(GridLayout gl, View c, Alignment a, int size, boolean horizontal) {
            return before - a.getAlignmentValue(c, size, gl.getLayoutMode());
        }

        protected final void include(GridLayout gl, View c, Spec spec, Axis axis, int size) {
            this.flexibility &= spec.getFlexibility();
            boolean horizontal = axis.horizontal;
            Alignment alignment = spec.getAbsoluteAlignment(horizontal);
            // todo test this works correctly when the returned value is UNDEFINED
            int before = alignment.getAlignmentValue(c, size, gl.getLayoutMode());
            include(before, size - before);
        }

        @Override
        public String toString() {
            return "Bounds{" + "before=" + before + ", after=" + after + '}';
        }
    }

    /**
     * An Interval represents a contiguous range of values that lie between
     * the interval's {@link #min} and {@link #max} values.
     * <p>
     * Intervals are immutable so may be passed as values and used as keys in hash tables.
     * It is not necessary to have multiple instances of Intervals which have the same
     * {@link #min} and {@link #max} values.
     * <p>
     * Intervals are often written as {@code [min, max]} and represent the set of values
     * {@code x} such that {@code min <= x < max}.
     */
    final static class Interval {
        public final int min;
        public final int max;

        public Interval(int min, int max) {
            this.min = min;
            this.max = max;
        }

        int size() {
            return max - min;
        }

        Interval inverse() {
            return new Interval(max, min);
        }

        @Override
        public boolean equals(Object that) {
            if (this == that) {
                return true;
            }
            if (that == null || getClass() != that.getClass()) {
                return false;
            }

            Interval interval = (Interval) that;

            if (max != interval.max) {
                return false;
            }
            //noinspection RedundantIfStatement
            if (min != interval.min) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = min;
            result = 31 * result + max;
            return result;
        }

        @Override
        public String toString() {
            return "[" + min + ", " + max + "]";
        }
    }

    /**
     * A Spec defines the horizontal or vertical characteristics of a group of
     * cells: its span, alignment and weight.
     */
    public static class Spec {
        static final Spec UNDEFINED = spec(GridLayout.UNDEFINED);
        static final float DEFAULT_WEIGHT = 0;

        final boolean startDefined;
        final Interval span;
        final Alignment alignment;
        final float weight;

        private Spec(boolean startDefined, Interval span, Alignment alignment, float weight) {
            this.startDefined = startDefined;
            this.span = span;
            this.alignment = alignment;
            this.weight = weight;
        }

        private Spec(boolean startDefined, int start, int size, Alignment alignment, float weight) {
            this(startDefined, new Interval(start, start + size), alignment, weight);
        }

        private Alignment getAbsoluteAlignment(boolean horizontal) {
            if (alignment != UNDEFINED_ALIGNMENT) {
                return alignment;
            }
            if (weight == 0f) {
                return horizontal ? START : BASELINE;
            }
            return FILL;
        }

        final Spec copyWriteSpan(Interval span) {
            return new Spec(startDefined, span, alignment, weight);
        }

        final Spec copyWriteAlignment(Alignment alignment) {
            return new Spec(startDefined, span, alignment, weight);
        }

        final int getFlexibility() {
            return (alignment == UNDEFINED_ALIGNMENT && weight == 0) ? INFLEXIBLE : CAN_STRETCH;
        }

        /**
         * Returns {@code true} if the {@code class}, {@code alignment} and {@code span}
         * properties of this Spec and the supplied parameter are pairwise equal,
         * {@code false} otherwise.
         */
        @Override
        public boolean equals(Object that) {
            if (this == that) {
                return true;
            }
            if (that == null || getClass() != that.getClass()) {
                return false;
            }

            Spec spec = (Spec) that;

            if (!alignment.equals(spec.alignment)) {
                return false;
            }
            //noinspection RedundantIfStatement
            if (!span.equals(spec.span)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = span.hashCode();
            result = 31 * result + alignment.hashCode();
            return result;
        }
    }

    public static Spec spec(int start, int size, Alignment alignment, float weight) {
        return new Spec(start != UNDEFINED, start, size, alignment, weight);
    }

    public static Spec spec(int start, Alignment alignment, float weight) {
        return spec(start, 1, alignment, weight);
    }

    public static Spec spec(int start, int size, float weight) {
        return spec(start, size, UNDEFINED_ALIGNMENT, weight);
    }

    public static Spec spec(int start, float weight) {
        return spec(start, 1, weight);
    }

    public static Spec spec(int start, int size, Alignment alignment) {
        return spec(start, size, alignment, Spec.DEFAULT_WEIGHT);
    }

    public static Spec spec(int start, Alignment alignment) {
        return spec(start, 1, alignment);
    }

    public static Spec spec(int start, int size) {
        return spec(start, size, UNDEFINED_ALIGNMENT);
    }

    public static Spec spec(int start) {
        return spec(start, 1);
    }

    /**
     * Alignments specify where a view should be placed within a cell group and
     * what size it should be.
     */
    public static abstract class Alignment {
        Alignment() {
        }

        abstract int getGravityOffset(View view, int cellDelta);

        /**
         * Returns an alignment value. In the case of vertical alignments the value
         * returned should indicate the distance from the top of the view to the
         * alignment location.
         * For horizontal alignments measurement is made from the left edge of the component.
         */
        abstract int getAlignmentValue(View view, int viewSize, int mode);

        /**
         * Returns the size of the view specified by this alignment.
         * In the case of vertical alignments this method should return a height; for
         * horizontal alignments this method should return the width.
         * <p>
         * The default implementation returns {@code viewSize}.
         */
        int getSizeInCell(View view, int viewSize, int cellSize) {
            return viewSize;
        }

        Bounds getBounds() {
            return new Bounds();
        }
    }

    static final Alignment UNDEFINED_ALIGNMENT = new Alignment() {
        @Override
        int getGravityOffset(View view, int cellDelta) {
            return UNDEFINED;
        }

        @Override
        public int getAlignmentValue(View view, int viewSize, int mode) {
            return UNDEFINED;
        }
    };

    /**
     * Indicates that a view should be aligned with the <em>start</em>
     * edges of the other views in its cell group.
     */
    private static final Alignment LEADING = new Alignment() {
        @Override
        int getGravityOffset(View view, int cellDelta) {
            return 0;
        }

        @Override
        public int getAlignmentValue(View view, int viewSize, int mode) {
            return 0;
        }
    };

    /**
     * Indicates that a view should be aligned with the <em>end</em>
     * edges of the other views in its cell group.
     */
    private static final Alignment TRAILING = new Alignment() {
        @Override
        int getGravityOffset(View view, int cellDelta) {
            return cellDelta;
        }

        @Override
        public int getAlignmentValue(View view, int viewSize, int mode) {
            return viewSize;
        }
    };

    public static final Alignment TOP = LEADING;
    public static final Alignment BOTTOM = TRAILING;
    public static final Alignment START = LEADING;
    public static final Alignment END = TRAILING;

    private static Alignment createSwitchingAlignment(final Alignment ltr, final Alignment rtl) {
        return new Alignment() {
            @Override
            int getGravityOffset(View view, int cellDelta) {
                return (!view.isLayoutRtl() ? ltr : rtl).getGravityOffset(view, cellDelta);
            }

            @Override
            public int getAlignmentValue(View view, int viewSize, int mode) {
                return (!view.isLayoutRtl() ? ltr : rtl).getAlignmentValue(view, viewSize, mode);
            }
        };
    }

    public static final Alignment LEFT = createSwitchingAlignment(START, END);
    public static final Alignment RIGHT = createSwitchingAlignment(END, START);

    public static final Alignment CENTER = new Alignment() {
        @Override
        int getGravityOffset(View view, int cellDelta) {
            return cellDelta >> 1;
        }

        @Override
        public int getAlignmentValue(View view, int viewSize, int mode) {
            return viewSize >> 1;
        }
    };

    public static final Alignment BASELINE = new Alignment() {
        @Override
        int getGravityOffset(View view, int cellDelta) {
            return 0; // baseline gravity is top
        }

        @Override
        public int getAlignmentValue(View view, int viewSize, int mode) {
            if (view.getVisibility() == GONE) {
                return 0;
            }
            int baseline = view.getBaseline();
            return baseline == -1 ? UNDEFINED : baseline;
        }

        @Override
        public Bounds getBounds() {
            return new Bounds() {
                /*
                In a baseline aligned row in which some components define a baseline
                and some don't, we need a third variable to properly account for all
                the sizes. This tracks the maximum size of all the components -
                including those that don't define a baseline.
                */
                private int size;

                @Override
                protected void reset() {
                    super.reset();
                    size = Integer.MIN_VALUE;
                }

                @Override
                protected void include(int before, int after) {
                    super.include(before, after);
                    size = max(size, before + after);
                }

                @Override
                protected int size(boolean min) {
                    return max(super.size(min), size);
                }

                @Override
                protected int getOffset(GridLayout gl, View c, Alignment a, int size, boolean hrz) {
                    return max(0, super.getOffset(gl, c, a, size, hrz));
                }
            };
        }
    };

    public static final Alignment FILL = new Alignment() {
        @Override
        int getGravityOffset(View view, int cellDelta) {
            return 0;
        }

        @Override
        public int getAlignmentValue(View view, int viewSize, int mode) {
            return UNDEFINED;
        }

        @Override
        public int getSizeInCell(View view, int viewSize, int cellSize) {
            return cellSize;
        }
    };

    static boolean canStretch(int flexibility) {
        return (flexibility & CAN_STRETCH) != 0;
    }

    private static final int INFLEXIBLE = 0;
    private static final int CAN_STRETCH = 2;
}
