package java.nio.charset;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

/** Encodes chars to bytes in steps; the mirror of CharsetDecoder. */
public abstract class CharsetEncoder {
    private static final int ST_RESET = 0;
    private static final int ST_CODING = 1;
    private static final int ST_END = 2;
    private static final int ST_FLUSHED = 3;
    private static final String[] STATE_NAMES = {"RESET", "CODING", "CODING_END", "FLUSHED"};

    private final Charset charset;
    private final float averageBytesPerChar;
    private final float maxBytesPerChar;
    private byte[] replacement;
    private CodingErrorAction malformedInputAction = CodingErrorAction.REPORT;
    private CodingErrorAction unmappableCharacterAction = CodingErrorAction.REPORT;
    private int state = ST_RESET;

    protected CharsetEncoder(Charset cs, float averageBytesPerChar, float maxBytesPerChar, byte[] replacement) {
        this.charset = cs;
        if (averageBytesPerChar <= 0.0f) {
            throw new IllegalArgumentException("Non-positive averageBytesPerChar");
        }
        if (maxBytesPerChar <= 0.0f) {
            throw new IllegalArgumentException("Non-positive maxBytesPerChar");
        }
        if (averageBytesPerChar > maxBytesPerChar) {
            throw new IllegalArgumentException("averageBytesPerChar exceeds maxBytesPerChar");
        }
        this.averageBytesPerChar = averageBytesPerChar;
        this.maxBytesPerChar = maxBytesPerChar;
        replaceWith(replacement);
    }

    protected CharsetEncoder(Charset cs, float averageBytesPerChar, float maxBytesPerChar) {
        this(cs, averageBytesPerChar, maxBytesPerChar, new byte[] {(byte) '?'});
    }

    public final Charset charset() {
        return charset;
    }

    public final byte[] replacement() {
        return replacement.clone();
    }

    public final CharsetEncoder replaceWith(byte[] newReplacement) {
        if (newReplacement == null) {
            throw new IllegalArgumentException("Null replacement");
        }
        int len = newReplacement.length;
        if (len == 0) {
            throw new IllegalArgumentException("Empty replacement");
        }
        if (len > maxBytesPerChar) {
            throw new IllegalArgumentException("Replacement too long");
        }
        if (!isLegalReplacement(newReplacement)) {
            throw new IllegalArgumentException("Illegal replacement");
        }
        this.replacement = newReplacement.clone();
        implReplaceWith(this.replacement);
        return this;
    }

    protected void implReplaceWith(byte[] newReplacement) {
    }

    /** A replacement is legal if this charset's decoder reads it without error. */
    public boolean isLegalReplacement(byte[] repl) {
        CharsetDecoder dec = charset().newDecoder();
        dec.onMalformedInput(CodingErrorAction.REPORT);
        dec.onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer bb = ByteBuffer.wrap(repl);
        CharBuffer cb = CharBuffer.allocate((int) (bb.remaining() * dec.maxCharsPerByte()) + 2);
        CoderResult cr = dec.decode(bb, cb, true);
        return !cr.isError();
    }

    public CodingErrorAction malformedInputAction() {
        return malformedInputAction;
    }

    public final CharsetEncoder onMalformedInput(CodingErrorAction newAction) {
        if (newAction == null) {
            throw new IllegalArgumentException("Null action");
        }
        malformedInputAction = newAction;
        implOnMalformedInput(newAction);
        return this;
    }

    protected void implOnMalformedInput(CodingErrorAction newAction) {
    }

    public CodingErrorAction unmappableCharacterAction() {
        return unmappableCharacterAction;
    }

    public final CharsetEncoder onUnmappableCharacter(CodingErrorAction newAction) {
        if (newAction == null) {
            throw new IllegalArgumentException("Null action");
        }
        unmappableCharacterAction = newAction;
        implOnUnmappableCharacter(newAction);
        return this;
    }

    protected void implOnUnmappableCharacter(CodingErrorAction newAction) {
    }

    public final float averageBytesPerChar() {
        return averageBytesPerChar;
    }

    public final float maxBytesPerChar() {
        return maxBytesPerChar;
    }

    private void throwIllegalStateException(int from, int to) {
        throw new IllegalStateException("Current state = " + STATE_NAMES[from] + ", new state = " + STATE_NAMES[to]);
    }

    public final CoderResult encode(CharBuffer in, ByteBuffer out, boolean endOfInput) {
        int newState = endOfInput ? ST_END : ST_CODING;
        if (state != ST_RESET && state != ST_CODING && !(endOfInput && state == ST_END)) {
            throwIllegalStateException(state, newState);
        }
        state = newState;
        for (;;) {
            CoderResult cr;
            try {
                cr = encodeLoop(in, out);
            } catch (BufferUnderflowException | BufferOverflowException x) {
                throw new CoderMalfunctionError(x);
            }
            if (cr.isOverflow()) {
                return cr;
            }
            if (cr.isUnderflow()) {
                if (endOfInput && in.hasRemaining()) {
                    cr = CoderResult.malformedForLength(in.remaining());
                } else {
                    return cr;
                }
            }
            CodingErrorAction action = cr.isMalformed() ? malformedInputAction : unmappableCharacterAction;
            if (action == CodingErrorAction.REPORT) {
                return cr;
            }
            if (action == CodingErrorAction.REPLACE) {
                if (out.remaining() < replacement.length) {
                    return CoderResult.OVERFLOW;
                }
                out.put(replacement);
            }
            in.position(in.position() + cr.length());
        }
    }

    public final CoderResult flush(ByteBuffer out) {
        if (state == ST_END) {
            CoderResult cr = implFlush(out);
            if (cr.isUnderflow()) {
                state = ST_FLUSHED;
            }
            return cr;
        }
        if (state != ST_FLUSHED) {
            throwIllegalStateException(state, ST_FLUSHED);
        }
        return CoderResult.UNDERFLOW;
    }

    protected CoderResult implFlush(ByteBuffer out) {
        return CoderResult.UNDERFLOW;
    }

    public final CharsetEncoder reset() {
        implReset();
        state = ST_RESET;
        return this;
    }

    protected void implReset() {
    }

    protected abstract CoderResult encodeLoop(CharBuffer in, ByteBuffer out);

    public final ByteBuffer encode(CharBuffer in) throws CharacterCodingException {
        int n = (int) (in.remaining() * averageBytesPerChar());
        ByteBuffer out = ByteBuffer.allocate(n);
        if (n == 0 && in.remaining() == 0) {
            return out;
        }
        reset();
        for (;;) {
            CoderResult cr = in.hasRemaining() ? encode(in, out, true) : CoderResult.UNDERFLOW;
            if (cr.isUnderflow()) {
                cr = flush(out);
            }
            if (cr.isUnderflow()) {
                break;
            }
            if (cr.isOverflow()) {
                n = 2 * n + 1;
                ByteBuffer o = ByteBuffer.allocate(n);
                out.flip();
                o.put(out);
                out = o;
                continue;
            }
            cr.throwException();
        }
        out.flip();
        return out;
    }

    /** Tries to encode c alone with REPORT actions. */
    public boolean canEncode(char c) {
        CharBuffer cb = CharBuffer.allocate(1);
        cb.put(c);
        cb.flip();
        return canEncode(cb);
    }

    public boolean canEncode(CharSequence cs) {
        CharBuffer cb = cs instanceof CharBuffer ? ((CharBuffer) cs).duplicate() : CharBuffer.wrap(cs.toString());
        CodingErrorAction ma = malformedInputAction, ua = unmappableCharacterAction;
        try {
            onMalformedInput(CodingErrorAction.REPORT);
            onUnmappableCharacter(CodingErrorAction.REPORT);
            encode(cb);
            return true;
        } catch (CharacterCodingException x) {
            return false;
        } finally {
            onMalformedInput(ma);
            onUnmappableCharacter(ua);
            reset();
        }
    }
}
