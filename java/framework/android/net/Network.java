package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import javax.net.SocketFactory;

/** A network the system knows about. There is only ever the one default network, so binding is a no-op. */
public class Network implements Parcelable {
    /** Framework-internal: read by ConnectivityManager. */
    final int netId;

    /** Framework-internal. */
    public Network(int netId) {
        this.netId = netId;
    }

    public InetAddress[] getAllByName(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }

    public InetAddress getByName(String host) throws UnknownHostException {
        return InetAddress.getByName(host);
    }

    public SocketFactory getSocketFactory() {
        return SocketFactory.getDefault();
    }

    public URLConnection openConnection(URL url) throws IOException {
        return url.openConnection();
    }

    public URLConnection openConnection(URL url, Proxy proxy) throws IOException {
        if (proxy == null) {
            throw new IllegalArgumentException("proxy is null");
        }
        return url.openConnection(proxy);
    }

    public void bindSocket(DatagramSocket socket) throws IOException {
    }

    public void bindSocket(Socket socket) throws IOException {
        if (socket.isConnected()) {
            throw new java.net.SocketException("Socket is connected");
        }
    }

    public void bindSocket(FileDescriptor fd) throws IOException {
    }

    public static Network fromNetworkHandle(long networkHandle) {
        if (networkHandle == 0) {
            throw new IllegalArgumentException("Network.fromNetworkHandle refusing to instantiate NETID_UNSET Network.");
        }
        if ((networkHandle & 0xffffffffL) != 0xfacade || networkHandle < 0) {
            throw new IllegalArgumentException("Value passed to fromNetworkHandle() is not a network handle.");
        }
        return new Network((int) (networkHandle >>> 32));
    }

    public long getNetworkHandle() {
        return netId == 0 ? 0L : (((long) netId) << 32) | 0xfacade;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(netId);
    }

    public static final Parcelable.Creator<Network> CREATOR = new Parcelable.Creator<Network>() {
        public Network createFromParcel(Parcel in) {
            return new Network(in.readInt());
        }

        public Network[] newArray(int size) {
            return new Network[size];
        }
    };

    public boolean equals(Object obj) {
        return obj instanceof Network && ((Network) obj).netId == netId;
    }

    public int hashCode() {
        return netId * 11;
    }

    public String toString() {
        return Integer.toString(netId);
    }
}
