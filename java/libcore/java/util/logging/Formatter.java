package java.util.logging;

import java.text.MessageFormat;

public abstract class Formatter {
    protected Formatter() {}

    public abstract String format(LogRecord record);

    public String getHead(Handler h) { return ""; }
    public String getTail(Handler h) { return ""; }

    public String formatMessage(LogRecord record) {
        String format = record.getMessage();
        Object[] parameters = record.getParameters();
        if (format == null || parameters == null || parameters.length == 0) return format;
        // Same test as OpenJDK: only messages that look like MessageFormat patterns are formatted.
        int index = -1;
        int fence = format.length() - 1;
        while ((index = format.indexOf('{', index + 1)) > -1) {
            if (index >= fence) break;
            char digit = format.charAt(index + 1);
            if (digit >= '0' && digit <= '9') {
                try {
                    return MessageFormat.format(format, parameters);
                } catch (Exception ex) {
                    return format;
                }
            }
        }
        return format;
    }
}
