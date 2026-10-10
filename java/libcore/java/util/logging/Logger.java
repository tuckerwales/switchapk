package java.util.logging;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Named loggers in a dot-separated hierarchy. The resource bundle overloads
 * are left out until java.util.ResourceBundle exists.
 */
public class Logger {
    public static final String GLOBAL_LOGGER_NAME = "global";

    @Deprecated
    public static final Logger global = new Logger(GLOBAL_LOGGER_NAME, null, false);

    private final String name;
    private final String resourceBundleName;
    private volatile Level level;
    private volatile Logger parent;
    private volatile Filter filter;
    private volatile boolean useParentHandlers = true;
    private final CopyOnWriteArrayList<Handler> handlers = new CopyOnWriteArrayList<Handler>();

    public static final Logger getGlobal() {
        LogManager.getLogManager().addLogger(global);
        return global;
    }

    protected Logger(String name, String resourceBundleName) {
        this(name, resourceBundleName, false);
        if (name == null) parent = LogManager.getLogManager().root;
    }

    Logger(String name, String resourceBundleName, boolean isRoot) {
        this.name = name;
        this.resourceBundleName = resourceBundleName;
    }

    public static Logger getLogger(String name) {
        if (name == null) throw new NullPointerException();
        return LogManager.getLogManager().demandLogger(name);
    }

    public static Logger getLogger(String name, String resourceBundleName) {
        return getLogger(name);
    }

    public static Logger getAnonymousLogger() {
        return getAnonymousLogger(null);
    }

    public static Logger getAnonymousLogger(String resourceBundleName) {
        Logger l = new Logger(null, resourceBundleName, false);
        l.parent = LogManager.getLogManager().root;
        return l;
    }

    public String getResourceBundleName() {
        return resourceBundleName;
    }

    public void setFilter(Filter newFilter) throws SecurityException {
        filter = newFilter;
    }

    public Filter getFilter() {
        return filter;
    }

    public void log(LogRecord record) {
        if (!isLoggable(record.getLevel())) return;
        Filter f = filter;
        if (f != null && !f.isLoggable(record)) return;
        Logger l = this;
        while (l != null) {
            for (Handler h : l.handlers) h.publish(record);
            if (!l.useParentHandlers) break;
            l = l.parent;
        }
    }

    private void doLog(LogRecord r) {
        r.setLoggerName(name);
        log(r);
    }

    public void log(Level level, String msg) {
        if (!isLoggable(level)) return;
        doLog(new LogRecord(level, msg));
    }

    public void log(Level level, Supplier<String> msgSupplier) {
        if (!isLoggable(level)) return;
        doLog(new LogRecord(level, msgSupplier.get()));
    }

    public void log(Level level, String msg, Object param1) {
        if (!isLoggable(level)) return;
        LogRecord r = new LogRecord(level, msg);
        r.setParameters(new Object[] {param1});
        doLog(r);
    }

    public void log(Level level, String msg, Object[] params) {
        if (!isLoggable(level)) return;
        LogRecord r = new LogRecord(level, msg);
        r.setParameters(params);
        doLog(r);
    }

    public void log(Level level, String msg, Throwable thrown) {
        if (!isLoggable(level)) return;
        LogRecord r = new LogRecord(level, msg);
        r.setThrown(thrown);
        doLog(r);
    }

    public void log(Level level, Throwable thrown, Supplier<String> msgSupplier) {
        if (!isLoggable(level)) return;
        LogRecord r = new LogRecord(level, msgSupplier.get());
        r.setThrown(thrown);
        doLog(r);
    }

    private LogRecord sourced(Level level, String sourceClass, String sourceMethod, String msg) {
        LogRecord r = new LogRecord(level, msg);
        r.setSourceClassName(sourceClass);
        r.setSourceMethodName(sourceMethod);
        return r;
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg) {
        if (!isLoggable(level)) return;
        doLog(sourced(level, sourceClass, sourceMethod, msg));
    }

    public void logp(Level level, String sourceClass, String sourceMethod, Supplier<String> msgSupplier) {
        if (!isLoggable(level)) return;
        doLog(sourced(level, sourceClass, sourceMethod, msgSupplier.get()));
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object param1) {
        if (!isLoggable(level)) return;
        LogRecord r = sourced(level, sourceClass, sourceMethod, msg);
        r.setParameters(new Object[] {param1});
        doLog(r);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object[] params) {
        if (!isLoggable(level)) return;
        LogRecord r = sourced(level, sourceClass, sourceMethod, msg);
        r.setParameters(params);
        doLog(r);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Throwable thrown) {
        if (!isLoggable(level)) return;
        LogRecord r = sourced(level, sourceClass, sourceMethod, msg);
        r.setThrown(thrown);
        doLog(r);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, Throwable thrown, Supplier<String> msgSupplier) {
        if (!isLoggable(level)) return;
        LogRecord r = sourced(level, sourceClass, sourceMethod, msgSupplier.get());
        r.setThrown(thrown);
        doLog(r);
    }

    @Deprecated
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg) {
        logp(level, sourceClass, sourceMethod, msg);
    }

    @Deprecated
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Object param1) {
        logp(level, sourceClass, sourceMethod, msg, param1);
    }

    @Deprecated
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Object[] params) {
        logp(level, sourceClass, sourceMethod, msg, params);
    }

    @Deprecated
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg, Throwable thrown) {
        logp(level, sourceClass, sourceMethod, msg, thrown);
    }

    public void entering(String sourceClass, String sourceMethod) {
        logp(Level.FINER, sourceClass, sourceMethod, "ENTRY");
    }

    public void entering(String sourceClass, String sourceMethod, Object param1) {
        logp(Level.FINER, sourceClass, sourceMethod, "ENTRY {0}", param1);
    }

    public void entering(String sourceClass, String sourceMethod, Object[] params) {
        StringBuilder msg = new StringBuilder("ENTRY");
        if (params != null) for (int i = 0; i < params.length; i++) msg.append(" {").append(i).append('}');
        logp(Level.FINER, sourceClass, sourceMethod, msg.toString(), params);
    }

    public void exiting(String sourceClass, String sourceMethod) {
        logp(Level.FINER, sourceClass, sourceMethod, "RETURN");
    }

    public void exiting(String sourceClass, String sourceMethod, Object result) {
        logp(Level.FINER, sourceClass, sourceMethod, "RETURN {0}", result);
    }

    public void throwing(String sourceClass, String sourceMethod, Throwable thrown) {
        logp(Level.FINER, sourceClass, sourceMethod, "THROW", thrown);
    }

    public void severe(String msg) {
        log(Level.SEVERE, msg);
    }

    public void warning(String msg) {
        log(Level.WARNING, msg);
    }

    public void info(String msg) {
        log(Level.INFO, msg);
    }

    public void config(String msg) {
        log(Level.CONFIG, msg);
    }

    public void fine(String msg) {
        log(Level.FINE, msg);
    }

    public void finer(String msg) {
        log(Level.FINER, msg);
    }

    public void finest(String msg) {
        log(Level.FINEST, msg);
    }

    public void severe(Supplier<String> msgSupplier) {
        log(Level.SEVERE, msgSupplier);
    }

    public void warning(Supplier<String> msgSupplier) {
        log(Level.WARNING, msgSupplier);
    }

    public void info(Supplier<String> msgSupplier) {
        log(Level.INFO, msgSupplier);
    }

    public void config(Supplier<String> msgSupplier) {
        log(Level.CONFIG, msgSupplier);
    }

    public void fine(Supplier<String> msgSupplier) {
        log(Level.FINE, msgSupplier);
    }

    public void finer(Supplier<String> msgSupplier) {
        log(Level.FINER, msgSupplier);
    }

    public void finest(Supplier<String> msgSupplier) {
        log(Level.FINEST, msgSupplier);
    }

    public void setLevel(Level newLevel) throws SecurityException {
        level = newLevel;
    }

    public Level getLevel() {
        return level;
    }

    private int effectiveLevel() {
        for (Logger l = this; l != null; l = l.parent) {
            Level lv = l.level;
            if (lv != null) return lv.intValue();
        }
        return Level.INFO.intValue();
    }

    public boolean isLoggable(Level level) {
        int threshold = effectiveLevel();
        return level.intValue() >= threshold && threshold != Level.OFF.intValue();
    }

    public String getName() {
        return name;
    }

    public void addHandler(Handler handler) throws SecurityException {
        if (handler == null) throw new NullPointerException();
        handlers.add(handler);
    }

    public void removeHandler(Handler handler) throws SecurityException {
        if (handler != null) handlers.remove(handler);
    }

    public Handler[] getHandlers() {
        return handlers.toArray(new Handler[0]);
    }

    public void setUseParentHandlers(boolean useParentHandlers) {
        this.useParentHandlers = useParentHandlers;
    }

    public boolean getUseParentHandlers() {
        return useParentHandlers;
    }

    public Logger getParent() {
        return parent;
    }

    public void setParent(Logger parent) {
        if (parent == null) throw new NullPointerException();
        this.parent = parent;
    }

    Logger getParentInternal() {
        return parent;
    }

    void setParentInternal(Logger p) {
        parent = p;
    }
}
