package java.nio.charset;

public class CoderResult {
    public static final CoderResult UNDERFLOW = new CoderResult(false);
    public static final CoderResult OVERFLOW = new CoderResult(true);
    private final boolean overflow;

    private CoderResult(boolean overflow) {
        this.overflow = overflow;
    }

    public boolean isUnderflow() {
        return !overflow;
    }

    public boolean isOverflow() {
        return overflow;
    }

    public boolean isError() {
        return false;
    }

    public boolean isMalformed() {
        return false;
    }

    public boolean isUnmappable() {
        return false;
    }

    public void throwException() throws CharacterCodingException {
        throw new CharacterCodingException();
    }
}
