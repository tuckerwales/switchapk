package javax.crypto;

public class IllegalBlockSizeException extends java.security.GeneralSecurityException {
    private static final long serialVersionUID = -1965144811953540392L;

    public IllegalBlockSizeException() {
    }

    public IllegalBlockSizeException(String msg) {
        super(msg);
    }
}
