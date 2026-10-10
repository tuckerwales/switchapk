package android.app;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;
import java.io.InputStream;

/** A past exit of this app. ActivityManager never reports any, so these are only made from parcels. */
public final class ApplicationExitInfo implements Parcelable {
    public static final int REASON_ANR = 6;
    public static final int REASON_CRASH = 4;
    public static final int REASON_CRASH_NATIVE = 5;
    public static final int REASON_DEPENDENCY_DIED = 12;
    public static final int REASON_EXCESSIVE_RESOURCE_USAGE = 9;
    public static final int REASON_EXIT_SELF = 1;
    public static final int REASON_FREEZER = 14;
    public static final int REASON_INITIALIZATION_FAILURE = 7;
    public static final int REASON_LOW_MEMORY = 3;
    public static final int REASON_OTHER = 13;
    public static final int REASON_PACKAGE_STATE_CHANGE = 15;
    public static final int REASON_PACKAGE_UPDATED = 16;
    public static final int REASON_PERMISSION_CHANGE = 8;
    public static final int REASON_SIGNALED = 2;
    public static final int REASON_UNKNOWN = 0;
    public static final int REASON_USER_REQUESTED = 10;
    public static final int REASON_USER_STOPPED = 11;

    private int mPid;
    private int mRealUid;
    private int mPackageUid;
    private int mDefiningUid;
    private String mProcessName;
    private int mReason;
    private int mStatus;
    private int mImportance;
    private long mPss;
    private long mRss;
    private long mTimestamp;
    private String mDescription;
    private byte[] mState;

    private ApplicationExitInfo() {}

    public int getPid() { return mPid; }
    public int getRealUid() { return mRealUid; }
    public int getPackageUid() { return mPackageUid; }
    public int getDefiningUid() { return mDefiningUid; }
    public String getProcessName() { return mProcessName; }
    public int getReason() { return mReason; }
    public int getStatus() { return mStatus; }
    public int getImportance() { return mImportance; }
    public long getPss() { return mPss; }
    public long getRss() { return mRss; }
    public long getTimestamp() { return mTimestamp; }
    public String getDescription() { return mDescription; }
    public UserHandle getUserHandle() { return UserHandle.getUserHandleForUid(mRealUid); }
    public byte[] getProcessStateSummary() { return mState; }
    public InputStream getTraceInputStream() throws java.io.IOException { return null; }
    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPid);
        dest.writeInt(mRealUid);
        dest.writeInt(mPackageUid);
        dest.writeInt(mDefiningUid);
        dest.writeString(mProcessName);
        dest.writeInt(mReason);
        dest.writeInt(mStatus);
        dest.writeInt(mImportance);
        dest.writeLong(mPss);
        dest.writeLong(mRss);
        dest.writeLong(mTimestamp);
        dest.writeString(mDescription);
        dest.writeByteArray(mState);
    }

    public String toString() {
        return "ApplicationExitInfo(timestamp=" + mTimestamp + " pid=" + mPid + " realUid=" + mRealUid + " packageUid="
                + mPackageUid + " definingUid=" + mDefiningUid + " user=" + (mRealUid / UserHandle.PER_USER_RANGE)
                + " process=" + mProcessName + " reason=" + mReason + " status=" + mStatus + " importance="
                + mImportance + " pss=" + mPss + " rss=" + mRss + " description=" + mDescription + ")";
    }

    public boolean equals(Object other) {
        if (!(other instanceof ApplicationExitInfo)) return false;
        ApplicationExitInfo o = (ApplicationExitInfo) other;
        return mPid == o.mPid && mRealUid == o.mRealUid && mReason == o.mReason && mStatus == o.mStatus
                && mTimestamp == o.mTimestamp && java.util.Objects.equals(mProcessName, o.mProcessName);
    }

    public int hashCode() {
        return ((mPid * 31 + mRealUid) * 31 + mReason) * 31 + (int) mTimestamp;
    }

    public static final Parcelable.Creator<ApplicationExitInfo> CREATOR =
            new Parcelable.Creator<ApplicationExitInfo>() {
        public ApplicationExitInfo createFromParcel(Parcel in) {
            ApplicationExitInfo e = new ApplicationExitInfo();
            e.mPid = in.readInt();
            e.mRealUid = in.readInt();
            e.mPackageUid = in.readInt();
            e.mDefiningUid = in.readInt();
            e.mProcessName = in.readString();
            e.mReason = in.readInt();
            e.mStatus = in.readInt();
            e.mImportance = in.readInt();
            e.mPss = in.readLong();
            e.mRss = in.readLong();
            e.mTimestamp = in.readLong();
            e.mDescription = in.readString();
            e.mState = in.createByteArray();
            return e;
        }

        public ApplicationExitInfo[] newArray(int size) { return new ApplicationExitInfo[size]; }
    };
}
