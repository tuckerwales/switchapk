package android.view;

import android.graphics.Point;
import android.util.DisplayMetrics;

/**
 * One display per process. nGetInfo is filled by the platform:
 * width, height, dpi, refresh x 1000, has-touch.
 */
public class Display {
    public static final int DEFAULT_DISPLAY = 0;
    public static final int FLAG_PRESENTATION = 8;
    public static final int FLAG_PRIVATE = 4;
    public static final int FLAG_ROUND = 16;
    public static final int FLAG_SECURE = 2;
    public static final int FLAG_SUPPORTS_PROTECTED_BUFFERS = 1;
    public static final int INVALID_DISPLAY = -1;
    public static final int STATE_DOZE = 3;
    public static final int STATE_DOZE_SUSPEND = 4;
    public static final int STATE_OFF = 1;
    public static final int STATE_ON = 2;
    public static final int STATE_ON_SUSPEND = 6;
    public static final int STATE_UNKNOWN = 0;
    public static final int STATE_VR = 5;

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

    public boolean isValid() { return true; }

    public int getDisplayId() { return DEFAULT_DISPLAY; }

    public String getName() { return "Built-in Screen"; }

    public int getFlags() { return FLAG_SECURE | FLAG_SUPPORTS_PROTECTED_BUFFERS; }

    @Deprecated
    public int getOrientation() { return getRotation(); }

    @Deprecated
    public int getPixelFormat() { return android.graphics.PixelFormat.RGBA_8888; }

    public int getState() { return STATE_ON; }

    public long getAppVsyncOffsetNanos() { return 0; }

    public long getPresentationDeadlineNanos() { return (long) (1000000000L / getRefreshRate()); }

    @Deprecated
    public float[] getSupportedRefreshRates() { return new float[] {getRefreshRate()}; }

    public void getCurrentSizeRange(Point outSmallestSize, Point outLargestSize) {
        int w = getWidth();
        int h = getHeight();
        int small = Math.min(w, h);
        int large = Math.max(w, h);
        outSmallestSize.set(small, small);
        outLargestSize.set(large, large);
    }

    @Deprecated
    public void getRectSize(android.graphics.Rect outSize) { outSize.set(0, 0, getWidth(), getHeight()); }

    public boolean isHdr() { return false; }

    public boolean isWideColorGamut() { return false; }

    public boolean isMinimalPostProcessingSupported() { return false; }

    public Mode getMode() { return new Mode(1, getWidth(), getHeight(), getRefreshRate()); }

    public Mode[] getSupportedModes() { return new Mode[] {getMode()}; }

    @Override
    public String toString() {
        return "Display id " + DEFAULT_DISPLAY + ": " + getName() + ", " + getWidth() + " x " + getHeight()
                + ", " + getRefreshRate() + " fps, " + mDpi + " dpi";
    }

    /** A display mode: resolution and refresh rate. */
    public static final class Mode implements android.os.Parcelable {
        private final int mModeId;
        private final int mWidth;
        private final int mHeight;
        private final float mRefreshRate;

        Mode(int modeId, int width, int height, float refreshRate) {
            mModeId = modeId;
            mWidth = width;
            mHeight = height;
            mRefreshRate = refreshRate;
        }

        public int getModeId() { return mModeId; }
        public int getPhysicalWidth() { return mWidth; }
        public int getPhysicalHeight() { return mHeight; }
        public float getRefreshRate() { return mRefreshRate; }
        public float[] getAlternativeRefreshRates() { return new float[0]; }
        public int[] getSupportedHdrTypes() { return new int[0]; }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Mode)) return false;
            Mode m = (Mode) other;
            return m.mModeId == mModeId && m.mWidth == mWidth && m.mHeight == mHeight && m.mRefreshRate == mRefreshRate;
        }

        @Override
        public int hashCode() { return (mModeId * 31 + mWidth) * 31 + mHeight; }

        @Override
        public String toString() {
            return "{id=" + mModeId + ", width=" + mWidth + ", height=" + mHeight + ", fps=" + mRefreshRate + "}";
        }

        public int describeContents() { return 0; }

        public void writeToParcel(android.os.Parcel out, int flags) {
            out.writeInt(mModeId);
            out.writeInt(mWidth);
            out.writeInt(mHeight);
            out.writeFloat(mRefreshRate);
        }

        public static final android.os.Parcelable.Creator<Mode> CREATOR = new android.os.Parcelable.Creator<Mode>() {
            public Mode createFromParcel(android.os.Parcel in) {
                return new Mode(in.readInt(), in.readInt(), in.readInt(), in.readFloat());
            }

            public Mode[] newArray(int size) { return new Mode[size]; }
        };
    }

    private static native void nGetInfo(int[] out);
}
