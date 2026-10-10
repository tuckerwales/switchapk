package java.util.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The logger registry. The root logger has level INFO and one ConsoleHandler, which prints to System.err (logcat on
 * Android). Configuration comes only from readConfiguration(InputStream): ".level", "<logger>.level" and
 * "<logger>.useParentHandlers". There is no JMX (getLoggingMXBean is absent).
 */
public class LogManager {
    public static final String LOGGING_MXBEAN_NAME = "java.util.logging:type=Logging";

    private static final LogManager sManager = new LogManager();

    private final HashMap<String, Logger> mLoggers = new HashMap<String, Logger>();
    private final ArrayList<Runnable> mListeners = new ArrayList<Runnable>();
    private Properties mProps = new Properties();
    private final Logger mRoot;

    protected LogManager() {
        mRoot = new Logger("", null);
        mRoot.setLevel(Level.INFO);
        mRoot.addHandler(new ConsoleHandler());
        mLoggers.put("", mRoot);
        if (sManager == null) {
            addLogger(Logger.global);
        }
    }

    public static LogManager getLogManager() { return sManager; }

    synchronized Logger demandLogger(String name) {
        Logger l = mLoggers.get(name);
        if (l == null) {
            l = new Logger(name, null);
            addLogger(l);
        }
        return l;
    }

    public synchronized boolean addLogger(Logger logger) {
        String name = logger.getName();
        if (name == null) throw new NullPointerException();
        if (mLoggers.containsKey(name)) return false;
        mLoggers.put(name, logger);
        // Parent: the nearest registered ancestor by name.
        Logger parent = mRoot;
        String n = name;
        int dot;
        while ((dot = n.lastIndexOf('.')) > 0) {
            n = n.substring(0, dot);
            Logger p = mLoggers.get(n);
            if (p != null) {
                parent = p;
                break;
            }
        }
        if (logger != mRoot) logger.setParentInternal(parent);
        // Descendants whose parent is above the new logger now hang under it.
        String prefix = name + ".";
        for (Logger child : mLoggers.values()) {
            String cn = child.getName();
            if (child == logger || !cn.startsWith(prefix)) continue;
            Logger cp = child.getParent();
            String cpn = cp == null ? "" : cp.getName();
            if (cp == null || cpn.length() < name.length()) child.setParentInternal(logger);
        }
        String level = mProps.getProperty(name + ".level");
        if (level != null) {
            try {
                logger.setLevel(Level.parse(level.trim()));
            } catch (IllegalArgumentException e) {
                // bad value: ignored, as in OpenJDK
            }
        }
        return true;
    }

    public synchronized Logger getLogger(String name) { return mLoggers.get(name); }

    public synchronized Enumeration<String> getLoggerNames() {
        return Collections.enumeration(new ArrayList<String>(mLoggers.keySet()));
    }

    /** Back to the defaults (there is no logging.properties): root at INFO with one ConsoleHandler. */
    public void readConfiguration() throws IOException, SecurityException {
        reset();
        mRoot.addHandler(new ConsoleHandler());
        runListeners();
    }

    private void runListeners() {
        ArrayList<Runnable> listeners;
        synchronized (this) {
            listeners = new ArrayList<Runnable>(mListeners);
        }
        for (Runnable r : listeners) r.run();
    }

    public void reset() throws SecurityException {
        synchronized (this) {
            mProps = new Properties();
            for (Logger l : mLoggers.values()) {
                for (Handler h : l.getHandlers()) {
                    l.removeHandler(h);
                    try {
                        h.close();
                    } catch (Exception e) {
                        // ignored, as in OpenJDK
                    }
                }
                l.setLevel(l == mRoot ? Level.INFO : null);
            }
        }
    }

    public void readConfiguration(InputStream ins) throws IOException, SecurityException {
        Properties p = new Properties();
        p.load(ins);
        reset();
        synchronized (this) {
            mProps = p;
            for (Logger l : mLoggers.values()) applyProperties(l);
            String handlers = p.getProperty("handlers");
            if (handlers != null) {
                for (String cls : handlers.split("[\\s,]+")) {
                    if (cls.isEmpty()) continue;
                    try {
                        mRoot.addHandler((Handler) Class.forName(cls).newInstance());
                    } catch (Exception e) {
                        System.err.println("Can't load log handler \"" + cls + "\"");
                        System.err.println("" + e);
                    }
                }
            }
        }
        runListeners();
    }

    private void applyProperties(Logger l) {
        String prefix = l.getName().isEmpty() ? "" : l.getName();
        String level = mProps.getProperty(prefix + ".level");
        if (level != null) {
            try {
                l.setLevel(Level.parse(level.trim()));
            } catch (IllegalArgumentException e) {
                // ignored
            }
        }
        String up = mProps.getProperty(prefix + ".useParentHandlers");
        if (up != null) l.setUseParentHandlers(Boolean.parseBoolean(up.trim()));
    }

    public void updateConfiguration(Function<String, BiFunction<String, String, String>> mapper) throws IOException {
        // No logging.properties file: nothing to merge.
    }

    public void updateConfiguration(InputStream ins, Function<String, BiFunction<String, String, String>> mapper)
            throws IOException {
        Properties next = new Properties();
        next.load(ins);
        synchronized (this) {
            java.util.HashSet<String> keys = new java.util.HashSet<String>(mProps.stringPropertyNames());
            keys.addAll(next.stringPropertyNames());
            Properties merged = new Properties();
            for (String k : keys) {
                String v = mapper == null ? next.getProperty(k)
                        : mapper.apply(k).apply(mProps.getProperty(k), next.getProperty(k));
                if (v != null) merged.setProperty(k, v);
            }
            mProps = merged;
            for (Logger l : mLoggers.values()) applyProperties(l);
        }
        runListeners();
    }

    public synchronized String getProperty(String name) { return mProps.getProperty(name); }
    public void checkAccess() throws SecurityException {}

    public synchronized LogManager addConfigurationListener(Runnable listener) {
        if (listener == null) throw new NullPointerException();
        mListeners.add(listener);
        return this;
    }

    public synchronized void removeConfigurationListener(Runnable listener) {
        if (listener == null) throw new NullPointerException();
        mListeners.remove(listener);
    }
}
