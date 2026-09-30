package android.graphics;

public class BlurMaskFilter extends MaskFilter {
    public enum Blur { NORMAL, SOLID, OUTER, INNER }
    final float mRadius;
    public BlurMaskFilter(float radius, Blur style) { mRadius = radius; }
}
