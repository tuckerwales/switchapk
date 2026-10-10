package libcore.crypto;

/**
 * A key whose bytes are not exported through getEncoded() (AndroidKeyStore
 * keys return null there, as on Android) but which the built-in ciphers and
 * MACs may still use. switchapk's keystore is software only, so this is a
 * convention about where keys show up, not a protection boundary.
 */
public interface KeyMaterial {
    /** A copy of the raw key bytes. */
    byte[] keyMaterial();

    /** Throws if the key may not be used for this operation (purpose, mode, padding, digest). */
    void checkUse(String operation, String mode, String padding, String digest) throws java.security.InvalidKeyException;
}
