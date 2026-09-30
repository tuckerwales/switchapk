package java.security;

public class SecureRandom extends java.util.Random {
    public SecureRandom() {
        super(System.nanoTime() ^ 0x5DEECE66DL ^ System.identityHashCode(new Object()));
    }

    public SecureRandom(byte[] seed) {
        this();
    }

    public static SecureRandom getInstance(String algorithm) throws NoSuchAlgorithmException {
        return new SecureRandom();
    }

    public static SecureRandom getInstanceStrong() {
        return new SecureRandom();
    }

    public void setSeed(byte[] seed) {
    }

    public byte[] generateSeed(int numBytes) {
        byte[] b = new byte[numBytes];
        nextBytes(b);
        return b;
    }

    public String getAlgorithm() {
        return "SHA1PRNG";
    }
}
