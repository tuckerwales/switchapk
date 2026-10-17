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

    public String readLine(String fmt, Object... args) {
        if (fmt.length() != 0) {
            format(fmt, args);
        }
        return readLine();
    }

    public String readLine() {
        return null;
    }

    public char[] readPassword(String fmt, Object... args) {
        String line = readLine(fmt, args);
        return line == null ? null : line.toCharArray();
    }

    public char[] readPassword() {
        return readPassword("");
    }

    public java.nio.charset.Charset charset() {
        return java.nio.charset.Charset.defaultCharset();
    }

    public void flush() {
        System.out.flush();
    }
}
