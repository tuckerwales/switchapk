package java.io;

import java.util.Formatter;
import java.util.Locale;

public class PrintWriter extends Writer {
    protected Writer out;
    private final boolean autoFlush;
    private boolean trouble = false;

    public PrintWriter(Writer out) {
        this(out, false);
    }

    public PrintWriter(Writer out, boolean autoFlush) {
        super(out);
        this.out = out;
        this.autoFlush = autoFlush;
    }

    public PrintWriter(OutputStream out) {
        this(out, false);
    }

    public PrintWriter(OutputStream out, boolean autoFlush) {
        this(new BufferedWriter(new OutputStreamWriter(out)), autoFlush);
    }

    public PrintWriter(String fileName) throws FileNotFoundException {
        this(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName))), false);
    }

    public PrintWriter(String fileName, String csn) throws FileNotFoundException, UnsupportedEncodingException {
        this(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), csn)), false);
    }

    public PrintWriter(File file) throws FileNotFoundException {
        this(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file))), false);
    }

    public void flush() {
        try {
            synchronized (lock) {
                if (out != null) {
                    out.flush();
                }
            }
        } catch (IOException x) {
            trouble = true;
        }
    }

    public void close() {
        try {
            synchronized (lock) {
                if (out == null) {
                    return;
                }
                out.close();
                out = null;
            }
        } catch (IOException x) {
            trouble = true;
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

    public void write(int c) {
        try {
            synchronized (lock) {
                if (out == null) {
                    throw new IOException("Stream closed");
                }
                out.write(c);
            }
        } catch (IOException x) {
            trouble = true;
        }
    }

    public void write(char[] buf, int off, int len) {
        try {
            synchronized (lock) {
                if (out == null) {
                    throw new IOException("Stream closed");
                }
                out.write(buf, off, len);
            }
        } catch (IOException x) {
            trouble = true;
        }
    }

    public void write(char[] buf) {
        write(buf, 0, buf.length);
    }

    public void write(String s, int off, int len) {
        try {
            synchronized (lock) {
                if (out == null) {
                    throw new IOException("Stream closed");
                }
                out.write(s, off, len);
            }
        } catch (IOException x) {
            trouble = true;
        }
    }

    public void write(String s) {
        write(s, 0, s.length());
    }

    private void newLine() {
        synchronized (lock) {
            write('\n');
            if (autoFlush) {
                flush();
            }
        }
    }

    public void print(boolean b) {
        write(b ? "true" : "false");
    }

    public void print(char c) {
        write(c);
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
        write(s);
    }

    public void print(String s) {
        write(String.valueOf(s));
    }

    public void print(Object obj) {
        write(String.valueOf(obj));
    }

    public void println() {
        newLine();
    }

    public void println(boolean x) {
        synchronized (lock) {
            print(x);
            println();
        }
    }

    public void println(char x) {
        synchronized (lock) {
            print(x);
            println();
        }
    }

    public void println(int x) {
        synchronized (lock) {
            print(x);
            println();
        }
    }

    public void println(long x) {
        synchronized (lock) {
            print(x);
            println();
        }
    }

    public void println(float x) {
        synchronized (lock) {
            print(x);
            println();
        }
    }

    public void println(double x) {
        synchronized (lock) {
            print(x);
            println();
        }
    }

    public void println(char[] x) {
        synchronized (lock) {
            print(x);
            println();
        }
    }

    public void println(String x) {
        synchronized (lock) {
            print(x);
            println();
        }
    }

    public void println(Object x) {
        String s = String.valueOf(x);
        synchronized (lock) {
            print(s);
            println();
        }
    }

    public PrintWriter printf(String format, Object... args) {
        return format(format, args);
    }

    public PrintWriter printf(Locale l, String format, Object... args) {
        return format(l, format, args);
    }

    public PrintWriter format(String format, Object... args) {
        write(new Formatter().format(format, args).toString());
        if (autoFlush) {
            flush();
        }
        return this;
    }

    public PrintWriter format(Locale l, String format, Object... args) {
        write(new Formatter(l).format(format, args).toString());
        if (autoFlush) {
            flush();
        }
        return this;
    }

    public PrintWriter append(CharSequence csq) {
        write(String.valueOf(csq));
        return this;
    }

    public PrintWriter append(CharSequence csq, int start, int end) {
        if (csq == null) {
            csq = "null";
        }
        return append(csq.subSequence(start, end));
    }

    public PrintWriter append(char c) {
        write(c);
        return this;
    }
}
