package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.Set;

public class NetworkRequest implements Parcelable {
    /** Framework-internal: the capabilities and transports the network must have. */
    final NetworkCapabilities networkCapabilities;

    NetworkRequest(NetworkCapabilities nc) {
        networkCapabilities = nc;
    }

    public static class Builder {
        private final NetworkCapabilities mCaps;

        public Builder() {
            mCaps = new NetworkCapabilities();
            mCaps.mNetworkCapabilities = NetworkCapabilities.DEFAULT_CAPABILITIES;
        }

        public Builder(NetworkRequest request) {
            mCaps = new NetworkCapabilities(request.networkCapabilities);
        }

        public NetworkRequest build() {
            return new NetworkRequest(new NetworkCapabilities(mCaps));
        }

        public Builder addCapability(int capability) {
            mCaps.mNetworkCapabilities |= 1L << capability;
            return this;
        }

        public Builder removeCapability(int capability) {
            mCaps.mNetworkCapabilities &= ~(1L << capability);
            return this;
        }

        public Builder clearCapabilities() {
            mCaps.mNetworkCapabilities = 0;
            return this;
        }

        public Builder addTransportType(int transportType) {
            mCaps.mTransportTypes |= 1L << transportType;
            return this;
        }

        public Builder removeTransportType(int transportType) {
            mCaps.mTransportTypes &= ~(1L << transportType);
            return this;
        }

        public Builder setNetworkSpecifier(String networkSpecifier) {
            return this;
        }

        public Builder setNetworkSpecifier(NetworkSpecifier networkSpecifier) {
            return this;
        }

        public Builder setSubscriptionIds(Set<Integer> subIds) {
            return this;
        }

        public Builder setIncludeOtherUidNetworks(boolean include) {
            return this;
        }
    }

    public boolean hasCapability(int capability) {
        return networkCapabilities.hasCapability(capability);
    }

    public boolean hasTransport(int transportType) {
        return networkCapabilities.hasTransport(transportType);
    }

    /** True when nc has every requested capability and, if transports were requested, one of them. */
    public boolean canBeSatisfiedBy(NetworkCapabilities nc) {
        if (nc == null) {
            return false;
        }
        long caps = networkCapabilities.mNetworkCapabilities;
        if ((nc.mNetworkCapabilities & caps) != caps) {
            return false;
        }
        long transports = networkCapabilities.mTransportTypes;
        return transports == 0 || (nc.mTransportTypes & transports) != 0;
    }

    public NetworkSpecifier getNetworkSpecifier() {
        return null;
    }

    public int[] getCapabilities() {
        return networkCapabilities.getCapabilities();
    }

    public int[] getTransportTypes() {
        int[] out = new int[Long.bitCount(networkCapabilities.mTransportTypes)];
        int k = 0;
        for (int i = 0; i < 64; i++) {
            if ((networkCapabilities.mTransportTypes & (1L << i)) != 0) {
                out[k++] = i;
            }
        }
        return out;
    }

    public Set<Integer> getSubscriptionIds() {
        return Collections.emptySet();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        networkCapabilities.writeToParcel(dest, flags);
    }

    public static final Parcelable.Creator<NetworkRequest> CREATOR = new Parcelable.Creator<NetworkRequest>() {
        public NetworkRequest createFromParcel(Parcel in) {
            return new NetworkRequest(NetworkCapabilities.CREATOR.createFromParcel(in));
        }

        public NetworkRequest[] newArray(int size) {
            return new NetworkRequest[size];
        }
    };

    public String toString() {
        return "NetworkRequest [ " + networkCapabilities + " ]";
    }

    public boolean equals(Object obj) {
        return obj instanceof NetworkRequest && ((NetworkRequest) obj).networkCapabilities.equals(networkCapabilities);
    }

    public int hashCode() {
        return networkCapabilities.hashCode();
    }
}
