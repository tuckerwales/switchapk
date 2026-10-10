package java.io;

/** The writing end of a pipe; see PipedInputStream. */
public class PipedOutputStream extends OutputStream {
    private volatile PipedInputStream sink;

    public PipedOutputStream(PipedInputStream snk) throws IOException {
        connect(snk);
    }

    public PipedOutputStream() {
    }

    public synchronized void connect(PipedInputStream snk) throws IOException {
        if (snk == null) throw new NullPointerException();
        if (sink != null || snk.connected) throw new IOException("Already connected");
        sink = snk;
        snk.in = -1;
        snk.out = 0;
        snk.connected = true;
    }

    public void write(int b) throws IOException {
        PipedInputStream s = sink;
        if (s == null) throw new IOException("Pipe not connected");
        s.receive(b);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        PipedInputStream s = sink;
        if (s == null) throw new IOException("Pipe not connected");
        if (b == null) throw new NullPointerException();
        if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
        if (len == 0) return;
        s.receive(b, off, len);
    }

    public synchronized void flush() throws IOException {
        PipedInputStream s = sink;
        if (s != null) {
            synchronized (s) {
                s.notifyAll();
            }
        }
    }

    public void close() throws IOException {
        PipedInputStream s = sink;
        if (s != null) s.receivedLast();
    }
}
