package java.lang;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Throwable implements java.io.Serializable {
    private String detailMessage;
    private Throwable cause = this;
    private transient Object backtrace;
    private StackTraceElement[] stackTrace;
    private List<Throwable> suppressedExceptions;

    private static final StackTraceElement[] EMPTY_TRACE = new StackTraceElement[0];

    public Throwable() {
        fillInStackTrace();
    }

    public Throwable(String message) {
        fillInStackTrace();
        detailMessage = message;
    }

    public Throwable(String message, Throwable cause) {
        fillInStackTrace();
        detailMessage = message;
        this.cause = cause;
    }

    public Throwable(Throwable cause) {
        fillInStackTrace();
        detailMessage = (cause == null ? null : cause.toString());
        this.cause = cause;
    }

    protected Throwable(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        if (writableStackTrace) {
            fillInStackTrace();
        }
        detailMessage = message;
        this.cause = cause;
    }

    public String getMessage() {
        return detailMessage;
    }

    public String getLocalizedMessage() {
        return getMessage();
    }

    public synchronized Throwable getCause() {
        return (cause == this ? null : cause);
    }

    public synchronized Throwable initCause(Throwable cause) {
        if (this.cause != this) {
            throw new IllegalStateException("Can't overwrite cause with " + cause, this);
        }
        if (cause == this) {
            throw new IllegalArgumentException("Self-causation not permitted", this);
        }
        this.cause = cause;
        return this;
    }

    public String toString() {
        String s = getClass().getName();
        String message = getLocalizedMessage();
        return (message != null) ? (s + ": " + message) : s;
    }

    public void printStackTrace() {
        printStackTrace(System.err);
    }

    public void printStackTrace(PrintStream s) {
        StringBuilder sb = new StringBuilder();
        appendTrace(sb, "", null);
        s.print(sb.toString());
        s.flush();
    }

    public void printStackTrace(PrintWriter s) {
        StringBuilder sb = new StringBuilder();
        appendTrace(sb, "", null);
        s.print(sb.toString());
        s.flush();
    }

    private void appendTrace(StringBuilder sb, String caption, StackTraceElement[] enclosing) {
        sb.append(caption).append(toString()).append('\n');
        StackTraceElement[] trace = getOurStackTrace();
        int m = trace.length - 1;
        if (enclosing != null) {
            int n = enclosing.length - 1;
            while (m >= 0 && n >= 0 && trace[m].equals(enclosing[n])) {
                m--;
                n--;
            }
        }
        for (int i = 0; i <= m; i++) {
            sb.append("\tat ").append(trace[i]).append('\n');
        }
        if (m < trace.length - 1) {
            sb.append("\t... ").append(trace.length - 1 - m).append(" more\n");
        }
        if (suppressedExceptions != null) {
            for (Throwable se : suppressedExceptions) {
                se.appendTrace(sb, "\tSuppressed: ", trace);
            }
        }
        Throwable c = getCause();
        if (c != null && c != this) {
            c.appendTrace(sb, "Caused by: ", trace);
        }
    }

    public synchronized Throwable fillInStackTrace() {
        fillInStackTraceNative();
        stackTrace = null;
        return this;
    }

    private native void fillInStackTraceNative();

    private native StackTraceElement[] getStackTraceNative();

    private synchronized StackTraceElement[] getOurStackTrace() {
        if (stackTrace == null) {
            stackTrace = backtrace == null ? EMPTY_TRACE : getStackTraceNative();
            if (stackTrace == null) {
                stackTrace = EMPTY_TRACE;
            }
        }
        return stackTrace;
    }

    public StackTraceElement[] getStackTrace() {
        return getOurStackTrace().clone();
    }

    public void setStackTrace(StackTraceElement[] stackTrace) {
        StackTraceElement[] copy = stackTrace.clone();
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] == null) {
                throw new NullPointerException("stackTrace[" + i + "]");
            }
        }
        synchronized (this) {
            this.stackTrace = copy;
        }
    }

    public final synchronized void addSuppressed(Throwable exception) {
        if (exception == this) {
            throw new IllegalArgumentException("Self-suppression not permitted", exception);
        }
        if (exception == null) {
            throw new NullPointerException("Cannot suppress a null exception.");
        }
        if (suppressedExceptions == null) {
            suppressedExceptions = new ArrayList<Throwable>(1);
        }
        suppressedExceptions.add(exception);
    }

    public final synchronized Throwable[] getSuppressed() {
        if (suppressedExceptions == null) {
            return new Throwable[0];
        }
        return suppressedExceptions.toArray(new Throwable[suppressedExceptions.size()]);
    }
}
