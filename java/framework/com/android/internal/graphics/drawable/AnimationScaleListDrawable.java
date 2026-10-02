package com.android.internal.graphics.drawable;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.util.AttributeSet;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Port of the AOSP internal drawable the Material progress spinners use: an
 * animatable child and a static child, picked by whether animations are
 * enabled. Animators do not run before WS5, so the static child shows.
 */
public class AnimationScaleListDrawable extends DrawableContainer implements Animatable {
    private AnimationScaleListState mAnimationScaleListState;
    private boolean mMutated;

    public AnimationScaleListDrawable() { this(null, null); }

    private AnimationScaleListDrawable(AnimationScaleListState state, Resources res) {
        final AnimationScaleListState asls = new AnimationScaleListState(state, this, res);
        setConstantState(asls);
        onStateChange(getState());
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        final boolean changed = super.onStateChange(stateSet);
        int idx = mAnimationScaleListState.getCurrentDrawableIndexBasedOnScale();
        return selectDrawable(idx) || changed;
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme)
            throws XmlPullParserException, IOException {
        int type;
        final int innerDepth = parser.getDepth() + 1;
        int depth;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (type != XmlPullParser.START_TAG || depth > innerDepth || !parser.getName().equals("item")) continue;
            final int[] drawableAttr = {android.R.attr.drawable};
            final TypedArray a = theme != null ? theme.obtainStyledAttributes(attrs, drawableAttr, 0, 0)
                    : r.obtainAttributes(attrs, drawableAttr);
            Drawable dr = a.getDrawable(0);
            a.recycle();
            if (dr == null) {
                while ((type = parser.next()) == XmlPullParser.TEXT) {}
                if (type != XmlPullParser.START_TAG) {
                    throw new XmlPullParserException(parser.getPositionDescription()
                            + ": <item> tag requires a 'drawable' attribute or child tag defining a drawable");
                }
                dr = Drawable.createFromXmlInner(r, parser, attrs, theme);
            }
            mAnimationScaleListState.addDrawable(dr);
        }
        onStateChange(getState());
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) mMutated = true;
        return this;
    }

    @Override
    public void start() {
        Drawable dr = getCurrent();
        if (dr instanceof Animatable) ((Animatable) dr).start();
    }

    @Override
    public void stop() {
        Drawable dr = getCurrent();
        if (dr instanceof Animatable) ((Animatable) dr).stop();
    }

    @Override
    public boolean isRunning() {
        Drawable dr = getCurrent();
        return dr instanceof Animatable && ((Animatable) dr).isRunning();
    }

    static class AnimationScaleListState extends DrawableContainerState {
        int mStaticDrawableIndex = -1;
        int mAnimatableDrawableIndex = -1;

        AnimationScaleListState(AnimationScaleListState orig, AnimationScaleListDrawable owner, Resources res) {
            super(orig, owner, res);
            if (orig != null) {
                mStaticDrawableIndex = orig.mStaticDrawableIndex;
                mAnimatableDrawableIndex = orig.mAnimatableDrawableIndex;
            }
        }

        int addDrawable(Drawable drawable) {
            final int pos = addChild(drawable);
            if (drawable instanceof Animatable) mAnimatableDrawableIndex = pos;
            else mStaticDrawableIndex = pos;
            return pos;
        }

        @Override
        public Drawable newDrawable() { return new AnimationScaleListDrawable(this, null); }

        @Override
        public Drawable newDrawable(Resources res) { return new AnimationScaleListDrawable(this, res); }

        /** The animatable child waits on AnimatedVectorDrawable. Until then the static child is shown. */
        int getCurrentDrawableIndexBasedOnScale() {
            // TODO(WS5) the animatable child once AnimatedVectorDrawable animates.
            return mStaticDrawableIndex >= 0 ? mStaticDrawableIndex : mAnimatableDrawableIndex;
        }
    }

    @Override
    protected void setConstantState(DrawableContainerState state) {
        super.setConstantState(state);
        if (state instanceof AnimationScaleListState) mAnimationScaleListState = (AnimationScaleListState) state;
    }
}
