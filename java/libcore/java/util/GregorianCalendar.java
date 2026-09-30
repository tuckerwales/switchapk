package java.util;

public class GregorianCalendar extends Calendar {
    public static final int BC = 0;
    public static final int AD = 1;

    public GregorianCalendar() {
        this(TimeZone.getDefault());
    }

    public GregorianCalendar(TimeZone zone) {
        super(zone, Locale.getDefault());
        setTimeInMillis(System.currentTimeMillis());
    }

    public GregorianCalendar(Locale aLocale) {
        this();
    }

    public GregorianCalendar(TimeZone zone, Locale aLocale) {
        this(zone);
    }

    public GregorianCalendar(int year, int month, int dayOfMonth) {
        this(year, month, dayOfMonth, 0, 0, 0);
    }

    public GregorianCalendar(int year, int month, int dayOfMonth, int hourOfDay, int minute) {
        this(year, month, dayOfMonth, hourOfDay, minute, 0);
    }

    public GregorianCalendar(int year, int month, int dayOfMonth, int hourOfDay, int minute, int second) {
        this();
        set(year, month, dayOfMonth, hourOfDay, minute, second);
        set(MILLISECOND, 0);
    }

    public boolean isLeapYear(int year) {
        return (year % 4 == 0) && (year % 100 != 0 || year % 400 == 0);
    }

    static long daysFromCivil(long y, long m, long d) {
        y -= m <= 2 ? 1 : 0;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    private static final int[] MONTH_DAYS = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    private int monthLength(int year, int month) {
        return month == 1 && isLeapYear(year) ? 29 : MONTH_DAYS[month];
    }

    protected void computeTime() {
        long y = fields[YEAR];
        long m = fields[MONTH];
        if (lastSetField == DAY_OF_YEAR) {
            long days = daysFromCivil(y, 1, 1) + fields[DAY_OF_YEAR] - 1;
            time = days * 86400000L + timeOfDayMillis() - getTimeZone().getRawOffset();
            return;
        }
        y += Math.floorDiv(m, 12);
        m = Math.floorMod(m, 12);
        long days = daysFromCivil(y, m + 1, 1) + fields[DAY_OF_MONTH] - 1;
        time = days * 86400000L + timeOfDayMillis() - getTimeZone().getRawOffset();
    }

    private long timeOfDayMillis() {
        long h = (lastSetField == HOUR || lastSetField == AM_PM) ? fields[HOUR] + 12L * fields[AM_PM] : fields[HOUR_OF_DAY];
        return ((h * 60 + fields[MINUTE]) * 60 + fields[SECOND]) * 1000L + fields[MILLISECOND];
    }

    protected void computeFields() {
        long local = time + getTimeZone().getRawOffset();
        long days = Math.floorDiv(local, 86400000L);
        long ms = Math.floorMod(local, 86400000L);
        long z = days + 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long d = doy - (153 * mp + 2) / 5 + 1;
        long m = mp < 10 ? mp + 3 : mp - 9;
        if (m <= 2) {
            y++;
        }
        fields[ERA] = y > 0 ? AD : BC;
        fields[YEAR] = (int) y;
        fields[MONTH] = (int) m - 1;
        fields[DAY_OF_MONTH] = (int) d;
        fields[DAY_OF_YEAR] = (int) (days - daysFromCivil(y, 1, 1)) + 1;
        fields[DAY_OF_WEEK] = (int) (Math.floorMod(days + 4, 7)) + 1;
        fields[DAY_OF_WEEK_IN_MONTH] = (int) ((d - 1) / 7) + 1;
        fields[WEEK_OF_YEAR] = (fields[DAY_OF_YEAR] - 1) / 7 + 1;
        fields[WEEK_OF_MONTH] = (int) ((d - 1) / 7) + 1;
        int hod = (int) (ms / 3600000L);
        fields[HOUR_OF_DAY] = hod;
        fields[AM_PM] = hod >= 12 ? PM : AM;
        fields[HOUR] = hod % 12;
        fields[MINUTE] = (int) (ms / 60000L % 60);
        fields[SECOND] = (int) (ms / 1000L % 60);
        fields[MILLISECOND] = (int) (ms % 1000L);
        fields[ZONE_OFFSET] = getTimeZone().getRawOffset();
        fields[DST_OFFSET] = 0;
        lastSetField = -1;
    }

    public void add(int field, int amount) {
        if (amount == 0) {
            return;
        }
        getTimeInMillis();
        switch (field) {
            case YEAR:
            case MONTH: {
                int y = get(YEAR);
                int m = get(MONTH);
                int total = y * 12 + m + (field == YEAR ? amount * 12 : amount);
                int ny = Math.floorDiv(total, 12);
                int nm = Math.floorMod(total, 12);
                int d = Math.min(get(DAY_OF_MONTH), monthLength(ny, nm));
                fields[YEAR] = ny;
                fields[MONTH] = nm;
                fields[DAY_OF_MONTH] = d;
                lastSetField = MONTH;
                computeTime();
                computeFields();
                isTimeSet = true;
                areFieldsSet = true;
                return;
            }
            case DATE:
            case DAY_OF_YEAR:
            case DAY_OF_WEEK:
                setTimeInMillis(time + amount * 86400000L);
                return;
            case WEEK_OF_YEAR:
            case WEEK_OF_MONTH:
            case DAY_OF_WEEK_IN_MONTH:
                setTimeInMillis(time + amount * 7L * 86400000L);
                return;
            case AM_PM:
                setTimeInMillis(time + amount * 12L * 3600000L);
                return;
            case HOUR:
            case HOUR_OF_DAY:
                setTimeInMillis(time + amount * 3600000L);
                return;
            case MINUTE:
                setTimeInMillis(time + amount * 60000L);
                return;
            case SECOND:
                setTimeInMillis(time + amount * 1000L);
                return;
            case MILLISECOND:
                setTimeInMillis(time + amount);
                return;
            default:
                throw new IllegalArgumentException();
        }
    }

    public int getActualMaximum(int field) {
        switch (field) {
            case DAY_OF_MONTH:
                return monthLength(get(YEAR), get(MONTH));
            case DAY_OF_YEAR:
                return isLeapYear(get(YEAR)) ? 366 : 365;
            default:
                return getMaximum(field);
        }
    }

    private static final int[] MIN = {0, 1, 0, 1, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, -13 * 3600000, 0};
    private static final int[] MAX = {1, 292278994, 11, 53, 6, 31, 366, 7, 6, 1, 11, 23, 59, 59, 999, 14 * 3600000, 7200000};

    public int getMinimum(int field) {
        return MIN[field];
    }

    public int getMaximum(int field) {
        return MAX[field];
    }
}
