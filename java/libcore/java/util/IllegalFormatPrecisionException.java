package java.util;

public class IllegalFormatPrecisionException extends IllegalFormatException {
    private final int p;

    public IllegalFormatPrecisionException(int p) {
        this.p = p;
    }

    public int getPrecision() {
        return p;
    }

    public String getMessage() {
        return Integer.toString(p);
    }
}
