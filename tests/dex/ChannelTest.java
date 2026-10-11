// FileChannel locks and maps, through FileInputStream/FileOutputStream/RandomAccessFile channels.
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.OverlappingFileLockException;

public class ChannelTest {
    public static void main(String[] args) throws Exception {
        File f = new File(System.getProperty("java.io.tmpdir"), "channeltest.bin");
        f.delete();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write("hello mapped world".getBytes("UTF-8"));
            System.out.println("same channel " + (out.getChannel() == out.getChannel()));
            FileLock lock = out.getChannel().lock();
            System.out.println("lock " + lock.isValid() + " shared=" + lock.isShared() + " pos=" + lock.position()
                    + " size=" + (lock.size() == Long.MAX_VALUE) + " channel " + (lock.channel() == out.getChannel()));
            try {
                out.getChannel().tryLock();
            } catch (OverlappingFileLockException e) {
                System.out.println("overlap rejected");
            }
            lock.release();
            System.out.println("released " + lock.isValid());
            FileLock a = out.getChannel().tryLock(0, 4, false);
            FileLock b = out.getChannel().tryLock(4, 4, false);
            System.out.println("disjoint " + (a != null) + " " + (b != null) + " overlaps " + a.overlaps(3, 2) + " "
                    + a.overlaps(4, 2));
            a.close();
            b.release();
            FileLock c = out.getChannel().tryLock(0, 2, false);
            out.close();
            System.out.println("closed channel releases " + c.isValid());
        }

        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            FileChannel ch = raf.getChannel();
            MappedByteBuffer ro = ch.map(FileChannel.MapMode.READ_ONLY, 6, 6);
            byte[] six = new byte[6];
            ro.get(six);
            System.out.println("ro [" + new String(six, "UTF-8") + "] readOnly=" + ro.isReadOnly() + " loaded="
                    + ro.isLoaded() + " cap=" + ro.capacity() + " mode " + FileChannel.MapMode.READ_ONLY);
            MappedByteBuffer rw = ch.map(FileChannel.MapMode.READ_WRITE, 0, 5);
            rw.put(0, (byte) 'J');
            rw.force();
            MappedByteBuffer pv = ch.map(FileChannel.MapMode.PRIVATE, 13, 5);
            pv.put(0, (byte) 'W');
            pv.force();
            MappedByteBuffer grow = ch.map(FileChannel.MapMode.READ_WRITE, 18, 4);
            grow.put("!!!!".getBytes("UTF-8"));
            grow.force();
            MappedByteBuffer s = rw.slice(1, 2);
            System.out.println("slice " + (char) s.get(0) + (char) s.get(1) + " cap=" + s.capacity());
            System.out.println("size after grow " + ch.size() + " position " + ch.position());
        }
        byte[] all = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int n = in.read(all);
            System.out.println("file [" + new String(all, 0, n, "UTF-8") + "]");
            try {
                in.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 1);
            } catch (NonWritableChannelException e) {
                System.out.println("read-only channel cannot map READ_WRITE");
            }
            try {
                in.getChannel().lock();
            } catch (NonWritableChannelException e) {
                System.out.println("read-only channel cannot lock exclusively");
            }
            System.out.println("shared lock " + in.getChannel().lock(0, 1, true).isShared());
        }
        f.delete();
    }
}
