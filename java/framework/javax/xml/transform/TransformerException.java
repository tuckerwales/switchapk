package javax.xml.transform;

import java.io.PrintStream;
import java.io.PrintWriter;

public class TransformerException extends Exception {
    private static final long serialVersionUID = 975798773772956428L;

    private SourceLocator locator;
    private Throwable containedException;

    public SourceLocator getLocator() {
        return locator;
    }

    public void setLocator(SourceLocator location) {
        locator = location;
    }

    public Throwable getException() {
        return containedException;
    }

    public Throwable getCause() {
        return containedException == this ? null : containedException;
    }

    public synchronized Throwable initCause(Throwable cause) {
        if (containedException != null) {
            throw new IllegalStateException("Can't overwrite cause");
        }
        if (cause == this) {
            throw new IllegalArgumentException("Self-causation not permitted");
        }
        containedException = cause;
        return this;
    }

    public TransformerException(String message) {
        super(message);
    }

    public TransformerException(Throwable e) {
        super(e == null ? null : e.toString());
        containedException = e;
    }

    public TransformerException(String message, Throwable e) {
        super(message == null || message.isEmpty() ? (e == null ? null : e.toString()) : message);
        containedException = e;
    }

    public TransformerException(String message, SourceLocator locator) {
        super(message);
        this.locator = locator;
    }

    public TransformerException(String message, SourceLocator locator, Throwable e) {
        super(message);
        this.locator = locator;
        containedException = e;
    }

    public String getMessageAndLocation() {
        StringBuilder sb = new StringBuilder();
        String message = super.getMessage();
        if (message != null) {
            sb.append(message);
        }
        String location = getLocationAsString();
        if (location != null) {
            sb.append("; ").append(location);
        }
        return sb.toString();
    }

    public String getLocationAsString() {
        if (locator == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        String systemID = locator.getSystemId();
        int line = locator.getLineNumber();
        int column = locator.getColumnNumber();
        if (systemID != null) {
            sb.append("; SystemID: ").append(systemID);
        }
        if (line != 0) {
            sb.append("; Line#: ").append(line);
        }
        if (column != 0) {
            sb.append("; Column#: ").append(column);
        }
        return sb.toString();
    }

    public void printStackTrace() {
        printStackTrace(new PrintWriter(System.err, true));
    }

    public void printStackTrace(PrintStream s) {
        printStackTrace(new PrintWriter(s));
    }

    public void printStackTrace(PrintWriter s) {
        if (s == null) {
            s = new PrintWriter(System.err, true);
        }
        super.printStackTrace(s);
        s.flush();
    }
}
