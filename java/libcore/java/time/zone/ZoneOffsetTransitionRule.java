package java.time.zone;

import static java.time.temporal.TemporalAdjusters.nextOrSame;
import static java.time.temporal.TemporalAdjusters.previousOrSame;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.chrono.IsoChronology;
import java.util.Objects;

/** A yearly rule for creating transitions, such as "last Sunday in March at 01:00 UTC". */
public final class ZoneOffsetTransitionRule implements java.io.Serializable {
    private static final long serialVersionUID = 6889046316657758795L;

    private final Month month;
    private final byte dom;
    private final DayOfWeek dow;
    private final LocalTime time;
    private final boolean timeEndOfDay;
    private final TimeDefinition timeDefinition;
    private final ZoneOffset standardOffset;
    private final ZoneOffset offsetBefore;
    private final ZoneOffset offsetAfter;

    public static ZoneOffsetTransitionRule of(Month month, int dayOfMonthIndicator, DayOfWeek dayOfWeek,
                                              LocalTime time, boolean timeEndOfDay, TimeDefinition timeDefinition,
                                              ZoneOffset standardOffset, ZoneOffset offsetBefore,
                                              ZoneOffset offsetAfter) {
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(timeDefinition, "timeDefinition");
        Objects.requireNonNull(standardOffset, "standardOffset");
        Objects.requireNonNull(offsetBefore, "offsetBefore");
        Objects.requireNonNull(offsetAfter, "offsetAfter");
        if (dayOfMonthIndicator < -28 || dayOfMonthIndicator > 31 || dayOfMonthIndicator == 0) {
            throw new IllegalArgumentException("Day of month indicator must be between -28 and 31 inclusive "
                    + "excluding zero");
        }
        if (timeEndOfDay && time.equals(LocalTime.MIDNIGHT) == false) {
            throw new IllegalArgumentException("Time must be midnight when end of day flag is true");
        }
        if (time.getNano() != 0) {
            throw new IllegalArgumentException("Time's nano-of-second must be zero");
        }
        return new ZoneOffsetTransitionRule(month, dayOfMonthIndicator, dayOfWeek, time, timeEndOfDay,
                timeDefinition, standardOffset, offsetBefore, offsetAfter);
    }

    ZoneOffsetTransitionRule(Month month, int dayOfMonthIndicator, DayOfWeek dayOfWeek, LocalTime time,
                             boolean timeEndOfDay, TimeDefinition timeDefinition, ZoneOffset standardOffset,
                             ZoneOffset offsetBefore, ZoneOffset offsetAfter) {
        this.month = month;
        this.dom = (byte) dayOfMonthIndicator;
        this.dow = dayOfWeek;
        this.time = time;
        this.timeEndOfDay = timeEndOfDay;
        this.timeDefinition = timeDefinition;
        this.standardOffset = standardOffset;
        this.offsetBefore = offsetBefore;
        this.offsetAfter = offsetAfter;
    }

    public Month getMonth() {
        return month;
    }

    public int getDayOfMonthIndicator() {
        return dom;
    }

    public DayOfWeek getDayOfWeek() {
        return dow;
    }

    public LocalTime getLocalTime() {
        return time;
    }

    public boolean isMidnightEndOfDay() {
        return timeEndOfDay;
    }

    public TimeDefinition getTimeDefinition() {
        return timeDefinition;
    }

    public ZoneOffset getStandardOffset() {
        return standardOffset;
    }

    public ZoneOffset getOffsetBefore() {
        return offsetBefore;
    }

    public ZoneOffset getOffsetAfter() {
        return offsetAfter;
    }

    public ZoneOffsetTransition createTransition(int year) {
        LocalDate date;
        if (dom < 0) {
            date = LocalDate.of(year, month, month.length(IsoChronology.INSTANCE.isLeapYear(year)) + 1 + dom);
            if (dow != null) {
                date = date.with(previousOrSame(dow));
            }
        } else {
            date = LocalDate.of(year, month, dom);
            if (dow != null) {
                date = date.with(nextOrSame(dow));
            }
        }
        if (timeEndOfDay) {
            date = date.plusDays(1);
        }
        LocalDateTime localDT = LocalDateTime.of(date, time);
        LocalDateTime transition = timeDefinition.createDateTime(localDT, standardOffset, offsetBefore);
        return new ZoneOffsetTransition(transition, offsetBefore, offsetAfter);
    }

    public boolean equals(Object otherRule) {
        if (otherRule == this) {
            return true;
        }
        if (otherRule instanceof ZoneOffsetTransitionRule) {
            ZoneOffsetTransitionRule other = (ZoneOffsetTransitionRule) otherRule;
            return month == other.month && dom == other.dom && dow == other.dow
                    && timeDefinition == other.timeDefinition && timeEndOfDay == other.timeEndOfDay
                    && time.equals(other.time) && standardOffset.equals(other.standardOffset)
                    && offsetBefore.equals(other.offsetBefore) && offsetAfter.equals(other.offsetAfter);
        }
        return false;
    }

    public int hashCode() {
        int hash = ((time.toSecondOfDay() + (timeEndOfDay ? 1 : 0)) << 15) + (month.ordinal() << 11)
                + ((dom + 32) << 5) + ((dow == null ? 7 : dow.ordinal()) << 2) + (timeDefinition.ordinal());
        return hash ^ standardOffset.hashCode() ^ offsetBefore.hashCode() ^ offsetAfter.hashCode();
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("TransitionRule[").append(offsetBefore.compareTo(offsetAfter) > 0 ? "Gap " : "Overlap ")
                .append(offsetBefore).append(" to ").append(offsetAfter).append(", ");
        if (dow != null) {
            if (dom == -1) {
                buf.append(dow.name()).append(" on or before last day of ").append(month.name());
            } else if (dom < 0) {
                buf.append(dow.name()).append(" on or before last day minus ").append(-dom - 1).append(" of ")
                        .append(month.name());
            } else {
                buf.append(dow.name()).append(" on or after ").append(month.name()).append(' ').append(dom);
            }
        } else {
            buf.append(month.name()).append(' ').append(dom);
        }
        buf.append(" at ").append(timeEndOfDay ? "24:00" : time.toString()).append(" ").append(timeDefinition)
                .append(", standard offset ").append(standardOffset).append(']');
        return buf.toString();
    }

    public static enum TimeDefinition {
        UTC,
        WALL,
        STANDARD;

        public LocalDateTime createDateTime(LocalDateTime dateTime, ZoneOffset standardOffset,
                                            ZoneOffset wallOffset) {
            switch (this) {
                case UTC: {
                    int difference = wallOffset.getTotalSeconds() - ZoneOffset.UTC.getTotalSeconds();
                    return dateTime.plusSeconds(difference);
                }
                case STANDARD: {
                    int difference = wallOffset.getTotalSeconds() - standardOffset.getTotalSeconds();
                    return dateTime.plusSeconds(difference);
                }
                default:
                    return dateTime;
            }
        }
    }
}
