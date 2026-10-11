package android.media;

import android.os.Parcel;
import android.os.Parcelable;

public final class AudioAttributes implements Parcelable {
    public static final int ALLOW_CAPTURE_BY_ALL = 1;
    public static final int ALLOW_CAPTURE_BY_SYSTEM = 2;
    public static final int ALLOW_CAPTURE_BY_NONE = 3;
    public static final int CONTENT_TYPE_UNKNOWN = 0;
    public static final int CONTENT_TYPE_SPEECH = 1;
    public static final int CONTENT_TYPE_MUSIC = 2;
    public static final int CONTENT_TYPE_MOVIE = 3;
    public static final int CONTENT_TYPE_SONIFICATION = 4;
    public static final int FLAG_AUDIBILITY_ENFORCED = 1;
    public static final int FLAG_HW_AV_SYNC = 16;
    public static final int FLAG_LOW_LATENCY = 256;
    /** @hide */
    public static final int FLAG_BYPASS_INTERRUPTION_POLICY = 0x1 << 6;
    public static final int SPATIALIZATION_BEHAVIOR_AUTO = 0;
    public static final int SPATIALIZATION_BEHAVIOR_NEVER = 1;
    public static final int USAGE_UNKNOWN = 0;
    public static final int USAGE_MEDIA = 1;
    public static final int USAGE_VOICE_COMMUNICATION = 2;
    public static final int USAGE_VOICE_COMMUNICATION_SIGNALLING = 3;
    public static final int USAGE_ALARM = 4;
    public static final int USAGE_NOTIFICATION = 5;
    public static final int USAGE_NOTIFICATION_RINGTONE = 6;
    public static final int USAGE_NOTIFICATION_COMMUNICATION_REQUEST = 7;
    public static final int USAGE_NOTIFICATION_COMMUNICATION_INSTANT = 8;
    public static final int USAGE_NOTIFICATION_COMMUNICATION_DELAYED = 9;
    public static final int USAGE_NOTIFICATION_EVENT = 10;
    public static final int USAGE_ASSISTANCE_ACCESSIBILITY = 11;
    public static final int USAGE_ASSISTANCE_NAVIGATION_GUIDANCE = 12;
    public static final int USAGE_ASSISTANCE_SONIFICATION = 13;
    public static final int USAGE_GAME = 14;
    public static final int USAGE_ASSISTANT = 16;

    private int mUsage;
    private int mContentType;
    private int mFlags;
    private int mCapture = ALLOW_CAPTURE_BY_ALL;
    private int mSpatialization;

    private AudioAttributes() {}

    public int getUsage() { return mUsage; }
    public int getContentType() { return mContentType; }
    public int getFlags() { return mFlags; }
    public int getAllowedCapturePolicy() { return mCapture; }
    public int getSpatializationBehavior() { return mSpatialization; }

    /** Historical stream numbers. There is no AudioManager yet. */
    public int getVolumeControlStream() {
        switch (mUsage) {
            case USAGE_VOICE_COMMUNICATION:
            case USAGE_VOICE_COMMUNICATION_SIGNALLING:
                return 0;
            case USAGE_ALARM:
                return 4;
            case USAGE_NOTIFICATION:
            case USAGE_NOTIFICATION_RINGTONE:
            case USAGE_NOTIFICATION_COMMUNICATION_REQUEST:
            case USAGE_NOTIFICATION_COMMUNICATION_INSTANT:
            case USAGE_NOTIFICATION_COMMUNICATION_DELAYED:
            case USAGE_NOTIFICATION_EVENT:
                return 5;
            default:
                return 3;
        }
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mUsage);
        dest.writeInt(mContentType);
        dest.writeInt(mFlags);
        dest.writeInt(mCapture);
        dest.writeInt(mSpatialization);
    }

    public static final Creator<AudioAttributes> CREATOR = new Creator<AudioAttributes>() {
        public AudioAttributes createFromParcel(Parcel in) {
            AudioAttributes a = new AudioAttributes();
            a.mUsage = in.readInt();
            a.mContentType = in.readInt();
            a.mFlags = in.readInt();
            a.mCapture = in.readInt();
            a.mSpatialization = in.readInt();
            return a;
        }
        public AudioAttributes[] newArray(int size) { return new AudioAttributes[size]; }
    };

    public static class Builder {
        private final AudioAttributes mAttr = new AudioAttributes();

        public Builder() {}

        public Builder(AudioAttributes aa) {
            mAttr.mUsage = aa.mUsage;
            mAttr.mContentType = aa.mContentType;
            mAttr.mFlags = aa.mFlags;
            mAttr.mCapture = aa.mCapture;
            mAttr.mSpatialization = aa.mSpatialization;
        }

        public Builder setUsage(int usage) { mAttr.mUsage = usage; return this; }
        public Builder setContentType(int contentType) { mAttr.mContentType = contentType; return this; }
        public Builder setFlags(int flags) { mAttr.mFlags = flags; return this; }
        public Builder setAllowedCapturePolicy(int capturePolicy) { mAttr.mCapture = capturePolicy; return this; }
        public Builder setSpatializationBehavior(int behavior) { mAttr.mSpatialization = behavior; return this; }
        public Builder setIsContentSpatialized(boolean spatialized) { return this; }
        public Builder setLegacyStreamType(int streamType) { return this; }
        public Builder setHapticChannelsMuted(boolean muted) { return this; }
        public AudioAttributes build() { return mAttr; }
    }
}
