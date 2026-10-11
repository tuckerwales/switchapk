package java.util.logging;

public class ConsoleHandler extends StreamHandler {
    public ConsoleHandler() {
        super(System.err, new SimpleFormatter());
        setLevel(Level.INFO);
    }

    public void publish(LogRecord record) {
        super.publish(record);
        flush();
    }

    public void close() {
        flush();
    }
}
