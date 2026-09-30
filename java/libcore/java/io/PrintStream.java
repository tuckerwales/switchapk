package java.io;

import java.nio.charset.Charset;
import java.util.Formatter;
import java.util.Locale;

public class PrintStream extends FilterOutputStream implements Appendable, Closeable {
    private final boolean autoFlush;
    private boolean trouble = false;
    private final Charset charset;
    private boolean closing = false;

    public PrintStream(OutputStream out) {
        this(out, false);
    }

    public PrintStream(OutputStream out, boolean autoFlush) {
        super(out);
        this.autoFlush = autoFlush;
        this.charset = Charset.defaultCharset();
    }

    public PrintStream(OutputStream out, boolean autoFlush, String encoding) throws UnsupportedEncodingException {
        super(out);
        this.autoFlush = autoFlush;
        try {
            this.charset = Charset.forName(encoding);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedEncodingException(encoding);
        }
    }

    public PrintStream(OutputStream out, boolean autoFlush, Charset charset) {
        super(out);
        this.autoFlush = autoFlush;
        this.charset = charset;
    }

    public PrintStream(String fileName) throws FileNotFoundException {
        this(new FileOutputStream(fileName), false);
    }

    public PrintStream(String fileName, String csn) throws FileNotFoundException, UnsupportedEncodingException {
        this(new FileOutputStream(fileName), false, csn);
    }

    public PrintStream(File file) throws FileNotFoundException {
        this(new FileOutputStream(file), false);
    }

    public void flush() {
        synchronized (this) {
            try {
                if (out != null) {
                    out.flush();
                }
            } catch (IOException x) {
                trouble = true;
            }
        }
    }

    public void close() {
        synchronized (this) {
            if (!closing) {
                closing = true;
                try {
                    out.close();
                } catch (IOException x) {
                    trouble = true;
                }
            }
        }
    }

    public boolean checkError() {
        if (out != null) {
            flush();
        }
        return trouble;
    }

    protected void setError() {
        trouble = true;
    }

    protected void clearError() {
        trouble = false;
    }

    public void write(int b) {
        try {
            synchronized (this) {
                out.write(b);
                if ((b == '\n') && autoFlush) {
                    out.flush();
                }
            }
        } catch (IOException x) {
            trouble = true;
        }
    }

    public void write(byte[] buf, int off, int len) {
        try {
            synchronized (this) {
                out.write(buf, off, len);
                if (autoFlush) {
                    out.flush();
                }
            }
        } catch (IOException x) {
            trouble = true;
        }
    }

    public void write(byte[] buf) throws IOException {
        write(buf, 0, buf.length);
    }

    public void writeBytes(byte[] buf) {
        write(buf, 0, buf.length);
    }

    private void write(String s) {
        byte[] b = s.getBytes(charset);
        write(b, 0, b.length);
    }

    private void newLine() {
        write("\n");
    }

    public void print(boolean b) {
        write(b ? "true" : "false");
    }

    public void print(char c) {
        write(String.valueOf(c));
    }

    public void print(int i) {
        write(String.valueOf(i));
    }

    public void print(long l) {
        write(String.valueOf(l));
    }

    public void print(float f) {
        write(String.valueOf(f));
    }

    public void print(double d) {
        write(String.valueOf(d));
    }

    public void print(char[] s) {
        write(new String(s));
    }

    public void print(String s) {
        write(s == null ? "null" : s);
    }

    public void print(Object obj) {
        write(String.valueOf(obj));
    }

    public void println() {
        newLine();
    }

    public void println(boolean x) {
        synchronized (this) {
            write((x ? "true" : "false") + "\n");
        }
    }

    public void println(char x) {
        write(x + "\n");
    }

    public void println(int x) {
        write(x + "\n");
    }

    public void println(long x) {
        write(x + "\n");
    }

    public void println(float x) {
        write(x + "\n");
    }

    public void println(double x) {
        write(x + "\n");
    }

    public void println(char[] x) {
        write(new String(x) + "\n");
    }

    public void println(String x) {
        write(x + "\n");
    }

    public void println(Object x) {
        write(String.valueOf(x) + "\n");
    }

    public PrintStream printf(String format, Object... args) {
        return format(format, args);
    }

    public PrintStream printf(Locale l, String format, Object... args) {
        return format(l, format, args);
    }

    public PrintStream format(String format, Object... args) {
        write(new Formatter().format(format, args).toString());
        return this;
    }

    public PrintStream format(Locale l, String format, Object... args) {
        write(new Formatter(l).format(format, args).toString());
        return this;
    }

    public PrintStream append(CharSequence csq) {
        print(String.valueOf(csq));
        return this;
    }

    public PrintStream append(CharSequence csq, int start, int end) {
        if (csq == null) {
            csq = "null";
        }
        return append(csq.subSequence(start, end));
    }

    public PrintStream append(char c) {
        print(c);
        return this;
    }
}
