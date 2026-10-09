package java.util;

public class DuplicateFormatFlagsException extends IllegalFormatException {
    private final String flags;

    public DuplicateFormatFlagsException(String f) {
        if (f == null) {
            throw new NullPointerException();
        }
        this.flags = f;
    }

    public String getFlags() {
        return flags;
    }

    public String getMessage() {
        return String.format("Flags = '%s'", flags);
    }
}
