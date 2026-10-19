package java.time;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_DAY;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_DAY;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoUnit.NANOS;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.time.temporal.ValueRange;
import java.util.Objects;

/** A time of day without a date or time-zone, with nanosecond precision (the JDK's arithmetic and ISO text). */
public final class LocalTime implements Temporal, TemporalAdjuster, Comparable<LocalTime>, java.io.Serializable {
    private static final long serialVersionUID = 6414437269572265201L;

    private static final LocalTime[] HOURS = new LocalTime[24];

    static {
        for (int i = 0; i < HOURS.length; i++) {
            HOURS[i] = new LocalTime(i, 0, 0, 0);
        }
    }

    public static final LocalTime MIDNIGHT = HOURS[0];
    public static final LocalTime NOON = HOURS[12];
    public static final LocalTime MIN = HOURS[0];
    public static final LocalTime MAX = new LocalTime(23, 59, 59, 999_999_999);

    static final int HOURS_PER_DAY = 24;
    static final int MINUTES_PER_HOUR = 60;
    static final int MINUTES_PER_DAY = MINUTES_PER_HOUR * HOURS_PER_DAY;
    static final int SECONDS_PER_MINUTE = 60;
    static final int SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR;
    static final int SECONDS_PER_DAY = SECONDS_PER_HOUR * HOURS_PER_DAY;
    static final long MILLIS_PER_DAY = SECONDS_PER_DAY * 1000L;
    static final long MICROS_PER_DAY = SECONDS_PER_DAY * 1000_000L;
    static final long NANOS_PER_MILLI = 1000_000L;
    static final long NANOS_PER_SECOND = 1000_000_000L;
    static final long NANOS_PER_MINUTE = NANOS_PER_SECOND * SECONDS_PER_MINUTE;
    static final long NANOS_PER_HOUR = NANOS_PER_MINUTE * MINUTES_PER_HOUR;
    static final long NANOS_PER_DAY = NANOS_PER_HOUR * HOURS_PER_DAY;

    static final TemporalQuery<LocalTime> FROM = new TemporalQuery<LocalTime>() {
        public LocalTime queryFrom(TemporalAccessor temporal) {
            return LocalTime.from(temporal);
        }
    };

    private final byte hour;
    private final byte minute;
    private final byte second;
    private final int nano;

    public static LocalTime now() {
        return now(Clock.systemDefaultZone());
    }

    public static LocalTime now(ZoneId zone) {
        return now(Clock.system(zone));
    }

    public static LocalTime now(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        final Instant now = clock.instant();
        return ofInstant(now, clock.getZone());
    }

    public static LocalTime of(int hour, int minute) {
        HOUR_OF_DAY.checkValidValue(hour);
        if (minute == 0) {
            return HOURS[hour];
        }
        MINUTE_OF_HOUR.checkValidValue(minute);
        return new LocalTime(hour, minute, 0, 0);
    }

    public static LocalTime of(int hour, int minute, int second) {
        HOUR_OF_DAY.checkValidValue(hour);
        if ((minute | second) == 0) {
            return HOURS[hour];
        }
        MINUTE_OF_HOUR.checkValidValue(minute);
        SECOND_OF_MINUTE.checkValidValue(second);
        return new LocalTime(hour, minute, second, 0);
    }

    public static LocalTime of(int hour, int minute, int second, int nanoOfSecond) {
        HOUR_OF_DAY.checkValidValue(hour);
        MINUTE_OF_HOUR.checkValidValue(minute);
        SECOND_OF_MINUTE.checkValidValue(second);
        NANO_OF_SECOND.checkValidValue(nanoOfSecond);
        return create(hour, minute, second, nanoOfSecond);
    }

    public static LocalTime ofInstant(Instant instant, ZoneId zone) {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(zone, "zone");
        ZoneOffset offset = zone.getRules().getOffset(instant);
        long localSecond = instant.getEpochSecond() + offset.getTotalSeconds();
        int secsOfDay = (int) Math.floorMod(localSecond, (long) SECONDS_PER_DAY);
        return ofNanoOfDay(secsOfDay * NANOS_PER_SECOND + instant.getNano());
    }

    public static LocalTime ofSecondOfDay(long secondOfDay) {
        SECOND_OF_DAY.checkValidValue(secondOfDay);
        int hours = (int) (secondOfDay / SECONDS_PER_HOUR);
        secondOfDay -= hours * (long) SECONDS_PER_HOUR;
        int minutes = (int) (secondOfDay / SECONDS_PER_MINUTE);
        secondOfDay -= minutes * SECONDS_PER_MINUTE;
        return create(hours, minutes, (int) secondOfDay, 0);
    }

    public static LocalTime ofNanoOfDay(long nanoOfDay) {
        NANO_OF_DAY.checkValidValue(nanoOfDay);
        int hours = (int) (nanoOfDay / NANOS_PER_HOUR);
        nanoOfDay -= hours * NANOS_PER_HOUR;
        int minutes = (int) (nanoOfDay / NANOS_PER_MINUTE);
        nanoOfDay -= minutes * NANOS_PER_MINUTE;
        int seconds = (int) (nanoOfDay / NANOS_PER_SECOND);
        nanoOfDay -= seconds * NANOS_PER_SECOND;
        return create(hours, minutes, seconds, (int) nanoOfDay);
    }

    public static LocalTime from(TemporalAccessor temporal) {
        Objects.requireNonNull(temporal, "temporal");
        LocalTime time = temporal.query(TemporalQueries.localTime());
        if (time == null) {
            throw new DateTimeException("Unable to obtain LocalTime from TemporalAccessor: " + temporal + " of type "
                    + temporal.getClass().getName());
        }
        return time;
    }

    public static LocalTime parse(CharSequence text) {
        return IsoParser.parseLocalTime(text);
    }

    public static LocalTime parse(CharSequence text, DateTimeFormatter formatter) {
        Objects.requireNonNull(formatter, "formatter");
        return formatter.parse(text, FROM);
    }

    private static LocalTime create(int hour, int minute, int second, int nanoOfSecond) {
        if ((minute | second | nanoOfSecond) == 0) {
            return HOURS[hour];
        }
        return new LocalTime(hour, minute, second, nanoOfSecond);
    }

    private LocalTime(int hour, int minute, int second, int nanoOfSecond) {
        this.hour = (byte) hour;
        this.minute = (byte) minute;
        this.second = (byte) second;
        this.nano = nanoOfSecond;
    }

    public boolean isSupported(TemporalField field) {
        if (field instanceof ChronoField) {
            return field.isTimeBased();
        }
        return field != null && field.isSupportedBy(this);
    }

    public boolean isSupported(TemporalUnit unit) {
        if (unit instanceof ChronoUnit) {
            return unit.isTimeBased();
        }
        return unit != null && unit.isSupportedBy(this);
    }

    public ValueRange range(TemporalField field) {
        return Temporal.super.range(field);
    }

    public int get(TemporalField field) {
        if (field instanceof ChronoField) {
            return get0(field);
        }
        return Temporal.super.get(field);
    }

    public long getLong(TemporalField field) {
        if (field instanceof ChronoField) {
            if (field == NANO_OF_DAY) {
                return toNanoOfDay();
            }
            if (field == ChronoField.MICRO_OF_DAY) {
                return toNanoOfDay() / 1000;
            }
            return get0(field);
        }
        return field.getFrom(this);
    }

    private int get0(TemporalField field) {
        switch ((ChronoField) field) {
            case NANO_OF_SECOND:
                return nano;
            case NANO_OF_DAY:
                throw new UnsupportedTemporalTypeException("Invalid field 'NanoOfDay' for get() method, use getLong() "
                        + "instead");
            case MICRO_OF_SECOND:
                return nano / 1000;
            case MICRO_OF_DAY:
                throw new UnsupportedTemporalTypeException("Invalid field 'MicroOfDay' for get() method, use getLong() "
                        + "instead");
            case MILLI_OF_SECOND:
                return nano / 1000_000;
            case MILLI_OF_DAY:
                return (int) (toNanoOfDay() / 1000_000);
            case SECOND_OF_MINUTE:
                return second;
            case SECOND_OF_DAY:
                return toSecondOfDay();
            case MINUTE_OF_HOUR:
                return minute;
            case MINUTE_OF_DAY:
                return hour * 60 + minute;
            case HOUR_OF_AMPM:
                return hour % 12;
            case CLOCK_HOUR_OF_AMPM: {
                int ham = hour % 12;
                return (ham % 12 == 0 ? 12 : ham);
            }
            case HOUR_OF_DAY:
                return hour;
            case CLOCK_HOUR_OF_DAY:
                return (hour == 0 ? 24 : hour);
            case AMPM_OF_DAY:
                return hour / 12;
            default:
                throw new UnsupportedTemporalTypeException("Unsupported field: " + field);
        }
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }

    public int getSecond() {
        return second;
    }

    public int getNano() {
        return nano;
    }

    public LocalTime with(TemporalAdjuster adjuster) {
        if (adjuster instanceof LocalTime) {
            return (LocalTime) adjuster;
        }
        return (LocalTime) adjuster.adjustInto(this);
    }

    public LocalTime with(TemporalField field, long newValue) {
        if (field instanceof ChronoField) {
            ChronoField f = (ChronoField) field;
            f.checkValidValue(newValue);
            switch (f) {
                case NANO_OF_SECOND:
                    return withNano((int) newValue);
                case NANO_OF_DAY:
                    return LocalTime.ofNanoOfDay(newValue);
                case MICRO_OF_SECOND:
                    return withNano((int) newValue * 1000);
                case MICRO_OF_DAY:
                    return LocalTime.ofNanoOfDay(newValue * 1000);
                case MILLI_OF_SECOND:
                    return withNano((int) newValue * 1000_000);
                case MILLI_OF_DAY:
                    return LocalTime.ofNanoOfDay(newValue * 1000_000);
                case SECOND_OF_MINUTE:
                    return withSecond((int) newValue);
                case SECOND_OF_DAY:
                    return plusSeconds(newValue - toSecondOfDay());
                case MINUTE_OF_HOUR:
                    return withMinute((int) newValue);
                case MINUTE_OF_DAY:
                    return plusMinutes(newValue - (hour * 60 + minute));
                case HOUR_OF_AMPM:
                    return plusHours(newValue - (hour % 12));
                case CLOCK_HOUR_OF_AMPM:
                    return plusHours((newValue == 12 ? 0 : newValue) - (hour % 12));
                case HOUR_OF_DAY:
                    return withHour((int) newValue);
                case CLOCK_HOUR_OF_DAY:
                    return withHour((int) (newValue == 24 ? 0 : newValue));
                case AMPM_OF_DAY:
                    return plusHours((newValue - (hour / 12)) * 12);
                default:
                    throw new UnsupportedTemporalTypeException("Unsupported field: " + field);
            }
        }
        return field.adjustInto(this, newValue);
    }

    public LocalTime withHour(int hour) {
        if (this.hour == hour) {
            return this;
        }
        HOUR_OF_DAY.checkValidValue(hour);
        return create(hour, minute, second, nano);
    }

    public LocalTime withMinute(int minute) {
        if (this.minute == minute) {
            return this;
        }
        MINUTE_OF_HOUR.checkValidValue(minute);
        return create(hour, minute, second, nano);
    }

    public LocalTime withSecond(int second) {
        if (this.second == second) {
            return this;
        }
        SECOND_OF_MINUTE.checkValidValue(second);
        return create(hour, minute, second, nano);
    }

    public LocalTime withNano(int nanoOfSecond) {
        if (this.nano == nanoOfSecond) {
            return this;
        }
        NANO_OF_SECOND.checkValidValue(nanoOfSecond);
        return create(hour, minute, second, nanoOfSecond);
    }

    public LocalTime truncatedTo(TemporalUnit unit) {
        if (unit == ChronoUnit.NANOS) {
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
        long nod = toNanoOfDay();
        return ofNanoOfDay((nod / dur) * dur);
    }

    public LocalTime plus(TemporalAmount amountToAdd) {
        Objects.requireNonNull(amountToAdd, "amountToAdd");
        return (LocalTime) amountToAdd.addTo(this);
    }

    public LocalTime plus(long amountToAdd, TemporalUnit unit) {
        if (unit instanceof ChronoUnit) {
            switch ((ChronoUnit) unit) {
                case NANOS:
                    return plusNanos(amountToAdd);
                case MICROS:
                    return plusNanos((amountToAdd % MICROS_PER_DAY) * 1000);
                case MILLIS:
                    return plusNanos((amountToAdd % MILLIS_PER_DAY) * 1000_000);
                case SECONDS:
                    return plusSeconds(amountToAdd);
                case MINUTES:
                    return plusMinutes(amountToAdd);
                case HOURS:
                    return plusHours(amountToAdd);
                case HALF_DAYS:
                    return plusHours((amountToAdd % 2) * 12);
                default:
                    throw new UnsupportedTemporalTypeException("Unsupported unit: " + unit);
            }
        }
        return unit.addTo(this, amountToAdd);
    }

    public LocalTime plusHours(long hoursToAdd) {
        if (hoursToAdd == 0) {
            return this;
        }
        int newHour = ((int) (hoursToAdd % HOURS_PER_DAY) + hour + HOURS_PER_DAY) % HOURS_PER_DAY;
        return create(newHour, minute, second, nano);
    }

    public LocalTime plusMinutes(long minutesToAdd) {
        if (minutesToAdd == 0) {
            return this;
        }
        int mofd = hour * MINUTES_PER_HOUR + minute;
        int newMofd = ((int) (minutesToAdd % MINUTES_PER_DAY) + mofd + MINUTES_PER_DAY) % MINUTES_PER_DAY;
        if (mofd == newMofd) {
            return this;
        }
        int newHour = newMofd / MINUTES_PER_HOUR;
        int newMinute = newMofd % MINUTES_PER_HOUR;
        return create(newHour, newMinute, second, nano);
    }

    public LocalTime plusSeconds(long secondstoAdd) {
        if (secondstoAdd == 0) {
            return this;
        }
        int sofd = hour * SECONDS_PER_HOUR + minute * SECONDS_PER_MINUTE + second;
        int newSofd = ((int) (secondstoAdd % SECONDS_PER_DAY) + sofd + SECONDS_PER_DAY) % SECONDS_PER_DAY;
        if (sofd == newSofd) {
            return this;
        }
        int newHour = newSofd / SECONDS_PER_HOUR;
        int newMinute = (newSofd / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR;
        int newSecond = newSofd % SECONDS_PER_MINUTE;
        return create(newHour, newMinute, newSecond, nano);
    }

    public LocalTime plusNanos(long nanosToAdd) {
        if (nanosToAdd == 0) {
            return this;
        }
        long nofd = toNanoOfDay();
        long newNofd = ((nanosToAdd % NANOS_PER_DAY) + nofd + NANOS_PER_DAY) % NANOS_PER_DAY;
        if (nofd == newNofd) {
            return this;
        }
        int newHour = (int) (newNofd / NANOS_PER_HOUR);
        int newMinute = (int) ((newNofd / NANOS_PER_MINUTE) % MINUTES_PER_HOUR);
        int newSecond = (int) ((newNofd / NANOS_PER_SECOND) % SECONDS_PER_MINUTE);
        int newNano = (int) (newNofd % NANOS_PER_SECOND);
        return create(newHour, newMinute, newSecond, newNano);
    }

    public LocalTime minus(TemporalAmount amountToSubtract) {
        Objects.requireNonNull(amountToSubtract, "amountToSubtract");
        return (LocalTime) amountToSubtract.subtractFrom(this);
    }

    public LocalTime minus(long amountToSubtract, TemporalUnit unit) {
        return (amountToSubtract == Long.MIN_VALUE ? plus(Long.MAX_VALUE, unit).plus(1, unit)
                : plus(-amountToSubtract, unit));
    }

    public LocalTime minusHours(long hoursToSubtract) {
        return plusHours(-(hoursToSubtract % HOURS_PER_DAY));
    }

    public LocalTime minusMinutes(long minutesToSubtract) {
        return plusMinutes(-(minutesToSubtract % MINUTES_PER_DAY));
    }

    public LocalTime minusSeconds(long secondsToSubtract) {
        return plusSeconds(-(secondsToSubtract % SECONDS_PER_DAY));
    }

    public LocalTime minusNanos(long nanosToSubtract) {
        return plusNanos(-(nanosToSubtract % NANOS_PER_DAY));
    }

    @SuppressWarnings("unchecked")
    public <R> R query(TemporalQuery<R> query) {
        if (query == TemporalQueries.chronology() || query == TemporalQueries.zoneId()
                || query == TemporalQueries.zone() || query == TemporalQueries.offset()) {
            return null;
        } else if (query == TemporalQueries.localTime()) {
            return (R) this;
        } else if (query == TemporalQueries.localDate()) {
            return null;
        } else if (query == TemporalQueries.precision()) {
            return (R) NANOS;
        }
        return query.queryFrom(this);
    }

    public Temporal adjustInto(Temporal temporal) {
        return temporal.with(NANO_OF_DAY, toNanoOfDay());
    }

    public long until(Temporal endExclusive, TemporalUnit unit) {
        LocalTime end = LocalTime.from(endExclusive);
        if (unit instanceof ChronoUnit) {
            long nanosUntil = end.toNanoOfDay() - toNanoOfDay();
            switch ((ChronoUnit) unit) {
                case NANOS:
                    return nanosUntil;
                case MICROS:
                    return nanosUntil / 1000;
                case MILLIS:
                    return nanosUntil / 1000_000;
                case SECONDS:
                    return nanosUntil / NANOS_PER_SECOND;
                case MINUTES:
                    return nanosUntil / NANOS_PER_MINUTE;
                case HOURS:
                    return nanosUntil / NANOS_PER_HOUR;
                case HALF_DAYS:
                    return nanosUntil / (12 * NANOS_PER_HOUR);
                default:
                    throw new UnsupportedTemporalTypeException("Unsupported unit: " + unit);
            }
        }
        return unit.between(this, end);
    }

    public String format(DateTimeFormatter formatter) {
        Objects.requireNonNull(formatter, "formatter");
        return formatter.format(this);
    }

    public LocalDateTime atDate(LocalDate date) {
        return LocalDateTime.of(date, this);
    }

    public OffsetTime atOffset(ZoneOffset offset) {
        return OffsetTime.of(this, offset);
    }

    public int toSecondOfDay() {
        int total = hour * SECONDS_PER_HOUR;
        total += minute * SECONDS_PER_MINUTE;
        total += second;
        return total;
    }

    public long toNanoOfDay() {
        long total = hour * NANOS_PER_HOUR;
        total += minute * NANOS_PER_MINUTE;
        total += second * NANOS_PER_SECOND;
        total += nano;
        return total;
    }

    public long toEpochSecond(LocalDate date, ZoneOffset offset) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(offset, "offset");
        long epochDay = date.toEpochDay();
        long secs = epochDay * 86400 + toSecondOfDay();
        secs -= offset.getTotalSeconds();
        return secs;
    }

    public int compareTo(LocalTime other) {
        int cmp = Integer.compare(hour, other.hour);
        if (cmp == 0) {
            cmp = Integer.compare(minute, other.minute);
            if (cmp == 0) {
                cmp = Integer.compare(second, other.second);
                if (cmp == 0) {
                    cmp = Integer.compare(nano, other.nano);
                }
            }
        }
        return cmp;
    }

    public boolean isAfter(LocalTime other) {
        return compareTo(other) > 0;
    }

    public boolean isBefore(LocalTime other) {
        return compareTo(other) < 0;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof LocalTime) {
            LocalTime other = (LocalTime) obj;
            return hour == other.hour && minute == other.minute && second == other.second && nano == other.nano;
        }
        return false;
    }

    public int hashCode() {
        long nod = toNanoOfDay();
        return (int) (nod ^ (nod >>> 32));
    }

    public String toString() {
        StringBuilder buf = new StringBuilder(18);
        appendTime(buf);
        return buf.toString();
    }

    void appendTime(StringBuilder buf) {
        int hourValue = hour;
        int minuteValue = minute;
        int secondValue = second;
        int nanoValue = nano;
        buf.append(hourValue < 10 ? "0" : "").append(hourValue).append(minuteValue < 10 ? ":0" : ":")
                .append(minuteValue);
        if (secondValue > 0 || nanoValue > 0) {
            buf.append(secondValue < 10 ? ":0" : ":").append(secondValue);
            if (nanoValue > 0) {
                buf.append('.');
                if (nanoValue % 1000_000 == 0) {
                    buf.append(Integer.toString((nanoValue / 1000_000) + 1000).substring(1));
                } else if (nanoValue % 1000 == 0) {
                    buf.append(Integer.toString((nanoValue / 1000) + 1000_000).substring(1));
                } else {
                    buf.append(Integer.toString((nanoValue) + 1000_000_000).substring(1));
                }
            }
        }
    }
}
