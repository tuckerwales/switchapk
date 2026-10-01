package android.text.method;

/** Turns line breaks into spaces so a single-line field stays one line (AOSP). */
public class SingleLineTransformationMethod extends ReplacementTransformationMethod {
    private static final char[] ORIGINAL = new char[] {'\n', '\r'};
    private static final char[] REPLACEMENT = new char[] {' ', '\uFEFF'};

    private static SingleLineTransformationMethod sInstance;

    public static SingleLineTransformationMethod getInstance() {
        if (sInstance == null) sInstance = new SingleLineTransformationMethod();
        return sInstance;
    }

    protected char[] getOriginal() { return ORIGINAL; }

    protected char[] getReplacement() { return REPLACEMENT; }
}
