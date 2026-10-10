package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;
import java.io.IOException;
import java.util.UUID;

/** A remote device by address. With no adapter (see BluetoothAdapter) it is never bonded or reachable. */
public final class BluetoothDevice implements Parcelable {
    public static final String ACTION_ACL_CONNECTED = "android.bluetooth.device.action.ACL_CONNECTED";
    public static final String ACTION_ACL_DISCONNECTED = "android.bluetooth.device.action.ACL_DISCONNECTED";
    public static final String ACTION_ACL_DISCONNECT_REQUESTED = "android.bluetooth.device.action.ACL_DISCONNECT_REQUESTED";
    public static final String ACTION_ALIAS_CHANGED = "android.bluetooth.device.action.ALIAS_CHANGED";
    public static final String ACTION_BOND_STATE_CHANGED = "android.bluetooth.device.action.BOND_STATE_CHANGED";
    public static final String ACTION_CLASS_CHANGED = "android.bluetooth.device.action.CLASS_CHANGED";
    public static final String ACTION_FOUND = "android.bluetooth.device.action.FOUND";
    public static final String ACTION_NAME_CHANGED = "android.bluetooth.device.action.NAME_CHANGED";
    public static final String ACTION_PAIRING_REQUEST = "android.bluetooth.device.action.PAIRING_REQUEST";
    public static final String ACTION_UUID = "android.bluetooth.device.action.UUID";
    public static final int ADDRESS_TYPE_ANONYMOUS = 255;
    public static final int ADDRESS_TYPE_PUBLIC = 0;
    public static final int ADDRESS_TYPE_RANDOM = 1;
    public static final int ADDRESS_TYPE_UNKNOWN = 65535;
    public static final int BOND_BONDED = 12;
    public static final int BOND_BONDING = 11;
    public static final int BOND_NONE = 10;
    public static final int DEVICE_TYPE_CLASSIC = 1;
    public static final int DEVICE_TYPE_DUAL = 3;
    public static final int DEVICE_TYPE_LE = 2;
    public static final int DEVICE_TYPE_UNKNOWN = 0;
    public static final int ERROR = -2147483648;
    public static final String EXTRA_BOND_STATE = "android.bluetooth.device.extra.BOND_STATE";
    public static final String EXTRA_CLASS = "android.bluetooth.device.extra.CLASS";
    public static final String EXTRA_DEVICE = "android.bluetooth.device.extra.DEVICE";
    public static final String EXTRA_IS_COORDINATED_SET_MEMBER = "android.bluetooth.extra.IS_COORDINATED_SET_MEMBER";
    public static final String EXTRA_NAME = "android.bluetooth.device.extra.NAME";
    public static final String EXTRA_PAIRING_KEY = "android.bluetooth.device.extra.PAIRING_KEY";
    public static final String EXTRA_PAIRING_VARIANT = "android.bluetooth.device.extra.PAIRING_VARIANT";
    public static final String EXTRA_PREVIOUS_BOND_STATE = "android.bluetooth.device.extra.PREVIOUS_BOND_STATE";
    public static final String EXTRA_RSSI = "android.bluetooth.device.extra.RSSI";
    public static final String EXTRA_TRANSPORT = "android.bluetooth.device.extra.TRANSPORT";
    public static final String EXTRA_UUID = "android.bluetooth.device.extra.UUID";
    public static final int PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2;
    public static final int PAIRING_VARIANT_PIN = 0;
    public static final int PHY_LE_1M = 1;
    public static final int PHY_LE_1M_MASK = 1;
    public static final int PHY_LE_2M = 2;
    public static final int PHY_LE_2M_MASK = 2;
    public static final int PHY_LE_CODED = 3;
    public static final int PHY_LE_CODED_MASK = 4;
    public static final int PHY_OPTION_NO_PREFERRED = 0;
    public static final int PHY_OPTION_S2 = 1;
    public static final int PHY_OPTION_S8 = 2;
    public static final int TRANSPORT_AUTO = 0;
    public static final int TRANSPORT_BREDR = 1;
    public static final int TRANSPORT_LE = 2;

    public static final Parcelable.Creator<BluetoothDevice> CREATOR = new Parcelable.Creator<BluetoothDevice>() {
        public BluetoothDevice createFromParcel(Parcel in) { return new BluetoothDevice(in.readString()); }
        public BluetoothDevice[] newArray(int size) { return new BluetoothDevice[size]; }
    };

    private final String mAddress;

    BluetoothDevice(String address) { mAddress = address; }

    @Override
    public boolean equals(Object o) { return o instanceof BluetoothDevice && mAddress.equals(((BluetoothDevice) o).mAddress); }

    @Override
    public int hashCode() { return mAddress.hashCode(); }

    @Override
    public String toString() { return mAddress; }

    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { out.writeString(mAddress); }

    public String getAddress() { return mAddress; }
    public int getAddressType() { return ADDRESS_TYPE_PUBLIC; }
    public String getName() { return null; }
    public int getType() { return DEVICE_TYPE_UNKNOWN; }
    public String getAlias() { return null; }
    public int setAlias(String alias) { return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED; }
    public boolean createBond() { return false; }
    public int getBondState() { return BOND_NONE; }
    public boolean fetchUuidsWithSdp() { return false; }
    public boolean setPin(byte[] pin) { return false; }
    public boolean setPairingConfirmation(boolean confirm) { return false; }

    public BluetoothSocket createRfcommSocketToServiceRecord(UUID uuid) throws IOException {
        return new BluetoothSocket(this, BluetoothSocket.TYPE_RFCOMM);
    }

    public BluetoothSocket createInsecureRfcommSocketToServiceRecord(UUID uuid) throws IOException {
        return new BluetoothSocket(this, BluetoothSocket.TYPE_RFCOMM);
    }

    public BluetoothSocket createL2capChannel(int psm) throws IOException {
        return new BluetoothSocket(this, BluetoothSocket.TYPE_L2CAP);
    }

    public BluetoothSocket createInsecureL2capChannel(int psm) throws IOException {
        return new BluetoothSocket(this, BluetoothSocket.TYPE_L2CAP);
    }
}
