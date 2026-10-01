package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import java.util.regex.Pattern;

/**
 * Port of AOSP TableLayout: a vertical stack of TableRows whose cells line up
 * in columns, with stretchable, shrinkable and collapsed columns.
 */
public class TableLayout extends LinearLayout {
    private int[] mMaxWidths;
    private SparseBooleanArray mStretchableColumns;
    private SparseBooleanArray mShrinkableColumns;
    private SparseBooleanArray mCollapsedColumns;
    private boolean mShrinkAllColumns;
    private boolean mStretchAllColumns;
    private PassThroughHierarchyChangeListener mPassThroughListener;
    private boolean mInitialized;

    public TableLayout(Context context) {
        super(context);
        initTableLayout();
    }

    public TableLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        final TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.stretchColumns, android.R.attr.shrinkColumns, android.R.attr.collapseColumns});
        final String stretchedColumns = a.getString(0);
        if (stretchedColumns != null && stretchedColumns.length() > 0) {
            if (stretchedColumns.charAt(0) == '*') mStretchAllColumns = true;
            else mStretchableColumns = parseColumns(stretchedColumns);
        }
        final String shrinkedColumns = a.getString(1);
        if (shrinkedColumns != null && shrinkedColumns.length() > 0) {
            if (shrinkedColumns.charAt(0) == '*') mShrinkAllColumns = true;
            else mShrinkableColumns = parseColumns(shrinkedColumns);
        }
        final String collapsedColumns = a.getString(2);
        if (collapsedColumns != null) mCollapsedColumns = parseColumns(collapsedColumns);
        a.recycle();
        initTableLayout();
    }

    private static SparseBooleanArray parseColumns(String sequence) {
        final SparseBooleanArray columns = new SparseBooleanArray();
        final Pattern pattern = Pattern.compile("\\s*,\\s*");
        final String[] columnDefs = pattern.split(sequence);
        for (String columnIdentifier : columnDefs) {
            try {
                final int columnIndex = Integer.parseInt(columnIdentifier.trim());
                // Only valid, positive column indices are kept.
                if (columnIndex >= 0) columns.put(columnIndex, true);
            } catch (NumberFormatException e) {
                // Ignored, as in AOSP.
            }
        }
        return columns;
    }

    private void initTableLayout() {
        if (mCollapsedColumns == null) mCollapsedColumns = new SparseBooleanArray();
        if (mStretchableColumns == null) mStretchableColumns = new SparseBooleanArray();
        if (mShrinkableColumns == null) mShrinkableColumns = new SparseBooleanArray();
        // A table is always vertical.
        setOrientation(VERTICAL);
        mPassThroughListener = new PassThroughHierarchyChangeListener();
        super.setOnHierarchyChangeListener(mPassThroughListener);
        mInitialized = true;
    }

    @Override
    public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) {
        mPassThroughListener.mOnHierarchyChangeListener = listener;
    }

    private void requestRowsLayout() {
        if (mInitialized) {
            final int count = getChildCount();
            for (int i = 0; i < count; i++) getChildAt(i).requestLayout();
        }
    }

    @Override
    public void requestLayout() {
        if (mInitialized) {
            final int count = getChildCount();
            for (int i = 0; i < count; i++) getChildAt(i).forceLayout();
        }
        super.requestLayout();
    }

    public boolean isShrinkAllColumns() { return mShrinkAllColumns; }

    public void setShrinkAllColumns(boolean shrinkAllColumns) { mShrinkAllColumns = shrinkAllColumns; }

    public boolean isStretchAllColumns() { return mStretchAllColumns; }

    public void setStretchAllColumns(boolean stretchAllColumns) { mStretchAllColumns = stretchAllColumns; }

    public void setColumnCollapsed(int columnIndex, boolean isCollapsed) {
        mCollapsedColumns.put(columnIndex, isCollapsed);
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View view = getChildAt(i);
            if (view instanceof TableRow) ((TableRow) view).setColumnCollapsed(columnIndex, isCollapsed);
        }
        requestRowsLayout();
    }

    public boolean isColumnCollapsed(int columnIndex) { return mCollapsedColumns.get(columnIndex); }

    public void setColumnStretchable(int columnIndex, boolean isStretchable) {
        mStretchableColumns.put(columnIndex, isStretchable);
        requestRowsLayout();
    }

    public boolean isColumnStretchable(int columnIndex) { return mStretchAllColumns || mStretchableColumns.get(columnIndex); }

    public void setColumnShrinkable(int columnIndex, boolean isShrinkable) {
        mShrinkableColumns.put(columnIndex, isShrinkable);
        requestRowsLayout();
    }

    public boolean isColumnShrinkable(int columnIndex) { return mShrinkAllColumns || mShrinkableColumns.get(columnIndex); }

    private void trackCollapsedColumns(View child) {
        if (child instanceof TableRow) {
            final TableRow row = (TableRow) child;
            final SparseBooleanArray collapsedColumns = mCollapsedColumns;
            final int count = collapsedColumns.size();
            for (int i = 0; i < count; i++) {
                final int columnIndex = collapsedColumns.keyAt(i);
                final boolean isCollapsed = collapsedColumns.valueAt(i);
                // Only collapsed columns are applied: a row's cells are visible by default.
                if (isCollapsed) row.setColumnCollapsed(columnIndex, isCollapsed);
            }
        }
    }

    @Override
    public void addView(View child) {
        super.addView(child);
        requestRowsLayout();
    }

    @Override
    public void addView(View child, int index) {
        super.addView(child, index);
        requestRowsLayout();
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        super.addView(child, params);
        requestRowsLayout();
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        requestRowsLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Tables are always vertical.
        measureVertical(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        layoutVertical(l, t, r, b);
    }

    @Override
    void measureChildBeforeLayout(View child, int childIndex, int widthMeasureSpec, int totalWidth,
            int heightMeasureSpec, int totalHeight) {
        // When measuring, the table tells each row the column widths.
        if (child instanceof TableRow) ((TableRow) child).setColumnsWidthConstraints(mMaxWidths);
        super.measureChildBeforeLayout(child, childIndex, widthMeasureSpec, totalWidth, heightMeasureSpec, totalHeight);
    }

    @Override
    void measureVertical(int widthMeasureSpec, int heightMeasureSpec) {
        findLargestCells(widthMeasureSpec, heightMeasureSpec);
        shrinkAndStretchColumns(widthMeasureSpec);
        super.measureVertical(widthMeasureSpec, heightMeasureSpec);
    }

    private void findLargestCells(int widthMeasureSpec, int heightMeasureSpec) {
        boolean firstRow = true;
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            if (child instanceof TableRow) {
                final TableRow row = (TableRow) child;
                // Rows are always wrap_content tall.
                final ViewGroup.LayoutParams layoutParams = row.getLayoutParams();
                layoutParams.height = LayoutParams.WRAP_CONTENT;
                final int[] widths = row.getColumnsWidths(widthMeasureSpec, heightMeasureSpec);
                final int newLength = widths.length;
                if (firstRow) {
                    if (mMaxWidths == null || mMaxWidths.length != newLength) mMaxWidths = new int[newLength];
                    System.arraycopy(widths, 0, mMaxWidths, 0, newLength);
                    firstRow = false;
                } else {
                    int length = mMaxWidths.length;
                    final int difference = newLength - length;
                    // This row has more columns than the previous ones.
                    if (difference > 0) {
                        final int[] oldMaxWidths = mMaxWidths;
                        mMaxWidths = new int[newLength];
                        System.arraycopy(oldMaxWidths, 0, mMaxWidths, 0, oldMaxWidths.length);
                        length = newLength;
                    }
                    final int[] maxWidths = mMaxWidths;
                    length = Math.min(length, newLength);
                    for (int j = 0; j < length; j++) maxWidths[j] = Math.max(maxWidths[j], widths[j]);
                }
            }
        }
    }

    private void shrinkAndStretchColumns(int widthMeasureSpec) {
        // Nothing to do without rows.
        if (mMaxWidths == null) return;
        int totalWidth = 0;
        for (int width : mMaxWidths) totalWidth += width;
        final int size = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        if (totalWidth > size && (mShrinkAllColumns || mShrinkableColumns.size() > 0)) {
            // Oops, the largest columns do not fit; shrink the shrinkable ones.
            mutateColumnsWidth(mShrinkableColumns, mShrinkAllColumns, size, totalWidth);
        } else if (totalWidth < size && (mStretchAllColumns || mStretchableColumns.size() > 0)) {
            // Extra room; give it to the stretchable columns.
            mutateColumnsWidth(mStretchableColumns, mStretchAllColumns, size, totalWidth);
        }
    }

    private void mutateColumnsWidth(SparseBooleanArray columns, boolean allColumns, int size, int totalWidth) {
        int skipped = 0;
        final int[] maxWidths = mMaxWidths;
        final int length = maxWidths.length;
        final int count = allColumns ? length : columns.size();
        final int totalExtraSpace = size - totalWidth;
        int extraSpace = totalExtraSpace / count;

        // Column widths changed: the rows must be measured again.
        final int nbChildren = getChildCount();
        for (int i = 0; i < nbChildren; i++) {
            final View child = getChildAt(i);
            if (child instanceof TableRow) child.forceLayout();
        }

        if (!allColumns) {
            for (int i = 0; i < count; i++) {
                final int column = columns.keyAt(i);
                if (columns.valueAt(i)) {
                    if (column < length) maxWidths[column] += extraSpace;
                    else skipped++;
                }
            }
        } else {
            for (int i = 0; i < count; i++) maxWidths[i] += extraSpace;
            // Every column changed, nothing was skipped.
            return;
        }

        if (skipped > 0 && skipped < count) {
            // Spread the space of missing columns over the existing ones.
            extraSpace = skipped * extraSpace / (count - skipped);
            for (int i = 0; i < count; i++) {
                final int column = columns.keyAt(i);
                if (columns.valueAt(i) && column < length) {
                    if (extraSpace > maxWidths[column]) maxWidths[column] = 0;
                    else maxWidths[column] += extraSpace;
                }
            }
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) { return new TableLayout.LayoutParams(getContext(), attrs); }

    @Override
    protected LinearLayout.LayoutParams generateDefaultLayoutParams() { return new LayoutParams(); }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof TableLayout.LayoutParams; }

    @Override
    protected LinearLayout.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) { return new LayoutParams(p); }

    @Override
    public CharSequence getAccessibilityClassName() { return TableLayout.class.getName(); }

    /** Rows are always match_parent wide. */
    public static class LayoutParams extends LinearLayout.LayoutParams {
        public LayoutParams(Context c, AttributeSet attrs) { super(c, attrs); }

        public LayoutParams(int w, int h) { super(MATCH_PARENT, h); }

        public LayoutParams(int w, int h, float initWeight) { super(MATCH_PARENT, h, initWeight); }

        public LayoutParams() { super(MATCH_PARENT, WRAP_CONTENT); }

        public LayoutParams(ViewGroup.LayoutParams p) {
            super(p);
            width = MATCH_PARENT;
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
            width = MATCH_PARENT;
            if (source instanceof TableLayout.LayoutParams) weight = ((TableLayout.LayoutParams) source).weight;
        }

        @Override
        protected void setBaseAttributes(TypedArray a, int widthAttr, int heightAttr) {
            width = MATCH_PARENT;
            height = a.hasValue(heightAttr) ? a.getLayoutDimension(heightAttr, "layout_height") : WRAP_CONTENT;
        }
    }

    // Applies collapsed columns to rows as they are added.
    private class PassThroughHierarchyChangeListener implements OnHierarchyChangeListener {
        private OnHierarchyChangeListener mOnHierarchyChangeListener;

        public void onChildViewAdded(View parent, View child) {
            trackCollapsedColumns(child);
            if (mOnHierarchyChangeListener != null) mOnHierarchyChangeListener.onChildViewAdded(parent, child);
        }

        public void onChildViewRemoved(View parent, View child) {
            if (mOnHierarchyChangeListener != null) mOnHierarchyChangeListener.onChildViewRemoved(parent, child);
        }
    }
}
