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
}
