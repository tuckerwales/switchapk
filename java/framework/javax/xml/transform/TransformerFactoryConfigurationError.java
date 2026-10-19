package javax.xml.transform;

public class TransformerFactoryConfigurationError extends Error {
    private static final long serialVersionUID = -6527718720676281516L;

    private Exception exception;

    public TransformerFactoryConfigurationError() {
        super();
    }

    public TransformerFactoryConfigurationError(String msg) {
        super(msg);
    }

    public TransformerFactoryConfigurationError(Exception e) {
        super(e.toString());
        exception = e;
    }

    public TransformerFactoryConfigurationError(Exception e, String msg) {
        super(msg);
        exception = e;
    }

    public String getMessage() {
        String message = super.getMessage();
        if (message == null && exception != null) {
            return exception.getMessage();
        }
        return message;
    }

    public Exception getException() {
        return exception;
    }

    public Throwable getCause() {
        return exception;
    }
}
