package java.lang.reflect;

/** Thrown when a generic signature in the class file is malformed. */
public class GenericSignatureFormatError extends ClassFormatError {
    private static final long serialVersionUID = 6709919147137911034L;

    public GenericSignatureFormatError() { super(); }

    public GenericSignatureFormatError(String message) { super(message); }
}
