package android.graphics;

import android.os.Parcel;
import android.os.Parcelable;

public final class Rect implements Parcelable {
    public int left;
    public int top;
    public int right;
    public int bottom;

    public Rect() {}
    public Rect(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
    public Rect(Rect r) {
        if (r == null) {
            left = top = right = bottom = 0;
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
        Rect r = (Rect) o;
        return left == r.left && top == r.top && right == r.right && bottom == r.bottom;
    }

    @Override
    public int hashCode() {
        int result = left;
        result = 31 * result + top;
        result = 31 * result + right;
        result = 31 * result + bottom;
        return result;
    }

    @Override
    public String toString() { return "Rect(" + left + ", " + top + " - " + right + ", " + bottom + ")"; }
    public String toShortString() { return "[" + left + "," + top + "][" + right + "," + bottom + "]"; }
    public String flattenToString() { return left + " " + top + " " + right + " " + bottom; }

    public static Rect unflattenFromString(String str) {
        if (str == null || str.isEmpty()) return null;
        String[] p = str.trim().split(" ");
        if (p.length != 4) return null;
        try {
            return new Rect(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public final boolean isEmpty() { return left >= right || top >= bottom; }
    public final int width() { return right - left; }
    public final int height() { return bottom - top; }
    public final int centerX() { return (left + right) >> 1; }
    public final int centerY() { return (top + bottom) >> 1; }
    public final float exactCenterX() { return (left + right) * 0.5f; }
    public final float exactCenterY() { return (top + bottom) * 0.5f; }
    public void setEmpty() { left = right = top = bottom = 0; }
    public void set(int left, int top, int right, int bottom) { this.left = left; this.top = top; this.right = right; this.bottom = bottom; }
    public void set(Rect src) { this.left = src.left; this.top = src.top; this.right = src.right; this.bottom = src.bottom; }
    public void offset(int dx, int dy) { left += dx; top += dy; right += dx; bottom += dy; }
    public void offsetTo(int newLeft, int newTop) { right += newLeft - left; bottom += newTop - top; left = newLeft; top = newTop; }
    public void inset(int dx, int dy) { left += dx; top += dy; right -= dx; bottom -= dy; }
    public void inset(Insets insets) { left += insets.left; top += insets.top; right -= insets.right; bottom -= insets.bottom; }
    public void inset(int left, int top, int right, int bottom) { this.left += left; this.top += top; this.right -= right; this.bottom -= bottom; }
    public boolean contains(int x, int y) { return left < right && top < bottom && x >= left && x < right && y >= top && y < bottom; }
    public boolean contains(int left, int top, int right, int bottom) {
        return this.left < this.right && this.top < this.bottom && this.left <= left && this.top <= top && this.right >= right && this.bottom >= bottom;
    }
    public boolean contains(Rect r) {
        return this.left < this.right && this.top < this.bottom && left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom;
    }
    public boolean intersect(int left, int top, int right, int bottom) {
        if (this.left < right && left < this.right && this.top < bottom && top < this.bottom) {
            if (this.left < left) this.left = left;
            if (this.top < top) this.top = top;
            if (this.right > right) this.right = right;
            if (this.bottom > bottom) this.bottom = bottom;
            return true;
        }
        return false;
    }
    public boolean intersect(Rect r) { return intersect(r.left, r.top, r.right, r.bottom); }
    public void intersectUnchecked(Rect other) {
        left = Math.max(left, other.left);
        top = Math.max(top, other.top);
        right = Math.min(right, other.right);
        bottom = Math.min(bottom, other.bottom);
    }
    public boolean setIntersect(Rect a, Rect b) {
        if (a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom) {
            left = Math.max(a.left, b.left);
            top = Math.max(a.top, b.top);
            right = Math.min(a.right, b.right);
            bottom = Math.min(a.bottom, b.bottom);
            return true;
        }
        return false;
    }
    public boolean intersects(int left, int top, int right, int bottom) { return this.left < right && left < this.right && this.top < bottom && top < this.bottom; }
    public static boolean intersects(Rect a, Rect b) { return a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom; }
    public void union(int left, int top, int right, int bottom) {
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
    public void union(Rect r) { union(r.left, r.top, r.right, r.bottom); }
    public void union(int x, int y) {
        if (x < left) left = x;
        else if (x > right) right = x;
        if (y < top) top = y;
        else if (y > bottom) bottom = y;
    }
    public void sort() {
        if (left > right) { int temp = left; left = right; right = temp; }
        if (top > bottom) { int temp = top; top = bottom; bottom = temp; }
    }
    public void scale(float scale) {
        if (scale != 1.0f) {
            left = (int) (left * scale + 0.5f);
            top = (int) (top * scale + 0.5f);
            right = (int) (right * scale + 0.5f);
            bottom = (int) (bottom * scale + 0.5f);
        }
    }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { out.writeInt(left); out.writeInt(top); out.writeInt(right); out.writeInt(bottom); }
    public void readFromParcel(Parcel in) { left = in.readInt(); top = in.readInt(); right = in.readInt(); bottom = in.readInt(); }
    public static final Parcelable.Creator<Rect> CREATOR = new Parcelable.Creator<Rect>() {
        public Rect createFromParcel(Parcel in) { Rect r = new Rect(); r.readFromParcel(in); return r; }
        public Rect[] newArray(int size) { return new Rect[size]; }
    };
}
