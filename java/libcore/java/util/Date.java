package java.util;

public class Date implements java.io.Serializable, Cloneable, Comparable<Date> {
    private long fastTime;

    public Date() {
        this(System.currentTimeMillis());
    }

    public Date(long date) {
        fastTime = date;
    }

    @Deprecated
    public Date(int year, int month, int date) {
        this(year, month, date, 0, 0, 0);
    }

    @Deprecated
    public Date(int year, int month, int date, int hrs, int min) {
        this(year, month, date, hrs, min, 0);
    }

    @Deprecated
    public Date(String s) {
        this(parse(s));
    }

    @Deprecated
    public Date(int year, int month, int date, int hrs, int min, int sec) {
        Calendar c = Calendar.getInstance();
        c.set(year + 1900, month, date, hrs, min, sec);
        c.set(Calendar.MILLISECOND, 0);
        fastTime = c.getTimeInMillis();
    }

    public Object clone() {
        return new Date(fastTime);
    }

    public long getTime() {
        return fastTime;
    }

    public void setTime(long time) {
        fastTime = time;
    }

    public boolean before(Date when) {
        return fastTime < when.fastTime;
    }

    public boolean after(Date when) {
        return fastTime > when.fastTime;
    }

    public boolean equals(Object obj) {
        return obj instanceof Date && getTime() == ((Date) obj).getTime();
    }

    public int compareTo(Date anotherDate) {
        return Long.compare(fastTime, anotherDate.fastTime);
    }

    public int hashCode() {
        long ht = this.getTime();
        return (int) ht ^ (int) (ht >> 32);
    }

    private Calendar cal() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(fastTime);
        return c;
    }

    @Deprecated
    public int getYear() {
        return cal().get(Calendar.YEAR) - 1900;
    }

    @Deprecated
    public int getMonth() {
        return cal().get(Calendar.MONTH);
    }

    @Deprecated
    public int getDate() {
        return cal().get(Calendar.DAY_OF_MONTH);
    }

    @Deprecated
    public int getDay() {
        return cal().get(Calendar.DAY_OF_WEEK) - 1;
    }

    @Deprecated
    public int getHours() {
        return cal().get(Calendar.HOUR_OF_DAY);
    }

    @Deprecated
    public int getMinutes() {
        return cal().get(Calendar.MINUTE);
    }

    @Deprecated
    public int getSeconds() {
        return cal().get(Calendar.SECOND);
    }

    public String toString() {
        return String.format("%ta %<tb %<td %<tT UTC %<tY", this);
    }

    public String toGMTString() {
        return String.format("%te %<tb %<tY %<tT GMT", this);
    }

    private void setField(int field, int value) {
        Calendar c = cal();
        c.set(field, value);
        fastTime = c.getTimeInMillis();
    }

    @Deprecated
    public void setYear(int year) {
        setField(Calendar.YEAR, year + 1900);
    }

    @Deprecated
    public void setMonth(int month) {
        setField(Calendar.MONTH, month);
    }

    @Deprecated
    public void setDate(int date) {
        setField(Calendar.DAY_OF_MONTH, date);
    }

    @Deprecated
    public void setHours(int hours) {
        setField(Calendar.HOUR_OF_DAY, hours);
    }

    @Deprecated
    public void setMinutes(int minutes) {
        setField(Calendar.MINUTE, minutes);
    }

    @Deprecated
    public void setSeconds(int seconds) {
        setField(Calendar.SECOND, seconds);
    }

    @Deprecated
    public int getTimezoneOffset() {
        return -TimeZone.getDefault().getOffset(fastTime) / 60000;
    }

    @Deprecated
    public String toLocaleString() {
        return String.format("%tb %<te, %<tY %<tl:%<tM:%<tS %<Tp", this);
    }

    @Deprecated
    public static long UTC(int year, int month, int date, int hrs, int min, int sec) {
        Calendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(year + 1900, month, date, hrs, min, sec);
        return c.getTimeInMillis();
    }

    private static final String[] WTB = {
        "am", "pm", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "january",
        "february", "march", "april", "may", "june", "july", "august", "september", "october", "november",
        "december", "gmt", "ut", "utc", "est", "edt", "cst", "cdt", "mst", "mdt", "pst", "pdt"
    };
    private static final int[] TTB = {
        14, 1, 0, 0, 0, 0, 0, 0, 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 10000 + 0, 10000 + 0, 10000 + 0,
        10000 + 5 * 60, 10000 + 4 * 60, 10000 + 6 * 60, 10000 + 5 * 60, 10000 + 7 * 60, 10000 + 6 * 60,
        10000 + 8 * 60, 10000 + 7 * 60
    };

    /* The legacy free-form date parser, ported from the JDK's Date.parse. */
    @Deprecated
    public static long parse(String s) {
        int year = Integer.MIN_VALUE;
        int mon = -1;
        int mday = -1;
        int hour = -1;
        int min = -1;
        int sec = -1;
        int c = -1;
        int i = 0;
        int n = -1;
        int wst = -1;
        int tzoffset = -1;
        int prevc = 0;
        syntax:
        {
            if (s == null) {
                break syntax;
            }
            int limit = s.length();
            while (i < limit) {
                c = s.charAt(i);
                i++;
                if (c <= ' ' || c == ',') {
                    continue;
                }
                if (c == '(') {
                    int depth = 1;
                    while (i < limit) {
                        c = s.charAt(i);
                        i++;
                        if (c == '(') {
                            depth++;
                        } else if (c == ')') {
                            if (--depth <= 0) {
                                break;
                            }
                        }
                    }
                    continue;
                }
                if ('0' <= c && c <= '9') {
                    n = c - '0';
                    while (i < limit && '0' <= (c = s.charAt(i)) && c <= '9') {
                        n = n * 10 + c - '0';
                        i++;
                    }
                    if (prevc == '+' || prevc == '-' && year != Integer.MIN_VALUE) {
                        if (tzoffset != 0 && tzoffset != -1) {
                            break syntax;
                        }
                        if (n < 24) {
                            n = n * 60;
                            if (i < limit && s.charAt(i) == ':') {
                                i++;
                                int mins = 0;
                                while (i < limit && '0' <= (c = s.charAt(i)) && c <= '9') {
                                    mins = mins * 10 + c - '0';
                                    i++;
                                }
                                n += mins;
                            }
                        } else {
                            n = (n % 100) + (n / 100) * 60;
                        }
                        if (prevc == '+') {
                            n = -n;
                        }
                        tzoffset = n;
                    } else if (n >= 70) {
                        if (year != Integer.MIN_VALUE) {
                            break syntax;
                        } else if (c <= ' ' || c == ',' || c == '/' || i >= limit) {
                            year = n;
                        } else {
                            break syntax;
                        }
                    } else if (c == ':') {
                        if (hour < 0) {
                            hour = (byte) n;
                        } else if (min < 0) {
                            min = (byte) n;
                        } else {
                            break syntax;
                        }
                    } else if (c == '/') {
                        if (mon < 0) {
                            mon = (byte) (n - 1);
                        } else if (mday < 0) {
                            mday = (byte) n;
                        } else {
                            break syntax;
                        }
                    } else if (i < limit && c != ',' && c > ' ' && c != '-') {
                        break syntax;
                    } else if (hour >= 0 && min < 0) {
                        min = (byte) n;
                    } else if (min >= 0 && sec < 0) {
                        sec = (byte) n;
                    } else if (mday < 0) {
                        mday = (byte) n;
                    } else if (year == Integer.MIN_VALUE && mon >= 0 && mday >= 0) {
                        year = n;
                    } else {
                        break syntax;
                    }
                    prevc = 0;
                } else if (c == '/' || c == ':' || c == '+' || c == '-') {
                    prevc = c;
                } else {
                    int st = i - 1;
                    while (i < limit) {
                        c = s.charAt(i);
                        if (!('A' <= c && c <= 'Z' || 'a' <= c && c <= 'z')) {
                            break;
                        }
                        i++;
                    }
                    if (i <= st + 1) {
                        break syntax;
                    }
                    int k;
                    for (k = WTB.length; --k >= 0;) {
                        if (WTB[k].regionMatches(true, 0, s, st, i - st)
                                && (i - st >= 3 || WTB[k].length() == i - st)) {
                            int action = TTB[k];
                            if (action != 0) {
                                if (action == 1) {
                                    if (hour > 12 || hour < 1) {
                                        break syntax;
                                    } else if (hour < 12) {
                                        hour += 12;
                                    }
                                } else if (action == 14) {
                                    if (hour > 12 || hour < 1) {
                                        break syntax;
                                    } else if (hour == 12) {
                                        hour = 0;
                                    }
                                } else if (action <= 13) {
                                    if (mon < 0) {
                                        mon = (byte) (action - 2);
                                    } else {
                                        break syntax;
                                    }
                                } else {
                                    tzoffset = action - 10000;
                                }
                            }
                            break;
                        }
                    }
                    if (k < 0) {
                        break syntax;
                    }
                    prevc = 0;
                }
            }
            if (year == Integer.MIN_VALUE || mon < 0 || mday < 0) {
                break syntax;
            }
            if (year < 100) {
                int defaultCenturyStart = new GregorianCalendar().get(Calendar.YEAR) - 80;
                year += (defaultCenturyStart / 100) * 100;
                if (year < defaultCenturyStart) {
                    year += 100;
                }
            }
            if (sec < 0) {
                sec = 0;
            }
            if (min < 0) {
                min = 0;
            }
            if (hour < 0) {
                hour = 0;
            }
            Calendar cal = new GregorianCalendar(tzoffset == -1 ? TimeZone.getDefault() : TimeZone.getTimeZone("UTC"));
            cal.clear();
            cal.set(year, mon, mday, hour, min, sec);
            long t = cal.getTimeInMillis();
            if (tzoffset != -1) {
                t += tzoffset * (60L * 1000);
            }
            return t;
        }
        throw new IllegalArgumentException();
    }
}
