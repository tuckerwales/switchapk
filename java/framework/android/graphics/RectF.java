package android.graphics;

import android.os.Parcel;
import android.os.Parcelable;

public class RectF implements Parcelable {
    public float left;
    public float top;
    public float right;
    public float bottom;

    public RectF() {}
    public RectF(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
    public RectF(RectF r) {
        if (r == null) {
            left = top = right = bottom = 0.0f;
        } else {
            left = r.left;
            top = r.top;
            right = r.right;
            bottom = r.bottom;
        }
    }
    public RectF(Rect r) {
        if (r == null) {
            left = top = right = bottom = 0.0f;
        } else {
            left = r.left;
            top = r.top;
            right = r.right;
            bottom = r.bottom;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RectF r = (RectF) o;
        return left == r.left && top == r.top && right == r.right && bottom == r.bottom;
    }

    @Override
    public int hashCode() {
        int result = (left != +0.0f ? Float.floatToIntBits(left) : 0);
        result = 31 * result + (top != +0.0f ? Float.floatToIntBits(top) : 0);
        result = 31 * result + (right != +0.0f ? Float.floatToIntBits(right) : 0);
        result = 31 * result + (bottom != +0.0f ? Float.floatToIntBits(bottom) : 0);
        return result;
    }

    public String toString() { return "RectF(" + left + ", " + top + ", " + right + ", " + bottom + ")"; }
    public String toShortString() { return "[" + left + "," + top + "][" + right + "," + bottom + "]"; }
    public final boolean isEmpty() { return left >= right || top >= bottom; }
    public final float width() { return right - left; }
    public final float height() { return bottom - top; }
    public final float centerX() { return (left + right) * 0.5f; }
    public final float centerY() { return (top + bottom) * 0.5f; }
    public void setEmpty() { left = right = top = bottom = 0; }
    public void set(float left, float top, float right, float bottom) { this.left = left; this.top = top; this.right = right; this.bottom = bottom; }
    public void set(RectF src) { this.left = src.left; this.top = src.top; this.right = src.right; this.bottom = src.bottom; }
    public void set(Rect src) { this.left = src.left; this.top = src.top; this.right = src.right; this.bottom = src.bottom; }
    public void offset(float dx, float dy) { left += dx; top += dy; right += dx; bottom += dy; }
    public void offsetTo(float newLeft, float newTop) { right += newLeft - left; bottom += newTop - top; left = newLeft; top = newTop; }
    public void inset(float dx, float dy) { left += dx; top += dy; right -= dx; bottom -= dy; }
    public boolean contains(float x, float y) { return left < right && top < bottom && x >= left && x < right && y >= top && y < bottom; }
    public boolean contains(float left, float top, float right, float bottom) {
        return this.left < this.right && this.top < this.bottom && this.left <= left && this.top <= top && this.right >= right && this.bottom >= bottom;
    }
    public boolean contains(RectF r) {
        return this.left < this.right && this.top < this.bottom && left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom;
    }
    public boolean intersect(float left, float top, float right, float bottom) {
        if (this.left < right && left < this.right && this.top < bottom && top < this.bottom) {
            if (this.left < left) this.left = left;
            if (this.top < top) this.top = top;
            if (this.right > right) this.right = right;
            if (this.bottom > bottom) this.bottom = bottom;
            return true;
        }
        return false;
    }
    public boolean intersect(RectF r) { return intersect(r.left, r.top, r.right, r.bottom); }
    public boolean setIntersect(RectF a, RectF b) {
        if (a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom) {
            left = Math.max(a.left, b.left);
            top = Math.max(a.top, b.top);
            right = Math.min(a.right, b.right);
            bottom = Math.min(a.bottom, b.bottom);
            return true;
        }
        return false;
    }
    public boolean intersects(float left, float top, float right, float bottom) { return this.left < right && left < this.right && this.top < bottom && top < this.bottom; }
    public static boolean intersects(RectF a, RectF b) { return a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom; }
    public void round(Rect dst) { dst.set(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom)); }
    public void roundOut(Rect dst) { dst.set((int) Math.floor(left), (int) Math.floor(top), (int) Math.ceil(right), (int) Math.ceil(bottom)); }
    public void union(float left, float top, float right, float bottom) {
        if ((left < right) && (top < bottom)) {
            if ((this.left < this.right) && (this.top < this.bottom)) {
                if (this.left > left) this.left = left;
                if (this.top > top) this.top = top;
                if (this.right < right) this.right = right;
                if (this.bottom < bottom) this.bottom = bottom;
            } else {
                this.left = left;
                this.top = top;
                this.right = right;
                this.bottom = bottom;
            }
        }
    }
    public void union(RectF r) { union(r.left, r.top, r.right, r.bottom); }
    public void union(float x, float y) {
        if (x < left) left = x;
        else if (x > right) right = x;
        if (y < top) top = y;
        else if (y > bottom) bottom = y;
    }
    public void sort() {
        if (left > right) { float temp = left; left = right; right = temp; }
        if (top > bottom) { float temp = top; top = bottom; bottom = temp; }
    }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { out.writeFloat(left); out.writeFloat(top); out.writeFloat(right); out.writeFloat(bottom); }
    public void readFromParcel(Parcel in) { left = in.readFloat(); top = in.readFloat(); right = in.readFloat(); bottom = in.readFloat(); }
    public static final Parcelable.Creator<RectF> CREATOR = new Parcelable.Creator<RectF>() {
        public RectF createFromParcel(Parcel in) { RectF r = new RectF(); r.readFromParcel(in); return r; }
        public RectF[] newArray(int size) { return new RectF[size]; }
    };
}
