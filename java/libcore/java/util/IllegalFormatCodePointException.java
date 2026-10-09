package java.util;

public class IllegalFormatCodePointException extends IllegalFormatException {
    private final int c;

    public IllegalFormatCodePointException(int c) {
        this.c = c;
    }

    public int getCodePoint() {
        return c;
    }

    public String getMessage() {
        return String.format("Code point = %#x", c);
    }
}
