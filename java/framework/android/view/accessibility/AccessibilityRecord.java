package android.view.accessibility;

import android.os.Parcelable;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Data of an accessibility event. No accessibility services run on switchapk; this only stores values. */
public class AccessibilityRecord {
    boolean mChecked;
    boolean mEnabled;
    boolean mPassword;
    boolean mFullScreen;
    boolean mScrollable;
    int mItemCount;
    int mCurrentItemIndex;
    int mFromIndex;
    int mToIndex;
    int mScrollX;
    int mScrollY;
    int mScrollDeltaX;
    int mScrollDeltaY;
    int mMaxScrollX;
    int mMaxScrollY;
    int mAddedCount;
    int mRemovedCount;
    CharSequence mClassName;
    CharSequence mBeforeText;
    CharSequence mContentDescription;
    Parcelable mParcelableData;
    final ArrayList<CharSequence> mText = new ArrayList<CharSequence>();
    View mSourceView;

    public AccessibilityRecord() {}

    public AccessibilityRecord(AccessibilityRecord record) { init(record); }

    void init(AccessibilityRecord record) {
        mChecked = record.mChecked;
        mEnabled = record.mEnabled;
        mPassword = record.mPassword;
        mFullScreen = record.mFullScreen;
        mScrollable = record.mScrollable;
        mItemCount = record.mItemCount;
        mCurrentItemIndex = record.mCurrentItemIndex;
        mFromIndex = record.mFromIndex;
        mToIndex = record.mToIndex;
        mScrollX = record.mScrollX;
        mScrollY = record.mScrollY;
        mScrollDeltaX = record.mScrollDeltaX;
        mScrollDeltaY = record.mScrollDeltaY;
        mMaxScrollX = record.mMaxScrollX;
        mMaxScrollY = record.mMaxScrollY;
        mAddedCount = record.mAddedCount;
        mRemovedCount = record.mRemovedCount;
        mClassName = record.mClassName;
        mBeforeText = record.mBeforeText;
        mContentDescription = record.mContentDescription;
        mParcelableData = record.mParcelableData;
        mText.clear();
        mText.addAll(record.mText);
        mSourceView = record.mSourceView;
    }

    public void setSource(View source) { mSourceView = source; }
    public void setSource(View root, int virtualDescendantId) { mSourceView = root; }
    public AccessibilityNodeInfo getSource() { return mSourceView != null ? AccessibilityNodeInfo.obtain(mSourceView) : null; }
    public AccessibilityNodeInfo getSource(int prefetchingStrategy) { return getSource(); }
    public int getDisplayId() { return 0; }
    public int getWindowId() { return -1; }
    public List<CharSequence> getText() { return mText; }

    public boolean isChecked() { return mChecked; }
    public void setChecked(boolean value) { mChecked = value; }
    public boolean isEnabled() { return mEnabled; }
    public void setEnabled(boolean value) { mEnabled = value; }
    public boolean isPassword() { return mPassword; }
    public void setPassword(boolean value) { mPassword = value; }
    public boolean isFullScreen() { return mFullScreen; }
    public void setFullScreen(boolean value) { mFullScreen = value; }
    public boolean isScrollable() { return mScrollable; }
    public void setScrollable(boolean value) { mScrollable = value; }
    public int getItemCount() { return mItemCount; }
    public void setItemCount(int value) { mItemCount = value; }
    public int getCurrentItemIndex() { return mCurrentItemIndex; }
    public void setCurrentItemIndex(int value) { mCurrentItemIndex = value; }
    public int getFromIndex() { return mFromIndex; }
    public void setFromIndex(int value) { mFromIndex = value; }
    public int getToIndex() { return mToIndex; }
    public void setToIndex(int value) { mToIndex = value; }
    public int getScrollX() { return mScrollX; }
    public void setScrollX(int value) { mScrollX = value; }
    public int getScrollY() { return mScrollY; }
    public void setScrollY(int value) { mScrollY = value; }
    public int getScrollDeltaX() { return mScrollDeltaX; }
    public void setScrollDeltaX(int value) { mScrollDeltaX = value; }
    public int getScrollDeltaY() { return mScrollDeltaY; }
    public void setScrollDeltaY(int value) { mScrollDeltaY = value; }
    public int getMaxScrollX() { return mMaxScrollX; }
    public void setMaxScrollX(int value) { mMaxScrollX = value; }
    public int getMaxScrollY() { return mMaxScrollY; }
    public void setMaxScrollY(int value) { mMaxScrollY = value; }
    public int getAddedCount() { return mAddedCount; }
    public void setAddedCount(int value) { mAddedCount = value; }
    public int getRemovedCount() { return mRemovedCount; }
    public void setRemovedCount(int value) { mRemovedCount = value; }
    public CharSequence getClassName() { return mClassName; }
    public void setClassName(CharSequence value) { mClassName = value; }
    public CharSequence getBeforeText() { return mBeforeText; }
    public void setBeforeText(CharSequence value) { mBeforeText = value; }
    public CharSequence getContentDescription() { return mContentDescription; }
    public void setContentDescription(CharSequence value) { mContentDescription = value; }
    public Parcelable getParcelableData() { return mParcelableData; }
    public void setParcelableData(Parcelable value) { mParcelableData = value; }

    public static AccessibilityRecord obtain(AccessibilityRecord record) { return new AccessibilityRecord(record); }
    public static AccessibilityRecord obtain() { return new AccessibilityRecord(); }
    public void recycle() {}

    @Override
    public String toString() { return "AccessibilityRecord [ClassName: " + mClassName + "; Text: " + mText + "]"; }
}
