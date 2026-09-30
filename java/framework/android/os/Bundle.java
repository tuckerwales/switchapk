package android.os;

import android.util.Size;
import android.util.SizeF;
import android.util.SparseArray;
import java.io.Serializable;
import java.util.ArrayList;

public final class Bundle extends BaseBundle implements Cloneable, Parcelable {
    public static final Bundle EMPTY = new Bundle();

    public Bundle() { super(); }
    public Bundle(ClassLoader loader) { super(); mClassLoader = loader; }
    public Bundle(int capacity) { super(capacity); }
    public Bundle(Bundle b) { super(b); }
    public Bundle(PersistableBundle b) { super(b); }

    public static Bundle forPair(String key, String value) {
        Bundle b = new Bundle(1);
        b.putString(key, value);
        return b;
    }

    @Override public void setClassLoader(ClassLoader loader) { super.setClassLoader(loader); }
    @Override public ClassLoader getClassLoader() { return super.getClassLoader(); }
    public boolean setAllowFds(boolean allowFds) { return false; }

    @Override
    public Object clone() { return new Bundle(this); }

    public Bundle deepCopy() { return new Bundle(this); }

    public boolean hasFileDescriptors() { return false; }
    public void putAll(Bundle bundle) { mMap.putAll(bundle.mMap); }

    @Override public void putByte(String key, byte value) { super.putByte(key, value); }
    @Override public void putChar(String key, char value) { super.putChar(key, value); }
    @Override public void putShort(String key, short value) { super.putShort(key, value); }
    @Override public void putFloat(String key, float value) { super.putFloat(key, value); }
    @Override public void putCharSequence(String key, CharSequence value) { super.putCharSequence(key, value); }
    public void putParcelable(String key, Parcelable value) { mMap.put(key, value); }
    public void putSize(String key, Size value) { mMap.put(key, value); }
    public void putSizeF(String key, SizeF value) { mMap.put(key, value); }
    public void putParcelableArray(String key, Parcelable[] value) { mMap.put(key, value); }
    public void putParcelableArrayList(String key, ArrayList<? extends Parcelable> value) { mMap.put(key, value); }
    public void putSparseParcelableArray(String key, SparseArray<? extends Parcelable> value) { mMap.put(key, value); }
    @Override public void putIntegerArrayList(String key, ArrayList<Integer> value) { super.putIntegerArrayList(key, value); }
    @Override public void putStringArrayList(String key, ArrayList<String> value) { super.putStringArrayList(key, value); }
    @Override public void putCharSequenceArrayList(String key, ArrayList<CharSequence> value) { super.putCharSequenceArrayList(key, value); }
    @Override public void putSerializable(String key, Serializable value) { super.putSerializable(key, value); }
    @Override public void putByteArray(String key, byte[] value) { super.putByteArray(key, value); }
    @Override public void putShortArray(String key, short[] value) { super.putShortArray(key, value); }
    @Override public void putCharArray(String key, char[] value) { super.putCharArray(key, value); }
    @Override public void putFloatArray(String key, float[] value) { super.putFloatArray(key, value); }
    @Override public void putCharSequenceArray(String key, CharSequence[] value) { super.putCharSequenceArray(key, value); }
    public void putBundle(String key, Bundle value) { mMap.put(key, value); }
    public void putBinder(String key, IBinder value) { mMap.put(key, value); }

    @Override public byte getByte(String key) { return super.getByte(key); }
    @Override public Byte getByte(String key, byte defaultValue) { return super.getByte(key, defaultValue); }
    @Override public char getChar(String key) { return super.getChar(key); }
    @Override public char getChar(String key, char defaultValue) { return super.getChar(key, defaultValue); }
    @Override public short getShort(String key) { return super.getShort(key); }
    @Override public short getShort(String key, short defaultValue) { return super.getShort(key, defaultValue); }
    @Override public float getFloat(String key) { return super.getFloat(key); }
    @Override public float getFloat(String key, float defaultValue) { return super.getFloat(key, defaultValue); }
    @Override public CharSequence getCharSequence(String key) { return super.getCharSequence(key); }
    @Override public CharSequence getCharSequence(String key, CharSequence defaultValue) { return super.getCharSequence(key, defaultValue); }
    public Size getSize(String key) { Object o = mMap.get(key); return o instanceof Size ? (Size) o : null; }
    public SizeF getSizeF(String key) { Object o = mMap.get(key); return o instanceof SizeF ? (SizeF) o : null; }
    public Bundle getBundle(String key) { Object o = mMap.get(key); return o instanceof Bundle ? (Bundle) o : null; }
    @SuppressWarnings("unchecked")
    public <T extends Parcelable> T getParcelable(String key) { Object o = mMap.get(key); return o instanceof Parcelable ? (T) o : null; }
    @SuppressWarnings("unchecked")
    public <T> T getParcelable(String key, Class<T> clazz) { Object o = mMap.get(key); return clazz.isInstance(o) ? (T) o : null; }
    public Parcelable[] getParcelableArray(String key) { Object o = mMap.get(key); return o instanceof Parcelable[] ? (Parcelable[]) o : null; }
    @SuppressWarnings("unchecked")
    public <T extends Parcelable> ArrayList<T> getParcelableArrayList(String key) { Object o = mMap.get(key); return o instanceof ArrayList ? (ArrayList<T>) o : null; }
    @SuppressWarnings("unchecked")
    public <T> ArrayList<T> getParcelableArrayList(String key, Class<? extends T> clazz) { Object o = mMap.get(key); return o instanceof ArrayList ? (ArrayList<T>) o : null; }
    @SuppressWarnings("unchecked")
    public <T extends Parcelable> SparseArray<T> getSparseParcelableArray(String key) { Object o = mMap.get(key); return o instanceof SparseArray ? (SparseArray<T>) o : null; }
    @Override public Serializable getSerializable(String key) { return super.getSerializable(key); }
    @SuppressWarnings("unchecked")
    public <T extends Serializable> T getSerializable(String key, Class<T> clazz) { Object o = mMap.get(key); return clazz.isInstance(o) ? (T) o : null; }
    @Override public ArrayList<Integer> getIntegerArrayList(String key) { return super.getIntegerArrayList(key); }
    @Override public ArrayList<String> getStringArrayList(String key) { return super.getStringArrayList(key); }
    @Override public ArrayList<CharSequence> getCharSequenceArrayList(String key) { return super.getCharSequenceArrayList(key); }
    @Override public byte[] getByteArray(String key) { return super.getByteArray(key); }
    @Override public short[] getShortArray(String key) { return super.getShortArray(key); }
    @Override public char[] getCharArray(String key) { return super.getCharArray(key); }
    @Override public float[] getFloatArray(String key) { return super.getFloatArray(key); }
    @Override public CharSequence[] getCharSequenceArray(String key) { return super.getCharSequenceArray(key); }
    public IBinder getBinder(String key) { Object o = mMap.get(key); return o instanceof IBinder ? (IBinder) o : null; }

    public int describeContents() { return 0; }
    public void writeToParcel(Parcel parcel, int flags) { parcel.writeBundle(this); }
    public void readFromParcel(Parcel parcel) { Bundle b = parcel.readBundle(); mMap.clear(); if (b != null) mMap.putAll(b.mMap); }

    public static final Parcelable.Creator<Bundle> CREATOR = new Parcelable.Creator<Bundle>() {
        public Bundle createFromParcel(Parcel in) { return in.readBundle(); }
        public Bundle[] newArray(int size) { return new Bundle[size]; }
    };

    @Override
    public synchronized String toString() { return "Bundle[" + mMap.toString() + "]"; }
}
