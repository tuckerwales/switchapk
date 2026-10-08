package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class LinkAddress implements Parcelable {
    private final InetAddress address;
    private final int prefixLength;
    private final int flags;
    private final int scope;

    /** Framework-internal. */
    public LinkAddress(InetAddress address, int prefixLength, int flags, int scope) {
        this.address = address;
        this.prefixLength = prefixLength;
        this.flags = flags;
        this.scope = scope;
    }

    public String toString() {
        return address.getHostAddress() + "/" + prefixLength;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof LinkAddress)) {
            return false;
        }
        LinkAddress o = (LinkAddress) obj;
        return address.equals(o.address) && prefixLength == o.prefixLength && flags == o.flags && scope == o.scope;
    }

    public int hashCode() {
        return address.hashCode() + 11 * prefixLength + 19 * flags + 43 * scope;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    public int getFlags() {
        return flags;
    }

    public int getScope() {
        return scope;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(address.getAddress());
        dest.writeInt(prefixLength);
        dest.writeInt(this.flags);
        dest.writeInt(scope);
    }

    public static final Parcelable.Creator<LinkAddress> CREATOR = new Parcelable.Creator<LinkAddress>() {
        public LinkAddress createFromParcel(Parcel in) {
            try {
                return new LinkAddress(InetAddress.getByAddress(in.createByteArray()), in.readInt(), in.readInt(),
                        in.readInt());
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException(e);
            }
        }

        public LinkAddress[] newArray(int size) {
            return new LinkAddress[size];
        }
    };
}
