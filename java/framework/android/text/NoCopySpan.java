package android.text;

/** Marks spans that must not be copied when the text is copied. */
public interface NoCopySpan {
    class Concrete implements NoCopySpan {
        public Concrete() {}
    }
}
