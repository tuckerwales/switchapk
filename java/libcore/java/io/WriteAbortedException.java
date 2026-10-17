package java.io;

/** An exception the writer hit while writing the stream, read back in its place. */
public class WriteAbortedException extends ObjectStreamException {
    private static final long serialVersionUID = -3326426625597282442L;

    public Exception detail;

    public WriteAbortedException(String s, Exception ex) {
        super(s);
        initCause(null);
        detail = ex;
    }

    public String getMessage() {
        if (detail == null) {
            return super.getMessage();
        }
        return super.getMessage() + "; " + detail.toString();
    }

    public Throwable getCause() {
        return detail;
    }
}
