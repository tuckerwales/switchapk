package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Interface name, addresses, DNS servers, domains and MTU. Routes, proxies and NAT64 are not modelled. */
public final class LinkProperties implements Parcelable {
    private String mIfaceName;
    private final ArrayList<LinkAddress> mLinkAddresses = new ArrayList<LinkAddress>();
    private final ArrayList<InetAddress> mDnses = new ArrayList<InetAddress>();
    private String mDomains;
    private int mMtu;
    private Inet4Address mDhcpServerAddress;

    public LinkProperties() {
    }

    public void setInterfaceName(String iface) {
        mIfaceName = iface;
    }

    public String getInterfaceName() {
        return mIfaceName;
    }

    public List<LinkAddress> getLinkAddresses() {
        return Collections.unmodifiableList(mLinkAddresses);
    }

    public void setLinkAddresses(Collection<LinkAddress> addresses) {
        mLinkAddresses.clear();
        mLinkAddresses.addAll(addresses);
    }

    public void setDnsServers(Collection<InetAddress> dnsServers) {
        mDnses.clear();
        mDnses.addAll(dnsServers);
    }

    public List<InetAddress> getDnsServers() {
        return Collections.unmodifiableList(mDnses);
    }

    public boolean isPrivateDnsActive() {
        return false;
    }

    public void setDhcpServerAddress(Inet4Address serverAddress) {
        mDhcpServerAddress = serverAddress;
    }

    public Inet4Address getDhcpServerAddress() {
        return mDhcpServerAddress;
    }

    public String getPrivateDnsServerName() {
        return null;
    }

    public void setDomains(String domains) {
        mDomains = domains;
    }

    public String getDomains() {
        return mDomains;
    }

    public void setMtu(int mtu) {
        mMtu = mtu;
    }

    public int getMtu() {
        return mMtu;
    }

    public void clear() {
        mIfaceName = null;
        mLinkAddresses.clear();
        mDnses.clear();
        mDomains = null;
        mMtu = 0;
        mDhcpServerAddress = null;
    }

    public boolean isWakeOnLanSupported() {
        return false;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mIfaceName);
        dest.writeString(mDomains);
        dest.writeInt(mMtu);
    }

    public static final Parcelable.Creator<LinkProperties> CREATOR = new Parcelable.Creator<LinkProperties>() {
        public LinkProperties createFromParcel(Parcel in) {
            LinkProperties lp = new LinkProperties();
            lp.mIfaceName = in.readString();
            lp.mDomains = in.readString();
            lp.mMtu = in.readInt();
            return lp;
        }

        public LinkProperties[] newArray(int size) {
            return new LinkProperties[size];
        }
    };

    public String toString() {
        return "{InterfaceName: " + mIfaceName + " LinkAddresses: " + mLinkAddresses + " DnsAddresses: " + mDnses + " Domains: " + mDomains + " MTU: " + mMtu
                + "}";
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof LinkProperties)) {
            return false;
        }
        LinkProperties o = (LinkProperties) obj;
        return java.util.Objects.equals(mIfaceName, o.mIfaceName) && mLinkAddresses.equals(o.mLinkAddresses)
                && mDnses.equals(o.mDnses)
                && java.util.Objects.equals(mDomains, o.mDomains) && mMtu == o.mMtu;
    }

    public int hashCode() {
        return (mIfaceName == null ? 0 : mIfaceName.hashCode()) + 31 * mMtu;
    }
}
