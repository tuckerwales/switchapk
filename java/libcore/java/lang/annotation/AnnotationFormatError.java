package java.lang.annotation;

/**
 * Thrown when a runtime annotation cannot be parsed. Matches
 * {@code java.lang.annotation.AnnotationFormatError}.
 */
public class AnnotationFormatError extends Error {
    public AnnotationFormatError(String message) {
        super(message);
    }

    public AnnotationFormatError(String message, Throwable cause) {
        super(message, cause);
    }

    public AnnotationFormatError(Throwable cause) {
        super(cause);
    }
}
