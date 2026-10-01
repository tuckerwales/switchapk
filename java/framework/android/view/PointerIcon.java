package android.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

/** Mouse pointer icon. switchapk shows no mouse pointer; icons are accepted and ignored. */
public final class PointerIcon implements Parcelable {
    public static final int TYPE_ALIAS = 1010;
    public static final int TYPE_ALL_SCROLL = 1013;
    public static final int TYPE_ARROW = 1000;
    public static final int TYPE_CELL = 1006;
    public static final int TYPE_CONTEXT_MENU = 1001;
    public static final int TYPE_COPY = 1011;
    public static final int TYPE_CROSSHAIR = 1007;
    public static final int TYPE_DEFAULT = 1000;
    public static final int TYPE_GRAB = 1020;
    public static final int TYPE_GRABBING = 1021;
    public static final int TYPE_HAND = 1002;
    public static final int TYPE_HANDWRITING = 1022;
    public static final int TYPE_HELP = 1003;
    public static final int TYPE_HORIZONTAL_DOUBLE_ARROW = 1014;
    public static final int TYPE_NO_DROP = 1012;
    public static final int TYPE_NULL = 0;
    public static final int TYPE_TEXT = 1008;
    public static final int TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW = 1017;
    public static final int TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW = 1016;
    public static final int TYPE_VERTICAL_DOUBLE_ARROW = 1015;
    public static final int TYPE_VERTICAL_TEXT = 1009;
    public static final int TYPE_WAIT = 1004;
    public static final int TYPE_ZOOM_IN = 1018;
    public static final int TYPE_ZOOM_OUT = 1019;

    private final int mType;

    private PointerIcon(int type) { mType = type; }

    public static PointerIcon getSystemIcon(Context context, int type) { return new PointerIcon(type); }

    public static PointerIcon create(Bitmap bitmap, float hotSpotX, float hotSpotY) { return new PointerIcon(-1); }

    public static PointerIcon load(android.content.res.Resources resources, int resourceId) { return new PointerIcon(-1); }

    /** framework-internal (hidden in AOSP). */
    public int getType() { return mType; }

    @Override
    public boolean equals(Object other) { return other instanceof PointerIcon && ((PointerIcon) other).mType == mType; }

    @Override
    public int hashCode() { return mType; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel out, int flags) { out.writeInt(mType); }

    public static final Parcelable.Creator<PointerIcon> CREATOR = new Parcelable.Creator<PointerIcon>() {
        public PointerIcon createFromParcel(Parcel in) { return new PointerIcon(in.readInt()); }
        public PointerIcon[] newArray(int size) { return new PointerIcon[size]; }
    };
}
