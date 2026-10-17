package java.util;

public class IllformedLocaleException extends RuntimeException {
    private static final long serialVersionUID = -5245986824925681401L;

    public IllformedLocaleException() {
        super();
    }

    public IllformedLocaleException(String message) {
        super(message);
    }

    private int errIdx = -1;

    public IllformedLocaleException(String message, int errorIndex) {
        super(message + ((errorIndex < 0) ? "" : " [at index " + errorIndex + "]"));
        errIdx = errorIndex;
    }

    public int getErrorIndex() {
        return errIdx;
    }
}
