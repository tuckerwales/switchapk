import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import java.util.zip.*;

/**
 * java.util.zip conformance: run on OpenJDK and on switchapk by tests/run_dex_test.sh, output must match.
 * Compressed bytes are never printed (zlib versions and gzip header OS bytes differ); round trips, checksums
 * and streams made by other tools (Python's zlib and gzip) are.
 */
public class ZipTest {
    static void p(Object o) {
        System.out.println(o);
    }

    static String name(Throwable t) {
        return t.getClass().getName();
    }

    static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return b;
    }

    static byte[] sample(int n) {
        byte[] b = new byte[n];
        Random r = new Random(42);
        for (int i = 0; i < n; i++) {
            b[i] = (byte) ((i % 97 < 60) ? 'a' + (i % 26) : r.nextInt(256));
        }
        return b;
    }

    static long crc(byte[] b) {
        CRC32 c = new CRC32();
        c.update(b, 0, b.length);
        return c.getValue();
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[333];
        int n;
        while ((n = in.read(buf, 0, buf.length)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        checksums();
        deflateInflate();
        dictionary();
        foreignStreams();
        errors();
        streams();
        gzip();
        zipStreams();
        zipFile();
        entries();
        jars();
        p("done");
    }

    static void checksums() {
        p("-- checksums");
        byte[] hw = "hello world".getBytes(StandardCharsets.US_ASCII);
        Checksum[] cs = {new CRC32(), new Adler32(), new CRC32C()};
        for (Checksum c : cs) {
            c.update(hw, 0, hw.length);
            String a = Long.toHexString(c.getValue());
            c.reset();
            for (byte b : hw) {
                c.update(b);
            }
            String b = Long.toHexString(c.getValue());
            c.reset();
            c.update(ByteBuffer.wrap(hw, 2, 5));
            String d = Long.toHexString(c.getValue());
            c.reset();
            c.update(sample(100000));
            p(c.getClass().getSimpleName() + " " + a + " " + b + " " + d + " " + Long.toHexString(c.getValue()));
        }
        CRC32 c = new CRC32();
        p("empty " + c.getValue() + " " + new Adler32().getValue() + " " + new CRC32C().getValue());
        try {
            c.update(hw, 5, 10);
        } catch (Exception e) {
            p("range " + name(e));
        }
    }

    static byte[] deflate(byte[] in, int level, boolean nowrap, int chunk) {
        Deflater d = new Deflater(level, nowrap);
        d.setInput(in);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[chunk];
        while (!d.finished()) {
            int n = d.deflate(buf);
            out.write(buf, 0, n);
        }
        if (d.getTotalIn() != in.length || d.getBytesRead() != in.length) {
            p("bad totalIn " + d.getTotalIn());
        }
        if (d.getTotalOut() != out.size()) {
            p("bad totalOut");
        }
        d.end();
        return out.toByteArray();
    }

    static byte[] inflate(byte[] in, boolean nowrap, int chunk) throws DataFormatException {
        Inflater f = new Inflater(nowrap);
        f.setInput(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[chunk];
        while (!f.finished()) {
            int n = f.inflate(buf);
            if (n == 0 && !f.finished() && (f.needsInput() || f.needsDictionary())) {
                p("stalled");
                break;
            }
            out.write(buf, 0, n);
        }
        if (f.getTotalIn() != in.length || f.getTotalOut() != out.size()) {
            p("bad inflater totals " + f.getTotalIn() + " " + f.getTotalOut());
        }
        f.end();
        return out.toByteArray();
    }

    static void deflateInflate() throws Exception {
        p("-- deflate/inflate");
        byte[] data = sample(50000);
        for (int level : new int[] {Deflater.DEFAULT_COMPRESSION, 0, 1, 9}) {
            for (boolean nowrap : new boolean[] {false, true}) {
                byte[] z = deflate(data, level, nowrap, level == 1 ? 7 : 4096);
                byte[] back = inflate(z, nowrap, level == 9 ? 1 : 1000);
                p("level " + level + " nowrap " + nowrap + " ok " + Arrays.equals(back, data)
                        + " smaller " + (z.length < data.length));
            }
        }
        byte[] empty = inflate(deflate(new byte[0], 6, false, 64), false, 64);
        p("empty " + empty.length);

        // trailing bytes after the stream stay in getRemaining
        byte[] z = deflate("abcabcabc".getBytes(StandardCharsets.US_ASCII), 6, false, 100);
        byte[] withTail = Arrays.copyOf(z, z.length + 5);
        Inflater f = new Inflater();
        f.setInput(withTail);
        byte[] out = new byte[100];
        int n = f.inflate(out);
        p("tail " + new String(out, 0, n, StandardCharsets.US_ASCII) + " finished " + f.finished() + " remaining "
                + f.getRemaining() + " adler " + Integer.toHexString(f.getAdler()));
        f.reset();
        p("after reset finished " + f.finished() + " needsInput " + f.needsInput() + " totalIn " + f.getTotalIn());
        f.end();
        f.end();
        try {
            f.inflate(out);
        } catch (NullPointerException e) {
            p("ended inflater " + name(e));
        }

        // level and strategy changes mid stream
        Deflater d = new Deflater(Deflater.NO_COMPRESSION);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        d.setInput(data, 0, 20000);
        while (!d.needsInput()) {
            bo.write(buf, 0, d.deflate(buf));
        }
        d.setLevel(Deflater.BEST_COMPRESSION);
        d.setStrategy(Deflater.FILTERED);
        d.setInput(data, 20000, 30000);
        d.finish();
        while (!d.finished()) {
            bo.write(buf, 0, d.deflate(buf));
        }
        d.end();
        p("param change ok " + Arrays.equals(inflate(bo.toByteArray(), false, 512), data));

        // sync flush makes everything so far decodable
        d = new Deflater();
        d.setInput(data, 0, 1000);
        bo.reset();
        int k;
        while ((k = d.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH)) > 0) {
            bo.write(buf, 0, k);
        }
        f = new Inflater();
        f.setInput(bo.toByteArray());
        byte[] got = new byte[2000];
        int m = f.inflate(got);
        p("sync flush " + m + " finished " + f.finished() + " match " + Arrays.equals(Arrays.copyOf(got, m),
                Arrays.copyOf(data, 1000)));
        d.end();
        f.end();

        // ByteBuffer input and output
        d = new Deflater();
        ByteBuffer src = ByteBuffer.wrap(data, 100, 3000);
        d.setInput(src);
        d.finish();
        ByteBuffer dst = ByteBuffer.allocate(8000);
        while (!d.finished()) {
            d.deflate(dst);
        }
        p("bytebuffer src remaining " + src.remaining() + " dst position>0 " + (dst.position() > 0));
        dst.flip();
        f = new Inflater();
        f.setInput(dst);
        ByteBuffer res = ByteBuffer.allocate(4000);
        f.inflate(res);
        p("bytebuffer inflate " + res.position() + " " + dst.remaining() + " "
                + Arrays.equals(Arrays.copyOf(res.array(), res.position()), Arrays.copyOfRange(data, 100, 3100)));
        d.end();
        f.end();
    }

    static void dictionary() throws Exception {
        p("-- dictionary");
        byte[] dict = "dictionary words common phrase".getBytes(StandardCharsets.US_ASCII);
        byte[] data = "common phrase with dictionary words and a common phrase".getBytes(StandardCharsets.US_ASCII);
        Deflater d = new Deflater();
        d.setDictionary(dict);
        d.setInput(data);
        d.finish();
        byte[] buf = new byte[200];
        int n = d.deflate(buf);
        p("dict adler " + Integer.toHexString(d.getAdler()));
        d.end();
        Inflater f = new Inflater();
        f.setInput(buf, 0, n);
        byte[] out = new byte[200];
        int m = f.inflate(out);
        p("first " + m + " needsDictionary " + f.needsDictionary() + " adler " + Integer.toHexString(f.getAdler()));
        f.setDictionary(dict);
        m = f.inflate(out);
        p("then " + new String(out, 0, m, StandardCharsets.US_ASCII) + " finished " + f.finished());
        f.end();
    }

    static void foreignStreams() throws Exception {
        p("-- foreign");
        byte[] z = hex("789c0bc94855282ccd4cce56482aca2fcf5348cbaf50c82acd2d2856c82f4b2d5228014ae72456552aa4e4a7eb"
                + "2984d04c3100f93c3076");
        p(new String(inflate(z, false, 16), StandardCharsets.US_ASCII).length());
        byte[] gz = hex("1f8b08080000000002ff666f782e747874004bafca2c50482bcacf5528a82cc9c8cfe30200983622a311000000");
        p(new String(readAll(new GZIPInputStream(new ByteArrayInputStream(gz))), StandardCharsets.US_ASCII).trim());
        byte[] two = Arrays.copyOf(gz, gz.length * 2);
        System.arraycopy(gz, 0, two, gz.length, gz.length);
        p(new String(readAll(new GZIPInputStream(new ByteArrayInputStream(two))), StandardCharsets.US_ASCII)
                .replace('\n', '|'));
    }

    static void errors() throws Exception {
        p("-- errors");
        Inflater f = new Inflater();
        f.setInput(new byte[] {1, 2, 3, 4, 5, 6});
        try {
            f.inflate(new byte[10]);
        } catch (DataFormatException e) {
            p(name(e) + ": " + e.getMessage());
        }
        f.end();
        try {
            new GZIPInputStream(new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}));
        } catch (IOException e) {
            p(name(e) + ": " + e.getMessage());
        }
        try {
            new GZIPInputStream(new ByteArrayInputStream(new byte[] {0x1f}));
        } catch (IOException e) {
            p(name(e));
        }
        try {
            readAll(new InflaterInputStream(new ByteArrayInputStream(new byte[] {0x78, (byte) 0x9c, 1, 2, 3})));
        } catch (IOException e) {
            p(name(e) + ": " + e.getMessage());
        }
        byte[] z = deflate(sample(5000), 6, false, 999);
        try {
            readAll(new InflaterInputStream(new ByteArrayInputStream(z, 0, z.length / 2)));
        } catch (IOException e) {
            p(name(e) + ": " + e.getMessage());
        }
        try {
            new Deflater(12);
        } catch (IllegalArgumentException e) {
            p("level 12 " + name(e));
        }
    }

    static void streams() throws Exception {
        p("-- streams");
        byte[] data = sample(20000);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DeflaterOutputStream dos = new DeflaterOutputStream(bo);
        dos.write(data, 0, 5000);
        dos.write(data[5000]);
        dos.write(data, 5001, data.length - 5001);
        dos.close();
        InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(bo.toByteArray()));
        p("available " + iis.available() + " markSupported " + iis.markSupported());
        int first = iis.read();
        long skipped = iis.skip(999);
        byte[] rest = readAll(iis);
        p("first " + (first == (data[0] & 0xff)) + " skipped " + skipped + " rest " + rest.length + " ok "
                + Arrays.equals(rest, Arrays.copyOfRange(data, 1000, data.length)) + " available " + iis.available()
                + " read " + iis.read());
        iis.close();
        try {
            iis.read();
        } catch (IOException e) {
            p("closed " + e.getMessage());
        }

        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        InflaterOutputStream ios = new InflaterOutputStream(plain);
        byte[] z = bo.toByteArray();
        for (int i = 0; i < z.length; i += 77) {
            ios.write(z, i, Math.min(77, z.length - i));
        }
        ios.close();
        p("inflater output " + Arrays.equals(plain.toByteArray(), data));

        DeflaterInputStream dis = new DeflaterInputStream(new ByteArrayInputStream(data));
        byte[] z2 = readAll(dis);
        p("deflater input " + Arrays.equals(inflate(z2, false, 4096), data) + " available " + dis.available());
        dis.close();

        CheckedInputStream cis = new CheckedInputStream(new ByteArrayInputStream(data), new Adler32());
        cis.read();
        cis.skip(100);
        readAll(cis);
        Adler32 a = new Adler32();
        a.update(data);
        p("checked in " + (cis.getChecksum().getValue() == a.getValue()));
        CheckedOutputStream cos = new CheckedOutputStream(new ByteArrayOutputStream(), new CRC32());
        cos.write(data, 0, 10);
        cos.write(data[10]);
        cos.write(data, 11, data.length - 11);
        p("checked out " + (cos.getChecksum().getValue() == crc(data)));
    }

    static void gzip() throws Exception {
        p("-- gzip");
        byte[] data = sample(30000);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(bo, 1024, true);
        gos.write(data, 0, 10000);
        gos.flush();
        int flushed = bo.size();
        gos.write(data, 10000, 20000);
        gos.close();
        byte[] g = bo.toByteArray();
        p("magic " + Integer.toHexString(g[0] & 0xff) + Integer.toHexString(g[1] & 0xff) + " method " + g[2]
                + " flushed>10 " + (flushed > 10));
        GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(g), 100);
        byte[] back = readAll(gis);
        p("round trip " + Arrays.equals(back, data) + " read " + gis.read());
        gis.close();
        // trailer is CRC32 and length mod 2^32, little endian
        int n = g.length;
        long tcrc = (g[n - 8] & 0xff) | (g[n - 7] & 0xff) << 8 | (g[n - 6] & 0xff) << 16 | (long) (g[n - 5] & 0xff) << 24;
        int tlen = (g[n - 4] & 0xff) | (g[n - 3] & 0xff) << 8 | (g[n - 2] & 0xff) << 16 | (g[n - 1] & 0xff) << 24;
        p("trailer crc " + (tcrc == crc(data)) + " len " + tlen);
        // corrupt trailer
        g[n - 8] ^= 1;
        try {
            readAll(new GZIPInputStream(new ByteArrayInputStream(g)));
        } catch (ZipException e) {
            p(e.getMessage());
        }
        // small writes and empty gzip
        bo.reset();
        gos = new GZIPOutputStream(bo);
        gos.close();
        p("empty gzip " + readAll(new GZIPInputStream(new ByteArrayInputStream(bo.toByteArray()))).length);
    }

    static byte[] makeZip() throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(bo);
        zos.setComment("archive comment");
        ZipEntry e = new ZipEntry("dir/");
        zos.putNextEntry(e);
        zos.closeEntry();
        e = new ZipEntry("dir/hello.txt");
        e.setTime(1600000000000L);
        e.setComment("greeting");
        zos.putNextEntry(e);
        zos.write("Hello, zip!\n".getBytes(StandardCharsets.US_ASCII));
        byte[] big = sample(70000);
        zos.putNextEntry(new ZipEntry("big.bin"));
        zos.write(big, 0, 30000);
        zos.write(big, 30000, 40000);
        zos.closeEntry();
        byte[] stored = "stored bytes, not compressed".getBytes(StandardCharsets.US_ASCII);
        e = new ZipEntry("stored.txt");
        e.setMethod(ZipEntry.STORED);
        e.setSize(stored.length);
        e.setCrc(crc(stored));
        e.setExtra(new byte[] {(byte) 0xfe, (byte) 0xca, 2, 0, 7, 8});
        zos.putNextEntry(e);
        zos.write(stored);
        e = new ZipEntry("unicodé/ünï.txt");
        zos.putNextEntry(e);
        zos.write(new byte[0]);
        try {
            zos.putNextEntry(new ZipEntry("big.bin"));
        } catch (ZipException ex) {
            p(ex.getMessage());
        }
        zos.setLevel(Deflater.BEST_SPEED);
        zos.putNextEntry(new ZipEntry("fast.bin"));
        zos.write(big, 0, 1000);
        zos.close();
        try {
            zos.putNextEntry(new ZipEntry("late"));
        } catch (IOException ex) {
            p("after close " + ex.getMessage());
        }
        return bo.toByteArray();
    }

    static void zipStreams() throws Exception {
        p("-- zip streams");
        byte[] zip = makeZip();
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip));
        ZipEntry e;
        while ((e = zis.getNextEntry()) != null) {
            String before = e.getName() + " dir=" + e.isDirectory() + " method=" + e.getMethod() + " size="
                    + e.getSize() + " time=" + (e.getTime() > 0);
            byte[] b = e.getName().equals("fast.bin") ? null : readAll(zis);
            p(before + " read=" + (b == null ? "skipped" : b.length) + " after size=" + e.getSize() + " csize>0="
                    + (e.getCompressedSize() > 0) + " crc=" + Long.toHexString(e.getCrc()));
            if (e.getName().equals("dir/hello.txt")) {
                p("  text " + new String(b, StandardCharsets.US_ASCII).trim() + " time " + e.getTime());
            }
            if (e.getName().equals("stored.txt")) {
                p("  extra " + Arrays.toString(e.getExtra()));
            }
        }
        p("available " + zis.available());
        zis.close();

        // skipping entries without reading them
        zis = new ZipInputStream(new ByteArrayInputStream(zip));
        int count = 0;
        while (zis.getNextEntry() != null) {
            count++;
        }
        p("entries " + count);
        zis.close();

        // a STORED entry with a wrong size is refused
        ZipOutputStream zos = new ZipOutputStream(new ByteArrayOutputStream());
        ZipEntry bad = new ZipEntry("bad");
        bad.setMethod(ZipEntry.STORED);
        try {
            zos.putNextEntry(bad);
        } catch (ZipException ex) {
            p(ex.getMessage());
        }
        bad.setSize(3);
        bad.setCrc(0);
        zos.putNextEntry(bad);
        zos.write(new byte[] {1, 2});
        try {
            zos.closeEntry();
        } catch (ZipException ex) {
            p(ex.getMessage());
        }
        try {
            zos.write(new byte[] {1, 2, 3, 4}, 0, 4);
        } catch (ZipException ex) {
            p(ex.getMessage());
        }

        // not a zip
        zis = new ZipInputStream(new ByteArrayInputStream("plain text".getBytes(StandardCharsets.US_ASCII)));
        p("plain " + zis.getNextEntry());
    }

    static void zipFile() throws Exception {
        p("-- zip file");
        File f = new File(System.getProperty("java.io.tmpdir"), "ziptest-" + System.nanoTime() + ".zip");
        try (FileOutputStream fo = new FileOutputStream(f)) {
            fo.write(makeZip());
        }
        ZipFile zf = new ZipFile(f);
        p("size " + zf.size() + " comment " + zf.getComment() + " name ok " + zf.getName().equals(f.getPath()));
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            p(e.getName() + " " + e.getMethod() + " " + e.getSize() + " " + Long.toHexString(e.getCrc()) + " "
                    + e.getComment());
        }
        p("stream " + zf.stream().map(ZipEntry::getName).reduce("", (a, b) -> a + b.length() + ","));
        ZipEntry h = zf.getEntry("dir/hello.txt");
        p("time " + h.getTime() + " " + h.getLastModifiedTime());
        InputStream in = zf.getInputStream(h);
        p("available " + in.available());
        p(new String(readAll(in), StandardCharsets.US_ASCII).trim());
        p("dir lookup " + zf.getEntry("dir") + " missing " + zf.getEntry("nope"));
        ZipEntry big = zf.getEntry("big.bin");
        InputStream bi = zf.getInputStream(big);
        InputStream bi2 = zf.getInputStream(big);
        byte[] a = new byte[1000];
        int got = bi.read(a);
        byte[] all = readAll(bi2);
        p("interleaved " + (got > 0) + " " + Arrays.equals(all, sample(70000)) + " "
                + (readAll(bi).length + got));
        p("stored " + new String(readAll(zf.getInputStream(zf.getEntry("stored.txt"))), StandardCharsets.US_ASCII));
        p("unicode " + zf.getEntry("unicodé/ünï.txt").getSize());
        InputStream open = zf.getInputStream(big);
        zf.close();
        zf.close();
        try {
            zf.getEntry("big.bin");
        } catch (IllegalStateException e) {
            p("closed " + e.getMessage());
        }
        try {
            open.read();
        } catch (IOException e) {
            p("open stream " + name(e));
        }
        f.delete();
        File empty = new File(System.getProperty("java.io.tmpdir"), "ziptest-empty-" + System.nanoTime());
        new FileOutputStream(empty).close();
        try {
            new ZipFile(empty);
        } catch (ZipException e) {
            p(e.getMessage());
        }
        try (FileOutputStream fo = new FileOutputStream(empty)) {
            fo.write(new byte[100]);
        }
        try {
            new ZipFile(empty);
        } catch (ZipException e) {
            p(name(e));
        }
        empty.delete();
        try {
            new ZipFile(new File(empty.getPath() + "-missing"));
        } catch (IOException e) {
            // OpenJDK 17 throws NoSuchFileException, Android FileNotFoundException
            p("missing file IOException");
        }
    }

    static void entries() {
        p("-- entries");
        ZipEntry e = new ZipEntry("a/b.txt");
        p(e + " " + e.getSize() + " " + e.getCompressedSize() + " " + e.getCrc() + " " + e.getMethod() + " "
                + e.getTime() + " " + e.getLastModifiedTime() + " " + e.isDirectory() + " " + e.hashCode());
        e.setTime(0);
        p("time 0 -> " + e.getTime());
        e.setTime(315532800000L + 3001);
        p("1980 -> " + e.getTime() + " " + e.getLastModifiedTime());
        e.setLastModifiedTime(FileTime.from(1700000000L, TimeUnit.SECONDS));
        p("ft -> " + e.getTime() + " " + e.getLastModifiedTime());
        ZipEntry c = (ZipEntry) e.clone();
        ZipEntry d = new ZipEntry(e);
        p(c.getName() + " " + d.getTime());
        try {
            e.setSize(-1);
        } catch (IllegalArgumentException ex) {
            p(ex.getMessage());
        }
        try {
            e.setCrc(1L << 32);
        } catch (IllegalArgumentException ex) {
            p(ex.getMessage());
        }
        try {
            e.setMethod(3);
        } catch (IllegalArgumentException ex) {
            p(ex.getMessage());
        }
        try {
            new ZipEntry((String) null);
        } catch (NullPointerException ex) {
            p("null name " + name(ex));
        }
        p(FileTime.fromMillis(1234567).toString() + " " + FileTime.from(5, TimeUnit.DAYS) + " "
                + FileTime.fromMillis(1000).equals(FileTime.from(1, TimeUnit.SECONDS)) + " "
                + FileTime.fromMillis(1).compareTo(FileTime.fromMillis(2)));
    }

    static void jars() throws Exception {
        p("-- jars");
        Manifest m = new Manifest();
        Attributes main = m.getMainAttributes();
        main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        main.putValue("Created-By", "switchapk");
        StringBuilder longValue = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            longValue.append("seg").append(i).append(' ');
        }
        main.putValue("Long-Value", longValue.toString().trim());
        main.putValue("Unicode", "h\u00e9llo w\u00f6rld \u4e2d\u6587");
        Attributes sec = new Attributes();
        sec.putValue("Sealed", "true");
        m.getEntries().put("com/example/", sec);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        m.write(bo);
        String text = new String(bo.toByteArray(), StandardCharsets.UTF_8);
        p(text.replace("\r\n", "|"));
        Manifest back = new Manifest(new ByteArrayInputStream(bo.toByteArray()));
        p("equal " + back.equals(m) + " long " + back.getMainAttributes().getValue("long-value").length()
                + " unicode " + back.getMainAttributes().getValue("UNICODE").equals(main.getValue("Unicode"))
                + " sealed " + back.getAttributes("com/example/").getValue(Attributes.Name.SEALED));
        Manifest lf = new Manifest(new ByteArrayInputStream(
                "Manifest-Version: 1.0\nMain-Class: a.B\n\nName: x\nK: v\n  more\n\n".getBytes(StandardCharsets.UTF_8)));
        p("lf main " + lf.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS) + " x [" + lf.getAttributes("x").getValue("K") + "] " + lf.getAttributes("x").size());
        try {
            new Manifest(new ByteArrayInputStream("Manifest-Version 1.0\n\n".getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            p("bad manifest " + name(e));
        }
        try {
            new Attributes.Name("bad name");
        } catch (IllegalArgumentException e) {
            p("bad name " + e.getMessage());
        }
        p("name eq " + new Attributes.Name("main-class").equals(Attributes.Name.MAIN_CLASS) + " "
                + (new Attributes.Name("MAIN-CLASS").hashCode() == Attributes.Name.MAIN_CLASS.hashCode()));
        Manifest noVersion = new Manifest();
        noVersion.getMainAttributes().putValue("Foo", "bar");
        bo.reset();
        noVersion.write(bo);
        p("no version bytes " + bo.size());

        bo.reset();
        JarOutputStream jos = new JarOutputStream(bo, m);
        jos.putNextEntry(new JarEntry("com/example/A.class"));
        jos.write(new byte[] {(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe});
        jos.putNextEntry(new ZipEntry("res/data.txt"));
        jos.write("data".getBytes(StandardCharsets.US_ASCII));
        jos.close();
        byte[] jar = bo.toByteArray();

        JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jar));
        p("jis manifest " + jis.getManifest().getMainAttributes().getValue("Created-By") + " read " + jis.read());
        JarEntry je;
        while ((je = jis.getNextJarEntry()) != null) {
            p("jis " + je.getName() + " attrs " + je.getAttributes() + " bytes " + readAll(jis).length);
        }
        jis.close();

        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(jar));
        ZipEntry ze = zis.getNextEntry();
        p("first " + ze.getName() + " extra " + Arrays.toString(ze.getExtra()));
        zis.close();

        File f = new File(System.getProperty("java.io.tmpdir"), "jartest-" + System.nanoTime() + ".jar");
        try (FileOutputStream fo = new FileOutputStream(f)) {
            fo.write(jar);
        }
        JarFile jf = new JarFile(f);
        p("jarfile " + jf.getManifest().getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION) + " multi "
                + jf.isMultiRelease() + " size " + jf.size());
        Enumeration<JarEntry> en = jf.entries();
        while (en.hasMoreElements()) {
            JarEntry e = en.nextElement();
            p("jf " + e.getName() + " " + e.getRealName() + " attrs " + e.getAttributes());
        }
        JarEntry a = jf.getJarEntry("com/example/");
        p("dir " + a);
        p("data " + new String(readAll(jf.getInputStream(jf.getEntry("res/data.txt"))), StandardCharsets.US_ASCII));
        p("stream " + jf.stream().count());
        jf.close();
        f.delete();
    }
}
