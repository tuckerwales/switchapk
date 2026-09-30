package android.opengl;

/** Column-major 4x4 helpers used by SurfaceTexture. invertM is intentionally absent until it is correct. */
public final class Matrix {
    private Matrix() {}

    public static void setIdentityM(float[] sm, int smOffset) {
        for (int i = 0; i < 16; i++) sm[smOffset + i] = 0;
        for (int i = 0; i < 16; i += 5) sm[smOffset + i] = 1;
    }

    public static void transposeM(float[] mTrans, int mTransOffset, float[] m, int mOffset) {
        for (int i = 0; i < 4; i++) {
            int base = i * 4 + mOffset;
            mTrans[i + mTransOffset] = m[base];
            mTrans[i + 4 + mTransOffset] = m[base + 1];
            mTrans[i + 8 + mTransOffset] = m[base + 2];
            mTrans[i + 12 + mTransOffset] = m[base + 3];
        }
    }

    public static void multiplyMM(float[] result, int resultOffset, float[] lhs, int lhsOffset, float[] rhs,
            int rhsOffset) {
        float[] tmp = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) sum += lhs[lhsOffset + k * 4 + row] * rhs[rhsOffset + col * 4 + k];
                tmp[col * 4 + row] = sum;
            }
        }
        System.arraycopy(tmp, 0, result, resultOffset, 16);
    }
}
