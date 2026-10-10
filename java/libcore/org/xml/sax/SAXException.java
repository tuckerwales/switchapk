package org.xml.sax;

public class SAXException extends Exception {
    private static final long serialVersionUID = 583241635256073760L;
    private Exception exception;

    public SAXException() {}

    public SAXException(String message) { super(message); }

    public SAXException(Exception e) { this.exception = e; }

    public SAXException(String message, Exception e) {
        super(message);
        this.exception = e;
    }

    public String getMessage() {
        String message = super.getMessage();
        if (message == null && exception != null) return exception.getMessage();
        return message;
    }

    public Exception getException() { return exception; }
    public Throwable getCause() { return exception; }

    public String toString() {
        if (exception != null) return super.toString() + "\n" + exception.toString();
        return super.toString();
    }
}
