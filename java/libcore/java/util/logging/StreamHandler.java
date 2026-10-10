package java.util.logging;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class StreamHandler extends Handler {
    private OutputStream output;
    private boolean doneHeader;
    private Writer writer;

    public StreamHandler() {
        setFormatter(new SimpleFormatter());
        setLevel(Level.INFO);
    }

    public StreamHandler(OutputStream out, Formatter formatter) {
        setFormatter(formatter);
        setLevel(Level.INFO);
        setOutputStream(out);
    }

    protected synchronized void setOutputStream(OutputStream out) throws SecurityException {
        if (out == null) throw new NullPointerException();
        flushAndClose();
        output = out;
        doneHeader = false;
        writer = new OutputStreamWriter(output);
    }

    public synchronized void publish(LogRecord record) {
        if (!isLoggable(record)) return;
        String msg;
        try {
            msg = getFormatter().format(record);
        } catch (Exception ex) {
            reportError(null, ex, ErrorManager.FORMAT_FAILURE);
            return;
        }
        try {
            if (!doneHeader) {
                writer.write(getFormatter().getHead(this));
                doneHeader = true;
            }
            writer.write(msg);
        } catch (Exception ex) {
            reportError(null, ex, ErrorManager.WRITE_FAILURE);
        }
    }

    public boolean isLoggable(LogRecord record) {
        return writer != null && record != null && super.isLoggable(record);
    }

    public synchronized void flush() {
        if (writer != null) {
            try {
                writer.flush();
            } catch (Exception ex) {
                reportError(null, ex, ErrorManager.FLUSH_FAILURE);
            }
        }
    }

    private synchronized void flushAndClose() {
        if (writer != null) {
            try {
                if (!doneHeader) {
                    writer.write(getFormatter().getHead(this));
                    doneHeader = true;
                }
                writer.write(getFormatter().getTail(this));
                writer.flush();
                writer.close();
            } catch (Exception ex) {
                reportError(null, ex, ErrorManager.CLOSE_FAILURE);
            }
            writer = null;
            output = null;
        }
    }

    public synchronized void close() throws SecurityException {
        flushAndClose();
    }
}
