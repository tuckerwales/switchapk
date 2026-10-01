package android.app;

import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.VibrationEffect;
import java.util.Arrays;
import java.util.Objects;

/** Port of AOSP NotificationChannel (the app-visible settings; the user never changes them here). */
public final class NotificationChannel implements Parcelable {
    public static final String DEFAULT_CHANNEL_ID = "miscellaneous";
    public static final String EDIT_IMPORTANCE = "importance";
    public static final String EDIT_SOUND = "sound";
    public static final String EDIT_VIBRATION = "vibration";
    public static final String EDIT_LOCKED_DEVICE = "locked";
    public static final String EDIT_ZEN = "zen";
    public static final String EDIT_LAUNCHER = "launcher";
    public static final String EDIT_CONVERSATION = "conversation";

    /** Settings.System.DEFAULT_NOTIFICATION_URI. */
    static final Uri DEFAULT_SOUND = Uri.parse("content://settings/system/notification_sound");
    /** NotificationManager.VISIBILITY_NO_OVERRIDE (hidden). */
    static final int VISIBILITY_NO_OVERRIDE = -1000;

    private final String mId;
    private CharSequence mName;
    private String mDesc;
    private int mImportance;
    private boolean mBypassDnd;
    private int mLockscreenVisibility = VISIBILITY_NO_OVERRIDE;
    private Uri mSound = DEFAULT_SOUND;
    private AudioAttributes mAudioAttributes = Notification.AUDIO_ATTRIBUTES_DEFAULT;
    private boolean mLights;
    private int mLightColor;
    private long[] mVibration;
    private VibrationEffect mVibrationEffect;
    private boolean mVibrationEnabled;
    private boolean mShowBadge = true;
    private String mGroup;
    private boolean mBlockableSystem;
    private boolean mAllowBubbles = true;
    private String mParentId;
    private String mConversationId;

    public NotificationChannel(String id, CharSequence name, int importance) {
        if (id == null) throw new NullPointerException("id");
        mId = id;
        mName = name;
        mImportance = importance;
    }

    public void writeToParcel(Parcel dest, int flags) { dest.writeValue(this); }

    public void setBlockable(boolean blockable) { mBlockableSystem = blockable; }

    public void setName(CharSequence name) { mName = name; }

    public void setDescription(String description) { mDesc = description; }

    public void setGroup(String groupId) { mGroup = groupId; }

    public void setShowBadge(boolean showBadge) { mShowBadge = showBadge; }

    public void setSound(Uri sound, AudioAttributes audioAttributes) {
        mSound = sound;
        mAudioAttributes = audioAttributes;
    }

    public void enableLights(boolean lights) { mLights = lights; }

    public void setLightColor(int argb) { mLightColor = argb; }

    public void enableVibration(boolean vibration) { mVibrationEnabled = vibration; }

    public void setVibrationPattern(long[] vibrationPattern) {
        mVibrationEnabled = vibrationPattern != null && vibrationPattern.length > 0;
        mVibration = vibrationPattern;
    }

    public void setVibrationEffect(VibrationEffect effect) {
        mVibrationEnabled = effect != null;
        mVibrationEffect = effect;
    }

    public void setImportance(int importance) { mImportance = importance; }

    public void setBypassDnd(boolean bypassDnd) { mBypassDnd = bypassDnd; }

    public void setLockscreenVisibility(int lockscreenVisibility) { mLockscreenVisibility = lockscreenVisibility; }

    public void setAllowBubbles(boolean allowBubbles) { mAllowBubbles = allowBubbles; }

    public void setConversationId(String parentChannelId, String conversationId) {
        mParentId = parentChannelId;
        mConversationId = conversationId;
    }

    public String getId() { return mId; }

    public CharSequence getName() { return mName; }

    public String getDescription() { return mDesc; }

    public int getImportance() { return mImportance; }

    public boolean canBypassDnd() { return mBypassDnd; }

    public boolean isConversation() { return mConversationId != null && !mConversationId.isEmpty(); }

    public boolean isImportantConversation() { return false; }

    public Uri getSound() { return mSound; }

    public AudioAttributes getAudioAttributes() { return mAudioAttributes; }

    public boolean shouldShowLights() { return mLights; }

    public int getLightColor() { return mLightColor; }

    public boolean shouldVibrate() { return mVibrationEnabled; }

    public long[] getVibrationPattern() { return mVibration; }

    public VibrationEffect getVibrationEffect() { return mVibrationEffect; }

    public int getLockscreenVisibility() { return mLockscreenVisibility; }

    public boolean canShowBadge() { return mShowBadge; }

    public String getGroup() { return mGroup; }

    public boolean canBubble() { return mAllowBubbles; }

    public String getParentChannelId() { return mParentId; }

    public String getConversationId() { return mConversationId; }

    public boolean isBlockable() { return mBlockableSystem; }

    public boolean isDemoted() { return false; }

    public boolean hasUserSetImportance() { return false; }

    public boolean hasUserSetSound() { return false; }

    public int describeContents() { return 0; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotificationChannel)) return false;
        NotificationChannel that = (NotificationChannel) o;
        return mImportance == that.mImportance && mBypassDnd == that.mBypassDnd
                && mLockscreenVisibility == that.mLockscreenVisibility && mLights == that.mLights
                && mLightColor == that.mLightColor && mVibrationEnabled == that.mVibrationEnabled
                && mShowBadge == that.mShowBadge && mBlockableSystem == that.mBlockableSystem
                && mAllowBubbles == that.mAllowBubbles && Objects.equals(mId, that.mId)
                && Objects.equals(mName, that.mName) && Objects.equals(mDesc, that.mDesc)
                && Objects.equals(mSound, that.mSound) && Arrays.equals(mVibration, that.mVibration)
                && Objects.equals(mGroup, that.mGroup) && Objects.equals(mParentId, that.mParentId)
                && Objects.equals(mConversationId, that.mConversationId);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(mId, mName, mDesc, mImportance, mBypassDnd, mLockscreenVisibility, mSound, mLights,
                mLightColor, mVibrationEnabled, mShowBadge, mGroup, mBlockableSystem, mAllowBubbles, mParentId,
                mConversationId) + Arrays.hashCode(mVibration);
    }

    @Override
    public String toString() {
        return "NotificationChannel{mId='" + mId + "', mName=" + mName + ", mDescription=" + (mDesc != null ? "hasDescription " : "")
                + ", mImportance=" + mImportance + ", mBypassDnd=" + mBypassDnd + ", mLockscreenVisibility="
                + mLockscreenVisibility + ", mSound=" + mSound + ", mLights=" + mLights + ", mLightColor=" + mLightColor
                + ", mVibration=" + Arrays.toString(mVibration) + ", mShowBadge=" + mShowBadge + ", mGroup='" + mGroup
                + "'}";
    }

    public static final Parcelable.Creator<NotificationChannel> CREATOR = new Parcelable.Creator<NotificationChannel>() {
        public NotificationChannel createFromParcel(Parcel in) { return (NotificationChannel) in.readValue(null); }
        public NotificationChannel[] newArray(int size) { return new NotificationChannel[size]; }
    };
}
