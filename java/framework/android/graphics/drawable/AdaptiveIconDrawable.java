package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.util.AttributeSet;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class AdaptiveIconDrawable extends Drawable implements Drawable.Callback {
    public static final float MASK_SIZE = 100f;
    private static final float EXTRA_INSET_PERCENTAGE = 1 / 4f;
    private static final float DEFAULT_VIEW_PORT_SCALE = 1f / (1 + 2 * EXTRA_INSET_PERCENTAGE);

    private Drawable mBackground, mForeground, mMonochrome;
    private final Path mMask = new Path();

    AdaptiveIconDrawable() {}

    public AdaptiveIconDrawable(Drawable backgroundDrawable, Drawable foregroundDrawable) { this(backgroundDrawable, foregroundDrawable, null); }

    public AdaptiveIconDrawable(Drawable backgroundDrawable, Drawable foregroundDrawable, Drawable monochromeDrawable) {
        mBackground = backgroundDrawable;
        mForeground = foregroundDrawable;
        mMonochrome = monochromeDrawable;
        if (mBackground != null) mBackground.setCallback(this);
        if (mForeground != null) mForeground.setCallback(this);
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final int innerDepth = parser.getDepth() + 1;
        int type, depth;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (type != XmlPullParser.START_TAG || depth > innerDepth) continue;
            String tag = parser.getName();
            final TypedArray a = obtainAttributes(r, theme, attrs, new int[] {android.R.attr.drawable});
            Drawable dr = a.getDrawable(0);
            a.recycle();
            if (dr == null) {
                while ((type = parser.next()) == XmlPullParser.TEXT) {}
                if (type == XmlPullParser.START_TAG) dr = Drawable.createFromXmlInner(r, parser, attrs, theme);
            }
            if (dr == null) continue;
            dr.setCallback(this);
            if (tag.equals("background")) mBackground = dr;
            else if (tag.equals("foreground")) mForeground = dr;
            else if (tag.equals("monochrome")) mMonochrome = dr;
        }
    }

    public Drawable getBackground() { return mBackground; }
    public Drawable getForeground() { return mForeground; }
    public Drawable getMonochrome() { return mMonochrome; }
    public Path getIconMask() { return mMask; }

    @Override
    protected void onBoundsChange(Rect b) {
        int cX = b.width() / 2, cY = b.height() / 2;
        int insetWidth = (int) (b.width() / (DEFAULT_VIEW_PORT_SCALE * 2));
        int insetHeight = (int) (b.height() / (DEFAULT_VIEW_PORT_SCALE * 2));
        Rect outRect = new Rect(b.left + cX - insetWidth, b.top + cY - insetHeight, b.left + cX + insetWidth, b.top + cY + insetHeight);
        if (mBackground != null) mBackground.setBounds(outRect);
        if (mForeground != null) mForeground.setBounds(outRect);
        mMask.reset();
        float r = Math.min(b.width(), b.height()) * 0.24f;
        mMask.addRoundRect(b.left, b.top, b.right, b.bottom, r, r, Path.Direction.CW);
    }

    @Override
    public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(mMask);
        if (mBackground != null) mBackground.draw(canvas);
        if (mForeground != null) mForeground.draw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    public int getIntrinsicWidth() {
        int w = Math.max(mBackground != null ? mBackground.getIntrinsicWidth() : -1, mForeground != null ? mForeground.getIntrinsicWidth() : -1);
        return w < 0 ? -1 : (int) (w * DEFAULT_VIEW_PORT_SCALE);
    }

    @Override
    public int getIntrinsicHeight() {
        int h = Math.max(mBackground != null ? mBackground.getIntrinsicHeight() : -1, mForeground != null ? mForeground.getIntrinsicHeight() : -1);
        return h < 0 ? -1 : (int) (h * DEFAULT_VIEW_PORT_SCALE);
    }

    @Override
    public void invalidateDrawable(Drawable who) { invalidateSelf(); }
    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }
    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }

    @Override
    public void setAlpha(int alpha) {
        if (mBackground != null) mBackground.setAlpha(alpha);
        if (mForeground != null) mForeground.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        if (mBackground != null) mBackground.setColorFilter(colorFilter);
        if (mForeground != null) mForeground.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
