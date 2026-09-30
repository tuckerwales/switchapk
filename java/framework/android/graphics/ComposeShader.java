package android.graphics;

/** Approximated by the destination shader (the renderer draws one shader). */
public class ComposeShader extends Shader {
    public ComposeShader(Shader shaderA, Shader shaderB, Xfermode mode) { this(shaderA, shaderB, PorterDuff.Mode.SRC_OVER); }
    public ComposeShader(Shader shaderA, Shader shaderB, BlendMode blendMode) { this(shaderA, shaderB, PorterDuff.Mode.SRC_OVER); }

    public ComposeShader(Shader shaderA, Shader shaderB, PorterDuff.Mode mode) {
        Shader s = shaderB != null ? shaderB : shaderA;
        mType = s.mType;
        mGeom = s.mGeom;
        mColors = s.mColors;
        mPositions = s.mPositions;
        mTileX = s.mTileX;
        mTileY = s.mTileY;
        mLocal = s.mLocal;
        mBitmap = s.mBitmap;
    }
}
