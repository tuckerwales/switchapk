package java.text;

import java.util.Locale;

/**
 * Text boundaries. The instances are rule-based approximations of Unicode
 * segmentation (UAX #29 grapheme clusters, words and sentences, UAX #14 line
 * breaks at spaces and hyphens) without the ICU data tables.
 */
public abstract class BreakIterator implements Cloneable {
    public static final int DONE = -1;

    protected BreakIterator() {
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    public abstract int first();

    public abstract int last();

    public abstract int next(int n);

    public abstract int next();

    public abstract int previous();

    public abstract int following(int offset);

    public int preceding(int offset) {
        int pos = following(offset);
        while (pos >= offset && pos != DONE) {
            pos = previous();
        }
        return pos;
    }

    public boolean isBoundary(int offset) {
        if (offset == 0) {
            return true;
        }
        int boundary = following(offset - 1);
        if (boundary == DONE) {
            throw new IllegalArgumentException();
        }
        return boundary == offset;
    }

    public abstract int current();

    public abstract CharacterIterator getText();

    public void setText(String newText) {
        setText(new StringCharacterIterator(newText));
    }

    public abstract void setText(CharacterIterator newText);

    public static BreakIterator getWordInstance() {
        return new Simple(Simple.WORD);
    }

    public static BreakIterator getWordInstance(Locale locale) {
        return getWordInstance();
    }

    public static BreakIterator getLineInstance() {
        return new Simple(Simple.LINE);
    }

    public static BreakIterator getLineInstance(Locale locale) {
        return getLineInstance();
    }

    public static BreakIterator getCharacterInstance() {
        return new Simple(Simple.CHARACTER);
    }

    public static BreakIterator getCharacterInstance(Locale locale) {
        return getCharacterInstance();
    }

    public static BreakIterator getSentenceInstance() {
        return new Simple(Simple.SENTENCE);
    }

    public static BreakIterator getSentenceInstance(Locale locale) {
        return getSentenceInstance();
    }

    public static synchronized Locale[] getAvailableLocales() {
        return new Locale[] { Locale.ROOT, Locale.US };
    }

    static final class Simple extends BreakIterator {
        static final int CHARACTER = 0, WORD = 1, LINE = 2, SENTENCE = 3;

        private final int kind;
        private CharacterIterator iter = new StringCharacterIterator("");
        private String text = "";
        private int begin;
        private int pos;

        Simple(int kind) {
            this.kind = kind;
        }

        public CharacterIterator getText() {
            return iter;
        }

        public void setText(CharacterIterator newText) {
            iter = newText;
            begin = newText.getBeginIndex();
            StringBuilder b = new StringBuilder(newText.getEndIndex() - begin);
            for (char c = newText.first(); c != CharacterIterator.DONE; c = newText.next()) {
                b.append(c);
            }
            text = b.toString();
            newText.first();
            pos = 0;
        }

        public int first() {
            pos = 0;
            return begin;
        }

        public int last() {
            pos = text.length();
            return begin + pos;
        }

        public int current() {
            return begin + pos;
        }

        public int next() {
            if (pos >= text.length()) {
                return DONE;
            }
            pos++;
            while (pos < text.length() && !boundaryAt(pos)) {
                pos++;
            }
            return begin + pos;
        }

        public int next(int n) {
            int result = current();
            while (n > 0) {
                result = next();
                n--;
                if (result == DONE) return DONE;
            }
            while (n < 0) {
                result = previous();
                n++;
                if (result == DONE) return DONE;
            }
            return result;
        }

        public int previous() {
            if (pos <= 0) {
                return DONE;
            }
            pos--;
            while (pos > 0 && !boundaryAt(pos)) {
                pos--;
            }
            return begin + pos;
        }

        public int following(int offset) {
            checkOffset(offset);
            pos = offset - begin;
            return next();
        }

        public int preceding(int offset) {
            checkOffset(offset);
            pos = offset - begin;
            return previous();
        }

        public boolean isBoundary(int offset) {
            checkOffset(offset);
            int p = offset - begin;
            boolean result = boundaryAt(p);
            if (result) {
                pos = p;
            } else {
                following(offset);
            }
            return result;
        }

        private void checkOffset(int offset) {
            if (offset < begin || offset > begin + text.length()) {
                throw new IllegalArgumentException("offset out of bounds");
            }
        }

        private boolean boundaryAt(int i) {
            if (i <= 0 || i >= text.length()) {
                return true;
            }
            if (!graphemeBoundary(i)) {
                return false;
            }
            int a = text.codePointBefore(i), b = text.codePointAt(i);
            switch (kind) {
                case CHARACTER:
                    return true;
                case WORD: {
                    if (isWordChar(a) && isWordChar(b)) return false;
                    // Keep "can't", "3.14" and "a_b" together.
                    if ((isMidWord(a) || isMidNum(a)) && i >= 2 && i < text.length()) {
                        int before = text.codePointBefore(i - Character.charCount(a));
                        if (isWordChar(before) && isWordChar(b)) return false;
                    }
                    if ((isMidWord(b) || isMidNum(b)) && isWordChar(a)) {
                        int after = i + Character.charCount(b);
                        if (after < text.length() && isWordChar(text.codePointAt(after))) return false;
                    }
                    return !(Character.isWhitespace(a) && Character.isWhitespace(b));
                }
                case LINE:
                    if (a == '\n' || a == '\r' && b != '\n') return true;
                    if (Character.isWhitespace(b) || b == 0xA0) return false;
                    return Character.isWhitespace(a) && a != 0xA0 || a == '-' && !Character.isWhitespace(b)
                            || isIdeographic(a) || isIdeographic(b);
                default: {
                    if (a == '\n' || a == '\r' && b != '\n' || a == 0x2029) return true;
                    if (Character.isWhitespace(b)) return false;
                    // Find the end of the previous run of spaces and closing punctuation.
                    int j = i;
                    while (j > 0 && (Character.isWhitespace(text.charAt(j - 1)) || isClose(text.charAt(j - 1)))) {
                        j--;
                    }
                    if (j == i || j == 0) return false;
                    char t = text.charAt(j - 1);
                    if (t != '.' && t != '!' && t != '?' && t != 0x3002) return false;
                    return Character.isWhitespace(text.charAt(i - 1)) || t == 0x3002;
                }
            }
        }

        private boolean graphemeBoundary(int i) {
            char lo = text.charAt(i), hi = text.charAt(i - 1);
            if (Character.isHighSurrogate(hi) && Character.isLowSurrogate(lo)) return false;
            if (hi == '\r' && lo == '\n') return false;
            int a = text.codePointBefore(i), b = text.codePointAt(i);
            if (isControl(a) || isControl(b)) return true;
            if (isExtend(b) || b == 0x200D) return false;
            if (a == 0x200D && isPictographic(b)) return false;
            // Hangul syllable sequences.
            if (isHangulL(a) && (isHangulL(b) || isHangulV(b) || isHangulLV(b))) return false;
            if ((isHangulV(a) || isHangulLV(a)) && (isHangulV(b) || isHangulT(b))) return false;
            if (isHangulT(a) && isHangulT(b)) return false;
            if (isRegionalIndicator(a) && isRegionalIndicator(b)) {
                int n = 0;
                for (int j = i; j > 0; ) {
                    int c = text.codePointBefore(j);
                    if (!isRegionalIndicator(c)) break;
                    n++;
                    j -= Character.charCount(c);
                }
                return n % 2 == 0;
            }
            return true;
        }

        private static boolean isControl(int c) {
            return c == '\r' || c == '\n' || Character.getType(c) == Character.CONTROL || c == 0x2028 || c == 0x2029;
        }

        private static boolean isExtend(int c) {
            int t = Character.getType(c);
            return t == Character.NON_SPACING_MARK || t == Character.ENCLOSING_MARK || t == Character.COMBINING_SPACING_MARK
                    || c >= 0xFE00 && c <= 0xFE0F || c >= 0xE0100 && c <= 0xE01EF
                    || c >= 0x1F3FB && c <= 0x1F3FF || c >= 0xE0020 && c <= 0xE007F;
        }

        private static boolean isPictographic(int c) {
            return c >= 0x1F000 && c <= 0x1FAFF || c >= 0x2600 && c <= 0x27BF || c >= 0x2300 && c <= 0x23FF
                    || c == 0x2B50 || c == 0x2B55 || c == 0x2764 || c == 0x00A9 || c == 0x00AE;
        }

        private static boolean isRegionalIndicator(int c) {
            return c >= 0x1F1E6 && c <= 0x1F1FF;
        }

        private static boolean isHangulL(int c) { return c >= 0x1100 && c <= 0x115F; }
        private static boolean isHangulV(int c) { return c >= 0x1160 && c <= 0x11A7; }
        private static boolean isHangulT(int c) { return c >= 0x11A8 && c <= 0x11FF; }
        private static boolean isHangulLV(int c) { return c >= 0xAC00 && c <= 0xD7A3; }

        private static boolean isWordChar(int c) {
            return Character.isLetterOrDigit(c) || c == '_' || isExtend(c);
        }

        private static boolean isMidWord(int c) {
            return c == '\'' || c == 0x2019 || c == ':' || c == 0xB7;
        }

        private static boolean isMidNum(int c) {
            return c == '.' || c == ',' || c == ';';
        }

        private static boolean isClose(char c) {
            return c == '"' || c == '\'' || c == ')' || c == ']' || c == '}' || c == 0x201D || c == 0x2019;
        }

        private static boolean isIdeographic(int c) {
            return c >= 0x3040 && c <= 0x30FF || c >= 0x3400 && c <= 0x9FFF || c >= 0xF900 && c <= 0xFAFF
                    || c >= 0x20000 && c <= 0x3FFFF;
        }
    }
}
