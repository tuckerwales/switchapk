package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class InsetDrawable extends DrawableWrapper {
    private final Rect mTmpRect = new Rect();
    private float mInsetLeftF, mInsetTopF, mInsetRightF, mInsetBottomF; // fraction if < 0 handled via flags
    private int mInsetLeft, mInsetTop, mInsetRight, mInsetBottom;
    private boolean mLeftFrac, mTopFrac, mRightFrac, mBottomFrac;

    InsetDrawable() { super(null); }

    public InsetDrawable(Drawable drawable, int inset) { this(drawable, inset, inset, inset, inset); }

    public InsetDrawable(Drawable drawable, float inset) { this(drawable, inset, inset, inset, inset); }

    public InsetDrawable(Drawable drawable, int insetLeft, int insetTop, int insetRight, int insetBottom) {
        super(drawable);
        mInsetLeft = insetLeft;
        mInsetTop = insetTop;
        mInsetRight = insetRight;
        mInsetBottom = insetBottom;
    }

    public InsetDrawable(Drawable drawable, float insetLeftFraction, float insetTopFraction, float insetRightFraction, float insetBottomFraction) {
        super(drawable);
        mInsetLeftF = insetLeftFraction;
        mInsetTopF = insetTopFraction;
        mInsetRightF = insetRightFraction;
        mInsetBottomF = insetBottomFraction;
        mLeftFrac = mTopFrac = mRightFrac = mBottomFrac = true;
    }

    private static final int[] ATTRS = {android.R.attr.drawable, android.R.attr.inset, android.R.attr.insetLeft, android.R.attr.insetTop,
            android.R.attr.insetRight, android.R.attr.insetBottom, android.R.attr.visible};

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final TypedArray a = obtainAttributes(r, theme, attrs, ATTRS);
        inflateDrawableAttr(r, a, 0, theme);
        if (a.hasValue(1)) {
            setInsetAll(a, 1);
        }
        if (a.hasValue(2)) mInsetLeft = dim(a, 2, 0);
        if (a.hasValue(3)) mInsetTop = dim(a, 3, 1);
        if (a.hasValue(4)) mInsetRight = dim(a, 4, 2);
        if (a.hasValue(5)) mInsetBottom = dim(a, 5, 3);
        a.recycle();
        if (getDrawable() == null) inflateChildDrawable(r, parser, attrs, theme);
    }

    private void setInsetAll(TypedArray a, int idx) {
        mInsetLeft = dim(a, idx, 0);
        mInsetTop = dim(a, idx, 1);
        mInsetRight = dim(a, idx, 2);
        mInsetBottom = dim(a, idx, 3);
    }

    private int dim(TypedArray a, int idx, int side) {
        TypedValue tv = a.peekValue(idx);
        if (tv != null && tv.type == TypedValue.TYPE_FRACTION) {
            float f = tv.getFraction(1f, 1f);
            switch (side) {
                case 0: mInsetLeftF = f; mLeftFrac = true; break;
                case 1: mInsetTopF = f; mTopFrac = true; break;
                case 2: mInsetRightF = f; mRightFrac = true; break;
                default: mInsetBottomF = f; mBottomFrac = true; break;
            }
            return 0;
        }
        switch (side) {
            case 0: mLeftFrac = false; break;
            case 1: mTopFrac = false; break;
            case 2: mRightFrac = false; break;
            default: mBottomFrac = false; break;
        }
        return a.getDimensionPixelOffset(idx, 0);
    }

    private void getInsets(Rect out) {
        final Rect b = getBounds();
        out.left = mLeftFrac ? (int) (b.width() * mInsetLeftF) : mInsetLeft;
        out.right = mRightFrac ? (int) (b.width() * mInsetRightF) : mInsetRight;
        out.top = mTopFrac ? (int) (b.height() * mInsetTopF) : mInsetTop;
        out.bottom = mBottomFrac ? (int) (b.height() * mInsetBottomF) : mInsetBottom;
    }

    @Override
    public boolean getPadding(Rect padding) {
        final boolean pad = super.getPadding(padding);
        getInsets(mTmpRect);
        padding.left += mTmpRect.left;
        padding.right += mTmpRect.right;
        padding.top += mTmpRect.top;
        padding.bottom += mTmpRect.bottom;
        return pad || (mTmpRect.left | mTmpRect.right | mTmpRect.top | mTmpRect.bottom) != 0;
    }

    @Override
    public Insets getOpticalInsets() {
        final Insets contentInsets = super.getOpticalInsets();
        getInsets(mTmpRect);
        return Insets.of(contentInsets.left + mTmpRect.left, contentInsets.top + mTmpRect.top, contentInsets.right + mTmpRect.right, contentInsets.bottom + mTmpRect.bottom);
    }

    @Override
    public int getOpacity() {
        getInsets(mTmpRect);
        final int opacity = getDrawable() != null ? getDrawable().getOpacity() : PixelFormat.TRANSPARENT;
        if (opacity == PixelFormat.OPAQUE && (mTmpRect.left > 0 || mTmpRect.top > 0 || mTmpRect.right > 0 || mTmpRect.bottom > 0)) return PixelFormat.TRANSLUCENT;
        return opacity;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        final Rect r = new Rect(bounds);
        getInsets(mTmpRect);
        r.left += mTmpRect.left;
        r.top += mTmpRect.top;
        r.right -= mTmpRect.right;
        r.bottom -= mTmpRect.bottom;
        super.onBoundsChange(r);
    }

    @Override
    public int getIntrinsicWidth() {
        final int childWidth = getDrawable() != null ? getDrawable().getIntrinsicWidth() : -1;
        if (childWidth < 0) return -1;
        if (mLeftFrac || mRightFrac) return (int) (childWidth / (1 - (mLeftFrac ? mInsetLeftF : 0) - (mRightFrac ? mInsetRightF : 0)));
        return childWidth + mInsetLeft + mInsetRight;
    }

    @Override
    public int getIntrinsicHeight() {
        final int childHeight = getDrawable() != null ? getDrawable().getIntrinsicHeight() : -1;
        if (childHeight < 0) return -1;
        if (mTopFrac || mBottomFrac) return (int) (childHeight / (1 - (mTopFrac ? mInsetTopF : 0) - (mBottomFrac ? mInsetBottomF : 0)));
        return childHeight + mInsetTop + mInsetBottom;
    }

    @Override
    public void getOutline(Outline outline) {
        if (getDrawable() != null) getDrawable().getOutline(outline);
    }
}
