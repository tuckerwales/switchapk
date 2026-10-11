package android.view.inputmethod;

import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;

/** The editor and handwriting bounds of an editor, part of CursorAnchorInfo. */
public final class EditorBoundsInfo implements Parcelable {
    private final RectF mEditorBounds;
    private final RectF mHandwritingBounds;

    EditorBoundsInfo(RectF editorBounds, RectF handwritingBounds) {
        mEditorBounds = editorBounds;
        mHandwritingBounds = handwritingBounds;
    }

    public RectF getEditorBounds() { return mEditorBounds; }
    public RectF getHandwritingBounds() { return mHandwritingBounds; }

    @Override
    public int hashCode() {
        return (mEditorBounds != null ? mEditorBounds.hashCode() : 0) * 31
                + (mHandwritingBounds != null ? mHandwritingBounds.hashCode() : 0);
    }

    @Override
    public String toString() {
        return "EditorBoundsInfo{mEditorBounds=" + mEditorBounds + " mHandwritingBounds=" + mHandwritingBounds + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EditorBoundsInfo)) return false;
        EditorBoundsInfo o = (EditorBoundsInfo) obj;
        return (mEditorBounds == null ? o.mEditorBounds == null : mEditorBounds.equals(o.mEditorBounds))
                && (mHandwritingBounds == null ? o.mHandwritingBounds == null
                        : mHandwritingBounds.equals(o.mHandwritingBounds));
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        writeRect(dest, mEditorBounds);
        writeRect(dest, mHandwritingBounds);
    }

    private static void writeRect(Parcel dest, RectF r) {
        dest.writeInt(r != null ? 1 : 0);
        if (r == null) return;
        dest.writeFloat(r.left);
        dest.writeFloat(r.top);
        dest.writeFloat(r.right);
        dest.writeFloat(r.bottom);
    }

    private static RectF readRect(Parcel in) {
        if (in.readInt() == 0) return null;
        float l = in.readFloat(), t = in.readFloat(), r = in.readFloat();
        return new RectF(l, t, r, in.readFloat());
    }

    public static final Parcelable.Creator<EditorBoundsInfo> CREATOR = new Parcelable.Creator<EditorBoundsInfo>() {
        public EditorBoundsInfo createFromParcel(Parcel in) {
            RectF editor = readRect(in);
            return new EditorBoundsInfo(editor, readRect(in));
        }
        public EditorBoundsInfo[] newArray(int size) { return new EditorBoundsInfo[size]; }
    };

    public static final class Builder {
        private RectF mEditorBounds;
        private RectF mHandwritingBounds;

        public Builder() {}

        public Builder setEditorBounds(RectF bounds) {
            mEditorBounds = bounds;
            return this;
        }

        public Builder setHandwritingBounds(RectF bounds) {
            mHandwritingBounds = bounds;
            return this;
        }

        public EditorBoundsInfo build() { return new EditorBoundsInfo(mEditorBounds, mHandwritingBounds); }
    }
}
