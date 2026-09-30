package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class LayerDrawable extends Drawable implements Drawable.Callback {
    public static final int PADDING_MODE_NEST = 0;
    public static final int PADDING_MODE_STACK = 1;
    public static final int INSET_UNDEFINED = Integer.MIN_VALUE;

    LayerState mLayerState;
    private final Rect mTmpRect = new Rect();
    private final Rect mTmpOutRect = new Rect();
    private final Rect mTmpContainer = new Rect();
    private final Rect mHotspotBounds = new Rect();
    private boolean mMutated;
    private int[] mPaddingL = new int[0], mPaddingT = new int[0], mPaddingR = new int[0], mPaddingB = new int[0];

    public LayerDrawable(Drawable[] layers) { this(layers, null); }

    LayerDrawable(Drawable[] layers, LayerState state) {
        this(state, null);
        if (layers == null) throw new IllegalArgumentException("layers must be non-null");
        final int length = layers.length;
        final ChildDrawable[] r = new ChildDrawable[length];
        for (int i = 0; i < length; i++) {
            r[i] = new ChildDrawable();
            r[i].mDrawable = layers[i];
            if (layers[i] != null) layers[i].setCallback(this);
        }
        mLayerState.mNumChildren = length;
        mLayerState.mChildren = r;
        ensurePadding();
        refreshPadding();
    }

    LayerDrawable() { this((LayerState) null, null); }

    LayerDrawable(LayerState state, Resources res) {
        mLayerState = createConstantState(state, res);
        if (mLayerState.mNumChildren > 0) {
            ensurePadding();
            refreshPadding();
        }
    }

    LayerState createConstantState(LayerState state, Resources res) { return new LayerState(state, this, res); }

    private static final int[] ATTRS = {android.R.attr.paddingMode, android.R.attr.paddingLeft, android.R.attr.paddingTop,
            android.R.attr.paddingRight, android.R.attr.paddingBottom, android.R.attr.paddingStart, android.R.attr.paddingEnd,
            android.R.attr.opacity, android.R.attr.autoMirrored};

    private static final int[] ITEM_ATTRS = {android.R.attr.drawable, android.R.attr.id, android.R.attr.left, android.R.attr.top,
            android.R.attr.right, android.R.attr.bottom, android.R.attr.start, android.R.attr.end, android.R.attr.width,
            android.R.attr.height, android.R.attr.gravity};

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs, theme);
        final TypedArray a = obtainAttributes(r, theme, attrs, ATTRS);
        mLayerState.mPaddingMode = a.getInt(0, mLayerState.mPaddingMode);
        mLayerState.mPaddingLeft = a.getDimensionPixelOffset(1, mLayerState.mPaddingLeft);
        mLayerState.mPaddingTop = a.getDimensionPixelOffset(2, mLayerState.mPaddingTop);
        mLayerState.mPaddingRight = a.getDimensionPixelOffset(3, mLayerState.mPaddingRight);
        mLayerState.mPaddingBottom = a.getDimensionPixelOffset(4, mLayerState.mPaddingBottom);
        if (a.hasValue(5)) mLayerState.mPaddingLeft = a.getDimensionPixelOffset(5, 0);
        if (a.hasValue(6)) mLayerState.mPaddingRight = a.getDimensionPixelOffset(6, 0);
        mLayerState.mOpacityOverride = a.getInt(7, mLayerState.mOpacityOverride);
        mLayerState.mAutoMirrored = a.getBoolean(8, false);
        a.recycle();
        inflateLayers(r, parser, attrs, theme);
        ensurePadding();
        refreshPadding();
    }

    private void inflateLayers(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final LayerState state = mLayerState;
        final int innerDepth = parser.getDepth() + 1;
        int type;
        int depth;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (type != XmlPullParser.START_TAG) continue;
            if (depth > innerDepth || !parser.getName().equals("item")) continue;
            final ChildDrawable layer = new ChildDrawable();
            final TypedArray a = obtainAttributes(r, theme, attrs, ITEM_ATTRS);
            layer.mInsetL = a.getDimensionPixelOffset(2, 0);
            layer.mInsetT = a.getDimensionPixelOffset(3, 0);
            layer.mInsetR = a.getDimensionPixelOffset(4, 0);
            layer.mInsetB = a.getDimensionPixelOffset(5, 0);
            if (a.hasValue(6)) layer.mInsetL = a.getDimensionPixelOffset(6, 0);
            if (a.hasValue(7)) layer.mInsetR = a.getDimensionPixelOffset(7, 0);
            layer.mWidth = a.getDimensionPixelSize(8, -1);
            layer.mHeight = a.getDimensionPixelSize(9, -1);
            layer.mGravity = a.getInt(10, Gravity.NO_GRAVITY);
            layer.mId = a.getResourceId(1, View_NO_ID);
            final Drawable dr = a.getDrawable(0);
            a.recycle();
            if (dr != null) {
                layer.mDrawable = dr;
            } else {
                while ((type = parser.next()) == XmlPullParser.TEXT) {}
                if (type == XmlPullParser.START_TAG) layer.mDrawable = Drawable.createFromXmlInner(r, parser, attrs, theme);
            }
            if (layer.mDrawable != null) {
                state.mChildrenChangingConfigurations |= layer.mDrawable.getChangingConfigurations();
                layer.mDrawable.setCallback(this);
            }
            addLayer(layer);
        }
    }

    static final int View_NO_ID = -1;

    int addLayer(ChildDrawable layer) {
        final LayerState st = mLayerState;
        final int N = st.mChildren != null ? st.mChildren.length : 0;
        final int i = st.mNumChildren;
        if (i >= N) {
            final ChildDrawable[] nu = new ChildDrawable[N + 10];
            if (i > 0) System.arraycopy(st.mChildren, 0, nu, 0, i);
            st.mChildren = nu;
        }
        st.mChildren[i] = layer;
        st.mNumChildren++;
        st.invalidateCache();
        return i;
    }

    public int addLayer(Drawable dr) {
        final ChildDrawable layer = createLayer(dr);
        final int index = addLayer(layer);
        ensurePadding();
        refreshChildPadding(index, layer);
        return index;
    }

    private ChildDrawable createLayer(Drawable dr) {
        final ChildDrawable layer = new ChildDrawable();
        layer.mDrawable = dr;
        if (dr != null) {
            dr.setCallback(this);
            dr.setLayoutDirection(getLayoutDirection());
            dr.setBounds(getBounds());
        }
        return layer;
    }

    public Drawable findDrawableByLayerId(int id) {
        final ChildDrawable[] layers = mLayerState.mChildren;
        for (int i = mLayerState.mNumChildren - 1; i >= 0; i--) if (layers[i].mId == id) return layers[i].mDrawable;
        return null;
    }

    public void setId(int index, int id) { mLayerState.mChildren[index].mId = id; }
    public int getId(int index) { return mLayerState.mChildren[index].mId; }
    public int getNumberOfLayers() { return mLayerState.mNumChildren; }

    public boolean setDrawableByLayerId(int id, Drawable drawable) {
        final int index = findIndexByLayerId(id);
        if (index < 0) return false;
        setDrawable(index, drawable);
        return true;
    }

    public int findIndexByLayerId(int id) {
        final ChildDrawable[] layers = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) if (layers[i].mId == id) return i;
        return -1;
    }

    public void setDrawable(int index, Drawable drawable) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        if (childDrawable.mDrawable != null) {
            if (drawable != null) drawable.setBounds(childDrawable.mDrawable.getBounds());
            childDrawable.mDrawable.setCallback(null);
        }
        if (drawable != null) drawable.setCallback(this);
        childDrawable.mDrawable = drawable;
        mLayerState.invalidateCache();
        refreshChildPadding(index, childDrawable);
    }

    public Drawable getDrawable(int index) {
        if (index >= mLayerState.mNumChildren) throw new IndexOutOfBoundsException();
        return mLayerState.mChildren[index].mDrawable;
    }

    public void setLayerSize(int index, int w, int h) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        childDrawable.mWidth = w;
        childDrawable.mHeight = h;
    }

    public void setLayerWidth(int index, int w) { mLayerState.mChildren[index].mWidth = w; }
    public int getLayerWidth(int index) { return mLayerState.mChildren[index].mWidth; }
    public void setLayerHeight(int index, int h) { mLayerState.mChildren[index].mHeight = h; }
    public int getLayerHeight(int index) { return mLayerState.mChildren[index].mHeight; }
    public void setLayerGravity(int index, int gravity) { mLayerState.mChildren[index].mGravity = gravity; }
    public int getLayerGravity(int index) { return mLayerState.mChildren[index].mGravity; }

    public void setLayerInset(int index, int l, int t, int r, int b) {
        final ChildDrawable childDrawable = mLayerState.mChildren[index];
        childDrawable.mInsetL = l;
        childDrawable.mInsetT = t;
        childDrawable.mInsetR = r;
        childDrawable.mInsetB = b;
    }

    public void setLayerInsetRelative(int index, int s, int t, int e, int b) { setLayerInset(index, s, t, e, b); }
    public void setLayerInsetLeft(int index, int l) { mLayerState.mChildren[index].mInsetL = l; }
    public int getLayerInsetLeft(int index) { return mLayerState.mChildren[index].mInsetL; }
    public void setLayerInsetRight(int index, int r) { mLayerState.mChildren[index].mInsetR = r; }
    public int getLayerInsetRight(int index) { return mLayerState.mChildren[index].mInsetR; }
    public void setLayerInsetTop(int index, int t) { mLayerState.mChildren[index].mInsetT = t; }
    public int getLayerInsetTop(int index) { return mLayerState.mChildren[index].mInsetT; }
    public void setLayerInsetBottom(int index, int b) { mLayerState.mChildren[index].mInsetB = b; }
    public int getLayerInsetBottom(int index) { return mLayerState.mChildren[index].mInsetB; }
    public void setLayerInsetStart(int index, int s) { setLayerInsetLeft(index, s); }
    public int getLayerInsetStart(int index) { return getLayerInsetLeft(index); }
    public void setLayerInsetEnd(int index, int e) { setLayerInsetRight(index, e); }
    public int getLayerInsetEnd(int index) { return getLayerInsetRight(index); }
    public void setPaddingMode(int mode) { mLayerState.mPaddingMode = mode; }
    public int getPaddingMode() { return mLayerState.mPaddingMode; }

    @Override
    public void invalidateDrawable(Drawable who) { invalidateSelf(); }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }

    @Override
    public void draw(Canvas canvas) {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) dr.draw(canvas);
        }
    }

    @Override
    public int getChangingConfigurations() { return super.getChangingConfigurations() | mLayerState.getChangingConfigurations(); }

    @Override
    public boolean getPadding(Rect padding) {
        final LayerState layerState = mLayerState;
        if (layerState.mPaddingMode == PADDING_MODE_NEST) computeNestedPadding(padding);
        else computeStackedPadding(padding);
        padding.left += layerState.mPaddingLeft;
        padding.top += layerState.mPaddingTop;
        padding.right += layerState.mPaddingRight;
        padding.bottom += layerState.mPaddingBottom;
        return padding.left != 0 || padding.top != 0 || padding.right != 0 || padding.bottom != 0;
    }

    public void setPadding(int left, int top, int right, int bottom) {
        mLayerState.mPaddingLeft = left;
        mLayerState.mPaddingTop = top;
        mLayerState.mPaddingRight = right;
        mLayerState.mPaddingBottom = bottom;
    }

    public void setPaddingRelative(int start, int top, int end, int bottom) { setPadding(start, top, end, bottom); }
    public int getLeftPadding() { return mLayerState.mPaddingLeft; }
    public int getRightPadding() { return mLayerState.mPaddingRight; }
    public int getStartPadding() { return mLayerState.mPaddingLeft; }
    public int getEndPadding() { return mLayerState.mPaddingRight; }
    public int getTopPadding() { return mLayerState.mPaddingTop; }
    public int getBottomPadding() { return mLayerState.mPaddingBottom; }

    private void computeNestedPadding(Rect padding) {
        padding.set(0, 0, 0, 0);
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            refreshChildPadding(i, mLayerState.mChildren[i]);
            padding.left += mPaddingL[i];
            padding.top += mPaddingT[i];
            padding.right += mPaddingR[i];
            padding.bottom += mPaddingB[i];
        }
    }

    private void computeStackedPadding(Rect padding) {
        padding.set(0, 0, 0, 0);
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            refreshChildPadding(i, mLayerState.mChildren[i]);
            padding.left = Math.max(padding.left, mPaddingL[i]);
            padding.top = Math.max(padding.top, mPaddingT[i]);
            padding.right = Math.max(padding.right, mPaddingR[i]);
            padding.bottom = Math.max(padding.bottom, mPaddingB[i]);
        }
    }

    @Override
    public void getOutline(Outline outline) {
        final ChildDrawable[] array = mLayerState.mChildren;
        final int N = mLayerState.mNumChildren;
        for (int i = 0; i < N; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null) {
                dr.getOutline(outline);
                if (!outline.isEmpty()) return;
            }
        }
    }

    @Override
    public void setHotspot(float x, float y) {
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) if (array[i].mDrawable != null) array[i].mDrawable.setHotspot(x, y);
    }

    @Override
    public void setHotspotBounds(int left, int top, int right, int bottom) {
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) if (array[i].mDrawable != null) array[i].mDrawable.setHotspotBounds(left, top, right, bottom);
        mHotspotBounds.set(left, top, right, bottom);
    }

    @Override
    public void getHotspotBounds(Rect outRect) {
        if (!mHotspotBounds.isEmpty()) outRect.set(mHotspotBounds);
        else super.getHotspotBounds(outRect);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        final boolean changed = super.setVisible(visible, restart);
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) if (array[i].mDrawable != null) array[i].mDrawable.setVisible(visible, restart);
        return changed;
    }

    @Override
    public void setAlpha(int alpha) {
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) if (array[i].mDrawable != null) array[i].mDrawable.setAlpha(alpha);
    }

    @Override
    public int getAlpha() {
        final Drawable dr = getFirstNonNullDrawable();
        return dr != null ? dr.getAlpha() : super.getAlpha();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) if (array[i].mDrawable != null) array[i].mDrawable.setColorFilter(colorFilter);
    }

    @Override
    public void setTintList(ColorStateList tint) {
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) if (array[i].mDrawable != null) array[i].mDrawable.setTintList(tint);
    }

    @Override
    public void setTintMode(PorterDuff.Mode tintMode) {
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) if (array[i].mDrawable != null) array[i].mDrawable.setTintMode(tintMode);
    }

    private Drawable getFirstNonNullDrawable() {
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) if (array[i].mDrawable != null) return array[i].mDrawable;
        return null;
    }

    public void setOpacity(int opacity) { mLayerState.mOpacityOverride = opacity; }

    @Override
    public int getOpacity() {
        if (mLayerState.mOpacityOverride != PixelFormat.UNKNOWN) return mLayerState.mOpacityOverride;
        return mLayerState.getOpacity();
    }

    @Override
    public void setAutoMirrored(boolean mirrored) {
        mLayerState.mAutoMirrored = mirrored;
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) if (array[i].mDrawable != null) array[i].mDrawable.setAutoMirrored(mirrored);
    }

    @Override
    public boolean isAutoMirrored() { return mLayerState.mAutoMirrored; }

    @Override
    public void jumpToCurrentState() {
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) if (array[i].mDrawable != null) array[i].mDrawable.jumpToCurrentState();
    }

    @Override
    public boolean isStateful() { return mLayerState.isStateful(); }

    @Override
    public boolean hasFocusStateSpecified() {
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) if (array[i].mDrawable != null && array[i].mDrawable.hasFocusStateSpecified()) return true;
        return false;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean changed = false;
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null && dr.isStateful() && dr.setState(state)) {
                refreshChildPadding(i, array[i]);
                changed = true;
            }
        }
        if (changed) updateLayerBounds(getBounds());
        return changed;
    }

    @Override
    protected boolean onLevelChange(int level) {
        boolean changed = false;
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) {
            final Drawable dr = array[i].mDrawable;
            if (dr != null && dr.setLevel(level)) {
                refreshChildPadding(i, array[i]);
                changed = true;
            }
        }
        if (changed) updateLayerBounds(getBounds());
        return changed;
    }

    @Override
    protected void onBoundsChange(Rect bounds) { updateLayerBounds(bounds); }

    private void updateLayerBounds(Rect bounds) {
        int paddingL = 0, paddingT = 0, paddingR = 0, paddingB = 0;
        final Rect outRect = mTmpOutRect;
        final boolean isPaddingNested = mLayerState.mPaddingMode == PADDING_MODE_NEST;
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) {
            final ChildDrawable r = array[i];
            final Drawable d = r.mDrawable;
            if (d == null) continue;
            final Rect container = mTmpContainer;
            container.set(d.getBounds());
            container.set(bounds.left + r.mInsetL + paddingL, bounds.top + r.mInsetT + paddingT, bounds.right - r.mInsetR - paddingR,
                    bounds.bottom - r.mInsetB - paddingB);
            final int intrinsicW = d.getIntrinsicWidth();
            final int intrinsicH = d.getIntrinsicHeight();
            final int layerW = r.mWidth;
            final int layerH = r.mHeight;
            final int gravity = resolveGravity(r.mGravity, layerW, layerH, intrinsicW, intrinsicH);
            final int resolvedW = layerW < 0 ? intrinsicW : layerW;
            final int resolvedH = layerH < 0 ? intrinsicH : layerH;
            Gravity.apply(gravity, resolvedW, resolvedH, container, outRect, getLayoutDirection());
            d.setBounds(outRect);
            if (isPaddingNested) {
                paddingL += i < mPaddingL.length ? mPaddingL[i] : 0;
                paddingR += i < mPaddingR.length ? mPaddingR[i] : 0;
                paddingT += i < mPaddingT.length ? mPaddingT[i] : 0;
                paddingB += i < mPaddingB.length ? mPaddingB[i] : 0;
            }
        }
    }

    private static int resolveGravity(int gravity, int width, int height, int intrinsicWidth, int intrinsicHeight) {
        if (!Gravity.isHorizontal(gravity)) {
            if (width < 0) gravity |= Gravity.FILL_HORIZONTAL;
            else gravity |= Gravity.START;
        }
        if (!Gravity.isVertical(gravity)) {
            if (height < 0) gravity |= Gravity.FILL_VERTICAL;
            else gravity |= Gravity.TOP;
        }
        if (width < 0 && intrinsicWidth < 0) gravity |= Gravity.FILL_HORIZONTAL;
        if (height < 0 && intrinsicHeight < 0) gravity |= Gravity.FILL_VERTICAL;
        return gravity;
    }

    @Override
    public int getIntrinsicWidth() {
        int width = -1;
        int padL = 0, padR = 0;
        final boolean nest = mLayerState.mPaddingMode == PADDING_MODE_NEST;
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) {
            final ChildDrawable r = array[i];
            if (r.mDrawable == null) continue;
            final int minWidth = r.mWidth < 0 ? r.mDrawable.getIntrinsicWidth() : r.mWidth;
            final int w = minWidth < 0 ? -1 : minWidth + r.mInsetL + r.mInsetR + padL + padR;
            if (w > width) width = w;
            if (nest) {
                padL += i < mPaddingL.length ? mPaddingL[i] : 0;
                padR += i < mPaddingR.length ? mPaddingR[i] : 0;
            }
        }
        return width;
    }

    @Override
    public int getIntrinsicHeight() {
        int height = -1;
        int padT = 0, padB = 0;
        final boolean nest = mLayerState.mPaddingMode == PADDING_MODE_NEST;
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) {
            final ChildDrawable r = array[i];
            if (r.mDrawable == null) continue;
            final int minHeight = r.mHeight < 0 ? r.mDrawable.getIntrinsicHeight() : r.mHeight;
            final int h = minHeight < 0 ? -1 : minHeight + r.mInsetT + r.mInsetB + padT + padB;
            if (h > height) height = h;
            if (nest) {
                padT += i < mPaddingT.length ? mPaddingT[i] : 0;
                padB += i < mPaddingB.length ? mPaddingB[i] : 0;
            }
        }
        return height;
    }

    private boolean refreshChildPadding(int i, ChildDrawable r) {
        if (r.mDrawable != null && i < mPaddingL.length) {
            final Rect rect = mTmpRect;
            r.mDrawable.getPadding(rect);
            if (rect.left != mPaddingL[i] || rect.top != mPaddingT[i] || rect.right != mPaddingR[i] || rect.bottom != mPaddingB[i]) {
                mPaddingL[i] = rect.left;
                mPaddingT[i] = rect.top;
                mPaddingR[i] = rect.right;
                mPaddingB[i] = rect.bottom;
                return true;
            }
        }
        return false;
    }

    void ensurePadding() {
        final int N = mLayerState.mNumChildren;
        if (mPaddingL != null && mPaddingL.length >= N) return;
        mPaddingL = new int[N];
        mPaddingT = new int[N];
        mPaddingR = new int[N];
        mPaddingB = new int[N];
    }

    void refreshPadding() {
        final int N = mLayerState.mNumChildren;
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < N; i++) refreshChildPadding(i, array[i]);
    }

    @Override
    public ConstantState getConstantState() { return mLayerState; }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mLayerState = createConstantState(mLayerState, null);
            for (int i = 0; i < mLayerState.mNumChildren; i++) {
                final Drawable dr = mLayerState.mChildren[i].mDrawable;
                if (dr != null) dr.mutate();
            }
            mMutated = true;
        }
        return this;
    }

    @Override
    public boolean onLayoutDirectionChanged(int layoutDirection) {
        boolean changed = false;
        final ChildDrawable[] array = mLayerState.mChildren;
        for (int i = 0; i < mLayerState.mNumChildren; i++) if (array[i].mDrawable != null) changed |= array[i].mDrawable.setLayoutDirection(layoutDirection);
        updateLayerBounds(getBounds());
        return changed;
    }

    static class ChildDrawable {
        public Drawable mDrawable;
        public int mInsetL, mInsetT, mInsetR, mInsetB;
        public int mWidth = -1;
        public int mHeight = -1;
        public int mGravity = Gravity.NO_GRAVITY;
        public int mId = View_NO_ID;

        ChildDrawable() {}

        ChildDrawable(ChildDrawable orig, LayerDrawable owner, Resources res) {
            final Drawable dr = orig.mDrawable;
            Drawable clone = null;
            if (dr != null) {
                final ConstantState cs = dr.getConstantState();
                clone = cs == null ? dr : cs.newDrawable(res);
                clone.setCallback(owner);
                clone.setBounds(dr.getBounds());
                clone.setLevel(dr.getLevel());
            }
            mDrawable = clone;
            mInsetL = orig.mInsetL;
            mInsetT = orig.mInsetT;
            mInsetR = orig.mInsetR;
            mInsetB = orig.mInsetB;
            mWidth = orig.mWidth;
            mHeight = orig.mHeight;
            mGravity = orig.mGravity;
            mId = orig.mId;
        }
    }

    static class LayerState extends ConstantState {
        int mNumChildren;
        ChildDrawable[] mChildren;
        int mPaddingLeft, mPaddingTop, mPaddingRight, mPaddingBottom;
        int mOpacityOverride = PixelFormat.UNKNOWN;
        int mChangingConfigurations;
        int mChildrenChangingConfigurations;
        boolean mAutoMirrored = false;
        int mPaddingMode = PADDING_MODE_NEST;

        LayerState(LayerState orig, LayerDrawable owner, Resources res) {
            if (orig != null) {
                final ChildDrawable[] origChildDrawable = orig.mChildren;
                final int N = orig.mNumChildren;
                mNumChildren = N;
                mChildren = new ChildDrawable[N];
                mChangingConfigurations = orig.mChangingConfigurations;
                mChildrenChangingConfigurations = orig.mChildrenChangingConfigurations;
                for (int i = 0; i < N; i++) mChildren[i] = new ChildDrawable(origChildDrawable[i], owner, res);
                mPaddingLeft = orig.mPaddingLeft;
                mPaddingTop = orig.mPaddingTop;
                mPaddingRight = orig.mPaddingRight;
                mPaddingBottom = orig.mPaddingBottom;
                mOpacityOverride = orig.mOpacityOverride;
                mAutoMirrored = orig.mAutoMirrored;
                mPaddingMode = orig.mPaddingMode;
            } else {
                mNumChildren = 0;
                mChildren = null;
            }
        }

        @Override
        public Drawable newDrawable() { return new LayerDrawable(this, null); }
        @Override
        public Drawable newDrawable(Resources res) { return new LayerDrawable(this, res); }
        @Override
        public int getChangingConfigurations() { return mChangingConfigurations | mChildrenChangingConfigurations; }

        public final int getOpacity() {
            final ChildDrawable[] array = mChildren;
            final int N = mNumChildren;
            int firstIndex = -1;
            for (int i = 0; i < N; i++) {
                if (array[i].mDrawable != null) {
                    firstIndex = i;
                    break;
                }
            }
            int op;
            if (firstIndex >= 0) op = array[firstIndex].mDrawable.getOpacity();
            else op = PixelFormat.TRANSPARENT;
            for (int i = firstIndex + 1; i < N; i++) {
                final Drawable dr = array[i].mDrawable;
                if (dr != null) op = Drawable.resolveOpacity(op, dr.getOpacity());
            }
            return op;
        }

        public final boolean isStateful() {
            final ChildDrawable[] array = mChildren;
            for (int i = 0; i < mNumChildren; i++) if (array[i].mDrawable != null && array[i].mDrawable.isStateful()) return true;
            return false;
        }

        void invalidateCache() {}
    }
}
