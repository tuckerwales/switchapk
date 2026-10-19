package java.time.chrono;

import static java.time.temporal.ChronoField.ERA;
import static java.time.temporal.ChronoUnit.ERAS;

import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.time.temporal.ValueRange;
import java.util.Locale;
import java.util.Objects;

public interface Era extends TemporalAccessor, TemporalAdjuster {
    int getValue();

    default boolean isSupported(TemporalField field) {
        if (field instanceof ChronoField) {
            return field == ERA;
        }
        return field != null && field.isSupportedBy(this);
    }

    default ValueRange range(TemporalField field) {
        return TemporalAccessor.super.range(field);
    }

    default int get(TemporalField field) {
        if (field == ERA) {
            return getValue();
        }
        return TemporalAccessor.super.get(field);
    }

    default long getLong(TemporalField field) {
        if (field == ERA) {
            return getValue();
        } else if (field instanceof ChronoField) {
            throw new UnsupportedTemporalTypeException("Unsupported field: " + field);
        }
        return field.getFrom(this);
    }

    @SuppressWarnings("unchecked")
    default <R> R query(TemporalQuery<R> query) {
        if (query == TemporalQueries.precision()) {
            return (R) ERAS;
        }
        return TemporalAccessor.super.query(query);
    }

    default Temporal adjustInto(Temporal temporal) {
        return temporal.with(ERA, getValue());
    }

    /** English names only (there is no locale data for eras); other eras fall back to the numeric value. */
    default String getDisplayName(TextStyle style, Locale locale) {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(locale, "locale");
        if (this instanceof IsoEra) {
            boolean ce = getValue() == 1;
            switch (style.asNormal()) {
                case FULL:
                    return ce ? "Anno Domini" : "Before Christ";
                case NARROW:
                    return ce ? "A" : "B";
                default:
                    return ce ? "AD" : "BC";
            }
        }
        return Integer.toString(getValue());
    }
}
