package android.graphics;

public final class BlendModeColorFilter extends ColorFilter {
    final int mBlendColor;
    private final BlendMode mBlendMode;

    public BlendModeColorFilter(int color, BlendMode mode) {
        mBlendColor = color;
        mBlendMode = mode;
        mMode = mode.toPorterDuff();
        mColor = color;
    }

    public int getColor() { return mBlendColor; }
    public BlendMode getMode() { return mBlendMode; }
}
