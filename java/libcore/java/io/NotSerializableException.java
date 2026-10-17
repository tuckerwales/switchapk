package java.io;

public class NotSerializableException extends ObjectStreamException {
    private static final long serialVersionUID = 2906642554793891381L;

    public NotSerializableException() {
    }

    public NotSerializableException(String message) {
        super(message);
    }
}
