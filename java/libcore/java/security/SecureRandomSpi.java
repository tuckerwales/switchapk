package java.security;

public abstract class SecureRandomSpi implements java.io.Serializable {
    private static final long serialVersionUID = -2991854161009191830L;

    private final SecureRandomParameters params;

    public SecureRandomSpi() {
        this(null);
    }

    protected SecureRandomSpi(SecureRandomParameters params) {
        this.params = params;
    }

    protected abstract void engineSetSeed(byte[] seed);

    protected abstract void engineNextBytes(byte[] bytes);

    protected void engineNextBytes(byte[] bytes, SecureRandomParameters params) {
        throw new UnsupportedOperationException();
    }

    protected abstract byte[] engineGenerateSeed(int numBytes);

    protected void engineReseed(SecureRandomParameters params) {
        throw new UnsupportedOperationException();
    }

    protected SecureRandomParameters engineGetParameters() {
        return params;
    }

    public String toString() {
        return getClass().getSimpleName();
    }
}
