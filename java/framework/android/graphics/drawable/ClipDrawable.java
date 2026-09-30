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

public class ClipDrawable extends DrawableWrapper {
    public static final int HORIZONTAL = 1;
    public static final int VERTICAL = 2;
    private static final int MAX_LEVEL = 10000;
    private final Rect mTmpRect = new Rect();
    private int mOrientation = HORIZONTAL;
    private int mGravity = Gravity.LEFT;

    ClipDrawable() { super(null); }

    public ClipDrawable(Drawable drawable, int gravity, int orientation) {
        super(drawable);
        mGravity = gravity;
        mOrientation = orientation;
    }

    private static final int[] ATTRS = {android.R.attr.drawable, android.R.attr.clipOrientation, android.R.attr.gravity};

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final TypedArray a = obtainAttributes(r, theme, attrs, ATTRS);
        inflateDrawableAttr(r, a, 0, theme);
        mOrientation = a.getInt(1, mOrientation);
        mGravity = a.getInt(2, mGravity);
        a.recycle();
        if (getDrawable() == null) inflateChildDrawable(r, parser, attrs, theme);
    }

    @Override
    protected boolean onLevelChange(int level) {
        super.onLevelChange(level);
        invalidateSelf();
        return true;
    }

    @Override
    public void draw(Canvas canvas) {
        final Drawable dr = getDrawable();
        if (dr == null || dr.getLevel() == 0) return;
        final Rect r = mTmpRect;
        final Rect bounds = getBounds();
        final int level = getLevel();
        int w = bounds.width();
        final int iw = 0;
        if ((mOrientation & HORIZONTAL) != 0) w -= (w - iw) * (MAX_LEVEL - level) / MAX_LEVEL;
        int h = bounds.height();
        final int ih = 0;
        if ((mOrientation & VERTICAL) != 0) h -= (h - ih) * (MAX_LEVEL - level) / MAX_LEVEL;
        Gravity.apply(mGravity, w, h, bounds, r, getLayoutDirection());
        if (w > 0 && h > 0) {
            canvas.save();
            canvas.clipRect(r);
            dr.draw(canvas);
            canvas.restore();
        }
    }
}
