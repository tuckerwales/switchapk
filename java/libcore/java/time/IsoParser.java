package java.time;

import java.time.format.DateTimeParseException;
import java.time.zone.ZoneRulesProvider;
import java.util.Set;

/**
 * Parses the ISO-8601 forms behind LocalDate.parse(CharSequence) and friends without DateTimeFormatter. It mirrors
 * the JDK's ISO_* formatters (strict number widths, optional sections, case-insensitive 'T' and 'Z', the lenient
 * offset of ISO_OFFSET_DATE_TIME and the bracketed region id of ISO_ZONED_DATE_TIME) and their STRICT resolution,
 * so that error messages and error indexes match: element parsers return the new position, or the complement of
 * the error position as the JDK's DateTimePrinterParser does.
 */
final class IsoParser {
    private static final int SIGN_NOT_NEGATIVE = 0;
    private static final int SIGN_EXCEEDS_PAD = 1;
    private static final int SIGN_NORMAL_LENIENT = 2;

    private final CharSequence text;
    private final int length;
    private boolean caseInsensitive;

    // parsed field values, with "has" flags as Parsed keeps a field map
    long year;
    long month;
    long day;
    long hour;
    long minute;
    long second;
    long nano;
    long offsetSeconds;
    boolean hasYear;
    boolean hasMonth;
    boolean hasDay;
    boolean hasHour;
    boolean hasMinute;
    boolean hasSecond;
    boolean hasNano;
    boolean hasOffset;
    ZoneId zone;

    private IsoParser(CharSequence text) {
        if (text == null) {
            throw new NullPointerException("text");
        }
        this.text = text;
        this.length = text.length();
    }

    // ---- element parsers

    private int literal(int pos, char c) {
        if (pos == length) {
            return ~pos;
        }
        char ch = text.charAt(pos);
        if (ch != c) {
            if (!caseInsensitive || (Character.toUpperCase(ch) != Character.toUpperCase(c)
                    && Character.toLowerCase(ch) != Character.toLowerCase(c))) {
                return ~pos;
            }
        }
        return pos + 1;
    }

    /** NumberPrinterParser.parse for the widths and sign styles the ISO formatters use; value goes to out[0]. */
    private int number(int position, int minWidth, int maxWidth, int signStyle, long[] out) {
        if (position == length) {
            return ~position;
        }
        boolean strict = signStyle != SIGN_NORMAL_LENIENT;
        char sign = text.charAt(position);
        boolean negative = false;
        boolean positive = false;
        if (sign == '+') {
            if (signStyle == SIGN_NOT_NEGATIVE) {
                return ~position;
            }
            positive = true;
            position++;
        } else if (sign == '-') {
            if (signStyle == SIGN_NOT_NEGATIVE) {
                return ~position;
            }
            negative = true;
            position++;
        }
        int effMinWidth = strict ? minWidth : 1;
        int minEndPos = position + effMinWidth;
        if (minEndPos > length) {
            return ~position;
        }
        int effMaxWidth = strict ? maxWidth : 9;
        long total = 0;
        int pos = position;
        int maxEndPos = Math.min(pos + effMaxWidth, length);
        while (pos < maxEndPos) {
            char ch = text.charAt(pos++);
            int digit = ch - '0';
            if (digit < 0 || digit > 9) {
                pos--;
                if (pos < minEndPos) {
                    return ~position;
                }
                break;
            }
            total = total * 10 + digit;
        }
        if (negative) {
            if (total == 0 && strict) {
                return ~(position - 1);
            }
            total = -total;
        } else if (signStyle == SIGN_EXCEEDS_PAD) {
            int parseLen = pos - position;
            if (positive) {
                if (parseLen <= minWidth) {
                    return ~(position - 1);
                }
            } else if (parseLen > minWidth) {
                return ~position;
            }
        }
        out[0] = total;
        return pos;
    }

    private final long[] num = new long[1];

    private int localDate(int pos) {
        pos = number(pos, 4, 10, SIGN_EXCEEDS_PAD, num);
        if (pos < 0) {
            return pos;
        }
        year = num[0];
        hasYear = true;
        pos = literal(pos, '-');
        if (pos < 0) {
            return pos;
        }
        pos = number(pos, 2, 2, SIGN_NOT_NEGATIVE, num);
        if (pos < 0) {
            return pos;
        }
        month = num[0];
        hasMonth = true;
        pos = literal(pos, '-');
        if (pos < 0) {
            return pos;
        }
        pos = number(pos, 2, 2, SIGN_NOT_NEGATIVE, num);
        if (pos < 0) {
            return pos;
        }
        day = num[0];
        hasDay = true;
        return pos;
    }

    private int localTime(int pos) {
        pos = number(pos, 2, 2, SIGN_NOT_NEGATIVE, num);
        if (pos < 0) {
            return pos;
        }
        hour = num[0];
        hasHour = true;
        pos = literal(pos, ':');
        if (pos < 0) {
            return pos;
        }
        pos = number(pos, 2, 2, SIGN_NOT_NEGATIVE, num);
        if (pos < 0) {
            return pos;
        }
        minute = num[0];
        hasMinute = true;
        // optional [':' second [fraction]]
        int start = pos;
        int p = literal(pos, ':');
        if (p >= 0) {
            p = number(p, 2, 2, SIGN_NOT_NEGATIVE, num);
        }
        if (p < 0) {
            return start;
        }
        second = num[0];
        hasSecond = true;
        pos = p;
        // optional fraction: '.' then 0 to 9 digits
        if (pos < length && text.charAt(pos) == '.') {
            int fpos = pos + 1;
            int end = Math.min(fpos + 9, length);
            long total = 0;
            int digits = 0;
            while (fpos < end) {
                int digit = text.charAt(fpos) - '0';
                if (digit < 0 || digit > 9) {
                    break;
                }
                total = total * 10 + digit;
                digits++;
                fpos++;
            }
            for (int i = digits; i < 9; i++) {
                total *= 10;
            }
            nano = total;
            hasNano = true;
            pos = fpos;
        }
        return pos;
    }

    /** OffsetIdPrinterParser("+HH:MM:ss", "Z"): strict needs +HH:MM, lenient also takes +HH. */
    private int offset(int position, boolean lenient, String noOffsetText) {
        if (position == length) {
            return ~position;
        }
        int noOffsetLen = noOffsetText.length();
        if (position + noOffsetLen <= length) {
            boolean match = true;
            for (int i = 0; i < noOffsetLen; i++) {
                char a = text.charAt(position + i);
                char b = noOffsetText.charAt(i);
                if (a != b && !(caseInsensitive && Character.toUpperCase(a) == Character.toUpperCase(b))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                offsetSeconds = 0;
                hasOffset = true;
                return position + noOffsetLen;
            }
        }
        char sign = text.charAt(position);
        if (sign == '+' || sign == '-') {
            int negative = (sign == '-' ? -1 : 1);
            int[] array = new int[4];
            array[0] = position + 1;
            if (!parseDigits(false, 1, array)) {
                array[0] = ~array[0];
            }
            if (lenient) {
                if (parseDigits(true, 2, array)) {
                    parseDigits(true, 3, array);
                }
            } else {
                if (!parseDigits(true, 2, array)) {
                    array[0] = ~array[0];
                }
                parseDigits(true, 3, array);
            }
            if (array[0] > 0) {
                if (array[1] > 23 || array[2] > 59 || array[3] > 59) {
                    throw new DateTimeException("Value out of range: Hour[0-23], Minute[0-59], Second[0-59]");
                }
                offsetSeconds = negative * (array[1] * 3600L + array[2] * 60L + array[3]);
                hasOffset = true;
                return array[0];
            }
        }
        return ~position;
    }

    private boolean parseDigits(boolean isColon, int arrayIndex, int[] array) {
        int pos = array[0];
        if (pos < 0) {
            return true;
        }
        if (isColon && arrayIndex != 1) {
            if (pos + 1 > length || text.charAt(pos) != ':') {
                return false;
            }
            pos++;
        }
        if (pos + 2 > length) {
            return false;
        }
        char ch1 = text.charAt(pos++);
        char ch2 = text.charAt(pos++);
        if (ch1 < '0' || ch1 > '9' || ch2 < '0' || ch2 > '9') {
            return false;
        }
        int value = (ch1 - 48) * 10 + (ch2 - 48);
        if (value < 0 || value > 59) {
            return false;
        }
        array[arrayIndex] = value;
        array[0] = pos;
        return true;
    }

    /** ZoneIdPrinterParser for appendZoneRegionId(), case-sensitive, strict. */
    private int zoneRegionId(int position) {
        if (position == length) {
            return ~position;
        }
        char nextChar = text.charAt(position);
        if (nextChar == '+' || nextChar == '-') {
            return parseOffsetBased(position, position, true);
        } else if (length >= position + 2) {
            char nextNextChar = text.charAt(position + 1);
            if (nextChar == 'U' && nextNextChar == 'T') {
                if (length >= position + 3 && text.charAt(position + 2) == 'C') {
                    return parseOffsetBased(position, position + 3, false);
                }
                return parseOffsetBased(position, position + 2, false);
            } else if (nextChar == 'G' && length >= position + 3 && nextNextChar == 'M'
                    && text.charAt(position + 2) == 'T') {
                if (length >= position + 4 && text.charAt(position + 3) == '0') {
                    zone = ZoneId.of("GMT0");
                    return position + 4;
                }
                return parseOffsetBased(position, position + 3, false);
            }
        }
        // longest available region id at this position
        String best = null;
        Set<String> ids = ZoneRulesProvider.getAvailableZoneIds();
        for (String id : ids) {
            int n = id.length();
            if (position + n <= length && (best == null || n > best.length())
                    && text.subSequence(position, position + n).toString().equals(id)) {
                best = id;
            }
        }
        if (best == null) {
            if (nextChar == 'Z') {
                zone = ZoneOffset.UTC;
                return position + 1;
            }
            return ~position;
        }
        zone = ZoneId.of(best);
        return position + best.length();
    }

    private int parseOffsetBased(int prefixPos, int position, boolean signOnly) {
        String prefix = text.subSequence(prefixPos, position).toString().toUpperCase();
        if (position >= length) {
            zone = ZoneId.of(prefix);
            return position;
        }
        if (text.charAt(position) == '0' || text.charAt(position) == 'Z') {
            zone = ZoneId.of(prefix);
            return position;
        }
        IsoParser sub = new IsoParser(text);
        int endPos = sub.offset(position, false, signOnly ? "Z" : "0");
        try {
            if (endPos < 0) {
                if (signOnly) {
                    return ~prefixPos;
                }
                zone = ZoneId.of(prefix);
                return position;
            }
            ZoneOffset zoneOffset = ZoneOffset.ofTotalSeconds((int) sub.offsetSeconds);
            zone = ZoneId.ofOffset(prefix, zoneOffset);
            return endPos;
        } catch (DateTimeException dte) {
            return ~prefixPos;
        }
    }

    // ---- driving and resolution

    private DateTimeParseException error(int pos) {
        if (pos < 0) {
            int errorIndex = ~pos;
            return new DateTimeParseException("Text '" + abbreviate() + "' could not be parsed at index " + errorIndex,
                    text, errorIndex);
        }
        return new DateTimeParseException("Text '" + abbreviate() + "' could not be parsed, unparsed text found at "
                + "index " + pos, text, pos);
    }

    private String abbreviate() {
        if (text.length() > 64) {
            return text.subSequence(0, 64).toString() + "...";
        }
        return text.toString();
    }

    private void finish(int pos) {
        if (pos < 0 || pos < length) {
            throw error(pos);
        }
    }

    private DateTimeParseException resolveError(RuntimeException ex) {
        return new DateTimeParseException("Text '" + abbreviate() + "' could not be parsed: " + ex.getMessage(), text,
                0, ex);
    }

    private LocalDate resolveDate() {
        int y = java.time.temporal.ChronoField.YEAR.checkValidIntValue(year);
        int moy = java.time.temporal.ChronoField.MONTH_OF_YEAR.checkValidIntValue(month);
        int dom = java.time.temporal.ChronoField.DAY_OF_MONTH.checkValidIntValue(day);
        return LocalDate.of(y, moy, dom);
    }

    private LocalTime resolveTime() {
        int moh = java.time.temporal.ChronoField.MINUTE_OF_HOUR.checkValidIntValue(minute);
        int nos = java.time.temporal.ChronoField.NANO_OF_SECOND.checkValidIntValue(hasNano ? nano : 0);
        int hod = java.time.temporal.ChronoField.HOUR_OF_DAY.checkValidIntValue(hour);
        int som = java.time.temporal.ChronoField.SECOND_OF_MINUTE.checkValidIntValue(hasSecond ? second : 0);
        return LocalTime.of(hod, moh, som, nos);
    }

    static LocalDate parseLocalDate(CharSequence text) {
        IsoParser p = new IsoParser(text);
        p.finish(p.localDate(0));
        try {
            return p.resolveDate();
        } catch (DateTimeException ex) {
            throw p.resolveError(ex);
        }
    }

    static LocalTime parseLocalTime(CharSequence text) {
        IsoParser p = new IsoParser(text);
        p.finish(p.localTime(0));
        try {
            return p.resolveTime();
        } catch (DateTimeException ex) {
            throw p.resolveError(ex);
        }
    }

    private int localDateTime(int pos) {
        caseInsensitive = true;
        pos = localDate(pos);
        if (pos < 0) {
            return pos;
        }
        pos = literal(pos, 'T');
        if (pos < 0) {
            return pos;
        }
        return localTime(pos);
    }

    static LocalDateTime parseLocalDateTime(CharSequence text) {
        IsoParser p = new IsoParser(text);
        p.finish(p.localDateTime(0));
        try {
            LocalDate date = p.resolveDate();
            return LocalDateTime.of(date, p.resolveTime());
        } catch (DateTimeException ex) {
            throw p.resolveError(ex);
        }
    }

    private int offsetDateTime(int pos) {
        pos = localDateTime(pos);
        if (pos < 0) {
            return pos;
        }
        return offset(pos, true, "Z");
    }

    static OffsetDateTime parseOffsetDateTime(CharSequence text) {
        IsoParser p = new IsoParser(text);
        int pos;
        try {
            pos = p.offsetDateTime(0);
        } catch (DateTimeException ex) {
            throw p.resolveError(ex);
        }
        p.finish(pos);
        try {
            LocalDate date = p.resolveDate();
            LocalTime time = p.resolveTime();
            return OffsetDateTime.of(date, time, ZoneOffset.ofTotalSeconds((int) p.offsetSeconds));
        } catch (DateTimeException ex) {
            throw p.resolveError(ex);
        }
    }

    static OffsetTime parseOffsetTime(CharSequence text) {
        IsoParser p = new IsoParser(text);
        p.caseInsensitive = true;
        int pos;
        try {
            pos = p.localTime(0);
            if (pos >= 0) {
                pos = p.offset(pos, false, "Z");
            }
        } catch (DateTimeException ex) {
            throw p.resolveError(ex);
        }
        p.finish(pos);
        try {
            LocalTime time = p.resolveTime();
            return OffsetTime.of(time, ZoneOffset.ofTotalSeconds((int) p.offsetSeconds));
        } catch (DateTimeException ex) {
            throw p.resolveError(ex);
        }
    }

    static ZonedDateTime parseZonedDateTime(CharSequence text) {
        IsoParser p = new IsoParser(text);
        int pos;
        try {
            pos = p.offsetDateTime(0);
            if (pos >= 0) {
                // optional '[' region ']', parsed case-sensitively
                int start = pos;
                p.caseInsensitive = false;
                ZoneId saved = p.zone;
                int q = p.literal(pos, '[');
                if (q >= 0) {
                    q = p.zoneRegionId(q);
                }
                if (q >= 0) {
                    q = p.literal(q, ']');
                }
                if (q < 0) {
                    p.zone = saved;
                    pos = start;
                } else {
                    pos = q;
                }
            }
        } catch (DateTimeException ex) {
            throw p.resolveError(ex);
        }
        p.finish(pos);
        try {
            LocalDate date = p.resolveDate();
            LocalTime time = p.resolveTime();
            ZoneOffset offset = ZoneOffset.ofTotalSeconds((int) p.offsetSeconds);
            ZoneId zone = p.zone != null ? p.zone : offset;
            long epochSecond = LocalDateTime.of(date, time).toEpochSecond(offset);
            return ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSecond, time.getNano()), zone);
        } catch (DateTimeException ex) {
            throw p.resolveError(ex);
        }
    }

    static Year parseYear(CharSequence text) {
        IsoParser p = new IsoParser(text);
        int pos = p.number(0, 1, 10, SIGN_NORMAL_LENIENT, p.num);
        p.finish(pos);
        try {
            return Year.of((int) java.time.temporal.ChronoField.YEAR.checkValidIntValue(p.num[0]));
        } catch (DateTimeException ex) {
            throw p.resolveError(new DateTimeException("Unable to obtain Year from TemporalAccessor: {Year="
                    + p.num[0] + "},ISO of type java.time.format.Parsed", ex));
        }
    }

    static YearMonth parseYearMonth(CharSequence text) {
        IsoParser p = new IsoParser(text);
        int pos = p.number(0, 4, 10, SIGN_EXCEEDS_PAD, p.num);
        long y = p.num[0];
        if (pos >= 0) {
            pos = p.literal(pos, '-');
        }
        if (pos >= 0) {
            pos = p.number(pos, 2, 2, SIGN_NOT_NEGATIVE, p.num);
        }
        p.finish(pos);
        long m = p.num[0];
        try {
            return YearMonth.of(java.time.temporal.ChronoField.YEAR.checkValidIntValue(y),
                    java.time.temporal.ChronoField.MONTH_OF_YEAR.checkValidIntValue(m));
        } catch (DateTimeException ex) {
            throw p.resolveError(new DateTimeException("Unable to obtain YearMonth from TemporalAccessor: {Year=" + y
                    + ", MonthOfYear=" + m + "},ISO of type java.time.format.Parsed", ex));
        }
    }

    static MonthDay parseMonthDay(CharSequence text) {
        IsoParser p = new IsoParser(text);
        int pos = p.literal(0, '-');
        if (pos >= 0) {
            pos = p.literal(pos, '-');
            if (pos < 0) {
                pos = ~0;
            }
        }
        if (pos >= 0) {
            pos = p.number(pos, 2, 2, SIGN_NOT_NEGATIVE, p.num);
        }
        long m = p.num[0];
        if (pos >= 0) {
            pos = p.literal(pos, '-');
        }
        if (pos >= 0) {
            pos = p.number(pos, 2, 2, SIGN_NOT_NEGATIVE, p.num);
        }
        p.finish(pos);
        long d = p.num[0];
        try {
            return MonthDay.of(java.time.temporal.ChronoField.MONTH_OF_YEAR.checkValidIntValue(m),
                    java.time.temporal.ChronoField.DAY_OF_MONTH.checkValidIntValue(d));
        } catch (DateTimeException ex) {
            throw p.resolveError(new DateTimeException("Unable to obtain MonthDay from TemporalAccessor: {MonthOfYear="
                    + m + ", DayOfMonth=" + d + "},ISO of type java.time.format.Parsed", ex));
        }
    }
}
