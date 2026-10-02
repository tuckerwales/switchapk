package android.provider;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/** Reads and writes the settings provider. framework-internal. */
final class SettingsStore {
    private SettingsStore() {}

    static String getString(ContentResolver resolver, Uri uri, String name) {
        if (resolver == null || name == null) return null;
        Cursor c = resolver.query(uri, new String[] { Settings.NameValueTable.VALUE },
                Settings.NameValueTable.NAME + "=?", new String[] { name }, null);
        if (c == null) return null;
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }

    static boolean putString(ContentResolver resolver, Uri uri, String name, String value) {
        if (resolver == null || name == null) return false;
        ContentValues values = new ContentValues();
        values.put(Settings.NameValueTable.NAME, name);
        values.put(Settings.NameValueTable.VALUE, value);
        return resolver.insert(uri, values) != null;
    }

    static int getInt(ContentResolver resolver, Uri uri, String name, int def) {
        String v = getString(resolver, uri, name);
        if (v == null) return def;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static int getInt(ContentResolver resolver, Uri uri, String name) throws Settings.SettingNotFoundException {
        String v = getString(resolver, uri, name);
        if (v == null) throw new Settings.SettingNotFoundException(name);
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new Settings.SettingNotFoundException(name);
        }
    }

    static boolean putInt(ContentResolver resolver, Uri uri, String name, int value) {
        return putString(resolver, uri, name, Integer.toString(value));
    }

    static long getLong(ContentResolver resolver, Uri uri, String name, long def) {
        String v = getString(resolver, uri, name);
        if (v == null) return def;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static long getLong(ContentResolver resolver, Uri uri, String name) throws Settings.SettingNotFoundException {
        String v = getString(resolver, uri, name);
        if (v == null) throw new Settings.SettingNotFoundException(name);
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new Settings.SettingNotFoundException(name);
        }
    }

    static boolean putLong(ContentResolver resolver, Uri uri, String name, long value) {
        return putString(resolver, uri, name, Long.toString(value));
    }

    static float getFloat(ContentResolver resolver, Uri uri, String name, float def) {
        String v = getString(resolver, uri, name);
        if (v == null) return def;
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static float getFloat(ContentResolver resolver, Uri uri, String name) throws Settings.SettingNotFoundException {
        String v = getString(resolver, uri, name);
        if (v == null) throw new Settings.SettingNotFoundException(name);
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            throw new Settings.SettingNotFoundException(name);
        }
    }

    static boolean putFloat(ContentResolver resolver, Uri uri, String name, float value) {
        return putString(resolver, uri, name, Float.toString(value));
    }
}
