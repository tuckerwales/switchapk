package android.graphics;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Bitmaps keep their pixels in a Java int[] (0xAARRGGBB, unpremultiplied) so
 * the garbage collector owns the memory. Other configs are converted on the
 * way in and out.
 */
public final class Bitmap implements Parcelable {
    public static final int DENSITY_NONE = 0;
    private static volatile int sDefaultDensity = DisplayMetrics.DENSITY_DEFAULT;
    private static int sGenerationIds = 1;

    // read by natives
    int[] mPixels;
    int mWidth;
    int mHeight;

    private Config mConfig;
    private boolean mMutable;
    private boolean mHasAlpha = true;
    private boolean mPremultiplied = true;
    private boolean mRecycled;
    private boolean mHasMipMap;
    int mDensity = getDefaultDensity();
    private int mGenerationId = sGenerationIds++;
    byte[] mNinePatchChunk;
    NinePatch.InsetStruct mNinePatchInsets;

    public static void setDefaultDensity(int density) { sDefaultDensity = density; }
    static int getDefaultDensity() { return sDefaultDensity; }

    public enum Config {
        ALPHA_8(1), RGB_565(3), @Deprecated ARGB_4444(4), ARGB_8888(5), RGBA_F16(6), HARDWARE(7), RGBA_1010102(8);
        final int nativeInt;
        Config(int ni) { nativeInt = ni; }
        int bytes() {
            switch (this) {
                case ALPHA_8: return 1;
                case RGB_565: case ARGB_4444: return 2;
                case RGBA_F16: return 8;
                default: return 4;
            }
        }
    }

    public enum CompressFormat {
        JPEG(0), PNG(1), @Deprecated WEBP(2), WEBP_LOSSY(3), WEBP_LOSSLESS(4);
        final int nativeInt;
        CompressFormat(int ni) { nativeInt = ni; }
    }

    Bitmap(int width, int height, Config config, int[] pixels, boolean mutable) {
        mWidth = width;
        mHeight = height;
        mConfig = config == null || config == Config.HARDWARE ? Config.ARGB_8888 : config;
        mPixels = pixels != null ? pixels : new int[width * height];
        mMutable = mutable;
        mHasAlpha = mConfig != Config.RGB_565;
    }

    private void checkRecycled(String msg) {
        if (mRecycled) throw new IllegalStateException(msg);
    }

    private void checkMutable() {
        if (!mMutable) throw new IllegalStateException("Bitmap is immutable");
    }

    private void touch() { mGenerationId = sGenerationIds++; }

    public static Bitmap createBitmap(int width, int height, Config config) { return createBitmap(width, height, config, true); }
    public static Bitmap createBitmap(DisplayMetrics display, int width, int height, Config config) { return createBitmap(display, width, height, config, true); }

    public static Bitmap createBitmap(int width, int height, Config config, boolean hasAlpha) {
        return createBitmap(null, width, height, config, hasAlpha);
    }

    public static Bitmap createBitmap(int width, int height, Config config, boolean hasAlpha, ColorSpace colorSpace) {
        return createBitmap(null, width, height, config, hasAlpha);
    }

    public static Bitmap createBitmap(DisplayMetrics display, int width, int height, Config config, boolean hasAlpha) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("width and height must be > 0");
        Bitmap bm = new Bitmap(width, height, config, null, true);
        if (display != null) bm.mDensity = display.densityDpi;
        bm.setHasAlpha(hasAlpha);
        if (config == Config.ARGB_8888 && !hasAlpha) bm.eraseColor(0xff000000);
        return bm;
    }

    public static Bitmap createBitmap(DisplayMetrics display, int width, int height, Config config, boolean hasAlpha, ColorSpace colorSpace) {
        return createBitmap(display, width, height, config, hasAlpha);
    }

    public static Bitmap createBitmap(int[] colors, int offset, int stride, int width, int height, Config config) {
        return createBitmap(null, colors, offset, stride, width, height, config);
    }

    public static Bitmap createBitmap(DisplayMetrics display, int[] colors, int offset, int stride, int width, int height, Config config) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("width and height must be > 0");
        Bitmap bm = new Bitmap(width, height, config, null, false);
        for (int y = 0; y < height; y++) System.arraycopy(colors, offset + y * stride, bm.mPixels, y * width, width);
        if (display != null) bm.mDensity = display.densityDpi;
        return bm;
    }

    public static Bitmap createBitmap(int[] colors, int width, int height, Config config) { return createBitmap(null, colors, 0, width, width, height, config); }
    public static Bitmap createBitmap(DisplayMetrics display, int[] colors, int width, int height, Config config) { return createBitmap(display, colors, 0, width, width, height, config); }
    public static Bitmap createBitmap(Picture source) { return source.toBitmap(); }
    public static Bitmap createBitmap(Picture source, int width, int height, Config config) { return source.toBitmap(); }

    public static Bitmap createBitmap(Bitmap src) { return createBitmap(src, 0, 0, src.getWidth(), src.getHeight()); }
    public static Bitmap createBitmap(Bitmap source, int x, int y, int width, int height) { return createBitmap(source, x, y, width, height, null, false); }

    public static Bitmap createBitmap(Bitmap source, int x, int y, int width, int height, Matrix m, boolean filter) {
        if (x + width > source.getWidth()) throw new IllegalArgumentException("x + width must be <= bitmap.width()");
        if (y + height > source.getHeight()) throw new IllegalArgumentException("y + height must be <= bitmap.height()");
        if (x < 0 || y < 0) throw new IllegalArgumentException("x and y must be >= 0");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("width and height must be > 0");
        if (!source.isMutable() && x == 0 && y == 0 && width == source.getWidth() && height == source.getHeight() && (m == null || m.isIdentity())) {
            return source;
        }
        if (m == null || m.isIdentity()) {
            Bitmap bm = new Bitmap(width, height, source.mConfig, null, false);
            for (int row = 0; row < height; row++) System.arraycopy(source.mPixels, (y + row) * source.mWidth + x, bm.mPixels, row * width, width);
            bm.mDensity = source.mDensity;
            bm.mHasAlpha = source.mHasAlpha;
            return bm;
        }
        RectF srcR = new RectF(0, 0, width, height);
        RectF dstR = new RectF();
        m.mapRect(dstR, srcR);
        int neww = Math.round(dstR.width()), newh = Math.round(dstR.height());
        if (neww <= 0) neww = 1;
        if (newh <= 0) newh = 1;
        Bitmap bm = new Bitmap(neww, newh, source.mConfig == Config.RGB_565 && !m.rectStaysRect() ? Config.ARGB_8888 : source.mConfig, null, true);
        bm.mDensity = source.mDensity;
        Canvas c = new Canvas(bm);
        c.translate(-dstR.left, -dstR.top);
        c.concat(m);
        Paint p = new Paint();
        p.setFilterBitmap(filter);
        if (!m.rectStaysRect()) p.setAntiAlias(true);
        c.drawBitmap(source, new Rect(x, y, x + width, y + height), new RectF(0, 0, width, height), p);
        bm.mMutable = false;
        return bm;
    }

    public static Bitmap createScaledBitmap(Bitmap src, int dstWidth, int dstHeight, boolean filter) {
        if (dstWidth <= 0 || dstHeight <= 0) throw new IllegalArgumentException("width and height must be > 0");
        if (src.getWidth() == dstWidth && src.getHeight() == dstHeight && !src.isMutable()) return src;
        Bitmap bm = new Bitmap(dstWidth, dstHeight, src.mConfig, null, false);
        nScale(src.mPixels, src.mWidth, src.mHeight, bm.mPixels, dstWidth, dstHeight, filter);
        bm.mDensity = src.mDensity;
        bm.mHasAlpha = src.mHasAlpha;
        return bm;
    }

    public Bitmap copy(Config config, boolean isMutable) {
        checkRecycled("Can't copy a recycled bitmap");
        Bitmap b = new Bitmap(mWidth, mHeight, config, mPixels.clone(), isMutable);
        b.mDensity = mDensity;
        b.mHasAlpha = mHasAlpha && config != Config.RGB_565;
        if (config == Config.ALPHA_8) for (int i = 0; i < b.mPixels.length; i++) b.mPixels[i] &= 0xff000000;
        if (config == Config.RGB_565) for (int i = 0; i < b.mPixels.length; i++) b.mPixels[i] |= 0xff000000;
        return b;
    }

    public Bitmap asShared() { return this; }

    public Bitmap extractAlpha() { return extractAlpha(null, null); }

    public Bitmap extractAlpha(Paint paint, int[] offsetXY) {
        Bitmap b = new Bitmap(mWidth, mHeight, Config.ALPHA_8, null, true);
        for (int i = 0; i < mPixels.length; i++) b.mPixels[i] = mPixels[i] & 0xff000000;
        if (offsetXY != null) {
            offsetXY[0] = 0;
            offsetXY[1] = 0;
        }
        return b;
    }

    public void recycle() {
        mRecycled = true;
        mPixels = new int[0];
        mNinePatchChunk = null;
    }

    public final boolean isRecycled() { return mRecycled; }
    public int getGenerationId() { return mGenerationId; }
    public final int getWidth() { return mWidth; }
    public final int getHeight() { return mHeight; }
    public final int getDensity() { return mDensity; }
    public void setDensity(int density) { mDensity = density; }
    public final boolean isMutable() { return mMutable; }
    public final boolean isPremultiplied() { return mPremultiplied; }
    public final void setPremultiplied(boolean premultiplied) { mPremultiplied = premultiplied; }
    public final Config getConfig() { return mConfig; }
    public final boolean hasAlpha() { return mHasAlpha; }
    public void setHasAlpha(boolean hasAlpha) { mHasAlpha = hasAlpha; }
    public final boolean hasMipMap() { return mHasMipMap; }
    public final void setHasMipMap(boolean hasMipMap) { mHasMipMap = hasMipMap; }
    public final ColorSpace getColorSpace() { return ColorSpace.get(ColorSpace.Named.SRGB); }
    public void setColorSpace(ColorSpace colorSpace) {}
    public final int getRowBytes() { return mWidth * mConfig.bytes(); }
    public final int getByteCount() { return getRowBytes() * mHeight; }
    public final int getAllocationByteCount() { return getByteCount(); }
    public byte[] getNinePatchChunk() { return mNinePatchChunk; }
    public void getOpticalInsets(Rect outInsets) { outInsets.setEmpty(); }
    public void prepareToDraw() {}

    public void reconfigure(int width, int height, Config config) {
        checkMutable();
        if (width * height > mPixels.length) throw new IllegalArgumentException("Bitmap not large enough to support new configuration");
        mWidth = width;
        mHeight = height;
        mConfig = config;
        touch();
    }

    public void setWidth(int width) { reconfigure(width, getHeight(), getConfig()); }
    public void setHeight(int height) { reconfigure(getWidth(), height, getConfig()); }
    public void setConfig(Config config) { reconfigure(getWidth(), getHeight(), config); }

    public int getScaledWidth(Canvas canvas) { return scaleFromDensity(getWidth(), mDensity, canvas.getDensity()); }
    public int getScaledHeight(Canvas canvas) { return scaleFromDensity(getHeight(), mDensity, canvas.getDensity()); }
    public int getScaledWidth(DisplayMetrics metrics) { return scaleFromDensity(getWidth(), mDensity, metrics.densityDpi); }
    public int getScaledHeight(DisplayMetrics metrics) { return scaleFromDensity(getHeight(), mDensity, metrics.densityDpi); }
    public int getScaledWidth(int targetDensity) { return scaleFromDensity(getWidth(), mDensity, targetDensity); }
    public int getScaledHeight(int targetDensity) { return scaleFromDensity(getHeight(), mDensity, targetDensity); }

    static int scaleFromDensity(int size, int sdensity, int tdensity) {
        if (sdensity == DENSITY_NONE || tdensity == DENSITY_NONE || sdensity == tdensity) return size;
        return ((size * tdensity) + (sdensity >> 1)) / sdensity;
    }

    public boolean compress(CompressFormat format, int quality, OutputStream stream) {
        checkRecycled("Can't compress a recycled bitmap");
        if (stream == null) throw new NullPointerException();
        if (quality < 0 || quality > 100) throw new IllegalArgumentException("quality must be 0..100");
        byte[] data = nCompress(mPixels, mWidth, mHeight, format.nativeInt, quality);
        if (data == null) return false;
        try {
            stream.write(data);
            stream.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public int getPixel(int x, int y) {
        checkRecycled("Can't call getPixel() on a recycled bitmap");
        if (x < 0 || x >= mWidth || y < 0 || y >= mHeight) throw new IllegalArgumentException("x/y out of bounds");
        return mPixels[y * mWidth + x];
    }

    public Color getColor(int x, int y) { return Color.valueOf(getPixel(x, y)); }

    public void getPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {
        checkRecycled("Can't call getPixels() on a recycled bitmap");
        if (width == 0 || height == 0) return;
        if (x < 0 || y < 0 || x + width > mWidth || y + height > mHeight) throw new IllegalArgumentException("x/y/width/height out of bounds");
        for (int row = 0; row < height; row++) System.arraycopy(mPixels, (y + row) * mWidth + x, pixels, offset + row * stride, width);
    }

    public void setPixel(int x, int y, int color) {
        checkRecycled("Can't call setPixel() on a recycled bitmap");
        checkMutable();
        if (x < 0 || x >= mWidth || y < 0 || y >= mHeight) throw new IllegalArgumentException("x/y out of bounds");
        mPixels[y * mWidth + x] = fix(color);
        touch();
    }

    private int fix(int c) {
        if (mConfig == Config.RGB_565) return c | 0xff000000;
        if (mConfig == Config.ALPHA_8) return c & 0xff000000;
        return c;
    }

    public void setPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {
        checkRecycled("Can't call setPixels() on a recycled bitmap");
        checkMutable();
        if (width == 0 || height == 0) return;
        if (x < 0 || y < 0 || x + width > mWidth || y + height > mHeight) throw new IllegalArgumentException("x/y/width/height out of bounds");
        for (int row = 0; row < height; row++) {
            int d = (y + row) * mWidth + x, s = offset + row * stride;
            for (int i = 0; i < width; i++) mPixels[d + i] = fix(pixels[s + i]);
        }
        touch();
    }

    public void eraseColor(int c) {
        checkRecycled("Can't erase a recycled bitmap");
        checkMutable();
        java.util.Arrays.fill(mPixels, 0, mWidth * mHeight, fix(c));
        touch();
    }

    public void eraseColor(long color) { eraseColor(Color.toArgb(color)); }

    public void copyPixelsToBuffer(Buffer dst) {
        int n = mWidth * mHeight;
        if (dst instanceof IntBuffer) {
            IntBuffer ib = (IntBuffer) dst;
            for (int i = 0; i < n; i++) ib.put(toRgbaPremulInt(mPixels[i]));
            return;
        }
        if (dst instanceof ShortBuffer) {
            ShortBuffer sb = (ShortBuffer) dst;
            for (int i = 0; i < n; i++) sb.put(to565(mPixels[i]));
            return;
        }
        ByteBuffer bb = (ByteBuffer) dst;
        for (int i = 0; i < n; i++) {
            int c = mPixels[i];
            switch (mConfig) {
                case ALPHA_8:
                    bb.put((byte) (c >>> 24));
                    break;
                case RGB_565: {
                    short s = to565(c);
                    bb.put((byte) s);
                    bb.put((byte) (s >> 8));
                    break;
                }
                case ARGB_4444: {
                    int a = c >>> 28, r = (c >> 20) & 15, g = (c >> 12) & 15, b = (c >> 4) & 15;
                    int v = (r << 12) | (g << 8) | (b << 4) | a;
                    bb.put((byte) v);
                    bb.put((byte) (v >> 8));
                    break;
                }
                default: {
                    int a = c >>> 24;
                    int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                    if (mPremultiplied && a != 255) {
                        r = r * a / 255;
                        g = g * a / 255;
                        b = b * a / 255;
                    }
                    bb.put((byte) r);
                    bb.put((byte) g);
                    bb.put((byte) b);
                    bb.put((byte) a);
                }
            }
        }
    }

    private int toRgbaPremulInt(int c) {
        int a = c >>> 24, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        if (mPremultiplied && a != 255) {
            r = r * a / 255;
            g = g * a / 255;
            b = b * a / 255;
        }
        // native byte order of the buffer decides the memory layout; match little-endian RGBA
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static short to565(int c) {
        int r = (c >> 19) & 31, g = (c >> 10) & 63, b = (c >> 3) & 31;
        return (short) ((r << 11) | (g << 5) | b);
    }

    public void copyPixelsFromBuffer(Buffer src) {
        checkMutable();
        int n = mWidth * mHeight;
        if (src instanceof IntBuffer) {
            IntBuffer ib = (IntBuffer) src;
            for (int i = 0; i < n; i++) {
                int v = ib.get();
                int r = v & 255, g = (v >> 8) & 255, b = (v >> 16) & 255, a = v >>> 24;
                mPixels[i] = unpremul(a, r, g, b);
            }
            touch();
            return;
        }
        ByteBuffer bb = (ByteBuffer) src;
        for (int i = 0; i < n; i++) {
            switch (mConfig) {
                case ALPHA_8:
                    mPixels[i] = (bb.get() & 0xff) << 24;
                    break;
                case RGB_565: {
                    int v = (bb.get() & 0xff) | ((bb.get() & 0xff) << 8);
                    int r = (v >> 11) & 31, g = (v >> 5) & 63, b = v & 31;
                    mPixels[i] = 0xff000000 | ((r << 3 | r >> 2) << 16) | ((g << 2 | g >> 4) << 8) | (b << 3 | b >> 2);
                    break;
                }
                case ARGB_4444: {
                    int v = (bb.get() & 0xff) | ((bb.get() & 0xff) << 8);
                    int r = (v >> 12) & 15, g = (v >> 8) & 15, b = (v >> 4) & 15, a = v & 15;
                    mPixels[i] = (a * 17 << 24) | (r * 17 << 16) | (g * 17 << 8) | b * 17;
                    break;
                }
                default: {
                    int r = bb.get() & 0xff, g = bb.get() & 0xff, b = bb.get() & 0xff, a = bb.get() & 0xff;
                    mPixels[i] = unpremul(a, r, g, b);
                }
            }
        }
        touch();
    }

    private int unpremul(int a, int r, int g, int b) {
        if (mPremultiplied && a != 255 && a != 0) {
            r = Math.min(255, r * 255 / a);
            g = Math.min(255, g * 255 / a);
            b = Math.min(255, b * 255 / a);
        }
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public boolean sameAs(Bitmap other) {
        if (this == other) return true;
        if (other == null || mWidth != other.mWidth || mHeight != other.mHeight || mConfig != other.mConfig) return false;
        for (int i = 0; i < mWidth * mHeight; i++) if (mPixels[i] != other.mPixels[i]) return false;
        return true;
    }

    /** Direct access to the pixel store (framework internal, used by GL uploads and SurfaceView). */
    public int[] getPixelArray() { return mPixels; }

    public int describeContents() { return 0; }
    public void writeToParcel(Parcel p, int flags) { p.writeValue(this); }

    public static final Parcelable.Creator<Bitmap> CREATOR = new Parcelable.Creator<Bitmap>() {
        public Bitmap createFromParcel(Parcel p) { return (Bitmap) p.readValue(null); }
        public Bitmap[] newArray(int size) { return new Bitmap[size]; }
    };

    static native byte[] nCompress(int[] px, int w, int h, int format, int quality);
    static native void nScale(int[] src, int sw, int sh, int[] dst, int dw, int dh, boolean filter);
}
