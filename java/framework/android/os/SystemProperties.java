package android.os;

import java.util.HashMap;

public class SystemProperties {
    private static final HashMap<String, String> sProps = new HashMap<String, String>();

    static {
        sProps.put("ro.build.version.sdk", String.valueOf(Build.VERSION.SDK_INT));
        sProps.put("ro.product.model", Build.MODEL);
        sProps.put("ro.product.manufacturer", Build.MANUFACTURER);
        sProps.put("ro.kernel.qemu", "0");
        sProps.put("ro.debuggable", "0");
    }

    public static String get(String key) { return get(key, ""); }

    public static String get(String key, String def) {
        synchronized (sProps) {
            String v = sProps.get(key);
            return v == null ? def : v;
        }
    }

    public static int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static long getLong(String key, long def) {
        try {
            return Long.parseLong(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static boolean getBoolean(String key, boolean def) {
        String v = get(key, "");
        if (v.equals("1") || v.equals("true") || v.equals("y") || v.equals("yes") || v.equals("on")) return true;
        if (v.equals("0") || v.equals("false") || v.equals("n") || v.equals("no") || v.equals("off")) return false;
        return def;
    }

    public static void set(String key, String val) {
        synchronized (sProps) { sProps.put(key, val); }
    }
}
