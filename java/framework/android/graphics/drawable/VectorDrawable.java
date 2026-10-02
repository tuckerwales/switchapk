package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.PathParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class VectorDrawable extends Drawable {
    private VectorDrawableState mVectorState;
    private PorterDuffColorFilter mTintFilter;
    private ColorFilter mColorFilter;
    private boolean mMutated;

    public VectorDrawable() { mVectorState = new VectorDrawableState(); }

    private VectorDrawable(VectorDrawableState state, Resources res) {
        mVectorState = state;
        mTintFilter = updateTintFilter(mTintFilter, state.mTint, state.mTintMode);
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mVectorState = new VectorDrawableState(mVectorState);
            mMutated = true;
        }
        return this;
    }

    public Object getTargetByName(String name) { return mVectorState.mTargets.get(name); }

    @Override
    public ConstantState getConstantState() { return mVectorState; }

    @Override
    public void draw(Canvas canvas) {
        final Rect bounds = getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        final VectorDrawableState st = mVectorState;
        if (st.mRoot == null || st.mViewportWidth <= 0 || st.mViewportHeight <= 0) return;
        final ColorFilter cf = mColorFilter != null ? mColorFilter : mTintFilter;
        int save = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        if (needMirroring()) {
            canvas.translate(bounds.width(), 0);
            canvas.scale(-1.0f, 1.0f);
        }
        canvas.clipRect(0, 0, bounds.width(), bounds.height());
        canvas.scale(bounds.width() / st.mViewportWidth, bounds.height() / st.mViewportHeight);
        int alpha = (int) (st.mAlpha * 255 + 0.5f);
        if (alpha < 255) canvas.saveLayerAlpha(0, 0, st.mViewportWidth, st.mViewportHeight, alpha);
        st.mRoot.draw(canvas, cf, getState());
        canvas.restoreToCount(save);
    }

    private boolean needMirroring() { return isAutoMirrored() && getLayoutDirection() == 1; }

    @Override
    public int getAlpha() { return (int) (mVectorState.mAlpha * 255 + 0.5f); }

    @Override
    public void setAlpha(int alpha) {
        float a = alpha / 255f;
        if (mVectorState.mAlpha != a) {
            mVectorState.mAlpha = a;
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mColorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public ColorFilter getColorFilter() { return mColorFilter; }

    @Override
    public void setTintList(ColorStateList tint) {
        final VectorDrawableState state = mVectorState;
        if (state.mTint != tint) {
            state.mTint = tint;
            mTintFilter = updateTintFilter(mTintFilter, tint, state.mTintMode);
            invalidateSelf();
        }
    }

    @Override
    public void setTintMode(PorterDuff.Mode tintMode) {
        final VectorDrawableState state = mVectorState;
        if (state.mTintMode != tintMode) {
            state.mTintMode = tintMode;
            mTintFilter = updateTintFilter(mTintFilter, state.mTint, tintMode);
            invalidateSelf();
        }
    }

    @Override
    public boolean isStateful() { return super.isStateful() || (mVectorState != null && mVectorState.isStateful()); }

    @Override
    public boolean hasFocusStateSpecified() { return mVectorState != null && mVectorState.mTint != null && mVectorState.mTint.hasFocusStateSpecified(); }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        boolean changed = false;
        final VectorDrawableState state = mVectorState;
        if (state.mTint != null && state.mTintMode != null) {
            mTintFilter = updateTintFilter(mTintFilter, state.mTint, state.mTintMode);
            changed = true;
        }
        if (state.isStateful()) changed = true;
        if (changed) invalidateSelf();
        return changed;
    }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override
    public int getIntrinsicWidth() { return (int) mVectorState.mBaseWidth; }
    @Override
    public int getIntrinsicHeight() { return (int) mVectorState.mBaseHeight; }
    @Override
    public Insets getOpticalInsets() { return Insets.NONE; }
    @Override
    public boolean canApplyTheme() { return false; }
    @Override
    public void setAutoMirrored(boolean mirrored) { mVectorState.mAutoMirrored = mirrored; }
    @Override
    public boolean isAutoMirrored() { return mVectorState.mAutoMirrored; }

    private static final int[] VECTOR_ATTRS = {android.R.attr.name, android.R.attr.tint, android.R.attr.height, android.R.attr.width,
            android.R.attr.alpha, android.R.attr.autoMirrored, android.R.attr.viewportWidth, android.R.attr.viewportHeight,
            android.R.attr.tintMode, android.R.attr.opticalInsetLeft};
    private static final int[] GROUP_ATTRS = {android.R.attr.name, android.R.attr.pivotX, android.R.attr.pivotY, android.R.attr.scaleX,
            android.R.attr.scaleY, android.R.attr.rotation, android.R.attr.translateX, android.R.attr.translateY};
    private static final int[] PATH_ATTRS = {android.R.attr.name, android.R.attr.fillColor, android.R.attr.pathData,
            android.R.attr.strokeColor, android.R.attr.strokeWidth, android.R.attr.trimPathStart, android.R.attr.trimPathEnd,
            android.R.attr.trimPathOffset, android.R.attr.strokeLineCap, android.R.attr.strokeLineJoin, android.R.attr.strokeMiterLimit,
            android.R.attr.strokeAlpha, android.R.attr.fillAlpha, android.R.attr.fillType};
    private static final int[] CLIP_ATTRS = {android.R.attr.name, android.R.attr.pathData};

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final VectorDrawableState state = mVectorState;
        final TypedArray a = obtainAttributes(r, theme, attrs, VECTOR_ATTRS);
        if (a.hasValue(1)) state.mTint = a.getColorStateList(1);
        final int tintMode = a.getInt(8, -1);
        if (tintMode != -1) state.mTintMode = Drawable.parseTintMode(tintMode, PorterDuff.Mode.SRC_IN);
        state.mViewportWidth = a.getFloat(6, state.mViewportWidth);
        state.mViewportHeight = a.getFloat(7, state.mViewportHeight);
        state.mBaseWidth = a.getDimension(3, state.mBaseWidth);
        state.mBaseHeight = a.getDimension(2, state.mBaseHeight);
        state.mAlpha = a.getFloat(4, state.mAlpha);
        state.mAutoMirrored = a.getBoolean(5, state.mAutoMirrored);
        a.recycle();
        if (state.mViewportWidth <= 0) state.mViewportWidth = state.mBaseWidth;
        if (state.mViewportHeight <= 0) state.mViewportHeight = state.mBaseHeight;

        VGroup root = new VGroup();
        state.mRoot = root;
        ArrayList<VGroup> stack = new ArrayList<VGroup>();
        stack.add(root);
        int eventType = parser.getEventType();
        final int innerDepth = parser.getDepth() + 1;
        while (eventType != XmlPullParser.END_DOCUMENT && (parser.getDepth() >= innerDepth || eventType != XmlPullParser.END_TAG)) {
            eventType = parser.next();
            if (eventType == XmlPullParser.START_TAG) {
                final String tagName = parser.getName();
                final VGroup current = stack.get(stack.size() - 1);
                if ("path".equals(tagName)) {
                    VPath p = new VPath();
                    final TypedArray b = obtainAttributes(r, theme, attrs, PATH_ATTRS);
                    p.mName = b.getString(0);
                    String data = b.getString(2);
                    if (data != null) {
                        p.mPath = PathParser.createPathFromPathData(data);
                        p.mNodes = new PathParser.PathData(data);
                    }
                    p.mFillColor = safeColors(b, 1);
                    p.mStrokeColor = safeColors(b, 3);
                    p.mStrokeWidth = b.getFloat(4, 0);
                    p.mTrimStart = b.getFloat(5, 0);
                    p.mTrimEnd = b.getFloat(6, 1);
                    p.mTrimOffset = b.getFloat(7, 0);
                    p.mCap = b.getInt(8, 0);
                    p.mJoin = b.getInt(9, 0);
                    p.mMiter = b.getFloat(10, 4);
                    p.mStrokeAlpha = b.getFloat(11, 1);
                    p.mFillAlpha = b.getFloat(12, 1);
                    p.mFillType = b.getInt(13, 0);
                    b.recycle();
                    current.mChildren.add(p);
                    if (p.mName != null) state.mTargets.put(p.mName, p);
                } else if ("clip-path".equals(tagName)) {
                    VPath p = new VPath();
                    p.mClip = true;
                    final TypedArray b = obtainAttributes(r, theme, attrs, CLIP_ATTRS);
                    p.mName = b.getString(0);
                    String data = b.getString(1);
                    if (data != null) {
                        p.mPath = PathParser.createPathFromPathData(data);
                        p.mNodes = new PathParser.PathData(data);
                    }
                    b.recycle();
                    current.mChildren.add(p);
                    if (p.mName != null) state.mTargets.put(p.mName, p);
                } else if ("group".equals(tagName)) {
                    VGroup g = new VGroup();
                    final TypedArray b = obtainAttributes(r, theme, attrs, GROUP_ATTRS);
                    g.mName = b.getString(0);
                    g.mPivotX = b.getFloat(1, 0);
                    g.mPivotY = b.getFloat(2, 0);
                    g.mScaleX = b.getFloat(3, 1);
                    g.mScaleY = b.getFloat(4, 1);
                    g.mRotate = b.getFloat(5, 0);
                    g.mTranslateX = b.getFloat(6, 0);
                    g.mTranslateY = b.getFloat(7, 0);
                    b.recycle();
                    current.mChildren.add(g);
                    stack.add(g);
                    if (g.mName != null) state.mTargets.put(g.mName, g);
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if ("group".equals(parser.getName()) && stack.size() > 1) stack.remove(stack.size() - 1);
            }
        }
        mTintFilter = updateTintFilter(mTintFilter, state.mTint, state.mTintMode);
    }

    /** Colors may be gradients (complex colors); those fall back to their first stop color. */
    private static ColorStateList safeColors(TypedArray a, int index) {
        if (!a.hasValue(index)) return null;
        try {
            return a.getColorStateList(index);
        } catch (RuntimeException e) {
            return ColorStateList.valueOf(0xFF888888);
        }
    }

    /** A group node (transform + children). */
    public static class VGroup {
        String mName;
        float mRotate, mPivotX, mPivotY, mScaleX = 1, mScaleY = 1, mTranslateX, mTranslateY;
        final ArrayList<Object> mChildren = new ArrayList<Object>();

        VGroup() {}

        VGroup(VGroup copy) {
            mName = copy.mName;
            mRotate = copy.mRotate;
            mPivotX = copy.mPivotX;
            mPivotY = copy.mPivotY;
            mScaleX = copy.mScaleX;
            mScaleY = copy.mScaleY;
            mTranslateX = copy.mTranslateX;
            mTranslateY = copy.mTranslateY;
            for (Object c : copy.mChildren) mChildren.add(c instanceof VGroup ? new VGroup((VGroup) c) : new VPath((VPath) c));
        }

        void draw(Canvas canvas, ColorFilter cf, int[] state) {
            int save = canvas.save();
            canvas.translate(mTranslateX + mPivotX, mTranslateY + mPivotY);
            canvas.rotate(mRotate);
            canvas.scale(mScaleX, mScaleY);
            canvas.translate(-mPivotX, -mPivotY);
            for (Object c : mChildren) {
                if (c instanceof VGroup) ((VGroup) c).draw(canvas, cf, state);
                else ((VPath) c).draw(canvas, cf, state);
            }
            canvas.restoreToCount(save);
        }

        boolean isStateful() {
            for (Object c : mChildren) {
                if (c instanceof VGroup && ((VGroup) c).isStateful()) return true;
                if (c instanceof VPath && ((VPath) c).isStateful()) return true;
            }
            return false;
        }

        public void setRotation(float v) { mRotate = v; }
        public float getRotation() { return mRotate; }
        public void setPivotX(float v) { mPivotX = v; }
        public float getPivotX() { return mPivotX; }
        public void setPivotY(float v) { mPivotY = v; }
        public float getPivotY() { return mPivotY; }
        public void setScaleX(float v) { mScaleX = v; }
        public float getScaleX() { return mScaleX; }
        public void setScaleY(float v) { mScaleY = v; }
        public float getScaleY() { return mScaleY; }
        public void setTranslateX(float v) { mTranslateX = v; }
        public float getTranslateX() { return mTranslateX; }
        public void setTranslateY(float v) { mTranslateY = v; }
        public float getTranslateY() { return mTranslateY; }
    }

    /** A path or clip-path node. */
    public static class VPath {
        String mName;
        Path mPath;
        PathParser.PathData mNodes;
        boolean mClip;
        ColorStateList mFillColor, mStrokeColor;
        float mStrokeWidth, mTrimStart, mTrimEnd = 1, mTrimOffset, mMiter = 4, mStrokeAlpha = 1, mFillAlpha = 1;
        int mCap, mJoin, mFillType;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        VPath() {}

        VPath(VPath c) {
            mName = c.mName;
            mPath = c.mPath != null ? new Path(c.mPath) : null;
            mNodes = c.mNodes != null ? new PathParser.PathData(c.mNodes) : null;
            mClip = c.mClip;
            mFillColor = c.mFillColor;
            mStrokeColor = c.mStrokeColor;
            mStrokeWidth = c.mStrokeWidth;
            mTrimStart = c.mTrimStart;
            mTrimEnd = c.mTrimEnd;
            mTrimOffset = c.mTrimOffset;
            mMiter = c.mMiter;
            mStrokeAlpha = c.mStrokeAlpha;
            mFillAlpha = c.mFillAlpha;
            mCap = c.mCap;
            mJoin = c.mJoin;
            mFillType = c.mFillType;
        }

        boolean isStateful() {
            return (mFillColor != null && mFillColor.isStateful()) || (mStrokeColor != null && mStrokeColor.isStateful());
        }

        private Path renderPath() {
            if (mTrimStart == 0 && mTrimEnd == 1) return mPath;
            PathMeasure pm = new PathMeasure(mPath, false);
            float len = pm.getLength();
            float start = ((mTrimStart + mTrimOffset) % 1f) * len;
            float end = ((mTrimEnd + mTrimOffset) % 1f) * len;
            Path out = new Path();
            if (start > end) {
                pm.getSegment(start, len, out, true);
                pm.getSegment(0, end, out, true);
            } else {
                pm.getSegment(start, end, out, true);
            }
            return out;
        }

        void draw(Canvas canvas, ColorFilter cf, int[] state) {
            if (mPath == null) return;
            if (mClip) {
                canvas.clipPath(mPath);
                return;
            }
            Path p = renderPath();
            p.setFillType(mFillType == 1 ? Path.FillType.EVEN_ODD : Path.FillType.WINDING);
            mPaint.setColorFilter(cf);
            if (mFillColor != null) {
                int c = mFillColor.getColorForState(state, mFillColor.getDefaultColor());
                c = applyAlpha(c, mFillAlpha);
                if ((c >>> 24) != 0) {
                    mPaint.setStyle(Paint.Style.FILL);
                    mPaint.setColor(c);
                    canvas.drawPath(p, mPaint);
                }
            }
            if (mStrokeColor != null && mStrokeWidth > 0) {
                int c = mStrokeColor.getColorForState(state, mStrokeColor.getDefaultColor());
                c = applyAlpha(c, mStrokeAlpha);
                if ((c >>> 24) != 0) {
                    mPaint.setStyle(Paint.Style.STROKE);
                    mPaint.setColor(c);
                    mPaint.setStrokeWidth(mStrokeWidth);
                    mPaint.setStrokeCap(mCap == 1 ? Paint.Cap.ROUND : mCap == 2 ? Paint.Cap.SQUARE : Paint.Cap.BUTT);
                    mPaint.setStrokeJoin(mJoin == 1 ? Paint.Join.ROUND : mJoin == 2 ? Paint.Join.BEVEL : Paint.Join.MITER);
                    mPaint.setStrokeMiter(mMiter);
                    canvas.drawPath(p, mPaint);
                }
            }
        }

        private static int applyAlpha(int color, float alpha) {
            int a = (int) ((color >>> 24) * alpha);
            return (color & 0xFFFFFF) | (a << 24);
        }

        /** Rebuilds the drawn path. A morphable value copies parameters; any other value replaces them. */
        public void setPathData(PathParser.PathData data) {
            if (data == null) return;
            if (mNodes != null && PathParser.canMorph(mNodes, data)) mNodes.setPathData(data);
            else mNodes = new PathParser.PathData(data);
            if (mPath == null) mPath = new Path();
            else mPath.reset();
            mNodes.toPath(mPath);
        }

        public PathParser.PathData getPathData() {
            return mNodes == null ? new PathParser.PathData() : new PathParser.PathData(mNodes);
        }
        public void setTrimPathStart(float v) { mTrimStart = v; }
        public float getTrimPathStart() { return mTrimStart; }
        public void setTrimPathEnd(float v) { mTrimEnd = v; }
        public float getTrimPathEnd() { return mTrimEnd; }
        public void setTrimPathOffset(float v) { mTrimOffset = v; }
        public float getTrimPathOffset() { return mTrimOffset; }
        public void setStrokeWidth(float v) { mStrokeWidth = v; }
        public float getStrokeWidth() { return mStrokeWidth; }
        public void setStrokeAlpha(float v) { mStrokeAlpha = v; }
        public float getStrokeAlpha() { return mStrokeAlpha; }
        public void setFillAlpha(float v) { mFillAlpha = v; }
        public float getFillAlpha() { return mFillAlpha; }
        public void setFillColor(int c) { mFillColor = ColorStateList.valueOf(c); }
        public int getFillColor() { return mFillColor != null ? mFillColor.getDefaultColor() : 0; }
        public void setStrokeColor(int c) { mStrokeColor = ColorStateList.valueOf(c); }
        public int getStrokeColor() { return mStrokeColor != null ? mStrokeColor.getDefaultColor() : 0; }
    }

    static class VectorDrawableState extends ConstantState {
        int mChangingConfigurations;
        ColorStateList mTint = null;
        PorterDuff.Mode mTintMode = DEFAULT_TINT_MODE;
        boolean mAutoMirrored;
        float mBaseWidth = 0, mBaseHeight = 0, mViewportWidth = 0, mViewportHeight = 0;
        float mAlpha = 1f;
        VGroup mRoot;
        final HashMap<String, Object> mTargets = new HashMap<String, Object>();

        VectorDrawableState() {}

        VectorDrawableState(VectorDrawableState copy) {
            mChangingConfigurations = copy.mChangingConfigurations;
            mTint = copy.mTint;
            mTintMode = copy.mTintMode;
            mAutoMirrored = copy.mAutoMirrored;
            mBaseWidth = copy.mBaseWidth;
            mBaseHeight = copy.mBaseHeight;
            mViewportWidth = copy.mViewportWidth;
            mViewportHeight = copy.mViewportHeight;
            mAlpha = copy.mAlpha;
            if (copy.mRoot != null) {
                mRoot = new VGroup(copy.mRoot);
                collect(mRoot);
            }
        }

        private void collect(VGroup g) {
            if (g.mName != null) mTargets.put(g.mName, g);
            for (Object c : g.mChildren) {
                if (c instanceof VGroup) collect((VGroup) c);
                else if (((VPath) c).mName != null) mTargets.put(((VPath) c).mName, c);
            }
        }

        boolean isStateful() { return (mTint != null && mTint.isStateful()) || (mRoot != null && mRoot.isStateful()); }

        @Override
        public Drawable newDrawable() { return new VectorDrawable(new VectorDrawableState(this), null); }
        @Override
        public Drawable newDrawable(Resources res) { return new VectorDrawable(new VectorDrawableState(this), res); }
        @Override
        public int getChangingConfigurations() { return mChangingConfigurations; }
    }
}
