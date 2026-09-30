package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class ScaleDrawable extends DrawableWrapper {
    private static final int MAX_LEVEL = 10000;
    private final Rect mTmpRect = new Rect();
    private float mScaleWidth = -1f, mScaleHeight = -1f;
    private int mGravity = Gravity.LEFT;
    private boolean mUseIntrinsicSizeAsMin;

    ScaleDrawable() { super(null); }

    public ScaleDrawable(Drawable drawable, int gravity, float scaleWidth, float scaleHeight) {
        super(drawable);
        mGravity = gravity;
        mScaleWidth = scaleWidth;
        mScaleHeight = scaleHeight;
    }

    private static final int[] ATTRS = {android.R.attr.drawable, android.R.attr.scaleWidth, android.R.attr.scaleHeight,
            android.R.attr.scaleGravity, android.R.attr.useIntrinsicSizeAsMinimum};

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final TypedArray a = obtainAttributes(r, theme, attrs, ATTRS);
        inflateDrawableAttr(r, a, 0, theme);
        mScaleWidth = pct(a.getString(1), mScaleWidth);
        mScaleHeight = pct(a.getString(2), mScaleHeight);
        mGravity = a.getInt(3, mGravity);
        mUseIntrinsicSizeAsMin = a.getBoolean(4, mUseIntrinsicSizeAsMin);
        a.recycle();
        if (getDrawable() == null) inflateChildDrawable(r, parser, attrs, theme);
    }

    private static float pct(String s, float def) {
        if (s == null || !s.endsWith("%")) return def;
        return Float.parseFloat(s.substring(0, s.length() - 1)) / 100f;
    }

    @Override
    public void draw(Canvas canvas) {
        final Drawable d = getDrawable();
        if (d != null && d.getLevel() != 0) d.draw(canvas);
    }

    @Override
    protected boolean onLevelChange(int level) {
        super.onLevelChange(level);
        onBoundsChange(getBounds());
        invalidateSelf();
        return true;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        final Drawable d = getDrawable();
        if (d == null) return;
        final Rect r = mTmpRect;
        final int level = getLevel();
        int w = bounds.width();
        if (mScaleWidth > 0) {
            final int iw = mUseIntrinsicSizeAsMin ? d.getIntrinsicWidth() : 0;
            w -= (int) ((w - iw) * (MAX_LEVEL - level) * mScaleWidth / MAX_LEVEL);
        }
        int h = bounds.height();
        if (mScaleHeight > 0) {
            final int ih = mUseIntrinsicSizeAsMin ? d.getIntrinsicHeight() : 0;
            h -= (int) ((h - ih) * (MAX_LEVEL - level) * mScaleHeight / MAX_LEVEL);
        }
        Gravity.apply(mGravity, w, h, bounds, r, getLayoutDirection());
        if (w > 0 && h > 0) d.setBounds(r.left, r.top, r.right, r.bottom);
    }
}
