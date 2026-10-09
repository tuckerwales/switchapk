package javax.crypto;

/** The identity cipher: output equals input. */
public class NullCipher extends Cipher {
    public NullCipher() {
        super(new libcore.crypto.NullCipherSpi(), null, "NULL");
        try {
            init(ENCRYPT_MODE, (java.security.Key) null);
        } catch (java.security.InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }
}
