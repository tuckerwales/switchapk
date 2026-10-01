package android.text;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Printer;
import android.view.View;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Port of AOSP android.text.TextUtils (bidi treated as LTR). */
public class TextUtils {
    public static final int CAP_MODE_CHARACTERS = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
    public static final int CAP_MODE_WORDS = InputType.TYPE_TEXT_FLAG_CAP_WORDS;
    public static final int CAP_MODE_SENTENCES = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    public static final int SAFE_STRING_FLAG_TRIM = 0x1;
    public static final int SAFE_STRING_FLAG_SINGLE_LINE = 0x2;
    public static final int SAFE_STRING_FLAG_FIRST_LINE = 0x4;

    // Hidden AOSP span type ids (ParcelableSpan.getSpanTypeId).
    public static final int ALIGNMENT_SPAN = 1;
    public static final int FIRST_SPAN = ALIGNMENT_SPAN;
    public static final int FOREGROUND_COLOR_SPAN = 2;
    public static final int RELATIVE_SIZE_SPAN = 3;
    public static final int SCALE_X_SPAN = 4;
    public static final int STRIKETHROUGH_SPAN = 5;
    public static final int UNDERLINE_SPAN = 6;
    public static final int STYLE_SPAN = 7;
    public static final int BULLET_SPAN = 8;
    public static final int QUOTE_SPAN = 9;
    public static final int LEADING_MARGIN_SPAN = 10;
    public static final int URL_SPAN = 11;
    public static final int BACKGROUND_COLOR_SPAN = 12;
    public static final int TYPEFACE_SPAN = 13;
    public static final int SUPERSCRIPT_SPAN = 14;
    public static final int SUBSCRIPT_SPAN = 15;
    public static final int ABSOLUTE_SIZE_SPAN = 16;
    public static final int TEXT_APPEARANCE_SPAN = 17;
    public static final int ANNOTATION = 18;
    public static final int SUGGESTION_SPAN = 19;
    public static final int SPELL_CHECK_SPAN = 20;
    public static final int SUGGESTION_RANGE_SPAN = 21;
    public static final int EASY_EDIT_SPAN = 22;
    public static final int LOCALE_SPAN = 23;
    public static final int TTS_SPAN = 24;
    public static final int ACCESSIBILITY_CLICKABLE_SPAN = 25;
    public static final int ACCESSIBILITY_URL_SPAN = 26;
    public static final int LINE_BACKGROUND_SPAN = 27;
    public static final int LINE_HEIGHT_SPAN = 28;
    public static final int ACCESSIBILITY_REPLACEMENT_SPAN = 29;
    public static final int LAST_SPAN = ACCESSIBILITY_REPLACEMENT_SPAN;

    private static final String ELLIPSIS_NORMAL = "…";
    private static final String ELLIPSIS_TWO_DOTS = "‥";

    /** Parcel here stores object references, so the sequence round trips as is. */
    public static final Parcelable.Creator<CharSequence> CHAR_SEQUENCE_CREATOR = new Parcelable.Creator<CharSequence>() {
        public CharSequence createFromParcel(Parcel p) { return p.readCharSequence(); }
        public CharSequence[] newArray(int size) { return new CharSequence[size]; }
    };

    TextUtils() {}

    public enum TruncateAt { START, MIDDLE, END, MARQUEE, END_SMALL }

    public interface EllipsizeCallback {
        void ellipsized(int start, int end);
    }

    public interface StringSplitter extends Iterable<String> {
        void setString(String string);
    }

    public static class SimpleStringSplitter implements StringSplitter, Iterator<String> {
        private String mString;
        private final char mDelimiter;
        private int mPosition;
        private int mLength;

        public SimpleStringSplitter(char delimiter) { mDelimiter = delimiter; }

        public void setString(String string) {
            mString = string;
            mPosition = 0;
            mLength = mString.length();
        }

        public Iterator<String> iterator() { return this; }

        public boolean hasNext() { return mPosition < mLength; }

        public String next() {
            int end = mString.indexOf(mDelimiter, mPosition);
            if (end == -1) end = mLength;
            String nextString = mString.substring(mPosition, end);
            mPosition = end + 1;
            return nextString;
        }

        public void remove() { throw new UnsupportedOperationException(); }
    }

    public static void getChars(CharSequence s, int start, int end, char[] dest, int destoff) {
        if (s instanceof String) {
            ((String) s).getChars(start, end, dest, destoff);
        } else if (s instanceof StringBuffer) {
            ((StringBuffer) s).getChars(start, end, dest, destoff);
        } else if (s instanceof StringBuilder) {
            ((StringBuilder) s).getChars(start, end, dest, destoff);
        } else if (s instanceof GetChars) {
            ((GetChars) s).getChars(start, end, dest, destoff);
        } else {
            for (int i = start; i < end; i++) dest[destoff++] = s.charAt(i);
        }
    }

    public static int indexOf(CharSequence s, char ch) { return indexOf(s, ch, 0); }

    public static int indexOf(CharSequence s, char ch, int start) {
        if (s instanceof String) return ((String) s).indexOf(ch, start);
        return indexOf(s, ch, start, s.length());
    }

    public static int indexOf(CharSequence s, char ch, int start, int end) {
        if (start < 0) start = 0;
        for (int i = start; i < end; i++) if (s.charAt(i) == ch) return i;
        return -1;
    }

    public static int lastIndexOf(CharSequence s, char ch) { return lastIndexOf(s, ch, s.length() - 1); }

    public static int lastIndexOf(CharSequence s, char ch, int last) {
        if (s instanceof String) return ((String) s).lastIndexOf(ch, last);
        return lastIndexOf(s, ch, 0, last);
    }

    public static int lastIndexOf(CharSequence s, char ch, int start, int last) {
        if (last < 0) return -1;
        if (last >= s.length()) last = s.length() - 1;
        for (int i = last; i >= start; i--) if (s.charAt(i) == ch) return i;
        return -1;
    }

    public static int indexOf(CharSequence s, CharSequence needle) { return indexOf(s, needle, 0, s.length()); }

    public static int indexOf(CharSequence s, CharSequence needle, int start) { return indexOf(s, needle, start, s.length()); }

    public static int indexOf(CharSequence s, CharSequence needle, int start, int end) {
        int nlen = needle.length();
        if (nlen == 0) return start;
        char c = needle.charAt(0);
        for (;;) {
            start = indexOf(s, c, start);
            if (start > end - nlen) break;
            if (start < 0) return -1;
            if (regionMatches(s, start, needle, 0, nlen)) return start;
            start++;
        }
        return -1;
    }

    public static boolean regionMatches(CharSequence one, int toffset, CharSequence two, int ooffset, int len) {
        if (toffset < 0 || ooffset < 0 || toffset + len > one.length() || ooffset + len > two.length()) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = 0; i < len; i++) {
            if (one.charAt(toffset + i) != two.charAt(ooffset + i)) return false;
        }
        return true;
    }

    public static String substring(CharSequence source, int start, int end) {
        if (source instanceof String) return ((String) source).substring(start, end);
        if (source instanceof StringBuilder) return ((StringBuilder) source).substring(start, end);
        if (source instanceof StringBuffer) return ((StringBuffer) source).substring(start, end);
        char[] buf = new char[end - start];
        getChars(source, start, end, buf, 0);
        return new String(buf);
    }

    public static String join(CharSequence delimiter, Object[] tokens) {
        final int length = tokens.length;
        if (length == 0) return "";
        final StringBuilder sb = new StringBuilder();
        sb.append(tokens[0]);
        for (int i = 1; i < length; i++) {
            sb.append(delimiter);
            sb.append(tokens[i]);
        }
        return sb.toString();
    }

    public static String join(CharSequence delimiter, Iterable tokens) {
        final Iterator<?> it = tokens.iterator();
        if (!it.hasNext()) return "";
        final StringBuilder sb = new StringBuilder();
        sb.append(it.next());
        while (it.hasNext()) {
            sb.append(delimiter);
            sb.append(it.next());
        }
        return sb.toString();
    }

    private static final String[] EMPTY_STRING_ARRAY = new String[] {};

    public static String[] split(String text, String expression) {
        if (text.length() == 0) return EMPTY_STRING_ARRAY;
        return text.split(expression, -1);
    }

    public static String[] split(String text, Pattern pattern) {
        if (text.length() == 0) return EMPTY_STRING_ARRAY;
        return pattern.split(text, -1);
    }

    public static CharSequence stringOrSpannedString(CharSequence source) {
        if (source == null) return null;
        if (source instanceof SpannedString) return source;
        if (source instanceof Spanned) return new SpannedString(source);
        return source.toString();
    }

    /** Null is empty, matching android.text.TextUtils. */
    public static boolean isEmpty(CharSequence str) { return str == null || str.length() == 0; }

    public static int getTrimmedLength(CharSequence s) {
        int len = s.length();
        int start = 0;
        while (start < len && s.charAt(start) <= ' ') start++;
        int end = len;
        while (end > start && s.charAt(end - 1) <= ' ') end--;
        return end - start;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        int length;
        if (a != null && b != null && (length = a.length()) == b.length()) {
            if (a instanceof String && b instanceof String) return a.equals(b);
            for (int i = 0; i < length; i++) if (a.charAt(i) != b.charAt(i)) return false;
            return true;
        }
        return false;
    }

    @Deprecated
    public static CharSequence getReverse(CharSequence source, int start, int end) {
        char[] buf = new char[end - start];
        getChars(source, start, end, buf, 0);
        for (int i = 0, j = buf.length - 1; i < j; i++, j--) {
            char t = buf[i];
            buf[i] = buf[j];
            buf[j] = t;
        }
        return new String(buf);
    }

    public static void writeToParcel(CharSequence cs, Parcel p, int parcelableFlags) { p.writeCharSequence(cs); }

    public static void dumpSpans(CharSequence cs, Printer printer, String prefix) {
        if (cs instanceof Spanned) {
            Spanned sp = (Spanned) cs;
            Object[] os = sp.getSpans(0, cs.length(), Object.class);
            for (int i = 0; i < os.length; i++) {
                Object o = os[i];
                printer.println(prefix + cs.subSequence(sp.getSpanStart(o), sp.getSpanEnd(o)) + ": "
                        + Integer.toHexString(System.identityHashCode(o)) + " " + o.getClass().getCanonicalName()
                        + " (" + sp.getSpanStart(o) + "-" + sp.getSpanEnd(o) + ") fl=#" + sp.getSpanFlags(o));
            }
        } else {
            printer.println(prefix + cs + ": (no spans)");
        }
    }

    public static CharSequence replace(CharSequence template, String[] sources, CharSequence[] destinations) {
        SpannableStringBuilder tb = new SpannableStringBuilder(template);
        for (int i = 0; i < sources.length; i++) {
            int where = indexOf(tb, sources[i]);
            if (where >= 0) tb.setSpan(sources[i], where, where + sources[i].length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        for (int i = 0; i < sources.length; i++) {
            int start = tb.getSpanStart(sources[i]);
            int end = tb.getSpanEnd(sources[i]);
            if (start >= 0) tb.replace(start, end, destinations[i]);
        }
        return tb;
    }

    public static CharSequence expandTemplate(CharSequence template, CharSequence... values) {
        if (values.length > 9) throw new IllegalArgumentException("max of 9 values are supported");
        SpannableStringBuilder ssb = new SpannableStringBuilder(template);
        try {
            int i = 0;
            while (i < ssb.length()) {
                if (ssb.charAt(i) == '^') {
                    char next = ssb.charAt(i + 1);
                    if (next == '^') {
                        ssb.delete(i + 1, i + 2);
                        ++i;
                        continue;
                    } else if (Character.isDigit(next)) {
                        int which = Character.getNumericValue(next) - 1;
                        if (which < 0) throw new IllegalArgumentException("template requests value ^" + (which + 1));
                        if (which >= values.length) {
                            throw new IllegalArgumentException("template requests value ^" + (which + 1) + "; only "
                                    + values.length + " provided");
                        }
                        ssb.replace(i, i + 2, values[which]);
                        i += values[which].length();
                        continue;
                    }
                }
                ++i;
            }
        } catch (IndexOutOfBoundsException ignore) {
        }
        return ssb;
    }

    public static int getOffsetBefore(CharSequence text, int offset) {
        if (offset == 0) return 0;
        if (offset == 1) return 0;
        char c = text.charAt(offset - 1);
        if (c >= '\uDC00' && c <= '\uDFFF') {
            char c1 = text.charAt(offset - 2);
            if (c1 >= '\uD800' && c1 <= '\uDBFF') offset -= 2;
            else offset -= 1;
        } else {
            offset -= 1;
        }
        if (text instanceof Spanned) {
            android.text.style.ReplacementSpan[] spans = ((Spanned) text).getSpans(offset, offset,
                    android.text.style.ReplacementSpan.class);
            for (int i = 0; i < spans.length; i++) {
                int start = ((Spanned) text).getSpanStart(spans[i]);
                int end = ((Spanned) text).getSpanEnd(spans[i]);
                if (start < offset && end > offset) offset = start;
            }
        }
        return offset;
    }

    public static int getOffsetAfter(CharSequence text, int offset) {
        int len = text.length();
        if (offset == len) return len;
        if (offset == len - 1) return len;
        char c = text.charAt(offset);
        if (c >= '\uD800' && c <= '\uDBFF') {
            char c1 = text.charAt(offset + 1);
            if (c1 >= '\uDC00' && c1 <= '\uDFFF') offset += 2;
            else offset += 1;
        } else {
            offset += 1;
        }
        if (text instanceof Spanned) {
            android.text.style.ReplacementSpan[] spans = ((Spanned) text).getSpans(offset, offset,
                    android.text.style.ReplacementSpan.class);
            for (int i = 0; i < spans.length; i++) {
                int start = ((Spanned) text).getSpanStart(spans[i]);
                int end = ((Spanned) text).getSpanEnd(spans[i]);
                if (start < offset && end > offset) offset = end;
            }
        }
        return offset;
    }

    public static void copySpansFrom(Spanned source, int start, int end, Class kind, Spannable dest, int destoff) {
        if (kind == null) kind = Object.class;
        Object[] spans = source.getSpans(start, end, kind);
        for (int i = 0; i < spans.length; i++) {
            int st = source.getSpanStart(spans[i]);
            int en = source.getSpanEnd(spans[i]);
            int fl = source.getSpanFlags(spans[i]);
            if (st < start) st = start;
            if (en > end) en = end;
            dest.setSpan(spans[i], st - start + destoff, en - start + destoff, fl);
        }
    }

    public static CharSequence ellipsize(CharSequence text, TextPaint p, float avail, TruncateAt where) {
        return ellipsize(text, p, avail, where, false, null);
    }

    public static CharSequence ellipsize(CharSequence text, TextPaint paint, float avail, TruncateAt where,
            boolean preserveLength, EllipsizeCallback callback) {
        final String ellipsis = where == TruncateAt.END_SMALL ? ELLIPSIS_TWO_DOTS : ELLIPSIS_NORMAL;
        final int len = text.length();
        float width = paint.measureText(text, 0, len);
        if (width <= avail) {
            if (callback != null) callback.ellipsized(0, 0);
            return text;
        }
        float ellipsisWidth = paint.measureText(ellipsis);
        avail -= ellipsisWidth;
        int left = 0;
        int right = len;
        if (avail >= 0) {
            float[] widths = new float[len];
            paint.getTextWidths(text, 0, len, widths);
            if (where == TruncateAt.START) {
                float w = 0;
                right = len;
                while (right > 0 && w + widths[right - 1] <= avail) w += widths[--right];
                if (right > 0 && right < len && Character.isLowSurrogate(text.charAt(right))) right++;
            } else if (where == TruncateAt.END || where == TruncateAt.MARQUEE || where == TruncateAt.END_SMALL) {
                float w = 0;
                left = 0;
                while (left < len && w + widths[left] <= avail) w += widths[left++];
                if (left > 0 && left < len && Character.isLowSurrogate(text.charAt(left))) left--;
            } else {
                float half = avail / 2;
                float w = 0;
                right = len;
                while (right > 0 && w + widths[right - 1] <= half) w += widths[--right];
                float rem = avail - w;
                w = 0;
                while (left < right && w + widths[left] <= rem) w += widths[left++];
                if (left > 0 && left < len && Character.isLowSurrogate(text.charAt(left))) left--;
                if (right > 0 && right < len && Character.isLowSurrogate(text.charAt(right))) right++;
            }
        } else {
            left = 0;
            right = len;
        }
        if (where == TruncateAt.START) left = 0;
        else if (where != TruncateAt.MIDDLE) right = len;

        if (callback != null) callback.ellipsized(left, right);

        if (preserveLength) {
            char[] buf = new char[len];
            getChars(text, 0, len, buf, 0);
            if (left < right) {
                buf[left] = ellipsis.charAt(0);
                for (int i = left + 1; i < right; i++) buf[i] = '﻿';
            }
            String s = new String(buf);
            if (text instanceof Spanned) {
                SpannableString ss = new SpannableString(s);
                copySpansFrom((Spanned) text, 0, len, Object.class, ss, 0);
                return ss;
            }
            return s;
        }
        if (right - left == len) return "";
        if (text instanceof Spanned) {
            SpannableStringBuilder ssb = new SpannableStringBuilder();
            ssb.append(text, 0, left);
            ssb.append(ellipsis);
            ssb.append(text, right, len);
            return ssb;
        }
        StringBuilder sb = new StringBuilder(len - (right - left) + 1);
        sb.append(text, 0, left);
        sb.append(ellipsis);
        sb.append(text, right, len);
        return sb.toString();
    }

    public static CharSequence listEllipsize(Context context, List<CharSequence> elements, String separator,
            TextPaint paint, float avail, int moreId) {
        if (elements == null) return "";
        final int totalLen = elements.size();
        if (totalLen == 0) return "";
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < totalLen; i++) {
            if (i > 0) output.append(separator);
            output.append(elements.get(i));
        }
        if (paint.measureText(output, 0, output.length()) <= avail) return output.toString();
        for (int i = totalLen - 1; i > 0; i--) {
            output.setLength(0);
            for (int j = 0; j < i; j++) {
                if (j > 0) output.append(separator);
                output.append(elements.get(j));
            }
            String more;
            if (context != null && moreId != 0) {
                more = context.getResources().getQuantityString(moreId, totalLen - i, totalLen - i);
            } else {
                more = "+" + (totalLen - i);
            }
            output.append(separator).append(more);
            if (paint.measureText(output, 0, output.length()) <= avail) return output.toString();
        }
        return "";
    }

    @Deprecated
    public static CharSequence commaEllipsize(CharSequence text, TextPaint p, float avail, String oneMore, String more) {
        int len = text.length();
        if (p.measureText(text, 0, len) <= avail) return text;
        int commaCount = 0;
        for (int i = 0; i < len; i++) if (text.charAt(i) == ',') commaCount++;
        int count = commaCount + 1;
        String out = "";
        int remaining = count;
        int pos = 0;
        for (int i = 0; i < len; i++) {
            if (text.charAt(i) == ',') {
                remaining--;
                String format = remaining == 1 ? " " + oneMore : " " + String.format(more, remaining);
                CharSequence head = text.subSequence(0, i + 1);
                if (p.measureText(head, 0, head.length()) + p.measureText(format) <= avail) {
                    out = head + format;
                    pos = i;
                }
            }
        }
        return out;
    }

    public static String htmlEncode(String s) {
        StringBuilder sb = new StringBuilder();
        char c;
        for (int i = 0; i < s.length(); i++) {
            c = s.charAt(i);
            switch (c) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '\'': sb.append("&#39;"); break;
                case '"': sb.append("&quot;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    public static CharSequence concat(CharSequence... text) {
        if (text.length == 0) return "";
        if (text.length == 1) return text[0];
        boolean spanned = false;
        for (CharSequence piece : text) {
            if (piece instanceof Spanned) {
                spanned = true;
                break;
            }
        }
        if (spanned) {
            final SpannableStringBuilder ssb = new SpannableStringBuilder();
            for (CharSequence piece : text) ssb.append(piece == null ? "null" : piece);
            return new SpannedString(ssb);
        }
        final StringBuilder sb = new StringBuilder();
        for (CharSequence piece : text) sb.append(piece);
        return sb.toString();
    }

    public static boolean isGraphic(CharSequence str) {
        final int len = str.length();
        for (int cp, i = 0; i < len; i += Character.charCount(cp)) {
            cp = Character.codePointAt(str, i);
            int gc = Character.getType(cp);
            if (gc != Character.CONTROL && gc != Character.FORMAT && gc != Character.SURROGATE
                    && gc != Character.UNASSIGNED && gc != Character.LINE_SEPARATOR
                    && gc != Character.PARAGRAPH_SEPARATOR && gc != Character.SPACE_SEPARATOR) {
                return true;
            }
        }
        return false;
    }

    @Deprecated
    public static boolean isGraphic(char c) {
        int gc = Character.getType(c);
        return gc != Character.CONTROL && gc != Character.FORMAT && gc != Character.SURROGATE
                && gc != Character.UNASSIGNED && gc != Character.LINE_SEPARATOR && gc != Character.PARAGRAPH_SEPARATOR
                && gc != Character.SPACE_SEPARATOR;
    }

    public static boolean isDigitsOnly(CharSequence str) {
        final int len = str.length();
        for (int cp, i = 0; i < len; i += Character.charCount(cp)) {
            cp = Character.codePointAt(str, i);
            if (!Character.isDigit(cp)) return false;
        }
        return true;
    }

    public static int getCapsMode(CharSequence cs, int off, int reqModes) {
        if (off < 0) return 0;
        int i;
        char c;
        int mode = 0;
        if ((reqModes & CAP_MODE_CHARACTERS) != 0) mode |= CAP_MODE_CHARACTERS;
        if ((reqModes & (CAP_MODE_WORDS | CAP_MODE_SENTENCES)) == 0) return mode;

        for (i = off; i > 0; i--) {
            c = cs.charAt(i - 1);
            if (c != '"' && c != '\'' && Character.getType(c) != Character.START_PUNCTUATION) break;
        }
        int j = i;
        while (j > 0 && ((c = cs.charAt(j - 1)) == ' ' || c == '\t')) j--;
        if (j == 0 || cs.charAt(j - 1) == '\n') return mode | CAP_MODE_WORDS | CAP_MODE_SENTENCES;
        if ((reqModes & CAP_MODE_SENTENCES) == 0) {
            if (i != j) mode |= CAP_MODE_WORDS;
            return mode;
        }
        if (i == j) return mode;
        for (; j > 0; j--) {
            c = cs.charAt(j - 1);
            if (c != '"' && c != '\'' && Character.getType(c) != Character.END_PUNCTUATION) break;
        }
        if (j > 0) {
            c = cs.charAt(j - 1);
            if (c == '.' || c == '?' || c == '!') {
                if (c == '.') {
                    for (int k = j - 2; k >= 0; k--) {
                        c = cs.charAt(k);
                        if (c == '.') return mode;
                        if (!Character.isLetter(c)) break;
                    }
                }
                return mode | CAP_MODE_SENTENCES;
            }
        }
        return mode;
    }

    public static int getLayoutDirectionFromLocale(Locale locale) {
        if (locale != null && !locale.equals(Locale.ROOT)) {
            String lang = locale.getLanguage();
            if ("ar".equals(lang) || "fa".equals(lang) || "he".equals(lang) || "iw".equals(lang) || "ur".equals(lang)
                    || "yi".equals(lang) || "ji".equals(lang) || "ps".equals(lang) || "sd".equals(lang)
                    || "ug".equals(lang) || "dv".equals(lang) || "ckb".equals(lang)) {
                return View.LAYOUT_DIRECTION_RTL;
            }
        }
        return View.LAYOUT_DIRECTION_LTR;
    }

    public static CharSequence makeSafeForPresentation(String unclean, int maxCharactersToConsider,
            float ellipsizeDip, int flags) {
        String s = unclean;
        if (maxCharactersToConsider > 0 && s.length() > maxCharactersToConsider) {
            s = s.substring(0, maxCharactersToConsider);
        }
        if ((flags & SAFE_STRING_FLAG_FIRST_LINE) != 0) {
            int nl = s.indexOf('\n');
            if (nl >= 0) s = s.substring(0, nl);
        }
        if ((flags & SAFE_STRING_FLAG_SINGLE_LINE) != 0) s = s.replace('\n', ' ');
        if ((flags & SAFE_STRING_FLAG_TRIM) != 0) s = s.trim();
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || !Character.isISOControl(c)) sb.append(c);
        }
        if (ellipsizeDip > 0) {
            TextPaint paint = new TextPaint();
            paint.setTextSize(42);
            return ellipsize(sb.toString(), paint, ellipsizeDip * 42 / 14f, TruncateAt.END);
        }
        return sb.toString();
    }

    /** Hidden AOSP helper used by layouts: unpacks a range into a pooled char buffer. */
    static char[] obtain(int len) { return new char[len]; }

    static void recycle(char[] temp) {}

    /** Hidden AOSP helper: whether a char is a line or paragraph break for layout purposes. */
    static boolean couldAffectRtl(char c) { return false; }
}
