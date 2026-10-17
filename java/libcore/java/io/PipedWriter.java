package java.io;

/** The writing end of a character pipe; see PipedReader. */
public class PipedWriter extends Writer {
    private volatile PipedReader sink;
    private boolean closed = false;

    public PipedWriter(PipedReader snk) throws IOException {
        connect(snk);
    }

    public PipedWriter() {
    }

    public synchronized void connect(PipedReader snk) throws IOException {
        if (snk == null) throw new NullPointerException();
        if (sink != null || snk.connected) throw new IOException("Already connected");
        if (snk.closedByReader || closed) throw new IOException("Pipe closed");
        sink = snk;
        snk.in = -1;
        snk.out = 0;
        snk.connected = true;
    }

    public void write(int c) throws IOException {
        PipedReader s = sink;
        if (s == null) throw new IOException("Pipe not connected");
        s.receive(c);
    }

    public void write(char[] cbuf, int off, int len) throws IOException {
        PipedReader s = sink;
        if (s == null) throw new IOException("Pipe not connected");
        if ((off | len | (off + len)) < 0 || off + len > cbuf.length) throw new IndexOutOfBoundsException();
        s.receive(cbuf, off, len);
    }

    public synchronized void flush() throws IOException {
        PipedReader s = sink;
        if (s != null) {
            if (s.closedByReader || closed) throw new IOException("Pipe closed");
            synchronized (s) {
                s.notifyAll();
            }
        }
    }

    public void close() throws IOException {
        closed = true;
        PipedReader s = sink;
        if (s != null) s.receivedLast();
    }
}
