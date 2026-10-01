package android.text;

/** Text whose content and markup can be changed. */
public interface Editable extends CharSequence, GetChars, Spannable, Appendable {
    Editable replace(int st, int en, CharSequence source, int start, int end);
    Editable replace(int st, int en, CharSequence text);
    Editable insert(int where, CharSequence text, int start, int end);
    Editable insert(int where, CharSequence text);
    Editable delete(int st, int en);
    Editable append(CharSequence text);
    Editable append(CharSequence text, int start, int end);
    Editable append(char text);
    void clear();
    void clearSpans();
    void setFilters(InputFilter[] filters);
    InputFilter[] getFilters();

    class Factory {
        private static final Editable.Factory sInstance = new Editable.Factory();

        public Factory() {}

        public static Editable.Factory getInstance() { return sInstance; }

        public Editable newEditable(CharSequence source) { return new SpannableStringBuilder(source); }
    }
}
