package android.text.format;

import android.content.Context;
import java.util.Calendar;
import java.util.Formatter;
import java.util.TimeZone;

/**
 * Date and elapsed-time formatting (AOSP DateUtils). Strings are English
 * because the framework string table is not wired up. The deprecated
 * same-month and same-year tables are null, matching the SDK stub.
 */
public class DateUtils {
    public static final long SECOND_IN_MILLIS = 1000L;
    public static final long MINUTE_IN_MILLIS = 60000L;
    public static final long HOUR_IN_MILLIS = 3600000L;
    public static final long DAY_IN_MILLIS = 86400000L;
    public static final long WEEK_IN_MILLIS = 604800000L;
    public static final long YEAR_IN_MILLIS = 31449600000L;

    public static final int FORMAT_SHOW_TIME = 1;
    public static final int FORMAT_SHOW_WEEKDAY = 2;
    public static final int FORMAT_SHOW_YEAR = 4;
    public static final int FORMAT_NO_YEAR = 8;
    public static final int FORMAT_SHOW_DATE = 16;
    public static final int FORMAT_NO_MONTH_DAY = 32;
    public static final int FORMAT_12HOUR = 64;
    public static final int FORMAT_24HOUR = 128;
    public static final int FORMAT_CAP_AMPM = 256;
    public static final int FORMAT_NO_NOON = 512;
    public static final int FORMAT_CAP_NOON = 1024;
    public static final int FORMAT_NO_MIDNIGHT = 2048;
    public static final int FORMAT_CAP_MIDNIGHT = 4096;
    public static final int FORMAT_NO_NOON_MIDNIGHT = 2560;
    public static final int FORMAT_CAP_NOON_MIDNIGHT = 5120;
    public static final int FORMAT_UTC = 8192;
    public static final int FORMAT_ABBREV_TIME = 16384;
    public static final int FORMAT_ABBREV_WEEKDAY = 32768;
    public static final int FORMAT_ABBREV_MONTH = 65536;
    public static final int FORMAT_NUMERIC_DATE = 131072;
    public static final int FORMAT_ABBREV_RELATIVE = 262144;
    public static final int FORMAT_ABBREV_ALL = 524288;

    public static final int LENGTH_LONG = 10;
    public static final int LENGTH_MEDIUM = 20;
    public static final int LENGTH_SHORT = 30;
    public static final int LENGTH_SHORTER = 40;
    public static final int LENGTH_SHORTEST = 50;

    public static final String ABBREV_MONTH_FORMAT = "%b";
    public static final String ABBREV_WEEKDAY_FORMAT = "%a";
    public static final String HOUR_MINUTE_24 = "%H:%M";
    public static final String MONTH_DAY_FORMAT = "%-d";
    public static final String MONTH_FORMAT = "%B";
    public static final String NUMERIC_MONTH_FORMAT = "%m";
    public static final String WEEKDAY_FORMAT = "%A";
    public static final String YEAR_FORMAT = "%Y";
    public static final String YEAR_FORMAT_TWO_DIGITS = "%g";

    /** Deprecated. The SDK stub leaves this null; internal string ids are not available. */
    public static final int[] sameMonthTable = null;
    /** Deprecated. The SDK stub leaves this null; internal string ids are not available. */
    public static final int[] sameYearTable = null;

    private static final String[] WEEKDAYS = {"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
            "Saturday"};
    private static final String[] WEEKDAYS_SHORT = {"", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String[] WEEKDAYS_NARROW = {"", "S", "M", "T", "W", "T", "F", "S"};
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August",
            "September", "October", "November", "December"};
    private static final String[] MONTHS_SHORT = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct",
            "Nov", "Dec"};

    public DateUtils() {}

    public static String getDayOfWeekString(int dayOfWeek, int abbrev) {
        String[] table = WEEKDAYS_SHORT;
        if (abbrev == LENGTH_LONG) table = WEEKDAYS;
        else if (abbrev == LENGTH_SHORTEST) table = WEEKDAYS_NARROW;
        if (dayOfWeek < 0 || dayOfWeek >= table.length) return "";
        return table[dayOfWeek];
    }

    public static String getAMPMString(int ampm) {
        return ampm == Calendar.PM ? "PM" : "AM";
    }

    public static String getMonthString(int month, int abbrev) {
        String[] table = abbrev == LENGTH_LONG ? MONTHS : MONTHS_SHORT;
        if (month < 0 || month >= table.length) return "";
        return table[month];
    }

    public static CharSequence getRelativeTimeSpanString(long time) {
        return getRelativeTimeSpanString(time, System.currentTimeMillis(), MINUTE_IN_MILLIS);
    }

    public static CharSequence getRelativeTimeSpanString(long time, long now, long minResolution) {
        int flags = FORMAT_SHOW_DATE | FORMAT_SHOW_YEAR | FORMAT_ABBREV_MONTH;
        return getRelativeTimeSpanString(time, now, minResolution, flags);
    }

    public static CharSequence getRelativeTimeSpanString(long time, long now, long minResolution, int flags) {
        long duration = Math.abs(now - time);
        boolean past = now >= time;
        if (duration < MINUTE_IN_MILLIS && minResolution < MINUTE_IN_MILLIS) {
            return relative(duration / SECOND_IN_MILLIS, "second", past);
        }
        if (duration < HOUR_IN_MILLIS && minResolution < HOUR_IN_MILLIS) {
            return relative(duration / MINUTE_IN_MILLIS, "minute", past);
        }
        if (duration < DAY_IN_MILLIS && minResolution < DAY_IN_MILLIS) {
            return relative(duration / HOUR_IN_MILLIS, "hour", past);
        }
        if (duration < WEEK_IN_MILLIS && minResolution < WEEK_IN_MILLIS) {
            long days = duration / DAY_IN_MILLIS;
            if (days == 1 && (flags & FORMAT_ABBREV_RELATIVE) != 0) return past ? "yesterday" : "tomorrow";
            return relative(days, "day", past);
        }
        return formatDateRange(null, time, time, flags);
    }

    public static CharSequence getRelativeDateTimeString(Context c, long time, long minResolution,
            long transitionResolution, int flags) {
        long now = System.currentTimeMillis();
        long duration = Math.abs(now - time);
        if (duration < transitionResolution) {
            CharSequence rel = getRelativeTimeSpanString(time, now, minResolution, flags);
            CharSequence when = formatDateRange(c, time, time, flags | FORMAT_SHOW_TIME);
            return rel + ", " + when;
        }
        return formatDateRange(c, time, time, flags | FORMAT_SHOW_DATE | FORMAT_SHOW_YEAR);
    }

    public static String formatElapsedTime(long elapsedSeconds) {
        return formatElapsedTime(null, elapsedSeconds);
    }

    public static String formatElapsedTime(StringBuilder recycle, long elapsedSeconds) {
        if (elapsedSeconds < 0) elapsedSeconds = 0;
        long hours = elapsedSeconds / 3600;
        long minutes = (elapsedSeconds % 3600) / 60;
        long seconds = elapsedSeconds % 60;
        StringBuilder sb = recycle != null ? recycle : new StringBuilder(8);
        sb.setLength(0);
        if (hours > 0) {
            sb.append(hours).append(':').append(pad2(minutes)).append(':').append(pad2(seconds));
        } else {
            sb.append(pad2(minutes)).append(':').append(pad2(seconds));
        }
        return sb.toString();
    }

    public static final CharSequence formatSameDayTime(long then, long now, int dateStyle, int timeStyle) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(then);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(now);
        boolean same = a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
        java.text.DateFormat fmt = same ? java.text.DateFormat.getTimeInstance(timeStyle)
                : java.text.DateFormat.getDateInstance(dateStyle);
        return fmt.format(a.getTime());
    }

    public static boolean isToday(long when) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(when);
        return now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR);
    }

    public static String formatDateRange(Context context, long startMillis, long endMillis, int flags) {
        Formatter f = new Formatter();
        return formatDateRange(context, f, startMillis, endMillis, flags).toString();
    }

    public static Formatter formatDateRange(Context context, Formatter formatter, long startMillis, long endMillis,
            int flags) {
        return formatDateRange(context, formatter, startMillis, endMillis, flags, null);
    }

    public static Formatter formatDateRange(Context context, Formatter formatter, long startMillis, long endMillis,
            int flags, String timeZone) {
        if (formatter == null) formatter = new Formatter();
        String start = formatOne(startMillis, flags, timeZone);
        if (startMillis == endMillis) formatter.format("%s", start);
        else {
            String end = formatOne(endMillis, flags, timeZone);
            if (start.equals(end)) formatter.format("%s", start);
            else formatter.format("%s - %s", start, end);
        }
        return formatter;
    }

    public static String formatDateTime(Context context, long millis, int flags) {
        return formatDateRange(context, millis, millis, flags);
    }

    public static CharSequence getRelativeTimeSpanString(Context c, long millis, boolean withPreposition) {
        int flags = FORMAT_SHOW_DATE | FORMAT_SHOW_YEAR | FORMAT_ABBREV_MONTH;
        if (withPreposition) flags |= FORMAT_ABBREV_RELATIVE;
        return getRelativeTimeSpanString(millis, System.currentTimeMillis(), MINUTE_IN_MILLIS, flags);
    }

    public static CharSequence getRelativeTimeSpanString(Context c, long millis) {
        return getRelativeTimeSpanString(c, millis, false);
    }

    private static String relative(long count, String unit, boolean past) {
        String n = count + " " + unit + (count == 1 ? "" : "s");
        return past ? n + " ago" : "in " + n;
    }

    private static String formatOne(long millis, int flags, String timeZone) {
        TimeZone zone;
        if (timeZone != null && timeZone.length() > 0) zone = TimeZone.getTimeZone(timeZone);
        else if ((flags & FORMAT_UTC) != 0) zone = TimeZone.getTimeZone("UTC");
        else zone = TimeZone.getDefault();
        Calendar cal = Calendar.getInstance(zone);
        cal.setTimeInMillis(millis);
        boolean abbrevAll = (flags & FORMAT_ABBREV_ALL) != 0;
        boolean showTime = (flags & FORMAT_SHOW_TIME) != 0;
        boolean hour24 = (flags & FORMAT_24HOUR) != 0;
        StringBuilder out = new StringBuilder();
        if (showTime && cal.get(Calendar.MINUTE) == 0 && cal.get(Calendar.SECOND) == 0) {
            int hod = cal.get(Calendar.HOUR_OF_DAY);
            if (hod == 0 && (flags & FORMAT_NO_MIDNIGHT) == 0) {
                out.append((flags & FORMAT_CAP_MIDNIGHT) != 0 ? "Midnight" : "midnight");
                showTime = false;
            } else if (hod == 12 && (flags & FORMAT_NO_NOON) == 0) {
                out.append((flags & FORMAT_CAP_NOON) != 0 ? "Noon" : "noon");
                showTime = false;
            }
        }
        if ((flags & FORMAT_SHOW_WEEKDAY) != 0) {
            boolean abbrev = abbrevAll || (flags & FORMAT_ABBREV_WEEKDAY) != 0;
            appendPiece(out, getDayOfWeekString(cal.get(Calendar.DAY_OF_WEEK), abbrev ? LENGTH_SHORT : LENGTH_LONG));
        }
        boolean showYear = (flags & FORMAT_SHOW_YEAR) != 0 && (flags & FORMAT_NO_YEAR) == 0;
        if ((flags & FORMAT_SHOW_DATE) != 0 && (flags & FORMAT_NUMERIC_DATE) != 0) {
            String numeric = (cal.get(Calendar.MONTH) + 1) + "/" + cal.get(Calendar.DAY_OF_MONTH);
            if (showYear) numeric = numeric + "/" + cal.get(Calendar.YEAR);
            appendPiece(out, numeric);
            showYear = false;
        } else if ((flags & FORMAT_SHOW_DATE) != 0) {
            boolean abbrev = abbrevAll || (flags & FORMAT_ABBREV_MONTH) != 0;
            String month = getMonthString(cal.get(Calendar.MONTH), abbrev ? LENGTH_SHORT : LENGTH_LONG);
            if ((flags & FORMAT_NO_MONTH_DAY) != 0) appendPiece(out, month);
            else appendPiece(out, month + " " + cal.get(Calendar.DAY_OF_MONTH));
        }
        if (showYear) appendPiece(out, Integer.toString(cal.get(Calendar.YEAR)));
        if (showTime) appendPiece(out, formatTime(cal, hour24, abbrevAll || (flags & FORMAT_ABBREV_TIME) != 0, flags));
        return out.toString();
    }

    private static String formatTime(Calendar cal, boolean hour24, boolean abbrev, int flags) {
        int hod = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        if (hour24) return pad2(hod) + ":" + pad2(minute);
        int h = hod % 12;
        if (h == 0) h = 12;
        boolean cap = (flags & FORMAT_CAP_AMPM) != 0 || (flags & FORMAT_ABBREV_ALL) == 0;
        String ampm = getAMPMString(cal.get(Calendar.AM_PM));
        if (!cap) ampm = ampm.toLowerCase();
        if (abbrev) return h + ampm.toLowerCase();
        return h + ":" + pad2(minute) + " " + ampm;
    }

    private static void appendPiece(StringBuilder out, String piece) {
        if (piece == null || piece.length() == 0) return;
        if (out.length() > 0) out.append(", ");
        out.append(piece);
    }

    private static String pad2(long v) {
        return v < 10 ? "0" + v : Long.toString(v);
    }
}
