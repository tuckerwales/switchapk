package java.time.temporal;

import static java.time.temporal.ChronoField.EPOCH_DAY;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.FOREVER;

import java.time.DateTimeException;
import java.time.chrono.Chronology;
import java.time.format.ResolverStyle;
import java.util.Map;

public final class JulianFields {
    private static final long JULIAN_DAY_OFFSET = 2440588L;

    public static final TemporalField JULIAN_DAY = Field.JULIAN_DAY;
    public static final TemporalField MODIFIED_JULIAN_DAY = Field.MODIFIED_JULIAN_DAY;
    public static final TemporalField RATA_DIE = Field.RATA_DIE;

    private JulianFields() {
    }

    private enum Field implements TemporalField {
        JULIAN_DAY("JulianDay", JULIAN_DAY_OFFSET),
        MODIFIED_JULIAN_DAY("ModifiedJulianDay", 40587L),
        RATA_DIE("RataDie", 719163L);

        private final transient String name;
        private final transient ValueRange range;
        private final transient long offset;

        Field(String name, long offset) {
            this.name = name;
            this.range = ValueRange.of(-365243219162L + offset, 365241780471L + offset);
            this.offset = offset;
        }

        public TemporalUnit getBaseUnit() {
            return DAYS;
        }

        public TemporalUnit getRangeUnit() {
            return FOREVER;
        }

        public boolean isDateBased() {
            return true;
        }

        public boolean isTimeBased() {
            return false;
        }

        public ValueRange range() {
            return range;
        }

        public boolean isSupportedBy(TemporalAccessor temporal) {
            return temporal.isSupported(EPOCH_DAY);
        }

        public ValueRange rangeRefinedBy(TemporalAccessor temporal) {
            if (isSupportedBy(temporal) == false) {
                throw new DateTimeException("Unsupported field: " + this);
            }
            return range();
        }

        public long getFrom(TemporalAccessor temporal) {
            return temporal.getLong(EPOCH_DAY) + offset;
        }

        @SuppressWarnings("unchecked")
        public <R extends Temporal> R adjustInto(R temporal, long newValue) {
            if (range().isValidValue(newValue) == false) {
                throw new DateTimeException("Invalid value: " + name + " " + newValue);
            }
            return (R) temporal.with(EPOCH_DAY, Math.subtractExact(newValue, offset));
        }

        public TemporalAccessor resolve(Map<TemporalField, Long> fieldValues, TemporalAccessor partialTemporal,
                                        ResolverStyle resolverStyle) {
            long value = fieldValues.remove(this);
            Chronology chrono = Chronology.from(partialTemporal);
            if (resolverStyle == ResolverStyle.LENIENT) {
                return chrono.dateEpochDay(Math.subtractExact(value, offset));
            }
            range().checkValidValue(value, this);
            return chrono.dateEpochDay(value - offset);
        }

        public String toString() {
            return name;
        }
    }
}
