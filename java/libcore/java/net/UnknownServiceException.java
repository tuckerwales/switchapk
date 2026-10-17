package java.net;

public class UnknownServiceException extends java.io.IOException {
    private static final long serialVersionUID = -4169033248853639508L;

    public UnknownServiceException() {
    }

    public UnknownServiceException(String msg) {
        super(msg);
    }
}
