package java.util.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

public class SimpleFormatter extends Formatter {
    public SimpleFormatter() {
    }

    public String format(LogRecord record) {
        String source = record.getSourceClassName() != null
                ? record.getSourceClassName() + (record.getSourceMethodName() != null ? " " + record.getSourceMethodName() : "")
                : record.getLoggerName();
        StringBuilder sb = new StringBuilder();
        sb.append(new Date(record.getMillis())).append(' ').append(source).append('\n');
        sb.append(record.getLevel().getLocalizedName()).append(": ").append(formatMessage(record)).append('\n');
        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            record.getThrown().printStackTrace(pw);
            pw.close();
            sb.append(sw);
        }
        return sb.toString();
    }
}
