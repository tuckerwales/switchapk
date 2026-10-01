package android.app;

import android.util.Log;
import java.util.HashMap;

/**
 * Notifications have nowhere to show on the Switch; posting one logs its title
 * and text so apps behave and tests can see them. TODO(WS4) channels.
 */
public class NotificationManager {
    private static final String TAG = "NotificationManager";
    public static final String ACTION_APP_BLOCK_STATE_CHANGED = "android.app.action.APP_BLOCK_STATE_CHANGED";
    public static final String ACTION_AUTOMATIC_ZEN_RULE = "android.app.action.AUTOMATIC_ZEN_RULE";
    public static final String ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED = "android.app.action.AUTOMATIC_ZEN_RULE_STATUS_CHANGED";
    public static final String ACTION_CONSOLIDATED_NOTIFICATION_POLICY_CHANGED = "android.app.action.CONSOLIDATED_NOTIFICATION_POLICY_CHANGED";
    public static final String ACTION_INTERRUPTION_FILTER_CHANGED = "android.app.action.INTERRUPTION_FILTER_CHANGED";
    public static final String ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED = "android.app.action.NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED";
    public static final String ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED = "android.app.action.NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED";
    public static final String ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED = "android.app.action.NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED";
    public static final String ACTION_NOTIFICATION_POLICY_CHANGED = "android.app.action.NOTIFICATION_POLICY_CHANGED";
    public static final int AUTOMATIC_RULE_STATUS_ACTIVATED = 4;
    public static final int AUTOMATIC_RULE_STATUS_DEACTIVATED = 5;
    public static final int AUTOMATIC_RULE_STATUS_DISABLED = 2;
    public static final int AUTOMATIC_RULE_STATUS_ENABLED = 1;
    public static final int AUTOMATIC_RULE_STATUS_REMOVED = 3;
    public static final int AUTOMATIC_RULE_STATUS_UNKNOWN = -1;
    public static final int BUBBLE_PREFERENCE_ALL = 1;
    public static final int BUBBLE_PREFERENCE_NONE = 0;
    public static final int BUBBLE_PREFERENCE_SELECTED = 2;
    public static final String EXTRA_AUTOMATIC_RULE_ID = "android.app.extra.AUTOMATIC_RULE_ID";
    public static final String EXTRA_AUTOMATIC_ZEN_RULE_ID = "android.app.extra.AUTOMATIC_ZEN_RULE_ID";
    public static final String EXTRA_AUTOMATIC_ZEN_RULE_STATUS = "android.app.extra.AUTOMATIC_ZEN_RULE_STATUS";
    public static final String EXTRA_BLOCKED_STATE = "android.app.extra.BLOCKED_STATE";
    public static final String EXTRA_NOTIFICATION_CHANNEL_GROUP_ID = "android.app.extra.NOTIFICATION_CHANNEL_GROUP_ID";
    public static final String EXTRA_NOTIFICATION_CHANNEL_ID = "android.app.extra.NOTIFICATION_CHANNEL_ID";
    public static final String EXTRA_NOTIFICATION_POLICY = "android.app.extra.NOTIFICATION_POLICY";
    public static final int IMPORTANCE_DEFAULT = 3;
    public static final int IMPORTANCE_HIGH = 4;
    public static final int IMPORTANCE_LOW = 2;
    public static final int IMPORTANCE_MAX = 5;
    public static final int IMPORTANCE_MIN = 1;
    public static final int IMPORTANCE_NONE = 0;
    public static final int IMPORTANCE_UNSPECIFIED = -1000;
    public static final int INTERRUPTION_FILTER_ALARMS = 4;
    public static final int INTERRUPTION_FILTER_ALL = 1;
    public static final int INTERRUPTION_FILTER_NONE = 3;
    public static final int INTERRUPTION_FILTER_PRIORITY = 2;
    public static final int INTERRUPTION_FILTER_UNKNOWN = 0;
    public static final String META_DATA_AUTOMATIC_RULE_TYPE = "android.service.zen.automatic.ruleType";
    public static final String META_DATA_RULE_INSTANCE_LIMIT = "android.service.zen.automatic.ruleInstanceLimit";

    private static NotificationManager sInstance;
    private final HashMap<String, Notification> mActive = new HashMap<String, Notification>();

    NotificationManager() {}

    static synchronized NotificationManager getInstance() {
        if (sInstance == null) sInstance = new NotificationManager();
        return sInstance;
    }

    public void notify(int id, Notification notification) { notify(null, id, notification); }

    public void notify(String tag, int id, Notification notification) {
        if (notification == null) throw new NullPointerException("notification");
        synchronized (mActive) { mActive.put(key(tag, id), notification); }
        android.os.Bundle extras = notification.extras;
        Object title = extras != null ? extras.get(Notification.EXTRA_TITLE) : null;
        Object text = extras != null ? extras.get(Notification.EXTRA_TEXT) : null;
        if (title == null && text == null) text = notification.tickerText;
        Log.i(TAG, "notify " + (tag != null ? tag + "/" : "") + id + " [" + title + "] " + text);
    }

    public void cancel(int id) { cancel(null, id); }

    public void cancel(String tag, int id) {
        synchronized (mActive) {
            if (mActive.remove(key(tag, id)) != null) Log.i(TAG, "cancel " + (tag != null ? tag + "/" : "") + id);
        }
    }

    public void cancelAll() {
        synchronized (mActive) { mActive.clear(); }
    }

    public int getImportance() { return IMPORTANCE_DEFAULT; }

    public boolean areNotificationsEnabled() { return true; }

    public boolean areNotificationsPaused() { return false; }

    public boolean areBubblesAllowed() { return false; }

    public boolean isNotificationPolicyAccessGranted() { return false; }

    public final int getCurrentInterruptionFilter() { return INTERRUPTION_FILTER_ALL; }

    public final void setInterruptionFilter(int interruptionFilter) {}

    private static String key(String tag, int id) { return (tag != null ? tag : "") + "#" + id; }
}
