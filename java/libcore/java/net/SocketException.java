package java.net;

public class SocketException extends java.io.IOException {
    private static final long serialVersionUID = -5935874303556886934L;

    public SocketException() {
    }

    public SocketException(String msg) {
        super(msg);
    }
}
