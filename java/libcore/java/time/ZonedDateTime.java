package java.time;

import static java.time.temporal.ChronoField.INSTANT_SECONDS;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.OFFSET_SECONDS;

import java.time.chrono.ChronoZonedDateTime;
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
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Objects;

/** A date-time with a time-zone, resolved through the zone's rules (the JDK's gap/overlap handling and text). */
public final class ZonedDateTime implements Temporal, ChronoZonedDateTime<LocalDate>, java.io.Serializable {
    private static final long serialVersionUID = -6260982410461394882L;

    static final TemporalQuery<ZonedDateTime> FROM = new TemporalQuery<ZonedDateTime>() {
        public ZonedDateTime queryFrom(TemporalAccessor temporal) {
            return ZonedDateTime.from(temporal);
        }
    };

    private final LocalDateTime dateTime;
    private final ZoneOffset offset;
    private final ZoneId zone;

    public static ZonedDateTime now() {
        return now(Clock.systemDefaultZone());
    }

    public static ZonedDateTime now(ZoneId zone) {
        return now(Clock.system(zone));
    }

    public static ZonedDateTime now(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        final Instant now = clock.instant();
        return ofInstant(now, clock.getZone());
    }

    public static ZonedDateTime of(LocalDate date, LocalTime time, ZoneId zone) {
        return of(LocalDateTime.of(date, time), zone);
    }

    public static ZonedDateTime of(LocalDateTime localDateTime, ZoneId zone) {
        return ofLocal(localDateTime, zone, null);
    }

    public static ZonedDateTime of(int year, int month, int dayOfMonth, int hour, int minute, int second,
                                   int nanoOfSecond, ZoneId zone) {
        LocalDateTime dt = LocalDateTime.of(year, month, dayOfMonth, hour, minute, second, nanoOfSecond);
        return ofLocal(dt, zone, null);
    }

    public static ZonedDateTime ofLocal(LocalDateTime localDateTime, ZoneId zone, ZoneOffset preferredOffset) {
        Objects.requireNonNull(localDateTime, "localDateTime");
        Objects.requireNonNull(zone, "zone");
        if (zone instanceof ZoneOffset) {
            return new ZonedDateTime(localDateTime, (ZoneOffset) zone, zone);
        }
        ZoneRules rules = zone.getRules();
        List<ZoneOffset> validOffsets = rules.getValidOffsets(localDateTime);
        ZoneOffset offset;
        if (validOffsets.size() == 1) {
            offset = validOffsets.get(0);
        } else if (validOffsets.size() == 0) {
            ZoneOffsetTransition trans = rules.getTransition(localDateTime);
            localDateTime = localDateTime.plusSeconds(trans.getDuration().getSeconds());
            offset = trans.getOffsetAfter();
        } else {
            if (preferredOffset != null && validOffsets.contains(preferredOffset)) {
                offset = preferredOffset;
            } else {
                offset = Objects.requireNonNull(validOffsets.get(0), "offset");
            }
        }
        return new ZonedDateTime(localDateTime, offset, zone);
    }

    public static ZonedDateTime ofInstant(Instant instant, ZoneId zone) {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(zone, "zone");
        return create(instant.getEpochSecond(), instant.getNano(), zone);
    }

    public static ZonedDateTime ofInstant(LocalDateTime localDateTime, ZoneOffset offset, ZoneId zone) {
        Objects.requireNonNull(localDateTime, "localDateTime");
        Objects.requireNonNull(offset, "offset");
        Objects.requireNonNull(zone, "zone");
        if (zone.getRules().isValidOffset(localDateTime, offset)) {
            return new ZonedDateTime(localDateTime, offset, zone);
        }
        return create(localDateTime.toEpochSecond(offset), localDateTime.getNano(), zone);
    }

    private static ZonedDateTime create(long epochSecond, int nanoOfSecond, ZoneId zone) {
        ZoneRules rules = zone.getRules();
        Instant instant = Instant.ofEpochSecond(epochSecond, nanoOfSecond);
        ZoneOffset offset = rules.getOffset(instant);
        LocalDateTime ldt = LocalDateTime.ofEpochSecond(epochSecond, nanoOfSecond, offset);
        return new ZonedDateTime(ldt, offset, zone);
    }

    public static ZonedDateTime ofStrict(LocalDateTime localDateTime, ZoneOffset offset, ZoneId zone) {
        Objects.requireNonNull(localDateTime, "localDateTime");
        Objects.requireNonNull(offset, "offset");
        Objects.requireNonNull(zone, "zone");
        ZoneRules rules = zone.getRules();
        if (rules.isValidOffset(localDateTime, offset) == false) {
            ZoneOffsetTransition trans = rules.getTransition(localDateTime);
            if (trans != null && trans.isGap()) {
                throw new DateTimeException("LocalDateTime '" + localDateTime + "' does not exist in zone '" + zone
                        + "' due to a gap in the local time-line, typically caused by daylight savings");
            }
            throw new DateTimeException("ZoneOffset '" + offset + "' is not valid for LocalDateTime '"
                    + localDateTime + "' in zone '" + zone + "'");
        }
        return new ZonedDateTime(localDateTime, offset, zone);
    }

    public static ZonedDateTime from(TemporalAccessor temporal) {
        if (temporal instanceof ZonedDateTime) {
            return (ZonedDateTime) temporal;
        }
        try {
            ZoneId zone = ZoneId.from(temporal);
            if (temporal.isSupported(INSTANT_SECONDS)) {
                long epochSecond = temporal.getLong(INSTANT_SECONDS);
                int nanoOfSecond = temporal.get(NANO_OF_SECOND);
                return create(epochSecond, nanoOfSecond, zone);
            } else {
                LocalDate date = LocalDate.from(temporal);
                LocalTime time = LocalTime.from(temporal);
                return of(date, time, zone);
            }
        } catch (DateTimeException ex) {
            throw new DateTimeException("Unable to obtain ZonedDateTime from TemporalAccessor: " + temporal
                    + " of type " + temporal.getClass().getName(), ex);
        }
    }

    public static ZonedDateTime parse(CharSequence text) {
        return IsoParser.parseZonedDateTime(text);
    }

    public static ZonedDateTime parse(CharSequence text, DateTimeFormatter formatter) {
        Objects.requireNonNull(formatter, "formatter");
        return formatter.parse(text, FROM);
    }

    private ZonedDateTime(LocalDateTime dateTime, ZoneOffset offset, ZoneId zone) {
        this.dateTime = dateTime;
        this.offset = offset;
        this.zone = zone;
    }

    private ZonedDateTime resolveLocal(LocalDateTime newDateTime) {
        return ofLocal(newDateTime, zone, offset);
    }

    private ZonedDateTime resolveInstant(LocalDateTime newDateTime) {
        return ofInstant(newDateTime, offset, zone);
    }

    private ZonedDateTime resolveOffset(ZoneOffset offset) {
        if (offset.equals(this.offset) == false && zone.getRules().isValidOffset(dateTime, offset)) {
            return new ZonedDateTime(dateTime, offset, zone);
        }
        return this;
    }

    public boolean isSupported(TemporalField field) {
        return field instanceof ChronoField || (field != null && field.isSupportedBy(this));
    }

    public boolean isSupported(TemporalUnit unit) {
        return ChronoZonedDateTime.super.isSupported(unit);
    }

    public ValueRange range(TemporalField field) {
        if (field instanceof ChronoField) {
            if (field == INSTANT_SECONDS || field == OFFSET_SECONDS) {
                return field.range();
            }
            return dateTime.range(field);
        }
        return field.rangeRefinedBy(this);
    }

    public int get(TemporalField field) {
        if (field instanceof ChronoField) {
            switch ((ChronoField) field) {
                case INSTANT_SECONDS:
                    throw new UnsupportedTemporalTypeException("Invalid field 'InstantSeconds' for get() method, "
                            + "use getLong() instead");
                case OFFSET_SECONDS:
                    return getOffset().getTotalSeconds();
                default:
                    return dateTime.get(field);
            }
        }
        return ChronoZonedDateTime.super.get(field);
    }

    public long getLong(TemporalField field) {
        if (field instanceof ChronoField) {
            switch ((ChronoField) field) {
                case INSTANT_SECONDS:
                    return toEpochSecond();
                case OFFSET_SECONDS:
                    return getOffset().getTotalSeconds();
                default:
                    return dateTime.getLong(field);
            }
        }
        return field.getFrom(this);
    }

    public ZoneOffset getOffset() {
        return offset;
    }

    public ZonedDateTime withEarlierOffsetAtOverlap() {
        ZoneOffsetTransition trans = getZone().getRules().getTransition(dateTime);
        if (trans != null && trans.isOverlap()) {
            ZoneOffset earlierOffset = trans.getOffsetBefore();
            if (earlierOffset.equals(offset) == false) {
                return new ZonedDateTime(dateTime, earlierOffset, zone);
            }
        }
        return this;
    }

    public ZonedDateTime withLaterOffsetAtOverlap() {
        ZoneOffsetTransition trans = getZone().getRules().getTransition(toLocalDateTime());
        if (trans != null) {
            ZoneOffset laterOffset = trans.getOffsetAfter();
            if (laterOffset.equals(offset) == false) {
                return new ZonedDateTime(dateTime, laterOffset, zone);
            }
        }
        return this;
    }

    public ZoneId getZone() {
        return zone;
    }

    public ZonedDateTime withZoneSameLocal(ZoneId zone) {
        Objects.requireNonNull(zone, "zone");
        return this.zone.equals(zone) ? this : ofLocal(dateTime, zone, offset);
    }

    public ZonedDateTime withZoneSameInstant(ZoneId zone) {
        Objects.requireNonNull(zone, "zone");
        return this.zone.equals(zone) ? this : create(dateTime.toEpochSecond(offset), dateTime.getNano(), zone);
    }

    public ZonedDateTime withFixedOffsetZone() {
        return this.zone.equals(offset) ? this : new ZonedDateTime(dateTime, offset, offset);
    }

    public LocalDateTime toLocalDateTime() {
        return dateTime;
    }

    public LocalDate toLocalDate() {
        return dateTime.toLocalDate();
    }

    public int getYear() {
        return dateTime.getYear();
    }

    public int getMonthValue() {
        return dateTime.getMonthValue();
    }

    public Month getMonth() {
        return dateTime.getMonth();
    }

    public int getDayOfMonth() {
        return dateTime.getDayOfMonth();
    }

    public int getDayOfYear() {
        return dateTime.getDayOfYear();
    }

    public DayOfWeek getDayOfWeek() {
        return dateTime.getDayOfWeek();
    }

    public LocalTime toLocalTime() {
        return dateTime.toLocalTime();
    }

    public int getHour() {
        return dateTime.getHour();
    }

    public int getMinute() {
        return dateTime.getMinute();
    }

    public int getSecond() {
        return dateTime.getSecond();
    }

    public int getNano() {
        return dateTime.getNano();
    }

    public ZonedDateTime with(TemporalAdjuster adjuster) {
        if (adjuster instanceof LocalDate) {
            return resolveLocal(LocalDateTime.of((LocalDate) adjuster, dateTime.toLocalTime()));
        } else if (adjuster instanceof LocalTime) {
            return resolveLocal(LocalDateTime.of(dateTime.toLocalDate(), (LocalTime) adjuster));
        } else if (adjuster instanceof LocalDateTime) {
            return resolveLocal((LocalDateTime) adjuster);
        } else if (adjuster instanceof OffsetDateTime) {
            OffsetDateTime odt = (OffsetDateTime) adjuster;
            return ofLocal(odt.toLocalDateTime(), zone, odt.getOffset());
        } else if (adjuster instanceof Instant) {
            Instant instant = (Instant) adjuster;
            return create(instant.getEpochSecond(), instant.getNano(), zone);
        } else if (adjuster instanceof ZoneOffset) {
            return resolveOffset((ZoneOffset) adjuster);
        }
        return (ZonedDateTime) adjuster.adjustInto(this);
    }

    public ZonedDateTime with(TemporalField field, long newValue) {
        if (field instanceof ChronoField) {
            ChronoField f = (ChronoField) field;
            switch (f) {
                case INSTANT_SECONDS:
                    return create(newValue, getNano(), zone);
                case OFFSET_SECONDS:
                    ZoneOffset offset = ZoneOffset.ofTotalSeconds(f.checkValidIntValue(newValue));
                    return resolveOffset(offset);
                default:
                    return resolveLocal(dateTime.with(field, newValue));
            }
        }
        return field.adjustInto(this, newValue);
    }

    public ZonedDateTime withYear(int year) {
        return resolveLocal(dateTime.withYear(year));
    }

    public ZonedDateTime withMonth(int month) {
        return resolveLocal(dateTime.withMonth(month));
    }

    public ZonedDateTime withDayOfMonth(int dayOfMonth) {
        return resolveLocal(dateTime.withDayOfMonth(dayOfMonth));
    }

    public ZonedDateTime withDayOfYear(int dayOfYear) {
        return resolveLocal(dateTime.withDayOfYear(dayOfYear));
    }

    public ZonedDateTime withHour(int hour) {
        return resolveLocal(dateTime.withHour(hour));
    }

    public ZonedDateTime withMinute(int minute) {
        return resolveLocal(dateTime.withMinute(minute));
    }

    public ZonedDateTime withSecond(int second) {
        return resolveLocal(dateTime.withSecond(second));
    }

    public ZonedDateTime withNano(int nanoOfSecond) {
        return resolveLocal(dateTime.withNano(nanoOfSecond));
    }

    public ZonedDateTime truncatedTo(TemporalUnit unit) {
        return resolveLocal(dateTime.truncatedTo(unit));
    }

    public ZonedDateTime plus(TemporalAmount amountToAdd) {
        if (amountToAdd instanceof Period) {
            Period periodToAdd = (Period) amountToAdd;
            return resolveLocal(dateTime.plus(periodToAdd));
        }
        Objects.requireNonNull(amountToAdd, "amountToAdd");
        return (ZonedDateTime) amountToAdd.addTo(this);
    }

    public ZonedDateTime plus(long amountToAdd, TemporalUnit unit) {
        if (unit instanceof ChronoUnit) {
            if (unit.isDateBased()) {
                return resolveLocal(dateTime.plus(amountToAdd, unit));
            } else {
                return resolveInstant(dateTime.plus(amountToAdd, unit));
            }
        }
        return unit.addTo(this, amountToAdd);
    }

    public ZonedDateTime plusYears(long years) {
        return resolveLocal(dateTime.plusYears(years));
    }

    public ZonedDateTime plusMonths(long months) {
        return resolveLocal(dateTime.plusMonths(months));
    }

    public ZonedDateTime plusWeeks(long weeks) {
        return resolveLocal(dateTime.plusWeeks(weeks));
    }

    public ZonedDateTime plusDays(long days) {
        return resolveLocal(dateTime.plusDays(days));
    }

    public ZonedDateTime plusHours(long hours) {
        return resolveInstant(dateTime.plusHours(hours));
    }

    public ZonedDateTime plusMinutes(long minutes) {
        return resolveInstant(dateTime.plusMinutes(minutes));
    }

    public ZonedDateTime plusSeconds(long seconds) {
        return resolveInstant(dateTime.plusSeconds(seconds));
    }

    public ZonedDateTime plusNanos(long nanos) {
        return resolveInstant(dateTime.plusNanos(nanos));
    }

    public ZonedDateTime minus(TemporalAmount amountToSubtract) {
        if (amountToSubtract instanceof Period) {
            Period periodToSubtract = (Period) amountToSubtract;
            return resolveLocal(dateTime.minus(periodToSubtract));
        }
        Objects.requireNonNull(amountToSubtract, "amountToSubtract");
        return (ZonedDateTime) amountToSubtract.subtractFrom(this);
    }

    public ZonedDateTime minus(long amountToSubtract, TemporalUnit unit) {
        return (amountToSubtract == Long.MIN_VALUE ? plus(Long.MAX_VALUE, unit).plus(1, unit)
                : plus(-amountToSubtract, unit));
    }

    public ZonedDateTime minusYears(long years) {
        return (years == Long.MIN_VALUE ? plusYears(Long.MAX_VALUE).plusYears(1) : plusYears(-years));
    }

    public ZonedDateTime minusMonths(long months) {
        return (months == Long.MIN_VALUE ? plusMonths(Long.MAX_VALUE).plusMonths(1) : plusMonths(-months));
    }

    public ZonedDateTime minusWeeks(long weeks) {
        return (weeks == Long.MIN_VALUE ? plusWeeks(Long.MAX_VALUE).plusWeeks(1) : plusWeeks(-weeks));
    }

    public ZonedDateTime minusDays(long days) {
        return (days == Long.MIN_VALUE ? plusDays(Long.MAX_VALUE).plusDays(1) : plusDays(-days));
    }

    public ZonedDateTime minusHours(long hours) {
        return (hours == Long.MIN_VALUE ? plusHours(Long.MAX_VALUE).plusHours(1) : plusHours(-hours));
    }

    public ZonedDateTime minusMinutes(long minutes) {
        return (minutes == Long.MIN_VALUE ? plusMinutes(Long.MAX_VALUE).plusMinutes(1) : plusMinutes(-minutes));
    }

    public ZonedDateTime minusSeconds(long seconds) {
        return (seconds == Long.MIN_VALUE ? plusSeconds(Long.MAX_VALUE).plusSeconds(1) : plusSeconds(-seconds));
    }

    public ZonedDateTime minusNanos(long nanos) {
        return (nanos == Long.MIN_VALUE ? plusNanos(Long.MAX_VALUE).plusNanos(1) : plusNanos(-nanos));
    }

    @SuppressWarnings("unchecked")
    public <R> R query(TemporalQuery<R> query) {
        if (query == TemporalQueries.localDate()) {
            return (R) toLocalDate();
        }
        return ChronoZonedDateTime.super.query(query);
    }

    public long until(Temporal endExclusive, TemporalUnit unit) {
        ZonedDateTime end = ZonedDateTime.from(endExclusive);
        if (unit instanceof ChronoUnit) {
            ZonedDateTime start = this;
            try {
                end = end.withZoneSameInstant(zone);
            } catch (DateTimeException ex) {
                start = withFixedOffsetZone();
                end = end.withFixedOffsetZone();
            }
            return unit.isDateBased() ? start.dateTime.until(end.dateTime, unit)
                    : start.toOffsetDateTime().until(end.toOffsetDateTime(), unit);
        }
        return unit.between(this, end);
    }

    public String format(DateTimeFormatter formatter) {
        Objects.requireNonNull(formatter, "formatter");
        return formatter.format(this);
    }

    public OffsetDateTime toOffsetDateTime() {
        return OffsetDateTime.of(dateTime, offset);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ZonedDateTime) {
            ZonedDateTime other = (ZonedDateTime) obj;
            return dateTime.equals(other.dateTime) && offset.equals(other.offset) && zone.equals(other.zone);
        }
        return false;
    }

    public int hashCode() {
        return dateTime.hashCode() ^ offset.hashCode() ^ Integer.rotateLeft(zone.hashCode(), 3);
    }

    public String toString() {
        String str = dateTime.toString() + offset.toString();
        if (offset != zone) {
            str += '[' + zone.toString() + ']';
        }
        return str;
    }
}
