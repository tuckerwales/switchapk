package android.graphics;

public class Shader {
    static final int TYPE_NONE = 0;
    static final int TYPE_LINEAR = 1;
    static final int TYPE_RADIAL = 2;
    static final int TYPE_SWEEP = 3;
    static final int TYPE_BITMAP = 4;

    public enum TileMode {
        CLAMP(0), REPEAT(1), MIRROR(2), DECAL(0);
        TileMode(int nativeInt) { this.nativeInt = nativeInt; }
        final int nativeInt;
    }

    // read by the renderer natives
    int mType;
    float[] mGeom = new float[5];
    int[] mColors;
    float[] mPositions;
    int mTileX, mTileY;
    float[] mLocal;
    Bitmap mBitmap;
    private Matrix mLocalMatrix;

    public Shader() {}

    public boolean getLocalMatrix(Matrix localM) {
        if (mLocalMatrix != null) {
            localM.set(mLocalMatrix);
            return true;
        }
        return false;
    }

    public void setLocalMatrix(Matrix localM) {
        if (localM == null || localM.isIdentity()) {
            mLocalMatrix = null;
            mLocal = null;
        } else {
            mLocalMatrix = new Matrix(localM);
            mLocal = new float[6];
            mLocalMatrix.toAffine(mLocal);
        }
    }
}
