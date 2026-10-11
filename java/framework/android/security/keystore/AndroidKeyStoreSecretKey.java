package android.security.keystore;

import java.security.InvalidKeyException;
import javax.crypto.SecretKey;

/**
 * A secret key in the AndroidKeyStore. As on Android, getEncoded() and
 * getFormat() return null; the built-in ciphers and MACs use it through
 * libcore.crypto.KeyMaterial and apply its authorizations.
 */
public class AndroidKeyStoreSecretKey implements SecretKey, libcore.crypto.KeyMaterial {
    private final KeyStoreEntry entry;

    AndroidKeyStoreSecretKey(KeyStoreEntry entry) {
        this.entry = entry;
    }

    KeyStoreEntry entry() {
        return entry;
    }

    public String getAlgorithm() {
        return entry.algorithm;
    }

    public String getFormat() {
        return null;
    }

    public byte[] getEncoded() {
        return null;
    }

    /** framework-internal (libcore.crypto.KeyMaterial). */
    public byte[] keyMaterial() {
        if (!KeyStoreEntry.exists(entry.alias)) {
            throw new IllegalStateException("Key " + entry.alias + " has been deleted from the AndroidKeyStore");
        }
        return entry.key.clone();
    }

    /** framework-internal (libcore.crypto.KeyMaterial). */
    public void checkUse(String operation, String mode, String padding, String digest) throws InvalidKeyException {
        if (!KeyStoreEntry.exists(entry.alias)) {
            throw new KeyPermanentlyInvalidatedException("Key " + entry.alias + " has been deleted");
        }
        entry.checkUse(operation, mode, padding, digest);
    }

    public int hashCode() {
        return entry.alias.hashCode() * 31 + (int) entry.created;
    }

    public boolean equals(Object o) {
        if (!(o instanceof AndroidKeyStoreSecretKey)) return false;
        KeyStoreEntry e = ((AndroidKeyStoreSecretKey) o).entry;
        return e.alias.equals(entry.alias) && e.created == entry.created;
    }

    public String toString() {
        return "AndroidKeyStoreSecretKey{" + entry.alias + ", " + entry.algorithm + "}";
    }
}
