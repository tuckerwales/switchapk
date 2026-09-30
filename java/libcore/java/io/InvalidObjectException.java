package java.io;

public class InvalidObjectException extends ObjectStreamException {
    public InvalidObjectException() {
    }

    public InvalidObjectException(String message) {
        super(message);
    }
}
