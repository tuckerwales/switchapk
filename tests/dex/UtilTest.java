// javac-release: 21
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;

/**
 * java.util additions from the WS16 gap sweep: run on OpenJDK and on switchapk by tests/run_dex_test.sh, output
 * must match.
 */
public class UtilTest {
    static void p(Object o) {
        System.out.println(o);
    }

    static String name(Throwable t) {
        return t.getClass().getName();
    }

    interface Check {
        void run() throws Exception;
    }

    static void expect(String what, Check c) {
        try {
            c.run();
            p(what + " no exception");
        } catch (Exception e) {
            p(what + " " + name(e));
        }
    }

    public static void main(String[] args) throws Exception {
        immutables();
        arrays();
        spliterators();
        collections();
        dates();
        misc();
        formats();
        codecs();
        randoms();
        properties();
        locales();
        p("done");
    }

    static void immutables() {
        p("-- immutables");
        p(List.of() + " " + List.of(1) + " " + List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10) + " " + List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11));
        p(new TreeSet<>(Set.of(3, 1, 2)) + " " + Set.of().size() + " " + Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).size());
        p(new TreeMap<>(Map.of(1, "a", 2, "b", 3, "c", 4, "d", 5, "e", 6, "f", 7, "g")) + " "
                + Map.of(1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10).size());
        p(new TreeMap<>(Map.ofEntries(Map.entry("k", 1), Map.entry("j", 2))) + " " + Map.Entry.copyOf(Map.entry("a", "b")));
        expect("list null", () -> List.of(1, null));
        expect("set dup", () -> Set.of(1, 1));
        expect("map dup", () -> Map.of(1, "a", 1, "b"));
        expect("map null", () -> Map.of(1, null));
        expect("list add", () -> List.of(1).add(2));
        expect("copyOf null", () -> List.copyOf(Arrays.asList(1, null)));
        expect("entry null", () -> Map.entry(null, 1));
    }

    static void arrays() {
        p("-- arrays");
        int[] a = {1, 2, 3, 4, 5};
        int[] b = {9, 2, 3, 4, 0};
        p(Arrays.equals(a, 1, 4, b, 1, 4) + " " + Arrays.mismatch(a, b) + " " + Arrays.mismatch(a, a.clone()) + " "
                + Arrays.mismatch(new int[] {1, 2}, new int[] {1, 2, 3}) + " " + Arrays.compare(a, b) + " "
                + Arrays.compare(new int[] {1, 2}, new int[] {1, 2, 3}) + " " + Arrays.compare((int[]) null, a));
        p(Arrays.compareUnsigned(new byte[] {(byte) 0xff}, new byte[] {1}) + " " + Arrays.compare(new byte[] {(byte) 0xff},
                new byte[] {1}) + " " + Arrays.compareUnsigned(new long[] {-1L}, 0, 1, new long[] {5L}, 0, 1));
        p(Arrays.mismatch(new double[] {Double.NaN, 0.0}, new double[] {Double.NaN, -0.0}) + " "
                + Arrays.equals(new float[] {Float.NaN}, 0, 1, new float[] {Float.NaN}, 0, 1) + " "
                + Arrays.compare(new boolean[] {true}, new boolean[] {false}) + " "
                + Arrays.compare(new char[] {'a', 'b'}, new char[] {'a', 'c'}));
        String[] s1 = {"a", "B", "c"};
        String[] s2 = {"A", "b", "C"};
        p(Arrays.equals(s1, s2, String.CASE_INSENSITIVE_ORDER) + " " + Arrays.mismatch(s1, s2) + " "
                + Arrays.compare(new String[] {"a", null}, new String[] {"a", "b"}) + " "
                + Arrays.compare(s1, 0, 2, s2, 0, 2, String.CASE_INSENSITIVE_ORDER) + " "
                + Arrays.equals(new Object[] {1, 2, 3}, 1, 3, new Object[] {2, 3}, 0, 2));
        p(Arrays.binarySearch(new Object[] {1, 3, 5, 7}, 1, 3, 5) + " " + Arrays.binarySearch(new Object[] {1, 3, 5, 7}, 1, 3, 4));
        long[] l = new long[4];
        Arrays.setAll(l, i -> i * 10L);
        double[] d = new double[3];
        Arrays.parallelSetAll(d, i -> i / 2.0);
        int[] pre = {1, 2, 3, 4};
        Arrays.parallelPrefix(pre, Integer::sum);
        Integer[] obj = {5, 1, 4};
        Arrays.parallelSort(obj);
        p(Arrays.toString(l) + " " + Arrays.toString(d) + " " + Arrays.toString(pre) + " " + Arrays.toString(obj));
        p(Arrays.stream(a, 1, 3).sum() + " " + Arrays.stream(l).sum() + " " + Arrays.stream(d, 1, 3).sum() + " "
                + Arrays.stream(new long[] {1, 2, 3}, 0, 2).count());
        expect("mismatch range", () -> Arrays.mismatch(a, 3, 2, b, 0, 1));
    }

    static void spliterators() {
        p("-- spliterators");
        Spliterator.OfInt si = Arrays.spliterator(new int[] {1, 2, 3, 4}, 1, 3);
        StringBuilder sb = new StringBuilder();
        si.forEachRemaining((int i) -> sb.append(i));
        p(sb + " " + si.estimateSize() + " " + si.hasCharacteristics(Spliterator.SIZED));
        PrimitiveIterator.OfLong it = Spliterators.iterator(Arrays.spliterator(new long[] {7, 8}));
        p(it.nextLong() + " " + it.next() + " " + it.hasNext());
        Iterator<String> its = Spliterators.iterator(Arrays.spliterator(new String[] {"x", "y", "z"}, 1, 3));
        p(its.next() + its.next() + its.hasNext());
        p(Spliterators.emptyDoubleSpliterator().estimateSize() + " "
                + Spliterators.spliterator(new double[] {1.5, 2.5}, 0).tryAdvance((double v) -> p("got " + v)));
        PrimitiveIterator.OfInt pi = IntStream.range(0, 3).iterator();
        pi.forEachRemaining((Integer x) -> sb.append(x));
        p(sb);
    }

    static void collections() {
        p("-- collections");
        p(Collections.lastIndexOfSubList(Arrays.asList(1, 2, 1, 2, 3), Arrays.asList(1, 2)) + " "
                + Collections.lastIndexOfSubList(Arrays.asList(1, 2), Arrays.asList(3)) + " "
                + Collections.emptyNavigableSet().size() + " " + Collections.emptySortedMap().size() + " "
                + Collections.emptyNavigableMap().isEmpty());
        NavigableSet<Integer> ns = new TreeSet<>(Arrays.asList(3, 1, 2));
        p(Collections.unmodifiableNavigableSet(ns).first() + " " + Collections.synchronizedNavigableSet(ns).last() + " "
                + Collections.checkedSet(new HashSet<>(Arrays.asList("a")), String.class).size() + " "
                + Collections.checkedQueue(new ArrayDeque<>(Arrays.asList(1)), Integer.class).peek());
        PriorityQueue<Integer> pq = new PriorityQueue<>(new TreeSet<>(Comparator.reverseOrder()) {
            {
                add(1);
                add(5);
                add(3);
            }
        });
        p(pq.poll() + " " + new PriorityQueue<>(pq).poll());
        Vector<Integer> v = new Vector<>(100);
        v.add(1);
        v.trimToSize();
        p(v.capacity());
        IdentityHashMap<String, Integer> im = new IdentityHashMap<>();
        im.put("k", 1);
        @SuppressWarnings("unchecked")
        IdentityHashMap<String, Integer> clone = (IdentityHashMap<String, Integer>) im.clone();
        clone.put("j", 2);
        p(im.size() + " " + clone.size());
        p(new IntSummaryStatistics(3, 1, 9, 12) + " | " + new LongSummaryStatistics(0, 5, 1, 0).getMax());
        expect("stats", () -> new DoubleSummaryStatistics(2, 5, 1, 0));
    }

    @SuppressWarnings("deprecation")
    static void dates() {
        p("-- dates");
        Date d = new Date(2020 - 1900, 1, 3, 4, 5);
        p(d.getTime() + " " + d.getSeconds());
        d.setYear(121);
        d.setMonth(11);
        d.setDate(31);
        d.setHours(23);
        d.setMinutes(59);
        d.setSeconds(58);
        p(d.getTime() + " " + d.getTimezoneOffset());
        p(Date.UTC(99, 0, 1, 12, 0, 0) + " " + Date.UTC(70, 0, 1, 0, 0, 0));
        String[] in = {"Sat, 12 Aug 1995 13:30:00 GMT", "Sat, 12 Aug 1995 13:30:00 GMT+0430", "12 Aug 1995", "8/12/1995",
            "1995/8/12 10:20", "Aug 12, 1995 1:05 PM", "Mon Jan 01 00:00:00 UTC 2001", "Tue, 3 Jun 2008 11:05:30 PST"};
        for (String s : in) {
            p(s + " -> " + Date.parse(s));
        }
        expect("parse", () -> Date.parse("not a date"));
        p(new Date("1 Jan 2000").getTime());
    }

    static void misc() {
        p("-- misc");
        p(Objects.checkIndex(3L, 5L) + " " + Objects.checkFromToIndex(1L, 2L, 3L) + " " + Objects.checkFromIndexSize(1L, 1L, 3L));
        expect("checkIndex", () -> Objects.checkIndex(5L, 5L));
        StringBuilder sb = new StringBuilder();
        OptionalInt.of(4).ifPresentOrElse(x -> sb.append(x), () -> sb.append("none"));
        OptionalLong.empty().ifPresentOrElse(x -> sb.append(x), () -> sb.append("none"));
        OptionalDouble.of(1.5).ifPresentOrElse(x -> sb.append(x), () -> sb.append("none"));
        p(sb + " " + OptionalInt.of(2).stream().sum() + " " + OptionalLong.empty().stream().count() + " "
                + OptionalDouble.of(2.5).stream().sum());
        p(new NoSuchElementException("m", new RuntimeException("c")).getCause().getMessage() + " "
                + new ConcurrentModificationException(new RuntimeException("x")).getMessage() + " "
                + new InvalidPropertiesFormatException(new IOException("io")).getMessage());
        IllformedLocaleException ile = new IllformedLocaleException("bad", 3);
        p(ile.getMessage() + " " + ile.getErrorIndex() + " " + new IllformedLocaleException("x").getErrorIndex());
        UUID u1 = UUID.fromString("c232ab00-9414-11ec-b3c8-9f6bdeced846");
        p(u1.version() + " " + u1.timestamp() + " " + u1.clockSequence() + " " + u1.node());
        expect("v4 timestamp", () -> UUID.fromString("123e4567-e89b-42d3-a456-426614174000").timestamp());
        BitSet bs = BitSet.valueOf(new long[] {0b101101L});
        p(bs.previousClearBit(3) + " " + bs.previousClearBit(0) + " " + Arrays.toString(bs.stream().toArray()) + " "
                + BitSet.valueOf(ByteBuffer.wrap(new byte[] {3, 0, 1})) + " "
                + BitSet.valueOf(java.nio.LongBuffer.wrap(new long[] {6L})));
    }

    static class Money implements Formattable {
        public void formatTo(Formatter f, int flags, int width, int precision) {
            f.format("%s$%d[%d,%d]", (flags & FormattableFlags.UPPERCASE) != 0 ? "USD" : "usd", 42, width, precision);
        }
    }

    static void formats() throws Exception {
        p("-- formats");
        p(String.format("%s|%S|%10.2s", new Money(), new Money(), new Money()));
        String[] fmts = {"%q", "%d", "%c", "%5"};
        Object[] vals = {1, "x", 3.5, 1};
        for (int i = 0; i < fmts.length; i++) {
            try {
                String.format(fmts[i], vals[i]);
                p(fmts[i] + " ok");
            } catch (IllegalFormatException e) {
                p(name(e) + ": " + e.getMessage());
            }
        }
        try {
            String.format("%s %s", "only");
        } catch (MissingFormatArgumentException e) {
            p(e.getFormatSpecifier() + " " + e.getMessage());
        }
        IllegalFormatConversionException ic = new IllegalFormatConversionException('d', String.class);
        p(ic.getConversion() + " " + ic.getArgumentClass().getSimpleName() + " " + ic.getMessage());
        p(new DuplicateFormatFlagsException("--").getMessage() + " | " + new FormatFlagsConversionMismatchException("#", 'd').getMessage()
                + " | " + new IllegalFormatCodePointException(0x110000).getMessage() + " | "
                + new IllegalFormatPrecisionException(3).getMessage() + " | " + new IllegalFormatWidthException(4).getMessage()
                + " | " + new MissingFormatWidthException("%-d").getMessage() + " | "
                + new UnknownFormatFlagsException("?").getMessage() + " | " + new IllegalFormatFlagsException("+-").getMessage()
                + " | " + new UnknownFormatConversionException("q").getConversion());
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        Formatter f = new Formatter(bo, "UTF-8", Locale.US);
        f.format("%s=%d", "é", 5);
        f.close();
        p(new String(bo.toByteArray(), StandardCharsets.UTF_8));
        expect("closed", () -> f.format("x"));
        expect("charset", () -> new Formatter(new ByteArrayOutputStream(), "no-such-charset", Locale.US));
    }

    static void codecs() throws Exception {
        p("-- codecs");
        byte[] data = "hello base64 world, longer than a line?".getBytes(StandardCharsets.US_ASCII);
        Base64.Encoder mime = Base64.getMimeEncoder(16, new byte[] {'#'});
        p(new String(mime.encode(data), StandardCharsets.US_ASCII));
        p(new String(Base64.getMimeDecoder().decode(mime.encode(data)), StandardCharsets.US_ASCII));
        byte[] dst = new byte[100];
        int n = Base64.getEncoder().encode(data, dst);
        byte[] back = new byte[100];
        int m = Base64.getDecoder().decode(Arrays.copyOf(dst, n), back);
        p(n + " " + m + " " + new String(back, 0, m, StandardCharsets.US_ASCII));
        ByteBuffer eb = Base64.getUrlEncoder().encode(ByteBuffer.wrap(new byte[] {(byte) 0xfb, (byte) 0xff}));
        p(StandardCharsets.US_ASCII.decode(eb) + " " + Base64.getUrlDecoder().decode(ByteBuffer.wrap("-_8".getBytes())).remaining());
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        OutputStream os = Base64.getEncoder().wrap(bo);
        os.write(data, 0, 10);
        os.write(data, 10, data.length - 10);
        os.close();
        InputStream is = Base64.getDecoder().wrap(new ByteArrayInputStream(bo.toByteArray()));
        p(new String(is.readAllBytes(), StandardCharsets.US_ASCII).equals(new String(data, StandardCharsets.US_ASCII)));
        expect("small dst", () -> Base64.getEncoder().encode(data, new byte[4]));
        expect("bad separator", () -> Base64.getMimeEncoder(10, new byte[] {'A'}));
        p(Base64.getMimeEncoder(3, new byte[] {'\n'}).encodeToString(data).length());
    }

    static void randoms() {
        p("-- randoms");
        p(Arrays.toString(new Random(42).ints(5).toArray()));
        p(Arrays.toString(new Random(42).ints(5, 10, 20).toArray()));
        p(Arrays.toString(new Random(7).longs(3).toArray()));
        p(Arrays.toString(new Random(7).longs(4, -5, 1000000000000L).toArray()));
        p(Arrays.toString(new Random(7).doubles(2).toArray()));
        p(Arrays.toString(new Random(7).doubles(3, 1.0, 2.0).toArray()));
        p(new Random(3).ints().limit(4).sum() + " " + new Random(3).ints(0, 4).limit(10).boxed().collect(Collectors.toList()));
        expect("bad bound", () -> new Random().ints(5, 5));
        expect("bad size", () -> new Random().longs(-1));
    }

    static void properties() throws Exception {
        p("-- properties");
        Properties pr = new Properties(8);
        pr.setProperty("b", "two & <three>");
        pr.setProperty("a", "été");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        pr.storeToXML(bo, "comment \"q\"");
        Properties back = new Properties();
        back.loadFromXML(new ByteArrayInputStream(bo.toByteArray()));
        p(new TreeMap<>(back));
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n"
                + "<properties><comment>c</comment><entry key=\"x\">1</entry><entry key='y'>&#65;&amp;</entry>"
                + "<entry key=\"z\"/></properties>";
        Properties fx = new Properties();
        fx.loadFromXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        p(new TreeMap<>(fx));
        StringWriter sw = new StringWriter();
        pr.store(sw, null);
        p(sw.toString().split("\n", 2)[1].trim().replace("\n", "|"));
        bo.reset();
        pr.store(bo, null);
        p(new String(bo.toByteArray(), StandardCharsets.ISO_8859_1).split("\n", 2)[1].trim().replace("\n", "|"));
    }

    static void locales() {
        p("-- locales");
        String[] tags = {"zh-Hans-CN", "en-US-x-lvariant-POSIX", "de-DE-u-co-phonebk", "sr-Latn-RS", "en-GB-oed",
            "i-klingon", "und", "EN-us", "es-419", "x-private", "en-a-bbb-x-a-ccc", "zh-yue-HK", "iw",
            "en-u-attr1-attr2-ca-gregory-nu-arab", "en-US-1abc-polyton", "bad tag", "en-US-", "en-x"};
        for (String t : tags) {
            Locale l = Locale.forLanguageTag(t);
            p(t + " => [" + l + "] " + l.getLanguage() + "/" + l.getScript() + "/" + l.getCountry() + "/" + l.getVariant()
                    + " " + l.toLanguageTag() + " " + l.getExtensionKeys() + " " + l.getUnicodeLocaleKeys() + " "
                    + l.getUnicodeLocaleAttributes() + " " + l.getUnicodeLocaleType("ca") + " " + l.getExtension('x'));
        }
        Locale[] ls = {new Locale("ja", "JP", "JP"), new Locale("th", "TH", "TH"), new Locale("no", "NO", "NY"),
            new Locale("iw"), new Locale("in", "ID"), new Locale("", "US"), new Locale("en", "", "POSIX"),
            new Locale("EN", "us", "WIN"), new Locale("xx", "YY", "some_var"), new Locale("de", "DE", "1901_POSIX")};
        for (Locale l : ls) {
            p(l + " | " + l.toLanguageTag() + " | " + l.getLanguage() + " | " + l.getVariant() + " | " + l.hasExtensions());
        }
        Locale[] disp = {Locale.US, Locale.forLanguageTag("zh-Hans-CN"), Locale.forLanguageTag("sr-Latn-RS"),
            new Locale("", "GB"), Locale.forLanguageTag("es-419"), Locale.forLanguageTag("zh-yue-HK"), new Locale("xx", "YY"),
            Locale.forLanguageTag("pt-BR"), Locale.forLanguageTag("ar-EG"), Locale.ROOT};
        for (Locale l : disp) {
            p(l.toLanguageTag() + ": " + l.getDisplayName(Locale.ENGLISH) + " / " + l.getDisplayLanguage(Locale.ENGLISH)
                    + " / " + l.getDisplayCountry(Locale.ENGLISH) + " / " + l.getDisplayScript(Locale.ENGLISH));
        }
        p(Locale.US.getISO3Language() + " " + Locale.US.getISO3Country() + " " + Locale.forLanguageTag("zh-TW").getISO3Country()
                + " " + new Locale("abc").getISO3Language() + " " + Locale.ROOT.getISO3Country());
        expect("iso3 lang", () -> new Locale("xx").getISO3Language());
        expect("iso3 country", () -> new Locale("", "123").getISO3Country());
        p(Locale.getISOLanguages().length + " " + Locale.getISOCountries().length + " "
                + Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA3).contains("USA") + " "
                + Locale.getISOCountries(Locale.IsoCountryCode.PART3).size() + " " + Locale.PRC + " " + Locale.TAIWAN);
        Locale b = new Locale.Builder().setLanguage("sr").setScript("latn").setRegion("rs").setVariant("posix")
                .setUnicodeLocaleKeyword("nu", "arab").setExtension('a', "foo-bar").addUnicodeLocaleAttribute("xyz").build();
        p(b + " " + b.toLanguageTag() + " " + b.stripExtensions());
        p(new Locale.Builder().setLocale(b).removeUnicodeLocaleAttribute("xyz").setUnicodeLocaleKeyword("nu", null).build()
                + " " + new Locale.Builder().setLanguageTag("de-DE-u-co-phonebk").clearExtensions().build() + " "
                + new Locale.Builder().setLanguage("fr").clear().build().equals(Locale.ROOT));
        try {
            new Locale.Builder().setLanguage("toolongla");
        } catch (IllformedLocaleException e) {
            p(e.getMessage() + " " + e.getErrorIndex());
        }
        expect("bad region", () -> new Locale.Builder().setRegion("ABC"));
        expect("bad key", () -> new Locale.Builder().setExtension('$', "x"));
        List<Locale.LanguageRange> r = Locale.LanguageRange.parse("en-US;q=1.0,en;q=0.5,fr-*;q=0.3,de;q=0");
        p(r);
        List<Locale> cand = Arrays.asList(Locale.forLanguageTag("fr-FR"), Locale.forLanguageTag("en-GB"), Locale.US,
            Locale.forLanguageTag("de"));
        p(Locale.filter(r, cand) + " " + Locale.lookup(r, cand) + " " + Locale.filterTags(r, Arrays.asList("en-us", "fr-ca", "de"))
                + " " + Locale.lookupTag(r, Arrays.asList("en", "fr")) + " "
                + Locale.lookupTag(Locale.LanguageRange.parse("zh-Hant-TW"), Arrays.asList("zh", "zh-Hant")));
        Locale saved = Locale.getDefault();
        Locale.setDefault(Locale.Category.DISPLAY, Locale.FRANCE);
        p(Locale.getDefault(Locale.Category.DISPLAY) + " " + Locale.getDefault(Locale.Category.FORMAT) + " " + Locale.getDefault());
        Locale.setDefault(saved);
        p(Locale.getDefault(Locale.Category.DISPLAY) + " " + new Locale("en", "US").equals(Locale.US) + " "
                + Locale.forLanguageTag("en-US-u-ca-x").equals(Locale.US) + " " + (Locale.getAvailableLocales().length > 100));
    }
}
