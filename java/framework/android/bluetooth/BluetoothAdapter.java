package android.bluetooth;

import android.content.Context;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * The console has no Bluetooth radio the app can use, so this is a device without Bluetooth:
 * {@link #getDefaultAdapter()} returns null, as on Android hardware that lacks it. Apps check
 * for null; the instance methods answer "off" for code that keeps an adapter anyway.
 */
public final class BluetoothAdapter {
    public static final String ACTION_CONNECTION_STATE_CHANGED = "android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED";
    public static final String ACTION_DISCOVERY_FINISHED = "android.bluetooth.adapter.action.DISCOVERY_FINISHED";
    public static final String ACTION_DISCOVERY_STARTED = "android.bluetooth.adapter.action.DISCOVERY_STARTED";
    public static final String ACTION_LOCAL_NAME_CHANGED = "android.bluetooth.adapter.action.LOCAL_NAME_CHANGED";
    public static final String ACTION_REQUEST_DISCOVERABLE = "android.bluetooth.adapter.action.REQUEST_DISCOVERABLE";
    public static final String ACTION_REQUEST_ENABLE = "android.bluetooth.adapter.action.REQUEST_ENABLE";
    public static final String ACTION_SCAN_MODE_CHANGED = "android.bluetooth.adapter.action.SCAN_MODE_CHANGED";
    public static final String ACTION_STATE_CHANGED = "android.bluetooth.adapter.action.STATE_CHANGED";
    public static final int ERROR = -2147483648;
    public static final String EXTRA_CONNECTION_STATE = "android.bluetooth.adapter.extra.CONNECTION_STATE";
    public static final String EXTRA_DISCOVERABLE_DURATION = "android.bluetooth.adapter.extra.DISCOVERABLE_DURATION";
    public static final String EXTRA_LOCAL_NAME = "android.bluetooth.adapter.extra.LOCAL_NAME";
    public static final String EXTRA_PREVIOUS_CONNECTION_STATE = "android.bluetooth.adapter.extra.PREVIOUS_CONNECTION_STATE";
    public static final String EXTRA_PREVIOUS_SCAN_MODE = "android.bluetooth.adapter.extra.PREVIOUS_SCAN_MODE";
    public static final String EXTRA_PREVIOUS_STATE = "android.bluetooth.adapter.extra.PREVIOUS_STATE";
    public static final String EXTRA_SCAN_MODE = "android.bluetooth.adapter.extra.SCAN_MODE";
    public static final String EXTRA_STATE = "android.bluetooth.adapter.extra.STATE";
    public static final int SCAN_MODE_CONNECTABLE = 21;
    public static final int SCAN_MODE_CONNECTABLE_DISCOVERABLE = 23;
    public static final int SCAN_MODE_NONE = 20;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_DISCONNECTING = 3;
    public static final int STATE_OFF = 10;
    public static final int STATE_ON = 12;
    public static final int STATE_TURNING_OFF = 13;
    public static final int STATE_TURNING_ON = 11;

    public interface LeScanCallback {
        void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord);
    }

    BluetoothAdapter() {}

    public static synchronized BluetoothAdapter getDefaultAdapter() { return null; }

    public BluetoothDevice getRemoteDevice(String address) {
        if (!checkBluetoothAddress(address)) throw new IllegalArgumentException(address + " is not a valid Bluetooth address");
        return new BluetoothDevice(address);
    }

    public BluetoothDevice getRemoteDevice(byte[] address) {
        if (address == null || address.length != 6) throw new IllegalArgumentException("Bluetooth address must have 6 bytes");
        return new BluetoothDevice(String.format("%02X:%02X:%02X:%02X:%02X:%02X", address[0], address[1], address[2],
                address[3], address[4], address[5]));
    }

    public BluetoothDevice getRemoteLeDevice(String address, int addressType) { return getRemoteDevice(address); }

    public boolean isEnabled() { return false; }
    public int getState() { return STATE_OFF; }
    public boolean enable() { return false; }
    public boolean disable() { return false; }
    public String getAddress() { return "02:00:00:00:00:00"; }
    public String getName() { return null; }
    public boolean setName(String name) { return false; }
    public int getScanMode() { return SCAN_MODE_NONE; }
    public boolean startDiscovery() { return false; }
    public boolean cancelDiscovery() { return false; }
    public boolean isDiscovering() { return false; }
    public boolean isMultipleAdvertisementSupported() { return false; }
    public boolean isOffloadedFilteringSupported() { return false; }
    public boolean isOffloadedScanBatchingSupported() { return false; }
    public boolean isLe2MPhySupported() { return false; }
    public boolean isLeCodedPhySupported() { return false; }
    public boolean isLeExtendedAdvertisingSupported() { return false; }
    public boolean isLePeriodicAdvertisingSupported() { return false; }
    public int isLeAudioSupported() { return BluetoothStatusCodes.FEATURE_NOT_SUPPORTED; }
    public int isLeAudioBroadcastSourceSupported() { return BluetoothStatusCodes.FEATURE_NOT_SUPPORTED; }
    public int isLeAudioBroadcastAssistantSupported() { return BluetoothStatusCodes.FEATURE_NOT_SUPPORTED; }
    public int getLeMaximumAdvertisingDataLength() { return 0; }
    public int getMaxConnectedAudioDevices() { return 1; }
    public Set<BluetoothDevice> getBondedDevices() { return Collections.emptySet(); }
    public int getProfileConnectionState(int profile) { return BluetoothProfile.STATE_DISCONNECTED; }

    public BluetoothServerSocket listenUsingRfcommWithServiceRecord(String name, UUID uuid) throws IOException {
        throw new IOException("Bluetooth is off");
    }

    public BluetoothServerSocket listenUsingInsecureRfcommWithServiceRecord(String name, UUID uuid) throws IOException {
        throw new IOException("Bluetooth is off");
    }

    public BluetoothServerSocket listenUsingL2capChannel() throws IOException { throw new IOException("Bluetooth is off"); }
    public BluetoothServerSocket listenUsingInsecureL2capChannel() throws IOException { throw new IOException("Bluetooth is off"); }

    public boolean getProfileProxy(Context context, BluetoothProfile.ServiceListener listener, int profile) { return false; }
    public void closeProfileProxy(int profile, BluetoothProfile proxy) {}

    public static boolean checkBluetoothAddress(String address) {
        if (address == null || address.length() != 17) return false;
        for (int i = 0; i < 17; i++) {
            char c = address.charAt(i);
            if (i % 3 == 2) {
                if (c != ':') return false;
            } else if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    public boolean startLeScan(LeScanCallback callback) { return false; }
    public boolean startLeScan(UUID[] serviceUuids, LeScanCallback callback) { return false; }
    public void stopLeScan(LeScanCallback callback) {}
}
