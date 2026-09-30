package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.util.AttributeSet;
import java.io.IOException;
import java.util.ArrayList;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** Draws the underlying vector; target animations are applied instantly (end state not tracked). */
public class AnimatedVectorDrawable extends Drawable implements Animatable2 {
    private VectorDrawable mVector;
    private boolean mRunning;
    private final ArrayList<AnimationCallback> mCallbacks = new ArrayList<AnimationCallback>();

    public AnimatedVectorDrawable() {}

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final TypedArray a = obtainAttributes(r, theme, attrs, new int[] {android.R.attr.drawable});
        Drawable d = null;
        try {
            d = a.getDrawable(0);
        } catch (RuntimeException e) {
            d = null;
        }
        a.recycle();
        int type;
        final int depth = parser.getDepth();
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT && (type != XmlPullParser.END_TAG || parser.getDepth() > depth)) {
            if (type == XmlPullParser.START_TAG && d == null && "vector".equals(parser.getName())) {
                VectorDrawable vd = new VectorDrawable();
                vd.inflate(r, parser, attrs, theme);
                d = vd;
            }
        }
        if (d instanceof VectorDrawable) mVector = (VectorDrawable) d;
        if (mVector != null) mVector.setCallback(new Callback() {
            public void invalidateDrawable(Drawable who) { invalidateSelf(); }
            public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }
            public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }
        });
    }

    @Override
    public void draw(Canvas canvas) { if (mVector != null) mVector.draw(canvas); }

    @Override
    protected void onBoundsChange(Rect bounds) { if (mVector != null) mVector.setBounds(bounds); }

    @Override
    public int getIntrinsicWidth() { return mVector != null ? mVector.getIntrinsicWidth() : -1; }
    @Override
    public int getIntrinsicHeight() { return mVector != null ? mVector.getIntrinsicHeight() : -1; }
    @Override
    public void setAlpha(int alpha) { if (mVector != null) mVector.setAlpha(alpha); }
    @Override
    public int getAlpha() { return mVector != null ? mVector.getAlpha() : 255; }
    @Override
    public void setColorFilter(ColorFilter colorFilter) { if (mVector != null) mVector.setColorFilter(colorFilter); }
    @Override
    public void setTintList(android.content.res.ColorStateList tint) { if (mVector != null) mVector.setTintList(tint); }
    @Override
    public void setTintMode(android.graphics.PorterDuff.Mode tintMode) { if (mVector != null) mVector.setTintMode(tintMode); }
    @Override
    public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    @Override
    public boolean isStateful() { return mVector != null && mVector.isStateful(); }

    @Override
    protected boolean onStateChange(int[] state) { return mVector != null && mVector.setState(state); }

    public void start() {
        mRunning = true;
        for (AnimationCallback c : new ArrayList<AnimationCallback>(mCallbacks)) c.onAnimationStart(this);
        mRunning = false;
        for (AnimationCallback c : new ArrayList<AnimationCallback>(mCallbacks)) c.onAnimationEnd(this);
    }

    public void stop() { mRunning = false; }
    public boolean isRunning() { return mRunning; }
    public void reset() {}
    public void reverse() {}
    public boolean canReverse() { return false; }
    public void registerAnimationCallback(AnimationCallback callback) { if (callback != null) mCallbacks.add(callback); }
    public boolean unregisterAnimationCallback(AnimationCallback callback) { return mCallbacks.remove(callback); }
    public void clearAnimationCallbacks() { mCallbacks.clear(); }

    @Override
    public ConstantState getConstantState() {
        final AnimatedVectorDrawable self = this;
        return new ConstantState() {
            public Drawable newDrawable() {
                AnimatedVectorDrawable d = new AnimatedVectorDrawable();
                if (self.mVector != null) d.mVector = (VectorDrawable) self.mVector.getConstantState().newDrawable();
                return d;
            }
            public int getChangingConfigurations() { return 0; }
        };
    }
}
