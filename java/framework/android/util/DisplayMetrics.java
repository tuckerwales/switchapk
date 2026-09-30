package android.util;

public class DisplayMetrics {
    public static final int DENSITY_LOW = 120;
    public static final int DENSITY_MEDIUM = 160;
    public static final int DENSITY_TV = 213;
    public static final int DENSITY_HIGH = 240;
    public static final int DENSITY_260 = 260;
    public static final int DENSITY_280 = 280;
    public static final int DENSITY_300 = 300;
    public static final int DENSITY_XHIGH = 320;
    public static final int DENSITY_360 = 360;
    public static final int DENSITY_400 = 400;
    public static final int DENSITY_420 = 420;
    public static final int DENSITY_440 = 440;
    public static final int DENSITY_XXHIGH = 480;
    public static final int DENSITY_560 = 560;
    public static final int DENSITY_XXXHIGH = 640;
    public static final int DENSITY_DEFAULT = DENSITY_MEDIUM;
    public static final int DENSITY_DEVICE_STABLE = DENSITY_HIGH;

    public int widthPixels;
    public int heightPixels;
    public float density;
    public int densityDpi;
    public float scaledDensity;
    public float xdpi;
    public float ydpi;

    public DisplayMetrics() {}

    public void setTo(DisplayMetrics o) {
        widthPixels = o.widthPixels;
        heightPixels = o.heightPixels;
        density = o.density;
        densityDpi = o.densityDpi;
        scaledDensity = o.scaledDensity;
        xdpi = o.xdpi;
        ydpi = o.ydpi;
    }

    public void setToDefaults() {
        widthPixels = 0;
        heightPixels = 0;
        density = DENSITY_DEFAULT / (float) DENSITY_DEFAULT;
        densityDpi = DENSITY_DEFAULT;
        scaledDensity = density;
        xdpi = DENSITY_DEFAULT;
        ydpi = DENSITY_DEFAULT;
    }

    public boolean equals(Object o) {
        if (!(o instanceof DisplayMetrics)) return false;
        DisplayMetrics m = (DisplayMetrics) o;
        return widthPixels == m.widthPixels && heightPixels == m.heightPixels && density == m.density
                && densityDpi == m.densityDpi && scaledDensity == m.scaledDensity;
    }

    public int hashCode() { return widthPixels * heightPixels * densityDpi; }

    public String toString() {
        return "DisplayMetrics{density=" + density + ", width=" + widthPixels + ", height=" + heightPixels
                + ", scaledDensity=" + scaledDensity + ", xdpi=" + xdpi + ", ydpi=" + ydpi + "}";
    }
}
