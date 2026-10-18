import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.*;
import java.util.*;

/**
 * java.time Duration and Instant, the temporal core (ChronoUnit, ChronoField, ValueRange) and Date/Calendar
 * conversions. Run on OpenJDK and on switchapk by tests/run_dex_test.sh, output must match.
 */
public class TimeTest {
    static void p(Object o) {
        System.out.println(o);
    }

    static String tryIt(java.util.function.Supplier<Object> s) {
        try {
            return String.valueOf(s.get());
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    public static void main(String[] args) {
        // Duration construction and text
        Duration[] ds = {Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(1500), Duration.ofNanos(-1),
            Duration.ofMinutes(90), Duration.ofHours(-25), Duration.ofDays(3).plusNanos(5000), Duration.ofSeconds(-1, 500),
            Duration.ofSeconds(59, 999_999_999), Duration.ofMillis(-1500), Duration.ofSeconds(Long.MAX_VALUE, 999_999_999),
            Duration.ofSeconds(Long.MIN_VALUE)};
        for (Duration d : ds) {
            p(d + " secs=" + d.getSeconds() + " nano=" + d.getNano() + " neg=" + d.isNegative() + " hash=" + d.hashCode()
                    + " parts=" + d.toDaysPart() + "/" + d.toHoursPart() + "/" + d.toMinutesPart() + "/"
                    + d.toSecondsPart() + "/" + d.toMillisPart() + " millis=" + tryIt(d::toMillis)
                    + " nanos=" + tryIt(d::toNanos));
        }
        for (String s : new String[] {"PT20.345S", "PT15M", "PT10H", "P2D", "P2DT3H4M", "PT-6H3M", "-PT6H3M",
            "-PT-6H+3M", "PT-0.5S", "pt1.5s", "PT0.000000001S", "PT", "P", "P1Y", "PT1.S", "PT1,5S", "+PT1S",
            "PT9223372036854775807S", "P106751991167301D", "garbage"}) {
            p("parse " + s + " -> " + tryIt(() -> Duration.parse(s)));
        }
        Duration a = Duration.ofSeconds(100, 300);
        p("arith: " + a.plus(Duration.ofMillis(800)) + " " + a.minus(Duration.ofMinutes(2)) + " " + a.multipliedBy(-3)
                + " " + a.dividedBy(7) + " " + a.dividedBy(Duration.ofSeconds(9)) + " " + a.negated() + " "
                + a.negated().abs() + " " + a.plus(3, ChronoUnit.MICROS) + " " + a.plus(2, ChronoUnit.HALF_DAYS));
        p("estimated unit: " + tryIt(() -> a.plus(1, ChronoUnit.MONTHS)) + " divide 0: " + tryIt(() -> a.dividedBy(0)));
        p("overflow: " + tryIt(() -> Duration.ofSeconds(Long.MAX_VALUE).plusSeconds(1)));
        p("truncate: " + Duration.parse("PT1H23M45.678S").truncatedTo(ChronoUnit.MINUTES) + " "
                + Duration.ofSeconds(-1, 500).truncatedTo(ChronoUnit.SECONDS) + " "
                + tryIt(() -> a.truncatedTo(ChronoUnit.WEEKS)));
        p("compare/equals: " + a.compareTo(Duration.ofSeconds(100)) + " " + a.equals(Duration.ofSeconds(100, 300))
                + " units=" + a.getUnits() + " get=" + a.get(ChronoUnit.NANOS) + " " + tryIt(() -> a.get(ChronoUnit.DAYS)));
        p("from/of: " + Duration.from(Duration.ofMinutes(3)) + " " + Duration.of(3, ChronoUnit.HOURS) + " "
                + a.withSeconds(5) + " " + a.withNanos(7) + " " + tryIt(() -> a.withNanos(-1)));

        // Instant
        long[] secs = {0, 1, -1, 1_600_000_000L, 253402300799L, 253402300800L, -62167219200L, -62167219201L,
            -377705116800L, -377705116801L, 31556889864403199L, -31557014167219200L, 951782400L, 4102444800L};
        for (long s : secs) {
            Instant i = Instant.ofEpochSecond(s);
            p(s + " -> " + i + " millis=" + tryIt(i::toEpochMilli) + " hash=" + i.hashCode());
        }
        int[] nanos = {1, 10, 100_000, 1_000_000, 123_456_789, 999_999_999, 500_000_000};
        for (int n : nanos) p("nano " + n + " -> " + Instant.ofEpochSecond(0, n) + " " + Instant.ofEpochSecond(-1, n));
        p("ofEpochMilli: " + Instant.ofEpochMilli(-1) + " " + Instant.ofEpochMilli(1234567890123L));
        for (String s : new String[] {"2020-02-29T12:34:56Z", "2020-02-29T12:34:56.789Z", "1970-01-01T00:00Z",
            "2007-12-03T10:15:30.00Z", "+12345-01-01T00:00:00Z", "-0001-12-31T23:59:59.999999999Z",
            "2021-06-01T10:00:00+02:00", "2021-06-01T10:00:00-05:30", "2019-02-29T00:00:00Z", "2020-13-01T00:00:00Z",
            "2020-01-01T24:00:00Z", "2020-01-01 00:00:00Z", "2020-01-01T00:00:00", "2020-01-01T00:00:00Zjunk", "x"}) {
            p("parse " + s + " -> " + tryIt(() -> Instant.parse(s)));
        }
        Instant base = Instant.parse("2023-10-18T12:34:56.123456789Z");
        p("fields: " + base.getEpochSecond() + " " + base.getNano() + " " + base.get(ChronoField.MILLI_OF_SECOND) + " "
                + base.get(ChronoField.MICRO_OF_SECOND) + " " + base.getLong(ChronoField.INSTANT_SECONDS) + " "
                + tryIt(() -> base.get(ChronoField.INSTANT_SECONDS)) + " " + tryIt(() -> base.get(ChronoField.YEAR)));
        p("supported: " + base.isSupported(ChronoField.NANO_OF_SECOND) + " " + base.isSupported(ChronoField.YEAR) + " "
                + base.isSupported(ChronoUnit.DAYS) + " " + base.isSupported(ChronoUnit.WEEKS) + " "
                + base.range(ChronoField.MILLI_OF_SECOND));
        p("plus: " + base.plus(1, ChronoUnit.DAYS) + " " + base.plusMillis(-124) + " " + base.minus(Duration.ofHours(30))
                + " " + base.plus(Duration.ofNanos(1)) + " " + tryIt(() -> base.plus(1, ChronoUnit.MONTHS)));
        p("with: " + base.with(ChronoField.MILLI_OF_SECOND, 5) + " " + base.with(ChronoField.INSTANT_SECONDS, 0) + " "
                + tryIt(() -> base.with(ChronoField.NANO_OF_SECOND, -1)));
        p("truncate: " + base.truncatedTo(ChronoUnit.SECONDS) + " " + base.truncatedTo(ChronoUnit.HOURS) + " "
                + base.truncatedTo(ChronoUnit.DAYS) + " " + Instant.ofEpochSecond(-1, 5).truncatedTo(ChronoUnit.MINUTES));
        Instant later = base.plus(Duration.parse("PT49H3.5S"));
        p("until: " + base.until(later, ChronoUnit.NANOS) + " " + base.until(later, ChronoUnit.MILLIS) + " "
                + base.until(later, ChronoUnit.HOURS) + " " + later.until(base, ChronoUnit.DAYS) + " "
                + Duration.between(base, later) + " " + Duration.between(later, base));
        p("compare: " + base.isBefore(later) + " " + base.isAfter(later) + " " + base.compareTo(later) + " "
                + base.equals(Instant.ofEpochSecond(base.getEpochSecond(), base.getNano())));
        p("bounds: " + Instant.MIN + " " + Instant.MAX + " " + tryIt(() -> Instant.MAX.plusNanos(1)) + " "
                + Instant.EPOCH);
        p("adjust: " + Instant.EPOCH.with(base) + " from: " + Instant.from(base));
        p("now sane: " + (Math.abs(Instant.now().toEpochMilli() - System.currentTimeMillis()) < 5000));

        // temporal core
        for (ChronoUnit u : ChronoUnit.values()) {
            p(u.name() + " " + u + " " + u.getDuration() + " est=" + u.isDurationEstimated() + " date="
                    + u.isDateBased() + " time=" + u.isTimeBased());
        }
        for (ChronoField f : ChronoField.values()) {
            p(f.name() + " " + f + " " + f.range() + " base=" + f.getBaseUnit() + " rangeUnit=" + f.getRangeUnit()
                    + " date=" + f.isDateBased() + " time=" + f.isTimeBased() + " int=" + f.range().isIntValue());
        }
        ValueRange vr = ValueRange.of(1, 28, 31);
        p("range: " + vr + " fixed=" + vr.isFixed() + " " + vr.isValidValue(30) + " " + vr.hashCode() + " "
                + ValueRange.of(-5, 2, 10, 20) + " " + ValueRange.of(-5, 2, 10, 20).hashCode() + " "
                + tryIt(() -> ValueRange.of(5, 1)) + " " + tryIt(() -> vr.checkValidValue(40, ChronoField.DAY_OF_MONTH)));
        p("check: " + tryIt(() -> ChronoField.HOUR_OF_DAY.checkValidIntValue(24)));

        // Date and Calendar
        Date d = Date.from(base);
        p("Date: " + d.getTime() + " back: " + d.toInstant() + " " + tryIt(() -> Date.from(Instant.MAX)));
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(86400000L * 365);
        p("Calendar: " + c.toInstant());
    }
}
