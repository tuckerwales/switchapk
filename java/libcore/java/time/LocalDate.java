package java.time;

import static java.time.temporal.ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH;
import static java.time.temporal.ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR;
import static java.time.temporal.ChronoField.ALIGNED_WEEK_OF_MONTH;
import static java.time.temporal.ChronoField.ALIGNED_WEEK_OF_YEAR;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_YEAR;
import static java.time.temporal.ChronoField.EPOCH_DAY;
import static java.time.temporal.ChronoField.ERA;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.PROLEPTIC_MONTH;
import static java.time.temporal.ChronoField.YEAR;

import java.time.chrono.ChronoLocalDate;
import java.time.chrono.ChronoPeriod;
import java.time.chrono.IsoChronology;
import java.time.chrono.IsoEra;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** An ISO-8601 date without a time-zone, with the JDK's arithmetic and ISO text. */
public final class LocalDate implements Temporal, TemporalAdjuster, ChronoLocalDate, java.io.Serializable {
    private static final long serialVersionUID = 2942565459149668126L;

    public static final LocalDate MIN = LocalDate.of(Year.MIN_VALUE, 1, 1);
    public static final LocalDate MAX = LocalDate.of(Year.MAX_VALUE, 12, 31);
    public static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);

    private static final int DAYS_PER_CYCLE = 146097;
    static final long DAYS_0000_TO_1970 = (DAYS_PER_CYCLE * 5L) - (30L * 365L + 7L);

    private final int year;
    private final short month;
    private final short day;

    static final TemporalQuery<LocalDate> FROM = new TemporalQuery<LocalDate>() {
        public LocalDate queryFrom(TemporalAccessor temporal) {
            return LocalDate.from(temporal);
        }
    };

    public static LocalDate now() {
        return now(Clock.systemDefaultZone());
    }

    public static LocalDate now(ZoneId zone) {
        return now(Clock.system(zone));
    }

    public static LocalDate now(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        final Instant now = clock.instant();
        return ofInstant(now, clock.getZone());
    }

    public static LocalDate of(int year, Month month, int dayOfMonth) {
        YEAR.checkValidValue(year);
        Objects.requireNonNull(month, "month");
        DAY_OF_MONTH.checkValidValue(dayOfMonth);
        return create(year, month.getValue(), dayOfMonth);
    }

    public static LocalDate of(int year, int month, int dayOfMonth) {
        YEAR.checkValidValue(year);
        MONTH_OF_YEAR.checkValidValue(month);
        DAY_OF_MONTH.checkValidValue(dayOfMonth);
        return create(year, month, dayOfMonth);
    }

    public static LocalDate ofYearDay(int year, int dayOfYear) {
        YEAR.checkValidValue(year);
        DAY_OF_YEAR.checkValidValue(dayOfYear);
        boolean leap = IsoChronology.INSTANCE.isLeapYear(year);
        if (dayOfYear == 366 && leap == false) {
            throw new DateTimeException("Invalid date 'DayOfYear 366' as '" + year + "' is not a leap year");
        }
        Month moy = Month.of((dayOfYear - 1) / 31 + 1);
        int monthEnd = moy.firstDayOfYear(leap) + moy.length(leap) - 1;
        if (dayOfYear > monthEnd) {
            moy = moy.plus(1);
        }
        int dom = dayOfYear - moy.firstDayOfYear(leap) + 1;
        return new LocalDate(year, moy.getValue(), dom);
    }

    public static LocalDate ofInstant(Instant instant, ZoneId zone) {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(zone, "zone");
        ZoneRules rules = zone.getRules();
        ZoneOffset offset = rules.getOffset(instant);
        long localSecond = instant.getEpochSecond() + offset.getTotalSeconds();
        long localEpochDay = Math.floorDiv(localSecond, 86400L);
        return ofEpochDay(localEpochDay);
    }

    public static LocalDate ofEpochDay(long epochDay) {
        EPOCH_DAY.checkValidValue(epochDay);
        long zeroDay = epochDay + DAYS_0000_TO_1970;
        zeroDay -= 60;
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
        yearEst += marchMonth0 / 10;
        int year = YEAR.checkValidIntValue(yearEst);
        return new LocalDate(year, month, dom);
    }

    public static LocalDate from(TemporalAccessor temporal) {
        Objects.requireNonNull(temporal, "temporal");
        LocalDate date = temporal.query(TemporalQueries.localDate());
        if (date == null) {
            throw new DateTimeException("Unable to obtain LocalDate from TemporalAccessor: " + temporal + " of type "
                    + temporal.getClass().getName());
        }
        return date;
    }

    public static LocalDate parse(CharSequence text) {
        return IsoParser.parseLocalDate(text);
    }

    public static LocalDate parse(CharSequence text, DateTimeFormatter formatter) {
        Objects.requireNonNull(formatter, "formatter");
        return formatter.parse(text, FROM);
    }

    private static LocalDate create(int year, int month, int dayOfMonth) {
        if (dayOfMonth > 28) {
            int dom = 31;
            switch (month) {
                case 2:
                    dom = (IsoChronology.INSTANCE.isLeapYear(year) ? 29 : 28);
                    break;
                case 4:
                case 6:
                case 9:
                case 11:
                    dom = 30;
                    break;
                default:
                    break;
            }
            if (dayOfMonth > dom) {
                if (dayOfMonth == 29) {
                    throw new DateTimeException("Invalid date 'February 29' as '" + year + "' is not a leap year");
                } else {
                    throw new DateTimeException("Invalid date '" + Month.of(month).name() + " " + dayOfMonth + "'");
                }
            }
        }
        return new LocalDate(year, month, dayOfMonth);
    }

    private static LocalDate resolvePreviousValid(int year, int month, int day) {
        switch (month) {
            case 2:
                day = Math.min(day, IsoChronology.INSTANCE.isLeapYear(year) ? 29 : 28);
                break;
            case 4:
            case 6:
            case 9:
            case 11:
                day = Math.min(day, 30);
                break;
            default:
                break;
        }
        return new LocalDate(year, month, day);
    }

    private LocalDate(int year, int month, int dayOfMonth) {
        this.year = year;
        this.month = (short) month;
        this.day = (short) dayOfMonth;
    }

    public boolean isSupported(TemporalField field) {
        return ChronoLocalDate.super.isSupported(field);
    }

    public boolean isSupported(TemporalUnit unit) {
        return ChronoLocalDate.super.isSupported(unit);
    }

    public ValueRange range(TemporalField field) {
        if (field instanceof ChronoField) {
            ChronoField f = (ChronoField) field;
            if (f.isDateBased()) {
                switch (f) {
                    case DAY_OF_MONTH:
                        return ValueRange.of(1, lengthOfMonth());
                    case DAY_OF_YEAR:
                        return ValueRange.of(1, lengthOfYear());
                    case ALIGNED_WEEK_OF_MONTH:
                        return ValueRange.of(1, getMonth() == Month.FEBRUARY && isLeapYear() == false ? 4 : 5);
                    case YEAR_OF_ERA:
                        return (getYear() <= 0 ? ValueRange.of(1, Year.MAX_VALUE + 1)
                                : ValueRange.of(1, Year.MAX_VALUE));
                    default:
                        return field.range();
                }
            }
            throw new UnsupportedTemporalTypeException("Unsupported field: " + field);
        }
        return field.rangeRefinedBy(this);
    }

    public int get(TemporalField field) {
        if (field instanceof ChronoField) {
            return get0(field);
        }
        return ChronoLocalDate.super.get(field);
    }

    public long getLong(TemporalField field) {
        if (field instanceof ChronoField) {
            if (field == EPOCH_DAY) {
                return toEpochDay();
            }
            if (field == PROLEPTIC_MONTH) {
                return getProlepticMonth();
            }
            return get0(field);
        }
        return field.getFrom(this);
    }

    private int get0(TemporalField field) {
        switch ((ChronoField) field) {
            case DAY_OF_WEEK:
                return getDayOfWeek().getValue();
            case ALIGNED_DAY_OF_WEEK_IN_MONTH:
                return ((day - 1) % 7) + 1;
            case ALIGNED_DAY_OF_WEEK_IN_YEAR:
                return ((getDayOfYear() - 1) % 7) + 1;
            case DAY_OF_MONTH:
                return day;
            case DAY_OF_YEAR:
                return getDayOfYear();
            case EPOCH_DAY:
                throw new UnsupportedTemporalTypeException("Invalid field 'EpochDay' for get() method, use getLong() "
                        + "instead");
            case ALIGNED_WEEK_OF_MONTH:
                return ((day - 1) / 7) + 1;
            case ALIGNED_WEEK_OF_YEAR:
                return ((getDayOfYear() - 1) / 7) + 1;
            case MONTH_OF_YEAR:
                return month;
            case PROLEPTIC_MONTH:
                throw new UnsupportedTemporalTypeException("Invalid field 'ProlepticMonth' for get() method, use "
                        + "getLong() instead");
            case YEAR_OF_ERA:
                return (year >= 1 ? year : 1 - year);
            case YEAR:
                return year;
            case ERA:
                return (year >= 1 ? 1 : 0);
            default:
                throw new UnsupportedTemporalTypeException("Unsupported field: " + field);
        }
    }

    private long getProlepticMonth() {
        return (year * 12L + month - 1);
    }

    public IsoChronology getChronology() {
        return IsoChronology.INSTANCE;
    }

    public IsoEra getEra() {
        return (getYear() >= 1 ? IsoEra.CE : IsoEra.BCE);
    }

    public int getYear() {
        return year;
    }

    public int getMonthValue() {
        return month;
    }

    public Month getMonth() {
        return Month.of(month);
    }

    public int getDayOfMonth() {
        return day;
    }

    public int getDayOfYear() {
        return getMonth().firstDayOfYear(isLeapYear()) + day - 1;
    }

    public DayOfWeek getDayOfWeek() {
        int dow0 = (int) Math.floorMod(toEpochDay() + 3, 7L);
        return DayOfWeek.of(dow0 + 1);
    }

    public boolean isLeapYear() {
        return IsoChronology.INSTANCE.isLeapYear(year);
    }

    public int lengthOfMonth() {
        switch (month) {
            case 2:
                return (isLeapYear() ? 29 : 28);
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    public int lengthOfYear() {
        return (isLeapYear() ? 366 : 365);
    }

    public LocalDate with(TemporalAdjuster adjuster) {
        if (adjuster instanceof LocalDate) {
            return (LocalDate) adjuster;
        }
        return (LocalDate) adjuster.adjustInto(this);
    }

    public LocalDate with(TemporalField field, long newValue) {
        if (field instanceof ChronoField) {
            ChronoField f = (ChronoField) field;
            f.checkValidValue(newValue);
            switch (f) {
                case DAY_OF_WEEK:
                    return plusDays(newValue - getDayOfWeek().getValue());
                case ALIGNED_DAY_OF_WEEK_IN_MONTH:
                    return plusDays(newValue - getLong(ALIGNED_DAY_OF_WEEK_IN_MONTH));
                case ALIGNED_DAY_OF_WEEK_IN_YEAR:
                    return plusDays(newValue - getLong(ALIGNED_DAY_OF_WEEK_IN_YEAR));
                case DAY_OF_MONTH:
                    return withDayOfMonth((int) newValue);
                case DAY_OF_YEAR:
                    return withDayOfYear((int) newValue);
                case EPOCH_DAY:
                    return LocalDate.ofEpochDay(newValue);
                case ALIGNED_WEEK_OF_MONTH:
                    return plusWeeks(newValue - getLong(ALIGNED_WEEK_OF_MONTH));
                case ALIGNED_WEEK_OF_YEAR:
                    return plusWeeks(newValue - getLong(ALIGNED_WEEK_OF_YEAR));
                case MONTH_OF_YEAR:
                    return withMonth((int) newValue);
                case PROLEPTIC_MONTH:
                    return plusMonths(newValue - getProlepticMonth());
                case YEAR_OF_ERA:
                    return withYear((int) (year >= 1 ? newValue : 1 - newValue));
                case YEAR:
                    return withYear((int) newValue);
                case ERA:
                    return (getLong(ERA) == newValue ? this : withYear(1 - year));
                default:
                    throw new UnsupportedTemporalTypeException("Unsupported field: " + field);
            }
        }
        return field.adjustInto(this, newValue);
    }

    public LocalDate withYear(int year) {
        if (this.year == year) {
            return this;
        }
        YEAR.checkValidValue(year);
        return resolvePreviousValid(year, month, day);
    }

    public LocalDate withMonth(int month) {
        if (this.month == month) {
            return this;
        }
        MONTH_OF_YEAR.checkValidValue(month);
        return resolvePreviousValid(year, month, day);
    }

    public LocalDate withDayOfMonth(int dayOfMonth) {
        if (this.day == dayOfMonth) {
            return this;
        }
        return of(year, month, dayOfMonth);
    }

    public LocalDate withDayOfYear(int dayOfYear) {
        if (this.getDayOfYear() == dayOfYear) {
            return this;
        }
        return ofYearDay(year, dayOfYear);
    }

    public LocalDate plus(TemporalAmount amountToAdd) {
        if (amountToAdd instanceof Period) {
            Period periodToAdd = (Period) amountToAdd;
            return plusMonths(periodToAdd.toTotalMonths()).plusDays(periodToAdd.getDays());
        }
        Objects.requireNonNull(amountToAdd, "amountToAdd");
        return (LocalDate) amountToAdd.addTo(this);
    }

    public LocalDate plus(long amountToAdd, TemporalUnit unit) {
        if (unit instanceof ChronoUnit) {
            switch ((ChronoUnit) unit) {
                case DAYS:
                    return plusDays(amountToAdd);
                case WEEKS:
                    return plusWeeks(amountToAdd);
                case MONTHS:
                    return plusMonths(amountToAdd);
                case YEARS:
                    return plusYears(amountToAdd);
                case DECADES:
                    return plusYears(Math.multiplyExact(amountToAdd, 10));
                case CENTURIES:
                    return plusYears(Math.multiplyExact(amountToAdd, 100));
                case MILLENNIA:
                    return plusYears(Math.multiplyExact(amountToAdd, 1000));
                case ERAS:
                    return with(ERA, Math.addExact(getLong(ERA), amountToAdd));
                default:
                    throw new UnsupportedTemporalTypeException("Unsupported unit: " + unit);
            }
        }
        return unit.addTo(this, amountToAdd);
    }

    public LocalDate plusYears(long yearsToAdd) {
        if (yearsToAdd == 0) {
            return this;
        }
        int newYear = YEAR.checkValidIntValue(year + yearsToAdd);
        return resolvePreviousValid(newYear, month, day);
    }

    public LocalDate plusMonths(long monthsToAdd) {
        if (monthsToAdd == 0) {
            return this;
        }
        long monthCount = year * 12L + (month - 1);
        long calcMonths = monthCount + monthsToAdd;
        int newYear = YEAR.checkValidIntValue(Math.floorDiv(calcMonths, 12));
        int newMonth = (int) Math.floorMod(calcMonths, 12) + 1;
        return resolvePreviousValid(newYear, newMonth, day);
    }

    public LocalDate plusWeeks(long weeksToAdd) {
        return plusDays(Math.multiplyExact(weeksToAdd, 7));
    }

    public LocalDate plusDays(long daysToAdd) {
        if (daysToAdd == 0) {
            return this;
        }
        long dom = day + daysToAdd;
        if (dom > 0) {
            if (dom <= 28) {
                return new LocalDate(year, month, (int) dom);
            } else if (dom <= 59) {
                long monthLen = lengthOfMonth();
                if (dom <= monthLen) {
                    return new LocalDate(year, month, (int) dom);
                } else if (month < 12) {
                    return new LocalDate(year, month + 1, (int) (dom - monthLen));
                } else {
                    YEAR.checkValidValue(year + 1);
                    return new LocalDate(year + 1, 1, (int) (dom - monthLen));
                }
            }
        }
        long mjDay = Math.addExact(toEpochDay(), daysToAdd);
        return LocalDate.ofEpochDay(mjDay);
    }

    public LocalDate minus(TemporalAmount amountToSubtract) {
        if (amountToSubtract instanceof Period) {
            Period periodToSubtract = (Period) amountToSubtract;
            return minusMonths(periodToSubtract.toTotalMonths()).minusDays(periodToSubtract.getDays());
        }
        Objects.requireNonNull(amountToSubtract, "amountToSubtract");
        return (LocalDate) amountToSubtract.subtractFrom(this);
    }

    public LocalDate minus(long amountToSubtract, TemporalUnit unit) {
        return (amountToSubtract == Long.MIN_VALUE ? plus(Long.MAX_VALUE, unit).plus(1, unit)
                : plus(-amountToSubtract, unit));
    }

    public LocalDate minusYears(long yearsToSubtract) {
        return (yearsToSubtract == Long.MIN_VALUE ? plusYears(Long.MAX_VALUE).plusYears(1)
                : plusYears(-yearsToSubtract));
    }

    public LocalDate minusMonths(long monthsToSubtract) {
        return (monthsToSubtract == Long.MIN_VALUE ? plusMonths(Long.MAX_VALUE).plusMonths(1)
                : plusMonths(-monthsToSubtract));
    }

    public LocalDate minusWeeks(long weeksToSubtract) {
        return (weeksToSubtract == Long.MIN_VALUE ? plusWeeks(Long.MAX_VALUE).plusWeeks(1)
                : plusWeeks(-weeksToSubtract));
    }

    public LocalDate minusDays(long daysToSubtract) {
        return (daysToSubtract == Long.MIN_VALUE ? plusDays(Long.MAX_VALUE).plusDays(1) : plusDays(-daysToSubtract));
    }

    @SuppressWarnings("unchecked")
    public <R> R query(TemporalQuery<R> query) {
        if (query == TemporalQueries.localDate()) {
            return (R) this;
        }
        return ChronoLocalDate.super.query(query);
    }

    public Temporal adjustInto(Temporal temporal) {
        return ChronoLocalDate.super.adjustInto(temporal);
    }

    public long until(Temporal endExclusive, TemporalUnit unit) {
        LocalDate end = LocalDate.from(endExclusive);
        if (unit instanceof ChronoUnit) {
            switch ((ChronoUnit) unit) {
                case DAYS:
                    return daysUntil(end);
                case WEEKS:
                    return daysUntil(end) / 7;
                case MONTHS:
                    return monthsUntil(end);
                case YEARS:
                    return monthsUntil(end) / 12;
                case DECADES:
                    return monthsUntil(end) / 120;
                case CENTURIES:
                    return monthsUntil(end) / 1200;
                case MILLENNIA:
                    return monthsUntil(end) / 12000;
                case ERAS:
                    return end.getLong(ERA) - getLong(ERA);
                default:
                    throw new UnsupportedTemporalTypeException("Unsupported unit: " + unit);
            }
        }
        return unit.between(this, end);
    }

    long daysUntil(LocalDate end) {
        return end.toEpochDay() - toEpochDay();
    }

    private long monthsUntil(LocalDate end) {
        long packed1 = getProlepticMonth() * 32L + getDayOfMonth();
        long packed2 = end.getProlepticMonth() * 32L + end.getDayOfMonth();
        return (packed2 - packed1) / 32;
    }

    public Period until(ChronoLocalDate endDateExclusive) {
        LocalDate end = LocalDate.from(endDateExclusive);
        long totalMonths = end.getProlepticMonth() - this.getProlepticMonth();
        int days = end.day - this.day;
        if (totalMonths > 0 && days < 0) {
            totalMonths--;
            LocalDate calcDate = this.plusMonths(totalMonths);
            days = (int) (end.toEpochDay() - calcDate.toEpochDay());
        } else if (totalMonths < 0 && days > 0) {
            totalMonths++;
            days -= end.lengthOfMonth();
        }
        long years = totalMonths / 12;
        int months = (int) (totalMonths % 12);
        return Period.of(Math.toIntExact(years), months, days);
    }

    public Stream<LocalDate> datesUntil(LocalDate endExclusive) {
        long end = endExclusive.toEpochDay();
        long start = toEpochDay();
        if (end < start) {
            throw new IllegalArgumentException(endExclusive + " < " + this);
        }
        List<LocalDate> dates = new ArrayList<>();
        for (long d = start; d < end; d++) {
            dates.add(LocalDate.ofEpochDay(d));
        }
        return dates.stream();
    }

    public Stream<LocalDate> datesUntil(LocalDate endExclusive, Period step) {
        if (step.isZero()) {
            throw new IllegalArgumentException("step is zero");
        }
        long end = endExclusive.toEpochDay();
        long start = toEpochDay();
        long until = end - start;
        long months = step.toTotalMonths();
        long days = step.getDays();
        if ((months < 0 && days > 0) || (months > 0 && days < 0)) {
            throw new IllegalArgumentException("period months and days are of opposite sign");
        }
        if (until == 0) {
            return new ArrayList<LocalDate>().stream();
        }
        int sign = months > 0 || days > 0 ? 1 : -1;
        if (sign < 0 ^ until < 0) {
            throw new IllegalArgumentException(endExclusive + (sign < 0 ? " > " : " < ") + this);
        }
        List<LocalDate> dates = new ArrayList<>();
        if (months == 0) {
            for (long d = start; sign > 0 ? d < end : d > end; d += days) {
                dates.add(LocalDate.ofEpochDay(d));
            }
            return dates.stream();
        }
        for (long i = 0; ; i++) {
            LocalDate date = plusMonths(months * i).plusDays(days * i);
            if (sign > 0 ? date.toEpochDay() >= end : date.toEpochDay() <= end) {
                break;
            }
            dates.add(date);
        }
        return dates.stream();
    }

    public String format(DateTimeFormatter formatter) {
        Objects.requireNonNull(formatter, "formatter");
        return formatter.format(this);
    }

    public LocalDateTime atTime(LocalTime time) {
        return LocalDateTime.of(this, time);
    }

    public LocalDateTime atTime(int hour, int minute) {
        return atTime(LocalTime.of(hour, minute));
    }

    public LocalDateTime atTime(int hour, int minute, int second) {
        return atTime(LocalTime.of(hour, minute, second));
    }

    public LocalDateTime atTime(int hour, int minute, int second, int nanoOfSecond) {
        return atTime(LocalTime.of(hour, minute, second, nanoOfSecond));
    }

    public OffsetDateTime atTime(OffsetTime time) {
        return OffsetDateTime.of(LocalDateTime.of(this, time.toLocalTime()), time.getOffset());
    }

    public LocalDateTime atStartOfDay() {
        return LocalDateTime.of(this, LocalTime.MIDNIGHT);
    }

    public ZonedDateTime atStartOfDay(ZoneId zone) {
        Objects.requireNonNull(zone, "zone");
        LocalDateTime ldt = atTime(LocalTime.MIDNIGHT);
        if (zone instanceof ZoneOffset == false) {
            ZoneRules rules = zone.getRules();
            ZoneOffsetTransition trans = rules.getTransition(ldt);
            if (trans != null && trans.isGap()) {
                ldt = trans.getDateTimeAfter();
            }
        }
        return ZonedDateTime.of(ldt, zone);
    }

    public long toEpochDay() {
        long y = year;
        long m = month;
        long total = 0;
        total += 365 * y;
        if (y >= 0) {
            total += (y + 3) / 4 - (y + 99) / 100 + (y + 399) / 400;
        } else {
            total -= y / -4 - y / -100 + y / -400;
        }
        total += ((367 * m - 362) / 12);
        total += day - 1;
        if (m > 2) {
            total--;
            if (isLeapYear() == false) {
                total--;
            }
        }
        return total - DAYS_0000_TO_1970;
    }

    public long toEpochSecond(LocalTime time, ZoneOffset offset) {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(offset, "offset");
        long secs = toEpochDay() * 86400L + time.toSecondOfDay();
        secs -= offset.getTotalSeconds();
        return secs;
    }

    public int compareTo(ChronoLocalDate other) {
        if (other instanceof LocalDate) {
            return compareTo0((LocalDate) other);
        }
        return ChronoLocalDate.super.compareTo(other);
    }

    int compareTo0(LocalDate otherDate) {
        int cmp = (year - otherDate.year);
        if (cmp == 0) {
            cmp = (month - otherDate.month);
            if (cmp == 0) {
                cmp = (day - otherDate.day);
            }
        }
        return cmp;
    }

    public boolean isAfter(ChronoLocalDate other) {
        if (other instanceof LocalDate) {
            return compareTo0((LocalDate) other) > 0;
        }
        return ChronoLocalDate.super.isAfter(other);
    }

    public boolean isBefore(ChronoLocalDate other) {
        if (other instanceof LocalDate) {
            return compareTo0((LocalDate) other) < 0;
        }
        return ChronoLocalDate.super.isBefore(other);
    }

    public boolean isEqual(ChronoLocalDate other) {
        if (other instanceof LocalDate) {
            return compareTo0((LocalDate) other) == 0;
        }
        return ChronoLocalDate.super.isEqual(other);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof LocalDate) {
            return compareTo0((LocalDate) obj) == 0;
        }
        return false;
    }

    public int hashCode() {
        int yearValue = year;
        int monthValue = month;
        int dayValue = day;
        return (yearValue & 0xFFFFF800) ^ ((yearValue << 11) + (monthValue << 6) + (dayValue));
    }

    public String toString() {
        StringBuilder buf = new StringBuilder(10);
        appendDate(buf);
        return buf.toString();
    }

    void appendDate(StringBuilder buf) {
        int yearValue = year;
        int monthValue = month;
        int dayValue = day;
        int absYear = Math.abs(yearValue);
        int start = buf.length();
        if (absYear < 1000) {
            if (yearValue < 0) {
                buf.append(yearValue - 10000).deleteCharAt(start + 1);
            } else {
                buf.append(yearValue + 10000).deleteCharAt(start);
            }
        } else {
            if (yearValue > 9999) {
                buf.append('+');
            }
            buf.append(yearValue);
        }
        buf.append(monthValue < 10 ? "-0" : "-").append(monthValue).append(dayValue < 10 ? "-0" : "-")
                .append(dayValue);
    }
}
