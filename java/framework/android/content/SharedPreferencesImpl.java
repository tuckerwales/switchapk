package android.content;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

/** SharedPreferences stored as Android-compatible XML (shared_prefs/NAME.xml). */
public final class SharedPreferencesImpl implements SharedPreferences {
    private static final String TAG = "SharedPreferencesImpl";
    private static final Object CONTENT = new Object();

    private final File mFile;
    private final File mBackupFile;
    private HashMap<String, Object> mMap;
    private final WeakHashMap<OnSharedPreferenceChangeListener, Object> mListeners = new WeakHashMap<OnSharedPreferenceChangeListener, Object>();

    public SharedPreferencesImpl(File file) {
        mFile = file;
        mBackupFile = new File(file.getPath() + ".bak");
        load();
    }

    private void load() {
        HashMap<String, Object> map = new HashMap<String, Object>();
        if (mBackupFile.exists()) {
            mFile.delete();
            mBackupFile.renameTo(mFile);
        }
        if (mFile.exists() && mFile.canRead()) {
            FileInputStream in = null;
            try {
                in = new FileInputStream(mFile);
                XmlPullParser p = android.util.Xml.newPullParser();
                p.setInput(in, "UTF-8");
                readMap(p, map);
            } catch (Exception e) {
                Log.w(TAG, "Cannot read " + mFile, e);
            } finally {
                if (in != null) try { in.close(); } catch (Exception ignored) {}
            }
        }
        mMap = map;
    }

    private static void readMap(XmlPullParser p, Map<String, Object> map) throws Exception {
        int type;
        while ((type = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) continue;
            String tag = p.getName();
            String name = p.getAttributeValue(null, "name");
            String value = p.getAttributeValue(null, "value");
            if (tag.equals("map") || name == null) continue;
            switch (tag) {
                case "string": map.put(name, safeNextText(p)); break;
                case "int": map.put(name, Integer.parseInt(value)); break;
                case "long": map.put(name, Long.parseLong(value)); break;
                case "float": map.put(name, Float.parseFloat(value)); break;
                case "boolean": map.put(name, Boolean.parseBoolean(value)); break;
                case "null": map.put(name, null); break;
                case "set": {
                    HashSet<String> set = new HashSet<String>();
                    int depth = p.getDepth();
                    while ((type = p.next()) != XmlPullParser.END_DOCUMENT && !(type == XmlPullParser.END_TAG && p.getDepth() == depth)) {
                        if (type == XmlPullParser.START_TAG && p.getName().equals("string")) set.add(safeNextText(p));
                    }
                    map.put(name, set);
                    break;
                }
            }
        }
    }

    private static String safeNextText(XmlPullParser p) throws Exception {
        String t = p.nextText();
        return t;
    }

    private void writeToFile(Map<String, Object> map) {
        try {
            File dir = mFile.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            if (mFile.exists()) {
                if (!mBackupFile.exists()) mFile.renameTo(mBackupFile);
                else mFile.delete();
            }
            FileOutputStream out = new FileOutputStream(mFile);
            XmlSerializer s = android.util.Xml.newSerializer();
            s.setOutput(out, "utf-8");
            s.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            s.startDocument(null, true);
            s.startTag(null, "map");
            for (Map.Entry<String, Object> e : map.entrySet()) {
                Object v = e.getValue();
                String n = e.getKey();
                if (v == null) {
                    s.startTag(null, "null").attribute(null, "name", n).endTag(null, "null");
                } else if (v instanceof String) {
                    s.startTag(null, "string").attribute(null, "name", n).text((String) v).endTag(null, "string");
                } else if (v instanceof Integer) {
                    s.startTag(null, "int").attribute(null, "name", n).attribute(null, "value", v.toString()).endTag(null, "int");
                } else if (v instanceof Long) {
                    s.startTag(null, "long").attribute(null, "name", n).attribute(null, "value", v.toString()).endTag(null, "long");
                } else if (v instanceof Float) {
                    s.startTag(null, "float").attribute(null, "name", n).attribute(null, "value", v.toString()).endTag(null, "float");
                } else if (v instanceof Boolean) {
                    s.startTag(null, "boolean").attribute(null, "name", n).attribute(null, "value", v.toString()).endTag(null, "boolean");
                } else if (v instanceof Set) {
                    s.startTag(null, "set").attribute(null, "name", n);
                    for (Object o : (Set<?>) v) s.startTag(null, "string").text(String.valueOf(o)).endTag(null, "string");
                    s.endTag(null, "set");
                }
            }
            s.endTag(null, "map");
            s.endDocument();
            out.close();
            mBackupFile.delete();
        } catch (Exception e) {
            Log.w(TAG, "writeToFile: failed to write " + mFile, e);
        }
    }

    public Map<String, ?> getAll() {
        synchronized (this) { return new HashMap<String, Object>(mMap); }
    }

    public String getString(String key, String defValue) {
        synchronized (this) {
            Object v = mMap.get(key);
            return v instanceof String ? (String) v : defValue;
        }
    }

    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defValues) {
        synchronized (this) {
            Object v = mMap.get(key);
            return v instanceof Set ? (Set<String>) v : defValues;
        }
    }

    public int getInt(String key, int defValue) {
        synchronized (this) {
            Object v = mMap.get(key);
            if (v instanceof Integer) return (Integer) v;
            if (v != null && !(v instanceof Integer)) throw new ClassCastException(v.getClass().getName() + " cannot be cast to java.lang.Integer");
            return defValue;
        }
    }

    public long getLong(String key, long defValue) {
        synchronized (this) {
            Object v = mMap.get(key);
            if (v instanceof Long) return (Long) v;
            if (v != null) throw new ClassCastException(v.getClass().getName() + " cannot be cast to java.lang.Long");
            return defValue;
        }
    }

    public float getFloat(String key, float defValue) {
        synchronized (this) {
            Object v = mMap.get(key);
            if (v instanceof Float) return (Float) v;
            if (v != null) throw new ClassCastException(v.getClass().getName() + " cannot be cast to java.lang.Float");
            return defValue;
        }
    }

    public boolean getBoolean(String key, boolean defValue) {
        synchronized (this) {
            Object v = mMap.get(key);
            if (v instanceof Boolean) return (Boolean) v;
            if (v != null) throw new ClassCastException(v.getClass().getName() + " cannot be cast to java.lang.Boolean");
            return defValue;
        }
    }

    public boolean contains(String key) {
        synchronized (this) { return mMap.containsKey(key); }
    }

    public Editor edit() { return new EditorImpl(); }

    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        synchronized (this) { mListeners.put(listener, CONTENT); }
    }

    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        synchronized (this) { mListeners.remove(listener); }
    }

    final class EditorImpl implements Editor {
        private final HashMap<String, Object> mModified = new HashMap<String, Object>();
        private boolean mClear = false;

        public Editor putString(String key, String value) { synchronized (this) { mModified.put(key, value); return this; } }
        public Editor putStringSet(String key, Set<String> values) { synchronized (this) { mModified.put(key, values == null ? null : new HashSet<String>(values)); return this; } }
        public Editor putInt(String key, int value) { synchronized (this) { mModified.put(key, value); return this; } }
        public Editor putLong(String key, long value) { synchronized (this) { mModified.put(key, value); return this; } }
        public Editor putFloat(String key, float value) { synchronized (this) { mModified.put(key, value); return this; } }
        public Editor putBoolean(String key, boolean value) { synchronized (this) { mModified.put(key, value); return this; } }
        public Editor remove(String key) { synchronized (this) { mModified.put(key, this); return this; } }
        public Editor clear() { synchronized (this) { mClear = true; return this; } }

        public void apply() { commit(); }

        public boolean commit() {
            final ArrayList<String> keysModified = new ArrayList<String>();
            final ArrayList<OnSharedPreferenceChangeListener> listeners;
            HashMap<String, Object> snapshot;
            synchronized (SharedPreferencesImpl.this) {
                HashMap<String, Object> map = new HashMap<String, Object>(mMap);
                synchronized (this) {
                    if (mClear) {
                        if (!map.isEmpty()) map.clear();
                        mClear = false;
                    }
                    for (Map.Entry<String, Object> e : mModified.entrySet()) {
                        String k = e.getKey();
                        Object v = e.getValue();
                        if (v == this || v == null) {
                            if (!map.containsKey(k)) continue;
                            map.remove(k);
                        } else {
                            if (map.containsKey(k) && v.equals(map.get(k))) continue;
                            map.put(k, v);
                        }
                        keysModified.add(k);
                    }
                    mModified.clear();
                }
                mMap = map;
                snapshot = map;
                listeners = new ArrayList<OnSharedPreferenceChangeListener>(mListeners.keySet());
            }
            writeToFile(snapshot);
            if (!listeners.isEmpty() && !keysModified.isEmpty()) {
                Runnable notify = new Runnable() {
                    public void run() {
                        for (int i = keysModified.size() - 1; i >= 0; i--) {
                            for (OnSharedPreferenceChangeListener l : listeners) {
                                if (l != null) l.onSharedPreferenceChanged(SharedPreferencesImpl.this, keysModified.get(i));
                            }
                        }
                    }
                };
                if (Looper.myLooper() == Looper.getMainLooper()) notify.run();
                else new Handler(Looper.getMainLooper()).post(notify);
            }
            return true;
        }
    }
}
