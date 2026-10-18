package android.graphics;

/**
 * An effect applied to a RenderNode's content. RenderNode draws the offset and color filter effects (also inside a
 * chain); blurs, bitmaps, shaders and blend modes are kept but draw the content unchanged. createRuntimeShaderEffect
 * is absent: there is no RuntimeShader (AGSL) here.
 */
public final class RenderEffect {
    static final int OFFSET = 1, BLUR = 2, BITMAP = 3, COLOR_FILTER = 4, BLEND = 5, CHAIN = 6, SHADER = 7;

    final int kind;
    final float dx, dy;
    final RenderEffect input, second;
    final ColorFilter colorFilter;
    final Shader.TileMode tileMode;
    final Bitmap bitmap;
    final Shader shader;
    final BlendMode blendMode;

    private RenderEffect(int kind, float dx, float dy, RenderEffect input, RenderEffect second, ColorFilter colorFilter,
            Shader.TileMode tileMode, Bitmap bitmap, Shader shader, BlendMode blendMode) {
        this.kind = kind;
        this.dx = dx;
        this.dy = dy;
        this.input = input;
        this.second = second;
        this.colorFilter = colorFilter;
        this.tileMode = tileMode;
        this.bitmap = bitmap;
        this.shader = shader;
        this.blendMode = blendMode;
    }

    public static RenderEffect createOffsetEffect(float offsetX, float offsetY) {
        return new RenderEffect(OFFSET, offsetX, offsetY, null, null, null, null, null, null, null);
    }

    public static RenderEffect createOffsetEffect(float offsetX, float offsetY, RenderEffect input) {
        return new RenderEffect(OFFSET, offsetX, offsetY, input, null, null, null, null, null, null);
    }

    public static RenderEffect createBlurEffect(float radiusX, float radiusY, RenderEffect inputEffect,
            Shader.TileMode edgeTreatment) {
        return new RenderEffect(BLUR, radiusX, radiusY, inputEffect, null, null, edgeTreatment, null, null, null);
    }

    public static RenderEffect createBlurEffect(float radiusX, float radiusY, Shader.TileMode edgeTreatment) {
        return createBlurEffect(radiusX, radiusY, null, edgeTreatment);
    }

    public static RenderEffect createBitmapEffect(Bitmap bitmap) {
        return new RenderEffect(BITMAP, 0, 0, null, null, null, null, bitmap, null, null);
    }

    public static RenderEffect createBitmapEffect(Bitmap bitmap, Rect src, Rect dst) {
        return createBitmapEffect(bitmap);
    }

    public static RenderEffect createColorFilterEffect(ColorFilter colorFilter, RenderEffect renderEffect) {
        return new RenderEffect(COLOR_FILTER, 0, 0, renderEffect, null, colorFilter, null, null, null, null);
    }

    public static RenderEffect createColorFilterEffect(ColorFilter colorFilter) {
        return createColorFilterEffect(colorFilter, null);
    }

    public static RenderEffect createBlendModeEffect(RenderEffect dst, RenderEffect src, BlendMode blendMode) {
        return new RenderEffect(BLEND, 0, 0, dst, src, null, null, null, null, blendMode);
    }

    public static RenderEffect createChainEffect(RenderEffect outer, RenderEffect inner) {
        return new RenderEffect(CHAIN, 0, 0, inner, outer, null, null, null, null, null);
    }

    public static RenderEffect createShaderEffect(Shader shader) {
        return new RenderEffect(SHADER, 0, 0, null, null, null, null, null, shader, null);
    }

    /** The total offset and the color filters this effect applies, for RenderNode (inner effects first). */
    void collect(float[] offset, java.util.List<ColorFilter> filters) {
        if (input != null) input.collect(offset, filters);
        if (kind == OFFSET) {
            offset[0] += dx;
            offset[1] += dy;
        } else if (kind == COLOR_FILTER && colorFilter != null) {
            filters.add(colorFilter);
        } else if (kind == CHAIN && second != null) {
            second.collect(offset, filters);
        }
    }
}
