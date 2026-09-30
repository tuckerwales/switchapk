package java.nio.charset;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

public abstract class CharsetDecoder {
    private final Charset charset;
    private final float average;
    private final float max;

    protected CharsetDecoder(Charset cs, float averageCharsPerByte, float maxCharsPerByte) {
        charset = cs;
        average = averageCharsPerByte;
        max = maxCharsPerByte;
    }

    public final Charset charset() {
        return charset;
    }

    public final float averageCharsPerByte() {
        return average;
    }

    public final float maxCharsPerByte() {
        return max;
    }

    public final CharsetDecoder onMalformedInput(CodingErrorAction newAction) {
        return this;
    }

    public final CharsetDecoder onUnmappableCharacter(CodingErrorAction newAction) {
        return this;
    }

    public final CharsetDecoder reset() {
        return this;
    }

    public final CharBuffer decode(ByteBuffer in) throws CharacterCodingException {
        return charset.decode(in);
    }

    public final CoderResult decode(ByteBuffer in, CharBuffer out, boolean endOfInput) {
        CharBuffer cb = charset.decode(in);
        out.put(cb);
        return CoderResult.UNDERFLOW;
    }

    public final CoderResult flush(CharBuffer out) {
        return CoderResult.UNDERFLOW;
    }
}
