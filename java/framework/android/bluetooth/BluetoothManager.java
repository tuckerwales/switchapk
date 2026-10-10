package android.bluetooth;

import java.util.ArrayList;
import java.util.List;

/** Registered like on Android, but with no adapter: {@link #getAdapter()} returns null (see BluetoothAdapter). */
public final class BluetoothManager {
    BluetoothManager() {}

    /** @hide For ContextImpl. */
    public static BluetoothManager create$() { return new BluetoothManager(); }

    public BluetoothAdapter getAdapter() { return BluetoothAdapter.getDefaultAdapter(); }
    public int getConnectionState(BluetoothDevice device, int profile) { return BluetoothProfile.STATE_DISCONNECTED; }
    public List<BluetoothDevice> getConnectedDevices(int profile) { return new ArrayList<BluetoothDevice>(); }

    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int profile, int[] states) {
        return new ArrayList<BluetoothDevice>();
    }
}
