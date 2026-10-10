package android.security.keystore;

public class BackendBusyException extends java.security.ProviderException {
    private final long backOffHintMillis;

    public BackendBusyException(long backOffHintMillis) {
        this(backOffHintMillis, "The keystore backend has no operation slots available. Retry later.");
    }

    public BackendBusyException(long backOffHintMillis, String message) {
        super(message);
        if (backOffHintMillis < 0) throw new IllegalArgumentException("Back-off hint cannot be negative.");
        this.backOffHintMillis = backOffHintMillis;
    }

    public BackendBusyException(long backOffHintMillis, String message, Throwable cause) {
        super(message, cause);
        if (backOffHintMillis < 0) throw new IllegalArgumentException("Back-off hint cannot be negative.");
        this.backOffHintMillis = backOffHintMillis;
    }

    public long getBackOffHintMillis() {
        return backOffHintMillis;
    }
}
