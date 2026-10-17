package android.os;

/** AOSP's path patterns for intent filters: literal, prefix, suffix, and the simple and advanced globs. */
public class PatternMatcher implements Parcelable {
    public static final int PATTERN_LITERAL = 0;
    public static final int PATTERN_PREFIX = 1;
    public static final int PATTERN_SIMPLE_GLOB = 2;
    public static final int PATTERN_ADVANCED_GLOB = 3;
    public static final int PATTERN_SUFFIX = 4;

    private final String mPattern;
    private final int mType;

    public PatternMatcher(String pattern, int type) {
        mPattern = pattern;
        mType = type;
    }

    public PatternMatcher(Parcel src) {
        mPattern = src.readString();
        mType = src.readInt();
    }

    public final String getPath() {
        return mPattern;
    }

    public final int getType() {
        return mType;
    }

    public boolean match(String str) {
        if (str == null) return false;
        switch (mType) {
            case PATTERN_LITERAL:
                return mPattern.equals(str);
            case PATTERN_PREFIX:
                return str.startsWith(mPattern);
            case PATTERN_SUFFIX:
                return str.endsWith(mPattern);
            case PATTERN_SIMPLE_GLOB:
                return matchGlob(mPattern, str);
            case PATTERN_ADVANCED_GLOB:
                try {
                    return str.matches(mPattern);
                } catch (RuntimeException e) {
                    return false;
                }
            default:
                return false;
        }
    }

    /** AOSP matchGlobPattern: '.' is any character, 'x*' repeats x, ".*" anything, '\\' escapes. */
    static boolean matchGlob(String pattern, String match) {
        final int NP = pattern.length();
        if (NP <= 0) return match.length() <= 0;
        final int NM = match.length();
        int ip = 0, im = 0;
        char nextChar = pattern.charAt(0);
        while ((ip < NP) && (im < NM)) {
            char c = nextChar;
            ip++;
            nextChar = ip < NP ? pattern.charAt(ip) : 0;
            final boolean escaped = (c == '\\');
            if (escaped) {
                c = nextChar;
                ip++;
                nextChar = ip < NP ? pattern.charAt(ip) : 0;
            }
            if (nextChar == '*') {
                if (!escaped && c == '.') {
                    if (ip >= (NP - 1)) return true;
                    ip++;
                    nextChar = pattern.charAt(ip);
                    if (nextChar == '\\') {
                        ip++;
                        nextChar = ip < NP ? pattern.charAt(ip) : 0;
                    }
                    do {
                        if (match.charAt(im) == nextChar) break;
                        im++;
                    } while (im < NM);
                    if (im == NM) return false;
                    ip++;
                    nextChar = ip < NP ? pattern.charAt(ip) : 0;
                    im++;
                } else {
                    do {
                        if (match.charAt(im) != c) break;
                        im++;
                    } while (im < NM);
                    ip++;
                    nextChar = ip < NP ? pattern.charAt(ip) : 0;
                }
            } else {
                if (c != '.' && match.charAt(im) != c) return false;
                im++;
            }
        }
        if (ip >= NP && im >= NM) return true;
        if (ip == NP - 2 && pattern.charAt(ip) == '.' && pattern.charAt(ip + 1) == '*') return true;
        return false;
    }

    public String toString() {
        String type = "? ";
        switch (mType) {
            case PATTERN_LITERAL: type = "LITERAL: "; break;
            case PATTERN_PREFIX: type = "PREFIX: "; break;
            case PATTERN_SIMPLE_GLOB: type = "GLOB: "; break;
            case PATTERN_ADVANCED_GLOB: type = "ADVANCED: "; break;
            case PATTERN_SUFFIX: type = "SUFFIX: "; break;
        }
        return "PatternMatcher{" + type + mPattern + "}";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPattern);
        dest.writeInt(mType);
    }

    public static final Parcelable.Creator<PatternMatcher> CREATOR = new Parcelable.Creator<PatternMatcher>() {
        public PatternMatcher createFromParcel(Parcel source) {
            return new PatternMatcher(source);
        }

        public PatternMatcher[] newArray(int size) {
            return new PatternMatcher[size];
        }
    };
}
