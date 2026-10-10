package sun.misc;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.locks.LockSupport;

/**
 * Android's sun.misc.Unsafe (hidden API, but libraries and desugared j$ code reflect on theUnsafe).
 * Field offsets are byte offsets in the object; array offsets start at ARRAY_BASE_OFFSET and index the
 * element storage. Threads run under the VM's global lock, so every access here is atomic.
 */
public final class Unsafe {
    private static final Unsafe THE_ONE = new Unsafe();
    private static final Unsafe theUnsafe = THE_ONE;
    public static final int INVALID_FIELD_OFFSET = -1;
    private static final int ARRAY_BASE_OFFSET = 16;

    private Unsafe() {
    }

    public static Unsafe getUnsafe() {
        return THE_ONE;
    }

    public long objectFieldOffset(Field field) {
        if (Modifier.isStatic(field.getModifiers())) {
            throw new IllegalArgumentException("valid for instance fields only");
        }
        return objectFieldOffset0(field);
    }

    private static native long objectFieldOffset0(Field field);

    public int arrayBaseOffset(Class clazz) {
        Class<?> component = clazz.getComponentType();
        if (component == null) {
            throw new IllegalArgumentException("Valid for array classes only: " + clazz);
        }
        return ARRAY_BASE_OFFSET;
    }

    public int arrayIndexScale(Class clazz) {
        Class<?> component = clazz.getComponentType();
        if (component == null) {
            throw new IllegalArgumentException("Valid for array classes only: " + clazz);
        }
        if (component == boolean.class || component == byte.class) {
            return 1;
        } else if (component == char.class || component == short.class) {
            return 2;
        } else if (component == int.class || component == float.class) {
            return 4;
        } else if (component == long.class || component == double.class) {
            return 8;
        }
        return arrayIndexScale0(clazz);
    }

    private static native int arrayIndexScale0(Class clazz);

    public native boolean compareAndSwapInt(Object obj, long offset, int expectedValue, int newValue);

    public native boolean compareAndSwapLong(Object obj, long offset, long expectedValue, long newValue);

    public native boolean compareAndSwapObject(Object obj, long offset, Object expectedValue, Object newValue);

    public native int getInt(Object obj, long offset);

    public native void putInt(Object obj, long offset, int newValue);

    public int getIntVolatile(Object obj, long offset) {
        return getInt(obj, offset);
    }

    public void putIntVolatile(Object obj, long offset, int newValue) {
        putInt(obj, offset, newValue);
    }

    public native long getLong(Object obj, long offset);

    public native void putLong(Object obj, long offset, long newValue);

    public long getLongVolatile(Object obj, long offset) {
        return getLong(obj, offset);
    }

    public void putLongVolatile(Object obj, long offset, long newValue) {
        putLong(obj, offset, newValue);
    }

    public native Object getObject(Object obj, long offset);

    public native void putObject(Object obj, long offset, Object newValue);

    public Object getObjectVolatile(Object obj, long offset) {
        return getObject(obj, offset);
    }

    public void putObjectVolatile(Object obj, long offset, Object newValue) {
        putObject(obj, offset, newValue);
    }

    public native boolean getBoolean(Object obj, long offset);

    public native void putBoolean(Object obj, long offset, boolean newValue);

    public boolean getBooleanVolatile(Object obj, long offset) {
        return getBoolean(obj, offset);
    }

    public void putBooleanVolatile(Object obj, long offset, boolean newValue) {
        putBoolean(obj, offset, newValue);
    }

    public native byte getByte(Object obj, long offset);

    public native void putByte(Object obj, long offset, byte newValue);

    public byte getByteVolatile(Object obj, long offset) {
        return getByte(obj, offset);
    }

    public void putByteVolatile(Object obj, long offset, byte newValue) {
        putByte(obj, offset, newValue);
    }

    public native short getShort(Object obj, long offset);

    public native void putShort(Object obj, long offset, short newValue);

    public short getShortVolatile(Object obj, long offset) {
        return getShort(obj, offset);
    }

    public void putShortVolatile(Object obj, long offset, short newValue) {
        putShort(obj, offset, newValue);
    }

    public native char getChar(Object obj, long offset);

    public native void putChar(Object obj, long offset, char newValue);

    public char getCharVolatile(Object obj, long offset) {
        return getChar(obj, offset);
    }

    public void putCharVolatile(Object obj, long offset, char newValue) {
        putChar(obj, offset, newValue);
    }

    public native float getFloat(Object obj, long offset);

    public native void putFloat(Object obj, long offset, float newValue);

    public float getFloatVolatile(Object obj, long offset) {
        return getFloat(obj, offset);
    }

    public void putFloatVolatile(Object obj, long offset, float newValue) {
        putFloat(obj, offset, newValue);
    }

    public native double getDouble(Object obj, long offset);

    public native void putDouble(Object obj, long offset, double newValue);

    public double getDoubleVolatile(Object obj, long offset) {
        return getDouble(obj, offset);
    }

    public void putDoubleVolatile(Object obj, long offset, double newValue) {
        putDouble(obj, offset, newValue);
    }

    public void putOrderedInt(Object obj, long offset, int newValue) {
        putInt(obj, offset, newValue);
    }

    public void putOrderedLong(Object obj, long offset, long newValue) {
        putLong(obj, offset, newValue);
    }

    public void putOrderedObject(Object obj, long offset, Object newValue) {
        putObject(obj, offset, newValue);
    }

    public int getAndAddInt(Object o, long offset, int delta) {
        int v;
        do {
            v = getInt(o, offset);
        } while (!compareAndSwapInt(o, offset, v, v + delta));
        return v;
    }

    public long getAndAddLong(Object o, long offset, long delta) {
        long v;
        do {
            v = getLong(o, offset);
        } while (!compareAndSwapLong(o, offset, v, v + delta));
        return v;
    }

    public int getAndSetInt(Object o, long offset, int newValue) {
        int v;
        do {
            v = getInt(o, offset);
        } while (!compareAndSwapInt(o, offset, v, newValue));
        return v;
    }

    public long getAndSetLong(Object o, long offset, long newValue) {
        long v;
        do {
            v = getLong(o, offset);
        } while (!compareAndSwapLong(o, offset, v, newValue));
        return v;
    }

    public Object getAndSetObject(Object o, long offset, Object newValue) {
        Object v;
        do {
            v = getObject(o, offset);
        } while (!compareAndSwapObject(o, offset, v, newValue));
        return v;
    }

    public void park(boolean absolute, long time) {
        if (absolute) {
            LockSupport.parkUntil(time);
        } else if (time == 0) {
            LockSupport.park();
        } else {
            LockSupport.parkNanos(time);
        }
    }

    public void unpark(Object obj) {
        if (obj instanceof Thread) {
            LockSupport.unpark((Thread) obj);
        }
    }

    public native Object allocateInstance(Class<?> c) throws InstantiationException;

    public int addressSize() {
        return 8;
    }

    public int pageSize() {
        return 4096;
    }

    public native long allocateMemory(long bytes);

    public native void freeMemory(long address);

    public native void setMemory(long address, long bytes, byte value);

    public native void copyMemory(long srcAddr, long dstAddr, long bytes);

    public native byte getByte(long address);

    public native void putByte(long address, byte x);

    public native int getInt(long address);

    public native void putInt(long address, int x);

    public native long getLong(long address);

    public native void putLong(long address, long x);

    public native float getFloat(long address);

    public native void putFloat(long address, float x);

    public native double getDouble(long address);

    public native void putDouble(long address, double x);

    public native short getShort(long address);

    public native void putShort(long address, short x);

    public native char getChar(long address);

    public native void putChar(long address, char x);

    public void loadFence() {
    }

    public void storeFence() {
    }

    public void fullFence() {
    }
}
