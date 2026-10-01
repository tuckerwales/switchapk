package android.view.accessibility;

import android.os.Parcel;
import android.os.Parcelable;

/** Accessibility event; created and dispatched but never delivered to a service. */
public final class AccessibilityEvent extends AccessibilityRecord implements Parcelable {
    public static final int CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION = 4;
    public static final int CONTENT_CHANGE_TYPE_CONTENT_INVALID = 1024;
    public static final int CONTENT_CHANGE_TYPE_DRAG_CANCELLED = 512;
    public static final int CONTENT_CHANGE_TYPE_DRAG_DROPPED = 256;
    public static final int CONTENT_CHANGE_TYPE_DRAG_STARTED = 128;
    public static final int CONTENT_CHANGE_TYPE_ENABLED = 4096;
    public static final int CONTENT_CHANGE_TYPE_ERROR = 2048;
    public static final int CONTENT_CHANGE_TYPE_PANE_APPEARED = 16;
    public static final int CONTENT_CHANGE_TYPE_PANE_DISAPPEARED = 32;
    public static final int CONTENT_CHANGE_TYPE_PANE_TITLE = 8;
    public static final int CONTENT_CHANGE_TYPE_STATE_DESCRIPTION = 64;
    public static final int CONTENT_CHANGE_TYPE_SUBTREE = 1;
    public static final int CONTENT_CHANGE_TYPE_TEXT = 2;
    public static final int CONTENT_CHANGE_TYPE_UNDEFINED = 0;
    public static final int INVALID_POSITION = -1;
    public static final int MAX_TEXT_LENGTH = 500;
    public static final int SPEECH_STATE_LISTENING_END = 8;
    public static final int SPEECH_STATE_LISTENING_START = 4;
    public static final int SPEECH_STATE_SPEAKING_END = 2;
    public static final int SPEECH_STATE_SPEAKING_START = 1;
    public static final int TYPES_ALL_MASK = -1;
    public static final int TYPE_ANNOUNCEMENT = 16384;
    public static final int TYPE_ASSIST_READING_CONTEXT = 16777216;
    public static final int TYPE_GESTURE_DETECTION_END = 524288;
    public static final int TYPE_GESTURE_DETECTION_START = 262144;
    public static final int TYPE_NOTIFICATION_STATE_CHANGED = 64;
    public static final int TYPE_SPEECH_STATE_CHANGE = 33554432;
    public static final int TYPE_TOUCH_EXPLORATION_GESTURE_END = 1024;
    public static final int TYPE_TOUCH_EXPLORATION_GESTURE_START = 512;
    public static final int TYPE_TOUCH_INTERACTION_END = 2097152;
    public static final int TYPE_TOUCH_INTERACTION_START = 1048576;
    public static final int TYPE_VIEW_ACCESSIBILITY_FOCUSED = 32768;
    public static final int TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED = 65536;
    public static final int TYPE_VIEW_CLICKED = 1;
    public static final int TYPE_VIEW_CONTEXT_CLICKED = 8388608;
    public static final int TYPE_VIEW_FOCUSED = 8;
    public static final int TYPE_VIEW_HOVER_ENTER = 128;
    public static final int TYPE_VIEW_HOVER_EXIT = 256;
    public static final int TYPE_VIEW_LONG_CLICKED = 2;
    public static final int TYPE_VIEW_SCROLLED = 4096;
    public static final int TYPE_VIEW_SELECTED = 4;
    public static final int TYPE_VIEW_TARGETED_BY_SCROLL = 67108864;
    public static final int TYPE_VIEW_TEXT_CHANGED = 16;
    public static final int TYPE_VIEW_TEXT_SELECTION_CHANGED = 8192;
    public static final int TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY = 131072;
    public static final int TYPE_WINDOWS_CHANGED = 4194304;
    public static final int TYPE_WINDOW_CONTENT_CHANGED = 2048;
    public static final int TYPE_WINDOW_STATE_CHANGED = 32;
    public static final int WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED = 128;
    public static final int WINDOWS_CHANGE_ACTIVE = 32;
    public static final int WINDOWS_CHANGE_ADDED = 1;
    public static final int WINDOWS_CHANGE_BOUNDS = 8;
    public static final int WINDOWS_CHANGE_CHILDREN = 512;
    public static final int WINDOWS_CHANGE_FOCUSED = 64;
    public static final int WINDOWS_CHANGE_LAYER = 16;
    public static final int WINDOWS_CHANGE_PARENT = 256;
    public static final int WINDOWS_CHANGE_PIP = 1024;
    public static final int WINDOWS_CHANGE_REMOVED = 2;
    public static final int WINDOWS_CHANGE_TITLE = 4;

    private int mEventType;
    private CharSequence mPackageName;
    private long mEventTime;
    private int mContentChangeTypes;
    private int mAction;
    private int mMovementGranularity;
    private int mWindowChanges;

    public AccessibilityEvent() {}

    public AccessibilityEvent(int eventType) { mEventType = eventType; }

    public AccessibilityEvent(AccessibilityEvent event) {
        init(event);
        mEventType = event.mEventType;
        mPackageName = event.mPackageName;
        mEventTime = event.mEventTime;
        mContentChangeTypes = event.mContentChangeTypes;
        mAction = event.mAction;
        mMovementGranularity = event.mMovementGranularity;
    }

    public static AccessibilityEvent obtain(int eventType) { return new AccessibilityEvent(eventType); }
    public static AccessibilityEvent obtain(AccessibilityEvent event) { return new AccessibilityEvent(event); }
    public static AccessibilityEvent obtain() { return new AccessibilityEvent(); }

    public void appendRecord(AccessibilityRecord record) {}
    public AccessibilityRecord getRecord(int index) { throw new IndexOutOfBoundsException(); }
    public int getRecordCount() { return 0; }
    public int getContentChangeTypes() { return mContentChangeTypes; }
    public void setContentChangeTypes(int changeTypes) { mContentChangeTypes = changeTypes; }
    public int getWindowChanges() { return mWindowChanges; }
    public int getEventType() { return mEventType; }
    public void setEventType(int eventType) { mEventType = eventType; }
    public long getEventTime() { return mEventTime; }
    public void setEventTime(long eventTime) { mEventTime = eventTime; }
    public CharSequence getPackageName() { return mPackageName; }
    public void setPackageName(CharSequence packageName) { mPackageName = packageName; }
    public void setMovementGranularity(int granularity) { mMovementGranularity = granularity; }
    public int getMovementGranularity() { return mMovementGranularity; }
    public void setAction(int action) { mAction = action; }
    public int getAction() { return mAction; }
    public boolean isAccessibilityDataSensitive() { return false; }
    public void setAccessibilityDataSensitive(boolean accessibilityDataSensitive) {}

    public static String eventTypeToString(int eventType) { return "0x" + Integer.toHexString(eventType); }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel parcel, int flags) { parcel.writeInt(mEventType); }

    public static final Parcelable.Creator<AccessibilityEvent> CREATOR = new Parcelable.Creator<AccessibilityEvent>() {
        public AccessibilityEvent createFromParcel(Parcel parcel) { return new AccessibilityEvent(parcel.readInt()); }
        public AccessibilityEvent[] newArray(int size) { return new AccessibilityEvent[size]; }
    };
}
