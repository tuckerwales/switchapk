package android.graphics;

public class SweepGradient extends Shader {
    public SweepGradient(float cx, float cy, int[] colors, float[] positions) {
        if (colors.length < 2) throw new IllegalArgumentException("needs >= 2 number of colors");
        mType = TYPE_SWEEP;
        mGeom[0] = cx; mGeom[1] = cy;
        mColors = colors.clone();
        mPositions = positions != null ? positions.clone() : null;
    }

    public SweepGradient(float cx, float cy, long[] colors, float[] positions) { this(cx, cy, LinearGradient.toInts(colors), positions); }
    public SweepGradient(float cx, float cy, int color0, int color1) { this(cx, cy, new int[] {color0, color1}, null); }
    public SweepGradient(float cx, float cy, long color0, long color1) { this(cx, cy, Color.toArgb(color0), Color.toArgb(color1)); }
}
