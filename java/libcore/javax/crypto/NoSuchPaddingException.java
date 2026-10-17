package javax.crypto;

public class NoSuchPaddingException extends java.security.GeneralSecurityException {
    private static final long serialVersionUID = -4572885201200175466L;

    public NoSuchPaddingException() {
    }

    public NoSuchPaddingException(String msg) {
        super(msg);
    }
}
