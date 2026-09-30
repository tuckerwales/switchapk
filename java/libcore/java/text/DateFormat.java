package java.text;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public abstract class DateFormat extends Format {
    public static final int FULL = 0;
    public static final int LONG = 1;
    public static final int MEDIUM = 2;
    public static final int SHORT = 3;
    public static final int DEFAULT = MEDIUM;

    protected Calendar calendar = Calendar.getInstance();
    protected NumberFormat numberFormat = NumberFormat.getIntegerInstance();

    protected DateFormat() {
    }

    public final StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition fieldPosition) {
        if (obj instanceof Date) {
            return format((Date) obj, toAppendTo, fieldPosition);
        } else if (obj instanceof Number) {
            return format(new Date(((Number) obj).longValue()), toAppendTo, fieldPosition);
        }
        throw new IllegalArgumentException("Cannot format given Object as a Date");
    }

    public abstract StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition);

    public final String format(Date date) {
        return format(date, new StringBuffer(), new FieldPosition(0)).toString();
    }

    public Date parse(String source) throws ParseException {
        ParsePosition pos = new ParsePosition(0);
        Date result = parse(source, pos);
        if (pos.index == 0) {
            throw new ParseException("Unparseable date: \"" + source + "\"", pos.errorIndex);
        }
        return result;
    }

    public abstract Date parse(String source, ParsePosition pos);

    public Object parseObject(String source, ParsePosition pos) {
        return parse(source, pos);
    }

    public static final DateFormat getTimeInstance() {
        return new SimpleDateFormat("h:mm:ss a");
    }

    public static final DateFormat getTimeInstance(int style) {
        return new SimpleDateFormat(style == SHORT ? "h:mm a" : "h:mm:ss a");
    }

    public static final DateFormat getTimeInstance(int style, Locale aLocale) {
        return getTimeInstance(style);
    }

    public static final DateFormat getDateInstance() {
        return new SimpleDateFormat("MMM d, yyyy");
    }

    public static final DateFormat getDateInstance(int style) {
        switch (style) {
            case SHORT: return new SimpleDateFormat("M/d/yy");
            case LONG: return new SimpleDateFormat("MMMM d, yyyy");
            case FULL: return new SimpleDateFormat("EEEE, MMMM d, yyyy");
            default: return getDateInstance();
        }
    }

    public static final DateFormat getDateInstance(int style, Locale aLocale) {
        return getDateInstance(style);
    }

    public static final DateFormat getDateTimeInstance() {
        return new SimpleDateFormat("MMM d, yyyy h:mm:ss a");
    }

    public static final DateFormat getDateTimeInstance(int dateStyle, int timeStyle) {
        return new SimpleDateFormat(((SimpleDateFormat) getDateInstance(dateStyle)).toPattern() + " "
                + ((SimpleDateFormat) getTimeInstance(timeStyle)).toPattern());
    }

    public static final DateFormat getDateTimeInstance(int dateStyle, int timeStyle, Locale aLocale) {
        return getDateTimeInstance(dateStyle, timeStyle);
    }

    public static final DateFormat getInstance() {
        return getDateTimeInstance(SHORT, SHORT);
    }

    public void setCalendar(Calendar newCalendar) {
        calendar = newCalendar;
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public void setNumberFormat(NumberFormat newNumberFormat) {
        numberFormat = newNumberFormat;
    }

    public NumberFormat getNumberFormat() {
        return numberFormat;
    }

    public void setTimeZone(TimeZone zone) {
        calendar.setTimeZone(zone);
    }

    public TimeZone getTimeZone() {
        return calendar.getTimeZone();
    }

    public void setLenient(boolean lenient) {
        calendar.setLenient(lenient);
    }

    public boolean isLenient() {
        return calendar.isLenient();
    }
}
