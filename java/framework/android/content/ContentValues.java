package android.content;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class ContentValues implements Parcelable {
    private final HashMap<String, Object> mMap;

    public ContentValues() { mMap = new HashMap<String, Object>(8); }
    public ContentValues(int size) { mMap = new HashMap<String, Object>(size); }
    public ContentValues(ContentValues from) { mMap = new HashMap<String, Object>(from.mMap); }

    public boolean equals(Object object) { return object instanceof ContentValues && mMap.equals(((ContentValues) object).mMap); }
    public int hashCode() { return mMap.hashCode(); }
    public void put(String key, String value) { mMap.put(key, value); }
    public void putAll(ContentValues other) { mMap.putAll(other.mMap); }
    public void put(String key, Byte value) { mMap.put(key, value); }
    public void put(String key, Short value) { mMap.put(key, value); }
    public void put(String key, Integer value) { mMap.put(key, value); }
    public void put(String key, Long value) { mMap.put(key, value); }
    public void put(String key, Float value) { mMap.put(key, value); }
    public void put(String key, Double value) { mMap.put(key, value); }
    public void put(String key, Boolean value) { mMap.put(key, value); }
    public void put(String key, byte[] value) { mMap.put(key, value); }
    public void putNull(String key) { mMap.put(key, null); }
    public int size() { return mMap.size(); }
    public boolean isEmpty() { return mMap.isEmpty(); }
    public void remove(String key) { mMap.remove(key); }
    public void clear() { mMap.clear(); }
    public boolean containsKey(String key) { return mMap.containsKey(key); }
    public Object get(String key) { return mMap.get(key); }

    public String getAsString(String key) {
        Object value = mMap.get(key);
        return value != null ? value.toString() : null;
    }

    public Long getAsLong(String key) {
        Object value = mMap.get(key);
        try {
            return value != null ? ((Number) value).longValue() : null;
        } catch (ClassCastException e) {
            if (value instanceof CharSequence) {
                try {
                    return Long.valueOf(value.toString());
                } catch (NumberFormatException e2) {
                    return null;
                }
            }
            return null;
        }
    }

    public Integer getAsInteger(String key) {
        Long l = getAsLong(key);
        return l != null ? l.intValue() : null;
    }

    public Short getAsShort(String key) {
        Long l = getAsLong(key);
        return l != null ? l.shortValue() : null;
    }

    public Byte getAsByte(String key) {
        Long l = getAsLong(key);
        return l != null ? l.byteValue() : null;
    }

    public Double getAsDouble(String key) {
        Object value = mMap.get(key);
        try {
            return value != null ? ((Number) value).doubleValue() : null;
        } catch (ClassCastException e) {
            if (value instanceof CharSequence) {
                try {
                    return Double.valueOf(value.toString());
                } catch (NumberFormatException e2) {
                    return null;
                }
            }
            return null;
        }
    }

    public Float getAsFloat(String key) {
        Double d = getAsDouble(key);
        return d != null ? d.floatValue() : null;
    }

    public Boolean getAsBoolean(String key) {
        Object value = mMap.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof CharSequence) return Boolean.valueOf(value.toString()) || "1".equals(value.toString());
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return null;
    }

    public byte[] getAsByteArray(String key) {
        Object value = mMap.get(key);
        return value instanceof byte[] ? (byte[]) value : null;
    }

    public Set<Map.Entry<String, Object>> valueSet() { return mMap.entrySet(); }
    public Set<String> keySet() { return mMap.keySet(); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel parcel, int flags) { parcel.writeMap(mMap); }

    public static final Parcelable.Creator<ContentValues> CREATOR = new Parcelable.Creator<ContentValues>() {
        @SuppressWarnings({"unchecked"})
        public ContentValues createFromParcel(Parcel in) {
            ContentValues cv = new ContentValues();
            in.readMap(cv.mMap, null);
            return cv;
        }
        public ContentValues[] newArray(int size) { return new ContentValues[size]; }
    };

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String name : mMap.keySet()) {
            String value = getAsString(name);
            if (sb.length() > 0) sb.append(" ");
            sb.append(name + "=" + value);
        }
        return sb.toString();
    }
}
