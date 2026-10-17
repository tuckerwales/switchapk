package javax.crypto;

public class ShortBufferException extends java.security.GeneralSecurityException {
    private static final long serialVersionUID = 8427718640832943747L;

    public ShortBufferException() {
    }

    public ShortBufferException(String msg) {
        super(msg);
    }
}
