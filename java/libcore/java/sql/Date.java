package java.sql;

/** A date with no time of day (JDBC DATE), written yyyy-mm-dd. The time-of-day accessors throw. */
public class Date extends java.util.Date {
    private static final long serialVersionUID = 1511598038487230103L;

    @Deprecated
    public Date(int year, int month, int day) {
        super(year, month, day);
    }

    public Date(long date) {
        super(date);
    }

    public void setTime(long date) {
        super.setTime(date);
    }

    public static Date valueOf(String s) {
        if (s == null) {
            throw new IllegalArgumentException();
        }
        Date d = null;
        int firstDash = s.indexOf('-');
        int secondDash = s.indexOf('-', firstDash + 1);
        if (firstDash > 0 && secondDash > 0 && secondDash < s.length() - 1) {
            String yyyy = s.substring(0, firstDash);
            String mm = s.substring(firstDash + 1, secondDash);
            String dd = s.substring(secondDash + 1);
            if (yyyy.length() == 4 && mm.length() >= 1 && mm.length() <= 2 && dd.length() >= 1 && dd.length() <= 2) {
                int year = Integer.parseInt(yyyy);
                int month = Integer.parseInt(mm);
                int day = Integer.parseInt(dd);
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    d = new Date(year - 1900, month - 1, day);
                }
            }
        }
        if (d == null) {
            throw new IllegalArgumentException();
        }
        return d;
    }

    static void pad(StringBuilder b, int value, int width) {
        String s = Integer.toString(value);
        for (int i = s.length(); i < width; i++) {
            b.append('0');
        }
        b.append(s);
    }

    @SuppressWarnings("deprecation")
    public String toString() {
        StringBuilder b = new StringBuilder(10);
        pad(b, super.getYear() + 1900, 4);
        b.append('-');
        pad(b, super.getMonth() + 1, 2);
        b.append('-');
        pad(b, super.getDate(), 2);
        return b.toString();
    }

    @Deprecated
    public int getHours() {
        throw new IllegalArgumentException();
    }

    @Deprecated
    public int getMinutes() {
        throw new IllegalArgumentException();
    }

    @Deprecated
    public int getSeconds() {
        throw new IllegalArgumentException();
    }

    @Deprecated
    public void setHours(int i) {
        throw new IllegalArgumentException();
    }

    @Deprecated
    public void setMinutes(int i) {
        throw new IllegalArgumentException();
    }

    @Deprecated
    public void setSeconds(int i) {
        throw new IllegalArgumentException();
    }

    @SuppressWarnings("deprecation")
    public static Date valueOf(java.time.LocalDate date) {
        return new Date(date.getYear() - 1900, date.getMonthValue() - 1, date.getDayOfMonth());
    }

    @SuppressWarnings("deprecation")
    public java.time.LocalDate toLocalDate() {
        return java.time.LocalDate.of(getYear() + 1900, getMonth() + 1, getDate());
    }

    public java.time.Instant toInstant() {
        throw new java.lang.UnsupportedOperationException();
    }
}
