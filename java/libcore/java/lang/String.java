package java.lang;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Formatter;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Pattern;

public final class String implements java.io.Serializable, Comparable<String>, CharSequence {
    private final char[] value;
    private int hash;

    public static final Comparator<String> CASE_INSENSITIVE_ORDER = new Comparator<String>() {
        public int compare(String a, String b) {
            return a.compareToIgnoreCase(b);
        }
    };

    private static final char[] EMPTY = new char[0];

    public String() {
        value = EMPTY;
    }

    public String(String original) {
        value = original.value;
        hash = original.hash;
    }

    public String(char[] data) {
        this(data, 0, data.length);
    }

    public String(char[] data, int offset, int count) {
        if (offset < 0 || count < 0 || offset > data.length - count) {
            throw new StringIndexOutOfBoundsException("offset=" + offset + " count=" + count + " length=" + data.length);
        }
        value = new char[count];
        System.arraycopy(data, offset, value, 0, count);
    }

    public String(int[] codePoints, int offset, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.appendCodePoint(codePoints[offset + i]);
        }
        value = sb.toString().value;
    }

    public String(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    public String(byte[] bytes, int offset, int length) {
        value = Charset.decodeUtf8(bytes, offset, length);
    }

    public String(byte[] bytes, String charsetName) throws UnsupportedEncodingException {
        this(bytes, 0, bytes.length, charsetName);
    }

    public String(byte[] bytes, int offset, int length, String charsetName) throws UnsupportedEncodingException {
        Charset cs;
        try {
            cs = Charset.forName(charsetName);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedEncodingException(charsetName);
        }
        value = cs.decodeChars(bytes, offset, length);
    }

    public String(byte[] bytes, Charset charset) {
        this(bytes, 0, bytes.length, charset);
    }

    public String(byte[] bytes, int offset, int length, Charset charset) {
        value = charset.decodeChars(bytes, offset, length);
    }

    public String(byte[] ascii, int hibyte) {
        value = new char[ascii.length];
        for (int i = 0; i < ascii.length; i++) {
            value[i] = (char) (((hibyte & 0xff) << 8) | (ascii[i] & 0xff));
        }
    }

    public String(StringBuffer buffer) {
        this(buffer.toString());
    }

    public String(StringBuilder builder) {
        this(builder.toString());
    }

    /** Package-private: takes ownership of the array. */
    String(char[] value, boolean share) {
        this.value = value;
    }

    public int length() {
        return value.length;
    }

    public boolean isEmpty() {
        return value.length == 0;
    }

    public boolean isBlank() {
        for (char c : value) {
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    public char charAt(int index) {
        if (index < 0 || index >= value.length) {
            throw new StringIndexOutOfBoundsException("index=" + index + " length=" + value.length);
        }
        return value[index];
    }

    public int codePointAt(int index) {
        return Character.codePointAt(value, index);
    }

    public int codePointBefore(int index) {
        return Character.codePointBefore(value, index);
    }

    public int codePointCount(int begin, int end) {
        int n = 0;
        for (int i = begin; i < end; i++) {
            if (Character.isHighSurrogate(value[i]) && i + 1 < end && Character.isLowSurrogate(value[i + 1])) {
                i++;
            }
            n++;
        }
        return n;
    }

    public int offsetByCodePoints(int index, int codePointOffset) {
        int i = index;
        for (int k = 0; k < codePointOffset; k++) {
            if (Character.isHighSurrogate(value[i]) && i + 1 < value.length && Character.isLowSurrogate(value[i + 1])) {
                i += 2;
            } else {
                i++;
            }
        }
        return i;
    }

    public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        if (srcBegin < 0 || srcEnd > value.length || srcBegin > srcEnd) {
            throw new StringIndexOutOfBoundsException(srcBegin);
        }
        System.arraycopy(value, srcBegin, dst, dstBegin, srcEnd - srcBegin);
    }

    public byte[] getBytes() {
        return Charset.encodeUtf8(value, 0, value.length);
    }

    public byte[] getBytes(String charsetName) throws UnsupportedEncodingException {
        try {
            return Charset.forName(charsetName).encodeChars(value, 0, value.length);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedEncodingException(charsetName);
        }
    }

    public byte[] getBytes(Charset charset) {
        return charset.encodeChars(value, 0, value.length);
    }

    public void getBytes(int srcBegin, int srcEnd, byte[] dst, int dstBegin) {
        for (int i = srcBegin; i < srcEnd; i++) {
            dst[dstBegin++] = (byte) value[i];
        }
    }

    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof String)) {
            return false;
        }
        char[] o = ((String) anObject).value;
        if (o.length != value.length) {
            return false;
        }
        for (int i = 0; i < o.length; i++) {
            if (o[i] != value[i]) {
                return false;
            }
        }
        return true;
    }

    public boolean contentEquals(CharSequence cs) {
        if (cs.length() != value.length) {
            return false;
        }
        for (int i = 0; i < value.length; i++) {
            if (cs.charAt(i) != value[i]) {
                return false;
            }
        }
        return true;
    }

    public boolean contentEquals(StringBuffer sb) {
        return contentEquals((CharSequence) sb);
    }

    public boolean equalsIgnoreCase(String other) {
        if (other == this) {
            return true;
        }
        if (other == null || other.value.length != value.length) {
            return false;
        }
        return regionMatches(true, 0, other, 0, value.length);
    }

    public int compareTo(String other) {
        char[] a = value, b = other.value;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            if (a[i] != b[i]) {
                return a[i] - b[i];
            }
        }
        return a.length - b.length;
    }

    public int compareToIgnoreCase(String other) {
        char[] a = value, b = other.value;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            char c1 = a[i], c2 = b[i];
            if (c1 != c2) {
                c1 = Character.toUpperCase(c1);
                c2 = Character.toUpperCase(c2);
                if (c1 != c2) {
                    c1 = Character.toLowerCase(c1);
                    c2 = Character.toLowerCase(c2);
                    if (c1 != c2) {
                        return c1 - c2;
                    }
                }
            }
        }
        return a.length - b.length;
    }

    public boolean regionMatches(int toffset, String other, int ooffset, int len) {
        return regionMatches(false, toffset, other, ooffset, len);
    }

    public boolean regionMatches(boolean ignoreCase, int toffset, String other, int ooffset, int len) {
        if (toffset < 0 || ooffset < 0 || toffset > (long) value.length - len
                || ooffset > (long) other.value.length - len) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            char c1 = value[toffset + i], c2 = other.value[ooffset + i];
            if (c1 == c2) {
                continue;
            }
            if (ignoreCase) {
                char u1 = Character.toUpperCase(c1), u2 = Character.toUpperCase(c2);
                if (u1 == u2 || Character.toLowerCase(u1) == Character.toLowerCase(u2)) {
                    continue;
                }
            }
            return false;
        }
        return true;
    }

    public boolean startsWith(String prefix, int toffset) {
        return regionMatches(false, toffset, prefix, 0, prefix.value.length);
    }

    public boolean startsWith(String prefix) {
        return startsWith(prefix, 0);
    }

    public boolean endsWith(String suffix) {
        return startsWith(suffix, value.length - suffix.value.length);
    }

    public int hashCode() {
        int h = hash;
        if (h == 0 && value.length > 0) {
            for (char c : value) {
                h = 31 * h + c;
            }
            hash = h;
        }
        return h;
    }

    public int indexOf(int ch) {
        return indexOf(ch, 0);
    }

    public int indexOf(int ch, int fromIndex) {
        if (fromIndex < 0) {
            fromIndex = 0;
        }
        if (ch < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            for (int i = fromIndex; i < value.length; i++) {
                if (value[i] == ch) {
                    return i;
                }
            }
            return -1;
        }
        return indexOf(new String(Character.toChars(ch), true), fromIndex);
    }

    public int lastIndexOf(int ch) {
        return lastIndexOf(ch, value.length - 1);
    }

    public int lastIndexOf(int ch, int fromIndex) {
        if (fromIndex >= value.length) {
            fromIndex = value.length - 1;
        }
        for (int i = fromIndex; i >= 0; i--) {
            if (value[i] == ch) {
                return i;
            }
        }
        return -1;
    }

    public int indexOf(String str) {
        return indexOf(str, 0);
    }

    public int indexOf(String str, int fromIndex) {
        char[] s = str.value;
        if (fromIndex < 0) {
            fromIndex = 0;
        }
        if (s.length == 0) {
            return fromIndex <= value.length ? fromIndex : value.length;
        }
        outer:
        for (int i = fromIndex; i <= value.length - s.length; i++) {
            for (int j = 0; j < s.length; j++) {
                if (value[i + j] != s[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public int lastIndexOf(String str) {
        return lastIndexOf(str, value.length);
    }

    public int lastIndexOf(String str, int fromIndex) {
        char[] s = str.value;
        int start = Math.min(fromIndex, value.length - s.length);
        outer:
        for (int i = start; i >= 0; i--) {
            for (int j = 0; j < s.length; j++) {
                if (value[i + j] != s[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public String substring(int beginIndex) {
        return substring(beginIndex, value.length);
    }

    public String substring(int beginIndex, int endIndex) {
        if (beginIndex < 0 || endIndex > value.length || beginIndex > endIndex) {
            throw new StringIndexOutOfBoundsException("begin " + beginIndex + ", end " + endIndex + ", length " + value.length);
        }
        if (beginIndex == 0 && endIndex == value.length) {
            return this;
        }
        char[] r = new char[endIndex - beginIndex];
        System.arraycopy(value, beginIndex, r, 0, r.length);
        return new String(r, true);
    }

    public CharSequence subSequence(int beginIndex, int endIndex) {
        return substring(beginIndex, endIndex);
    }

    public String concat(String str) {
        if (str.value.length == 0) {
            return this;
        }
        char[] r = new char[value.length + str.value.length];
        System.arraycopy(value, 0, r, 0, value.length);
        System.arraycopy(str.value, 0, r, value.length, str.value.length);
        return new String(r, true);
    }

    public String replace(char oldChar, char newChar) {
        if (oldChar == newChar) {
            return this;
        }
        int i = indexOf(oldChar);
        if (i < 0) {
            return this;
        }
        char[] r = value.clone();
        for (; i < r.length; i++) {
            if (r[i] == oldChar) {
                r[i] = newChar;
            }
        }
        return new String(r, true);
    }

    public String replace(CharSequence target, CharSequence replacement) {
        String t = target.toString(), rep = replacement.toString();
        if (t.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(rep);
            for (char c : value) {
                sb.append(c).append(rep);
            }
            return sb.toString();
        }
        int idx = indexOf(t);
        if (idx < 0) {
            return this;
        }
        StringBuilder sb = new StringBuilder(value.length);
        int last = 0;
        while (idx >= 0) {
            sb.append(value, last, idx - last).append(rep);
            last = idx + t.length();
            idx = indexOf(t, last);
        }
        sb.append(value, last, value.length - last);
        return sb.toString();
    }

    public boolean matches(String regex) {
        return Pattern.matches(regex, this);
    }

    public boolean contains(CharSequence s) {
        return indexOf(s.toString()) >= 0;
    }

    public String replaceFirst(String regex, String replacement) {
        return Pattern.compile(regex).matcher(this).replaceFirst(replacement);
    }

    public String replaceAll(String regex, String replacement) {
        return Pattern.compile(regex).matcher(this).replaceAll(replacement);
    }

    private static boolean isRegexMeta(char c) {
        return ".$|()[{^?*+\\".indexOf(c) >= 0;
    }

    public String[] split(String regex, int limit) {
        // fast path for single literal characters (the common case)
        char ch = 0;
        if ((regex.value.length == 1 && !isRegexMeta(ch = regex.charAt(0)))
                || (regex.length() == 2 && regex.charAt(0) == '\\' && !Character.isLetterOrDigit(ch = regex.charAt(1)))) {
            ArrayList<String> list = new ArrayList<String>();
            int off = 0, next;
            boolean limited = limit > 0;
            while ((next = indexOf(ch, off)) != -1) {
                if (!limited || list.size() < limit - 1) {
                    list.add(substring(off, next));
                    off = next + 1;
                } else {
                    break;
                }
            }
            if (off == 0) {
                return new String[] {this};
            }
            list.add(substring(off, value.length));
            int size = list.size();
            if (limit == 0) {
                while (size > 0 && list.get(size - 1).isEmpty()) {
                    size--;
                }
            }
            String[] result = new String[size];
            for (int i = 0; i < size; i++) {
                result[i] = list.get(i);
            }
            return result;
        }
        return Pattern.compile(regex).split(this, limit);
    }

    public String[] split(String regex) {
        return split(regex, 0);
    }

    public static String join(CharSequence delimiter, CharSequence... elements) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(elements[i]);
        }
        return sb.toString();
    }

    public static String join(CharSequence delimiter, Iterable<? extends CharSequence> elements) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (CharSequence cs : elements) {
            if (!first) {
                sb.append(delimiter);
            }
            first = false;
            sb.append(cs);
        }
        return sb.toString();
    }

    public String toLowerCase(Locale locale) {
        return toLowerCase();
    }

    public String toLowerCase() {
        for (int i = 0; i < value.length; i++) {
            char c = value[i];
            if (Character.toLowerCase(c) != c) {
                char[] r = value.clone();
                for (int j = i; j < r.length; j++) {
                    r[j] = Character.toLowerCase(r[j]);
                }
                return new String(r, true);
            }
        }
        return this;
    }

    public String toUpperCase(Locale locale) {
        return toUpperCase();
    }

    public String toUpperCase() {
        for (int i = 0; i < value.length; i++) {
            char c = value[i];
            if (Character.toUpperCase(c) != c) {
                char[] r = value.clone();
                for (int j = i; j < r.length; j++) {
                    r[j] = Character.toUpperCase(r[j]);
                }
                return new String(r, true);
            }
        }
        return this;
    }

    public String trim() {
        int st = 0, len = value.length;
        while (st < len && value[st] <= ' ') {
            st++;
        }
        while (st < len && value[len - 1] <= ' ') {
            len--;
        }
        return (st > 0 || len < value.length) ? substring(st, len) : this;
    }

    public String strip() {
        int st = 0, len = value.length;
        while (st < len && Character.isWhitespace(value[st])) {
            st++;
        }
        while (st < len && Character.isWhitespace(value[len - 1])) {
            len--;
        }
        return substring(st, len);
    }

    public String stripLeading() {
        int st = 0;
        while (st < value.length && Character.isWhitespace(value[st])) {
            st++;
        }
        return substring(st);
    }

    public String stripTrailing() {
        int len = value.length;
        while (len > 0 && Character.isWhitespace(value[len - 1])) {
            len--;
        }
        return substring(0, len);
    }

    public String repeat(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count is negative: " + count);
        }
        StringBuilder sb = new StringBuilder(value.length * count);
        for (int i = 0; i < count; i++) {
            sb.append(this);
        }
        return sb.toString();
    }

    public String toString() {
        return this;
    }

    public char[] toCharArray() {
        return value.clone();
    }

    public static String format(String format, Object... args) {
        return new Formatter().format(format, args).toString();
    }

    public String formatted(Object... args) {
        return new Formatter().format(this, args).toString();
    }

    public static String format(Locale l, String format, Object... args) {
        return new Formatter(l).format(format, args).toString();
    }

    public static String valueOf(Object obj) {
        return obj == null ? "null" : obj.toString();
    }

    public static String valueOf(char[] data) {
        return new String(data);
    }

    public static String valueOf(char[] data, int offset, int count) {
        return new String(data, offset, count);
    }

    public static String copyValueOf(char[] data, int offset, int count) {
        return new String(data, offset, count);
    }

    public static String copyValueOf(char[] data) {
        return new String(data);
    }

    public static String valueOf(boolean b) {
        return b ? "true" : "false";
    }

    public static String valueOf(char c) {
        return new String(new char[] {c}, true);
    }

    public static String valueOf(int i) {
        return Integer.toString(i);
    }

    public static String valueOf(long l) {
        return Long.toString(l);
    }

    public static String valueOf(float f) {
        return Float.toString(f);
    }

    public static String valueOf(double d) {
        return Double.toString(d);
    }

    public native String intern();

    public java.util.stream.IntStream chars() {
        int[] a = new int[value.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = value[i];
        }
        return java.util.stream.IntStream.of(a);
    }

    public java.util.stream.Stream<String> lines() {
        return java.util.Arrays.stream(split("\r?\n"));
    }
}
