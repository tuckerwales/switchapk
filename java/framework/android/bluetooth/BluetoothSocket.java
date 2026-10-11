package android.bluetooth;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** A socket that never connects: there is no radio (see BluetoothAdapter). */
public final class BluetoothSocket implements Closeable {
    public static final int TYPE_L2CAP = 3;
    public static final int TYPE_RFCOMM = 1;
    public static final int TYPE_SCO = 2;

    private final BluetoothDevice mDevice;
    private final int mType;

    BluetoothSocket(BluetoothDevice device, int type) {
        mDevice = device;
        mType = type;
    }

    public BluetoothDevice getRemoteDevice() { return mDevice; }
    public InputStream getInputStream() throws IOException { throw new IOException("socket not connected"); }
    public OutputStream getOutputStream() throws IOException { throw new IOException("socket not connected"); }
    public boolean isConnected() { return false; }
    public void connect() throws IOException { throw new IOException("Bluetooth is off"); }
    public void close() throws IOException {}
    public int getMaxTransmitPacketSize() { return 0; }
    public int getMaxReceivePacketSize() { return 0; }
    public int getConnectionType() { return mType; }

    @Override
    public String toString() { return "BluetoothSocket{" + mDevice + "}"; }
}
