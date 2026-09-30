package android.graphics;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class BitmapFactory {
    public static class Options {
        public Bitmap inBitmap;
        public boolean inMutable;
        public boolean inJustDecodeBounds;
        public int inSampleSize;
        public Bitmap.Config inPreferredConfig = Bitmap.Config.ARGB_8888;
        public ColorSpace inPreferredColorSpace;
        public boolean inPremultiplied = true;
        @Deprecated public boolean inDither;
        public int inDensity;
        public int inTargetDensity;
        public int inScreenDensity;
        public boolean inScaled = true;
        @Deprecated public boolean inPurgeable;
        @Deprecated public boolean inInputShareable;
        @Deprecated public boolean inPreferQualityOverSpeed;
        public int outWidth;
        public int outHeight;
        public String outMimeType;
        public Bitmap.Config outConfig;
        public ColorSpace outColorSpace;
        public byte[] inTempStorage;
        @Deprecated public boolean mCancel;

        public Options() {}

        @Deprecated
        public void requestCancelDecode() { mCancel = true; }
    }

    public static Bitmap decodeFile(String pathName, Options opts) {
        InputStream stream = null;
        try {
            stream = new FileInputStream(pathName);
            return decodeStream(stream, null, opts);
        } catch (Exception e) {
            return null;
        } finally {
            if (stream != null) try { stream.close(); } catch (IOException ignored) {}
        }
    }

    public static Bitmap decodeFile(String pathName) { return decodeFile(pathName, null); }

    public static Bitmap decodeResourceStream(Resources res, TypedValue value, InputStream is, Rect pad, Options opts) {
        if (opts == null) opts = new Options();
        if (opts.inDensity == 0 && value != null) {
            final int density = value.density;
            if (density == TypedValue.DENSITY_DEFAULT) opts.inDensity = DisplayMetrics.DENSITY_DEFAULT;
            else if (density != TypedValue.DENSITY_NONE) opts.inDensity = density;
        }
        if (opts.inTargetDensity == 0 && res != null) opts.inTargetDensity = res.getDisplayMetrics().densityDpi;
        return decodeStream(is, pad, opts);
    }

    public static Bitmap decodeResource(Resources res, int id, Options opts) {
        Bitmap bm = null;
        InputStream is = null;
        try {
            final TypedValue value = new TypedValue();
            is = res.openRawResource(id, value);
            bm = decodeResourceStream(res, value, is, null, opts);
        } catch (Exception e) {
            // Android returns null when the resource cannot be decoded
        } finally {
            if (is != null) try { is.close(); } catch (IOException ignored) {}
        }
        if (bm == null && opts != null && opts.inBitmap != null) throw new IllegalArgumentException("Problem decoding into existing bitmap");
        return bm;
    }

    public static Bitmap decodeResource(Resources res, int id) { return decodeResource(res, id, null); }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length, Options opts) {
        if ((offset | length) < 0 || data.length < offset + length) throw new ArrayIndexOutOfBoundsException();
        return decode(data, offset, length, null, opts);
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length) { return decodeByteArray(data, offset, length, null); }

    public static Bitmap decodeStream(InputStream is, Rect outPadding, Options opts) {
        if (is == null) return null;
        byte[] data;
        try {
            if (is instanceof AssetManager.AssetInputStream && is.available() > 0) {
                data = new byte[is.available()];
                int n = 0;
                while (n < data.length) {
                    int r = is.read(data, n, data.length - n);
                    if (r <= 0) break;
                    n += r;
                }
            } else {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(64 * 1024);
                byte[] buf = opts != null && opts.inTempStorage != null ? opts.inTempStorage : new byte[16 * 1024];
                int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                data = bos.toByteArray();
            }
        } catch (IOException e) {
            return null;
        }
        return decode(data, 0, data.length, outPadding, opts);
    }

    public static Bitmap decodeStream(InputStream is) { return decodeStream(is, null, null); }

    public static Bitmap decodeFileDescriptor(FileDescriptor fd, Rect outPadding, Options opts) {
        return decodeStream(new FileInputStream(fd), outPadding, opts);
    }

    public static Bitmap decodeFileDescriptor(FileDescriptor fd) { return decodeFileDescriptor(fd, null, null); }

    static String mimeOf(byte[] d, int off, int len) {
        if (len >= 4 && (d[off] & 0xff) == 0x89 && d[off + 1] == 'P') return "image/png";
        if (len >= 3 && (d[off] & 0xff) == 0xff && (d[off + 1] & 0xff) == 0xd8) return "image/jpeg";
        if (len >= 3 && d[off] == 'G' && d[off + 1] == 'I' && d[off + 2] == 'F') return "image/gif";
        if (len >= 2 && d[off] == 'B' && d[off + 1] == 'M') return "image/bmp";
        if (len >= 12 && d[off] == 'R' && d[off + 8] == 'W' && d[off + 9] == 'E') return "image/webp";
        return null;
    }

    private static Bitmap decode(byte[] data, int off, int len, Rect outPadding, Options opts) {
        int sample = opts != null && opts.inSampleSize > 1 ? Integer.highestOneBit(opts.inSampleSize) : 1;
        int[] info = new int[3];
        if (opts != null) {
            opts.outMimeType = mimeOf(data, off, len);
            opts.outConfig = Bitmap.Config.ARGB_8888;
            opts.outColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
        }
        if (opts != null && opts.inJustDecodeBounds) {
            nDecode(data, off, len, info, true, sample);
            float scale = densityScale(opts);
            opts.outWidth = info[0] == 0 ? -1 : (int) (info[0] * scale + 0.5f);
            opts.outHeight = info[1] == 0 ? -1 : (int) (info[1] * scale + 0.5f);
            return null;
        }
        int[] px = nDecode(data, off, len, info, false, sample);
        if (px == null) {
            if (opts != null) {
                opts.outWidth = -1;
                opts.outHeight = -1;
            }
            return null;
        }
        int w = info[0], h = info[1];
        Bitmap.Config cfg = opts != null && opts.inPreferredConfig != null ? opts.inPreferredConfig : Bitmap.Config.ARGB_8888;
        if (cfg == Bitmap.Config.RGB_565 && info[2] != 0) cfg = Bitmap.Config.ARGB_8888;
        if (cfg == Bitmap.Config.HARDWARE || cfg == Bitmap.Config.RGBA_F16 || cfg == Bitmap.Config.RGBA_1010102) cfg = Bitmap.Config.ARGB_8888;
        boolean mutable = opts != null && opts.inMutable;
        Bitmap bm = new Bitmap(w, h, cfg, px, mutable);
        bm.setHasAlpha(info[2] != 0);
        byte[] chunk = NinePatch.findChunk(data, off, len);
        float scale = densityScale(opts);
        if (chunk != null && NinePatch.isNinePatchChunk(chunk)) {
            bm.mNinePatchChunk = chunk;
            if (outPadding != null) {
                NinePatch np = new NinePatch(bm, chunk);
                outPadding.set(np.mPadding);
                if (scale != 1f) outPadding.scale(scale);
            }
        } else if (outPadding != null) {
            outPadding.set(-1, -1, -1, -1);
        }
        if (opts != null) {
            int density = opts.inDensity;
            if (density != 0) {
                bm.setDensity(density);
                int target = opts.inTargetDensity;
                if (opts.inScaled && target != 0 && target != density && target != opts.inScreenDensity && chunk == null) {
                    int nw = (int) (w * scale + 0.5f), nh = (int) (h * scale + 0.5f);
                    if (nw > 0 && nh > 0 && (nw != w || nh != h)) {
                        Bitmap scaled = new Bitmap(nw, nh, cfg, null, mutable);
                        Bitmap.nScale(px, w, h, scaled.mPixels, nw, nh, true);
                        scaled.setHasAlpha(bm.hasAlpha());
                        bm = scaled;
                    }
                    bm.setDensity(target);
                } else if (opts.inScaled && target != 0 && chunk != null) {
                    // nine-patches keep their pixels; NinePatchDrawable scales divs at draw time
                    bm.setDensity(density);
                }
            }
            opts.outWidth = bm.getWidth();
            opts.outHeight = bm.getHeight();
            if (opts.inBitmap != null && opts.inBitmap.isMutable() && opts.inBitmap.mPixels.length >= bm.getWidth() * bm.getHeight()) {
                Bitmap reuse = opts.inBitmap;
                System.arraycopy(bm.mPixels, 0, reuse.mPixels, 0, bm.getWidth() * bm.getHeight());
                reuse.mWidth = bm.getWidth();
                reuse.mHeight = bm.getHeight();
                reuse.mDensity = bm.mDensity;
                return reuse;
            }
        }
        return bm;
    }

    private static float densityScale(Options opts) {
        if (opts == null || !opts.inScaled) return 1f;
        int d = opts.inDensity, t = opts.inTargetDensity;
        if (d == 0 || t == 0 || d == t || t == opts.inScreenDensity) return 1f;
        return t / (float) d;
    }

    static native int[] nDecode(byte[] data, int off, int len, int[] info, boolean boundsOnly, int sample);
}
