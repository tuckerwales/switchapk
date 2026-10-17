package java.io;

public class UncheckedIOException extends RuntimeException {
    private static final long serialVersionUID = -8134305061645241065L;

    public UncheckedIOException(String message, IOException cause) {
        super(message, cause);
    }

    public UncheckedIOException(IOException cause) {
        super(cause);
    }

    public IOException getCause() {
        return (IOException) super.getCause();
    }
}
