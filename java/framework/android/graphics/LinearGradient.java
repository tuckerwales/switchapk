package android.graphics;

public class LinearGradient extends Shader {
    public LinearGradient(float x0, float y0, float x1, float y1, int[] colors, float[] positions, TileMode tile) {
        if (colors.length < 2) throw new IllegalArgumentException("needs >= 2 number of colors");
        if (positions != null && colors.length != positions.length) throw new IllegalArgumentException("color and position arrays must be of equal length");
        mType = TYPE_LINEAR;
        mGeom[0] = x0; mGeom[1] = y0; mGeom[2] = x1; mGeom[3] = y1;
        mColors = colors.clone();
        mPositions = positions != null ? positions.clone() : null;
        mTileX = mTileY = tile.nativeInt;
    }

    public LinearGradient(float x0, float y0, float x1, float y1, long[] colors, float[] positions, TileMode tile) {
        this(x0, y0, x1, y1, toInts(colors), positions, tile);
    }

    public LinearGradient(float x0, float y0, float x1, float y1, int color0, int color1, TileMode tile) {
        this(x0, y0, x1, y1, new int[] {color0, color1}, null, tile);
    }

    public LinearGradient(float x0, float y0, float x1, float y1, long color0, long color1, TileMode tile) {
        this(x0, y0, x1, y1, Color.toArgb(color0), Color.toArgb(color1), tile);
    }

    static int[] toInts(long[] colors) {
        int[] r = new int[colors.length];
        for (int i = 0; i < r.length; i++) r[i] = Color.toArgb(colors[i]);
        return r;
    }
}
