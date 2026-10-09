package java.nio.file.attribute;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/** A file timestamp. Instant-based methods are missing because libcore has no java.time. */
public final class FileTime implements Comparable<FileTime> {
    private final long value;
    private final TimeUnit unit;

    private FileTime(long value, TimeUnit unit) {
        this.value = value;
        this.unit = unit;
    }

    public static FileTime from(long value, TimeUnit unit) {
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        return new FileTime(value, unit);
    }

    public static FileTime fromMillis(long value) {
        return new FileTime(value, TimeUnit.MILLISECONDS);
    }

    public long to(TimeUnit unit) {
        return unit.convert(this.value, this.unit);
    }

    public long toMillis() {
        return unit.toMillis(value);
    }

    /* Nanoseconds past the millisecond toMillis() returns, 0..999999. */
    private long nanoRemainder() {
        long nanos = unit.toNanos(value);
        if (nanos == Long.MAX_VALUE || nanos == Long.MIN_VALUE) {
            return 0;
        }
        return Math.floorMod(nanos, 1000000L);
    }

    public boolean equals(Object obj) {
        return obj instanceof FileTime && compareTo((FileTime) obj) == 0;
    }

    public int hashCode() {
        long ms = toMillis();
        long h = ms * 1000003L + nanoRemainder();
        return (int) (h ^ (h >>> 32));
    }

    public int compareTo(FileTime other) {
        int c = Long.compare(toMillis(), other.toMillis());
        return c != 0 ? c : Long.compare(nanoRemainder(), other.nanoRemainder());
    }

    /* ISO 8601 in UTC, like the JDK: 2021-03-04T05:06:07Z with a fraction only when nonzero. */
    public String toString() {
        long ms = toMillis();
        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(ms);
        StringBuilder sb = new StringBuilder();
        int year = c.get(Calendar.YEAR);
        if (c.get(Calendar.ERA) == GregorianCalendar.BC) {
            year = 1 - year;
        }
        if (year < 0) {
            sb.append('-');
            year = -year;
        }
        pad(sb, year, 4);
        sb.append('-');
        pad(sb, c.get(Calendar.MONTH) + 1, 2);
        sb.append('-');
        pad(sb, c.get(Calendar.DAY_OF_MONTH), 2);
        sb.append('T');
        pad(sb, c.get(Calendar.HOUR_OF_DAY), 2);
        sb.append(':');
        pad(sb, c.get(Calendar.MINUTE), 2);
        sb.append(':');
        pad(sb, c.get(Calendar.SECOND), 2);
        long frac = Math.floorMod(ms, 1000L) * 1000000L + nanoRemainder();
        if (frac != 0) {
            StringBuilder f = new StringBuilder();
            pad(f, frac, 9);
            int end = f.length();
            while (f.charAt(end - 1) == '0') {
                end--;
            }
            sb.append('.').append(f, 0, end);
        }
        return sb.append('Z').toString();
    }

    private static void pad(StringBuilder sb, long v, int width) {
        String s = Long.toString(v);
        for (int i = s.length(); i < width; i++) {
            sb.append('0');
        }
        sb.append(s);
    }
}
