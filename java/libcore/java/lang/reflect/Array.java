package java.lang.reflect;

public final class Array {
    private Array() {
    }

    public static Object newInstance(Class<?> componentType, int length) throws NegativeArraySizeException {
        return newArray(componentType, length);
    }

    public static Object newInstance(Class<?> componentType, int... dimensions) {
        if (dimensions.length == 0) {
            throw new IllegalArgumentException("Empty dimensions array");
        }
        return multiNew(componentType, dimensions, 0);
    }

    private static Object multiNew(Class<?> componentType, int[] dims, int index) {
        if (index == dims.length - 1) {
            return newArray(componentType, dims[index]);
        }
        Class<?> sub = componentType;
        for (int i = index + 1; i < dims.length; i++) {
            sub = newArray(sub, 0).getClass();
        }
        Object[] arr = (Object[]) newArray(sub, dims[index]);
        for (int i = 0; i < arr.length; i++) {
            arr[i] = multiNew(componentType, dims, index + 1);
        }
        return arr;
    }

    private static native Object newArray(Class<?> componentType, int length);

    public static native int getLength(Object array);

    public static native Object get(Object array, int index);

    public static native void set(Object array, int index, Object value);

    public static boolean getBoolean(Object array, int index) {
        return ((Boolean) get(array, index)).booleanValue();
    }

    public static byte getByte(Object array, int index) {
        return ((Number) get(array, index)).byteValue();
    }

    public static char getChar(Object array, int index) {
        return ((Character) get(array, index)).charValue();
    }

    public static short getShort(Object array, int index) {
        return ((Number) get(array, index)).shortValue();
    }

    public static int getInt(Object array, int index) {
        Object v = get(array, index);
        return v instanceof Character ? ((Character) v).charValue() : ((Number) v).intValue();
    }

    public static long getLong(Object array, int index) {
        Object v = get(array, index);
        return v instanceof Character ? ((Character) v).charValue() : ((Number) v).longValue();
    }

    public static float getFloat(Object array, int index) {
        Object v = get(array, index);
        return v instanceof Character ? ((Character) v).charValue() : ((Number) v).floatValue();
    }

    public static double getDouble(Object array, int index) {
        Object v = get(array, index);
        return v instanceof Character ? ((Character) v).charValue() : ((Number) v).doubleValue();
    }

    public static void setBoolean(Object array, int index, boolean z) {
        set(array, index, Boolean.valueOf(z));
    }

    public static void setByte(Object array, int index, byte b) {
        set(array, index, Byte.valueOf(b));
    }

    public static void setChar(Object array, int index, char c) {
        set(array, index, Character.valueOf(c));
    }

    public static void setShort(Object array, int index, short s) {
        set(array, index, Short.valueOf(s));
    }

    public static void setInt(Object array, int index, int i) {
        set(array, index, Integer.valueOf(i));
    }

    public static void setLong(Object array, int index, long l) {
        set(array, index, Long.valueOf(l));
    }

    public static void setFloat(Object array, int index, float f) {
        set(array, index, Float.valueOf(f));
    }

    public static void setDouble(Object array, int index, double d) {
        set(array, index, Double.valueOf(d));
    }
}
