package java.util;

public class IllegalFormatFlagsException extends IllegalFormatException {
    private final String flags;

    public IllegalFormatFlagsException(String f) {
        if (f == null) {
            throw new NullPointerException();
        }
        this.flags = f;
    }

    public String getFlags() {
        return flags;
    }

    public String getMessage() {
        return "Flags = '" + flags + "'";
    }
}
