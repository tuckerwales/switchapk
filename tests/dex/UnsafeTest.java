// sun.misc.Unsafe as desugared java.util.concurrent uses it: field and array offsets, CAS, get/put, getAndAdd.
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import sun.misc.Unsafe;

public class UnsafeTest {
    static class Box {
        int i = 1;
        long j = 2;
        Object o = "a";
        boolean z;
        byte b;
        short s;
        char c = 'x';
        float f = 1.5f;
        double d = 2.5;
        Box() { i = 99; }
    }

    static void p(Object o) { System.out.println(o); }

    public static void main(String[] args) throws Exception {
        Field tf = Unsafe.class.getDeclaredField("theUnsafe");
        tf.setAccessible(true);
        Unsafe u = (Unsafe) tf.get(null);
        Box x = new Box();
        long oi = u.objectFieldOffset(Box.class.getDeclaredField("i"));
        long oj = u.objectFieldOffset(Box.class.getDeclaredField("j"));
        long oo = u.objectFieldOffset(Box.class.getDeclaredField("o"));
        long oz = u.objectFieldOffset(Box.class.getDeclaredField("z"));
        long ob = u.objectFieldOffset(Box.class.getDeclaredField("b"));
        long os = u.objectFieldOffset(Box.class.getDeclaredField("s"));
        long oc = u.objectFieldOffset(Box.class.getDeclaredField("c"));
        long of = u.objectFieldOffset(Box.class.getDeclaredField("f"));
        long od = u.objectFieldOffset(Box.class.getDeclaredField("d"));
        p("distinct " + (oi != oj && oj != oo && oi != oo));
        p(u.getInt(x, oi) + " " + u.getLong(x, oj) + " " + u.getObject(x, oo) + " " + u.getChar(x, oc) + " "
                + u.getFloat(x, of) + " " + u.getDouble(x, od));
        p(u.compareAndSwapInt(x, oi, 99, 5) + " " + u.compareAndSwapInt(x, oi, 99, 6) + " " + x.i);
        p(u.compareAndSwapLong(x, oj, 2L, 1L << 40) + " " + u.compareAndSwapLong(x, oj, 2L, 3L) + " " + x.j);
        p(u.compareAndSwapObject(x, oo, "a", "b") + " " + u.compareAndSwapObject(x, oo, "a", "c") + " " + x.o);
        u.putIntVolatile(x, oi, 7);
        u.putOrderedLong(x, oj, -1L);
        u.putObjectVolatile(x, oo, null);
        u.putBoolean(x, oz, true);
        u.putByte(x, ob, (byte) -3);
        u.putShort(x, os, (short) 300);
        u.putChar(x, oc, 'y');
        u.putFloat(x, of, -0.25f);
        u.putDouble(x, od, 1e100);
        p(x.i + " " + x.j + " " + x.o + " " + x.z + " " + x.b + " " + x.s + " " + x.c + " " + x.f + " " + x.d);
        p(u.getAndAddInt(x, oi, 3) + " " + x.i + " " + u.getAndSetLong(x, oj, 9) + " " + x.j + " "
                + u.getAndSetObject(x, oo, "z") + " " + x.o + " " + u.getAndAddLong(x, oj, 1) + " " + x.j);

        Object[] objs = new Object[4];
        int ob0 = u.arrayBaseOffset(Object[].class);
        int os0 = u.arrayIndexScale(Object[].class);
        p("ref scale ok " + (os0 == 4 || os0 == 8));
        u.putObjectVolatile(objs, ob0 + 2L * os0, "two");
        p(objs[2] + " " + u.getObjectVolatile(objs, ob0 + 2L * os0) + " "
                + u.compareAndSwapObject(objs, ob0 + 3L * os0, null, "three") + " " + objs[3]);
        int[] ints = {10, 20, 30};
        int ib = u.arrayBaseOffset(int[].class);
        int is = u.arrayIndexScale(int[].class);
        p(is + " " + u.getInt(ints, ib + 1L * is) + " " + u.compareAndSwapInt(ints, ib + 2L * is, 30, 31) + " " + ints[2]);
        long[] longs = {5};
        p(u.arrayIndexScale(long[].class) + " " + u.getAndAddLong(longs, u.arrayBaseOffset(long[].class), 5) + " " + longs[0]);
        p(u.arrayIndexScale(byte[].class) + " " + u.arrayIndexScale(char[].class) + " " + u.arrayIndexScale(double[].class));

        Box raw = (Box) u.allocateInstance(Box.class);
        p("allocateInstance " + raw.i + " " + raw.o + " " + (int) raw.c);

        // contention through getAndAddInt
        final Unsafe fu = u;
        final int[] counter = new int[1];
        final long cb = u.arrayBaseOffset(int[].class);
        Thread[] ts = new Thread[4];
        for (int k = 0; k < ts.length; k++) {
            ts[k] = new Thread(() -> { for (int n = 0; n < 5000; n++) fu.getAndAddInt(counter, cb, 1); });
            ts[k].start();
        }
        for (Thread t : ts) t.join();
        p("counter " + counter[0]);

        // park/unpark round trip
        final Thread main = Thread.currentThread();
        final AtomicInteger flag = new AtomicInteger();
        Thread w = new Thread(() -> { flag.set(1); fu.unpark(main); });
        w.start();
        while (flag.get() == 0) u.park(false, 50000000L);
        w.join();
        p("parked " + flag.get());
        u.park(true, System.currentTimeMillis() + 10);
        p("done");
    }
}
