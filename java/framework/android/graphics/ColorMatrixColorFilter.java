package android.graphics;

public class ColorMatrixColorFilter extends ColorFilter {
    private final ColorMatrix mMatrix = new ColorMatrix();

    public ColorMatrixColorFilter(ColorMatrix matrix) { mMatrix.set(matrix); }
    public ColorMatrixColorFilter(float[] array) {
        if (array.length < 20) throw new ArrayIndexOutOfBoundsException();
        mMatrix.set(array);
    }

    public void getColorMatrix(ColorMatrix colorMatrix) { if (colorMatrix != null) colorMatrix.set(mMatrix); }
    public void setColorMatrix(ColorMatrix matrix) { if (matrix == null) mMatrix.reset(); else mMatrix.set(matrix); }
    public void setColorMatrixArray(float[] array) { if (array == null) mMatrix.reset(); else mMatrix.set(array); }

    @Override
    int filter(int c) {
        float[] m = mMatrix.getArray();
        float r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255, a = (c >>> 24);
        int R = clamp(m[0] * r + m[1] * g + m[2] * b + m[3] * a + m[4]);
        int G = clamp(m[5] * r + m[6] * g + m[7] * b + m[8] * a + m[9]);
        int B = clamp(m[10] * r + m[11] * g + m[12] * b + m[13] * a + m[14]);
        int A = clamp(m[15] * r + m[16] * g + m[17] * b + m[18] * a + m[19]);
        return (A << 24) | (R << 16) | (G << 8) | B;
    }

    private static int clamp(float v) { return v < 0 ? 0 : v > 255 ? 255 : (int) (v + 0.5f); }
}
