package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

/** Port of AOSP TableRow: a horizontal row whose cells are sized by the enclosing TableLayout. */
public class TableRow extends LinearLayout {
    private int mNumColumns = 0;
    private int[] mColumnWidths;
    private int[] mConstrainedColumnWidths;
    private SparseIntArray mColumnToChildIndex;
    private ChildrenTracker mChildrenTracker;

    public TableRow(Context context) {
        super(context);
        initTableRow();
    }

    public TableRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        initTableRow();
    }

    private void initTableRow() {
        final OnHierarchyChangeListener oldListener = mOnHierarchyChangeListener;
        mChildrenTracker = new ChildrenTracker();
        if (oldListener != null) mChildrenTracker.setOnHierarchyChangeListener(oldListener);
        super.setOnHierarchyChangeListener(mChildrenTracker);
    }

    @Override
    public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) {
        mChildrenTracker.setOnHierarchyChangeListener(listener);
    }

    /** Hidden AOSP API. Collapses or restores the cell at a column index. */
    void setColumnCollapsed(int columnIndex, boolean collapsed) {
        final View child = getVirtualChildAt(columnIndex);
        if (child != null) child.setVisibility(collapsed ? GONE : VISIBLE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Rows are always horizontal.
        measureHorizontal(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        layoutHorizontal(l, t, r, b);
    }

    @Override
    public View getVirtualChildAt(int i) {
        if (mColumnToChildIndex == null) mapIndexAndColumns();
        final int deflectedIndex = mColumnToChildIndex.get(i, -1);
        return deflectedIndex != -1 ? getChildAt(deflectedIndex) : null;
    }

    @Override
    public int getVirtualChildCount() {
        if (mColumnToChildIndex == null) mapIndexAndColumns();
        return mNumColumns;
    }

    private void mapIndexAndColumns() {
        if (mColumnToChildIndex == null) {
            int virtualCount = 0;
            final int count = getChildCount();
            mColumnToChildIndex = new SparseIntArray();
            final SparseIntArray columnToChild = mColumnToChildIndex;
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                final LayoutParams layoutParams = (LayoutParams) child.getLayoutParams();
                if (layoutParams.column >= virtualCount) virtualCount = layoutParams.column;
                for (int j = 0; j < layoutParams.span; j++) columnToChild.put(virtualCount++, i);
            }
            mNumColumns = virtualCount;
        }
    }

    @Override
    int measureNullChild(int childIndex) { return mConstrainedColumnWidths[childIndex]; }

    @Override
    void measureChildBeforeLayout(View child, int childIndex, int widthMeasureSpec, int totalWidth,
            int heightMeasureSpec, int totalHeight) {
        if (mConstrainedColumnWidths != null) {
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int measureMode = MeasureSpec.EXACTLY;
            int columnWidth = 0;
            final int span = lp.span;
            final int[] constrainedColumnWidths = mConstrainedColumnWidths;
            for (int i = 0; i < span; i++) columnWidth += constrainedColumnWidths[childIndex + i];
            final int gravity = lp.gravity;
            final boolean isHorizontalGravity = Gravity.isHorizontal(gravity);
            if (isHorizontalGravity) measureMode = MeasureSpec.AT_MOST;
            // Do not let the child exceed the column's width.
            final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                    Math.max(0, columnWidth - lp.leftMargin - lp.rightMargin), measureMode);
            final int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                    getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin + totalHeight, lp.height);
            child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            if (isHorizontalGravity) {
                final int childWidth = child.getMeasuredWidth();
                lp.mOffset[LayoutParams.LOCATION_NEXT] = columnWidth - childWidth;
                final int layoutDirection = getLayoutDirection();
                final int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);
                switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.RIGHT:
                        lp.mOffset[LayoutParams.LOCATION] = lp.mOffset[LayoutParams.LOCATION_NEXT];
                        break;
                    case Gravity.CENTER_HORIZONTAL:
                        lp.mOffset[LayoutParams.LOCATION] = lp.mOffset[LayoutParams.LOCATION_NEXT] / 2;
                        break;
                    default:
                        break;
                }
            } else {
                lp.mOffset[LayoutParams.LOCATION] = lp.mOffset[LayoutParams.LOCATION_NEXT] = 0;
            }
        } else {
            // Measuring the row on its own (no table above it).
            super.measureChildBeforeLayout(child, childIndex, widthMeasureSpec, totalWidth, heightMeasureSpec, totalHeight);
        }
    }

    @Override
    int getChildrenSkipCount(View child, int index) {
        final LayoutParams layoutParams = (LayoutParams) child.getLayoutParams();
        // Account for the column, not the index, as span can be > 1.
        return layoutParams.span - 1;
    }

    @Override
    int getLocationOffset(View child) { return ((TableRow.LayoutParams) child.getLayoutParams()).mOffset[LayoutParams.LOCATION]; }

    @Override
    int getNextLocationOffset(View child) {
        return ((TableRow.LayoutParams) child.getLayoutParams()).mOffset[LayoutParams.LOCATION_NEXT];
    }

    /** Hidden AOSP API. The width each column wants, from the children spanning one column. */
    int[] getColumnsWidths(int widthMeasureSpec, int heightMeasureSpec) {
        final int numColumns = getVirtualChildCount();
        if (mColumnWidths == null || numColumns != mColumnWidths.length) mColumnWidths = new int[numColumns];
        final int[] columnWidths = mColumnWidths;
        for (int i = 0; i < numColumns; i++) {
            final View child = getVirtualChildAt(i);
            if (child != null && child.getVisibility() != GONE) {
                final LayoutParams layoutParams = (LayoutParams) child.getLayoutParams();
                if (layoutParams.span == 1) {
                    int spec;
                    switch (layoutParams.width) {
                        case LayoutParams.WRAP_CONTENT:
                            spec = getChildMeasureSpec(widthMeasureSpec, 0, LayoutParams.WRAP_CONTENT);
                            break;
                        case LayoutParams.MATCH_PARENT:
                            spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                            break;
                        default:
                            spec = MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.EXACTLY);
                    }
                    child.measure(spec, spec);
                    final int width = child.getMeasuredWidth() + layoutParams.leftMargin + layoutParams.rightMargin;
                    columnWidths[i] = width;
                } else {
                    columnWidths[i] = 0;
                }
            } else {
                columnWidths[i] = 0;
            }
        }
        return columnWidths;
    }

    /** Hidden AOSP API. Column widths imposed by the TableLayout. */
    void setColumnsWidthConstraints(int[] columnWidths) {
        if (columnWidths == null || columnWidths.length < getVirtualChildCount()) {
            throw new IllegalArgumentException("columnWidths should be >= getVirtualChildCount()");
        }
        mConstrainedColumnWidths = columnWidths;
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) { return new TableRow.LayoutParams(getContext(), attrs); }

    @Override
    protected LinearLayout.LayoutParams generateDefaultLayoutParams() { return new LayoutParams(); }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof TableRow.LayoutParams; }

    @Override
    protected LinearLayout.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) { return new LayoutParams(p); }

    @Override
    public CharSequence getAccessibilityClassName() { return TableRow.class.getName(); }

    public static class LayoutParams extends LinearLayout.LayoutParams {
        public int column;
        public int span;

        private static final int LOCATION = 0;
        private static final int LOCATION_NEXT = 1;

        private final int[] mOffset = new int[2];

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            final TypedArray a = c.obtainStyledAttributes(attrs,
                    new int[] {android.R.attr.layout_column, android.R.attr.layout_span});
            column = a.getInt(0, -1);
            span = a.getInt(1, 1);
            if (span <= 1) span = 1;
            a.recycle();
        }

        public LayoutParams(int w, int h) {
            super(w, h);
            column = -1;
            span = 1;
        }

        public LayoutParams(int w, int h, float initWeight) {
            super(w, h, initWeight);
            column = -1;
            span = 1;
        }

        public LayoutParams() {
            super(MATCH_PARENT, WRAP_CONTENT);
            column = -1;
            span = 1;
        }

        public LayoutParams(int column) {
            this();
            this.column = column;
        }

        public LayoutParams(ViewGroup.LayoutParams p) {
            super(p);
            column = -1;
            span = 1;
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
            column = -1;
            span = 1;
        }

        @Override
        protected void setBaseAttributes(TypedArray a, int widthAttr, int heightAttr) {
            // A table cell is match_parent wide and wrap_content tall unless told otherwise.
            width = a.hasValue(widthAttr) ? a.getLayoutDimension(widthAttr, "layout_width") : MATCH_PARENT;
            height = a.hasValue(heightAttr) ? a.getLayoutDimension(heightAttr, "layout_height") : WRAP_CONTENT;
        }
    }

    // Children added or removed invalidate the column mapping.
    private class ChildrenTracker implements OnHierarchyChangeListener {
        private OnHierarchyChangeListener listener;

        private void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) { this.listener = listener; }

        public void onChildViewAdded(View parent, View child) {
            mColumnToChildIndex = null;
            if (listener != null) listener.onChildViewAdded(parent, child);
        }

        public void onChildViewRemoved(View parent, View child) {
            mColumnToChildIndex = null;
            if (listener != null) listener.onChildViewRemoved(parent, child);
        }
    }
}
