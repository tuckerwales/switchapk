package java.text;

import java.util.Locale;

public class DateFormatSymbols implements java.io.Serializable, Cloneable {
    public DateFormatSymbols() {
    }

    public DateFormatSymbols(Locale locale) {
    }

    public static DateFormatSymbols getInstance() {
        return new DateFormatSymbols();
    }

    public static DateFormatSymbols getInstance(Locale locale) {
        return new DateFormatSymbols();
    }

    public String[] getMonths() {
        return new String[] {"January", "February", "March", "April", "May", "June", "July", "August", "September",
            "October", "November", "December", ""};
    }

    public String[] getShortMonths() {
        return new String[] {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec", ""};
    }

    public String[] getWeekdays() {
        return new String[] {"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    }

    public String[] getShortWeekdays() {
        return new String[] {"", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    }

    public String[] getAmPmStrings() {
        return new String[] {"AM", "PM"};
    }

    public String[] getEras() {
        return new String[] {"BC", "AD"};
    }
}
