package java.io;

public final class Console implements Flushable {
    private Console() {
    }

    public PrintWriter writer() {
        return new PrintWriter(System.out);
    }

    public Reader reader() {
        return new InputStreamReader(System.in);
    }

    public Console format(String fmt, Object... args) {
        System.out.format(fmt, args);
        return this;
    }

    public Console printf(String format, Object... args) {
        return format(format, args);
    }

    public String readLine() {
        return null;
    }

    public void flush() {
        System.out.flush();
    }
}
