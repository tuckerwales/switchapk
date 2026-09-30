package android.graphics;

public final class Insets implements android.os.Parcelable {
    public static final Insets NONE = new Insets(0, 0, 0, 0);
    public final int left, top, right, bottom;

    private Insets(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public static Insets of(int left, int top, int right, int bottom) {
        if (left == 0 && top == 0 && right == 0 && bottom == 0) return NONE;
        return new Insets(left, top, right, bottom);
    }

    public static Insets of(Rect r) { return (r == null) ? NONE : of(r.left, r.top, r.right, r.bottom); }
    public static Insets add(Insets a, Insets b) { return of(a.left + b.left, a.top + b.top, a.right + b.right, a.bottom + b.bottom); }
    public static Insets subtract(Insets a, Insets b) { return of(a.left - b.left, a.top - b.top, a.right - b.right, a.bottom - b.bottom); }
    public static Insets max(Insets a, Insets b) { return of(Math.max(a.left, b.left), Math.max(a.top, b.top), Math.max(a.right, b.right), Math.max(a.bottom, b.bottom)); }
    public static Insets min(Insets a, Insets b) { return of(Math.min(a.left, b.left), Math.min(a.top, b.top), Math.min(a.right, b.right), Math.min(a.bottom, b.bottom)); }
    public Rect toRect() { return new Rect(left, top, right, bottom); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Insets)) return false;
        Insets i = (Insets) o;
        return bottom == i.bottom && left == i.left && right == i.right && top == i.top;
    }

    @Override
    public int hashCode() { return ((left * 31 + top) * 31 + right) * 31 + bottom; }
    @Override
    public String toString() { return "Insets{left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom + '}'; }
    public int describeContents() { return 0; }
    public void writeToParcel(android.os.Parcel out, int flags) { out.writeInt(left); out.writeInt(top); out.writeInt(right); out.writeInt(bottom); }
    public static final android.os.Parcelable.Creator<Insets> CREATOR = new android.os.Parcelable.Creator<Insets>() {
        public Insets createFromParcel(android.os.Parcel in) { return of(in.readInt(), in.readInt(), in.readInt(), in.readInt()); }
        public Insets[] newArray(int size) { return new Insets[size]; }
    };
}
