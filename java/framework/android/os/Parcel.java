package android.os;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-process Parcel: values are kept as objects in write order. Enough for
 * Parcelable round trips inside one process (saved state, intents).
 */
public final class Parcel {
    private final ArrayList<Object> mData = new ArrayList<Object>();
    private int mPos;

    public static final Parcelable.Creator<String> STRING_CREATOR = new Parcelable.Creator<String>() {
        public String createFromParcel(Parcel source) { return source.readString(); }
        public String[] newArray(int size) { return new String[size]; }
    };

    private Parcel() {}

    public static Parcel obtain() { return new Parcel(); }
    public final void recycle() { mData.clear(); mPos = 0; }
    public final int dataSize() { return mData.size(); }
    public final int dataAvail() { return mData.size() - mPos; }
    public final int dataPosition() { return mPos; }
    public final int dataCapacity() { return mData.size(); }
    public final void setDataPosition(int pos) { mPos = pos; }
    public final void setDataSize(int size) { while (mData.size() > size) mData.remove(mData.size() - 1); }
    public final void setDataCapacity(int size) {}
    public final byte[] marshall() { throw new UnsupportedOperationException("Parcel.marshall"); }
    public final void unmarshall(byte[] data, int offset, int length) { throw new UnsupportedOperationException("Parcel.unmarshall"); }
    public final void appendFrom(Parcel parcel, int offset, int length) {
        for (int i = offset; i < offset + length && i < parcel.mData.size(); i++) mData.add(parcel.mData.get(i));
    }
    public final boolean hasFileDescriptors() { return false; }
    public final void writeInterfaceToken(String interfaceName) { put(interfaceName); }
    public final void enforceInterface(String interfaceName) { get(); }

    private void put(Object o) {
        if (mPos < mData.size()) mData.set(mPos, o);
        else mData.add(o);
        mPos++;
    }

    private Object get() {
        if (mPos >= mData.size()) return null;
        return mData.get(mPos++);
    }

    private Number num() {
        Object o = get();
        return o instanceof Number ? (Number) o : (o instanceof Boolean ? (((Boolean) o) ? 1 : 0) : 0);
    }

    public final void writeByteArray(byte[] b) { put(b == null ? null : b.clone()); }
    public final void writeByteArray(byte[] b, int offset, int len) {
        if (b == null) { put(null); return; }
        byte[] c = new byte[len];
        System.arraycopy(b, offset, c, 0, len);
        put(c);
    }
    public final void writeBlob(byte[] b) { writeByteArray(b); }
    public final void writeInt(int val) { put(val); }
    public final void writeLong(long val) { put(val); }
    public final void writeFloat(float val) { put(val); }
    public final void writeDouble(double val) { put(val); }
    public final void writeString(String val) { put(val); }
    public final void writeString8(String val) { put(val); }
    public final void writeString16(String val) { put(val); }
    public final void writeBoolean(boolean val) { put(val); }
    public final void writeCharSequence(CharSequence val) { put(val); }
    public final void writeStrongBinder(IBinder val) { put(val); }
    public final void writeStrongInterface(IInterface val) { put(val); }
    public final void writeFileDescriptor(java.io.FileDescriptor val) { put(val); }
    public final void writeByte(byte val) { put(val); }
    public final void writeMap(Map val) { put(val == null ? null : new HashMap(val)); }
    public final void writeBundle(Bundle val) { put(val == null ? null : new Bundle(val)); }
    public final void writePersistableBundle(PersistableBundle val) { put(val); }
    public final void writeSize(android.util.Size val) { put(val); }
    public final void writeSizeF(android.util.SizeF val) { put(val); }
    public final void writeList(List val) { put(val == null ? null : new ArrayList(val)); }
    public final void writeArray(Object[] val) { put(val == null ? null : val.clone()); }
    public final void writeSparseArray(android.util.SparseArray<Object> val) { put(val); }
    public final void writeSparseBooleanArray(android.util.SparseBooleanArray val) { put(val); }
    public final void writeBooleanArray(boolean[] val) { put(val == null ? null : val.clone()); }
    public final void writeCharArray(char[] val) { put(val == null ? null : val.clone()); }
    public final void writeIntArray(int[] val) { put(val == null ? null : val.clone()); }
    public final void writeLongArray(long[] val) { put(val == null ? null : val.clone()); }
    public final void writeFloatArray(float[] val) { put(val == null ? null : val.clone()); }
    public final void writeDoubleArray(double[] val) { put(val == null ? null : val.clone()); }
    public final void writeStringArray(String[] val) { put(val == null ? null : val.clone()); }
    public final void writeBinderArray(IBinder[] val) { put(val); }
    public final void writeStringList(List<String> val) { put(val == null ? null : new ArrayList<String>(val)); }
    public final void writeBinderList(List<IBinder> val) { put(val); }
    public final <T extends Parcelable> void writeTypedList(List<T> val) { put(val == null ? null : new ArrayList<T>(val)); }
    public final <T extends Parcelable> void writeTypedList(List<T> val, int flags) { writeTypedList(val); }
    public final <T extends Parcelable> void writeTypedArray(T[] val, int flags) { put(val == null ? null : val.clone()); }
    public final <T extends Parcelable> void writeTypedObject(T val, int flags) { put(val); }
    public final void writeParcelable(Parcelable p, int flags) { put(p); }
    public final void writeParcelableArray(Parcelable[] value, int flags) { put(value == null ? null : value.clone()); }
    public final void writeParcelableList(List<? extends Parcelable> val, int flags) { put(val == null ? null : new ArrayList(val)); }
    public final void writeSerializable(Serializable s) { put(s); }
    public final void writeException(Exception e) { put(e); }
    public final void writeNoException() { put(null); }
    public final void writeValue(Object v) { put(v); }

    public final int readInt() { return num().intValue(); }
    public final long readLong() { return num().longValue(); }
    public final float readFloat() { return num().floatValue(); }
    public final double readDouble() { return num().doubleValue(); }
    public final byte readByte() { return num().byteValue(); }
    public final boolean readBoolean() { Object o = get(); return o instanceof Boolean ? (Boolean) o : (o instanceof Number && ((Number) o).intValue() != 0); }
    public final String readString() { Object o = get(); return o == null ? null : o.toString(); }
    public final String readString8() { return readString(); }
    public final String readString16() { return readString(); }
    public final CharSequence readCharSequence() { return (CharSequence) get(); }
    public final IBinder readStrongBinder() { return (IBinder) get(); }
    public final java.io.FileDescriptor readRawFileDescriptor() { return (java.io.FileDescriptor) get(); }
    public final ParcelFileDescriptor readFileDescriptor() { return (ParcelFileDescriptor) get(); }
    public final byte[] createByteArray() { return (byte[]) get(); }
    public final byte[] readBlob() { return createByteArray(); }
    public final void readByteArray(byte[] val) { byte[] b = createByteArray(); if (b != null) System.arraycopy(b, 0, val, 0, Math.min(b.length, val.length)); }
    public final boolean[] createBooleanArray() { return (boolean[]) get(); }
    public final void readBooleanArray(boolean[] val) { boolean[] b = createBooleanArray(); if (b != null) System.arraycopy(b, 0, val, 0, Math.min(b.length, val.length)); }
    public final char[] createCharArray() { return (char[]) get(); }
    public final void readCharArray(char[] val) { char[] b = createCharArray(); if (b != null) System.arraycopy(b, 0, val, 0, Math.min(b.length, val.length)); }
    public final int[] createIntArray() { return (int[]) get(); }
    public final void readIntArray(int[] val) { int[] b = createIntArray(); if (b != null) System.arraycopy(b, 0, val, 0, Math.min(b.length, val.length)); }
    public final long[] createLongArray() { return (long[]) get(); }
    public final void readLongArray(long[] val) { long[] b = createLongArray(); if (b != null) System.arraycopy(b, 0, val, 0, Math.min(b.length, val.length)); }
    public final float[] createFloatArray() { return (float[]) get(); }
    public final void readFloatArray(float[] val) { float[] b = createFloatArray(); if (b != null) System.arraycopy(b, 0, val, 0, Math.min(b.length, val.length)); }
    public final double[] createDoubleArray() { return (double[]) get(); }
    public final void readDoubleArray(double[] val) { double[] b = createDoubleArray(); if (b != null) System.arraycopy(b, 0, val, 0, Math.min(b.length, val.length)); }
    public final String[] createStringArray() { return (String[]) get(); }
    public final String[] readStringArray() { return createStringArray(); }
    public final void readStringArray(String[] val) { String[] b = createStringArray(); if (b != null) System.arraycopy(b, 0, val, 0, Math.min(b.length, val.length)); }
    @SuppressWarnings("unchecked")
    public final ArrayList<String> createStringArrayList() { return (ArrayList<String>) get(); }
    @SuppressWarnings("unchecked")
    public final void readStringList(List<String> list) { List<String> l = (List<String>) get(); list.clear(); if (l != null) list.addAll(l); }
    @SuppressWarnings("unchecked")
    public final <T> ArrayList<T> createTypedArrayList(Parcelable.Creator<T> c) { return (ArrayList<T>) get(); }
    @SuppressWarnings("unchecked")
    public final <T> void readTypedList(List<T> list, Parcelable.Creator<T> c) { List<T> l = (List<T>) get(); list.clear(); if (l != null) list.addAll(l); }
    @SuppressWarnings("unchecked")
    public final <T> T[] createTypedArray(Parcelable.Creator<T> c) { return (T[]) get(); }
    @SuppressWarnings("unchecked")
    public final <T> T readTypedObject(Parcelable.Creator<T> c) { return (T) get(); }
    @SuppressWarnings("unchecked")
    public final <T extends Parcelable> T readParcelable(ClassLoader loader) { return (T) get(); }
    @SuppressWarnings("unchecked")
    public <T> T readParcelable(ClassLoader loader, Class<T> clazz) { return (T) get(); }
    public final Parcelable[] readParcelableArray(ClassLoader loader) { return (Parcelable[]) get(); }
    @SuppressWarnings("unchecked")
    public final <T extends Parcelable> List<T> readParcelableList(List<T> list, ClassLoader cl) { List<T> l = (List<T>) get(); list.clear(); if (l != null) list.addAll(l); return list; }
    public final Serializable readSerializable() { return (Serializable) get(); }
    public final Object readValue(ClassLoader loader) { return get(); }
    public final Object[] readArray(ClassLoader loader) { return (Object[]) get(); }
    @SuppressWarnings("unchecked")
    public final ArrayList readArrayList(ClassLoader loader) { return (ArrayList) get(); }
    @SuppressWarnings("unchecked")
    public final void readList(List outVal, ClassLoader loader) { List l = (List) get(); outVal.clear(); if (l != null) outVal.addAll(l); }
    @SuppressWarnings("unchecked")
    public final void readMap(Map outVal, ClassLoader loader) { Map m = (Map) get(); outVal.clear(); if (m != null) outVal.putAll(m); }
    @SuppressWarnings("unchecked")
    public final HashMap readHashMap(ClassLoader loader) { Map m = (Map) get(); return m == null ? null : new HashMap(m); }
    public final Bundle readBundle() { return (Bundle) get(); }
    public final Bundle readBundle(ClassLoader loader) { return readBundle(); }
    public final PersistableBundle readPersistableBundle() { return (PersistableBundle) get(); }
    public final PersistableBundle readPersistableBundle(ClassLoader loader) { return readPersistableBundle(); }
    public final android.util.Size readSize() { return (android.util.Size) get(); }
    public final android.util.SizeF readSizeF() { return (android.util.SizeF) get(); }
    @SuppressWarnings("unchecked")
    public final <T> android.util.SparseArray<T> readSparseArray(ClassLoader loader) { return (android.util.SparseArray<T>) get(); }
    public final android.util.SparseBooleanArray readSparseBooleanArray() { return (android.util.SparseBooleanArray) get(); }
    public final void readException() { Object o = get(); if (o instanceof RuntimeException) throw (RuntimeException) o; }
    public final int readExceptionCode() { return 0; }
}
