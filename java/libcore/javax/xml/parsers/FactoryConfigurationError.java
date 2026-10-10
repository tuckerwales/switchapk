package javax.xml.parsers;

public class FactoryConfigurationError extends Error {
    private final Exception exception;

    public FactoryConfigurationError() { super(); exception = null; }
    public FactoryConfigurationError(String msg) { super(msg); exception = null; }
    public FactoryConfigurationError(Exception e) { super(e.toString()); exception = e; }
    public FactoryConfigurationError(Exception e, String msg) { super(msg); exception = e; }

    public String getMessage() {
        String message = super.getMessage();
        if (message == null && exception != null) return exception.getMessage();
        return message;
    }

    public Exception getException() { return exception; }
    public Throwable getCause() { return exception; }
}
