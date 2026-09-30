package android.graphics;

public class LightingColorFilter extends ColorFilter {
    private final int mMul, mAdd;

    public LightingColorFilter(int mul, int add) {
        mMul = mul;
        mAdd = add;
        // pure multiplies map onto the renderer's MULTIPLY filter
        if ((add & 0xffffff) == 0) {
            mMode = PorterDuff.Mode.MULTIPLY.nativeInt;
            mColor = mul | 0xff000000;
        }
    }

    public int getColorMultiply() { return mMul; }
    public int getColorAdd() { return mAdd; }

    @Override
    int filter(int c) {
        int r = Math.min(255, ((c >> 16) & 255) * ((mMul >> 16) & 255) / 255 + ((mAdd >> 16) & 255));
        int g = Math.min(255, ((c >> 8) & 255) * ((mMul >> 8) & 255) / 255 + ((mAdd >> 8) & 255));
        int b = Math.min(255, (c & 255) * (mMul & 255) / 255 + (mAdd & 255));
        return (c & 0xff000000) | (r << 16) | (g << 8) | b;
    }
}
