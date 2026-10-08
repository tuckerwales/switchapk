package android.net;

import android.os.Parcel;
import android.os.Parcelable;

@Deprecated
public class NetworkInfo implements Parcelable {
    public enum State {
        CONNECTING, CONNECTED, SUSPENDED, DISCONNECTING, DISCONNECTED, UNKNOWN
    }

    public enum DetailedState {
        IDLE, SCANNING, CONNECTING, AUTHENTICATING, OBTAINING_IPADDR, CONNECTED, SUSPENDED, DISCONNECTING,
        DISCONNECTED, FAILED, BLOCKED, VERIFYING_POOR_LINK, CAPTIVE_PORTAL_CHECK
    }

    private final int mNetworkType;
    private final int mSubtype;
    private final String mTypeName;
    private final String mSubtypeName;
    private State mState = State.UNKNOWN;
    private DetailedState mDetailedState = DetailedState.IDLE;
    private String mReason;
    private String mExtraInfo;
    private boolean mIsAvailable;

    public NetworkInfo(int type, int subtype, String typeName, String subtypeName) {
        mNetworkType = type;
        mSubtype = subtype;
        mTypeName = typeName;
        mSubtypeName = subtypeName;
    }

    /** Framework-internal: a snapshot for ConnectivityManager. */
    static NetworkInfo of(int type, boolean connected, boolean available) {
        NetworkInfo ni = new NetworkInfo(type, 0, type == ConnectivityManager.TYPE_WIFI ? "WIFI" : "ETHERNET", "");
        ni.setDetailedState(connected ? DetailedState.CONNECTED : DetailedState.DISCONNECTED, null, null);
        ni.mIsAvailable = available;
        return ni;
    }

    public int getType() {
        return mNetworkType;
    }

    public int getSubtype() {
        return mSubtype;
    }

    public String getTypeName() {
        return mTypeName;
    }

    public String getSubtypeName() {
        return mSubtypeName;
    }

    public boolean isConnectedOrConnecting() {
        return mState == State.CONNECTED || mState == State.CONNECTING;
    }

    public boolean isConnected() {
        return mState == State.CONNECTED;
    }

    public boolean isAvailable() {
        return mIsAvailable;
    }

    public boolean isFailover() {
        return false;
    }

    public boolean isRoaming() {
        return false;
    }

    public State getState() {
        return mState;
    }

    public DetailedState getDetailedState() {
        return mDetailedState;
    }

    public void setDetailedState(DetailedState detailedState, String reason, String extraInfo) {
        mDetailedState = detailedState;
        mState = stateOf(detailedState);
        mReason = reason;
        mExtraInfo = extraInfo;
    }

    private static State stateOf(DetailedState d) {
        switch (d) {
            case CONNECTED:
                return State.CONNECTED;
            case SCANNING:
            case CONNECTING:
            case AUTHENTICATING:
            case OBTAINING_IPADDR:
            case VERIFYING_POOR_LINK:
            case CAPTIVE_PORTAL_CHECK:
                return State.CONNECTING;
            case SUSPENDED:
                return State.SUSPENDED;
            case DISCONNECTING:
                return State.DISCONNECTING;
            case IDLE:
            case DISCONNECTED:
            case FAILED:
            case BLOCKED:
                return State.DISCONNECTED;
            default:
                return State.UNKNOWN;
        }
    }

    public String getReason() {
        return mReason;
    }

    public String getExtraInfo() {
        return mExtraInfo;
    }

    public String toString() {
        return "[type: " + mTypeName + "[" + mSubtypeName + "], state: " + mState + "/" + mDetailedState
                + ", reason: " + (mReason == null ? "(unspecified)" : mReason) + ", extra: "
                + (mExtraInfo == null ? "(none)" : mExtraInfo) + ", failover: false, available: " + mIsAvailable
                + ", roaming: false]";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mNetworkType);
        dest.writeInt(mSubtype);
        dest.writeString(mTypeName);
        dest.writeString(mSubtypeName);
        dest.writeInt(mDetailedState.ordinal());
        dest.writeInt(mIsAvailable ? 1 : 0);
        dest.writeString(mReason);
        dest.writeString(mExtraInfo);
    }

    public static final Parcelable.Creator<NetworkInfo> CREATOR = new Parcelable.Creator<NetworkInfo>() {
        public NetworkInfo createFromParcel(Parcel in) {
            NetworkInfo ni = new NetworkInfo(in.readInt(), in.readInt(), in.readString(), in.readString());
            DetailedState d = DetailedState.values()[in.readInt()];
            ni.mIsAvailable = in.readInt() != 0;
            ni.setDetailedState(d, in.readString(), in.readString());
            return ni;
        }

        public NetworkInfo[] newArray(int size) {
            return new NetworkInfo[size];
        }
    };
}
