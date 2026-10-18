package java.nio.charset;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;

/** The outcome of a coding step: underflow, overflow, or a malformed or unmappable sequence of some length. */
public class CoderResult {
    private static final int CR_UNDERFLOW = 0;
    private static final int CR_OVERFLOW = 1;
    private static final int CR_MALFORMED = 2;
    private static final int CR_UNMAPPABLE = 3;
    private static final String[] NAMES = {"UNDERFLOW", "OVERFLOW", "MALFORMED", "UNMAPPABLE"};

    public static final CoderResult UNDERFLOW = new CoderResult(CR_UNDERFLOW, 0);
    public static final CoderResult OVERFLOW = new CoderResult(CR_OVERFLOW, 0);
    private static final CoderResult[] MALFORMED = new CoderResult[8];
    private static final CoderResult[] UNMAPPABLE = new CoderResult[8];

    static {
        for (int i = 1; i < 8; i++) {
            MALFORMED[i] = new CoderResult(CR_MALFORMED, i);
            UNMAPPABLE[i] = new CoderResult(CR_UNMAPPABLE, i);
        }
    }

    private final int type;
    private final int length;

    private CoderResult(int type, int length) {
        this.type = type;
        this.length = length;
    }

    public String toString() {
        String nm = NAMES[type];
        return isError() ? nm + "[" + length + "]" : nm;
    }

    public boolean isUnderflow() {
        return type == CR_UNDERFLOW;
    }

    public boolean isOverflow() {
        return type == CR_OVERFLOW;
    }

    public boolean isError() {
        return type >= CR_MALFORMED;
    }

    public boolean isMalformed() {
        return type == CR_MALFORMED;
    }

    public boolean isUnmappable() {
        return type == CR_UNMAPPABLE;
    }

    public int length() {
        if (!isError()) {
            throw new UnsupportedOperationException();
        }
        return length;
    }

    public static CoderResult malformedForLength(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Non-positive length");
        }
        return length < 8 ? MALFORMED[length] : new CoderResult(CR_MALFORMED, length);
    }

    public static CoderResult unmappableForLength(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Non-positive length");
        }
        return length < 8 ? UNMAPPABLE[length] : new CoderResult(CR_UNMAPPABLE, length);
    }

    public void throwException() throws CharacterCodingException {
        switch (type) {
            case CR_UNDERFLOW:
                throw new BufferUnderflowException();
            case CR_OVERFLOW:
                throw new BufferOverflowException();
            case CR_MALFORMED:
                throw new MalformedInputException(length);
            default:
                throw new UnmappableCharacterException(length);
        }
    }
}
