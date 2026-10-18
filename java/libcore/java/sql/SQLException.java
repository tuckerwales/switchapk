package java.sql;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class SQLException extends Exception implements Iterable<Throwable> {
    private static final long serialVersionUID = 2135244094396331484L;
    private String SQLState;
    private int vendorCode;
    private volatile SQLException next;

    public SQLException(String reason, String SQLState, int vendorCode) {
        super(reason);
        this.SQLState = SQLState;
        this.vendorCode = vendorCode;
    }

    public SQLException(String reason, String SQLState) {
        this(reason, SQLState, 0);
    }

    public SQLException(String reason) {
        this(reason, null, 0);
    }

    public SQLException() {
        super();
    }

    public SQLException(Throwable cause) {
        super(cause);
    }

    public SQLException(String reason, Throwable cause) {
        super(reason, cause);
    }

    public SQLException(String reason, String sqlState, Throwable cause) {
        super(reason, cause);
        this.SQLState = sqlState;
    }

    public SQLException(String reason, String sqlState, int vendorCode, Throwable cause) {
        super(reason, cause);
        this.SQLState = sqlState;
        this.vendorCode = vendorCode;
    }

    public String getSQLState() {
        return SQLState;
    }

    public int getErrorCode() {
        return vendorCode;
    }

    public SQLException getNextException() {
        return next;
    }

    public synchronized void setNextException(SQLException ex) {
        SQLException current = this;
        while (current.next != null) {
            current = current.next;
        }
        current.next = ex;
    }

    /** This exception and its causes, then each chained exception and its causes. */
    public Iterator<Throwable> iterator() {
        return new Iterator<Throwable>() {
            SQLException firstException = SQLException.this;
            SQLException nextException = firstException.getNextException();
            Throwable cause = firstException.getCause();

            public boolean hasNext() {
                return firstException != null || nextException != null || cause != null;
            }

            public Throwable next() {
                Throwable throwable;
                if (firstException != null) {
                    throwable = firstException;
                    firstException = null;
                } else if (cause != null) {
                    throwable = cause;
                    cause = cause.getCause();
                } else if (nextException != null) {
                    throwable = nextException;
                    cause = nextException.getCause();
                    nextException = nextException.getNextException();
                } else {
                    throw new NoSuchElementException();
                }
                return throwable;
            }
        };
    }
}
