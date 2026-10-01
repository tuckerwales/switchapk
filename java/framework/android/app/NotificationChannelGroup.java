package android.app;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NotificationChannelGroup implements Parcelable {
    private final String mId;
    private CharSequence mName;
    private String mDescription;
    private boolean mBlocked;
    private List<NotificationChannel> mChannels = new ArrayList<NotificationChannel>();

    public NotificationChannelGroup(String id, CharSequence name) {
        if (id == null) throw new NullPointerException("id");
        mId = id;
        mName = name;
    }

    public void writeToParcel(Parcel dest, int flags) { dest.writeValue(clone()); }

    public String getId() { return mId; }

    public CharSequence getName() { return mName; }

    public String getDescription() { return mDescription; }

    public List<NotificationChannel> getChannels() { return mChannels; }

    public boolean isBlocked() { return mBlocked; }

    public void setDescription(String description) { mDescription = description; }

    /** framework-internal. */
    void setChannels(List<NotificationChannel> channels) { mChannels = channels; }

    /** framework-internal. */
    void setName(CharSequence name) { mName = name; }

    public int describeContents() { return 0; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotificationChannelGroup)) return false;
        NotificationChannelGroup that = (NotificationChannelGroup) o;
        return mBlocked == that.mBlocked && Objects.equals(mId, that.mId) && Objects.equals(mName, that.mName)
                && Objects.equals(mDescription, that.mDescription) && Objects.equals(mChannels, that.mChannels);
    }

    @Override
    public int hashCode() { return Objects.hash(mId, mName, mDescription, mBlocked, mChannels); }

    @Override
    public NotificationChannelGroup clone() {
        NotificationChannelGroup cloned = new NotificationChannelGroup(mId, mName);
        cloned.mDescription = mDescription;
        cloned.mBlocked = mBlocked;
        cloned.mChannels = new ArrayList<NotificationChannel>(mChannels);
        return cloned;
    }

    @Override
    public String toString() {
        return "NotificationChannelGroup{mId='" + mId + "', mName=" + mName + ", mDescription="
                + (mDescription != null ? "hasDescription " : "") + ", mBlocked=" + mBlocked + ", mChannels=" + mChannels
                + "}";
    }

    public static final Parcelable.Creator<NotificationChannelGroup> CREATOR =
            new Parcelable.Creator<NotificationChannelGroup>() {
                public NotificationChannelGroup createFromParcel(Parcel in) {
                    return (NotificationChannelGroup) in.readValue(null);
                }

                public NotificationChannelGroup[] newArray(int size) { return new NotificationChannelGroup[size]; }
            };
}
