package android.view.accessibility;

import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Accessibility node; a value holder only, since switchapk runs no accessibility services. */
public class AccessibilityNodeInfo implements Parcelable {
    public static final int ACTION_ACCESSIBILITY_FOCUS = 64;
    public static final String ACTION_ARGUMENT_COLUMN_INT = "android.view.accessibility.action.ARGUMENT_COLUMN_INT";
    public static final String ACTION_ARGUMENT_DIRECTION_INT = "android.view.accessibility.action.ARGUMENT_DIRECTION_INT";
    public static final String ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN = "ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN";
    public static final String ACTION_ARGUMENT_HTML_ELEMENT_STRING = "ACTION_ARGUMENT_HTML_ELEMENT_STRING";
    public static final String ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT = "ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT";
    public static final String ACTION_ARGUMENT_MOVE_WINDOW_X = "ACTION_ARGUMENT_MOVE_WINDOW_X";
    public static final String ACTION_ARGUMENT_MOVE_WINDOW_Y = "ACTION_ARGUMENT_MOVE_WINDOW_Y";
    public static final String ACTION_ARGUMENT_PRESS_AND_HOLD_DURATION_MILLIS_INT = "android.view.accessibility.action.ARGUMENT_PRESS_AND_HOLD_DURATION_MILLIS_INT";
    public static final String ACTION_ARGUMENT_PROGRESS_VALUE = "android.view.accessibility.action.ARGUMENT_PROGRESS_VALUE";
    public static final String ACTION_ARGUMENT_ROW_INT = "android.view.accessibility.action.ARGUMENT_ROW_INT";
    public static final String ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT = "android.view.accessibility.action.ARGUMENT_SCROLL_AMOUNT_FLOAT";
    public static final String ACTION_ARGUMENT_SELECTION_END_INT = "ACTION_ARGUMENT_SELECTION_END_INT";
    public static final String ACTION_ARGUMENT_SELECTION_START_INT = "ACTION_ARGUMENT_SELECTION_START_INT";
    public static final String ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE = "ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE";
    public static final int ACTION_CLEAR_ACCESSIBILITY_FOCUS = 128;
    public static final int ACTION_CLEAR_FOCUS = 2;
    public static final int ACTION_CLEAR_SELECTION = 8;
    public static final int ACTION_CLICK = 16;
    public static final int ACTION_COLLAPSE = 524288;
    public static final int ACTION_COPY = 16384;
    public static final int ACTION_CUT = 65536;
    public static final int ACTION_DISMISS = 1048576;
    public static final int ACTION_EXPAND = 262144;
    public static final int ACTION_FOCUS = 1;
    public static final int ACTION_LONG_CLICK = 32;
    public static final int ACTION_NEXT_AT_MOVEMENT_GRANULARITY = 256;
    public static final int ACTION_NEXT_HTML_ELEMENT = 1024;
    public static final int ACTION_PASTE = 32768;
    public static final int ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY = 512;
    public static final int ACTION_PREVIOUS_HTML_ELEMENT = 2048;
    public static final int ACTION_SCROLL_BACKWARD = 8192;
    public static final int ACTION_SCROLL_FORWARD = 4096;
    public static final int ACTION_SELECT = 4;
    public static final int ACTION_SET_SELECTION = 131072;
    public static final int ACTION_SET_TEXT = 2097152;
    public static final String EXTRA_DATA_RENDERING_INFO_KEY = "android.view.accessibility.extra.DATA_RENDERING_INFO_KEY";
    public static final String EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH = "android.view.accessibility.extra.DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH";
    public static final int EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_MAX_LENGTH = 20000;
    public static final String EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX = "android.view.accessibility.extra.DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX";
    public static final String EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY = "android.view.accessibility.extra.DATA_TEXT_CHARACTER_LOCATION_KEY";
    public static final int FLAG_PREFETCH_ANCESTORS = 1;
    public static final int FLAG_PREFETCH_DESCENDANTS_BREADTH_FIRST = 16;
    public static final int FLAG_PREFETCH_DESCENDANTS_DEPTH_FIRST = 8;
    public static final int FLAG_PREFETCH_DESCENDANTS_HYBRID = 4;
    public static final int FLAG_PREFETCH_SIBLINGS = 2;
    public static final int FLAG_PREFETCH_UNINTERRUPTIBLE = 32;
    public static final int FOCUS_ACCESSIBILITY = 2;
    public static final int FOCUS_INPUT = 1;
    public static final int MAX_NUMBER_OF_PREFETCHED_NODES = 50;
    public static final int MOVEMENT_GRANULARITY_CHARACTER = 1;
    public static final int MOVEMENT_GRANULARITY_LINE = 4;
    public static final int MOVEMENT_GRANULARITY_PAGE = 16;
    public static final int MOVEMENT_GRANULARITY_PARAGRAPH = 8;
    public static final int MOVEMENT_GRANULARITY_WORD = 2;

    private boolean mCheckable;
    private boolean mChecked;
    private boolean mFocusable;
    private boolean mFocused;
    private boolean mSelected;
    private boolean mClickable;
    private boolean mLongClickable;
    private boolean mEnabled;
    private boolean mPassword;
    private boolean mScrollable;
    private boolean mEditable;
    private boolean mVisibleToUser;
    private boolean mAccessibilityFocused;
    private boolean mContentInvalid;
    private boolean mContextClickable;
    private boolean mDismissable;
    private boolean mMultiline;
    private boolean mHeading;
    private boolean mScreenReaderFocusable;
    private boolean mShowingHintText;
    private boolean mTextEntryKey;
    private boolean mImportantForAccessibility;
    private CharSequence mClassName;
    private CharSequence mText;
    private CharSequence mContentDescription;
    private CharSequence mPackageName;
    private CharSequence mHintText;
    private CharSequence mError;
    private CharSequence mPaneTitle;
    private CharSequence mTooltipText;
    private CharSequence mStateDescription;
    private int mMaxTextLength;
    private int mMovementGranularities;
    private int mInputType;
    private int mLiveRegion;
    private int mDrawingOrder;
    private final Rect mBoundsInParent = new Rect();
    private final Rect mBoundsInScreen = new Rect();
    private final ArrayList<AccessibilityAction> mActions = new ArrayList<AccessibilityAction>();
    private String mViewIdResourceName;
    private int mChildCount;
    private int mTextSelectionStart = -1;
    private int mTextSelectionEnd = -1;
    private Bundle mExtras;

    public AccessibilityNodeInfo() {}

    public AccessibilityNodeInfo(View source) { setSource(source); }

    public AccessibilityNodeInfo(View root, int virtualDescendantId) { setSource(root, virtualDescendantId); }

    public AccessibilityNodeInfo(AccessibilityNodeInfo info) {
        mClassName = info.mClassName;
        mText = info.mText;
        mContentDescription = info.mContentDescription;
        mActions.addAll(info.mActions);
    }

    public static AccessibilityNodeInfo obtain(View source) { return new AccessibilityNodeInfo(source); }
    public static AccessibilityNodeInfo obtain(View root, int virtualDescendantId) {
        return new AccessibilityNodeInfo(root, virtualDescendantId);
    }
    public static AccessibilityNodeInfo obtain() { return new AccessibilityNodeInfo(); }
    public static AccessibilityNodeInfo obtain(AccessibilityNodeInfo info) { return new AccessibilityNodeInfo(info); }
    public void recycle() {}

    public void setSource(View source) {}
    public void setSource(View root, int virtualDescendantId) {}
    public void setParent(View parent) {}
    public void setParent(View root, int virtualDescendantId) {}
    public void addChild(View child) { mChildCount++; }
    public void addChild(View root, int virtualDescendantId) { mChildCount++; }
    public boolean removeChild(View child) { return false; }
    public boolean removeChild(View root, int virtualDescendantId) { return false; }
    public int getChildCount() { return mChildCount; }
    public AccessibilityNodeInfo getChild(int index) { return null; }
    public AccessibilityNodeInfo getParent() { return null; }
    public void setLabelFor(View labeled) {}
    public void setLabelFor(View root, int virtualDescendantId) {}
    public void setLabeledBy(View label) {}
    public void setLabeledBy(View root, int virtualDescendantId) {}
    public void setTraversalBefore(View view) {}
    public void setTraversalBefore(View root, int virtualDescendantId) {}
    public void setTraversalAfter(View view) {}
    public void setTraversalAfter(View root, int virtualDescendantId) {}
    public int getWindowId() { return -1; }
    public boolean refresh() { return false; }

    public void getBoundsInParent(Rect outBounds) { outBounds.set(mBoundsInParent); }
    public void setBoundsInParent(Rect bounds) { mBoundsInParent.set(bounds); }
    public void getBoundsInScreen(Rect outBounds) { outBounds.set(mBoundsInScreen); }
    public void setBoundsInScreen(Rect bounds) { mBoundsInScreen.set(bounds); }

    public int getActions() {
        int mask = 0;
        for (AccessibilityAction a : mActions) if (Integer.bitCount(a.getId()) == 1 && a.getId() > 0) mask |= a.getId();
        return mask;
    }

    public void addAction(AccessibilityAction action) {
        if (action == null) throw new IllegalArgumentException("action must not be null");
        mActions.remove(action);
        mActions.add(action);
    }

    public void addAction(int action) { addAction(new AccessibilityAction(action)); }
    public void removeAction(int action) { mActions.remove(new AccessibilityAction(action)); }
    public boolean removeAction(AccessibilityAction action) { return mActions.remove(action); }
    public List<AccessibilityAction> getActionList() { return mActions; }
    public boolean performAction(int action) { return false; }
    public boolean performAction(int action, Bundle arguments) { return false; }
    public String getViewIdResourceName() { return mViewIdResourceName; }
    public void setViewIdResourceName(String viewIdResName) { mViewIdResourceName = viewIdResName; }
    public int getTextSelectionStart() { return mTextSelectionStart; }
    public int getTextSelectionEnd() { return mTextSelectionEnd; }
    public void setTextSelection(int start, int end) {
        mTextSelectionStart = start;
        mTextSelectionEnd = end;
    }
    public Bundle getExtras() {
        if (mExtras == null) mExtras = new Bundle();
        return mExtras;
    }
    public List<String> getAvailableExtraData() { return new ArrayList<String>(); }
    public void setAvailableExtraData(List<String> extraDataKeys) {}

    public boolean isCheckable() { return mCheckable; }
    public void setCheckable(boolean value) { mCheckable = value; }
    public boolean isChecked() { return mChecked; }
    public void setChecked(boolean value) { mChecked = value; }
    public boolean isFocusable() { return mFocusable; }
    public void setFocusable(boolean value) { mFocusable = value; }
    public boolean isFocused() { return mFocused; }
    public void setFocused(boolean value) { mFocused = value; }
    public boolean isSelected() { return mSelected; }
    public void setSelected(boolean value) { mSelected = value; }
    public boolean isClickable() { return mClickable; }
    public void setClickable(boolean value) { mClickable = value; }
    public boolean isLongClickable() { return mLongClickable; }
    public void setLongClickable(boolean value) { mLongClickable = value; }
    public boolean isEnabled() { return mEnabled; }
    public void setEnabled(boolean value) { mEnabled = value; }
    public boolean isPassword() { return mPassword; }
    public void setPassword(boolean value) { mPassword = value; }
    public boolean isScrollable() { return mScrollable; }
    public void setScrollable(boolean value) { mScrollable = value; }
    public boolean isEditable() { return mEditable; }
    public void setEditable(boolean value) { mEditable = value; }
    public boolean isVisibleToUser() { return mVisibleToUser; }
    public void setVisibleToUser(boolean value) { mVisibleToUser = value; }
    public boolean isAccessibilityFocused() { return mAccessibilityFocused; }
    public void setAccessibilityFocused(boolean value) { mAccessibilityFocused = value; }
    public boolean isContentInvalid() { return mContentInvalid; }
    public void setContentInvalid(boolean value) { mContentInvalid = value; }
    public boolean isContextClickable() { return mContextClickable; }
    public void setContextClickable(boolean value) { mContextClickable = value; }
    public boolean isDismissable() { return mDismissable; }
    public void setDismissable(boolean value) { mDismissable = value; }
    public boolean isMultiline() { return mMultiline; }
    public void setMultiline(boolean value) { mMultiline = value; }
    public boolean isHeading() { return mHeading; }
    public void setHeading(boolean value) { mHeading = value; }
    public boolean isScreenReaderFocusable() { return mScreenReaderFocusable; }
    public void setScreenReaderFocusable(boolean value) { mScreenReaderFocusable = value; }
    public boolean isShowingHintText() { return mShowingHintText; }
    public void setShowingHintText(boolean value) { mShowingHintText = value; }
    public boolean isTextEntryKey() { return mTextEntryKey; }
    public void setTextEntryKey(boolean value) { mTextEntryKey = value; }
    public boolean isImportantForAccessibility() { return mImportantForAccessibility; }
    public void setImportantForAccessibility(boolean value) { mImportantForAccessibility = value; }
    public CharSequence getClassName() { return mClassName; }
    public void setClassName(CharSequence value) { mClassName = value; }
    public CharSequence getText() { return mText; }
    public void setText(CharSequence value) { mText = value; }
    public CharSequence getContentDescription() { return mContentDescription; }
    public void setContentDescription(CharSequence value) { mContentDescription = value; }
    public CharSequence getPackageName() { return mPackageName; }
    public void setPackageName(CharSequence value) { mPackageName = value; }
    public CharSequence getHintText() { return mHintText; }
    public void setHintText(CharSequence value) { mHintText = value; }
    public CharSequence getError() { return mError; }
    public void setError(CharSequence value) { mError = value; }
    public CharSequence getPaneTitle() { return mPaneTitle; }
    public void setPaneTitle(CharSequence value) { mPaneTitle = value; }
    public CharSequence getTooltipText() { return mTooltipText; }
    public void setTooltipText(CharSequence value) { mTooltipText = value; }
    public CharSequence getStateDescription() { return mStateDescription; }
    public void setStateDescription(CharSequence value) { mStateDescription = value; }
    public int getMaxTextLength() { return mMaxTextLength; }
    public void setMaxTextLength(int value) { mMaxTextLength = value; }
    public int getMovementGranularities() { return mMovementGranularities; }
    public void setMovementGranularities(int value) { mMovementGranularities = value; }
    public int getInputType() { return mInputType; }
    public void setInputType(int value) { mInputType = value; }
    public int getLiveRegion() { return mLiveRegion; }
    public void setLiveRegion(int value) { mLiveRegion = value; }
    public int getDrawingOrder() { return mDrawingOrder; }
    public void setDrawingOrder(int value) { mDrawingOrder = value; }

    public int describeContents() { return 0; }
    public void writeToParcel(Parcel parcel, int flags) {}

    @Override
    public String toString() { return "AccessibilityNodeInfo{" + mClassName + " " + mText + "}"; }

    public static final Parcelable.Creator<AccessibilityNodeInfo> CREATOR = new Parcelable.Creator<AccessibilityNodeInfo>() {
        public AccessibilityNodeInfo createFromParcel(Parcel parcel) { return new AccessibilityNodeInfo(); }
        public AccessibilityNodeInfo[] newArray(int size) { return new AccessibilityNodeInfo[size]; }
    };

    public static final class AccessibilityAction implements Parcelable {
        public static final AccessibilityAction ACTION_ACCESSIBILITY_FOCUS = new AccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        public static final AccessibilityAction ACTION_CLEAR_ACCESSIBILITY_FOCUS = new AccessibilityAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
        public static final AccessibilityAction ACTION_CLEAR_FOCUS = new AccessibilityAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS);
        public static final AccessibilityAction ACTION_CLEAR_SELECTION = new AccessibilityAction(AccessibilityNodeInfo.ACTION_CLEAR_SELECTION);
        public static final AccessibilityAction ACTION_CLICK = new AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK);
        public static final AccessibilityAction ACTION_COLLAPSE = new AccessibilityAction(AccessibilityNodeInfo.ACTION_COLLAPSE);
        public static final AccessibilityAction ACTION_CONTEXT_CLICK = new AccessibilityAction(android.R.id.accessibilityActionContextClick);
        public static final AccessibilityAction ACTION_COPY = new AccessibilityAction(AccessibilityNodeInfo.ACTION_COPY);
        public static final AccessibilityAction ACTION_CUT = new AccessibilityAction(AccessibilityNodeInfo.ACTION_CUT);
        public static final AccessibilityAction ACTION_DISMISS = new AccessibilityAction(AccessibilityNodeInfo.ACTION_DISMISS);
        public static final AccessibilityAction ACTION_DRAG_CANCEL = new AccessibilityAction(android.R.id.accessibilityActionDragCancel);
        public static final AccessibilityAction ACTION_DRAG_DROP = new AccessibilityAction(android.R.id.accessibilityActionDragDrop);
        public static final AccessibilityAction ACTION_DRAG_START = new AccessibilityAction(android.R.id.accessibilityActionDragStart);
        public static final AccessibilityAction ACTION_EXPAND = new AccessibilityAction(AccessibilityNodeInfo.ACTION_EXPAND);
        public static final AccessibilityAction ACTION_FOCUS = new AccessibilityAction(AccessibilityNodeInfo.ACTION_FOCUS);
        public static final AccessibilityAction ACTION_HIDE_TOOLTIP = new AccessibilityAction(android.R.id.accessibilityActionHideTooltip);
        public static final AccessibilityAction ACTION_IME_ENTER = new AccessibilityAction(android.R.id.accessibilityActionImeEnter);
        public static final AccessibilityAction ACTION_LONG_CLICK = new AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
        public static final AccessibilityAction ACTION_MOVE_WINDOW = new AccessibilityAction(android.R.id.accessibilityActionMoveWindow);
        public static final AccessibilityAction ACTION_NEXT_AT_MOVEMENT_GRANULARITY = new AccessibilityAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
        public static final AccessibilityAction ACTION_NEXT_HTML_ELEMENT = new AccessibilityAction(AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT);
        public static final AccessibilityAction ACTION_PAGE_DOWN = new AccessibilityAction(android.R.id.accessibilityActionPageDown);
        public static final AccessibilityAction ACTION_PAGE_LEFT = new AccessibilityAction(android.R.id.accessibilityActionPageLeft);
        public static final AccessibilityAction ACTION_PAGE_RIGHT = new AccessibilityAction(android.R.id.accessibilityActionPageRight);
        public static final AccessibilityAction ACTION_PAGE_UP = new AccessibilityAction(android.R.id.accessibilityActionPageUp);
        public static final AccessibilityAction ACTION_PASTE = new AccessibilityAction(AccessibilityNodeInfo.ACTION_PASTE);
        public static final AccessibilityAction ACTION_PRESS_AND_HOLD = new AccessibilityAction(android.R.id.accessibilityActionPressAndHold);
        public static final AccessibilityAction ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY = new AccessibilityAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY);
        public static final AccessibilityAction ACTION_PREVIOUS_HTML_ELEMENT = new AccessibilityAction(AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT);
        public static final AccessibilityAction ACTION_SCROLL_BACKWARD = new AccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        public static final AccessibilityAction ACTION_SCROLL_DOWN = new AccessibilityAction(android.R.id.accessibilityActionScrollDown);
        public static final AccessibilityAction ACTION_SCROLL_FORWARD = new AccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        public static final AccessibilityAction ACTION_SCROLL_IN_DIRECTION = new AccessibilityAction(android.R.id.accessibilityActionScrollInDirection);
        public static final AccessibilityAction ACTION_SCROLL_LEFT = new AccessibilityAction(android.R.id.accessibilityActionScrollLeft);
        public static final AccessibilityAction ACTION_SCROLL_RIGHT = new AccessibilityAction(android.R.id.accessibilityActionScrollRight);
        public static final AccessibilityAction ACTION_SCROLL_TO_POSITION = new AccessibilityAction(android.R.id.accessibilityActionScrollToPosition);
        public static final AccessibilityAction ACTION_SCROLL_UP = new AccessibilityAction(android.R.id.accessibilityActionScrollUp);
        public static final AccessibilityAction ACTION_SELECT = new AccessibilityAction(AccessibilityNodeInfo.ACTION_SELECT);
        public static final AccessibilityAction ACTION_SET_PROGRESS = new AccessibilityAction(android.R.id.accessibilityActionSetProgress);
        public static final AccessibilityAction ACTION_SET_SELECTION = new AccessibilityAction(AccessibilityNodeInfo.ACTION_SET_SELECTION);
        public static final AccessibilityAction ACTION_SET_TEXT = new AccessibilityAction(AccessibilityNodeInfo.ACTION_SET_TEXT);
        public static final AccessibilityAction ACTION_SHOW_ON_SCREEN = new AccessibilityAction(android.R.id.accessibilityActionShowOnScreen);
        public static final AccessibilityAction ACTION_SHOW_TEXT_SUGGESTIONS = new AccessibilityAction(android.R.id.accessibilityActionShowTextSuggestions);
        public static final AccessibilityAction ACTION_SHOW_TOOLTIP = new AccessibilityAction(android.R.id.accessibilityActionShowTooltip);

        private final int mActionId;
        private final CharSequence mLabel;

        public AccessibilityAction(int actionId, CharSequence label) {
            mActionId = actionId;
            mLabel = label;
        }

        AccessibilityAction(int standardActionId) { this(standardActionId, null); }

        public int getId() { return mActionId; }
        public CharSequence getLabel() { return mLabel; }

        @Override
        public int hashCode() { return mActionId; }

        @Override
        public boolean equals(Object other) {
            return other instanceof AccessibilityAction && ((AccessibilityAction) other).mActionId == mActionId;
        }

        @Override
        public String toString() { return "AccessibilityAction: " + mActionId + " - " + mLabel; }

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(mActionId);
            out.writeCharSequence(mLabel);
        }

        public static final Parcelable.Creator<AccessibilityAction> CREATOR = new Parcelable.Creator<AccessibilityAction>() {
            public AccessibilityAction createFromParcel(Parcel in) {
                return new AccessibilityAction(in.readInt(), in.readCharSequence());
            }
            public AccessibilityAction[] newArray(int size) { return new AccessibilityAction[size]; }
        };
    }
}
