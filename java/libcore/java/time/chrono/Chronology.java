package java.time.chrono;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.ResolverStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.ValueRange;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A calendar system. Only the ISO calendar exists in this library, so the lookups (of, ofLocale,
 * getAvailableChronologies) know just IsoChronology and the defaults that the JDK builds from
 * ChronoLocalDateImpl/ChronoZonedDateTimeImpl use the ISO classes instead.
 */
public interface Chronology extends Comparable<Chronology> {
    static Chronology from(TemporalAccessor temporal) {
        Objects.requireNonNull(temporal, "temporal");
        Chronology obj = temporal.query(TemporalQueries.chronology());
        return obj != null ? obj : IsoChronology.INSTANCE;
    }

    static Chronology ofLocale(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        return IsoChronology.INSTANCE;
    }

    static Chronology of(String id) {
        Objects.requireNonNull(id, "id");
        if ("ISO".equals(id) || "iso8601".equals(id)) {
            return IsoChronology.INSTANCE;
        }
        throw new DateTimeException("Unknown chronology: " + id);
    }

    static Set<Chronology> getAvailableChronologies() {
        return Collections.<Chronology>singleton(IsoChronology.INSTANCE);
    }

    String getId();

    String getCalendarType();

    default ChronoLocalDate date(Era era, int yearOfEra, int month, int dayOfMonth) {
        return date(prolepticYear(era, yearOfEra), month, dayOfMonth);
    }

    ChronoLocalDate date(int prolepticYear, int month, int dayOfMonth);

    default ChronoLocalDate dateYearDay(Era era, int yearOfEra, int dayOfYear) {
        return dateYearDay(prolepticYear(era, yearOfEra), dayOfYear);
    }

    ChronoLocalDate dateYearDay(int prolepticYear, int dayOfYear);

    ChronoLocalDate dateEpochDay(long epochDay);

    default ChronoLocalDate dateNow() {
        return dateNow(Clock.systemDefaultZone());
    }

    default ChronoLocalDate dateNow(ZoneId zone) {
        return dateNow(Clock.system(zone));
    }

    default ChronoLocalDate dateNow(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return date(LocalDate.now(clock));
    }

    ChronoLocalDate date(TemporalAccessor temporal);

    default ChronoLocalDateTime<? extends ChronoLocalDate> localDateTime(TemporalAccessor temporal) {
        try {
            return date(temporal).atTime(LocalTime.from(temporal));
        } catch (DateTimeException ex) {
            throw new DateTimeException("Unable to obtain ChronoLocalDateTime from TemporalAccessor: "
                    + temporal.getClass(), ex);
        }
    }

    default ChronoZonedDateTime<? extends ChronoLocalDate> zonedDateTime(TemporalAccessor temporal) {
        return ZonedDateTime.from(temporal);
    }

    default ChronoZonedDateTime<? extends ChronoLocalDate> zonedDateTime(Instant instant, ZoneId zone) {
        return ZonedDateTime.ofInstant(instant, zone);
    }

    boolean isLeapYear(long prolepticYear);

    int prolepticYear(Era era, int yearOfEra);

    Era eraOf(int eraValue);

    List<Era> eras();

    ValueRange range(ChronoField field);

    /** No localized calendar names: the id, which is what the JDK shows for ISO in English. */
    default String getDisplayName(TextStyle style, Locale locale) {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(locale, "locale");
        return getId();
    }

    ChronoLocalDate resolveDate(Map<TemporalField, Long> fieldValues, ResolverStyle resolverStyle);

    default ChronoPeriod period(int years, int months, int days) {
        return java.time.Period.of(years, months, days);
    }

    default long epochSecond(int prolepticYear, int month, int dayOfMonth, int hour, int minute, int second,
                             ZoneOffset zoneOffset) {
        Objects.requireNonNull(zoneOffset, "zoneOffset");
        HOUR_OF_DAY.checkValidValue(hour);
        MINUTE_OF_HOUR.checkValidValue(minute);
        SECOND_OF_MINUTE.checkValidValue(second);
        long daysInSec = Math.multiplyExact(date(prolepticYear, month, dayOfMonth).toEpochDay(), 86400);
        long timeinSec = (hour * 60 + minute) * 60 + second;
        return Math.addExact(daysInSec, timeinSec - zoneOffset.getTotalSeconds());
    }

    default long epochSecond(Era era, int yearOfEra, int month, int dayOfMonth, int hour, int minute, int second,
                             ZoneOffset zoneOffset) {
        Objects.requireNonNull(era, "era");
        return epochSecond(prolepticYear(era, yearOfEra), month, dayOfMonth, hour, minute, second, zoneOffset);
    }

    default boolean isIsoBased() {
        return false;
    }

    int compareTo(Chronology other);

    boolean equals(Object obj);

    int hashCode();

    String toString();
}
