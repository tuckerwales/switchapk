package android.os;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Set;

public class BaseBundle {
    final android.util.ArrayMap<String, Object> mMap;
    ClassLoader mClassLoader;

    BaseBundle() { mMap = new android.util.ArrayMap<String, Object>(); }
    BaseBundle(int capacity) { mMap = new android.util.ArrayMap<String, Object>(capacity); }
    BaseBundle(BaseBundle b) { mMap = new android.util.ArrayMap<String, Object>(b.mMap); mClassLoader = b.mClassLoader; }

    void setClassLoader(ClassLoader loader) { mClassLoader = loader; }
    ClassLoader getClassLoader() { return mClassLoader; }

    public int size() { return mMap.size(); }
    public boolean isEmpty() { return mMap.isEmpty(); }
    public void clear() { mMap.clear(); }
    public boolean containsKey(String key) { return mMap.containsKey(key); }
    public Object get(String key) { return mMap.get(key); }
    public void remove(String key) { mMap.remove(key); }
    public void putAll(PersistableBundle bundle) { mMap.putAll(bundle.mMap); }
    public Set<String> keySet() { return mMap.keySet(); }

    public void putBoolean(String key, boolean value) { mMap.put(key, value); }
    void putByte(String key, byte value) { mMap.put(key, value); }
    void putChar(String key, char value) { mMap.put(key, value); }
    void putShort(String key, short value) { mMap.put(key, value); }
    public void putInt(String key, int value) { mMap.put(key, value); }
    public void putLong(String key, long value) { mMap.put(key, value); }
    void putFloat(String key, float value) { mMap.put(key, value); }
    public void putDouble(String key, double value) { mMap.put(key, value); }
    public void putString(String key, String value) { mMap.put(key, value); }
    void putCharSequence(String key, CharSequence value) { mMap.put(key, value); }
    void putIntegerArrayList(String key, ArrayList<Integer> value) { mMap.put(key, value); }
    void putStringArrayList(String key, ArrayList<String> value) { mMap.put(key, value); }
    void putCharSequenceArrayList(String key, ArrayList<CharSequence> value) { mMap.put(key, value); }
    void putSerializable(String key, Serializable value) { mMap.put(key, value); }
    public void putBooleanArray(String key, boolean[] value) { mMap.put(key, value); }
    void putByteArray(String key, byte[] value) { mMap.put(key, value); }
    void putShortArray(String key, short[] value) { mMap.put(key, value); }
    void putCharArray(String key, char[] value) { mMap.put(key, value); }
    public void putIntArray(String key, int[] value) { mMap.put(key, value); }
    public void putLongArray(String key, long[] value) { mMap.put(key, value); }
    void putFloatArray(String key, float[] value) { mMap.put(key, value); }
    public void putDoubleArray(String key, double[] value) { mMap.put(key, value); }
    public void putStringArray(String key, String[] value) { mMap.put(key, value); }
    void putCharSequenceArray(String key, CharSequence[] value) { mMap.put(key, value); }

    private <T> T typed(String key, Class<T> c, T def) {
        Object o = mMap.get(key);
        if (o == null) return def;
        if (c.isInstance(o)) return c.cast(o);
        return def;
    }

    public boolean getBoolean(String key) { return getBoolean(key, false); }
    public boolean getBoolean(String key, boolean defaultValue) { return typed(key, Boolean.class, defaultValue); }
    byte getByte(String key) { return getByte(key, (byte) 0); }
    Byte getByte(String key, byte defaultValue) { return typed(key, Byte.class, defaultValue); }
    char getChar(String key) { return getChar(key, (char) 0); }
    char getChar(String key, char defaultValue) { return typed(key, Character.class, defaultValue); }
    short getShort(String key) { return getShort(key, (short) 0); }
    short getShort(String key, short defaultValue) { return typed(key, Short.class, defaultValue); }
    public int getInt(String key) { return getInt(key, 0); }
    public int getInt(String key, int defaultValue) { return typed(key, Integer.class, defaultValue); }
    public long getLong(String key) { return getLong(key, 0L); }
    public long getLong(String key, long defaultValue) { return typed(key, Long.class, defaultValue); }
    float getFloat(String key) { return getFloat(key, 0f); }
    float getFloat(String key, float defaultValue) { return typed(key, Float.class, defaultValue); }
    public double getDouble(String key) { return getDouble(key, 0.0); }
    public double getDouble(String key, double defaultValue) { return typed(key, Double.class, defaultValue); }
    public String getString(String key) { Object o = mMap.get(key); return o instanceof String ? (String) o : null; }
    public String getString(String key, String defaultValue) { String s = getString(key); return s == null ? defaultValue : s; }
    CharSequence getCharSequence(String key) { Object o = mMap.get(key); return o instanceof CharSequence ? (CharSequence) o : null; }
    CharSequence getCharSequence(String key, CharSequence defaultValue) { CharSequence cs = getCharSequence(key); return cs == null ? defaultValue : cs; }
    Serializable getSerializable(String key) { Object o = mMap.get(key); return o instanceof Serializable ? (Serializable) o : null; }
    @SuppressWarnings("unchecked") ArrayList<Integer> getIntegerArrayList(String key) { return typed(key, ArrayList.class, null); }
    @SuppressWarnings("unchecked") ArrayList<String> getStringArrayList(String key) { return typed(key, ArrayList.class, null); }
    @SuppressWarnings("unchecked") ArrayList<CharSequence> getCharSequenceArrayList(String key) { return typed(key, ArrayList.class, null); }
    public boolean[] getBooleanArray(String key) { return typed(key, boolean[].class, null); }
    byte[] getByteArray(String key) { return typed(key, byte[].class, null); }
    short[] getShortArray(String key) { return typed(key, short[].class, null); }
    char[] getCharArray(String key) { return typed(key, char[].class, null); }
    public int[] getIntArray(String key) { return typed(key, int[].class, null); }
    public long[] getLongArray(String key) { return typed(key, long[].class, null); }
    float[] getFloatArray(String key) { return typed(key, float[].class, null); }
    public double[] getDoubleArray(String key) { return typed(key, double[].class, null); }
    public String[] getStringArray(String key) { return typed(key, String[].class, null); }
    CharSequence[] getCharSequenceArray(String key) { return typed(key, CharSequence[].class, null); }
}
