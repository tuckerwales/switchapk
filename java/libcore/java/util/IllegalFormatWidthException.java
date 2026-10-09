package java.util;

public class IllegalFormatWidthException extends IllegalFormatException {
    private final int w;

    public IllegalFormatWidthException(int w) {
        this.w = w;
    }

    public int getWidth() {
        return w;
    }

    public String getMessage() {
        return Integer.toString(w);
    }
}
