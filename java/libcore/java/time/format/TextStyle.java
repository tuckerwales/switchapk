package java.time.format;

public enum TextStyle {
    FULL(2, 0),
    FULL_STANDALONE(32770, 0),
    SHORT(1, 1),
    SHORT_STANDALONE(32769, 1),
    NARROW(4, 1),
    NARROW_STANDALONE(32772, 1);

    private final int calendarStyle;
    private final int zoneNameStyleIndex;

    TextStyle(int calendarStyle, int zoneNameStyleIndex) {
        this.calendarStyle = calendarStyle;
        this.zoneNameStyleIndex = zoneNameStyleIndex;
    }

    public boolean isStandalone() {
        return (ordinal() & 1) == 1;
    }

    public TextStyle asStandalone() {
        return TextStyle.values()[ordinal() | 1];
    }

    public TextStyle asNormal() {
        return TextStyle.values()[ordinal() & ~1];
    }

    /** The java.util.Calendar display-name style (LONG_FORMAT, SHORT_STANDALONE, ...) as in the JDK. */
    int toCalendarStyle() {
        return calendarStyle;
    }

    /** 0 for long zone names, 1 for short ones, as in the JDK. */
    int zoneNameStyleIndex() {
        return zoneNameStyleIndex;
    }
}
