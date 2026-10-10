package libcore.crypto;

import java.security.SecureRandomSpi;

/**
 * SecureRandom backed directly by the operating system's entropy
 * (getrandom(2) on the host, libnx randomGet on the Switch). Seeds passed to
 * setSeed are ignored: they cannot make the output weaker, and the OS
 * generator needs none.
 */
public final class NativePrng extends SecureRandomSpi {
    public NativePrng() {
    }

    protected void engineSetSeed(byte[] seed) {
        if (seed == null) throw new NullPointerException("seed == null");
    }

    protected void engineNextBytes(byte[] bytes) {
        nativeRandomBytes(bytes, 0, bytes.length);
    }

    protected byte[] engineGenerateSeed(int numBytes) {
        return entropy(numBytes);
    }

    public static byte[] entropy(int numBytes) {
        if (numBytes < 0) throw new IllegalArgumentException("numBytes cannot be negative");
        byte[] b = new byte[numBytes];
        nativeRandomBytes(b, 0, numBytes);
        return b;
    }

    private static native void nativeRandomBytes(byte[] b, int off, int len);
}
