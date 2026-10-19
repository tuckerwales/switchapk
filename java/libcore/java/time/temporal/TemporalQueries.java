package java.time.temporal;

import static java.time.temporal.ChronoField.EPOCH_DAY;
import static java.time.temporal.ChronoField.NANO_OF_DAY;
import static java.time.temporal.ChronoField.OFFSET_SECONDS;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.chrono.Chronology;

public final class TemporalQueries {
    private TemporalQueries() {
    }

    static final TemporalQuery<ZoneId> ZONE_ID = new TemporalQuery<ZoneId>() {
        public ZoneId queryFrom(TemporalAccessor temporal) {
            return temporal.query(ZONE_ID);
        }

        public String toString() {
            return "ZoneId";
        }
    };

    static final TemporalQuery<Chronology> CHRONO = new TemporalQuery<Chronology>() {
        public Chronology queryFrom(TemporalAccessor temporal) {
            return temporal.query(CHRONO);
        }

        public String toString() {
            return "Chronology";
        }
    };

    static final TemporalQuery<TemporalUnit> PRECISION = new TemporalQuery<TemporalUnit>() {
        public TemporalUnit queryFrom(TemporalAccessor temporal) {
            return temporal.query(PRECISION);
        }

        public String toString() {
            return "Precision";
        }
    };

    static final TemporalQuery<ZoneOffset> OFFSET = new TemporalQuery<ZoneOffset>() {
        public ZoneOffset queryFrom(TemporalAccessor temporal) {
            if (temporal.isSupported(OFFSET_SECONDS)) {
                return ZoneOffset.ofTotalSeconds(temporal.get(OFFSET_SECONDS));
            }
            return null;
        }

        public String toString() {
            return "ZoneOffset";
        }
    };

    static final TemporalQuery<ZoneId> ZONE = new TemporalQuery<ZoneId>() {
        public ZoneId queryFrom(TemporalAccessor temporal) {
            ZoneId zone = temporal.query(ZONE_ID);
            return (zone != null ? zone : temporal.query(OFFSET));
        }

        public String toString() {
            return "Zone";
        }
    };

    static final TemporalQuery<LocalDate> LOCAL_DATE = new TemporalQuery<LocalDate>() {
        public LocalDate queryFrom(TemporalAccessor temporal) {
            if (temporal.isSupported(EPOCH_DAY)) {
                return LocalDate.ofEpochDay(temporal.getLong(EPOCH_DAY));
            }
            return null;
        }

        public String toString() {
            return "LocalDate";
        }
    };

    static final TemporalQuery<LocalTime> LOCAL_TIME = new TemporalQuery<LocalTime>() {
        public LocalTime queryFrom(TemporalAccessor temporal) {
            if (temporal.isSupported(NANO_OF_DAY)) {
                return LocalTime.ofNanoOfDay(temporal.getLong(NANO_OF_DAY));
            }
            return null;
        }

        public String toString() {
            return "LocalTime";
        }
    };

    public static TemporalQuery<ZoneId> zoneId() {
        return ZONE_ID;
    }

    public static TemporalQuery<Chronology> chronology() {
        return CHRONO;
    }

    public static TemporalQuery<TemporalUnit> precision() {
        return PRECISION;
    }

    public static TemporalQuery<ZoneId> zone() {
        return ZONE;
    }

    public static TemporalQuery<ZoneOffset> offset() {
        return OFFSET;
    }

    public static TemporalQuery<LocalDate> localDate() {
        return LOCAL_DATE;
    }

    public static TemporalQuery<LocalTime> localTime() {
        return LOCAL_TIME;
    }
}
