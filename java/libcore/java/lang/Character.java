package java.lang;

public final class Character implements java.io.Serializable, Comparable<Character> {
    public static final int MIN_RADIX = 2;
    public static final int MAX_RADIX = 36;
    public static final char MIN_VALUE = '\u0000';
    public static final char MAX_VALUE = '￿';
    public static final char MIN_HIGH_SURROGATE = '\uD800';
    public static final char MAX_HIGH_SURROGATE = '\uDBFF';
    public static final char MIN_LOW_SURROGATE = '\uDC00';
    public static final char MAX_LOW_SURROGATE = '\uDFFF';
    public static final char MIN_SURROGATE = MIN_HIGH_SURROGATE;
    public static final char MAX_SURROGATE = MAX_LOW_SURROGATE;
    public static final int MIN_SUPPLEMENTARY_CODE_POINT = 0x010000;
    public static final int MIN_CODE_POINT = 0x000000;
    public static final int MAX_CODE_POINT = 0x10FFFF;
    public static final int SIZE = 16;
    public static final int BYTES = 2;
    public static final Class<Character> TYPE = (Class<Character>) Class.getPrimitiveClass("char");

    public static final byte UNASSIGNED = 0;
    public static final byte UPPERCASE_LETTER = 1;
    public static final byte LOWERCASE_LETTER = 2;
    public static final byte TITLECASE_LETTER = 3;
    public static final byte MODIFIER_LETTER = 4;
    public static final byte OTHER_LETTER = 5;
    public static final byte NON_SPACING_MARK = 6;
    public static final byte ENCLOSING_MARK = 7;
    public static final byte COMBINING_SPACING_MARK = 8;
    public static final byte DECIMAL_DIGIT_NUMBER = 9;
    public static final byte LETTER_NUMBER = 10;
    public static final byte OTHER_NUMBER = 11;
    public static final byte SPACE_SEPARATOR = 12;
    public static final byte LINE_SEPARATOR = 13;
    public static final byte PARAGRAPH_SEPARATOR = 14;
    public static final byte CONTROL = 15;
    public static final byte FORMAT = 16;
    public static final byte PRIVATE_USE = 18;
    public static final byte SURROGATE = 19;
    public static final byte DASH_PUNCTUATION = 20;
    public static final byte START_PUNCTUATION = 21;
    public static final byte END_PUNCTUATION = 22;
    public static final byte CONNECTOR_PUNCTUATION = 23;
    public static final byte OTHER_PUNCTUATION = 24;
    public static final byte MATH_SYMBOL = 25;
    public static final byte CURRENCY_SYMBOL = 26;
    public static final byte MODIFIER_SYMBOL = 27;
    public static final byte OTHER_SYMBOL = 28;
    public static final byte INITIAL_QUOTE_PUNCTUATION = 29;
    public static final byte FINAL_QUOTE_PUNCTUATION = 30;

    public static final byte DIRECTIONALITY_UNDEFINED = -1;
    public static final byte DIRECTIONALITY_LEFT_TO_RIGHT = 0;
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT = 1;
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC = 2;
    public static final byte DIRECTIONALITY_EUROPEAN_NUMBER = 3;
    public static final byte DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR = 4;
    public static final byte DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR = 5;
    public static final byte DIRECTIONALITY_ARABIC_NUMBER = 6;
    public static final byte DIRECTIONALITY_COMMON_NUMBER_SEPARATOR = 7;
    public static final byte DIRECTIONALITY_NONSPACING_MARK = 8;
    public static final byte DIRECTIONALITY_BOUNDARY_NEUTRAL = 9;
    public static final byte DIRECTIONALITY_PARAGRAPH_SEPARATOR = 10;
    public static final byte DIRECTIONALITY_SEGMENT_SEPARATOR = 11;
    public static final byte DIRECTIONALITY_WHITESPACE = 12;
    public static final byte DIRECTIONALITY_OTHER_NEUTRALS = 13;
    public static final byte DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING = 14;
    public static final byte DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE = 15;
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING = 16;
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE = 17;
    public static final byte DIRECTIONALITY_POP_DIRECTIONAL_FORMAT = 18;
    public static final byte DIRECTIONALITY_LEFT_TO_RIGHT_ISOLATE = 19;
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT_ISOLATE = 20;
    public static final byte DIRECTIONALITY_FIRST_STRONG_ISOLATE = 21;
    public static final byte DIRECTIONALITY_POP_DIRECTIONAL_ISOLATE = 22;

    private final char value;

    private static final Character[] CACHE = new Character[128];

    public Character(char value) {
        this.value = value;
    }

    public static Character valueOf(char c) {
        if (c < 128) {
            Character r = CACHE[c];
            if (r == null) {
                r = new Character(c);
                CACHE[c] = r;
            }
            return r;
        }
        return new Character(c);
    }

    public char charValue() {
        return value;
    }

    public int hashCode() {
        return value;
    }

    public static int hashCode(char c) {
        return c;
    }

    public boolean equals(Object obj) {
        return obj instanceof Character && ((Character) obj).value == value;
    }

    public String toString() {
        return String.valueOf(value);
    }

    public static String toString(char c) {
        return String.valueOf(c);
    }

    public static String toString(int codePoint) {
        return new String(toChars(codePoint));
    }

    public int compareTo(Character another) {
        return value - another.value;
    }

    public static int compare(char x, char y) {
        return x - y;
    }

    public static boolean isValidCodePoint(int codePoint) {
        return codePoint >= MIN_CODE_POINT && codePoint <= MAX_CODE_POINT;
    }

    public static boolean isBmpCodePoint(int codePoint) {
        return (codePoint >>> 16) == 0;
    }

    public static boolean isSupplementaryCodePoint(int codePoint) {
        return codePoint >= MIN_SUPPLEMENTARY_CODE_POINT && codePoint <= MAX_CODE_POINT;
    }

    public static boolean isHighSurrogate(char ch) {
        return ch >= MIN_HIGH_SURROGATE && ch <= MAX_HIGH_SURROGATE;
    }

    public static boolean isLowSurrogate(char ch) {
        return ch >= MIN_LOW_SURROGATE && ch <= MAX_LOW_SURROGATE;
    }

    public static boolean isSurrogate(char ch) {
        return ch >= MIN_SURROGATE && ch <= MAX_SURROGATE;
    }

    public static boolean isSurrogatePair(char high, char low) {
        return isHighSurrogate(high) && isLowSurrogate(low);
    }

    public static int charCount(int codePoint) {
        return codePoint >= MIN_SUPPLEMENTARY_CODE_POINT ? 2 : 1;
    }

    public static int toCodePoint(char high, char low) {
        return ((high - MIN_HIGH_SURROGATE) << 10) + (low - MIN_LOW_SURROGATE) + MIN_SUPPLEMENTARY_CODE_POINT;
    }

    public static int codePointAt(CharSequence seq, int index) {
        char c1 = seq.charAt(index);
        if (isHighSurrogate(c1) && index + 1 < seq.length()) {
            char c2 = seq.charAt(index + 1);
            if (isLowSurrogate(c2)) {
                return toCodePoint(c1, c2);
            }
        }
        return c1;
    }

    public static int codePointAt(char[] a, int index) {
        return codePointAt(a, index, a.length);
    }

    public static int codePointAt(char[] a, int index, int limit) {
        char c1 = a[index];
        if (isHighSurrogate(c1) && index + 1 < limit) {
            char c2 = a[index + 1];
            if (isLowSurrogate(c2)) {
                return toCodePoint(c1, c2);
            }
        }
        return c1;
    }

    public static int codePointBefore(char[] a, int index) {
        char c2 = a[index - 1];
        if (isLowSurrogate(c2) && index - 2 >= 0) {
            char c1 = a[index - 2];
            if (isHighSurrogate(c1)) {
                return toCodePoint(c1, c2);
            }
        }
        return c2;
    }

    public static int codePointBefore(CharSequence seq, int index) {
        char c2 = seq.charAt(index - 1);
        if (isLowSurrogate(c2) && index - 2 >= 0) {
            char c1 = seq.charAt(index - 2);
            if (isHighSurrogate(c1)) {
                return toCodePoint(c1, c2);
            }
        }
        return c2;
    }

    public static char highSurrogate(int codePoint) {
        return (char) ((codePoint >>> 10) + (MIN_HIGH_SURROGATE - (MIN_SUPPLEMENTARY_CODE_POINT >>> 10)));
    }

    public static char lowSurrogate(int codePoint) {
        return (char) ((codePoint & 0x3ff) + MIN_LOW_SURROGATE);
    }

    public static char[] toChars(int codePoint) {
        if (isBmpCodePoint(codePoint)) {
            return new char[] {(char) codePoint};
        }
        return new char[] {highSurrogate(codePoint), lowSurrogate(codePoint)};
    }

    public static int toChars(int codePoint, char[] dst, int dstIndex) {
        if (isBmpCodePoint(codePoint)) {
            dst[dstIndex] = (char) codePoint;
            return 1;
        }
        dst[dstIndex] = highSurrogate(codePoint);
        dst[dstIndex + 1] = lowSurrogate(codePoint);
        return 2;
    }

    public static boolean isLowerCase(char ch) {
        return isLowerCase((int) ch);
    }

    public static boolean isLowerCase(int ch) {
        if (ch >= 'a' && ch <= 'z') {
            return true;
        }
        if (ch < 0x80) {
            return false;
        }
        return ch == 0xaa || ch == 0xb5 || ch == 0xba || (ch >= 0xdf && ch <= 0xff && ch != 0xf7)
                || (ch >= 0x100 && ch < 0x250 && toUpperCase(ch) != ch)
                || (ch >= 0x3b1 && ch <= 0x3c9) || (ch >= 0x430 && ch <= 0x45f);
    }

    public static boolean isUpperCase(char ch) {
        return isUpperCase((int) ch);
    }

    public static boolean isUpperCase(int ch) {
        if (ch >= 'A' && ch <= 'Z') {
            return true;
        }
        if (ch < 0x80) {
            return false;
        }
        return (ch >= 0xc0 && ch <= 0xde && ch != 0xd7) || (ch >= 0x100 && ch < 0x250 && toLowerCase(ch) != ch)
                || (ch >= 0x391 && ch <= 0x3a9) || (ch >= 0x400 && ch <= 0x42f);
    }

    public static boolean isTitleCase(char ch) {
        return ch == 0x1c5 || ch == 0x1c8 || ch == 0x1cb || ch == 0x1f2;
    }

    public static boolean isDigit(char ch) {
        return isDigit((int) ch);
    }

    public static boolean isDigit(int ch) {
        if (ch >= '0' && ch <= '9') {
            return true;
        }
        if (ch < 0x660) {
            return false;
        }
        return (ch >= 0x660 && ch <= 0x669) || (ch >= 0x6f0 && ch <= 0x6f9) || (ch >= 0x966 && ch <= 0x96f)
                || (ch >= 0xff10 && ch <= 0xff19);
    }

    public static boolean isDefined(char ch) {
        return true;
    }

    public static boolean isDefined(int ch) {
        return isValidCodePoint(ch);
    }

    public static boolean isLetter(char ch) {
        return isLetter((int) ch);
    }

    public static boolean isLetter(int ch) {
        if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
            return true;
        }
        if (ch < 0xaa) {
            return false;
        }
        if (ch < 0x100) {
            return ch == 0xaa || ch == 0xb5 || ch == 0xba || (ch >= 0xc0 && ch != 0xd7 && ch != 0xf7);
        }
        if (ch >= 0x2000 && ch < 0x3040) {
            return false; /* punctuation, symbols */
        }
        if (ch >= 0xff00 && ch <= 0xff20) {
            return false;
        }
        return !isWhitespace(ch) && !isDigit(ch) && !isSurrogate((char) ch) && ch != 0xfeff;
    }

    public static boolean isLetterOrDigit(char ch) {
        return isLetter(ch) || isDigit(ch);
    }

    public static boolean isLetterOrDigit(int ch) {
        return isLetter(ch) || isDigit(ch);
    }

    public static boolean isAlphabetic(int codePoint) {
        return isLetter(codePoint);
    }

    public static boolean isIdeographic(int ch) {
        return (ch >= 0x4e00 && ch <= 0x9fff) || (ch >= 0x3400 && ch <= 0x4dbf);
    }

    public static boolean isJavaIdentifierStart(char ch) {
        return isLetter(ch) || ch == '_' || ch == '$';
    }

    public static boolean isJavaIdentifierStart(int ch) {
        return isLetter(ch) || ch == '_' || ch == '$';
    }

    public static boolean isJavaIdentifierPart(char ch) {
        return isLetterOrDigit(ch) || ch == '_' || ch == '$';
    }

    public static boolean isJavaIdentifierPart(int ch) {
        return isLetterOrDigit(ch) || ch == '_' || ch == '$';
    }

    public static boolean isUnicodeIdentifierStart(char ch) {
        return isLetter(ch);
    }

    public static boolean isUnicodeIdentifierPart(char ch) {
        return isLetterOrDigit(ch) || ch == '_';
    }

    public static boolean isIdentifierIgnorable(char ch) {
        return (ch <= 8) || (ch >= 0xe && ch <= 0x1b) || (ch >= 0x7f && ch <= 0x9f);
    }

    public static boolean isSpaceChar(char ch) {
        return ch == ' ' || ch == 0xa0 || ch == 0x1680 || (ch >= 0x2000 && ch <= 0x200a) || ch == 0x2028
                || ch == 0x2029 || ch == 0x202f || ch == 0x205f || ch == 0x3000;
    }

    public static boolean isWhitespace(char ch) {
        return isWhitespace((int) ch);
    }

    public static boolean isWhitespace(int ch) {
        if ((ch >= 0x1c && ch <= 0x20) || (ch >= 0x09 && ch <= 0x0d)) {
            return true;
        }
        if (ch < 0x1000) {
            return false;
        }
        return ch == 0x1680 || (ch >= 0x2000 && ch <= 0x2006) || (ch >= 0x2008 && ch <= 0x200a) || ch == 0x2028
                || ch == 0x2029 || ch == 0x205f || ch == 0x3000;
    }

    public static boolean isISOControl(char ch) {
        return ch <= 0x1f || (ch >= 0x7f && ch <= 0x9f);
    }

    public static boolean isISOControl(int ch) {
        return (ch >= 0 && ch <= 0x1f) || (ch >= 0x7f && ch <= 0x9f);
    }

    public static int getType(char ch) {
        return getType((int) ch);
    }

    public static int getType(int ch) {
        if (ch >= 0xd800 && ch <= 0xdfff) {
            return SURROGATE;
        }
        if ((ch >= 0xe000 && ch <= 0xf8ff) || ch >= 0xf0000) {
            return PRIVATE_USE;
        }
        if (ch == 0xad || (ch >= 0x200b && ch <= 0x200f) || (ch >= 0x202a && ch <= 0x202e)
                || (ch >= 0x2060 && ch <= 0x2064) || (ch >= 0x2066 && ch <= 0x206f) || ch == 0xfeff) {
            return FORMAT;
        }
        if (ch == 0x2028) {
            return LINE_SEPARATOR;
        }
        if (ch == 0x2029) {
            return PARAGRAPH_SEPARATOR;
        }
        if (isUpperCase(ch)) {
            return UPPERCASE_LETTER;
        }
        if (isLowerCase(ch)) {
            return LOWERCASE_LETTER;
        }
        if (isDigit(ch)) {
            return DECIMAL_DIGIT_NUMBER;
        }
        if (isLetter(ch)) {
            return OTHER_LETTER;
        }
        if (isSpaceChar((char) ch)) {
            return SPACE_SEPARATOR;
        }
        if (isISOControl(ch)) {
            return CONTROL;
        }
        switch (ch) {
            case '(': case '[': case '{': case 0x2018: case 0x201c: case 0x3008: case 0x300a: case 0x300c:
            case 0x300e: case 0x3010: case 0xff08: case 0xff3b: case 0xff5b:
                return ch == 0x2018 || ch == 0x201c ? INITIAL_QUOTE_PUNCTUATION : START_PUNCTUATION;
            case ')': case ']': case '}': case 0x2019: case 0x201d: case 0x3009: case 0x300b: case 0x300d:
            case 0x300f: case 0x3011: case 0xff09: case 0xff3d: case 0xff5d:
                return ch == 0x2019 || ch == 0x201d ? FINAL_QUOTE_PUNCTUATION : END_PUNCTUATION;
            case '-': case 0x2010: case 0x2011: case 0x2012: case 0x2013: case 0x2014: case 0x2015:
                return DASH_PUNCTUATION;
            case '_':
                return CONNECTOR_PUNCTUATION;
            case '+': case '<': case '=': case '>': case '|': case '~': case 0xac: case 0xb1: case 0xd7: case 0xf7:
                return MATH_SYMBOL;
            case '$': case 0xa2: case 0xa3: case 0xa4: case 0xa5: case 0x20ac:
                return CURRENCY_SYMBOL;
            case '^': case '`': case 0xa8: case 0xaf: case 0xb4: case 0xb8:
                return MODIFIER_SYMBOL;
            case 0xa6: case 0xa9: case 0xae: case 0xb0:
                return OTHER_SYMBOL;
            default:
                break;
        }
        if (ch >= 0x300 && ch <= 0x36f) {
            return NON_SPACING_MARK;
        }
        if ((ch >= 0x2190 && ch <= 0x21ff) || (ch >= 0x2200 && ch <= 0x22ff)) {
            return MATH_SYMBOL;
        }
        if ((ch >= 0x2300 && ch <= 0x2bff) || (ch >= 0x1f000 && ch <= 0x1faff)) {
            return OTHER_SYMBOL;
        }
        return OTHER_PUNCTUATION;
    }

    public static byte getDirectionality(char ch) {
        return getDirectionality((int) ch);
    }

    /** Range-based approximation of the Unicode bidi class. */
    public static byte getDirectionality(int ch) {
        if (ch < 0 || ch > MAX_CODE_POINT) {
            return DIRECTIONALITY_UNDEFINED;
        }
        if (ch == '\n' || ch == '\r' || ch == 0x1c || ch == 0x1d || ch == 0x1e || ch == 0x85 || ch == 0x2029) {
            return DIRECTIONALITY_PARAGRAPH_SEPARATOR;
        }
        if (ch == '\t' || ch == 0x0b || ch == 0x1f) {
            return DIRECTIONALITY_SEGMENT_SEPARATOR;
        }
        if (ch == ' ' || ch == 0x0c || ch == 0x2028 || isSpaceChar((char) ch) && ch < 0x10000) {
            return DIRECTIONALITY_WHITESPACE;
        }
        if (ch >= '0' && ch <= '9') {
            return DIRECTIONALITY_EUROPEAN_NUMBER;
        }
        if (ch == '+' || ch == '-') {
            return DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR;
        }
        if (ch == '#' || ch == '$' || ch == '%' || (ch >= 0xa2 && ch <= 0xa5) || ch == 0xb0 || ch == 0xb1) {
            return DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR;
        }
        if (ch == ',' || ch == '.' || ch == '/' || ch == ':' || ch == 0xa0) {
            return DIRECTIONALITY_COMMON_NUMBER_SEPARATOR;
        }
        if (ch == 0x200e) return DIRECTIONALITY_LEFT_TO_RIGHT;
        if (ch == 0x200f) return DIRECTIONALITY_RIGHT_TO_LEFT;
        if (ch == 0x202a) return DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING;
        if (ch == 0x202b) return DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING;
        if (ch == 0x202c) return DIRECTIONALITY_POP_DIRECTIONAL_FORMAT;
        if (ch == 0x202d) return DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE;
        if (ch == 0x202e) return DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE;
        if (ch == 0x2066) return DIRECTIONALITY_LEFT_TO_RIGHT_ISOLATE;
        if (ch == 0x2067) return DIRECTIONALITY_RIGHT_TO_LEFT_ISOLATE;
        if (ch == 0x2068) return DIRECTIONALITY_FIRST_STRONG_ISOLATE;
        if (ch == 0x2069) return DIRECTIONALITY_POP_DIRECTIONAL_ISOLATE;
        if (ch < 0x20 || (ch >= 0x7f && ch <= 0x9f) || ch == 0xad || (ch >= 0x200b && ch <= 0x200d) || ch == 0xfeff) {
            return DIRECTIONALITY_BOUNDARY_NEUTRAL;
        }
        if ((ch >= 0x300 && ch <= 0x36f) || (ch >= 0x591 && ch <= 0x5bd) || (ch >= 0x610 && ch <= 0x61a)
                || (ch >= 0x64b && ch <= 0x65f) || (ch >= 0x20d0 && ch <= 0x20ff) || (ch >= 0xfe00 && ch <= 0xfe0f)) {
            return DIRECTIONALITY_NONSPACING_MARK;
        }
        if ((ch >= 0x660 && ch <= 0x669) || (ch >= 0x6f0 && ch <= 0x6f9)) {
            return DIRECTIONALITY_ARABIC_NUMBER;
        }
        if ((ch >= 0x590 && ch <= 0x5ff) || (ch >= 0x7c0 && ch <= 0x85f) || (ch >= 0xfb1d && ch <= 0xfb4f)
                || (ch >= 0x10800 && ch <= 0x10fff) || (ch >= 0x1e800 && ch <= 0x1edff)) {
            return DIRECTIONALITY_RIGHT_TO_LEFT;
        }
        if ((ch >= 0x600 && ch <= 0x7bf) || (ch >= 0x860 && ch <= 0x8ff) || (ch >= 0xfb50 && ch <= 0xfdff)
                || (ch >= 0xfe70 && ch <= 0xfefe) || (ch >= 0x1ee00 && ch <= 0x1eeff)) {
            return DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
        }
        if (isLetterOrDigit(ch) || (ch >= 0x2e80 && ch <= 0xd7ff) || (ch >= 0xf900 && ch <= 0xfaff)
                || (ch >= 0x20000 && ch <= 0x3ffff)) {
            return DIRECTIONALITY_LEFT_TO_RIGHT;
        }
        return DIRECTIONALITY_OTHER_NEUTRALS;
    }

    public static char toLowerCase(char ch) {
        return (char) toLowerCase((int) ch);
    }

    public static int toLowerCase(int ch) {
        if (ch >= 'A' && ch <= 'Z') {
            return ch + 32;
        }
        if (ch < 0xc0) {
            return ch;
        }
        if (ch <= 0xde) {
            return ch == 0xd7 ? ch : ch + 32;
        }
        if (ch >= 0x100 && ch < 0x138) {
            return (ch & 1) == 0 ? ch + 1 : ch;
        }
        if (ch >= 0x139 && ch < 0x149) {
            return (ch & 1) == 1 ? ch + 1 : ch;
        }
        if (ch >= 0x14a && ch < 0x178) {
            return (ch & 1) == 0 ? ch + 1 : ch;
        }
        if (ch == 0x178) {
            return 0xff;
        }
        if (ch >= 0x179 && ch < 0x17f) {
            return (ch & 1) == 1 ? ch + 1 : ch;
        }
        if (ch >= 0x391 && ch <= 0x3a9 && ch != 0x3a2) {
            return ch + 32;
        }
        if (ch >= 0x410 && ch <= 0x42f) {
            return ch + 32;
        }
        if (ch >= 0x400 && ch <= 0x40f) {
            return ch + 80;
        }
        if (ch >= 0xff21 && ch <= 0xff3a) {
            return ch + 32;
        }
        return ch;
    }

    public static char toUpperCase(char ch) {
        return (char) toUpperCase((int) ch);
    }

    public static int toUpperCase(int ch) {
        if (ch >= 'a' && ch <= 'z') {
            return ch - 32;
        }
        if (ch < 0xb5) {
            return ch;
        }
        if (ch == 0xb5) {
            return 0x39c;
        }
        if (ch >= 0xe0 && ch <= 0xfe) {
            return ch == 0xf7 ? ch : ch - 32;
        }
        if (ch == 0xff) {
            return 0x178;
        }
        if (ch >= 0x100 && ch < 0x138) {
            return (ch & 1) == 1 ? ch - 1 : ch;
        }
        if (ch >= 0x139 && ch < 0x149) {
            return (ch & 1) == 0 ? ch - 1 : ch;
        }
        if (ch >= 0x14a && ch < 0x178) {
            return (ch & 1) == 1 ? ch - 1 : ch;
        }
        if (ch >= 0x179 && ch < 0x17f) {
            return (ch & 1) == 0 ? ch - 1 : ch;
        }
        if (ch >= 0x3b1 && ch <= 0x3c9 && ch != 0x3c2) {
            return ch - 32;
        }
        if (ch >= 0x430 && ch <= 0x44f) {
            return ch - 32;
        }
        if (ch >= 0x450 && ch <= 0x45f) {
            return ch - 80;
        }
        if (ch >= 0xff41 && ch <= 0xff5a) {
            return ch - 32;
        }
        return ch;
    }

    public static char toTitleCase(char ch) {
        return toUpperCase(ch);
    }

    public static int toTitleCase(int ch) {
        return toUpperCase(ch);
    }

    public static int digit(char ch, int radix) {
        return digit((int) ch, radix);
    }

    public static int digit(int ch, int radix) {
        if (radix < MIN_RADIX || radix > MAX_RADIX) {
            return -1;
        }
        int d;
        if (ch >= '0' && ch <= '9') {
            d = ch - '0';
        } else if (ch >= 'a' && ch <= 'z') {
            d = ch - 'a' + 10;
        } else if (ch >= 'A' && ch <= 'Z') {
            d = ch - 'A' + 10;
        } else if (ch >= 0xff10 && ch <= 0xff19) {
            d = ch - 0xff10;
        } else if (ch >= 0x660 && ch <= 0x669) {
            d = ch - 0x660;
        } else {
            return -1;
        }
        return d < radix ? d : -1;
    }

    public static int getNumericValue(char ch) {
        return digit(ch, 36);
    }

    public static int getNumericValue(int ch) {
        return digit(ch, 36);
    }

    public static char forDigit(int digit, int radix) {
        if (digit >= radix || digit < 0 || radix < MIN_RADIX || radix > MAX_RADIX) {
            return '\0';
        }
        return digit < 10 ? (char) ('0' + digit) : (char) ('a' - 10 + digit);
    }

    public static char reverseBytes(char ch) {
        return (char) (((ch & 0xFF00) >> 8) | (ch << 8));
    }

    public static boolean isMirrored(char ch) {
        return "()<>[]{}".indexOf(ch) >= 0;
    }

    public static class Subset {
        private final String name;

        protected Subset(String name) {
            this.name = name;
        }

        public final String toString() {
            return name;
        }
    }

    public static final class UnicodeBlock extends Subset {
        private UnicodeBlock(String name) {
            super(name);
        }

        public static final UnicodeBlock BASIC_LATIN = new UnicodeBlock("BASIC_LATIN");
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS = new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS");
        public static final UnicodeBlock HIRAGANA = new UnicodeBlock("HIRAGANA");
        public static final UnicodeBlock KATAKANA = new UnicodeBlock("KATAKANA");
        public static final UnicodeBlock OTHER = new UnicodeBlock("OTHER");

        public static UnicodeBlock of(char c) {
            return of((int) c);
        }

        public static UnicodeBlock of(int c) {
            if (c < 0x80) {
                return BASIC_LATIN;
            }
            if (c >= 0x4e00 && c <= 0x9fff) {
                return CJK_UNIFIED_IDEOGRAPHS;
            }
            if (c >= 0x3040 && c <= 0x309f) {
                return HIRAGANA;
            }
            if (c >= 0x30a0 && c <= 0x30ff) {
                return KATAKANA;
            }
            return OTHER;
        }
    }
}
