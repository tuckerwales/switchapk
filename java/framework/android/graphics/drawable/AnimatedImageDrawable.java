package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.util.AttributeSet;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class AnimatedImageDrawable extends Drawable implements Animatable2 {
    public static final int REPEAT_INFINITE = -1;
    private Drawable mFrame;
    private int mRepeatCount = REPEAT_INFINITE;

    public AnimatedImageDrawable() {}

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final TypedArray a = obtainAttributes(r, theme, attrs, new int[] {android.R.attr.src});
        mFrame = a.getDrawable(0);
        a.recycle();
    }

    @Override
    public void draw(Canvas canvas) { if (mFrame != null) mFrame.draw(canvas); }
    @Override
    protected void onBoundsChange(Rect bounds) { if (mFrame != null) mFrame.setBounds(bounds); }
    @Override
    public int getIntrinsicWidth() { return mFrame != null ? mFrame.getIntrinsicWidth() : -1; }
    @Override
    public int getIntrinsicHeight() { return mFrame != null ? mFrame.getIntrinsicHeight() : -1; }
    @Override
    public void setAlpha(int alpha) { if (mFrame != null) mFrame.setAlpha(alpha); }
    @Override
    public void setColorFilter(ColorFilter colorFilter) { if (mFrame != null) mFrame.setColorFilter(colorFilter); }
    @Override
    public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    public void setRepeatCount(int repeatCount) { mRepeatCount = repeatCount; }
    public int getRepeatCount() { return mRepeatCount; }
    public void start() {}
    public void stop() {}
    public boolean isRunning() { return false; }
    public void registerAnimationCallback(AnimationCallback callback) {}
    public boolean unregisterAnimationCallback(AnimationCallback callback) { return false; }
    public void clearAnimationCallbacks() {}
}
