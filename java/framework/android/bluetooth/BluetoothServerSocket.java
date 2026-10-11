package android.bluetooth;

import java.io.Closeable;
import java.io.IOException;

/** Never created: listening needs an adapter, and there is none (see BluetoothAdapter). */
public final class BluetoothServerSocket implements Closeable {
    BluetoothServerSocket() {}

    public BluetoothSocket accept() throws IOException { return accept(-1); }
    public BluetoothSocket accept(int timeout) throws IOException { throw new IOException("Bluetooth is off"); }
    public void close() throws IOException {}
    public int getPsm() { return -1; }

    @Override
    public String toString() { return "ServerSocket: Type: TYPE_RFCOMM"; }
}
