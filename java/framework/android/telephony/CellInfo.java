package android.telephony;

/** A cell the modem sees; there is no modem, so none exist. */
public abstract class CellInfo implements android.os.Parcelable {
    public static final int CONNECTION_NONE = 0;
    public static final int CONNECTION_PRIMARY_SERVING = 1;
    public static final int CONNECTION_SECONDARY_SERVING = 2;
    public static final int CONNECTION_UNKNOWN = 2147483647;
    public static final int UNAVAILABLE = 2147483647;
    public static final long UNAVAILABLE_LONG = 9223372036854775807L;

    CellInfo() {}

    public boolean isRegistered() { return false; }
    public long getTimeStamp() { return 0; }
    public long getTimestampMillis() { return 0; }
    public int getCellConnectionStatus() { return CONNECTION_NONE; }
    public int describeContents() { return 0; }
    public void writeToParcel(android.os.Parcel dest, int flags) {}
}
