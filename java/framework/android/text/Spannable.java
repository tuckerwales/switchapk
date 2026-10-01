package android.text;

/** Text whose markup can be changed. */
public interface Spannable extends Spanned {
    void setSpan(Object what, int start, int end, int flags);
    void removeSpan(Object what);

    /** framework-internal (hidden in AOSP). */
    default void removeSpan(Object what, int flags) { removeSpan(what); }

    class Factory {
        private static final Spannable.Factory sInstance = new Spannable.Factory();

        public Factory() {}

        public static Spannable.Factory getInstance() { return sInstance; }

        public Spannable newSpannable(CharSequence source) { return new SpannableString(source); }
    }
}
