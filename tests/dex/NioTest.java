import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * java.nio.channels.FileChannel, FileLock and MappedByteBuffer: run on OpenJDK and on switchapk by
 * tests/run_dex_test.sh, output must match.
 */
public class NioTest {
    static void p(Object o) {
        System.out.println(o);
    }

    static String name(Throwable t) {
        return t.getClass().getName();
    }

    static String str(ByteBuffer b) {
        byte[] a = new byte[b.remaining()];
        b.duplicate().get(a);
        return new String(a, StandardCharsets.US_ASCII);
    }

    static String read(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("US-ASCII");
        }
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "niotest-" + System.nanoTime());
        dir.mkdirs();
        File f = new File(dir, "a.bin");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write("hello mapped world".getBytes(StandardCharsets.US_ASCII));
        }

        // Channel basics through the streams.
        try (FileInputStream in = new FileInputStream(f)) {
            FileChannel ch = in.getChannel();
            p("same channel " + (ch == in.getChannel()));
            p("size " + ch.size() + " pos " + ch.position());
            ByteBuffer b = ByteBuffer.allocate(5);
            p("read " + ch.read(b) + " pos " + ch.position());
            b.flip();
            p(str(b));
            ByteBuffer at = ByteBuffer.allocate(6);
            p("pread " + ch.read(at, 12) + " pos " + ch.position());
            at.flip();
            p(str(at));
            ByteBuffer[] parts = {ByteBuffer.allocate(3), ByteBuffer.allocate(4)};
            p("scatter " + ch.read(parts));
            p(str((ByteBuffer) parts[0].flip()) + "|" + str((ByteBuffer) parts[1].flip()));
            try {
                ch.write(ByteBuffer.wrap(new byte[1]));
            } catch (Exception e) {
                p("write on read-only: " + name(e));
            }
            try {
                ch.map(FileChannel.MapMode.READ_WRITE, 0, 4);
            } catch (Exception e) {
                p("map rw on read-only: " + name(e));
            }
            try {
                ch.lock();
            } catch (Exception e) {
                p("exclusive lock on read-only: " + name(e));
            }
            FileLock shared = ch.lock(0, Long.MAX_VALUE, true);
            p("shared " + shared.isShared() + " valid " + shared.isValid() + " pos " + shared.position());
            shared.release();
            p("released valid " + shared.isValid());
            MappedByteBuffer m = ch.map(FileChannel.MapMode.READ_ONLY, 6, 6);
            p("mapped " + str(m) + " direct " + m.isDirect() + " readonly " + m.isReadOnly() + " loaded "
                    + m.isLoaded() + " cap " + m.capacity());
            try {
                m.put(0, (byte) 'x');
            } catch (Exception e) {
                p("put on read-only map: " + name(e));
            }
            MappedByteBuffer s = m.slice();
            p("slice mapped " + (s instanceof MappedByteBuffer) + " " + str(s));
            m.position(2);
            p("pos " + m.position() + " rem " + m.remaining());
            in.close();
            p("closed with stream " + !ch.isOpen());
            try {
                ch.size();
            } catch (Exception e) {
                p("after close: " + name(e));
            }
        }

        // RandomAccessFile: read-write map, locks, truncate.
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            FileChannel ch = raf.getChannel();
            FileLock l = ch.lock();
            p("lock " + l.isShared() + " " + l.isValid() + " size " + (l.size() == Long.MAX_VALUE));
            try {
                ch.tryLock();
            } catch (OverlappingFileLockException e) {
                p("second lock: " + name(e));
            }
            FileLock l2 = null;
            l.release();
            l2 = ch.tryLock(0, 4, false);
            FileLock l3 = ch.tryLock(10, 4, false);
            p("disjoint " + (l2 != null) + " " + (l3 != null) + " overlaps " + l2.overlaps(2, 4) + " " + l2.overlaps(4, 4));
            l2.close();
            l3.release();

            MappedByteBuffer m = ch.map(FileChannel.MapMode.READ_WRITE, 0, ch.size());
            m.put(0, (byte) 'H');
            m.put(6, (byte) 'M');
            m.force();
            p("after force " + read(f));
            MappedByteBuffer grow = ch.map(FileChannel.MapMode.READ_WRITE, 16, 8);
            p("grown size " + ch.size());
            grow.put(new byte[] {'l', 'd', '!', '!', '!', '!', '!', '!'});
            grow.force();
            p("after grow " + read(f));
            MappedByteBuffer priv = ch.map(FileChannel.MapMode.PRIVATE, 0, 5);
            priv.put(0, (byte) 'J');
            p("private " + str(priv) + " file " + read(f).substring(0, 5));
            ch.truncate(18);
            p("truncated " + ch.size() + " " + read(f));
            ch.position(5);
            p("write " + ch.write(ByteBuffer.wrap(new byte[] {'_'})) + " pos " + ch.position());
            p("pwrite " + ch.write(ByteBuffer.wrap(new byte[] {'?'}), 17) + " pos " + ch.position());
            p("file " + read(f));
            try {
                ch.map(FileChannel.MapMode.READ_ONLY, -1, 3);
            } catch (Exception e) {
                p("negative position: " + name(e));
            }
        }

        // FileOutputStream append and transfer.
        try (FileOutputStream out = new FileOutputStream(f, true)) {
            FileChannel ch = out.getChannel();
            p("append pos " + ch.position());
            ch.write(ByteBuffer.wrap("++".getBytes(StandardCharsets.US_ASCII)));
            p("append size " + ch.size());
            try {
                ch.read(ByteBuffer.allocate(1));
            } catch (Exception e) {
                p("read on write-only: " + name(e));
            }
        }
        File g = new File(dir, "b.bin");
        try (FileInputStream in = new FileInputStream(f); FileOutputStream out = new FileOutputStream(g)) {
            p("transferTo " + in.getChannel().transferTo(2, 5, out.getChannel()));
        }
        p("copied " + read(g));
        try (RandomAccessFile raf = new RandomAccessFile(g, "rw"); FileInputStream in = new FileInputStream(f)) {
            p("transferFrom " + raf.getChannel().transferFrom(in.getChannel(), 5, 3));
        }
        p("copied " + read(g));

        // FileChannel.open.
        Path path = Paths.get(new File(dir, "c.bin").getPath());
        try {
            FileChannel.open(path, StandardOpenOption.READ);
        } catch (Exception e) {
            p("open missing: " + name(e));
        }
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            ch.write(ByteBuffer.wrap("abc".getBytes(StandardCharsets.US_ASCII)));
            p("opened size " + ch.size());
        }
        try {
            FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (Exception e) {
            p("create new existing: " + name(e));
        }
        try (FileChannel ch = FileChannel.open(path)) {
            ByteBuffer b = ByteBuffer.allocate(8);
            p("read " + ch.read(b) + " then " + ch.read(b));
            p("map " + str(ch.map(FileChannel.MapMode.READ_ONLY, 0, 3)));
            ch.close();
            p("open " + ch.isOpen());
        }
        for (File x : dir.listFiles()) {
            x.delete();
        }
        dir.delete();
    }
}
