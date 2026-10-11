package java.util.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

public class SimpleFormatter extends Formatter {
    /** The JDK default for java.util.logging.SimpleFormatter.format. */
    private static final String DEFAULT_FORMAT = "%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %2$s%n%4$s: %5$s%6$s%n";

    public SimpleFormatter() {
    }

    public String format(LogRecord record) {
        Date date = new Date(record.getMillis());
        String source;
        if (record.getSourceClassName() != null) {
            source = record.getSourceClassName();
            if (record.getSourceMethodName() != null) {
                source += " " + record.getSourceMethodName();
            }
        } else {
            source = record.getLoggerName();
        }
        String message = formatMessage(record);
        String throwable = "";
        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println();
            record.getThrown().printStackTrace(pw);
            pw.close();
            throwable = sw.toString();
        }
        String format = System.getProperty("java.util.logging.SimpleFormatter.format", DEFAULT_FORMAT);
        return String.format(format, date, source, record.getLoggerName(), record.getLevel().getLocalizedName(),
                message, throwable);
    }
}
