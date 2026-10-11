import java.lang.reflect.Field;
import sun.misc.Unsafe;

/**
 * sun.misc.Unsafe as libraries use it (desugared j$ ConcurrentHashMap, atomics libraries): run on OpenJDK and on
 * switchapk by tests/run_dex_test.sh, output must match. Offsets are layout specific, so only behaviour is printed.
 */
public class UnsafeTest {
    static class Box {
        int count = 3;
        long total = 10L;
        volatile Object ref = "a";
        boolean flag;
        byte b = 1;
        short s = 2;
        char c = 'x';
        float f = 1.5f;
        double d = 2.25;
        static int created;

        Box() {
            created++;
        }
    }

    public static void main(String[] args) throws Exception {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Unsafe u = (Unsafe) theUnsafe.get(null);
        System.out.println("unsafe " + (u != null));

        long count = u.objectFieldOffset(Box.class.getDeclaredField("count"));
        long total = u.objectFieldOffset(Box.class.getDeclaredField("total"));
        long ref = u.objectFieldOffset(Box.class.getDeclaredField("ref"));
        long flag = u.objectFieldOffset(Box.class.getDeclaredField("flag"));
        long bo = u.objectFieldOffset(Box.class.getDeclaredField("b"));
        long so = u.objectFieldOffset(Box.class.getDeclaredField("s"));
        long co = u.objectFieldOffset(Box.class.getDeclaredField("c"));
        long fo = u.objectFieldOffset(Box.class.getDeclaredField("f"));
        long dO = u.objectFieldOffset(Box.class.getDeclaredField("d"));
        System.out.println("distinct " + (count != total && total != ref && ref != count));

        Box box = new Box();
        System.out.println("getInt " + u.getInt(box, count) + " getLong " + u.getLong(box, total) + " getObject "
                + u.getObject(box, ref));
        System.out.println("cas int " + u.compareAndSwapInt(box, count, 3, 4) + " " + u.compareAndSwapInt(box, count, 3, 5)
                + " -> " + box.count);
        System.out.println("cas long " + u.compareAndSwapLong(box, total, 10L, 1L << 40) + " -> " + box.total);
        System.out.println("cas obj " + u.compareAndSwapObject(box, ref, "a", "b") + " "
                + u.compareAndSwapObject(box, ref, "a", "c") + " -> " + box.ref);
        u.putIntVolatile(box, count, 42);
        u.putOrderedLong(box, total, -7L);
        u.putObjectVolatile(box, ref, null);
        System.out.println("put " + box.count + " " + box.total + " " + box.ref + " " + u.getIntVolatile(box, count));
        System.out.println("getAndAdd " + u.getAndAddInt(box, count, 8) + " " + box.count + " "
                + u.getAndAddLong(box, total, 7) + " " + box.total + " " + u.getAndSetObject(box, ref, "z") + " "
                + box.ref);
        u.putBoolean(box, flag, true);
        u.putByte(box, bo, (byte) -3);
        u.putShort(box, so, (short) 300);
        u.putChar(box, co, 'q');
        u.putFloat(box, fo, 0.25f);
        u.putDouble(box, dO, -1.5);
        System.out.println("small " + box.flag + " " + box.b + " " + box.s + " " + box.c + " " + box.f + " " + box.d + " "
                + u.getBoolean(box, flag) + " " + u.getByte(box, bo) + " " + u.getShort(box, so) + " "
                + u.getChar(box, co) + " " + u.getFloat(box, fo) + " " + u.getDouble(box, dO));

        int[] ints = {1, 2, 3, 4};
        long ib = u.arrayBaseOffset(int[].class);
        int is = u.arrayIndexScale(int[].class);
        System.out.println("int scale " + is + " long scale " + u.arrayIndexScale(long[].class) + " byte scale "
                + u.arrayIndexScale(byte[].class));
        System.out.println("array " + u.getInt(ints, ib + 2L * is) + " " + u.compareAndSwapInt(ints, ib + 3L * is, 4, 40)
                + " " + ints[3]);
        Object[] objs = {"p", "q"};
        long ob = u.arrayBaseOffset(Object[].class);
        int os = u.arrayIndexScale(Object[].class);
        System.out.println("objs " + u.getObjectVolatile(objs, ob + os) + " "
                + u.compareAndSwapObject(objs, ob, "p", "r") + " " + objs[0]);
        long[] longs = new long[2];
        long lb = u.arrayBaseOffset(long[].class);
        u.putLongVolatile(longs, lb + 8, 99L);
        System.out.println("longs " + longs[1] + " " + u.getAndAddLong(longs, lb + 8, 1) + " " + longs[1]);

        Box raw = (Box) u.allocateInstance(Box.class);
        System.out.println("allocate " + raw.count + " " + raw.ref + " created " + Box.created);
        try {
            u.allocateInstance(Runnable.class);
        } catch (InstantiationException e) {
            System.out.println("interface " + e.getClass().getName());
        }

        long mem = u.allocateMemory(32);
        u.setMemory(mem, 32, (byte) 0);
        u.putLong(mem, 0x1122334455667788L);
        u.putInt(mem + 8, -5);
        u.putByte(mem + 12, (byte) 7);
        u.putDouble(mem + 16, 3.5);
        u.copyMemory(mem, mem + 24, 8);
        System.out.println("memory " + Long.toHexString(u.getLong(mem)) + " " + u.getInt(mem + 8) + " " + u.getByte(mem + 12)
                + " " + u.getDouble(mem + 16) + " " + Long.toHexString(u.getLong(mem + 24)));
        u.freeMemory(mem);
        System.out.println("address " + u.addressSize());
        try {
            u.objectFieldOffset(Box.class.getDeclaredField("created"));
            System.out.println("static allowed");
        } catch (IllegalArgumentException e) {
            System.out.println("static " + e.getClass().getName());
        }
    }
}
