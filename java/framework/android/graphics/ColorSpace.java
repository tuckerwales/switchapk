package android.graphics;

public abstract class ColorSpace {
    public enum Named { SRGB, LINEAR_SRGB, EXTENDED_SRGB, LINEAR_EXTENDED_SRGB, BT709, BT2020, DCI_P3, DISPLAY_P3, NTSC_1953, SMPTE_C, ADOBE_RGB, PRO_PHOTO_RGB, ACES, ACESCG, CIE_XYZ, CIE_LAB, BT2020_HLG, BT2020_PQ }
    public enum Model {
        RGB(3), XYZ(3), LAB(3), CMYK(4);
        private final int mComponentCount;
        Model(int componentCount) { mComponentCount = componentCount; }
        public int getComponentCount() { return mComponentCount; }
    }
    public enum RenderIntent { PERCEPTUAL, RELATIVE, SATURATION, ABSOLUTE }

    private static final ColorSpace SRGB = new Rgb("sRGB IEC61966-2.1");
    private final String mName;

    ColorSpace(String name) { mName = name; }

    public String getName() { return mName; }
    public int getId() { return 0; }
    public Model getModel() { return Model.RGB; }
    public int getComponentCount() { return 3; }
    public boolean isWideGamut() { return false; }
    public boolean isSrgb() { return true; }
    public float getMinValue(int component) { return 0f; }
    public float getMaxValue(int component) { return 1f; }
    public float[] toXyz(float r, float g, float b) { return new float[] {r, g, b}; }
    public float[] fromXyz(float x, float y, float z) { return new float[] {x, y, z}; }
    public static ColorSpace get(Named name) { return SRGB; }
    public static ColorSpace match(float[] toXYZD50, Rgb.TransferParameters function) { return SRGB; }
    public static Connector connect(ColorSpace source, ColorSpace destination) { return new Connector(); }
    public static Connector connect(ColorSpace source) { return new Connector(); }
    public static ColorSpace adapt(ColorSpace colorSpace, float[] whitePoint) { return colorSpace; }

    public static class Rgb extends ColorSpace {
        Rgb(String name) { super(name); }
        public static class TransferParameters {
            public final double a, b, c, d, e, f, g;
            public TransferParameters(double a, double b, double c, double d, double g) { this(a, b, c, d, 0, 0, g); }
            public TransferParameters(double a, double b, double c, double d, double e, double f, double g) {
                this.a = a; this.b = b; this.c = c; this.d = d; this.e = e; this.f = f; this.g = g;
            }
        }
    }

    public static class Connector {
        public float[] transform(float r, float g, float b) { return new float[] {r, g, b}; }
        public float[] transform(float[] v) { return v; }
        public ColorSpace getSource() { return SRGB; }
        public ColorSpace getDestination() { return SRGB; }
        public RenderIntent getRenderIntent() { return RenderIntent.PERCEPTUAL; }
    }
}
