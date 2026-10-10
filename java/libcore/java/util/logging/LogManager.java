package java.util.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

/**
 * Loggers by name, with parents found along the dotted name. As on Android, the root logger logs at
 * INFO through a handler that writes to android.util.Log (logcat, here the switchapk log).
 */
public class LogManager {
    public static final String LOGGING_MXBEAN_NAME = "java.util.logging:type=Logging";

    private static final LogManager MANAGER = new LogManager();

    private final HashMap<String, Logger> loggers = new HashMap<String, Logger>();
    private final Properties props = new Properties();
    private final ArrayList<Runnable> listeners = new ArrayList<Runnable>();
    final Logger rootLogger;

    protected LogManager() {
        rootLogger = new Logger("", null);
        rootLogger.setLevel(Level.INFO);
        rootLogger.addHandler(new AndroidLogHandler());
        loggers.put("", rootLogger);
    }

    public static LogManager getLogManager() {
        return MANAGER;
    }

    public synchronized boolean addLogger(Logger logger) {
        String name = logger.getName();
        if (name == null) {
            throw new NullPointerException();
        }
        if (loggers.containsKey(name)) {
            return false;
        }
        loggers.put(name, logger);
        String levelProp = props.getProperty(name + ".level");
        if (levelProp != null) {
            try {
                logger.setLevel(Level.parse(levelProp.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        logger.setParentInternal(findParent(name));
        // Loggers below the new one now hang from it.
        for (Logger other : loggers.values()) {
            String n = other.getName();
            if (other != logger && n != null && n.startsWith(name + ".")) {
                Logger p = findParent(n);
                if (p == logger) {
                    other.setParentInternal(logger);
                }
            }
        }
        return true;
    }

    private Logger findParent(String name) {
        if (name.isEmpty()) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        while (dot > 0) {
            Logger p = loggers.get(name.substring(0, dot));
            if (p != null) {
                return p;
            }
            dot = name.lastIndexOf('.', dot - 1);
        }
        return rootLogger;
    }

    synchronized Logger demandLogger(String name) {
        Logger l = loggers.get(name);
        if (l == null) {
            l = new Logger(name, null);
            addLogger(l);
        }
        return l;
    }

    public synchronized Logger getLogger(String name) {
        return loggers.get(name);
    }

    public synchronized Enumeration<String> getLoggerNames() {
        return Collections.enumeration(new ArrayList<String>(loggers.keySet()));
    }

    public void readConfiguration() throws IOException, SecurityException {
    }

    public void reset() throws SecurityException {
        ArrayList<Logger> all;
        synchronized (this) {
            props.clear();
            all = new ArrayList<Logger>(loggers.values());
        }
        for (Logger l : all) {
            for (Handler h : l.getHandlers()) {
                l.removeHandler(h);
                try {
                    h.close();
                } catch (Exception ignored) {
                }
            }
            l.setLevel(l == rootLogger ? Level.INFO : null);
        }
    }

    public void readConfiguration(InputStream ins) throws IOException, SecurityException {
        Properties p = new Properties();
        p.load(ins);
        ArrayList<Runnable> copy;
        synchronized (this) {
            props.clear();
            props.putAll(p);
            for (Logger l : loggers.values()) {
                String levelProp = props.getProperty((l.getName().isEmpty() ? "" : l.getName()) + ".level");
                if (levelProp != null) {
                    try {
                        l.setLevel(Level.parse(levelProp.trim()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            copy = new ArrayList<Runnable>(listeners);
        }
        for (Runnable r : copy) {
            r.run();
        }
    }

    public synchronized String getProperty(String name) {
        return props.getProperty(name);
    }

    public void checkAccess() throws SecurityException {
    }

    public LogManager addConfigurationListener(Runnable listener) {
        listener.getClass();
        synchronized (this) {
            listeners.add(listener);
        }
        return this;
    }

    public void removeConfigurationListener(Runnable listener) {
        synchronized (this) {
            listeners.remove(listener);
        }
    }

    /** Android's AndroidHandler: logcat priorities and tags from the logger name. */
    static final class AndroidLogHandler extends Handler {
        private static java.lang.reflect.Method println;
        private static boolean looked;

        AndroidLogHandler() {
            setFormatter(new Formatter() {
                public String format(LogRecord r) {
                    Throwable thrown = r.getThrown();
                    String msg = formatMessage(r);
                    if (thrown == null) {
                        return msg;
                    }
                    java.io.StringWriter sw = new java.io.StringWriter();
                    java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                    pw.println(msg);
                    thrown.printStackTrace(pw);
                    pw.flush();
                    return sw.toString();
                }
            });
        }

        static String tagOf(String loggerName) {
            if (loggerName == null) {
                return "null";
            }
            int length = loggerName.length();
            if (length <= 23) {
                return loggerName;
            }
            int lastPeriod = loggerName.lastIndexOf('.');
            return length - (lastPeriod + 1) <= 23 ? loggerName.substring(lastPeriod + 1)
                    : loggerName.substring(loggerName.length() - 23);
        }

        static int priorityOf(Level level) {
            int value = level.intValue();
            if (value >= 1000) {
                return 6;
            } else if (value >= 900) {
                return 5;
            } else if (value >= 800) {
                return 4;
            }
            return 3;
        }

        public void publish(LogRecord record) {
            if (!isLoggable(record)) {
                return;
            }
            String message;
            try {
                message = getFormatter().format(record);
            } catch (RuntimeException e) {
                reportError(null, e, ErrorManager.FORMAT_FAILURE);
                return;
            }
            String tag = tagOf(record.getLoggerName());
            int priority = priorityOf(record.getLevel());
            synchronized (AndroidLogHandler.class) {
                if (!looked) {
                    looked = true;
                    try {
                        println = Class.forName("android.util.Log").getMethod("println", int.class, String.class,
                                String.class);
                    } catch (Exception ignored) {
                    }
                }
            }
            if (println != null) {
                try {
                    println.invoke(null, priority, tag, message);
                    return;
                } catch (Exception ignored) {
                }
            }
            System.err.println(tag + ": " + message);
        }

        public void flush() {
        }

        public void close() {
        }
    }
}
