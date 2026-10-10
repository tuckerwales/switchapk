package java.util.logging;

import java.text.MessageFormat;

public abstract class Formatter {
    protected Formatter() {
    }

    public abstract String format(LogRecord record);

    public String getHead(Handler h) {
        return "";
    }

    public String getTail(Handler h) {
        return "";
    }

    /** Fills {0}-style parameters in, as the JDK does when a message has any. */
    public String formatMessage(LogRecord record) {
        String format = record.getMessage();
        Object[] parameters = record.getParameters();
        if (format == null || parameters == null || parameters.length == 0) return format;
        try {
            if (format.indexOf("{0") >= 0 || format.indexOf("{1") >= 0 || format.indexOf("{2") >= 0 || format.indexOf("{3") >= 0) {
                return MessageFormat.format(format, parameters);
            }
            return format;
        } catch (Exception ex) {
            return format;
        }
    }
}
