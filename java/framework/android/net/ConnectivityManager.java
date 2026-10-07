package android.net;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;

/**
 * Connectivity from the platform (src/android/android_net.c, platform_network_state): one default network, Wi-Fi or
 * Ethernet, never metered or roaming. Callbacks get the AOSP sequence on registration and the state is polled every
 * few seconds while anyone listens (callbacks or a CONNECTIVITY_ACTION receiver), so changes produce onLost /
 * onAvailable and a new sticky CONNECTIVITY_ACTION.
 */
public class ConnectivityManager {
    public static final String ACTION_BACKGROUND_DATA_SETTING_CHANGED =
            "android.net.conn.BACKGROUND_DATA_SETTING_CHANGED";
    public static final String ACTION_CAPTIVE_PORTAL_SIGN_IN = "android.net.conn.CAPTIVE_PORTAL";
    public static final String ACTION_RESTRICT_BACKGROUND_CHANGED = "android.net.conn.RESTRICT_BACKGROUND_CHANGED";
    @Deprecated
    public static final String CONNECTIVITY_ACTION = "android.net.conn.CONNECTIVITY_CHANGE";
    @Deprecated
    public static final int DEFAULT_NETWORK_PREFERENCE = 1;
    public static final String EXTRA_CAPTIVE_PORTAL = "android.net.extra.CAPTIVE_PORTAL";
    public static final String EXTRA_CAPTIVE_PORTAL_URL = "android.net.extra.CAPTIVE_PORTAL_URL";
    @Deprecated
    public static final String EXTRA_EXTRA_INFO = "extraInfo";
    @Deprecated
    public static final String EXTRA_IS_FAILOVER = "isFailover";
    public static final String EXTRA_NETWORK = "android.net.extra.NETWORK";
    @Deprecated
    public static final String EXTRA_NETWORK_INFO = "networkInfo";
    public static final String EXTRA_NETWORK_REQUEST = "android.net.extra.NETWORK_REQUEST";
    @Deprecated
    public static final String EXTRA_NETWORK_TYPE = "networkType";
    public static final String EXTRA_NO_CONNECTIVITY = "noConnectivity";
    @Deprecated
    public static final String EXTRA_OTHER_NETWORK_INFO = "otherNetwork";
    public static final String EXTRA_REASON = "reason";
    public static final int MULTIPATH_PREFERENCE_HANDOVER = 1;
    public static final int MULTIPATH_PREFERENCE_RELIABILITY = 2;
    public static final int MULTIPATH_PREFERENCE_PERFORMANCE = 4;
    public static final int RESTRICT_BACKGROUND_STATUS_DISABLED = 1;
    public static final int RESTRICT_BACKGROUND_STATUS_WHITELISTED = 2;
    public static final int RESTRICT_BACKGROUND_STATUS_ENABLED = 3;
    @Deprecated
    public static final int TYPE_MOBILE = 0;
    @Deprecated
    public static final int TYPE_WIFI = 1;
    @Deprecated
    public static final int TYPE_MOBILE_MMS = 2;
    @Deprecated
    public static final int TYPE_MOBILE_SUPL = 3;
    @Deprecated
    public static final int TYPE_MOBILE_DUN = 4;
    @Deprecated
    public static final int TYPE_MOBILE_HIPRI = 5;
    @Deprecated
    public static final int TYPE_WIMAX = 6;
    @Deprecated
    public static final int TYPE_BLUETOOTH = 7;
    @Deprecated
    public static final int TYPE_DUMMY = 8;
    @Deprecated
    public static final int TYPE_ETHERNET = 9;
    @Deprecated
    public static final int TYPE_VPN = 17;

    private static final int POLL_MS = 3000;
    // platform transports (PLATFORM_NET_*) and the net ids we give their networks
    private static final int PLATFORM_WIFI = 1;
    private static final int PLATFORM_ETHERNET = 2;
    private static final int NETID_BASE = 100;

    private static ConnectivityManager sInstance;
    private static Network sProcessNetwork;

    private final Context mContext;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final ArrayList<Record> mRecords = new ArrayList<Record>();
    private final ArrayList<OnNetworkActiveListener> mActiveListeners = new ArrayList<OnNetworkActiveListener>();
    private int mState;
    private boolean mPolling;
    private boolean mWatchBroadcasts;
    private int mPreference = DEFAULT_NETWORK_PREFERENCE;

    private final Runnable mPoll = new Runnable() {
        public void run() {
            synchronized (mRecords) {
                mPolling = false;
            }
            refresh();
            schedulePoll();
        }
    };

    private static final class Record {
        final NetworkRequest request; // null: the default network
        final NetworkCallback callback;
        final Handler handler;
        Network current;

        Record(NetworkRequest request, NetworkCallback callback, Handler handler) {
            this.request = request;
            this.callback = callback;
            this.handler = handler;
        }
    }

    ConnectivityManager(Context context) {
        mContext = context;
        mState = nGetState();
    }

    /** Framework-internal: the process-wide instance behind Context.CONNECTIVITY_SERVICE. */
    public static synchronized ConnectivityManager from(Context context) {
        if (sInstance == null) {
            Context app = context.getApplicationContext();
            sInstance = new ConnectivityManager(app != null ? app : context);
        }
        return sInstance;
    }

    /**
     * Framework-internal: the sticky CONNECTIVITY_ACTION for the current state, and from now on keep it current. Called
     * by the broadcast queue when the sticky list is built and when a receiver for it registers.
     */
    public static Intent stickyConnectivityIntent(Context context) {
        ConnectivityManager cm = from(context);
        synchronized (cm.mRecords) {
            cm.mWatchBroadcasts = true;
        }
        cm.schedulePoll();
        return cm.connectivityIntent(nGetState());
    }

    static native int nGetState();

    private static boolean connected(int s) {
        return (s & 1) != 0;
    }

    private static int platformTransport(int s) {
        return (s >> 1) & 7;
    }

    private static int bars(int s) {
        return ((s >> 4) & 15) - 1;
    }

    private static int legacyType(int s) {
        return platformTransport(s) == PLATFORM_ETHERNET ? TYPE_ETHERNET : TYPE_WIFI;
    }

    private static Network networkFor(int s) {
        return connected(s) ? new Network(NETID_BASE + platformTransport(s)) : null;
    }

    private static NetworkCapabilities capabilitiesFor(int s) {
        NetworkCapabilities nc = new NetworkCapabilities();
        int[] caps = {NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
            NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED, NetworkCapabilities.NET_CAPABILITY_TRUSTED,
            NetworkCapabilities.NET_CAPABILITY_NOT_VPN, NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING, NetworkCapabilities.NET_CAPABILITY_FOREGROUND,
            NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED, NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED};
        for (int c : caps) {
            nc.mNetworkCapabilities |= 1L << c;
        }
        boolean ethernet = platformTransport(s) == PLATFORM_ETHERNET;
        nc.mTransportTypes = 1L << (ethernet ? NetworkCapabilities.TRANSPORT_ETHERNET : NetworkCapabilities.TRANSPORT_WIFI);
        nc.mLinkUpBandwidthKbps = ethernet ? 100000 : 30000;
        nc.mLinkDownBandwidthKbps = ethernet ? 100000 : 30000;
        int b = bars(s);
        nc.mSignalStrength = ethernet || b < 0 ? NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED : -85 + 15 * b;
        return nc;
    }

    private static LinkProperties linkPropertiesFor(int s) {
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(platformTransport(s) == PLATFORM_ETHERNET ? "eth0" : "wlan0");
        lp.setMtu(1500);
        return lp;
    }

    private int state() {
        int s = nGetState();
        if (s != mState) {
            refresh();
        }
        return s;
    }

    private Intent connectivityIntent(int s) {
        Intent intent = new Intent(CONNECTIVITY_ACTION);
        NetworkInfo ni = NetworkInfo.of(legacyType(s), connected(s), true);
        intent.putExtra(EXTRA_NETWORK_INFO, ni);
        intent.putExtra(EXTRA_NETWORK_TYPE, ni.getType());
        if (!connected(s)) {
            intent.putExtra(EXTRA_NO_CONNECTIVITY, true);
        }
        return intent;
    }

    public static boolean isNetworkTypeValid(int networkType) {
        return (networkType >= TYPE_MOBILE && networkType <= TYPE_ETHERNET) || networkType == TYPE_VPN
                || (networkType >= 10 && networkType <= 16);
    }

    public void setNetworkPreference(int preference) {
        mPreference = preference;
    }

    public int getNetworkPreference() {
        return mPreference;
    }

    public NetworkInfo getActiveNetworkInfo() {
        int s = state();
        return connected(s) ? NetworkInfo.of(legacyType(s), true, true) : null;
    }

    public Network getActiveNetwork() {
        return networkFor(state());
    }

    public NetworkInfo getNetworkInfo(int networkType) {
        int s = state();
        if (networkType != TYPE_WIFI && networkType != TYPE_ETHERNET) {
            return null;
        }
        if (networkType == TYPE_ETHERNET && legacyType(s) != TYPE_ETHERNET) {
            return null;
        }
        boolean up = connected(s) && legacyType(s) == networkType;
        return NetworkInfo.of(networkType, up, true);
    }

    public NetworkInfo getNetworkInfo(Network network) {
        int s = state();
        return network != null && network.equals(networkFor(s)) ? NetworkInfo.of(legacyType(s), true, true) : null;
    }

    public NetworkInfo[] getAllNetworkInfo() {
        int s = state();
        if (legacyType(s) == TYPE_ETHERNET) {
            return new NetworkInfo[] {NetworkInfo.of(TYPE_WIFI, false, true), NetworkInfo.of(TYPE_ETHERNET, connected(s), true)};
        }
        return new NetworkInfo[] {NetworkInfo.of(TYPE_WIFI, connected(s), true)};
    }

    public Network[] getAllNetworks() {
        Network n = networkFor(state());
        return n != null ? new Network[] {n} : new Network[0];
    }

    public LinkProperties getLinkProperties(Network network) {
        int s = state();
        return network != null && network.equals(networkFor(s)) ? linkPropertiesFor(s) : null;
    }

    public NetworkCapabilities getNetworkCapabilities(Network network) {
        int s = state();
        return network != null && network.equals(networkFor(s)) ? capabilitiesFor(s) : null;
    }

    @Deprecated
    public boolean getBackgroundDataSetting() {
        return true;
    }

    public void addDefaultNetworkActiveListener(OnNetworkActiveListener l) {
        synchronized (mActiveListeners) {
            mActiveListeners.add(l);
        }
    }

    public void removeDefaultNetworkActiveListener(OnNetworkActiveListener l) {
        synchronized (mActiveListeners) {
            if (!mActiveListeners.remove(l)) {
                throw new IllegalArgumentException("Listener not registered: " + l);
            }
        }
    }

    public boolean isDefaultNetworkActive() {
        return connected(state());
    }

    @Deprecated
    public void reportBadNetwork(Network network) {
    }

    public void reportNetworkConnectivity(Network network, boolean hasConnectivity) {
    }

    public boolean isActiveNetworkMetered() {
        return false;
    }

    public void requestNetwork(NetworkRequest request, NetworkCallback networkCallback) {
        requestNetwork(request, networkCallback, null, 0);
    }

    public void requestNetwork(NetworkRequest request, NetworkCallback networkCallback, Handler handler) {
        requestNetwork(request, networkCallback, handler, 0);
    }

    public void requestNetwork(NetworkRequest request, NetworkCallback networkCallback, int timeoutMs) {
        requestNetwork(request, networkCallback, null, timeoutMs);
    }

    public void requestNetwork(NetworkRequest request, final NetworkCallback networkCallback, Handler handler,
            int timeoutMs) {
        if (request == null) {
            throw new IllegalArgumentException("null NetworkRequest");
        }
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Non-positive timeoutMs: " + timeoutMs);
        }
        final Record r = register(request, networkCallback, handler);
        if (timeoutMs > 0) {
            r.handler.postDelayed(new Runnable() {
                public void run() {
                    boolean unavailable;
                    synchronized (mRecords) {
                        unavailable = mRecords.contains(r) && r.current == null;
                        if (unavailable) {
                            mRecords.remove(r);
                        }
                    }
                    if (unavailable) {
                        networkCallback.onUnavailable();
                    }
                }
            }, timeoutMs);
        }
    }

    public void requestNetwork(NetworkRequest request, PendingIntent operation) {
    }

    public void releaseNetworkRequest(PendingIntent operation) {
    }

    public void registerNetworkCallback(NetworkRequest request, NetworkCallback networkCallback) {
        registerNetworkCallback(request, networkCallback, null);
    }

    public void registerNetworkCallback(NetworkRequest request, NetworkCallback networkCallback, Handler handler) {
        if (request == null) {
            throw new IllegalArgumentException("null NetworkRequest");
        }
        register(request, networkCallback, handler);
    }

    public void registerNetworkCallback(NetworkRequest request, PendingIntent operation) {
    }

    public void registerDefaultNetworkCallback(NetworkCallback networkCallback) {
        registerDefaultNetworkCallback(networkCallback, null);
    }

    public void registerDefaultNetworkCallback(NetworkCallback networkCallback, Handler handler) {
        register(null, networkCallback, handler);
    }

    public void registerBestMatchingNetworkCallback(NetworkRequest request, NetworkCallback networkCallback,
            Handler handler) {
        registerNetworkCallback(request, networkCallback, handler);
    }

    public boolean requestBandwidthUpdate(Network network) {
        return true;
    }

    public void unregisterNetworkCallback(NetworkCallback networkCallback) {
        if (networkCallback == null) {
            throw new IllegalArgumentException("null NetworkCallback");
        }
        synchronized (mRecords) {
            for (int i = 0; i < mRecords.size(); i++) {
                if (mRecords.get(i).callback == networkCallback) {
                    mRecords.remove(i);
                    return;
                }
            }
        }
        throw new IllegalArgumentException("NetworkCallback was not registered");
    }

    public void unregisterNetworkCallback(PendingIntent operation) {
    }

    public int getMultipathPreference(Network network) {
        return 0;
    }

    public boolean bindProcessToNetwork(Network network) {
        sProcessNetwork = network;
        return true;
    }

    @Deprecated
    public static boolean setProcessDefaultNetwork(Network network) {
        sProcessNetwork = network;
        return true;
    }

    public Network getBoundNetworkForProcess() {
        return sProcessNetwork;
    }

    @Deprecated
    public static Network getProcessDefaultNetwork() {
        return sProcessNetwork;
    }

    public int getRestrictBackgroundStatus() {
        return RESTRICT_BACKGROUND_STATUS_DISABLED;
    }

    public byte[] getNetworkWatchlistConfigHash() {
        return null;
    }

    public int getConnectionOwnerUid(int protocol, InetSocketAddress local, InetSocketAddress remote) {
        return android.os.Process.myUid();
    }

    // ---------------------------------------------------------------- callbacks

    private Record register(NetworkRequest request, NetworkCallback cb, Handler handler) {
        if (cb == null) {
            throw new IllegalArgumentException("null NetworkCallback");
        }
        Handler h = handler != null ? handler : mMain;
        Record r = new Record(request, cb, h);
        int s = nGetState();
        synchronized (mRecords) {
            for (Record o : mRecords) {
                if (o.callback == cb) {
                    throw new IllegalArgumentException("NetworkCallback was already registered");
                }
            }
            mRecords.add(r);
        }
        if (s != mState) {
            refresh();
        } else {
            update(r, s, true);
        }
        schedulePoll();
        return r;
    }

    private static boolean satisfies(Record r, int s) {
        if (!connected(s)) {
            return false;
        }
        return r.request == null || r.request.canBeSatisfiedBy(capabilitiesFor(s));
    }

    /** Brings one record in line with state s, posting the callbacks Android would send. */
    private void update(final Record r, final int s, boolean fresh) {
        final Network now = satisfies(r, s) ? networkFor(s) : null;
        final Network before;
        synchronized (mRecords) {
            before = r.current;
            r.current = now;
        }
        final boolean same = now != null && now.equals(before);
        if (same && fresh) {
            return;
        }
        r.handler.post(new Runnable() {
            public void run() {
                if (before != null && !same) {
                    r.callback.onLost(before);
                }
                if (now != null && !same) {
                    r.callback.onAvailable(now);
                    r.callback.onCapabilitiesChanged(now, capabilitiesFor(s));
                    r.callback.onLinkPropertiesChanged(now, linkPropertiesFor(s));
                    r.callback.onBlockedStatusChanged(now, false);
                } else if (same) {
                    r.callback.onCapabilitiesChanged(now, capabilitiesFor(s));
                }
            }
        });
    }

    /** Re-reads the platform state and notifies callbacks and broadcast receivers of any change. */
    void refresh() {
        int s = nGetState();
        Record[] records;
        boolean broadcast;
        synchronized (mRecords) {
            if (s == mState) {
                return;
            }
            boolean networkChanged = connected(s) != connected(mState) || platformTransport(s) != platformTransport(mState);
            mState = s;
            records = mRecords.toArray(new Record[mRecords.size()]);
            broadcast = mWatchBroadcasts && networkChanged;
        }
        for (Record r : records) {
            update(r, s, false);
        }
        if (broadcast) {
            mContext.sendStickyBroadcast(connectivityIntent(s));
        }
        if (connected(s)) {
            OnNetworkActiveListener[] ls;
            synchronized (mActiveListeners) {
                ls = mActiveListeners.toArray(new OnNetworkActiveListener[mActiveListeners.size()]);
            }
            for (final OnNetworkActiveListener l : ls) {
                mMain.post(new Runnable() {
                    public void run() {
                        l.onNetworkActive();
                    }
                });
            }
        }
    }

    private void schedulePoll() {
        synchronized (mRecords) {
            if (mPolling || (mRecords.isEmpty() && !mWatchBroadcasts)) {
                return;
            }
            mPolling = true;
        }
        mMain.postDelayed(mPoll, POLL_MS);
    }

    public static class NetworkCallback {
        public static final int FLAG_INCLUDE_LOCATION_INFO = 1;

        public NetworkCallback() {
        }

        public NetworkCallback(int flags) {
        }

        public void onAvailable(Network network) {
        }

        public void onLosing(Network network, int maxMsToLive) {
        }

        public void onLost(Network network) {
        }

        public void onUnavailable() {
        }

        public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
        }

        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
        }

        public void onBlockedStatusChanged(Network network, boolean blocked) {
        }
    }

    public interface OnNetworkActiveListener {
        void onNetworkActive();
    }
}
