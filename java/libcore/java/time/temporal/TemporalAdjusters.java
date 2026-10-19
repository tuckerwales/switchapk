package java.time.temporal;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.DAY_OF_YEAR;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MONTHS;
import static java.time.temporal.ChronoUnit.YEARS;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.UnaryOperator;

public final class TemporalAdjusters {
    private TemporalAdjusters() {
    }

    public static TemporalAdjuster ofDateAdjuster(final UnaryOperator<LocalDate> dateBasedAdjuster) {
        Objects.requireNonNull(dateBasedAdjuster, "dateBasedAdjuster");
        return new TemporalAdjuster() {
            public Temporal adjustInto(Temporal temporal) {
                LocalDate input = LocalDate.from(temporal);
                LocalDate output = dateBasedAdjuster.apply(input);
                return temporal.with(output);
            }
        };
    }

    public static TemporalAdjuster firstDayOfMonth() {
        return new TemporalAdjuster() {
            public Temporal adjustInto(Temporal temporal) {
                return temporal.with(DAY_OF_MONTH, 1);
            }
        };
    }

    public static TemporalAdjuster lastDayOfMonth() {
        return new TemporalAdjuster() {
            public Temporal adjustInto(Temporal temporal) {
                return temporal.with(DAY_OF_MONTH, temporal.range(DAY_OF_MONTH).getMaximum());
            }
        };
    }

    public static TemporalAdjuster firstDayOfNextMonth() {
        return new TemporalAdjuster() {
            public Temporal adjustInto(Temporal temporal) {
                return temporal.with(DAY_OF_MONTH, 1).plus(1, MONTHS);
            }
        };
    }

    public static TemporalAdjuster firstDayOfYear() {
        return new TemporalAdjuster() {
            public Temporal adjustInto(Temporal temporal) {
                return temporal.with(DAY_OF_YEAR, 1);
            }
        };
    }

    public static TemporalAdjuster lastDayOfYear() {
        return new TemporalAdjuster() {
            public Temporal adjustInto(Temporal temporal) {
                return temporal.with(DAY_OF_YEAR, temporal.range(DAY_OF_YEAR).getMaximum());
            }
        };
    }

    public static TemporalAdjuster firstDayOfNextYear() {
        return new TemporalAdjuster() {
            public Temporal adjustInto(Temporal temporal) {
                return temporal.with(DAY_OF_YEAR, 1).plus(1, YEARS);
            }
        };
    }

    public static TemporalAdjuster firstInMonth(DayOfWeek dayOfWeek) {
        return TemporalAdjusters.dayOfWeekInMonth(1, dayOfWeek);
    }

    public static TemporalAdjuster lastInMonth(DayOfWeek dayOfWeek) {
        return TemporalAdjusters.dayOfWeekInMonth(-1, dayOfWeek);
    }

    public static TemporalAdjuster dayOfWeekInMonth(final int ordinal, DayOfWeek dayOfWeek) {
        Objects.requireNonNull(dayOfWeek, "dayOfWeek");
        final int dowValue = dayOfWeek.getValue();
        if (ordinal >= 0) {
            return new TemporalAdjuster() {
                public Temporal adjustInto(Temporal temporal) {
                    Temporal temp = temporal.with(DAY_OF_MONTH, 1);
                    int curDow = temp.get(DAY_OF_WEEK);
                    int dowDiff = (dowValue - curDow + 7) % 7;
                    dowDiff += (ordinal - 1L) * 7L;
                    return temp.plus(dowDiff, DAYS);
                }
            };
        }
        return new TemporalAdjuster() {
            public Temporal adjustInto(Temporal temporal) {
                Temporal temp = temporal.with(DAY_OF_MONTH, temporal.range(DAY_OF_MONTH).getMaximum());
                int curDow = temp.get(DAY_OF_WEEK);
                int daysDiff = dowValue - curDow;
                daysDiff = (daysDiff == 0 ? 0 : (daysDiff > 0 ? daysDiff - 7 : daysDiff));
                daysDiff -= (-ordinal - 1L) * 7L;
                return temp.plus(daysDiff, DAYS);
            }
        };
    }

    public static TemporalAdjuster next(DayOfWeek dayOfWeek) {
        final int dowValue = dayOfWeek.getValue();
        return new TemporalAdjuster() {
            public Temporal adjustInto(Temporal temporal) {
                int calDow = temporal.get(DAY_OF_WEEK);
                int daysDiff = calDow - dowValue;
                return temporal.plus(daysDiff >= 0 ? 7 - daysDiff : -daysDiff, DAYS);
            }
        };
    }

    public static TemporalAdjuster nextOrSame(DayOfWeek dayOfWeek) {
        final int dowValue = dayOfWeek.getValue();
        return new TemporalAdjuster() {
            public Temporal adjustInto(Temporal temporal) {
                int calDow = temporal.get(DAY_OF_WEEK);
                if (calDow == dowValue) {
                    return temporal;
                }
                int daysDiff = calDow - dowValue;
                return temporal.plus(daysDiff >= 0 ? 7 - daysDiff : -daysDiff, DAYS);
            }
        };
    }

    public static TemporalAdjuster previous(DayOfWeek dayOfWeek) {
        final int dowValue = dayOfWeek.getValue();
        return new TemporalAdjuster() {
            public Temporal adjustInto(Temporal temporal) {
                int calDow = temporal.get(DAY_OF_WEEK);
                int daysDiff = dowValue - calDow;
                return temporal.minus(daysDiff >= 0 ? 7 - daysDiff : -daysDiff, DAYS);
            }
        };
    }

    public static TemporalAdjuster previousOrSame(DayOfWeek dayOfWeek) {
        final int dowValue = dayOfWeek.getValue();
        return new TemporalAdjuster() {
            public Temporal adjustInto(Temporal temporal) {
                int calDow = temporal.get(DAY_OF_WEEK);
                if (calDow == dowValue) {
                    return temporal;
                }
                int daysDiff = dowValue - calDow;
                return temporal.minus(daysDiff >= 0 ? 7 - daysDiff : -daysDiff, DAYS);
            }
        };
    }
}
