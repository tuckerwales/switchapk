package android.accessibilityservice;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Describes an accessibility service. switchapk runs no accessibility
 * services, so AccessibilityManager never hands these out; the class exists
 * for apps that declare or inspect them.
 */
public class AccessibilityServiceInfo implements Parcelable {
    public static final int CAPABILITY_CAN_CONTROL_MAGNIFICATION = 16;
    public static final int CAPABILITY_CAN_PERFORM_GESTURES = 32;
    public static final int CAPABILITY_CAN_REQUEST_ENHANCED_WEB_ACCESSIBILITY = 4;
    public static final int CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS = 8;
    public static final int CAPABILITY_CAN_REQUEST_FINGERPRINT_GESTURES = 64;
    public static final int CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION = 2;
    public static final int CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT = 1;
    public static final int CAPABILITY_CAN_TAKE_SCREENSHOT = 128;
    public static final int DEFAULT = 1;
    public static final int FEEDBACK_ALL_MASK = -1;
    public static final int FEEDBACK_AUDIBLE = 4;
    public static final int FEEDBACK_BRAILLE = 32;
    public static final int FEEDBACK_GENERIC = 16;
    public static final int FEEDBACK_HAPTIC = 2;
    public static final int FEEDBACK_SPOKEN = 1;
    public static final int FEEDBACK_VISUAL = 8;
    public static final int FLAG_ENABLE_ACCESSIBILITY_VOLUME = 128;
    public static final int FLAG_INCLUDE_NOT_IMPORTANT_VIEWS = 2;
    public static final int FLAG_INPUT_METHOD_EDITOR = 32768;
    public static final int FLAG_REPORT_VIEW_IDS = 16;
    public static final int FLAG_REQUEST_2_FINGER_PASSTHROUGH = 8192;
    public static final int FLAG_REQUEST_ACCESSIBILITY_BUTTON = 256;
    public static final int FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY = 8;
    public static final int FLAG_REQUEST_FILTER_KEY_EVENTS = 32;
    public static final int FLAG_REQUEST_FINGERPRINT_GESTURES = 512;
    public static final int FLAG_REQUEST_MULTI_FINGER_GESTURES = 4096;
    public static final int FLAG_REQUEST_SHORTCUT_WARNING_DIALOG_SPOKEN_FEEDBACK = 1024;
    public static final int FLAG_REQUEST_TOUCH_EXPLORATION_MODE = 4;
    public static final int FLAG_RETRIEVE_INTERACTIVE_WINDOWS = 64;
    public static final int FLAG_SEND_MOTION_EVENTS = 16384;
    public static final int FLAG_SERVICE_HANDLES_DOUBLE_TAP = 2048;

    public int eventTypes;
    public int feedbackType;
    public int flags;
    public long notificationTimeout;
    public String[] packageNames;

    private int mNonInteractiveUiTimeout;
    private int mInteractiveUiTimeout;
    private int mMotionEventSources;

    public AccessibilityServiceInfo() {}

    public String getId() { return null; }
    public ResolveInfo getResolveInfo() { return null; }
    public String getSettingsActivityName() { return null; }
    public String getTileServiceName() { return null; }
    public boolean getCanRetrieveWindowContent() { return false; }
    public int getCapabilities() { return 0; }
    public int getMotionEventSources() { return mMotionEventSources; }
    public void setMotionEventSources(int motionEventSources) { mMotionEventSources = motionEventSources; }
    public CharSequence loadSummary(PackageManager packageManager) { return null; }
    public CharSequence loadIntro(PackageManager packageManager) { return null; }
    public String getDescription() { return null; }
    public String loadDescription(PackageManager packageManager) { return null; }
    public void setNonInteractiveUiTimeoutMillis(int timeout) { mNonInteractiveUiTimeout = timeout; }
    public int getNonInteractiveUiTimeoutMillis() { return mNonInteractiveUiTimeout; }
    public void setInteractiveUiTimeoutMillis(int timeout) { mInteractiveUiTimeout = timeout; }
    public int getInteractiveUiTimeoutMillis() { return mInteractiveUiTimeout; }
    public boolean isAccessibilityTool() { return false; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(eventTypes);
        out.writeInt(feedbackType);
        out.writeInt(this.flags);
        out.writeLong(notificationTimeout);
        out.writeInt(mNonInteractiveUiTimeout);
        out.writeInt(mInteractiveUiTimeout);
        out.writeInt(mMotionEventSources);
    }

    public static final Parcelable.Creator<AccessibilityServiceInfo> CREATOR =
            new Parcelable.Creator<AccessibilityServiceInfo>() {
        public AccessibilityServiceInfo createFromParcel(Parcel in) {
            AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            info.eventTypes = in.readInt();
            info.feedbackType = in.readInt();
            info.flags = in.readInt();
            info.notificationTimeout = in.readLong();
            info.mNonInteractiveUiTimeout = in.readInt();
            info.mInteractiveUiTimeout = in.readInt();
            info.mMotionEventSources = in.readInt();
            return info;
        }
        public AccessibilityServiceInfo[] newArray(int size) { return new AccessibilityServiceInfo[size]; }
    };

    @Override
    public String toString() {
        return "AccessibilityServiceInfo[eventTypes=" + eventTypes + ", feedbackType=" + feedbackTypeToString(feedbackType)
                + ", flags=" + flags + "]";
    }

    public static String feedbackTypeToString(int feedbackType) {
        StringBuilder b = new StringBuilder("[");
        String[] names = {"FEEDBACK_SPOKEN", "FEEDBACK_HAPTIC", "FEEDBACK_AUDIBLE", "FEEDBACK_VISUAL",
                "FEEDBACK_GENERIC", "FEEDBACK_BRAILLE"};
        for (int i = 0; i < names.length; i++) {
            if ((feedbackType & (1 << i)) == 0) continue;
            if (b.length() > 1) b.append(", ");
            b.append(names[i]);
        }
        return b.append(']').toString();
    }

    public static String flagToString(int flag) {
        switch (flag) {
            case DEFAULT: return "DEFAULT";
            case FLAG_INCLUDE_NOT_IMPORTANT_VIEWS: return "FLAG_INCLUDE_NOT_IMPORTANT_VIEWS";
            case FLAG_REQUEST_TOUCH_EXPLORATION_MODE: return "FLAG_REQUEST_TOUCH_EXPLORATION_MODE";
            case FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY: return "FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY";
            case FLAG_REPORT_VIEW_IDS: return "FLAG_REPORT_VIEW_IDS";
            case FLAG_REQUEST_FILTER_KEY_EVENTS: return "FLAG_REQUEST_FILTER_KEY_EVENTS";
            case FLAG_RETRIEVE_INTERACTIVE_WINDOWS: return "FLAG_RETRIEVE_INTERACTIVE_WINDOWS";
            case FLAG_ENABLE_ACCESSIBILITY_VOLUME: return "FLAG_ENABLE_ACCESSIBILITY_VOLUME";
            case FLAG_REQUEST_ACCESSIBILITY_BUTTON: return "FLAG_REQUEST_ACCESSIBILITY_BUTTON";
            case FLAG_REQUEST_FINGERPRINT_GESTURES: return "FLAG_REQUEST_FINGERPRINT_GESTURES";
            default: return null;
        }
    }

    public static String capabilityToString(int capability) {
        switch (capability) {
            case CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT: return "CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT";
            case CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION: return "CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION";
            case CAPABILITY_CAN_REQUEST_ENHANCED_WEB_ACCESSIBILITY:
                return "CAPABILITY_CAN_REQUEST_ENHANCED_WEB_ACCESSIBILITY";
            case CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS: return "CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS";
            case CAPABILITY_CAN_CONTROL_MAGNIFICATION: return "CAPABILITY_CAN_CONTROL_MAGNIFICATION";
            case CAPABILITY_CAN_PERFORM_GESTURES: return "CAPABILITY_CAN_PERFORM_GESTURES";
            case CAPABILITY_CAN_REQUEST_FINGERPRINT_GESTURES: return "CAPABILITY_CAN_REQUEST_FINGERPRINT_GESTURES";
            case CAPABILITY_CAN_TAKE_SCREENSHOT: return "CAPABILITY_CAN_TAKE_SCREENSHOT";
            default: return "UNKNOWN";
        }
    }
}
