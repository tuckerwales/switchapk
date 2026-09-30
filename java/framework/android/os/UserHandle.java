package android.os;

public final class UserHandle implements Parcelable {
    public static final int USER_ALL = -1;
    public static final int USER_CURRENT = -2;
    public static final int USER_SYSTEM = 0;
    public static final int PER_USER_RANGE = 100000;
    public static final UserHandle ALL = new UserHandle(USER_ALL);
    public static final UserHandle CURRENT = new UserHandle(USER_CURRENT);
    public static final UserHandle SYSTEM = new UserHandle(USER_SYSTEM);
    static final UserHandle CURRENT_USER = SYSTEM;

    final int mHandle;

    public UserHandle(int h) { mHandle = h; }

    public static UserHandle getUserHandleForUid(int uid) { return SYSTEM; }
    public static int getUserId(int uid) { return uid / PER_USER_RANGE; }
    public static int myUserId() { return 0; }
    public static UserHandle of(int userId) { return userId == 0 ? SYSTEM : new UserHandle(userId); }
    public static int getAppId(int uid) { return uid % PER_USER_RANGE; }
    public int getIdentifier() { return mHandle; }
    public boolean isSystem() { return mHandle == 0; }
    public boolean isOwner() { return mHandle == 0; }
    public static int getCallingUserId() { return 0; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { out.writeInt(mHandle); }
    public static final Parcelable.Creator<UserHandle> CREATOR = new Parcelable.Creator<UserHandle>() {
        public UserHandle createFromParcel(Parcel in) { return new UserHandle(in.readInt()); }
        public UserHandle[] newArray(int size) { return new UserHandle[size]; }
    };
    public boolean equals(Object o) { return o instanceof UserHandle && ((UserHandle) o).mHandle == mHandle; }
    public int hashCode() { return mHandle; }
    public String toString() { return "UserHandle{" + mHandle + "}"; }
}
