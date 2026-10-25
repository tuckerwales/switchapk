import java.nio.*;
import java.util.*;

/**
 * java.nio buffers: multi-byte ByteBuffer access, typed views in both byte orders, bulk get/put, compact,
 * slices and the exceptions. Run on OpenJDK and on switchapk by tests/run_dex_test.sh, output must match.
 */
public class BufferTest {
    static void p(Object o) {
        System.out.println(o);
    }

    static String hex(byte[] a) {
        StringBuilder sb = new StringBuilder();
        for (byte b : a) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    static String state(Buffer b) {
        return "pos " + b.position() + " lim " + b.limit() + " cap " + b.capacity();
    }

    interface Op {
        void run() throws Exception;
    }

    static void expect(String what, Op op) {
        try {
            op.run();
            p(what + ": ok");
        } catch (Exception e) {
            p(what + ": " + e.getClass().getName());
        }
    }

    static void scalars(ByteOrder order) {
        p("== scalars " + order);
        byte[] backing = new byte[40];
        ByteBuffer whole = ByteBuffer.wrap(backing);
        whole.position(3);
        ByteBuffer b = whole.slice().order(order);
        b.putChar('€').putShort((short) -2).putInt(0x12345678).putLong(0x0123456789abcdefL);
        b.putFloat(Float.intBitsToFloat(0x7fc00001)).putDouble(-1.5e-300);
        p(state(b) + " " + hex(backing));
        b.flip();
        p((int) b.getChar() + " " + b.getShort() + " " + Integer.toHexString(b.getInt()) + " "
                + Long.toHexString(b.getLong()) + " " + Integer.toHexString(Float.floatToRawIntBits(b.getFloat()))
                + " " + b.getDouble());
        p("abs " + (int) b.getChar(0) + " " + b.getShort(2) + " " + Integer.toHexString(b.getInt(4)) + " "
                + Long.toHexString(b.getLong(8)) + " " + b.getFloat(16) + " " + b.getDouble(20));
        b.putInt(1, -1).putLong(9, 7).putShort(18, (short) 0x1234).putChar(0, 'A').putFloat(24, 2.5f).putDouble(20, 3.0);
        p(hex(backing));
        expect("getInt past limit", () -> b.getInt(b.limit() - 3));
        expect("getLong negative", () -> b.getLong(-1));
        expect("putDouble underflow", () -> {
            b.position(b.limit() - 7);
            b.putDouble(1);
        });
        expect("getShort underflow", () -> {
            b.position(b.limit() - 1);
            b.getShort();
        });
        ByteBuffer ro = b.asReadOnlyBuffer().order(order);
        expect("read-only putInt", () -> ro.putInt(0, 1));
        expect("read-only putLong relative", () -> ro.putLong(1));
        p("read-only getInt " + Integer.toHexString(ro.getInt(0)));
    }

    static void views(ByteOrder order) {
        p("== views " + order);
        byte[] backing = new byte[64];
        ByteBuffer bb = ByteBuffer.wrap(backing, 0, 64);
        bb.position(5);
        bb.order(order);

        FloatBuffer fb = bb.asFloatBuffer();
        p("float " + state(fb) + " order " + fb.order());
        fb.put(1.5f).put(new float[] {2f, -3f, Float.NaN}, 1, 2);
        fb.put(4, 0.25f);
        p(hex(backing));
        float[] fo = new float[6];
        fb.flip();
        fb.get(fo, 1, 3);
        p(Arrays.toString(fo) + " " + state(fb) + " get(2)=" + fb.get(2));
        Arrays.fill(backing, (byte) 0);

        IntBuffer ib = bb.asIntBuffer();
        ib.put(new int[] {1, -2, 0x01020304, Integer.MIN_VALUE});
        p("int " + hex(backing));
        IntBuffer heap = IntBuffer.wrap(new int[] {9, 8, 7});
        ib.put(heap);
        p("int put(heap) " + hex(backing) + " " + state(ib) + " heap " + state(heap));
        ib.position(1);
        ib.compact();
        p("int compact " + hex(backing) + " " + state(ib));
        int[] io = new int[7];
        ib.clear();
        ib.get(io);
        p(Arrays.toString(io));
        IntBuffer islice = ((IntBuffer) ib.position(2)).slice();
        islice.put(0, 55);
        p("int slice " + state(islice) + " " + islice.get(0) + " " + ib.get(2) + " " + hex(backing));
        Arrays.fill(backing, (byte) 0);

        ShortBuffer sb = bb.asShortBuffer();
        sb.put(new short[] {1, -1, 0x7f80});
        sb.put(5, (short) 0x0102);
        short at5 = sb.get(5);
        short[] so = new short[3];
        ((ShortBuffer) sb.flip()).get(so);
        p("short " + hex(backing) + " " + Arrays.toString(so) + " " + at5);
        Arrays.fill(backing, (byte) 0);

        CharBuffer cb = bb.asCharBuffer();
        cb.put("hé€".toCharArray());
        cb.put(new char[] {'x', 'y', 'z'}, 1, 1);
        char[] co = new char[4];
        ((CharBuffer) cb.flip()).get(co);
        p("char " + hex(backing) + " " + new String(co));
        Arrays.fill(backing, (byte) 0);

        LongBuffer lb = bb.asLongBuffer();
        lb.put(new long[] {-1L, 0x0102030405060708L});
        lb.put(2, Long.MIN_VALUE);
        long[] lo = new long[3];
        lb.clear();
        lb.get(lo);
        p("long " + hex(backing) + " " + Arrays.toString(lo));
        Arrays.fill(backing, (byte) 0);

        DoubleBuffer db = bb.asDoubleBuffer();
        db.put(new double[] {1.0, -0.0, Math.PI});
        DoubleBuffer dup = db.duplicate();
        dup.flip();
        double[] dout = new double[3];
        dup.get(dout);
        p("double " + hex(backing) + " " + Arrays.toString(dout));
        Arrays.fill(backing, (byte) 0);

        // views of the other byte order over the same bytes
        ByteOrder other = order == ByteOrder.BIG_ENDIAN ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        FloatBuffer mine = bb.asFloatBuffer();
        mine.put(new float[] {1f, 2f, 3f});
        mine.flip();
        FloatBuffer theirs = bb.duplicate().order(other).asFloatBuffer();
        theirs.position(4);
        theirs.put(mine);
        p("cross-order put " + hex(backing) + " " + state(mine) + " " + state(theirs));
        theirs.position(4);
        p("read back " + theirs.get() + " " + theirs.get() + " " + theirs.get());
        FloatBuffer heapF = FloatBuffer.allocate(4);
        theirs.position(4);
        theirs.limit(7);
        heapF.put(theirs);
        p("into heap " + Arrays.toString(heapF.array()));

        FloatBuffer rov = bb.asReadOnlyBuffer().asFloatBuffer();
        expect("read-only view bulk put", () -> rov.put(new float[] {1f}));
        expect("read-only view put", () -> rov.put(0, 1f));
        fb.clear();
        float[] small = new float[2];
        expect("get negative offset", () -> fb.get(small, -1, 1));
        expect("get offset+length past array", () -> fb.get(small, 1, 2));
        expect("get more than remaining", () -> {
            fb.position(fb.limit() - 1);
            fb.get(small, 0, 2);
        });
        expect("put more than remaining", () -> {
            fb.position(fb.limit() - 1);
            fb.put(small);
        });
        expect("put array bad range", () -> {
            fb.position(0);
            fb.put(small, 2, 1);
        });
        expect("put buffer into itself", () -> fb.put(fb));
        expect("put buffer overflow", () -> {
            fb.position(fb.limit() - 1);
            fb.put(FloatBuffer.allocate(2));
        });
    }

    static void heapBuffers() {
        p("== heap typed buffers");
        IntBuffer a = IntBuffer.allocate(6);
        a.put(new int[] {1, 2, 3, 4, 5}, 1, 3);
        a.flip();
        IntBuffer b = IntBuffer.allocate(4);
        b.put(a);
        p(Arrays.toString(b.array()) + " " + state(a) + " " + state(b) + " " + b.order());
        IntBuffer w = IntBuffer.wrap(new int[] {10, 11, 12, 13, 14});
        w.position(2);
        IntBuffer s = w.slice();
        s.put(new int[] {20, 21});
        p(Arrays.toString(w.array()) + " " + s.get(0));
        w.position(3);
        w.compact();
        p("compact " + Arrays.toString(w.array()) + " " + state(w));
        DoubleBuffer d = DoubleBuffer.wrap(new double[] {1, 2, 3});
        double[] out = new double[3];
        d.get(out, 0, 3);
        p(Arrays.toString(out));
        CharBuffer c = CharBuffer.wrap("hello");
        char[] cs = new char[3];
        c.position(1);
        c.get(cs);
        p(new String(cs) + " " + c.remaining());
        CharBuffer c2 = CharBuffer.allocate(8);
        c2.put(CharBuffer.wrap("abc"));
        c2.put("de".toCharArray());
        c2.flip();
        p(c2.toString());
    }

    public static void main(String[] args) {
        scalars(ByteOrder.BIG_ENDIAN);
        scalars(ByteOrder.LITTLE_ENDIAN);
        views(ByteOrder.BIG_ENDIAN);
        views(ByteOrder.LITTLE_ENDIAN);
        heapBuffers();
        ByteBuffer direct = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        direct.asFloatBuffer().put(new float[] {1, 2, 3, 4});
        p("direct " + direct.getFloat(12) + " " + direct.isDirect() + " " + direct.asFloatBuffer().isDirect());
    }
}
