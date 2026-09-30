package android.graphics;

public class RadialGradient extends Shader {
    public RadialGradient(float centerX, float centerY, float radius, int[] colors, float[] stops, TileMode tileMode) {
        if (radius <= 0) throw new IllegalArgumentException("radius must be > 0");
        if (colors.length < 2) throw new IllegalArgumentException("needs >= 2 number of colors");
        mType = TYPE_RADIAL;
        mGeom[0] = centerX; mGeom[1] = centerY; mGeom[4] = radius;
        mColors = colors.clone();
        mPositions = stops != null ? stops.clone() : null;
        mTileX = mTileY = tileMode.nativeInt;
    }

    public RadialGradient(float centerX, float centerY, float radius, long[] colors, float[] stops, TileMode tileMode) {
        this(centerX, centerY, radius, LinearGradient.toInts(colors), stops, tileMode);
    }

    public RadialGradient(float startX, float startY, float startRadius, float endX, float endY, float endRadius, long[] colors, float[] stops, TileMode tileMode) {
        this(endX, endY, endRadius, LinearGradient.toInts(colors), stops, tileMode);
    }

    public RadialGradient(float centerX, float centerY, float radius, int centerColor, int edgeColor, TileMode tileMode) {
        this(centerX, centerY, radius, new int[] {centerColor, edgeColor}, null, tileMode);
    }

    public RadialGradient(float centerX, float centerY, float radius, long centerColor, long edgeColor, TileMode tileMode) {
        this(centerX, centerY, radius, Color.toArgb(centerColor), Color.toArgb(edgeColor), tileMode);
    }
}
