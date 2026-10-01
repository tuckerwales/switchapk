package android.widget;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;

/**
 * A vertical list of adapter rows with optional headers, footers and dividers (AOSP ListView).
 */
public class ListView extends AbsListView {
    private static final int[] LIST_ATTRS = {
        android.R.attr.entries,
        android.R.attr.divider,
        android.R.attr.dividerHeight,
        android.R.attr.headerDividersEnabled,
        android.R.attr.footerDividersEnabled,
    };

    private final ArrayList<FixedViewInfo> mHeaderViewInfos = new ArrayList<FixedViewInfo>();
    private final ArrayList<FixedViewInfo> mFooterViewInfos = new ArrayList<FixedViewInfo>();
    private Drawable mDivider;
    private int mDividerHeight;
    private boolean mHeaderDividersEnabled = true;
    private boolean mFooterDividersEnabled = true;
    private Drawable mOverScrollHeader;
    private Drawable mOverScrollFooter;
    private boolean mItemsCanFocus;
    private final Rect mTempRect = new Rect();

    public ListView(Context context) { this(context, null); }

    public ListView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.listViewStyle);
    }

    public ListView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        android.content.res.TypedArray a = context.obtainStyledAttributes(
                attrs, LIST_ATTRS, defStyleAttr, defStyleRes);
        CharSequence[] entries = a.getTextArray(0);
        Drawable divider = a.getDrawable(1);
        if (divider != null) setDivider(divider);
        int dividerHeight = a.getDimensionPixelSize(2, 0);
        if (dividerHeight != 0) setDividerHeight(dividerHeight);
        mHeaderDividersEnabled = a.getBoolean(3, true);
        mFooterDividersEnabled = a.getBoolean(4, true);
        a.recycle();
        if (entries != null) {
            setAdapter(new ArrayAdapter<CharSequence>(context, android.R.layout.simple_list_item_1, entries));
        }
    }

    public int getMaxScrollAmount() { return (int) (0.33f * (mBottom - mTop)); }

    @Override
    public ListAdapter getAdapter() { return mAdapter; }

    @Override
    public void setSelection(int position) { setSelectionFromTop(position, 0); }

    /** One fixed header or footer row. */
    public class FixedViewInfo {
        public View view;
        public Object data;
        public boolean isSelectable;
    }

    public void addHeaderView(View v, Object data, boolean isSelectable) {
        FixedViewInfo info = new FixedViewInfo();
        info.view = v;
        info.data = data;
        info.isSelectable = isSelectable;
        mHeaderViewInfos.add(info);
        if (mAdapter != null) {
            if (!(mAdapter instanceof HeaderViewListAdapter)) {
                setAdapter(mAdapter);
            } else {
                mItemCount = mAdapter.getCount();
                mDataChanged = true;
                requestLayout();
            }
        }
    }

    public void addHeaderView(View v) { addHeaderView(v, null, true); }

    public int getHeaderViewsCount() { return mHeaderViewInfos.size(); }

    public boolean removeHeaderView(View v) {
        if (mHeaderViewInfos.size() == 0) return false;
        boolean result = false;
        if (mAdapter instanceof HeaderViewListAdapter) {
            result = ((HeaderViewListAdapter) mAdapter).removeHeader(v);
        } else {
            result = removeFixedViewInfo(v, mHeaderViewInfos);
        }
        if (result) {
            mItemCount = mAdapter == null ? mHeaderViewInfos.size() + mFooterViewInfos.size() : mAdapter.getCount();
            mDataChanged = true;
            requestLayout();
        }
        return result;
    }

    public void addFooterView(View v, Object data, boolean isSelectable) {
        FixedViewInfo info = new FixedViewInfo();
        info.view = v;
        info.data = data;
        info.isSelectable = isSelectable;
        mFooterViewInfos.add(info);
        if (mAdapter != null) {
            if (!(mAdapter instanceof HeaderViewListAdapter)) setAdapter(mAdapter);
            else {
                mItemCount = mAdapter.getCount();
                mDataChanged = true;
                requestLayout();
            }
        }
    }

    public void addFooterView(View v) { addFooterView(v, null, true); }

    public int getFooterViewsCount() { return mFooterViewInfos.size(); }

    public boolean removeFooterView(View v) {
        if (mFooterViewInfos.size() == 0) return false;
        boolean result = false;
        if (mAdapter instanceof HeaderViewListAdapter) {
            result = ((HeaderViewListAdapter) mAdapter).removeFooter(v);
        } else {
            result = removeFixedViewInfo(v, mFooterViewInfos);
        }
        if (result) {
            mItemCount = mAdapter == null ? mHeaderViewInfos.size() + mFooterViewInfos.size() : mAdapter.getCount();
            mDataChanged = true;
            requestLayout();
        }
        return result;
    }

    private boolean removeFixedViewInfo(View v, ArrayList<FixedViewInfo> where) {
        for (int i = 0; i < where.size(); i++) {
            if (where.get(i).view == v) {
                where.remove(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        if (mHeaderViewInfos.size() > 0 || mFooterViewInfos.size() > 0) {
            adapter = new HeaderViewListAdapter(mHeaderViewInfos, mFooterViewInfos, adapter);
        }
        super.setAdapter(adapter);
    }

    @Override
    public void setRemoteViewsAdapter(Intent intent) { super.setRemoteViewsAdapter(intent); }

    public void smoothScrollByOffset(int offset) {
        int index = offset < 0 ? getFirstVisiblePosition() : getLastVisiblePosition();
        if (index < 0) return;
        smoothScrollToPosition(Math.max(0, Math.min(getCount(), index + offset)));
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        int count = getChildCount();
        if (count > 0) {
            for (int i = 0; i < count; i++) addHeaderView(getChildAt(i));
            // addHeaderView does not attach the view as a child. The inflate pass did.
            // Drop those children so the next layout adds them through the adapter.
            removeAllViewsInLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mListPadding.set(getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom());
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int childWidth = 0;
        int childHeight = 0;
        mItemCount = mAdapter == null ? 0 : mAdapter.getCount();
        if (mItemCount > 0 && (widthMode == MeasureSpec.UNSPECIFIED || heightMode != MeasureSpec.EXACTLY)) {
            View child = obtainAndMeasure(0, widthMeasureSpec);
            childWidth = child.getMeasuredWidth();
            childHeight = child.getMeasuredHeight();
            mRecycler.addScrapView(child);
        }
        if (widthMode == MeasureSpec.UNSPECIFIED) {
            widthSize = getPaddingLeft() + getPaddingRight() + childWidth;
        }
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            heightSize = getPaddingTop() + getPaddingBottom() + childHeight + getVerticalFadingEdgeLength() * 2;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            heightSize = measureHeightOfChildren(widthMeasureSpec, heightSize);
        }
        setMeasuredDimension(widthSize, heightSize);
        mWidthMeasureSpec = widthMeasureSpec;
    }

    private View obtainAndMeasure(int position, int widthMeasureSpec) {
        View scrap = mRecycler.getScrapView(position);
        View child = mAdapter.getView(position, scrap, this);
        if (child == null) child = new View(getContext());
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (!(lp instanceof LayoutParams)) {
            lp = lp == null ? generateDefaultLayoutParams() : generateLayoutParams(lp);
            child.setLayoutParams(lp);
        }
        ((LayoutParams) lp).viewType = mAdapter.getItemViewType(position);
        int childWidth = ViewGroup.getChildMeasureSpec(widthMeasureSpec,
                mListPadding.left + mListPadding.right, lp.width);
        int childHeight;
        if (lp.height > 0) childHeight = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
        else childHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        child.measure(childWidth, childHeight);
        return child;
    }

    private int measureHeightOfChildren(int widthMeasureSpec, int maxHeight) {
        int height = mListPadding.top + mListPadding.bottom;
        int count = mAdapter == null ? 0 : mAdapter.getCount();
        for (int i = 0; i < count; i++) {
            View child = obtainAndMeasure(i, widthMeasureSpec);
            mRecycler.addScrapView(child);
            height += child.getMeasuredHeight();
            if (i < count - 1) height += childGap();
            if (height >= maxHeight) return maxHeight;
        }
        return height;
    }

    @Override
    int childGap() { return mDividerHeight > 0 ? mDividerHeight : 0; }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawDividers(canvas);
        super.dispatchDraw(canvas);
    }

    private void drawDividers(Canvas canvas) {
        Drawable overscrollHeader = mOverScrollHeader;
        Drawable overscrollFooter = mOverScrollFooter;
        boolean drawDividers = mDivider != null && mDividerHeight > 0;
        if (!drawDividers && overscrollHeader == null && overscrollFooter == null) return;
        int count = getChildCount();
        if (count == 0 && overscrollHeader == null && overscrollFooter == null) return;
        mTempRect.left = getPaddingLeft();
        mTempRect.right = getWidth() - getPaddingRight();
        int listBottom = getHeight() - mListPadding.bottom;
        int headerCount = getHeaderViewsCount();
        int footerLimit = mItemCount - getFooterViewsCount();
        if (overscrollHeader != null && getScrollY() < 0) {
            mTempRect.bottom = 0;
            mTempRect.top = getScrollY();
            overscrollHeader.setBounds(mTempRect);
            overscrollHeader.draw(canvas);
        }
        if (drawDividers) {
            for (int i = 0; i < count; i++) {
                int itemIndex = mFirstPosition + i;
                boolean isHeader = itemIndex < headerCount;
                boolean isFooter = itemIndex >= footerLimit;
                if ((isHeader && !mHeaderDividersEnabled) || (isFooter && !mFooterDividersEnabled)) continue;
                View child = getChildAt(i);
                int bottom = child.getBottom();
                boolean last = i == count - 1;
                if (bottom < listBottom && !(overscrollFooter != null && last)) {
                    mTempRect.top = bottom;
                    mTempRect.bottom = bottom + mDividerHeight;
                    mDivider.setBounds(mTempRect);
                    mDivider.draw(canvas);
                }
            }
        }
        if (overscrollFooter != null && count > 0 && getScrollY() > 0) {
            mTempRect.top = listBottom;
            mTempRect.bottom = listBottom + getScrollY();
            overscrollFooter.setBounds(mTempRect);
            overscrollFooter.draw(canvas);
        }
    }

    public Drawable getDivider() { return mDivider; }

    public void setDivider(Drawable divider) {
        if (divider != null) mDividerHeight = divider.getIntrinsicHeight();
        else mDividerHeight = 0;
        mDivider = divider;
        requestLayout();
        invalidate();
    }

    public int getDividerHeight() { return mDividerHeight; }

    public void setDividerHeight(int height) {
        mDividerHeight = height;
        requestLayout();
        invalidate();
    }

    public void setHeaderDividersEnabled(boolean headerDividersEnabled) {
        mHeaderDividersEnabled = headerDividersEnabled;
        invalidate();
    }

    public boolean areHeaderDividersEnabled() { return mHeaderDividersEnabled; }

    public void setFooterDividersEnabled(boolean footerDividersEnabled) {
        mFooterDividersEnabled = footerDividersEnabled;
        invalidate();
    }

    public boolean areFooterDividersEnabled() { return mFooterDividersEnabled; }

    public void setOverscrollHeader(Drawable header) {
        mOverScrollHeader = header;
        if (getScrollY() < 0) invalidate();
    }

    public Drawable getOverscrollHeader() { return mOverScrollHeader; }

    public void setOverscrollFooter(Drawable footer) {
        mOverScrollFooter = footer;
        invalidate();
    }

    public Drawable getOverscrollFooter() { return mOverScrollFooter; }

    public void setItemsCanFocus(boolean itemsCanFocus) {
        mItemsCanFocus = itemsCanFocus;
        if (!itemsCanFocus) setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        else setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    }

    public boolean getItemsCanFocus() { return mItemsCanFocus; }

    @Override
    public boolean isOpaque() {
        if ((mCacheColorHint >>> 24) == 0xFF) return true;
        return super.isOpaque();
    }

    public void setSelectionAfterHeaderView() { setSelection(getHeaderViewsCount()); }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
        int listTop = mListPadding.top;
        int listBottom = getHeight() - mListPadding.bottom;
        int childTop = child.getTop();
        int childBottom = child.getBottom();
        int scroll = 0;
        if (childBottom > listBottom) scroll = childBottom - listBottom;
        if (childTop + scroll < listTop) scroll = childTop - listTop;
        if (scroll == 0) return false;
        if (immediate) scrollListBy(scroll);
        else smoothScrollBy(scroll, 200);
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) { return commonKey(keyCode, 1, event); }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        return commonKey(keyCode, repeatCount, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (event.isTracking() && !event.isCanceled()
                && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (mSelectedPosition >= 0 && mAdapter != null && mSelectedPosition < mAdapter.getCount()) {
                View child = getChildAt(mSelectedPosition - mFirstPosition);
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
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    handled = arrowScroll(FOCUS_DOWN);
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    handled = arrowScroll(FOCUS_UP);
                    break;
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    pageScroll(FOCUS_DOWN);
                    handled = true;
                    break;
                case KeyEvent.KEYCODE_PAGE_UP:
                    pageScroll(FOCUS_UP);
                    handled = true;
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
                    // AOSP keyPressed(): track the confirm key so onKeyUp clicks the selected row.
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

    private boolean arrowScroll(int direction) {
        boolean down = direction == FOCUS_DOWN;
        int pos = mSelectedPosition;
        if (pos == INVALID_POSITION) pos = down ? mFirstPosition : mFirstPosition + getChildCount() - 1;
        else pos = lookForSelectablePosition(down ? pos + 1 : pos - 1, down);
        if (pos == INVALID_POSITION) return false;
        setSelectionFromTop(pos, 0);
        return true;
    }

    private void pageScroll(int direction) {
        int page = Math.max(1, getHeight() - mListPadding.top - mListPadding.bottom);
        scrollListBy(direction == FOCUS_DOWN ? page : -page);
    }

    @Override
    public CharSequence getAccessibilityClassName() { return ListView.class.getName(); }

    @Override
    public void onInitializeAccessibilityNodeInfoForItem(View view, int position, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfoForItem(view, position, info);
    }

    /** @deprecated Use {@link #getCheckedItemIds()}. */
    @Deprecated
    public long[] getCheckItemIds() { return getCheckedItemIds(); }
}
