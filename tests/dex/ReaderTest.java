import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * InputStreamReader and BufferedReader over valid UTF-8, UTF-16 and ASCII: sequences split across short reads,
 * supplementary characters at odd buffer ends, readLine. Run on OpenJDK and on switchapk by
 * tests/run_dex_test.sh, output must match.
 */
public class ReaderTest {
    static void p(Object o) {
        System.out.println(o);
    }

    /* Returns at most 1, 2 or 3 bytes per read, cycling, so multi-byte sequences straddle refills. */
    static final class Trickle extends InputStream {
        private final byte[] data;
        private int pos, step;

        Trickle(byte[] data) {
            this.data = data;
        }

        public int read() {
            return pos < data.length ? data[pos++] & 0xff : -1;
        }

        public int read(byte[] b, int off, int len) {
            if (pos >= data.length) {
                return -1;
            }
            int n = Math.min(Math.min(len, 1 + (step++ % 3)), data.length - pos);
            System.arraycopy(data, pos, b, off, n);
            pos += n;
            return n;
        }
    }

    static String sum(String s) {
        long h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = h * 31 + s.charAt(i);
        }
        return s.length() + " chars, hash " + Long.toHexString(h);
    }

    static String readAll(Reader r, int chunk) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[chunk];
        int n;
        while ((n = r.read(buf, 0, chunk)) >= 0) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    public static void main(String[] args) throws IOException {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            text.append("line ").append(i).append(": {\"k\": \"vé€\", \"emoji\": \"😀\"}");
            if (i % 7 == 0) {
                text.append(" 你好 𐍈");
            }
            text.append('\n');
        }
        String s = text.toString();
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        p("source " + sum(s) + " " + utf8.length + " bytes");
        for (int chunk : new int[] {1, 2, 3, 7, 64, 8191, 100000}) {
            String a = readAll(new InputStreamReader(new ByteArrayInputStream(utf8), StandardCharsets.UTF_8), chunk);
            String b = readAll(new InputStreamReader(new Trickle(utf8), "UTF-8"), chunk);
            p("chunk " + chunk + ": " + a.equals(s) + " " + b.equals(s));
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(new Trickle(utf8), StandardCharsets.UTF_8));
        int lines = 0;
        String line, last = null;
        while ((line = br.readLine()) != null) {
            lines++;
            last = line;
        }
        p("readLine " + lines + " last " + sum(last));
        InputStreamReader one = new InputStreamReader(new ByteArrayInputStream("a😀b".getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        p("single " + one.read() + " " + one.read() + " " + one.read() + " " + one.read() + " " + one.read());
        String ascii = "plain ascii only\n";
        p("ascii " + readAll(new InputStreamReader(new ByteArrayInputStream(ascii.getBytes(StandardCharsets.US_ASCII)),
                StandardCharsets.US_ASCII), 5).equals(ascii));
        for (String cs : new String[] {"UTF-16LE", "UTF-16BE"}) {
            byte[] u16 = s.substring(0, 500).getBytes(cs);
            p(cs + " " + readAll(new InputStreamReader(new Trickle(u16), cs), 13).equals(s.substring(0, 500)));
        }
        byte[] latin = "café".getBytes(StandardCharsets.ISO_8859_1);
        p("latin1 " + readAll(new InputStreamReader(new ByteArrayInputStream(latin), StandardCharsets.ISO_8859_1), 2));
    }
}
