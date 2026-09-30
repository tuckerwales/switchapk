package android.view;

import android.graphics.Point;
import android.util.DisplayMetrics;

/**
 * One display per process. nGetInfo is filled by the platform:
 * width, height, dpi, refresh x 1000, has-touch.
 */
public class Display {
    public static final int DEFAULT_DISPLAY = 0;

    private static Display sDefault;

    private int mWidth = 1280;
    private int mHeight = 720;
    private int mDpi = 240;
    private int mRefreshMilli = 60000;

    private Display() {}

    /** framework-internal */
    static Display defaultDisplay() {
        if (sDefault == null) sDefault = new Display();
        return sDefault;
    }

    private void refresh() {
        int[] info = new int[5];
        nGetInfo(info);
        if (info[0] > 0 && info[1] > 0) {
            mWidth = info[0];
            mHeight = info[1];
            if (info[2] > 0) mDpi = info[2];
            if (info[3] > 0) mRefreshMilli = info[3];
        }
    }

    public int getWidth() { refresh(); return mWidth; }
    public int getHeight() { refresh(); return mHeight; }
    public int getRotation() { return 0; }
    public float getRefreshRate() { refresh(); return mRefreshMilli / 1000f; }

    public void getSize(Point outSize) { outSize.set(getWidth(), getHeight()); }
    public void getRealSize(Point outSize) { getSize(outSize); }

    public void getMetrics(DisplayMetrics outMetrics) { fill(outMetrics); }
    public void getRealMetrics(DisplayMetrics outMetrics) { fill(outMetrics); }

    private void fill(DisplayMetrics out) {
        refresh();
        out.widthPixels = mWidth;
        out.heightPixels = mHeight;
        out.densityDpi = mDpi;
        out.density = mDpi / 160f;
        out.scaledDensity = out.density;
        out.xdpi = mDpi;
        out.ydpi = mDpi;
    }

    private static native void nGetInfo(int[] out);
}
