package java.io;

import java.util.Enumeration;
import java.util.Vector;

public class SequenceInputStream extends InputStream {
    private final Enumeration<? extends InputStream> e;
    private InputStream in;

    public SequenceInputStream(Enumeration<? extends InputStream> e) {
        this.e = e;
        peekNextStream();
    }

    public SequenceInputStream(InputStream s1, InputStream s2) {
        Vector<InputStream> v = new Vector<InputStream>(2);
        v.addElement(s1);
        v.addElement(s2);
        e = v.elements();
        peekNextStream();
    }

    private void nextStream() throws IOException {
        if (in != null) {
            in.close();
        }
        peekNextStream();
    }

    private void peekNextStream() {
        in = e.hasMoreElements() ? e.nextElement() : null;
    }

    public int available() throws IOException {
        return in == null ? 0 : in.available();
    }

    public int read() throws IOException {
        while (in != null) {
            int c = in.read();
            if (c != -1) {
                return c;
            }
            nextStream();
        }
        return -1;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (in == null) {
            return -1;
        }
        if (len == 0) {
            return 0;
        }
        do {
            int n = in.read(b, off, len);
            if (n > 0) {
                return n;
            }
            nextStream();
        } while (in != null);
        return -1;
    }

    public void close() throws IOException {
        while (in != null) {
            nextStream();
        }
    }
}
