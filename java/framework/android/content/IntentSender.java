package android.content;

import android.app.PendingIntent;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;

/** A PendingIntent seen from the sending side (the hidden AOSP constructor takes its target). */
public class IntentSender implements Parcelable {
    private final PendingIntent mTarget;

    public static class SendIntentException extends android.util.AndroidException {
        public SendIntentException() {}
        public SendIntentException(String name) { super(name); }
        public SendIntentException(Exception cause) { super(cause); }
    }

    public interface OnFinished {
        void onSendFinished(IntentSender IntentSender, Intent intent, int resultCode, String resultData,
                android.os.Bundle resultExtras);
    }

    /** framework-internal. */
    public IntentSender(PendingIntent target) { mTarget = target; }

    /** framework-internal. */
    public PendingIntent getTarget() { return mTarget; }

    public void sendIntent(Context context, int code, Intent intent, OnFinished onFinished, Handler handler)
            throws SendIntentException {
        sendIntent(context, code, intent, onFinished, handler, null);
    }

    public void sendIntent(Context context, int code, Intent intent, final OnFinished onFinished, Handler handler,
            String requiredPermission) throws SendIntentException {
        PendingIntent.OnFinished finished = null;
        if (onFinished != null) {
            finished = new PendingIntent.OnFinished() {
                public void onSendFinished(PendingIntent pi, Intent i, int resultCode, String resultData,
                        android.os.Bundle resultExtras) {
                    onFinished.onSendFinished(IntentSender.this, i, resultCode, resultData, resultExtras);
                }
            };
        }
        try {
            mTarget.send(context, code, intent, finished, handler, requiredPermission);
        } catch (PendingIntent.CanceledException e) {
            throw new SendIntentException(e);
        }
    }

    public String getTargetPackage() { return mTarget.getTargetPackage(); }
    public String getCreatorPackage() { return mTarget.getCreatorPackage(); }
    public int getCreatorUid() { return mTarget.getCreatorUid(); }
    public UserHandle getCreatorUserHandle() { return mTarget.getCreatorUserHandle(); }

    @Override
    public boolean equals(Object otherObj) {
        return otherObj instanceof IntentSender && mTarget.equals(((IntentSender) otherObj).mTarget);
    }

    @Override
    public int hashCode() { return mTarget.hashCode(); }

    @Override
    public String toString() { return "IntentSender{" + Integer.toHexString(System.identityHashCode(this)) + ": " + mTarget + "}"; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel out, int flags) { mTarget.writeToParcel(out, flags); }

    public static void writeIntentSenderOrNullToParcel(IntentSender sender, Parcel out) {
        PendingIntent.writePendingIntentOrNullToParcel(sender != null ? sender.mTarget : null, out);
    }

    public static IntentSender readIntentSenderOrNullFromParcel(Parcel in) {
        PendingIntent target = PendingIntent.readPendingIntentOrNullFromParcel(in);
        return target != null ? new IntentSender(target) : null;
    }

    public static final Parcelable.Creator<IntentSender> CREATOR = new Parcelable.Creator<IntentSender>() {
        public IntentSender createFromParcel(Parcel in) { return readIntentSenderOrNullFromParcel(in); }
        public IntentSender[] newArray(int size) { return new IntentSender[size]; }
    };
}
