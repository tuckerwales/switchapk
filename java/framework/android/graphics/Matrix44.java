package android.graphics;

public class Matrix44 {
    final float[] mBackingArray = new float[16];
    public Matrix44() { reset(); }
    public Matrix44(Matrix mat) {
        reset();
        float[] v = new float[9];
        mat.getValues(v);
        mBackingArray[0] = v[0]; mBackingArray[1] = v[1]; mBackingArray[3] = v[2];
        mBackingArray[4] = v[3]; mBackingArray[5] = v[4]; mBackingArray[7] = v[5];
    }
    public void reset() {
        java.util.Arrays.fill(mBackingArray, 0);
        mBackingArray[0] = mBackingArray[5] = mBackingArray[10] = mBackingArray[15] = 1;
    }
    public float get(int row, int col) { return mBackingArray[row * 4 + col]; }
    public void set(int row, int col, float val) { mBackingArray[row * 4 + col] = val; }
    public boolean isIdentity() {
        for (int i = 0; i < 16; i++) if (mBackingArray[i] != ((i % 5 == 0) ? 1 : 0)) return false;
        return true;
    }
}
