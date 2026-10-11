package java.util.logging;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public class Logger {
    public static final String GLOBAL_LOGGER_NAME = "global";
    @Deprecated
    public static final Logger global = new Logger(GLOBAL_LOGGER_NAME, null);

    static {
        LogManager.getLogManager().addLogger(global);
    }

    private static final Handler[] emptyHandlers = new Handler[0];

    private final String name;
    private final String resourceBundleName;
    private final CopyOnWriteArrayList<Handler> handlers = new CopyOnWriteArrayList<Handler>();
    private volatile Logger parent;
    private volatile Level levelObject;
    private volatile Filter filter;
    private volatile boolean useParentHandlers = true;

    public static final Logger getGlobal() {
        return global;
    }

    protected Logger(String name, String resourceBundleName) {
        this.name = name;
        this.resourceBundleName = resourceBundleName;
    }

    public static Logger getLogger(String name) {
        return LogManager.getLogManager().demandLogger(name);
    }

    public static Logger getLogger(String name, String resourceBundleName) {
        return getLogger(name);
    }

    public static Logger getAnonymousLogger() {
        return getAnonymousLogger(null);
    }

    public static Logger getAnonymousLogger(String resourceBundleName) {
        Logger result = new Logger(null, resourceBundleName);
        result.parent = LogManager.getLogManager().rootLogger;
        return result;
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
        if (!isLoggable(record.getLevel())) {
            return;
        }
        Filter theFilter = filter;
        if (theFilter != null && !theFilter.isLoggable(record)) {
            return;
        }
        Logger logger = this;
        while (logger != null) {
            for (Handler handler : logger.handlers) {
                handler.publish(record);
            }
            if (!logger.useParentHandlers) {
                break;
            }
            logger = logger.parent;
        }
    }

    private void doLog(LogRecord lr) {
        lr.setLoggerName(name);
        lr.setResourceBundleName(resourceBundleName);
        log(lr);
    }

    public void log(Level level, String msg) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(new LogRecord(level, msg));
    }

    public void log(Level level, Supplier<String> msgSupplier) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(new LogRecord(level, msgSupplier.get()));
    }

    public void log(Level level, String msg, Object param1) {
        if (!isLoggable(level)) {
            return;
        }
        LogRecord lr = new LogRecord(level, msg);
        lr.setParameters(new Object[] {param1});
        doLog(lr);
    }

    public void log(Level level, String msg, Object[] params) {
        if (!isLoggable(level)) {
            return;
        }
        LogRecord lr = new LogRecord(level, msg);
        lr.setParameters(params);
        doLog(lr);
    }

    public void log(Level level, String msg, Throwable thrown) {
        if (!isLoggable(level)) {
            return;
        }
        LogRecord lr = new LogRecord(level, msg);
        lr.setThrown(thrown);
        doLog(lr);
    }

    public void log(Level level, Throwable thrown, Supplier<String> msgSupplier) {
        if (!isLoggable(level)) {
            return;
        }
        LogRecord lr = new LogRecord(level, msgSupplier.get());
        lr.setThrown(thrown);
        doLog(lr);
    }

    private LogRecord sourced(Level level, String sourceClass, String sourceMethod, String msg) {
        LogRecord lr = new LogRecord(level, msg);
        lr.setSourceClassName(sourceClass);
        lr.setSourceMethodName(sourceMethod);
        return lr;
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(sourced(level, sourceClass, sourceMethod, msg));
    }

    public void logp(Level level, String sourceClass, String sourceMethod, Supplier<String> msgSupplier) {
        if (!isLoggable(level)) {
            return;
        }
        doLog(sourced(level, sourceClass, sourceMethod, msgSupplier.get()));
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object param1) {
        if (!isLoggable(level)) {
            return;
        }
        LogRecord lr = sourced(level, sourceClass, sourceMethod, msg);
        lr.setParameters(new Object[] {param1});
        doLog(lr);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Object[] params) {
        if (!isLoggable(level)) {
            return;
        }
        LogRecord lr = sourced(level, sourceClass, sourceMethod, msg);
        lr.setParameters(params);
        doLog(lr);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, String msg, Throwable thrown) {
        if (!isLoggable(level)) {
            return;
        }
        LogRecord lr = sourced(level, sourceClass, sourceMethod, msg);
        lr.setThrown(thrown);
        doLog(lr);
    }

    public void logp(Level level, String sourceClass, String sourceMethod, Throwable thrown,
            Supplier<String> msgSupplier) {
        if (!isLoggable(level)) {
            return;
        }
        LogRecord lr = sourced(level, sourceClass, sourceMethod, msgSupplier.get());
        lr.setThrown(thrown);
        doLog(lr);
    }

    // No resource bundles here: the bundle name is recorded and the key logged as is.
    @Deprecated
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg) {
        logp(level, sourceClass, sourceMethod, msg);
    }

    @Deprecated
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg,
            Object param1) {
        logp(level, sourceClass, sourceMethod, msg, param1);
    }

    @Deprecated
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg,
            Object[] params) {
        logp(level, sourceClass, sourceMethod, msg, params);
    }

    @Deprecated
    public void logrb(Level level, String sourceClass, String sourceMethod, String bundleName, String msg,
            Throwable thrown) {
        logp(level, sourceClass, sourceMethod, msg, thrown);
    }

    public void entering(String sourceClass, String sourceMethod) {
        logp(Level.FINER, sourceClass, sourceMethod, "ENTRY");
    }

    public void entering(String sourceClass, String sourceMethod, Object param1) {
        logp(Level.FINER, sourceClass, sourceMethod, "ENTRY {0}", param1);
    }

    public void entering(String sourceClass, String sourceMethod, Object[] params) {
        String msg = "ENTRY";
        if (params == null) {
            logp(Level.FINER, sourceClass, sourceMethod, msg);
            return;
        }
        if (!isLoggable(Level.FINER)) {
            return;
        }
        StringBuilder b = new StringBuilder(msg);
        for (int i = 0; i < params.length; i++) {
            b.append(" {").append(i).append('}');
        }
        logp(Level.FINER, sourceClass, sourceMethod, b.toString(), params);
    }

    public void exiting(String sourceClass, String sourceMethod) {
        logp(Level.FINER, sourceClass, sourceMethod, "RETURN");
    }

    public void exiting(String sourceClass, String sourceMethod, Object result) {
        logp(Level.FINER, sourceClass, sourceMethod, "RETURN {0}", result);
    }

    public void throwing(String sourceClass, String sourceMethod, Throwable thrown) {
        if (!isLoggable(Level.FINER)) {
            return;
        }
        LogRecord lr = sourced(Level.FINER, sourceClass, sourceMethod, "THROW");
        lr.setThrown(thrown);
        doLog(lr);
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
        levelObject = newLevel;
    }

    public Level getLevel() {
        return levelObject;
    }

    private int effectiveLevel() {
        for (Logger l = this; l != null; l = l.parent) {
            Level level = l.levelObject;
            if (level != null) {
                return level.intValue();
            }
        }
        return Level.INFO.intValue();
    }

    public boolean isLoggable(Level level) {
        int levelValue = effectiveLevel();
        return level.intValue() >= levelValue && levelValue != Level.OFF.intValue();
    }

    public String getName() {
        return name;
    }

    public void addHandler(Handler handler) throws SecurityException {
        handler.getClass();
        handlers.add(handler);
    }

    public void removeHandler(Handler handler) throws SecurityException {
        if (handler == null) {
            return;
        }
        handlers.remove(handler);
    }

    public Handler[] getHandlers() {
        return handlers.toArray(emptyHandlers);
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
        if (parent == null) {
            throw new NullPointerException();
        }
        this.parent = parent;
    }

    void setParentInternal(Logger parent) {
        this.parent = parent;
    }
}
