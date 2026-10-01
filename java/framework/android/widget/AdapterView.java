package android.widget;

import android.content.Context;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

/**
 * A view whose children come from an {@link Adapter} (AOSP AdapterView).
 * Callers add rows through the adapter. {@link #addView} throws.
 */
public abstract class AdapterView<T extends Adapter> extends ViewGroup {
    public static final int INVALID_POSITION = -1;
    public static final long INVALID_ROW_ID = Long.MIN_VALUE;
    public static final int ITEM_VIEW_TYPE_HEADER_OR_FOOTER = -2;
    public static final int ITEM_VIEW_TYPE_IGNORE = -1;

    OnItemClickListener mOnItemClickListener;
    OnItemLongClickListener mOnItemLongClickListener;
    OnItemSelectedListener mOnItemSelectedListener;
    View mEmptyView;
    int mFirstPosition;
    int mSpecificTop;
    int mSelectedPosition = INVALID_POSITION;
    long mSelectedRowId = INVALID_ROW_ID;
    int mItemCount;
    int mOldSelectedPosition = INVALID_POSITION;
    long mOldSelectedRowId = INVALID_ROW_ID;
    boolean mDataChanged;
    int mDesiredFocusableState = FOCUSABLE;
    boolean mDesiredFocusableInTouchModeState;
    ContextMenu.ContextMenuInfo mContextMenuInfo;

    public AdapterView(Context context) { this(context, null); }

    public AdapterView(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public AdapterView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AdapterView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setFocusable(FOCUSABLE);
    }

    public void setOnItemClickListener(OnItemClickListener listener) { mOnItemClickListener = listener; }

    public final OnItemClickListener getOnItemClickListener() { return mOnItemClickListener; }

    public boolean performItemClick(View view, int position, long id) {
        if (mOnItemClickListener == null) return false;
        playSoundEffect(SoundEffectConstants.CLICK);
        mOnItemClickListener.onItemClick(this, view, position, id);
        if (view != null) view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
        return true;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        if (!isLongClickable()) setLongClickable(true);
        mOnItemLongClickListener = listener;
    }

    public final OnItemLongClickListener getOnItemLongClickListener() { return mOnItemLongClickListener; }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) { mOnItemSelectedListener = listener; }

    public final OnItemSelectedListener getOnItemSelectedListener() { return mOnItemSelectedListener; }

    public abstract T getAdapter();

    public abstract void setAdapter(T adapter);

    @Override
    public void addView(View child) { throw unsupported(); }

    @Override
    public void addView(View child, int index) { throw unsupported(); }

    @Override
    public void addView(View child, LayoutParams params) { throw unsupported(); }

    @Override
    public void addView(View child, int index, LayoutParams params) { throw unsupported(); }

    @Override
    public void removeView(View child) { throw unsupported(); }

    @Override
    public void removeViewAt(int index) { throw unsupported(); }

    @Override
    public void removeAllViews() { throw unsupported(); }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("addView(View) is not supported in AdapterView");
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {}

    public int getSelectedItemPosition() { return mSelectedPosition; }

    public long getSelectedItemId() { return mSelectedRowId; }

    public abstract View getSelectedView();

    public Object getSelectedItem() {
        T adapter = getAdapter();
        int pos = getSelectedItemPosition();
        if (adapter != null && adapter.getCount() > 0 && pos >= 0) return adapter.getItem(pos);
        return null;
    }

    public int getCount() { return mItemCount; }

    public int getPositionForView(View view) {
        View listItem = view;
        while (true) {
            View parent;
            try {
                parent = (View) listItem.getParent();
            } catch (ClassCastException e) {
                return INVALID_POSITION;
            }
            if (parent == null || parent == this) break;
            listItem = parent;
        }
        if (listItem.getParent() != this) return INVALID_POSITION;
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            if (getChildAt(i) == listItem) return mFirstPosition + i;
        }
        return INVALID_POSITION;
    }

    public int getFirstVisiblePosition() { return mFirstPosition; }

    public int getLastVisiblePosition() { return mFirstPosition + getChildCount() - 1; }

    public abstract void setSelection(int position);

    public void setEmptyView(View emptyView) {
        mEmptyView = emptyView;
        T adapter = getAdapter();
        updateEmptyStatus(adapter == null || adapter.isEmpty());
    }

    public View getEmptyView() { return mEmptyView; }

    @Override
    public void setFocusable(int focusable) {
        T adapter = getAdapter();
        boolean empty = adapter == null || adapter.getCount() == 0;
        mDesiredFocusableState = focusable;
        if (focusable == NOT_FOCUSABLE) mDesiredFocusableInTouchModeState = false;
        super.setFocusable(empty ? NOT_FOCUSABLE : focusable);
    }

    @Override
    public void setFocusableInTouchMode(boolean focusable) {
        T adapter = getAdapter();
        boolean empty = adapter == null || adapter.getCount() == 0;
        mDesiredFocusableInTouchModeState = focusable;
        if (focusable) mDesiredFocusableState = FOCUSABLE;
        super.setFocusableInTouchMode(focusable && !empty);
    }

    public Object getItemAtPosition(int position) {
        T adapter = getAdapter();
        if (adapter == null || position < 0) return null;
        return adapter.getItem(position);
    }

    public long getItemIdAtPosition(int position) {
        T adapter = getAdapter();
        if (adapter == null || position < 0) return INVALID_ROW_ID;
        return adapter.getItemId(position);
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        throw new RuntimeException("Don't call setOnClickListener for an AdapterView. "
                + "You probably want setOnItemClickListener instead");
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        dispatchFreezeSelfOnly(container);
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        dispatchThawSelfOnly(container);
    }

    @Override
    protected void onDetachedFromWindow() { super.onDetachedFromWindow(); }

    @Override
    public CharSequence getAccessibilityClassName() { return AdapterView.class.getName(); }

    void selectionChanged() {
        if (mOnItemSelectedListener == null) return;
        if (mSelectedPosition >= 0) {
            View v = getSelectedView();
            mOnItemSelectedListener.onItemSelected(this, v, mSelectedPosition, getSelectedItemId());
        } else {
            mOnItemSelectedListener.onNothingSelected(this);
        }
    }

    void checkSelectionChanged() {
        if (mSelectedPosition != mOldSelectedPosition || mSelectedRowId != mOldSelectedRowId) {
            selectionChanged();
            mOldSelectedPosition = mSelectedPosition;
            mOldSelectedRowId = mSelectedRowId;
        }
    }

    void checkFocus() {
        T adapter = getAdapter();
        boolean empty = adapter == null || adapter.getCount() == 0;
        boolean focusable = !empty || mEmptyView != null;
        super.setFocusableInTouchMode(focusable && mDesiredFocusableInTouchModeState);
        super.setFocusable(focusable ? mDesiredFocusableState : NOT_FOCUSABLE);
        if (mEmptyView != null) updateEmptyStatus(adapter == null || adapter.isEmpty());
    }

    private void updateEmptyStatus(boolean empty) {
        if (empty) {
            if (mEmptyView != null) {
                mEmptyView.setVisibility(VISIBLE);
                setVisibility(GONE);
            } else {
                setVisibility(VISIBLE);
            }
        } else {
            if (mEmptyView != null) mEmptyView.setVisibility(GONE);
            setVisibility(VISIBLE);
        }
    }

    int lookForSelectablePosition(int position, boolean lookDown) {
        T adapter = getAdapter();
        if (adapter == null || !(adapter instanceof ListAdapter)) return INVALID_POSITION;
        ListAdapter list = (ListAdapter) adapter;
        int count = list.getCount();
        if (!list.areAllItemsEnabled()) {
            if (lookDown) {
                position = Math.max(0, position);
                while (position < count && !list.isEnabled(position)) position++;
            } else {
                position = Math.min(position, count - 1);
                while (position >= 0 && !list.isEnabled(position)) position--;
            }
        }
        if (position < 0 || position >= count) return INVALID_POSITION;
        return position;
    }

    /** A click on a row. */
    public interface OnItemClickListener {
        void onItemClick(AdapterView<?> parent, View view, int position, long id);
    }

    /** A long press on a row. Return true when the press was consumed. */
    public interface OnItemLongClickListener {
        boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id);
    }

    /** Selection moved to a row, or to nothing. */
    public interface OnItemSelectedListener {
        void onItemSelected(AdapterView<?> parent, View view, int position, long id);

        void onNothingSelected(AdapterView<?> parent);
    }

    /** Extra data for a context menu opened on a row. */
    public static class AdapterContextMenuInfo implements ContextMenu.ContextMenuInfo {
        public View targetView;
        public int position;
        public long id;

        public AdapterContextMenuInfo(View targetView, int position, long id) {
            this.targetView = targetView;
            this.position = position;
            this.id = id;
        }
    }
}
