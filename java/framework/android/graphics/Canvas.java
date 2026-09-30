package android.graphics;

import android.text.TextUtils;
import java.util.ArrayList;

/**
 * Software canvas. State (matrix, clip, layers) is kept here; pixels are
 * drawn by the native renderer, which reads mPixels/mWidth/mHeight/mMatrix/
 * mClip/mClipMask directly.
 */
public class Canvas {
    public static final int ALL_SAVE_FLAG = 0x1F;
    public static final int MATRIX_SAVE_FLAG = 0x01;
    public static final int CLIP_SAVE_FLAG = 0x02;
    public static final int HAS_ALPHA_LAYER_SAVE_FLAG = 0x04;
    public static final int FULL_COLOR_LAYER_SAVE_FLAG = 0x08;
    public static final int CLIP_TO_LAYER_SAVE_FLAG = 0x10;

    public enum EdgeType {
        BW(0), AA(1);
        EdgeType(int nativeInt) { this.nativeInt = nativeInt; }
        final int nativeInt;
    }

    public enum VertexMode { TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN }

    // ---- read by natives ----
    int[] mPixels;
    int mWidth;
    int mHeight;
    float[] mMatrix = {1, 0, 0, 1, 0, 0};
    int[] mClip = new int[4];
    byte[] mClipMask;

    private Bitmap mBitmap;
    private int mDensity = Bitmap.DENSITY_NONE;
    private DrawFilter mDrawFilter;
    private boolean mMaskShared;

    private static final class State {
        float[] matrix;
        int[] clip;
        byte[] mask;
        // layer
        boolean layer;
        int[] parentPixels;
        int parentWidth, parentHeight;
        byte[] parentMask;
        int layerX, layerY;
        Paint layerPaint;
    }

    private final ArrayList<State> mStack = new ArrayList<State>();

    public Canvas() {}

    public Canvas(Bitmap bitmap) {
        if (!bitmap.isMutable()) throw new IllegalStateException("Immutable bitmap passed to Canvas constructor");
        setBitmap(bitmap);
        mDensity = bitmap.mDensity;
    }

    public boolean isHardwareAccelerated() { return false; }

    public void setBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isMutable()) throw new IllegalStateException();
        mBitmap = bitmap;
        mStack.clear();
        mMatrix = new float[] {1, 0, 0, 1, 0, 0};
        mClipMask = null;
        if (bitmap == null) {
            mPixels = null;
            mWidth = mHeight = 0;
        } else {
            mPixels = bitmap.mPixels;
            mWidth = bitmap.mWidth;
            mHeight = bitmap.mHeight;
            mDensity = bitmap.mDensity;
        }
        mClip = new int[] {0, 0, mWidth, mHeight};
    }

    /** Framework: renders into a raw pixel buffer (window surfaces). */
    public void setTarget(int[] pixels, int width, int height) {
        mBitmap = null;
        mStack.clear();
        mPixels = pixels;
        mWidth = width;
        mHeight = height;
        mMatrix = new float[] {1, 0, 0, 1, 0, 0};
        mClip = new int[] {0, 0, width, height};
        mClipMask = null;
    }

    public void enableZ() {}
    public void disableZ() {}
    public boolean isOpaque() { return mBitmap != null && !mBitmap.hasAlpha(); }
    public int getWidth() { return mWidth; }
    public int getHeight() { return mHeight; }
    public int getDensity() { return mDensity; }
    public void setDensity(int density) { mDensity = density; }
    public void setScreenDensity(int density) {}
    public int getMaximumBitmapWidth() { return 16384; }
    public int getMaximumBitmapHeight() { return 16384; }

    // ---- save / restore ----

    private State pushState() {
        State s = new State();
        s.matrix = mMatrix.clone();
        s.clip = mClip.clone();
        s.mask = mClipMask;
        mMaskShared = mClipMask != null;
        mStack.add(s);
        return s;
    }

    public int save() {
        pushState();
        return mStack.size();
    }

    public int save(int saveFlags) { return save(); }

    public int saveLayer(RectF bounds, Paint paint, int saveFlags) {
        if (bounds == null) return saveLayer(0, 0, 0, 0, paint, saveFlags, true);
        return saveLayer(bounds.left, bounds.top, bounds.right, bounds.bottom, paint, saveFlags, false);
    }

    public int saveLayer(RectF bounds, Paint paint) { return saveLayer(bounds, paint, ALL_SAVE_FLAG); }

    public int saveLayer(float left, float top, float right, float bottom, Paint paint, int saveFlags) {
        return saveLayer(left, top, right, bottom, paint, saveFlags, false);
    }

    public int saveLayer(float left, float top, float right, float bottom, Paint paint) {
        return saveLayer(left, top, right, bottom, paint, ALL_SAVE_FLAG, false);
    }

    private int saveLayer(float left, float top, float right, float bottom, Paint paint, int flags, boolean unbounded) {
        State s = pushState();
        int[] dev = unbounded ? mClip.clone() : deviceBounds(left, top, right, bottom);
        int l = Math.max(dev[0], mClip[0]), t = Math.max(dev[1], mClip[1]);
        int r = Math.min(dev[2], mClip[2]), b = Math.min(dev[3], mClip[3]);
        if (r <= l || b <= t) {
            l = t = 0;
            r = b = 1;
        }
        int w = r - l, h = b - t;
        s.layer = true;
        s.parentPixels = mPixels;
        s.parentWidth = mWidth;
        s.parentHeight = mHeight;
        s.parentMask = mClipMask;
        s.layerX = l;
        s.layerY = t;
        s.layerPaint = paint != null ? new Paint(paint) : null;
        mPixels = new int[w * h];
        mWidth = w;
        mHeight = h;
        mClipMask = null;
        mMatrix[4] -= l;
        mMatrix[5] -= t;
        mClip = new int[] {0, 0, w, h};
        return mStack.size();
    }

    public int saveLayerAlpha(RectF bounds, int alpha, int saveFlags) {
        Paint p = new Paint();
        p.setAlpha(Math.min(Math.max(alpha, 0), 255));
        return saveLayer(bounds, p, saveFlags);
    }

    public int saveLayerAlpha(RectF bounds, int alpha) { return saveLayerAlpha(bounds, alpha, ALL_SAVE_FLAG); }

    public int saveLayerAlpha(float left, float top, float right, float bottom, int alpha, int saveFlags) {
        Paint p = new Paint();
        p.setAlpha(Math.min(Math.max(alpha, 0), 255));
        return saveLayer(left, top, right, bottom, p, saveFlags, false);
    }

    public int saveLayerAlpha(float left, float top, float right, float bottom, int alpha) {
        return saveLayerAlpha(left, top, right, bottom, alpha, ALL_SAVE_FLAG);
    }

    public void restore() {
        if (mStack.isEmpty()) throw new IllegalStateException("Underflow in restore - more restores than saves");
        State s = mStack.remove(mStack.size() - 1);
        if (s.layer) {
            int[] layerPx = mPixels;
            int lw = mWidth, lh = mHeight;
            mPixels = s.parentPixels;
            mWidth = s.parentWidth;
            mHeight = s.parentHeight;
            mMatrix = new float[] {1, 0, 0, 1, 0, 0};
            mClip = s.clip;
            mClipMask = s.parentMask;
            Bitmap layer = new Bitmap(lw, lh, Bitmap.Config.ARGB_8888, layerPx, true);
            Paint p = s.layerPaint != null ? s.layerPaint : new Paint();
            nDrawBitmapRect(this, layer, 0, 0, lw, lh, s.layerX, s.layerY, s.layerX + lw, s.layerY + lh, p);
        }
        mMatrix = s.matrix;
        mClip = s.clip;
        mClipMask = s.mask;
        mMaskShared = false;
        for (State st : mStack) if (st.mask == mClipMask && mClipMask != null) mMaskShared = true;
    }

    public int getSaveCount() { return mStack.size() + 1; }

    public void restoreToCount(int saveCount) {
        if (saveCount < 1) throw new IllegalArgumentException("Underflow in restoreToCount - more restores than saves");
        while (mStack.size() + 1 > saveCount) restore();
    }

    // ---- matrix ----

    private void preConcat(float a, float b, float c, float d, float e, float f) {
        float[] m = mMatrix;
        float na = m[0] * a + m[2] * b;
        float nb = m[1] * a + m[3] * b;
        float nc = m[0] * c + m[2] * d;
        float nd = m[1] * c + m[3] * d;
        float ne = m[0] * e + m[2] * f + m[4];
        float nf = m[1] * e + m[3] * f + m[5];
        m[0] = na; m[1] = nb; m[2] = nc; m[3] = nd; m[4] = ne; m[5] = nf;
    }

    public void translate(float dx, float dy) {
        if (dx == 0 && dy == 0) return;
        mMatrix[4] += mMatrix[0] * dx + mMatrix[2] * dy;
        mMatrix[5] += mMatrix[1] * dx + mMatrix[3] * dy;
    }

    public void scale(float sx, float sy) {
        if (sx == 1 && sy == 1) return;
        preConcat(sx, 0, 0, sy, 0, 0);
    }

    public final void scale(float sx, float sy, float px, float py) {
        if (sx == 1 && sy == 1) return;
        translate(px, py);
        scale(sx, sy);
        translate(-px, -py);
    }

    public void rotate(float degrees) {
        if (degrees == 0) return;
        double r = Math.toRadians(degrees);
        float c = (float) Math.cos(r), s = (float) Math.sin(r);
        preConcat(c, s, -s, c, 0, 0);
    }

    public final void rotate(float degrees, float px, float py) {
        if (degrees == 0) return;
        translate(px, py);
        rotate(degrees);
        translate(-px, -py);
    }

    public void skew(float sx, float sy) {
        if (sx == 0 && sy == 0) return;
        preConcat(1, sy, sx, 1, 0, 0);
    }

    public void concat(Matrix matrix) {
        if (matrix == null || matrix.isIdentity()) return;
        float[] a = new float[6];
        matrix.toAffine(a);
        preConcat(a[0], a[1], a[2], a[3], a[4], a[5]);
    }

    public void concat(android.graphics.Matrix44 m) {}

    public void setMatrix(Matrix matrix) {
        float[] layerOffset = currentLayerOffset();
        if (matrix == null) {
            mMatrix = new float[] {1, 0, 0, 1, -layerOffset[0], -layerOffset[1]};
        } else {
            matrix.toAffine(mMatrix);
            mMatrix[4] -= layerOffset[0];
            mMatrix[5] -= layerOffset[1];
        }
    }

    private float[] currentLayerOffset() {
        float x = 0, y = 0;
        for (State s : mStack) {
            if (s.layer) {
                x += s.layerX;
                y += s.layerY;
            }
        }
        return new float[] {x, y};
    }

    @Deprecated
    public void getMatrix(Matrix ctm) {
        float[] m = mMatrix.clone();
        float[] off = currentLayerOffset();
        m[4] += off[0];
        m[5] += off[1];
        ctm.fromAffine(m);
    }

    @Deprecated
    public final Matrix getMatrix() {
        Matrix m = new Matrix();
        getMatrix(m);
        return m;
    }

    // ---- clipping ----

    private void mapPoint(float x, float y, float[] out, int off) {
        out[off] = mMatrix[0] * x + mMatrix[2] * y + mMatrix[4];
        out[off + 1] = mMatrix[1] * x + mMatrix[3] * y + mMatrix[5];
    }

    private boolean axisAligned() { return mMatrix[1] == 0 && mMatrix[2] == 0; }

    private int[] deviceBounds(float l, float t, float r, float b) {
        float[] p = new float[8];
        mapPoint(l, t, p, 0);
        mapPoint(r, t, p, 2);
        mapPoint(r, b, p, 4);
        mapPoint(l, b, p, 6);
        float minx = Math.min(Math.min(p[0], p[2]), Math.min(p[4], p[6]));
        float maxx = Math.max(Math.max(p[0], p[2]), Math.max(p[4], p[6]));
        float miny = Math.min(Math.min(p[1], p[3]), Math.min(p[5], p[7]));
        float maxy = Math.max(Math.max(p[1], p[3]), Math.max(p[5], p[7]));
        return new int[] {(int) Math.floor(minx + 0.001f), (int) Math.floor(miny + 0.001f), (int) Math.ceil(maxx - 0.001f), (int) Math.ceil(maxy - 0.001f)};
    }

    private int[] roundedDeviceRect(float l, float t, float r, float b) {
        float[] p = new float[4];
        mapPoint(l, t, p, 0);
        mapPoint(r, b, p, 2);
        return new int[] {Math.round(Math.min(p[0], p[2])), Math.round(Math.min(p[1], p[3])), Math.round(Math.max(p[0], p[2])), Math.round(Math.max(p[1], p[3]))};
    }

    private byte[] writableMask() {
        if (mClipMask == null) {
            mClipMask = new byte[mWidth * mHeight];
            for (int y = Math.max(0, mClip[1]); y < Math.min(mHeight, mClip[3]); y++) {
                java.util.Arrays.fill(mClipMask, y * mWidth + Math.max(0, mClip[0]), y * mWidth + Math.min(mWidth, mClip[2]), (byte) 255);
            }
            mMaskShared = false;
        } else if (mMaskShared) {
            mClipMask = mClipMask.clone();
            mMaskShared = false;
        }
        return mClipMask;
    }

    private boolean clipRectInternal(float left, float top, float right, float bottom, Region.Op op) {
        if (op == Region.Op.INTERSECT || op == null) {
            if (axisAligned()) {
                int[] d = roundedDeviceRect(left, top, right, bottom);
                mClip[0] = Math.max(mClip[0], d[0]);
                mClip[1] = Math.max(mClip[1], d[1]);
                mClip[2] = Math.min(mClip[2], d[2]);
                mClip[3] = Math.min(mClip[3], d[3]);
            } else {
                Path p = new Path();
                p.addRect(left, top, right, bottom, Path.Direction.CW);
                clipPathInternal(p, true);
            }
        } else if (op == Region.Op.DIFFERENCE) {
            Path p = new Path();
            p.addRect(left, top, right, bottom, Path.Direction.CW);
            clipPathInternal(p, false);
        } else if (op == Region.Op.REPLACE) {
            int[] d = axisAligned() ? roundedDeviceRect(left, top, right, bottom) : deviceBounds(left, top, right, bottom);
            mClip = new int[] {Math.max(0, d[0]), Math.max(0, d[1]), Math.min(mWidth, d[2]), Math.min(mHeight, d[3])};
            mClipMask = null;
        } else if (op == Region.Op.UNION || op == Region.Op.XOR || op == Region.Op.REVERSE_DIFFERENCE) {
            int[] d = deviceBounds(left, top, right, bottom);
            mClip[0] = Math.max(0, Math.min(mClip[0], d[0]));
            mClip[1] = Math.max(0, Math.min(mClip[1], d[1]));
            mClip[2] = Math.min(mWidth, Math.max(mClip[2], d[2]));
            mClip[3] = Math.min(mHeight, Math.max(mClip[3], d[3]));
            mClipMask = null;
        }
        return mClip[0] < mClip[2] && mClip[1] < mClip[3];
    }

    private boolean clipPathInternal(Path path, boolean intersect) {
        if (mWidth <= 0 || mHeight <= 0) return false;
        if (intersect) {
            RectF b = new RectF();
            path.computeBounds(b, true);
            int[] d = deviceBounds(b.left, b.top, b.right, b.bottom);
            if (!path.isInverseFillType()) {
                mClip[0] = Math.max(mClip[0], d[0]);
                mClip[1] = Math.max(mClip[1], d[1]);
                mClip[2] = Math.min(mClip[2], d[2]);
                mClip[3] = Math.min(mClip[3], d[3]);
            }
            if (path.isRect(null) && axisAligned()) return mClip[0] < mClip[2] && mClip[1] < mClip[3];
        }
        if (mClip[0] >= mClip[2] || mClip[1] >= mClip[3]) return false;
        byte[] mask = writableMask();
        nClipPathMask(this, mask, path.mVerbs, path.mVerbCount, path.mPts, path.mPtCount, path.getFillType().nativeInt, intersect);
        return true;
    }

    @Deprecated
    public boolean clipRect(RectF rect, Region.Op op) { return clipRectInternal(rect.left, rect.top, rect.right, rect.bottom, op); }
    @Deprecated
    public boolean clipRect(Rect rect, Region.Op op) { return clipRectInternal(rect.left, rect.top, rect.right, rect.bottom, op); }
    public boolean clipRectUnion(Rect rect) { return clipRectInternal(rect.left, rect.top, rect.right, rect.bottom, Region.Op.UNION); }
    public boolean clipRect(RectF rect) { return clipRectInternal(rect.left, rect.top, rect.right, rect.bottom, Region.Op.INTERSECT); }
    public boolean clipOutRect(RectF rect) { return clipRectInternal(rect.left, rect.top, rect.right, rect.bottom, Region.Op.DIFFERENCE); }
    public boolean clipRect(Rect rect) { return clipRectInternal(rect.left, rect.top, rect.right, rect.bottom, Region.Op.INTERSECT); }
    public boolean clipOutRect(Rect rect) { return clipRectInternal(rect.left, rect.top, rect.right, rect.bottom, Region.Op.DIFFERENCE); }
    @Deprecated
    public boolean clipRect(float left, float top, float right, float bottom, Region.Op op) { return clipRectInternal(left, top, right, bottom, op); }
    public boolean clipRect(float left, float top, float right, float bottom) { return clipRectInternal(left, top, right, bottom, Region.Op.INTERSECT); }
    public boolean clipOutRect(float left, float top, float right, float bottom) { return clipRectInternal(left, top, right, bottom, Region.Op.DIFFERENCE); }
    public boolean clipRect(int left, int top, int right, int bottom) { return clipRectInternal(left, top, right, bottom, Region.Op.INTERSECT); }
    public boolean clipOutRect(int left, int top, int right, int bottom) { return clipRectInternal(left, top, right, bottom, Region.Op.DIFFERENCE); }

    @Deprecated
    public boolean clipPath(Path path, Region.Op op) {
        if (op == Region.Op.DIFFERENCE) return clipPathInternal(path, false);
        if (op == Region.Op.INTERSECT) return clipPathInternal(path, true);
        RectF b = new RectF();
        path.computeBounds(b, true);
        return clipRectInternal(b.left, b.top, b.right, b.bottom, op);
    }

    public boolean clipPath(Path path) { return clipPathInternal(path, true); }
    public boolean clipOutPath(Path path) { return clipPathInternal(path, false); }
    public boolean clipShader(Shader shader) { return true; }
    public boolean clipOutShader(Shader shader) { return true; }
    public DrawFilter getDrawFilter() { return mDrawFilter; }
    public void setDrawFilter(DrawFilter filter) { mDrawFilter = filter; }

    @Deprecated
    public boolean quickReject(RectF rect, EdgeType type) { return quickReject(rect.left, rect.top, rect.right, rect.bottom); }
    public boolean quickReject(RectF rect) { return quickReject(rect.left, rect.top, rect.right, rect.bottom); }

    @Deprecated
    public boolean quickReject(Path path, EdgeType type) { return quickReject(path); }

    public boolean quickReject(Path path) {
        RectF b = new RectF();
        path.computeBounds(b, true);
        return quickReject(b.left, b.top, b.right, b.bottom);
    }

    @Deprecated
    public boolean quickReject(float left, float top, float right, float bottom, EdgeType type) { return quickReject(left, top, right, bottom); }

    public boolean quickReject(float left, float top, float right, float bottom) {
        int[] d = deviceBounds(left, top, right, bottom);
        return d[2] <= mClip[0] || d[0] >= mClip[2] || d[3] <= mClip[1] || d[1] >= mClip[3] || mClip[0] >= mClip[2] || mClip[1] >= mClip[3];
    }

    public boolean getClipBounds(Rect bounds) {
        Matrix inv = new Matrix();
        Matrix m = new Matrix();
        m.fromAffine(mMatrix);
        if (!m.invert(inv)) {
            if (bounds != null) bounds.setEmpty();
            return false;
        }
        RectF r = new RectF(mClip[0], mClip[1], mClip[2], mClip[3]);
        inv.mapRect(r);
        if (bounds != null) bounds.set((int) Math.floor(r.left + 0.001f), (int) Math.floor(r.top + 0.001f), (int) Math.ceil(r.right - 0.001f), (int) Math.ceil(r.bottom - 0.001f));
        return mClip[0] < mClip[2] && mClip[1] < mClip[3];
    }

    public final Rect getClipBounds() {
        Rect r = new Rect();
        getClipBounds(r);
        return r;
    }

    // ---- drawing ----

    private boolean ready() { return mPixels != null && mClip[0] < mClip[2] && mClip[1] < mClip[3]; }

    /** Paint with Java-only color filters folded into its color. */
    private Paint effective(Paint paint) {
        if (paint == null) return null;
        ColorFilter jf = paint.javaColorFilter();
        if (jf == null) return paint;
        Paint p = new Paint(paint);
        p.setColorFilter(null);
        p.setColor(jf.filter(paint.getColor()));
        return p;
    }

    public void drawARGB(int a, int r, int g, int b) { drawColor(Color.argb(a, r, g, b)); }
    public void drawRGB(int r, int g, int b) { drawColor(Color.rgb(r, g, b)); }
    public void drawColor(int color) { if (ready()) nDrawColor(this, color, PorterDuff.Mode.SRC_OVER.nativeInt); }
    public void drawColor(long color) { drawColor(Color.toArgb(color)); }
    public void drawColor(int color, PorterDuff.Mode mode) { if (ready()) nDrawColor(this, color, mode.nativeInt); }
    public void drawColor(int color, BlendMode mode) { if (ready()) nDrawColor(this, color, mode.toPorterDuff()); }
    public void drawColor(long color, BlendMode mode) { drawColor(Color.toArgb(color), mode); }

    public void drawPaint(Paint paint) {
        if (!ready()) return;
        Rect r = new Rect();
        getClipBounds(r);
        Paint p = new Paint(paint);
        p.setStyle(Paint.Style.FILL);
        nDrawRect(this, r.left - 1, r.top - 1, r.right + 1, r.bottom + 1, effective(p));
    }

    public void drawPoint(float x, float y, Paint paint) { drawPoints(new float[] {x, y}, 0, 2, paint); }

    public void drawPoints(float[] pts, int offset, int count, Paint paint) {
        if (!ready()) return;
        nDrawPoints(this, pts, offset, count & ~1, effective(paint));
    }

    public void drawPoints(float[] pts, Paint paint) { drawPoints(pts, 0, pts.length, paint); }

    public void drawLine(float startX, float startY, float stopX, float stopY, Paint paint) {
        if (!ready()) return;
        if (paint.getPathEffect() != null) {
            Path p = new Path();
            p.moveTo(startX, startY);
            p.lineTo(stopX, stopY);
            Paint s = new Paint(paint);
            s.setStyle(Paint.Style.STROKE);
            drawPath(p, s);
            return;
        }
        nDrawLine(this, startX, startY, stopX, stopY, effective(paint));
    }

    public void drawLines(float[] pts, int offset, int count, Paint paint) {
        for (int i = offset; i + 3 < offset + count; i += 4) drawLine(pts[i], pts[i + 1], pts[i + 2], pts[i + 3], paint);
    }

    public void drawLines(float[] pts, Paint paint) { drawLines(pts, 0, pts.length, paint); }

    public void drawRect(RectF rect, Paint paint) { drawRect(rect.left, rect.top, rect.right, rect.bottom, paint); }
    public void drawRect(Rect r, Paint paint) { drawRect(r.left, r.top, r.right, r.bottom, paint); }

    public void drawRect(float left, float top, float right, float bottom, Paint paint) {
        if (!ready()) return;
        if (paint.getPathEffect() != null && paint.getStyle() != Paint.Style.FILL) {
            Path p = new Path();
            p.addRect(left, top, right, bottom, Path.Direction.CW);
            drawPath(p, paint);
            return;
        }
        nDrawRect(this, left, top, right, bottom, effective(paint));
    }

    public void drawOval(RectF oval, Paint paint) { drawOval(oval.left, oval.top, oval.right, oval.bottom, paint); }

    public void drawOval(float left, float top, float right, float bottom, Paint paint) {
        if (!ready()) return;
        nDrawOval(this, left, top, right, bottom, effective(paint));
    }

    public void drawCircle(float cx, float cy, float radius, Paint paint) {
        if (!ready() || radius <= 0) return;
        nDrawOval(this, cx - radius, cy - radius, cx + radius, cy + radius, effective(paint));
    }

    public void drawArc(RectF oval, float startAngle, float sweepAngle, boolean useCenter, Paint paint) {
        drawArc(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle, useCenter, paint);
    }

    public void drawArc(float left, float top, float right, float bottom, float startAngle, float sweepAngle, boolean useCenter, Paint paint) {
        if (!ready()) return;
        nDrawArc(this, left, top, right, bottom, startAngle, sweepAngle, useCenter, effective(paint));
    }

    public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) { drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, paint); }

    public void drawRoundRect(float left, float top, float right, float bottom, float rx, float ry, Paint paint) {
        if (!ready()) return;
        nDrawRoundRect(this, left, top, right, bottom, rx, ry, effective(paint));
    }

    public void drawDoubleRoundRect(RectF outer, float outerRx, float outerRy, RectF inner, float innerRx, float innerRy, Paint paint) {
        Path p = new Path();
        p.addRoundRect(outer, outerRx, outerRy, Path.Direction.CW);
        p.addRoundRect(inner, innerRx, innerRy, Path.Direction.CCW);
        p.setFillType(Path.FillType.EVEN_ODD);
        drawPath(p, paint);
    }

    public void drawDoubleRoundRect(RectF outer, float[] outerRadii, RectF inner, float[] innerRadii, Paint paint) {
        Path p = new Path();
        p.addRoundRect(outer, outerRadii, Path.Direction.CW);
        p.addRoundRect(inner, innerRadii, Path.Direction.CCW);
        p.setFillType(Path.FillType.EVEN_ODD);
        drawPath(p, paint);
    }

    public void drawPath(Path path, Paint paint) {
        if (!ready() || path == null) return;
        PathEffect pe = paint.getPathEffect();
        if (pe != null && paint.getStyle() != Paint.Style.FILL) {
            Path dashed = pe.apply(path, paint);
            if (dashed != null) {
                Paint p = new Paint(paint);
                p.setPathEffect(null);
                p.setStyle(Paint.Style.STROKE);
                nDrawPath(this, dashed.mVerbs, dashed.mVerbCount, dashed.mPts, dashed.mPtCount, dashed.getFillType().nativeInt, effective(p));
                return;
            }
        }
        if (path.mVerbCount == 0) {
            if (path.isInverseFillType()) drawPaint(paint);
            return;
        }
        nDrawPath(this, path.mVerbs, path.mVerbCount, path.mPts, path.mPtCount, path.getFillType().nativeInt, effective(paint));
    }

    // ---- bitmaps ----

    private Bitmap filtered(Bitmap bitmap, Paint paint) {
        if (paint == null) return bitmap;
        ColorFilter jf = paint.javaColorFilter();
        if (jf == null) return bitmap;
        Bitmap b = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        int[] px = b.mPixels;
        for (int i = 0; i < b.mWidth * b.mHeight; i++) px[i] = jf.filter(px[i]);
        return b;
    }

    private Paint bitmapPaint(Paint paint) {
        if (paint == null) return null;
        if (paint.javaColorFilter() == null) return paint;
        Paint p = new Paint(paint);
        p.setColorFilter(null);
        return p;
    }

    public void drawBitmap(Bitmap bitmap, float left, float top, Paint paint) {
        if (!ready() || bitmap == null) return;
        if (bitmap.isRecycled()) throw new RuntimeException("Canvas: trying to use a recycled bitmap " + bitmap);
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        float dw = w, dh = h;
        if (mDensity != Bitmap.DENSITY_NONE && bitmap.mDensity != Bitmap.DENSITY_NONE && mDensity != bitmap.mDensity) {
            dw = Bitmap.scaleFromDensity(w, bitmap.mDensity, mDensity);
            dh = Bitmap.scaleFromDensity(h, bitmap.mDensity, mDensity);
        }
        if (bitmap.mNinePatchChunk != null) {
            nDrawBitmapRect(this, filtered(bitmap, paint), 0, 0, w, h, left, top, left + dw, top + dh, bitmapPaint(paint));
            return;
        }
        nDrawBitmapRect(this, filtered(bitmap, paint), 0, 0, w, h, left, top, left + dw, top + dh, bitmapPaint(paint));
    }

    public void drawBitmap(Bitmap bitmap, Rect src, RectF dst, Paint paint) {
        if (!ready() || bitmap == null) return;
        if (bitmap.isRecycled()) throw new RuntimeException("Canvas: trying to use a recycled bitmap " + bitmap);
        float sl = 0, st = 0, sr = bitmap.getWidth(), sb = bitmap.getHeight();
        if (src != null) {
            sl = src.left;
            st = src.top;
            sr = src.right;
            sb = src.bottom;
        }
        nDrawBitmapRect(this, filtered(bitmap, paint), sl, st, sr, sb, dst.left, dst.top, dst.right, dst.bottom, bitmapPaint(paint));
    }

    public void drawBitmap(Bitmap bitmap, Rect src, Rect dst, Paint paint) { drawBitmap(bitmap, src, new RectF(dst), paint); }

    @Deprecated
    public void drawBitmap(int[] colors, int offset, int stride, float x, float y, int width, int height, boolean hasAlpha, Paint paint) {
        if (!ready() || width <= 0 || height <= 0) return;
        nDrawPixels(this, colors, offset, stride, x, y, width, height, hasAlpha, effective(paint));
    }

    @Deprecated
    public void drawBitmap(int[] colors, int offset, int stride, int x, int y, int width, int height, boolean hasAlpha, Paint paint) {
        drawBitmap(colors, offset, stride, (float) x, (float) y, width, height, hasAlpha, paint);
    }

    public void drawBitmap(Bitmap bitmap, Matrix matrix, Paint paint) {
        if (!ready() || bitmap == null) return;
        float[] m = new float[6];
        (matrix != null ? matrix : Matrix.IDENTITY_MATRIX).toAffine(m);
        nDrawBitmapMatrix(this, filtered(bitmap, paint), m, bitmapPaint(paint));
    }

    public void drawBitmapMesh(Bitmap bitmap, int meshWidth, int meshHeight, float[] verts, int vertOffset, int[] colors, int colorOffset, Paint paint) {
        if (verts == null || bitmap == null) return;
        int n = (meshWidth + 1) * (meshHeight + 1);
        float minx = Float.MAX_VALUE, miny = Float.MAX_VALUE, maxx = -Float.MAX_VALUE, maxy = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float x = verts[vertOffset + i * 2], y = verts[vertOffset + i * 2 + 1];
            minx = Math.min(minx, x);
            maxx = Math.max(maxx, x);
            miny = Math.min(miny, y);
            maxy = Math.max(maxy, y);
        }
        drawBitmap(bitmap, null, new RectF(minx, miny, maxx, maxy), paint);
    }

    public void drawVertices(VertexMode mode, int vertexCount, float[] verts, int vertOffset, float[] texs, int texOffset, int[] colors, int colorOffset, short[] indices, int indexOffset, int indexCount, Paint paint) {
        if (verts == null || vertexCount < 3) return;
        int[] idx;
        int count;
        if (indices != null) {
            count = indexCount;
            idx = new int[count];
            for (int i = 0; i < count; i++) idx[i] = indices[indexOffset + i];
        } else {
            count = vertexCount;
            idx = new int[count];
            for (int i = 0; i < count; i++) idx[i] = i;
        }
        Path p = new Path();
        for (int i = 0; i + 2 < count; ) {
            int a = idx[i], b = idx[i + 1], c = idx[i + 2];
            if (mode == VertexMode.TRIANGLE_FAN) { a = idx[0]; b = idx[i + 1]; c = idx[i + 2]; }
            p.moveTo(verts[vertOffset + a * 2], verts[vertOffset + a * 2 + 1]);
            p.lineTo(verts[vertOffset + b * 2], verts[vertOffset + b * 2 + 1]);
            p.lineTo(verts[vertOffset + c * 2], verts[vertOffset + c * 2 + 1]);
            p.close();
            i += mode == VertexMode.TRIANGLES ? 3 : 1;
        }
        Paint f = new Paint(paint);
        if (colors != null) f.setColor(colors[colorOffset + idx[0]]);
        f.setStyle(Paint.Style.FILL);
        drawPath(p, f);
    }

    public void drawPicture(Picture picture) { picture.draw(this); }

    public void drawPicture(Picture picture, RectF dst) {
        save();
        translate(dst.left, dst.top);
        if (picture.getWidth() > 0 && picture.getHeight() > 0) scale(dst.width() / picture.getWidth(), dst.height() / picture.getHeight());
        drawPicture(picture);
        restore();
    }

    public void drawPicture(Picture picture, Rect dst) { drawPicture(picture, new RectF(dst)); }

    public void drawPatch(NinePatch patch, Rect dst, Paint paint) { patch.draw(this, dst, paint); }
    public void drawPatch(NinePatch patch, RectF dst, Paint paint) { patch.draw(this, dst, paint); }

    public void drawRenderNode(android.graphics.RenderNode renderNode) { renderNode.drawInto(this); }

    // ---- text ----

    public void drawText(char[] text, int index, int count, float x, float y, Paint paint) {
        if ((index | count | (index + count) | (text.length - index - count)) < 0) throw new IndexOutOfBoundsException();
        drawChars(text, index, count, x, y, paint);
    }

    public void drawText(String text, float x, float y, Paint paint) {
        char[] c = text.toCharArray();
        drawChars(c, 0, c.length, x, y, paint);
    }

    public void drawText(String text, int start, int end, float x, float y, Paint paint) {
        if ((start | end | (end - start) | (text.length() - end)) < 0) throw new IndexOutOfBoundsException();
        drawChars(text.toCharArray(), start, end - start, x, y, paint);
    }

    public void drawText(CharSequence text, int start, int end, float x, float y, Paint paint) {
        if ((start | end | (end - start) | (text.length() - end)) < 0) throw new IndexOutOfBoundsException();
        char[] c = Paint.chars(text, start, end);
        drawChars(c, 0, c.length, x, y, paint);
    }

    public void drawTextRun(char[] text, int index, int count, int contextIndex, int contextCount, float x, float y, boolean isRtl, Paint paint) {
        drawChars(text, index, count, x, y, paint);
    }

    public void drawTextRun(CharSequence text, int start, int end, int contextStart, int contextEnd, float x, float y, boolean isRtl, Paint paint) {
        drawText(text, start, end, x, y, paint);
    }

    public void drawTextRun(android.graphics.text.MeasuredText text, int start, int end, int contextStart, int contextEnd, float x, float y, boolean isRtl, Paint paint) {}

    @Deprecated
    public void drawPosText(char[] text, int index, int count, float[] pos, Paint paint) {
        for (int i = 0; i < count; i++) drawChars(text, index + i, 1, pos[i * 2], pos[i * 2 + 1], paint);
    }

    @Deprecated
    public void drawPosText(String text, float[] pos, Paint paint) { drawPosText(text.toCharArray(), 0, text.length(), pos, paint); }

    public void drawTextOnPath(char[] text, int index, int count, Path path, float hOffset, float vOffset, Paint paint) {
        PathMeasure pm = new PathMeasure(path, false);
        float[] widths = new float[count];
        paint.getTextWidths(text, index, count, widths);
        float d = hOffset;
        float[] pos = new float[2], tan = new float[2];
        Paint p = new Paint(paint);
        p.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < count; i++) {
            float mid = d + widths[i] / 2;
            if (mid > pm.getLength()) break;
            if (pm.getPosTan(mid, pos, tan)) {
                save();
                translate(pos[0], pos[1]);
                rotate((float) Math.toDegrees(Math.atan2(tan[1], tan[0])));
                drawChars(text, index + i, 1, -widths[i] / 2, vOffset, p);
                restore();
            }
            d += widths[i];
        }
    }

    public void drawTextOnPath(String text, Path path, float hOffset, float vOffset, Paint paint) {
        drawTextOnPath(text.toCharArray(), 0, text.length(), path, hOffset, vOffset, paint);
    }

    private void drawChars(char[] text, int index, int count, float x, float y, Paint paint) {
        if (!ready() || count <= 0) return;
        Paint.Align align = paint.getTextAlign();
        float width = -1;
        if (align != Paint.Align.LEFT || paint.isUnderlineText() || paint.isStrikeThruText()) {
            width = paint.measureChars(text, index, count, null, 0);
            if (align == Paint.Align.CENTER) x -= width / 2;
            else if (align == Paint.Align.RIGHT) x -= width;
        }
        Paint draw = effective(paint);
        if (draw.effectiveSkew() != draw.mTextSkewX) {
            draw = new Paint(draw);
            draw.mTextSkewX = paint.effectiveSkew();
        }
        if (paint.hasShadowLayer()) {
            Paint sp = new Paint(draw);
            int sc = paint.getShadowLayerColor();
            int alpha = (sc >>> 24) * (paint.getAlpha()) / 255;
            sp.setColor((sc & 0xffffff) | (alpha << 24));
            sp.setShader(null);
            sp.clearShadowLayer();
            drawGlyphs(text, index, count, x + paint.getShadowLayerDx(), y + paint.getShadowLayerDy(), sp);
        }
        drawGlyphs(text, index, count, x, y, draw);
        if (paint.isUnderlineText()) {
            float t = paint.getUnderlineThickness(), pos = y + paint.getUnderlinePosition();
            Paint lp = new Paint(draw);
            lp.setStyle(Paint.Style.FILL);
            nDrawRect(this, x, pos, x + width, pos + t, lp);
        }
        if (paint.isStrikeThruText()) {
            float t = paint.getStrikeThruThickness(), pos = y + paint.getStrikeThruPosition();
            Paint lp = new Paint(draw);
            lp.setStyle(Paint.Style.FILL);
            nDrawRect(this, x, pos - t / 2, x + width, pos + t / 2, lp);
        }
    }

    private void drawGlyphs(char[] text, int index, int count, float x, float y, Paint paint) {
        if (paint.getLetterSpacing() == 0 && paint.getWordSpacing() == 0) {
            nDrawText(this, text, index, count, x, y, paint);
            return;
        }
        float[] widths = new float[count];
        paint.measureChars(text, index, count, widths, 0);
        float cx = x;
        for (int i = 0; i < count; i++) {
            int n = 1;
            if (Character.isHighSurrogate(text[index + i]) && i + 1 < count) n = 2;
            nDrawText(this, text, index + i, n, cx, y, paint);
            for (int k = 0; k < n; k++) cx += widths[i + k];
            i += n - 1;
        }
    }

    public void release() {}

    // ---- natives ----
    static native void nDrawColor(Canvas c, int color, int mode);
    static native void nDrawRect(Canvas c, float l, float t, float r, float b, Paint p);
    static native void nDrawRoundRect(Canvas c, float l, float t, float r, float b, float rx, float ry, Paint p);
    static native void nDrawOval(Canvas c, float l, float t, float r, float b, Paint p);
    static native void nDrawArc(Canvas c, float l, float t, float r, float b, float start, float sweep, boolean useCenter, Paint p);
    static native void nDrawLine(Canvas c, float x0, float y0, float x1, float y1, Paint p);
    static native void nDrawPoints(Canvas c, float[] pts, int offset, int count, Paint p);
    static native void nDrawPath(Canvas c, byte[] verbs, int nverbs, float[] pts, int npts, int fillType, Paint p);
    static native void nDrawBitmapRect(Canvas c, Bitmap b, float sl, float st, float sr, float sb, float l, float t, float r, float bt, Paint p);
    static native void nDrawBitmapMatrix(Canvas c, Bitmap b, float[] m, Paint p);
    static native void nDrawPixels(Canvas c, int[] colors, int offset, int stride, float x, float y, int w, int h, boolean alpha, Paint p);
    static native void nDrawText(Canvas c, char[] text, int start, int count, float x, float y, Paint p);
    static native void nClipPathMask(Canvas c, byte[] mask, byte[] verbs, int nverbs, float[] pts, int npts, int fillType, boolean intersect);
}
