package android.app;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Process;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Notifications have nowhere to show on the Switch. Posting one logs it (title,
 * text, channel) and keeps it in the active list; channels and groups are kept
 * per process as NotificationManagerService keeps them per package. As on
 * Android, apps targeting O and later must post to an existing channel, and a
 * channel at IMPORTANCE_NONE blocks its notifications.
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
    private final LinkedHashMap<String, StatusBarNotification> mActive = new LinkedHashMap<String, StatusBarNotification>();
    private final LinkedHashMap<String, NotificationChannel> mChannels = new LinkedHashMap<String, NotificationChannel>();
    private final LinkedHashMap<String, NotificationChannelGroup> mGroups =
            new LinkedHashMap<String, NotificationChannelGroup>();
    private String mDelegate;
    private Policy mPolicy = new Policy(0, 0, 0);

    NotificationManager() {}

    static synchronized NotificationManager getInstance() {
        if (sInstance == null) sInstance = new NotificationManager();
        return sInstance;
    }

    private static int targetSdk() {
        ApplicationInfo app = ActivityThread.sAppInfo;
        return app != null ? app.targetSdkVersion : 10000;
    }

    // ---------------------------------------------------------------- posting

    public void notify(int id, Notification notification) { notify(null, id, notification); }

    public void notify(String tag, int id, Notification notification) {
        if (notification == null) throw new NullPointerException("notification");
        String pkg = ActivityThread.sPackageName;
        synchronized (mActive) {
            String channelId = notification.getChannelId();
            NotificationChannel channel = channelId != null ? mChannels.get(channelId) : null;
            if (channel == null && targetSdk() >= 26) {
                Log.e("NotificationService", "No Channel found for pkg=" + pkg + ", channelId=" + channelId + ", id="
                        + id + ", tag=" + tag + ", opPkg=" + pkg + ", callingUid=" + Process.myUid()
                        + ", userId=0, incomingUserId=0, notificationUid=" + Process.myUid() + ", notification="
                        + notification);
                return;
            }
            if (channel != null && (channel.getImportance() == IMPORTANCE_NONE || isGroupBlocked(channel))) {
                Log.i(TAG, "blocked by channel " + channelId + ": " + (tag != null ? tag + "/" : "") + id);
                return;
            }
            StatusBarNotification sbn = new StatusBarNotification(pkg, pkg, id, tag, Process.myUid(),
                    Process.myPid(), 0, notification.clone(), UserHandle.SYSTEM, System.currentTimeMillis());
            mActive.remove(sbn.getKey());
            mActive.put(sbn.getKey(), sbn);
        }
        android.os.Bundle extras = notification.extras;
        Object title = extras != null ? extras.get(Notification.EXTRA_TITLE) : null;
        Object text = extras != null ? extras.get(Notification.EXTRA_TEXT) : null;
        if (title == null && text == null) text = notification.tickerText;
        Log.i(TAG, "notify " + (tag != null ? tag + "/" : "") + id + " [" + title + "] " + text
                + (notification.getChannelId() != null ? " channel=" + notification.getChannelId() : ""));
    }

    public void notifyAsPackage(String targetPackage, String tag, int id, Notification notification) {
        notify(tag, id, notification);
    }

    public void cancel(int id) { cancel(null, id); }

    public void cancel(String tag, int id) {
        synchronized (mActive) {
            String key = "0|" + ActivityThread.sPackageName + "|" + id + "|" + tag + "|" + Process.myUid();
            if (mActive.remove(key) != null) Log.i(TAG, "cancel " + (tag != null ? tag + "/" : "") + id);
        }
    }

    public void cancelAsPackage(String targetPackage, String tag, int id) { cancel(tag, id); }

    public void cancelAll() {
        synchronized (mActive) { mActive.clear(); }
    }

    public StatusBarNotification[] getActiveNotifications() {
        synchronized (mActive) {
            ArrayList<StatusBarNotification> out = new ArrayList<StatusBarNotification>();
            for (StatusBarNotification sbn : mActive.values()) out.add(sbn.clone());
            return out.toArray(new StatusBarNotification[0]);
        }
    }

    public void setNotificationDelegate(String delegate) { mDelegate = delegate; }

    public String getNotificationDelegate() { return mDelegate; }

    public boolean canNotifyAsPackage(String pkg) { return pkg != null && pkg.equals(ActivityThread.sPackageName); }

    public boolean canUseFullScreenIntent() { return true; }

    // ---------------------------------------------------------------- channels

    public void createNotificationChannelGroup(NotificationChannelGroup group) {
        List<NotificationChannelGroup> groups = new ArrayList<NotificationChannelGroup>();
        groups.add(group);
        createNotificationChannelGroups(groups);
    }

    public void createNotificationChannelGroups(List<NotificationChannelGroup> groups) {
        synchronized (mActive) {
            for (NotificationChannelGroup group : groups) {
                NotificationChannelGroup existing = mGroups.get(group.getId());
                if (existing != null) {
                    existing.setName(group.getName());
                    existing.setDescription(group.getDescription());
                } else {
                    mGroups.put(group.getId(), group.clone());
                }
            }
        }
    }

    public void createNotificationChannel(NotificationChannel channel) {
        List<NotificationChannel> channels = new ArrayList<NotificationChannel>();
        channels.add(channel);
        createNotificationChannels(channels);
    }

    public void createNotificationChannels(List<NotificationChannel> channels) {
        synchronized (mActive) {
            for (NotificationChannel channel : channels) {
                if (channel == null) throw new IllegalArgumentException("Channel cannot be null");
                if (channel.getName() == null || channel.getName().toString().trim().isEmpty()) {
                    throw new IllegalArgumentException("Channel name cannot be empty");
                }
                int importance = channel.getImportance();
                if (importance < IMPORTANCE_NONE || importance > IMPORTANCE_MAX) {
                    throw new IllegalArgumentException("Invalid importance level");
                }
                if (channel.getGroup() != null && !mGroups.containsKey(channel.getGroup())) {
                    throw new IllegalArgumentException("NotificationChannelGroup doesn't exist");
                }
                NotificationChannel existing = mChannels.get(channel.getId());
                if (existing != null) {
                    // As in PreferencesHelper: apps may rename, redescribe, lower importance and set a group once.
                    existing.setName(channel.getName());
                    existing.setDescription(channel.getDescription());
                    if (importance < existing.getImportance()) existing.setImportance(importance);
                    if (existing.getGroup() == null) existing.setGroup(channel.getGroup());
                } else {
                    mChannels.put(channel.getId(), copy(channel));
                }
            }
        }
    }

    public NotificationChannel getNotificationChannel(String channelId) {
        synchronized (mActive) {
            NotificationChannel c = mChannels.get(channelId);
            return c != null ? copy(c) : null;
        }
    }

    public NotificationChannel getNotificationChannel(String channelId, String conversationId) {
        synchronized (mActive) {
            for (NotificationChannel c : mChannels.values()) {
                if (channelId.equals(c.getParentChannelId()) && conversationId != null
                        && conversationId.equals(c.getConversationId())) {
                    return copy(c);
                }
            }
        }
        return getNotificationChannel(channelId);
    }

    public List<NotificationChannel> getNotificationChannels() {
        synchronized (mActive) {
            ArrayList<NotificationChannel> out = new ArrayList<NotificationChannel>();
            for (NotificationChannel c : mChannels.values()) out.add(copy(c));
            return out;
        }
    }

    public void deleteNotificationChannel(String channelId) {
        if (NotificationChannel.DEFAULT_CHANNEL_ID.equals(channelId)) {
            throw new IllegalArgumentException("Cannot delete default channel");
        }
        synchronized (mActive) {
            if (mChannels.remove(channelId) == null) return;
            for (java.util.Iterator<StatusBarNotification> it = mActive.values().iterator(); it.hasNext();) {
                if (channelId.equals(it.next().getNotification().getChannelId())) it.remove();
            }
        }
    }

    public NotificationChannelGroup getNotificationChannelGroup(String channelGroupId) {
        synchronized (mActive) {
            NotificationChannelGroup g = mGroups.get(channelGroupId);
            return g != null ? withChannels(g) : null;
        }
    }

    public List<NotificationChannelGroup> getNotificationChannelGroups() {
        synchronized (mActive) {
            ArrayList<NotificationChannelGroup> out = new ArrayList<NotificationChannelGroup>();
            for (NotificationChannelGroup g : mGroups.values()) out.add(withChannels(g));
            return out;
        }
    }

    public void deleteNotificationChannelGroup(String groupId) {
        synchronized (mActive) {
            if (mGroups.remove(groupId) == null) return;
            ArrayList<String> doomed = new ArrayList<String>();
            for (NotificationChannel c : mChannels.values()) if (groupId.equals(c.getGroup())) doomed.add(c.getId());
            for (String id : doomed) {
                mChannels.remove(id);
                for (java.util.Iterator<StatusBarNotification> it = mActive.values().iterator(); it.hasNext();) {
                    if (id.equals(it.next().getNotification().getChannelId())) it.remove();
                }
            }
        }
    }

    private NotificationChannelGroup withChannels(NotificationChannelGroup g) {
        NotificationChannelGroup out = g.clone();
        ArrayList<NotificationChannel> channels = new ArrayList<NotificationChannel>();
        for (NotificationChannel c : mChannels.values()) if (g.getId().equals(c.getGroup())) channels.add(copy(c));
        out.setChannels(channels);
        return out;
    }

    private boolean isGroupBlocked(NotificationChannel channel) {
        NotificationChannelGroup g = channel.getGroup() != null ? mGroups.get(channel.getGroup()) : null;
        return g != null && g.isBlocked();
    }

    private static NotificationChannel copy(NotificationChannel c) {
        NotificationChannel out = new NotificationChannel(c.getId(), c.getName(), c.getImportance());
        out.setDescription(c.getDescription());
        out.setGroup(c.getGroup());
        out.setShowBadge(c.canShowBadge());
        out.setSound(c.getSound(), c.getAudioAttributes());
        out.enableLights(c.shouldShowLights());
        out.setLightColor(c.getLightColor());
        out.setVibrationPattern(c.getVibrationPattern());
        if (c.getVibrationEffect() != null) out.setVibrationEffect(c.getVibrationEffect());
        out.enableVibration(c.shouldVibrate());
        out.setBypassDnd(c.canBypassDnd());
        out.setLockscreenVisibility(c.getLockscreenVisibility());
        out.setAllowBubbles(c.canBubble());
        out.setBlockable(c.isBlockable());
        if (c.getConversationId() != null) out.setConversationId(c.getParentChannelId(), c.getConversationId());
        return out;
    }

    // ---------------------------------------------------------------- state

    public int getImportance() { return IMPORTANCE_DEFAULT; }

    public boolean areNotificationsEnabled() { return true; }

    public boolean areNotificationsPaused() { return false; }

    public boolean areBubblesAllowed() { return false; }

    public boolean areBubblesEnabled() { return false; }

    public int getBubblePreference() { return BUBBLE_PREFERENCE_NONE; }

    public boolean isNotificationPolicyAccessGranted() { return false; }

    public boolean isNotificationListenerAccessGranted(ComponentName listener) { return false; }

    public boolean shouldHideSilentStatusBarIcons() { return false; }

    public boolean matchesCallFilter(Uri uri) { return true; }

    public boolean areAutomaticZenRulesUserManaged() { return false; }

    public Policy getNotificationPolicy() { return mPolicy; }

    public Policy getConsolidatedNotificationPolicy() { return mPolicy; }

    public void setNotificationPolicy(Policy policy) {
        throw new SecurityException("Notification policy access denied");
    }

    public final int getCurrentInterruptionFilter() { return INTERRUPTION_FILTER_ALL; }

    public final void setInterruptionFilter(int interruptionFilter) {
        throw new SecurityException("Notification policy access denied");
    }

    /** Do Not Disturb policy; nothing on the Switch changes it. */
    public static class Policy implements android.os.Parcelable {
        public static final int PRIORITY_CATEGORY_REMINDERS = 1 << 0;
        public static final int PRIORITY_CATEGORY_EVENTS = 1 << 1;
        public static final int PRIORITY_CATEGORY_MESSAGES = 1 << 2;
        public static final int PRIORITY_CATEGORY_CALLS = 1 << 3;
        public static final int PRIORITY_CATEGORY_REPEAT_CALLERS = 1 << 4;
        public static final int PRIORITY_CATEGORY_ALARMS = 1 << 5;
        public static final int PRIORITY_CATEGORY_MEDIA = 1 << 6;
        public static final int PRIORITY_CATEGORY_SYSTEM = 1 << 7;
        public static final int PRIORITY_CATEGORY_CONVERSATIONS = 1 << 8;
        public static final int PRIORITY_SENDERS_ANY = 0;
        public static final int PRIORITY_SENDERS_CONTACTS = 1;
        public static final int PRIORITY_SENDERS_STARRED = 2;
        public static final int SUPPRESSED_EFFECTS_UNSET = -1;
        public static final int CONVERSATION_SENDERS_ANYONE = 1;
        public static final int CONVERSATION_SENDERS_IMPORTANT = 2;
        public static final int CONVERSATION_SENDERS_NONE = 3;
        public static final int SUPPRESSED_EFFECT_SCREEN_OFF = 1 << 0;
        public static final int SUPPRESSED_EFFECT_SCREEN_ON = 1 << 1;
        public static final int SUPPRESSED_EFFECT_FULL_SCREEN_INTENT = 1 << 2;
        public static final int SUPPRESSED_EFFECT_LIGHTS = 1 << 3;
        public static final int SUPPRESSED_EFFECT_PEEK = 1 << 4;
        public static final int SUPPRESSED_EFFECT_STATUS_BAR = 1 << 5;
        public static final int SUPPRESSED_EFFECT_BADGE = 1 << 6;
        public static final int SUPPRESSED_EFFECT_AMBIENT = 1 << 7;
        public static final int SUPPRESSED_EFFECT_NOTIFICATION_LIST = 1 << 8;

        public final int priorityCategories;
        public final int priorityCallSenders;
        public final int priorityMessageSenders;
        public final int suppressedVisualEffects;
        public final int priorityConversationSenders;

        public Policy(int priorityCategories, int priorityCallSenders, int priorityMessageSenders) {
            this(priorityCategories, priorityCallSenders, priorityMessageSenders, SUPPRESSED_EFFECTS_UNSET);
        }

        public Policy(int priorityCategories, int priorityCallSenders, int priorityMessageSenders,
                int suppressedVisualEffects) {
            this(priorityCategories, priorityCallSenders, priorityMessageSenders, suppressedVisualEffects,
                    CONVERSATION_SENDERS_NONE);
        }

        public Policy(int priorityCategories, int priorityCallSenders, int priorityMessageSenders,
                int suppressedVisualEffects, int priorityConversationSenders) {
            this.priorityCategories = priorityCategories;
            this.priorityCallSenders = priorityCallSenders;
            this.priorityMessageSenders = priorityMessageSenders;
            this.suppressedVisualEffects = suppressedVisualEffects;
            this.priorityConversationSenders = priorityConversationSenders;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Policy)) return false;
            Policy other = (Policy) o;
            return other.priorityCategories == priorityCategories && other.priorityCallSenders == priorityCallSenders
                    && other.priorityMessageSenders == priorityMessageSenders
                    && other.suppressedVisualEffects == suppressedVisualEffects
                    && other.priorityConversationSenders == priorityConversationSenders;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(priorityCategories, priorityCallSenders, priorityMessageSenders,
                    suppressedVisualEffects, priorityConversationSenders);
        }

        @Override
        public String toString() {
            return "NotificationManager.Policy[priorityCategories=" + priorityCategoriesToString(priorityCategories)
                    + ",priorityCallSenders=" + prioritySendersToString(priorityCallSenders)
                    + ",priorityMessageSenders=" + prioritySendersToString(priorityMessageSenders)
                    + ",suppressedVisualEffects=" + suppressedEffectsToString(suppressedVisualEffects) + "]";
        }

        private static final String[] CATEGORY_NAMES = { "PRIORITY_CATEGORY_REMINDERS", "PRIORITY_CATEGORY_EVENTS",
                "PRIORITY_CATEGORY_MESSAGES", "PRIORITY_CATEGORY_CALLS", "PRIORITY_CATEGORY_REPEAT_CALLERS",
                "PRIORITY_CATEGORY_ALARMS", "PRIORITY_CATEGORY_MEDIA", "PRIORITY_CATEGORY_SYSTEM",
                "PRIORITY_CATEGORY_CONVERSATIONS" };
        private static final String[] EFFECT_NAMES = { "SUPPRESSED_EFFECT_SCREEN_OFF", "SUPPRESSED_EFFECT_SCREEN_ON",
                "SUPPRESSED_EFFECT_FULL_SCREEN_INTENT", "SUPPRESSED_EFFECT_LIGHTS", "SUPPRESSED_EFFECT_PEEK",
                "SUPPRESSED_EFFECT_STATUS_BAR", "SUPPRESSED_EFFECT_BADGE", "SUPPRESSED_EFFECT_AMBIENT",
                "SUPPRESSED_EFFECT_NOTIFICATION_LIST" };

        private static String bitsToString(int bits, String[] names) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < names.length; i++) {
                if ((bits & (1 << i)) == 0) continue;
                if (sb.length() > 0) sb.append(',');
                sb.append(names[i]);
            }
            return sb.toString();
        }

        public static String suppressedEffectsToString(int effects) {
            if (effects <= 0) return "";
            return bitsToString(effects, EFFECT_NAMES);
        }

        public static String priorityCategoriesToString(int priorityCategories) {
            return bitsToString(priorityCategories, CATEGORY_NAMES);
        }

        public static String prioritySendersToString(int prioritySenders) {
            switch (prioritySenders) {
                case PRIORITY_SENDERS_ANY: return "PRIORITY_SENDERS_ANY";
                case PRIORITY_SENDERS_CONTACTS: return "PRIORITY_SENDERS_CONTACTS";
                case PRIORITY_SENDERS_STARRED: return "PRIORITY_SENDERS_STARRED";
                default: return "PRIORITY_SENDERS_UNKNOWN_" + prioritySenders;
            }
        }

        public int describeContents() { return 0; }

        public void writeToParcel(android.os.Parcel dest, int flags) { dest.writeValue(this); }

        public static final android.os.Parcelable.Creator<Policy> CREATOR = new android.os.Parcelable.Creator<Policy>() {
            public Policy createFromParcel(android.os.Parcel in) { return (Policy) in.readValue(null); }
            public Policy[] newArray(int size) { return new Policy[size]; }
        };
    }

}
