package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.Set;

public final class NetworkCapabilities implements Parcelable {
    public static final int NET_CAPABILITY_MMS = 0;
    public static final int NET_CAPABILITY_SUPL = 1;
    public static final int NET_CAPABILITY_DUN = 2;
    public static final int NET_CAPABILITY_FOTA = 3;
    public static final int NET_CAPABILITY_IMS = 4;
    public static final int NET_CAPABILITY_CBS = 5;
    public static final int NET_CAPABILITY_WIFI_P2P = 6;
    public static final int NET_CAPABILITY_IA = 7;
    public static final int NET_CAPABILITY_RCS = 8;
    public static final int NET_CAPABILITY_XCAP = 9;
    public static final int NET_CAPABILITY_EIMS = 10;
    public static final int NET_CAPABILITY_NOT_METERED = 11;
    public static final int NET_CAPABILITY_INTERNET = 12;
    public static final int NET_CAPABILITY_NOT_RESTRICTED = 13;
    public static final int NET_CAPABILITY_TRUSTED = 14;
    public static final int NET_CAPABILITY_NOT_VPN = 15;
    public static final int NET_CAPABILITY_VALIDATED = 16;
    public static final int NET_CAPABILITY_CAPTIVE_PORTAL = 17;
    public static final int NET_CAPABILITY_NOT_ROAMING = 18;
    public static final int NET_CAPABILITY_FOREGROUND = 19;
    public static final int NET_CAPABILITY_NOT_CONGESTED = 20;
    public static final int NET_CAPABILITY_NOT_SUSPENDED = 21;
    public static final int NET_CAPABILITY_MCX = 23;
    public static final int NET_CAPABILITY_TEMPORARILY_NOT_METERED = 25;
    public static final int NET_CAPABILITY_ENTERPRISE = 29;
    public static final int NET_CAPABILITY_HEAD_UNIT = 32;
    public static final int NET_CAPABILITY_MMTEL = 33;
    public static final int NET_CAPABILITY_PRIORITIZE_LATENCY = 34;
    public static final int NET_CAPABILITY_PRIORITIZE_BANDWIDTH = 35;
    public static final int NET_CAPABILITY_LOCAL_NETWORK = 36;

    public static final int NET_ENTERPRISE_ID_1 = 1;
    public static final int NET_ENTERPRISE_ID_2 = 2;
    public static final int NET_ENTERPRISE_ID_3 = 3;
    public static final int NET_ENTERPRISE_ID_4 = 4;
    public static final int NET_ENTERPRISE_ID_5 = 5;

    public static final int TRANSPORT_CELLULAR = 0;
    public static final int TRANSPORT_WIFI = 1;
    public static final int TRANSPORT_BLUETOOTH = 2;
    public static final int TRANSPORT_ETHERNET = 3;
    public static final int TRANSPORT_VPN = 4;
    public static final int TRANSPORT_WIFI_AWARE = 5;
    public static final int TRANSPORT_LOWPAN = 6;
    public static final int TRANSPORT_USB = 8;
    public static final int TRANSPORT_THREAD = 9;
    public static final int TRANSPORT_SATELLITE = 10;

    public static final int SIGNAL_STRENGTH_UNSPECIFIED = Integer.MIN_VALUE;

    // capabilities a request asks for unless it removes them (AOSP DEFAULT_CAPABILITIES)
    static final long DEFAULT_CAPABILITIES = (1L << NET_CAPABILITY_INTERNET) | (1L << NET_CAPABILITY_NOT_RESTRICTED)
            | (1L << NET_CAPABILITY_TRUSTED) | (1L << NET_CAPABILITY_NOT_VPN);

    long mNetworkCapabilities;
    long mTransportTypes;
    int mLinkUpBandwidthKbps;
    int mLinkDownBandwidthKbps;
    int mSignalStrength = SIGNAL_STRENGTH_UNSPECIFIED;

    public NetworkCapabilities() {
    }

    public NetworkCapabilities(NetworkCapabilities nc) {
        if (nc != null) {
            mNetworkCapabilities = nc.mNetworkCapabilities;
            mTransportTypes = nc.mTransportTypes;
            mLinkUpBandwidthKbps = nc.mLinkUpBandwidthKbps;
            mLinkDownBandwidthKbps = nc.mLinkDownBandwidthKbps;
            mSignalStrength = nc.mSignalStrength;
        }
    }

    private static int[] bits(long mask) {
        int n = Long.bitCount(mask);
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < 64; i++) {
            if ((mask & (1L << i)) != 0) {
                out[k++] = i;
            }
        }
        return out;
    }

    public int[] getEnterpriseIds() {
        return new int[0];
    }

    public boolean hasEnterpriseId(int enterpriseId) {
        return false;
    }

    public int[] getCapabilities() {
        return bits(mNetworkCapabilities);
    }

    public boolean hasCapability(int capability) {
        return capability >= 0 && capability < 64 && (mNetworkCapabilities & (1L << capability)) != 0;
    }

    public boolean hasTransport(int transportType) {
        return transportType >= 0 && transportType < 64 && (mTransportTypes & (1L << transportType)) != 0;
    }

    public int getOwnerUid() {
        return -1; // Process.INVALID_UID
    }

    public int getLinkUpstreamBandwidthKbps() {
        return mLinkUpBandwidthKbps;
    }

    public int getLinkDownstreamBandwidthKbps() {
        return mLinkDownBandwidthKbps;
    }

    public NetworkSpecifier getNetworkSpecifier() {
        return null;
    }

    public TransportInfo getTransportInfo() {
        return null;
    }

    public int getSignalStrength() {
        return mSignalStrength;
    }

    public Set<Integer> getSubscriptionIds() {
        return Collections.emptySet();
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof NetworkCapabilities)) {
            return false;
        }
        NetworkCapabilities o = (NetworkCapabilities) obj;
        return mNetworkCapabilities == o.mNetworkCapabilities && mTransportTypes == o.mTransportTypes
                && mLinkUpBandwidthKbps == o.mLinkUpBandwidthKbps && mLinkDownBandwidthKbps == o.mLinkDownBandwidthKbps
                && mSignalStrength == o.mSignalStrength;
    }

    public int hashCode() {
        return (int) mNetworkCapabilities * 7 + (int) mTransportTypes * 17 + mLinkUpBandwidthKbps * 23
                + mLinkDownBandwidthKbps * 29 + mSignalStrength * 31;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mNetworkCapabilities);
        dest.writeLong(mTransportTypes);
        dest.writeInt(mLinkUpBandwidthKbps);
        dest.writeInt(mLinkDownBandwidthKbps);
        dest.writeInt(mSignalStrength);
    }

    public static final Parcelable.Creator<NetworkCapabilities> CREATOR =
            new Parcelable.Creator<NetworkCapabilities>() {
        public NetworkCapabilities createFromParcel(Parcel in) {
            NetworkCapabilities nc = new NetworkCapabilities();
            nc.mNetworkCapabilities = in.readLong();
            nc.mTransportTypes = in.readLong();
            nc.mLinkUpBandwidthKbps = in.readInt();
            nc.mLinkDownBandwidthKbps = in.readInt();
            nc.mSignalStrength = in.readInt();
            return nc;
        }

        public NetworkCapabilities[] newArray(int size) {
            return new NetworkCapabilities[size];
        }
    };

    private static final String[] TRANSPORT_NAMES = {"CELLULAR", "WIFI", "BLUETOOTH", "ETHERNET", "VPN",
        "WIFI_AWARE", "LOWPAN", "TEST", "USB", "THREAD", "SATELLITE"};

    private static String capabilityName(int c) {
        switch (c) {
            case NET_CAPABILITY_NOT_METERED: return "NOT_METERED";
            case NET_CAPABILITY_INTERNET: return "INTERNET";
            case NET_CAPABILITY_NOT_RESTRICTED: return "NOT_RESTRICTED";
            case NET_CAPABILITY_TRUSTED: return "TRUSTED";
            case NET_CAPABILITY_NOT_VPN: return "NOT_VPN";
            case NET_CAPABILITY_VALIDATED: return "VALIDATED";
            case NET_CAPABILITY_NOT_ROAMING: return "NOT_ROAMING";
            case NET_CAPABILITY_FOREGROUND: return "FOREGROUND";
            case NET_CAPABILITY_NOT_CONGESTED: return "NOT_CONGESTED";
            case NET_CAPABILITY_NOT_SUSPENDED: return "NOT_SUSPENDED";
            default: return Integer.toString(c);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        int[] t = bits(mTransportTypes);
        if (t.length > 0) {
            sb.append(" Transports: ");
            for (int i = 0; i < t.length; i++) {
                sb.append(i > 0 ? "|" : "").append(t[i] < TRANSPORT_NAMES.length ? TRANSPORT_NAMES[t[i]] : "" + t[i]);
            }
        }
        int[] c = bits(mNetworkCapabilities);
        if (c.length > 0) {
            sb.append(" Capabilities: ");
            for (int i = 0; i < c.length; i++) {
                sb.append(i > 0 ? "&" : "").append(capabilityName(c[i]));
            }
        }
        if (mLinkUpBandwidthKbps > 0) {
            sb.append(" LinkUpBandwidth>=").append(mLinkUpBandwidthKbps).append("Kbps");
        }
        if (mLinkDownBandwidthKbps > 0) {
            sb.append(" LinkDnBandwidth>=").append(mLinkDownBandwidthKbps).append("Kbps");
        }
        if (mSignalStrength != SIGNAL_STRENGTH_UNSPECIFIED) {
            sb.append(" SignalStrength: ").append(mSignalStrength);
        }
        return sb.append("]").toString();
    }
}
