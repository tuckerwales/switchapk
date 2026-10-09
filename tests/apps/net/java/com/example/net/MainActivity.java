package com.example.net;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

/**
 * Networking on the host: HTTP and UDP over loopback against a server in this process, https and DNS failures, and
 * ConnectivityManager. The app then switches the host's reported network (none, then ethernet) by writing
 * /data/local/tmp/network and waits for the callbacks and the CONNECTIVITY_ACTION broadcast. The step in progress is
 * shown under the title (and logged), so a run that stalls on a console shows where.
 */
public class MainActivity extends Activity {
    private static final String TAG = "NET";
    private static final File NETWORK_FILE = new File("/data/local/tmp/network");

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Set<String> mSeen = new HashSet<String>();
    private ConnectivityManager mCm;
    private FrameLayout mRoot;
    private TextView mText;
    private TextView mStatus;

    private void log(String s) {
        Log.i(TAG, s);
        synchronized (mSeen) {
            mSeen.add(s);
        }
    }

    /** Shows and logs the step about to run. */
    private void step(final String s) {
        Log.i(TAG, "step " + s);
        mMain.post(new Runnable() {
            public void run() {
                mStatus.setText(s);
            }
        });
    }

    private boolean seen(String s) {
        synchronized (mSeen) {
            return mSeen.contains(s);
        }
    }

    private static void setNetwork(String mode) {
        try {
            NETWORK_FILE.getParentFile().mkdirs();
            FileOutputStream out = new FileOutputStream(NETWORK_FILE);
            out.write(mode.getBytes("UTF-8"));
            out.close();
        } catch (IOException e) {
            Log.e(TAG, "cannot write " + NETWORK_FILE, e);
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        NETWORK_FILE.delete();
        mRoot = new FrameLayout(this);
        mRoot.setBackgroundColor(Color.GRAY);
        mText = new TextView(this);
        mText.setTextColor(Color.WHITE);
        mText.setTextSize(24);
        mText.setText("networking...");
        mStatus = new TextView(this);
        mStatus.setTextColor(Color.WHITE);
        mStatus.setTextSize(14);
        mStatus.setText("starting");
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.addView(mText, new LinearLayout.LayoutParams(-2, -2));
        column.addView(mStatus, new LinearLayout.LayoutParams(-2, -2));
        mRoot.addView(column, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        setContentView(mRoot);

        mCm = getSystemService(ConnectivityManager.class);
        NetworkInfo info = mCm.getActiveNetworkInfo();
        log("active " + info.getTypeName() + " connected=" + info.isConnected() + " type=" + info.getType());
        Network n = mCm.getActiveNetwork();
        NetworkCapabilities caps = mCm.getNetworkCapabilities(n);
        log("caps wifi=" + caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) + " internet="
                + caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) + " validated="
                + caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) + " metered="
                + mCm.isActiveNetworkMetered());
        LinkProperties lp = mCm.getLinkProperties(n);
        log("link " + lp.getInterfaceName() + " handle=" + (Network.fromNetworkHandle(n.getNetworkHandle()).equals(n)));

        mCm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                NetworkCapabilities c = mCm.getNetworkCapabilities(network);
                log("default available " + (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "ethernet" : "wifi"));
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities c) {
                log("default caps internet=" + c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
            }

            @Override
            public void onLost(Network network) {
                log("default lost");
            }
        });
        mCm.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build(), new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                log("ethernet available");
            }
        });
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean none = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
                NetworkInfo ni = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                log("broadcast noConnectivity=" + none + " type=" + ni.getTypeName());
            }
        }, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        new Thread("net-test") {
            public void run() {
                try {
                    sockets();
                } catch (Throwable t) {
                    Log.e(TAG, "sockets failed", t);
                    step("sockets failed: " + t);
                }
                switchNetworks();
            }
        }.start();
    }

    // ---- loopback server ------------------------------------------------------------------------------------------

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0 && c != '\n') {
            if (c != '\r') {
                sb.append((char) c);
            }
        }
        return sb.toString();
    }

    private static void serve(Socket c) throws IOException {
        InputStream in = new BufferedInputStream(c.getInputStream());
        String[] req = readLine(in).split(" ");
        int length = 0;
        String line;
        while (!(line = readLine(in)).isEmpty()) {
            if (line.toLowerCase().startsWith("content-length:")) {
                length = Integer.parseInt(line.substring(15).trim());
            }
        }
        byte[] body = new byte[length];
        for (int i = 0; i < length; i++) {
            body[i] = (byte) in.read();
        }
        String resp;
        if (req[1].equals("/hello")) {
            resp = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\n\r\n"
                    + "6\r\nhello \r\nf\r\nfrom switchapk!\r\n0\r\n\r\n";
        } else if (req[1].equals("/echo")) {
            String b = new String(body, "UTF-8");
            resp = "HTTP/1.1 201 Created\r\nContent-Length: " + b.length() + "\r\n\r\n" + b;
        } else {
            resp = "HTTP/1.1 404 Not Found\r\nContent-Length: 4\r\n\r\nnope";
        }
        OutputStream out = c.getOutputStream();
        out.write(resp.getBytes("UTF-8"));
        out.flush();
        c.close();
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[256];
        int n;
        while ((n = in.read(b)) > 0) {
            bo.write(b, 0, n);
        }
        in.close();
        return bo.toString("UTF-8");
    }

    private void sockets() throws Exception {
        step("loopback server");
        final ServerSocket ss = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread server = new Thread("net-server") {
            public void run() {
                try {
                    for (;;) {
                        serve(ss.accept());
                    }
                } catch (IOException e) {
                    // closed
                }
            }
        };
        server.setDaemon(true);
        server.start();
        String base = "http://127.0.0.1:" + ss.getLocalPort();

        step("http GET " + base + "/hello");
        HttpURLConnection c = (HttpURLConnection) new URL(base + "/hello").openConnection();
        log("http " + c.getResponseCode() + " " + c.getContentType() + " " + readAll(c.getInputStream()));

        step("http POST");
        c = (HttpURLConnection) new URL(base + "/echo").openConnection();
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.getOutputStream().write("{\"a\":1}".getBytes("UTF-8"));
        log("post " + c.getResponseCode() + " " + readAll(c.getInputStream()));

        step("http 404");
        c = (HttpURLConnection) new URL(base + "/missing").openConnection();
        try {
            c.getInputStream();
        } catch (FileNotFoundException e) {
            log("404 " + c.getResponseCode() + " err=" + readAll(c.getErrorStream()));
        }

        step("https");
        try {
            new URL("https://example.com/").openConnection().getInputStream();
        } catch (IOException e) {
            log("https " + e.getClass().getName());
        }
        step("dns lookup of no-such-host.invalid");
        try {
            InetAddress.getByName("no-such-host.invalid");
        } catch (UnknownHostException e) {
            log("dns UnknownHostException");
        }

        step("udp");
        DatagramSocket a = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        DatagramSocket b = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        byte[] msg = "ping".getBytes("UTF-8");
        a.send(new DatagramPacket(msg, msg.length, InetAddress.getLoopbackAddress(), b.getLocalPort()));
        DatagramPacket p = new DatagramPacket(new byte[16], 16);
        b.setSoTimeout(2000);
        b.receive(p);
        log("udp " + new String(p.getData(), 0, p.getLength(), "UTF-8"));
        step("closing sockets");
        a.close();
        b.close();
        ss.close();
    }

    // ---- connectivity changes -------------------------------------------------------------------------------------

    private boolean waitFor(String line, long ms) {
        step("waiting up to " + ms / 1000 + " s for: " + line);
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (seen(line)) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                return false;
            }
        }
        Log.w(TAG, "timed out waiting for " + line);
        return false;
    }

    private void switchNetworks() {
        waitFor("broadcast noConnectivity=false type=WIFI", 2000);
        setNetwork("none");
        waitFor("default lost", 5000);
        waitFor("broadcast noConnectivity=true type=WIFI", 2000);
        log("offline active=" + mCm.getActiveNetworkInfo() + " network=" + mCm.getActiveNetwork());
        setNetwork("ethernet");
        waitFor("ethernet available", 5000);
        waitFor("broadcast noConnectivity=false type=ETHERNET", 2000);
        log("online active=" + mCm.getActiveNetworkInfo().getTypeName());
        NETWORK_FILE.delete();

        final String[] expected = {
            "active WIFI connected=true type=1",
            "caps wifi=true internet=true validated=true metered=false",
            "link wlan0 handle=true",
            "default available wifi",
            "default caps internet=true",
            "broadcast noConnectivity=false type=WIFI",
            "http 200 text/plain hello from switchapk!",
            "post 201 {\"a\":1}",
            "404 404 err=nope",
            "https javax.net.ssl.SSLHandshakeException",
            "dns UnknownHostException",
            "udp ping",
            "default lost",
            "broadcast noConnectivity=true type=WIFI",
            "offline active=null network=null",
            "ethernet available",
            "default available ethernet",
            "broadcast noConnectivity=false type=ETHERNET",
            "online active=ETHERNET",
        };
        mMain.post(new Runnable() {
            public void run() {
                int missing = 0;
                for (String e : expected) {
                    if (!seen(e)) {
                        Log.w(TAG, "missing: " + e);
                        missing++;
                    }
                }
                boolean ok = missing == 0 && !seen("ethernet available early");
                Log.i(TAG, ok ? "all ok" : "missing " + missing);
                mRoot.setBackgroundColor(ok ? 0xFF43A047 : 0xFFE53935);
                mText.setText(ok ? "network ok" : "network: " + missing + " missing");
                mStatus.setText(ok ? "" : "see the W/NET missing lines in the log");
            }
        });
    }
}
