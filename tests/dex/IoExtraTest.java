import java.io.*;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * java.io extras: StreamTokenizer, PipedReader/PipedWriter, LineNumberInputStream,
 * StringBufferInputStream, Reader.nullReader and the charset constructors of the writers and
 * print streams. Run on OpenJDK and on switchapk by tests/run_dex_test.sh, output must match.
 */
@SuppressWarnings("deprecation")
public class IoExtraTest {
    static void p(Object o) {
        System.out.println(o);
    }

    static String name(Throwable t) {
        return t.getClass().getName();
    }

    static String esc(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c < 32 || c > 126) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static void dump(String label, StreamTokenizer st) throws IOException {
        StringBuilder sb = new StringBuilder(label).append(":");
        int guard = 0;
        while (st.nextToken() != StreamTokenizer.TT_EOF && guard++ < 200) {
            sb.append(" [").append(esc(st.toString())).append(" t=").append(st.ttype);
            if (st.ttype == StreamTokenizer.TT_NUMBER) {
                sb.append(" n=").append(st.nval);
            } else if (st.sval != null) {
                sb.append(" s=").append(esc(st.sval));
            }
            sb.append(" l=").append(st.lineno()).append("]");
        }
        sb.append(" end ").append(esc(st.toString()));
        p(sb);
    }

    static StreamTokenizer tok(String s) {
        return new StreamTokenizer(new StringReader(s));
    }

    static void tokenizer() throws IOException {
        StreamTokenizer fresh = tok("x");
        p("fresh " + fresh + " ttype " + fresh.ttype + " sval " + fresh.sval + " nval " + fresh.nval);
        fresh.pushBack();
        p("pushBack on nothing " + fresh.nextToken() + " " + fresh);

        dump("numbers", tok("1 23 4.5 -6 -.5 .25 1.2.3 - -x 007 3e5 12abc"));
        dump("words", tok("hello World foo_bar a1b2 \u00e9t\u00e9 \u4e2d\u6587 x-y"));
        dump("quotes", tok("\"double q\" 'single' \"esc \\n\\t\\\\ \\\" \\a\\b\\f\\r\\v\\q\" \"oct \\101\\60\\0\\477\\8\""));
        dump("unterminated", tok("\"open string\nnext 'also\r\nlast"));
        dump("comments", tok("a / b // c\nd /* e */ f\ng"));
        dump("chars", tok("a+b*(c)=d; {e} [f] , g ! @ # $ % ^ &"));

        StreamTokenizer st = tok("line1\nline2\r\nline3\rline4\n\nend");
        st.eolIsSignificant(true);
        dump("eol", st);
        dump("noeol", tok("line1\nline2\r\nline3\rline4\n\nend"));

        st = tok("Hello WORLD MiXeD 'Quoted STAYS'");
        st.lowerCaseMode(true);
        dump("lower", st);

        st = tok("alpha 42 beta");
        p("first " + st.nextToken() + " " + st);
        st.pushBack();
        p("again " + st.nextToken() + " " + st);
        st.pushBack();
        st.pushBack();
        p("twice " + st.nextToken() + " " + st);
        p("next " + st.nextToken() + " " + st);
        p("next " + st.nextToken() + " " + st);
        p("eof " + st.nextToken() + " " + st);
        st.pushBack();
        p("eof again " + st.nextToken() + " " + st);

        st = tok("a.b-c 1-2 'q' \"r\" /x");
        st.ordinaryChar('.');
        st.ordinaryChar('-');
        st.ordinaryChar('\'');
        st.ordinaryChar('/');
        dump("ordinaryChar", st);

        st = tok("abc 123 def");
        st.ordinaryChars('0', '9');
        dump("ordinaryChars", st);

        st = tok("x // slash slash\ny /* star\nstar */ z / w /* unterminated");
        st.slashSlashComments(true);
        st.slashStarComments(true);
        dump("slash both", st);

        st = tok("x // slash slash\ny /* star */ z");
        st.slashSlashComments(true);
        st.ordinaryChar('/');
        dump("slashslash only", st);

        st = tok("x // slash slash\ny /* star\n\nstar */ z");
        st.slashStarComments(true);
        st.ordinaryChar('/');
        dump("slashstar only", st);

        st = tok("p /* a\r\nb\nc */ q");
        st.slashStarComments(true);
        dump("slashstar lines", st);

        st = tok("x /y\nz");
        st.slashStarComments(true);
        dump("slash comment fallback", st);

        st = tok("key=value # comment\nother=1,2");
        st.resetSyntax();
        st.wordChars('a', 'z');
        st.wordChars('0', '9');
        st.whitespaceChars(0, ' ');
        st.commentChar('#');
        st.eolIsSignificant(true);
        dump("reset", st);

        st = tok("path/to/file.txt  C:\\dir");
        st.wordChars('/', '/');
        st.wordChars('.', '.');
        st.wordChars(':', ':');
        st.wordChars('\\', '\\');
        dump("wordChars", st);

        st = tok("|a| b |c d| e");
        st.quoteChar('|');
        dump("quoteChar", st);

        st = tok("a\u0100b c");
        dump("high chars", st);

        st = tok("1 2 3");
        st.whitespaceChars('2', '2');
        dump("ws digits", st);

        StreamTokenizer is = new StreamTokenizer(new ByteArrayInputStream("in 1 'stream'".getBytes(StandardCharsets.US_ASCII)));
        dump("inputstream", is);

        try {
            new StreamTokenizer((Reader) null);
        } catch (NullPointerException e) {
            p("null reader " + name(e));
        }
        try {
            new StreamTokenizer((InputStream) null);
        } catch (NullPointerException e) {
            p("null stream " + name(e));
        }
        p("constants " + StreamTokenizer.TT_EOF + " " + StreamTokenizer.TT_EOL + " "
                + StreamTokenizer.TT_NUMBER + " " + StreamTokenizer.TT_WORD);
    }

    static void piped() throws Exception {
        final PipedWriter w = new PipedWriter();
        final PipedReader r = new PipedReader(w, 16);
        p("ready empty " + r.ready());
        Thread writer = new Thread(new Runnable() {
            public void run() {
                try {
                    for (int i = 0; i < 50; i++) {
                        w.write("line " + i + "\n");
                    }
                    w.write('!');
                    w.write(new char[] {'a', 'b', 'c', 'd'}, 1, 2);
                    w.flush();
                    w.close();
                } catch (IOException e) {
                    System.out.println("writer " + e);
                }
            }
        });
        writer.start();
        BufferedReader br = new BufferedReader(r);
        int lines = 0;
        String last = null;
        String line;
        while ((line = br.readLine()) != null) {
            lines++;
            last = line;
        }
        writer.join();
        p("lines " + lines + " last " + last);
        p("read after eof " + r.read());
        r.close();
        try {
            r.read();
        } catch (IOException e) {
            p("closed read " + e.getMessage());
        }

        PipedReader r2 = new PipedReader();
        try {
            r2.read();
        } catch (IOException e) {
            p("unconnected read " + e.getMessage());
        }
        PipedWriter w2 = new PipedWriter();
        try {
            w2.write('x');
        } catch (IOException e) {
            p("unconnected write " + e.getMessage());
        }
        w2.connect(r2);
        try {
            w2.connect(r2);
        } catch (IOException e) {
            p("reconnect " + e.getMessage());
        }
        try {
            new PipedWriter().connect(r2);
        } catch (IOException e) {
            p("reader taken " + e.getMessage());
        }
        w2.write("hey".toCharArray(), 0, 3);
        p("ready " + r2.ready());
        char[] cb = new char[10];
        int n = r2.read(cb, 2, 8);
        p("bulk " + n + " " + new String(cb, 2, n));
        p("ready after " + r2.ready());
        p("zero len " + r2.read(cb, 0, 0));
        try {
            r2.read(cb, 5, 10);
        } catch (IndexOutOfBoundsException e) {
            p("bounds " + name(e));
        }
        try {
            new PipedReader(0);
        } catch (IllegalArgumentException e) {
            p("size 0 " + name(e));
        }
        w2.close();
        p("after writer close " + r2.read());
        PipedReader r3 = new PipedReader();
        r3.close();
        try {
            new PipedWriter(r3);
        } catch (IOException e) {
            p("connect closed " + e.getMessage());
        }

        // Reader and writer each on their own thread, with a tiny buffer so both sides block.
        final PipedReader r4 = new PipedReader(2);
        final PipedWriter w4 = new PipedWriter(r4);
        final StringBuilder got = new StringBuilder();
        Thread reader = new Thread(new Runnable() {
            public void run() {
                try {
                    char[] b = new char[3];
                    int k;
                    while ((k = r4.read(b, 0, b.length)) != -1) {
                        got.append(b, 0, k);
                    }
                } catch (IOException e) {
                    got.append("ERR " + e);
                }
            }
        });
        reader.start();
        String msg = "The quick brown fox jumps over the lazy dog";
        for (int i = 0; i < msg.length(); i++) {
            w4.write(msg.charAt(i));
        }
        w4.close();
        reader.join();
        p("two threads " + got);
    }

    static void lineNumberInputStream() throws IOException {
        byte[] data = "ab\ncd\r\nef\rgh\n\r\n".getBytes(StandardCharsets.US_ASCII);
        LineNumberInputStream in = new LineNumberInputStream(new ByteArrayInputStream(data));
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            sb.append(c == '\n' ? "|" : String.valueOf((char) c)).append(in.getLineNumber());
        }
        p("lnis " + sb);
        in = new LineNumberInputStream(new ByteArrayInputStream(data));
        byte[] buf = new byte[32];
        int n = in.read(buf, 1, 6);
        p("bulk " + n + " " + esc(new String(buf, 1, n, StandardCharsets.US_ASCII)) + " line " + in.getLineNumber());
        p("available " + in.available());
        in.mark(100);
        p("skip " + in.skip(3) + " line " + in.getLineNumber());
        in.reset();
        p("reset line " + in.getLineNumber() + " next " + in.read());
        in.setLineNumber(100);
        n = in.read(buf, 0, buf.length);
        p("rest " + n + " " + esc(new String(buf, 0, n, StandardCharsets.US_ASCII)) + " line " + in.getLineNumber());
        p("eof " + in.read(buf, 0, 4) + " zero " + in.read(buf, 0, 0) + " skip0 " + in.skip(0));
        LineNumberInputStream cr = new LineNumberInputStream(new ByteArrayInputStream("x\ry".getBytes(StandardCharsets.US_ASCII)));
        p("cr " + cr.read() + " " + cr.read() + " avail " + cr.available() + " " + cr.read() + " " + cr.read());
    }

    static void stringBufferInputStream() throws IOException {
        StringBufferInputStream in = new StringBufferInputStream("hello \u0141\u00ff world");
        p("avail " + in.available());
        p("read " + in.read() + " " + in.read());
        byte[] b = new byte[6];
        int n = in.read(b, 1, 5);
        p("bulk " + n + " " + b[1] + " " + b[2] + " " + b[3] + " " + b[4] + " " + b[5]);
        p("skip " + in.skip(3) + " avail " + in.available() + " skipneg " + in.skip(-2));
        p("skip big " + in.skip(100) + " read " + in.read() + " bulk " + in.read(b, 0, 2));
        in.reset();
        p("reset " + in.read() + " avail " + in.available() + " mark " + in.markSupported());
        try {
            in.read(b, 4, 4);
        } catch (IndexOutOfBoundsException e) {
            p("bounds " + name(e));
        }
        p("zero " + in.read(b, 0, 0));
    }

    static void misc() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(new byte[] {'A', 'b', (byte) 0xe9, 0x31});
        String hi0 = bos.toString(0);
        String hi1 = bos.toString(1);
        p("hibyte0 " + esc(hi0) + " hibyte1 " + esc(hi1));

        CharArrayWriter caw = new CharArrayWriter();
        caw.append("abcdef", 1, 4).append(null, 0, 2).append('!');
        p("caw " + caw);
        try {
            caw.append("abc", 2, 5);
        } catch (IndexOutOfBoundsException e) {
            p("caw bounds " + name(e));
        }

        Reader nr = Reader.nullReader();
        char[] cb = new char[4];
        p("null read " + nr.read() + " " + nr.read(cb, 0, 4) + " " + nr.read(cb, 0, 0) + " ready " + nr.ready()
                + " skip " + nr.skip(10) + " transfer " + nr.transferTo(new StringWriter()));
        p("null cb " + nr.read(CharBuffer.allocate(3)) + " " + nr.read(CharBuffer.allocate(0)));
        try {
            nr.read(cb, 3, 2);
        } catch (IndexOutOfBoundsException e) {
            p("null bounds " + name(e));
        }
        nr.close();
        nr.close();
        try {
            nr.read();
        } catch (IOException e) {
            p("null closed " + e.getMessage());
        }
        try {
            nr.ready();
        } catch (IOException e) {
            p("null closed ready " + e.getMessage());
        }

        InterruptedIOException iioe = new InterruptedIOException("t");
        p("bytesTransferred " + iioe.bytesTransferred);
        iioe.bytesTransferred = 7;
        p("bytesTransferred " + iioe.bytesTransferred);

        IOError err = new IOError(new IOException("boom"));
        p("ioerror " + err.getMessage() + " cause " + err.getCause().getMessage());

        OutputStreamWriter enc = new OutputStreamWriter(new ByteArrayOutputStream(), StandardCharsets.UTF_16BE.newEncoder());
        enc.close();
    }

    static String readFile(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : out.toByteArray()) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        }
    }

    static void constructors() throws IOException {
        File dir = new File(System.getProperty("java.io.tmpdir"), "ioextra-" + System.nanoTime());
        dir.mkdirs();
        File f = new File(dir, "a.txt");
        Charset utf16 = StandardCharsets.UTF_16BE;
        Charset latin1 = StandardCharsets.ISO_8859_1;

        try (FileWriter w = new FileWriter(f, utf16)) {
            w.write("h\u00e9");
        }
        p("FileWriter(File,Charset) " + readFile(f));
        try (FileWriter w = new FileWriter(f.getPath(), latin1, true)) {
            w.write("\u00e9!");
        }
        p("FileWriter(String,Charset,boolean) " + readFile(f));
        try (FileWriter w = new FileWriter(f.getPath(), latin1, false)) {
            w.write("x");
        }
        p("FileWriter no append " + readFile(f));

        try (PrintStream ps = new PrintStream(f, "UTF-16BE")) {
            ps.print("ps\u00e9");
        }
        p("PrintStream(File,String) " + readFile(f));
        try (PrintStream ps = new PrintStream(f, latin1)) {
            ps.print("ps\u00e9");
        }
        p("PrintStream(File,Charset) " + readFile(f));
        try (PrintStream ps = new PrintStream(f.getPath(), utf16)) {
            ps.print("s");
        }
        p("PrintStream(String,Charset) " + readFile(f));
        try {
            new PrintStream(new File(dir, "nope.txt"), "no-such-charset");
        } catch (UnsupportedEncodingException e) {
            p("PrintStream bad csn " + name(e) + " created " + new File(dir, "nope.txt").exists());
        }

        try (PrintWriter pw = new PrintWriter(f, "UTF-16BE")) {
            pw.print("pw\u00e9");
        }
        p("PrintWriter(File,String) " + readFile(f));
        try (PrintWriter pw = new PrintWriter(f, latin1)) {
            pw.print("pw\u00e9");
        }
        p("PrintWriter(File,Charset) " + readFile(f));
        try (PrintWriter pw = new PrintWriter(f.getPath(), utf16)) {
            pw.print("w");
        }
        p("PrintWriter(String,Charset) " + readFile(f));
        try {
            new PrintWriter(new File(dir, "nope2.txt"), "no-such-charset");
        } catch (UnsupportedEncodingException e) {
            p("PrintWriter bad csn " + name(e) + " created " + new File(dir, "nope2.txt").exists());
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(bos, true, latin1);
        pw.println("auto\u00e9");
        p("PrintWriter(OutputStream,boolean,Charset) autoflushed " + bos.size());
        pw.close();

        ByteArrayOutputStream eb = new ByteArrayOutputStream();
        try (OutputStreamWriter w = new OutputStreamWriter(eb, utf16.newEncoder())) {
            w.write("ok");
        }
        p("OutputStreamWriter(CharsetEncoder) " + eb.size() + " " + eb.toString("UTF-16BE"));

        for (File x : dir.listFiles()) {
            x.delete();
        }
        dir.delete();
    }

    static class Ser implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public static void main(String[] args) throws Exception {
        tokenizer();
        piped();
        lineNumberInputStream();
        stringBufferInputStream();
        misc();
        constructors();
        FilePermission fp = new FilePermission("/tmp/x", "read");
        p("FilePermission " + fp.getName());
        p("Serial on Ser " + new Ser().getClass().getDeclaredFields().length);
        p("done");
    }
}
