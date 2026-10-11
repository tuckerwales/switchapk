package android.app;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserHandle;
import java.util.HashMap;

/**
 * Port of AOSP PendingIntent against an in-process record table standing in for
 * the AMS one. Identity follows AOSP: the same kind, request code, filter-equal
 * intent and flags give the same token, and FLAG_NO_CREATE, FLAG_CANCEL_CURRENT,
 * FLAG_UPDATE_CURRENT and FLAG_ONE_SHOT act on it as they do there. Apps
 * targeting S and later must say FLAG_IMMUTABLE or FLAG_MUTABLE.
 */
public final class PendingIntent implements Parcelable {
    public static final int FLAG_ONE_SHOT = 1 << 30;
    public static final int FLAG_NO_CREATE = 1 << 29;
    public static final int FLAG_CANCEL_CURRENT = 1 << 28;
    public static final int FLAG_UPDATE_CURRENT = 1 << 27;
    public static final int FLAG_IMMUTABLE = 1 << 26;
    public static final int FLAG_MUTABLE = 1 << 25;
    public static final int FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT = 1 << 24;

    static final int TYPE_BROADCAST = 1;
    static final int TYPE_ACTIVITY = 2;
    static final int TYPE_ACTIVITY_RESULT = 3;
    static final int TYPE_SERVICE = 4;
    static final int TYPE_FOREGROUND_SERVICE = 5;

    private static final HashMap<Key, Record> sRecords = new HashMap<Key, Record>();

    private final Record mTarget;

    public static class CanceledException extends android.util.AndroidException {
        public CanceledException() {}
        public CanceledException(String name) { super(name); }
        public CanceledException(Exception cause) { super(cause); }
    }

    public interface OnFinished {
        void onSendFinished(PendingIntent pendingIntent, Intent intent, int resultCode, String resultData,
                Bundle resultExtras);
    }

    /** The AMS PendingIntentRecord.Key. */
    private static final class Key {
        final int type;
        final int requestCode;
        final Intent intent;
        final int flags;
        final Activity activity;
        final int hash;

        Key(int type, int requestCode, Intent intent, int flags, Activity activity) {
            this.type = type;
            this.requestCode = requestCode;
            this.intent = intent;
            this.flags = flags;
            this.activity = activity;
            int h = 23;
            h = 31 * h + type;
            h = 31 * h + requestCode;
            h = 31 * h + flags;
            h = 31 * h + (intent != null ? intent.filterHashCode() : 0);
            h = 31 * h + (activity != null ? activity.hashCode() : 0);
            hash = h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            if (type != k.type || requestCode != k.requestCode || flags != k.flags || activity != k.activity) return false;
            return intent == null ? k.intent == null : intent.filterEquals(k.intent);
        }

        @Override
        public int hashCode() { return hash; }
    }

    /** The AMS PendingIntentRecord. */
    static final class Record {
        final Key key;
        Intent intent;
        Intent[] allIntents;
        Bundle options;
        volatile boolean canceled;

        Record(Key key, Intent intent, Intent[] allIntents, Bundle options) {
            this.key = key;
            this.intent = intent;
            this.allIntents = allIntents;
            this.options = options;
        }
    }

    PendingIntent(Record target) { mTarget = target; }

    public static PendingIntent getActivity(Context context, int requestCode, Intent intent, int flags) {
        return getActivity(context, requestCode, intent, flags, null);
    }

    public static PendingIntent getActivity(Context context, int requestCode, Intent intent, int flags,
            Bundle options) {
        return get(TYPE_ACTIVITY, requestCode, new Intent[] { intent }, flags, options, null);
    }

    public static PendingIntent getActivities(Context context, int requestCode, Intent[] intents, int flags) {
        return getActivities(context, requestCode, intents, flags, null);
    }

    public static PendingIntent getActivities(Context context, int requestCode, Intent[] intents, int flags,
            Bundle options) {
        if (intents == null || intents.length == 0) throw new IllegalArgumentException("intents is empty");
        return get(TYPE_ACTIVITY, requestCode, intents, flags, options, null);
    }

    public static PendingIntent getBroadcast(Context context, int requestCode, Intent intent, int flags) {
        return get(TYPE_BROADCAST, requestCode, new Intent[] { intent }, flags, null, null);
    }

    public static PendingIntent getService(Context context, int requestCode, Intent intent, int flags) {
        return get(TYPE_SERVICE, requestCode, new Intent[] { intent }, flags, null, null);
    }

    public static PendingIntent getForegroundService(Context context, int requestCode, Intent intent, int flags) {
        return get(TYPE_FOREGROUND_SERVICE, requestCode, new Intent[] { intent }, flags, null, null);
    }

    /** framework-internal. Activity.createPendingResult. */
    static PendingIntent getActivityResult(Activity activity, int requestCode, Intent data, int flags) {
        return get(TYPE_ACTIVITY_RESULT, requestCode, new Intent[] { data != null ? data : new Intent() }, flags,
                null, activity);
    }

    private static PendingIntent get(int type, int requestCode, Intent[] intents, int flags, Bundle options,
            Activity activity) {
        checkFlags(flags);
        Intent[] copies = new Intent[intents.length];
        for (int i = 0; i < intents.length; i++) {
            if (intents[i] == null) throw new NullPointerException("intent");
            copies[i] = new Intent(intents[i]);
        }
        Intent last = copies[copies.length - 1];
        boolean noCreate = (flags & FLAG_NO_CREATE) != 0;
        boolean cancelCurrent = (flags & FLAG_CANCEL_CURRENT) != 0;
        boolean updateCurrent = (flags & FLAG_UPDATE_CURRENT) != 0;
        int keyFlags = flags & ~(FLAG_NO_CREATE | FLAG_CANCEL_CURRENT | FLAG_UPDATE_CURRENT);
        Key key = new Key(type, requestCode, last, keyFlags, activity);
        synchronized (sRecords) {
            Record rec = sRecords.get(key);
            if (rec != null) {
                if (!cancelCurrent) {
                    if (updateCurrent) {
                        rec.intent.replaceExtras(last);
                        if (rec.allIntents != null) {
                            rec.allIntents[rec.allIntents.length - 1] = rec.intent;
                        }
                    }
                    return new PendingIntent(rec);
                }
                rec.canceled = true;
                sRecords.remove(key);
            }
            if (noCreate) return null;
            rec = new Record(key, last, copies.length > 1 ? copies : null, options);
            sRecords.put(key, rec);
            return new PendingIntent(rec);
        }
    }

    private static void checkFlags(int flags) {
        boolean immutable = (flags & FLAG_IMMUTABLE) != 0;
        boolean mutable = (flags & FLAG_MUTABLE) != 0;
        if (immutable && mutable) {
            throw new IllegalArgumentException("Cannot set both FLAG_IMMUTABLE and FLAG_MUTABLE for PendingIntent");
        }
        ApplicationInfo app = ActivityThread.sAppInfo;
        // Android 12 added this check, so it applies only when the reported SDK level is 31 or more
        // (libraries such as WorkManager pick the flag from SDK_INT, not from the target).
        if (!immutable && !mutable && app != null && app.targetSdkVersion >= 31
                && android.os.Build.VERSION.SDK_INT >= 31) {
            throw new IllegalArgumentException(app.packageName + ": Targeting S+ (version 31 and above) requires"
                    + " that one of FLAG_IMMUTABLE or FLAG_MUTABLE be specified when creating a PendingIntent.\n"
                    + "Strongly consider using FLAG_IMMUTABLE, only use FLAG_MUTABLE if some functionality depends"
                    + " on the PendingIntent being mutable, e.g. if it needs to be used with inline replies or"
                    + " bubbles.");
        }
    }

    public IntentSender getIntentSender() { return new IntentSender(this); }

    public void cancel() {
        synchronized (sRecords) {
            if (mTarget.canceled) return;
            mTarget.canceled = true;
            if (sRecords.get(mTarget.key) == mTarget) sRecords.remove(mTarget.key);
        }
    }

    public void send() throws CanceledException { send(null, 0, null, null, null, null, null); }

    public void send(int code) throws CanceledException { send(null, code, null, null, null, null, null); }

    public void send(Context context, int code, Intent intent) throws CanceledException {
        send(context, code, intent, null, null, null, null);
    }

    public void send(Bundle options) throws CanceledException { send(null, 0, null, null, null, null, options); }

    public void send(int code, OnFinished onFinished, Handler handler) throws CanceledException {
        send(null, code, null, onFinished, handler, null, null);
    }

    public void send(Context context, int code, Intent intent, OnFinished onFinished, Handler handler)
            throws CanceledException {
        send(context, code, intent, onFinished, handler, null, null);
    }

    public void send(Context context, int code, Intent intent, OnFinished onFinished, Handler handler,
            String requiredPermission) throws CanceledException {
        send(context, code, intent, onFinished, handler, requiredPermission, null);
    }

    public void send(Context context, int code, Intent intent, final OnFinished onFinished, Handler handler,
            String requiredPermission, Bundle options) throws CanceledException {
        Record rec = mTarget;
        Intent finalIntent;
        Intent[] all = null;
        synchronized (sRecords) {
            if (rec.canceled) throw new CanceledException();
            finalIntent = new Intent(rec.intent);
            if (rec.allIntents != null) {
                all = new Intent[rec.allIntents.length];
                for (int i = 0; i < all.length; i++) all[i] = new Intent(rec.allIntents[i]);
                all[all.length - 1] = finalIntent;
            }
            if ((rec.key.flags & FLAG_ONE_SHOT) != 0) {
                rec.canceled = true;
                if (sRecords.get(rec.key) == rec) sRecords.remove(rec.key);
            }
        }
        if (intent != null && !isImmutable()) finalIntent.fillIn(intent, rec.key.flags);
        boolean finishedByBroadcast = false;
        switch (rec.key.type) {
            case TYPE_ACTIVITY:
                try {
                    if (all != null) {
                        for (int i = 0; i < all.length; i++) startActivity(all[i]);
                    } else {
                        startActivity(finalIntent);
                    }
                } catch (ActivityNotFoundException e) {
                    throw new CanceledException(e);
                }
                break;
            case TYPE_ACTIVITY_RESULT:
                ActivityThread.sendPendingResult(rec.key.activity, rec.key.requestCode, code, finalIntent);
                break;
            case TYPE_BROADCAST:
                if (onFinished != null) {
                    final Intent sent = finalIntent;
                    BroadcastReceiver resultTo = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context c, Intent i) {
                            onFinished.onSendFinished(PendingIntent.this, sent, getResultCode(), getResultData(),
                                    getResultExtras(false));
                        }
                    };
                    BroadcastQueue.send(finalIntent, true, resultTo, handler, code, null, null);
                    finishedByBroadcast = true;
                } else {
                    BroadcastQueue.send(finalIntent, false, null, null, code, null, null);
                }
                break;
            case TYPE_SERVICE:
            case TYPE_FOREGROUND_SERVICE:
                if (ActiveServices.startService(finalIntent) == null) throw new CanceledException();
                break;
            default:
                break;
        }
        if (onFinished != null && !finishedByBroadcast) {
            final Intent sent = finalIntent;
            final int resultCode = code;
            Runnable r = new Runnable() {
                public void run() { onFinished.onSendFinished(PendingIntent.this, sent, resultCode, null, null); }
            };
            if (handler != null) handler.post(r);
            else ActivityThread.post(r);
        }
    }

    /** framework-internal. The activity intent this would start, for Activity.startIntentSenderForResult. */
    Intent activityIntent(Intent fillIn) throws CanceledException {
        synchronized (sRecords) {
            if (mTarget.canceled) throw new CanceledException();
            if (mTarget.key.type != TYPE_ACTIVITY || mTarget.allIntents != null) return null;
            Intent i = new Intent(mTarget.intent);
            if (fillIn != null && !isImmutable()) i.fillIn(fillIn, mTarget.key.flags);
            if ((mTarget.key.flags & FLAG_ONE_SHOT) != 0) cancel();
            return i;
        }
    }

    private static void startActivity(Intent intent) {
        ActivityThread.startActivity(null, intent, -1);
    }

    public String getTargetPackage() { return getCreatorPackage(); }

    public String getCreatorPackage() { return ActivityThread.sPackageName; }

    public int getCreatorUid() { return Process.myUid(); }

    public UserHandle getCreatorUserHandle() { return UserHandle.SYSTEM; }

    public boolean isImmutable() { return (mTarget.key.flags & FLAG_IMMUTABLE) != 0; }

    public boolean isActivity() { return mTarget.key.type == TYPE_ACTIVITY; }

    public boolean isForegroundService() { return mTarget.key.type == TYPE_FOREGROUND_SERVICE; }

    public boolean isService() { return mTarget.key.type == TYPE_SERVICE; }

    public boolean isBroadcast() { return mTarget.key.type == TYPE_BROADCAST; }

    @Override
    public boolean equals(Object otherObj) {
        return otherObj instanceof PendingIntent && ((PendingIntent) otherObj).mTarget == mTarget;
    }

    @Override
    public int hashCode() { return System.identityHashCode(mTarget); }

    @Override
    public String toString() {
        return "PendingIntent{" + Integer.toHexString(System.identityHashCode(this)) + ": "
                + Integer.toHexString(System.identityHashCode(mTarget)) + "}";
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel out, int flags) { out.writeValue(mTarget); }

    public static void writePendingIntentOrNullToParcel(PendingIntent sender, Parcel out) {
        out.writeValue(sender != null ? sender.mTarget : null);
    }

    public static PendingIntent readPendingIntentOrNullFromParcel(Parcel in) {
        Object target = in.readValue(null);
        return target instanceof Record ? new PendingIntent((Record) target) : null;
    }

    public static final Parcelable.Creator<PendingIntent> CREATOR = new Parcelable.Creator<PendingIntent>() {
        public PendingIntent createFromParcel(Parcel in) {
            Object target = in.readValue(null);
            return target instanceof Record ? new PendingIntent((Record) target) : null;
        }

        public PendingIntent[] newArray(int size) { return new PendingIntent[size]; }
    };
}
