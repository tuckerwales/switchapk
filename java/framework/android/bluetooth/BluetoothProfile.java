package android.bluetooth;

import java.util.List;

public interface BluetoothProfile {
    int A2DP = 2;
    int CSIP_SET_COORDINATOR = 25;
    String EXTRA_PREVIOUS_STATE = "android.bluetooth.profile.extra.PREVIOUS_STATE";
    String EXTRA_STATE = "android.bluetooth.profile.extra.STATE";
    int GATT = 7;
    int GATT_SERVER = 8;
    int HAP_CLIENT = 28;
    int HEADSET = 1;
    int HEALTH = 3;
    int HEARING_AID = 21;
    int HID_DEVICE = 19;
    int LE_AUDIO = 22;
    int SAP = 10;
    int STATE_CONNECTED = 2;
    int STATE_CONNECTING = 1;
    int STATE_DISCONNECTED = 0;
    int STATE_DISCONNECTING = 3;

    List<BluetoothDevice> getConnectedDevices();
    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states);
    int getConnectionState(BluetoothDevice device);

    interface ServiceListener {
        void onServiceConnected(int profile, BluetoothProfile proxy);
        void onServiceDisconnected(int profile);
    }
}
