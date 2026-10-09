package android.view.accessibility;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Accessibility is always disabled on switchapk; listeners are stored and never called. */
public final class AccessibilityManager {
    public static final int FLAG_CONTENT_CONTROLS = 4;
    public static final int FLAG_CONTENT_ICONS = 1;
    public static final int FLAG_CONTENT_TEXT = 2;

    private static AccessibilityManager sInstance;
    private final ArrayList<Object> mListeners = new ArrayList<Object>();

    public interface AccessibilityStateChangeListener {
        void onAccessibilityStateChanged(boolean enabled);
    }

    public interface TouchExplorationStateChangeListener {
        void onTouchExplorationStateChanged(boolean enabled);
    }

    private AccessibilityManager() {}

    /** framework-internal (hidden in AOSP). */
    public static synchronized AccessibilityManager getInstance(Context context) {
        if (sInstance == null) sInstance = new AccessibilityManager();
        return sInstance;
    }

    public boolean isEnabled() { return false; }
    public boolean isTouchExplorationEnabled() { return false; }
    public void sendAccessibilityEvent(AccessibilityEvent event) {
        throw new IllegalStateException("Accessibility off. Did you forget to check that?");
    }
    public void interrupt() {}
    @Deprecated
    public List<ServiceInfo> getAccessibilityServiceList() { return Collections.emptyList(); }
    public List<AccessibilityServiceInfo> getInstalledAccessibilityServiceList() { return Collections.emptyList(); }
    public List<AccessibilityServiceInfo> getEnabledAccessibilityServiceList(int feedbackTypeFlags) {
        return Collections.emptyList();
    }
    public boolean addAccessibilityStateChangeListener(AccessibilityStateChangeListener listener) {
        return mListeners.add(listener);
    }
    public void addAccessibilityStateChangeListener(AccessibilityStateChangeListener listener, Handler handler) {
        mListeners.add(listener);
    }
    public boolean removeAccessibilityStateChangeListener(AccessibilityStateChangeListener listener) {
        return mListeners.remove(listener);
    }
    public boolean addTouchExplorationStateChangeListener(TouchExplorationStateChangeListener listener) {
        return mListeners.add(listener);
    }
    public void addTouchExplorationStateChangeListener(TouchExplorationStateChangeListener listener, Handler handler) {
        mListeners.add(listener);
    }
    public boolean removeTouchExplorationStateChangeListener(TouchExplorationStateChangeListener listener) {
        return mListeners.remove(listener);
    }
    public boolean isRequestFromAccessibilityTool() { return false; }
    public int getRecommendedTimeoutMillis(int originalTimeout, int uiContentFlags) { return originalTimeout; }
    public int getAccessibilityFocusStrokeWidth() { return 4; }
    public int getAccessibilityFocusColor() { return 0xff4285f4; }
    public boolean isAudioDescriptionRequested() { return false; }
    public static boolean isAccessibilityButtonSupported() { return false; }
}
