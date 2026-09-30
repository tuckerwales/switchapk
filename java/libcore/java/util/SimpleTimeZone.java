package java.util;

public class SimpleTimeZone extends TimeZone {
    private int rawOffset;

    public SimpleTimeZone(int rawOffset, String id) {
        this.rawOffset = rawOffset;
        setID(id);
    }

    public int getOffset(int era, int year, int month, int day, int dayOfWeek, int millis) {
        return rawOffset;
    }

    public void setRawOffset(int offsetMillis) {
        rawOffset = offsetMillis;
    }

    public int getRawOffset() {
        return rawOffset;
    }

    public boolean useDaylightTime() {
        return false;
    }

    public boolean inDaylightTime(Date date) {
        return false;
    }
}
