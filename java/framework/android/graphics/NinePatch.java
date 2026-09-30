package android.graphics;

/**
 * Nine-patch support. The chunk is aapt's serialized Res_png_9patch as stored
 * in the "npTc" PNG chunk (big endian): header, x divs, y divs, colors.
 */
public class NinePatch {
    public static class InsetStruct {
        public final Rect opticalRect;
        public final Rect outlineRect;
        public final float outlineRadius;
        public final float outlineAlpha;
        public final float decodeScale;

        InsetStruct(int opticalLeft, int opticalTop, int opticalRight, int opticalBottom, int outlineLeft, int outlineTop,
                int outlineRight, int outlineBottom, float outlineRadius, int outlineFilled, float decodeScale) {
            opticalRect = new Rect(opticalLeft, opticalTop, opticalRight, opticalBottom);
            outlineRect = new Rect(outlineLeft, outlineTop, outlineRight, outlineBottom);
            this.outlineRadius = outlineRadius;
            this.outlineAlpha = outlineFilled / 255.0f;
            this.decodeScale = decodeScale;
        }
    }

    private final Bitmap mBitmap;
    private final String mSrcName;
    final int[] mXDivs;
    final int[] mYDivs;
    final Rect mPadding = new Rect();
    private Paint mPaint;

    public NinePatch(Bitmap bitmap, byte[] chunk) { this(bitmap, chunk, null); }

    public NinePatch(Bitmap bitmap, byte[] chunk, String srcName) {
        mBitmap = bitmap;
        mSrcName = srcName;
        if (chunk != null && isNinePatchChunk(chunk)) {
            int nx = chunk[1] & 0xff, ny = chunk[2] & 0xff;
            mPadding.set(be(chunk, 12), be(chunk, 20), be(chunk, 16), be(chunk, 24));
            mXDivs = new int[nx];
            mYDivs = new int[ny];
            for (int i = 0; i < nx; i++) mXDivs[i] = be(chunk, 32 + i * 4);
            for (int i = 0; i < ny; i++) mYDivs[i] = be(chunk, 32 + nx * 4 + i * 4);
        } else {
            mXDivs = new int[0];
            mYDivs = new int[0];
        }
    }

    static int be(byte[] b, int off) {
        return ((b[off] & 0xff) << 24) | ((b[off + 1] & 0xff) << 16) | ((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff);
    }

    public String getName() { return mSrcName; }
    public Paint getPaint() { return mPaint; }
    public void setPaint(Paint p) { mPaint = p; }
    public Bitmap getBitmap() { return mBitmap; }
    public int getDensity() { return mBitmap.mDensity; }
    public int getWidth() { return mBitmap.getWidth(); }
    public int getHeight() { return mBitmap.getHeight(); }
    public final boolean hasAlpha() { return mBitmap.hasAlpha(); }
    public final Region getTransparentRegion(Rect bounds) { return null; }

    public void draw(Canvas canvas, RectF location) { draw(canvas, location, mPaint); }
    public void draw(Canvas canvas, Rect location) { draw(canvas, new RectF(location), mPaint); }
    public void draw(Canvas canvas, Rect location, Paint paint) { draw(canvas, new RectF(location), paint); }

    /** Draws the stretched bitmap; divs are scaled by the canvas density ratio. */
    public void draw(Canvas canvas, RectF dst, Paint paint) { drawScaled(canvas, dst, paint, 1f); }

    /** Draws with fixed segments scaled by 'scale' (density ratio). */
    public void drawScaled(Canvas canvas, RectF dst, Paint paint, float scale) {
        Bitmap b = mBitmap;
        int bw = b.getWidth(), bh = b.getHeight();
        float[] xs = segments(mXDivs, bw, dst.width(), scale);
        float[] ys = segments(mYDivs, bh, dst.height(), scale);
        int[] sx = srcBounds(mXDivs, bw);
        int[] sy = srcBounds(mYDivs, bh);
        Rect s = new Rect();
        RectF d = new RectF();
        for (int j = 0; j + 1 < sy.length; j++) {
            for (int i = 0; i + 1 < sx.length; i++) {
                if (sx[i + 1] <= sx[i] || sy[j + 1] <= sy[j]) continue;
                s.set(sx[i], sy[j], sx[i + 1], sy[j + 1]);
                d.set(dst.left + xs[i], dst.top + ys[j], dst.left + xs[i + 1], dst.top + ys[j + 1]);
                if (d.width() <= 0 || d.height() <= 0) continue;
                canvas.drawBitmap(b, s, d, paint);
            }
        }
    }

    private static int[] srcBounds(int[] divs, int size) {
        int[] r = new int[divs.length + 2];
        r[0] = 0;
        for (int i = 0; i < divs.length; i++) r[i + 1] = Math.min(Math.max(divs[i], 0), size);
        r[r.length - 1] = size;
        return r;
    }

    /** Destination offsets of each segment; odd segments (between div pairs) stretch. */
    private static float[] segments(int[] divs, int srcSize, float dstSize, float scale) {
        int[] b = srcBounds(divs, srcSize);
        int n = b.length - 1;
        float fixed = 0, stretch = 0;
        for (int i = 0; i < n; i++) {
            int len = b[i + 1] - b[i];
            if ((i & 1) == 1) stretch += len;
            else fixed += len * scale;
        }
        float remaining = dstSize - fixed;
        float fixedScale = scale;
        if (remaining < 0) {
            fixedScale = fixed > 0 ? scale * dstSize / fixed : 0;
            remaining = 0;
        }
        float[] out = new float[n + 1];
        float pos = 0;
        for (int i = 0; i < n; i++) {
            out[i] = pos;
            int len = b[i + 1] - b[i];
            if ((i & 1) == 1) pos += stretch > 0 ? remaining * len / stretch : 0;
            else pos += len * fixedScale;
        }
        out[n] = dstSize;
        return out;
    }

    public static boolean isNinePatchChunk(byte[] chunk) {
        if (chunk == null || chunk.length < 32) return false;
        int nx = chunk[1] & 0xff, ny = chunk[2] & 0xff, nc = chunk[3] & 0xff;
        return chunk.length >= 32 + (nx + ny + nc) * 4 && (nx & 1) == 0 && (ny & 1) == 0;
    }

    /** Extracts the npTc chunk from PNG bytes (null if absent). */
    static byte[] findChunk(byte[] png, int off, int len) {
        if (len < 16 || (png[off] & 0xff) != 0x89 || png[off + 1] != 'P') return null;
        int p = off + 8, end = off + len;
        while (p + 12 <= end) {
            int clen = be(png, p);
            if (clen < 0 || p + 12 + clen > end) return null;
            if (png[p + 4] == 'n' && png[p + 5] == 'p' && png[p + 6] == 'T' && png[p + 7] == 'c') {
                byte[] c = new byte[clen];
                System.arraycopy(png, p + 8, c, 0, clen);
                return c;
            }
            if (png[p + 4] == 'I' && png[p + 5] == 'D' && png[p + 6] == 'A' && png[p + 7] == 'T') return null;
            p += 12 + clen;
        }
        return null;
    }
}
