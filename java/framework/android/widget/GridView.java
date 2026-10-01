package android.widget;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Port of AOSP GridView's measuring and column rules (determineColumns,
 * stretch modes, spacing, gravity) on top of AbsListView, which lays out
 * rows of {@link #getNumColumns()} items. D-pad navigation moves by item
 * and row and scrolls the selected row into view.
 */
public class GridView extends AbsListView {
    public static final int NO_STRETCH = 0;
    public static final int STRETCH_SPACING = 1;
    public static final int STRETCH_COLUMN_WIDTH = 2;
    public static final int STRETCH_SPACING_UNIFORM = 3;
    public static final int AUTO_FIT = -1;

    private int mNumColumns = AUTO_FIT;
    private int mHorizontalSpacing = 0;
    private int mRequestedHorizontalSpacing;
    private int mVerticalSpacing = 0;
    private int mStretchMode = STRETCH_COLUMN_WIDTH;
    private int mColumnWidth;
    private int mRequestedColumnWidth;
    private int mRequestedNumColumns;
    private int mGravity = Gravity.START;

    public GridView(Context context) { this(context, null); }

    public GridView(Context context, AttributeSet attrs) { this(context, attrs, android.R.attr.gridViewStyle); }

    public GridView(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public GridView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.horizontalSpacing, android.R.attr.verticalSpacing, android.R.attr.stretchMode,
                android.R.attr.columnWidth, android.R.attr.numColumns, android.R.attr.gravity},
                defStyleAttr, defStyleRes);
        setHorizontalSpacing(a.getDimensionPixelOffset(0, 0));
        setVerticalSpacing(a.getDimensionPixelOffset(1, 0));
        final int index = a.getInt(2, STRETCH_COLUMN_WIDTH);
        if (index >= 0) setStretchMode(index);
        final int columnWidth = a.getDimensionPixelOffset(3, -1);
        if (columnWidth > 0) setColumnWidth(columnWidth);
        setNumColumns(a.getInt(4, 1));
        final int gravity = a.getInt(5, -1);
        if (gravity >= 0) setGravity(gravity);
        a.recycle();
    }

    @Override
    public ListAdapter getAdapter() { return mAdapter; }

    @Override
    public void setAdapter(ListAdapter adapter) { super.setAdapter(adapter); }

    public void setRemoteViewsAdapter(Intent intent) {}

    // ---------------------------------------------------------------- AbsListView row hooks

    @Override
    int itemsPerRow() { return Math.max(1, mNumColumns); }

    @Override
    int childGap() { return mVerticalSpacing; }

    @Override
    int childWidthMeasureSpec(AbsListView.LayoutParams p, int column) {
        return ViewGroup.getChildMeasureSpec(MeasureSpec.makeMeasureSpec(mColumnWidth, MeasureSpec.EXACTLY), 0, p.width);
    }

    @Override
    int childLeft(int column, int measuredWidth) {
        final int n = itemsPerRow();
        if (isLayoutRtl()) column = n - 1 - column;
        int x = mListPadding.left + (mStretchMode == STRETCH_SPACING_UNIFORM ? mHorizontalSpacing : 0);
        x += column * (mColumnWidth + mHorizontalSpacing);
        final int absoluteGravity = Gravity.getAbsoluteGravity(mGravity, getLayoutDirection());
        switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
            case Gravity.CENTER_HORIZONTAL:
                return x + (mColumnWidth - measuredWidth) / 2;
            case Gravity.RIGHT:
                return x + mColumnWidth - measuredWidth;
            default:
                return x;
        }
    }

    // ---------------------------------------------------------------- measuring

    private boolean determineColumns(int availableSpace) {
        final int requestedHorizontalSpacing = mRequestedHorizontalSpacing;
        final int stretchMode = mStretchMode;
        final int requestedColumnWidth = mRequestedColumnWidth;
        boolean didNotInitiallyFit = false;
        if (mRequestedNumColumns == AUTO_FIT) {
            if (requestedColumnWidth > 0) {
                // Client told us to pick the number of columns.
                mNumColumns = (availableSpace + requestedHorizontalSpacing)
                        / (requestedColumnWidth + requestedHorizontalSpacing);
            } else {
                // Just make up a number if we don't have enough info.
                mNumColumns = 2;
            }
        } else {
            mNumColumns = mRequestedNumColumns;
        }
        if (mNumColumns <= 0) mNumColumns = 1;
        if (stretchMode == NO_STRETCH) {
            mColumnWidth = requestedColumnWidth;
            mHorizontalSpacing = requestedHorizontalSpacing;
        } else {
            final int spaceLeftOver = availableSpace - (mNumColumns * requestedColumnWidth)
                    - ((mNumColumns - 1) * requestedHorizontalSpacing);
            if (spaceLeftOver < 0) didNotInitiallyFit = true;
            switch (stretchMode) {
                case STRETCH_COLUMN_WIDTH:
                    mColumnWidth = requestedColumnWidth + spaceLeftOver / mNumColumns;
                    mHorizontalSpacing = requestedHorizontalSpacing;
                    break;
                case STRETCH_SPACING:
                    mColumnWidth = requestedColumnWidth;
                    mHorizontalSpacing = mNumColumns > 1 ? requestedHorizontalSpacing + spaceLeftOver / (mNumColumns - 1)
                            : requestedHorizontalSpacing + spaceLeftOver;
                    break;
                case STRETCH_SPACING_UNIFORM:
                    mColumnWidth = requestedColumnWidth;
                    mHorizontalSpacing = mNumColumns > 1 ? requestedHorizontalSpacing + spaceLeftOver / (mNumColumns + 1)
                            : requestedHorizontalSpacing + spaceLeftOver;
                    break;
                default:
                    break;
            }
        }
        return didNotInitiallyFit;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Sets up mListPadding.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        if (widthMode == MeasureSpec.UNSPECIFIED) {
            widthSize = mColumnWidth > 0 ? mColumnWidth + mListPadding.left + mListPadding.right
                    : mListPadding.left + mListPadding.right;
            widthSize += getVerticalScrollbarWidth();
        }
        final int childWidth = widthSize - mListPadding.left - mListPadding.right;
        final boolean didNotInitiallyFit = determineColumns(childWidth);

        int childHeight = 0;
        int childState = 0;
        mItemCount = mAdapter == null ? 0 : mAdapter.getCount();
        final int count = mItemCount;
        if (count > 0) {
            final View child = mAdapter.getView(0, mRecycler.getScrapView(0), this);
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (!(lp instanceof AbsListView.LayoutParams)) {
                lp = lp == null ? generateDefaultLayoutParams() : generateLayoutParams(lp);
                child.setLayoutParams(lp);
            }
            final AbsListView.LayoutParams p = (AbsListView.LayoutParams) lp;
            p.viewType = mAdapter.getItemViewType(0);
            final int childHeightSpec = getChildMeasureSpec(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.UNSPECIFIED), 0, p.height);
            final int childWidthSpec = getChildMeasureSpec(
                    MeasureSpec.makeMeasureSpec(mColumnWidth, MeasureSpec.EXACTLY), 0, p.width);
            child.measure(childWidthSpec, childHeightSpec);
            childHeight = child.getMeasuredHeight();
            childState = combineMeasuredStates(childState, child.getMeasuredState());
            mRecycler.addScrapView(child);
        }
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            heightSize = mListPadding.top + mListPadding.bottom + childHeight + getVerticalFadingEdgeLength() * 2;
        }
        if (heightMode == MeasureSpec.AT_MOST) {
            int ourSize = mListPadding.top + mListPadding.bottom;
            final int numColumns = mNumColumns;
            for (int i = 0; i < count; i += numColumns) {
                ourSize += childHeight;
                if (i + numColumns < count) ourSize += mVerticalSpacing;
                if (ourSize >= heightSize) {
                    ourSize = heightSize;
                    break;
                }
            }
            heightSize = ourSize;
        }
        if (widthMode == MeasureSpec.AT_MOST && mRequestedNumColumns != AUTO_FIT) {
            final int ourSize = (mRequestedNumColumns * mColumnWidth) + ((mRequestedNumColumns - 1) * mHorizontalSpacing)
                    + mListPadding.left + mListPadding.right;
            if (ourSize > widthSize || didNotInitiallyFit) widthSize |= MEASURED_STATE_TOO_SMALL;
        }
        setMeasuredDimension(widthSize, heightSize);
        mWidthMeasureSpec = widthMeasureSpec;
    }

    // ---------------------------------------------------------------- attributes

    public void setGravity(int gravity) {
        if (mGravity != gravity) {
            mGravity = gravity;
            requestLayoutIfNecessary();
        }
    }

    public int getGravity() { return mGravity; }

    public void setHorizontalSpacing(int horizontalSpacing) {
        if (horizontalSpacing != mRequestedHorizontalSpacing) {
            mRequestedHorizontalSpacing = horizontalSpacing;
            requestLayoutIfNecessary();
        }
    }

    public int getHorizontalSpacing() { return mHorizontalSpacing; }

    public int getRequestedHorizontalSpacing() { return mRequestedHorizontalSpacing; }

    public void setVerticalSpacing(int verticalSpacing) {
        if (verticalSpacing != mVerticalSpacing) {
            mVerticalSpacing = verticalSpacing;
            requestLayoutIfNecessary();
        }
    }

    public int getVerticalSpacing() { return mVerticalSpacing; }

    public void setStretchMode(int stretchMode) {
        if (stretchMode != mStretchMode) {
            mStretchMode = stretchMode;
            requestLayoutIfNecessary();
        }
    }

    public int getStretchMode() { return mStretchMode; }

    public void setColumnWidth(int columnWidth) {
        if (columnWidth != mRequestedColumnWidth) {
            mRequestedColumnWidth = columnWidth;
            requestLayoutIfNecessary();
        }
    }

    public int getColumnWidth() { return mColumnWidth; }

    public int getRequestedColumnWidth() { return mRequestedColumnWidth; }

    public void setNumColumns(int numColumns) {
        if (numColumns != mRequestedNumColumns) {
            mRequestedNumColumns = numColumns;
            requestLayoutIfNecessary();
        }
    }

    public int getNumColumns() { return mNumColumns; }

    private void requestLayoutIfNecessary() {
        if (getChildCount() > 0) {
            mLayoutMode = LAYOUT_SPECIFIC;
            mSpecificPosition = mFirstPosition - mFirstPosition % Math.max(1, mNumColumns);
            mSpecificTop = getChildAt(0).getTop() - mListPadding.top;
            requestLayout();
            invalidate();
        }
    }

    // ---------------------------------------------------------------- selection and keys

    @Override
    public void setSelection(int position) { setSelectionFromTop(position, 0); }

    public void smoothScrollByOffset(int offset) {
        final int target = Math.max(0, Math.min(mItemCount - 1, mFirstPosition + offset * Math.max(1, mNumColumns)));
        smoothScrollToPosition(target);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) { return commonKey(keyCode, 1, event); }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) { return commonKey(keyCode, repeatCount, event); }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (event.isTracking() && !event.isCanceled()
                && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (mSelectedPosition >= 0 && mAdapter != null && mSelectedPosition < mAdapter.getCount()) {
                final View child = getChildAt(mSelectedPosition - mFirstPosition);
                performItemClick(child, mSelectedPosition, mAdapter.getItemId(mSelectedPosition));
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean commonKey(int keyCode, int count, KeyEvent event) {
        if (mAdapter == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        boolean handled = false;
        for (int i = 0; i < count; i++) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    handled = arrowScroll(FOCUS_LEFT);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    handled = arrowScroll(FOCUS_RIGHT);
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    handled = arrowScroll(FOCUS_UP);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    handled = arrowScroll(FOCUS_DOWN);
                    break;
                case KeyEvent.KEYCODE_MOVE_HOME:
                    setSelection(0);
                    handled = true;
                    break;
                case KeyEvent.KEYCODE_MOVE_END:
                    setSelection(Math.max(0, mItemCount - 1));
                    handled = true;
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (event.hasNoModifiers() && event.getRepeatCount() == 0 && getChildCount() > 0
                            && mSelectedPosition >= 0) {
                        event.startTracking();
                        handled = true;
                    }
                    break;
                default:
                    break;
            }
        }
        return handled;
    }

    /** Moves the selection one item or row (AOSP arrowScroll); false at the grid's edge. */
    boolean arrowScroll(int direction) {
        if (mItemCount == 0) return false;
        final int n = Math.max(1, mNumColumns);
        final int sel = mSelectedPosition;
        int target;
        if (sel == INVALID_POSITION || sel >= mItemCount) {
            target = mFirstPosition;
        } else {
            switch (direction) {
                case FOCUS_LEFT:
                    if (sel % n == 0) return false;
                    target = sel - 1;
                    break;
                case FOCUS_RIGHT:
                    if (sel % n == n - 1 || sel + 1 >= mItemCount) return false;
                    target = sel + 1;
                    break;
                case FOCUS_UP:
                    if (sel < n) return false;
                    target = sel - n;
                    break;
                case FOCUS_DOWN:
                    if (sel / n == (mItemCount - 1) / n) return false;
                    target = Math.min(sel + n, mItemCount - 1);
                    break;
                default:
                    return false;
            }
        }
        reveal(target);
        return true;
    }

    private void reveal(int target) {
        final int n = Math.max(1, mNumColumns);
        final int rowStart = target - target % n;
        final int listTop = mListPadding.top;
        final int listBottom = getHeight() - mListPadding.bottom;
        final View child = getChildAt(target - mFirstPosition);
        if (child != null && child.getTop() >= listTop && child.getBottom() <= listBottom) {
            // Already on screen: keep the scroll position.
            selectWithAnchor(target, mFirstPosition, getChildAt(0).getTop() - listTop);
        } else if (child != null && child.getTop() < listTop || target < mFirstPosition) {
            selectWithAnchor(target, rowStart, 0);
        } else {
            final int rowHeight = child != null ? child.getHeight()
                    : (getChildCount() > 0 ? getChildAt(0).getHeight() : 0);
            selectWithAnchor(target, rowStart, Math.max(0, listBottom - listTop - rowHeight));
        }
    }

    @Override
    protected int computeVerticalScrollExtent() {
        final int count = getChildCount();
        if (count > 0) {
            final int numColumns = Math.max(1, mNumColumns);
            final int rowCount = (count + numColumns - 1) / numColumns;
            int extent = rowCount * 100;
            View view = getChildAt(0);
            final int top = view.getTop();
            int height = view.getHeight();
            if (height > 0) extent += (top * 100) / height;
            view = getChildAt(count - 1);
            final int bottom = view.getBottom();
            height = view.getHeight();
            if (height > 0) extent -= ((bottom - getHeight()) * 100) / height;
            return extent;
        }
        return 0;
    }

    @Override
    protected int computeVerticalScrollOffset() {
        if (mFirstPosition >= 0 && getChildCount() > 0) {
            final View view = getChildAt(0);
            final int top = view.getTop();
            final int height = view.getHeight();
            if (height > 0) {
                final int numColumns = Math.max(1, mNumColumns);
                final int whichRow = mFirstPosition / numColumns;
                return Math.max(whichRow * 100 - (top * 100) / height, 0);
            }
        }
        return 0;
    }

    @Override
    protected int computeVerticalScrollRange() {
        final int numColumns = Math.max(1, mNumColumns);
        final int rowCount = (mItemCount + numColumns - 1) / numColumns;
        return Math.max(rowCount * 100, 0);
    }

    @Override
    public CharSequence getAccessibilityClassName() { return GridView.class.getName(); }

    @Override
    public void onInitializeAccessibilityNodeInfoForItem(View view, int position, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfoForItem(view, position, info);
    }
}
