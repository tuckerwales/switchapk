package java.net;

public class SocketTimeoutException extends java.io.InterruptedIOException {
    private static final long serialVersionUID = -8846654841826352300L;

    public SocketTimeoutException() {
    }

    public SocketTimeoutException(String msg) {
        super(msg);
    }
}
