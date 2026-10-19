package java.sql;

/** A time of day (JDBC TIME) on 1970-01-01, written hh:mm:ss. The date accessors throw. */
public class Time extends java.util.Date {
    private static final long serialVersionUID = 8397324403548013681L;

    @Deprecated
    public Time(int hour, int minute, int second) {
        super(70, 0, 1, hour, minute, second);
    }

    public Time(long time) {
        super(time);
    }

    public void setTime(long time) {
        super.setTime(time);
    }

    public static Time valueOf(String s) {
        if (s == null) {
            throw new IllegalArgumentException();
        }
        int firstColon = s.indexOf(':');
        int secondColon = s.indexOf(':', firstColon + 1);
        if (!(firstColon > 0 && secondColon > 0 && secondColon < s.length() - 1)) {
            throw new IllegalArgumentException();
        }
        int hour = Integer.parseInt(s.substring(0, firstColon));
        int minute = Integer.parseInt(s.substring(firstColon + 1, secondColon));
        int second = Integer.parseInt(s.substring(secondColon + 1));
        return new Time(hour, minute, second);
    }

    @SuppressWarnings("deprecation")
    public String toString() {
        StringBuilder b = new StringBuilder(8);
        Date.pad(b, super.getHours(), 2);
        b.append(':');
        Date.pad(b, super.getMinutes(), 2);
        b.append(':');
        Date.pad(b, super.getSeconds(), 2);
        return b.toString();
    }

    @Deprecated
    public int getYear() {
        throw new IllegalArgumentException();
    }

    @Deprecated
    public int getMonth() {
        throw new IllegalArgumentException();
    }

    @Deprecated
    public int getDay() {
        throw new IllegalArgumentException();
    }

    @Deprecated
    public int getDate() {
        throw new IllegalArgumentException();
    }

    @Deprecated
    public void setYear(int i) {
        throw new IllegalArgumentException();
    }

    @Deprecated
    public void setMonth(int i) {
        throw new IllegalArgumentException();
    }

    @Deprecated
    public void setDate(int i) {
        throw new IllegalArgumentException();
    }
}
