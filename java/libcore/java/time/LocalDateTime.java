package java.time;

import static java.time.LocalTime.HOURS_PER_DAY;
import static java.time.LocalTime.MICROS_PER_DAY;
import static java.time.LocalTime.MILLIS_PER_DAY;
import static java.time.LocalTime.MINUTES_PER_DAY;
import static java.time.LocalTime.NANOS_PER_DAY;
import static java.time.LocalTime.NANOS_PER_HOUR;
import static java.time.LocalTime.NANOS_PER_MINUTE;
import static java.time.LocalTime.NANOS_PER_SECOND;
import static java.time.LocalTime.SECONDS_PER_DAY;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;

import java.time.chrono.ChronoLocalDateTime;
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
import java.time.temporal.ValueRange;
import java.time.zone.ZoneRules;
import java.util.Objects;

/** A date-time without a time-zone in the ISO-8601 calendar (the JDK's arithmetic and ISO text). */
public final class LocalDateTime
        implements Temporal, TemporalAdjuster, ChronoLocalDateTime<LocalDate>, java.io.Serializable {
    private static final long serialVersionUID = 6207766400415563566L;

    public static final LocalDateTime MIN = LocalDateTime.of(LocalDate.MIN, LocalTime.MIN);
    public static final LocalDateTime MAX = LocalDateTime.of(LocalDate.MAX, LocalTime.MAX);

    static final TemporalQuery<LocalDateTime> FROM = new TemporalQuery<LocalDateTime>() {
        public LocalDateTime queryFrom(TemporalAccessor temporal) {
            return LocalDateTime.from(temporal);
        }
    };

    private final LocalDate date;
    private final LocalTime time;

    public static LocalDateTime now() {
        return now(Clock.systemDefaultZone());
    }

    public static LocalDateTime now(ZoneId zone) {
        return now(Clock.system(zone));
    }

    public static LocalDateTime now(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        final Instant now = clock.instant();
        ZoneOffset offset = clock.getZone().getRules().getOffset(now);
        return ofEpochSecond(now.getEpochSecond(), now.getNano(), offset);
    }

    public static LocalDateTime of(int year, Month month, int dayOfMonth, int hour, int minute) {
        LocalDate date = LocalDate.of(year, month, dayOfMonth);
        LocalTime time = LocalTime.of(hour, minute);
        return new LocalDateTime(date, time);
    }

    public static LocalDateTime of(int year, Month month, int dayOfMonth, int hour, int minute, int second) {
        LocalDate date = LocalDate.of(year, month, dayOfMonth);
        LocalTime time = LocalTime.of(hour, minute, second);
        return new LocalDateTime(date, time);
    }

    public static LocalDateTime of(int year, Month month, int dayOfMonth, int hour, int minute, int second,
                                   int nanoOfSecond) {
        LocalDate date = LocalDate.of(year, month, dayOfMonth);
        LocalTime time = LocalTime.of(hour, minute, second, nanoOfSecond);
        return new LocalDateTime(date, time);
    }

    public static LocalDateTime of(int year, int month, int dayOfMonth, int hour, int minute) {
        LocalDate date = LocalDate.of(year, month, dayOfMonth);
        LocalTime time = LocalTime.of(hour, minute);
        return new LocalDateTime(date, time);
    }

    public static LocalDateTime of(int year, int month, int dayOfMonth, int hour, int minute, int second) {
        LocalDate date = LocalDate.of(year, month, dayOfMonth);
        LocalTime time = LocalTime.of(hour, minute, second);
        return new LocalDateTime(date, time);
    }

    public static LocalDateTime of(int year, int month, int dayOfMonth, int hour, int minute, int second,
                                   int nanoOfSecond) {
        LocalDate date = LocalDate.of(year, month, dayOfMonth);
        LocalTime time = LocalTime.of(hour, minute, second, nanoOfSecond);
        return new LocalDateTime(date, time);
    }

    public static LocalDateTime of(LocalDate date, LocalTime time) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(time, "time");
        return new LocalDateTime(date, time);
    }

    public static LocalDateTime ofInstant(Instant instant, ZoneId zone) {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(zone, "zone");
        ZoneRules rules = zone.getRules();
        ZoneOffset offset = rules.getOffset(instant);
        return ofEpochSecond(instant.getEpochSecond(), instant.getNano(), offset);
    }

    public static LocalDateTime ofEpochSecond(long epochSecond, int nanoOfSecond, ZoneOffset offset) {
        Objects.requireNonNull(offset, "offset");
        NANO_OF_SECOND.checkValidValue(nanoOfSecond);
        long localSecond = epochSecond + offset.getTotalSeconds();
        long localEpochDay = Math.floorDiv(localSecond, (long) SECONDS_PER_DAY);
        int secsOfDay = (int) Math.floorMod(localSecond, (long) SECONDS_PER_DAY);
        LocalDate date = LocalDate.ofEpochDay(localEpochDay);
        LocalTime time = LocalTime.ofNanoOfDay(secsOfDay * NANOS_PER_SECOND + nanoOfSecond);
        return new LocalDateTime(date, time);
    }

    public static LocalDateTime from(TemporalAccessor temporal) {
        if (temporal instanceof LocalDateTime) {
            return (LocalDateTime) temporal;
        } else if (temporal instanceof ZonedDateTime) {
            return ((ZonedDateTime) temporal).toLocalDateTime();
        } else if (temporal instanceof OffsetDateTime) {
            return ((OffsetDateTime) temporal).toLocalDateTime();
        }
        try {
            LocalDate date = LocalDate.from(temporal);
            LocalTime time = LocalTime.from(temporal);
            return new LocalDateTime(date, time);
        } catch (DateTimeException ex) {
            throw new DateTimeException("Unable to obtain LocalDateTime from TemporalAccessor: " + temporal
                    + " of type " + temporal.getClass().getName(), ex);
        }
    }

    public static LocalDateTime parse(CharSequence text) {
        return IsoParser.parseLocalDateTime(text);
    }

    public static LocalDateTime parse(CharSequence text, DateTimeFormatter formatter) {
        Objects.requireNonNull(formatter, "formatter");
        return formatter.parse(text, FROM);
    }

    private LocalDateTime(LocalDate date, LocalTime time) {
        this.date = date;
        this.time = time;
    }

    private LocalDateTime with(LocalDate newDate, LocalTime newTime) {
        if (date == newDate && time == newTime) {
            return this;
        }
        return new LocalDateTime(newDate, newTime);
    }

    public boolean isSupported(TemporalField field) {
        if (field instanceof ChronoField) {
            ChronoField f = (ChronoField) field;
            return f.isDateBased() || f.isTimeBased();
        }
        return field != null && field.isSupportedBy(this);
    }

    public boolean isSupported(TemporalUnit unit) {
        return ChronoLocalDateTime.super.isSupported(unit);
    }

    public ValueRange range(TemporalField field) {
        if (field instanceof ChronoField) {
            ChronoField f = (ChronoField) field;
            return (f.isTimeBased() ? time.range(field) : date.range(field));
        }
        return field.rangeRefinedBy(this);
    }

    public int get(TemporalField field) {
        if (field instanceof ChronoField) {
            ChronoField f = (ChronoField) field;
            return (f.isTimeBased() ? time.get(field) : date.get(field));
        }
        return ChronoLocalDateTime.super.get(field);
    }

    public long getLong(TemporalField field) {
        if (field instanceof ChronoField) {
            ChronoField f = (ChronoField) field;
            return (f.isTimeBased() ? time.getLong(field) : date.getLong(field));
        }
        return field.getFrom(this);
    }

    public LocalDate toLocalDate() {
        return date;
    }

    public int getYear() {
        return date.getYear();
    }

    public int getMonthValue() {
        return date.getMonthValue();
    }

    public Month getMonth() {
        return date.getMonth();
    }

    public int getDayOfMonth() {
        return date.getDayOfMonth();
    }

    public int getDayOfYear() {
        return date.getDayOfYear();
    }

    public DayOfWeek getDayOfWeek() {
        return date.getDayOfWeek();
    }

    public LocalTime toLocalTime() {
        return time;
    }

    public int getHour() {
        return time.getHour();
    }

    public int getMinute() {
        return time.getMinute();
    }

    public int getSecond() {
        return time.getSecond();
    }

    public int getNano() {
        return time.getNano();
    }

    public LocalDateTime with(TemporalAdjuster adjuster) {
        if (adjuster instanceof LocalDate) {
            return with((LocalDate) adjuster, time);
        } else if (adjuster instanceof LocalTime) {
            return with(date, (LocalTime) adjuster);
        } else if (adjuster instanceof LocalDateTime) {
            return (LocalDateTime) adjuster;
        }
        return (LocalDateTime) adjuster.adjustInto(this);
    }

    public LocalDateTime with(TemporalField field, long newValue) {
        if (field instanceof ChronoField) {
            ChronoField f = (ChronoField) field;
            if (f.isTimeBased()) {
                return with(date, time.with(field, newValue));
            } else {
                return with(date.with(field, newValue), time);
            }
        }
        return field.adjustInto(this, newValue);
    }

    public LocalDateTime withYear(int year) {
        return with(date.withYear(year), time);
    }

    public LocalDateTime withMonth(int month) {
        return with(date.withMonth(month), time);
    }

    public LocalDateTime withDayOfMonth(int dayOfMonth) {
        return with(date.withDayOfMonth(dayOfMonth), time);
    }

    public LocalDateTime withDayOfYear(int dayOfYear) {
        return with(date.withDayOfYear(dayOfYear), time);
    }

    public LocalDateTime withHour(int hour) {
        LocalTime newTime = time.withHour(hour);
        return with(date, newTime);
    }

    public LocalDateTime withMinute(int minute) {
        LocalTime newTime = time.withMinute(minute);
        return with(date, newTime);
    }

    public LocalDateTime withSecond(int second) {
        LocalTime newTime = time.withSecond(second);
        return with(date, newTime);
    }

    public LocalDateTime withNano(int nanoOfSecond) {
        LocalTime newTime = time.withNano(nanoOfSecond);
        return with(date, newTime);
    }

    public LocalDateTime truncatedTo(TemporalUnit unit) {
        return with(date, time.truncatedTo(unit));
    }

    public LocalDateTime plus(TemporalAmount amountToAdd) {
        if (amountToAdd instanceof Period) {
            Period periodToAdd = (Period) amountToAdd;
            return with(date.plus(periodToAdd), time);
        }
        Objects.requireNonNull(amountToAdd, "amountToAdd");
        return (LocalDateTime) amountToAdd.addTo(this);
    }

    public LocalDateTime plus(long amountToAdd, TemporalUnit unit) {
        if (unit instanceof ChronoUnit) {
            switch ((ChronoUnit) unit) {
                case NANOS:
                    return plusNanos(amountToAdd);
                case MICROS:
                    return plusDays(amountToAdd / MICROS_PER_DAY).plusNanos((amountToAdd % MICROS_PER_DAY) * 1000);
                case MILLIS:
                    return plusDays(amountToAdd / MILLIS_PER_DAY).plusNanos((amountToAdd % MILLIS_PER_DAY) * 1000_000);
                case SECONDS:
                    return plusSeconds(amountToAdd);
                case MINUTES:
                    return plusMinutes(amountToAdd);
                case HOURS:
                    return plusHours(amountToAdd);
                case HALF_DAYS:
                    return plusDays(amountToAdd / 256).plusHours((amountToAdd % 256) * 12);
                default:
                    return with(date.plus(amountToAdd, unit), time);
            }
        }
        return unit.addTo(this, amountToAdd);
    }

    public LocalDateTime plusYears(long years) {
        LocalDate newDate = date.plusYears(years);
        return with(newDate, time);
    }

    public LocalDateTime plusMonths(long months) {
        LocalDate newDate = date.plusMonths(months);
        return with(newDate, time);
    }

    public LocalDateTime plusWeeks(long weeks) {
        LocalDate newDate = date.plusWeeks(weeks);
        return with(newDate, time);
    }

    public LocalDateTime plusDays(long days) {
        LocalDate newDate = date.plusDays(days);
        return with(newDate, time);
    }

    public LocalDateTime plusHours(long hours) {
        return plusWithOverflow(date, hours, 0, 0, 0, 1);
    }

    public LocalDateTime plusMinutes(long minutes) {
        return plusWithOverflow(date, 0, minutes, 0, 0, 1);
    }

    public LocalDateTime plusSeconds(long seconds) {
        return plusWithOverflow(date, 0, 0, seconds, 0, 1);
    }

    public LocalDateTime plusNanos(long nanos) {
        return plusWithOverflow(date, 0, 0, 0, nanos, 1);
    }

    public LocalDateTime minus(TemporalAmount amountToSubtract) {
        if (amountToSubtract instanceof Period) {
            Period periodToSubtract = (Period) amountToSubtract;
            return with(date.minus(periodToSubtract), time);
        }
        Objects.requireNonNull(amountToSubtract, "amountToSubtract");
        return (LocalDateTime) amountToSubtract.subtractFrom(this);
    }

    public LocalDateTime minus(long amountToSubtract, TemporalUnit unit) {
        return (amountToSubtract == Long.MIN_VALUE ? plus(Long.MAX_VALUE, unit).plus(1, unit)
                : plus(-amountToSubtract, unit));
    }

    public LocalDateTime minusYears(long years) {
        return (years == Long.MIN_VALUE ? plusYears(Long.MAX_VALUE).plusYears(1) : plusYears(-years));
    }

    public LocalDateTime minusMonths(long months) {
        return (months == Long.MIN_VALUE ? plusMonths(Long.MAX_VALUE).plusMonths(1) : plusMonths(-months));
    }

    public LocalDateTime minusWeeks(long weeks) {
        return (weeks == Long.MIN_VALUE ? plusWeeks(Long.MAX_VALUE).plusWeeks(1) : plusWeeks(-weeks));
    }

    public LocalDateTime minusDays(long days) {
        return (days == Long.MIN_VALUE ? plusDays(Long.MAX_VALUE).plusDays(1) : plusDays(-days));
    }

    public LocalDateTime minusHours(long hours) {
        return plusWithOverflow(date, hours, 0, 0, 0, -1);
    }

    public LocalDateTime minusMinutes(long minutes) {
        return plusWithOverflow(date, 0, minutes, 0, 0, -1);
    }

    public LocalDateTime minusSeconds(long seconds) {
        return plusWithOverflow(date, 0, 0, seconds, 0, -1);
    }

    public LocalDateTime minusNanos(long nanos) {
        return plusWithOverflow(date, 0, 0, 0, nanos, -1);
    }

    private LocalDateTime plusWithOverflow(LocalDate newDate, long hours, long minutes, long seconds, long nanos,
                                           int sign) {
        if ((hours | minutes | seconds | nanos) == 0) {
            return with(newDate, time);
        }
        long totDays = nanos / NANOS_PER_DAY + seconds / SECONDS_PER_DAY + minutes / MINUTES_PER_DAY
                + hours / HOURS_PER_DAY;
        totDays *= sign;
        long totNanos = nanos % NANOS_PER_DAY + (seconds % SECONDS_PER_DAY) * NANOS_PER_SECOND
                + (minutes % MINUTES_PER_DAY) * NANOS_PER_MINUTE + (hours % HOURS_PER_DAY) * NANOS_PER_HOUR;
        long curNoD = time.toNanoOfDay();
        totNanos = totNanos * sign + curNoD;
        totDays += Math.floorDiv(totNanos, NANOS_PER_DAY);
        long newNoD = Math.floorMod(totNanos, NANOS_PER_DAY);
        LocalTime newTime = (newNoD == curNoD ? time : LocalTime.ofNanoOfDay(newNoD));
        return with(newDate.plusDays(totDays), newTime);
    }

    @SuppressWarnings("unchecked")
    public <R> R query(TemporalQuery<R> query) {
        if (query == TemporalQueries.localDate()) {
            return (R) date;
        }
        return ChronoLocalDateTime.super.query(query);
    }

    public Temporal adjustInto(Temporal temporal) {
        return ChronoLocalDateTime.super.adjustInto(temporal);
    }

    public long until(Temporal endExclusive, TemporalUnit unit) {
        LocalDateTime end = LocalDateTime.from(endExclusive);
        if (unit instanceof ChronoUnit) {
            if (unit.isTimeBased()) {
                long amount = date.daysUntil(end.date);
                if (amount == 0) {
                    return time.until(end.time, unit);
                }
                long timePart = end.time.toNanoOfDay() - time.toNanoOfDay();
                if (amount > 0) {
                    amount--;
                    timePart += NANOS_PER_DAY;
                } else {
                    amount++;
                    timePart -= NANOS_PER_DAY;
                }
                switch ((ChronoUnit) unit) {
                    case NANOS:
                        amount = Math.multiplyExact(amount, NANOS_PER_DAY);
                        break;
                    case MICROS:
                        amount = Math.multiplyExact(amount, MICROS_PER_DAY);
                        timePart = timePart / 1000;
                        break;
                    case MILLIS:
                        amount = Math.multiplyExact(amount, MILLIS_PER_DAY);
                        timePart = timePart / 1_000_000;
                        break;
                    case SECONDS:
                        amount = Math.multiplyExact(amount, SECONDS_PER_DAY);
                        timePart = timePart / NANOS_PER_SECOND;
                        break;
                    case MINUTES:
                        amount = Math.multiplyExact(amount, MINUTES_PER_DAY);
                        timePart = timePart / NANOS_PER_MINUTE;
                        break;
                    case HOURS:
                        amount = Math.multiplyExact(amount, HOURS_PER_DAY);
                        timePart = timePart / NANOS_PER_HOUR;
                        break;
                    case HALF_DAYS:
                        amount = Math.multiplyExact(amount, 2);
                        timePart = timePart / (NANOS_PER_HOUR * 12);
                        break;
                    default:
                        break;
                }
                return Math.addExact(amount, timePart);
            }
            LocalDate endDate = end.date;
            if (endDate.isAfter(date) && end.time.isBefore(time)) {
                endDate = endDate.minusDays(1);
            } else if (endDate.isBefore(date) && end.time.isAfter(time)) {
                endDate = endDate.plusDays(1);
            }
            return date.until(endDate, unit);
        }
        return unit.between(this, end);
    }

    public String format(DateTimeFormatter formatter) {
        Objects.requireNonNull(formatter, "formatter");
        return formatter.format(this);
    }

    public OffsetDateTime atOffset(ZoneOffset offset) {
        return OffsetDateTime.of(this, offset);
    }

    public ZonedDateTime atZone(ZoneId zone) {
        return ZonedDateTime.of(this, zone);
    }

    public int compareTo(ChronoLocalDateTime<?> other) {
        if (other instanceof LocalDateTime) {
            return compareTo0((LocalDateTime) other);
        }
        return ChronoLocalDateTime.super.compareTo(other);
    }

    private int compareTo0(LocalDateTime other) {
        int cmp = date.compareTo0(other.toLocalDate());
        if (cmp == 0) {
            cmp = time.compareTo(other.toLocalTime());
        }
        return cmp;
    }

    public boolean isAfter(ChronoLocalDateTime<?> other) {
        if (other instanceof LocalDateTime) {
            return compareTo0((LocalDateTime) other) > 0;
        }
        return ChronoLocalDateTime.super.isAfter(other);
    }

    public boolean isBefore(ChronoLocalDateTime<?> other) {
        if (other instanceof LocalDateTime) {
            return compareTo0((LocalDateTime) other) < 0;
        }
        return ChronoLocalDateTime.super.isBefore(other);
    }

    public boolean isEqual(ChronoLocalDateTime<?> other) {
        if (other instanceof LocalDateTime) {
            return compareTo0((LocalDateTime) other) == 0;
        }
        return ChronoLocalDateTime.super.isEqual(other);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof LocalDateTime) {
            LocalDateTime other = (LocalDateTime) obj;
            return date.equals(other.date) && time.equals(other.time);
        }
        return false;
    }

    public int hashCode() {
        return date.hashCode() ^ time.hashCode();
    }

    public String toString() {
        StringBuilder buf = new StringBuilder(29);
        date.appendDate(buf);
        buf.append('T');
        time.appendTime(buf);
        return buf.toString();
    }
}
