package java.time;

import static java.time.temporal.ChronoField.INSTANT_SECONDS;
import static java.time.temporal.ChronoField.MICRO_OF_SECOND;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.NANOS;

import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQuery;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.time.temporal.ValueRange;
import java.util.Objects;

/**
 * A point on the UTC time-line: seconds from 1970-01-01T00:00:00Z plus nanoseconds, with the JDK's arithmetic and
 * ISO-8601 text (DateTimeFormatter.ISO_INSTANT). The zone-related methods (atOffset, atZone, now(Clock)) are not
 * provided yet, as there are no ZoneId or Clock classes.
 */
public final class Instant implements Temporal, TemporalAdjuster, Comparable<Instant>, java.io.Serializable {
    private static final long serialVersionUID = -665713676816604388L;

    private static final long MIN_SECOND = -31557014167219200L;
    private static final long MAX_SECOND = 31556889864403199L;
    public static final Instant EPOCH = new Instant(0, 0);
    public static final Instant MIN = Instant.ofEpochSecond(MIN_SECOND, 0);
    public static final Instant MAX = Instant.ofEpochSecond(MAX_SECOND, 999_999_999);

    private static final long SECONDS_PER_DAY = 86400;
    private static final long NANOS_PER_SECOND = 1000_000_000L;
    private static final long NANOS_PER_DAY = NANOS_PER_SECOND * SECONDS_PER_DAY;
    private static final long DAYS_PER_CYCLE = 146097;
    private static final long DAYS_0000_TO_1970 = (DAYS_PER_CYCLE * 5L) - (30L * 365L + 7L);
    private static final long SECONDS_PER_10000_YEARS = 146097L * 25L * 86400L;
    private static final long SECONDS_0000_TO_1970 = DAYS_0000_TO_1970 * 86400L;

    private final long seconds;
    private final int nanos;

    private Instant(long epochSecond, int nanos) {
        this.seconds = epochSecond;
        this.nanos = nanos;
    }

    private static Instant create(long seconds, int nanoOfSecond) {
        if ((seconds | nanoOfSecond) == 0) {
            return EPOCH;
        }
        if (seconds < MIN_SECOND || seconds > MAX_SECOND) {
            throw new DateTimeException("Instant exceeds minimum or maximum instant");
        }
        return new Instant(seconds, nanoOfSecond);
    }

    public static Instant now() {
        return ofEpochMilli(System.currentTimeMillis());
    }

    public static Instant ofEpochSecond(long epochSecond) {
        return create(epochSecond, 0);
    }

    public static Instant ofEpochSecond(long epochSecond, long nanoAdjustment) {
        long secs = Math.addExact(epochSecond, Math.floorDiv(nanoAdjustment, NANOS_PER_SECOND));
        int nos = (int) Math.floorMod(nanoAdjustment, NANOS_PER_SECOND);
        return create(secs, nos);
    }

    public static Instant ofEpochMilli(long epochMilli) {
        long secs = Math.floorDiv(epochMilli, 1000);
        int mos = (int) Math.floorMod(epochMilli, 1000);
        return create(secs, mos * 1000_000);
    }

    public static Instant from(TemporalAccessor temporal) {
        if (temporal instanceof Instant) {
            return (Instant) temporal;
        }
        Objects.requireNonNull(temporal, "temporal");
        try {
            long instantSecs = temporal.getLong(INSTANT_SECONDS);
            int nanoOfSecond = temporal.get(NANO_OF_SECOND);
            return Instant.ofEpochSecond(instantSecs, nanoOfSecond);
        } catch (DateTimeException ex) {
            throw new DateTimeException("Unable to obtain Instant from TemporalAccessor: " + temporal + " of type "
                    + temporal.getClass().getName(), ex);
        }
    }

    // ---- ISO-8601 parsing (DateTimeFormatter.ISO_INSTANT)

    private static final class Parser {
        final CharSequence text;
        int pos;

        Parser(CharSequence text) {
            this.text = text;
        }

        DateTimeParseException error() {
            return new DateTimeParseException("Text '" + text + "' could not be parsed at index " + pos, text, pos);
        }

        void expect(char c) {
            if (pos >= text.length() || Character.toUpperCase(text.charAt(pos)) != c) {
                throw error();
            }
            pos++;
        }

        boolean peek(char c) {
            return pos < text.length() && text.charAt(pos) == c;
        }

        int digits(int min, int max) {
            int start = pos;
            long v = 0;
            while (pos < text.length() && pos - start < max && Character.isDigit(text.charAt(pos))) {
                v = v * 10 + (text.charAt(pos) - '0');
                pos++;
            }
            if (pos - start < min) {
                pos = start;
                throw error();
            }
            return (int) v;
        }
    }

    public static Instant parse(CharSequence text) {
        Objects.requireNonNull(text, "text");
        Parser p = new Parser(text);
        int sign = 1;
        boolean signed = false;
        if (p.peek('+') || p.peek('-')) {
            sign = p.peek('-') ? -1 : 1;
            signed = true;
            p.pos++;
        }
        int yearStart = p.pos;
        long year = p.digits(4, signed ? 10 : 4);
        if (signed && p.pos - yearStart == 4 && sign > 0) {
            p.pos = yearStart - 1;
            throw p.error(); // a '+' needs more than four digits
        }
        year *= sign;
        p.expect('-');
        int month = p.digits(2, 2);
        p.expect('-');
        int day = p.digits(2, 2);
        p.expect('T');
        int hour = p.digits(2, 2);
        p.expect(':');
        int minute = p.digits(2, 2);
        p.expect(':');
        int second = p.digits(2, 2), nano = 0;
        if (p.peek('.') || p.peek(',')) {
            p.pos++;
            int start = p.pos;
            nano = p.digits(1, 9);
            for (int i = p.pos - start; i < 9; i++) {
                nano *= 10;
            }
        }
        int offsetSecs = 0;
        if (p.pos < text.length() && Character.toUpperCase(text.charAt(p.pos)) == 'Z') {
            p.pos++;
        } else if (p.peek('+') || p.peek('-')) {
            int osign = p.peek('-') ? -1 : 1;
            p.pos++;
            int oh = p.digits(2, 2);
            int om = 0, os = 0;
            if (p.peek(':')) {
                p.pos++;
                om = p.digits(2, 2);
                if (p.peek(':')) {
                    p.pos++;
                    os = p.digits(2, 2);
                }
            }
            offsetSecs = osign * (oh * 3600 + om * 60 + os);
        } else {
            throw p.error();
        }
        if (p.pos != text.length()) {
            throw new DateTimeParseException("Text '" + text + "' could not be parsed, unparsed text found at index "
                    + p.pos, text, p.pos);
        }
        try {
            checkField(text, ChronoField.MONTH_OF_YEAR, month);
            checkField(text, ChronoField.DAY_OF_MONTH, day);
            checkField(text, ChronoField.HOUR_OF_DAY, hour == 24 ? 0 : hour);
            checkField(text, ChronoField.MINUTE_OF_HOUR, minute);
            boolean leap = second == 60;
            checkField(text, ChronoField.SECOND_OF_MINUTE, leap ? 59 : second);
            if (day > lengthOfMonth(year, month)) {
                throw new DateTimeException("Invalid date '" + monthName(month) + " " + day + "'");
            }
            long epochDay = toEpochDay(year, month, day);
            long secs = epochDay * SECONDS_PER_DAY + hour * 3600L + minute * 60L + (leap ? 59 : second) - offsetSecs;
            return ofEpochSecond(secs, nano);
        } catch (DateTimeException | ArithmeticException ex) {
            // ISO_INSTANT resolves while parsing, so an invalid value reports the start of the text
            throw new DateTimeParseException("Text '" + text + "' could not be parsed at index 0", text, 0, ex);
        }
    }

    private static void checkField(CharSequence text, ChronoField field, long value) {
        field.checkValidValue(value);
    }

    private static boolean isLeapYear(long year) {
        return ((year & 3) == 0) && ((year % 100) != 0 || (year % 400) == 0);
    }

    private static int lengthOfMonth(long year, int month) {
        switch (month) {
            case 2:
                return isLeapYear(year) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    private static String monthName(int month) {
        String[] names = {"JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER",
            "OCTOBER", "NOVEMBER", "DECEMBER"};
        return names[month - 1];
    }

    private static long toEpochDay(long y, long m, long day) {
        long total = 365 * y;
        if (y >= 0) {
            total += (y + 3) / 4 - (y + 99) / 100 + (y + 399) / 400;
        } else {
            total -= y / -4 - y / -100 + y / -400;
        }
        total += ((367 * m - 362) / 12);
        total += day - 1;
        if (m > 2) {
            total--;
            if (!isLeapYear(y)) {
                total--;
            }
        }
        return total - DAYS_0000_TO_1970;
    }

    // ---- TemporalAccessor / Temporal

    public boolean isSupported(TemporalField field) {
        if (field instanceof ChronoField) {
            return field == INSTANT_SECONDS || field == NANO_OF_SECOND || field == MICRO_OF_SECOND
                    || field == MILLI_OF_SECOND;
        }
        return field != null && field.isSupportedBy(this);
    }

    public boolean isSupported(TemporalUnit unit) {
        if (unit instanceof ChronoUnit) {
            return unit.isTimeBased() || unit == DAYS;
        }
        return unit != null && unit.isSupportedBy(this);
    }

    public ValueRange range(TemporalField field) {
        return Temporal.super.range(field);
    }

    public int get(TemporalField field) {
        if (field instanceof ChronoField) {
            switch ((ChronoField) field) {
                case NANO_OF_SECOND:
                    return nanos;
                case MICRO_OF_SECOND:
                    return nanos / 1000;
                case MILLI_OF_SECOND:
                    return nanos / 1000_000;
                default:
                    throw new UnsupportedTemporalTypeException("Unsupported field: " + field);
            }
        }
        return range(field).checkValidIntValue(field.getFrom(this), field);
    }

    public long getLong(TemporalField field) {
        if (field instanceof ChronoField) {
            switch ((ChronoField) field) {
                case NANO_OF_SECOND:
                    return nanos;
                case MICRO_OF_SECOND:
                    return nanos / 1000;
                case MILLI_OF_SECOND:
                    return nanos / 1000_000;
                case INSTANT_SECONDS:
                    return seconds;
                default:
                    throw new UnsupportedTemporalTypeException("Unsupported field: " + field);
            }
        }
        return field.getFrom(this);
    }

    public long getEpochSecond() {
        return seconds;
    }

    public int getNano() {
        return nanos;
    }

    public Instant with(TemporalAdjuster adjuster) {
        return (Instant) adjuster.adjustInto(this);
    }

    public Instant with(TemporalField field, long newValue) {
        if (field instanceof ChronoField) {
            ChronoField f = (ChronoField) field;
            f.checkValidValue(newValue);
            switch (f) {
                case MILLI_OF_SECOND: {
                    int nval = (int) newValue * 1000_000;
                    return nval != nanos ? create(seconds, nval) : this;
                }
                case MICRO_OF_SECOND: {
                    int nval = (int) newValue * 1000;
                    return nval != nanos ? create(seconds, nval) : this;
                }
                case NANO_OF_SECOND:
                    return newValue != nanos ? create(seconds, (int) newValue) : this;
                case INSTANT_SECONDS:
                    return newValue != seconds ? create(newValue, nanos) : this;
                default:
                    throw new UnsupportedTemporalTypeException("Unsupported field: " + field);
            }
        }
        return field.adjustInto(this, newValue);
    }

    public Instant truncatedTo(TemporalUnit unit) {
        if (unit == NANOS) {
            return this;
        }
        Duration unitDur = unit.getDuration();
        if (unitDur.getSeconds() > SECONDS_PER_DAY) {
            throw new UnsupportedTemporalTypeException("Unit is too large to be used for truncation");
        }
        long dur = unitDur.toNanos();
        if ((NANOS_PER_DAY % dur) != 0) {
            throw new UnsupportedTemporalTypeException("Unit must divide into a standard day without remainder");
        }
        long nod = (seconds % SECONDS_PER_DAY) * NANOS_PER_SECOND + nanos;
        long result = Math.floorDiv(nod, dur) * dur;
        return plusNanos(result - nod);
    }

    public Instant plus(TemporalAmount amountToAdd) {
        return (Instant) amountToAdd.addTo(this);
    }

    public Instant plus(long amountToAdd, TemporalUnit unit) {
        if (unit instanceof ChronoUnit) {
            switch ((ChronoUnit) unit) {
                case NANOS:
                    return plusNanos(amountToAdd);
                case MICROS:
                    return plus(amountToAdd / 1000_000, (amountToAdd % 1000_000) * 1000);
                case MILLIS:
                    return plusMillis(amountToAdd);
                case SECONDS:
                    return plusSeconds(amountToAdd);
                case MINUTES:
                    return plusSeconds(Math.multiplyExact(amountToAdd, 60));
                case HOURS:
                    return plusSeconds(Math.multiplyExact(amountToAdd, 3600));
                case HALF_DAYS:
                    return plusSeconds(Math.multiplyExact(amountToAdd, SECONDS_PER_DAY / 2));
                case DAYS:
                    return plusSeconds(Math.multiplyExact(amountToAdd, SECONDS_PER_DAY));
                default:
                    throw new UnsupportedTemporalTypeException("Unsupported unit: " + unit);
            }
        }
        return unit.addTo(this, amountToAdd);
    }

    public Instant plusSeconds(long secondsToAdd) {
        return plus(secondsToAdd, 0);
    }

    public Instant plusMillis(long millisToAdd) {
        return plus(millisToAdd / 1000, (millisToAdd % 1000) * 1000_000);
    }

    public Instant plusNanos(long nanosToAdd) {
        return plus(0, nanosToAdd);
    }

    private Instant plus(long secondsToAdd, long nanosToAdd) {
        if ((secondsToAdd | nanosToAdd) == 0) {
            return this;
        }
        long epochSec = Math.addExact(seconds, secondsToAdd);
        epochSec = Math.addExact(epochSec, nanosToAdd / NANOS_PER_SECOND);
        nanosToAdd = nanosToAdd % NANOS_PER_SECOND;
        long nanoAdjustment = nanos + nanosToAdd;
        return ofEpochSecond(epochSec, nanoAdjustment);
    }

    public Instant minus(TemporalAmount amountToSubtract) {
        return (Instant) amountToSubtract.subtractFrom(this);
    }

    public Instant minus(long amountToSubtract, TemporalUnit unit) {
        return amountToSubtract == Long.MIN_VALUE ? plus(Long.MAX_VALUE, unit).plus(1, unit)
                : plus(-amountToSubtract, unit);
    }

    public Instant minusSeconds(long secondsToSubtract) {
        return secondsToSubtract == Long.MIN_VALUE ? plusSeconds(Long.MAX_VALUE).plusSeconds(1)
                : plusSeconds(-secondsToSubtract);
    }

    public Instant minusMillis(long millisToSubtract) {
        return millisToSubtract == Long.MIN_VALUE ? plusMillis(Long.MAX_VALUE).plusMillis(1)
                : plusMillis(-millisToSubtract);
    }

    public Instant minusNanos(long nanosToSubtract) {
        return nanosToSubtract == Long.MIN_VALUE ? plusNanos(Long.MAX_VALUE).plusNanos(1) : plusNanos(-nanosToSubtract);
    }

    public <R> R query(TemporalQuery<R> query) {
        return query.queryFrom(this);
    }

    public Temporal adjustInto(Temporal temporal) {
        return temporal.with(INSTANT_SECONDS, seconds).with(NANO_OF_SECOND, nanos);
    }

    public long until(Temporal endExclusive, TemporalUnit unit) {
        Instant end = Instant.from(endExclusive);
        if (unit instanceof ChronoUnit) {
            switch ((ChronoUnit) unit) {
                case NANOS:
                    return nanosUntil(end);
                case MICROS:
                    return microsUntil(end);
                case MILLIS:
                    return millisUntil(end);
                case SECONDS:
                    return secondsUntil(end);
                case MINUTES:
                    return secondsUntil(end) / 60;
                case HOURS:
                    return secondsUntil(end) / 3600;
                case HALF_DAYS:
                    return secondsUntil(end) / (12 * 3600);
                case DAYS:
                    return secondsUntil(end) / SECONDS_PER_DAY;
                default:
                    throw new UnsupportedTemporalTypeException("Unsupported unit: " + unit);
            }
        }
        return unit.between(this, end);
    }

    private long nanosUntil(Instant end) {
        long secsDiff = Math.subtractExact(end.seconds, seconds);
        long totalNanos = Math.multiplyExact(secsDiff, NANOS_PER_SECOND);
        return Math.addExact(totalNanos, end.nanos - nanos);
    }

    private long microsUntil(Instant end) {
        long secsDiff = Math.subtractExact(end.seconds, seconds);
        long totalMicros = Math.multiplyExact(secsDiff, 1000_000);
        return Math.addExact(totalMicros, (end.nanos - nanos) / 1000);
    }

    private long millisUntil(Instant end) {
        long secsDiff = Math.subtractExact(end.seconds, seconds);
        long totalMillis = Math.multiplyExact(secsDiff, 1000);
        return Math.addExact(totalMillis, (end.nanos - nanos) / 1000_000);
    }

    private long secondsUntil(Instant end) {
        long secsDiff = Math.subtractExact(end.seconds, seconds);
        long nanosDiff = end.nanos - nanos;
        if (secsDiff > 0 && nanosDiff < 0) {
            secsDiff--;
        } else if (secsDiff < 0 && nanosDiff > 0) {
            secsDiff++;
        }
        return secsDiff;
    }

    public long toEpochMilli() {
        if (seconds < 0 && nanos > 0) {
            long millis = Math.multiplyExact(seconds + 1, 1000);
            long adjustment = nanos / 1000_000 - 1000;
            return Math.addExact(millis, adjustment);
        }
        long millis = Math.multiplyExact(seconds, 1000);
        return Math.addExact(millis, nanos / 1000_000);
    }

    public int compareTo(Instant otherInstant) {
        int cmp = Long.compare(seconds, otherInstant.seconds);
        return cmp != 0 ? cmp : nanos - otherInstant.nanos;
    }

    public boolean isAfter(Instant otherInstant) {
        return compareTo(otherInstant) > 0;
    }

    public boolean isBefore(Instant otherInstant) {
        return compareTo(otherInstant) < 0;
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Instant)) {
            return false;
        }
        Instant o = (Instant) other;
        return seconds == o.seconds && nanos == o.nanos;
    }

    public int hashCode() {
        return ((int) (seconds ^ (seconds >>> 32))) + 51 * nanos;
    }

    // ---- ISO-8601 formatting

    /** LocalDateTime.ofEpochSecond(secs, 0, UTC).toString(), plus ":00" when the second is zero. */
    private static void appendDateTime(StringBuilder buf, long epochSecond) {
        long epochDay = Math.floorDiv(epochSecond, SECONDS_PER_DAY);
        int secsOfDay = (int) Math.floorMod(epochSecond, SECONDS_PER_DAY);
        long zeroDay = epochDay + DAYS_0000_TO_1970 - 60;
        long adjust = 0;
        if (zeroDay < 0) {
            long adjustCycles = (zeroDay + 1) / DAYS_PER_CYCLE - 1;
            adjust = adjustCycles * 400;
            zeroDay += -adjustCycles * DAYS_PER_CYCLE;
        }
        long yearEst = (400 * zeroDay + 591) / DAYS_PER_CYCLE;
        long doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
        if (doyEst < 0) {
            yearEst--;
            doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
        }
        yearEst += adjust;
        int marchDoy0 = (int) doyEst;
        int marchMonth0 = (marchDoy0 * 5 + 2) / 153;
        int month = (marchMonth0 + 2) % 12 + 1;
        int dom = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1;
        long year = yearEst + marchMonth0 / 10;

        long absYear = Math.abs(year);
        if (absYear < 1000) {
            if (year < 0) {
                buf.append(year - 10000).deleteCharAt(buf.length() - 5);
            } else {
                buf.append(year + 10000).deleteCharAt(buf.length() - 5);
            }
        } else {
            if (year > 9999) {
                buf.append('+');
            }
            buf.append(year);
        }
        buf.append(month < 10 ? "-0" : "-").append(month).append(dom < 10 ? "-0" : "-").append(dom);
        int hour = secsOfDay / 3600, minute = secsOfDay / 60 % 60, second = secsOfDay % 60;
        buf.append('T').append(hour < 10 ? "0" : "").append(hour).append(minute < 10 ? ":0" : ":").append(minute);
        buf.append(second < 10 ? ":0" : ":").append(second);
    }

    /** DateTimeFormatter.ISO_INSTANT: years outside 0000-9999 carry their sign, fractions in groups of three. */
    public String toString() {
        StringBuilder buf = new StringBuilder(32);
        long inSecs = seconds;
        if (inSecs >= -SECONDS_0000_TO_1970) {
            long zeroSecs = inSecs - SECONDS_PER_10000_YEARS + SECONDS_0000_TO_1970;
            long hi = Math.floorDiv(zeroSecs, SECONDS_PER_10000_YEARS) + 1;
            long lo = Math.floorMod(zeroSecs, SECONDS_PER_10000_YEARS);
            if (hi > 0) {
                buf.append('+').append(hi);
            }
            appendDateTime(buf, lo - SECONDS_0000_TO_1970);
        } else {
            long zeroSecs = inSecs + SECONDS_0000_TO_1970;
            long hi = zeroSecs / SECONDS_PER_10000_YEARS;
            long lo = zeroSecs % SECONDS_PER_10000_YEARS;
            int pos = buf.length();
            appendDateTime(buf, lo - SECONDS_0000_TO_1970);
            if (hi < 0) {
                if (buf.charAt(pos) == '-' && buf.substring(pos, pos + 6).equals("-10000")) {
                    buf.replace(pos, pos + 2, Long.toString(hi - 1));
                } else if (lo == 0) {
                    buf.insert(pos, hi);
                } else {
                    buf.insert(pos + 1, Math.abs(hi));
                }
            }
        }
        if (nanos > 0) {
            buf.append('.');
            if (nanos % 1000_000 == 0) {
                buf.append(Integer.toString((nanos / 1000_000) + 1000).substring(1));
            } else if (nanos % 1000 == 0) {
                buf.append(Integer.toString((nanos / 1000) + 1000_000).substring(1));
            } else {
                buf.append(Integer.toString(nanos + 1000_000_000).substring(1));
            }
        }
        buf.append('Z');
        return buf.toString();
    }
}
