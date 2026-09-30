package java.io;

public class InvalidClassException extends ObjectStreamException {
    public InvalidClassException() {
    }

    public InvalidClassException(String message) {
        super(message);
    }
}
