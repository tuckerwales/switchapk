package android.text.format;

import android.content.Context;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Format strings and locale date helpers (AOSP android.text.format.DateFormat).
 * There is no system 12/24-hour setting, so {@link #is24HourFormat} is false
 * and the default hour is 12-hour.
 */
public class DateFormat {
    public DateFormat() {}

    public static boolean is24HourFormat(Context context) {
        return false;
    }

    /**
     * Skeleton to pattern for the en-US locale (what ICU returns there). Fields
     * are matched order-insensitively; skeletons outside the table fall back to
     * the skeleton itself. {@code j} (locale hour) is 12-hour because the
     * default is 12-hour, and an {@code a} is added when the hour needs it.
     */
    public static String getBestDateTimePattern(Locale locale, String skeleton) {
        if (skeleton == null) return "";
        String s = skeleton.replace("j", "h");
        String best = bestPattern(canonicalSkeleton(s));
        if (best != null) return best;
        boolean hour12 = s.indexOf('h') >= 0 || s.indexOf('K') >= 0;
        boolean hour24 = s.indexOf('H') >= 0 || s.indexOf('k') >= 0;
        if (hour12 && !hour24 && s.indexOf('a') < 0) s = s + " a";
        return s;
    }

    private static final String SKELETON_ORDER = "GyYQMLwWEcdDFahHkKmsSzZ";

    /** Collapses each field to a single run, in a fixed order (numeric 'y' and 'd' widths are irrelevant to en-US). */
    private static String canonicalSkeleton(String skeleton) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < SKELETON_ORDER.length(); i++) {
            char f = SKELETON_ORDER.charAt(i);
            int n = 0;
            for (int j = 0; j < skeleton.length(); j++) if (skeleton.charAt(j) == f) n++;
            if (n == 0) continue;
            if (f == 'y' || f == 'd' || f == 'h' || f == 'H' || f == 'm' || f == 's' || f == 'a') n = 1;
            if (f == 'E' && n <= 3) n = 1;
            if ((f == 'M' || f == 'L') && n <= 2) n = 1;
            for (int k = 0; k < n; k++) out.append(f == 'L' ? 'M' : f);
        }
        return out.toString();
    }

    private static final String[] EN_US_PATTERNS = {
        "y", "y",
        "M", "L",
        "MMM", "LLL",
        "MMMM", "LLLL",
        "d", "d",
        "yM", "M/y",
        "yMd", "M/d/y",
        "yMMM", "MMM y",
        "yMMMM", "MMMM y",
        "yMMMd", "MMM d, y",
        "yMMMMd", "MMMM d, y",
        "yMEd", "EEE, M/d/y",
        "yMMMEd", "EEE, MMM d, y",
        "yMMMMEEEEd", "EEEE, MMMM d, y",
        "Md", "M/d",
        "MEd", "EEE, M/d",
        "MMMd", "MMM d",
        "MMMMd", "MMMM d",
        "MMMEd", "EEE, MMM d",
        "MMMMEEEEd", "EEEE, MMMM d",
        "E", "ccc",
        "EEEE", "cccc",
        "Ed", "d EEE",
        "h", "h a",
        "H", "HH",
        "ah", "h a",
        "hm", "h:mm a",
        "ahm", "h:mm a",
        "Hm", "HH:mm",
        "hms", "h:mm:ss a",
        "ahms", "h:mm:ss a",
        "Hms", "HH:mm:ss",
        "ms", "mm:ss",
    };

    private static String bestPattern(String canonical) {
        for (int i = 0; i < EN_US_PATTERNS.length; i += 2) {
            if (EN_US_PATTERNS[i].equals(canonical)) return EN_US_PATTERNS[i + 1];
        }
        return null;
    }

    public static java.text.DateFormat getTimeFormat(Context context) {
        if (is24HourFormat(context)) return new java.text.SimpleDateFormat("HH:mm");
        return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT);
    }

    public static java.text.DateFormat getDateFormat(Context context) {
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT);
    }

    public static java.text.DateFormat getLongDateFormat(Context context) {
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG);
    }

    public static java.text.DateFormat getMediumDateFormat(Context context) {
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
    }

    public static char[] getDateFormatOrder(Context context) {
        String pattern = ((java.text.SimpleDateFormat) getDateFormat(context)).toPattern();
        char[] order = new char[3];
        int n = 0;
        for (int i = 0; i < pattern.length() && n < 3; i++) {
            char c = pattern.charAt(i);
            if ((c == 'd' || c == 'M' || c == 'y') && !seen(order, n, c)) order[n++] = c;
        }
        return order;
    }

    public static CharSequence format(CharSequence inFormat, long inTimeInMillis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(inTimeInMillis);
        return format(inFormat, c);
    }

    public static CharSequence format(CharSequence inFormat, Date inDate) {
        Calendar c = Calendar.getInstance();
        c.setTime(inDate);
        return format(inFormat, c);
    }

    public static CharSequence format(CharSequence inFormat, Calendar inDate) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = inFormat.length();
        while (i < n) {
            char c = inFormat.charAt(i);
            if (c == '\'') {
                if (i + 1 < n && inFormat.charAt(i + 1) == '\'') {
                    out.append('\'');
                    i += 2;
                    continue;
                }
                i++;
                while (i < n) {
                    char d = inFormat.charAt(i);
                    if (d == '\'') {
                        if (i + 1 < n && inFormat.charAt(i + 1) == '\'') {
                            out.append('\'');
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    out.append(d);
                    i++;
                }
                continue;
            }
            int j = i + 1;
            while (j < n && inFormat.charAt(j) == c) j++;
            appendToken(out, c, j - i, inDate);
            i = j;
        }
        return out.toString();
    }

    /** framework-internal (hidden in AOSP): whether the format shows seconds, ignoring quoted text. */
    public static boolean hasSeconds(CharSequence inFormat) { return hasDesignator(inFormat, 's'); }

    /** framework-internal (hidden in AOSP). */
    public static boolean hasDesignator(CharSequence inFormat, char designator) {
        if (inFormat == null) return false;
        final int length = inFormat.length();
        boolean insideQuote = false;
        for (int i = 0; i < length; i++) {
            final char c = inFormat.charAt(i);
            if (c == '\'') insideQuote = !insideQuote;
            else if (!insideQuote && c == designator) return true;
        }
        return false;
    }

    private static boolean seen(char[] order, int n, char c) {
        for (int i = 0; i < n; i++) if (order[i] == c) return true;
        return false;
    }

    private static void appendToken(StringBuilder out, char c, int count, Calendar cal) {
        switch (c) {
            case 'y':
                int year = cal.get(Calendar.YEAR);
                if (count == 2) appendNumber(out, year % 100, 2);
                else appendNumber(out, year, count >= 4 ? 4 : 0);
                break;
            case 'M':
            case 'L':
                if (count == 5) out.append(DateUtils.getMonthString(cal.get(Calendar.MONTH), DateUtils.LENGTH_LONG).charAt(0));
                else if (count == 4) out.append(DateUtils.getMonthString(cal.get(Calendar.MONTH), DateUtils.LENGTH_LONG));
                else if (count == 3) {
                    out.append(DateUtils.getMonthString(cal.get(Calendar.MONTH), DateUtils.LENGTH_SHORT));
                }
                else appendNumber(out, cal.get(Calendar.MONTH) + 1, count);
                break;
            case 'd':
                appendNumber(out, cal.get(Calendar.DAY_OF_MONTH), count);
                break;
            case 'E':
            case 'c':
                int style = count >= 4 ? DateUtils.LENGTH_LONG : DateUtils.LENGTH_SHORT;
                String day = DateUtils.getDayOfWeekString(cal.get(Calendar.DAY_OF_WEEK), style);
                if (count == 5) out.append(day.charAt(0));
                else out.append(day);
                break;
            case 'a':
                out.append(DateUtils.getAMPMString(cal.get(Calendar.AM_PM)));
                break;
            case 'h':
            case 'K': {
                int h = cal.get(Calendar.HOUR);
                if (c == 'h' && h == 0) h = 12;
                appendNumber(out, h, count);
                break;
            }
            case 'H':
                appendNumber(out, cal.get(Calendar.HOUR_OF_DAY), count);
                break;
            case 'k': {
                int h = cal.get(Calendar.HOUR_OF_DAY);
                if (h == 0) h = 24;
                appendNumber(out, h, count);
                break;
            }
            case 'm':
                appendNumber(out, cal.get(Calendar.MINUTE), count);
                break;
            case 's':
                appendNumber(out, cal.get(Calendar.SECOND), count);
                break;
            case 'z':
                out.append(cal.getTimeZone().getID());
                break;
            default:
                for (int i = 0; i < count; i++) out.append(c);
                break;
        }
    }

    private static void appendNumber(StringBuilder out, int value, int width) {
        String s = Integer.toString(value);
        for (int i = s.length(); i < width; i++) out.append('0');
        out.append(s);
    }
}
