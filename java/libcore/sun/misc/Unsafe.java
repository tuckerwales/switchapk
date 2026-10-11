package sun.misc;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.locks.LockSupport;

/**
 * ART's sun.misc.Unsafe (hidden on Android, used by desugared java.util.concurrent and by
 * libraries that look it up through the theUnsafe field). Field and array element access by
 * offset and compare-and-swap are natives in src/native/java_unsafe.c; each runs holding the
 * GIL, so it is atomic. The volatile and ordered variants are the plain accesses (the GIL
 * orders them). Raw memory (allocateMemory, address-based get/put) is not provided.
 */
public final class Unsafe {
    private static final Unsafe THE_ONE = new Unsafe();
    private static final Unsafe theUnsafe = THE_ONE;

    /** Must match UNSAFE_ARRAY_BASE in java_unsafe.c. */
    private static final int ARRAY_BASE = 16;

    public static final int INVALID_FIELD_OFFSET = -1;

    private Unsafe() {}

    public static Unsafe getUnsafe() { return THE_ONE; }

    public long objectFieldOffset(Field field) {
        if (Modifier.isStatic(field.getModifiers())) {
            throw new IllegalArgumentException("valid for instance fields only");
        }
        return objectFieldOffset0(field);
    }

    private static native long objectFieldOffset0(Field field);

    public int arrayBaseOffset(Class<?> clazz) {
        if (!clazz.isArray()) throw new IllegalArgumentException("valid for array classes only");
        return ARRAY_BASE;
    }

    public int arrayIndexScale(Class<?> clazz) {
        if (!clazz.isArray()) throw new IllegalArgumentException("valid for array classes only");
        return arrayIndexScale0(clazz);
    }

    private static native int arrayIndexScale0(Class<?> clazz);

    public int addressSize() { return 8; }

    public int pageSize() { return 4096; }

    public native boolean compareAndSwapInt(Object obj, long offset, int expectedValue, int newValue);
    public native boolean compareAndSwapLong(Object obj, long offset, long expectedValue, long newValue);
    public native boolean compareAndSwapObject(Object obj, long offset, Object expectedValue, Object newValue);

    public native int getInt(Object obj, long offset);
    public native void putInt(Object obj, long offset, int newValue);
    public native long getLong(Object obj, long offset);
    public native void putLong(Object obj, long offset, long newValue);
    public native Object getObject(Object obj, long offset);
    public native void putObject(Object obj, long offset, Object newValue);
    public native boolean getBoolean(Object obj, long offset);
    public native void putBoolean(Object obj, long offset, boolean newValue);
    public native byte getByte(Object obj, long offset);
    public native void putByte(Object obj, long offset, byte newValue);
    public native char getChar(Object obj, long offset);
    public native void putChar(Object obj, long offset, char newValue);
    public native short getShort(Object obj, long offset);
    public native void putShort(Object obj, long offset, short newValue);

    public float getFloat(Object obj, long offset) { return Float.intBitsToFloat(getInt(obj, offset)); }
    public void putFloat(Object obj, long offset, float newValue) { putInt(obj, offset, Float.floatToRawIntBits(newValue)); }
    public double getDouble(Object obj, long offset) { return Double.longBitsToDouble(getLong(obj, offset)); }
    public void putDouble(Object obj, long offset, double newValue) {
        putLong(obj, offset, Double.doubleToRawLongBits(newValue));
    }

    public int getIntVolatile(Object obj, long offset) { return getInt(obj, offset); }
    public void putIntVolatile(Object obj, long offset, int newValue) { putInt(obj, offset, newValue); }
    public long getLongVolatile(Object obj, long offset) { return getLong(obj, offset); }
    public void putLongVolatile(Object obj, long offset, long newValue) { putLong(obj, offset, newValue); }
    public Object getObjectVolatile(Object obj, long offset) { return getObject(obj, offset); }
    public void putObjectVolatile(Object obj, long offset, Object newValue) { putObject(obj, offset, newValue); }
    public boolean getBooleanVolatile(Object obj, long offset) { return getBoolean(obj, offset); }
    public void putBooleanVolatile(Object obj, long offset, boolean newValue) { putBoolean(obj, offset, newValue); }
    public byte getByteVolatile(Object obj, long offset) { return getByte(obj, offset); }
    public void putByteVolatile(Object obj, long offset, byte newValue) { putByte(obj, offset, newValue); }
    public char getCharVolatile(Object obj, long offset) { return getChar(obj, offset); }
    public void putCharVolatile(Object obj, long offset, char newValue) { putChar(obj, offset, newValue); }
    public short getShortVolatile(Object obj, long offset) { return getShort(obj, offset); }
    public void putShortVolatile(Object obj, long offset, short newValue) { putShort(obj, offset, newValue); }
    public float getFloatVolatile(Object obj, long offset) { return getFloat(obj, offset); }
    public void putFloatVolatile(Object obj, long offset, float newValue) { putFloat(obj, offset, newValue); }
    public double getDoubleVolatile(Object obj, long offset) { return getDouble(obj, offset); }
    public void putDoubleVolatile(Object obj, long offset, double newValue) { putDouble(obj, offset, newValue); }

    public void putOrderedInt(Object obj, long offset, int newValue) { putInt(obj, offset, newValue); }
    public void putOrderedLong(Object obj, long offset, long newValue) { putLong(obj, offset, newValue); }
    public void putOrderedObject(Object obj, long offset, Object newValue) { putObject(obj, offset, newValue); }

    public final int getAndAddInt(Object o, long offset, int delta) {
        int v;
        do {
            v = getIntVolatile(o, offset);
        } while (!compareAndSwapInt(o, offset, v, v + delta));
        return v;
    }

    public final long getAndAddLong(Object o, long offset, long delta) {
        long v;
        do {
            v = getLongVolatile(o, offset);
        } while (!compareAndSwapLong(o, offset, v, v + delta));
        return v;
    }

    public final int getAndSetInt(Object o, long offset, int newValue) {
        int v;
        do {
            v = getIntVolatile(o, offset);
        } while (!compareAndSwapInt(o, offset, v, newValue));
        return v;
    }

    public final long getAndSetLong(Object o, long offset, long newValue) {
        long v;
        do {
            v = getLongVolatile(o, offset);
        } while (!compareAndSwapLong(o, offset, v, newValue));
        return v;
    }

    public final Object getAndSetObject(Object o, long offset, Object newValue) {
        Object v;
        do {
            v = getObjectVolatile(o, offset);
        } while (!compareAndSwapObject(o, offset, v, newValue));
        return v;
    }

    public void loadFence() {}
    public void storeFence() {}
    public void fullFence() {}

    /** absolute: time is a wall-clock deadline in ms; otherwise a relative wait in ns (0 = until unparked). */
    public void park(boolean absolute, long time) {
        if (absolute) {
            long ms = time - System.currentTimeMillis();
            if (ms > 0) LockSupport.parkNanos(ms * 1000000L);
        } else if (time == 0) {
            LockSupport.park();
        } else if (time > 0) {
            LockSupport.parkNanos(time);
        }
    }

    public void unpark(Object obj) {
        if (obj instanceof Thread) LockSupport.unpark((Thread) obj);
    }

    public native Object allocateInstance(Class<?> c) throws InstantiationException;
}
