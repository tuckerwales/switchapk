package android.graphics;

import java.util.Arrays;

/**
 * 3x3 transformation matrix with the android.graphics.Matrix API. Values are
 * stored row-major: [MSCALE_X MSKEW_X MTRANS_X / MSKEW_Y MSCALE_Y MTRANS_Y / MPERSP_0 MPERSP_1 MPERSP_2].
 */
public class Matrix {
    public static final int MSCALE_X = 0;
    public static final int MSKEW_X = 1;
    public static final int MTRANS_X = 2;
    public static final int MSKEW_Y = 3;
    public static final int MSCALE_Y = 4;
    public static final int MTRANS_Y = 5;
    public static final int MPERSP_0 = 6;
    public static final int MPERSP_1 = 7;
    public static final int MPERSP_2 = 8;

    public static final Matrix IDENTITY_MATRIX = new Matrix() {
        void oops() { throw new IllegalStateException("Matrix can not be modified"); }
        @Override public void set(Matrix src) { oops(); }
        @Override public void reset() { oops(); }
        @Override public void setTranslate(float dx, float dy) { oops(); }
        @Override public void setScale(float sx, float sy, float px, float py) { oops(); }
        @Override public void setScale(float sx, float sy) { oops(); }
        @Override public void setRotate(float degrees, float px, float py) { oops(); }
        @Override public void setRotate(float degrees) { oops(); }
        @Override public boolean preConcat(Matrix other) { oops(); return false; }
        @Override public boolean postConcat(Matrix other) { oops(); return false; }
        @Override public void setValues(float[] values) { oops(); }
    };

    public enum ScaleToFit {
        FILL(0), START(1), CENTER(2), END(3);
        final int nativeInt;
        ScaleToFit(int nativeInt) { this.nativeInt = nativeInt; }
    }

    final float[] v = new float[9];

    public Matrix() { v[0] = v[4] = v[8] = 1; }
    public Matrix(Matrix src) { this(); if (src != null) System.arraycopy(src.v, 0, v, 0, 9); }

    public boolean isIdentity() {
        return v[0] == 1 && v[1] == 0 && v[2] == 0 && v[3] == 0 && v[4] == 1 && v[5] == 0 && v[6] == 0 && v[7] == 0 && v[8] == 1;
    }

    public boolean isAffine() { return v[6] == 0 && v[7] == 0 && v[8] == 1; }

    public boolean rectStaysRect() {
        if (!isAffine()) return false;
        return (v[1] == 0 && v[3] == 0 && v[0] != 0 && v[4] != 0) || (v[0] == 0 && v[4] == 0 && v[1] != 0 && v[3] != 0);
    }

    public void set(Matrix src) {
        if (src == null) reset();
        else System.arraycopy(src.v, 0, v, 0, 9);
    }

    @Override
    public boolean equals(Object obj) { return obj instanceof Matrix && Arrays.equals(v, ((Matrix) obj).v); }
    @Override
    public int hashCode() { return Arrays.hashCode(v); }

    public void reset() {
        Arrays.fill(v, 0);
        v[0] = v[4] = v[8] = 1;
    }

    private void setRaw(float a, float b, float c, float d, float e, float f, float g, float h, float i) {
        v[0] = a; v[1] = b; v[2] = c; v[3] = d; v[4] = e; v[5] = f; v[6] = g; v[7] = h; v[8] = i;
    }

    public void setTranslate(float dx, float dy) { setRaw(1, 0, dx, 0, 1, dy, 0, 0, 1); }
    public void setScale(float sx, float sy, float px, float py) { setRaw(sx, 0, px - sx * px, 0, sy, py - sy * py, 0, 0, 1); }
    public void setScale(float sx, float sy) { setRaw(sx, 0, 0, 0, sy, 0, 0, 0, 1); }

    public void setRotate(float degrees, float px, float py) {
        double r = Math.toRadians(degrees);
        setSinCos((float) Math.sin(r), (float) Math.cos(r), px, py);
    }

    public void setRotate(float degrees) { setRotate(degrees, 0, 0); }

    public void setSinCos(float sinValue, float cosValue, float px, float py) {
        float oneMinusCos = 1 - cosValue;
        setRaw(cosValue, -sinValue, sinValue * py + oneMinusCos * px, sinValue, cosValue, -sinValue * px + oneMinusCos * py, 0, 0, 1);
    }

    public void setSinCos(float sinValue, float cosValue) { setRaw(cosValue, -sinValue, 0, sinValue, cosValue, 0, 0, 0, 1); }
    public void setSkew(float kx, float ky, float px, float py) { setRaw(1, kx, -kx * py, ky, 1, -ky * px, 0, 0, 1); }
    public void setSkew(float kx, float ky) { setRaw(1, kx, 0, ky, 1, 0, 0, 0, 1); }

    public boolean setConcat(Matrix a, Matrix b) {
        float[] r = mul(a.v, b.v);
        System.arraycopy(r, 0, v, 0, 9);
        return true;
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        return r;
    }

    private void pre(float[] o) { System.arraycopy(mul(v, o), 0, v, 0, 9); }
    private void post(float[] o) { System.arraycopy(mul(o, v), 0, v, 0, 9); }

    private static float[] T(float dx, float dy) { return new float[] {1, 0, dx, 0, 1, dy, 0, 0, 1}; }
    private static float[] S(float sx, float sy, float px, float py) { return new float[] {sx, 0, px - sx * px, 0, sy, py - sy * py, 0, 0, 1}; }

    private static float[] R(float degrees, float px, float py) {
        Matrix m = new Matrix();
        m.setRotate(degrees, px, py);
        return m.v.clone();
    }

    private static float[] K(float kx, float ky, float px, float py) { return new float[] {1, kx, -kx * py, ky, 1, -ky * px, 0, 0, 1}; }

    public boolean preTranslate(float dx, float dy) { pre(T(dx, dy)); return true; }
    public boolean preScale(float sx, float sy, float px, float py) { pre(S(sx, sy, px, py)); return true; }
    public boolean preScale(float sx, float sy) { pre(S(sx, sy, 0, 0)); return true; }
    public boolean preRotate(float degrees, float px, float py) { pre(R(degrees, px, py)); return true; }
    public boolean preRotate(float degrees) { pre(R(degrees, 0, 0)); return true; }
    public boolean preSkew(float kx, float ky, float px, float py) { pre(K(kx, ky, px, py)); return true; }
    public boolean preSkew(float kx, float ky) { pre(K(kx, ky, 0, 0)); return true; }
    public boolean preConcat(Matrix other) { pre(other.v); return true; }
    public boolean postTranslate(float dx, float dy) { post(T(dx, dy)); return true; }
    public boolean postScale(float sx, float sy, float px, float py) { post(S(sx, sy, px, py)); return true; }
    public boolean postScale(float sx, float sy) { post(S(sx, sy, 0, 0)); return true; }
    public boolean postRotate(float degrees, float px, float py) { post(R(degrees, px, py)); return true; }
    public boolean postRotate(float degrees) { post(R(degrees, 0, 0)); return true; }
    public boolean postSkew(float kx, float ky, float px, float py) { post(K(kx, ky, px, py)); return true; }
    public boolean postSkew(float kx, float ky) { post(K(kx, ky, 0, 0)); return true; }
    public boolean postConcat(Matrix other) { post(other.v); return true; }

    public boolean setRectToRect(RectF src, RectF dst, ScaleToFit stf) {
        if (dst == null || src == null) throw new NullPointerException();
        if (src.isEmpty()) {
            reset();
            return false;
        }
        if (dst.isEmpty()) {
            setRaw(0, 0, 0, 0, 0, 0, 0, 0, 1);
            return true;
        }
        float sx = dst.width() / src.width();
        float sy = dst.height() / src.height();
        boolean xLarger = false;
        if (stf != ScaleToFit.FILL) {
            if (sx > sy) {
                xLarger = true;
                sx = sy;
            } else {
                sy = sx;
            }
        }
        float tx = dst.left - src.left * sx;
        float ty = dst.top - src.top * sy;
        if (stf == ScaleToFit.CENTER || stf == ScaleToFit.END) {
            float diff;
            if (xLarger) diff = dst.width() - src.width() * sy;
            else diff = dst.height() - src.height() * sy;
            if (stf == ScaleToFit.CENTER) diff = diff / 2;
            if (xLarger) tx += diff;
            else ty += diff;
        }
        setRaw(sx, 0, tx, 0, sy, ty, 0, 0, 1);
        return true;
    }

    public boolean setPolyToPoly(float[] src, int srcIndex, float[] dst, int dstIndex, int pointCount) {
        if (pointCount > 4) throw new IllegalArgumentException();
        if (pointCount == 0) {
            reset();
            return true;
        }
        if (pointCount == 1) {
            setTranslate(dst[dstIndex] - src[srcIndex], dst[dstIndex + 1] - src[srcIndex + 1]);
            return true;
        }
        // solve affine from up to 3 points (4th ignored: no perspective support)
        float x0 = src[srcIndex], y0 = src[srcIndex + 1], x1 = src[srcIndex + 2], y1 = src[srcIndex + 3];
        float u0 = dst[dstIndex], v0 = dst[dstIndex + 1], u1 = dst[dstIndex + 2], v1 = dst[dstIndex + 3];
        float x2, y2, u2, v2;
        if (pointCount >= 3) {
            x2 = src[srcIndex + 4]; y2 = src[srcIndex + 5]; u2 = dst[dstIndex + 4]; v2 = dst[dstIndex + 5];
        } else {
            // similarity transform: third point perpendicular
            x2 = x0 - (y1 - y0); y2 = y0 + (x1 - x0);
            u2 = u0 - (v1 - v0); v2 = v0 + (u1 - u0);
        }
        float det = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
        if (det == 0) return false;
        float a = ((u1 - u0) * (y2 - y0) - (u2 - u0) * (y1 - y0)) / det;
        float b = ((u2 - u0) * (x1 - x0) - (u1 - u0) * (x2 - x0)) / det;
        float d = ((v1 - v0) * (y2 - y0) - (v2 - v0) * (y1 - y0)) / det;
        float e = ((v2 - v0) * (x1 - x0) - (v1 - v0) * (x2 - x0)) / det;
        setRaw(a, b, u0 - a * x0 - b * y0, d, e, v0 - d * x0 - e * y0, 0, 0, 1);
        return true;
    }

    public boolean invert(Matrix inverse) {
        float[] m = v;
        double det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6]);
        if (det == 0 || Double.isNaN(det)) return false;
        double id = 1.0 / det;
        float[] r = new float[9];
        r[0] = (float) ((m[4] * m[8] - m[5] * m[7]) * id);
        r[1] = (float) ((m[2] * m[7] - m[1] * m[8]) * id);
        r[2] = (float) ((m[1] * m[5] - m[2] * m[4]) * id);
        r[3] = (float) ((m[5] * m[6] - m[3] * m[8]) * id);
        r[4] = (float) ((m[0] * m[8] - m[2] * m[6]) * id);
        r[5] = (float) ((m[2] * m[3] - m[0] * m[5]) * id);
        r[6] = (float) ((m[3] * m[7] - m[4] * m[6]) * id);
        r[7] = (float) ((m[1] * m[6] - m[0] * m[7]) * id);
        r[8] = (float) ((m[0] * m[4] - m[1] * m[3]) * id);
        if (inverse != null) System.arraycopy(r, 0, inverse.v, 0, 9);
        return true;
    }

    public void mapPoints(float[] dst, int dstIndex, float[] src, int srcIndex, int pointCount) {
        float[] tmp = new float[pointCount * 2];
        for (int i = 0; i < pointCount; i++) {
            float x = src[srcIndex + i * 2], y = src[srcIndex + i * 2 + 1];
            float w = v[6] * x + v[7] * y + v[8];
            if (w == 0) w = 1;
            tmp[i * 2] = (v[0] * x + v[1] * y + v[2]) / w;
            tmp[i * 2 + 1] = (v[3] * x + v[4] * y + v[5]) / w;
        }
        System.arraycopy(tmp, 0, dst, dstIndex, pointCount * 2);
    }

    public void mapVectors(float[] dst, int dstIndex, float[] src, int srcIndex, int vectorCount) {
        float[] tmp = new float[vectorCount * 2];
        for (int i = 0; i < vectorCount; i++) {
            float x = src[srcIndex + i * 2], y = src[srcIndex + i * 2 + 1];
            tmp[i * 2] = v[0] * x + v[1] * y;
            tmp[i * 2 + 1] = v[3] * x + v[4] * y;
        }
        System.arraycopy(tmp, 0, dst, dstIndex, vectorCount * 2);
    }

    public void mapPoints(float[] dst, float[] src) {
        if (dst.length != src.length) throw new ArrayIndexOutOfBoundsException();
        mapPoints(dst, 0, src, 0, dst.length >> 1);
    }

    public void mapVectors(float[] dst, float[] src) {
        if (dst.length != src.length) throw new ArrayIndexOutOfBoundsException();
        mapVectors(dst, 0, src, 0, dst.length >> 1);
    }

    public void mapPoints(float[] pts) { mapPoints(pts, 0, pts, 0, pts.length >> 1); }
    public void mapVectors(float[] vecs) { mapVectors(vecs, 0, vecs, 0, vecs.length >> 1); }

    public boolean mapRect(RectF dst, RectF src) {
        float[] p = {src.left, src.top, src.right, src.top, src.right, src.bottom, src.left, src.bottom};
        mapPoints(p);
        float l = Math.min(Math.min(p[0], p[2]), Math.min(p[4], p[6]));
        float r = Math.max(Math.max(p[0], p[2]), Math.max(p[4], p[6]));
        float t = Math.min(Math.min(p[1], p[3]), Math.min(p[5], p[7]));
        float b = Math.max(Math.max(p[1], p[3]), Math.max(p[5], p[7]));
        dst.set(l, t, r, b);
        return rectStaysRect();
    }

    public boolean mapRect(RectF rect) { return mapRect(rect, rect); }

    public float mapRadius(float radius) {
        float[] vec = {radius, 0, 0, radius};
        mapVectors(vec);
        float d0 = (float) Math.hypot(vec[0], vec[1]);
        float d1 = (float) Math.hypot(vec[2], vec[3]);
        return (float) Math.sqrt(d0 * d1);
    }

    public void getValues(float[] values) {
        if (values.length < 9) throw new ArrayIndexOutOfBoundsException();
        System.arraycopy(v, 0, values, 0, 9);
    }

    public void setValues(float[] values) {
        if (values.length < 9) throw new ArrayIndexOutOfBoundsException();
        System.arraycopy(values, 0, v, 0, 9);
    }

    @Override
    public String toString() { return "Matrix{" + toShortString() + "}"; }

    public String toShortString() {
        return "[" + v[0] + ", " + v[1] + ", " + v[2] + "][" + v[3] + ", " + v[4] + ", " + v[5] + "][" + v[6] + ", " + v[7] + ", " + v[8] + "]";
    }

    public void dump(java.io.PrintWriter pw) { pw.print(toShortString()); }

    /** Affine part in the renderer's order: a, b, c, d, e, f (x' = a*x + c*y + e). */
    void toAffine(float[] out6) {
        out6[0] = v[0];
        out6[1] = v[3];
        out6[2] = v[1];
        out6[3] = v[4];
        out6[4] = v[2];
        out6[5] = v[5];
    }

    void fromAffine(float[] m6) { setRaw(m6[0], m6[2], m6[4], m6[1], m6[3], m6[5], 0, 0, 1); }
}
