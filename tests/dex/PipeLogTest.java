import java.io.*;
import java.util.*;
import java.util.logging.*;

/**
 * java.io piped streams and java.util.logging against OpenJDK. Pipes: a writer thread, small pipe
 * sizes, EOF on close, a subclass that resets the buffer as JSch's channel streams do, errors.
 * Logging: levels, the logger hierarchy, parent handlers, filters and parameter formatting, all seen
 * through a handler that records into a list (the default console output differs by platform).
 */
public class PipeLogTest {
    static void p(Object o) {
        System.out.println(o);
    }

    /** Like JSch's Channel.MyPipedInputStream: works on the protected fields directly. */
    static class Resettable extends PipedInputStream {
        Resettable(int size) {
            super(size);
        }

        synchronized int filled() {
            return in < 0 ? 0 : buffer.length;
        }

        synchronized void grow(int size) {
            if (in < 0) {
                buffer = new byte[size];
                out = 0;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        PipedInputStream in = new PipedInputStream(7);
        final PipedOutputStream out = new PipedOutputStream(in);
        Thread w = new Thread(() -> {
            try {
                for (int i = 0; i < 200; i++) out.write(i);
                byte[] big = new byte[1000];
                for (int i = 0; i < big.length; i++) big[i] = (byte) (i * 3);
                out.write(big, 0, big.length);
                out.close();
            } catch (IOException e) {
                p("writer " + e);
            }
        });
        w.start();
        long sum = 0;
        int n = 0, c;
        byte[] buf = new byte[13];
        while ((c = in.read(buf, 0, buf.length)) > 0) {
            for (int i = 0; i < c; i++) sum = sum * 31 + (buf[i] & 0xff);
            n += c;
        }
        w.join();
        p("pipe read " + n + " sum " + sum + " eof " + in.read());

        Resettable r = new Resettable(4);
        PipedOutputStream ro = new PipedOutputStream(r);
        ro.write(new byte[] {1, 2, 3});
        p("avail " + r.available() + " read " + r.read() + r.read() + r.read() + " avail " + r.available());
        r.grow(64);
        ro.write(new byte[50]);
        p("grown avail " + r.available());
        ro.close();
        try {
            new PipedOutputStream().write(1);
        } catch (IOException e) {
            p("unconnected " + e.getMessage());
        }
        try {
            PipedInputStream a = new PipedInputStream();
            new PipedOutputStream(a);
            new PipedOutputStream(a);
        } catch (IOException e) {
            p("double " + e.getMessage());
        }
        PipedInputStream closed = new PipedInputStream();
        PipedOutputStream co = new PipedOutputStream(closed);
        closed.close();
        try {
            co.write(1);
        } catch (IOException e) {
            p("closed " + e.getMessage());
        }

        final List<String> seen = new ArrayList<>();
        Handler h = new Handler() {
            public void publish(LogRecord rec) {
                if (isLoggable(rec)) seen.add(rec.getLoggerName() + " " + rec.getLevel() + " " + new SimpleFormatter().formatMessage(rec)
                        + (rec.getThrown() != null ? " thrown=" + rec.getThrown().getMessage() : ""));
            }

            public void flush() {
            }

            public void close() {
            }
        };
        Logger parent = Logger.getLogger("org.example");
        Logger child = Logger.getLogger("org.example.app.Thing");
        p("parent link " + (child.getParent() == parent) + " root name '" + parent.getParent().getName() + "'");
        parent.addHandler(h);
        parent.setUseParentHandlers(false);
        child.info("info {0}");
        child.fine("fine dropped");
        child.log(Level.WARNING, "warn {0} and {1}", new Object[] {"a", 7});
        child.log(Level.SEVERE, "boom", new IllegalStateException("bad"));
        parent.setLevel(Level.FINE);
        child.fine("fine now");
        child.finer("finer dropped");
        child.setLevel(Level.ALL);
        child.finest(() -> "supplied");
        child.setFilter(rec -> !rec.getMessage().contains("secret"));
        child.info("secret dropped");
        child.info("public");
        child.setUseParentHandlers(false);
        child.info("no handler");
        h.setLevel(Level.WARNING);
        child.setUseParentHandlers(true);
        child.info("below handler level");
        child.warning("at handler level");
        for (String s : seen) p("log " + s);
        p("levels " + Level.parse("WARNING") + " " + Level.parse("800") + " " + Level.INFO.intValue() + " " + Level.ALL.intValue());
        p("loggable " + child.isLoggable(Level.FINEST) + " " + Logger.getLogger("other").isLoggable(Level.FINE));
        p("same " + (Logger.getLogger("org.example") == parent));
        LogRecord rec = new LogRecord(Level.INFO, "x {0} y {1}");
        rec.setParameters(new Object[] {1, "two"});
        p("format " + new SimpleFormatter().formatMessage(rec));
    }
}
