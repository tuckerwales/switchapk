package java.util.logging;

import java.util.concurrent.atomic.AtomicLong;

public class LogRecord implements java.io.Serializable {
    private static final AtomicLong nextSequence = new AtomicLong();

    private Level level;
    private long sequenceNumber;
    private String sourceClassName;
    private String sourceMethodName;
    private String message;
    private int threadID;
    private long millis;
    private Throwable thrown;
    private String loggerName;
    private String resourceBundleName;
    private Object[] parameters;

    public LogRecord(Level level, String msg) {
        if (level == null) throw new NullPointerException();
        this.level = level;
        this.message = msg;
        this.sequenceNumber = nextSequence.getAndIncrement();
        this.threadID = (int) Thread.currentThread().getId();
        this.millis = System.currentTimeMillis();
    }

    public String getLoggerName() {
        return loggerName;
    }

    public void setLoggerName(String name) {
        loggerName = name;
    }

    public String getResourceBundleName() {
        return resourceBundleName;
    }

    public void setResourceBundleName(String name) {
        resourceBundleName = name;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        if (level == null) throw new NullPointerException();
        this.level = level;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(long seq) {
        sequenceNumber = seq;
    }

    public String getSourceClassName() {
        return sourceClassName;
    }

    public void setSourceClassName(String sourceClassName) {
        this.sourceClassName = sourceClassName;
    }

    public String getSourceMethodName() {
        return sourceMethodName;
    }

    public void setSourceMethodName(String sourceMethodName) {
        this.sourceMethodName = sourceMethodName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public void setParameters(Object[] parameters) {
        this.parameters = parameters;
    }

    public int getThreadID() {
        return threadID;
    }

    public void setThreadID(int threadID) {
        this.threadID = threadID;
    }

    public long getMillis() {
        return millis;
    }

    @Deprecated
    public void setMillis(long millis) {
        this.millis = millis;
    }

    public Throwable getThrown() {
        return thrown;
    }

    public void setThrown(Throwable thrown) {
        this.thrown = thrown;
    }
}
