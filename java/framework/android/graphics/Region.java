package android.graphics;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;

/**
 * Region as a list of shapes: rectangles and flattened path polygons.
 * Union/intersect keep exact membership for contains(); getBounds() is exact
 * for rectangles and polygons.
 */
public class Region implements Parcelable {
    public enum Op {
        DIFFERENCE(0), INTERSECT(1), UNION(2), XOR(3), REVERSE_DIFFERENCE(4), REPLACE(5);
        Op(int nativeInt) { this.nativeInt = nativeInt; }
        public final int nativeInt;
    }

    // a region is a boolean combination: (OR of adds) AND NOT (OR of subs) AND (AND of intersects)
    private final ArrayList<float[]> mAdd = new ArrayList<float[]>();
    private final ArrayList<float[]> mSub = new ArrayList<float[]>();
    private final ArrayList<float[]> mAnd = new ArrayList<float[]>();

    public Region() {}
    public Region(Region region) { set(region); }
    public Region(Rect r) { set(r); }
    public Region(int left, int top, int right, int bottom) { set(left, top, right, bottom); }

    private static float[] rectPoly(int l, int t, int r, int b) { return new float[] {l, t, r, t, r, b, l, b}; }

    public void setEmpty() {
        mAdd.clear();
        mSub.clear();
        mAnd.clear();
    }

    public boolean set(Region region) {
        setEmpty();
        mAdd.addAll(region.mAdd);
        mSub.addAll(region.mSub);
        mAnd.addAll(region.mAnd);
        return !isEmpty();
    }

    public boolean set(Rect r) { return set(r.left, r.top, r.right, r.bottom); }

    public boolean set(int left, int top, int right, int bottom) {
        setEmpty();
        if (left < right && top < bottom) mAdd.add(rectPoly(left, top, right, bottom));
        return !isEmpty();
    }

    public boolean setPath(Path path, Region clip) {
        setEmpty();
        for (float[] c : path.flatten(0.5f)) mAdd.add(c);
        if (clip != null) op(clip, Op.INTERSECT);
        return !isEmpty();
    }

    public boolean isEmpty() { return getBounds().isEmpty(); }
    public boolean isRect() { return mAdd.size() == 1 && mSub.isEmpty() && mAnd.isEmpty() && mAdd.get(0).length == 8; }
    public boolean isComplex() { return !isRect() && !isEmpty(); }

    public Rect getBounds() {
        Rect r = new Rect();
        getBounds(r);
        return r;
    }

    public boolean getBounds(Rect r) {
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE, rr = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
        for (float[] p : mAdd) {
            for (int i = 0; i < p.length; i += 2) {
                l = Math.min(l, p[i]);
                rr = Math.max(rr, p[i]);
                t = Math.min(t, p[i + 1]);
                b = Math.max(b, p[i + 1]);
            }
        }
        if (l > rr) {
            r.setEmpty();
            return false;
        }
        for (float[] p : mAnd) {
            float al = Float.MAX_VALUE, at = Float.MAX_VALUE, ar = -Float.MAX_VALUE, ab = -Float.MAX_VALUE;
            for (int i = 0; i < p.length; i += 2) {
                al = Math.min(al, p[i]);
                ar = Math.max(ar, p[i]);
                at = Math.min(at, p[i + 1]);
                ab = Math.max(ab, p[i + 1]);
            }
            l = Math.max(l, al);
            t = Math.max(t, at);
            rr = Math.min(rr, ar);
            b = Math.min(b, ab);
        }
        r.set((int) Math.floor(l), (int) Math.floor(t), (int) Math.ceil(rr), (int) Math.ceil(b));
        if (r.isEmpty()) {
            r.setEmpty();
            return false;
        }
        return true;
    }

    public boolean getBoundaryPath(Path path) {
        path.reset();
        for (float[] p : mAdd) {
            path.moveTo(p[0], p[1]);
            for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
            path.close();
        }
        return !mAdd.isEmpty();
    }

    public Path getBoundaryPath() {
        Path p = new Path();
        getBoundaryPath(p);
        return p;
    }

    private static boolean inPoly(float[] p, float x, float y) {
        boolean in = false;
        int n = p.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = p[i * 2], yi = p[i * 2 + 1], xj = p[j * 2], yj = p[j * 2 + 1];
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) in = !in;
        }
        return in;
    }

    public boolean contains(int x, int y) {
        float fx = x + 0.5f, fy = y + 0.5f;
        boolean in = false;
        for (float[] p : mAdd) if (inPoly(p, fx, fy)) { in = true; break; }
        if (!in) return false;
        for (float[] p : mSub) if (inPoly(p, fx, fy)) return false;
        for (float[] p : mAnd) if (!inPoly(p, fx, fy)) return false;
        return true;
    }

    public boolean quickContains(Rect r) { return quickContains(r.left, r.top, r.right, r.bottom); }

    public boolean quickContains(int left, int top, int right, int bottom) {
        return isRect() && getBounds().contains(left, top, right, bottom);
    }

    public boolean quickReject(Rect r) { return quickReject(r.left, r.top, r.right, r.bottom); }

    public boolean quickReject(int left, int top, int right, int bottom) {
        Rect b = getBounds();
        return b.isEmpty() || !b.intersects(left, top, right, bottom);
    }

    public boolean quickReject(Region rgn) { return quickReject(rgn.getBounds()); }

    public void translate(int dx, int dy) { translate(dx, dy, this); }

    public void translate(int dx, int dy, Region dst) {
        Region src = new Region(this);
        dst.setEmpty();
        for (float[] p : src.mAdd) dst.mAdd.add(shift(p, dx, dy));
        for (float[] p : src.mSub) dst.mSub.add(shift(p, dx, dy));
        for (float[] p : src.mAnd) dst.mAnd.add(shift(p, dx, dy));
    }

    private static float[] shift(float[] p, int dx, int dy) {
        float[] q = p.clone();
        for (int i = 0; i < q.length; i += 2) {
            q[i] += dx;
            q[i + 1] += dy;
        }
        return q;
    }

    public void scale(float scale) { scale(scale, this); }

    public void scale(float scale, Region dst) {
        Region src = new Region(this);
        dst.setEmpty();
        for (float[] p : src.mAdd) {
            float[] q = p.clone();
            for (int i = 0; i < q.length; i++) q[i] *= scale;
            dst.mAdd.add(q);
        }
    }

    public final boolean union(Rect r) { return op(r, Op.UNION); }
    public boolean op(Rect r, Op op) { return op(r.left, r.top, r.right, r.bottom, op); }

    public boolean op(int left, int top, int right, int bottom, Op op) {
        return op(this, new Region(left, top, right, bottom), op);
    }

    public boolean op(Region region, Op op) { return op(this, region, op); }
    public boolean op(Rect rect, Region region, Op op) { return op(new Region(rect), region, op); }

    public boolean op(Region region1, Region region2, Op op) {
        Region a = new Region(region1), b = new Region(region2);
        setEmpty();
        switch (op) {
            case UNION:
            case XOR:
                mAdd.addAll(a.mAdd);
                mAdd.addAll(b.mAdd);
                break;
            case INTERSECT:
                mAdd.addAll(a.mAdd);
                mSub.addAll(a.mSub);
                mAnd.addAll(a.mAnd);
                mAnd.addAll(b.mAdd);
                break;
            case DIFFERENCE:
                mAdd.addAll(a.mAdd);
                mSub.addAll(a.mSub);
                mAnd.addAll(a.mAnd);
                mSub.addAll(b.mAdd);
                break;
            case REVERSE_DIFFERENCE:
                mAdd.addAll(b.mAdd);
                mSub.addAll(a.mAdd);
                break;
            case REPLACE:
                set(b);
                break;
        }
        return !isEmpty();
    }

    public String toString() { return "Region" + getBounds().toShortString(); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel p, int flags) { p.writeValue(new Region(this)); }

    public static final Parcelable.Creator<Region> CREATOR = new Parcelable.Creator<Region>() {
        public Region createFromParcel(Parcel p) { return (Region) p.readValue(null); }
        public Region[] newArray(int size) { return new Region[size]; }
    };

    @Override
    public boolean equals(Object obj) { return obj instanceof Region && ((Region) obj).getBounds().equals(getBounds()); }
}
