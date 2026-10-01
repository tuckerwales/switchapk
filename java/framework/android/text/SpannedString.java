package android.text;

/** Immutable text with immutable markup. */
public final class SpannedString extends SpannableStringInternal implements CharSequence, GetChars, Spanned {
    public SpannedString(CharSequence source) { super(source, 0, source.length(), true); }

    private SpannedString(CharSequence source, int start, int end) { super(source, start, end, false); }

    public CharSequence subSequence(int start, int end) { return new SpannedString(this, start, end); }

    public static SpannedString valueOf(CharSequence source) {
        if (source instanceof SpannedString) return (SpannedString) source;
        return new SpannedString(source);
    }
}
