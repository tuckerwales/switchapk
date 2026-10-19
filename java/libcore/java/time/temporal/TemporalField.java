package java.time.temporal;

import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public interface TemporalField {
    default String getDisplayName(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        return toString();
    }

    TemporalUnit getBaseUnit();

    TemporalUnit getRangeUnit();

    ValueRange range();

    boolean isDateBased();

    boolean isTimeBased();

    boolean isSupportedBy(TemporalAccessor temporal);

    ValueRange rangeRefinedBy(TemporalAccessor temporal);

    long getFrom(TemporalAccessor temporal);

    <R extends Temporal> R adjustInto(R temporal, long newValue);

    default TemporalAccessor resolve(Map<TemporalField, Long> fieldValues, TemporalAccessor partialTemporal,
                                     ResolverStyle resolverStyle) {
        return null;
    }

    String toString();
}
