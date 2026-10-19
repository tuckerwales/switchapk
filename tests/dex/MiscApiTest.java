import java.io.*;
import java.math.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.StampedLock;
import java.util.regex.MatchResult;

/**
 * Scanner, ResourceBundle (class and properties bundles), java.sql Date/Time/Timestamp/SQLException, StampedLock,
 * ClassValue and the atomic accumulators. Run on OpenJDK and on switchapk by tests/run_dex_test.sh, output must
 * match.
 */
public class MiscApiTest {
    static void p(Object o) {
        System.out.println(o);
    }

    static String tryIt(java.util.function.Supplier<Object> s) {
        try {
            return String.valueOf(s.get());
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName();
        }
    }

    /** Hands out one character per read, so tokens straddle buffer refills. */
    static final class SlowReader extends Reader {
        final String s;
        int i;

        SlowReader(String s) {
            this.s = s;
        }

        public int read(char[] b, int off, int len) {
            if (i >= s.length()) return -1;
            b[off] = s.charAt(i++);
            return 1;
        }

        public void close() {
        }
    }

    public static class Res extends ListResourceBundle {
        protected Object[][] getContents() {
            return new Object[][] {{"hello", "Hello"}, {"bye", "Goodbye"}, {"list", new String[] {"a", "b"}}};
        }
    }

    public static class Res_fr extends ListResourceBundle {
        protected Object[][] getContents() {
            return new Object[][] {{"hello", "Bonjour"}};
        }
    }

    public static class Res_fr_CA extends ListResourceBundle {
        protected Object[][] getContents() {
            return new Object[][] {{"bye", "Salut"}};
        }
    }

    public static void main(String[] args) throws Exception {
        // Scanner tokens and numbers
        Scanner sc = new Scanner(new SlowReader("  12 -7 1,234 0x1F word 3.5 1,234.75 -2e3 true FALSE 99999999999 \n"
                + "line two here\nthird"));
        p(sc.nextInt() + " " + sc.nextInt() + " " + sc.hasNextInt() + " " + sc.nextInt() + " " + sc.hasNextInt()
                + " " + sc.next() + " " + sc.hasNextDouble() + " " + tryIt(sc::nextInt) + " " + sc.next());
        p(sc.nextDouble() + " " + sc.nextDouble() + " " + sc.nextDouble() + " " + sc.nextBoolean() + " "
                + sc.nextBoolean() + " " + sc.hasNextInt() + " " + sc.hasNextLong() + " " + sc.nextLong());
        p("rest of line: [" + sc.nextLine() + "] next line: [" + sc.nextLine() + "] has: " + sc.hasNextLine()
                + " last: [" + sc.nextLine() + "] " + sc.hasNext() + " " + sc.hasNextLine() + " "
                + tryIt(sc::next) + " " + tryIt(sc::nextLine));
        sc.close();
        p("closed: " + tryIt(sc::hasNext));

        Scanner csv = new Scanner("a, b,,c ,d").useDelimiter("\\s*,\\s*");
        List<String> parts = new ArrayList<String>();
        while (csv.hasNext()) parts.add(csv.next());
        p("csv " + parts + " delimiter " + csv.delimiter());
        Scanner lines = new Scanner(new SlowReader("one\r\ntwo\rthree\n\nfive"));
        List<String> ls = new ArrayList<String>();
        while (lines.hasNextLine()) ls.add(lines.nextLine());
        p("lines " + ls);
        Scanner f = new Scanner(new SlowReader("name=alpha id=42\nname=beta id=7\n"));
        p("findInLine " + f.findInLine("id=(\\d+)") + " group " + f.match().group(1) + " rest [" + f.nextLine()
                + "] next " + f.findInLine("zzz") + " horizon " + f.findWithinHorizon("id=\\d+", 0) + " "
                + f.findWithinHorizon("name", 3));
        Scanner sk = new Scanner("abc123 def");
        sk.skip("[a-z]+");
        p("skip then int " + sk.nextInt() + " " + tryIt(() -> sk.skip("\\d")) + " " + sk.next());
        Scanner rad = new Scanner("ff 777 101").useRadix(16);
        p("radix " + rad.nextInt() + " " + rad.nextInt(8) + " " + rad.nextInt(2) + " " + rad.radix());
        Scanner big = new Scanner("123456789012345678901234567890 3.14159265358979323846 NaN");
        p("big " + big.nextBigInteger() + " " + big.nextBigDecimal() + " " + big.hasNextBigDecimal() + " "
                + big.nextDouble());
        Scanner tok = new Scanner("x y  z");
        p("tokens " + tok.tokens().collect(java.util.stream.Collectors.toList()));
        Scanner fa = new Scanner("a1b22c333");
        StringBuilder sb = new StringBuilder();
        fa.findAll("\\d+").forEach(m -> sb.append(m.group()).append('@').append(m.start()).append(' '));
        p("findAll " + sb);
        Scanner de = new Scanner("1.234,5 7,25").useLocale(Locale.GERMANY);
        p("German " + de.nextDouble() + " " + de.nextDouble());
        Scanner mm = new Scanner("12abc");
        p("mismatch keeps token: " + tryIt(mm::nextInt) + " " + mm.next());

        // ResourceBundle
        ResourceBundle en = ResourceBundle.getBundle("MiscApiTest$Res", Locale.ENGLISH);
        ResourceBundle fr = ResourceBundle.getBundle("MiscApiTest$Res", Locale.FRENCH);
        ResourceBundle ca = ResourceBundle.getBundle("MiscApiTest$Res", Locale.CANADA_FRENCH);
        p("bundles " + en.getString("hello") + " " + fr.getString("hello") + " " + fr.getString("bye") + " "
                + ca.getString("hello") + " " + ca.getString("bye") + " " + Arrays.toString(ca.getStringArray("list")));
        p("locales [" + en.getLocale() + "] [" + fr.getLocale() + "] [" + ca.getLocale() + "] base "
                + ca.getBaseBundleName() + " same " + (fr == ResourceBundle.getBundle("MiscApiTest$Res", Locale.FRENCH)));
        p("keys " + new TreeSet<String>(ca.keySet()) + " contains " + ca.containsKey("list") + " "
                + ca.containsKey("nope") + " " + Collections.list(ca.getKeys()).size());
        try {
            ca.getString("nope");
        } catch (MissingResourceException e) {
            p("missing key: " + e.getMessage() + " | " + e.getKey());
        }
        try {
            ResourceBundle.getBundle("NoSuchBundle", Locale.ENGLISH);
        } catch (MissingResourceException e) {
            p("missing bundle: " + e.getMessage() + " | " + e.getClassName());
        }
        PropertyResourceBundle prb = new PropertyResourceBundle(new ByteArrayInputStream(
                "greet = caf\u00e9 \\u00e9\nother: x\n# comment\n".getBytes("UTF-8")));
        p("properties " + prb.getString("greet") + " " + prb.getString("other") + " "
                + new TreeSet<String>(prb.keySet()));
        PropertyResourceBundle latin = new PropertyResourceBundle(new ByteArrayInputStream(new byte[] {'k', '=', (byte) 0xe9, 'x', '\n'}));
        p("latin-1 fallback " + (int) latin.getString("k").charAt(0));
        ResourceBundle.Control ctl = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_DEFAULT);
        p("control " + ctl.getCandidateLocales("x", new Locale("de", "CH", "POSIX")) + " "
                + ctl.toBundleName("pkg.Msgs", Locale.US) + " " + ctl.toResourceName("pkg.Msgs_en", "properties"));

        // java.sql
        java.sql.Date d = java.sql.Date.valueOf("2024-3-7");
        p("sql date " + d + " " + tryIt(d::getHours) + " " + tryIt(() -> java.sql.Date.valueOf("24-03-07")));
        java.sql.Time t = java.sql.Time.valueOf("7:05:09");
        p("sql time " + t + " " + tryIt(t::getYear));
        java.sql.Timestamp ts = java.sql.Timestamp.valueOf("2021-01-02 03:04:05.12");
        java.sql.Timestamp ts2 = java.sql.Timestamp.valueOf("2021-01-02 03:04:05.120000001");
        p("timestamp " + ts + " " + ts.getNanos() + " " + ts2 + " " + ts.before(ts2) + " " + ts.compareTo(ts2) + " "
                + ts.equals(ts2) + " " + java.sql.Timestamp.valueOf("2000-01-01 00:00:00"));
        java.sql.Timestamp neg = new java.sql.Timestamp(-1500);
        p("negative millis " + neg.getTime() + " " + neg.getNanos() + " " + tryIt(() -> java.sql.Timestamp.valueOf("bad")));
        java.sql.SQLException e1 = new java.sql.SQLException("first", "42000", 7, new IOException("io"));
        e1.setNextException(new java.sql.SQLException("second"));
        e1.setNextException(new java.sql.SQLException("third", new IllegalStateException("cause3")));
        StringBuilder chain = new StringBuilder();
        for (Throwable th : e1) chain.append(th.getMessage()).append(' ');
        p("sql chain " + chain + e1.getSQLState() + " " + e1.getErrorCode());

        // StampedLock
        StampedLock sl = new StampedLock();
        long opt = sl.tryOptimisticRead();
        p("optimistic " + StampedLock.isOptimisticReadStamp(opt) + " " + sl.validate(opt));
        long w = sl.writeLock();
        p("write " + StampedLock.isWriteLockStamp(w) + " " + sl.validate(opt) + " " + sl.isWriteLocked() + " "
                + sl.tryReadLock() + " " + sl.tryOptimisticRead());
        long r = sl.tryConvertToReadLock(w);
        p("converted " + StampedLock.isReadLockStamp(r) + " " + sl.isWriteLocked() + " " + sl.getReadLockCount()
                + " old optimistic valid " + sl.validate(opt));
        long r2 = sl.readLock();
        p("two readers " + sl.getReadLockCount() + " " + sl.tryWriteLock() + " " + sl.tryConvertToWriteLock(r));
        sl.unlockRead(r2);
        long w2 = sl.tryConvertToWriteLock(r);
        p("upgrade " + StampedLock.isWriteLockStamp(w2) + " " + tryIt(() -> {
            sl.unlockRead(r);
            return "unlocked";
        }));
        sl.unlock(w2);
        String sls = sl.toString();
        p("unlocked " + sls.substring(sls.indexOf('[')) + " " + sl.tryUnlockWrite());
        Thread[] th = new Thread[4];
        final long[] counter = {0};
        for (int i = 0; i < th.length; i++) {
            th[i] = new Thread(() -> {
                for (int k = 0; k < 2000; k++) {
                    long s = sl.writeLock();
                    counter[0]++;
                    sl.unlockWrite(s);
                }
            });
            th[i].start();
        }
        for (Thread x : th) x.join();
        p("contended " + counter[0]);

        // charset coders
        java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
        byte[] text = "a\u00e9\u20ac\ud83d\ude00z".getBytes("UTF-8");
        java.nio.charset.CharsetDecoder dec = utf8.newDecoder();
        java.nio.CharBuffer out = java.nio.CharBuffer.allocate(3);
        StringBuilder got = new StringBuilder();
        StringBuilder steps = new StringBuilder();
        java.nio.ByteBuffer in = java.nio.ByteBuffer.allocate(16);
        for (int i = 0; i < text.length; i++) {
            in.put(text[i]);
            in.flip();
            for (;;) {
                java.nio.charset.CoderResult cr = dec.decode(in, out, i == text.length - 1);
                steps.append(cr).append(',');
                out.flip();
                got.append(out);
                out.clear();
                if (!cr.isOverflow()) break;
            }
            in.compact();
        }
        dec.flush(out);
        p("streamed " + got.toString().equals("a\u00e9\u20ac\ud83d\ude00z") + " " + steps.toString().replace("UNDERFLOW", "U"));
        String[] bad = {"c3", "c328", "e282", "e228a1", "e2822a", "f09f98", "f028", "c0af", "eda080", "f4908080",
            "ff", "80", "f09f9828", "e0a080"};
        for (String hex : bad) {
            byte[] b = new byte[hex.length() / 2];
            for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
            String report;
            try {
                report = utf8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString();
            } catch (java.nio.charset.CharacterCodingException e) {
                report = e.getClass().getSimpleName() + "(" + e.getMessage() + ")";
            }
            StringBuilder rep = new StringBuilder();
            for (char ch : utf8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
                    .decode(java.nio.ByteBuffer.wrap(b)).toString().toCharArray()) {
                rep.append(Integer.toHexString(ch)).append(' ');
            }
            String ign = utf8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.IGNORE)
                    .decode(java.nio.ByteBuffer.wrap(b)).toString();
            p("bad " + hex + ": " + report + " | " + rep + "| ignore " + ign.length());
        }
        java.nio.charset.CharsetEncoder latinEnc = java.nio.charset.StandardCharsets.ISO_8859_1.newEncoder();
        p("latin canEncode " + latinEnc.canEncode('\u00ff') + " " + latinEnc.canEncode('\u0100') + " "
                + latinEnc.canEncode("ab\u20ac") + " replacement " + Arrays.toString(latinEnc.replacement()));
        try {
            latinEnc.encode(java.nio.CharBuffer.wrap("x\u20acy"));
        } catch (java.nio.charset.CharacterCodingException e) {
            p("unmappable: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
        p("replaced " + Arrays.toString(java.nio.charset.StandardCharsets.US_ASCII.newEncoder()
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)
                .encode(java.nio.CharBuffer.wrap("h\u00e9\ud83d\ude00!")).array()));
        try {
            utf8.newEncoder().encode(java.nio.CharBuffer.wrap("a\udc00"));
        } catch (java.nio.charset.CharacterCodingException e) {
            p("lone surrogate: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
        java.nio.ByteBuffer u16 = java.nio.charset.StandardCharsets.UTF_16.newEncoder().encode(java.nio.CharBuffer.wrap("hi"));
        byte[] u16b = new byte[u16.remaining()];
        u16.get(u16b);
        p("utf16 " + Arrays.toString(u16b) + " "
                + java.nio.charset.StandardCharsets.UTF_16.newDecoder().decode(java.nio.ByteBuffer.wrap(
                        new byte[] {(byte) 0xff, (byte) 0xfe, 'h', 0, 'i', 0})) + " "
                + java.nio.charset.StandardCharsets.UTF_16LE.newDecoder().decode(java.nio.ByteBuffer.wrap(
                        new byte[] {'o', 0, 'k', 0})));
        java.nio.charset.CoderResult mr = java.nio.charset.CoderResult.malformedForLength(3);
        p("coder results " + mr + " " + mr.length() + " " + java.nio.charset.CoderResult.unmappableForLength(9) + " "
                + java.nio.charset.CoderResult.OVERFLOW + " " + tryIt(() -> java.nio.charset.CoderResult.UNDERFLOW.length())
                + " " + (mr == java.nio.charset.CoderResult.malformedForLength(3)));
        try {
            java.nio.charset.CharsetDecoder d2 = utf8.newDecoder();
            d2.decode(java.nio.ByteBuffer.wrap(new byte[] {1}), java.nio.CharBuffer.allocate(4), true);
            d2.decode(java.nio.ByteBuffer.wrap(new byte[] {1}), java.nio.CharBuffer.allocate(4), false);
        } catch (IllegalStateException e) {
            p("state: " + e.getMessage());
        }

        // ClassValue and accumulators
        final int[] computed = {0};
        ClassValue<String> cv = new ClassValue<String>() {
            protected String computeValue(Class<?> type) {
                computed[0]++;
                return type.getSimpleName().toLowerCase();
            }
        };
        p("classvalue " + cv.get(String.class) + " " + cv.get(String.class) + " " + cv.get(Integer.class) + " "
                + computed[0]);
        cv.remove(String.class);
        p("after remove " + cv.get(String.class) + " " + computed[0]);
        LongAccumulator max = new LongAccumulator(Long::max, Long.MIN_VALUE);
        for (long v : new long[] {3, 9, -2}) max.accumulate(v);
        DoubleAdder da = new DoubleAdder();
        da.add(1.5);
        da.add(2.25);
        DoubleAccumulator dmin = new DoubleAccumulator(Math::min, Double.POSITIVE_INFINITY);
        dmin.accumulate(4.5);
        dmin.accumulate(-1.0);
        p("accumulators " + max + " " + max.intValue() + " " + max.getThenReset() + " " + max.get() + " " + da + " "
                + da.sumThenReset() + " " + da.sum() + " " + dmin + " " + dmin.longValue());
    }
}
