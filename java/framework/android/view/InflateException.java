package android.view;

/** Thrown when an inflater cannot inflate a layout. */
public class InflateException extends RuntimeException {
    public InflateException() {}

    public InflateException(String detailMessage, Throwable throwable) { super(detailMessage, throwable); }

    public InflateException(String detailMessage) { super(detailMessage); }

    public InflateException(Throwable throwable) { super(throwable); }
}
