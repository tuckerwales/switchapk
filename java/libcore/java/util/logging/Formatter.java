package java.util.logging;

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

    /** As the JDK: MessageFormat only when the message has a {0} to {3} style placeholder. */
    public String formatMessage(LogRecord record) {
        String format = record.getMessage();
        Object[] parameters = record.getParameters();
        if (format == null || parameters == null || parameters.length == 0) {
            return format;
        }
        try {
            int index = -1;
            int fence = format.length() - 1;
            while ((index = format.indexOf('{', index + 1)) > -1) {
                if (index >= fence) {
                    break;
                }
                char digit = format.charAt(index + 1);
                if (digit >= '0' && digit <= '9') {
                    return java.text.MessageFormat.format(format, parameters);
                }
            }
            return format;
        } catch (Exception ex) {
            return format;
        }
    }
}
