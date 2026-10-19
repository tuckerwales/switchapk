package java.time.chrono;

import java.time.temporal.Temporal;
import java.util.Comparator;

/** Package helpers behind the chrono interfaces' defaults (the JDK keeps these in the *Impl classes). */
final class ChronoSupport {
    private ChronoSupport() {
    }

    static final Comparator<ChronoLocalDate> DATE_ORDER = new Comparator<ChronoLocalDate>() {
        public int compare(ChronoLocalDate date1, ChronoLocalDate date2) {
            return Long.compare(date1.toEpochDay(), date2.toEpochDay());
        }
    };

    static final Comparator<ChronoLocalDateTime<?>> DATE_TIME_ORDER = new Comparator<ChronoLocalDateTime<?>>() {
        public int compare(ChronoLocalDateTime<?> dateTime1, ChronoLocalDateTime<?> dateTime2) {
            int cmp = Long.compare(dateTime1.toLocalDate().toEpochDay(), dateTime2.toLocalDate().toEpochDay());
            if (cmp == 0) {
                cmp = Long.compare(dateTime1.toLocalTime().toNanoOfDay(), dateTime2.toLocalTime().toNanoOfDay());
            }
            return cmp;
        }
    };

    static final Comparator<ChronoZonedDateTime<?>> INSTANT_ORDER = new Comparator<ChronoZonedDateTime<?>>() {
        public int compare(ChronoZonedDateTime<?> dateTime1, ChronoZonedDateTime<?> dateTime2) {
            int cmp = Long.compare(dateTime1.toEpochSecond(), dateTime2.toEpochSecond());
            if (cmp == 0) {
                cmp = Long.compare(dateTime1.toLocalTime().getNano(), dateTime2.toLocalTime().getNano());
            }
            return cmp;
        }
    };

    static ChronoLocalDate ensureValid(Chronology chrono, Temporal temporal) {
        ChronoLocalDate other = (ChronoLocalDate) temporal;
        if (chrono.equals(other.getChronology()) == false) {
            throw new ClassCastException("Chronology mismatch, expected: " + chrono.getId() + ", actual: "
                    + other.getChronology().getId());
        }
        return other;
    }

    @SuppressWarnings("unchecked")
    static <D extends ChronoLocalDate> ChronoLocalDateTime<D> ensureValidDateTime(Chronology chrono,
                                                                                  Temporal temporal) {
        ChronoLocalDateTime<D> other = (ChronoLocalDateTime<D>) temporal;
        if (chrono.equals(other.getChronology()) == false) {
            throw new ClassCastException("Chronology mismatch, required: " + chrono.getId() + ", actual: "
                    + other.getChronology().getId());
        }
        return other;
    }

    @SuppressWarnings("unchecked")
    static <D extends ChronoLocalDate> ChronoZonedDateTime<D> ensureValidZoned(Chronology chrono,
                                                                               Temporal temporal) {
        ChronoZonedDateTime<D> other = (ChronoZonedDateTime<D>) temporal;
        if (chrono.equals(other.getChronology()) == false) {
            throw new ClassCastException("Chronology mismatch, required: " + chrono.getId() + ", actual: "
                    + other.getChronology().getId());
        }
        return other;
    }
}
