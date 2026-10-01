package com.android.internal.util;

import android.content.res.Resources;
import java.util.HashMap;

/**
 * framework-internal. Looks up framework resource ids that are not in the
 * public android.R (com.android.internal.R on AOSP) by name, with a cache.
 * Returns 0 when framework-res has no such resource.
 */
public final class InternalRes {
    private static final HashMap<String, Integer> sCache = new HashMap<String, Integer>();

    private InternalRes() {}

    public static int id(String type, String name) {
        final String key = type + "/" + name;
        synchronized (sCache) {
            Integer cached = sCache.get(key);
            if (cached != null) return cached;
            int id = Resources.getSystem().getIdentifier(name, type, "android");
            sCache.put(key, id);
            return id;
        }
    }

    public static int attr(String name) { return id("attr", name); }

    public static int layout(String name) { return id("layout", name); }

    public static int viewId(String name) { return id("id", name); }

    public static int style(String name) { return id("style", name); }

    public static int dimen(String name) { return id("dimen", name); }

    public static int drawable(String name) { return id("drawable", name); }

    /** Resolves internal attr names into an attribute array for obtainStyledAttributes. */
    public static int[] attrs(String... names) {
        int[] out = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            String n = names[i];
            if (n.startsWith("android:")) out[i] = publicAttr(n.substring(8));
            else out[i] = attr(n);
        }
        return out;
    }

    private static int publicAttr(String name) {
        try {
            return android.R.attr.class.getField(name).getInt(null);
        } catch (Exception e) {
            return attr(name);
        }
    }
}
