package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ExpandableListConnector.PositionMetadata;

import java.util.ArrayList;

/**
 * A two-level list: groups that expand to show their children (AOSP ExpandableListView).
 * The {@link ExpandableListAdapter} is flattened by {@link ExpandableListConnector}; the group and
 * child indicators are drawn over the rows in {@link #dispatchDraw}.
 */
public class ExpandableListView extends ListView {
    public static final int PACKED_POSITION_TYPE_GROUP = 0;
    public static final int PACKED_POSITION_TYPE_CHILD = 1;
    public static final int PACKED_POSITION_TYPE_NULL = 2;
    public static final long PACKED_POSITION_VALUE_NULL = 0x00000000FFFFFFFFL;
    public static final int CHILD_INDICATOR_INHERIT = -1;

    private static final long PACKED_POSITION_MASK_CHILD = 0x00000000FFFFFFFFL;
    private static final long PACKED_POSITION_MASK_GROUP = 0x7FFFFFFF00000000L;
    private static final long PACKED_POSITION_MASK_TYPE = 0x8000000000000000L;
    private static final long PACKED_POSITION_SHIFT_GROUP = 32;
    private static final long PACKED_POSITION_SHIFT_TYPE = 63;
    private static final long PACKED_POSITION_INT_MASK_CHILD = 0xFFFFFFFFL;
    private static final long PACKED_POSITION_INT_MASK_GROUP = 0x7FFFFFFFL;

    private static final int INDICATOR_UNDEFINED = -2;

    private static final int[] EMPTY_STATE_SET = {};
    private static final int[] GROUP_EXPANDED_STATE_SET = {android.R.attr.state_expanded};
    private static final int[] GROUP_EMPTY_STATE_SET = {android.R.attr.state_empty};
    private static final int[] GROUP_EXPANDED_EMPTY_STATE_SET = {android.R.attr.state_expanded,
            android.R.attr.state_empty};
    private static final int[][] GROUP_STATE_SETS = {EMPTY_STATE_SET, GROUP_EXPANDED_STATE_SET,
            GROUP_EMPTY_STATE_SET, GROUP_EXPANDED_EMPTY_STATE_SET};
    private static final int[] CHILD_LAST_STATE_SET = {android.R.attr.state_last};

    private ExpandableListConnector mConnector;
    private ExpandableListAdapter mAdapter;

    private int mIndicatorLeft;
    private int mIndicatorRight;
    private int mIndicatorStart;
    private int mIndicatorEnd;
    private int mChildIndicatorLeft;
    private int mChildIndicatorRight;
    private int mChildIndicatorStart;
    private int mChildIndicatorEnd;

    private Drawable mGroupIndicator;
    private Drawable mChildIndicator;
    private Drawable mChildDivider;
    private final Rect mIndicatorRect = new Rect();

    private OnGroupCollapseListener mOnGroupCollapseListener;
    private OnGroupExpandListener mOnGroupExpandListener;
    private OnGroupClickListener mOnGroupClickListener;
    private OnChildClickListener mOnChildClickListener;

    public ExpandableListView(Context context) { this(context, null); }

    public ExpandableListView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.expandableListViewStyle);
    }

    public ExpandableListView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ExpandableListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.groupIndicator, android.R.attr.childIndicator, android.R.attr.indicatorLeft,
                android.R.attr.indicatorRight, android.R.attr.childIndicatorLeft,
                android.R.attr.childIndicatorRight, android.R.attr.childDivider, android.R.attr.indicatorStart,
                android.R.attr.indicatorEnd, android.R.attr.childIndicatorStart,
                android.R.attr.childIndicatorEnd}, defStyleAttr, defStyleRes);
        mGroupIndicator = a.getDrawable(0);
        mChildIndicator = a.getDrawable(1);
        mIndicatorLeft = a.getDimensionPixelSize(2, 0);
        mIndicatorRight = a.getDimensionPixelSize(3, 0);
        if (mIndicatorRight == 0 && mGroupIndicator != null) {
            mIndicatorRight = mIndicatorLeft + mGroupIndicator.getIntrinsicWidth();
        }
        mChildIndicatorLeft = a.getDimensionPixelSize(4, CHILD_INDICATOR_INHERIT);
        mChildIndicatorRight = a.getDimensionPixelSize(5, CHILD_INDICATOR_INHERIT);
        mChildDivider = a.getDrawable(6);
        if (!isRtlCompatibilityMode()) {
            mIndicatorStart = a.getDimensionPixelSize(7, INDICATOR_UNDEFINED);
            mIndicatorEnd = a.getDimensionPixelSize(8, INDICATOR_UNDEFINED);
            mChildIndicatorStart = a.getDimensionPixelSize(9, CHILD_INDICATOR_INHERIT);
            mChildIndicatorEnd = a.getDimensionPixelSize(10, CHILD_INDICATOR_INHERIT);
        } else {
            mIndicatorStart = mIndicatorEnd = INDICATOR_UNDEFINED;
            mChildIndicatorStart = mChildIndicatorEnd = CHILD_INDICATOR_INHERIT;
        }
        a.recycle();
    }

    private boolean isRtlCompatibilityMode() {
        return getContext().getApplicationInfo().targetSdkVersion < 17;
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        resolveIndicator();
        resolveChildIndicator();
    }

    private void resolveIndicator() {
        final boolean isLayoutRtl = isLayoutRtl();
        if (isLayoutRtl) {
            if (mIndicatorStart >= 0) mIndicatorRight = mIndicatorStart;
            if (mIndicatorEnd >= 0) mIndicatorLeft = mIndicatorEnd;
        } else {
            if (mIndicatorStart >= 0) mIndicatorLeft = mIndicatorStart;
            if (mIndicatorEnd >= 0) mIndicatorRight = mIndicatorEnd;
        }
        if (mIndicatorRight == 0 && mGroupIndicator != null) {
            mIndicatorRight = mIndicatorLeft + mGroupIndicator.getIntrinsicWidth();
        }
    }

    private void resolveChildIndicator() {
        final boolean isLayoutRtl = isLayoutRtl();
        if (isLayoutRtl) {
            if (mChildIndicatorStart >= CHILD_INDICATOR_INHERIT) mChildIndicatorRight = mChildIndicatorStart;
            if (mChildIndicatorEnd >= CHILD_INDICATOR_INHERIT) mChildIndicatorLeft = mChildIndicatorEnd;
        } else {
            if (mChildIndicatorStart >= CHILD_INDICATOR_INHERIT) mChildIndicatorLeft = mChildIndicatorStart;
            if (mChildIndicatorEnd >= CHILD_INDICATOR_INHERIT) mChildIndicatorRight = mChildIndicatorEnd;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if ((mChildIndicator == null) && (mGroupIndicator == null)) return;
        int saveCount = 0;
        final boolean clipToPadding = (mGroupFlags & CLIP_TO_PADDING_MASK) == CLIP_TO_PADDING_MASK;
        if (clipToPadding) {
            saveCount = canvas.save();
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            canvas.clipRect(scrollX + getPaddingLeft(), scrollY + getPaddingTop(),
                    scrollX + getWidth() - getPaddingRight(), scrollY + getHeight() - getPaddingBottom());
        }
        final int headerViewsCount = getHeaderViewsCount();
        final int lastChildFlPos = mItemCount - getFooterViewsCount() - headerViewsCount - 1;
        final int myB = getHeight();
        int lastItemType = ~(ExpandableListPosition.CHILD | ExpandableListPosition.GROUP);
        final Rect indicatorRect = mIndicatorRect;
        final int childCount = getChildCount();
        for (int i = 0, childFlPos = mFirstPosition - headerViewsCount; i < childCount; i++, childFlPos++) {
            if (childFlPos < 0) continue;
            if (childFlPos > lastChildFlPos) break;
            View item = getChildAt(i);
            int t = item.getTop();
            int b = item.getBottom();
            if ((b < 0) || (t > myB)) continue;
            PositionMetadata pos = mConnector.getUnflattenedPos(childFlPos);
            final boolean isLayoutRtl = isLayoutRtl();
            final int width = getWidth();
            if (pos.position.type != lastItemType) {
                if (pos.position.type == ExpandableListPosition.CHILD) {
                    indicatorRect.left = (mChildIndicatorLeft == CHILD_INDICATOR_INHERIT)
                            ? mIndicatorLeft : mChildIndicatorLeft;
                    indicatorRect.right = (mChildIndicatorRight == CHILD_INDICATOR_INHERIT)
                            ? mIndicatorRight : mChildIndicatorRight;
                } else {
                    indicatorRect.left = mIndicatorLeft;
                    indicatorRect.right = mIndicatorRight;
                }
                if (isLayoutRtl) {
                    final int temp = indicatorRect.left;
                    indicatorRect.left = width - indicatorRect.right;
                    indicatorRect.right = width - temp;
                    indicatorRect.left -= getPaddingRight();
                    indicatorRect.right -= getPaddingRight();
                } else {
                    indicatorRect.left += getPaddingLeft();
                    indicatorRect.right += getPaddingLeft();
                }
                lastItemType = pos.position.type;
            }
            if (indicatorRect.left != indicatorRect.right) {
                indicatorRect.top = t;
                indicatorRect.bottom = b;
                Drawable indicator = getIndicator(pos);
                if (indicator != null) {
                    indicator.setBounds(indicatorRect);
                    indicator.draw(canvas);
                }
            }
            pos.recycle();
        }
        if (clipToPadding) canvas.restoreToCount(saveCount);
    }

    private Drawable getIndicator(PositionMetadata pos) {
        Drawable indicator;
        if (pos.position.type == ExpandableListPosition.GROUP) {
            indicator = mGroupIndicator;
            if (indicator != null && indicator.isStateful()) {
                boolean isEmpty = (pos.groupMetadata == null)
                        || (pos.groupMetadata.lastChildFlPos == pos.groupMetadata.flPos);
                final int stateSetIndex = (pos.isExpanded() ? 1 : 0) | (isEmpty ? 2 : 0);
                indicator.setState(GROUP_STATE_SETS[stateSetIndex]);
            }
        } else {
            indicator = mChildIndicator;
            if (indicator != null && indicator.isStateful()) {
                final int[] stateSet = pos.position.flatListPos == pos.groupMetadata.lastChildFlPos
                        ? CHILD_LAST_STATE_SET : EMPTY_STATE_SET;
                indicator.setState(stateSet);
            }
        }
        return indicator;
    }

    public void setChildDivider(Drawable childDivider) {
        mChildDivider = childDivider;
        invalidate();
    }

    @Override
    void drawDivider(Canvas canvas, Rect bounds, int childIndex) {
        int flatListPosition = childIndex + mFirstPosition;
        // Only the rows of the adapter, not headers or footers, can be children.
        if (flatListPosition >= 0) {
            final int adjustedPosition = getFlatPositionForConnector(flatListPosition);
            if (adjustedPosition >= 0 && adjustedPosition < mConnector.getCount()) {
                PositionMetadata pos = mConnector.getUnflattenedPos(adjustedPosition);
                if ((pos.position.type == ExpandableListPosition.CHILD) || (pos.isExpanded()
                        && pos.groupMetadata.lastChildFlPos != pos.groupMetadata.flPos)) {
                    final Drawable divider = mChildDivider;
                    if (divider != null) {
                        divider.setBounds(bounds);
                        divider.draw(canvas);
                    }
                    pos.recycle();
                    return;
                }
                pos.recycle();
            }
        }
        super.drawDivider(canvas, bounds, childIndex);
    }

    /** Use {@link #setAdapter(ExpandableListAdapter)}: a plain ListAdapter is not accepted. */
    @Override
    public void setAdapter(ListAdapter adapter) {
        throw new RuntimeException("For ExpandableListView, use setAdapter(ExpandableListAdapter) instead of "
                + "setAdapter(ListAdapter)");
    }

    @Override
    public ListAdapter getAdapter() { return super.getAdapter(); }

    @Override
    public void setOnItemClickListener(OnItemClickListener l) { super.setOnItemClickListener(l); }

    public void setAdapter(ExpandableListAdapter adapter) {
        mAdapter = adapter;
        if (adapter != null) mConnector = new ExpandableListConnector(adapter);
        else mConnector = null;
        super.setAdapter(mConnector);
    }

    public ExpandableListAdapter getExpandableListAdapter() { return mAdapter; }

    private boolean isHeaderOrFooterPosition(int position) {
        final int footerViewsStart = mItemCount - getFooterViewsCount();
        return (position < getHeaderViewsCount() || position >= footerViewsStart);
    }

    private int getFlatPositionForConnector(int flatListPosition) { return flatListPosition - getHeaderViewsCount(); }

    private int getAbsoluteFlatPosition(int flatListPosition) { return flatListPosition + getHeaderViewsCount(); }

    @Override
    public boolean performItemClick(View v, int position, long id) {
        if (isHeaderOrFooterPosition(position)) return super.performItemClick(v, position, id);
        final int adjustedPosition = getFlatPositionForConnector(position);
        return handleItemClick(v, adjustedPosition, id);
    }

    boolean handleItemClick(View v, int position, long id) {
        final PositionMetadata posMetadata = mConnector.getUnflattenedPos(position);
        id = getChildOrGroupId(posMetadata.position);
        boolean returnValue;
        if (posMetadata.position.type == ExpandableListPosition.GROUP) {
            if (mOnGroupClickListener != null) {
                if (mOnGroupClickListener.onGroupClick(this, v, posMetadata.position.groupPos, id)) {
                    posMetadata.recycle();
                    return true;
                }
            }
            if (posMetadata.isExpanded()) {
                mConnector.collapseGroup(posMetadata);
                playSoundEffect(SoundEffectConstants.CLICK);
                if (mOnGroupCollapseListener != null) {
                    mOnGroupCollapseListener.onGroupCollapse(posMetadata.position.groupPos);
                }
            } else {
                mConnector.expandGroup(posMetadata);
                playSoundEffect(SoundEffectConstants.CLICK);
                if (mOnGroupExpandListener != null) {
                    mOnGroupExpandListener.onGroupExpand(posMetadata.position.groupPos);
                }
                final int groupPos = posMetadata.position.groupPos;
                final int groupFlatPos = posMetadata.position.flatListPos;
                final int shiftedGroupPosition = groupFlatPos + getHeaderViewsCount();
                smoothScrollToPosition(shiftedGroupPosition + mAdapter.getChildrenCount(groupPos),
                        shiftedGroupPosition);
            }
            returnValue = true;
        } else {
            if (mOnChildClickListener != null) {
                playSoundEffect(SoundEffectConstants.CLICK);
                return mOnChildClickListener.onChildClick(this, v, posMetadata.position.groupPos,
                        posMetadata.position.childPos, id);
            }
            returnValue = false;
        }
        posMetadata.recycle();
        return returnValue;
    }

    public boolean expandGroup(int groupPos) { return expandGroup(groupPos, false); }

    public boolean expandGroup(int groupPos, boolean animate) {
        ExpandableListPosition elGroupPos = ExpandableListPosition.obtain(ExpandableListPosition.GROUP, groupPos, -1, -1);
        PositionMetadata pm = mConnector.getFlattenedPos(elGroupPos);
        elGroupPos.recycle();
        boolean retValue = mConnector.expandGroup(pm);
        if (mOnGroupExpandListener != null) mOnGroupExpandListener.onGroupExpand(groupPos);
        if (animate) {
            final int groupFlatPos = pm.position.flatListPos;
            final int shiftedGroupPosition = groupFlatPos + getHeaderViewsCount();
            smoothScrollToPosition(shiftedGroupPosition + mAdapter.getChildrenCount(groupPos), shiftedGroupPosition);
        }
        pm.recycle();
        return retValue;
    }

    public boolean collapseGroup(int groupPos) {
        boolean retValue = mConnector.collapseGroup(groupPos);
        if (mOnGroupCollapseListener != null) mOnGroupCollapseListener.onGroupCollapse(groupPos);
        return retValue;
    }

    /** Told when a group collapses. */
    public interface OnGroupCollapseListener {
        void onGroupCollapse(int groupPosition);
    }

    public void setOnGroupCollapseListener(OnGroupCollapseListener onGroupCollapseListener) {
        mOnGroupCollapseListener = onGroupCollapseListener;
    }

    /** Told when a group expands. */
    public interface OnGroupExpandListener {
        void onGroupExpand(int groupPosition);
    }

    public void setOnGroupExpandListener(OnGroupExpandListener onGroupExpandListener) {
        mOnGroupExpandListener = onGroupExpandListener;
    }

    /** Told when a group row is clicked; return true to stop it expanding or collapsing. */
    public interface OnGroupClickListener {
        boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id);
    }

    public void setOnGroupClickListener(OnGroupClickListener onGroupClickListener) {
        mOnGroupClickListener = onGroupClickListener;
    }

    /** Told when a child row is clicked. */
    public interface OnChildClickListener {
        boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id);
    }

    public void setOnChildClickListener(OnChildClickListener onChildClickListener) {
        mOnChildClickListener = onChildClickListener;
    }

    public long getExpandableListPosition(int flatListPosition) {
        if (isHeaderOrFooterPosition(flatListPosition)) return PACKED_POSITION_VALUE_NULL;
        final int adjustedPosition = getFlatPositionForConnector(flatListPosition);
        PositionMetadata pm = mConnector.getUnflattenedPos(adjustedPosition);
        long packedPos = pm.position.getPackedPosition();
        pm.recycle();
        return packedPos;
    }

    public int getFlatListPosition(long packedPosition) {
        ExpandableListPosition elPackedPos = ExpandableListPosition.obtainPosition(packedPosition);
        if (elPackedPos == null) return INVALID_POSITION;
        PositionMetadata pm = mConnector.getFlattenedPos(elPackedPos);
        elPackedPos.recycle();
        if (pm == null) return INVALID_POSITION;
        final int flatListPosition = pm.position.flatListPos;
        pm.recycle();
        return getAbsoluteFlatPosition(flatListPosition);
    }

    public long getSelectedPosition() {
        final int selectedPos = getSelectedItemPosition();
        return getExpandableListPosition(selectedPos);
    }

    public long getSelectedId() {
        long packedPos = getSelectedPosition();
        if (packedPos == PACKED_POSITION_VALUE_NULL) return -1;
        int groupPos = getPackedPositionGroup(packedPos);
        if (getPackedPositionType(packedPos) == PACKED_POSITION_TYPE_GROUP) return mAdapter.getGroupId(groupPos);
        return mAdapter.getChildId(groupPos, getPackedPositionChild(packedPos));
    }

    public void setSelectedGroup(int groupPosition) {
        ExpandableListPosition elGroupPos = ExpandableListPosition.obtainGroupPosition(groupPosition);
        PositionMetadata pm = mConnector.getFlattenedPos(elGroupPos);
        elGroupPos.recycle();
        final int absoluteFlatPosition = getAbsoluteFlatPosition(pm.position.flatListPos);
        super.setSelection(absoluteFlatPosition);
        pm.recycle();
    }

    public boolean setSelectedChild(int groupPosition, int childPosition, boolean shouldExpandGroup) {
        ExpandableListPosition elChildPos = ExpandableListPosition.obtainChildPosition(groupPosition, childPosition);
        PositionMetadata flatChildPos = mConnector.getFlattenedPos(elChildPos);
        if (flatChildPos == null) {
            if (!shouldExpandGroup) return false;
            expandGroup(groupPosition);
            flatChildPos = mConnector.getFlattenedPos(elChildPos);
            if (flatChildPos == null) throw new IllegalStateException("Could not find child");
        }
        int absoluteFlatPosition = getAbsoluteFlatPosition(flatChildPos.position.flatListPos);
        super.setSelection(absoluteFlatPosition);
        elChildPos.recycle();
        flatChildPos.recycle();
        return true;
    }

    public boolean isGroupExpanded(int groupPosition) { return mConnector.isGroupExpanded(groupPosition); }

    public static int getPackedPositionType(long packedPosition) {
        if (packedPosition == PACKED_POSITION_VALUE_NULL) return PACKED_POSITION_TYPE_NULL;
        return (packedPosition & PACKED_POSITION_MASK_TYPE) == PACKED_POSITION_MASK_TYPE
                ? PACKED_POSITION_TYPE_CHILD : PACKED_POSITION_TYPE_GROUP;
    }

    public static int getPackedPositionGroup(long packedPosition) {
        if (packedPosition == PACKED_POSITION_VALUE_NULL) return -1;
        return (int) ((packedPosition & PACKED_POSITION_MASK_GROUP) >> PACKED_POSITION_SHIFT_GROUP);
    }

    public static int getPackedPositionChild(long packedPosition) {
        if (packedPosition == PACKED_POSITION_VALUE_NULL) return -1;
        if ((packedPosition & PACKED_POSITION_MASK_TYPE) != PACKED_POSITION_MASK_TYPE) return -1;
        return (int) (packedPosition & PACKED_POSITION_MASK_CHILD);
    }

    public static long getPackedPositionForChild(int groupPosition, int childPosition) {
        return (((long) PACKED_POSITION_TYPE_CHILD) << PACKED_POSITION_SHIFT_TYPE)
                | ((((long) groupPosition) & PACKED_POSITION_INT_MASK_GROUP) << PACKED_POSITION_SHIFT_GROUP)
                | (((long) childPosition) & PACKED_POSITION_INT_MASK_CHILD);
    }

    public static long getPackedPositionForGroup(int groupPosition) {
        return ((((long) groupPosition) & PACKED_POSITION_INT_MASK_GROUP) << PACKED_POSITION_SHIFT_GROUP);
    }

    @Override
    ContextMenu.ContextMenuInfo createContextMenuInfo(View view, int flatListPosition, long id) {
        if (isHeaderOrFooterPosition(flatListPosition)) {
            return new AdapterContextMenuInfo(view, flatListPosition, id);
        }
        final int adjustedPosition = getFlatPositionForConnector(flatListPosition);
        PositionMetadata pm = mConnector.getUnflattenedPos(adjustedPosition);
        ExpandableListPosition pos = pm.position;
        id = getChildOrGroupId(pos);
        long packedPosition = pos.getPackedPosition();
        pm.recycle();
        return new ExpandableListContextMenuInfo(view, packedPosition, id);
    }

    @Override
    public void onInitializeAccessibilityNodeInfoForItem(View view, int position, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfoForItem(view, position, info);
    }

    private long getChildOrGroupId(ExpandableListPosition position) {
        if (position.type == ExpandableListPosition.CHILD) {
            return mAdapter.getChildId(position.groupPos, position.childPos);
        }
        return mAdapter.getGroupId(position.groupPos);
    }

    public void setChildIndicator(Drawable childIndicator) { mChildIndicator = childIndicator; }

    public void setChildIndicatorBounds(int left, int right) {
        mChildIndicatorLeft = left;
        mChildIndicatorRight = right;
        resolveChildIndicator();
    }

    public void setChildIndicatorBoundsRelative(int start, int end) {
        mChildIndicatorStart = start;
        mChildIndicatorEnd = end;
        resolveChildIndicator();
    }

    public void setGroupIndicator(Drawable groupIndicator) {
        mGroupIndicator = groupIndicator;
        if (mIndicatorRight == 0 && mGroupIndicator != null) {
            mIndicatorRight = mIndicatorLeft + mGroupIndicator.getIntrinsicWidth();
        }
    }

    public void setIndicatorBounds(int left, int right) {
        mIndicatorLeft = left;
        mIndicatorRight = right;
        resolveIndicator();
    }

    public void setIndicatorBoundsRelative(int start, int end) {
        mIndicatorStart = start;
        mIndicatorEnd = end;
        resolveIndicator();
    }

    /** The menu info of a long press on a group or child row. */
    public static class ExpandableListContextMenuInfo implements ContextMenu.ContextMenuInfo {
        public View targetView;
        public long packedPosition;
        public long id;

        public ExpandableListContextMenuInfo(View targetView, long packedPosition, long id) {
            this.targetView = targetView;
            this.packedPosition = packedPosition;
            this.id = id;
        }
    }

    static class SavedState extends BaseSavedState {
        ArrayList<ExpandableListConnector.GroupMetadata> expandedGroupMetadataList;

        SavedState(Parcelable superState, ArrayList<ExpandableListConnector.GroupMetadata> list) {
            super(superState);
            expandedGroupMetadataList = list;
        }

        private SavedState(Parcel in) {
            super(in);
            int n = in.readInt();
            expandedGroupMetadataList = new ArrayList<ExpandableListConnector.GroupMetadata>(Math.max(n, 0));
            for (int i = 0; i < n; i++) {
                expandedGroupMetadataList.add(ExpandableListConnector.GroupMetadata.CREATOR.createFromParcel(in));
            }
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            int n = expandedGroupMetadataList == null ? 0 : expandedGroupMetadataList.size();
            out.writeInt(n);
            for (int i = 0; i < n; i++) expandedGroupMetadataList.get(i).writeToParcel(out, flags);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) { return new SavedState(in); }

            public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState, mConnector != null ? mConnector.getExpandedGroupMetadataList() : null);
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        if (mConnector != null && ss.expandedGroupMetadataList != null) {
            mConnector.setExpandedGroupMetadataList(ss.expandedGroupMetadataList);
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() { return ExpandableListView.class.getName(); }
}
