package java.text;

import java.util.Locale;

public abstract class BreakIterator implements Cloneable {
    public static final int DONE = -1;

    protected BreakIterator() {
    }

    public abstract int first();

    public abstract int last();

    public abstract int next();

    public abstract int previous();

    public abstract int following(int offset);

    public abstract int current();

    public abstract void setText(String newText);

    public static BreakIterator getCharacterInstance() {
        return new Simple(0);
    }

    public static BreakIterator getCharacterInstance(Locale l) {
        return getCharacterInstance();
    }

    public static BreakIterator getWordInstance() {
        return new Simple(1);
    }

    public static BreakIterator getWordInstance(Locale l) {
        return getWordInstance();
    }

    public static BreakIterator getLineInstance() {
        return new Simple(2);
    }

    public static BreakIterator getLineInstance(Locale l) {
        return getLineInstance();
    }

    public static BreakIterator getSentenceInstance() {
        return new Simple(3);
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    static final class Simple extends BreakIterator {
        private final int kind;
        private String text = "";
        private int pos;

        Simple(int kind) {
            this.kind = kind;
        }

        public void setText(String newText) {
            text = newText;
            pos = 0;
        }

        public int first() {
            pos = 0;
            return 0;
        }

        public int last() {
            pos = text.length();
            return pos;
        }

        public int current() {
            return pos;
        }

        private boolean isBoundary(int i) {
            if (i <= 0 || i >= text.length()) {
                return true;
            }
            char a = text.charAt(i - 1), b = text.charAt(i);
            switch (kind) {
                case 0: return !(Character.isHighSurrogate(a) && Character.isLowSurrogate(b));
                case 1: return Character.isLetterOrDigit(a) != Character.isLetterOrDigit(b) || !Character.isLetterOrDigit(a);
                case 2: return Character.isWhitespace(a) && !Character.isWhitespace(b);
                default: return (a == '.' || a == '!' || a == '?') && Character.isWhitespace(b);
            }
        }

        public int next() {
            if (pos >= text.length()) {
                return DONE;
            }
            pos++;
            while (pos < text.length() && !isBoundary(pos)) {
                pos++;
            }
            return pos;
        }

        public int previous() {
            if (pos <= 0) {
                return DONE;
            }
            pos--;
            while (pos > 0 && !isBoundary(pos)) {
                pos--;
            }
            return pos;
        }

        public int following(int offset) {
            pos = offset;
            return next();
        }
    }
}
