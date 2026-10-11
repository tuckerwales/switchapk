package libcore.crypto;

import java.security.InvalidKeyException;
import java.security.Key;

final class Keys {
    private Keys() {
    }

    /** The raw bytes of a secret key, from KeyMaterial or a RAW encoding. */
    static byte[] raw(Key key, String operation, String mode, String padding, String digest) throws InvalidKeyException {
        if (key == null) throw new InvalidKeyException("key == null");
        if (key instanceof KeyMaterial) {
            KeyMaterial m = (KeyMaterial) key;
            m.checkUse(operation, mode, padding, digest);
            return m.keyMaterial();
        }
        if (!"RAW".equalsIgnoreCase(key.getFormat())) {
            throw new InvalidKeyException("Unsupported key format: " + key.getFormat() + " (" + key.getClass().getName() + ")");
        }
        byte[] b = key.getEncoded();
        if (b == null) throw new InvalidKeyException("key.getEncoded() == null");
        return b;
    }
}
