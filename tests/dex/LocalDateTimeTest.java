import java.time.*;
import java.time.chrono.*;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.format.TextStyle;
import java.time.temporal.*;
import java.time.zone.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * java.time dates and times: LocalDate/LocalTime/LocalDateTime arithmetic, Period, Year/YearMonth/MonthDay,
 * zones and offsets, ZoneRules with explicit transitions, ISO parsing, adjusters, IsoFields, chrono interfaces and
 * java.util/java.sql interop. Everything is deterministic (no now()). Run on OpenJDK and on switchapk by
 * tests/run_dex_test.sh, output must match.
 */
public class LocalDateTimeTest {
    static void p(Object o) {
        System.out.println(o);
    }

    static String t(Supplier<Object> s) {
        try {
            return String.valueOf(s.get());
        } catch (RuntimeException e) {
            return "!" + e.getClass().getName() + ": " + e.getMessage();
        }
    }

    static String parse(Function<String, Object> f, String s) {
        try {
            return String.valueOf(f.apply(s));
        } catch (DateTimeParseException e) {
            return "!DTPE idx=" + e.getErrorIndex() + " " + e.getMessage();
        } catch (RuntimeException e) {
            return "!" + e.getClass().getName() + ": " + e.getMessage();
        }
    }

    public static void main(String[] args) {
        localDate();
        localTime();
        localDateTime();
        period();
        yearMonthDay();
        enums();
        offsetsAndZones();
        zoneRules();
        zonedAndOffset();
        parsing();
        adjustersAndFields();
        chrono();
        clocks();
        interop();
    }

    static void localDate() {
        p("== LocalDate");
        int[] years = {1900, 2000, 2023, 2024, 2100, 0, -1, -4, -100, -400, 1, 999, 1000, 9999, 10000, -10000,
            -999999999, 999999999};
        for (int y : years) {
            LocalDate d = LocalDate.of(y, 3, 1);
            p(y + " leap=" + d.isLeapYear() + " lenY=" + d.lengthOfYear() + " feb=" + d.withMonth(2).lengthOfMonth()
                    + " str=" + d + " epoch=" + d.toEpochDay() + " dow=" + d.getDayOfWeek() + " doy=" + d.getDayOfYear()
                    + " era=" + d.getEra() + " yoe=" + d.get(ChronoField.YEAR_OF_ERA) + " hash=" + d.hashCode());
        }
        long[] days = {0, -1, 1, 59, 60, 365, -719528, -719529, 10957, 11016, 2932896, -365243219162L, 365241780471L,
            -678941, 100000, -100000};
        for (long e : days) {
            LocalDate d = LocalDate.ofEpochDay(e);
            p("epoch " + e + " -> " + d + " back=" + d.toEpochDay() + " pm=" + d.getLong(ChronoField.PROLEPTIC_MONTH)
                    + " awm=" + d.get(ChronoField.ALIGNED_WEEK_OF_MONTH) + " awy=" + d.get(ChronoField.ALIGNED_WEEK_OF_YEAR)
                    + " adm=" + d.get(ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH)
                    + " ady=" + d.get(ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR));
        }
        p(t(() -> LocalDate.ofEpochDay(365241780472L)));
        p(t(() -> LocalDate.of(2023, 2, 29)));
        p(t(() -> LocalDate.of(2024, 2, 30)));
        p(t(() -> LocalDate.of(2023, 4, 31)));
        p(t(() -> LocalDate.of(2023, 13, 1)));
        p(t(() -> LocalDate.of(2023, 1, 0)));
        p(t(() -> LocalDate.of(1000000000, 1, 1)));
        p(t(() -> LocalDate.of(2023, Month.JUNE, 31)));
        p(t(() -> LocalDate.ofYearDay(2023, 366)));
        p(t(() -> LocalDate.ofYearDay(2024, 366)));
        p(t(() -> LocalDate.ofYearDay(2024, 60)));
        p(t(() -> LocalDate.ofYearDay(2023, 0)));
        p(LocalDate.MIN + " " + LocalDate.MAX + " " + LocalDate.EPOCH);

        LocalDate jan31 = LocalDate.of(2023, 1, 31);
        for (int m = -14; m <= 14; m++) {
            p("jan31 plusMonths " + m + " = " + jan31.plusMonths(m));
        }
        LocalDate feb29 = LocalDate.of(2024, 2, 29);
        p(feb29.plusYears(1) + " " + feb29.plusYears(4) + " " + feb29.minusYears(1) + " " + feb29.withYear(2023)
                + " " + feb29.plusYears(-2024) + " " + feb29.plus(Period.of(1, 1, 1)) + " " + feb29.minus(Period.ofMonths(13)));
        for (long d : new long[] {1, 27, 28, 29, 30, 31, 59, 60, 306, 307, 366, -1, -29, -30, -60, -366, 1000000}) {
            p("feb29 plusDays " + d + " = " + feb29.plusDays(d) + " / dec31 " + LocalDate.of(2023, 12, 31).plusDays(d));
        }
        p(feb29.plusWeeks(-3) + " " + feb29.plus(2, ChronoUnit.DECADES) + " " + feb29.plus(3, ChronoUnit.CENTURIES)
                + " " + feb29.plus(1, ChronoUnit.MILLENNIA) + " " + t(() -> feb29.plus(1, ChronoUnit.ERAS)) + " "
                + feb29.minus(1, ChronoUnit.ERAS));
        p(t(() -> feb29.plus(1, ChronoUnit.HOURS)));
        p(t(() -> feb29.plus(1, ChronoUnit.FOREVER)));
        p(t(() -> LocalDate.MAX.plusDays(1)));
        p(t(() -> LocalDate.MIN.minusDays(1)));
        p(t(() -> LocalDate.MAX.plusMonths(1)));
        for (ChronoField f : ChronoField.values()) {
            p(f + " supported=" + feb29.isSupported(f) + " range=" + t(() -> feb29.range(f)) + " get="
                    + t(() -> feb29.get(f)) + " getLong=" + t(() -> feb29.getLong(f)));
        }
        long[][] withs = {{0, 1}, {0, 7}, {1, 3}, {2, 5}, {3, 1}, {3, 31}, {4, 1}, {4, 366}, {5, 0}, {6, 2}, {7, 50},
            {8, 11}, {9, 24000}, {10, 5}, {11, -5}, {12, 0}, {12, 1}, {3, 30}, {8, 13}};
        ChronoField[] wf = {ChronoField.DAY_OF_WEEK, ChronoField.DAY_OF_WEEK, ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH,
            ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR, ChronoField.DAY_OF_MONTH, ChronoField.DAY_OF_MONTH,
            ChronoField.DAY_OF_YEAR, ChronoField.DAY_OF_YEAR, ChronoField.EPOCH_DAY, ChronoField.ALIGNED_WEEK_OF_MONTH,
            ChronoField.ALIGNED_WEEK_OF_YEAR, ChronoField.MONTH_OF_YEAR, ChronoField.PROLEPTIC_MONTH,
            ChronoField.YEAR_OF_ERA, ChronoField.YEAR, ChronoField.ERA, ChronoField.ERA};
        for (int i = 0; i < withs.length; i++) {
            ChronoField f = i < wf.length ? wf[i] : (withs[i][0] == 3 ? ChronoField.DAY_OF_MONTH : ChronoField.MONTH_OF_YEAR);
            long v = withs[i][1];
            p("with " + f + " " + v + " = " + t(() -> feb29.with(f, v)));
        }
        p(t(() -> feb29.with(ChronoField.HOUR_OF_DAY, 1)));
        p(t(() -> feb29.withDayOfMonth(30)));
        p(t(() -> feb29.withDayOfYear(367)));

        LocalDate[] ends = {LocalDate.of(2024, 3, 28), LocalDate.of(2024, 3, 29), LocalDate.of(2025, 2, 28),
            LocalDate.of(2025, 3, 1), LocalDate.of(2023, 1, 30), LocalDate.of(1999, 12, 31), LocalDate.of(-5, 6, 15),
            LocalDate.of(2024, 2, 29), LocalDate.of(3024, 2, 28)};
        for (LocalDate e : ends) {
            StringBuilder sb = new StringBuilder("until " + e + ":");
            for (ChronoUnit u : new ChronoUnit[] {ChronoUnit.DAYS, ChronoUnit.WEEKS, ChronoUnit.MONTHS, ChronoUnit.YEARS,
                ChronoUnit.DECADES, ChronoUnit.CENTURIES, ChronoUnit.MILLENNIA, ChronoUnit.ERAS}) {
                sb.append(' ').append(u).append('=').append(feb29.until(e, u));
            }
            sb.append(" period=").append(feb29.until(e)).append(" back=").append(e.until(feb29))
                    .append(" between=").append(Period.between(e, feb29)).append(" cmp=").append(feb29.compareTo(e))
                    .append(feb29.isBefore(e)).append(feb29.isAfter(e)).append(feb29.isEqual(e)).append(feb29.equals(e));
            p(sb);
        }
        p(t(() -> feb29.until(LocalTime.NOON, ChronoUnit.DAYS)));
        p(t(() -> feb29.until(LocalDate.of(2025, 1, 1), ChronoUnit.HOURS)));
        p(feb29.until(LocalDateTime.of(2024, 3, 5, 1, 0), ChronoUnit.DAYS));
        p(feb29.atStartOfDay() + " " + feb29.atTime(10, 15) + " " + feb29.atTime(10, 15, 30) + " "
                + feb29.atTime(10, 15, 30, 5000) + " " + feb29.atTime(OffsetTime.of(1, 2, 3, 4, ZoneOffset.ofHours(-3)))
                + " " + feb29.atStartOfDay(ZoneOffset.ofHours(5)) + " " + feb29.atStartOfDay(ZoneId.of("UTC")));
        p(feb29.toEpochSecond(LocalTime.of(12, 0), ZoneOffset.ofHours(2)));
        p(feb29.datesUntil(LocalDate.of(2024, 3, 4)).collect(java.util.stream.Collectors.toList()));
        p(feb29.datesUntil(LocalDate.of(2024, 9, 1), Period.ofMonths(2)).collect(java.util.stream.Collectors.toList()));
        p(t(() -> feb29.datesUntil(LocalDate.of(2024, 1, 1))));
        p(LocalDate.from(LocalDateTime.of(2020, 5, 6, 7, 8)) + " " + LocalDate.from(feb29.atStartOfDay(ZoneOffset.UTC)));
        p(t(() -> LocalDate.from(LocalTime.of(10, 0))));
        p(t(() -> LocalDate.from(Year.of(2020))));
        List<LocalDate> sorted = new ArrayList<>(Arrays.asList(ends));
        Collections.sort(sorted);
        p(sorted);
    }

    static void localTime() {
        p("== LocalTime");
        LocalTime[] ts = {LocalTime.MIDNIGHT, LocalTime.NOON, LocalTime.MAX, LocalTime.of(1, 2), LocalTime.of(1, 2, 3),
            LocalTime.of(1, 2, 3, 400_000_000), LocalTime.of(1, 2, 3, 450_000), LocalTime.of(1, 2, 3, 7),
            LocalTime.of(23, 0, 0, 1), LocalTime.ofSecondOfDay(45296), LocalTime.ofNanoOfDay(1),
            LocalTime.of(0, 0, 0, 100_000_000)};
        for (LocalTime lt : ts) {
            p(lt + " sod=" + lt.toSecondOfDay() + " nod=" + lt.toNanoOfDay() + " hash=" + lt.hashCode() + " ampm="
                    + lt.get(ChronoField.AMPM_OF_DAY) + " ch=" + lt.get(ChronoField.CLOCK_HOUR_OF_AMPM) + "/"
                    + lt.get(ChronoField.CLOCK_HOUR_OF_DAY) + " mod=" + lt.get(ChronoField.MILLI_OF_DAY) + " mic="
                    + lt.getLong(ChronoField.MICRO_OF_DAY));
        }
        LocalTime x = LocalTime.of(22, 45, 30, 123_456_789);
        for (ChronoField f : ChronoField.values()) {
            p(f + " " + x.isSupported(f) + " " + t(() -> x.get(f)) + " " + t(() -> x.getLong(f)) + " "
                    + t(() -> x.range(f)));
        }
        long[] amounts = {0, 1, -1, 25, -25, 1441, 86401, 1000000007L, Long.MAX_VALUE, Long.MIN_VALUE};
        for (long a : amounts) {
            p("plus " + a + ": h=" + x.plusHours(a) + " m=" + x.plusMinutes(a) + " s=" + x.plusSeconds(a) + " n="
                    + x.plusNanos(a) + " mh=" + x.minusHours(a) + " ms=" + x.minusSeconds(a) + " ms2="
                    + t(() -> x.minus(a, ChronoUnit.MILLIS)) + " us=" + x.plus(a, ChronoUnit.MICROS) + " hd="
                    + x.plus(a, ChronoUnit.HALF_DAYS));
        }
        for (ChronoUnit u : ChronoUnit.values()) {
            p("trunc " + u + " " + t(() -> x.truncatedTo(u)) + " until " + t(() -> x.until(LocalTime.of(1, 2, 3), u))
                    + " sup=" + x.isSupported(u));
        }
        p(t(() -> x.truncatedTo(ChronoUnit.valueOf("DAYS"))) + " " + t(() -> x.truncatedTo(Duration.ofMinutes(7).isZero() ? null : ChronoUnit.MINUTES)));
        p(x.with(ChronoField.HOUR_OF_AMPM, 3) + " " + x.with(ChronoField.CLOCK_HOUR_OF_AMPM, 12) + " "
                + x.with(ChronoField.AMPM_OF_DAY, 0) + " " + x.with(ChronoField.CLOCK_HOUR_OF_DAY, 24) + " "
                + x.with(ChronoField.MINUTE_OF_DAY, 61) + " " + x.with(ChronoField.SECOND_OF_DAY, 3661) + " "
                + x.with(ChronoField.MILLI_OF_SECOND, 5) + " " + x.with(ChronoField.MICRO_OF_DAY, 5) + " "
                + x.withHour(0).withMinute(0).withSecond(0).withNano(0));
        p(t(() -> LocalTime.of(24, 0)));
        p(t(() -> LocalTime.of(1, 60)));
        p(t(() -> LocalTime.of(1, 1, 1, 1_000_000_000)));
        p(t(() -> LocalTime.ofSecondOfDay(86400)));
        p(t(() -> x.with(ChronoField.DAY_OF_MONTH, 1)));
        p(t(() -> x.plus(1, ChronoUnit.DAYS)));
        p(t(() -> x.get(ChronoField.NANO_OF_DAY)));
        p(x.compareTo(LocalTime.NOON) + " " + x.isAfter(LocalTime.NOON) + " " + x.isBefore(LocalTime.MAX) + " "
                + x.atDate(LocalDate.of(2001, 2, 3)) + " " + x.atOffset(ZoneOffset.ofHoursMinutes(-9, -30)) + " "
                + x.toEpochSecond(LocalDate.of(2001, 2, 3), ZoneOffset.UTC));
        p(LocalTime.ofInstant(Instant.ofEpochSecond(-1, 5), ZoneOffset.ofHours(3)) + " "
                + LocalTime.from(LocalDateTime.of(2000, 1, 1, 5, 6)));
    }

    static void localDateTime() {
        p("== LocalDateTime");
        LocalDateTime dt = LocalDateTime.of(2024, 2, 29, 23, 59, 59, 999_999_999);
        p(dt + " " + dt.hashCode() + " " + LocalDateTime.MIN + " " + LocalDateTime.MAX);
        p(dt.plusNanos(1) + " " + dt.plusSeconds(1) + " " + dt.plusMinutes(-1441) + " " + dt.plusHours(25) + " "
                + dt.minusHours(48) + " " + dt.plusDays(366) + " " + dt.plusWeeks(1) + " " + dt.plusMonths(12) + " "
                + dt.minusYears(4) + " " + dt.minusNanos(Long.MAX_VALUE) + " " + dt.plus(1000, ChronoUnit.MILLIS) + " "
                + dt.plus(-3, ChronoUnit.HALF_DAYS) + " " + dt.plus(Duration.ofSeconds(90061, 1)) + " "
                + dt.minus(Period.of(1, 2, 3)) + " " + dt.plus(1L << 40, ChronoUnit.MICROS));
        LocalDateTime[] ends = {LocalDateTime.of(2024, 3, 1, 0, 0), LocalDateTime.of(2024, 2, 29, 23, 59),
            LocalDateTime.of(2023, 2, 28, 23, 59, 59, 999_999_999), LocalDateTime.of(2025, 3, 1, 23, 59, 59, 999_999_998),
            LocalDateTime.of(1970, 1, 1, 0, 0)};
        for (LocalDateTime e : ends) {
            StringBuilder sb = new StringBuilder("until " + e + ":");
            for (ChronoUnit u : ChronoUnit.values()) {
                sb.append(' ').append(u).append('=').append(t(() -> dt.until(e, u)));
            }
            sb.append(" cmp=").append(dt.compareTo(e)).append(dt.isAfter(e)).append(dt.isBefore(e)).append(dt.isEqual(e));
            sb.append(" dur=").append(Duration.between(dt, e));
            p(sb);
        }
        for (ChronoUnit u : new ChronoUnit[] {ChronoUnit.NANOS, ChronoUnit.MILLIS, ChronoUnit.SECONDS, ChronoUnit.HOURS,
            ChronoUnit.HALF_DAYS, ChronoUnit.DAYS, ChronoUnit.WEEKS}) {
            p("trunc " + u + " " + t(() -> dt.truncatedTo(u)));
        }
        p(dt.with(ChronoField.DAY_OF_YEAR, 1) + " " + dt.with(LocalTime.NOON) + " " + dt.with(LocalDate.of(1, 1, 1)) + " "
                + dt.with(TemporalAdjusters.lastDayOfYear()) + " " + dt.withDayOfMonth(1).withMonth(4) + " "
                + dt.toLocalDate() + " " + dt.toLocalTime() + " " + dt.getDayOfWeek() + " " + dt.getDayOfYear());
        p(dt.toEpochSecond(ZoneOffset.ofHours(-8)) + " " + dt.toInstant(ZoneOffset.ofHours(-8)) + " "
                + dt.atOffset(ZoneOffset.ofHours(14)) + " " + dt.atZone(ZoneOffset.MIN) + " "
                + LocalDateTime.ofEpochSecond(-1, 1, ZoneOffset.ofHoursMinutes(5, 30)) + " "
                + LocalDateTime.ofInstant(Instant.ofEpochMilli(1700000000123L), ZoneId.of("GMT-03:30")));
        p(t(() -> LocalDateTime.ofEpochSecond(0, 1_000_000_000, ZoneOffset.UTC)));
        p(t(() -> LocalDateTime.from(LocalDate.of(2020, 1, 1))));
        p(t(() -> LocalDateTime.MAX.plusNanos(1)));
        p(LocalDateTime.from(OffsetDateTime.of(dt, ZoneOffset.ofHours(1))));
        p(LocalDateTime.of(-1, 1, 1, 0, 0) + " " + LocalDateTime.of(12345, 12, 31, 1, 2, 3, 40));
    }

    static void period() {
        p("== Period");
        Period[] ps = {Period.ZERO, Period.of(1, 2, 3), Period.ofMonths(-25), Period.ofWeeks(3), Period.ofYears(-1),
            Period.of(0, 14, -40), Period.of(Integer.MAX_VALUE, 11, 0), Period.of(-1, 15, 0)};
        for (Period pr : ps) {
            p(pr + " zero=" + pr.isZero() + " neg=" + pr.isNegative() + " total=" + pr.toTotalMonths() + " norm="
                    + pr.normalized() + " neg=" + t(pr::negated) + " x3=" + t(() -> pr.multipliedBy(3)) + " hash="
                    + pr.hashCode() + " units=" + pr.getUnits() + " chrono=" + pr.getChronology());
        }
        for (String s : new String[] {"P1Y2M3D", "P3W", "-P1Y2M", "p-1y+2m", "P1Y2W3D", "PT1S", "P", "P0D", "+P2D",
            "P2147483648D", "P2147483647W", "P-0D", "P1D2M", "", "-P-1W-1D"}) {
            p("parse " + s + " -> " + parse(Period::parse, s));
        }
        Period pr = Period.of(1, 13, 32);
        p(pr.plusYears(2) + " " + pr.minusMonths(13) + " " + pr.plusDays(-32) + " " + pr.withDays(0).withMonths(0).withYears(0)
                + " " + pr.plus(Period.ofDays(1)) + " " + pr.minus(Period.of(1, 13, 32)) + " " + pr.get(ChronoUnit.MONTHS));
        p(t(() -> pr.get(ChronoUnit.WEEKS)));
        p(t(() -> pr.plus(Duration.ofDays(1))));
        p(t(() -> Period.from(Duration.ofDays(1))));
        p(t(() -> Period.ofYears(Integer.MAX_VALUE).plusYears(1)));
        p(LocalDate.of(2023, 1, 31).plus(Period.ofMonths(1)) + " " + Period.ofMonths(1).addTo(LocalDate.of(2023, 1, 31))
                + " " + Period.of(1, 1, 1).subtractFrom(LocalDateTime.of(2024, 3, 31, 10, 0)));
        p(t(() -> LocalTime.NOON.plus(Period.ofDays(1))));
        p(Period.between(LocalDate.of(2020, 5, 31), LocalDate.of(2020, 6, 30)) + " "
                + Period.between(LocalDate.of(2020, 6, 30), LocalDate.of(2020, 5, 31)) + " "
                + Period.between(LocalDate.of(2020, 1, 31), LocalDate.of(2020, 3, 1)) + " "
                + Period.between(LocalDate.of(2021, 3, 1), LocalDate.of(2020, 1, 31)) + " "
                + ChronoPeriod.between(LocalDate.of(2000, 1, 1), LocalDate.of(2010, 6, 15)));
    }

    static void yearMonthDay() {
        p("== Year/YearMonth/MonthDay");
        for (int y : new int[] {2024, 2023, 0, -1, 1900, 2000}) {
            Year yr = Year.of(y);
            p(yr + " leap=" + yr.isLeap() + " len=" + yr.length() + " d60=" + yr.atDay(60) + " ym=" + yr.atMonth(2)
                    + " md=" + yr.atMonthDay(MonthDay.of(2, 29)) + " valid=" + yr.isValidMonthDay(MonthDay.of(2, 29))
                    + " yoe=" + yr.get(ChronoField.YEAR_OF_ERA) + " era=" + yr.get(ChronoField.ERA) + " plus="
                    + yr.plus(3, ChronoUnit.DECADES) + " minus=" + yr.minusYears(5) + " until="
                    + yr.until(Year.of(2100), ChronoUnit.DECADES) + " hash=" + yr.hashCode());
        }
        p(t(() -> Year.of(1_000_000_000)) + " " + t(() -> Year.of(5).with(ChronoField.ERA, 0)) + " "
                + t(() -> Year.of(5).plus(1, ChronoUnit.MONTHS)) + " " + t(() -> Year.of(5).getLong(ChronoField.MONTH_OF_YEAR))
                + " " + Year.from(LocalDate.of(77, 1, 1)) + " " + t(() -> Year.from(LocalTime.NOON)));
        YearMonth ym = YearMonth.of(2024, 1);
        for (int m = -13; m <= 13; m += 2) {
            YearMonth x = ym.plusMonths(m);
            p(x + " len=" + x.lengthOfMonth() + " end=" + x.atEndOfMonth() + " valid30=" + x.isValidDay(30) + " pm="
                    + x.getLong(ChronoField.PROLEPTIC_MONTH) + " until=" + ym.until(x, ChronoUnit.MONTHS) + "/"
                    + ym.until(x, ChronoUnit.YEARS) + " cmp=" + ym.compareTo(x) + " hash=" + x.hashCode());
        }
        p(YearMonth.of(-5, 3) + " " + YearMonth.of(12345, 11) + " " + YearMonth.of(999, 1) + " "
                + ym.with(ChronoField.PROLEPTIC_MONTH, 0) + " " + ym.withYear(-1) + " " + ym.plus(Period.ofYears(1)) + " "
                + t(() -> ym.plus(Period.ofDays(1))) + " " + t(() -> ym.atDay(32)) + " " + YearMonth.from(LocalDate.of(2001, 9, 9)));
        p(LocalDate.of(2024, 3, 31).with(YearMonth.of(2023, 2)) + " " + LocalDate.of(2024, 2, 29).with(Year.of(2023)) + " "
                + LocalDate.of(2023, 3, 31).with(MonthDay.of(2, 29)) + " " + LocalDate.of(2024, 3, 31).with(Month.APRIL));
        MonthDay md = MonthDay.of(2, 29);
        p(md + " " + md.isValidYear(2023) + " " + md.atYear(2023) + " " + md.atYear(2024) + " " + md.withMonth(4) + " "
                + MonthDay.of(1, 31).with(Month.FEBRUARY) + " " + md.withDayOfMonth(1) + " " + md.range(ChronoField.DAY_OF_MONTH)
                + " " + md.compareTo(MonthDay.of(3, 1)) + " " + md.hashCode() + " " + MonthDay.from(LocalDate.of(5, 12, 25)));
        p(t(() -> MonthDay.of(2, 30)) + " " + t(() -> MonthDay.of(4, 31)) + " " + t(() -> MonthDay.of(13, 1)) + " "
                + t(() -> md.getLong(ChronoField.YEAR)));
    }

    static void enums() {
        p("== DayOfWeek/Month");
        for (DayOfWeek d : DayOfWeek.values()) {
            p(d + " " + d.getValue() + " +1=" + d.plus(1) + " -8=" + d.minus(8) + " +100=" + d.plus(100) + " "
                    + d.getDisplayName(TextStyle.FULL, Locale.US) + " " + d.getDisplayName(TextStyle.SHORT, Locale.US) + " "
                    + d.getDisplayName(TextStyle.NARROW, Locale.US) + " " + d.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.US));
        }
        for (Month m : Month.values()) {
            p(m + " " + m.getValue() + " len=" + m.length(false) + "/" + m.length(true) + " min=" + m.minLength() + " max="
                    + m.maxLength() + " first=" + m.firstDayOfYear(false) + "/" + m.firstDayOfYear(true) + " q="
                    + m.firstMonthOfQuarter() + " +13=" + m.plus(13) + " -1=" + m.minus(1) + " "
                    + m.getDisplayName(TextStyle.FULL, Locale.US) + " " + m.getDisplayName(TextStyle.SHORT, Locale.US) + " "
                    + m.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.US));
        }
        p(t(() -> DayOfWeek.of(0)) + " " + t(() -> Month.of(13)) + " " + DayOfWeek.from(LocalDate.of(2024, 2, 29)) + " "
                + Month.from(LocalDate.of(2024, 2, 29)) + " " + t(() -> DayOfWeek.from(LocalTime.NOON)) + " "
                + t(() -> Month.from(LocalTime.NOON)) + " " + t(() -> Month.MAY.get(ChronoField.DAY_OF_MONTH)));
        for (TextStyle s : TextStyle.values()) {
            p(s + " " + s.isStandalone() + " " + s.asStandalone() + " " + s.asNormal());
        }
        p(IsoEra.CE.getDisplayName(TextStyle.SHORT, Locale.US) + " " + IsoEra.BCE.getDisplayName(TextStyle.FULL, Locale.US)
                + " " + IsoEra.of(0) + " " + t(() -> IsoEra.of(2)));
    }

    static void offsetsAndZones() {
        p("== ZoneOffset/ZoneId");
        for (String s : new String[] {"Z", "+0", "-5", "+05", "+0530", "+05:30", "-08:00:30", "+083045", "+18:00",
            "-18:00", "+18:01", "+19", "05:00", "+5:00", "+05:0", "+05-30", "z", "", "+05:30:", "+1a"}) {
            p("offset " + s + " -> " + t(() -> {
                ZoneOffset o = ZoneOffset.of(s);
                return o + " " + o.getTotalSeconds() + " " + o.getId() + " " + o.hashCode();
            }));
        }
        p(t(() -> ZoneOffset.ofHours(19)) + " " + t(() -> ZoneOffset.ofHoursMinutes(1, -30)) + " "
                + t(() -> ZoneOffset.ofHoursMinutes(-1, 30)) + " " + t(() -> ZoneOffset.ofHoursMinutesSeconds(0, 1, -1)) + " "
                + t(() -> ZoneOffset.ofHoursMinutes(0, 60)) + " " + t(() -> ZoneOffset.ofHoursMinutesSeconds(0, 0, 60)) + " "
                + t(() -> ZoneOffset.ofHoursMinutes(18, 1)) + " " + t(() -> ZoneOffset.ofTotalSeconds(64801)) + " "
                + ZoneOffset.ofTotalSeconds(-1) + " " + ZoneOffset.MIN + " " + ZoneOffset.MAX + " " + ZoneOffset.UTC);
        List<ZoneOffset> offs = new ArrayList<>(Arrays.asList(ZoneOffset.ofHours(3), ZoneOffset.UTC, ZoneOffset.ofHours(-2),
            ZoneOffset.ofHoursMinutes(3, 30)));
        Collections.sort(offs);
        p(offs + " " + (ZoneOffset.of("+01:00") == ZoneOffset.ofHours(1)) + " " + ZoneOffset.UTC.getRules() + " "
                + ZoneOffset.ofHours(2).getRules().isFixedOffset() + " " + ZoneOffset.ofHours(2).normalized() + " "
                + ZoneOffset.from(OffsetTime.of(1, 0, 0, 0, ZoneOffset.ofHours(4))) + " " + t(() -> ZoneOffset.from(LocalDate.MIN)));
        for (String s : new String[] {"Z", "+01:00", "-0130", "UTC", "GMT", "UT", "UTC+01:00", "GMT+2", "UT-05:30",
            "GMT0", "UTC+", "UTC+25", "GMTx", "Foo/Bar", "Europe/Nowhere", "Etc/GMT-1", "Etc/GMT+10", "Etc/UTC", "", "1",
            "+", "Ab", "A", "A/b c", "/Foo", "UTCZ", "EST", "UTC-00:00", "GMT+00", "Etc/GMT+0"}) {
            p("zone '" + s + "' -> " + t(() -> {
                ZoneId z = ZoneId.of(s);
                return z.getClass().getSimpleName().replace("ZoneRegion", "R").replace("ZoneOffset", "O") + " " + z
                        + " " + z.getId() + " norm=" + z.normalized() + " fixed=" + z.getRules().isFixedOffset()
                        + " off=" + z.getRules().getOffset(Instant.EPOCH) + " hash=" + z.hashCode();
            }));
        }
        p(t(() -> ZoneId.of("EST", ZoneId.SHORT_IDS)) + " " + t(() -> ZoneId.of("XYZ", ZoneId.SHORT_IDS)) + " "
                + t(() -> ZoneId.of(null)) + " " + t(() -> ZoneId.ofOffset("UTC", ZoneOffset.ofHours(-3))) + " "
                + t(() -> ZoneId.ofOffset("", ZoneOffset.ofHours(-3))) + " " + t(() -> ZoneId.ofOffset("XX", ZoneOffset.UTC))
                + " " + t(() -> ZoneId.ofOffset("GMT", ZoneOffset.UTC)));
        p(new TreeMap<>(ZoneId.SHORT_IDS));
        Set<String> ids = ZoneId.getAvailableZoneIds();
        p(ids.contains("Europe/Paris") + " " + ids.contains("America/New_York") + " " + ids.contains("UTC") + " "
                + ids.contains("Foo/Bar") + " " + ids.contains("Asia/Kolkata") + " " + (ids.size() > 400));
        p(ZoneId.systemDefault() + " " + ZoneId.of("UTC").equals(ZoneId.of("UTC")) + " "
                + ZoneId.of("UTC").equals(ZoneOffset.UTC) + " " + ZoneId.of("Z").equals(ZoneOffset.UTC) + " "
                + ZoneId.from(ZonedDateTime.of(LocalDateTime.of(2000, 1, 1, 0, 0), ZoneId.of("GMT+1"))) + " "
                + t(() -> ZoneId.from(LocalDate.MIN)) + " " + ZoneId.of("UTC").getDisplayName(TextStyle.SHORT, Locale.US).length());
    }

    static ZoneRules rules() {
        ZoneOffset std = ZoneOffset.ofHours(1);
        ZoneOffset dst = ZoneOffset.ofHours(2);
        List<ZoneOffsetTransition> stdTrans = Arrays.asList(
            ZoneOffsetTransition.of(LocalDateTime.of(1950, 1, 1, 0, 0), ZoneOffset.ofHoursMinutes(0, 30), std));
        List<ZoneOffsetTransition> trans = Arrays.asList(
            ZoneOffsetTransition.of(LocalDateTime.of(1950, 1, 1, 0, 0), ZoneOffset.ofHoursMinutes(0, 30), std),
            ZoneOffsetTransition.of(LocalDateTime.of(2000, 3, 26, 2, 0), std, dst),
            ZoneOffsetTransition.of(LocalDateTime.of(2000, 10, 29, 3, 0), dst, std));
        List<ZoneOffsetTransitionRule> last = Arrays.asList(
            ZoneOffsetTransitionRule.of(Month.MARCH, -1, DayOfWeek.SUNDAY, LocalTime.of(1, 0), false,
                ZoneOffsetTransitionRule.TimeDefinition.UTC, std, std, dst),
            ZoneOffsetTransitionRule.of(Month.OCTOBER, -1, DayOfWeek.SUNDAY, LocalTime.of(1, 0), false,
                ZoneOffsetTransitionRule.TimeDefinition.UTC, std, dst, std));
        return ZoneRules.of(ZoneOffset.ofHoursMinutes(0, 30), ZoneOffset.ofHoursMinutes(0, 30), stdTrans, trans, last);
    }

    static void zoneRules() {
        p("== ZoneRules");
        ZoneRules r = rules();
        p(r + " fixed=" + r.isFixedOffset() + " eq=" + r.equals(rules()) + " hash=" + (r.hashCode() == rules().hashCode()));
        p(r.getTransitions());
        p(r.getTransitionRules());
        LocalDateTime[] ldts = {LocalDateTime.of(1940, 6, 1, 0, 0), LocalDateTime.of(1999, 12, 31, 23, 0),
            LocalDateTime.of(2000, 3, 26, 1, 59), LocalDateTime.of(2000, 3, 26, 2, 0), LocalDateTime.of(2000, 3, 26, 2, 30),
            LocalDateTime.of(2000, 3, 26, 3, 0), LocalDateTime.of(2000, 10, 29, 1, 59), LocalDateTime.of(2000, 10, 29, 2, 0),
            LocalDateTime.of(2000, 10, 29, 2, 59), LocalDateTime.of(2000, 10, 29, 3, 0), LocalDateTime.of(2024, 3, 31, 2, 30),
            LocalDateTime.of(2024, 10, 27, 2, 30), LocalDateTime.of(2024, 7, 1, 12, 0), LocalDateTime.of(2150, 3, 25, 2, 30),
            LocalDateTime.of(2150, 1, 1, 0, 0)};
        for (LocalDateTime l : ldts) {
            p(l + " off=" + r.getOffset(l) + " valid=" + r.getValidOffsets(l) + " trans=" + r.getTransition(l)
                    + " ok2=" + r.isValidOffset(l, ZoneOffset.ofHours(2)));
        }
        long[] secs = {-1000000000L, 954032399L, 954032400L, 972781200L, 1711846800L, 1711846799L, 1729990800L,
            4000000000L, 1600000000L};
        for (long s : secs) {
            Instant i = Instant.ofEpochSecond(s);
            p(i + " off=" + r.getOffset(i) + " std=" + r.getStandardOffset(i) + " dst=" + r.isDaylightSavings(i) + " save="
                    + r.getDaylightSavings(i) + " next=" + r.nextTransition(i) + " prev=" + r.previousTransition(i));
        }
        ZoneOffsetTransition gap = ZoneOffsetTransition.of(LocalDateTime.of(2000, 3, 26, 2, 0), ZoneOffset.ofHours(1),
            ZoneOffset.ofHours(2));
        p(gap + " " + gap.isGap() + " " + gap.isOverlap() + " " + gap.getDuration() + " " + gap.getInstant() + " "
                + gap.toEpochSecond() + " " + gap.getDateTimeBefore() + " " + gap.getDateTimeAfter() + " "
                + gap.isValidOffset(ZoneOffset.ofHours(1)) + " " + gap.equals(ZoneOffsetTransition.of(LocalDateTime.of(2000,
                3, 26, 2, 0), ZoneOffset.ofHours(1), ZoneOffset.ofHours(2))));
        p(t(() -> ZoneOffsetTransition.of(LocalDateTime.of(2000, 1, 1, 0, 0), ZoneOffset.UTC, ZoneOffset.UTC)) + " "
                + t(() -> ZoneOffsetTransition.of(LocalDateTime.of(2000, 1, 1, 0, 0, 0, 1), ZoneOffset.UTC, ZoneOffset.MAX))
                + " " + t(() -> ZoneOffsetTransitionRule.of(Month.MAY, 0, null, LocalTime.MIDNIGHT, false,
                ZoneOffsetTransitionRule.TimeDefinition.WALL, ZoneOffset.UTC, ZoneOffset.UTC, ZoneOffset.MAX)) + " "
                + t(() -> ZoneOffsetTransitionRule.of(Month.MAY, 1, null, LocalTime.NOON, true,
                ZoneOffsetTransitionRule.TimeDefinition.WALL, ZoneOffset.UTC, ZoneOffset.UTC, ZoneOffset.MAX)));
        ZoneOffsetTransitionRule rule = ZoneOffsetTransitionRule.of(Month.APRIL, 1, DayOfWeek.FRIDAY, LocalTime.MIDNIGHT,
            true, ZoneOffsetTransitionRule.TimeDefinition.STANDARD, ZoneOffset.ofHours(-5), ZoneOffset.ofHours(-4),
            ZoneOffset.ofHours(-5));
        ZoneOffsetTransitionRule rule2 = ZoneOffsetTransitionRule.of(Month.FEBRUARY, -3, DayOfWeek.MONDAY,
            LocalTime.of(3, 0), false, ZoneOffsetTransitionRule.TimeDefinition.WALL, ZoneOffset.ofHours(-5),
            ZoneOffset.ofHours(-5), ZoneOffset.ofHours(-4));
        ZoneOffsetTransitionRule rule3 = ZoneOffsetTransitionRule.of(Month.JUNE, 15, null, LocalTime.of(3, 0), false,
            ZoneOffsetTransitionRule.TimeDefinition.WALL, ZoneOffset.ofHours(-5), ZoneOffset.ofHours(-5), ZoneOffset.ofHours(-4));
        for (ZoneOffsetTransitionRule x : new ZoneOffsetTransitionRule[] {rule, rule2, rule3, r.getTransitionRules().get(0)}) {
            p(x + " " + x.createTransition(2023) + " " + x.createTransition(2024) + " " + x.getMonth() + " "
                    + x.getDayOfMonthIndicator() + " " + x.getDayOfWeek() + " " + x.getLocalTime() + " " + x.isMidnightEndOfDay()
                    + " " + x.getTimeDefinition() + " " + x.getStandardOffset() + " " + x.getOffsetBefore() + " " + x.getOffsetAfter());
        }
        p(ZoneOffsetTransitionRule.TimeDefinition.STANDARD.createDateTime(LocalDateTime.of(2000, 1, 1, 0, 0),
            ZoneOffset.ofHours(1), ZoneOffset.ofHours(2)) + " " + ZoneOffsetTransitionRule.TimeDefinition.UTC.createDateTime(
            LocalDateTime.of(2000, 1, 1, 0, 0), ZoneOffset.ofHours(1), ZoneOffset.ofHours(2)));
        ZoneRules fixed = ZoneRules.of(ZoneOffset.ofHours(3));
        p(fixed + " " + fixed.isFixedOffset() + " " + fixed.getOffset(Instant.EPOCH) + " " + fixed.getTransitions() + " "
                + fixed.nextTransition(Instant.EPOCH) + " " + fixed.previousTransition(Instant.EPOCH) + " "
                + fixed.getDaylightSavings(Instant.EPOCH) + " " + fixed.getValidOffsets(LocalDateTime.MIN) + " "
                + fixed.equals(ZoneRules.of(ZoneOffset.ofHours(3))));
        p(t(() -> ZoneId.of("Foo/Bar")) + " " + t(() -> ZoneId.of("Europe/Nowhere").getRules()));
    }

    static void zonedAndOffset() {
        p("== ZonedDateTime/OffsetDateTime/OffsetTime");
        ZoneId[] zones = {ZoneOffset.UTC, ZoneOffset.ofHours(5), ZoneOffset.ofHoursMinutes(-9, -30), ZoneId.of("UTC"),
            ZoneId.of("GMT+02:00"), ZoneId.of("UT-01:15"), ZoneId.of("Etc/GMT-14")};
        LocalDateTime ldt = LocalDateTime.of(2024, 2, 29, 22, 30, 15, 5_000_000);
        for (ZoneId z : zones) {
            ZonedDateTime zdt = ZonedDateTime.of(ldt, z);
            p(zdt + " inst=" + zdt.toInstant() + " es=" + zdt.toEpochSecond() + " off=" + zdt.getOffset() + " odt="
                    + zdt.toOffsetDateTime() + " utc=" + zdt.withZoneSameInstant(ZoneOffset.UTC) + " local="
                    + zdt.withZoneSameLocal(ZoneOffset.UTC) + " fixed=" + zdt.withFixedOffsetZone() + " +1d="
                    + zdt.plusDays(1) + " +25h=" + zdt.plusHours(25) + " -1M=" + zdt.minusMonths(1) + " trunc="
                    + zdt.truncatedTo(ChronoUnit.HOURS) + " hash=" + zdt.hashCode() + " os=" + zdt.get(ChronoField.OFFSET_SECONDS)
                    + " is=" + zdt.getLong(ChronoField.INSTANT_SECONDS) + " early=" + zdt.withEarlierOffsetAtOverlap()
                    + " late=" + zdt.withLaterOffsetAtOverlap());
        }
        ZonedDateTime a = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(10));
        ZonedDateTime b = ZonedDateTime.of(2023, 12, 31, 14, 0, 0, 0, ZoneOffset.UTC);
        p(a.equals(b) + " " + a.isEqual(b) + " " + a.compareTo(b) + " " + a.isBefore(b) + " " + a.isAfter(b) + " "
                + a.until(b, ChronoUnit.HOURS) + " " + a.until(b.plusDays(40), ChronoUnit.MONTHS) + " "
                + a.until(b.plusDays(40), ChronoUnit.DAYS) + " " + Duration.between(a, b.plusMinutes(90)) + " "
                + t(() -> a.get(ChronoField.INSTANT_SECONDS)) + " " + a.range(ChronoField.DAY_OF_MONTH) + " "
                + a.with(ChronoField.OFFSET_SECONDS, 3600) + " " + a.with(ChronoField.INSTANT_SECONDS, 0) + " "
                + a.with(Instant.ofEpochSecond(86400)) + " " + a.with(LocalTime.NOON) + " " + a.withYear(1999));
        p(ZonedDateTime.ofInstant(Instant.ofEpochSecond(-1, 999), ZoneId.of("UTC+3")) + " "
                + ZonedDateTime.ofInstant(LocalDateTime.of(2000, 1, 1, 0, 0), ZoneOffset.ofHours(1), ZoneId.of("GMT")) + " "
                + ZonedDateTime.ofStrict(LocalDateTime.of(2000, 1, 1, 0, 0), ZoneOffset.UTC, ZoneId.of("UTC")) + " "
                + t(() -> ZonedDateTime.ofStrict(LocalDateTime.of(2000, 1, 1, 0, 0), ZoneOffset.ofHours(1), ZoneOffset.UTC))
                + " " + ZonedDateTime.ofLocal(ldt, ZoneId.of("GMT-5"), ZoneOffset.UTC) + " "
                + ZonedDateTime.from(OffsetDateTime.of(ldt, ZoneOffset.ofHours(2))) + " " + t(() -> ZonedDateTime.from(ldt)));
        OffsetDateTime odt = OffsetDateTime.of(ldt, ZoneOffset.ofHours(-7));
        p(odt + " " + odt.toInstant() + " " + odt.toEpochSecond() + " " + odt.withOffsetSameInstant(ZoneOffset.ofHours(3))
                + " " + odt.withOffsetSameLocal(ZoneOffset.ofHours(3)) + " " + odt.toOffsetTime() + " " + odt.toZonedDateTime()
                + " " + odt.atZoneSameInstant(ZoneOffset.UTC) + " " + odt.atZoneSimilarLocal(ZoneId.of("UTC")) + " "
                + odt.plusMonths(1) + " " + odt.minusNanos(5_000_001) + " " + odt.with(ChronoField.OFFSET_SECONDS, -3600)
                + " " + odt.with(ChronoField.INSTANT_SECONDS, 0) + " " + odt.hashCode() + " " + odt.getDayOfWeek() + " "
                + OffsetDateTime.MIN + " " + OffsetDateTime.MAX);
        OffsetDateTime o2 = odt.withOffsetSameInstant(ZoneOffset.UTC);
        List<OffsetDateTime> list = new ArrayList<>(Arrays.asList(o2, odt, odt.plusNanos(1), o2.minusNanos(1)));
        Collections.sort(list);
        p(list + " " + odt.compareTo(o2) + " " + odt.isEqual(o2) + " " + odt.equals(o2) + " "
                + OffsetDateTime.timeLineOrder().compare(odt, o2) + " " + odt.until(o2.plusHours(3), ChronoUnit.MINUTES) + " "
                + OffsetDateTime.ofInstant(Instant.ofEpochSecond(1234567890), ZoneId.of("GMT+08:00")) + " "
                + OffsetDateTime.from(ZonedDateTime.of(ldt, ZoneId.of("UTC"))) + " " + t(() -> OffsetDateTime.from(ldt)));
        OffsetTime ot = OffsetTime.of(23, 30, 0, 0, ZoneOffset.ofHours(-2));
        OffsetTime ot2 = OffsetTime.of(1, 30, 0, 0, ZoneOffset.UTC);
        p(ot + " " + ot.withOffsetSameInstant(ZoneOffset.UTC) + " " + ot.withOffsetSameLocal(ZoneOffset.UTC) + " "
                + ot.compareTo(ot2) + " " + ot.isEqual(ot2) + " " + ot.isAfter(ot2) + " " + ot.until(ot2, ChronoUnit.MINUTES)
                + " " + ot.plusHours(3) + " " + ot.atDate(LocalDate.of(2020, 2, 2)) + " " + ot.toEpochSecond(LocalDate.EPOCH)
                + " " + ot.getLong(ChronoField.OFFSET_SECONDS) + " " + ot.isSupported(ChronoField.DAY_OF_MONTH) + " "
                + ot.hashCode() + " " + OffsetTime.MIN + " " + OffsetTime.MAX + " "
                + OffsetTime.ofInstant(Instant.ofEpochSecond(-30), ZoneOffset.ofHours(1)) + " " + t(() -> ot.plus(1, ChronoUnit.DAYS)));
        Instant i = Instant.ofEpochSecond(1_000_000_000L, 1);
        p(i.atZone(ZoneId.of("UTC")) + " " + i.atOffset(ZoneOffset.ofHours(-11)) + " " + i.atZone(ZoneOffset.ofHours(9)) + " "
                + Instant.from(ZonedDateTime.of(ldt, ZoneOffset.ofHours(1))) + " " + i.query(TemporalQueries.precision()) + " "
                + i.query(TemporalQueries.localDate()) + " " + t(() -> Instant.from(ldt)));
    }

    static void parsing() {
        p("== parse");
        String[] years = {"2024", "-5", "+12345", "12345", "0", "+5", "1234567890", "abc", "", "-0", "2024x", "999999999"};
        for (String s : years) {
            p("Year " + s + " " + parse(Year::parse, s));
        }
        for (String s : new String[] {"2024-02", "+12345-01", "12345-01", "2024-1", "-0005-03", "2024"}) {
            p("YM " + s + " " + parse(YearMonth::parse, s));
        }
        for (String s : new String[] {"2024-13", "2024-00"}) {
            p("YM " + s + " " + t(() -> YearMonth.parse(s)).startsWith("!java.time.format.DateTimeParseException"));
        }
        for (String s : new String[] {"--02-29", "--1-01", "-02-01", "--12-31", "--12-31x"}) {
            p("MD " + s + " " + parse(MonthDay::parse, s));
        }
        for (String s : new String[] {"--02-30", "--13-01", "--04-31"}) {
            p("MD " + s + " " + t(() -> MonthDay.parse(s)).startsWith("!java.time.format.DateTimeParseException"));
        }
        String[] lts = {"10:15", "10:15:30", "10:15:30.", "10:15:30.1", "10:15:30.1234567891", "10:15:3", "24:00", "25:61",
            "23:60", "10:15:60", "1:15", "10:15:30,5", "10:15:30.000", "10:15:30.000001", "00:00:00.123456789", "10-15", "+1:15"};
        for (String s : lts) {
            p("LT " + s + " " + parse(LocalTime::parse, s));
        }
        String[] lds = {"2024-02-29", "2023-02-29", "2024-02-30", "2023-04-31", "2023-13-01", "2023-00-10", "2023-01-32",
            "+2023-01-01", "20231-01-01", "+20231-01-01", "-2023-01-01", "2023-1-01", "2023-01-01T", "abcd", "",
            "+1000000000-01-01", "-0000-01-01", "+999999999-12-31", "0000-01-01", "-0001-12-31", "2023-01-+1",
            "2023/01/01"};
        for (String s : lds) {
            p("LD " + s + " " + parse(LocalDate::parse, s));
        }
        String[] ldts = {"2023-01-01T10:15", "2023-01-01t10:15", "2023-01-01 10:15", "2023-02-30T25:00", "2023-01-01T25:61",
            "2023-01-01T10:15:30.5", "2023-01-01T", "2023-01-01T10"};
        for (String s : ldts) {
            p("LDT " + s + " " + parse(LocalDateTime::parse, s));
        }
        String[] odts = {"2023-01-01T10:15+01:00", "2023-01-01T10:15z", "2023-01-01T10:15Z", "2023-01-01T10:15+01",
            "2023-01-01T10:15+0100", "2023-01-01T10:15+01:00:30", "2023-01-01T10:15+24:00", "2023-01-01T10:15+19:00",
            "2023-01-01T10:15-1:00", "2023-01-01T10:15", "2023-01-01T10:15:00.5-03:30", "2023-01-01T10:15+01:60"};
        for (String s : odts) {
            p("ODT " + s + " " + parse(OffsetDateTime::parse, s));
        }
        for (String s : new String[] {"10:15+01:00", "10:15+01", "10:15Z", "10:15z", "10:15:30.5-05:30", "10:15"}) {
            p("OT " + s + " " + parse(OffsetTime::parse, s));
        }
        String[] zdts = {"2023-01-01T10:15+01:00", "2023-01-01T10:15Z[UTC]", "2023-01-01T10:15+05:00[UTC]",
            "2023-01-01T10:15Z[Foo/Bar]", "2023-01-01T10:15Z[GMT+02:00]", "2023-01-01T10:15Z[+02:00]",
            "2023-01-01T10:15Z[UT]", "2023-01-01T10:15Z[UTC+1]", "2023-01-01T10:15Z[Z]", "2023-01-01T10:15Z[UTC",
            "2023-01-01T10:15+01:00[Etc/GMT-1]", "2023-01-01T10:15Z[GMT]", "2023-01-01T10:15Z[GMT0]",
            "2023-01-01T10:15Z[utc]", "2023-01-01T10:15Z[-05:00]", "2023-01-01T10:15Z[UT-05:00]"};
        for (String s : zdts) {
            p("ZDT " + s + " " + parse(ZonedDateTime::parse, s));
        }
        p(t(() -> LocalDate.parse(null)));
        String longText = "2023-01-01T10:15:00.000000000000000000000000000000000000000000000000000000000000000000000";
        p(parse(LocalDateTime::parse, longText));
        LocalDate[] rt = {LocalDate.of(-1, 1, 1), LocalDate.of(10000, 5, 6), LocalDate.of(-10000, 5, 6), LocalDate.MAX,
            LocalDate.MIN};
        for (LocalDate d : rt) {
            p(d + " " + LocalDate.parse(d.toString()).equals(d));
        }
    }

    static void adjustersAndFields() {
        p("== adjusters/fields/queries");
        LocalDate d = LocalDate.of(2024, 2, 14);
        p(d.with(TemporalAdjusters.firstDayOfMonth()) + " " + d.with(TemporalAdjusters.lastDayOfMonth()) + " "
                + d.with(TemporalAdjusters.firstDayOfNextMonth()) + " " + d.with(TemporalAdjusters.firstDayOfYear()) + " "
                + d.with(TemporalAdjusters.lastDayOfYear()) + " " + d.with(TemporalAdjusters.firstDayOfNextYear()));
        for (DayOfWeek w : DayOfWeek.values()) {
            p(w + " first=" + d.with(TemporalAdjusters.firstInMonth(w)) + " last=" + d.with(TemporalAdjusters.lastInMonth(w))
                    + " 2nd=" + d.with(TemporalAdjusters.dayOfWeekInMonth(2, w)) + " -2=" + d.with(TemporalAdjusters.dayOfWeekInMonth(-2, w))
                    + " 0=" + d.with(TemporalAdjusters.dayOfWeekInMonth(0, w)) + " 6=" + d.with(TemporalAdjusters.dayOfWeekInMonth(6, w))
                    + " next=" + d.with(TemporalAdjusters.next(w)) + " nos=" + d.with(TemporalAdjusters.nextOrSame(w))
                    + " prev=" + d.with(TemporalAdjusters.previous(w)) + " pos=" + d.with(TemporalAdjusters.previousOrSame(w)));
        }
        p(d.with(TemporalAdjusters.ofDateAdjuster(x -> x.plusDays(100))) + " "
                + LocalDateTime.of(2024, 2, 14, 9, 0).with(TemporalAdjusters.lastDayOfMonth()));
        LocalDate[] ws = {LocalDate.of(2004, 12, 31), LocalDate.of(2005, 1, 1), LocalDate.of(2005, 1, 2),
            LocalDate.of(2005, 12, 31), LocalDate.of(2007, 1, 1), LocalDate.of(2008, 12, 29), LocalDate.of(2008, 12, 31),
            LocalDate.of(2009, 12, 31), LocalDate.of(2010, 1, 3), LocalDate.of(2010, 1, 4), LocalDate.of(2020, 12, 31),
            LocalDate.of(2021, 1, 3), LocalDate.of(2024, 2, 29), LocalDate.of(2024, 12, 30), LocalDate.of(1, 1, 1),
            LocalDate.of(-1, 12, 31)};
        for (LocalDate w : ws) {
            p(w + " wowby=" + w.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + " wby=" + w.get(IsoFields.WEEK_BASED_YEAR) + " q="
                    + w.get(IsoFields.QUARTER_OF_YEAR) + " doq=" + w.get(IsoFields.DAY_OF_QUARTER) + " range="
                    + w.range(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + "/" + w.range(IsoFields.DAY_OF_QUARTER) + " jd="
                    + w.getLong(JulianFields.JULIAN_DAY) + " mjd=" + w.getLong(JulianFields.MODIFIED_JULIAN_DAY) + " rd="
                    + w.getLong(JulianFields.RATA_DIE));
        }
        LocalDate w0 = LocalDate.of(2020, 12, 31);
        p(w0.with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, 1) + " " + w0.with(IsoFields.WEEK_BASED_YEAR, 2021) + " "
                + w0.with(IsoFields.QUARTER_OF_YEAR, 1) + " " + w0.with(IsoFields.DAY_OF_QUARTER, 1) + " "
                + w0.plus(1, IsoFields.QUARTER_YEARS) + " " + w0.plus(5, IsoFields.WEEK_BASED_YEARS) + " "
                + w0.until(LocalDate.of(2023, 1, 1), IsoFields.QUARTER_YEARS) + " "
                + IsoFields.WEEK_BASED_YEARS.between(w0, LocalDate.of(2023, 1, 1)) + " " + w0.with(JulianFields.JULIAN_DAY, 2440588)
                + " " + t(() -> LocalTime.NOON.get(IsoFields.QUARTER_OF_YEAR)) + " " + t(() -> w0.with(IsoFields.QUARTER_OF_YEAR, 5))
                + " " + IsoFields.WEEK_OF_WEEK_BASED_YEAR + " " + IsoFields.QUARTER_YEARS + " " + IsoFields.DAY_OF_QUARTER.range()
                + " " + IsoFields.WEEK_BASED_YEAR.isDateBased() + " " + JulianFields.RATA_DIE + " " + JulianFields.JULIAN_DAY.range());
        Map<TemporalField, Long> fv = new HashMap<>();
        fv.put(IsoFields.WEEK_BASED_YEAR, 2020L);
        fv.put(IsoFields.WEEK_OF_WEEK_BASED_YEAR, 53L);
        fv.put(ChronoField.DAY_OF_WEEK, 5L);
        p(IsoFields.WEEK_OF_WEEK_BASED_YEAR.resolve(fv, LocalDate.MIN, ResolverStyle.STRICT) + " " + fv);
        fv.clear();
        fv.put(ChronoField.YEAR, 2023L);
        fv.put(IsoFields.QUARTER_OF_YEAR, 3L);
        fv.put(IsoFields.DAY_OF_QUARTER, 92L);
        p(IsoFields.DAY_OF_QUARTER.resolve(fv, LocalDate.MIN, ResolverStyle.SMART) + " " + fv);

        Object[] temporals = {LocalDate.of(2020, 1, 2), LocalTime.of(3, 4), LocalDateTime.of(2020, 1, 2, 3, 4),
            ZonedDateTime.of(2020, 1, 2, 3, 4, 0, 0, ZoneId.of("UTC")), OffsetDateTime.of(2020, 1, 2, 3, 4, 0, 0, ZoneOffset.UTC),
            OffsetTime.of(3, 4, 0, 0, ZoneOffset.ofHours(1)), Instant.EPOCH, Year.of(2020), YearMonth.of(2020, 1),
            MonthDay.of(1, 2), DayOfWeek.MONDAY, Month.MAY, ZoneOffset.ofHours(2), IsoEra.CE};
        for (Object o : temporals) {
            TemporalAccessor ta = (TemporalAccessor) o;
            p(o.getClass().getSimpleName() + " zoneId=" + ta.query(TemporalQueries.zoneId()) + " zone="
                    + ta.query(TemporalQueries.zone()) + " off=" + ta.query(TemporalQueries.offset()) + " chrono="
                    + ta.query(TemporalQueries.chronology()) + " prec=" + ta.query(TemporalQueries.precision()) + " ld="
                    + t(() -> ta.query(TemporalQueries.localDate())) + " lt=" + t(() -> ta.query(TemporalQueries.localTime())));
            if (o instanceof Temporal) {
                Temporal tm = (Temporal) o;
                StringBuilder sb = new StringBuilder("  units:");
                for (ChronoUnit u : ChronoUnit.values()) {
                    sb.append(tm.isSupported(u) ? '1' : '0');
                }
                sb.append(" fields:");
                for (ChronoField f : ChronoField.values()) {
                    sb.append(tm.isSupported(f) ? '1' : '0');
                }
                p(sb);
            }
        }
        p(TemporalQueries.zoneId() + " " + TemporalQueries.chronology() + " " + TemporalQueries.precision() + " "
                + TemporalQueries.zone() + " " + TemporalQueries.offset() + " " + TemporalQueries.localDate() + " "
                + TemporalQueries.localTime());
    }

    static void chrono() {
        p("== chrono");
        ChronoLocalDate cld = LocalDate.of(2024, 2, 29);
        ChronoLocalDate c2 = cld.plus(1, ChronoUnit.YEARS).with(ChronoField.DAY_OF_MONTH, 1);
        ChronoLocalDateTime<?> cldt = cld.atTime(LocalTime.NOON);
        ChronoZonedDateTime<?> czdt = cldt.atZone(ZoneOffset.ofHours(3));
        p(cld + " " + c2 + " " + c2.getClass().getSimpleName() + " " + cld.getChronology() + " " + cld.getEra() + " "
                + cld.isLeapYear() + " " + cld.lengthOfYear() + " " + cld.until(c2) + " " + cld.compareTo(c2) + " "
                + cld.isAfter(c2) + " " + cld.toEpochDay() + " " + cldt + " " + cldt.toInstant(ZoneOffset.UTC) + " "
                + cldt.getChronology() + " " + czdt + " " + czdt.toInstant() + " " + czdt.toLocalDate() + " "
                + czdt.getChronology());
        p(ChronoLocalDate.timeLineOrder().compare(cld, c2) + " " + ChronoLocalDateTime.timeLineOrder().compare(cldt,
                LocalDateTime.of(2024, 2, 29, 12, 0)) + " " + ChronoZonedDateTime.timeLineOrder().compare(czdt,
                ZonedDateTime.of(2024, 2, 29, 9, 0, 0, 0, ZoneOffset.UTC)) + " " + ChronoLocalDate.from(LocalDateTime.of(2001,
                1, 1, 0, 0)) + " " + ChronoLocalDateTime.from(ZonedDateTime.of(LocalDateTime.of(2001, 1, 1, 0, 0),
                ZoneOffset.UTC)) + " " + ChronoZonedDateTime.from(OffsetDateTime.of(LocalDateTime.of(2001, 1, 1, 0, 0),
                ZoneOffset.UTC)) + " " + t(() -> ChronoLocalDate.from(LocalTime.NOON)));
        IsoChronology iso = IsoChronology.INSTANCE;
        p(iso + " " + iso.getId() + " " + iso.getCalendarType() + " " + iso.date(2020, 2, 29) + " " + iso.date(IsoEra.BCE, 5, 1, 1)
                + " " + iso.dateYearDay(2021, 365) + " " + iso.dateYearDay(IsoEra.CE, 2021, 1) + " " + iso.dateEpochDay(1) + " "
                + iso.isLeapYear(1600) + iso.isLeapYear(1700) + " " + iso.prolepticYear(IsoEra.BCE, 1) + " " + iso.eraOf(1) + " "
                + iso.eras() + " " + iso.range(ChronoField.DAY_OF_MONTH) + " " + iso.period(1, 2, 3) + " "
                + iso.epochSecond(2000, 1, 1, 0, 0, 0, ZoneOffset.ofHours(1)) + " "
                + iso.epochSecond(IsoEra.CE, 1970, 1, 2, 0, 0, 1, ZoneOffset.UTC) + " " + iso.localDateTime(LocalDateTime.MIN) + " "
                + iso.zonedDateTime(Instant.EPOCH, ZoneOffset.ofHours(-1)) + " " + " "
                + iso.getDisplayName(TextStyle.FULL, Locale.US) + " " + iso.date(ZonedDateTime.of(LocalDateTime.of(2001, 2, 3, 4,
                5), ZoneOffset.UTC)) + " " + iso.equals(Chronology.of("ISO")) + " " + iso.compareTo(Chronology.of("ISO")));
        p(Chronology.of("ISO") + " " + t(() -> Chronology.of("Hindu")) + " " + Chronology.from(LocalDate.MIN) + " "
                + Chronology.from(LocalTime.NOON) + " " + Chronology.ofLocale(Locale.US) + " " + t(() -> iso.prolepticYear(null, 1))
                + " " + t(() -> iso.epochSecond(2023, 2, 29, 0, 0, 0, ZoneOffset.UTC)) + " " + t(() -> iso.date(2023, 2, 29)));
        ResolverStyle[] styles = ResolverStyle.values();
        long[][] ymds = {{2024, 2, 30}, {2023, 2, 29}, {2023, 4, 31}, {2023, 13, 1}, {2023, 0, 40}, {2023, 6, 15}};
        for (long[] ymd : ymds) {
            StringBuilder sb = new StringBuilder("resolve " + Arrays.toString(ymd) + ":");
            for (ResolverStyle st : styles) {
                Map<TemporalField, Long> m = new HashMap<>();
                m.put(ChronoField.YEAR, ymd[0]);
                m.put(ChronoField.MONTH_OF_YEAR, ymd[1]);
                m.put(ChronoField.DAY_OF_MONTH, ymd[2]);
                sb.append(' ').append(st).append('=').append(t(() -> iso.resolveDate(m, st))).append(m.size());
            }
            p(sb);
        }
        Map<TemporalField, Long> m = new HashMap<>();
        m.put(ChronoField.YEAR_OF_ERA, 5L);
        m.put(ChronoField.ERA, 0L);
        m.put(ChronoField.DAY_OF_YEAR, 60L);
        p(t(() -> iso.resolveDate(m, ResolverStyle.STRICT)));
        Map<TemporalField, Long> m2 = new HashMap<>();
        m2.put(ChronoField.PROLEPTIC_MONTH, 24287L);
        m2.put(ChronoField.ALIGNED_WEEK_OF_MONTH, 3L);
        m2.put(ChronoField.DAY_OF_WEEK, 3L);
        p(t(() -> iso.resolveDate(m2, ResolverStyle.SMART)));
        Map<TemporalField, Long> m3 = new HashMap<>();
        m3.put(ChronoField.YEAR, 2024L);
        m3.put(ChronoField.ALIGNED_WEEK_OF_YEAR, 53L);
        m3.put(ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR, 3L);
        p(t(() -> iso.resolveDate(new HashMap<>(m3), ResolverStyle.SMART)) + " "
                + t(() -> iso.resolveDate(new HashMap<>(m3), ResolverStyle.STRICT)));
        Map<TemporalField, Long> m4 = new HashMap<>();
        m4.put(ChronoField.YEAR, 2024L);
        m4.put(ChronoField.YEAR_OF_ERA, 2023L);
        m4.put(ChronoField.DAY_OF_YEAR, 1L);
        p(t(() -> iso.resolveDate(m4, ResolverStyle.SMART)));
        Map<TemporalField, Long> m5 = new HashMap<>();
        m5.put(ChronoField.EPOCH_DAY, 19000L);
        p(t(() -> iso.resolveDate(m5, ResolverStyle.STRICT)) + " " + m5);
    }

    static void clocks() {
        p("== Clock");
        Instant base = Instant.parse("2024-02-29T10:15:30.123456789Z");
        Clock fixed = Clock.fixed(base, ZoneId.of("GMT+3"));
        p(fixed + " " + fixed.instant() + " " + fixed.millis() + " " + fixed.getZone() + " " + fixed.withZone(ZoneOffset.UTC)
                + " " + fixed.equals(Clock.fixed(base, ZoneId.of("GMT+3"))) + " " + (fixed.hashCode() == Clock.fixed(base,
                ZoneId.of("GMT+3")).hashCode()));
        p(LocalDate.now(fixed) + " " + LocalTime.now(fixed) + " " + LocalDateTime.now(fixed) + " " + ZonedDateTime.now(fixed)
                + " " + OffsetDateTime.now(fixed) + " " + OffsetTime.now(fixed) + " " + Year.now(fixed) + " " + YearMonth.now(fixed)
                + " " + MonthDay.now(fixed) + " " + Instant.now(fixed) + " " + IsoChronology.INSTANCE.dateNow(fixed));
        Clock off = Clock.offset(fixed, Duration.ofHours(-11));
        p(off + " " + off.instant() + " " + off.millis() + " " + LocalDateTime.now(off) + " " + (Clock.offset(fixed,
                Duration.ZERO) == fixed));
        for (Duration d : new Duration[] {Duration.ofMillis(1), Duration.ofSeconds(1), Duration.ofMinutes(15),
            Duration.ofNanos(1000), Duration.ofNanos(1), Duration.ofDays(1)}) {
            p("tick " + d + " " + t(() -> {
                Clock c = Clock.tick(fixed, d);
                return c.instant() + " " + c.millis();
            }));
        }
        p(t(() -> Clock.tick(fixed, Duration.ofNanos(7))) + " " + t(() -> Clock.tick(fixed, Duration.ofSeconds(-1))) + " "
                + Clock.systemUTC().getZone() + " " + Clock.system(ZoneOffset.UTC).equals(Clock.systemUTC()) + " "
                + Clock.systemDefaultZone().getZone() + " " + Clock.systemUTC() + " " + Clock.tickSeconds(ZoneOffset.UTC).getZone()
                + " " + (Clock.systemUTC().millis() > 1600000000000L));
        InstantSource src = InstantSource.fixed(base);
        p(src.instant() + " " + src.millis() + " " + src.withZone(ZoneOffset.ofHours(1)).getZone() + " "
                + src.withZone(ZoneOffset.ofHours(1)).instant() + " " + InstantSource.offset(src, Duration.ofSeconds(1)).instant()
                + " " + InstantSource.tick(src, Duration.ofSeconds(1)).instant());
    }

    @SuppressWarnings("deprecation")
    static void interop() {
        p("== interop");
        TimeZone tz = TimeZone.getTimeZone(ZoneOffset.ofHours(-5));
        p(tz.getID() + " " + tz.getRawOffset() + " " + TimeZone.getTimeZone(ZoneOffset.UTC).getID() + " "
                + TimeZone.getTimeZone(ZoneId.of("UTC")).getID() + " " + TimeZone.getTimeZone(ZoneId.of("GMT+02:00")).getRawOffset()
                + " " + TimeZone.getTimeZone("UTC").toZoneId() + " " + TimeZone.getTimeZone("GMT+05:30").toZoneId() + " "
                + tz.toZoneId());
        ZonedDateTime zdt = ZonedDateTime.of(2024, 2, 29, 23, 59, 59, 987_654_321, ZoneId.of("GMT+02:00"));
        GregorianCalendar cal = GregorianCalendar.from(zdt);
        p(cal.getTimeInMillis() + " " + cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.MONTH) + "-" + cal.get(Calendar.DAY_OF_MONTH)
                + " " + cal.get(Calendar.HOUR_OF_DAY) + ":" + cal.get(Calendar.MINUTE) + " " + cal.get(Calendar.MILLISECOND) + " "
                + cal.getFirstDayOfWeek() + " " + cal.getMinimalDaysInFirstWeek() + " " + cal.getTimeZone().getID() + " "
                + cal.toZonedDateTime() + " " + cal.toInstant());
        GregorianCalendar g2 = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        g2.setTimeInMillis(-86_400_001L);
        p(g2.toZonedDateTime());
        java.sql.Date sd = java.sql.Date.valueOf(LocalDate.of(2024, 2, 29));
        p(sd + " " + sd.toLocalDate() + " " + t(sd::toInstant) + " " + java.sql.Date.valueOf(LocalDate.of(1, 1, 1)).toLocalDate());
        java.sql.Time st = java.sql.Time.valueOf(LocalTime.of(13, 14, 15, 999));
        p(st + " " + st.toLocalTime() + " " + t(st::toInstant));
        java.sql.Timestamp ts = java.sql.Timestamp.valueOf(LocalDateTime.of(2024, 2, 29, 13, 14, 15, 123_456_789));
        p(ts + " " + ts.toLocalDateTime() + " " + ts.toInstant() + " " + ts.getNanos());
        java.sql.Timestamp ts2 = java.sql.Timestamp.from(Instant.ofEpochSecond(-1, 5));
        p(ts2 + " " + ts2.getTime() + " " + ts2.getNanos() + " " + ts2.toInstant() + " "
                + t(() -> java.sql.Timestamp.from(Instant.ofEpochSecond(253402300799L, 7))));
        Date d = Date.from(Instant.parse("2024-02-29T10:00:00Z"));
        p(d.getTime() + " " + d.toInstant() + " " + LocalDateTime.ofInstant(d.toInstant(), ZoneId.of("UTC")));
    }
}
