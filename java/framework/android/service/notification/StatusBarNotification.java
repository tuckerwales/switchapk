package android.service.notification;

import android.app.Notification;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;

/** A posted notification, as NotificationManager.getActiveNotifications returns it. */
public class StatusBarNotification implements Parcelable {
    private final String pkg;
    private final int id;
    private final String tag;
    private final String key;
    private String groupKey;
    private String overrideGroupKey;
    private final int uid;
    private final String opPkg;
    private final int initialPid;
    private final Notification notification;
    private final UserHandle user;
    private final long postTime;

    public StatusBarNotification(String pkg, String opPkg, int id, String tag, int uid, int initialPid, int score,
            Notification notification, UserHandle user, long postTime) {
        this.pkg = pkg;
        this.opPkg = opPkg;
        this.id = id;
        this.tag = tag;
        this.uid = uid;
        this.initialPid = initialPid;
        this.notification = notification;
        this.user = user;
        this.postTime = postTime;
        this.key = 0 + "|" + pkg + "|" + id + "|" + tag + "|" + uid;
        this.groupKey = groupKey();
    }

    public StatusBarNotification(Parcel in) {
        StatusBarNotification o = (StatusBarNotification) in.readValue(null);
        pkg = o.pkg;
        opPkg = o.opPkg;
        id = o.id;
        tag = o.tag;
        uid = o.uid;
        initialPid = o.initialPid;
        notification = o.notification;
        user = o.user;
        postTime = o.postTime;
        key = o.key;
        groupKey = o.groupKey;
        overrideGroupKey = o.overrideGroupKey;
    }

    private String groupKey() {
        if (overrideGroupKey != null) return 0 + "|" + pkg + "|g:" + overrideGroupKey;
        String group = notification.getGroup();
        String sortKey = notification.getSortKey();
        if (group == null && sortKey == null) return key;
        return 0 + "|" + pkg + "|" + (group == null ? "c:" + notification.getChannelId() : "g:" + group);
    }

    public boolean isGroup() { return overrideGroupKey != null || isAppGroup(); }

    public boolean isAppGroup() { return notification.getGroup() != null || notification.getSortKey() != null; }

    public void writeToParcel(Parcel out, int flags) { out.writeValue(clone()); }

    public int describeContents() { return 0; }

    @Override
    public StatusBarNotification clone() {
        StatusBarNotification c = new StatusBarNotification(pkg, opPkg, id, tag, uid, initialPid, 0,
                notification.clone(), user, postTime);
        c.setOverrideGroupKey(overrideGroupKey);
        return c;
    }

    @Override
    public String toString() {
        return "StatusBarNotification(pkg=" + pkg + " user=" + user + " id=" + id + " tag=" + tag + " key=" + key
                + ": " + notification + ")";
    }

    public boolean isOngoing() { return (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0; }

    public boolean isClearable() {
        return (notification.flags & Notification.FLAG_ONGOING_EVENT) == 0
                && (notification.flags & Notification.FLAG_NO_CLEAR) == 0;
    }

    @Deprecated
    public int getUserId() { return 0; }

    public String getPackageName() { return pkg; }

    public int getId() { return id; }

    public String getTag() { return tag; }

    public int getUid() { return uid; }

    public String getOpPkg() { return opPkg; }

    public Notification getNotification() { return notification; }

    public UserHandle getUser() { return user; }

    public long getPostTime() { return postTime; }

    public String getKey() { return key; }

    public String getGroupKey() { return groupKey; }

    public void setOverrideGroupKey(String overrideGroupKey) {
        this.overrideGroupKey = overrideGroupKey;
        groupKey = groupKey();
    }

    public String getOverrideGroupKey() { return overrideGroupKey; }

    public static final Parcelable.Creator<StatusBarNotification> CREATOR =
            new Parcelable.Creator<StatusBarNotification>() {
                public StatusBarNotification createFromParcel(Parcel parcel) { return new StatusBarNotification(parcel); }
                public StatusBarNotification[] newArray(int size) { return new StatusBarNotification[size]; }
            };
}
