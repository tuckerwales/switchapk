package java.time.format;

import java.time.DateTimeException;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.Objects;

/**
 * placeholder, replaced by the full formatter. It only carries the two methods java.time calls (format and parse
 * with a query) so the date-time classes compile; their ISO parse(CharSequence) and toString paths do not use it.
 */
public final class DateTimeFormatter {
    private DateTimeFormatter() {
    }

    public String format(TemporalAccessor temporal) {
        Objects.requireNonNull(temporal, "temporal");
        return temporal.toString();
    }

    public <T> T parse(CharSequence text, TemporalQuery<T> query) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(query, "query");
        throw new DateTimeException("DateTimeFormatter is not available");
    }
}
