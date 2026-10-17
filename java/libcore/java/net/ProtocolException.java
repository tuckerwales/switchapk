package java.net;

public class ProtocolException extends java.io.IOException {
    private static final long serialVersionUID = -6098449442062388080L;

    public ProtocolException() {
    }

    public ProtocolException(String msg) {
        super(msg);
    }
}
