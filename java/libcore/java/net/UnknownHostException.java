package java.net;

public class UnknownHostException extends java.io.IOException {
    private static final long serialVersionUID = -4639126076052875403L;

    public UnknownHostException() {
    }

    public UnknownHostException(String msg) {
        super(msg);
    }
}
