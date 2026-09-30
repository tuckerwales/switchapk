package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;

public class ColorStateListDrawable extends Drawable {
    private ColorStateList mColor;
    private final ColorDrawable mDelegate = new ColorDrawable();
    private int mAlpha = -1;

    public ColorStateListDrawable() { mColor = ColorStateList.valueOf(0); }
    public ColorStateListDrawable(ColorStateList colorStateList) { mColor = colorStateList; onStateChange(getState()); }

    @Override
    public void draw(Canvas canvas) {
        mDelegate.setBounds(getBounds());
        mDelegate.draw(canvas);
    }

    @Override
    protected boolean onStateChange(int[] state) {
        int c = mColor.getColorForState(state, mColor.getDefaultColor());
        mDelegate.setColor(c);
        if (mAlpha >= 0) mDelegate.setAlpha(mAlpha);
        return true;
    }

    @Override
    public boolean isStateful() { return mColor.isStateful(); }
    @Override
    public void setAlpha(int alpha) { mAlpha = alpha; mDelegate.setAlpha(alpha); invalidateSelf(); }
    @Override
    public void setColorFilter(ColorFilter colorFilter) { mDelegate.setColorFilter(colorFilter); }
    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    public ColorStateList getColorStateList() { return mColor; }
    public void setColorStateList(ColorStateList colorStateList) { mColor = colorStateList; onStateChange(getState()); invalidateSelf(); }
}
