package android.graphics;

import android.os.Parcel;
import android.os.Parcelable;

public class PointF implements Parcelable {
    public float x;
    public float y;

    public PointF() {}
    public PointF(float x, float y) { this.x = x; this.y = y; }
    public PointF(Point p) { this.x = p.x; this.y = p.y; }
    public PointF(PointF p) { this.x = p.x; this.y = p.y; }

    public final void set(float x, float y) { this.x = x; this.y = y; }
    public final void set(PointF p) { this.x = p.x; this.y = p.y; }
    public final void negate() { x = -x; y = -y; }
    public final void offset(float dx, float dy) { x += dx; y += dy; }
    public final boolean equals(float x, float y) { return this.x == x && this.y == y; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PointF pointF = (PointF) o;
        return Float.compare(pointF.x, x) == 0 && Float.compare(pointF.y, y) == 0;
    }

    @Override
    public int hashCode() { return 31 * Float.floatToIntBits(x) + Float.floatToIntBits(y); }
    @Override
    public String toString() { return "PointF(" + x + ", " + y + ")"; }
    public final float length() { return length(x, y); }
    public static float length(float x, float y) { return (float) Math.hypot(x, y); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { out.writeFloat(x); out.writeFloat(y); }
    public void readFromParcel(Parcel in) { x = in.readFloat(); y = in.readFloat(); }

    public static final Parcelable.Creator<PointF> CREATOR = new Parcelable.Creator<PointF>() {
        public PointF createFromParcel(Parcel in) { PointF r = new PointF(); r.readFromParcel(in); return r; }
        public PointF[] newArray(int size) { return new PointF[size]; }
    };
}
