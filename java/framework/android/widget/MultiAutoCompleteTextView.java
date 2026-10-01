package android.widget;

import android.content.Context;
import android.text.Editable;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.QwertyKeyListener;
import android.util.AttributeSet;

/**
 * An {@link AutoCompleteTextView} that completes each token of a list, for example the
 * comma-separated addresses of an email field (AOSP MultiAutoCompleteTextView).
 */
public class MultiAutoCompleteTextView extends AutoCompleteTextView {
    private Tokenizer mTokenizer;

    public MultiAutoCompleteTextView(Context context) { this(context, null); }

    public MultiAutoCompleteTextView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.autoCompleteTextViewStyle);
    }

    public MultiAutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public MultiAutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setTokenizer(Tokenizer t) { mTokenizer = t; }

    @Override
    protected void performFiltering(CharSequence text, int keyCode) {
        if (enoughToFilter()) {
            int end = getSelectionEnd();
            int start = mTokenizer.findTokenStart(text, end);
            performFiltering(text, start, end, keyCode);
        } else {
            dismissDropDown();
            Filter f = getFilter();
            if (f != null) f.filter(null);
        }
    }

    @Override
    public boolean enoughToFilter() {
        Editable text = getText();
        int end = getSelectionEnd();
        if (end < 0 || mTokenizer == null) return false;
        int start = mTokenizer.findTokenStart(text, end);
        return end - start >= getThreshold();
    }

    @Override
    public void performValidation() {
        Validator v = getValidator();
        if (v == null || mTokenizer == null) return;
        Editable e = getText();
        int i = getText().length();
        while (i > 0) {
            int start = mTokenizer.findTokenStart(e, i);
            int end = mTokenizer.findTokenEnd(e, start);
            CharSequence sub = e.subSequence(start, end);
            if (TextUtils.isEmpty(sub)) {
                e.replace(start, i, "");
            } else if (!v.isValid(sub)) {
                e.replace(start, i, mTokenizer.terminateToken(v.fixText(sub)));
            }
            i = start;
        }
    }

    protected void performFiltering(CharSequence text, int start, int end, int keyCode) {
        getFilter().filter(text.subSequence(start, end), this);
    }

    @Override
    protected void replaceText(CharSequence text) {
        clearComposingText();
        int end = getSelectionEnd();
        int start = mTokenizer.findTokenStart(getText(), end);
        Editable editable = getText();
        String original = TextUtils.substring(editable, start, end);
        QwertyKeyListener.markAsReplaced(editable, start, end, original);
        editable.replace(start, end, mTokenizer.terminateToken(text));
    }

    @Override
    public CharSequence getAccessibilityClassName() { return MultiAutoCompleteTextView.class.getName(); }

    /** Finds the token around the cursor and terminates a completed one. */
    public static interface Tokenizer {
        int findTokenStart(CharSequence text, int cursor);

        int findTokenEnd(CharSequence text, int cursor);

        CharSequence terminateToken(CharSequence text);
    }

    /** Tokens separated by commas and spaces; completions end with ", ". */
    public static class CommaTokenizer implements Tokenizer {
        public CommaTokenizer() {}

        public int findTokenStart(CharSequence text, int cursor) {
            int i = cursor;
            while (i > 0 && text.charAt(i - 1) != ',') i--;
            while (i < cursor && text.charAt(i) == ' ') i++;
            return i;
        }

        public int findTokenEnd(CharSequence text, int cursor) {
            int i = cursor;
            int len = text.length();
            while (i < len) {
                if (text.charAt(i) == ',') return i;
                i++;
            }
            return len;
        }

        public CharSequence terminateToken(CharSequence text) {
            int i = text.length();
            while (i > 0 && text.charAt(i - 1) == ' ') i--;
            if (i > 0 && text.charAt(i - 1) == ',') return text;
            if (text instanceof Spanned) {
                SpannableString sp = new SpannableString(text + ", ");
                TextUtils.copySpansFrom((Spanned) text, 0, text.length(), Object.class, sp, 0);
                return sp;
            }
            return text + ", ";
        }
    }
}
