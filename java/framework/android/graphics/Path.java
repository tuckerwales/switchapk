package android.graphics;

import java.util.Arrays;

/**
 * A geometric path. Stored as verbs (MOVE, LINE, QUAD, CUBIC, CLOSE) and a
 * flat point array; the renderer consumes these directly.
 */
public class Path {
    static final byte VERB_MOVE = 0;
    static final byte VERB_LINE = 1;
    static final byte VERB_QUAD = 2;
    static final byte VERB_CUBIC = 3;
    static final byte VERB_CLOSE = 4;

    byte[] mVerbs = new byte[16];
    int mVerbCount;
    float[] mPts = new float[32];
    int mPtCount; // number of floats
    private FillType mFillType = FillType.WINDING;
    private boolean mHasMove;
    private float mStartX, mStartY; // start of current contour
    private boolean mIsSimpleRect;

    public enum FillType {
        WINDING(0), EVEN_ODD(1), INVERSE_WINDING(2), INVERSE_EVEN_ODD(3);
        final int nativeInt;
        FillType(int ni) { nativeInt = ni; }
    }

    public enum Op { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }

    public enum Direction {
        CW(0), CCW(1);
        final int nativeInt;
        Direction(int ni) { nativeInt = ni; }
    }

    public Path() {}

    public Path(Path src) {
        if (src != null) set(src);
    }

    public void reset() {
        mVerbCount = 0;
        mPtCount = 0;
        mHasMove = false;
        mFillType = FillType.WINDING;
        mIsSimpleRect = false;
    }

    public void rewind() {
        mVerbCount = 0;
        mPtCount = 0;
        mHasMove = false;
        mIsSimpleRect = false;
    }

    public void set(Path src) {
        if (this == src) return;
        mVerbs = Arrays.copyOf(src.mVerbs, Math.max(src.mVerbs.length, 16));
        mVerbCount = src.mVerbCount;
        mPts = Arrays.copyOf(src.mPts, Math.max(src.mPts.length, 32));
        mPtCount = src.mPtCount;
        mFillType = src.mFillType;
        mHasMove = src.mHasMove;
        mStartX = src.mStartX;
        mStartY = src.mStartY;
        mIsSimpleRect = src.mIsSimpleRect;
    }

    public boolean op(Path path, Op op) { return op(this, path, op); }

    public boolean op(Path path1, Path path2, Op op) {
        // Boolean operations are approximated: UNION and XOR concatenate the
        // contours (exact for disjoint shapes); INTERSECT/DIFFERENCE keep path1.
        Path result = new Path(path1);
        switch (op) {
            case UNION:
            case XOR:
                result.addPath(path2);
                if (op == Op.XOR) result.setFillType(FillType.EVEN_ODD);
                break;
            case REVERSE_DIFFERENCE:
                result.set(path2);
                break;
            default:
                break;
        }
        set(result);
        return true;
    }

    public boolean isConvex() { return mIsSimpleRect; }
    public FillType getFillType() { return mFillType; }
    public void setFillType(FillType ft) { mFillType = ft; }
    public boolean isInverseFillType() { return mFillType == FillType.INVERSE_WINDING || mFillType == FillType.INVERSE_EVEN_ODD; }

    public void toggleInverseFillType() {
        switch (mFillType) {
            case WINDING: mFillType = FillType.INVERSE_WINDING; break;
            case EVEN_ODD: mFillType = FillType.INVERSE_EVEN_ODD; break;
            case INVERSE_WINDING: mFillType = FillType.WINDING; break;
            case INVERSE_EVEN_ODD: mFillType = FillType.EVEN_ODD; break;
        }
    }

    public boolean isEmpty() { return mVerbCount == 0; }

    public boolean isRect(RectF rect) {
        if (!mIsSimpleRect) return false;
        if (rect != null) computeBounds(rect, true);
        return true;
    }

    public void computeBounds(RectF bounds, boolean exact) {
        if (mPtCount == 0) {
            bounds.set(0, 0, 0, 0);
            return;
        }
        float l = mPts[0], t = mPts[1], r = l, b = t;
        for (int i = 2; i < mPtCount; i += 2) {
            float x = mPts[i], y = mPts[i + 1];
            if (x < l) l = x;
            if (x > r) r = x;
            if (y < t) t = y;
            if (y > b) b = y;
        }
        bounds.set(l, t, r, b);
    }

    public void incReserve(int extraPtCount) {
        ensurePts(extraPtCount * 2);
    }

    private void ensurePts(int n) {
        if (mPtCount + n > mPts.length) mPts = Arrays.copyOf(mPts, Math.max(mPts.length * 2, mPtCount + n));
    }

    private void verb(byte v) {
        if (mVerbCount == mVerbs.length) mVerbs = Arrays.copyOf(mVerbs, mVerbs.length * 2);
        mVerbs[mVerbCount++] = v;
        mIsSimpleRect = false;
    }

    private void pt(float x, float y) {
        ensurePts(2);
        mPts[mPtCount++] = x;
        mPts[mPtCount++] = y;
    }

    private float lastX() { return mPtCount >= 2 ? mPts[mPtCount - 2] : 0; }
    private float lastY() { return mPtCount >= 2 ? mPts[mPtCount - 1] : 0; }

    private void injectMoveIfNeeded() {
        if (!mHasMove) {
            float x = mPtCount >= 2 ? mStartX : 0, y = mPtCount >= 2 ? mStartY : 0;
            moveTo(x, y);
        }
    }

    public void moveTo(float x, float y) {
        if (mVerbCount > 0 && mVerbs[mVerbCount - 1] == VERB_MOVE) {
            mPts[mPtCount - 2] = x;
            mPts[mPtCount - 1] = y;
        } else {
            verb(VERB_MOVE);
            pt(x, y);
        }
        mStartX = x;
        mStartY = y;
        mHasMove = true;
    }

    public void rMoveTo(float dx, float dy) { moveTo(lastX() + dx, lastY() + dy); }

    public void lineTo(float x, float y) {
        injectMoveIfNeeded();
        verb(VERB_LINE);
        pt(x, y);
    }

    public void rLineTo(float dx, float dy) {
        float lx = lastX(), ly = lastY();
        lineTo(lx + dx, ly + dy);
    }

    public void quadTo(float x1, float y1, float x2, float y2) {
        injectMoveIfNeeded();
        verb(VERB_QUAD);
        pt(x1, y1);
        pt(x2, y2);
    }

    public void rQuadTo(float dx1, float dy1, float dx2, float dy2) {
        float lx = lastX(), ly = lastY();
        quadTo(lx + dx1, ly + dy1, lx + dx2, ly + dy2);
    }

    public void conicTo(float x1, float y1, float x2, float y2, float weight) {
        // approximate the conic by a quadratic (exact for weight == 1)
        quadTo(x1, y1, x2, y2);
    }

    public void rConicTo(float dx1, float dy1, float dx2, float dy2, float weight) {
        float lx = lastX(), ly = lastY();
        conicTo(lx + dx1, ly + dy1, lx + dx2, ly + dy2, weight);
    }

    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        injectMoveIfNeeded();
        verb(VERB_CUBIC);
        pt(x1, y1);
        pt(x2, y2);
        pt(x3, y3);
    }

    public void rCubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        float lx = lastX(), ly = lastY();
        cubicTo(lx + x1, ly + y1, lx + x2, ly + y2, lx + x3, ly + y3);
    }

    public void arcTo(RectF oval, float startAngle, float sweepAngle, boolean forceMoveTo) {
        arcTo(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle, forceMoveTo);
    }

    public void arcTo(RectF oval, float startAngle, float sweepAngle) { arcTo(oval, startAngle, sweepAngle, false); }

    public void arcTo(float left, float top, float right, float bottom, float startAngle, float sweepAngle, boolean forceMoveTo) {
        float cx = (left + right) / 2, cy = (top + bottom) / 2;
        float rx = (right - left) / 2, ry = (bottom - top) / 2;
        double a0 = Math.toRadians(startAngle);
        float sx = cx + rx * (float) Math.cos(a0), sy = cy + ry * (float) Math.sin(a0);
        if (forceMoveTo || !mHasMove || mVerbCount == 0) moveTo(sx, sy);
        else lineTo(sx, sy);
        appendArc(cx, cy, rx, ry, startAngle, sweepAngle);
    }

    /** Appends cubic segments for an elliptical arc (current point must be at the arc start). */
    private void appendArc(float cx, float cy, float rx, float ry, float startAngle, float sweepAngle) {
        if (sweepAngle == 0) return;
        if (sweepAngle > 360) sweepAngle = 360;
        if (sweepAngle < -360) sweepAngle = -360;
        int segs = (int) Math.ceil(Math.abs(sweepAngle) / 90.0);
        double step = Math.toRadians(sweepAngle) / segs;
        double a = Math.toRadians(startAngle);
        double k = 4.0 / 3.0 * Math.tan(step / 4);
        for (int i = 0; i < segs; i++) {
            double c0 = Math.cos(a), s0 = Math.sin(a);
            double c1 = Math.cos(a + step), s1 = Math.sin(a + step);
            cubicTo((float) (cx + rx * (c0 - k * s0)), (float) (cy + ry * (s0 + k * c0)),
                    (float) (cx + rx * (c1 + k * s1)), (float) (cy + ry * (s1 - k * c1)),
                    (float) (cx + rx * c1), (float) (cy + ry * s1));
            a += step;
        }
    }

    public void close() {
        if (mVerbCount > 0 && mVerbs[mVerbCount - 1] != VERB_CLOSE) {
            verb(VERB_CLOSE);
            mHasMove = false;
        }
    }

    public void addRect(RectF rect, Direction dir) { addRect(rect.left, rect.top, rect.right, rect.bottom, dir); }

    public void addRect(float left, float top, float right, float bottom, Direction dir) {
        boolean wasEmpty = isEmpty();
        moveTo(left, top);
        if (dir == Direction.CCW) {
            lineTo(left, bottom);
            lineTo(right, bottom);
            lineTo(right, top);
        } else {
            lineTo(right, top);
            lineTo(right, bottom);
            lineTo(left, bottom);
        }
        close();
        mIsSimpleRect = wasEmpty;
    }

    public void addOval(RectF oval, Direction dir) { addOval(oval.left, oval.top, oval.right, oval.bottom, dir); }

    public void addOval(float left, float top, float right, float bottom, Direction dir) {
        float cx = (left + right) / 2, cy = (top + bottom) / 2, rx = (right - left) / 2, ry = (bottom - top) / 2;
        moveTo(right, cy);
        appendArc(cx, cy, rx, ry, 0, dir == Direction.CCW ? -360 : 360);
        close();
    }

    public void addCircle(float x, float y, float radius, Direction dir) { addOval(x - radius, y - radius, x + radius, y + radius, dir); }

    public void addArc(RectF oval, float startAngle, float sweepAngle) { addArc(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle); }

    public void addArc(float left, float top, float right, float bottom, float startAngle, float sweepAngle) {
        arcTo(left, top, right, bottom, startAngle, sweepAngle, true);
    }

    public void addRoundRect(RectF rect, float rx, float ry, Direction dir) { addRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, dir); }

    public void addRoundRect(float left, float top, float right, float bottom, float rx, float ry, Direction dir) {
        addRoundRect(left, top, right, bottom, new float[] {rx, ry, rx, ry, rx, ry, rx, ry}, dir);
    }

    public void addRoundRect(RectF rect, float[] radii, Direction dir) { addRoundRect(rect.left, rect.top, rect.right, rect.bottom, radii, dir); }

    public void addRoundRect(float left, float top, float right, float bottom, float[] radii, Direction dir) {
        if (radii.length < 8) throw new ArrayIndexOutOfBoundsException("radii[] needs 8 values");
        float w = right - left, h = bottom - top;
        float[] r = radii.clone();
        // scale down radii that do not fit (as Skia does)
        float scale = 1f;
        scale = fit(scale, r[0] + r[2], w);
        scale = fit(scale, r[4] + r[6], w);
        scale = fit(scale, r[1] + r[7], h);
        scale = fit(scale, r[3] + r[5], h);
        if (scale < 1f) for (int i = 0; i < 8; i++) r[i] *= scale;
        float tlx = r[0], tly = r[1], trx = r[2], try_ = r[3], brx = r[4], bry = r[5], blx = r[6], bly = r[7];
        final float k = 0.5522848f;
        if (dir == Direction.CW) {
            moveTo(left + tlx, top);
            lineTo(right - trx, top);
            if (trx > 0 || try_ > 0) cubicTo(right - trx + trx * k, top, right, top + try_ - try_ * k, right, top + try_);
            lineTo(right, bottom - bry);
            if (brx > 0 || bry > 0) cubicTo(right, bottom - bry + bry * k, right - brx + brx * k, bottom, right - brx, bottom);
            lineTo(left + blx, bottom);
            if (blx > 0 || bly > 0) cubicTo(left + blx - blx * k, bottom, left, bottom - bly + bly * k, left, bottom - bly);
            lineTo(left, top + tly);
            if (tlx > 0 || tly > 0) cubicTo(left, top + tly - tly * k, left + tlx - tlx * k, top, left + tlx, top);
        } else {
            moveTo(left + tlx, top);
            if (tlx > 0 || tly > 0) cubicTo(left + tlx - tlx * k, top, left, top + tly - tly * k, left, top + tly);
            lineTo(left, bottom - bly);
            if (blx > 0 || bly > 0) cubicTo(left, bottom - bly + bly * k, left + blx - blx * k, bottom, left + blx, bottom);
            lineTo(right - brx, bottom);
            if (brx > 0 || bry > 0) cubicTo(right - brx + brx * k, bottom, right, bottom - bry + bry * k, right, bottom - bry);
            lineTo(right, top + try_);
            if (trx > 0 || try_ > 0) cubicTo(right, top + try_ - try_ * k, right - trx + trx * k, top, right - trx, top);
        }
        close();
    }

    private static float fit(float scale, float sum, float size) {
        if (sum > size && sum > 0) return Math.min(scale, size / sum);
        return scale;
    }

    public void addPath(Path src, float dx, float dy) {
        Matrix m = new Matrix();
        m.setTranslate(dx, dy);
        addPath(src, m);
    }

    public void addPath(Path src) { addPath(src, null); }

    public void addPath(Path src, Matrix matrix) {
        Path s = src == this ? new Path(src) : src;
        float[] pts = Arrays.copyOf(s.mPts, s.mPtCount);
        if (matrix != null && !matrix.isIdentity()) matrix.mapPoints(pts);
        int p = 0;
        for (int i = 0; i < s.mVerbCount; i++) {
            switch (s.mVerbs[i]) {
                case VERB_MOVE: moveTo(pts[p], pts[p + 1]); p += 2; break;
                case VERB_LINE: lineTo(pts[p], pts[p + 1]); p += 2; break;
                case VERB_QUAD: quadTo(pts[p], pts[p + 1], pts[p + 2], pts[p + 3]); p += 4; break;
                case VERB_CUBIC: cubicTo(pts[p], pts[p + 1], pts[p + 2], pts[p + 3], pts[p + 4], pts[p + 5]); p += 6; break;
                case VERB_CLOSE: close(); break;
            }
        }
    }

    public void offset(float dx, float dy, Path dst) {
        if (dst != null && dst != this) dst.set(this);
        else dst = this;
        dst.offset(dx, dy);
    }

    public void offset(float dx, float dy) {
        for (int i = 0; i < mPtCount; i += 2) {
            mPts[i] += dx;
            mPts[i + 1] += dy;
        }
        mStartX += dx;
        mStartY += dy;
    }

    public void setLastPoint(float dx, float dy) {
        if (mPtCount >= 2) {
            mPts[mPtCount - 2] = dx;
            mPts[mPtCount - 1] = dy;
        } else {
            moveTo(dx, dy);
        }
    }

    public void transform(Matrix matrix, Path dst) {
        if (dst != null && dst != this) dst.set(this);
        else dst = this;
        dst.transform(matrix);
    }

    public void transform(Matrix matrix) {
        if (matrix == null || matrix.isIdentity() || mPtCount == 0) return;
        float[] pts = Arrays.copyOf(mPts, mPtCount);
        matrix.mapPoints(pts);
        System.arraycopy(pts, 0, mPts, 0, mPtCount);
        float[] s = {mStartX, mStartY};
        matrix.mapPoints(s);
        mStartX = s[0];
        mStartY = s[1];
        mIsSimpleRect = mIsSimpleRect && matrix.rectStaysRect();
    }

    public boolean interpolate(Path otherPath, float t, Path interpolatedPath) {
        if (!isInterpolatable(otherPath)) return false;
        interpolatedPath.set(this);
        for (int i = 0; i < mPtCount; i++) interpolatedPath.mPts[i] = otherPath.mPts[i] + (mPts[i] - otherPath.mPts[i]) * t;
        return true;
    }

    public boolean isInterpolatable(Path otherPath) {
        if (otherPath.mVerbCount != mVerbCount || otherPath.mPtCount != mPtCount) return false;
        for (int i = 0; i < mVerbCount; i++) if (mVerbs[i] != otherPath.mVerbs[i]) return false;
        return true;
    }

    public float[] approximate(float acceptableError) {
        PathMeasure pm = new PathMeasure(this, false);
        java.util.ArrayList<float[]> out = new java.util.ArrayList<float[]>();
        float total = 0;
        do {
            total += pm.getLength();
        } while (pm.nextContour());
        pm.setPath(this, false);
        float before = 0;
        do {
            float len = pm.getLength();
            float[] pos = new float[2];
            int steps = Math.max(1, (int) (len / Math.max(acceptableError * 4, 0.5f)));
            for (int i = 0; i <= steps; i++) {
                float d = len * i / steps;
                pm.getPosTan(d, pos, null);
                out.add(new float[] {total > 0 ? (before + d) / total : 0, pos[0], pos[1]});
            }
            before += len;
        } while (pm.nextContour());
        float[] r = new float[out.size() * 3];
        for (int i = 0; i < out.size(); i++) System.arraycopy(out.get(i), 0, r, i * 3, 3);
        return r;
    }

    public PathIterator getPathIterator() { return new PathIterator(this); }

    /** Flattens the path into polylines (one float[] of x,y pairs per contour; closed contours repeat the first point). */
    java.util.ArrayList<float[]> flatten(float tolerance) {
        java.util.ArrayList<float[]> contours = new java.util.ArrayList<float[]>();
        float[] cur = new float[64];
        int n = 0;
        float lx = 0, ly = 0, sx = 0, sy = 0;
        int p = 0;
        for (int i = 0; i < mVerbCount; i++) {
            switch (mVerbs[i]) {
                case VERB_MOVE:
                    if (n >= 4) contours.add(Arrays.copyOf(cur, n));
                    n = 0;
                    lx = sx = mPts[p];
                    ly = sy = mPts[p + 1];
                    p += 2;
                    cur = add(cur, n, lx, ly);
                    n += 2;
                    break;
                case VERB_LINE:
                    lx = mPts[p];
                    ly = mPts[p + 1];
                    p += 2;
                    cur = add(cur, n, lx, ly);
                    n += 2;
                    break;
                case VERB_QUAD: {
                    float x1 = mPts[p], y1 = mPts[p + 1], x2 = mPts[p + 2], y2 = mPts[p + 3];
                    p += 4;
                    float d = Math.abs(lx - 2 * x1 + x2) + Math.abs(ly - 2 * y1 + y2);
                    int segs = Math.max(1, Math.min(100, (int) Math.ceil(Math.sqrt(d / tolerance))));
                    for (int k = 1; k <= segs; k++) {
                        float t = (float) k / segs, mt = 1 - t;
                        float x = mt * mt * lx + 2 * mt * t * x1 + t * t * x2;
                        float y = mt * mt * ly + 2 * mt * t * y1 + t * t * y2;
                        cur = add(cur, n, x, y);
                        n += 2;
                    }
                    lx = x2;
                    ly = y2;
                    break;
                }
                case VERB_CUBIC: {
                    float x1 = mPts[p], y1 = mPts[p + 1], x2 = mPts[p + 2], y2 = mPts[p + 3], x3 = mPts[p + 4], y3 = mPts[p + 5];
                    p += 6;
                    float d = Math.abs(lx - 2 * x1 + x2) + Math.abs(ly - 2 * y1 + y2) + Math.abs(x1 - 2 * x2 + x3) + Math.abs(y1 - 2 * y2 + y3);
                    int segs = Math.max(1, Math.min(100, (int) Math.ceil(Math.sqrt(d / tolerance))));
                    for (int k = 1; k <= segs; k++) {
                        float t = (float) k / segs, mt = 1 - t;
                        float x = mt * mt * mt * lx + 3 * mt * mt * t * x1 + 3 * mt * t * t * x2 + t * t * t * x3;
                        float y = mt * mt * mt * ly + 3 * mt * mt * t * y1 + 3 * mt * t * t * y2 + t * t * t * y3;
                        cur = add(cur, n, x, y);
                        n += 2;
                    }
                    lx = x3;
                    ly = y3;
                    break;
                }
                case VERB_CLOSE:
                    if (n >= 2 && (cur[n - 2] != sx || cur[n - 1] != sy)) {
                        cur = add(cur, n, sx, sy);
                        n += 2;
                    }
                    if (n >= 4) contours.add(Arrays.copyOf(cur, n));
                    n = 0;
                    lx = sx;
                    ly = sy;
                    cur = add(cur, n, sx, sy);
                    n += 2;
                    break;
            }
        }
        if (n >= 4) contours.add(Arrays.copyOf(cur, n));
        return contours;
    }

    private static float[] add(float[] a, int n, float x, float y) {
        if (n + 2 > a.length) a = Arrays.copyOf(a, a.length * 2);
        a[n] = x;
        a[n + 1] = y;
        return a;
    }

    /** API 34 path iteration. */
    public static class PathIterator implements java.util.Iterator<PathIterator.Segment> {
        public static final int VERB_MOVE = 0;
        public static final int VERB_LINE = 1;
        public static final int VERB_QUAD = 2;
        public static final int VERB_CONIC = 3;
        public static final int VERB_CUBIC = 4;
        public static final int VERB_CLOSE = 5;
        public static final int VERB_DONE = 6;

        private final Path mPath;
        private int mVerb, mPt;
        private float mLastX, mLastY, mStartX, mStartY;

        PathIterator(Path p) { mPath = new Path(p); }

        public boolean hasNext() { return mVerb < mPath.mVerbCount; }

        public int peek() {
            if (!hasNext()) return VERB_DONE;
            return map(mPath.mVerbs[mVerb]);
        }

        private static int map(byte v) {
            switch (v) {
                case Path.VERB_MOVE: return VERB_MOVE;
                case Path.VERB_LINE: return VERB_LINE;
                case Path.VERB_QUAD: return VERB_QUAD;
                case Path.VERB_CUBIC: return VERB_CUBIC;
                default: return VERB_CLOSE;
            }
        }

        public int next(float[] points, int offset) {
            if (!hasNext()) return VERB_DONE;
            byte v = mPath.mVerbs[mVerb++];
            float[] p = mPath.mPts;
            switch (v) {
                case Path.VERB_MOVE:
                    points[offset] = mStartX = mLastX = p[mPt];
                    points[offset + 1] = mStartY = mLastY = p[mPt + 1];
                    mPt += 2;
                    break;
                case Path.VERB_LINE:
                    points[offset] = mLastX; points[offset + 1] = mLastY;
                    points[offset + 2] = mLastX = p[mPt]; points[offset + 3] = mLastY = p[mPt + 1];
                    mPt += 2;
                    break;
                case Path.VERB_QUAD:
                    points[offset] = mLastX; points[offset + 1] = mLastY;
                    System.arraycopy(p, mPt, points, offset + 2, 4);
                    mLastX = p[mPt + 2]; mLastY = p[mPt + 3];
                    mPt += 4;
                    break;
                case Path.VERB_CUBIC:
                    points[offset] = mLastX; points[offset + 1] = mLastY;
                    System.arraycopy(p, mPt, points, offset + 2, 6);
                    mLastX = p[mPt + 4]; mLastY = p[mPt + 5];
                    mPt += 6;
                    break;
                default:
                    points[offset] = mLastX; points[offset + 1] = mLastY;
                    points[offset + 2] = mStartX; points[offset + 3] = mStartY;
                    break;
            }
            return map(v);
        }

        public Segment next() {
            float[] pts = new float[8];
            int verb = next(pts, 0);
            return new Segment(verb, pts, 1f);
        }

        public static class Segment {
            private final int mVerb;
            private final float[] mPoints;
            private final float mConicWeight;
            Segment(int verb, float[] points, float w) { mVerb = verb; mPoints = points; mConicWeight = w; }
            public int getVerb() { return mVerb; }
            public float[] getPoints() { return mPoints; }
            public float getConicWeight() { return mConicWeight; }
        }
    }
}
