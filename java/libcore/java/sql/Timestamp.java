package java.sql;

/** A java.util.Date with nanosecond precision (JDBC TIMESTAMP), written yyyy-mm-dd hh:mm:ss.f... */
public class Timestamp extends java.util.Date {
    private static final long serialVersionUID = 2745179027874758501L;
    private static final String FORMAT_ERROR = "Timestamp format must be yyyy-mm-dd hh:mm:ss[.fffffffff]";

    private int nanos;

    @Deprecated
    public Timestamp(int year, int month, int date, int hour, int minute, int second, int nano) {
        super(year, month, date, hour, minute, second);
        if (nano > 999999999 || nano < 0) {
            throw new IllegalArgumentException("nanos > 999999999 or < 0");
        }
        nanos = nano;
    }

    public Timestamp(long time) {
        super((time / 1000) * 1000);
        nanos = (int) ((time % 1000) * 1000000);
        if (nanos < 0) {
            nanos = 1000000000 + nanos;
            super.setTime(((time / 1000) - 1) * 1000);
        }
    }

    public void setTime(long time) {
        super.setTime((time / 1000) * 1000);
        nanos = (int) ((time % 1000) * 1000000);
        if (nanos < 0) {
            nanos = 1000000000 + nanos;
            super.setTime(((time / 1000) - 1) * 1000);
        }
    }

    public long getTime() {
        long time = super.getTime();
        return time + (nanos / 1000000);
    }

    public static Timestamp valueOf(String s) {
        if (s == null) {
            throw new IllegalArgumentException("null string");
        }
        s = s.trim();
        int dividingSpace = s.indexOf(' ');
        if (dividingSpace < 0) {
            throw new IllegalArgumentException(FORMAT_ERROR);
        }
        String dateS = s.substring(0, dividingSpace);
        String timeS = s.substring(dividingSpace + 1);
        int firstDash = dateS.indexOf('-');
        int secondDash = dateS.indexOf('-', firstDash + 1);
        int firstColon = timeS.indexOf(':');
        int secondColon = timeS.indexOf(':', firstColon + 1);
        int period = timeS.indexOf('.', secondColon + 1);
        int year = 0, month = 0, day = 0, hour, minute, second, aNanos = 0;
        boolean parsedDate = false;
        if (firstDash > 0 && secondDash > 0 && secondDash < dateS.length() - 1) {
            String yyyy = dateS.substring(0, firstDash);
            String mm = dateS.substring(firstDash + 1, secondDash);
            String dd = dateS.substring(secondDash + 1);
            if (yyyy.length() == 4 && mm.length() >= 1 && mm.length() <= 2 && dd.length() >= 1 && dd.length() <= 2) {
                year = Integer.parseInt(yyyy);
                month = Integer.parseInt(mm);
                day = Integer.parseInt(dd);
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    parsedDate = true;
                }
            }
        }
        if (!parsedDate) {
            throw new IllegalArgumentException(FORMAT_ERROR);
        }
        if (firstColon > 0 && secondColon > 0 && secondColon < timeS.length() - 1) {
            hour = Integer.parseInt(timeS.substring(0, firstColon));
            minute = Integer.parseInt(timeS.substring(firstColon + 1, secondColon));
            if (period > 0 && period < timeS.length() - 1) {
                second = Integer.parseInt(timeS.substring(secondColon + 1, period));
                int nanoPrecision = timeS.length() - (period + 1);
                if (nanoPrecision > 9 || !Character.isDigit(timeS.charAt(period + 1))) {
                    throw new IllegalArgumentException(FORMAT_ERROR);
                }
                int tmpNanos = Integer.parseInt(timeS.substring(period + 1));
                while (nanoPrecision < 9) {
                    tmpNanos *= 10;
                    nanoPrecision++;
                }
                aNanos = tmpNanos;
            } else if (period > 0) {
                throw new IllegalArgumentException(FORMAT_ERROR);
            } else {
                second = Integer.parseInt(timeS.substring(secondColon + 1));
            }
        } else {
            throw new IllegalArgumentException(FORMAT_ERROR);
        }
        return new Timestamp(year - 1900, month - 1, day, hour, minute, second, aNanos);
    }

    @SuppressWarnings("deprecation")
    public String toString() {
        StringBuilder b = new StringBuilder(29);
        Date.pad(b, super.getYear() + 1900, 4);
        b.append('-');
        Date.pad(b, super.getMonth() + 1, 2);
        b.append('-');
        Date.pad(b, super.getDate(), 2);
        b.append(' ');
        Date.pad(b, super.getHours(), 2);
        b.append(':');
        Date.pad(b, super.getMinutes(), 2);
        b.append(':');
        Date.pad(b, super.getSeconds(), 2);
        b.append('.');
        if (nanos == 0) {
            b.append('0');
        } else {
            int start = b.length();
            Date.pad(b, nanos, 9);
            int end = b.length();
            while (end > start + 1 && b.charAt(end - 1) == '0') {
                end--;
            }
            b.setLength(end);
        }
        return b.toString();
    }

    public int getNanos() {
        return nanos;
    }

    public void setNanos(int n) {
        if (n > 999999999 || n < 0) {
            throw new IllegalArgumentException("nanos > 999999999 or < 0");
        }
        nanos = n;
    }

    public boolean equals(Timestamp ts) {
        return super.equals(ts) && nanos == ts.nanos;
    }

    public boolean equals(Object ts) {
        return ts instanceof Timestamp && equals((Timestamp) ts);
    }

    public boolean before(Timestamp ts) {
        return compareTo(ts) < 0;
    }

    public boolean after(Timestamp ts) {
        return compareTo(ts) > 0;
    }

    public int compareTo(Timestamp ts) {
        long thisTime = getTime();
        long anotherTime = ts.getTime();
        int i = thisTime < anotherTime ? -1 : (thisTime == anotherTime ? 0 : 1);
        if (i == 0) {
            if (nanos < ts.nanos) {
                i = -1;
            } else if (nanos > ts.nanos) {
                i = 1;
            }
        }
        return i;
    }

    public int compareTo(java.util.Date o) {
        if (o instanceof Timestamp) {
            return compareTo((Timestamp) o);
        }
        return compareTo(new Timestamp(o.getTime()));
    }

    public int hashCode() {
        return super.hashCode();
    }
}
