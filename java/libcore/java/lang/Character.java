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

        public final boolean equals(Object obj) {
            return this == obj;
        }

        public final int hashCode() {
            return super.hashCode();
        }

        public final String toString() {
            return name;
        }
    }

    public static final class UnicodeBlock extends Subset {
        private static final java.util.HashMap<String, UnicodeBlock> BY_NAME = new java.util.HashMap<>();

        private UnicodeBlock(String name) {
            super(name);
            BY_NAME.put(normalize(name), this);
        }

        /* Upper case without spaces, hyphens and underscores, so canonical and constant names meet. */
        private static String normalize(String name) {
            StringBuilder sb = new StringBuilder(name.length());
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c != ' ' && c != '_' && c != '-') {
                    sb.append(Character.toUpperCase(c));
                }
            }
            return sb.toString();
        }

        public static final UnicodeBlock BASIC_LATIN = new UnicodeBlock("BASIC_LATIN");
        public static final UnicodeBlock LATIN_1_SUPPLEMENT = new UnicodeBlock("LATIN_1_SUPPLEMENT");
        public static final UnicodeBlock LATIN_EXTENDED_A = new UnicodeBlock("LATIN_EXTENDED_A");
        public static final UnicodeBlock LATIN_EXTENDED_B = new UnicodeBlock("LATIN_EXTENDED_B");
        public static final UnicodeBlock IPA_EXTENSIONS = new UnicodeBlock("IPA_EXTENSIONS");
        public static final UnicodeBlock SPACING_MODIFIER_LETTERS = new UnicodeBlock("SPACING_MODIFIER_LETTERS");
        public static final UnicodeBlock COMBINING_DIACRITICAL_MARKS = new UnicodeBlock("COMBINING_DIACRITICAL_MARKS");
        public static final UnicodeBlock GREEK = new UnicodeBlock("GREEK");
        public static final UnicodeBlock CYRILLIC = new UnicodeBlock("CYRILLIC");
        public static final UnicodeBlock ARMENIAN = new UnicodeBlock("ARMENIAN");
        public static final UnicodeBlock HEBREW = new UnicodeBlock("HEBREW");
        public static final UnicodeBlock ARABIC = new UnicodeBlock("ARABIC");
        public static final UnicodeBlock DEVANAGARI = new UnicodeBlock("DEVANAGARI");
        public static final UnicodeBlock BENGALI = new UnicodeBlock("BENGALI");
        public static final UnicodeBlock GURMUKHI = new UnicodeBlock("GURMUKHI");
        public static final UnicodeBlock GUJARATI = new UnicodeBlock("GUJARATI");
        public static final UnicodeBlock ORIYA = new UnicodeBlock("ORIYA");
        public static final UnicodeBlock TAMIL = new UnicodeBlock("TAMIL");
        public static final UnicodeBlock TELUGU = new UnicodeBlock("TELUGU");
        public static final UnicodeBlock KANNADA = new UnicodeBlock("KANNADA");
        public static final UnicodeBlock MALAYALAM = new UnicodeBlock("MALAYALAM");
        public static final UnicodeBlock THAI = new UnicodeBlock("THAI");
        public static final UnicodeBlock LAO = new UnicodeBlock("LAO");
        public static final UnicodeBlock TIBETAN = new UnicodeBlock("TIBETAN");
        public static final UnicodeBlock GEORGIAN = new UnicodeBlock("GEORGIAN");
        public static final UnicodeBlock HANGUL_JAMO = new UnicodeBlock("HANGUL_JAMO");
        public static final UnicodeBlock LATIN_EXTENDED_ADDITIONAL = new UnicodeBlock("LATIN_EXTENDED_ADDITIONAL");
        public static final UnicodeBlock GREEK_EXTENDED = new UnicodeBlock("GREEK_EXTENDED");
        public static final UnicodeBlock GENERAL_PUNCTUATION = new UnicodeBlock("GENERAL_PUNCTUATION");
        public static final UnicodeBlock SUPERSCRIPTS_AND_SUBSCRIPTS = new UnicodeBlock("SUPERSCRIPTS_AND_SUBSCRIPTS");
        public static final UnicodeBlock CURRENCY_SYMBOLS = new UnicodeBlock("CURRENCY_SYMBOLS");
        public static final UnicodeBlock COMBINING_MARKS_FOR_SYMBOLS = new UnicodeBlock("COMBINING_MARKS_FOR_SYMBOLS");
        public static final UnicodeBlock LETTERLIKE_SYMBOLS = new UnicodeBlock("LETTERLIKE_SYMBOLS");
        public static final UnicodeBlock NUMBER_FORMS = new UnicodeBlock("NUMBER_FORMS");
        public static final UnicodeBlock ARROWS = new UnicodeBlock("ARROWS");
        public static final UnicodeBlock MATHEMATICAL_OPERATORS = new UnicodeBlock("MATHEMATICAL_OPERATORS");
        public static final UnicodeBlock MISCELLANEOUS_TECHNICAL = new UnicodeBlock("MISCELLANEOUS_TECHNICAL");
        public static final UnicodeBlock CONTROL_PICTURES = new UnicodeBlock("CONTROL_PICTURES");
        public static final UnicodeBlock OPTICAL_CHARACTER_RECOGNITION = new UnicodeBlock("OPTICAL_CHARACTER_RECOGNITION");
        public static final UnicodeBlock ENCLOSED_ALPHANUMERICS = new UnicodeBlock("ENCLOSED_ALPHANUMERICS");
        public static final UnicodeBlock BOX_DRAWING = new UnicodeBlock("BOX_DRAWING");
        public static final UnicodeBlock BLOCK_ELEMENTS = new UnicodeBlock("BLOCK_ELEMENTS");
        public static final UnicodeBlock GEOMETRIC_SHAPES = new UnicodeBlock("GEOMETRIC_SHAPES");
        public static final UnicodeBlock MISCELLANEOUS_SYMBOLS = new UnicodeBlock("MISCELLANEOUS_SYMBOLS");
        public static final UnicodeBlock DINGBATS = new UnicodeBlock("DINGBATS");
        public static final UnicodeBlock CJK_SYMBOLS_AND_PUNCTUATION = new UnicodeBlock("CJK_SYMBOLS_AND_PUNCTUATION");
        public static final UnicodeBlock HIRAGANA = new UnicodeBlock("HIRAGANA");
        public static final UnicodeBlock KATAKANA = new UnicodeBlock("KATAKANA");
        public static final UnicodeBlock BOPOMOFO = new UnicodeBlock("BOPOMOFO");
        public static final UnicodeBlock HANGUL_COMPATIBILITY_JAMO = new UnicodeBlock("HANGUL_COMPATIBILITY_JAMO");
        public static final UnicodeBlock KANBUN = new UnicodeBlock("KANBUN");
        public static final UnicodeBlock ENCLOSED_CJK_LETTERS_AND_MONTHS = new UnicodeBlock("ENCLOSED_CJK_LETTERS_AND_MONTHS");
        public static final UnicodeBlock CJK_COMPATIBILITY = new UnicodeBlock("CJK_COMPATIBILITY");
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS = new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS");
        public static final UnicodeBlock HANGUL_SYLLABLES = new UnicodeBlock("HANGUL_SYLLABLES");
        public static final UnicodeBlock PRIVATE_USE_AREA = new UnicodeBlock("PRIVATE_USE_AREA");
        public static final UnicodeBlock CJK_COMPATIBILITY_IDEOGRAPHS = new UnicodeBlock("CJK_COMPATIBILITY_IDEOGRAPHS");
        public static final UnicodeBlock ALPHABETIC_PRESENTATION_FORMS = new UnicodeBlock("ALPHABETIC_PRESENTATION_FORMS");
        public static final UnicodeBlock ARABIC_PRESENTATION_FORMS_A = new UnicodeBlock("ARABIC_PRESENTATION_FORMS_A");
        public static final UnicodeBlock COMBINING_HALF_MARKS = new UnicodeBlock("COMBINING_HALF_MARKS");
        public static final UnicodeBlock CJK_COMPATIBILITY_FORMS = new UnicodeBlock("CJK_COMPATIBILITY_FORMS");
        public static final UnicodeBlock SMALL_FORM_VARIANTS = new UnicodeBlock("SMALL_FORM_VARIANTS");
        public static final UnicodeBlock ARABIC_PRESENTATION_FORMS_B = new UnicodeBlock("ARABIC_PRESENTATION_FORMS_B");
        public static final UnicodeBlock HALFWIDTH_AND_FULLWIDTH_FORMS = new UnicodeBlock("HALFWIDTH_AND_FULLWIDTH_FORMS");
        public static final UnicodeBlock SPECIALS = new UnicodeBlock("SPECIALS");
        public static final UnicodeBlock SURROGATES_AREA = new UnicodeBlock("SURROGATES_AREA");
        public static final UnicodeBlock SYRIAC = new UnicodeBlock("SYRIAC");
        public static final UnicodeBlock THAANA = new UnicodeBlock("THAANA");
        public static final UnicodeBlock SINHALA = new UnicodeBlock("SINHALA");
        public static final UnicodeBlock MYANMAR = new UnicodeBlock("MYANMAR");
        public static final UnicodeBlock ETHIOPIC = new UnicodeBlock("ETHIOPIC");
        public static final UnicodeBlock CHEROKEE = new UnicodeBlock("CHEROKEE");
        public static final UnicodeBlock UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS = new UnicodeBlock("UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS");
        public static final UnicodeBlock OGHAM = new UnicodeBlock("OGHAM");
        public static final UnicodeBlock RUNIC = new UnicodeBlock("RUNIC");
        public static final UnicodeBlock KHMER = new UnicodeBlock("KHMER");
        public static final UnicodeBlock MONGOLIAN = new UnicodeBlock("MONGOLIAN");
        public static final UnicodeBlock BRAILLE_PATTERNS = new UnicodeBlock("BRAILLE_PATTERNS");
        public static final UnicodeBlock CJK_RADICALS_SUPPLEMENT = new UnicodeBlock("CJK_RADICALS_SUPPLEMENT");
        public static final UnicodeBlock KANGXI_RADICALS = new UnicodeBlock("KANGXI_RADICALS");
        public static final UnicodeBlock IDEOGRAPHIC_DESCRIPTION_CHARACTERS = new UnicodeBlock("IDEOGRAPHIC_DESCRIPTION_CHARACTERS");
        public static final UnicodeBlock BOPOMOFO_EXTENDED = new UnicodeBlock("BOPOMOFO_EXTENDED");
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A = new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A");
        public static final UnicodeBlock YI_SYLLABLES = new UnicodeBlock("YI_SYLLABLES");
        public static final UnicodeBlock YI_RADICALS = new UnicodeBlock("YI_RADICALS");
        public static final UnicodeBlock CYRILLIC_SUPPLEMENTARY = new UnicodeBlock("CYRILLIC_SUPPLEMENTARY");
        public static final UnicodeBlock TAGALOG = new UnicodeBlock("TAGALOG");
        public static final UnicodeBlock HANUNOO = new UnicodeBlock("HANUNOO");
        public static final UnicodeBlock BUHID = new UnicodeBlock("BUHID");
        public static final UnicodeBlock TAGBANWA = new UnicodeBlock("TAGBANWA");
        public static final UnicodeBlock LIMBU = new UnicodeBlock("LIMBU");
        public static final UnicodeBlock TAI_LE = new UnicodeBlock("TAI_LE");
        public static final UnicodeBlock KHMER_SYMBOLS = new UnicodeBlock("KHMER_SYMBOLS");
        public static final UnicodeBlock PHONETIC_EXTENSIONS = new UnicodeBlock("PHONETIC_EXTENSIONS");
        public static final UnicodeBlock MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A = new UnicodeBlock("MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A");
        public static final UnicodeBlock SUPPLEMENTAL_ARROWS_A = new UnicodeBlock("SUPPLEMENTAL_ARROWS_A");
        public static final UnicodeBlock SUPPLEMENTAL_ARROWS_B = new UnicodeBlock("SUPPLEMENTAL_ARROWS_B");
        public static final UnicodeBlock MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B = new UnicodeBlock("MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B");
        public static final UnicodeBlock SUPPLEMENTAL_MATHEMATICAL_OPERATORS = new UnicodeBlock("SUPPLEMENTAL_MATHEMATICAL_OPERATORS");
        public static final UnicodeBlock MISCELLANEOUS_SYMBOLS_AND_ARROWS = new UnicodeBlock("MISCELLANEOUS_SYMBOLS_AND_ARROWS");
        public static final UnicodeBlock KATAKANA_PHONETIC_EXTENSIONS = new UnicodeBlock("KATAKANA_PHONETIC_EXTENSIONS");
        public static final UnicodeBlock YIJING_HEXAGRAM_SYMBOLS = new UnicodeBlock("YIJING_HEXAGRAM_SYMBOLS");
        public static final UnicodeBlock VARIATION_SELECTORS = new UnicodeBlock("VARIATION_SELECTORS");
        public static final UnicodeBlock LINEAR_B_SYLLABARY = new UnicodeBlock("LINEAR_B_SYLLABARY");
        public static final UnicodeBlock LINEAR_B_IDEOGRAMS = new UnicodeBlock("LINEAR_B_IDEOGRAMS");
        public static final UnicodeBlock AEGEAN_NUMBERS = new UnicodeBlock("AEGEAN_NUMBERS");
        public static final UnicodeBlock OLD_ITALIC = new UnicodeBlock("OLD_ITALIC");
        public static final UnicodeBlock GOTHIC = new UnicodeBlock("GOTHIC");
        public static final UnicodeBlock UGARITIC = new UnicodeBlock("UGARITIC");
        public static final UnicodeBlock DESERET = new UnicodeBlock("DESERET");
        public static final UnicodeBlock SHAVIAN = new UnicodeBlock("SHAVIAN");
        public static final UnicodeBlock OSMANYA = new UnicodeBlock("OSMANYA");
        public static final UnicodeBlock CYPRIOT_SYLLABARY = new UnicodeBlock("CYPRIOT_SYLLABARY");
        public static final UnicodeBlock BYZANTINE_MUSICAL_SYMBOLS = new UnicodeBlock("BYZANTINE_MUSICAL_SYMBOLS");
        public static final UnicodeBlock MUSICAL_SYMBOLS = new UnicodeBlock("MUSICAL_SYMBOLS");
        public static final UnicodeBlock TAI_XUAN_JING_SYMBOLS = new UnicodeBlock("TAI_XUAN_JING_SYMBOLS");
        public static final UnicodeBlock MATHEMATICAL_ALPHANUMERIC_SYMBOLS = new UnicodeBlock("MATHEMATICAL_ALPHANUMERIC_SYMBOLS");
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B = new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B");
        public static final UnicodeBlock CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT = new UnicodeBlock("CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT");
        public static final UnicodeBlock TAGS = new UnicodeBlock("TAGS");
        public static final UnicodeBlock VARIATION_SELECTORS_SUPPLEMENT = new UnicodeBlock("VARIATION_SELECTORS_SUPPLEMENT");
        public static final UnicodeBlock SUPPLEMENTARY_PRIVATE_USE_AREA_A = new UnicodeBlock("SUPPLEMENTARY_PRIVATE_USE_AREA_A");
        public static final UnicodeBlock SUPPLEMENTARY_PRIVATE_USE_AREA_B = new UnicodeBlock("SUPPLEMENTARY_PRIVATE_USE_AREA_B");
        public static final UnicodeBlock HIGH_SURROGATES = new UnicodeBlock("HIGH_SURROGATES");
        public static final UnicodeBlock HIGH_PRIVATE_USE_SURROGATES = new UnicodeBlock("HIGH_PRIVATE_USE_SURROGATES");
        public static final UnicodeBlock LOW_SURROGATES = new UnicodeBlock("LOW_SURROGATES");
        public static final UnicodeBlock ARABIC_SUPPLEMENT = new UnicodeBlock("ARABIC_SUPPLEMENT");
        public static final UnicodeBlock NKO = new UnicodeBlock("NKO");
        public static final UnicodeBlock SAMARITAN = new UnicodeBlock("SAMARITAN");
        public static final UnicodeBlock MANDAIC = new UnicodeBlock("MANDAIC");
        public static final UnicodeBlock ETHIOPIC_SUPPLEMENT = new UnicodeBlock("ETHIOPIC_SUPPLEMENT");
        public static final UnicodeBlock UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED = new UnicodeBlock("UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED");
        public static final UnicodeBlock NEW_TAI_LUE = new UnicodeBlock("NEW_TAI_LUE");
        public static final UnicodeBlock BUGINESE = new UnicodeBlock("BUGINESE");
        public static final UnicodeBlock TAI_THAM = new UnicodeBlock("TAI_THAM");
        public static final UnicodeBlock BALINESE = new UnicodeBlock("BALINESE");
        public static final UnicodeBlock SUNDANESE = new UnicodeBlock("SUNDANESE");
        public static final UnicodeBlock BATAK = new UnicodeBlock("BATAK");
        public static final UnicodeBlock LEPCHA = new UnicodeBlock("LEPCHA");
        public static final UnicodeBlock OL_CHIKI = new UnicodeBlock("OL_CHIKI");
        public static final UnicodeBlock VEDIC_EXTENSIONS = new UnicodeBlock("VEDIC_EXTENSIONS");
        public static final UnicodeBlock PHONETIC_EXTENSIONS_SUPPLEMENT = new UnicodeBlock("PHONETIC_EXTENSIONS_SUPPLEMENT");
        public static final UnicodeBlock COMBINING_DIACRITICAL_MARKS_SUPPLEMENT = new UnicodeBlock("COMBINING_DIACRITICAL_MARKS_SUPPLEMENT");
        public static final UnicodeBlock GLAGOLITIC = new UnicodeBlock("GLAGOLITIC");
        public static final UnicodeBlock LATIN_EXTENDED_C = new UnicodeBlock("LATIN_EXTENDED_C");
        public static final UnicodeBlock COPTIC = new UnicodeBlock("COPTIC");
        public static final UnicodeBlock GEORGIAN_SUPPLEMENT = new UnicodeBlock("GEORGIAN_SUPPLEMENT");
        public static final UnicodeBlock TIFINAGH = new UnicodeBlock("TIFINAGH");
        public static final UnicodeBlock ETHIOPIC_EXTENDED = new UnicodeBlock("ETHIOPIC_EXTENDED");
        public static final UnicodeBlock CYRILLIC_EXTENDED_A = new UnicodeBlock("CYRILLIC_EXTENDED_A");
        public static final UnicodeBlock SUPPLEMENTAL_PUNCTUATION = new UnicodeBlock("SUPPLEMENTAL_PUNCTUATION");
        public static final UnicodeBlock CJK_STROKES = new UnicodeBlock("CJK_STROKES");
        public static final UnicodeBlock LISU = new UnicodeBlock("LISU");
        public static final UnicodeBlock VAI = new UnicodeBlock("VAI");
        public static final UnicodeBlock CYRILLIC_EXTENDED_B = new UnicodeBlock("CYRILLIC_EXTENDED_B");
        public static final UnicodeBlock BAMUM = new UnicodeBlock("BAMUM");
        public static final UnicodeBlock MODIFIER_TONE_LETTERS = new UnicodeBlock("MODIFIER_TONE_LETTERS");
        public static final UnicodeBlock LATIN_EXTENDED_D = new UnicodeBlock("LATIN_EXTENDED_D");
        public static final UnicodeBlock SYLOTI_NAGRI = new UnicodeBlock("SYLOTI_NAGRI");
        public static final UnicodeBlock COMMON_INDIC_NUMBER_FORMS = new UnicodeBlock("COMMON_INDIC_NUMBER_FORMS");
        public static final UnicodeBlock PHAGS_PA = new UnicodeBlock("PHAGS_PA");
        public static final UnicodeBlock SAURASHTRA = new UnicodeBlock("SAURASHTRA");
        public static final UnicodeBlock DEVANAGARI_EXTENDED = new UnicodeBlock("DEVANAGARI_EXTENDED");
        public static final UnicodeBlock KAYAH_LI = new UnicodeBlock("KAYAH_LI");
        public static final UnicodeBlock REJANG = new UnicodeBlock("REJANG");
        public static final UnicodeBlock HANGUL_JAMO_EXTENDED_A = new UnicodeBlock("HANGUL_JAMO_EXTENDED_A");
        public static final UnicodeBlock JAVANESE = new UnicodeBlock("JAVANESE");
        public static final UnicodeBlock CHAM = new UnicodeBlock("CHAM");
        public static final UnicodeBlock MYANMAR_EXTENDED_A = new UnicodeBlock("MYANMAR_EXTENDED_A");
        public static final UnicodeBlock TAI_VIET = new UnicodeBlock("TAI_VIET");
        public static final UnicodeBlock ETHIOPIC_EXTENDED_A = new UnicodeBlock("ETHIOPIC_EXTENDED_A");
        public static final UnicodeBlock MEETEI_MAYEK = new UnicodeBlock("MEETEI_MAYEK");
        public static final UnicodeBlock HANGUL_JAMO_EXTENDED_B = new UnicodeBlock("HANGUL_JAMO_EXTENDED_B");
        public static final UnicodeBlock VERTICAL_FORMS = new UnicodeBlock("VERTICAL_FORMS");
        public static final UnicodeBlock ANCIENT_GREEK_NUMBERS = new UnicodeBlock("ANCIENT_GREEK_NUMBERS");
        public static final UnicodeBlock ANCIENT_SYMBOLS = new UnicodeBlock("ANCIENT_SYMBOLS");
        public static final UnicodeBlock PHAISTOS_DISC = new UnicodeBlock("PHAISTOS_DISC");
        public static final UnicodeBlock LYCIAN = new UnicodeBlock("LYCIAN");
        public static final UnicodeBlock CARIAN = new UnicodeBlock("CARIAN");
        public static final UnicodeBlock OLD_PERSIAN = new UnicodeBlock("OLD_PERSIAN");
        public static final UnicodeBlock IMPERIAL_ARAMAIC = new UnicodeBlock("IMPERIAL_ARAMAIC");
        public static final UnicodeBlock PHOENICIAN = new UnicodeBlock("PHOENICIAN");
        public static final UnicodeBlock LYDIAN = new UnicodeBlock("LYDIAN");
        public static final UnicodeBlock KHAROSHTHI = new UnicodeBlock("KHAROSHTHI");
        public static final UnicodeBlock OLD_SOUTH_ARABIAN = new UnicodeBlock("OLD_SOUTH_ARABIAN");
        public static final UnicodeBlock AVESTAN = new UnicodeBlock("AVESTAN");
        public static final UnicodeBlock INSCRIPTIONAL_PARTHIAN = new UnicodeBlock("INSCRIPTIONAL_PARTHIAN");
        public static final UnicodeBlock INSCRIPTIONAL_PAHLAVI = new UnicodeBlock("INSCRIPTIONAL_PAHLAVI");
        public static final UnicodeBlock OLD_TURKIC = new UnicodeBlock("OLD_TURKIC");
        public static final UnicodeBlock RUMI_NUMERAL_SYMBOLS = new UnicodeBlock("RUMI_NUMERAL_SYMBOLS");
        public static final UnicodeBlock BRAHMI = new UnicodeBlock("BRAHMI");
        public static final UnicodeBlock KAITHI = new UnicodeBlock("KAITHI");
        public static final UnicodeBlock CUNEIFORM = new UnicodeBlock("CUNEIFORM");
        public static final UnicodeBlock CUNEIFORM_NUMBERS_AND_PUNCTUATION = new UnicodeBlock("CUNEIFORM_NUMBERS_AND_PUNCTUATION");
        public static final UnicodeBlock EGYPTIAN_HIEROGLYPHS = new UnicodeBlock("EGYPTIAN_HIEROGLYPHS");
        public static final UnicodeBlock BAMUM_SUPPLEMENT = new UnicodeBlock("BAMUM_SUPPLEMENT");
        public static final UnicodeBlock KANA_SUPPLEMENT = new UnicodeBlock("KANA_SUPPLEMENT");
        public static final UnicodeBlock ANCIENT_GREEK_MUSICAL_NOTATION = new UnicodeBlock("ANCIENT_GREEK_MUSICAL_NOTATION");
        public static final UnicodeBlock COUNTING_ROD_NUMERALS = new UnicodeBlock("COUNTING_ROD_NUMERALS");
        public static final UnicodeBlock MAHJONG_TILES = new UnicodeBlock("MAHJONG_TILES");
        public static final UnicodeBlock DOMINO_TILES = new UnicodeBlock("DOMINO_TILES");
        public static final UnicodeBlock PLAYING_CARDS = new UnicodeBlock("PLAYING_CARDS");
        public static final UnicodeBlock ENCLOSED_ALPHANUMERIC_SUPPLEMENT = new UnicodeBlock("ENCLOSED_ALPHANUMERIC_SUPPLEMENT");
        public static final UnicodeBlock ENCLOSED_IDEOGRAPHIC_SUPPLEMENT = new UnicodeBlock("ENCLOSED_IDEOGRAPHIC_SUPPLEMENT");
        public static final UnicodeBlock MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS = new UnicodeBlock("MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS");
        public static final UnicodeBlock EMOTICONS = new UnicodeBlock("EMOTICONS");
        public static final UnicodeBlock TRANSPORT_AND_MAP_SYMBOLS = new UnicodeBlock("TRANSPORT_AND_MAP_SYMBOLS");
        public static final UnicodeBlock ALCHEMICAL_SYMBOLS = new UnicodeBlock("ALCHEMICAL_SYMBOLS");
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C = new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C");
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D = new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D");
        public static final UnicodeBlock ARABIC_EXTENDED_A = new UnicodeBlock("ARABIC_EXTENDED_A");
        public static final UnicodeBlock SUNDANESE_SUPPLEMENT = new UnicodeBlock("SUNDANESE_SUPPLEMENT");
        public static final UnicodeBlock MEETEI_MAYEK_EXTENSIONS = new UnicodeBlock("MEETEI_MAYEK_EXTENSIONS");
        public static final UnicodeBlock MEROITIC_HIEROGLYPHS = new UnicodeBlock("MEROITIC_HIEROGLYPHS");
        public static final UnicodeBlock MEROITIC_CURSIVE = new UnicodeBlock("MEROITIC_CURSIVE");
        public static final UnicodeBlock SORA_SOMPENG = new UnicodeBlock("SORA_SOMPENG");
        public static final UnicodeBlock CHAKMA = new UnicodeBlock("CHAKMA");
        public static final UnicodeBlock SHARADA = new UnicodeBlock("SHARADA");
        public static final UnicodeBlock TAKRI = new UnicodeBlock("TAKRI");
        public static final UnicodeBlock MIAO = new UnicodeBlock("MIAO");
        public static final UnicodeBlock ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS = new UnicodeBlock("ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS");
        public static final UnicodeBlock COMBINING_DIACRITICAL_MARKS_EXTENDED = new UnicodeBlock("COMBINING_DIACRITICAL_MARKS_EXTENDED");
        public static final UnicodeBlock MYANMAR_EXTENDED_B = new UnicodeBlock("MYANMAR_EXTENDED_B");
        public static final UnicodeBlock LATIN_EXTENDED_E = new UnicodeBlock("LATIN_EXTENDED_E");
        public static final UnicodeBlock COPTIC_EPACT_NUMBERS = new UnicodeBlock("COPTIC_EPACT_NUMBERS");
        public static final UnicodeBlock OLD_PERMIC = new UnicodeBlock("OLD_PERMIC");
        public static final UnicodeBlock ELBASAN = new UnicodeBlock("ELBASAN");
        public static final UnicodeBlock CAUCASIAN_ALBANIAN = new UnicodeBlock("CAUCASIAN_ALBANIAN");
        public static final UnicodeBlock LINEAR_A = new UnicodeBlock("LINEAR_A");
        public static final UnicodeBlock PALMYRENE = new UnicodeBlock("PALMYRENE");
        public static final UnicodeBlock NABATAEAN = new UnicodeBlock("NABATAEAN");
        public static final UnicodeBlock OLD_NORTH_ARABIAN = new UnicodeBlock("OLD_NORTH_ARABIAN");
        public static final UnicodeBlock MANICHAEAN = new UnicodeBlock("MANICHAEAN");
        public static final UnicodeBlock PSALTER_PAHLAVI = new UnicodeBlock("PSALTER_PAHLAVI");
        public static final UnicodeBlock MAHAJANI = new UnicodeBlock("MAHAJANI");
        public static final UnicodeBlock SINHALA_ARCHAIC_NUMBERS = new UnicodeBlock("SINHALA_ARCHAIC_NUMBERS");
        public static final UnicodeBlock KHOJKI = new UnicodeBlock("KHOJKI");
        public static final UnicodeBlock KHUDAWADI = new UnicodeBlock("KHUDAWADI");
        public static final UnicodeBlock GRANTHA = new UnicodeBlock("GRANTHA");
        public static final UnicodeBlock TIRHUTA = new UnicodeBlock("TIRHUTA");
        public static final UnicodeBlock SIDDHAM = new UnicodeBlock("SIDDHAM");
        public static final UnicodeBlock MODI = new UnicodeBlock("MODI");
        public static final UnicodeBlock WARANG_CITI = new UnicodeBlock("WARANG_CITI");
        public static final UnicodeBlock PAU_CIN_HAU = new UnicodeBlock("PAU_CIN_HAU");
        public static final UnicodeBlock MRO = new UnicodeBlock("MRO");
        public static final UnicodeBlock BASSA_VAH = new UnicodeBlock("BASSA_VAH");
        public static final UnicodeBlock PAHAWH_HMONG = new UnicodeBlock("PAHAWH_HMONG");
        public static final UnicodeBlock DUPLOYAN = new UnicodeBlock("DUPLOYAN");
        public static final UnicodeBlock SHORTHAND_FORMAT_CONTROLS = new UnicodeBlock("SHORTHAND_FORMAT_CONTROLS");
        public static final UnicodeBlock MENDE_KIKAKUI = new UnicodeBlock("MENDE_KIKAKUI");
        public static final UnicodeBlock ORNAMENTAL_DINGBATS = new UnicodeBlock("ORNAMENTAL_DINGBATS");
        public static final UnicodeBlock GEOMETRIC_SHAPES_EXTENDED = new UnicodeBlock("GEOMETRIC_SHAPES_EXTENDED");
        public static final UnicodeBlock SUPPLEMENTAL_ARROWS_C = new UnicodeBlock("SUPPLEMENTAL_ARROWS_C");
        public static final UnicodeBlock CHEROKEE_SUPPLEMENT = new UnicodeBlock("CHEROKEE_SUPPLEMENT");
        public static final UnicodeBlock HATRAN = new UnicodeBlock("HATRAN");
        public static final UnicodeBlock OLD_HUNGARIAN = new UnicodeBlock("OLD_HUNGARIAN");
        public static final UnicodeBlock MULTANI = new UnicodeBlock("MULTANI");
        public static final UnicodeBlock AHOM = new UnicodeBlock("AHOM");
        public static final UnicodeBlock EARLY_DYNASTIC_CUNEIFORM = new UnicodeBlock("EARLY_DYNASTIC_CUNEIFORM");
        public static final UnicodeBlock ANATOLIAN_HIEROGLYPHS = new UnicodeBlock("ANATOLIAN_HIEROGLYPHS");
        public static final UnicodeBlock SUTTON_SIGNWRITING = new UnicodeBlock("SUTTON_SIGNWRITING");
        public static final UnicodeBlock SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS = new UnicodeBlock("SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS");
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E = new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E");
        public static final UnicodeBlock SYRIAC_SUPPLEMENT = new UnicodeBlock("SYRIAC_SUPPLEMENT");
        public static final UnicodeBlock CYRILLIC_EXTENDED_C = new UnicodeBlock("CYRILLIC_EXTENDED_C");
        public static final UnicodeBlock OSAGE = new UnicodeBlock("OSAGE");
        public static final UnicodeBlock NEWA = new UnicodeBlock("NEWA");
        public static final UnicodeBlock MONGOLIAN_SUPPLEMENT = new UnicodeBlock("MONGOLIAN_SUPPLEMENT");
        public static final UnicodeBlock MARCHEN = new UnicodeBlock("MARCHEN");
        public static final UnicodeBlock IDEOGRAPHIC_SYMBOLS_AND_PUNCTUATION = new UnicodeBlock("IDEOGRAPHIC_SYMBOLS_AND_PUNCTUATION");
        public static final UnicodeBlock TANGUT = new UnicodeBlock("TANGUT");
        public static final UnicodeBlock TANGUT_COMPONENTS = new UnicodeBlock("TANGUT_COMPONENTS");
        public static final UnicodeBlock KANA_EXTENDED_A = new UnicodeBlock("KANA_EXTENDED_A");
        public static final UnicodeBlock GLAGOLITIC_SUPPLEMENT = new UnicodeBlock("GLAGOLITIC_SUPPLEMENT");
        public static final UnicodeBlock ADLAM = new UnicodeBlock("ADLAM");
        public static final UnicodeBlock MASARAM_GONDI = new UnicodeBlock("MASARAM_GONDI");
        public static final UnicodeBlock ZANABAZAR_SQUARE = new UnicodeBlock("ZANABAZAR_SQUARE");
        public static final UnicodeBlock NUSHU = new UnicodeBlock("NUSHU");
        public static final UnicodeBlock SOYOMBO = new UnicodeBlock("SOYOMBO");
        public static final UnicodeBlock BHAIKSUKI = new UnicodeBlock("BHAIKSUKI");
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F = new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F");
        public static final UnicodeBlock GEORGIAN_EXTENDED = new UnicodeBlock("GEORGIAN_EXTENDED");
        public static final UnicodeBlock HANIFI_ROHINGYA = new UnicodeBlock("HANIFI_ROHINGYA");
        public static final UnicodeBlock OLD_SOGDIAN = new UnicodeBlock("OLD_SOGDIAN");
        public static final UnicodeBlock SOGDIAN = new UnicodeBlock("SOGDIAN");
        public static final UnicodeBlock DOGRA = new UnicodeBlock("DOGRA");
        public static final UnicodeBlock GUNJALA_GONDI = new UnicodeBlock("GUNJALA_GONDI");
        public static final UnicodeBlock MAKASAR = new UnicodeBlock("MAKASAR");
        public static final UnicodeBlock MEDEFAIDRIN = new UnicodeBlock("MEDEFAIDRIN");
        public static final UnicodeBlock MAYAN_NUMERALS = new UnicodeBlock("MAYAN_NUMERALS");
        public static final UnicodeBlock INDIC_SIYAQ_NUMBERS = new UnicodeBlock("INDIC_SIYAQ_NUMBERS");
        public static final UnicodeBlock CHESS_SYMBOLS = new UnicodeBlock("CHESS_SYMBOLS");
        public static final UnicodeBlock ELYMAIC = new UnicodeBlock("ELYMAIC");
        public static final UnicodeBlock NANDINAGARI = new UnicodeBlock("NANDINAGARI");
        public static final UnicodeBlock TAMIL_SUPPLEMENT = new UnicodeBlock("TAMIL_SUPPLEMENT");
        public static final UnicodeBlock EGYPTIAN_HIEROGLYPH_FORMAT_CONTROLS = new UnicodeBlock("EGYPTIAN_HIEROGLYPH_FORMAT_CONTROLS");
        public static final UnicodeBlock SMALL_KANA_EXTENSION = new UnicodeBlock("SMALL_KANA_EXTENSION");
        public static final UnicodeBlock NYIAKENG_PUACHUE_HMONG = new UnicodeBlock("NYIAKENG_PUACHUE_HMONG");
        public static final UnicodeBlock WANCHO = new UnicodeBlock("WANCHO");
        public static final UnicodeBlock OTTOMAN_SIYAQ_NUMBERS = new UnicodeBlock("OTTOMAN_SIYAQ_NUMBERS");
        public static final UnicodeBlock SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A = new UnicodeBlock("SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A");
        public static final UnicodeBlock YEZIDI = new UnicodeBlock("YEZIDI");
        public static final UnicodeBlock CHORASMIAN = new UnicodeBlock("CHORASMIAN");
        public static final UnicodeBlock DIVES_AKURU = new UnicodeBlock("DIVES_AKURU");
        public static final UnicodeBlock LISU_SUPPLEMENT = new UnicodeBlock("LISU_SUPPLEMENT");
        public static final UnicodeBlock KHITAN_SMALL_SCRIPT = new UnicodeBlock("KHITAN_SMALL_SCRIPT");
        public static final UnicodeBlock TANGUT_SUPPLEMENT = new UnicodeBlock("TANGUT_SUPPLEMENT");
        public static final UnicodeBlock SYMBOLS_FOR_LEGACY_COMPUTING = new UnicodeBlock("SYMBOLS_FOR_LEGACY_COMPUTING");
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_G = new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_G");

        /* Generated from the JDK's block ranges, limited to the blocks android.jar declares. */
        private static final int[] STARTS = {
            0x0, 0x80, 0x100, 0x180, 0x250, 0x2b0, 0x300, 0x370, 0x400, 0x500, 0x530, 0x590, 0x600, 0x700, 0x750,
            0x780, 0x7c0, 0x800, 0x840, 0x860, 0x870, 0x8a0, 0x900, 0x980, 0xa00, 0xa80, 0xb00, 0xb80, 0xc00, 0xc80,
            0xd00, 0xd80, 0xe00, 0xe80, 0xf00, 0x1000, 0x10a0, 0x1100, 0x1200, 0x1380, 0x13a0, 0x1400, 0x1680,
            0x16a0, 0x1700, 0x1720, 0x1740, 0x1760, 0x1780, 0x1800, 0x18b0, 0x1900, 0x1950, 0x1980, 0x19e0, 0x1a00,
            0x1a20, 0x1ab0, 0x1b00, 0x1b80, 0x1bc0, 0x1c00, 0x1c50, 0x1c80, 0x1c90, 0x1cc0, 0x1cd0, 0x1d00, 0x1d80,
            0x1dc0, 0x1e00, 0x1f00, 0x2000, 0x2070, 0x20a0, 0x20d0, 0x2100, 0x2150, 0x2190, 0x2200, 0x2300, 0x2400,
            0x2440, 0x2460, 0x2500, 0x2580, 0x25a0, 0x2600, 0x2700, 0x27c0, 0x27f0, 0x2800, 0x2900, 0x2980, 0x2a00,
            0x2b00, 0x2c00, 0x2c60, 0x2c80, 0x2d00, 0x2d30, 0x2d80, 0x2de0, 0x2e00, 0x2e80, 0x2f00, 0x2fe0, 0x2ff0,
            0x3000, 0x3040, 0x30a0, 0x3100, 0x3130, 0x3190, 0x31a0, 0x31c0, 0x31f0, 0x3200, 0x3300, 0x3400, 0x4dc0,
            0x4e00, 0xa000, 0xa490, 0xa4d0, 0xa500, 0xa640, 0xa6a0, 0xa700, 0xa720, 0xa800, 0xa830, 0xa840, 0xa880,
            0xa8e0, 0xa900, 0xa930, 0xa960, 0xa980, 0xa9e0, 0xaa00, 0xaa60, 0xaa80, 0xaae0, 0xab00, 0xab30, 0xab70,
            0xabc0, 0xac00, 0xd7b0, 0xd800, 0xdb80, 0xdc00, 0xe000, 0xf900, 0xfb00, 0xfb50, 0xfe00, 0xfe10, 0xfe20,
            0xfe30, 0xfe50, 0xfe70, 0xff00, 0xfff0, 0x10000, 0x10080, 0x10100, 0x10140, 0x10190, 0x101d0, 0x10200,
            0x10280, 0x102a0, 0x102e0, 0x10300, 0x10330, 0x10350, 0x10380, 0x103a0, 0x103e0, 0x10400, 0x10450,
            0x10480, 0x104b0, 0x10500, 0x10530, 0x10570, 0x10600, 0x10780, 0x10800, 0x10840, 0x10860, 0x10880,
            0x108b0, 0x108e0, 0x10900, 0x10920, 0x10940, 0x10980, 0x109a0, 0x10a00, 0x10a60, 0x10a80, 0x10aa0,
            0x10ac0, 0x10b00, 0x10b40, 0x10b60, 0x10b80, 0x10bb0, 0x10c00, 0x10c50, 0x10c80, 0x10d00, 0x10d40,
            0x10e60, 0x10e80, 0x10ec0, 0x10f00, 0x10f30, 0x10f70, 0x10fb0, 0x10fe0, 0x11000, 0x11080, 0x110d0,
            0x11100, 0x11150, 0x11180, 0x111e0, 0x11200, 0x11250, 0x11280, 0x112b0, 0x11300, 0x11380, 0x11400,
            0x11480, 0x114e0, 0x11580, 0x11600, 0x11660, 0x11680, 0x116d0, 0x11700, 0x11750, 0x11800, 0x11850,
            0x118a0, 0x11900, 0x11960, 0x119a0, 0x11a00, 0x11a50, 0x11ab0, 0x11ac0, 0x11b00, 0x11c00, 0x11c70,
            0x11cc0, 0x11d00, 0x11d60, 0x11db0, 0x11ee0, 0x11f00, 0x11fb0, 0x11fc0, 0x12000, 0x12400, 0x12480,
            0x12550, 0x13000, 0x13430, 0x13460, 0x14400, 0x14680, 0x16800, 0x16a40, 0x16a70, 0x16ad0, 0x16b00,
            0x16b90, 0x16e40, 0x16ea0, 0x16f00, 0x16fa0, 0x16fe0, 0x17000, 0x18800, 0x18b00, 0x18d00, 0x18d80,
            0x1b000, 0x1b100, 0x1b130, 0x1b170, 0x1b300, 0x1bc00, 0x1bca0, 0x1bcb0, 0x1d000, 0x1d100, 0x1d200,
            0x1d250, 0x1d2e0, 0x1d300, 0x1d360, 0x1d380, 0x1d400, 0x1d800, 0x1dab0, 0x1e000, 0x1e030, 0x1e100,
            0x1e150, 0x1e2c0, 0x1e300, 0x1e800, 0x1e8e0, 0x1e900, 0x1e960, 0x1ec70, 0x1ecc0, 0x1ed00, 0x1ed50,
            0x1ee00, 0x1ef00, 0x1f000, 0x1f030, 0x1f0a0, 0x1f100, 0x1f200, 0x1f300, 0x1f600, 0x1f650, 0x1f680,
            0x1f700, 0x1f780, 0x1f800, 0x1f900, 0x1fa00, 0x1fa70, 0x1fb00, 0x1fc00, 0x20000, 0x2a6e0, 0x2a700,
            0x2b740, 0x2b820, 0x2ceb0, 0x2ebf0, 0x2f800, 0x2fa20, 0x30000, 0x31350, 0xe0000, 0xe0080, 0xe0100,
            0xe01f0, 0xf0000, 0x100000,
        };

        private static final UnicodeBlock[] BLOCKS = {
            BASIC_LATIN, LATIN_1_SUPPLEMENT, LATIN_EXTENDED_A, LATIN_EXTENDED_B, IPA_EXTENSIONS,
            SPACING_MODIFIER_LETTERS, COMBINING_DIACRITICAL_MARKS, GREEK, CYRILLIC, CYRILLIC_SUPPLEMENTARY,
            ARMENIAN, HEBREW, ARABIC, SYRIAC, ARABIC_SUPPLEMENT, THAANA, NKO, SAMARITAN, MANDAIC, SYRIAC_SUPPLEMENT,
            null, ARABIC_EXTENDED_A, DEVANAGARI, BENGALI, GURMUKHI, GUJARATI, ORIYA, TAMIL, TELUGU, KANNADA,
            MALAYALAM, SINHALA, THAI, LAO, TIBETAN, MYANMAR, GEORGIAN, HANGUL_JAMO, ETHIOPIC, ETHIOPIC_SUPPLEMENT,
            CHEROKEE, UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS, OGHAM, RUNIC, TAGALOG, HANUNOO, BUHID, TAGBANWA, KHMER,
            MONGOLIAN, UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED, LIMBU, TAI_LE, NEW_TAI_LUE, KHMER_SYMBOLS,
            BUGINESE, TAI_THAM, COMBINING_DIACRITICAL_MARKS_EXTENDED, BALINESE, SUNDANESE, BATAK, LEPCHA, OL_CHIKI,
            CYRILLIC_EXTENDED_C, GEORGIAN_EXTENDED, SUNDANESE_SUPPLEMENT, VEDIC_EXTENSIONS, PHONETIC_EXTENSIONS,
            PHONETIC_EXTENSIONS_SUPPLEMENT, COMBINING_DIACRITICAL_MARKS_SUPPLEMENT, LATIN_EXTENDED_ADDITIONAL,
            GREEK_EXTENDED, GENERAL_PUNCTUATION, SUPERSCRIPTS_AND_SUBSCRIPTS, CURRENCY_SYMBOLS,
            COMBINING_MARKS_FOR_SYMBOLS, LETTERLIKE_SYMBOLS, NUMBER_FORMS, ARROWS, MATHEMATICAL_OPERATORS,
            MISCELLANEOUS_TECHNICAL, CONTROL_PICTURES, OPTICAL_CHARACTER_RECOGNITION, ENCLOSED_ALPHANUMERICS,
            BOX_DRAWING, BLOCK_ELEMENTS, GEOMETRIC_SHAPES, MISCELLANEOUS_SYMBOLS, DINGBATS,
            MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A, SUPPLEMENTAL_ARROWS_A, BRAILLE_PATTERNS, SUPPLEMENTAL_ARROWS_B,
            MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B, SUPPLEMENTAL_MATHEMATICAL_OPERATORS,
            MISCELLANEOUS_SYMBOLS_AND_ARROWS, GLAGOLITIC, LATIN_EXTENDED_C, COPTIC, GEORGIAN_SUPPLEMENT, TIFINAGH,
            ETHIOPIC_EXTENDED, CYRILLIC_EXTENDED_A, SUPPLEMENTAL_PUNCTUATION, CJK_RADICALS_SUPPLEMENT,
            KANGXI_RADICALS, null, IDEOGRAPHIC_DESCRIPTION_CHARACTERS, CJK_SYMBOLS_AND_PUNCTUATION, HIRAGANA,
            KATAKANA, BOPOMOFO, HANGUL_COMPATIBILITY_JAMO, KANBUN, BOPOMOFO_EXTENDED, CJK_STROKES,
            KATAKANA_PHONETIC_EXTENSIONS, ENCLOSED_CJK_LETTERS_AND_MONTHS, CJK_COMPATIBILITY,
            CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A, YIJING_HEXAGRAM_SYMBOLS, CJK_UNIFIED_IDEOGRAPHS, YI_SYLLABLES,
            YI_RADICALS, LISU, VAI, CYRILLIC_EXTENDED_B, BAMUM, MODIFIER_TONE_LETTERS, LATIN_EXTENDED_D,
            SYLOTI_NAGRI, COMMON_INDIC_NUMBER_FORMS, PHAGS_PA, SAURASHTRA, DEVANAGARI_EXTENDED, KAYAH_LI, REJANG,
            HANGUL_JAMO_EXTENDED_A, JAVANESE, MYANMAR_EXTENDED_B, CHAM, MYANMAR_EXTENDED_A, TAI_VIET,
            MEETEI_MAYEK_EXTENSIONS, ETHIOPIC_EXTENDED_A, LATIN_EXTENDED_E, CHEROKEE_SUPPLEMENT, MEETEI_MAYEK,
            HANGUL_SYLLABLES, HANGUL_JAMO_EXTENDED_B, HIGH_SURROGATES, HIGH_PRIVATE_USE_SURROGATES, LOW_SURROGATES,
            PRIVATE_USE_AREA, CJK_COMPATIBILITY_IDEOGRAPHS, ALPHABETIC_PRESENTATION_FORMS,
            ARABIC_PRESENTATION_FORMS_A, VARIATION_SELECTORS, VERTICAL_FORMS, COMBINING_HALF_MARKS,
            CJK_COMPATIBILITY_FORMS, SMALL_FORM_VARIANTS, ARABIC_PRESENTATION_FORMS_B,
            HALFWIDTH_AND_FULLWIDTH_FORMS, SPECIALS, LINEAR_B_SYLLABARY, LINEAR_B_IDEOGRAMS, AEGEAN_NUMBERS,
            ANCIENT_GREEK_NUMBERS, ANCIENT_SYMBOLS, PHAISTOS_DISC, null, LYCIAN, CARIAN, COPTIC_EPACT_NUMBERS,
            OLD_ITALIC, GOTHIC, OLD_PERMIC, UGARITIC, OLD_PERSIAN, null, DESERET, SHAVIAN, OSMANYA, OSAGE, ELBASAN,
            CAUCASIAN_ALBANIAN, null, LINEAR_A, null, CYPRIOT_SYLLABARY, IMPERIAL_ARAMAIC, PALMYRENE, NABATAEAN,
            null, HATRAN, PHOENICIAN, LYDIAN, null, MEROITIC_HIEROGLYPHS, MEROITIC_CURSIVE, KHAROSHTHI,
            OLD_SOUTH_ARABIAN, OLD_NORTH_ARABIAN, null, MANICHAEAN, AVESTAN, INSCRIPTIONAL_PARTHIAN,
            INSCRIPTIONAL_PAHLAVI, PSALTER_PAHLAVI, null, OLD_TURKIC, null, OLD_HUNGARIAN, HANIFI_ROHINGYA, null,
            RUMI_NUMERAL_SYMBOLS, YEZIDI, null, OLD_SOGDIAN, SOGDIAN, null, CHORASMIAN, ELYMAIC, BRAHMI, KAITHI,
            SORA_SOMPENG, CHAKMA, MAHAJANI, SHARADA, SINHALA_ARCHAIC_NUMBERS, KHOJKI, null, MULTANI, KHUDAWADI,
            GRANTHA, null, NEWA, TIRHUTA, null, SIDDHAM, MODI, MONGOLIAN_SUPPLEMENT, TAKRI, null, AHOM, null, DOGRA,
            null, WARANG_CITI, DIVES_AKURU, null, NANDINAGARI, ZANABAZAR_SQUARE, SOYOMBO, null, PAU_CIN_HAU, null,
            BHAIKSUKI, MARCHEN, null, MASARAM_GONDI, GUNJALA_GONDI, null, MAKASAR, null, LISU_SUPPLEMENT,
            TAMIL_SUPPLEMENT, CUNEIFORM, CUNEIFORM_NUMBERS_AND_PUNCTUATION, EARLY_DYNASTIC_CUNEIFORM, null,
            EGYPTIAN_HIEROGLYPHS, EGYPTIAN_HIEROGLYPH_FORMAT_CONTROLS, null, ANATOLIAN_HIEROGLYPHS, null,
            BAMUM_SUPPLEMENT, MRO, null, BASSA_VAH, PAHAWH_HMONG, null, MEDEFAIDRIN, null, MIAO, null,
            IDEOGRAPHIC_SYMBOLS_AND_PUNCTUATION, TANGUT, TANGUT_COMPONENTS, KHITAN_SMALL_SCRIPT, TANGUT_SUPPLEMENT,
            null, KANA_SUPPLEMENT, KANA_EXTENDED_A, SMALL_KANA_EXTENSION, NUSHU, null, DUPLOYAN,
            SHORTHAND_FORMAT_CONTROLS, null, BYZANTINE_MUSICAL_SYMBOLS, MUSICAL_SYMBOLS,
            ANCIENT_GREEK_MUSICAL_NOTATION, null, MAYAN_NUMERALS, TAI_XUAN_JING_SYMBOLS, COUNTING_ROD_NUMERALS,
            null, MATHEMATICAL_ALPHANUMERIC_SYMBOLS, SUTTON_SIGNWRITING, null, GLAGOLITIC_SUPPLEMENT, null,
            NYIAKENG_PUACHUE_HMONG, null, WANCHO, null, MENDE_KIKAKUI, null, ADLAM, null, INDIC_SIYAQ_NUMBERS, null,
            OTTOMAN_SIYAQ_NUMBERS, null, ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS, null, MAHJONG_TILES, DOMINO_TILES,
            PLAYING_CARDS, ENCLOSED_ALPHANUMERIC_SUPPLEMENT, ENCLOSED_IDEOGRAPHIC_SUPPLEMENT,
            MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS, EMOTICONS, ORNAMENTAL_DINGBATS, TRANSPORT_AND_MAP_SYMBOLS,
            ALCHEMICAL_SYMBOLS, GEOMETRIC_SHAPES_EXTENDED, SUPPLEMENTAL_ARROWS_C,
            SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS, CHESS_SYMBOLS, SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A,
            SYMBOLS_FOR_LEGACY_COMPUTING, null, CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B, null,
            CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C, CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D,
            CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E, CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F, null,
            CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT, null, CJK_UNIFIED_IDEOGRAPHS_EXTENSION_G, null, TAGS, null,
            VARIATION_SELECTORS_SUPPLEMENT, null, SUPPLEMENTARY_PRIVATE_USE_AREA_A,
            SUPPLEMENTARY_PRIVATE_USE_AREA_B,
        };

        public static UnicodeBlock of(char c) {
            return of((int) c);
        }

        public static UnicodeBlock of(int codePoint) {
            if (!isValidCodePoint(codePoint)) {
                throw new IllegalArgumentException(String.format("Not a valid Unicode code point: 0x%X", codePoint));
            }
            int lo = 0;
            int hi = STARTS.length - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (STARTS[mid] <= codePoint) {
                    lo = mid;
                } else {
                    hi = mid - 1;
                }
            }
            return BLOCKS[lo];
        }

        public static UnicodeBlock forName(String blockName) {
            UnicodeBlock b = BY_NAME.get(normalize(blockName));
            if (b == null) {
                throw new IllegalArgumentException("Not a valid block name: " + blockName);
            }
            return b;
        }
    }

    public static int codePointBefore(char[] a, int index, int start) {
        if (index <= start || start < 0 || index > a.length) {
            throw new IndexOutOfBoundsException();
        }
        char c2 = a[--index];
        if (isLowSurrogate(c2) && index > start) {
            char c1 = a[index - 1];
            if (isHighSurrogate(c1)) {
                return toCodePoint(c1, c2);
            }
        }
        return c2;
    }

    public static int codePointCount(CharSequence seq, int beginIndex, int endIndex) {
        int length = seq.length();
        if (beginIndex < 0 || endIndex > length || beginIndex > endIndex) {
            throw new IndexOutOfBoundsException();
        }
        int n = endIndex - beginIndex;
        for (int i = beginIndex; i < endIndex;) {
            if (isHighSurrogate(seq.charAt(i++)) && i < endIndex && isLowSurrogate(seq.charAt(i))) {
                n--;
                i++;
            }
        }
        return n;
    }

    public static int codePointCount(char[] a, int offset, int count) {
        if (count > a.length - offset || offset < 0 || count < 0) {
            throw new IndexOutOfBoundsException();
        }
        int endIndex = offset + count;
        int n = count;
        for (int i = offset; i < endIndex;) {
            if (isHighSurrogate(a[i++]) && i < endIndex && isLowSurrogate(a[i])) {
                n--;
                i++;
            }
        }
        return n;
    }

    public static int offsetByCodePoints(CharSequence seq, int index, int codePointOffset) {
        int length = seq.length();
        if (index < 0 || index > length) {
            throw new IndexOutOfBoundsException();
        }
        int x = index;
        if (codePointOffset >= 0) {
            int i;
            for (i = 0; x < length && i < codePointOffset; i++) {
                if (isHighSurrogate(seq.charAt(x++)) && x < length && isLowSurrogate(seq.charAt(x))) {
                    x++;
                }
            }
            if (i < codePointOffset) {
                throw new IndexOutOfBoundsException();
            }
        } else {
            int i;
            for (i = codePointOffset; x > 0 && i < 0; i++) {
                if (isLowSurrogate(seq.charAt(--x)) && x > 0 && isHighSurrogate(seq.charAt(x - 1))) {
                    x--;
                }
            }
            if (i < 0) {
                throw new IndexOutOfBoundsException();
            }
        }
        return x;
    }

    public static int offsetByCodePoints(char[] a, int start, int count, int index, int codePointOffset) {
        if (count > a.length - start || start < 0 || count < 0 || index < start || index > start + count) {
            throw new IndexOutOfBoundsException();
        }
        int x = index;
        if (codePointOffset >= 0) {
            int limit = start + count;
            int i;
            for (i = 0; x < limit && i < codePointOffset; i++) {
                if (isHighSurrogate(a[x++]) && x < limit && isLowSurrogate(a[x])) {
                    x++;
                }
            }
            if (i < codePointOffset) {
                throw new IndexOutOfBoundsException();
            }
        } else {
            int i;
            for (i = codePointOffset; x > start && i < 0; i++) {
                if (isLowSurrogate(a[--x]) && x > start && isHighSurrogate(a[x - 1])) {
                    x--;
                }
            }
            if (i < 0) {
                throw new IndexOutOfBoundsException();
            }
        }
        return x;
    }

    public static boolean isTitleCase(int codePoint) {
        return codePoint < 0x10000 && isTitleCase((char) codePoint);
    }

    public static boolean isMirrored(int codePoint) {
        return codePoint < 0x10000 && isMirrored((char) codePoint);
    }

    public static boolean isSpaceChar(int codePoint) {
        return codePoint < 0x10000 && isSpaceChar((char) codePoint);
    }

    public static boolean isIdentifierIgnorable(int codePoint) {
        return codePoint < 0x10000 ? isIdentifierIgnorable((char) codePoint)
                : (codePoint >= 0xe0001 && codePoint <= 0xe007f);
    }

    public static boolean isUnicodeIdentifierStart(int codePoint) {
        return isLetter(codePoint);
    }

    public static boolean isUnicodeIdentifierPart(int codePoint) {
        return isLetterOrDigit(codePoint) || codePoint == '_' || isIdentifierIgnorable(codePoint);
    }

    @Deprecated
    public static boolean isJavaLetter(char ch) {
        return isJavaIdentifierStart(ch);
    }

    @Deprecated
    public static boolean isJavaLetterOrDigit(char ch) {
        return isJavaIdentifierPart(ch);
    }

    @Deprecated
    public static boolean isSpace(char ch) {
        return ch <= 0x0020 && (((((1L << 0x0009) | (1L << 0x000A) | (1L << 0x000C) | (1L << 0x000D)
                | (1L << 0x0020)) >> ch) & 1L) != 0);
    }

    /* There is no Unicode name table in libcore: names are unknown (null) for valid code points. */
    public static String getName(int codePoint) {
        if (!isValidCodePoint(codePoint)) {
            throw new IllegalArgumentException("Not a valid Unicode code point: 0x" + Integer.toHexString(codePoint));
        }
        return null;
    }

    public static int codePointOf(String name) {
        throw new IllegalArgumentException("Unrecognized character name :" + name);
    }
}
