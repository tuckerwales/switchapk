package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

/** Port of AOSP AbsSpinner: an AdapterView that shows only its selected item. */
public abstract class AbsSpinner extends AdapterView<SpinnerAdapter> {
    SpinnerAdapter mAdapter;
    int mHeightMeasureSpec;
    int mWidthMeasureSpec;
    int mSelectionLeftPadding = 0;
    int mSelectionTopPadding = 0;
    int mSelectionRightPadding = 0;
    int mSelectionBottomPadding = 0;
    final Rect mSpinnerPadding = new Rect();
    final RecycleBin mRecycler = new RecycleBin();
    private DataSetObserver mDataSetObserver;
    private Rect mTouchFrame;

    // AOSP AdapterView selection bookkeeping that our AdapterView does not carry.
    int mNextSelectedPosition = INVALID_POSITION;
    long mNextSelectedRowId = INVALID_ROW_ID;
    int mOldItemCount;
    boolean mNeedSync;
    long mSyncRowId = INVALID_ROW_ID;
    int mSyncPosition;

    public AbsSpinner(Context context) {
        super(context);
        initAbsSpinner();
    }

    public AbsSpinner(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public AbsSpinner(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public AbsSpinner(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (getImportantForAutofill() == IMPORTANT_FOR_AUTOFILL_AUTO) {
            setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_YES);
        }
        initAbsSpinner();
        final TypedArray a = context.obtainStyledAttributes(attrs, new int[] {android.R.attr.entries}, defStyleAttr,
                defStyleRes);
        final CharSequence[] entries = a.getTextArray(0);
        if (entries != null) {
            final ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(context,
                    android.R.layout.simple_spinner_item, entries);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            setAdapter(adapter);
        }
        a.recycle();
    }

    private void initAbsSpinner() {
        setFocusable(true);
        setWillNotDraw(false);
    }

    @Override
    public void setAdapter(SpinnerAdapter adapter) {
        if (null != mAdapter) {
            mAdapter.unregisterDataSetObserver(mDataSetObserver);
            resetList();
        }
        mAdapter = adapter;
        mOldSelectedPosition = INVALID_POSITION;
        mOldSelectedRowId = INVALID_ROW_ID;
        if (mAdapter != null) {
            mOldItemCount = mItemCount;
            mItemCount = mAdapter.getCount();
            checkFocus();
            mDataSetObserver = new AdapterDataSetObserver();
            mAdapter.registerDataSetObserver(mDataSetObserver);
            int position = mItemCount > 0 ? 0 : INVALID_POSITION;
            setSelectedPositionInt(position);
            setNextSelectedPositionInt(position);
            if (mItemCount == 0) checkSelectionChanged();
        } else {
            checkFocus();
            resetList();
            checkSelectionChanged();
        }
        requestLayout();
    }

    void resetList() {
        mDataChanged = false;
        mNeedSync = false;
        removeAllViewsInLayout();
        mOldSelectedPosition = INVALID_POSITION;
        mOldSelectedRowId = INVALID_ROW_ID;
        setSelectedPositionInt(INVALID_POSITION);
        setNextSelectedPositionInt(INVALID_POSITION);
        invalidate();
    }

    void setSelectedPositionInt(int position) {
        mSelectedPosition = position;
        mSelectedRowId = getItemIdAtPosition(position);
    }

    void setNextSelectedPositionInt(int position) {
        mNextSelectedPosition = position;
        mNextSelectedRowId = getItemIdAtPosition(position);
        if (mNeedSync && position >= 0) {
            mSyncPosition = position;
            mSyncRowId = mNextSelectedRowId;
        }
    }

    void handleDataChanged() {
        final int count = mItemCount;
        boolean found = false;
        if (count > 0) {
            int newPos;
            if (mNeedSync) {
                mNeedSync = false;
                newPos = findSyncPosition();
                if (newPos >= 0) {
                    int selectablePos = lookForSelectablePosition(newPos, true);
                    if (selectablePos == newPos) {
                        setNextSelectedPositionInt(newPos);
                        found = true;
                    }
                }
            }
            if (!found) {
                newPos = getSelectedItemPosition();
                if (newPos >= count) newPos = count - 1;
                if (newPos < 0) newPos = 0;
                int selectablePos = lookForSelectablePosition(newPos, true);
                if (selectablePos < 0) selectablePos = lookForSelectablePosition(newPos, false);
                if (selectablePos < 0) selectablePos = newPos;
                setNextSelectedPositionInt(selectablePos);
                checkSelectionChanged();
                found = true;
            }
        }
        if (!found) {
            mSelectedPosition = INVALID_POSITION;
            mSelectedRowId = INVALID_ROW_ID;
            mNextSelectedPosition = INVALID_POSITION;
            mNextSelectedRowId = INVALID_ROW_ID;
            mNeedSync = false;
            checkSelectionChanged();
        }
    }

    private int findSyncPosition() {
        int count = mItemCount;
        if (count == 0 || mAdapter == null) return INVALID_POSITION;
        long idToMatch = mSyncRowId;
        if (idToMatch == INVALID_ROW_ID) return INVALID_POSITION;
        int seed = Math.max(0, Math.min(count - 1, mSyncPosition));
        for (int i = 0; i < count; i++) {
            if (mAdapter.getItemId(i) == idToMatch) return i;
        }
        return seed;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize;
        int heightSize;
        mSpinnerPadding.left = mPaddingLeft > mSelectionLeftPadding ? mPaddingLeft : mSelectionLeftPadding;
        mSpinnerPadding.top = mPaddingTop > mSelectionTopPadding ? mPaddingTop : mSelectionTopPadding;
        mSpinnerPadding.right = mPaddingRight > mSelectionRightPadding ? mPaddingRight : mSelectionRightPadding;
        mSpinnerPadding.bottom = mPaddingBottom > mSelectionBottomPadding ? mPaddingBottom : mSelectionBottomPadding;
        if (mDataChanged) handleDataChanged();
        int preferredHeight = 0;
        int preferredWidth = 0;
        boolean needsMeasuring = true;
        int selectedPosition = getSelectedItemPosition();
        if (selectedPosition >= 0 && mAdapter != null && selectedPosition < mAdapter.getCount()) {
            View view = mRecycler.get(selectedPosition);
            if (view == null) {
                view = mAdapter.getView(selectedPosition, null, this);
                if (view.getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
                    view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
                }
            }
            mRecycler.put(selectedPosition, view);
            if (view.getLayoutParams() == null) {
                mBlockLayoutRequests = true;
                view.setLayoutParams(generateDefaultLayoutParams());
                mBlockLayoutRequests = false;
            }
            measureChild(view, widthMeasureSpec, heightMeasureSpec);
            preferredHeight = getChildHeight(view) + mSpinnerPadding.top + mSpinnerPadding.bottom;
            preferredWidth = getChildWidth(view) + mSpinnerPadding.left + mSpinnerPadding.right;
            needsMeasuring = false;
        }
        if (needsMeasuring) {
            preferredHeight = mSpinnerPadding.top + mSpinnerPadding.bottom;
            if (widthMode == MeasureSpec.UNSPECIFIED) preferredWidth = mSpinnerPadding.left + mSpinnerPadding.right;
        }
        preferredHeight = Math.max(preferredHeight, getSuggestedMinimumHeight());
        preferredWidth = Math.max(preferredWidth, getSuggestedMinimumWidth());
        heightSize = resolveSizeAndState(preferredHeight, heightMeasureSpec, 0);
        widthSize = resolveSizeAndState(preferredWidth, widthMeasureSpec, 0);
        setMeasuredDimension(widthSize, heightSize);
        mHeightMeasureSpec = heightMeasureSpec;
        mWidthMeasureSpec = widthMeasureSpec;
    }

    int getChildHeight(View child) { return child.getMeasuredHeight(); }

    int getChildWidth(View child) { return child.getMeasuredWidth(); }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    void recycleAllViews() {
        final int childCount = getChildCount();
        final AbsSpinner.RecycleBin recycleBin = mRecycler;
        final int position = mFirstPosition;
        for (int i = 0; i < childCount; i++) {
            View v = getChildAt(i);
            int index = position + i;
            recycleBin.put(index, v);
        }
    }

    public void setSelection(int position, boolean animate) {
        boolean shouldAnimate = animate && mFirstPosition <= position
                && position <= mFirstPosition + getChildCount() - 1;
        setSelectionInt(position, shouldAnimate);
    }

    @Override
    public void setSelection(int position) {
        setNextSelectedPositionInt(position);
        requestLayout();
        invalidate();
    }

    void setSelectionInt(int position, boolean animate) {
        if (position != mOldSelectedPosition) {
            mBlockLayoutRequests = true;
            int delta = position - mSelectedPosition;
            setNextSelectedPositionInt(position);
            layout(delta, animate);
            mBlockLayoutRequests = false;
        }
    }

    abstract void layout(int delta, boolean animate);

    @Override
    public View getSelectedView() {
        if (mItemCount > 0 && mSelectedPosition >= 0) return getChildAt(mSelectedPosition - mFirstPosition);
        return null;
    }

    @Override
    public void requestLayout() {
        if (!mBlockLayoutRequests) super.requestLayout();
    }

    @Override
    public SpinnerAdapter getAdapter() { return mAdapter; }

    @Override
    public int getCount() { return mItemCount; }

    public int pointToPosition(int x, int y) {
        Rect frame = mTouchFrame;
        if (frame == null) {
            mTouchFrame = new Rect();
            frame = mTouchFrame;
        }
        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                child.getHitRect(frame);
                if (frame.contains(x, y)) return mFirstPosition + i;
            }
        }
        return INVALID_POSITION;
    }

    static class SavedState extends BaseSavedState {
        long selectedId;
        int position;

        SavedState(Parcelable superState) { super(superState); }

        SavedState(Parcel in) {
            super(in);
            selectedId = in.readLong();
            position = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeLong(selectedId);
            out.writeInt(position);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) { return new SavedState(in); }
            public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.selectedId = getSelectedItemId();
        if (ss.selectedId >= 0) ss.position = getSelectedItemPosition();
        else ss.position = INVALID_POSITION;
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        if (ss.selectedId >= 0) {
            mDataChanged = true;
            mNeedSync = true;
            mSyncRowId = ss.selectedId;
            mSyncPosition = ss.position;
            requestLayout();
        }
    }

    class RecycleBin {
        private final SparseArray<View> mScrapHeap = new SparseArray<View>();

        public void put(int position, View v) { mScrapHeap.put(position, v); }

        View get(int position) {
            View result = mScrapHeap.get(position);
            if (result != null) mScrapHeap.delete(position);
            return result;
        }

        void clear() {
            final SparseArray<View> scrapHeap = mScrapHeap;
            final int count = scrapHeap.size();
            for (int i = 0; i < count; i++) {
                final View view = scrapHeap.valueAt(i);
                if (view != null) removeDetachedView(view, true);
            }
            scrapHeap.clear();
        }
    }

    class AdapterDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            mDataChanged = true;
            mOldItemCount = mItemCount;
            mItemCount = getAdapter().getCount();
            if (getAdapter().hasStableIds() && mOldItemCount == 0 && mItemCount > 0) {
                mNeedSync = true;
            } else if (!mNeedSync) {
                mSyncRowId = mSelectedRowId;
                mSyncPosition = mSelectedPosition;
                mNeedSync = mSelectedRowId != INVALID_ROW_ID;
            }
            checkFocus();
            requestLayout();
        }

        @Override
        public void onInvalidated() {
            mDataChanged = true;
            mOldItemCount = mItemCount;
            mItemCount = 0;
            mSelectedPosition = INVALID_POSITION;
            mSelectedRowId = INVALID_ROW_ID;
            mNextSelectedPosition = INVALID_POSITION;
            mNextSelectedRowId = INVALID_ROW_ID;
            mNeedSync = false;
            checkFocus();
            requestLayout();
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() { return AbsSpinner.class.getName(); }
}
