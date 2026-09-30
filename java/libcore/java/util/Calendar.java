package java.util;

public abstract class Calendar implements java.io.Serializable, Cloneable, Comparable<Calendar> {
    public static final int ERA = 0;
    public static final int YEAR = 1;
    public static final int MONTH = 2;
    public static final int WEEK_OF_YEAR = 3;
    public static final int WEEK_OF_MONTH = 4;
    public static final int DATE = 5;
    public static final int DAY_OF_MONTH = 5;
    public static final int DAY_OF_YEAR = 6;
    public static final int DAY_OF_WEEK = 7;
    public static final int DAY_OF_WEEK_IN_MONTH = 8;
    public static final int AM_PM = 9;
    public static final int HOUR = 10;
    public static final int HOUR_OF_DAY = 11;
    public static final int MINUTE = 12;
    public static final int SECOND = 13;
    public static final int MILLISECOND = 14;
    public static final int ZONE_OFFSET = 15;
    public static final int DST_OFFSET = 16;
    public static final int FIELD_COUNT = 17;

    public static final int SUNDAY = 1, MONDAY = 2, TUESDAY = 3, WEDNESDAY = 4, THURSDAY = 5, FRIDAY = 6, SATURDAY = 7;
    public static final int JANUARY = 0, FEBRUARY = 1, MARCH = 2, APRIL = 3, MAY = 4, JUNE = 5, JULY = 6, AUGUST = 7,
            SEPTEMBER = 8, OCTOBER = 9, NOVEMBER = 10, DECEMBER = 11, UNDECIMBER = 12;
    public static final int AM = 0, PM = 1;
    public static final int ALL_STYLES = 0, SHORT = 1, LONG = 2;

    protected int[] fields = new int[FIELD_COUNT];
    protected boolean[] isSet = new boolean[FIELD_COUNT];
    protected long time;
    protected boolean isTimeSet;
    protected boolean areFieldsSet;
    private TimeZone zone;
    private boolean lenient = true;
    private int firstDayOfWeek = SUNDAY;
    private int minimalDaysInFirstWeek = 1;

    protected Calendar() {
        this(TimeZone.getDefault(), Locale.getDefault());
    }

    protected Calendar(TimeZone zone, Locale aLocale) {
        this.zone = zone;
    }

    public static Calendar getInstance() {
        return new GregorianCalendar();
    }

    public static Calendar getInstance(TimeZone zone) {
        return new GregorianCalendar(zone);
    }

    public static Calendar getInstance(Locale aLocale) {
        return new GregorianCalendar();
    }

    public static Calendar getInstance(TimeZone zone, Locale aLocale) {
        return new GregorianCalendar(zone);
    }

    protected abstract void computeTime();

    protected abstract void computeFields();

    public final Date getTime() {
        return new Date(getTimeInMillis());
    }

    public final void setTime(Date date) {
        setTimeInMillis(date.getTime());
    }

    public long getTimeInMillis() {
        if (!isTimeSet) {
            computeTime();
            isTimeSet = true;
            computeFields();
            areFieldsSet = true;
        }
        return time;
    }

    public void setTimeInMillis(long millis) {
        time = millis;
        isTimeSet = true;
        computeFields();
        areFieldsSet = true;
    }

    public int get(int field) {
        getTimeInMillis();
        if (!areFieldsSet) {
            computeFields();
            areFieldsSet = true;
        }
        return fields[field];
    }

    public void set(int field, int value) {
        getTimeInMillis();
        fields[field] = value;
        isSet[field] = true;
        isTimeSet = false;
        areFieldsSet = false;
        lastSetField = field;
    }

    int lastSetField = -1;

    public final void set(int year, int month, int date) {
        set(YEAR, year);
        set(MONTH, month);
        set(DATE, date);
    }

    public final void set(int year, int month, int date, int hourOfDay, int minute) {
        set(year, month, date);
        set(HOUR_OF_DAY, hourOfDay);
        set(MINUTE, minute);
    }

    public final void set(int year, int month, int date, int hourOfDay, int minute, int second) {
        set(year, month, date, hourOfDay, minute);
        set(SECOND, second);
    }

    public final void clear() {
        for (int i = 0; i < FIELD_COUNT; i++) {
            fields[i] = 0;
            isSet[i] = false;
        }
        fields[YEAR] = 1970;
        fields[DATE] = 1;
        time = 0;
        isTimeSet = false;
        areFieldsSet = false;
        lastSetField = HOUR_OF_DAY;
        computeTime();
        isTimeSet = true;
        computeFields();
    }

    public final void clear(int field) {
        set(field, field == DATE ? 1 : 0);
    }

    public final boolean isSet(int field) {
        return isSet[field];
    }

    public abstract void add(int field, int amount);

    public void roll(int field, boolean up) {
        add(field, up ? 1 : -1);
    }

    public void roll(int field, int amount) {
        add(field, amount);
    }

    public void setTimeZone(TimeZone value) {
        zone = value;
        if (isTimeSet) {
            computeFields();
        }
    }

    public TimeZone getTimeZone() {
        return zone;
    }

    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    public boolean isLenient() {
        return lenient;
    }

    public void setFirstDayOfWeek(int value) {
        firstDayOfWeek = value;
    }

    public int getFirstDayOfWeek() {
        return firstDayOfWeek;
    }

    public void setMinimalDaysInFirstWeek(int value) {
        minimalDaysInFirstWeek = value;
    }

    public int getMinimalDaysInFirstWeek() {
        return minimalDaysInFirstWeek;
    }

    public abstract int getActualMaximum(int field);

    public int getActualMinimum(int field) {
        return getMinimum(field);
    }

    public abstract int getMinimum(int field);

    public abstract int getMaximum(int field);

    public boolean before(Object when) {
        return when instanceof Calendar && compareTo((Calendar) when) < 0;
    }

    public boolean after(Object when) {
        return when instanceof Calendar && compareTo((Calendar) when) > 0;
    }

    public int compareTo(Calendar anotherCalendar) {
        return Long.compare(getTimeInMillis(), anotherCalendar.getTimeInMillis());
    }

    public boolean equals(Object obj) {
        return obj instanceof Calendar && ((Calendar) obj).getTimeInMillis() == getTimeInMillis();
    }

    public int hashCode() {
        long t = getTimeInMillis();
        return (int) t ^ (int) (t >> 32);
    }

    public String getDisplayName(int field, int style, Locale locale) {
        String[] months = {"January", "February", "March", "April", "May", "June", "July", "August", "September",
            "October", "November", "December"};
        String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        String s = null;
        if (field == MONTH) {
            s = months[get(MONTH)];
        } else if (field == DAY_OF_WEEK) {
            s = days[get(DAY_OF_WEEK) - 1];
        } else if (field == AM_PM) {
            s = get(AM_PM) == AM ? "AM" : "PM";
        }
        if (s != null && style == SHORT && field != AM_PM) {
            s = s.substring(0, 3);
        }
        return s;
    }

    public Object clone() {
        try {
            Calendar other = (Calendar) super.clone();
            other.fields = fields.clone();
            other.isSet = isSet.clone();
            other.zone = (TimeZone) zone.clone();
            return other;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    public String toString() {
        return getClass().getName() + "[time=" + getTimeInMillis() + "]";
    }
}
