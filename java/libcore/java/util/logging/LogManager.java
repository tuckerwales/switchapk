package java.util.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * The logger namespace. As on Android, the root logger logs INFO and above
 * to the platform log, tagged with the logger's name; there is no
 * logging.properties to read.
 */
public class LogManager {
    public static final String LOGGING_MXBEAN_NAME = "java.util.logging:type=Logging";

    private static final LogManager manager = new LogManager();

    private final HashMap<String, Logger> loggers = new HashMap<String, Logger>();
    private final Properties props = new Properties();
    final Logger root;

    protected LogManager() {
        root = new Logger("", null, true);
        root.setLevel(Level.INFO);
        root.addHandler(new LogcatHandler());
        loggers.put("", root);
    }

    public static LogManager getLogManager() {
        return manager;
    }

    public synchronized boolean addLogger(Logger logger) {
        String name = logger.getName();
        if (name == null) throw new NullPointerException();
        if (loggers.containsKey(name)) return false;
        loggers.put(name, logger);
        if (logger != root && logger.getParentInternal() == null) logger.setParentInternal(parentFor(name));
        // Loggers below this one that skipped it now get it as parent.
        for (Logger l : loggers.values()) {
            if (l != logger && l.getName().startsWith(name + ".") && l.getParentInternal() != null
                    && (l.getParentInternal() == root || !l.getParentInternal().getName().startsWith(name + "."))
                    && isAncestorCandidate(name, l.getParentInternal().getName())) {
                l.setParentInternal(logger);
            }
        }
        return true;
    }

    private static boolean isAncestorCandidate(String newName, String currentParent) {
        return currentParent.isEmpty() || newName.startsWith(currentParent + ".");
    }

    private Logger parentFor(String name) {
        int dot = name.lastIndexOf('.');
        while (dot > 0) {
            Logger p = loggers.get(name.substring(0, dot));
            if (p != null) return p;
            dot = name.lastIndexOf('.', dot - 1);
        }
        return root;
    }

    public synchronized Logger getLogger(String name) {
        return loggers.get(name);
    }

    public synchronized Enumeration<String> getLoggerNames() {
        return Collections.enumeration(new java.util.ArrayList<String>(loggers.keySet()));
    }

    public void readConfiguration() throws IOException, SecurityException {
    }

    public void readConfiguration(InputStream ins) throws IOException, SecurityException {
        synchronized (this) {
            props.load(ins);
        }
    }

    public void reset() throws SecurityException {
        synchronized (this) {
            props.clear();
            for (Logger l : loggers.values()) {
                for (Handler h : l.getHandlers()) {
                    l.removeHandler(h);
                    try {
                        h.close();
                    } catch (Exception ignored) {
                    }
                }
                if (l != root) l.setLevel(null);
            }
            root.setLevel(Level.INFO);
        }
    }

    public synchronized String getProperty(String name) {
        return props.getProperty(name);
    }

    public void checkAccess() throws SecurityException {
    }

    synchronized Logger demandLogger(String name) {
        Logger l = loggers.get(name);
        if (l == null) {
            l = new Logger(name, null, false);
            addLogger(l);
        }
        return l;
    }

    /** Writes records to the platform log at the matching priority, tagged with the logger name. */
    static final class LogcatHandler extends Handler {
        LogcatHandler() {
            setFormatter(new Formatter() {
                public String format(LogRecord r) {
                    String msg = formatMessage(r);
                    if (r.getThrown() == null) return msg;
                    java.io.StringWriter sw = new java.io.StringWriter();
                    r.getThrown().printStackTrace(new java.io.PrintWriter(sw));
                    return msg + "\n" + sw;
                }
            });
        }

        public void publish(LogRecord record) {
            if (!isLoggable(record)) return;
            int v = record.getLevel().intValue();
            int priority = v >= 1000 ? libcore.io.Logcat.ERROR : v >= 900 ? libcore.io.Logcat.WARN
                    : v >= 800 ? libcore.io.Logcat.INFO : libcore.io.Logcat.DEBUG;
            String tag = record.getLoggerName();
            if (tag == null || tag.isEmpty()) tag = "global";
            int dot = tag.lastIndexOf('.');
            if (tag.length() > 23 && dot >= 0) tag = tag.substring(dot + 1);
            try {
                libcore.io.Logcat.println(priority, tag, getFormatter().format(record));
            } catch (RuntimeException e) {
                reportError(null, e, ErrorManager.FORMAT_FAILURE);
            }
        }

        public void flush() {
        }

        public void close() {
        }
    }
}
