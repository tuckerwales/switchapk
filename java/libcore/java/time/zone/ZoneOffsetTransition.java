package java.time.zone;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A change of offset on the local time-line: a gap (clocks go forward) or an overlap (clocks go back). */
public final class ZoneOffsetTransition implements Comparable<ZoneOffsetTransition>, java.io.Serializable {
    private static final long serialVersionUID = -6946044323557704546L;

    private final long epochSecond;
    private final LocalDateTime transition;
    private final ZoneOffset offsetBefore;
    private final ZoneOffset offsetAfter;

    public static ZoneOffsetTransition of(LocalDateTime transition, ZoneOffset offsetBefore,
                                          ZoneOffset offsetAfter) {
        Objects.requireNonNull(transition, "transition");
        Objects.requireNonNull(offsetBefore, "offsetBefore");
        Objects.requireNonNull(offsetAfter, "offsetAfter");
        if (offsetBefore.equals(offsetAfter)) {
            throw new IllegalArgumentException("Offsets must not be equal");
        }
        if (transition.getNano() != 0) {
            throw new IllegalArgumentException("Nano-of-second must be zero");
        }
        return new ZoneOffsetTransition(transition, offsetBefore, offsetAfter);
    }

    ZoneOffsetTransition(LocalDateTime transition, ZoneOffset offsetBefore, ZoneOffset offsetAfter) {
        this.epochSecond = transition.toEpochSecond(offsetBefore);
        this.transition = transition;
        this.offsetBefore = offsetBefore;
        this.offsetAfter = offsetAfter;
    }

    ZoneOffsetTransition(long epochSecond, ZoneOffset offsetBefore, ZoneOffset offsetAfter) {
        this.epochSecond = epochSecond;
        this.transition = LocalDateTime.ofEpochSecond(epochSecond, 0, offsetBefore);
        this.offsetBefore = offsetBefore;
        this.offsetAfter = offsetAfter;
    }

    public Instant getInstant() {
        return Instant.ofEpochSecond(epochSecond);
    }

    public long toEpochSecond() {
        return epochSecond;
    }

    public LocalDateTime getDateTimeBefore() {
        return transition;
    }

    public LocalDateTime getDateTimeAfter() {
        return transition.plusSeconds(getDurationSeconds());
    }

    public ZoneOffset getOffsetBefore() {
        return offsetBefore;
    }

    public ZoneOffset getOffsetAfter() {
        return offsetAfter;
    }

    public Duration getDuration() {
        return Duration.ofSeconds(getDurationSeconds());
    }

    private int getDurationSeconds() {
        return getOffsetAfter().getTotalSeconds() - getOffsetBefore().getTotalSeconds();
    }

    public boolean isGap() {
        return getOffsetAfter().getTotalSeconds() > getOffsetBefore().getTotalSeconds();
    }

    public boolean isOverlap() {
        return getOffsetAfter().getTotalSeconds() < getOffsetBefore().getTotalSeconds();
    }

    public boolean isValidOffset(ZoneOffset offset) {
        return isGap() ? false : (getOffsetBefore().equals(offset) || getOffsetAfter().equals(offset));
    }

    List<ZoneOffset> getValidOffsets() {
        if (isGap()) {
            return Collections.emptyList();
        }
        return Arrays.asList(getOffsetBefore(), getOffsetAfter());
    }

    public int compareTo(ZoneOffsetTransition transition) {
        return Long.compare(epochSecond, transition.epochSecond);
    }

    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other instanceof ZoneOffsetTransition) {
            ZoneOffsetTransition d = (ZoneOffsetTransition) other;
            return epochSecond == d.epochSecond && offsetBefore.equals(d.offsetBefore)
                    && offsetAfter.equals(d.offsetAfter);
        }
        return false;
    }

    public int hashCode() {
        return transition.hashCode() ^ offsetBefore.hashCode() ^ Integer.rotateLeft(offsetAfter.hashCode(), 16);
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("Transition[").append(isGap() ? "Gap" : "Overlap").append(" at ").append(transition)
                .append(offsetBefore).append(" to ").append(offsetAfter).append(']');
        return buf.toString();
    }
}
