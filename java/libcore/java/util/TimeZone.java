package java.util;

public abstract class TimeZone implements java.io.Serializable, Cloneable {
    public static final int SHORT = 0;
    public static final int LONG = 1;
    private static TimeZone defaultZone;
    private String id;

    public TimeZone() {
    }

    public abstract int getOffset(int era, int year, int month, int day, int dayOfWeek, int milliseconds);

    public int getOffset(long date) {
        return getRawOffset();
    }

    public abstract void setRawOffset(int offsetMillis);

    public abstract int getRawOffset();

    public String getID() {
        return id;
    }

    public void setID(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return id;
    }

    public String getDisplayName(boolean daylight, int style) {
        return id;
    }

    public String getDisplayName(Locale locale) {
        return id;
    }

    public int getDSTSavings() {
        return 0;
    }

    public abstract boolean useDaylightTime();

    public boolean observesDaylightTime() {
        return useDaylightTime();
    }

    public abstract boolean inDaylightTime(Date date);

    public static synchronized TimeZone getTimeZone(String id) {
        int off = 0;
        if (id.startsWith("GMT") && id.length() > 3) {
            String s = id.substring(3);
            int sign = s.charAt(0) == '-' ? -1 : 1;
            String[] hm = s.substring(1).split(":");
            try {
                off = sign * (Integer.parseInt(hm[0]) * 3600000 + (hm.length > 1 ? Integer.parseInt(hm[1]) * 60000 : 0));
            } catch (NumberFormatException e) {
                off = 0;
            }
        }
        return new SimpleTimeZone(off, id);
    }

    public static String[] getAvailableIDs() {
        return new String[] {"UTC", "GMT"};
    }

    public static String[] getAvailableIDs(int rawOffset) {
        return rawOffset == 0 ? getAvailableIDs() : new String[0];
    }

    public static synchronized TimeZone getDefault() {
        if (defaultZone == null) {
            defaultZone = new SimpleTimeZone(0, "UTC");
        }
        return (TimeZone) defaultZone.clone();
    }

    public static synchronized void setDefault(TimeZone timeZone) {
        defaultZone = timeZone;
    }

    public boolean hasSameRules(TimeZone other) {
        return other != null && getRawOffset() == other.getRawOffset();
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }
}
