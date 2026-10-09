package javax.crypto;

public class BadPaddingException extends java.security.GeneralSecurityException {
    public BadPaddingException() {
    }

    public BadPaddingException(String msg) {
        super(msg);
    }
}
