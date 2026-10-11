package java.util.logging;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

public abstract class Handler {
    private volatile Filter filter;
    private volatile Formatter formatter;
    private volatile Level logLevel = Level.ALL;
    private volatile ErrorManager errorManager = new ErrorManager();
    private volatile String encoding;

    protected Handler() {
    }

    public abstract void publish(LogRecord record);

    public abstract void flush();

    public abstract void close() throws SecurityException;

    public synchronized void setFormatter(Formatter newFormatter) throws SecurityException {
        newFormatter.getClass();
        formatter = newFormatter;
    }

    public Formatter getFormatter() {
        return formatter;
    }

    public synchronized void setEncoding(String encoding) throws SecurityException, UnsupportedEncodingException {
        if (encoding != null) {
            try {
                if (!Charset.isSupported(encoding)) {
                    throw new UnsupportedEncodingException(encoding);
                }
            } catch (java.nio.charset.IllegalCharsetNameException e) {
                throw new UnsupportedEncodingException(encoding);
            }
        }
        this.encoding = encoding;
    }

    public String getEncoding() {
        return encoding;
    }

    public synchronized void setFilter(Filter newFilter) throws SecurityException {
        filter = newFilter;
    }

    public Filter getFilter() {
        return filter;
    }

    public synchronized void setErrorManager(ErrorManager em) {
        if (em == null) {
            throw new NullPointerException();
        }
        errorManager = em;
    }

    public ErrorManager getErrorManager() {
        return errorManager;
    }

    protected void reportError(String msg, Exception ex, int code) {
        try {
            errorManager.error(msg, ex, code);
        } catch (Exception ex2) {
            System.err.println("Handler.reportError caught:");
            ex2.printStackTrace();
        }
    }

    public synchronized void setLevel(Level newLevel) throws SecurityException {
        if (newLevel == null) {
            throw new NullPointerException();
        }
        logLevel = newLevel;
    }

    public Level getLevel() {
        return logLevel;
    }

    public boolean isLoggable(LogRecord record) {
        int levelValue = getLevel().intValue();
        if (record == null) {
            return false;
        }
        if (record.getLevel().intValue() < levelValue || levelValue == Level.OFF.intValue()) {
            return false;
        }
        Filter f = getFilter();
        return f == null || f.isLoggable(record);
    }
}
