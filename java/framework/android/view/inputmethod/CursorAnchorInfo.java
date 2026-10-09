package android.view.inputmethod;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cursor and character positions an editor reports to the input method.
 * switchapk's input method (the system keyboard applet) does not use them.
 */
public final class CursorAnchorInfo implements Parcelable {
    public static final int FLAG_HAS_INVISIBLE_REGION = 2;
    public static final int FLAG_HAS_VISIBLE_REGION = 1;
    public static final int FLAG_IS_RTL = 4;

    private final int mSelectionStart;
    private final int mSelectionEnd;
    private final int mComposingTextStart;
    private final CharSequence mComposingText;
    private final int mInsertionMarkerFlags;
    private final float mInsertionMarkerHorizontal;
    private final float mInsertionMarkerTop;
    private final float mInsertionMarkerBaseline;
    private final float mInsertionMarkerBottom;
    private final int mCharacterBoundsStart;
    private final float[] mCharacterBounds;
    private final int[] mCharacterBoundsFlags;
    private final float[] mVisibleLineBounds;
    private final EditorBoundsInfo mEditorBoundsInfo;
    private final float[] mMatrixValues;

    private CursorAnchorInfo(Builder b) {
        mSelectionStart = b.mSelectionStart;
        mSelectionEnd = b.mSelectionEnd;
        mComposingTextStart = b.mComposingTextStart;
        mComposingText = b.mComposingText;
        mInsertionMarkerFlags = b.mInsertionMarkerFlags;
        mInsertionMarkerHorizontal = b.mInsertionMarkerHorizontal;
        mInsertionMarkerTop = b.mInsertionMarkerTop;
        mInsertionMarkerBaseline = b.mInsertionMarkerBaseline;
        mInsertionMarkerBottom = b.mInsertionMarkerBottom;
        int lo = Integer.MAX_VALUE, hi = -1;
        for (int i = 0; i < b.mBoundsIndex.size(); i++) {
            lo = Math.min(lo, b.mBoundsIndex.get(i));
            hi = Math.max(hi, b.mBoundsIndex.get(i));
        }
        if (hi < 0) {
            mCharacterBoundsStart = 0;
            mCharacterBounds = new float[0];
            mCharacterBoundsFlags = new int[0];
        } else {
            int n = hi - lo + 1;
            mCharacterBoundsStart = lo;
            mCharacterBounds = new float[n * 4];
            mCharacterBoundsFlags = new int[n];
            Arrays.fill(mCharacterBounds, Float.NaN);
            Arrays.fill(mCharacterBoundsFlags, -1);
            for (int i = 0; i < b.mBoundsIndex.size(); i++) {
                int k = b.mBoundsIndex.get(i) - lo;
                System.arraycopy(b.mBounds, i * 4, mCharacterBounds, k * 4, 4);
                mCharacterBoundsFlags[k] = b.mBoundsFlags.get(i);
            }
        }
        mVisibleLineBounds = Arrays.copyOf(b.mLineBounds, b.mLineCount * 4);
        mEditorBoundsInfo = b.mEditorBoundsInfo;
        mMatrixValues = new float[9];
        (b.mMatrix != null ? b.mMatrix : new Matrix()).getValues(mMatrixValues);
    }

    public CursorAnchorInfo(Parcel source) {
        mSelectionStart = source.readInt();
        mSelectionEnd = source.readInt();
        mComposingTextStart = source.readInt();
        mComposingText = source.readCharSequence();
        mInsertionMarkerFlags = source.readInt();
        mInsertionMarkerHorizontal = source.readFloat();
        mInsertionMarkerTop = source.readFloat();
        mInsertionMarkerBaseline = source.readFloat();
        mInsertionMarkerBottom = source.readFloat();
        mCharacterBoundsStart = source.readInt();
        mCharacterBounds = source.createFloatArray();
        mCharacterBoundsFlags = source.createIntArray();
        mVisibleLineBounds = source.createFloatArray();
        mEditorBoundsInfo = source.readInt() != 0 ? EditorBoundsInfo.CREATOR.createFromParcel(source) : null;
        mMatrixValues = source.createFloatArray();
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSelectionStart);
        dest.writeInt(mSelectionEnd);
        dest.writeInt(mComposingTextStart);
        dest.writeCharSequence(mComposingText);
        dest.writeInt(mInsertionMarkerFlags);
        dest.writeFloat(mInsertionMarkerHorizontal);
        dest.writeFloat(mInsertionMarkerTop);
        dest.writeFloat(mInsertionMarkerBaseline);
        dest.writeFloat(mInsertionMarkerBottom);
        dest.writeInt(mCharacterBoundsStart);
        dest.writeFloatArray(mCharacterBounds);
        dest.writeIntArray(mCharacterBoundsFlags);
        dest.writeFloatArray(mVisibleLineBounds);
        dest.writeInt(mEditorBoundsInfo != null ? 1 : 0);
        if (mEditorBoundsInfo != null) mEditorBoundsInfo.writeToParcel(dest, flags);
        dest.writeFloatArray(mMatrixValues);
    }

    @Override
    public int hashCode() {
        return ((mSelectionStart * 31 + mSelectionEnd) * 31 + mComposingTextStart) * 31
                + Float.floatToIntBits(mInsertionMarkerHorizontal);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CursorAnchorInfo)) return false;
        CursorAnchorInfo o = (CursorAnchorInfo) obj;
        return mSelectionStart == o.mSelectionStart && mSelectionEnd == o.mSelectionEnd
                && mComposingTextStart == o.mComposingTextStart
                && String.valueOf(mComposingText).equals(String.valueOf(o.mComposingText))
                && mInsertionMarkerFlags == o.mInsertionMarkerFlags
                && Float.compare(mInsertionMarkerHorizontal, o.mInsertionMarkerHorizontal) == 0
                && Float.compare(mInsertionMarkerTop, o.mInsertionMarkerTop) == 0
                && Float.compare(mInsertionMarkerBaseline, o.mInsertionMarkerBaseline) == 0
                && Float.compare(mInsertionMarkerBottom, o.mInsertionMarkerBottom) == 0
                && mCharacterBoundsStart == o.mCharacterBoundsStart
                && Arrays.equals(mCharacterBounds, o.mCharacterBounds)
                && Arrays.equals(mCharacterBoundsFlags, o.mCharacterBoundsFlags)
                && Arrays.equals(mVisibleLineBounds, o.mVisibleLineBounds)
                && (mEditorBoundsInfo == null ? o.mEditorBoundsInfo == null : mEditorBoundsInfo.equals(o.mEditorBoundsInfo))
                && Arrays.equals(mMatrixValues, o.mMatrixValues);
    }

    @Override
    public String toString() {
        return "CursorAnchorInfo{mSelection=" + mSelectionStart + "," + mSelectionEnd
                + " mComposingTextStart=" + mComposingTextStart + " mComposingText=" + mComposingText
                + " mInsertionMarkerFlags=" + mInsertionMarkerFlags
                + " mInsertionMarkerHorizontal=" + mInsertionMarkerHorizontal
                + " mInsertionMarkerTop=" + mInsertionMarkerTop
                + " mInsertionMarkerBaseline=" + mInsertionMarkerBaseline
                + " mInsertionMarkerBottom=" + mInsertionMarkerBottom + "}";
    }

    public int getSelectionStart() { return mSelectionStart; }
    public int getSelectionEnd() { return mSelectionEnd; }
    public int getComposingTextStart() { return mComposingTextStart; }
    public CharSequence getComposingText() { return mComposingText; }
    public int getInsertionMarkerFlags() { return mInsertionMarkerFlags; }
    public float getInsertionMarkerHorizontal() { return mInsertionMarkerHorizontal; }
    public float getInsertionMarkerTop() { return mInsertionMarkerTop; }
    public float getInsertionMarkerBaseline() { return mInsertionMarkerBaseline; }
    public float getInsertionMarkerBottom() { return mInsertionMarkerBottom; }

    public RectF getCharacterBounds(int index) {
        int k = index - mCharacterBoundsStart;
        if (k < 0 || k >= mCharacterBoundsFlags.length || mCharacterBoundsFlags[k] == -1) return null;
        return new RectF(mCharacterBounds[k * 4], mCharacterBounds[k * 4 + 1],
                mCharacterBounds[k * 4 + 2], mCharacterBounds[k * 4 + 3]);
    }

    public int getCharacterBoundsFlags(int index) {
        int k = index - mCharacterBoundsStart;
        if (k < 0 || k >= mCharacterBoundsFlags.length || mCharacterBoundsFlags[k] == -1) return 0;
        return mCharacterBoundsFlags[k];
    }

    public List<RectF> getVisibleLineBounds() {
        ArrayList<RectF> out = new ArrayList<RectF>(mVisibleLineBounds.length / 4);
        for (int i = 0; i < mVisibleLineBounds.length; i += 4) {
            out.add(new RectF(mVisibleLineBounds[i], mVisibleLineBounds[i + 1],
                    mVisibleLineBounds[i + 2], mVisibleLineBounds[i + 3]));
        }
        return out;
    }

    public EditorBoundsInfo getEditorBoundsInfo() { return mEditorBoundsInfo; }

    public Matrix getMatrix() {
        Matrix m = new Matrix();
        m.setValues(mMatrixValues);
        return m;
    }

    public int describeContents() { return 0; }

    public static final Parcelable.Creator<CursorAnchorInfo> CREATOR = new Parcelable.Creator<CursorAnchorInfo>() {
        public CursorAnchorInfo createFromParcel(Parcel source) { return new CursorAnchorInfo(source); }
        public CursorAnchorInfo[] newArray(int size) { return new CursorAnchorInfo[size]; }
    };

    public static final class Builder {
        private int mSelectionStart = -1;
        private int mSelectionEnd = -1;
        private int mComposingTextStart = -1;
        private CharSequence mComposingText;
        private int mInsertionMarkerFlags;
        private float mInsertionMarkerHorizontal = Float.NaN;
        private float mInsertionMarkerTop = Float.NaN;
        private float mInsertionMarkerBaseline = Float.NaN;
        private float mInsertionMarkerBottom = Float.NaN;
        private final ArrayList<Integer> mBoundsIndex = new ArrayList<Integer>();
        private final ArrayList<Integer> mBoundsFlags = new ArrayList<Integer>();
        private float[] mBounds = new float[16];
        private float[] mLineBounds = new float[16];
        private int mLineCount;
        private EditorBoundsInfo mEditorBoundsInfo;
        private Matrix mMatrix;

        public Builder() {}

        public Builder setSelectionRange(int newStart, int newEnd) {
            mSelectionStart = newStart;
            mSelectionEnd = newEnd;
            return this;
        }

        public Builder setComposingText(int composingTextStart, CharSequence composingText) {
            mComposingTextStart = composingTextStart;
            mComposingText = composingText;
            return this;
        }

        public Builder setInsertionMarkerLocation(float horizontalPosition, float lineTop, float lineBaseline,
                float lineBottom, int flags) {
            mInsertionMarkerHorizontal = horizontalPosition;
            mInsertionMarkerTop = lineTop;
            mInsertionMarkerBaseline = lineBaseline;
            mInsertionMarkerBottom = lineBottom;
            mInsertionMarkerFlags = flags;
            return this;
        }

        public Builder addCharacterBounds(int index, float left, float top, float right, float bottom, int flags) {
            if (index < 0) throw new IllegalArgumentException("index must not be a negative integer.");
            int n = mBoundsIndex.size();
            if (mBounds.length < (n + 1) * 4) mBounds = Arrays.copyOf(mBounds, mBounds.length * 2);
            mBounds[n * 4] = left;
            mBounds[n * 4 + 1] = top;
            mBounds[n * 4 + 2] = right;
            mBounds[n * 4 + 3] = bottom;
            mBoundsIndex.add(index);
            mBoundsFlags.add(flags);
            return this;
        }

        public Builder setEditorBoundsInfo(EditorBoundsInfo bounds) {
            mEditorBoundsInfo = bounds;
            return this;
        }

        public Builder setMatrix(Matrix matrix) {
            mMatrix = matrix != null ? new Matrix(matrix) : null;
            return this;
        }

        public Builder addVisibleLineBounds(float left, float top, float right, float bottom) {
            if (mLineBounds.length < (mLineCount + 1) * 4) mLineBounds = Arrays.copyOf(mLineBounds, mLineBounds.length * 2);
            mLineBounds[mLineCount * 4] = left;
            mLineBounds[mLineCount * 4 + 1] = top;
            mLineBounds[mLineCount * 4 + 2] = right;
            mLineBounds[mLineCount * 4 + 3] = bottom;
            mLineCount++;
            return this;
        }

        public Builder clearVisibleLineBounds() {
            mLineCount = 0;
            return this;
        }

        public CursorAnchorInfo build() { return new CursorAnchorInfo(this); }

        public void reset() {
            mSelectionStart = mSelectionEnd = mComposingTextStart = -1;
            mComposingText = null;
            mInsertionMarkerFlags = 0;
            mInsertionMarkerHorizontal = mInsertionMarkerTop = Float.NaN;
            mInsertionMarkerBaseline = mInsertionMarkerBottom = Float.NaN;
            mBoundsIndex.clear();
            mBoundsFlags.clear();
            mLineCount = 0;
            mEditorBoundsInfo = null;
            mMatrix = null;
        }
    }
}
