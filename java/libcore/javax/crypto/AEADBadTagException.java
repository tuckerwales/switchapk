package javax.crypto;

public class AEADBadTagException extends BadPaddingException {
    public AEADBadTagException() {
    }

    public AEADBadTagException(String msg) {
        super(msg);
    }
}
