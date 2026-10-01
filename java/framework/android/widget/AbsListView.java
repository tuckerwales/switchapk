package android.widget;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A scrolling list of adapter rows (AOSP AbsListView).
 * Only the visible rows are children. A drag moves them with the finger and a fling continues
 * on {@link OverScroller}. Rows that leave the screen go back to the recycler.
 */
public abstract class AbsListView extends AdapterView<ListAdapter> implements TextWatcher,
        ViewTreeObserver.OnGlobalLayoutListener, Filter.FilterListener,
        ViewTreeObserver.OnTouchModeChangeListener {
    public static final int CHOICE_MODE_NONE = 0;
    public static final int CHOICE_MODE_SINGLE = 1;
    public static final int CHOICE_MODE_MULTIPLE = 2;
    public static final int CHOICE_MODE_MULTIPLE_MODAL = 3;
    public static final int TRANSCRIPT_MODE_DISABLED = 0;
    public static final int TRANSCRIPT_MODE_NORMAL = 1;
    public static final int TRANSCRIPT_MODE_ALWAYS_SCROLL = 2;

    static final int LAYOUT_NORMAL = 0;
    static final int LAYOUT_FORCE_TOP = 1;
    static final int LAYOUT_FORCE_BOTTOM = 3;
    static final int LAYOUT_SPECIFIC = 4;

    private static final int TOUCH_MODE_REST = -1;
    private static final int TOUCH_MODE_DOWN = 0;
    private static final int TOUCH_MODE_SCROLL = 3;
    private static final int TOUCH_MODE_FLING = 4;
    private static final int INVALID_POINTER = -1;

    private static final int[] LIST_ATTRS = {
        android.R.attr.listSelector,
        android.R.attr.drawSelectorOnTop,
        android.R.attr.stackFromBottom,
        android.R.attr.scrollingCache,
        android.R.attr.textFilterEnabled,
        android.R.attr.transcriptMode,
        android.R.attr.cacheColorHint,
        android.R.attr.smoothScrollbar,
        android.R.attr.choiceMode,
        android.R.attr.fastScrollEnabled,
    };

    ListAdapter mAdapter;
    final RecycleBin mRecycler = new RecycleBin();
    final Rect mListPadding = new Rect();
    int mLayoutMode = LAYOUT_NORMAL;
    int mSpecificPosition;
    int mWidthMeasureSpec;
    boolean mStackFromBottom;
    int mTouchMode = TOUCH_MODE_REST;
    int mTouchSlop;
    int mMinimumVelocity;
    int mMaximumVelocity;
    int mLastMotionY;
    int mActivePointerId = INVALID_POINTER;
    int mMotionPosition = INVALID_POSITION;
    int mLastFlingY;
    float mVelocityScale = 1f;
    VelocityTracker mVelocityTracker;
    OverScroller mScroller;
    EdgeEffect mEdgeGlowTop;
    EdgeEffect mEdgeGlowBottom;
    FlingRunnable mFlingRunnable;
    DataSetObserverAdapter mDataSetObserver;

    int mChoiceMode = CHOICE_MODE_NONE;
    SparseBooleanArray mCheckStates;
    HashMap<Long, Boolean> mCheckedIdStates;
    int mCheckedItemCount;
    OnScrollListener mOnScrollListener;
    int mLastScrollState = OnScrollListener.SCROLL_STATE_IDLE;
    boolean mScrollingCacheEnabled = true;
    boolean mTextFilterEnabled;
    boolean mFastScrollEnabled;
    boolean mFastScrollAlwaysVisible;
    int mFastScrollStyle;
    boolean mSmoothScrollbarEnabled = true;
    int mTranscriptMode = TRANSCRIPT_MODE_DISABLED;
    int mCacheColorHint;
    Drawable mSelector;
    final Rect mSelectorRect = new Rect();
    int mSelectorPosition = INVALID_POSITION;
    boolean mDrawSelectorOnTop;
    RecyclerListener mRecyclerListener;
    MultiChoiceModeListener mMultiChoiceModeListener;
    String mTextFilter;
    boolean mSelectedChildViewEnabled = true;
    boolean mDeferNotifyDataSetChanged;
    private boolean mGlowing;

    private final Runnable mPendingCheckForLongPress = new Runnable() {
        public void run() { onLongPress(); }
    };

    public AbsListView(Context context) { this(context, null); }

    public AbsListView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.absListViewStyle);
    }

    public AbsListView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AbsListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initAbsListView();
        android.content.res.TypedArray a = context.obtainStyledAttributes(
                attrs, LIST_ATTRS, defStyleAttr, defStyleRes);
        Drawable selector = a.getDrawable(0);
        if (selector != null) setSelector(selector);
        mDrawSelectorOnTop = a.getBoolean(1, false);
        setStackFromBottom(a.getBoolean(2, false));
        mScrollingCacheEnabled = a.getBoolean(3, true);
        mTextFilterEnabled = a.getBoolean(4, false);
        mTranscriptMode = a.getInt(5, TRANSCRIPT_MODE_DISABLED);
        mCacheColorHint = a.getColor(6, 0);
        mSmoothScrollbarEnabled = a.getBoolean(7, true);
        setChoiceMode(a.getInt(8, CHOICE_MODE_NONE));
        mFastScrollEnabled = a.getBoolean(9, false);
        a.recycle();
    }

    private void initAbsListView() {
        setFocusable(true);
        setClickable(true);
        setWillNotDraw(false);
        mScroller = new OverScroller(getContext());
        ViewConfiguration vc = ViewConfiguration.get(getContext());
        mTouchSlop = vc.getScaledTouchSlop();
        mMinimumVelocity = vc.getScaledMinimumFlingVelocity();
        mMaximumVelocity = vc.getScaledMaximumFlingVelocity();
        mEdgeGlowTop = new EdgeEffect(getContext());
        mEdgeGlowBottom = new EdgeEffect(getContext());
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    }

    // ---------------------------------------------------------------- adapter

    @Override
    public void setAdapter(ListAdapter adapter) {
        if (mAdapter != null && mDataSetObserver != null) {
            mAdapter.unregisterDataSetObserver(mDataSetObserver);
            mDataSetObserver = null;
        }
        mAdapter = adapter;
        mRecycler.clear();
        mCheckStates = null;
        mCheckedIdStates = null;
        mCheckedItemCount = 0;
        resetList();
        if (mAdapter != null) {
            mDataSetObserver = new DataSetObserverAdapter();
            mAdapter.registerDataSetObserver(mDataSetObserver);
            mItemCount = mAdapter.getCount();
            mRecycler.setViewTypeCount(mAdapter.getViewTypeCount());
            if (mChoiceMode != CHOICE_MODE_NONE) ensureCheckedState();
            mSelectedPosition = INVALID_POSITION;
            mSelectedRowId = INVALID_ROW_ID;
            if (mStackFromBottom) mLayoutMode = LAYOUT_FORCE_BOTTOM;
        } else {
            mItemCount = 0;
            mSelectedPosition = INVALID_POSITION;
            mSelectedRowId = INVALID_ROW_ID;
        }
        checkFocus();
        requestLayout();
    }

    @Override
    public View getSelectedView() {
        if (mItemCount > 0 && mSelectedPosition >= 0) return getChildAt(mSelectedPosition - mFirstPosition);
        return null;
    }

    public void setSelectionFromTop(int position, int y) {
        if (mAdapter == null) return;
        if (mAdapter.getCount() == 0) {
            mSelectedPosition = INVALID_POSITION;
            return;
        }
        position = Math.max(0, Math.min(position, mAdapter.getCount() - 1));
        mSpecificPosition = position;
        mSpecificTop = y;
        mLayoutMode = LAYOUT_SPECIFIC;
        mSelectedPosition = position;
        mSelectedRowId = mAdapter.getItemId(position);
        requestLayout();
    }

    /**
     * framework-internal. Selects {@code selected} and lays out again with the row of
     * {@code anchor} at {@code y} below the top padding (GridView key navigation).
     */
    void selectWithAnchor(int selected, int anchor, int y) {
        if (mAdapter == null || mAdapter.getCount() == 0) return;
        mSpecificPosition = anchor;
        mSpecificTop = y;
        mLayoutMode = LAYOUT_SPECIFIC;
        mSelectedPosition = selected;
        mSelectedRowId = mAdapter.getItemId(selected);
        requestLayout();
    }

    void resetList() {
        removeAllViewsInLayout();
        mFirstPosition = 0;
        mDataChanged = false;
        mSelectorPosition = INVALID_POSITION;
        mLayoutMode = LAYOUT_NORMAL;
        invalidate();
    }

    // ---------------------------------------------------------------- choice

    public int getCheckedItemCount() { return mCheckedItemCount; }

    public boolean isItemChecked(int position) {
        if (mChoiceMode != CHOICE_MODE_NONE && mCheckStates != null) return mCheckStates.get(position);
        return false;
    }

    public int getCheckedItemPosition() {
        if (mChoiceMode == CHOICE_MODE_SINGLE && mCheckStates != null && mCheckStates.size() == 1) {
            return mCheckStates.keyAt(0);
        }
        return INVALID_POSITION;
    }

    public SparseBooleanArray getCheckedItemPositions() {
        if (mChoiceMode != CHOICE_MODE_NONE) return mCheckStates;
        return null;
    }

    public long[] getCheckedItemIds() {
        if (mAdapter == null || !mAdapter.hasStableIds() || mCheckStates == null) return new long[0];
        int n = mCheckStates.size();
        long[] ids = new long[n];
        int out = 0;
        for (int i = 0; i < n; i++) {
            if (mCheckStates.valueAt(i)) ids[out++] = mAdapter.getItemId(mCheckStates.keyAt(i));
        }
        if (out == n) return ids;
        long[] trimmed = new long[out];
        System.arraycopy(ids, 0, trimmed, 0, out);
        return trimmed;
    }

    public void clearChoices() {
        if (mCheckStates != null) mCheckStates.clear();
        if (mCheckedIdStates != null) mCheckedIdStates.clear();
        mCheckedItemCount = 0;
        updateOnScreenCheckedViews();
    }

    public void setItemChecked(int position, boolean value) {
        if (mChoiceMode == CHOICE_MODE_NONE) return;
        ensureCheckedState();
        if (mChoiceMode == CHOICE_MODE_MULTIPLE || mChoiceMode == CHOICE_MODE_MULTIPLE_MODAL) {
            boolean old = mCheckStates.get(position);
            mCheckStates.put(position, value);
            if (mCheckedIdStates != null && mAdapter != null && mAdapter.hasStableIds()) {
                if (value) mCheckedIdStates.put(mAdapter.getItemId(position), Boolean.TRUE);
                else mCheckedIdStates.remove(mAdapter.getItemId(position));
            }
            if (old != value) mCheckedItemCount += value ? 1 : -1;
        } else {
            if (value || isItemChecked(position)) {
                mCheckStates.clear();
                if (mCheckedIdStates != null) mCheckedIdStates.clear();
                if (value) {
                    mCheckStates.put(position, true);
                    if (mCheckedIdStates != null && mAdapter != null && mAdapter.hasStableIds()) {
                        mCheckedIdStates.put(mAdapter.getItemId(position), Boolean.TRUE);
                    }
                    mCheckedItemCount = 1;
                } else {
                    mCheckedItemCount = 0;
                }
            }
        }
        updateOnScreenCheckedViews();
    }

    private void ensureCheckedState() {
        if (mCheckStates == null) mCheckStates = new SparseBooleanArray();
        if (mCheckedIdStates == null && mAdapter != null && mAdapter.hasStableIds()) {
            mCheckedIdStates = new HashMap<Long, Boolean>();
        }
    }

    private void updateOnScreenCheckedViews() {
        if (mChoiceMode == CHOICE_MODE_NONE || mCheckStates == null) return;
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            int position = mFirstPosition + i;
            if (child instanceof Checkable) ((Checkable) child).setChecked(mCheckStates.get(position));
        }
        invalidate();
    }

    @Override
    public boolean performItemClick(View view, int position, long id) {
        boolean handled = false;
        if (mChoiceMode != CHOICE_MODE_NONE) {
            handled = true;
            boolean checked;
            if (mChoiceMode == CHOICE_MODE_SINGLE) checked = true;
            else checked = !isItemChecked(position);
            setItemChecked(position, checked);
        }
        mSelectedPosition = position;
        mSelectedRowId = id;
        return handled | super.performItemClick(view, position, id);
    }

    public int getChoiceMode() { return mChoiceMode; }

    public void setChoiceMode(int choiceMode) {
        mChoiceMode = choiceMode;
        if (mChoiceMode != CHOICE_MODE_NONE) ensureCheckedState();
    }

    public void setMultiChoiceModeListener(MultiChoiceModeListener listener) {
        mMultiChoiceModeListener = listener;
    }

    // ---------------------------------------------------------------- scroll properties

    public void setFastScrollEnabled(boolean enabled) { mFastScrollEnabled = enabled; }

    public void setFastScrollStyle(int styleResId) { mFastScrollStyle = styleResId; }

    public void setFastScrollAlwaysVisible(boolean alwaysShow) { mFastScrollAlwaysVisible = alwaysShow; }

    public boolean isFastScrollAlwaysVisible() { return mFastScrollAlwaysVisible; }

    public boolean isFastScrollEnabled() { return mFastScrollEnabled; }

    public void setSmoothScrollbarEnabled(boolean enabled) { mSmoothScrollbarEnabled = enabled; }

    public boolean isSmoothScrollbarEnabled() { return mSmoothScrollbarEnabled; }

    public void setOnScrollListener(OnScrollListener l) { mOnScrollListener = l; }

    @Override
    public CharSequence getAccessibilityClassName() { return AbsListView.class.getName(); }

    public boolean isScrollingCacheEnabled() { return mScrollingCacheEnabled; }

    public void setScrollingCacheEnabled(boolean enabled) { mScrollingCacheEnabled = enabled; }

    public void setTextFilterEnabled(boolean textFilterEnabled) { mTextFilterEnabled = textFilterEnabled; }

    public boolean isTextFilterEnabled() { return mTextFilterEnabled; }

    public boolean isStackFromBottom() { return mStackFromBottom; }

    public void setStackFromBottom(boolean stackFromBottom) {
        if (mStackFromBottom != stackFromBottom) {
            mStackFromBottom = stackFromBottom;
            mLayoutMode = stackFromBottom ? LAYOUT_FORCE_BOTTOM : LAYOUT_FORCE_TOP;
            requestLayout();
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.selectedPosition = mSelectedPosition;
        ss.firstPosition = mFirstPosition;
        ss.top = getChildCount() > 0 ? getChildAt(0).getTop() - mListPadding.top : 0;
        ss.filter = mTextFilter;
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mSelectedPosition = ss.selectedPosition;
        mSpecificPosition = ss.firstPosition;
        mSpecificTop = ss.top;
        mTextFilter = ss.filter;
        mLayoutMode = LAYOUT_SPECIFIC;
        requestLayout();
    }

    public void setFilterText(String filterText) {
        if (!mTextFilterEnabled || filterText == null || filterText.length() == 0) return;
        mTextFilter = filterText;
        if (mAdapter instanceof Filterable) {
            Filter filter = ((Filterable) mAdapter).getFilter();
            if (filter != null) filter.filter(filterText, this);
        }
    }

    public CharSequence getTextFilter() { return mTextFilter; }

    public int getListPaddingTop() { return mListPadding.top; }

    public int getListPaddingBottom() { return mListPadding.bottom; }

    public int getListPaddingLeft() { return mListPadding.left; }

    public int getListPaddingRight() { return mListPadding.right; }

    public void onInitializeAccessibilityNodeInfoForItem(View view, int position, AccessibilityNodeInfo info) {
        if (view != null && info != null) view.onInitializeAccessibilityNodeInfo(info);
    }

    public boolean isSelectedChildViewEnabled() { return mSelectedChildViewEnabled; }

    public void setSelectedChildViewEnabled(boolean enabled) { mSelectedChildViewEnabled = enabled; }

    public void setDrawSelectorOnTop(boolean onTop) { mDrawSelectorOnTop = onTop; }

    public boolean isDrawSelectorOnTop() { return mDrawSelectorOnTop; }

    public void setSelector(int resID) { setSelector(getContext().getDrawable(resID)); }

    public void setSelector(Drawable sel) {
        if (mSelector != null) {
            mSelector.setCallback(null);
            unscheduleDrawable(mSelector);
        }
        mSelector = sel;
        if (mSelector != null) {
            mSelector.setCallback(this);
            if (mSelector.isStateful()) mSelector.setState(getDrawableState());
        }
        invalidate();
    }

    public Drawable getSelector() { return mSelector; }

    public void setScrollIndicators(View up, View down) {}

    public void setFriction(float friction) {
        if (mScroller != null) mScroller.setFriction(friction);
    }

    public void setVelocityScale(float scale) { mVelocityScale = scale; }

    public void setTranscriptMode(int mode) { mTranscriptMode = mode; }

    public int getTranscriptMode() { return mTranscriptMode; }

    public void setCacheColorHint(int color) {
        if (color != mCacheColorHint) {
            mCacheColorHint = color;
            invalidate();
        }
    }

    public int getCacheColorHint() { return mCacheColorHint; }

    public void setRecyclerListener(RecyclerListener listener) { mRecyclerListener = listener; }

    public void setEdgeEffectColor(int color) {
        setTopEdgeEffectColor(color);
        setBottomEdgeEffectColor(color);
    }

    public void setTopEdgeEffectColor(int color) { mEdgeGlowTop.setColor(color); }

    public void setBottomEdgeEffectColor(int color) { mEdgeGlowBottom.setColor(color); }

    public int getTopEdgeEffectColor() { return mEdgeGlowTop.getColor(); }

    public int getBottomEdgeEffectColor() { return mEdgeGlowBottom.getColor(); }

    public void setRemoteViewsAdapter(Intent intent) {}

    public void deferNotifyDataSetChanged() { mDeferNotifyDataSetChanged = true; }

    public boolean onRemoteAdapterConnected() {
        if (mDeferNotifyDataSetChanged) {
            mDeferNotifyDataSetChanged = false;
            requestLayout();
            return true;
        }
        return false;
    }

    public void onRemoteAdapterDisconnected() {}

    public void reclaimViews(List<View> views) {
        if (views == null) return;
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            views.add(child);
            if (mRecyclerListener != null) mRecyclerListener.onMovedToScrapHeap(child);
        }
        mRecycler.reclaimScrapViews(views);
        removeAllViewsInLayout();
    }

    public void invalidateViews() {
        mDataChanged = true;
        requestLayout();
        invalidate();
    }

    protected void handleDataChanged() {
        int count = mAdapter == null ? 0 : mAdapter.getCount();
        if (count == 0) {
            mSelectedPosition = INVALID_POSITION;
            mSelectedRowId = INVALID_ROW_ID;
            return;
        }
        if (mSelectedPosition >= count) mSelectedPosition = count - 1;
    }

    protected boolean isInFilterMode() { return mTextFilter != null && mTextFilter.length() > 0; }

    public void clearTextFilter() {
        if (!hasTextFilter()) return;
        mTextFilter = null;
        if (mAdapter instanceof Filterable) {
            Filter filter = ((Filterable) mAdapter).getFilter();
            if (filter != null) filter.filter(null, this);
        }
    }

    public boolean hasTextFilter() { return mTextFilter != null && mTextFilter.length() > 0; }

    // ---------------------------------------------------------------- layout

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mListPadding.set(getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom());
        int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? getSuggestedMinimumWidth() : MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? getSuggestedMinimumHeight() : MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        mWidthMeasureSpec = widthMeasureSpec;
        if (mAdapter != null) mItemCount = mAdapter.getCount();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mInLayout = true;
        mListPadding.set(getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom());
        if (changed) {
            int count = getChildCount();
            for (int i = 0; i < count; i++) getChildAt(i).forceLayout();
        }
        layoutChildren();
        mInLayout = false;
    }

    @Override
    public void requestLayout() {
        if (!mBlockLayoutRequests && !mInLayout) super.requestLayout();
    }

    protected void layoutChildren() {
        if (mBlockLayoutRequests) return;
        mBlockLayoutRequests = true;
        try {
            if (mAdapter == null) {
                resetList();
                return;
            }
            mItemCount = mAdapter.getCount();
            int childrenTop = mListPadding.top;
            int childrenBottom = getHeight() - mListPadding.bottom;
            int childCount = getChildCount();
            int oldFirst = mFirstPosition;
            int newFirst = oldFirst;
            int newTop = childCount > 0 ? getChildAt(0).getTop() : childrenTop;
            boolean forceBottom = mLayoutMode == LAYOUT_FORCE_BOTTOM;
            if (mLayoutMode == LAYOUT_SPECIFIC) {
                newFirst = Math.max(0, Math.min(mSpecificPosition, Math.max(0, mItemCount - 1)));
                newTop = childrenTop + mSpecificTop;
            } else if (mLayoutMode == LAYOUT_FORCE_TOP) {
                newFirst = 0;
                newTop = childrenTop;
            } else if (mDataChanged && newFirst >= mItemCount) {
                newFirst = Math.max(0, mItemCount - 1);
                newTop = childrenTop;
            }
            // Rows always start at a multiple of the row size.
            newFirst -= newFirst % itemsPerRow();
            for (int i = 0; i < childCount; i++) mRecycler.addScrapView(getChildAt(i));
            detachAllViewsFromParent();
            if (mItemCount == 0) {
                mFirstPosition = 0;
            } else if (forceBottom) {
                fillUp(mItemCount - 1, childrenBottom);
                correctEnds();
            } else {
                mFirstPosition = newFirst;
                fillDown(mFirstPosition, newTop);
                fillGap(false);
                correctEnds();
                fillGap(true);
                fillGap(false);
                correctEnds();
            }
            mLayoutMode = LAYOUT_NORMAL;
            mDataChanged = false;
            positionSelectorIfNeeded();
            positionKeyboardSelector();
            invokeOnItemScrollListener();
            checkSelectionChanged();
        } finally {
            mBlockLayoutRequests = false;
        }
    }

    /** Gap between rows. ListView uses the divider height, GridView the vertical spacing. */
    int childGap() { return 0; }

    /** Items laid out side by side in one row (GridView: its column count). Rows start at multiples of it. */
    int itemsPerRow() { return 1; }

    /** Width measure spec for an item in the given column. */
    int childWidthMeasureSpec(LayoutParams p, int column) {
        return ViewGroup.getChildMeasureSpec(mWidthMeasureSpec, mListPadding.left + mListPadding.right, p.width);
    }

    /** Left edge for a measured item in the given column. */
    int childLeft(int column, int measuredWidth) { return mListPadding.left; }

    private View fillDown(int pos, int nextTop) {
        final int n = itemsPerRow();
        int end = getHeight() - mListPadding.bottom;
        View child = null;
        while (nextTop < end && pos < mItemCount) {
            int before = nextTop;
            final int rowEnd = Math.min(pos + n, mItemCount);
            int bottom = nextTop;
            for (int p = pos; p < rowEnd; p++) {
                child = makeAndAddView(p, nextTop, true, p - pos, -1);
                bottom = Math.max(bottom, child.getBottom());
            }
            nextTop = bottom + childGap();
            if (nextTop <= before) break;
            pos = rowEnd;
        }
        return child;
    }

    private View fillUp(int pos, int nextBottom) {
        final int n = itemsPerRow();
        int end = mListPadding.top;
        View child = null;
        while (nextBottom > end && pos >= 0) {
            int before = nextBottom;
            final int rowStart = pos - pos % n;
            int top = nextBottom;
            for (int p = rowStart; p <= pos; p++) {
                // The row goes in front of the existing children, in position order.
                child = makeAndAddView(p, nextBottom, false, p - rowStart, p - rowStart);
                top = Math.min(top, child.getTop());
            }
            nextBottom = top - childGap();
            pos = rowStart - 1;
            if (nextBottom >= before) break;
        }
        mFirstPosition = pos + 1;
        return child;
    }

    private void fillGap(boolean down) {
        int count = getChildCount();
        if (down) {
            int start = count > 0 ? getChildAt(count - 1).getBottom() + childGap() : mListPadding.top;
            fillDown(mFirstPosition + count, start);
        } else if (count > 0 && mFirstPosition > 0) {
            int start = getChildAt(0).getTop() - childGap();
            fillUp(mFirstPosition - 1, start);
        }
    }

    private void correctEnds() {
        int count = getChildCount();
        if (count == 0) return;
        int listTop = mListPadding.top;
        int listBottom = getHeight() - mListPadding.bottom;
        View first = getChildAt(0);
        View last = getChildAt(count - 1);
        boolean forceBottom = mLayoutMode == LAYOUT_FORCE_BOTTOM;
        // Close a gap above position 0. A negative top means that row is
        // partly scrolled off, which a drag is allowed to do.
        if (mFirstPosition == 0 && first.getTop() > listTop && !forceBottom) {
            offsetChildrenTopAndBottom(listTop - first.getTop());
            return;
        }
        if (mFirstPosition + count >= mItemCount && last.getBottom() < listBottom) {
            if (mFirstPosition == 0 && !forceBottom) return;
            int move = listBottom - last.getBottom();
            if (move != 0) offsetChildrenTopAndBottom(move);
        }
    }

    private View makeAndAddView(int position, int y, boolean flowDown, int column, int where) {
        View scrap = null;
        if (!mDataChanged) scrap = mRecycler.getScrapView(position);
        View child = mAdapter.getView(position, scrap, this);
        if (child == null) child = new View(getContext());
        setupChild(child, position, y, flowDown, column, where);
        return child;
    }

    private void setupChild(View child, int position, int y, boolean flowDown, int column, int where) {
        LayoutParams p;
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp == null) p = (LayoutParams) generateDefaultLayoutParams();
        else if (lp instanceof LayoutParams) p = (LayoutParams) lp;
        else p = (LayoutParams) generateLayoutParams(lp);
        p.viewType = mAdapter.getItemViewType(position);
        if (child.getParent() == this) {
            // Already attached from a previous fill in this pass. Relayout only.
        } else {
            if (child.getParent() instanceof ViewGroup) ((ViewGroup) child.getParent()).removeView(child);
            addViewInLayout(child, flowDown ? -1 : where, p, true);
        }
        if (mChoiceMode != CHOICE_MODE_NONE && mCheckStates != null && child instanceof Checkable) {
            ((Checkable) child).setChecked(mCheckStates.get(position));
        }
        boolean enable = mAdapter.isEnabled(position);
        if (!mSelectedChildViewEnabled && position == mSelectedPosition) enable = false;
        if (child.isEnabled() != enable) child.setEnabled(enable);
        int childWidthSpec = childWidthMeasureSpec(p, column);
        int childHeightSpec;
        if (p.height > 0) childHeightSpec = MeasureSpec.makeMeasureSpec(p.height, MeasureSpec.EXACTLY);
        else childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        child.measure(childWidthSpec, childHeightSpec);
        int w = child.getMeasuredWidth();
        int h = child.getMeasuredHeight();
        int childTop = flowDown ? y : y - h;
        int left = childLeft(column, w);
        child.layout(left, childTop, left + w, childTop + h);
    }

    private void offsetChildrenTopAndBottom(int offset) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) getChildAt(i).offsetTopAndBottom(offset);
    }

    /**
     * @param incrementalDeltaY added to each child's top. Positive moves rows down.
     * @return true when the list is against that end and cannot move any further
     */
    boolean trackMotionScroll(int deltaY, int incrementalDeltaY) {
        int childCount = getChildCount();
        if (childCount == 0) return true;
        int listTop = mListPadding.top;
        int listBottom = getHeight() - mListPadding.bottom;
        int height = Math.max(1, getHeight());
        if (incrementalDeltaY > height - 1) incrementalDeltaY = height - 1;
        else if (incrementalDeltaY < -(height - 1)) incrementalDeltaY = -(height - 1);
        int firstTop = getChildAt(0).getTop();
        int lastBottom = getChildAt(childCount - 1).getBottom();
        if (mFirstPosition == 0 && firstTop >= listTop && incrementalDeltaY >= 0) {
            return incrementalDeltaY != 0;
        }
        if (mFirstPosition + childCount >= mItemCount && lastBottom <= listBottom && incrementalDeltaY <= 0) {
            return incrementalDeltaY != 0;
        }
        boolean hitEdge = false;
        if (incrementalDeltaY > 0 && mFirstPosition == 0) {
            int room = listTop - firstTop;
            if (room <= 0) {
                hitEdge = true;
                incrementalDeltaY = 0;
            } else if (incrementalDeltaY > room) {
                incrementalDeltaY = room;
                hitEdge = true;
            }
        } else if (incrementalDeltaY < 0 && mFirstPosition + childCount >= mItemCount) {
            int room = lastBottom - listBottom;
            if (room <= 0) {
                hitEdge = true;
                incrementalDeltaY = 0;
            } else if (-incrementalDeltaY > room) {
                incrementalDeltaY = -room;
                hitEdge = true;
            }
        }
        if (incrementalDeltaY == 0) return hitEdge;
        boolean movingUp = incrementalDeltaY < 0;
        int start = 0;
        int count = 0;
        if (movingUp) {
            int threshold = listTop - incrementalDeltaY;
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child.getBottom() >= threshold) break;
                count++;
            }
        } else {
            int threshold = listBottom - incrementalDeltaY;
            for (int i = childCount - 1; i >= 0; i--) {
                View child = getChildAt(i);
                if (child.getTop() <= threshold) break;
                start = i;
                count++;
            }
        }
        final int n = itemsPerRow();
        if (n > 1 && count > 0) {
            // Only whole rows leave, so the first position stays at a row start.
            if (movingUp) {
                count -= count % n;
            } else {
                final int rem = (mFirstPosition + start) % n;
                if (rem != 0) {
                    start += n - rem;
                    count -= n - rem;
                }
            }
            if (count <= 0) count = 0;
        }
        for (int i = start; i < start + count; i++) mRecycler.addScrapView(getChildAt(i));
        mBlockLayoutRequests = true;
        if (count > 0) detachViewsFromParent(start, count);
        if (movingUp) mFirstPosition += count;
        offsetChildrenTopAndBottom(incrementalDeltaY);
        fillGap(movingUp);
        correctEnds();
        // Pinning the last row down opens a gap above the first child.
        if (getChildCount() > 0 && mFirstPosition > 0
                && getChildAt(0).getTop() > mListPadding.top) {
            fillGap(false);
            correctEnds();
        }
        mBlockLayoutRequests = false;
        invokeOnItemScrollListener();
        awakenScrollBars();
        return hitEdge;
    }

    public void scrollListBy(int y) {
        int remaining = y;
        int guard = 0;
        while (remaining != 0 && guard++ < 10000) {
            int max = Math.max(1, getHeight() - 1);
            int step = remaining;
            if (step > max) step = max;
            else if (step < -max) step = -max;
            boolean atEdge = trackMotionScroll(-step, -step);
            if (atEdge || step == 0) break;
            remaining -= step;
        }
        invalidate();
    }

    public boolean canScrollList(int direction) {
        int childCount = getChildCount();
        if (childCount == 0) return false;
        int firstTop = getChildAt(0).getTop();
        int lastBottom = getChildAt(childCount - 1).getBottom();
        if (direction > 0) {
            return lastBottom > getHeight() - mListPadding.bottom || mFirstPosition + childCount < mItemCount;
        }
        return firstTop < mListPadding.top || mFirstPosition > 0;
    }

    public void smoothScrollBy(int distance, int duration) {
        if (getChildCount() == 0 || distance == 0) {
            if (mFlingRunnable != null) mFlingRunnable.endFling();
            return;
        }
        if (mFlingRunnable == null) mFlingRunnable = new FlingRunnable();
        reportScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);
        mFlingRunnable.startScroll(distance, duration <= 0 ? 200 : duration);
    }

    public void smoothScrollToPosition(int position) {
        if (mAdapter == null || getChildCount() == 0) {
            setSelection(position);
            return;
        }
        position = Math.max(0, Math.min(position, Math.max(0, mItemCount - 1)));
        int first = mFirstPosition;
        int last = first + getChildCount() - 1;
        int row = Math.max(1, getChildAt(0).getHeight() + childGap());
        int distance;
        if (position <= first) {
            View child = getChildAt(0);
            distance = -(rowsBetween(position, first) * row) + (mListPadding.top - child.getTop());
        } else if (position >= last) {
            View child = getChildAt(getChildCount() - 1);
            int end = getHeight() - mListPadding.bottom;
            distance = rowsBetween(last, position) * row + (child.getBottom() - end);
        } else {
            View child = getChildAt(position - first);
            distance = child.getTop() - mListPadding.top;
        }
        if (distance == 0) setSelectionFromTop(position, 0);
        else smoothScrollBy(distance, Math.max(100, Math.min(400, Math.abs(distance))));
    }

    /** Rows from the one holding position {@code from} to the one holding {@code to} (to >= from). */
    private int rowsBetween(int from, int to) {
        final int n = itemsPerRow();
        return to / n - from / n;
    }

    public void smoothScrollToPosition(int position, int boundPosition) { smoothScrollToPosition(position); }

    public void smoothScrollToPositionFromTop(int position, int offset, int duration) {
        if (mAdapter == null) return;
        position = Math.max(0, Math.min(position, Math.max(0, mAdapter.getCount() - 1)));
        int currentTop;
        int index = position - mFirstPosition;
        if (index >= 0 && index < getChildCount()) currentTop = getChildAt(index).getTop();
        else {
            int row = getChildCount() > 0 ? Math.max(1, getChildAt(0).getHeight() + childGap()) : 1;
            if (position < mFirstPosition) {
                currentTop = (getChildCount() > 0 ? getChildAt(0).getTop() : 0) - rowsBetween(position, mFirstPosition) * row;
            } else {
                View last = getChildCount() > 0 ? getChildAt(getChildCount() - 1) : null;
                int lastPos = mFirstPosition + getChildCount() - 1;
                currentTop = (last == null ? 0 : last.getBottom()) + rowsBetween(lastPos, position) * row;
            }
        }
        smoothScrollBy(currentTop - (mListPadding.top + offset), duration <= 0 ? 200 : duration);
    }

    public void smoothScrollToPositionFromTop(int position, int offset) {
        smoothScrollToPositionFromTop(position, offset, 200);
    }

    /**
     * Fling. Positive {@code velocityY} moves the rows up, revealing items below.
     * A finger moving up has a negative tracker velocity, so the touch path passes the negation.
     */
    public void fling(int velocityY) {
        if (getChildCount() == 0) return;
        if (mFlingRunnable == null) mFlingRunnable = new FlingRunnable();
        reportScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);
        mFlingRunnable.start(velocityY);
    }

    public int pointToPosition(int x, int y) {
        int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (y >= child.getTop() && y <= child.getBottom() && x >= child.getLeft() && x <= child.getRight()) {
                return mFirstPosition + i;
            }
        }
        return INVALID_POSITION;
    }

    public long pointToRowId(int x, int y) {
        int position = pointToPosition(x, y);
        if (position >= 0 && mAdapter != null) return mAdapter.getItemId(position);
        return INVALID_ROW_ID;
    }

    // ---------------------------------------------------------------- touch

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && mTouchMode == TOUCH_MODE_FLING) return true;
        if (action == MotionEvent.ACTION_MOVE && mTouchMode == TOUCH_MODE_SCROLL) return true;
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                int y = (int) ev.getY();
                mActivePointerId = ev.getPointerId(0);
                mLastMotionY = y;
                mMotionPosition = pointToPosition((int) ev.getX(), y);
                if (mTouchMode == TOUCH_MODE_FLING) {
                    mTouchMode = TOUCH_MODE_SCROLL;
                    mLastMotionY = y;
                    return true;
                }
                mTouchMode = TOUCH_MODE_DOWN;
                return false;
            }
            case MotionEvent.ACTION_MOVE: {
                int index = ev.findPointerIndex(mActivePointerId);
                if (index < 0) break;
                int y = (int) ev.getY(index);
                if (Math.abs(y - mLastMotionY) > mTouchSlop) {
                    mTouchMode = TOUCH_MODE_SCROLL;
                    mLastMotionY = y;
                    clearPressedChildren();
                    reportScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
                    ViewParent parent = getParent();
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTouchMode = TOUCH_MODE_REST;
                mActivePointerId = INVALID_POINTER;
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mAdapter == null || mItemCount == 0) return false;
        initVelocityTrackerIfNotExists();
        MotionEvent vtev = MotionEvent.obtain(ev);
        int action = ev.getActionMasked();
        mVelocityTracker.addMovement(vtev);
        vtev.recycle();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                if (mTouchMode == TOUCH_MODE_FLING || (mFlingRunnable != null && !mScroller.isFinished())) {
                    if (mFlingRunnable != null) mFlingRunnable.endFling();
                    mTouchMode = TOUCH_MODE_SCROLL;
                } else {
                    mTouchMode = TOUCH_MODE_DOWN;
                    int index = 0;
                    mMotionPosition = pointToPosition((int) ev.getX(), (int) ev.getY());
                    if (mMotionPosition != INVALID_POSITION) {
                        View child = getChildAt(mMotionPosition - mFirstPosition);
                        if (child != null) {
                            child.setPressed(true);
                            positionSelector(mMotionPosition, child);
                        }
                    }
                    postDelayed(mPendingCheckForLongPress, ViewConfiguration.getLongPressTimeout());
                }
                mLastMotionY = (int) ev.getY();
                mActivePointerId = ev.getPointerId(0);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                int index = ev.findPointerIndex(mActivePointerId);
                if (index < 0) break;
                int y = (int) ev.getY(index);
                int delta = y - mLastMotionY;
                if (mTouchMode != TOUCH_MODE_SCROLL && Math.abs(delta) > mTouchSlop) {
                    removeCallbacks(mPendingCheckForLongPress);
                    mTouchMode = TOUCH_MODE_SCROLL;
                    if (delta > 0) delta -= mTouchSlop;
                    else delta += mTouchSlop;
                    clearPressedChildren();
                    reportScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
                    ViewParent parent = getParent();
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                }
                if (mTouchMode == TOUCH_MODE_SCROLL) {
                    mLastMotionY = y;
                    if (delta != 0) {
                        boolean atEdge = trackMotionScroll(delta, delta);
                        if (atEdge) pullEdge(delta, ev.getX(index));
                        else releaseEdges();
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                removeCallbacks(mPendingCheckForLongPress);
                if (mTouchMode == TOUCH_MODE_SCROLL) {
                    VelocityTracker tracker = mVelocityTracker;
                    tracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int velocity = (int) (tracker.getYVelocity(mActivePointerId) * mVelocityScale);
                    if (Math.abs(velocity) > mMinimumVelocity) fling(-velocity);
                    else reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                } else if (mTouchMode == TOUCH_MODE_DOWN) {
                    int position = mMotionPosition;
                    if (position == INVALID_POSITION) position = pointToPosition((int) ev.getX(), (int) ev.getY());
                    if (position != INVALID_POSITION && mAdapter != null && position < mAdapter.getCount()
                            && mAdapter.isEnabled(position)) {
                        View child = getChildAt(position - mFirstPosition);
                        performItemClick(child, position, mAdapter.getItemId(position));
                    }
                    reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                }
                clearPressedChildren();
                endTouch();
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(mPendingCheckForLongPress);
                clearPressedChildren();
                endTouch();
                reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = ev.getActionIndex();
                mLastMotionY = (int) ev.getY(index);
                mActivePointerId = ev.getPointerId(index);
                break;
            }
            default:
                break;
        }
        return true;
    }

    private void onLongPress() {
        if (mTouchMode != TOUCH_MODE_DOWN) return;
        int position = mMotionPosition;
        if (position == INVALID_POSITION || mAdapter == null) return;
        View child = getChildAt(position - mFirstPosition);
        if (child == null) return;
        if (mOnItemLongClickListener != null) {
            boolean handled = mOnItemLongClickListener.onItemLongClick(
                    this, child, position, mAdapter.getItemId(position));
            if (handled) {
                mTouchMode = TOUCH_MODE_REST;
                child.setPressed(false);
                setPressed(false);
            }
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        int index = ev.getActionIndex();
        if (ev.getPointerId(index) == mActivePointerId) {
            int newIndex = index == 0 ? 1 : 0;
            mLastMotionY = (int) ev.getY(newIndex);
            mActivePointerId = ev.getPointerId(newIndex);
            if (mVelocityTracker != null) mVelocityTracker.clear();
        }
    }

    private void endTouch() {
        // A fling started by this ACTION_UP keeps running.
        if (mTouchMode != TOUCH_MODE_FLING) mTouchMode = TOUCH_MODE_REST;
        mActivePointerId = INVALID_POINTER;
        recycleVelocityTracker();
        releaseEdges();
    }

    private void clearPressedChildren() {
        setPressed(false);
        int count = getChildCount();
        for (int i = 0; i < count; i++) getChildAt(i).setPressed(false);
        mSelectorPosition = INVALID_POSITION;
        invalidate();
    }

    private void pullEdge(int delta, float x) {
        if (getHeight() == 0) return;
        int mode = getOverScrollMode();
        boolean allow = mode == OVER_SCROLL_ALWAYS
                || (mode == OVER_SCROLL_IF_CONTENT_SCROLLS && mItemCount > 0 && canContentScroll());
        if (!allow) return;
        float displacement = getWidth() == 0 ? 0.5f : x / getWidth();
        if (delta > 0) {
            mEdgeGlowTop.onPull((float) delta / getHeight(), displacement);
            if (!mEdgeGlowBottom.isFinished()) mEdgeGlowBottom.onRelease();
        } else if (delta < 0) {
            mEdgeGlowBottom.onPull((float) -delta / getHeight(), 1f - displacement);
            if (!mEdgeGlowTop.isFinished()) mEdgeGlowTop.onRelease();
        }
        if (!mEdgeGlowTop.isFinished() || !mEdgeGlowBottom.isFinished()) {
            mGlowing = true;
            postInvalidateOnAnimation();
        }
    }

    private boolean canContentScroll() {
        return mItemCount > getChildCount() || (getChildCount() > 0
                && (getChildAt(0).getTop() < mListPadding.top
                || getChildAt(getChildCount() - 1).getBottom() > getHeight() - mListPadding.bottom));
    }

    private void releaseEdges() {
        mEdgeGlowTop.onRelease();
        mEdgeGlowBottom.onRelease();
        if (mGlowing) {
            mGlowing = false;
            postInvalidateOnAnimation();
        }
    }

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) mVelocityTracker = VelocityTracker.obtain();
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0
                && event.getAction() == MotionEvent.ACTION_SCROLL) {
            float vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (vscroll != 0) {
                int delta = (int) (vscroll * ViewConfiguration.get(getContext()).getScaledVerticalScrollFactor());
                if (delta != 0) {
                    scrollListBy(-delta);
                    return true;
                }
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        if (mScroller != null && !mScroller.isFinished()) {
            int oldX = mScrollX;
            int oldY = mScrollY;
            mScrollX = scrollX;
            mScrollY = scrollY;
            onScrollChanged(mScrollX, mScrollY, oldX, oldY);
        } else {
            super.scrollTo(scrollX, scrollY);
        }
    }

    @Override
    public boolean onStartNestedScroll(View child, View target, int axes) {
        return (axes & SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int axes) {
        super.onNestedScrollAccepted(child, target, axes);
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        if (dyUnconsumed != 0) scrollListBy(dyUnconsumed);
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        if (!consumed) {
            fling((int) velocityY);
            return true;
        }
        return false;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
        if (disallowIntercept) recycleVelocityTracker();
    }

    // ---------------------------------------------------------------- draw

    @Override
    protected void dispatchDraw(Canvas canvas) {
        boolean drawOnTop = mDrawSelectorOnTop;
        if (!drawOnTop) drawSelector(canvas);
        super.dispatchDraw(canvas);
        if (drawOnTop) drawSelector(canvas);
    }

    private void drawSelector(Canvas canvas) {
        if (mSelector == null || mSelectorPosition == INVALID_POSITION) return;
        View child = getChildAt(mSelectorPosition - mFirstPosition);
        if (child == null) return;
        positionSelector(mSelectorPosition, child);
        mSelector.draw(canvas);
    }

    private void positionSelector(int position, View sel) {
        mSelectorPosition = position;
        if (mSelector == null || sel == null) return;
        int left = sel.getLeft();
        int top = sel.getTop();
        int right = sel.getRight();
        int bottom = sel.getBottom();
        if (sel instanceof SelectionBoundsAdjuster) {
            mSelectorRect.set(left, top, right, bottom);
            ((SelectionBoundsAdjuster) sel).adjustListItemSelectionBounds(mSelectorRect);
            left = mSelectorRect.left;
            top = mSelectorRect.top;
            right = mSelectorRect.right;
            bottom = mSelectorRect.bottom;
        }
        mSelector.setBounds(left, top, right, bottom);
    }

    /** AOSP keeps the selector on the selected row while navigating with keys (out of touch mode). */
    private void positionKeyboardSelector() {
        if (isInTouchMode() || mSelectedPosition < 0) return;
        View sel = getChildAt(mSelectedPosition - mFirstPosition);
        if (sel != null) positionSelector(mSelectedPosition, sel);
    }

    /** framework-internal (hidden in AOSP). */
    void hideSelector() {
        mSelectorPosition = INVALID_POSITION;
        invalidate();
    }

    private void positionSelectorIfNeeded() {
        if (mSelectorPosition == INVALID_POSITION) return;
        View child = getChildAt(mSelectorPosition - mFirstPosition);
        if (child != null) positionSelector(mSelectorPosition, child);
        else mSelectorPosition = INVALID_POSITION;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        if (!mEdgeGlowTop.isFinished()) {
            int count = canvas.save();
            canvas.translate(getPaddingLeft(), getPaddingTop());
            mEdgeGlowTop.setSize(width - getPaddingLeft() - getPaddingRight(), height);
            if (mEdgeGlowTop.draw(canvas)) postInvalidateOnAnimation();
            canvas.restoreToCount(count);
        }
        if (!mEdgeGlowBottom.isFinished()) {
            int count = canvas.save();
            canvas.translate(-width + getPaddingRight(), getHeight());
            canvas.rotate(180, width, 0);
            mEdgeGlowBottom.setSize(width - getPaddingLeft() - getPaddingRight(), height);
            if (mEdgeGlowBottom.draw(canvas)) postInvalidateOnAnimation();
            canvas.restoreToCount(count);
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mSelector != null && mSelector.isStateful()) mSelector.setState(getDrawableState());
    }

    @Override
    public boolean verifyDrawable(Drawable dr) {
        return mSelector == dr || super.verifyDrawable(dr);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mSelector != null) mSelector.jumpToCurrentState();
    }

    @Override
    public void dispatchDrawableHotspotChanged(float x, float y) {
        super.dispatchDrawableHotspotChanged(x, y);
        if (mSelector != null) mSelector.setHotspot(x, y);
    }

    @Override
    protected int computeVerticalScrollExtent() {
        int count = getChildCount();
        if (count == 0) return 0;
        if (!mSmoothScrollbarEnabled) return count;
        int extent = count * 100;
        View first = getChildAt(0);
        int fh = first.getHeight();
        if (fh > 0) extent += (first.getTop() * 100) / fh;
        View last = getChildAt(count - 1);
        int lh = last.getHeight();
        if (lh > 0) extent -= ((last.getBottom() - getHeight()) * 100) / lh;
        return Math.max(extent, 0);
    }

    @Override
    protected int computeVerticalScrollOffset() {
        int first = mFirstPosition;
        int count = getChildCount();
        if (first < 0 || count == 0) return 0;
        if (!mSmoothScrollbarEnabled) return first;
        View view = getChildAt(0);
        int height = view.getHeight();
        if (height <= 0) return first;
        return Math.max(first * 100 - (view.getTop() * 100) / height, 0);
    }

    @Override
    protected int computeVerticalScrollRange() {
        if (!mSmoothScrollbarEnabled) return mItemCount;
        int result = Math.max(mItemCount * 100, 0);
        if (getChildCount() > 0) {
            View first = getChildAt(0);
            if (first.getTop() > mListPadding.top) result += 1;
        }
        return result;
    }

    @Override
    protected float getTopFadingEdgeStrength() {
        if (getChildCount() == 0) return 0;
        if (mFirstPosition > 0) return 1;
        int length = getVerticalFadingEdgeLength();
        if (length == 0) return 0;
        int top = getChildAt(0).getTop();
        if (top >= mListPadding.top) return 0;
        return Math.min(1f, (mListPadding.top - top) / (float) length);
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        int count = getChildCount();
        if (count == 0) return 0;
        if (mFirstPosition + count < mItemCount) return 1;
        int length = getVerticalFadingEdgeLength();
        if (length == 0) return 0;
        int bottom = getChildAt(count - 1).getBottom();
        int listBottom = getHeight() - mListPadding.bottom;
        if (bottom <= listBottom) return 0;
        return Math.min(1f, (bottom - listBottom) / (float) length);
    }

    // ---------------------------------------------------------------- keys, context, attach

    @Override
    protected ContextMenu.ContextMenuInfo getContextMenuInfo() { return mContextMenuInfo; }

    @Override
    public boolean showContextMenu() { return showContextMenuForChild(this); }

    @Override
    public boolean showContextMenu(float x, float y) {
        int position = pointToPosition((int) x, (int) y);
        if (position >= 0) {
            View child = getChildAt(position - mFirstPosition);
            long id = mAdapter == null ? INVALID_ROW_ID : mAdapter.getItemId(position);
            mContextMenuInfo = new AdapterContextMenuInfo(child, position, id);
        }
        return super.showContextMenu(x, y);
    }

    @Override
    public boolean showContextMenuForChild(View originalView) {
        int position = getPositionForView(originalView);
        if (position >= 0) {
            long id = mAdapter == null ? INVALID_ROW_ID : mAdapter.getItemId(position);
            mContextMenuInfo = new AdapterContextMenuInfo(originalView, position, id);
        }
        return super.showContextMenuForChild(originalView);
    }

    @Override
    public boolean showContextMenuForChild(View originalView, float x, float y) {
        int position = getPositionForView(originalView);
        if (position >= 0) {
            long id = mAdapter == null ? INVALID_ROW_ID : mAdapter.getItemId(position);
            mContextMenuInfo = new AdapterContextMenuInfo(originalView, position, id);
        }
        return super.showContextMenuForChild(originalView, x, y);
    }

    @Override
    public void addTouchables(ArrayList<View> views) {
        int count = getChildCount();
        int first = mFirstPosition;
        ListAdapter adapter = mAdapter;
        if (adapter == null) return;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (adapter.isEnabled(first + i)) views.add(child);
            child.addTouchables(views);
        }
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {
        // The selector draws the press. Pressing every row would light the whole list.
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewTreeObserver observer = getViewTreeObserver();
        observer.addOnTouchModeChangeListener(this);
        if (mTextFilterEnabled && observer.isAlive()) observer.addOnGlobalLayoutListener(this);
        if (mAdapter != null && mDataSetObserver == null) {
            mDataSetObserver = new DataSetObserverAdapter();
            mAdapter.registerDataSetObserver(mDataSetObserver);
            mDataChanged = true;
            mItemCount = mAdapter.getCount();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(mPendingCheckForLongPress);
        if (mFlingRunnable != null) mFlingRunnable.endFling();
        ViewTreeObserver observer = getViewTreeObserver();
        observer.removeOnTouchModeChangeListener(this);
        observer.removeOnGlobalLayoutListener(this);
        if (mAdapter != null && mDataSetObserver != null) {
            mAdapter.unregisterDataSetObserver(mDataSetObserver);
            mDataSetObserver = null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (getChildCount() > 0) {
            mDataChanged = true;
            requestLayout();
        }
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus && mSelectedPosition < 0 && mAdapter != null && !isInTouchMode()) {
            int pos = lookForSelectablePosition(mFirstPosition, true);
            if (pos >= 0) setSelection(pos);
        }
        positionKeyboardSelector();
        invalidate();
    }

    // ---------------------------------------------------------------- listeners the class implements

    public void onTouchModeChanged(boolean isInTouchMode) {
        invalidate();
        if (isInTouchMode) {
            if (mTouchMode == TOUCH_MODE_FLING && mFlingRunnable != null) mFlingRunnable.endFling();
        } else if (mSelectedPosition == INVALID_POSITION && mAdapter != null && getChildCount() > 0) {
            int pos = lookForSelectablePosition(mFirstPosition, true);
            if (pos >= 0) {
                mSelectedPosition = pos;
                mSelectedRowId = mAdapter.getItemId(pos);
            }
        }
    }

    public void onGlobalLayout() {}

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (mTextFilterEnabled && s != null) setFilterText(s.toString());
    }

    public void afterTextChanged(Editable s) {}

    public void onFilterComplete(int count) {
        if (mSelectedPosition >= count) mSelectedPosition = count > 0 ? 0 : INVALID_POSITION;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof LayoutParams; }

    void reportScrollStateChange(int newState) {
        if (newState == mLastScrollState) return;
        mLastScrollState = newState;
        if (mOnScrollListener != null) mOnScrollListener.onScrollStateChanged(this, newState);
    }

    void invokeOnItemScrollListener() {
        if (mOnScrollListener != null) {
            mOnScrollListener.onScroll(this, mFirstPosition, getChildCount(), mItemCount);
        }
    }

    // ---------------------------------------------------------------- recycler and fling

    private class FlingRunnable implements Runnable {
        void start(int velocityY) {
            mLastFlingY = 0;
            mScroller.fling(0, 0, 0, velocityY, 0, 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
            mTouchMode = TOUCH_MODE_FLING;
            postOnAnimation(this);
        }

        void startScroll(int distance, int duration) {
            mLastFlingY = 0;
            mScroller.startScroll(0, 0, 0, distance, duration);
            mTouchMode = TOUCH_MODE_FLING;
            postOnAnimation(this);
        }

        void endFling() {
            removeCallbacks(this);
            mTouchMode = TOUCH_MODE_REST;
            if (mScroller != null) mScroller.abortAnimation();
            reportScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
        }

        public void run() {
            if (mTouchMode != TOUCH_MODE_FLING) return;
            boolean more = mScroller.computeScrollOffset();
            int y = mScroller.getCurrY();
            int delta = mLastFlingY - y;
            mLastFlingY = y;
            boolean atEdge = false;
            int remaining = delta;
            int guard = 0;
            while (remaining != 0 && guard++ < 10000) {
                int max = Math.max(1, getHeight() - 1);
                int step = remaining;
                if (step > max) step = max;
                else if (step < -max) step = -max;
                atEdge = trackMotionScroll(step, step);
                if (atEdge || step == 0) break;
                remaining -= step;
            }
            if (atEdge) {
                int speed = (int) mScroller.getCurrVelocity();
                if (delta > 0) mEdgeGlowTop.onAbsorb(speed);
                else if (delta < 0) mEdgeGlowBottom.onAbsorb(speed);
                endFling();
                postInvalidateOnAnimation();
                return;
            }
            if (more && !mScroller.isFinished()) postOnAnimation(this);
            else endFling();
        }
    }

    /** Scrap bins keyed by view type. Framework-internal. */
    class RecycleBin {
        private ArrayList<View>[] mScrapViews;
        private int mViewTypeCount;

        void setViewTypeCount(int count) {
            if (count < 1) count = 1;
            if (count == mViewTypeCount && mScrapViews != null) return;
            @SuppressWarnings("unchecked")
            ArrayList<View>[] scrap = new ArrayList[count];
            for (int i = 0; i < count; i++) scrap[i] = new ArrayList<View>();
            mScrapViews = scrap;
            mViewTypeCount = count;
        }

        void clear() {
            if (mScrapViews == null) return;
            for (int i = 0; i < mScrapViews.length; i++) mScrapViews[i].clear();
        }

        void addScrapView(View scrap) {
            if (scrap == null || mScrapViews == null) return;
            LayoutParams lp = scrap.getLayoutParams() instanceof LayoutParams
                    ? (LayoutParams) scrap.getLayoutParams() : null;
            int type = lp == null ? 0 : lp.viewType;
            if (type < 0 || type >= mViewTypeCount) return;
            mScrapViews[type].add(scrap);
            if (mRecyclerListener != null) mRecyclerListener.onMovedToScrapHeap(scrap);
        }

        View getScrapView(int position) {
            if (mAdapter == null || mScrapViews == null) return null;
            int type = mAdapter.getItemViewType(position);
            if (type < 0 || type >= mViewTypeCount) return null;
            ArrayList<View> scrap = mScrapViews[type];
            if (scrap.isEmpty()) return null;
            return scrap.remove(scrap.size() - 1);
        }

        void reclaimScrapViews(List<View> views) {
            if (mScrapViews == null) return;
            for (int i = 0; i < mScrapViews.length; i++) {
                views.addAll(mScrapViews[i]);
                mScrapViews[i].clear();
            }
        }
    }

    private class DataSetObserverAdapter extends android.database.DataSetObserver {
        @Override
        public void onChanged() {
            mDataChanged = true;
            int oldCount = mItemCount;
            mItemCount = mAdapter == null ? 0 : mAdapter.getCount();
            boolean atBottom = false;
            int childCount = getChildCount();
            if (childCount > 0 && mFirstPosition + childCount >= oldCount) {
                View last = getChildAt(childCount - 1);
                atBottom = last.getBottom() <= getHeight() - mListPadding.bottom + 1;
            }
            if (mTranscriptMode == TRANSCRIPT_MODE_ALWAYS_SCROLL
                    || (mTranscriptMode == TRANSCRIPT_MODE_NORMAL && atBottom)) {
                mLayoutMode = LAYOUT_FORCE_BOTTOM;
            }
            handleDataChanged();
            if (!mDeferNotifyDataSetChanged) requestLayout();
            invalidate();
        }

        @Override
        public void onInvalidated() {
            mDataChanged = true;
            mItemCount = mAdapter == null ? 0 : mAdapter.getCount();
            mSelectedPosition = INVALID_POSITION;
            mSelectedRowId = INVALID_ROW_ID;
            handleDataChanged();
            if (!mDeferNotifyDataSetChanged) requestLayout();
            invalidate();
        }
    }

    static class SavedState extends BaseSavedState {
        int selectedPosition;
        int firstPosition;
        int top;
        String filter;

        SavedState(Parcelable superState) { super(superState); }

        SavedState(Parcel source) {
            super(source);
            selectedPosition = source.readInt();
            firstPosition = source.readInt();
            top = source.readInt();
            filter = source.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(selectedPosition);
            dest.writeInt(firstPosition);
            dest.writeInt(top);
            dest.writeString(filter);
        }
    }

    /** Row layout params. {@code viewType} is hidden framework state used by the recycler. */
    public static class LayoutParams extends ViewGroup.LayoutParams {
        int viewType;

        public LayoutParams(Context c, AttributeSet attrs) { super(c, attrs); }

        public LayoutParams(int w, int h) { super(w, h); }

        public LayoutParams(int w, int h, int viewType) {
            super(w, h);
            this.viewType = viewType;
        }

        public LayoutParams(ViewGroup.LayoutParams source) { super(source); }
    }

    /** Reports scroll position and whether the finger, a fling, or nothing is driving it. */
    public interface OnScrollListener {
        int SCROLL_STATE_IDLE = 0;
        int SCROLL_STATE_TOUCH_SCROLL = 1;
        int SCROLL_STATE_FLING = 2;

        void onScrollStateChanged(AbsListView view, int scrollState);

        void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount);
    }

    /** Told when a row view is dropped into the recycler. */
    public interface RecyclerListener {
        void onMovedToScrapHeap(View view);
    }

    /** A row can move the selector bounds, for example to skip a divider. */
    public interface SelectionBoundsAdjuster {
        void adjustListItemSelectionBounds(Rect bounds);
    }

    /** Choice-mode callback. Action mode presentation itself lands with the window work. */
    public interface MultiChoiceModeListener extends ActionMode.Callback {
        void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked);
    }
}
