package java.util.logging;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

/** getInstant/setInstant are absent until libcore has java.time (WS16). */
public class LogRecord implements Serializable {
    private static final long serialVersionUID = 5372048053134512534L;
    private static final AtomicLong sSequence = new AtomicLong();

    private Level level;
    private long sequenceNumber;
    private String sourceClassName;
    private String sourceMethodName;
    private String message;
    private long threadID;
    private long millis;
    private Throwable thrown;
    private String loggerName;
    private String resourceBundleName;
    private transient Object[] parameters;
    // As in OpenJDK: the caller is found on the stack the first time the source is asked for, unless set.
    private transient boolean needToInferCaller = true;

    public LogRecord(Level level, String msg) {
        if (level == null) throw new NullPointerException();
        this.level = level;
        this.message = msg;
        this.sequenceNumber = sSequence.getAndIncrement();
        this.threadID = Thread.currentThread().getId();
        this.millis = System.currentTimeMillis();
    }

    public String getLoggerName() { return loggerName; }
    public void setLoggerName(String name) { loggerName = name; }
    public String getResourceBundleName() { return resourceBundleName; }
    public void setResourceBundleName(String name) { resourceBundleName = name; }
    public Level getLevel() { return level; }

    public void setLevel(Level level) {
        if (level == null) throw new NullPointerException();
        this.level = level;
    }

    public long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(long seq) { sequenceNumber = seq; }
    public String getSourceClassName() {
        if (needToInferCaller) inferCaller();
        return sourceClassName;
    }

    public void setSourceClassName(String sourceClassName) {
        this.sourceClassName = sourceClassName;
        needToInferCaller = false;
    }

    public String getSourceMethodName() {
        if (needToInferCaller) inferCaller();
        return sourceMethodName;
    }

    public void setSourceMethodName(String sourceMethodName) {
        this.sourceMethodName = sourceMethodName;
        needToInferCaller = false;
    }

    /** The first frame after the innermost run of Logger frames that is not logging or reflection code. */
    private void inferCaller() {
        needToInferCaller = false;
        StackTraceElement[] stack = new Throwable().getStackTrace();
        boolean lookingForLogger = true;
        for (StackTraceElement e : stack) {
            String cname = e.getClassName();
            boolean isLoggerImpl = cname.equals("java.util.logging.Logger")
                    || cname.startsWith("java.util.logging.LoggingProxyImpl");
            if (lookingForLogger) {
                if (isLoggerImpl) lookingForLogger = false;
            } else if (!isLoggerImpl && !cname.startsWith("java.lang.reflect.") && !cname.startsWith("sun.reflect.")) {
                sourceClassName = cname;
                sourceMethodName = e.getMethodName();
                return;
            }
        }
    }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Object[] getParameters() { return parameters; }
    public void setParameters(Object[] parameters) { this.parameters = parameters; }
    @Deprecated public int getThreadID() { return (int) threadID; }
    @Deprecated public void setThreadID(int threadID) { this.threadID = threadID; }
    public long getLongThreadID() { return threadID; }

    public LogRecord setLongThreadID(long longThreadID) {
        threadID = longThreadID;
        return this;
    }

    public long getMillis() { return millis; }
    @Deprecated public void setMillis(long millis) { this.millis = millis; }
    public Throwable getThrown() { return thrown; }
    public void setThrown(Throwable thrown) { this.thrown = thrown; }
}
