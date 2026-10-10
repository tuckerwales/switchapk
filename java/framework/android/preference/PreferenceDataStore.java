package android.preference;

import java.util.Set;

/** Port of AOSP's PreferenceDataStore: a replacement for SharedPreferences; unimplemented types throw. */
@Deprecated
public interface PreferenceDataStore {
    default void putString(String key, String value) { throw new UnsupportedOperationException("Not implemented"); }
    default void putStringSet(String key, Set<String> values) {
        throw new UnsupportedOperationException("Not implemented");
    }
    default void putInt(String key, int value) { throw new UnsupportedOperationException("Not implemented"); }
    default void putLong(String key, long value) { throw new UnsupportedOperationException("Not implemented"); }
    default void putFloat(String key, float value) { throw new UnsupportedOperationException("Not implemented"); }
    default void putBoolean(String key, boolean value) { throw new UnsupportedOperationException("Not implemented"); }
    default String getString(String key, String defValue) { return defValue; }
    default Set<String> getStringSet(String key, Set<String> defValues) { return defValues; }
    default int getInt(String key, int defValue) { return defValue; }
    default long getLong(String key, long defValue) { return defValue; }
    default float getFloat(String key, float defValue) { return defValue; }
    default boolean getBoolean(String key, boolean defValue) { return defValue; }
}
