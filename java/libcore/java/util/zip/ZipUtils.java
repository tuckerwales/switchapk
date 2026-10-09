package java.util.zip;

import java.util.Date;

/* Little-endian field access and DOS time conversion shared by the zip classes (framework-internal). */
final class ZipUtils {
    static final long DOSTIME_BEFORE_1980 = (1 << 21) | (1 << 16);
    static final long UPPER_DOSTIME_BOUND = 128L * 365 * 24 * 60 * 60 * 1000;
    static final long ZIP64_MAGICVAL = 0xFFFFFFFFL;
    static final int ZIP64_EXTID = 0x0001;
    static final int EXTID_EXTT = 0x5455;
    static final int EFS = 0x800;

    private ZipUtils() {
    }

    static int get16(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    static long get32(byte[] b, int off) {
        return (get16(b, off) | ((long) get16(b, off + 2) << 16)) & 0xffffffffL;
    }

    static long get64(byte[] b, int off) {
        return get32(b, off) | (get32(b, off + 4) << 32);
    }

    @SuppressWarnings("deprecation")
    static long dosToJavaTime(long dtime) {
        Date d = new Date((int) (((dtime >> 25) & 0x7f) + 80), (int) (((dtime >> 21) & 0x0f) - 1),
                (int) ((dtime >> 16) & 0x1f), (int) ((dtime >> 11) & 0x1f), (int) ((dtime >> 5) & 0x3f),
                (int) ((dtime << 1) & 0x3e));
        return d.getTime();
    }

    static long extendedDosToJavaTime(long xdostime) {
        long time = dosToJavaTime(xdostime);
        return time + (xdostime >> 32);
    }

    @SuppressWarnings("deprecation")
    private static long javaToDosTime(long time) {
        Date d = new Date(time);
        int year = d.getYear() + 1900;
        if (year < 1980) {
            return DOSTIME_BEFORE_1980;
        }
        return (year - 1980) << 25 | (d.getMonth() + 1) << 21 | d.getDate() << 16 | d.getHours() << 11
                | d.getMinutes() << 5 | d.getSeconds() >> 1;
    }

    static long javaToExtendedDosTime(long time) {
        if (time < 0) {
            return DOSTIME_BEFORE_1980;
        }
        long dostime = javaToDosTime(time);
        return (dostime != DOSTIME_BEFORE_1980) ? dostime + ((time % 2000) << 32) : DOSTIME_BEFORE_1980;
    }
}
