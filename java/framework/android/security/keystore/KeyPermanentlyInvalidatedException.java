package android.security.keystore;

public class KeyPermanentlyInvalidatedException extends java.security.InvalidKeyException {
    public KeyPermanentlyInvalidatedException() {
        super();
    }

    public KeyPermanentlyInvalidatedException(String message) {
        super(message);
    }

    public KeyPermanentlyInvalidatedException(String message, Throwable cause) {
        super(message, cause);
    }
}
