package java.time;

import static java.time.LocalTime.NANOS_PER_MINUTE;
import static java.time.LocalTime.NANOS_PER_SECOND;

import java.util.Objects;

/** Access to the current instant and a time-zone; system, fixed, offset and tick clocks as in the JDK. */
public abstract class Clock implements InstantSource {
    private static final long NANOS_PER_MILLI = 1000_000L;

    public static Clock systemUTC() {
        return SystemClock.UTC;
    }

    public static Clock systemDefaultZone() {
        return new SystemClock(ZoneId.systemDefault());
    }

    public static Clock system(ZoneId zone) {
        Objects.requireNonNull(zone, "zone");
        if (zone == ZoneOffset.UTC) {
            return SystemClock.UTC;
        }
        return new SystemClock(zone);
    }

    public static Clock tickMillis(ZoneId zone) {
        return new TickClock(system(zone), NANOS_PER_MILLI);
    }

    public static Clock tickSeconds(ZoneId zone) {
        return new TickClock(system(zone), NANOS_PER_SECOND);
    }

    public static Clock tickMinutes(ZoneId zone) {
        return new TickClock(system(zone), NANOS_PER_MINUTE);
    }

    public static Clock tick(Clock baseClock, Duration tickDuration) {
        Objects.requireNonNull(baseClock, "baseClock");
        Objects.requireNonNull(tickDuration, "tickDuration");
        if (tickDuration.isNegative()) {
            throw new IllegalArgumentException("Tick duration must not be negative");
        }
        long tickNanos = tickDuration.toNanos();
        if (tickNanos % 1000_000 == 0) {
            // ok, no fraction of millisecond
        } else if (1000_000_000 % tickNanos == 0) {
            // ok, divides into one second without remainder
        } else {
            throw new IllegalArgumentException("Invalid tick duration");
        }
        if (tickNanos <= 1) {
            return baseClock;
        }
        return new TickClock(baseClock, tickNanos);
    }

    public static Clock fixed(Instant fixedInstant, ZoneId zone) {
        Objects.requireNonNull(fixedInstant, "fixedInstant");
        Objects.requireNonNull(zone, "zone");
        return new FixedClock(fixedInstant, zone);
    }

    public static Clock offset(Clock baseClock, Duration offsetDuration) {
        Objects.requireNonNull(baseClock, "baseClock");
        Objects.requireNonNull(offsetDuration, "offsetDuration");
        if (offsetDuration.equals(Duration.ZERO)) {
            return baseClock;
        }
        return new OffsetClock(baseClock, offsetDuration);
    }

    protected Clock() {
    }

    public abstract ZoneId getZone();

    public abstract Clock withZone(ZoneId zone);

    public long millis() {
        return instant().toEpochMilli();
    }

    public abstract Instant instant();

    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    public int hashCode() {
        return super.hashCode();
    }

    static final class SystemClock extends Clock implements java.io.Serializable {
        private static final long serialVersionUID = 6740630888130243051L;
        static final SystemClock UTC = new SystemClock(ZoneOffset.UTC);

        private final ZoneId zone;

        SystemClock(ZoneId zone) {
            this.zone = zone;
        }

        public ZoneId getZone() {
            return zone;
        }

        public Clock withZone(ZoneId zone) {
            if (zone.equals(this.zone)) {
                return this;
            }
            return new SystemClock(zone);
        }

        public long millis() {
            return System.currentTimeMillis();
        }

        public Instant instant() {
            return Instant.ofEpochMilli(millis());
        }

        public boolean equals(Object obj) {
            if (obj instanceof SystemClock) {
                return zone.equals(((SystemClock) obj).zone);
            }
            return false;
        }

        public int hashCode() {
            return zone.hashCode() + 1;
        }

        public String toString() {
            return "SystemClock[" + zone + "]";
        }
    }

    static final class FixedClock extends Clock implements java.io.Serializable {
        private static final long serialVersionUID = 7430389292664866958L;
        private final Instant instant;
        private final ZoneId zone;

        FixedClock(Instant fixedInstant, ZoneId zone) {
            this.instant = fixedInstant;
            this.zone = zone;
        }

        public ZoneId getZone() {
            return zone;
        }

        public Clock withZone(ZoneId zone) {
            if (zone.equals(this.zone)) {
                return this;
            }
            return new FixedClock(instant, zone);
        }

        public long millis() {
            return instant.toEpochMilli();
        }

        public Instant instant() {
            return instant;
        }

        public boolean equals(Object obj) {
            if (obj instanceof FixedClock) {
                FixedClock other = (FixedClock) obj;
                return instant.equals(other.instant) && zone.equals(other.zone);
            }
            return false;
        }

        public int hashCode() {
            return instant.hashCode() ^ zone.hashCode();
        }

        public String toString() {
            return "FixedClock[" + instant + "," + zone + "]";
        }
    }

    static final class OffsetClock extends Clock implements java.io.Serializable {
        private static final long serialVersionUID = 2007484719125426256L;
        private final Clock baseClock;
        private final Duration offset;

        OffsetClock(Clock baseClock, Duration offset) {
            this.baseClock = baseClock;
            this.offset = offset;
        }

        public ZoneId getZone() {
            return baseClock.getZone();
        }

        public Clock withZone(ZoneId zone) {
            if (zone.equals(baseClock.getZone())) {
                return this;
            }
            return new OffsetClock(baseClock.withZone(zone), offset);
        }

        public long millis() {
            return Math.addExact(baseClock.millis(), offset.toMillis());
        }

        public Instant instant() {
            return baseClock.instant().plus(offset);
        }

        public boolean equals(Object obj) {
            if (obj instanceof OffsetClock) {
                OffsetClock other = (OffsetClock) obj;
                return baseClock.equals(other.baseClock) && offset.equals(other.offset);
            }
            return false;
        }

        public int hashCode() {
            return baseClock.hashCode() ^ offset.hashCode();
        }

        public String toString() {
            return "OffsetClock[" + baseClock + "," + offset + "]";
        }
    }

    static final class TickClock extends Clock implements java.io.Serializable {
        private static final long serialVersionUID = 6504659149906368850L;
        private final Clock baseClock;
        private final long tickNanos;

        TickClock(Clock baseClock, long tickNanos) {
            this.baseClock = baseClock;
            this.tickNanos = tickNanos;
        }

        public ZoneId getZone() {
            return baseClock.getZone();
        }

        public Clock withZone(ZoneId zone) {
            if (zone.equals(baseClock.getZone())) {
                return this;
            }
            return new TickClock(baseClock.withZone(zone), tickNanos);
        }

        public long millis() {
            long millis = baseClock.millis();
            return millis - Math.floorMod(millis, tickNanos / 1000_000L);
        }

        public Instant instant() {
            if ((tickNanos % 1000_000) == 0) {
                long millis = baseClock.millis();
                return Instant.ofEpochMilli(millis - Math.floorMod(millis, tickNanos / 1000_000L));
            }
            Instant instant = baseClock.instant();
            long nanos = instant.getNano();
            long adjust = Math.floorMod(nanos, tickNanos);
            return instant.minusNanos(adjust);
        }

        public boolean equals(Object obj) {
            if (obj instanceof TickClock) {
                TickClock other = (TickClock) obj;
                return baseClock.equals(other.baseClock) && tickNanos == other.tickNanos;
            }
            return false;
        }

        public int hashCode() {
            return baseClock.hashCode() ^ ((int) (tickNanos ^ (tickNanos >>> 32)));
        }

        public String toString() {
            return "TickClock[" + baseClock + "," + Duration.ofNanos(tickNanos) + "]";
        }
    }

    /** InstantSource.withZone(ZoneId): a clock reading another source. */
    static final class SourceClock extends Clock implements java.io.Serializable {
        private static final long serialVersionUID = 235386528762398L;
        private final InstantSource baseSource;
        private final ZoneId zone;

        SourceClock(InstantSource baseSource, ZoneId zone) {
            this.baseSource = Objects.requireNonNull(baseSource, "baseSource");
            this.zone = Objects.requireNonNull(zone, "zone");
        }

        public ZoneId getZone() {
            return zone;
        }

        public Clock withZone(ZoneId zone) {
            if (zone.equals(this.zone)) {
                return this;
            }
            return new SourceClock(baseSource, zone);
        }

        public long millis() {
            return baseSource.millis();
        }

        public Instant instant() {
            return baseSource.instant();
        }

        public boolean equals(Object obj) {
            if (obj instanceof SourceClock) {
                SourceClock other = (SourceClock) obj;
                return zone.equals(other.zone) && baseSource.equals(other.baseSource);
            }
            return false;
        }

        public int hashCode() {
            return baseSource.hashCode() ^ zone.hashCode();
        }

        public String toString() {
            return "SourceClock[" + baseSource + "," + zone + "]";
        }
    }
}
