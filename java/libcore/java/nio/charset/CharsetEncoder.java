package java.nio.charset;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

public abstract class CharsetEncoder {
    private final Charset charset;
    private final float average;
    private final float max;

    protected CharsetEncoder(Charset cs, float averageBytesPerChar, float maxBytesPerChar) {
        charset = cs;
        average = averageBytesPerChar;
        max = maxBytesPerChar;
    }

    public final Charset charset() {
        return charset;
    }

    public final float averageBytesPerChar() {
        return average;
    }

    public final float maxBytesPerChar() {
        return max;
    }

    public final CharsetEncoder onMalformedInput(CodingErrorAction newAction) {
        return this;
    }

    public final CharsetEncoder onUnmappableCharacter(CodingErrorAction newAction) {
        return this;
    }

    public final CharsetEncoder reset() {
        return this;
    }

    public boolean canEncode(char c) {
        return true;
    }

    public boolean canEncode(CharSequence cs) {
        return true;
    }

    public final ByteBuffer encode(CharBuffer in) throws CharacterCodingException {
        return charset.encode(in);
    }

    public final CoderResult encode(CharBuffer in, ByteBuffer out, boolean endOfInput) {
        out.put(charset.encode(in));
        return CoderResult.UNDERFLOW;
    }

    public final CoderResult flush(ByteBuffer out) {
        return CoderResult.UNDERFLOW;
    }
}
