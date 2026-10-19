package javax.xml.xpath;

import java.io.PrintStream;
import java.io.PrintWriter;

public class XPathException extends Exception {
    private static final long serialVersionUID = -1837080260374986980L;

    public XPathException(String message) {
        super(message);
        if (message == null) {
            throw new NullPointerException("message == null");
        }
    }

    public XPathException(Throwable cause) {
        super(cause);
        if (cause == null) {
            throw new NullPointerException("cause == null");
        }
    }

    public Throwable getCause() {
        return super.getCause();
    }

    public void printStackTrace(PrintStream s) {
        super.printStackTrace(s);
    }

    public void printStackTrace() {
        printStackTrace(System.err);
    }

    public void printStackTrace(PrintWriter s) {
        super.printStackTrace(s);
    }
}
