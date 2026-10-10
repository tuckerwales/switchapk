package java.util.logging;

import java.io.PrintWriter;
import java.io.StringWriter;

/** "LEVEL: message" lines (the OpenJDK two-line default needs java.time; the format property is not read). */
public class SimpleFormatter extends Formatter {
    public SimpleFormatter() {}

    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append(record.getLevel().getLocalizedName()).append(": ").append(formatMessage(record));
        sb.append(System.lineSeparator());
        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            record.getThrown().printStackTrace(pw);
            pw.flush();
            sb.append(sw);
        }
        return sb.toString();
    }
}
