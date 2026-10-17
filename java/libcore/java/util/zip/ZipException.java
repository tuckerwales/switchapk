package java.util.zip;

public class ZipException extends java.io.IOException {
    private static final long serialVersionUID = 8000196834066748623L;

    public ZipException() {
    }

    public ZipException(String s) {
        super(s);
    }
}
