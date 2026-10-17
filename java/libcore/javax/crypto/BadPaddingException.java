package javax.crypto;

public class BadPaddingException extends java.security.GeneralSecurityException {
    private static final long serialVersionUID = -5315033893984728443L;

    public BadPaddingException() {
    }

    public BadPaddingException(String msg) {
        super(msg);
    }
}
