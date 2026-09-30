package android.text;

public final class TextUtils {
    private TextUtils() {}

    public static void getChars(CharSequence s, int start, int end, char[] dest, int destoff) {
        if (s instanceof String) {
            ((String) s).getChars(start, end, dest, destoff);
        } else if (s instanceof StringBuffer) {
            ((StringBuffer) s).getChars(start, end, dest, destoff);
        } else if (s instanceof StringBuilder) {
            ((StringBuilder) s).getChars(start, end, dest, destoff);
        } else {
            for (int i = start; i < end; i++) dest[destoff++] = s.charAt(i);
        }
    }

    public static String substring(CharSequence source, int start, int end) {
        if (source instanceof String) return ((String) source).substring(start, end);
        char[] buf = new char[end - start];
        getChars(source, start, end, buf, 0);
        return new String(buf);
    }

    public static String join(CharSequence delimiter, Object[] tokens) {
        if (tokens == null || tokens.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(tokens[i]);
        }
        return sb.toString();
    }

    public static String join(CharSequence delimiter, Iterable<?> tokens) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object token : tokens) {
            if (!first) sb.append(delimiter);
            first = false;
            sb.append(token);
        }
        return sb.toString();
    }

    /** Null is empty, matching android.text.TextUtils. */
    public static boolean isEmpty(CharSequence s) { return s == null || s.length() == 0; }

    public static int getTrimmedLength(CharSequence s) {
        int len = s.length();
        int start = 0;
        while (start < len && s.charAt(start) <= ' ') start++;
        while (len > start && s.charAt(len - 1) <= ' ') len--;
        return len - start;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a == null || b == null || a.length() != b.length()) return false;
        if (a instanceof String && b instanceof String) return a.equals(b);
        for (int i = 0; i < a.length(); i++) if (a.charAt(i) != b.charAt(i)) return false;
        return true;
    }
}
