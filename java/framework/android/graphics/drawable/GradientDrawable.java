package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Insets;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.util.TypedValue;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class GradientDrawable extends Drawable {
    public static final int RECTANGLE = 0;
    public static final int OVAL = 1;
    public static final int LINE = 2;
    public static final int RING = 3;
    public static final int LINEAR_GRADIENT = 0;
    public static final int RADIAL_GRADIENT = 1;
    public static final int SWEEP_GRADIENT = 2;

    public enum Orientation { TOP_BOTTOM, TR_BL, RIGHT_LEFT, BR_TL, BOTTOM_TOP, BL_TR, LEFT_RIGHT, TL_BR }

    private GradientState mGradientState;
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint mStrokePaint;
    private ColorFilter mColorFilter;
    private PorterDuffColorFilter mTintFilter;
    private int mAlpha = 0xFF;
    private final Path mPath = new Path();
    private final RectF mRect = new RectF();
    private boolean mGradientIsDirty;
    private boolean mMutated;
    private Path mRingPath;
    private boolean mPathIsDirty = true;
    private Rect mPadding;

    public GradientDrawable() { this(new GradientState(Orientation.TOP_BOTTOM, null), null); }

    public GradientDrawable(Orientation orientation, int[] colors) { this(new GradientState(orientation, colors), null); }

    private GradientDrawable(GradientState state, Resources res) {
        mGradientState = state;
        updateLocalState(res);
    }

    private void updateLocalState(Resources res) {
        final GradientState state = mGradientState;
        if (state.mSolidColors != null) {
            final int[] currentState = getState();
            final int stateColor = state.mSolidColors.getColorForState(currentState, 0);
            mFillPaint.setColor(stateColor);
        } else if (state.mGradientColors == null) {
            mFillPaint.setColor(0);
        } else {
            mFillPaint.setColor(Color.BLACK);
        }
        mPadding = state.mPadding;
        if (state.mStrokeWidth >= 0) {
            mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mStrokePaint.setStyle(Paint.Style.STROKE);
            mStrokePaint.setStrokeWidth(state.mStrokeWidth);
            if (state.mStrokeColors != null) {
                final int[] currentState = getState();
                mStrokePaint.setColor(state.mStrokeColors.getColorForState(currentState, 0));
            }
            if (state.mStrokeDashWidth != 0.0f) {
                mStrokePaint.setPathEffect(new DashPathEffect(new float[] {state.mStrokeDashWidth, state.mStrokeDashGap}, 0));
            }
        }
        mTintFilter = updateTintFilter(mTintFilter, state.mTint, state.mTintMode);
        mGradientIsDirty = true;
        state.computeOpacity();
    }

    @Override
    public boolean getPadding(Rect padding) {
        if (mPadding != null) {
            padding.set(mPadding);
            return true;
        }
        return super.getPadding(padding);
    }

    public void setCornerRadii(float[] radii) {
        mGradientState.setCornerRadii(radii);
        mPathIsDirty = true;
        invalidateSelf();
    }

    public float[] getCornerRadii() { return mGradientState.mRadiusArray != null ? mGradientState.mRadiusArray.clone() : null; }

    public void setCornerRadius(float radius) {
        mGradientState.setCornerRadius(radius);
        mPathIsDirty = true;
        invalidateSelf();
    }

    public float getCornerRadius() { return mGradientState.mRadius; }

    public void setStroke(int width, int color) { setStroke(width, color, 0, 0); }
    public void setStroke(int width, ColorStateList colorStateList) { setStroke(width, colorStateList, 0, 0); }

    public void setStroke(int width, int color, float dashWidth, float dashGap) {
        mGradientState.setStroke(width, ColorStateList.valueOf(color), dashWidth, dashGap);
        setStrokeInternal(width, color, dashWidth, dashGap);
    }

    public void setStroke(int width, ColorStateList colorStateList, float dashWidth, float dashGap) {
        mGradientState.setStroke(width, colorStateList, dashWidth, dashGap);
        final int color = colorStateList == null ? Color.TRANSPARENT : colorStateList.getColorForState(getState(), 0);
        setStrokeInternal(width, color, dashWidth, dashGap);
    }

    private void setStrokeInternal(int width, int color, float dashWidth, float dashGap) {
        if (mStrokePaint == null) {
            mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mStrokePaint.setStyle(Paint.Style.STROKE);
        }
        mStrokePaint.setStrokeWidth(width);
        mStrokePaint.setColor(color);
        mStrokePaint.setPathEffect(dashWidth > 0 ? new DashPathEffect(new float[] {dashWidth, dashGap}, 0) : null);
        invalidateSelf();
    }

    public void setSize(int width, int height) {
        mGradientState.setSize(width, height);
        mPathIsDirty = true;
        invalidateSelf();
    }

    public void setShape(int shape) {
        mRingPath = null;
        mPathIsDirty = true;
        mGradientState.setShape(shape);
        invalidateSelf();
    }

    public int getShape() { return mGradientState.mShape; }

    public void setGradientType(int gradient) {
        mGradientState.setGradientType(gradient);
        mGradientIsDirty = true;
        invalidateSelf();
    }

    public int getGradientType() { return mGradientState.mGradient; }

    public void setGradientCenter(float x, float y) {
        mGradientState.setGradientCenter(x, y);
        mGradientIsDirty = true;
        invalidateSelf();
    }

    public float getGradientCenterX() { return mGradientState.mCenterX; }
    public float getGradientCenterY() { return mGradientState.mCenterY; }

    public void setGradientRadius(float gradientRadius) {
        mGradientState.setGradientRadius(gradientRadius, TypedValue.COMPLEX_UNIT_PX);
        mGradientIsDirty = true;
        invalidateSelf();
    }

    public float getGradientRadius() { return mGradientState.mGradientRadius; }

    public void setUseLevel(boolean useLevel) {
        mGradientState.mUseLevel = useLevel;
        mGradientIsDirty = true;
        invalidateSelf();
    }

    public boolean getUseLevel() { return mGradientState.mUseLevel; }
    public Orientation getOrientation() { return mGradientState.mOrientation; }

    public void setOrientation(Orientation orientation) {
        mGradientState.mOrientation = orientation;
        mGradientIsDirty = true;
        invalidateSelf();
    }

    public void setColors(int[] colors) { setColors(colors, null); }

    public void setColors(int[] colors, float[] offsets) {
        mGradientState.setGradientColors(colors);
        mGradientState.mPositions = offsets;
        mGradientIsDirty = true;
        invalidateSelf();
    }

    public int[] getColors() { return mGradientState.mGradientColors == null ? null : mGradientState.mGradientColors.clone(); }

    public void setColor(int argb) {
        mGradientState.setSolidColors(ColorStateList.valueOf(argb));
        mFillPaint.setColor(argb);
        invalidateSelf();
    }

    public void setColor(ColorStateList colorStateList) {
        mGradientState.setSolidColors(colorStateList);
        final int color = colorStateList == null ? Color.TRANSPARENT : colorStateList.getColorForState(getState(), 0);
        mFillPaint.setColor(color);
        invalidateSelf();
    }

    public ColorStateList getColor() { return mGradientState.mSolidColors; }

    @Override
    public void draw(Canvas canvas) {
        if (!ensureValidRect()) return;
        final int prevFillAlpha = mFillPaint.getAlpha();
        final int prevStrokeAlpha = mStrokePaint != null ? mStrokePaint.getAlpha() : 0;
        final int currFillAlpha = modulateAlpha(prevFillAlpha);
        final int currStrokeAlpha = modulateAlpha(prevStrokeAlpha);
        final boolean haveStroke = currStrokeAlpha > 0 && mStrokePaint != null && mStrokePaint.getStrokeWidth() > 0;
        final boolean haveFill = currFillAlpha > 0 || mFillPaint.getShader() != null;
        final GradientState st = mGradientState;
        final ColorFilter colorFilter = mColorFilter != null ? mColorFilter : mTintFilter;
        mFillPaint.setAlpha(currFillAlpha);
        mFillPaint.setColorFilter(colorFilter);
        if (colorFilter != null && st.mSolidColors == null) mFillPaint.setColor(mAlpha << 24);
        if (haveStroke) {
            mStrokePaint.setAlpha(currStrokeAlpha);
            mStrokePaint.setColorFilter(colorFilter);
        }
        switch (st.mShape) {
            case RECTANGLE:
                if (st.mRadiusArray != null) {
                    buildPathIfDirty();
                    if (haveFill) canvas.drawPath(mPath, mFillPaint);
                    if (haveStroke) canvas.drawPath(mPath, mStrokePaint);
                } else if (st.mRadius > 0.0f) {
                    float rad = Math.min(st.mRadius, Math.min(mRect.width(), mRect.height()) * 0.5f);
                    if (haveFill) canvas.drawRoundRect(mRect, rad, rad, mFillPaint);
                    if (haveStroke) canvas.drawRoundRect(mRect, rad, rad, mStrokePaint);
                } else {
                    if (haveFill && (mFillPaint.getColor() != 0 || colorFilter != null || mFillPaint.getShader() != null)) canvas.drawRect(mRect, mFillPaint);
                    if (haveStroke) canvas.drawRect(mRect, mStrokePaint);
                }
                break;
            case OVAL:
                if (haveFill) canvas.drawOval(mRect, mFillPaint);
                if (haveStroke) canvas.drawOval(mRect, mStrokePaint);
                break;
            case LINE: {
                RectF r = mRect;
                float y = r.centerY();
                if (haveStroke) canvas.drawLine(r.left, y, r.right, y, mStrokePaint);
                break;
            }
            case RING:
                Path path = buildRing(st);
                if (haveFill) canvas.drawPath(path, mFillPaint);
                if (haveStroke) canvas.drawPath(path, mStrokePaint);
                break;
        }
        mFillPaint.setAlpha(prevFillAlpha);
        if (haveStroke) mStrokePaint.setAlpha(prevStrokeAlpha);
    }

    private int modulateAlpha(int alpha) {
        int scale = mAlpha + (mAlpha >> 7);
        return alpha * scale >> 8;
    }

    private void buildPathIfDirty() {
        final GradientState st = mGradientState;
        if (mPathIsDirty) {
            ensureValidRect();
            mPath.reset();
            mPath.addRoundRect(mRect, st.mRadiusArray, Path.Direction.CW);
            mPathIsDirty = false;
        }
    }

    private Path buildRing(GradientState st) {
        if (mRingPath != null && (!st.mUseLevelForShape || !mPathIsDirty)) return mRingPath;
        mPathIsDirty = false;
        float sweep = st.mUseLevelForShape ? (360.0f * getLevel() / 10000.0f) : 360f;
        RectF bounds = new RectF(mRect);
        float x = bounds.width() / 2.0f;
        float y = bounds.height() / 2.0f;
        float thickness = st.mThickness != -1 ? st.mThickness : bounds.width() / st.mThicknessRatio;
        float radius = st.mInnerRadius != -1 ? st.mInnerRadius : bounds.width() / st.mInnerRadiusRatio;
        RectF innerBounds = new RectF(bounds);
        innerBounds.inset(x - radius, y - radius);
        bounds = new RectF(innerBounds);
        bounds.inset(-thickness, -thickness);
        if (mRingPath == null) mRingPath = new Path();
        else mRingPath.reset();
        final Path ringPath = mRingPath;
        if (sweep < 360 && sweep > -360) {
            ringPath.setFillType(Path.FillType.EVEN_ODD);
            ringPath.moveTo(x + radius, y);
            ringPath.lineTo(x + radius + thickness, y);
            ringPath.arcTo(bounds, 0.0f, sweep, false);
            ringPath.arcTo(innerBounds, sweep, -sweep, false);
            ringPath.close();
        } else {
            ringPath.addOval(bounds, Path.Direction.CW);
            ringPath.addOval(innerBounds, Path.Direction.CCW);
        }
        return ringPath;
    }

    private boolean ensureValidRect() {
        if (mGradientIsDirty) {
            mGradientIsDirty = false;
            Rect bounds = getBounds();
            float inset = 0;
            if (mStrokePaint != null) inset = mStrokePaint.getStrokeWidth() * 0.5f;
            final GradientState st = mGradientState;
            mRect.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
            final int[] gradientColors = st.mGradientColors;
            if (gradientColors != null) {
                final RectF r = mRect;
                final float x0, x1, y0, y1;
                if (st.mGradient == LINEAR_GRADIENT) {
                    final float level = st.mUseLevel ? getLevel() / 10000.0f : 1.0f;
                    switch (st.mOrientation) {
                        case TOP_BOTTOM: x0 = r.left; y0 = r.top; x1 = x0; y1 = level * r.bottom; break;
                        case TR_BL: x0 = r.right; y0 = r.top; x1 = level * r.left; y1 = level * r.bottom; break;
                        case RIGHT_LEFT: x0 = r.right; y0 = r.top; x1 = level * r.left; y1 = y0; break;
                        case BR_TL: x0 = r.right; y0 = r.bottom; x1 = level * r.left; y1 = level * r.top; break;
                        case BOTTOM_TOP: x0 = r.left; y0 = r.bottom; x1 = x0; y1 = level * r.top; break;
                        case BL_TR: x0 = r.left; y0 = r.bottom; x1 = level * r.right; y1 = level * r.top; break;
                        case LEFT_RIGHT: x0 = r.left; y0 = r.top; x1 = level * r.right; y1 = y0; break;
                        default: x0 = r.left; y0 = r.top; x1 = level * r.right; y1 = level * r.bottom; break;
                    }
                    mFillPaint.setShader(new LinearGradient(x0, y0, x1, y1, gradientColors, st.mPositions, Shader.TileMode.CLAMP));
                } else if (st.mGradient == RADIAL_GRADIENT) {
                    x0 = r.left + (r.right - r.left) * st.mCenterX;
                    y0 = r.top + (r.bottom - r.top) * st.mCenterY;
                    float radius = st.mGradientRadius;
                    if (st.mGradientRadiusType == TypedValue.TYPE_FRACTION) {
                        final float width = st.mWidth >= 0 ? st.mWidth : r.width();
                        final float height = st.mHeight >= 0 ? st.mHeight : r.height();
                        radius *= Math.min(width, height);
                    }
                    if (st.mUseLevel) radius *= getLevel() / 10000.0f;
                    if (radius <= 0) radius = 0.001f;
                    mFillPaint.setShader(new RadialGradient(x0, y0, radius, gradientColors, null, Shader.TileMode.CLAMP));
                } else if (st.mGradient == SWEEP_GRADIENT) {
                    x0 = r.left + (r.right - r.left) * st.mCenterX;
                    y0 = r.top + (r.bottom - r.top) * st.mCenterY;
                    mFillPaint.setShader(new SweepGradient(x0, y0, gradientColors, null));
                }
                if (st.mSolidColors == null) mFillPaint.setColor(Color.BLACK);
            }
        }
        return !mRect.isEmpty();
    }

    @Override
    protected void onBoundsChange(Rect r) {
        super.onBoundsChange(r);
        mRingPath = null;
        mPathIsDirty = true;
        mGradientIsDirty = true;
    }

    @Override
    protected boolean onLevelChange(int level) {
        super.onLevelChange(level);
        mGradientIsDirty = true;
        mPathIsDirty = true;
        invalidateSelf();
        return true;
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        boolean invalidateSelf = false;
        final GradientState s = mGradientState;
        final ColorStateList solidColors = s.mSolidColors;
        if (solidColors != null) {
            final int newColor = solidColors.getColorForState(stateSet, 0);
            final int oldColor = mFillPaint.getColor();
            if (oldColor != newColor) {
                mFillPaint.setColor(newColor);
                invalidateSelf = true;
            }
        }
        final Paint strokePaint = mStrokePaint;
        if (strokePaint != null) {
            final ColorStateList strokeColors = s.mStrokeColors;
            if (strokeColors != null) {
                final int newColor = strokeColors.getColorForState(stateSet, 0);
                final int oldColor = strokePaint.getColor();
                if (oldColor != newColor) {
                    strokePaint.setColor(newColor);
                    invalidateSelf = true;
                }
            }
        }
        if (s.mTint != null && s.mTintMode != null) {
            mTintFilter = updateTintFilter(mTintFilter, s.mTint, s.mTintMode);
            invalidateSelf = true;
        }
        if (invalidateSelf) {
            invalidateSelf();
            return true;
        }
        return false;
    }

    @Override
    public boolean isStateful() {
        final GradientState s = mGradientState;
        return super.isStateful() || (s.mSolidColors != null && s.mSolidColors.isStateful()) || (s.mStrokeColors != null && s.mStrokeColors.isStateful())
                || (s.mTint != null && s.mTint.isStateful());
    }

    @Override
    public boolean hasFocusStateSpecified() {
        final GradientState s = mGradientState;
        return (s.mSolidColors != null && s.mSolidColors.hasFocusStateSpecified()) || (s.mStrokeColors != null && s.mStrokeColors.hasFocusStateSpecified())
                || (s.mTint != null && s.mTint.hasFocusStateSpecified());
    }

    @Override
    public int getChangingConfigurations() { return super.getChangingConfigurations() | mGradientState.getChangingConfigurations(); }

    @Override
    public void setAlpha(int alpha) {
        if (alpha != mAlpha) {
            mAlpha = alpha;
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() { return mAlpha; }

    @Override
    public ColorFilter getColorFilter() { return mColorFilter; }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        if (colorFilter != mColorFilter) {
            mColorFilter = colorFilter;
            invalidateSelf();
        }
    }

    @Override
    public void setTintList(ColorStateList tint) {
        mGradientState.mTint = tint;
        mTintFilter = updateTintFilter(mTintFilter, tint, mGradientState.mTintMode);
        invalidateSelf();
    }

    @Override
    public void setTintMode(PorterDuff.Mode tintMode) {
        mGradientState.mTintMode = tintMode;
        mTintFilter = updateTintFilter(mTintFilter, mGradientState.mTint, tintMode);
        invalidateSelf();
    }

    @Override
    public int getOpacity() { return (mAlpha == 255 && mGradientState.mOpaqueOverBounds && isOpaqueForState()) ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT; }

    private boolean isOpaqueForState() {
        if (mGradientState.mStrokeWidth >= 0 && mStrokePaint != null && !isOpaque(mStrokePaint.getColor())) return false;
        if (mGradientState.mGradientColors == null && !isOpaque(mFillPaint.getColor())) return false;
        return true;
    }

    private static boolean isOpaque(int color) { return ((color >> 24) & 0xff) == 0xff; }

    @Override
    public void getOutline(Outline outline) {
        final GradientState st = mGradientState;
        final Rect bounds = getBounds();
        outline.setAlpha(st.mOpaqueOverShape && isOpaqueForState() ? (mAlpha / 255.0f) : 0.0f);
        switch (st.mShape) {
            case RECTANGLE:
                if (st.mRadiusArray != null) {
                    buildPathIfDirty();
                    outline.setPath(mPath);
                    return;
                }
                float rad = 0;
                if (st.mRadius > 0.0f) rad = Math.min(st.mRadius, Math.min(bounds.width(), bounds.height()) * 0.5f);
                outline.setRoundRect(bounds, rad);
                return;
            case OVAL:
                outline.setOval(bounds);
                return;
            case LINE: {
                final float halfStrokeWidth = (mStrokePaint == null) ? 0.0001f : mStrokePaint.getStrokeWidth() * 0.5f;
                final float centerY = bounds.centerY();
                final int top = (int) Math.floor(centerY - halfStrokeWidth);
                final int bottom = (int) Math.ceil(centerY + halfStrokeWidth);
                outline.setRect(bounds.left, top, bounds.right, bottom);
                return;
            }
            default:
        }
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mGradientState = new GradientState(mGradientState, null);
            updateLocalState(null);
            mMutated = true;
        }
        return this;
    }

    @Override
    public int getIntrinsicWidth() { return mGradientState.mWidth; }
    @Override
    public int getIntrinsicHeight() { return mGradientState.mHeight; }
    @Override
    public Insets getOpticalInsets() { return Insets.NONE; }
    @Override
    public ConstantState getConstantState() { return mGradientState; }

    private static final int[] SHAPE_ATTRS = {android.R.attr.shape, android.R.attr.dither, android.R.attr.innerRadiusRatio,
            android.R.attr.thicknessRatio, android.R.attr.innerRadius, android.R.attr.thickness, android.R.attr.useLevel,
            android.R.attr.tint, android.R.attr.tintMode, android.R.attr.opticalInsetLeft, android.R.attr.visible};
    private static final int[] SIZE_ATTRS = {android.R.attr.width, android.R.attr.height};
    private static final int[] GRADIENT_ATTRS = {android.R.attr.startColor, android.R.attr.centerColor, android.R.attr.endColor,
            android.R.attr.useLevel, android.R.attr.angle, android.R.attr.type, android.R.attr.centerX, android.R.attr.centerY,
            android.R.attr.gradientRadius};
    private static final int[] SOLID_ATTRS = {android.R.attr.color};
    private static final int[] STROKE_ATTRS = {android.R.attr.width, android.R.attr.color, android.R.attr.dashWidth, android.R.attr.dashGap};
    private static final int[] CORNERS_ATTRS = {android.R.attr.radius, android.R.attr.topLeftRadius, android.R.attr.topRightRadius,
            android.R.attr.bottomLeftRadius, android.R.attr.bottomRightRadius};
    private static final int[] PADDING_ATTRS = {android.R.attr.left, android.R.attr.top, android.R.attr.right, android.R.attr.bottom};

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs, theme);
        final GradientState st = mGradientState;
        final TypedArray a = obtainAttributes(r, theme, attrs, SHAPE_ATTRS);
        st.mShape = a.getInt(0, st.mShape);
        if (st.mShape == RING) {
            st.mInnerRadius = a.getDimensionPixelSize(4, st.mInnerRadius);
            if (st.mInnerRadius == -1) st.mInnerRadiusRatio = a.getFloat(2, st.mInnerRadiusRatio);
            st.mThickness = a.getDimensionPixelSize(5, st.mThickness);
            if (st.mThickness == -1) st.mThicknessRatio = a.getFloat(3, st.mThicknessRatio);
            st.mUseLevelForShape = a.getBoolean(6, st.mUseLevelForShape);
        }
        if (a.hasValue(7)) st.mTint = a.getColorStateList(7);
        final int tintMode = a.getInt(8, -1);
        if (tintMode != -1) st.mTintMode = Drawable.parseTintMode(tintMode, PorterDuff.Mode.SRC_IN);
        a.recycle();

        final int innerDepth = parser.getDepth() + 1;
        int type;
        int depth;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (type != XmlPullParser.START_TAG || depth > innerDepth) continue;
            String name = parser.getName();
            if (name.equals("size")) {
                final TypedArray b = obtainAttributes(r, theme, attrs, SIZE_ATTRS);
                st.mWidth = b.getDimensionPixelSize(0, st.mWidth);
                st.mHeight = b.getDimensionPixelSize(1, st.mHeight);
                b.recycle();
            } else if (name.equals("gradient")) {
                final TypedArray b = obtainAttributes(r, theme, attrs, GRADIENT_ATTRS);
                final int startColor = b.getColor(0, 0);
                final boolean hasCenterColor = b.hasValue(1);
                final int centerColor = b.getColor(1, 0);
                final int endColor = b.getColor(2, 0);
                if (hasCenterColor) {
                    st.mGradientColors = new int[] {startColor, centerColor, endColor};
                    st.mPositions = new float[] {0.0f, st.mCenterX != 0.5f ? st.mCenterX : 0.5f, 1f};
                } else {
                    st.mGradientColors = new int[] {startColor, endColor};
                }
                st.mCenterX = getFloatOrFraction(b, 6, st.mCenterX);
                st.mCenterY = getFloatOrFraction(b, 7, st.mCenterY);
                if (hasCenterColor) st.mPositions[1] = st.mGradient == LINEAR_GRADIENT && isVerticalAngle(b) ? st.mCenterY : st.mCenterX;
                st.mUseLevel = b.getBoolean(3, st.mUseLevel);
                st.mGradient = b.getInt(5, st.mGradient);
                int angle = (int) b.getFloat(4, st.mAngle);
                st.mAngle = ((angle % 360) + 360) % 360;
                switch (st.mAngle) {
                    case 0: st.mOrientation = Orientation.LEFT_RIGHT; break;
                    case 45: st.mOrientation = Orientation.BL_TR; break;
                    case 90: st.mOrientation = Orientation.BOTTOM_TOP; break;
                    case 135: st.mOrientation = Orientation.BR_TL; break;
                    case 180: st.mOrientation = Orientation.RIGHT_LEFT; break;
                    case 225: st.mOrientation = Orientation.TR_BL; break;
                    case 270: st.mOrientation = Orientation.TOP_BOTTOM; break;
                    case 315: st.mOrientation = Orientation.TL_BR; break;
                }
                final TypedValue tv = b.peekValue(8);
                if (tv != null) {
                    final float radius;
                    final int radiusType;
                    if (tv.type == TypedValue.TYPE_FRACTION) {
                        radius = tv.getFraction(1.0f, 1.0f);
                        radiusType = TypedValue.TYPE_FRACTION;
                    } else if (tv.type == TypedValue.TYPE_DIMENSION) {
                        radius = tv.getDimension(r.getDisplayMetrics());
                        radiusType = TypedValue.COMPLEX_UNIT_PX;
                    } else {
                        radius = tv.getFloat();
                        radiusType = TypedValue.COMPLEX_UNIT_PX;
                    }
                    st.mGradientRadius = radius;
                    st.mGradientRadiusType = radiusType;
                }
                b.recycle();
            } else if (name.equals("solid")) {
                final TypedArray b = obtainAttributes(r, theme, attrs, SOLID_ATTRS);
                final ColorStateList colorStateList = b.getColorStateList(0);
                if (colorStateList != null) setColor(colorStateList);
                b.recycle();
            } else if (name.equals("stroke")) {
                final TypedArray b = obtainAttributes(r, theme, attrs, STROKE_ATTRS);
                final int width = b.getDimensionPixelSize(0, st.mStrokeWidth);
                final float dashWidth = b.getDimension(2, st.mStrokeDashWidth);
                ColorStateList colorStateList = b.getColorStateList(1);
                if (colorStateList == null) colorStateList = st.mStrokeColors;
                if (dashWidth != 0.0f) {
                    final float dashGap = b.getDimension(3, st.mStrokeDashGap);
                    setStroke(width, colorStateList, dashWidth, dashGap);
                } else {
                    setStroke(width, colorStateList);
                }
                b.recycle();
            } else if (name.equals("corners")) {
                final TypedArray b = obtainAttributes(r, theme, attrs, CORNERS_ATTRS);
                final int radius = b.getDimensionPixelSize(0, (int) st.mRadius);
                setCornerRadius(radius);
                final int topLeftRadius = b.getDimensionPixelSize(1, radius);
                final int topRightRadius = b.getDimensionPixelSize(2, radius);
                final int bottomLeftRadius = b.getDimensionPixelSize(3, radius);
                final int bottomRightRadius = b.getDimensionPixelSize(4, radius);
                if (topLeftRadius != radius || topRightRadius != radius || bottomLeftRadius != radius || bottomRightRadius != radius) {
                    setCornerRadii(new float[] {topLeftRadius, topLeftRadius, topRightRadius, topRightRadius, bottomRightRadius, bottomRightRadius,
                            bottomLeftRadius, bottomLeftRadius});
                }
                b.recycle();
            } else if (name.equals("padding")) {
                final TypedArray b = obtainAttributes(r, theme, attrs, PADDING_ATTRS);
                if (st.mPadding == null) st.mPadding = new Rect();
                st.mPadding.set(b.getDimensionPixelOffset(0, st.mPadding.left), b.getDimensionPixelOffset(1, st.mPadding.top),
                        b.getDimensionPixelOffset(2, st.mPadding.right), b.getDimensionPixelOffset(3, st.mPadding.bottom));
                mPadding = st.mPadding;
                b.recycle();
            }
        }
        mGradientState.computeOpacity();
        updateLocalState(r);
    }

    private static boolean isVerticalAngle(TypedArray a) {
        int angle = (int) a.getFloat(4, 0);
        return angle % 180 == 90;
    }

    private static float getFloatOrFraction(TypedArray a, int index, float defaultValue) {
        TypedValue tv = a.peekValue(index);
        float v = defaultValue;
        if (tv != null) {
            boolean vIsFraction = tv.type == TypedValue.TYPE_FRACTION;
            v = vIsFraction ? tv.getFraction(1.0f, 1.0f) : tv.getFloat();
        }
        return v;
    }

    static final class GradientState extends ConstantState {
        int mChangingConfigurations;
        int mShape = RECTANGLE;
        int mGradient = LINEAR_GRADIENT;
        int mAngle = 0;
        Orientation mOrientation;
        ColorStateList mSolidColors;
        ColorStateList mStrokeColors;
        int[] mGradientColors;
        float[] mPositions;
        int mStrokeWidth = -1;
        float mStrokeDashWidth = 0.0f;
        float mStrokeDashGap = 0.0f;
        float mRadius = 0.0f;
        float[] mRadiusArray = null;
        Rect mPadding = null;
        int mWidth = -1;
        int mHeight = -1;
        float mInnerRadiusRatio = 3.0f;
        float mThicknessRatio = 9.0f;
        int mInnerRadius = -1;
        int mThickness = -1;
        boolean mDither = false;
        float mCenterX = 0.5f;
        float mCenterY = 0.5f;
        float mGradientRadius = 0.5f;
        int mGradientRadiusType = TypedValue.COMPLEX_UNIT_PX;
        boolean mUseLevel = false;
        boolean mUseLevelForShape = true;
        boolean mOpaqueOverBounds;
        boolean mOpaqueOverShape;
        ColorStateList mTint = null;
        PorterDuff.Mode mTintMode = DEFAULT_TINT_MODE;

        GradientState(Orientation orientation, int[] gradientColors) {
            mOrientation = orientation;
            setGradientColors(gradientColors);
        }

        GradientState(GradientState orig, Resources res) {
            mChangingConfigurations = orig.mChangingConfigurations;
            mShape = orig.mShape;
            mGradient = orig.mGradient;
            mAngle = orig.mAngle;
            mOrientation = orig.mOrientation;
            mSolidColors = orig.mSolidColors;
            if (orig.mGradientColors != null) mGradientColors = orig.mGradientColors.clone();
            if (orig.mPositions != null) mPositions = orig.mPositions.clone();
            mStrokeColors = orig.mStrokeColors;
            mStrokeWidth = orig.mStrokeWidth;
            mStrokeDashWidth = orig.mStrokeDashWidth;
            mStrokeDashGap = orig.mStrokeDashGap;
            mRadius = orig.mRadius;
            if (orig.mRadiusArray != null) mRadiusArray = orig.mRadiusArray.clone();
            if (orig.mPadding != null) mPadding = new Rect(orig.mPadding);
            mWidth = orig.mWidth;
            mHeight = orig.mHeight;
            mInnerRadiusRatio = orig.mInnerRadiusRatio;
            mThicknessRatio = orig.mThicknessRatio;
            mInnerRadius = orig.mInnerRadius;
            mThickness = orig.mThickness;
            mDither = orig.mDither;
            mCenterX = orig.mCenterX;
            mCenterY = orig.mCenterY;
            mGradientRadius = orig.mGradientRadius;
            mGradientRadiusType = orig.mGradientRadiusType;
            mUseLevel = orig.mUseLevel;
            mUseLevelForShape = orig.mUseLevelForShape;
            mOpaqueOverBounds = orig.mOpaqueOverBounds;
            mOpaqueOverShape = orig.mOpaqueOverShape;
            mTint = orig.mTint;
            mTintMode = orig.mTintMode;
        }

        @Override
        public Drawable newDrawable() { return new GradientDrawable(this, null); }
        @Override
        public Drawable newDrawable(Resources res) { return new GradientDrawable(this, res); }
        @Override
        public int getChangingConfigurations() { return mChangingConfigurations; }

        public void setShape(int shape) {
            mShape = shape;
            computeOpacity();
        }

        public void setGradientType(int gradient) { mGradient = gradient; }

        public void setGradientCenter(float x, float y) {
            mCenterX = x;
            mCenterY = y;
        }

        public void setGradientColors(int[] colors) {
            mGradientColors = colors;
            mSolidColors = null;
            computeOpacity();
        }

        public void setSolidColors(ColorStateList colors) {
            mGradientColors = null;
            mSolidColors = colors;
            computeOpacity();
        }

        void computeOpacity() {
            mOpaqueOverBounds = false;
            mOpaqueOverShape = false;
            if (mGradientColors != null) {
                for (int i = 0; i < mGradientColors.length; i++) if (!isOpaque(mGradientColors[i])) return;
            }
            if (mGradientColors == null && mSolidColors == null) return;
            mOpaqueOverShape = true;
            mOpaqueOverBounds = mShape == RECTANGLE && mRadius <= 0 && mRadiusArray == null;
        }

        public void setStroke(int width, ColorStateList colors, float dashWidth, float dashGap) {
            mStrokeWidth = width;
            mStrokeColors = colors;
            mStrokeDashWidth = dashWidth;
            mStrokeDashGap = dashGap;
            computeOpacity();
        }

        public void setCornerRadius(float radius) {
            if (radius < 0) radius = 0;
            mRadius = radius;
            mRadiusArray = null;
            computeOpacity();
        }

        public void setCornerRadii(float[] radii) {
            mRadiusArray = radii;
            if (radii == null) mRadius = 0;
            computeOpacity();
        }

        public void setSize(int width, int height) {
            mWidth = width;
            mHeight = height;
        }

        public void setGradientRadius(float gradientRadius, int type) {
            mGradientRadius = gradientRadius;
            mGradientRadiusType = type;
        }
    }
}
